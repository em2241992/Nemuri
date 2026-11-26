package org.koitharu.kotatsu.favourites.domain

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.isBroken
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.data.FavouriteManga
import org.koitharu.kotatsu.favourites.data.toManga
import org.koitharu.kotatsu.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * A favourite that looks like the manga the user is about to add.
 *
 * @param chaptersCount chapters stored for it locally, or 0 when its details were never fetched
 * @param categories titles of the favourite categories it currently sits in
 * @param progress how far it has been read, in 0..1, or [PROGRESS_NONE] when it was never opened
 * @param canReplace false when migrating onto it would be meaningless — local/imported entries have
 * no remote counterpart to re-key against
 */
class MangaDuplicate(
	val manga: Manga,
	val chaptersCount: Int,
	val categories: List<String>,
	val progress: Float,
	val canReplace: Boolean,
	val editionFlags: Set<EditionFlag> = emptySet(),
	val isEditionVariant: Boolean = false,
	val isRecommendedWinner: Boolean = false,
	val isSourceHealthy: Boolean = true,
	val matchReason: String = "Exact Title Match",
)

/**
 * Edition-aware duplicate detector using [TitleSignatureParser]:
 *
 * 1. Parses clean core titles, sequel/season markers, and edition flags (`COLORED`, `UNCENSORED`,
 *    `ORIGIN_VARIANT`, `SPINOFF`) from titles and tags instead of loose substring `.contains()`.
 * 2. Hard-vetoes `Manga != Novel` (`isEpub`, `isNovelSource`, `LN_*`, `settings.novelSourceIds`),
 *    differing `partOrSequelMarker`, `SPINOFF` mismatches, ignored pairs, and conflicting authors
 *    (unless explicitly linked to the same remote tracker entry).
 * 3. Requires `wordJaccard >= 0.82f` (with short 1-word `<= 10` char exact match rule) and ranks
 *    candidates according to [AppSettings.duplicateWinnerStrategy].
 */
@Reusable
class DuplicatesUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
	private val sourcesRepository: MangaSourcesRepository,
) {

	suspend operator fun invoke(manga: Manga): List<MangaDuplicate> {
		val incomingTags = manga.tags.map { it.title }
		val primarySigIncoming = TitleSignatureParser.parse(manga.title, incomingTags)
		val incomingSigs = manga.allTitles().map { TitleSignatureParser.parse(it, incomingTags) }
		val novelSourceIds = settings.novelSourceIds
		val isIncomingNovel = isNovelWork(manga, novelSourceIds)

		val searchQueries = LinkedHashSet<String>().apply {
			for (raw in manga.allTitles()) {
				val lower = raw.trim().lowercase()
				if (lower.length >= MIN_QUERY_LENGTH) add(lower)
			}
			for (sig in incomingSigs) {
				val core = sig.cleanCoreTitle.trim()
				if (core.length >= MIN_QUERY_LENGTH) add(core)
			}
		}

		val found = HashMap<Long, MutableList<FavouriteManga>>()
		val favouritesDao = db.getFavouritesDao()
		for (query in searchQueries) {
			for (row in favouritesDao.findSimilar(manga.id, query)) {
				found.getOrPut(row.manga.id) { ArrayList(1) }.add(row)
			}
		}

		val trackerLinkedIds = db.getScrobblingDao().findLinkedMangaIds(manga.id).toSet()
		val missingLinkedIds = trackerLinkedIds.filterNot { it in found }
		if (missingLinkedIds.isNotEmpty()) {
			for ((id, rows) in favouritesDao.findByIds(missingLinkedIds).groupBy { it.manga.id }) {
				found[id] = rows.toMutableList()
			}
		}

		if (found.isEmpty()) {
			return emptyList()
		}

		val chaptersDao = db.getChaptersDao()
		val historyDao = db.getHistoryDao()
		val enabledSourceNames = runCatching {
			sourcesRepository.getEnabledSources().mapTo(HashSet()) { it.name }
		}.getOrDefault(emptySet())
		val incomingAuthor = manga.authors.firstOrNull()

		data class EvaluatedCandidate(
			val existing: Manga,
			val chaptersCount: Int,
			val categories: List<String>,
			val progress: Float,
			val canReplace: Boolean,
			val editionFlags: Set<EditionFlag>,
			val isEditionVariant: Boolean,
			val isSourceHealthy: Boolean,
			val matchReason: String,
		)

		val evaluated = found.values.mapNotNull { rows ->
			val existing = rows.first().toManga()
			if (existing.id == manga.id) return@mapNotNull null
			if (settings.isDuplicatePairIgnored(manga.id, existing.id)) return@mapNotNull null

			val isCandidateNovel = isNovelWork(existing, novelSourceIds)
			val existingTags = existing.tags.map { it.title }
			val primarySigCandidate = TitleSignatureParser.parse(existing.title, existingTags)
			if (TitleSignatureParser.isHardVeto(isIncomingNovel, isCandidateNovel, primarySigIncoming, primarySigCandidate)) {
				return@mapNotNull null
			}

			val isSharedTracker = existing.id in trackerLinkedIds
			if (!isSharedTracker &&
				TitleSignatureParser.hasAuthorConflict(incomingAuthor, existing.authors.firstOrNull())
			) {
				return@mapNotNull null
			}

			val candidateSigs = existing.allTitles().map { TitleSignatureParser.parse(it, existingTags) }
			var bestJaccard = 0f
			var exactCoreMatch = false
			for (sigIn in incomingSigs) {
				for (sigCand in candidateSigs) {
					if (TitleSignatureParser.isHardVeto(isIncomingNovel, isCandidateNovel, sigIn, sigCand)) {
						continue
					}
					if (sigIn.cleanCoreTitle.isNotBlank() && sigIn.cleanCoreTitle == sigCand.cleanCoreTitle) {
						exactCoreMatch = true
						bestJaccard = 1f
						break
					}
					val jaccard = TitleSignatureParser.wordJaccard(sigIn, sigCand)
					if (jaccard > bestJaccard) {
						bestJaccard = jaccard
					}
				}
				if (exactCoreMatch) break
			}

			if (!isSharedTracker && bestJaccard < MIN_WORD_JACCARD) {
				return@mapNotNull null
			}

			val isEditionVariant = TitleSignatureParser.hasEditionDifference(primarySigIncoming, primarySigCandidate)
			val matchReason = when {
				isSharedTracker && isEditionVariant -> "Shared Tracker • Edition Variant"
				isSharedTracker -> "Shared Tracker ID"
				isEditionVariant -> "Edition Variant"
				exactCoreMatch -> "Exact Title Match"
				else -> "${(bestJaccard * 100).toInt()}% Title Match"
			}

			val isHealthy = existing.source.isLocal ||
				(!existing.source.isBroken && (enabledSourceNames.isEmpty() || existing.source.name in enabledSourceNames))

			EvaluatedCandidate(
				existing = existing,
				chaptersCount = chaptersDao.count(existing.id),
				categories = rows.mapNotNull { row ->
					row.categories.singleOrNull()?.takeIf { it.deletedAt == 0L }?.title
				}.distinct(),
				progress = historyDao.find(existing.id)?.percent ?: PROGRESS_NONE,
				canReplace = !existing.source.isLocal && !manga.source.isLocal,
				editionFlags = primarySigCandidate.editionFlags,
				isEditionVariant = isEditionVariant,
				isSourceHealthy = isHealthy,
				matchReason = matchReason,
			)
		}

		if (evaluated.isEmpty()) {
			return emptyList()
		}

		val strategy = settings.duplicateWinnerStrategy
		val sorted = evaluated.sortedWith { a, b ->
			// Non-variant matches rank ahead of edition variants; within each group sort by strategy.
			val variantCmp = a.isEditionVariant.compareTo(b.isEditionVariant)
			if (variantCmp != 0) {
				variantCmp
			} else {
				compareByStrategy(
					AnyCandidateMetrics(b.chaptersCount, b.progress, b.isSourceHealthy, b.existing.rating),
					AnyCandidateMetrics(a.chaptersCount, a.progress, a.isSourceHealthy, a.existing.rating),
					strategy,
				)
			}
		}

		val winnerId = sorted.firstOrNull { !it.isEditionVariant }?.existing?.id

		return sorted.map { item ->
			MangaDuplicate(
				manga = item.existing,
				chaptersCount = item.chaptersCount,
				categories = item.categories,
				progress = item.progress,
				canReplace = item.canReplace,
				editionFlags = item.editionFlags,
				isEditionVariant = item.isEditionVariant,
				isRecommendedWinner = winnerId != null && item.existing.id == winnerId,
				isSourceHealthy = item.isSourceHealthy,
				matchReason = item.matchReason,
			)
		}
	}

	private fun compareByStrategy(
		left: AnyCandidateMetrics,
		right: AnyCandidateMetrics,
		strategy: DuplicateWinnerStrategy,
	): Int = when (strategy) {
		DuplicateWinnerStrategy.MOST_CHAPTERS -> compareValuesBy(
			left,
			right,
			{ it.chaptersCount },
			{ it.isSourceHealthy },
			{ it.progress },
			{ it.rating },
		)

		DuplicateWinnerStrategy.FURTHEST_READ -> compareValuesBy(
			left,
			right,
			{ it.progress },
			{ it.chaptersCount },
			{ it.isSourceHealthy },
			{ it.rating },
		)

		DuplicateWinnerStrategy.HEALTHY_SOURCE -> compareValuesBy(
			left,
			right,
			{ it.isSourceHealthy },
			{ it.chaptersCount },
			{ it.progress },
			{ it.rating },
		)

		DuplicateWinnerStrategy.HIGHEST_RATING -> compareValuesBy(
			left,
			right,
			{ it.rating },
			{ it.chaptersCount },
			{ it.isSourceHealthy },
			{ it.progress },
		)
	}

	private data class AnyCandidateMetrics(
		val chaptersCount: Int,
		val progress: Float,
		val isSourceHealthy: Boolean,
		val rating: Float,
	)

	/**
	 * Chapters known for [manga] without asking the network, or `null` when nothing is stored yet.
	 * Used to show a count immediately while [fetchChaptersCount] catches up.
	 */
	suspend fun getLocalChaptersCount(manga: Manga): Int? {
		manga.chapters?.let { return it.size }
		return db.getChaptersDao().count(manga.id).takeIf { it > 0 }
	}

	/**
	 * Chapter count straight from the source. Returns null on any failure — the chapter-difference
	 * arrows are a nicety, never worth surfacing an error over.
	 */
	suspend fun fetchChaptersCount(manga: Manga): Int? = runCatchingCancellable {
		mangaRepositoryFactory.create(manga.source).getDetails(manga).chapters?.size
	}.getOrNull()

	private fun Manga.allTitles(): List<String> = buildList(altTitles.size + 1) {
		add(title)
		addAll(altTitles)
	}.filter { it.isNotBlank() }

	private fun isNovelWork(manga: Manga, novelSourceIds: Set<Long>): Boolean {
		if (manga.isEpub || manga.source.isNovelSource) return true
		val rawName = manga.source.name
		if (rawName.startsWith("LN_", ignoreCase = true) || rawName.equals("LOCAL_EPUB", ignoreCase = true)) {
			return true
		}
		if (rawName.startsWith("MIHON_")) {
			val sourceId = rawName.removePrefix("MIHON_").substringBefore(':').toLongOrNull()
			if (sourceId != null && sourceId in novelSourceIds) return true
		}
		return false
	}

	private companion object {
		const val MIN_QUERY_LENGTH = 3
		const val MIN_WORD_JACCARD = 0.82f
	}
}
