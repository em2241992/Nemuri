package org.nemuri.app.ui.explore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.core.parser.favicon.faviconUri
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.search.domain.SearchKind
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchScreen(
	initialQuery: String = "",
	onBack: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onOpenSourceBrowse: (sourceName: String, query: String) -> Unit,
	viewModel: GlobalSearchViewModel = viewModel(key = "global_search:$initialQuery"),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(initialQuery) {
		viewModel.bind(initialQuery)
	}

	var retryTargetSource by remember { mutableStateOf<MangaSource?>(null) }
	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { success ->
		val source = retryTargetSource
		retryTargetSource = null
		if (success && source != null) {
			viewModel.retrySource(source)
		}
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { success ->
		val source = retryTargetSource
		retryTargetSource = null
		if (success && source != null) {
			viewModel.retrySource(source)
		}
	}

	var showErrors by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background)
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
		// Search Header
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.weight(1f)
					.height(44.dp)
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Icon(
						imageVector = Icons.Default.Search,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(18.dp),
					)
					Box(modifier = Modifier.weight(1f)) {
						if (uiState.query.isEmpty()) {
							Text(
								text = "Search across enabled sources…",
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textDim,
								maxLines = 1,
							)
						}
						BasicTextField(
							value = uiState.query,
							onValueChange = viewModel::setQuery,
							singleLine = true,
							textStyle = MaterialTheme.typography.bodyMedium.copy(color = NemuriTheme.colors.textPrimary),
							cursorBrush = SolidColor(NemuriTheme.colors.cyan),
							keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
							keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
							modifier = Modifier.fillMaxWidth(),
						)
					}
					if (uiState.query.isNotEmpty()) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Clear",
							tint = NemuriTheme.colors.textMuted,
							modifier = Modifier
								.size(18.dp)
								.clickable { viewModel.submitSearch("") },
						)
					}
				}
			}
			Surface(
				color = NemuriTheme.colors.cyan,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.height(44.dp)
					.clip(RoundedCornerShape(12.dp))
					.clickable { viewModel.submitSearch() },
			) {
				Box(
					modifier = Modifier.padding(horizontal = 14.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = "Search",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.onCyan,
					)
				}
			}
		}

		// Search Kind & Scope Filter Chips
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState())
				.padding(horizontal = 12.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			SearchScopeFilter.entries.forEach { scope ->
				SearchChip(
					label = scope.label,
					selected = uiState.scopeFilter == scope,
					onClick = { viewModel.setScopeFilter(scope) },
				)
			}
			SearchKind.entries.forEach { kind ->
				SearchChip(
					label = when (kind) {
						SearchKind.SIMPLE -> "Default"
						SearchKind.TITLE -> "Title"
						SearchKind.AUTHOR -> "Author"
						SearchKind.TAG -> "Tag/Genre"
					},
					selected = uiState.searchKind == kind,
					onClick = { viewModel.setSearchKind(kind) },
				)
			}
		}

		if (uiState.isSearching && uiState.totalCount > 0) {
			LinearProgressIndicator(
				progress = { (uiState.completedCount.toFloat() / uiState.totalCount.toFloat()).coerceIn(0f, 1f) },
				color = NemuriTheme.colors.cyan,
				trackColor = NemuriTheme.colors.border,
				modifier = Modifier
					.fillMaxWidth()
					.height(2.5.dp),
			)
		} else {
			HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(top = 4.dp))
		}

		if (uiState.activeQuery.isBlank()) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				if (uiState.recentQueries.isNotEmpty()) {
					item {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "RECENT SEARCHES",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
							TextButton(onClick = viewModel::clearRecentQueries) {
								Icon(
									imageVector = Icons.Default.DeleteSweep,
									contentDescription = null,
									tint = NemuriTheme.colors.textMuted,
									modifier = Modifier.size(16.dp),
								)
								Spacer(modifier = Modifier.width(4.dp))
								Text("Clear all", color = NemuriTheme.colors.textMuted)
							}
						}
					}
					item {
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							uiState.recentQueries.forEach { past ->
								Surface(
									color = NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(999.dp),
									modifier = Modifier
										.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(999.dp))
										.clip(RoundedCornerShape(999.dp))
										.clickable { viewModel.submitSearch(past) },
								) {
									Row(
										modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
									) {
										Icon(
											imageVector = Icons.Default.History,
											contentDescription = null,
											tint = NemuriTheme.colors.textMuted,
											modifier = Modifier.size(15.dp),
										)
										Text(
											text = past,
											style = MaterialTheme.typography.labelMedium,
											color = NemuriTheme.colors.textPrimary,
										)
										Icon(
											imageVector = Icons.Default.Close,
											contentDescription = "Delete",
											tint = NemuriTheme.colors.textDim,
											modifier = Modifier
												.size(16.dp)
												.clickable { viewModel.deleteRecentQuery(past) },
										)
									}
								}
							}
						}
					}
				} else {
					item {
						NemuriEmptyState(
							icon = Icons.Default.Search,
							title = "Search across your sources",
							subtitle = "Type a title, author, or comma-separated genre tags (using Tag/Genre mode).",
						)
					}
				}
			}
		} else if (!uiState.isSearching && uiState.sections.isEmpty() && uiState.sourceErrors.isEmpty()) {
			NemuriEmptyState(
				icon = Icons.Default.SearchOff,
				title = "No results for \"${uiState.activeQuery}\"",
				subtitle = if (uiState.totalCount == 0) {
					"No enabled sources matched the selected filter."
				} else {
					"Searched ${uiState.totalCount} source(s) with no matches."
				},
			)
		} else {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				if (uiState.isSearching) {
					item {
						Text(
							text = "Searched ${uiState.completedCount} of ${uiState.totalCount} sources • ${uiState.sections.size} with matches",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
							modifier = Modifier.padding(horizontal = 16.dp),
						)
					}
				}

				items(uiState.sections, key = { it.sourceName }) { section ->
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Surface(
								color = NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(8.dp),
								modifier = Modifier
									.size(28.dp)
									.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(8.dp)),
							) {
								SubcomposeAsyncImage(
									model = section.source.faviconUri(),
									contentDescription = section.sourceTitle,
									contentScale = ContentScale.Crop,
									modifier = Modifier
										.fillMaxSize()
										.clip(RoundedCornerShape(8.dp)),
								)
							}
							Text(
								text = section.sourceTitle,
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f, fill = false),
							)
							if (section.isNovel) {
								Surface(
									color = NemuriTheme.colors.cyan.copy(alpha = 0.16f),
									shape = RoundedCornerShape(5.dp),
								) {
									NonWrappingBadgeText(
										text = "NOVEL",
										color = NemuriTheme.colors.cyan,
										fontSizeSp = 8.5f,
										modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
									)
								}
							}
							Surface(
								color = NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(6.dp),
							) {
								Text(
									text = section.items.size.toString(),
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.textMuted,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								)
							}
							Spacer(modifier = Modifier.weight(1f))
							Row(
								modifier = Modifier
									.clip(RoundedCornerShape(8.dp))
									.clickable { onOpenSourceBrowse(section.sourceName, uiState.activeQuery) }
									.padding(horizontal = 6.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Text(
									text = "See all",
									style = MaterialTheme.typography.labelMedium,
									color = NemuriTheme.colors.cyan,
								)
								Icon(
									imageVector = Icons.AutoMirrored.Filled.ArrowForward,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(14.dp),
								)
							}
						}

						LazyRow(
							contentPadding = PaddingValues(horizontal = 16.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							items(section.items, key = { "${section.sourceName}:${it.id}" }) { manga ->
								MangaCompactGridCard(
									manga = manga,
									inLibraryBadge = manga.id in uiState.favouriteIds,
									onClick = {
										MangaTransferCache.put(manga)
										onOpenDetails(manga)
									},
									modifier = Modifier.width(116.dp),
								)
							}
						}
					}
				}

				if (uiState.sourceErrors.isNotEmpty()) {
					item {
						Surface(
							color = NemuriTheme.colors.card,
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp)
								.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
						) {
							Column(modifier = Modifier.fillMaxWidth()) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.clickable { showErrors = !showErrors }
										.padding(14.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text(
										text = "${uiState.sourceErrors.size} source(s) reported errors or challenges",
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.amber,
									)
									Icon(
										imageVector = if (showErrors) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
										contentDescription = null,
										tint = NemuriTheme.colors.textMuted,
									)
								}
								if (showErrors) {
									HorizontalDivider(color = NemuriTheme.colors.border)
									uiState.sourceErrors.forEach { err ->
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.padding(horizontal = 14.dp, vertical = 10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(10.dp),
										) {
											Column(modifier = Modifier.weight(1f)) {
												Text(
													text = err.sourceTitle,
													style = MaterialTheme.typography.titleSmall,
													color = NemuriTheme.colors.textPrimary,
												)
												Text(
													text = err.message,
													style = MaterialTheme.typography.labelSmall,
													color = NemuriTheme.colors.textMuted,
													maxLines = 2,
													overflow = TextOverflow.Ellipsis,
												)
											}
											val cf = err.cloudFlareException
											val interactive = err.interactiveException
											OutlinedButton(
												onClick = {
													when {
														cf != null -> {
															retryTargetSource = err.source
															cfLauncher.launch(cf)
														}
														interactive != null -> {
															retryTargetSource = err.source
															browserLauncher.launch(interactive)
														}
														else -> viewModel.retrySource(err.source)
													}
												},
												shape = RoundedCornerShape(10.dp),
												contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
											) {
												Icon(
													imageVector = if (cf != null || interactive != null) {
														Icons.Default.Security
													} else {
														Icons.Default.Refresh
													},
													contentDescription = null,
													tint = NemuriTheme.colors.cyan,
													modifier = Modifier.size(15.dp),
												)
												Spacer(modifier = Modifier.width(4.dp))
												Text(
													text = if (cf != null || interactive != null) "Verify" else "Retry",
													color = NemuriTheme.colors.cyan,
													style = MaterialTheme.typography.labelMedium,
												)
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SearchChip(
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
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
		)
	}
}
