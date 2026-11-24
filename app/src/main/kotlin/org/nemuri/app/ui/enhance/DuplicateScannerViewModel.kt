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
import kotlinx.coroutines.flow.first
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
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.local.data.isEpub
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

@HiltViewModel
class DuplicateScannerViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val databaseProvider: Provider<MangaDatabase>,
	private val favouritesRepository: FavouritesRepository,
	private val migrateUseCaseProvider: Provider<MigrateUseCase>,
	private val semanticTitleMatcherProvider: Provider<SemanticTitleMatcher>,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val _uiState = MutableStateFlow(DuplicateScannerUiState())
	val uiState: StateFlow<DuplicateScannerUiState> = _uiState.asStateFlow()

	private val dismissedKeys = HashSet<String>()
	private var batchAiJob: Job? = null

	init {
		scanLibrary()
	}

	fun selectBucket(bucket: DuplicateConfidenceBucket) {
		_uiState.update { it.copy(activeBucket = bucket) }
	}

	fun dismissPair(pairKey: String) {
		dismissedKeys += pairKey
		_uiState.update { state ->
			state.copy(
				highConfidencePairs = state.highConfidencePairs.filterNot { it.key == pairKey },
				reviewCarefullyPairs = state.reviewCarefullyPairs.filterNot { it.key == pairKey },
			)
		}
	}

	fun requestMerge(pair: DuplicatePairItem, keepFirst: Boolean) {
		_uiState.update { it.copy(pendingMerge = PendingDuplicateMerge(pair, keepFirst)) }
	}

	fun cancelMerge() {
		_uiState.update { it.copy(pendingMerge = null) }
	}

	fun confirmMerge(migrateProgress: Boolean) {
		val pending = _uiState.value.pendingMerge ?: return
		if (_uiState.value.isMerging) return
		val keep = if (pending.keepFirst) pending.pair.first.manga else pending.pair.second.manga
		val discard = if (pending.keepFirst) pending.pair.second.manga else pending.pair.first.manga
		_uiState.update { it.copy(pendingMerge = null, isMerging = true) }

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
			_uiState.update { it.copy(isMerging = false) }
			result.onSuccess {
				toastManager.show("Kept ${keep.title} (${keep.source.getTitle(context)}) and removed duplicate")
				scanLibrary()
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to merge duplicate pair")
			}
		}
	}

	fun scanLibrary() {
		batchAiJob?.cancel()
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.update {
				it.copy(
					isScanning = true,
					isAiEnabled = AiMode.fromKey(settings.aiMode) != AiMode.OFF,
					isBatchAiRunning = false,
				)
			}
			val pairs = runCatchingCancellable { buildDuplicatePairs() }.getOrDefault(emptyList())
			val filtered = pairs.filterNot { it.key in dismissedKeys }
			_uiState.update {
				it.copy(
					isScanning = false,
					isAiEnabled = AiMode.fromKey(settings.aiMode) != AiMode.OFF,
					highConfidencePairs = filtered.filter { p -> p.bucket == DuplicateConfidenceBucket.HIGH_CONFIDENCE },
					reviewCarefullyPairs = filtered.filter { p -> p.bucket == DuplicateConfidenceBucket.REVIEW_CAREFULLY },
				)
			}
		}
	}

	fun verifyPairWithAi(pair: DuplicatePairItem) {
		if (AiMode.fromKey(settings.aiMode) == AiMode.OFF) {
			toastManager.show("AI features are turned off")
			return
		}
		if (pair.key in _uiState.value.verifyingPairKeys) return
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.update { it.copy(verifyingPairKeys = it.verifyingPairKeys + pair.key) }
			val res = semanticTitleMatcherProvider.get().verifyDuplicatePair(
				titleA = pair.first.manga.title,
				altTitlesA = pair.first.manga.altTitles,
				titleB = pair.second.manga.title,
				altTitlesB = pair.second.manga.altTitles,
			)
			_uiState.update { state ->
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
		val state = _uiState.value
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
			_uiState.update {
				it.copy(isBatchAiRunning = true, batchAiDone = 0, batchAiTotal = targets.size)
			}
			val matcher = semanticTitleMatcherProvider.get()
			for ((idx, pair) in targets.withIndex()) {
				if (!isActive) break
				_uiState.update { it.copy(verifyingPairKeys = it.verifyingPairKeys + pair.key) }
				val res = matcher.verifyDuplicatePair(
					titleA = pair.first.manga.title,
					altTitlesA = pair.first.manga.altTitles,
					titleB = pair.second.manga.title,
					altTitlesB = pair.second.manga.altTitles,
				)
				_uiState.update { cur ->
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
			_uiState.update { it.copy(isBatchAiRunning = false) }
		}
	}

	fun cancelBatchAi() {
		batchAiJob?.cancel()
		_uiState.update { it.copy(isBatchAiRunning = false) }
	}

	private suspend fun buildDuplicatePairs(): List<DuplicatePairItem> {
		val allManga = favouritesRepository.getAllManga().distinctBy { it.id }
		if (allManga.size < 2) return emptyList()

		val db = databaseProvider.get()
		val chaptersDao = db.getChaptersDao()
		val historyDao = db.getHistoryDao()
		val scrobblingDao = db.getScrobblingDao()
		val categoryTitleMap = favouritesRepository.observeCategories().first().associate { it.id to it.title }

		data class Prepared(
			val summary: DuplicateEntrySummary,
			val isProse: Boolean,
			val normalizedTitles: Set<String>,
			val wordTitles: Set<String>,
			val primaryBigrams: Set<String>,
			val trackerKeys: Set<Long>,
		)

		val prepared = allManga.map { manga ->
			val rawTitles = buildList(manga.altTitles.size + 1) {
				add(manga.title)
				addAll(manga.altTitles)
			}.filter { it.isNotBlank() }

			val normSet = rawTitles.mapNotNullTo(HashSet()) { raw ->
				normalizeAlnum(raw).takeIf { it.length >= 4 }
			}
			val wordSet = rawTitles.mapNotNullTo(HashSet()) { raw ->
				normalizeWords(raw).takeIf { it.length >= 4 }
			}
			val trackerKeys = scrobblingDao.findAll(manga.id).mapTo(HashSet()) { entity ->
				(entity.scrobbler.toLong() shl 48) xor entity.targetId
			}
			val chCount = chaptersDao.count(manga.id)
			val pct = ((historyDao.find(manga.id)?.percent ?: -1f) * 100f).toInt().coerceIn(-1, 100)
			val catNames = favouritesRepository.getCategoriesIds(manga.id).mapNotNull { categoryTitleMap[it] }

			Prepared(
				summary = DuplicateEntrySummary(
					manga = manga,
					sourceTitle = manga.source.getTitle(context),
					chaptersCount = chCount,
					progressPercent = pct,
					categoryTitles = catNames,
				),
				isProse = manga.source.isNovelSource || manga.isEpub,
				normalizedTitles = normSet,
				wordTitles = wordSet,
				primaryBigrams = bigrams(normalizeAlnum(manga.title)),
				trackerKeys = trackerKeys,
			)
		}

		val result = ArrayList<DuplicatePairItem>()
		for (i in 0 until prepared.lastIndex) {
			val a = prepared[i]
			for (j in (i + 1) until prepared.size) {
				val b = prepared[j]
				if (a.isProse != b.isProse) continue

				val sharedTracker = a.trackerKeys.any { it in b.trackerKeys }
				var bucket: DuplicateConfidenceBucket? = null
				var reason = ""

				if (sharedTracker) {
					bucket = DuplicateConfidenceBucket.HIGH_CONFIDENCE
					reason = "Shared Tracker ID"
				} else if (a.normalizedTitles.any { it in b.normalizedTitles }) {
					bucket = DuplicateConfidenceBucket.HIGH_CONFIDENCE
					reason = "Exact Title Match"
				} else {
					val sim = diceSimilarity(a.primaryBigrams, b.primaryBigrams)
					if (sim >= 0.85f) {
						bucket = DuplicateConfidenceBucket.HIGH_CONFIDENCE
						reason = "${(sim * 100).toInt()}% Title Match"
					} else if (sim >= 0.72f) {
						bucket = DuplicateConfidenceBucket.REVIEW_CAREFULLY
						reason = "${(sim * 100).toInt()}% Similar"
					} else if (hasWholeWordContainment(a.wordTitles, b.wordTitles)) {
						bucket = DuplicateConfidenceBucket.REVIEW_CAREFULLY
						reason = "Partial Title Match"
					}
				}

				if (bucket != null) {
					val minId = minOf(a.summary.manga.id, b.summary.manga.id)
					val maxId = maxOf(a.summary.manga.id, b.summary.manga.id)
					result += DuplicatePairItem(
						key = "${minId}_$maxId",
						bucket = bucket,
						reason = reason,
						first = a.summary,
						second = b.summary,
					)
				}
			}
		}
		return result
	}

	private fun hasWholeWordContainment(aWords: Set<String>, bWords: Set<String>): Boolean {
		for (wa in aWords) {
			for (wb in bWords) {
				if (minOf(wa.length, wb.length) >= 4 && (" $wa ".contains(" $wb ") || " $wb ".contains(" $wa "))) {
					return true
				}
			}
		}
		return false
	}

	private fun normalizeAlnum(raw: String): String = buildString(raw.length) {
		val cleaned = raw.lowercase()
			.replace(BRACKETED_REGEX, " ")
			.replace(LEADING_THE_REGEX, "")
		for (c in cleaned) {
			if (c.isLetterOrDigit()) append(c)
		}
	}

	private fun normalizeWords(raw: String): String =
		raw.lowercase()
			.replace(BRACKETED_REGEX, " ")
			.replace(NON_WORD_REGEX, " ")
			.trim()
			.removePrefix("the ")

	private fun bigrams(text: String): Set<String> {
		if (text.length < 2) return emptySet()
		val set = HashSet<String>(text.length)
		for (i in 0 until text.length - 1) {
			set += text.substring(i, i + 2)
		}
		return set
	}

	private fun diceSimilarity(a: Set<String>, b: Set<String>): Float {
		if (a.isEmpty() || b.isEmpty()) return 0f
		var intersection = 0
		for (item in a) {
			if (item in b) intersection++
		}
		return (2f * intersection) / (a.size + b.size)
	}

	private companion object {
		const val MAX_BATCH_AI_PAIRS = 5
		val BRACKETED_REGEX = Regex("""\([^)]*\)|\[[^]]*]""")
		val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
		val LEADING_THE_REGEX = Regex("""^\s*the\s+""")
	}
}
