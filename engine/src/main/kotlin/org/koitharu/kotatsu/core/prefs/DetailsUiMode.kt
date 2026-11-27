package org.koitharu.kotatsu.core.prefs

enum class DetailsUiMode {
	/** Compose layout with a large cover centered above the title (the default, showcase style). */
	EXPRESSIVE,

	/** Compose layout with a compact cover on the side next to the title and info. */
	COMPACT,

	/** Condensed minimal header strip so the chapter list begins immediately in the upper half. */
	ULTRA_COMPACT,
}
