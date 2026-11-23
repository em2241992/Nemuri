package org.nemuri.app.ui.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.favicon.faviconUri
import org.koitharu.kotatsu.explore.data.SourcesSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun ExploreScreen(
	onOpenExtensions: () -> Unit,
	onOpenLocalFiles: () -> Unit,
	onOpenBookmarks: () -> Unit,
	onOpenDownloads: () -> Unit,
	onOpenSourceBrowse: (String) -> Unit,
	onOpenGlobalSearch: (String) -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: ExploreViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	var showSortAndLanguageSheet by remember { mutableStateOf(false) }
	var actionTargetTile by remember { mutableStateOf<ExploreSourceTile?>(null) }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = uiState.sourceQuery,
			onQueryChange = viewModel::setSourceQuery,
			placeholder = "Filter sources or search all…",
			onSearchSubmit = {
				onOpenGlobalSearch(uiState.sourceQuery.trim())
			},
			onTuneClick = { showSortAndLanguageSheet = true },
			onSettingsClick = onOpenSettings,
		)

		LazyVerticalGrid(
			columns = GridCells.Adaptive(minSize = 160.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 120.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			item(span = { GridItemSpan(maxLineSpan) }) {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						ExploreQuickPill(
							icon = Icons.Default.Extension,
							label = "Extensions",
							showDot = uiState.hasExtensionUpdates,
							onClick = onOpenExtensions,
							modifier = Modifier.weight(1f),
						)
						ExploreQuickPill(
							icon = Icons.Default.Folder,
							label = "Local files",
							onClick = onOpenLocalFiles,
							modifier = Modifier.weight(1f),
						)
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						ExploreQuickPill(
							icon = Icons.Default.Bookmark,
							label = "Bookmarks",
							onClick = onOpenBookmarks,
							modifier = Modifier.weight(1f),
						)
						ExploreQuickPill(
							icon = Icons.Default.Download,
							label = "Downloads",
							onClick = onOpenDownloads,
							modifier = Modifier.weight(1f),
						)
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.weight(1f)
								.height(44.dp)
								.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
								.clip(RoundedCornerShape(14.dp))
								.clickable(enabled = !uiState.isFindingRandom) {
									viewModel.findRandomManga { manga ->
										MangaTransferCache.put(manga)
										onOpenDetails(manga)
									}
								},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.Center,
							) {
								if (uiState.isFindingRandom) {
									CircularProgressIndicator(
										color = NemuriTheme.colors.cyan,
										strokeWidth = 2.dp,
										modifier = Modifier.size(16.dp),
									)
								} else {
									Icon(
										imageVector = Icons.Default.Shuffle,
										contentDescription = null,
										tint = NemuriTheme.colors.cyan,
										modifier = Modifier.size(18.dp),
									)
								}
								Spacer(modifier = Modifier.width(8.dp))
								Text(
									text = if (uiState.isFindingRandom) "Rolling…" else "Random Discovery",
									style = MaterialTheme.typography.labelLarge,
									color = NemuriTheme.colors.cyan,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}

						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.weight(1f)
								.height(44.dp)
								.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
								.clip(RoundedCornerShape(14.dp))
								.clickable { onOpenGlobalSearch(uiState.sourceQuery.trim()) },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.Center,
							) {
								Icon(
									imageVector = Icons.Default.Search,
									contentDescription = null,
									tint = NemuriTheme.colors.textSecondary,
									modifier = Modifier.size(18.dp),
								)
								Spacer(modifier = Modifier.width(8.dp))
								Text(
									text = "Global Search",
									style = MaterialTheme.typography.labelLarge,
									color = NemuriTheme.colors.textPrimary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
					}
				}
			}

			item(span = { GridItemSpan(maxLineSpan) }) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState())
						.padding(top = 4.dp, bottom = 2.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ExploreSourceFilter.entries.forEach { filter ->
						val selected = uiState.activeFilter == filter
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
								.clickable { viewModel.setFilter(filter) },
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
			}

			if (uiState.isLoadingSources && uiState.sources.isEmpty()) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 28.dp),
						horizontalArrangement = Arrangement.Center,
						verticalAlignment = Alignment.CenterVertically,
					) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.cyan,
							strokeWidth = 2.5.dp,
							modifier = Modifier.size(22.dp),
						)
						Spacer(modifier = Modifier.width(10.dp))
						Text(
							text = "Loading installed sources…",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
			} else if (uiState.sources.isEmpty()) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					val emptyTitle = if (uiState.allEnabledCount == 0) {
						"No extensions installed yet"
					} else {
						"No matching sources"
					}
					val emptySubtitle = if (uiState.allEnabledCount == 0) {
						"Install Mihon manga APKs or LNReader novel plugins in Extensions to browse catalogs."
					} else {
						"Clear your search or filter to view your ${uiState.allEnabledCount} enabled source(s)."
					}
					NemuriEmptyState(
						icon = Icons.Default.Explore,
						title = emptyTitle,
						subtitle = emptySubtitle,
						actionLabel = if (uiState.allEnabledCount == 0) "Open Extensions" else null,
						onAction = if (uiState.allEnabledCount == 0) onOpenExtensions else null,
					)
				}
			} else {
				val pinned = uiState.sources.filter { it.isPinned }
				val unpinned = uiState.sources.filterNot { it.isPinned }

				if (pinned.isNotEmpty() && uiState.activeFilter != ExploreSourceFilter.PINNED) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						SectionHeading("PINNED (${pinned.size})")
					}
					items(pinned, key = { "pinned:${it.sourceName}" }) { tile ->
						ExploreSourceCard(
							tile = tile,
							onClick = {
								viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
								onOpenSourceBrowse(tile.sourceName)
							},
							onLongClick = { actionTargetTile = tile },
							onTogglePin = { viewModel.togglePin(tile) },
						)
					}
				}

				val listToRender = if (uiState.activeFilter == ExploreSourceFilter.PINNED) pinned else unpinned
				if (listToRender.isNotEmpty()) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						val heading = if (uiState.activeFilter == ExploreSourceFilter.PINNED) {
							"PINNED SOURCES (${listToRender.size})"
						} else {
							"ALL SOURCES (${listToRender.size})"
						}
						SectionHeading(heading)
					}
					items(listToRender, key = { "src:${it.sourceName}" }) { tile ->
						ExploreSourceCard(
							tile = tile,
							onClick = {
								viewModel.trackSourceOpen(tile.sourceInfo.mangaSource)
								onOpenSourceBrowse(tile.sourceName)
							},
							onLongClick = { actionTargetTile = tile },
							onTogglePin = { viewModel.togglePin(tile) },
						)
					}
				}
			}
		}
	}

	if (showSortAndLanguageSheet) {
		NemuriModalSheet(
			title = "Explore Sources",
			subtitle = "Ordering & enabled source languages",
			onDismiss = { showSortAndLanguageSheet = false },
		) {
			LazyColumn(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 12.dp),
			) {
				item {
					Text(
						text = "SORT ORDER",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
				}
				items(SourcesSortOrder.entries, key = { it.name }) { order ->
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
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = when (order) {
									SourcesSortOrder.ALPHABETIC -> "Alphabetical"
									SourcesSortOrder.MANUAL -> "Manual order"
									SourcesSortOrder.LAST_USED -> "Recently used"
								},
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

				if (uiState.languages.isNotEmpty()) {
					item {
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = "SOURCE LANGUAGES",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
						)
					}
					items(uiState.languages, key = { it.code }) { lang ->
						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.toggleLanguage(lang.code) },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column {
									Text(
										text = lang.displayName,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
									)
									Text(
										text = "${lang.sourceCount} source(s)",
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.textMuted,
									)
								}
								Checkbox(
									checked = lang.isEnabled,
									onCheckedChange = { viewModel.toggleLanguage(lang.code) },
									colors = CheckboxDefaults.colors(checkedColor = NemuriTheme.colors.cyan),
								)
							}
						}
					}
				}
			}
		}
	}

	val sheetTile = actionTargetTile
	if (sheetTile != null) {
		NemuriModalSheet(
			title = sheetTile.title,
			subtitle = sheetTile.summary,
			onDismiss = { actionTargetTile = null },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				SheetActionRow(
					icon = if (sheetTile.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
					label = if (sheetTile.isPinned) "Unpin Source" else "Pin Source to Top",
					onClick = {
						viewModel.togglePin(sheetTile)
						actionTargetTile = null
					},
				)
				val url = sheetTile.homeUrl
				if (!url.isNullOrBlank()) {
					SheetActionRow(
						icon = Icons.Default.OpenInBrowser,
						label = "Open Site in WebView",
						onClick = {
							runCatching {
								context.startActivity(
									AppRouter.browserIntent(
										context,
										url,
										sheetTile.sourceInfo.mangaSource,
										sheetTile.title,
									),
								)
							}
							actionTargetTile = null
						},
					)
				}
				SheetActionRow(
					icon = Icons.Default.VisibilityOff,
					label = "Hide Source from Explore",
					isDestructive = true,
					onClick = {
						viewModel.hideSource(sheetTile)
						actionTargetTile = null
					},
				)
			}
		}
	}
}

@Composable
private fun ExploreQuickPill(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	showDot: Boolean = false,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(24.dp),
		modifier = modifier
			.height(52.dp)
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(24.dp))
			.clip(RoundedCornerShape(24.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			BadgedBox(
				badge = {
					if (showDot) {
						Badge(
							containerColor = NemuriTheme.colors.cyan,
							modifier = Modifier.size(7.dp),
						)
					}
				},
			) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(20.dp),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				color = NemuriTheme.colors.textPrimary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceCard(
	tile: ExploreSourceTile,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onTogglePin: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				if (tile.isPinned) NemuriTheme.colors.cyan.copy(alpha = 0.42f) else NemuriTheme.colors.border,
				RoundedCornerShape(16.dp),
			)
			.clip(RoundedCornerShape(16.dp))
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Row(
			modifier = Modifier.padding(11.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.size(40.dp)
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
			) {
				SubcomposeAsyncImage(
					model = tile.sourceInfo.mangaSource.faviconUri(),
					contentDescription = tile.title,
					contentScale = ContentScale.Crop,
					error = {
						Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
							Icon(
								imageVector = if (tile.isNovel) Icons.Default.MenuBook else Icons.Default.Explore,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(18.dp),
							)
						}
					},
					modifier = Modifier
						.fillMaxSize()
						.clip(RoundedCornerShape(12.dp)),
				)
			}
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Text(
					text = tile.title,
					style = MaterialTheme.typography.titleSmall,
					color = NemuriTheme.colors.textPrimary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				val meta = tile.summary ?: if (tile.isNovel) "Novel" else "Manga"
				Text(
					text = meta,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (tile.isNovel || tile.isNsfw) {
					Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
						if (tile.isNovel) {
							Surface(
								color = NemuriTheme.colors.cyan.copy(alpha = 0.16f),
								shape = RoundedCornerShape(5.dp),
							) {
								NonWrappingBadgeText(
									text = "NOVEL",
									color = NemuriTheme.colors.cyan,
									fontSizeSp = 8.5f,
									modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
								)
							}
						}
						if (tile.isNsfw) {
							Surface(
								color = NemuriTheme.colors.danger.copy(alpha = 0.18f),
								shape = RoundedCornerShape(5.dp),
							) {
								NonWrappingBadgeText(
									text = "18+",
									color = NemuriTheme.colors.danger,
									fontSizeSp = 8.5f,
									modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
								)
							}
						}
					}
				}
			}
			IconButton(
				onClick = onTogglePin,
				modifier = Modifier.size(28.dp),
			) {
				Icon(
					imageVector = if (tile.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
					contentDescription = if (tile.isPinned) "Unpin" else "Pin",
					tint = if (tile.isPinned) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
					modifier = Modifier.size(16.dp),
				)
			}
		}
	}
}

@Composable
private fun SectionHeading(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
	)
}

@Composable
private fun SheetActionRow(
	icon: ImageVector,
	label: String,
	isDestructive: Boolean = false,
	onClick: () -> Unit,
) {
	val color = if (isDestructive) NemuriTheme.colors.danger else NemuriTheme.colors.textPrimary
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp))
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = color,
				modifier = Modifier.size(20.dp),
			)
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				color = color,
			)
		}
	}
}
