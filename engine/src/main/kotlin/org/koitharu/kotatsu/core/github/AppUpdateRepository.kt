package org.koitharu.kotatsu.core.github

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.asArrayList
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"
private val AUTO_CHECK_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(15)

@Singleton
class AppUpdateRepository @Inject constructor(
	@BaseHttpClient private val okHttpProvider: Provider<OkHttpClient>,
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val downloadProgress = MutableStateFlow<Float?>(null)
	private val isDownloading = MutableStateFlow(false)

	private val configuredRepo: String = context.getString(R.string.github_updates_repo).trim()
	private val releasesUrl = buildString {
		append("https://api.github.com/repos/")
		append(configuredRepo)
		append("/releases?page=1&per_page=10")
	}
	private val currentVersionName = runCatching {
		context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
	}.getOrDefault("")

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	fun observeDownloadProgress() = downloadProgress.asStateFlow()

	fun observeIsDownloading() = isDownloading.asStateFlow()

	fun dismissUpdate(versionId: Long) {
		settings.dismissedUpdateId = versionId
		if (availableUpdate.value?.id == versionId) {
			availableUpdate.value = null
		}
	}

	suspend fun getAvailableVersions(): List<AppVersion> {
		if (!isUpdateSupported()) {
			return emptyList()
		}
		val request = Request.Builder()
			.get()
			.header("Accept", "application/vnd.github+json")
			.url(releasesUrl)
		val jsonArray = okHttpProvider.get().newCall(request.build()).await().parseJsonArray()
		return jsonArray.mapJSONNotNull { json ->
			if (json.optBoolean("draft", false)) return@mapJSONNotNull null
			val asset = selectBestApkAsset(json.optJSONArray("assets")) ?: return@mapJSONNotNull null
			val rawName = json.optString("name").ifBlank { json.optString("tag_name") }
			AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = rawName.removePrefix("v").removePrefix("V"),
				apkSize = asset.optLong("size", 0L),
				apkUrl = asset.getString("browser_download_url"),
				description = json.optString("body", ""),
			)
		}
	}

	suspend fun fetchUpdate(force: Boolean = false): AppVersion? = withContext(Dispatchers.IO) {
		if (!isUpdateSupported()) {
			availableUpdate.value = null
			return@withContext null
		}
		val now = System.currentTimeMillis()
		if (!force && (now - settings.lastUpdateCheckAt) < AUTO_CHECK_COOLDOWN_MS) {
			return@withContext availableUpdate.value
		}
		runCatchingCancellable {
			settings.lastUpdateCheckAt = now
			val currentVersion = VersionId(currentVersionName)
			val available = getAvailableVersions().asArrayList()
			available.sortBy { it.versionId }
			if (currentVersion.isStable) {
				available.retainAll { it.versionId.isStable }
			}
			val best = available.maxByOrNull { it.versionId }
				?.takeIf { it.versionId > currentVersion }
			if (!force && best != null && best.id == settings.dismissedUpdateId) {
				null
			} else {
				best
			}
		}.onFailure {
			it.printStackTraceDebug()
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	suspend fun downloadAndInstallUpdate(version: AppVersion): Result<Unit> = withContext(Dispatchers.IO) {
		if (isDownloading.value) {
			return@withContext Result.failure(IllegalStateException("Download already in progress"))
		}
		isDownloading.value = true
		downloadProgress.value = 0f
		runCatchingCancellable {
			val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
			val apkFile = File(updatesDir, "nemuri-${version.id}.apk")
			val partFile = File(updatesDir, "nemuri-${version.id}.apk.part")
			partFile.delete()

			val client = okHttpProvider.get().newBuilder()
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)
				.followRedirects(true)
				.followSslRedirects(true)
				.build()
			val req = Request.Builder().url(version.apkUrl).get().build()
			client.newCall(req).execute().use { resp ->
				if (!resp.isSuccessful) {
					error("HTTP ${resp.code} downloading update APK")
				}
				val body = resp.body
				val total = body.contentLength().takeIf { it > 0L } ?: version.apkSize
				var copied = 0L
				var lastEmit = 0L
				val buf = ByteArray(64 * 1024)
				body.byteStream().use { input ->
					FileOutputStream(partFile).use { out ->
						while (true) {
							val n = input.read(buf)
							if (n < 0) break
							out.write(buf, 0, n)
							copied += n
							val now = System.currentTimeMillis()
							if (now - lastEmit >= 120L) {
								lastEmit = now
								downloadProgress.value = if (total > 0L) {
									(copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
								} else {
									null
								}
							}
						}
						out.flush()
					}
				}
			}
			if (apkFile.exists()) apkFile.delete()
			if (!partFile.renameTo(apkFile)) {
				partFile.copyTo(apkFile, overwrite = true)
				partFile.delete()
			}
			downloadProgress.value = 1f

			val uri = FileProvider.getUriForFile(
				context,
				"${context.packageName}.files",
				apkFile,
			)
			val installIntent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(uri, CONTENT_TYPE_APK)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			withContext(Dispatchers.Main) {
				context.startActivity(installIntent)
			}
		}.also {
			isDownloading.value = false
			downloadProgress.value = null
		}
	}

	/**
	 * Strictly guarded so F-Droid builds never phone home to GitHub and upstream Kotatsu/DropSauce
	 * repositories can never be queried over a Nemuri installation.
	 */
	fun isUpdateSupported(): Boolean {
		if (BuildConfig.FLAVOR == "fdroid") {
			return false
		}
		if (configuredRepo.isBlank() || '/' !in configuredRepo) {
			return false
		}
		val lower = configuredRepo.lowercase()
		if ("kotatsu" in lower || "dropsauce" in lower || "haziffe" in lower) {
			return false
		}
		return true
	}

	private fun selectBestApkAsset(assets: JSONArray?): JSONObject? {
		if (assets == null || assets.length() == 0) return null
		val apkAssets = ArrayList<JSONObject>(assets.length())
		for (i in 0 until assets.length()) {
			val jo = assets.optJSONObject(i) ?: continue
			val name = jo.optString("name").lowercase()
			val contentType = jo.optString("content_type")
			val isApk = contentType == CONTENT_TYPE_APK || name.endsWith(".apk")
			if (!isApk) continue
			if ("fdroid" in name) continue
			apkAssets.add(jo)
		}
		if (apkAssets.isEmpty()) return null
		val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
		// Prefer exact flavor ("github") + preferred device ABI
		for (abi in supportedAbis) {
			apkAssets.firstOrNull { jo ->
				val n = jo.optString("name").lowercase()
				"github" in n && abi in n
			}?.let { return it }
		}
		// Any APK matching device ABI
		for (abi in supportedAbis) {
			apkAssets.firstOrNull { jo -> abi in jo.optString("name").lowercase() }?.let { return it }
		}
		// Universal GitHub APK or first GitHub APK
		return apkAssets.firstOrNull { jo ->
			val n = jo.optString("name").lowercase()
			"github" in n && ("universal" in n || !hasSpecificAbiToken(n))
		} ?: apkAssets.firstOrNull { "github" in it.optString("name").lowercase() }
			?: apkAssets.first()
	}

	private fun hasSpecificAbiToken(name: String): Boolean =
		"arm64-v8a" in name || "armeabi-v7a" in name || "x86_64" in name || "x86" in name
}
