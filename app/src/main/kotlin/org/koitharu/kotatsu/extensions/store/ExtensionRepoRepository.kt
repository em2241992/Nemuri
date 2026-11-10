package org.koitharu.kotatsu.extensions.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * ExternalExtensionRepoRepository:
 * Fetches, validates, caches, and decodes Mihon / Tachiyomi / Keiyoushi / LNReader extension repositories:
 *  - repo.json with index_v2 pointer (up to MAX_INDEX_HOPS = 3)
 *  - Binary Protobuf index.pb (NetworkExtensionStore & ExtensionList wire format)
 *  - Legacy index.min.json / index.json arrays
 *  - LNReader plugins.min.json (.js novel plugin stores)
 */
class ExtensionRepoRepository(
    context: Context,
    private val httpClient: OkHttpClient = defaultRepoHttpClient(),
) {
    private val cacheDir = File(context.cacheDir, "extension_repo_cache").apply { mkdirs() }
    private val memoryCache = LinkedHashMap<String, List<ExternalExtensionRepoEntry>>()
    private val repoMetaMemoryCache = LinkedHashMap<String, ExternalRepoInfo>()

    suspend fun getRepoInfo(repoUrl: String, forceRefresh: Boolean = false): Result<ExternalRepoInfo> =
        validateStore(repoUrl, forceRefresh).map { validated ->
            ExternalRepoInfo(
                name = validated.displayName,
                baseUrl = getBaseUrl(validated.normalizedUrl),
                website = validated.website,
                signingKeyFingerprint = validated.signingKeyFingerprint,
                extensionCount = validated.extensionCount,
            )
        }

    suspend fun validateStore(repoUrl: String, forceRefresh: Boolean = false): Result<ValidatedExtensionStore> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedUrl = normalizeExtensionStoreUrl(repoUrl)
                require(normalizedUrl.isNotBlank()) { "Repository URL cannot be empty" }

                val baseUrl = getBaseUrl(normalizedUrl)
                val repoJson = fetchRepoJsonOrNull("$baseUrl/repo.json")
                val entriesResult = loadEntriesForUrl(
                    normalizedUrl = normalizedUrl,
                    baseUrl = baseUrl,
                    repoJson = repoJson,
                    forceRefresh = forceRefresh,
                )
                val entries = entriesResult.entries
                val resolvedMeta = entriesResult.meta ?: repoJson?.meta
                val displayName = resolvedMeta?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultExtensionStoreName(normalizedUrl)

                val info = ExternalRepoInfo(
                    name = displayName,
                    baseUrl = baseUrl,
                    website = resolvedMeta?.website,
                    signingKeyFingerprint = resolvedMeta?.signingKeyFingerprint,
                    extensionCount = entries.size,
                )
                synchronized(repoMetaMemoryCache) {
                    repoMetaMemoryCache[normalizedUrl] = info
                }

                ValidatedExtensionStore(
                    normalizedUrl = normalizedUrl,
                    displayName = displayName,
                    website = resolvedMeta?.website,
                    signingKeyFingerprint = resolvedMeta?.signingKeyFingerprint,
                    extensionCount = entries.size,
                    entries = entries,
                )
            }
        }

    suspend fun getAvailableExtensions(
        repoUrl: String,
        forceRefresh: Boolean = false,
    ): Result<List<ExternalExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeExtensionStoreUrl(repoUrl)
        if (!forceRefresh) {
            synchronized(memoryCache) {
                memoryCache[normalizedUrl]?.takeIf { it.isNotEmpty() }?.let { return@withContext Result.success(it) }
            }
            readDiskCache(normalizedUrl)?.takeIf { it.isNotEmpty() }?.let { cached ->
                synchronized(memoryCache) {
                    memoryCache[normalizedUrl] = cached
                }
                return@withContext Result.success(cached)
            }
        }
        validateStore(normalizedUrl, forceRefresh = true).map { it.entries }
    }

    fun getCachedExtensions(repoUrl: String): List<ExternalExtensionRepoEntry> {
        val normalizedUrl = normalizeExtensionStoreUrl(repoUrl)
        synchronized(memoryCache) {
            memoryCache[normalizedUrl]?.let { return it }
        }
        val disk = readDiskCache(normalizedUrl) ?: return emptyList()
        synchronized(memoryCache) {
            memoryCache[normalizedUrl] = disk
        }
        return disk
    }

    fun hasCachedExtensions(repoUrl: String): Boolean {
        return getCachedExtensions(repoUrl).isNotEmpty()
    }

    fun isDeprecatedStubExtension(pkg: String, name: String): Boolean {
        val p = pkg.trim().lowercase(Locale.ROOT)
        val n = name.trim().lowercase(Locale.ROOT)
        return p == "eu.kanade.tachiyomi.extension.all.keiyoushi" ||
            p == "eu.kanade.tachiyomi.extension.all.mihon" ||
            n.contains("outdated app") ||
            n.contains("update to mihon")
    }

    fun resolveApkUrl(repoUrl: String, entry: ExternalExtensionRepoEntry): String {
        entry.apkUrl?.takeIf { it.isNotBlank() }?.let { return it }
        if (entry.apk.startsWith("http://") || entry.apk.startsWith("https://")) {
            return entry.apk
        }
        val baseUrl = getBaseUrl(normalizeExtensionStoreUrl(repoUrl))
        return "$baseUrl/apk/${entry.apk.trimStart('/')}"
    }

    fun resolveIconUrl(repoUrl: String, entry: ExternalExtensionRepoEntry): String {
        entry.iconUrl?.takeIf { it.isNotBlank() }?.let { return it }
        val baseUrl = getBaseUrl(normalizeExtensionStoreUrl(repoUrl))
        return "$baseUrl/icon/${entry.pkg}.png"
    }

    fun getBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/index.min.json", ignoreCase = true) ->
                trimmed.removeSuffix("/index.min.json").removeSuffix("/INDEX.MIN.JSON")
            trimmed.endsWith("/index.json", ignoreCase = true) ->
                trimmed.removeSuffix("/index.json").removeSuffix("/INDEX.JSON")
            trimmed.endsWith("/index.pb", ignoreCase = true) ->
                trimmed.removeSuffix("/index.pb").removeSuffix("/INDEX.PB")
            trimmed.endsWith("/repo.json", ignoreCase = true) ->
                trimmed.removeSuffix("/repo.json").removeSuffix("/REPO.JSON")
            trimmed.endsWith("/plugins.min.json", ignoreCase = true) ->
                trimmed.removeSuffix("/plugins.min.json").removeSuffix("/PLUGINS.MIN.JSON")
            else -> trimmed
        }
    }

    private data class LoadedEntriesResult(
        val entries: List<ExternalExtensionRepoEntry>,
        val meta: ExternalRepoMeta? = null,
    )

    private fun loadEntriesForUrl(
        normalizedUrl: String,
        baseUrl: String,
        repoJson: ExternalRepoJson?,
        forceRefresh: Boolean,
    ): LoadedEntriesResult {
        if (!forceRefresh) {
            synchronized(memoryCache) {
                memoryCache[normalizedUrl]?.takeIf { it.isNotEmpty() }?.let {
                    return LoadedEntriesResult(it, repoJson?.meta)
                }
            }
            readDiskCache(normalizedUrl)?.takeIf { it.isNotEmpty() }?.let { cached ->
                synchronized(memoryCache) {
                    memoryCache[normalizedUrl] = cached
                }
                return LoadedEntriesResult(cached, repoJson?.meta)
            }
        }

        val candidateUrls = buildList {
            // As in DropSauce/Mihon: a repo whose repo.json names an index_v2 has moved there
            // (Keiyoushi's index.min.json is now just a 2-item "Outdated App / Update to Mihon" stub).
            val indexV2 = repoJson?.indexV2?.takeIf { it.isNotBlank() }
            if (indexV2 != null) {
                if (indexV2.startsWith("http://") || indexV2.startsWith("https://")) {
                    add(indexV2)
                } else {
                    add("$baseUrl/${indexV2.trimStart('/')}")
                }
            }
            if (normalizedUrl.endsWith(".pb", ignoreCase = true)) {
                add(normalizedUrl)
            }
            add("$baseUrl/index.pb")
            if (normalizedUrl.endsWith(".json", ignoreCase = true)) {
                add(normalizedUrl)
            }
            add("$baseUrl/index.min.json")
            add("$baseUrl/index.json")
            add("$baseUrl/plugins.min.json")
            if (normalizedUrl !in this) {
                add(normalizedUrl)
            }
        }.distinct()

        var lastError: Throwable? = null
        for (candidate in candidateUrls) {
            val attempt = runCatching {
                loadEntriesWithHops(
                    url = candidate,
                    fallbackBaseUrl = baseUrl,
                    hopCount = 0,
                )
            }
            if (attempt.isSuccess) {
                val loaded = attempt.getOrThrow()
                if (loaded.entries.isNotEmpty()) {
                    synchronized(memoryCache) {
                        memoryCache[normalizedUrl] = loaded.entries
                    }
                    writeDiskCache(normalizedUrl, loaded.entries)
                    return loaded
                }
            } else {
                lastError = attempt.exceptionOrNull()
            }
        }

        // Fall back to existing disk cache if network failed
        readDiskCache(normalizedUrl)?.takeIf { it.isNotEmpty() }?.let { cached ->
            return LoadedEntriesResult(cached, repoJson?.meta)
        }

        throw (lastError ?: IllegalStateException("No extensions found at repository: $normalizedUrl"))
    }

    private fun loadEntriesWithHops(
        url: String,
        fallbackBaseUrl: String,
        hopCount: Int,
    ): LoadedEntriesResult {
        require(hopCount <= MAX_INDEX_HOPS) { "Exceeded maximum index redirect hops ($MAX_INDEX_HOPS)" }

        val rawBytes = downloadBytes(url)
        val bytes = maybeDecompressGzip(rawBytes)
        val firstNonWhitespace = bytes.firstOrNull {
            val ch = it.toInt().toChar()
            !ch.isWhitespace()
        }?.toInt()?.toChar()

        return when (firstNonWhitespace) {
            '[' -> {
                val text = bytes.toString(Charsets.UTF_8)
                val entries = parseJsonArrayEntries(text, fallbackBaseUrl)
                LoadedEntriesResult(entries = entries)
            }
            '{' -> {
                val text = bytes.toString(Charsets.UTF_8)
                val jsonObj = JSONObject(text)
                // Case 1: repo.json with meta + index_v2
                val metaObj = jsonObj.optJSONObject("meta")
                val indexV2 = jsonObj.optString("index_v2").takeIf { it.isNotBlank() }
                if (metaObj != null && indexV2 != null && !jsonObj.has("extensions")) {
                    val repoMeta = ExternalRepoMeta(
                        name = metaObj.optString("name", defaultExtensionStoreName(fallbackBaseUrl)),
                        website = metaObj.optString("website").takeIf { it.isNotBlank() },
                        signingKeyFingerprint = metaObj.optString("signingKeyFingerprint").takeIf { it.isNotBlank() },
                    )
                    val targetUrl = if (indexV2.startsWith("http://") || indexV2.startsWith("https://")) {
                        indexV2
                    } else {
                        "$fallbackBaseUrl/${indexV2.trimStart('/')}"
                    }
                    val hopped = loadEntriesWithHops(targetUrl, fallbackBaseUrl, hopCount + 1)
                    return hopped.copy(meta = hopped.meta ?: repoMeta)
                }

                // Case 2: NetworkExtensionStore JSON object
                val store = parseNetworkExtensionStoreJson(jsonObj, fallbackBaseUrl)
                if (store.extensions.isEmpty() && store.extensionListUrl.isNotBlank()) {
                    val nextUrl = if (store.extensionListUrl.startsWith("http://") || store.extensionListUrl.startsWith("https://")) {
                        store.extensionListUrl
                    } else {
                        "${store.baseUrl.ifBlank { fallbackBaseUrl }.trimEnd('/')}/${store.extensionListUrl.trimStart('/')}"
                    }
                    val hopped = loadEntriesWithHops(
                        url = nextUrl,
                        fallbackBaseUrl = store.baseUrl.ifBlank { fallbackBaseUrl },
                        hopCount = hopCount + 1,
                    )
                    val storeMeta = ExternalRepoMeta(
                        name = store.name.ifBlank { defaultExtensionStoreName(fallbackBaseUrl) },
                        website = store.baseUrl.takeIf { it.isNotBlank() },
                        signingKeyFingerprint = store.signingKeyFingerprints.firstOrNull(),
                    )
                    return hopped.copy(meta = hopped.meta ?: storeMeta)
                }

                val enriched = enrichEntriesWithBaseUrl(
                    entries = store.extensions,
                    baseUrl = store.baseUrl.ifBlank { fallbackBaseUrl },
                )
                val storeMeta = ExternalRepoMeta(
                    name = store.name.ifBlank { defaultExtensionStoreName(fallbackBaseUrl) },
                    website = store.baseUrl.takeIf { it.isNotBlank() },
                    signingKeyFingerprint = store.signingKeyFingerprints.firstOrNull(),
                )
                LoadedEntriesResult(entries = enriched, meta = storeMeta)
            }
            else -> {
                // Case 3: Protobuf binary (NetworkExtensionStore or ExtensionList in index.pb)
                val parsedPb = parseProtobufStoreOrExtensionList(bytes, fallbackBaseUrl)
                if (parsedPb.entries.isEmpty() && parsedPb.extensionListUrl.isNotBlank()) {
                    val nextUrl = if (parsedPb.extensionListUrl.startsWith("http://") || parsedPb.extensionListUrl.startsWith("https://")) {
                        parsedPb.extensionListUrl
                    } else {
                        "${parsedPb.baseUrl.ifBlank { fallbackBaseUrl }.trimEnd('/')}/${parsedPb.extensionListUrl.trimStart('/')}"
                    }
                    val hopped = loadEntriesWithHops(
                        url = nextUrl,
                        fallbackBaseUrl = parsedPb.baseUrl.ifBlank { fallbackBaseUrl },
                        hopCount = hopCount + 1,
                    )
                    return hopped.copy(meta = hopped.meta ?: parsedPb.meta)
                }
                LoadedEntriesResult(
                    entries = enrichEntriesWithBaseUrl(parsedPb.entries, parsedPb.baseUrl.ifBlank { fallbackBaseUrl }),
                    meta = parsedPb.meta,
                )
            }
        }
    }

    private fun fetchRepoJsonOrNull(url: String): ExternalRepoJson? {
        return runCatching {
            val bytes = maybeDecompressGzip(downloadBytes(url))
            val text = bytes.toString(Charsets.UTF_8).trim()
            if (!text.startsWith("{")) return null
            val obj = JSONObject(text)
            val metaObj = obj.optJSONObject("meta") ?: return null
            ExternalRepoJson(
                meta = ExternalRepoMeta(
                    name = metaObj.optString("name", ""),
                    website = metaObj.optString("website").takeIf { it.isNotBlank() },
                    signingKeyFingerprint = metaObj.optString("signingKeyFingerprint").takeIf { it.isNotBlank() },
                ),
                indexV2 = obj.optString("index_v2").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    fun parseJsonArrayEntries(jsonText: String, baseUrl: String): List<ExternalExtensionRepoEntry> {
        val array = JSONArray(jsonText)
        if (array.length() == 0) return emptyList()

        val firstObj = array.optJSONObject(0) ?: return emptyList()
        val isLnReaderFormat = firstObj.has("site") &&
            firstObj.has("url") &&
            !firstObj.has("pkg") &&
            firstObj.optString("url").endsWith(".js", ignoreCase = true)

        if (isLnReaderFormat) {
            val lnEntries = mutableListOf<ExternalExtensionRepoEntry>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val site = item.optString("site").trim()
                val langRaw = item.optString("lang", "en").trim()
                val version = item.optString("version", "1.0.0").trim()
                val url = item.optString("url").trim()
                val iconUrl = item.optString("iconUrl").trim()
                if (id.isEmpty() || name.isEmpty() || url.isEmpty()) continue

                val lang = mapLnReaderLanguage(langRaw)
                val pkg = "lnreader.plugin.${id.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_")}"
                val code = version.split('.')
                    .mapNotNull { it.filter(Char::isDigit).toLongOrNull() }
                    .fold(0L) { acc, part -> acc * 100L + part }
                    .coerceAtLeast(1L)
                val sourceId = pkg.hashCode().toLong() and 0x7FFFFFFFL

                lnEntries += ExternalExtensionRepoEntry(
                    name = name,
                    pkg = pkg,
                    apk = url,
                    lang = lang,
                    code = code,
                    version = version,
                    nsfw = 0,
                    sources = listOf(
                        ExternalExtensionRepoSource(
                            name = name,
                            lang = lang,
                            id = sourceId,
                            baseUrl = site,
                            versionId = 1,
                        ),
                    ),
                    iconUrl = iconUrl.takeIf { it.isNotBlank() },
                    apkUrl = url,
                )
            }
            return lnEntries
        }

        val mihonEntries = mutableListOf<ExternalExtensionRepoEntry>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val parsed = parseSingleExtensionJson(item) ?: continue
            if (isDeprecatedStubExtension(parsed.pkg, parsed.name)) continue
            mihonEntries += parsed
        }
        return enrichEntriesWithBaseUrl(mihonEntries, baseUrl)
    }

    private fun parseNetworkExtensionStoreJson(
        obj: JSONObject,
        fallbackBaseUrl: String,
    ): NetworkExtensionStore {
        val name = obj.optString("name", "")
        val baseUrl = obj.optString("baseUrl", fallbackBaseUrl).ifBlank { fallbackBaseUrl }
        val extensionListUrl = obj.optString("extensionListUrl", "")
        val fingerprints = mutableListOf<String>()
        val fpArray = obj.optJSONArray("signingKeyFingerprints")
        if (fpArray != null) {
            for (i in 0 until fpArray.length()) {
                val fp = fpArray.optString(i).trim()
                if (fp.isNotEmpty()) fingerprints += fp
            }
        }
        val extensions = mutableListOf<ExternalExtensionRepoEntry>()
        val extArray = obj.optJSONArray("extensions")
        if (extArray != null) {
            for (i in 0 until extArray.length()) {
                val item = extArray.optJSONObject(i) ?: continue
                parseSingleExtensionJson(item)?.let { extensions += it }
            }
        }
        return NetworkExtensionStore(
            name = name,
            baseUrl = baseUrl,
            extensionListUrl = extensionListUrl,
            signingKeyFingerprints = fingerprints,
            extensions = extensions,
        )
    }

    private fun parseSingleExtensionJson(item: JSONObject): ExternalExtensionRepoEntry? {
        val pkg = item.optString("pkg").trim()
        val name = item.optString("name").trim()
        if (pkg.isEmpty() || name.isEmpty()) return null
        val apk = item.optString("apk").trim()
        val lang = item.optString("lang", "all").trim().ifEmpty { "all" }
        val code = item.optLong("code", 1L)
        val version = item.optString("version", "1.0").trim()
        val nsfw = when (val rawNsfw = item.opt("nsfw")) {
            is Boolean -> if (rawNsfw) 1 else 0
            is Number -> rawNsfw.toInt()
            is String -> if (rawNsfw == "1" || rawNsfw.equals("true", ignoreCase = true)) 1 else 0
            else -> 0
        }
        val hasReadme = item.optInt("hasReadme", 0)
        val hasChangelog = item.optInt("hasChangelog", 0)
        val iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() }
        val apkUrl = item.optString("apkUrl").takeIf { it.isNotBlank() }

        val sources = mutableListOf<ExternalExtensionRepoSource>()
        val sourcesArr = item.optJSONArray("sources")
        if (sourcesArr != null) {
            for (j in 0 until sourcesArr.length()) {
                val srcObj = sourcesArr.optJSONObject(j) ?: continue
                val srcName = srcObj.optString("name", name).trim().ifEmpty { name }
                val srcLang = srcObj.optString("lang", lang).trim().ifEmpty { lang }
                val srcId = when (val rawId = srcObj.opt("id")) {
                    is Number -> rawId.toLong()
                    is String -> rawId.toLongOrNull() ?: (srcName.hashCode().toLong() and 0x7FFFFFFFL)
                    else -> srcName.hashCode().toLong() and 0x7FFFFFFFL
                }
                val srcBaseUrl = srcObj.optString("baseUrl", "").trim()
                val versionId = srcObj.optInt("versionId", 1)
                sources += ExternalExtensionRepoSource(
                    name = srcName,
                    lang = srcLang,
                    id = srcId,
                    baseUrl = srcBaseUrl,
                    versionId = versionId,
                )
            }
        }

        return ExternalExtensionRepoEntry(
            name = name,
            pkg = pkg,
            apk = apk,
            lang = lang,
            code = code,
            version = version,
            nsfw = nsfw,
            hasReadme = hasReadme,
            hasChangelog = hasChangelog,
            sources = sources,
            iconUrl = iconUrl,
            apkUrl = apkUrl,
        )
    }

    private data class ParsedProtobufResult(
        val entries: List<ExternalExtensionRepoEntry>,
        val baseUrl: String,
        val extensionListUrl: String,
        val meta: ExternalRepoMeta?,
    )

    /**
     * Decodes Keiyoushi / Mihon Protobuf `index.pb`:
     * Supports both `NetworkExtensionStore` (fields: 1=name, 2=baseUrl, 3=extensionListUrl,
     * 4=signingKeyFingerprints, 5=extensions) and direct `ExtensionList` (field 1 = repeated entry).
     */
    fun parseProtobufEntries(bytes: ByteArray, fallbackBaseUrl: String): List<ExternalExtensionRepoEntry> {
        val result = parseProtobufStoreOrExtensionList(bytes, fallbackBaseUrl)
        return enrichEntriesWithBaseUrl(result.entries, result.baseUrl.ifBlank { fallbackBaseUrl })
    }

    private fun parseProtobufStoreOrExtensionList(
        bytes: ByteArray,
        fallbackBaseUrl: String,
    ): ParsedProtobufResult {
        var storeName = ""
        var storeBaseUrl = ""
        var extensionListUrl = ""
        val fingerprints = mutableListOf<String>()
        val entriesFromField101 = mutableListOf<ExternalExtensionRepoEntry>()
        val entriesFromField5 = mutableListOf<ExternalExtensionRepoEntry>()
        val entriesFromField1 = mutableListOf<ExternalExtensionRepoEntry>()

        var pos = 0
        val limit = bytes.size
        while (pos < limit) {
            val tagPair = readVarint(bytes, pos, limit) ?: break
            val tag = tagPair.first.toInt()
            pos = tagPair.second
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> {
                    val v = readVarint(bytes, pos, limit) ?: break
                    pos = v.second
                }
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val lenPair = readVarint(bytes, pos, limit) ?: break
                    val len = lenPair.first.toInt()
                    pos = lenPair.second
                    if (len < 0 || pos + len > limit) break
                    val slice = bytes.copyOfRange(pos, pos + len)
                    pos += len

                    when (fieldNumber) {
                        1 -> {
                            // Could be either ExtensionList.extensions (message) OR NetworkExtensionStore.name (string)
                            val maybeEntry = parseProtobufExtensionEntry(slice)
                            if (maybeEntry != null && maybeEntry.pkg.contains('.')) {
                                if (!isDeprecatedStubExtension(maybeEntry.pkg, maybeEntry.name)) {
                                    entriesFromField1 += maybeEntry
                                }
                            } else if (storeName.isEmpty()) {
                                storeName = slice.toString(Charsets.UTF_8)
                            }
                        }
                        2 -> {
                            val maybeEntry = parseProtobufExtensionEntry(slice)
                            if (maybeEntry != null && maybeEntry.pkg.contains('.')) {
                                if (!isDeprecatedStubExtension(maybeEntry.pkg, maybeEntry.name)) {
                                    entriesFromField5 += maybeEntry
                                }
                            } else {
                                val str = slice.toString(Charsets.UTF_8)
                                if (str.startsWith("http://") || str.startsWith("https://")) {
                                    storeBaseUrl = str
                                }
                            }
                        }
                        3 -> {
                            val str = slice.toString(Charsets.UTF_8)
                            if (str.length == 64 && str.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                                fingerprints += str
                            } else {
                                extensionListUrl = str
                            }
                        }
                        4 -> {
                            // In v2 NetworkExtensionStore, field 4 is Contact (field 1 = website, field 2 = discord);
                            // in legacy format, field 4 is signingKeyFingerprints string
                            val contactWebsite = parseProtobufFirstStringField(slice, 1)
                            if (contactWebsite != null && (contactWebsite.startsWith("http://") || contactWebsite.startsWith("https://"))) {
                                if (storeBaseUrl.isEmpty()) storeBaseUrl = contactWebsite
                            } else {
                                fingerprints += slice.toString(Charsets.UTF_8)
                            }
                        }
                        5 -> {
                            parseProtobufExtensionEntry(slice)?.let { entry ->
                                if (!isDeprecatedStubExtension(entry.pkg, entry.name)) {
                                    entriesFromField5 += entry
                                }
                            }
                        }
                        101 -> {
                            // NetworkExtensionStore.extensionList (@ProtoNumber(101)) -> repeated @ProtoNumber(1) Extension
                            var innerPos = 0
                            val innerLimit = slice.size
                            while (innerPos < innerLimit) {
                                val innerTagPair = readVarint(slice, innerPos, innerLimit) ?: break
                                val innerTag = innerTagPair.first.toInt()
                                innerPos = innerTagPair.second
                                val innerField = innerTag ushr 3
                                val innerWire = innerTag and 0x07
                                when (innerWire) {
                                    0 -> {
                                        val iv = readVarint(slice, innerPos, innerLimit) ?: break
                                        innerPos = iv.second
                                    }
                                    1 -> innerPos = (innerPos + 8).coerceAtMost(innerLimit)
                                    2 -> {
                                        val ilPair = readVarint(slice, innerPos, innerLimit) ?: break
                                        val il = ilPair.first.toInt()
                                        innerPos = ilPair.second
                                        if (il < 0 || innerPos + il > innerLimit) break
                                        val extBytes = slice.copyOfRange(innerPos, innerPos + il)
                                        innerPos += il
                                        if (innerField == 1) {
                                            parseProtobufExtensionEntry(extBytes)?.let { entry ->
                                                if (!isDeprecatedStubExtension(entry.pkg, entry.name)) {
                                                    entriesFromField101 += entry
                                                }
                                            }
                                        }
                                    }
                                    5 -> innerPos = (innerPos + 4).coerceAtMost(innerLimit)
                                    else -> break
                                }
                            }
                        }
                        102 -> {
                            extensionListUrl = slice.toString(Charsets.UTF_8)
                        }
                    }
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> break
            }
        }

        val resolvedEntries = when {
            entriesFromField101.isNotEmpty() -> entriesFromField101
            entriesFromField5.isNotEmpty() -> entriesFromField5
            else -> entriesFromField1
        }
        val resolvedBase = storeBaseUrl.ifBlank { fallbackBaseUrl }
        val meta = if (storeName.isNotBlank() || fingerprints.isNotEmpty()) {
            ExternalRepoMeta(
                name = storeName.ifBlank { defaultExtensionStoreName(resolvedBase) },
                website = resolvedBase,
                signingKeyFingerprint = fingerprints.firstOrNull(),
            )
        } else {
            null
        }
        return ParsedProtobufResult(
            entries = resolvedEntries,
            baseUrl = resolvedBase,
            extensionListUrl = extensionListUrl,
            meta = meta,
        )
    }

    private fun parseProtobufFirstStringField(slice: ByteArray, targetField: Int): String? {
        var pos = 0
        val limit = slice.size
        while (pos < limit) {
            val tagPair = readVarint(slice, pos, limit) ?: return null
            val tag = tagPair.first.toInt()
            pos = tagPair.second
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (wireType) {
                0 -> {
                    val v = readVarint(slice, pos, limit) ?: return null
                    pos = v.second
                }
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val lenPair = readVarint(slice, pos, limit) ?: return null
                    val len = lenPair.first.toInt()
                    pos = lenPair.second
                    if (len < 0 || pos + len > limit) return null
                    val sub = slice.copyOfRange(pos, pos + len)
                    pos += len
                    if (fieldNumber == targetField) {
                        return sub.toString(Charsets.UTF_8)
                    }
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> return null
            }
        }
        return null
    }

    private fun parseProtobufExtensionEntry(slice: ByteArray): ExternalExtensionRepoEntry? {
        var name = ""
        var pkg = ""
        var apk = ""
        var apkUrl: String? = null
        var iconUrl: String? = null
        var rawField4 = ""
        var code = 1L
        var version = "1.0"
        var rawField7 = 0
        var hasResourcesSubmsg = false
        var hasReadme = 0
        var hasChangelog = 0
        val sources = mutableListOf<ExternalExtensionRepoSource>()

        var pos = 0
        val limit = slice.size
        while (pos < limit) {
            val tagPair = readVarint(slice, pos, limit) ?: break
            val tag = tagPair.first.toInt()
            pos = tagPair.second
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> {
                    val v = readVarint(slice, pos, limit) ?: break
                    pos = v.second
                    when (fieldNumber) {
                        5 -> code = v.first
                        7 -> rawField7 = v.first.toInt()
                        8 -> hasReadme = v.first.toInt()
                        9 -> hasChangelog = v.first.toInt()
                    }
                }
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val lenPair = readVarint(slice, pos, limit) ?: break
                    val len = lenPair.first.toInt()
                    pos = lenPair.second
                    if (len < 0 || pos + len > limit) break
                    val sub = slice.copyOfRange(pos, pos + len)
                    pos += len
                    when (fieldNumber) {
                        1 -> name = sub.toString(Charsets.UTF_8)
                        2 -> pkg = sub.toString(Charsets.UTF_8)
                        3 -> {
                            // In v2 NetworkExtensionStore.Extension, field 3 is Resources(1=apkUrl, 2=iconUrl);
                            // in legacy format, field 3 is a plain apk filename string.
                            if (sub.isNotEmpty() && sub[0].toInt() == 0x0a) {
                                hasResourcesSubmsg = true
                                val parsedApkUrl = parseProtobufFirstStringField(sub, 1).orEmpty()
                                val parsedIconUrl = parseProtobufFirstStringField(sub, 2)
                                apkUrl = parsedApkUrl.takeIf { it.isNotBlank() }
                                iconUrl = parsedIconUrl?.takeIf { it.isNotBlank() }
                                apk = parsedApkUrl.substringAfterLast('/').ifBlank { parsedApkUrl }
                            } else {
                                apk = sub.toString(Charsets.UTF_8)
                            }
                        }
                        4 -> rawField4 = sub.toString(Charsets.UTF_8)
                        6 -> version = sub.toString(Charsets.UTF_8)
                        8 -> {
                            // In v2 NetworkExtensionStore.Extension, field 8 is repeated Source(1=id, 2=name, 3=language, 4=homeUrl)
                            parseProtobufV2SourceEntry(sub, name)?.let { sources += it }
                        }
                        9, 10 -> {
                            parseProtobufSourceEntry(sub, name, rawField4.ifBlank { "all" })?.let { sources += it }
                        }
                    }
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> return null
            }
        }

        if (name.isBlank() || pkg.isBlank()) return null

        val sourceLangs = sources.map { it.lang.trim() }.filter { it.isNotBlank() }.toSet()
        val pkgLang = pkg.removePrefix("eu.kanade.tachiyomi.extension.")
            .removePrefix("eu.kanade.tachiyomi.novelextension.")
            .substringBefore('.')
            .takeIf { it.isNotBlank() && it.all(Char::isLetter) }
        val resolvedLang = when {
            rawField4.isNotBlank() && rawField4.first().isLetter() -> rawField4
            sourceLangs.size == 1 -> sourceLangs.first()
            sourceLangs.size > 1 -> "all"
            pkgLang != null -> pkgLang
            else -> "all"
        }
        val nsfw = if (hasResourcesSubmsg) {
            // In v2 ContentWarning: 0=UNSPECIFIED, 1=SAFE, 2=MIXED, 3=NSFW
            if (rawField7 >= 3) 1 else 0
        } else {
            if (rawField7 >= 1) 1 else 0
        }

        return ExternalExtensionRepoEntry(
            name = name,
            pkg = pkg,
            apk = apk,
            lang = resolvedLang,
            code = code,
            version = version,
            nsfw = nsfw,
            hasReadme = hasReadme,
            hasChangelog = hasChangelog,
            sources = sources,
            iconUrl = iconUrl,
            apkUrl = apkUrl,
        )
    }

    private fun parseProtobufV2SourceEntry(
        slice: ByteArray,
        fallbackName: String,
    ): ExternalExtensionRepoSource? {
        var id = 0L
        var name = ""
        var lang = ""
        var homeUrl = ""

        var pos = 0
        val limit = slice.size
        while (pos < limit) {
            val tagPair = readVarint(slice, pos, limit) ?: break
            val tag = tagPair.first.toInt()
            pos = tagPair.second
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> {
                    val v = readVarint(slice, pos, limit) ?: break
                    pos = v.second
                    if (fieldNumber == 1) id = v.first
                }
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val lenPair = readVarint(slice, pos, limit) ?: break
                    val len = lenPair.first.toInt()
                    pos = lenPair.second
                    if (len < 0 || pos + len > limit) break
                    val sub = slice.copyOfRange(pos, pos + len)
                    pos += len
                    when (fieldNumber) {
                        2 -> name = sub.toString(Charsets.UTF_8)
                        3 -> lang = sub.toString(Charsets.UTF_8)
                        4 -> homeUrl = sub.toString(Charsets.UTF_8)
                    }
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> return null
            }
        }

        val resolvedName = name.ifBlank { fallbackName }
        if (resolvedName.isBlank()) return null
        return ExternalExtensionRepoSource(
            name = resolvedName,
            lang = lang.ifBlank { "all" },
            id = if (id != 0L) id else (resolvedName.hashCode().toLong() and 0x7FFFFFFFL),
            baseUrl = homeUrl,
            versionId = 1,
        )
    }

    private fun parseProtobufSourceEntry(
        slice: ByteArray,
        fallbackName: String,
        fallbackLang: String,
    ): ExternalExtensionRepoSource? {
        var name = ""
        var lang = ""
        var id = 0L
        var baseUrl = ""
        var versionId = 1

        var pos = 0
        val limit = slice.size
        while (pos < limit) {
            val tagPair = readVarint(slice, pos, limit) ?: break
            val tag = tagPair.first.toInt()
            pos = tagPair.second
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> {
                    val v = readVarint(slice, pos, limit) ?: break
                    pos = v.second
                    when (fieldNumber) {
                        3 -> id = v.first
                        5 -> versionId = v.first.toInt()
                    }
                }
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val lenPair = readVarint(slice, pos, limit) ?: break
                    val len = lenPair.first.toInt()
                    pos = lenPair.second
                    if (len < 0 || pos + len > limit) break
                    val sub = slice.copyOfRange(pos, pos + len)
                    pos += len
                    when (fieldNumber) {
                        1 -> name = sub.toString(Charsets.UTF_8)
                        2 -> lang = sub.toString(Charsets.UTF_8)
                        4 -> baseUrl = sub.toString(Charsets.UTF_8)
                    }
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> return null
            }
        }

        val resolvedName = name.ifBlank { fallbackName }
        if (resolvedName.isBlank()) return null
        return ExternalExtensionRepoSource(
            name = resolvedName,
            lang = lang.ifBlank { fallbackLang },
            id = if (id != 0L) id else (resolvedName.hashCode().toLong() and 0x7FFFFFFFL),
            baseUrl = baseUrl,
            versionId = versionId,
        )
    }

    private fun readVarint(bytes: ByteArray, startPos: Int, limit: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = startPos
        while (pos < limit && shift < 64) {
            val b = bytes[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return result to pos
            }
            shift += 7
        }
        return null
    }

    private fun enrichEntriesWithBaseUrl(
        entries: List<ExternalExtensionRepoEntry>,
        baseUrl: String,
    ): List<ExternalExtensionRepoEntry> {
        val cleanBase = getBaseUrl(baseUrl)
        return entries.map { entry ->
            val resolvedIcon = entry.iconUrl?.takeIf { it.isNotBlank() }
                ?: "$cleanBase/icon/${entry.pkg}.png"
            val resolvedApk = entry.apkUrl?.takeIf { it.isNotBlank() }
                ?: if (entry.apk.startsWith("http://") || entry.apk.startsWith("https://")) {
                    entry.apk
                } else {
                    "$cleanBase/apk/${entry.apk.trimStart('/')}"
                }
            entry.copy(
                iconUrl = resolvedIcon,
                apkUrl = resolvedApk,
            )
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
            .header("Accept", "application/json, application/x-protobuf, application/octet-stream, */*")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} fetching $url")
            }
            return response.body?.bytes() ?: throw IllegalStateException("Empty body from $url")
        }
    }

    private fun maybeDecompressGzip(bytes: ByteArray): ByteArray {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrDefault(bytes)
        }
        return bytes
    }

    private fun getCacheFile(normalizedUrl: String): File {
        val key = stableExtensionStoreId(normalizedUrl)
        return File(cacheDir, "repo_$key.json")
    }

    private fun writeDiskCache(normalizedUrl: String, entries: List<ExternalExtensionRepoEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("name", entry.name)
                obj.put("pkg", entry.pkg)
                obj.put("apk", entry.apk)
                obj.put("lang", entry.lang)
                obj.put("code", entry.code)
                obj.put("version", entry.version)
                obj.put("nsfw", entry.nsfw)
                obj.put("hasReadme", entry.hasReadme)
                obj.put("hasChangelog", entry.hasChangelog)
                entry.iconUrl?.let { obj.put("iconUrl", it) }
                entry.apkUrl?.let { obj.put("apkUrl", it) }
                val sourcesArr = JSONArray()
                entry.sources.forEach { src ->
                    val srcObj = JSONObject()
                    srcObj.put("name", src.name)
                    srcObj.put("lang", src.lang)
                    srcObj.put("id", src.id)
                    srcObj.put("baseUrl", src.baseUrl)
                    srcObj.put("versionId", src.versionId)
                    sourcesArr.put(srcObj)
                }
                obj.put("sources", sourcesArr)
                arr.put(obj)
            }
            getCacheFile(normalizedUrl).writeText(arr.toString())
        }
    }

    private fun readDiskCache(normalizedUrl: String): List<ExternalExtensionRepoEntry>? {
        val file = getCacheFile(normalizedUrl)
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readText()
            if (raw.contains("eu.kanade.tachiyomi.extension.all.keiyoushi") || raw.contains("Outdated App")) {
                file.delete()
                return@runCatching null
            }
            val parsed = parseJsonArrayEntries(raw, getBaseUrl(normalizedUrl))
            if (parsed.isEmpty()) {
                file.delete()
                null
            } else {
                parsed
            }
        }.getOrNull()
    }

    private fun mapLnReaderLanguage(rawLang: String): String {
        val lower = rawLang.trim().lowercase(Locale.ROOT)
        return when {
            lower.startsWith("english") || lower == "en" -> "en"
            lower.startsWith("spanish") || lower.startsWith("español") || lower == "es" -> "es"
            lower.startsWith("portuguese") || lower.startsWith("português") || lower == "pt" -> "pt"
            lower.startsWith("french") || lower.startsWith("français") || lower == "fr" -> "fr"
            lower.startsWith("german") || lower.startsWith("deutsch") || lower == "de" -> "de"
            lower.startsWith("italian") || lower.startsWith("italiano") || lower == "it" -> "it"
            lower.startsWith("russian") || lower.startsWith("русский") || lower == "ru" -> "ru"
            lower.startsWith("japanese") || lower.startsWith("日本語") || lower == "ja" -> "ja"
            lower.startsWith("korean") || lower.startsWith("한국어") || lower == "ko" -> "ko"
            lower.startsWith("chinese") || lower.startsWith("中文") || lower == "zh" -> "zh"
            lower.startsWith("arabic") || lower.startsWith("العربية") || lower == "ar" -> "ar"
            lower.startsWith("indonesian") || lower == "id" -> "id"
            lower.startsWith("turkish") || lower.startsWith("türkçe") || lower == "tr" -> "tr"
            lower.startsWith("vietnamese") || lower.startsWith("tiếng việt") || lower == "vi" -> "vi"
            lower.startsWith("thai") || lower == "th" -> "th"
            lower.startsWith("polish") || lower == "pl" -> "pl"
            else -> lower.take(2).ifBlank { "en" }
        }
    }

    companion object {
        private const val MAX_INDEX_HOPS = 3

        fun defaultRepoHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
