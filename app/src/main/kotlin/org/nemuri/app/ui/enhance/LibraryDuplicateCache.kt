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
import org.koitharu.kotatsu.core.model.isBroken
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.data.toManga
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.domain.ParsedTitleSignature
import org.koitharu.kotatsu.favourites.domain.TitleSignatureParser
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class DuplicateScanSnapshot(
	val isScanningTier1: Boolean = false,
	val isScanningTier2: Boolean = false,
	val exactPairs: List<DuplicatePairItem> = emptyList(),
	val editionVariantPairs: List<DuplicatePairItem> = emptyList(),
	val needsReviewPairs: List<DuplicatePairItem> = emptyList(),
	val exactPairCount: Int = 0,
	val editionVariantCount: Int = 0,
	val needsReviewCount: Int = 0,
	val lastScanAtMs: Long = 0L,
)

/**
 * Shared singleton edition-aware duplicate-detection engine:
 * 1. Fetches lightweight [FavouritesDao.findDuplicateSeeds] (including author, rating, and tags) +
 *    1-shot [ScrobblingDao.findAllKeys].
 * 2. Parses [TitleSignatureParser] signatures for every title/alias and computes Tier 1 Exact
 *    Duplicates vs Editions & Variants in O(N) hash buckets (`<100ms`), enforcing all hard vetoes
 *    (`Manga != Novel`, sequel/season mismatch, `SPINOFF` mismatch, author conflict, ignored pairs).
 * 3. Computes Tier 2 Needs Review (`wordJaccard >= 0.82f`) via an inverted word-token index on
 *    `Dispatchers.Default` and orders `first` (`★ Winner`) vs `second` by [AppSettings.duplicateWinnerStrategy].
 */
@Singleton
class LibraryDuplicateCache @Inject constructor(
	@ApplicationContext private val context: Context,
	private val databaseProvider: Provider<MangaDatabase>,
	private val favouritesRepository: FavouritesRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
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
			val nextExact = cur.exactPairs.filterNot {
				it.first.manga.id == removedMangaId || it.second.manga.id == removedMangaId
			}
			val nextEditions = cur.editionVariantPairs.filterNot {
				it.first.manga.id == removedMangaId || it.second.manga.id == removedMangaId
			}
			val nextReview = cur.needsReviewPairs.filterNot {
				it.first.manga.id == removedMangaId || it.second.manga.id == removedMangaId
			}
			cur.copy(
				exactPairs = nextExact,
				editionVariantPairs = nextEditions,
				needsReviewPairs = nextReview,
				exactPairCount = nextExact.size,
				editionVariantCount = nextEditions.size,
				needsReviewCount = nextReview.size,
			)
		}
	}

	fun removeIgnoredPair(idA: Long, idB: Long) {
		val minId = min(idA, idB)
		val maxId = max(idA, idB)
		val pairKey = "${minId}_$maxId"
		_snapshot.update { cur ->
			val nextExact = cur.exactPairs.filterNot { it.key == pairKey }
			val nextEditions = cur.editionVariantPairs.filterNot { it.key == pairKey }
			val nextReview = cur.needsReviewPairs.filterNot { it.key == pairKey }
			cur.copy(
				exactPairs = nextExact,
				editionVariantPairs = nextEditions,
				needsReviewPairs = nextReview,
				exactPairCount = nextExact.size,
				editionVariantCount = nextEditions.size,
				needsReviewCount = nextReview.size,
			)
		}
	}

	private class RawPair(
		val idxA: Int,
		val idxB: Int,
		val bucket: DuplicateConfidenceBucket,
		val reason: String,
		val isEditionVariant: Boolean,
	)

	private suspend fun runTwoStageScan() {
		_snapshot.update {
			it.copy(
				isScanningTier1 = true,
				isScanningTier2 = true,
			)
		}

		val db = databaseProvider.get()
		val novelSourceIds = settings.novelSourceIds
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
				exactPairs = emptyList(),
				editionVariantPairs = emptyList(),
				needsReviewPairs = emptyList(),
				exactPairCount = 0,
				editionVariantCount = 0,
				needsReviewCount = 0,
				lastScanAtMs = System.currentTimeMillis(),
			)
			return
		}

		data class PreparedSeed(
			val index: Int,
			val mangaId: Long,
			val isNovel: Boolean,
			val author: String?,
			val primarySig: ParsedTitleSignature,
			val allSigs: List<ParsedTitleSignature>,
			val indexTokens: List<String>,
			val trackers: Set<Long>,
		)

		val prepared = withContext(Dispatchers.Default) {
			seeds.mapIndexed { idx, seed ->
				val source = MangaSource(seed.source, seed.sourceTitle)
				val isMihonNovel = seed.source.startsWith("MIHON_") &&
					seed.source.removePrefix("MIHON_").substringBefore(':').toLongOrNull()
						?.let { it in novelSourceIds } == true
				val isNovel = source.isNovelSource ||
					isMihonNovel ||
					seed.source.startsWith("LN_", ignoreCase = true) ||
					seed.url.endsWith(".epub", ignoreCase = true) ||
					seed.source.equals("LOCAL_EPUB", ignoreCase = true)

				val tags = seed.tagTitles
					?.split('|')
					?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
					.orEmpty()

				val rawTitles = ArrayList<String>(4).apply {
					if (seed.title.isNotBlank()) add(seed.title)
					seed.altTitle?.split('\n', ';', '|')?.forEach { alt ->
						val trimmed = alt.trim()
						if (trimmed.isNotEmpty()) add(trimmed)
					}
				}
				val primarySig = TitleSignatureParser.parse(seed.title, tags)
				val allSigs = if (rawTitles.isEmpty()) {
					listOf(primarySig)
				} else {
					rawTitles.map { TitleSignatureParser.parse(it, tags) }
				}
				val tokenSet = LinkedHashSet<String>()
				for (sig in allSigs) {
					for (tok in sig.wordTokens) {
						if (tok.length >= 2) tokenSet.add(tok)
					}
				}
				PreparedSeed(
					index = idx,
					mangaId = seed.mangaId,
					isNovel = isNovel,
					author = seed.author,
					primarySig = primarySig,
					allSigs = allSigs,
					indexTokens = tokenSet.toList(),
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

		val tier1ExactRaw = ArrayList<RawPair>()
		val tier1EditionRaw = ArrayList<RawPair>()

		withContext(Dispatchers.Default) {
			val trackerBuckets = HashMap<Long, MutableList<Int>>()
			val coreTitleBuckets = HashMap<String, MutableList<Int>>()

			for (p in prepared) {
				for (tKey in p.trackers) {
					trackerBuckets.getOrPut(tKey) { ArrayList(2) }.add(p.index)
				}
				val kind = if (p.isNovel) 'N' else 'M'
				val seenCores = HashSet<String>(p.allSigs.size)
				for (sig in p.allSigs) {
					val core = sig.cleanCoreTitle
					if (core.isNotBlank() && seenCores.add(core)) {
						coreTitleBuckets.getOrPut("$kind:$core") { ArrayList(2) }.add(p.index)
					}
				}
			}

			fun evaluateTier1Group(indices: List<Int>, isTrackerBucket: Boolean) {
				if (indices.size < 2) return
				val capped = if (indices.size > 14) indices.subList(0, 14) else indices
				for (aPos in 0 until capped.lastIndex) {
					val pa = prepared[capped[aPos]]
					for (bPos in (aPos + 1) until capped.size) {
						val pb = prepared[capped[bPos]]
						if (pa.mangaId == pb.mangaId) continue
						if (settings.isDuplicatePairIgnored(pa.mangaId, pb.mangaId)) continue
						if (TitleSignatureParser.isHardVeto(pa.isNovel, pb.isNovel, pa.primarySig, pb.primarySig)) {
							continue
						}
						val sharesTracker = isTrackerBucket ||
							(pa.trackers.isNotEmpty() && pb.trackers.any { it in pa.trackers })
						if (!sharesTracker && TitleSignatureParser.hasAuthorConflict(pa.author, pb.author)) {
							continue
						}
						if (!sharesTracker) {
							val hasValidCoreMatch = pa.allSigs.any { sigA ->
								pb.allSigs.any { sigB ->
									sigA.cleanCoreTitle.isNotBlank() &&
										sigA.cleanCoreTitle == sigB.cleanCoreTitle &&
										!TitleSignatureParser.isHardVeto(pa.isNovel, pb.isNovel, sigA, sigB)
								}
							}
							if (!hasValidCoreMatch) continue
						}

						val key = pairLongKey(pa.mangaId, pb.mangaId)
						if (!seenPairKeys.add(key)) continue

						val hasEditionDiff = TitleSignatureParser.hasEditionDifference(pa.primarySig, pb.primarySig)
						if (hasEditionDiff) {
							val reason = if (sharesTracker) "Shared Tracker • Edition Variant" else "Edition Variant"
							tier1EditionRaw += RawPair(
								idxA = pa.index,
								idxB = pb.index,
								bucket = DuplicateConfidenceBucket.EDITIONS_VARIANTS,
								reason = reason,
								isEditionVariant = true,
							)
						} else {
							val reason = if (sharesTracker) "Shared Tracker ID" else "Exact Title Match"
							tier1ExactRaw += RawPair(
								idxA = pa.index,
								idxB = pb.index,
								bucket = DuplicateConfidenceBucket.EXACT_DUPLICATES,
								reason = reason,
								isEditionVariant = false,
							)
						}
					}
				}
			}

			for (group in trackerBuckets.values) evaluateTier1Group(group, isTrackerBucket = true)
			for (group in coreTitleBuckets.values) evaluateTier1Group(group, isTrackerBucket = false)
		}

		val (categoryTitleMap, enabledSourceNames) = withContext(Dispatchers.IO) {
			val cats = favouritesRepository.observeCategories().first().associate { it.id to it.title }
			val enabled = runCatching {
				sourcesRepository.getEnabledSources().mapTo(HashSet()) { it.name }
			}.getOrDefault(emptySet())
			cats to enabled
		}

		val summaryCache = HashMap<Long, DuplicateEntrySummary>()
		val tier1Combined = tier1ExactRaw + tier1EditionRaw
		if (tier1Combined.isNotEmpty()) {
			val tier1Ids = HashSet<Long>(tier1Combined.size * 2).apply {
				for (r in tier1Combined) {
					add(prepared[r.idxA].mangaId)
					add(prepared[r.idxB].mangaId)
				}
			}
			hydrateSummariesBatch(db, tier1Ids, categoryTitleMap, enabledSourceNames, summaryCache)
		}

		val strategy = settings.duplicateWinnerStrategy
		val mangaIds = prepared.map { it.mangaId }
		val tier1ExactItems = buildPairItems(tier1ExactRaw, mangaIds, summaryCache, strategy)
		val tier1EditionItems = buildPairItems(tier1EditionRaw, mangaIds, summaryCache, strategy)

		_snapshot.update {
			it.copy(
				isScanningTier1 = false,
				isScanningTier2 = true,
				exactPairs = tier1ExactItems,
				editionVariantPairs = tier1EditionItems,
				exactPairCount = tier1ExactItems.size,
				editionVariantCount = tier1EditionItems.size,
				lastScanAtMs = System.currentTimeMillis(),
			)
		}

		// --- Tier 2: Inverted word-token index on Dispatchers.Default (wordJaccard >= 0.82f) ---
		val tier2EditionRaw = ArrayList<RawPair>()
		val tier2ReviewRaw = ArrayList<RawPair>()
		withContext(Dispatchers.Default) {
			val tokenIndex = HashMap<String, MutableList<Int>>()
			for (p in prepared) {
				val kind = if (p.isNovel) 'N' else 'M'
				for (tok in p.indexTokens) {
					tokenIndex.getOrPut("$kind:$tok") { ArrayList(2) }.add(p.index)
				}
			}

			val candidatePairs = HashSet<Long>(256)
			fun evaluateFuzzyPair(idxA: Int, idxB: Int) {
				if (idxA == idxB) return
				val lo = min(idxA, idxB)
				val hi = max(idxA, idxB)
				val pack = (lo.toLong() shl 24) or hi.toLong()
				if (!candidatePairs.add(pack)) return

				val pa = prepared[lo]
				val pb = prepared[hi]
				if (settings.isDuplicatePairIgnored(pa.mangaId, pb.mangaId)) return
				val idKey = pairLongKey(pa.mangaId, pb.mangaId)
				if (idKey in seenPairKeys) return

				if (TitleSignatureParser.isHardVeto(pa.isNovel, pb.isNovel, pa.primarySig, pb.primarySig)) {
					return
				}
				if (TitleSignatureParser.hasAuthorConflict(pa.author, pb.author)) {
					return
				}

				var bestJaccard = 0f
				for (sigA in pa.allSigs) {
					for (sigB in pb.allSigs) {
						if (TitleSignatureParser.isHardVeto(pa.isNovel, pb.isNovel, sigA, sigB)) continue
						val j = TitleSignatureParser.wordJaccard(sigA, sigB)
						if (j > bestJaccard) bestJaccard = j
					}
				}

				if (bestJaccard < MIN_FUZZY_JACCARD) return
				seenPairKeys.add(idKey)

				val hasEditionDiff = TitleSignatureParser.hasEditionDifference(pa.primarySig, pb.primarySig)
				val pct = (bestJaccard * 100f).toInt()
				if (hasEditionDiff) {
					tier2EditionRaw += RawPair(
						idxA = lo,
						idxB = hi,
						bucket = DuplicateConfidenceBucket.EDITIONS_VARIANTS,
						reason = "Edition Variant ($pct% Match)",
						isEditionVariant = true,
					)
				} else {
					tier2ReviewRaw += RawPair(
						idxA = lo,
						idxB = hi,
						bucket = DuplicateConfidenceBucket.NEEDS_REVIEW,
						reason = "$pct% Title Match",
						isEditionVariant = false,
					)
				}
			}

			for (group in tokenIndex.values) {
				if (group.size in 2..MAX_TOKEN_BUCKET_SIZE) {
					for (i in 0 until group.lastIndex) {
						for (j in (i + 1) until group.size) {
							evaluateFuzzyPair(group[i], group[j])
						}
					}
				}
			}
		}

		val tier2Combined = tier2EditionRaw + tier2ReviewRaw
		if (tier2Combined.isNotEmpty()) {
			val tier2Ids = HashSet<Long>(tier2Combined.size * 2).apply {
				for (r in tier2Combined) {
					val idA = prepared[r.idxA].mangaId
					val idB = prepared[r.idxB].mangaId
					if (idA !in summaryCache) add(idA)
					if (idB !in summaryCache) add(idB)
				}
			}
			if (tier2Ids.isNotEmpty()) {
				hydrateSummariesBatch(db, tier2Ids, categoryTitleMap, enabledSourceNames, summaryCache)
			}
		}

		val latestStrategy = settings.duplicateWinnerStrategy
		val allEditionItems = tier1EditionItems + buildPairItems(tier2EditionRaw, mangaIds, summaryCache, latestStrategy)
		val allReviewItems = buildPairItems(tier2ReviewRaw, mangaIds, summaryCache, latestStrategy)

		_snapshot.value = DuplicateScanSnapshot(
			isScanningTier1 = false,
			isScanningTier2 = false,
			exactPairs = tier1ExactItems,
			editionVariantPairs = allEditionItems,
			needsReviewPairs = allReviewItems,
			exactPairCount = tier1ExactItems.size,
			editionVariantCount = allEditionItems.size,
			needsReviewCount = allReviewItems.size,
			lastScanAtMs = System.currentTimeMillis(),
		)
	}

	private suspend fun hydrateSummariesBatch(
		db: MangaDatabase,
		mangaIds: Set<Long>,
		categoryTitleMap: Map<Long, String>,
		enabledSourceNames: Set<String>,
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
			val sig = TitleSignatureParser.parse(manga.title, manga.tags.map { it.title })
			val isHealthy = manga.source.isLocal ||
				(!manga.source.isBroken && (enabledSourceNames.isEmpty() || manga.source.name in enabledSourceNames))
			outCache[mId] = DuplicateEntrySummary(
				manga = manga,
				sourceTitle = manga.source.getTitle(context),
				chaptersCount = chCountByManga[mId] ?: 0,
				progressPercent = pctByManga[mId] ?: -1,
				categoryTitles = catNames,
				editionFlags = sig.editionFlags,
				isSourceHealthy = isHealthy,
				rating = manga.rating,
			)
		}
	}

	private fun buildPairItems(
		raw: List<RawPair>,
		mangaIds: List<Long>,
		summaryCache: Map<Long, DuplicateEntrySummary>,
		strategy: org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy,
	): List<DuplicatePairItem> {
		if (raw.isEmpty()) return emptyList()
		return raw.mapNotNull { candidate ->
			val idA = mangaIds.getOrNull(candidate.idxA) ?: return@mapNotNull null
			val idB = mangaIds.getOrNull(candidate.idxB) ?: return@mapNotNull null
			if (settings.isDuplicatePairIgnored(idA, idB)) return@mapNotNull null
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
				isEditionVariant = candidate.isEditionVariant,
			).orderedBy(strategy)
		}
	}

	private companion object {
		const val SQL_CHUNK_SIZE = 900
		const val CACHE_TTL_MS = 180_000L
		const val MAX_TOKEN_BUCKET_SIZE = 80
		const val MIN_FUZZY_JACCARD = 0.82f
	}
}
