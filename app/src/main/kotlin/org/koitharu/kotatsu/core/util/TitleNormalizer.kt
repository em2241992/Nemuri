package org.koitharu.kotatsu.core.util

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

object TitleNormalizer {

	// Strips bracketed tags like [Official], (Colored), (Digital), (Manga), scanlator tags
	private val BRACKET_TAG_REGEX = Regex("""[\[\(\{][^\]\)\}]*[\]\)\}]""")

	// Strips common suffix noise across sources
	private val NOISE_SUFFIX_REGEX = Regex(
		"""\b(official|colored|full color|digital|ver\.?|version|edition|manga|manhwa|manhua|webtoon|scan|scans)\b""",
		RegexOption.IGNORE_CASE,
	)
	private val LEADING_ARTICLE_REGEX = Regex("""^(the|a|an)\s+""", RegexOption.IGNORE_CASE)
	private val NON_ALPHANUMERIC_REGEX = Regex("""[^\p{L}\p{N}]+""")
	private val CHAPTER_NUM_FALLBACK_REGEX = Regex(
		"""(?:ch(?:apter)?\.?\s*|ep(?:isode)?\.?\s*|#)(\d+(?:\.\d+)?)""",
		RegexOption.IGNORE_CASE,
	)

	/**
	 * Produces a canonical, source-agnostic fingerprint for matching titles across
	 * Kotatsu parsers, MangaDex (Neko), and Mihon/DropSauce extensions.
	 */
	fun toNormalizedSlug(rawTitle: String): String {
		if (rawTitle.isBlank()) return ""
		val nfkd = Normalizer.normalize(rawTitle, Normalizer.Form.NFKD)
			.replace(Regex("""\p{Mn}+"""), "") // strip diacritics
			.lowercase(Locale.ROOT)

		val cleaned = nfkd
			.replace(BRACKET_TAG_REGEX, " ")
			.replace(NOISE_SUFFIX_REGEX, " ")
			.trim()
			.replace(LEADING_ARTICLE_REGEX, "")
			.replace(NON_ALPHANUMERIC_REGEX, "")

		return cleaned.ifEmpty {
			nfkd.replace(NON_ALPHANUMERIC_REGEX, "")
		}
	}

	/**
	 * Converts float chapter numbers (e.g., 14.5f) or unparsed chapter titles into a
	 * deterministic integer key (14.5 -> 1450) so merged chapters from multiple sources
	 * align and deduplicate cleanly.
	 */
	fun toCanonicalChapterKey(chapterNumber: Float, rawChapterTitle: String?): Int {
		if (chapterNumber >= 0f) {
			return (chapterNumber * 100f).roundToInt()
		}
		if (!rawChapterTitle.isNullOrBlank()) {
			val match = CHAPTER_NUM_FALLBACK_REGEX.find(rawChapterTitle)
			val parsed = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
			if (parsed != null) {
				return (parsed * 100f).roundToInt()
			}
		}
		return -1
	}
}
