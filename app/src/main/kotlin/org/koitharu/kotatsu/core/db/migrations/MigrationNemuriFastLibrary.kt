package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class MigrationNemuriFastLibrary(
	startVersion: Int = 28,
	endVersion: Int = 29,
) : Migration(startVersion, endVersion) {
	override fun migrate(db: SupportSQLiteDatabase) {
		// 1. Add normalized_slug and canonical_id (e.g., "al:101557" or "md:uuid") to manga
		runCatching {
			db.execSQL("ALTER TABLE manga ADD COLUMN normalized_slug TEXT NOT NULL DEFAULT ''")
		}
		runCatching {
			db.execSQL("ALTER TABLE manga ADD COLUMN canonical_id TEXT DEFAULT NULL")
		}
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_normalized_slug ON manga(normalized_slug)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_canonical_id ON manga(canonical_id)")

		// 2. Alias table for alt-titles fetched from MangaDex / AniList / MAL
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS manga_aliases (
				alias_slug TEXT NOT NULL,
				manga_id INTEGER NOT NULL,
				raw_alias TEXT NOT NULL,
				PRIMARY KEY (alias_slug, manga_id),
				FOREIGN KEY (manga_id) REFERENCES manga(manga_id) ON DELETE CASCADE
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_aliases_manga_id ON manga_aliases(manga_id)")

		// 3. Sidecar Merge Links table
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS manga_merge_links (
				parent_manga_id INTEGER NOT NULL,
				child_manga_id INTEGER NOT NULL,
				source_name TEXT NOT NULL,
				priority_weight INTEGER NOT NULL DEFAULT 100,
				preferred_scanlator TEXT DEFAULT NULL,
				created_at INTEGER NOT NULL,
				PRIMARY KEY (parent_manga_id, child_manga_id),
				FOREIGN KEY (parent_manga_id) REFERENCES manga(manga_id) ON DELETE CASCADE,
				FOREIGN KEY (child_manga_id) REFERENCES manga(manga_id) ON DELETE CASCADE
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_merge_links_parent ON manga_merge_links(parent_manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_merge_links_child ON manga_merge_links(child_manga_id)")

		// 4. Denormalized Library Fast View (O(1) Library Grid loading for 4,000+ titles)
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS library_fast_view (
				manga_id INTEGER PRIMARY KEY NOT NULL,
				total_chapters INTEGER NOT NULL DEFAULT 0,
				read_chapters INTEGER NOT NULL DEFAULT 0,
				unread_count INTEGER NOT NULL DEFAULT 0,
				last_read_at INTEGER NOT NULL DEFAULT 0,
				latest_chapter_date INTEGER NOT NULL DEFAULT 0,
				avg_release_interval_days INTEGER NOT NULL DEFAULT 7,
				last_polled_at INTEGER NOT NULL DEFAULT 0,
				FOREIGN KEY (manga_id) REFERENCES manga(manga_id) ON DELETE CASCADE
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_lib_fast_unread ON library_fast_view(unread_count DESC, latest_chapter_date DESC)")
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_lib_fast_poll ON library_fast_view(last_polled_at ASC)")

		// 4b. Chapters History table for accurate per-chapter read tracking
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS chapters_history (
				manga_id INTEGER NOT NULL,
				chapter_id INTEGER NOT NULL,
				read_at INTEGER NOT NULL,
				PRIMARY KEY (manga_id, chapter_id)
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_history_manga_id ON chapters_history(manga_id)")

		// 4c. On-Device Gemini Nano Recap Cache table (manga_ai_recaps)
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS manga_ai_recaps (
				manga_id INTEGER PRIMARY KEY NOT NULL,
				paused_chapter_id INTEGER NOT NULL,
				paused_chapter_number REAL NOT NULL,
				recap_markdown TEXT NOT NULL,
				generated_at INTEGER NOT NULL,
				FOREIGN KEY (manga_id) REFERENCES manga(manga_id) ON DELETE CASCADE
			)
			""".trimIndent(),
		)

		// 5. External-Content FTS5 / FTS4 table for instant multi-language search
		runCatching {
			db.execSQL(
				"""
				CREATE VIRTUAL TABLE IF NOT EXISTS manga_fts USING fts5(
					title,
					alt_title,
					author,
					normalized_slug,
					content='manga',
					content_rowid='manga_id',
					tokenize='unicode61 remove_diacritics 2'
				)
				""".trimIndent(),
			)
		}.onFailure {
			runCatching {
				db.execSQL(
					"CREATE VIRTUAL TABLE IF NOT EXISTS `manga_fts` USING FTS4(`title`, `alt_title`, `author`, `description`, content=`manga`)",
				)
			}
		}

		// 6. Keep FTS synced automatically via SQLite triggers
		runCatching {
			db.execSQL(
				"""
				CREATE TRIGGER IF NOT EXISTS trg_manga_fts_ai AFTER INSERT ON manga BEGIN
					INSERT INTO manga_fts(rowid, title, alt_title, author, normalized_slug)
					VALUES (new.manga_id, new.title, new.alt_title, new.author, new.normalized_slug);
				END;
				""".trimIndent(),
			)
			db.execSQL(
				"""
				CREATE TRIGGER IF NOT EXISTS trg_manga_fts_ad AFTER DELETE ON manga BEGIN
					INSERT INTO manga_fts(manga_fts, rowid, title, alt_title, author, normalized_slug)
					VALUES ('delete', old.manga_id, old.title, old.alt_title, old.author, old.normalized_slug);
				END;
				""".trimIndent(),
			)
			db.execSQL(
				"""
				CREATE TRIGGER IF NOT EXISTS trg_manga_fts_au AFTER UPDATE ON manga BEGIN
					INSERT INTO manga_fts(manga_fts, rowid, title, alt_title, author, normalized_slug)
					VALUES ('delete', old.manga_id, old.title, old.alt_title, old.author, old.normalized_slug);
					INSERT INTO manga_fts(rowid, title, alt_title, author, normalized_slug)
					VALUES (new.manga_id, new.title, new.alt_title, new.author, new.normalized_slug);
				END;
				""".trimIndent(),
			)
		}

		// 7. Keep library_fast_view automatically maintained via SQLite triggers on chapters & history
		runCatching {
			db.execSQL(
				"""
				CREATE TRIGGER IF NOT EXISTS trg_lib_fast_ch_ai AFTER INSERT ON chapters BEGIN
					INSERT OR REPLACE INTO library_fast_view(manga_id, total_chapters, read_chapters, unread_count, last_read_at, latest_chapter_date)
					SELECT
						new.manga_id,
						COUNT(*),
						SUM(CASE WHEN is_read = 1 THEN 1 ELSE 0 END),
						SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END),
						COALESCE((SELECT updated_at FROM history WHERE history.manga_id = new.manga_id), 0),
						COALESCE(MAX(upload_date), 0)
					FROM chapters WHERE manga_id = new.manga_id;
				END;
				""".trimIndent(),
			)
			db.execSQL(
				"""
				CREATE TRIGGER IF NOT EXISTS trg_lib_fast_ch_au AFTER UPDATE ON chapters BEGIN
					INSERT OR REPLACE INTO library_fast_view(manga_id, total_chapters, read_chapters, unread_count, last_read_at, latest_chapter_date)
					SELECT
						new.manga_id,
						COUNT(*),
						SUM(CASE WHEN is_read = 1 THEN 1 ELSE 0 END),
						SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END),
						COALESCE((SELECT updated_at FROM history WHERE history.manga_id = new.manga_id), 0),
						COALESCE(MAX(upload_date), 0)
					FROM chapters WHERE manga_id = new.manga_id;
				END;
				""".trimIndent(),
			)
		}
	}
}
