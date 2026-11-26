package org.nemuri.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaComfortableGridCard
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.gridDragRangeSelect
import org.nemuri.app.ui.components.listDragRangeSelect
import org.nemuri.app.ui.components.pinchToStepColumns
import org.nemuri.app.ui.components.rememberAppSettings
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

			// Categories Row
			if (uiState.categories.isNotEmpty() || uiState.items.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState())
						.padding(horizontal = 16.dp, vertical = 4.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (uiState.showAllCategoryTab || uiState.categories.isEmpty()) {
						LibraryChip(
							label = "All",
							selected = uiState.selectedCategoryId == LibraryViewModel.ALL_CATEGORIES_ID,
							onClick = { viewModel.selectCategory(LibraryViewModel.ALL_CATEGORIES_ID) },
						)
					}
					uiState.categories.forEach { cat ->
						LibraryChip(
							label = cat.title,
							selected = uiState.selectedCategoryId == cat.id,
							onClick = { viewModel.selectCategory(cat.id) },
						)
					}
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						modifier = Modifier
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(999.dp))
							.clip(RoundedCornerShape(999.dp))
							.clickable {
								newCategoryName = ""
								showCreateCategoryDialog = true
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Icon(
								imageVector = Icons.Default.Add,
								contentDescription = "New category",
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(16.dp),
							)
							Text(
								text = "Category",
								style = MaterialTheme.typography.labelMedium,
								color = NemuriTheme.colors.cyan,
							)
						}
					}
				}
			}

			// Quick Filters
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 16.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				LibraryQuickFilter.entries.forEach { filter ->
					LibraryChip(
						label = filter.label,
						selected = uiState.quickFilter == filter,
						onClick = { viewModel.setQuickFilter(filter) },
					)
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
				}

				else -> {
					val gridState = rememberLazyGridState()
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
		NemuriModalSheet(
			title = "Library Display",
			subtitle = "${uiState.items.size} title(s)",
			onDismiss = { showDisplaySheet = false },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text = "CARD STYLE",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					listOf(
						Triple(MangaCardStyle.COMPACT, ListMode.GRID, Icons.Default.GridView) to "Compact",
						Triple(MangaCardStyle.COMFORTABLE, ListMode.GRID, Icons.Default.ViewAgenda) to "Comfortable",
						Triple(MangaCardStyle.DETAILED_LIST, ListMode.LIST, Icons.Default.ViewList) to "List Row",
					).forEach { (triple, label) ->
						val (style, listMode, icon) = triple
						val selected = effectiveCardStyle == style
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.weight(1f)
								.border(
									1.dp,
									if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									RoundedCornerShape(12.dp),
								)
								.clip(RoundedCornerShape(12.dp))
								.clickable {
									appSettings.mangaCardStyle = style
									appSettings.isTitleOverCover = style != MangaCardStyle.COMFORTABLE
									viewModel.setListMode(listMode)
								},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
								horizontalArrangement = Arrangement.Center,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Icon(icon, null, tint = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
								Spacer(modifier = Modifier.width(4.dp))
								Text(
									text = label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
									maxLines = 1,
									softWrap = false,
								)
							}
						}
					}
				}

				if (effectiveCardStyle != MangaCardStyle.DETAILED_LIST) {
					Text(
						text = if (isLandscape) "LANDSCAPE COLUMNS" else "PORTRAIT COLUMNS",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					val colChoices = if (isLandscape) listOf(4, 5, 6, 7, 8) else listOf(2, 3, 4, 5, 6)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						colChoices.forEach { cols ->
							LibraryChip(
								label = "$cols",
								selected = gridColumns == cols,
								onClick = {
									if (isLandscape) {
										viewModel.setLandscapeColumns(cols)
									} else {
										viewModel.setPortraitColumns(cols)
									}
								},
							)
						}
					}

					Text(
						text = "CORNER RADIUS",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf(0, 8, 12, 16, 20).forEach { radius ->
							LibraryChip(
								label = "${radius}dp",
								selected = layoutPrefs.cardCornerRadiusDp == radius,
								onClick = { appSettings.cardCornerRadiusDp = radius },
							)
						}
					}
				}

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
					val selected = uiState.sortOrder == order
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(12.dp),
							)
							.clip(RoundedCornerShape(12.dp))
							.clickable { viewModel.setSortOrder(order) },
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
private fun LibraryChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(999.dp),
		modifier = Modifier
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				RoundedCornerShape(999.dp),
			)
			.clip(RoundedCornerShape(999.dp))
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
