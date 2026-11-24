package org.nemuri.app.ui.explore

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.model.MangaSourceInfo
import org.koitharu.kotatsu.core.model.getSummary
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.homeUrl
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.SourceLanguage
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.explore.domain.ExploreRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreManager
import org.koitharu.kotatsu.suggestions.domain.SuggestionRepository
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

enum class ExploreSourceFilter(val label: String) {
	ALL("All"),
	PINNED("Pinned"),
	MANGA("Manga"),
	NOVELS("Novels"),
}

enum class LibraryVisibilityFilter(val label: String) {
	ALL("All Titles"),
	NOT_IN_LIBRARY("Not in Library"),
	IN_LIBRARY("In Library"),
}

@Immutable
data class ExploreSourceTile(
	val sourceInfo: MangaSourceInfo,
	val sourceName: String,
	val title: String,
	val summary: String?,
	val homeUrl: String?,
	val isNovel: Boolean,
	val isNsfw: Boolean,
	val isPinned: Boolean,
)

@Immutable
data class HomeHubCarousel(
	val id: String,
	val title: String,
	val items: List<Manga>,
	val sortOrder: SortOrder? = null,
)

@Immutable
data class ExploreUiState(
	val sources: List<ExploreSourceTile> = emptyList(),
	val allEnabledSources: List<ExploreSourceTile> = emptyList(),
	val allEnabledCount: Int = 0,
	val activeFilter: ExploreSourceFilter = ExploreSourceFilter.ALL,
	val sourceQuery: String = "",
	val sortOrder: SourcesSortOrder = SourcesSortOrder.ALPHABETIC,
	val languages: List<SourceLanguage> = emptyList(),
	val isLoadingSources: Boolean = false,
	val hasExtensionUpdates: Boolean = false,
	val isFindingRandom: Boolean = false,
	// Neko-style single Home Source Hub
	val homeSource: ExploreSourceTile? = null,
	val isHomeHubLoading: Boolean = false,
	val homeHubError: String? = null,
	val homeCarousels: List<HomeHubCarousel> = emptyList(),
	val visibilityFilter: LibraryVisibilityFilter = LibraryVisibilityFilter.ALL,
	val libraryMangaIds: Set<Long> = emptySet(),
	val libraryMangaUrls: Set<String> = emptySet(),
)

private data class HomeHubRawState(
	val sourceName: String? = null,
	val isLoading: Boolean = false,
	val error: String? = null,
	val carousels: List<HomeHubCarousel> = emptyList(),
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val sourcesRepository: MangaSourcesRepository,
	private val exploreRepository: ExploreRepository,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
	private val favouritesRepository: FavouritesRepository,
	private val suggestionRepository: SuggestionRepository,
	private val extensionStoreManager: ExtensionStoreManager,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val activeFilter = MutableStateFlow(ExploreSourceFilter.ALL)
	private val sourceQuery = MutableStateFlow("")
	private val isFindingRandom = MutableStateFlow(false)
	private val languageRefreshTrigger = MutableStateFlow(0)
	private val visibilityFilterFlow = MutableStateFlow(LibraryVisibilityFilter.ALL)
	private val homeHubRawFlow = MutableStateFlow(HomeHubRawState())

	private var loadedHomeSourceName: String? = null
		@Volatile get
		@Volatile set
	private var homeHubJob: Job? = null

	private val sourcesMetadataFlow = combine(
		sourcesRepository.observeEnabledSources(),
		sourcesRepository.observeMihonLoadingState(),
		extensionStoreManager.hasUpdates,
		settings.observeAsFlow(AppSettings.KEY_SOURCES_ORDER) { sourcesSortOrder },
		combine(
			settings.observeChanges()
				.filter {
					it == null ||
						it == AppSettings.KEY_HIDDEN_SOURCE_LANGUAGES ||
						it == AppSettings.KEY_MIHON_HIDDEN_PACKAGES ||
						it == AppSettings.KEY_LN_HIDDEN_PLUGINS ||
						it == AppSettings.KEY_EXPLORE_HOME_SOURCE
				}
				.onStart { emit(null) },
			languageRefreshTrigger,
		) { _, _ -> sourcesRepository.getSourceLanguages() },
	) { enabledSources, isLoading, hasUpdates, sortOrder, languages ->
		SourcesMetadataSnapshot(
			enabledSources = enabledSources,
			isLoading = isLoading,
			hasUpdates = hasUpdates,
			sortOrder = sortOrder,
			languages = languages,
			preferredHomeSourceName = settings.exploreHomeSource,
		)
	}

	private val libraryMembershipFlow = favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), 2000)

	val uiState: StateFlow<ExploreUiState> = combine(
		sourcesMetadataFlow,
		combine(
			activeFilter,
			sourceQuery,
			isFindingRandom,
			visibilityFilterFlow,
		) { f, q, r, v -> QuadControls(f, q, r, v) },
		homeHubRawFlow,
		libraryMembershipFlow,
		suggestionRepository.observeRandomList(12),
	) { meta, controls, hubRaw, favManga, storedSuggestions ->
		val trimmed = controls.query.trim()
		val allTiles = meta.enabledSources.map { info ->
			ExploreSourceTile(
				sourceInfo = info,
				sourceName = info.name,
				title = info.getTitle(context),
				summary = info.getSummary(context),
				homeUrl = info.homeUrl,
				isNovel = info.isNovelSource,
				isNsfw = info.isNsfw(),
				isPinned = info.isPinned,
			)
		}

		val filteredTiles = allTiles
			.filter { tile ->
				when (controls.filter) {
					ExploreSourceFilter.ALL -> true
					ExploreSourceFilter.PINNED -> tile.isPinned
					ExploreSourceFilter.MANGA -> !tile.isNovel
					ExploreSourceFilter.NOVELS -> tile.isNovel
				}
			}
			.filter { tile ->
				trimmed.isEmpty() ||
					tile.title.contains(trimmed, ignoreCase = true) ||
					tile.summary?.contains(trimmed, ignoreCase = true) == true
			}

		val resolvedHome = allTiles.firstOrNull { it.sourceName == meta.preferredHomeSourceName }
			?: allTiles.firstOrNull { it.isPinned && !it.isNovel }
			?: allTiles.firstOrNull { !it.isNovel }
			?: allTiles.firstOrNull()

		if (resolvedHome != null && resolvedHome.sourceName != loadedHomeSourceName && !hubRaw.isLoading) {
			triggerHomeHubLoad(resolvedHome)
		}

		val libIds = favManga.mapTo(HashSet(favManga.size)) { it.id }
		val libUrls = favManga.mapTo(HashSet(favManga.size)) { it.url }

		fun Manga.inLibrary(): Boolean = id in libIds || url in libUrls

		val rawCarousels = buildList {
			addAll(hubRaw.carousels)
			if (storedSuggestions.isNotEmpty() && none { it.id == "suggested" }) {
				add(HomeHubCarousel(id = "suggested", title = "Suggested For You", items = storedSuggestions))
			}
		}.mapNotNull { carousel ->
			val filteredItems = when (controls.visibility) {
				LibraryVisibilityFilter.ALL -> carousel.items
				LibraryVisibilityFilter.NOT_IN_LIBRARY -> carousel.items.filterNot { it.inLibrary() }
				LibraryVisibilityFilter.IN_LIBRARY -> carousel.items.filter { it.inLibrary() }
			}
			if (filteredItems.isEmpty() && carousel.items.isNotEmpty() && controls.visibility != LibraryVisibilityFilter.ALL) {
				carousel.copy(items = emptyList())
			} else if (filteredItems.isEmpty()) {
				null
			} else {
				carousel.copy(items = filteredItems)
			}
		}

		ExploreUiState(
			sources = filteredTiles,
			allEnabledSources = allTiles,
			allEnabledCount = meta.enabledSources.size,
			activeFilter = controls.filter,
			sourceQuery = controls.query,
			sortOrder = meta.sortOrder,
			languages = meta.languages,
			isLoadingSources = meta.isLoading,
			hasExtensionUpdates = meta.hasUpdates,
			isFindingRandom = controls.findingRandom,
			homeSource = resolvedHome,
			isHomeHubLoading = hubRaw.isLoading,
			homeHubError = hubRaw.error,
			homeCarousels = rawCarousels,
			visibilityFilter = controls.visibility,
			libraryMangaIds = libIds,
			libraryMangaUrls = libUrls,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = ExploreUiState(),
		)

	fun setHomeSource(sourceName: String) {
		settings.exploreHomeSource = sourceName
		loadedHomeSourceName = null
		val tile = uiState.value.allEnabledSources.firstOrNull { it.sourceName == sourceName }
		if (tile != null) {
			triggerHomeHubLoad(tile)
		}
	}

	fun refreshHomeHub() {
		val current = uiState.value.homeSource ?: return
		loadedHomeSourceName = null
		triggerHomeHubLoad(current)
	}

	fun setVisibilityFilter(filter: LibraryVisibilityFilter) {
		visibilityFilterFlow.value = filter
	}

	private fun triggerHomeHubLoad(tile: ExploreSourceTile) {
		loadedHomeSourceName = tile.sourceName
		homeHubJob?.cancel()
		homeHubJob = viewModelScope.launch(Dispatchers.IO) {
			homeHubRawFlow.value = HomeHubRawState(sourceName = tile.sourceName, isLoading = true)
			val result = runCatchingCancellable {
				val repo = mangaRepositoryFactoryProvider.get().create(tile.sourceInfo.mangaSource)
				val sortOrders = repo.sortOrders
				coroutineScope {
					val popularJob = if (SortOrder.POPULARITY in sortOrders) {
						async {
							runCatchingCancellable {
								repo.getList(0, SortOrder.POPULARITY, MangaListFilter()).take(15)
							}.getOrDefault(emptyList())
						}
					} else null

					val updatedJob = if (SortOrder.UPDATED in sortOrders) {
						async {
							runCatchingCancellable {
								repo.getList(0, SortOrder.UPDATED, MangaListFilter()).take(15)
							}.getOrDefault(emptyList())
						}
					} else null

					val secondaryOrder = when {
						SortOrder.RATING in sortOrders -> SortOrder.RATING
						SortOrder.NEWEST in sortOrders -> SortOrder.NEWEST
						else -> null
					}
					val secondaryJob = secondaryOrder?.let { ord ->
						async {
							runCatchingCancellable {
								repo.getList(0, ord, MangaListFilter()).take(15)
							}.getOrDefault(emptyList())
						}
					}

					val genreJob = async {
						loadGenreRecommendation(repo)
					}

					val list = ArrayList<HomeHubCarousel>(4)
					popularJob?.await()?.takeIf { it.isNotEmpty() }?.let { items ->
						list += HomeHubCarousel("popular", "Popular on ${tile.title}", items, SortOrder.POPULARITY)
					}
					updatedJob?.await()?.takeIf { it.isNotEmpty() }?.let { items ->
						list += HomeHubCarousel("updated", "Latest Updates", items, SortOrder.UPDATED)
					}
					secondaryJob?.await()?.takeIf { it.isNotEmpty() }?.let { items ->
						val label = if (secondaryOrder == SortOrder.RATING) "Top Rated" else "Newly Added"
						list += HomeHubCarousel("secondary", label, items, secondaryOrder)
					}
					genreJob.await()?.let { (tagTitle, items) ->
						if (items.isNotEmpty()) {
							list += HomeHubCarousel(
								id = "suggested",
								title = "Suggested ($tagTitle)",
								items = items,
								sortOrder = SortOrder.POPULARITY,
							)
						}
					}
					list
				}
			}
			if (loadedHomeSourceName != tile.sourceName) return@launch
			result.onSuccess { carousels ->
				homeHubRawFlow.value = HomeHubRawState(
					sourceName = tile.sourceName,
					isLoading = false,
					error = if (carousels.isEmpty()) "No feeds returned by ${tile.title}" else null,
					carousels = carousels,
				)
			}.onFailure { err ->
				homeHubRawFlow.value = HomeHubRawState(
					sourceName = tile.sourceName,
					isLoading = false,
					error = err.localizedMessage ?: "Failed to load ${tile.title}",
				)
			}
		}
	}

	private suspend fun loadGenreRecommendation(repo: MangaRepository): Pair<String, List<Manga>>? {
		val topGenreTitle = runCatchingCancellable {
			favouritesRepository.getAllManga()
				.asSequence()
				.flatMap { it.tags.asSequence() }
				.groupingBy { it.title }
				.eachCount()
				.maxByOrNull { it.value }
				?.key
		}.getOrNull() ?: return null

		val options = runCatchingCancellable { repo.getFilterOptions() }.getOrNull() ?: return null
		val matchingTag = options.availableTags.firstOrNull {
			it.title.equals(topGenreTitle, ignoreCase = true)
		} ?: return null

		val order = when {
			SortOrder.POPULARITY in repo.sortOrders -> SortOrder.POPULARITY
			else -> repo.defaultSortOrder
		}
		val items = runCatchingCancellable {
			repo.getList(0, order, MangaListFilter(tags = setOf(matchingTag))).take(12)
		}.getOrDefault(emptyList())
		return if (items.isNotEmpty()) matchingTag.title to items else null
	}

	fun setFilter(filter: ExploreSourceFilter) {
		activeFilter.value = filter
	}

	fun setSourceQuery(query: String) {
		sourceQuery.value = query
	}

	fun trackSourceOpen(source: MangaSource) {
		viewModelScope.launch(Dispatchers.IO) {
			sourcesRepository.trackUsage(source)
		}
	}

	fun togglePin(tile: ExploreSourceTile) {
		viewModelScope.launch(Dispatchers.IO) {
			val targetPinned = !tile.isPinned
			val handle = sourcesRepository.setIsPinned(listOf(tile.sourceInfo.mangaSource), targetPinned)
			toastManager.show(
				message = if (targetPinned) "Pinned ${tile.title}" else "Unpinned ${tile.title}",
				actionLabel = "Undo",
				onAction = { handle.reverseAsync() },
			)
		}
	}

	fun hideSource(tile: ExploreSourceTile) {
		viewModelScope.launch(Dispatchers.IO) {
			val handle = sourcesRepository.setSourcesHidden(listOf(tile.sourceInfo.mangaSource), true)
			languageRefreshTrigger.value++
			toastManager.show(
				message = "Hidden ${tile.title}",
				actionLabel = "Undo",
				onAction = {
					handle.reverseAsync()
					languageRefreshTrigger.value++
				},
			)
		}
	}

	fun setSortOrder(order: SourcesSortOrder) {
		settings.sourcesSortOrder = order
		toastManager.show(
			when (order) {
				SourcesSortOrder.ALPHABETIC -> "Sources sorted alphabetically"
				SourcesSortOrder.MANUAL -> "Sources set to manual order"
				SourcesSortOrder.LAST_USED -> "Sources sorted by last used"
			},
		)
	}

	fun toggleLanguage(code: String) {
		val current = settings.hiddenSourceLanguages
		val updated = if (code in current) current - code else current + code
		sourcesRepository.setHiddenLanguages(updated)
		languageRefreshTrigger.value++
	}

	fun reloadSources() {
		viewModelScope.launch(Dispatchers.IO) {
			sourcesRepository.reloadMihonSources()
			languageRefreshTrigger.value++
		}
	}

	fun findRandomManga(onFound: (Manga) -> Unit) {
		if (isFindingRandom.value) return
		viewModelScope.launch(Dispatchers.IO) {
			isFindingRandom.value = true
			val result = runCatching {
				exploreRepository.findRandomManga(tagsLimit = 5)
			}
			isFindingRandom.value = false
			result.fold(
				onSuccess = { manga ->
					MangaTransferCache.put(manga)
					launch(Dispatchers.Main) {
						onFound(manga)
					}
				},
				onFailure = { error ->
					toastManager.show(error.localizedMessage ?: "No random title found across enabled sources")
				},
			)
		}
	}

	private data class SourcesMetadataSnapshot(
		val enabledSources: List<MangaSourceInfo>,
		val isLoading: Boolean,
		val hasUpdates: Boolean,
		val sortOrder: SourcesSortOrder,
		val languages: List<SourceLanguage>,
		val preferredHomeSourceName: String?,
	)

	private data class QuadControls(
		val filter: ExploreSourceFilter,
		val query: String,
		val findingRandom: Boolean,
		val visibility: LibraryVisibilityFilter,
	)
}
