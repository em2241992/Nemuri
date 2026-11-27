package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.RecapDepth
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.domain.UpscaleEffect
import org.nemuri.app.BuildConfig
import org.nemuri.app.ui.components.AiUpscalePreviewSheet
import org.nemuri.app.ui.components.HardwareTelemetryStrip
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.settings.SettingsGroupCard
import org.nemuri.app.ui.settings.SettingsRowDivider
import org.nemuri.app.ui.settings.SettingsToggleRow
import org.nemuri.app.ui.theme.NemuriTheme
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class EnhanceHubTab(val label: String) {
	OVERVIEW("Overview"),
	DUPLICATES("Duplicates"),
	REPAIR("Repair"),
	UPSCALER("Upscaler"),
	AI("AI"),
}

@Composable
fun EnhanceHubScreen(
	onOpenBrokenSources: () -> Unit,
	onOpenDuplicates: () -> Unit,
	onOpenStats: () -> Unit,
	onOpenReadingSettings: () -> Unit,
	onOpenAiSettings: () -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: EnhanceHubViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val bottomPadding = rememberRootTabBottomPadding()
	var selectedTab by rememberSaveable { mutableStateOf(EnhanceHubTab.OVERVIEW) }
	var showAiPreviewSheet by rememberSaveable { mutableStateOf(false) }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = "",
			onQueryChange = {},
			placeholder = "Enhance Hub • Care, Repair, Shaders & AI",
			onTuneClick = {
				if (selectedTab == EnhanceHubTab.AI) {
					onOpenAiSettings()
				} else {
					onOpenReadingSettings()
				}
			},
			onSettingsClick = onOpenSettings,
		)

		// Equal-width 5-tab top bar: Overview | Duplicates | Repair | Upscaler | AI
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			val tabShape = NemuriTheme.pillShape()
			EnhanceHubTab.entries.forEach { tab ->
				val selected = selectedTab == tab
				Surface(
					color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
					shape = tabShape,
					border = BorderStroke(
						width = 1.dp,
						color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
					),
					modifier = Modifier
						.weight(1f)
						.clip(tabShape)
						.clickable { selectedTab = tab },
				) {
					Text(
						text = tab.label,
						style = MaterialTheme.typography.labelMedium,
						fontSize = 11.5.sp,
						fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
						color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						textAlign = TextAlign.Center,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 4.dp, vertical = 8.dp),
					)
				}
			}
		}

		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			when (selectedTab) {
				EnhanceHubTab.OVERVIEW -> overviewTabContent(
					uiState = uiState,
					viewModel = viewModel,
					onOpenDuplicates = onOpenDuplicates,
					onOpenBrokenSources = onOpenBrokenSources,
					onOpenReadingSettings = onOpenReadingSettings,
					onOpenAiPreview = { showAiPreviewSheet = true },
					onOpenAiSettings = onOpenAiSettings,
					onOpenStats = onOpenStats,
				)

				EnhanceHubTab.DUPLICATES -> duplicatesTabContent(
					uiState = uiState,
					viewModel = viewModel,
					onOpenDuplicates = onOpenDuplicates,
				)

				EnhanceHubTab.REPAIR -> repairTabContent(
					uiState = uiState,
					viewModel = viewModel,
					onOpenBrokenSources = onOpenBrokenSources,
					onOpenStats = onOpenStats,
				)

				EnhanceHubTab.UPSCALER -> upscalerTabContent(
					uiState = uiState,
					viewModel = viewModel,
					onOpenReadingSettings = onOpenReadingSettings,
					onOpenAiPreview = { showAiPreviewSheet = true },
				)

				EnhanceHubTab.AI -> aiTabContent(
					uiState = uiState,
					viewModel = viewModel,
					onOpenAiSettings = onOpenAiSettings,
				)
			}
		}
	}

	if (showAiPreviewSheet) {
		AiUpscalePreviewSheet(
			shaderConfig = ReaderShaderConfig(
				preset = uiState.readerShaderPreset,
				descreen = uiState.isReaderDescreenEnabled,
				inkLevels = uiState.isReaderInkLevelsEnabled,
			),
			isNeuralUpscaleEnabled = uiState.isNeuralUpscaleEnabled,
			isNeuralUpscaleSupported = uiState.isNeuralUpscaleSupported,
			neuralAccelerator = uiState.neuralAccelerator,
			activeDelegateLabel = uiState.activeNeuralDelegateLabel,
			hasReadyNeuralModel = uiState.hasReadyNeuralModel,
			isWarmBedtimeActive = uiState.isWarmBedtimePresetActive,
			previewModel = uiState.sampleCoverUrl,
			onSelectShaderPreset = viewModel::setReaderShaderPreset,
			onSetInkLevels = viewModel::setReaderInkLevelsEnabled,
			onSetDescreen = viewModel::setReaderDescreenEnabled,
			onSetNeuralUpscaleEnabled = viewModel::setNeuralUpscaleEnabled,
			onSelectNeuralAccelerator = viewModel::setNeuralAccelerator,
			onApplyBedtimeWarmPreset = viewModel::applyBedtimeWarmPreset,
			onOpenUpscalingSettings = {
				showAiPreviewSheet = false
				onOpenReadingSettings()
			},
			onDismiss = { showAiPreviewSheet = false },
		)
	}
}

private fun LazyListScope.overviewTabContent(
	uiState: EnhanceHubUiState,
	viewModel: EnhanceHubViewModel,
	onOpenDuplicates: () -> Unit,
	onOpenBrokenSources: () -> Unit,
	onOpenReadingSettings: () -> Unit,
	onOpenAiPreview: () -> Unit,
	onOpenAiSettings: () -> Unit,
	onOpenStats: () -> Unit,
) {
	// Top header + quick Reading Stats KPI strip
	item {
		HubHealthHeaderRow(
			title = "CONVENIENCE & LIBRARY CARE",
			subtitle = "${uiState.libraryCount} saved titles monitored • ${formatHubDuration(uiState.totalReadingTimeMs)} read",
			isScanning = uiState.isScanningHealth,
			onRefresh = viewModel::refreshDiagnostics,
		)
	}

	// 1. Convenience & Library Care (high-use convenience tools right at the top!)
	item {
		DuplicateGuardPreferencesCard(
			uiState = uiState,
			onToggleCheckOnAdd = viewModel::setDuplicateCheckOnAdd,
			onToggleMigrateProgress = viewModel::setDuplicateProgressMigrated,
			onSelectWinnerStrategy = viewModel::setDuplicateWinnerStrategy,
			onResetIgnoredPairs = viewModel::resetIgnoredDuplicatePairs,
		)
	}

	item {
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			DuplicateGuardStatusCard(
				exactCount = uiState.duplicatePairsCount,
				variantCount = uiState.editionVariantPairsCount,
				reviewCount = uiState.needsReviewPairsCount,
				onOpenDuplicates = onOpenDuplicates,
			)
			BrokenSourceRepairStatusCard(
				brokenSourcesCount = uiState.brokenSourcesCount,
				brokenTitlesCount = uiState.brokenTitlesCount,
				onOpenBrokenSources = onOpenBrokenSources,
			)
		}
	}

	// 2. Visual Reader Pipeline
	item {
		SectionOverlineLabel("VISUAL READER PIPELINE")
	}

	item {
		VisualReaderPipelineCard(
			uiState = uiState,
			onToggleSeamlessStitch = viewModel::setReaderSeamlessStitchEnabled,
			onSelectShaderPreset = viewModel::setReaderShaderPreset,
			onToggleNeuralUpscale = viewModel::setNeuralUpscaleEnabled,
			onClearCache = viewModel::clearUpscaleCache,
			onOpenReadingSettings = onOpenReadingSettings,
			onOpenAiPreview = onOpenAiPreview,
			showAdvancedToggles = false,
		)
	}

	// 3. AI Brain & "Where Was I?" Recaps
	item {
		SectionOverlineLabel("AI BRAIN & \"WHERE WAS I?\" RECAPS")
	}

	item {
		AiBrainOverviewCard(
			uiState = uiState,
			onTestAiBackend = viewModel::testAiBackend,
			onSelectRecapDepth = viewModel::setAiRecapDepth,
			onOpenAiSettings = onOpenAiSettings,
		)
	}

	// Reading Activity & On-Device Safety footer
	item {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			TelemetryKpiCard(
				label = "Reading Time",
				value = formatHubDuration(uiState.totalReadingTimeMs),
				accent = NemuriTheme.colors.cyan,
				onClick = onOpenStats,
				modifier = Modifier.weight(1f),
			)
			TelemetryKpiCard(
				label = "Current Streak",
				value = if (uiState.currentStreakDays > 0) "${uiState.currentStreakDays} day(s)" else "0 days",
				accent = NemuriTheme.colors.mergePurple,
				onClick = onOpenStats,
				modifier = Modifier.weight(1f),
			)
		}
	}

	item {
		OnDeviceSafetyBanner()
	}
}

private fun LazyListScope.duplicatesTabContent(
	uiState: EnhanceHubUiState,
	viewModel: EnhanceHubViewModel,
	onOpenDuplicates: () -> Unit,
) {
	item {
		HubHealthHeaderRow(
			title = "EDITION-AWARE DUPLICATE GUARD",
			subtitle = "O(N) signature scanner separating Exact matches from Colored & Variant editions",
			isScanning = uiState.isScanningHealth,
			onRefresh = viewModel::refreshDiagnostics,
		)
	}

	item {
		DuplicateGuardStatusCard(
			exactCount = uiState.duplicatePairsCount,
			variantCount = uiState.editionVariantPairsCount,
			reviewCount = uiState.needsReviewPairsCount,
			onOpenDuplicates = onOpenDuplicates,
		)
	}

	item {
		Button(
			onClick = onOpenDuplicates,
			colors = ButtonDefaults.buttonColors(
				containerColor = NemuriTheme.colors.cyan,
				contentColor = NemuriTheme.colors.onCyan,
			),
			shape = NemuriTheme.shape(14.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(50.dp),
		) {
			Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(
				text = "Open Library Duplicate Scanner",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
			)
		}
	}

	item {
		DuplicateGuardPreferencesCard(
			uiState = uiState,
			onToggleCheckOnAdd = viewModel::setDuplicateCheckOnAdd,
			onToggleMigrateProgress = viewModel::setDuplicateProgressMigrated,
			onSelectWinnerStrategy = viewModel::setDuplicateWinnerStrategy,
			onResetIgnoredPairs = viewModel::resetIgnoredDuplicatePairs,
		)
	}
}

private fun LazyListScope.repairTabContent(
	uiState: EnhanceHubUiState,
	viewModel: EnhanceHubViewModel,
	onOpenBrokenSources: () -> Unit,
	onOpenStats: () -> Unit,
) {
	item {
		HubHealthHeaderRow(
			title = "BROKEN SOURCES & SMART AUTO-REPAIR",
			subtitle = "Detect missing or dead extensions and batch-migrate titles with full progress transfer",
			isScanning = uiState.isScanningHealth,
			onRefresh = viewModel::refreshDiagnostics,
		)
	}

	item {
		BrokenSourceRepairStatusCard(
			brokenSourcesCount = uiState.brokenSourcesCount,
			brokenTitlesCount = uiState.brokenTitlesCount,
			onOpenBrokenSources = onOpenBrokenSources,
		)
	}

	item {
		Button(
			onClick = onOpenBrokenSources,
			colors = ButtonDefaults.buttonColors(
				containerColor = NemuriTheme.colors.cyan,
				contentColor = NemuriTheme.colors.onCyan,
			),
			shape = NemuriTheme.shape(14.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(50.dp),
		) {
			Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(
				text = "Launch Broken Sources & Smart Repair",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
			)
		}
	}

	item {
		SettingsGroupCard("Source Diagnostics") {
			if (uiState.brokenSourceEntries.isEmpty()) {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Icon(
						imageVector = Icons.Default.CheckCircle,
						contentDescription = null,
						tint = NemuriTheme.colors.healthy,
						modifier = Modifier.size(22.dp),
					)
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "All Library Sources Healthy",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Every source referenced by your ${uiState.libraryCount} saved title(s) is installed and enabled.",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
			} else {
				uiState.brokenSourceEntries.forEachIndexed { index, (sourceName, count) ->
					if (index > 0) SettingsRowDivider()
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable(onClick = onOpenBrokenSources)
							.padding(horizontal = 16.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = sourceName,
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "Extension unavailable or disabled",
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Surface(
							color = NemuriTheme.colors.amber.copy(alpha = 0.16f),
							shape = NemuriTheme.pillShape(),
						) {
							Text(
								text = "$count title(s)",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.amber,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
							)
						}
					}
				}
			}
		}
	}

	item {
		EnhanceToolCard(
			icon = Icons.Default.BarChart,
			title = "Reading Statistics & Activity",
			subtitle = "${formatHubDuration(uiState.totalReadingTimeMs)} total reading time • ${uiState.currentStreakDays} day streak",
			onClick = onOpenStats,
		)
	}
}

private fun LazyListScope.upscalerTabContent(
	uiState: EnhanceHubUiState,
	viewModel: EnhanceHubViewModel,
	onOpenReadingSettings: () -> Unit,
	onOpenAiPreview: () -> Unit,
) {
	item {
		SectionOverlineLabel("GPU SHADERS & NEURAL 2X SUPER-RESOLUTION")
	}

	item {
		VisualReaderPipelineCard(
			uiState = uiState,
			onToggleSeamlessStitch = viewModel::setReaderSeamlessStitchEnabled,
			onSelectShaderPreset = viewModel::setReaderShaderPreset,
			onToggleNeuralUpscale = viewModel::setNeuralUpscaleEnabled,
			onClearCache = viewModel::clearUpscaleCache,
			onOpenReadingSettings = onOpenReadingSettings,
			onOpenAiPreview = onOpenAiPreview,
			showAdvancedToggles = true,
			onToggleDescreen = viewModel::setReaderDescreenEnabled,
			onToggleInkLevels = viewModel::setReaderInkLevelsEnabled,
			onToggleNeuralPreload = viewModel::setNeuralPreloadEnabled,
		)
	}

	item {
		Button(
			onClick = onOpenReadingSettings,
			colors = ButtonDefaults.buttonColors(
				containerColor = NemuriTheme.colors.cyan,
				contentColor = NemuriTheme.colors.onCyan,
			),
			shape = NemuriTheme.shape(14.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(50.dp),
		) {
			Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(
				text = "Open Full Reader & ONNX Model Studio",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
			)
		}
	}

	item {
		OnDeviceSafetyBanner()
	}
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.aiTabContent(
	uiState: EnhanceHubUiState,
	viewModel: EnhanceHubViewModel,
	onOpenAiSettings: () -> Unit,
) {
	item {
		SectionOverlineLabel("AI ENGINE, RECAPS & SYNOPSIS TRANSLATION")
	}

	item {
		SettingsGroupCard("Active AI Routing") {
			Column(
				modifier = Modifier.padding(14.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Active Backend: ${uiState.activeAiEngineLabel}",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Mode: ${uiState.aiModeLabel} • Gemini Nano • LiteRT Local • Ollama LAN • Gemini API",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					if (uiState.isLocalModelLoadedInRam) {
						val unloadShape = NemuriTheme.pillShape()
						Surface(
							color = NemuriTheme.colors.healthy.copy(alpha = 0.16f),
							shape = unloadShape,
							modifier = Modifier
								.clip(unloadShape)
								.clickable(onClick = viewModel::unloadLocalModelFromRam),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(Icons.Default.Memory, contentDescription = null, tint = NemuriTheme.colors.healthy, modifier = Modifier.size(13.dp))
								Text("Unload RAM", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.healthy)
							}
						}
					}
				}
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					val chipShape = NemuriTheme.pillShape()
					AiMode.entries.forEach { mode ->
						val selected = uiState.aiMode == mode
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = chipShape,
							border = BorderStroke(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.clip(chipShape)
								.clickable { viewModel.setAiMode(mode) },
						) {
							Text(
								text = mode.label,
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
		AiBrainOverviewCard(
			uiState = uiState,
			onTestAiBackend = viewModel::testAiBackend,
			onSelectRecapDepth = viewModel::setAiRecapDepth,
			onOpenAiSettings = onOpenAiSettings,
			showExtendedToggles = true,
			onToggleShowRecapCard = viewModel::setAiShowRecapCard,
			onToggleIncludeNovelExcerpt = viewModel::setAiIncludeNovelExcerpt,
		)
	}

	item {
		Button(
			onClick = onOpenAiSettings,
			colors = ButtonDefaults.buttonColors(
				containerColor = NemuriTheme.colors.cyan,
				contentColor = NemuriTheme.colors.onCyan,
			),
			shape = NemuriTheme.shape(14.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(50.dp),
		) {
			Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(
				text = "Open AI Engine Studio",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
			)
		}
	}

	item {
		OnDeviceSafetyBanner()
	}
}

@Composable
private fun HubHealthHeaderRow(
	title: String,
	subtitle: String,
	isScanning: Boolean,
	onRefresh: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.cyan,
				)
				if (BuildConfig.DEBUG || BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)) {
					Surface(
						color = NemuriTheme.colors.amber.copy(alpha = 0.16f),
						shape = NemuriTheme.pillShape(),
						border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.6f)),
					) {
						Text(
							text = "BETA",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.amber,
							modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
						)
					}
				}
			}
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = NemuriTheme.colors.textMuted,
			)
		}
		IconButton(
			onClick = onRefresh,
			enabled = !isScanning,
			modifier = Modifier.size(34.dp),
		) {
			if (isScanning) {
				CircularProgressIndicator(
					color = NemuriTheme.colors.cyan,
					strokeWidth = 2.dp,
					modifier = Modifier.size(16.dp),
				)
			} else {
				Icon(
					imageVector = Icons.Default.Refresh,
					contentDescription = "Rescan health",
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(18.dp),
				)
			}
		}
	}
}

@Composable
private fun SectionOverlineLabel(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		fontWeight = FontWeight.Bold,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier.padding(top = 2.dp),
	)
}

@Composable
private fun DuplicateGuardPreferencesCard(
	uiState: EnhanceHubUiState,
	onToggleCheckOnAdd: (Boolean) -> Unit,
	onToggleMigrateProgress: (Boolean) -> Unit,
	onSelectWinnerStrategy: (DuplicateWinnerStrategy) -> Unit,
	onResetIgnoredPairs: () -> Unit,
) {
	SettingsGroupCard {
		SettingsToggleRow(
			title = "Check & Swap on Add",
			subtitle = "Warn and offer 1-tap Replace before adding an existing library title",
			checked = uiState.isDuplicateCheckOnAdd,
			onCheckedChange = onToggleCheckOnAdd,
		)
		SettingsRowDivider()
		SettingsToggleRow(
			title = "Carry Progress on Duplicate Replace",
			subtitle = "Transfer history, bookmarks & tracker links when replacing a duplicate",
			checked = uiState.isDuplicateProgressMigrated,
			onCheckedChange = onToggleMigrateProgress,
		)
		SettingsRowDivider()
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "Primary Winner Strategy",
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
					)
					Text(
						text = uiState.duplicateWinnerStrategy.label,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
				if (uiState.ignoredDuplicatePairsCount > 0) {
					TextButton(onClick = onResetIgnoredPairs) {
						Text(
							text = "Reset Ignored (${uiState.ignoredDuplicatePairsCount})",
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
				val chipShape = NemuriTheme.pillShape()
				DuplicateWinnerStrategy.entries.forEach { strategy ->
					val selected = uiState.duplicateWinnerStrategy == strategy
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = chipShape,
						border = BorderStroke(
							1.dp,
							if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
						),
						modifier = Modifier
							.clip(chipShape)
							.clickable { onSelectWinnerStrategy(strategy) },
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
		}
	}
}

@Composable
private fun DuplicateGuardStatusCard(
	exactCount: Int,
	variantCount: Int,
	reviewCount: Int,
	onOpenDuplicates: () -> Unit,
) {
	val cardShape = NemuriTheme.shape(16.dp)
	Surface(
		color = NemuriTheme.colors.card,
		shape = cardShape,
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = Modifier
			.fillMaxWidth()
			.clip(cardShape)
			.clickable(onClick = onOpenDuplicates),
	) {
		Column(
			modifier = Modifier.padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.weight(1f),
				) {
					Surface(
						color = NemuriTheme.colors.tealContainer,
						shape = NemuriTheme.shape(12.dp),
						modifier = Modifier.size(40.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.ContentCopy,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(20.dp),
							)
						}
					}
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Duplicate Guard Scanner",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = buildString {
								append("Exact matches & Colored / Edition Variants")
								if (reviewCount > 0) append(" • $reviewCount review")
							},
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
				TextButton(onClick = onOpenDuplicates) {
					Text("Scan", color = NemuriTheme.colors.cyan, fontWeight = FontWeight.SemiBold)
					Icon(
						imageVector = Icons.Default.ChevronRight,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(16.dp),
					)
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				InlineStatPill(
					label = "Exact Duplicates",
					value = if (exactCount == 0) "0 (Clean)" else "$exactCount pair(s)",
					accent = if (exactCount == 0) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
					modifier = Modifier.weight(1f),
				)
				InlineStatPill(
					label = "Colored / Edition Variants",
					value = if (variantCount == 0) "0" else "$variantCount variant(s)",
					accent = if (variantCount == 0) NemuriTheme.colors.textSecondary else NemuriTheme.colors.mergePurple,
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun BrokenSourceRepairStatusCard(
	brokenSourcesCount: Int,
	brokenTitlesCount: Int,
	onOpenBrokenSources: () -> Unit,
) {
	val healthy = brokenSourcesCount == 0
	EnhanceToolCard(
		icon = Icons.Default.Build,
		title = "Broken Source Health & Smart Auto-Repair",
		subtitle = if (healthy) {
			"All library extensions are active • Tap to inspect or batch-migrate sources"
		} else {
			"$brokenSourcesCount unavailable source(s) affecting $brokenTitlesCount library title(s) • 1-tap Smart Replace ready"
		},
		badge = if (healthy) "Healthy" else "$brokenTitlesCount affected",
		badgeTint = if (healthy) NemuriTheme.colors.healthy else NemuriTheme.colors.amber,
		onClick = onOpenBrokenSources,
	)
}

@Composable
private fun VisualReaderPipelineCard(
	uiState: EnhanceHubUiState,
	onToggleSeamlessStitch: (Boolean) -> Unit,
	onSelectShaderPreset: (ReaderShaderPreset) -> Unit,
	onToggleNeuralUpscale: (Boolean) -> Unit,
	onClearCache: () -> Unit,
	onOpenReadingSettings: () -> Unit,
	onOpenAiPreview: () -> Unit,
	showAdvancedToggles: Boolean,
	onToggleDescreen: (Boolean) -> Unit = {},
	onToggleInkLevels: (Boolean) -> Unit = {},
	onToggleNeuralPreload: (Boolean) -> Unit = {},
) {
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		HardwareTelemetryStrip(
			activeDelegateOverride = when {
				uiState.isNeuralUpscaleEnabled && uiState.activeNeuralDelegateLabel.isNotBlank() ->
					uiState.activeNeuralDelegateLabel
				uiState.activeShaderLabel != "Off" ->
					"Active: GPU Shader (${uiState.activeShaderLabel})"
				else -> null
			},
		)

		SettingsGroupCard {
			SettingsToggleRow(
				title = "Smart Seamless Page Stitch",
				subtitle = "Reassemble chopped webtoon/manga slivers cleanly before rendering",
				checked = uiState.isReaderSeamlessStitchEnabled,
				onCheckedChange = onToggleSeamlessStitch,
			)
			SettingsRowDivider()
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Upscaler / GPU Shader Preset",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = if (UpscaleEffect.isSupported) {
								"Active pipeline: ${uiState.activeShaderLabel}"
							} else {
								"Requires Android 13+ for AGSL RuntimeShader"
							},
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					TextButton(onClick = onOpenReadingSettings) {
						Text("Studio", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan)
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					val presetShape = NemuriTheme.shape(10.dp)
					listOf(
						ReaderShaderPreset.OFF to "Off",
						ReaderShaderPreset.ANIME4K to "Anime4K",
						ReaderShaderPreset.ARTCNN to "ArtCNN",
						ReaderShaderPreset.FSR_1 to "FSR 1.0",
					).forEach { (preset, shortLabel) ->
						val selected = uiState.readerShaderPreset == preset
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = presetShape,
							border = BorderStroke(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.weight(1f)
								.clip(presetShape)
								.clickable(enabled = UpscaleEffect.isSupported || preset == ReaderShaderPreset.OFF) {
									onSelectShaderPreset(preset)
								},
						) {
							Text(
								text = shortLabel,
								style = MaterialTheme.typography.labelMedium,
								fontSize = 11.5.sp,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
							)
						}
					}
				}

				val previewBtnShape = NemuriTheme.shape(12.dp)
				Surface(
					color = NemuriTheme.colors.tealContainer,
					shape = previewBtnShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.65f)),
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 2.dp)
						.clip(previewBtnShape)
						.clickable(onClick = onOpenAiPreview),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Icon(
							imageVector = Icons.Default.AutoAwesome,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(17.dp),
						)
						Spacer(Modifier.width(8.dp))
						Text(
							text = "Interactive AI Upscaling Preview",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
						)
					}
				}
			}

			SettingsRowDivider()
			SettingsToggleRow(
				title = "Neural 2x Super-Resolution",
				subtitle = if (uiState.isNeuralUpscaleSupported) {
					if (uiState.activeNeuralDelegateLabel.isNotBlank()) {
						"ONNX / LiteRT tiled 2x upscaler • ${uiState.activeNeuralDelegateLabel}"
					} else {
						"ONNX / LiteRT tiled 2x super-resolution; pauses on Battery Saver"
					}
				} else {
					"Available in 64-bit builds with ONNX Runtime"
				},
				checked = uiState.isNeuralUpscaleEnabled,
				enabled = uiState.isNeuralUpscaleSupported,
				onCheckedChange = onToggleNeuralUpscale,
			)

			if (showAdvancedToggles) {
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Screentone Descreen",
					subtitle = "Smooths halftone dot noise while preserving crisp line art",
					checked = uiState.isReaderDescreenEnabled,
					onCheckedChange = onToggleDescreen,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Crisp Ink & Paper Levels",
					subtitle = "Deepens faded blacks and cleans muddy scan backgrounds",
					checked = uiState.isReaderInkLevelsEnabled,
					onCheckedChange = onToggleInkLevels,
				)
				if (uiState.isNeuralUpscaleSupported) {
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Pre-Upscale Ahead",
						subtitle = "Background-upscale 1–2 upcoming paged pages into WebP disk cache",
						checked = uiState.isNeuralPreloadEnabled,
						onCheckedChange = onToggleNeuralPreload,
					)
				}
			}

			SettingsRowDivider()
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Text(
						text = "WebP Upscale Disk Cache",
						style = MaterialTheme.typography.labelLarge,
						color = NemuriTheme.colors.textPrimary,
					)
					Text(
						text = "${String.format(Locale.US, "%.1f MB", uiState.neuralCacheBytes / (1024f * 1024f))} / 250.0 MB",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
				TextButton(
					onClick = onClearCache,
					enabled = uiState.neuralCacheBytes > 0L,
				) {
					Text(
						text = "Clear",
						color = if (uiState.neuralCacheBytes > 0L) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
						fontWeight = FontWeight.SemiBold,
					)
				}
			}
		}
	}
}

@Composable
private fun AiBrainOverviewCard(
	uiState: EnhanceHubUiState,
	onTestAiBackend: () -> Unit,
	onSelectRecapDepth: (RecapDepth) -> Unit,
	onOpenAiSettings: () -> Unit,
	showExtendedToggles: Boolean = false,
	onToggleShowRecapCard: (Boolean) -> Unit = {},
	onToggleIncludeNovelExcerpt: (Boolean) -> Unit = {},
) {
	SettingsGroupCard {
		// 1. Active AI backend status + 1-tap Test AI Backend button
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.weight(1f),
				) {
					Surface(
						color = NemuriTheme.colors.mergePurple.copy(alpha = 0.16f),
						shape = NemuriTheme.shape(10.dp),
						modifier = Modifier.size(38.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.AutoAwesome,
								contentDescription = null,
								tint = NemuriTheme.colors.mergePurple,
								modifier = Modifier.size(20.dp),
							)
						}
					}
					Column(modifier = Modifier.weight(1f)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								text = "Active AI Backend",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
							)
							Surface(
								color = NemuriTheme.colors.mergePurple.copy(alpha = 0.16f),
								shape = NemuriTheme.pillShape(),
							) {
								Text(
									text = uiState.activeAiEngineLabel,
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.mergePurple,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
								)
							}
						}
						Text(
							text = "Spoiler-quarantined \"Where Was I?\" story recaps",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}

				OutlinedButton(
					onClick = onTestAiBackend,
					enabled = !uiState.isTestingAi,
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.65f)),
					shape = NemuriTheme.shape(10.dp),
					contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
				) {
					if (uiState.isTestingAi) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.cyan,
							strokeWidth = 2.dp,
							modifier = Modifier.size(14.dp),
						)
						Spacer(Modifier.width(6.dp))
						Text("Testing…", style = MaterialTheme.typography.labelMedium, color = NemuriTheme.colors.cyan)
					} else {
						Icon(
							imageVector = Icons.Default.NetworkCheck,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(15.dp),
						)
						Spacer(Modifier.width(5.dp))
						Text("Test AI Backend", style = MaterialTheme.typography.labelMedium, color = NemuriTheme.colors.cyan)
					}
				}
			}

			uiState.aiTestResult?.let { res ->
				Surface(
					color = if (res.ok) NemuriTheme.colors.healthy.copy(alpha = 0.14f) else NemuriTheme.colors.danger.copy(alpha = 0.14f),
					shape = NemuriTheme.shape(10.dp),
					border = BorderStroke(
						1.dp,
						if (res.ok) NemuriTheme.colors.healthy.copy(alpha = 0.5f) else NemuriTheme.colors.danger.copy(alpha = 0.5f),
					),
					modifier = Modifier.fillMaxWidth(),
				) {
					Row(
						modifier = Modifier.padding(10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = if (res.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
							contentDescription = null,
							tint = if (res.ok) NemuriTheme.colors.healthy else NemuriTheme.colors.danger,
							modifier = Modifier.size(17.dp),
						)
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = res.engineName,
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = res.message,
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textSecondary,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}
		}

		SettingsRowDivider()

		// 2. Default Recap Depth (Brief vs Detailed segmented pills)
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = "Default Recap Depth",
				style = MaterialTheme.typography.titleSmall,
				color = NemuriTheme.colors.textPrimary,
			)
			Text(
				text = uiState.aiRecapDepth.description,
				style = MaterialTheme.typography.bodySmall,
				color = NemuriTheme.colors.textMuted,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				val depthShape = NemuriTheme.shape(10.dp)
				RecapDepth.entries.forEach { depth ->
					val selected = uiState.aiRecapDepth == depth
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = depthShape,
						border = BorderStroke(
							1.dp,
							if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
						),
						modifier = Modifier
							.weight(1f)
							.clip(depthShape)
							.clickable { onSelectRecapDepth(depth) },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
							horizontalArrangement = Arrangement.Center,
							verticalAlignment = Alignment.CenterVertically,
						) {
							if (selected) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(14.dp),
								)
								Spacer(Modifier.width(4.dp))
							}
							Text(
								text = depth.shortLabel,
								style = MaterialTheme.typography.labelLarge,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							)
						}
					}
				}
			}
		}

		if (showExtendedToggles) {
			SettingsRowDivider()
			SettingsToggleRow(
				title = "Show Recap Card on Details",
				subtitle = "Strictly spoiler-quarantined up to your current read chapter",
				checked = uiState.isAiShowRecapCard,
				onCheckedChange = onToggleShowRecapCard,
			)
			SettingsRowDivider()
			SettingsToggleRow(
				title = "Include Read Novel Excerpt",
				subtitle = "Pass a short snippet from your last read chapter for richer novel recaps",
				checked = uiState.isAiIncludeNovelExcerpt,
				onCheckedChange = onToggleIncludeNovelExcerpt,
			)
		}

		SettingsRowDivider()

		// 3. Inline Synopsis Translation (G -> 文 ML Kit / AI status row) + Open AI Engine Studio button
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = Icons.Default.Translate,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(20.dp),
					)
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Inline Synopsis Translation",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "On-device ML Kit & AI fallback ready on Manga Details",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
				Surface(
					color = NemuriTheme.colors.tealContainer,
					shape = NemuriTheme.shape(8.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f)),
				) {
					Text(
						text = "G → 文 Ready",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = NemuriTheme.colors.cyan,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
					)
				}
			}

			OutlinedButton(
				onClick = onOpenAiSettings,
				border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)),
				shape = NemuriTheme.shape(12.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Icon(
					imageVector = Icons.Default.AutoAwesome,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(16.dp),
				)
				Spacer(Modifier.width(8.dp))
				Text(
					text = "Open AI Engine Studio",
					color = NemuriTheme.colors.cyan,
					fontWeight = FontWeight.SemiBold,
				)
			}
		}
	}
}

@Composable
private fun InlineStatPill(
	label: String,
	value: String,
	accent: Color,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = NemuriTheme.shape(12.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = modifier,
	) {
		Column(
			modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = value,
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Bold,
				color = accent,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textMuted,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun OnDeviceSafetyBanner() {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = NemuriTheme.shape(16.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier.padding(14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(
				imageVector = Icons.Default.AutoAwesome,
				contentDescription = null,
				tint = NemuriTheme.colors.mergePurple,
				modifier = Modifier.size(24.dp),
			)
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text(
					text = "On-Device Safety & Battery Guard",
					style = MaterialTheme.typography.titleSmall,
					color = NemuriTheme.colors.textPrimary,
				)
				Text(
					text = "API keys stay Keystore-encrypted, local LLM weights auto-unload after 60s idle, and Neural/GPU upscalers pause under Battery Saver.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
		}
	}
}

@Composable
private fun TelemetryKpiCard(
	label: String,
	value: String,
	accent: Color,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val kpiShape = NemuriTheme.shape(14.dp)
	Surface(
		color = NemuriTheme.colors.card,
		shape = kpiShape,
		modifier = modifier
			.border(1.dp, NemuriTheme.colors.border, kpiShape)
			.clip(kpiShape)
			.clickable(onClick = onClick),
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp),
		) {
			Text(
				text = value,
				style = MaterialTheme.typography.titleMedium,
				color = accent,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textMuted,
				maxLines = 1,
			)
		}
	}
}

@Composable
private fun EnhanceToolCard(
	icon: ImageVector,
	title: String,
	subtitle: String,
	badge: String? = null,
	badgeTint: Color = NemuriTheme.colors.cyan,
	onClick: () -> Unit,
) {
	val toolCardShape = NemuriTheme.shape(16.dp)
	Surface(
		color = NemuriTheme.colors.card,
		shape = toolCardShape,
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, toolCardShape)
			.clip(toolCardShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				color = NemuriTheme.colors.tealContainer,
				shape = NemuriTheme.shape(12.dp),
				modifier = Modifier.size(42.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(22.dp),
					)
				}
			}
			Spacer(Modifier.width(12.dp))
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
						modifier = Modifier.weight(1f, fill = false),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					if (!badge.isNullOrBlank()) {
						Surface(
							color = badgeTint.copy(alpha = 0.16f),
							shape = NemuriTheme.pillShape(),
						) {
							Text(
								text = badge,
								style = MaterialTheme.typography.labelSmall,
								color = badgeTint,
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
							)
						}
					}
				}
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Spacer(Modifier.width(6.dp))
			Icon(
				imageVector = Icons.Default.ChevronRight,
				contentDescription = null,
				tint = NemuriTheme.colors.textDim,
			)
		}
	}
}

private fun formatHubDuration(durationMs: Long): String {
	if (durationMs <= 0L) return "0m"
	val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs)
	val hours = mins / 60
	val remMins = mins % 60
	return if (hours > 0) "${hours}h ${remMins}m" else "${mins}m"
}
