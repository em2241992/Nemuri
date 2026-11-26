package org.koitharu.kotatsu.core.prefs

enum class MangaCardStyle(val key: String, val label: String) {
	COMPACT("COMPACT", "Compact (Corner-Safe Overlay)"),
	COMFORTABLE("COMFORTABLE", "Comfortable (Title Below)"),
	DETAILED_LIST("DETAILED_LIST", "Detailed List Row"),
	;

	companion object {
		fun fromKey(key: String?): MangaCardStyle =
			entries.firstOrNull { it.key == key } ?: COMFORTABLE
	}
}

enum class GridGutterDensity(
	val key: String,
	val label: String,
	val spacingDp: Int,
	val outerPaddingDp: Int,
) {
	COMPACT("COMPACT", "Compact", 8, 12),
	STANDARD("STANDARD", "Standard", 12, 16),
	RELAXED("RELAXED", "Relaxed", 16, 20),
	;

	companion object {
		fun fromKey(key: String?): GridGutterDensity =
			entries.firstOrNull { it.key == key } ?: STANDARD
	}
}

enum class CoverAspectRatio(val key: String, val label: String, val ratio: Float) {
	STANDARD_2_3("STANDARD_2_3", "2:3 Standard", 2f / 3f),
	TANKOBON_5_7("TANKOBON_5_7", "5:7 Tankobon", 5f / 7f),
	;

	companion object {
		fun fromKey(key: String?): CoverAspectRatio =
			entries.firstOrNull { it.key == key } ?: STANDARD_2_3
	}
}

enum class NavLabelVisibility(val key: String, val label: String) {
	ALWAYS("ALWAYS", "Always Show"),
	SELECTED_ONLY("SELECTED_ONLY", "Selected Only"),
	HIDDEN("HIDDEN", "Hide Labels"),
	;

	companion object {
		fun fromKey(key: String?): NavLabelVisibility =
			entries.firstOrNull { it.key == key } ?: ALWAYS
	}
}

enum class ReaderTapZoneMode(val key: String, val label: String, val description: String) {
	DEFAULT("DEFAULT", "Default", "Left = Prev • Center = Menu • Right = Next"),
	L_SHAPED("L_SHAPED", "L-Shaped", "Top/Left = Prev • Center = Menu • Bottom/Right = Next"),
	KINDLE("KINDLE", "Kindle", "Narrow Left = Prev • Top = Menu • Wide Right = Next"),
	EDGE_ONLY("EDGE_ONLY", "Edge-Only", "Outer Edges = Turn • Wide Center = Menu & Zoom"),
	;

	companion object {
		fun fromKey(key: String?): ReaderTapZoneMode =
			entries.firstOrNull { it.key == key } ?: DEFAULT
	}
}

enum class SelectionMenuStyle(val key: String, val label: String) {
	VISUAL_DIAGRAM("VISUAL", "Visual Diagram Cards"),
	COMPACT_LIST("COMPACT", "Compact List"),
	;

	companion object {
		fun fromKey(key: String?): SelectionMenuStyle =
			entries.firstOrNull { it.key == key } ?: VISUAL_DIAGRAM
	}
}

enum class ReaderScrubberEdge(val key: String, val label: String) {
	RIGHT("RIGHT", "Right Edge"),
	LEFT("LEFT", "Left Edge"),
	BOTTOM("BOTTOM", "Bottom Edge"),
	TOP("TOP", "Top Edge"),
	;

	val isVertical: Boolean
		get() = this == RIGHT || this == LEFT

	companion object {
		fun fromKey(key: String?): ReaderScrubberEdge =
			entries.firstOrNull { it.key == key } ?: RIGHT
	}
}

enum class DuplicateWinnerStrategy(
	val key: String,
	val label: String,
	val shortLabel: String,
) {
	MOST_CHAPTERS("MOST_CHAPTERS", "Keep Entry with Most Chapters", "Most Chapters"),
	FURTHEST_READ("FURTHEST_READ", "Keep Entry with Furthest Read Progress", "Furthest Read"),
	HEALTHY_SOURCE("HEALTHY_SOURCE", "Prefer Healthy / Enabled Source", "Healthy Source"),
	HIGHEST_RATING("HIGHEST_RATING", "Prefer Entry with Highest Rating", "Highest Rating"),
	;

	companion object {
		fun fromKey(key: String?): DuplicateWinnerStrategy =
			entries.firstOrNull { it.key == key } ?: MOST_CHAPTERS
	}
}

enum class FeedScreenMode(val key: String, val label: String) {
	HUB("HUB", "Hub"),
	UPDATES("UPDATES", "Updates"),
	GALLERY("GALLERY", "Gallery"),
	FOR_YOU("FOR_YOU", "For You"),
	;

	companion object {
		fun fromKey(key: String?): FeedScreenMode =
			entries.firstOrNull { it.key == key } ?: HUB
	}
}
