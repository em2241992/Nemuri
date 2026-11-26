package org.nemuri.app.ui.enhance

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.favourites.data.toManga
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class DuplicateScanSnapshot(
	val isScanningTier1: Boolean = false,
	val isScanningTier2: Boolean = false,
	val highConfidencePairs: List<DuplicatePairItem> = emptyList(),
	val reviewCarefullyPairs: List<DuplicatePairItem> = emptyList(),
	val exactPairCount: Int = 0,
	val lastScanAtMs: Long = 0L,
)

/**
 * Shared singleton duplicate-detection engine eliminating `MangaWithTags` joins and `N+1`
 * per-manga queries:
 * 1. Fetches lightweight [FavouritesDao.findDuplicateSeeds] + 1-shot [ScrobblingDao.findAllKeys].
 * 2. Computes Tier 1 exact & tracker matches in O(N) hash buckets (`<100ms`) and hydrates ONLY
 *    matched IDs via chunked batch queries, publishing High Confidence results immediately.
 * 3. Progressively computes Tier 2 fuzzy/partial matches using an inverted word-token index &
 *    length-pruned bigram Dice similarity on `Dispatchers.Default`.
 */
@Singleton
class LibraryDuplicateCache @Inject constructor(
	@ApplicationContext private val context: Context,
	private val databaseProvider: Provider<MangaDatabase>,
	private val favouritesRepository: FavouritesRepository,
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val scanMutex = Mutex()
	private var activeScanJob: Job? = null

	private val _snapshot = MutableStateFlow(DuplicateScanSnapshot())
	val snapshot: StateFlow<DuplicateScanSnapshot> = _snapshot.asStateFlow()

	fun ensureScanned(forceRefresh: Boolean = false) {
		val current = _snapshot.value
		val ageMs = System.currentTimeMillis() - current.lastScanAtMs
		if (!forceRefresh && (current.isScanningTier1 || current.isScanningTier2 || (current.lastScanAtMs > 0L && ageMs < CACHE_TTL_MS))) {
			return
		}
		activeScanJob?.cancel()
		activeScanJob = scope.launch {
			runCatchingCancellable { runTwoStageScan() }
		}
	}

	suspend fun refreshNow() {
		activeScanJob?.cancel()
		scanMutex.withLock {
			runTwoStageScan()
		}
	}

	fun removeMergedManga(removedMangaId: Long) {
		_snapshot.update { cur ->
			val nextHigh = cur.highConfidencePairs.filterNot {
				it.first.manga.id == removedMangaId || it.second.manga.id == removedMangaId
			}
			val nextReview = cur.reviewCarefullyPairs.filterNot {
				it.first.manga.id == removedMangaId || it.second.manga.id == removedMangaId
			}
			cur.copy(
				highConfidencePairs = nextHigh,
				reviewCarefullyPairs = nextReview,
				exactPairCount = nextHigh.size,
			)
		}
	}

	private class RawPair(
		val idxA: Int,
		val idxB: Int,
		val bucket: DuplicateConfidenceBucket,
		val reason: String,
	)

	private suspend fun runTwoStageScan() {
		_snapshot.update {
			it.copy(
				isScanningTier1 = true,
				isScanningTier2 = true,
			)
		}

		val db = databaseProvider.get()
		val (seeds, trackerByManga) = withContext(Dispatchers.IO) {
			val favSeeds = db.getFavouritesDao().findDuplicateSeeds()
			val scrobbleRows = db.getScrobblingDao().findAllKeys()
			val trackerMap = HashMap<Long, MutableSet<Long>>()
			for (row in scrobbleRows) {
				val key = (row.scrobbler.toLong() shl 48) xor row.targetId
				trackerMap.getOrPut(row.mangaId) { HashSet(2) }.add(key)
			}
			favSeeds to trackerMap
		}

		if (seeds.size < 2) {
			_snapshot.value = DuplicateScanSnapshot(
				isScanningTier1 = false,
				isScanningTier2 = false,
				highConfidencePairs = emptyList(),
				reviewCarefullyPairs = emptyList(),
				exactPairCount = 0,
				lastScanAtMs = System.currentTimeMillis(),
			)
			return
		}

		// --- Tier 1: <100ms O(N) hash-bucket exact title & tracker matching ---
		data class PreparedSeed(
			val index: Int,
			val mangaId: Long,
			val isProse: Boolean,
			val primaryNorm: String,
			val normSet: Set<String>,
			val wordTokens: List<String>,
			val wordTextList: List<String>,
			val bigrams: Set<String>,
			val trackers: Set<Long>,
		)

		val prepared = withContext(Dispatchers.Default) {
			seeds.mapIndexed { idx, seed ->
				val source = MangaSource(seed.source, seed.sourceTitle)
				val isProse = source.isNovelSource ||
					seed.url.endsWith(".epub", ignoreCase = true) ||
					seed.source.equals("LOCAL_EPUB", ignoreCase = true)

				val rawTitles = ArrayList<String>(4).apply {
					if (seed.title.isNotBlank()) add(seed.title)
					seed.altTitle?.split('\n', ';', '|')?.forEach { alt ->
						val trimmed = alt.trim()
						if (trimmed.isNotEmpty()) add(trimmed)
					}
				}
				val normSet = HashSet<String>(rawTitles.size)
				val wordTextList = ArrayList<String>(rawTitles.size)
				val tokenList = ArrayList<String>(8)
				var primaryNorm = ""
				for ((tIdx, raw) in rawTitles.withIndex()) {
					val n = normalizeAlnum(raw)
					if (tIdx == 0) primaryNorm = n
					if (n.length >= 4) normSet.add(n)
					val w = normalizeWords(raw)
					if (w.length >= 4) {
						wordTextList.add(w)
						for (tok in w.split(' ')) {
							if (tok.length >= 3 && tok !in STOP_WORDS) {
								tokenList.add(tok)
							}
						}
					}
				}
				PreparedSeed(
					index = idx,
					mangaId = seed.mangaId,
					isProse = isProse,
					primaryNorm = primaryNorm,
					normSet = normSet,
					wordTokens = tokenList.distinct(),
					wordTextList = wordTextList,
					bigrams = bigrams(primaryNorm),
					trackers = trackerByManga[seed.mangaId].orEmpty(),
				)
			}
		}

		val seenPairKeys = HashSet<Long>()
		fun pairLongKey(idA: Long, idB: Long): Long {
			val lo = min(idA, idB)
			val hi = max(idA, idB)
			return (lo * 1_000_003L) xor hi
		}

		val tier1Raw = ArrayList<RawPair>()
		withContext(Dispatchers.Default) {
			val trackerBuckets = HashMap<Long, MutableList<Int>>()
			val titleBuckets = HashMap<String, MutableList<Int>>()

			for (p in prepared) {
				for (tKey in p.trackers) {
					trackerBuckets.getOrPut(tKey) { ArrayList(2) }.add(p.index)
				}
				for (nTitle in p.normSet) {
					titleBuckets.getOrPut("${if (p.isProse) 'N' else 'M'}:$nTitle") { ArrayList(2) }.add(p.index)
				}
			}

			fun collectBucketPairs(indices: List<Int>, reason: String) {
				if (indices.size < 2) return
				val capped = if (indices.size > 12) indices.subList(0, 12) else indices
				for (aPos in 0 until capped.lastIndex) {
					val pa = prepared[capped[aPos]]
					for (bPos in (aPos + 1) until capped.size) {
						val pb = prepared[capped[bPos]]
						if (pa.isProse != pb.isProse) continue
						val key = pairLongKey(pa.mangaId, pb.mangaId)
						if (seenPairKeys.add(key)) {
							tier1Raw += RawPair(pa.index, pb.index, DuplicateConfidenceBucket.HIGH_CONFIDENCE, reason)
						}
					}
				}
			}

			for (group in trackerBuckets.values) collectBucketPairs(group, "Shared Tracker ID")
			for (group in titleBuckets.values) collectBucketPairs(group, "Exact Title Match")
		}

		// Hydrate ONLY Tier 1 matched manga in batch queries and publish immediately
		val categoryTitleMap = withContext(Dispatchers.IO) {
			favouritesRepository.observeCategories().first().associate { it.id to it.title }
		}
		val summaryCache = HashMap<Long, DuplicateEntrySummary>()
		if (tier1Raw.isNotEmpty()) {
			val tier1Ids = HashSet<Long>(tier1Raw.size * 2).apply {
				for (r in tier1Raw) {
					add(prepared[r.idxA].mangaId)
					add(prepared[r.idxB].mangaId)
				}
			}
			hydrateSummariesBatch(db, tier1Ids, categoryTitleMap, summaryCache)
		}

		val tier1Items = buildPairItems(tier1Raw, prepared.map { it.mangaId }, summaryCache)
		_snapshot.update {
			it.copy(
				isScanningTier1 = false,
				isScanningTier2 = true,
				highConfidencePairs = tier1Items,
				exactPairCount = tier1Items.size,
				lastScanAtMs = System.currentTimeMillis(),
			)
		}

		// --- Tier 2: Progressive indexed fuzzy & partial containment scan on Dispatchers.Default ---
		val tier2Raw = ArrayList<RawPair>()
		val extraHighRaw = ArrayList<RawPair>()
		withContext(Dispatchers.Default) {
			val tokenIndex = HashMap<String, MutableList<Int>>()
			val lengthBuckets = HashMap<String, MutableList<Int>>()
			for (p in prepared) {
				val kind = if (p.isProse) 'N' else 'M'
				for (tok in p.wordTokens) {
					tokenIndex.getOrPut("$kind:$tok") { ArrayList(2) }.add(p.index)
				}
				val lenBucket = p.primaryNorm.length / 4
				if (p.primaryNorm.length >= 5) {
					for (delta in -1..1) {
						lengthBuckets.getOrPut("$kind:${lenBucket + delta}") { ArrayList(4) }.add(p.index)
					}
				}
			}

			val candidatePairs = HashSet<Long>(256)
			fun evaluateCandidatePair(idxA: Int, idxB: Int) {
				if (idxA == idxB) return
				val lo = min(idxA, idxB)
				val hi = max(idxA, idxB)
				val pack = (lo.toLong() shl 24) or hi.toLong()
				if (!candidatePairs.add(pack)) return

				val pa = prepared[lo]
				val pb = prepared[hi]
				if (pa.isProse != pb.isProse) return
				val idKey = pairLongKey(pa.mangaId, pb.mangaId)
				if (idKey in seenPairKeys) return

				val sim = diceSimilarity(pa.bigrams, pb.bigrams)
				when {
					sim >= 0.85f -> {
						seenPairKeys.add(idKey)
						extraHighRaw += RawPair(lo, hi, DuplicateConfidenceBucket.HIGH_CONFIDENCE, "${(sim * 100).toInt()}% Title Match")
					}
					sim >= 0.72f -> {
						seenPairKeys.add(idKey)
						tier2Raw += RawPair(lo, hi, DuplicateConfidenceBucket.REVIEW_CAREFULLY, "${(sim * 100).toInt()}% Similar")
					}
					hasWholeWordContainment(pa.wordTokens, pb.wordTokens, pa.wordTextList, pb.wordTextList) -> {
						seenPairKeys.add(idKey)
						tier2Raw += RawPair(lo, hi, DuplicateConfidenceBucket.REVIEW_CAREFULLY, "Partial Title Match")
					}
				}
			}

			for (group in tokenIndex.values) {
				if (group.size in 2..MAX_TOKEN_BUCKET_SIZE) {
					for (i in 0 until group.lastIndex) {
						for (j in (i + 1) until group.size) {
							evaluateCandidatePair(group[i], group[j])
						}
					}
				}
			}
			for (group in lengthBuckets.values) {
				if (group.size in 2..MAX_LENGTH_BUCKET_SIZE) {
					for (i in 0 until group.lastIndex) {
						for (j in (i + 1) until group.size) {
							evaluateCandidatePair(group[i], group[j])
						}
					}
				}
			}
		}

		val tier2Ids = HashSet<Long>(tier2Raw.size * 2 + extraHighRaw.size * 2).apply {
			for (r in extraHighRaw) {
				val idA = prepared[r.idxA].mangaId
				val idB = prepared[r.idxB].mangaId
				if (idA !in summaryCache) add(idA)
				if (idB !in summaryCache) add(idB)
			}
			for (r in tier2Raw) {
				val idA = prepared[r.idxA].mangaId
				val idB = prepared[r.idxB].mangaId
				if (idA !in summaryCache) add(idA)
				if (idB !in summaryCache) add(idB)
			}
		}
		if (tier2Ids.isNotEmpty()) {
			hydrateSummariesBatch(db, tier2Ids, categoryTitleMap, summaryCache)
		}

		val mangaIds = prepared.map { it.mangaId }
		val allHigh = tier1Items + buildPairItems(extraHighRaw, mangaIds, summaryCache)
		val allReview = buildPairItems(tier2Raw, mangaIds, summaryCache)

		_snapshot.value = DuplicateScanSnapshot(
			isScanningTier1 = false,
			isScanningTier2 = false,
			highConfidencePairs = allHigh,
			reviewCarefullyPairs = allReview,
			exactPairCount = allHigh.size,
			lastScanAtMs = System.currentTimeMillis(),
		)
	}

	private suspend fun hydrateSummariesBatch(
		db: MangaDatabase,
		mangaIds: Set<Long>,
		categoryTitleMap: Map<Long, String>,
		outCache: MutableMap<Long, DuplicateEntrySummary>,
	) = withContext(Dispatchers.IO) {
		val favDao = db.getFavouritesDao()
		val chaptersDao = db.getChaptersDao()
		val historyDao = db.getHistoryDao()

		val favsByManga = HashMap<Long, org.koitharu.kotatsu.parsers.model.Manga>(mangaIds.size)
		val catIdsByManga = HashMap<Long, MutableSet<Long>>(mangaIds.size)
		val chCountByManga = HashMap<Long, Int>(mangaIds.size)
		val pctByManga = HashMap<Long, Int>(mangaIds.size)

		for (chunk in mangaIds.chunked(SQL_CHUNK_SIZE)) {
			val favRows = favDao.findByIds(chunk)
			for (row in favRows) {
				val mId = row.manga.id
				favsByManga.putIfAbsent(mId, row.toManga())
				val catSet = catIdsByManga.getOrPut(mId) { LinkedHashSet() }
				row.categories.forEach { catSet.add(it.categoryId.toLong()) }
			}
			for (chRow in chaptersDao.countByMangaIds(chunk)) {
				chCountByManga[chRow.mangaId] = chRow.chaptersCount
			}
			for (hRow in historyDao.findPercentByIds(chunk)) {
				pctByManga[hRow.mangaId] = (hRow.percent * 100f).toInt().coerceIn(-1, 100)
			}
		}

		for ((mId, manga) in favsByManga) {
			val catNames = catIdsByManga[mId].orEmpty().mapNotNull { categoryTitleMap[it] }
			outCache[mId] = DuplicateEntrySummary(
				manga = manga,
				sourceTitle = manga.source.getTitle(context),
				chaptersCount = chCountByManga[mId] ?: 0,
				progressPercent = pctByManga[mId] ?: -1,
				categoryTitles = catNames,
			)
		}
	}

	private fun buildPairItems(
		raw: List<RawPair>,
		mangaIds: List<Long>,
		summaryCache: Map<Long, DuplicateEntrySummary>,
	): List<DuplicatePairItem> {
		if (raw.isEmpty()) return emptyList()
		return raw.mapNotNull { candidate ->
			val idA = mangaIds.getOrNull(candidate.idxA) ?: return@mapNotNull null
			val idB = mangaIds.getOrNull(candidate.idxB) ?: return@mapNotNull null
			val sumA = summaryCache[idA] ?: return@mapNotNull null
			val sumB = summaryCache[idB] ?: return@mapNotNull null
			val minId = min(idA, idB)
			val maxId = max(idA, idB)
			DuplicatePairItem(
				key = "${minId}_$maxId",
				bucket = candidate.bucket,
				reason = candidate.reason,
				first = sumA,
				second = sumB,
			)
		}
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

	private fun hasWholeWordContainment(
		tokensA: List<String>,
		tokensB: List<String>,
		wordsA: List<String>,
		wordsB: List<String>,
	): Boolean {
		if (tokensA.isEmpty() || tokensB.isEmpty()) return false
		var shared = 0
		for (t in tokensA) {
			if (t in tokensB) {
				shared++
				if (shared >= 2) break
			}
		}
		if (shared < 2 && min(tokensA.size, tokensB.size) > 1) return false
		for (wa in wordsA) {
			for (wb in wordsB) {
				if (min(wa.length, wb.length) >= 5 && (wa == wb || wa.contains(" $wb ") || wb.contains(" $wa ") ||
						wa.startsWith("$wb ") || wa.endsWith(" $wb") ||
						wb.startsWith("$wa ") || wb.endsWith(" $wa"))
				) {
					return true
				}
			}
		}
		return false
	}

	private companion object {
		const val SQL_CHUNK_SIZE = 900
		const val CACHE_TTL_MS = 180_000L
		const val MAX_TOKEN_BUCKET_SIZE = 64
		const val MAX_LENGTH_BUCKET_SIZE = 120
		val BRACKETED_REGEX = Regex("""\([^)]*\)|\[[^]]*]""")
		val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
		val LEADING_THE_REGEX = Regex("""^\s*the\s+""")
		val STOP_WORDS = setOf(
			"the", "and", "for", "with", "into", "from", "that", "this",
			"vol", "volume", "chapter", "part", "story", "tales", "manga", "novel",
		)
	}
}
