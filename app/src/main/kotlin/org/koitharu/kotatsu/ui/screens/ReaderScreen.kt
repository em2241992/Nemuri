package org.koitharu.kotatsu.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.NekoBackgroundColorMode
import org.koitharu.kotatsu.core.model.NekoRotationMode
import org.koitharu.kotatsu.core.model.NekoWideSpreadMode
import org.koitharu.kotatsu.core.model.ReaderColorFilterPreset
import org.koitharu.kotatsu.core.model.ReaderCustomizationConfig
import org.koitharu.kotatsu.core.model.ReaderScaleMode
import org.koitharu.kotatsu.core.model.ReaderStyleMode
import org.koitharu.kotatsu.core.model.SliderPosition
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.reader.domain.WidePageProcessor
import org.koitharu.kotatsu.ui.components.MangaReaderPageCanvas
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class NekoBottomSheetTab(val title: String) {
	CHAPTERS("Chapters"),
	AI_TOOLS("AI & Tools"),
	SETTINGS("Settings"),
}

private enum class NekoSettingsSubTab(val title: String) {
	GENERAL("General"),
	PAGED("Paged"),
	FILTER("Filter"),
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
	mangaItem: LibraryMangaItem,
	chapterIndex: Int,
	pageIndex: Int,
	readerConfig: ReaderCustomizationConfig,
	isPageBookmarked: Boolean,
	hasCustomSeriesProfile: Boolean,
	onPageChange: (Int) -> Unit,
	onChapterChange: (Int) -> Unit,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onTogglePageBookmark: () -> Unit,
	onSaveSeriesProfile: () -> Unit,
	onCloseReader: () -> Unit,
	onJumpToHome: () -> Unit = onCloseReader,
	volumeKeyEvents: Flow<Int> = emptyFlow(),
	isIncognitoSession: Boolean = false,
) {
	val totalPages = 18
	val totalChapters = mangaItem.totalChapters.coerceAtLeast(1)

	// Start in clean Fullscreen Reading State (isHudVisible == false) or collapse on page turn / scroll
	var isHudVisible by remember { mutableStateOf(false) }
	var expandedSheetTab by remember { mutableStateOf<NekoBottomSheetTab?>(null) }
	var settingsSubTab by remember { mutableStateOf(NekoSettingsSubTab.GENERAL) }
	var showAiUpscalePreviewModal by remember { mutableStateOf(false) }
	var showReadingMoreSheet by remember { mutableStateOf(false) }
	var activeReadingMoreTab by remember { mutableStateOf(ReaderOptionsSheetTab.READING) }
	var showColorCorrectionPanel by remember { mutableStateOf(false) }
	var showThreeTabDrawer by remember { mutableStateOf(false) }
	var activeThreeTabDrawerTab by remember { mutableStateOf(ReaderDrawerTab.PAGE_GRID) }

	val bookmarkedPagesInChapter = remember(mangaItem.manga.id, chapterIndex) {
		mutableStateListOf<Int>().apply {
			if (isPageBookmarked) add(pageIndex)
		}
	}

	BackHandler {
		when {
			showAiUpscalePreviewModal -> showAiUpscalePreviewModal = false
			showColorCorrectionPanel -> showColorCorrectionPanel = false
			showReadingMoreSheet -> showReadingMoreSheet = false
			showThreeTabDrawer -> showThreeTabDrawer = false
			expandedSheetTab != null -> expandedSheetTab = null
			isHudVisible -> isHudVisible = false
			else -> onCloseReader()
		}
	}

	var zoomScale by remember(pageIndex, chapterIndex) { mutableFloatStateOf(1f) }
	var panOffset by remember(pageIndex, chapterIndex) { mutableStateOf(Offset.Zero) }
	var activeChapterSource by remember(mangaItem.manga.id) {
		mutableStateOf(UiSourceAndTitleFormatter.formatSourceName(mangaItem.manga.source))
	}

	val bookmarkedChapters = remember(mangaItem.manga.id) {
		mutableStateListOf<Int>().apply {
			if (isPageBookmarked) add(chapterIndex)
		}
	}

	val context = LocalContext.current
	val coroutineScope = rememberCoroutineScope()

	val cleanMangaTitle = remember(mangaItem.manga.title) {
		UiSourceAndTitleFormatter.formatDisplayTitle(mangaItem.manga.title)
	}

	val chapterEntries = remember(mangaItem.manga.id, totalChapters, activeChapterSource) {
		val relativeDates = listOf("Today", "Yesterday", "3d ago", "6d ago", "2w ago", "1mo ago", "2mo ago")
		val chapterTitles = listOf(
			"The Quiet Threshold",
			"Echoes Under Rain",
			"Promises in Neon",
			"Lanterns at Midnight",
			"Distant Footsteps",
			"Fractured Horizon",
			"Beneath the Cherry Tree",
			"Unspoken Words",
		)
		List(totalChapters) { idx ->
			val vol = (idx / 10) + 1
			val chNum = if (idx % 7 == 4) "${idx + 1}.5" else "${idx + 1}"
			ReaderChapterEntry(
				index = idx,
				volumeNumber = vol,
				chapterNumber = chNum,
				title = chapterTitles[idx % chapterTitles.size],
				relativeDate = relativeDates[(totalChapters - 1 - idx).coerceAtLeast(0) % relativeDates.size],
				scanlatorOrSource = activeChapterSource,
			)
		}
	}

	val currentChapterEntry = chapterEntries.getOrNull(chapterIndex.coerceIn(0, chapterEntries.lastIndex))
		?: ReaderChapterEntry(
			index = chapterIndex,
			volumeNumber = (chapterIndex / 10) + 1,
			chapterNumber = "${chapterIndex + 1}",
			title = "Chapter ${chapterIndex + 1}",
			relativeDate = "Today",
			scanlatorOrSource = activeChapterSource,
		)

	val effectiveAutoCrop = readerConfig.autoCropMargins ||
		readerConfig.scaleMode == ReaderScaleMode.SMART_CROP_BORDERS

	val isRtl = readerConfig.readingMode == ReaderStyleMode.PAGER_RTL
	val isVerticalPager = readerConfig.readingMode == ReaderStyleMode.PAGER_VERTICAL
	val isWebtoon = readerConfig.readingMode == ReaderStyleMode.WEBTOON_CONTINUOUS

	val decodedPageBitmap by produceState<Bitmap?>(
		initialValue = null,
		mangaItem.manga.url,
		chapterIndex,
		pageIndex,
		readerConfig.wideSpreadMode,
		isRtl,
	) {
		value = withContext(Dispatchers.IO) {
			decodeLocalMangaPageBitmap(
				mangaUrl = mangaItem.manga.url,
				pageIndex = pageIndex,
				wideSpreadMode = readerConfig.wideSpreadMode,
				isRtl = isRtl,
			)
		}
	}

	// Extract dominant edge/palette color from current Bitmap when Smart (by page) is selected
	val smartPageEdgeColor by produceState(
		initialValue = fallbackSeriesAccentColor(cleanMangaTitle),
		decodedPageBitmap,
		cleanMangaTitle,
	) {
		val bmp = decodedPageBitmap
		value = if (bmp != null && !bmp.isRecycled && bmp.width > 8 && bmp.height > 8) {
			withContext(Dispatchers.Default) {
				extractDominantEdgeColor(bmp, cleanMangaTitle)
			}
		} else {
			fallbackSeriesAccentColor(cleanMangaTitle)
		}
	}

	val colorFilter = remember(
		readerConfig.colorFilterPreset,
		readerConfig.brightnessDelta,
		readerConfig.contrastDelta,
		readerConfig.saturation,
		readerConfig.redBalance,
		readerConfig.greenBalance,
		readerConfig.blueBalance,
		readerConfig.invertColors,
		readerConfig.grayscale,
		readerConfig.warmPaperTint,
	) {
		buildReaderColorFilter(readerConfig)
	}

	val contentScale = when (readerConfig.scaleMode) {
		ReaderScaleMode.FIT_WIDTH -> ContentScale.FillWidth
		ReaderScaleMode.FIT_HEIGHT -> ContentScale.FillHeight
		ReaderScaleMode.FIT_SCREEN -> ContentScale.Fit
		ReaderScaleMode.ORIGINAL -> ContentScale.Inside
		ReaderScaleMode.SMART_CROP_BORDERS -> ContentScale.Crop
	}

	val baseBackgroundColor by animateColorAsState(
		targetValue = when (readerConfig.backgroundColorMode) {
			NekoBackgroundColorMode.WHITE -> Color(0xFFF6F7F9)
			NekoBackgroundColorMode.BLACK -> Color(0xFF040507)
			NekoBackgroundColorMode.SMART_BY_PAGE -> Color(0xFF090B10)
			NekoBackgroundColorMode.SMART_BY_THEME -> MaterialTheme.colorScheme.background
			NekoBackgroundColorMode.SMART_BY_THEME_BLACK -> Color(0xFF000000)
		},
		animationSpec = tween(260),
		label = "readerBgColor",
	)

	BoxWithConstraints(
		modifier = Modifier
			.fillMaxSize()
			.background(baseBackgroundColor)
			.testTag("reader_root_container"),
	) {
		val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
		val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
		val screenHeightDp = maxHeight

		// Smart (by page) radial vignette ambient glow extracted from bitmap edge + series palette
		if (readerConfig.backgroundColorMode == NekoBackgroundColorMode.SMART_BY_PAGE) {
			Canvas(modifier = Modifier.fillMaxSize()) {
				drawRect(
					brush = Brush.radialGradient(
						colors = listOf(
							smartPageEdgeColor.copy(alpha = 0.44f),
							smartPageEdgeColor.copy(alpha = 0.18f),
							Color(0xFF050608),
						),
						center = Offset(size.width * 0.5f, size.height * 0.48f),
						radius = size.maxDimension * 0.78f,
					)
				)
			}
		} else if (readerConfig.backgroundColorMode == NekoBackgroundColorMode.SMART_BY_THEME_BLACK) {
			val accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
			Canvas(modifier = Modifier.fillMaxSize()) {
				drawRect(
					brush = Brush.radialGradient(
						colors = listOf(accentGlow, Color.Black),
						center = Offset(size.width * 0.5f, size.height * 0.5f),
						radius = size.maxDimension * 0.65f,
					)
				)
			}
		}

		// Tap handler for 3x3 reading zones + Bedtime One-Handed Tap Zones
		val handleCanvasTap: (Offset, IntSize) -> Unit = { tapOffset, containerSize ->
			val normX = if (containerSize.width > 0) tapOffset.x / containerSize.width else 0.5f
			val normY = if (containerSize.height > 0) tapOffset.y / containerSize.height else 0.5f

			when {
				showColorCorrectionPanel -> showColorCorrectionPanel = false
				expandedSheetTab != null -> expandedSheetTab = null
				normX in 0.30f..0.70f && normY in 0.25f..0.75f -> {
					isHudVisible = !isHudVisible
				}
				else -> {
					// Turning a page via tap zone hides HUD for zero-clutter reading
					isHudVisible = false
					val isLeftZone = normX < 0.30f
					val isRightZone = normX > 0.70f
					val isLowerEdge = normY > 0.50f && (isLeftZone || isRightZone)

					val goNext = if (readerConfig.bedtimeOneHandedTapZones && isLowerEdge) {
						true // Both lower edges advance to next page for effortless bedtime reading
					} else if (isRtl) {
						isLeftZone
					} else {
						isRightZone
					}
					val goPrev = if (readerConfig.bedtimeOneHandedTapZones && isLowerEdge) {
						false
					} else if (isRtl) {
						isRightZone
					} else {
						isLeftZone
					}

					if (goNext) {
						if (pageIndex < totalPages - 1) {
							onPageChange(pageIndex + 1)
						} else if (chapterIndex < totalChapters - 1) {
							onChapterChange(chapterIndex + 1)
						}
					} else if (goPrev) {
						if (pageIndex > 0) {
							onPageChange(pageIndex - 1)
						} else if (chapterIndex > 0) {
							onChapterChange(chapterIndex - 1)
						}
					} else {
						isHudVisible = !isHudVisible
					}
				}
			}
		}

		val webtoonListState = rememberLazyListState(
			initialFirstVisibleItemIndex = pageIndex.coerceIn(0, totalPages - 1)
		)

		// Hands-Free Automatic Scroll with Bedtime Auto-Pause at end of chapter
		LaunchedEffect(
			readerConfig.autoScrollEnabled,
			readerConfig.autoScrollSpeedMultiplier,
			readerConfig.bedtimeAutoPauseAtChapterEnd,
			isWebtoon,
			pageIndex,
			chapterIndex,
		) {
			if (readerConfig.autoScrollEnabled) {
				val speed = readerConfig.autoScrollSpeedMultiplier.coerceIn(0.25f, 3.0f)
				if (isWebtoon) {
					while (readerConfig.autoScrollEnabled) {
						delay(48L)
						val scrolled = webtoonListState.animateScrollBy(14f * speed)
						if (scrolled <= 0.5f && readerConfig.bedtimeAutoPauseAtChapterEnd) {
							onUpdateReaderConfig { it.copy(autoScrollEnabled = false) }
							break
						}
					}
				} else {
					val intervalMs = (3600L / speed).toLong().coerceIn(900L, 8000L)
					delay(intervalMs)
					if (pageIndex < totalPages - 1) {
						onPageChange(pageIndex + 1)
					} else if (readerConfig.bedtimeAutoPauseAtChapterEnd) {
						// Bedtime Auto-Pause at end of chapter so it stops if the user falls asleep
						onUpdateReaderConfig { it.copy(autoScrollEnabled = false) }
					} else if (chapterIndex < totalChapters - 1) {
						onChapterChange(chapterIndex + 1)
					} else {
						onUpdateReaderConfig { it.copy(autoScrollEnabled = false) }
					}
				}
			}
		}

		val handleHardwareKeyNavigation: (Int) -> Boolean = { keyCode ->
			val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
			val isPageOrArrowKey = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
				keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
				keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
				keyCode == KeyEvent.KEYCODE_PAGE_UP
			if ((isVolumeKey && readerConfig.switchPagesWithVolumeKeys) || isPageOrArrowKey) {
				isHudVisible = false
				expandedSheetTab = null
				val rawForward = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
					keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
					keyCode == KeyEvent.KEYCODE_PAGE_DOWN
				val goNext = if (isVolumeKey && readerConfig.invertVolumeKeys) !rawForward else rawForward
				if (isWebtoon) {
					val deltaPx = if (goNext) screenHeightPx * 0.75f else -screenHeightPx * 0.75f
					coroutineScope.launch {
						webtoonListState.animateScrollBy(deltaPx)
					}
				} else if (goNext) {
					if (pageIndex < totalPages - 1) {
						onPageChange(pageIndex + 1)
					} else if (chapterIndex < totalChapters - 1) {
						onChapterChange(chapterIndex + 1)
					}
				} else {
					if (pageIndex > 0) {
						onPageChange(pageIndex - 1)
					} else if (chapterIndex > 0) {
						onChapterChange(chapterIndex - 1)
					}
				}
				true
			} else {
				false
			}
		}

		LaunchedEffect(volumeKeyEvents, readerConfig.switchPagesWithVolumeKeys, readerConfig.invertVolumeKeys, isWebtoon, pageIndex, chapterIndex) {
			volumeKeyEvents.collect { keyCode ->
				handleHardwareKeyNavigation(keyCode)
			}
		}

		// MAIN READER VIEWPORT (Webtoon Strip vs Horizontal/Vertical Pager)
		if (isWebtoon) {
			// Sync external page scrubber changes to Webtoon scroll position
			LaunchedEffect(pageIndex) {
				if (webtoonListState.firstVisibleItemIndex != pageIndex && !webtoonListState.isScrollInProgress) {
					webtoonListState.scrollToItem(pageIndex.coerceIn(0, totalPages - 1))
				}
			}

			// Automatically hide HUD & collapse sheet as soon as the user scrolls
			LaunchedEffect(webtoonListState) {
				snapshotFlow { webtoonListState.isScrollInProgress }
					.distinctUntilChanged()
					.collect { scrolling ->
						if (scrolling) {
							isHudVisible = false
							expandedSheetTab = null
						}
					}
			}

			LaunchedEffect(webtoonListState) {
				snapshotFlow { webtoonListState.firstVisibleItemIndex }
					.distinctUntilChanged()
					.collect { visibleIdx ->
						if (visibleIdx in 0 until totalPages && visibleIdx != pageIndex) {
							onPageChange(visibleIdx)
						}
					}
			}

			val sidePadFraction = (readerConfig.webtoonSidePaddingPercent / 100f).coerceIn(0f, 0.35f)
			var webtoonViewportSize by remember { mutableStateOf(IntSize.Zero) }

			LazyColumn(
				state = webtoonListState,
				modifier = Modifier
					.fillMaxSize()
					.onSizeChanged { webtoonViewportSize = it }
					.pointerInput(isRtl, pageIndex, chapterIndex, isHudVisible, expandedSheetTab, readerConfig.bedtimeOneHandedTapZones) {
						detectTapGestures(
							onDoubleTap = {
								zoomScale = if (zoomScale > 1.05f) 1f else 2f
								panOffset = Offset.Zero
							},
							onTap = { tapOffset ->
								handleCanvasTap(tapOffset, webtoonViewportSize)
							},
						)
					}
					.padding(horizontal = (sidePadFraction * 180).dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				items(totalPages, key = { "webtoon_page_$it" }) { idx ->
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(0.71f),
					) {
						MangaReaderPageCanvas(
							mangaTitle = cleanMangaTitle,
							chapterNumber = (chapterIndex + 1).toFloat(),
							pageNumber = idx + 1,
							totalPages = totalPages,
							upscaleMode = readerConfig.aiUpscaleMode,
							showBeforeAfterSplit = readerConfig.showBeforeAfterSplit,
							warmPaperTint = readerConfig.warmPaperTint,
							invertColors = readerConfig.invertColors,
							grayscale = readerConfig.grayscale,
							colorFilterPreset = readerConfig.colorFilterPreset,
							brightnessDelta = readerConfig.brightnessDelta,
							contrastDelta = readerConfig.contrastDelta,
							warmthDelta = readerConfig.warmthDelta,
							bedtimeDimmerAlpha = readerConfig.bedtimeDimmerAlpha,
							saturation = readerConfig.saturation,
							redBalance = readerConfig.redBalance,
							greenBalance = readerConfig.greenBalance,
							blueBalance = readerConfig.blueBalance,
							decodedBitmap = if (idx == pageIndex) decodedPageBitmap else null,
							modifier = Modifier
								.fillMaxSize()
								.padding(if (effectiveAutoCrop) 0.dp else 6.dp),
						)
					}
				}
			}
		} else {
			val pagerState = rememberPagerState(
				initialPage = pageIndex.coerceIn(0, totalPages - 1),
				pageCount = { totalPages },
			)

			// Sync external pageIndex (e.g. Neko Scrubber drag) with Pager
			LaunchedEffect(pageIndex) {
				if (pagerState.currentPage != pageIndex && !pagerState.isScrollInProgress) {
					pagerState.scrollToPage(pageIndex.coerceIn(0, totalPages - 1))
				}
			}

			// Automatically hide HUD & collapse bottom sheet as soon as the user scrolls or turns a page
			LaunchedEffect(pagerState) {
				snapshotFlow { pagerState.isScrollInProgress }
					.distinctUntilChanged()
					.collect { scrolling ->
						if (scrolling) {
							isHudVisible = false
							expandedSheetTab = null
						}
					}
			}

			LaunchedEffect(pagerState) {
				snapshotFlow { pagerState.currentPage }
					.distinctUntilChanged()
					.collect { settledPage ->
						if (settledPage != pageIndex) {
							isHudVisible = false
							expandedSheetTab = null
							onPageChange(settledPage)
						}
					}
			}

			var pagerViewportSize by remember { mutableStateOf(IntSize.Zero) }

			val pageContent: @Composable (Int) -> Unit = { pageIdx ->
				Box(
					modifier = Modifier
						.fillMaxSize()
						.onSizeChanged { pagerViewportSize = it }
						.pointerInput(isRtl, pageIndex, chapterIndex, isHudVisible, expandedSheetTab, readerConfig.bedtimeOneHandedTapZones) {
							detectTapGestures(
								onDoubleTap = {
									zoomScale = if (zoomScale > 1.05f) 1f else 2.25f
									panOffset = Offset.Zero
								},
								onTap = { tapOffset ->
									handleCanvasTap(tapOffset, pagerViewportSize)
								},
							)
						}
						.pointerInput(pageIdx) {
							detectTransformGestures { _, pan, zoom, _ ->
								val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
								zoomScale = newScale
								panOffset = if (newScale > 1.02f) {
									Offset(
										x = (panOffset.x + pan.x).coerceIn(-600f, 600f),
										y = (panOffset.y + pan.y).coerceIn(-900f, 900f),
									)
								} else {
									Offset.Zero
								}
							}
						}
						.padding(
							horizontal = if (readerConfig.scaleMode == ReaderScaleMode.FIT_HEIGHT) 16.dp else 0.dp,
							vertical = if (readerConfig.scaleMode == ReaderScaleMode.FIT_WIDTH) 0.dp else 8.dp,
						),
					contentAlignment = Alignment.Center,
				) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.graphicsLayer {
								scaleX = zoomScale
								scaleY = zoomScale
								translationX = panOffset.x
								translationY = panOffset.y
							},
						contentAlignment = Alignment.Center,
					) {
						MangaReaderPageCanvas(
							mangaTitle = cleanMangaTitle,
							chapterNumber = (chapterIndex + 1).toFloat(),
							pageNumber = pageIdx + 1,
							totalPages = totalPages,
							upscaleMode = readerConfig.aiUpscaleMode,
							showBeforeAfterSplit = readerConfig.showBeforeAfterSplit,
							warmPaperTint = readerConfig.warmPaperTint,
							invertColors = readerConfig.invertColors,
							grayscale = readerConfig.grayscale,
							colorFilterPreset = readerConfig.colorFilterPreset,
							brightnessDelta = readerConfig.brightnessDelta,
							contrastDelta = readerConfig.contrastDelta,
							warmthDelta = readerConfig.warmthDelta,
							bedtimeDimmerAlpha = readerConfig.bedtimeDimmerAlpha,
							saturation = readerConfig.saturation,
							redBalance = readerConfig.redBalance,
							greenBalance = readerConfig.greenBalance,
							blueBalance = readerConfig.blueBalance,
							decodedBitmap = if (pageIdx == pageIndex) decodedPageBitmap else null,
							modifier = Modifier
								.fillMaxSize()
								.padding(if (effectiveAutoCrop) 0.dp else 6.dp),
						)
					}
				}
			}

			if (isVerticalPager) {
				VerticalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { pageIdx ->
					pageContent(pageIdx)
				}
			} else {
				HorizontalPager(
					state = pagerState,
					reverseLayout = isRtl,
					modifier = Modifier.fillMaxSize(),
				) { pageIdx ->
					pageContent(pageIdx)
				}
			}
		}

		// =========================================================================
		// 1. FULLSCREEN READING STATE (isHudVisible == false)
		// Render ONLY the compact page counter text ("${currentPage} /${totalPages}",
		// 12.sp, semi-bold white with subtle drop shadow) centered at the very bottom edge.
		// =========================================================================
		if (!isHudVisible) {
			Text(
				text = "${pageIndex + 1} /$totalPages",
				style = TextStyle(
					color = Color.White,
					fontSize = 12.sp,
					fontWeight = FontWeight.SemiBold,
					shadow = Shadow(
						color = Color.Black.copy(alpha = 0.9f),
						offset = Offset(0f, 1.5f),
						blurRadius = 6f,
					),
				),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 12.dp)
					.testTag("fullscreen_page_counter"),
			)
		}

		// =========================================================================
		// 2. MOVABLE NEKO-STYLE PAGE SCRUBBER (Left | Horizontal | Right)
		// Visible only when isHudVisible == true && !isBottomSheetExpanded
		// =========================================================================
		val anyModalOrDrawerOpen = expandedSheetTab != null ||
			showReadingMoreSheet ||
			showColorCorrectionPanel ||
			showThreeTabDrawer ||
			showAiUpscalePreviewModal

		if (!anyModalOrDrawerOpen) {
			AnimatedVisibility(
				visible = isHudVisible && (readerConfig.sliderPosition == SliderPosition.LEFT_ALIGN || readerConfig.sliderPosition == SliderPosition.RIGHT_ALIGN),
				enter = fadeIn(tween(180)),
				exit = fadeOut(tween(120)),
				modifier = Modifier
					.align(
						if (readerConfig.sliderPosition == SliderPosition.LEFT_ALIGN) {
							Alignment.CenterStart
						} else {
							Alignment.CenterEnd
						},
					)
					.padding(
						start = 12.dp,
						end = 12.dp,
						bottom = if (readerConfig.showZoomButtons && readerConfig.sliderPosition == SliderPosition.RIGHT_ALIGN) 88.dp else 0.dp,
					),
			) {
				NekoVerticalPageScrubber(
					currentPage = pageIndex + 1,
					totalPages = totalPages,
					canGoPrevChapter = chapterIndex > 0,
					canGoNextChapter = chapterIndex < totalChapters - 1,
					onSelectPage = { oneBased ->
						onPageChange((oneBased - 1).coerceIn(0, totalPages - 1))
					},
					onPrevChapter = {
						if (chapterIndex > 0) onChapterChange(chapterIndex - 1)
					},
					onNextChapter = {
						if (chapterIndex < totalChapters - 1) onChapterChange(chapterIndex + 1)
					},
					onRepositionByDrag = { rawScreenOffset ->
						val newPos = resolveSliderPositionFromDrag(
							rawScreenOffset = rawScreenOffset,
							currentPos = readerConfig.sliderPosition,
							screenWidthPx = screenWidthPx,
							screenHeightPx = screenHeightPx,
						)
						if (newPos != readerConfig.sliderPosition) {
							onUpdateReaderConfig { it.copy(sliderPosition = newPos) }
						}
					},
				)
			}
		}

		// =========================================================================
		// TASK 5.1: TOGGLEABLE ON-SCREEN ZOOM BUTTONS (🔍+ / 🔍- & 100% ↺)
		// Bound strictly to isHudVisible && readerConfig.showZoomButtons && !anyModalOrDrawerOpen
		// Aligned to exact 12.dp right margin and 40.dp width matching NekoVerticalPageScrubber
		// =========================================================================
		AnimatedVisibility(
			visible = isHudVisible && readerConfig.showZoomButtons && !anyModalOrDrawerOpen,
			enter = fadeIn(tween(160)),
			exit = fadeOut(tween(120)),
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(end = 12.dp, bottom = 144.dp),
		) {
			ReaderFloatingZoomControls(
				zoomScale = zoomScale,
				onZoomIn = { zoomScale = (zoomScale + 0.35f).coerceAtMost(3.5f) },
				onZoomOut = {
					val next = (zoomScale - 0.35f).coerceAtLeast(1f)
					zoomScale = next
					if (next <= 1.02f) panOffset = Offset.Zero
				},
				onResetZoom = {
					zoomScale = 1f
					panOffset = Offset.Zero
				},
			)
		}

		// =========================================================================
		// 3.1 NEKO TOP APP BAR (when isHudVisible == true)
		// - Left: Back arrow button (single tap = back; long-press = jump to Home)
		// - Center: Two-line title block + compact 🕵️ Incognito badge next to chapter subtitle
		// - Right: Circular [ ✦ ] (AI Upscaling Preview) & [ ⋮ ] (Two-Tab Reading | More Sheet)
		// - Swipe down on top bar exits Reader back to MangaDetailsScreen
		// =========================================================================
		AnimatedVisibility(
			visible = isHudVisible,
			enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
			exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it },
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			var topBarSwipeDownDelta by remember { mutableFloatStateOf(0f) }
			Surface(
				color = Color(0xE6121318),
				tonalElevation = 0.dp,
				modifier = Modifier
					.fillMaxWidth()
					.pointerInput(Unit) {
						detectDragGestures(
							onDragStart = { topBarSwipeDownDelta = 0f },
							onDrag = { change, dragAmount ->
								if (dragAmount.y > 0f && abs(dragAmount.y) > abs(dragAmount.x)) {
									change.consume()
									topBarSwipeDownDelta += dragAmount.y
								}
							},
							onDragEnd = {
								if (topBarSwipeDownDelta > 60f) {
									onCloseReader()
								}
								topBarSwipeDownDelta = 0f
							},
						)
					}
					.testTag("neko_reader_top_bar"),
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.statusBarsPadding()
						.height(60.dp)
						.padding(horizontal = 8.dp),
				) {
					// Left: Back arrow button (single tap = back; long-press = jump to Home)
					Box(
						modifier = Modifier
							.align(Alignment.CenterStart)
							.size(48.dp)
							.clip(CircleShape)
							.combinedClickable(
								onClick = onCloseReader,
								onLongClick = onJumpToHome,
							)
							.testTag("reader_close_button"),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back (Long-press for Home)",
							tint = Color.White,
						)
					}

					// Center: Two-line centered title block + 🕵️ Incognito HUD badge
					Column(
						modifier = Modifier
							.align(Alignment.Center)
							.padding(horizontal = 52.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Text(
							text = cleanMangaTitle,
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							textAlign = TextAlign.Center,
						)
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								text = "Vol.${currentChapterEntry.volumeNumber} Ch.${currentChapterEntry.chapterNumber}",
								style = MaterialTheme.typography.bodySmall,
								color = Color(0xFF9EA4B0),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								textAlign = TextAlign.Center,
							)
							if (isIncognitoSession) {
								Surface(
									color = Color(0xFF5CE1E6).copy(alpha = 0.18f),
									shape = RoundedCornerShape(999.dp),
									modifier = Modifier.testTag("reader_incognito_hud_badge"),
								) {
									Text(
										text = "🕵️ Incognito",
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Bold,
										color = Color(0xFF5CE1E6),
										modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
									)
								}
							}
						}
					}

					// Right: Single circular icon button [ ⋮ ] (AI tools consolidated into bottom [✦] sheet)
					Surface(
						color = Color.White.copy(alpha = 0.08f),
						shape = CircleShape,
						modifier = Modifier
							.align(Alignment.CenterEnd)
							.size(40.dp)
							.clip(CircleShape)
							.clickable {
								activeReadingMoreTab = ReaderOptionsSheetTab.READING
								showReadingMoreSheet = true
							}
							.testTag("reader_more_options_button"),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.MoreVert,
								contentDescription = "Reader Reading & More options",
								tint = Color.White,
								modifier = Modifier.size(20.dp),
							)
						}
					}
				}
			}
		}

		// =========================================================================
		// 3.2 EXPANDABLE BOTTOM SHEET + NEMURI BOTTOM SCRUBBER DOCK
		// [ |◀ ]  ━━━|••••••••  [ ▶| ]  [ ⊞ ]
		// =========================================================================
		AnimatedVisibility(
			visible = isHudVisible && !showColorCorrectionPanel,
			enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
			exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			Column(
				modifier = Modifier.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				// Nemuri Bottom Scrubber Dock ([ |◀ ]  ━━━|••••••••  [ ▶| ]  [ ⊞ ])
				AnimatedVisibility(
					visible = isHudVisible && expandedSheetTab == null && readerConfig.sliderPosition == SliderPosition.HORIZONTAL,
					enter = fadeIn(tween(160)),
					exit = fadeOut(tween(120)),
				) {
					NemuriBottomScrubberDock(
						currentPage = pageIndex + 1,
						totalPages = totalPages,
						isRtl = isRtl,
						canGoPrevChapter = chapterIndex > 0,
						canGoNextChapter = chapterIndex < totalChapters - 1,
						onSelectPage = { oneBased ->
							onPageChange((oneBased - 1).coerceIn(0, totalPages - 1))
						},
						onPrevChapter = {
							if (chapterIndex > 0) onChapterChange(chapterIndex - 1)
						},
						onNextChapter = {
							if (chapterIndex < totalChapters - 1) onChapterChange(chapterIndex + 1)
						},
						onOpenThreeTabDrawer = {
							activeThreeTabDrawerTab = ReaderDrawerTab.PAGE_GRID
							showThreeTabDrawer = true
						},
						onSwipeUpOpenOptions = {
							activeReadingMoreTab = ReaderOptionsSheetTab.READING
							showReadingMoreSheet = true
						},
						onSwipeDownExitReader = onCloseReader,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 8.dp),
					)
				}

				val isAiOrCropActive = readerConfig.aiUpscaleMode != AiUpscaleMode.OFF || effectiveAutoCrop
				val targetSheetBodyHeight by animateDpAsState(
					targetValue = if (expandedSheetTab != null) (screenHeightDp * 0.55f) else 0.dp,
					animationSpec = tween(240),
					label = "nekoBottomSheetHeight",
				)

				Surface(
					color = Color(0xFF181920),
					shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
					tonalElevation = 8.dp,
					shadowElevation = 16.dp,
					modifier = Modifier
						.fillMaxWidth()
						.navigationBarsPadding()
						.testTag("neko_reader_bottom_sheet"),
				) {
					Column(modifier = Modifier.fillMaxWidth()) {
						// Centered 36.dp x 4.dp rounded drag handle (#4A4E58, padding(top = 10.dp, bottom = 6.dp))
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									expandedSheetTab = if (expandedSheetTab != null) {
										null
									} else {
										NekoBottomSheetTab.AI_TOOLS
									}
								}
								.padding(top = 10.dp, bottom = 6.dp)
								.testTag("neko_reader_sheet_drag_handle"),
							contentAlignment = Alignment.Center,
						) {
							Box(
								modifier = Modifier
									.width(36.dp)
									.height(4.dp)
									.clip(CircleShape)
									.background(Color(0xFF4A4E58)),
							)
						}

						// Smoothly expanding 55% height content area
						if (targetSheetBodyHeight > 0.dp && expandedSheetTab != null) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(targetSheetBodyHeight),
							) {
								when (expandedSheetTab!!) {
									NekoBottomSheetTab.CHAPTERS -> {
										NekoChaptersTabContent(
											chapters = chapterEntries,
											activeChapterIndex = chapterIndex,
											bookmarkedChapterIndices = bookmarkedChapters,
											onSelectChapter = { selectedIdx ->
												onChapterChange(selectedIdx)
												expandedSheetTab = null
											},
											onToggleChapterBookmark = { targetIdx ->
												if (bookmarkedChapters.contains(targetIdx)) {
													bookmarkedChapters.remove(targetIdx)
												} else {
													bookmarkedChapters.add(targetIdx)
												}
												if (targetIdx == chapterIndex) {
													onTogglePageBookmark()
												}
											},
										)
									}

									NekoBottomSheetTab.AI_TOOLS -> {
										NekoAiAndPageToolsTabContent(
											mangaItem = mangaItem,
											readerConfig = readerConfig,
											effectiveAutoCrop = effectiveAutoCrop,
											croppedPercent = readerConfig.marginCropPercent,
											activeSource = activeChapterSource,
											onSelectSource = { newSource ->
												activeChapterSource = newSource
											},
											onOpenAiUpscalePreview = {
												expandedSheetTab = null
												showAiUpscalePreviewModal = true
											},
											onUpdateReaderConfig = onUpdateReaderConfig,
										)
									}

									NekoBottomSheetTab.SETTINGS -> {
										NekoSettingsTabContent(
											readerConfig = readerConfig,
											activeSubTab = settingsSubTab,
											onSelectSubTab = { settingsSubTab = it },
											onUpdateReaderConfig = onUpdateReaderConfig,
										)
									}
								}
							}
							HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
						}

						// Collapsed 3-Icon Navigation Bar (height = 64.dp)
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.height(64.dp)
								.padding(horizontal = 16.dp),
							horizontalArrangement = Arrangement.SpaceEvenly,
							verticalAlignment = Alignment.CenterVertically,
						) {
							// 1. Chapters (FormatListNumbered icon)
							NekoBottomBarIconButton(
								icon = Icons.Default.FormatListNumbered,
								contentDescription = "Chapters",
								isSelected = expandedSheetTab == NekoBottomSheetTab.CHAPTERS,
								showActiveBadgeDot = false,
								onClick = {
									expandedSheetTab = if (expandedSheetTab == NekoBottomSheetTab.CHAPTERS) {
										null
									} else {
										NekoBottomSheetTab.CHAPTERS
									}
								},
								modifier = Modifier.testTag("neko_tab_chapters"),
							)

							// 2. AI & Crop Tools (AutoAwesome icon — replaces Neko's unused Comments icon)
							NekoBottomBarIconButton(
								icon = Icons.Default.AutoAwesome,
								contentDescription = "AI & Crop Tools",
								isSelected = expandedSheetTab == NekoBottomSheetTab.AI_TOOLS,
								showActiveBadgeDot = isAiOrCropActive,
								onClick = {
									expandedSheetTab = if (expandedSheetTab == NekoBottomSheetTab.AI_TOOLS) {
										null
									} else {
										NekoBottomSheetTab.AI_TOOLS
									}
								},
								modifier = Modifier.testTag("neko_tab_ai_tools"),
							)

							// 3. Reader Settings (Tune sliders icon)
							NekoBottomBarIconButton(
								icon = Icons.Default.Tune,
								contentDescription = "Reader Settings",
								isSelected = expandedSheetTab == NekoBottomSheetTab.SETTINGS,
								showActiveBadgeDot = false,
								onClick = {
									expandedSheetTab = if (expandedSheetTab == NekoBottomSheetTab.SETTINGS) {
										null
									} else {
										NekoBottomSheetTab.SETTINGS
									}
								},
								modifier = Modifier.testTag("neko_tab_settings"),
							)
						}
					}
				}
			}
		}

		// =========================================================================
		// TASK 3.3: LIVE COLOR CORRECTION & "NEMURI BEDTIME PAPER" PANEL
		// Compact bottom panel WITHOUT a dark backdrop dim so the live manga page
		// above updates in real time.
		// =========================================================================
		AnimatedVisibility(
			visible = showColorCorrectionPanel,
			enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it },
			exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it },
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			ReaderBedtimeColorCorrectionPanel(
				readerConfig = readerConfig,
				hasCustomSeriesProfile = hasCustomSeriesProfile,
				onUpdateReaderConfig = onUpdateReaderConfig,
				onSaveSeriesProfile = onSaveSeriesProfile,
				onDismiss = { showColorCorrectionPanel = false },
			)
		}

		// =========================================================================
		// TASK 3: TWO-TAB READER OPTIONS SHEET (Reading | More)
		// =========================================================================
		if (showReadingMoreSheet) {
			ReaderTwoTabOptionsSheet(
				activeTab = activeReadingMoreTab,
				onSelectTab = { activeReadingMoreTab = it },
				readerConfig = readerConfig,
				isPageBookmarked = isPageBookmarked || bookmarkedPagesInChapter.contains(pageIndex),
				mangaTitle = cleanMangaTitle,
				chapterNumber = currentChapterEntry.chapterNumber,
				pageNumber = pageIndex + 1,
				onUpdateReaderConfig = onUpdateReaderConfig,
				onTogglePageBookmark = {
					if (bookmarkedPagesInChapter.contains(pageIndex)) {
						bookmarkedPagesInChapter.remove(pageIndex)
					} else {
						bookmarkedPagesInChapter.add(pageIndex)
					}
					onTogglePageBookmark()
				},
				onOpenColorCorrection = {
					showReadingMoreSheet = false
					showColorCorrectionPanel = true
				},
				onOpenAdvancedReaderSettings = {
					showReadingMoreSheet = false
					isHudVisible = true
					expandedSheetTab = NekoBottomSheetTab.SETTINGS
				},
				onDismiss = { showReadingMoreSheet = false },
			)
		}

		// =========================================================================
		// TASK 4.2: 3-TAB CHAPTER / PAGE GRID / BOOKMARKS DRAWER
		// =========================================================================
		if (showThreeTabDrawer) {
			ReaderThreeTabChapterPageGridDrawer(
				activeTab = activeThreeTabDrawerTab,
				onSelectTab = { activeThreeTabDrawerTab = it },
				mangaTitle = cleanMangaTitle,
				chapters = chapterEntries,
				activeChapterIndex = chapterIndex,
				currentPageIndex = pageIndex,
				totalPages = totalPages,
				bookmarkedPages = bookmarkedPagesInChapter,
				readerConfig = readerConfig,
				onSelectChapter = { selectedCh ->
					onChapterChange(selectedCh)
					showThreeTabDrawer = false
					isHudVisible = false
				},
				onSelectPage = { selectedPage ->
					onPageChange(selectedPage)
					showThreeTabDrawer = false
					isHudVisible = false
				},
				onToggleBookmarkPage = { targetPage ->
					if (bookmarkedPagesInChapter.contains(targetPage)) {
						bookmarkedPagesInChapter.remove(targetPage)
					} else {
						bookmarkedPagesInChapter.add(targetPage)
					}
					if (targetPage == pageIndex) {
						onTogglePageBookmark()
					}
				},
				onDismiss = { showThreeTabDrawer = false },
			)
		}

		// =========================================================================
		// TASK 2: LIVE "✦ AI UPSCALING PREVIEW" MODAL
		// =========================================================================
		if (showAiUpscalePreviewModal) {
			ReaderAiUpscalingPreviewModal(
				mangaTitle = cleanMangaTitle,
				chapterNumber = (chapterIndex + 1).toFloat(),
				pageNumber = pageIndex + 1,
				totalPages = totalPages,
				readerConfig = readerConfig,
				decodedBitmap = decodedPageBitmap,
				onUpdateReaderConfig = onUpdateReaderConfig,
				onDismiss = { showAiUpscalePreviewModal = false },
			)
		}
	}
}

@Composable
private fun NekoBottomBarIconButton(
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	contentDescription: String,
	isSelected: Boolean,
	showActiveBadgeDot: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accentCyan = Color(0xFF5CE1E6)
	Box(
		modifier = modifier
			.size(52.dp)
			.clip(RoundedCornerShape(16.dp))
			.background(
				if (isSelected) accentCyan.copy(alpha = 0.16f) else Color.Transparent
			)
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = icon,
			contentDescription = contentDescription,
			tint = if (isSelected) accentCyan else Color(0xFFD5DAE5),
			modifier = Modifier.size(24.dp),
		)
		if (showActiveBadgeDot) {
			Box(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(top = 10.dp, end = 10.dp)
					.size(7.dp)
					.clip(CircleShape)
					.background(accentCyan),
			)
		}
	}
}

// =========================================================================
// 2.1 VERTICAL NEKO 3-PIECE PAGE SCRUBBER & CHAPTER SKIPPER
// =========================================================================
@Composable
private fun NekoVerticalPageScrubber(
	currentPage: Int,
	totalPages: Int,
	canGoPrevChapter: Boolean,
	canGoNextChapter: Boolean,
	onSelectPage: (Int) -> Unit,
	onPrevChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onRepositionByDrag: (Offset) -> Unit,
	modifier: Modifier = Modifier,
) {
	val capsuleBg = Color(0xD91E2532)
	val capsuleBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
	val accentColor = Color(0xFF5CE1E6)
	var dragAccumulatedOffset by remember { mutableStateOf(Offset.Zero) }

	Column(
		modifier = modifier
			.pointerInput(Unit) {
				detectDragGesturesAfterLongPress(
					onDragStart = { dragAccumulatedOffset = Offset.Zero },
					onDrag = { change, dragAmount ->
						change.consume()
						dragAccumulatedOffset += dragAmount
					},
					onDragEnd = {
						onRepositionByDrag(dragAccumulatedOffset)
						dragAccumulatedOffset = Offset.Zero
					},
				)
			}
			.testTag("neko_vertical_scrubber"),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// Top Capsule (40.dp x 40.dp, RoundedCornerShape(14.dp), rgba(30, 37, 50, 0.85)): SkipPrevious rotated vertically
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(14.dp),
			border = capsuleBorder,
			modifier = Modifier.size(40.dp),
		) {
			IconButton(
				onClick = onPrevChapter,
				enabled = canGoPrevChapter,
				modifier = Modifier.fillMaxSize(),
			) {
				Icon(
					imageVector = Icons.Default.SkipPrevious,
					contentDescription = "Previous Chapter",
					tint = if (canGoPrevChapter) Color.White else Color.White.copy(alpha = 0.35f),
					modifier = Modifier
						.size(20.dp)
						.rotate(90f),
				)
			}
		}

		// Middle Scrubber Pill (40.dp wide, 200.dp tall, RoundedCornerShape(20.dp), rgba(30, 37, 50, 0.85))
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(20.dp),
			border = capsuleBorder,
			modifier = Modifier
				.width(40.dp)
				.height(200.dp),
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(vertical = 10.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.SpaceBetween,
			) {
				// Top label: Current page number
				Text(
					text = "$currentPage",
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Bold,
					color = Color.White,
				)

				// Center track: Vertical continuous interactive slider track with crisp horizontal bar thumb
				var trackHeightPx by remember { mutableFloatStateOf(1f) }
				val progress = if (totalPages > 1) {
					((currentPage - 1).toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
				} else {
					0f
				}

				val updatePageFromY: (Float) -> Unit = { yPx ->
					val fraction = (yPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
					val targetPage = 1 + (fraction * (totalPages - 1)).roundToInt()
					onSelectPage(targetPage.coerceIn(1, totalPages))
				}

				Box(
					modifier = Modifier
						.weight(1f)
						.fillMaxWidth()
						.padding(vertical = 8.dp)
						.onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
						.pointerInput(totalPages) {
							detectTapGestures { tapOffset ->
								updatePageFromY(tapOffset.y)
							}
						}
						.pointerInput(totalPages) {
							detectDragGestures { change, _ ->
								change.consume()
								updatePageFromY(change.position.y)
							}
						},
					contentAlignment = Alignment.Center,
				) {
					Canvas(modifier = Modifier.fillMaxSize()) {
						val centerX = size.width / 2f
						val trackTop = 4.dp.toPx()
						val trackBottom = (size.height - 4.dp.toPx()).coerceAtLeast(trackTop + 1f)
						val usableHeight = trackBottom - trackTop
						val thumbY = trackTop + usableHeight * progress

						// Unfilled track
						drawLine(
							color = Color.White.copy(alpha = 0.24f),
							start = Offset(centerX, trackTop),
							end = Offset(centerX, trackBottom),
							strokeWidth = 3.dp.toPx(),
						)
						// Active progress track
						drawLine(
							color = accentColor,
							start = Offset(centerX, trackTop),
							end = Offset(centerX, thumbY),
							strokeWidth = 3.5.dp.toPx(),
						)
						// Crisp horizontal bar thumb (#5CE1E6 / #8AB4F8 accent)
						val halfThumbW = 11.dp.toPx()
						drawLine(
							color = accentColor,
							start = Offset(centerX - halfThumbW, thumbY),
							end = Offset(centerX + halfThumbW, thumbY),
							strokeWidth = 3.5.dp.toPx(),
						)
					}
				}

				// Bottom label: Total page count
				Text(
					text = "$totalPages",
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Medium,
					color = Color.White.copy(alpha = 0.78f),
				)
			}
		}

		// Bottom Capsule (40.dp x 40.dp, RoundedCornerShape(14.dp), rgba(30, 37, 50, 0.85)): SkipNext rotated vertically
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(14.dp),
			border = capsuleBorder,
			modifier = Modifier.size(40.dp),
		) {
			IconButton(
				onClick = onNextChapter,
				enabled = canGoNextChapter,
				modifier = Modifier.fillMaxSize(),
			) {
				Icon(
					imageVector = Icons.Default.SkipNext,
					contentDescription = "Next Chapter",
					tint = if (canGoNextChapter) Color.White else Color.White.copy(alpha = 0.35f),
					modifier = Modifier
						.size(20.dp)
						.rotate(90f),
				)
			}
		}
	}
}

// =========================================================================
// 2.2 HORIZONTAL NEKO 3-PIECE PAGE SCRUBBER & CHAPTER SKIPPER
// [ ⏮ ] — [ 1  ━━━━━━━|━━━━  13 ] — [ ⏭ ]
// =========================================================================
@Composable
private fun NekoHorizontalPageScrubber(
	currentPage: Int,
	totalPages: Int,
	isRtl: Boolean,
	canGoPrevChapter: Boolean,
	canGoNextChapter: Boolean,
	onSelectPage: (Int) -> Unit,
	onPrevChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onRepositionByDrag: (Offset) -> Unit,
	modifier: Modifier = Modifier,
) {
	val capsuleBg = Color(0xCC1A1C24)
	val accentColor = Color(0xFF5CE1E6)
	var dragAccumulatedOffset by remember { mutableStateOf(Offset.Zero) }

	Row(
		modifier = modifier
			.pointerInput(Unit) {
				detectDragGesturesAfterLongPress(
					onDragStart = { dragAccumulatedOffset = Offset.Zero },
					onDrag = { change, dragAmount ->
						change.consume()
						dragAccumulatedOffset += dragAmount
					},
					onDragEnd = {
						onRepositionByDrag(dragAccumulatedOffset)
						dragAccumulatedOffset = Offset.Zero
					},
				)
			}
			.testTag("neko_horizontal_scrubber"),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// Left Capsule: SkipPrevious (or Next when RTL if preferred, keeping standard Prev/Next capsule icons)
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(12.dp),
			modifier = Modifier.size(44.dp),
		) {
			IconButton(
				onClick = if (isRtl) onNextChapter else onPrevChapter,
				enabled = if (isRtl) canGoNextChapter else canGoPrevChapter,
				modifier = Modifier.fillMaxSize(),
			) {
				Icon(
					imageVector = Icons.Default.SkipPrevious,
					contentDescription = if (isRtl) "Next Chapter" else "Previous Chapter",
					tint = if (if (isRtl) canGoNextChapter else canGoPrevChapter) {
						Color.White
					} else {
						Color.White.copy(alpha = 0.35f)
					},
					modifier = Modifier.size(22.dp),
				)
			}
		}

		// Middle Horizontal Pill: [ 1  ━━━━━━━|━━━━  18 ]
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(16.dp),
			modifier = Modifier
				.weight(1f)
				.height(44.dp),
		) {
			Row(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 14.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				val leftLabel = if (isRtl) "$totalPages" else "$currentPage"
				val rightLabel = if (isRtl) "$currentPage" else "$totalPages"

				Text(
					text = leftLabel,
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Bold,
					color = Color.White,
				)

				var trackWidthPx by remember { mutableFloatStateOf(1f) }
				val progress = if (totalPages > 1) {
					((currentPage - 1).toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
				} else {
					0f
				}
				val visualFraction = if (isRtl) 1f - progress else progress

				val updatePageFromX: (Float) -> Unit = { xPx ->
					val rawRatio = (xPx / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
					val effectiveRatio = if (isRtl) 1f - rawRatio else rawRatio
					val targetPage = 1 + (effectiveRatio * (totalPages - 1)).roundToInt()
					onSelectPage(targetPage.coerceIn(1, totalPages))
				}

				Box(
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight()
						.onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
						.pointerInput(totalPages, isRtl) {
							detectTapGestures { tapOffset ->
								updatePageFromX(tapOffset.x)
							}
						}
						.pointerInput(totalPages, isRtl) {
							detectDragGestures { change, _ ->
								change.consume()
								updatePageFromX(change.position.x)
							}
						},
					contentAlignment = Alignment.Center,
				) {
					Canvas(modifier = Modifier.fillMaxSize()) {
						val centerY = size.height / 2f
						val trackLeft = 4.dp.toPx()
						val trackRight = (size.width - 4.dp.toPx()).coerceAtLeast(trackLeft + 1f)
						val usableW = trackRight - trackLeft
						val thumbX = trackLeft + usableW * visualFraction

						// Base track
						drawLine(
							color = Color.White.copy(alpha = 0.24f),
							start = Offset(trackLeft, centerY),
							end = Offset(trackRight, centerY),
							strokeWidth = 3.dp.toPx(),
						)
						// Active track
						if (isRtl) {
							drawLine(
								color = accentColor,
								start = Offset(thumbX, centerY),
								end = Offset(trackRight, centerY),
								strokeWidth = 3.5.dp.toPx(),
							)
						} else {
							drawLine(
								color = accentColor,
								start = Offset(trackLeft, centerY),
								end = Offset(thumbX, centerY),
								strokeWidth = 3.5.dp.toPx(),
							)
						}
						// Crisp vertical bar thumb
						val halfThumbH = 9.dp.toPx()
						drawLine(
							color = accentColor,
							start = Offset(thumbX, centerY - halfThumbH),
							end = Offset(thumbX, centerY + halfThumbH),
							strokeWidth = 3.5.dp.toPx(),
						)
					}
				}

				Text(
					text = rightLabel,
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Medium,
					color = Color.White.copy(alpha = 0.78f),
				)
			}
		}

		// Right Capsule: SkipNext
		Surface(
			color = capsuleBg,
			shape = RoundedCornerShape(12.dp),
			modifier = Modifier.size(44.dp),
		) {
			IconButton(
				onClick = if (isRtl) onPrevChapter else onNextChapter,
				enabled = if (isRtl) canGoPrevChapter else canGoNextChapter,
				modifier = Modifier.fillMaxSize(),
			) {
				Icon(
					imageVector = Icons.Default.SkipNext,
					contentDescription = if (isRtl) "Previous Chapter" else "Next Chapter",
					tint = if (if (isRtl) canGoPrevChapter else canGoNextChapter) {
						Color.White
					} else {
						Color.White.copy(alpha = 0.35f)
					},
					modifier = Modifier.size(22.dp),
				)
			}
		}
	}
}

private fun resolveSliderPositionFromDrag(
	rawScreenOffset: Offset,
	currentPos: SliderPosition,
	screenWidthPx: Float,
	screenHeightPx: Float,
): SliderPosition {
	if (rawScreenOffset.y > screenHeightPx * 0.16f) {
		return SliderPosition.HORIZONTAL
	}
	if (currentPos == SliderPosition.RIGHT_ALIGN && rawScreenOffset.x < -screenWidthPx * 0.25f) {
		return SliderPosition.LEFT_ALIGN
	}
	if (currentPos == SliderPosition.LEFT_ALIGN && rawScreenOffset.x > screenWidthPx * 0.25f) {
		return SliderPosition.RIGHT_ALIGN
	}
	return currentPos
}

// =========================================================================
// TAB 1: CHAPTERS LIST (NEKO STYLE)
// =========================================================================
@Composable
private fun NekoChaptersTabContent(
	chapters: List<ReaderChapterEntry>,
	activeChapterIndex: Int,
	bookmarkedChapterIndices: List<Int>,
	onSelectChapter: (Int) -> Unit,
	onToggleChapterBookmark: (Int) -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val listState = rememberLazyListState(
		initialFirstVisibleItemIndex = (activeChapterIndex - 1).coerceAtLeast(0),
	)

	LaunchedEffect(activeChapterIndex) {
		listState.scrollToItem((activeChapterIndex - 1).coerceAtLeast(0))
	}

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = "Chapters (${chapters.size})",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
				color = Color.White,
			)
			Text(
				text = "Active: Ch.${chapters.getOrNull(activeChapterIndex)?.chapterNumber ?: (activeChapterIndex + 1)}",
				style = MaterialTheme.typography.labelMedium,
				color = accentCyan,
			)
		}

		HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

		LazyColumn(
			state = listState,
			modifier = Modifier
				.fillMaxSize()
				.testTag("neko_chapters_lazy_list"),
			contentPadding = PaddingValues(vertical = 6.dp),
		) {
			itemsIndexed(chapters, key = { _, item -> "neko_ch_${item.index}" }) { idx, chapter ->
				val isCurrent = idx == activeChapterIndex
				val isBookmarked = bookmarkedChapterIndices.contains(idx)

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.background(
							if (isCurrent) accentCyan.copy(alpha = 0.10f) else Color.Transparent
						)
						.clickable { onSelectChapter(idx) }
						.padding(horizontal = 20.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(
						modifier = Modifier
							.weight(1f)
							.padding(end = 12.dp),
						verticalArrangement = Arrangement.spacedBy(3.dp),
					) {
						// Line 1: Vol.X Ch.Y - Chapter Title
						Text(
							text = "Vol.${chapter.volumeNumber} Ch.${chapter.chapterNumber} - ${chapter.title}",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
							fontStyle = if (isCurrent) FontStyle.Italic else FontStyle.Normal,
							color = if (isCurrent) accentCyan else Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						// Line 2: Relative Date • Scanlator / Source
						Text(
							text = "${chapter.relativeDate} • ${chapter.scanlatorOrSource}",
							style = MaterialTheme.typography.bodySmall,
							color = if (isCurrent) accentCyan.copy(alpha = 0.85f) else Color(0xFF9EA4B0),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}

					// Right action: One-tap Bookmark ribbon icon button
					IconButton(
						onClick = { onToggleChapterBookmark(idx) },
						modifier = Modifier.size(40.dp),
					) {
						Icon(
							imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
							contentDescription = if (isBookmarked) "Remove chapter bookmark" else "Bookmark chapter",
							tint = if (isBookmarked) accentCyan else Color(0xFF9EA4B0),
							modifier = Modifier.size(20.dp),
						)
					}
				}
			}
		}
	}
}

// =========================================================================
// TAB 2: AI & PAGE TOOLS (REPLACING NEKO'S UNUSED COMMENTS TAB)
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NekoAiAndPageToolsTabContent(
	mangaItem: LibraryMangaItem,
	readerConfig: ReaderCustomizationConfig,
	effectiveAutoCrop: Boolean,
	croppedPercent: Int,
	activeSource: String,
	onSelectSource: (String) -> Unit,
	onOpenAiUpscalePreview: () -> Unit,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	var showSourceFailoverPicker by remember { mutableStateOf(false) }
	var isAutoFailoverEnabled by remember { mutableStateOf(true) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		// 1. AI Crisp Ink & Line Art Upscaler: Segmented selector (Off | 1.5x Fast NPU | 2.0x HQ)
		//    + Full-width action button: ✦ Compare Original vs. AI Upscaled Preview
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Icon(
					imageVector = Icons.Default.AutoAwesome,
					contentDescription = null,
					tint = accentCyan,
					modifier = Modifier.size(18.dp),
				)
				Text(
					text = "AI Crisp Ink & Line Art Upscaler",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
				)
			}

			val upscaleOptions = listOf(
				AiUpscaleMode.OFF to "Off",
				AiUpscaleMode.FAST_CRISP_INK_1_5X to "1.5x Fast NPU",
				AiUpscaleMode.ANIME_SUPER_RES_2X to "2.0x HQ",
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				upscaleOptions.forEach { (mode, label) ->
					val selected = readerConfig.aiUpscaleMode == mode
					NekoPillOptionButton(
						label = label,
						selected = selected,
						onClick = {
							onUpdateReaderConfig { it.copy(aiUpscaleMode = mode) }
						},
						modifier = Modifier.weight(1f),
					)
				}
			}

			OutlinedButton(
				onClick = onOpenAiUpscalePreview,
				shape = RoundedCornerShape(12.dp),
				border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.45f)),
				colors = ButtonDefaults.outlinedButtonColors(
					containerColor = accentCyan.copy(alpha = 0.10f),
					contentColor = accentCyan,
				),
				modifier = Modifier
					.fillMaxWidth()
					.testTag("reader_ai_compare_upscale_preview_button"),
			) {
				Icon(
					imageVector = Icons.Default.AutoAwesome,
					contentDescription = null,
					tint = accentCyan,
					modifier = Modifier.size(16.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = "✦ Preview AI Upscaling (Split Compare)",
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					color = accentCyan,
				)
			}
		}

		// 2. Smart Margin Crop: Toggle switch + live percentage badge (Auto-Crop Whitespace (-27%))
		Surface(
			color = Color(0xFF21232C),
			shape = RoundedCornerShape(16.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						imageVector = Icons.Default.Crop,
						contentDescription = null,
						tint = if (effectiveAutoCrop) accentCyan else Color(0xFF9EA4B0),
						modifier = Modifier.size(20.dp),
					)
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = "Smart Margin Crop",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
						)
						Text(
							text = if (effectiveAutoCrop) {
								"Auto-Crop Whitespace (-${croppedPercent.coerceAtLeast(12)}%)"
							} else {
								"Preserve original scan borders"
							},
							style = MaterialTheme.typography.labelSmall,
							color = if (effectiveAutoCrop) accentCyan else Color(0xFF9EA4B0),
						)
					}
				}
				Switch(
					checked = effectiveAutoCrop,
					onCheckedChange = { enabled ->
						onUpdateReaderConfig {
							it.copy(
								autoCropMargins = enabled,
								scaleMode = if (!enabled && it.scaleMode == ReaderScaleMode.SMART_CROP_BORDERS) {
									ReaderScaleMode.FIT_WIDTH
								} else {
									it.scaleMode
								},
							)
						}
					},
					colors = SwitchDefaults.colors(
						checkedThumbColor = Color(0xFF0A1014),
						checkedTrackColor = accentCyan,
					),
				)
			}
		}

		// 3. Wide-Page Spreads: Segmented selector (None | Split into 2 Pages | Auto-Rotate 90°)
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(
				text = "Wide-Page Spreads",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				color = Color.White,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				NekoWideSpreadMode.entries.forEach { mode ->
					val selected = readerConfig.wideSpreadMode == mode
					NekoPillOptionButton(
						label = mode.label,
						selected = selected,
						onClick = {
							onUpdateReaderConfig {
								it.copy(
									wideSpreadMode = mode,
									splitDoubleSpreadsInPortrait = mode == NekoWideSpreadMode.SPLIT_INTO_TWO,
								)
							}
						},
						modifier = Modifier.weight(1f),
					)
				}
			}
		}

		// 4. Source Failover / Switcher: Single compact card with Switch ▸ button opening MigrationSourceSelectorScreen
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Icon(
					imageVector = Icons.Default.SwapHoriz,
					contentDescription = null,
					tint = accentCyan,
					modifier = Modifier.size(18.dp),
				)
				Text(
					text = "Source Failover / Switcher",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
				)
			}
			Surface(
				color = Color(0xFF21232C),
				shape = RoundedCornerShape(16.dp),
				border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { showSourceFailoverPicker = true }
					.testTag("reader_source_failover_compact_card"),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(
						modifier = Modifier.weight(1f),
						verticalArrangement = Arrangement.spacedBy(3.dp),
					) {
						Text(
							text = "Active Source: $activeSource (Mirror)",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Bold,
							color = Color.White,
						)
						Text(
							text = if (isAutoFailoverEnabled) {
								"✦ Auto-Failover Enabled (4 backup sources ready)"
							} else {
								"Manual Mirror Override • Tap Switch to change source"
							},
							style = MaterialTheme.typography.labelSmall,
							color = accentCyan,
						)
					}
					FilledTonalButton(
						onClick = { showSourceFailoverPicker = true },
						colors = ButtonDefaults.filledTonalButtonColors(
							containerColor = accentCyan.copy(alpha = 0.16f),
							contentColor = accentCyan,
						),
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier.testTag("reader_source_failover_switch_button"),
					) {
						Text(
							text = "Switch ▸",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
						)
					}
				}
			}
		}
	}

	if (showSourceFailoverPicker) {
		val liveReaderItem = remember(mangaItem, activeSource) {
			mangaItem.copy(manga = mangaItem.manga.copy(source = activeSource))
		}
		org.koitharu.kotatsu.ui.components.UniversalSheet(
			onClose = { showSourceFailoverPicker = false },
			containerColor = Color(0xFF121620),
			sheetTestTag = "reader_source_manager_universal_sheet",
		) {
			SourceMigrationModalSheet(
				itemsToMigrate = listOf(liveReaderItem),
				onMigrateSingle = { _, targetSource, _ ->
					isAutoFailoverEnabled = false
					onSelectSource(UiSourceAndTitleFormatter.formatSourceName(targetSource))
					showSourceFailoverPicker = false
				},
				onMigrateAllSelected = { migrations ->
					migrations.firstOrNull()?.let { (_, targetSource, _) ->
						isAutoFailoverEnabled = false
						onSelectSource(UiSourceAndTitleFormatter.formatSourceName(targetSource))
					}
					showSourceFailoverPicker = false
				},
				onMergeSecondarySource = { _, mergedSource, _ ->
					isAutoFailoverEnabled = true
					onSelectSource(UiSourceAndTitleFormatter.formatSourceName(mergedSource))
				},
				onClose = { showSourceFailoverPicker = false },
			)
		}
	}
}

// =========================================================================
// TAB 3: SETTINGS (General | Paged | Filter SUB-TABS)
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NekoSettingsTabContent(
	readerConfig: ReaderCustomizationConfig,
	activeSubTab: NekoSettingsSubTab,
	onSelectSubTab: (NekoSettingsSubTab) -> Unit,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)

	Column(modifier = Modifier.fillMaxSize()) {
		// Top Sub-Tab Header: General | Paged | Filter
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(24.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			NekoSettingsSubTab.entries.forEach { tab ->
				val selected = activeSubTab == tab
				Column(
					modifier = Modifier
						.clickable { onSelectSubTab(tab) }
						.padding(vertical = 4.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					Text(
						text = tab.title,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
						color = if (selected) accentCyan else Color(0xFF9EA4B0),
					)
					Spacer(modifier = Modifier.height(4.dp))
					Box(
						modifier = Modifier
							.width(36.dp)
							.height(2.5.dp)
							.clip(CircleShape)
							.background(if (selected) accentCyan else Color.Transparent),
					)
				}
			}
		}

		HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 14.dp),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			when (activeSubTab) {
				NekoSettingsSubTab.GENERAL -> {
					// 1. Reading mode: Default, Left to right, Right to left, Vertical, Webtoon
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Reading mode",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						val readingModeOptions = listOf(
							"Default" to ReaderStyleMode.PAGER_RTL,
							"Left to right" to ReaderStyleMode.PAGER_LTR,
							"Right to left" to ReaderStyleMode.PAGER_RTL,
							"Vertical" to ReaderStyleMode.PAGER_VERTICAL,
							"Webtoon" to ReaderStyleMode.WEBTOON_CONTINUOUS,
						)
						var useDefaultReadingMode by remember { mutableStateOf(false) }

						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							readingModeOptions.forEachIndexed { idx, (label, styleMode) ->
								val isSelected = if (idx == 0) {
									useDefaultReadingMode
								} else {
									!useDefaultReadingMode && readerConfig.readingMode == styleMode
								}
								NekoPillOptionButton(
									label = label,
									selected = isSelected,
									onClick = {
										useDefaultReadingMode = (idx == 0)
										onUpdateReaderConfig { it.copy(readingMode = styleMode) }
									},
								)
							}
						}
					}

					// 2. Rotation: Default, Free, Portrait, Landscape, Locked portrait, Locked landscape
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Rotation",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							NekoRotationMode.entries.forEach { rotMode ->
								NekoPillOptionButton(
									label = rotMode.label,
									selected = readerConfig.rotationMode == rotMode,
									onClick = {
										onUpdateReaderConfig { it.copy(rotationMode = rotMode) }
									},
								)
							}
						}
					}

					// 3. Background color: White, Black, Smart (by page), Smart (by theme), Smart (by theme but black)
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Background color",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							NekoBackgroundColorMode.entries.forEach { bgMode ->
								NekoPillOptionButton(
									label = bgMode.label,
									selected = readerConfig.backgroundColorMode == bgMode,
									onClick = {
										onUpdateReaderConfig { it.copy(backgroundColorMode = bgMode) }
									},
								)
							}
						}
					}

					// 4. Slider position: Left align, Horizontal, Right align
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Slider position",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							SliderPosition.entries.forEach { pos ->
								NekoPillOptionButton(
									label = pos.label,
									selected = readerConfig.sliderPosition == pos,
									onClick = {
										onUpdateReaderConfig { it.copy(sliderPosition = pos) }
									},
								)
							}
						}
					}

					// 5. Show Zoom Buttons (🔍+ / 🔍-): ON / OFF toggle switch
					Surface(
						color = Color(0xFF21232C),
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier
							.fillMaxWidth()
							.testTag("reader_settings_show_zoom_buttons_row"),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(2.dp),
							) {
								Text(
									text = "Show Zoom Buttons (🔍+ / 🔍-)",
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold,
									color = Color.White,
								)
								Text(
									text = "Floating zoom pill aligned beneath vertical page scrubber",
									style = MaterialTheme.typography.labelSmall,
									color = Color(0xFF9EA4B0),
								)
							}
							Switch(
								checked = readerConfig.showZoomButtons,
								onCheckedChange = { enabled ->
									onUpdateReaderConfig { it.copy(showZoomButtons = enabled) }
								},
								colors = SwitchDefaults.colors(
									checkedThumbColor = Color(0xFF0A1014),
									checkedTrackColor = accentCyan,
								),
								modifier = Modifier.testTag("reader_sheet_toggle_zoom_buttons_switch"),
							)
						}
					}
				}

				NekoSettingsSubTab.PAGED -> {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Scale type",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							ReaderScaleMode.entries.forEach { scale ->
								NekoPillOptionButton(
									label = scale.label,
									selected = readerConfig.scaleMode == scale,
									onClick = {
										onUpdateReaderConfig { it.copy(scaleMode = scale) }
									},
								)
							}
						}
					}

					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Wide-page spreads",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							NekoWideSpreadMode.entries.forEach { mode ->
								NekoPillOptionButton(
									label = mode.label,
									selected = readerConfig.wideSpreadMode == mode,
									onClick = {
										onUpdateReaderConfig { it.copy(wideSpreadMode = mode) }
									},
								)
							}
						}
					}
				}

				NekoSettingsSubTab.FILTER -> {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = "Color filter preset",
							style = MaterialTheme.typography.labelLarge,
							color = Color(0xFFD5DAE5),
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							ReaderColorFilterPreset.entries.forEach { preset ->
								NekoPillOptionButton(
									label = preset.label,
									selected = readerConfig.colorFilterPreset == preset,
									onClick = {
										onUpdateReaderConfig { it.copy(colorFilterPreset = preset) }
									},
								)
							}
						}
					}

					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(
							text = "Brightness (${(readerConfig.brightnessDelta * 100).roundToInt()}%)",
							style = MaterialTheme.typography.labelMedium,
							color = Color(0xFFD5DAE5),
						)
						Slider(
							value = readerConfig.brightnessDelta,
							onValueChange = { v -> onUpdateReaderConfig { it.copy(brightnessDelta = v) } },
							valueRange = -0.4f..0.4f,
							colors = SliderDefaults.colors(
								thumbColor = accentCyan,
								activeTrackColor = accentCyan,
							),
						)
					}

					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(
							text = "Contrast (${(readerConfig.contrastDelta * 100).roundToInt()}%)",
							style = MaterialTheme.typography.labelMedium,
							color = Color(0xFFD5DAE5),
						)
						Slider(
							value = readerConfig.contrastDelta,
							onValueChange = { v -> onUpdateReaderConfig { it.copy(contrastDelta = v) } },
							valueRange = -0.3f..0.5f,
							colors = SliderDefaults.colors(
								thumbColor = accentCyan,
								activeTrackColor = accentCyan,
							),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NekoPillOptionButton(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	leadingCheckIcon: Boolean = false,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val bgColor = if (selected) accentCyan.copy(alpha = 0.20f) else Color(0xFF232630)
	val borderColor = if (selected) accentCyan else Color.White.copy(alpha = 0.12f)
	val textColor = if (selected) accentCyan else Color(0xFFE1E5EE)

	Surface(
		color = bgColor,
		shape = RoundedCornerShape(12.dp),
		modifier = modifier
			.border(1.dp, borderColor, RoundedCornerShape(12.dp))
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Center,
		) {
			if (leadingCheckIcon) {
				Icon(
					imageVector = Icons.Default.CheckCircle,
					contentDescription = null,
					tint = accentCyan,
					modifier = Modifier
						.padding(end = 4.dp)
						.size(14.dp),
				)
			}
			Text(
				text = label,
				fontSize = 12.sp,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = textColor,
				maxLines = 1,
				softWrap = false,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private fun decodeLocalMangaPageBitmap(
	mangaUrl: String,
	pageIndex: Int,
	wideSpreadMode: NekoWideSpreadMode,
	isRtl: Boolean,
): Bitmap? {
	return runCatching {
		val cleanPath = mangaUrl.removePrefix("file://")
		val f = File(cleanPath)
		if (!f.exists()) return@runCatching null

		val rawBitmap = if (f.isDirectory) {
			val imgFiles = f.walkTopDown()
				.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
				.sortedBy { it.name }
				.toList()
			if (imgFiles.isEmpty()) return@runCatching null
			val targetFile = imgFiles[pageIndex.coerceIn(0, imgFiles.lastIndex)]
			if (wideSpreadMode == NekoWideSpreadMode.SPLIT_INTO_TWO && WidePageProcessor.isWideSpread(targetFile)) {
				WidePageProcessor.decodeSplitHalf(
					imageFile = targetFile,
					isFirstHalfInReadingOrder = (pageIndex % 2 == 0),
					isRtlReading = isRtl,
				)
			} else {
				BitmapFactory.decodeFile(targetFile.absolutePath)
			}
		} else if (f.extension.lowercase() in setOf("cbz", "zip")) {
			ZipFile(f).use { zip ->
				val entries = zip.entries().asSequence()
					.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp") }
					.sortedBy { it.name }
					.toList()
				if (entries.isEmpty()) return@use null
				val targetEntry = entries[pageIndex.coerceIn(0, entries.lastIndex)]
				zip.getInputStream(targetEntry).use { stream ->
					BitmapFactory.decodeStream(stream)
				}
			}
		} else {
			null
		}
		val validBitmap = rawBitmap ?: return@runCatching null

		transformBitmapForSpreadMode(
			src = validBitmap,
			mode = wideSpreadMode,
			isRtl = isRtl,
			subHalf = pageIndex % 2,
		)
	}.getOrNull()
}

private fun transformBitmapForSpreadMode(
	src: Bitmap,
	mode: NekoWideSpreadMode,
	isRtl: Boolean,
	subHalf: Int,
): Bitmap {
	val isWide = src.width.toFloat() / src.height.toFloat().coerceAtLeast(1f) > 1.2f
	return when (mode) {
		NekoWideSpreadMode.NONE -> src
		NekoWideSpreadMode.AUTO_ROTATE_90 -> {
			if (!isWide) return src
			WidePageProcessor.rotateWideBitmap(src)
		}
		NekoWideSpreadMode.SPLIT_INTO_TWO -> {
			if (!isWide) return src
			val halfW = (src.width / 2).coerceAtLeast(1)
			val takeRightHalf = if (isRtl) (subHalf == 0) else (subHalf != 0)
			val startX = if (takeRightHalf) (src.width - halfW).coerceAtLeast(0) else 0
			Bitmap.createBitmap(src, startX, 0, halfW, src.height)
		}
	}
}

private fun fallbackSeriesAccentColor(title: String): Color {
	val hash = abs(title.hashCode())
	return when (hash % 7) {
		0 -> Color(0xFF3B82F6)
		1 -> Color(0xFFFF3366)
		2 -> Color(0xFF10B981)
		3 -> Color(0xFFF59E0B)
		4 -> Color(0xFF6366F1)
		5 -> Color(0xFFF43F5E)
		else -> Color(0xFF5CE1E6)
	}
}

private fun extractDominantEdgeColor(bitmap: Bitmap, fallbackTitle: String): Color {
	var rSum = 0L
	var gSum = 0L
	var bSum = 0L
	var count = 0L

	val w = bitmap.width
	val h = bitmap.height
	val stepX = (w / 16).coerceAtLeast(1)
	val stepY = (h / 16).coerceAtLeast(1)

	for (x in 0 until w step stepX) {
		val topPixel = bitmap.getPixel(x, 2.coerceAtMost(h - 1))
		val botPixel = bitmap.getPixel(x, (h - 3).coerceAtLeast(0))
		rSum += ((topPixel shr 16) and 0xFF) + ((botPixel shr 16) and 0xFF)
		gSum += ((topPixel shr 8) and 0xFF) + ((botPixel shr 8) and 0xFF)
		bSum += (topPixel and 0xFF) + (botPixel and 0xFF)
		count += 2
	}
	for (y in 0 until h step stepY) {
		val leftPixel = bitmap.getPixel(2.coerceAtMost(w - 1), y)
		val rightPixel = bitmap.getPixel((w - 3).coerceAtLeast(0), y)
		rSum += ((leftPixel shr 16) and 0xFF) + ((rightPixel shr 16) and 0xFF)
		gSum += ((leftPixel shr 8) and 0xFF) + ((rightPixel shr 8) and 0xFF)
		bSum += (leftPixel and 0xFF) + (rightPixel and 0xFF)
		count += 2
	}

	if (count == 0L) {
		return fallbackSeriesAccentColor(fallbackTitle)
	}

	val avgR = (rSum / count).toInt().coerceIn(0, 255)
	val avgG = (gSum / count).toInt().coerceIn(0, 255)
	val avgB = (bSum / count).toInt().coerceIn(0, 255)

	if (abs(avgR - avgG) < 8 && abs(avgG - avgB) < 8 && avgR > 210) {
		return fallbackSeriesAccentColor(fallbackTitle)
	}
	return Color(avgR, avgG, avgB)
}

private fun buildReaderColorFilter(config: ReaderCustomizationConfig): ColorFilter? {
	val isDefault = config.colorFilterPreset == ReaderColorFilterPreset.NONE &&
		abs(config.brightnessDelta) < 0.01f &&
		abs(config.contrastDelta) < 0.01f &&
		abs(config.warmthDelta) < 0.01f &&
		config.bedtimeDimmerAlpha < 0.01f &&
		abs(config.saturation - 1f) < 0.01f &&
		abs(config.redBalance - 1f) < 0.01f &&
		abs(config.greenBalance - 1f) < 0.01f &&
		abs(config.blueBalance - 1f) < 0.01f &&
		!config.invertColors &&
		!config.grayscale &&
		!config.warmPaperTint

	if (isDefault) return null

	val matrix = ColorMatrix()

	// Saturation / Grayscale
	val effectiveSat = when {
		config.grayscale || config.colorFilterPreset == ReaderColorFilterPreset.MONOCHROME_INK -> 0f
		else -> config.saturation
	}
	matrix.setToSaturation(effectiveSat)

	// Contrast & Brightness + RGB balance
	val c = 1f + config.contrastDelta +
		when (config.colorFilterPreset) {
			ReaderColorFilterPreset.CRISP_HIGH_CONTRAST -> 0.22f
			ReaderColorFilterPreset.AI_AUTO_INK_RESTORE -> 0.28f
			else -> 0f
		}
	val b = (config.brightnessDelta - config.bedtimeDimmerAlpha * 0.65f) * 255f

	var rScale = config.redBalance * c
	var gScale = config.greenBalance * c
	var bScale = config.blueBalance * c

	val warmth = config.warmthDelta +
		when {
			config.colorFilterPreset == ReaderColorFilterPreset.NEMURI_BEDTIME -> 0.45f
			config.warmPaperTint || config.colorFilterPreset == ReaderColorFilterPreset.WARM_SEPIA -> 0.30f
			else -> 0f
		}

	if (warmth > 0.01f) {
		rScale *= (1f + warmth * 0.08f)
		gScale *= (1f - warmth * 0.02f)
		bScale *= (1f - warmth * 0.26f)
	} else if (config.colorFilterPreset == ReaderColorFilterPreset.NIGHT_BLUE_LIGHT) {
		rScale *= 0.90f
		gScale *= 0.95f
		bScale *= 1.08f
	}

	val invert = config.invertColors || config.colorFilterPreset == ReaderColorFilterPreset.INVERTED_NIGHT
	val scaleMatrix = if (invert) {
		ColorMatrix(
			floatArrayOf(
				-rScale, 0f, 0f, 0f, 255f + b,
				0f, -gScale, 0f, 0f, 255f + b,
				0f, 0f, -bScale, 0f, 255f + b,
				0f, 0f, 0f, 1f, 0f,
			)
		)
	} else {
		val translate = (1f - c) * 128f + b
		ColorMatrix(
			floatArrayOf(
				rScale, 0f, 0f, 0f, translate,
				0f, gScale, 0f, 0f, translate,
				0f, 0f, bScale, 0f, translate,
				0f, 0f, 0f, 1f, 0f,
			)
		)
	}

	matrix.timesAssign(scaleMatrix)
	return ColorFilter.colorMatrix(matrix)
}

