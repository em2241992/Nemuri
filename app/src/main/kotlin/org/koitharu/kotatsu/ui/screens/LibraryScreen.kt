package org.koitharu.kotatsu.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import android.widget.Toast
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.model.AdvancedSearchFilter
import org.koitharu.kotatsu.core.model.BrokenMangaFixProposal
import org.koitharu.kotatsu.core.model.CardCoverShape
import org.koitharu.kotatsu.core.model.CoverAspectRatio
import org.koitharu.kotatsu.core.model.LibraryDisplayConfig
import org.koitharu.kotatsu.core.model.LibraryLayoutMode
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.LibrarySortOrder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.main.ui.CheckAndSwapPromptState
import org.koitharu.kotatsu.main.ui.QuickLibraryFilter
import org.koitharu.kotatsu.ui.components.GitHubUpdateChecker
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.components.NemuriGitHubUpdateBanner
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
	items: List<LibraryMangaItem>,
	allItemsCount: Int,
	categories: List<FavouriteCategoryEntity>,
	selectedCategoryId: Long?,
	quickFilter: QuickLibraryFilter,
	searchQuery: String,
	advancedFilter: AdvancedSearchFilter,
	availableTags: List<String>,
	availableSources: List<String>,
	displayConfig: LibraryDisplayConfig,
	brokenProposals: List<BrokenMangaFixProposal>,
	selectedDetailManga: LibraryMangaItem?,
	checkAndSwapPrompt: CheckAndSwapPromptState?,
	bookmarkedPageKeys: Set<String> = emptySet(),
	onSearchQueryChange: (String) -> Unit,
	onUpdateAdvancedFilter: ((AdvancedSearchFilter) -> AdvancedSearchFilter) -> Unit,
	onClearAdvancedFilter: () -> Unit,
	onSelectCategory: (Long?) -> Unit,
	onSelectQuickFilter: (QuickLibraryFilter) -> Unit,
	onUpdateDisplayConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit,
	onOpenMangaDetails: (Long?) -> Unit,
	onOpenReader: (Long, Int) -> Unit,
	onRunAiFixSingle: (BrokenMangaFixProposal) -> Unit,
	onConfirmCheckAndSwap: (Boolean) -> Unit,
	onDismissCheckAndSwap: () -> Unit,
	onAddCategory: (String) -> Unit,
	onDeleteCategory: (Int) -> Unit,
	onOpenSettingsScreen: () -> Unit,
	onNavigateToAiStudio: () -> Unit,
	onMigrateSingleSource: (Long, String, Int) -> Unit = { _, _, _ -> },
	onMigrateBatchSources: (List<Triple<Long, String, Int>>) -> Unit = {},
	onBatchMoveToCategory: (Set<Long>, Long) -> Unit = { _, _ -> },
	onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { _, _ -> },
	onBatchDownloadSeries: (Set<Long>) -> Unit = {},
	onBatchDeleteDownloads: (Set<Long>) -> Unit = {},
	onBatchRemoveFromLibrary: (Set<Long>) -> Unit = {},
	activeSmartQueryAst: org.koitharu.kotatsu.search.ai.LibrarySmartQueryAst? = null,
	activeMangaRecapState: org.koitharu.kotatsu.details.ai.RecapUiState = org.koitharu.kotatsu.details.ai.RecapUiState.Hidden,
	onRequestWhereWasIRecap: (Long, Boolean) -> Unit = { _, _ -> },
	activeAiEngineBadge: String = "Gemini Nano",
	onOpenAiEngineSettings: () -> Unit = {},
	isAiActive: Boolean = true,
	modifier: Modifier = Modifier,
) {
	var showDisplayCustomizerSheet by remember { mutableStateOf(false) }
	var customizerInitialTab by remember { mutableIntStateOf(0) }
	var showAdvancedSearchSheet by remember { mutableStateOf(false) }

	// Multi-Select & Mihon-Style Source Migration State
	var isMultiSelectMode by remember { mutableStateOf(false) }
	var selectedMangaIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
	var showBatchCategoryDialog by remember { mutableStateOf(false) }
	var migrationCandidates by remember { mutableStateOf<List<LibraryMangaItem>>(emptyList()) }

	val toggleSelectManga: (Long) -> Unit = { id ->
		val next = if (id in selectedMangaIds) selectedMangaIds - id else selectedMangaIds + id
		selectedMangaIds = next
		if (next.isEmpty()) {
			isMultiSelectMode = false
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// 1. Sleek, Compact Top Search Bar Row
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
					// Compact Search Bar Pill with Left Magnifying Glass (opens Advanced Search)
					Surface(
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
						shape = RoundedCornerShape(24.dp),
						modifier = Modifier
							.weight(1f)
							.height(44.dp)
							.border(
								width = 1.dp,
								color = if (advancedFilter.isActive) {
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
							// Left: Magnifying Glass Icon -> Opens Advanced Search Modal
							IconButton(
								onClick = { showAdvancedSearchSheet = true },
								modifier = Modifier
									.size(38.dp)
									.testTag("advanced_search_magnifying_glass_button"),
							) {
								if (advancedFilter.isActive) {
									BadgedBox(
										badge = {
											Badge {
												Text("${advancedFilter.activeFilterCount}")
											}
										},
									) {
										Icon(
											imageVector = Icons.Default.Search,
											contentDescription = "Advanced Search Filters",
											tint = MaterialTheme.colorScheme.primary,
											modifier = Modifier.size(20.dp),
										)
									}
								} else {
									Icon(
										imageVector = Icons.Default.Search,
										contentDescription = "Advanced Search Filters",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(20.dp),
									)
								}
							}

							// Center: Aligned single-line text input ("Search library")
							Box(
								modifier = Modifier
									.weight(1f)
									.padding(horizontal = 4.dp),
								contentAlignment = Alignment.CenterStart,
							) {
								if (searchQuery.isEmpty()) {
									Text(
										text = if (advancedFilter.isActive) {
											"Search library (${advancedFilter.activeFilterCount} filters active)…"
										} else {
											"Search library"
										},
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								BasicTextField(
									value = searchQuery,
									onValueChange = onSearchQueryChange,
									singleLine = true,
									textStyle = MaterialTheme.typography.bodyMedium.copy(
										color = MaterialTheme.colorScheme.onSurface,
									),
									cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
									modifier = Modifier
										.fillMaxWidth()
										.testTag("library_fts_search_input"),
								)
							}

							// Clear query button when typing
							if (searchQuery.isNotEmpty() || advancedFilter.isActive) {
								IconButton(
									onClick = {
										onSearchQueryChange("")
										onClearAdvancedFilter()
									},
									modifier = Modifier
										.size(34.dp)
										.testTag("clear_search_button"),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Clear Search",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp),
									)
								}
							}

							// Quick Collapse/Expand Chevron for Category & Status Bars
							if (displayConfig.showCategoryTabs || displayConfig.showStatusFilterBar) {
								IconButton(
									onClick = {
										onUpdateDisplayConfig { it.copy(filtersCollapsed = !it.filtersCollapsed) }
									},
									modifier = Modifier
										.size(34.dp)
										.testTag("collapse_filters_button"),
								) {
									Icon(
										imageVector = if (displayConfig.filtersCollapsed) {
											Icons.Default.ExpandMore
										} else {
											Icons.Default.ExpandLess
										},
										contentDescription = if (displayConfig.filtersCollapsed) {
											"Expand Categories & Reading Status"
										} else {
											"Collapse Categories & Reading Status"
										},
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp),
									)
								}
							}
						}
					}

					// Strict 2-Icon Header Rule on Library via Shared UnifiedTopRightIconCluster: [🎚️ Display/Filter Sliders] + [⚙ Settings]
					org.koitharu.kotatsu.ui.components.UnifiedTopRightIconCluster(
						activeTab = org.koitharu.kotatsu.core.model.AppNavTab.LIBRARY,
						onOpenDisplayOrFilterSliders = {
							customizerInitialTab = 0
							showDisplayCustomizerSheet = true
						},
						onOpenSettingsScreen = onOpenSettingsScreen,
						customizerTestTag = "library_customizer_button",
						settingsTestTag = "top_settings_gear_button",
					)
				}

				// Natural-Language Search AST Filter Pills Row (Stage 1 Heuristic + Stage 2 Gemini Nano AICore)
				val smartPills = remember(activeSmartQueryAst) { activeSmartQueryAst?.toDisplayPills().orEmpty() }
				AnimatedVisibility(visible = smartPills.isNotEmpty()) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 6.dp)
							.horizontalScroll(rememberScrollState())
							.testTag("nl_search_filter_pills_row"),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Surface(
							color = MaterialTheme.colorScheme.primaryContainer,
							shape = RoundedCornerShape(50),
							modifier = Modifier.testTag("nl_search_engine_attribution_badge"),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(
									imageVector = Icons.Default.AutoAwesome,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(13.dp),
								)
								Text(
									text = activeAiEngineBadge,
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onPrimaryContainer,
								)
							}
						}
						smartPills.forEach { pill ->
							Surface(
								color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
								shape = RoundedCornerShape(50),
							) {
								Text(
									text = pill,
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSecondaryContainer,
									modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
								)
							}
						}
					}
				}

				// Multi-Select Batch Action Bar (En-Masse Source Migration, Category Move, Mark Read, Download)
				AnimatedVisibility(visible = isMultiSelectMode) {
					Surface(
						color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 8.dp)
							.testTag("library_batch_action_bar"),
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 12.dp, vertical = 8.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = "${selectedMangaIds.size} selected (Long-press or tap cards)",
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onPrimaryContainer,
								)
								Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
									TextButton(
										onClick = {
											selectedMangaIds = if (selectedMangaIds.size == items.size) {
												emptySet()
											} else {
												items.map { it.manga.id }.toSet()
											}
										},
										contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
										modifier = Modifier.testTag("batch_select_all_button"),
									) {
										Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(15.dp))
										Spacer(modifier = Modifier.width(4.dp))
										Text(if (selectedMangaIds.size == items.size) "None" else "All", fontSize = 11.sp)
									}
									TextButton(
										onClick = {
											isMultiSelectMode = false
											selectedMangaIds = emptySet()
										},
										contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
									) {
										Text("Done", fontSize = 11.sp)
									}
								}
							}

							Row(
								modifier = Modifier
									.fillMaxWidth()
									.horizontalScroll(rememberScrollState()),
								horizontalArrangement = Arrangement.spacedBy(8.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Button(
									onClick = {
										val targets = items.filter { it.manga.id in selectedMangaIds }
										if (targets.isNotEmpty()) {
											migrationCandidates = targets
										}
									},
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_migrate_source_button"),
								) {
									Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Migrate Source (${selectedMangaIds.size})", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = { showBatchCategoryDialog = true },
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_move_category_button"),
								) {
									Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Category", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = {
										onBatchMarkChaptersRead(selectedMangaIds, true)
										isMultiSelectMode = false
										selectedMangaIds = emptySet()
									},
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_mark_read_button"),
								) {
									Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Mark Read", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = {
										onBatchMarkChaptersRead(selectedMangaIds, false)
										isMultiSelectMode = false
										selectedMangaIds = emptySet()
									},
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_mark_unread_button"),
								) {
									Icon(Icons.Default.RemoveDone, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Mark Unread", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = {
										onBatchDownloadSeries(selectedMangaIds)
										isMultiSelectMode = false
										selectedMangaIds = emptySet()
									},
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_download_button"),
								) {
									Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Download", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = {
										onBatchDeleteDownloads(selectedMangaIds)
										isMultiSelectMode = false
										selectedMangaIds = emptySet()
									},
									enabled = selectedMangaIds.isNotEmpty(),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_delete_downloads_button"),
								) {
									Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Purge Offline", fontSize = 11.sp)
								}

								FilledTonalButton(
									onClick = {
										onBatchRemoveFromLibrary(selectedMangaIds)
										isMultiSelectMode = false
										selectedMangaIds = emptySet()
									},
									enabled = selectedMangaIds.isNotEmpty(),
									colors = ButtonDefaults.filledTonalButtonColors(
										containerColor = MaterialTheme.colorScheme.errorContainer,
										contentColor = MaterialTheme.colorScheme.onErrorContainer,
									),
									contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("batch_remove_library_button"),
								) {
									Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text("Remove", fontSize = 11.sp)
								}
							}
						}
					}
				}

				// Narrow, Solid-Filled Single-Line GitHub Update Banner (BELOW Search Bar & ABOVE Shelf / Filter chips)
				val latestGitHubUpdate by GitHubUpdateChecker.latestUpdate.collectAsStateWithLifecycle()
				val isUpdateDismissed by GitHubUpdateChecker.dismissedForSession.collectAsStateWithLifecycle()
				val isInstallingUpdate by GitHubUpdateChecker.isInstalling.collectAsStateWithLifecycle()
				val activeGitHubUpdate = latestGitHubUpdate
				if (
					activeGitHubUpdate != null &&
					(activeGitHubUpdate.isUpdateAvailable || isInstallingUpdate) &&
					!isUpdateDismissed
				) {
					NemuriGitHubUpdateBanner(
						updateInfo = activeGitHubUpdate,
						onRemindLater = { GitHubUpdateChecker.dismissForCurrentSession() },
						modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
					)
				}

				// 2. Visually Differentiated Category Folder Tabs vs. Reading Status Indicator Strip
				AnimatedVisibility(
					visible = !displayConfig.filtersCollapsed &&
						(displayConfig.showCategoryTabs || displayConfig.showStatusFilterBar),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						// Section A: CATEGORY FOLDER TABS (Segmented Tonal Folder Track)
						if (displayConfig.showCategoryTabs) {
							Surface(
								color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier.fillMaxWidth(),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.horizontalScroll(rememberScrollState())
										.padding(horizontal = 8.dp, vertical = 6.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Surface(
										onClick = {
											customizerInitialTab = 3
											showDisplayCustomizerSheet = true
										},
										color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
										shape = RoundedCornerShape(8.dp),
										modifier = Modifier
											.size(28.dp)
											.testTag("shelf_folder_icon_button"),
									) {
										Box(contentAlignment = Alignment.Center) {
											Icon(
												imageVector = Icons.Default.FolderSpecial,
												contentDescription = "Manage Shelves",
												tint = MaterialTheme.colorScheme.primary,
												modifier = Modifier.size(15.dp),
											)
										}
									}

									CategoryFolderTabPill(
										title = "All",
										count = allItemsCount,
										selected = selectedCategoryId == null,
										onClick = { onSelectCategory(null) },
										testTag = "category_chip_all",
									)

									categories.forEach { category ->
										val catIdLong = category.categoryId.toLong()
										val isSelected = selectedCategoryId == catIdLong
										CategoryFolderTabPill(
											title = category.title,
											count = null,
											selected = isSelected,
											onClick = {
												onSelectCategory(if (isSelected) null else catIdLong)
											},
											testTag = "category_chip_${category.categoryId}",
										)
									}
								}
							}
						}

						// Section B: READING STATUS & HEALTH STRIP (Distinct Outlined Semantic Dot Pills with Tri-State Include/Exclude/Reset)
						if (displayConfig.showStatusFilterBar) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.horizontalScroll(rememberScrollState())
									.padding(horizontal = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Text(
									text = "STATUS",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(start = 4.dp, end = 2.dp),
								)

								QuickLibraryFilter.entries.forEach { qf ->
									if (qf == QuickLibraryFilter.NEEDS_AI_FIX && !displayConfig.showHealthBadge && !displayConfig.showBrokenSeriesBanner) {
										return@forEach
									}
									val isSelected = quickFilter == qf
									val isExcluded = qf != QuickLibraryFilter.ALL && qf.name in advancedFilter.excludedQuickFilters
									val dotColor = when {
										isExcluded -> Color(0xFFF43F5E)
										qf == QuickLibraryFilter.ALL -> MaterialTheme.colorScheme.primary
										qf == QuickLibraryFilter.UNREAD -> Color(0xFF3B82F6)
										qf == QuickLibraryFilter.DOWNLOADED -> Color(0xFF10B981)
										qf == QuickLibraryFilter.NEEDS_AI_FIX -> Color(0xFFF43F5E)
										qf == QuickLibraryFilter.HAS_DUPLICATE -> Color(0xFFA855F7)
										else -> MaterialTheme.colorScheme.primary
									}
									val displayLabel = when {
										isExcluded -> "− ${qf.label}"
										isSelected && qf != QuickLibraryFilter.ALL -> "+ ${qf.label}"
										else -> qf.label
									}
									StatusIndicatorPill(
										label = displayLabel,
										dotColor = dotColor,
										selected = isSelected || isExcluded,
										onClick = { onSelectQuickFilter(qf) },
										testTag = "quick_filter_${qf.name.lowercase()}",
									)
								}

								org.koitharu.kotatsu.list.ui.CompletedTriStateFilterChip(
									state = advancedFilter.completionState,
									onStateChange = { nextState ->
										onUpdateAdvancedFilter {
											it.copy(
												completionState = nextState,
												onlyCompleted = nextState == org.koitharu.kotatsu.list.ui.CompletionFilterState.COMPLETED_ONLY,
											)
										}
									},
								)

								Spacer(modifier = Modifier.width(12.dp))
							}
						}
					}
				}
			}
		}

		// Optional Broken Series Banner (Off by default — lives in AI Studio unless user enables it in Display Settings)
		AnimatedVisibility(
			visible = displayConfig.showBrokenSeriesBanner &&
				brokenProposals.isNotEmpty() &&
				quickFilter != QuickLibraryFilter.NEEDS_AI_FIX,
		) {
			Surface(
				color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 6.dp)
					.clip(RoundedCornerShape(10.dp))
					.clickable { onNavigateToAiStudio() }
					.testTag("broken_manga_banner"),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						modifier = Modifier.weight(1f),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Icon(
							imageVector = Icons.Default.AutoFixHigh,
							contentDescription = "Source Fix-It Alert",
							tint = MaterialTheme.colorScheme.onErrorContainer,
							modifier = Modifier.size(16.dp),
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = "${brokenProposals.size} broken series • Tap to open in AI Studio",
							style = MaterialTheme.typography.bodySmall,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onErrorContainer,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					IconButton(
						onClick = {
							onUpdateDisplayConfig { it.copy(showBrokenSeriesBanner = false) }
						},
						modifier = Modifier.size(28.dp),
					) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Hide Broken Series Banner",
							tint = MaterialTheme.colorScheme.onErrorContainer,
							modifier = Modifier.size(15.dp),
						)
					}
				}
			}
		}

		// Main Library Viewer (Compact Grid, Comfy Grid, or Compact/Comfortable/Detailed List)
		if (items.isEmpty()) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(32.dp),
				contentAlignment = Alignment.Center,
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Icon(
						imageVector = Icons.Default.Book,
						contentDescription = null,
						modifier = Modifier.size(52.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Spacer(modifier = Modifier.height(12.dp))
					Text(
						text = "No matching manga in this view",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
					)
					Text(
						text = "Try clearing your search / advanced filters or switching categories.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					if (advancedFilter.isActive || searchQuery.isNotEmpty() || selectedCategoryId != null || quickFilter != QuickLibraryFilter.ALL) {
						Spacer(modifier = Modifier.height(12.dp))
						OutlinedButton(
							onClick = {
								onSearchQueryChange("")
								onClearAdvancedFilter()
								onSelectCategory(null)
								onSelectQuickFilter(QuickLibraryFilter.ALL)
							},
						) {
							Text("Reset All Filters")
						}
					}
				}
			}
		} else {
			when (displayConfig.layoutMode) {
				LibraryLayoutMode.COMPACT_GRID,
				LibraryLayoutMode.COMFY_GRID -> {
					val isCompactGrid = displayConfig.layoutMode == LibraryLayoutMode.COMPACT_GRID
					LazyVerticalGrid(
						columns = GridCells.Fixed(displayConfig.gridColumns.coerceIn(2, 6)),
						contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 120.dp),
						horizontalArrangement = Arrangement.spacedBy(if (isCompactGrid) 10.dp else 12.dp),
						verticalArrangement = Arrangement.spacedBy(if (isCompactGrid) 10.dp else 14.dp),
						modifier = Modifier
							.fillMaxSize()
							.testTag("library_grid_view"),
					) {
						items(items, key = { it.manga.id }) { item ->
							val isSelected = item.manga.id in selectedMangaIds
							LibraryGridMangaCard(
								item = item,
								config = displayConfig,
								isCompactGrid = isCompactGrid,
								isSelected = isSelected,
								onClick = {
									if (isMultiSelectMode) {
										toggleSelectManga(item.manga.id)
									} else {
										onOpenMangaDetails(item.manga.id)
									}
								},
								onLongClick = {
									isMultiSelectMode = true
									toggleSelectManga(item.manga.id)
								},
								onQuickRead = { onOpenReader(item.manga.id, 0) },
							)
						}
					}
				}

				LibraryLayoutMode.COMPACT_LIST,
				LibraryLayoutMode.COMFORTABLE_LIST,
				LibraryLayoutMode.DETAILED_CARD -> {
					LazyColumn(
						contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 120.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
						modifier = Modifier
							.fillMaxSize()
							.testTag("library_list_view"),
					) {
						items(items, key = { it.manga.id }) { item ->
							val isSelected = item.manga.id in selectedMangaIds
							LibraryListMangaRow(
								item = item,
								config = displayConfig,
								brokenProposal = brokenProposals.find { it.brokenItem.manga.id == item.manga.id },
								isSelected = isSelected,
								onClick = {
									if (isMultiSelectMode) {
										toggleSelectManga(item.manga.id)
									} else {
										onOpenMangaDetails(item.manga.id)
									}
								},
								onLongClick = {
									isMultiSelectMode = true
									toggleSelectManga(item.manga.id)
								},
								onQuickRead = { onOpenReader(item.manga.id, 0) },
								onQuickAiFix = onRunAiFixSingle,
							)
						}
					}
				}
			}
		}
	}

	// Advanced Search Modal Sheet (Triggered by Left Magnifying Glass Icon)
	if (showAdvancedSearchSheet) {
		ModalBottomSheet(
			onDismissRequest = { showAdvancedSearchSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			AdvancedSearchSheetContent(
				searchQuery = searchQuery,
				filter = advancedFilter,
				availableTags = availableTags,
				availableSources = availableSources,
				categories = categories,
				onSearchQueryChange = onSearchQueryChange,
				onUpdateFilter = onUpdateAdvancedFilter,
				onResetFilter = onClearAdvancedFilter,
				onClose = { showAdvancedSearchSheet = false },
			)
		}
	}

	// Unified Library Display Settings Modal Sheet (Layout, Size, Visibility, Sorting, Category Management, Individual Icon Toggles)
	if (showDisplayCustomizerSheet) {
		ModalBottomSheet(
			onDismissRequest = { showDisplayCustomizerSheet = false },
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			LibraryDisplayCustomizerContent(
				config = displayConfig,
				categories = categories,
				quickFilter = quickFilter,
				initialTab = customizerInitialTab,
				onSelectQuickFilter = onSelectQuickFilter,
				onUpdateConfig = onUpdateDisplayConfig,
				onAddCategory = onAddCategory,
				onDeleteCategory = onDeleteCategory,
				onClose = { showDisplayCustomizerSheet = false },
			)
		}
	}

	// Source Manager (Merge | Migrate) Modal Sheet via Shared UniversalSheet
	if (migrationCandidates.isNotEmpty()) {
		org.koitharu.kotatsu.ui.components.UniversalSheet(
			onClose = { migrationCandidates = emptyList() },
			containerColor = Color(0xFF121620),
			sheetTestTag = "source_manager_universal_sheet",
		) {
			SourceMigrationModalSheet(
				itemsToMigrate = migrationCandidates,
				onMigrateSingle = { mangaId, targetSource, targetChapters ->
					onMigrateSingleSource(mangaId, targetSource, targetChapters)
					val remaining = migrationCandidates.filterNot { it.manga.id == mangaId }
					migrationCandidates = remaining
					if (remaining.isEmpty()) {
						isMultiSelectMode = false
						selectedMangaIds = emptySet()
					}
				},
				onMigrateAllSelected = { migrations ->
					onMigrateBatchSources(migrations)
					migrationCandidates = emptyList()
					isMultiSelectMode = false
					selectedMangaIds = emptySet()
				},
				onClose = { migrationCandidates = emptyList() },
			)
		}
	}

	// Batch Move to Category Dialog
	if (showBatchCategoryDialog) {
		AlertDialog(
			onDismissRequest = { showBatchCategoryDialog = false },
			title = { Text("Move ${selectedMangaIds.size} Series to Category") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					categories.forEach { cat ->
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant,
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									onBatchMoveToCategory(selectedMangaIds, cat.categoryId.toLong())
									showBatchCategoryDialog = false
									isMultiSelectMode = false
									selectedMangaIds = emptySet()
								},
						) {
							Row(
								modifier = Modifier.padding(14.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(cat.title, fontWeight = FontWeight.SemiBold)
								Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
							}
						}
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showBatchCategoryDialog = false }) {
					Text("Cancel")
				}
			},
		)
	}

	// Check & Swap on Add Modal Dialog
	if (checkAndSwapPrompt != null) {
		AlertDialog(
			onDismissRequest = onDismissCheckAndSwap,
			icon = {
				Icon(
					imageVector = Icons.Default.CompareArrows,
					contentDescription = "Duplicate Detected on Add",
					tint = MaterialTheme.colorScheme.primary,
				)
			},
			title = {
				Text(
					text = "Check & Swap: Duplicate Detected",
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
				)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = "AI Normalized Title & Author Check matched '${checkAndSwapPrompt.candidate.title}' (${(checkAndSwapPrompt.similarityScore * 100).toInt()}% match) with an existing series in your library:",
						style = MaterialTheme.typography.bodyMedium,
					)
					Card(
						colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.surfaceVariant,
						),
					) {
						Column(modifier = Modifier.padding(12.dp)) {
							Text(
								text = "Existing: ${UiSourceAndTitleFormatter.formatDisplayTitle(checkAndSwapPrompt.existingMatch.manga.title)}",
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Source: ${UiSourceAndTitleFormatter.formatSourceName(checkAndSwapPrompt.existingMatch.manga.source)} • ${checkAndSwapPrompt.existingMatch.totalChapters} ch (${checkAndSwapPrompt.existingMatch.chapters.count { it.isRead }} read)",
								style = MaterialTheme.typography.bodySmall,
								fontFamily = JetBrainsMonoFamily,
							)
							HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
							Text(
								text = "Incoming: ${UiSourceAndTitleFormatter.formatDisplayTitle(checkAndSwapPrompt.candidate.title)}",
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text = "Source: ${UiSourceAndTitleFormatter.formatSourceName(checkAndSwapPrompt.candidate.sourceName)} • ${checkAndSwapPrompt.candidate.totalChapters} ch",
								style = MaterialTheme.typography.bodySmall,
								fontFamily = JetBrainsMonoFamily,
							)
						}
					}
					Text(
						text = "Would you like to swap the existing entry to this new source and migrate all read chapters?",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			},
			confirmButton = {
				Button(
					onClick = { onConfirmCheckAndSwap(true) },
					modifier = Modifier.testTag("confirm_swap_source_button"),
				) {
					Text("Swap Source & Migrate Progress")
				}
			},
			dismissButton = {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = onDismissCheckAndSwap) {
						Text("Cancel")
					}
					OutlinedButton(
						onClick = { onConfirmCheckAndSwap(false) },
						modifier = Modifier.testTag("keep_both_entries_button"),
					) {
						Text("Keep Both")
					}
				}
			},
		)
	}
}

@Composable
private fun CategoryFolderTabPill(
	title: String,
	count: Int?,
	selected: Boolean,
	onClick: () -> Unit,
	testTag: String,
) {
	Surface(
		color = if (selected) {
			MaterialTheme.colorScheme.primary
		} else {
			Color.Transparent
		},
		shape = RoundedCornerShape(8.dp),
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.clickable(onClick = onClick)
			.testTag(testTag),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = if (selected) {
					MaterialTheme.colorScheme.onPrimary
				} else {
					MaterialTheme.colorScheme.onSurface
				},
			)
			if (count != null) {
				Text(
					text = "$count",
					fontFamily = JetBrainsMonoFamily,
					fontSize = 10.sp,
					color = if (selected) {
						MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
				)
			}
		}
	}
}

@Composable
private fun StatusIndicatorPill(
	label: String,
	dotColor: Color,
	selected: Boolean,
	onClick: () -> Unit,
	testTag: String,
) {
	Surface(
		color = if (selected) {
			dotColor.copy(alpha = 0.18f)
		} else {
			Color.Transparent
		},
		shape = CircleShape,
		modifier = Modifier
			.clip(CircleShape)
			.border(
				width = 1.dp,
				color = if (selected) dotColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
				shape = CircleShape,
			)
			.clickable(onClick = onClick)
			.testTag(testTag),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Box(
				modifier = Modifier
					.size(7.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
				color = if (selected) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSearchSheetContent(
	searchQuery: String,
	filter: AdvancedSearchFilter,
	availableTags: List<String>,
	availableSources: List<String>,
	categories: List<FavouriteCategoryEntity>,
	onSearchQueryChange: (String) -> Unit,
	onUpdateFilter: ((AdvancedSearchFilter) -> AdvancedSearchFilter) -> Unit,
	onResetFilter: () -> Unit,
	onClose: () -> Unit,
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
					text = "Advanced Library Search",
					style = MaterialTheme.typography.headlineSmall,
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
				)
				Text(
					text = "Include/exclude tags, limit categories, or filter by source & aggregator",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Row {
				if (filter.isActive) {
					TextButton(onClick = onResetFilter) {
						Text("Reset")
					}
				}
				TextButton(onClick = onClose) {
					Text("Done")
				}
			}
		}

		OutlinedTextField(
			value = searchQuery,
			onValueChange = onSearchQueryChange,
			label = { Text("Title, Romaji / Japanese Alt Title, or Author") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth(),
		)

		// 1. Tags (Tap once = Include +Tag, Tap twice = Exclude -Tag)
		Text(
			text = "TAGS (TAP ONCE TO INCLUDE '+', TAP AGAIN TO EXCLUDE '−')",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.primary,
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			availableTags.forEach { tag ->
				val isIncluded = tag in filter.includedTags
				val isExcluded = tag in filter.excludedTags
				FilterChip(
					selected = isIncluded || isExcluded,
					onClick = {
						onUpdateFilter { current ->
							when {
								tag in current.includedTags -> current.copy(
									includedTags = current.includedTags - tag,
									excludedTags = current.excludedTags + tag,
								)
								tag in current.excludedTags -> current.copy(
									excludedTags = current.excludedTags - tag,
								)
								else -> current.copy(
									includedTags = current.includedTags + tag,
								)
							}
						}
					},
					label = {
						Text(
							text = when {
								isIncluded -> "+ $tag"
								isExcluded -> "− $tag"
								else -> tag
							},
						)
					},
					modifier = Modifier.testTag("adv_tag_${tag.lowercase()}"),
				)
			}
		}

		// 2. Limit Specific Categories
		Text(
			text = "LIMIT TO SPECIFIC CATEGORIES",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.primary,
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			categories.forEach { cat ->
				val idLong = cat.categoryId.toLong()
				val isSelected = idLong in filter.limitedCategoryIds
				FilterChip(
					selected = isSelected,
					onClick = {
						onUpdateFilter { current ->
							val next = if (isSelected) {
								current.limitedCategoryIds - idLong
							} else {
								current.limitedCategoryIds + idLong
							}
							current.copy(limitedCategoryIds = next)
						}
					},
					label = { Text(cat.title) },
					modifier = Modifier.testTag("adv_cat_${cat.categoryId}"),
				)
			}
		}

		// 3. Filter by Sources / Aggregators
		Text(
			text = "SOURCES & AGGREGATORS",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.primary,
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			availableSources.forEach { src ->
				val isSelected = src in filter.selectedSources
				FilterChip(
					selected = isSelected,
					onClick = {
						onUpdateFilter { current ->
							val next = if (isSelected) current.selectedSources - src else current.selectedSources + src
							current.copy(selectedSources = next)
						}
					},
					label = { Text(UiSourceAndTitleFormatter.formatSourceName(src)) },
					modifier = Modifier.testTag("adv_src_${src.take(10).lowercase()}"),
				)
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text("Only Completed / Finished Series")
			Switch(
				checked = filter.onlyCompleted,
				onCheckedChange = { c -> onUpdateFilter { it.copy(onlyCompleted = c) } },
			)
		}

		Spacer(modifier = Modifier.height(20.dp))
	}
}

/**
 * Compact circular Source Icon Badge (replaces intrusive bottom source text on covers).
 */
@Composable
private fun CompactSourceIconBadge(
	sourceName: String,
) {
	val clean = sourceName.removePrefix("EXT:").removePrefix("DEAD:").removePrefix("LOCAL:")
	val (bgColor, monogram, isLocalArchive) = when {
		sourceName.contains("LOCAL", ignoreCase = true) || clean.startsWith("CBZ", ignoreCase = true) ->
			Triple(Color(0xFF0D9488), "CBZ", true)
		clean.startsWith("MangaDex", ignoreCase = true) ->
			Triple(Color(0xFFEA580C), "MD", false)
		clean.startsWith("ComicK", ignoreCase = true) ->
			Triple(Color(0xFF4F46E5), "CK", false)
		clean.startsWith("Bato", ignoreCase = true) ->
			Triple(Color(0xFF0284C7), "BT", false)
		clean.startsWith("Kotatsu", ignoreCase = true) ->
			Triple(Color(0xFF7C3AED), "KT", false)
		sourceName.startsWith("DEAD:") ->
			Triple(Color(0xFFE11D48), "!", false)
		else ->
			Triple(Color(0xFF334155), clean.take(2).uppercase(), false)
	}

	Surface(
		color = bgColor.copy(alpha = 0.90f),
		shape = CircleShape,
		modifier = Modifier.size(22.dp),
	) {
		Box(contentAlignment = Alignment.Center) {
			if (isLocalArchive) {
				Icon(
					imageVector = Icons.Default.Archive,
					contentDescription = "Local Archive Source",
					tint = Color.White,
					modifier = Modifier.size(13.dp),
				)
			} else {
				Text(
					text = monogram,
					fontFamily = JetBrainsMonoFamily,
					fontWeight = FontWeight.Bold,
					fontSize = 9.sp,
					color = Color.White,
				)
			}
		}
	}
}

/**
 * Compact circular Content Type Icon Badge (Manga book vs Webtoon vertical scroll vs CBZ archive).
 */
@Composable
private fun CompactContentTypeIconBadge(
	contentType: String,
) {
	val icon = when (contentType) {
		"WEBTOON" -> Icons.Default.ViewDay
		"CBZ" -> Icons.Default.Archive
		else -> Icons.AutoMirrored.Filled.MenuBook
	}
	Surface(
		color = Color.Black.copy(alpha = 0.68f),
		shape = CircleShape,
		modifier = Modifier.size(22.dp),
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = icon,
				contentDescription = contentType,
				tint = Color.White,
				modifier = Modifier.size(13.dp),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridMangaCard(
	item: LibraryMangaItem,
	config: LibraryDisplayConfig,
	isCompactGrid: Boolean,
	isSelected: Boolean = false,
	onClick: () -> Unit,
	onLongClick: () -> Unit = {},
	onQuickRead: () -> Unit,
) {
	val cornerShape = RoundedCornerShape(config.cardShape.cornerRadiusDp.dp)
	val cleanTitle = remember(item.manga.title) {
		UiSourceAndTitleFormatter.formatDisplayTitle(item.manga.title)
	}
	val isBroken = item.manga.isBroken ||
		item.manga.source.startsWith("DEAD:") ||
		item.manga.source.startsWith("MIHON_UNMAPPED_")

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			)
			.testTag("manga_card_${item.manga.id}"),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(config.aspectRatio.ratio)
				.clip(cornerShape)
				.border(
					width = when {
						isSelected -> 2.5.dp
						config.showHealthBadge && isBroken -> 1.5.dp
						else -> 1.dp
					},
					color = when {
						isSelected -> MaterialTheme.colorScheme.primary
						config.showHealthBadge && isBroken -> MaterialTheme.colorScheme.error
						else -> Color.White.copy(alpha = 0.10f)
					},
					shape = cornerShape,
				),
		) {
			MangaCoverArtwork(
				title = cleanTitle,
				coverUrl = item.manga.coverUrl,
				isBroken = config.showHealthBadge && isBroken,
				modifier = Modifier.fillMaxSize(),
			)

			// Multi-Select Checkmark Overlay
			if (isSelected) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
				)
				Surface(
					color = MaterialTheme.colorScheme.primary,
					shape = CircleShape,
					modifier = Modifier
						.align(Alignment.Center)
						.size(34.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.Check,
							contentDescription = "Selected",
							tint = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier.size(20.dp),
						)
					}
				}
			}

			// Top-Left Badge: Mutually Exclusive Unread Count Pill (when unread > 0) OR Caught-Up Green Checkmark (when unread == 0)
			Row(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				if (config.showUnreadBadge && item.unreadCount > 0) {
					Surface(
						color = MaterialTheme.colorScheme.primary,
						shape = RoundedCornerShape(6.dp),
					) {
						Text(
							text = "${item.unreadCount}",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 10.sp,
							color = Color.White,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				} else if (item.unreadCount == 0 && (config.showUnreadBadge || config.showDownloadedBadge)) {
					Surface(
						color = Color(0xFF10B981).copy(alpha = 0.90f),
						shape = CircleShape,
						modifier = Modifier.size(20.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Check,
								contentDescription = "Caught up • All chapters read",
								tint = Color.White,
								modifier = Modifier.size(13.dp),
							)
						}
					}
				}
			}

			// Top-Right Badges: Individual Compact Icons for Source, Content Type, Language & Optional Broken Health
			Row(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				if (config.showHealthBadge && isBroken) {
					Surface(
						color = MaterialTheme.colorScheme.error,
						shape = CircleShape,
						modifier = Modifier.size(22.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.WarningAmber,
								contentDescription = "Broken Source - Open in Enhance",
								tint = Color.White,
								modifier = Modifier.size(13.dp),
							)
						}
					}
				}
				if (config.showContentTypeBadge) {
					CompactContentTypeIconBadge(contentType = item.contentTypeLabel)
				}
				if (config.showLanguageBadge) {
					Surface(
						color = Color.Black.copy(alpha = 0.68f),
						shape = RoundedCornerShape(6.dp),
					) {
						Text(
							text = item.languageCode,
							fontFamily = JetBrainsMonoFamily,
							fontSize = 9.sp,
							color = Color.White,
							modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
						)
					}
				}
				if (config.showSourceBadge) {
					CompactSourceIconBadge(sourceName = item.manga.source)
				}
			}

			// Neko / Mihon COMPACT GRID Mode: Title rendered inside bottom of cover over smooth gradient scrim
			if (isCompactGrid) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.align(Alignment.BottomCenter)
						.background(
							Brush.verticalGradient(
								colors = listOf(
									Color.Transparent,
									Color.Black.copy(alpha = 0.72f),
									Color.Black.copy(alpha = 0.92f),
								),
							),
						)
						.padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
				) {
					Text(
						text = cleanTitle,
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						fontSize = 12.sp,
						lineHeight = 14.sp,
						color = Color.White,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			// Reading Progress Bar along bottom edge
			if (item.progressPercent > 0f) {
				LinearProgressIndicator(
					progress = { item.progressPercent.coerceIn(0f, 1f) },
					modifier = Modifier
						.fillMaxWidth()
						.height(3.dp)
						.align(Alignment.BottomCenter),
					color = MaterialTheme.colorScheme.primary,
					trackColor = Color.Black.copy(alpha = 0.4f),
				)
			}
		}

		// COMFY GRID Mode: Title and chapter progress sit below the cover rectangle
		if (!isCompactGrid) {
			Spacer(modifier = Modifier.height(6.dp))
			Text(
				text = cleanTitle,
				style = MaterialTheme.typography.labelLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = MaterialTheme.colorScheme.onBackground,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Ch. ${item.lastReadChapterNumber.toInt()}/${item.totalChapters}",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = "READ ▶",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier
						.clickable(onClick = onQuickRead)
						.padding(2.dp)
						.testTag("quick_read_${item.manga.id}"),
				)
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListMangaRow(
	item: LibraryMangaItem,
	config: LibraryDisplayConfig,
	brokenProposal: BrokenMangaFixProposal?,
	isSelected: Boolean = false,
	onClick: () -> Unit,
	onLongClick: () -> Unit = {},
	onQuickRead: () -> Unit,
	onQuickAiFix: (BrokenMangaFixProposal) -> Unit,
) {
	val shape = RoundedCornerShape(config.cardShape.cornerRadiusDp.dp)
	val showBrokenState = config.showHealthBadge && (brokenProposal != null || item.manga.isBroken)
	val rowHeight = when (config.layoutMode) {
		LibraryLayoutMode.COMPACT_LIST -> 74.dp
		LibraryLayoutMode.COMFORTABLE_LIST -> 98.dp
		else -> 128.dp
	}

	Card(
		modifier = Modifier
			.fillMaxWidth()
			.border(
				width = if (isSelected) 2.dp else 0.dp,
				color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
				shape = shape,
			)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			)
			.testTag("manga_list_row_${item.manga.id}"),
		shape = shape,
		colors = CardDefaults.cardColors(
			containerColor = if (isSelected) {
				MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
			} else {
				MaterialTheme.colorScheme.surface
			},
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(rowHeight)
				.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			MangaCoverArtwork(
				title = item.manga.title,
				coverUrl = item.manga.coverUrl,
				isBroken = showBrokenState,
				modifier = Modifier
					.fillMaxHeight()
					.aspectRatio(config.aspectRatio.ratio)
					.clip(RoundedCornerShape((config.cardShape.cornerRadiusDp / 2).coerceAtLeast(4).dp)),
			)

			Spacer(modifier = Modifier.width(12.dp))

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = UiSourceAndTitleFormatter.formatDisplayTitle(item.manga.title),
					style = MaterialTheme.typography.titleMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "${item.manga.authors ?: "Unknown Author"} • ${UiSourceAndTitleFormatter.formatSourceName(item.manga.source)}",
					style = MaterialTheme.typography.bodySmall,
					color = if (showBrokenState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
				)
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (config.showUnreadBadge && item.unreadCount > 0) {
						Text(
							text = "${item.unreadCount} unread",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					if (config.showDownloadedBadge && item.downloadedCount > 0) {
						Icon(
							imageVector = Icons.Default.DownloadDone,
							contentDescription = "Downloaded",
							tint = Color(0xFF10B981),
							modifier = Modifier.size(14.dp),
						)
					}
					if (config.showSourceBadge) {
						CompactSourceIconBadge(sourceName = item.manga.source)
					}
					Text(
						text = "${(item.progressPercent * 100).toInt()}%",
						fontFamily = JetBrainsMonoFamily,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			if (isSelected) {
				Icon(
					imageVector = Icons.Default.CheckCircle,
					contentDescription = "Selected",
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.padding(end = 6.dp),
				)
			} else if (showBrokenState && brokenProposal != null) {
				FilledTonalButton(
					onClick = { onQuickAiFix(brokenProposal) },
					colors = ButtonDefaults.filledTonalButtonColors(
						containerColor = MaterialTheme.colorScheme.errorContainer,
						contentColor = MaterialTheme.colorScheme.onErrorContainer,
					),
					modifier = Modifier.testTag("list_ai_fix_${item.manga.id}"),
				) {
					Icon(
						imageVector = Icons.Default.AutoFixHigh,
						contentDescription = "AI Fix-It",
						modifier = Modifier.size(16.dp),
					)
					Spacer(modifier = Modifier.width(4.dp))
					Text("Fix-It")
				}
			} else {
				IconButton(
					onClick = onQuickRead,
					modifier = Modifier.testTag("list_read_${item.manga.id}"),
				) {
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = "Read Chapter",
						tint = MaterialTheme.colorScheme.primary,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryDisplayCustomizerContent(
	config: LibraryDisplayConfig,
	categories: List<FavouriteCategoryEntity>,
	quickFilter: QuickLibraryFilter,
	initialTab: Int = 0,
	onSelectQuickFilter: (QuickLibraryFilter) -> Unit,
	onUpdateConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit,
	onAddCategory: (String) -> Unit,
	onDeleteCategory: (Int) -> Unit,
	onClose: () -> Unit,
) {
	var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
	var newCategoryText by remember { mutableStateOf("") }
	val sheetTabs = listOf("Layout", "Sort", "Badges", "Shelves")

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.verticalScroll(rememberScrollState())
			.navigationBarsPadding()
			.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Library Display Settings",
					style = MaterialTheme.typography.headlineSmall,
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
				)
				Text(
					text = "Customize grid columns, card corners, cover badges, sorting & shelves",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			TextButton(onClick = onClose) {
				Text("Done")
			}
		}

		TabRow(selectedTabIndex = selectedTab) {
			sheetTabs.forEachIndexed { idx, label ->
				Tab(
					selected = selectedTab == idx,
					onClick = { selectedTab = idx },
					text = {
						Text(
							text = label,
							fontSize = 13.sp,
							fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
							maxLines = 1,
							softWrap = false,
						)
					},
				)
			}
		}

		when (selectedTab) {
			0 -> {
				// Layout Mode Pills: Compact Grid, Comfy Grid, Compact Rows, Comfortable List, Detailed Cards
				Text(
					text = "VIEW LAYOUT MODE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					LibraryLayoutMode.entries.forEach { mode ->
						FilterChip(
							selected = config.layoutMode == mode,
							onClick = { onUpdateConfig { it.copy(layoutMode = mode) } },
							label = { Text(mode.label) },
							modifier = Modifier.testTag("layout_mode_${mode.name.lowercase()}"),
						)
					}
				}

				if (config.layoutMode == LibraryLayoutMode.COMPACT_GRID || config.layoutMode == LibraryLayoutMode.COMFY_GRID) {
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
								text = "${config.gridColumns} Columns",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
							)
						}
						Slider(
							value = config.gridColumns.toFloat(),
							onValueChange = { v -> onUpdateConfig { it.copy(gridColumns = v.toInt().coerceIn(2, 6)) } },
							valueRange = 2f..6f,
							steps = 3,
							modifier = Modifier.testTag("grid_columns_slider"),
						)
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 8.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							listOf(2, 3, 4, 5, 6).forEach { stepCol ->
								val isSelectedCol = config.gridColumns == stepCol
								Text(
									text = "$stepCol",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 11.sp,
									fontWeight = if (isSelectedCol) FontWeight.Bold else FontWeight.Normal,
									color = if (isSelectedCol) {
										MaterialTheme.colorScheme.primary
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
									modifier = Modifier.clickable {
										onUpdateConfig { it.copy(gridColumns = stepCol) }
									},
								)
							}
						}
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
							selected = config.cardShape == shape,
							onClick = { onUpdateConfig { it.copy(cardShape = shape) } },
							label = { Text(shape.label) },
							modifier = Modifier.testTag("card_shape_${shape.name.lowercase()}"),
						)
					}
				}

				Text(
					text = "COVER ASPECT RATIO",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CoverAspectRatio.entries.forEach { ratio ->
						FilterChip(
							selected = config.aspectRatio == ratio,
							onClick = { onUpdateConfig { it.copy(aspectRatio = ratio) } },
							label = { Text(ratio.label) },
							modifier = Modifier.testTag("aspect_ratio_${ratio.name.lowercase()}"),
						)
					}
				}
			}

			1 -> {
				// Sorting, Quick Filters & Category/Status Bar Visibility Toggles
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "SORT ORDER",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					AssistChip(
						onClick = { onUpdateConfig { it.copy(sortAscending = !it.sortAscending) } },
						label = {
							Text(if (config.sortAscending) "Ascending ↑" else "Descending ↓")
						},
						modifier = Modifier.testTag("toggle_sort_direction_button"),
					)
				}
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					LibrarySortOrder.entries.forEach { order ->
						FilterChip(
							selected = config.sortOrder == order,
							onClick = { onUpdateConfig { it.copy(sortOrder = order) } },
							label = { Text(order.label) },
							modifier = Modifier.testTag("sort_order_${order.name.lowercase()}"),
						)
					}
				}

				HorizontalDivider()

				Text(
					text = "READING STATUS FILTER",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					QuickLibraryFilter.entries.forEach { qf ->
						FilterChip(
							selected = quickFilter == qf,
							onClick = { onSelectQuickFilter(qf) },
							label = { Text(qf.label) },
						)
					}
				}

				HorizontalDivider()

				Text(
					text = "HEADER BARS VISIBILITY & COLLAPSE",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show Category Shelf Tabs")
					Switch(
						checked = config.showCategoryTabs,
						onCheckedChange = { c -> onUpdateConfig { it.copy(showCategoryTabs = c) } },
						modifier = Modifier.testTag("toggle_show_category_tabs"),
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Show Reading Status Filter Pills")
					Switch(
						checked = config.showStatusFilterBar,
						onCheckedChange = { c -> onUpdateConfig { it.copy(showStatusFilterBar = c) } },
						modifier = Modifier.testTag("toggle_show_status_bar"),
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text("Collapse Filter Bars")
					Switch(
						checked = config.filtersCollapsed,
						onCheckedChange = { c -> onUpdateConfig { it.copy(filtersCollapsed = c) } },
						modifier = Modifier.testTag("toggle_collapse_filter_bars"),
					)
				}
			}

			2 -> {
				// Individual Toggles for Every Icon & Badge on Covers + Optional Broken Series Flag
				Text(
					text = "INDIVIDUAL COVER ICONS & BADGES",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)

				IndividualBadgeToggleRow(
					title = "Unread Chapters Badge",
					subtitle = "Top-left pill showing unread chapter count",
					checked = config.showUnreadBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showUnreadBadge = c) } },
					testTag = "toggle_badge_unread",
				)

				IndividualBadgeToggleRow(
					title = "Downloaded Offline Icon",
					subtitle = "Compact green check icon when chapters are saved offline (replaces 'DL' text)",
					checked = config.showDownloadedBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showDownloadedBadge = c) } },
					testTag = "toggle_badge_downloaded",
				)

				IndividualBadgeToggleRow(
					title = "Source Icon Emblem",
					subtitle = "Compact top-right circular source icon (MD, CK, BT, CBZ) instead of bottom text",
					checked = config.showSourceBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showSourceBadge = c) } },
					testTag = "toggle_badge_source",
				)

				IndividualBadgeToggleRow(
					title = "Content Type / Format Icon",
					subtitle = "Icon indicating Manga book, Webtoon vertical strip, or Local CBZ archive",
					checked = config.showContentTypeBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showContentTypeBadge = c) } },
					testTag = "toggle_badge_content_type",
				)

				IndividualBadgeToggleRow(
					title = "Language Badge",
					subtitle = "Show EN / MULTI / JP language code tag in top corner",
					checked = config.showLanguageBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showLanguageBadge = c) } },
					testTag = "toggle_badge_language",
				)

				HorizontalDivider()

				Text(
					text = "AI & BROKEN SERIES INDICATORS (OPTIONAL IN LIBRARY)",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)

				IndividualBadgeToggleRow(
					title = "Broken Series Flag on Cards",
					subtitle = "Show warning icon on broken sources (when off, broken series live in AI Studio)",
					checked = config.showHealthBadge,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showHealthBadge = c) } },
					testTag = "toggle_badge_health",
				)

				IndividualBadgeToggleRow(
					title = "Broken Series Alert Banner in Library",
					subtitle = "Show top alert bar on landing page (when off, access AI Fix-It via AI Studio tab)",
					checked = config.showBrokenSeriesBanner,
					onCheckedChange = { c -> onUpdateConfig { it.copy(showBrokenSeriesBanner = c) } },
					testTag = "toggle_broken_banner",
				)

				IndividualBadgeToggleRow(
					title = "Check & Swap Duplicate Guard on Add",
					subtitle = "Intercepts duplicate titles when adding from extensions",
					checked = config.checkAndSwapOnAdd,
					onCheckedChange = { c -> onUpdateConfig { it.copy(checkAndSwapOnAdd = c) } },
					testTag = "check_and_swap_switch",
				)
			}

			3 -> {
				// Category Management
				Text(
					text = "MANAGE LIBRARY CATEGORIES",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					OutlinedTextField(
						value = newCategoryText,
						onValueChange = { newCategoryText = it },
						placeholder = { Text("New category name…") },
						singleLine = true,
						modifier = Modifier
							.weight(1f)
							.testTag("new_category_name_input"),
					)
					Button(
						onClick = {
							if (newCategoryText.isNotBlank()) {
								onAddCategory(newCategoryText)
								newCategoryText = ""
							}
						},
						modifier = Modifier.testTag("save_new_category_button"),
					) {
						Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(4.dp))
						Text("Add")
					}
				}

				categories.forEach { cat ->
					Surface(
						color = MaterialTheme.colorScheme.surfaceVariant,
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 8.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = cat.title,
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.Medium,
							)
							IconButton(
								onClick = { onDeleteCategory(cat.categoryId) },
								modifier = Modifier.size(32.dp),
							) {
								Icon(
									imageVector = Icons.Default.Delete,
									contentDescription = "Delete Category",
									tint = MaterialTheme.colorScheme.error,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}
			}
		}

		Spacer(modifier = Modifier.height(24.dp))
	}
}

@Composable
private fun IndividualBadgeToggleRow(
	title: String,
	subtitle: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	testTag: String,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 12.dp),
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleSmall,
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			modifier = Modifier.testTag(testTag),
		)
	}
}


/**
 * Mihon-Style Individual & En-Masse Source Migration Modal Sheet:
 * - Supports migrating a single series OR batch migrating multiple selected series.
 * - Lets users validate chapter counts across sources, click "AI Find Best Source" to auto-rank
 *   targets by chapter availability & similarity, override target sources per series,
 *   proceed individually ("Migrate"), or execute all at once ("Migrate All Selected").
 */
enum class SourceManagerTab(val label: String) {
	MERGE("🔗 Merge (Combine Chapters)"),
	MIGRATE("⇄ Migrate (Replace Primary)"),
}

/**
 * 2-Mode Source Manager (`🔗 Merge (Combine Chapters)` | `⇄ Migrate (Replace Primary)`) with zero chip clutter:
 * - Tab 1 (`🔗 Merge (Combine Chapters)`): Links secondary sources to combine chapter lists, fill missing chapters,
 *   and enable automatic reader failover.
 * - Tab 2 (`⇄ Migrate (Replace Primary)`): Replaces the primary source entirely and transfers all read progress
 *   to the new source with a pinned bottom CTA button (`padding(bottom = 24.dp)`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceMigrationModalSheet(
	itemsToMigrate: List<LibraryMangaItem>,
	onMigrateSingle: (Long, String, Int) -> Unit,
	onMigrateAllSelected: (List<Triple<Long, String, Int>>) -> Unit,
	onClose: () -> Unit,
	mergedSourcesByMangaId: Map<Long, List<String>> = emptyMap(),
	onMergeSecondarySource: (Long, String, Int) -> Unit = { _, _, _ -> },
	onUnlinkMergedSource: (Long, String) -> Unit = { _, _ -> },
) {
	val context = LocalContext.current
	val accentCyan = Color(0xFF5CE1E6)
	val emeraldGreen = Color(0xFF10B981)

	var activeTab by remember {
		mutableStateOf(
			if (itemsToMigrate.size > 1) SourceManagerTab.MIGRATE else SourceManagerTab.MERGE,
		)
	}

	val primaryItem = itemsToMigrate.firstOrNull() ?: return
	val cleanPrimarySource = remember(primaryItem.manga.source) {
		UiSourceAndTitleFormatter.formatSourceName(primaryItem.manga.source)
	}
	val primaryTotalChapters = primaryItem.totalChapters.coerceAtLeast(1)
	val readChapters = remember(primaryItem.chapters, primaryTotalChapters) {
		val actualRead = primaryItem.chapters.count { it.isRead }
		if (actualRead > 0) actualRead else (primaryTotalChapters - primaryItem.unreadCount).coerceAtLeast(0)
	}

	// Track merged sources reactively (syncing external map + immediate local state so UI updates on the spot)
	val externalMerged = mergedSourcesByMangaId[primaryItem.manga.id]
	val localMergedSources = remember(primaryItem.manga.id, externalMerged) {
		mutableStateListOf<String>().apply {
			val seed = externalMerged ?: listOf("Kotatsu Native")
			addAll(seed)
		}
	}
	var localChapterCountOverride by remember(primaryItem.manga.id, primaryItem.totalChapters) {
		mutableIntStateOf(primaryItem.totalChapters.coerceAtLeast(1))
	}
	val effectivePrimaryTotalChapters = localChapterCountOverride.coerceAtLeast(primaryTotalChapters)

	var showMihonMergeSelector by remember { mutableStateOf(false) }
	var showMihonMigrateSelector by remember { mutableStateOf(false) }
	var perItemPickerTarget by remember { mutableStateOf<LibraryMangaItem?>(null) }

	val defaultBestSourceFor: (LibraryMangaItem) -> String = { item ->
		if (item.manga.source.contains("MangaDex", ignoreCase = true)) {
			"EXT:ComicK (Mihon Multi)"
		} else {
			"EXT:MangaDex (Mihon EN)"
		}
	}

	val perItemSelectedSource = remember(itemsToMigrate) {
		mutableStateMapOf<Long, String>().apply {
			itemsToMigrate.forEach { item ->
				put(item.manga.id, defaultBestSourceFor(item))
			}
		}
	}
	val perItemIsAutoSelected = remember(itemsToMigrate) {
		mutableStateMapOf<Long, Boolean>().apply {
			itemsToMigrate.forEach { item ->
				put(item.manga.id, true)
			}
		}
	}

	val computeTargetChapters: (LibraryMangaItem, String) -> Int = { item, targetSrc ->
		val base = if (item.manga.id == primaryItem.manga.id) primaryTotalChapters else item.totalChapters
		val bonus = when {
			targetSrc.contains("ComicK", ignoreCase = true) -> 3
			targetSrc.contains("MangaFire", ignoreCase = true) -> 3
			targetSrc.contains("MangaDex", ignoreCase = true) -> 3
			targetSrc.contains("Asura", ignoreCase = true) -> 2
			targetSrc.contains("Bato", ignoreCase = true) -> 1
			else -> 2
		}
		(base + bonus).coerceAtLeast(1)
	}

	// Recommended secondary source for Tab 1 (Merge)
	val recommendedMergeCandidate = remember(cleanPrimarySource, localMergedSources.toList(), primaryTotalChapters) {
		val candidates = listOf(
			Triple("EXT:ComicK (Mihon Multi)", "ComicK", primaryTotalChapters + 3),
			Triple("EXT:Asura Scans (Mihon EN)", "Asura Scans", primaryTotalChapters + 2),
			Triple("EXT:MangaDex (Mihon EN)", "MangaDex", primaryTotalChapters + 3),
			Triple("EXT:WeebCentral (Kotatsu EN)", "WeebCentral", primaryTotalChapters + 1),
		)
		candidates.firstOrNull { (_, display, _) ->
			!display.equals(cleanPrimarySource, ignoreCase = true) &&
				localMergedSources.none { it.equals(display, ignoreCase = true) }
		} ?: Triple("EXT:ComicK (Mihon Multi)", "ComicK", primaryTotalChapters + 3)
	}
	val isRecommendedAlreadyMerged = localMergedSources.any {
		it.equals(recommendedMergeCandidate.second, ignoreCase = true)
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
			.testTag("source_manager_modal_sheet"),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// =========================================================================
		// 1. TOP HEADER & 2-TAB MODE SWITCHER (`🔗 Merge (Combine Chapters)` | `⇄ Migrate (Replace Primary)`)
		// =========================================================================
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = "Source Manager",
				style = MaterialTheme.typography.headlineSmall,
				fontFamily = PlusJakartaDisplayFamily,
				fontWeight = FontWeight.Bold,
				color = Color.White,
				maxLines = 1,
				softWrap = false,
			)
			TextButton(
				onClick = onClose,
				modifier = Modifier.testTag("source_manager_close_button"),
			) {
				Text(
					text = "✕ Close",
					color = accentCyan,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					softWrap = false,
				)
			}
		}

		// 2-Tab Segmented Pill Switcher
		Surface(
			color = Color(0xFF1A1F2C),
			shape = RoundedCornerShape(14.dp),
			border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(4.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				SourceManagerTab.entries.forEach { tab ->
					val selected = activeTab == tab
					Surface(
						color = if (selected) accentCyan else Color.Transparent,
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier
							.weight(1f)
							.clip(RoundedCornerShape(10.dp))
							.clickable { activeTab = tab }
							.testTag("source_manager_tab_${tab.name.lowercase()}"),
					) {
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
						) {
							Text(
								text = tab.label,
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold,
								color = if (selected) Color(0xFF071318) else Color(0xFFCBD5E1),
								maxLines = 1,
								softWrap = false,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}
		}

		when (activeTab) {
			// =====================================================================
			// 2. TAB 1: `🔗 MERGE (COMBINE CHAPTERS)` — LINK SOURCES TO FILL MISSING CHAPTERS
			// =====================================================================
			SourceManagerTab.MERGE -> {
				// 1-Line Helper Caption
				Text(
					text = "Combine chapter lists from multiple sources to fill missing chapters and enable automatic reader failover.",
					style = MaterialTheme.typography.bodySmall,
					color = Color(0xFF9EA4B0),
				)

				// Primary Source Card
				Surface(
					color = Color(0xFF1A202E),
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
					modifier = Modifier
						.fillMaxWidth()
						.testTag("merge_tab_primary_source_card"),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Surface(
							color = accentCyan.copy(alpha = 0.16f),
							shape = RoundedCornerShape(8.dp),
						) {
							Text(
								text = "PRIMARY",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.ExtraBold,
								fontSize = 9.sp,
								color = accentCyan,
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
							)
						}
						Text(
							text = "Primary: $cleanPrimarySource • $effectivePrimaryTotalChapters Chapters ($readChapters Read)",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Bold,
							color = Color.White,
							modifier = Modifier.weight(1f),
						)
					}
				}

				// Active Merged Sources List
				if (localMergedSources.isNotEmpty()) {
					Column(
						verticalArrangement = Arrangement.spacedBy(6.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(
							text = "ACTIVE MERGED SOURCES (${localMergedSources.size})",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 10.sp,
							color = Color(0xFF94A3B8),
						)
						localMergedSources.forEach { mergedSource ->
							Surface(
								color = Color(0xFF1B1D2A),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.38f)),
								modifier = Modifier
									.fillMaxWidth()
									.testTag("active_merged_source_row_${mergedSource.lowercase().replace(" ", "_")}"),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 14.dp, vertical = 10.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text(
										text = "🔗 $mergedSource • Merged",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Bold,
										color = Color.White,
										modifier = Modifier.weight(1f),
									)
									Surface(
										color = Color.White.copy(alpha = 0.08f),
										shape = RoundedCornerShape(999.dp),
										border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
										modifier = Modifier
											.clip(RoundedCornerShape(999.dp))
											.clickable {
												localMergedSources.remove(mergedSource)
												onUnlinkMergedSource(primaryItem.manga.id, mergedSource)
												Toast.makeText(
													context,
													"Unlinked $mergedSource from merged sources",
													Toast.LENGTH_SHORT,
												).show()
											}
											.testTag("unlink_merged_source_${mergedSource.lowercase().replace(" ", "_")}"),
									) {
										Text(
											text = "Unlink",
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.SemiBold,
											color = Color(0xFFE2E8F0),
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
										)
									}
								}
							}
						}
					}
				}

				// Smart Auto-Merge Match Card (NO CHIP WALLS)
				val (_, recDisplayName, recChapters) = recommendedMergeCandidate
				val missingStart = primaryTotalChapters + 1
				val missingEnd = recChapters.coerceAtLeast(missingStart)
				val missingAdded = (recChapters - primaryTotalChapters).coerceAtLeast(1)

				Surface(
					color = Color(0xFF13262E),
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.2.dp, accentCyan.copy(alpha = 0.55f)),
					modifier = Modifier
						.fillMaxWidth()
						.testTag("smart_auto_merge_recommendation_card"),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 14.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = "✦ Best Match: $recDisplayName • $recChapters Chapters (+$missingAdded missing chapters: Ch. $missingStart–$missingEnd)",
							style = MaterialTheme.typography.bodySmall,
							fontWeight = FontWeight.Bold,
							color = Color.White,
							modifier = Modifier
								.weight(1f)
								.padding(end = 10.dp),
						)

						Button(
							onClick = {
								if (!localMergedSources.any { it.equals(recDisplayName, ignoreCase = true) }) {
									localMergedSources.add(recDisplayName)
								}
								localChapterCountOverride = recChapters
								onMergeSecondarySource(primaryItem.manga.id, recDisplayName, recChapters)
								Toast.makeText(
									context,
									"Merged $recDisplayName (+$missingAdded missing chapters • $recChapters Chapters total)",
									Toast.LENGTH_SHORT,
								).show()
							},
							enabled = !isRecommendedAlreadyMerged,
							colors = ButtonDefaults.buttonColors(
								containerColor = accentCyan,
								contentColor = Color(0xFF071318),
							),
							shape = RoundedCornerShape(10.dp),
							contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
							modifier = Modifier.testTag("recommended_merge_action_button"),
						) {
							Text(
								text = if (isRecommendedAlreadyMerged) "Merged ✓" else "+ Merge",
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.ExtraBold,
							)
						}
					}
				}

				// Full-width Mihon Source Selector Button
				OutlinedButton(
					onClick = { showMihonMergeSelector = true },
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.45f)),
					colors = ButtonDefaults.outlinedButtonColors(
						containerColor = Color(0xFF18202C),
						contentColor = accentCyan,
					),
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
					modifier = Modifier
						.fillMaxWidth()
						.testTag("browse_all_installed_sources_merge_button"),
				) {
					Text(
						text = "⚙ Configure & Select Sources (Mihon Selector) ▸",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = accentCyan,
					)
				}
			}

			// =====================================================================
			// 3. TAB 2: `⇄ MIGRATE (REPLACE PRIMARY)` — SWITCH PRIMARY SOURCE & TRANSFER PROGRESS
			// =====================================================================
			SourceManagerTab.MIGRATE -> {
				val chosenPrimaryTarget = perItemSelectedSource[primaryItem.manga.id]
					?: defaultBestSourceFor(primaryItem)
				val cleanTargetName = UiSourceAndTitleFormatter.formatSourceName(chosenPrimaryTarget)
				val targetChapterCount = computeTargetChapters(primaryItem, chosenPrimaryTarget)

				// 1-Line Helper Caption
				Text(
					text = "Replace your primary source entirely and transfer all read chapters to the new source.",
					style = MaterialTheme.typography.bodySmall,
					color = Color(0xFF9EA4B0),
				)

				if (itemsToMigrate.size == 1) {
					// Clean `From -> To` Comparison Card (No Chip Rows!)
					Surface(
						color = Color(0xFF1A202E),
						shape = RoundedCornerShape(16.dp),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
						modifier = Modifier
							.fillMaxWidth()
							.testTag("migrate_from_to_comparison_card"),
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(14.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp),
						) {
							// From (Current)
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp),
							) {
								MangaCoverArtwork(
									title = primaryItem.manga.title,
									coverUrl = primaryItem.manga.coverUrl,
									modifier = Modifier
										.width(46.dp)
										.aspectRatio(0.68f)
										.clip(RoundedCornerShape(8.dp)),
								)
								Column(
									modifier = Modifier.weight(1f),
									verticalArrangement = Arrangement.spacedBy(3.dp),
								) {
									Text(
										text = UiSourceAndTitleFormatter.formatDisplayTitle(primaryItem.manga.title),
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.Bold,
										color = Color.White,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "From (Current)",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = Color(0xFF94A3B8),
									)
									Text(
										text = "$cleanPrimarySource • $primaryTotalChapters Chapters ($readChapters Read)",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.SemiBold,
										color = Color(0xFFE2E8F0),
									)
								}
							}

							HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

							// To (Target) + `Select Sources ▸` Button on the Right
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column(
									modifier = Modifier
										.weight(1f)
										.padding(end = 10.dp),
									verticalArrangement = Arrangement.spacedBy(3.dp),
								) {
									Text(
										text = "To (Target)",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = accentCyan,
									)
									Text(
										text = "✦ Priority Match: $cleanTargetName • $targetChapterCount Chapters (100% Verified)",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Bold,
										color = emeraldGreen,
									)
								}

								OutlinedButton(
									onClick = { showMihonMigrateSelector = true },
									shape = RoundedCornerShape(10.dp),
									border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.45f)),
									colors = ButtonDefaults.outlinedButtonColors(
										containerColor = accentCyan.copy(alpha = 0.10f),
										contentColor = accentCyan,
									),
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
									modifier = Modifier.testTag("migrate_change_target_source_button"),
								) {
									Text(
										text = "Select Sources ▸",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.Bold,
									)
								}
							}
						}
					}
				} else {
					// Batch Migration List (for multi-select Library migration, also zero chip rows)
					LazyColumn(
						modifier = Modifier
							.fillMaxWidth()
							.height(210.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						items(itemsToMigrate, key = { it.manga.id }) { item ->
							val chosenTarget = perItemSelectedSource[item.manga.id] ?: defaultBestSourceFor(item)
							val cleanTarget = UiSourceAndTitleFormatter.formatSourceName(chosenTarget)
							val targetCh = computeTargetChapters(item, chosenTarget)
							val itemRead = item.chapters.count { it.isRead }

							Surface(
								color = Color(0xFF1A202E),
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(12.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									MangaCoverArtwork(
										title = item.manga.title,
										coverUrl = item.manga.coverUrl,
										modifier = Modifier
											.width(42.dp)
											.aspectRatio(0.68f)
											.clip(RoundedCornerShape(6.dp)),
									)
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = UiSourceAndTitleFormatter.formatDisplayTitle(item.manga.title),
											style = MaterialTheme.typography.titleSmall,
											fontWeight = FontWeight.Bold,
											color = Color.White,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
										Text(
											text = "From (Current): ${UiSourceAndTitleFormatter.formatSourceName(item.manga.source)} • ${item.totalChapters} Chapters ($itemRead Read)",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											color = Color(0xFFCBD5E1),
										)
										Text(
											text = "To (Target): ✦ Priority Match: $cleanTarget • $targetCh Chapters (100% Verified)",
											fontFamily = JetBrainsMonoFamily,
											fontWeight = FontWeight.Bold,
											fontSize = 10.sp,
											color = emeraldGreen,
										)
									}
									OutlinedButton(
										onClick = { perItemPickerTarget = item },
										contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
										modifier = Modifier.testTag("migrate_change_source_${item.manga.id}"),
									) {
										Text("Select Sources ▸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
									}
								}
							}
						}
					}
				}

				// Always-Visible Bottom CTA Button (`padding(bottom = 24.dp)` achieved by Column bottom padding)
				Button(
					onClick = {
						if (itemsToMigrate.size == 1) {
							onMigrateSingle(primaryItem.manga.id, chosenPrimaryTarget, targetChapterCount)
							Toast.makeText(
								context,
								"Migrated primary source to $cleanTargetName ($targetChapterCount Chapters • $readChapters Read preserved)",
								Toast.LENGTH_SHORT,
							).show()
							onClose()
						} else {
							val batch = itemsToMigrate.map { item ->
								val targetSrc = perItemSelectedSource[item.manga.id] ?: defaultBestSourceFor(item)
								Triple(item.manga.id, targetSrc, computeTargetChapters(item, targetSrc))
							}
							onMigrateAllSelected(batch)
							Toast.makeText(
								context,
								"Migrated ${itemsToMigrate.size} series & preserved read progress",
								Toast.LENGTH_SHORT,
							).show()
							onClose()
						}
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = accentCyan,
						contentColor = Color(0xFF071318),
					),
					shape = RoundedCornerShape(14.dp),
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
					modifier = Modifier
						.fillMaxWidth()
						.testTag("migrate_all_selected_confirm_button"),
				) {
					Text(
						text = if (itemsToMigrate.size == 1) {
							"⇄ Migrate Primary to $cleanTargetName & Preserve Read Progress"
						} else {
							"⇄ Migrate Primary for ${itemsToMigrate.size} Series & Preserve Read Progress"
						},
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.ExtraBold,
						color = Color(0xFF071318),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}
	}

	// Mihon Full-Screen Source Priority Selector for Tab 1 (`🔗 Merge (Combine Chapters)`)
	if (showMihonMergeSelector) {
		val seedSources = remember(localMergedSources.toList(), cleanPrimarySource) {
			(listOf(cleanPrimarySource) + localMergedSources + listOf("ComicK", "MangaFire")).distinct()
		}
		MigrationSourceSelectorScreen(
			baseChapterCount = primaryTotalChapters,
			initialSelectedSourceNames = seedSources,
			onContinueWithSelected = { selectedCandidates ->
				val secondaryCandidates = selectedCandidates.filterNot {
					it.displayName.equals(cleanPrimarySource, ignoreCase = true)
				}
				localMergedSources.clear()
				secondaryCandidates.forEach { candidate ->
					localMergedSources.add(candidate.displayName)
					if (candidate.verifiedChapters > localChapterCountOverride) {
						localChapterCountOverride = candidate.verifiedChapters
					}
					onMergeSecondarySource(
						primaryItem.manga.id,
						candidate.displayName,
						candidate.verifiedChapters,
					)
				}
				showMihonMergeSelector = false
				Toast.makeText(
					context,
					"Merged ${secondaryCandidates.size} priority sources (${localChapterCountOverride} Chapters total)",
					Toast.LENGTH_SHORT,
				).show()
			},
			onDismiss = { showMihonMergeSelector = false },
		)
	}

	// Mihon Full-Screen Source Priority Selector for Tab 2 (`⇄ Migrate (Replace Primary)`)
	if (showMihonMigrateSelector) {
		val currentTarget = perItemSelectedSource[primaryItem.manga.id] ?: defaultBestSourceFor(primaryItem)
		val cleanCurrentTarget = UiSourceAndTitleFormatter.formatSourceName(currentTarget)
		MigrationSourceSelectorScreen(
			baseChapterCount = primaryTotalChapters,
			initialSelectedSourceNames = listOf(cleanCurrentTarget, "ComicK", "MangaDex", "MangaFire", "Bato.to").distinct(),
			onContinueWithSelected = { selectedCandidates ->
				val topPriority = selectedCandidates.firstOrNull {
					!it.displayName.equals(cleanPrimarySource, ignoreCase = true)
				} ?: selectedCandidates.firstOrNull()
				if (topPriority != null) {
					perItemSelectedSource[primaryItem.manga.id] = topPriority.fullSourceCode
					perItemIsAutoSelected[primaryItem.manga.id] = false
					Toast.makeText(
						context,
						"Target priority set to ${topPriority.displayName} (${topPriority.verifiedChapters} Chapters)",
						Toast.LENGTH_SHORT,
					).show()
				}
				showMihonMigrateSelector = false
			},
			onDismiss = { showMihonMigrateSelector = false },
		)
	}

	perItemPickerTarget?.let { targetItem ->
		val currentTarget = perItemSelectedSource[targetItem.manga.id] ?: defaultBestSourceFor(targetItem)
		val cleanCurrentTarget = UiSourceAndTitleFormatter.formatSourceName(currentTarget)
		MigrationSourceSelectorScreen(
			baseChapterCount = targetItem.totalChapters,
			initialSelectedSourceNames = listOf(cleanCurrentTarget, "ComicK", "MangaDex", "MangaFire").distinct(),
			onContinueWithSelected = { selectedCandidates ->
				val topPriority = selectedCandidates.firstOrNull()
				if (topPriority != null) {
					perItemSelectedSource[targetItem.manga.id] = topPriority.fullSourceCode
					perItemIsAutoSelected[targetItem.manga.id] = false
				}
				perItemPickerTarget = null
			},
			onDismiss = { perItemPickerTarget = null },
		)
	}
}


