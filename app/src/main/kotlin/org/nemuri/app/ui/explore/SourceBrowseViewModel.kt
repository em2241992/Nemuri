package org.nemuri.app.ui.explore

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.MangaSource as ResolveMangaSource
import org.koitharu.kotatsu.core.model.MissingMangaSource
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.homeUrl
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.ResolvedSource
import org.koitharu.kotatsu.explore.data.SourceLanguageVariant
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.mihon.MihonFilterHost
import org.koitharu.kotatsu.mihon.MihonFilterMapper
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

@Immutable
sealed interface DynamicFilterNode {
	val path: String
	val name: String

	@Immutable
	data class Header(override val path: String, override val name: String) : DynamicFilterNode

	@Immutable
	data class Separator(override val path: String, override val name: String) : DynamicFilterNode

	@Immutable
	data class CheckBox(
		override val path: String,
		override val name: String,
		val checked: Boolean,
	) : DynamicFilterNode

	@Immutable
	data class TriState(
		override val path: String,
		override val name: String,
		val state: Int,
	) : DynamicFilterNode

	@Immutable
	data class Select(
		override val path: String,
		override val name: String,
		val options: List<String>,
		val selectedIndex: Int,
	) : DynamicFilterNode

	@Immutable
	data class Sort(
		override val path: String,
		override val name: String,
		val options: List<String>,
		val selectedIndex: Int?,
		val ascending: Boolean,
	) : DynamicFilterNode

	@Immutable
	data class Text(
		override val path: String,
		override val name: String,
		val value: String,
	) : DynamicFilterNode

	@Immutable
	data class Group(
		override val path: String,
		override val name: String,
		val children: List<DynamicFilterNode>,
	) : DynamicFilterNode
}

@Immutable
data class StaticFilterSnapshot(
	val availableTags: List<MangaTag> = emptyList(),
	val availableStates: List<MangaState> = emptyList(),
	val availableContentRatings: List<ContentRating> = emptyList(),
	val availableContentTypes: List<ContentType> = emptyList(),
	val selectedTags: Set<MangaTag> = emptySet(),
	val excludedTags: Set<MangaTag> = emptySet(),
	val selectedStates: Set<MangaState> = emptySet(),
	val selectedRatings: Set<ContentRating> = emptySet(),
	val selectedContentTypes: Set<ContentType> = emptySet(),
	val isMultipleTagsSupported: Boolean = true,
	val isTagsExclusionSupported: Boolean = false,
)

@Immutable
data class SourceBrowseUiState(
	val sourceTitle: String = "",
	val languageSubtitle: String? = null,
	val languageVariants: List<SourceLanguageVariant> = emptyList(),
	val homeUrl: String? = null,
	val resolvedSource: MangaSource = UnknownMangaSource,
	val query: String = "",
	val availableSortOrders: List<SortOrder> = emptyList(),
	val selectedSortOrder: SortOrder = SortOrder.POPULARITY,
	val isSearchSupported: Boolean = false,
	val isSearchWithFiltersSupported: Boolean = false,
	val supportsDynamicFilters: Boolean = false,
	val dynamicFilters: List<DynamicFilterNode> = emptyList(),
	val staticFilters: StaticFilterSnapshot = StaticFilterSnapshot(),
	val activeFilterCount: Int = 0,
	val items: List<Manga> = emptyList(),
	val favouriteIds: Set<Long> = emptySet(),
	val isInitialLoading: Boolean = true,
	val isLoadingMore: Boolean = false,
	val hasMorePages: Boolean = false,
	val errorMessage: String? = null,
	val pendingCloudFlare: CloudFlareProtectedException? = null,
	val pendingInteractive: InteractiveActionRequiredException? = null,
)

@HiltViewModel
class SourceBrowseViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val sourcesRepository: MangaSourcesRepository,
	private val favouritesRepository: FavouritesRepository,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val _uiState = MutableStateFlow(SourceBrowseUiState())
	val uiState: StateFlow<SourceBrowseUiState> = _uiState.asStateFlow()

	private var boundKey: String? = null
	private var repository: MangaRepository? = null
	private var activeSource: MangaSource = UnknownMangaSource
	private var inAppListingsExclusive = false
	private var defaultDynamicFilters = FilterList(emptyList())
	private var workingDynamicFilters = FilterList(emptyList())
	private var appliedTags: Set<MangaTag> = emptySet()

	private var loadJob: Job? = null
	private val seenIds = HashSet<Long>()

	init {
		viewModelScope.launch {
			favouritesRepository.observeAll(ListSortOrder.ALPHABETIC, emptySet(), Int.MAX_VALUE)
				.map { list -> list.mapTo(HashSet()) { it.id } }
				.flowOn(Dispatchers.IO)
				.catch { }
				.collect { ids ->
					_uiState.update { it.copy(favouriteIds = ids) }
				}
		}
	}

	fun bind(sourceName: String, initialQuery: String?) {
		val key = "$sourceName|${initialQuery.orEmpty()}"
		if (boundKey == key && repository != null) {
			return
		}
		boundKey = key
		loadJob?.cancel()
		seenIds.clear()

		viewModelScope.launch(Dispatchers.IO) {
			_uiState.update {
				SourceBrowseUiState(
					sourceTitle = sourceName,
					query = initialQuery.orEmpty(),
					isInitialLoading = true,
				)
			}

			val raw = ResolveMangaSource(sourceName)
			if (raw is MissingMangaSource && sourceName.startsWith("MIHON_")) {
				sourcesRepository.ensureMihonSourcesLoaded()
			}
			val resolved = sourcesRepository.resolveActiveSource(ResolveMangaSource(sourceName))
			applyResolvedSource(resolved, startQuery = initialQuery?.trim().orEmpty())
			loadPage(reset = true)
		}
	}

	fun selectSourceLanguage(languageCode: String) {
		val currentVariants = _uiState.value.languageVariants
		if (currentVariants.any { it.isActive && it.languageCode.equals(languageCode, ignoreCase = true) }) {
			return
		}
		loadJob?.cancel()
		seenIds.clear()
		viewModelScope.launch(Dispatchers.IO) {
			val resolved = sourcesRepository.setActiveSourceLanguage(activeSource, languageCode)
			applyResolvedSource(resolved, startQuery = _uiState.value.query)
			val langLabel = resolved.languageSubtitle ?: languageCode.uppercase()
			toastManager.show("Switched ${activeSource.getTitle(context)} to $langLabel")
			loadPage(reset = true)
		}
	}

	private suspend fun applyResolvedSource(
		resolved: ResolvedSource,
		startQuery: String,
	) {
		activeSource = resolved.source
		sourcesRepository.trackUsage(activeSource)

		val repo = mangaRepositoryFactory.create(activeSource)
		repository = repo

		val sortOrders = repo.sortOrders.toList().ifEmpty { listOf(SortOrder.POPULARITY) }
		val initialSort = if (startQuery.isNotEmpty() && SortOrder.RELEVANCE in sortOrders) {
			SortOrder.RELEVANCE
		} else if (repo.defaultSortOrder in sortOrders) {
			repo.defaultSortOrder
		} else {
			sortOrders.first()
		}

		val filterHost = repo as? MihonFilterHost
		val isDynamic = filterHost?.supportsDynamicFilters == true
		inAppListingsExclusive = filterHost?.inAppListingsExclusive == true

		val caps = repo.filterCapabilities
		var staticSnapshot = StaticFilterSnapshot(
			isMultipleTagsSupported = caps.isMultipleTagsSupported,
			isTagsExclusionSupported = caps.isTagsExclusionSupported,
		)

		if (isDynamic && filterHost != null) {
			defaultDynamicFilters = runCatchingCancellable { filterHost.loadDefaultFilterList() }
				.getOrDefault(FilterList(emptyList()))
			workingDynamicFilters = runCatchingCancellable { filterHost.loadDefaultFilterList() }
				.getOrDefault(FilterList(emptyList()))
			appliedTags = emptySet()
		} else {
			defaultDynamicFilters = FilterList(emptyList())
			workingDynamicFilters = FilterList(emptyList())
			appliedTags = emptySet()
			val opts = runCatchingCancellable { repo.getFilterOptions() }.getOrNull()
			if (opts != null) {
				staticSnapshot = staticSnapshot.copy(
					availableTags = opts.availableTags.sortedBy { it.title.lowercase() },
					availableStates = opts.availableStates.toList(),
					availableContentRatings = opts.availableContentRating.toList(),
					availableContentTypes = opts.availableContentTypes.toList(),
				)
			}
		}

		val variants = sourcesRepository.getSourceLanguageVariants(activeSource)
		_uiState.update {
			it.copy(
				sourceTitle = activeSource.getTitle(context),
				languageSubtitle = resolved.languageSubtitle,
				languageVariants = variants,
				homeUrl = activeSource.homeUrl,
				resolvedSource = activeSource,
				query = startQuery,
				availableSortOrders = sortOrders,
				selectedSortOrder = initialSort,
				isSearchSupported = caps.isSearchSupported,
				isSearchWithFiltersSupported = caps.isSearchWithFiltersSupported,
				supportsDynamicFilters = isDynamic,
				dynamicFilters = if (isDynamic) snapshotDynamicFilters(workingDynamicFilters) else emptyList(),
				staticFilters = staticSnapshot,
				activeFilterCount = 0,
			)
		}
	}

	fun setQuery(query: String) {
		_uiState.update { it.copy(query = query) }
	}

	fun submitSearch() {
		loadPage(reset = true)
	}

	fun clearSearch() {
		_uiState.update { it.copy(query = "") }
		loadPage(reset = true)
	}

	fun selectSortOrder(order: SortOrder) {
		val current = _uiState.value
		if (current.selectedSortOrder == order) return
		if (inAppListingsExclusive && order != SortOrder.RELEVANCE) {
			resetFiltersInternal()
		}
		_uiState.update { it.copy(selectedSortOrder = order) }
		loadPage(reset = true)
	}

	fun updateDynamicCheckBox(path: String, checked: Boolean) {
		val target = findFilterByPath(workingDynamicFilters, path) as? Filter.CheckBox ?: return
		target.state = checked
		publishDynamicFilterSnapshot()
	}

	fun cycleDynamicTriState(path: String) {
		val target = findFilterByPath(workingDynamicFilters, path) as? Filter.TriState ?: return
		target.state = when (target.state) {
			Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
			Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
			else -> Filter.TriState.STATE_IGNORE
		}
		publishDynamicFilterSnapshot()
	}

	fun updateDynamicSelect(path: String, index: Int) {
		val target = findFilterByPath(workingDynamicFilters, path) as? Filter.Select<*> ?: return
		if (index in target.values.indices) {
			target.state = index
			publishDynamicFilterSnapshot()
		}
	}

	fun updateDynamicSort(path: String, index: Int, defaultAscending: Boolean = false) {
		val target = findFilterByPath(workingDynamicFilters, path) as? Filter.Sort ?: return
		val current = target.state
		val next = if (current?.index == index) {
			Filter.Sort.Selection(index, !current.ascending)
		} else {
			Filter.Sort.Selection(index, defaultAscending)
		}
		target.state = next
		publishDynamicFilterSnapshot()
	}

	fun updateDynamicText(path: String, text: String) {
		val target = findFilterByPath(workingDynamicFilters, path) as? Filter.Text ?: return
		target.state = text
		publishDynamicFilterSnapshot()
	}

	fun toggleStaticTag(tag: MangaTag) {
		_uiState.update { state ->
			val sf = state.staticFilters
			val (nextSelected, nextExcluded) = if (!sf.isMultipleTagsSupported) {
				val next = if (tag in sf.selectedTags) emptySet() else setOf(tag)
				next to emptySet()
			} else if (sf.isTagsExclusionSupported) {
				when (tag) {
					in sf.selectedTags -> (sf.selectedTags - tag) to (sf.excludedTags + tag)
					in sf.excludedTags -> sf.selectedTags to (sf.excludedTags - tag)
					else -> (sf.selectedTags + tag) to sf.excludedTags
				}
			} else {
				val next = if (tag in sf.selectedTags) sf.selectedTags - tag else sf.selectedTags + tag
				next to sf.excludedTags
			}
			state.copy(staticFilters = sf.copy(selectedTags = nextSelected, excludedTags = nextExcluded))
		}
	}

	fun toggleStaticState(mangaState: MangaState) {
		_uiState.update { state ->
			val sf = state.staticFilters
			val next = if (mangaState in sf.selectedStates) sf.selectedStates - mangaState else sf.selectedStates + mangaState
			state.copy(staticFilters = sf.copy(selectedStates = next))
		}
	}

	fun toggleStaticRating(rating: ContentRating) {
		_uiState.update { state ->
			val sf = state.staticFilters
			val next = if (rating in sf.selectedRatings) sf.selectedRatings - rating else sf.selectedRatings + rating
			state.copy(staticFilters = sf.copy(selectedRatings = next))
		}
	}

	fun toggleStaticContentType(type: ContentType) {
		_uiState.update { state ->
			val sf = state.staticFilters
			val next = if (type in sf.selectedContentTypes) sf.selectedContentTypes - type else sf.selectedContentTypes + type
			state.copy(staticFilters = sf.copy(selectedContentTypes = next))
		}
	}

	fun applyFilters() {
		val current = _uiState.value
		if (current.supportsDynamicFilters) {
			appliedTags = MihonFilterMapper.encode(workingDynamicFilters, defaultDynamicFilters, activeSource)
			_uiState.update { it.copy(activeFilterCount = appliedTags.size) }
		} else {
			val sf = current.staticFilters
			appliedTags = sf.selectedTags
			val count = sf.selectedTags.size +
				sf.excludedTags.size +
				sf.selectedStates.size +
				sf.selectedRatings.size +
				sf.selectedContentTypes.size
			_uiState.update { it.copy(activeFilterCount = count) }
		}
		loadPage(reset = true)
	}

	fun resetFilters() {
		viewModelScope.launch(Dispatchers.IO) {
			if (_uiState.value.supportsDynamicFilters) {
				val host = repository as? MihonFilterHost
				if (host != null) {
					workingDynamicFilters = runCatchingCancellable { host.loadDefaultFilterList() }
						.getOrDefault(defaultDynamicFilters)
				}
			}
			resetFiltersInternal()
			loadPage(reset = true)
		}
	}

	private fun resetFiltersInternal() {
		appliedTags = emptySet()
		_uiState.update { state ->
			state.copy(
				dynamicFilters = if (state.supportsDynamicFilters) snapshotDynamicFilters(workingDynamicFilters) else emptyList(),
				staticFilters = state.staticFilters.copy(
					selectedTags = emptySet(),
					excludedTags = emptySet(),
					selectedStates = emptySet(),
					selectedRatings = emptySet(),
					selectedContentTypes = emptySet(),
				),
				activeFilterCount = 0,
			)
		}
	}

	fun loadNextPage() {
		val state = _uiState.value
		if (state.isInitialLoading || state.isLoadingMore || !state.hasMorePages) return
		loadPage(reset = false)
	}

	fun retry() {
		loadPage(reset = _uiState.value.items.isEmpty())
	}

	fun onChallengeResolved(success: Boolean) {
		_uiState.update {
			it.copy(
				pendingCloudFlare = null,
				pendingInteractive = null,
			)
		}
		if (success) {
			toastManager.show("Verification completed, retrying…")
			retry()
		}
	}

	fun launchCloudFlareSolver() {
		// Handled by screen via current pendingCloudFlare or re-trigger on user click.
	}

	private fun loadPage(reset: Boolean) {
		val repo = repository ?: return
		loadJob?.cancel()
		loadJob = viewModelScope.launch(Dispatchers.IO) {
			val offset = if (reset) 0 else _uiState.value.items.size
			if (reset) {
				seenIds.clear()
				_uiState.update {
					it.copy(
						isInitialLoading = true,
						isLoadingMore = false,
						errorMessage = null,
						pendingCloudFlare = null,
						pendingInteractive = null,
					)
				}
			} else {
				_uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
			}

			val state = _uiState.value
			val filter = buildListFilter(state)
			val result = runCatchingCancellable {
				repo.getList(offset = offset, order = state.selectedSortOrder, filter = filter)
			}

			result.fold(
				onSuccess = { fetched ->
					val uniqueNew = fetched.filter { seenIds.add(it.id) }
					val combined = if (reset) uniqueNew else _uiState.value.items + uniqueNew
					_uiState.update {
						it.copy(
							items = combined,
							isInitialLoading = false,
							isLoadingMore = false,
							hasMorePages = fetched.isNotEmpty() && uniqueNew.isNotEmpty(),
							errorMessage = null,
						)
					}
				},
				onFailure = { error ->
					val cfException = error as? CloudFlareProtectedException
					val interactiveException = error as? InteractiveActionRequiredException
					val message = error.getDisplayMessage(context.resources)
					_uiState.update {
						it.copy(
							isInitialLoading = false,
							isLoadingMore = false,
							errorMessage = message,
							pendingCloudFlare = cfException,
							pendingInteractive = interactiveException,
						)
					}
				},
			)
		}
	}

	private fun buildListFilter(state: SourceBrowseUiState): MangaListFilter {
		val queryText = state.query.trim().ifBlank { null }
		val sf = state.staticFilters
		return if (state.supportsDynamicFilters) {
			MangaListFilter(
				query = queryText,
				tags = appliedTags,
			)
		} else {
			MangaListFilter(
				query = queryText,
				tags = sf.selectedTags,
				tagsExclude = sf.excludedTags,
				states = sf.selectedStates,
				contentRating = sf.selectedRatings,
				types = sf.selectedContentTypes,
			)
		}
	}

	private fun publishDynamicFilterSnapshot() {
		_uiState.update {
			it.copy(dynamicFilters = snapshotDynamicFilters(workingDynamicFilters))
		}
	}

	private fun snapshotDynamicFilters(filters: FilterList): List<DynamicFilterNode> =
		snapshotFilters(filters.toList(), prefix = "")

	private fun snapshotFilters(filters: List<Filter<*>>, prefix: String): List<DynamicFilterNode> {
		return filters.mapIndexedNotNull { index, filter ->
			val path = if (prefix.isEmpty()) index.toString() else "$prefix.$index"
			when (filter) {
				is Filter.Header -> DynamicFilterNode.Header(path, filter.name)
				is Filter.Separator -> DynamicFilterNode.Separator(path, filter.name)
				is Filter.CheckBox -> DynamicFilterNode.CheckBox(path, filter.name, filter.state)
				is Filter.TriState -> DynamicFilterNode.TriState(path, filter.name, filter.state)
				is Filter.Select<*> -> DynamicFilterNode.Select(
					path = path,
					name = filter.name,
					options = filter.values.map { it.toString() },
					selectedIndex = filter.state,
				)
				is Filter.Sort -> DynamicFilterNode.Sort(
					path = path,
					name = filter.name,
					options = filter.values.toList(),
					selectedIndex = filter.state?.index,
					ascending = filter.state?.ascending == true,
				)
				is Filter.Text -> DynamicFilterNode.Text(path, filter.name, filter.state)
				is Filter.Group<*> -> DynamicFilterNode.Group(
					path = path,
					name = filter.name,
					children = snapshotFilters(filter.state.filterIsInstance<Filter<*>>(), path),
				)
			}
		}
	}

	private fun findFilterByPath(filters: FilterList, path: String): Filter<*>? {
		val segments = path.split('.').mapNotNull { it.toIntOrNull() }
		if (segments.isEmpty()) return null
		var currentList: List<Filter<*>> = filters.toList()
		for (i in segments.indices) {
			val filter = currentList.getOrNull(segments[i]) ?: return null
			if (i == segments.lastIndex) return filter
			currentList = (filter as? Filter.Group<*>)?.state?.filterIsInstance<Filter<*>>() ?: return null
		}
		return null
	}
}
