package org.koitharu.kotatsu.search.ai

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koitharu.kotatsu.core.ai.NemuriAiCoreEngine
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.util.TitleNormalizer
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Serializable
enum class SmartCompletionFilter { ALL, COMPLETED_ONLY, ONGOING_ONLY }

@Serializable
enum class SmartSortOrder { RELEVANCE, UNREAD_ASC, UNREAD_DESC, STALLED_OLDEST, LATEST_RELEASE }

@Serializable
data class LibrarySmartQueryAst(
	val ftsKeywords: List<String> = emptyList(),
	val includedTags: List<String> = emptyList(),
	val excludedTags: List<String> = emptyList(),
	val completionState: SmartCompletionFilter = SmartCompletionFilter.ALL,
	val minUnread: Int? = null,
	val maxUnread: Int? = null,
	val stalledDaysMin: Int? = null,
	val sortOrder: SmartSortOrder = SmartSortOrder.RELEVANCE,
) {
	val hasStructuredConstraints: Boolean
		get() = includedTags.isNotEmpty() ||
			excludedTags.isNotEmpty() ||
			completionState != SmartCompletionFilter.ALL ||
			minUnread != null ||
			maxUnread != null ||
			stalledDaysMin != null ||
			sortOrder != SmartSortOrder.RELEVANCE

	fun toDisplayPills(): List<String> = buildList {
		when (completionState) {
			SmartCompletionFilter.COMPLETED_ONLY -> add("Status: Completed")
			SmartCompletionFilter.ONGOING_ONLY -> add("Status: Ongoing")
			SmartCompletionFilter.ALL -> Unit
		}
		if (minUnread != null && maxUnread != null) {
			add("Unread: $minUnread..$maxUnread")
		} else if (maxUnread != null) {
			add("Unread: ≤ $maxUnread")
		} else if (minUnread != null) {
			add("Unread: ≥ $minUnread")
		}
		if (stalledDaysMin != null) {
			add("Stalled: ≥ ${stalledDaysMin}d")
		}
		includedTags.forEach { tag -> add("+$tag") }
		excludedTags.forEach { tag -> add("−$tag") }
		ftsKeywords.forEach { kw -> add("\"$kw\"") }
		if (sortOrder != SmartSortOrder.RELEVANCE) {
			add("Sort: ${sortOrder.name}")
		}
	}
}

class NaturalLanguageLibrarySearchUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val aiEngine: NemuriAiCoreEngine,
) {
	private val jsonParser = Json {
		ignoreUnknownKeys = true
		isLenient = true
		coerceInputValues = true
	}

	/**
	 * Stage 1: 0ms Deterministic Heuristic Parser (runs immediately on every keystroke).
	 */
	fun parseFastHeuristic(rawQuery: String): LibrarySmartQueryAst {
		val lower = rawQuery.lowercase()
		var working = lower

		val completion = when {
			Regex("""\b(not completed|uncompleted|ongoing|publishing)\b""").containsMatchIn(working) -> {
				working = working.replace(Regex("""\b(not completed|uncompleted|ongoing|publishing)\b"""), " ")
				SmartCompletionFilter.ONGOING_ONLY
			}
			Regex("""\b(completed|finished|complete)\b""").containsMatchIn(working) -> {
				working = working.replace(Regex("""\b(completed|finished|complete)\b"""), " ")
				SmartCompletionFilter.COMPLETED_ONLY
			}
			else -> SmartCompletionFilter.ALL
		}

		// Match "under 10 unread", "less than 5 unread", "< 10 unread"
		val maxUnreadMatch = Regex("""(?:under|less than|max|fewer than)\s+(\d+)\s+unread""").find(working)
		val maxUnread = maxUnreadMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
		if (maxUnreadMatch != null) working = working.replace(maxUnreadMatch.value, " ")

		val minUnreadMatch = Regex("""(?:over|more than|at least)\s+(\d+)\s+unread""").find(working)
		val minUnread = minUnreadMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
			?: if (working.contains("unread")) 1 else null
		if (minUnreadMatch != null) working = working.replace(minUnreadMatch.value, " ")

		val stalledDays = when {
			working.contains("month") || working.contains("stalled") -> {
				working = working.replace(Regex("""\b(month|months|stalled)\b"""), " ")
				30
			}
			working.contains("week") -> {
				working = working.replace(Regex("""\b(week|weeks)\b"""), " ")
				7
			}
			working.contains("year") -> {
				working = working.replace(Regex("""\b(year|years)\b"""), " ")
				365
			}
			else -> null
		}

		val stopWords = setOf(
			"with", "that", "have", "havent", "read", "in", "a", "month", "week", "year",
			"unread", "chapters", "chapter", "stalled", "on", "my", "library", "or", "and", "the",
			"manga", "series", "show", "me", "find", "under", "less", "than", "over", "more",
		)
		val tokens = working.split(Regex("""\s+"""))
			.map { it.replace(Regex("""[^\p{L}\p{N}]"""), "") }
			.filter { it.length >= 2 && it !in stopWords }

		return LibrarySmartQueryAst(
			ftsKeywords = tokens,
			completionState = completion,
			minUnread = minUnread,
			maxUnread = maxUnread,
			stalledDaysMin = stalledDays,
		)
	}

	/**
	 * Stage 2: Gemini Nano AICore Semantic Expansion (expands genres/themes & complex phrasing).
	 */
	suspend fun parseWithGeminiNano(rawQuery: String): LibrarySmartQueryAst {
		val fastAst = parseFastHeuristic(rawQuery)
		// Short 1-2 word title searches don't need LLM expansion
		if (rawQuery.trim().split(Regex("""\s+""")).size <= 2) {
			return fastAst
		}

		val rawJson = aiEngine.generateSearchAstJson(rawQuery) ?: return fastAst
		val cleanJson = rawJson
			.removePrefix("```json")
			.removePrefix("```")
			.removeSuffix("```")
			.trim()

		return runCatching {
			jsonParser.decodeFromString<LibrarySmartQueryAst>(cleanJson)
		}.getOrDefault(fastAst)
	}

	/**
	 * Compiles the LibrarySmartQueryAst into a single indexed SQLite query joining
	 * favourites, manga, library_fast_view, manga_fts, and manga_aliases.
	 */
	fun compileToSqlQuery(ast: LibrarySmartQueryAst): SupportSQLiteQuery {
		val args = mutableListOf<Any>()
		val sql = StringBuilder(
			"""
			SELECT DISTINCT m.* FROM manga m
			INNER JOIN favourites f ON f.manga_id = m.manga_id AND f.deleted_at = 0
			LEFT JOIN library_fast_view lfv ON lfv.manga_id = m.manga_id
			""".trimIndent(),
		)

		if (ast.includedTags.isNotEmpty()) {
			sql.append(" INNER JOIN manga_tags mt ON mt.manga_id = m.manga_id INNER JOIN tags t ON t.tag_id = mt.tag_id ")
		}

		sql.append(" WHERE 1=1 ")

		// 1. Completion state filter
		when (ast.completionState) {
			SmartCompletionFilter.COMPLETED_ONLY -> sql.append(" AND m.state = 'FINISHED' ")
			SmartCompletionFilter.ONGOING_ONLY -> sql.append(" AND m.state != 'FINISHED' ")
			SmartCompletionFilter.ALL -> Unit
		}

		// 2. Unread chapter bounds from denormalized library_fast_view
		if (ast.minUnread != null) {
			sql.append(" AND COALESCE(lfv.unread_count, 0) >= ? ")
			args.add(ast.minUnread)
		}
		if (ast.maxUnread != null) {
			sql.append(" AND COALESCE(lfv.unread_count, 0) <= ? ")
			args.add(ast.maxUnread)
		}

		// 3. Stalled reading filter
		if (ast.stalledDaysMin != null) {
			val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ast.stalledDaysMin.toLong())
			sql.append(" AND COALESCE(lfv.last_read_at, 0) > 0 AND lfv.last_read_at <= ? ")
			args.add(cutoffTimestamp)
		}

		// 4. Semantic Tag matches (included & excluded)
		if (ast.includedTags.isNotEmpty()) {
			val placeholders = ast.includedTags.joinToString(",") { "?" }
			sql.append(" AND LOWER(t.title) IN ($placeholders) ")
			ast.includedTags.forEach { args.add(it.lowercase()) }
		}
		if (ast.excludedTags.isNotEmpty()) {
			val placeholders = ast.excludedTags.joinToString(",") { "?" }
			sql.append(
				"""
				 AND m.manga_id NOT IN (
					SELECT mt_ex.manga_id FROM manga_tags mt_ex
					INNER JOIN tags t_ex ON t_ex.tag_id = mt_ex.tag_id
					WHERE LOWER(t_ex.title) IN ($placeholders)
				) 
				""".trimIndent(),
			)
			ast.excludedTags.forEach { args.add(it.lowercase()) }
		}

		// 5. FTS5 + Normalized Alias + Tag/Slug lookup for title/author/genre keywords
		if (ast.ftsKeywords.isNotEmpty()) {
			val ftsMatchExpr = ast.ftsKeywords.joinToString(" OR ") { "${it}*" }
			val combinedSlug = TitleNormalizer.toNormalizedSlug(ast.ftsKeywords.joinToString(" "))
			val keywordPlaceholders = ast.ftsKeywords.joinToString(",") { "?" }
			sql.append(
				"""
				 AND (
					m.manga_id IN (SELECT rowid FROM manga_fts WHERE manga_fts MATCH ?)
					OR m.manga_id IN (SELECT manga_id FROM manga_aliases WHERE alias_slug LIKE ?)
					OR m.normalized_slug LIKE ?
					OR m.manga_id IN (
						SELECT mt_kw.manga_id FROM manga_tags mt_kw
						INNER JOIN tags t_kw ON t_kw.tag_id = mt_kw.tag_id
						WHERE LOWER(t_kw.title) IN ($keywordPlaceholders)
					)
				) 
				""".trimIndent(),
			)
			args.add(ftsMatchExpr)
			args.add("%$combinedSlug%")
			args.add("%$combinedSlug%")
			ast.ftsKeywords.forEach { args.add(it.lowercase()) }
		}

		// 6. Ordering
		when (ast.sortOrder) {
			SmartSortOrder.UNREAD_ASC -> sql.append(" ORDER BY COALESCE(lfv.unread_count, 0) ASC ")
			SmartSortOrder.UNREAD_DESC -> sql.append(" ORDER BY COALESCE(lfv.unread_count, 0) DESC ")
			SmartSortOrder.STALLED_OLDEST -> sql.append(" ORDER BY COALESCE(lfv.last_read_at, 0) ASC ")
			SmartSortOrder.LATEST_RELEASE, SmartSortOrder.RELEVANCE -> {
				sql.append(" ORDER BY COALESCE(lfv.latest_chapter_date, 0) DESC ")
			}
		}

		sql.append(" LIMIT 200")
		return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
	}

	suspend fun executeSmartSearch(ast: LibrarySmartQueryAst): List<MangaEntity> {
		val rawQuery = compileToSqlQuery(ast)
		return db.getMangaDao().findByRawQuery(rawQuery)
	}
}
