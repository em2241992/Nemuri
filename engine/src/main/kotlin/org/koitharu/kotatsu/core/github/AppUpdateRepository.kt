package org.koitharu.kotatsu.core.github

import android.content.Context
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
import org.koitharu.kotatsu.core.util.ext.asArrayList
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"

@Singleton
class AppUpdateRepository @Inject constructor(
	@BaseHttpClient private val okHttpProvider: Provider<OkHttpClient>,
	@ApplicationContext context: Context,
) {

	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val configuredRepo: String = context.getString(R.string.github_updates_repo).trim()
	private val releasesUrl = buildString {
		append("https://api.github.com/repos/")
		append(configuredRepo)
		append("/releases?page=1&per_page=10")
	}
	private val currentVersionName = context.packageManager
		.getPackageInfo(context.packageName, 0)
		.versionName
		.orEmpty()

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	suspend fun getAvailableVersions(): List<AppVersion> {
		if (!isUpdateSupported()) {
			return emptyList()
		}
		val request = Request.Builder()
			.get()
			.url(releasesUrl)
		val jsonArray = okHttpProvider.get().newCall(request.build()).await().parseJsonArray()
		return jsonArray.mapJSONNotNull { json ->
			val asset = json.optJSONArray("assets")?.find { jo ->
				jo.optString("content_type") == CONTENT_TYPE_APK
			} ?: return@mapJSONNotNull null
			AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = json.getString("name").removePrefix("v"),
				apkSize = asset.getLong("size"),
				apkUrl = asset.getString("browser_download_url"),
				description = json.getString("body"),
			)
		}
	}

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.Default) {
		if (!isUpdateSupported()) {
			availableUpdate.value = null
			return@withContext null
		}
		runCatchingCancellable {
			val currentVersion = VersionId(currentVersionName)
			val available = getAvailableVersions().asArrayList()
			available.sortBy { it.versionId }
			if (currentVersion.isStable) {
				available.retainAll { it.versionId.isStable }
			}
			available.maxByOrNull { it.versionId }
				?.takeIf { it.versionId > currentVersion }
		}.onFailure {
			it.printStackTraceDebug()
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	/**
	 * Strictly guarded so F-Droid builds never phone home to GitHub and upstream Kotatsu/DropSauce
	 * repositories can never be queried over a Nemuri installation.
	 */
	fun isUpdateSupported(): Boolean {
		if (BuildConfig.FLAVOR == "fdroid" || BuildConfig.DEBUG) {
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

	private inline fun JSONArray.find(predicate: (JSONObject) -> Boolean): JSONObject? {
		val size = length()
		for (i in 0 until size) {
			val jo = getJSONObject(i)
			if (predicate(jo)) {
				return jo
			}
		}
		return null
	}
}
