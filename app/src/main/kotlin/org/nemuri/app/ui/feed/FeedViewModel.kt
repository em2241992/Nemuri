package org.nemuri.app.ui.feed

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.core.model.getSummary
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.homeUrl
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.FeedScreenMode
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.koitharu.kotatsu.tracker.domain.model.TrackingLogItem
import org.koitharu.kotatsu.tracker.work.TrackWorker
import org.nemuri.app.browser.WebViewChallengeAction
import org.nemuri.app.browser.toWebViewChallengeAction
import org.nemuri.app.ui.components.NemuriToastManager
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.roundToInt

enum class FeedSecondaryFilter(val label: String) {
	ALL("All"),
	UNREAD("Unread"),
	DOWNLOADED("Downloaded"),
	NOT_IN_LIBRARY("Not in Library"),
}

@Immutable
data class FeedSourceOption(
	val sourceInfo: MangaSourceInfo,
	val sourceName: String,
	val title: String,
	val summary: String?,
	val homeUrl: String?,
	val isPinned: Boolean,
	val isNovel: Boolean,
)

@Immutable
data class FeedHubCarousel(
	val id: String,
	val title: String,
	val items: List<Manga>,
	val sortOrder: SortOrder? = null,
)

@Immutable
data class FeedEntryItem(
	val logId: Long,
	val logItem: TrackingLogItem,
	val sourceTitle: String,
	val chaptersSummary: String,
	val shortChapterLabel: String,
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
	val progressBarFraction: Float,
	val sourceTitle: String,
)

@Immutable
data class FeedUiState(
	val mode: FeedScreenMode = FeedScreenMode.HUB,
	val secondaryFilter: FeedSecondaryFilter = FeedSecondaryFilter.ALL,
	val continueReading: List<ContinueReadingItem> = emptyList(),
	val recentlyAdded: List<Manga> = emptyList(),
	val hubUpdates: List<FeedEntryItem> = emptyList(),
	val galleryEntries: List<FeedEntryItem> = emptyList(),
	val groups: Map<String, List<FeedEntryItem>> = emptyMap(),
	val totalCount: Int = 0,
	val unreadCount: Int = 0,
	val readFeedCount: Int = 0,
	val query: String = "",
	val isTrackerRunning: Boolean = false,
	val isLoaded: Boolean = false,
	// Pinned Home Source Hub Carousels (Popular & Latest Updates)
	val enabledSources: List<FeedSourceOption> = emptyList(),
	val homeSource: FeedSourceOption? = null,
	val isHomeSourceExplicitlyPinned: Boolean = false,
	val isHomeSourceLoading: Boolean = false,
	val homeSourceError: String? = null,
	val homeSourceCloudFlare: CloudFlareProtectedException? = null,
	val homeSourceInteractive: InteractiveActionRequiredException? = null,
	val homeSourceBrowserUrl: String? = null,
	val homeCarousels: List<FeedHubCarousel> = emptyList(),
	val libraryMangaIds: Set<Long> = emptySet(),
	val libraryMangaUrls: Set<String> = emptySet(),
	// For You Recommendations & Interactive Taste Profile Filter
	val isDiscoveryLoading: Boolean = false,
	val selectedGenre: String? = null,
	val discoveryPreview: List<DiscoveryRecommendation> = emptyList(),
	val discoverySections: List<DiscoverySection> = emptyList(),
	val discoveryGridItems: List<DiscoveryRecommendation> = emptyList(),
	val discoveryGenres: List<String> = emptyList(),
)

private data class FeedPrefsSnapshot(
	val mode: FeedScreenMode,
	val unreadOnlyDefault: Boolean,
	val preferredHomeSourceName: String?,
)

private data class HomeSourceRawState(
	val sourceName: String? = null,
	val isLoading: Boolean = false,
	val error: String? = null,
	val cloudFlare: CloudFlareProtectedException? = null,
	val interactive: InteractiveActionRequiredException? = null,
	val browserUrl: String? = null,
	val carousels: List<FeedHubCarousel> = emptyList(),
)

private data class DiscoveryRawState(
	val isLoading: Boolean = false,
	val result: DiscoveryFeedResult = DiscoveryFeedResult(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val db: MangaDatabase,
	private val trackingRepository: TrackingRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
	private val feedDiscoveryUseCase: FeedDiscoveryUseCase,
	private val trackScheduler: TrackWorker.Scheduler,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val queryFlow = MutableStateFlow("")
	private val secondaryFilterFlow = MutableStateFlow(
		if (settings.isFeedUnreadOnly) FeedSecondaryFilter.UNREAD else FeedSecondaryFilter.ALL,
	)
	private val selectedGenreFlow = MutableStateFlow<String?>(null)
	private val homeSourceRawFlow = MutableStateFlow(HomeSourceRawState())
	private val discoveryRawFlow = MutableStateFlow(DiscoveryRawState(isLoading = true))

	@Volatile
	private var loadedHomeSourceName: String? = null
	private var homeSourceJob: Job? = null
	private var discoveryJob: Job? = null

	private val prefsFlow = settings.observeChanges()
		.filter { key ->
			key == null ||
				key == AppSettings.KEY_FEED_MODE ||
				key == AppSettings.KEY_FEED_HUB_MODE ||
				key == AppSettings.KEY_FEED_UNREAD_ONLY ||
				key == AppSettings.KEY_EXPLORE_HOME_SOURCE
		}
		.onStart { emit(null) }
		.map {
			FeedPrefsSnapshot(
				mode = settings.feedMode,
				unreadOnlyDefault = settings.isFeedUnreadOnly,
				preferredHomeSourceName = settings.exploreHomeSource,
			)
		}

	private val enabledSourceOptionsFlow = sourcesRepository.observeEnabledSources()
		.map { list ->
			list.map { info ->
				FeedSourceOption(
					sourceInfo = info,
					sourceName = info.name,
					title = info.getTitle(context),
					summary = info.getSummary(context),
					homeUrl = info.homeUrl,
					isPinned = info.isPinned,
					isNovel = info.isNovelSource,
				)
			}
		}

	// TrackLogsDao SQL-backed filtering for All / Unread / Downloaded / Not in Library
	private val filteredLogsFlow = secondaryFilterFlow.flatMapLatest { filter ->
		val options: Set<ListFilterOption> = when (filter) {
			FeedSecondaryFilter.ALL -> emptySet()
			FeedSecondaryFilter.UNREAD -> setOf(ListFilterOption.Unread)
			FeedSecondaryFilter.DOWNLOADED -> setOf(ListFilterOption.Downloaded)
			FeedSecondaryFilter.NOT_IN_LIBRARY -> setOf(ListFilterOption.NOT_FAVORITE)
		}
		trackingRepository.observeTrackingLog(limit = 500, filterOptions = options)
	}

	private val readLogsCountFlow = trackingRepository.observeTrackingLog(limit = 500, filterOptions = emptySet())
		.map { logs -> logs.count { !it.isNew } }
		.distinctUntilChanged()

	private val localHubDataFlow = secondaryFilterFlow.flatMapLatest { filter ->
		val historyOptions: Set<ListFilterOption> = when (filter) {
			FeedSecondaryFilter.ALL -> emptySet()
			FeedSecondaryFilter.UNREAD -> setOf(ListFilterOption.NOT_COMPLETED)
			FeedSecondaryFilter.DOWNLOADED -> setOf(ListFilterOption.Downloaded)
			FeedSecondaryFilter.NOT_IN_LIBRARY -> setOf(ListFilterOption.NOT_FAVORITE)
		}
		val favOptions: Set<ListFilterOption> = when (filter) {
			FeedSecondaryFilter.DOWNLOADED -> setOf(ListFilterOption.Downloaded)
			else -> emptySet()
		}
		combine(
			historyRepository.observeAllWithHistory(ListSortOrder.LAST_READ, historyOptions, 16),
			favouritesRepository.observeAll(ListSortOrder.NEWEST, favOptions, 16),
			favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), 2000),
			trackingRepository.observeUnreadUpdatesCount(),
			readLogsCountFlow,
		) { historyList, recentFavs, allFavs, unreadCount, readCount ->
			LocalHubSnapshot(
				historyList = historyList,
				recentFavs = if (filter == FeedSecondaryFilter.NOT_IN_LIBRARY) emptyList() else recentFavs,
				allFavs = allFavs,
				unreadCount = unreadCount,
				readFeedCount = readCount,
			)
		}
	}

	init {
		// Automatically resolve & load Pinned Home Source (Popular & Latest Updates) for Hub
		viewModelScope.launch(Dispatchers.IO) {
			combine(enabledSourceOptionsFlow, prefsFlow) { sources, prefs ->
				resolveHomeSourceOption(sources, prefs.preferredHomeSourceName)
			}
				.distinctUntilChanged { a, b -> a?.sourceName == b?.sourceName }
				.collect { resolved ->
					if (resolved == null) {
						loadedHomeSourceName = null
						homeSourceJob?.cancel()
						homeSourceRawFlow.value = HomeSourceRawState()
					} else if (resolved.sourceName != loadedHomeSourceName) {
						triggerHomeSourceLoad(resolved)
					}
				}
		}

		// Load For You discovery recommendations when sources become available
		viewModelScope.launch(Dispatchers.IO) {
			enabledSourceOptionsFlow
				.map { it.size }
				.distinctUntilChanged()
				.collect { count ->
					if (count > 0) {
						refreshDiscovery(force = discoveryRawFlow.value.result.sections.isEmpty())
					} else {
						discoveryRawFlow.value = DiscoveryRawState(isLoading = false)
					}
				}
		}
	}

	private val baseFeedFlow = combine(
		filteredLogsFlow,
		localHubDataFlow,
		prefsFlow,
		queryFlow,
		secondaryFilterFlow,
	) { logs, localData, prefs, query, secFilter ->
		BaseFeedInputs(logs, localData, prefs, query, secFilter)
	}

	private val remoteFeedFlow = combine(
		enabledSourceOptionsFlow,
		homeSourceRawFlow,
		discoveryRawFlow,
		selectedGenreFlow,
		trackScheduler.observeIsRunning(),
	) { sources, homeRaw, discoveryRaw, activeGenre, isTrackerRunning ->
		RemoteFeedInputs(sources, homeRaw, discoveryRaw, activeGenre, isTrackerRunning)
	}

	val uiState: StateFlow<FeedUiState> = combine(
		baseFeedFlow,
		remoteFeedFlow,
	) { base, remote ->
		val trimmed = base.query.trim()
		val zoneId = ZoneId.systemDefault()
		val today = LocalDate.now(zoneId)
		val yesterday = today.minusDays(1)
		val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

		val filteredEntries = base.logs
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
				val firstName = item.chapters.firstOrNull()?.name?.trim().orEmpty()
				val chaptersText = when (item.chapters.size) {
					0 -> "Updated"
					1 -> firstName
					2 -> "${item.chapters[0].name} • ${item.chapters[1].name}"
					else -> "$firstName + ${item.chapters.size - 1} more"
				}
				val shortCh = firstName.ifEmpty { "New chapter" }
				val sourcePrefix = if (item.manga.source.isNovelSource) "Novel" else null
				val sourceName = listOfNotNull(sourcePrefix, item.manga.source.getTitle(context))
					.joinToString(" • ")

				FeedEntryItem(
					logId = item.id,
					logItem = item,
					sourceTitle = sourceName,
					chaptersSummary = chaptersText,
					shortChapterLabel = shortCh,
					newChaptersCount = item.chapters.count { it.isNew }.takeIf { it > 0 } ?: item.chapters.size,
					timeLabel = relative,
					dateGroup = groupLabel,
					firstChapterId = item.chapters.firstOrNull()?.id,
				)
			}
			.toList()
			.distinctBy { it.logId }

		// Resolve real chapter progress so Continue Reading NEVER displays "▶ 0%"
		val chaptersDao = db.getChaptersDao()
		val continueItems = base.localData.historyList
			.filter { item ->
				trimmed.isEmpty() || item.manga.title.contains(trimmed, ignoreCase = true)
			}
			.distinctBy { it.manga.id }
			.map { item ->
				val (label, barFraction) = resolveMeaningfulReadProgress(
					mangaId = item.manga.id,
					history = item.history,
					chaptersDao = chaptersDao,
				)
				ContinueReadingItem(
					manga = item.manga,
					history = item.history,
					progressPercentLabel = label,
					progressBarFraction = barFraction,
					sourceTitle = item.manga.source.getTitle(context),
				)
			}

		val recentAddedItems = base.localData.recentFavs.filter { manga ->
			trimmed.isEmpty() || manga.title.contains(trimmed, ignoreCase = true)
		}.distinctBy { it.id }

		val hubUpdates = if (base.secondaryFilter == FeedSecondaryFilter.ALL) {
			(filteredEntries.filter { it.logItem.isNew }.ifEmpty { filteredEntries }).take(6)
		} else {
			filteredEntries.take(6)
		}

		val libIds = base.localData.allFavs.mapTo(HashSet(base.localData.allFavs.size)) { it.id }
		val libUrls = base.localData.allFavs.mapTo(HashSet(base.localData.allFavs.size)) { it.url }

		val resolvedHome = resolveHomeSourceOption(
			sources = remote.sources,
			preferredName = base.prefs.preferredHomeSourceName,
		)
		val isExplicitHome = resolvedHome != null &&
			!base.prefs.preferredHomeSourceName.isNullOrBlank() &&
			resolvedHome.sourceName == base.prefs.preferredHomeSourceName
		val matchingHomeRaw = remote.homeRaw.takeIf {
			resolvedHome != null && it.sourceName == resolvedHome.sourceName
		}

		val filteredCarousels = matchingHomeRaw?.carousels.orEmpty().mapNotNull { carousel ->
			val items = carousel.items.filter { manga ->
				val matchesQuery = trimmed.isEmpty() || manga.title.contains(trimmed, ignoreCase = true)
				val matchesNotInLib = base.secondaryFilter != FeedSecondaryFilter.NOT_IN_LIBRARY ||
					(manga.id !in libIds && manga.url !in libUrls)
				matchesQuery && matchesNotInLib
			}
			if (items.isEmpty()) null else carousel.copy(items = items)
		}

		val availableGenres = remote.discoveryRaw.result.matchedGenres
		val validSelectedGenre = remote.selectedGenre?.takeIf { sel ->
			availableGenres.any { it.equals(sel, ignoreCase = true) }
		}

		fun DiscoveryRecommendation.matchesBaseFilters(): Boolean {
			val matchesQuery = trimmed.isEmpty() ||
				manga.title.contains(trimmed, ignoreCase = true) ||
				reasonLabel.contains(trimmed, ignoreCase = true) ||
				sourceTitle.contains(trimmed, ignoreCase = true)
			val matchesNotInLib = base.secondaryFilter != FeedSecondaryFilter.NOT_IN_LIBRARY || !inLibrary
			return matchesQuery && matchesNotInLib
		}

		fun DiscoveryRecommendation.matchesGenreChip(genre: String): Boolean {
			return matchedGenres.any { it.equals(genre, ignoreCase = true) || it.contains(genre, ignoreCase = true) } ||
				manga.tags.any { it.title.equals(genre, ignoreCase = true) || it.title.contains(genre, ignoreCase = true) } ||
				reasonLabel.contains(genre, ignoreCase = true)
		}

		val discoveryFilteredSections = remote.discoveryRaw.result.sections.mapNotNull { sec ->
			val sectionMatchesGenre = validSelectedGenre != null && sec.title.contains(validSelectedGenre, ignoreCase = true)
			val items = sec.items.filter { rec ->
				rec.matchesBaseFilters() &&
					(validSelectedGenre == null || sectionMatchesGenre || rec.matchesGenreChip(validSelectedGenre))
			}
			if (items.isEmpty()) null else sec.copy(items = items)
		}

		val rawFilteredGrid = remote.discoveryRaw.result.discoveryGridItems.filter { rec ->
			rec.matchesBaseFilters() && (validSelectedGenre == null || rec.matchesGenreChip(validSelectedGenre))
		}
		val discoveryFilteredGrid = if (validSelectedGenre != null && rawFilteredGrid.size < 12) {
			(rawFilteredGrid + discoveryFilteredSections.flatMap { it.items })
				.distinctBy { it.manga.id }
				.take(48)
		} else {
			rawFilteredGrid
		}

		val hubPreviewItems = remote.discoveryRaw.result.previewItems
			.ifEmpty {
				(remote.discoveryRaw.result.sections.flatMap { it.items } + remote.discoveryRaw.result.discoveryGridItems)
			}
			.filter { it.matchesBaseFilters() }
			.distinctBy { it.manga.id }
			.take(16)

		FeedUiState(
			mode = base.prefs.mode,
			secondaryFilter = base.secondaryFilter,
			continueReading = continueItems,
			recentlyAdded = recentAddedItems,
			hubUpdates = hubUpdates,
			galleryEntries = filteredEntries,
			groups = filteredEntries.groupBy { it.dateGroup },
			totalCount = filteredEntries.size,
			unreadCount = base.localData.unreadCount,
			readFeedCount = base.localData.readFeedCount,
			query = base.query,
			isTrackerRunning = remote.isTrackerRunning,
			isLoaded = true,
			enabledSources = remote.sources,
			homeSource = resolvedHome,
			isHomeSourceExplicitlyPinned = isExplicitHome,
			isHomeSourceLoading = matchingHomeRaw?.isLoading == true,
			homeSourceError = matchingHomeRaw?.error,
			homeSourceCloudFlare = matchingHomeRaw?.cloudFlare,
			homeSourceInteractive = matchingHomeRaw?.interactive,
			homeSourceBrowserUrl = matchingHomeRaw?.browserUrl,
			homeCarousels = filteredCarousels,
			libraryMangaIds = libIds,
			libraryMangaUrls = libUrls,
			isDiscoveryLoading = remote.discoveryRaw.isLoading,
			selectedGenre = validSelectedGenre,
			discoveryPreview = hubPreviewItems,
			discoverySections = discoveryFilteredSections,
			discoveryGridItems = discoveryFilteredGrid,
			discoveryGenres = availableGenres,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = FeedUiState(mode = settings.feedMode),
		)

	private suspend fun resolveMeaningfulReadProgress(
		mangaId: Long,
		history: MangaHistory,
		chaptersDao: org.koitharu.kotatsu.core.db.dao.ChaptersDao,
	): Pair<String, Float> {
		val cachedChapters = runCatching { chaptersDao.findAll(mangaId) }.getOrDefault(emptyList())
		val matchedChapter = cachedChapters.firstOrNull { it.chapterId == history.chapterId }

		val chapterLabel: String? = when {
			matchedChapter != null && matchedChapter.number > 0f -> {
				val num = matchedChapter.number
				if (num % 1f == 0f) {
					"Ch. ${num.toInt()}"
				} else {
					val clean = BigDecimal.valueOf(num.toDouble()).stripTrailingZeros().toPlainString()
					"Ch. $clean"
				}
			}
			matchedChapter != null && matchedChapter.index >= 0 -> {
				"Ch. ${matchedChapter.index + 1}"
			}
			history.percent in 0f..1f && history.chaptersCount > 0 -> {
				val estimatedCh = (history.percent * history.chaptersCount).roundToInt().coerceAtLeast(1)
				"Ch. $estimatedCh"
			}
			else -> null
		}

		val validPct = history.percent in 0f..1f
		val roundedPct = if (validPct) (history.percent * 100f).roundToInt() else 0
		val hasInChapterActivity = (validPct && history.percent > 0f) || history.page > 0 || history.scroll > 0

		val label = when {
			chapterLabel != null && validPct && roundedPct >= 1 -> "$chapterLabel • $roundedPct%"
			chapterLabel != null && hasInChapterActivity -> "$chapterLabel • <1%"
			chapterLabel != null -> chapterLabel
			validPct && roundedPct >= 1 -> "${maxOf(1, roundedPct)}%"
			history.page > 0 -> "Page ${history.page + 1}"
			hasInChapterActivity -> "<1%"
			else -> "Page 1"
		}

		val barFraction = when {
			validPct && history.percent > 0f -> history.percent.coerceIn(0.03f, 1f)
			matchedChapter != null && cachedChapters.isNotEmpty() -> {
				((matchedChapter.index + 1f) / cachedChapters.size.toFloat()).coerceIn(0.04f, 1f)
			}
			else -> 0.04f
		}
		return label to barFraction
	}

	private fun resolveHomeSourceOption(
		sources: List<FeedSourceOption>,
		preferredName: String?,
	): FeedSourceOption? {
		if (sources.isEmpty()) return null
		return sources.firstOrNull { it.sourceName == preferredName }
			?: sources.firstOrNull { it.isPinned && !it.isNovel }
			?: sources.firstOrNull { !it.isNovel }
			?: sources.firstOrNull()
	}

	fun setMode(mode: FeedScreenMode) {
		settings.feedMode = mode
		if (mode == FeedScreenMode.FOR_YOU && discoveryRawFlow.value.result.sections.isEmpty() && !discoveryRawFlow.value.isLoading) {
			refreshDiscovery(force = true)
		}
	}

	fun setSecondaryFilter(filter: FeedSecondaryFilter) {
		secondaryFilterFlow.value = filter
		if (filter == FeedSecondaryFilter.UNREAD || filter == FeedSecondaryFilter.ALL) {
			settings.isFeedUnreadOnly = (filter == FeedSecondaryFilter.UNREAD)
		}
	}

	fun setSelectedGenre(genre: String?) {
		selectedGenreFlow.value = if (genre != null && selectedGenreFlow.value.equals(genre, ignoreCase = true)) {
			null
		} else {
			genre
		}
	}

	fun setQuery(query: String) {
		queryFlow.value = query
	}

	fun setHomeSource(sourceName: String) {
		settings.exploreHomeSource = sourceName
		loadedHomeSourceName = null
		val target = uiState.value.enabledSources.firstOrNull { it.sourceName == sourceName }
		if (target != null) {
			triggerHomeSourceLoad(target)
			toastManager.show("Pinned ${target.title} to Feed Hub")
		}
	}

	fun refreshHomeSource() {
		val current = uiState.value.homeSource ?: return
		loadedHomeSourceName = null
		triggerHomeSourceLoad(current)
	}

	fun refreshDiscovery(force: Boolean = true) {
		if (!force && discoveryJob?.isActive == true) return
		discoveryJob?.cancel()
		discoveryJob = viewModelScope.launch(Dispatchers.IO) {
			discoveryRawFlow.value = discoveryRawFlow.value.copy(isLoading = true)
			val result = runCatchingCancellable {
				feedDiscoveryUseCase.loadDiscoveryFeed(settings.exploreHomeSource)
			}.getOrDefault(DiscoveryFeedResult())
			discoveryRawFlow.value = DiscoveryRawState(
				isLoading = false,
				result = result,
			)
		}
	}

	private fun triggerHomeSourceLoad(option: FeedSourceOption) {
		loadedHomeSourceName = option.sourceName
		homeSourceJob?.cancel()
		homeSourceJob = viewModelScope.launch(Dispatchers.IO) {
			homeSourceRawFlow.value = HomeSourceRawState(sourceName = option.sourceName, isLoading = true)
			var capturedError: Throwable? = null
			val result = runCatchingCancellable {
				val repo = mangaRepositoryFactoryProvider.get().create(option.sourceInfo.mangaSource)
				val sortOrders = repo.sortOrders
				coroutineScope {
					val popOrder = when {
						SortOrder.POPULARITY in sortOrders -> SortOrder.POPULARITY
						SortOrder.RATING in sortOrders -> SortOrder.RATING
						else -> repo.defaultSortOrder
					}
					val popJob = async {
						runCatchingCancellable {
							repo.getList(0, popOrder, MangaListFilter()).distinctBy { it.id }.take(15)
						}
					}

					val latestOrder = when {
						SortOrder.UPDATED in sortOrders -> SortOrder.UPDATED
						SortOrder.NEWEST in sortOrders -> SortOrder.NEWEST
						else -> null
					}
					val latestJob = latestOrder?.takeIf { it != popOrder }?.let { ord ->
						async {
							runCatchingCancellable {
								repo.getList(0, ord, MangaListFilter()).distinctBy { it.id }.take(15)
							}
						}
					}

					val popRes = popJob.await()
					val latestRes = latestJob?.await()
					capturedError = popRes.exceptionOrNull() ?: latestRes?.exceptionOrNull()

					buildList {
						popRes.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
							add(
								FeedHubCarousel(
									id = "popular",
									title = "Popular on ${option.title}",
									items = items,
									sortOrder = popOrder,
								),
							)
						}
						latestRes?.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
							add(
								FeedHubCarousel(
									id = "latest",
									title = "Latest Updates on ${option.title}",
									items = items,
									sortOrder = latestOrder,
								),
							)
						}
					}
				}
			}
			if (loadedHomeSourceName != option.sourceName) return@launch
			result.onSuccess { carousels ->
				val err = if (carousels.isEmpty()) capturedError else null
				val challenge = err?.toWebViewChallengeAction(
					fallbackUrl = option.homeUrl,
					fallbackSource = option.sourceInfo.mangaSource,
					title = option.title,
				)
				homeSourceRawFlow.value = HomeSourceRawState(
					sourceName = option.sourceName,
					isLoading = false,
					error = when {
						carousels.isNotEmpty() -> null
						err != null -> err.getDisplayMessage(context.resources)
						else -> "No feeds returned by ${option.title}"
					},
					cloudFlare = (challenge as? WebViewChallengeAction.CloudFlare)?.exception,
					interactive = (challenge as? WebViewChallengeAction.Interactive)?.exception,
					browserUrl = (challenge as? WebViewChallengeAction.BrowserUrl)?.url,
					carousels = carousels,
				)
			}.onFailure { err ->
				val challenge = err.toWebViewChallengeAction(
					fallbackUrl = option.homeUrl,
					fallbackSource = option.sourceInfo.mangaSource,
					title = option.title,
				)
				homeSourceRawFlow.value = HomeSourceRawState(
					sourceName = option.sourceName,
					isLoading = false,
					error = err.getDisplayMessage(context.resources),
					cloudFlare = (challenge as? WebViewChallengeAction.CloudFlare)?.exception,
					interactive = (challenge as? WebViewChallengeAction.Interactive)?.exception,
					browserUrl = (challenge as? WebViewChallengeAction.BrowserUrl)?.url,
				)
			}
		}
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
			val mangaIds = db.getTrackLogsDao().findMangaIds().toList()
			trackingRepository.clearCounters()
			if (mangaIds.isNotEmpty()) {
				trackingRepository.clearUpdates(mangaIds)
			}
			toastManager.show("Marked all updates read")
		}
	}

	fun clearReadFeedEntries() {
		viewModelScope.launch(Dispatchers.IO) {
			val logsDao = db.getTrackLogsDao()
			val readEntries = logsDao.findAllForSync().filter { !it.isUnread }
			if (readEntries.isEmpty()) {
				toastManager.show("No read feed entries to clear")
				return@launch
			}
			for (entry in readEntries) {
				logsDao.delete(entry.id)
			}
			toastManager.show(
				message = "Cleared ${readEntries.size} read ${if (readEntries.size == 1) "entry" else "entries"}",
				actionLabel = "Undo",
				onAction = {
					viewModelScope.launch(Dispatchers.IO) {
						for (entry in readEntries) {
							logsDao.insert(entry)
						}
					}
				},
			)
		}
	}

	fun clearFeedHistory() {
		viewModelScope.launch(Dispatchers.IO) {
			trackingRepository.clearLogs()
			toastManager.show("Cleared updates feed")
		}
	}

	private data class LocalHubSnapshot(
		val historyList: List<org.koitharu.kotatsu.history.domain.model.MangaWithHistory>,
		val recentFavs: List<Manga>,
		val allFavs: List<Manga>,
		val unreadCount: Int,
		val readFeedCount: Int,
	)

	private data class BaseFeedInputs(
		val logs: List<TrackingLogItem>,
		val localData: LocalHubSnapshot,
		val prefs: FeedPrefsSnapshot,
		val query: String,
		val secondaryFilter: FeedSecondaryFilter,
	)

	private data class RemoteFeedInputs(
		val sources: List<FeedSourceOption>,
		val homeRaw: HomeSourceRawState,
		val discoveryRaw: DiscoveryRawState,
		val selectedGenre: String?,
		val isTrackerRunning: Boolean,
	)
}
