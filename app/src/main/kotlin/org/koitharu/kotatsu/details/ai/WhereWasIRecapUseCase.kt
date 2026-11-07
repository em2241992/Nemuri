package org.koitharu.kotatsu.details.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.koitharu.kotatsu.core.ai.NemuriAiCoreEngine
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.MangaAiRecapEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface RecapUiState {
	data object Hidden : RecapUiState
	data class Streaming(
		val partialText: String,
		val daysStalled: Int,
	) : RecapUiState

	data class Ready(
		val markdown: String,
		val daysStalled: Int,
		val pausedChapterId: Long = 0L,
		val pausedChapterNumber: Float = 0f,
		val fromCache: Boolean = false,
	) : RecapUiState
}

class WhereWasIRecapUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val aiEngine: NemuriAiCoreEngine,
) {
	companion object {
		const val AUTO_SHOW_STALLED_DAYS_THRESHOLD = 7
		const val MAX_QUARANTINED_TRAIL_CHAPTERS = 12
	}

	/**
	 * Builds the strictly spoiler-quarantined prompt context for `mangaId`.
	 * NEVER includes any chapter beyond `pausedChapterId` (`allChapters.take(pausedIdx + 1).takeLast(12)`).
	 */
	fun buildSpoilerQuarantinedContext(
		manga: MangaEntity,
		tags: List<TagEntity>,
		allChaptersSorted: List<ChapterEntity>,
		pausedChapterId: Long,
	): Triple<ChapterEntity, List<ChapterEntity>, String>? {
		if (allChaptersSorted.isEmpty()) return null
		val pausedIdx = allChaptersSorted.indexOfFirst { it.chapterId == pausedChapterId }
			.takeIf { it >= 0 }
			?: allChaptersSorted.indexOfLast { it.isRead }
				.takeIf { it >= 0 }
			?: return null

		val pausedChapter = allChaptersSorted[pausedIdx]
		// STRICT HARDWARE SPOILER QUARANTINE: Slice strictly up to pausedIdx + 1, then takeLast(12)
		val quarantinedTrail = allChaptersSorted
			.take(pausedIdx + 1)
			.takeLast(MAX_QUARANTINED_TRAIL_CHAPTERS)

		val tagNames = tags.joinToString(", ") { it.title }.ifBlank { "Manga" }
		val sanitizedPremise = manga.description.trim().take(360).ifBlank {
			"A ${tagNames.lowercase()} series by ${manga.authors ?: "unknown author"}."
		}
		val trailFormatted = quarantinedTrail.joinToString("\n") { ch ->
			"- Ch. ${formatChapterNumber(ch.number)}: ${ch.title}"
		}

		val payload = buildString {
			appendLine("Title: ${manga.title}")
			appendLine("Genres: $tagNames")
			appendLine("Premise: $sanitizedPremise")
			appendLine("Recently Read Chapters (Chronological up to stopping point):")
			appendLine(trailFormatted)
			appendLine("Stopping Point: Chapter ${formatChapterNumber(pausedChapter.number)} (${pausedChapter.title})")
		}.trim()

		return Triple(pausedChapter, quarantinedTrail, payload)
	}

	/**
	 * Observes or generates the spoiler-quarantined "Where Was I?" recap for `mangaId`.
	 *
	 * @param mangaId Target manga ID.
	 * @param forceShow True when user explicitly taps the "Where Was I?" chip (bypasses the 7-day stall check).
	 * @param forceRegenerate True when user taps Refresh on the recap card (bypasses the SQLite cache).
	 */
	fun observeOrGenerateRecap(
		mangaId: Long,
		forceShow: Boolean = false,
		forceRegenerate: Boolean = false,
	): Flow<RecapUiState> = flow {
		val mangaWithTags = db.getMangaDao().find(mangaId)
		if (mangaWithTags == null) {
			emit(RecapUiState.Hidden)
			return@flow
		}

		val history = db.getHistoryDao().findByMangaId(mangaId)
		val allChapters = db.getChaptersDao().findAll(mangaId).sortedBy { it.number }
		if (allChapters.isEmpty()) {
			emit(RecapUiState.Hidden)
			return@flow
		}

		val pausedChapterId = history?.chapterId
			?: allChapters.lastOrNull { it.isRead }?.chapterId
		if (pausedChapterId == null) {
			emit(RecapUiState.Hidden)
			return@flow
		}

		val now = System.currentTimeMillis()
		val lastReadAt = history?.updatedAt ?: 0L
		val daysStalled = if (lastReadAt > 0L && now > lastReadAt) {
			TimeUnit.MILLISECONDS.toDays(now - lastReadAt).toInt()
		} else {
			0
		}

		if (!forceShow && !forceRegenerate && daysStalled < AUTO_SHOW_STALLED_DAYS_THRESHOLD) {
			emit(RecapUiState.Hidden)
			return@flow
		}

		val quarantined = buildSpoilerQuarantinedContext(
			manga = mangaWithTags.manga,
			tags = mangaWithTags.tags,
			allChaptersSorted = allChapters,
			pausedChapterId = pausedChapterId,
		)
		if (quarantined == null) {
			emit(RecapUiState.Hidden)
			return@flow
		}
		val (pausedChapter, trail, contextPayload) = quarantined

		// 1. Check 0ms SQLite Recap Cache (unless user requested regeneration)
		if (!forceRegenerate) {
			val cached = db.getMangaAiRecapDao().findValidRecap(mangaId, pausedChapter.chapterId)
			if (cached != null && cached.recapMarkdown.isNotBlank()) {
				emit(
					RecapUiState.Ready(
						markdown = cached.recapMarkdown,
						daysStalled = daysStalled,
						pausedChapterId = pausedChapter.chapterId,
						pausedChapterNumber = pausedChapter.number,
						fromCache = true,
					),
				)
				return@flow
			}
		}

		// 2. Attempt live token streaming from Gemini Nano on-device AICore
		val accumulated = StringBuilder()
		runCatching {
			aiEngine.streamRecap(contextPayload).collect { chunk ->
				if (chunk.isNotEmpty()) {
					accumulated.append(chunk)
					emit(
						RecapUiState.Streaming(
							partialText = accumulated.toString(),
							daysStalled = daysStalled,
						),
					)
				}
			}
		}

		// 3. Fallback to zero-latency deterministic 3-bullet extractive summary if NPU stream is empty
		val finalMarkdown = accumulated.toString().trim().ifBlank {
			buildDeterministicFallbackRecap(
				manga = mangaWithTags.manga,
				tags = mangaWithTags.tags,
				quarantinedTrail = trail,
				pausedChapter = pausedChapter,
			)
		}

		// 4. Persist in manga_ai_recaps so subsequent opens take 0ms and 0 NPU power
		db.getMangaAiRecapDao().upsert(
			MangaAiRecapEntity(
				mangaId = mangaId,
				pausedChapterId = pausedChapter.chapterId,
				pausedChapterNumber = pausedChapter.number,
				recapMarkdown = finalMarkdown,
				generatedAt = System.currentTimeMillis(),
			),
		)

		emit(
			RecapUiState.Ready(
				markdown = finalMarkdown,
				daysStalled = daysStalled,
				pausedChapterId = pausedChapter.chapterId,
				pausedChapterNumber = pausedChapter.number,
				fromCache = false,
			),
		)
	}.flowOn(Dispatchers.IO)

	fun buildDeterministicFallbackRecap(
		manga: MangaEntity,
		tags: List<TagEntity>,
		quarantinedTrail: List<ChapterEntity>,
		pausedChapter: ChapterEntity,
	): String {
		val firstSentence = manga.description
			.split(Regex("""(?<=[.!?])\s+"""))
			.firstOrNull { it.isNotBlank() }
			?.trim()
			?.take(140)
			?: "${manga.title} follows a ${tags.take(3).joinToString("/") { it.title }.ifBlank { "dramatic" }} journey."

		val recentSlice = quarantinedTrail.takeLast(4)
		val arcSummary = if (recentSlice.size >= 2) {
			val startCh = recentSlice.first()
			val prevCh = recentSlice[recentSlice.size - 2]
			"Recent chapters progressed from Ch. ${formatChapterNumber(startCh.number)} (${startCh.title}) through Ch. ${formatChapterNumber(prevCh.number)} (${prevCh.title})."
		} else {
			"You began the opening arc in Ch. ${formatChapterNumber(pausedChapter.number)} (${pausedChapter.title})."
		}

		val stoppingPoint = "Paused at Chapter ${formatChapterNumber(pausedChapter.number)} — \"${pausedChapter.title}\"."

		return buildString {
			appendLine("• Premise: $firstSentence")
			appendLine("• Recent Progression: $arcSummary")
			append("• Stopping Point: $stoppingPoint")
		}
	}

	private fun formatChapterNumber(num: Float): String {
		val asInt = num.toInt()
		return if (num == asInt.toFloat()) asInt.toString() else num.toString()
	}
}
