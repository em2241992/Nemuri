package org.koitharu.kotatsu.ai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.koitharu.kotatsu.ai.domain.RecapDepth

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
) {

	fun parsedDepth(): RecapDepth? = when {
		engineUsed.endsWith(SUFFIX_BRIEF, ignoreCase = true) -> RecapDepth.BRIEF
		engineUsed.endsWith(SUFFIX_DETAILED, ignoreCase = true) -> RecapDepth.DETAILED
		else -> null
	}

	fun displayEngineLabel(): String = engineUsed.substringBefore(DEPTH_SEPARATOR).trim()

	companion object {
		private const val DEPTH_SEPARATOR = " • "
		private const val SUFFIX_BRIEF = "• Brief"
		private const val SUFFIX_DETAILED = "• Detailed"

		fun encodeEngineWithDepth(engineLabel: String, depth: RecapDepth): String {
			val base = engineLabel.substringBefore(DEPTH_SEPARATOR).trim()
			return "$base$DEPTH_SEPARATOR${depth.shortLabel}"
		}
	}
}
