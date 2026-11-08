package org.koitharu.kotatsu.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.core.util.TitleNormalizer
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

enum class NekoHubAndGalleryFilter(val label: String) {
	ALL("All"),
	MY_FOLLOWS("My Follows"),
	UNREAD_ONLY("Unread Only"),
	NEW_AND_TRENDING("New & Trending"),
}

enum class NekoHubSectionCategory(val headerTitle: String) {
	FEED_UPDATES("Feed updates"),
	POPULAR_NEW_TITLES("Popular new titles"),
	LATEST_UPLOADS("Latest uploads"),
}

@Composable
fun NekoTopFilterChipRow(
	activeFilter: NekoHubAndGalleryFilter,
	onSelectFilter: (NekoHubAndGalleryFilter) -> Unit,
	activeSectionFilter: NekoHubSectionCategory? = null,
	onClearSectionFilter: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp)
			.horizontalScroll(rememberScrollState())
			.testTag("neko_top_filter_chip_row"),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (activeSectionFilter != null && onClearSectionFilter != null) {
			Surface(
				onClick = onClearSectionFilter,
				color = Color(0xFF5CE1E6).copy(alpha = 0.22f),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.border(
						width = 1.dp,
						color = Color(0xFF5CE1E6),
						shape = RoundedCornerShape(14.dp),
					)
					.testTag("neko_clear_section_filter_chip"),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = "Back to Hub",
						tint = Color(0xFF5CE1E6),
						modifier = Modifier.size(13.dp),
					)
					Text(
						text = activeSectionFilter.headerTitle,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = Color(0xFF5CE1E6),
					)
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = "Clear section filter",
						tint = Color(0xFF5CE1E6),
						modifier = Modifier.size(13.dp),
					)
				}
			}
		}

		NekoHubAndGalleryFilter.entries.forEach { filter ->
			val selected = activeFilter == filter
			Surface(
				onClick = { onSelectFilter(filter) },
				color = if (selected) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
				},
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.testTag("neko_filter_chip_${filter.name.lowercase()}"),
			) {
				Text(
					text = filter.label,
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

/**
 * 1.2 Neko Sectioned Carousel Hub (`NekoBrowseHubView`):
 * - Top horizontal filter chip row (`All` | `My Follows` | `Unread Only` | `New & Trending`)
 *   matching the exact position and style of the filter row in `Updates` (no double-stacked bottom bar).
 * - Vertically scrollable `LazyColumn` (`contentPadding = PaddingValues(bottom = 120.dp)`)
 *   containing horizontal section carousels (`LazyRow`):
 *   - Section 1: `Feed updates →` (Latest unread chapters across followed/library series)
 *   - Section 2: `Popular new titles →` (Trending / high-velocity new series)
 *   - Section 3: `Latest uploads →` (Recently updated across active sources)
 * - Tapping any section header switches to `Feed -> Gallery` filtered to that section.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NekoBrowseHubView(
	entries: List<HybridFeedEntry>,
	libraryItems: List<LibraryMangaItem>,
	activeFilter: NekoHubAndGalleryFilter = NekoHubAndGalleryFilter.ALL,
	onSelectFilter: (NekoHubAndGalleryFilter) -> Unit = {},
	onSelectSectionForGallery: ((NekoHubSectionCategory) -> Unit)? = null,
	galleryColumns: Int = 5,
	onSelectGalleryColumns: (Int) -> Unit = {},
	isBookmarked: (HybridFeedEntry) -> Boolean,
	onToggleBookmark: (HybridFeedEntry) -> Unit,
	onOpenReader: (HybridFeedEntry) -> Unit,
	onOpenDetails: (HybridFeedEntry) -> Unit,
	onRequestFocusTopSearch: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var drillDownSection by rememberSaveable { mutableStateOf<NekoHubSectionCategory?>(null) }
	var quickPeekEntry by remember { mutableStateOf<HybridFeedEntry?>(null) }

	val haptic = LocalHapticFeedback.current

	if (drillDownSection != null) {
		BackHandler {
			drillDownSection = null
		}
	} else if (activeFilter != NekoHubAndGalleryFilter.ALL) {
		BackHandler {
			onSelectFilter(NekoHubAndGalleryFilter.ALL)
		}
	}

	val libraryMangaIds = remember(libraryItems) {
		libraryItems.map { it.manga.id }.toSet()
	}
	val librarySlugs = remember(libraryItems) {
		libraryItems.map { TitleNormalizer.toNormalizedSlug(it.manga.title) }.toSet()
	}

	val isEntryInLibrary: (HybridFeedEntry) -> Boolean = { entry ->
		isBookmarked(entry) ||
			(entry.mangaId != null && entry.mangaId in libraryMangaIds) ||
			(TitleNormalizer.toNormalizedSlug(entry.title) in librarySlugs)
	}

	val filteredPool = remember(entries, activeFilter, libraryItems, libraryMangaIds, librarySlugs) {
		when (activeFilter) {
			NekoHubAndGalleryFilter.ALL -> entries
			NekoHubAndGalleryFilter.MY_FOLLOWS -> entries.filter { isEntryInLibrary(it) }.ifEmpty { entries }
			NekoHubAndGalleryFilter.UNREAD_ONLY -> entries.filter { entry ->
				val libMatch = libraryItems.find { it.manga.id == entry.mangaId }
				(libMatch != null && libMatch.unreadCount > 0) || entry.isNewDrop
			}.ifEmpty { entries }
			NekoHubAndGalleryFilter.NEW_AND_TRENDING -> entries
				.filter { it.isNewDrop || it.matchPercent >= 95 }
				.sortedByDescending { it.baseHeatScore + it.matchPercent * 20 }
				.ifEmpty { entries }
		}
	}

	val feedUpdateEntries = remember(filteredPool) {
		filteredPool.filter { it.inLibrary || it.mangaId != null }.ifEmpty { filteredPool }
	}
	val popularNewEntries = remember(filteredPool) {
		filteredPool.sortedByDescending { it.baseHeatScore + it.matchPercent * 25 }
	}
	val latestUploadEntries = remember(filteredPool) {
		filteredPool.sortedByDescending { (if (it.isNewDrop) 5_000 else 0) + it.chapterNumber }
	}

	val handleOpenSection: (NekoHubSectionCategory) -> Unit = { section ->
		if (onSelectSectionForGallery != null) {
			onSelectSectionForGallery(section)
		} else {
			drillDownSection = section
		}
	}

	if (drillDownSection != null) {
		val sectionItems = when (drillDownSection!!) {
			NekoHubSectionCategory.FEED_UPDATES -> feedUpdateEntries
			NekoHubSectionCategory.POPULAR_NEW_TITLES -> popularNewEntries
			NekoHubSectionCategory.LATEST_UPLOADS -> latestUploadEntries
		}
		NekoDenseGalleryGrid(
			title = drillDownSection!!.headerTitle,
			entries = sectionItems,
			libraryItems = libraryItems,
			activeFilter = activeFilter,
			onSelectFilter = onSelectFilter,
			activeSectionFilter = drillDownSection,
			onClearSectionFilter = { drillDownSection = null },
			galleryColumns = galleryColumns,
			showRankFooter = drillDownSection == NekoHubSectionCategory.POPULAR_NEW_TITLES,
			onSelectGalleryColumns = onSelectGalleryColumns,
			isBookmarked = isEntryInLibrary,
			onToggleBookmark = onToggleBookmark,
			onOpenReader = onOpenReader,
			onOpenDetails = onOpenDetails,
			onBack = { drillDownSection = null },
			modifier = modifier,
		)
		return
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.testTag("neko_browse_hub_view"),
	) {
		// Top Horizontal Filter Chip Row directly below Updates | Hub | Gallery | For You
		NekoTopFilterChipRow(
			activeFilter = activeFilter,
			onSelectFilter = onSelectFilter,
		)

		LazyColumn(
			contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(22.dp),
			modifier = Modifier
				.fillMaxSize()
				.testTag("neko_hub_lazy_column"),
		) {
			// Section 1: Feed updates →
			item(key = "neko_hub_sec_feed_updates") {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					NekoHubSectionHeader(
						title = NekoHubSectionCategory.FEED_UPDATES.headerTitle,
						onClick = { handleOpenSection(NekoHubSectionCategory.FEED_UPDATES) },
					)
					LazyRow(
						contentPadding = PaddingValues(horizontal = 14.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						itemsIndexed(
							items = feedUpdateEntries,
							key = { _, item -> "hub_feed_${item.id}" },
						) { idx, entry ->
							val inLib = isEntryInLibrary(entry)
							val volNum = (entry.chapterNumber / 10).coerceAtLeast(1)
							val cleanChTitle = entry.chapterTitle
								.removePrefix("Chapter ${entry.chapterNumber}: ")
								.removePrefix("Ch. ${entry.chapterNumber} • ")
								.trim()
							NekoCarouselCard(
								title = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title),
								subtitle = "Vol.$volNum Ch.${entry.chapterNumber} - $cleanChTitle",
								coverUrl = entry.coverUrl,
								inLibrary = inLib,
								onClick = { onOpenDetails(entry) },
								onLongClick = {
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
									onToggleBookmark(entry)
								},
								modifier = Modifier.testTag("neko_hub_feed_card_$idx"),
							)
						}
					}
				}
			}

			// Section 2: Popular new titles →
			item(key = "neko_hub_sec_popular_new") {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					NekoHubSectionHeader(
						title = NekoHubSectionCategory.POPULAR_NEW_TITLES.headerTitle,
						onClick = { handleOpenSection(NekoHubSectionCategory.POPULAR_NEW_TITLES) },
					)
					LazyRow(
						contentPadding = PaddingValues(horizontal = 14.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						itemsIndexed(
							items = popularNewEntries,
							key = { _, item -> "hub_pop_${item.id}" },
						) { idx, entry ->
							val inLib = isEntryInLibrary(entry)
							NekoCarouselCard(
								title = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title),
								subtitle = "No. ${idx + 1}",
								coverUrl = entry.coverUrl,
								inLibrary = inLib,
								onClick = { onOpenDetails(entry) },
								onLongClick = {
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
									onToggleBookmark(entry)
								},
								modifier = Modifier.testTag("neko_hub_popular_card_$idx"),
							)
						}
					}
				}
			}

			// Section 3: Latest uploads →
			item(key = "neko_hub_sec_latest_uploads") {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					NekoHubSectionHeader(
						title = NekoHubSectionCategory.LATEST_UPLOADS.headerTitle,
						onClick = { handleOpenSection(NekoHubSectionCategory.LATEST_UPLOADS) },
					)
					LazyRow(
						contentPadding = PaddingValues(horizontal = 14.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						itemsIndexed(
							items = latestUploadEntries,
							key = { _, item -> "hub_latest_${item.id}" },
						) { idx, entry ->
							val inLib = isEntryInLibrary(entry)
							val volNum = (entry.chapterNumber / 10).coerceAtLeast(1)
							NekoCarouselCard(
								title = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title),
								subtitle = "Vol.$volNum Ch.${entry.chapterNumber} • ${entry.releaseTimeLabel}",
								coverUrl = entry.coverUrl,
								inLibrary = inLib,
								onClick = { onOpenDetails(entry) },
								onLongClick = {
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
									onToggleBookmark(entry)
								},
								modifier = Modifier.testTag("neko_hub_latest_card_$idx"),
							)
						}
					}
				}
			}
		}
	}

	val peekTarget = quickPeekEntry
	if (peekTarget != null) {
		NekoQuickPeekDetailsSheet(
			entry = peekTarget,
			inLibrary = isEntryInLibrary(peekTarget),
			onDismiss = { quickPeekEntry = null },
			onToggleLibrary = {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onToggleBookmark(peekTarget)
			},
			onOpenReader = {
				val target = peekTarget
				quickPeekEntry = null
				onOpenReader(target)
			},
			onOpenFullDetails = {
				val target = peekTarget
				quickPeekEntry = null
				onOpenDetails(target)
			},
		)
	}
}

@Composable
private fun NekoHubSectionHeader(
	title: String,
	onClick: () -> Unit,
) {
	val accentColor = Color(0xFF5CE1E6)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 4.dp)
			.testTag("neko_section_header_${title.lowercase().replace(" ", "_")}"),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleLarge,
			fontFamily = PlusJakartaDisplayFamily,
			fontWeight = FontWeight.Bold,
			color = accentColor,
		)
		Spacer(modifier = Modifier.width(8.dp))
		Icon(
			imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
			contentDescription = "Open $title in Dense Gallery Grid",
			tint = accentColor,
			modifier = Modifier.size(20.dp),
		)
	}
}

/**
 * Neko Section Carousel Card (`165.dp` width):
 * - Top: Rounded square/portrait cover (`165.dp x 165.dp`, `RoundedCornerShape(12.dp)`).
 * - Top-Left `In Library` badge (`RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp)`,
 *   background `#5CE1E6` / `#B8C4FF`, dark text `#0F1419`, `labelSmall`).
 * - Below Cover Line 1: Manga Title (`bodyMedium`, bold, max 2 lines, ellipsized).
 * - Below Cover Line 2: Chapter or Rank subtitle (`labelSmall`, muted gray).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NekoCarouselCard(
	title: String,
	subtitle: String,
	coverUrl: String,
	inLibrary: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.width(165.dp)
			.clip(RoundedCornerShape(12.dp))
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Box(
			modifier = Modifier
				.size(165.dp)
				.clip(RoundedCornerShape(12.dp))
				.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
		) {
			MangaCoverArtwork(
				title = title,
				coverUrl = coverUrl,
				modifier = Modifier.fillMaxSize(),
			)

			if (inLibrary) {
				Surface(
					color = Color(0xFF5CE1E6),
					shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp),
					modifier = Modifier.align(Alignment.TopStart),
				) {
					Text(
						text = "In Library",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = Color(0xFF0F1419),
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(6.dp))

		Text(
			text = title,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)

		Spacer(modifier = Modifier.height(2.dp))

		Text(
			text = subtitle,
			style = MaterialTheme.typography.labelSmall,
			color = Color(0xFF9EA4B0),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/**
 * 1.3 Neko 5-Column Dense Gallery View (`NekoDenseGalleryGrid`):
 * - Accessible both as a top-level `Gallery` tab in Feed AND whenever the user taps a section header
 *   (`Popular new titles →`, `Feed updates →`, `Latest uploads →`).
 * - Includes the top horizontal filter chip row (`All` | `My Follows` | `Unread Only` | `New & Trending`)
 *   plus a back/clear filter chip when filtered from a `Hub` section header.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NekoDenseGalleryGrid(
	title: String,
	entries: List<HybridFeedEntry>,
	libraryItems: List<LibraryMangaItem>,
	activeFilter: NekoHubAndGalleryFilter = NekoHubAndGalleryFilter.ALL,
	onSelectFilter: (NekoHubAndGalleryFilter) -> Unit = {},
	activeSectionFilter: NekoHubSectionCategory? = null,
	onClearSectionFilter: (() -> Unit)? = null,
	galleryColumns: Int = 5,
	showRankFooter: Boolean = true,
	onSelectGalleryColumns: (Int) -> Unit = {},
	isBookmarked: (HybridFeedEntry) -> Boolean,
	onToggleBookmark: (HybridFeedEntry) -> Unit,
	onOpenReader: (HybridFeedEntry) -> Unit,
	onOpenDetails: (HybridFeedEntry) -> Unit,
	onBack: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val haptic = LocalHapticFeedback.current
	var quickPeekEntry by remember { mutableStateOf<HybridFeedEntry?>(null) }
	val effectiveColumns = galleryColumns.coerceIn(3, 5)

	if (activeSectionFilter != null && onClearSectionFilter != null) {
		BackHandler {
			onClearSectionFilter()
		}
	}

	val libraryMangaIds = remember(libraryItems) {
		libraryItems.map { it.manga.id }.toSet()
	}
	val librarySlugs = remember(libraryItems) {
		libraryItems.map { TitleNormalizer.toNormalizedSlug(it.manga.title) }.toSet()
	}

	val isEntryInLibrary: (HybridFeedEntry) -> Boolean = { entry ->
		isBookmarked(entry) ||
			(entry.mangaId != null && entry.mangaId in libraryMangaIds) ||
			(TitleNormalizer.toNormalizedSlug(entry.title) in librarySlugs)
	}

	val filteredGalleryEntries = remember(entries, activeFilter, activeSectionFilter, libraryItems, libraryMangaIds, librarySlugs) {
		val byFilter = when (activeFilter) {
			NekoHubAndGalleryFilter.ALL -> entries
			NekoHubAndGalleryFilter.MY_FOLLOWS -> entries.filter { isEntryInLibrary(it) }.ifEmpty { entries }
			NekoHubAndGalleryFilter.UNREAD_ONLY -> entries.filter { entry ->
				val libMatch = libraryItems.find { it.manga.id == entry.mangaId }
				(libMatch != null && libMatch.unreadCount > 0) || entry.isNewDrop
			}.ifEmpty { entries }
			NekoHubAndGalleryFilter.NEW_AND_TRENDING -> entries
				.filter { it.isNewDrop || it.matchPercent >= 95 }
				.sortedByDescending { it.baseHeatScore + it.matchPercent * 20 }
				.ifEmpty { entries }
		}
		when (activeSectionFilter) {
			null -> byFilter
			NekoHubSectionCategory.FEED_UPDATES -> byFilter.filter { it.inLibrary || it.mangaId != null }.ifEmpty { byFilter }
			NekoHubSectionCategory.POPULAR_NEW_TITLES -> byFilter.sortedByDescending { it.baseHeatScore + it.matchPercent * 25 }
			NekoHubSectionCategory.LATEST_UPLOADS -> byFilter.sortedByDescending { (if (it.isNewDrop) 5_000 else 0) + it.chapterNumber }
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.testTag("neko_dense_gallery_container"),
	) {
		// Top Horizontal Filter Chip Row (All | My Follows | Unread Only | New & Trending + Section Back Chip)
		NekoTopFilterChipRow(
			activeFilter = activeFilter,
			onSelectFilter = onSelectFilter,
			activeSectionFilter = activeSectionFilter,
			onClearSectionFilter = onClearSectionFilter,
		)

		// Compact Header Bar with optional Back button + Column Switcher (3 / 4 / 5 columns)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				val backHandler = onBack ?: onClearSectionFilter
				if (backHandler != null) {
					IconButton(
						onClick = backHandler,
						modifier = Modifier
							.size(34.dp)
							.testTag("neko_gallery_back_button"),
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back to Hub",
							tint = Color(0xFF5CE1E6),
							modifier = Modifier.size(18.dp),
						)
					}
				} else {
					Icon(
						imageVector = Icons.Default.GridView,
						contentDescription = null,
						tint = Color(0xFF5CE1E6),
						modifier = Modifier.size(16.dp),
					)
				}
				Text(
					text = "$title (${filteredGalleryEntries.size})",
					style = MaterialTheme.typography.titleSmall,
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			// Quick 3 / 4 / 5 Column Toggle Pill
			Row(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				listOf(3, 4, 5).forEach { cols ->
					val selected = effectiveColumns == cols
					Surface(
						onClick = { onSelectGalleryColumns(cols) },
						color = if (selected) {
							Color(0xFF5CE1E6).copy(alpha = 0.22f)
						} else {
							MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
						},
						shape = RoundedCornerShape(8.dp),
						modifier = Modifier.testTag("neko_gallery_cols_$cols"),
					) {
						Text(
							text = "${cols}c",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
							fontSize = 10.sp,
							color = if (selected) Color(0xFF5CE1E6) else MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
						)
					}
				}
			}
		}

		LazyVerticalGrid(
			columns = GridCells.Fixed(effectiveColumns),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
			modifier = Modifier
				.fillMaxSize()
				.testTag("neko_dense_gallery_grid"),
		) {
			itemsIndexed(
				items = filteredGalleryEntries,
				key = { _, item -> "neko_dense_${item.id}" },
			) { idx, entry ->
				val inLib = isEntryInLibrary(entry)
				val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title)
				val footerLabel = if (showRankFooter && !entry.inLibrary) {
					"No. ${idx + 1}"
				} else {
					"Ch. ${entry.chapterNumber}"
				}

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(0.68f)
						.clip(RoundedCornerShape(8.dp))
						.border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
						.combinedClickable(
							onClick = { onOpenDetails(entry) },
							onLongClick = {
								haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								onToggleBookmark(entry)
							},
						)
						.testTag("neko_dense_poster_$idx"),
				) {
					// 1. Full-bleed cover image
					MangaCoverArtwork(
						title = cleanTitle,
						coverUrl = entry.coverUrl,
						modifier = Modifier.fillMaxSize(),
					)

					// 2. Top-Left Badge: Compact "In Library" pill flush with top-left corner
					if (inLib) {
						Surface(
							color = Color(0xFF5CE1E6),
							shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
							modifier = Modifier.align(Alignment.TopStart),
						) {
							Text(
								text = "In Library",
								fontSize = 9.sp,
								fontWeight = FontWeight.Bold,
								color = Color(0xFF0F1419),
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
							)
						}
					}

					// 3. Bottom Scrim Overlay: Vertical gradient covering the bottom 65% of the card
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.fillMaxHeight(0.65f)
							.align(Alignment.BottomCenter)
							.background(
								Brush.verticalGradient(
									colors = listOf(
										Color.Transparent,
										Color.Black.copy(alpha = 0.88f),
									),
								),
							),
					)

					// 4. Bottom Text Block (padding(4.dp)): Title + Subtitle Footer
					Column(
						modifier = Modifier
							.align(Alignment.BottomStart)
							.fillMaxWidth()
							.padding(4.dp),
						verticalArrangement = Arrangement.spacedBy(1.dp),
					) {
						Text(
							text = cleanTitle,
							color = Color.White,
							fontSize = 10.sp,
							lineHeight = 11.sp,
							fontWeight = FontWeight.Bold,
							maxLines = 3,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = footerLabel,
							color = Color(0xFFB0B8C4),
							fontSize = 9.sp,
							lineHeight = 10.sp,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}

	val peekTarget = quickPeekEntry
	if (peekTarget != null) {
		NekoQuickPeekDetailsSheet(
			entry = peekTarget,
			inLibrary = isEntryInLibrary(peekTarget),
			onDismiss = { quickPeekEntry = null },
			onToggleLibrary = {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onToggleBookmark(peekTarget)
			},
			onOpenReader = {
				val target = peekTarget
				quickPeekEntry = null
				onOpenReader(target)
			},
			onOpenFullDetails = {
				val target = peekTarget
				quickPeekEntry = null
				onOpenDetails(target)
			},
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NekoQuickPeekDetailsSheet(
	entry: HybridFeedEntry,
	inLibrary: Boolean,
	onDismiss: () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenReader: () -> Unit,
	onOpenFullDetails: () -> Unit,
) {
	val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(entry.title)
	val cleanSource = UiSourceAndTitleFormatter.formatSourceName(entry.sourceName)
	val accentCyan = Color(0xFF5CE1E6)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		containerColor = Color(0xFF161922),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.navigationBarsPadding()
				.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(14.dp),
				verticalAlignment = Alignment.Top,
			) {
				MangaCoverArtwork(
					title = cleanTitle,
					coverUrl = entry.coverUrl,
					modifier = Modifier
						.width(88.dp)
						.aspectRatio(0.68f)
						.clip(RoundedCornerShape(12.dp)),
				)

				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Text(
						text = cleanTitle,
						style = MaterialTheme.typography.titleMedium,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = Color.White,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = "${entry.author} • $cleanSource",
						style = MaterialTheme.typography.bodySmall,
						color = accentCyan,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = "Latest: Ch. ${entry.chapterNumber} (${entry.totalChapters} total chapters)",
						style = MaterialTheme.typography.labelSmall,
						color = Color(0xFF9EA4B0),
					)

					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
						modifier = Modifier.padding(top = 4.dp),
					) {
						entry.genres.take(4).forEach { tag ->
							Surface(
								color = Color.White.copy(alpha = 0.08f),
								shape = RoundedCornerShape(6.dp),
							) {
								Text(
									text = tag,
									fontSize = 10.sp,
									color = Color(0xFFD5DAE5),
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
						}
					}
				}
			}

			Text(
				text = entry.synopsis,
				style = MaterialTheme.typography.bodySmall,
				color = Color(0xFFCBD5E1),
				maxLines = 4,
				overflow = TextOverflow.Ellipsis,
			)

			HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 16.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				FilledTonalButton(
					onClick = onToggleLibrary,
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
						contentDescription = null,
						modifier = Modifier.size(16.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(if (inLibrary) "In Library" else "Add Library", fontSize = 12.sp)
				}

				FilledTonalButton(
					onClick = onOpenFullDetails,
					modifier = Modifier.weight(1f),
				) {
					Text("Details", fontSize = 12.sp)
				}

				Button(
					onClick = onOpenReader,
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.MenuBook,
						contentDescription = null,
						modifier = Modifier.size(16.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text("Read Ch.${entry.chapterNumber}", fontSize = 12.sp)
				}
			}
		}
	}
}
