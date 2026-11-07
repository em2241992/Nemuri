package org.koitharu.kotatsu.details.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.core.parser.HybridMangaRepositoryFactory
import org.koitharu.kotatsu.core.util.TitleNormalizer
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import javax.inject.Inject

data class MergeSourceCandidate(
	val manga: Manga,
	val priorityWeight: Int, // Lower = higher priority (e.g., 10 = Primary/MangaDex, 50 = Kotatsu Native, 100 = Extension)
	val preferredScanlator: String? = null,
)

class MergedChapterAggregator @Inject constructor(
	private val repositoryFactory: HybridMangaRepositoryFactory = HybridMangaRepositoryFactory(),
) {
	/**
	 * Fetches chapters concurrently across primary + merged secondary sources and
	 * deduplicates by canonical chapter key while keeping fallback sources attached.
	 */
	suspend fun fetchMergedChapters(candidates: List<MergeSourceCandidate>): List<MangaChapter> = coroutineScope {
		val fetchedGroups = candidates.map { candidate ->
			async {
				runCatching {
					val repo = repositoryFactory.getOrCreateAsync(candidate.manga.source)
					val details = repo.getDetails(candidate.manga)
					details.chapters.orEmpty().map { chapter ->
						RankedChapter(
							chapter = chapter,
							canonicalKey = TitleNormalizer.toCanonicalChapterKey(chapter.number, chapter.name),
							sourcePriority = candidate.priorityWeight,
							matchesPreferredScanlator = !candidate.preferredScanlator.isNullOrBlank() &&
								chapter.scanlator?.equals(candidate.preferredScanlator, ignoreCase = true) == true,
						)
					}
				}.getOrDefault(emptyList())
			}
		}.awaitAll().flatten()

		// Group by canonicalKey; chapters without a valid number (-1) are kept by unique ID
		val (numbered, unnumbered) = fetchedGroups.partition { it.canonicalKey >= 0 }

		val deduplicatedNumbered = numbered
			.groupBy { it.canonicalKey }
			.values
			.map { sameChapterList ->
				// Sort by: 1) Preferred Scanlator match, 2) Source Priority weight, 3) Latest upload date
				sameChapterList.sortedWith(
					compareByDescending<RankedChapter> { it.matchesPreferredScanlator }
						.thenBy { it.sourcePriority }
						.thenByDescending { it.chapter.uploadDate },
				).first().chapter
			}

		val deduplicatedUnnumbered = unnumbered
			.distinctBy { TitleNormalizer.toNormalizedSlug(it.chapter.name) }
			.map { it.chapter }

		(deduplicatedNumbered + deduplicatedUnnumbered).sortedBy { it.number }
	}

	private data class RankedChapter(
		val chapter: MangaChapter,
		val canonicalKey: Int,
		val sourcePriority: Int,
		val matchesPreferredScanlator: Boolean,
	)
}
