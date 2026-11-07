package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(
	tableName = "manga_ai_recaps",
	foreignKeys = [
		ForeignKey(
			entity = MangaEntity::class,
			parentColumns = ["manga_id"],
			childColumns = ["manga_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
)
data class MangaAiRecapEntity(
	@PrimaryKey
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "paused_chapter_id") val pausedChapterId: Long,
	@ColumnInfo(name = "paused_chapter_number") val pausedChapterNumber: Float,
	@ColumnInfo(name = "recap_markdown") val recapMarkdown: String,
	@ColumnInfo(name = "generated_at") val generatedAt: Long,
)

@Dao
interface MangaAiRecapDao {
	@Query("SELECT * FROM manga_ai_recaps WHERE manga_id = :mangaId AND paused_chapter_id = :chapterId LIMIT 1")
	suspend fun findValidRecap(mangaId: Long, chapterId: Long): MangaAiRecapEntity?

	@Upsert
	suspend fun upsert(entity: MangaAiRecapEntity)

	@Query("DELETE FROM manga_ai_recaps WHERE manga_id = :mangaId")
	suspend fun deleteForManga(mangaId: Long)
}
