package org.nemuri.app.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.resolve.CaptchaAutoResolveCoordinator
import org.koitharu.kotatsu.core.exceptions.resolve.CaptchaHandler
import org.koitharu.kotatsu.core.model.MangaSource as ResolveMangaSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.network.CommonHeaders
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.network.proxy.ProxyProvider
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.util.ext.configureForParser
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.theme.NemuriTheme
import javax.inject.Inject
import javax.inject.Provider

@AndroidEntryPoint
class CloudFlareActivity : ComponentActivity() {

	private var pendingResult = RESULT_CANCELED
	private val isHidden: Boolean by lazy { intent?.getBooleanExtra(EXTRA_HIDDEN, false) == true }
	private val isAutoResolve: Boolean by lazy { intent?.getBooleanExtra(EXTRA_AUTO_RESOLVE, false) == true }
	private var resultNotified = false
	private var hiddenTimeoutJob: Job? = null
	private var clearanceVerificationJob: Job? = null

	/** Clearance cookie a silent check already found insufficient, so it is not re-tested on every page load. */
	private var rejectedClearance: String? = null

	@Inject
	lateinit var proxyProvider: ProxyProvider

	@Inject
	lateinit var adBlock: AdBlock

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	@MangaHttpClient
	lateinit var okHttpClientProvider: Provider<OkHttpClient>

	@Inject
	@BaseHttpClient
	lateinit var baseHttpClientProvider: Provider<OkHttpClient>

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	@Inject
	lateinit var captchaAutoResolveCoordinator: CaptchaAutoResolveCoordinator

	private var webView: WebView? = null
	private var cfClient: CloudFlareClient? = null

	private var pageTitle by mutableStateOf("Cloudflare Check")
	private var subtitleHost by mutableStateOf("")
	private var isLoading by mutableStateOf(true)
	private var progressPercent by mutableIntStateOf(10)
	private var canGoBack by mutableStateOf(false)
	private var statusMessage by mutableStateOf<String?>(null)
	private var webViewError by mutableStateOf<String?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		val url = intent?.dataString
		if (url.isNullOrBlank()) {
			finishAfterTransition()
			return
		}
		val mangaSource = ResolveMangaSource(intent?.getStringExtra(AppRouter.KEY_SOURCE))
		val userAgent = intent?.getStringExtra(AppRouter.KEY_USER_AGENT)?.nullIfEmpty()
		subtitleHost = url.toHttpUrlOrNull()?.host.ifNullOrEmpty { url }

		if (isHidden) {
			window.addFlags(
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			)
			hiddenTimeoutJob = lifecycleScope.launch {
				delay(HIDDEN_TIMEOUT_MS)
				webView?.stopLoading()
				finishAfterTransition()
			}
		}

		setContent {
			BackHandler(enabled = !isHidden && canGoBack) {
				webView?.goBack()
			}

			NemuriTheme {
				val rootModifier = if (isHidden) {
					Modifier
						.fillMaxSize()
						.alpha(0.01f)
				} else {
					Modifier
						.fillMaxSize()
						.background(NemuriTheme.colors.background)
						.navigationBarsPadding()
				}

				Column(modifier = rootModifier) {
					if (!isHidden) {
						Surface(
							color = NemuriTheme.colors.surface,
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(modifier = Modifier.statusBarsPadding()) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 4.dp, vertical = 6.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									IconButton(onClick = {
										webView?.stopLoading()
										finishAfterTransition()
									}) {
										Icon(
											imageVector = Icons.Default.Close,
											contentDescription = "Close",
											tint = NemuriTheme.colors.textPrimary,
										)
									}
									Column(
										modifier = Modifier
											.weight(1f)
											.padding(horizontal = 4.dp),
									) {
										Text(
											text = pageTitle,
											style = MaterialTheme.typography.titleSmall,
											color = NemuriTheme.colors.textPrimary,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
										Text(
											text = subtitleHost,
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.textMuted,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
									IconButton(onClick = { restartCheck() }) {
										Icon(
											imageVector = Icons.Default.Refresh,
											contentDescription = "Retry check",
											tint = NemuriTheme.colors.cyan,
										)
									}
								}
								if (isLoading) {
									LinearProgressIndicator(
										progress = { (progressPercent.coerceIn(5, 100)) / 100f },
										color = NemuriTheme.colors.cyan,
										trackColor = NemuriTheme.colors.border,
										modifier = Modifier
											.fillMaxWidth()
											.height(2.dp),
									)
								}
								val banner = statusMessage
								if (!banner.isNullOrBlank()) {
									Surface(
										color = NemuriTheme.colors.surfaceElevated,
										shape = RoundedCornerShape(10.dp),
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 12.dp, vertical = 6.dp)
											.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(10.dp)),
									) {
										Text(
											text = banner,
											style = MaterialTheme.typography.labelMedium,
											color = NemuriTheme.colors.amber,
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
										)
									}
								}
							}
						}
					}

					Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
						val initError = webViewError
						if (initError != null && !isHidden) {
							Column(
								modifier = Modifier
									.fillMaxSize()
									.padding(24.dp),
								verticalArrangement = Arrangement.Center,
								horizontalAlignment = Alignment.CenterHorizontally,
							) {
								Text(
									text = initError,
									style = MaterialTheme.typography.bodyMedium,
									color = NemuriTheme.colors.textMuted,
								)
								Button(
									onClick = { finishAfterTransition() },
									colors = ButtonDefaults.buttonColors(
										containerColor = NemuriTheme.colors.cyan,
										contentColor = NemuriTheme.colors.onCyan,
									),
									modifier = Modifier.padding(top = 12.dp),
								) {
									Text("Close")
								}
							}
						} else {
							AndroidView(
								modifier = Modifier.fillMaxSize(),
								factory = { ctx ->
									val instance = runCatching { WebView(ctx) }.getOrElse { error ->
										webViewError = error.localizedMessage ?: "Android WebView is not available"
										if (isHidden) {
											finishAfterTransition()
										}
										return@AndroidView android.view.View(ctx)
									}
									webView = instance
									instance.configureForParser(userAgent)
									instance.webChromeClient = object : WebChromeClient() {
										override fun onProgressChanged(view: WebView?, newProgress: Int) {
											progressPercent = newProgress
										}
									}
									lifecycleScope.launch {
										try {
											proxyProvider.applyWebViewConfig()
										} catch (e: Exception) {
											if (!isHidden) {
												statusMessage = e.getDisplayMessage(resources)
											}
										}
										val useIntercept = shouldUseInterception(mangaSource)
										val client = if (useIntercept) {
											val baseClient = withContext(Dispatchers.IO) { baseHttpClientProvider.get() }
											CloudFlareInterceptClient(
												cookieJar = cookieJar,
												onPageLoaded = ::onPageLoaded,
												onCheckPassed = { onCheckPassed() },
												onLoopDetected = ::onLoopDetected,
												onLoadingStateChanged = { loading -> isLoading = loading },
												onTitleChanged = ::onTitleChanged,
												onHistoryChanged = { back -> canGoBack = back },
												adBlock = adBlock,
												targetUrl = url,
												baseHttpClient = baseClient,
											)
										} else {
											CloudFlareClient(
												cookieJar = cookieJar,
												onPageLoaded = ::onPageLoaded,
												onCheckPassed = { onCheckPassed() },
												onLoopDetected = ::onLoopDetected,
												onLoadingStateChanged = { loading -> isLoading = loading },
												onTitleChanged = ::onTitleChanged,
												onHistoryChanged = { back -> canGoBack = back },
												adBlock = adBlock,
												targetUrl = url,
											)
										}
										cfClient = client
										instance.webViewClient = client
										onTitleChanged("Verifying…", url)
										instance.loadUrl(url)
									}
									instance
								},
							)
						}
					}
				}
			}
		}
	}

	override fun onPause() {
		webView?.onPause()
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		webView?.onResume()
	}

	override fun onDestroy() {
		webView?.let { instance ->
			instance.stopLoading()
			(instance.parent as? ViewGroup)?.removeView(instance)
			instance.destroy()
		}
		webView = null
		super.onDestroy()
	}

	override fun finish() {
		hiddenTimeoutJob?.cancel()
		setResult(pendingResult)
		if (isAutoResolve && !resultNotified) {
			resultNotified = true
			intent?.getStringExtra(AppRouter.KEY_SOURCE)?.let { sourceName ->
				captchaAutoResolveCoordinator.notifyResolveResult(
					ResolveMangaSource(sourceName),
					pendingResult == RESULT_OK,
				)
			}
		}
		super.finish()
	}

	fun onPageLoaded() {
		isLoading = false
		// CloudFlareClient can only detect a clearance cookie that *changed*, so a still-valid
		// cookie we already had reads as a failure there. Whenever a clearance cookie exists at
		// all, hand over to onCheckPassed - it settles the question with a real request instead
		// of guessing from the cookie. Silent, and only once per distinct cookie.
		val clearance = intent?.dataString?.let { CloudFlareHelper.getClearanceCookie(cookieJar, it) }
		if (clearance != null && clearance != rejectedClearance) {
			onCheckPassed(silentFor = clearance)
		}
	}

	fun onLoopDetected() {
		cfClient?.reset()
		if (!isHidden && !isAutoResolve) {
			Log.w(TAG, "Cloudflare loop detected; keeping manual browser open for user action")
		}
	}

	fun onCheckPassed(silentFor: String? = null) {
		if (clearanceVerificationJob?.isActive == true) {
			return
		}
		clearanceVerificationJob = lifecycleScope.launch {
			// Verify against the originally blocked url, not the rewritten challenge (root) url.
			val url = intent?.getStringExtra(EXTRA_ORIGINAL_URL)?.takeIf { it.isNotBlank() } ?: intent?.dataString
			if (url.isNullOrBlank() || !verifyClearance(url)) {
				if (silentFor != null) {
					rejectedClearance = silentFor
				} else if (!isHidden) {
					statusMessage = "Please complete the Cloudflare verification challenge."
				}
				return@launch
			}
			pendingResult = RESULT_OK
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(ResolveMangaSource(source))
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			finishAfterTransition()
		}
	}

	private suspend fun verifyClearance(url: String): Boolean = runCatchingCancellable {
		runInterruptible(Dispatchers.IO) {
			val request = Request.Builder()
				.url(url)
				.get()
				.apply {
					intent?.getStringExtra(AppRouter.KEY_USER_AGENT)?.takeIf { it.isNotBlank() }?.let {
						header(CommonHeaders.USER_AGENT, it)
					}
					intent?.getStringExtra(AppRouter.KEY_SOURCE)?.takeIf { it.isNotBlank() }?.let {
						header(CommonHeaders.MANGA_SOURCE, it)
					}
				}
				.build()
			okHttpClientProvider.get().newCall(request).execute().use { response ->
				Log.i(TAG, "verify clearance: url=$url status=${response.code}")
				response.isSuccessful
			}
		}
	}.onFailure { error ->
		Log.w(TAG, "verify clearance failed: url=$url", error)
	}.getOrDefault(false)

	private fun onTitleChanged(title: String, subtitle: String?) {
		if (title.isNotBlank()) {
			pageTitle = title
		}
		subtitleHost = subtitle?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }.orEmpty()
	}

	private fun restartCheck() {
		statusMessage = null
		lifecycleScope.launch {
			webView?.stopLoading()
			yield()
			cfClient?.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				clearCfCookies(targetUrl)
				webView?.loadUrl(targetUrl.toString())
			}
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.Default) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	private fun shouldUseInterception(source: MangaSource): Boolean {
		return SourceSettings(this, ResolveMangaSource(source.name)).isInterceptCloudflareEnabled
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == RESULT_OK
	}

	companion object {
		const val TAG = "CloudFlareActivity"
		const val EXTRA_HIDDEN = AppRouter.EXTRA_HIDDEN
		const val EXTRA_AUTO_RESOLVE = AppRouter.EXTRA_AUTO_RESOLVE
		const val EXTRA_ORIGINAL_URL = AppRouter.EXTRA_ORIGINAL_URL
		private const val HIDDEN_TIMEOUT_MS = 45_000L
	}
}
