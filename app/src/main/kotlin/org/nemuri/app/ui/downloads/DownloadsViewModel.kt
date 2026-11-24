package org.nemuri.app.ui.downloads

import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.download.domain.DownloadState
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class DownloadSection(val title: String) {
	ACTIVE("Active"),
	QUEUED("Queued"),
	FINISHED("History"),
}

@Immutable
data class DownloadEntryItem(
	val id: UUID,
	val manga: Manga?,
	val mangaTitle: String,
	val workState: WorkInfo.State,
	val section: DownloadSection,
	val progress: Int,
	val max: Int,
	val isPaused: Boolean,
	val isIndeterminate: Boolean,
	val error: String?,
	val etaMillis: Long,
	val downloadedChapters: Int,
	val statusLabel: String,
)

@Immutable
data class DownloadsUiState(
	val activeItems: List<DownloadEntryItem> = emptyList(),
	val queuedItems: List<DownloadEntryItem> = emptyList(),
	val historyItems: List<DownloadEntryItem> = emptyList(),
	val hasCancelableWork: Boolean = false,
	val hasCompletedWork: Boolean = false,
	val isLoaded: Boolean = false,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
	private val scheduler: DownloadWorker.Scheduler,
	private val mangaDataRepository: MangaDataRepository,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val mangaCache = ConcurrentHashMap<Long, Manga>()

	val uiState: StateFlow<DownloadsUiState> = scheduler.observeWorks()
		.map { works ->
			val entries = works
				.mapNotNull { info -> mapWorkInfo(info) }
				.sortedByDescending { it.id.mostSignificantBits }

			val active = entries.filter { it.section == DownloadSection.ACTIVE }
			val queued = entries.filter { it.section == DownloadSection.QUEUED }
			val finished = entries.filter { it.section == DownloadSection.FINISHED }

			DownloadsUiState(
				activeItems = active,
				queuedItems = queued,
				historyItems = finished,
				hasCancelableWork = active.isNotEmpty() || queued.isNotEmpty(),
				hasCompletedWork = finished.isNotEmpty(),
				isLoaded = true,
			)
		}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = DownloadsUiState(),
		)

	fun pause(id: UUID) {
		scheduler.pause(id)
	}

	fun resume(id: UUID) {
		scheduler.resume(id)
	}

	fun skip(id: UUID) {
		scheduler.skip(id)
	}

	fun skipAll(id: UUID) {
		scheduler.skipAll(id)
	}

	fun cancel(id: UUID) {
		viewModelScope.launch(Dispatchers.IO) {
			scheduler.cancel(id)
			toastManager.show("Cancelled download")
		}
	}

	fun retry(id: UUID) {
		viewModelScope.launch(Dispatchers.IO) {
			scheduler.retry(id)
			toastManager.show("Retrying download…")
		}
	}

	fun deleteEntry(id: UUID) {
		viewModelScope.launch(Dispatchers.IO) {
			scheduler.delete(id)
		}
	}

	fun cancelAll() {
		viewModelScope.launch(Dispatchers.IO) {
			scheduler.cancelAll()
			toastManager.show("Cancelled active downloads")
		}
	}

	fun clearCompleted() {
		viewModelScope.launch(Dispatchers.IO) {
			scheduler.removeCompleted()
			toastManager.show("Cleared download history")
		}
	}

	private suspend fun mapWorkInfo(info: WorkInfo): DownloadEntryItem? {
		val data = info.outputData.takeUnless { it.keyValueMap.isEmpty() }
			?: info.progress.takeUnless { it.keyValueMap.isEmpty() }
			?: scheduler.getInputData(info.id)
			?: return null

		val mangaId = DownloadState.getMangaId(data)
		val manga = if (mangaId != 0L) {
			mangaCache[mangaId] ?: runCatchingCancellable {
				mangaDataRepository.findMangaById(mangaId, withChapters = false)
			}.getOrNull()?.also { mangaCache[mangaId] = it }
		} else {
			null
		}

		val isPaused = DownloadState.isPaused(data)
		val isIndeterminate = DownloadState.isIndeterminate(data)
		val max = DownloadState.getMax(data)
		val progress = DownloadState.getProgress(data)
		val error = DownloadState.getError(data)
		val eta = DownloadState.getEta(data)
		val downloadedChapters = DownloadState.getDownloadedChapters(data)

		val section = when (info.state) {
			WorkInfo.State.RUNNING -> DownloadSection.ACTIVE
			WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadSection.QUEUED
			else -> DownloadSection.FINISHED
		}

		val statusLabel = when (info.state) {
			WorkInfo.State.RUNNING -> when {
				isPaused -> "Paused • $progress / $max"
				max > 0 -> {
					val etaSuffix = if (eta > 0L) {
						" • ${DateUtils.formatElapsedTime(eta / 1000L)}"
					} else {
						""
					}
					"Downloading $progress / $max$etaSuffix"
				}
				else -> "Preparing download…"
			}
			WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting in queue…"
			WorkInfo.State.SUCCEEDED -> {
				if (downloadedChapters > 0) "Completed • $downloadedChapters chapter(s)" else "Completed"
			}
			WorkInfo.State.FAILED -> error ?: "Download failed"
			WorkInfo.State.CANCELLED -> "Cancelled"
		}

		return DownloadEntryItem(
			id = info.id,
			manga = manga,
			mangaTitle = manga?.title ?: "Manga #$mangaId",
			workState = info.state,
			section = section,
			progress = progress,
			max = max,
			isPaused = isPaused,
			isIndeterminate = isIndeterminate,
			error = error,
			etaMillis = eta,
			downloadedChapters = downloadedChapters,
			statusLabel = statusLabel,
		)
	}
}
