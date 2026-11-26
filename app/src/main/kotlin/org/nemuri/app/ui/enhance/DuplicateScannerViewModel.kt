package org.nemuri.app.ui.enhance

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.SemanticPairResult
import org.koitharu.kotatsu.ai.domain.SemanticTitleMatcher
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

enum class DuplicateConfidenceBucket(val label: String) {
	HIGH_CONFIDENCE("High Confidence"),
	REVIEW_CAREFULLY("Review Carefully"),
}

@Immutable
data class DuplicateEntrySummary(
	val manga: Manga,
	val sourceTitle: String,
	val chaptersCount: Int,
	val progressPercent: Int,
	val categoryTitles: List<String>,
)

@Immutable
data class DuplicatePairItem(
	val key: String,
	val bucket: DuplicateConfidenceBucket,
	val reason: String,
	val first: DuplicateEntrySummary,
	val second: DuplicateEntrySummary,
)

@Immutable
data class PendingDuplicateMerge(
	val pair: DuplicatePairItem,
	val keepFirst: Boolean,
)

@Immutable
data class DuplicateScannerUiState(
	val isScanning: Boolean = true,
	val isScanningTier2: Boolean = false,
	val isMerging: Boolean = false,
	val activeBucket: DuplicateConfidenceBucket = DuplicateConfidenceBucket.HIGH_CONFIDENCE,
	val highConfidencePairs: List<DuplicatePairItem> = emptyList(),
	val reviewCarefullyPairs: List<DuplicatePairItem> = emptyList(),
	val pendingMerge: PendingDuplicateMerge? = null,
	val isAiEnabled: Boolean = false,
	val aiVerdicts: Map<String, SemanticPairResult> = emptyMap(),
	val verifyingPairKeys: Set<String> = emptySet(),
	val isBatchAiRunning: Boolean = false,
	val batchAiDone: Int = 0,
	val batchAiTotal: Int = 0,
)

private data class ScannerLocalState(
	val isMerging: Boolean = false,
	val activeBucket: DuplicateConfidenceBucket = DuplicateConfidenceBucket.HIGH_CONFIDENCE,
	val dismissedKeys: Set<String> = emptySet(),
	val pendingMerge: PendingDuplicateMerge? = null,
	val aiVerdicts: Map<String, SemanticPairResult> = emptyMap(),
	val verifyingPairKeys: Set<String> = emptySet(),
	val isBatchAiRunning: Boolean = false,
	val batchAiDone: Int = 0,
	val batchAiTotal: Int = 0,
)

@HiltViewModel
class DuplicateScannerViewModel @Inject constructor(
	private val databaseProvider: Provider<MangaDatabase>,
	private val favouritesRepository: FavouritesRepository,
	private val migrateUseCaseProvider: Provider<MigrateUseCase>,
	private val semanticTitleMatcherProvider: Provider<SemanticTitleMatcher>,
	private val duplicateCache: LibraryDuplicateCache,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val localState = MutableStateFlow(ScannerLocalState())
	private var batchAiJob: Job? = null

	init {
		duplicateCache.ensureScanned(forceRefresh = false)
	}

	val uiState: StateFlow<DuplicateScannerUiState> = combine(
		duplicateCache.snapshot,
		localState,
	) { snap, local ->
		DuplicateScannerUiState(
			isScanning = snap.isScanningTier1 && snap.highConfidencePairs.isEmpty(),
			isScanningTier2 = snap.isScanningTier2,
			isMerging = local.isMerging,
			activeBucket = local.activeBucket,
			highConfidencePairs = snap.highConfidencePairs.filterNot { it.key in local.dismissedKeys },
			reviewCarefullyPairs = snap.reviewCarefullyPairs.filterNot { it.key in local.dismissedKeys },
			pendingMerge = local.pendingMerge,
			isAiEnabled = AiMode.fromKey(settings.aiMode) != AiMode.OFF,
			aiVerdicts = local.aiVerdicts,
			verifyingPairKeys = local.verifyingPairKeys,
			isBatchAiRunning = local.isBatchAiRunning,
			batchAiDone = local.batchAiDone,
			batchAiTotal = local.batchAiTotal,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = DuplicateScannerUiState(),
	)

	fun selectBucket(bucket: DuplicateConfidenceBucket) {
		localState.update { it.copy(activeBucket = bucket) }
	}

	fun dismissPair(pairKey: String) {
		localState.update { it.copy(dismissedKeys = it.dismissedKeys + pairKey) }
	}

	fun requestMerge(pair: DuplicatePairItem, keepFirst: Boolean) {
		localState.update { it.copy(pendingMerge = PendingDuplicateMerge(pair, keepFirst)) }
	}

	fun cancelMerge() {
		localState.update { it.copy(pendingMerge = null) }
	}

	fun confirmMerge(migrateProgress: Boolean) {
		val pending = localState.value.pendingMerge ?: return
		if (localState.value.isMerging) return
		val keepSummary = if (pending.keepFirst) pending.pair.first else pending.pair.second
		val discardSummary = if (pending.keepFirst) pending.pair.second else pending.pair.first
		val keep = keepSummary.manga
		val discard = discardSummary.manga
		localState.update { it.copy(pendingMerge = null, isMerging = true) }

		viewModelScope.launch(Dispatchers.IO) {
			val result = runCatchingCancellable {
				val keepCats = favouritesRepository.getCategoriesIds(keep.id)
				val discardCats = favouritesRepository.getCategoriesIds(discard.id)
				if (migrateProgress && !keep.source.isLocal && !discard.source.isLocal) {
					val historyDao = databaseProvider.get().getHistoryDao()
					val keepPercent = historyDao.find(keep.id)?.percent ?: -1f
					val discardPercent = historyDao.find(discard.id)?.percent ?: -1f
					migrateUseCaseProvider.get().invoke(
						oldManga = discard,
						newManga = keep,
						migrateProgress = discardPercent > keepPercent,
					)
				} else {
					favouritesRepository.removeFromFavourites(listOf(discard.id))
				}
				val allCats = (keepCats + discardCats).distinct()
				for (catId in allCats) {
					favouritesRepository.addToCategory(catId, listOf(keep))
				}
			}
			localState.update { it.copy(isMerging = false) }
			result.onSuccess {
				duplicateCache.removeMergedManga(discard.id)
				toastManager.show("Kept ${keep.title} (${keepSummary.sourceTitle}) and removed duplicate")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to merge duplicate pair")
			}
		}
	}

	fun scanLibrary() {
		batchAiJob?.cancel()
		localState.update { it.copy(isBatchAiRunning = false) }
		duplicateCache.ensureScanned(forceRefresh = true)
	}

	fun verifyPairWithAi(pair: DuplicatePairItem) {
		if (AiMode.fromKey(settings.aiMode) == AiMode.OFF) {
			toastManager.show("AI features are turned off")
			return
		}
		if (pair.key in localState.value.verifyingPairKeys) return
		viewModelScope.launch(Dispatchers.IO) {
			localState.update { it.copy(verifyingPairKeys = it.verifyingPairKeys + pair.key) }
			val res = semanticTitleMatcherProvider.get().verifyDuplicatePair(
				titleA = pair.first.manga.title,
				altTitlesA = pair.first.manga.altTitles,
				titleB = pair.second.manga.title,
				altTitlesB = pair.second.manga.altTitles,
			)
			localState.update { state ->
				val nextVerdicts = res.getOrNull()?.let { state.aiVerdicts + (pair.key to it) } ?: state.aiVerdicts
				state.copy(
					verifyingPairKeys = state.verifyingPairKeys - pair.key,
					aiVerdicts = nextVerdicts,
				)
			}
			res.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "AI verification failed")
			}
		}
	}

	fun verifyNextFiveWithAi() {
		val state = uiState.value
		if (state.isBatchAiRunning || AiMode.fromKey(settings.aiMode) == AiMode.OFF) return
		val targets = state.reviewCarefullyPairs
			.filterNot { it.key in state.aiVerdicts }
			.take(MAX_BATCH_AI_PAIRS)
		if (targets.isEmpty()) {
			toastManager.show("All ambiguous pairs have already been checked")
			return
		}
		batchAiJob?.cancel()
		batchAiJob = viewModelScope.launch(Dispatchers.IO) {
			localState.update {
				it.copy(isBatchAiRunning = true, batchAiDone = 0, batchAiTotal = targets.size)
			}
			val matcher = semanticTitleMatcherProvider.get()
			for ((idx, pair) in targets.withIndex()) {
				if (!isActive) break
				localState.update { it.copy(verifyingPairKeys = it.verifyingPairKeys + pair.key) }
				val res = matcher.verifyDuplicatePair(
					titleA = pair.first.manga.title,
					altTitlesA = pair.first.manga.altTitles,
					titleB = pair.second.manga.title,
					altTitlesB = pair.second.manga.altTitles,
				)
				localState.update { cur ->
					val nextVerdicts = res.getOrNull()?.let { cur.aiVerdicts + (pair.key to it) } ?: cur.aiVerdicts
					cur.copy(
						verifyingPairKeys = cur.verifyingPairKeys - pair.key,
						aiVerdicts = nextVerdicts,
						batchAiDone = idx + 1,
					)
				}
				if (res.isFailure) {
					toastManager.show(res.exceptionOrNull()?.localizedMessage ?: "AI verification stopped")
					break
				}
			}
			localState.update { it.copy(isBatchAiRunning = false) }
		}
	}

	fun cancelBatchAi() {
		batchAiJob?.cancel()
		localState.update { it.copy(isBatchAiRunning = false) }
	}

	private companion object {
		const val MAX_BATCH_AI_PAIRS = 5
	}
}
