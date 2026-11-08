package org.koitharu.kotatsu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.BatchDeduplicationRule
import org.koitharu.kotatsu.core.model.BrokenMangaFixProposal
import org.koitharu.kotatsu.core.model.DuplicateCandidatePair
import org.koitharu.kotatsu.core.model.LibraryDisplayConfig
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.ReaderCustomizationConfig
import org.koitharu.kotatsu.core.model.RepairAuditLogEntry
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.components.MangaReaderPageCanvas
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiStudioScreen(
	duplicateCandidates: List<DuplicateCandidatePair>,
	brokenProposals: List<BrokenMangaFixProposal>,
	batchRule: BatchDeduplicationRule,
	displayConfig: LibraryDisplayConfig,
	readerConfig: ReaderCustomizationConfig,
	extensionCatalog: List<ExtensionCatalogManga>,
	onSetBatchRule: (BatchDeduplicationRule) -> Unit,
	onResolveDuplicateOneByOne: (DuplicateCandidatePair, Boolean) -> Unit,
	onIgnoreDuplicate: (DuplicateCandidatePair) -> Unit,
	onResolveAllDuplicatesEnMasse: () -> Unit,
	onRunSingleAiFix: (BrokenMangaFixProposal) -> Unit,
	onRunAllAiFixBatch: () -> Unit,
	onUpdateDisplayConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onTestCheckAndSwapAdd: (ExtensionCatalogManga) -> Unit,
	aiEnginePillLabel: String = "AI Engine: Gemini Nano",
	onOpenAiEngineSettings: () -> Unit = {},
	onOpenSettingsScreen: () -> Unit = {},
	isAiActive: Boolean = true,
	libraryItems: List<LibraryMangaItem> = emptyList(),
	onMigrateSingleSource: (Long, String, Int) -> Unit = { _, _, _ -> },
	onMigrateBatchSources: (List<Triple<Long, String, Int>>) -> Unit = {},
	repairAuditLog: List<RepairAuditLogEntry> = emptyList(),
	onRunManualHealthScan: () -> Unit = {},
	onOpenMangaDetails: (Long) -> Unit = {},
	onRevertRepair: (RepairAuditLogEntry) -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var selectedTab by remember { mutableIntStateOf(0) }
	var showBatchMigrateSheet by remember { mutableStateOf(false) }
	val tabs = listOf(
		"Duplicates (${duplicateCandidates.size})",
		"Repair (${brokenProposals.size})",
		"Upscaler",
	)

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// Clean Header + Standardized 2-Icon Cluster ([✦ AI] + [⚙ Settings]) + Material 3 Segmented Pill Bar
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "AI & Enhancement",
					style = MaterialTheme.typography.headlineSmall,
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
				)

				// Strict 2-Icon Header Rule on Enhance via Shared UnifiedTopRightIconCluster: [✦ AI Engine] + [⚙ Settings]
				org.koitharu.kotatsu.ui.components.UnifiedTopRightIconCluster(
					activeTab = org.koitharu.kotatsu.core.model.AppNavTab.AI_STUDIO,
					onOpenAiEngineSettings = onOpenAiEngineSettings,
					onOpenSettingsScreen = onOpenSettingsScreen,
					isAiActive = isAiActive,
					aiButtonTestTag = "enhance_ai_engine_pill_button",
					settingsTestTag = "enhance_settings_gear_button",
				)
			}

			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
				shape = RoundedCornerShape(20.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(40.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(3.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					tabs.forEachIndexed { idx, title ->
						val isSelected = selectedTab == idx
						val icon = when (idx) {
							0 -> Icons.Default.MergeType
							1 -> Icons.Default.AutoFixHigh
							else -> Icons.Default.AutoAwesome
						}
						Surface(
							onClick = { selectedTab = idx },
							color = if (isSelected) {
								MaterialTheme.colorScheme.primary
							} else {
								Color.Transparent
							},
							shape = RoundedCornerShape(17.dp),
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight()
								.testTag("ai_studio_tab_$idx"),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.Center,
							) {
								Icon(
									imageVector = icon,
									contentDescription = null,
									tint = if (isSelected) {
										MaterialTheme.colorScheme.onPrimary
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
									modifier = Modifier.size(15.dp),
								)
								Spacer(modifier = Modifier.width(5.dp))
								Text(
									text = title,
									style = MaterialTheme.typography.labelMedium,
									fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									color = if (isSelected) {
										MaterialTheme.colorScheme.onPrimary
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									},
								)
							}
						}
					}
				}
			}
		}

		when (selectedTab) {
			0 -> DuplicateResolutionTab(
				duplicateCandidates = duplicateCandidates,
				batchRule = batchRule,
				checkAndSwapEnabled = displayConfig.checkAndSwapOnAdd,
				onSetBatchRule = onSetBatchRule,
				onResolveOneByOne = onResolveDuplicateOneByOne,
				onIgnoreDuplicate = onIgnoreDuplicate,
				onResolveAllEnMasse = onResolveAllDuplicatesEnMasse,
				onToggleCheckAndSwap = { enabled ->
					onUpdateDisplayConfig { it.copy(checkAndSwapOnAdd = enabled) }
				},
			)

			1 -> SourceRepairAndFixItTab(
				proposals = brokenProposals,
				repairAuditLog = repairAuditLog,
				onRunSingleFix = onRunSingleAiFix,
				onRunAllBatchFix = onRunAllAiFixBatch,
				onRunManualHealthScan = onRunManualHealthScan,
				onOpenBatchMigrate = { showBatchMigrateSheet = true },
				onOpenMangaDetails = onOpenMangaDetails,
				onRevertRepair = onRevertRepair,
			)

			else -> AiPageUpscalerStudioTab(
				readerConfig = readerConfig,
				onUpdateReaderConfig = onUpdateReaderConfig,
			)
		}
	}

	if (showBatchMigrateSheet && libraryItems.isNotEmpty()) {
		org.koitharu.kotatsu.ui.components.UniversalSheet(
			onClose = { showBatchMigrateSheet = false },
			containerColor = Color(0xFF121620),
			sheetTestTag = "enhance_batch_migrate_universal_sheet",
		) {
			SourceMigrationModalSheet(
				itemsToMigrate = libraryItems,
				onMigrateSingle = { mangaId, targetSource, targetChapters ->
					onMigrateSingleSource(mangaId, targetSource, targetChapters)
				},
				onMigrateAllSelected = { migrations ->
					onMigrateBatchSources(migrations)
					showBatchMigrateSheet = false
				},
				onClose = { showBatchMigrateSheet = false },
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateResolutionTab(
	duplicateCandidates: List<DuplicateCandidatePair>,
	batchRule: BatchDeduplicationRule,
	checkAndSwapEnabled: Boolean,
	onSetBatchRule: (BatchDeduplicationRule) -> Unit,
	onResolveOneByOne: (DuplicateCandidatePair, Boolean) -> Unit,
	onIgnoreDuplicate: (DuplicateCandidatePair) -> Unit,
	onResolveAllEnMasse: () -> Unit,
	onToggleCheckAndSwap: (Boolean) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 120.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		// Compact 2x2 Priority Rule Header & En-Masse Deduplication Card
		item {
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
				),
				modifier = Modifier
					.fillMaxWidth()
					.border(
						width = 1.dp,
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
						shape = RoundedCornerShape(18.dp),
					),
			) {
				Column(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Check & Swap on add",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold,
							)
							Text(
								text = "Prompt to swap sources & merge read history on add",
								style = MaterialTheme.typography.bodySmall,
								fontSize = 11.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = checkAndSwapEnabled,
							onCheckedChange = onToggleCheckAndSwap,
							modifier = Modifier.testTag("ai_studio_check_swap_switch"),
						)
					}

					if (duplicateCandidates.isNotEmpty()) {
						HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

						// Compact 2x2 Grid of Priority Rule Chips
						Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
							BatchDeduplicationRule.entries.chunked(2).forEach { rowRules ->
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									rowRules.forEach { rule ->
										val isSelected = batchRule == rule
										FilterChip(
											selected = isSelected,
											onClick = { onSetBatchRule(rule) },
											label = {
												Text(
													text = rule.shortLabel,
													fontSize = 11.sp,
													fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
													maxLines = 1,
												)
											},
											modifier = Modifier
												.weight(1f)
												.height(32.dp)
												.testTag("batch_rule_${rule.name.lowercase()}"),
										)
									}
								}
							}
						}

						Button(
							onClick = onResolveAllEnMasse,
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(36.dp)
								.testTag("resolve_all_duplicates_en_masse_button"),
						) {
							Icon(
								imageVector = Icons.Default.DoneAll,
								contentDescription = null,
								modifier = Modifier.size(16.dp),
							)
							Spacer(modifier = Modifier.width(6.dp))
							Text("Merge All (${duplicateCandidates.size}) • ${batchRule.shortLabel}", fontSize = 12.sp)
						}
					}

					var showDuplicatesMihonSelector by remember { mutableStateOf(false) }
					var priorityOrderSummary by remember {
						mutableStateOf("ComicK → MangaDex → MangaFire → Bato.to")
					}
					OutlinedButton(
						onClick = { showDuplicatesMihonSelector = true },
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(36.dp)
							.testTag("duplicates_open_mihon_source_selector_button"),
					) {
						Text(
							text = "⚙ Configure & Select Sources (Mihon Selector) ▸",
							fontSize = 11.sp,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					Text(
						text = "Priority: $priorityOrderSummary",
						fontFamily = JetBrainsMonoFamily,
						fontSize = 10.sp,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)

					if (showDuplicatesMihonSelector) {
						MigrationSourceSelectorScreen(
							baseChapterCount = 30,
							initialSelectedSourceNames = listOf("ComicK", "MangaDex", "MangaFire", "Bato.to"),
							onContinueWithSelected = { selected ->
								priorityOrderSummary = selected.joinToString(" → ") { it.displayName }
								showDuplicatesMihonSelector = false
							},
							onDismiss = { showDuplicatesMihonSelector = false },
						)
					}
				}
			}
		}

		if (duplicateCandidates.isEmpty()) {
			item {
				Card(
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
					),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(18.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Icon(
							imageVector = Icons.Default.CheckCircle,
							contentDescription = null,
							tint = Color(0xFF10B981),
							modifier = Modifier.size(28.dp),
						)
						Column {
							Text(
								text = "No Duplicates Found",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Your library has no duplicate series across sources.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}
		} else {
			items(duplicateCandidates, key = { it.pairId }) { pair ->
				DuplicateOneByOneCard(
					pair = pair,
					onKeepPrimary = { onResolveOneByOne(pair, true) },
					onKeepSecondary = { onResolveOneByOne(pair, false) },
					onIgnore = { onIgnoreDuplicate(pair) },
				)
			}
		}
	}
}

@Composable
private fun DuplicateOneByOneCard(
	pair: DuplicateCandidatePair,
	onKeepPrimary: () -> Unit,
	onKeepSecondary: () -> Unit,
	onIgnore: () -> Unit,
) {
	val primarySource = UiSourceAndTitleFormatter.formatSourceName(pair.primary.manga.source)
	val secondarySource = UiSourceAndTitleFormatter.formatSourceName(pair.secondary.manga.source)
	val cleanTitle = UiSourceAndTitleFormatter.formatDisplayTitle(pair.primary.manga.title)

	Card(
		shape = RoundedCornerShape(18.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
		),
		modifier = Modifier
			.fillMaxWidth()
			.border(
				width = 1.dp,
				color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
				shape = RoundedCornerShape(18.dp),
			)
			.testTag("duplicate_pair_card_${pair.pairId}"),
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = cleanTitle,
						style = MaterialTheme.typography.titleSmall,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					Surface(
						color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
						shape = RoundedCornerShape(6.dp),
					) {
						Text(
							text = "${(pair.similarityScore * 100).toInt()}%",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.secondary,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				}
				TextButton(
					onClick = onIgnore,
					contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
					modifier = Modifier
						.height(28.dp)
						.testTag("ignore_duplicate_${pair.pairId}"),
				) {
					Text("Ignore", fontSize = 11.sp)
				}
			}

			// Clean Side-by-Side Source Cards
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				// Left Entry (Primary)
				Surface(
					color = MaterialTheme.colorScheme.surface,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.weight(1f),
				) {
					Column(
						modifier = Modifier.padding(8.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							MangaCoverArtwork(
								title = cleanTitle,
								coverUrl = pair.primary.manga.coverUrl,
								modifier = Modifier
									.width(38.dp)
									.aspectRatio(0.68f)
									.clip(RoundedCornerShape(6.dp)),
							)
							Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
								Text(
									text = primarySource,
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.primary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								Text(
									text = "${pair.primary.totalChapters} ch • Read ${pair.primary.lastReadChapterNumber.toInt()}",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						Button(
							onClick = onKeepPrimary,
							modifier = Modifier
								.fillMaxWidth()
								.height(32.dp)
								.testTag("keep_primary_${pair.pairId}"),
							contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
						) {
							Icon(
								imageVector = Icons.Default.MergeType,
								contentDescription = null,
								modifier = Modifier.size(14.dp),
							)
							Spacer(modifier = Modifier.width(4.dp))
							Text(
								text = "Primary: $primarySource",
								fontSize = 10.sp,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}

				// Right Entry (Secondary)
				Surface(
					color = MaterialTheme.colorScheme.surface,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.weight(1f),
				) {
					Column(
						modifier = Modifier.padding(8.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							MangaCoverArtwork(
								title = UiSourceAndTitleFormatter.formatDisplayTitle(pair.secondary.manga.title),
								coverUrl = pair.secondary.manga.coverUrl,
								modifier = Modifier
									.width(38.dp)
									.aspectRatio(0.68f)
									.clip(RoundedCornerShape(6.dp)),
							)
							Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
								Text(
									text = secondarySource,
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.secondary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								Text(
									text = "${pair.secondary.totalChapters} ch • Read ${pair.secondary.lastReadChapterNumber.toInt()}",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						FilledTonalButton(
							onClick = onKeepSecondary,
							modifier = Modifier
								.fillMaxWidth()
								.height(32.dp)
								.testTag("keep_secondary_${pair.pairId}"),
							contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
						) {
							Icon(
								imageVector = Icons.Default.MergeType,
								contentDescription = null,
								modifier = Modifier.size(14.dp),
							)
							Spacer(modifier = Modifier.width(4.dp))
							Text(
								text = "Primary: $secondarySource",
								fontSize = 10.sp,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SourceRepairAndFixItTab(
	proposals: List<BrokenMangaFixProposal>,
	repairAuditLog: List<RepairAuditLogEntry>,
	onRunSingleFix: (BrokenMangaFixProposal) -> Unit,
	onRunAllBatchFix: () -> Unit,
	onRunManualHealthScan: () -> Unit,
	onOpenBatchMigrate: () -> Unit = {},
	onOpenMangaDetails: (Long) -> Unit,
	onRevertRepair: (RepairAuditLogEntry) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxSize()
			.testTag("repair_tab_lazy_column"),
	) {
		item {
			Surface(
				color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp, vertical = 10.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Mihon-Style Source Migration",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Validate & migrate series individually or en-masse across sources",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					FilledTonalButton(
						onClick = onOpenBatchMigrate,
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier.testTag("repair_open_batch_migrate_button"),
					) {
						Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(4.dp))
						Text("Migrate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
					}
				}
			}
		}
		if (proposals.isNotEmpty()) {
			item {
				Card(
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
					),
					modifier = Modifier
						.fillMaxWidth()
						.border(
							width = 1.dp,
							color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
							shape = RoundedCornerShape(20.dp),
						),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 12.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = "${proposals.size} unreachable sources",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
								)
								Text(
									text = "Ready to migrate to healthy extensions",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Button(
								onClick = onRunAllBatchFix,
								modifier = Modifier.testTag("ai_fix_all_batch_button"),
							) {
								Icon(
									imageVector = Icons.Default.AutoFixHigh,
									contentDescription = null,
									modifier = Modifier.size(16.dp),
								)
								Spacer(modifier = Modifier.width(6.dp))
								Text("Repair All")
							}
						}

						OutlinedButton(
							onClick = onRunManualHealthScan,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("run_manual_health_scan_button"),
						) {
							Text("Run Source & Extension Health Scan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
						}
					}
				}
			}
		}

		if (proposals.isEmpty()) {
			item {
				Card(
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
					),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(18.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp),
						) {
							Icon(
								imageVector = Icons.Default.CheckCircle,
								contentDescription = null,
								tint = Color(0xFF10B981),
								modifier = Modifier.size(28.dp),
							)
							Column {
								Text(
									text = "All Sources Healthy",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
								)
								Text(
									text = "Every series in your library is connected to an active source.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}

						OutlinedButton(
							onClick = onRunManualHealthScan,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("run_manual_health_scan_button"),
						) {
							Text(
								text = "Run Source & Extension Health Scan",
								fontSize = 12.sp,
								fontWeight = FontWeight.SemiBold,
							)
						}
					}
				}
			}
		} else {
			items(proposals, key = { it.brokenItem.manga.id }) { proposal ->
				val cleanTarget = UiSourceAndTitleFormatter.formatSourceName(proposal.candidateSource)
				val cleanBrokenTitle = UiSourceAndTitleFormatter.formatDisplayTitle(proposal.brokenItem.manga.title)
				Card(
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
					),
					modifier = Modifier
						.fillMaxWidth()
						.border(
							width = 1.dp,
							color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
							shape = RoundedCornerShape(20.dp),
						)
						.testTag("broken_fix_card_${proposal.brokenItem.manga.id}"),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(14.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						MangaCoverArtwork(
							title = cleanBrokenTitle,
							coverUrl = proposal.brokenItem.manga.coverUrl,
							isBroken = true,
							modifier = Modifier
								.width(48.dp)
								.aspectRatio(0.68f)
								.clip(RoundedCornerShape(8.dp)),
						)
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(2.dp),
						) {
							Text(
								text = cleanBrokenTitle,
								style = MaterialTheme.typography.titleSmall,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Text(
								text = proposal.failureReason,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.error,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Text(
								text = "→ $cleanTarget (${proposal.candidateChaptersCount} ch)",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 11.sp,
								color = Color(0xFF10B981),
							)
						}
						FilledTonalButton(
							onClick = { onRunSingleFix(proposal) },
							contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
							modifier = Modifier
								.height(36.dp)
								.testTag("apply_single_ai_fix_${proposal.brokenItem.manga.id}"),
						) {
							Text("Migrate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
						}
					}
				}
			}
		}

		// RECENT REPAIRS & SOURCE MIGRATIONS Audit Log Section
		if (repairAuditLog.isNotEmpty()) {
			item(key = "recent_repairs_audit_header") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 6.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Text(
						text = "RECENT REPAIRS & SOURCE MIGRATIONS",
						fontFamily = JetBrainsMonoFamily,
						fontWeight = FontWeight.Bold,
						fontSize = 11.sp,
						color = Color(0xFF5CE1E6),
						modifier = Modifier.testTag("recent_repairs_audit_header"),
					)
					Text(
						text = "Session audit trail of automatic & manual source migrations with chapter preservation",
						style = MaterialTheme.typography.bodySmall,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			items(
				items = repairAuditLog,
				key = { it.id },
			) { audit ->
				Card(
					shape = RoundedCornerShape(18.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
					),
					modifier = Modifier
						.fillMaxWidth()
						.border(
							width = 1.dp,
							color = Color(0xFF10B981).copy(alpha = 0.28f),
							shape = RoundedCornerShape(18.dp),
						)
						.testTag("repair_audit_card_${audit.mangaId}"),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							MangaCoverArtwork(
								title = audit.mangaTitle,
								coverUrl = audit.coverUrl,
								modifier = Modifier
									.width(44.dp)
									.aspectRatio(0.68f)
									.clip(RoundedCornerShape(8.dp)),
							)
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(3.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = audit.mangaTitle,
										style = MaterialTheme.typography.titleSmall,
										fontFamily = PlusJakartaDisplayFamily,
										fontWeight = FontWeight.Bold,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f),
									)
									Surface(
										color = Color(0xFF10B981).copy(alpha = 0.16f),
										shape = RoundedCornerShape(50),
									) {
										Text(
											text = "✓ Repaired • ${audit.timestampLabel}",
											fontFamily = JetBrainsMonoFamily,
											fontSize = 9.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFF34D399),
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
										)
									}
								}
								Text(
									text = "${audit.previousSource} → ${audit.newSource} • ${audit.chaptersPreserved} Chapters Preserved",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 11.sp,
									color = Color(0xFF5CE1E6),
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							FilledTonalButton(
								onClick = { onOpenMangaDetails(audit.mangaId) },
								contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
								modifier = Modifier
									.weight(1f)
									.height(34.dp)
									.testTag("repair_audit_view_series_${audit.mangaId}"),
							) {
								Text("View Series", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
							}

							if (audit.canRevert) {
								OutlinedButton(
									onClick = { onRevertRepair(audit) },
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
									modifier = Modifier
										.height(34.dp)
										.testTag("repair_audit_revert_${audit.mangaId}"),
								) {
									Text("Revert", fontSize = 12.sp)
								}
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiPageUpscalerStudioTab(
	readerConfig: ReaderCustomizationConfig,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
) {
	var splitFraction by remember { mutableFloatStateOf(0.5f) }
	var canvasWidthPx by remember { mutableFloatStateOf(1f) }

	val activeRightBadgeLabel = when (readerConfig.aiUpscaleMode) {
		AiUpscaleMode.OFF -> "AI Crisp Ink 1.5x"
		AiUpscaleMode.FAST_CRISP_INK_1_5X -> "AI Crisp Ink 1.5x"
		AiUpscaleMode.ANIME_SUPER_RES_2X -> "AI Super-Res 2.0x"
		AiUpscaleMode.HIGH_CONTRAST_RESTORE -> "De-Noise & Ink 1.75x"
	}

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 116.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				shape = RoundedCornerShape(20.dp),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
				),
				modifier = Modifier
					.fillMaxWidth()
					.border(
						width = 1.dp,
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
						shape = RoundedCornerShape(20.dp),
					),
			) {
				Column(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					// 1. Preset Chips (2 clean horizontal rows with padding(top = 2.dp) so top borders are never clipped)
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 2.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						// Row 1: Standard Native Decode + AI Crisp Ink (1.5x)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							val row1Modes = listOf(
								AiUpscaleMode.OFF to "Standard Native (1.0x)",
								AiUpscaleMode.FAST_CRISP_INK_1_5X to "AI Crisp Ink (1.5x)",
							)
							row1Modes.forEach { (mode, shortLabel) ->
								FilterChip(
									selected = readerConfig.aiUpscaleMode == mode,
									onClick = { onUpdateReaderConfig { it.copy(aiUpscaleMode = mode) } },
									label = {
										Text(
											text = shortLabel,
											fontSize = 11.sp,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									},
									modifier = Modifier
										.weight(1f)
										.testTag("studio_upscale_chip_${mode.name.lowercase()}"),
								)
							}
						}

						// Row 2: Super-Res (2.0x) and De-Noise & Ink Restore side by side on a single horizontal row
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							val row2Modes = listOf(
								AiUpscaleMode.ANIME_SUPER_RES_2X to "Super-Res (2.0x)",
								AiUpscaleMode.HIGH_CONTRAST_RESTORE to "De-Noise & Ink Restore",
							)
							row2Modes.forEach { (mode, shortLabel) ->
								FilterChip(
									selected = readerConfig.aiUpscaleMode == mode,
									onClick = { onUpdateReaderConfig { it.copy(aiUpscaleMode = mode) } },
									label = {
										Text(
											text = shortLabel,
											fontSize = 11.sp,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									},
									modifier = Modifier
										.weight(1f)
										.testTag("studio_upscale_chip_${mode.name.lowercase()}"),
								)
							}
						}
					}

					// 2. Quick Color Filter Chips
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						FilterChip(
							selected = readerConfig.warmPaperTint,
							onClick = { onUpdateReaderConfig { it.copy(warmPaperTint = !it.warmPaperTint) } },
							label = { Text("Warm Paper", fontSize = 11.sp, maxLines = 1) },
							modifier = Modifier.weight(1f),
						)
						FilterChip(
							selected = readerConfig.grayscale,
							onClick = { onUpdateReaderConfig { it.copy(grayscale = !it.grayscale) } },
							label = { Text("Grayscale", fontSize = 11.sp, maxLines = 1) },
							modifier = Modifier.weight(1f),
						)
						FilterChip(
							selected = readerConfig.invertColors,
							onClick = { onUpdateReaderConfig { it.copy(invertColors = !it.invertColors) } },
							label = { Text("Invert", fontSize = 11.sp, maxLines = 1) },
							modifier = Modifier.weight(1f),
						)
					}

					// 3. Unified Interactive Split-Comparison Preview Canvas
					// Driven strictly from the draggable vertical divider line (splitFraction state)
					// with Top-Left ("Original 1.0x") and Top-Right ("AI Crisp Ink 1.5x") corner badges.
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(0.75f)
							.clip(RoundedCornerShape(14.dp))
							.onSizeChanged { size ->
								canvasWidthPx = size.width.toFloat().coerceAtLeast(1f)
							}
							.pointerInput(Unit) {
								detectTapGestures { tapOffset ->
									splitFraction = (tapOffset.x / canvasWidthPx).coerceIn(0.15f, 0.85f)
								}
							}
							.pointerInput(Unit) {
								detectHorizontalDragGestures { change, _ ->
									change.consume()
									splitFraction = (change.position.x / canvasWidthPx).coerceIn(0.15f, 0.85f)
								}
							}
							.testTag("studio_upscale_split_slider"),
					) {
						MangaReaderPageCanvas(
							mangaTitle = "Omniscient Reader's Viewpoint",
							chapterNumber = 42f,
							pageNumber = 7,
							totalPages = 18,
							upscaleMode = if (readerConfig.aiUpscaleMode == AiUpscaleMode.OFF) {
								AiUpscaleMode.FAST_CRISP_INK_1_5X
							} else {
								readerConfig.aiUpscaleMode
							},
							showBeforeAfterSplit = true,
							splitRatio = splitFraction,
							warmPaperTint = readerConfig.warmPaperTint,
							invertColors = readerConfig.invertColors,
							grayscale = readerConfig.grayscale,
							modifier = Modifier.fillMaxSize(),
						)

						// Top-Left Corner Badge: "Original 1.0x"
						Surface(
							color = Color(0xCC0F1419),
							shape = RoundedCornerShape(8.dp),
							modifier = Modifier
								.align(Alignment.TopStart)
								.padding(10.dp)
								.border(
									width = 1.dp,
									color = Color.White.copy(alpha = 0.18f),
									shape = RoundedCornerShape(8.dp),
								)
								.testTag("upscaler_badge_original"),
						) {
							Text(
								text = "Original 1.0x",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.SemiBold,
								fontSize = 10.sp,
								color = Color(0xFFD5DAE5),
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							)
						}

						// Top-Right Corner Badge: "AI Crisp Ink 1.5x"
						Surface(
							color = Color(0xCC0F1419),
							shape = RoundedCornerShape(8.dp),
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(10.dp)
								.border(
									width = 1.dp,
									color = Color(0xFF5CE1E6).copy(alpha = 0.55f),
									shape = RoundedCornerShape(8.dp),
								)
								.testTag("upscaler_badge_enhanced"),
						) {
							Text(
								text = activeRightBadgeLabel,
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 10.sp,
								color = Color(0xFF5CE1E6),
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}
	}
}
