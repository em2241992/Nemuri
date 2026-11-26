package org.nemuri.app.ui.library

import android.content.Context
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.koitharu.kotatsu.tracker.work.TrackWorker
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

enum class LibraryQuickFilter(val label: String, val option: ListFilterOption?) {
	ALL("All", null),
	DOWNLOADED("Downloaded", ListFilterOption.Downloaded),
	NEW_CHAPTERS("New Chapters", ListFilterOption.Macro.NEW_CHAPTERS),
	ONGOING("Ongoing", ListFilterOption.NOT_COMPLETED),
	COMPLETED("Completed", ListFilterOption.Macro.COMPLETED),
}

@Immutable
data class LibraryMangaItem(
	val manga: Manga,
	val sourceTitle: String,
	val unreadCount: Int,
	val progress: Float,
	val isCaughtUp: Boolean,
	val isDownloaded: Boolean,
)

@Immutable
data class LibraryUiState(
	val categories: List<FavouriteCategory> = emptyList(),
	val selectedCategoryId: Long = LibraryViewModel.ALL_CATEGORIES_ID,
	val items: List<LibraryMangaItem> = emptyList(),
	val query: String = "",
	val quickFilter: LibraryQuickFilter = LibraryQuickFilter.ALL,
	val listMode: ListMode = ListMode.GRID,
	val gridColumns: Int = 3,
	val sortOrder: ListSortOrder = ListSortOrder.NEWEST,
	val selectedIds: Set<Long> = emptySet(),
	val isTrackerRunning: Boolean = false,
	val showAllCategoryTab: Boolean = true,
	val isLoaded: Boolean = false,
)

private data class LibraryControls(
	val categoryId: Long = LibraryViewModel.ALL_CATEGORIES_ID,
	val query: String = "",
	val quickFilter: LibraryQuickFilter = LibraryQuickFilter.ALL,
	val sortOrderOverride: ListSortOrder? = null,
)

private data class LibraryPrefSnapshot(
	val listMode: ListMode,
	val gridColumns: Int,
	val allSortOrder: ListSortOrder,
	val showAllTab: Boolean,
)

private data class LibrarySupplementary(
	val categories: List<FavouriteCategory>,
	val progressByManga: Map<Long, Float>,
	val newChaptersByManga: Map<Long, Int>,
	val downloadedIds: Set<Long>,
	val isTrackerRunning: Boolean,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val favouritesRepository: FavouritesRepository,
	private val historyRepository: HistoryRepository,
	private val trackingRepository: TrackingRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val trackScheduler: TrackWorker.Scheduler,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val controls = MutableStateFlow(LibraryControls())
	private val selectedIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

	private val prefsFlow = settings.observeChanges()
		.filter { key ->
			key == null ||
				key == AppSettings.KEY_LIST_MODE ||
				key == AppSettings.KEY_LIST_MODE_FAVORITES ||
				key == AppSettings.KEY_GRID_SIZE ||
				key == AppSettings.KEY_FAVORITES_ORDER ||
				key == AppSettings.KEY_ALL_FAVOURITES_VISIBLE
		}
		.onStart { emit(null) }
		.map {
			LibraryPrefSnapshot(
				listMode = settings.favoritesListMode,
				gridColumns = gridColumnsFromPref(settings.gridSize),
				allSortOrder = settings.allFavoritesSortOrder,
				showAllTab = settings.isAllFavouritesVisible,
			)
		}

	private val supplementaryFlow = combine(
		favouritesRepository.observeCategoriesForLibrary(),
		historyRepository.observeAllWithHistory(ListSortOrder.LAST_READ, emptySet(), Int.MAX_VALUE)
			.map { list ->
				list.associate { entry ->
					val pct = if (ReadingProgress.isValid(entry.history.percent)) entry.history.percent else 0f
					entry.manga.id to pct
				}
			},
		trackingRepository.observeUpdatedManga(Int.MAX_VALUE, emptySet())
			.map { list -> list.associate { it.manga.id to it.newChapters } },
		favouritesRepository.observeAll(ListSortOrder.ALPHABETIC, setOf(ListFilterOption.Downloaded), Int.MAX_VALUE)
			.map { list -> list.mapTo(HashSet()) { it.id } },
		trackScheduler.observeIsRunning(),
	) { categories, progress, newChapters, downloaded, running ->
		LibrarySupplementary(
			categories = categories,
			progressByManga = progress,
			newChaptersByManga = newChapters,
			downloadedIds = downloaded,
			isTrackerRunning = running,
		)
	}

	private val mangaListFlow = combine(controls, prefsFlow, supplementaryFlow) { ctrl, prefs, supp ->
		val effectiveCatId = resolveCategoryId(ctrl.categoryId, supp.categories, prefs.showAllTab)
		val catSort = supp.categories.firstOrNull { it.id == effectiveCatId }?.order ?: prefs.allSortOrder
		val filterSet = setOfNotNull(ctrl.quickFilter.option)
		Triple(effectiveCatId, catSort, filterSet)
	}.flatMapLatest { (catId, order, filterSet) ->
		if (catId == ALL_CATEGORIES_ID) {
			favouritesRepository.observeAll(order, filterSet, Int.MAX_VALUE)
		} else {
			favouritesRepository.observeAll(catId, order, filterSet, Int.MAX_VALUE)
		}
	}

	val uiState: StateFlow<LibraryUiState> = combine(
		combine(mangaListFlow, supplementaryFlow, prefsFlow) { list, supp, prefs ->
			Triple(list, supp, prefs)
		},
		controls,
		selectedIdsFlow,
	) { (list, supp, prefs), ctrl, selected ->
		val effectiveCatId = resolveCategoryId(ctrl.categoryId, supp.categories, prefs.showAllTab)
		val effectiveSort = supp.categories.firstOrNull { it.id == effectiveCatId }?.order ?: prefs.allSortOrder
		val q = ctrl.query.trim()
		val mapped = list
			.filter { manga ->
				q.isEmpty() ||
					manga.title.contains(q, ignoreCase = true) ||
					manga.altTitles.any { it.contains(q, ignoreCase = true) } ||
					manga.authors.any { it.contains(q, ignoreCase = true) }
			}
			.map { manga ->
				val unread = supp.newChaptersByManga[manga.id] ?: 0
				val pct = supp.progressByManga[manga.id] ?: 0f
				LibraryMangaItem(
					manga = manga,
					sourceTitle = buildString {
						append(manga.source.getTitle(context))
						if (manga.source.isNovelSource) append(" • Novel")
					},
					unreadCount = unread,
					progress = pct,
					isCaughtUp = unread == 0 && ReadingProgress.isCompleted(pct),
					isDownloaded = manga.id in supp.downloadedIds,
				)
			}

		LibraryUiState(
			categories = supp.categories,
			selectedCategoryId = effectiveCatId,
			items = mapped,
			query = ctrl.query,
			quickFilter = ctrl.quickFilter,
			listMode = prefs.listMode,
			gridColumns = prefs.gridColumns,
			sortOrder = effectiveSort,
			selectedIds = selected,
			isTrackerRunning = supp.isTrackerRunning,
			showAllCategoryTab = prefs.showAllTab,
			isLoaded = true,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = LibraryUiState(
				listMode = settings.favoritesListMode,
				gridColumns = gridColumnsFromPref(settings.gridSize),
				sortOrder = settings.allFavoritesSortOrder,
				showAllCategoryTab = settings.isAllFavouritesVisible,
			),
		)

	fun selectCategory(categoryId: Long) {
		selectedIdsFlow.value = emptySet()
		controls.update { it.copy(categoryId = categoryId) }
	}

	fun setQuery(query: String) {
		controls.update { it.copy(query = query) }
	}

	fun setQuickFilter(filter: LibraryQuickFilter) {
		controls.update { it.copy(quickFilter = filter) }
	}

	fun setListMode(mode: ListMode) {
		settings.favoritesListMode = mode
	}

	fun setGridColumns(columns: Int) {
		settings.gridSize = prefFromGridColumns(columns)
	}

	fun setSortOrder(order: ListSortOrder) {
		val categoryId = uiState.value.selectedCategoryId
		if (categoryId == ALL_CATEGORIES_ID) {
			settings.allFavoritesSortOrder = order
		} else {
			viewModelScope.launch(Dispatchers.IO) {
				favouritesRepository.setCategoryOrder(categoryId, order)
			}
		}
	}

	fun toggleSelection(mangaId: Long) {
		selectedIdsFlow.update { current ->
			if (mangaId in current) current - mangaId else current + mangaId
		}
	}

	fun selectAll() {
		selectedIdsFlow.value = uiState.value.items.mapTo(HashSet()) { it.manga.id }
	}

	fun invertSelection() {
		val allIds = uiState.value.items.map { it.manga.id }
		val current = selectedIdsFlow.value
		selectedIdsFlow.value = allIds.filterTo(HashSet()) { it !in current }
	}

	fun setSelection(ids: Set<Long>) {
		selectedIdsFlow.value = ids
	}

	fun clearSelection() {
		selectedIdsFlow.value = emptySet()
	}

	fun setPortraitColumns(columns: Int) {
		settings.gridColumnsPortrait = columns.coerceIn(2, 6)
	}

	fun setLandscapeColumns(columns: Int) {
		settings.gridColumnsLandscape = columns.coerceIn(4, 9)
	}

	fun createCategory(title: String) {
		val trimmed = title.trim()
		if (trimmed.isEmpty()) return
		viewModelScope.launch(Dispatchers.IO) {
			val cat = favouritesRepository.createCategory(
				title = trimmed,
				sortOrder = settings.defaultCategorySortOrder,
				isTrackerEnabled = true,
				isNewChaptersDownloadEnabled = false,
				isVisibleOnShelf = true,
			)
			controls.update { it.copy(categoryId = cat.id) }
			toastManager.show("Created category \"${cat.title}\"")
		}
	}

	fun deleteSelectedCategory() {
		val categoryId = uiState.value.selectedCategoryId
		if (categoryId == ALL_CATEGORIES_ID) return
		viewModelScope.launch(Dispatchers.IO) {
			favouritesRepository.removeCategories(listOf(categoryId))
			controls.update { it.copy(categoryId = ALL_CATEGORIES_ID) }
			toastManager.show("Category deleted")
		}
	}

	fun removeSelectedFromLibrary() {
		val ids = selectedIdsFlow.value.toSet()
		if (ids.isEmpty()) return
		selectedIdsFlow.value = emptySet()
		val currentCatId = uiState.value.selectedCategoryId
		viewModelScope.launch(Dispatchers.IO) {
			val handle = if (currentCatId == ALL_CATEGORIES_ID) {
				favouritesRepository.removeFromFavourites(ids)
			} else {
				favouritesRepository.removeFromCategory(currentCatId, ids)
			}
			toastManager.show(
				message = if (currentCatId == ALL_CATEGORIES_ID) {
					"Removed ${ids.size} title(s) from library"
				} else {
					"Removed ${ids.size} title(s) from category"
				},
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}

	fun addSelectedToCategory(category: FavouriteCategory) {
		val ids = selectedIdsFlow.value
		val mangas = uiState.value.items.filter { it.manga.id in ids }.map { it.manga }
		if (mangas.isEmpty()) return
		selectedIdsFlow.value = emptySet()
		viewModelScope.launch(Dispatchers.IO) {
			favouritesRepository.addToCategory(category.id, mangas)
			toastManager.show("Added ${mangas.size} title(s) to ${category.title}")
		}
	}

	fun downloadSelected() {
		val ids = selectedIdsFlow.value
		val mangas = uiState.value.items.filter { it.manga.id in ids }.map { it.manga }
		if (mangas.isEmpty()) return
		selectedIdsFlow.value = emptySet()
		viewModelScope.launch(Dispatchers.IO) {
			val meteredAllowed = settings.allowDownloadOnMeteredNetwork != TriStateOption.DISABLED
			val tasks = mangas.map { manga ->
				manga to DownloadTask(
					mangaId = manga.id,
					isPaused = false,
					isSilent = false,
					chaptersIds = null,
					destination = null,
					format = null,
					allowMeteredNetwork = meteredAllowed,
				)
			}
			downloadScheduler.schedule(tasks)
			toastManager.show("Queued ${tasks.size} download(s)")
		}
	}

	fun markSelectedUpdatesRead() {
		val ids = selectedIdsFlow.value.toSet()
		if (ids.isEmpty()) return
		selectedIdsFlow.value = emptySet()
		viewModelScope.launch(Dispatchers.IO) {
			trackingRepository.clearUpdates(ids)
			toastManager.show("Marked ${ids.size} title(s) read")
		}
	}

	fun markSelectedUnread() {
		val ids = selectedIdsFlow.value.toSet()
		if (ids.isEmpty()) return
		selectedIdsFlow.value = emptySet()
		viewModelScope.launch(Dispatchers.IO) {
			historyRepository.delete(ids)
			toastManager.show("Reset reading progress for ${ids.size} title(s)")
		}
	}

	fun checkForUpdatesNow() {
		viewModelScope.launch(Dispatchers.IO) {
			if (uiState.value.isTrackerRunning) {
				trackScheduler.stopNow()
				toastManager.show("Stopped library update check")
			} else {
				trackScheduler.startNow()
				toastManager.show("Checking library for updates…")
			}
		}
	}

	private fun resolveCategoryId(
		requestedId: Long,
		categories: List<FavouriteCategory>,
		showAllTab: Boolean,
	): Long {
		if (requestedId == ALL_CATEGORIES_ID && (showAllTab || categories.isEmpty())) {
			return ALL_CATEGORIES_ID
		}
		if (categories.any { it.id == requestedId }) {
			return requestedId
		}
		return if (showAllTab || categories.isEmpty()) ALL_CATEGORIES_ID else categories.first().id
	}

	private fun gridColumnsFromPref(gridSize: Int): Int = when {
		gridSize >= 130 -> 2
		gridSize >= 95 -> 3
		gridSize >= 75 -> 4
		else -> 5
	}

	private fun prefFromGridColumns(columns: Int): Int = when (columns) {
		2 -> 135
		3 -> 100
		4 -> 80
		else -> 65
	}

	companion object {
		const val ALL_CATEGORIES_ID = 0L
	}
}
