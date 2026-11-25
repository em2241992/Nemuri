package org.nemuri.app.reader

import android.content.Context
import android.graphics.Rect
import android.net.Uri
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
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isZipUri
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.domain.DetailsLoadUseCase
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.domain.ChaptersLoader
import org.koitharu.kotatsu.reader.domain.DetectReaderModeUseCase
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.NeuralModelDownloadState
import org.koitharu.kotatsu.reader.domain.NeuralUpscaleModelManager
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.koitharu.kotatsu.stats.domain.StatsCollector
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
	val neuralCatalog: List<NeuralModelUiItem> = emptyList(),
	val neuralDownloadState: NeuralModelDownloadState = NeuralModelDownloadState(),
	val neuralCacheBytes: Long = 0L,
	val neuralRevision: Int = 0,
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
	private val pageUpscaleEngine: PageUpscaleEngine,
	private val upscaleModelManager: NeuralUpscaleModelManager,
	private val detectReaderModeUseCase: DetectReaderModeUseCase,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val statsCollector: StatsCollector,
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
	private var latestScrollOffset: Int = 0

	init {
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
		_uiState.update { it.copy(readerMode = mode) }
		viewModelScope.launch(Dispatchers.IO) {
			mangaDataRepository.saveReaderMode(manga, mode)
		}
	}

	fun onMangaPageSelected(globalIndex: Int) {
		val pages = _uiState.value.pages
		val page = pages.getOrNull(globalIndex) ?: return
		val details = activeDetails ?: return
		val chapterPages = pages.filter { it.chapterId == page.chapterId }
		val inChapterIdx = chapterPages.indexOfFirst { it.id == page.id }.coerceAtLeast(0)
		latestChapterPageIndex = inChapterIdx
		latestScrollOffset = 0

		val switchedChapter = page.chapterId != activeChapter?.id
		if (switchedChapter) {
			val ch = activeBranchChapters.firstOrNull { it.id == page.chapterId }
				?: chaptersLoader.peekChapter(page.chapterId)
			if (ch != null) {
				activeChapter = ch
			}
		}

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
					val updated = chaptersLoader.snapshot()
					_uiState.update { it.copy(pages = updated) }
				}
			}
		}

		scheduleProgressSave(
			chapterId = page.chapterId,
			page = inChapterIdx,
			scroll = 0,
			inChapterFraction = if (chapterPages.isNotEmpty()) (inChapterIdx + 1f) / chapterPages.size else 0f,
		)
		statsCollector.onMangaPageChanged(
			mangaId = details.id,
			chapterId = page.chapterId,
			pageIndex = inChapterIdx,
			totalPages = chapterPages.size,
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
			for (nextPage in nextPages) {
				runCatchingCancellable {
					val uri = pageLoader.loadPage(nextPage.toMangaPage(), false)
					val bounds = pageLoader.getImageBounds(uri)
					pageUpscaleEngine.upscalePageIfEnabled(uri, bounds, isWebtoon = false)
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
				val uri = pageLoader.loadPage(page.toMangaPage(), false)
				val bounds = pageLoader.getImageBounds(uri)
				uri to bounds
			}
		}

	suspend fun loadPageImage(page: ReaderPage, force: Boolean = false): Result<Pair<Uri, Rect?>> =
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val uri = pageLoader.loadPage(page.toMangaPage(), force)
				val bounds = pageLoader.getImageBounds(uri)
				val isWebtoon = _uiState.value.readerMode == ReaderMode.WEBTOON
				val result = pageUpscaleEngine.upscalePageIfEnabled(uri, bounds, isWebtoon)
				_uiState.update { it.copy(neuralCacheBytes = pageUpscaleEngine.computeCacheSizeBytes()) }
				result
			}.onFailure { err ->
				when (err) {
					is CloudFlareProtectedException -> _uiState.update { it.copy(pendingCloudFlare = err) }
					is InteractiveActionRequiredException -> _uiState.update { it.copy(pendingInteractive = err) }
				}
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
			val chPages = state.pages.filter { it.chapterId == chapterId }
			if (chPages.isNotEmpty()) (latestChapterPageIndex + 1f) / chPages.size else 0f
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
					val branchChapters = loaded.chapters[branch] ?: loaded.allChapters
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
				_uiState.update {
					it.copy(
						isLoading = false,
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
					val snapshot = chaptersLoader.snapshot()
					if (!loadedOk || snapshot.isEmpty()) {
						error("This chapter has no readable pages.")
					}
					val firstIndexOfChapter = snapshot.indexOfFirst { it.chapterId == chapter.id }.coerceAtLeast(0)
					val chapterPagesCount = snapshot.count { it.chapterId == chapter.id }.coerceAtLeast(1)
					val clampedInChapter = targetPage.coerceIn(0, chapterPagesCount - 1)
					val globalIndex = (firstIndexOfChapter + clampedInChapter).coerceIn(0, snapshot.lastIndex)
					latestChapterPageIndex = clampedInChapter
					latestScrollOffset = targetScroll

					_uiState.update {
						it.copy(
							readerMode = resolvedMode,
							pages = snapshot,
							currentPageIndex = globalIndex,
							pageJumpToken = it.pageJumpToken + 1,
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
				_uiState.update {
					it.copy(
						isLoading = false,
						isChapterLoading = false,
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
