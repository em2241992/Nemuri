package org.koitharu.kotatsu.reader.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.util.TitleNormalizer
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import javax.inject.Inject
import javax.inject.Singleton

data class FailoverNotification(
	val chapterId: Long,
	val chapterNumber: Float,
	val failedSourceName: String,
	val fallbackSourceTitle: String,
	val message: String,
)

@Singleton
class SilentFailoverChapterLoader @Inject constructor(
	private val db: MangaDatabase,
	private val repositoryFactory: MangaRepository.Factory = MangaRepository.Factory(),
) {
	private val _failoverEvents = MutableSharedFlow<FailoverNotification>(extraBufferCapacity = 16)
	val failoverEvents: SharedFlow<FailoverNotification> = _failoverEvents.asSharedFlow()

	/**
	 * Attempts to load pages from the active `chapter.source` via `MangaRepository.Factory`.
	 * If `getPages(chapter)` throws an exception or returns an empty list, automatically queries
	 * `manga_merge_links` and `manga` matching the parent manga's `normalized_slug` or `canonical_id`
	 * (ordered by `priority_weight ASC`), locates the matching canonical chapter key, and silently
	 * fails over while emitting a non-blocking `FailoverNotification`.
	 */
	suspend fun getPagesWithFailover(
		chapter: MangaChapter,
		parentMangaId: Long? = null,
	): List<MangaPage> {
		val primaryPages = runCatching {
			val primaryRepo = repositoryFactory.createAsync(chapter.source)
			primaryRepo.getPages(chapter)
		}.getOrNull()

		if (!primaryPages.isNullOrEmpty()) {
			return primaryPages
		}

		val targetKey = TitleNormalizer.toCanonicalChapterKey(chapter.number, chapter.name)
		if (targetKey < 0) return emptyList()

		val candidates = resolveFallbackCandidates(chapter, parentMangaId)
		for (candidateManga in candidates) {
			val fallbackPages = runCatching {
				val fallbackSource = MangaSource(candidateManga.source)
				val repo = repositoryFactory.createAsync(fallbackSource)
				val domainManga = candidateManga.toDomainManga(fallbackSource)
				val details = repo.getDetails(domainManga)
				val remoteChapters = details.chapters.orEmpty()
				val matchingChapter = remoteChapters.firstOrNull {
					TitleNormalizer.toCanonicalChapterKey(it.number, it.name) == targetKey
				} ?: db.getChaptersDao().findAll(candidateManga.id).map { chEntity ->
					MangaChapter(
						id = chEntity.chapterId,
						name = chEntity.title,
						number = chEntity.number,
						volume = chEntity.volume,
						url = chEntity.url,
						scanlator = chEntity.scanlator,
						uploadDate = chEntity.uploadDate,
						branch = chEntity.branch,
						source = fallbackSource,
					)
				}.firstOrNull {
					TitleNormalizer.toCanonicalChapterKey(it.number, it.name) == targetKey
				} ?: return@runCatching emptyList<MangaPage>()

				val pages = repo.getPages(matchingChapter)
				if (pages.isNotEmpty()) {
					val cleanNumber = if (chapter.number % 1f == 0f) {
						chapter.number.toInt().toString()
					} else {
						chapter.number.toString()
					}
					val msg = "Switched to ${fallbackSource.title} for Ch.$cleanNumber"
					_failoverEvents.tryEmit(
						FailoverNotification(
							chapterId = chapter.id,
							chapterNumber = chapter.number,
							failedSourceName = chapter.source.name,
							fallbackSourceTitle = fallbackSource.title,
							message = msg,
						),
					)
				}
				pages
			}.getOrDefault(emptyList())

			if (fallbackPages.isNotEmpty()) {
				return fallbackPages
			}
		}

		return emptyList()
	}

	private suspend fun resolveFallbackCandidates(
		chapter: MangaChapter,
		explicitParentMangaId: Long?,
	): List<MangaEntity> {
		val allWithTags = db.getMangaDao().getAllWithTags().map { it.manga }
		val byId = allWithTags.associateBy { it.id }

		val resolvedParentId = explicitParentMangaId ?: run {
			allWithTags.firstOrNull { m ->
				db.getChaptersDao().findAll(m.id).any { it.chapterId == chapter.id }
			}?.id
		}

		val orderedCandidates = LinkedHashMap<Long, MangaEntity>()

		if (resolvedParentId != null) {
			val links = db.getAuxiliaryDao().getMergeLinksForParent(resolvedParentId)
			for (link in links) {
				val child = byId[link.childMangaId]
				if (child != null && child.source != chapter.source.name) {
					orderedCandidates[child.id] = child
				}
			}
			val parent = byId[resolvedParentId]
			if (parent != null) {
				val parentSlug = parent.normalizedSlug.ifBlank {
					TitleNormalizer.toNormalizedSlug(parent.title)
				}
				for (other in allWithTags) {
					if (other.id == parent.id || other.source == chapter.source.name) continue
					val otherSlug = other.normalizedSlug.ifBlank {
						TitleNormalizer.toNormalizedSlug(other.title)
					}
					val sameCanonical = !parent.canonicalId.isNullOrBlank() && parent.canonicalId == other.canonicalId
					val sameSlug = parentSlug.isNotBlank() && parentSlug == otherSlug
					if (sameCanonical || sameSlug) {
						orderedCandidates.putIfAbsent(other.id, other)
					}
				}
			}
		} else {
			for (other in allWithTags) {
				if (other.source != chapter.source.name) {
					orderedCandidates.putIfAbsent(other.id, other)
				}
			}
		}

		return orderedCandidates.values.toList()
	}

	private fun MangaEntity.toDomainManga(fallbackSource: MangaSource): Manga = Manga(
		id = id,
		title = title,
		altTitle = altTitles,
		url = url,
		publicUrl = publicUrl,
		rating = rating,
		isNsfw = isNsfw,
		coverUrl = coverUrl,
		largeCoverUrl = largeCoverUrl,
		description = description,
		author = authors,
		source = fallbackSource,
	)
}
