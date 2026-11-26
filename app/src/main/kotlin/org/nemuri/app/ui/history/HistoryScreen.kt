package org.nemuri.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.listDragRangeSelect
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriEffectsSpring
import org.nemuri.app.ui.theme.nemuriSpatialSpring

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
	onOpenDetails: (Manga, String?) -> Unit,
	onResumeReading: (Manga, MangaHistory) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: HistoryViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val layoutPrefs = LocalLayoutStudioPrefs.current
	val isSelectionMode = uiState.selectedIds.isNotEmpty()
	val bottomPadding = rememberRootTabBottomPadding(extraBottom = if (isSelectionMode) 92.dp else 16.dp)
	val snackbarHostState = remember { SnackbarHostState() }
	var showOptionsSheet by remember { mutableStateOf(false) }
	var showClearDialog by remember { mutableStateOf(false) }

	BackHandler(enabled = isSelectionMode) {
		viewModel.clearSelection()
	}

	LaunchedEffect(viewModel) {
		viewModel.undoEvents.collect { event ->
			snackbarHostState.currentSnackbarData?.dismiss()
			val result = snackbarHostState.showSnackbar(
				message = event.message,
				actionLabel = "Undo",
				duration = SnackbarDuration.Short,
			)
			if (result == SnackbarResult.ActionPerformed) {
				viewModel.undoDelete(event.handle)
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
						TextButton(onClick = viewModel::removeSelectedEntries) {
							Text("Delete", color = NemuriTheme.colors.danger, fontWeight = FontWeight.Bold)
						}
					}
				}
			} else {
				NemuriTopSearchHeader(
					query = uiState.query,
					onQueryChange = viewModel::setQuery,
					placeholder = "Search history…",
					onTuneClick = { showOptionsSheet = true },
					onSettingsClick = onOpenSettings,
				)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 14.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				HistoryQuickFilter.entries.forEach { filter ->
					val selected = uiState.quickFilter == filter
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
							.clickable { viewModel.setQuickFilter(filter) },
					) {
						Text(
							text = filter.label,
							style = MaterialTheme.typography.labelMedium,
							color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
						)
					}
				}
			}

			when {
				!uiState.isLoaded -> {
					NemuriLoadingState(message = "Loading reading history…")
				}

				uiState.groups.isEmpty() -> {
					NemuriEmptyState(
						icon = Icons.Default.History,
						title = if (uiState.query.isBlank()) "No reading history yet" else "No matching history",
						subtitle = if (uiState.query.isBlank()) {
							"Chapters you read will be tracked here cleanly across manga and novels."
						} else {
							"Try another search query or switch quick filters."
						},
					)
				}

				else -> {
					val listState = rememberLazyListState()
					val safeRowCorner = layoutPrefs.cardCornerRadiusDp.coerceIn(8, 18)
					LazyColumn(
						state = listState,
						modifier = Modifier
							.fillMaxSize()
							.listDragRangeSelect(
								listState = listState,
								orderedIds = uiState.allVisibleIds,
								selectedIds = uiState.selectedIds,
								onSelectionChange = viewModel::setSelection,
							),
						contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						uiState.groups.forEach { (groupTitle, items) ->
							if (uiState.groupByDate) {
								item(key = "hdr:$groupTitle") {
									Text(
										text = groupTitle.uppercase(),
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.cyan,
										modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
									)
								}
							}
							items(items, key = { "${it.manga.id}_${it.history.updatedAt.toEpochMilli()}" }) { item ->
								val selected = item.manga.id in uiState.selectedIds
								val coverKey = "hist-${item.manga.id}"
								val dismissState = rememberSwipeToDismissBoxState(
									confirmValueChange = { target ->
										if (target == SwipeToDismissBoxValue.EndToStart && !isSelectionMode) {
											viewModel.removeEntry(item.manga)
											false
										} else {
											false
										}
									},
								)
								SwipeToDismissBox(
									state = dismissState,
									enableDismissFromStartToEnd = false,
									enableDismissFromEndToStart = !isSelectionMode,
									backgroundContent = {
										val isActiveSwipe = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
											dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
										Box(
											modifier = Modifier
												.fillMaxSize()
												.clip(RoundedCornerShape(safeRowCorner.dp))
												.background(
													if (isActiveSwipe) {
														NemuriTheme.colors.danger.copy(alpha = 0.24f)
													} else {
														Color.Transparent
													},
												)
												.border(
													width = 1.dp,
													color = if (isActiveSwipe) {
														NemuriTheme.colors.danger.copy(alpha = 0.55f)
													} else {
														Color.Transparent
													},
													shape = RoundedCornerShape(safeRowCorner.dp),
												)
												.padding(horizontal = 20.dp),
											contentAlignment = Alignment.CenterEnd,
										) {
											if (isActiveSwipe) {
												Row(
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(6.dp),
												) {
													Text(
														text = "Remove",
														style = MaterialTheme.typography.labelMedium,
														fontWeight = FontWeight.Bold,
														color = NemuriTheme.colors.danger,
													)
													Icon(
														imageVector = Icons.Default.DeleteOutline,
														contentDescription = "Remove from history",
														tint = NemuriTheme.colors.danger,
														modifier = Modifier.size(20.dp),
													)
												}
											}
										}
									},
								) {
									MangaListRow(
										manga = item.manga,
										subtitle = item.timeSummary,
										progress = item.progressPercent,
										isSelected = selected,
										sharedCoverKey = coverKey,
										onClick = {
											if (isSelectionMode) {
												viewModel.toggleSelection(item.manga.id)
											} else {
												MangaTransferCache.put(item.manga)
												onOpenDetails(item.manga, coverKey)
											}
										},
										onLongClick = { viewModel.toggleSelection(item.manga.id) },
										onQuickActionClick = if (isSelectionMode) {
											null
										} else {
											{
												MangaTransferCache.put(item.manga)
												onResumeReading(item.manga, item.history)
											}
										},
										modifier = Modifier.fillMaxWidth(),
									)
								}
							}
						}
					}
				}
			}
		}

		// Floating Contextual Multi-Select Action Bar
		AnimatedVisibility(
			visible = isSelectionMode,
			enter = slideInVertically(nemuriSpatialSpring()) { it / 2 } + fadeIn(nemuriEffectsSpring()),
			exit = slideOutVertically(nemuriSpatialSpring()) { it / 2 } + fadeOut(nemuriEffectsSpring()),
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(
					start = 16.dp,
					end = 16.dp,
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
						.padding(horizontal = 12.dp, vertical = 8.dp),
					horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
				) {
					HistoryContextualAction(
						icon = Icons.Default.SelectAll,
						label = "Select All",
						onClick = viewModel::selectAll,
					)
					HistoryContextualAction(
						icon = Icons.Default.DeleteOutline,
						label = "Delete Selected",
						tint = NemuriTheme.colors.danger,
						onClick = viewModel::removeSelectedEntries,
					)
					HistoryContextualAction(
						icon = Icons.Default.Close,
						label = "Cancel",
						tint = NemuriTheme.colors.textSecondary,
						onClick = viewModel::clearSelection,
					)
				}
			}
		}

		// Undo Snackbar Host positioned cleanly above the bottom dock / contextual bar
		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(
					start = 16.dp,
					end = 16.dp,
					bottom = if (isSelectionMode) 144.dp else if (layoutPrefs.isNavBarCompact) 74.dp else 84.dp,
				),
		) { data ->
			Snackbar(
				snackbarData = data,
				containerColor = NemuriTheme.colors.sheet,
				contentColor = NemuriTheme.colors.textPrimary,
				actionColor = NemuriTheme.colors.cyan,
				shape = RoundedCornerShape(14.dp),
			)
		}
	}

	if (showOptionsSheet) {
		NemuriModalSheet(
			title = "History Options",
			subtitle = "${uiState.totalCount} titles in history",
			onDismiss = { showOptionsSheet = false },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = "Group by Date",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Switch(
							checked = uiState.groupByDate,
							onCheckedChange = viewModel::setGroupByDate,
							colors = SwitchDefaults.colors(checkedTrackColor = NemuriTheme.colors.cyan),
						)
					}
				}

				Text(
					text = "SORT BY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				val sortChoices = listOf(
					ListSortOrder.LAST_READ to "Recently read",
					ListSortOrder.LONG_AGO_READ to "Oldest read",
					ListSortOrder.ALPHABETIC to "Alphabetical A–Z",
					ListSortOrder.PROGRESS to "Reading progress",
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
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = label,
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

				Spacer(modifier = Modifier.height(4.dp))
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.danger.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
						.clip(RoundedCornerShape(12.dp))
						.clickable {
							showOptionsSheet = false
							showClearDialog = true
						},
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.DeleteSweep,
							contentDescription = null,
							tint = NemuriTheme.colors.danger,
						)
						Text(
							text = "Clear Reading History…",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.danger,
							fontWeight = FontWeight.SemiBold,
						)
					}
				}
			}
		}
	}

	if (showClearDialog) {
		AlertDialog(
			onDismissRequest = { showClearDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			textContentColor = NemuriTheme.colors.textSecondary,
			title = { Text("Clear Reading History") },
			text = { Text("Choose whether to clear all entries or only titles not in your library.") },
			confirmButton = {
				TextButton(
					onClick = {
						showClearDialog = false
						viewModel.clearAllHistory()
					},
					colors = ButtonDefaults.textButtonColors(contentColor = NemuriTheme.colors.danger),
				) {
					Text("Clear All")
				}
			},
			dismissButton = {
				Row {
					TextButton(
						onClick = {
							showClearDialog = false
							viewModel.clearNonFavouriteHistory()
						},
					) {
						Text("Non-Library Only", color = NemuriTheme.colors.cyan)
					}
					TextButton(onClick = { showClearDialog = false }) {
						Text("Cancel", color = NemuriTheme.colors.textSecondary)
					}
				}
			},
		)
	}
}

@Composable
private fun HistoryContextualAction(
	icon: ImageVector,
	label: String,
	tint: Color = NemuriTheme.colors.textPrimary,
	onClick: () -> Unit,
) {
	Column(
		modifier = Modifier
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 6.dp),
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
