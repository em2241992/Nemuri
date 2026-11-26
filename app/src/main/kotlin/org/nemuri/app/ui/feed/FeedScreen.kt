package org.nemuri.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.SnappingSwipeRow
import org.nemuri.app.ui.components.cornerSafeBadgeInset
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriSharedCover

@Composable
fun FeedScreen(
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onOpenSettings: () -> Unit,
	onResumeReading: (Manga, MangaHistory) -> Unit = { manga, history ->
		onOpenChapter(manga, history.chapterId)
	},
	viewModel: FeedViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val layoutPrefs = LocalLayoutStudioPrefs.current
	val bottomPadding = rememberRootTabBottomPadding()
	var showClearDialog by remember { mutableStateOf(false) }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = uiState.query,
			onQueryChange = viewModel::setQuery,
			placeholder = if (uiState.isHubMode) "Search hub, library & updates…" else "Search updates feed…",
			onSettingsClick = onOpenSettings,
		)

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// 1-tap Hub <-> Classic mode switch
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(999.dp))
					.clip(RoundedCornerShape(999.dp))
					.clickable { viewModel.setHubMode(!uiState.isHubMode) },
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Icon(
						imageVector = if (uiState.isHubMode) {
							Icons.Default.ViewAgenda
						} else {
							Icons.AutoMirrored.Filled.FormatListBulleted
						},
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(14.dp),
					)
					Text(
						text = if (uiState.isHubMode) "Hub" else "Classic",
						style = MaterialTheme.typography.labelMedium,
						color = NemuriTheme.colors.cyan,
					)
				}
			}

			if (!uiState.isHubMode) {
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
			}

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

			if (!uiState.isHubMode && uiState.totalCount > 0) {
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
				NemuriLoadingState(message = "Loading reading hub…")
			}

			uiState.isHubMode -> {
				val hubEmpty = uiState.continueReading.isEmpty() &&
					uiState.hubUpdates.isEmpty() &&
					uiState.recentlyAdded.isEmpty()

				if (hubEmpty) {
					NemuriEmptyState(
						icon = Icons.Default.DynamicFeed,
						title = if (uiState.query.isNotBlank()) "No hub matches" else "Your Feed Hub is ready",
						subtitle = if (uiState.query.isNotBlank()) {
							"No history, updates, or library titles match \"${uiState.query}\"."
						} else {
							"Titles you read, add to your library, or update will appear here automatically."
						},
						actionLabel = if (!uiState.isTrackerRunning && uiState.query.isBlank()) "Check Updates Now" else null,
						onAction = if (!uiState.isTrackerRunning && uiState.query.isBlank()) viewModel::checkForUpdatesNow else null,
					)
				} else {
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
						verticalArrangement = Arrangement.spacedBy(18.dp),
					) {
						if (uiState.continueReading.isNotEmpty()) {
							item(key = "hub_continue_header") {
								HubSectionHeader(
									title = "CONTINUE READING",
									subtitle = "Tap cover to resume where you left off",
								)
							}
							item(key = "hub_continue_row") {
								LazyRow(
									contentPadding = PaddingValues(horizontal = 16.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									items(uiState.continueReading, key = { "cont:${it.manga.id}" }) { item ->
										val shareKey = "feed-cont-${item.manga.id}"
										ContinueReadingCard(
											item = item,
											cornerRadiusDp = layoutPrefs.cardCornerRadiusDp,
											aspectRatio = layoutPrefs.coverAspectRatio.ratio,
											sharedCoverKey = shareKey,
											onResume = {
												MangaTransferCache.put(item.manga)
												onResumeReading(item.manga, item.history)
											},
											onOpenDetails = {
												MangaTransferCache.put(item.manga)
												onOpenDetails(item.manga, shareKey)
											},
										)
									}
								}
							}
						}

						if (uiState.hubUpdates.isNotEmpty()) {
							item(key = "hub_updates_header") {
								HubSectionHeader(
									title = if (uiState.unreadCount > 0) "UPDATES (${uiState.unreadCount} NEW)" else "RECENT UPDATES",
									actionLabel = "Classic View →",
									onAction = { viewModel.setHubMode(false) },
								)
							}
							items(uiState.hubUpdates, key = { "hub_upd:${it.logId}" }) { entry ->
								val shareKey = "feed-hub-${entry.logId}"
								Box(
									modifier = Modifier
										.animateItem()
										.padding(horizontal = 16.dp),
								) {
									SnappingSwipeRow(
										rightLabel = if (entry.logItem.isNew) "Mark Read" else "Dismiss",
										rightIcon = Icons.Default.MarkEmailRead,
										onSwipeRight = {
											if (entry.logItem.isNew) {
												viewModel.markMangaRead(entry.logItem.manga.id)
											} else {
												viewModel.removeLogEntry(entry.logId)
											}
										},
										leftLabel = "Download",
										onSwipeLeft = { viewModel.downloadUpdates(entry) },
									) {
										FeedEntryCard(
											entry = entry,
											sharedCoverKey = shareKey,
											onOpenDetails = {
												MangaTransferCache.put(entry.logItem.manga)
												onOpenDetails(entry.logItem.manga, shareKey)
											},
											onOpenLatestChapter = {
												val chapterId = entry.firstChapterId
												MangaTransferCache.put(entry.logItem.manga)
												if (chapterId != null) {
													onOpenChapter(entry.logItem.manga, chapterId)
												} else {
													onOpenDetails(entry.logItem.manga, shareKey)
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

						if (uiState.recentlyAdded.isNotEmpty()) {
							item(key = "hub_recent_header") {
								HubSectionHeader(
									title = "RECENTLY ADDED",
									subtitle = "Latest titles saved to your library",
								)
							}
							item(key = "hub_recent_row") {
								LazyRow(
									contentPadding = PaddingValues(horizontal = 16.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									items(uiState.recentlyAdded, key = { "rec:${it.id}" }) { manga ->
										val shareKey = "feed-rec-${manga.id}"
										RecentLibraryCard(
											manga = manga,
											cornerRadiusDp = layoutPrefs.cardCornerRadiusDp,
											aspectRatio = layoutPrefs.coverAspectRatio.ratio,
											sharedCoverKey = shareKey,
											onClick = {
												MangaTransferCache.put(manga)
												onOpenDetails(manga, shareKey)
											},
										)
									}
								}
							}
						}
					}
				}
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
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
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
							val shareKey = "feed-cls-${entry.logId}"
							Box(modifier = Modifier.animateItem()) {
								SnappingSwipeRow(
									rightLabel = if (entry.logItem.isNew) "Mark Read" else "Dismiss",
									rightIcon = Icons.Default.MarkEmailRead,
									onSwipeRight = {
										if (entry.logItem.isNew) {
											viewModel.markMangaRead(entry.logItem.manga.id)
										} else {
											viewModel.removeLogEntry(entry.logId)
										}
									},
									leftLabel = "Download",
									onSwipeLeft = { viewModel.downloadUpdates(entry) },
								) {
									FeedEntryCard(
										entry = entry,
										sharedCoverKey = shareKey,
										onOpenDetails = {
											MangaTransferCache.put(entry.logItem.manga)
											onOpenDetails(entry.logItem.manga, shareKey)
										},
										onOpenLatestChapter = {
											val chapterId = entry.firstChapterId
											if (chapterId != null) {
												MangaTransferCache.put(entry.logItem.manga)
												onOpenChapter(entry.logItem.manga, chapterId)
											} else {
												MangaTransferCache.put(entry.logItem.manga)
												onOpenDetails(entry.logItem.manga, shareKey)
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
private fun HubSectionHeader(
	title: String,
	subtitle: String? = null,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
				color = NemuriTheme.colors.cyan,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textDim,
				)
			}
		}
		if (!actionLabel.isNullOrBlank() && onAction != null) {
			Text(
				text = actionLabel,
				style = MaterialTheme.typography.labelMedium,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier
					.clip(RoundedCornerShape(8.dp))
					.clickable(onClick = onAction)
					.padding(horizontal = 6.dp, vertical = 4.dp),
			)
		}
	}
}

@Composable
private fun ContinueReadingCard(
	item: ContinueReadingItem,
	cornerRadiusDp: Int,
	aspectRatio: Float,
	sharedCoverKey: String,
	onResume: () -> Unit,
	onOpenDetails: () -> Unit,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val coverShape = RoundedCornerShape(safeCorner.dp)
	Column(
		modifier = Modifier
			.width(126.dp)
			.clickable(onClick = onResume),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(aspectRatio)
				.clip(coverShape)
				.border(1.dp, NemuriTheme.colors.border, coverShape),
		) {
			MangaCoverImage(
				manga = item.manga,
				coverUrl = item.manga.coverUrl,
				modifier = Modifier
					.fillMaxSize()
					.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
			)
			Surface(
				color = NemuriTheme.colors.dock.copy(alpha = 0.88f),
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(cornerSafeBadgeInset(safeCorner)),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(3.dp),
				) {
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = "Resume",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(12.dp),
					)
					Text(
						text = item.progressPercentLabel,
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textPrimary,
					)
				}
			}
			LinearProgressIndicator(
				progress = { item.history.percent.coerceIn(0f, 1f) },
				color = NemuriTheme.colors.cyan,
				trackColor = NemuriTheme.colors.surfaceElevated,
				modifier = Modifier
					.fillMaxWidth()
					.height(3.dp)
					.align(Alignment.BottomCenter),
			)
		}
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(8.dp))
				.clickable(onClick = onOpenDetails)
				.padding(horizontal = 2.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = item.manga.title,
				style = MaterialTheme.typography.labelMedium,
				color = NemuriTheme.colors.textPrimary,
				minLines = 2,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = item.sourceTitle,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textDim,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun RecentLibraryCard(
	manga: Manga,
	cornerRadiusDp: Int,
	aspectRatio: Float,
	sharedCoverKey: String,
	onClick: () -> Unit,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val coverShape = RoundedCornerShape(safeCorner.dp)
	Column(
		modifier = Modifier
			.width(110.dp)
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		MangaCoverImage(
			manga = manga,
			coverUrl = manga.coverUrl,
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(aspectRatio)
				.clip(coverShape)
				.border(1.dp, NemuriTheme.colors.border, coverShape)
				.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
		)
		Text(
			text = manga.title,
			style = MaterialTheme.typography.labelMedium,
			color = NemuriTheme.colors.textPrimary,
			minLines = 2,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 2.dp),
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
	sharedCoverKey: String,
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
					.nemuriSharedCover(sharedCoverKey, 10.dp)
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
