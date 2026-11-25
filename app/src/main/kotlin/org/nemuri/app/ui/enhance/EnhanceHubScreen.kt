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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.settings.SettingsGroupCard
import org.nemuri.app.ui.settings.SettingsRowDivider
import org.nemuri.app.ui.settings.SettingsToggleRow
import org.nemuri.app.ui.theme.NemuriTheme
import java.util.concurrent.TimeUnit

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

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = "",
			onQueryChange = {},
			placeholder = "Enhance Hub • Health, Migration & Shaders",
			onSettingsClick = onOpenSettings,
		)

		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column {
						Text(
							text = "LIBRARY HEALTH & INSIGHTS",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
						)
						Text(
							text = "${uiState.libraryCount} saved titles monitored",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					IconButton(
						onClick = viewModel::refreshDiagnostics,
						enabled = !uiState.isScanningHealth,
						modifier = Modifier.size(34.dp),
					) {
						if (uiState.isScanningHealth) {
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

			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						TelemetryKpiCard(
							label = "Broken Sources",
							value = if (uiState.brokenSourcesCount == 0) "Healthy" else "${uiState.brokenSourcesCount} (${uiState.brokenTitlesCount} titles)",
							accent = if (uiState.brokenSourcesCount == 0) NemuriTheme.colors.healthy else NemuriTheme.colors.amber,
							onClick = onOpenBrokenSources,
							modifier = Modifier.weight(1f),
						)
						TelemetryKpiCard(
							label = "Exact Duplicates",
							value = if (uiState.duplicatePairsCount == 0) "Clean" else "${uiState.duplicatePairsCount} found",
							accent = if (uiState.duplicatePairsCount == 0) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
							onClick = onOpenDuplicates,
							modifier = Modifier.weight(1f),
						)
					}
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
			}

			item {
				Text(
					text = "TOOLS",
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.textDim,
					modifier = Modifier.padding(top = 4.dp),
				)
			}

			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					EnhanceToolCard(
						icon = Icons.Default.Build,
						title = "Broken Sources & Smart Replace",
						subtitle = "Fix unavailable extensions or batch-migrate titles with side-by-side confirmation",
						badge = if (uiState.brokenTitlesCount > 0) "${uiState.brokenTitlesCount} affected" else null,
						badgeTint = NemuriTheme.colors.amber,
						onClick = onOpenBrokenSources,
					)
					EnhanceToolCard(
						icon = Icons.Default.ContentCopy,
						title = "Library Duplicate Scanner",
						subtitle = "Detect duplicate series by tracker ID or title and safely consolidate progress",
						badge = if (uiState.duplicatePairsCount > 0) "${uiState.duplicatePairsCount} ready" else null,
						badgeTint = NemuriTheme.colors.cyan,
						onClick = onOpenDuplicates,
					)
					EnhanceToolCard(
						icon = Icons.Default.BarChart,
						title = "Reading Statistics",
						subtitle = "Activity charts, streaks, reading pace, and top titles breakdown",
						onClick = onOpenStats,
					)
					EnhanceToolCard(
						icon = Icons.Default.AutoAwesome,
						title = "AI Engine & \"Where Was I?\" Recaps",
						subtitle = "Gemini Nano, LiteRT-LM local CPU/GPU fallback, Ollama LAN & Gemini API",
						badge = uiState.aiModeLabel,
						badgeTint = NemuriTheme.colors.mergePurple,
						onClick = onOpenAiSettings,
					)
					EnhanceToolCard(
						icon = Icons.Default.GraphicEq,
						title = "GPU Shaders & Neural 2x Upscaler",
						subtitle = "Active: ${uiState.activeShaderLabel} (Anime4K, FSR 1.0, Waifu2x / Real-ESRGAN)",
						onClick = onOpenReadingSettings,
					)
				}
			}

			item {
				SettingsGroupCard("Duplicate Guard Preferences") {
					SettingsToggleRow(
						title = "Check Duplicates When Adding",
						subtitle = "Warn and offer 1-tap Replace before adding an existing library title",
						checked = uiState.isDuplicateCheckOnAdd,
						onCheckedChange = viewModel::setDuplicateCheckOnAdd,
					)
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Carry Progress on Duplicate Replace",
						subtitle = "Transfer history, bookmarks & tracker links when replacing a duplicate",
						checked = uiState.isDuplicateProgressMigrated,
						onCheckedChange = viewModel::setDuplicateProgressMigrated,
					)
				}
			}

			item {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(16.dp),
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
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = modifier
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
			.clip(RoundedCornerShape(14.dp))
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
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp))
			.clip(RoundedCornerShape(16.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				color = NemuriTheme.colors.tealContainer,
				shape = RoundedCornerShape(12.dp),
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
							shape = RoundedCornerShape(999.dp),
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
