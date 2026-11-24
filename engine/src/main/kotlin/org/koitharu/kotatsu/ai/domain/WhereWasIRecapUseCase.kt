package org.koitharu.kotatsu.ai.domain

import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.ai.data.MangaAiRecapEntity
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WhereWasIRecapUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val aiRouter: NemuriAiRouter,
	private val settings: AppSettings,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
) {

	fun observeCached(mangaId: Long): Flow<MangaAiRecapEntity?> =
		db.getMangaAiRecapDao().observe(mangaId)

	suspend fun clearCache(mangaId: Long) = withContext(Dispatchers.IO) {
		db.getMangaAiRecapDao().delete(mangaId)
	}

	suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
		db.getMangaAiRecapDao().deleteAll()
	}

	suspend fun generateRecap(
		manga: Manga,
		orderedChapters: List<MangaChapter>,
		currentChapterId: Long?,
		descriptionText: CharSequence? = manga.description,
		forceRefresh: Boolean = false,
	): MangaAiRecapEntity = withContext(Dispatchers.IO) {
		val dao = db.getMangaAiRecapDao()
		val currentIndex = currentChapterId?.let { id -> orderedChapters.indexOfFirst { it.id == id } } ?: -1
		// STRICT SPOILER QUARANTINE: never inspect or pass any chapter after currentIndex.
		val readChapters = when {
			orderedChapters.isEmpty() -> emptyList()
			currentIndex >= 0 -> orderedChapters.subList(0, currentIndex + 1)
			else -> emptyList()
		}
		val currentChapter = if (currentIndex in orderedChapters.indices) orderedChapters[currentIndex] else null
		val targetChapterId = currentChapter?.id ?: (currentChapterId ?: 0L)
		val readCount = readChapters.size

		val existing = dao.find(manga.id)
		if (!forceRefresh &&
			existing != null &&
			existing.lastChapterId == targetChapterId &&
			existing.chaptersReadCount == readCount &&
			(existing.isAiGenerated || !aiRouter.isAiEnabled())
		) {
			return@withContext existing
		}

		val premisePlain = htmlToPlain(descriptionText?.toString().orEmpty()).take(MAX_PREMISE_CHARS)
		val recentTitles = readChapters.takeLast(MAX_CHAPTER_TITLES).mapIndexed { idx, ch ->
			val baseIndex = (readCount - readChapters.takeLast(MAX_CHAPTER_TITLES).size) + idx + 1
			"#$baseIndex: ${ch.title?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.number}"}"
		}

		val isProse = manga.source.isNovelSource || manga.isEpub
		val novelExcerpt = if (isProse && settings.isAiIncludeNovelExcerpt && currentChapter != null) {
			loadReadChapterExcerpt(currentChapter)
		} else {
			""
		}

		val aiResult = if (aiRouter.isAiEnabled() && (premisePlain.isNotBlank() || recentTitles.isNotEmpty())) {
			val systemPrompt =
				"You write concise, spoiler-free 'Where Was I?' reading recaps for a reader returning to a series. " +
					"CRITICAL RULE: Only reference the premise and chapters already read (listed below). NEVER speculate about or include events beyond the last read chapter. " +
					"Keep it to 3-4 crisp sentences highlighting the core premise, current arc/stopping point, and key context to jump back in."
			val userPrompt = buildString {
				appendLine("Title: ${manga.title}")
				if (manga.tags.isNotEmpty()) {
					appendLine("Genres: ${manga.tags.take(6).joinToString { it.title }}")
				}
				if (premisePlain.isNotBlank()) {
					appendLine("Premise: $premisePlain")
				}
				appendLine("Chapters Read So Far: $readCount")
				if (currentChapter != null) {
					val curName = currentChapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${currentChapter.number}"
					appendLine("Last Read Stopping Point: $curName")
				}
				if (recentTitles.isNotEmpty()) {
					appendLine("Recent Read Chapters:")
					recentTitles.forEach { appendLine("- $it") }
				}
				if (novelExcerpt.isNotBlank()) {
					appendLine("Snippet from last read chapter (already read):")
					appendLine(novelExcerpt)
				}
			}
			aiRouter.generate(systemPrompt, userPrompt, maxTokens = 280).getOrNull()
		} else {
			null
		}

		if (aiResult != null) {
			val entity = MangaAiRecapEntity(
				mangaId = manga.id,
				lastChapterId = targetChapterId,
				chaptersReadCount = readCount,
				summaryText = aiResult.text,
				engineUsed = aiResult.engineUsed,
				isAiGenerated = true,
				updatedAt = System.currentTimeMillis(),
			)
			dao.upsert(entity)
			return@withContext entity
		}

		// Honest non-AI fallback ("Reading Trail") when AI is off or unavailable;
		// never overwrite a valid cached AI recap for the same stopping point.
		if (existing != null && existing.isAiGenerated && existing.lastChapterId == targetChapterId && !forceRefresh) {
			return@withContext existing
		}

		val trailText = buildReadingTrail(
			premisePlain = premisePlain,
			readCount = readCount,
			totalCount = orderedChapters.size,
			currentChapter = currentChapter,
			recentTitles = recentTitles,
			novelExcerpt = novelExcerpt,
		)
		val fallbackEntity = MangaAiRecapEntity(
			mangaId = manga.id,
			lastChapterId = targetChapterId,
			chaptersReadCount = readCount,
			summaryText = trailText,
			engineUsed = ENGINE_READING_TRAIL,
			isAiGenerated = false,
			updatedAt = System.currentTimeMillis(),
		)
		if (existing == null || !existing.isAiGenerated) {
			dao.upsert(fallbackEntity)
		}
		fallbackEntity
	}

	private fun buildReadingTrail(
		premisePlain: String,
		readCount: Int,
		totalCount: Int,
		currentChapter: MangaChapter?,
		recentTitles: List<String>,
		novelExcerpt: String,
	): String = buildString {
		if (currentChapter != null) {
			val curName = currentChapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${currentChapter.number}"
			append("Last read: $curName")
			if (totalCount > 0) append(" ($readCount of $totalCount read)")
			append(".")
		} else {
			append("Reading trail ready — open a chapter to anchor your recap position.")
		}
		if (recentTitles.size > 1) {
			append("\nRecent: ")
			append(recentTitles.takeLast(4).joinToString(" → ") { it.substringAfter(": ") })
		}
		if (premisePlain.isNotBlank()) {
			append("\n\nPremise: ")
			append(premisePlain.take(260))
			if (premisePlain.length > 260) append("…")
		}
		if (novelExcerpt.isNotBlank()) {
			append("\n\nExcerpt: \"")
			append(novelExcerpt.take(240))
			if (novelExcerpt.length > 240) append("…")
			append("\"")
		}
	}

	private suspend fun loadReadChapterExcerpt(chapter: MangaChapter): String {
		val html = runCatchingCancellable {
			withTimeoutOrNull(5_000L) {
				val url = chapter.url
				val uri = url.toUri()
				val isZip = uri.scheme.equals("zip", ignoreCase = true) ||
					uri.scheme.equals("file+zip", ignoreCase = true) ||
					url.substringBefore('#').endsWith(".epub", ignoreCase = true)
				if (isZip) {
					val zipPath = uri.path?.takeIf { it.isNotBlank() }
						?: uri.schemeSpecificPart.substringBefore('#').removePrefix("//")
					val entryName = uri.fragment.orEmpty().removePrefix("/")
					if (zipPath.isNotBlank() && entryName.isNotBlank()) {
						ZipFile(File(zipPath)).use { zip ->
							val entry = zip.getEntry(entryName)
								?: zip.entries().asSequence().firstOrNull { it.name.equals(entryName, ignoreCase = true) }
							if (entry != null) {
								return@withTimeoutOrNull zip.getInputStream(entry).bufferedReader().use { it.readText() }
							}
						}
					}
				}
				mangaRepositoryFactoryProvider.get().create(chapter.source).getChapterHtml(chapter)
			}
		}.getOrNull().orEmpty()
		if (html.isBlank()) return ""
		return htmlToPlain(html).take(MAX_EXCERPT_CHARS)
	}

	private fun htmlToPlain(raw: String): String {
		if (raw.isBlank()) return ""
		val stripped = raw
			.replace(SCRIPT_STYLE_REGEX, "")
			.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString() }
		return stripped.replace(WHITESPACE_REGEX, " ").trim()
	}

	private companion object {
		const val ENGINE_READING_TRAIL = "Reading Trail (Local)"
		const val MAX_PREMISE_CHARS = 700
		const val MAX_CHAPTER_TITLES = 12
		const val MAX_EXCERPT_CHARS = 1200
		val SCRIPT_STYLE_REGEX = Regex("(?is)<(head|style|script|title)[^>]*>.*?</\\1>")
		val WHITESPACE_REGEX = Regex("\\s+")
	}
}
