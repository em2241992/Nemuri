package org.koitharu.kotatsu.core.ui.util

/**
 * UI display sanitizer for manga titles and technical parser/extension identifiers.
 *
 * Ensures raw file extensions (`.cbz`, `.cbr`, `.zip`, `.pdf`, `.epub`) and internal
 * parser class names (`BuiltInKotatsuParser`, `LOCAL:CBZ & ZIP Archive Loader`,
 * `DEAD:AsuraLegacyMirror`, `EXT:...`) are transformed into clean, human-readable labels
 * across Library, Feed, History, and AI & Enhancement screens without mutating
 * underlying database keys or deterministic manga IDs.
 */
object UiSourceAndTitleFormatter {

	private val ARCHIVE_EXTENSION_REGEX = Regex(
		"""\.(cbz|cbr|zip|pdf|epub|7z|rar)$""",
		RegexOption.IGNORE_CASE,
	)

	private val DIAGNOSTIC_SUFFIX_REGEX = Regex(
		"""\s*(\[Legacy Source Broken\]|\(CBZ Archive\)|\[CBZ Archive\]|\(Local CBZ\))\s*$""",
		RegexOption.IGNORE_CASE,
	)

	private val PAREN_TECH_TAG_REGEX = Regex(
		"""\s*\((Mihon\s+[A-Za-z0-9_+-]+|LNReader\s+[A-Za-z0-9_+-]+|IPC\s+Provider)\)\s*$""",
		RegexOption.IGNORE_CASE,
	)

	/**
	 * Strips raw archive file extensions (`.cbz`, `.cbr`, `.zip`, `.pdf`, `.epub`)
	 * and internal diagnostic bracket suffixes from user-facing manga titles.
	 */
	fun formatDisplayTitle(rawTitle: String): String {
		if (rawTitle.isBlank()) return rawTitle
		return rawTitle
			.trim()
			.replace(DIAGNOSTIC_SUFFIX_REGEX, "")
			.replace(ARCHIVE_EXTENSION_REGEX, "")
			.trim()
	}

	/**
	 * Maps internal parser class names and extension prefixes into clean,
	 * user-facing source names.
	 */
	fun formatSourceName(rawSource: String): String {
		if (rawSource.isBlank()) return "Unknown Source"
		val trimmed = rawSource.trim()
		val withoutPrefix = trimmed
			.removePrefix("EXT:")
			.removePrefix("DEAD:")
			.removePrefix("LOCAL:")
			.removePrefix("MIHON_UNMAPPED_")
			.trim()

		return when {
			withoutPrefix.equals("BuiltInKotatsuParser", ignoreCase = true) ||
				withoutPrefix.equals("Kotatsu IPC Provider", ignoreCase = true) ||
				withoutPrefix.equals("KOTATSU_NATIVE", ignoreCase = true) ||
				withoutPrefix.contains("BuiltInKotatsuParser", ignoreCase = true) -> "Kotatsu"

			withoutPrefix.equals("CBZ & ZIP Archive Loader", ignoreCase = true) ||
				withoutPrefix.contains("Archive Loader", ignoreCase = true) ||
				trimmed.startsWith("LOCAL:", ignoreCase = true) -> "Local Archive"

			withoutPrefix.equals("AsuraLegacyMirror", ignoreCase = true) ||
				withoutPrefix.contains("AsuraLegacyMirror", ignoreCase = true) -> "Asura Scans (Mirror)"

			withoutPrefix.equals("FlameScansLegacy", ignoreCase = true) ||
				withoutPrefix.contains("FlameScansLegacy", ignoreCase = true) -> "Flame Scans (Legacy)"

			withoutPrefix.equals("ReaperLegacy", ignoreCase = true) ||
				withoutPrefix.contains("ReaperLegacy", ignoreCase = true) -> "Reaper Scans (Legacy)"

			else -> withoutPrefix.replace(PAREN_TECH_TAG_REGEX, "").trim().ifBlank { withoutPrefix }
		}
	}
}
