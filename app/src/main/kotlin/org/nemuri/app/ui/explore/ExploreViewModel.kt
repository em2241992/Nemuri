package org.nemuri.app.ui.explore

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
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.SourceLanguage
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.explore.domain.ExploreRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreManager
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

enum class ExploreSourceFilter(val label: String) {
	ALL("All"),
	PINNED("Pinned"),
	MANGA("Manga"),
	NOVELS("Novels"),
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
data class ExploreUiState(
	val sources: List<ExploreSourceTile> = emptyList(),
	val allEnabledCount: Int = 0,
	val activeFilter: ExploreSourceFilter = ExploreSourceFilter.ALL,
	val sourceQuery: String = "",
	val sortOrder: SourcesSortOrder = SourcesSortOrder.ALPHABETIC,
	val languages: List<SourceLanguage> = emptyList(),
	val isLoadingSources: Boolean = false,
	val hasExtensionUpdates: Boolean = false,
	val isFindingRandom: Boolean = false,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val sourcesRepository: MangaSourcesRepository,
	private val exploreRepository: ExploreRepository,
	private val extensionStoreManager: ExtensionStoreManager,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val activeFilter = MutableStateFlow(ExploreSourceFilter.ALL)
	private val sourceQuery = MutableStateFlow("")
	private val isFindingRandom = MutableStateFlow(false)
	private val languageRefreshTrigger = MutableStateFlow(0)

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
						it == AppSettings.KEY_LN_HIDDEN_PLUGINS
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
		)
	}

	val uiState: StateFlow<ExploreUiState> = combine(
		sourcesMetadataFlow,
		activeFilter,
		sourceQuery,
		isFindingRandom,
	) { meta, filter, query, findingRandom ->
		val trimmed = query.trim()
		val filteredTiles = meta.enabledSources
			.filter { info ->
				when (filter) {
					ExploreSourceFilter.ALL -> true
					ExploreSourceFilter.PINNED -> info.isPinned
					ExploreSourceFilter.MANGA -> !info.isNovelSource
					ExploreSourceFilter.NOVELS -> info.isNovelSource
				}
			}
			.map { info ->
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
			.filter { tile ->
				trimmed.isEmpty() ||
					tile.title.contains(trimmed, ignoreCase = true) ||
					tile.summary?.contains(trimmed, ignoreCase = true) == true
			}

		ExploreUiState(
			sources = filteredTiles,
			allEnabledCount = meta.enabledSources.size,
			activeFilter = filter,
			sourceQuery = query,
			sortOrder = meta.sortOrder,
			languages = meta.languages,
			isLoadingSources = meta.isLoading,
			hasExtensionUpdates = meta.hasUpdates,
			isFindingRandom = findingRandom,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = ExploreUiState(),
		)

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
	)
}
