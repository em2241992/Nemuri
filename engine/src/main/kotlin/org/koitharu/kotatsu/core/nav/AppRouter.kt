package org.koitharu.kotatsu.core.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder

class AppRouter private constructor() {

	companion object {

		@Volatile
		var navigator: EngineNavigator = EngineNavigator.NoOp

		fun homeIntent(context: Context): Intent = navigator.homeIntent(context)

		fun detailsIntent(context: Context, mangaId: Long): Intent = navigator.detailsIntent(context, mangaId)

		fun detailsIntent(context: Context, manga: Manga): Intent = navigator.detailsIntent(context, manga)

		fun listIntent(
			context: Context,
			source: MangaSource,
			filter: MangaListFilter?,
			sortOrder: SortOrder?,
		): Intent = navigator.listIntent(context, source, filter, sortOrder)

		fun readerBaseIntent(context: Context): Intent = navigator.readerBaseIntent(context)

		fun downloadsIntent(context: Context): Intent = navigator.downloadsIntent(context)

		fun extensionsCatalogIntent(context: Context): Intent = navigator.extensionsCatalogIntent(context)

		fun suggestionsIntent(context: Context): Intent = navigator.suggestionsIntent(context)

		fun suggestionsSettingsIntent(context: Context): Intent = navigator.suggestionsSettingsIntent(context)

		fun syncSettingsIntent(context: Context): Intent = navigator.syncSettingsIntent(context)

		fun mangaUpdatesIntent(context: Context): Intent = navigator.mangaUpdatesIntent(context)

		fun trackerDebugIntent(context: Context): Intent = navigator.trackerDebugIntent(context)

		fun trackerSettingsIntent(context: Context): Intent = navigator.trackerSettingsIntent(context)

		fun browserIntent(
			context: Context,
			url: String,
			source: MangaSource?,
			title: String?,
		): Intent = navigator.browserIntent(context, url, source, title)

		fun cloudFlareResolveIntent(
			context: Context,
			exception: CloudFlareProtectedException,
			hidden: Boolean = false,
		): Intent = navigator.cloudFlareResolveIntent(context, exception, hidden)

		fun shortMangaUrl(mangaId: Long): Uri = Uri.Builder()
			.scheme("kotatsu")
			.path("manga")
			.appendQueryParameter("id", mangaId.toString())
			.build()

		const val EXTRA_AUTO_RESOLVE = "auto_resolve"
		const val KEY_DATA = "data"
		const val KEY_ENTRIES = "entries"
		const val KEY_ERROR = "error"
		const val KEY_EXCLUDE = "exclude"
		const val KEY_FILE = "file"
		const val KEY_FILTER = "filter"
		const val KEY_ID = "id"
		const val KEY_INDEX = "index"
		const val KEY_ACCENT_COLOR = "accent_color"
		const val KEY_IS_BOTTOMTAB = "is_btab"
		const val KEY_KIND = "kind"
		const val KEY_LIST_SECTION = "list_section"
		const val KEY_MANGA = "manga"
		const val KEY_MANGA_LIST = "manga_list"
		const val KEY_PAGES = "pages"
		const val KEY_PREVIEW = "preview"
		const val KEY_READER_MODE = "reader_mode"
		const val KEY_REQUEST_KEY = "request_key"
		const val KEY_ROUTE = "nemuri_route"
		const val KEY_SORT_ORDER = "sort_order"
		const val KEY_SOURCE = "source"
		const val KEY_TAB = "tab"
		const val KEY_TITLE = "title"
	}
}
