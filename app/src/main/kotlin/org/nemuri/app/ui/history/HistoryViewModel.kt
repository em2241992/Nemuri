package org.nemuri.app.ui.history

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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.ui.components.NemuriToastManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HistoryQuickFilter(val label: String) {
	ALL("All"),
	DOWNLOADED("Downloaded"),
	IN_PROGRESS("In Progress"),
	COMPLETED("Completed"),
}

@Immutable
data class HistoryEntryItem(
	val manga: Manga,
	val history: MangaHistory,
	val sourceTitle: String,
	val progressPercent: Float,
	val dateGroup: String,
	val timeSummary: String,
)

@Immutable
data class HistoryUiState(
	val groups: Map<String, List<HistoryEntryItem>> = emptyMap(),
	val totalCount: Int = 0,
	val query: String = "",
	val sortOrder: ListSortOrder = ListSortOrder.LAST_READ,
	val quickFilter: HistoryQuickFilter = HistoryQuickFilter.ALL,
	val groupByDate: Boolean = true,
	val isLoaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val queryFlow = MutableStateFlow("")
	private val quickFilterFlow = MutableStateFlow(HistoryQuickFilter.ALL)

	private val prefStateFlow = settings.observeChanges()
		.filter {
			it == null ||
				it == AppSettings.KEY_HISTORY_ORDER ||
				it == AppSettings.KEY_HISTORY_GROUPING
		}
		.onStart { emit(null) }

	private val rawHistoryFlow = combine(prefStateFlow, quickFilterFlow) { _, filter ->
		val sortOrder = settings.historySortOrder
		val options: Set<ListFilterOption> = when (filter) {
			HistoryQuickFilter.ALL -> emptySet()
			HistoryQuickFilter.DOWNLOADED -> setOf(ListFilterOption.Downloaded)
			HistoryQuickFilter.IN_PROGRESS -> setOf(ListFilterOption.NOT_COMPLETED)
			HistoryQuickFilter.COMPLETED -> setOf(ListFilterOption.Macro.COMPLETED)
		}
		sortOrder to options
	}.flatMapLatest { (sortOrder, options) ->
		historyRepository.observeAllWithHistory(sortOrder, options, Int.MAX_VALUE)
	}

	val uiState: StateFlow<HistoryUiState> = combine(
		rawHistoryFlow,
		queryFlow,
		quickFilterFlow,
		prefStateFlow,
	) { items, query, filter, _ ->
		val sortOrder = settings.historySortOrder
		val groupEnabled = settings.isHistoryGroupingEnabled &&
			(sortOrder.type == ListSortOrder.Type.LAST_READ || sortOrder.type == ListSortOrder.Type.DATE_ADDED || sortOrder.type == ListSortOrder.Type.PROGRESS)
		val q = query.trim()
		val today = LocalDate.now()
		val yesterday = today.minusDays(1)
		val zone = ZoneId.systemDefault()
		val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

		val filtered = items
			.filter { entry ->
				q.isEmpty() ||
					entry.manga.title.contains(q, ignoreCase = true) ||
					entry.manga.altTitles.any { it.contains(q, ignoreCase = true) }
			}
			.map { entry ->
				val updatedDate = entry.history.updatedAt.atZone(zone).toLocalDate()
				val group = if (!groupEnabled) {
					"History"
				} else when {
					updatedDate == today -> "Today"
					updatedDate == yesterday -> "Yesterday"
					updatedDate.isAfter(today.minusDays(7)) -> "This Week"
					else -> dateFormatter.format(updatedDate)
				}
				val relTime = DateUtils.getRelativeTimeSpanString(
					entry.history.updatedAt.toEpochMilli(),
					System.currentTimeMillis(),
					DateUtils.MINUTE_IN_MILLIS,
					DateUtils.FORMAT_ABBREV_RELATIVE,
				).toString()
				val pct = entry.history.percent.coerceIn(0f, 1f)
				val pctLabel = if (ReadingProgress.isValid(entry.history.percent)) {
					"${ReadingProgress.percentToString(entry.history.percent)}%"
				} else {
					""
				}
				val sourceTitle = entry.manga.source.getTitle(context)
				HistoryEntryItem(
					manga = entry.manga,
					history = entry.history,
					sourceTitle = sourceTitle,
					progressPercent = pct,
					dateGroup = group,
					timeSummary = buildString {
						append(sourceTitle)
						if (entry.manga.source.isNovelSource) append(" • Novel")
						if (pctLabel.isNotEmpty()) append(" • ").append(pctLabel)
						append(" • ").append(relTime)
					},
				)
			}

		val grouped = LinkedHashMap<String, List<HistoryEntryItem>>()
		for (item in filtered) {
			grouped[item.dateGroup] = (grouped[item.dateGroup].orEmpty() + item)
		}

		HistoryUiState(
			groups = grouped,
			totalCount = filtered.size,
			query = query,
			sortOrder = sortOrder,
			quickFilter = filter,
			groupByDate = groupEnabled,
			isLoaded = true,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = HistoryUiState(),
		)

	fun setQuery(query: String) {
		queryFlow.value = query
	}

	fun setQuickFilter(filter: HistoryQuickFilter) {
		quickFilterFlow.value = filter
	}

	fun setSortOrder(order: ListSortOrder) {
		settings.historySortOrder = order
	}

	fun setGroupByDate(enabled: Boolean) {
		settings.isHistoryGroupingEnabled = enabled
	}

	fun removeEntry(manga: Manga) {
		viewModelScope.launch(Dispatchers.IO) {
			val handle = historyRepository.delete(listOf(manga.id))
			toastManager.show(
				message = "Removed ${manga.title} from history",
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}

	fun clearAllHistory() {
		viewModelScope.launch(Dispatchers.IO) {
			historyRepository.clear()
			toastManager.show("Reading history cleared")
		}
	}

	fun clearNonFavouriteHistory() {
		viewModelScope.launch(Dispatchers.IO) {
			historyRepository.deleteNotFavorite()
			toastManager.show("Cleared non-library history")
		}
	}
}
