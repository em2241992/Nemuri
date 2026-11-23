package org.nemuri.app

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.EngineNavigator
import org.koitharu.kotatsu.core.network.CommonHeaders
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.navigation.MangaTransferCache

class NemuriEngineNavigator : EngineNavigator {

	private fun baseIntent(context: Context, route: String): Intent =
		Intent(context, MainActivity::class.java)
			.putExtra(AppRouter.KEY_ROUTE, route)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

	override fun homeIntent(context: Context): Intent = baseIntent(context, "home")

	override fun detailsIntent(context: Context, mangaId: Long): Intent =
		baseIntent(context, "details").putExtra(AppRouter.KEY_ID, mangaId)

	override fun detailsIntent(context: Context, manga: Manga): Intent {
		MangaTransferCache.put(manga)
		return baseIntent(context, "details")
			.putExtra(AppRouter.KEY_ID, manga.id)
			.putExtra(AppRouter.KEY_SOURCE, manga.source.name)
	}

	override fun listIntent(
		context: Context,
		source: MangaSource,
		filter: MangaListFilter?,
		sortOrder: SortOrder?,
	): Intent = baseIntent(context, "list")
		.putExtra(AppRouter.KEY_SOURCE, source.name)

	override fun readerBaseIntent(context: Context): Intent = baseIntent(context, "reader")

	override fun downloadsIntent(context: Context): Intent = baseIntent(context, "downloads")

	override fun extensionsCatalogIntent(context: Context): Intent = baseIntent(context, "extensions")

	override fun suggestionsIntent(context: Context): Intent = baseIntent(context, "suggestions")

	override fun suggestionsSettingsIntent(context: Context): Intent = baseIntent(context, "settings_suggestions")

	override fun syncSettingsIntent(context: Context): Intent = baseIntent(context, "settings_sync")

	override fun mangaUpdatesIntent(context: Context): Intent = baseIntent(context, "updates")

	override fun trackerDebugIntent(context: Context): Intent = baseIntent(context, "tracker_debug")

	override fun trackerSettingsIntent(context: Context): Intent = baseIntent(context, "settings_tracker")

	override fun browserIntent(
		context: Context,
		url: String,
		source: MangaSource?,
		title: String?,
	): Intent = Intent(context, NemuriBrowserActivity::class.java)
		.setData(url.toUri())
		.putExtra(AppRouter.KEY_TITLE, title)
		.putExtra(AppRouter.KEY_SOURCE, source?.name)

	override fun cloudFlareResolveIntent(
		context: Context,
		exception: CloudFlareProtectedException,
		hidden: Boolean,
	): Intent {
		val challengeUrl = getChallengeUrl(
			exception.url,
			preservePage = exception.source.name.startsWith("LN_"),
		)
		return Intent(context, CloudFlareActivity::class.java).apply {
			data = challengeUrl.toUri()
			putExtra(AppRouter.KEY_SOURCE, exception.source.name)
			putExtra(AppRouter.EXTRA_HIDDEN, hidden)
			putExtra(AppRouter.EXTRA_AUTO_RESOLVE, hidden)
			putExtra(AppRouter.EXTRA_ORIGINAL_URL, exception.url)
			exception.headers[CommonHeaders.USER_AGENT]?.let {
				putExtra(AppRouter.KEY_USER_AGENT, it)
			}
		}
	}

	companion object {
		internal fun getChallengeUrl(url: String, preservePage: Boolean): String {
			val httpUrl = url.toHttpUrlOrNull() ?: return url
			val host = httpUrl.host.lowercase()
			val path = httpUrl.encodedPath
			val ext = path.substringAfterLast('.', "").lowercase()
			val isAsset = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "svg") ||
				host.startsWith("imagenes.") ||
				host.startsWith("images.") ||
				host.startsWith("cdn.") ||
				host.startsWith("img.") ||
				host.startsWith("static.")
			// Some novel sites protect list/detail paths while leaving the homepage open.
			if (preservePage && !isAsset) {
				return httpUrl.toString()
			}
			if (isAsset) {
				val rootDomain = getRootDomain(host)
				if (rootDomain.isNotBlank()) {
					return "https://$rootDomain/"
				}
			}
			return httpUrl.newBuilder()
				.encodedPath("/")
				.query(null)
				.fragment(null)
				.build()
				.toString()
		}

		private fun getRootDomain(host: String): String {
			val parts = host.split('.')
			if (parts.size <= 2) return host
			val last = parts.last()
			val secondLast = parts[parts.size - 2]
			val isDoubleTld = secondLast in setOf("com", "co", "org", "net", "edu", "gov") && last.length == 2
			return if (isDoubleTld && parts.size >= 3) {
				parts.takeLast(3).joinToString(".")
			} else {
				parts.takeLast(2).joinToString(".")
			}
		}
	}
}
