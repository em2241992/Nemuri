package org.nemuri.app.ui.feed

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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.koitharu.kotatsu.tracker.domain.model.TrackingLogItem
import org.koitharu.kotatsu.tracker.work.TrackWorker
import org.nemuri.app.ui.components.NemuriToastManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@Immutable
data class FeedEntryItem(
	val logId: Long,
	val logItem: TrackingLogItem,
	val sourceTitle: String,
	val chaptersSummary: String,
	val newChaptersCount: Int,
	val timeLabel: String,
	val dateGroup: String,
	val firstChapterId: Long?,
)

@Immutable
data class ContinueReadingItem(
	val manga: Manga,
	val history: MangaHistory,
	val progressPercentLabel: String,
	val sourceTitle: String,
)

@Immutable
data class FeedUiState(
	val isHubMode: Boolean = true,
	val continueReading: List<ContinueReadingItem> = emptyList(),
	val recentlyAdded: List<Manga> = emptyList(),
	val hubUpdates: List<FeedEntryItem> = emptyList(),
	val groups: Map<String, List<FeedEntryItem>> = emptyMap(),
	val totalCount: Int = 0,
	val unreadCount: Int = 0,
	val query: String = "",
	val unreadOnly: Boolean = false,
	val isTrackerRunning: Boolean = false,
	val isLoaded: Boolean = false,
)

private data class FeedPrefsSnapshot(
	val unreadOnly: Boolean,
	val isHubMode: Boolean,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val trackingRepository: TrackingRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val trackScheduler: TrackWorker.Scheduler,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val queryFlow = MutableStateFlow("")

	private val prefsFlow = settings.observeChanges()
		.filter { it == null || it == AppSettings.KEY_FEED_UNREAD_ONLY || it == AppSettings.KEY_FEED_HUB_MODE }
		.onStart { emit(null) }
		.map {
			FeedPrefsSnapshot(
				unreadOnly = settings.isFeedUnreadOnly,
				isHubMode = settings.isFeedHubMode,
			)
		}

	private val localHubDataFlow = combine(
		historyRepository.observeAllWithHistory(ListSortOrder.LAST_READ, emptySet(), 12),
		favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), 12),
	) { historyList, recentFavs ->
		historyList to recentFavs
	}

	val uiState: StateFlow<FeedUiState> = combine(
		trackingRepository.observeTrackingLog(limit = 500, filterOptions = emptySet()),
		localHubDataFlow,
		prefsFlow,
		queryFlow,
		trackScheduler.observeIsRunning(),
	) { logs, (historyList, recentFavs), prefs, query, isTrackerRunning ->
		val trimmed = query.trim()
		val unreadEntriesCount = logs.count { it.isNew }
		val zoneId = ZoneId.systemDefault()
		val today = LocalDate.now(zoneId)
		val yesterday = today.minusDays(1)
		val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

		val allEntries = logs
			.asSequence()
			.filter { item ->
				trimmed.isEmpty() ||
					item.manga.title.contains(trimmed, ignoreCase = true) ||
					item.chapters.any { ch -> ch.name.contains(trimmed, ignoreCase = true) } ||
					item.manga.source.getTitle(context).contains(trimmed, ignoreCase = true)
			}
			.map { item ->
				val date = item.createdAt.atZone(zoneId).toLocalDate()
				val groupLabel = when (date) {
					today -> "Today"
					yesterday -> "Yesterday"
					else -> dateFormatter.format(date)
				}
				val relative = DateUtils.getRelativeTimeSpanString(
					item.createdAt.toEpochMilli(),
					System.currentTimeMillis(),
					DateUtils.MINUTE_IN_MILLIS,
					DateUtils.FORMAT_ABBREV_RELATIVE,
				).toString()
				val chaptersText = when (item.chapters.size) {
					0 -> "Updated"
					1 -> item.chapters.first().name
					2 -> "${item.chapters[0].name} • ${item.chapters[1].name}"
					else -> "${item.chapters.first().name} + ${item.chapters.size - 1} more"
				}
				val sourcePrefix = if (item.manga.source.isNovelSource) "Novel" else null
				val sourceName = listOfNotNull(sourcePrefix, item.manga.source.getTitle(context))
					.joinToString(" • ")

				FeedEntryItem(
					logId = item.id,
					logItem = item,
					sourceTitle = sourceName,
					chaptersSummary = chaptersText,
					newChaptersCount = item.chapters.count { it.isNew }.takeIf { it > 0 } ?: item.chapters.size,
					timeLabel = relative,
					dateGroup = groupLabel,
					firstChapterId = item.chapters.firstOrNull()?.id,
				)
			}
			.toList()

		val filtered = if (prefs.unreadOnly) {
			allEntries.filter { it.logItem.isNew }
		} else {
			allEntries
		}

		val continueItems = historyList
			.filter { item ->
				trimmed.isEmpty() || item.manga.title.contains(trimmed, ignoreCase = true)
			}
			.map { item ->
				val pct = (item.history.percent.coerceIn(0f, 1f) * 100f).roundToInt()
				ContinueReadingItem(
					manga = item.manga,
					history = item.history,
					progressPercentLabel = "$pct%",
					sourceTitle = item.manga.source.getTitle(context),
				)
			}

		val recentAddedItems = recentFavs.filter { manga ->
			trimmed.isEmpty() || manga.title.contains(trimmed, ignoreCase = true)
		}

		val hubUpdates = (allEntries.filter { it.logItem.isNew }.ifEmpty { allEntries }).take(8)

		FeedUiState(
			isHubMode = prefs.isHubMode,
			continueReading = continueItems,
			recentlyAdded = recentAddedItems,
			hubUpdates = hubUpdates,
			groups = filtered.groupBy { it.dateGroup },
			totalCount = filtered.size,
			unreadCount = unreadEntriesCount,
			query = query,
			unreadOnly = prefs.unreadOnly,
			isTrackerRunning = isTrackerRunning,
			isLoaded = true,
		)
	}.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = FeedUiState(),
		)

	fun setQuery(query: String) {
		queryFlow.value = query
	}

	fun setUnreadOnly(enabled: Boolean) {
		settings.isFeedUnreadOnly = enabled
	}

	fun setHubMode(enabled: Boolean) {
		settings.isFeedHubMode = enabled
	}

	fun checkForUpdatesNow() {
		viewModelScope.launch(Dispatchers.IO) {
			if (uiState.value.isTrackerRunning) {
				trackScheduler.stopNow()
				toastManager.show("Stopped update check")
			} else {
				trackScheduler.startNow()
				toastManager.show("Checking for new chapters…")
			}
		}
	}

	fun markMangaRead(mangaId: Long) {
		viewModelScope.launch(Dispatchers.IO) {
			val handle = trackingRepository.markLogsRead(mangaId)
			toastManager.show(
				message = "Marked updates read",
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}

	fun removeLogEntry(logId: Long) {
		viewModelScope.launch(Dispatchers.IO) {
			val handle = trackingRepository.removeLog(logId)
			if (handle != null) {
				toastManager.show(
					message = "Removed feed entry",
					actionLabel = "Undo",
					onAction = { handle.reverseAsync() },
				)
			}
		}
	}

	fun downloadUpdates(entry: FeedEntryItem) {
		viewModelScope.launch(Dispatchers.IO) {
			val manga = entry.logItem.manga
			val chapterIds = entry.logItem.chapters
				.mapNotNull { it.id }
				.toLongArray()
				.takeIf { it.isNotEmpty() }
			val meteredAllowed = settings.allowDownloadOnMeteredNetwork != TriStateOption.DISABLED
			downloadScheduler.schedule(
				listOf(
					manga to DownloadTask(
						mangaId = manga.id,
						isPaused = false,
						isSilent = false,
						chaptersIds = chapterIds,
						destination = null,
						format = null,
						allowMeteredNetwork = meteredAllowed,
					),
				),
			)
			toastManager.show("Queued download for ${manga.title}")
		}
	}

	fun markAllRead() {
		viewModelScope.launch(Dispatchers.IO) {
			trackingRepository.clearCounters()
			toastManager.show("Cleared all unread update badges")
		}
	}

	fun clearFeedHistory() {
		viewModelScope.launch(Dispatchers.IO) {
			trackingRepository.clearLogs()
			toastManager.show("Cleared updates feed")
		}
	}
}
