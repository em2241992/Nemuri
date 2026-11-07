package org.koitharu.kotatsu.tracker.domain

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity
import org.koitharu.kotatsu.core.db.entity.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

data class ReverseSyncSummary(
	val mangaId: Long,
	val previousLocalHighestChapter: Float,
	val remoteLastChapterRead: Float,
	val newlyMarkedChaptersCount: Int,
	val updatedUnreadCount: Int,
)

@Singleton
class ReverseTrackerSyncUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val trackingRepository: TrackingRepository,
) {
	/**
	 * Queries the remote tracker's `lastChapterRead` float (from AniList / MAL / Kitsu) and compares it
	 * against local `library_fast_view.read_chapters` and the highest read chapter number in `chapters_history`.
	 * If `remoteLastChapterRead > localHighestReadChapterNumber`, marks all local chapters with
	 * `number <= remoteLastChapterRead` as read inside `chapters_history`, advances `HistoryEntity.chapterId`
	 * to the chapter matching `remoteLastChapterRead`, and updates `library_fast_view.unread_count`.
	 */
	suspend fun syncFromRemoteTracker(
		mangaId: Long,
		explicitRemoteLastChapterRead: Float? = null,
	): ReverseSyncSummary = withContext(Dispatchers.IO) {
		val remoteLastChapterRead = explicitRemoteLastChapterRead
			?: trackingRepository.fetchRemoteLastChapterRead(mangaId)
			?: 0f

		var previousHighest = 0f
		var newlyMarked = 0
		var finalUnread = 0

		db.withTransaction {
			val chapters = db.getChaptersDao().findAll(mangaId).sortedBy { it.number }
			val readChapterIds = db.getChaptersHistoryDao().findReadChapterIds(mangaId).toSet()

			previousHighest = chapters
				.filter { it.isRead || it.chapterId in readChapterIds }
				.maxOfOrNull { it.number } ?: 0f

			if (remoteLastChapterRead > previousHighest && chapters.isNotEmpty()) {
				val now = System.currentTimeMillis()
				var targetChapter = chapters.first()

				for (ch in chapters) {
					if (ch.number <= remoteLastChapterRead + 0.001f) {
						targetChapter = ch
						if (!ch.isRead || ch.chapterId !in readChapterIds) {
							db.getChaptersDao().updateReadStatus(
								mangaId = mangaId,
								chapterId = ch.chapterId,
								isRead = true,
								lastPage = 18,
							)
							db.getChaptersHistoryDao().upsert(
								ChapterHistoryEntity(
									mangaId = mangaId,
									chapterId = ch.chapterId,
									readAt = now,
								),
							)
							newlyMarked++
						}
					}
				}

				val updatedChapters = db.getChaptersDao().findAll(mangaId)
				val totalCount = updatedChapters.size.coerceAtLeast(1)
				val readCount = maxOf(
					updatedChapters.count { it.isRead },
					db.getChaptersHistoryDao().countReadChapters(mangaId),
				)
				finalUnread = (totalCount - readCount).coerceAtLeast(0)
				val accuratePercent = (readCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

				val existingHistory = db.getHistoryDao().find(mangaId)
				db.getHistoryDao().upsert(
					HistoryEntity(
						mangaId = mangaId,
						createdAt = existingHistory?.createdAt ?: now,
						updatedAt = now,
						chapterId = targetChapter.chapterId,
						page = 18,
						scroll = 0f,
						percent = accuratePercent,
						chaptersCount = totalCount,
						deletedAt = 0L,
					),
				)

				val existingFastView = db.getAuxiliaryDao().getFastView(mangaId)
				db.getAuxiliaryDao().upsertFastView(
					LibraryFastViewEntity(
						mangaId = mangaId,
						totalChapters = totalCount,
						readChapters = readCount,
						unreadCount = finalUnread,
						lastReadAt = now,
						latestChapterDate = existingFastView?.latestChapterDate
							?: updatedChapters.maxOfOrNull { it.uploadDate }
							?: now,
						avgReleaseIntervalDays = existingFastView?.avgReleaseIntervalDays ?: 7,
						lastPolledAt = existingFastView?.lastPolledAt ?: now,
					),
				)

				db.getAuxiliaryDao().upsertTrack(
					TrackEntity(
						mangaId = mangaId,
						totalChapters = totalCount,
						lastChapterId = updatedChapters.last().chapterId,
						newChapters = finalUnread,
						lastCheckTime = now,
						lastChapterDate = updatedChapters.maxOfOrNull { it.uploadDate } ?: now,
					),
				)
			} else {
				val existingFastView = db.getAuxiliaryDao().getFastView(mangaId)
				finalUnread = existingFastView?.unreadCount ?: chapters.count { !it.isRead }
			}
		}

		ReverseSyncSummary(
			mangaId = mangaId,
			previousLocalHighestChapter = previousHighest,
			remoteLastChapterRead = remoteLastChapterRead,
			newlyMarkedChaptersCount = newlyMarked,
			updatedUnreadCount = finalUnread,
		)
	}
}
