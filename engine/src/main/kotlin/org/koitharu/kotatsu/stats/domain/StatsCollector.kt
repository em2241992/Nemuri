package org.koitharu.kotatsu.stats.domain

import androidx.collection.LongSparseArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.stats.data.StatsEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsCollector @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val activeSessions = LongSparseArray<SessionEntry>()

	@Synchronized
	fun onMangaPageChanged(
		mangaId: Long,
		chapterId: Long,
		pageIndex: Int,
		totalPages: Int,
		isIncognito: Boolean,
	) {
		if (!settings.isStatsEnabled || isIncognito) return
		val normalizedTotal = totalPages.coerceAtLeast(1)
		val isChapterDone = pageIndex >= normalizedTotal - 1
		recordProgress(
			mangaId = mangaId,
			chapterId = chapterId,
			positionKey = pageIndex,
			isChapterCompleted = isChapterDone,
		)
	}

	@Synchronized
	fun onNovelProgressChanged(
		mangaId: Long,
		chapterId: Long,
		blockIndex: Int,
		totalBlocks: Int,
		isIncognito: Boolean,
	) {
		if (!settings.isStatsEnabled || isIncognito) return
		val normalizedTotal = totalBlocks.coerceAtLeast(1)
		val isChapterDone = blockIndex >= ((normalizedTotal - 1) * 0.85f).toInt()
		recordProgress(
			mangaId = mangaId,
			chapterId = chapterId,
			positionKey = blockIndex,
			isChapterCompleted = isChapterDone,
		)
	}

	@Synchronized
	fun onPauseOrClose(mangaId: Long) {
		val updated = popSessionForFlush(mangaId) ?: return
		commit(updated)
	}

	/**
	 * Suspends until the active session is flushed to Room. Used after history persistence on
	 * reader pause/exit so the `stats.manga_id` FK onto `history.manga_id` is already satisfied.
	 */
	suspend fun flushSessionNow(mangaId: Long) {
		val updated = popSessionForFlush(mangaId) ?: return
		commitInternal(updated)
	}

	@Synchronized
	private fun popSessionForFlush(mangaId: Long): StatsEntity? {
		val entry = activeSessions.get(mangaId) ?: return null
		val now = System.currentTimeMillis()
		val elapsed = (now - entry.stats.startedAt).coerceAtLeast(0L)
		val updated = entry.stats.copy(duration = elapsed)
		activeSessions.remove(mangaId)
		return if (updated.duration >= MIN_DURATION_TO_SAVE_MS || updated.pages > 0 || updated.chapters > 0) {
			updated
		} else {
			null
		}
	}

	private fun recordProgress(
		mangaId: Long,
		chapterId: Long,
		positionKey: Int,
		isChapterCompleted: Boolean,
	) {
		val now = System.currentTimeMillis()
		val existing = activeSessions.get(mangaId)
		if (existing == null) {
			val counted = HashSet<Long>()
			val initialChapters = if (isChapterCompleted && counted.add(chapterId)) 1 else 0
			activeSessions.put(
				mangaId,
				SessionEntry(
					lastChapterId = chapterId,
					lastPosition = positionKey,
					stats = StatsEntity(
						mangaId = mangaId,
						startedAt = now,
						duration = 0L,
						pages = 1,
						chapters = initialChapters,
					),
					countedChapters = counted,
				),
			)
			return
		}

		// Start a fresh session if reader sat idle for >15 minutes
		if (now - existing.lastUpdateAt > SESSION_GAP_SPLIT_MS) {
			val finalized = existing.stats.copy(duration = (existing.lastUpdateAt - existing.stats.startedAt).coerceAtLeast(0L))
			if (finalized.duration >= MIN_DURATION_TO_SAVE_MS || finalized.pages > 0) {
				commit(finalized)
			}
			activeSessions.remove(mangaId)
			recordProgress(mangaId, chapterId, positionKey, isChapterCompleted)
			return
		}

		val positionAdvanced = existing.lastChapterId != chapterId || existing.lastPosition != positionKey
		val pagesDelta = if (positionAdvanced) 1 else 0
		val chaptersDelta = if (isChapterCompleted && existing.countedChapters.add(chapterId)) 1 else 0
		val updatedStats = existing.stats.copy(
			duration = (now - existing.stats.startedAt).coerceAtLeast(0L),
			pages = existing.stats.pages + pagesDelta,
			chapters = existing.stats.chapters + chaptersDelta,
		)
		activeSessions.put(
			mangaId,
			existing.copy(
				lastChapterId = chapterId,
				lastPosition = positionKey,
				lastUpdateAt = now,
				stats = updatedStats,
			),
		)
		commit(updatedStats)
	}

	private fun commit(entity: StatsEntity) {
		scope.launch {
			commitInternal(entity)
		}
	}

	private suspend fun commitInternal(entity: StatsEntity) {
		runCatchingCancellable {
			// `stats` has a foreign key on `history(manga_id)`; wait briefly if history autosave hasn't landed yet.
			if (db.getHistoryDao().findIncludingDeleted(entity.mangaId) == null) {
				delay(1_200L)
				if (db.getHistoryDao().findIncludingDeleted(entity.mangaId) == null) {
					return@runCatchingCancellable
				}
			}
			db.getStatsDao().upsert(entity)
		}.onFailure { e ->
			e.printStackTraceDebug()
		}
	}

	private data class SessionEntry(
		val lastChapterId: Long,
		val lastPosition: Int,
		val lastUpdateAt: Long = System.currentTimeMillis(),
		val stats: StatsEntity,
		val countedChapters: MutableSet<Long>,
	)

	private companion object {
		const val MIN_DURATION_TO_SAVE_MS = 2_000L
		const val SESSION_GAP_SPLIT_MS = 15L * 60L * 1000L
	}
}
