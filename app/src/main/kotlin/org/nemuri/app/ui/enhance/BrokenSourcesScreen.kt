package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.model.chaptersCount
import org.koitharu.kotatsu.core.model.getTitle
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun BrokenSourcesScreen(
	onOpenManualMigrate: (Long) -> Unit,
	onBack: () -> Unit,
	viewModel: BrokenSourcesViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	val stepTitle = when (uiState.step) {
		BrokenWizardStep.SOURCES -> "1. Select Sources to Repair"
		BrokenWizardStep.TARGETS -> "2. Prioritize Target Sources"
		BrokenWizardStep.REVIEW -> "3. Review & Confirm"
	}

	SettingsScaffold(
		title = "Fix-It / Smart Replace",
		subtitle = stepTitle,
		onBack = {
			when (uiState.step) {
				BrokenWizardStep.SOURCES -> onBack()
				BrokenWizardStep.TARGETS -> viewModel.goToSources()
				BrokenWizardStep.REVIEW -> viewModel.goToTargets()
			}
		},
	) {
		when (uiState.step) {
			BrokenWizardStep.SOURCES -> {
				Column(modifier = Modifier.fillMaxSize()) {
					LazyColumn(
						modifier = Modifier.weight(1f),
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						item {
							Text(
								text = "Broken or uninstalled sources are pre-selected. You can also check working sources to migrate off them.",
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						if (uiState.librarySources.isEmpty()) {
							item {
								NemuriEmptyState(
									icon = Icons.Default.Build,
									title = "No external sources in library",
									subtitle = "Add titles from installed sources to monitor source health.",
								)
							}
						} else {
							items(uiState.librarySources, key = { it.sourceName }) { item ->
								val selected = item.sourceName in uiState.selectedSourceNames
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
									shape = RoundedCornerShape(14.dp),
									modifier = Modifier
										.fillMaxWidth()
										.border(
											1.dp,
											if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											RoundedCornerShape(14.dp),
										)
										.clip(RoundedCornerShape(14.dp))
										.clickable { viewModel.toggleSourceSelection(item.sourceName) },
								) {
									Row(
										modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
										verticalAlignment = Alignment.CenterVertically,
									) {
										Checkbox(
											checked = selected,
											onCheckedChange = { viewModel.toggleSourceSelection(item.sourceName) },
											colors = CheckboxDefaults.colors(
												checkedColor = NemuriTheme.colors.cyan,
												checkmarkColor = NemuriTheme.colors.onCyan,
											),
										)
										Spacer(Modifier.width(6.dp))
										Column(modifier = Modifier.weight(1f)) {
											Text(
												text = item.displayTitle,
												style = MaterialTheme.typography.titleSmall,
												color = NemuriTheme.colors.textPrimary,
											)
											Text(
												text = "${item.mangaCount} title(s)",
												style = MaterialTheme.typography.bodySmall,
												color = NemuriTheme.colors.textMuted,
											)
										}
										Surface(
											color = if (item.isUnavailable) {
												NemuriTheme.colors.danger.copy(alpha = 0.16f)
											} else {
												NemuriTheme.colors.healthy.copy(alpha = 0.16f)
											},
											shape = RoundedCornerShape(999.dp),
										) {
											Text(
												text = if (item.isUnavailable) "Unavailable" else "Installed",
												style = MaterialTheme.typography.labelSmall,
												color = if (item.isUnavailable) NemuriTheme.colors.danger else NemuriTheme.colors.healthy,
												modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
											)
										}
									}
								}
							}
						}
					}

					Surface(color = NemuriTheme.colors.surface, tonalElevation = 4.dp) {
						Button(
							onClick = viewModel::goToTargets,
							enabled = uiState.selectedSourceNames.isNotEmpty(),
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.padding(16.dp),
						) {
							Text("Next: Pick Target Sources (${uiState.selectedSourceNames.size})")
						}
					}
				}
			}

			BrokenWizardStep.TARGETS -> {
				Column(modifier = Modifier.fillMaxSize()) {
					LazyColumn(
						modifier = Modifier.weight(1f),
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						item {
							Text(
								text = "Smart Replace checks selected sources in priority order and verifies chapters actually load before proposing a replacement.",
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						itemsIndexed(uiState.orderedTargets, key = { _, item -> item.name }) { idx, target ->
							val selected = target.name in uiState.selectedTargetNames
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(
										1.dp,
										if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										RoundedCornerShape(14.dp),
									)
									.clip(RoundedCornerShape(14.dp))
									.clickable { viewModel.toggleTarget(target.name) },
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Checkbox(
										checked = selected,
										onCheckedChange = { viewModel.toggleTarget(target.name) },
										colors = CheckboxDefaults.colors(
											checkedColor = NemuriTheme.colors.cyan,
											checkmarkColor = NemuriTheme.colors.onCyan,
										),
									)
									Text(
										text = target.title,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
										modifier = Modifier.weight(1f),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									IconButton(
										onClick = { viewModel.moveTarget(idx, -1) },
										enabled = idx > 0,
									) {
										Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = NemuriTheme.colors.textSecondary)
									}
									IconButton(
										onClick = { viewModel.moveTarget(idx, +1) },
										enabled = idx < uiState.orderedTargets.lastIndex,
									) {
										Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = NemuriTheme.colors.textSecondary)
									}
								}
							}
						}
					}

					Surface(color = NemuriTheme.colors.surface, tonalElevation = 4.dp) {
						Button(
							onClick = viewModel::startSmartReplaceScan,
							enabled = uiState.selectedTargetNames.isNotEmpty(),
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.padding(16.dp),
						) {
							Text("Scan & Propose Replacements")
						}
					}
				}
			}

			BrokenWizardStep.REVIEW -> {
				val readySelectedCount = uiState.proposals.count {
					it.isSelected && !it.isApplied && it.workingReplacement != null
				}
				Column(modifier = Modifier.fillMaxSize()) {
					if (uiState.isScanning) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(
								modifier = Modifier.padding(14.dp),
								verticalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = "Testing healthy replacements (${uiState.scanDone}/${uiState.scanTotal})…",
										style = MaterialTheme.typography.labelLarge,
										color = NemuriTheme.colors.cyan,
									)
									TextButton(onClick = viewModel::cancelScan) {
										Text("Stop", color = NemuriTheme.colors.amber)
									}
								}
								val frac = if (uiState.scanTotal > 0) uiState.scanDone.toFloat() / uiState.scanTotal else 0f
								LinearProgressIndicator(
									progress = { frac },
									color = NemuriTheme.colors.cyan,
									trackColor = NemuriTheme.colors.surfaceElevated,
									modifier = Modifier.fillMaxWidth(),
								)
							}
						}
					}

					LazyColumn(
						modifier = Modifier.weight(1f),
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						items(uiState.proposals, key = { it.seed.id }) { proposal ->
							SmartReplaceProposalCard(
								proposal = proposal,
								enabled = !uiState.isApplying,
								onToggleSelected = { viewModel.toggleProposalSelected(proposal.seed.id) },
								onSelectCandidate = { cand -> viewModel.selectCandidateForProposal(proposal.seed.id, cand) },
								onConfirmOne = { viewModel.confirmSingleProposal(proposal) },
								onManualMigrate = { onOpenManualMigrate(proposal.seed.id) },
							)
						}
					}

					if (readySelectedCount > 0) {
						Surface(color = NemuriTheme.colors.surface, tonalElevation = 4.dp) {
							Button(
								onClick = viewModel::openBatchConfirmDialog,
								enabled = !uiState.isApplying,
								colors = ButtonDefaults.buttonColors(
									containerColor = NemuriTheme.colors.cyan,
									contentColor = NemuriTheme.colors.onCyan,
								),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier
									.fillMaxWidth()
									.padding(16.dp),
							) {
								Text("Confirm Selected ($readySelectedCount)")
							}
						}
					}
				}
			}
		}
	}

	if (uiState.showBatchConfirmDialog) {
		val count = uiState.proposals.count { it.isSelected && !it.isApplied && it.workingReplacement != null }
		AlertDialog(
			onDismissRequest = viewModel::dismissBatchConfirmDialog,
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Confirm Smart Replace") },
			text = {
				Text(
					text = "Migrate $count selected title(s) to their proposed working sources while preserving favourites, reading history, bookmarks, and trackers?",
					color = NemuriTheme.colors.textMuted,
				)
			},
			confirmButton = {
				Button(
					onClick = viewModel::confirmBatchMigration,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) { Text("Migrate ($count)") }
			},
			dismissButton = {
				TextButton(onClick = viewModel::dismissBatchConfirmDialog) {
					Text("Cancel", color = NemuriTheme.colors.textMuted)
				}
			},
		)
	}
}

@Composable
private fun SmartReplaceProposalCard(
	proposal: SmartReplaceProposal,
	enabled: Boolean,
	onToggleSelected: () -> Unit,
	onSelectCandidate: (org.koitharu.kotatsu.parsers.model.Manga) -> Unit,
	onConfirmOne: () -> Unit,
	onManualMigrate: () -> Unit,
) {
	var showAltMenu by remember { mutableStateOf(false) }
	val context = LocalContext.current

	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		border = BorderStroke(
			1.dp,
			when {
				proposal.isApplied -> NemuriTheme.colors.healthy.copy(alpha = 0.6f)
				proposal.isSelected -> NemuriTheme.colors.cyan.copy(alpha = 0.55f)
				else -> NemuriTheme.colors.border
			},
		),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (proposal.workingReplacement != null && !proposal.isApplied) {
					Checkbox(
						checked = proposal.isSelected,
						onCheckedChange = { onToggleSelected() },
						enabled = enabled,
						colors = CheckboxDefaults.colors(
							checkedColor = NemuriTheme.colors.cyan,
							checkmarkColor = NemuriTheme.colors.onCyan,
						),
					)
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = proposal.seed.title,
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = "From: ${proposal.seedSourceTitle} (${proposal.seedChaptersCount} ch)",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
				if (proposal.isApplied) {
					Icon(
						imageVector = Icons.Default.CheckCircle,
						contentDescription = "Migrated",
						tint = NemuriTheme.colors.healthy,
					)
				}
			}

			if (proposal.workingReplacement != null) {
				Text(
					text = "Proposed: ${proposal.replacementSourceTitle} (${proposal.replacementChaptersCount} ch)",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.cyan,
				)
				if (!proposal.isApplied) {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						if (proposal.alternateCandidates.size > 1) {
							Box {
								OutlinedButton(
									onClick = { showAltMenu = true },
									border = BorderStroke(1.dp, NemuriTheme.colors.border),
								) {
									Text("Switch (${proposal.alternateCandidates.size})", color = NemuriTheme.colors.textSecondary)
								}
								DropdownMenu(expanded = showAltMenu, onDismissRequest = { showAltMenu = false }) {
									proposal.alternateCandidates.forEach { cand ->
										DropdownMenuItem(
											text = { Text("${cand.source.getTitle(context)} (${cand.chaptersCount()} ch)") },
											onClick = {
												showAltMenu = false
												onSelectCandidate(cand)
											},
										)
									}
								}
							}
						}
						Button(
							onClick = onConfirmOne,
							enabled = enabled,
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.tealContainer,
								contentColor = NemuriTheme.colors.cyan,
							),
							shape = RoundedCornerShape(10.dp),
						) {
							Text("1-Tap Confirm")
						}
					}
				}
			} else if (!proposal.isApplied) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "No automatic working match found.",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.amber,
						modifier = Modifier.weight(1f),
					)
					TextButton(onClick = onManualMigrate) {
						Text("Manual Migrate →", color = NemuriTheme.colors.cyan)
					}
				}
			}

			if (!proposal.errorMessage.isNullOrBlank()) {
				Text(
					text = proposal.errorMessage,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.danger,
				)
			}
		}
	}
}
