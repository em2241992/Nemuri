package org.nemuri.app.ui.downloads

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.rememberPushScreenBottomPadding
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun DownloadsScreen(
	onBack: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	viewModel: DownloadsViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val bottomPadding = rememberPushScreenBottomPadding(24.dp)

	Column(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Downloads",
					style = MaterialTheme.typography.titleLarge,
					color = NemuriTheme.colors.textPrimary,
				)
				val activeCount = uiState.activeItems.size + uiState.queuedItems.size
				if (activeCount > 0) {
					Text(
						text = "$activeCount active or queued",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
				}
			}
			if (uiState.hasCancelableWork) {
				TextButton(onClick = viewModel::cancelAll) {
					Text("Cancel All", color = NemuriTheme.colors.danger)
				}
			}
			if (uiState.hasCompletedWork) {
				IconButton(onClick = viewModel::clearCompleted) {
					Icon(
						imageVector = Icons.Default.ClearAll,
						contentDescription = "Clear finished",
						tint = NemuriTheme.colors.textSecondary,
					)
				}
			}
		}

		HorizontalDivider(color = NemuriTheme.colors.border)

		when {
			!uiState.isLoaded -> {
				NemuriLoadingState(message = "Loading downloads queue…")
			}

			uiState.activeItems.isEmpty() && uiState.queuedItems.isEmpty() && uiState.historyItems.isEmpty() -> {
				NemuriEmptyState(
					icon = Icons.Default.Download,
					title = "No downloads yet",
					subtitle = "Download chapters from manga or novel details to read offline.",
				)
			}

			else -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					if (uiState.activeItems.isNotEmpty()) {
						item(key = "dl_header_active") {
							DownloadSectionHeader("In Progress (${uiState.activeItems.size})")
						}
						items(uiState.activeItems, key = { it.id.toString() }) { item ->
							DownloadItemCard(
								item = item,
								onOpenDetails = {
									item.manga?.let { m ->
										MangaTransferCache.put(m)
										onOpenDetails(m)
									}
								},
								onPause = { viewModel.pause(item.id) },
								onResume = { viewModel.resume(item.id) },
								onSkip = { viewModel.skip(item.id) },
								onCancel = { viewModel.cancel(item.id) },
								onRetry = { viewModel.retry(item.id) },
								onDelete = { viewModel.deleteEntry(item.id) },
							)
						}
					}

					if (uiState.queuedItems.isNotEmpty()) {
						item(key = "dl_header_queued") {
							DownloadSectionHeader("Queued (${uiState.queuedItems.size})")
						}
						items(uiState.queuedItems, key = { it.id.toString() }) { item ->
							DownloadItemCard(
								item = item,
								onOpenDetails = {
									item.manga?.let { m ->
										MangaTransferCache.put(m)
										onOpenDetails(m)
									}
								},
								onPause = { viewModel.pause(item.id) },
								onResume = { viewModel.resume(item.id) },
								onSkip = { viewModel.skip(item.id) },
								onCancel = { viewModel.cancel(item.id) },
								onRetry = { viewModel.retry(item.id) },
								onDelete = { viewModel.deleteEntry(item.id) },
							)
						}
					}

					if (uiState.historyItems.isNotEmpty()) {
						item(key = "dl_header_history") {
							DownloadSectionHeader("Completed & Recent (${uiState.historyItems.size})")
						}
						items(uiState.historyItems, key = { it.id.toString() }) { item ->
							DownloadItemCard(
								item = item,
								onOpenDetails = {
									item.manga?.let { m ->
										MangaTransferCache.put(m)
										onOpenDetails(m)
									}
								},
								onPause = { viewModel.pause(item.id) },
								onResume = { viewModel.resume(item.id) },
								onSkip = { viewModel.skip(item.id) },
								onCancel = { viewModel.cancel(item.id) },
								onRetry = { viewModel.retry(item.id) },
								onDelete = { viewModel.deleteEntry(item.id) },
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun DownloadSectionHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.labelSmall,
		fontWeight = FontWeight.SemiBold,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier.padding(top = 4.dp, start = 2.dp),
	)
}

@Composable
private fun DownloadItemCard(
	item: DownloadEntryItem,
	onOpenDetails: () -> Unit,
	onPause: () -> Unit,
	onResume: () -> Unit,
	onSkip: () -> Unit,
	onCancel: () -> Unit,
	onRetry: () -> Unit,
	onDelete: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
			.clip(RoundedCornerShape(14.dp))
			.clickable(enabled = item.manga != null, onClick = onOpenDetails),
	) {
		Column(
			modifier = Modifier.padding(10.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				MangaCoverImage(
					manga = item.manga,
					coverUrl = item.manga?.coverUrl,
					modifier = Modifier
						.size(width = 48.dp, height = 66.dp)
						.clip(RoundedCornerShape(8.dp)),
				)
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(3.dp),
				) {
					Text(
						text = item.mangaTitle,
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					val statusColor = when (item.workState) {
						WorkInfo.State.FAILED -> NemuriTheme.colors.danger
						WorkInfo.State.SUCCEEDED -> NemuriTheme.colors.healthy
						WorkInfo.State.RUNNING -> NemuriTheme.colors.cyan
						else -> NemuriTheme.colors.textMuted
					}
					Text(
						text = item.statusLabel,
						style = MaterialTheme.typography.labelSmall,
						color = statusColor,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Row(verticalAlignment = Alignment.CenterVertically) {
					when (item.workState) {
						WorkInfo.State.RUNNING -> {
							IconButton(
								onClick = if (item.isPaused) onResume else onPause,
								modifier = Modifier.size(34.dp),
							) {
								Icon(
									imageVector = if (item.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
									contentDescription = if (item.isPaused) "Resume" else "Pause",
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
							IconButton(onClick = onSkip, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.SkipNext,
									contentDescription = "Skip chapter",
									tint = NemuriTheme.colors.textSecondary,
									modifier = Modifier.size(18.dp),
								)
							}
							IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.Cancel,
									contentDescription = "Cancel",
									tint = NemuriTheme.colors.danger,
									modifier = Modifier.size(18.dp),
								)
							}
						}

						WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
							IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.Cancel,
									contentDescription = "Cancel",
									tint = NemuriTheme.colors.danger,
									modifier = Modifier.size(18.dp),
								)
							}
						}

						WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
							IconButton(onClick = onRetry, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.Refresh,
									contentDescription = "Retry",
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
							IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.DeleteOutline,
									contentDescription = "Remove",
									tint = NemuriTheme.colors.textDim,
									modifier = Modifier.size(18.dp),
								)
							}
						}

						WorkInfo.State.SUCCEEDED -> {
							IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
								Icon(
									imageVector = Icons.Default.DeleteOutline,
									contentDescription = "Remove",
									tint = NemuriTheme.colors.textDim,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}
			}

			if (item.workState == WorkInfo.State.RUNNING) {
				if (item.isIndeterminate || item.max <= 0) {
					LinearProgressIndicator(
						color = NemuriTheme.colors.cyan,
						trackColor = NemuriTheme.colors.border,
						modifier = Modifier
							.fillMaxWidth()
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp)),
					)
				} else {
					LinearProgressIndicator(
						progress = { (item.progress.toFloat() / item.max.toFloat()).coerceIn(0f, 1f) },
						color = NemuriTheme.colors.cyan,
						trackColor = NemuriTheme.colors.border,
						modifier = Modifier
							.fillMaxWidth()
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp)),
					)
				}
			}
		}
	}
}
