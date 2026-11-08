package org.koitharu.kotatsu.core.model

import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity

enum class LibraryLayoutMode(val label: String, val subtitle: String) {
	COMPACT_GRID("Compact Grid", "Full-bleed cover • Title inside bottom gradient"),
	COMFY_GRID("Comfy Grid", "Spacious grid • Title & progress below cover"),
	COMPACT_LIST("Compact Rows", "Dense single-line rows"),
	COMFORTABLE_LIST("Comfortable List", "Standard list with metadata"),
	DETAILED_CARD("Detailed Cards", "Large editorial cards"),
}

enum class CardCoverShape(val label: String, val cornerRadiusDp: Int) {
	SHARP("Sharp (4dp)", 4),
	ROUNDED("Material Rounded (14dp)", 14),
	PILL_EDGED("Soft Capsule (24dp)", 24),
	BORDERLESS_FLAT("Crisp Edge (0dp)", 0),
}

enum class DefaultFeedViewMode(val label: String) {
	UPDATES("Updates"),
	HUB("Hub"),
	GALLERY("Gallery"),
	FOR_YOU("For You"),
}

enum class CoverAspectRatio(val label: String, val ratio: Float) {
	MANGA_2_3("Manga 2:3", 0.67f),
	WIDE_3_4("Tankobon 3:4", 0.75f),
	SQUARE_1_1("Square 1:1", 1.0f),
	WEBTOON_TALL("Webtoon 9:15", 0.60f),
}

enum class LibrarySortOrder(val label: String) {
	LAST_READ("Last Read"),
	ALPHABETICAL("Alphabetical (A–Z)"),
	UNREAD_COUNT("Unread Chapters"),
	DATE_ADDED("Date Added"),
	LATEST_CHAPTER("Latest Chapter Update"),
	RATING("Highest Rating"),
	PROGRESS("Reading Progress %"),
}

enum class NavBarStyle(val label: String, val description: String) {
	DEFAULT_DOCKED("Default Material Bar", "Full-width docked Material 3 bottom navigation bar"),
	FLOATING_ISLAND("Floating Island Pill", "Elevated Material 3 floating capsule dock with tonal blur"),
}

enum class AppNavTab(val id: String, val label: String) {
	LIBRARY("library", "Library"),
	HISTORY("history", "History"),
	FEED("feed", "Feed"),
	EXPLORE("explore", "Explore"),
	AI_STUDIO("ai_studio", "Enhance"),
	BACKUPS_DB("backups_db", "Backups & DB"),
}

enum class AppThemeMode(val label: String, val subtitle: String) {
	SYSTEM("Follow system", "Automatically switch between Light and Dark"),
	LIGHT("Light", "Warm Archival Paper daylight palette"),
	DARK("Dark", "Obsidian Slate or AMOLED Pure Black"),
}

enum class AppColorSchemePreset(
	val label: String,
	val subtitle: String,
	val darkPrimaryHex: Long,
	val lightPrimaryHex: Long,
	val darkContainerHex: Long,
	val lightContainerHex: Long,
	val secondarySwatchHex: Long = darkPrimaryHex,
	val surfaceSwatchHex: Long = darkContainerHex,
) {
	KOTATSU_TEAL(
		label = "Kotatsu Teal",
		subtitle = "Signature Kotatsu cyan-teal",
		darkPrimaryHex = 0xFF4CCAD0,
		lightPrimaryHex = 0xFF0D9488,
		darkContainerHex = 0xFF0F363A,
		lightContainerHex = 0xFFCCFBF1,
		secondarySwatchHex = 0xFF2DD4BF,
		surfaceSwatchHex = 0xFF132A2E,
	),
	NEKO_INDIGO(
		label = "Midnight Indigo",
		subtitle = "Deep twilight periwinkle blue",
		darkPrimaryHex = 0xFF818CF8,
		lightPrimaryHex = 0xFF4F46E5,
		darkContainerHex = 0xFF1E1B4B,
		lightContainerHex = 0xFFE0E7FF,
		secondarySwatchHex = 0xFF6366F1,
		surfaceSwatchHex = 0xFF191838,
	),
	MIHON_COBALT(
		label = "Mihon Cobalt",
		subtitle = "Classic sapphire blue",
		darkPrimaryHex = 0xFF60A5FA,
		lightPrimaryHex = 0xFF2563EB,
		darkContainerHex = 0xFF172554,
		lightContainerHex = 0xFFDBEAFE,
		secondarySwatchHex = 0xFF38BDF8,
		surfaceSwatchHex = 0xFF132042,
	),
	CRIMSON_INK(
		label = "Crimson Ink",
		subtitle = "Editorial manga studio crimson",
		darkPrimaryHex = 0xFFFF3366,
		lightPrimaryHex = 0xFFD81B54,
		darkContainerHex = 0xFF3B1022,
		lightContainerHex = 0xFFFFD9E1,
		secondarySwatchHex = 0xFFFB7185,
		surfaceSwatchHex = 0xFF2D0D1B,
	),
	CEREMONIAL_MATCHA(
		label = "Ceremonial Matcha",
		subtitle = "Rich Kyoto Uji green",
		darkPrimaryHex = 0xFF84CC16,
		lightPrimaryHex = 0xFF4D7C0F,
		darkContainerHex = 0xFF1A2E05,
		lightContainerHex = 0xFFECFCCB,
		secondarySwatchHex = 0xFFA3E635,
		surfaceSwatchHex = 0xFF182809,
	),
	SAKURA_ROSE(
		label = "Sakura Rose",
		subtitle = "Soft cherry blossom pink",
		darkPrimaryHex = 0xFFF472B6,
		lightPrimaryHex = 0xFFDB2777,
		darkContainerHex = 0xFF4A1533,
		lightContainerHex = 0xFFFCE7F3,
		secondarySwatchHex = 0xFFF9A8D4,
		surfaceSwatchHex = 0xFF381228,
	),
	ROYAL_AMETHYST(
		label = "Royal Amethyst",
		subtitle = "Deep twilight violet",
		darkPrimaryHex = 0xFFA78BFA,
		lightPrimaryHex = 0xFF7C3AED,
		darkContainerHex = 0xFF2E1065,
		lightContainerHex = 0xFFEDE9FE,
		secondarySwatchHex = 0xFFC084FC,
		surfaceSwatchHex = 0xFF240F4E,
	),
	AMBER_GOLD(
		label = "Amber Gold",
		subtitle = "Warm goldenrod accent",
		darkPrimaryHex = 0xFFFBBF24,
		lightPrimaryHex = 0xFFD97706,
		darkContainerHex = 0xFF451A03,
		lightContainerHex = 0xFFFEF3C7,
		secondarySwatchHex = 0xFFF59E0B,
		surfaceSwatchHex = 0xFF331505,
	),
	EMERALD_FOREST(
		label = "Emerald Forest",
		subtitle = "Fresh botanical green",
		darkPrimaryHex = 0xFF34D399,
		lightPrimaryHex = 0xFF059669,
		darkContainerHex = 0xFF064E3B,
		lightContainerHex = 0xFFD1FAE5,
		secondarySwatchHex = 0xFF10B981,
		surfaceSwatchHex = 0xFF083A2C,
	),
	MONOCHROME_E_INK(
		label = "Monochrome E-Ink",
		subtitle = "High-contrast grayscale ink",
		darkPrimaryHex = 0xFFE2E8F0,
		lightPrimaryHex = 0xFF1E293B,
		darkContainerHex = 0xFF27272A,
		lightContainerHex = 0xFFE4E4E7,
		secondarySwatchHex = 0xFF94A3B8,
		surfaceSwatchHex = 0xFF18181B,
	),
}

data class AdvancedSearchFilter(
	val includedTags: Set<String> = emptySet(),
	val excludedTags: Set<String> = emptySet(),
	val limitedCategoryIds: Set<Long> = emptySet(),
	val selectedSources: Set<String> = emptySet(),
	val excludedQuickFilters: Set<String> = emptySet(),
	val onlyCompleted: Boolean = false,
	val completionState: org.koitharu.kotatsu.list.ui.CompletionFilterState = org.koitharu.kotatsu.list.ui.CompletionFilterState.ALL,
	val minimumRating: Float = 0f,
) {
	val isActive: Boolean
		get() = includedTags.isNotEmpty() ||
			excludedTags.isNotEmpty() ||
			limitedCategoryIds.isNotEmpty() ||
			selectedSources.isNotEmpty() ||
			excludedQuickFilters.isNotEmpty() ||
			onlyCompleted ||
			completionState != org.koitharu.kotatsu.list.ui.CompletionFilterState.ALL ||
			minimumRating > 0f

	val activeFilterCount: Int
		get() = includedTags.size +
			excludedTags.size +
			limitedCategoryIds.size +
			selectedSources.size +
			excludedQuickFilters.size +
			(if (onlyCompleted) 1 else 0) +
			(if (minimumRating > 0f) 1 else 0)
}

data class LibraryDisplayConfig(
	val layoutMode: LibraryLayoutMode = LibraryLayoutMode.COMPACT_GRID,
	val gridColumns: Int = 3,
	val cardShape: CardCoverShape = CardCoverShape.ROUNDED,
	val aspectRatio: CoverAspectRatio = CoverAspectRatio.MANGA_2_3,
	val sortOrder: LibrarySortOrder = LibrarySortOrder.LAST_READ,
	val sortAscending: Boolean = false,
	val showCategoryTabs: Boolean = true,
	val showStatusFilterBar: Boolean = true,
	val filtersCollapsed: Boolean = false,
	val showUnreadBadge: Boolean = true,
	val showDownloadedBadge: Boolean = true,
	val showSourceBadge: Boolean = true,
	val showContentTypeBadge: Boolean = false,
	val showLanguageBadge: Boolean = false,
	val showHealthBadge: Boolean = false,
	val showBrokenSeriesBanner: Boolean = false,
	val checkAndSwapOnAdd: Boolean = true,
)

enum class ReaderStyleMode(val label: String, val shortLabel: String) {
	PAGER_LTR("Left-to-Right Pager", "LTR"),
	PAGER_RTL("Manga Right-to-Left", "RTL"),
	PAGER_VERTICAL("Vertical Page Flip", "Vert Pager"),
	WEBTOON_CONTINUOUS("Continuous Webtoon Strip", "Webtoon"),
	DOUBLE_PAGE("Double-Page Spread", "2-Page"),
}

enum class ReaderScaleMode(val label: String) {
	FIT_WIDTH("Fit Width"),
	FIT_HEIGHT("Fit Height"),
	FIT_SCREEN("Fit Screen"),
	ORIGINAL("Original 1:1"),
	SMART_CROP_BORDERS("Smart Auto-Crop Borders"),
}

enum class HudBarPlacement(val label: String) {
	BOTTOM("Bottom Edge"),
	TOP("Top Edge"),
	LEFT("Left Vertical Rail"),
	RIGHT("Right Vertical Rail"),
	FLOATING_PILL("Floating Capsule"),
	HIDDEN("Hidden"),
}

enum class AiUpscaleMode(val label: String, val badge: String, val scaleFactor: Float) {
	OFF("Standard Native Decode", "1.0x", 1.0f),
	FAST_CRISP_INK_1_5X("AI Crisp Ink & Line Art (1.5x)", "AI 1.5x", 1.5f),
	ANIME_SUPER_RES_2X("AI Anime/Manga Super-Res (2.0x)", "AI 2.0x", 2.0f),
	HIGH_CONTRAST_RESTORE("Scan De-Noise & Ink Restore", "INK+", 1.75f),
}

enum class ReaderColorFilterPreset(val label: String, val badge: String) {
	NONE("Original", "RAW"),
	NEMURI_BEDTIME("🌙 Nemuri Bedtime (Warm Paper + Dimmer)", "BEDTIME"),
	AI_AUTO_INK_RESTORE("✦ AI Auto-Ink Restore", "AI INK"),
	WARM_SEPIA("Warm Archival Paper", "SEPIA"),
	NIGHT_BLUE_LIGHT("Eye-Care Night Blue", "NIGHT"),
	MONOCHROME_INK("Grayscale", "B&W"),
	CRISP_HIGH_CONTRAST("Crisp Deep Blacks", "INK"),
	INVERTED_NIGHT("Inverted OLED Dark", "INV"),
	CUSTOM_RGB("Custom RGB Balance", "RGB"),
}

enum class SliderPosition(val label: String) {
	LEFT_ALIGN("Left Align"),
	HORIZONTAL("Horizontal"),
	RIGHT_ALIGN("Right Align"),
}

enum class NekoRotationMode(val label: String) {
	DEFAULT("Default"),
	FREE("Free"),
	PORTRAIT("Portrait"),
	LANDSCAPE("Landscape"),
	LOCKED_PORTRAIT("Locked portrait"),
	LOCKED_LANDSCAPE("Locked landscape"),
}

enum class NekoBackgroundColorMode(val label: String) {
	WHITE("White"),
	BLACK("Black"),
	SMART_BY_PAGE("Smart (by page)"),
	SMART_BY_THEME("Smart (by theme)"),
	SMART_BY_THEME_BLACK("Smart (by theme but black)"),
}

enum class NekoWideSpreadMode(val label: String) {
	NONE("None"),
	SPLIT_INTO_TWO("Split 2-Page"),
	AUTO_ROTATE_90("Rotate 90°"),
}

enum class VerticalSpacingMode(val label: String, val subtitle: String) {
	GAPLESS("Gapless (0px Seamless)", "Zero gap between all pages/strips"),
	SMART_SEAM_AUTO_STITCH("Smart Seam Auto-Stitch", "Recommended • 0px on sliced panels, 8px between standalone pages"),
	PAGED_GAP("Paged Gap (12px)", "Traditional spacing between full pages"),
}

data class ReaderCustomizationConfig(
	val readingMode: ReaderStyleMode = ReaderStyleMode.PAGER_RTL,
	val scaleMode: ReaderScaleMode = ReaderScaleMode.FIT_WIDTH,
	val progressBarPlacement: HudBarPlacement = HudBarPlacement.BOTTOM,
	val scrollbarPlacement: HudBarPlacement = HudBarPlacement.RIGHT,
	val sliderPosition: SliderPosition = SliderPosition.RIGHT_ALIGN,
	val rotationMode: NekoRotationMode = NekoRotationMode.DEFAULT,
	val backgroundColorMode: NekoBackgroundColorMode = NekoBackgroundColorMode.SMART_BY_PAGE,
	val wideSpreadMode: NekoWideSpreadMode = NekoWideSpreadMode.SPLIT_INTO_TWO,
	val verticalSpacingMode: VerticalSpacingMode = VerticalSpacingMode.SMART_SEAM_AUTO_STITCH,
	val webtoonSidePaddingPercent: Int = 0,
	val aiUpscaleMode: AiUpscaleMode = AiUpscaleMode.FAST_CRISP_INK_1_5X,
	val showBeforeAfterSplit: Boolean = false,
	val colorFilterPreset: ReaderColorFilterPreset = ReaderColorFilterPreset.NONE,
	val brightnessDelta: Float = 0f,
	val contrastDelta: Float = 0.1f,
	val warmthDelta: Float = 0f,
	val bedtimeDimmerAlpha: Float = 0.18f,
	val marginCropPercent: Int = -27,
	val saturation: Float = 1.0f,
	val redBalance: Float = 1.0f,
	val greenBalance: Float = 1.0f,
	val blueBalance: Float = 1.0f,
	val invertColors: Boolean = false,
	val grayscale: Boolean = false,
	val warmPaperTint: Boolean = false,
	val autoDetectDoubleSpread: Boolean = true,
	val autoCropMargins: Boolean = true,
	val splitDoubleSpreadsInPortrait: Boolean = true,
	val showTapGridZones: Boolean = false,
	val switchPagesWithVolumeKeys: Boolean = true,
	val invertVolumeKeys: Boolean = false,
	val showZoomButtons: Boolean = true,
	val showPageNumberOverlay: Boolean = true,
	val keepScreenOn: Boolean = true,
	val bedtimeSleepGuard: Boolean = true,
	val showChapterTransitions: Boolean = true,
	val skipDuplicateChapters: Boolean = true,
	val bedtimeOneHandedTapZones: Boolean = false,
	val autoIncognitoMatureTags: Boolean = true,
	val twoPageOnLandscape: Boolean = true,
	val autoTwoPageOnFoldable: Boolean = true,
	val twoPageScrollSensitivity: Float = 0.5f,
	val autoScrollEnabled: Boolean = false,
	val autoScrollSpeedMultiplier: Float = 1.0f,
	val bedtimeAutoPauseAtChapterEnd: Boolean = true,
)

data class SourceMigrationTargetOption(
	val sourceName: String,
	val targetTitle: String,
	val targetUrl: String,
	val targetChaptersCount: Int,
	val confidenceScore: Float,
	val isRecommendedBest: Boolean,
)

data class SourceMigrationItemProposal(
	val sourceItem: LibraryMangaItem,
	val availableTargets: List<SourceMigrationTargetOption>,
	val selectedTarget: SourceMigrationTargetOption,
	val isSelectedToProceed: Boolean = true,
	val isMigrated: Boolean = false,
)

@androidx.compose.runtime.Immutable
data class LibraryMangaItem(
	val manga: MangaEntity,
	val tags: List<TagEntity>,
	val chapters: List<ChapterEntity>,
	val categoryIds: Set<Long>,
	val unreadCount: Int,
	val downloadedCount: Int,
	val lastReadAt: Long,
	val progressPercent: Float,
	val lastReadChapterNumber: Float,
	val addedAt: Long,
	val newChaptersCount: Int,
	val languageCode: String,
	val isDebugMock: Boolean = false,
) {
	val totalChapters: Int get() = chapters.size

	val hasMatureIncognitoTag: Boolean
		get() = tags.any { tag ->
			val t = tag.title.trim()
			t.equals("Erotica", ignoreCase = true) ||
				t.equals("Pornographic", ignoreCase = true) ||
				t.equals("Smut", ignoreCase = true) ||
				t.equals("18+", ignoreCase = true) ||
				t.equals("Adult", ignoreCase = true) ||
				t.contains("Erotica", ignoreCase = true) ||
				t.contains("Pornographic", ignoreCase = true)
		}

	val contentTypeLabel: String
		get() = when {
			manga.source.contains("LOCAL", ignoreCase = true) || languageCode == "CBZ" -> "CBZ"
			tags.any { it.title.equals("Webtoon", ignoreCase = true) } ||
				manga.source.contains("Webtoon", ignoreCase = true) -> "WEBTOON"
			else -> "MANGA"
		}
}

data class FeedUpdateItem(
	val id: String,
	val mangaItem: LibraryMangaItem,
	val chapter: ChapterEntity,
	val chapterIndex: Int,
	val timestampMillis: Long,
	val dateBucketLabel: String, // "Today", "Yesterday", "Earlier This Week"
	val isNewRelease: Boolean,
)

enum class DuplicateMatchType(val label: String) {
	EXACT_NORMALIZED("Exact Normalized Title"),
	ALT_TITLE_CROSS_LANG("Cross-Language / Alt Title Match"),
	FUZZY_AUTHOR_SIMILARITY("AI Semantic & Author Similarity"),
}

data class DuplicateCandidatePair(
	val pairId: String,
	val primary: LibraryMangaItem,
	val secondary: LibraryMangaItem,
	val similarityScore: Float,
	val matchType: DuplicateMatchType,
	val reasonSummary: String,
)

enum class BatchDeduplicationRule(val label: String, val shortLabel: String) {
	MOST_CHAPTERS("Keep Entry with Most Chapters", "Most Chapters"),
	FURTHEST_PROGRESS("Keep Entry with Furthest Read Progress", "Furthest Read"),
	HEALTHIEST_SOURCE("Prefer Healthy / Active Extension Source", "Healthy Source"),
	HIGHEST_RATING("Prefer Entry with Highest Rating", "Highest Rating"),
}

data class BrokenMangaFixProposal(
	val brokenItem: LibraryMangaItem,
	val failureReason: String,
	val candidateTitle: String,
	val candidateSource: String,
	val candidateUrl: String,
	val candidateChaptersCount: Int,
	val confidenceScore: Float,
	val aiDiagnosisNote: String,
	val isFixed: Boolean = false,
)

data class RepairAuditLogEntry(
	val id: String,
	val mangaId: Long,
	val mangaTitle: String,
	val coverUrl: String,
	val previousSource: String,
	val newSource: String,
	val chaptersPreserved: Int,
	val timestampLabel: String = "This Session",
	val canRevert: Boolean = true,
)

enum class ExtensionInstallStatus(val label: String) {
	NOT_INSTALLED("Available"),
	DOWNLOADING("Downloading..."),
	VERIFYING("Verifying..."),
	INSTALLED("Installed"),
	UPDATE_AVAILABLE("Update Ready"),
	FAILED("Failed"),
}

data class ExtensionInstallTask(
	val extensionId: String,
	val pkgName: String,
	val progressFraction: Float,
	val status: ExtensionInstallStatus,
	val downloadedFilePath: String,
	val fileProviderUriString: String,
	val sizeBytes: Long = 0L,
) {
	val progressPercent: Int
		get() = (progressFraction * 100f).toInt().coerceIn(0, 100)

	val fileProviderUri: String?
		get() = fileProviderUriString.takeIf { it.isNotBlank() }
}

data class ExternalExtensionInfo(
	val id: String,
	val name: String,
	val pkgName: String,
	val versionName: String,
	val lang: String,
	val mihonSourceId: Long,
	val authorityOrUrl: String,
	val extensionType: String, // MIHON_EXT, KOTATSU_CONTENT_PROVIDER, LOCAL_ARCHIVE
	val isInstalled: Boolean,
	val isEnabled: Boolean,
	val isNsfw: Boolean = false,
	val sampleCatalogCount: Int = 12,
	val hasUpdate: Boolean = false,
	val latestVersionName: String? = null,
	val apkFileName: String = "",
	val repoName: String = "Keiyoushi Official",
) {
	val apkDownloadUrl: String?
		get() = authorityOrUrl.takeIf { it.startsWith("http", ignoreCase = true) }
}

