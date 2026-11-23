package org.nemuri.app.browser

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

private const val LOOP_COUNTER = 3

open class CloudFlareClient(
	private val cookieJar: MutableCookieJar,
	private val onPageLoaded: () -> Unit,
	private val onCheckPassed: () -> Unit,
	private val onLoopDetected: () -> Unit,
	onLoadingStateChanged: (Boolean) -> Unit,
	onTitleChanged: (String, String) -> Unit,
	onHistoryChanged: (Boolean) -> Unit,
	adBlock: AdBlock?,
	private val targetUrl: String,
) : NemuriBrowserClient(
	onLoadingStateChanged = onLoadingStateChanged,
	onTitleChanged = onTitleChanged,
	onHistoryChanged = onHistoryChanged,
	adBlock = adBlock,
) {

	private val oldClearance = getClearance()
	private var counter = 0

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		checkClearance()
	}

	override fun onPageCommitVisible(view: WebView, url: String) {
		super.onPageCommitVisible(view, url)
		onPageLoaded()
	}

	override fun onPageFinished(webView: WebView, url: String) {
		super.onPageFinished(webView, url)
		onPageLoaded()
	}

	fun reset() {
		counter = 0
	}

	private fun checkClearance() {
		val clearance = getClearance()
		if (clearance != null && clearance != oldClearance) {
			onCheckPassed()
		} else {
			counter++
			if (counter >= LOOP_COUNTER) {
				reset()
				onLoopDetected()
			}
		}
	}

	override fun onReceivedError(
		view: WebView?,
		request: WebResourceRequest?,
		error: WebResourceError?,
	) {
		if (request?.isForMainFrame == true) {
			Log.w("CloudFlareClient", "WebView error: ${error?.errorCode} - ${error?.description}")
		}
		super.onReceivedError(view, request, error)
	}

	private fun getClearance(): String? = CloudFlareHelper.getClearanceCookie(cookieJar, targetUrl)
}
