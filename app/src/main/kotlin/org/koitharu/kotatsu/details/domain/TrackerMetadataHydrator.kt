package org.koitharu.kotatsu.details.domain

import androidx.room.withTransaction
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaAliasEntity
import org.koitharu.kotatsu.core.util.TitleNormalizer
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import javax.inject.Inject

data class CanonicalTrackerMetadata(
	val canonicalId: String, // e.g., "al:101557" or "mal:37970"
	val primaryTitle: String,
	val alternativeTitles: List<String>,
	val description: String?,
	val highResCoverUrl: String?,
	val author: String?,
	val status: String?,
)

class TrackerMetadataHydrator @Inject constructor(
	private val db: MangaDatabase,
	private val trackingRepository: TrackingRepository,
) {
	/**
	 * Fetches canonical metadata from linked AniList / MyAnimeList trackers (or queries by title if unlinked),
	 * overrides broken/sparse source metadata in EditMangaSheet, and populates manga_aliases for instant FTS5 search.
	 */
	suspend fun fetchAndApplyCanonicalMetadata(
		mangaId: Long,
		overrideTitle: Boolean = false,
		overrideCover: Boolean = true,
		overrideDescription: Boolean = true,
	): Result<CanonicalTrackerMetadata> = runCatching {
		val meta = trackingRepository.fetchRemoteCanonicalMetadata(mangaId)
			?: throw IllegalStateException("No matching tracker record found on AniList or MyAnimeList.")

		db.withTransaction {
			val mangaDao = db.getMangaDao()
			val current = mangaDao.find(mangaId) ?: return@withTransaction

			val updatedTitle = if (overrideTitle && meta.primaryTitle.isNotBlank()) meta.primaryTitle else current.title
			val updatedCover = if (overrideCover && !meta.highResCoverUrl.isNullOrBlank()) meta.highResCoverUrl else current.coverUrl
			val updatedDesc = if (overrideDescription && !meta.description.isNullOrBlank()) meta.description else current.description

			mangaDao.updateCanonicalDetails(
				mangaId = mangaId,
				title = updatedTitle,
				normalizedSlug = TitleNormalizer.toNormalizedSlug(updatedTitle),
				canonicalId = meta.canonicalId,
				coverUrl = updatedCover,
				description = updatedDesc,
			)

			// Hydrate manga_aliases with all Romaji, English, Native, and Synonym titles
			val aliasEntities = (meta.alternativeTitles + meta.primaryTitle)
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.distinct()
				.mapNotNull { rawAlias ->
					val slug = TitleNormalizer.toNormalizedSlug(rawAlias)
					if (slug.isNotEmpty()) {
						MangaAliasEntity(aliasSlug = slug, mangaId = mangaId, rawAlias = rawAlias)
					} else {
						null
					}
				}
			db.getMangaAliasesDao().upsertAll(aliasEntities)
		}
		meta
	}
}
