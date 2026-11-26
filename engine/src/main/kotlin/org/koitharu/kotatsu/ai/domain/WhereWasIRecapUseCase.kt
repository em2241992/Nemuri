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
		depthOverride: RecapDepth? = null,
	): MangaAiRecapEntity = withContext(Dispatchers.IO) {
		val depth = depthOverride ?: RecapDepth.fromKey(settings.aiRecapDepth)
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
			existing.parsedDepth() == depth &&
			(existing.isAiGenerated || !aiRouter.isAiEnabled())
		) {
			return@withContext existing
		}

		val premiseLimit = if (depth == RecapDepth.BRIEF) MAX_BRIEF_PREMISE_CHARS else MAX_DETAILED_PREMISE_CHARS
		val titleLimit = if (depth == RecapDepth.BRIEF) MAX_BRIEF_CHAPTER_TITLES else MAX_DETAILED_CHAPTER_TITLES
		val genreLimit = if (depth == RecapDepth.BRIEF) 6 else 14

		val premisePlain = htmlToPlain(descriptionText?.toString().orEmpty()).take(premiseLimit)
		val recentSlice = readChapters.takeLast(titleLimit)
		val recentTitles = recentSlice.mapIndexed { idx, ch ->
			val baseIndex = (readCount - recentSlice.size) + idx + 1
			"#$baseIndex: ${ch.title?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.number}"}"
		}

		val isProse = manga.source.isNovelSource || manga.isEpub
		val includeExcerpt = isProse && settings.isAiIncludeNovelExcerpt && currentChapter != null &&
			(depth == RecapDepth.DETAILED || premisePlain.isBlank())
		val novelExcerpt = if (includeExcerpt && currentChapter != null) {
			loadReadChapterExcerpt(
				chapter = currentChapter,
				maxChars = if (depth == RecapDepth.BRIEF) MAX_BRIEF_EXCERPT_CHARS else MAX_DETAILED_EXCERPT_CHARS,
			)
		} else {
			""
		}

		val lastTitle = currentChapter?.let { ch ->
			ch.title?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.number}"
		} ?: "the current reading checkpoint"

		val aiResult = if (aiRouter.isAiEnabled() && (premisePlain.isNotBlank() || recentTitles.isNotEmpty())) {
			val systemPrompt = when (depth) {
				RecapDepth.BRIEF ->
					"You are Nemuri's spoiler-safe 'Where Was I?' assistant. Write a concise 2-3 sentence atmospheric safe teaser " +
						"reminding a returning reader of the series premise and current arc position up to $lastTitle. " +
						"Do NOT name major plot twists, character deaths/fates, or anything occurring after $lastTitle."

				RecapDepth.DETAILED ->
					"You are Nemuri's 'Where Was I?' story and character recap assistant. Help a returning reader remember concrete story details up to their exact stopping point. " +
						"You MAY explicitly name specific characters, key relationships, faction conflicts, abilities, and concrete plot events that occurred AT OR BEFORE $lastTitle, " +
						"leveraging the provided context and your prior series knowledge strictly up to $lastTitle. " +
						"CRITICAL ANTI-SPOILER RULE: NEVER mention, foreshadow, or hint at any events, twists, or character fates occurring AFTER $lastTitle."
			}

			val contextBlock = buildString {
				appendLine("Title: ${manga.title}")
				if (manga.authors.isNotEmpty() && depth == RecapDepth.DETAILED) {
					appendLine("Author(s): ${manga.authors.joinToString(", ")}")
				}
				if (manga.tags.isNotEmpty()) {
					appendLine("Genres: ${manga.tags.take(genreLimit).joinToString { it.title }}")
				}
				if (premisePlain.isNotBlank()) {
					appendLine("Synopsis: $premisePlain")
				}
				appendLine("Chapters Read So Far: $readCount${if (orderedChapters.isNotEmpty()) " of ${orderedChapters.size}" else ""}")
				if (currentChapter != null) {
					appendLine("Last Read Stopping Point: $lastTitle")
				}
				if (recentTitles.isNotEmpty()) {
					appendLine("Read Chapters Leading Here:")
					recentTitles.forEach { appendLine("- $it") }
				}
				if (novelExcerpt.isNotBlank()) {
					appendLine("Read Chapter Excerpt (from $lastTitle):")
					appendLine(novelExcerpt)
				}
			}

			val rawUserPrompt = when (depth) {
				RecapDepth.BRIEF -> buildString {
					appendLine(contextBlock.trimEnd())
					appendLine()
					append("Task: Write a brief, atmospheric, spoiler-free 2-3 sentence teaser anchored at $lastTitle without revealing major plot twists or character fates.")
				}

				RecapDepth.DETAILED -> buildString {
					appendLine(contextBlock.trimEnd())
					appendLine()
					append(
						"Task: Write a detailed 4-6 sentence 'Story & Characters So Far' recap up to and including $lastTitle. " +
							"You MAY explicitly name characters, key relationships, faction conflicts, and concrete plot events occurring up to and including $lastTitle. " +
							"NEVER mention, foreshadow, or hint at any events after $lastTitle.",
					)
				}
			}

			val paddedArticle = ensureMinRecapLength(
				raw = contextBlock,
				title = manga.title,
				readCount = readCount,
				totalCount = orderedChapters.size,
				currentChapterTitle = lastTitle,
				depth = depth,
			)
			aiRouter.summarizeRecap(
				systemPrompt = systemPrompt,
				userPrompt = rawUserPrompt,
				articleText = paddedArticle,
				maxTokens = if (depth == RecapDepth.BRIEF) 200 else 520,
				recapDepth = depth,
			).getOrNull()
		} else {
			null
		}

		if (aiResult != null) {
			val entity = MangaAiRecapEntity(
				mangaId = manga.id,
				lastChapterId = targetChapterId,
				chaptersReadCount = readCount,
				summaryText = aiResult.text,
				engineUsed = MangaAiRecapEntity.encodeEngineWithDepth(aiResult.engineUsed, depth),
				isAiGenerated = true,
				updatedAt = System.currentTimeMillis(),
			)
			dao.upsert(entity)
			return@withContext entity
		}

		// Honest non-AI fallback ("Reading Trail") when AI is off or unavailable;
		// never overwrite a valid cached AI recap for the same stopping point & depth.
		if (existing != null &&
			existing.isAiGenerated &&
			existing.lastChapterId == targetChapterId &&
			existing.parsedDepth() == depth &&
			!forceRefresh
		) {
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
			engineUsed = MangaAiRecapEntity.encodeEngineWithDepth(ENGINE_READING_TRAIL, depth),
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

	private suspend fun loadReadChapterExcerpt(chapter: MangaChapter, maxChars: Int): String {
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
		return htmlToPlain(html).take(maxChars)
	}

	private fun htmlToPlain(raw: String): String {
		if (raw.isBlank()) return ""
		val stripped = raw
			.replace(SCRIPT_STYLE_REGEX, "")
			.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString() }
		return stripped.replace(WHITESPACE_REGEX, " ").trim()
	}

	/**
	 * Pads short synopses + read chapter trails to `>= 420` characters so ML Kit's 400-character
	 * minimum summarizer input threshold never rejects concise series entries.
	 */
	private fun ensureMinRecapLength(
		raw: String,
		title: String,
		readCount: Int,
		totalCount: Int,
		currentChapterTitle: String?,
		depth: RecapDepth,
	): String {
		val trimmed = raw.trim()
		if (trimmed.length >= MIN_RECAP_INPUT_CHARS) return trimmed
		return buildString(MIN_RECAP_INPUT_CHARS + 96) {
			appendLine(trimmed)
			appendLine()
			val anchor = currentChapterTitle ?: "the current reading checkpoint"
			append("Reader Status: The reader is returning to \"$title\" after reading $readCount")
			if (totalCount > 0) append(" out of $totalCount")
			append(" chapters, paused right at $anchor. ")
			if (depth == RecapDepth.DETAILED) {
				append("Detail the main characters, key relationships, faction dynamics, and established story events up to $anchor without revealing anything beyond $anchor.")
			} else {
				append("Give a brief, spoiler-free atmospheric refresher anchored up to $anchor without revealing twists or later chapters.")
			}
			while (length < MIN_RECAP_INPUT_CHARS) {
				append(" Keep the recap strictly anchored to \"$title\" at $anchor.")
			}
		}
	}

	private companion object {
		const val ENGINE_READING_TRAIL = "Reading Trail (Local)"
		const val MIN_RECAP_INPUT_CHARS = 430
		const val MAX_BRIEF_PREMISE_CHARS = 550
		const val MAX_DETAILED_PREMISE_CHARS = 1600
		const val MAX_BRIEF_CHAPTER_TITLES = 6
		const val MAX_DETAILED_CHAPTER_TITLES = 20
		const val MAX_BRIEF_EXCERPT_CHARS = 400
		const val MAX_DETAILED_EXCERPT_CHARS = 1800
		val SCRIPT_STYLE_REGEX = Regex("(?is)<(head|style|script|title)[^>]*>.*?</\\1>")
		val WHITESPACE_REGEX = Regex("\\s+")
	}
}
