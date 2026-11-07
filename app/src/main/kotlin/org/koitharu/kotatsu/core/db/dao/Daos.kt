package org.koitharu.kotatsu.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.core.db.entity.BookmarkEntity
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.DuplicateIgnoreEntity
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.LocalMangaIndexEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.MangaPrefsEntity
import org.koitharu.kotatsu.core.db.entity.MangaSourceEntity
import org.koitharu.kotatsu.core.db.entity.MangaTagsEntity
import org.koitharu.kotatsu.core.db.entity.MangaWithTags
import org.koitharu.kotatsu.core.db.entity.ScrobblingEntity
import org.koitharu.kotatsu.core.db.entity.StatsEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TrackEntity

@Dao
interface MangaDao {
	@RawQuery
	suspend fun findByRawQuery(query: SupportSQLiteQuery): List<MangaEntity>

	@Transaction
	@Query("SELECT * FROM manga ORDER BY title COLLATE NOCASE ASC")
	fun observeAllMangaWithTags(): Flow<List<MangaWithTags>>

	@Transaction
	@Query("SELECT * FROM manga WHERE manga_id = :id")
	suspend fun find(id: Long): MangaWithTags?

	@Transaction
	@Query("SELECT * FROM manga")
	suspend fun getAllWithTags(): List<MangaWithTags>

	@Transaction
	@Query(
		"""
		SELECT manga.* FROM manga
		JOIN manga_fts ON manga.rowid = manga_fts.rowid
		WHERE manga_fts MATCH :ftsQuery
		"""
	)
	suspend fun searchFts(ftsQuery: String): List<MangaWithTags>

	@Query(
		"""
		SELECT manga.manga_id FROM manga
		JOIN manga_fts ON manga.rowid = manga_fts.rowid
		WHERE manga_fts MATCH :ftsQuery
		"""
	)
	suspend fun searchFtsIds(ftsQuery: String): List<Long>

	@Query(
		"""
		SELECT manga_id FROM manga
		WHERE normalized_title LIKE '%' || :normQuery || '%'
		   OR title LIKE '%' || :rawQuery || '%'
		   OR alt_title LIKE '%' || :rawQuery || '%'
		   OR author LIKE '%' || :rawQuery || '%'
		"""
	)
	suspend fun searchIdsBySubstring(normQuery: String, rawQuery: String): List<Long>

	@Transaction
	@Query(
		"""
		SELECT * FROM manga
		WHERE normalized_title = :normalizedTitle
		"""
	)
	suspend fun findByNormalizedTitle(normalizedTitle: String): List<MangaWithTags>

	@Upsert
	suspend fun upsertManga(manga: MangaEntity)

	@Upsert
	suspend fun upsertTags(tags: List<TagEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertTagRelations(relations: List<MangaTagsEntity>)

	@Query("DELETE FROM manga_tags WHERE manga_id = :mangaId")
	suspend fun clearTagRelations(mangaId: Long)

	@Transaction
	suspend fun upsert(manga: MangaEntity, tags: List<TagEntity> = emptyList()) {
		upsertManga(manga)
		if (tags.isNotEmpty()) {
			upsertTags(tags)
			clearTagRelations(manga.id)
			insertTagRelations(tags.map { MangaTagsEntity(manga.id, it.id) })
		}
	}

	@Query(
		"""
		UPDATE manga
		SET title = :title,
			normalized_slug = :normalizedSlug,
			canonical_id = :canonicalId,
			cover_url = :coverUrl,
			description = :description
		WHERE manga_id = :mangaId
		"""
	)
	suspend fun updateCanonicalDetails(
		mangaId: Long,
		title: String,
		normalizedSlug: String,
		canonicalId: String?,
		coverUrl: String,
		description: String,
	)

	@Query("DELETE FROM manga WHERE manga_id = :mangaId")
	suspend fun deleteById(mangaId: Long)

	@Query("DELETE FROM manga_tags WHERE manga_id NOT IN (SELECT manga_id FROM manga)")
	suspend fun pruneOrphanedTagRelations(): Int

	@Query("DELETE FROM tags WHERE tag_id NOT IN (SELECT DISTINCT tag_id FROM manga_tags)")
	suspend fun pruneOrphanedTags(): Int
}

@Dao
interface ChaptersDao {
	@Query("SELECT * FROM chapters WHERE manga_id = :mangaId ORDER BY number ASC")
	fun observeChapters(mangaId: Long): Flow<List<ChapterEntity>>

	@Query("SELECT * FROM chapters WHERE manga_id = :mangaId ORDER BY number ASC")
	suspend fun findAll(mangaId: Long): List<ChapterEntity>

	@Query("SELECT * FROM chapters")
	fun observeAllChapters(): Flow<List<ChapterEntity>>

	@Query("SELECT * FROM chapters")
	suspend fun getAllChapters(): List<ChapterEntity>

	@Upsert
	suspend fun upsertAll(chapters: List<ChapterEntity>)

	@Query("UPDATE chapters SET is_read = :isRead, last_page_read = :lastPage WHERE manga_id = :mangaId AND chapter_id = :chapterId")
	suspend fun updateReadStatus(mangaId: Long, chapterId: Long, isRead: Boolean, lastPage: Int)

	@Query("UPDATE chapters SET is_read = :isRead, last_page_read = CASE WHEN :isRead THEN 18 ELSE 0 END WHERE manga_id = :mangaId")
	suspend fun updateAllReadStatusForManga(mangaId: Long, isRead: Boolean)

	@Query("UPDATE chapters SET is_downloaded = :isDownloaded WHERE manga_id = :mangaId")
	suspend fun updateDownloadedStatusForManga(mangaId: Long, isDownloaded: Boolean)

	@Query("UPDATE chapters SET is_downloaded = 0")
	suspend fun clearAllDownloadedChapters(): Int

	@Query("DELETE FROM chapters WHERE manga_id = :mangaId")
	suspend fun deleteByMangaId(mangaId: Long)

	@Query("DELETE FROM chapters WHERE manga_id NOT IN (SELECT manga_id FROM manga)")
	suspend fun pruneOrphanedChapters(): Int
}

@Dao
interface FavouritesDao {
	@Query("SELECT * FROM favourites WHERE deleted_at = 0 ORDER BY created_at DESC")
	fun observeAllFavourites(): Flow<List<FavouriteEntity>>

	@Query("SELECT * FROM favourites WHERE deleted_at = 0")
	suspend fun getAllFavourites(): List<FavouriteEntity>

	@Query("SELECT * FROM favourites WHERE manga_id = :mangaId AND deleted_at = 0")
	suspend fun findForManga(mangaId: Long): List<FavouriteEntity>

	@Upsert
	suspend fun upsert(favourite: FavouriteEntity)

	@Upsert
	suspend fun upsertAll(favourites: List<FavouriteEntity>)

	@Query("DELETE FROM favourites WHERE manga_id = :mangaId")
	suspend fun deleteForManga(mangaId: Long)

	@Query("SELECT * FROM favourite_categories WHERE deleted_at = 0 ORDER BY sort_key ASC")
	fun observeCategories(): Flow<List<FavouriteCategoryEntity>>

	@Query("SELECT * FROM favourite_categories WHERE deleted_at = 0 ORDER BY sort_key ASC")
	suspend fun getCategories(): List<FavouriteCategoryEntity>

	@Upsert
	suspend fun upsertCategory(category: FavouriteCategoryEntity)

	@Upsert
	suspend fun upsertCategories(categories: List<FavouriteCategoryEntity>)

	@Query("DELETE FROM favourite_categories WHERE category_id = :categoryId")
	suspend fun deleteCategory(categoryId: Int)
}

@Dao
interface HistoryDao {
	@Query("SELECT * FROM history WHERE deleted_at = 0 ORDER BY updated_at DESC")
	fun observeAllHistory(): Flow<List<HistoryEntity>>

	@Query("SELECT * FROM history WHERE deleted_at = 0 ORDER BY updated_at DESC")
	suspend fun getAllHistory(): List<HistoryEntity>

	@Query("SELECT * FROM history WHERE manga_id = :mangaId LIMIT 1")
	suspend fun findByMangaId(mangaId: Long): HistoryEntity?

	@Query("SELECT * FROM history WHERE manga_id = :mangaId LIMIT 1")
	suspend fun find(mangaId: Long): HistoryEntity?

	@Upsert
	suspend fun upsert(history: HistoryEntity)

	@Upsert
	suspend fun upsertAll(historyList: List<HistoryEntity>)

	@Query("DELETE FROM history WHERE manga_id = :mangaId")
	suspend fun deleteByMangaId(mangaId: Long)

	@Query("DELETE FROM history")
	suspend fun clearAllHistory()
}

@Dao
interface ChaptersHistoryDao {
	@Upsert
	suspend fun upsert(entry: org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity)

	@Query("SELECT COUNT(*) FROM chapters_history WHERE manga_id = :mangaId")
	suspend fun countReadChapters(mangaId: Long): Int

	@Query("SELECT chapter_id FROM chapters_history WHERE manga_id = :mangaId")
	suspend fun findReadChapterIds(mangaId: Long): List<Long>

	@Query("DELETE FROM chapters_history WHERE manga_id = :mangaId")
	suspend fun deleteByMangaId(mangaId: Long)
}

@Dao
interface AuxiliaryDao {
	@Query("SELECT * FROM tracks")
	fun observeTracks(): Flow<List<TrackEntity>>

	@Query("SELECT * FROM tracks")
	suspend fun getAllTracks(): List<TrackEntity>

	@Upsert
	suspend fun upsertTrack(track: TrackEntity)

	@Upsert
	suspend fun upsertTracks(tracks: List<TrackEntity>)

	@Query("DELETE FROM tracks WHERE manga_id = :mangaId")
	suspend fun deleteTrack(mangaId: Long)

	@Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
	fun observeBookmarks(): Flow<List<BookmarkEntity>>

	@Query("SELECT * FROM bookmarks")
	suspend fun getAllBookmarks(): List<BookmarkEntity>

	@Upsert
	suspend fun upsertBookmark(bookmark: BookmarkEntity)

	@Upsert
	suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>)

	@Query("DELETE FROM bookmarks WHERE manga_id = :mangaId AND page_id = :pageId")
	suspend fun deleteBookmark(mangaId: Long, pageId: Long)

	@Query("DELETE FROM bookmarks WHERE manga_id = :mangaId")
	suspend fun deleteBookmarksForManga(mangaId: Long)

	@Query("SELECT * FROM preferences")
	fun observePreferences(): Flow<List<MangaPrefsEntity>>

	@Query("SELECT * FROM preferences WHERE manga_id = :mangaId")
	suspend fun findPreference(mangaId: Long): MangaPrefsEntity?

	@Upsert
	suspend fun upsertPreference(pref: MangaPrefsEntity)

	@Query("SELECT * FROM sources ORDER BY sort_key ASC")
	fun observeSources(): Flow<List<MangaSourceEntity>>

	@Query("SELECT * FROM sources")
	suspend fun getAllSources(): List<MangaSourceEntity>

	@Upsert
	suspend fun upsertSources(sources: List<MangaSourceEntity>)

	@Query("SELECT * FROM scrobblings")
	suspend fun getAllScrobblings(): List<ScrobblingEntity>

	@Query("SELECT * FROM scrobblings WHERE manga_id = :mangaId")
	suspend fun getScrobblings(mangaId: Long): List<ScrobblingEntity>

	@Upsert
	suspend fun upsertScrobbling(item: ScrobblingEntity)

	@Upsert
	suspend fun upsertScrobblings(items: List<ScrobblingEntity>)

	@Query("SELECT * FROM stats")
	suspend fun getAllStats(): List<StatsEntity>

	@Upsert
	suspend fun upsertStats(items: List<StatsEntity>)

	@Query("SELECT * FROM local_index")
	fun observeLocalIndex(): Flow<List<LocalMangaIndexEntity>>

	@Upsert
	suspend fun upsertLocalIndex(entity: LocalMangaIndexEntity)

	@Query("SELECT * FROM extension_repos ORDER BY repo_id ASC")
	fun observeExtensionRepos(): Flow<List<ExtensionRepoEntity>>

	@Query("SELECT * FROM extension_repos ORDER BY repo_id ASC")
	suspend fun getExtensionRepos(): List<ExtensionRepoEntity>

	@Upsert
	suspend fun upsertExtensionRepo(repo: ExtensionRepoEntity)

	@Query("DELETE FROM extension_repos WHERE repo_id = :repoId")
	suspend fun deleteExtensionRepo(repoId: Long)

	@Query("SELECT * FROM duplicate_ignore")
	fun observeIgnoredDuplicates(): Flow<List<DuplicateIgnoreEntity>>

	@Upsert
	suspend fun ignoreDuplicatePair(pair: DuplicateIgnoreEntity)

	@Query("DELETE FROM duplicate_ignore")
	suspend fun clearIgnoredDuplicates()

	@Upsert
	suspend fun upsertAliases(aliases: List<org.koitharu.kotatsu.core.db.entity.MangaAliasEntity>)

	@Query("SELECT * FROM manga_aliases WHERE manga_id = :mangaId")
	suspend fun getAliasesForManga(mangaId: Long): List<org.koitharu.kotatsu.core.db.entity.MangaAliasEntity>

	@Query("SELECT manga_id FROM manga_aliases WHERE alias_slug = :aliasSlug")
	suspend fun findMangaIdsByAliasSlug(aliasSlug: String): List<Long>

	@Upsert
	suspend fun upsertMergeLink(link: org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity)

	@Query("SELECT * FROM manga_merge_links WHERE parent_manga_id = :parentMangaId ORDER BY priority_weight ASC")
	suspend fun getMergeLinksForParent(parentMangaId: Long): List<org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity>

	@Query("SELECT * FROM manga_merge_links")
	fun observeAllMergeLinks(): Flow<List<org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity>>

	@Query("DELETE FROM manga_merge_links WHERE parent_manga_id = :parentMangaId AND child_manga_id = :childMangaId")
	suspend fun deleteMergeLink(parentMangaId: Long, childMangaId: Long)

	@Upsert
	suspend fun upsertFastView(fastView: org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity)

	@Query("SELECT * FROM library_fast_view")
	fun observeAllFastViews(): Flow<List<org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity>>

	@Query("SELECT * FROM library_fast_view WHERE manga_id = :mangaId LIMIT 1")
	suspend fun getFastView(mangaId: Long): org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity?
}

@Dao
interface MangaAliasesDao {
	@Upsert
	suspend fun upsertAll(aliases: List<org.koitharu.kotatsu.core.db.entity.MangaAliasEntity>)

	@Query("SELECT * FROM manga_aliases WHERE manga_id = :mangaId")
	suspend fun findForManga(mangaId: Long): List<org.koitharu.kotatsu.core.db.entity.MangaAliasEntity>

	@Query("SELECT * FROM manga_aliases WHERE manga_id = :mangaId")
	suspend fun findByMangaId(mangaId: Long): List<org.koitharu.kotatsu.core.db.entity.MangaAliasEntity>

	@Query("SELECT manga_id FROM manga_aliases WHERE alias_slug = :aliasSlug")
	suspend fun findMangaIdsBySlug(aliasSlug: String): List<Long>
}

