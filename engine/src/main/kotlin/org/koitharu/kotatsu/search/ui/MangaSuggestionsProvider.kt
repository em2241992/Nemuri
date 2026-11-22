package org.koitharu.kotatsu.search.ui

import android.app.SearchManager
import android.content.ContentResolver
import android.content.Context
import android.content.SearchRecentSuggestionsProvider
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.SearchRecentSuggestions
import androidx.core.net.toUri

class MangaSuggestionsProvider : SearchRecentSuggestionsProvider() {

	override fun attachInfo(context: Context?, info: ProviderInfo?) {
		val authority = info?.authority ?: context?.let(::authority)
		if (authority != null) {
			setupSuggestions(authority, MODE)
		}
		super.attachInfo(context, info)
	}

	companion object {

		private const val MODE = DATABASE_MODE_QUERIES

		fun authority(context: Context): String = "${context.packageName}.MangaSuggestionsProvider"

		fun createSuggestions(context: Context): SearchRecentSuggestions {
			return SearchRecentSuggestions(context, authority(context), MODE)
		}

		fun queryUri(context: Context): Uri = Uri.Builder()
			.scheme(ContentResolver.SCHEME_CONTENT)
			.authority(authority(context))
			.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY)
			.build()

		fun uri(context: Context): Uri = "content://${authority(context)}/suggestions".toUri()
	}
}
