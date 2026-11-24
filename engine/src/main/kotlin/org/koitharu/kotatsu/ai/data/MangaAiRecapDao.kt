package org.koitharu.kotatsu.ai.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MangaAiRecapDao {

	@Query("SELECT * FROM manga_ai_recaps WHERE manga_id = :mangaId")
	abstract suspend fun find(mangaId: Long): MangaAiRecapEntity?

	@Query("SELECT * FROM manga_ai_recaps WHERE manga_id = :mangaId")
	abstract fun observe(mangaId: Long): Flow<MangaAiRecapEntity?>

	@Upsert
	abstract suspend fun upsert(entity: MangaAiRecapEntity)

	@Query("DELETE FROM manga_ai_recaps WHERE manga_id = :mangaId")
	abstract suspend fun delete(mangaId: Long)

	@Query("DELETE FROM manga_ai_recaps")
	abstract suspend fun deleteAll()
}
