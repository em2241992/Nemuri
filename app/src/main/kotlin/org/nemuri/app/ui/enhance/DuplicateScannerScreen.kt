package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun DuplicateScannerScreen(
	onOpenDetails: (Manga) -> Unit,
	onBack: () -> Unit,
	viewModel: DuplicateScannerViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val shownPairs = when (uiState.activeBucket) {
		DuplicateConfidenceBucket.HIGH_CONFIDENCE -> uiState.highConfidencePairs
		DuplicateConfidenceBucket.REVIEW_CAREFULLY -> uiState.reviewCarefullyPairs
	}

	SettingsScaffold(
		title = "Library Duplicate Scanner",
		subtitle = "${uiState.highConfidencePairs.size} exact • ${uiState.reviewCarefullyPairs.size} review",
		onBack = onBack,
		actions = {
			IconButton(
				onClick = viewModel::scanLibrary,
				enabled = !uiState.isScanning && !uiState.isMerging,
			) {
				if (uiState.isScanning || uiState.isMerging) {
					CircularProgressIndicator(
						color = NemuriTheme.colors.cyan,
						strokeWidth = 2.dp,
						modifier = Modifier.size(18.dp),
					)
				} else {
					Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = NemuriTheme.colors.cyan)
				}
			}
		},
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 10.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				val highSelected = uiState.activeBucket == DuplicateConfidenceBucket.HIGH_CONFIDENCE
				BucketChip(
					label = "High Confidence (${uiState.highConfidencePairs.size})",
					selected = highSelected,
					onClick = { viewModel.selectBucket(DuplicateConfidenceBucket.HIGH_CONFIDENCE) },
					modifier = Modifier.weight(1f),
				)
				BucketChip(
					label = "Review (${uiState.reviewCarefullyPairs.size})",
					selected = !highSelected,
					onClick = { viewModel.selectBucket(DuplicateConfidenceBucket.REVIEW_CAREFULLY) },
					modifier = Modifier.weight(1f),
				)
			}

			when {
				uiState.isScanning -> NemuriLoadingState(message = "Scanning library for duplicates…")

				shownPairs.isEmpty() -> NemuriEmptyState(
					icon = Icons.Default.ContentCopy,
					title = "No ${uiState.activeBucket.label} duplicates",
					subtitle = if (uiState.activeBucket == DuplicateConfidenceBucket.HIGH_CONFIDENCE) {
						"No identical titles or shared tracker IDs found in your library."
					} else {
						"No ambiguous subtitle/sequel matches to review."
					},
				)

				else -> LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 36.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					if (uiState.activeBucket == DuplicateConfidenceBucket.REVIEW_CAREFULLY) {
						item {
							Surface(
								color = NemuriTheme.colors.amber.copy(alpha = 0.12f),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.4f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Text(
									text = "Careful Review: Substring or loose matches may be sequels or spin-offs. Confirm carefully before merging. Phase 5B will unlock capped AI semantic verification here.",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.amber,
									modifier = Modifier.padding(12.dp),
								)
							}
						}
					}

					items(shownPairs, key = { it.key }) { pair ->
						DuplicatePairCard(
							pair = pair,
							enabled = !uiState.isMerging,
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								onOpenDetails(manga)
							},
							onKeepFirst = { viewModel.requestMerge(pair, keepFirst = true) },
							onKeepSecond = { viewModel.requestMerge(pair, keepFirst = false) },
							onDismiss = { viewModel.dismissPair(pair.key) },
						)
					}
				}
			}
		}
	}

	uiState.pendingMerge?.let { pending ->
		var copyProgress by remember(pending) { mutableStateOf(true) }
		val keep = if (pending.keepFirst) pending.pair.first else pending.pair.second
		val discard = if (pending.keepFirst) pending.pair.second else pending.pair.first

		AlertDialog(
			onDismissRequest = viewModel::cancelMerge,
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Confirm Duplicate Merge") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = "Keep: ${keep.manga.title} (${keep.sourceTitle}, ${keep.chaptersCount} ch)",
						style = MaterialTheme.typography.bodyMedium,
						color = NemuriTheme.colors.healthy,
					)
					Text(
						text = "Remove: ${discard.manga.title} (${discard.sourceTitle}, ${discard.chaptersCount} ch)",
						style = MaterialTheme.typography.bodyMedium,
						color = NemuriTheme.colors.danger,
					)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { copyProgress = !copyProgress },
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = copyProgress,
							onCheckedChange = { copyProgress = it },
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
						)
						Text(
							text = "Merge reading progress, bookmarks & tracker links",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = { viewModel.confirmMerge(copyProgress) },
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) { Text("Merge & Remove") }
			},
			dismissButton = {
				TextButton(onClick = viewModel::cancelMerge) {
					Text("Cancel", color = NemuriTheme.colors.textMuted)
				}
			},
		)
	}
}

@Composable
private fun BucketChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
		modifier = modifier
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
			textAlign = androidx.compose.ui.text.style.TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun DuplicatePairCard(
	pair: DuplicatePairItem,
	enabled: Boolean,
	onOpenDetails: (Manga) -> Unit,
	onKeepFirst: () -> Unit,
	onKeepSecond: () -> Unit,
	onDismiss: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier.padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Surface(
					color = if (pair.bucket == DuplicateConfidenceBucket.HIGH_CONFIDENCE) {
						NemuriTheme.colors.tealContainer
					} else {
						NemuriTheme.colors.amber.copy(alpha = 0.16f)
					},
					shape = RoundedCornerShape(999.dp),
				) {
					Text(
						text = pair.reason,
						style = MaterialTheme.typography.labelSmall,
						color = if (pair.bucket == DuplicateConfidenceBucket.HIGH_CONFIDENCE) {
							NemuriTheme.colors.cyan
						} else {
							NemuriTheme.colors.amber
						},
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					)
				}
				TextButton(onClick = onDismiss, enabled = enabled) {
					Text("Dismiss", color = NemuriTheme.colors.textDim)
				}
			}

			DuplicateSideRow("Entry A", pair.first) { onOpenDetails(pair.first.manga) }
			DuplicateSideRow("Entry B", pair.second) { onOpenDetails(pair.second.manga) }

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				OutlinedButton(
					onClick = onKeepFirst,
					enabled = enabled,
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)),
					modifier = Modifier.weight(1f),
				) {
					Text("Keep A", color = NemuriTheme.colors.cyan)
				}
				OutlinedButton(
					onClick = onKeepSecond,
					enabled = enabled,
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)),
					modifier = Modifier.weight(1f),
				) {
					Text("Keep B", color = NemuriTheme.colors.cyan)
				}
			}
		}
	}
}

@Composable
private fun DuplicateSideRow(
	tag: String,
	entry: DuplicateEntrySummary,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.clickable(onClick = onClick)
			.padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		MangaCoverImage(
			manga = entry.manga,
			coverUrl = entry.manga.coverUrl,
			modifier = Modifier
				.size(width = 44.dp, height = 62.dp)
				.clip(RoundedCornerShape(8.dp)),
		)
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = "$tag • ${entry.manga.title}",
				style = MaterialTheme.typography.titleSmall,
				color = NemuriTheme.colors.textPrimary,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = buildString {
					append(entry.sourceTitle)
					append(" • ${entry.chaptersCount} ch")
					if (entry.progressPercent >= 0) append(" • ${entry.progressPercent}% read")
				},
				style = MaterialTheme.typography.bodySmall,
				color = NemuriTheme.colors.textMuted,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (entry.categoryTitles.isNotEmpty()) {
				Text(
					text = entry.categoryTitles.joinToString(),
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textDim,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}
