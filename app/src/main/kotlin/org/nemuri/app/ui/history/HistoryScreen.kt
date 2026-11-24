package org.nemuri.app.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun HistoryScreen(
	onOpenDetails: (Manga) -> Unit,
	onResumeReading: (Manga, MangaHistory) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: HistoryViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var showOptionsSheet by remember { mutableStateOf(false) }
	var showClearDialog by remember { mutableStateOf(false) }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = uiState.query,
			onQueryChange = viewModel::setQuery,
			placeholder = "Search history…",
			onTuneClick = { showOptionsSheet = true },
			onSettingsClick = onOpenSettings,
		)

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
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
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
						items(items, key = { it.manga.id }) { item ->
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								MangaListRow(
									manga = item.manga,
									subtitle = item.timeSummary,
									progress = item.progressPercent,
									onClick = {
										MangaTransferCache.put(item.manga)
										onOpenDetails(item.manga)
									},
									onQuickActionClick = {
										MangaTransferCache.put(item.manga)
										onResumeReading(item.manga, item.history)
									},
									modifier = Modifier.weight(1f),
								)
								IconButton(onClick = { viewModel.removeEntry(item.manga) }) {
									Icon(
										imageVector = Icons.Default.DeleteOutline,
										contentDescription = "Remove from history",
										tint = NemuriTheme.colors.textDim,
										modifier = Modifier.size(19.dp),
									)
								}
							}
						}
					}
				}
			}
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
					ListSortOrder.ALPHABETICAL to "Alphabetical A–Z",
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
