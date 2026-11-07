package org.koitharu.kotatsu.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ManualCloudflareSolveRequest(
	val domain: String,
	val targetUrl: String,
	val userAgent: String,
	val reason: String,
)

data class ClearanceResult(
	val solved: Boolean,
	val cfClearanceValue: String?,
	val verifiedUserAgent: String,
)

@Singleton
class CloudflareClearancePool @Inject constructor(
	@ApplicationContext private val context: Context? = null,
	private val loaderContext: MangaLoaderContext = MangaLoaderContext(),
) {
	companion object {
		const val HEADLESS_SOLVE_TIMEOUT_MS = 12_000L
	}

	private val domainLocks = ConcurrentHashMap<String, Mutex>()
	private val activeHeadlessSolvesCounter = AtomicInteger(0)

	private val _manualSolveRequests = MutableSharedFlow<ManualCloudflareSolveRequest>(extraBufferCapacity = 8)
	val manualSolveRequests: SharedFlow<ManualCloudflareSolveRequest> = _manualSolveRequests.asSharedFlow()

	/**
	 * Optional injectable headless solver override (used in unit tests or custom WebView hosts);
	 * defaults to the real off-screen Android WebView + CookieManager solver.
	 */
	@Volatile
	var customHeadlessSolver: (suspend (targetUrl: String, userAgent: String) -> String?)? = null

	val totalHeadlessSolvesExecuted: Int
		get() = activeHeadlessSolvesCounter.get()

	/**
	 * 1. Detection (`isCloudflareChallenge`):
	 * Inspects responses with status `403` or `503` where header `Server` starts with `cloudflare`
	 * AND (`cf-mitigated: challenge` is present OR body contains `Just a moment...` / `__cf_chl_opt`).
	 */
	fun isCloudflareChallenge(response: Response, peekedBodySnippet: String? = null): Boolean {
		val code = response.code
		if (code != 403 && code != 503) return false

		val serverHeader = response.header("Server")?.lowercase(Locale.ROOT).orEmpty()
		if (!serverHeader.startsWith("cloudflare")) return false

		val cfMitigated = response.header("cf-mitigated")?.lowercase(Locale.ROOT).orEmpty()
		if (cfMitigated.contains("challenge")) return true

		val body = peekedBodySnippet ?: runCatching {
			response.peekBody(16_384L).string()
		}.getOrDefault("")

		return body.contains("Just a moment...", ignoreCase = true) ||
			body.contains("__cf_chl_opt", ignoreCase = true)
	}

	/**
	 * 2. Per-Domain Mutex & Headless WebView Solver:
	 * When N concurrent coroutine requests hit a Cloudflare wall on `example.com`, the first
	 * acquires the domain `Mutex` while the remaining N-1 suspend awaiting the result.
	 * If the first coroutine already solved and populated `cf_clearance` in `MutableCookieJar`,
	 * subsequent waiters immediately reuse it without spinning up another WebView.
	 */
	suspend fun acquireClearanceForUrl(
		targetUrl: String,
		timeoutMs: Long = HEADLESS_SOLVE_TIMEOUT_MS,
	): ClearanceResult {
		val httpUrl = targetUrl.toHttpUrlOrNull()
		val host = httpUrl?.host?.lowercase(Locale.ROOT) ?: return ClearanceResult(
			solved = false,
			cfClearanceValue = null,
			verifiedUserAgent = loaderContext.defaultUserAgent,
		)

		val domainMutex = domainLocks.computeIfAbsent(host) { Mutex() }
		return domainMutex.withLock {
			// Fast-path: Check if the coroutine ahead of us in the Mutex queue already solved it!
			val existingToken = loaderContext.mutableCookieJar.getClearanceToken(host)
			if (!existingToken.isNullOrBlank()) {
				return@withLock ClearanceResult(
					solved = true,
					cfClearanceValue = existingToken,
					verifiedUserAgent = loaderContext.getUserAgentForHost(host),
				)
			}

			val userAgent = loaderContext.getUserAgentForHost(host)
			activeHeadlessSolvesCounter.incrementAndGet()

			val rawCookieString = withTimeoutOrNull(timeoutMs) {
				val overrideSolver = customHeadlessSolver
				if (overrideSolver != null) {
					overrideSolver.invoke(targetUrl, userAgent)
				} else {
					solveWithHeadlessWebView(targetUrl, host, userAgent, timeoutMs)
				}
			}

			if (!rawCookieString.isNullOrBlank() && rawCookieString.contains("cf_clearance=")) {
				if (httpUrl != null) {
					loaderContext.mutableCookieJar.putRawCookieHeader(httpUrl, rawCookieString)
				}
				loaderContext.setVerifiedUserAgent(host, userAgent)
				val token = loaderContext.mutableCookieJar.getClearanceToken(host)
					?: rawCookieString.substringAfter("cf_clearance=").substringBefore(";").trim()
				ClearanceResult(
					solved = true,
					cfClearanceValue = token,
					verifiedUserAgent = userAgent,
				)
			} else {
				// Emit UI notification only if headless WebView fails within the 12-second timeout
				_manualSolveRequests.tryEmit(
					ManualCloudflareSolveRequest(
						domain = host,
						targetUrl = targetUrl,
						userAgent = userAgent,
						reason = "Interactive Turnstile verification required for $host",
					),
				)
				ClearanceResult(
					solved = false,
					cfClearanceValue = null,
					verifiedUserAgent = userAgent,
				)
			}
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	private suspend fun solveWithHeadlessWebView(
		targetUrl: String,
		domain: String,
		userAgent: String,
		timeoutMs: Long,
	): String? {
		val appCtx = context ?: return null
		return runCatching {
			val cookieManager = CookieManager.getInstance()
			cookieManager.setAcceptCookie(true)

			val webView = withContext(Dispatchers.Main) {
				WebView(appCtx).apply {
					settings.javaScriptEnabled = true
					settings.domStorageEnabled = true
					settings.userAgentString = userAgent
					settings.cacheMode = WebSettings.LOAD_DEFAULT
					webViewClient = object : WebViewClient() {}
					loadUrl(targetUrl)
				}
			}

			try {
				val start = System.currentTimeMillis()
				var resolvedCookies: String? = null
				while (System.currentTimeMillis() - start < timeoutMs) {
					val currentCookies = runCatching { cookieManager.getCookie(targetUrl) ?: cookieManager.getCookie(domain) }.getOrNull()
					if (!currentCookies.isNullOrBlank() && currentCookies.contains("cf_clearance=")) {
						resolvedCookies = currentCookies
						break
					}
					delay(250L)
				}
				resolvedCookies
			} finally {
				withContext(Dispatchers.Main) {
					runCatching {
						webView.stopLoading()
						webView.destroy()
					}
				}
			}
		}.getOrNull()
	}
}

class CloudflareClearanceInterceptor(
	private val clearancePool: CloudflareClearancePool,
	private val loaderContext: MangaLoaderContext,
) : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val originalRequest = chain.request()
		val host = originalRequest.url.host.lowercase(Locale.ROOT)
		val requestWithUa = originalRequest.newBuilder()
			.header("User-Agent", loaderContext.getUserAgentForHost(host))
			.apply {
				val clearance = loaderContext.mutableCookieJar.getClearanceToken(host)
				if (!clearance.isNullOrBlank()) {
					header("Cookie", "cf_clearance=$clearance")
				}
			}
			.build()

		val initialResponse = chain.proceed(requestWithUa)
		if (!clearancePool.isCloudflareChallenge(initialResponse)) {
			return initialResponse
		}

		// Close challenged response body before retrying with clearance
		initialResponse.close()

		val clearanceResult = runBlocking {
			clearancePool.acquireClearanceForUrl(originalRequest.url.toString())
		}

		val retryRequest = originalRequest.newBuilder()
			.header("User-Agent", clearanceResult.verifiedUserAgent)
			.apply {
				if (!clearanceResult.cfClearanceValue.isNullOrBlank()) {
					header("Cookie", "cf_clearance=${clearanceResult.cfClearanceValue}")
				}
			}
			.build()

		return chain.proceed(retryRequest)
	}
}
