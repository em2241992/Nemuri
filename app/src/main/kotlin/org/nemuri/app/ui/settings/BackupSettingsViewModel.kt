package org.nemuri.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.backup.MihonBackupExporter
import org.koitharu.kotatsu.backup.MihonBackupManager
import org.koitharu.kotatsu.backup.local.data.LocalBackupRepository
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.koitharu.kotatsu.backup.local.domain.BackupUtils
import org.koitharu.kotatsu.backup.local.ui.periodical.PeriodicalBackupWorker
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.nemuri.app.ui.components.NemuriToastManager
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Provider

data class MihonBackupPreview(
	val uri: Uri,
	val report: MihonBackupManager.RestoreReport,
)

data class BackupSettingsUiState(
	val isWorking: Boolean = false,
	val workingMessage: String? = null,
	val progressFraction: Float? = null,
	val lastSummary: String? = null,
	val isErrorSummary: Boolean = false,
	val pendingNemuriRestoreUri: Uri? = null,
	val pendingMihonPreview: MihonBackupPreview? = null,
	val isPeriodicalEnabled: Boolean = false,
	val periodicalDirText: String? = null,
	val periodicalFrequencyIndex: Int = 3,
	val isPeriodicalTrimEnabled: Boolean = true,
	val periodicalKeepCount: Int = 10,
)

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val localBackupRepositoryProvider: Provider<LocalBackupRepository>,
	private val mihonBackupManagerProvider: Provider<MihonBackupManager>,
	private val mihonBackupExporterProvider: Provider<MihonBackupExporter>,
	private val periodicalBackupScheduler: PeriodicalBackupWorker.Scheduler,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private data class WorkingState(
		val isWorking: Boolean = false,
		val workingMessage: String? = null,
		val progressFraction: Float? = null,
		val lastSummary: String? = null,
		val isErrorSummary: Boolean = false,
		val pendingNemuriRestoreUri: Uri? = null,
		val pendingMihonPreview: MihonBackupPreview? = null,
	)

	private val workingState = MutableStateFlow(WorkingState())

	val uiState: StateFlow<BackupSettingsUiState> = combine(
		workingState,
		settings.observeChanges().onStart { emit(null) },
	) { work, _ ->
		BackupSettingsUiState(
			isWorking = work.isWorking,
			workingMessage = work.workingMessage,
			progressFraction = work.progressFraction,
			lastSummary = work.lastSummary,
			isErrorSummary = work.isErrorSummary,
			pendingNemuriRestoreUri = work.pendingNemuriRestoreUri,
			pendingMihonPreview = work.pendingMihonPreview,
			isPeriodicalEnabled = settings.isPeriodicalBackupEnabled,
			periodicalDirText = settings.periodicalBackupDirectory?.let(::formatBackupDirUri),
			periodicalFrequencyIndex = settings.periodicalBackupFrequencyIndex,
			isPeriodicalTrimEnabled = settings.isPeriodicalBackupTrimEnabled,
			periodicalKeepCount = settings.periodicalBackupKeepCount,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = BackupSettingsUiState(),
	)

	fun suggestedNemuriFileName(): String = BackupUtils.generateFileName(context)

	fun suggestedMihonFileName(): String = MihonBackupExporter.generateFileName()

	fun exportNemuriBackup(uri: Uri) {
		if (workingState.value.isWorking) return
		viewModelScope.launch {
			workingState.value = workingState.value.copy(
				isWorking = true,
				workingMessage = "Creating Nemuri backup…",
				progressFraction = null,
			)
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val output = context.contentResolver.openOutputStream(uri)
						?: throw IOException("Could not open destination file")
					val collector = FlowCollector<org.koitharu.kotatsu.core.util.progress.Progress> { p ->
						workingState.value = workingState.value.copy(
							progressFraction = if (p.isIndeterminate) null else p.percent.coerceIn(0f, 1f),
						)
					}
					output.use { out ->
						ZipOutputStream(out).use { zip ->
							localBackupRepositoryProvider.get().createBackup(zip, collector)
						}
					}
				}
			}
			result.onSuccess {
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					progressFraction = null,
					lastSummary = "Nemuri backup (.bk.zip) exported successfully.",
					isErrorSummary = false,
				)
				toastManager.show("Backup exported")
			}.onFailure { err ->
				val msg = err.localizedMessage ?: "Backup export failed"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					progressFraction = null,
					lastSummary = msg,
					isErrorSummary = true,
				)
				toastManager.show(msg)
			}
		}
	}

	fun beginNemuriRestore(uri: Uri) {
		workingState.value = workingState.value.copy(pendingNemuriRestoreUri = uri)
	}

	fun cancelNemuriRestore() {
		workingState.value = workingState.value.copy(pendingNemuriRestoreUri = null)
	}

	fun confirmNemuriRestore(selectedSections: Set<BackupSection>) {
		val uri = workingState.value.pendingNemuriRestoreUri ?: return
		if (workingState.value.isWorking) return
		workingState.value = workingState.value.copy(
			pendingNemuriRestoreUri = null,
			isWorking = true,
			workingMessage = "Restoring Nemuri backup…",
			progressFraction = null,
		)
		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val input = context.contentResolver.openInputStream(uri)
						?: throw IOException("Could not open backup archive")
					val collector = FlowCollector<org.koitharu.kotatsu.core.util.progress.Progress> { p ->
						workingState.value = workingState.value.copy(
							progressFraction = if (p.isIndeterminate) null else p.percent.coerceIn(0f, 1f),
						)
					}
					val sectionsToRestore = selectedSections + BackupSection.INDEX
					input.use { stream ->
						ZipInputStream(stream).use { zip ->
							localBackupRepositoryProvider.get().restoreBackup(zip, sectionsToRestore, collector)
						}
					}
				}
			}
			result.onSuccess { composite ->
				val summary = if (composite.isAllSuccess) {
					"Restored backup (${composite.size} entries processed)."
				} else {
					"Restored with ${composite.failures.size} warning(s) out of ${composite.size} entries."
				}
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					progressFraction = null,
					lastSummary = summary,
					isErrorSummary = composite.isAllFailed,
				)
				toastManager.show("Backup restore finished")
			}.onFailure { err ->
				val msg = err.localizedMessage ?: "Restore failed"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					progressFraction = null,
					lastSummary = msg,
					isErrorSummary = true,
				)
				toastManager.show(msg)
			}
		}
	}

	fun exportMihonBackup(uri: Uri) {
		if (workingState.value.isWorking) return
		viewModelScope.launch {
			workingState.value = workingState.value.copy(
				isWorking = true,
				workingMessage = "Exporting Mihon backup (.tachibk)…",
				progressFraction = null,
			)
			val result = runCatching {
				withContext(Dispatchers.IO) {
					mihonBackupExporterProvider.get().export(uri)
				}
			}
			result.onSuccess { report ->
				val msg = "Exported ${report.exportedCount} title(s) to .tachibk" +
					if (report.skippedCount > 0) " (${report.skippedCount} skipped)" else ""
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					lastSummary = msg,
					isErrorSummary = false,
				)
				toastManager.show("Mihon backup exported")
			}.onFailure { err ->
				val msg = err.localizedMessage ?: "Mihon export failed"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					lastSummary = msg,
					isErrorSummary = true,
				)
				toastManager.show(msg)
			}
		}
	}

	fun analyzeMihonBackup(uri: Uri) {
		if (workingState.value.isWorking) return
		viewModelScope.launch {
			workingState.value = workingState.value.copy(
				isWorking = true,
				workingMessage = "Inspecting .tachibk backup…",
				progressFraction = null,
			)
			val result = runCatching {
				withContext(Dispatchers.IO) {
					mihonBackupManagerProvider.get().analyzeBackup(uri)
				}
			}
			result.onSuccess { report ->
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					pendingMihonPreview = MihonBackupPreview(uri = uri, report = report),
				)
			}.onFailure { err ->
				val msg = err.localizedMessage ?: "Could not inspect .tachibk file"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					lastSummary = msg,
					isErrorSummary = true,
				)
				toastManager.show(msg)
			}
		}
	}

	fun cancelMihonImport() {
		workingState.value = workingState.value.copy(pendingMihonPreview = null)
	}

	fun confirmMihonImport(options: MihonBackupManager.Options) {
		val preview = workingState.value.pendingMihonPreview ?: return
		if (workingState.value.isWorking) return
		workingState.value = workingState.value.copy(
			pendingMihonPreview = null,
			isWorking = true,
			workingMessage = "Importing Mihon backup…",
			progressFraction = null,
		)
		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					mihonBackupManagerProvider.get().restoreBackup(preview.uri, options)
				}
			}
			result.onSuccess { report ->
				val missingSuffix = if (report.missingSources.isNotEmpty()) {
					"\nMissing sources (${report.missingSources.size}): ${report.missingSources.take(5).joinToString()}"
				} else {
					""
				}
				val summary = "Imported ${report.restoredMangaCount} title(s) and ${report.restoredTrackingCount} tracker link(s).$missingSuffix"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					lastSummary = summary,
					isErrorSummary = false,
				)
				toastManager.show("Imported ${report.restoredMangaCount} titles")
			}.onFailure { err ->
				val msg = err.localizedMessage ?: "Mihon import failed"
				workingState.value = workingState.value.copy(
					isWorking = false,
					workingMessage = null,
					lastSummary = msg,
					isErrorSummary = true,
				)
				toastManager.show(msg)
			}
		}
	}

	// Scheduled backups
	fun setPeriodicalEnabled(enabled: Boolean) {
		settings.setPeriodicalBackupEnabled(enabled)
		viewModelScope.launch {
			runCatching {
				if (enabled && settings.periodicalBackupDirectory != null) {
					periodicalBackupScheduler.schedule()
				} else if (!enabled) {
					periodicalBackupScheduler.unschedule()
				}
			}
		}
	}

	fun setPeriodicalDirectory(uri: Uri?) {
		if (uri == null) return
		runCatching {
			context.contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
			)
		}
		settings.periodicalBackupDirectory = uri
		if (settings.isPeriodicalBackupEnabled) {
			viewModelScope.launch {
				runCatching { periodicalBackupScheduler.schedule() }
			}
		}
		toastManager.show("Backup folder updated")
	}

	fun setPeriodicalFrequencyIndex(index: Int) {
		settings.setPeriodicalBackupFrequencyIndex(index)
	}

	fun setPeriodicalTrim(enabled: Boolean, maxCount: Int) {
		settings.setPeriodicalBackupTrim(enabled, maxCount)
	}

	private fun formatBackupDirUri(uri: Uri): String {
		val last = uri.lastPathSegment ?: return uri.toString()
		return last.substringAfter(':', last).ifBlank { uri.toString() }
	}
}
