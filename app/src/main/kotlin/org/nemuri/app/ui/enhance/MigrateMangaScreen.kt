package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.settings.SettingsGroupCard
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.settings.SettingsToggleRow
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MigrateMangaScreen(
	mangaId: Long,
	onMigrated: (Manga) -> Unit,
	onBack: () -> Unit,
	viewModel: MigrateMangaViewModel = viewModel(),
) {
	LaunchedEffect(mangaId) {
		viewModel.bind(mangaId)
	}

	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	SettingsScaffold(
		title = "Migrate Source",
		subtitle = uiState.seed?.title,
		onBack = onBack,
	) {
		when {
			uiState.isLoadingSeed -> NemuriLoadingState(message = "Loading title details…")

			uiState.seed == null -> NemuriEmptyState(
				icon = Icons.Default.SwapHoriz,
				title = "Title not found",
				subtitle = "Go back and reload the title before migrating.",
			)

			else -> LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				item {
					SettingsGroupCard("Current Entry") {
						Row(
							modifier = Modifier.padding(12.dp),
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							MangaCoverImage(
								manga = uiState.seed!!,
								coverUrl = uiState.seed!!.coverUrl,
								modifier = Modifier
									.size(width = 48.dp, height = 68.dp)
									.clip(RoundedCornerShape(8.dp)),
							)
							Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
								Text(
									text = uiState.seed!!.title,
									style = MaterialTheme.typography.titleSmall,
									color = NemuriTheme.colors.textPrimary,
								)
								Text(
									text = "${uiState.seedSourceTitle} • ${uiState.seedChaptersCount} ch",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
						}
					}
				}

				item {
					SettingsGroupCard("Target Sources (${uiState.selectedSourceNames.size}/${uiState.availableSources.size})") {
						Column(
							modifier = Modifier.padding(14.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								val sourceQueryField = rememberSyncedTextField(uiState.sourceQuery, viewModel::setSourceQuery)
								OutlinedTextField(
									value = sourceQueryField.fieldValue,
									onValueChange = sourceQueryField::onValueChange,
									placeholder = { Text("Filter target sources…") },
									singleLine = true,
									modifier = Modifier.weight(1f),
								)
								Spacer(Modifier.width(8.dp))
								Column {
									TextButton(onClick = viewModel::selectAllSources) {
										Text("All", color = NemuriTheme.colors.cyan)
									}
									TextButton(onClick = viewModel::clearSources) {
										Text("None", color = NemuriTheme.colors.textMuted)
									}
								}
							}

							val filteredSources = uiState.availableSources.filter {
								uiState.sourceQuery.isBlank() ||
									it.title.contains(uiState.sourceQuery.trim(), ignoreCase = true)
							}
							FlowRow(
								horizontalArrangement = Arrangement.spacedBy(6.dp),
								verticalArrangement = Arrangement.spacedBy(6.dp),
							) {
								filteredSources.forEach { src ->
									val selected = src.name in uiState.selectedSourceNames
									Surface(
										color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
										shape = RoundedCornerShape(999.dp),
										border = BorderStroke(
											1.dp,
											if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										),
										modifier = Modifier
											.clip(RoundedCornerShape(999.dp))
											.clickable { viewModel.toggleSource(src.name) },
									) {
										Text(
											text = src.title,
											style = MaterialTheme.typography.labelSmall,
											color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
											modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
										)
									}
								}
							}
						}
					}
				}

				item {
					SettingsGroupCard {
						SettingsToggleRow(
							title = "Loose Title Match",
							subtitle = "Include subtitle/sequel variants when searching target sources",
							checked = uiState.looseMatch,
							onCheckedChange = viewModel::setLooseMatch,
						)
					}
				}

				item {
					Button(
						onClick = viewModel::searchAlternatives,
						enabled = !uiState.isSearching && !uiState.isMigrating && uiState.selectedSourceNames.isNotEmpty(),
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.cyan,
							contentColor = NemuriTheme.colors.onCyan,
						),
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						if (uiState.isSearching) {
							CircularProgressIndicator(
								color = NemuriTheme.colors.onCyan,
								strokeWidth = 2.dp,
								modifier = Modifier.size(18.dp),
							)
							Spacer(Modifier.width(8.dp))
							Text("Searching ${uiState.selectedSourceNames.size} source(s)…")
						} else {
							Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
							Spacer(Modifier.width(8.dp))
							Text("Search Selected Sources")
						}
					}
				}

				if (uiState.hasSearched && !uiState.isSearching && uiState.candidates.isEmpty()) {
					item {
						Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
							NemuriEmptyState(
								icon = Icons.Default.SearchOff,
								title = "No matching entries found",
								subtitle = if (uiState.aiSuggestedQueries.isNotEmpty()) {
									"Tried AI aliases (${uiState.aiSuggestedQueries.joinToString()}) via ${uiState.aiEngineUsed ?: "AI"}."
								} else if (uiState.isAiEnabled) {
									"Try enabling Loose Match, selecting more sources, or run single-title AI alias discovery."
								} else {
									"Try enabling Loose Match or selecting additional target sources."
								},
							)
							if (uiState.isAiEnabled) {
								Button(
									onClick = viewModel::runAiFallbackSearch,
									enabled = !uiState.isRunningAiFallback && !uiState.isMigrating,
									colors = ButtonDefaults.buttonColors(
										containerColor = NemuriTheme.colors.mergePurple.copy(alpha = 0.22f),
										contentColor = NemuriTheme.colors.mergePurple,
									),
									shape = RoundedCornerShape(12.dp),
									modifier = Modifier
										.fillMaxWidth()
										.border(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
								) {
									if (uiState.isRunningAiFallback) {
										CircularProgressIndicator(
											color = NemuriTheme.colors.mergePurple,
											strokeWidth = 2.dp,
											modifier = Modifier.size(18.dp),
										)
										Spacer(Modifier.width(8.dp))
										Text("Finding alternate titles with AI…")
									} else {
										Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
										Spacer(Modifier.width(8.dp))
										Text("Ask AI for Alternate Titles (1 Title)")
									}
								}
							}
						}
					}
				} else if (uiState.candidates.isNotEmpty()) {
					if (uiState.aiSuggestedQueries.isNotEmpty()) {
						item {
							Surface(
								color = NemuriTheme.colors.mergePurple.copy(alpha = 0.14f),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Text(
									text = "Matched via ${uiState.aiEngineUsed ?: "AI"} queries: ${uiState.aiSuggestedQueries.joinToString(" • ")}",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.mergePurple,
									modifier = Modifier.padding(10.dp),
								)
							}
						}
					}
					item {
						Text(
							text = "MATCHES (${uiState.candidates.size})",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
						)
					}
					items(uiState.candidates, key = { "${it.manga.source.name}:${it.manga.id}" }) { candidate ->
						val delta = candidate.chaptersCount - uiState.seedChaptersCount
						Surface(
							color = NemuriTheme.colors.card,
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.fillMaxWidth()
								.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
								.clip(RoundedCornerShape(14.dp))
								.clickable(enabled = !uiState.isMigrating) { viewModel.requestConfirm(candidate) },
						) {
							Row(
								modifier = Modifier.padding(12.dp),
								horizontalArrangement = Arrangement.spacedBy(12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								MangaCoverImage(
									manga = candidate.manga,
									coverUrl = candidate.manga.coverUrl,
									modifier = Modifier
										.size(width = 48.dp, height = 68.dp)
										.clip(RoundedCornerShape(8.dp)),
								)
								Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
									Text(
										text = candidate.manga.title,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "${candidate.sourceTitle} • ${candidate.chaptersCount} ch",
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.textMuted,
									)
									if (uiState.seedChaptersCount > 0) {
										val deltaLabel = when {
											delta > 0 -> "+$delta chapters vs current"
											delta < 0 -> "$delta chapters vs current"
											else -> "Same chapter count"
										}
										Text(
											text = deltaLabel,
											style = MaterialTheme.typography.labelSmall,
											color = if (delta >= 0) NemuriTheme.colors.healthy else NemuriTheme.colors.amber,
										)
									}
								}
								Button(
									onClick = { viewModel.requestConfirm(candidate) },
									enabled = !uiState.isMigrating,
									colors = ButtonDefaults.buttonColors(
										containerColor = NemuriTheme.colors.tealContainer,
										contentColor = NemuriTheme.colors.cyan,
									),
									shape = RoundedCornerShape(10.dp),
								) {
									Text("Migrate")
								}
							}
						}
					}
				}
			}
		}
	}

	uiState.pendingConfirm?.let { candidate ->
		var carryProgress by remember(candidate) { mutableStateOf(true) }
		AlertDialog(
			onDismissRequest = viewModel::dismissConfirm,
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Confirm Source Migration") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = "From: ${uiState.seedSourceTitle} (${uiState.seedChaptersCount} ch)\nTo: ${candidate.sourceTitle} (${candidate.chaptersCount} ch)",
						style = MaterialTheme.typography.bodyMedium,
						color = NemuriTheme.colors.textSecondary,
					)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { carryProgress = !carryProgress },
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = carryProgress,
							onCheckedChange = { carryProgress = it },
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
						)
						Text(
							text = "Migrate reading history, bookmarks & tracker links",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textPrimary,
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = { viewModel.confirmMigration(carryProgress, onMigrated) },
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) { Text("Confirm") }
			},
			dismissButton = {
				TextButton(onClick = viewModel::dismissConfirm) {
					Text("Cancel", color = NemuriTheme.colors.textMuted)
				}
			},
		)
	}
}
