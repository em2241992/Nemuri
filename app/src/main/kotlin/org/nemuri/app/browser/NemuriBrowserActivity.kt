package org.nemuri.app.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.network.proxy.ProxyProvider
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import org.koitharu.kotatsu.core.util.ext.configureForParser
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.nemuri.app.ui.theme.NemuriTheme
import javax.inject.Inject

@AndroidEntryPoint
class NemuriBrowserActivity : ComponentActivity() {

	@Inject
	lateinit var proxyProvider: ProxyProvider

	@Inject
	lateinit var adBlock: AdBlock

	private var webView: WebView? = null
	private var successCookieUrl: String? = null
	private var successCookieName: String? = null

	private var pageTitle by mutableStateOf("Browser")
	private var currentUrl by mutableStateOf("")
	private var isLoading by mutableStateOf(true)
	private var progressPercent by mutableIntStateOf(0)
	private var canGoBack by mutableStateOf(false)
	private var webViewError by mutableStateOf<String?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		val initialUrl = intent?.dataString
		if (initialUrl.isNullOrBlank()) {
			finishAfterTransition()
			return
		}
		currentUrl = initialUrl
		pageTitle = intent?.getStringExtra(AppRouter.KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Loading…"
		successCookieUrl = intent?.getStringExtra(AppRouter.KEY_SUCCESS_COOKIE_URL)
		successCookieName = intent?.getStringExtra(AppRouter.KEY_SUCCESS_COOKIE_NAME)
		val userAgent = intent?.getStringExtra(AppRouter.KEY_USER_AGENT)?.nullIfEmpty()

		setContent {
			BackHandler(enabled = canGoBack) {
				webView?.goBack()
			}

			NemuriTheme {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.background(NemuriTheme.colors.background)
						.navigationBarsPadding(),
				) {
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
									val host = currentUrl.toHttpUrlOrNull()?.host ?: currentUrl
									Text(
										text = host,
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.textMuted,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								IconButton(onClick = { webView?.reload() }) {
									Icon(
										imageVector = Icons.Default.Refresh,
										contentDescription = "Reload",
										tint = NemuriTheme.colors.textSecondary,
									)
								}
								IconButton(onClick = { openInExternalBrowser(webView?.url ?: currentUrl) }) {
									Icon(
										imageVector = Icons.Default.OpenInBrowser,
										contentDescription = "Open in browser",
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
						}
					}

					Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
						val initError = webViewError
						if (initError != null) {
							Column(
								modifier = Modifier
									.fillMaxSize()
									.padding(24.dp),
								verticalArrangement = Arrangement.Center,
								horizontalAlignment = Alignment.CenterHorizontally,
							) {
								Surface(
									color = NemuriTheme.colors.card,
									shape = RoundedCornerShape(16.dp),
									modifier = Modifier.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
								) {
									Column(
										modifier = Modifier.padding(20.dp),
										horizontalAlignment = Alignment.CenterHorizontally,
										verticalArrangement = Arrangement.spacedBy(12.dp),
									) {
										Text(
											text = "WebView unavailable",
											style = MaterialTheme.typography.titleMedium,
											color = NemuriTheme.colors.textPrimary,
										)
										Text(
											text = initError,
											style = MaterialTheme.typography.bodySmall,
											color = NemuriTheme.colors.textMuted,
										)
										Button(
											onClick = { openInExternalBrowser(currentUrl) },
											colors = ButtonDefaults.buttonColors(
												containerColor = NemuriTheme.colors.cyan,
												contentColor = NemuriTheme.colors.onCyan,
											),
										) {
											Text("Open External Browser")
										}
									}
								}
							}
						} else {
							AndroidView(
								modifier = Modifier.fillMaxSize(),
								factory = { ctx ->
									val instance = runCatching { WebView(ctx) }.getOrElse { error ->
										webViewError = error.localizedMessage ?: "Android WebView is not available"
										return@AndroidView android.view.View(ctx)
									}
									webView = instance
									instance.configureForParser(userAgent)
									instance.webChromeClient = object : WebChromeClient() {
										override fun onProgressChanged(view: WebView?, newProgress: Int) {
											progressPercent = newProgress
										}
									}
									instance.webViewClient = NemuriBrowserClient(
										onLoadingStateChanged = { loading -> isLoading = loading },
										onTitleChanged = { title, url ->
											if (title.isNotBlank()) pageTitle = title
											if (url.isNotBlank()) currentUrl = url
										},
										onHistoryChanged = { back -> canGoBack = back },
										adBlock = adBlock,
									)
									lifecycleScope.launch {
										runCatching {
											proxyProvider.applyWebViewConfig()
										}.onFailure { it.printStackTraceDebug() }
										instance.loadUrl(initialUrl)
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

	private fun openInExternalBrowser(url: String) {
		if (url.isBlank()) return
		runCatching {
			startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
		}.onFailure { it.printStackTraceDebug() }
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
		if (successCookieUrl != null && successCookieName != null) {
			CookieManager.getInstance().flush()
			val currentValue = getCookieValue(successCookieUrl!!, successCookieName!!)
			setResult(if (!currentValue.isNullOrBlank()) RESULT_OK else RESULT_CANCELED)
		} else {
			setResult(RESULT_OK)
		}
		super.finish()
	}

	private fun getCookieValue(url: String, cookieName: String): String? {
		val cookies = CookieManager.getInstance().getCookie(url) ?: return null
		return cookies.split(';')
			.map { it.trim() }
			.firstOrNull { it.startsWith("$cookieName=") }
			?.substringAfter('=')
	}

	class Contract : ActivityResultContract<InteractiveActionRequiredException, Boolean>() {
		override fun createIntent(
			context: Context,
			input: InteractiveActionRequiredException,
		): Intent = AppRouter.browserIntent(
			context = context,
			url = input.url,
			source = input.source,
			title = null,
		).apply {
			putExtra(AppRouter.KEY_SUCCESS_COOKIE_URL, input.successCookieUrl)
			putExtra(AppRouter.KEY_SUCCESS_COOKIE_NAME, input.successCookieName)
			if (input.userAgent != null) {
				putExtra(AppRouter.KEY_USER_AGENT, input.userAgent)
			}
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == RESULT_OK
	}

	companion object {
		const val TAG = "NemuriBrowserActivity"
	}
}
