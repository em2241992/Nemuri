package org.koitharu.kotatsu.ai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga_ai_recaps")
data class MangaAiRecapEntity(
	@PrimaryKey
	@ColumnInfo(name = "manga_id")
	val mangaId: Long,
	@ColumnInfo(name = "last_chapter_id")
	val lastChapterId: Long,
	@ColumnInfo(name = "chapters_read_count")
	val chaptersReadCount: Int,
	@ColumnInfo(name = "summary_text")
	val summaryText: String,
	@ColumnInfo(name = "engine_used")
	val engineUsed: String,
	@ColumnInfo(name = "is_ai_generated")
	val isAiGenerated: Boolean,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long,
)
