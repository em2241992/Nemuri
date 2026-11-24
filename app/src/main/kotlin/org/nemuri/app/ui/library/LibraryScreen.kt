package org.nemuri.app.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
	onOpenDetails: (Manga) -> Unit,
	onOpenExplore: () -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: LibraryViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var showDisplaySheet by remember { mutableStateOf(false) }
	var showCategoryPickerSheet by remember { mutableStateOf(false) }
	var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
	var newCategoryName by rememberSaveable { mutableStateOf("") }

	val isSelectionMode = uiState.selectedIds.isNotEmpty()
	BackHandler(enabled = isSelectionMode) {
		viewModel.clearSelection()
	}

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
					IconButton(onClick = { showCategoryPickerSheet = true }) {
						Icon(
							imageVector = Icons.Default.CreateNewFolder,
							contentDescription = "Add to category",
							tint = NemuriTheme.colors.textSecondary,
						)
					}
					IconButton(onClick = viewModel::downloadSelected) {
						Icon(
							imageVector = Icons.Default.Download,
							contentDescription = "Download",
							tint = NemuriTheme.colors.textSecondary,
						)
					}
					IconButton(onClick = viewModel::markSelectedUpdatesRead) {
						Icon(
							imageVector = Icons.Default.DoneAll,
							contentDescription = "Mark read",
							tint = NemuriTheme.colors.textSecondary,
						)
					}
					IconButton(onClick = viewModel::removeSelectedFromLibrary) {
						Icon(
							imageVector = Icons.Default.DeleteOutline,
							contentDescription = "Remove",
							tint = NemuriTheme.colors.danger,
						)
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
					.padding(horizontal = 14.dp, vertical = 4.dp),
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
				.padding(horizontal = 14.dp, vertical = 4.dp),
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

			uiState.listMode == ListMode.LIST || uiState.listMode == ListMode.DETAILED_LIST -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					items(uiState.items, key = { it.manga.id }) { item ->
						val selected = item.manga.id in uiState.selectedIds
						MangaListRow(
							manga = item.manga,
							subtitle = item.sourceTitle,
							unreadCount = item.unreadCount,
							progress = item.progress,
							onClick = {
								if (isSelectionMode) {
									viewModel.toggleSelection(item.manga.id)
								} else {
									MangaTransferCache.put(item.manga)
									onOpenDetails(item.manga)
								}
							},
							modifier = Modifier
								.then(
									if (selected) {
										Modifier.border(2.dp, NemuriTheme.colors.cyan, RoundedCornerShape(14.dp))
									} else {
										Modifier
									},
								)
								.combinedClickable(
									onClick = {
										if (isSelectionMode) {
											viewModel.toggleSelection(item.manga.id)
										} else {
											MangaTransferCache.put(item.manga)
											onOpenDetails(item.manga)
										}
									},
									onLongClick = { viewModel.toggleSelection(item.manga.id) },
								),
						)
					}
				}
			}

			else -> {
				LazyVerticalGrid(
					columns = GridCells.Fixed(uiState.gridColumns),
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					items(uiState.items, key = { it.manga.id }) { item ->
						val selected = item.manga.id in uiState.selectedIds
						Box(
							modifier = Modifier
								.clip(RoundedCornerShape(14.dp))
								.combinedClickable(
									onClick = {
										if (isSelectionMode) {
											viewModel.toggleSelection(item.manga.id)
										} else {
											MangaTransferCache.put(item.manga)
											onOpenDetails(item.manga)
										}
									},
									onLongClick = { viewModel.toggleSelection(item.manga.id) },
								),
						) {
							MangaCompactGridCard(
								manga = item.manga,
								unreadCount = item.unreadCount,
								isCaughtUp = item.isCaughtUp,
								isDownloaded = item.isDownloaded,
								progress = item.progress,
								isSelected = selected,
								onClick = {
									if (isSelectionMode) {
										viewModel.toggleSelection(item.manga.id)
									} else {
										MangaTransferCache.put(item.manga)
										onOpenDetails(item.manga)
									}
								},
							)
						}
					}
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
					text = "LAYOUT",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					Surface(
						color = if (uiState.listMode == ListMode.GRID || uiState.listMode == ListMode.COVER_ONLY) {
							NemuriTheme.colors.tealContainer
						} else {
							NemuriTheme.colors.surfaceElevated
						},
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.weight(1f)
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
							.clip(RoundedCornerShape(12.dp))
							.clickable { viewModel.setListMode(ListMode.GRID) },
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							horizontalArrangement = Arrangement.Center,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Icon(Icons.Default.GridView, null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(18.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Grid", style = MaterialTheme.typography.titleSmall, color = NemuriTheme.colors.textPrimary)
						}
					}
					Surface(
						color = if (uiState.listMode == ListMode.LIST || uiState.listMode == ListMode.DETAILED_LIST) {
							NemuriTheme.colors.tealContainer
						} else {
							NemuriTheme.colors.surfaceElevated
						},
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.weight(1f)
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
							.clip(RoundedCornerShape(12.dp))
							.clickable { viewModel.setListMode(ListMode.LIST) },
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							horizontalArrangement = Arrangement.Center,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Icon(Icons.Default.ViewList, null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(18.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("List", style = MaterialTheme.typography.titleSmall, color = NemuriTheme.colors.textPrimary)
						}
					}
				}

				if (uiState.listMode == ListMode.GRID || uiState.listMode == ListMode.COVER_ONLY) {
					Text(
						text = "GRID COLUMNS",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf(2, 3, 4, 5).forEach { cols ->
							LibraryChip(
								label = "$cols cols",
								selected = uiState.gridColumns == cols,
								onClick = { viewModel.setGridColumns(cols) },
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
