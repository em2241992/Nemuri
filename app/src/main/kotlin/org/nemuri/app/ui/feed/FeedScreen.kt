package org.nemuri.app.ui.feed

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun FeedScreen(
	onOpenDetails: (Manga) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: FeedViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var showClearDialog by remember { mutableStateOf(false) }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = uiState.query,
			onQueryChange = viewModel::setQuery,
			placeholder = "Search updates feed…",
			onSettingsClick = onOpenSettings,
		)

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			FeedFilterPill(
				label = "All",
				selected = !uiState.unreadOnly,
				onClick = { viewModel.setUnreadOnly(false) },
			)
			FeedFilterPill(
				label = if (uiState.unreadCount > 0) "Unread (${uiState.unreadCount})" else "Unread",
				selected = uiState.unreadOnly,
				onClick = { viewModel.setUnreadOnly(true) },
			)

			Spacer(modifier = Modifier.weight(1f))

			if (uiState.unreadCount > 0) {
				IconButton(
					onClick = viewModel::markAllRead,
					modifier = Modifier.size(34.dp),
				) {
					Icon(
						imageVector = Icons.Default.DoneAll,
						contentDescription = "Mark all read",
						tint = NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(18.dp),
					)
				}
			}

			if (uiState.totalCount > 0) {
				IconButton(
					onClick = { showClearDialog = true },
					modifier = Modifier.size(34.dp),
				) {
					Icon(
						imageVector = Icons.Default.DeleteSweep,
						contentDescription = "Clear feed",
						tint = NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(18.dp),
					)
				}
			}

			Surface(
				color = if (uiState.isTrackerRunning) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier
					.border(
						1.dp,
						if (uiState.isTrackerRunning) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
						RoundedCornerShape(999.dp),
					)
					.clip(RoundedCornerShape(999.dp))
					.clickable(onClick = viewModel::checkForUpdatesNow),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					if (uiState.isTrackerRunning) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.cyan,
							strokeWidth = 2.dp,
							modifier = Modifier.size(13.dp),
						)
						Text(
							text = "Syncing",
							style = MaterialTheme.typography.labelMedium,
							color = NemuriTheme.colors.cyan,
						)
					} else {
						Icon(
							imageVector = Icons.Default.Refresh,
							contentDescription = "Check updates",
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(15.dp),
						)
						Text(
							text = "Check",
							style = MaterialTheme.typography.labelMedium,
							color = NemuriTheme.colors.textSecondary,
						)
					}
				}
			}
		}

		when {
			!uiState.isLoaded -> {
				NemuriLoadingState(message = "Loading updates feed…")
			}

			uiState.totalCount == 0 -> {
				val message = when {
					uiState.query.isNotBlank() -> "No updates match \"${uiState.query}\"."
					uiState.unreadOnly -> "You're caught up on all unread updates."
					else -> "New chapters detected for tracked library titles appear here."
				}
				NemuriEmptyState(
					icon = Icons.Default.DynamicFeed,
					title = "No updates yet",
					subtitle = message,
					actionLabel = if (!uiState.isTrackerRunning && uiState.query.isBlank() && !uiState.unreadOnly) {
						"Check Now"
					} else {
						null
					},
					onAction = if (!uiState.isTrackerRunning && uiState.query.isBlank() && !uiState.unreadOnly) {
						viewModel::checkForUpdatesNow
					} else {
						null
					},
				)
			}

			else -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					uiState.groups.forEach { (dateLabel, entries) ->
						item(key = "feed_header_$dateLabel") {
							Text(
								text = "$dateLabel (${entries.size})",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
								modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp),
							)
						}

						items(entries, key = { it.logId }) { entry ->
							FeedEntryCard(
								entry = entry,
								onOpenDetails = {
									MangaTransferCache.put(entry.logItem.manga)
									onOpenDetails(entry.logItem.manga)
								},
								onOpenLatestChapter = {
									val chapterId = entry.firstChapterId
									if (chapterId != null) {
										MangaTransferCache.put(entry.logItem.manga)
										onOpenChapter(entry.logItem.manga, chapterId)
									} else {
										MangaTransferCache.put(entry.logItem.manga)
										onOpenDetails(entry.logItem.manga)
									}
								},
								onDownload = { viewModel.downloadUpdates(entry) },
								onMarkRead = {
									if (entry.logItem.isNew) {
										viewModel.markMangaRead(entry.logItem.manga.id)
									} else {
										viewModel.removeLogEntry(entry.logId)
									}
								},
							)
						}
					}
				}
			}
		}
	}

	if (showClearDialog) {
		AlertDialog(
			onDismissRequest = { showClearDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			title = { Text("Clear updates feed", color = NemuriTheme.colors.textPrimary) },
			text = {
				Text(
					"Remove all update log entries from the feed? This will not delete chapters or remove library titles.",
					color = NemuriTheme.colors.textSecondary,
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.clearFeedHistory()
						showClearDialog = false
					},
					colors = ButtonDefaults.textButtonColors(contentColor = NemuriTheme.colors.danger),
				) {
					Text("Clear Feed")
				}
			},
			dismissButton = {
				TextButton(onClick = { showClearDialog = false }) {
					Text("Cancel", color = NemuriTheme.colors.textSecondary)
				}
			},
		)
	}
}

@Composable
private fun FeedFilterPill(
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
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			if (selected) {
				Icon(
					imageVector = Icons.Default.Check,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(13.dp),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
			)
		}
	}
}

@Composable
private fun FeedEntryCard(
	entry: FeedEntryItem,
	onOpenDetails: () -> Unit,
	onOpenLatestChapter: () -> Unit,
	onDownload: () -> Unit,
	onMarkRead: () -> Unit,
) {
	val manga = entry.logItem.manga
	val isNew = entry.logItem.isNew
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				if (isNew) NemuriTheme.colors.cyan.copy(alpha = 0.45f) else NemuriTheme.colors.border,
				RoundedCornerShape(14.dp),
			)
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onOpenLatestChapter),
	) {
		Row(
			modifier = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.size(width = 52.dp, height = 74.dp)
					.clip(RoundedCornerShape(10.dp))
					.clickable(onClick = onOpenDetails),
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(3.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = manga.title,
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (isNew && entry.newChaptersCount > 0) {
						Surface(
							color = NemuriTheme.colors.cyan,
							shape = RoundedCornerShape(6.dp),
						) {
							NonWrappingBadgeText(
								text = "+${entry.newChaptersCount}",
								color = NemuriTheme.colors.onCyan,
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
							)
						}
					}
				}
				Text(
					text = entry.chaptersSummary,
					style = MaterialTheme.typography.bodySmall,
					color = if (isNew) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textSecondary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "${entry.sourceTitle} • ${entry.timeLabel}",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Column(
				horizontalAlignment = Alignment.End,
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				IconButton(
					onClick = onDownload,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						imageVector = Icons.Default.Download,
						contentDescription = "Download chapter(s)",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(18.dp),
					)
				}
				IconButton(
					onClick = onMarkRead,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						imageVector = Icons.Default.MarkEmailRead,
						contentDescription = if (isNew) "Mark read" else "Dismiss",
						tint = if (isNew) NemuriTheme.colors.healthy else NemuriTheme.colors.textDim,
						modifier = Modifier.size(18.dp),
					)
				}
			}
		}
	}
}
