package org.nemuri.app.browser

import android.content.Context
import android.content.Intent
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.getCauseUrl
import org.koitharu.kotatsu.core.util.ext.isHttp403OrChallenge
import org.koitharu.kotatsu.core.util.ext.isTlsOrSniBlock
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Resolves user-actionable WebView verification targets for HTTP 403 / Cloudflare / CAPTCHA / login
 * failures across Explore, Details, and Reader screens so the user can solve challenges directly in
 * a visible WebView (syncing cookies back to OkHttp).
 */
sealed interface WebViewChallengeAction {
	data class CloudFlare(val exception: CloudFlareProtectedException) : WebViewChallengeAction
	data class Interactive(val exception: InteractiveActionRequiredException) : WebViewChallengeAction
	data class BrowserUrl(
		val url: String,
		val source: MangaSource,
		val title: String? = null,
	) : WebViewChallengeAction
}

private inline fun <reified T : Throwable> Throwable.findCauseExt(): T? {
	var cur: Throwable? = this
	var depth = 0
	while (cur != null && depth < 16) {
		if (cur is T) return cur
		cur = cur.cause
		depth++
	}
	return null
}

fun Throwable.isDnsOrTlsBlockError(): Boolean = isTlsOrSniBlock()

fun String?.isLikelyTlsOrDnsBlockMessage(): Boolean {
	val msg = this ?: return false
	return msg.contains("TLS", ignoreCase = true) ||
		msg.contains("SSL", ignoreCase = true) ||
		msg.contains("DNS", ignoreCase = true) ||
		msg.contains("SNI", ignoreCase = true) ||
		msg.contains("packet header", ignoreCase = true)
}

fun String?.isLikelyChallengeErrorMessage(): Boolean {
	val msg = this ?: return false
	return msg.contains("403", ignoreCase = true) ||
		msg.contains("cloudflare", ignoreCase = true) ||
		msg.contains("captcha", ignoreCase = true) ||
		msg.contains("forbidden", ignoreCase = true) ||
		msg.contains("protection", ignoreCase = true)
}

fun Throwable.toWebViewChallengeAction(
	fallbackUrl: String? = null,
	fallbackSource: MangaSource = UnknownMangaSource,
	title: String? = null,
): WebViewChallengeAction? {
	findCauseExt<CloudFlareProtectedException>()?.let { return WebViewChallengeAction.CloudFlare(it) }
	findCauseExt<InteractiveActionRequiredException>()?.let { return WebViewChallengeAction.Interactive(it) }
	val resolvedUrl = getCauseUrl()?.takeIf { it.isNotBlank() } ?: fallbackUrl?.takeIf { it.isNotBlank() }
	if (resolvedUrl != null && (isHttp403OrChallenge() || isTlsOrSniBlock())) {
		return WebViewChallengeAction.BrowserUrl(
			url = resolvedUrl,
			source = (fallbackSource as? MangaSourceInfo)?.mangaSource ?: fallbackSource,
			title = title,
		)
	}
	return null
}

fun WebViewChallengeAction.BrowserUrl.toBrowserIntent(context: Context): Intent =
	AppRouter.browserIntent(context, url, source, title)
