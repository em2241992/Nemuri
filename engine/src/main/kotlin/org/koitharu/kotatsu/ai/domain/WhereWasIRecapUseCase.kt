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
		val rawIndex = currentChapterId?.let { id -> orderedChapters.indexOfFirst { it.id == id } } ?: -1
		// If currentChapterId isn't in orderedChapters due to branch mismatch, fall back gracefully
		// to orderedChapters.firstOrNull() so valid reading history never yields an empty read trail.
		val effectiveIndex = when {
			orderedChapters.isEmpty() -> -1
			rawIndex >= 0 -> rawIndex
			currentChapterId != null -> 0
			else -> -1
		}
		// STRICT SPOILER QUARANTINE: never inspect or pass any chapter after effectiveIndex.
		val readChapters = if (effectiveIndex >= 0) {
			orderedChapters.subList(0, effectiveIndex + 1)
		} else {
			emptyList()
		}
		val currentChapter = when {
			effectiveIndex in orderedChapters.indices -> orderedChapters[effectiveIndex]
			currentChapterId != null -> orderedChapters.firstOrNull()
			else -> null
		}
		val targetChapterId = if (rawIndex >= 0) {
			currentChapter?.id ?: 0L
		} else {
			currentChapterId ?: currentChapter?.id ?: 0L
		}
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
		val cleanChapterLabels = recentSlice.map { ch ->
			ch.title?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.number}"
		}

		val isProse = manga.source.isNovelSource || manga.isEpub
		val includeExcerpt = isProse && settings.isAiIncludeNovelExcerpt &&
			(depth == RecapDepth.DETAILED || premisePlain.isBlank())
		val novelExcerpt = if (includeExcerpt && currentChapter != null) {
			loadReadChapterExcerpt(
				chapter = currentChapter,
				maxChars = if (depth == RecapDepth.BRIEF) MAX_BRIEF_EXCERPT_CHARS else MAX_DETAILED_EXCERPT_CHARS,
			)
		} else {
			""
		}

		val lastReadChapterTitle = currentChapter?.let { ch ->
			ch.title?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.number}"
		} ?: if (readCount > 0) "Chapter $readCount" else "Chapter 1"

		val aiResult = if (aiRouter.isAiEnabled()) {
			val systemPrompt = when (depth) {
				RecapDepth.BRIEF ->
					"You write Nemuri's spoiler-safe 'Where Was I?' refresher for returning readers. " +
						"Write a crisp, atmospheric 2-sentence hook in natural prose reminding the reader of the story's core premise and their current stopping point at $lastReadChapterTitle. " +
						"Do NOT use robotic section labels like 'Premise:' or 'Stopping Point:', and NEVER reveal or hint at anything occurring after $lastReadChapterTitle."

				RecapDepth.DETAILED ->
					"You write Nemuri's 'Where Was I?' story recap in natural, engaging prose for a returning reader paused at $lastReadChapterTitle. " +
						"Explicitly name concrete characters, key relationships, faction conflicts, abilities, and concrete plot events established up to and including $lastReadChapterTitle. " +
						"Write cohesive paragraphs or flowing sentences with ZERO robotic headers (never write 'Premise:', 'Stopping Point:', or 'Summary:'). " +
						"STRICT SPOILER QUARANTINE: NEVER mention, foreshadow, or hint at any events, twists, or character fates occurring AFTER $lastReadChapterTitle."
			}

			val narrativeStorySheet = buildNarrativeStorySheet(
				manga = manga,
				premisePlain = premisePlain,
				genreLimit = genreLimit,
				readCount = readCount,
				totalCount = orderedChapters.size,
				currentChapter = currentChapter,
				lastReadChapterTitle = lastReadChapterTitle,
				cleanChapterLabels = cleanChapterLabels,
				novelExcerpt = novelExcerpt,
			)

			val rawUserPrompt = when (depth) {
				RecapDepth.BRIEF -> buildString {
					appendLine(narrativeStorySheet)
					appendLine()
					append(
						"Write a crisp 2-sentence atmospheric hook for \"${manga.title}\" anchored at $lastReadChapterTitle. " +
							"Keep the prose natural with zero 'Premise:' labels and zero spoilers past $lastReadChapterTitle.",
					)
				}

				RecapDepth.DETAILED -> buildString {
					appendLine(narrativeStorySheet)
					appendLine()
					append(
						"Write a rich 4-6 sentence natural-prose recap of \"${manga.title}\" covering concrete characters, key relationships, " +
							"faction conflicts, and plot events up to and including $lastReadChapterTitle. " +
							"Do not use headers like 'Premise:' or 'Stopping Point:', and strictly forbid anything after $lastReadChapterTitle.",
					)
				}
			}

			aiRouter.summarizeRecap(
				systemPrompt = systemPrompt,
				userPrompt = rawUserPrompt,
				articleText = narrativeStorySheet,
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
	 * Synthesizes a clean 520+ character pure third-person narrative story-sheet from series
	 * metadata, synopsis (or genre/author prose fallback when blank), chapter progress, and read
	 * chapter trail — strictly free of chat meta-instructions or repetitive loop padding so
	 * task-specific ML Kit `genai-summarization` produces natural story prose.
	 */
	private fun buildNarrativeStorySheet(
		manga: Manga,
		premisePlain: String,
		genreLimit: Int,
		readCount: Int,
		totalCount: Int,
		currentChapter: MangaChapter?,
		lastReadChapterTitle: String,
		cleanChapterLabels: List<String>,
		novelExcerpt: String,
	): String {
		val title = manga.title.trim().ifEmpty { "this series" }
		val authors = manga.authors.filter { it.isNotBlank() }.joinToString(", ")
		val genres = manga.tags.take(genreLimit).mapNotNull { it.title.trim().takeIf(String::isNotEmpty) }
		val genrePhrase = when {
			genres.isEmpty() -> ""
			genres.size == 1 -> genres.first().lowercase()
			else -> genres.dropLast(1).joinToString(", ") { it.lowercase() } + " and " + genres.last().lowercase()
		}

		return buildString(MIN_RECAP_INPUT_CHARS + 240) {
			when {
				authors.isNotEmpty() && genrePhrase.isNotEmpty() ->
					append("\"$title\", created by $authors, unfolds as a serialized $genrePhrase story. ")
				authors.isNotEmpty() ->
					append("\"$title\", created by $authors, unfolds as an ongoing serialized chronicle of conflict and discovery. ")
				genrePhrase.isNotEmpty() ->
					append("\"$title\" unfolds as a serialized $genrePhrase chronicle following its central cast through interconnected arcs. ")
				else ->
					append("\"$title\" unfolds as an ongoing serialized story following its central characters through escalating trials and shifting alliances. ")
			}

			if (premisePlain.isNotBlank()) {
				append(premisePlain)
				if (premisePlain.last() !in ".!?") append('.')
				append(' ')
			} else {
				append(
					"From its opening chapters, the narrative introduces a richly drawn setting where personal ambitions, " +
						"loyalties, and buried histories collide across every stage of the journey. ",
				)
			}

			if (currentChapter != null) {
				append("Across the chronicle so far, the narrative has advanced through Chapter $readCount")
				if (totalCount > 0) append(" of $totalCount")
				append(", reaching $lastReadChapterTitle. ")
				if (cleanChapterLabels.size > 1) {
					val earlier = cleanChapterLabels.dropLast(1).takeLast(6).joinToString(", ")
					append("The chapters leading directly to this point move through $earlier before arriving at ${cleanChapterLabels.last()}. ")
				}
			} else {
				append("At the opening threshold")
				if (totalCount > 0) append(" of its $totalCount recorded chapters")
				append(", the story establishes its core cast, initial tensions, and immediate stakes around $lastReadChapterTitle. ")
			}

			if (novelExcerpt.isNotBlank()) {
				append("Within $lastReadChapterTitle, the scene continues as follows: ")
				append(novelExcerpt)
				if (novelExcerpt.last() !in ".!?\"") append('.')
				append(' ')
			}

			val narrativeBridges = listOf(
				"Along the way, early turning points shape the bonds and rivalries among the central figures, grounding each confrontation in the choices made during the opening arcs.",
				"Every milestone leading up to $lastReadChapterTitle sharpens the central conflict of \"$title\", leaving alliances tested and immediate questions hanging in the balance as the chapter concludes.",
				"At this point in \"$title\", the hard-won progress up to $lastReadChapterTitle anchors the cast at a pivotal crossroads before the next stage of the story begins.",
			)
			for (bridge in narrativeBridges) {
				if (length >= MIN_RECAP_INPUT_CHARS) break
				append(bridge)
				append(' ')
			}
		}.trim()
	}

	private companion object {
		const val ENGINE_READING_TRAIL = "Reading Trail (Local)"
		const val MIN_RECAP_INPUT_CHARS = 560
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
