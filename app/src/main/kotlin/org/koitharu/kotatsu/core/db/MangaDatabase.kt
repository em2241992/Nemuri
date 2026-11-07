package org.koitharu.kotatsu.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.koitharu.kotatsu.core.db.dao.AuxiliaryDao
import org.koitharu.kotatsu.core.db.dao.ChaptersDao
import org.koitharu.kotatsu.core.db.dao.FavouritesDao
import org.koitharu.kotatsu.core.db.dao.HistoryDao
import org.koitharu.kotatsu.core.db.dao.MangaDao
import org.koitharu.kotatsu.core.db.entity.BookmarkEntity
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.DuplicateIgnoreEntity
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.LocalMangaIndexEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.MangaFtsEntity
import org.koitharu.kotatsu.core.db.entity.MangaPrefsEntity
import org.koitharu.kotatsu.core.db.entity.MangaSourceEntity
import org.koitharu.kotatsu.core.db.entity.MangaTagsEntity
import org.koitharu.kotatsu.core.db.entity.ScrobblingEntity
import org.koitharu.kotatsu.core.db.entity.StatsEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TrackEntity

import org.koitharu.kotatsu.core.db.dao.ChaptersHistoryDao
import org.koitharu.kotatsu.core.db.dao.MangaAliasesDao
import org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity
import org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity
import org.koitharu.kotatsu.core.db.entity.MangaAliasEntity
import org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity
import org.koitharu.kotatsu.core.db.migrations.MigrationNemuriFastLibrary

const val DATABASE_VERSION = 29

/**
 * Migration 27 -> 28:
 * Upgrades Kotatsu's Room SQLite database with:
 * 1. FTS4 Virtual Table (`manga_fts`) for <2ms full-text search across large libraries
 * 2. Indexed `normalized_title` column on `manga` for O(1) duplicate detection on add
 * 3. Composite covering indexes on `favourites`, `history`, `tracks`, and `chapters`
 * 4. `extension_repos` and `duplicate_ignore` tables for Mihon external extensions & deduplication
 */
val Migration27To28 = object : Migration(27, 28) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE manga ADD COLUMN description TEXT NOT NULL DEFAULT ''")
		db.execSQL("ALTER TABLE manga ADD COLUMN normalized_title TEXT NOT NULL DEFAULT ''")
		db.execSQL("ALTER TABLE manga ADD COLUMN is_broken INTEGER NOT NULL DEFAULT 0")
		db.execSQL("ALTER TABLE manga ADD COLUMN health_error TEXT")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_normalized_title ON manga(normalized_title)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_source_title ON manga(source, title)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_public_url ON manga(public_url)")

		db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `manga_fts` USING FTS4(`title`, `alt_title`, `author`, `description`, content=`manga`)")
		db.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")

		db.execSQL("CREATE INDEX IF NOT EXISTS idx_fav_cat_del_created ON favourites(category_id, deleted_at, created_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_fav_manga_del ON favourites(manga_id, deleted_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_manga_updated_pct ON history(manga_id, updated_at, percent)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_tracks_manga_new_date ON tracks(manga_id, chapters_new, last_chapter_date)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_chapters_manga_number ON chapters(manga_id, number)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_chapters_manga_read ON chapters(manga_id, is_read)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_chapters_manga_downloaded ON chapters(manga_id, is_downloaded)")

		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `extension_repos` (
				`repo_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
				`name` TEXT NOT NULL,
				`base_url` TEXT NOT NULL,
				`repo_type` TEXT NOT NULL,
				`enabled` INTEGER NOT NULL DEFAULT 1,
				`extension_count` INTEGER NOT NULL DEFAULT 0,
				`last_synced_at` INTEGER NOT NULL DEFAULT 0
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_extension_repos_base_url ON extension_repos(base_url)")

		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `duplicate_ignore` (
				`manga_id_a` INTEGER NOT NULL,
				`manga_id_b` INTEGER NOT NULL,
				`ignored_at` INTEGER NOT NULL,
				PRIMARY KEY(`manga_id_a`, `manga_id_b`)
			)
			""".trimIndent(),
		)
	}
}

val Migration28To29 = MigrationNemuriFastLibrary(28, 29)

@Database(
	entities = [
		MangaEntity::class,
		MangaFtsEntity::class,
		TagEntity::class,
		MangaTagsEntity::class,
		ChapterEntity::class,
		FavouriteCategoryEntity::class,
		FavouriteEntity::class,
		HistoryEntity::class,
		TrackEntity::class,
		BookmarkEntity::class,
		MangaPrefsEntity::class,
		MangaSourceEntity::class,
		ScrobblingEntity::class,
		StatsEntity::class,
		LocalMangaIndexEntity::class,
		ExtensionRepoEntity::class,
		DuplicateIgnoreEntity::class,
		MangaAliasEntity::class,
		MangaMergeLinkEntity::class,
		LibraryFastViewEntity::class,
		ChapterHistoryEntity::class,
		org.koitharu.kotatsu.core.db.entity.MangaAiRecapEntity::class,
	],
	version = DATABASE_VERSION,
	exportSchema = false,
)
abstract class MangaDatabase : RoomDatabase() {
	abstract fun getMangaDao(): MangaDao
	abstract fun getChaptersDao(): ChaptersDao
	abstract fun getFavouritesDao(): FavouritesDao
	abstract fun getHistoryDao(): HistoryDao
	abstract fun getChaptersHistoryDao(): ChaptersHistoryDao
	abstract fun getMangaAliasesDao(): org.koitharu.kotatsu.core.db.dao.MangaAliasesDao
	abstract fun getMangaAiRecapDao(): org.koitharu.kotatsu.core.db.entity.MangaAiRecapDao
	abstract fun getAuxiliaryDao(): AuxiliaryDao

	companion object {
		@Volatile
		private var instance: MangaDatabase? = null

		fun getInstance(context: Context): MangaDatabase {
			return instance ?: synchronized(this) {
				instance ?: buildDatabase(context.applicationContext).also { instance = it }
			}
		}

		private fun buildDatabase(context: Context): MangaDatabase {
			return Room.databaseBuilder(context, MangaDatabase::class.java, "kotatsu-db")
				.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
				.addMigrations(Migration27To28, Migration28To29)
				.fallbackToDestructiveMigration(dropAllTables = true)
				.addCallback(
					object : Callback() {
						override fun onOpen(db: SupportSQLiteDatabase) {
							super.onOpen(db)
							db.query("PRAGMA synchronous = NORMAL").close()
							db.query("PRAGMA mmap_size = 268435456").close()
							db.query("PRAGMA temp_store = MEMORY").close()
							db.query("PRAGMA cache_size = -32000").close()
						}
					},
				)
				.build()
		}
	}
}
