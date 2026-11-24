package org.nemuri.app.ui.bookmarks

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.BookmarksRepository
import org.koitharu.kotatsu.bookmarks.domain.highlightText
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import kotlin.math.roundToInt

@Immutable
data class BookmarkEntryItem(
	val bookmark: Bookmark,
	val pageLabel: String,
	val excerpt: String?,
	val thumbnailUrl: String?,
	val timeLabel: String,
)

@Immutable
data class BookmarkMangaGroup(
	val manga: Manga,
	val sourceTitle: String,
	val items: List<BookmarkEntryItem>,
)

@Immutable
data class BookmarksUiState(
	val groups: List<BookmarkMangaGroup> = emptyList(),
	val totalBookmarks: Int = 0,
	val query: String = "",
	val isLoaded: Boolean = false,
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val bookmarksRepository: BookmarksRepository,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val queryFlow = MutableStateFlow("")

	val uiState: StateFlow<BookmarksUiState> = combine(
		bookmarksRepository.observeBookmarks(),
		queryFlow,
	) { rawMap, query ->
		val trimmed = query.trim()
		val now = System.currentTimeMillis()
		val groups = rawMap.entries
			.mapNotNull { (manga, bookmarks) ->
				val filteredBookmarks = bookmarks
					.filter { bookmark ->
						trimmed.isEmpty() ||
							manga.title.contains(trimmed, ignoreCase = true) ||
							bookmark.highlightText?.contains(trimmed, ignoreCase = true) == true
					}
					.sortedByDescending { it.createdAt }
				if (filteredBookmarks.isEmpty()) {
					null
				} else {
					BookmarkMangaGroup(
						manga = manga,
						sourceTitle = manga.source.getTitle(context),
						items = filteredBookmarks.map { bookmark ->
							val pct = (bookmark.percent * 100f).roundToInt().coerceIn(0, 100)
							val excerpt = bookmark.highlightText?.trim()?.takeIf { it.isNotEmpty() }
							val thumb = if (excerpt != null || bookmark.imageUrl.startsWith("file+")) {
								manga.coverUrl
							} else {
								bookmark.imageUrl.takeIf { it.isNotBlank() } ?: manga.coverUrl
							}
							val relative = DateUtils.getRelativeTimeSpanString(
								bookmark.createdAt.toEpochMilli(),
								now,
								DateUtils.MINUTE_IN_MILLIS,
								DateUtils.FORMAT_ABBREV_RELATIVE,
							).toString()
							BookmarkEntryItem(
								bookmark = bookmark,
								pageLabel = "Page ${bookmark.page + 1} • $pct%",
								excerpt = excerpt,
								thumbnailUrl = thumb,
								timeLabel = relative,
							)
						},
					)
				}
			}
			.sortedByDescending { group -> group.items.maxOf { it.bookmark.createdAt } }

		BookmarksUiState(
			groups = groups,
			totalBookmarks = groups.sumOf { it.items.size },
			query = query,
			isLoaded = true,
		)
	}.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = BookmarksUiState(),
		)

	fun setQuery(query: String) {
		queryFlow.value = query
	}

	fun removeBookmark(bookmark: Bookmark) {
		viewModelScope.launch(Dispatchers.IO) {
			val handle = bookmarksRepository.removeBookmarks(setOf(bookmark.pageId))
			toastManager.show(
				message = "Bookmark removed",
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}

	fun removeGroupBookmarks(group: BookmarkMangaGroup) {
		viewModelScope.launch(Dispatchers.IO) {
			val ids = group.items.map { it.bookmark.pageId }.toSet()
			if (ids.isEmpty()) return@launch
			val handle = bookmarksRepository.removeBookmarks(ids)
			toastManager.show(
				message = "Removed ${ids.size} bookmark(s) for ${group.manga.title}",
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}
}
