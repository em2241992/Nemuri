package org.nemuri.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.prefs.CoverAspectRatio
import org.koitharu.kotatsu.core.prefs.GridGutterDensity
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.core.prefs.QuickScrollButtonsMode
import org.koitharu.kotatsu.core.prefs.SelectionMenuStyle
import kotlin.math.roundToInt
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaComfortableGridCard
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriListNavigationOverlay
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.NemuriUpdateBanner
import org.nemuri.app.ui.components.gridDragRangeSelect
import org.nemuri.app.ui.components.listDragRangeSelect
import org.nemuri.app.ui.components.pinchToStepColumns
import org.nemuri.app.ui.components.rememberAppSettings
import org.nemuri.app.ui.components.rememberNemuriScrollAdapter
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriEffectsSpring
import org.nemuri.app.ui.theme.nemuriSpatialSpring

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenExplore: () -> Unit,
	onOpenSettings: () -> Unit,
	onOpenMigrate: (Long) -> Unit = {},
	viewModel: LibraryViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val layoutPrefs = LocalLayoutStudioPrefs.current
	val appSettings = rememberAppSettings()
	val config = LocalConfiguration.current
	val isLandscape = config.screenWidthDp > config.screenHeightDp
	val gridColumns = layoutPrefs.columnsForOrientation(isLandscape)

	var showDisplaySheet by remember { mutableStateOf(false) }
	var showCategoryPickerSheet by remember { mutableStateOf(false) }
	var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
	var newCategoryName by rememberSaveable { mutableStateOf("") }

	val isSelectionMode = uiState.selectedIds.isNotEmpty()
	val bottomClearance = rememberRootTabBottomPadding(extraBottom = if (isSelectionMode) 96.dp else 16.dp)
	BackHandler(enabled = isSelectionMode) {
		viewModel.clearSelection()
	}

	val effectiveCardStyle = when {
		layoutPrefs.cardStyle == MangaCardStyle.DETAILED_LIST ||
			uiState.listMode == ListMode.LIST ||
			uiState.listMode == ListMode.DETAILED_LIST -> MangaCardStyle.DETAILED_LIST
		layoutPrefs.cardStyle == MangaCardStyle.COMFORTABLE -> MangaCardStyle.COMFORTABLE
		else -> MangaCardStyle.COMPACT
	}

	val orderedIds = remember(uiState.items) { uiState.items.map { it.manga.id } }
	val libraryCalloutForIndex: (Int) -> String? = remember(uiState.items) {
		{ idx ->
			val title = uiState.items.getOrNull(idx)?.manga?.title?.trim().orEmpty()
			val firstChar = title.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
			when {
				title.isEmpty() -> null
				firstChar != null && firstChar.isLetter() -> firstChar.toString()
				else -> "#"
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			if (isSelectionMode) {
				Surface(
					color = NemuriTheme.colors.surface,
					modifier = Modifier.fillMaxWidth(),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.statusBarsPadding()
							.padding(horizontal = 8.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						IconButton(onClick = viewModel::clearSelection) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear selection",
								tint = NemuriTheme.colors.textPrimary,
							)
						}
						Text(
							text = "${uiState.selectedIds.size} selected",
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.cyan,
							modifier = Modifier.weight(1f),
						)
						IconButton(onClick = viewModel::selectAll) {
							Icon(
								imageVector = Icons.Default.SelectAll,
								contentDescription = "Select all",
								tint = NemuriTheme.colors.textSecondary,
							)
						}
						TextButton(onClick = viewModel::invertSelection) {
							Text("Invert", color = NemuriTheme.colors.cyan)
						}
					}
				}
			} else {
				NemuriTopSearchHeader(
					query = uiState.query,
					onQueryChange = viewModel::setQuery,
					placeholder = "Search library…",
					pillTrailingIcon = Icons.Default.Refresh,
					pillTrailingContentDescription = if (uiState.isTrackerRunning) "Stop check" else "Check updates",
					onPillTrailingClick = viewModel::checkForUpdatesNow,
					onTuneClick = { showDisplaySheet = true },
					onSettingsClick = onOpenSettings,
				)
			}

			uiState.availableUpdate?.let { update ->
				if (!isSelectionMode) {
					NemuriUpdateBanner(
						version = update,
						isDownloading = uiState.isDownloadingUpdate,
						downloadProgress = uiState.updateDownloadProgress,
						onInstall = viewModel::installAvailableUpdate,
						onDismiss = viewModel::dismissAvailableUpdate,
					)
				}
			}

			// Row 1: Categories Row inside subtle tonal container track with Folder icon badge
			if (uiState.categories.isNotEmpty() || uiState.items.isNotEmpty() || uiState.totalLibraryCount > 0) {
				val trackShape = NemuriTheme.shape(16.dp)
				val badgeShape = NemuriTheme.shape(10.dp)
				val pillShape = NemuriTheme.pillShape()
				Surface(
					color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.72f),
					shape = trackShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp, vertical = 3.dp),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 8.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = badgeShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
							modifier = Modifier.size(30.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = Icons.Rounded.Folder,
									contentDescription = "Categories",
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(16.dp),
								)
							}
						}

						Row(
							modifier = Modifier
								.weight(1f)
								.clipToBounds()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							if (uiState.showAllCategoryTab || uiState.categories.isEmpty()) {
								val isAllSelected = uiState.selectedCategoryId == LibraryViewModel.ALL_CATEGORIES_ID
								val allCount = if (isAllSelected && uiState.query.isBlank() && uiState.quickFilter == LibraryQuickFilter.ALL) {
									uiState.items.size.takeIf { it > 0 } ?: uiState.totalLibraryCount
								} else {
									uiState.totalLibraryCount
								}
								LibraryCategoryPill(
									label = "All",
									count = allCount.takeIf { it > 0 },
									selected = isAllSelected,
									onClick = { viewModel.selectCategory(LibraryViewModel.ALL_CATEGORIES_ID) },
								)
							}
							uiState.categories.forEach { cat ->
								val selected = uiState.selectedCategoryId == cat.id
								val catCount = if (selected && uiState.query.isBlank() && uiState.quickFilter == LibraryQuickFilter.ALL) {
									uiState.items.size
								} else {
									uiState.categoryCounts[cat.id] ?: 0
								}
								LibraryCategoryPill(
									label = cat.title,
									count = catCount,
									selected = selected,
									onClick = { viewModel.selectCategory(cat.id) },
								)
							}
							Surface(
								color = NemuriTheme.colors.card,
								shape = pillShape,
								modifier = Modifier
									.border(1.dp, NemuriTheme.colors.border, pillShape)
									.clip(pillShape)
									.clickable {
										newCategoryName = ""
										showCreateCategoryDialog = true
									},
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(3.dp),
								) {
									Icon(
										imageVector = Icons.Default.Add,
										contentDescription = "New category",
										tint = NemuriTheme.colors.cyan,
										modifier = Modifier.size(14.dp),
									)
									Text(
										text = "Category",
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.SemiBold,
										color = NemuriTheme.colors.cyan,
									)
								}
							}
						}
					}
				}
			}

			// Row 2: Status Filters inside matching tonal container track with Veri-Lys secondary accent badge
			val statusTrackShape = NemuriTheme.shape(16.dp)
			val statusBadgeShape = NemuriTheme.shape(10.dp)
			Surface(
				color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.72f),
				shape = statusTrackShape,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 3.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 8.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Surface(
						color = NemuriTheme.colors.secondaryContainer,
						shape = statusBadgeShape,
						border = BorderStroke(1.dp, NemuriTheme.colors.secondary.copy(alpha = 0.45f)),
						modifier = Modifier.size(30.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.FilterList,
								contentDescription = "Status filters",
								tint = NemuriTheme.colors.secondary,
								modifier = Modifier.size(16.dp),
							)
						}
					}

					Row(
						modifier = Modifier
							.weight(1f)
							.clipToBounds()
							.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						LibraryQuickFilter.entries.forEach { filter ->
							val dotColor: Color? = when (filter) {
								LibraryQuickFilter.ALL -> null
								LibraryQuickFilter.UNREAD -> NemuriTheme.colors.cyan
								LibraryQuickFilter.DOWNLOADED -> NemuriTheme.colors.healthy
								LibraryQuickFilter.NEW_CHAPTERS -> NemuriTheme.colors.amber
								LibraryQuickFilter.ONGOING -> Color(0xFF60A5FA)
								LibraryQuickFilter.COMPLETED -> NemuriTheme.colors.secondary
							}
							LibraryStatusChip(
								label = filter.label,
								dotColor = dotColor,
								selected = uiState.quickFilter == filter,
								onClick = { viewModel.setQuickFilter(filter) },
							)
						}
					}
				}
			}

			when {
				!uiState.isLoaded -> {
					NemuriLoadingState(message = "Loading library…")
				}

				uiState.items.isEmpty() -> {
					val isFiltered = uiState.query.isNotBlank() || uiState.quickFilter != LibraryQuickFilter.ALL
					NemuriEmptyState(
						icon = Icons.Default.LocalLibrary,
						title = if (isFiltered) "No matching titles" else "Your library is empty",
						subtitle = if (isFiltered) {
							"Adjust your search query or quick filter."
						} else {
							"Explore extensions or import local CBZ/EPUB/PDF files to build your shelf."
						},
						actionLabel = if (isFiltered) null else "Open Explore",
						onAction = if (isFiltered) null else onOpenExplore,
					)
				}

				effectiveCardStyle == MangaCardStyle.DETAILED_LIST -> {
					val listState = rememberLazyListState()
					val listAdapter = rememberNemuriScrollAdapter(listState)
					Box(modifier = Modifier.fillMaxSize()) {
						LazyColumn(
							state = listState,
							modifier = Modifier
								.fillMaxSize()
								.listDragRangeSelect(
									listState = listState,
									orderedIds = orderedIds,
									selectedIds = uiState.selectedIds,
									onSelectionChange = viewModel::setSelection,
								),
							contentPadding = PaddingValues(
								start = layoutPrefs.gutterDensity.outerPaddingDp.dp,
								end = layoutPrefs.gutterDensity.outerPaddingDp.dp,
								top = 8.dp,
								bottom = bottomClearance,
							),
							verticalArrangement = Arrangement.spacedBy(layoutPrefs.gutterDensity.spacingDp.dp),
						) {
							items(uiState.items, key = { it.manga.id }) { item ->
								val selected = item.manga.id in uiState.selectedIds
								val shareKey = "lib-${item.manga.id}"
								MangaListRow(
									manga = item.manga,
									subtitle = item.sourceTitle,
									unreadCount = item.unreadCount,
									progress = item.progress,
									isSelected = selected,
									isDownloaded = item.isDownloaded,
									tagsSummary = item.manga.tags.take(3).joinToString(" • ") { it.title }.takeIf { it.isNotBlank() },
									sharedCoverKey = shareKey,
									onClick = {
										if (isSelectionMode) {
											viewModel.toggleSelection(item.manga.id)
										} else {
											MangaTransferCache.put(item.manga)
											onOpenDetails(item.manga, shareKey)
										}
									},
									onLongClick = { viewModel.toggleSelection(item.manga.id) },
								)
							}
						}

						NemuriListNavigationOverlay(
							adapter = listAdapter,
							hasBottomDock = true,
							visibleOverride = !isSelectionMode,
							labelForIndex = libraryCalloutForIndex,
						)
					}
				}

				else -> {
					val gridState = rememberLazyGridState()
					val gridAdapter = rememberNemuriScrollAdapter(gridState)
					Box(modifier = Modifier.fillMaxSize()) {
						LazyVerticalGrid(
							state = gridState,
							columns = GridCells.Fixed(gridColumns),
							modifier = Modifier
								.fillMaxSize()
								.pinchToStepColumns(
									currentColumns = gridColumns,
									minColumns = if (isLandscape) 4 else 2,
									maxColumns = if (isLandscape) 9 else 6,
									onStepColumns = { next ->
										if (isLandscape) {
											viewModel.setLandscapeColumns(next)
										} else {
											viewModel.setPortraitColumns(next)
										}
									},
								)
								.gridDragRangeSelect(
									gridState = gridState,
									orderedIds = orderedIds,
									selectedIds = uiState.selectedIds,
									onSelectionChange = viewModel::setSelection,
								),
							contentPadding = PaddingValues(
								start = layoutPrefs.gutterDensity.outerPaddingDp.dp,
								end = layoutPrefs.gutterDensity.outerPaddingDp.dp,
								top = 8.dp,
								bottom = bottomClearance,
							),
							horizontalArrangement = Arrangement.spacedBy(layoutPrefs.gutterDensity.spacingDp.dp),
							verticalArrangement = Arrangement.spacedBy(layoutPrefs.gutterDensity.spacingDp.dp),
						) {
							items(uiState.items, key = { it.manga.id }) { item ->
								val selected = item.manga.id in uiState.selectedIds
								val shareKey = "lib-${item.manga.id}"
								if (effectiveCardStyle == MangaCardStyle.COMFORTABLE) {
									MangaComfortableGridCard(
										manga = item.manga,
										unreadCount = item.unreadCount,
										isCaughtUp = item.isCaughtUp,
										isDownloaded = item.isDownloaded,
										sourceBadge = item.sourceTitle.take(6).uppercase(),
										progress = item.progress,
										isSelected = selected,
										sharedCoverKey = shareKey,
										onClick = {
											if (isSelectionMode) {
												viewModel.toggleSelection(item.manga.id)
											} else {
												MangaTransferCache.put(item.manga)
												onOpenDetails(item.manga, shareKey)
											}
										},
										onLongClick = { viewModel.toggleSelection(item.manga.id) },
									)
								} else {
									MangaCompactGridCard(
										manga = item.manga,
										unreadCount = item.unreadCount,
										isCaughtUp = item.isCaughtUp,
										isDownloaded = item.isDownloaded,
										sourceBadge = item.sourceTitle.take(6).uppercase(),
										progress = item.progress,
										isSelected = selected,
										sharedCoverKey = shareKey,
										onClick = {
											if (isSelectionMode) {
												viewModel.toggleSelection(item.manga.id)
											} else {
												MangaTransferCache.put(item.manga)
												onOpenDetails(item.manga, shareKey)
											}
										},
										onLongClick = { viewModel.toggleSelection(item.manga.id) },
									)
								}
							}
						}

						NemuriListNavigationOverlay(
							adapter = gridAdapter,
							hasBottomDock = true,
							visibleOverride = !isSelectionMode,
							labelForIndex = libraryCalloutForIndex,
						)
					}
				}
			}
		}

		// Floating M3 Expressive Contextual Action Bar above Navigation Dock
		AnimatedVisibility(
			visible = isSelectionMode,
			enter = slideInVertically(nemuriSpatialSpring()) { it / 2 } + fadeIn(nemuriEffectsSpring()),
			exit = slideOutVertically(nemuriSpatialSpring()) { it / 2 } + fadeOut(nemuriEffectsSpring()),
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(
					start = 14.dp,
					end = 14.dp,
					bottom = if (layoutPrefs.isNavBarCompact) 72.dp else 82.dp,
				),
		) {
			Surface(
				color = NemuriTheme.colors.sheet,
				shape = RoundedCornerShape(24.dp),
				shadowElevation = 14.dp,
				modifier = Modifier
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState())
						.padding(horizontal = 10.dp, vertical = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					ContextualBarAction(
						icon = Icons.Default.CreateNewFolder,
						label = "Category",
						onClick = { showCategoryPickerSheet = true },
					)
					ContextualBarAction(
						icon = Icons.Default.DoneAll,
						label = "Mark Read",
						onClick = viewModel::markSelectedUpdatesRead,
					)
					ContextualBarAction(
						icon = Icons.Default.Undo,
						label = "Unread",
						onClick = viewModel::markSelectedUnread,
					)
					ContextualBarAction(
						icon = Icons.Default.Download,
						label = "Download",
						onClick = viewModel::downloadSelected,
					)
					uiState.selectedIds.firstOrNull()?.let { firstId ->
						ContextualBarAction(
							icon = Icons.Default.SwapHoriz,
							label = "Migrate",
							onClick = {
								viewModel.clearSelection()
								onOpenMigrate(firstId)
							},
						)
					}
					ContextualBarAction(
						icon = Icons.Default.DeleteOutline,
						label = "Remove",
						tint = NemuriTheme.colors.danger,
						onClick = viewModel::removeSelectedFromLibrary,
					)
				}
			}
		}
	}

	if (showDisplaySheet) {
		val isVisualDiagram = layoutPrefs.selectionMenuStyle == SelectionMenuStyle.VISUAL_DIAGRAM
		NemuriModalSheet(
			title = "Library Display",
			subtitle = "${uiState.items.size} title(s) • ${effectiveCardStyle.label}",
			onDismiss = { showDisplaySheet = false },
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				// Header Visual <-> Compact Menu Style Toggle
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "CARD STYLE",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.clickable {
								appSettings.selectionMenuStyle = if (isVisualDiagram) {
									SelectionMenuStyle.COMPACT_LIST
								} else {
									SelectionMenuStyle.VISUAL_DIAGRAM
								}
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(5.dp),
						) {
							Icon(
								imageVector = if (isVisualDiagram) Icons.Default.GridView else Icons.Default.ViewList,
								contentDescription = "Menu style",
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(14.dp),
							)
							Text(
								text = if (isVisualDiagram) "Visual ↔ Compact" else "Compact ↔ Visual",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
							)
						}
					}
				}

				val cardStyleEntries = listOf(
					Triple(MangaCardStyle.COMPACT, ListMode.GRID, "Compact") to "Corner-Safe Overlay",
					Triple(MangaCardStyle.COMFORTABLE, ListMode.GRID, "Comfortable") to "Title Below Cover",
					Triple(MangaCardStyle.DETAILED_LIST, ListMode.LIST, "Detailed") to "Full List Row",
				)
				if (isVisualDiagram) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						cardStyleEntries.forEach { (triple, sub) ->
							val (style, listMode, shortLabel) = triple
							val selected = effectiveCardStyle == style
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(14.dp),
								border = BorderStroke(
									width = if (selected) 1.5.dp else 1.dp,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(14.dp))
									.clickable {
										appSettings.mangaCardStyle = style
										appSettings.isTitleOverCover = style != MangaCardStyle.COMFORTABLE
										viewModel.setListMode(listMode)
									},
							) {
								Column(
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									CardStyleDiagramCanvas(
										style = style,
										selected = selected,
										modifier = Modifier.size(width = 46.dp, height = 42.dp),
									)
									Text(
										text = shortLabel,
										style = MaterialTheme.typography.labelMedium,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
										maxLines = 1,
									)
									Text(
										text = sub,
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.textMuted,
										textAlign = TextAlign.Center,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
							}
						}
					}
				} else {
					Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
						cardStyleEntries.forEach { (triple, sub) ->
							val (style, listMode, shortLabel) = triple
							val selected = effectiveCardStyle == style
							LibrarySheetCompactRow(
								label = "$shortLabel — $sub",
								selected = selected,
								onClick = {
									appSettings.mangaCardStyle = style
									appSettings.isTitleOverCover = style != MangaCardStyle.COMFORTABLE
									viewModel.setListMode(listMode)
								},
							)
						}
					}
				}

				if (effectiveCardStyle != MangaCardStyle.DETAILED_LIST) {
					Text(
						text = "COVER ASPECT RATIO",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					if (isVisualDiagram) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							CoverAspectRatio.entries.forEach { ratio ->
								val selected = layoutPrefs.coverAspectRatio == ratio
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(12.dp),
									border = BorderStroke(
										width = if (selected) 1.5.dp else 1.dp,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(RoundedCornerShape(12.dp))
										.clickable { appSettings.coverAspectRatio = ratio },
								) {
									Row(
										modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(10.dp),
									) {
										AspectRatioDiagramCanvas(
											ratio = ratio,
											selected = selected,
											modifier = Modifier.size(width = 28.dp, height = 34.dp),
										)
										Text(
											text = ratio.label,
											style = MaterialTheme.typography.labelMedium,
											fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
											color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
											maxLines = 1,
										)
									}
								}
							}
						}
					} else {
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							CoverAspectRatio.entries.forEach { ratio ->
								LibraryChip(
									label = ratio.label,
									selected = layoutPrefs.coverAspectRatio == ratio,
									onClick = { appSettings.coverAspectRatio = ratio },
								)
							}
						}
					}
				}

				Text(
					text = "GRID GUTTER DENSITY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				if (isVisualDiagram) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						GridGutterDensity.entries.forEach { density ->
							val selected = layoutPrefs.gutterDensity == density
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(
									width = if (selected) 1.5.dp else 1.dp,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(12.dp))
									.clickable { appSettings.gridGutterDensity = density },
							) {
								Column(
									modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									GutterDensityDiagramCanvas(
										density = density,
										selected = selected,
										modifier = Modifier.size(34.dp),
									)
									Text(
										text = density.label,
										style = MaterialTheme.typography.labelSmall,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										maxLines = 1,
									)
								}
							}
						}
					}
				} else {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						GridGutterDensity.entries.forEach { density ->
							LibraryChip(
								label = "${density.label} (${density.spacingDp}dp)",
								selected = layoutPrefs.gutterDensity == density,
								onClick = { appSettings.gridGutterDensity = density },
							)
						}
					}
				}

				if (effectiveCardStyle != MangaCardStyle.DETAILED_LIST) {
					val minCols = if (isLandscape) 4 else 2
					val maxCols = if (isLandscape) 9 else 6
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = if (isLandscape) "LANDSCAPE COLUMNS / CARD SIZE" else "PORTRAIT COLUMNS / CARD SIZE",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
							Text(
								text = "$gridColumns cols",
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.cyan,
							)
						}
						Slider(
							value = gridColumns.coerceIn(minCols, maxCols).toFloat(),
							onValueChange = { raw ->
								val cols = raw.roundToInt().coerceIn(minCols, maxCols)
								if (isLandscape) {
									viewModel.setLandscapeColumns(cols)
								} else {
									viewModel.setPortraitColumns(cols)
								}
							},
							valueRange = minCols.toFloat()..maxCols.toFloat(),
							steps = (maxCols - minCols - 1).coerceAtLeast(0),
							colors = SliderDefaults.colors(
								thumbColor = NemuriTheme.colors.cyan,
								activeTrackColor = NemuriTheme.colors.cyan,
								inactiveTrackColor = NemuriTheme.colors.surfaceElevated,
							),
						)
					}
				}

				Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "CORNER RADIUS",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
							Text(
								text = "Automatically adjusts corner-safe title & badge insets",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Text(
							text = "${layoutPrefs.cardCornerRadiusDp} dp",
							style = MaterialTheme.typography.labelLarge,
							color = NemuriTheme.colors.cyan,
						)
					}
					Slider(
						value = layoutPrefs.cardCornerRadiusDp.coerceIn(0, 24).toFloat(),
						onValueChange = { appSettings.cardCornerRadiusDp = it.roundToInt().coerceIn(0, 24) },
						valueRange = 0f..24f,
						steps = 11,
						colors = SliderDefaults.colors(
							thumbColor = NemuriTheme.colors.cyan,
							activeTrackColor = NemuriTheme.colors.cyan,
							inactiveTrackColor = NemuriTheme.colors.surfaceElevated,
						),
					)
				}

				HorizontalDivider(color = NemuriTheme.colors.border)

				Text(
					text = "CARD BADGES & OVERLAYS",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				LibraryBadgeToggleRow(
					title = "Unread Count",
					subtitle = "Show unread chapter pill / caught-up indicator",
					checked = layoutPrefs.showUnreadBadge,
					onCheckedChange = { appSettings.showUnreadBadge = it },
				)
				LibraryBadgeToggleRow(
					title = "Downloaded",
					subtitle = "Show offline saved badge on library covers",
					checked = layoutPrefs.showDownloadedBadge,
					onCheckedChange = { appSettings.showDownloadedBadge = it },
				)
				LibraryBadgeToggleRow(
					title = "Source Badge",
					subtitle = "Show compact source monogram on top-right of covers",
					checked = layoutPrefs.showSourceBadge,
					onCheckedChange = { appSettings.showSourceBadge = it },
				)
				LibraryBadgeToggleRow(
					title = "Bottom Reading Progress Bar",
					subtitle = "Show subtle progress bar along bottom edge of cards",
					checked = layoutPrefs.showBottomProgressBar,
					onCheckedChange = { appSettings.showBottomProgressBar = it },
				)
				LibraryBadgeToggleRow(
					title = "Floating ▲ / ▼ Scroll Buttons",
					subtitle = "Show quick jump-to-top / bottom capsule on long lists",
					checked = layoutPrefs.quickScrollButtonsMode != QuickScrollButtonsMode.OFF,
					onCheckedChange = { enabled ->
						appSettings.quickScrollButtonsMode = if (enabled) {
							QuickScrollButtonsMode.LONG_LISTS_ONLY
						} else {
							QuickScrollButtonsMode.OFF
						}
					},
				)

				HorizontalDivider(color = NemuriTheme.colors.border)

				Text(
					text = "SORT BY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				val sortChoices = listOf(
					ListSortOrder.NEWEST to "Date Added",
					ListSortOrder.ALPHABETIC to "Alphabetical A–Z",
					ListSortOrder.LAST_READ to "Last Read",
					ListSortOrder.NEW_CHAPTERS to "Unread / New Chapters",
					ListSortOrder.LATEST_CHAPTER to "Latest Chapter",
					ListSortOrder.PROGRESS to "Reading Progress",
					ListSortOrder.TOTAL_CHAPTERS to "Total Chapters",
				)
				sortChoices.forEach { (order, label) ->
					LibrarySheetCompactRow(
						label = label,
						selected = uiState.sortOrder == order,
						onClick = { viewModel.setSortOrder(order) },
					)
				}

				if (uiState.selectedCategoryId != LibraryViewModel.ALL_CATEGORIES_ID) {
					HorizontalDivider(color = NemuriTheme.colors.border)
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(1.dp, NemuriTheme.colors.danger.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								viewModel.deleteSelectedCategory()
								showDisplaySheet = false
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(Icons.Default.DeleteOutline, null, tint = NemuriTheme.colors.danger)
							Text(
								text = "Delete Current Category",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.danger,
							)
						}
					}
				}
			}
		}
	}

	if (showCategoryPickerSheet) {
		NemuriModalSheet(
			title = "Add to Category",
			subtitle = "${uiState.selectedIds.size} title(s) selected",
			onDismiss = { showCategoryPickerSheet = false },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				uiState.categories.forEach { cat ->
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								viewModel.addSelectedToCategory(cat)
								showCategoryPickerSheet = false
							},
					) {
						Text(
							text = cat.title,
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
						)
					}
				}
				Button(
					onClick = {
						showCategoryPickerSheet = false
						newCategoryName = ""
						showCreateCategoryDialog = true
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("+ Create New Category")
				}
			}
		}
	}

	if (showCreateCategoryDialog) {
		AlertDialog(
			onDismissRequest = { showCreateCategoryDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("New Library Category") },
			text = {
				OutlinedTextField(
					value = newCategoryName,
					onValueChange = { newCategoryName = it },
					label = { Text("Category name") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
			},
			confirmButton = {
				Button(
					onClick = {
						viewModel.createCategory(newCategoryName)
						showCreateCategoryDialog = false
					},
					enabled = newCategoryName.isNotBlank(),
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) {
					Text("Create", fontWeight = FontWeight.Bold)
				}
			},
			dismissButton = {
				TextButton(onClick = { showCreateCategoryDialog = false }) {
					Text("Cancel", color = NemuriTheme.colors.textSecondary)
				}
			},
		)
	}
}

@Composable
private fun ContextualBarAction(
	icon: ImageVector,
	label: String,
	tint: Color = NemuriTheme.colors.textPrimary,
	onClick: () -> Unit,
) {
	Column(
		modifier = Modifier
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = 10.dp, vertical = 6.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		Icon(
			imageVector = icon,
			contentDescription = label,
			tint = tint,
			modifier = Modifier.size(20.dp),
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = tint,
			maxLines = 1,
			softWrap = false,
		)
	}
}

@Composable
private fun LibraryCategoryPill(
	label: String,
	count: Int?,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val pillShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.card,
		shape = pillShape,
		modifier = Modifier
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				pillShape,
			)
			.clip(pillShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(5.dp),
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.textPrimary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (count != null) {
				Surface(
					color = if (selected) {
						NemuriTheme.colors.onCyan.copy(alpha = 0.20f)
					} else {
						NemuriTheme.colors.secondaryContainer
					},
					shape = pillShape,
				) {
					Text(
						text = count.toString(),
						style = MaterialTheme.typography.labelSmall,
						fontSize = 10.sp,
						fontWeight = FontWeight.Bold,
						color = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.secondary,
						maxLines = 1,
						modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.5.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun LibraryStatusChip(
	label: String,
	dotColor: Color?,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val activeTint = dotColor ?: NemuriTheme.colors.cyan
	val pillShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) activeTint.copy(alpha = 0.16f) else Color.Transparent,
		shape = pillShape,
		modifier = Modifier
			.border(
				1.dp,
				if (selected) activeTint else NemuriTheme.colors.border.copy(alpha = 0.85f),
				pillShape,
			)
			.clip(pillShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(5.dp),
		) {
			if (dotColor != null) {
				Box(
					modifier = Modifier
						.size(6.5.dp)
						.background(dotColor, CircleShape),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = if (selected) activeTint else NemuriTheme.colors.textSecondary,
				maxLines = 1,
				softWrap = false,
			)
		}
	}
}

@Composable
private fun LibraryChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val pillShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = pillShape,
		modifier = Modifier
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				pillShape,
			)
			.clip(pillShape)
			.clickable(onClick = onClick),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
		)
	}
}

@Composable
private fun LibrarySheetCompactRow(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val rowShape = NemuriTheme.shape(12.dp)
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = rowShape,
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				rowShape,
			)
			.clip(rowShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
			)
			if (selected) {
				Icon(Icons.Default.Check, null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(18.dp))
			}
		}
	}
}

@Composable
private fun LibraryBadgeToggleRow(
	title: String,
	subtitle: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.clickable { onCheckedChange(!checked) }
			.padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium,
				color = NemuriTheme.colors.textPrimary,
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textMuted,
			)
		}
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			colors = SwitchDefaults.colors(
				checkedThumbColor = NemuriTheme.colors.onCyan,
				checkedTrackColor = NemuriTheme.colors.cyan,
			),
		)
	}
}

@Composable
private fun CardStyleDiagramCanvas(
	style: MangaCardStyle,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val accent = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary
	val frame = if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.55f) else NemuriTheme.colors.border
	Canvas(modifier = modifier) {
		val stroke = Stroke(width = 1.4.dp.toPx())
		when (style) {
			MangaCardStyle.COMPACT -> {
				val w = size.width * 0.62f
				val h = size.height * 0.90f
				val left = (size.width - w) * 0.5f
				val top = (size.height - h) * 0.5f
				drawRoundRect(frame, Offset(left, top), Size(w, h), CornerRadius(4.dp.toPx()), stroke)
				drawRoundRect(
					color = accent.copy(alpha = 0.45f),
					topLeft = Offset(left + 3.dp.toPx(), top + h * 0.68f),
					size = Size(w - 6.dp.toPx(), h * 0.22f),
					cornerRadius = CornerRadius(2.dp.toPx()),
				)
			}
			MangaCardStyle.COMFORTABLE -> {
				val w = size.width * 0.60f
				val coverH = size.height * 0.68f
				val left = (size.width - w) * 0.5f
				drawRoundRect(frame, Offset(left, size.height * 0.04f), Size(w, coverH), CornerRadius(4.dp.toPx()), stroke)
				drawRoundRect(
					color = accent,
					topLeft = Offset(left, size.height * 0.80f),
					size = Size(w * 0.90f, 3.dp.toPx()),
					cornerRadius = CornerRadius(1.5.dp.toPx()),
				)
				drawRoundRect(
					color = accent.copy(alpha = 0.55f),
					topLeft = Offset(left, size.height * 0.90f),
					size = Size(w * 0.62f, 2.5.dp.toPx()),
					cornerRadius = CornerRadius(1.5.dp.toPx()),
				)
			}
			MangaCardStyle.DETAILED_LIST -> {
				val rowH = size.height * 0.40f
				listOf(size.height * 0.06f, size.height * 0.54f).forEach { y ->
					drawRoundRect(frame, Offset(0f, y), Size(size.width, rowH), CornerRadius(3.dp.toPx()), stroke)
					val thumbW = rowH * 0.68f
					drawRoundRect(
						color = accent.copy(alpha = 0.45f),
						topLeft = Offset(3.dp.toPx(), y + 2.5.dp.toPx()),
						size = Size(thumbW, rowH - 5.dp.toPx()),
						cornerRadius = CornerRadius(2.dp.toPx()),
					)
					drawRoundRect(
						color = accent,
						topLeft = Offset(thumbW + 7.dp.toPx(), y + rowH * 0.30f),
						size = Size(size.width - thumbW - 11.dp.toPx(), 2.5.dp.toPx()),
						cornerRadius = CornerRadius(1.2.dp.toPx()),
					)
				}
			}
		}
	}
}

@Composable
private fun AspectRatioDiagramCanvas(
	ratio: CoverAspectRatio,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val accent = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary
	Canvas(modifier = modifier) {
		val h = size.height * 0.90f
		val w = (h * ratio.ratio).coerceAtMost(size.width * 0.94f)
		val left = (size.width - w) * 0.5f
		val top = (size.height - h) * 0.5f
		drawRoundRect(
			color = accent.copy(alpha = 0.22f),
			topLeft = Offset(left, top),
			size = Size(w, h),
			cornerRadius = CornerRadius(3.dp.toPx()),
		)
		drawRoundRect(
			color = accent,
			topLeft = Offset(left, top),
			size = Size(w, h),
			cornerRadius = CornerRadius(3.dp.toPx()),
			style = Stroke(width = 1.4.dp.toPx()),
		)
	}
}

@Composable
private fun GutterDensityDiagramCanvas(
	density: GridGutterDensity,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val accent = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary
	Canvas(modifier = modifier) {
		val gap = when (density) {
			GridGutterDensity.COMPACT -> 2.dp.toPx()
			GridGutterDensity.STANDARD -> 4.5.dp.toPx()
			GridGutterDensity.RELAXED -> 7.dp.toPx()
		}
		val pad = gap * 0.7f
		val cellW = (size.width - pad * 2f - gap) * 0.5f
		val cellH = (size.height - pad * 2f - gap) * 0.5f
		for (r in 0..1) {
			for (c in 0..1) {
				val x = pad + c * (cellW + gap)
				val y = pad + r * (cellH + gap)
				drawRoundRect(
					color = accent.copy(alpha = if (selected) 0.45f else 0.25f),
					topLeft = Offset(x, y),
					size = Size(cellW, cellH),
					cornerRadius = CornerRadius(2.dp.toPx()),
				)
				drawRoundRect(
					color = accent,
					topLeft = Offset(x, y),
					size = Size(cellW, cellH),
					cornerRadius = CornerRadius(2.dp.toPx()),
					style = Stroke(width = 1.1.dp.toPx()),
				)
			}
		}
	}
}
