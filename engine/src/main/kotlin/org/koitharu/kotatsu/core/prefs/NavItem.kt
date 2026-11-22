package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import org.koitharu.kotatsu.R

@Keep
enum class NavItem(
	@StringRes val title: Int,
) {

	HISTORY(R.string.history),
	FAVORITES(R.string.favourites),
	LOCAL(R.string.on_device),
	EXPLORE(R.string.explore),
	SUGGESTIONS(R.string.suggestions),
	FEED(R.string.feed),
	UPDATED(R.string.updated),
	BOOKMARKS(R.string.bookmarks),
	;

	fun isAvailable(settings: AppSettings): Boolean = when (this) {
		SUGGESTIONS -> settings.isSuggestionsEnabled
		UPDATED, FEED -> settings.isTrackerEnabled
		else -> true
	}
}
