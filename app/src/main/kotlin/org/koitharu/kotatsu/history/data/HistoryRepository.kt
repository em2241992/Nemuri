package org.koitharu.kotatsu.history.data

import androidx.room.withTransaction
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity
import javax.inject.Inject

class HistoryRepository @Inject constructor(
	private val db: MangaDatabase,
) {
	/**
	 * Marks a single newly released chapter as read from the Feed screen, recording the individual
	 * chapter in `chapters_history`, computing the exact progress ratio against total chapters,
	 * and simultaneously updating `HistoryEntity` and `library_fast_view` (`read_chapters` & `unread_count`)
	 * inside a single Room transaction.
	 */
	suspend fun markChapterReadFromFeed(
		mangaId: Long,
		chapterId: Long,
		totalChapterCount: Int,
	) {
		db.withTransaction {
			val now = System.currentTimeMillis()
			// 1. Record the individual chapter in chapters_history (and sync chapters table if present)
			db.getChaptersHistoryDao().upsert(
				ChapterHistoryEntity(
					mangaId = mangaId,
					chapterId = chapterId,
					readAt = now,
				),
			)
			db.getChaptersDao().updateReadStatus(
				mangaId = mangaId,
				chapterId = chapterId,
				isRead = true,
				lastPage = 18,
			)

			// 2. Count actual read chapters for accurate overall manga progress
			val readHistoryCount = db.getChaptersHistoryDao().countReadChapters(mangaId)
			val readChaptersTableCount = db.getChaptersDao().findAll(mangaId).count { it.isRead }
			val readCount = maxOf(readHistoryCount, readChaptersTableCount)
			val safeTotal = totalChapterCount.coerceAtLeast(1)
			val accuratePercent = (readCount.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

			val existing = db.getHistoryDao().find(mangaId)
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = mangaId,
					createdAt = existing?.createdAt ?: now,
					updatedAt = now,
					chapterId = chapterId,
					page = 0,
					scroll = 0f,
					percent = accuratePercent,
					chaptersCount = totalChapterCount,
					deletedAt = 0L,
				),
			)

			// 3. Simultaneously update library_fast_view.read_chapters and library_fast_view.unread_count
			val existingFastView = db.getAuxiliaryDao().getFastView(mangaId)
			val effectiveTotal = maxOf(safeTotal, existingFastView?.totalChapters ?: 0)
			val unreadCount = (effectiveTotal - readCount).coerceAtLeast(0)
			db.getAuxiliaryDao().upsertFastView(
				LibraryFastViewEntity(
					mangaId = mangaId,
					totalChapters = effectiveTotal,
					readChapters = readCount,
					unreadCount = unreadCount,
					lastReadAt = now,
					latestChapterDate = existingFastView?.latestChapterDate ?: now,
					avgReleaseIntervalDays = existingFastView?.avgReleaseIntervalDays ?: 7,
					lastPolledAt = existingFastView?.lastPolledAt ?: now,
				),
			)
		}
	}
}
