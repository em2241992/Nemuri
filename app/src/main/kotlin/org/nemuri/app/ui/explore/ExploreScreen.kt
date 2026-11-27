package org.nemuri.app.ui.explore

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.favicon.faviconUri
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.HeaderSquareAction
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriSuggestionsOptInCard
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.yorhaTacticalBrackets

@Composable
fun ExploreScreen(
	onOpenExtensions: () -> Unit,
	onOpenLocalFiles: () -> Unit,
	onOpenBookmarks: () -> Unit,
	onOpenDownloads: () -> Unit,
	onOpenSourceBrowse: (String) -> Unit,
	onOpenGlobalSearch: (String) -> Unit,
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: ExploreViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val bottomPadding = rememberRootTabBottomPadding()
	val isWideScreen = LocalConfiguration.current.screenWidthDp >= 700
	val gridColumns = if (isWideScreen) {
		(uiState.sourcesGridColumns + 2).coerceIn(5, 8)
	} else {
		uiState.sourcesGridColumns.coerceIn(3, 5)
	}

	var showSortAndLanguageSheet by remember { mutableStateOf(false) }
	var actionTargetTile by remember { mutableStateOf<ExploreSourceTile?>(null) }
	var languagePickerTile by remember { mutableStateOf<ExploreSourceTile?>(null) }

	Column(modifier = Modifier.fillMaxSize()) {
		// Top Search Header with consolidated Tune (Display / Filter) and Settings icons
		ExploreTopBar(
			query = uiState.sourceQuery,
			onQueryChange = viewModel::setSourceQuery,
			onSearchSubmit = { onOpenGlobalSearch(uiState.sourceQuery.trim()) },
			onTuneClick = { showSortAndLanguageSheet = true },
			onSettingsClick = onOpenSettings,
		)

		// Segmented Tabs: Manga | Novels | Manage
		ExploreSegmentedTabs(
			selectedKind = uiState.selectedKind,
			isNovelFirst = uiState.isNovelTabFirst,
			mangaCount = uiState.mangaSourceCount,
			novelCount = uiState.novelSourceCount,
			hasExtensionUpdates = uiState.hasExtensionUpdates,
			onSelectKind = viewModel::selectCatalogKind,
			onOpenManage = onOpenExtensions,
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
		)

		// Compact Utility Strip (Global Search, Random, Local, Bookmarks, Downloads)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState())
				.padding(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ExploreUtilityChip(
				icon = Icons.Default.Search,
				label = "Global Search",
				highlighted = true,
				onClick = { onOpenGlobalSearch(uiState.sourceQuery.trim()) },
			)
			ExploreUtilityChip(
				icon = Icons.Default.Shuffle,
				label = if (uiState.isFindingRandom) "Rolling…" else "Random",
				isLoading = uiState.isFindingRandom,
				onClick = {
					viewModel.findRandomManga { manga ->
						MangaTransferCache.put(manga)
						onOpenDetails(manga, "explore-random-${manga.id}")
					}
				},
			)
			ExploreUtilityChip(
				icon = Icons.Default.Folder,
				label = "Local",
				onClick = onOpenLocalFiles,
			)
			ExploreUtilityChip(
				icon = Icons.Default.Bookmark,
				label = "Bookmarks",
				onClick = onOpenBookmarks,
			)
			ExploreUtilityChip(
				icon = Icons.Default.Download,
				label = "Downloads",
				onClick = onOpenDownloads,
			)
		}

		// Sources Grid (4-col squircle) or List
		val columnsSpec = if (uiState.isSourcesGridMode) {
			GridCells.Fixed(gridColumns)
		} else {
			GridCells.Adaptive(minSize = 280.dp)
		}

		LazyVerticalGrid(
			columns = columnsSpec,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 14.dp,
				end = 14.dp,
				top = 6.dp,
				bottom = bottomPadding,
			),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(if (uiState.isSourcesGridMode) 14.dp else 10.dp),
		) {
			if (uiState.showSuggestionsOptIn) {
				item(key = "explore_suggestions_optin", span = { GridItemSpan(maxLineSpan) }) {
					NemuriSuggestionsOptInCard(
						onEnable = viewModel::enablePersonalizedSuggestions,
						onDismiss = viewModel::dismissSuggestionsPrompt,
						modifier = Modifier.padding(bottom = 4.dp),
					)
				}
			}
			if (uiState.isLoadingSources && uiState.sources.isEmpty()) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 32.dp),
						horizontalArrangement = Arrangement.Center,
						verticalAlignment = Alignment.CenterVertically,
					) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.cyan,
							strokeWidth = 2.5.dp,
							modifier = Modifier.size(22.dp),
						)
						Spacer(modifier = Modifier.width(10.dp))
						Text(
							text = "Loading installed sources…",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
			} else if (uiState.sources.isEmpty()) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					val isKindEmpty = uiState.allEnabledCount > 0 && uiState.sourceQuery.isBlank()
					val emptyTitle = when {
						uiState.allEnabledCount == 0 -> "No extensions installed yet"
						isKindEmpty && uiState.selectedKind == ExploreCatalogKind.NOVELS -> "No novel plugins installed"
						isKindEmpty -> "No manga extensions enabled"
						else -> "No matching sources"
					}
					val emptySubtitle = when {
						uiState.allEnabledCount == 0 ->
							"Open Manage to install Mihon manga APKs or LNReader light novel plugins."
						isKindEmpty && uiState.selectedKind == ExploreCatalogKind.NOVELS ->
							"Tap Manage to add LNReader JavaScript novel plugins."
						isKindEmpty ->
							"Tap Manage to install Mihon manga extensions or unhide disabled sources."
						else ->
							"Clear your search or language filter to view your enabled sources."
					}
					NemuriEmptyState(
						icon = if (uiState.selectedKind == ExploreCatalogKind.NOVELS) {
							Icons.AutoMirrored.Filled.MenuBook
						} else {
							Icons.Default.Explore
						},
						title = emptyTitle,
						subtitle = emptySubtitle,
						actionLabel = if (uiState.allEnabledCount == 0 || isKindEmpty) "Manage Extensions" else null,
						onAction = if (uiState.allEnabledCount == 0 || isKindEmpty) onOpenExtensions else null,
					)
				}
			} else {
				val pinned = uiState.sources.filter { it.isPinned }
				val unpinned = uiState.sources.filterNot { it.isPinned }

				if (pinned.isNotEmpty()) {
					item(key = "hdr_pinned", span = { GridItemSpan(maxLineSpan) }) {
						SectionHeading("PINNED (${pinned.size})")
					}
					items(pinned, key = { "pin:${it.sourceName}" }) { tile ->
						if (uiState.isSourcesGridMode) {
							ExploreSourceSquircleGridCell(
								tile = tile,
								onClick = {
									viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
									onOpenSourceBrowse(tile.sourceName)
								},
								onLongClick = { actionTargetTile = tile },
							)
						} else {
							ExploreSourceListCard(
								tile = tile,
								onClick = {
									viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
									onOpenSourceBrowse(tile.sourceName)
								},
								onLongClick = { actionTargetTile = tile },
								onTogglePin = { viewModel.togglePin(tile) },
							)
						}
					}
				}

				if (unpinned.isNotEmpty()) {
					item(key = "hdr_all", span = { GridItemSpan(maxLineSpan) }) {
						val label = if (uiState.selectedKind == ExploreCatalogKind.NOVELS) {
							"NOVEL SOURCES (${unpinned.size})"
						} else {
							"MANGA SOURCES (${unpinned.size})"
						}
						SectionHeading(label)
					}
					items(unpinned, key = { "src:${it.sourceName}" }) { tile ->
						if (uiState.isSourcesGridMode) {
							ExploreSourceSquircleGridCell(
								tile = tile,
								onClick = {
									viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
									onOpenSourceBrowse(tile.sourceName)
								},
								onLongClick = { actionTargetTile = tile },
							)
						} else {
							ExploreSourceListCard(
								tile = tile,
								onClick = {
									viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
									onOpenSourceBrowse(tile.sourceName)
								},
								onLongClick = { actionTargetTile = tile },
								onTogglePin = { viewModel.togglePin(tile) },
							)
						}
					}
				}
			}
		}
	}

	if (showSortAndLanguageSheet) {
		val sheetCardShape = NemuriTheme.shape(12.dp)
		NemuriModalSheet(
			title = "Explore Display & Filter",
			subtitle = "Source layout, columns, ordering & languages",
			onDismiss = { showSortAndLanguageSheet = false },
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 480.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 12.dp),
			) {
				item {
					Text(
						text = "SOURCE LAYOUT",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					Spacer(modifier = Modifier.height(6.dp))
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf(
							Triple(true, "Grid", Icons.Default.GridView),
							Triple(false, "List", Icons.AutoMirrored.Filled.ViewList),
						).forEach { (isGrid, label, icon) ->
							val selected = uiState.isSourcesGridMode == isGrid
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = sheetCardShape,
								border = BorderStroke(
									width = if (selected) 1.5.dp else 1.dp,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.weight(1f)
									.clip(sheetCardShape)
									.clickable { viewModel.setSourcesGridMode(isGrid) },
							) {
								Row(
									modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
									horizontalArrangement = Arrangement.Center,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Icon(
										imageVector = icon,
										contentDescription = null,
										tint = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										modifier = Modifier.size(17.dp),
									)
									Spacer(modifier = Modifier.width(6.dp))
									Text(
										text = label,
										style = MaterialTheme.typography.labelLarge,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
									)
								}
							}
						}
					}
				}

				if (uiState.isSourcesGridMode) {
					item {
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							text = "GRID COLUMNS",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
						)
						Spacer(modifier = Modifier.height(6.dp))
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							listOf(3, 4, 5).forEach { cols ->
								val selected = uiState.sourcesGridColumns == cols
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = sheetCardShape,
									border = BorderStroke(
										1.dp,
										if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(sheetCardShape)
										.clickable { viewModel.setSourcesGridColumns(cols) },
								) {
									Text(
										text = "$cols Columns",
										style = MaterialTheme.typography.labelLarge,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
										textAlign = TextAlign.Center,
										modifier = Modifier.padding(vertical = 10.dp),
									)
								}
							}
						}
					}
				}

				item {
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = "SORT ORDER",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
				}
				items(SourcesSortOrder.entries, key = { it.name }) { order ->
					val selected = uiState.sortOrder == order
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = sheetCardShape,
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								sheetCardShape,
							)
							.clip(sheetCardShape)
							.clickable { viewModel.setSortOrder(order) },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = when (order) {
									SourcesSortOrder.ALPHABETIC -> "Alphabetical"
									SourcesSortOrder.MANUAL -> "Manual order"
									SourcesSortOrder.LAST_USED -> "Recently used"
								},
								style = MaterialTheme.typography.titleSmall,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
							)
							if (selected) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}

				if (uiState.languages.isNotEmpty()) {
					item {
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = "SOURCE LANGUAGES",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
						)
					}
					items(uiState.languages, key = { it.code }) { lang ->
						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = sheetCardShape,
							modifier = Modifier
								.fillMaxWidth()
								.border(1.dp, NemuriTheme.colors.border, sheetCardShape)
								.clip(sheetCardShape)
								.clickable { viewModel.toggleLanguage(lang.code) },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column {
									Text(
										text = lang.displayName,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
									)
									Text(
										text = "${lang.sourceCount} source(s)",
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.textMuted,
									)
								}
								Checkbox(
									checked = lang.isEnabled,
									onCheckedChange = { viewModel.toggleLanguage(lang.code) },
									colors = CheckboxDefaults.colors(checkedColor = NemuriTheme.colors.cyan),
								)
							}
						}
					}
				}
			}
		}
	}

	val sheetTile = actionTargetTile
	if (sheetTile != null) {
		val languageVariants = remember(sheetTile.sourceName) { viewModel.getLanguageVariants(sheetTile) }
		NemuriModalSheet(
			title = sheetTile.title,
			subtitle = sheetTile.summary,
			onDismiss = { actionTargetTile = null },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				SheetActionRow(
					icon = if (sheetTile.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
					label = if (sheetTile.isPinned) "Unpin Source" else "Pin Source to Top",
					onClick = {
						viewModel.togglePin(sheetTile)
						actionTargetTile = null
					},
				)
				SheetActionRow(
					icon = Icons.Default.Home,
					label = if (sheetTile.isHomeSource) {
						"Pinned as Home Source for Feed Hub ✓"
					} else {
						"Set as Home Source for Feed Hub"
					},
					onClick = {
						viewModel.setHomeSourceForHub(sheetTile)
						actionTargetTile = null
					},
				)
				SheetActionRow(
					icon = Icons.Default.LocalFireDepartment,
					label = "Open Popular Catalog",
					onClick = {
						viewModel.prepareOpenPopular(sheetTile)
						actionTargetTile = null
						onOpenSourceBrowse(sheetTile.sourceName)
					},
				)
				if (languageVariants.size > 1) {
					val activeLabel = languageVariants.firstOrNull { it.isActive }?.displayName
						?: "${languageVariants.size} available"
					SheetActionRow(
						icon = Icons.Default.Language,
						label = "Switch Source Language ($activeLabel)",
						onClick = {
							languagePickerTile = sheetTile
							actionTargetTile = null
						},
					)
				}
				val url = sheetTile.homeUrl
				if (!url.isNullOrBlank()) {
					SheetActionRow(
						icon = Icons.Default.OpenInBrowser,
						label = "Open Site in WebView",
						onClick = {
							runCatching {
								context.startActivity(
									AppRouter.browserIntent(
										context,
										url,
										sheetTile.sourceInfo.mangaSource,
										sheetTile.title,
									),
								)
							}
							actionTargetTile = null
						},
					)
				}
				SheetActionRow(
					icon = Icons.Default.VisibilityOff,
					label = "Disable / Hide Source",
					isDestructive = true,
					onClick = {
						viewModel.hideSource(sheetTile)
						actionTargetTile = null
					},
				)
			}
		}
	}

	val pickerTile = languagePickerTile
	if (pickerTile != null) {
		val variants = remember(pickerTile.sourceName) { viewModel.getLanguageVariants(pickerTile) }
		NemuriModalSheet(
			title = "Source Language",
			subtitle = "${pickerTile.title} • ${variants.size} languages available",
			onDismiss = { languagePickerTile = null },
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 420.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 12.dp),
			) {
				items(variants, key = { it.languageCode }) { variant ->
					Surface(
						color = if (variant.isActive) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (variant.isActive) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(12.dp),
							)
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								viewModel.selectTileLanguage(pickerTile, variant.languageCode)
								languagePickerTile = null
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = variant.displayName,
									style = MaterialTheme.typography.titleSmall,
									color = if (variant.isActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								)
								Text(
									text = variant.languageCode.uppercase(),
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
							if (variant.isActive) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ExploreTopBar(
	query: String,
	onQueryChange: (String) -> Unit,
	onSearchSubmit: () -> Unit,
	onTuneClick: () -> Unit,
	onSettingsClick: () -> Unit,
) {
	val searchShape = NemuriTheme.shape(24.dp)
	val pillShape = NemuriTheme.pillShape()
	Surface(
		color = NemuriTheme.colors.background,
		modifier = Modifier.fillMaxWidth(),
	) {
		val searchField = rememberSyncedTextField(query, onQueryChange)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Surface(
				color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.78f),
				shape = searchShape,
				modifier = Modifier
					.weight(1f)
					.height(44.dp)
					.border(1.dp, NemuriTheme.colors.border, searchShape),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Box(
						modifier = Modifier
							.size(34.dp)
							.clip(pillShape)
							.clickable(onClick = onSearchSubmit),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = "Search",
							tint = NemuriTheme.colors.textMuted,
							modifier = Modifier.size(20.dp),
						)
					}

					Box(
						modifier = Modifier
							.weight(1f)
							.padding(horizontal = 6.dp),
						contentAlignment = Alignment.CenterStart,
					) {
						if (searchField.fieldValue.text.isEmpty()) {
							Text(
								text = "Filter sources or search all…",
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textDim,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
						BasicTextField(
							value = searchField.fieldValue,
							onValueChange = searchField::onValueChange,
							singleLine = true,
							textStyle = MaterialTheme.typography.bodyMedium.copy(
								color = NemuriTheme.colors.textPrimary,
							),
							cursorBrush = SolidColor(NemuriTheme.colors.cyan),
							keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
							keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
							modifier = Modifier.fillMaxWidth(),
						)
					}

					if (searchField.fieldValue.text.isNotEmpty()) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(pillShape)
								.clickable { searchField.clear() },
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear search",
								tint = NemuriTheme.colors.textMuted,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}

			HeaderSquareAction(
				icon = Icons.Default.Tune,
				contentDescription = "Display & filter options",
				onClick = onTuneClick,
			)

			HeaderSquareAction(
				icon = Icons.Default.Settings,
				contentDescription = "Settings",
				onClick = onSettingsClick,
			)
		}
	}
}

@Composable
private fun ExploreSegmentedTabs(
	selectedKind: ExploreCatalogKind,
	isNovelFirst: Boolean,
	mangaCount: Int,
	novelCount: Int,
	hasExtensionUpdates: Boolean,
	onSelectKind: (ExploreCatalogKind) -> Unit,
	onOpenManage: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val orderedKinds = if (isNovelFirst) {
		listOf(ExploreCatalogKind.NOVELS, ExploreCatalogKind.MANGA)
	} else {
		listOf(ExploreCatalogKind.MANGA, ExploreCatalogKind.NOVELS)
	}
	val containerShape = NemuriTheme.shape(16.dp)
	val tabShape = NemuriTheme.shape(12.dp)
	val badgePillShape = NemuriTheme.pillShape()

	Surface(
		color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.72f),
		shape = containerShape,
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = modifier
			.fillMaxWidth()
			.yorhaTacticalBrackets(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(4.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			orderedKinds.forEach { kind ->
				val selected = selectedKind == kind
				val count = if (kind == ExploreCatalogKind.MANGA) mangaCount else novelCount
				val bg by animateColorAsState(
					targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
					label = "exploreKindTabBg_${kind.name}",
				)
				val fg by animateColorAsState(
					targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else NemuriTheme.colors.textSecondary,
					label = "exploreKindTabFg_${kind.name}",
				)
				Surface(
					onClick = { onSelectKind(kind) },
					color = bg,
					shape = tabShape,
					border = if (selected) {
						BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f))
					} else {
						null
					},
					modifier = Modifier
						.weight(1f)
						.height(38.dp),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Text(
							text = if (count > 0) "${kind.label} ($count)" else kind.label,
							style = MaterialTheme.typography.labelLarge,
							fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
							color = fg,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}

			// 3rd Segmented Tab: Manage (opens Extensions Catalog + shows update badge)
			Box(modifier = Modifier.weight(1f)) {
				Surface(
					onClick = onOpenManage,
					color = androidx.compose.ui.graphics.Color.Transparent,
					shape = tabShape,
					modifier = Modifier
						.fillMaxWidth()
						.height(38.dp),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Icon(
							imageVector = Icons.Default.Extension,
							contentDescription = null,
							tint = if (hasExtensionUpdates) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(15.dp),
						)
						Spacer(modifier = Modifier.width(5.dp))
						Text(
							text = "Manage",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Medium,
							color = if (hasExtensionUpdates) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							maxLines = 1,
						)
					}
				}

				if (hasExtensionUpdates) {
					Surface(
						color = NemuriTheme.colors.cyan,
						shape = badgePillShape,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.offset(x = (-4).dp, y = (-2).dp)
							.border(1.5.dp, NemuriTheme.colors.surfaceElevated, badgePillShape),
					) {
						Text(
							text = "UPD",
							style = MaterialTheme.typography.labelSmall,
							fontSize = 8.5.sp,
							fontWeight = FontWeight.ExtraBold,
							color = NemuriTheme.colors.onCyan,
							modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ExploreUtilityChip(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	highlighted: Boolean = false,
	isLoading: Boolean = false,
) {
	Surface(
		onClick = onClick,
		enabled = !isLoading,
		color = if (highlighted) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
		shape = NemuriTheme.pillShape(),
		border = BorderStroke(
			1.dp,
			if (highlighted) NemuriTheme.colors.cyan.copy(alpha = 0.55f) else NemuriTheme.colors.border,
		),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			if (isLoading) {
				CircularProgressIndicator(
					color = NemuriTheme.colors.cyan,
					strokeWidth = 2.dp,
					modifier = Modifier.size(13.dp),
				)
			} else {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(15.dp),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				color = if (highlighted) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
				maxLines = 1,
				softWrap = false,
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceSquircleGridCell(
	tile: ExploreSourceTile,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val squircleShape = NemuriTheme.shape(18.dp)
	val cellShape = NemuriTheme.shape(14.dp)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(cellShape)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			)
			.padding(horizontal = 2.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(contentAlignment = Alignment.Center) {
			Surface(
				color = NemuriTheme.colors.card,
				shape = squircleShape,
				modifier = Modifier
					.size(56.dp)
					.border(
						width = if (tile.isPinned || tile.isHomeSource) 1.5.dp else 1.dp,
						color = when {
							tile.isHomeSource -> NemuriTheme.colors.cyan
							tile.isPinned -> NemuriTheme.colors.cyan.copy(alpha = 0.55f)
							else -> NemuriTheme.colors.border
						},
						shape = squircleShape,
					),
			) {
				SubcomposeAsyncImage(
					model = tile.sourceInfo.mangaSource.faviconUri(),
					contentDescription = tile.title,
					contentScale = ContentScale.Crop,
					error = {
						Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
							Icon(
								imageVector = if (tile.isNovel) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Explore,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(22.dp),
							)
						}
					},
					modifier = Modifier
						.fillMaxSize()
						.padding(4.dp)
						.clip(RoundedCornerShape(14.dp)),
				)
			}

			// Top-right corner indicator for Home Source or Pin
			if (tile.isHomeSource || tile.isPinned) {
				Surface(
					color = NemuriTheme.colors.cyan,
					shape = CircleShape,
					modifier = Modifier
						.align(Alignment.TopEnd)
						.offset(x = 3.dp, y = (-3).dp)
						.size(17.dp)
						.border(1.5.dp, NemuriTheme.colors.background, CircleShape),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = if (tile.isHomeSource) Icons.Default.Home else Icons.Default.PushPin,
							contentDescription = null,
							tint = NemuriTheme.colors.onCyan,
							modifier = Modifier.size(10.dp),
						)
					}
				}
			}

			if (tile.isNsfw) {
				Surface(
					color = NemuriTheme.colors.danger,
					shape = RoundedCornerShape(4.dp),
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.offset(x = 2.dp, y = 2.dp)
						.border(1.dp, NemuriTheme.colors.background, RoundedCornerShape(4.dp)),
				) {
					NonWrappingBadgeText(
						text = "18+",
						color = androidx.compose.ui.graphics.Color.White,
						fontSizeSp = 7.5f,
						modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
					)
				}
			}
		}

		Text(
			text = tile.title,
			style = MaterialTheme.typography.labelMedium,
			fontSize = 11.5.sp,
			lineHeight = 14.sp,
			fontWeight = FontWeight.Medium,
			color = NemuriTheme.colors.textPrimary,
			textAlign = TextAlign.Center,
			minLines = 2,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth(),
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceListCard(
	tile: ExploreSourceTile,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onTogglePin: () -> Unit,
) {
	val cardShape = NemuriTheme.shape(16.dp)
	val iconShape = NemuriTheme.shape(12.dp)
	Surface(
		color = NemuriTheme.colors.card,
		shape = cardShape,
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				when {
					tile.isHomeSource -> NemuriTheme.colors.cyan
					tile.isPinned -> NemuriTheme.colors.cyan.copy(alpha = 0.42f)
					else -> NemuriTheme.colors.border
				},
				cardShape,
			)
			.clip(cardShape)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = iconShape,
				modifier = Modifier
					.size(42.dp)
					.border(1.dp, NemuriTheme.colors.border, iconShape),
			) {
				SubcomposeAsyncImage(
					model = tile.sourceInfo.mangaSource.faviconUri(),
					contentDescription = tile.title,
					contentScale = ContentScale.Crop,
					error = {
						Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
							Icon(
								imageVector = if (tile.isNovel) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Explore,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(18.dp),
							)
						}
					},
					modifier = Modifier
						.fillMaxSize()
						.clip(RoundedCornerShape(12.dp)),
				)
			}
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = tile.title,
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (tile.isHomeSource) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = RoundedCornerShape(5.dp),
						) {
							NonWrappingBadgeText(
								text = "HUB",
								color = NemuriTheme.colors.cyan,
								fontSizeSp = 8.5f,
								modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
							)
						}
					}
				}
				val meta = tile.summary ?: if (tile.isNovel) "Novel" else "Manga"
				Text(
					text = meta,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (tile.isNovel || tile.isNsfw) {
					Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
						if (tile.isNovel) {
							Surface(
								color = NemuriTheme.colors.cyan.copy(alpha = 0.16f),
								shape = RoundedCornerShape(5.dp),
							) {
								NonWrappingBadgeText(
									text = "NOVEL",
									color = NemuriTheme.colors.cyan,
									fontSizeSp = 8.5f,
									modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
								)
							}
						}
						if (tile.isNsfw) {
							Surface(
								color = NemuriTheme.colors.danger.copy(alpha = 0.18f),
								shape = RoundedCornerShape(5.dp),
							) {
								NonWrappingBadgeText(
									text = "18+",
									color = NemuriTheme.colors.danger,
									fontSizeSp = 8.5f,
									modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
								)
							}
						}
					}
				}
			}
			IconButton(
				onClick = onTogglePin,
				modifier = Modifier.size(34.dp),
			) {
				Icon(
					imageVector = if (tile.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
					contentDescription = if (tile.isPinned) "Unpin" else "Pin",
					tint = if (tile.isPinned) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
					modifier = Modifier.size(16.dp),
				)
			}
		}
	}
}

@Composable
private fun SectionHeading(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		fontWeight = FontWeight.Bold,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier
			.padding(top = 4.dp, bottom = 2.dp)
			.yorhaTacticalBrackets()
			.padding(horizontal = 4.dp, vertical = 2.dp),
	)
}

@Composable
private fun SheetActionRow(
	icon: ImageVector,
	label: String,
	isDestructive: Boolean = false,
	onClick: () -> Unit,
) {
	val color = if (isDestructive) NemuriTheme.colors.danger else NemuriTheme.colors.textPrimary
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = color,
				modifier = Modifier.size(20.dp),
			)
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				color = color,
			)
		}
	}
}
