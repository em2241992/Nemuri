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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.MangaSearchRepository
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

enum class SearchScopeFilter(val label: String) {
	ALL("All Sources"),
	PINNED("Pinned"),
	MANGA("Manga"),
	NOVELS("Novels"),
}

@Immutable
data class GlobalSearchSourceSection(
	val source: MangaSource,
	val sourceName: String,
	val sourceTitle: String,
	val isNovel: Boolean,
	val items: List<Manga>,
)

@Immutable
data class GlobalSearchSourceError(
	val source: MangaSource,
	val sourceName: String,
	val sourceTitle: String,
	val message: String,
	val cloudFlareException: CloudFlareProtectedException? = null,
	val interactiveException: InteractiveActionRequiredException? = null,
)

@Immutable
data class GlobalSearchUiState(
	val query: String = "",
	val activeQuery: String = "",
	val searchKind: SearchKind = SearchKind.SIMPLE,
	val scopeFilter: SearchScopeFilter = SearchScopeFilter.ALL,
	val recentQueries: List<String> = emptyList(),
	val sections: List<GlobalSearchSourceSection> = emptyList(),
	val sourceErrors: List<GlobalSearchSourceError> = emptyList(),
	val favouriteIds: Set<Long> = emptySet(),
	val isSearching: Boolean = false,
	val completedCount: Int = 0,
	val totalCount: Int = 0,
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val sourcesRepository: MangaSourcesRepository,
	private val searchRepository: MangaSearchRepository,
	private val searchV2HelperFactory: SearchV2Helper.Factory,
	private val favouritesRepository: FavouritesRepository,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val _uiState = MutableStateFlow(GlobalSearchUiState())
	val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

	private var initializedWith: String? = null
	private var searchJob: Job? = null
	private val stateMutex = Mutex()

	init {
		loadRecentQueries()
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

	fun bind(initialQuery: String) {
		if (initializedWith == initialQuery) return
		initializedWith = initialQuery
		if (initialQuery.isNotBlank()) {
			_uiState.update { it.copy(query = initialQuery) }
			submitSearch(initialQuery)
		}
	}

	fun setQuery(query: String) {
		_uiState.update { it.copy(query = query) }
	}

	fun setSearchKind(kind: SearchKind) {
		if (_uiState.value.searchKind == kind) return
		_uiState.update { it.copy(searchKind = kind) }
		val current = _uiState.value.activeQuery
		if (current.isNotBlank()) {
			submitSearch(current)
		}
	}

	fun setScopeFilter(scope: SearchScopeFilter) {
		if (_uiState.value.scopeFilter == scope) return
		_uiState.update { it.copy(scopeFilter = scope) }
		val current = _uiState.value.activeQuery
		if (current.isNotBlank()) {
			submitSearch(current)
		}
	}

	fun deleteRecentQuery(query: String) {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching { searchRepository.deleteSearchQuery(query) }
			loadRecentQueries()
		}
	}

	fun clearRecentQueries() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching { searchRepository.clearSearchHistory() }
			_uiState.update { it.copy(recentQueries = emptyList()) }
			toastManager.show("Search history cleared")
		}
	}

	fun submitSearch(rawQuery: String = _uiState.value.query) {
		val trimmed = rawQuery.trim()
		if (trimmed.isEmpty()) {
			searchJob?.cancel()
			_uiState.update {
				it.copy(
					query = "",
					activeQuery = "",
					sections = emptyList(),
					sourceErrors = emptyList(),
					isSearching = false,
					completedCount = 0,
					totalCount = 0,
				)
			}
			return
		}

		searchJob?.cancel()
		searchJob = viewModelScope.launch(Dispatchers.IO) {
			runCatching { searchRepository.saveSearchQuery(trimmed) }
			loadRecentQueries()

			sourcesRepository.ensureMihonSourcesLoaded()
			val pinned = sourcesRepository.getPinnedSources()
			val scope = _uiState.value.scopeFilter
			val kind = _uiState.value.searchKind

			val candidates = sourcesRepository.getEnabledSources()
				.filter { source ->
					when (scope) {
						SearchScopeFilter.ALL -> true
						SearchScopeFilter.PINNED -> source in pinned
						SearchScopeFilter.MANGA -> !source.isNovelSource
						SearchScopeFilter.NOVELS -> source.isNovelSource
					}
				}
				.sortedByDescending { it in pinned }

			_uiState.update {
				it.copy(
					query = trimmed,
					activeQuery = trimmed,
					sections = emptyList(),
					sourceErrors = emptyList(),
					isSearching = candidates.isNotEmpty(),
					completedCount = 0,
					totalCount = candidates.size,
				)
			}

			if (candidates.isEmpty()) {
				return@launch
			}

			val semaphore = Semaphore(MAX_CONCURRENT_SOURCES)
			supervisorScope {
				candidates.map { source ->
					async {
						semaphore.withPermit {
							queryOneSource(source, trimmed, kind)
						}
					}
				}.awaitAll()
			}

			_uiState.update { it.copy(isSearching = false) }
		}
	}

	fun retrySource(source: MangaSource) {
		val activeQuery = _uiState.value.activeQuery
		val kind = _uiState.value.searchKind
		if (activeQuery.isBlank()) return

		viewModelScope.launch(Dispatchers.IO) {
			stateMutex.withLock {
				_uiState.update { state ->
					state.copy(sourceErrors = state.sourceErrors.filterNot { it.source == source })
				}
			}
			val outcome = runCatchingCancellable {
				searchV2HelperFactory.create(source).invoke(activeQuery, kind)
			}
			outcome.fold(
				onSuccess = { results ->
					if (results != null && results.manga.isNotEmpty()) {
						val section = GlobalSearchSourceSection(
							source = source,
							sourceName = source.name,
							sourceTitle = source.getTitle(context),
							isNovel = source.isNovelSource,
							items = results.manga.distinctBy { it.id },
						)
						stateMutex.withLock {
							_uiState.update { state ->
								state.copy(
									sections = (state.sections.filterNot { it.source == source } + section),
								)
							}
						}
					} else {
						toastManager.show("No results from ${source.getTitle(context)}")
					}
				},
				onFailure = { error ->
					val errEntry = GlobalSearchSourceError(
						source = source,
						sourceName = source.name,
						sourceTitle = source.getTitle(context),
						message = error.getDisplayMessage(context.resources),
						cloudFlareException = error as? CloudFlareProtectedException,
						interactiveException = error as? InteractiveActionRequiredException,
					)
					stateMutex.withLock {
						_uiState.update { state ->
							state.copy(sourceErrors = state.sourceErrors + errEntry)
						}
					}
				},
			)
		}
	}

	private suspend fun queryOneSource(
		source: MangaSource,
		query: String,
		kind: SearchKind,
	) {
		val result = runCatchingCancellable {
			searchV2HelperFactory.create(source).invoke(query, kind)
		}
		stateMutex.withLock {
			result.fold(
				onSuccess = { searchResults ->
					if (searchResults != null && searchResults.manga.isNotEmpty()) {
						val section = GlobalSearchSourceSection(
							source = source,
							sourceName = source.name,
							sourceTitle = source.getTitle(context),
							isNovel = source.isNovelSource,
							items = searchResults.manga.distinctBy { it.id },
						)
						_uiState.update { state ->
							state.copy(
								sections = state.sections + section,
								completedCount = state.completedCount + 1,
							)
						}
					} else {
						_uiState.update { state ->
							state.copy(completedCount = state.completedCount + 1)
						}
					}
				},
				onFailure = { error ->
					val errEntry = GlobalSearchSourceError(
						source = source,
						sourceName = source.name,
						sourceTitle = source.getTitle(context),
						message = error.getDisplayMessage(context.resources),
						cloudFlareException = error as? CloudFlareProtectedException,
						interactiveException = error as? InteractiveActionRequiredException,
					)
					_uiState.update { state ->
						state.copy(
							sourceErrors = state.sourceErrors + errEntry,
							completedCount = state.completedCount + 1,
						)
					}
				},
			)
		}
	}

	private fun loadRecentQueries() {
		viewModelScope.launch(Dispatchers.IO) {
			val recent = runCatching { searchRepository.getQuerySuggestion("", 12) }.getOrDefault(emptyList())
			_uiState.update { it.copy(recentQueries = recent) }
		}
	}

	private companion object {
		const val MAX_CONCURRENT_SOURCES = 4
	}
}
