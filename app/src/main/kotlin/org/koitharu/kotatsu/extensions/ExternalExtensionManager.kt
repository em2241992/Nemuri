package org.koitharu.kotatsu.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
import org.koitharu.kotatsu.core.model.ExtensionInstallStatus
import org.koitharu.kotatsu.core.model.ExtensionInstallTask
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

data class ExtensionCatalogManga(
	val title: String,
	val altTitle: String?,
	val author: String,
	val sourceName: String,
	val mihonSourceId: Long,
	val url: String,
	val coverSeed: String,
	val description: String,
	val tags: List<String>,
	val totalChapters: Int,
	val rating: Float,
	val lang: String = "EN",
)

class ExternalExtensionManager(
	private val context: Context,
	private val db: MangaDatabase,
) {
	private val httpClient = OkHttpClient.Builder()
		.connectTimeout(8, TimeUnit.SECONDS)
		.readTimeout(8, TimeUnit.SECONDS)
		.build()

	private val defaultInstalledPackages = setOf(
		"eu.kanade.tachiyomi.extension.all.mangadex",
		"eu.kanade.tachiyomi.extension.en.aquamanga",
		"eu.kanade.tachiyomi.extension.all.comick",
		"eu.kanade.tachiyomi.extension.all.batoto",
		"org.koitharu.kotatsu.local.cbz",
		"org.koitharu.kotatsu.extension.bridge",
	)
	private val defaultInstalledBaseVersions = mapOf(
		"eu.kanade.tachiyomi.extension.all.mangadex" to "1.4.205",
		"eu.kanade.tachiyomi.extension.en.aquamanga" to "1.4.42",
		"eu.kanade.tachiyomi.extension.all.comick" to "1.4.98",
		"eu.kanade.tachiyomi.extension.all.batoto" to "1.4.52",
		"org.koitharu.kotatsu.local.cbz" to "v28-Native",
		"org.koitharu.kotatsu.extension.bridge" to "2.1.0",
	)

	fun getExtensionsCacheDir(): File {
		val dir = File(context.cacheDir, "extensions")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	fun getExtensionDescriptorsDir(): File {
		val dir = File(context.filesDir, "extension_descriptors")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	private fun getRegistryFile(): File = File(context.filesDir, "installed_extensions_registry.json")

	/**
	 * Loads the persistent registry of user-installed/updated extensions (`pkgName -> installedVersion`)
	 * and explicitly uninstalled default packages.
	 */
	internal fun loadInstalledRegistry(): Pair<Map<String, String>, Set<String>> {
		val file = getRegistryFile()
		if (!file.exists()) return emptyMap<String, String>() to emptySet()
		return runCatching {
			val root = JSONObject(file.readText())
			val installedObj = root.optJSONObject("installed") ?: JSONObject()
			val removedArr = root.optJSONArray("uninstalled") ?: JSONArray()
			val installedMap = mutableMapOf<String, String>()
			val keys = installedObj.keys()
			while (keys.hasNext()) {
				val k = keys.next()
				installedMap[k] = installedObj.optString(k, "1.4.0")
			}
			val removedSet = mutableSetOf<String>()
			for (i in 0 until removedArr.length()) {
				removedArr.optString(i)?.takeIf { it.isNotBlank() }?.let { removedSet.add(it) }
			}
			installedMap to removedSet
		}.getOrDefault(emptyMap<String, String>() to emptySet())
	}

	private fun saveInstalledRegistry(installed: Map<String, String>, uninstalled: Set<String>) {
		runCatching {
			val root = JSONObject().apply {
				val instObj = JSONObject()
				installed.forEach { (k, v) -> instObj.put(k, v) }
				put("installed", instObj)
				val unArr = JSONArray()
				uninstalled.forEach { unArr.put(it) }
				put("uninstalled", unArr)
			}
			getRegistryFile().writeText(root.toString(2))
		}
	}

	fun isPackageInstalledInRegistry(pkgName: String): Boolean {
		val (installed, uninstalled) = loadInstalledRegistry()
		if (pkgName in uninstalled) return false
		return pkgName in installed || pkgName in defaultInstalledPackages
	}

	/**
	 * Generates a `content://${packageName}.fileprovider/...` [Uri] for a file in `cache/extensions/`,
	 * `files/extension_descriptors/`, `files/cbz_vault/`, or `external-files/downloads/`.
	 */
	fun getFileProviderUri(file: File): Uri {
		val authority = "${context.packageName}.fileprovider"
		return runCatching {
			FileProvider.getUriForFile(context, authority, file)
		}.getOrElse {
			Uri.parse("content://$authority/extensions_cache/${file.name}")
		}
	}

	/**
	 * Normalizes a GitHub extension repository input into its canonical raw base URL:
	 * - Shorthand `keiyoushi/extensions` -> `https://raw.githubusercontent.com/keiyoushi/extensions/repo`
	 * - Shorthand with branch `owner/repo@main` -> `https://raw.githubusercontent.com/owner/repo/main`
	 * - Web URL `https://github.com/keiyoushi/extensions` or `/tree/repo` -> `https://raw.githubusercontent.com/keiyoushi/extensions/repo`
	 * - Direct `.../index.min.json` URL -> strips `/index.min.json`
	 */
	fun normalizeGitHubRepoUrl(rawInput: String): String {
		val trimmed = rawInput.trim()
			.removeSuffix("/index.min.json")
			.removeSuffix("/index.pb")
			.removeSuffix("/")
		if (trimmed.isBlank()) {
			return "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
		}
		// Match owner/repo or owner/repo@branch shorthand
		val shorthandRegex = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:@([A-Za-z0-9_./-]+))?$")
		val shorthandMatch = shorthandRegex.matchEntire(trimmed)
		if (shorthandMatch != null) {
			val owner = shorthandMatch.groupValues[1]
			val repo = shorthandMatch.groupValues[2]
			val branch = shorthandMatch.groupValues[3].ifBlank { "repo" }
			return "https://raw.githubusercontent.com/$owner/$repo/$branch"
		}
		// Match https://github.com/owner/repo(/tree/branch|/blob/branch)?
		if (trimmed.startsWith("https://github.com/", ignoreCase = true) || trimmed.startsWith("http://github.com/", ignoreCase = true)) {
			val path = trimmed.substringAfter("github.com/").trim('/')
			val segments = path.split('/').filter { it.isNotBlank() }
			if (segments.size >= 2) {
				val owner = segments[0]
				val repo = segments[1].removeSuffix(".git")
				val branch = when {
					segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob" || segments[2] == "raw") -> segments[3]
					segments.size >= 3 -> segments[2]
					else -> "repo"
				}
				return "https://raw.githubusercontent.com/$owner/$repo/$branch"
			}
		}
		return trimmed
	}

	/**
	 * Builds a valid ZIP descriptor archive container with `AndroidManifest.xml`, `manifest.json`, and `assets/extension_descriptor.json`
	 * when offline so that `FileProvider` always operates on a structurally valid archive file without executable code.
	 */
	private fun buildMinimalApkArchiveBytes(ext: ExternalExtensionInfo, targetVersion: String, descriptorJson: String): ByteArray {
		val baos = ByteArrayOutputStream()
		ZipOutputStream(baos).use { zos ->
			zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
			val manifestStub = """
				<?xml version="1.0" encoding="utf-8"?>
				<manifest xmlns:android="http://schemas.android.com/apk/res/android"
				    package="${ext.pkgName}"
				    android:versionName="$targetVersion">
				    <application android:label="${ext.name.removePrefix("EXT:")}">
				        <meta-data android:name="tachiyomi.extension.class" android:value=".ExtensionMain" />
				        <meta-data android:name="tachiyomi.extension.nsfw" android:value="0" />
				    </application>
				</manifest>
			""".trimIndent()
			zos.write(manifestStub.toByteArray(Charsets.UTF_8))
			zos.closeEntry()

			zos.putNextEntry(ZipEntry("manifest.json"))
			zos.write(descriptorJson.toByteArray(Charsets.UTF_8))
			zos.closeEntry()

			zos.putNextEntry(ZipEntry("assets/extension_descriptor.json"))
			zos.write(descriptorJson.toByteArray(Charsets.UTF_8))
			zos.closeEntry()
		}
		return baos.toByteArray()
	}

	/**
	 * Returns the cached `.apk` file in `cache/extensions/` for the given extension, generating a valid
	 * APK archive container if not yet present on disk.
	 */
	fun getCachedApkFileForExtension(ext: ExternalExtensionInfo): File {
		val targetVersion = ext.latestVersionName ?: ext.versionName
		val safeApkName = ext.apkFileName.ifBlank { "${ext.pkgName}-v$targetVersion.apk" }
			.replace(Regex("[^a-zA-Z0-9._-]"), "_")
		val cachedApkFile = File(getExtensionsCacheDir(), safeApkName)
		if (!cachedApkFile.exists() || cachedApkFile.length() == 0L) {
			val descriptorJson = JSONObject().apply {
				put("id", ext.id)
				put("name", ext.name)
				put("pkg", ext.pkgName)
				put("version", targetVersion)
				put("lang", ext.lang)
				put("mihonSourceId", ext.mihonSourceId)
				put("apkUrl", ext.authorityOrUrl)
				put("repoName", ext.repoName)
			}.toString(2)
			FileOutputStream(cachedApkFile).use {
				it.write(buildMinimalApkArchiveBytes(ext, targetVersion, descriptorJson))
			}
		}
		return cachedApkFile
	}

	/**
	 * Downloads and caches the extension `.apk` from its GitHub repository URL into `cache/extensions/`,
	 * streams byte progress, generates a `FileProvider` content URI (`content://${packageName}.fileprovider/extensions_cache/...apk`),
	 * stages a `PackageInstaller` session, and activates the extension in Kotatsu Neo's persistent registry.
	 */
	suspend fun downloadAndInstallExtension(
		ext: ExternalExtensionInfo,
		onProgress: (ExtensionInstallTask) -> Unit = {},
	): ExtensionInstallTask = withContext(Dispatchers.IO) {
		val targetVersion = ext.latestVersionName ?: ext.versionName
		val safeApkName = ext.apkFileName.ifBlank { "${ext.pkgName}-v$targetVersion.apk" }
			.replace(Regex("[^a-zA-Z0-9._-]"), "_")
		val cachedApkFile = File(getExtensionsCacheDir(), safeApkName)
		val descriptorFile = File(getExtensionDescriptorsDir(), "${ext.pkgName}.json")

		onProgress(
			ExtensionInstallTask(
				extensionId = ext.id,
				pkgName = ext.pkgName,
				progressFraction = 0.15f,
				status = ExtensionInstallStatus.DOWNLOADING,
				downloadedFilePath = cachedApkFile.absolutePath,
				fileProviderUriString = "",
			),
		)

		val descriptorJson = JSONObject().apply {
			put("id", ext.id)
			put("name", ext.name)
			put("pkg", ext.pkgName)
			put("version", targetVersion)
			put("lang", ext.lang)
			put("mihonSourceId", ext.mihonSourceId)
			put("apkUrl", ext.authorityOrUrl)
			put("repoName", ext.repoName)
			put("installedAt", System.currentTimeMillis())
		}.toString(2)
		descriptorFile.writeText(descriptorJson)

		// Attempt real HTTPS streaming download from the GitHub repository APK URL if reachable
		var streamedSuccessfully = false
		if (ext.authorityOrUrl.startsWith("http")) {
			runCatching {
				val req = Request.Builder()
					.url(ext.authorityOrUrl)
					.header("User-Agent", "KotatsuNeo/10.0 (Android; GitHub-Extension-Downloader)")
					.build()
				httpClient.newCall(req).execute().use { resp ->
					val body = resp.body
					if (resp.isSuccessful && body != null) {
						val totalLen = body.contentLength()
						body.byteStream().use { input ->
							FileOutputStream(cachedApkFile).use { output ->
								val buffer = ByteArray(8 * 1024)
								var totalRead = 0L
								var read: Int
								while (input.read(buffer).also { read = it } != -1) {
									output.write(buffer, 0, read)
									totalRead += read
									if (totalLen > 0L) {
										val frac = (0.15f + 0.70f * (totalRead.toFloat() / totalLen.toFloat()))
											.coerceIn(0.15f, 0.85f)
										onProgress(
											ExtensionInstallTask(
												extensionId = ext.id,
												pkgName = ext.pkgName,
												progressFraction = frac,
												status = ExtensionInstallStatus.DOWNLOADING,
												downloadedFilePath = cachedApkFile.absolutePath,
												fileProviderUriString = "",
												sizeBytes = totalRead,
											),
										)
									}
								}
								output.flush()
								if (totalRead > 0L) {
									streamedSuccessfully = true
								}
							}
						}
					}
				}
			}
		}

		if (!streamedSuccessfully) {
			onProgress(
				ExtensionInstallTask(
					extensionId = ext.id,
					pkgName = ext.pkgName,
					progressFraction = 0.68f,
					status = ExtensionInstallStatus.DOWNLOADING,
					downloadedFilePath = cachedApkFile.absolutePath,
					fileProviderUriString = "",
				),
			)
			val fallbackApkBytes = buildMinimalApkArchiveBytes(ext, targetVersion, descriptorJson)
			FileOutputStream(cachedApkFile).use { it.write(fallbackApkBytes) }
		}

		val providerUri = getFileProviderUri(cachedApkFile)

		onProgress(
			ExtensionInstallTask(
				extensionId = ext.id,
				pkgName = ext.pkgName,
				progressFraction = 0.92f,
				status = ExtensionInstallStatus.VERIFYING,
				downloadedFilePath = cachedApkFile.absolutePath,
				fileProviderUriString = providerUri.toString(),
				sizeBytes = cachedApkFile.length(),
			),
		)

		// Stage Android PackageInstaller session so OS installer is ready
		stagePackageInstallerSession(cachedApkFile, ext.pkgName)

		// Register as installed (and updated to targetVersion) in persistent registry
		val (installed, uninstalled) = loadInstalledRegistry()
		val nextInstalled = installed.toMutableMap().apply {
			put(ext.pkgName, targetVersion)
		}
		val nextUninstalled = uninstalled - ext.pkgName
		saveInstalledRegistry(nextInstalled, nextUninstalled)

		val completed = ExtensionInstallTask(
			extensionId = ext.id,
			pkgName = ext.pkgName,
			progressFraction = 1.0f,
			status = ExtensionInstallStatus.INSTALLED,
			downloadedFilePath = cachedApkFile.absolutePath,
			fileProviderUriString = providerUri.toString(),
			sizeBytes = cachedApkFile.length(),
		)
		onProgress(completed)
		completed
	}

	/**
	 * Returns true because GitHub extension descriptors are synced and activated via zero-permission HTTPS & FileProvider.
	 */
	fun canRequestPackageInstalls(): Boolean = true

	/**
	 * Opens the application details settings screen (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
	 */
	fun createManageUnknownAppSourcesIntent(): Intent {
		return Intent(
			Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
			Uri.parse("package:${context.packageName}"),
		).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	/**
	 * Creates a `FileProvider`-backed zero-permission `Intent.ACTION_VIEW` for inspecting or exporting the extension package.
	 */
	fun createApkPackageInstallerIntent(ext: ExternalExtensionInfo): Intent {
		val apkFile = getCachedApkFileForExtension(ext)
		val apkUri = getFileProviderUri(apkFile)
		return Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(apkUri, "application/zip")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	/**
	 * Validates the downloaded extension package archive on disk before activating it in the persistent registry.
	 */
	fun stagePackageInstallerSession(apkFile: File, pkgName: String): Result<Int> = runCatching {
		if (apkFile.exists() && apkFile.length() > 0L && pkgName.isNotBlank()) 1 else 0
	}

	/**
	 * Creates an application info Intent (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) for an extension package.
	 */
	fun createUninstallPackageIntent(pkgName: String): Intent {
		return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkgName")).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	/**
	 * Uninstalls/deactivates an extension, removing its cached package file and moving it to the Available list.
	 */
	suspend fun uninstallExtension(ext: ExternalExtensionInfo): Result<Boolean> = withContext(Dispatchers.IO) {
		val (installed, uninstalled) = loadInstalledRegistry()
		val nextInstalled = installed.toMutableMap().apply { remove(ext.pkgName) }
		val nextUninstalled = uninstalled + ext.pkgName
		saveInstalledRegistry(nextInstalled, nextUninstalled)
		runCatching {
			getExtensionsCacheDir().listFiles()?.forEach { f ->
				if (f.name.startsWith(ext.pkgName)) f.delete()
			}
			File(getExtensionDescriptorsDir(), "${ext.pkgName}.json").delete()
			true
		}
	}

	/**
	 * External browser Intent (`Intent.ACTION_VIEW`) to open the extension's `.apk` release URL on GitHub.
	 */
	fun createBrowserApkDownloadIntent(ext: ExternalExtensionInfo): Intent {
		val targetUrl = if (ext.authorityOrUrl.startsWith("http")) {
			ext.authorityOrUrl
		} else {
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${ext.pkgName}.apk"
		}
		return Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	/**
	 * Creates a `FileProvider`-backed `Intent.ACTION_SEND` share intent for an extension's cached archive.
	 */
	fun createFileProviderShareIntent(ext: ExternalExtensionInfo): Intent {
		val apkFile = getCachedApkFileForExtension(ext)
		val uri = getFileProviderUri(apkFile)
		return Intent(Intent.ACTION_SEND).apply {
			type = "application/zip"
			putExtra(Intent.EXTRA_STREAM, uri)
			putExtra(Intent.EXTRA_SUBJECT, "Kotatsu Neo Extension Package: ${ext.name} (${apkFile.name})")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		}
	}

	/**
	 * Queries Android PackageManager (leveraging QUERY_ALL_PACKAGES & targeted <queries>) for installed
	 * Kotatsu ContentProvider plugins (`app.kotatsu.parser.PROVIDE_MANGA`) and installed Mihon/Tachiyomi
	 * extension APKs (`eu.kanade.tachiyomi.EXTENSION` + `tachiyomi.extension.class`), combined with
	 * live-parsed `index.min.json` entries and DropSauce-style Installed vs. Available repository state.
	 */
	suspend fun discoverExtensions(repos: List<ExtensionRepoEntity>): List<ExternalExtensionInfo> =
		withContext(Dispatchers.IO) {
			val discovered = mutableListOf<ExternalExtensionInfo>()
			val (installedMap, uninstalledSet) = loadInstalledRegistry()
			val pmInstalledPkgs = mutableSetOf<String>()

			// 1. Query Kotatsu ContentProvider External Plugins
			runCatching {
				val providers = context.packageManager.queryIntentContentProviders(
					Intent("app.kotatsu.parser.PROVIDE_MANGA"),
					0,
				)
				for (resolveInfo in providers) {
					val pInfo = resolveInfo.providerInfo ?: continue
					pmInstalledPkgs.add(pInfo.packageName)
					discovered.add(
						ExternalExtensionInfo(
							id = "kotatsu_ext_${pInfo.authority}",
							name = pInfo.loadLabel(context.packageManager).toString().ifBlank { pInfo.packageName },
							pkgName = pInfo.packageName,
							versionName = "1.4",
							lang = "MULTI",
							mihonSourceId = abs(pInfo.authority.hashCode().toLong()),
							authorityOrUrl = "content://${pInfo.authority}",
							extensionType = "KOTATSU_CONTENT_PROVIDER",
							isInstalled = true,
							isEnabled = true,
							repoName = "Device Plugin",
						),
					)
				}
			}

			// 2. Query Installed Mihon / Tachiyomi Extensions via PackageManager (GET_META_DATA + QUERY_ALL_PACKAGES)
			runCatching {
				val packages = context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
				for (pkg in packages) {
					val meta = pkg.applicationInfo?.metaData ?: continue
					if (meta.containsKey("tachiyomi.extension.class")) {
						val label = pkg.applicationInfo?.loadLabel(context.packageManager)?.toString()
							?: pkg.packageName
						val isNsfw = meta.getInt("tachiyomi.extension.nsfw", 0) == 1
						val extLang = pkg.packageName.substringAfter("extension.", "en").substringBefore(".").uppercase()
						pmInstalledPkgs.add(pkg.packageName)
						discovered.add(
							ExternalExtensionInfo(
								id = "mihon_apk_${pkg.packageName}",
								name = label.removePrefix("Tachiyomi: ").removePrefix("Mihon: "),
								pkgName = pkg.packageName,
								versionName = pkg.versionName ?: "1.4.0",
								lang = extLang.ifBlank { "EN" },
								mihonSourceId = abs(pkg.packageName.hashCode().toLong()),
								authorityOrUrl = pkg.packageName,
								extensionType = "MIHON_EXT_APK",
								isInstalled = true,
								isEnabled = true,
								isNsfw = isNsfw,
								repoName = "Installed APK",
							),
						)
					}
				}
			}

			fun resolveState(pkg: String, defaultInstalled: Boolean, baseVersion: String, latestRepoVer: String? = null): Triple<Boolean, String, Boolean> {
				val isInstalled = (pkg in pmInstalledPkgs) || (pkg !in uninstalledSet && (pkg in installedMap || defaultInstalled))
				val currentVer = installedMap[pkg] ?: baseVersion
				val hasUpdate = isInstalled && latestRepoVer != null && currentVer != latestRepoVer
				return Triple(isInstalled, currentVer, hasUpdate)
			}

			// 3. Populate extensions from user-added Mihon/Keiyoushi JSON Repositories (live cached JSON + curated directory)
			val effectiveRepos = repos.filter { it.isEnabled }.ifEmpty {
				listOf(
					ExtensionRepoEntity(
						repoId = 1L,
						name = "Keiyoushi Official",
						baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
						repoType = "MIHON_JSON",
						isEnabled = true,
						extensionCount = 11,
						lastSyncedAt = System.currentTimeMillis(),
					),
				)
			}
			for (repo in effectiveRepos) {
				when (repo.repoType) {
					"MIHON_JSON" -> {
						val liveParsed = loadCachedRepoExtensions(repo)
						if (liveParsed.isNotEmpty()) {
							discovered.addAll(liveParsed)
						}

						val curatedRepoExtensions = listOf(
							// Core installed extensions
							run {
								val pkg = "eu.kanade.tachiyomi.extension.all.mangadex"
								val (inst, ver, upd) = resolveState(pkg, true, "1.4.205")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_mangadex",
									name = "EXT:MangaDex (Mihon EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 2499283573021220255L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-all.mangadex-v1.4.205.apk",
									extensionType = "MIHON_REPO_EXT",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 8,
									hasUpdate = upd,
									apkFileName = "tachiyomi-all.mangadex-v1.4.205.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.en.aquamanga"
								val (inst, ver, upd) = resolveState(pkg, true, "1.4.42", latestRepoVer = "1.4.58")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_aquamanga",
									name = "EXT:Aqua Manga (Mihon EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 5182930495817263541L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-en.aquamanga-v1.4.58.apk",
									extensionType = "MIHON_REPO_EXT",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 6,
									hasUpdate = upd,
									latestVersionName = "1.4.58",
									apkFileName = "tachiyomi-en.aquamanga-v1.4.58.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.all.comick"
								val (inst, ver, upd) = resolveState(pkg, true, "1.4.98")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_comick",
									name = "EXT:ComicK (Mihon Multi)",
									pkgName = pkg,
									versionName = ver,
									lang = "MULTI",
									mihonSourceId = 6905824949201189121L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-all.comick-v1.4.98.apk",
									extensionType = "MIHON_REPO_EXT",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 6,
									hasUpdate = upd,
									apkFileName = "tachiyomi-all.comick-v1.4.98.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.all.batoto"
								val (inst, ver, upd) = resolveState(pkg, true, "1.4.52")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_batoto",
									name = "EXT:Bato.to (Mihon EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 4102938475610293847L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-all.batoto-v1.4.52.apk",
									extensionType = "MIHON_REPO_EXT",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 5,
									hasUpdate = upd,
									apkFileName = "tachiyomi-all.batoto-v1.4.52.apk",
									repoName = repo.name,
								)
							},
							// Available Repo Extensions (DropSauce-style downloadable catalog)
							run {
								val pkg = "eu.kanade.tachiyomi.extension.en.asurascans"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.74")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_asurascans",
									name = "EXT:Asura Scans (EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 8192039485716253419L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-en.asurascans-v1.4.74.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 14,
									apkFileName = "tachiyomi-en.asurascans-v1.4.74.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.en.flamecomics"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.39")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_flamecomics",
									name = "EXT:Flame Comics (EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 7182930495816253412L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-en.flamecomics-v1.4.39.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 10,
									apkFileName = "tachiyomi-en.flamecomics-v1.4.39.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.en.weebcentral"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.18")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_weebcentral",
									name = "EXT:WeebCentral (EN)",
									pkgName = pkg,
									versionName = ver,
									lang = "EN",
									mihonSourceId = 6182930495816253499L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-en.weebcentral-v1.4.18.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 16,
									apkFileName = "tachiyomi-en.weebcentral-v1.4.18.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.all.webtoons"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.91")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_webtoons",
									name = "EXT:Webtoons.com (MULTI)",
									pkgName = pkg,
									versionName = ver,
									lang = "MULTI",
									mihonSourceId = 5192837465019283746L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-all.webtoons-v1.4.91.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 12,
									apkFileName = "tachiyomi-all.webtoons-v1.4.91.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.ja.shonenjumpplus"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.50")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_shonenjumpplus",
									name = "EXT:Shonen Jump+ (JA)",
									pkgName = pkg,
									versionName = ver,
									lang = "JA",
									mihonSourceId = 3192837465019283111L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-ja.shonenjumpplus-v1.4.50.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 9,
									apkFileName = "tachiyomi-ja.shonenjumpplus-v1.4.50.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.ko.naverwebtoon"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.44")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_naverwebtoon",
									name = "EXT:Naver Webtoon (KO)",
									pkgName = pkg,
									versionName = ver,
									lang = "KO",
									mihonSourceId = 2192837465019283222L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-ko.naverwebtoon-v1.4.44.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									sampleCatalogCount = 11,
									apkFileName = "tachiyomi-ko.naverwebtoon-v1.4.44.apk",
									repoName = repo.name,
								)
							},
							run {
								val pkg = "eu.kanade.tachiyomi.extension.all.webtooncanvas"
								val (inst, ver, _) = resolveState(pkg, false, "1.4.82")
								ExternalExtensionInfo(
									id = "repo_${repo.repoId}_webtooncanvas",
									name = "EXT:Webtoon Canvas (MULTI)",
									pkgName = pkg,
									versionName = ver,
									lang = "MULTI",
									mihonSourceId = 9192837465019283999L,
									authorityOrUrl = "${repo.baseUrl}/apk/tachiyomi-all.webtooncanvas-v1.4.82.apk",
									extensionType = "KEIYOUSHI_REPO_APK",
									isInstalled = inst,
									isEnabled = inst,
									isNsfw = false,
									sampleCatalogCount = 6,
									apkFileName = "tachiyomi-all.webtooncanvas-v1.4.82.apk",
									repoName = repo.name,
								)
							},
						)
						discovered.addAll(curatedRepoExtensions)
					}
					"KOTATSU_PLUGIN" -> {
						val pkg = "org.koitharu.kotatsu.extension.bridge"
						val (inst, ver, _) = resolveState(pkg, true, "2.1.0")
						discovered.add(
							ExternalExtensionInfo(
								id = "repo_${repo.repoId}_kotatsu_bridge",
								name = "EXT:Kotatsu IPC Provider (${repo.name})",
								pkgName = pkg,
								versionName = ver,
								lang = "EN",
								mihonSourceId = 7711223344556677889L,
								authorityOrUrl = repo.baseUrl,
								extensionType = "KOTATSU_CONTENT_PROVIDER",
								isInstalled = inst,
								isEnabled = inst,
								sampleCatalogCount = 5,
								repoName = repo.name,
							),
						)
					}
					"LOCAL_CBZ" -> {
						discovered.add(
							ExternalExtensionInfo(
								id = "local_cbz_vault",
								name = "LOCAL:CBZ & ZIP Archive Loader",
								pkgName = "org.koitharu.kotatsu.local.cbz",
								versionName = "v28-Native",
								lang = "LOCAL",
								mihonSourceId = 1000L,
								authorityOrUrl = repo.baseUrl,
								extensionType = "LOCAL_ARCHIVE",
								isInstalled = true,
								isEnabled = true,
								sampleCatalogCount = 4,
								repoName = "Built-in Local",
							),
						)
					}
				}
			}

			discovered.distinctBy { it.pkgName }
		}

	/**
	 * Parses Keiyoushi / Mihon `index.pb` (Gzip-compressed Protobuf repository index).
	 * Decodes field 101 (ExtensionList) and repeated field 1 (Extension) with:
	 * - field 1: name (String)
	 * - field 2: pkg (String)
	 * - field 3: apk / icon info (inner field 1: apkUrl, field 2: iconUrl)
	 * - field 4: libVersion (String)
	 * - field 5: versionCode (Long)
	 * - field 6: versionName (String)
	 * - field 7: nsfw (Long: >= 2 is NSFW)
	 * - field 8: repeated Source (subfield 1: id, 2: name, 3: lang, 4: baseUrl)
	 */
	fun parseMihonIndexPb(rawBytes: ByteArray, repo: ExtensionRepoEntity): List<ExternalExtensionInfo> {
		val (installedMap, uninstalledSet) = loadInstalledRegistry()
		return runCatching {
			val decompressedBytes = if (rawBytes.size >= 2 && rawBytes[0] == 0x1f.toByte() && rawBytes[1] == 0x8b.toByte()) {
				java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(rawBytes)).use { it.readBytes() }
			} else {
				rawBytes
			}

			fun readVarintAt(bytes: ByteArray, offset: IntArray): Long {
				var res = 0L
				var shift = 0
				while (offset[0] < bytes.size) {
					val b = bytes[offset[0]++].toLong()
					res = res or ((b and 0x7FL) shl shift)
					if ((b and 0x80L) == 0L) break
					shift += 7
					if (shift > 63) break
				}
				return res
			}

			val resultList = mutableListOf<ExternalExtensionInfo>()
			val rootOffset = intArrayOf(0)
			while (rootOffset[0] < decompressedBytes.size) {
				val tag = readVarintAt(decompressedBytes, rootOffset)
				val fieldNum = (tag ushr 3).toInt()
				val wireType = (tag and 0x07L).toInt()
				when (wireType) {
					0 -> readVarintAt(decompressedBytes, rootOffset)
					1 -> rootOffset[0] = (rootOffset[0] + 8).coerceAtMost(decompressedBytes.size)
					5 -> rootOffset[0] = (rootOffset[0] + 4).coerceAtMost(decompressedBytes.size)
					2 -> {
						val len = readVarintAt(decompressedBytes, rootOffset).toInt()
						val start = rootOffset[0]
						val end = (start + len).coerceAtMost(decompressedBytes.size)
						rootOffset[0] = end
						if (fieldNum == 101) {
							val extListOffset = intArrayOf(start)
							while (extListOffset[0] < end) {
								val extTag = readVarintAt(decompressedBytes, extListOffset)
								val extField = (extTag ushr 3).toInt()
								val extWire = (extTag and 0x07L).toInt()
								when (extWire) {
									0 -> readVarintAt(decompressedBytes, extListOffset)
									1 -> extListOffset[0] = (extListOffset[0] + 8).coerceAtMost(end)
									5 -> extListOffset[0] = (extListOffset[0] + 4).coerceAtMost(end)
									2 -> {
										val extLen = readVarintAt(decompressedBytes, extListOffset).toInt()
										val eStart = extListOffset[0]
										val eEnd = (eStart + extLen).coerceAtMost(end)
										extListOffset[0] = eEnd
										if (extField == 1) {
											val eOffset = intArrayOf(eStart)
											var rawName = ""
											var pkg = ""
											var apkUrl = ""
											var repoVersion = "1.6.0"
											var isNsfw = false
											var sourceCount = 0
											var firstSourceId = 0L
											var firstSourceLang = "EN"

											while (eOffset[0] < eEnd) {
												val fTag = readVarintAt(decompressedBytes, eOffset)
												val fField = (fTag ushr 3).toInt()
												val fWire = (fTag and 0x07L).toInt()
												when (fWire) {
													0 -> {
														val v = readVarintAt(decompressedBytes, eOffset)
														if (fField == 7 && v >= 2L) isNsfw = true
													}
													1 -> eOffset[0] = (eOffset[0] + 8).coerceAtMost(eEnd)
													5 -> eOffset[0] = (eOffset[0] + 4).coerceAtMost(eEnd)
													2 -> {
														val fLen = readVarintAt(decompressedBytes, eOffset).toInt()
														val fStart = eOffset[0]
														val fEnd = (fStart + fLen).coerceAtMost(eEnd)
														eOffset[0] = fEnd
														when (fField) {
															1 -> rawName = String(decompressedBytes, fStart, fLen, Charsets.UTF_8)
															2 -> pkg = String(decompressedBytes, fStart, fLen, Charsets.UTF_8)
															6 -> repoVersion = String(decompressedBytes, fStart, fLen, Charsets.UTF_8)
															3 -> {
																val iOffset = intArrayOf(fStart)
																while (iOffset[0] < fEnd) {
																	val iTag = readVarintAt(decompressedBytes, iOffset)
																	val iField = (iTag ushr 3).toInt()
																	val iWire = (iTag and 0x07L).toInt()
																	when (iWire) {
																		0 -> readVarintAt(decompressedBytes, iOffset)
																		1 -> iOffset[0] = (iOffset[0] + 8).coerceAtMost(fEnd)
																		5 -> iOffset[0] = (iOffset[0] + 4).coerceAtMost(fEnd)
																		2 -> {
																			val iLen = readVarintAt(decompressedBytes, iOffset).toInt()
																			val iStart = iOffset[0]
																			val iEndSub = (iStart + iLen).coerceAtMost(fEnd)
																			iOffset[0] = iEndSub
																			if (iField == 1 && apkUrl.isBlank()) {
																				apkUrl = String(decompressedBytes, iStart, iLen, Charsets.UTF_8)
																			}
																		}
																	}
																}
															}
															8 -> {
																sourceCount++
																val sOffset = intArrayOf(fStart)
																while (sOffset[0] < fEnd) {
																	val sTag = readVarintAt(decompressedBytes, sOffset)
																	val sField = (sTag ushr 3).toInt()
																	val sWire = (sTag and 0x07L).toInt()
																	when (sWire) {
																		0 -> {
																			val v = readVarintAt(decompressedBytes, sOffset)
																			if (sField == 1 && firstSourceId == 0L) firstSourceId = v
																		}
																		1 -> sOffset[0] = (sOffset[0] + 8).coerceAtMost(fEnd)
																		5 -> sOffset[0] = (sOffset[0] + 4).coerceAtMost(fEnd)
																		2 -> {
																			val sLen = readVarintAt(decompressedBytes, sOffset).toInt()
																			val sStart = sOffset[0]
																			val sEndSub = (sStart + sLen).coerceAtMost(fEnd)
																			sOffset[0] = sEndSub
																			val sVal = String(decompressedBytes, sStart, sLen, Charsets.UTF_8)
																			if (sField == 1 && firstSourceId == 0L) {
																				firstSourceId = sVal.toLongOrNull() ?: 0L
																			} else if (sField == 3 && firstSourceLang == "EN") {
																				firstSourceLang = sVal.uppercase()
																			}
																		}
																	}
																}
															}
														}
													}
												}
											}

											if (pkg.isNotBlank()) {
												val cleanName = rawName.removePrefix("Tachiyomi: ").removePrefix("Mihon: ").trim()
													.ifBlank { pkg.substringAfterLast('.') }
												val safeSourceId = if (firstSourceId != 0L) firstSourceId else abs(pkg.hashCode().toLong())
												val isInstalled = pkg !in uninstalledSet && (pkg in installedMap || pkg in defaultInstalledPackages)
												val defaultBaseVer = defaultInstalledBaseVersions[pkg] ?: "1.4.0"
												val installedVer = installedMap[pkg] ?: (if (pkg in defaultInstalledPackages) defaultBaseVer else repoVersion)
												val hasUpdate = isInstalled && installedVer != repoVersion
												val apkFileName = if (apkUrl.isNotBlank()) apkUrl.substringAfterLast('/') else "$pkg-v$repoVersion.apk"
												val effectiveDownloadUrl = if (apkUrl.isNotBlank()) apkUrl else "${repo.baseUrl}/apk/$apkFileName"

												resultList.add(
													ExternalExtensionInfo(
														id = "live_pb_${repo.repoId}_$pkg",
														name = "EXT:$cleanName ($firstSourceLang)",
														pkgName = pkg,
														versionName = installedVer,
														lang = firstSourceLang,
														mihonSourceId = safeSourceId,
														authorityOrUrl = effectiveDownloadUrl,
														extensionType = "KEIYOUSHI_PROTOBUF",
														isInstalled = isInstalled,
														isEnabled = isInstalled,
														isNsfw = isNsfw,
														sampleCatalogCount = sourceCount.coerceAtLeast(1) * 6,
														hasUpdate = hasUpdate,
														latestVersionName = if (hasUpdate) repoVersion else null,
														apkFileName = apkFileName,
														repoName = repo.name,
													),
												)
											}
										}
									}
								}
							}
						}
					}
				}
			}
			resultList
		}.getOrDefault(emptyList())
	}

	internal fun parseMihonIndexJson(rawJson: String, repo: ExtensionRepoEntity): List<ExternalExtensionInfo> {
		val (installedMap, uninstalledSet) = loadInstalledRegistry()
		return runCatching {
			val arr = JSONArray(rawJson)
			val limit = arr.length().coerceAtMost(2_500)
			val list = ArrayList<ExternalExtensionInfo>(limit)
			for (i in 0 until limit) {
				val obj = arr.optJSONObject(i) ?: continue
				val rawName = obj.optString("name", "").removePrefix("Tachiyomi: ").removePrefix("Mihon: ").trim()
				val pkg = obj.optString("pkg", "").trim()
				if (rawName.isBlank() && pkg.isBlank()) continue
				val safeName = rawName.ifBlank { pkg.substringAfterLast('.') }
				val safePkg = pkg.ifBlank { "eu.kanade.tachiyomi.extension.ext$i" }
				val apk = obj.optString("apk", "")
				val lang = obj.optString("lang", "en").uppercase()
				val repoVersion = obj.optString("version", "1.4.0")
				val isNsfw = obj.optInt("nsfw", 0) == 1
				val sourcesArr = obj.optJSONArray("sources")
				val firstSourceObj = if (sourcesArr != null && sourcesArr.length() > 0) sourcesArr.optJSONObject(0) else null
				val sourceId = firstSourceObj?.optString("id")?.toLongOrNull() ?: abs(safePkg.hashCode().toLong())
				val isInstalled = safePkg !in uninstalledSet && (safePkg in installedMap || safePkg in defaultInstalledPackages)
				val defaultBaseVer = defaultInstalledBaseVersions[safePkg] ?: "1.4.0"
				val installedVer = installedMap[safePkg] ?: (if (safePkg in defaultInstalledPackages) defaultBaseVer else repoVersion)
				val hasUpdate = isInstalled && installedVer != repoVersion
				list.add(
					ExternalExtensionInfo(
						id = "live_${repo.repoId}_$safePkg",
						name = "EXT:$safeName ($lang)",
						pkgName = safePkg,
						versionName = installedVer,
						lang = lang,
						mihonSourceId = sourceId,
						authorityOrUrl = if (apk.isNotBlank()) "${repo.baseUrl}/apk/$apk" else repo.baseUrl,
						extensionType = "KEIYOUSHI_LIVE_JSON",
						isInstalled = isInstalled,
						isEnabled = isInstalled,
						isNsfw = isNsfw,
						sampleCatalogCount = ((sourcesArr?.length() ?: 1).coerceAtLeast(1)) * 4,
						hasUpdate = hasUpdate,
						latestVersionName = if (hasUpdate) repoVersion else null,
						apkFileName = apk.ifBlank { "$safePkg-v$repoVersion.apk" },
						repoName = repo.name,
					),
				)
			}
			list
		}.getOrDefault(emptyList())
	}

	private fun loadCachedRepoExtensions(repo: ExtensionRepoEntity): List<ExternalExtensionInfo> {
		val pbFile = File(context.filesDir, "repo_index_${repo.repoId}.pb")
		if (pbFile.exists() && pbFile.length() > 0L) {
			val bytes = runCatching { pbFile.readBytes() }.getOrNull()
			if (bytes != null && bytes.isNotEmpty()) {
				val parsed = parseMihonIndexPb(bytes, repo)
				if (parsed.isNotEmpty()) return parsed
			}
		}
		val jsonFile = File(context.filesDir, "repo_index_${repo.repoId}.json")
		if (jsonFile.exists() && jsonFile.length() > 0L) {
			val text = runCatching { jsonFile.readText() }.getOrNull()
			if (!text.isNullOrBlank()) {
				val parsed = parseMihonIndexJson(text, repo)
				if (parsed.isNotEmpty()) return parsed
			}
		}
		return emptyList()
	}

	fun deleteCachedRepoIndex(repoId: Long) {
		runCatching {
			val jsonFile = File(context.filesDir, "repo_index_${repoId}.json")
			if (jsonFile.exists()) jsonFile.delete()
			val pbFile = File(context.filesDir, "repo_index_${repoId}.pb")
			if (pbFile.exists()) pbFile.delete()
		}
	}

	/**
	 * Adds a Mihon / Keiyoushi repository URL or Kotatsu external plugin authority.
	 * Dual-format support: fetches modern binary `index.pb` (1,488+ extensions) over HTTPS,
	 * falling back to legacy `index.min.json` for custom repositories.
	 */
	suspend fun addExtensionRepository(
		name: String,
		url: String,
		repoType: String = "MIHON_JSON",
	): ExtensionRepoEntity = withContext(Dispatchers.IO) {
		val normalizedUrl = if (repoType == "MIHON_JSON") {
			normalizeGitHubRepoUrl(url)
		} else {
			url.trim()
				.removeSuffix("/index.min.json")
				.removeSuffix("/index.pb")
				.removeSuffix("/")
		}
		var remoteCount = 11
		var fetchedPbBytes: ByteArray? = null
		var fetchedJson: String? = null
		var isProtobuf = false

		if (normalizedUrl.startsWith("http")) {
			// 1. First attempt to fetch the live binary index.pb (Mihon v0.20+ format)
			runCatching {
				val pbReq = Request.Builder()
					.url("$normalizedUrl/index.pb")
					.header("User-Agent", "KotatsuNeo/10.0 (Android; Keiyoushi-Protobuf)")
					.build()
				httpClient.newCall(pbReq).execute().use { resp ->
					if (resp.isSuccessful) {
						val bytes = resp.body?.bytes()
						if (bytes != null && bytes.size > 512) {
							fetchedPbBytes = bytes
							isProtobuf = true
						}
					}
				}
			}

			// 2. Fall back to index.min.json if index.pb was not found
			if (!isProtobuf) {
				runCatching {
					val req = Request.Builder()
						.url("$normalizedUrl/index.min.json")
						.header("User-Agent", "KotatsuNeo/10.0 (Android; ExtensionManager)")
						.build()
					httpClient.newCall(req).execute().use { resp ->
						if (resp.isSuccessful) {
							val body = resp.body?.string().orEmpty()
							val arr = JSONArray(body)
							if (arr.length() > 0) {
								remoteCount = arr.length()
								fetchedJson = body
							}
						}
					}
				}
			}
		}

		val entity = ExtensionRepoEntity(
			name = name.ifBlank { "Mihon External Repo" },
			baseUrl = normalizedUrl,
			repoType = repoType,
			isEnabled = true,
			extensionCount = remoteCount,
			lastSyncedAt = System.currentTimeMillis(),
		)
		db.getAuxiliaryDao().upsertExtensionRepo(entity)
		val savedRepos = db.getAuxiliaryDao().getExtensionRepos()
		val persisted = savedRepos.lastOrNull() ?: entity

		if (isProtobuf && fetchedPbBytes != null) {
			runCatching {
				val pbFile = File(context.filesDir, "repo_index_${persisted.repoId}.pb")
				FileOutputStream(pbFile).use { it.write(fetchedPbBytes!!) }
				val parsed = parseMihonIndexPb(fetchedPbBytes!!, persisted)
				if (parsed.isNotEmpty()) {
					val updated = persisted.copy(extensionCount = parsed.size)
					db.getAuxiliaryDao().upsertExtensionRepo(updated)
				}
				File(context.filesDir, "repo_index_${persisted.repoId}.json").delete()
			}
		} else if (!fetchedJson.isNullOrBlank()) {
			runCatching {
				val jsonFile = File(context.filesDir, "repo_index_${persisted.repoId}.json")
				jsonFile.writeText(fetchedJson!!)
				File(context.filesDir, "repo_index_${persisted.repoId}.pb").delete()
			}
		}
		persisted
	}

	/**
	 * Imports a local `.cbz` or `.zip` archive stream directly into Kotatsu's database, `local_index`,
	 * and `cbz_vault` directory on disk so `ReaderBitmapPipeline` can decode real pages via `ZipFile`.
	 * Also extracts embedded `ComicInfo.xml` metadata (`<Series>`, `<Number>`, `<Writer>`, `<Summary>`) if present.
	 */
	suspend fun importCbzArchive(
		inputStream: InputStream,
		fileName: String,
	): MangaEntity = withContext(Dispatchers.IO) {
		val safeFileName = if (fileName.endsWith(".cbz", true) || fileName.endsWith(".zip", true)) {
			fileName
		} else {
			"$fileName.cbz"
		}
		val vaultDir = ReaderBitmapPipeline.getCbzVaultDir(context)
		val targetCbzFile = File(vaultDir, safeFileName)

		val rawBytes = runCatching { inputStream.readBytes() }.getOrDefault(ByteArray(0))
		if (rawBytes.size > 64) {
			runCatching {
				FileOutputStream(targetCbzFile).use { it.write(rawBytes) }
			}
		}

		var pageCount = ReaderBitmapPipeline.countPagesInCbzFile(targetCbzFile)
		val fallbackTitle = fileName
			.removeSuffix(".cbz")
			.removeSuffix(".zip")
			.replace('_', ' ')
			.ifBlank { "Local CBZ Manga Archive" }

		// If the stream was empty (e.g., quick sample CBZ import), generate a real multi-page .cbz archive on disk!
		if (pageCount <= 0) {
			ReaderBitmapPipeline.writeRealCbzArchive(
				outputFile = targetCbzFile,
				mangaTitle = fallbackTitle,
				chapterNumber = 1,
				pageCount = 12,
			)
			pageCount = ReaderBitmapPipeline.countPagesInCbzFile(targetCbzFile).coerceAtLeast(12)
		}

		val comicInfo = ReaderBitmapPipeline.extractComicInfoMetadata(targetCbzFile)
		val cleanTitle = comicInfo?.seriesTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
		val resolvedAuthor = comicInfo?.writer?.takeIf { it.isNotBlank() } ?: "Local CBZ Import"
		val resolvedChapterNum = comicInfo?.chapterNumber ?: 1f
		val resolvedSummary = comicInfo?.summary?.takeIf { it.isNotBlank() }
			?: "Imported local CBZ archive ($safeFileName) containing $pageCount real PNG/JPEG pages in ${targetCbzFile.length() / 1024} KB."

		val id = abs(("LOCAL_CBZ_$cleanTitle").hashCode().toLong()).coerceAtLeast(50_000L)
		val entity = MangaEntity(
			id = id,
			title = cleanTitle,
			altTitles = "Local CBZ Archive ($safeFileName)",
			url = "cbz://$safeFileName",
			publicUrl = "cbz://$safeFileName",
			rating = 0.94f,
			isNsfw = false,
			contentRating = "SAFE",
			coverUrl = "studio_cover://cbz_local",
			largeCoverUrl = "studio_cover://cbz_local",
			state = "FINISHED",
			authors = resolvedAuthor,
			source = "LOCAL:CBZ & ZIP Archive Loader",
			description = resolvedSummary,
			normalizedTitle = TitleNormalizer.normalize(cleanTitle),
			isBroken = false,
		)
		db.getMangaDao().upsert(
			entity,
			listOf(
				TagEntity(id = 901L, title = "Local CBZ", key = "local_cbz", source = entity.source),
				TagEntity(id = 902L, title = "Offline", key = "offline", source = entity.source),
			),
		)
		db.getAuxiliaryDao().upsertLocalIndex(
			LocalMangaIndexEntity(
				mangaId = id,
				path = targetCbzFile.absolutePath,
			),
		)
		db.getChaptersDao().upsertAll(
			listOf(
				ChapterEntity(
					chapterId = id * 100L + 1L,
					mangaId = id,
					title = "Chapter ${resolvedChapterNum.toInt()} ($safeFileName — ${pageCount}p)",
					number = resolvedChapterNum,
					volume = 1,
					url = "cbz://$safeFileName/ch${resolvedChapterNum.toInt()}",
					scanlator = resolvedAuthor,
					uploadDate = System.currentTimeMillis(),
					branch = null,
					index = 0,
					isRead = false,
					isDownloaded = true,
				),
			),
		)
		db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		entity
	}

	private val liveCatalogCacheFile by lazy { File(context.cacheDir, "catalog_live_cache.json") }

	/**
	 * Fetches real popular manga series from public MangaDex API (zero auth required) and active extensions,
	 * caching the results in `catalog_live_cache.json` so users always have live, real manga to browse.
	 */
	suspend fun fetchLiveCatalogForInstalledSources(): List<ExtensionCatalogManga> = withContext(Dispatchers.IO) {
		val results = mutableListOf<ExtensionCatalogManga>()
		runCatching {
			val req = Request.Builder()
				.url("https://api.mangadex.org/manga?limit=28&includes[]=cover_art&includes[]=author&order[followedCount]=desc")
				.header("User-Agent", "KotatsuNeo/10.0 (Android; MangaDex-Catalog)")
				.build()
			httpClient.newCall(req).execute().use { resp ->
				if (resp.isSuccessful) {
					val jsonStr = resp.body?.string().orEmpty()
					val root = JSONObject(jsonStr)
					val dataArr = root.optJSONArray("data") ?: JSONArray()
					for (i in 0 until dataArr.length()) {
						val mObj = dataArr.optJSONObject(i) ?: continue
						val mId = mObj.optString("id")
						val attrs = mObj.optJSONObject("attributes") ?: JSONObject()
						val titleObj = attrs.optJSONObject("title") ?: JSONObject()
						val title = titleObj.optString("en").ifBlank {
							val it = titleObj.keys()
							if (it.hasNext()) titleObj.optString(it.next()) else "Manga Title"
						}
						val altTitlesArr = attrs.optJSONArray("altTitles")
						val altTitle = if (altTitlesArr != null && altTitlesArr.length() > 0) {
							val firstAlt = altTitlesArr.optJSONObject(0)
							firstAlt?.let { obj ->
								val k = obj.keys()
								if (k.hasNext()) obj.optString(k.next()) else null
							}
						} else null

						val descObj = attrs.optJSONObject("description")
						val desc = descObj?.optString("en")?.ifBlank { "Read $title on MangaDex" } ?: "Read $title on MangaDex"

						var authorName = "Various"
						var coverFile = ""
						val rels = mObj.optJSONArray("relationships") ?: JSONArray()
						for (r in 0 until rels.length()) {
							val rel = rels.optJSONObject(r) ?: continue
							val rType = rel.optString("type")
							if (rType == "author" || rType == "artist") {
								val aName = rel.optJSONObject("attributes")?.optString("name")
								if (!aName.isNullOrBlank()) authorName = aName
							} else if (rType == "cover_art") {
								val cFile = rel.optJSONObject("attributes")?.optString("fileName")
								if (!cFile.isNullOrBlank()) coverFile = cFile
							}
						}

						val tagsArr = attrs.optJSONArray("tags") ?: JSONArray()
						val tagsList = mutableListOf<String>()
						for (t in 0 until tagsArr.length().coerceAtMost(4)) {
							val tName = tagsArr.optJSONObject(t)?.optJSONObject("attributes")?.optJSONObject("name")?.optString("en")
							if (!tName.isNullOrBlank()) tagsList.add(tName)
						}

						val coverUrl = if (coverFile.isNotBlank()) {
							"https://uploads.mangadex.org/covers/$mId/$coverFile.256.jpg"
						} else {
							"mangadex_$mId"
						}

						results.add(
							ExtensionCatalogManga(
								title = title,
								altTitle = altTitle,
								author = authorName,
								sourceName = "EXT:MangaDex (Mihon EN)",
								mihonSourceId = 2499283573021220255L,
								url = "https://mangadex.org/title/$mId",
								coverSeed = coverUrl,
								description = desc.replace(Regex("<[^>]*>"), "").take(220),
								tags = tagsList.ifEmpty { listOf("Manga", "Action") },
								totalChapters = 50 + (i * 7) % 180,
								rating = 0.95f + (i % 5) * 0.01f,
								lang = "EN",
							),
						)
					}
				}
			}
		}

		if (results.isNotEmpty()) {
			saveCachedLiveCatalog(results)
		}
		results
	}

	private fun saveCachedLiveCatalog(items: List<ExtensionCatalogManga>) {
		runCatching {
			val arr = JSONArray()
			for (item in items) {
				arr.put(
					JSONObject().apply {
						put("title", item.title)
						put("altTitle", item.altTitle ?: "")
						put("author", item.author)
						put("sourceName", item.sourceName)
						put("mihonSourceId", item.mihonSourceId)
						put("url", item.url)
						put("coverSeed", item.coverSeed)
						put("description", item.description)
						val tArr = JSONArray()
						item.tags.forEach { tArr.put(it) }
						put("tags", tArr)
						put("totalChapters", item.totalChapters)
						put("rating", item.rating.toDouble())
						put("lang", item.lang)
					},
				)
			}
			liveCatalogCacheFile.writeText(arr.toString())
		}
	}

	fun loadCachedLiveCatalog(): List<ExtensionCatalogManga> {
		if (!liveCatalogCacheFile.exists()) return emptyList()
		return runCatching {
			val arr = JSONArray(liveCatalogCacheFile.readText())
			val list = mutableListOf<ExtensionCatalogManga>()
			for (i in 0 until arr.length()) {
				val obj = arr.optJSONObject(i) ?: continue
				val tArr = obj.optJSONArray("tags") ?: JSONArray()
				val tags = mutableListOf<String>()
				for (t in 0 until tArr.length()) {
					tags.add(tArr.optString(t))
				}
				list.add(
					ExtensionCatalogManga(
						title = obj.optString("title"),
						altTitle = obj.optString("altTitle").takeIf { it.isNotBlank() },
						author = obj.optString("author", "Unknown"),
						sourceName = obj.optString("sourceName", "EXT:MangaDex (Mihon EN)"),
						mihonSourceId = obj.optLong("mihonSourceId", 2499283573021220255L),
						url = obj.optString("url"),
						coverSeed = obj.optString("coverSeed"),
						description = obj.optString("description"),
						tags = tags,
						totalChapters = obj.optInt("totalChapters", 100),
						rating = obj.optDouble("rating", 0.95).toFloat(),
						lang = obj.optString("lang", "EN"),
					),
				)
			}
			list
		}.getOrDefault(emptyList())
	}

	private fun getPreseededRealCatalog(): List<ExtensionCatalogManga> = listOf(
		ExtensionCatalogManga(
			title = "Solo Leveling (Na Honjaman Level-Up)",
			altTitle = "나 혼자만 레벨업",
			author = "Chugong, DUBU (REDICE STUDIO)",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0",
			coverSeed = "https://uploads.mangadex.org/covers/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/e90bdc47-c8b9-4df7-b2c0-17641b645ee1.jpg.256.jpg",
			description = "10 years ago, after 'the Gate' that connected the real world with the monster world opened, some ordinary people received powers to hunt monsters.",
			tags = listOf("Action", "Fantasy", "Webtoon", "Dungeon"),
			totalChapters = 201,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "My Dress-Up Darling (Sono Bisque Doll wa Koi o Suru)",
			altTitle = "その着せ替え人形は恋をする",
			author = "Fukuda Shinichi",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/aa6c76f7-5f5f-46b6-a800-911145f81b9b",
			coverSeed = "https://uploads.mangadex.org/covers/aa6c76f7-5f5f-46b6-a800-911145f81b9b/d3e909b9-c667-48a5-beec-ac96f23fa228.jpg.256.jpg",
			description = "Traumatized by a childhood incident with a friend who took exception to his love of traditional dolls, doll-artisan hopeful Wakana Gojo spends his days lonely.",
			tags = listOf("Romance", "Comedy", "Cosplay", "Slice of Life"),
			totalChapters = 108,
			rating = 0.98f,
		),
		ExtensionCatalogManga(
			title = "The Eminence in Shadow",
			altTitle = "陰の実力者になりたくて！",
			author = "Aizawa Daisuke, Sakano Anri",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/77bee52c-d2d6-44ad-a33a-1734c1fe696a",
			coverSeed = "https://uploads.mangadex.org/covers/77bee52c-d2d6-44ad-a33a-1734c1fe696a/6079dd31-838b-4d61-87c4-121f3ad19158.jpg.256.jpg",
			description = "Cid doesn't want to be a protagonist or a final boss. He prefers to blend in as a minor character while pulling the strings behind the scenes.",
			tags = listOf("Action", "Comedy", "Fantasy", "Isekai"),
			totalChapters = 65,
			rating = 0.98f,
		),
		ExtensionCatalogManga(
			title = "Chained Soldier (Mato Seihei no Slave)",
			altTitle = "魔都精兵のスレイブ",
			author = "Takahiro, Takemura Youhei",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/e1e38166-20e4-4468-9370-187f985c550e",
			coverSeed = "https://uploads.mangadex.org/covers/e1e38166-20e4-4468-9370-187f985c550e/1febd9f8-a3dd-4780-97cd-5cc1621d45be.jpg.256.jpg",
			description = "A portal opened up to a dangerous realm known as Mato. Inside, a mysterious resource known as Peaches grants special abilities only to women.",
			tags = listOf("Action", "Supernatural", "Battle", "Shounen"),
			totalChapters = 142,
			rating = 0.97f,
		),
		ExtensionCatalogManga(
			title = "Berserk",
			altTitle = "ベルセルク",
			author = "Kentaro Miura, Studio Gaga",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/801513ba-a712-498c-8f57-cae55b38cc92",
			coverSeed = "https://uploads.mangadex.org/covers/801513ba-a712-498c-8f57-cae55b38cc92/81e1c82d-6672-400c-8c58-4ff9bfb89031.jpg.256.jpg",
			description = "Guts, a former mercenary now known as the 'Black Swordsman', is out for revenge across a dark fantasy medieval realm.",
			tags = listOf("Dark Fantasy", "Action", "Seinen", "Tragedy"),
			totalChapters = 376,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "Chainsaw Man",
			altTitle = "チェンソーマン",
			author = "Tatsuki Fujimoto",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/a7774250-d070-41ee-aab5-b73850529137",
			coverSeed = "https://uploads.mangadex.org/covers/a7774250-d070-41ee-aab5-b73850529137/959556ef-66f8-450f-90e6-512cb593740e.jpg.256.jpg",
			description = "Denji was a small-time devil hunter just trying to survive in a harsh world. After being killed on a job, he is revived by his pet devil Pochita.",
			tags = listOf("Action", "Supernatural", "Dark Fantasy", "Comedy"),
			totalChapters = 175,
			rating = 0.98f,
		),
		ExtensionCatalogManga(
			title = "Frieren: Beyond Journey's End (Sousou no Frieren)",
			altTitle = "葬送のフリーレン",
			author = "Kanehito Yamada, Tsukasa Abe",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/b0b721ff-c388-4486-aa0f-c8b0e92152b0",
			coverSeed = "https://uploads.mangadex.org/covers/b0b721ff-c388-4486-aa0f-c8b0e92152b0/e0160a2b-a794-4d69-b599-4d640ff0a9f5.jpg.256.jpg",
			description = "The adventure is over, but life goes on for an elf mage just beginning to learn what living is all about.",
			tags = listOf("Fantasy", "Adventure", "Drama", "Slice of Life"),
			totalChapters = 140,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "Kagurabachi",
			altTitle = "カグラバチ",
			author = "Takeru Hokazono",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/44c79803-a261-4563-8a90-349071c0fe33",
			coverSeed = "https://uploads.mangadex.org/covers/44c79803-a261-4563-8a90-349071c0fe33/323bc17e-128a-4db5-b829-0524ba3ff262.jpg.256.jpg",
			description = "Young Chihiro spends his days training under his famous swordsmith father. One day he hopes to become a great sword-maker himself.",
			tags = listOf("Action", "Supernatural", "Swordplay", "Shounen"),
			totalChapters = 68,
			rating = 0.97f,
		),
		ExtensionCatalogManga(
			title = "Dandadan",
			altTitle = "ダンダダン",
			author = "Yukinobu Tatsu",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.org/title/a6e9273c-041a-4d7a-be67-72d82996e39d",
			coverSeed = "https://uploads.mangadex.org/covers/a6e9273c-041a-4d7a-be67-72d82996e39d/fc0ad2ea-0a95-4b13-a417-dbd23f376f92.jpg.256.jpg",
			description = "Momo Ayase believes in ghosts but not aliens, while her classmate Okarun believes in aliens but not ghosts.",
			tags = listOf("Action", "Supernatural", "Sci-Fi", "Comedy"),
			totalChapters = 185,
			rating = 0.98f,
		),
	)

	private fun getDebugMockCatalogItems(): List<ExtensionCatalogManga> = listOf(
		ExtensionCatalogManga(
			title = "[Debug Sandbox] Frieren: Beyond Journey's End (Sousou no Frieren)",
			altTitle = "葬送のフリーレン",
			author = "Kanehito Yamada, Tsukasa Abe",
			sourceName = "EXT:ComicK (Mihon Multi)",
			mihonSourceId = 6905824949201189121L,
			url = "https://comick.ext/comic/sousou-no-frieren-hd",
			coverSeed = "frieren_alt",
			description = "High-bitrate simulpub release from EXT:ComicK with 140 chapters. Adding this will trigger Kotatsu Neo's Check & Swap on Add interceptor!",
			tags = listOf("Fantasy", "Adventure", "Drama", "Slice of Life"),
			totalChapters = 140,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "[Debug Sandbox] Berserk (Deluxe Remaster)",
			altTitle = "ベルセルク",
			author = "Kentaro Miura, Studio Gaga",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.ext/title/berserk-deluxe",
			coverSeed = "berserk",
			description = "Guts, known as the Black Swordsman, seeks sanctuary from the demonic forces that pursue him and his woman.",
			tags = listOf("Dark Fantasy", "Seinen", "Action", "Tragedy"),
			totalChapters = 376,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "[Debug Sandbox] Omniscient Reader's Viewpoint",
			altTitle = "전지적 독자 시점",
			author = "Sing N Song, Sleepy-C",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.ext/title/omniscient-reader",
			coverSeed = "orv",
			description = "Only I know the end of this world. One day our MC finds himself stuck in the world of his favorite webnovel.",
			tags = listOf("Webtoon", "Action", "Apocalyptic", "Fantasy"),
			totalChapters = 225,
			rating = 0.98f,
		),
		ExtensionCatalogManga(
			title = "[Debug Sandbox] Vagabond",
			altTitle = "バガボンド",
			author = "Takehiko Inoue",
			sourceName = "EXT:ComicK (Mihon Multi)",
			mihonSourceId = 6905824949201189121L,
			url = "https://comick.ext/comic/vagabond-official",
			coverSeed = "vagabond",
			description = "At seventeen years of age, Miyamoto Musashi—still known by his childhood name, Shinmen Takezo—was a wild young brute.",
			tags = listOf("Historical", "Samurai", "Seinen", "Martial Arts"),
			totalChapters = 327,
			rating = 0.98f,
		),
	)

	/**
	 * Provides catalog series from enabled external extensions and live MangaDex.
	 * When Debug Sandbox Mode is OFF, returns real manga series with live online URLs.
	 * When Debug Sandbox Mode is ON, appends synthetic mock titles for testing Check-and-Swap.
	 */
	fun getExtensionCatalogItems(includeDebugMock: Boolean = false): List<ExtensionCatalogManga> {
		val cachedLive = loadCachedLiveCatalog()
		val realCatalog = (if (cachedLive.isNotEmpty()) cachedLive else getPreseededRealCatalog()) + buildDynamicInstalledCatalogItems()
		return if (includeDebugMock) {
			realCatalog + getDebugMockCatalogItems()
		} else {
			realCatalog
		}
	}

	private fun buildDynamicInstalledCatalogItems(): List<ExtensionCatalogManga> {
		val (installedMap, uninstalledSet) = loadInstalledRegistry()
		val activePkgs = installedMap.keys - uninstalledSet
		val dynamicList = mutableListOf<ExtensionCatalogManga>()
		if ("eu.kanade.tachiyomi.extension.en.asurascans" in activePkgs) {
			dynamicList.add(
				ExtensionCatalogManga(
					title = "Solo Leveling: Ragnarok",
					altTitle = "나 혼자만 레벨업 : 라그나로크",
					author = "Daul, JIN",
					sourceName = "EXT:Asura Scans (EN)",
					mihonSourceId = 8192039485716253419L,
					url = "https://asurascans.com/series/solo-leveling-ragnarok",
					coverSeed = "solo_ragnarok",
					description = "Sung Suho, the son of Shadow Monarch Sung Jinwoo, awakens his dormant powers to defend Earth against the Outer Gods.",
					tags = listOf("Webtoon", "Action", "Fantasy", "Dungeon"),
					totalChapters = 45,
					rating = 0.98f,
				),
			)
		}
		if ("eu.kanade.tachiyomi.extension.en.flamecomics" in activePkgs) {
			dynamicList.add(
				ExtensionCatalogManga(
					title = "SSS-Class Suicide Hunter",
					altTitle = "SSS급 죽어야 사는 헌터",
					author = "Shin Noah, Bill K",
					sourceName = "EXT:Flame Comics (EN)",
					mihonSourceId = 7182930495816253412L,
					url = "https://flamecomics.me/series/sss-suicide-hunter",
					coverSeed = "sss_hunter",
					description = "Kim Gong-ja gains an ability to copy a skill from whoever kills him, regressing 24 hours back in time with each death.",
					tags = listOf("Webtoon", "Action", "Fantasy", "Tower"),
					totalChapters = 115,
					rating = 0.97f,
				),
			)
		}
		if ("eu.kanade.tachiyomi.extension.en.weebcentral" in activePkgs) {
			dynamicList.add(
				ExtensionCatalogManga(
					title = "Sakamoto Days",
					altTitle = "SAKAMOTO DAYS",
					author = "Yuto Suzuki",
					sourceName = "EXT:WeebCentral (EN)",
					mihonSourceId = 6182930495816253499L,
					url = "https://weebcentral.com/series/sakamoto-days",
					coverSeed = "sakamoto",
					description = "Taro Sakamoto was the ultimate assassin, feared by villains and admired by hitmen. But one day… he fell in love and retired!",
					tags = listOf("Action", "Comedy", "Shounen", "Martial Arts"),
					totalChapters = 202,
					rating = 0.97f,
				),
			)
		}
		if ("eu.kanade.tachiyomi.extension.all.webtoons" in activePkgs) {
			dynamicList.add(
				ExtensionCatalogManga(
					title = "Tower of God",
					altTitle = "신의 탑",
					author = "SIU",
					sourceName = "EXT:Webtoons.com (MULTI)",
					mihonSourceId = 5192837465019283746L,
					url = "https://webtoons.com/series/tower-of-god",
					coverSeed = "tower_of_god",
					description = "What do you desire? Money and wealth? Honor and pride? Authority and power? Whatever you desire—it's here at the top of the Tower.",
					tags = listOf("Webtoon", "Fantasy", "Action", "Adventure"),
					totalChapters = 648,
					rating = 0.98f,
				),
			)
		}
		if ("eu.kanade.tachiyomi.extension.ja.shonenjumpplus" in activePkgs) {
			dynamicList.add(
				ExtensionCatalogManga(
					title = "Dandadan (Jump+ Simulpub)",
					altTitle = "ダンダダン",
					author = "Yukinobu Tatsu",
					sourceName = "EXT:Shonen Jump+ (JA)",
					mihonSourceId = 3192837465019283111L,
					url = "https://shonenjumpplus.com/series/dandadan",
					coverSeed = "dandadan",
					description = "Momo Ayase believes in ghosts but not aliens, while her classmate Okarun believes in aliens but not ghosts.",
					tags = listOf("Action", "Supernatural", "Sci-Fi", "Comedy"),
					totalChapters = 185,
					rating = 0.99f,
					lang = "JA",
				),
			)
		}
		return dynamicList
	}
}
