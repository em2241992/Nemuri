package org.nemuri.app.ui.enhance

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.SemanticTitleMatcher
import org.koitharu.kotatsu.alternatives.domain.AlternativesUseCase
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.core.model.chaptersCount
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class MigrationTargetSource(
	val name: String,
	val title: String,
)

@Immutable
data class MigrationCandidateItem(
	val manga: Manga,
	val sourceTitle: String,
	val chaptersCount: Int,
)

@Immutable
data class MigrateMangaUiState(
	val isLoadingSeed: Boolean = true,
	val seed: Manga? = null,
	val seedChaptersCount: Int = 0,
	val seedSourceTitle: String = "",
	val availableSources: List<MigrationTargetSource> = emptyList(),
	val selectedSourceNames: Set<String> = emptySet(),
	val sourceQuery: String = "",
	val looseMatch: Boolean = true,
	val isSearching: Boolean = false,
	val hasSearched: Boolean = false,
	val candidates: List<MigrationCandidateItem> = emptyList(),
	val pendingConfirm: MigrationCandidateItem? = null,
	val isMigrating: Boolean = false,
	val isAiEnabled: Boolean = false,
	val isRunningAiFallback: Boolean = false,
	val aiSuggestedQueries: List<String> = emptyList(),
	val aiEngineUsed: String? = null,
)

@HiltViewModel
class MigrateMangaViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val mangaDataRepository: MangaDataRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val alternativesUseCaseProvider: Provider<AlternativesUseCase>,
	private val migrateUseCaseProvider: Provider<MigrateUseCase>,
	private val semanticTitleMatcherProvider: Provider<SemanticTitleMatcher>,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val _uiState = MutableStateFlow(MigrateMangaUiState())
	val uiState: StateFlow<MigrateMangaUiState> = _uiState.asStateFlow()

	private var boundId: Long = 0L
	private var searchJob: Job? = null

	fun bind(mangaId: Long) {
		if (boundId == mangaId && _uiState.value.seed != null) return
		boundId = mangaId
		searchJob?.cancel()
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.value = MigrateMangaUiState(isLoadingSeed = true)
			val seed = mangaDataRepository.findMangaById(mangaId, withChapters = true)
				?: MangaTransferCache.get(mangaId)
			if (seed == null) {
				_uiState.update { it.copy(isLoadingSeed = false) }
				return@launch
			}
			val isNovel = seed.source.isNovelSource
			val enabled = sourcesRepository.observeEnabledSources().first()
			val targets = enabled
				.filter { it.mangaSource != seed.source && it.isNovelSource == isNovel }
				.map { MigrationTargetSource(it.name, it.getTitle(context)) }
			val defaultSelected = targets.take(8).mapTo(HashSet()) { it.name }
			_uiState.update {
				it.copy(
					isLoadingSeed = false,
					seed = seed,
					seedChaptersCount = seed.chaptersCount(),
					seedSourceTitle = seed.source.getTitle(context),
					availableSources = targets,
					selectedSourceNames = defaultSelected,
					isAiEnabled = AiMode.fromKey(settings.aiMode) != AiMode.OFF,
				)
			}
		}
	}

	fun setSourceQuery(query: String) {
		_uiState.update { it.copy(sourceQuery = query) }
	}

	fun toggleSource(sourceName: String) {
		_uiState.update { state ->
			val next = state.selectedSourceNames.toMutableSet()
			if (sourceName in next) next.remove(sourceName) else next.add(sourceName)
			state.copy(selectedSourceNames = next)
		}
	}

	fun selectAllSources() {
		_uiState.update { state ->
			state.copy(selectedSourceNames = state.availableSources.mapTo(HashSet()) { it.name })
		}
	}

	fun clearSources() {
		_uiState.update { it.copy(selectedSourceNames = emptySet()) }
	}

	fun setLooseMatch(loose: Boolean) {
		_uiState.update { it.copy(looseMatch = loose) }
	}

	fun searchAlternatives() {
		val state = _uiState.value
		val seed = state.seed ?: return
		val targets = state.selectedSourceNames
		if (targets.isEmpty()) {
			toastManager.show("Select at least one target source")
			return
		}
		searchJob?.cancel()
		searchJob = viewModelScope.launch(Dispatchers.IO) {
			_uiState.update {
				it.copy(
					isSearching = true,
					hasSearched = true,
					candidates = emptyList(),
					aiSuggestedQueries = emptyList(),
					aiEngineUsed = null,
				)
			}
			val found = ArrayList<MigrationCandidateItem>()
			alternativesUseCaseProvider.get()
				.invoke(seed, targets = targets, loose = state.looseMatch)
				.catch { err ->
					toastManager.show(err.localizedMessage ?: "Source search interrupted")
				}
				.collect { match ->
					val item = MigrationCandidateItem(
						manga = match,
						sourceTitle = match.source.getTitle(context),
						chaptersCount = match.chaptersCount(),
					)
					found += item
					_uiState.update { it.copy(candidates = found.sortedByDescending { c -> c.chaptersCount }) }
				}
			_uiState.update { it.copy(isSearching = false) }
		}
	}

	/**
	 * Strictly single-title Tier 3 AI Semantic Matcher fallback: only runs after Tier 1 & 2 search
	 * returns 0 matches for this single title, suggests up to 3 alternate localized/romanized
	 * queries, and searches selected targets for explicit user confirmation.
	 */
	fun runAiFallbackSearch() {
		val state = _uiState.value
		val seed = state.seed ?: return
		if (!state.hasSearched || state.isSearching || state.isRunningAiFallback || state.candidates.isNotEmpty()) {
			return
		}
		if (AiMode.fromKey(settings.aiMode) == AiMode.OFF) {
			toastManager.show("AI features are turned off")
			return
		}
		val targets = state.selectedSourceNames
		if (targets.isEmpty()) {
			toastManager.show("Select at least one target source")
			return
		}
		searchJob?.cancel()
		searchJob = viewModelScope.launch(Dispatchers.IO) {
			_uiState.update { it.copy(isRunningAiFallback = true, aiSuggestedQueries = emptyList(), aiEngineUsed = null) }
			val suggestionRes = semanticTitleMatcherProvider.get().suggestAlternateQueries(
				title = seed.title,
				altTitles = seed.altTitles,
				isNovel = seed.source.isNovelSource,
			)
			suggestionRes.onFailure { err ->
				_uiState.update { it.copy(isRunningAiFallback = false) }
				toastManager.show(err.localizedMessage ?: "AI title suggestion unavailable")
				return@launch
			}
			val (queries, engineUsed) = suggestionRes.getOrThrow()
			_uiState.update { it.copy(aiSuggestedQueries = queries, aiEngineUsed = engineUsed) }

			val isNovel = seed.source.isNovelSource
			val enabledSources = sourcesRepository.observeEnabledSources().first()
				.filter { it.mangaSource != seed.source && it.isNovelSource == isNovel && it.name in targets }
			val seenKeys = HashSet<String>()
			val found = ArrayList<MigrationCandidateItem>()
			val repoFactory = mangaRepositoryFactoryProvider.get()

			for (sourceInfo in enabledSources) {
				val searchHelper = searchHelperFactory.create(sourceInfo.mangaSource)
				for (query in queries) {
					val hits = runCatchingCancellable {
						searchHelper(query, SearchKind.SIMPLE)?.manga.orEmpty()
					}.getOrDefault(emptyList())
					val topHit = hits.firstOrNull { it.id != seed.id && it.source.isNovelSource == isNovel } ?: continue
					val dedupeKey = "${topHit.source.name}:${topHit.id}"
					if (!seenKeys.add(dedupeKey)) continue
					val detailed = runCatchingCancellable {
						repoFactory.create(topHit.source).getDetails(topHit)
					}.getOrDefault(topHit)
					found += MigrationCandidateItem(
						manga = detailed,
						sourceTitle = detailed.source.getTitle(context),
						chaptersCount = detailed.chaptersCount(),
					)
					_uiState.update { it.copy(candidates = found.sortedByDescending { c -> c.chaptersCount }) }
					break
				}
			}
			_uiState.update { it.copy(isRunningAiFallback = false) }
			if (found.isEmpty()) {
				toastManager.show("AI queries tried (${queries.joinToString()}), no source matches found")
			}
		}
	}

	fun requestConfirm(candidate: MigrationCandidateItem) {
		_uiState.update { it.copy(pendingConfirm = candidate) }
	}

	fun dismissConfirm() {
		_uiState.update { it.copy(pendingConfirm = null) }
	}

	fun confirmMigration(migrateProgress: Boolean, onMigrated: (Manga) -> Unit) {
		val state = _uiState.value
		val seed = state.seed ?: return
		val target = state.pendingConfirm ?: return
		if (state.isMigrating) return
		_uiState.update { it.copy(pendingConfirm = null, isMigrating = true) }

		viewModelScope.launch(Dispatchers.IO) {
			val result = runCatchingCancellable {
				migrateUseCaseProvider.get().invoke(
					oldManga = seed,
					newManga = target.manga,
					migrateProgress = migrateProgress,
				)
			}
			_uiState.update { it.copy(isMigrating = false) }
			result.onSuccess {
				MangaTransferCache.put(target.manga)
				toastManager.show("Migrated to ${target.sourceTitle}")
				launch(Dispatchers.Main) {
					onMigrated(target.manga)
				}
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Migration failed")
			}
		}
	}
}
