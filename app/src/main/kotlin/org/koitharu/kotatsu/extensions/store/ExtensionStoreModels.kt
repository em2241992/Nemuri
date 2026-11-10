package org.koitharu.kotatsu.extensions.store

import org.koitharu.kotatsu.core.model.MangaSource
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

enum class MangaSourceType {
    NATIVE_KOTATSU,
    MIHON_EXTENSION,
    LNREADER_PLUGIN,
}

data class ExternalRepoMeta(
    val name: String,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

data class ExternalRepoJson(
    val meta: ExternalRepoMeta,
    val indexV2: String? = null,
)

data class ExternalExtensionRepoSource(
    val name: String,
    val lang: String = "all",
    val id: Long = 0L,
    val baseUrl: String = "",
    val versionId: Int = 1,
)

data class ExternalExtensionRepoEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "all",
    val code: Long = 0L,
    val version: String = "",
    val nsfw: Int = 0,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<ExternalExtensionRepoSource> = emptyList(),
    val iconUrl: String? = null,
    val apkUrl: String? = null,
) {
    val isNsfw: Boolean
        get() = nsfw == 1
}

data class LnStoreEntry(
    val id: String,
    val name: String,
    val site: String = "",
    val lang: String = "en",
    val version: String = "1.0.0",
    val url: String = "",
    val iconUrl: String = "",
)

data class NetworkExtensionStore(
    val name: String = "",
    val baseUrl: String = "",
    val extensionListUrl: String = "",
    val signingKeyFingerprints: List<String> = emptyList(),
    val extensions: List<ExternalExtensionRepoEntry> = emptyList(),
)

data class ExternalRepoInfo(
    val name: String,
    val baseUrl: String,
    val website: String?,
    val signingKeyFingerprint: String?,
    val extensionCount: Int,
)

data class ValidatedExtensionStore(
    val normalizedUrl: String,
    val displayName: String,
    val website: String?,
    val signingKeyFingerprint: String?,
    val extensionCount: Int,
    val entries: List<ExternalExtensionRepoEntry>,
)

enum class ExtensionInstallMode {
    SHARED,
    PRIVILEGED,
    EXTERNAL,
    PRIVATE,
}

data class ExtensionStoreRecord(
    val id: String,
    val name: String,
    val url: String,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
    val extensionCount: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
)

data class ExtensionOwnershipRecord(
    val pkgName: String,
    val storeId: String? = null,
    val storeName: String? = null,
    val storeUrl: String? = null,
    val apkUrl: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val installMode: ExtensionInstallMode = ExtensionInstallMode.EXTERNAL,
    val updatedAt: Long = 0L,
)

data class ExtensionStoreRegistryState(
    val stores: List<ExtensionStoreRecord> = emptyList(),
    val ownership: Map<String, ExtensionOwnershipRecord> = emptyMap(),
)

data class ExtensionCatalogPage(
    val items: List<ExternalExtensionRepoEntry>,
    val totalCount: Int,
    val hasMore: Boolean,
)

data class StoreHealth(
    val storeId: String,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
)

data class ExtensionStoreState(
    val stores: List<ExtensionStoreRecord> = emptyList(),
    val activeStoreId: String? = null,
    val ownershipByPackage: Map<String, ExtensionOwnershipRecord> = emptyMap(),
    val storeHealthById: Map<String, StoreHealth> = emptyMap(),
    val isRefreshingCatalog: Boolean = false,
) {
    val activeStore: ExtensionStoreRecord?
        get() = stores.firstOrNull { it.id == activeStoreId } ?: stores.firstOrNull { it.enabled } ?: stores.firstOrNull()
}

enum class ContentWarning {
    NONE,
    NSFW,
}

sealed interface SourceCatalogItem {
    data class SourceItem(
        val source: MangaSource,
        val isEnabled: Boolean = true,
        val isPinned: Boolean = false,
    ) : SourceCatalogItem

    data class AvailableExtension(
        val entry: ExternalExtensionRepoEntry,
        val storeId: String,
        val storeName: String,
        val storeUrl: String,
        val isInstalled: Boolean = false,
        val hasUpdate: Boolean = false,
        val isInstalling: Boolean = false,
    ) : SourceCatalogItem

    data class Hint(
        val iconResId: Int = 0,
        val title: String,
        val text: String,
    ) : SourceCatalogItem
}

data class SourcesCatalogFilter(
    val types: Set<MangaSourceType> = emptySet(),
    val query: String? = null,
    val locale: String? = null,
    val contentWarning: Set<ContentWarning> = setOf(ContentWarning.NONE),
    val isNewOnly: Boolean = false,
    val isInstalledOnly: Boolean = false,
    val activeStoreId: String? = null,
)

private val LANGUAGE_AUTONYMS = mapOf(
    "ar" to "العربية",
    "bg" to "български",
    "bn" to "বাংলা",
    "ca" to "català",
    "cs" to "čeština",
    "da" to "dansk",
    "de" to "Deutsch",
    "el" to "Ελληνικά",
    "en" to "English",
    "es" to "español",
    "es-419" to "español (Latinoamérica)",
    "fa" to "فارسی",
    "fi" to "suomi",
    "fil" to "Filipino",
    "fr" to "français",
    "he" to "עברית",
    "hi" to "हिन्दी",
    "hr" to "hrvatski",
    "hu" to "magyar",
    "id" to "Indonesia",
    "it" to "italiano",
    "ja" to "日本語",
    "jv" to "Basa Jawa",
    "kk" to "қазақ тілі",
    "ko" to "한국어",
    "lt" to "lietuvių",
    "lv" to "latviešu",
    "ms" to "Melayu",
    "my" to "မြန်မာ",
    "nb" to "norsk bokmål",
    "nl" to "Nederlands",
    "no" to "norsk",
    "pl" to "polski",
    "pt" to "português",
    "pt-br" to "português (Brasil)",
    "ro" to "română",
    "ru" to "русский",
    "sk" to "slovenčina",
    "sr" to "српски",
    "sv" to "svenska",
    "th" to "ไทย",
    "tl" to "Tagalog",
    "tr" to "Türkçe",
    "uk" to "українська",
    "ur" to "اردو",
    "uz" to "o‘zbek",
    "vi" to "Tiếng Việt",
    "zh" to "中文",
    "zh-hans" to "简体中文",
    "zh-hant" to "繁體中文",
)

fun normalizeExternalExtensionLangCode(lang: String): String {
    return lang.trim().lowercase(Locale.ROOT).replace('_', '-')
}

fun getExternalExtensionLangCodeOrNull(lang: String): String? {
    val normalized = normalizeExternalExtensionLangCode(lang)
    return when {
        normalized.isEmpty() -> null
        normalized == "all" || normalized == "multi" || normalized == "other" -> null
        else -> normalized
    }
}

fun getExternalExtensionLanguageDisplayName(lang: String, displayLocale: Locale = Locale.getDefault()): String? {
    val normalized = getExternalExtensionLangCodeOrNull(lang) ?: return null
    val locale = Locale.forLanguageTag(normalized)
    val language = locale.getDisplayLanguage(displayLocale).trim()
    if (language.isEmpty() || language.equals(normalized, ignoreCase = true)) {
        return LANGUAGE_AUTONYMS[normalized] ?: normalized.uppercase(Locale.ROOT)
    }
    val country = locale.getDisplayCountry(displayLocale).trim()
    return if (country.isNotEmpty()) {
        "$language ($country)"
    } else {
        language.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
        }
    }
}

fun getExternalExtensionLanguageAutonym(lang: String): String? {
    val normalized = getExternalExtensionLangCodeOrNull(lang) ?: return null
    LANGUAGE_AUTONYMS[normalized]?.let { return it }
    val baseLanguage = normalized.substringBefore('-')
    LANGUAGE_AUTONYMS[baseLanguage]?.let { return it }
    val locale = Locale.forLanguageTag(normalized)
    val selfDisplay = locale.getDisplayName(locale).trim()
    return selfDisplay.takeIf { it.isNotEmpty() }
}

fun resolveActiveMihonLanguage(
    selectedContentLocales: Set<String>,
    uiLocale: Locale = Locale.getDefault(),
): String {
    val normalizedSelected = selectedContentLocales
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() && it != "all" }
    val uiLanguage = uiLocale.language.lowercase(Locale.ROOT)
    return when {
        uiLanguage.isNotEmpty() && uiLanguage in normalizedSelected -> uiLanguage
        normalizedSelected.isNotEmpty() -> normalizedSelected.sorted().first()
        uiLanguage.isNotEmpty() -> uiLanguage
        else -> "en"
    }
}

fun normalizeExtensionStoreUrl(rawUrl: String): String {
    var trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return ""

    // Support shorthand "owner/repo" or "owner/repo@branch/path"
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        val atBranchRegex = Regex("""^([^/\s]+)/([^@\s/]+)@([^/\s]+)(?:/(.*))?$""")
        val shorthandRegex = Regex("""^([^/\s.]+)/([^/\s]+)$""")
        when {
            atBranchRegex.matches(trimmed) -> {
                val match = atBranchRegex.find(trimmed)!!
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                val branch = match.groupValues[3]
                val path = match.groupValues[4].trim('/')
                trimmed = if (path.isNotEmpty()) {
                    "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
                } else {
                    "https://raw.githubusercontent.com/$owner/$repo/$branch"
                }
            }
            shorthandRegex.matches(trimmed) -> {
                val (owner, repo) = shorthandRegex.find(trimmed)!!.destructured
                val defaultBranch = if (owner.equals("keiyoushi", ignoreCase = true) || repo.contains("extension", ignoreCase = true)) {
                    "repo"
                } else {
                    "main"
                }
                trimmed = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch"
            }
            else -> {
                trimmed = "https://$trimmed"
            }
        }
    }
    trimmed = trimmed.trimEnd('/')

    if (trimmed.contains("github.com/") && !trimmed.contains("raw.githubusercontent.com")) {
        val blobRegex = Regex("""https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)""")
        val treeRegex = Regex("""https?://github\.com/([^/]+)/([^/]+)/tree/([^/]+)(?:/(.*))?""")
        val repoRegex = Regex("""https?://github\.com/([^/]+)/([^/]+)$""")
        trimmed = when {
            blobRegex.matches(trimmed) -> {
                val (owner, repo, branch, path) = blobRegex.find(trimmed)!!.destructured
                "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
            }
            treeRegex.matches(trimmed) -> {
                val match = treeRegex.find(trimmed)!!
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                val branch = match.groupValues[3]
                val path = match.groupValues[4].trim('/')
                if (path.isNotEmpty()) {
                    "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
                } else {
                    "https://raw.githubusercontent.com/$owner/$repo/$branch"
                }
            }
            repoRegex.matches(trimmed) -> {
                val (owner, repo) = repoRegex.find(trimmed)!!.destructured
                val defaultBranch = if (owner.equals("keiyoushi", ignoreCase = true) || repo.contains("extension", ignoreCase = true)) {
                    "repo"
                } else {
                    "main"
                }
                "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch"
            }
            else -> trimmed
        }
    }
    return trimmed.trimEnd('/')
}

fun stableExtensionStoreId(normalizedUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedUrl.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

fun defaultExtensionStoreName(normalizedUrl: String): String {
    return runCatching {
        val uri = URI(normalizedUrl)
        val host = uri.host.orEmpty().removePrefix("www.")
        val segments = uri.path.orEmpty()
            .split('/')
            .filter {
                it.isNotBlank() &&
                    !it.equals("index.min.json", ignoreCase = true) &&
                    !it.equals("index.json", ignoreCase = true) &&
                    !it.equals("index.pb", ignoreCase = true) &&
                    !it.equals("repo.json", ignoreCase = true) &&
                    !it.equals("plugins.min.json", ignoreCase = true)
            }
        when {
            host.equals("raw.githubusercontent.com", ignoreCase = true) && segments.size >= 2 -> {
                "${segments[0]} / ${segments[1]}"
            }
            segments.isNotEmpty() -> {
                "$host (${segments.last()})"
            }
            host.isNotBlank() -> host
            else -> normalizedUrl
        }
    }.getOrDefault(normalizedUrl)
}

fun extensionDisplayName(entry: ExternalExtensionRepoEntry): String {
    return entry.name
        .removePrefix("Tachiyomi: ")
        .removePrefix("Mihon: ")
        .removePrefix("Aniyomi: ")
        .trim()
        .ifBlank { entry.pkg }
}

fun matchesExtensionQuery(entry: ExternalExtensionRepoEntry, query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    if (extensionDisplayName(entry).lowercase(Locale.ROOT).contains(q)) return true
    if (entry.name.lowercase(Locale.ROOT).contains(q)) return true
    if (entry.pkg.lowercase(Locale.ROOT).contains(q)) return true
    if (entry.lang.lowercase(Locale.ROOT).contains(q)) return true
    val langDisplayName = getExternalExtensionLanguageDisplayName(entry.lang)?.lowercase(Locale.ROOT)
    if (langDisplayName != null && langDisplayName.contains(q)) return true
    val langAutonym = getExternalExtensionLanguageAutonym(entry.lang)?.lowercase(Locale.ROOT)
    if (langAutonym != null && langAutonym.contains(q)) return true
    return entry.sources.any { source ->
        source.name.lowercase(Locale.ROOT).contains(q) ||
            source.baseUrl.lowercase(Locale.ROOT).contains(q) ||
            source.lang.lowercase(Locale.ROOT).contains(q)
    }
}
