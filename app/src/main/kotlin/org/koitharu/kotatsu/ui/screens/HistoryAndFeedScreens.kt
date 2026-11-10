package org.koitharu.kotatsu.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.model.CardCoverShape
import org.koitharu.kotatsu.core.model.DefaultFeedViewMode
import org.koitharu.kotatsu.core.model.FeedUpdateItem
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

enum class HistoryGroupingMode(val label: String) {
	BY_DATE("Group by Date"),
	BY_SERIES("Group by Series"),
}

enum class SectionViewLayout(val label: String) {
	COMPACT_ROWS("Compact Rows"),
	COMFORTABLE_ROWS("Comfortable List"),
	COMPACT_GRID("Compact Grid"),
	COMFY_GRID("Comfy Grid"),
}

enum class FeedGroupingMode(val label: String) {
	BY_DATE("Group by Date"),
	BY_SERIES("Group by Series"),
	BY_SOURCE("Group by Source"),
}

enum class FeedSectionCategory(val label: String) {
	HOME_PAGE("Home page"),
	FOR_YOU("For You"),
	UPDATES("Updates"),
	SUMMARY("Reading Summary"),
	SUGGESTIONS("Suggestions"),
}

private data class HistorySessionEntry(
	val id: String,
	val item: LibraryMangaItem,
	val chapterTitle: String,
	val chapterNumber: Int,
	val resumeChapterIndex: Int,
	val dateBucket: String,
	val relativeTimeLabel: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
	historyItems: List<LibraryMangaItem>,
	onResumeReading: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
	onRemoveHistoryItem: (Long) -> Unit,
	onClearAllHistory: () -> Unit,
	onOpenSettingsScreen: () -> Unit,
	onOpenAiEngineSettings: () -> Unit = {},
	isAiActive: Boolean = true,
	modifier: Modifier = Modifier,
) {
	var searchQuery by remember { mutableStateOf("") }
	var groupingMode by remember { mutableStateOf(HistoryGroupingMode.BY_DATE) }
	var layoutMode by remember { mutableStateOf(SectionViewLayout.COMPACT_ROWS) }
	var gridColumns by remember { mutableIntStateOf(3) }
	var cardShape by remember { mutableStateOf(CardCoverShape.ROUNDED) }
	var showProgressBar by remember { mutableStateOf(true) }
	var showSourceLabel by remember { mutableStateOf(true) }
	var headerBarCollapsed by remember { mutableStateOf(false) }
	var selectedSourceFilter by remember { mutableStateOf<String?>(null) }
	var selectedTagFilter by remember { mutableStateOf<String?>(null) }

	var showHistoryCustomizerSheet by remember { mutableStateOf(false) }
	var showAdvancedSearchSheet by remember { mutableStateOf(false) }

	val allSources = remember(historyItems) {
		historyItems.map { it.manga.source }.distinct().sorted()
	}
	val allTags = remember(historyItems) {
		historyItems.flatMap { item -> item.tags.map { it.title } }.distinct().sorted()
	}
	val activeAdvCount = (if (selectedSourceFilter != null) 1 else 0) + (if (selectedTagFilter != null) 1 else 0)

	val filteredItems = remember(historyItems, searchQuery, selectedSourceFilter, selectedTagFilter) {
		historyItems.filter { item ->
			val matchesQuery = searchQuery.isBlank() ||
				item.manga.title.contains(searchQuery, ignoreCase = true) ||
				item.manga.altTitles?.contains(searchQuery, ignoreCase = true) == true ||
				item.manga.authors?.contains(searchQuery, ignoreCase = true) == true
			val matchesSource = selectedSourceFilter == null || item.manga.source == selectedSourceFilter
			val matchesTag = selectedTagFilter == null || item.tags.any { it.title == selectedTagFilter }
			matchesQuery && matchesSource && matchesTag
		}
	}

	val sessionEntries = remember(filteredItems) {
		val now = System.currentTimeMillis()
		val dayMs = 86_400_000L
		filteredItems.mapIndexed { idx, item ->
			val resumeIdx = item.chapters.indexOfFirst { !it.isRead }.coerceAtLeast(0)
			val lastReadCh = item.chapters.lastOrNull { it.isRead } ?: item.chapters.firstOrNull()
			val chNum = item.lastReadChapterNumber.toInt().coerceAtLeast(1)
			val ts = if (item.lastReadAt > 0L) item.lastReadAt else (now - idx * 18_000_000L)
			val ageHours = ((now - ts).coerceAtLeast(0L)) / 3_600_000L
			val ageDays = ((now - ts).coerceAtLeast(0L)) / dayMs
			val dateBucket = when {
				ageDays == 0L -> "Today"
				ageDays == 1L -> "Yesterday"
				else -> "Earlier This Week"
			}
			val relTime = when {
				ageHours < 1L -> "Just now"
				ageHours < 24L -> "${ageHours}h ago"
				ageDays == 1L -> "Yesterday"
				else -> "${ageDays}d ago"
			}
			HistorySessionEntry(
				id = "hist_${item.manga.id}",
				item = item,
				chapterTitle = lastReadCh?.title ?: "Chapter $chNum",
				chapterNumber = chNum,
				resumeChapterIndex = resumeIdx,
				dateBucket = dateBucket,
				relativeTimeLabel = relTime,
			)
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// 1. Unified Compact Top Search Bar + Display Customizer (Tune) + Settings (Gear)
		Surface(
			color = MaterialTheme.colorScheme.surface,
			tonalElevation = 2.dp,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 12.dp, vertical = 8.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Surface(
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
						shape = RoundedCornerShape(24.dp),
						modifier = Modifier
							.weight(1f)
							.height(44.dp)
							.border(
								width = 1.dp,
								color = if (activeAdvCount > 0) {
									MaterialTheme.colorScheme.primary
								} else {
									MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
								},
								shape = RoundedCornerShape(24.dp),
							),
					) {
						Row(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							IconButton(
								onClick = { showAdvancedSearchSheet = true },
								modifier = Modifier
									.size(38.dp)
									.testTag("history_advanced_search_button"),
							) {
								if (activeAdvCount > 0) {
									BadgedBox(
										badge = { Badge { Text("$activeAdvCount") } },
									) {
										Icon(
											imageVector = Icons.Default.Search,
											contentDescription = "Advanced History Search",
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(20.dp),
										)
									}
								} else {
									Icon(
										imageVector = Icons.Default.Search,
										contentDescription = "Advanced History Search",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(20.dp),
									)
								}
							}

							Box(
								modifier = Modifier
									.weight(1f)
									.padding(horizontal = 4.dp),
								contentAlignment = Alignment.CenterStart,
							) {
								if (searchQuery.isEmpty()) {
									Text(
										text = if (activeAdvCount > 0) {
											"Search history ($activeAdvCount filters)…"
										} else {
											"Search history"
										},
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								BasicTextField(
									value = searchQuery,
									onValueChange = { searchQuery = it },
									singleLine = true,
									textStyle = MaterialTheme.typography.bodyMedium.copy(
										color = MaterialTheme.colorScheme.onSurface,
									),
									cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
									modifier = Modifier
										.fillMaxWidth()
										.testTag("history_search_input"),
								)
							}

							if (searchQuery.isNotEmpty() || activeAdvCount > 0) {
								IconButton(
									onClick = {
										searchQuery = ""
										selectedSourceFilter = null
										selectedTagFilter = null
									},
									modifier = Modifier.size(34.dp),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Clear Search",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp),
									)
								}
							}

							IconButton(
								onClick = { headerBarCollapsed = !headerBarCollapsed },
								modifier = Modifier.size(34.dp),
							) {
								Icon(
									imageVector = if (headerBarCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
									contentDescription = "Toggle Grouping Bar",
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}

					// Strict 2-Icon Header Rule on History via Shared UnifiedTopRightIconCluster: [🎚️ Display/Filter Sliders] + [⚙ Settings]
					org.koitharu.kotatsu.ui.components.UnifiedTopRightIconCluster(
						activeTab = org.koitharu.kotatsu.core.model.AppNavTab.HISTORY,
						onOpenDisplayOrFilterSliders = { showHistoryCustomizerSheet = true },
						onOpenSettingsScreen = onOpenSettingsScreen,
						customizerTestTag = "history_customizer_button",
						settingsTestTag = "history_settings_gear_button",
					)
				}

				// Collapsible Grouping & Clear Strip (Swappable Group by Date <-> Group by Series)
				AnimatedVisibility(visible = !headerBarCollapsed) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 8.dp)
							.horizontalScroll(rememberScrollState()),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
							shape = RoundedCornerShape(10.dp),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
								horizontalArrangement = Arrangement.spacedBy(4.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								HistoryGroupingMode.entries.forEach { mode ->
									val isSelected = groupingMode == mode
									Surface(
										color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
										shape = RoundedCornerShape(8.dp),
										modifier = Modifier
											.clip(RoundedCornerShape(8.dp))
											.clickable { groupingMode = mode }
											.testTag("history_group_${mode.name.lowercase()}"),
									) {
										Text(
											text = mode.label,
											style = MaterialTheme.typography.labelSmall,
											fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
											color = if (isSelected) {
												MaterialTheme.colorScheme.onPrimary
											} else {
												MaterialTheme.colorScheme.onSurfaceVariant
											},
											modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
										)
									}
								}
							}
						}

						if (historyItems.isNotEmpty()) {
							Surface(
								color = Color.Transparent,
								shape = CircleShape,
								modifier = Modifier
									.clip(CircleShape)
									.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
									.clickable { onClearAllHistory() }
									.testTag("clear_all_history_button"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Icon(
										imageVector = Icons.Default.DeleteSweep,
										contentDescription = null,
										modifier = Modifier.size(13.dp),
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
									)
									Text(
										text = "Clear Log",
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
							}
						}
					}
				}
			}
		}

		// 2. Main History Body
		if (sessionEntries.isEmpty()) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(32.dp),
				contentAlignment = Alignment.Center,
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Icon(
						imageVector = Icons.Default.History,
						contentDescription = null,
						modifier = Modifier.size(48.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Spacer(modifier = Modifier.height(10.dp))
					Text(
						text = "No matching reading history",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
					)
					Text(
						text = "Open any series to start logging chapters.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		} else if (layoutMode == SectionViewLayout.COMPACT_GRID || layoutMode == SectionViewLayout.COMFY_GRID) {
			val isCompactGrid = layoutMode == SectionViewLayout.COMPACT_GRID
			LazyVerticalGrid(
				columns = GridCells.Fixed(gridColumns.coerceIn(2, 6)),
				contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 120.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier
					.fillMaxSize()
					.testTag("history_grid_view"),
			) {
				items(sessionEntries, key = { it.id }) { entry ->
					CompactSectionGridCard(
						title = UiSourceAndTitleFormatter.formatDisplayTitle(entry.item.manga.title),
						subtitle = "Ch. ${entry.chapterNumber} • ${entry.relativeTimeLabel}",
						coverUrl = entry.item.manga.coverUrl,
						progress = entry.item.progressPercent,
						cornerRadiusDp = cardShape.cornerRadiusDp,
						isCompactGrid = isCompactGrid,
						showProgressBar = showProgressBar,
						onClick = { onResumeReading(entry.item.manga.id, entry.resumeChapterIndex) },
					)
				}
			}
		} else {
			val isCompactRow = layoutMode == SectionViewLayout.COMPACT_ROWS
			val groupedMap = remember(sessionEntries, groupingMode) {
				when (groupingMode) {
					HistoryGroupingMode.BY_DATE -> sessionEntries.groupBy { it.dateBucket }
					HistoryGroupingMode.BY_SERIES -> sessionEntries.groupBy { UiSourceAndTitleFormatter.formatDisplayTitle(it.item.manga.title) }
				}
			}

			LazyColumn(
				contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier
					.fillMaxSize()
					.testTag("history_list_view"),
			) {
				groupedMap.forEach { (groupHeader, groupEntries) ->
					item(key = "hist_group_$groupHeader") {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 6.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = groupHeader,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onBackground,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f),
							)
							if (groupingMode == HistoryGroupingMode.BY_DATE) {
								Text(
									text = "${groupEntries.size} read",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}

					items(groupEntries, key = { it.id }) { entry ->
						CompactHistoryRow(
							entry = entry,
							isCompact = isCompactRow,
							cornerRadiusDp = cardShape.cornerRadiusDp,
							showProgressBar = showProgressBar,
							showSourceLabel = showSourceLabel,
							groupingMode = groupingMode,
							onOpenDetails = { onOpenDetails(entry.item.manga.id) },
							onResume = { onResumeReading(entry.item.manga.id, entry.resumeChapterIndex) },
							onRemove = { onRemoveHistoryItem(entry.item.manga.id) },
						)
					}
				}
			}
		}
	}

	// History Display & Layout Customizer Sheet (Pill Visuals)
	if (showHistoryCustomizerSheet) {
		ModalBottomSheet(
			onDismissRequest = { showHistoryCustomizerSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.navigationBarsPadding()
					.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 56.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column {
						Text(
							text = "History Display Settings",
							style = MaterialTheme.typography.headlineSmall,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Customize grouping, layout size, corner shape & indicators",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					TextButton(onClick = { showHistoryCustomizerSheet = false }) {
						Text("Done")
					}
				}

				Text(
					text = "GROUPING MODE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					HistoryGroupingMode.entries.forEach { mode ->
						FilterChip(
							selected = groupingMode == mode,
							onClick = { groupingMode = mode },
							label = { Text(mode.label) },
						)
					}
				}

				Text(
					text = "VIEW LAYOUT MODE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					SectionViewLayout.entries.forEach { mode ->
						FilterChip(
							selected = layoutMode == mode,
							onClick = { layoutMode = mode },
							label = { Text(mode.label) },
						)
					}
				}

				if (layoutMode == SectionViewLayout.COMPACT_GRID || layoutMode == SectionViewLayout.COMFY_GRID) {
					Column {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "GRID SIZE (COLUMNS)",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text = "$gridColumns Columns",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
							)
						}
						Slider(
							value = gridColumns.toFloat(),
							onValueChange = { gridColumns = it.toInt().coerceIn(2, 6) },
							valueRange = 2f..6f,
							steps = 3,
						)
					}
				}

				Text(
					text = "CARD CORNER SHAPE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CardCoverShape.entries.forEach { shape ->
						FilterChip(
							selected = cardShape == shape,
							onClick = { cardShape = shape },
							label = { Text(shape.label) },
						)
					}
				}

				HorizontalDivider()

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show Reading Progress Bar")
					Switch(checked = showProgressBar, onCheckedChange = { showProgressBar = it })
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show Source Name")
					Switch(checked = showSourceLabel, onCheckedChange = { showSourceLabel = it })
				}

				Spacer(modifier = Modifier.height(20.dp))
			}
		}
	}

	// Advanced History Search Sheet
	if (showAdvancedSearchSheet) {
		ModalBottomSheet(
			onDismissRequest = { showAdvancedSearchSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.navigationBarsPadding()
					.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 56.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "Advanced History Filter",
						style = MaterialTheme.typography.headlineSmall,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
					)
					Row {
						if (activeAdvCount > 0) {
							TextButton(
								onClick = {
									selectedSourceFilter = null
									selectedTagFilter = null
								},
							) {
								Text("Reset")
							}
						}
						TextButton(onClick = { showAdvancedSearchSheet = false }) {
							Text("Done")
						}
					}
				}

				OutlinedTextField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					label = { Text("Search History by Title or Author") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)

				Text(
					text = "FILTER BY TAG",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					allTags.forEach { tag ->
						FilterChip(
							selected = selectedTagFilter == tag,
							onClick = {
								selectedTagFilter = if (selectedTagFilter == tag) null else tag
							},
							label = { Text(tag) },
						)
					}
				}

				Text(
					text = "FILTER BY SOURCE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					allSources.forEach { src ->
						FilterChip(
							selected = selectedSourceFilter == src,
							onClick = {
								selectedSourceFilter = if (selectedSourceFilter == src) null else src
							},
							label = { Text(src.removePrefix("EXT:")) },
						)
					}
				}

				Spacer(modifier = Modifier.height(20.dp))
			}
		}
	}
}

@Composable
private fun CompactHistoryRow(
	entry: HistorySessionEntry,
	isCompact: Boolean,
	cornerRadiusDp: Int,
	showProgressBar: Boolean,
	showSourceLabel: Boolean,
	groupingMode: HistoryGroupingMode,
	onOpenDetails: () -> Unit,
	onResume: () -> Unit,
	onRemove: () -> Unit,
) {
	val rowHeight = if (isCompact) 58.dp else 76.dp
	val shape = RoundedCornerShape(cornerRadiusDp.coerceIn(4, 20).dp)

	Card(
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surface,
		),
		shape = shape,
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onOpenDetails)
			.testTag("history_row_${entry.item.manga.id}"),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(rowHeight)
				.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			MangaCoverArtwork(
				title = entry.item.manga.title,
				coverUrl = entry.item.manga.coverUrl,
				modifier = Modifier
					.fillMaxHeight()
					.aspectRatio(0.68f)
					.clip(RoundedCornerShape(6.dp)),
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.Center,
			) {
				Text(
					text = if (groupingMode == HistoryGroupingMode.BY_SERIES) {
						entry.chapterTitle
					} else {
						UiSourceAndTitleFormatter.formatDisplayTitle(entry.item.manga.title)
					},
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				val srcPart = if (showSourceLabel) " • ${UiSourceAndTitleFormatter.formatSourceName(entry.item.manga.source)}" else ""
				Text(
					text = "Ch. ${entry.chapterNumber}/${entry.item.totalChapters} • ${entry.relativeTimeLabel}$srcPart",
					fontFamily = JetBrainsMonoFamily,
					fontSize = 10.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (showProgressBar) {
					Spacer(modifier = Modifier.height(4.dp))
					LinearProgressIndicator(
						progress = { entry.item.progressPercent.coerceIn(0f, 1f) },
						modifier = Modifier
							.fillMaxWidth(0.65f)
							.height(3.dp)
							.clip(CircleShape),
						color = MaterialTheme.colorScheme.primary,
					)
				}
			}

			FilledTonalButton(
				onClick = onResume,
				contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
				modifier = Modifier.height(30.dp),
			) {
				Icon(
					imageVector = Icons.Default.PlayArrow,
					contentDescription = "Resume",
					modifier = Modifier.size(14.dp),
				)
				Spacer(modifier = Modifier.width(3.dp))
				Text("Ch. ${entry.resumeChapterIndex + 1}", fontSize = 11.sp)
			}

			IconButton(
				onClick = onRemove,
				modifier = Modifier.size(28.dp),
			) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = "Remove from History",
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(15.dp),
				)
			}
		}
	}
}

/**
 * Feed Screen with 4 Dedicated Categories:
 * 1. Home page (Catalogue to discover: Feed updates, Popular new titles, Suggestions, All-time favorites from a user-defaulted source — defaults to MangaDex!)
 * 2. Updates (Dedicated chapter release timeline)
 * 3. Reading Summary (Reading stats & activity summary)
 * 4. Suggestions (Dedicated AI & Tag-matched manga recommendations)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	extensionCatalog: List<ExtensionCatalogManga>,
	onOpenReader: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
	onRequestAddCatalogManga: (ExtensionCatalogManga) -> Unit,
	onRefreshFeed: () -> Unit,
	onOpenSettingsScreen: () -> Unit,
	onOpenAiEngineSettings: () -> Unit = {},
	isAiActive: Boolean = true,
	defaultFeedView: DefaultFeedViewMode = DefaultFeedViewMode.UPDATES,
	onSetDefaultFeedView: (DefaultFeedViewMode) -> Unit = {},
	onMarkFeedChapterRead: (Long, Long, Int) -> Unit = { _, _, _ -> },
	initialPrimaryMode: HybridFeedPrimaryMode = HybridFeedPrimaryMode.UPDATES_AND_BROWSE,
	modifier: Modifier = Modifier,
) {
	var selectedCategory by rememberSaveable { mutableStateOf(FeedSectionCategory.HOME_PAGE) }
	var defaultCatalogueSource by rememberSaveable { mutableStateOf("MangaDex") }

	var searchQuery by remember { mutableStateOf("") }
	val feedSearchFocusRequester = remember { FocusRequester() }
	var onlyUnread by remember { mutableStateOf(false) }
	var selectedSourceFilter by remember { mutableStateOf<String?>(null) }
	var groupingMode by remember { mutableStateOf(FeedGroupingMode.BY_DATE) }
	var layoutMode by remember { mutableStateOf(SectionViewLayout.COMPACT_ROWS) }
	var gridColumns by remember { mutableIntStateOf(3) }
	var hubGalleryColumns by rememberSaveable { mutableIntStateOf(5) }
	var cardShape by remember { mutableStateOf(CardCoverShape.ROUNDED) }
	var showNewDropBadge by remember { mutableStateOf(true) }
	var showScanlator by remember { mutableStateOf(true) }
	var headerBarCollapsed by remember { mutableStateOf(false) }

	var showFeedCustomizerSheet by remember { mutableStateOf(false) }
	var showAdvancedSearchSheet by remember { mutableStateOf(false) }
	var useFloatingBottomCategoryPill by rememberSaveable { mutableStateOf(false) }

	val availableCatalogueSources = remember(extensionCatalog, libraryItems) {
		val dynamicSources = (
			extensionCatalog.map { org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(it.sourceName) } +
				libraryItems.map { org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter.formatSourceName(it.manga.source) }
			).filter { it.isNotBlank() }.distinct()
		(listOf("MangaDex") + dynamicSources + listOf("All Sources")).distinct()
	}
	val sources = remember(feedUpdates) {
		feedUpdates.map { it.mangaItem.manga.source }.distinct().sorted()
	}
	val activeFilterCount = (if (onlyUnread) 1 else 0) + (if (selectedSourceFilter != null) 1 else 0)

	val filteredUpdates = remember(feedUpdates, searchQuery, onlyUnread, selectedSourceFilter) {
		feedUpdates.filter { item ->
			val matchesQuery = searchQuery.isBlank() ||
				item.mangaItem.manga.title.contains(searchQuery, ignoreCase = true) ||
				item.chapter.title.contains(searchQuery, ignoreCase = true) ||
				item.mangaItem.manga.source.contains(searchQuery, ignoreCase = true)
			val matchesUnread = !onlyUnread || !item.chapter.isRead
			val matchesSource = selectedSourceFilter == null || item.mangaItem.manga.source == selectedSourceFilter
			matchesQuery && matchesUnread && matchesSource
		}
	}

	val groupedUpdates = remember(filteredUpdates, groupingMode) {
		when (groupingMode) {
			FeedGroupingMode.BY_DATE -> filteredUpdates.groupBy { it.dateBucketLabel }
			FeedGroupingMode.BY_SERIES -> filteredUpdates.groupBy { it.mangaItem.manga.title }
			FeedGroupingMode.BY_SOURCE -> filteredUpdates.groupBy { it.mangaItem.manga.source.removePrefix("EXT:") }
		}
	}

	val libraryNormalizedTitles = remember(libraryItems) {
		libraryItems.map { TitleNormalizer.normalize(it.manga.title) }.toSet()
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		Column(
			modifier = Modifier.fillMaxSize(),
		) {
			// 1. Unified Compact Top Search Bar + Display Customizer (Tune) + Settings (Gear)
			Surface(
				color = MaterialTheme.colorScheme.surface,
				tonalElevation = 2.dp,
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 8.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
							shape = RoundedCornerShape(24.dp),
							modifier = Modifier
								.weight(1f)
								.height(44.dp)
								.border(
									width = 1.dp,
									color = if (activeFilterCount > 0) {
										MaterialTheme.colorScheme.primary
									} else {
										MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
									},
									shape = RoundedCornerShape(24.dp),
								),
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								IconButton(
									onClick = { showAdvancedSearchSheet = true },
									modifier = Modifier
										.size(38.dp)
										.testTag("feed_advanced_search_button"),
								) {
									if (activeFilterCount > 0) {
										BadgedBox(
											badge = { Badge { Text("$activeFilterCount") } },
										) {
											Icon(
												imageVector = Icons.Default.Search,
												contentDescription = "Advanced Feed Search",
												tint = MaterialTheme.colorScheme.primary,
												modifier = Modifier.size(20.dp),
											)
										}
									} else {
										Icon(
											imageVector = Icons.Default.Search,
											contentDescription = "Advanced Feed Search",
											tint = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.size(20.dp),
										)
									}
								}

								Box(
									modifier = Modifier
										.weight(1f)
										.padding(horizontal = 4.dp),
									contentAlignment = Alignment.CenterStart,
								) {
									if (searchQuery.isEmpty()) {
										Text(
											text = if (activeFilterCount > 0) {
												"Search feed ($activeFilterCount filters)…"
											} else {
												"Search feed"
											},
											style = MaterialTheme.typography.bodyMedium,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
									BasicTextField(
										value = searchQuery,
										onValueChange = { searchQuery = it },
										singleLine = true,
										textStyle = MaterialTheme.typography.bodyMedium.copy(
											color = MaterialTheme.colorScheme.onSurface,
										),
										cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
										modifier = Modifier
											.fillMaxWidth()
											.focusRequester(feedSearchFocusRequester)
											.testTag("feed_search_input"),
									)
								}

								if (searchQuery.isNotEmpty() || activeFilterCount > 0) {
									IconButton(
										onClick = {
											searchQuery = ""
											onlyUnread = false
											selectedSourceFilter = null
										},
										modifier = Modifier.size(34.dp),
									) {
										Icon(
											imageVector = Icons.Default.Close,
											contentDescription = "Clear Search",
											tint = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.size(18.dp),
										)
									}
								}

								IconButton(
									onClick = onRefreshFeed,
									modifier = Modifier
										.size(34.dp)
										.testTag("refresh_feed_button"),
								) {
									Icon(
										imageVector = Icons.Default.Refresh,
										contentDescription = "Sync Feed",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp),
									)
								}
							}
						}

						// Strict 2-Icon Header Rule on Feed via Shared UnifiedTopRightIconCluster: [🎚️ Display/Filter Sliders] + [⚙ Settings]
						org.koitharu.kotatsu.ui.components.UnifiedTopRightIconCluster(
							activeTab = org.koitharu.kotatsu.core.model.AppNavTab.FEED,
							onOpenDisplayOrFilterSliders = { showFeedCustomizerSheet = true },
							onOpenSettingsScreen = onOpenSettingsScreen,
							customizerTestTag = "feed_customizer_button",
							settingsTestTag = "feed_settings_gear_button",
						)
					}
				}
			}

			// 2. Active Feed Category View
			when (selectedCategory) {
				FeedSectionCategory.HOME_PAGE -> {
					DualModeHybridFeedHomeView(
						initialPrimaryMode = initialPrimaryMode,
						defaultFeedView = defaultFeedView,
						onSetDefaultFeedView = onSetDefaultFeedView,
						defaultSource = defaultCatalogueSource,
						availableSources = availableCatalogueSources,
						feedUpdates = filteredUpdates,
						libraryItems = libraryItems,
						extensionCatalog = extensionCatalog,
						libraryNormalizedTitles = libraryNormalizedTitles,
						searchQuery = searchQuery,
						hubGalleryColumns = hubGalleryColumns,
						onSelectHubGalleryColumns = { hubGalleryColumns = it.coerceIn(3, 5) },
						onRequestFocusTopSearch = {
							runCatching { feedSearchFocusRequester.requestFocus() }
						},
						onSelectDefaultSource = { defaultCatalogueSource = it },
						onNavigateToSubCategory = { selectedCategory = it },
						onOpenReader = onOpenReader,
						onOpenDetails = onOpenDetails,
						onRequestAddCatalogManga = onRequestAddCatalogManga,
						onMarkFeedChapterRead = onMarkFeedChapterRead,
						catalogueFallbackContent = {
							FeedHomePageCatalogueView(
								defaultSource = defaultCatalogueSource,
								availableSources = availableCatalogueSources,
								feedUpdates = filteredUpdates,
								libraryItems = libraryItems,
								extensionCatalog = extensionCatalog,
								libraryNormalizedTitles = libraryNormalizedTitles,
								searchQuery = searchQuery,
								onSelectDefaultSource = { defaultCatalogueSource = it },
								onNavigateToSubCategory = { selectedCategory = it },
								onOpenReader = onOpenReader,
								onOpenDetails = onOpenDetails,
								onRequestAddCatalogManga = onRequestAddCatalogManga,
							)
						},
					)
				}

				FeedSectionCategory.FOR_YOU -> {
					DualModeHybridFeedHomeView(
						initialPrimaryMode = HybridFeedPrimaryMode.DISCOVERY_FOR_YOU,
						defaultSource = defaultCatalogueSource,
						availableSources = availableCatalogueSources,
						feedUpdates = filteredUpdates,
						libraryItems = libraryItems,
						extensionCatalog = extensionCatalog,
						libraryNormalizedTitles = libraryNormalizedTitles,
						searchQuery = searchQuery,
						hubGalleryColumns = hubGalleryColumns,
						onSelectHubGalleryColumns = { hubGalleryColumns = it.coerceIn(3, 5) },
						onRequestFocusTopSearch = {
							runCatching { feedSearchFocusRequester.requestFocus() }
						},
						onSelectDefaultSource = { defaultCatalogueSource = it },
						onNavigateToSubCategory = { selectedCategory = it },
						onOpenReader = onOpenReader,
						onOpenDetails = onOpenDetails,
						onRequestAddCatalogManga = onRequestAddCatalogManga,
						onMarkFeedChapterRead = onMarkFeedChapterRead,
						catalogueFallbackContent = {
							FeedHomePageCatalogueView(
								defaultSource = defaultCatalogueSource,
								availableSources = availableCatalogueSources,
								feedUpdates = filteredUpdates,
								libraryItems = libraryItems,
								extensionCatalog = extensionCatalog,
								libraryNormalizedTitles = libraryNormalizedTitles,
								searchQuery = searchQuery,
								onSelectDefaultSource = { defaultCatalogueSource = it },
								onNavigateToSubCategory = { selectedCategory = it },
								onOpenReader = onOpenReader,
								onOpenDetails = onOpenDetails,
								onRequestAddCatalogManga = onRequestAddCatalogManga,
							)
						},
					)
				}

				FeedSectionCategory.UPDATES -> {
					FeedDedicatedUpdatesView(
						filteredUpdates = filteredUpdates,
						groupedUpdates = groupedUpdates,
						groupingMode = groupingMode,
						layoutMode = layoutMode,
						gridColumns = gridColumns,
						cardShape = cardShape,
						showNewDropBadge = showNewDropBadge,
						showScanlator = showScanlator,
						onlyUnread = onlyUnread,
						onSelectGrouping = { groupingMode = it },
						onToggleUnread = { onlyUnread = !onlyUnread },
						onOpenReader = onOpenReader,
						onOpenDetails = onOpenDetails,
					)
				}

				FeedSectionCategory.SUMMARY -> {
					FeedReadingSummaryView(
						libraryItems = libraryItems,
						onOpenReader = onOpenReader,
						onOpenDetails = onOpenDetails,
					)
				}

				FeedSectionCategory.SUGGESTIONS -> {
					FeedDedicatedSuggestionsView(
						libraryItems = libraryItems,
						extensionCatalog = extensionCatalog,
						libraryNormalizedTitles = libraryNormalizedTitles,
						defaultSource = defaultCatalogueSource,
						searchQuery = searchQuery,
						onOpenDetails = onOpenDetails,
						onRequestAddCatalogManga = onRequestAddCatalogManga,
					)
				}
			}
		}

		// Screenshot 3 style Floating Bottom Category Pill Bar (when enabled in Feed Settings)
		if (useFloatingBottomCategoryPill) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
				tonalElevation = 8.dp,
				shadowElevation = 12.dp,
				shape = RoundedCornerShape(28.dp),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 78.dp, start = 16.dp, end = 16.dp)
					.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
					.testTag("feed_floating_bottom_category_pill"),
			) {
				Row(
					modifier = Modifier
						.padding(horizontal = 8.dp, vertical = 6.dp)
						.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					FeedSectionCategory.entries.forEach { cat ->
						val isSelected = selectedCategory == cat
						Surface(
							color = if (isSelected) {
								MaterialTheme.colorScheme.primaryContainer
							} else {
								Color.Transparent
							},
							shape = RoundedCornerShape(20.dp),
							modifier = Modifier
								.clip(RoundedCornerShape(20.dp))
								.clickable { selectedCategory = cat }
								.testTag("feed_bottom_pill_${cat.name.lowercase()}"),
						) {
							Text(
								text = cat.label,
								style = MaterialTheme.typography.labelMedium,
								fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
								color = if (isSelected) {
									MaterialTheme.colorScheme.onPrimaryContainer
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
							)
						}
					}
				}
			}
		}
	}

	// Feed Display & Layout Customizer Sheet (Pill Visuals)
	if (showFeedCustomizerSheet) {
		ModalBottomSheet(
			onDismissRequest = { showFeedCustomizerSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.navigationBarsPadding()
					.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column {
						Text(
							text = "Feed & Catalogue Settings",
							style = MaterialTheme.typography.headlineSmall,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Default catalogue source, grouping, layout pills & badges",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					TextButton(onClick = { showFeedCustomizerSheet = false }) {
						Text("Done")
					}
				}

				Text(
					text = "DEFAULT CATALOGUE SOURCE (HOME PAGE & SUGGESTIONS)",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					availableCatalogueSources.forEach { src ->
						FilterChip(
							selected = defaultCatalogueSource == src,
							onClick = { defaultCatalogueSource = src },
							label = { Text(src) },
						)
					}
				}

				Text(
					text = "UPDATES GROUPING MODE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					FeedGroupingMode.entries.forEach { mode ->
						FilterChip(
							selected = groupingMode == mode,
							onClick = { groupingMode = mode },
							label = { Text(mode.label) },
						)
					}
				}

				Text(
					text = "VIEW LAYOUT MODE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					SectionViewLayout.entries.forEach { mode ->
						FilterChip(
							selected = layoutMode == mode,
							onClick = { layoutMode = mode },
							label = { Text(mode.label) },
						)
					}
				}

				if (layoutMode == SectionViewLayout.COMPACT_GRID || layoutMode == SectionViewLayout.COMFY_GRID) {
					Column {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "GRID SIZE (COLUMNS)",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text = "$gridColumns Columns",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
							)
						}
						Slider(
							value = gridColumns.toFloat(),
							onValueChange = { gridColumns = it.toInt().coerceIn(2, 6) },
							valueRange = 2f..6f,
							steps = 3,
						)
					}
				}

				Text(
					text = "DENSE GALLERY COLUMNS (3 / 4 / 5)",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					listOf(3, 4, 5).forEach { cols ->
						FilterChip(
							selected = hubGalleryColumns == cols,
							onClick = { hubGalleryColumns = cols },
							label = { Text(if (cols == 5) "5 Columns (Default)" else "$cols Columns") },
							modifier = Modifier.testTag("tune_hub_gallery_cols_$cols"),
						)
					}
				}

				Text(
					text = "CARD CORNER SHAPE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CardCoverShape.entries.forEach { shape ->
						FilterChip(
							selected = cardShape == shape,
							onClick = { cardShape = shape },
							label = { Text(shape.label) },
						)
					}
				}

				HorizontalDivider()

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Floating Bottom Category Pill")
						Text(
							text = "Show Home page / Updates / Summary / Suggestions as a floating bottom pill",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Switch(
						checked = useFloatingBottomCategoryPill,
						onCheckedChange = { useFloatingBottomCategoryPill = it },
						modifier = Modifier.testTag("toggle_feed_floating_bottom_pill"),
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show 'NEW' Release Badge")
					Switch(checked = showNewDropBadge, onCheckedChange = { showNewDropBadge = it })
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show Scanlator Group")
					Switch(checked = showScanlator, onCheckedChange = { showScanlator = it })
				}

				Spacer(modifier = Modifier.height(20.dp))
			}
		}
	}

	// Advanced Feed Filter Sheet
	if (showAdvancedSearchSheet) {
		ModalBottomSheet(
			onDismissRequest = { showAdvancedSearchSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.navigationBarsPadding()
					.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "Advanced Feed Filter",
						style = MaterialTheme.typography.headlineSmall,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
					)
					Row {
						if (activeFilterCount > 0) {
							TextButton(
								onClick = {
									onlyUnread = false
									selectedSourceFilter = null
								},
							) {
								Text("Reset")
							}
						}
						TextButton(onClick = { showAdvancedSearchSheet = false }) {
							Text("Done")
						}
					}
				}

				OutlinedTextField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					label = { Text("Search Feed by Series or Chapter Title") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)

				Text(
					text = "FILTER BY SOURCE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					sources.forEach { src ->
						FilterChip(
							selected = selectedSourceFilter == src,
							onClick = {
								selectedSourceFilter = if (selectedSourceFilter == src) null else src
							},
							label = { Text(src.removePrefix("EXT:").removePrefix("DEAD:")) },
						)
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Only Unread Chapter Releases")
					Switch(checked = onlyUnread, onCheckedChange = { onlyUnread = it })
				}

				Spacer(modifier = Modifier.height(20.dp))
			}
		}
	}
}

/**
 * Screenshot 3 style Feed Home Page Discover Catalogue:
 * - Centered section headers with arrow (`Feed updates →`, `Popular new titles →`, `Suggestions →`, `All-time favorites →`)
 * - Interactive section drill-downs when tapping `Popular new titles →` or `All-time favorites →`
 * - Horizontal carousels of tall rounded manga cards with `"In Library"` top-left pill badges
 * - Defaulted to MangaDex (user-selectable via top source chip or Feed Settings)
 */
@Composable
private fun FeedHomePageCatalogueView(
	defaultSource: String,
	availableSources: List<String>,
	feedUpdates: List<FeedUpdateItem>,
	libraryItems: List<LibraryMangaItem>,
	extensionCatalog: List<ExtensionCatalogManga>,
	libraryNormalizedTitles: Set<String>,
	searchQuery: String,
	onSelectDefaultSource: (String) -> Unit,
	onNavigateToSubCategory: (FeedSectionCategory) -> Unit,
	onOpenReader: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
	onRequestAddCatalogManga: (ExtensionCatalogManga) -> Unit,
) {
	var drillDownSection by rememberSaveable { mutableStateOf<String?>(null) }

	if (drillDownSection != null) {
		androidx.activity.compose.BackHandler {
			drillDownSection = null
		}
	}

	val sourceFilteredCatalog = remember(extensionCatalog, defaultSource, searchQuery) {
		val base = if (defaultSource == "All Sources") {
			extensionCatalog
		} else {
			extensionCatalog.filter { it.sourceName.contains(defaultSource, ignoreCase = true) }
				.ifEmpty { extensionCatalog }
		}
		if (searchQuery.isBlank()) base else base.filter {
			it.title.contains(searchQuery, ignoreCase = true) ||
				it.author.contains(searchQuery, ignoreCase = true)
		}
	}

	if (drillDownSection != null) {
		Column(modifier = Modifier.fillMaxSize()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					FilledTonalButton(
						onClick = { drillDownSection = null },
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier.testTag("catalogue_drilldown_back_button"),
					) {
						Text("← Home page")
					}
					Text(
						text = drillDownSection.orEmpty(),
						style = MaterialTheme.typography.titleMedium,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
					)
				}
				Text(
					text = defaultSource,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
			}

			LazyVerticalGrid(
				columns = GridCells.Fixed(3),
				contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				if (drillDownSection == "Popular new titles") {
					items(sourceFilteredCatalog, key = { "drill_pop_${it.url}" }) { catItem ->
						val isInLib = TitleNormalizer.normalize(catItem.title) in libraryNormalizedTitles
						CatalogueTallCoverCard(
							title = catItem.title,
							subtitle = "${catItem.totalChapters} ch • ${catItem.sourceName.removePrefix("EXT:")}",
							coverUrl = catItem.coverSeed,
							inLibrary = isInLib,
							unreadCountBadge = null,
							onClick = {
								val existing = libraryItems.find {
									TitleNormalizer.normalize(it.manga.title) == TitleNormalizer.normalize(catItem.title)
								}
								if (existing != null) onOpenDetails(existing.manga.id) else onRequestAddCatalogManga(catItem)
							},
						)
					}
				} else {
					val allTimeList = libraryItems.sortedByDescending { it.manga.rating }
					items(allTimeList, key = { "drill_fav_${it.manga.id}" }) { item ->
						CatalogueTallCoverCard(
							title = item.manga.title,
							subtitle = "★ ${"%.1f".format(item.manga.rating * 5f)} • ${item.totalChapters} ch",
							coverUrl = item.manga.coverUrl,
							inLibrary = true,
							unreadCountBadge = item.unreadCount.takeIf { it > 0 },
							onClick = { onOpenDetails(item.manga.id) },
						)
					}
				}
			}
		}
		return
	}

	LazyColumn(
		contentPadding = PaddingValues(top = 10.dp, bottom = 128.dp),
		verticalArrangement = Arrangement.spacedBy(18.dp),
		modifier = Modifier
			.fillMaxSize()
			.testTag("feed_home_catalogue_view"),
	) {
		// Quick Source Switcher Strip for Catalogue
		item {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
					.horizontalScroll(rememberScrollState()),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Text(
					text = "Catalogue Source:",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				availableSources.forEach { src ->
					FilterChip(
						selected = defaultSource == src,
						onClick = { onSelectDefaultSource(src) },
						label = { Text(src, fontSize = 11.sp) },
					)
				}
			}
		}

		// 1. Feed updates → (Horizontal Carousel matching Screenshot 3)
		item {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				CatalogueSectionHeader(
					title = "Feed updates",
					onClick = { onNavigateToSubCategory(FeedSectionCategory.UPDATES) },
				)
				LazyRow(
					contentPadding = PaddingValues(horizontal = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					items(feedUpdates.distinctBy { it.mangaItem.manga.id }.take(8), key = { "home_upd_${it.id}" }) { upd ->
						CatalogueTallCoverCard(
							title = upd.mangaItem.manga.title,
							subtitle = "Ch.${upd.chapter.number.toInt()} - ${upd.chapter.title.removePrefix("Chapter ${upd.chapter.number.toInt()}: ")}",
							coverUrl = upd.mangaItem.manga.coverUrl,
							inLibrary = true,
							unreadCountBadge = upd.mangaItem.unreadCount.takeIf { it > 0 },
							onClick = { onOpenReader(upd.mangaItem.manga.id, upd.chapterIndex) },
						)
					}
				}
			}
		}

		// 2. Popular new titles → (Interactive Drill-Down from Default Source e.g. MangaDex)
		item {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				CatalogueSectionHeader(
					title = "Popular new titles",
					onClick = { drillDownSection = "Popular new titles" },
				)
				LazyRow(
					contentPadding = PaddingValues(horizontal = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					items(sourceFilteredCatalog, key = { "pop_${it.url}" }) { catItem ->
						val isInLib = TitleNormalizer.normalize(catItem.title) in libraryNormalizedTitles
						CatalogueTallCoverCard(
							title = catItem.title,
							subtitle = "No. ${(catItem.totalChapters % 12) + 1} • ${catItem.sourceName.removePrefix("EXT:")}",
							coverUrl = catItem.coverSeed,
							inLibrary = isInLib,
							unreadCountBadge = null,
							onClick = {
								val existing = libraryItems.find {
									TitleNormalizer.normalize(it.manga.title) == TitleNormalizer.normalize(catItem.title)
								}
								if (existing != null) {
									onOpenDetails(existing.manga.id)
								} else {
									onRequestAddCatalogManga(catItem)
								}
							},
						)
					}
				}
			}
		}

		// 3. Suggestions → (Dedicated Suggestions Carousel)
		item {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				CatalogueSectionHeader(
					title = "Suggestions",
					onClick = { onNavigateToSubCategory(FeedSectionCategory.SUGGESTIONS) },
				)
				LazyRow(
					contentPadding = PaddingValues(horizontal = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					items(extensionCatalog.reversed(), key = { "sug_${it.url}" }) { catItem ->
						val isInLib = TitleNormalizer.normalize(catItem.title) in libraryNormalizedTitles
						CatalogueTallCoverCard(
							title = catItem.title,
							subtitle = "${(catItem.rating * 100).toInt()}% Tag Match • ${catItem.tags.firstOrNull() ?: "Manga"}",
							coverUrl = catItem.coverSeed,
							inLibrary = isInLib,
							unreadCountBadge = null,
							onClick = { onRequestAddCatalogManga(catItem) },
						)
					}
				}
			}
		}

		// 4. All-time favorites → (Interactive Drill-Down of Highest Rated)
		item {
			val allTimeList = remember(libraryItems, sourceFilteredCatalog) {
				libraryItems.sortedByDescending { it.manga.rating }
			}
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				CatalogueSectionHeader(
					title = "All-time favorites",
					onClick = { drillDownSection = "All-time favorites" },
				)
				LazyRow(
					contentPadding = PaddingValues(horizontal = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					items(allTimeList, key = { "fav_${it.manga.id}" }) { item ->
						CatalogueTallCoverCard(
							title = item.manga.title,
							subtitle = "★ ${"%.1f".format(item.manga.rating * 5f)} • ${item.totalChapters} Chapters",
							coverUrl = item.manga.coverUrl,
							inLibrary = true,
							unreadCountBadge = null,
							onClick = { onOpenDetails(item.manga.id) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CatalogueSectionHeader(
	title: String,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 4.dp)
			.testTag("catalogue_header_${title.lowercase().replace(" ", "_")}"),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleLarge,
			fontFamily = PlusJakartaDisplayFamily,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.primary,
		)
		Spacer(modifier = Modifier.width(8.dp))
		Icon(
			imageVector = Icons.AutoMirrored.Filled.ArrowForward,
			contentDescription = "Open $title",
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(20.dp),
		)
	}
}

@Composable
private fun CatalogueTallCoverCard(
	title: String,
	subtitle: String,
	coverUrl: String,
	inLibrary: Boolean,
	unreadCountBadge: Int?,
	onClick: () -> Unit,
) {
	Column(
		modifier = Modifier
			.width(156.dp)
			.clickable(onClick = onClick),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(0.68f)
				.clip(RoundedCornerShape(14.dp))
				.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
		) {
			MangaCoverArtwork(
				title = title,
				coverUrl = coverUrl,
				modifier = Modifier.fillMaxSize(),
			)

			// Top-Left "In Library" Pill Badge inset cleanly from corner
			if (inLibrary) {
				Surface(
					color = Color(0xFF5CE1E6),
					shape = RoundedCornerShape(6.dp),
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(top = 8.dp, start = 8.dp),
				) {
					Text(
						text = "In Library",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = Color(0xFF0F1419),
						maxLines = 1,
						softWrap = false,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					)
				}
			}

			// Optional Unread Pill Badge on Middle-Left
			if (unreadCountBadge != null && unreadCountBadge > 0) {
				Surface(
					color = Color(0xFFD8E2FF).copy(alpha = 0.92f),
					shape = RoundedCornerShape(8.dp),
					modifier = Modifier
						.align(Alignment.CenterStart)
						.padding(start = 8.dp),
				) {
					Text(
						text = "$unreadCountBadge",
						fontFamily = JetBrainsMonoFamily,
						fontWeight = FontWeight.Bold,
						fontSize = 11.sp,
						color = Color(0xFF111827),
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
					)
				}
			}
		}

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 6.dp, start = 2.dp, end = 2.dp),
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onBackground,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun FeedDedicatedUpdatesView(
	filteredUpdates: List<FeedUpdateItem>,
	groupedUpdates: Map<String, List<FeedUpdateItem>>,
	groupingMode: FeedGroupingMode,
	layoutMode: SectionViewLayout,
	gridColumns: Int,
	cardShape: CardCoverShape,
	showNewDropBadge: Boolean,
	showScanlator: Boolean,
	onlyUnread: Boolean,
	onSelectGrouping: (FeedGroupingMode) -> Unit,
	onToggleUnread: () -> Unit,
	onOpenReader: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 6.dp)
				.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			FeedGroupingMode.entries.forEach { mode ->
				FilterChip(
					selected = groupingMode == mode,
					onClick = { onSelectGrouping(mode) },
					label = { Text(mode.label, fontSize = 11.sp) },
				)
			}
			FilterChip(
				selected = onlyUnread,
				onClick = onToggleUnread,
				label = { Text("Unread Only", fontSize = 11.sp) },
			)
		}

		if (filteredUpdates.isEmpty()) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(32.dp),
				contentAlignment = Alignment.Center,
			) {
				Text("No chapter updates match this filter.")
			}
		} else if (layoutMode == SectionViewLayout.COMPACT_GRID || layoutMode == SectionViewLayout.COMFY_GRID) {
			val isCompactGrid = layoutMode == SectionViewLayout.COMPACT_GRID
			LazyVerticalGrid(
				columns = GridCells.Fixed(gridColumns.coerceIn(2, 6)),
				contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(filteredUpdates, key = { it.id }) { update ->
					CompactSectionGridCard(
						title = update.mangaItem.manga.title,
						subtitle = "Ch. ${update.chapter.number.toInt()} • ${update.dateBucketLabel}",
						coverUrl = update.mangaItem.manga.coverUrl,
						progress = if (update.chapter.isRead) 1f else 0f,
						cornerRadiusDp = cardShape.cornerRadiusDp,
						isCompactGrid = isCompactGrid,
						showProgressBar = true,
						onClick = { onOpenReader(update.mangaItem.manga.id, update.chapterIndex) },
					)
				}
			}
		} else {
			val isCompactRow = layoutMode == SectionViewLayout.COMPACT_ROWS
			LazyColumn(
				contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier
					.fillMaxSize()
					.testTag("feed_list_view"),
			) {
				groupedUpdates.forEach { (groupHeader, itemsForGroup) ->
					item(key = "feed_header_$groupHeader") {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 6.dp, bottom = 2.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = groupHeader,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onBackground,
							)
							Text(
								text = "${itemsForGroup.size} drops",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 10.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					items(itemsForGroup, key = { it.id }) { update ->
						FeedReleaseRow(
							update = update,
							isCompact = isCompactRow,
							cornerRadiusDp = cardShape.cornerRadiusDp,
							showNewDropBadge = showNewDropBadge,
							showScanlator = showScanlator,
							onOpenDetails = { onOpenDetails(update.mangaItem.manga.id) },
							onRead = { onOpenReader(update.mangaItem.manga.id, update.chapterIndex) },
						)
					}
				}
			}
		}
	}
}

/**
 * Reading Summary Category under Feed.
 */
@Composable
private fun FeedReadingSummaryView(
	libraryItems: List<LibraryMangaItem>,
	onOpenReader: (Long, Int) -> Unit,
	onOpenDetails: (Long) -> Unit,
) {
	val totalChaptersRead = remember(libraryItems) {
		libraryItems.sumOf { item -> item.chapters.count { it.isRead } }
	}
	val totalChaptersAll = remember(libraryItems) {
		libraryItems.sumOf { it.totalChapters }.coerceAtLeast(1)
	}
	val totalPagesTurned = totalChaptersRead * 19
	val estHoursRead = "%.1f".format(totalPagesTurned / 55f)
	val overallCompletion = (totalChaptersRead.toFloat() / totalChaptersAll.toFloat()).coerceIn(0f, 1f)

	val hasDebugMock = remember(libraryItems) { libraryItems.any { it.isDebugMock } }
	val weeklyActivity = remember(totalChaptersRead, libraryItems, hasDebugMock) {
		if (totalChaptersRead == 0) {
			listOf(
				"Mon" to 0,
				"Tue" to 0,
				"Wed" to 0,
				"Thu" to 0,
				"Fri" to 0,
				"Sat" to 0,
				"Sun" to 0,
			)
		} else if (hasDebugMock) {
			listOf(
				"Mon" to ((totalChaptersRead * 2) % 9 + 1),
				"Tue" to ((totalChaptersRead * 3) % 11 + 1),
				"Wed" to ((totalChaptersRead + 4) % 8 + 1),
				"Thu" to ((totalChaptersRead * 5) % 12 + 1),
				"Fri" to ((totalChaptersRead + 7) % 10 + 1),
				"Sat" to ((totalChaptersRead * 4) % 15 + 1),
				"Sun" to ((totalChaptersRead * 3) % 14 + 1),
			)
		} else {
			val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
			val counts = IntArray(7)
			val cal = java.util.Calendar.getInstance()
			libraryItems.forEach { item ->
				val readChapters = item.chapters.count { it.isRead }
				if (readChapters > 0 && item.lastReadAt > 0L) {
					cal.timeInMillis = item.lastReadAt
					val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
					val idx = when (dow) {
						java.util.Calendar.MONDAY -> 0
						java.util.Calendar.TUESDAY -> 1
						java.util.Calendar.WEDNESDAY -> 2
						java.util.Calendar.THURSDAY -> 3
						java.util.Calendar.FRIDAY -> 4
						java.util.Calendar.SATURDAY -> 5
						else -> 6
					}
					counts[idx] += readChapters
				}
			}
			dayLabels.mapIndexed { idx, label -> label to counts[idx] }
		}
	}
	val maxWeeklyDay = remember(weeklyActivity) {
		weeklyActivity.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
	}

	val genreCounts = remember(libraryItems) {
		libraryItems.flatMap { it.tags.map { t -> t.title } }
			.groupingBy { it }
			.eachCount()
			.entries
			.sortedByDescending { it.value }
			.take(5)
	}

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 112.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier
			.fillMaxSize()
			.testTag("feed_reading_summary_view"),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				shape = RoundedCornerShape(16.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = Icons.Default.Insights,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
						)
						Column {
							Text(
								text = "Reading Summary & Activity",
								style = MaterialTheme.typography.titleMedium,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Cumulative reading velocity, completion rate & top genres",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						SummaryStatTile(
							value = "$totalChaptersRead",
							label = "Chapters Read",
							modifier = Modifier.weight(1f),
						)
						SummaryStatTile(
							value = "$totalPagesTurned",
							label = "Pages Turned",
							modifier = Modifier.weight(1f),
						)
						SummaryStatTile(
							value = "${estHoursRead}h",
							label = "Reading Time",
							modifier = Modifier.weight(1f),
						)
					}

					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "Library Completion Rate",
								style = MaterialTheme.typography.labelMedium,
							)
							Text(
								text = "${(overallCompletion * 100).toInt()}% ($totalChaptersRead / $totalChaptersAll ch)",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 11.sp,
								color = MaterialTheme.colorScheme.primary,
							)
						}
						LinearProgressIndicator(
							progress = { overallCompletion },
							modifier = Modifier
								.fillMaxWidth()
								.height(6.dp)
								.clip(CircleShape),
							color = MaterialTheme.colorScheme.primary,
						)
					}
				}
			}
		}

		// 7-Day Reading Activity Bar Chart
		item {
			Card(
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				shape = RoundedCornerShape(16.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "7-DAY READING VELOCITY (CHAPTERS / DAY)",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
						val activeDaysCount = weeklyActivity.count { it.second > 0 }
						Text(
							text = "Streak: $activeDaysCount ${if (activeDaysCount == 1) "Day" else "Days"}",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 11.sp,
							color = if (activeDaysCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}

					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(110.dp),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.Bottom,
					) {
						weeklyActivity.forEach { (dayLabel, count) ->
							val barFraction = if (count == 0) {
								0.06f
							} else {
								(count.toFloat() / maxWeeklyDay.toFloat()).coerceIn(0.15f, 1f)
							}
							Column(
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.Bottom,
								modifier = Modifier.weight(1f),
							) {
								Text(
									text = "$count",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
								Spacer(modifier = Modifier.height(4.dp))
								Box(
									modifier = Modifier
										.width(20.dp)
										.height((72.dp * barFraction))
										.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
										.background(
											if (barFraction >= 0.8f) {
												MaterialTheme.colorScheme.primary
											} else {
												MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
											},
										),
								)
								Spacer(modifier = Modifier.height(4.dp))
								Text(
									text = dayLabel,
									style = MaterialTheme.typography.labelSmall,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}
				}
			}
		}

		// Top Genres Breakdown
		item {
			Card(
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				shape = RoundedCornerShape(16.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Text(
						text = "TOP GENRE AFFINITY",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					genreCounts.forEach { (genre, count) ->
						val ratio = (count.toFloat() / libraryItems.size.coerceAtLeast(1).toFloat()).coerceIn(0.15f, 1f)
						Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Text(text = genre, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
								Text(
									text = "$count series",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							LinearProgressIndicator(
								progress = { ratio },
								modifier = Modifier
									.fillMaxWidth()
									.height(4.dp)
									.clip(CircleShape),
								color = MaterialTheme.colorScheme.secondary,
							)
						}
					}
				}
			}
		}

		// Up-Next In-Progress Series
		item {
			Text(
				text = "IN-PROGRESS SERIES SUMMARY",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.primary,
			)
		}

		items(libraryItems.filter { it.unreadCount > 0 }, key = { "sum_${it.manga.id}" }) { item ->
			val resumeIdx = item.chapters.indexOfFirst { !it.isRead }.coerceAtLeast(0)
			Card(
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onOpenDetails(item.manga.id) },
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					MangaCoverArtwork(
						title = item.manga.title,
						coverUrl = item.manga.coverUrl,
						modifier = Modifier
							.width(44.dp)
							.aspectRatio(0.68f)
							.clip(RoundedCornerShape(6.dp)),
					)
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = item.manga.title,
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.SemiBold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = "${item.unreadCount} chapters left • ${(item.progressPercent * 100).toInt()}% complete",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					FilledTonalButton(
						onClick = { onOpenReader(item.manga.id, resumeIdx) },
						contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
						modifier = Modifier.height(30.dp),
					) {
						Text("Continue Ch. ${resumeIdx + 1}", fontSize = 11.sp)
					}
				}
			}
		}
	}
}

@Composable
private fun SummaryStatTile(
	value: String,
	label: String,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
		shape = RoundedCornerShape(12.dp),
		modifier = modifier,
	) {
		Column(
			modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = value,
				style = MaterialTheme.typography.titleLarge,
				fontFamily = JetBrainsMonoFamily,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
			)
		}
	}
}

/**
 * Dedicated Suggestions Category under Feed.
 */
@Composable
private fun FeedDedicatedSuggestionsView(
	libraryItems: List<LibraryMangaItem>,
	extensionCatalog: List<ExtensionCatalogManga>,
	libraryNormalizedTitles: Set<String>,
	defaultSource: String,
	searchQuery: String,
	onOpenDetails: (Long) -> Unit,
	onRequestAddCatalogManga: (ExtensionCatalogManga) -> Unit,
) {
	val topLibraryTags = remember(libraryItems) {
		libraryItems.flatMap { it.tags.map { t -> t.title } }.distinct().take(4)
	}

	val suggestions = remember(extensionCatalog, searchQuery) {
		if (searchQuery.isBlank()) {
			extensionCatalog
		} else {
			extensionCatalog.filter {
				it.title.contains(searchQuery, ignoreCase = true) ||
					it.tags.any { t -> t.contains(searchQuery, ignoreCase = true) }
			}
		}
	}

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxSize()
			.testTag("feed_suggestions_view"),
	) {
		item {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier.padding(12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						imageVector = Icons.Default.Lightbulb,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
					)
					Column {
						Text(
							text = "Personalized Suggestions (Default Source: $defaultSource)",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = if (topLibraryTags.isNotEmpty()) {
								"Matched against your top genres: ${topLibraryTags.joinToString(", ")}"
							} else {
								"Add series to your Library to generate personalized genre matches"
							},
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}

		if (suggestions.isEmpty()) {
			item(key = "ded_sug_empty_state") {
				Card(
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surface,
					),
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(24.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = "No suggestions yet",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Add series to your Library or browse extensions in Explore to unlock personalized suggestions.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
					}
				}
			}
		}

		items(suggestions, key = { "ded_sug_${it.url}" }) { item ->
			val isInLib = TitleNormalizer.normalize(item.title) in libraryNormalizedTitles
			Card(
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					MangaCoverArtwork(
						title = item.title,
						coverUrl = item.coverSeed,
						modifier = Modifier
							.width(60.dp)
							.aspectRatio(0.68f)
							.clip(RoundedCornerShape(8.dp)),
					)
					Column(
						modifier = Modifier.weight(1f),
						verticalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								text = item.title,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f, fill = false),
							)
							if (isInLib) {
								Surface(
									color = Color(0xFFD8E2FF),
									shape = RoundedCornerShape(4.dp),
								) {
									Text(
										text = "In Library",
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
										color = Color(0xFF111827),
										modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
									)
								}
							}
						}
						Text(
							text = "Because you read ${item.tags.take(2).joinToString(" & ")} • ${(item.rating * 100).toInt()}% Match",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.primary,
						)
						Text(
							text = item.description,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}
					Button(
						onClick = {
							val existing = libraryItems.find {
								TitleNormalizer.normalize(it.manga.title) == TitleNormalizer.normalize(item.title)
							}
							if (existing != null) {
								onOpenDetails(existing.manga.id)
							} else {
								onRequestAddCatalogManga(item)
							}
						},
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
					) {
						if (isInLib) {
							Text("Open", fontSize = 11.sp)
						} else {
							Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("Add", fontSize = 11.sp)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun FeedReleaseRow(
	update: FeedUpdateItem,
	isCompact: Boolean,
	cornerRadiusDp: Int,
	showNewDropBadge: Boolean,
	showScanlator: Boolean,
	onOpenDetails: () -> Unit,
	onRead: () -> Unit,
) {
	val rowHeight = if (isCompact) 62.dp else 86.dp
	val shape = RoundedCornerShape(cornerRadiusDp.coerceIn(4, 20).dp)

	Card(
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surface,
		),
		shape = shape,
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onOpenDetails)
			.testTag("feed_card_${update.id}"),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(rowHeight)
				.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			MangaCoverArtwork(
				title = UiSourceAndTitleFormatter.formatDisplayTitle(update.mangaItem.manga.title),
				coverUrl = update.mangaItem.manga.coverUrl,
				modifier = Modifier
					.fillMaxHeight()
					.aspectRatio(0.68f)
					.clip(RoundedCornerShape(6.dp)),
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = UiSourceAndTitleFormatter.formatDisplayTitle(update.mangaItem.manga.title),
						style = MaterialTheme.typography.bodyMedium,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (showNewDropBadge && update.isNewRelease) {
						Surface(
							color = Color(0xFF10B981).copy(alpha = 0.16f),
							shape = RoundedCornerShape(4.dp),
						) {
							Text(
								text = "NEW",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 8.sp,
								color = Color(0xFF10B981),
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
							)
						}
					}
				}

				Text(
					text = update.chapter.title,
					style = MaterialTheme.typography.bodySmall,
					color = if (update.isNewRelease) {
						MaterialTheme.colorScheme.onSurface
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)

				val cleanSource = UiSourceAndTitleFormatter.formatSourceName(update.mangaItem.manga.source)
				val cleanScanlator = UiSourceAndTitleFormatter.formatSourceName(update.chapter.scanlator ?: "Official")
				val scanlatorText = if (showScanlator) " • $cleanScanlator" else ""
				Text(
					text = "$cleanSource$scanlatorText",
					fontFamily = JetBrainsMonoFamily,
					fontSize = 9.sp,
					color = MaterialTheme.colorScheme.primary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			FilledTonalButton(
				onClick = onRead,
				contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
				modifier = Modifier
					.height(30.dp)
					.testTag("feed_read_${update.id}"),
			) {
				Icon(
					imageVector = Icons.Default.PlayArrow,
					contentDescription = "Read Chapter",
					modifier = Modifier.size(14.dp),
				)
				Spacer(modifier = Modifier.width(3.dp))
				Text("Ch. ${update.chapter.number.toInt()}", fontSize = 11.sp)
			}
		}
	}
}

@Composable
private fun CompactSectionGridCard(
	title: String,
	subtitle: String,
	coverUrl: String,
	progress: Float,
	cornerRadiusDp: Int,
	isCompactGrid: Boolean,
	showProgressBar: Boolean,
	onClick: () -> Unit,
) {
	val shape = RoundedCornerShape(cornerRadiusDp.dp)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(0.67f)
				.clip(shape)
				.border(1.dp, Color.White.copy(alpha = 0.10f), shape),
		) {
			MangaCoverArtwork(
				title = title,
				coverUrl = coverUrl,
				modifier = Modifier.fillMaxSize(),
			)

			if (isCompactGrid) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.align(Alignment.BottomCenter)
						.background(
							Brush.verticalGradient(
								colors = listOf(
									Color.Transparent,
									Color.Black.copy(alpha = 0.75f),
									Color.Black.copy(alpha = 0.92f),
								),
							),
						)
						.padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						fontSize = 11.sp,
						lineHeight = 13.sp,
						color = Color.White,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = subtitle,
						fontFamily = JetBrainsMonoFamily,
						fontSize = 9.sp,
						color = Color(0xFFCBD5E1),
						maxLines = 1,
					)
				}
			}

			if (showProgressBar && progress > 0f) {
				LinearProgressIndicator(
					progress = { progress.coerceIn(0f, 1f) },
					modifier = Modifier
						.fillMaxWidth()
						.height(3.dp)
						.align(Alignment.BottomCenter),
					color = MaterialTheme.colorScheme.primary,
					trackColor = Color.Black.copy(alpha = 0.4f),
				)
			}
		}

		if (!isCompactGrid) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 6.dp, start = 2.dp, end = 2.dp),
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(modifier = Modifier.height(2.dp))
				Text(
					text = subtitle,
					fontFamily = JetBrainsMonoFamily,
					fontSize = 10.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}
