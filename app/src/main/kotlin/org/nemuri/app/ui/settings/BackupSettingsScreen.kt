package org.nemuri.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.backup.MihonBackupManager
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun BackupSettingsScreen(
	onBack: () -> Unit,
	viewModel: BackupSettingsViewModel = viewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showFreqDialog by remember { mutableStateOf(false) }
	var showKeepCountDialog by remember { mutableStateOf(false) }

	val createNemuriBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip"),
	) { uri -> uri?.let(viewModel::exportNemuriBackup) }

	val openNemuriRestoreLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri -> uri?.let(viewModel::beginNemuriRestore) }

	val createMihonBackupLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/octet-stream"),
	) { uri -> uri?.let(viewModel::exportMihonBackup) }

	val openMihonImportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri -> uri?.let(viewModel::analyzeMihonBackup) }

	val openBackupTreeLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree(),
	) { uri -> uri?.let(viewModel::setPeriodicalDirectory) }

	SettingsLazyPage(
		title = SettingsSection.BACKUP_RESTORE.title,
		onBack = onBack,
	) {
		if (state.isWorking) {
			item {
				Surface(
					color = NemuriTheme.colors.tealContainer,
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = state.workingMessage ?: "Processing backup…",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.cyan,
						)
						val fraction = state.progressFraction
						if (fraction != null) {
							LinearProgressIndicator(
								progress = { fraction },
								color = NemuriTheme.colors.cyan,
								trackColor = NemuriTheme.colors.surfaceElevated,
								modifier = Modifier.fillMaxWidth(),
							)
						} else {
							LinearProgressIndicator(
								color = NemuriTheme.colors.cyan,
								trackColor = NemuriTheme.colors.surfaceElevated,
								modifier = Modifier.fillMaxWidth(),
							)
						}
					}
				}
			}
		}

		if (!state.lastSummary.isNullOrBlank()) {
			item {
				val accent = if (state.isErrorSummary) NemuriTheme.colors.danger else NemuriTheme.colors.healthy
				Surface(
					color = accent.copy(alpha = 0.12f),
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(
						text = state.lastSummary.orEmpty(),
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textPrimary,
						modifier = Modifier.padding(14.dp),
					)
				}
			}
		}

		item {
			SettingsGroupCard("Nemuri / Kotatsu Backup (.bk.zip)") {
				SettingsActionRow(
					icon = Icons.Default.Archive,
					title = "Create Full Backup",
					subtitle = "Export library, history, bookmarks, stats, trackers & settings",
					enabled = !state.isWorking,
					onClick = { createNemuriBackupLauncher.launch(viewModel.suggestedNemuriFileName()) },
				)
				SettingsRowDivider()
				SettingsActionRow(
					icon = Icons.Default.Restore,
					title = "Restore Backup Archive",
					subtitle = "Restore selected sections from a .bk.zip or .zip file",
					enabled = !state.isWorking,
					onClick = {
						openNemuriRestoreLauncher.launch(
							arrayOf("application/zip", "application/octet-stream", "*/*"),
						)
					},
				)
			}
		}

		item {
			SettingsGroupCard("Mihon / Tachiyomi Migration (.tachibk)") {
				SettingsActionRow(
					icon = Icons.Default.FileOpen,
					title = "Import .tachibk Backup",
					subtitle = "Migrate library entries, reading progress, repos & tracker links",
					enabled = !state.isWorking,
					onClick = { openMihonImportLauncher.launch(arrayOf("*/*")) },
				)
				SettingsRowDivider()
				SettingsActionRow(
					icon = Icons.Default.UploadFile,
					title = "Export to .tachibk",
					subtitle = "Save library & reading state in Mihon / Tachiyomi protobuf format",
					enabled = !state.isWorking,
					onClick = { createMihonBackupLauncher.launch(viewModel.suggestedMihonFileName()) },
				)
			}
		}

		item {
			SettingsGroupCard("Automatic Scheduled Backups") {
				SettingsToggleRow(
					title = "Enable Scheduled Backups",
					subtitle = "Automatically save .bk.zip snapshots to a chosen folder",
					checked = state.isPeriodicalEnabled,
					onCheckedChange = viewModel::setPeriodicalEnabled,
				)
				SettingsRowDivider()
				SettingsActionRow(
					icon = Icons.Default.FolderOpen,
					title = "Backup Folder",
					subtitle = state.periodicalDirText ?: "Not selected — tap to choose folder",
					onClick = { openBackupTreeLauncher.launch(null) },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Backup Frequency",
					currentLabel = backupFrequencyLabel(state.periodicalFrequencyIndex),
					onClick = { showFreqDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Keep Limited Snapshots",
					subtitle = "Automatically prune oldest automatic backup files",
					checked = state.isPeriodicalTrimEnabled,
					onCheckedChange = { enabled ->
						viewModel.setPeriodicalTrim(enabled, state.periodicalKeepCount)
					},
				)
				if (state.isPeriodicalTrimEnabled) {
					SettingsRowDivider()
					SettingsChoiceRow(
						title = "Maximum Snapshots to Keep",
						currentLabel = "${state.periodicalKeepCount}",
						onClick = { showKeepCountDialog = true },
					)
				}
			}
		}
	}

	if (state.pendingNemuriRestoreUri != null) {
		NemuriRestoreSectionsDialog(
			onConfirm = viewModel::confirmNemuriRestore,
			onDismiss = viewModel::cancelNemuriRestore,
		)
	}

	state.pendingMihonPreview?.let { preview ->
		MihonImportOptionsDialog(
			preview = preview,
			onConfirm = viewModel::confirmMihonImport,
			onDismiss = viewModel::cancelMihonImport,
		)
	}

	if (showFreqDialog) {
		SettingsOptionDialog(
			title = "Backup Frequency",
			options = listOf(
				0 to "Every 6 hours",
				1 to "Daily",
				2 to "Every 2 days",
				3 to "Weekly",
				4 to "Every 2 weeks",
				5 to "Monthly",
			),
			selected = state.periodicalFrequencyIndex,
			onSelect = viewModel::setPeriodicalFrequencyIndex,
			onDismiss = { showFreqDialog = false },
		)
	}

	if (showKeepCountDialog) {
		SettingsOptionDialog(
			title = "Maximum Snapshots to Keep",
			options = listOf(3, 5, 10, 20, 30).map { it to "$it backups" },
			selected = state.periodicalKeepCount,
			onSelect = { count -> viewModel.setPeriodicalTrim(true, count) },
			onDismiss = { showKeepCountDialog = false },
		)
	}
}

@Composable
private fun NemuriRestoreSectionsDialog(
	onConfirm: (Set<BackupSection>) -> Unit,
	onDismiss: () -> Unit,
) {
	val selectableSections = remember {
		BackupSection.entries.filter { it != BackupSection.INDEX }
	}
	val checkedMap = remember {
		mutableStateMapOf<BackupSection, Boolean>().apply {
			selectableSections.forEach { put(it, true) }
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text("Select Sections to Restore") },
		text = {
			Column(
				modifier = Modifier
					.heightIn(max = 360.dp)
					.verticalScroll(rememberScrollState()),
			) {
				selectableSections.forEach { section ->
					val isChecked = checkedMap[section] == true
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { checkedMap[section] = !isChecked }
							.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = isChecked,
							onCheckedChange = { checkedMap[section] = it },
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
						)
						Spacer(Modifier.width(6.dp))
						Text(
							text = stringResource(section.titleResId),
							style = MaterialTheme.typography.bodyMedium,
							color = NemuriTheme.colors.textPrimary,
						)
					}
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					val chosen = checkedMap.filterValues { it }.keys
					onConfirm(chosen)
				},
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
			) { Text("Restore") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel", color = NemuriTheme.colors.textMuted)
			}
		},
	)
}

@Composable
private fun MihonImportOptionsDialog(
	preview: MihonBackupPreview,
	onConfirm: (MihonBackupManager.Options) -> Unit,
	onDismiss: () -> Unit,
) {
	var libraryEntries by remember { mutableStateOf(true) }
	var tracking by remember { mutableStateOf(true) }
	var extensionRepos by remember { mutableStateOf(true) }
	var sourceSettings by remember { mutableStateOf(true) }
	var appSettings by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text("Import Mihon Backup") },
		text = {
			Column(
				modifier = Modifier
					.heightIn(max = 380.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				if (preview.report.missingSources.isNotEmpty()) {
					Surface(
						color = NemuriTheme.colors.amber.copy(alpha = 0.14f),
						shape = RoundedCornerShape(10.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.4f)),
					) {
						Text(
							text = "${preview.report.missingSources.size} source extension(s) are not currently installed. Entries still import and can be opened once their extensions are added.",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.amber,
							modifier = Modifier.padding(10.dp),
						)
					}
				}

				OptionCheckboxRow("Library entries, categories & history", libraryEntries) { libraryEntries = it }
				OptionCheckboxRow("Tracker links (AniList, MAL, Shikimori, Kitsu, MangaBaka)", tracking) { tracking = it }
				OptionCheckboxRow("Extension repositories", extensionRepos) { extensionRepos = it }
				OptionCheckboxRow("Per-source preferences", sourceSettings) { sourceSettings = it }
				OptionCheckboxRow("App settings", appSettings) { appSettings = it }
			}
		},
		confirmButton = {
			Button(
				onClick = {
					onConfirm(
						MihonBackupManager.Options(
							libraryEntries = libraryEntries,
							appSettings = appSettings,
							sourceSettings = sourceSettings,
							extensionRepoSettings = extensionRepos,
							tracking = tracking,
						),
					)
				},
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
			) { Text("Import") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel", color = NemuriTheme.colors.textMuted)
			}
		},
	)
}

@Composable
private fun OptionCheckboxRow(
	label: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onCheckedChange(!checked) }
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Checkbox(
			checked = checked,
			onCheckedChange = onCheckedChange,
			colors = CheckboxDefaults.colors(
				checkedColor = NemuriTheme.colors.cyan,
				checkmarkColor = NemuriTheme.colors.onCyan,
			),
		)
		Spacer(Modifier.width(6.dp))
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = NemuriTheme.colors.textPrimary,
		)
	}
}

private fun backupFrequencyLabel(index: Int): String = when (index) {
	0 -> "Every 6h"
	1 -> "Daily"
	2 -> "Every 2d"
	3 -> "Weekly"
	4 -> "Every 2w"
	else -> "Monthly"
}
