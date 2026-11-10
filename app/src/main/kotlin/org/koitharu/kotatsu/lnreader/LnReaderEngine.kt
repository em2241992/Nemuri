package org.koitharu.kotatsu.lnreader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.MutableCookieJar
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class InstalledLnPlugin(
	val pluginId: String,
	val pkgName: String,
	val name: String,
	val lang: String,
	val version: String,
	val siteUrl: String,
	val iconUrl: String?,
	val sourceJsUrl: String,
	val localJsPath: String,
) {
	val sourceKey: String
		get() = "LNREADER_$pluginId"

	fun toMangaSource(): MangaSource = MangaSource(
		name = sourceKey,
		title = "$name (${lang.uppercase(Locale.ROOT)})",
	)
}

class JsHost(
	private val context: Context,
	private val httpClient: OkHttpClient,
) {
	private val mainHandler = Handler(Looper.getMainLooper())
	private val initMutex = Mutex()
	private var webView: WebView? = null
	private var hostReady = CompletableDeferred<Unit>()
	private val loadedPluginIds = ConcurrentHashMap.newKeySet<String>()
	private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<String>>()
	private val callCounter = AtomicLong(1L)

	@SuppressLint("SetJavaScriptEnabled")
	private suspend fun ensureInitialized() {
		if (hostReady.isCompleted) return
		initMutex.withLock {
			if (hostReady.isCompleted) return
			val hostJs = runCatching {
				context.assets.open("lnreader-host.js").bufferedReader().use { it.readText() }
			}.getOrDefault("")

			withContext(Dispatchers.Main) {
				val wv = WebView(context.applicationContext)
				wv.settings.javaScriptEnabled = true
				wv.settings.domStorageEnabled = true
				wv.settings.allowFileAccess = false
				wv.settings.allowContentAccess = false
				wv.addJavascriptInterface(AndroidBridge(), "AndroidLnBridge")
				wv.webViewClient = object : WebViewClient() {
					override fun onPageFinished(view: WebView?, url: String?) {
						view?.evaluateJavascript(hostJs) {
							if (!hostReady.isCompleted) {
								hostReady.complete(Unit)
							}
						}
					}
				}
				webView = wv
				wv.loadDataWithBaseURL("https://lnreader.local/", "<html><body></body></html>", "text/html", "UTF-8", null)
			}
			withTimeout(10_000L) {
				hostReady.await()
			}
		}
	}

	suspend fun ensurePluginLoaded(pluginId: String, jsCode: String) {
		ensureInitialized()
		if (pluginId in loadedPluginIds) return
		val encodedPluginId = JSONObject.quote(pluginId)
		val encodedCode = JSONObject.quote(jsCode)
		withContext(Dispatchers.Main) {
			val deferred = CompletableDeferred<Unit>()
			webView?.evaluateJavascript("window.__lnHost.loadPlugin($encodedPluginId, $encodedCode);") {
				loadedPluginIds += pluginId
				deferred.complete(Unit)
			} ?: deferred.complete(Unit)
			withTimeout(8_000L) { deferred.await() }
		}
	}

	suspend fun callPluginMethod(pluginId: String, methodName: String, argsJsonArray: JSONArray): String {
		ensureInitialized()
		val callId = "call_${callCounter.getAndIncrement()}"
		val deferred = CompletableDeferred<String>()
		pendingCalls[callId] = deferred

		val jsStmt = "window.__lnHost.callPlugin(${JSONObject.quote(callId)}, ${JSONObject.quote(pluginId)}, ${JSONObject.quote(methodName)}, ${JSONObject.quote(argsJsonArray.toString())});"
		withContext(Dispatchers.Main) {
			webView?.evaluateJavascript(jsStmt, null)
		}

		return try {
			withTimeout(25_000L) {
				deferred.await()
			}
		} finally {
			pendingCalls.remove(callId)
		}
	}

	private inner class AndroidBridge {
		@JavascriptInterface
		fun httpRequest(url: String, method: String, headersJson: String, body: String?): String {
			return runCatching {
				val reqBuilder = Request.Builder().url(url)
				reqBuilder.header(
					"User-Agent",
					"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
				)
				val headersObj = runCatching { JSONObject(headersJson) }.getOrNull()
				if (headersObj != null) {
					val keys = headersObj.keys()
					while (keys.hasNext()) {
						val k = keys.next()
						val v = headersObj.optString(k)
						if (k.isNotBlank() && v.isNotBlank()) {
							reqBuilder.header(k, v)
						}
					}
				}
				val upperMethod = method.uppercase(Locale.ROOT)
				if (upperMethod == "GET" || upperMethod == "HEAD") {
					reqBuilder.method(upperMethod, null)
				} else {
					val contentType = headersObj?.optString("Content-Type")?.takeIf { it.isNotBlank() }
						?: "application/x-www-form-urlencoded; charset=UTF-8"
					reqBuilder.method(upperMethod, (body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
				}
				httpClient.newCall(reqBuilder.build()).execute().use { resp ->
					JSONObject()
						.put("status", resp.code)
						.put("url", resp.request.url.toString())
						.put("body", resp.body?.string().orEmpty())
						.toString()
				}
			}.getOrElse { err ->
				JSONObject()
					.put("status", 500)
					.put("error", err.message ?: "Network request failed")
					.toString()
			}
		}

		@JavascriptInterface
		fun onJsResult(callId: String, resultJson: String?, errorMessage: String?) {
			val pending = pendingCalls[callId] ?: return
			if (!errorMessage.isNullOrBlank()) {
				pending.completeExceptionally(IllegalStateException(errorMessage))
			} else {
				pending.complete(resultJson ?: "null")
			}
		}
	}
}

class LnPluginManager private constructor(
	private val context: Context,
) {
	private val pluginsDir = File(context.filesDir, "lnreader_plugins").apply { mkdirs() }
	private val manifestFile = File(pluginsDir, "plugins_manifest.json")
	private val mutex = Mutex()

	val httpClient: OkHttpClient = OkHttpClient.Builder()
		.cookieJar(MutableCookieJar())
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(25, TimeUnit.SECONDS)
		.followRedirects(true)
		.followSslRedirects(true)
		.build()

	val jsHost: JsHost by lazy { JsHost(context, httpClient) }

	private val _installedPlugins = MutableStateFlow<List<InstalledLnPlugin>>(readManifestFromDisk())
	val installedPlugins: StateFlow<List<InstalledLnPlugin>> = _installedPlugins.asStateFlow()

	suspend fun installPlugin(
		pkgName: String,
		displayName: String,
		lang: String,
		version: String,
		siteUrl: String,
		iconUrl: String?,
		jsDownloadUrl: String,
	): Result<InstalledLnPlugin> = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(jsDownloadUrl)
				.header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
				.build()
			val jsCode = httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					throw IllegalStateException("HTTP ${response.code} downloading plugin from $jsDownloadUrl")
				}
				response.body?.string().orEmpty()
			}
			require(jsCode.isNotBlank()) { "Downloaded LNReader plugin script is empty" }

			val cleanId = pkgName.removePrefix("lnreader.plugin.")
				.replace(Regex("[^a-zA-Z0-9._-]"), "_")
				.ifBlank { displayName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_") }

			val targetFile = File(pluginsDir, "$cleanId.js")
			targetFile.writeText(jsCode)

			val installed = InstalledLnPlugin(
				pluginId = cleanId,
				pkgName = if (pkgName.startsWith("lnreader.plugin.")) pkgName else "lnreader.plugin.$cleanId",
				name = displayName,
				lang = lang.ifBlank { "en" },
				version = version.ifBlank { "1.0.0" },
				siteUrl = siteUrl,
				iconUrl = iconUrl,
				sourceJsUrl = jsDownloadUrl,
				localJsPath = targetFile.absolutePath,
			)

			mutex.withLock {
				val updated = _installedPlugins.value.filterNot {
					it.pluginId == installed.pluginId || it.pkgName.equals(installed.pkgName, ignoreCase = true)
				} + installed
				writeManifestToDisk(updated)
				_installedPlugins.value = updated
			}
			installed
		}
	}

	suspend fun uninstallPlugin(pkgNameOrPluginId: String) = withContext(Dispatchers.IO) {
		mutex.withLock {
			val target = _installedPlugins.value.firstOrNull {
				it.pkgName.equals(pkgNameOrPluginId, ignoreCase = true) ||
					it.pluginId.equals(pkgNameOrPluginId.removePrefix("LNREADER_"), ignoreCase = true)
			} ?: return@withLock
			runCatching { File(target.localJsPath).delete() }
			val remaining = _installedPlugins.value.filterNot { it.pluginId == target.pluginId }
			writeManifestToDisk(remaining)
			_installedPlugins.value = remaining
		}
	}

	suspend fun clearAllPlugins() = withContext(Dispatchers.IO) {
		mutex.withLock {
			_installedPlugins.value.forEach { runCatching { File(it.localJsPath).delete() } }
			runCatching { manifestFile.delete() }
			_installedPlugins.value = emptyList()
		}
	}

	fun findPlugin(query: String): InstalledLnPlugin? {
		val clean = query.trim()
		if (clean.isEmpty()) return null
		val stripped = clean.removePrefix("LNREADER_").removePrefix("lnreader.plugin.")
		return _installedPlugins.value.firstOrNull { p ->
			p.sourceKey.equals(clean, ignoreCase = true) ||
				p.pluginId.equals(stripped, ignoreCase = true) ||
				p.pkgName.equals(clean, ignoreCase = true) ||
				p.name.equals(clean, ignoreCase = true) ||
				p.toMangaSource().title.equals(clean, ignoreCase = true)
		}
	}

	private fun readManifestFromDisk(): List<InstalledLnPlugin> {
		if (!manifestFile.exists()) return emptyList()
		return runCatching {
			val arr = JSONArray(manifestFile.readText())
			val list = mutableListOf<InstalledLnPlugin>()
			for (i in 0 until arr.length()) {
				val obj = arr.optJSONObject(i) ?: continue
				val localPath = obj.optString("localJsPath")
				if (localPath.isBlank() || !File(localPath).exists()) continue
				list += InstalledLnPlugin(
					pluginId = obj.optString("pluginId"),
					pkgName = obj.optString("pkgName"),
					name = obj.optString("name"),
					lang = obj.optString("lang", "en"),
					version = obj.optString("version", "1.0.0"),
					siteUrl = obj.optString("siteUrl"),
					iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
					sourceJsUrl = obj.optString("sourceJsUrl"),
					localJsPath = localPath,
				)
			}
			list
		}.getOrDefault(emptyList())
	}

	private fun writeManifestToDisk(plugins: List<InstalledLnPlugin>) {
		runCatching {
			val arr = JSONArray()
			plugins.forEach { p ->
				arr.put(
					JSONObject()
						.put("pluginId", p.pluginId)
						.put("pkgName", p.pkgName)
						.put("name", p.name)
						.put("lang", p.lang)
						.put("version", p.version)
						.put("siteUrl", p.siteUrl)
						.put("iconUrl", p.iconUrl ?: "")
						.put("sourceJsUrl", p.sourceJsUrl)
						.put("localJsPath", p.localJsPath),
				)
			}
			manifestFile.writeText(arr.toString())
		}
	}

	companion object {
		@Volatile
		private var INSTANCE: LnPluginManager? = null

		fun getInstance(context: Context): LnPluginManager {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: LnPluginManager(context.applicationContext).also { INSTANCE = it }
			}
		}
	}
}

class LnMangaRepository(
	private val plugin: InstalledLnPlugin,
	private val pluginManager: LnPluginManager,
) : CachingMangaRepository() {

	override val source: MangaSource = plugin.toMangaSource()

	override val sortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)

	private suspend fun ensureLoaded() {
		val jsCode = withContext(Dispatchers.IO) {
			File(plugin.localJsPath).readText()
		}
		pluginManager.jsHost.ensurePluginLoaded(plugin.pluginId, jsCode)
	}

	override suspend fun getList(offset: Int, query: String?, sortOrder: SortOrder?): List<Manga> {
		ensureLoaded()
		val page = (offset / 20) + 1
		val rawJson = if (!query.isNullOrBlank()) {
			val args = JSONArray().put(query.trim()).put(page)
			pluginManager.jsHost.callPluginMethod(plugin.pluginId, "searchNovels", args)
		} else {
			val options = JSONObject().put("showLatestNovels", sortOrder == SortOrder.UPDATED)
			val args = JSONArray().put(page).put(options)
			pluginManager.jsHost.callPluginMethod(plugin.pluginId, "popularNovels", args)
		}

		val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
		val results = ArrayList<Manga>(arr.length())
		for (i in 0 until arr.length()) {
			val obj = arr.optJSONObject(i) ?: continue
			val name = obj.optString("name").trim()
			val path = obj.optString("path").trim().ifBlank { obj.optString("url").trim() }
			if (name.isEmpty() || path.isEmpty()) continue
			val cover = resolveUrl(obj.optString("cover").trim())
			val publicUrl = resolveUrl(path)
			val stableId = ((source.name.hashCode().toLong() shl 31) xor path.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL
			results += Manga(
				id = stableId,
				title = name,
				altTitle = null,
				url = path,
				publicUrl = publicUrl,
				rating = -1f,
				isNsfw = false,
				coverUrl = cover.takeIf { it.isNotBlank() },
				largeCoverUrl = cover.takeIf { it.isNotBlank() },
				description = null,
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
		return results
	}

	override suspend fun fetchDetailsImpl(manga: Manga): Manga {
		ensureLoaded()
		val rawJson = pluginManager.jsHost.callPluginMethod(
			plugin.pluginId,
			"parseNovel",
			JSONArray().put(manga.url),
		)
		val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return manga
		val summary = obj.optString("summary").takeIf { it.isNotBlank() } ?: manga.description
		val author = obj.optString("author").takeIf { it.isNotBlank() } ?: manga.author
		val cover = resolveUrl(obj.optString("cover")).takeIf { it.isNotBlank() } ?: manga.coverUrl
		val genresRaw = obj.optString("genres")
		val tags = genresRaw.split(',', ';')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { t -> MangaTag(title = t, key = t.lowercase(Locale.ROOT), source = source) }
			.toSet()
		val statusStr = obj.optString("status").lowercase(Locale.ROOT)
		val state = when {
			statusStr.contains("ongoing") -> MangaState.ONGOING
			statusStr.contains("completed") -> MangaState.FINISHED
			statusStr.contains("hiatus") -> MangaState.PAUSED
			statusStr.contains("cancelled") -> MangaState.ABANDONED
			else -> manga.state
		}

		val chaptersArr = obj.optJSONArray("chapters")
		val chapters = mutableListOf<MangaChapter>()
		if (chaptersArr != null) {
			for (i in 0 until chaptersArr.length()) {
				val chObj = chaptersArr.optJSONObject(i) ?: continue
				val chName = chObj.optString("name", "Chapter ${i + 1}").trim()
				val chPath = chObj.optString("path").trim().ifBlank { chObj.optString("url").trim() }
				if (chPath.isEmpty()) continue
				val chNum = chObj.optDouble("chapterNumber", (i + 1).toDouble()).toFloat()
				val stableChId = ((source.name.hashCode().toLong() shl 31) xor chPath.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL
				chapters += MangaChapter(
					id = stableChId,
					name = chName,
					number = if (chNum > 0f) chNum else (i + 1).toFloat(),
					volume = 0,
					url = chPath,
					scanlator = plugin.name,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			}
		}

		return manga.copy(
			title = obj.optString("name").takeIf { it.isNotBlank() } ?: manga.title,
			description = summary,
			author = author,
			coverUrl = cover,
			largeCoverUrl = cover,
			tags = if (tags.isNotEmpty()) tags else manga.tags,
			state = state,
			chapters = chapters,
		)
	}

	override suspend fun fetchPagesImpl(chapter: MangaChapter): List<MangaPage> {
		ensureLoaded()
		val chapterHtml = pluginManager.jsHost.callPluginMethod(
			plugin.pluginId,
			"parseChapter",
			JSONArray().put(chapter.url),
		)
		val unquoted = runCatching {
			if (chapterHtml.startsWith("\"")) JSONArray("[$chapterHtml]").getString(0) else chapterHtml
		}.getOrDefault(chapterHtml)
		val encoded = Base64.encodeToString(unquoted.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
		val dataUri = "data:text/html;base64,$encoded"
		val stableId = ((source.name.hashCode().toLong() shl 31) xor chapter.url.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL
		return listOf(
			MangaPage(
				id = stableId,
				url = dataUri,
				preview = null,
				source = source,
			),
		)
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	private fun resolveUrl(raw: String): String {
		if (raw.isBlank()) return ""
		if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
		val base = plugin.siteUrl.trimEnd('/')
		return if (base.isNotBlank()) "$base/${raw.trimStart('/')}" else raw
	}
}
