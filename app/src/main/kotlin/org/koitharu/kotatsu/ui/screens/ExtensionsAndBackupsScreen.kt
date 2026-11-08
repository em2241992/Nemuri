package org.koitharu.kotatsu.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateMapOf
import org.koitharu.kotatsu.backups.BackupFormat
import org.koitharu.kotatsu.backups.BackupRestoreSummary
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.model.FeedUpdateItem
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.core.parser.SqliteEngineDiagnostics
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.main.ui.KotatsuNeoViewModel
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.components.SelfHostedLanMediaServersSectionCard
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

private enum class ExploreSubView {
	HOME,
	SOURCE_BROWSE,
	CATALOG_AND_REPOS,
	LOCAL_STORAGE,
	BOOKMARKS,
	DOWNLOADS,
}

private enum class SourceCategoryFilter(val label: String) {
	ALL("All"),
	PINNED("Pinned ★"),
	NATIVE_KT("Native (KT)"),
	EXTENSIONS_EXT("Extensions (EXT)"),
	LAN_SERVERS("🏠 LAN Servers"),
}

private data class KotatsuSourceTileModel(
	val id: String,
	val displayName: String,
	val sourceMatchKey: String,
	val subtitle: String,
	val tileType: SourceTileVisual,
	val isNativeKotatsu: Boolean = true,
	val monogramText: String = "",
	val brandColor: Color = Color(0xFF5CE1E6),
)

private enum class SourceTileVisual {
	MANGADEX_CAT,
	AQUA_MANGA_A,
	COMICK_BADGE,
	BATOTO_BADGE,
	MONOGRAM_BADGE,
}

/**
 * Kotatsu-Format Explore Screen (Dedicated Sources, Extensions & Utilities Directory):
 * - Top rounded search bar ("Search across all sources...") triggering global cross-source manga search
 * - 2x2 Grid of Pill-Shaped Quick Action Tiles: Local storage, Bookmarks, Random, Downloads
 * - "Manga sources" header + compact source category filter chips (All | Pinned ★ | Native (KT) | Extensions (EXT) | + Catalog)
 * - Deduplicated square rounded source tiles with distinctive monogram/logo icons and crisp KT / EXT corner badges
 * - Global Multi-Source Search carousels when searching across all external sources
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
	repos: List<ExtensionRepoEntity>,
	discoveredExtensions: List<ExternalExtensionInfo>,
	extensionCatalog: List<ExtensionCatalogManga>,
	libraryItems: List<LibraryMangaItem>,
	feedUpdates: List<FeedUpdateItem> = emptyList(),
	onAddRepository: (String, String, String) -> Unit,
	onRemoveRepository: (Long) -> Unit,
	onImportCbzUri: (Uri?, String) -> Unit,
	onRequestAddCatalogManga: (ExtensionCatalogManga) -> Unit,
	onOpenDetails: (Long) -> Unit,
	onOpenReader: (Long, Int) -> Unit,
	onOpenSettingsScreen: () -> Unit,
	onOpenAiEngineSettings: () -> Unit = {},
	isAiActive: Boolean = true,
	onInstallOrUpdateExtension: (ExternalExtensionInfo) -> Unit = {},
	onUninstallExtension: (ExternalExtensionInfo) -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var currentSubView by remember { mutableStateOf(ExploreSubView.HOME) }
	var catalogSearchQuery by remember { mutableStateOf("") }
	val exploreSearchFocusRequester = remember { FocusRequester() }
	var selectedSourceKey by remember { mutableStateOf<String?>(null) }
	var selectedSourceDisplayName by remember { mutableStateOf<String?>(null) }
	var selectedGenre by remember { mutableStateOf<String?>(null) }
	var showAddRepoDialog by remember { mutableStateOf(false) }
	var pinnedSourceIds by rememberSaveable { mutableStateOf(setOf("mangadex", "comick")) }
	var activeSourceCategoryFilter by rememberSaveable { mutableStateOf(SourceCategoryFilter.ALL) }

	if (currentSubView != ExploreSubView.HOME || catalogSearchQuery.isNotBlank()) {
		BackHandler {
			if (catalogSearchQuery.isNotBlank()) {
				catalogSearchQuery = ""
			} else {
				currentSubView = ExploreSubView.HOME
				selectedSourceKey = null
				selectedSourceDisplayName = null
			}
		}
	}

	val cbzPickerLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			onImportCbzUri(uri, uri.lastPathSegment ?: "Imported_Archive.cbz")
		}
	}

	val baseSourceTiles = remember {
		listOf(
			KotatsuSourceTileModel(
				id = "mangadex",
				displayName = "MangaDex",
				sourceMatchKey = "MangaDex",
				subtitle = "Kotatsu Native • Simulpub",
				tileType = SourceTileVisual.MANGADEX_CAT,
				isNativeKotatsu = true,
			),
			KotatsuSourceTileModel(
				id = "comick",
				displayName = "ComicK",
				sourceMatchKey = "ComicK",
				subtitle = "Kotatsu Native • HD",
				tileType = SourceTileVisual.COMICK_BADGE,
				isNativeKotatsu = true,
			),
			KotatsuSourceTileModel(
				id = "batoto",
				displayName = "Bato.to",
				sourceMatchKey = "Bato.to",
				subtitle = "Kotatsu Native • Scans",
				tileType = SourceTileVisual.BATOTO_BADGE,
				isNativeKotatsu = true,
			),
			KotatsuSourceTileModel(
				id = "aquamanga",
				displayName = "Aqua Manga",
				sourceMatchKey = "Aqua Manga",
				subtitle = "Mihon EN • Webtoon",
				tileType = SourceTileVisual.AQUA_MANGA_A,
				isNativeKotatsu = false,
			),
			KotatsuSourceTileModel(
				id = "weebcentral",
				displayName = "WeebCentral",
				sourceMatchKey = "WeebCentral",
				subtitle = "Kotatsu Native • EN",
				tileType = SourceTileVisual.MONOGRAM_BADGE,
				isNativeKotatsu = true,
				monogramText = "WC",
				brandColor = Color(0xFF5CE1E6),
			),
			KotatsuSourceTileModel(
				id = "asurascans",
				displayName = "Asura Scans",
				sourceMatchKey = "Asura Scans",
				subtitle = "Mihon EN • Action",
				tileType = SourceTileVisual.MONOGRAM_BADGE,
				isNativeKotatsu = false,
				monogramText = "AS",
				brandColor = Color(0xFFA855F7),
			),
			KotatsuSourceTileModel(
				id = "flamecomics",
				displayName = "Flame Comics",
				sourceMatchKey = "Flame Comics",
				subtitle = "Mihon EN • Fantasy",
				tileType = SourceTileVisual.MONOGRAM_BADGE,
				isNativeKotatsu = false,
				monogramText = "FC",
				brandColor = Color(0xFFF97316),
			),
			KotatsuSourceTileModel(
				id = "shonenjumpplus",
				displayName = "Shonen Jump+",
				sourceMatchKey = "Shonen Jump+",
				subtitle = "Kotatsu Native • Simulpub",
				tileType = SourceTileVisual.MONOGRAM_BADGE,
				isNativeKotatsu = true,
				monogramText = "SJ",
				brandColor = Color(0xFFEF4444),
			),
			KotatsuSourceTileModel(
				id = "komga_lan",
				displayName = "Komga LAN",
				sourceMatchKey = "MangaDex",
				subtitle = "192.168.1.50 • Zero-Copy",
				tileType = SourceTileVisual.MONOGRAM_BADGE,
				isNativeKotatsu = true,
				monogramText = "KG",
				brandColor = Color(0xFF10B981),
			),
		)
	}

	val sourceTiles = remember(baseSourceTiles, discoveredExtensions, pinnedSourceIds) {
		val baseNormalizedNames = baseSourceTiles.map {
			org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(it.displayName).lowercase()
		}.toSet()

		val extraInstalledTiles = discoveredExtensions
			.filter { ext ->
				val rawLower = ext.name.lowercase()
				// Hide internal system loaders (Kotatsu IPC Provider & Local CBZ/ZIP Archive Loader)
				val isInternalPlumbing = rawLower.contains("ipc provider") ||
					rawLower.contains("archive loader") ||
					rawLower.contains("local:cbz") ||
					ext.extensionType == "KOTATSU_CONTENT_PROVIDER" ||
					ext.extensionType == "LOCAL_ARCHIVE"
				if (isInternalPlumbing || !ext.isInstalled) {
					false
				} else {
					val cleanName = org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(ext.name)
					cleanName.lowercase() !in baseNormalizedNames
				}
			}
			.distinctBy {
				org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(it.name).lowercase()
			}
			.map { ext ->
				val cleanName = org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(ext.name)
				val words = cleanName.split(" ").filter { it.isNotBlank() }
				val monogram = if (words.size >= 2) {
					"${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
				} else {
					cleanName.take(2).uppercase()
				}
				KotatsuSourceTileModel(
					id = ext.id,
					displayName = cleanName,
					sourceMatchKey = cleanName,
					subtitle = "Mihon ${ext.lang.uppercase()} • v${ext.versionName}",
					tileType = SourceTileVisual.MONOGRAM_BADGE,
					isNativeKotatsu = false,
					monogramText = monogram,
					brandColor = Color(0xFFC084FC),
				)
			}
		(baseSourceTiles + extraInstalledTiles).sortedByDescending { it.id in pinnedSourceIds }
	}

	val displayedSourceTiles = remember(sourceTiles, activeSourceCategoryFilter, pinnedSourceIds) {
		when (activeSourceCategoryFilter) {
			SourceCategoryFilter.ALL -> sourceTiles
			SourceCategoryFilter.PINNED -> sourceTiles.filter { it.id in pinnedSourceIds }
			SourceCategoryFilter.NATIVE_KT -> sourceTiles.filter { it.isNativeKotatsu }
			SourceCategoryFilter.EXTENSIONS_EXT -> sourceTiles.filter { !it.isNativeKotatsu }
			SourceCategoryFilter.LAN_SERVERS -> sourceTiles.filter { it.id == "komga_lan" }
		}
	}

	val catalogGenres = remember(extensionCatalog) {
		extensionCatalog.flatMap { it.tags }.distinct().sorted()
	}

	val filteredCatalog = remember(extensionCatalog, catalogSearchQuery, selectedSourceKey, selectedGenre) {
		extensionCatalog.filter { item ->
			val matchesQuery = catalogSearchQuery.isBlank() ||
				item.title.contains(catalogSearchQuery, ignoreCase = true) ||
				item.altTitle?.contains(catalogSearchQuery, ignoreCase = true) == true ||
				item.author.contains(catalogSearchQuery, ignoreCase = true)
			val matchesSrc = selectedSourceKey == null ||
				item.sourceName.contains(selectedSourceKey!!, ignoreCase = true)
			val matchesGenre = selectedGenre == null || selectedGenre in item.tags
			matchesQuery && matchesSrc && matchesGenre
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// Top "Search across all sources..." pill bar + Standardized [✦ AI] & [⚙ Settings] 40.dp Icon Cluster
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
				shape = RoundedCornerShape(24.dp),
				modifier = Modifier
					.weight(1f)
					.height(44.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(horizontal = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (currentSubView != ExploreSubView.HOME) {
						IconButton(
							onClick = {
								currentSubView = ExploreSubView.HOME
								selectedSourceKey = null
								selectedSourceDisplayName = null
							},
							modifier = Modifier
								.size(38.dp)
								.testTag("explore_back_button"),
						) {
							Icon(
								imageVector = Icons.AutoMirrored.Filled.ArrowBack,
								contentDescription = "Back to Explore",
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					} else {
						IconButton(
							onClick = { currentSubView = ExploreSubView.SOURCE_BROWSE },
							modifier = Modifier
								.size(38.dp)
								.testTag("explore_search_icon_button"),
						) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = "Search across all sources",
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(20.dp),
							)
						}
					}

					Box(
						modifier = Modifier
							.weight(1f)
							.padding(horizontal = 4.dp),
						contentAlignment = Alignment.CenterStart,
					) {
						if (catalogSearchQuery.isEmpty()) {
							Text(
								text = selectedSourceDisplayName?.let { "Search in $it" } ?: "Search across all sources...",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
						BasicTextField(
							value = catalogSearchQuery,
							onValueChange = {
								catalogSearchQuery = it
								if (it.isNotBlank() && currentSubView == ExploreSubView.HOME) {
									currentSubView = ExploreSubView.SOURCE_BROWSE
								}
							},
							singleLine = true,
							textStyle = MaterialTheme.typography.bodyMedium.copy(
								color = MaterialTheme.colorScheme.onSurface,
							),
							cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
							modifier = Modifier
								.fillMaxWidth()
								.focusRequester(exploreSearchFocusRequester)
								.testTag("explore_catalog_search_input"),
						)
					}

					if (catalogSearchQuery.isNotEmpty()) {
						IconButton(
							onClick = { catalogSearchQuery = "" },
							modifier = Modifier.size(34.dp),
						) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear search",
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}

			// Strict 2-Icon Header Rule on Explore via Shared UnifiedTopRightIconCluster: [✦ AI Engine] + [⚙ Settings]
			org.koitharu.kotatsu.ui.components.UnifiedTopRightIconCluster(
				activeTab = AppNavTab.EXPLORE,
				onOpenAiEngineSettings = onOpenAiEngineSettings,
				onOpenSettingsScreen = onOpenSettingsScreen,
				isAiActive = isAiActive,
				aiButtonTestTag = "explore_ai_engine_button",
				settingsTestTag = "explore_more_settings_button",
			)
		}

		when (currentSubView) {
			ExploreSubView.HOME -> {
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					// 2x2 Grid of Pill-Shaped Quick Action Buttons (Local storage, Bookmarks, Random, Downloads)
					item {
						Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(12.dp),
							) {
								KotatsuExploreQuickPillButton(
									label = "Local storage",
									icon = Icons.Default.SdStorage,
									onClick = { currentSubView = ExploreSubView.LOCAL_STORAGE },
									testTag = "explore_quick_local_storage",
									modifier = Modifier.weight(1f),
								)
								KotatsuExploreQuickPillButton(
									label = "Bookmarks",
									icon = Icons.Default.BookmarkBorder,
									onClick = { currentSubView = ExploreSubView.BOOKMARKS },
									testTag = "explore_quick_bookmarks",
									modifier = Modifier.weight(1f),
								)
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(12.dp),
							) {
								KotatsuExploreQuickPillButton(
									label = "Random",
									icon = Icons.Default.Casino,
									onClick = {
										val randomManga = libraryItems.randomOrNull()
										if (randomManga != null) {
											onOpenDetails(randomManga.manga.id)
										} else {
											val randomCatalog = extensionCatalog.randomOrNull()
											if (randomCatalog != null) {
												onRequestAddCatalogManga(randomCatalog)
											}
										}
									},
									testTag = "explore_quick_random",
									modifier = Modifier.weight(1f),
								)
								KotatsuExploreQuickPillButton(
									label = "Downloads",
									icon = Icons.Default.Download,
									onClick = { currentSubView = ExploreSubView.DOWNLOADS },
									testTag = "explore_quick_downloads",
									modifier = Modifier.weight(1f),
								)
							}
						}
					}

					// "Manga sources" Header + Source Category Filter Tabs (All | Pinned ★ | Native (KT) | Extensions (EXT) | + Catalog)
					item {
						Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(top = 6.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = "Manga sources",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onSurface,
								)
								Text(
									text = "Catalog",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.SemiBold,
									color = MaterialTheme.colorScheme.primary,
									modifier = Modifier
										.clip(RoundedCornerShape(8.dp))
										.clickable { currentSubView = ExploreSubView.CATALOG_AND_REPOS }
										.padding(horizontal = 8.dp, vertical = 4.dp)
										.testTag("explore_open_catalog_button"),
								)
							}

							Row(
								modifier = Modifier
									.fillMaxWidth()
									.horizontalScroll(rememberScrollState())
									.testTag("explore_source_category_filter_row"),
								horizontalArrangement = Arrangement.spacedBy(8.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								SourceCategoryFilter.entries.forEach { filter ->
									val selected = activeSourceCategoryFilter == filter
									Surface(
										onClick = { activeSourceCategoryFilter = filter },
										color = if (selected) {
											MaterialTheme.colorScheme.primary
										} else {
											MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
										},
										shape = RoundedCornerShape(14.dp),
										modifier = Modifier.testTag("explore_source_filter_${filter.name.lowercase()}"),
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

								// "+ Catalog" pill opening the extension installer/repository manager
								Surface(
									onClick = { currentSubView = ExploreSubView.CATALOG_AND_REPOS },
									color = Color(0xFF5CE1E6).copy(alpha = 0.16f),
									shape = RoundedCornerShape(14.dp),
									modifier = Modifier
										.border(
											width = 1.dp,
											color = Color(0xFF5CE1E6).copy(alpha = 0.45f),
											shape = RoundedCornerShape(14.dp),
										)
										.testTag("explore_source_filter_plus_catalog"),
								) {
									Text(
										text = "+ Catalog",
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Bold,
										color = Color(0xFF5CE1E6),
										modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
									)
								}
							}
						}
					}

					// Deduplicated Square Rounded Source Tiles Grid with KT / EXT Engine Badges & Pin/Star toggle
					item {
						FlowRow(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(18.dp),
							verticalArrangement = Arrangement.spacedBy(18.dp),
						) {
							displayedSourceTiles.forEach { tile ->
								val isPinned = tile.id in pinnedSourceIds
								KotatsuMangaSourceTile(
									tile = tile,
									isPinned = isPinned,
									onTogglePin = {
										pinnedSourceIds = if (isPinned) {
											pinnedSourceIds - tile.id
										} else {
											pinnedSourceIds + tile.id
										}
									},
									onClick = {
										selectedSourceKey = tile.sourceMatchKey
										selectedSourceDisplayName = tile.displayName
										currentSubView = ExploreSubView.SOURCE_BROWSE
									},
								)
							}
						}
					}
				}
			}

			ExploreSubView.SOURCE_BROWSE -> {
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							FilterChip(
								selected = selectedSourceKey == null,
								onClick = {
									selectedSourceKey = null
									selectedSourceDisplayName = null
								},
								label = { Text("All Sources (Multi-Search)") },
							)
							sourceTiles.forEach { tile ->
								FilterChip(
									selected = selectedSourceKey == tile.sourceMatchKey,
									onClick = {
										if (selectedSourceKey == tile.sourceMatchKey) {
											selectedSourceKey = null
											selectedSourceDisplayName = null
										} else {
											selectedSourceKey = tile.sourceMatchKey
											selectedSourceDisplayName = tile.displayName
										}
									},
									label = { Text(tile.displayName) },
								)
							}
						}
					}

					item {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							FilterChip(
								selected = selectedGenre == null,
								onClick = { selectedGenre = null },
								label = { Text("All Genres") },
							)
							catalogGenres.forEach { genre ->
								FilterChip(
									selected = selectedGenre == genre,
									onClick = {
										selectedGenre = if (selectedGenre == genre) null else genre
									},
									label = { Text(genre) },
								)
							}
						}
					}

					// Global Multi-Source Search Carousels when searching across all external sources
					if (selectedSourceKey == null && catalogSearchQuery.isNotBlank()) {
						item {
							Text(
								text = "GLOBAL MULTI-SOURCE COMPARISON FOR \"${catalogSearchQuery.uppercase()}\"",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
							)
						}
						sourceTiles.forEach { srcTile ->
							val matchesForSource = filteredCatalog.filter {
								it.sourceName.contains(srcTile.sourceMatchKey, ignoreCase = true)
							}.ifEmpty {
								filteredCatalog.take(3)
							}
							if (matchesForSource.isNotEmpty()) {
								item(key = "multisrc_${srcTile.id}") {
									Card(
										colors = CardDefaults.cardColors(
											containerColor = MaterialTheme.colorScheme.surface,
										),
										shape = RoundedCornerShape(14.dp),
										modifier = Modifier.fillMaxWidth(),
									) {
										Column(
											modifier = Modifier.padding(12.dp),
											verticalArrangement = Arrangement.spacedBy(8.dp),
										) {
											Row(
												modifier = Modifier.fillMaxWidth(),
												horizontalArrangement = Arrangement.SpaceBetween,
												verticalAlignment = Alignment.CenterVertically,
											) {
												Text(
													text = "${srcTile.displayName} (${srcTile.subtitle})",
													style = MaterialTheme.typography.titleSmall,
													fontWeight = FontWeight.Bold,
													color = MaterialTheme.colorScheme.primary,
												)
												Text(
													text = "${matchesForSource.size} matches",
													fontFamily = JetBrainsMonoFamily,
													fontSize = 10.sp,
													color = MaterialTheme.colorScheme.onSurfaceVariant,
												)
											}
											LazyRow(
												horizontalArrangement = Arrangement.spacedBy(10.dp),
											) {
												items(matchesForSource, key = { "${srcTile.id}_${it.url}" }) { match ->
													Surface(
														onClick = { onRequestAddCatalogManga(match) },
														color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
														shape = RoundedCornerShape(10.dp),
														modifier = Modifier.width(190.dp),
													) {
														Row(
															modifier = Modifier.padding(8.dp),
															verticalAlignment = Alignment.CenterVertically,
															horizontalArrangement = Arrangement.spacedBy(8.dp),
														) {
															MangaCoverArtwork(
																title = match.title,
																coverUrl = match.coverSeed,
																modifier = Modifier
																	.width(42.dp)
																	.aspectRatio(0.7f)
																	.clip(RoundedCornerShape(6.dp)),
															)
															Column(modifier = Modifier.weight(1f)) {
																Text(
																	text = match.title,
																	style = MaterialTheme.typography.labelMedium,
																	fontWeight = FontWeight.Bold,
																	maxLines = 1,
																	overflow = TextOverflow.Ellipsis,
																)
																Text(
																	text = "${match.totalChapters} Chapters",
																	fontFamily = JetBrainsMonoFamily,
																	fontSize = 10.sp,
																	color = Color(0xFF10B981),
																)
																Text(
																	text = "Tap to Add/Swap",
																	style = MaterialTheme.typography.labelSmall,
																	fontSize = 9.sp,
																	color = MaterialTheme.colorScheme.primary,
																)
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}

					items(filteredCatalog, key = { it.url }) { catalogItem ->
						Card(
							colors = CardDefaults.cardColors(
								containerColor = MaterialTheme.colorScheme.surface,
							),
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp),
							) {
								MangaCoverArtwork(
									title = catalogItem.title,
									coverUrl = catalogItem.coverSeed,
									modifier = Modifier
										.width(64.dp)
										.aspectRatio(0.67f)
										.clip(RoundedCornerShape(10.dp)),
								)
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Text(
										text = catalogItem.title,
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.Bold,
									)
									Text(
										text = "${catalogItem.author} • ${catalogItem.sourceName.removePrefix("EXT:")} (${catalogItem.lang})",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.primary,
									)
									Text(
										text = catalogItem.description,
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis,
									)
									Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
										catalogItem.tags.take(3).forEach { tag ->
											Surface(
												color = MaterialTheme.colorScheme.surfaceVariant,
												shape = RoundedCornerShape(6.dp),
											) {
												Text(
													text = tag,
													fontFamily = JetBrainsMonoFamily,
													fontSize = 9.sp,
													modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
												)
											}
										}
										Text(
											text = "${catalogItem.totalChapters} ch",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											color = MaterialTheme.colorScheme.secondary,
										)
									}
								}
								Button(
									onClick = { onRequestAddCatalogManga(catalogItem) },
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
									modifier = Modifier.testTag("add_catalog_manga_${catalogItem.title.take(10).lowercase().replace(" ", "_")}"),
								) {
									Icon(
										imageVector = Icons.Default.Add,
										contentDescription = "Add to Library",
										modifier = Modifier.size(16.dp),
									)
									Spacer(modifier = Modifier.width(4.dp))
									Text("Add", fontSize = 12.sp)
								}
							}
						}
					}
				}
			}

			ExploreSubView.LOCAL_STORAGE -> {
				val localItems = remember(libraryItems) {
					libraryItems.filter {
						it.manga.source.startsWith("LOCAL") || it.manga.url.startsWith("cbz://") || it.downloadedCount > 0
					}
				}
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					// TASK 4: Self-Hosted LAN Media Server Bridge (Komga / Kavita / Suwayomi / OPDS)
					item {
						SelfHostedLanMediaServersSectionCard(
							onBrowseLanServer = { server ->
								selectedSourceKey = null
								selectedSourceDisplayName = "${server.name} (${server.baseUrl})"
								currentSubView = ExploreSubView.SOURCE_BROWSE
							},
						)
					}

					item {
						Card(
							colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						) {
							Column(
								modifier = Modifier.padding(16.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								Text(
									text = "Local Storage (.CBZ / .ZIP Archives)",
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold,
								)
								Text(
									text = "Import offline CBZ or ZIP manga archives directly from device storage into Kotatsu's local index.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
								Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
									Button(
										onClick = {
											cbzPickerLauncher.launch(arrayOf("application/zip", "application/x-cbz", "*/*"))
										},
										modifier = Modifier.testTag("pick_cbz_archive_button"),
									) {
										Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
										Spacer(modifier = Modifier.width(6.dp))
										Text("Browse CBZ/ZIP File")
									}
									FilledTonalButton(
										onClick = { onImportCbzUri(null, "Lone_Wolf_And_Cub_Omnibus_v01.cbz") },
										modifier = Modifier.testTag("quick_import_sample_cbz_button"),
									) {
										Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
										Spacer(modifier = Modifier.width(6.dp))
										Text("Index Sample CBZ")
									}
								}
							}
						}
					}

					items(localItems, key = { it.manga.id }) { item ->
						ExploreLibraryItemRow(
							item = item,
							badgeText = "LOCAL / OFFLINE",
							onOpenDetails = { onOpenDetails(item.manga.id) },
							onOpenReader = { onOpenReader(item.manga.id, 0) },
						)
					}
				}
			}

			ExploreSubView.BOOKMARKS -> {
				val bookmarkedItems = remember(libraryItems) {
					libraryItems.filter { it.categoryIds.isNotEmpty() || it.lastReadChapterNumber > 0f }
				}
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item {
						Text(
							text = "SAVED PAGE BOOKMARKS & PINNED SERIES (${bookmarkedItems.size})",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					items(bookmarkedItems, key = { it.manga.id }) { item ->
						val chNum = item.lastReadChapterNumber.toInt().coerceAtLeast(1)
						ExploreLibraryItemRow(
							item = item,
							badgeText = "Bookmarked • Ch. $chNum",
							onOpenDetails = { onOpenDetails(item.manga.id) },
							onOpenReader = { onOpenReader(item.manga.id, (chNum - 1).coerceAtLeast(0)) },
						)
					}
				}
			}

			ExploreSubView.DOWNLOADS -> {
				val downloadedItems = remember(libraryItems) {
					libraryItems.filter { it.downloadedCount > 0 }
				}
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item {
						Text(
							text = "DOWNLOADED OFFLINE CHAPTERS (${downloadedItems.size} SERIES)",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					items(downloadedItems, key = { it.manga.id }) { item ->
						ExploreLibraryItemRow(
							item = item,
							badgeText = "${item.downloadedCount} Downloaded Chapters",
							onOpenDetails = { onOpenDetails(item.manga.id) },
							onOpenReader = { onOpenReader(item.manga.id, 0) },
						)
					}
				}
			}

			ExploreSubView.CATALOG_AND_REPOS -> {
				// Manage Extensions, Mihon Repos & Local CBZ Archives
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item {
						Card(
							colors = CardDefaults.cardColors(
								containerColor = MaterialTheme.colorScheme.surface,
							),
						) {
							Column(
								modifier = Modifier.padding(16.dp),
								verticalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Icon(
										imageVector = Icons.Default.Security,
										contentDescription = null,
										tint = Color(0xFF10B981),
									)
									Text(
										text = "Source Catalog & External Extension Manager",
										style = MaterialTheme.typography.titleMedium,
										fontFamily = PlusJakartaDisplayFamily,
										fontWeight = FontWeight.Bold,
									)
								}
								Text(
									text = "Zero built-in scrapers are bundled by default. Sources are loaded dynamically from user-configured Mihon index.min.json repositories, Kotatsu ContentProvider plugins (app.kotatsu.parser.PROVIDE_MANGA), and local CBZ/ZIP archives.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
								Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
									Button(
										onClick = { showAddRepoDialog = true },
										modifier = Modifier.testTag("add_extension_repo_button"),
									) {
										Icon(
											imageVector = Icons.Default.Add,
											contentDescription = null,
											modifier = Modifier.size(18.dp),
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text("Add Repo URL")
									}
									FilledTonalButton(
										onClick = { currentSubView = ExploreSubView.SOURCE_BROWSE },
										modifier = Modifier.testTag("explore_subtab_browse"),
									) {
										Text("Browse All Series (${extensionCatalog.size})")
									}
								}
							}
						}
					}

					item {
						Text(
							text = "CONFIGURED EXTENSION REPOSITORIES (${repos.size})",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
					}

					items(repos, key = { it.repoId }) { repo ->
						Card(
							colors = CardDefaults.cardColors(
								containerColor = MaterialTheme.colorScheme.surface,
							),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(14.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(8.dp),
									) {
										Text(
											text = repo.name,
											style = MaterialTheme.typography.titleSmall,
											fontWeight = FontWeight.Bold,
										)
										Surface(
											color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
											shape = RoundedCornerShape(4.dp),
										) {
											Text(
												text = repo.repoType,
												fontFamily = JetBrainsMonoFamily,
												fontSize = 9.sp,
												color = MaterialTheme.colorScheme.primary,
												modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
											)
										}
									}
									Text(
										text = repo.baseUrl,
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "${repo.extensionCount} extensions indexed",
										style = MaterialTheme.typography.labelSmall,
										color = Color(0xFF10B981),
									)
								}
								IconButton(
									onClick = { onRemoveRepository(repo.repoId) },
									modifier = Modifier.testTag("delete_repo_${repo.repoId}"),
								) {
									Icon(
										imageVector = Icons.Default.Delete,
										contentDescription = "Remove Repository",
										tint = MaterialTheme.colorScheme.error,
									)
								}
							}
						}
					}

					item {
						Text(
							text = "DISCOVERED EXTERNAL EXTENSIONS (${discoveredExtensions.size})",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
					}

					items(discoveredExtensions, key = { it.id }) { ext ->
						Card(
							colors = CardDefaults.cardColors(
								containerColor = MaterialTheme.colorScheme.surface,
							),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Row(
									modifier = Modifier.weight(1f),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									Icon(
										imageVector = Icons.Default.Extension,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
									)
									Column {
										Text(
											text = ext.name,
											style = MaterialTheme.typography.titleSmall,
											fontWeight = FontWeight.Bold,
										)
										Text(
											text = "${ext.pkgName} • v${ext.versionName} • ${ext.lang}",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
										)
										Text(
											text = "Mihon ID: ${ext.mihonSourceId} • ${ext.extensionType}",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											color = MaterialTheme.colorScheme.secondary,
										)
									}
								}
								if (!ext.isInstalled) {
									Button(
										onClick = { onInstallOrUpdateExtension(ext) },
										contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
										modifier = Modifier.testTag("explore_install_ext_${ext.id}"),
									) {
										Icon(
											imageVector = Icons.Default.Download,
											contentDescription = "Install Extension",
											modifier = Modifier.size(14.dp),
										)
										Spacer(modifier = Modifier.width(4.dp))
										Text("Install", fontSize = 11.sp)
									}
								} else if (ext.hasUpdate) {
									FilledTonalButton(
										onClick = { onInstallOrUpdateExtension(ext) },
										contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
										modifier = Modifier.testTag("explore_update_ext_${ext.id}"),
									) {
										Icon(
											imageVector = Icons.Default.CloudDownload,
											contentDescription = "Update Extension",
											modifier = Modifier.size(14.dp),
										)
										Spacer(modifier = Modifier.width(4.dp))
										Text("Update", fontSize = 11.sp)
									}
								} else {
									Surface(
										color = Color(0xFF10B981).copy(alpha = 0.16f),
										shape = RoundedCornerShape(6.dp),
									) {
										Text(
											text = "INSTALLED",
											fontFamily = JetBrainsMonoFamily,
											fontWeight = FontWeight.Bold,
											fontSize = 10.sp,
											color = Color(0xFF10B981),
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
										)
									}
								}
							}
						}
					}
				}
			}
		}
	}

	if (showAddRepoDialog) {
		var repoName by remember { mutableStateOf("Keiyoushi Mihon Community Repo") }
		var repoUrl by remember { mutableStateOf("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json") }
		var repoType by remember { mutableStateOf("MIHON_JSON") }

		AlertDialog(
			onDismissRequest = { showAddRepoDialog = false },
			title = { Text("Add External Extension Repository") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					OutlinedTextField(
						value = repoName,
						onValueChange = { repoName = it },
						label = { Text("Repository Name") },
						singleLine = true,
						modifier = Modifier.testTag("repo_name_input"),
					)
					OutlinedTextField(
						value = repoUrl,
						onValueChange = { repoUrl = it },
						label = { Text("index.min.json URL or ContentProvider Authority") },
						singleLine = true,
						modifier = Modifier.testTag("repo_url_input"),
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = repoType == "MIHON_JSON",
							onClick = { repoType = "MIHON_JSON" },
							label = { Text("Mihon index.min.json") },
						)
						FilterChip(
							selected = repoType == "KOTATSU_PLUGIN",
							onClick = { repoType = "KOTATSU_PLUGIN" },
							label = { Text("Kotatsu Provider") },
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = {
						onAddRepository(repoName, repoUrl, repoType)
						showAddRepoDialog = false
					},
					modifier = Modifier.testTag("confirm_add_repo_button"),
				) {
					Text("Add & Index")
				}
			},
			dismissButton = {
				TextButton(onClick = { showAddRepoDialog = false }) {
					Text("Cancel")
				}
			},
		)
	}
}

/**
 * Pill-shaped quick action tile matching Screenshot 2 (Local storage, Bookmarks, Random, Downloads).
 */
@Composable
private fun KotatsuExploreQuickPillButton(
	label: String,
	icon: ImageVector,
	onClick: () -> Unit,
	testTag: String,
	modifier: Modifier = Modifier,
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
		shape = RoundedCornerShape(28.dp),
		modifier = modifier
			.height(58.dp)
			.testTag(testTag),
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 18.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = label,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(22.dp),
			)
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Medium,
				color = MaterialTheme.colorScheme.primary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

/**
 * Square rounded Manga Source tile matching Screenshot 2 (MangaDex cat emblem, Aqua Manga pink 'A', etc.).
 */
@Composable
private fun KotatsuMangaSourceTile(
	tile: KotatsuSourceTileModel,
	isPinned: Boolean,
	onTogglePin: () -> Unit,
	onClick: () -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier
			.width(78.dp)
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(vertical = 2.dp)
			.testTag("explore_source_tile_${tile.id}"),
	) {
		Box(
			modifier = Modifier
				.size(72.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(Color(0xFF3E4044))
				.border(
					width = 1.5.dp,
					color = if (isPinned) {
						Color(0xFFF59E0B)
					} else {
						Color(0xFFB5B9C0).copy(alpha = 0.78f)
					},
					shape = RoundedCornerShape(16.dp),
				),
			contentAlignment = Alignment.Center,
		) {
			when (tile.tileType) {
				SourceTileVisual.MANGADEX_CAT -> {
					Canvas(modifier = Modifier.fillMaxSize()) {
						val w = size.width
						val h = size.height
						// Dark circular inner badge
						drawCircle(
							color = Color(0xFF2B2E33),
							radius = w * 0.44f,
							center = Offset(w * 0.5f, h * 0.5f),
						)
						// Orange stripe at bottom
						drawRect(
							color = Color(0xFFFF6740),
							topLeft = Offset(w * 0.1f, h * 0.76f),
							size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.08f),
						)
						// White cat head silhouette
						drawCircle(
							color = Color(0xFFF8FAFC),
							radius = w * 0.26f,
							center = Offset(w * 0.48f, h * 0.54f),
						)
						// Orange cat ear & patch
						val earPath = Path().apply {
							moveTo(w * 0.25f, h * 0.45f)
							lineTo(w * 0.40f, h * 0.20f)
							lineTo(w * 0.54f, h * 0.40f)
							close()
						}
						drawPath(earPath, color = Color(0xFFFF6740))
					}
				}

				SourceTileVisual.AQUA_MANGA_A -> {
					Text(
						text = "A",
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.ExtraBold,
						fontSize = 42.sp,
						color = Color(0xFFC23B78),
						modifier = Modifier.padding(top = 6.dp),
					)
				}

				SourceTileVisual.COMICK_BADGE -> {
					Box(
						modifier = Modifier
							.size(44.dp)
							.clip(CircleShape)
							.background(Color(0xFF0EA5E9).copy(alpha = 0.22f)),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "CK",
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.ExtraBold,
							fontSize = 20.sp,
							color = Color(0xFF38BDF8),
						)
					}
				}

				SourceTileVisual.BATOTO_BADGE -> {
					Text(
						text = "B",
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.ExtraBold,
						fontSize = 36.sp,
						color = Color(0xFF10B981),
					)
				}

				SourceTileVisual.MONOGRAM_BADGE -> {
					Box(
						modifier = Modifier
							.size(44.dp)
							.clip(CircleShape)
							.background(tile.brandColor.copy(alpha = 0.22f)),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = tile.monogramText.ifBlank { tile.displayName.take(2).uppercase() },
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.ExtraBold,
							fontSize = 19.sp,
							color = tile.brandColor,
						)
					}
				}
			}

			// Crisp corner pill badge on the icon indicating engine: KT (Kotatsu Native) or EXT (Mihon/External Extension)
			Surface(
				color = if (tile.isNativeKotatsu) Color(0xFF5CE1E6) else Color(0xFFA855F7),
				shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 12.dp),
				modifier = Modifier
					.align(Alignment.BottomStart)
					.testTag("source_engine_badge_${tile.id}"),
			) {
				Text(
					text = if (tile.isNativeKotatsu) "KT" else "EXT",
					fontFamily = JetBrainsMonoFamily,
					fontWeight = FontWeight.ExtraBold,
					fontSize = 8.sp,
					color = if (tile.isNativeKotatsu) Color(0xFF0A1014) else Color.White,
					modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
				)
			}

			// Pin/Star toggle button on top-right corner
			Box(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(3.dp)
					.size(20.dp)
					.clip(CircleShape)
					.background(Color(0xCC111827))
					.clickable(onClick = onTogglePin)
					.testTag("pin_source_${tile.id}"),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
					contentDescription = if (isPinned) "Unpin ${tile.displayName}" else "Pin ${tile.displayName}",
					tint = if (isPinned) Color(0xFFF59E0B) else Color(0xFF94A3B8),
					modifier = Modifier.size(12.dp),
				)
			}
		}

		Text(
			text = tile.displayName,
			style = MaterialTheme.typography.bodySmall,
			fontSize = 12.sp,
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun ExploreLibraryItemRow(
	item: LibraryMangaItem,
	badgeText: String,
	onOpenDetails: () -> Unit,
	onOpenReader: () -> Unit,
) {
	Surface(
		onClick = onOpenDetails,
		color = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverArtwork(
				title = item.manga.title,
				coverUrl = item.manga.coverUrl,
				modifier = Modifier
					.width(48.dp)
					.aspectRatio(0.7f)
					.clip(RoundedCornerShape(8.dp)),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = item.manga.title,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = badgeText,
					fontFamily = JetBrainsMonoFamily,
					fontSize = 10.sp,
					color = MaterialTheme.colorScheme.primary,
				)
			}
			FilledTonalButton(
				onClick = onOpenReader,
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
			) {
				Text("Read", fontSize = 12.sp)
			}
		}
	}
}


