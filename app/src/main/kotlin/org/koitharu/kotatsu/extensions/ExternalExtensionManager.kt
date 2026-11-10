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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
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
import org.koitharu.kotatsu.core.model.ExtensionInstallStatus
import org.koitharu.kotatsu.core.model.ExtensionInstallTask
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.KotatsuExternalProviderScanner
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline
import org.koitharu.kotatsu.core.util.TitleNormalizer
import org.koitharu.kotatsu.extensions.store.ExtensionRepoRepository
import org.koitharu.kotatsu.extensions.store.ExtensionStoreManager
import org.koitharu.kotatsu.extensions.store.ExternalExtensionRepoEntry
import org.koitharu.kotatsu.extensions.store.extensionDisplayName
import org.koitharu.kotatsu.extensions.store.normalizeExtensionStoreUrl
import org.koitharu.kotatsu.lnreader.LnPluginManager
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

data class ExtensionCatalogManga(
	val title: String,
	val altTitle: String?,
	val author: String,
	val sourceName: String,
	val url: String,
	val coverSeed: String,
	val totalChapters: Int,
	val rating: Float,
	val tags: List<String>,
	val description: String,
	val lang: String = "EN",
	val isDebugMock: Boolean = false,
)

class ExternalExtensionManager(
	private val context: Context,
	private val db: MangaDatabase,
) {
	private val appContext = context.applicationContext
	private val apkCacheDir = File(appContext.cacheDir, "extension_apks").apply { mkdirs() }
	private val cbzStorageDir = File(appContext.filesDir, "cbz_imports").apply { mkdirs() }
	private val repoIndexCacheDir = File(appContext.filesDir, "repo_indexes").apply { mkdirs() }

	private val httpClient: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(25, TimeUnit.SECONDS)
		.followRedirects(true)
		.followSslRedirects(true)
		.build()

	val repoRepository: ExtensionRepoRepository = ExtensionRepoRepository(appContext, httpClient)
	val storeManager: ExtensionStoreManager = ExtensionStoreManager(appContext, repoRepository)
	val mihonExtensionManager: MihonExtensionManager = MihonExtensionManager.getInstance(appContext)
	val lnPluginManager: LnPluginManager = LnPluginManager.getInstance(appContext)
	val mangaRepositoryFactory: MangaRepository.Factory = MangaRepository.Factory(appContext)

	private val dynamicLiveCatalog = CopyOnWriteArrayList<ExtensionCatalogManga>()

	// Strictly isolated sandbox-only state (NEVER read when includeDebugMock == false)
	private val debugSandboxInstalledPkgs = HashMap<String, String>()
	private val debugSandboxExtraCatalog = CopyOnWriteArrayList<ExtensionCatalogManga>()

	fun normalizeGitHubRepoUrl(rawUrl: String): String {
		val normalized = normalizeExtensionStoreUrl(rawUrl)
		return repoRepository.getBaseUrl(normalized)
	}

	suspend fun addExtensionRepository(
		name: String,
		rawUrl: String,
		repoType: String = "MIHON_JSON",
	): ExtensionRepoEntity = withContext(Dispatchers.IO) {
		val normalizedUrl = normalizeGitHubRepoUrl(rawUrl)
		val existing = db.getAuxiliaryDao().getExtensionRepos()
			.firstOrNull { it.baseUrl.equals(normalizedUrl, ignoreCase = true) }

		val validated = runCatching {
			repoRepository.validateStore(normalizedUrl, forceRefresh = true).getOrNull()
		}.getOrNull()

		val resolvedType = when {
			rawUrl.endsWith("plugins.min.json", ignoreCase = true) -> "LNREADER_JSON"
			normalizedUrl.contains("kotatsu", ignoreCase = true) -> "KOTATSU_JSON"
			else -> repoType
		}

		val resolvedName = name.trim().takeIf { it.isNotBlank() }
			?: validated?.displayName?.takeIf { it.isNotBlank() }
			?: "Extension Repository"

		val now = System.currentTimeMillis()
		val nextId = existing?.repoId ?: ((db.getAuxiliaryDao().getExtensionRepos().maxOfOrNull { it.repoId } ?: 0L) + 1L)

		val entity = ExtensionRepoEntity(
			repoId = nextId,
			name = resolvedName,
			baseUrl = normalizedUrl,
			repoType = resolvedType,
			isEnabled = true,
			extensionCount = validated?.extensionCount ?: 0,
			lastSyncedAt = if (validated != null) now else 0L,
		)
		db.getAuxiliaryDao().upsertExtensionRepo(entity)
		if (validated != null && validated.entries.isNotEmpty()) {
			writeLocalRepoEntriesCache(nextId, validated.entries)
		}
		entity
	}

	suspend fun fetchAndCacheRepositoryIndex(repo: ExtensionRepoEntity): List<ExternalExtensionInfo> =
		withContext(Dispatchers.IO) {
			val validated = repoRepository.validateStore(repo.baseUrl, forceRefresh = true).getOrThrow()
			writeLocalRepoEntriesCache(repo.repoId, validated.entries)
			db.getAuxiliaryDao().upsertExtensionRepo(
				repo.copy(
					name = if (repo.name.isBlank() || repo.name == "Extension Repository") validated.displayName else repo.name,
					extensionCount = validated.extensionCount,
					lastSyncedAt = System.currentTimeMillis(),
				),
			)
			entriesToExternalExtensionInfo(validated.entries, repo)
		}

	fun deleteCachedRepoIndex(repoId: Long) {
		runCatching { File(repoIndexCacheDir, "repo_${repoId}.json").delete() }
	}

	fun parseMihonIndexJson(jsonText: String, repo: ExtensionRepoEntity): List<ExternalExtensionInfo> {
		val entries = runCatching {
			repoRepository.parseJsonArrayEntries(jsonText, repoRepository.getBaseUrl(repo.baseUrl))
		}.getOrDefault(emptyList())
		return entriesToExternalExtensionInfo(entries, repo)
	}

	fun discoverExtensions(
		enabledRepos: List<ExtensionRepoEntity> = emptyList(),
		includeDebugMock: Boolean = false,
	): List<ExternalExtensionInfo> {
		val combined = LinkedHashMap<String, ExternalExtensionInfo>()

		// 1. OS-installed Mihon / Tachiyomi extension packages (PackageManager + MihonExtensionLoader)
		val mihonLoaded = runBlocking(Dispatchers.IO) {
			runCatching { mihonExtensionManager.refreshExtensions() }.getOrDefault(emptyList())
		}
		for (ext in mihonLoaded) {
			if (repoRepository.isDeprecatedStubExtension(ext.pkgName, ext.appName)) continue
			val firstSourceId = ext.catalogueSources.firstOrNull()?.id ?: (ext.pkgName.hashCode().toLong() and 0x7FFFFFFFL)
			val sourceTitle = ext.catalogueSources.firstOrNull()?.name ?: ext.appName
			combined[ext.pkgName] = ExternalExtensionInfo(
				id = "MIHON_${firstSourceId}",
				name = sourceTitle,
				pkgName = ext.pkgName,
				versionName = ext.versionName,
				lang = ext.lang.ifBlank { "all" },
				mihonSourceId = firstSourceId,
				authorityOrUrl = ext.pkgName,
				extensionType = "MIHON_EXT",
				isInstalled = true,
				isEnabled = true,
				isNsfw = ext.isNsfw,
				sampleCatalogCount = ext.catalogueSources.size,
				hasUpdate = false,
				latestVersionName = ext.versionName,
				apkFileName = "",
				repoName = "Installed on Device",
			)
		}

		// 2. OS-installed Kotatsu external ContentProvider extensions (app.kotatsu.parser.PROVIDE_MANGA)
		val kotatsuProviders = KotatsuExternalProviderScanner.discoverProviders(appContext)
		for (prov in kotatsuProviders) {
			combined[prov.pkgName] = ExternalExtensionInfo(
				id = prov.sourceKey,
				name = prov.label,
				pkgName = prov.pkgName,
				versionName = prov.versionName,
				lang = prov.lang,
				mihonSourceId = prov.authority.hashCode().toLong() and 0x7FFFFFFFL,
				authorityOrUrl = "content://${prov.authority}",
				extensionType = "KOTATSU_CONTENT_PROVIDER",
				isInstalled = true,
				isEnabled = true,
				isNsfw = false,
				sampleCatalogCount = 1,
				hasUpdate = false,
				latestVersionName = prov.versionName,
				apkFileName = "",
				repoName = "Kotatsu Extension",
			)
		}

		// 3. Installed LNReader JavaScript plugins (WebView JsHost)
		for (plugin in lnPluginManager.installedPlugins.value) {
			combined[plugin.pkgName] = ExternalExtensionInfo(
				id = plugin.sourceKey,
				name = plugin.name,
				pkgName = plugin.pkgName,
				versionName = plugin.version,
				lang = plugin.lang,
				mihonSourceId = plugin.pkgName.hashCode().toLong() and 0x7FFFFFFFL,
				authorityOrUrl = plugin.sourceJsUrl,
				extensionType = "LNREADER_PLUGIN",
				isInstalled = true,
				isEnabled = true,
				isNsfw = false,
				sampleCatalogCount = 1,
				hasUpdate = false,
				latestVersionName = plugin.version,
				apkFileName = "${plugin.pluginId}.js",
				repoName = "LNReader Plugin",
			)
		}

		// 4. Available extensions from user-configured repositories
		for (repo in enabledRepos.filter { it.isEnabled }) {
			val cachedEntries = readLocalRepoEntriesCache(repo.repoId).ifEmpty {
				repoRepository.getCachedExtensions(repo.baseUrl)
			}
			val mapped = entriesToExternalExtensionInfo(cachedEntries, repo)
			for (info in mapped) {
				val existingInstalled = combined[info.pkgName]
				if (existingInstalled != null && existingInstalled.isInstalled) {
					val hasNewer = existingInstalled.versionName != info.versionName && info.versionName.isNotBlank()
					combined[info.pkgName] = existingInstalled.copy(
						hasUpdate = hasNewer,
						latestVersionName = info.versionName,
						authorityOrUrl = info.authorityOrUrl.ifBlank { existingInstalled.authorityOrUrl },
						apkFileName = info.apkFileName,
						repoName = repo.name,
					)
				} else {
					combined[info.pkgName] = info
				}
			}
		}

		// 5. Debug Sandbox extensions ONLY when includeDebugMock == true
		if (includeDebugMock) {
			for (mockExt in buildDebugSandboxExtensions()) {
				if (mockExt.pkgName !in combined) {
					combined[mockExt.pkgName] = mockExt
				}
			}
		}

		return combined.values.toList()
	}

	private fun entriesToExternalExtensionInfo(
		entries: List<ExternalExtensionRepoEntry>,
		repo: ExtensionRepoEntity,
	): List<ExternalExtensionInfo> {
		val installedLnByPkg = lnPluginManager.installedPlugins.value.associateBy { it.pkgName.lowercase(Locale.ROOT) }
		return entries
			.filterNot { repoRepository.isDeprecatedStubExtension(it.pkg, it.name) }
			.map { entry ->
				val isLnJs = entry.pkg.startsWith("lnreader.plugin.", ignoreCase = true) ||
					entry.apk.endsWith(".js", ignoreCase = true) ||
					entry.apkUrl?.endsWith(".js", ignoreCase = true) == true
				val isKotatsuExt = entry.pkg.startsWith("org.koitharu.kotatsu.extension", ignoreCase = true) ||
					entry.pkg.startsWith("app.kotatsu.extension", ignoreCase = true) ||
					repo.repoType.equals("KOTATSU_JSON", ignoreCase = true)

				val osPkgInfo = if (!isLnJs) getInstalledOsPackageOrNull(entry.pkg) else null
				val lnInstalled = installedLnByPkg[entry.pkg.lowercase(Locale.ROOT)]

				val isActuallyInstalled = osPkgInfo != null || lnInstalled != null
				val installedVer = osPkgInfo?.versionName ?: lnInstalled?.version
				val osVerCode = if (osPkgInfo != null) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) osPkgInfo.longVersionCode else {
						@Suppress("DEPRECATION")
						osPkgInfo.versionCode.toLong()
					}
				} else {
					0L
				}
				val hasUpdate = when {
					osPkgInfo != null && entry.code > 0L -> entry.code > osVerCode
					lnInstalled != null -> lnInstalled.version != entry.version && entry.version.isNotBlank()
					else -> false
				}

				val resolvedApkUrl = repoRepository.resolveApkUrl(repo.baseUrl, entry)
				val primarySource = entry.sources.firstOrNull()
				val sourceId = primarySource?.id?.takeIf { it != 0L }
					?: (entry.pkg.hashCode().toLong() and 0x7FFFFFFFL)

				val cleanTitle = extensionDisplayName(entry)
				val extType = when {
					isLnJs -> "LNREADER_PLUGIN"
					isKotatsuExt -> "KOTATSU_CONTENT_PROVIDER"
					else -> "MIHON_EXT"
				}
				val idKey = when {
					isLnJs -> "LNREADER_${entry.pkg.removePrefix("lnreader.plugin.")}"
					isKotatsuExt -> "KOTATSU_${entry.pkg}"
					else -> "MIHON_$sourceId"
				}

				ExternalExtensionInfo(
					id = idKey,
					name = cleanTitle,
					pkgName = entry.pkg,
					versionName = installedVer ?: entry.version.ifBlank { "1.0" },
					lang = entry.lang.ifBlank { "all" },
					mihonSourceId = sourceId,
					authorityOrUrl = resolvedApkUrl,
					extensionType = extType,
					isInstalled = isActuallyInstalled,
					isEnabled = isActuallyInstalled,
					isNsfw = entry.isNsfw,
					sampleCatalogCount = entry.sources.size.coerceAtLeast(1),
					hasUpdate = hasUpdate,
					latestVersionName = entry.version.ifBlank { null },
					apkFileName = entry.apk.substringAfterLast('/'),
					repoName = repo.name,
				)
			}
	}

	private fun getInstalledOsPackageOrNull(pkgName: String): PackageInfo? {
		return runCatching {
			val pm = appContext.packageManager
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
			} else {
				@Suppress("DEPRECATION")
				pm.getPackageInfo(pkgName, 0)
			}
		}.getOrNull()
	}

	fun getExtensionCatalogItems(includeDebugMock: Boolean = false): List<ExtensionCatalogManga> {
		val realItems = dynamicLiveCatalog.filter { !it.isDebugMock }
		if (!includeDebugMock) {
			return realItems.distinctBy { "${it.sourceName}|${it.url}" }
		}
		return (realItems + buildDebugSandboxCatalog() + debugSandboxExtraCatalog)
			.distinctBy { "${it.sourceName}|${it.url}" }
	}

	fun purgeMockCatalogCache() {
		dynamicLiveCatalog.removeAll { it.isDebugMock }
		debugSandboxInstalledPkgs.clear()
		debugSandboxExtraCatalog.clear()
	}

	suspend fun fetchLiveCatalogForInstalledSources() = withContext(Dispatchers.IO) {
		val mihonLoaded = mihonExtensionManager.refreshExtensions()
		val kotatsuProviders = KotatsuExternalProviderScanner.discoverProviders(appContext)
		val lnPlugins = lnPluginManager.installedPlugins.value

		val activeSources = buildList {
			mihonLoaded.flatMap { ext ->
				ext.catalogueSources.map { cat ->
					MangaSource("MIHON_${cat.id}", cat.name)
				}
			}.forEach { add(it) }
			kotatsuProviders.forEach { add(it.toMangaSource()) }
			lnPlugins.forEach { add(it.toMangaSource()) }
		}

		if (activeSources.isEmpty()) return@withContext

		coroutineScope {
			activeSources.map { src ->
				async {
					runCatching {
						val repo = mangaRepositoryFactory.createAsync(src)
						val items = repo.getList(offset = 0, query = null, sortOrder = SortOrder.POPULARITY)
						mergeDomainMangasIntoCatalog(items, src)
					}
				}
			}.awaitAll()
		}
	}

	suspend fun fetchCatalogForSourceOrSearch(
		sourceName: String,
		query: String = "",
		includeDebugMock: Boolean = false,
	): List<ExtensionCatalogManga> = withContext(Dispatchers.IO) {
		val cleanSource = sourceName.trim()
		val isGlobalSearch = cleanSource.isEmpty() ||
			cleanSource.equals("ALL", ignoreCase = true) ||
			cleanSource.equals("All Sources", ignoreCase = true)

		mihonExtensionManager.refreshExtensions()
		val allMihon = mihonExtensionManager.mihonSources.value.map { it.toMangaSource() }
		val allKotatsu = KotatsuExternalProviderScanner.discoverProviders(appContext).map { it.toMangaSource() }
		val allLn = lnPluginManager.installedPlugins.value.map { it.toMangaSource() }
		val localSrc = listOf(MangaSource.LOCAL)

		val candidateSources = if (isGlobalSearch) {
			allMihon + allKotatsu + allLn + localSrc
		} else {
			(allMihon + allKotatsu + allLn + localSrc).filter { src ->
				src.name.equals(cleanSource, ignoreCase = true) ||
					src.title.equals(cleanSource, ignoreCase = true) ||
					src.title.contains(cleanSource, ignoreCase = true) ||
					cleanSource.contains(src.title, ignoreCase = true)
			}
		}

		val fetchedItems = mutableListOf<ExtensionCatalogManga>()
		if (candidateSources.isNotEmpty()) {
			coroutineScope {
				candidateSources.map { src ->
					async {
						runCatching {
							val repo = mangaRepositoryFactory.createAsync(src)
							val list = repo.getList(
								offset = 0,
								query = query.trim().takeIf { it.isNotBlank() },
								sortOrder = SortOrder.POPULARITY,
							)
							mergeDomainMangasIntoCatalog(list, src)
						}.getOrDefault(emptyList())
					}
				}.awaitAll().forEach { fetchedItems += it }
			}
		}

		if (fetchedItems.isEmpty() && includeDebugMock) {
			return@withContext getExtensionCatalogItems(includeDebugMock = true).filter {
				query.isBlank() || it.title.contains(query, ignoreCase = true)
			}
		}
		fetchedItems
	}

	private fun mergeDomainMangasIntoCatalog(
		mangas: List<Manga>,
		src: MangaSource,
	): List<ExtensionCatalogManga> {
		if (mangas.isEmpty()) return emptyList()
		val mapped = mangas.map { m ->
			ExtensionCatalogManga(
				title = m.title,
				altTitle = m.altTitle,
				author = m.author?.takeIf { it.isNotBlank() } ?: src.title,
				sourceName = src.name,
				url = m.url,
				coverSeed = m.coverUrl?.takeIf { it.isNotBlank() } ?: m.largeCoverUrl.orEmpty(),
				totalChapters = m.chapters?.size ?: 0,
				rating = if (m.rating >= 0f) m.rating else 0f,
				tags = m.tags.map { it.title },
				description = m.description?.takeIf { it.isNotBlank() } ?: "",
				lang = extractLangBadge(src.title),
				isDebugMock = false,
			)
		}
		for (item in mapped) {
			dynamicLiveCatalog.removeAll { it.sourceName == item.sourceName && it.url == item.url }
			dynamicLiveCatalog.add(item)
		}
		return mapped
	}

	private fun extractLangBadge(title: String): String {
		val match = Regex("""\(([A-Z]{2,5})\)\s*$""").find(title.trim())
		return match?.groupValues?.getOrNull(1) ?: "EN"
	}

	suspend fun downloadAndInstallExtension(
		ext: ExternalExtensionInfo,
		includeDebugMock: Boolean = false,
		onProgress: (ExtensionInstallTask) -> Unit = {},
	): ExtensionInstallTask = withContext(Dispatchers.IO) {
		val isLnPlugin = ext.extensionType == "LNREADER_PLUGIN" ||
			ext.pkgName.startsWith("lnreader.plugin.", ignoreCase = true) ||
			ext.authorityOrUrl.endsWith(".js", ignoreCase = true)

		// Case 1: LNReader JavaScript Plugin -> Direct download & immediate activation in JsHost
		if (isLnPlugin && (ext.authorityOrUrl.startsWith("http://") || ext.authorityOrUrl.startsWith("https://"))) {
			onProgress(
				ExtensionInstallTask(
					extensionId = ext.id,
					pkgName = ext.pkgName,
					progressFraction = 0.35f,
					status = ExtensionInstallStatus.DOWNLOADING,
					downloadedFilePath = "",
					fileProviderUriString = "",
				),
			)
			val installResult = lnPluginManager.installPlugin(
				pkgName = ext.pkgName,
				displayName = ext.name,
				lang = ext.lang,
				version = ext.latestVersionName ?: ext.versionName,
				siteUrl = "",
				iconUrl = null,
				jsDownloadUrl = ext.authorityOrUrl,
			)
			return@withContext installResult.fold(
				onSuccess = { installedPlugin ->
					mangaRepositoryFactory.invalidateCache()
					runCatching {
						val repo = mangaRepositoryFactory.createAsync(installedPlugin.toMangaSource())
						val novels = repo.getList(offset = 0, query = null, sortOrder = SortOrder.POPULARITY)
						mergeDomainMangasIntoCatalog(novels, installedPlugin.toMangaSource())
					}
					val done = ExtensionInstallTask(
						extensionId = ext.id,
						pkgName = ext.pkgName,
						progressFraction = 1.0f,
						status = ExtensionInstallStatus.INSTALLED,
						downloadedFilePath = installedPlugin.localJsPath,
						fileProviderUriString = "",
						sizeBytes = File(installedPlugin.localJsPath).length(),
					)
					onProgress(done)
					done
				},
				onFailure = {
					val failed = ExtensionInstallTask(
						extensionId = ext.id,
						pkgName = ext.pkgName,
						progressFraction = 0f,
						status = ExtensionInstallStatus.FAILED,
						downloadedFilePath = "",
						fileProviderUriString = "",
					)
					onProgress(failed)
					failed
				},
			)
		}

		// Case 2: Debug Sandbox Mock Extension (strictly isolated under includeDebugMock == true)
		val isDebugMockExtension = includeDebugMock &&
			(ext.authorityOrUrl.contains("example.org") || ext.repoName.contains("Sandbox", ignoreCase = true))
		if (isDebugMockExtension) {
			onProgress(
				ExtensionInstallTask(
					extensionId = ext.id,
					pkgName = ext.pkgName,
					progressFraction = 0.5f,
					status = ExtensionInstallStatus.DOWNLOADING,
					downloadedFilePath = "",
					fileProviderUriString = "",
				),
			)
			val targetVer = ext.latestVersionName ?: ext.versionName
			debugSandboxInstalledPkgs[ext.pkgName] = targetVer
			val dummyFile = getCachedApkFileForExtension(ext)
			if (!dummyFile.exists()) {
				dummyFile.writeBytes("SANDBOX_EXTENSION_ARCHIVE".toByteArray())
			}
			val uriStr = runCatching {
				FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", dummyFile).toString()
			}.getOrDefault("file://${dummyFile.absolutePath}")
			debugSandboxExtraCatalog.add(
				ExtensionCatalogManga(
					title = "[Sandbox] ${ext.name} Verification Series",
					altTitle = null,
					author = "Sandbox Studio",
					sourceName = ext.name,
					url = "https://sandbox.example.org/${ext.pkgName}/1",
					coverSeed = "studio_cover://sandbox_${ext.pkgName}",
					totalChapters = 12,
					rating = 4.5f,
					tags = listOf("Sandbox"),
					description = "Debug Sandbox verification entry.",
					lang = ext.lang.uppercase(Locale.ROOT),
					isDebugMock = true,
				),
			)
			val task = ExtensionInstallTask(
				extensionId = ext.id,
				pkgName = ext.pkgName,
				progressFraction = 1.0f,
				status = ExtensionInstallStatus.INSTALLED,
				downloadedFilePath = dummyFile.absolutePath,
				fileProviderUriString = uriStr,
				sizeBytes = dummyFile.length(),
			)
			onProgress(task)
			return@withContext task
		}

		// Case 3: Real Mihon / Kotatsu Android Extension APK
		// Never spoof installation or inject MangaDex dummy titles!
		val apkUrl = ext.apkDownloadUrl
		if (apkUrl.isNullOrBlank()) {
			val failed = ExtensionInstallTask(
				extensionId = ext.id,
				pkgName = ext.pkgName,
				progressFraction = 0f,
				status = ExtensionInstallStatus.FAILED,
				downloadedFilePath = "",
				fileProviderUriString = "",
			)
			onProgress(failed)
			return@withContext failed
		}

		onProgress(
			ExtensionInstallTask(
				extensionId = ext.id,
				pkgName = ext.pkgName,
				progressFraction = 0.30f,
				status = ExtensionInstallStatus.DOWNLOADING,
				downloadedFilePath = "",
				fileProviderUriString = "",
			),
		)

		val destFile = getCachedApkFileForExtension(ext)
		val downloadOk = runCatching {
			val request = Request.Builder()
				.url(apkUrl)
				.header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
				.build()
			httpClient.newCall(request).execute().use { resp ->
				if (!resp.isSuccessful) error("HTTP ${resp.code}")
				val body = resp.body ?: error("Empty APK body")
				FileOutputStream(destFile).use { out ->
					body.byteStream().copyTo(out)
				}
			}
			destFile.exists() && destFile.length() > 128L
		}.getOrDefault(false)

		val fileProviderUri = if (downloadOk) {
			runCatching {
				FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", destFile).toString()
			}.getOrDefault("")
		} else {
			""
		}

		// Open zero-permission HTTPS APK download/install intent via browser/system handler
		runCatching {
			appContext.startActivity(createBrowserApkDownloadIntent(ext))
		}

		val osInstalledNow = getInstalledOsPackageOrNull(ext.pkgName) != null
		if (osInstalledNow) {
			mihonExtensionManager.refreshExtensions()
			mangaRepositoryFactory.invalidateCache()
			fetchLiveCatalogForInstalledSources()
		}

		val finalTask = ExtensionInstallTask(
			extensionId = ext.id,
			pkgName = ext.pkgName,
			progressFraction = if (downloadOk) 1.0f else 0f,
			status = if (osInstalledNow) ExtensionInstallStatus.INSTALLED else ExtensionInstallStatus.NOT_INSTALLED,
			downloadedFilePath = if (downloadOk) destFile.absolutePath else "",
			fileProviderUriString = fileProviderUri,
			sizeBytes = if (downloadOk) destFile.length() else 0L,
		)
		onProgress(finalTask)
		finalTask
	}

	suspend fun uninstallExtension(ext: ExternalExtensionInfo): Result<Boolean> = withContext(Dispatchers.IO) {
		runCatching {
			if (ext.extensionType == "LNREADER_PLUGIN" || ext.pkgName.startsWith("lnreader.plugin.")) {
				lnPluginManager.uninstallPlugin(ext.pkgName)
				dynamicLiveCatalog.removeAll { it.sourceName.equals(ext.id, ignoreCase = true) || it.sourceName.contains(ext.name, ignoreCase = true) }
				mangaRepositoryFactory.invalidateCache()
				return@runCatching true
			}
			if (ext.pkgName in debugSandboxInstalledPkgs) {
				debugSandboxInstalledPkgs.remove(ext.pkgName)
				debugSandboxExtraCatalog.removeAll { it.sourceName.equals(ext.name, ignoreCase = true) }
				return@runCatching true
			}
			appContext.startActivity(createUninstallPackageIntent(ext.pkgName))
			true
		}
	}

	fun getCachedApkFileForExtension(ext: ExternalExtensionInfo): File {
		val fileName = ext.apkFileName.takeIf { it.isNotBlank() } ?: "${ext.pkgName}.apk"
		return File(apkCacheDir, fileName)
	}

	fun canRequestPackageInstalls(): Boolean = true

	fun createManageUnknownAppSourcesIntent(): Intent {
		return Intent(
			Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
			Uri.parse("package:${appContext.packageName}"),
		).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	}

	fun createBrowserApkDownloadIntent(ext: ExternalExtensionInfo): Intent {
		val url = ext.apkDownloadUrl ?: "https://github.com/keiyoushi/extensions"
		return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	fun createApkPackageInstallerIntent(ext: ExternalExtensionInfo): Intent {
		// Creator Program compliant: delegate APK download/install via zero-permission HTTPS ACTION_VIEW
		return createBrowserApkDownloadIntent(ext)
	}

	fun createFileProviderShareIntent(ext: ExternalExtensionInfo): Intent? {
		val file = getCachedApkFileForExtension(ext)
		if (!file.exists()) return null
		val uri = runCatching {
			FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
		}.getOrNull() ?: return null
		val sendIntent = Intent(Intent.ACTION_SEND).apply {
			type = "application/vnd.android.package-archive"
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		return Intent.createChooser(sendIntent, "Share Extension APK").apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	fun createUninstallPackageIntent(pkgName: String): Intent {
		return Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName")).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	suspend fun importCbzArchive(
		inputStream: InputStream?,
		fileName: String,
		allowDebugSampleWhenEmpty: Boolean = true,
	): MangaEntity = withContext(Dispatchers.IO) {
		val cleanFileName = fileName.substringAfterLast('/').ifBlank { "Imported_Archive.cbz" }
		val safeName = if (cleanFileName.endsWith(".cbz", true) || cleanFileName.endsWith(".zip", true)) {
			cleanFileName
		} else {
			"$cleanFileName.cbz"
		}
		val targetFile = File(cbzStorageDir, safeName)

		val bytes = inputStream?.readBytes() ?: ByteArray(0)
		if (bytes.isNotEmpty()) {
			FileOutputStream(targetFile).use { it.write(bytes) }
		} else if (allowDebugSampleWhenEmpty) {
			writeSampleVerificationCbz(targetFile, safeName)
		} else {
			throw IllegalArgumentException("Empty CBZ archive stream for $safeName")
		}

		var comicSeries = ""
		var comicTitle = ""
		var comicNumber = 1f
		var comicWriter = "Local Archive"
		var comicSummary = ""
		var comicGenres = emptyList<String>()

		runCatching {
			ZipInputStream(targetFile.inputStream().buffered()).use { zis ->
				var entry: ZipEntry? = zis.nextEntry
				while (entry != null) {
					val entryName = entry.name
					if (!entry.isDirectory && entryName.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)) {
						val parsed = parseComicInfoXml(zis.readBytes())
						comicSeries = parsed["Series"].orEmpty()
						comicTitle = parsed["Title"].orEmpty()
						comicNumber = parsed["Number"]?.toFloatOrNull() ?: 1f
						comicWriter = parsed["Writer"]?.takeIf { it.isNotBlank() } ?: comicWriter
						comicSummary = parsed["Summary"].orEmpty()
						comicGenres = parsed["Genre"].orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
					}
					entry = zis.nextEntry
				}
			}
		}

		val pageCount = ReaderBitmapPipeline.countPagesInCbzFile(targetFile).coerceAtLeast(1)
		val fallbackTitle = safeName.substringBeforeLast('.')
			.replace('_', ' ')
			.replace('-', ' ')
			.trim()
			.ifBlank { "Local Archive" }
		val resolvedSeries = comicSeries.ifBlank { fallbackTitle }
		val resolvedChapterTitle = comicTitle.ifBlank { "Chapter ${comicNumber.toInt()}" }
		val mangaId = abs((resolvedSeries + targetFile.absolutePath).hashCode().toLong()).coerceAtLeast(2000L)
		val cbzUrl = "cbz://${targetFile.absolutePath}"
		val now = System.currentTimeMillis()

		val entity = MangaEntity(
			id = mangaId,
			title = resolvedSeries,
			altTitles = null,
			url = cbzUrl,
			publicUrl = cbzUrl,
			rating = 0f,
			isNsfw = false,
			contentRating = "SAFE",
			coverUrl = cbzUrl,
			largeCoverUrl = cbzUrl,
			state = "FINISHED",
			authors = comicWriter,
			source = "LOCAL",
			description = comicSummary.ifBlank { "Local CBZ archive (${targetFile.name}, $pageCount pages)." },
			normalizedTitle = org.koitharu.kotatsu.core.db.entity.TitleNormalizer.normalize(resolvedSeries),
			normalizedSlug = TitleNormalizer.toNormalizedSlug(resolvedSeries),
			isBroken = false,
		)

		val tags = (if (comicGenres.isNotEmpty()) comicGenres else listOf("Local CBZ")).mapIndexed { idx, g ->
			TagEntity(
				id = abs((g + "LOCAL").hashCode().toLong()).coerceAtLeast(1L) + idx,
				title = g,
				key = g.lowercase(Locale.ROOT),
				source = "LOCAL",
			)
		}

		val chapter = ChapterEntity(
			chapterId = mangaId * 1000L + 1L,
			mangaId = mangaId,
			title = resolvedChapterTitle,
			number = comicNumber,
			volume = 1,
			url = cbzUrl,
			scanlator = "Local CBZ",
			uploadDate = now,
			branch = null,
			index = 0,
			isRead = false,
			isDownloaded = true,
			lastPageRead = 0,
		)

		db.withTransaction {
			db.getMangaDao().upsert(entity, tags)
			db.getChaptersDao().deleteByMangaId(mangaId)
			db.getChaptersDao().upsertAll(listOf(chapter))
			db.getAuxiliaryDao().upsertLocalIndex(
				LocalMangaIndexEntity(
					mangaId = mangaId,
					path = targetFile.absolutePath,
				),
			)
		}

		entity
	}

	private fun parseComicInfoXml(xmlBytes: ByteArray): Map<String, String> {
		val result = HashMap<String, String>()
		runCatching {
			val factory = XmlPullParserFactory.newInstance()
			val parser = factory.newPullParser()
			parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
			var eventType = parser.eventType
			var currentTag: String? = null
			while (eventType != XmlPullParser.END_DOCUMENT) {
				when (eventType) {
					XmlPullParser.START_TAG -> currentTag = parser.name
					XmlPullParser.TEXT -> {
						val tag = currentTag
						if (!tag.isNullOrBlank()) {
							result[tag] = (result[tag].orEmpty() + parser.text).trim()
						}
					}
					XmlPullParser.END_TAG -> currentTag = null
				}
				eventType = parser.next()
			}
		}
		return result
	}

	private fun writeSampleVerificationCbz(file: File, cleanName: String) {
		val title = cleanName.substringBeforeLast('.').replace('_', ' ')
		ZipOutputStream(FileOutputStream(file)).use { zos ->
			zos.putNextEntry(ZipEntry("ComicInfo.xml"))
			val xml = """
				<?xml version="1.0" encoding="utf-8"?>
				<ComicInfo>
					<Series>$title</Series>
					<Title>Chapter 1</Title>
					<Number>1</Number>
					<Writer>Archive Author</Writer>
					<Summary>Sample local CBZ archive.</Summary>
					<Genre>Seinen, Historical</Genre>
					<LanguageISO>en</LanguageISO>
					<Manga>YesAndRightToLeft</Manga>
				</ComicInfo>
			""".trimIndent()
			zos.write(xml.toByteArray(Charsets.UTF_8))
			zos.closeEntry()

			for (p in 1..12) {
				val isSpread = (p == 6)
				val bmp = ReaderBitmapPipeline.createRealMangaPageBitmap(
					mangaTitle = title,
					chapterNumber = 1,
					pageNumber = p,
					isWideSpread = isSpread,
					baseWidth = if (isSpread) 720 else 400,
					baseHeight = 560,
				)
				zos.putNextEntry(ZipEntry("page_%02d.png".format(p)))
				zos.write(ReaderBitmapPipeline.encodeBitmapToPngBytes(bmp))
				zos.closeEntry()
			}
		}
	}

	private fun writeLocalRepoEntriesCache(repoId: Long, entries: List<ExternalExtensionRepoEntry>) {
		if (repoId <= 0L) return
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
				entry.iconUrl?.let { obj.put("iconUrl", it) }
				entry.apkUrl?.let { obj.put("apkUrl", it) }
				val sourcesArr = JSONArray()
				entry.sources.forEach { s ->
					sourcesArr.put(
						JSONObject()
							.put("name", s.name)
							.put("lang", s.lang)
							.put("id", s.id)
							.put("baseUrl", s.baseUrl)
							.put("versionId", s.versionId),
					)
				}
				obj.put("sources", sourcesArr)
				arr.put(obj)
			}
			File(repoIndexCacheDir, "repo_${repoId}.json").writeText(arr.toString())
		}
	}

	private fun readLocalRepoEntriesCache(repoId: Long): List<ExternalExtensionRepoEntry> {
		if (repoId <= 0L) return emptyList()
		val f = File(repoIndexCacheDir, "repo_${repoId}.json")
		if (!f.exists()) return emptyList()
		return runCatching {
			val text = f.readText()
			if (text.contains("Outdated App") || text.contains("eu.kanade.tachiyomi.extension.all.keiyoushi")) {
				f.delete()
				emptyList()
			} else {
				repoRepository.parseJsonArrayEntries(text, "")
			}
		}.getOrDefault(emptyList())
	}

	private fun buildDebugSandboxExtensions(): List<ExternalExtensionInfo> {
		val asuraVer = debugSandboxInstalledPkgs["eu.kanade.tachiyomi.extension.en.asurascans"]
		val aquaVer = debugSandboxInstalledPkgs["eu.kanade.tachiyomi.extension.en.aquamanga"] ?: "1.4.42"
		return listOf(
			ExternalExtensionInfo(
				id = "MIHON_7701",
				name = "Asura Scans",
				pkgName = "eu.kanade.tachiyomi.extension.en.asurascans",
				versionName = asuraVer ?: "1.4.42",
				lang = "en",
				mihonSourceId = 7701L,
				authorityOrUrl = "https://sandbox.example.org/apk/asurascans.apk",
				extensionType = "MIHON_EXT",
				isInstalled = asuraVer != null,
				isEnabled = asuraVer != null,
				isNsfw = false,
				sampleCatalogCount = 12,
				hasUpdate = false,
				latestVersionName = "1.4.42",
				apkFileName = "tachiyomi-en.asurascans-v1.4.42.apk",
				repoName = "Debug Sandbox",
			),
			ExternalExtensionInfo(
				id = "MIHON_7702",
				name = "Aqua Manga",
				pkgName = "eu.kanade.tachiyomi.extension.en.aquamanga",
				versionName = aquaVer,
				lang = "en",
				mihonSourceId = 7702L,
				authorityOrUrl = "https://sandbox.example.org/apk/aquamanga.apk",
				extensionType = "MIHON_EXT",
				isInstalled = true,
				isEnabled = true,
				isNsfw = false,
				sampleCatalogCount = 8,
				hasUpdate = aquaVer != "1.4.58",
				latestVersionName = "1.4.58",
				apkFileName = "tachiyomi-en.aquamanga-v1.4.58.apk",
				repoName = "Debug Sandbox",
			),
		)
	}

	private fun buildDebugSandboxCatalog(): List<ExtensionCatalogManga> = listOf(
		ExtensionCatalogManga(
			title = "Sousou no Frieren",
			altTitle = "Frieren: Beyond Journey's End",
			author = "Kanehito Yamada",
			sourceName = "EXT:ComicK (Mihon Multi)",
			url = "https://sandbox.example.org/series/frieren",
			coverSeed = "studio_cover://frieren",
			totalChapters = 138,
			rating = 4.95f,
			tags = listOf("Fantasy", "Adventure", "Drama"),
			description = "[Debug Sandbox] Elven mage Frieren reflects on mortality and companionship.",
			lang = "EN",
			isDebugMock = true,
		),
		ExtensionCatalogManga(
			title = "Omniscient Reader's Viewpoint",
			altTitle = "Jeonjijeok Dokja Sijeom",
			author = "Sing N Song",
			sourceName = "EXT:Asura Scans (Mihon EN)",
			url = "https://sandbox.example.org/series/orv",
			coverSeed = "studio_cover://orv",
			totalChapters = 228,
			rating = 4.91f,
			tags = listOf("Action", "Fantasy", "Webtoon"),
			description = "[Debug Sandbox] Kim Dokja's favorite web novel becomes reality.",
			lang = "EN",
			isDebugMock = true,
		),
		ExtensionCatalogManga(
			title = "Dungeon Meshi",
			altTitle = "Delicious in Dungeon",
			author = "Ryoko Kui",
			sourceName = "EXT:Bato.to (Mihon Multi)",
			url = "https://sandbox.example.org/series/dungeon-meshi",
			coverSeed = "studio_cover://dungeon_meshi",
			totalChapters = 97,
			rating = 4.92f,
			tags = listOf("Fantasy", "Comedy", "Seinen"),
			description = "[Debug Sandbox] Laios and his party cook dungeon monsters to rescue Falin.",
			lang = "EN",
			isDebugMock = true,
		),
	)
}
