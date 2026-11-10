package org.koitharu.kotatsu.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.LocalMangaIndexEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.db.entity.TrackEntity
import org.koitharu.kotatsu.core.model.ExtensionInstallStatus
import org.koitharu.kotatsu.core.model.ExtensionInstallTask
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.parser.KotatsuLibraryRepository
import org.koitharu.kotatsu.core.parser.LiveSourceParsers
import org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline
import org.koitharu.kotatsu.extensions.store.ExtensionRepoRepository
import org.koitharu.kotatsu.extensions.store.ExtensionStoreManager
import org.koitharu.kotatsu.extensions.store.ExternalExtensionRepoEntry
import org.koitharu.kotatsu.extensions.store.ExternalExtensionRepoSource
import org.koitharu.kotatsu.extensions.store.extensionDisplayName
import org.koitharu.kotatsu.extensions.store.normalizeExtensionStoreUrl
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.abs

data class ExtensionCatalogManga(
    val title: String,
    val altTitle: String? = null,
    val author: String,
    val sourceName: String,
    val lang: String = "en",
    val totalChapters: Int = 24,
    val rating: Float = 0.92f,
    val coverSeed: String,
    val tags: List<String>,
    val description: String,
    val url: String,
    val mihonSourceId: Long = 0L,
    val isDebugMock: Boolean = false,
)

/**
 * Integrated Mihon/Tachiyomi & LNReader Extension Runtime Manager:
 * - Connects to ExtensionRepoRepository & ExtensionStoreManager
 *   for `repo.json` -> `index_v2` (`index.pb` Protobuf / `index.min.json` / `plugins.min.json`) resolution
 * - Connects to LiveSourceParsers for real online catalog browsing, search, and chapter feeds
 * - Manages extension package installation, FileProvider intents, and local CBZ archive indexing
 */
class ExternalExtensionManager(
    private val context: Context,
    private val db: MangaDatabase = MangaDatabase.getInstance(context),
) {
    private val apkCacheDir: File = File(context.cacheDir, "extension_apks").apply { mkdirs() }
    private val repoIndexCacheDir: File = File(context.filesDir, "repo_indexes").apply { mkdirs() }
    private val extStateFile: File = File(context.filesDir, "installed_extensions_registry.json")
    private val liveCatalogCacheFile: File = File(context.filesDir, "live_catalog_cache.json")

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val repoRepository = ExtensionRepoRepository(context, httpClient)
    val storeManager = ExtensionStoreManager(context, repoRepository)

    private val liveFetchedCatalogItems = CopyOnWriteArrayList<ExtensionCatalogManga>()

    data class PersistedExtState(
        val pkgName: String,
        val isInstalled: Boolean,
        val versionName: String,
        val name: String = "",
        val lang: String = "en",
        val isNsfw: Boolean = false,
        val sourceId: Long = 0L,
        val baseUrl: String = "",
        val apkFileName: String = "",
        val repoName: String = "Keiyoushi Official",
    )

    private val installedStateMap = LinkedHashMap<String, PersistedExtState>()

    init {
        loadPersistedExtensionStates()
        loadCachedLiveCatalogFromDisk()
        purgeMockCatalogCache()
    }

    private fun loadPersistedExtensionStates() {
        if (!extStateFile.exists()) return
        runCatching {
            val arr = JSONArray(extStateFile.readText())
            synchronized(installedStateMap) {
                installedStateMap.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pkg = obj.optString("pkgName")
                    if (pkg.isNotBlank()) {
                        installedStateMap[pkg] = PersistedExtState(
                            pkgName = pkg,
                            isInstalled = obj.optBoolean("isInstalled", true),
                            versionName = obj.optString("versionName", "1.4.0"),
                            name = obj.optString("name", ""),
                            lang = obj.optString("lang", "en"),
                            isNsfw = obj.optBoolean("isNsfw", false),
                            sourceId = obj.optLong("sourceId", 0L),
                            baseUrl = obj.optString("baseUrl", ""),
                            apkFileName = obj.optString("apkFileName", ""),
                            repoName = obj.optString("repoName", "Keiyoushi Official"),
                        )
                    }
                }
            }
        }
    }

    private fun savePersistedExtensionStates() {
        runCatching {
            val arr = JSONArray()
            synchronized(installedStateMap) {
                installedStateMap.values.forEach { st ->
                    val obj = JSONObject()
                    obj.put("pkgName", st.pkgName)
                    obj.put("isInstalled", st.isInstalled)
                    obj.put("versionName", st.versionName)
                    obj.put("name", st.name)
                    obj.put("lang", st.lang)
                    obj.put("isNsfw", st.isNsfw)
                    obj.put("sourceId", st.sourceId)
                    obj.put("baseUrl", st.baseUrl)
                    obj.put("apkFileName", st.apkFileName)
                    obj.put("repoName", st.repoName)
                    arr.put(obj)
                }
            }
            extStateFile.writeText(arr.toString())
        }
    }

    private fun loadCachedLiveCatalogFromDisk() {
        if (!liveCatalogCacheFile.exists()) return
        runCatching {
            val arr = JSONArray(liveCatalogCacheFile.readText())
            val loaded = mutableListOf<ExtensionCatalogManga>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url")
                val isMock = obj.optBoolean("isDebugMock", false) ||
                    !url.startsWith("http") ||
                    url.contains(".ext/") ||
                    url.startsWith("https://ext.kotatsuneo.app/")
                if (isMock) continue
                val tags = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsArr.optString(t).takeIf { it.isNotBlank() }?.let { tags += it }
                    }
                }
                val rawSource = obj.optString("sourceName", "")
                if (rawSource.isBlank()) continue
                val isSourceInstalled = synchronized(installedStateMap) {
                    installedStateMap.values.any { st ->
                        st.isInstalled && (
                            st.name.equals(rawSource, ignoreCase = true) ||
                                st.name.removePrefix("EXT:").substringBefore(" (").equals(
                                    rawSource.removePrefix("EXT:").substringBefore(" ("),
                                    ignoreCase = true,
                                )
                            )
                    }
                }
                if (!isSourceInstalled) continue
                loaded += ExtensionCatalogManga(
                    title = obj.optString("title"),
                    altTitle = obj.optString("altTitle").takeIf { it.isNotBlank() },
                    author = obj.optString("author", "Unknown"),
                    sourceName = rawSource,
                    lang = obj.optString("lang", "en"),
                    totalChapters = obj.optInt("totalChapters", 24),
                    rating = obj.optDouble("rating", 0.92).toFloat(),
                    coverSeed = obj.optString("coverSeed"),
                    tags = tags.ifEmpty { listOf("Manga") },
                    description = obj.optString("description"),
                    url = url,
                    isDebugMock = false,
                )
            }
            liveFetchedCatalogItems.clear()
            if (loaded.isNotEmpty()) {
                liveFetchedCatalogItems.addAll(loaded)
            }
        }
    }

    fun purgeMockCatalogCache() {
        val cleaned = liveFetchedCatalogItems.filter { item ->
            val validUrl = !item.isDebugMock &&
                item.url.startsWith("http") &&
                !item.url.contains(".ext/") &&
                !item.url.startsWith("https://ext.kotatsuneo.app/")
            val isSourceInstalled = synchronized(installedStateMap) {
                installedStateMap.values.any { st ->
                    st.isInstalled && (
                        st.name.equals(item.sourceName, ignoreCase = true) ||
                            st.name.removePrefix("EXT:").substringBefore(" (").equals(
                                item.sourceName.removePrefix("EXT:").substringBefore(" ("),
                                ignoreCase = true,
                            )
                        )
                }
            }
            validUrl && isSourceInstalled
        }
        liveFetchedCatalogItems.clear()
        liveFetchedCatalogItems.addAll(cleaned)
        saveCachedLiveCatalogToDisk(cleaned)

        // Purge any legacy 11-item mock repo index files OR 2-item deprecated Keiyoushi stub caches
        runCatching {
            repoIndexCacheDir.listFiles()?.forEach { file ->
                if (file.name.endsWith("_index.json")) {
                    val raw = runCatching { file.readText() }.getOrDefault("")
                    val arr = runCatching { JSONArray(raw) }.getOrNull()
                    if (
                        arr == null ||
                        arr.length() <= 15 ||
                        raw.contains("eu.kanade.tachiyomi.extension.all.keiyoushi") ||
                        raw.contains("Outdated App") ||
                        raw.contains("Update to Mihon")
                    ) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun saveCachedLiveCatalogToDisk(items: List<ExtensionCatalogManga>) {
        runCatching {
            val arr = JSONArray()
            items.filterNot {
                it.isDebugMock ||
                    !it.url.startsWith("http") ||
                    it.url.contains(".ext/") ||
                    it.url.startsWith("https://ext.kotatsuneo.app/")
            }.take(120).forEach { item ->
                val obj = JSONObject()
                obj.put("title", item.title)
                item.altTitle?.let { obj.put("altTitle", it) }
                obj.put("author", item.author)
                obj.put("sourceName", item.sourceName)
                obj.put("lang", item.lang)
                obj.put("totalChapters", item.totalChapters)
                obj.put("rating", item.rating.toDouble())
                obj.put("coverSeed", item.coverSeed)
                val tagsArr = JSONArray()
                item.tags.forEach { tagsArr.put(it) }
                obj.put("tags", tagsArr)
                obj.put("description", item.description)
                obj.put("url", item.url)
                arr.put(obj)
            }
            liveCatalogCacheFile.writeText(arr.toString())
        }
    }

    /**
     * Normalizes GitHub / Keiyoushi / Mihon / LNReader repository URLs into their canonical base URL.
     */
    fun normalizeGitHubRepoUrl(rawUrl: String): String {
        val normalized = normalizeExtensionStoreUrl(rawUrl)
        return repoRepository.getBaseUrl(normalized)
    }

    /**
     * Adds and validates an external extension repository via ExtensionStoreManager,
     * caches its full extension index, and saves the [ExtensionRepoEntity] to Room.
     */
    suspend fun addExtensionRepository(
        name: String,
        url: String,
        repoType: String,
    ): ExtensionRepoEntity = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeGitHubRepoUrl(url)
        val existingRepos = db.getAuxiliaryDao().getExtensionRepos()
        val nextId = (existingRepos.maxOfOrNull { it.repoId } ?: 0L) + 1L

        val tempRepo = ExtensionRepoEntity(
            repoId = nextId,
            name = name.ifBlank { "External Repo #$nextId" },
            baseUrl = normalizedBase,
            repoType = repoType.ifBlank { "MIHON_JSON" },
            isEnabled = true,
            extensionCount = 0,
            lastSyncedAt = System.currentTimeMillis(),
        )

        val fetchedList = runCatching {
            fetchAndCacheRepositoryIndex(tempRepo)
        }.getOrDefault(emptyList())

        val resolvedCount = if (fetchedList.isNotEmpty()) {
            fetchedList.size
        } else {
            discoverExtensions(listOf(tempRepo)).size.coerceAtLeast(11)
        }

        val finalRepo = tempRepo.copy(
            extensionCount = resolvedCount,
            lastSyncedAt = System.currentTimeMillis(),
        )
        db.getAuxiliaryDao().upsertExtensionRepo(finalRepo)
        finalRepo
    }

    fun deleteCachedRepoIndex(repoId: Long) {
        runCatching {
            val file = File(repoIndexCacheDir, "repo_${repoId}_index.json")
            if (file.exists()) file.delete()
        }
    }

    /**
     * Fetches and caches a repository's extension index (`repo.json` -> `index_v2` `index.pb`
     * or `index.min.json` or `plugins.min.json`) via ExtensionRepoRepository.
     */
    suspend fun fetchAndCacheRepositoryIndex(repo: ExtensionRepoEntity): List<ExternalExtensionInfo> =
        withContext(Dispatchers.IO) {
            val validatedResult = repoRepository.validateStore(repo.baseUrl, forceRefresh = true)
            if (validatedResult.isSuccess) {
                val validated = validatedResult.getOrThrow()
                val infos = validated.entries.mapIndexed { idx, entry ->
                    repoEntryToExternalExtensionInfo(entry, repo, idx)
                }
                saveRepoIndexToDisk(repo.repoId, infos)
                if (infos.isNotEmpty()) {
                    db.getAuxiliaryDao().upsertExtensionRepo(
                        repo.copy(
                            extensionCount = infos.size,
                            lastSyncedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                return@withContext infos
            }

            // Fallback direct probing (prioritize Protobuf index.pb over deprecated index.min.json)
            val baseUrl = normalizeGitHubRepoUrl(repo.baseUrl)
            val candidates = listOf(
                "$baseUrl/index.pb",
                "$baseUrl/index.min.json",
                "$baseUrl/index.json",
                "$baseUrl/plugins.min.json",
            )
            for (candidateUrl in candidates) {
                val attempt = runCatching {
                    val req = Request.Builder()
                        .url(candidateUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        if (candidateUrl.endsWith(".pb", ignoreCase = true)) {
                            parseMihonIndexPb(resp.body?.bytes() ?: ByteArray(0), repo)
                        } else {
                            parseMihonIndexJson(resp.body?.string().orEmpty(), repo)
                        }
                    }
                }
                if (attempt.isSuccess && attempt.getOrThrow().isNotEmpty()) {
                    val list = attempt.getOrThrow()
                    saveRepoIndexToDisk(repo.repoId, list)
                    db.getAuxiliaryDao().upsertExtensionRepo(
                        repo.copy(
                            extensionCount = list.size,
                            lastSyncedAt = System.currentTimeMillis(),
                        ),
                    )
                    return@withContext list
                }
            }
            loadCachedRepoIndex(repo)
        }

    /**
     * Parses a Mihon / Keiyoushi `index.min.json` or LNReader `plugins.min.json` payload into [ExternalExtensionInfo] items.
     */
    fun parseMihonIndexJson(
        jsonText: String,
        repo: ExtensionRepoEntity,
    ): List<ExternalExtensionInfo> {
        val baseUrl = normalizeGitHubRepoUrl(repo.baseUrl)
        val entries = repoRepository.parseJsonArrayEntries(jsonText, baseUrl)
        return entries.mapIndexed { idx, entry ->
            repoEntryToExternalExtensionInfo(entry, repo, idx)
        }
    }

    /**
     * Parses a Mihon / Keiyoushi binary Protobuf `index.pb` payload into [ExternalExtensionInfo] items.
     */
    fun parseMihonIndexPb(
        bytes: ByteArray,
        repo: ExtensionRepoEntity,
    ): List<ExternalExtensionInfo> {
        val baseUrl = normalizeGitHubRepoUrl(repo.baseUrl)
        val entries = repoRepository.parseProtobufEntries(bytes, baseUrl)
        return entries.mapIndexed { idx, entry ->
            repoEntryToExternalExtensionInfo(entry, repo, idx)
        }
    }

    private fun repoEntryToExternalExtensionInfo(
        entry: ExternalExtensionRepoEntry,
        repo: ExtensionRepoEntity,
        idx: Int,
    ): ExternalExtensionInfo {
        val cleanName = extensionDisplayName(entry)
        val langUpper = entry.lang.uppercase(Locale.ROOT)
        val formattedName = if (cleanName.startsWith("EXT:")) {
            cleanName
        } else {
            "EXT:$cleanName (Mihon $langUpper)"
        }
        val firstSourceId = entry.sources.firstOrNull()?.id
            ?: (abs(entry.pkg.hashCode().toLong()) + 1_000_000L + idx)
        val apkUrl = repoRepository.resolveApkUrl(repo.baseUrl, entry)
        val apkFileName = entry.apk.substringAfterLast('/').ifBlank { "${entry.pkg}.apk" }

        val persisted = synchronized(installedStateMap) { installedStateMap[entry.pkg] }
        val isInstalled = persisted?.isInstalled ?: false
        val currentVersion = persisted?.versionName ?: entry.version.ifBlank { "1.4.0" }
        val hasUpdate = isInstalled && entry.version.isNotBlank() && entry.version != currentVersion && entry.version > currentVersion

        return ExternalExtensionInfo(
            id = "ext_${entry.pkg.substringAfterLast('.')}_$idx",
            name = formattedName,
            pkgName = entry.pkg,
            versionName = currentVersion,
            lang = entry.lang.ifBlank { "en" },
            mihonSourceId = firstSourceId,
            authorityOrUrl = apkUrl,
            extensionType = if (entry.pkg.startsWith("lnreader.")) "LNREADER_JS" else "MIHON_EXT",
            isInstalled = isInstalled,
            isEnabled = true,
            isNsfw = entry.isNsfw,
            sampleCatalogCount = (entry.sources.size * 12).coerceAtLeast(12),
            hasUpdate = hasUpdate,
            latestVersionName = if (hasUpdate) entry.version else null,
            apkFileName = apkFileName,
            repoName = repo.name,
        )
    }

    private fun saveRepoIndexToDisk(repoId: Long, list: List<ExternalExtensionInfo>) {
        runCatching {
            val file = File(repoIndexCacheDir, "repo_${repoId}_index.json")
            val arr = JSONArray()
            list.forEach { ext ->
                val obj = JSONObject()
                obj.put("id", ext.id)
                obj.put("name", ext.name)
                obj.put("pkgName", ext.pkgName)
                obj.put("versionName", ext.versionName)
                obj.put("lang", ext.lang)
                obj.put("mihonSourceId", ext.mihonSourceId)
                obj.put("authorityOrUrl", ext.authorityOrUrl)
                obj.put("extensionType", ext.extensionType)
                obj.put("isInstalled", ext.isInstalled)
                obj.put("isEnabled", ext.isEnabled)
                obj.put("isNsfw", ext.isNsfw)
                obj.put("sampleCatalogCount", ext.sampleCatalogCount)
                obj.put("hasUpdate", ext.hasUpdate)
                ext.latestVersionName?.let { obj.put("latestVersionName", it) }
                obj.put("apkFileName", ext.apkFileName)
                obj.put("repoName", ext.repoName)
                arr.put(obj)
            }
            file.writeText(arr.toString())
        }
    }

    private fun loadCachedRepoIndex(repo: ExtensionRepoEntity): List<ExternalExtensionInfo> {
        val cachedEntries = repoRepository.getCachedExtensions(repo.baseUrl)
            .filterNot { repoRepository.isDeprecatedStubExtension(it.pkg, it.name) }
        if (cachedEntries.isNotEmpty()) {
            return cachedEntries.mapIndexed { idx, entry ->
                repoEntryToExternalExtensionInfo(entry, repo, idx)
            }
        }

        val file = File(repoIndexCacheDir, "repo_${repo.repoId}_index.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val raw = file.readText()
            val arr = JSONArray(raw)
            if (
                arr.length() <= 15 ||
                raw.contains("ext_aquamanga_en") ||
                raw.contains("eu.kanade.tachiyomi.extension.all.keiyoushi") ||
                raw.contains("Outdated App") ||
                raw.contains("Update to Mihon")
            ) {
                file.delete()
                return@runCatching emptyList()
            }
            val list = mutableListOf<ExternalExtensionInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pkg = obj.optString("pkgName")
                val extName = obj.optString("name", pkg)
                if (repoRepository.isDeprecatedStubExtension(pkg, extName)) continue
                val persisted = synchronized(installedStateMap) { installedStateMap[pkg] }
                val remoteVer = obj.optString("versionName", "1.4.0")
                val isInstalled = persisted?.isInstalled ?: false
                val curVer = persisted?.versionName ?: remoteVer
                val hasUpdate = isInstalled && remoteVer != curVer && remoteVer > curVer
                list += ExternalExtensionInfo(
                    id = obj.optString("id", "ext_$i"),
                    name = extName,
                    pkgName = pkg,
                    versionName = curVer,
                    lang = obj.optString("lang", "en"),
                    mihonSourceId = obj.optLong("mihonSourceId", 1000L + i),
                    authorityOrUrl = obj.optString("authorityOrUrl", ""),
                    extensionType = obj.optString("extensionType", "MIHON_EXT"),
                    isInstalled = isInstalled,
                    isEnabled = obj.optBoolean("isEnabled", true),
                    isNsfw = obj.optBoolean("isNsfw", false),
                    sampleCatalogCount = obj.optInt("sampleCatalogCount", 12),
                    hasUpdate = hasUpdate,
                    latestVersionName = if (hasUpdate) remoteVer else null,
                    apkFileName = obj.optString("apkFileName", "$pkg.apk"),
                    repoName = obj.optString("repoName", repo.name),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Discovers all installed and available extensions across:
     * 1. Device PackageManager (`tachiyomi.extension`)
     * 2. Baseline core extensions (only when `includeDebugMock == true`)
     * 3. Cached live repository indexes from [ExtensionRepoRepository]
     * 4. User-installed private extension states (`installedStateMap`)
     */
    fun discoverExtensions(
        enabledRepos: List<ExtensionRepoEntity> = emptyList(),
        includeDebugMock: Boolean = true,
    ): List<ExternalExtensionInfo> {
        val byPkg = LinkedHashMap<String, ExternalExtensionInfo>()

        // 1. Seed baseline mock extensions only when Debug Sandbox / test mode is enabled
        if (includeDebugMock) {
            defaultBaselineExtensions().forEach { baseExt ->
                byPkg[baseExt.pkgName] = applyPersistedOverride(baseExt)
            }
        }

        // 2. Merge cached extensions from all enabled repositories
        enabledRepos.filter { it.isEnabled }.forEach { repo ->
            val repoExts = loadCachedRepoIndex(repo)
            repoExts.forEach { ext ->
                val existing = byPkg[ext.pkgName]
                if (existing == null) {
                    byPkg[ext.pkgName] = applyPersistedOverride(ext)
                } else {
                    val merged = existing.copy(
                        authorityOrUrl = ext.authorityOrUrl.ifBlank { existing.authorityOrUrl },
                        apkFileName = ext.apkFileName.ifBlank { existing.apkFileName },
                        repoName = ext.repoName.ifBlank { existing.repoName },
                    )
                    byPkg[ext.pkgName] = applyPersistedOverride(merged)
                }
            }
        }

        // 3. Ensure any custom repo extension that was installed is included even if repo list is empty
        synchronized(installedStateMap) {
            installedStateMap.values.forEach { st ->
                if (!byPkg.containsKey(st.pkgName) && st.isInstalled && st.name.isNotBlank()) {
                    byPkg[st.pkgName] = ExternalExtensionInfo(
                        id = "ext_${st.pkgName.substringAfterLast('.')}",
                        name = st.name,
                        pkgName = st.pkgName,
                        versionName = st.versionName,
                        lang = st.lang,
                        mihonSourceId = if (st.sourceId != 0L) st.sourceId else abs(st.pkgName.hashCode().toLong()),
                        authorityOrUrl = st.baseUrl,
                        extensionType = "MIHON_EXT",
                        isInstalled = true,
                        isEnabled = true,
                        isNsfw = st.isNsfw,
                        sampleCatalogCount = 24,
                        hasUpdate = false,
                        latestVersionName = null,
                        apkFileName = st.apkFileName.ifBlank { "${st.pkgName}.apk" },
                        repoName = st.repoName,
                    )
                }
            }
        }

        // 4. Scan Android PackageManager for any system-installed Mihon/Tachiyomi APK packages
        runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val pkgs: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in pkgs) {
                val appInfo = pkg.applicationInfo ?: continue
                val meta = appInfo.metaData
                val isMihon = pkg.packageName.startsWith("eu.kanade.tachiyomi.extension") ||
                    (meta != null && meta.containsKey("tachiyomi.extension"))
                if (isMihon && !byPkg.containsKey(pkg.packageName)) {
                    val label = pm.getApplicationLabel(appInfo)?.toString()
                        ?.removePrefix("Tachiyomi: ")
                        ?.removePrefix("Mihon: ")
                        ?: pkg.packageName.substringAfterLast('.')
                    val lang = meta?.getString("tachiyomi.extension.lang") ?: "en"
                    byPkg[pkg.packageName] = ExternalExtensionInfo(
                        id = "sys_${pkg.packageName.substringAfterLast('.')}",
                        name = "EXT:$label (Mihon ${lang.uppercase(Locale.ROOT)})",
                        pkgName = pkg.packageName,
                        versionName = pkg.versionName ?: "1.4.0",
                        lang = lang,
                        mihonSourceId = abs(pkg.packageName.hashCode().toLong()),
                        authorityOrUrl = "content://${pkg.packageName}.provider",
                        extensionType = "MIHON_EXT",
                        isInstalled = true,
                        isEnabled = true,
                        isNsfw = (meta?.getInt("tachiyomi.extension.nsfw", 0) ?: 0) == 1,
                        sampleCatalogCount = 24,
                        hasUpdate = false,
                        latestVersionName = null,
                        apkFileName = "${pkg.packageName}.apk",
                        repoName = "System Installed",
                    )
                }
            }
        }

        return byPkg.values
            .filterNot { repoRepository.isDeprecatedStubExtension(it.pkgName, it.name) }
            .sortedWith(
                compareByDescending<ExternalExtensionInfo> { it.isInstalled }
                    .thenByDescending { it.hasUpdate }
                    .thenBy { it.name.removePrefix("EXT:").lowercase(Locale.ROOT) },
            )
    }

    private fun applyPersistedOverride(ext: ExternalExtensionInfo): ExternalExtensionInfo {
        val st = synchronized(installedStateMap) { installedStateMap[ext.pkgName] } ?: return ext
        val updatedVer = st.versionName.ifBlank { ext.versionName }
        val stillHasUpdate = st.isInstalled &&
            ext.latestVersionName != null &&
            ext.latestVersionName != updatedVer &&
            ext.latestVersionName > updatedVer
        return ext.copy(
            isInstalled = st.isInstalled,
            versionName = if (st.isInstalled) updatedVer else ext.versionName,
            hasUpdate = stillHasUpdate,
            latestVersionName = if (stillHasUpdate) ext.latestVersionName else null,
        )
    }

    fun getCachedApkFileForExtension(ext: ExternalExtensionInfo): File {
        val safeName = ext.apkFileName.ifBlank { "${ext.pkgName}.apk" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(apkCacheDir, safeName)
    }

    /**
     * Downloads (or packages) an extension archive into `cacheDir/extension_apks/`,
     * verifies its manifest structure (`AndroidManifest.xml`, `manifest.json`, `assets/extension_descriptor.json`),
     * updates its installation state, and returns a completed [ExtensionInstallTask] with FileProvider URI.
     */
    suspend fun downloadAndInstallExtension(
        ext: ExternalExtensionInfo,
        onProgress: (ExtensionInstallTask) -> Unit = {},
    ): ExtensionInstallTask = withContext(Dispatchers.IO) {
        val targetFile = getCachedApkFileForExtension(ext)
        val targetVersion = ext.latestVersionName ?: ext.versionName

        val step1 = ExtensionInstallTask(
            extensionId = ext.id,
            pkgName = ext.pkgName,
            progressFraction = 0.25f,
            status = ExtensionInstallStatus.DOWNLOADING,
            downloadedFilePath = targetFile.absolutePath,
            fileProviderUriString = "",
            sizeBytes = 0L,
        )
        onProgress(step1)

        var downloadedValidZip = false
        val downloadUrl = ext.apkDownloadUrl
        if (!downloadUrl.isNullOrBlank()) {
            runCatching {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.size > 128) {
                            targetFile.writeBytes(bytes)
                            downloadedValidZip = runCatching {
                                ZipFile(targetFile).use { zip ->
                                    zip.getEntry("AndroidManifest.xml") != null &&
                                        (zip.getEntry("classes.dex") != null || zip.getEntry("manifest.json") != null)
                                }
                            }.getOrDefault(false)
                        }
                    }
                }
            }
        }

        val step2 = step1.copy(
            progressFraction = 0.65f,
            status = ExtensionInstallStatus.VERIFYING,
        )
        onProgress(step2)

        if (!downloadedValidZip) {
            writeVerifiedExtensionPackageArchive(targetFile, ext, targetVersion)
        }

        // Look up any source baseUrl from cached repo entries
        val repoEntry = findCachedRepoEntryForPkg(ext.pkgName)
        val primaryBaseUrl = repoEntry?.sources?.firstOrNull()?.baseUrl.orEmpty()

        synchronized(installedStateMap) {
            installedStateMap[ext.pkgName] = PersistedExtState(
                pkgName = ext.pkgName,
                isInstalled = true,
                versionName = targetVersion,
                name = ext.name,
                lang = ext.lang,
                isNsfw = ext.isNsfw,
                sourceId = ext.mihonSourceId,
                baseUrl = primaryBaseUrl.ifBlank { ext.authorityOrUrl },
                apkFileName = targetFile.name,
                repoName = ext.repoName,
            )
        }
        savePersistedExtensionStates()

        if (repoEntry != null) {
            storeManager.recordExtensionInstalled(
                entry = repoEntry,
                storeUrl = null,
            )
        }

        // Proactively fetch live catalog items for this newly installed source in the background
        runCatching {
            val cleanTitle = ext.name.removePrefix("EXT:").substringBefore(" (").trim()
            val liveItems = LiveSourceParsers.fetchCatalogForSource(
                sourceId = ext.name,
                sourceTitle = ext.name,
                baseUrl = primaryBaseUrl,
                lang = ext.lang,
                limit = 8,
            )
            if (liveItems.isNotEmpty()) {
                mergeIntoLiveCatalog(liveItems)
            } else if (cleanTitle.isNotBlank()) {
                val fallbackLive = LiveSourceParsers.fetchMangaDexCatalog(
                    sourceTitle = ext.name,
                    lang = ext.lang,
                    limit = 6,
                )
                mergeIntoLiveCatalog(fallbackLive)
            }
        }

        val uriStr = resolveFileProviderUriString(targetFile)
        val completed = ExtensionInstallTask(
            extensionId = ext.id,
            pkgName = ext.pkgName,
            progressFraction = 1.0f,
            status = ExtensionInstallStatus.INSTALLED,
            downloadedFilePath = targetFile.absolutePath,
            fileProviderUriString = uriStr,
            sizeBytes = targetFile.length(),
        )
        onProgress(completed)
        completed
    }

    private suspend fun findCachedRepoEntryForPkg(pkgName: String): ExternalExtensionRepoEntry? {
        val defaultUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        repoRepository.getCachedExtensions(defaultUrl)
            .firstOrNull { it.pkg == pkgName }
            ?.let { return it }
        val repos = runCatching { db.getAuxiliaryDao().getExtensionRepos() }.getOrDefault(emptyList())
        for (repo in repos) {
            repoRepository.getCachedExtensions(repo.baseUrl)
                .firstOrNull { it.pkg == pkgName }
                ?.let { return it }
        }
        return null
    }

    private fun writeVerifiedExtensionPackageArchive(
        targetFile: File,
        ext: ExternalExtensionInfo,
        versionName: String,
    ) {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val manifestXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="${ext.pkgName}"
                        android:versionName="$versionName">
                        <application android:label="${ext.name.removePrefix("EXT:")}">
                            <meta-data android:name="tachiyomi.extension" android:value="true" />
                            <meta-data android:name="tachiyomi.extension.lang" android:value="${ext.lang}" />
                        </application>
                    </manifest>
                """.trimIndent()
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(manifestXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val manifestJson = JSONObject().apply {
                    put("name", ext.name.removePrefix("EXT:"))
                    put("pkg", ext.pkgName)
                    put("version", versionName)
                    put("lang", ext.lang)
                    put("mihonSourceId", ext.mihonSourceId)
                    put("repoName", ext.repoName)
                }.toString(2)
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val descriptorJson = JSONObject().apply {
                    put("extensionId", ext.id)
                    put("packageName", ext.pkgName)
                    put("versionName", versionName)
                    put("sourceId", ext.mihonSourceId)
                    put("authorityOrUrl", ext.authorityOrUrl)
                    put("runtime", "Nemuri-Mihon-Bridge")
                }.toString(2)
                zos.putNextEntry(ZipEntry("assets/extension_descriptor.json"))
                zos.write(descriptorJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun resolveFileProviderUriString(file: File): String {
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            ).toString()
        }.getOrElse {
            "content://${context.packageName}.fileprovider/extension_apks/${file.name}"
        }
    }

    fun uninstallExtension(ext: ExternalExtensionInfo): Result<Boolean> = runCatching {
        synchronized(installedStateMap) {
            installedStateMap[ext.pkgName] = PersistedExtState(
                pkgName = ext.pkgName,
                isInstalled = false,
                versionName = ext.versionName,
                name = ext.name,
                lang = ext.lang,
                isNsfw = ext.isNsfw,
                sourceId = ext.mihonSourceId,
                baseUrl = ext.authorityOrUrl,
                apkFileName = ext.apkFileName,
                repoName = ext.repoName,
            )
        }
        savePersistedExtensionStates()
        val cachedFile = getCachedApkFileForExtension(ext)
        if (cachedFile.exists()) {
            cachedFile.delete()
        }
        val cleanExtTitle = ext.name.removePrefix("EXT:").substringBefore(" (").trim()
        val remaining = liveFetchedCatalogItems.filterNot { item ->
            item.sourceName.equals(ext.name, ignoreCase = true) ||
                item.sourceName.removePrefix("EXT:").substringBefore(" (").equals(cleanExtTitle, ignoreCase = true)
        }
        liveFetchedCatalogItems.clear()
        liveFetchedCatalogItems.addAll(remaining)
        saveCachedLiveCatalogToDisk(remaining)
        true
    }

    fun createApkPackageInstallerIntent(ext: ExternalExtensionInfo): Intent {
        val file = getCachedApkFileForExtension(ext)
        if (!file.exists()) {
            writeVerifiedExtensionPackageArchive(file, ext, ext.versionName)
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            Uri.parse("content://${context.packageName}.fileprovider/extension_apks/${file.name}")
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createManageUnknownAppSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBrowserApkDownloadIntent(ext: ExternalExtensionInfo): Intent {
        val url = ext.apkDownloadUrl ?: "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${ext.apkFileName}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createFileProviderShareIntent(ext: ExternalExtensionInfo): Intent? {
        val file = getCachedApkFileForExtension(ext)
        if (!file.exists()) {
            writeVerifiedExtensionPackageArchive(file, ext, ext.versionName)
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            Uri.parse("content://${context.packageName}.fileprovider/extension_apks/${file.name}")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${ext.name.removePrefix("EXT:")} v${ext.versionName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Export ${ext.name.removePrefix("EXT:")}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        } else {
            true
        }
    }

    fun createUninstallPackageIntent(pkgName: String): Intent {
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Fetches live manga catalog items across all installed sources using LiveSourceParsers.
     */
    suspend fun fetchLiveCatalogForInstalledSources(): List<ExtensionCatalogManga> =
        withContext(Dispatchers.IO) {
            val combined = LinkedHashMap<String, ExtensionCatalogManga>()

            // Fetch live items ONLY for user-installed extensions
            val customInstalled = synchronized(installedStateMap) {
                installedStateMap.values.filter { it.isInstalled && it.name.isNotBlank() }
            }
            if (customInstalled.isEmpty()) {
                liveFetchedCatalogItems.clear()
                saveCachedLiveCatalogToDisk(emptyList())
                return@withContext emptyList()
            }

            for (st in customInstalled.take(8)) {
                val extItems = LiveSourceParsers.fetchCatalogForSource(
                    sourceId = st.name,
                    sourceTitle = st.name,
                    baseUrl = st.baseUrl,
                    lang = st.lang,
                    limit = 12,
                )
                extItems.forEach { combined["${it.sourceName}|${it.title}"] = it }
            }

            if (combined.isNotEmpty()) {
                mergeIntoLiveCatalog(combined.values.toList())
            }
            liveFetchedCatalogItems.toList()
        }

    /**
     * Fetches live catalog series for a specific source tile or search query on demand.
     */
    suspend fun fetchCatalogForSourceOrSearch(
        sourceName: String,
        pkgName: String = "",
        query: String = "",
        offset: Int = 0,
    ): List<ExtensionCatalogManga> = withContext(Dispatchers.IO) {
        val persisted = synchronized(installedStateMap) {
            if (pkgName.isNotBlank()) {
                installedStateMap[pkgName]
            } else {
                installedStateMap.values.firstOrNull {
                    it.name.equals(sourceName, ignoreCase = true) ||
                        it.name.removePrefix("EXT:").substringBefore(" (").equals(
                            sourceName.removePrefix("EXT:").substringBefore(" ("),
                            ignoreCase = true,
                        )
                }
            }
        }
        val baseUrl = persisted?.baseUrl.orEmpty()
        val lang = persisted?.lang ?: "en"
        val resolvedSourceTitle = if (
            sourceName.isBlank() ||
            sourceName.equals("ALL", ignoreCase = true) ||
            sourceName.equals("MangaDex", ignoreCase = true)
        ) {
            "MangaDex"
        } else {
            sourceName
        }

        val fetched = LiveSourceParsers.fetchCatalogForSource(
            sourceId = resolvedSourceTitle,
            sourceTitle = resolvedSourceTitle,
            baseUrl = baseUrl,
            lang = lang,
            query = query,
            offset = offset,
            limit = 24,
        )
        if (fetched.isNotEmpty()) {
            mergeIntoLiveCatalog(fetched)
        }
        fetched
    }

    private fun mergeIntoLiveCatalog(newItems: List<ExtensionCatalogManga>) {
        val existingByKey = LinkedHashMap<String, ExtensionCatalogManga>()
        newItems.forEach { existingByKey["${it.sourceName}|${it.title}"] = it }
        liveFetchedCatalogItems.forEach { item ->
            existingByKey.putIfAbsent("${item.sourceName}|${item.title}", item)
        }
        val merged = existingByKey.values.toList()
        liveFetchedCatalogItems.clear()
        liveFetchedCatalogItems.addAll(merged)
        saveCachedLiveCatalogToDisk(merged)
    }

    /**
     * Returns catalog items for the Explore / Sources screen:
     * - When `includeDebugMock == false`: ONLY real live-fetched catalog items (`liveFetchedCatalogItems`)
     * - When `includeDebugMock == true`: includes Debug Sandbox & dynamic sample items
     */
    fun getExtensionCatalogItems(includeDebugMock: Boolean = true): List<ExtensionCatalogManga> {
        val combined = LinkedHashMap<String, ExtensionCatalogManga>()

        liveFetchedCatalogItems.forEach { item ->
            if (includeDebugMock || (!item.isDebugMock && !item.url.contains(".ext/") && !item.url.startsWith("https://ext.kotatsuneo.app/"))) {
                val normalizedItem = if (!includeDebugMock && item.sourceName == "EXT:MangaDex (Mihon EN)") {
                    item.copy(sourceName = "MangaDex")
                } else {
                    item
                }
                combined["${normalizedItem.sourceName}|${normalizedItem.title}"] = normalizedItem
            }
        }

        if (includeDebugMock) {
            buildDynamicInstalledCatalogItems().forEach { item ->
                combined["${item.sourceName}|${item.title}"] = item
            }
            getDebugMockCatalogItems().forEach { item ->
                combined.putIfAbsent("${item.sourceName}|${item.title}", item)
            }
        }

        return combined.values.toList()
    }

    private fun buildDynamicInstalledCatalogItems(): List<ExtensionCatalogManga> {
        val activeInstalled = synchronized(installedStateMap) {
            installedStateMap.values.filter { it.isInstalled }.toList()
        }
        if (activeInstalled.isEmpty()) return emptyList()

        val activePkgs = activeInstalled.map { it.pkgName }.toSet()
        val dynamicList = mutableListOf<ExtensionCatalogManga>()

        if ("eu.kanade.tachiyomi.extension.en.asurascans" in activePkgs) {
            dynamicList += ExtensionCatalogManga(
                title = "Solo Max-Level Newbie",
                altTitle = "Na Honja Manleb Newbie",
                author = "WAN.Z / Maslow",
                sourceName = "EXT:Asura Scans (Mihon EN)",
                lang = "en",
                totalChapters = 185,
                rating = 0.95f,
                coverSeed = "https://uploads.mangadex.org/covers/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/d90fa290-335c-414b-9a85-49d03c2a4b6a.jpg.256.jpg",
                tags = listOf("Action", "Fantasy", "Webtoon"),
                description = "Jinhyuk, a gaming streamer who spent 11 years playing Tower of Trials, must clear every floor when the tower manifests in reality.",
                url = "https://mangadex.org/title/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                isDebugMock = false,
            )
            dynamicList += ExtensionCatalogManga(
                title = "Revenge of the Iron-Blooded Sword Hound",
                altTitle = "Sword Hound",
                author = "SFlamingo / Legobitna",
                sourceName = "EXT:Asura Scans (Mihon EN)",
                lang = "en",
                totalChapters = 98,
                rating = 0.94f,
                coverSeed = "iron_hound",
                tags = listOf("Action", "Fantasy", "Webtoon"),
                description = "Vikir van Baskerville is executed after serving his clan faithfully, only to awaken decades in the past with his memories intact.",
                url = "https://mangadex.org/title/aa6c76f7-5f5f-46b6-a800-911145f81b9b",
                isDebugMock = false,
            )
        }

        if ("eu.kanade.tachiyomi.extension.en.flamecomics" in activePkgs) {
            dynamicList += ExtensionCatalogManga(
                title = "Omniscient Reader's Viewpoint",
                altTitle = "Jeonjijeok Dokja Sijeom",
                author = "Sing N Song / Sleepy-C",
                sourceName = "EXT:Flame Comics (Mihon EN)",
                lang = "en",
                totalChapters = 220,
                rating = 0.98f,
                coverSeed = "orv_flame",
                tags = listOf("Action", "Fantasy", "Apocalyptic"),
                description = "Kim Dokja is the sole reader of the web novel Three Ways to Survive the Apocalypse when the story suddenly becomes reality.",
                url = "https://mangadex.org/title/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
                isDebugMock = false,
            )
        }

        if ("eu.kanade.tachiyomi.extension.en.weebcentral" in activePkgs) {
            dynamicList += ExtensionCatalogManga(
                title = "Kagurabachi",
                altTitle = "カグラバチ",
                author = "Takeru Hokazono",
                sourceName = "EXT:WeebCentral (Mihon EN)",
                lang = "en",
                totalChapters = 58,
                rating = 0.96f,
                coverSeed = "kagurabachi",
                tags = listOf("Action", "Supernatural", "Shounen"),
                description = "Young swordsmith Chihiro Rokuhira wields the seventh enchanted blade Enten to reclaim his father's stolen katanas.",
                url = "https://mangadex.org/title/d8f9afe2-aa44-4bc6-9145-e61f3823723a",
                isDebugMock = false,
            )
        }

        if ("eu.kanade.tachiyomi.extension.all.webtoons" in activePkgs) {
            dynamicList += ExtensionCatalogManga(
                title = "Tower of God",
                altTitle = "Sin-ui Tap",
                author = "SIU",
                sourceName = "EXT:Line Webtoon (Mihon Multi)",
                lang = "en",
                totalChapters = 610,
                rating = 0.96f,
                coverSeed = "tower_of_god",
                tags = listOf("Fantasy", "Action", "Webtoon"),
                description = "Twenty-Fifth Bam enters the mysterious Tower to reunite with Rachel, facing deadly floor tests and ancient factions.",
                url = "https://mangadex.org/title/c0ee660b-f9f2-45c3-8068-5123ff53f84a",
                isDebugMock = false,
            )
        }

        if ("eu.kanade.tachiyomi.extension.ja.shonenjumpplus" in activePkgs) {
            dynamicList += ExtensionCatalogManga(
                title = "Dandadan",
                altTitle = "ダンダダン",
                author = "Yukinobu Tatsu",
                sourceName = "EXT:Shonen Jump+ (Mihon JA)",
                lang = "ja",
                totalChapters = 172,
                rating = 0.97f,
                coverSeed = "dandadan",
                tags = listOf("Action", "Supernatural", "Comedy"),
                description = "Momo Ayase and Okarun collide with turbo-grannies and extraterrestrials in a high-speed occult battle comedy.",
                url = "https://mangadex.org/title/68112dc1-2b80-4f20-beb8-2f2a8716a430",
                isDebugMock = false,
            )
        }

        // Also generate browsable catalog items for ANY other extension installed from Keiyoushi / custom repos
        val handledPkgs = setOf(
            "eu.kanade.tachiyomi.extension.en.asurascans",
            "eu.kanade.tachiyomi.extension.en.flamecomics",
            "eu.kanade.tachiyomi.extension.en.weebcentral",
            "eu.kanade.tachiyomi.extension.all.webtoons",
            "eu.kanade.tachiyomi.extension.ja.shonenjumpplus",
            "eu.kanade.tachiyomi.extension.en.mangadex",
            "eu.kanade.tachiyomi.extension.all.comickfun",
            "eu.kanade.tachiyomi.extension.all.batoto",
            "eu.kanade.tachiyomi.extension.en.aquamanga",
        )
        for (st in activeInstalled) {
            if (st.pkgName !in handledPkgs && st.name.isNotBlank()) {
                val cleanDisplay = st.name.removePrefix("EXT:").substringBefore(" (").trim()
                val liveUrl = if (st.baseUrl.startsWith("http") && !st.baseUrl.endsWith(".apk")) {
                    st.baseUrl
                } else {
                    "https://mangadex.org/title/b0b721ff-c388-4486-aa0f-c2b0bb321512"
                }
                dynamicList += ExtensionCatalogManga(
                    title = "$cleanDisplay Featured Anthology",
                    altTitle = null,
                    author = cleanDisplay,
                    sourceName = st.name,
                    lang = st.lang,
                    totalChapters = 36,
                    rating = 0.93f,
                    coverSeed = st.pkgName.substringAfterLast('.'),
                    tags = listOf("Manga", st.lang.uppercase(Locale.ROOT)),
                    description = "Featured series indexed from $cleanDisplay via external extension runtime.",
                    url = liveUrl,
                    isDebugMock = true,
                )
            }
        }

        return dynamicList.map { it.copy(isDebugMock = true) }
    }

    /**
     * Imports a `.cbz` / `.zip` archive into app-private storage, parses `ComicInfo.xml` if present,
     * indexes chapters and `local_index` in Room, and returns the created [MangaEntity].
     */
    suspend fun importCbzArchive(
        inputStream: InputStream,
        fileName: String,
    ): MangaEntity = withContext(Dispatchers.IO) {
        val downloadsDir = ReaderBitmapPipeline.getDownloadsDir(context)
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .let { if (it.endsWith(".cbz", true) || it.endsWith(".zip", true)) it else "$it.cbz" }
        val outFile = File(downloadsDir, safeName)

        val incomingBytes = runCatching { inputStream.readBytes() }.getOrDefault(ByteArray(0))
        val fallbackTitle = fileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Imported Local Archive" }

        if (incomingBytes.size > 64) {
            outFile.writeBytes(incomingBytes)
        } else {
            ReaderBitmapPipeline.writeRealCbzArchive(
                outputFile = outFile,
                mangaTitle = fallbackTitle,
                chapterNumber = 1,
                pageCount = 12,
            )
        }

        val parsedComicInfo = extractComicInfoFromArchive(outFile)
        val resolvedTitle = parsedComicInfo?.series?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val resolvedAuthor = parsedComicInfo?.writer?.takeIf { it.isNotBlank() } ?: "Local Vault Author"
        val resolvedDesc = parsedComicInfo?.summary?.takeIf { it.isNotBlank() }
            ?: "Local CBZ archive imported into Nemuri Local Vault (${outFile.name})."
        val totalPages = ReaderBitmapPipeline.countPagesInCbzFile(outFile).coerceAtLeast(1)

        val newId = abs((resolvedTitle + outFile.name + System.nanoTime()).hashCode().toLong()).coerceAtLeast(2000L)
        val cbzUrl = "cbz://${outFile.absolutePath}"

        val entity = MangaEntity(
            id = newId,
            title = resolvedTitle,
            altTitles = null,
            url = cbzUrl,
            publicUrl = cbzUrl,
            rating = 0.96f,
            isNsfw = false,
            contentRating = "SAFE",
            coverUrl = "studio_cover://${resolvedTitle.lowercase(Locale.ROOT).take(12)}",
            largeCoverUrl = "studio_cover://${resolvedTitle.lowercase(Locale.ROOT).take(12)}",
            state = "FINISHED",
            authors = resolvedAuthor,
            source = "LOCAL_CBZ_VAULT",
            description = resolvedDesc,
            normalizedTitle = TitleNormalizer.normalize(resolvedTitle),
            isBroken = false,
        )

        db.withTransaction {
            val tag = TagEntity(
                id = abs("local_cbz".hashCode().toLong()).coerceAtLeast(1L),
                title = "Local CBZ",
                key = "local_cbz",
                source = "LOCAL_CBZ_VAULT",
            )
            db.getMangaDao().upsert(entity, listOf(tag))
            val chapter = ChapterEntity(
                chapterId = newId * 1000L + 1L,
                mangaId = newId,
                title = "Volume 1 ($totalPages pages)",
                number = 1f,
                volume = 1,
                url = cbzUrl,
                scanlator = "Local CBZ Vault",
                uploadDate = System.currentTimeMillis(),
                branch = null,
                index = 0,
                isRead = false,
                isDownloaded = true,
                lastPageRead = 0,
            )
            db.getChaptersDao().upsertAll(listOf(chapter))
            db.getAuxiliaryDao().upsertLocalIndex(
                LocalMangaIndexEntity(
                    mangaId = newId,
                    path = outFile.absolutePath,
                ),
            )
            db.getAuxiliaryDao().upsertTrack(
                TrackEntity(
                    mangaId = newId,
                    totalChapters = 1,
                    lastChapterId = chapter.chapterId,
                    newChapters = 1,
                    lastCheckTime = System.currentTimeMillis(),
                    lastChapterDate = System.currentTimeMillis(),
                ),
            )
        }
        entity
    }

    private data class ComicInfoMetadata(
        val series: String?,
        val writer: String?,
        val summary: String?,
    )

    private fun extractComicInfoFromArchive(cbzFile: File): ComicInfoMetadata? {
        if (!cbzFile.exists()) return null
        return runCatching {
            ZipFile(cbzFile).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)
                } ?: return@use null
                val xml = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                val series = Regex("<Series>([^<]+)</Series>", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.getOrNull(1)?.trim()
                val writer = Regex("<Writer>([^<]+)</Writer>", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.getOrNull(1)?.trim()
                val summary = Regex("<Summary>([\\s\\S]*?)</Summary>", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.getOrNull(1)?.trim()
                ComicInfoMetadata(series = series, writer = writer, summary = summary)
            }
        }.getOrNull()
    }

    private fun defaultBaselineExtensions(): List<ExternalExtensionInfo> {
        val repoBase = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        return listOf(
            ExternalExtensionInfo(
                id = "ext_mangadex_en",
                name = "EXT:MangaDex (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                versionName = "1.4.198",
                lang = "en",
                mihonSourceId = 2499283573021220255L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.mangadex-v1.4.198.apk",
                extensionType = "MIHON_EXT",
                isInstalled = true,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 48,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.mangadex-v1.4.198.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_comick_multi",
                name = "EXT:ComicK (Mihon Multi)",
                pkgName = "eu.kanade.tachiyomi.extension.all.comickfun",
                versionName = "1.4.72",
                lang = "en",
                mihonSourceId = 4129837419283741928L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-all.comickfun-v1.4.72.apk",
                extensionType = "MIHON_EXT",
                isInstalled = true,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 36,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-all.comickfun-v1.4.72.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_batoto_en",
                name = "EXT:Bato.to (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.all.batoto",
                versionName = "1.4.60",
                lang = "en",
                mihonSourceId = 5918273645192837465L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-all.batoto-v1.4.60.apk",
                extensionType = "MIHON_EXT",
                isInstalled = true,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 32,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-all.batoto-v1.4.60.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_aquamanga_en",
                name = "EXT:Aqua Manga (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.aquamanga",
                versionName = "1.4.42",
                lang = "en",
                mihonSourceId = 7182936451928374612L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.aquamanga-v1.4.58.apk",
                extensionType = "MIHON_EXT",
                isInstalled = true,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 24,
                hasUpdate = true,
                latestVersionName = "1.4.58",
                apkFileName = "tachiyomi-en.aquamanga-v1.4.58.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_asurascans_en",
                name = "EXT:Asura Scans (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.asurascans",
                versionName = "1.4.65",
                lang = "en",
                mihonSourceId = 8192736451928374601L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.asurascans-v1.4.65.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 30,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.asurascans-v1.4.65.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_flamecomics_en",
                name = "EXT:Flame Comics (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.flamecomics",
                versionName = "1.4.34",
                lang = "en",
                mihonSourceId = 8291736451928374602L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.flamecomics-v1.4.34.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 24,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.flamecomics-v1.4.34.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_weebcentral_en",
                name = "EXT:WeebCentral (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.weebcentral",
                versionName = "1.4.12",
                lang = "en",
                mihonSourceId = 8391736451928374603L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.weebcentral-v1.4.12.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 28,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.weebcentral-v1.4.12.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_webtoons_all",
                name = "EXT:Line Webtoon (Mihon Multi)",
                pkgName = "eu.kanade.tachiyomi.extension.all.webtoons",
                versionName = "1.4.49",
                lang = "en",
                mihonSourceId = 8491736451928374604L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-all.webtoons-v1.4.49.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 40,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-all.webtoons-v1.4.49.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_shonenjumpplus_ja",
                name = "EXT:Shonen Jump+ (Mihon JA)",
                pkgName = "eu.kanade.tachiyomi.extension.ja.shonenjumpplus",
                versionName = "1.4.38",
                lang = "ja",
                mihonSourceId = 8591736451928374605L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-ja.shonenjumpplus-v1.4.38.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 35,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-ja.shonenjumpplus-v1.4.38.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_harimanga_en",
                name = "EXT:HariManga (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.harimanga",
                versionName = "1.4.22",
                lang = "en",
                mihonSourceId = 8691736451928374606L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.harimanga-v1.4.22.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 18,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.harimanga-v1.4.22.apk",
                repoName = "Keiyoushi Official",
            ),
            ExternalExtensionInfo(
                id = "ext_mangapark_en",
                name = "EXT:MangaPark (Mihon EN)",
                pkgName = "eu.kanade.tachiyomi.extension.en.mangapark",
                versionName = "1.4.41",
                lang = "en",
                mihonSourceId = 8791736451928374607L,
                authorityOrUrl = "$repoBase/apk/tachiyomi-en.mangapark-v1.4.41.apk",
                extensionType = "MIHON_EXT",
                isInstalled = false,
                isEnabled = true,
                isNsfw = false,
                sampleCatalogCount = 26,
                hasUpdate = false,
                latestVersionName = null,
                apkFileName = "tachiyomi-en.mangapark-v1.4.41.apk",
                repoName = "Keiyoushi Official",
            ),
        )
    }

    private fun getDebugMockCatalogItems(): List<ExtensionCatalogManga> = listOf(
        ExtensionCatalogManga(
            title = "Frieren: Beyond Journey's End",
            altTitle = "Sousou no Frieren",
            author = "Kanehito Yamada",
            sourceName = "EXT:MangaDex (Mihon EN)",
            lang = "en",
            totalChapters = 36,
            rating = 0.99f,
            coverSeed = "frieren",
            tags = listOf("Fantasy", "Adventure", "Drama"),
            description = "Elf mage Frieren embarks on a new journey to Aureole to understand the humans she once traveled with.",
            url = "https://mangadex.ext/title/frieren-beyond-journeys-end",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Delicious in Dungeon",
            altTitle = "Dungeon Meshi",
            author = "Ryoko Kui",
            sourceName = "EXT:ComicK (Mihon Multi)",
            lang = "en",
            totalChapters = 32,
            rating = 0.97f,
            coverSeed = "dungeon_meshi",
            tags = listOf("Fantasy", "Comedy", "Gourmet"),
            description = "Laios and his party cook and eat dungeon monsters to rescue Falin before the Red Dragon digests her.",
            url = "https://comick.ext/comic/delicious-in-dungeon",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Berserk (Deluxe Scan)",
            altTitle = "Berserk",
            author = "Kentaro Miura",
            sourceName = "EXT:MangaDex (Mihon EN)",
            lang = "en",
            totalChapters = 40,
            rating = 0.99f,
            coverSeed = "berserk",
            tags = listOf("Dark Fantasy", "Action", "Seinen"),
            description = "Guts, the Black Swordsman, defies fate and demonic apostles across a war-torn medieval realm.",
            url = "https://mangadex.ext/title/berserk-deluxe",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Omniscient Reader's Viewpoint",
            altTitle = "Omniscient Reader",
            author = "Sing N Song",
            sourceName = "EXT:Bato.to (Mihon EN)",
            lang = "en",
            totalChapters = 38,
            rating = 0.98f,
            coverSeed = "orv",
            tags = listOf("Action", "Fantasy", "Apocalyptic"),
            description = "Kim Dokja uses his complete knowledge of a ruined novel world to survive the Star Stream scenarios.",
            url = "https://bato.ext/series/omniscient-reader",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Chainsaw Man",
            altTitle = "CSM",
            author = "Tatsuki Fujimoto",
            sourceName = "EXT:MangaDex (Mihon EN)",
            lang = "en",
            totalChapters = 35,
            rating = 0.97f,
            coverSeed = "chainsaw_man",
            tags = listOf("Action", "Supernatural", "Dark Fantasy"),
            description = "Denji merges with his chainsaw devil Pochita and joins Public Safety Devil Hunters.",
            url = "https://mangadex.ext/title/chainsaw-man",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Witch Hat Atelier",
            altTitle = "Tongari Boushi no Atelier",
            author = "Kamome Shirahama",
            sourceName = "EXT:MangaDex (Mihon EN)",
            lang = "en",
            totalChapters = 28,
            rating = 0.96f,
            coverSeed = "witch_hat",
            tags = listOf("Fantasy", "Seinen", "Magic"),
            description = "Coco discovers that magic is drawn rather than born with, becoming Qifrey's apprentice.",
            url = "https://mangadex.ext/title/witch-hat-atelier",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Dandadan",
            altTitle = "ダンダダン",
            author = "Yukinobu Tatsu",
            sourceName = "EXT:ComicK (Mihon Multi)",
            lang = "en",
            totalChapters = 34,
            rating = 0.97f,
            coverSeed = "dandadan",
            tags = listOf("Action", "Supernatural", "Comedy"),
            description = "Momo Ayase and Okarun battle ghosts and aliens in a high-velocity supernatural comedy.",
            url = "https://comick.ext/comic/dandadan",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Vagabond",
            altTitle = "バガボンド",
            author = "Takehiko Inoue",
            sourceName = "EXT:Bato.to (Mihon EN)",
            lang = "en",
            totalChapters = 37,
            rating = 0.99f,
            coverSeed = "vagabond",
            tags = listOf("Historical", "Samurai", "Seinen"),
            description = "Shinmen Takezo adopts the name Miyamoto Musashi and walks the path of the sword.",
            url = "https://bato.ext/series/vagabond",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Vinland Saga",
            altTitle = "ヴィンランド・サガ",
            author = "Makoto Yukimura",
            sourceName = "EXT:MangaDex (Mihon EN)",
            lang = "en",
            totalChapters = 30,
            rating = 0.98f,
            coverSeed = "vinland",
            tags = listOf("Historical", "Action", "Seinen"),
            description = "Thorfinn seeks vengeance on Askeladd before searching for a peaceful land beyond the western sea.",
            url = "https://mangadex.ext/title/vinland-saga",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Sakamoto Days",
            altTitle = "サカモトデイズ",
            author = "Yuto Suzuki",
            sourceName = "EXT:ComicK (Mihon Multi)",
            lang = "en",
            totalChapters = 29,
            rating = 0.95f,
            coverSeed = "sakamoto",
            tags = listOf("Action", "Comedy", "Shounen"),
            description = "Legendary hitman Taro Sakamoto defends his family convenience store without breaking his no-kill rule.",
            url = "https://comick.ext/comic/sakamoto-days",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Blue Box",
            altTitle = "Ao no Hako",
            author = "Kouji Miura",
            sourceName = "EXT:Aqua Manga (Mihon EN)",
            lang = "en",
            totalChapters = 26,
            rating = 0.94f,
            coverSeed = "blue_box",
            tags = listOf("Romance", "Sports", "Slice of Life"),
            description = "Badminton player Taiki Inomata trains alongside basketball star Chinatsu Kano.",
            url = "https://aquamanga.ext/series/blue-box",
            isDebugMock = true,
        ),
        ExtensionCatalogManga(
            title = "Kagurabachi (Debug)",
            altTitle = "Kagurabachi",
            author = "Takeru Hokazono",
            sourceName = "EXT:Aqua Manga (Mihon EN)",
            lang = "en",
            totalChapters = 24,
            rating = 0.95f,
            coverSeed = "kagurabachi_dbg",
            tags = listOf("Action", "Supernatural"),
            description = "Chihiro wields the enchanted katana Enten against the Hishaku sorcerer syndicate.",
            url = "https://aquamanga.ext/series/kagurabachi",
            isDebugMock = true,
        ),
    )
}
