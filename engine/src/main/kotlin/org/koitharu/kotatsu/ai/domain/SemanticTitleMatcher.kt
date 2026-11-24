package org.koitharu.kotatsu.ai.domain

import javax.inject.Inject
import javax.inject.Singleton

enum class SemanticVerdict {
	SAME_SERIES,
	DIFFERENT_WORK,
	UNCERTAIN,
}

data class SemanticPairResult(
	val verdict: SemanticVerdict,
	val reason: String,
	val engineUsed: String,
)

@Singleton
class SemanticTitleMatcher @Inject constructor(
	private val aiRouter: NemuriAiRouter,
) {

	fun isAvailable(): Boolean = aiRouter.isAiEnabled()

	suspend fun suggestAlternateQueries(
		title: String,
		altTitles: Collection<String>,
		isNovel: Boolean,
	): Result<Pair<List<String>, String>> {
		val systemPrompt =
			"You help match manga and light novel titles across catalogs. " +
				"Given a title and known aliases, list up to 3 common alternate titles " +
				"(English localization, Romaji, or official short title) for searching another source. " +
				"Output ONLY the candidate titles, one per line, without numbering or commentary."
		val kindLabel = if (isNovel) "Light Novel / Web Novel" else "Manga / Comic"
		val userPrompt = buildString {
			appendLine("Media Type: $kindLabel")
			appendLine("Primary Title: $title")
			val filteredAlts = altTitles.filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }.take(6)
			if (filteredAlts.isNotEmpty()) {
				appendLine("Known Aliases: ${filteredAlts.joinToString(" | ")}")
			}
		}
		return aiRouter.generate(systemPrompt, userPrompt, maxTokens = 120).mapCatching { res ->
			val queries = res.text
				.lineSequence()
				.map { it.trim().removePrefix("-").removePrefix("*").replace(LEADING_NUMBER_REGEX, "").trim() }
				.filter { it.length >= 3 && !it.equals(title, ignoreCase = true) }
				.distinctBy { it.lowercase() }
				.take(3)
				.toList()
			if (queries.isEmpty()) error("No alternate search titles suggested")
			queries to res.engineUsed
		}
	}

	suspend fun verifyDuplicatePair(
		titleA: String,
		altTitlesA: Collection<String>,
		titleB: String,
		altTitlesB: Collection<String>,
	): Result<SemanticPairResult> {
		val systemPrompt =
			"You compare two comic/novel library entries to decide if they are the EXACT same work or different works " +
				"(e.g., sequel, prequel, spin-off, anthology, side story, or unrelated series). " +
				"Reply in exactly two lines:\n" +
				"VERDICT: SAME_SERIES | DIFFERENT_WORK | UNCERTAIN\n" +
				"REASON: concise one-sentence explanation"
		val userPrompt = buildString {
			appendLine("Entry A: $titleA")
			if (altTitlesA.isNotEmpty()) appendLine("Aliases A: ${altTitlesA.take(5).joinToString(" | ")}")
			appendLine("Entry B: $titleB")
			if (altTitlesB.isNotEmpty()) appendLine("Aliases B: ${altTitlesB.take(5).joinToString(" | ")}")
		}
		return aiRouter.generate(systemPrompt, userPrompt, maxTokens = 140).map { res ->
			var verdict = SemanticVerdict.UNCERTAIN
			var reason = res.text.trim()
			for (line in res.text.lineSequence()) {
				val trimmed = line.trim()
				when {
					trimmed.startsWith("VERDICT:", ignoreCase = true) -> {
						val value = trimmed.substringAfter(":").trim().uppercase()
						verdict = when {
							"SAME_SERIES" in value || "SAME" == value -> SemanticVerdict.SAME_SERIES
							"DIFFERENT" in value -> SemanticVerdict.DIFFERENT_WORK
							else -> SemanticVerdict.UNCERTAIN
						}
					}

					trimmed.startsWith("REASON:", ignoreCase = true) -> {
						reason = trimmed.substringAfter(":").trim().ifEmpty { reason }
					}
				}
			}
			SemanticPairResult(verdict = verdict, reason = reason, engineUsed = res.engineUsed)
		}
	}

	private companion object {
		val LEADING_NUMBER_REGEX = Regex("""^\d+[.)]\s*""")
	}
}
