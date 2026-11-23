package org.nemuri.app

import android.content.Context
import android.content.Intent
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.EngineNavigator
import org.koitharu.kotatsu.core.network.CommonHeaders
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
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
	): Intent = baseIntent(context, "browser")
		.putExtra(AppRouter.KEY_DATA, url)
		.putExtra(AppRouter.KEY_SOURCE, source?.name)
		.putExtra(AppRouter.KEY_TITLE, title)

	override fun cloudFlareResolveIntent(
		context: Context,
		exception: CloudFlareProtectedException,
		hidden: Boolean,
	): Intent = baseIntent(context, "cloudflare")
		.putExtra(AppRouter.KEY_SOURCE, exception.source.name)
		.putExtra(AppRouter.KEY_DATA, exception.url)
		.putExtra(AppRouter.EXTRA_ORIGINAL_URL, exception.url)
		.putExtra(AppRouter.KEY_USER_AGENT, exception.headers[CommonHeaders.USER_AGENT])
		.putExtra(AppRouter.EXTRA_HIDDEN, hidden)
		.putExtra(AppRouter.EXTRA_AUTO_RESOLVE, hidden)
}
