package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.stats.domain.StatsBucketUnit
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsRecord
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.settings.SettingsGroupCard
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.settings.SettingsToggleRow
import org.nemuri.app.ui.theme.NemuriTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ReadingStatsScreen(
	onOpenDetails: (Manga) -> Unit,
	onBack: () -> Unit,
	viewModel: ReadingStatsViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	var showClearDialog by remember { mutableStateOf(false) }
	var expandOthers by remember { mutableStateOf(false) }

	SettingsScaffold(
		title = "Reading Statistics",
		subtitle = stringResource(uiState.period.titleResId),
		onBack = onBack,
		actions = {
			if (!uiState.stats.isEmpty) {
				IconButton(onClick = { showClearDialog = true }) {
					Icon(
						imageVector = Icons.Default.DeleteOutline,
						contentDescription = "Clear statistics",
						tint = NemuriTheme.colors.textSecondary,
					)
				}
			}
		},
	) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					StatsPeriod.entries.forEach { period ->
						val selected = uiState.period == period
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
								.clickable { viewModel.selectPeriod(period) },
						) {
							Text(
								text = stringResource(period.titleResId),
								style = MaterialTheme.typography.labelMedium,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
							)
						}
					}
				}
			}

			if (uiState.categories.isNotEmpty()) {
				item {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						val allSelected = uiState.selectedCategoryIds.isEmpty()
						Surface(
							color = if (allSelected) NemuriTheme.colors.surfaceElevated else NemuriTheme.colors.card,
							shape = RoundedCornerShape(999.dp),
							modifier = Modifier
								.border(
									1.dp,
									if (allSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									RoundedCornerShape(999.dp),
								)
								.clip(RoundedCornerShape(999.dp))
								.clickable { viewModel.clearCategoryFilter() },
						) {
							Text(
								text = "All Categories",
								style = MaterialTheme.typography.labelSmall,
								color = if (allSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
							)
						}
						uiState.categories.forEach { cat ->
							val selected = cat.id in uiState.selectedCategoryIds
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
								shape = RoundedCornerShape(999.dp),
								modifier = Modifier
									.border(
										1.dp,
										if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										RoundedCornerShape(999.dp),
									)
									.clip(RoundedCornerShape(999.dp))
									.clickable { viewModel.toggleCategoryFilter(cat.id) },
							) {
								Text(
									text = cat.title,
									style = MaterialTheme.typography.labelSmall,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
								)
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard {
					SettingsToggleRow(
						title = "Record Reading Sessions",
						subtitle = "Track time, chapters & pages automatically (skipped in Incognito)",
						checked = uiState.isStatsEnabled,
						onCheckedChange = viewModel::setStatsEnabled,
					)
				}
			}

			when {
				uiState.isLoading -> {
					item {
						NemuriLoadingState(message = "Aggregating reading statistics…")
					}
				}

				uiState.stats.isEmpty -> {
					item {
						NemuriEmptyState(
							icon = Icons.Default.BarChart,
							title = "No statistics for this window",
							subtitle = "Open a manga or novel and read a few pages/chapters to populate charts and streaks.",
						)
					}
				}

				else -> {
					val stats = uiState.stats
					item {
						Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								StatsKpiCard("Total Time", formatStatsDuration(stats.totalDuration), NemuriTheme.colors.cyan, Modifier.weight(1f))
								StatsKpiCard("Chapters Read", "${stats.chapters}", NemuriTheme.colors.healthy, Modifier.weight(1f))
								StatsKpiCard("Pages / Blocks", "${stats.pages}", NemuriTheme.colors.textPrimary, Modifier.weight(1f))
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								StatsKpiCard("Current Streak", "${stats.currentStreak}d", NemuriTheme.colors.mergePurple, Modifier.weight(1f))
								StatsKpiCard("Longest Streak", "${stats.longestStreak}d", NemuriTheme.colors.amber, Modifier.weight(1f))
								StatsKpiCard("Active Days", "${stats.activeDays}", NemuriTheme.colors.textSecondary, Modifier.weight(1f))
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								val pace = if (stats.minutesPerChapter > 0.0) String.format(Locale.US, "%.1f min/ch", stats.minutesPerChapter) else "—"
								val avgDay = formatStatsDuration(stats.averagePerActiveDay)
								StatsKpiCard("Avg Chapter Pace", pace, NemuriTheme.colors.textSecondary, Modifier.weight(1f))
								StatsKpiCard("Avg / Active Day", avgDay, NemuriTheme.colors.textSecondary, Modifier.weight(1f))
							}
						}
					}

					if (stats.buckets.isNotEmpty()) {
						item {
							SettingsGroupCard("Activity Timeline") {
								Column(
									modifier = Modifier.padding(14.dp),
									verticalArrangement = Arrangement.spacedBy(10.dp),
								) {
									val maxDuration = stats.buckets.maxOf { it.duration }.coerceAtLeast(1L)
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.height(110.dp),
										horizontalArrangement = Arrangement.spacedBy(3.dp),
										verticalAlignment = Alignment.Bottom,
									) {
										stats.buckets.forEach { bucket ->
											val fraction = if (bucket.duration <= 0L) {
												0.04f
											} else {
												(bucket.duration.toFloat() / maxDuration.toFloat()).coerceIn(0.10f, 1f)
											}
											Box(
												modifier = Modifier
													.weight(1f)
													.height((98f * fraction).dp)
													.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
													.background(
														if (bucket.duration > 0L) NemuriTheme.colors.cyan else NemuriTheme.colors.surfaceElevated,
													),
											)
										}
									}
									val firstLabel = formatBucketLabel(stats.buckets.first().startAt, stats.bucketUnit)
									val lastLabel = formatBucketLabel(stats.buckets.last().startAt, stats.bucketUnit)
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween,
									) {
										Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textDim)
										Text(
											text = "Peak: ${formatStatsDuration(maxDuration)}",
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.cyan,
										)
										Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textDim)
									}
								}
							}
						}
					}

					item {
						Text(
							text = "MOST READ TITLES",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textDim,
						)
					}

					items(
						stats.records,
						key = { it.manga?.id ?: -999999L },
					) { record ->
						if (record.manga != null) {
							StatsTitleRow(
								record = record,
								totalDuration = stats.totalDuration,
								onClick = {
									MangaTransferCache.put(record.manga!!)
									onOpenDetails(record.manga!!)
								},
							)
						} else {
							Surface(
								color = NemuriTheme.colors.card,
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
									.clip(RoundedCornerShape(14.dp))
									.clickable { expandOthers = !expandOthers },
							) {
								Row(
									modifier = Modifier.padding(14.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = "Other titles (${stats.otherRecords.size})",
											style = MaterialTheme.typography.titleSmall,
											color = NemuriTheme.colors.textPrimary,
										)
										Text(
											text = "${formatStatsDuration(record.duration)} • ${record.chapters} ch • ${record.pages} pages",
											style = MaterialTheme.typography.bodySmall,
											color = NemuriTheme.colors.textMuted,
										)
									}
									Icon(
										imageVector = if (expandOthers) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
										contentDescription = null,
										tint = NemuriTheme.colors.cyan,
									)
								}
							}
						}
					}

					if (expandOthers && stats.otherRecords.isNotEmpty()) {
						items(stats.otherRecords, key = { "other:${it.manga?.id ?: it.hashCode()}" }) { record ->
							record.manga?.let { m ->
								StatsTitleRow(
									record = record,
									totalDuration = stats.totalDuration,
									onClick = {
										MangaTransferCache.put(m)
										onOpenDetails(m)
									},
								)
							}
						}
					}
				}
			}
		}
	}

	if (showClearDialog) {
		AlertDialog(
			onDismissRequest = { showClearDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Clear Reading Statistics?") },
			text = {
				Text(
					"This resets all recorded reading times, streaks, and per-title stats. Library entries and reading progress are kept.",
					color = NemuriTheme.colors.textMuted,
				)
			},
			confirmButton = {
				Button(
					onClick = {
						showClearDialog = false
						viewModel.clearAllStats()
					},
					colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.danger),
				) { Text("Clear") }
			},
			dismissButton = {
				TextButton(onClick = { showClearDialog = false }) {
					Text("Cancel", color = NemuriTheme.colors.textMuted)
				}
			},
		)
	}
}

@Composable
private fun StatsKpiCard(
	label: String,
	value: String,
	accent: Color,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = modifier,
	) {
		Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(value, style = MaterialTheme.typography.titleMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(label, style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted, maxLines = 1)
		}
	}
}

@Composable
private fun StatsTitleRow(
	record: StatsRecord,
	totalDuration: Long,
	onClick: () -> Unit,
) {
	val manga = record.manga ?: return
	val share = if (totalDuration > 0L) (record.duration.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.size(width = 46.dp, height = 66.dp)
					.clip(RoundedCornerShape(8.dp)),
			)
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(
					text = manga.title,
					style = MaterialTheme.typography.titleSmall,
					color = NemuriTheme.colors.textPrimary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "${formatStatsDuration(record.duration)} • ${record.chapters} ch • ${record.pages} pages",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
				LinearProgressIndicator(
					progress = { share },
					color = NemuriTheme.colors.cyan,
					trackColor = NemuriTheme.colors.surfaceElevated,
					modifier = Modifier
						.fillMaxWidth()
						.height(4.dp)
						.clip(RoundedCornerShape(2.dp)),
				)
			}
		}
	}
}

private fun formatBucketLabel(startAt: Long, unit: StatsBucketUnit): String {
	val pattern = when (unit) {
		StatsBucketUnit.HOUR -> "HH:mm"
		StatsBucketUnit.DAY -> "MMM d"
		StatsBucketUnit.WEEK -> "MMM d"
		StatsBucketUnit.MONTH -> "MMM yy"
	}
	return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(startAt))
}

private fun formatStatsDuration(durationMs: Long): String {
	if (durationMs <= 0L) return "0m"
	val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs)
	val hours = mins / 60
	val remMins = mins % 60
	return when {
		hours > 0 -> "${hours}h ${remMins}m"
		mins > 0 -> "${mins}m"
		else -> "${TimeUnit.MILLISECONDS.toSeconds(durationMs)}s"
	}
}
