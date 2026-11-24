package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration40To41 : Migration(40, 41) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `manga_ai_recaps` (
				`manga_id` INTEGER NOT NULL,
				`last_chapter_id` INTEGER NOT NULL,
				`chapters_read_count` INTEGER NOT NULL,
				`summary_text` TEXT NOT NULL,
				`engine_used` TEXT NOT NULL,
				`is_ai_generated` INTEGER NOT NULL,
				`updated_at` INTEGER NOT NULL,
				PRIMARY KEY(`manga_id`)
			)
			""".trimIndent(),
		)
	}
}
