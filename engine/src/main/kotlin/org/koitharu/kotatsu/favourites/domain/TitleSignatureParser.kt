package org.koitharu.kotatsu.favourites.domain

import java.text.Normalizer
import java.util.Locale

enum class EditionFlag(
	val key: String,
	val label: String,
	val badgeText: String,
) {
	COLORED("COLORED", "Colored Edition", "🎨 Colored"),
	UNCENSORED("UNCENSORED", "Uncensored / Mature Edition", "🔞 Uncensored"),
	ORIGIN_VARIANT("ORIGIN_VARIANT", "Webcomic / Pre-Serialization", "📜 Webcomic"),
	SPINOFF("SPINOFF", "Spin-Off / Side Story", "🌿 Spin-Off"),
}

data class ParsedTitleSignature(
	val rawTitle: String,
	val cleanCoreTitle: String,
	val wordTokens: List<String>,
	val partOrSequelMarker: String?,
	val editionFlags: Set<EditionFlag>,
) {
	val hasNonStandardEdition: Boolean
		get() = editionFlags.any { it != EditionFlag.SPINOFF }
}

object TitleSignatureParser {

	private val BRACKET_SEGMENT_REGEX = Regex("""\[([^\]]+)]|\(([^)]+)\)|\{([^}]+)}|【([^】]+)】|《([^》]+)》""")

	private val COLORED_REGEX = Regex(
		"""\b(full[\s-]*colou?r(?:ed)?|digital[\s-]*colou?r(?:ed)?|fan[\s-]*colou?r(?:ed)?|official[\s-]*colou?r(?:ed)?|colou?red(?:\s+edition|\s+ver\.?)?|all[\s-]*colou?r)\b""",
		RegexOption.IGNORE_CASE,
	)

	private val UNCENSORED_REGEX = Regex(
		"""\b(uncensored|decensored|untrimmed|mature[\s-]*(?:version|ver\.?|edition)|r[\s-]*18[\s-]*(?:version|ver\.?|edition)?|18\+[\s-]*(?:version|edition)|censored[\s-]*(?:version|edition))\b""",
		RegexOption.IGNORE_CASE,
	)

	private val ORIGIN_VARIANT_REGEX = Regex(
		"""\b(original[\s-]*webcomic|web[\s-]*comic|pre[\s-]*seriali[sz]ation|one[\s-]*shot|oneshot|pilot(?:\s+chapter|\s+ver\.?)?|draft[\s-]*version|prototype)\b""",
		RegexOption.IGNORE_CASE,
	)

	private val SPINOFF_REGEX = Regex(
		"""\b(gaiden|side[\s-]*stor(?:y|ies)|spin[\s-]*off|spinoff|anthology|4[\s-]*koma|yonkoma|after[\s-]*story|extra[\s-]*story|omake|official[\s-]*comic[\s-]*anthology)\b""",
		RegexOption.IGNORE_CASE,
	)

	private val COLON_RE_REGEX = Regex("""(?::\s*re\b|\bre\s*:(?!\s*zero))""", RegexOption.IGNORE_CASE)
	private val PART_SEASON_REGEX = Regex(
		"""\b(?:part|season|cour|arc|act|phase|book|stage)\s*([0-9]+|ii|iii|iv|v|vi|vii|viii|ix|x)\b""",
		RegexOption.IGNORE_CASE,
	)
	private val SHORT_SEASON_REGEX = Regex("""\bs([2-9]|[1-9][0-9])\b""", RegexOption.IGNORE_CASE)
	private val ORDINAL_SEASON_REGEX = Regex("""\b([2-9]|[1-9][0-9])(?:nd|rd|th)\s+season\b""", RegexOption.IGNORE_CASE)
	private val TRAILING_ROMAN_REGEX = Regex("""\s+(ii|iii|iv|v|vi|vii|viii|ix)\s*$""", RegexOption.IGNORE_CASE)
	private val TRAILING_NUMBER_REGEX = Regex("""\s+([2-9])\s*$""")

	private val NOISE_WORDS = setOf(
		"official",
		"digital",
		"scan",
		"scans",
		"scanlation",
		"manga",
		"manhwa",
		"manhua",
		"webtoon",
		"tapas",
		"tappytoon",
		"lezhin",
		"manta",
		"pocket",
		"comics",
		"comic",
		"version",
		"ver",
		"edition",
		"english",
		"eng",
		"raw",
		"hq",
		"hd",
		"remastered",
	)

	private val STOP_WORDS = setOf("the", "a", "an")

	fun parse(
		rawTitle: String,
		tags: Collection<String> = emptyList(),
	): ParsedTitleSignature {
		val normalizedFull = stripDiacritics(rawTitle).trim()
		val flags = mutableSetOf<EditionFlag>()

		val combinedSignal = buildString {
			append(normalizedFull)
			for (tag in tags) {
				append(' ')
				append(stripDiacritics(tag))
			}
		}

		if (COLORED_REGEX.containsMatchIn(combinedSignal) || tags.any { isColoredTag(it) }) {
			flags.add(EditionFlag.COLORED)
		}
		if (UNCENSORED_REGEX.containsMatchIn(combinedSignal) || tags.any { isUncensoredTag(it) }) {
			flags.add(EditionFlag.UNCENSORED)
		}
		if (ORIGIN_VARIANT_REGEX.containsMatchIn(normalizedFull) || tags.any { isOriginVariantTag(it) }) {
			flags.add(EditionFlag.ORIGIN_VARIANT)
		}
		if (SPINOFF_REGEX.containsMatchIn(combinedSignal) || tags.any { isSpinoffTag(it) }) {
			flags.add(EditionFlag.SPINOFF)
		}

		var working = normalizedFull

		// Detect explicit :re sequel marker before punctuation stripping
		var partMarker: String? = null
		if (COLON_RE_REGEX.containsMatchIn(working)) {
			partMarker = "re"
			working = COLON_RE_REGEX.replace(working, " ")
		}

		// Strip bracketed publisher/edition annotations while preserving sequel markers inside brackets if any
		working = BRACKET_SEGMENT_REGEX.replace(working) { match ->
			val inner = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
			val sequelInside = extractSequelMarker(inner)
			if (sequelInside != null && partMarker == null) {
				partMarker = sequelInside
			}
			" "
		}

		if (partMarker == null) {
			partMarker = extractSequelMarker(working)
		}

		// Remove detected edition/sequel phrases from core title
		working = COLORED_REGEX.replace(working, " ")
		working = UNCENSORED_REGEX.replace(working, " ")
		working = ORIGIN_VARIANT_REGEX.replace(working, " ")
		working = SPINOFF_REGEX.replace(working, " ")
		working = PART_SEASON_REGEX.replace(working, " ")
		working = SHORT_SEASON_REGEX.replace(working, " ")
		working = ORDINAL_SEASON_REGEX.replace(working, " ")

		if (partMarker == null) {
			TRAILING_ROMAN_REGEX.find(working)?.let { m ->
				val prefix = working.substring(0, m.range.first).trim()
				if (prefix.length >= 3) {
					partMarker = romanToIntString(m.groupValues[1])
					working = prefix
				}
			}
		}
		if (partMarker == null) {
			TRAILING_NUMBER_REGEX.find(working)?.let { m ->
				val prefix = working.substring(0, m.range.first).trim()
				// Avoid treating "Level 2" or "Room 3" as sequel #2
				val lastWord = prefix.substringAfterLast(' ').lowercase(Locale.ROOT)
				if (prefix.length >= 4 && lastWord !in setOf("level", "lv", "lvl", "no", "number", "room", "district", "ward", "unit", "type", "rank", "tier", "class", "age", "year", "chapter", "vol", "volume")) {
					partMarker = m.groupValues[1]
					working = prefix
				}
			}
		}

		val rawTokens = working
			.lowercase(Locale.ROOT)
			.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
			.trim()
			.split(Regex("""\s+"""))
			.filter { it.isNotBlank() }

		val filteredNoise = rawTokens.filterNot { it in NOISE_WORDS }
		val baseTokens = filteredNoise.ifEmpty { rawTokens }
		val coreTokens = if (baseTokens.size > 1 && baseTokens.first() in STOP_WORDS) {
			baseTokens.drop(1)
		} else {
			baseTokens
		}

		val cleanCore = coreTokens.joinToString(" ")

		return ParsedTitleSignature(
			rawTitle = rawTitle,
			cleanCoreTitle = cleanCore,
			wordTokens = coreTokens,
			partOrSequelMarker = partMarker,
			editionFlags = flags,
		)
	}

	fun wordJaccard(a: ParsedTitleSignature, b: ParsedTitleSignature): Float {
		if (a.cleanCoreTitle.isBlank() || b.cleanCoreTitle.isBlank()) return 0f
		if (a.cleanCoreTitle == b.cleanCoreTitle) return 1f
		// Short 1-word titles (<= 10 chars) require exact core title match
		if ((a.wordTokens.size <= 1 && a.cleanCoreTitle.length <= 10) ||
			(b.wordTokens.size <= 1 && b.cleanCoreTitle.length <= 10)
		) {
			return 0f
		}
		val setA = a.wordTokens.toSet()
		val setB = b.wordTokens.toSet()
		if (setA.isEmpty() || setB.isEmpty()) return 0f
		val intersection = setA.count { it in setB }
		if (intersection == 0) return 0f
		val union = setA.size + setB.size - intersection
		return if (union <= 0) 0f else intersection.toFloat() / union.toFloat()
	}

	fun isHardVeto(
		isNovelA: Boolean,
		isNovelB: Boolean,
		sigA: ParsedTitleSignature,
		sigB: ParsedTitleSignature,
	): Boolean {
		if (isNovelA != isNovelB) return true
		if (sigA.partOrSequelMarker != sigB.partOrSequelMarker) return true
		if ((EditionFlag.SPINOFF in sigA.editionFlags) != (EditionFlag.SPINOFF in sigB.editionFlags)) return true
		return false
	}

	fun hasEditionDifference(sigA: ParsedTitleSignature, sigB: ParsedTitleSignature): Boolean {
		val edA = sigA.editionFlags - EditionFlag.SPINOFF
		val edB = sigB.editionFlags - EditionFlag.SPINOFF
		return edA != edB
	}

	fun hasAuthorConflict(authorA: String?, authorB: String?): Boolean {
		val tokensA = tokenizeAuthor(authorA)
		val tokensB = tokenizeAuthor(authorB)
		if (tokensA.isEmpty() || tokensB.isEmpty()) return false
		return tokensA.none { it in tokensB }
	}

	private fun tokenizeAuthor(author: String?): Set<String> {
		if (author.isNullOrBlank()) return emptySet()
		return stripDiacritics(author)
			.lowercase(Locale.ROOT)
			.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
			.trim()
			.split(Regex("""\s+"""))
			.filter { it.length >= 2 && it !in setOf("unknown", "various", "anonymous", "studio", "team", "and") }
			.toSet()
	}

	private fun extractSequelMarker(text: String): String? {
		PART_SEASON_REGEX.find(text)?.let { return romanToIntString(it.groupValues[1]) }
		SHORT_SEASON_REGEX.find(text)?.let { return it.groupValues[1] }
		ORDINAL_SEASON_REGEX.find(text)?.let { return it.groupValues[1] }
		return null
	}

	private fun romanToIntString(token: String): String = when (token.lowercase(Locale.ROOT)) {
		"ii" -> "2"
		"iii" -> "3"
		"iv" -> "4"
		"v" -> "5"
		"vi" -> "6"
		"vii" -> "7"
		"viii" -> "8"
		"ix" -> "9"
		"x" -> "10"
		else -> token.lowercase(Locale.ROOT)
	}

	private fun isColoredTag(tag: String): Boolean {
		val lower = tag.trim().lowercase(Locale.ROOT)
		return lower == "full color" || lower == "full colour" || lower == "colored" ||
			lower == "official colored" || lower == "digital colored" || lower == "fan colored"
	}

	private fun isUncensoredTag(tag: String): Boolean {
		val lower = tag.trim().lowercase(Locale.ROOT)
		return lower == "uncensored" || lower == "decensored"
	}

	private fun isOriginVariantTag(tag: String): Boolean {
		val lower = tag.trim().lowercase(Locale.ROOT)
		return lower == "oneshot" || lower == "one-shot" || lower == "pre-serialization" || lower == "original webcomic"
	}

	private fun isSpinoffTag(tag: String): Boolean {
		val lower = tag.trim().lowercase(Locale.ROOT)
		return lower == "spin-off" || lower == "spinoff" || lower == "anthology" ||
			lower == "4-koma" || lower == "yonkoma" || lower == "side story" || lower == "gaiden"
	}

	private fun stripDiacritics(input: String): String {
		val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
		return decomposed.replace(Regex("""\p{M}+"""), "")
	}
}
