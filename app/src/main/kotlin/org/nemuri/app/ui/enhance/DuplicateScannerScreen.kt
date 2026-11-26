package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.ai.domain.SemanticPairResult
import org.koitharu.kotatsu.ai.domain.SemanticVerdict
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.theme.NemuriTheme
import java.util.Locale

@Composable
fun DuplicateScannerScreen(
	onOpenDetails: (Manga) -> Unit,
	onBack: () -> Unit,
	viewModel: DuplicateScannerViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val shownPairs = when (uiState.activeBucket) {
		DuplicateConfidenceBucket.EXACT_DUPLICATES -> uiState.exactPairs
		DuplicateConfidenceBucket.EDITIONS_VARIANTS -> uiState.editionVariantPairs
		DuplicateConfidenceBucket.NEEDS_REVIEW -> uiState.needsReviewPairs
	}

	SettingsScaffold(
		title = "Library Duplicate Scanner",
		subtitle = "${uiState.exactPairs.size} exact • ${uiState.editionVariantPairs.size} editions • ${uiState.needsReviewPairs.size} review",
		onBack = onBack,
		actions = {
			IconButton(
				onClick = viewModel::scanLibrary,
				enabled = !uiState.isScanning && !uiState.isScanningTier2 && !uiState.isMerging,
			) {
				if (uiState.isScanning || uiState.isScanningTier2 || uiState.isMerging) {
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
			// 3 Filter Tabs: Exact Duplicates | Editions & Variants | Needs Review
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				BucketChip(
					label = "Exact (${uiState.exactPairs.size})",
					selected = uiState.activeBucket == DuplicateConfidenceBucket.EXACT_DUPLICATES,
					onClick = { viewModel.selectBucket(DuplicateConfidenceBucket.EXACT_DUPLICATES) },
					modifier = Modifier.weight(1f),
				)
				BucketChip(
					label = "Editions (${uiState.editionVariantPairs.size})",
					selected = uiState.activeBucket == DuplicateConfidenceBucket.EDITIONS_VARIANTS,
					onClick = { viewModel.selectBucket(DuplicateConfidenceBucket.EDITIONS_VARIANTS) },
					modifier = Modifier.weight(1f),
				)
				BucketChip(
					label = "Needs Review (${uiState.needsReviewPairs.size})",
					selected = uiState.activeBucket == DuplicateConfidenceBucket.NEEDS_REVIEW,
					onClick = { viewModel.selectBucket(DuplicateConfidenceBucket.NEEDS_REVIEW) },
					modifier = Modifier.weight(1f),
				)
			}

			// Primary Winner Strategy + Check & Swap on Add + Reset Ignored Pairs
			Surface(
				color = NemuriTheme.colors.card,
				shape = RoundedCornerShape(14.dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp),
			) {
				Column(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = "PRIMARY WINNER STRATEGY (★ WINNER)",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
						)
						if (uiState.ignoredPairsCount > 0) {
							TextButton(
								onClick = viewModel::resetIgnoredPairs,
								contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
							) {
								Text(
									text = "Reset Ignored Pairs (${uiState.ignoredPairsCount})",
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.amber,
								)
							}
						}
					}

					Row(
						modifier = Modifier
							.fillMaxWidth()
							.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						DuplicateWinnerStrategy.entries.forEach { strategy ->
							val selected = uiState.winnerStrategy == strategy
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(999.dp),
								border = BorderStroke(
									1.dp,
									if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.clip(RoundedCornerShape(999.dp))
									.clickable { viewModel.setWinnerStrategy(strategy) },
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
										text = strategy.shortLabel,
										style = MaterialTheme.typography.labelSmall,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									)
								}
							}
						}
					}

					HorizontalDivider(color = NemuriTheme.colors.border.copy(alpha = 0.6f))

					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
							Text(
								text = "Check & Swap on Add",
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "Warn before adding existing series and rank ★ Winner automatically",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Switch(
							checked = uiState.isCheckOnAddEnabled,
							onCheckedChange = viewModel::setCheckOnAddEnabled,
							colors = SwitchDefaults.colors(checkedTrackColor = NemuriTheme.colors.cyan),
						)
					}
				}
			}

			when {
				uiState.isScanning -> NemuriLoadingState(message = "Scanning library for duplicates…")

				shownPairs.isEmpty() -> if (uiState.isScanningTier2 && uiState.activeBucket != DuplicateConfidenceBucket.EXACT_DUPLICATES) {
					NemuriLoadingState(message = "Checking edition variants & fuzzy matches…")
				} else {
					NemuriEmptyState(
						icon = Icons.Default.ContentCopy,
						title = "No ${uiState.activeBucket.label}",
						subtitle = when (uiState.activeBucket) {
							DuplicateConfidenceBucket.EXACT_DUPLICATES ->
								"No identical core titles or shared tracker IDs found in your library."
							DuplicateConfidenceBucket.EDITIONS_VARIANTS ->
								"No Colored, Uncensored, or Webcomic edition variants found."
							DuplicateConfidenceBucket.NEEDS_REVIEW ->
								"No fuzzy title matches requiring manual or AI review."
						},
					)
				}

				else -> LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					if (uiState.activeBucket == DuplicateConfidenceBucket.EXACT_DUPLICATES) {
						item {
							Surface(
								color = NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.border),
								modifier = Modifier.fillMaxWidth(),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 8.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									val selectedCount = uiState.selectedBatchKeys.size
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
									) {
										TextButton(
											onClick = if (selectedCount == shownPairs.size) {
												viewModel::clearBatchSelection
											} else {
												viewModel::selectAllExactPairs
											},
											enabled = !uiState.isMerging,
										) {
											Text(
												text = if (selectedCount == shownPairs.size) "Clear Selection" else "Select All Exact",
												color = NemuriTheme.colors.cyan,
											)
										}
									}
									if (selectedCount > 0) {
										Button(
											onClick = viewModel::requestBatchMerge,
											enabled = !uiState.isMerging,
											colors = ButtonDefaults.buttonColors(
												containerColor = NemuriTheme.colors.cyan,
												contentColor = NemuriTheme.colors.onCyan,
											),
											shape = RoundedCornerShape(10.dp),
										) {
											Text("Merge Selected ($selectedCount)")
										}
									} else {
										Text(
											text = "Ranked by ${uiState.winnerStrategy.shortLabel}",
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.textMuted,
										)
									}
								}
							}
						}
					}

					if (uiState.activeBucket == DuplicateConfidenceBucket.EDITIONS_VARIANTS) {
						item {
							Surface(
								color = NemuriTheme.colors.mergePurple.copy(alpha = 0.12f),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Text(
									text = "Editions & Variants (Colored, Uncensored, Webcomic) are isolated from batch merges so distinct releases are never merged accidentally. Tap Keep Both to silence a pair.",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(12.dp),
								)
							}
						}
					}

					if (uiState.activeBucket == DuplicateConfidenceBucket.NEEDS_REVIEW) {
						item {
							val unverifiedCount = shownPairs.count { it.key !in uiState.aiVerdicts }
							Surface(
								color = NemuriTheme.colors.amber.copy(alpha = 0.12f),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.4f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Column(
									modifier = Modifier.padding(12.dp),
									verticalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Text(
										text = "Needs Review: Fuzzy word-token matches (≥82% similarity). Sequels, seasons, spin-offs, and Manga/Novel pairs are already vetoed automatically.",
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.amber,
									)
									if (uiState.isAiEnabled && (unverifiedCount > 0 || uiState.isBatchAiRunning)) {
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.SpaceBetween,
											verticalAlignment = Alignment.CenterVertically,
										) {
											if (uiState.isBatchAiRunning) {
												Text(
													text = "AI verifying ${uiState.batchAiDone}/${uiState.batchAiTotal}…",
													style = MaterialTheme.typography.labelMedium,
													color = NemuriTheme.colors.mergePurple,
												)
												TextButton(onClick = viewModel::cancelBatchAi) {
													Text("Cancel", color = NemuriTheme.colors.danger)
												}
											} else {
												Text(
													text = "$unverifiedCount unchecked pair(s)",
													style = MaterialTheme.typography.labelSmall,
													color = NemuriTheme.colors.textMuted,
												)
												Button(
													onClick = viewModel::verifyNextFiveWithAi,
													colors = ButtonDefaults.buttonColors(
														containerColor = NemuriTheme.colors.mergePurple.copy(alpha = 0.22f),
														contentColor = NemuriTheme.colors.mergePurple,
													),
													shape = RoundedCornerShape(10.dp),
												) {
													Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
													Spacer(Modifier.width(6.dp))
													Text("AI Verify Next ${minOf(5, unverifiedCount)}")
												}
											}
										}
									}
								}
							}
						}
					}

					items(shownPairs, key = { it.key }) { pair ->
						val allowBatchSelect = uiState.activeBucket == DuplicateConfidenceBucket.EXACT_DUPLICATES && !pair.isEditionVariant
						DuplicatePairCard(
							pair = pair,
							enabled = !uiState.isMerging,
							allowBatchCheckbox = allowBatchSelect,
							isBatchSelected = allowBatchSelect && pair.key in uiState.selectedBatchKeys,
							onToggleBatchSelect = { viewModel.toggleBatchSelection(pair) },
							showAiAction = uiState.activeBucket == DuplicateConfidenceBucket.NEEDS_REVIEW && uiState.isAiEnabled,
							aiResult = uiState.aiVerdicts[pair.key],
							isVerifyingAi = pair.key in uiState.verifyingPairKeys,
							onAskAi = { viewModel.verifyPairWithAi(pair) },
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								onOpenDetails(manga)
							},
							onKeepFirst = { viewModel.requestMerge(pair, keepFirst = true) },
							onKeepSecond = { viewModel.requestMerge(pair, keepFirst = false) },
							onKeepBothIgnore = { viewModel.ignorePair(pair) },
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
							text = "Preserve furthest reading progress, bookmarks & tracker links",
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

	if (uiState.showBatchMergeConfirm) {
		var batchCopyProgress by remember { mutableStateOf(true) }
		val count = uiState.selectedBatchKeys.size
		AlertDialog(
			onDismissRequest = viewModel::cancelBatchMerge,
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Batch Consolidate $count Exact Pair(s)") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = "Keeps the ★ Winner (${uiState.winnerStrategy.shortLabel}) for each selected Exact Duplicate pair and removes the secondary copy. Editions & Variants are never touched.",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textSecondary,
					)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { batchCopyProgress = !batchCopyProgress },
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = batchCopyProgress,
							onCheckedChange = { batchCopyProgress = it },
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
						)
						Text(
							text = "Preserve furthest reading progress, bookmarks & tracker links",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = { viewModel.confirmBatchMerge(batchCopyProgress) },
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) { Text("Consolidate $count Pair(s)") }
			},
			dismissButton = {
				TextButton(onClick = viewModel::cancelBatchMerge) {
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
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun DuplicatePairCard(
	pair: DuplicatePairItem,
	enabled: Boolean,
	allowBatchCheckbox: Boolean,
	isBatchSelected: Boolean,
	onToggleBatchSelect: () -> Unit,
	showAiAction: Boolean,
	aiResult: SemanticPairResult?,
	isVerifyingAi: Boolean,
	onAskAi: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onKeepFirst: () -> Unit,
	onKeepSecond: () -> Unit,
	onKeepBothIgnore: () -> Unit,
) {
	val reasonBg = when (pair.bucket) {
		DuplicateConfidenceBucket.EXACT_DUPLICATES -> NemuriTheme.colors.tealContainer
		DuplicateConfidenceBucket.EDITIONS_VARIANTS -> NemuriTheme.colors.mergePurple.copy(alpha = 0.16f)
		DuplicateConfidenceBucket.NEEDS_REVIEW -> NemuriTheme.colors.amber.copy(alpha = 0.16f)
	}
	val reasonFg = when (pair.bucket) {
		DuplicateConfidenceBucket.EXACT_DUPLICATES -> NemuriTheme.colors.cyan
		DuplicateConfidenceBucket.EDITIONS_VARIANTS -> NemuriTheme.colors.mergePurple
		DuplicateConfidenceBucket.NEEDS_REVIEW -> NemuriTheme.colors.amber
	}

	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		border = BorderStroke(
			1.dp,
			if (isBatchSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
		),
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
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (allowBatchCheckbox) {
						Checkbox(
							checked = isBatchSelected,
							onCheckedChange = { onToggleBatchSelect() },
							enabled = enabled,
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
							modifier = Modifier.size(22.dp),
						)
					}
					Surface(
						color = reasonBg,
						shape = RoundedCornerShape(999.dp),
					) {
						Text(
							text = pair.reason,
							style = MaterialTheme.typography.labelSmall,
							color = reasonFg,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
						)
					}
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					if (showAiAction && aiResult == null) {
						TextButton(onClick = onAskAi, enabled = enabled && !isVerifyingAi) {
							if (isVerifyingAi) {
								CircularProgressIndicator(
									color = NemuriTheme.colors.mergePurple,
									strokeWidth = 2.dp,
									modifier = Modifier.size(14.dp),
								)
							} else {
								Text("Ask AI", color = NemuriTheme.colors.mergePurple)
							}
						}
					}
					TextButton(onClick = onKeepBothIgnore, enabled = enabled) {
						Text("Keep Both (Ignore Pair)", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textSecondary)
					}
				}
			}

			if (aiResult != null) {
				val (badgeLabel, badgeTint) = when (aiResult.verdict) {
					SemanticVerdict.SAME_SERIES -> "AI: Same Series" to NemuriTheme.colors.healthy
					SemanticVerdict.DIFFERENT_WORK -> "AI: Different Work" to NemuriTheme.colors.danger
					SemanticVerdict.UNCERTAIN -> "AI: Uncertain" to NemuriTheme.colors.amber
				}
				Surface(
					color = badgeTint.copy(alpha = 0.12f),
					shape = RoundedCornerShape(10.dp),
					border = BorderStroke(1.dp, badgeTint.copy(alpha = 0.45f)),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(10.dp),
						verticalArrangement = Arrangement.spacedBy(2.dp),
					) {
						Text(
							text = "$badgeLabel • ${aiResult.engineUsed}",
							style = MaterialTheme.typography.labelMedium,
							color = badgeTint,
						)
						Text(
							text = aiResult.reason,
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
						)
					}
				}
			}

			DuplicateSideRow(
				tag = "★ Winner",
				isWinner = true,
				showStandardEditionBadge = pair.isEditionVariant && pair.first.editionFlags.isEmpty(),
				entry = pair.first,
				onClick = { onOpenDetails(pair.first.manga) },
			)
			DuplicateSideRow(
				tag = "Entry B",
				isWinner = false,
				showStandardEditionBadge = pair.isEditionVariant && pair.second.editionFlags.isEmpty(),
				entry = pair.second,
				onClick = { onOpenDetails(pair.second.manga) },
			)

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Button(
					onClick = onKeepFirst,
					enabled = enabled,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.tealContainer,
						contentColor = NemuriTheme.colors.cyan,
					),
					shape = RoundedCornerShape(10.dp),
					modifier = Modifier.weight(1f),
				) {
					Text("Keep ★ Winner", maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				OutlinedButton(
					onClick = onKeepSecond,
					enabled = enabled,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					shape = RoundedCornerShape(10.dp),
					modifier = Modifier.weight(1f),
				) {
					Text("Keep Entry B", color = NemuriTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateSideRow(
	tag: String,
	isWinner: Boolean,
	showStandardEditionBadge: Boolean,
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
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
				itemVerticalAlignment = Alignment.CenterVertically,
			) {
				Surface(
					color = if (isWinner) NemuriTheme.colors.healthy.copy(alpha = 0.16f) else NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(6.dp),
				) {
					Text(
						text = tag,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = if (isWinner) NemuriTheme.colors.healthy else NemuriTheme.colors.textSecondary,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
				entry.editionFlags.forEach { flag ->
					Surface(
						color = NemuriTheme.colors.mergePurple.copy(alpha = 0.16f),
						shape = RoundedCornerShape(6.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f)),
					) {
						Text(
							text = flag.badgeText,
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.mergePurple,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				}
				if (showStandardEditionBadge) {
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(6.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
					) {
						Text(
							text = "📘 Standard",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textSecondary,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				}
				if (!entry.isSourceHealthy) {
					Surface(
						color = NemuriTheme.colors.amber.copy(alpha = 0.16f),
						shape = RoundedCornerShape(6.dp),
					) {
						Text(
							text = "Broken Source",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.amber,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				}
			}
			Text(
				text = entry.manga.title,
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
					if (entry.rating > 0f) {
						append(" • ★ ")
						append(String.format(Locale.US, "%.1f", entry.rating * 5f))
					}
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
