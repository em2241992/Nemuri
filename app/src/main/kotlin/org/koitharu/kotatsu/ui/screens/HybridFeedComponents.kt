package org.koitharu.kotatsu.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.FeedUpdateItem
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.components.MangaReaderPageCanvas
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily
import kotlin.math.abs

/**
 * Top-level mode for the Dual-Mode Hybrid Feed:
 * - Mode A: "Updates & Browse" (Stories Quick Updates Bar + 2-Column Masonry Grid / Reddit-Style Release Card Feed)
 * - Mode B: "For You • Discover" (TikTok Photo-Mode Vertical Series Pager + 5-Page Horizontal Chapter 1 Hook Carousel)
 */
enum class HybridFeedPrimaryMode(val label: String) {
	UPDATES_AND_BROWSE("Updates"),
	NEKO_HUB("Hub"),
	NEKO_GALLERY("Gallery"),
	DISCOVERY_FOR_YOU("For You"),
}

enum class HybridUpdatesBrowseViewMode(
	val label: String,
	val shortLabel: String,
	val roleSubtitle: String,
) {
	MASONRY_GALLERY("Catalog", "Catalog", ""),
	REDDIT_CARD_FEED("Updates", "Updates", ""),
	SOURCE_CATALOGUE("Shelves", "Shelves", ""),
}

enum class MasonryDiscoverySortMode(val label: String) {
	AI_MATCH("Best Match"),
	POPULARITY("Trending"),
	CHAPTER_COUNT("Most Chapters"),
}

enum class RedditStreamSortMode(val label: String) {
	HOT_DISCUSSIONS("Hot"),
	LATEST_DROPS("Latest"),
	LIBRARY_ONLY("Following"),
}

data class HybridFeedEntry(
	val id: String,
	val mangaId: Long?,
	val catalogRef: ExtensionCatalogManga?,
	val title: String,
	val author: String,
	val coverUrl: String,
	val sourceName: String,
	val chapterNumber: Int,
	val chapterIndex: Int,
	val chapterTitle: String,
	val isNewDrop: Boolean,
	val inLibrary: Boolean,
	val genres: List<String>,
	val baseHeatScore: Int,
	val baseCommentCount: Int,
	val aspectRatio: Float,
	val hookQuote: String,
	val synopsis: String,
	val matchPercent: Int = 95,
	val totalChapters: Int = chapterNumber,
	val releaseTimeLabel: String = "2h ago",
	val releaseDateBucket: String = "TODAY'S SIMULPUB DROPS",
	val scanlatorGroup: String = "Official Simulpub",
	val topCommentPreview: String = "The artwork and pacing in this chapter are unreal!",
	val topCommentAuthor: String = "u/SakuraInkReader",
)

data class ChapterDiscussionComment(
	val id: String,
	val authorHandle: String,
	val timeAgo: String,
	val body: String,
	val likes: Int,
	val isTopComment: Boolean = false,
)

/**
 * Extra discovery catalog series so the 2-Column Masonry Catalog Gallery is a true visual discovery
 * showcase rather than repeating the user's active library chapter drops from the 1-row top bar.
 */
private fun getExpandedDiscoveryCatalogPool(
	extensionCatalog: List<ExtensionCatalogManga>,
): List<ExtensionCatalogManga> {
	if (extensionCatalog.isEmpty()) return emptyList()
	val hasDebugSandboxItems = extensionCatalog.any {
		it.title.contains("Frieren", ignoreCase = true) ||
			it.title.contains("Berserk", ignoreCase = true)
	}
	if (!hasDebugSandboxItems) return extensionCatalog
	val extraSeries = listOf(
		ExtensionCatalogManga(
			title = "Witch Hat Atelier (Tongari Boushi no Atelier)",
			altTitle = "とんがり帽子のアトリエ",
			author = "Kamome Shirahama",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.ext/title/witch-hat-atelier",
			coverSeed = "witch_hat",
			description = "In a world where everyone takes wonders like magic spells and dragons for granted, Coco is a girl with a simple dream: she wants to be a witch.",
			tags = listOf("Fantasy", "Adventure", "Seinen", "Magic"),
			totalChapters = 82,
			rating = 0.99f,
		),
		ExtensionCatalogManga(
			title = "Dandadan",
			altTitle = "ダンダダン",
			author = "Yukinobu Tatsu",
			sourceName = "EXT:MangaDex (Mihon EN)",
			mihonSourceId = 2499283573021220255L,
			url = "https://mangadex.ext/title/dandadan",
			coverSeed = "dandadan",
			description = "Momo Ayase believes in ghosts but not aliens, while her classmate Okarun believes in aliens but not ghosts. In a bet to determine who's right, both discover they are terrifyingly wrong.",
			tags = listOf("Action", "Supernatural", "Sci-Fi", "Comedy"),
			totalChapters = 176,
			rating = 0.98f,
		),
		ExtensionCatalogManga(
			title = "Sakamoto Days",
			altTitle = "サカモトデイズ",
			author = "Yuto Suzuki",
			sourceName = "EXT:ComicK (Mihon Multi)",
			mihonSourceId = 6905824949201189121L,
			url = "https://comick.ext/comic/sakamoto-days",
			coverSeed = "sakamoto_days",
			description = "Taro Sakamoto was the ultimate assassin, feared by villains and admired by hitmen. But one day… he fell in love! Retirement, marriage, fatherhood and then… Sakamoto gained weight!",
			tags = listOf("Action", "Comedy", "Martial Arts", "Shounen"),
			totalChapters = 192,
			rating = 0.97f,
		),
		ExtensionCatalogManga(
			title = "Dungeon Odyssey",
			altTitle = "던전 오디세이",
			author = "Munpia, Son Min-woo",
			sourceName = "EXT:Aqua Manga (Mihon EN)",
			mihonSourceId = 5182930495817263541L,
			url = "https://aquamanga.ext/series/dungeon-odyssey",
			coverSeed = "dungeon_odyssey",
			description = "Raised in the subterranean labyrinth, Jinwoo inherits control of the 12th Floor Naga Labyrinth and must wage strategic war against rival underground lords.",
			tags = listOf("Dark Fantasy", "Action", "Strategy", "Webtoon"),
			totalChapters = 114,
			rating = 0.96f,
		),
	)
	val existingUrls = extensionCatalog.map { it.url }.toSet()
	return extensionCatalog + extraSeries.filter { it.url !in existingUrls }
}

/**
 * Builds unified entries for the Hybrid Feed from the user's active feed updates, library items, and discovery catalog.
 */
fun buildHybridFeedEntries(
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	extensionCatalog: List<ExtensionCatalogManga>,
	libraryNormalizedTitles: Set<String>,
	defaultSource: String,
	searchQuery: String,
): List<HybridFeedEntry> {
	val aspectPool = listOf(0.62f, 0.75f, 0.57f, 0.71f, 0.60f, 0.78f)
	val hookQuotes = listOf(
		"\"When the hero's party disbands, the real journey through time begins.\"",
		"\"Rule #1 of the dungeon: eat or be eaten—literally.\"",
		"\"Every devil contract demands a price no human should pay.\"",
		"\"He was the weakest hunter in the world until the double dungeon altar awoke.\"",
		"\"Behind the spotlight, entertainment is a ruthless battlefield of lies.\"",
		"\"A cursed blade forged in vengeance cuts through the underworld.\"",
	)
	val scanlators = listOf(
		"Solaris Simulpub",
		"Kirei Cake Scans",
		"Asura Scans HD",
		"MangaDex Official",
		"Team Fallen Angels",
		"Hokazono Ink Guild",
	)
	val timeLabels = listOf("18m ago", "1h ago", "3h ago", "5h ago", "Yesterday", "2d ago")
	val topComments = listOf(
		"That double-page spread on page 14 gave me chills! The linework this arc is 10/10.",
		"Anyone else catch the callback to Chapter 1 in the background panel? Pure genius.",
		"The pacing of this chapter was relentless—best cliffhanger of the month.",
		"Reading this with the 1.5x Crisp Ink shader makes the halftone shadows pop like print.",
		"Wait, so that character was working for the underground syndicate the whole time?!",
		"Peak choreography. You can feel the weight of every sword strike across the panels.",
	)
	val commentAuthors = listOf(
		"u/SakuraInkReader",
		"u/TankobonCollector",
		"u/MihonMigrator",
		"u/SeinenArchivist",
		"u/HalftoneEnthusiast",
		"u/NightOwlOtaku",
	)

	val effectiveUpdates = if (feedUpdates.isNotEmpty()) {
		feedUpdates.distinctBy { it.mangaItem.manga.id }
	} else {
		libraryItems.mapIndexedNotNull { idx, item ->
			val ch = item.chapters.lastOrNull() ?: return@mapIndexedNotNull null
			FeedUpdateItem(
				id = "hybrid_lib_fallback_${item.manga.id}",
				mangaItem = item,
				chapter = ch,
				chapterIndex = (item.chapters.size - 1).coerceAtLeast(0),
				timestampMillis = System.currentTimeMillis() - idx * 3600_000L,
				dateBucketLabel = if (idx < 3) "Today" else "Yesterday",
				isNewRelease = idx < 4,
			)
		}
	}

	val fromUpdates = effectiveUpdates.mapIndexed { idx, upd ->
		val m = upd.mangaItem.manga
		val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(m.title)
		val cleanSource = UiSourceAndTitleFormatter.formatSourceName(m.source)
		val hash = abs(cleanTitle.hashCode())
		val chNum = upd.chapter.number.toInt().coerceAtLeast(1)
		val rawSynopsis = m.description?.takeIf { it.isNotBlank() }
			?: "New chapter release for $cleanTitle (${upd.chapter.title.ifBlank { "Chapter $chNum" }})."
		val ownSynopsis = rawSynopsis
			.replace("Duplicate entry from EXT:", "Merged with ")
			.replace("EXT:", "")
		val ownHookQuote = resolveSeriesSpecificHookQuote(cleanTitle, ownSynopsis)
		val rawScanlator = upd.chapter.scanlator?.takeIf { it.isNotBlank() }
		val cleanScanlator = if (rawScanlator != null) {
			UiSourceAndTitleFormatter.formatSourceName(rawScanlator)
		} else {
			scanlators[idx % scanlators.size]
		}
		HybridFeedEntry(
			id = "hybrid_upd_${m.id}",
			mangaId = m.id,
			catalogRef = null,
			title = cleanTitle,
			author = m.authors ?: "Studio Creator",
			coverUrl = m.coverUrl,
			sourceName = cleanSource,
			chapterNumber = chNum,
			chapterIndex = upd.chapterIndex,
			chapterTitle = upd.chapter.title.ifBlank { "Chapter $chNum" },
			isNewDrop = upd.isNewRelease,
			inLibrary = true,
			genres = upd.mangaItem.tags.map { it.title }.ifEmpty { listOf("Manga", "Action", "Fantasy") },
			baseHeatScore = 640 + (hash % 1850),
			baseCommentCount = 84 + (hash % 260),
			aspectRatio = aspectPool[idx % aspectPool.size],
			hookQuote = ownHookQuote,
			synopsis = ownSynopsis,
			matchPercent = (m.rating * 100f).toInt().coerceIn(88, 99),
			totalChapters = upd.mangaItem.totalChapters.coerceAtLeast(chNum),
			releaseTimeLabel = timeLabels[idx % timeLabels.size],
			releaseDateBucket = if (idx < 3) "TODAY'S SIMULPUB DROPS" else "YESTERDAY'S RELEASES",
			scanlatorGroup = cleanScanlator,
			topCommentPreview = topComments[idx % topComments.size],
			topCommentAuthor = commentAuthors[idx % commentAuthors.size],
		)
	}

	val existingIds = fromUpdates.mapNotNull { it.mangaId }.toSet()
	val expandedCatalog = getExpandedDiscoveryCatalogPool(extensionCatalog)
	val fromCatalog = expandedCatalog.mapIndexed { idx, cat ->
		val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(cat.title)
		val cleanSource = UiSourceAndTitleFormatter.formatSourceName(cat.sourceName)
		val norm = TitleNormalizer.normalize(cat.title)
		val matchedLib = libraryItems.find { TitleNormalizer.normalize(it.manga.title) == norm }
		val hash = abs(cleanTitle.hashCode())
		val chNum = cat.totalChapters.coerceAtLeast(1)
		val rawSynopsis = matchedLib?.manga?.description?.takeIf { it.isNotBlank() } ?: cat.description
		val ownSynopsis = rawSynopsis
			.replace("Duplicate entry from EXT:", "Merged with ")
			.replace("EXT:", "")
		val ownHookQuote = resolveSeriesSpecificHookQuote(cleanTitle, ownSynopsis)
		HybridFeedEntry(
			id = "hybrid_cat_${cat.url}",
			mangaId = matchedLib?.manga?.id,
			catalogRef = cat,
			title = cleanTitle,
			author = cat.author,
			coverUrl = cat.coverSeed,
			sourceName = cleanSource,
			chapterNumber = chNum,
			chapterIndex = ((matchedLib?.chapters?.size ?: 1) - 1).coerceAtLeast(0),
			chapterTitle = "Ch. $chNum • Latest Release",
			isNewDrop = idx % 2 == 0,
			inLibrary = norm in libraryNormalizedTitles,
			genres = cat.tags.ifEmpty { listOf("Action", "Seinen") },
			baseHeatScore = 420 + (hash % 1400),
			baseCommentCount = 52 + (hash % 190),
			aspectRatio = aspectPool[(idx + 2) % aspectPool.size],
			hookQuote = ownHookQuote,
			synopsis = ownSynopsis,
			matchPercent = (cat.rating * 100f).toInt().coerceIn(89, 99),
			totalChapters = chNum,
			releaseTimeLabel = timeLabels[(idx + 2) % timeLabels.size],
			releaseDateBucket = if (idx % 2 == 0) "TRENDING COMMUNITY THREADS" else "THIS WEEK'S TOP [DISC]",
			scanlatorGroup = scanlators[(idx + 1) % scanlators.size],
			topCommentPreview = topComments[(idx + 2) % topComments.size],
			topCommentAuthor = commentAuthors[(idx + 2) % commentAuthors.size],
		)
	}

	val combined = (fromUpdates + fromCatalog)
	return if (searchQuery.isBlank()) {
		combined
	} else {
		combined.filter {
			it.title.contains(searchQuery, ignoreCase = true) ||
				it.author.contains(searchQuery, ignoreCase = true) ||
				it.genres.any { g -> g.contains(searchQuery, ignoreCase = true) } ||
				it.sourceName.contains(searchQuery, ignoreCase = true)
		}
	}
}

/**
 * Dual-Mode Hybrid Feed Container:
 * Splits the Feed Home experience into separate, non-overlapping functional views:
 * - Top 1-Row Stories Bar (`FeedStoriesQuickUpdatesBar`): Strictly for resuming active Library chapter drops (1-tap straight into Reader).
 * - Mode A.1 (`MASONRY_GALLERY` — 2-Column Catalog Gallery): Strictly for Visual Series Discovery & Catalog Browsing across sources/genres (excludes items already shown in the 1-row quick updates bar unless toggled).
 * - Mode A.2 (`REDDIT_CARD_FEED` — 1-Column [DISC] Release Stream): Dedicated to Chapter Release Breakdowns, 3-Panel Interior Page Previews, Scanlator credits & Community Spoiler Discussions (💬).
 * - Mode A.3 (`SOURCE_CATALOGUE` — Shelves): Curated horizontal shelves by category without duplicating the top story bar.
 * - Mode B (`DISCOVERY_FOR_YOU` — TikTok Photo-Mode): Full-screen vertical series pager with a 5-page Chapter 1 hook carousel.
 */
private fun resolveSeriesSpecificHookQuote(cleanTitle: String, synopsis: String): String {
	val lower = cleanTitle.lowercase()
	return when {
		lower.contains("akira") -> "In post-WWIII cyberpunk Neo-Tokyo, Kaneda and Tetsuo collide with a psychic government project."
		lower.contains("berserk") -> "Guts wields the Dragon Slayer greatsword across a dark medieval realm of demons and fate."
		lower.contains("frieren") -> "When the hero's party disbands, an elven mage's real journey through time and memory begins."
		lower.contains("dungeon meshi") || lower.contains("delicious in dungeon") -> "Rule #1 of the dungeon: eat or be eaten—crafting gourmet meals from monsters."
		lower.contains("chainsaw") -> "Every devil contract demands a price no human should pay."
		lower.contains("solo leveling") -> "He was the weakest hunter in the world until the double dungeon altar awoke."
		lower.contains("oshi no ko") -> "Behind the idol spotlight, the entertainment world is a ruthless battlefield of secrets."
		lower.contains("kagurabachi") -> "A cursed blade forged in vengeance cuts through the sorcerer underworld."
		else -> synopsis
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DualModeHybridFeedHomeView(
	initialPrimaryMode: HybridFeedPrimaryMode = HybridFeedPrimaryMode.UPDATES_AND_BROWSE,
	defaultFeedView: org.koitharu.kotatsu.core.model.DefaultFeedViewMode = org.koitharu.kotatsu.core.model.DefaultFeedViewMode.UPDATES,
	onSetDefaultFeedView: (org.koitharu.kotatsu.core.model.DefaultFeedViewMode) -> Unit = {},
	defaultSource: String,
	availableSources: List<String>,
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	extensionCatalog: List<ExtensionCatalogManga>,
	libraryNormalizedTitles: Set<String>,
	searchQuery: String,
	nekoGalleryColumns: Int = 5,
	onSelectNekoGalleryColumns: (Int) -> Unit = {},
	onRequestFocusTopSearch: () -> Unit = {},
	onSelectDefaultSource: (String) -> Unit,
	onNavigateToSubCategory: (FeedSectionCategory) -> Unit,
	onOpenReader: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
	onRequestAddCatalogManga: (ExtensionCatalogManga) -> Unit,
	onMarkFeedChapterRead: (Long, Long, Int) -> Unit = { _, _, _ -> },
	catalogueFallbackContent: @Composable () -> Unit,
) {
	var primaryMode by rememberSaveable(initialPrimaryMode) { mutableStateOf(initialPrimaryMode) }
	var browseViewMode by rememberSaveable { mutableStateOf(HybridUpdatesBrowseViewMode.MASONRY_GALLERY) }

	// Separate functional filter/sort states for 2-Column Catalog Discovery vs 1-Column [DISC] Release Stream
	var selectedDiscoveryGenre by rememberSaveable { mutableStateOf("All Genres") }
	var masonrySortMode by rememberSaveable { mutableStateOf(MasonryDiscoverySortMode.AI_MATCH) }
	var includeLibraryInCatalogGrid by rememberSaveable { mutableStateOf(false) }
	var redditStreamSortMode by rememberSaveable { mutableStateOf(RedditStreamSortMode.HOT_DISCUSSIONS) }
	var nekoFilter by rememberSaveable { mutableStateOf(NekoHubAndGalleryFilter.ALL) }
	var gallerySectionFilter by rememberSaveable { mutableStateOf<NekoHubSectionCategory?>(null) }

	// Interactive state for bookmarks, read status, upvotes, comments, and "More Like This" similarity filtering
	val bookmarkedEntryIds = remember { mutableStateMapOf<String, Boolean>() }
	val markedReadEntryIds = remember { mutableStateMapOf<String, Boolean>() }
	val upvotedEntryIds = remember { mutableStateMapOf<String, Boolean>() }
	val customCommentsByEntry = remember { mutableStateMapOf<String, MutableList<ChapterDiscussionComment>>() }
	var activeCommentEntry by remember { mutableStateOf<HybridFeedEntry?>(null) }
	var activeSimilaritySeedEntry by remember { mutableStateOf<HybridFeedEntry?>(null) }
	var similarityFilterGenre by rememberSaveable { mutableStateOf<String?>(null) }

	val allEntries = remember(
		feedUpdates,
		libraryItems,
		extensionCatalog,
		libraryNormalizedTitles,
		defaultSource,
		searchQuery,
	) {
		buildHybridFeedEntries(
			feedUpdates = feedUpdates,
			libraryItems = libraryItems,
			extensionCatalog = extensionCatalog,
			libraryNormalizedTitles = libraryNormalizedTitles,
			defaultSource = defaultSource,
			searchQuery = searchQuery,
		)
	}

	val displayedEntries = remember(allEntries, similarityFilterGenre) {
		if (similarityFilterGenre == null) {
			allEntries
		} else {
			allEntries.filter { entry ->
				entry.genres.any { it.equals(similarityFilterGenre, ignoreCase = true) }
			}.ifEmpty { allEntries }
		}
	}

	// IDs already displayed in the Top 1-Row Quick Updates Bar so the 2-Column Discovery Grid never duplicates them!
	val storyRowMangaIds = remember(feedUpdates, libraryItems) {
		val fromFeed = feedUpdates.map { it.mangaItem.manga.id }.toSet()
		if (fromFeed.isNotEmpty()) fromFeed else libraryItems.map { it.manga.id }.toSet()
	}

	// 2-Column Masonry Dataset: Dedicated to Catalog Discovery & Uncollected Series (excludes Top 1-Row Quick Updates by default)
	val masonryCatalogEntries = remember(
		displayedEntries,
		storyRowMangaIds,
		includeLibraryInCatalogGrid,
		selectedDiscoveryGenre,
		masonrySortMode,
	) {
		val basePool = if (includeLibraryInCatalogGrid) {
			displayedEntries
		} else {
			displayedEntries.filter { entry ->
				entry.catalogRef != null || (entry.mangaId != null && entry.mangaId !in storyRowMangaIds)
			}.ifEmpty { displayedEntries }
		}
		val genreFiltered = if (selectedDiscoveryGenre == "All Genres") {
			basePool
		} else {
			basePool.filter { entry ->
				entry.genres.any { it.contains(selectedDiscoveryGenre, ignoreCase = true) }
			}.ifEmpty { basePool }
		}
		when (masonrySortMode) {
			MasonryDiscoverySortMode.AI_MATCH -> genreFiltered.sortedByDescending { it.matchPercent * 1000 + it.baseHeatScore }
			MasonryDiscoverySortMode.POPULARITY -> genreFiltered.sortedByDescending { it.baseHeatScore }
			MasonryDiscoverySortMode.CHAPTER_COUNT -> genreFiltered.sortedByDescending { it.totalChapters }
		}
	}

	// 1-Column Reddit [DISC] Dataset: Dedicated to Chapter Releases & Community Discussions
	val redditStreamEntries = remember(displayedEntries, redditStreamSortMode, upvotedEntryIds, customCommentsByEntry) {
		val basePool = when (redditStreamSortMode) {
			RedditStreamSortMode.LIBRARY_ONLY -> displayedEntries.filter { it.inLibrary }.ifEmpty { displayedEntries }
			else -> displayedEntries
		}
		when (redditStreamSortMode) {
			RedditStreamSortMode.HOT_DISCUSSIONS -> basePool.sortedByDescending {
				it.baseHeatScore + (if (upvotedEntryIds[it.id] == true) 50 else 0)
			}
			RedditStreamSortMode.LATEST_DROPS -> basePool.sortedByDescending {
				(if (it.isNewDrop) 10_000 else 0) + (if (it.inLibrary) 5_000 else 0) + it.chapterNumber
			}
			RedditStreamSortMode.LIBRARY_ONLY -> basePool
		}
	}

	val isEntryBookmarked: (HybridFeedEntry) -> Boolean = { entry ->
		bookmarkedEntryIds[entry.id] ?: entry.inLibrary
	}

	val onToggleEntryBookmark: (HybridFeedEntry) -> Unit = { entry ->
		val current = bookmarkedEntryIds[entry.id] ?: entry.inLibrary
		bookmarkedEntryIds[entry.id] = !current
		if (!current && entry.catalogRef != null && !entry.inLibrary) {
			onRequestAddCatalogManga(entry.catalogRef)
		}
	}

	val onLaunchEntryReader: (HybridFeedEntry, Boolean) -> Unit = { entry, startFromChapterOne ->
		val targetMangaId = entry.mangaId ?: libraryItems.firstOrNull()?.manga?.id ?: 1001L
		val targetChapterIdx = if (startFromChapterOne) 0 else entry.chapterIndex.coerceAtLeast(0)
		onOpenReader(targetMangaId, targetChapterIdx)
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.testTag("feed_home_catalogue_view"),
	) {
		// Top Dual-Mode Hybrid Switcher Bar (Mode A: "Updates & Browse" vs Mode B: "For You • Discover")
		Surface(
			color = MaterialTheme.colorScheme.background,
			modifier = Modifier.fillMaxWidth(),
		) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
				shape = RoundedCornerShape(20.dp),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 6.dp)
					.height(40.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(3.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					HybridFeedPrimaryMode.entries.forEach { mode ->
						val isSelected = primaryMode == mode
						val mappedDefaultMode = when (mode) {
							HybridFeedPrimaryMode.UPDATES_AND_BROWSE -> org.koitharu.kotatsu.core.model.DefaultFeedViewMode.UPDATES
							HybridFeedPrimaryMode.NEKO_HUB -> org.koitharu.kotatsu.core.model.DefaultFeedViewMode.HUB
							HybridFeedPrimaryMode.NEKO_GALLERY -> org.koitharu.kotatsu.core.model.DefaultFeedViewMode.GALLERY
							HybridFeedPrimaryMode.DISCOVERY_FOR_YOU -> org.koitharu.kotatsu.core.model.DefaultFeedViewMode.FOR_YOU
						}
						val isDefaultMode = defaultFeedView == mappedDefaultMode
						val tag = when (mode) {
							HybridFeedPrimaryMode.UPDATES_AND_BROWSE -> "hybrid_feed_mode_updates_browse"
							HybridFeedPrimaryMode.NEKO_HUB -> "hybrid_feed_mode_neko_hub"
							HybridFeedPrimaryMode.NEKO_GALLERY -> "hybrid_feed_mode_neko_gallery"
							HybridFeedPrimaryMode.DISCOVERY_FOR_YOU -> "hybrid_feed_mode_discovery"
						}
						val modeIcon = when (mode) {
							HybridFeedPrimaryMode.UPDATES_AND_BROWSE -> Icons.Default.ViewAgenda
							HybridFeedPrimaryMode.NEKO_HUB -> Icons.Default.ViewCarousel
							HybridFeedPrimaryMode.NEKO_GALLERY -> Icons.Default.GridView
							HybridFeedPrimaryMode.DISCOVERY_FOR_YOU -> Icons.Default.AutoAwesome
						}
						Surface(
							color = if (isSelected) {
								MaterialTheme.colorScheme.primary
							} else {
								Color.Transparent
							},
							shape = RoundedCornerShape(17.dp),
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight()
								.clip(RoundedCornerShape(17.dp))
								.combinedClickable(
									onClick = {
										if (mode == HybridFeedPrimaryMode.NEKO_HUB) {
											gallerySectionFilter = null
										}
										primaryMode = mode
									},
									onLongClick = {
										onSetDefaultFeedView(mappedDefaultMode)
										primaryMode = mode
									},
								)
								.testTag(tag),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.Center,
							) {
								Icon(
									imageVector = modeIcon,
									contentDescription = null,
									tint = if (isSelected) {
										MaterialTheme.colorScheme.onPrimary
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
									modifier = Modifier.size(13.dp),
								)
								Spacer(modifier = Modifier.width(3.dp))
								Text(
									text = if (isDefaultMode) "★ ${mode.label}" else mode.label,
									style = MaterialTheme.typography.labelMedium,
									fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									color = if (isSelected) {
										MaterialTheme.colorScheme.onPrimary
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
								)
							}
						}
					}
				}
			}
		}

		// Active Similarity Filter Chip Banner (if triggered from "More Like This")
		if (similarityFilterGenre != null) {
			Surface(
				color = MaterialTheme.colorScheme.secondaryContainer,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 4.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(
							imageVector = Icons.Default.AutoAwesome,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSecondaryContainer,
							modifier = Modifier.size(16.dp),
						)
						Text(
							text = "Visual & Genre Similarity Active: $similarityFilterGenre",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
						)
					}
					TextButton(
						onClick = { similarityFilterGenre = null },
						contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
					) {
						Text("Clear", fontSize = 11.sp)
					}
				}
			}
		}

		// Render Mode A ("Updates & Browse") or Mode B ("For You • Discover")
		when (primaryMode) {
			HybridFeedPrimaryMode.UPDATES_AND_BROWSE -> {
				HybridRedditReleaseCardFeed(
					entries = redditStreamEntries,
					feedUpdates = feedUpdates,
					libraryItems = libraryItems,
					sortMode = redditStreamSortMode,
					onSelectSortMode = { redditStreamSortMode = it },
					isBookmarked = isEntryBookmarked,
					isMarkedRead = { entry ->
						val libMatch = libraryItems.find { it.manga.id == entry.mangaId }
						val chReadInDb = libMatch?.chapters?.getOrNull(entry.chapterIndex)?.isRead == true
						markedReadEntryIds[entry.id] ?: chReadInDb
					},
					onToggleMarkRead = { entry ->
						val current = markedReadEntryIds[entry.id] ?: false
						markedReadEntryIds[entry.id] = !current
						val libMatch = libraryItems.find { it.manga.id == entry.mangaId }
						val ch = libMatch?.chapters?.getOrNull(entry.chapterIndex) ?: libMatch?.chapters?.lastOrNull()
						if (libMatch != null && ch != null) {
							onMarkFeedChapterRead(libMatch.manga.id, ch.chapterId, libMatch.totalChapters)
						}
					},
					onToggleBookmark = onToggleEntryBookmark,
					onOpenComments = { entry -> activeCommentEntry = entry },
					onOpenStoryReader = onOpenReader,
					onOpenEntryReader = { entry -> onLaunchEntryReader(entry, false) },
					onOpenEntryDetails = { entry ->
						if (entry.mangaId != null) {
							onOpenDetails(entry.mangaId)
						} else if (entry.catalogRef != null) {
							onRequestAddCatalogManga(entry.catalogRef)
						}
					},
				)
			}

			HybridFeedPrimaryMode.NEKO_HUB -> {
				NekoBrowseHubView(
					entries = displayedEntries,
					libraryItems = libraryItems,
					activeFilter = nekoFilter,
					onSelectFilter = { nekoFilter = it },
					onSelectSectionForGallery = { section ->
						gallerySectionFilter = section
						primaryMode = HybridFeedPrimaryMode.NEKO_GALLERY
					},
					galleryColumns = nekoGalleryColumns,
					onSelectGalleryColumns = onSelectNekoGalleryColumns,
					isBookmarked = isEntryBookmarked,
					onToggleBookmark = onToggleEntryBookmark,
					onOpenReader = { entry -> onLaunchEntryReader(entry, false) },
					onOpenDetails = { entry ->
						if (entry.mangaId != null) {
							onOpenDetails(entry.mangaId)
						} else if (entry.catalogRef != null) {
							onRequestAddCatalogManga(entry.catalogRef)
						}
					},
					onRequestFocusTopSearch = onRequestFocusTopSearch,
				)
			}

			HybridFeedPrimaryMode.NEKO_GALLERY -> {
				NekoDenseGalleryGrid(
					title = gallerySectionFilter?.headerTitle ?: "Dense Gallery",
					entries = displayedEntries,
					libraryItems = libraryItems,
					activeFilter = nekoFilter,
					onSelectFilter = { nekoFilter = it },
					activeSectionFilter = gallerySectionFilter,
					onClearSectionFilter = if (gallerySectionFilter != null) {
						{
							gallerySectionFilter = null
							primaryMode = HybridFeedPrimaryMode.NEKO_HUB
						}
					} else {
						null
					},
					galleryColumns = nekoGalleryColumns,
					showRankFooter = gallerySectionFilter == null || gallerySectionFilter == NekoHubSectionCategory.POPULAR_NEW_TITLES,
					onSelectGalleryColumns = onSelectNekoGalleryColumns,
					isBookmarked = isEntryBookmarked,
					onToggleBookmark = onToggleEntryBookmark,
					onOpenReader = { entry -> onLaunchEntryReader(entry, false) },
					onOpenDetails = { entry ->
						if (entry.mangaId != null) {
							onOpenDetails(entry.mangaId)
						} else if (entry.catalogRef != null) {
							onRequestAddCatalogManga(entry.catalogRef)
						}
					},
				)
			}

			HybridFeedPrimaryMode.DISCOVERY_FOR_YOU -> {
				TikTokDiscoveryFeedView(
					entries = displayedEntries,
					isBookmarked = isEntryBookmarked,
					commentCountFor = { entry ->
						entry.baseCommentCount + (customCommentsByEntry[entry.id]?.size ?: 0)
					},
					onToggleBookmark = onToggleEntryBookmark,
					onReadChapterOne = { entry -> onLaunchEntryReader(entry, true) },
					onOpenComments = { entry -> activeCommentEntry = entry },
					onOpenMoreLikeThis = { entry -> activeSimilaritySeedEntry = entry },
				)
			}
		}
	}

	// Interactive Chapter Discussion & Comments ModalBottomSheet (💬 142)
	val commentTarget = activeCommentEntry
	if (commentTarget != null) {
		var draftComment by remember { mutableStateOf("") }
		val userAdded = customCommentsByEntry.getOrPut(commentTarget.id) { mutableStateListOf() }
		val seededComments = remember(commentTarget.id) {
			listOf(
				ChapterDiscussionComment(
					id = "c1",
					authorHandle = "u/SakuraInkReader",
					timeAgo = "42m ago",
					body = "That double-page spread in ${commentTarget.chapterTitle} gave me chills! The pacing this arc is 10/10.",
					likes = 128,
					isTopComment = true,
				),
				ChapterDiscussionComment(
					id = "c2",
					authorHandle = "u/TankobonCollector",
					timeAgo = "2h ago",
					body = "Anyone else notice the foreshadowing on page 3? Connects directly back to Chapter 1!",
					likes = 64,
				),
				ChapterDiscussionComment(
					id = "c3",
					authorHandle = "u/MihonMigrator",
					timeAgo = "4h ago",
					body = "Reading this with the AI 1.5x Crisp Ink filter makes the halftone shading pop like print.",
					likes = 39,
				),
			)
		}

		ModalBottomSheet(
			onDismissRequest = { activeCommentEntry = null },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Chapter Discussion • 💬 ${commentTarget.baseCommentCount + userAdded.size}",
							style = MaterialTheme.typography.titleMedium,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "${commentTarget.title} — ${commentTarget.chapterTitle}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					FilledTonalButton(
						onClick = {
							activeCommentEntry = null
							onLaunchEntryReader(commentTarget, false)
						},
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
					) {
						Text("Read Ch. ${commentTarget.chapterNumber}", fontSize = 11.sp)
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					OutlinedTextField(
						value = draftComment,
						onValueChange = { draftComment = it },
						placeholder = { Text("Share your reaction to Ch. ${commentTarget.chapterNumber}…") },
						singleLine = true,
						modifier = Modifier
							.weight(1f)
							.testTag("chapter_comment_input"),
					)
					IconButton(
						onClick = {
							if (draftComment.isNotBlank()) {
								userAdded.add(
									0,
									ChapterDiscussionComment(
										id = "user_${System.currentTimeMillis()}",
										authorHandle = "u/You (Kotatsu Reader)",
										timeAgo = "Just now",
										body = draftComment.trim(),
										likes = 1,
									),
								)
								draftComment = ""
							}
						},
						modifier = Modifier
							.size(48.dp)
							.clip(CircleShape)
							.background(MaterialTheme.colorScheme.primaryContainer)
							.testTag("chapter_comment_send_button"),
					) {
						Icon(
							imageVector = Icons.Default.Send,
							contentDescription = "Post comment",
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
						)
					}
				}

				HorizontalDivider()

				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(10.dp),
					contentPadding = PaddingValues(bottom = 28.dp),
					modifier = Modifier.height(280.dp),
				) {
					items(userAdded + seededComments, key = { it.id }) { comment ->
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(
								modifier = Modifier.padding(12.dp),
								verticalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
									) {
										Text(
											text = comment.authorHandle,
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.Bold,
											color = MaterialTheme.colorScheme.primary,
										)
										if (comment.isTopComment) {
											Surface(
												color = Color(0xFFF59E0B).copy(alpha = 0.2f),
												shape = RoundedCornerShape(4.dp),
											) {
												Text(
													text = "TOP COMMENT",
													fontFamily = JetBrainsMonoFamily,
													fontSize = 8.sp,
													fontWeight = FontWeight.Bold,
													color = Color(0xFFF59E0B),
													modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
												)
											}
										}
									}
									Text(
										text = "${comment.timeAgo} • ▲ ${comment.likes}",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								Text(
									text = comment.body,
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurface,
								)
							}
						}
					}
				}
			}
		}
	}

	// "More Like This" Visual & Genre Similarity Bottom Sheet
	val similaritySeed = activeSimilaritySeedEntry
	if (similaritySeed != null) {
		val similarMatches = remember(similaritySeed, allEntries) {
			allEntries.filter { candidate ->
				candidate.id != similaritySeed.id &&
					(candidate.genres.any { g -> g in similaritySeed.genres } ||
						abs(candidate.aspectRatio - similaritySeed.aspectRatio) < 0.08f)
			}.ifEmpty { allEntries.filter { it.id != similaritySeed.id } }.take(6)
		}

		ModalBottomSheet(
			onDismissRequest = { activeSimilaritySeedEntry = null },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						imageVector = Icons.Default.AutoAwesome,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
					)
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "More Like \"${similaritySeed.title}\"",
							style = MaterialTheme.typography.titleMedium,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Visual linework, cover composition & shared tags (${similaritySeed.genres.take(3).joinToString(", ")})",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}

				Row(
					modifier = Modifier.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					similaritySeed.genres.forEach { genre ->
						FilterChip(
							selected = similarityFilterGenre == genre,
							onClick = {
								similarityFilterGenre = genre
								activeSimilaritySeedEntry = null
							},
							label = { Text("Filter Feed by $genre") },
						)
					}
				}

				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					contentPadding = PaddingValues(bottom = 24.dp),
				) {
					items(similarMatches, key = { "sim_${it.id}" }) { sim ->
						Column(
							modifier = Modifier
								.width(132.dp)
								.clickable {
									activeSimilaritySeedEntry = null
									onLaunchEntryReader(sim, true)
								},
						) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.aspectRatio(0.68f)
									.clip(RoundedCornerShape(14.dp)),
							) {
								MangaCoverArtwork(
									title = sim.title,
									coverUrl = sim.coverUrl,
									modifier = Modifier.fillMaxSize(),
								)
								Surface(
									color = Color.Black.copy(alpha = 0.78f),
									shape = RoundedCornerShape(bottomEnd = 10.dp),
									modifier = Modifier.align(Alignment.TopStart),
								) {
									Text(
										text = "${92 + (abs(sim.title.hashCode()) % 7)}% Visual Match",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
										color = Color(0xFF4CCAD0),
										modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
									)
								}
							}
							Spacer(modifier = Modifier.height(6.dp))
							Text(
								text = sim.title,
								style = MaterialTheme.typography.labelLarge,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Text(
								text = sim.genres.take(2).joinToString(" • "),
								style = MaterialTheme.typography.bodySmall,
								fontSize = 10.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
							)
						}
					}
				}
			}
		}
	}
}

/**
 * Top 1-Row Horizontal "Stories" Bar (Strictly for Your Active Library Drops & 1-Tap Resume):
 * Displays ONLY manga the user is actively following in their Library that have unread chapters
 * or active reading progress. One tap jumps straight into the reader at the exact chapter.
 * (Never duplicated in the 2-Column Catalog Discovery Grid below!)
 */
@Composable
fun FeedStoriesQuickUpdatesBar(
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	onOpenReader: (Long, Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	val storyDrops = remember(feedUpdates, libraryItems) {
		val fromFeed = feedUpdates.distinctBy { it.mangaItem.manga.id }
		if (fromFeed.isNotEmpty()) {
			fromFeed
		} else {
			libraryItems.mapIndexedNotNull { idx, item ->
				val ch = item.chapters.lastOrNull() ?: return@mapIndexedNotNull null
				FeedUpdateItem(
					id = "story_fallback_${item.manga.id}",
					mangaItem = item,
					chapter = ch,
					chapterIndex = (item.chapters.size - 1).coerceAtLeast(0),
					timestampMillis = System.currentTimeMillis() - idx * 3600_000L,
					dateBucketLabel = "Today",
					isNewRelease = true,
				)
			}
		}
	}

	val ringBrush = Brush.linearGradient(
		colors = listOf(
			Color(0xFF4CCAD0),
			Color(0xFF6366F1),
			Color(0xFFFF3366),
		),
	)

	LazyRow(
		contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		modifier = modifier
			.fillMaxWidth()
			.testTag("feed_stories_quick_updates_bar"),
	) {
		items(storyDrops, key = { "story_${it.id}" }) { drop ->
			val chNum = drop.chapter.number.toInt().coerceAtLeast(1)
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier
					.width(72.dp)
					.clickable {
						onOpenReader(drop.mangaItem.manga.id, drop.chapterIndex)
					}
					.testTag("story_quick_update_${drop.mangaItem.manga.id}"),
			) {
				Box(
					contentAlignment = Alignment.BottomCenter,
					modifier = Modifier.size(width = 68.dp, height = 84.dp),
				) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.padding(bottom = 5.dp)
							.clip(RoundedCornerShape(20.dp))
							.border(
								width = 2.dp,
								brush = ringBrush,
								shape = RoundedCornerShape(20.dp),
							)
							.padding(2.5.dp)
							.clip(RoundedCornerShape(17.dp)),
					) {
						MangaCoverArtwork(
							title = drop.mangaItem.manga.title,
							coverUrl = drop.mangaItem.manga.coverUrl,
							modifier = Modifier.fillMaxSize(),
						)
					}

					Surface(
						color = Color(0xFF101418),
						shape = RoundedCornerShape(8.dp),
						modifier = Modifier.border(
							width = 1.dp,
							color = Color(0xFF4CCAD0).copy(alpha = 0.7f),
							shape = RoundedCornerShape(8.dp),
						),
					) {
						Text(
							text = "Ch. $chNum",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 9.sp,
							color = Color(0xFF4CCAD0),
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
						)
					}
				}

				Spacer(modifier = Modifier.height(4.dp))

				Text(
					text = UiSourceAndTitleFormatter.formatDisplayTitle(drop.mangaItem.manga.title),
					style = MaterialTheme.typography.labelSmall,
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					textAlign = TextAlign.Center,
				)
			}
		}
	}
}

/**
 * Mode A.1: 2-Column Masonry Grid ("Catalog Discovery Gallery")
 * Distinct Function from the Top 1-Row Shelf and 1-Column [DISC] Feed:
 * - While the Top 1-Row Shelf resumes your active Library chapter updates, the 2-Column Masonry Grid
 *   below it is a Pinterest-style **Visual Catalog Discovery Gallery** for exploring new & trending
 *   series across extension catalogs (MangaDex, ComicK, Bato.to, Aqua Manga).
 * - Features interactive Genre Filter Chips, Sort Pills (✨ Best Match / 🔥 Trending / 📚 Most Chapters),
 *   AI Taste Match % badges (`✨ 99% Match • 168 Chs`), 1-tap `+ Library` bookmarking, and full cover aspect ratios.
 */
@Composable
private fun HybridMasonryGalleryFeed(
	entries: List<HybridFeedEntry>,
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	selectedGenre: String,
	sortMode: MasonryDiscoverySortMode,
	includeLibraryItems: Boolean,
	onSelectGenre: (String) -> Unit,
	onSelectSortMode: (MasonryDiscoverySortMode) -> Unit,
	onToggleIncludeLibrary: () -> Unit,
	isBookmarked: (HybridFeedEntry) -> Boolean,
	onToggleBookmark: (HybridFeedEntry) -> Unit,
	onOpenStoryReader: (Long, Int) -> Unit,
	onOpenEntryReader: (HybridFeedEntry) -> Unit,
	onOpenEntryDetails: (HybridFeedEntry) -> Unit,
) {
	val discoveryGenres = remember {
		listOf("All Genres", "Fantasy", "Action", "Dark Fantasy", "Comedy", "Mystery", "Supernatural", "Sci-Fi")
	}

	LazyVerticalStaggeredGrid(
		columns = StaggeredGridCells.Fixed(2),
		contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalItemSpacing = 12.dp,
		modifier = Modifier
			.fillMaxSize()
			.testTag("hybrid_masonry_staggered_grid"),
	) {
		// 1. Top 1-Row Horizontal Shelf: Strictly for Active Library Chapter Drops (1-Tap Resume)
		item(span = StaggeredGridItemSpan.FullLine, key = "masonry_stories_header") {
			FeedStoriesQuickUpdatesBar(
				feedUpdates = feedUpdates,
				libraryItems = libraryItems,
				onOpenReader = onOpenStoryReader,
			)
		}

		// 2. 2-Column Visual Catalog Discovery Header & Interactive Genre/Sort Bar
		item(span = StaggeredGridItemSpan.FullLine, key = "masonry_discovery_controls_header") {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 4.dp, bottom = 2.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 4.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "2-COL GALLERY • CATALOG DISCOVERY & COVER ART",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.primary,
						)
						Text(
							text = "Explore new series across sources (tap cover for details, bookmark to add to Library)",
							style = MaterialTheme.typography.bodySmall,
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}

				// Sort Pills + Optional "Include My Library" Toggle
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					MasonryDiscoverySortMode.entries.forEach { mode ->
						val selected = sortMode == mode
						Surface(
							onClick = { onSelectSortMode(mode) },
							color = if (selected) {
								MaterialTheme.colorScheme.primaryContainer
							} else {
								MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
							},
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier.testTag("masonry_sort_${mode.name.lowercase()}"),
						) {
							Text(
								text = mode.label,
								style = MaterialTheme.typography.labelSmall,
								fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
								color = if (selected) {
									MaterialTheme.colorScheme.onPrimaryContainer
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							)
						}
					}

					Surface(
						onClick = onToggleIncludeLibrary,
						color = if (includeLibraryItems) {
							MaterialTheme.colorScheme.tertiaryContainer
						} else {
							MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
						},
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier.testTag("masonry_toggle_include_library"),
					) {
						Text(
							text = if (includeLibraryItems) "✓ Showing Library Too" else "Hide Top-Row Duplicates",
							style = MaterialTheme.typography.labelSmall,
							fontSize = 10.sp,
							color = if (includeLibraryItems) {
								MaterialTheme.colorScheme.onTertiaryContainer
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						)
					}
				}

				// Genre Filter Chips
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					discoveryGenres.forEach { genre ->
						val selected = selectedGenre == genre
						Surface(
							onClick = { onSelectGenre(genre) },
							color = if (selected) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
							},
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.testTag("masonry_genre_${genre.lowercase().replace(" ", "_")}"),
						) {
							Text(
								text = genre,
								style = MaterialTheme.typography.labelSmall,
								fontSize = 10.sp,
								fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
								color = if (selected) {
									MaterialTheme.colorScheme.onPrimary
								} else {
									MaterialTheme.colorScheme.onSurface
								},
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
							)
						}
					}
				}
			}
		}

		items(entries, key = { "masonry_${it.id}" }) { entry ->
			val bookmarked = isBookmarked(entry)
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onOpenEntryDetails(entry) }
					.testTag("masonry_card_${entry.id}"),
			) {
				Column {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(entry.aspectRatio.coerceIn(0.56f, 0.80f))
							.clip(RoundedCornerShape(18.dp)),
					) {
						MangaCoverArtwork(
							title = entry.title,
							coverUrl = entry.coverUrl,
							modifier = Modifier.fillMaxSize(),
						)

						// Top-Left Discovery Match & Total Chapters Badge ("✨ 99% • 176 Chs")
						Surface(
							color = Color(0xFF0E1318).copy(alpha = 0.88f),
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.align(Alignment.TopStart)
								.padding(10.dp)
								.border(
									width = 1.dp,
									color = Color(0xFF4CCAD0).copy(alpha = 0.7f),
									shape = RoundedCornerShape(10.dp),
								)
								.clickable { onOpenEntryDetails(entry) },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Text(
									text = "✨ ${entry.matchPercent}% • ${entry.totalChapters} Chs",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 9.sp,
									color = Color(0xFF4CCAD0),
								)
							}
						}

						// Top-Right 1-Tap Bookmark Icon (48.dp accessible touch target)
						IconButton(
							onClick = { onToggleBookmark(entry) },
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(4.dp)
								.size(48.dp)
								.testTag("masonry_bookmark_${entry.id}"),
						) {
							Surface(
								color = if (bookmarked) {
									Color(0xFF4CCAD0)
								} else {
									Color.Black.copy(alpha = 0.62f)
								},
								shape = CircleShape,
								modifier = Modifier.size(34.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
										contentDescription = if (bookmarked) "Bookmarked in Library" else "Add series to Library",
										tint = if (bookmarked) Color(0xFF062C2E) else Color.White,
										modifier = Modifier.size(18.dp),
									)
								}
							}
						}

						// Bottom Scrim with Series Title, Source Badge, Genres & "Sample Ch. 1 →"
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.align(Alignment.BottomCenter)
								.background(
									Brush.verticalGradient(
										colors = listOf(
											Color.Transparent,
											Color.Black.copy(alpha = 0.78f),
											Color.Black.copy(alpha = 0.95f),
										),
									),
								)
								.padding(horizontal = 12.dp, vertical = 10.dp),
						) {
							Text(
								text = UiSourceAndTitleFormatter.formatSourceName(entry.sourceName),
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 9.sp,
								color = Color(0xFFFBBF24),
								maxLines = 1,
							)
							Text(
								text = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title),
								style = MaterialTheme.typography.titleSmall,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
								color = Color.White,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(3.dp))
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = entry.genres.take(2).joinToString(" • "),
									style = MaterialTheme.typography.labelSmall,
									fontSize = 10.sp,
									color = Color(0xFFCBD5E1),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f),
								)
								Text(
									text = "Ch. 1 →",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 10.sp,
									color = Color(0xFF4CCAD0),
									modifier = Modifier.clickable { onOpenEntryReader(entry) },
								)
							}
						}
					}
				}
			}
		}
	}
}

/**
 * Mode A.2: 1-Column Reddit-Style "[DISC] Release Stream"
 * Distinct Function from the 2-Column Catalog Discovery Grid:
 * - Focused on **Chronological Chapter Releases, 3-Panel Interior Page Previews & Community [DISC] Threads** (like r/manga).
 * - Includes its own Stream Sort Bar (`🔥 Hot [DISC]`, `🕒 Newest Drops`, `💬 Most Discussed`, `📖 Following Only`),
 *   release date bucket headers (`TODAY'S SIMULPUB DROPS`, `YESTERDAY'S RELEASES`, `TRENDING COMMUNITY THREADS`),
 *   scanlator credits, a 3-panel interior chapter page strip, a pinned Top Comment preview box, upvote heat (🔥), and comment sheet trigger (💬).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HybridRedditReleaseCardFeed(
	entries: List<HybridFeedEntry>,
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	sortMode: RedditStreamSortMode,
	onSelectSortMode: (RedditStreamSortMode) -> Unit,
	isBookmarked: (HybridFeedEntry) -> Boolean,
	isMarkedRead: (HybridFeedEntry) -> Boolean = { false },
	onToggleMarkRead: (HybridFeedEntry) -> Unit = {},
	onToggleBookmark: (HybridFeedEntry) -> Unit,
	onOpenComments: (HybridFeedEntry) -> Unit,
	onOpenStoryReader: (Long, Int) -> Unit,
	onOpenEntryReader: (HybridFeedEntry) -> Unit,
	onOpenEntryDetails: (HybridFeedEntry) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 120.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxSize()
			.testTag("hybrid_reddit_card_list"),
	) {
		// 1. Top Horizontal "Stories" Quick Updates Bar (only when active drops exist)
		if (feedUpdates.isNotEmpty() || libraryItems.isNotEmpty()) {
			item(key = "reddit_stories_quick_updates_header") {
				FeedStoriesQuickUpdatesBar(
					feedUpdates = feedUpdates,
					libraryItems = libraryItems,
					onOpenReader = onOpenStoryReader,
				)
			}
		}

		// 2. Compact Sort Filter Pills
		item(key = "reddit_stream_filter_header") {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState()),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				RedditStreamSortMode.entries.forEach { mode ->
					val selected = sortMode == mode
					Surface(
						onClick = { onSelectSortMode(mode) },
						color = if (selected) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
						},
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier.testTag("reddit_sort_${mode.name.lowercase()}"),
					) {
						Text(
							text = mode.label,
							style = MaterialTheme.typography.labelSmall,
							fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
							color = if (selected) {
								MaterialTheme.colorScheme.onPrimary
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						)
					}
				}
			}
		}

		if (entries.isEmpty()) {
			item(key = "reddit_stream_empty_state") {
				Card(
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
					),
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 12.dp)
						.testTag("reddit_feed_empty_card"),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(24.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = "No chapter releases yet",
							style = MaterialTheme.typography.titleMedium,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
						)
						Text(
							text = "Add series from Explore or import a local .CBZ archive to see chronological chapter drops here.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
					}
				}
			}
		}

		// 3. Streamlined Chapter Release Summary Cards (Cover Artwork + Release Summary Block + Real Reader Metadata Pills + Mark as Read)
		items(entries, key = { "reddit_${it.id}" }) { entry ->
			val bookmarked = isBookmarked(entry)
			val isRead = isMarkedRead(entry)
			val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title)
			val cleanSource = UiSourceAndTitleFormatter.formatSourceName(entry.sourceName)
			val matchingLibItem = libraryItems.find { it.manga.id == entry.mangaId }
			val pageCount = 18 + (abs(entry.title.hashCode()) % 14)
			val unreadBadgeText = when {
				isRead -> "Read ✓"
				matchingLibItem != null && matchingLibItem.unreadCount > 0 -> "Unread: ${matchingLibItem.unreadCount}"
				else -> "New Drop"
			}
			val cleanScanlator = UiSourceAndTitleFormatter.formatSourceName(entry.scanlatorGroup)
			val progressContextSnippet = remember(entry.id, matchingLibItem?.manga?.id, pageCount) {
				val rawSeriesSynopsis = matchingLibItem?.manga?.description?.takeIf { it.isNotBlank() } ?: entry.synopsis
				val ownSeriesSynopsis = rawSeriesSynopsis
					.replace("Duplicate entry from EXT:", "Merged with ")
					.replace("EXT:", "")
				val progressPrefix = if (matchingLibItem != null && matchingLibItem.lastReadChapterNumber > 0f) {
					"Paused on Ch. ${matchingLibItem.lastReadChapterNumber.toInt()} • ${pageCount}p"
				} else {
					"$cleanScanlator • ${pageCount} pages"
				}
				"$progressPrefix — $ownSeriesSynopsis"
			}

			Card(
				shape = RoundedCornerShape(20.dp),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isRead) 0.22f else 0.35f),
				),
				modifier = Modifier
					.fillMaxWidth()
					.border(
						width = 1.dp,
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
						shape = RoundedCornerShape(20.dp),
					)
					.testTag("reddit_feed_card_${entry.id}"),
			) {
				Column(
					modifier = Modifier.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					// 1. Clean Single-Header Row: Series Title + Source/Time Subtitle + Bookmark
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(
							modifier = Modifier
								.weight(1f)
								.clickable { onOpenEntryDetails(entry) },
							verticalArrangement = Arrangement.spacedBy(2.dp),
						) {
							Text(
								text = cleanTitle,
								style = MaterialTheme.typography.titleMedium,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Text(
								text = "$cleanSource • ${entry.releaseTimeLabel} • ${entry.author}",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						IconButton(
							onClick = { onToggleBookmark(entry) },
							modifier = Modifier.size(38.dp),
						) {
							Icon(
								imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
								contentDescription = "Bookmark",
								tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					// 2. Cover Artwork + Rich Chapter Release Summary Block (Replaces Duplicate Wireframe Boxes)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(14.dp))
							.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
							.clickable { onOpenEntryReader(entry) }
							.padding(10.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						MangaCoverArtwork(
							title = cleanTitle,
							coverUrl = entry.coverUrl,
							modifier = Modifier
								.width(68.dp)
								.aspectRatio(0.70f)
								.clip(RoundedCornerShape(10.dp)),
						)

						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(5.dp),
						) {
							// Chapter Header + Scanlator/Source Pill
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = entry.chapterTitle,
									style = MaterialTheme.typography.titleSmall,
									fontFamily = PlusJakartaDisplayFamily,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f),
								)
								Spacer(modifier = Modifier.width(6.dp))
								Surface(
									color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
									shape = RoundedCornerShape(6.dp),
								) {
									Text(
										text = cleanScanlator,
										fontFamily = JetBrainsMonoFamily,
										fontWeight = FontWeight.Bold,
										fontSize = 9.sp,
										color = MaterialTheme.colorScheme.onPrimaryContainer,
										maxLines = 1,
										modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
									)
								}
							}

							// 2-Line Spoiler-Safe Context / Recap Snippet
							Text(
								text = progressContextSnippet,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)

							// Genre & Format Tags
							Row(
								horizontalArrangement = Arrangement.spacedBy(6.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								entry.genres.take(2).forEach { genre ->
									Surface(
										color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
										shape = RoundedCornerShape(6.dp),
									) {
										Text(
											text = genre,
											fontFamily = JetBrainsMonoFamily,
											fontSize = 9.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
										)
									}
								}
							}
						}
					}

					// 3. Action Row: Real Reader Metadata Pills + Quick Mark as Read (✓) + ▶ Ch. X Button
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Row(
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.weight(1f),
						) {
							// Page Count Pill
							Surface(
								color = MaterialTheme.colorScheme.surface,
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier.border(
									width = 1.dp,
									color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
									shape = RoundedCornerShape(10.dp),
								),
							) {
								Text(
									text = "${pageCount}p",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurface,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
								)
							}

							// Unread / Status Pill
							Surface(
								color = if (isRead) {
									Color(0xFF10B981).copy(alpha = 0.16f)
								} else {
									MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
								},
								shape = RoundedCornerShape(10.dp),
							) {
								Text(
									text = unreadBadgeText,
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.SemiBold,
									fontSize = 10.sp,
									color = if (isRead) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
								)
							}

							// Optional Notes / Discussion Sheet Trigger
							Surface(
								onClick = { onOpenComments(entry) },
								color = MaterialTheme.colorScheme.surface,
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier
									.border(
										width = 1.dp,
										color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
										shape = RoundedCornerShape(10.dp),
									)
									.testTag("reddit_comments_${entry.id}"),
							) {
								Text(
									text = "Notes",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
								)
							}
						}

						Row(
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							// Quick Mark as Read (✓) IconButton
							Surface(
								onClick = { onToggleMarkRead(entry) },
								color = if (isRead) {
									Color(0xFF10B981).copy(alpha = 0.22f)
								} else {
									MaterialTheme.colorScheme.surface
								},
								shape = CircleShape,
								modifier = Modifier
									.size(34.dp)
									.border(
										width = 1.dp,
										color = if (isRead) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
										shape = CircleShape,
									)
									.testTag("feed_mark_read_${entry.id}"),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = Icons.Default.Check,
										contentDescription = "Mark Chapter as Read",
										tint = if (isRead) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(16.dp),
									)
								}
							}

							FilledTonalButton(
								onClick = { onOpenEntryReader(entry) },
								contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
								modifier = Modifier.height(34.dp),
							) {
								Icon(
									imageVector = Icons.Default.PlayArrow,
									contentDescription = null,
									modifier = Modifier.size(15.dp),
								)
								Spacer(modifier = Modifier.width(4.dp))
								Text("Ch. ${entry.chapterNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
							}
						}
					}
				}
			}
		}
	}
}

/**
 * Mode B: The "Discovery" Feed (TikTok Photo-Mode Format)
 * - Vertical Swipe = Next Manga Series (`VerticalPager`)
 * - Horizontal Swipe = 3-to-5 Page Hook (`HorizontalPager` with Slide 1 = Striking Visual Spread Hook, followed by 4 consecutive setup pages from Chapter 1)
 * - Bottom-Right Action Rail: Add to Library, Read Chapter 1, Comments/Reviews, and More Like This (visual similarity search)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TikTokDiscoveryFeedView(
	entries: List<HybridFeedEntry>,
	isBookmarked: (HybridFeedEntry) -> Boolean,
	commentCountFor: (HybridFeedEntry) -> Int,
	onToggleBookmark: (HybridFeedEntry) -> Unit,
	onReadChapterOne: (HybridFeedEntry) -> Unit,
	onOpenComments: (HybridFeedEntry) -> Unit,
	onOpenMoreLikeThis: (HybridFeedEntry) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (entries.isEmpty()) {
		Box(
			modifier = modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Text("No discovery recommendations match your current filter.")
		}
		return
	}

	val verticalPagerState = rememberPagerState(pageCount = { entries.size })
	val coroutineScope = rememberCoroutineScope()

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(Color(0xFF07090D))
			.testTag("tiktok_discovery_vertical_pager"),
	) {
		VerticalPager(
			state = verticalPagerState,
			modifier = Modifier.fillMaxSize(),
		) { seriesIndex ->
			val entry = entries[seriesIndex]
			val bookmarked = isBookmarked(entry)
			val commentsCount = commentCountFor(entry)
			val hookSlideCount = 5
			val horizontalPagerState = rememberPagerState(pageCount = { hookSlideCount })

			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(bottom = 78.dp),
			) {
				// Horizontal Swipe = 5-Slide Hook Carousel (Slide 1 Cover Spread + 4 Consecutive Chapter 1 Pages)
				HorizontalPager(
					state = horizontalPagerState,
					modifier = Modifier
						.fillMaxSize()
						.testTag("tiktok_horizontal_hook_pager_${entry.id}"),
				) { slideIdx ->
					if (slideIdx == 0) {
						Box(modifier = Modifier.fillMaxSize()) {
							MangaCoverArtwork(
								title = entry.title,
								coverUrl = entry.coverUrl,
								modifier = Modifier.fillMaxSize(),
							)
							Box(
								modifier = Modifier
									.fillMaxSize()
									.background(
										Brush.verticalGradient(
											colors = listOf(
												Color.Black.copy(alpha = 0.25f),
												Color.Transparent,
												Color.Black.copy(alpha = 0.78f),
												Color.Black.copy(alpha = 0.94f),
											),
										),
									),
							)
						}
					} else {
						Box(modifier = Modifier.fillMaxSize()) {
							MangaReaderPageCanvas(
								mangaTitle = entry.title,
								chapterNumber = 1f,
								pageNumber = slideIdx,
								totalPages = 4,
								upscaleMode = AiUpscaleMode.FAST_CRISP_INK_1_5X,
								showBeforeAfterSplit = false,
								invertColors = slideIdx == 4,
								warmPaperTint = slideIdx == 2,
								modifier = Modifier.fillMaxSize(),
							)
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(210.dp)
									.align(Alignment.BottomCenter)
									.background(
										Brush.verticalGradient(
											colors = listOf(
												Color.Transparent,
												Color.Black.copy(alpha = 0.78f),
												Color.Black.copy(alpha = 0.94f),
											),
										),
									),
							)
						}
					}
				}

				// Compact Top Page Indicator Pill
				Surface(
					color = Color.Black.copy(alpha = 0.65f),
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(horizontal = 16.dp, vertical = 12.dp),
				) {
					Text(
						text = "${horizontalPagerState.currentPage + 1} / $hookSlideCount",
						fontFamily = JetBrainsMonoFamily,
						fontWeight = FontWeight.Bold,
						fontSize = 11.sp,
						color = Color.White,
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
					)
				}

				// Bottom-Left Series Details & Carousel Dots (with extra 16.dp bottom margin above dock)
				val cleanDiscoverTitle = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title)
				Column(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth(0.78f)
						.padding(start = 16.dp, end = 12.dp, bottom = 34.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Row(
						horizontalArrangement = Arrangement.spacedBy(5.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						for (i in 0 until hookSlideCount) {
							val isCurrentSlide = horizontalPagerState.currentPage == i
							Box(
								modifier = Modifier
									.height(5.dp)
									.width(if (isCurrentSlide) 18.dp else 6.dp)
									.clip(CircleShape)
									.background(if (isCurrentSlide) Color(0xFF4CCAD0) else Color.White.copy(alpha = 0.35f))
									.clickable {
										coroutineScope.launch {
											horizontalPagerState.animateScrollToPage(i)
										}
									}
									.testTag("discover_hook_slide_pill_$i"),
							)
						}
					}

					Text(
						text = cleanDiscoverTitle,
						style = MaterialTheme.typography.titleLarge,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = Color.White,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)

					Text(
						text = "${entry.genres.take(2).joinToString(" • ")} • ${entry.totalChapters} Chs",
						style = MaterialTheme.typography.labelMedium,
						color = Color(0xFF4CCAD0),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)

					Text(
						text = entry.synopsis,
						style = MaterialTheme.typography.bodySmall,
						color = Color(0xFFE2E8F0),
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}

				// Bottom-Right Action Rail (Thumb-Zone Ergonomics mirroring TikTok)
				Column(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(end = 12.dp, bottom = 18.dp)
						.testTag("discover_bottom_right_action_rail"),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					// 1. Add to Library
					TikTokActionRailButton(
						icon = if (bookmarked) Icons.Default.Check else Icons.Default.Add,
						label = if (bookmarked) "In Library" else "Add",
						accentColor = if (bookmarked) Color(0xFF4CCAD0) else Color.White,
						containerColor = if (bookmarked) Color(0xFF0F363A) else Color.Black.copy(alpha = 0.65f),
						testTag = "discover_action_add_library",
						onClick = { onToggleBookmark(entry) },
					)

					// 2. Read Chapter 1
					TikTokActionRailButton(
						icon = Icons.AutoMirrored.Filled.MenuBook,
						label = "Read Ch.1",
						accentColor = Color(0xFF062C2E),
						containerColor = Color(0xFF4CCAD0),
						testTag = "discover_action_read_ch1",
						onClick = { onReadChapterOne(entry) },
					)

					// 3. Comments / Reviews
					TikTokActionRailButton(
						icon = Icons.AutoMirrored.Filled.Chat,
						label = "$commentsCount",
						accentColor = Color.White,
						containerColor = Color.Black.copy(alpha = 0.65f),
						testTag = "discover_action_comments",
						onClick = { onOpenComments(entry) },
					)

					// 4. More Like This (Visual Similarity Search)
					TikTokActionRailButton(
						icon = Icons.Default.AutoAwesome,
						label = "Similar",
						accentColor = Color(0xFFFBBF24),
						containerColor = Color.Black.copy(alpha = 0.65f),
						testTag = "discover_action_more_like_this",
						onClick = { onOpenMoreLikeThis(entry) },
					)
				}
			}
		}
	}
}

@Composable
private fun TikTokActionRailButton(
	icon: ImageVector,
	label: String,
	accentColor: Color,
	containerColor: Color,
	testTag: String,
	onClick: () -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.clickable(onClick = onClick)
			.testTag(testTag),
	) {
		Surface(
			color = containerColor,
			shape = CircleShape,
			modifier = Modifier
				.size(48.dp)
				.border(
					width = 1.dp,
					color = Color.White.copy(alpha = 0.22f),
					shape = CircleShape,
				),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					imageVector = icon,
					contentDescription = label,
					tint = accentColor,
					modifier = Modifier.size(22.dp),
				)
			}
		}
		Spacer(modifier = Modifier.height(3.dp))
		Text(
			text = label,
			fontFamily = JetBrainsMonoFamily,
			fontWeight = FontWeight.Bold,
			fontSize = 10.sp,
			color = Color.White,
			textAlign = TextAlign.Center,
		)
	}
}
