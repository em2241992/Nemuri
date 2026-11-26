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
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.SourceLanguage
import org.koitharu.kotatsu.explore.data.SourceLanguageVariant
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.explore.domain.ExploreRepository
import org.koitharu.kotatsu.extensions.runtime.normalizeExternalExtensionLangCode
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreManager
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

enum class ExploreCatalogKind(val label: String) {
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
	val isHomeSource: Boolean,
)

@Immutable
data class ExploreUiState(
	val sources: List<ExploreSourceTile> = emptyList(),
	val allEnabledSources: List<ExploreSourceTile> = emptyList(),
	val mangaSourceCount: Int = 0,
	val novelSourceCount: Int = 0,
	val allEnabledCount: Int = 0,
	val selectedKind: ExploreCatalogKind = ExploreCatalogKind.MANGA,
	val isNovelTabFirst: Boolean = false,
	val isSourcesGridMode: Boolean = true,
	val sourcesGridColumns: Int = 4,
	val sourceQuery: String = "",
	val sortOrder: SourcesSortOrder = SourcesSortOrder.ALPHABETIC,
	val languages: List<SourceLanguage> = emptyList(),
	val isLoadingSources: Boolean = false,
	val hasExtensionUpdates: Boolean = false,
	val isFindingRandom: Boolean = false,
	val homeSourceName: String? = null,
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

	private val selectedKindFlow = MutableStateFlow(
		if (settings.isNovelTabFirst) ExploreCatalogKind.NOVELS else ExploreCatalogKind.MANGA,
	)
	private val sourceQueryFlow = MutableStateFlow("")
	private val isFindingRandomFlow = MutableStateFlow(false)
	private val languageRefreshTrigger = MutableStateFlow(0)

	private val prefsAndLanguagesFlow = combine(
		settings.observeChanges()
			.filter { key ->
				key == null ||
					key == AppSettings.KEY_SOURCES_ORDER ||
					key == AppSettings.KEY_SOURCES_GRID ||
					key == AppSettings.KEY_SOURCES_GRID_COLUMNS ||
					key == AppSettings.KEY_EXPLORE_NOVEL_TAB_FIRST ||
					key == AppSettings.KEY_EXPLORE_HOME_SOURCE ||
					key == AppSettings.KEY_HIDDEN_SOURCE_LANGUAGES ||
					key == AppSettings.KEY_MIHON_HIDDEN_PACKAGES ||
					key == AppSettings.KEY_LN_HIDDEN_PLUGINS
			}
			.onStart { emit(null) },
		languageRefreshTrigger,
	) { _, _ ->
		ExplorePrefsSnapshot(
			sortOrder = settings.sourcesSortOrder,
			isSourcesGridMode = settings.isSourcesGridMode,
			sourcesGridColumns = settings.sourcesGridColumns,
			isNovelTabFirst = settings.isNovelTabFirst,
			preferredHomeSourceName = settings.exploreHomeSource,
			languages = sourcesRepository.getSourceLanguages(),
		)
	}

	private val runtimeSourcesFlow = combine(
		sourcesRepository.observeEnabledSources(),
		sourcesRepository.observeMihonLoadingState(),
		extensionStoreManager.hasUpdates,
	) { enabled, isLoading, hasUpdates ->
		Triple(enabled, isLoading, hasUpdates)
	}

	val uiState: StateFlow<ExploreUiState> = combine(
		runtimeSourcesFlow,
		prefsAndLanguagesFlow,
		selectedKindFlow,
		sourceQueryFlow,
		isFindingRandomFlow,
	) { (enabledSources, isLoading, hasUpdates), prefs, selectedKind, query, findingRandom ->
		val trimmed = query.trim()
		val resolvedHomeName = prefs.preferredHomeSourceName
			?: enabledSources.firstOrNull { it.isPinned && !it.isNovelSource }?.name
			?: enabledSources.firstOrNull { !it.isNovelSource }?.name
			?: enabledSources.firstOrNull()?.name

		val allTiles = enabledSources.map { info ->
			ExploreSourceTile(
				sourceInfo = info,
				sourceName = info.name,
				title = info.getTitle(context),
				summary = info.getSummary(context),
				homeUrl = info.homeUrl,
				isNovel = info.isNovelSource,
				isNsfw = info.isNsfw(),
				isPinned = info.isPinned,
				isHomeSource = info.name == resolvedHomeName,
			)
		}

		val mangaCount = allTiles.count { !it.isNovel }
		val novelCount = allTiles.count { it.isNovel }

		// Auto-fallback to the non-empty kind only if user hasn't searched and current tab has 0 while the other has >0
		val filteredByKind = allTiles.filter { tile ->
			when (selectedKind) {
				ExploreCatalogKind.MANGA -> !tile.isNovel
				ExploreCatalogKind.NOVELS -> tile.isNovel
			}
		}

		val filteredTiles = filteredByKind.filter { tile ->
			trimmed.isEmpty() ||
				tile.title.contains(trimmed, ignoreCase = true) ||
				tile.summary?.contains(trimmed, ignoreCase = true) == true
		}

		ExploreUiState(
			sources = filteredTiles,
			allEnabledSources = allTiles,
			mangaSourceCount = mangaCount,
			novelSourceCount = novelCount,
			allEnabledCount = allTiles.size,
			selectedKind = selectedKind,
			isNovelTabFirst = prefs.isNovelTabFirst,
			isSourcesGridMode = prefs.isSourcesGridMode,
			sourcesGridColumns = prefs.sourcesGridColumns,
			sourceQuery = query,
			sortOrder = prefs.sortOrder,
			languages = prefs.languages,
			isLoadingSources = isLoading,
			hasExtensionUpdates = hasUpdates,
			isFindingRandom = findingRandom,
			homeSourceName = resolvedHomeName,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = ExploreUiState(
				isSourcesGridMode = settings.isSourcesGridMode,
				sourcesGridColumns = settings.sourcesGridColumns,
			),
		)

	fun selectCatalogKind(kind: ExploreCatalogKind) {
		selectedKindFlow.value = kind
	}

	fun toggleSourcesGridMode() {
		settings.isSourcesGridMode = !settings.isSourcesGridMode
	}

	fun setSourcesGridColumns(columns: Int) {
		settings.sourcesGridColumns = columns.coerceIn(3, 5)
	}

	fun setSourceQuery(query: String) {
		sourceQueryFlow.value = query
	}

	fun setHomeSourceForHub(tile: ExploreSourceTile) {
		settings.exploreHomeSource = tile.sourceName
		toastManager.show("Set ${tile.title} as Feed Hub source")
	}

	fun prepareOpenPopular(tile: ExploreSourceTile) {
		viewModelScope.launch(Dispatchers.IO) {
			sourcesRepository.trackUsage(tile.sourceInfo.mangaSource)
			val mihon = tile.sourceInfo.mangaSource as? MihonMangaSource
			if (mihon != null) {
				runCatching {
					SourceSettings(context, mihon).defaultSortOrder = SortOrder.POPULARITY
				}
			}
		}
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
				message = "Disabled ${tile.title} in Explore",
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
		val normalized = normalizeExternalExtensionLangCode(code)
		val current = settings.hiddenSourceLanguages.mapTo(LinkedHashSet()) { normalizeExternalExtensionLangCode(it) }
		val updated = if (current.any { it.equals(normalized, ignoreCase = true) }) {
			current.filterNotTo(LinkedHashSet()) { it.equals(normalized, ignoreCase = true) }
		} else {
			current + normalized
		}
		sourcesRepository.setHiddenLanguages(updated)
		languageRefreshTrigger.value++
	}

	fun getLanguageVariants(tile: ExploreSourceTile): List<SourceLanguageVariant> =
		sourcesRepository.getSourceLanguageVariants(tile.sourceInfo.mangaSource)

	fun selectTileLanguage(tile: ExploreSourceTile, languageCode: String) {
		viewModelScope.launch(Dispatchers.IO) {
			val resolved = sourcesRepository.setActiveSourceLanguage(tile.sourceInfo.mangaSource, languageCode)
			languageRefreshTrigger.value++
			toastManager.show("Switched ${tile.title} to ${resolved.languageSubtitle ?: languageCode.uppercase()}")
		}
	}

	fun findRandomManga(onFound: (Manga) -> Unit) {
		if (isFindingRandomFlow.value) return
		viewModelScope.launch(Dispatchers.IO) {
			isFindingRandomFlow.value = true
			val result = runCatching {
				exploreRepository.findRandomManga(tagsLimit = 5)
			}
			isFindingRandomFlow.value = false
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

	private data class ExplorePrefsSnapshot(
		val sortOrder: SourcesSortOrder,
		val isSourcesGridMode: Boolean,
		val sourcesGridColumns: Int,
		val isNovelTabFirst: Boolean,
		val preferredHomeSourceName: String?,
		val languages: List<SourceLanguage>,
	)
}
