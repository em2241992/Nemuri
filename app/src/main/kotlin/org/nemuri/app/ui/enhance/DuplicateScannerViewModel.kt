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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.SemanticPairResult
import org.koitharu.kotatsu.ai.domain.SemanticTitleMatcher
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.favourites.domain.EditionFlag
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

enum class DuplicateConfidenceBucket(val label: String, val shortLabel: String) {
	EXACT_DUPLICATES("Exact Duplicates", "Exact"),
	EDITIONS_VARIANTS("Editions & Variants", "Editions"),
	NEEDS_REVIEW("Needs Review", "Needs Review"),
}

@Immutable
data class DuplicateEntrySummary(
	val manga: Manga,
	val sourceTitle: String,
	val chaptersCount: Int,
	val progressPercent: Int,
	val categoryTitles: List<String>,
	val editionFlags: Set<EditionFlag> = emptySet(),
	val isSourceHealthy: Boolean = true,
	val rating: Float = -1f,
)

@Immutable
data class DuplicatePairItem(
	val key: String,
	val bucket: DuplicateConfidenceBucket,
	val reason: String,
	val first: DuplicateEntrySummary,
	val second: DuplicateEntrySummary,
	val isEditionVariant: Boolean = false,
) {
	fun orderedBy(strategy: DuplicateWinnerStrategy): DuplicatePairItem {
		val cmp = when (strategy) {
			DuplicateWinnerStrategy.MOST_CHAPTERS -> compareValuesBy(
				first,
				second,
				{ it.chaptersCount },
				{ it.isSourceHealthy },
				{ it.progressPercent },
				{ it.rating },
			)

			DuplicateWinnerStrategy.FURTHEST_READ -> compareValuesBy(
				first,
				second,
				{ it.progressPercent },
				{ it.chaptersCount },
				{ it.isSourceHealthy },
				{ it.rating },
			)

			DuplicateWinnerStrategy.HEALTHY_SOURCE -> compareValuesBy(
				first,
				second,
				{ it.isSourceHealthy },
				{ it.chaptersCount },
				{ it.progressPercent },
				{ it.rating },
			)

			DuplicateWinnerStrategy.HIGHEST_RATING -> compareValuesBy(
				first,
				second,
				{ it.rating },
				{ it.chaptersCount },
				{ it.isSourceHealthy },
				{ it.progressPercent },
			)
		}
		return if (cmp >= 0) this else copy(first = second, second = first)
	}
}

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
	val activeBucket: DuplicateConfidenceBucket = DuplicateConfidenceBucket.EXACT_DUPLICATES,
	val exactPairs: List<DuplicatePairItem> = emptyList(),
	val editionVariantPairs: List<DuplicatePairItem> = emptyList(),
	val needsReviewPairs: List<DuplicatePairItem> = emptyList(),
	val winnerStrategy: DuplicateWinnerStrategy = DuplicateWinnerStrategy.MOST_CHAPTERS,
	val isCheckOnAddEnabled: Boolean = true,
	val ignoredPairsCount: Int = 0,
	val selectedBatchKeys: Set<String> = emptySet(),
	val showBatchMergeConfirm: Boolean = false,
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
	val activeBucket: DuplicateConfidenceBucket = DuplicateConfidenceBucket.EXACT_DUPLICATES,
	val dismissedKeys: Set<String> = emptySet(),
	val selectedBatchKeys: Set<String> = emptySet(),
	val showBatchMergeConfirm: Boolean = false,
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
		settings.observeChanges().onStart { emit(null) },
	) { snap, local, _ ->
		val strategy = settings.duplicateWinnerStrategy
		val ignoredCount = settings.ignoredDuplicatePairs.size

		fun List<DuplicatePairItem>.prepareVisible(): List<DuplicatePairItem> =
			asSequence()
				.filterNot { pair ->
					pair.key in local.dismissedKeys ||
						settings.isDuplicatePairIgnored(pair.first.manga.id, pair.second.manga.id)
				}
				.map { it.orderedBy(strategy) }
				.toList()

		val exactVisible = snap.exactPairs.prepareVisible()
		val editionVisible = snap.editionVariantPairs.prepareVisible()
		val reviewVisible = snap.needsReviewPairs.prepareVisible()

		val validExactKeys = exactVisible.mapTo(HashSet(exactVisible.size)) { it.key }
		// SAFETY: Never allow Editions & Variants or non-Exact pairs in batch merge selection.
		val sanitizedBatchKeys = local.selectedBatchKeys.intersect(validExactKeys)

		DuplicateScannerUiState(
			isScanning = snap.isScanningTier1 && snap.exactPairs.isEmpty() && snap.editionVariantPairs.isEmpty(),
			isScanningTier2 = snap.isScanningTier2,
			isMerging = local.isMerging,
			activeBucket = local.activeBucket,
			exactPairs = exactVisible,
			editionVariantPairs = editionVisible,
			needsReviewPairs = reviewVisible,
			winnerStrategy = strategy,
			isCheckOnAddEnabled = settings.isDuplicateCheckEnabled,
			ignoredPairsCount = ignoredCount,
			selectedBatchKeys = sanitizedBatchKeys,
			showBatchMergeConfirm = local.showBatchMergeConfirm && sanitizedBatchKeys.isNotEmpty(),
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
		initialValue = DuplicateScannerUiState(
			winnerStrategy = settings.duplicateWinnerStrategy,
			isCheckOnAddEnabled = settings.isDuplicateCheckEnabled,
			ignoredPairsCount = settings.ignoredDuplicatePairs.size,
		),
	)

	fun selectBucket(bucket: DuplicateConfidenceBucket) {
		localState.update { state ->
			// Editions & Variants default selection MUST always be OFF.
			val nextSelected = if (bucket != DuplicateConfidenceBucket.EXACT_DUPLICATES) {
				emptySet()
			} else {
				state.selectedBatchKeys
			}
			state.copy(
				activeBucket = bucket,
				selectedBatchKeys = nextSelected,
				showBatchMergeConfirm = false,
			)
		}
	}

	fun setWinnerStrategy(strategy: DuplicateWinnerStrategy) {
		settings.duplicateWinnerStrategy = strategy
	}

	fun setCheckOnAddEnabled(enabled: Boolean) {
		settings.isDuplicateCheckEnabled = enabled
	}

	fun ignorePair(pair: DuplicatePairItem) {
		val idA = pair.first.manga.id
		val idB = pair.second.manga.id
		settings.ignoreDuplicatePair(idA, idB)
		duplicateCache.removeIgnoredPair(idA, idB)
		localState.update {
			it.copy(selectedBatchKeys = it.selectedBatchKeys - pair.key)
		}
		toastManager.show("Kept both — pair added to ignored list")
	}

	fun resetIgnoredPairs() {
		val count = settings.ignoredDuplicatePairs.size
		if (count == 0) return
		settings.clearIgnoredDuplicatePairs()
		localState.update { it.copy(dismissedKeys = emptySet()) }
		duplicateCache.ensureScanned(forceRefresh = true)
		toastManager.show("Reset $count ignored pair(s) — rescanning library")
	}

	fun toggleBatchSelection(pair: DuplicatePairItem) {
		// Strictly block Editions & Variants from batch merge selection.
		if (pair.isEditionVariant || pair.bucket != DuplicateConfidenceBucket.EXACT_DUPLICATES) return
		localState.update { state ->
			val next = if (pair.key in state.selectedBatchKeys) {
				state.selectedBatchKeys - pair.key
			} else {
				state.selectedBatchKeys + pair.key
			}
			state.copy(selectedBatchKeys = next)
		}
	}

	fun selectAllExactPairs() {
		val safeExactKeys = uiState.value.exactPairs
			.filter { !it.isEditionVariant && !it.first.manga.source.isLocal && !it.second.manga.source.isLocal }
			.mapTo(LinkedHashSet()) { it.key }
		localState.update { it.copy(selectedBatchKeys = safeExactKeys) }
	}

	fun clearBatchSelection() {
		localState.update { it.copy(selectedBatchKeys = emptySet(), showBatchMergeConfirm = false) }
	}

	fun requestBatchMerge() {
		if (uiState.value.selectedBatchKeys.isEmpty()) return
		localState.update { it.copy(showBatchMergeConfirm = true) }
	}

	fun cancelBatchMerge() {
		localState.update { it.copy(showBatchMergeConfirm = false) }
	}

	fun confirmBatchMerge(migrateProgress: Boolean) {
		val state = uiState.value
		if (state.isMerging) return
		val targets = state.exactPairs.filter {
			it.key in state.selectedBatchKeys && !it.isEditionVariant
		}
		if (targets.isEmpty()) {
			localState.update { it.copy(showBatchMergeConfirm = false, selectedBatchKeys = emptySet()) }
			return
		}
		localState.update {
			it.copy(isMerging = true, showBatchMergeConfirm = false, selectedBatchKeys = emptySet())
		}

		viewModelScope.launch(Dispatchers.IO) {
			var mergedCount = 0
			val consumedIds = HashSet<Long>()
			for (pair in targets) {
				val ordered = pair.orderedBy(settings.duplicateWinnerStrategy)
				val keep = ordered.first.manga
				val discard = ordered.second.manga
				if (keep.id in consumedIds || discard.id in consumedIds) continue
				val res = runCatchingCancellable {
					mergePairPreservingFurthestProgress(keep = keep, discard = discard, migrateProgress = migrateProgress)
				}
				if (res.isSuccess) {
					consumedIds.add(discard.id)
					duplicateCache.removeMergedManga(discard.id)
					mergedCount++
				}
			}
			localState.update { it.copy(isMerging = false) }
			toastManager.show("Consolidated $mergedCount exact duplicate pair(s)")
		}
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
		localState.update {
			it.copy(
				pendingMerge = null,
				isMerging = true,
				selectedBatchKeys = it.selectedBatchKeys - pending.pair.key,
			)
		}

		viewModelScope.launch(Dispatchers.IO) {
			val result = runCatchingCancellable {
				mergePairPreservingFurthestProgress(keep = keep, discard = discard, migrateProgress = migrateProgress)
			}
			localState.update { it.copy(isMerging = false) }
			result.onSuccess {
				duplicateCache.removeMergedManga(discard.id)
				toastManager.show("Kept ${keep.title} (${keepSummary.sourceTitle}) and consolidated duplicate")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to merge duplicate pair")
			}
		}
	}

	/**
	 * Consolidates [discard] into [keep]:
	 * Always runs [MigrateUseCase] with `migrateProgress = true` when requested so bookmarks,
	 * update trackers, and scrobbler links transfer onto [keep], while snapshotting [keep]'s
	 * prior reading history beforehand and restoring it whenever [keep] was already further ahead.
	 */
	private suspend fun mergePairPreservingFurthestProgress(
		keep: Manga,
		discard: Manga,
		migrateProgress: Boolean,
	) {
		val keepCats = favouritesRepository.getCategoriesIds(keep.id)
		val discardCats = favouritesRepository.getCategoriesIds(discard.id)
		if (migrateProgress && !keep.source.isLocal && !discard.source.isLocal) {
			val db = databaseProvider.get()
			val historyDao = db.getHistoryDao()
			val chaptersDao = db.getChaptersDao()
			val keepHistorySnapshot = historyDao.find(keep.id)

			migrateUseCaseProvider.get().invoke(
				oldManga = discard,
				newManga = keep,
				migrateProgress = true,
			)

			if (keepHistorySnapshot != null) {
				val postHistory = historyDao.find(keep.id)
				val shouldRestoreKeepHistory = if (postHistory == null) {
					true
				} else {
					val keepChapters = chaptersDao.findAll(keep.id)
					val snapIdx = keepChapters.indexOfFirst { it.chapterId == keepHistorySnapshot.chapterId }
					val postIdx = keepChapters.indexOfFirst { it.chapterId == postHistory.chapterId }
					when {
						snapIdx >= 0 && postIdx >= 0 && snapIdx != postIdx -> snapIdx > postIdx
						snapIdx >= 0 && postIdx == snapIdx ->
							keepHistorySnapshot.page > postHistory.page ||
								keepHistorySnapshot.scroll > postHistory.scroll ||
								keepHistorySnapshot.percent > postHistory.percent
						else -> keepHistorySnapshot.percent > postHistory.percent
					}
				}
				if (shouldRestoreKeepHistory) {
					historyDao.upsert(
						keepHistorySnapshot.copy(
							deletedAt = 0L,
							updatedAt = maxOf(keepHistorySnapshot.updatedAt, System.currentTimeMillis()),
						),
					)
				}
			}
		} else {
			favouritesRepository.removeFromFavourites(listOf(discard.id))
		}
		val allCats = (keepCats + discardCats).distinct()
		for (catId in allCats) {
			favouritesRepository.addToCategory(catId, listOf(keep))
		}
	}

	fun scanLibrary() {
		batchAiJob?.cancel()
		localState.update { it.copy(isBatchAiRunning = false, selectedBatchKeys = emptySet()) }
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
		val targets = state.needsReviewPairs
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
