package org.nemuri.app.reader

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.ai.domain.LocalLlmClient
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.BookmarksRepository
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.getLocalizedTitle
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.MangaIntent
import org.koitharu.kotatsu.core.nav.ReaderIntent
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ReaderTapZoneMode
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isZipUri
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.domain.DetailsLoadUseCase
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.domain.ChaptersLoader
import org.koitharu.kotatsu.reader.domain.DetectReaderModeUseCase
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.NeuralModelDownloadState
import org.koitharu.kotatsu.reader.domain.NeuralUpscaleModelManager
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.domain.PageStitcher
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.koitharu.kotatsu.stats.domain.StatsCollector
import org.nemuri.app.browser.WebViewChallengeAction
import org.nemuri.app.browser.toWebViewChallengeAction
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import org.nemuri.app.ui.settings.NeuralModelUiItem
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile
import javax.inject.Inject

@Immutable
sealed interface NovelBlock {
	val id: String

	@Immutable
	data class Heading(override val id: String, val text: String, val level: Int) : NovelBlock

	@Immutable
	data class Paragraph(override val id: String, val text: String) : NovelBlock

	@Immutable
	data class Divider(override val id: String) : NovelBlock

	@Immutable
	data class Image(override val id: String, val url: String) : NovelBlock
}

@Immutable
data class ReaderChapterItem(
	val chapter: MangaChapter,
	val title: String,
	val isCurrent: Boolean,
)

@Immutable
data class ReaderUiState(
	val manga: Manga? = null,
	val details: MangaDetails? = null,
	val title: String = "",
	val chapterTitle: String = "",
	val currentChapterId: Long = 0L,
	val chapters: List<ReaderChapterItem> = emptyList(),
	val isTextMode: Boolean = false,
	val readerMode: ReaderMode = ReaderMode.STANDARD,
	val pages: List<ReaderPage> = emptyList(),
	val currentPageIndex: Int = 0,
	val pageJumpToken: Int = 0,
	val novelBlocks: List<NovelBlock> = emptyList(),
	val novelInitialBlockIndex: Int = 0,
	val novelScrollToken: Int = 0,
	val novelCurrentBlockIndex: Int = 0,
	val hasPrevChapter: Boolean = false,
	val hasNextChapter: Boolean = false,
	val isBookmarked: Boolean = false,
	val isIncognito: Boolean = false,
	val keepScreenOn: Boolean = true,
	val volumeButtonsEnabled: Boolean = false,
	val is32BitColors: Boolean = false,
	val shaderConfig: ReaderShaderConfig = ReaderShaderConfig(),
	val isNeuralUpscaleSupported: Boolean = false,
	val isNeuralUpscaleEnabled: Boolean = false,
	val neuralAccelerator: NeuralAccelerator = NeuralAccelerator.AUTO,
	val selectedNeuralModelId: String = "",
	val hasReadyNeuralModel: Boolean = false,
	val activeNeuralModelLabel: String = "",
	val activeNeuralDelegate: String = "Idle (Ready)",
	val neuralCatalog: List<NeuralModelUiItem> = emptyList(),
	val neuralDownloadState: NeuralModelDownloadState = NeuralModelDownloadState(),
	val neuralCacheBytes: Long = 0L,
	val neuralRevision: Int = 0,
	val isSeamlessStitchEnabled: Boolean = true,
	val tapZoneMode: ReaderTapZoneMode = ReaderTapZoneMode.DEFAULT,
	val isDoubleTapSmartZoom: Boolean = true,
	val showPageNumbers: Boolean = false,
	val chapterStartPageNumber: Int = 1,
	val chapterEndPageNumber: Int = 1,
	val chapterTotalPages: Int = 0,
	val chapterStartGlobalIndex: Int = 0,
	val chapterLogicalCount: Int = 0,
	val epubTheme: String = "system",
	val epubFontSize: Int = 100,
	val epubLineHeight: Int = 160,
	val epubFontFamily: String = "serif",
	val epubHorizontalPadding: Int = 20,
	val isLoading: Boolean = true,
	val isChapterLoading: Boolean = false,
	val errorMessage: String? = null,
	val pendingCloudFlare: CloudFlareProtectedException? = null,
	val pendingInteractive: InteractiveActionRequiredException? = null,
	val pendingChallengeUrl: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	@ApplicationContext private val context: Context,
	private val detailsLoadUseCase: DetailsLoadUseCase,
	private val mangaDataRepository: MangaDataRepository,
	private val historyRepository: HistoryRepository,
	private val bookmarksRepository: BookmarksRepository,
	private val chaptersLoader: ChaptersLoader,
	private val pageLoader: PageLoader,
	private val pageStitcher: PageStitcher,
	private val pageUpscaleEngine: PageUpscaleEngine,
	private val upscaleModelManager: NeuralUpscaleModelManager,
	private val detectReaderModeUseCase: DetectReaderModeUseCase,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val statsCollector: StatsCollector,
	private val localLlmClient: LocalLlmClient,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val rawIntent = MangaIntent(savedStateHandle)
	private val initialState = savedStateHandle.get<ReaderState>(ReaderIntent.EXTRA_STATE)
	private val requestedBranch = savedStateHandle.get<String>(ReaderIntent.EXTRA_BRANCH)
	private val extraIncognito = savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO) == true
	private val isPeek = savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_PEEK) == true

	private val _uiState = MutableStateFlow(
		ReaderUiState(
			keepScreenOn = settings.isReaderKeepScreenOn,
			volumeButtonsEnabled = settings.isReaderVolumeButtonsEnabled,
			is32BitColors = settings.is32BitColorsEnabled,
			shaderConfig = ReaderShaderConfig(
				preset = ReaderShaderPreset.fromKey(settings.readerShaderMode),
				descreen = settings.isReaderDescreenEnabled,
				inkLevels = settings.isReaderInkLevelsEnabled,
				strength = settings.readerShaderStrength,
			),
			isNeuralUpscaleSupported = pageUpscaleEngine.isSupported,
			isNeuralUpscaleEnabled = settings.isNeuralUpscaleEnabled,
			neuralAccelerator = NeuralAccelerator.fromKey(settings.neuralUpscaleAccelerator),
			selectedNeuralModelId = settings.neuralUpscaleModelId,
			isSeamlessStitchEnabled = settings.isReaderSeamlessStitchEnabled,
			tapZoneMode = ReaderTapZoneMode.fromKey(settings.readerTapZoneMode),
			isDoubleTapSmartZoom = settings.isReaderDoubleTapFill,
			showPageNumbers = settings.isPagesNumbersEnabled,
			epubTheme = settings.epubTheme,
			epubFontSize = settings.epubFontSize,
			epubLineHeight = settings.epubLineHeight,
			epubFontFamily = settings.epubFontFamily,
			epubHorizontalPadding = settings.epubHorizontalPadding,
		),
	)
	val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

	private var activeDetails: MangaDetails? = null
	private var activeBranchChapters: List<MangaChapter> = emptyList()
	private var activeChapter: MangaChapter? = null
	private var loadJob: Job? = null
	private var chapterLoadJob: Job? = null
	private var autosaveJob: Job? = null
	private var neuralPreloadJob: Job? = null
	private val bookmarkTargetFlow = MutableStateFlow<Triple<Manga, Long, Int>?>(null)
	private var effectiveIncognito: Boolean = extraIncognito || settings.isIncognitoModeEnabled
	private var latestChapterPageIndex: Int = 0
	private var latestChapterEndPageIndex: Int = 0
	private var latestScrollOffset: Int = 0
	private val rawPagesByChapter = java.util.concurrent.ConcurrentHashMap<Long, List<MangaPage>>()
	private val pageBoundsCache = java.util.concurrent.ConcurrentHashMap<Long, Size>()

	init {
		// RAM/GPU Mutex Handoff: release any resident ~1.5 GB LiteRT-LM model immediately on Reader start.
		viewModelScope.launch(Dispatchers.IO) {
			runCatching { localLlmClient.unloadNow() }
		}
		viewModelScope.launch {
			pageUpscaleEngine.activeDelegateLabel.collect { label ->
				_uiState.update { it.copy(activeNeuralDelegate = label) }
			}
		}
		viewModelScope.launch {
			pageUpscaleEngine.cacheBytesFlow.collect { bytes ->
				_uiState.update { it.copy(neuralCacheBytes = bytes) }
			}
		}
		viewModelScope.launch {
			combine(
				upscaleModelManager.downloadState,
				upscaleModelManager.inventoryVersion,
			) { dlState, _ -> dlState }.collect { dlState ->
				refreshNeuralUiState(dlState)
			}
		}
		viewModelScope.launch {
			bookmarkTargetFlow
				.flatMapLatest { target ->
					if (target == null) {
						flowOf(false)
					} else {
						bookmarksRepository.observeBookmark(target.first, target.second, target.third)
							.map { it != null }
							.catch { emit(false) }
					}
				}
				.distinctUntilChanged()
				.collect { saved ->
					_uiState.update { it.copy(isBookmarked = saved) }
				}
		}
		loadReader()
	}

	fun retry() {
		loadReader()
	}

	fun selectChapter(chapterId: Long) {
		val details = activeDetails ?: return
		val chapter = activeBranchChapters.firstOrNull { it.id == chapterId }
			?: details.allChapters.firstOrNull { it.id == chapterId }
			?: return
		loadChapterContent(details, chapter, targetPage = 0, targetScroll = 0)
	}

	fun goToAdjacentChapter(isNext: Boolean) {
		val current = activeChapter ?: return
		val chapters = activeBranchChapters
		val idx = chapters.indexOfFirst { it.id == current.id }
		if (idx < 0) return
		val nextIdx = if (isNext) idx + 1 else idx - 1
		val target = chapters.getOrNull(nextIdx) ?: return
		loadChapterContent(
			details = activeDetails ?: return,
			chapter = target,
			targetPage = 0,
			targetScroll = 0,
		)
	}

	fun setReaderMode(mode: ReaderMode) {
		val manga = _uiState.value.manga ?: return
		val anchorRawIndex = _uiState.value.pages.getOrNull(_uiState.value.currentPageIndex)?.index ?: latestChapterPageIndex
		_uiState.update { it.copy(readerMode = mode) }
		viewModelScope.launch(Dispatchers.IO) {
			mangaDataRepository.saveReaderMode(manga, mode)
			regroupAllLoadedChapters(isWebtoon = mode == ReaderMode.WEBTOON, anchorChapterId = _uiState.value.currentChapterId, anchorRawIndex = anchorRawIndex)
		}
	}

	fun setSeamlessStitchEnabled(enabled: Boolean) {
		settings.isReaderSeamlessStitchEnabled = enabled
		val anchorChapterId = _uiState.value.currentChapterId
		val anchorRawIndex = _uiState.value.pages.getOrNull(_uiState.value.currentPageIndex)?.index ?: latestChapterPageIndex
		_uiState.update { it.copy(isSeamlessStitchEnabled = enabled) }
		viewModelScope.launch(Dispatchers.IO) {
			regroupAllLoadedChapters(
				isWebtoon = _uiState.value.readerMode == ReaderMode.WEBTOON,
				anchorChapterId = anchorChapterId,
				anchorRawIndex = anchorRawIndex,
			)
		}
	}

	fun setTapZoneMode(mode: ReaderTapZoneMode) {
		settings.readerTapZoneMode = mode.key
		_uiState.update { it.copy(tapZoneMode = mode) }
	}

	fun setDoubleTapSmartZoom(enabled: Boolean) {
		settings.isReaderDoubleTapFill = enabled
		_uiState.update { it.copy(isDoubleTapSmartZoom = enabled) }
	}

	fun onMangaPageSelected(globalIndex: Int) {
		val pages = _uiState.value.pages
		val page = pages.getOrNull(globalIndex) ?: return
		val details = activeDetails ?: return
		val chapterPages = pages.filter { it.chapterId == page.chapterId }
		val chFirstGlobal = pages.indexOfFirst { it.chapterId == page.chapterId }.coerceAtLeast(0)
		val rawTotal = rawPagesByChapter[page.chapterId]?.size ?: chapterPages.size
		latestChapterPageIndex = page.index
		latestChapterEndPageIndex = page.endIndex
		latestScrollOffset = 0

		val switchedChapter = page.chapterId != activeChapter?.id
		if (switchedChapter) {
			val ch = activeBranchChapters.firstOrNull { it.id == page.chapterId }
				?: chaptersLoader.peekChapter(page.chapterId)
			if (ch != null) {
				activeChapter = ch
			}
		}

		// Cancel stale neural lookahead jobs behind or far ahead of the current page immediately.
		pageUpscaleEngine.cancelStaleLookahead(
			activeIndex = globalIndex,
			keepRange = (globalIndex - 1)..(globalIndex + 2),
		)

		val chIdx = activeBranchChapters.indexOfFirst { it.id == page.chapterId }
		_uiState.update { state ->
			val chapter = activeChapter
			state.copy(
				currentPageIndex = globalIndex,
				currentChapterId = page.chapterId,
				chapterTitle = chapter?.let { chapterDisplayName(it) } ?: state.chapterTitle,
				chapters = activeBranchChapters.map { c ->
					ReaderChapterItem(c, chapterDisplayName(c), c.id == page.chapterId)
				},
				hasPrevChapter = chIdx > 0,
				hasNextChapter = chIdx in 0 until activeBranchChapters.lastIndex,
				chapterStartPageNumber = (page.index + 1).coerceIn(1, rawTotal.coerceAtLeast(1)),
				chapterEndPageNumber = (page.endIndex + 1).coerceIn(1, rawTotal.coerceAtLeast(1)),
				chapterTotalPages = rawTotal,
				chapterStartGlobalIndex = chFirstGlobal,
				chapterLogicalCount = chapterPages.size,
			)
		}
		observeCurrentBookmark()

		pageLoader.preload(pages, page) {
			chaptersLoader.loadNextChapterPages(details, page.chapterId)
		}
		scheduleNeuralPreloadAhead(pages, globalIndex)

		if (!stateHasChapterAfter(pages, globalIndex) && chIdx in 0 until activeBranchChapters.lastIndex) {
			viewModelScope.launch(Dispatchers.IO) {
				if (chaptersLoader.loadPrevNextChapter(details, page.chapterId, isNext = true)) {
					val rawNext = chaptersLoader.snapshot().distinctBy { it.id }
					val isWebtoonNow = _uiState.value.readerMode == ReaderMode.WEBTOON
					val grouped = buildAllLoadedChaptersGrouped(rawNext, isWebtoonNow)
					_uiState.update { cur ->
						val curChId = cur.currentChapterId
						val curChCount = grouped.count { it.chapterId == curChId }
						val curChStart = grouped.indexOfFirst { it.chapterId == curChId }.coerceAtLeast(0)
						cur.copy(
							pages = grouped,
							chapterStartGlobalIndex = curChStart,
							chapterLogicalCount = curChCount,
						)
					}
				}
			}
		}

		scheduleProgressSave(
			chapterId = page.chapterId,
			page = page.index,
			scroll = 0,
			inChapterFraction = if (rawTotal > 0) (page.endIndex + 1f) / rawTotal else 0f,
		)
		statsCollector.onMangaPageChanged(
			mangaId = details.id,
			chapterId = page.chapterId,
			pageIndex = page.endIndex,
			totalPages = rawTotal.coerceAtLeast(1),
			isIncognito = effectiveIncognito || isPeek,
		)
	}

	fun jumpToMangaPage(globalIndex: Int) {
		val clamped = globalIndex.coerceIn(0, (_uiState.value.pages.size - 1).coerceAtLeast(0))
		_uiState.update { it.copy(pageJumpToken = it.pageJumpToken + 1, currentPageIndex = clamped) }
		onMangaPageSelected(clamped)
	}

	fun onNovelScrollChanged(firstBlockIndex: Int) {
		val chapter = activeChapter ?: return
		val total = _uiState.value.novelBlocks.size.coerceAtLeast(1)
		latestChapterPageIndex = 0
		latestScrollOffset = ReaderState.encodeEpubOffset(firstBlockIndex)
		val fraction = (firstBlockIndex + 1f) / total.toFloat()
		_uiState.update { it.copy(novelCurrentBlockIndex = firstBlockIndex) }
		observeCurrentBookmark()
		scheduleProgressSave(
			chapterId = chapter.id,
			page = 0,
			scroll = latestScrollOffset,
			inChapterFraction = fraction,
		)
		activeDetails?.let { details ->
			statsCollector.onNovelProgressChanged(
				mangaId = details.id,
				chapterId = chapter.id,
				blockIndex = firstBlockIndex,
				totalBlocks = total,
				isIncognito = effectiveIncognito || isPeek,
			)
		}
	}

	fun setShaderPreset(preset: ReaderShaderPreset) {
		settings.readerShaderMode = preset.key
		_uiState.update { it.copy(shaderConfig = it.shaderConfig.copy(preset = preset)) }
	}

	fun setShaderDescreen(enabled: Boolean) {
		settings.isReaderDescreenEnabled = enabled
		_uiState.update { it.copy(shaderConfig = it.shaderConfig.copy(descreen = enabled)) }
	}

	fun setShaderInkLevels(enabled: Boolean) {
		settings.isReaderInkLevelsEnabled = enabled
		_uiState.update { it.copy(shaderConfig = it.shaderConfig.copy(inkLevels = enabled)) }
	}

	fun setShaderStrength(strength: Float) {
		val clamped = strength.coerceIn(0.3f, 1.0f)
		settings.readerShaderStrength = clamped
		_uiState.update { it.copy(shaderConfig = it.shaderConfig.copy(strength = clamped)) }
	}

	fun setNeuralUpscaleEnabled(enabled: Boolean) {
		settings.isNeuralUpscaleEnabled = enabled
		if (enabled && upscaleModelManager.resolveActiveReadyModel() == null) {
			toastManager.show("Download a Neural 2x model below to activate super-resolution")
		}
		_uiState.update {
			it.copy(
				isNeuralUpscaleEnabled = enabled,
				neuralRevision = it.neuralRevision + 1,
			)
		}
	}

	fun setNeuralAccelerator(accelerator: NeuralAccelerator) {
		settings.neuralUpscaleAccelerator = accelerator.key
		refreshNeuralUiState(bumpRevision = false)
	}

	fun selectNeuralModel(modelId: String) {
		settings.neuralUpscaleModelId = modelId
		refreshNeuralUiState(bumpRevision = settings.isNeuralUpscaleEnabled)
	}

	fun downloadNeuralModel(modelId: String) {
		if (upscaleModelManager.downloadState.value.activeModelId != null) return
		viewModelScope.launch(Dispatchers.IO) {
			val res = upscaleModelManager.downloadModel(modelId)
			res.onSuccess {
				if (!settings.isNeuralUpscaleEnabled) {
					settings.isNeuralUpscaleEnabled = true
				}
				refreshNeuralUiState(bumpRevision = true)
				toastManager.show("Neural 2x model ready")
			}.onFailure { err ->
				val msg = err.localizedMessage
				if (!msg.isNullOrBlank() && !msg.contains("cancel", ignoreCase = true)) {
					toastManager.show(msg)
				}
			}
		}
	}

	fun cancelNeuralDownload() {
		upscaleModelManager.cancelDownload()
		toastManager.show("Download cancelled")
	}

	fun deleteNeuralModel(modelId: String) {
		viewModelScope.launch(Dispatchers.IO) {
			if (upscaleModelManager.deleteModel(modelId)) {
				pageUpscaleEngine.releaseSession()
				refreshNeuralUiState(bumpRevision = true)
				toastManager.show("Deleted neural model")
			}
		}
	}

	fun clearNeuralCache() {
		viewModelScope.launch(Dispatchers.IO) {
			pageUpscaleEngine.clearCache()
			refreshNeuralUiState(bumpRevision = settings.isNeuralUpscaleEnabled)
			toastManager.show("Cleared neural upscale cache")
		}
	}

	private fun refreshNeuralUiState(
		dlState: NeuralModelDownloadState = upscaleModelManager.downloadState.value,
		bumpRevision: Boolean = false,
	) {
		val selectedId = settings.neuralUpscaleModelId
		val items = upscaleModelManager.catalog.map { entry ->
			val file = upscaleModelManager.getModelFile(entry.id)
			val bytes = file?.length() ?: entry.approxSizeBytes
			NeuralModelUiItem(
				id = entry.id,
				title = entry.title,
				subtitle = entry.subtitle,
				isDownloadable = entry.downloadUrl != null,
				isDownloaded = file != null,
				isSelected = selectedId == entry.id,
				sizeLabel = formatCompactBytes(bytes),
			)
		}
		val ready = upscaleModelManager.resolveActiveReadyModel()?.first
		val cacheBytes = pageUpscaleEngine.computeCacheSizeBytes()
		_uiState.update { state ->
			state.copy(
				isNeuralUpscaleEnabled = settings.isNeuralUpscaleEnabled,
				neuralAccelerator = NeuralAccelerator.fromKey(settings.neuralUpscaleAccelerator),
				selectedNeuralModelId = selectedId,
				hasReadyNeuralModel = ready != null,
				activeNeuralModelLabel = ready?.title ?: "None downloaded",
				neuralCatalog = items,
				neuralDownloadState = dlState,
				neuralCacheBytes = cacheBytes,
				neuralRevision = if (bumpRevision) state.neuralRevision + 1 else state.neuralRevision,
			)
		}
	}

	private fun scheduleNeuralPreloadAhead(pages: List<ReaderPage>, globalIndex: Int) {
		neuralPreloadJob?.cancel()
		val state = _uiState.value
		if (!pageUpscaleEngine.isSupported ||
			!state.isNeuralUpscaleEnabled ||
			!settings.isNeuralPreloadEnabled ||
			state.readerMode == ReaderMode.WEBTOON ||
			upscaleModelManager.resolveActiveReadyModel() == null
		) {
			return
		}
		val currentChapterId = pages.getOrNull(globalIndex)?.chapterId ?: return
		val nextPages = (1..2).mapNotNull { offset ->
			pages.getOrNull(globalIndex + offset)?.takeIf { it.chapterId == currentChapterId }
		}
		if (nextPages.isEmpty()) return
		neuralPreloadJob = viewModelScope.launch(Dispatchers.IO) {
			for (offset in 1..2) {
				val targetIdx = globalIndex + offset
				val nextPage = pages.getOrNull(targetIdx) ?: continue
				if (nextPage.chapterId != currentChapterId) continue
				runCatchingCancellable {
					val (resolvedUri, resolvedBounds) = resolveLogicalPageUri(nextPage, force = false)
					pageUpscaleEngine.upscalePageIfEnabled(
						sourceUri = resolvedUri,
						cropBounds = resolvedBounds,
						isWebtoon = false,
						pageIndex = targetIdx,
					)
				}
			}
		}
	}

	private fun formatCompactBytes(bytes: Long): String {
		if (bytes <= 0L) return "Custom"
		val mb = bytes.toFloat() / (1024f * 1024f)
		return if (mb >= 10f) "${mb.toInt()} MB" else String.format("%.1f MB", mb)
	}

	fun onPauseReading() {
		autosaveJob?.cancel()
		val mangaId = activeDetails?.id
		viewModelScope.launch(Dispatchers.IO + NonCancellable) {
			persistProgressNow()
			if (mangaId != null) {
				statsCollector.flushSessionNow(mangaId)
			}
		}
	}

	fun releaseUpscalerOnClose() {
		neuralPreloadJob?.cancel()
		CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
			pageUpscaleEngine.releaseSession()
		}
	}

	override fun onCleared() {
		neuralPreloadJob?.cancel()
		activeDetails?.id?.let { statsCollector.onPauseOrClose(it) }
		CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
			pageUpscaleEngine.releaseSession()
		}
		super.onCleared()
	}

	fun jumpToNovelBlock(blockIndex: Int) {
		val clamped = blockIndex.coerceIn(0, (_uiState.value.novelBlocks.size - 1).coerceAtLeast(0))
		_uiState.update {
			it.copy(
				novelInitialBlockIndex = clamped,
				novelCurrentBlockIndex = clamped,
				novelScrollToken = it.novelScrollToken + 1,
			)
		}
		onNovelScrollChanged(clamped)
	}

	fun setEpubTheme(theme: String) {
		settings.epubTheme = theme
		_uiState.update { it.copy(epubTheme = theme) }
	}

	fun setEpubFontSize(size: Int) {
		val clamped = size.coerceIn(70, 200)
		settings.epubFontSize = clamped
		_uiState.update { it.copy(epubFontSize = clamped) }
	}

	fun setEpubLineHeight(height: Int) {
		val clamped = height.coerceIn(110, 230)
		settings.epubLineHeight = clamped
		_uiState.update { it.copy(epubLineHeight = clamped) }
	}

	fun setEpubFontFamily(family: String) {
		settings.epubFontFamily = family
		_uiState.update { it.copy(epubFontFamily = family) }
	}

	fun setEpubHorizontalPadding(paddingDp: Int) {
		val clamped = paddingDp.coerceIn(8, 56)
		settings.epubHorizontalPadding = clamped
		_uiState.update { it.copy(epubHorizontalPadding = clamped) }
	}

	fun toggleBookmark() {
		val state = _uiState.value
		val manga = state.manga ?: return
		val chapterId = state.currentChapterId
		if (chapterId == 0L) return

		val pageNum = if (state.isTextMode) state.novelCurrentBlockIndex else latestChapterPageIndex
		val pageObj = state.pages.getOrNull(state.currentPageIndex)
		val chapterIdx = activeBranchChapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
		val percent = if (activeBranchChapters.isNotEmpty()) {
			((chapterIdx + 0.5f) / activeBranchChapters.size).coerceIn(0f, 1f)
		} else {
			0f
		}

		viewModelScope.launch(Dispatchers.IO) {
			if (state.isBookmarked) {
				bookmarksRepository.removeBookmark(manga.id, chapterId, pageNum)
				toastManager.show("Bookmark removed")
			} else {
				val bookmark = Bookmark(
					manga = manga,
					pageId = pageObj?.id ?: (chapterId xor pageNum.toLong()),
					chapterId = chapterId,
					page = pageNum,
					scroll = latestScrollOffset,
					imageUrl = pageObj?.preview ?: pageObj?.url ?: manga.coverUrl.orEmpty(),
					createdAt = Instant.now(),
					percent = percent,
				)
				bookmarksRepository.addBookmark(bookmark)
				toastManager.show("Bookmark saved")
			}
		}
	}

	suspend fun loadRawPageImage(page: ReaderPage): Result<Pair<Uri, Rect?>> =
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				resolveLogicalPageUri(page, force = false)
			}
		}

	suspend fun loadPageImage(page: ReaderPage, force: Boolean = false): Result<Pair<Uri, Rect?>> =
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val (uri, bounds) = resolveLogicalPageUri(page, force = force)
				val isWebtoon = _uiState.value.readerMode == ReaderMode.WEBTOON
				val pageIdx = _uiState.value.pages.indexOfFirst { it.id == page.id }.let {
					if (it >= 0) it else page.index
				}
				val result = pageUpscaleEngine.upscalePageIfEnabled(uri, bounds, isWebtoon, pageIndex = pageIdx)
				_uiState.update { it.copy(neuralCacheBytes = pageUpscaleEngine.computeCacheSizeBytes()) }
				result
			}.onFailure { err ->
				when (err) {
					is CloudFlareProtectedException -> _uiState.update { it.copy(pendingCloudFlare = err) }
					is InteractiveActionRequiredException -> _uiState.update { it.copy(pendingInteractive = err) }
				}
			}
		}

	private suspend fun resolveLogicalPageUri(
		page: ReaderPage,
		force: Boolean,
	): Pair<Uri, Rect?> {
		if (page.isStitched && settings.isReaderSeamlessStitchEnabled) {
			val sliceUris = page.stitchedPages.map { slice ->
				val u = pageLoader.loadPage(slice, force)
				pageLoader.getImageBounds(u)?.let { r ->
					pageBoundsCache[slice.id] = Size(r.width(), r.height())
				}
				u
			}
			val stitched = pageStitcher.compositeSlices(
				sliceUris = sliceUris,
				cacheKeySeed = "${page.chapterId}_${page.index}_${page.endIndex}",
			)
			if (stitched != null) {
				val (stitchedUri, size) = stitched
				return stitchedUri to Rect(0, 0, size.width, size.height)
			}
		}
		val uri = pageLoader.loadPage(page.toMangaPage(), force)
		val bounds = pageLoader.getImageBounds(uri)
		if (bounds != null && !bounds.isEmpty) {
			val size = Size(bounds.width(), bounds.height())
			val prevSize = pageBoundsCache.put(page.id, size)
			if (prevSize == null && settings.isReaderSeamlessStitchEnabled) {
				maybeStitchDiscoveredSliver(page.chapterId, size)
			}
		}
		return uri to bounds
	}

	private fun maybeStitchDiscoveredSliver(chapterId: Long, newlyDiscoveredSize: Size) {
		val isSliverOrSplit = newlyDiscoveredSize.height < newlyDiscoveredSize.width * 1.15f
		if (!isSliverOrSplit) return
		val rawList = rawPagesByChapter[chapterId] ?: return
		// Re-group asynchronously only when all adjacent slices around a sliver now have cached bounds
		viewModelScope.launch(Dispatchers.IO) {
			val isWebtoonNow = _uiState.value.readerMode == ReaderMode.WEBTOON
			val newChapterPages = groupChapterFromCachedBounds(chapterId, rawList, isWebtoonNow)
			val currentPages = _uiState.value.pages
			val currentChapterSlice = currentPages.filter { it.chapterId == chapterId }
			if (newChapterPages.size < currentChapterSlice.size) {
				val anchorCh = _uiState.value.currentChapterId
				val anchorRaw = _uiState.value.pages.getOrNull(_uiState.value.currentPageIndex)?.index ?: latestChapterPageIndex
				regroupAllLoadedChapters(isWebtoonNow, anchorCh, anchorRaw)
			}
		}
	}

	private suspend fun groupChapterFromCachedBounds(
		chapterId: Long,
		rawPages: List<MangaPage>,
		isWebtoon: Boolean,
	): List<ReaderPage> {
		if (!settings.isReaderSeamlessStitchEnabled || rawPages.size <= 1) {
			return rawPages.mapIndexed { idx, p -> ReaderPage(p, idx, chapterId) }
		}
		return pageStitcher.groupChapterSlices(
			chapterId = chapterId,
			rawPages = rawPages,
			isWebtoon = isWebtoon,
		) { slice ->
			pageBoundsCache[slice.id]?.let { return@groupChapterSlices it }
			val peeked = pageLoader.peekLocalOrCachedUri(slice) ?: return@groupChapterSlices null
			val r = pageLoader.getImageBounds(peeked) ?: return@groupChapterSlices null
			Size(r.width(), r.height()).also { pageBoundsCache[slice.id] = it }
		}
	}

	private suspend fun buildAllLoadedChaptersGrouped(
		rawSnapshot: List<ReaderPage>,
		isWebtoon: Boolean,
	): List<ReaderPage> {
		val result = ArrayList<ReaderPage>(rawSnapshot.size)
		val byChapter = rawSnapshot.groupBy { it.chapterId }
		for ((chId, pages) in byChapter) {
			val rawList = rawPagesByChapter.getOrPut(chId) { pages.map { it.toMangaPage() } }
			result += groupChapterFromCachedBounds(chId, rawList, isWebtoon)
		}
		return result
	}

	private suspend fun regroupAllLoadedChapters(
		isWebtoon: Boolean,
		anchorChapterId: Long,
		anchorRawIndex: Int,
	) {
		val snapshot = chaptersLoader.snapshot().distinctBy { it.id }
		if (snapshot.isEmpty()) return
		val grouped = buildAllLoadedChaptersGrouped(snapshot, isWebtoon)
		val newGlobalIdx = grouped.indexOfFirst {
			it.chapterId == anchorChapterId && it.containsRawIndex(anchorRawIndex)
		}.let { found ->
			if (found >= 0) found else grouped.indexOfFirst { it.chapterId == anchorChapterId }.coerceAtLeast(0)
		}
		val activePage = grouped.getOrNull(newGlobalIdx)
		val rawTotal = rawPagesByChapter[anchorChapterId]?.size ?: grouped.count { it.chapterId == anchorChapterId }
		val chStartGlobal = grouped.indexOfFirst { it.chapterId == anchorChapterId }.coerceAtLeast(0)
		val chLogicalCount = grouped.count { it.chapterId == anchorChapterId }
		latestChapterPageIndex = activePage?.index ?: anchorRawIndex
		latestChapterEndPageIndex = activePage?.endIndex ?: anchorRawIndex
		_uiState.update { state ->
			state.copy(
				pages = grouped,
				currentPageIndex = newGlobalIdx,
				pageJumpToken = state.pageJumpToken + 1,
				chapterStartPageNumber = ((activePage?.index ?: 0) + 1).coerceIn(1, rawTotal.coerceAtLeast(1)),
				chapterEndPageNumber = ((activePage?.endIndex ?: 0) + 1).coerceIn(1, rawTotal.coerceAtLeast(1)),
				chapterTotalPages = rawTotal,
				chapterStartGlobalIndex = chStartGlobal,
				chapterLogicalCount = chLogicalCount,
			)
		}
	}

	fun flushProgress() {
		viewModelScope.launch(Dispatchers.IO + NonCancellable) {
			persistProgressNow()
		}
	}

	private suspend fun persistProgressNow() {
		val state = _uiState.value
		val manga = activeDetails?.sourceManga ?: state.manga ?: return
		val chapterId = state.currentChapterId
		if (chapterId == 0L || effectiveIncognito || isPeek) return
		val fraction = if (state.isTextMode) {
			val total = state.novelBlocks.size.coerceAtLeast(1)
			(state.novelCurrentBlockIndex + 1f) / total
		} else {
			val rawTotal = rawPagesByChapter[chapterId]?.size ?: state.pages.count { it.chapterId == chapterId }
			if (rawTotal > 0) (latestChapterEndPageIndex + 1f) / rawTotal else 0f
		}
		val percent = computeOverallPercent(chapterId, fraction)
		runCatching {
			historyRepository.addOrUpdate(
				manga = manga,
				chapterId = chapterId,
				page = latestChapterPageIndex,
				scroll = latestScrollOffset,
				percent = percent,
				force = false,
			)
		}
	}

	private fun loadReader() {
		loadJob?.cancel()
		val cachedManga = rawIntent.manga ?: MangaTransferCache.get(rawIntent.mangaId)
		val resolvedHandle = SavedStateHandle(
			buildMap {
				put(AppRouter.KEY_ID, rawIntent.mangaId)
				if (cachedManga != null) {
					put(AppRouter.KEY_MANGA, ParcelableManga(cachedManga))
				}
			},
		)
		val intent = MangaIntent(resolvedHandle)

		loadJob = viewModelScope.launch(Dispatchers.IO) {
			_uiState.update {
				it.copy(
					isLoading = true,
					errorMessage = null,
					pendingCloudFlare = null,
					pendingInteractive = null,
					pendingChallengeUrl = null,
				)
			}

			try {
				var initializedChapter = false
				detailsLoadUseCase.invoke(intent, force = false).collect { rawLoaded ->
					val sortOrder = settings.getChaptersSortOrder(rawLoaded.id)
					val loaded = rawLoaded.withChaptersOrder(sortOrder)
					activeDetails = loaded
					val manga = loaded.toManga()
					MangaTransferCache.put(manga)
					effectiveIncognito = extraIncognito || settings.isIncognitoModeEnabled(manga.isNsfw())

					val history = runCatchingCancellable { historyRepository.getOne(manga) }.getOrNull()
					val branch = requestedBranch?.takeIf { loaded.chapters.containsKey(it) }
						?: manga.getPreferredBranch(history).takeIf { loaded.chapters.containsKey(it) }
						?: loaded.chapters.keys.firstOrNull()
					val branchChapters = (loaded.chapters[branch] ?: loaded.allChapters).distinctBy { it.id }
					activeBranchChapters = branchChapters

					if (!initializedChapter && branchChapters.isNotEmpty()) {
						initializedChapter = true
						val targetChapter = initialState?.chapterId?.let { id ->
							branchChapters.firstOrNull { it.id == id } ?: loaded.allChapters.firstOrNull { it.id == id }
						} ?: history?.chapterId?.let { id ->
							branchChapters.firstOrNull { it.id == id }
						} ?: branchChapters.first()

						val targetPage = initialState?.page ?: history?.takeIf { it.chapterId == targetChapter.id }?.page ?: 0
						val targetScroll = initialState?.scroll ?: history?.takeIf { it.chapterId == targetChapter.id }?.scroll ?: 0

						loadChapterContent(
							details = loaded,
							chapter = targetChapter,
							targetPage = targetPage,
							targetScroll = targetScroll,
						)
					} else if (branchChapters.isEmpty() && loaded.isLoaded) {
						_uiState.update {
							it.copy(
								manga = manga,
								details = loaded,
								title = manga.title,
								isIncognito = effectiveIncognito,
								isLoading = false,
								errorMessage = "No chapters available to read.",
							)
						}
					}
				}
			} catch (e: CloudFlareProtectedException) {
				_uiState.update {
					it.copy(
						isLoading = false,
						pendingCloudFlare = e,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			} catch (e: InteractiveActionRequiredException) {
				_uiState.update {
					it.copy(
						isLoading = false,
						pendingInteractive = e,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			} catch (e: Throwable) {
				val challenge = e.toWebViewChallengeAction(
					fallbackUrl = cachedManga?.publicUrl ?: activeDetails?.sourceManga?.publicUrl,
					fallbackSource = cachedManga?.source ?: activeDetails?.sourceManga?.source ?: org.koitharu.kotatsu.core.model.UnknownMangaSource,
					title = cachedManga?.title ?: activeDetails?.toManga()?.title,
				)
				_uiState.update {
					it.copy(
						isLoading = false,
						pendingCloudFlare = (challenge as? WebViewChallengeAction.CloudFlare)?.exception,
						pendingInteractive = (challenge as? WebViewChallengeAction.Interactive)?.exception,
						pendingChallengeUrl = (challenge as? WebViewChallengeAction.BrowserUrl)?.url,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			}
		}
	}

	private fun loadChapterContent(
		details: MangaDetails,
		chapter: MangaChapter,
		targetPage: Int,
		targetScroll: Int,
	) {
		chapterLoadJob?.cancel()
		activeChapter = chapter
		val manga = details.toManga()
		val isTextMode = manga.isEpub ||
			manga.source.isNovelSource ||
			chapter.url.substringBefore('#').endsWith(".epub", ignoreCase = true)

		chapterLoadJob = viewModelScope.launch(Dispatchers.IO) {
			val chIdx = activeBranchChapters.indexOfFirst { it.id == chapter.id }
			_uiState.update {
				it.copy(
					manga = manga,
					details = details,
					title = manga.title,
					chapterTitle = chapterDisplayName(chapter),
					currentChapterId = chapter.id,
					chapters = activeBranchChapters.map { c ->
						ReaderChapterItem(c, chapterDisplayName(c), c.id == chapter.id)
					},
					isTextMode = isTextMode,
					hasPrevChapter = chIdx > 0,
					hasNextChapter = chIdx in 0 until activeBranchChapters.lastIndex,
					isIncognito = effectiveIncognito,
					isChapterLoading = true,
					errorMessage = null,
					pendingCloudFlare = null,
					pendingInteractive = null,
					pendingChallengeUrl = null,
				)
			}

			try {
				if (isTextMode) {
					val rawHtml = loadNovelChapterHtml(chapter)
					val blocks = parseHtmlToBlocks(rawHtml, chapter.url)
					val decodedOffset = ReaderState.decodeEpubOffset(targetScroll)
					val restoredIdx = when {
						decodedOffset != null -> decodedOffset.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
						targetScroll > 0 && blocks.size > 1 -> ((targetScroll / 1000f) * blocks.lastIndex).toInt().coerceIn(0, blocks.lastIndex)
						else -> targetPage.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
					}
					latestChapterPageIndex = 0
					latestScrollOffset = ReaderState.encodeEpubOffset(restoredIdx)

					_uiState.update {
						it.copy(
							novelBlocks = blocks,
							novelInitialBlockIndex = restoredIdx,
							novelCurrentBlockIndex = restoredIdx,
							novelScrollToken = it.novelScrollToken + 1,
							isLoading = false,
							isChapterLoading = false,
						)
					}
					observeCurrentBookmark()
				} else {
					val resolvedMode = mangaDataRepository.getReaderMode(manga.id)
						?: detectReaderModeUseCase(manga, initialState)
					chaptersLoader.init(details)
					val loadedOk = chaptersLoader.loadSingleChapter(chapter.id)
					val rawSnapshot = chaptersLoader.snapshot().distinctBy { it.id }
					if (!loadedOk || rawSnapshot.isEmpty()) {
						error("This chapter has no readable pages.")
					}
					val rawChapterList = rawSnapshot.filter { it.chapterId == chapter.id }.map { it.toMangaPage() }
					rawPagesByChapter[chapter.id] = rawChapterList
					val snapshot = buildAllLoadedChaptersGrouped(rawSnapshot, isWebtoon = resolvedMode == ReaderMode.WEBTOON)

					val firstIndexOfChapter = snapshot.indexOfFirst { it.chapterId == chapter.id }.coerceAtLeast(0)
					val chapterLogicalCount = snapshot.count { it.chapterId == chapter.id }.coerceAtLeast(1)
					val rawTotal = rawChapterList.size.coerceAtLeast(1)
					val clampedRawTarget = targetPage.coerceIn(0, rawTotal - 1)
					val globalIndex = snapshot.indexOfFirst {
						it.chapterId == chapter.id && it.containsRawIndex(clampedRawTarget)
					}.let { found ->
						if (found >= 0) found else firstIndexOfChapter
					}.coerceIn(0, snapshot.lastIndex)
					val activePage = snapshot[globalIndex]
					latestChapterPageIndex = activePage.index
					latestChapterEndPageIndex = activePage.endIndex
					latestScrollOffset = targetScroll

					_uiState.update {
						it.copy(
							readerMode = resolvedMode,
							pages = snapshot,
							currentPageIndex = globalIndex,
							pageJumpToken = it.pageJumpToken + 1,
							chapterStartPageNumber = (activePage.index + 1).coerceIn(1, rawTotal),
							chapterEndPageNumber = (activePage.endIndex + 1).coerceIn(1, rawTotal),
							chapterTotalPages = rawTotal,
							chapterStartGlobalIndex = firstIndexOfChapter,
							chapterLogicalCount = chapterLogicalCount,
							isLoading = false,
							isChapterLoading = false,
						)
					}
					snapshot.getOrNull(globalIndex)?.let { current ->
						pageLoader.preload(snapshot, current) {
							chaptersLoader.loadNextChapterPages(details, current.chapterId)
						}
					}
					observeCurrentBookmark()
				}
			} catch (e: CloudFlareProtectedException) {
				_uiState.update {
					it.copy(
						isLoading = false,
						isChapterLoading = false,
						pendingCloudFlare = e,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			} catch (e: InteractiveActionRequiredException) {
				_uiState.update {
					it.copy(
						isLoading = false,
						isChapterLoading = false,
						pendingInteractive = e,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			} catch (e: Throwable) {
				val challenge = e.toWebViewChallengeAction(
					fallbackUrl = manga.publicUrl,
					fallbackSource = manga.source,
					title = manga.title,
				)
				_uiState.update {
					it.copy(
						isLoading = false,
						isChapterLoading = false,
						pendingCloudFlare = (challenge as? WebViewChallengeAction.CloudFlare)?.exception,
						pendingInteractive = (challenge as? WebViewChallengeAction.Interactive)?.exception,
						pendingChallengeUrl = (challenge as? WebViewChallengeAction.BrowserUrl)?.url,
						errorMessage = e.getDisplayMessage(context.resources),
					)
				}
			}
		}
	}

	private fun observeCurrentBookmark() {
		val state = _uiState.value
		val manga = state.manga ?: return
		val chapterId = state.currentChapterId
		if (chapterId == 0L) return
		val pageNum = if (state.isTextMode) state.novelCurrentBlockIndex else latestChapterPageIndex
		bookmarkTargetFlow.value = Triple(manga, chapterId, pageNum)
	}

	private fun scheduleProgressSave(
		chapterId: Long,
		page: Int,
		scroll: Int,
		inChapterFraction: Float,
	) {
		if (effectiveIncognito || isPeek) return
		val manga = activeDetails?.sourceManga ?: _uiState.value.manga ?: return
		val percent = computeOverallPercent(chapterId, inChapterFraction)
		autosaveJob?.cancel()
		autosaveJob = viewModelScope.launch(Dispatchers.IO) {
			delay(900L)
			runCatching {
				historyRepository.addOrUpdate(
					manga = manga,
					chapterId = chapterId,
					page = page,
					scroll = scroll,
					percent = percent,
					force = false,
				)
			}
		}
	}

	private fun computeOverallPercent(chapterId: Long, inChapterFraction: Float): Float {
		val chapters = activeBranchChapters
		if (chapters.isEmpty()) return inChapterFraction.coerceIn(0f, 1f)
		val idx = chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
		return ((idx + inChapterFraction.coerceIn(0f, 1f)) / chapters.size.toFloat()).coerceIn(0f, 1f)
	}

	private suspend fun loadNovelChapterHtml(chapter: MangaChapter): String = runInterruptible(Dispatchers.IO) {
		val url = chapter.url
		val uri = url.toUri()
		if (uri.isZipUri() || url.substringBefore('#').endsWith(".epub", ignoreCase = true)) {
			val zipPath = uri.path?.takeIf { it.isNotBlank() }
				?: uri.schemeSpecificPart.substringBefore('#').removePrefix("//")
			val entryName = uri.fragment.orEmpty().removePrefix("/")
			if (zipPath.isNotBlank() && entryName.isNotBlank()) {
				ZipFile(File(zipPath)).use { zip ->
					val entry = zip.getEntry(entryName)
						?: zip.entries().asSequence().firstOrNull { it.name.equals(entryName, ignoreCase = true) }
					if (entry != null) {
						return@runInterruptible zip.getInputStream(entry).bufferedReader().use { it.readText() }
					}
				}
			}
		}
		""
	}.ifEmpty {
		mangaRepositoryFactory.create(chapter.source).getChapterHtml(chapter).orEmpty()
	}

	private fun parseHtmlToBlocks(rawHtml: String, chapterUrl: String): List<NovelBlock> {
		if (rawHtml.isBlank()) {
			return listOf(NovelBlock.Paragraph("empty", "Chapter content is empty."))
		}
		val cleaned = rawHtml
			.replace(Regex("(?is)<!--.*?-->"), "")
			.replace(Regex("(?is)<(head|style|script|title)[^>]*>.*?</\\1>"), "")

		val body = Regex("(?is)<body[^>]*>(.*?)</body>").find(cleaned)?.groupValues?.get(1) ?: cleaned

		val baseZipUrl = chapterUrl.toUri().takeIf {
			it.isZipUri() || chapterUrl.substringBefore('#').endsWith(".epub", ignoreCase = true)
		}
		val zipPath = baseZipUrl?.let {
			it.path?.takeIf { p -> p.isNotBlank() } ?: it.schemeSpecificPart.substringBefore('#').removePrefix("//")
		}
		val entryDir = baseZipUrl?.fragment?.removePrefix("/")?.substringBeforeLast('/', "")

		val result = ArrayList<NovelBlock>()
		val blockPattern = Regex(
			"(?is)<(h[1-6]|p|blockquote|li|pre|div)[^>]*>(.*?)</\\1>|<hr[^>]*/?>|<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>|<image[^>]+(?:xlink:href|href)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
		)

		var lastIndex = 0
		var seq = 0
		for (match in blockPattern.findAll(body)) {
			val prefixRaw = body.substring(lastIndex, match.range.first)
			val prefixText = htmlToPlain(prefixRaw)
			if (prefixText.isNotBlank()) {
				prefixText.split(Regex("\\n{2,}")).forEach { part ->
					val trimmed = part.trim()
					if (trimmed.isNotEmpty()) {
						result.add(NovelBlock.Paragraph("p_${seq++}", trimmed))
					}
				}
			}
			lastIndex = match.range.last + 1

			val whole = match.value.lowercase()
			when {
				whole.startsWith("<hr") -> result.add(NovelBlock.Divider("hr_${seq++}"))
				whole.startsWith("<img") || whole.startsWith("<image") -> {
					val src = (match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
						?: match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() })
					if (!src.isNullOrBlank()) {
						val resolvedSrc = resolveAssetUrl(src, zipPath, entryDir)
						if (resolvedSrc != null) {
							result.add(NovelBlock.Image("img_${seq++}", resolvedSrc))
						}
					}
				}
				else -> {
					val tag = match.groupValues[1].lowercase()
					val inner = match.groupValues[2]
					// Also extract nested <img> inside <p> / <div>
					val nestedImg = Regex("(?is)<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']").find(inner)
					if (nestedImg != null) {
						val resolved = resolveAssetUrl(nestedImg.groupValues[1], zipPath, entryDir)
						if (resolved != null) {
							result.add(NovelBlock.Image("img_${seq++}", resolved))
						}
					}
					val text = htmlToPlain(inner)
					if (text.isNotBlank()) {
						if (tag.startsWith("h")) {
							val level = tag.drop(1).toIntOrNull()?.coerceIn(1, 6) ?: 2
							result.add(NovelBlock.Heading("h_${seq++}", text, level))
						} else {
							result.add(NovelBlock.Paragraph("p_${seq++}", text))
						}
					}
				}
			}
		}

		if (lastIndex < body.length) {
			val tailText = htmlToPlain(body.substring(lastIndex))
			if (tailText.isNotBlank()) {
				tailText.split(Regex("\\n{2,}")).forEach { part ->
					val trimmed = part.trim()
					if (trimmed.isNotEmpty()) {
						result.add(NovelBlock.Paragraph("p_${seq++}", trimmed))
					}
				}
			}
		}

		return if (result.isEmpty()) {
			val plain = htmlToPlain(body)
			if (plain.isBlank()) {
				listOf(NovelBlock.Paragraph("empty", "Chapter has no readable text."))
			} else {
				plain.split(Regex("\\n{2,}")).mapIndexed { i, p -> NovelBlock.Paragraph("p_$i", p.trim()) }
			}
		} else {
			result
		}
	}

	private fun htmlToPlain(fragment: String): String {
		if (fragment.isBlank()) return ""
		return HtmlCompat.fromHtml(fragment, HtmlCompat.FROM_HTML_MODE_COMPACT)
			.toString()
			.replace('\u00A0', ' ')
			.replace(Regex("[ \t]+\\n"), "\n")
			.replace(Regex("\\n{3,}"), "\n\n")
			.trim()
	}

	private fun resolveAssetUrl(src: String, zipPath: String?, entryDir: String?): String? {
		if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
			return src
		}
		if (zipPath == null) return null
		val segments = ArrayList<String>()
		if (!entryDir.isNullOrEmpty() && !src.startsWith('/')) {
			segments.addAll(entryDir.split('/'))
		}
		src.substringBefore('#').trimStart('/').split('/').forEach { part ->
			when (part) {
				"", "." -> Unit
				".." -> segments.removeLastOrNull()
				else -> segments.add(part)
			}
		}
		val resolvedPath = segments.joinToString("/")
		return if (resolvedPath.isNotEmpty()) "file+zip://$zipPath#$resolvedPath" else null
	}

	private fun stateHasChapterAfter(pages: List<ReaderPage>, index: Int): Boolean {
		val currentId = pages.getOrNull(index)?.chapterId ?: return false
		return pages.drop(index + 1).any { it.chapterId != currentId }
	}

	private fun chapterDisplayName(chapter: MangaChapter): String =
		chapter.getLocalizedTitle(context.resources).ifBlank {
			chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.number.toInt()}"
		}
}
