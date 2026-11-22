package org.koitharu.kotatsu.core.nav

import android.content.Context
import android.content.Intent
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * UI routing bridge implemented by `:app` and registered at application startup so `:engine`
 * notifications, background workers, and extension challenge flows can launch concrete UI screens
 * without compile-time coupling to Activities or Fragments.
 */
interface EngineNavigator {

	fun homeIntent(context: Context): Intent

	fun detailsIntent(context: Context, mangaId: Long): Intent

	fun detailsIntent(context: Context, manga: Manga): Intent

	fun listIntent(
		context: Context,
		source: MangaSource,
		filter: MangaListFilter?,
		sortOrder: SortOrder?,
	): Intent

	fun readerBaseIntent(context: Context): Intent

	fun downloadsIntent(context: Context): Intent

	fun extensionsCatalogIntent(context: Context): Intent

	fun suggestionsIntent(context: Context): Intent

	fun suggestionsSettingsIntent(context: Context): Intent

	fun syncSettingsIntent(context: Context): Intent

	fun mangaUpdatesIntent(context: Context): Intent

	fun trackerDebugIntent(context: Context): Intent

	fun trackerSettingsIntent(context: Context): Intent

	fun browserIntent(
		context: Context,
		url: String,
		source: MangaSource?,
		title: String?,
	): Intent

	fun cloudFlareResolveIntent(
		context: Context,
		exception: CloudFlareProtectedException,
		hidden: Boolean = false,
	): Intent

	object NoOp : EngineNavigator {
		private fun fallbackIntent(context: Context, route: String): Intent =
			(context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(Intent.ACTION_MAIN))
				.setPackage(context.packageName)
				.putExtra(AppRouter.KEY_ROUTE, route)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

		override fun homeIntent(context: Context) = fallbackIntent(context, "home")

		override fun detailsIntent(context: Context, mangaId: Long) =
			fallbackIntent(context, "details").putExtra(AppRouter.KEY_ID, mangaId)

		override fun detailsIntent(context: Context, manga: Manga) =
			fallbackIntent(context, "details").putExtra(AppRouter.KEY_ID, manga.id)

		override fun listIntent(
			context: Context,
			source: MangaSource,
			filter: MangaListFilter?,
			sortOrder: SortOrder?,
		) = fallbackIntent(context, "list").putExtra(AppRouter.KEY_SOURCE, source.name)

		override fun readerBaseIntent(context: Context) = fallbackIntent(context, "reader")

		override fun downloadsIntent(context: Context) = fallbackIntent(context, "downloads")

		override fun extensionsCatalogIntent(context: Context) = fallbackIntent(context, "extensions")

		override fun suggestionsIntent(context: Context) = fallbackIntent(context, "suggestions")

		override fun suggestionsSettingsIntent(context: Context) = fallbackIntent(context, "settings_suggestions")

		override fun syncSettingsIntent(context: Context) = fallbackIntent(context, "settings_sync")

		override fun mangaUpdatesIntent(context: Context) = fallbackIntent(context, "updates")

		override fun trackerDebugIntent(context: Context) = fallbackIntent(context, "tracker_debug")

		override fun trackerSettingsIntent(context: Context) = fallbackIntent(context, "settings_tracker")

		override fun browserIntent(
			context: Context,
			url: String,
			source: MangaSource?,
			title: String?,
		) = fallbackIntent(context, "browser")
			.putExtra(AppRouter.KEY_DATA, url)
			.putExtra(AppRouter.KEY_SOURCE, source?.name)
			.putExtra(AppRouter.KEY_TITLE, title)

		override fun cloudFlareResolveIntent(
			context: Context,
			exception: CloudFlareProtectedException,
			hidden: Boolean,
		) = fallbackIntent(context, "cloudflare")
			.putExtra(AppRouter.KEY_SOURCE, exception.source.name)
			.putExtra(AppRouter.KEY_DATA, exception.url)
	}
}
