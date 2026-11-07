package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.koitharu.kotatsu.core.db.TABLE_BOOKMARKS
import org.koitharu.kotatsu.core.db.TABLE_CHAPTERS
import org.koitharu.kotatsu.core.db.TABLE_DUPLICATE_IGNORE
import org.koitharu.kotatsu.core.db.TABLE_EXTENSION_REPOS
import org.koitharu.kotatsu.core.db.TABLE_FAVOURITES
import org.koitharu.kotatsu.core.db.TABLE_FAVOURITE_CATEGORIES
import org.koitharu.kotatsu.core.db.TABLE_HISTORY
import org.koitharu.kotatsu.core.db.TABLE_LOCAL_INDEX
import org.koitharu.kotatsu.core.db.TABLE_MANGA
import org.koitharu.kotatsu.core.db.TABLE_MANGA_FTS
import org.koitharu.kotatsu.core.db.TABLE_MANGA_TAGS
import org.koitharu.kotatsu.core.db.TABLE_PREFERENCES
import org.koitharu.kotatsu.core.db.TABLE_SCROBBLINGS
import org.koitharu.kotatsu.core.db.TABLE_SOURCES
import org.koitharu.kotatsu.core.db.TABLE_STATS
import org.koitharu.kotatsu.core.db.TABLE_TAGS
import org.koitharu.kotatsu.core.db.TABLE_TRACKS

import org.koitharu.kotatsu.core.db.TABLE_CHAPTERS_HISTORY
import org.koitharu.kotatsu.core.db.TABLE_LIBRARY_FAST_VIEW
import org.koitharu.kotatsu.core.db.TABLE_MANGA_ALIASES
import org.koitharu.kotatsu.core.db.TABLE_MANGA_MERGE_LINKS

object TitleNormalizer {
	private val noiseRegex = Regex(
		"\\b(official|colored|digital|webtoon|manga|manhwa|manhua|scan|scans|ver|version|hd)\\b",
		RegexOption.IGNORE_CASE,
	)
	private val nonAlphaNum = Regex("[^\\p{L}\\p{N}\\s]")
	private val multiSpace = Regex("\\s+")
	private val normCache = java.util.concurrent.ConcurrentHashMap<String, String>(256)
	private val bigramCache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>(256)

	fun normalize(raw: String): String {
		if (raw.isBlank()) return ""
		normCache[raw]?.let { return it }
		val computed = raw.lowercase()
			.replace(noiseRegex, " ")
			.replace(nonAlphaNum, " ")
			.replace(multiSpace, " ")
			.trim()
		if (normCache.size > 2048) normCache.clear()
		normCache[raw] = computed
		return computed
	}

	fun toNormalizedSlug(rawTitle: String): String =
		org.koitharu.kotatsu.core.util.TitleNormalizer.toNormalizedSlug(rawTitle)

	fun toCanonicalChapterKey(chapterNumber: Float, rawChapterTitle: String?): Int =
		org.koitharu.kotatsu.core.util.TitleNormalizer.toCanonicalChapterKey(chapterNumber, rawChapterTitle)

	fun similarity(a: String, b: String): Float {
		val normA = normalize(a)
		val normB = normalize(b)
		if (normA.isEmpty() || normB.isEmpty()) return 0f
		if (normA == normB) return 1.0f
		if (normA.contains(normB) || normB.contains(normA)) {
			val ratio = minOf(normA.length, normB.length).toFloat() / maxOf(normA.length, normB.length).toFloat()
			if (ratio >= 0.65f) return 0.85f + (ratio * 0.12f)
		}
		val bigramsA = bigrams(normA)
		val bigramsB = bigrams(normB)
		if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0f
		var intersection = 0
		for (bg in bigramsA) {
			if (bg in bigramsB) intersection++
		}
		val dice = (2f * intersection) / (bigramsA.size + bigramsB.size).toFloat()
		val tokensA = normA.split(' ').filter { it.length >= 5 }.toSet()
		val tokensB = normB.split(' ').filter { it.length >= 5 }.toSet()
		val sharedSignificant = tokensA.intersect(tokensB)
		return if (sharedSignificant.isNotEmpty()) {
			maxOf(dice, (dice + 0.15f).coerceAtMost(0.95f))
		} else {
			dice
		}
	}

	private fun bigrams(s: String): Set<String> {
		if (s.length < 2) return setOf(s)
		bigramCache[s]?.let { return it }
		val result = HashSet<String>(s.length - 1)
		for (i in 0 until s.length - 1) {
			result.add(s.substring(i, i + 2))
		}
		if (bigramCache.size > 2048) bigramCache.clear()
		bigramCache[s] = result
		return result
	}
}

@Entity(
	tableName = TABLE_MANGA,
	indices = [
		Index(value = ["normalized_title"], name = "index_manga_normalized_title"),
		Index(value = ["normalized_slug"], name = "index_manga_normalized_slug"),
		Index(value = ["canonical_id"], name = "index_manga_canonical_id"),
		Index(value = ["source", "title"], name = "index_manga_source_title"),
		Index(value = ["public_url"], name = "index_manga_public_url"),
	],
)
data class MangaEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val id: Long,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "alt_title") val altTitles: String?,
	@ColumnInfo(name = "url") val url: String,
	@ColumnInfo(name = "public_url") val publicUrl: String,
	@ColumnInfo(name = "rating") val rating: Float,
	@ColumnInfo(name = "nsfw") val isNsfw: Boolean,
	@ColumnInfo(name = "content_rating") val contentRating: String?,
	@ColumnInfo(name = "cover_url") val coverUrl: String,
	@ColumnInfo(name = "large_cover_url") val largeCoverUrl: String?,
	@ColumnInfo(name = "state") val state: String?,
	@ColumnInfo(name = "author") val authors: String?,
	@ColumnInfo(name = "source") val source: String,
	@ColumnInfo(name = "description", defaultValue = "") val description: String = "",
	@ColumnInfo(name = "normalized_title", defaultValue = "") val normalizedTitle: String = TitleNormalizer.normalize(title),
	@ColumnInfo(name = "normalized_slug", defaultValue = "") val normalizedSlug: String = TitleNormalizer.toNormalizedSlug(title),
	@ColumnInfo(name = "canonical_id") val canonicalId: String? = null,
	@ColumnInfo(name = "is_broken", defaultValue = "0") val isBroken: Boolean = false,
	@ColumnInfo(name = "health_error", defaultValue = "") val healthError: String? = null,
)

@Fts4(contentEntity = MangaEntity::class)
@Entity(tableName = TABLE_MANGA_FTS)
data class MangaFtsEntity(
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "alt_title") val altTitles: String?,
	@ColumnInfo(name = "author") val authors: String?,
	@ColumnInfo(name = "description") val description: String,
)

@Entity(
	tableName = TABLE_TAGS,
	indices = [Index(value = ["key", "source"], unique = true)],
)
data class TagEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "tag_id") val id: Long,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "key") val key: String,
	@ColumnInfo(name = "source") val source: String,
)

@Entity(
	tableName = TABLE_MANGA_TAGS,
	primaryKeys = ["manga_id", "tag_id"],
	indices = [Index(value = ["tag_id"])],
)
data class MangaTagsEntity(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
	tableName = TABLE_CHAPTERS,
	primaryKeys = ["chapter_id", "manga_id"],
	indices = [
		Index(value = ["manga_id", "number"], name = "idx_chapters_manga_number"),
		Index(value = ["manga_id", "is_read"], name = "idx_chapters_manga_read"),
		Index(value = ["manga_id", "is_downloaded"], name = "idx_chapters_manga_downloaded"),
	],
)
data class ChapterEntity(
	@ColumnInfo(name = "chapter_id") val chapterId: Long,
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "number") val number: Float,
	@ColumnInfo(name = "volume") val volume: Int,
	@ColumnInfo(name = "url") val url: String,
	@ColumnInfo(name = "scanlator") val scanlator: String?,
	@ColumnInfo(name = "upload_date") val uploadDate: Long,
	@ColumnInfo(name = "branch") val branch: String?,
	@ColumnInfo(name = "index") val index: Int,
	@ColumnInfo(name = "is_read", defaultValue = "0") val isRead: Boolean = false,
	@ColumnInfo(name = "is_downloaded", defaultValue = "0") val isDownloaded: Boolean = false,
	@ColumnInfo(name = "last_page_read", defaultValue = "0") val lastPageRead: Int = 0,
)

@Entity(tableName = TABLE_FAVOURITE_CATEGORIES)
data class FavouriteCategoryEntity(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "category_id") val categoryId: Int = 0,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	@ColumnInfo(name = "sort_key") val sortKey: Int,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "order") val order: String,
	@ColumnInfo(name = "track") val track: Boolean,
	@ColumnInfo(name = "show_in_lib") val isVisibleInLibrary: Boolean,
	@ColumnInfo(name = "deleted_at") val deletedAt: Long = 0L,
)

@Entity(
	tableName = TABLE_FAVOURITES,
	primaryKeys = ["manga_id", "category_id"],
	indices = [
		Index(value = ["category_id", "deleted_at", "created_at"], name = "idx_fav_cat_del_created"),
		Index(value = ["manga_id", "deleted_at"], name = "idx_fav_manga_del"),
	],
)
data class FavouriteEntity(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "category_id") val categoryId: Long,
	@ColumnInfo(name = "sort_key") val sortKey: Int,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	@ColumnInfo(name = "deleted_at") val deletedAt: Long = 0L,
)

@Entity(
	tableName = TABLE_HISTORY,
	indices = [
		Index(value = ["manga_id", "updated_at", "percent"], name = "idx_hist_manga_updated_pct"),
		Index(value = ["updated_at"], name = "idx_hist_updated_at"),
	],
)
data class HistoryEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
	@ColumnInfo(name = "chapter_id") val chapterId: Long,
	@ColumnInfo(name = "page") val page: Int,
	@ColumnInfo(name = "scroll") val scroll: Float,
	@ColumnInfo(name = "percent") val percent: Float,
	@ColumnInfo(name = "deleted_at") val deletedAt: Long = 0L,
	@ColumnInfo(name = "chapters_count") val chaptersCount: Int = 0,
)

@Entity(
	tableName = TABLE_TRACKS,
	indices = [
		Index(value = ["manga_id", "chapters_new", "last_chapter_date"], name = "idx_tracks_manga_new_date"),
	],
)
data class TrackEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "total_chapters") val totalChapters: Int,
	@ColumnInfo(name = "last_chapter_id") val lastChapterId: Long,
	@ColumnInfo(name = "chapters_new") val newChapters: Int,
	@ColumnInfo(name = "last_check_time") val lastCheckTime: Long,
	@ColumnInfo(name = "last_chapter_date") val lastChapterDate: Long,
	@ColumnInfo(name = "last_result") val lastResult: Int = 1,
)

@Entity(
	tableName = TABLE_BOOKMARKS,
	primaryKeys = ["manga_id", "page_id"],
)
data class BookmarkEntity(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "page_id") val pageId: Long,
	@ColumnInfo(name = "chapter_id") val chapterId: Long,
	@ColumnInfo(name = "page") val page: Int,
	@ColumnInfo(name = "image_url") val imageUrl: String,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	@ColumnInfo(name = "percent") val percent: Float,
)

@Entity(tableName = TABLE_PREFERENCES)
data class MangaPrefsEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "mode") val mode: Int,
	@ColumnInfo(name = "cf_brightness") val cfBrightness: Float,
	@ColumnInfo(name = "cf_contrast") val cfContrast: Float,
	@ColumnInfo(name = "cf_invert") val cfInvert: Boolean,
	@ColumnInfo(name = "cf_grayscale") val cfGrayscale: Boolean,
	@ColumnInfo(name = "cf_book") val cfBookEffect: Boolean = false,
	@ColumnInfo(name = "title_override") val titleOverride: String? = null,
	@ColumnInfo(name = "cover_override") val coverUrlOverride: String? = null,
	@ColumnInfo(name = "content_rating_override") val contentRatingOverride: String? = null,
	@ColumnInfo(name = "ai_upscale_mode", defaultValue = "0") val aiUpscaleMode: Int = 0,
)

@Entity(tableName = TABLE_SOURCES)
data class MangaSourceEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "source") val source: String,
	@ColumnInfo(name = "enabled") val isEnabled: Boolean,
	@ColumnInfo(name = "sort_key") val sortKey: Int,
	@ColumnInfo(name = "added_in") val addedIn: Int,
	@ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
	@ColumnInfo(name = "is_pinned") val isPinned: Boolean,
	@ColumnInfo(name = "cf_state") val cfState: Int = 0,
)

@Entity(
	tableName = TABLE_SCROBBLINGS,
	primaryKeys = ["scrobbler", "id", "manga_id"],
)
data class ScrobblingEntity(
	@ColumnInfo(name = "scrobbler") val scrobbler: Int,
	@ColumnInfo(name = "id") val id: Int,
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "target_id") val targetId: Long,
	@ColumnInfo(name = "status") val status: String?,
	@ColumnInfo(name = "chapter") val chapter: Int,
	@ColumnInfo(name = "comment") val comment: String?,
	@ColumnInfo(name = "rating") val rating: Float,
)

@Entity(
	tableName = TABLE_STATS,
	primaryKeys = ["manga_id", "started_at"],
)
data class StatsEntity(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "started_at") val startedAt: Long,
	@ColumnInfo(name = "duration") val duration: Long,
	@ColumnInfo(name = "pages") val pages: Int,
)

@Entity(tableName = TABLE_LOCAL_INDEX)
data class LocalMangaIndexEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "path") val path: String,
)

@Entity(
	tableName = TABLE_EXTENSION_REPOS,
	indices = [Index(value = ["base_url"], unique = true)],
)
data class ExtensionRepoEntity(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "repo_id") val repoId: Long = 0L,
	@ColumnInfo(name = "name") val name: String,
	@ColumnInfo(name = "base_url") val baseUrl: String,
	@ColumnInfo(name = "repo_type") val repoType: String, // MIHON_JSON, KOTATSU_PLUGIN, LOCAL_CBZ
	@ColumnInfo(name = "enabled") val isEnabled: Boolean = true,
	@ColumnInfo(name = "extension_count") val extensionCount: Int = 0,
	@ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long = System.currentTimeMillis(),
)

@Entity(
	tableName = TABLE_DUPLICATE_IGNORE,
	primaryKeys = ["manga_id_a", "manga_id_b"],
)
data class DuplicateIgnoreEntity(
	@ColumnInfo(name = "manga_id_a") val mangaIdA: Long,
	@ColumnInfo(name = "manga_id_b") val mangaIdB: Long,
	@ColumnInfo(name = "ignored_at") val ignoredAt: Long = System.currentTimeMillis(),
)

@Entity(
	tableName = TABLE_MANGA_ALIASES,
	primaryKeys = ["alias_slug", "manga_id"],
	indices = [Index(value = ["manga_id"], name = "index_manga_aliases_manga_id")],
)
data class MangaAliasEntity(
	@ColumnInfo(name = "alias_slug") val aliasSlug: String,
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "raw_alias") val rawAlias: String,
)

@Entity(
	tableName = TABLE_MANGA_MERGE_LINKS,
	primaryKeys = ["parent_manga_id", "child_manga_id"],
	indices = [
		Index(value = ["parent_manga_id"], name = "index_merge_links_parent"),
		Index(value = ["child_manga_id"], name = "index_merge_links_child"),
	],
)
data class MangaMergeLinkEntity(
	@ColumnInfo(name = "parent_manga_id") val parentMangaId: Long,
	@ColumnInfo(name = "child_manga_id") val childMangaId: Long,
	@ColumnInfo(name = "source_name") val sourceName: String,
	@ColumnInfo(name = "priority_weight", defaultValue = "100") val priorityWeight: Int = 100,
	@ColumnInfo(name = "preferred_scanlator") val preferredScanlator: String? = null,
	@ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
	tableName = TABLE_LIBRARY_FAST_VIEW,
	indices = [
		Index(value = ["unread_count", "latest_chapter_date"], name = "idx_lib_fast_unread"),
		Index(value = ["last_polled_at"], name = "idx_lib_fast_poll"),
	],
)
data class LibraryFastViewEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "total_chapters", defaultValue = "0") val totalChapters: Int = 0,
	@ColumnInfo(name = "read_chapters", defaultValue = "0") val readChapters: Int = 0,
	@ColumnInfo(name = "unread_count", defaultValue = "0") val unreadCount: Int = 0,
	@ColumnInfo(name = "last_read_at", defaultValue = "0") val lastReadAt: Long = 0L,
	@ColumnInfo(name = "latest_chapter_date", defaultValue = "0") val latestChapterDate: Long = 0L,
	@ColumnInfo(name = "avg_release_interval_days", defaultValue = "7") val avgReleaseIntervalDays: Int = 7,
	@ColumnInfo(name = "last_polled_at", defaultValue = "0") val lastPolledAt: Long = 0L,
)

@Entity(
	tableName = TABLE_CHAPTERS_HISTORY,
	primaryKeys = ["manga_id", "chapter_id"],
	indices = [Index(value = ["manga_id"], name = "index_chapters_history_manga_id")],
)
data class ChapterHistoryEntity(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "chapter_id") val chapterId: Long,
	@ColumnInfo(name = "read_at") val readAt: Long = System.currentTimeMillis(),
)

data class MangaWithTags(
	@Embedded val manga: MangaEntity,
	@Relation(
		parentColumn = "manga_id",
		entityColumn = "tag_id",
		associateBy = Junction(MangaTagsEntity::class),
	)
	val tags: List<TagEntity>,
) {
	val title: String get() = manga.title
	val coverUrl: String get() = manga.coverUrl
	val description: String get() = manga.description
}
