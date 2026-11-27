package org.nemuri.app.reader

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.highlightText
import org.koitharu.kotatsu.core.image.AvifCapableImageDecoder
import org.koitharu.kotatsu.core.image.AvifCapableRegionDecoder
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.ReaderIntent
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ReaderScrubberEdge
import org.koitharu.kotatsu.core.prefs.ReaderTapZoneMode
import org.koitharu.kotatsu.core.prefs.SelectionMenuStyle
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isPdfUri
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.ReaderColorFilter
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.domain.UpscaleEffect
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.nemuri.app.MainActivity
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.browser.WebViewChallengeAction
import org.nemuri.app.browser.toBrowserIntent
import org.nemuri.app.browser.toWebViewChallengeAction
import org.nemuri.app.navigation.EXTRA_OPEN_MIGRATE_MANGA_ID
import org.nemuri.app.ui.components.HardwareTelemetryStrip
import org.nemuri.app.ui.components.NemuriCapsuleToastHost
import org.nemuri.app.ui.components.NemuriErrorState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriToastManager
import org.nemuri.app.security.AppLockManager
import org.nemuri.app.security.AppLockShieldOverlay
import org.nemuri.app.security.ObserveWindowSecurity
import org.nemuri.app.ui.theme.NemuriTheme
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReaderActivity : FragmentActivity() {

	@Inject
	lateinit var toastManager: NemuriToastManager

	private val viewModel: ReaderViewModel by viewModels()
	private var volumeKeyCallback: ((Boolean) -> Unit)? = null
	private var keepSystemBarsVisible: Boolean = false

	private fun applySystemBarsVisibility(visible: Boolean) {
		keepSystemBarsVisible = visible
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.systemBarsBehavior =
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		if (visible) {
			controller.show(WindowInsetsCompat.Type.systemBars())
		} else {
			controller.hide(WindowInsetsCompat.Type.systemBars())
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus && !keepSystemBarsVisible) {
			applySystemBarsVisibility(false)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			window.attributes = window.attributes.apply {
				layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
				} else {
					WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
				}
			}
		}
		applySystemBarsVisibility(false)

		setContent {
			NemuriTheme {
				val uiState by viewModel.uiState.collectAsStateWithLifecycle()
				val appLockManager = remember { AppLockManager.from(this@ReaderActivity) }
				val isAppLocked by appLockManager.isLocked.collectAsStateWithLifecycle()
				val isMangaNsfw = remember(uiState.manga) {
					val m = uiState.manga
					m != null && (m.contentRating == org.koitharu.kotatsu.parsers.model.ContentRating.ADULT ||
						m.tags.any { it.title.lowercase() in setOf("18+", "adult", "erotica", "pornographic", "smut", "hentai") })
				}

				ObserveWindowSecurity(
					isSessionIncognito = uiState.isIncognito,
					isNsfwContent = isMangaNsfw,
					isAppLocked = isAppLocked,
				)

				var manualOrientation by rememberSaveable {
					mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
				}
				val isLandscapeNow = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

				LaunchedEffect(manualOrientation) {
					requestedOrientation = manualOrientation
				}

				LaunchedEffect(uiState.keepScreenOn) {
					if (uiState.keepScreenOn) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					} else {
						window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					}
				}

				Box(modifier = Modifier.fillMaxSize()) {
					var readerWantsSystemBars by remember { mutableStateOf(false) }
					LaunchedEffect(readerWantsSystemBars, isAppLocked) {
						applySystemBarsVisibility(readerWantsSystemBars || isAppLocked)
					}
					ReaderRootScreen(
						uiState = uiState,
						toastManager = toastManager,
						viewModel = viewModel,
						isLandscape = isLandscapeNow,
						isOrientationLocked = manualOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
						onToggleManualRotate = {
							manualOrientation = if (isLandscapeNow) {
								ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
							} else {
								ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
							}
						},
						onResetAutoRotate = {
							manualOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
						},
						onSystemBarsVisibilityChange = { readerWantsSystemBars = it },
						onRegisterVolumeHandler = { volumeKeyCallback = it },
						onBack = { finish() },
					)
					AppLockShieldOverlay(
						isLocked = isAppLocked,
						onUnlocked = appLockManager::unlock,
					)
				}
			}
		}
	}

	override fun onPause() {
		viewModel.onPauseReading()
		super.onPause()
	}

	override fun onDestroy() {
		if (isFinishing) {
			viewModel.releaseUpscalerOnClose()
		}
		super.onDestroy()
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (viewModel.uiState.value.volumeButtonsEnabled && event.action == KeyEvent.ACTION_DOWN) {
			when (event.keyCode) {
				KeyEvent.KEYCODE_VOLUME_UP -> {
					volumeKeyCallback?.invoke(false)
					return true
				}
				KeyEvent.KEYCODE_VOLUME_DOWN -> {
					volumeKeyCallback?.invoke(true)
					return true
				}
			}
		}
		return super.dispatchKeyEvent(event)
	}

	companion object {

		fun newIntent(
			context: Context,
			manga: Manga,
			chapterId: Long? = null,
			page: Int = 0,
			scroll: Int = 0,
			branch: String? = null,
			incognito: Boolean = false,
		): Intent {
			val state = chapterId?.let { ReaderState(chapterId = it, page = page, scroll = scroll) }
			return ReaderIntent.Builder(context)
				.manga(manga)
				.apply {
					if (branch != null) branch(branch)
					if (state != null) state(state)
					if (incognito) incognito()
				}
				.build()
				.intent
		}

		fun newBookmarkIntent(context: Context, bookmark: Bookmark): Intent =
			ReaderIntent.Builder(context)
				.bookmark(bookmark)
				.build()
				.intent
	}
}

@Composable
private fun ReaderRootScreen(
	uiState: ReaderUiState,
	toastManager: NemuriToastManager,
	viewModel: ReaderViewModel,
	isLandscape: Boolean,
	isOrientationLocked: Boolean,
	onToggleManualRotate: () -> Unit,
	onResetAutoRotate: () -> Unit,
	onSystemBarsVisibilityChange: (Boolean) -> Unit,
	onRegisterVolumeHandler: (((Boolean) -> Unit)?) -> Unit,
	onBack: () -> Unit,
) {
	val context = LocalContext.current
	val haptic = LocalHapticFeedback.current
	var showHud by remember { mutableStateOf(false) }
	var showChaptersSheet by remember { mutableStateOf(false) }
	var showSettingsSheet by remember { mutableStateOf(false) }
	var quickActionPage by remember { mutableStateOf<ReaderPage?>(null) }
	var compareSplitPage by remember { mutableStateOf<ReaderPage?>(null) }

	LaunchedEffect(showHud, showChaptersSheet, showSettingsSheet, quickActionPage) {
		onSystemBarsVisibilityChange(
			showHud || showChaptersSheet || showSettingsSheet || quickActionPage != null,
		)
	}

	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { solved ->
		if (solved) viewModel.retry()
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { solved ->
		if (solved) viewModel.retry()
	}

	BoxWithConstraints(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black),
	) {
		val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
		val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
		var isDraggingScrubber by remember { mutableStateOf(false) }
		var previewSnapEdge by remember { mutableStateOf<ReaderScrubberEdge?>(null) }

		when {
			uiState.isLoading -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {
							detectTapGestures(onTap = { showHud = !showHud })
						},
					contentAlignment = Alignment.Center,
				) {
					NemuriLoadingState(message = "Opening reader…")
				}
			}

			uiState.pendingCloudFlare != null -> {
				val cf = uiState.pendingCloudFlare
				Box(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {
							detectTapGestures(onTap = { showHud = !showHud })
						},
				) {
					ReaderChallengeScreen(
						title = "Cloudflare check required",
						message = uiState.errorMessage ?: "Verify in WebView to load this chapter.",
						actionLabel = "Solve in WebView",
						onAction = { cfLauncher.launch(cf) },
						onRetry = viewModel::retry,
						onFindAlternative = uiState.manga?.id?.takeIf { it != 0L }?.let { id ->
							{ openMigrateFromReader(context, id) }
						},
						onBack = onBack,
					)
				}
			}

			uiState.pendingInteractive != null -> {
				val interactive = uiState.pendingInteractive
				Box(
					modifier = Modifier
						.fillMaxSize()
						.pointerInput(Unit) {
							detectTapGestures(onTap = { showHud = !showHud })
						},
				) {
					ReaderChallengeScreen(
						title = "Sign-in required",
						message = uiState.errorMessage ?: "Complete login or action in WebView, then retry.",
						actionLabel = "Solve in WebView",
						onAction = { browserLauncher.launch(interactive) },
						onRetry = viewModel::retry,
						onFindAlternative = uiState.manga?.id?.takeIf { it != 0L }?.let { id ->
							{ openMigrateFromReader(context, id) }
						},
						onBack = onBack,
					)
				}
			}

			uiState.errorMessage != null && !uiState.isChapterLoading &&
				((!uiState.isTextMode && uiState.pages.isEmpty()) || (uiState.isTextMode && uiState.novelBlocks.isEmpty())) -> {
				val challengeUrl = uiState.pendingChallengeUrl
				if (!challengeUrl.isNullOrBlank()) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.pointerInput(Unit) {
								detectTapGestures(onTap = { showHud = !showHud })
							},
					) {
						ReaderChallengeScreen(
							title = "HTTP 403 / Verification required",
							message = uiState.errorMessage,
							actionLabel = "Solve in WebView",
							onAction = {
								runCatching {
									context.startActivity(
										AppRouter.browserIntent(
											context,
											challengeUrl,
											uiState.manga?.source ?: UnknownMangaSource,
											uiState.title,
										),
									)
								}
							},
							onRetry = viewModel::retry,
							onFindAlternative = uiState.manga?.id?.takeIf { it != 0L }?.let { id ->
								{ openMigrateFromReader(context, id) }
							},
							onBack = onBack,
						)
					}
				} else {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.pointerInput(Unit) {
								detectTapGestures(onTap = { showHud = !showHud })
							}
							.statusBarsPadding(),
					) {
						if (!showHud) {
							IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
								Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
							}
						}
						NemuriErrorState(
							message = uiState.errorMessage,
							onRetry = viewModel::retry,
						)
					}
				}
			}

			uiState.isTextMode -> {
				NovelReaderViewport(
					uiState = uiState,
					viewModel = viewModel,
					onRegisterVolumeHandler = onRegisterVolumeHandler,
					onToggleHud = { showHud = !showHud },
				)
			}

			else -> {
				MangaReaderViewport(
					uiState = uiState,
					viewModel = viewModel,
					onRegisterVolumeHandler = onRegisterVolumeHandler,
					onToggleHud = { showHud = !showHud },
					onLongPressPage = { page ->
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						quickActionPage = page
					},
				)
			}
		}

		// Subtle non-blocking top loading indicator whenever reader or chapter is loading
		if (uiState.isLoading || uiState.isChapterLoading) {
			LinearProgressIndicator(
				color = NemuriTheme.colors.cyan,
				trackColor = Color.Transparent,
				modifier = Modifier
					.fillMaxWidth()
					.height(2.dp)
					.align(Alignment.TopCenter),
			)
		}

		// Edge-Snap Highlight Overlay while long-press dragging the 4-side scrubber
		if (isDraggingScrubber && previewSnapEdge != null) {
			ReaderScrubberEdgeSnapOverlay(targetEdge = previewSnapEdge!!)
		}

		// Top Chapter / Source Bar (always obeys showHud immediately, even while loading!)
		AnimatedVisibility(
			visible = showHud,
			enter = fadeIn() + slideInVertically { -it },
			exit = fadeOut() + slideOutVertically { -it },
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			Surface(
				color = NemuriTheme.colors.background.copy(alpha = 0.90f),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.statusBarsPadding()
						.padding(horizontal = 6.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back",
							tint = NemuriTheme.colors.textPrimary,
						)
					}
					Column(
						modifier = Modifier
							.weight(1f)
							.padding(horizontal = 4.dp),
						verticalArrangement = Arrangement.spacedBy(2.dp),
					) {
						Text(
							text = uiState.title,
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.SemiBold,
							color = NemuriTheme.colors.textPrimary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							val sourceAndChapter = remember(uiState.sourceTitle, uiState.chapterTitle) {
								when {
									uiState.sourceTitle.isNotBlank() && uiState.chapterTitle.isNotBlank() ->
										"${uiState.sourceTitle} • ${uiState.chapterTitle}"
									uiState.chapterTitle.isNotBlank() -> uiState.chapterTitle
									else -> uiState.sourceTitle
								}
							}
							Text(
								text = sourceAndChapter,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textSecondary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f, fill = false),
							)
							if (uiState.isIncognito) {
								Surface(
									color = NemuriTheme.colors.cyan.copy(alpha = 0.18f),
									shape = RoundedCornerShape(999.dp),
									border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.55f)),
								) {
									Text(
										text = "Incognito",
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Bold,
										color = NemuriTheme.colors.cyan,
										modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
									)
								}
							}
						}
					}
					IconButton(onClick = viewModel::toggleBookmark) {
						Icon(
							imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
							contentDescription = "Bookmark",
							tint = if (uiState.isBookmarked) NemuriTheme.colors.amber else NemuriTheme.colors.textSecondary,
						)
					}
					val manga = uiState.manga
					if (manga != null && manga.publicUrl.startsWith("http")) {
						IconButton(
							onClick = {
								runCatching {
									context.startActivity(
										AppRouter.browserIntent(context, manga.publicUrl, manga.source, manga.title),
									)
								}
							},
						) {
							Icon(
								imageVector = Icons.Default.OpenInBrowser,
								contentDescription = "Open in WebView",
								tint = NemuriTheme.colors.textSecondary,
							)
						}
					}
				}
			}
		}

		// 4-Side Movable Capsule Scrubber (Right | Left | Bottom | Top)
		val scrubberVisible = showHud && !uiState.isLoading && !showChaptersSheet && !showSettingsSheet
		val scrubberItemCount = if (uiState.isTextMode) {
			uiState.novelBlocks.size.coerceAtLeast(1)
		} else {
			uiState.chapterLogicalCount.coerceAtLeast(uiState.pages.size).coerceAtLeast(1)
		}
		val scrubberCurrentIdx = if (uiState.isTextMode) {
			uiState.novelCurrentBlockIndex.coerceIn(0, scrubberItemCount - 1)
		} else {
			(uiState.currentPageIndex - uiState.chapterStartGlobalIndex).coerceIn(0, scrubberItemCount - 1)
		}
		val currentDisplayNum = if (uiState.isTextMode) {
			(scrubberCurrentIdx + 1).coerceAtMost(scrubberItemCount)
		} else {
			uiState.chapterStartPageNumber.coerceAtLeast(1)
		}
		val totalDisplayNum = if (uiState.isTextMode) {
			scrubberItemCount
		} else {
			uiState.chapterTotalPages.coerceAtLeast(1)
		}
		val isScrubberRtl = !uiState.isTextMode && uiState.readerMode == ReaderMode.REVERSED

		val scrubberAlignment = when (uiState.scrubberEdge) {
			ReaderScrubberEdge.RIGHT -> Alignment.CenterEnd
			ReaderScrubberEdge.LEFT -> Alignment.CenterStart
			ReaderScrubberEdge.BOTTOM -> Alignment.BottomCenter
			ReaderScrubberEdge.TOP -> Alignment.TopCenter
		}
		val scrubberEdgeModifier = when (uiState.scrubberEdge) {
			ReaderScrubberEdge.RIGHT -> Modifier
				.align(scrubberAlignment)
				.padding(end = 12.dp, top = 76.dp, bottom = 84.dp)
			ReaderScrubberEdge.LEFT -> Modifier
				.align(scrubberAlignment)
				.padding(start = 12.dp, top = 76.dp, bottom = 84.dp)
			ReaderScrubberEdge.BOTTOM -> Modifier
				.align(scrubberAlignment)
				.navigationBarsPadding()
				.padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
			ReaderScrubberEdge.TOP -> Modifier
				.align(scrubberAlignment)
				.statusBarsPadding()
				.padding(start = 16.dp, end = 16.dp, top = 64.dp)
		}

		AnimatedVisibility(
			visible = scrubberVisible,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = scrubberEdgeModifier,
		) {
			ReaderMovableCapsuleScrubber(
				edge = uiState.scrubberEdge,
				currentIndex = scrubberCurrentIdx,
				itemCount = scrubberItemCount,
				currentDisplayNumber = currentDisplayNum,
				totalDisplayNumber = totalDisplayNum,
				isRtl = isScrubberRtl,
				canGoPrevChapter = uiState.hasPrevChapter,
				canGoNextChapter = uiState.hasNextChapter,
				screenWidthPx = screenWidthPx,
				screenHeightPx = screenHeightPx,
				onSelectIndex = { idx ->
					if (uiState.isTextMode) {
						viewModel.jumpToNovelBlock(idx)
					} else {
						viewModel.jumpToMangaPage(uiState.chapterStartGlobalIndex + idx)
					}
				},
				onPrevChapter = { viewModel.goToAdjacentChapter(isNext = false) },
				onNextChapter = { viewModel.goToAdjacentChapter(isNext = true) },
				onDragStateChange = { dragging, candidate ->
					isDraggingScrubber = dragging
					previewSnapEdge = candidate
				},
				onSnapEdge = { newEdge ->
					viewModel.setScrubberEdge(newEdge)
				},
			)
		}

		// Floating Auto-Scroll Play / Pause & Adjustable Speed Pill
		AnimatedVisibility(
			visible = uiState.isAutoScrollEnabled,
			enter = fadeIn() + slideInVertically { it },
			exit = fadeOut() + slideOutVertically { it },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(
					bottom = if (showHud) {
						if (uiState.scrubberEdge == ReaderScrubberEdge.BOTTOM) 134.dp else 76.dp
					} else {
						14.dp
					},
				),
		) {
			val isContinuousScroll = uiState.isTextMode || uiState.readerMode == ReaderMode.WEBTOON
			val canSlowDown = if (isContinuousScroll) {
				uiState.autoScrollSpeed > 0.055f
			} else {
				uiState.autoScrollPageDelaySec < 10
			}
			val canSpeedUp = if (isContinuousScroll) {
				uiState.autoScrollSpeed < 0.995f
			} else {
				uiState.autoScrollPageDelaySec > 1
			}
			Surface(
				color = NemuriTheme.colors.dock.copy(alpha = 0.95f),
				shape = RoundedCornerShape(999.dp),
				border = BorderStroke(
					1.dp,
					if (uiState.isAutoScrollPaused) NemuriTheme.colors.amber.copy(alpha = 0.6f) else NemuriTheme.colors.cyan.copy(alpha = 0.6f),
				),
				shadowElevation = 12.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Surface(
						color = if (uiState.isAutoScrollPaused) {
							NemuriTheme.colors.amber.copy(alpha = 0.18f)
						} else {
							NemuriTheme.colors.tealContainer
						},
						shape = CircleShape,
						modifier = Modifier
							.size(32.dp)
							.clip(CircleShape)
							.clickable { viewModel.setAutoScrollPaused(!uiState.isAutoScrollPaused) },
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = if (uiState.isAutoScrollPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
								contentDescription = if (uiState.isAutoScrollPaused) "Resume auto-scroll" else "Pause auto-scroll",
								tint = if (uiState.isAutoScrollPaused) NemuriTheme.colors.amber else NemuriTheme.colors.cyan,
								modifier = Modifier.size(17.dp),
							)
						}
					}
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = CircleShape,
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
						modifier = Modifier
							.size(28.dp)
							.clip(CircleShape)
							.clickable(enabled = canSlowDown) {
								if (isContinuousScroll) {
									viewModel.setAutoScrollSpeed(uiState.autoScrollSpeed - 0.1f)
								} else {
									viewModel.setAutoScrollPageDelaySec(uiState.autoScrollPageDelaySec + 1)
								}
							},
					) {
						Box(contentAlignment = Alignment.Center) {
							Text(
								text = "−",
								fontSize = 16.sp,
								fontWeight = FontWeight.Bold,
								color = if (canSlowDown) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
							)
						}
					}
					Text(
						text = if (isContinuousScroll) {
							val mult = (uiState.autoScrollSpeed * 2f).coerceAtLeast(0.1f)
							val speedLabel = String.format(java.util.Locale.US, "%.1fx", mult)
							if (uiState.isAutoScrollPaused) "Paused • $speedLabel" else "Auto $speedLabel"
						} else {
							val delayLabel = "${uiState.autoScrollPageDelaySec}s/p"
							if (uiState.isAutoScrollPaused) "Paused • $delayLabel" else "Auto $delayLabel"
						},
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = if (uiState.isAutoScrollPaused) NemuriTheme.colors.amber else NemuriTheme.colors.cyan,
						modifier = Modifier.padding(horizontal = 4.dp),
					)
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = CircleShape,
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
						modifier = Modifier
							.size(28.dp)
							.clip(CircleShape)
							.clickable(enabled = canSpeedUp) {
								if (isContinuousScroll) {
									viewModel.setAutoScrollSpeed(uiState.autoScrollSpeed + 0.1f)
								} else {
									viewModel.setAutoScrollPageDelaySec(uiState.autoScrollPageDelaySec - 1)
								}
							},
					) {
						Box(contentAlignment = Alignment.Center) {
							Text(
								text = "+",
								fontSize = 15.sp,
								fontWeight = FontWeight.Bold,
								color = if (canSpeedUp) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
							)
						}
					}
					IconButton(
						onClick = { viewModel.setAutoScrollEnabled(false) },
						modifier = Modifier.size(28.dp),
					) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Stop auto-scroll",
							tint = NemuriTheme.colors.textMuted,
							modifier = Modifier.size(15.dp),
						)
					}
				}
			}
		}

		// Minimal Bottom HUD Pill (Prev Chapter | Next Chapter | 3-Tab Navigator | Reader & Enhance Sheet)
		AnimatedVisibility(
			visible = showHud,
			enter = fadeIn() + slideInVertically { it },
			exit = fadeOut() + slideOutVertically { it },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(horizontal = 16.dp, vertical = 12.dp),
		) {
			val isEnhanceActive = !uiState.isTextMode && (
				uiState.shaderConfig.isActive ||
					!uiState.colorFilter.isEmpty ||
					uiState.colorWarmth > 0.01f ||
					(uiState.isNeuralUpscaleEnabled && uiState.hasReadyNeuralModel)
				)
			Surface(
				color = NemuriTheme.colors.dock.copy(alpha = 0.95f),
				shape = RoundedCornerShape(999.dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				shadowElevation = 14.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					IconButton(
						onClick = { viewModel.goToAdjacentChapter(isNext = false) },
						enabled = uiState.hasPrevChapter,
						modifier = Modifier.size(40.dp),
					) {
						Icon(
							imageVector = Icons.Default.SkipPrevious,
							contentDescription = "Previous chapter",
							tint = if (uiState.hasPrevChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
							modifier = Modifier.size(20.dp),
						)
					}

					IconButton(
						onClick = { viewModel.goToAdjacentChapter(isNext = true) },
						enabled = uiState.hasNextChapter,
						modifier = Modifier.size(40.dp),
					) {
						Icon(
							imageVector = Icons.Default.SkipNext,
							contentDescription = "Next chapter",
							tint = if (uiState.hasNextChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
							modifier = Modifier.size(20.dp),
						)
					}

					Box(
						modifier = Modifier
							.width(1.dp)
							.height(22.dp)
							.background(NemuriTheme.colors.border),
					)

					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.clickable { showChaptersSheet = true },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Icon(
								imageVector = Icons.Default.FormatListBulleted,
								contentDescription = "Chapters",
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(16.dp),
							)
							Text(
								text = "Ch (${uiState.chapters.size})",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.textPrimary,
								maxLines = 1,
							)
						}
					}

					Surface(
						color = if (isEnhanceActive) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						border = if (isEnhanceActive) BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)) else null,
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.clickable { showSettingsSheet = true },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Box {
								Icon(
									imageVector = Icons.Default.Tune,
									contentDescription = "Reader & Enhance Tools",
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(16.dp),
								)
								if (isEnhanceActive) {
									Box(
										modifier = Modifier
											.align(Alignment.TopEnd)
											.offset(x = 2.dp, y = (-2).dp)
											.size(6.dp)
											.background(NemuriTheme.colors.cyan, CircleShape),
									)
								}
							}
							Text(
								text = if (uiState.isTextMode) "Style" else "Mode & Tools",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = if (isEnhanceActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								maxLines = 1,
							)
						}
					}
				}
			}
		}

		if (!showHud && !uiState.isLoading && !uiState.isTextMode && uiState.showPageNumbers && uiState.chapterTotalPages > 0) {
			Surface(
				color = Color.Black.copy(alpha = 0.56f),
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.navigationBarsPadding()
					.padding(end = 14.dp, bottom = 12.dp),
			) {
				val label = if (uiState.chapterEndPageNumber > uiState.chapterStartPageNumber) {
					"${uiState.chapterStartPageNumber}–${uiState.chapterEndPageNumber} / ${uiState.chapterTotalPages}"
				} else {
					"${uiState.chapterStartPageNumber} / ${uiState.chapterTotalPages}"
				}
				Text(
					text = label,
					style = MaterialTheme.typography.labelSmall,
					color = Color.White.copy(alpha = 0.9f),
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
				)
			}
		}

		NemuriCapsuleToastHost(
			toastManager = toastManager,
			bottomPadding = if (showHud) {
				if (uiState.scrubberEdge == ReaderScrubberEdge.BOTTOM) 140.dp else 84.dp
			} else {
				24.dp
			},
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}

	if (showChaptersSheet) {
		ReaderNavigatorModalSheet(
			uiState = uiState,
			viewModel = viewModel,
			onDismiss = { showChaptersSheet = false },
		)
	}

	val activeQuickPage = quickActionPage
	if (activeQuickPage != null) {
		ReaderPageQuickSheet(
			page = activeQuickPage,
			uiState = uiState,
			viewModel = viewModel,
			onOpenCompareSplit = {
				quickActionPage = null
				compareSplitPage = activeQuickPage
			},
			onDismiss = { quickActionPage = null },
		)
	}

	val activeComparePage = compareSplitPage
	if (activeComparePage != null) {
		ShaderSplitPreviewDialog(
			page = activeComparePage,
			config = uiState.shaderConfig,
			isNeuralActive = uiState.isNeuralUpscaleEnabled && uiState.hasReadyNeuralModel,
			neuralModelLabel = uiState.activeNeuralModelLabel,
			neuralRevision = uiState.neuralRevision,
			loadOriginalPage = viewModel::loadRawPageImage,
			loadEnhancedPage = viewModel::loadPageImage,
			onDismiss = { compareSplitPage = null },
		)
	}

	if (showSettingsSheet) {
		ReaderSettingsModalSheet(
			uiState = uiState,
			viewModel = viewModel,
			isLandscape = isLandscape,
			isOrientationLocked = isOrientationLocked,
			onToggleManualRotate = onToggleManualRotate,
			onResetAutoRotate = onResetAutoRotate,
			onAutoScrollStarted = {
				showSettingsSheet = false
				showHud = false
			},
			onDismiss = { showSettingsSheet = false },
		)
	}
}

private enum class TapZoneAction {
	PREV,
	NEXT,
	TOGGLE_HUD,
}

private fun resolveTapZoneAction(
	normX: Float,
	normY: Float,
	mode: ReaderTapZoneMode,
	isRtl: Boolean,
	isWebtoon: Boolean,
): TapZoneAction {
	val horizontalAction = when (mode) {
		ReaderTapZoneMode.DEFAULT -> when {
			normX < 0.33f -> -1
			normX > 0.67f -> 1
			else -> 0
		}

		ReaderTapZoneMode.L_SHAPED -> when {
			normY < 0.25f -> -1
			normY > 0.75f -> 1
			normX < 0.33f -> -1
			normX > 0.67f -> 1
			else -> 0
		}

		ReaderTapZoneMode.KINDLE -> when {
			normY < 0.18f -> 0
			normX < 0.24f -> -1
			else -> 1
		}

		ReaderTapZoneMode.EDGE_ONLY -> when {
			normX < 0.16f -> -1
			normX > 0.84f -> 1
			else -> 0
		}
	}
	if (horizontalAction == 0) return TapZoneAction.TOGGLE_HUD
	val flip = isRtl && !isWebtoon
	val effective = if (flip) -horizontalAction else horizontalAction
	return if (effective < 0) TapZoneAction.PREV else TapZoneAction.NEXT
}

@Composable
private fun MangaReaderViewport(
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	onRegisterVolumeHandler: (((Boolean) -> Unit)?) -> Unit,
	onToggleHud: () -> Unit,
	onLongPressPage: (ReaderPage) -> Unit,
) {
	val pages = uiState.pages
	val latestOnToggleHud by rememberUpdatedState(onToggleHud)
	val latestOnLongPressPage by rememberUpdatedState(onLongPressPage)
	if (pages.isEmpty()) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(Unit) {
					detectTapGestures(onTap = { latestOnToggleHud() })
				},
			contentAlignment = Alignment.Center,
		) {
			if (uiState.isChapterLoading) {
				NemuriLoadingState(message = "Loading pages…")
			}
		}
		return
	}

	val context = LocalContext.current
	val density = LocalDensity.current
	val coroutineScope = rememberCoroutineScope()
	val migrateAction: (() -> Unit)? = uiState.manga?.id?.takeIf { it != 0L }?.let { id ->
		{ openMigrateFromReader(context, id) }
	}
	val bitmapConfig = if (uiState.is32BitColors) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
	val currentTapZoneMode by rememberUpdatedState(uiState.tapZoneMode)
	val currentSmartZoom by rememberUpdatedState(uiState.isDoubleTapSmartZoom)
	val latestPages by rememberUpdatedState(pages)

	if (uiState.readerMode == ReaderMode.WEBTOON) {
		val listState = rememberLazyListState()
		var webtoonZoom by remember { mutableStateOf(1f) }
		var webtoonPanX by remember { mutableStateOf(0f) }

		LaunchedEffect(uiState.isAutoScrollEnabled, uiState.isAutoScrollPaused, uiState.autoScrollSpeed) {
			if (!uiState.isAutoScrollEnabled || uiState.isAutoScrollPaused) return@LaunchedEffect
			var prevNanos = withFrameNanos { it }
			while (true) {
				val nowNanos = withFrameNanos { it }
				val dtSec = ((nowNanos - prevNanos).coerceIn(0L, 64_000_000L)) / 1_000_000_000f
				prevNanos = nowNanos
				if (!listState.isScrollInProgress && listState.canScrollForward) {
					val pxPerSec = with(density) { (52f + uiState.autoScrollSpeed * 380f).dp.toPx() }
					listState.dispatchRawDelta(pxPerSec * dtSec)
				}
			}
		}

		LaunchedEffect(uiState.pageJumpToken) {
			if (uiState.currentPageIndex in pages.indices) {
				listState.scrollToItem(uiState.currentPageIndex)
			}
		}

		LaunchedEffect(listState, pages) {
			snapshotFlow { listState.firstVisibleItemIndex }
				.distinctUntilChanged()
				.collect { index ->
					if (index in pages.indices) {
						viewModel.onMangaPageSelected(index)
					}
				}
		}

		LaunchedEffect(listState, pages) {
			onRegisterVolumeHandler { forward ->
				val target = (listState.firstVisibleItemIndex + if (forward) 1 else -1)
					.coerceIn(0, pages.lastIndex)
				coroutineScope.launch { listState.animateScrollToItem(target) }
			}
		}

		fun handleWebtoonTap(normX: Float, normY: Float) {
			if (uiState.isChapterLoading) {
				latestOnToggleHud()
				return
			}
			when (resolveTapZoneAction(normX, normY, currentTapZoneMode, isRtl = false, isWebtoon = true)) {
				TapZoneAction.TOGGLE_HUD -> latestOnToggleHud()
				TapZoneAction.PREV -> {
					val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
					coroutineScope.launch { listState.animateScrollToItem(target) }
				}
				TapZoneAction.NEXT -> {
					val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.lastIndex)
					coroutineScope.launch { listState.animateScrollToItem(target) }
				}
			}
		}

		BoxWithConstraints(
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(Unit) {
					awaitEachGesture {
						do {
							val event = awaitPointerEvent()
							if (event.changes.size >= 2) {
								val zoomChange = event.calculateZoom()
								val pan = event.calculatePan()
								val nextZoom = (webtoonZoom * zoomChange).coerceIn(1f, 2.8f)
								val maxPan = ((nextZoom - 1f) * size.width * 0.5f).coerceAtLeast(0f)
								webtoonZoom = nextZoom
								webtoonPanX = if (nextZoom <= 1.02f) 0f else (webtoonPanX + pan.x).coerceIn(-maxPan, maxPan)
								event.changes.forEach { it.consume() }
							} else if (webtoonZoom > 1.04f) {
								val pan = event.calculatePan()
								if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y) * 1.15f) {
									val maxPan = ((webtoonZoom - 1f) * size.width * 0.5f).coerceAtLeast(0f)
									webtoonPanX = (webtoonPanX + pan.x).coerceIn(-maxPan, maxPan)
									event.changes.forEach { it.consume() }
								}
							}
						} while (event.changes.any { it.pressed })
					}
				}
				.pointerInput(currentTapZoneMode, currentSmartZoom) {
					detectTapGestures(
						onDoubleTap = if (currentSmartZoom) {
							{ offset ->
								if (webtoonZoom > 1.15f) {
									webtoonZoom = 1f
									webtoonPanX = 0f
								} else {
									val targetZoom = 2.0f
									val maxPan = ((targetZoom - 1f) * size.width * 0.5f).coerceAtLeast(0f)
									val focalPan = (size.width * 0.5f - offset.x) * (targetZoom - 1f)
									webtoonZoom = targetZoom
									webtoonPanX = focalPan.coerceIn(-maxPan, maxPan)
								}
							}
						} else {
							null
						},
						onLongPress = { offset ->
							val unscaledY = if (webtoonZoom > 1.01f && size.height > 0) {
								(offset.y - size.height * 0.5f) / webtoonZoom + size.height * 0.5f
							} else {
								offset.y
							}
							val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
								unscaledY.toInt() in info.offset..(info.offset + info.size)
							}
							val idx = (hit?.index ?: listState.firstVisibleItemIndex)
								.coerceIn(0, (latestPages.size - 1).coerceAtLeast(0))
							latestPages.getOrNull(idx)?.let(latestOnLongPressPage)
						},
						onTap = { offset ->
							val nx = if (size.width > 0) offset.x / size.width.toFloat() else 0.5f
							val ny = if (size.height > 0) offset.y / size.height.toFloat() else 0.5f
							handleWebtoonTap(nx, ny)
						},
					)
				},
		) {
			val zoomInset = maxWidth * (uiState.defaultWebtoonZoomOutPercent.coerceIn(0, 50) / 200f)
			val stripGap = if (uiState.isWebtoonGapsEnabled) 12.dp else 0.dp
			LazyColumn(
				state = listState,
				verticalArrangement = Arrangement.spacedBy(stripGap),
				contentPadding = PaddingValues(horizontal = zoomInset),
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						scaleX = webtoonZoom
						scaleY = webtoonZoom
						translationX = webtoonPanX
					},
			) {
				items(pages, key = { it.id }) { page ->
					SsivPageView(
						page = page,
						bitmapConfig = bitmapConfig,
						isWebtoon = true,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						colorFilter = uiState.colorFilter,
						colorWarmth = uiState.colorWarmth,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = ::handleWebtoonTap,
						onLongPress = { latestOnLongPressPage(page) },
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier.fillMaxWidth(),
					)
				}

				item(key = "webtoon_footer") {
					ChapterTransitionFooter(
						hasNext = uiState.hasNextChapter,
						onNext = { viewModel.goToAdjacentChapter(isNext = true) },
					)
				}
			}
		}
	} else {
		val config = LocalConfiguration.current
		val isLandscapeViewport = config.screenWidthDp > config.screenHeightDp
		val minDimDp = minOf(config.screenWidthDp, config.screenHeightDp)
		val maxDimDp = maxOf(config.screenWidthDp, config.screenHeightDp)
		val isFoldableUnfolded = minDimDp >= 580 && (maxDimDp.toFloat() / minDimDp.toFloat().coerceAtLeast(1f)) < 1.65f
		val useDoublePages = (uiState.readerMode == ReaderMode.STANDARD || uiState.readerMode == ReaderMode.REVERSED) &&
			((isLandscapeViewport && uiState.isDoubleOnLandscape) || (isFoldableUnfolded && uiState.isDoubleOnFoldable))

		val spreadSlots = remember(
			pages,
			useDoublePages,
			uiState.boundsRevision,
			uiState.doublePagesSensitivity,
		) {
			if (!useDoublePages) {
				pages.indices.map { idx -> PagerSpreadSlot(idx, null) }
			} else {
				val wideThreshold = 1.35f - (uiState.doublePagesSensitivity.coerceIn(0f, 1f) * 0.35f)
				fun isWide(page: ReaderPage): Boolean {
					val size = viewModel.getCachedPageSize(page.id) ?: return false
					return size.height > 0 && (size.width.toFloat() / size.height.toFloat()) > wideThreshold
				}
				val built = ArrayList<PagerSpreadSlot>(pages.size)
				var i = 0
				while (i < pages.size) {
					val cur = pages[i]
					val next = pages.getOrNull(i + 1)
					if (next != null &&
						cur.chapterId == next.chapterId &&
						!isWide(cur) &&
						!isWide(next)
					) {
						built += PagerSpreadSlot(i, i + 1)
						i += 2
					} else {
						built += PagerSpreadSlot(i, null)
						i += 1
					}
				}
				built
			}
		}

		val currentSlotIndex = remember(spreadSlots, uiState.currentPageIndex) {
			spreadSlots.indexOfFirst { slot ->
				slot.primaryIndex == uiState.currentPageIndex || slot.secondaryIndex == uiState.currentPageIndex
			}.coerceAtLeast(0).coerceAtMost((spreadSlots.size - 1).coerceAtLeast(0))
		}

		val pagerState = rememberPagerState(
			initialPage = currentSlotIndex,
			pageCount = { spreadSlots.size },
		)
		val isRtl = uiState.readerMode == ReaderMode.REVERSED

		LaunchedEffect(uiState.pageJumpToken, spreadSlots.size) {
			val target = spreadSlots.indexOfFirst { slot ->
				slot.primaryIndex == uiState.currentPageIndex || slot.secondaryIndex == uiState.currentPageIndex
			}.coerceIn(0, (spreadSlots.size - 1).coerceAtLeast(0))
			if (pagerState.currentPage != target) {
				pagerState.scrollToPage(target)
			}
		}

		LaunchedEffect(pagerState, spreadSlots) {
			snapshotFlow { pagerState.currentPage }
				.distinctUntilChanged()
				.collect { slotIdx ->
					val slot = spreadSlots.getOrNull(slotIdx) ?: return@collect
					if (slot.primaryIndex in pages.indices) {
						viewModel.onMangaPageSelected(slot.primaryIndex)
					}
				}
		}

		LaunchedEffect(pagerState, spreadSlots) {
			onRegisterVolumeHandler { forward ->
				val delta = if (forward) 1 else -1
				val target = (pagerState.currentPage + delta).coerceIn(0, (spreadSlots.size - 1).coerceAtLeast(0))
				coroutineScope.launch { pagerState.animateScrollToPage(target) }
			}
		}

		LaunchedEffect(
			uiState.isAutoScrollEnabled,
			uiState.isAutoScrollPaused,
			uiState.autoScrollPageDelaySec,
			pagerState.currentPage,
			spreadSlots.size,
		) {
			if (!uiState.isAutoScrollEnabled || uiState.isAutoScrollPaused || spreadSlots.isEmpty()) return@LaunchedEffect
			delay(uiState.autoScrollPageDelaySec.coerceIn(1, 10) * 1000L)
			if (!pagerState.isScrollInProgress) {
				val next = pagerState.currentPage + 1
				if (next <= spreadSlots.lastIndex) {
					pagerState.animateScrollToPage(next)
				} else if (uiState.hasNextChapter) {
					viewModel.goToAdjacentChapter(isNext = true)
				} else {
					viewModel.setAutoScrollEnabled(false)
				}
			}
		}

		fun handlePagedTap(normX: Float, normY: Float) {
			if (uiState.isChapterLoading) {
				latestOnToggleHud()
				return
			}
			when (resolveTapZoneAction(normX, normY, currentTapZoneMode, isRtl = isRtl, isWebtoon = false)) {
				TapZoneAction.TOGGLE_HUD -> latestOnToggleHud()
				TapZoneAction.PREV -> {
					val target = pagerState.currentPage - 1
					if (target >= 0) {
						coroutineScope.launch { pagerState.animateScrollToPage(target) }
					} else if (uiState.hasPrevChapter) {
						viewModel.goToAdjacentChapter(isNext = false)
					} else {
						latestOnToggleHud()
					}
				}
				TapZoneAction.NEXT -> {
					val target = pagerState.currentPage + 1
					if (target <= spreadSlots.lastIndex) {
						coroutineScope.launch { pagerState.animateScrollToPage(target) }
					} else if (uiState.hasNextChapter) {
						viewModel.goToAdjacentChapter(isNext = true)
					} else {
						latestOnToggleHud()
					}
				}
			}
		}

		@Composable
		fun RenderSpreadSlot(slotIdx: Int) {
			val slot = spreadSlots.getOrNull(slotIdx) ?: return
			val firstPage = pages.getOrNull(slot.primaryIndex) ?: return
			val secondPage = slot.secondaryIndex?.let { pages.getOrNull(it) }
			if (secondPage == null) {
				SsivPageView(
					page = firstPage,
					bitmapConfig = bitmapConfig,
					isWebtoon = false,
					isDoubleTapSmartZoom = currentSmartZoom,
					shaderConfig = uiState.shaderConfig,
					colorFilter = uiState.colorFilter,
					colorWarmth = uiState.colorWarmth,
					neuralRevision = uiState.neuralRevision,
					loadPage = viewModel::loadPageImage,
					onTapNormalized = ::handlePagedTap,
					onLongPress = { latestOnLongPressPage(firstPage) },
					fallbackUrl = uiState.manga?.publicUrl,
					onFindAlternative = migrateAction,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				val leftPage = if (isRtl) secondPage else firstPage
				val rightPage = if (isRtl) firstPage else secondPage
				Row(
					modifier = Modifier.fillMaxSize(),
					horizontalArrangement = Arrangement.spacedBy(2.dp),
				) {
					SsivPageView(
						page = leftPage,
						bitmapConfig = bitmapConfig,
						isWebtoon = false,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						colorFilter = uiState.colorFilter,
						colorWarmth = uiState.colorWarmth,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = { nx, ny -> handlePagedTap(nx * 0.5f, ny) },
						onLongPress = { latestOnLongPressPage(leftPage) },
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight(),
					)
					SsivPageView(
						page = rightPage,
						bitmapConfig = bitmapConfig,
						isWebtoon = false,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						colorFilter = uiState.colorFilter,
						colorWarmth = uiState.colorWarmth,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = { nx, ny -> handlePagedTap(0.5f + nx * 0.5f, ny) },
						onLongPress = { latestOnLongPressPage(rightPage) },
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight(),
					)
				}
			}
		}

		when (uiState.readerMode) {
			ReaderMode.VERTICAL -> {
				VerticalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { slotIdx ->
					RenderSpreadSlot(slotIdx)
				}
			}

			ReaderMode.REVERSED -> {
				HorizontalPager(
					state = pagerState,
					reverseLayout = true,
					modifier = Modifier.fillMaxSize(),
				) { slotIdx ->
					RenderSpreadSlot(slotIdx)
				}
			}

			else -> {
				HorizontalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { slotIdx ->
					RenderSpreadSlot(slotIdx)
				}
			}
		}
	}
}

private class ReaderSubsamplingImageView(
	context: Context,
	var isWebtoonMode: Boolean,
) : SubsamplingScaleImageView(context) {
	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		// Never swallow touches in Webtoon mode (parent Compose viewport owns scroll, zoom, tap, and long-press)
		// or before tiles/header are decoded in Paged mode (outer Compose Box handles instant 0ms tap & long-press).
		if (isWebtoonMode || !isReady) {
			return false
		}
		return super.dispatchTouchEvent(event)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SsivPageView(
	page: ReaderPage,
	bitmapConfig: Bitmap.Config,
	isWebtoon: Boolean,
	isDoubleTapSmartZoom: Boolean,
	shaderConfig: ReaderShaderConfig,
	colorFilter: ReaderColorFilter = ReaderColorFilter.EMPTY,
	colorWarmth: Float = 0f,
	neuralRevision: Int,
	loadPage: suspend (ReaderPage, Boolean) -> Result<Pair<Uri, Rect?>>,
	onTapNormalized: (Float, Float) -> Unit,
	onLongPress: () -> Unit = {},
	fallbackUrl: String? = null,
	onFindAlternative: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val latestShaderConfig by rememberUpdatedState(shaderConfig)
	val latestColorFilter by rememberUpdatedState(colorFilter)
	val latestColorWarmth by rememberUpdatedState(colorWarmth)
	val latestOnTap by rememberUpdatedState(onTapNormalized)
	val latestOnLongPress by rememberUpdatedState(onLongPress)
	val latestSmartZoom by rememberUpdatedState(isDoubleTapSmartZoom)
	var retryToken by remember(page.id) { mutableStateOf(0) }
	var resolved by remember(page.id) { mutableStateOf<Pair<Uri, Rect?>?>(null) }
	var errorText by remember(page.id) { mutableStateOf<String?>(null) }
	var errorThrowable by remember(page.id) { mutableStateOf<Throwable?>(null) }
	var imageReady by remember(page.id) { mutableStateOf(false) }

	val cfPageLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { solved ->
		if (solved) retryToken++
	}
	val browserPageLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { solved ->
		if (solved) retryToken++
	}

	LaunchedEffect(page.id, retryToken, neuralRevision) {
		errorText = null
		errorThrowable = null
		if (resolved == null) {
			imageReady = false
		}
		loadPage(page, retryToken > 0)
			.onSuccess { newPair ->
				if (resolved?.first != newPair.first) {
					imageReady = false
				}
				resolved = newPair
			}
			.onFailure { err ->
				errorThrowable = err
				errorText = err.getDisplayMessage(context.resources)
			}
	}

	// Webtoon strips use exact integer pixel heights without aspect clamping so consecutive slices join with 0px seam.
	val boxModifier = if (isWebtoon) {
		modifier
			.fillMaxWidth()
			.layout { measurable, constraints ->
				val w = constraints.maxWidth
				val bounds = resolved?.second
				val h = if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
					((w.toLong() * bounds.height()) / bounds.width()).toInt().coerceAtLeast(1)
				} else {
					(w * 1.42f).roundToInt().coerceAtLeast(1)
				}
				val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
				layout(w, h) { placeable.place(0, 0) }
			}
	} else {
		modifier.fillMaxSize()
	}

	val pair = resolved
	// Forward center-taps and long-presses with 0ms latency whenever fetching, decoding tiles (!imageReady), or failed!
	val useComposeTapFallback = !isWebtoon && (pair == null || !imageReady || errorText != null)
	val fallbackGestureModifier = if (useComposeTapFallback) {
		Modifier.pointerInput(page.id) {
			detectTapGestures(
				onTap = { offset ->
					val nx = if (size.width > 0) (offset.x / size.width.toFloat()).coerceIn(0f, 1f) else 0.5f
					val ny = if (size.height > 0) (offset.y / size.height.toFloat()).coerceIn(0f, 1f) else 0.5f
					latestOnTap(nx, ny)
				},
				onLongPress = { latestOnLongPress() },
			)
		}
	} else {
		Modifier
	}

	Box(
		modifier = boxModifier
			.background(Color.Black)
			.then(fallbackGestureModifier),
		contentAlignment = Alignment.Center,
	) {
		if (pair != null && errorText == null) {
			val (uri, bounds) = pair
			AndroidView(
				factory = { ctx ->
					val isLowRam = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
						?.isLowRamDevice == true
					ReaderSubsamplingImageView(ctx, isWebtoon).apply {
						isEagerLoadingEnabled = !isLowRam
						panLimit = SubsamplingScaleImageView.PAN_LIMIT_INSIDE
						minimumScaleType = if (isWebtoon) {
							SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
						} else {
							SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
						}
						maxScale = 4f
						isZoomEnabled = !isWebtoon
						isPanEnabled = !isWebtoon
						bitmapDecoderFactory = AvifCapableImageDecoder.Factory(bitmapConfig)
						regionDecoderFactory = AvifCapableRegionDecoder.Factory(bitmapConfig, isLowRam)
						val tapDetector = GestureDetector(
							ctx,
							object : GestureDetector.SimpleOnGestureListener() {
								override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
									val nx = if (width > 0) (e.x / width.toFloat()).coerceIn(0f, 1f) else 0.5f
									val ny = if (height > 0) (e.y / height.toFloat()).coerceIn(0f, 1f) else 0.5f
									latestOnTap(nx, ny)
									return true
								}

								override fun onLongPress(e: MotionEvent) {
									if (!isWebtoonMode) {
										latestOnLongPress()
									}
								}
							},
						)
						setOnTouchListener { _, event ->
							tapDetector.onTouchEvent(event)
							false
						}
						addOnImageEventListener(
							object : DefaultOnImageEventListener {
								override fun onReady() {
									imageReady = true
									configureSsivScaleAndZoom(this@apply, isWebtoonMode, latestSmartZoom)
									applyShaderToSsiv(
										this@apply,
										latestShaderConfig,
										latestColorFilter,
										latestColorWarmth,
									)
								}

								override fun onImageLoadError(e: Throwable) {
									imageReady = false
									errorText = e.localizedMessage ?: "Failed to decode page"
								}
							},
						)
					}
				},
				update = { ssiv ->
					ssiv.isWebtoonMode = isWebtoon
					ssiv.isZoomEnabled = !isWebtoon
					ssiv.isPanEnabled = !isWebtoon
					val holder = ssivState(ssiv)
					val tagKey = "${uri}_${bitmapConfig.name}"
					if (holder.imageKey != tagKey) {
						holder.imageKey = tagKey
						imageReady = false
						val isLowRam = (ssiv.context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
							?.isLowRamDevice == true
						ssiv.bitmapDecoderFactory = AvifCapableImageDecoder.Factory(bitmapConfig)
						ssiv.regionDecoderFactory = AvifCapableRegionDecoder.Factory(bitmapConfig, isLowRam)
						val source = ImageSource.uri(uri).let { src ->
							if (uri.isPdfUri() && bounds != null) src.region(bounds) else src
						}
						ssiv.setImage(source)
					}
					if (ssiv.isReady) {
						configureSsivScaleAndZoom(ssiv, isWebtoon, isDoubleTapSmartZoom)
					}
					applyShaderToSsiv(ssiv, shaderConfig, colorFilter, colorWarmth)
				},
				onRelease = { it.recycle() },
				modifier = Modifier.fillMaxSize(),
			)
		}

		if (errorText != null) {
			val challenge = errorThrowable?.toWebViewChallengeAction(
				fallbackUrl = page.url.takeIf { it.startsWith("http", ignoreCase = true) } ?: fallbackUrl,
				fallbackSource = page.source,
			)
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.padding(24.dp),
			) {
				Text(
					text = errorText!!,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textSecondary,
					textAlign = TextAlign.Center,
				)
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (challenge != null) {
						Button(
							onClick = {
								when (challenge) {
									is WebViewChallengeAction.CloudFlare -> cfPageLauncher.launch(challenge.exception)
									is WebViewChallengeAction.Interactive -> browserPageLauncher.launch(challenge.exception)
									is WebViewChallengeAction.BrowserUrl -> runCatching {
										context.startActivity(challenge.toBrowserIntent(context))
									}
								}
							},
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
						) {
							Text("Solve in WebView")
						}
					}
					Button(
						onClick = { retryToken++ },
						colors = ButtonDefaults.buttonColors(
							containerColor = if (challenge == null) NemuriTheme.colors.cyan else NemuriTheme.colors.surfaceElevated,
							contentColor = if (challenge == null) NemuriTheme.colors.onCyan else NemuriTheme.colors.textPrimary,
						),
					) {
						Text("Retry")
					}
					if (challenge != null && onFindAlternative != null) {
						TextButton(onClick = onFindAlternative) {
							Text("Find Alternative", color = NemuriTheme.colors.cyan)
						}
					}
				}
			}
		} else if (!imageReady) {
			CircularProgressIndicator(
				color = NemuriTheme.colors.cyan,
				strokeWidth = 2.5.dp,
				modifier = Modifier.size(28.dp),
			)
		}
	}
}

private fun configureSsivScaleAndZoom(
	ssiv: SubsamplingScaleImageView,
	isWebtoon: Boolean,
	smartDoubleTapFill: Boolean,
) {
	val vW = ssiv.width.toFloat()
	val vH = ssiv.height.toFloat()
	val sW = ssiv.sWidth.toFloat()
	val sH = ssiv.sHeight.toFloat()
	if (vW <= 0f || vH <= 0f || sW <= 0f || sH <= 0f) return

	val fitScale = minOf(vW / sW, vH / sH)
	val fillScale = maxOf(vW / sW, vH / sH)
	if (isWebtoon) {
		ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
		ssiv.minScale = fillScale
		ssiv.maxScale = fillScale
	} else {
		ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
		ssiv.maxScale = maxOf(fillScale * 2.5f, fitScale * 4.0f, 4.0f)
		val targetDoubleTap = if (smartDoubleTapFill && fillScale > fitScale * 1.12f) {
			fillScale
		} else {
			fitScale * 2.0f
		}
		ssiv.doubleTapZoomScale = targetDoubleTap
		ssiv.doubleTapZoomStyle = SubsamplingScaleImageView.ZOOM_FOCUS_FIXED
	}
}

@Composable
private fun NovelReaderViewport(
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	onRegisterVolumeHandler: (((Boolean) -> Unit)?) -> Unit,
	onToggleHud: () -> Unit,
) {
	val context = LocalContext.current
	val density = LocalDensity.current
	val listState = rememberLazyListState()
	val coroutineScope = rememberCoroutineScope()

	LaunchedEffect(uiState.isAutoScrollEnabled, uiState.isAutoScrollPaused, uiState.autoScrollSpeed) {
		if (!uiState.isAutoScrollEnabled || uiState.isAutoScrollPaused) return@LaunchedEffect
		var prevNanos = withFrameNanos { it }
		while (true) {
			val nowNanos = withFrameNanos { it }
			val dtSec = ((nowNanos - prevNanos).coerceIn(0L, 64_000_000L)) / 1_000_000_000f
			prevNanos = nowNanos
			if (!listState.isScrollInProgress && listState.canScrollForward) {
				val pxPerSec = with(density) { (42f + uiState.autoScrollSpeed * 280f).dp.toPx() }
				listState.dispatchRawDelta(pxPerSec * dtSec)
			}
		}
	}

	val (bgColor, textColor, accentColor) = when (uiState.epubTheme) {
		"white" -> Triple(Color(0xFFFAF8F4), Color(0xFF1D1F24), Color(0xFF00778F))
		"gray" -> Triple(Color(0xFFECE5D8), Color(0xFF2B2620), Color(0xFF006D82))
		"black" -> Triple(Color(0xFF000000), Color(0xFFE0E4EC), NemuriTheme.colors.cyan)
		else -> Triple(NemuriTheme.colors.background, NemuriTheme.colors.textPrimary, NemuriTheme.colors.cyan)
	}

	val fontFamily = when (uiState.epubFontFamily) {
		"sans-serif" -> FontFamily.SansSerif
		"monospace" -> FontFamily.Monospace
		else -> FontFamily.Serif
	}
	val fontSizeSp = (17f * (uiState.epubFontSize / 100f)).sp
	val lineHeightSp = (17f * (uiState.epubFontSize / 100f) * (uiState.epubLineHeight / 100f)).sp

	LaunchedEffect(uiState.novelScrollToken) {
		if (uiState.novelBlocks.isNotEmpty()) {
			val target = uiState.novelInitialBlockIndex.coerceIn(0, uiState.novelBlocks.lastIndex)
			listState.scrollToItem(target)
		}
	}

	LaunchedEffect(listState, uiState.novelBlocks) {
		snapshotFlow { listState.firstVisibleItemIndex }
			.distinctUntilChanged()
			.collect { idx ->
				if (uiState.novelBlocks.isNotEmpty()) {
					viewModel.onNovelScrollChanged(idx.coerceIn(0, uiState.novelBlocks.lastIndex))
				}
			}
	}

	LaunchedEffect(listState, uiState.novelBlocks) {
		onRegisterVolumeHandler { forward ->
			val target = (listState.firstVisibleItemIndex + if (forward) 3 else -3)
				.coerceIn(0, (uiState.novelBlocks.size - 1).coerceAtLeast(0))
			coroutineScope.launch { listState.animateScrollToItem(target) }
		}
	}

	val currentTapZoneMode by rememberUpdatedState(uiState.tapZoneMode)
	val latestBlocksSize by rememberUpdatedState(uiState.novelBlocks.size)
	val latestOnToggleHud by rememberUpdatedState(onToggleHud)
	val latestIsChapterLoading by rememberUpdatedState(uiState.isChapterLoading)

	if (uiState.novelBlocks.isEmpty()) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(bgColor)
				.pointerInput(Unit) {
					detectTapGestures(onTap = { latestOnToggleHud() })
				},
			contentAlignment = Alignment.Center,
		) {
			if (uiState.isChapterLoading) {
				NemuriLoadingState(message = "Loading chapter…")
			}
		}
		return
	}

	SelectionContainer {
		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(
				start = uiState.epubHorizontalPadding.dp,
				end = uiState.epubHorizontalPadding.dp,
				top = 72.dp,
				bottom = 96.dp,
			),
			verticalArrangement = Arrangement.spacedBy(14.dp),
			modifier = Modifier
				.fillMaxSize()
				.background(bgColor)
				.pointerInput(Unit) {
					detectTapGestures(
						onTap = { offset ->
							if (latestIsChapterLoading) {
								latestOnToggleHud()
								return@detectTapGestures
							}
							val nx = if (size.width > 0) offset.x / size.width.toFloat() else 0.5f
							val ny = if (size.height > 0) offset.y / size.height.toFloat() else 0.5f
							when (resolveTapZoneAction(nx, ny, currentTapZoneMode, isRtl = false, isWebtoon = true)) {
								TapZoneAction.TOGGLE_HUD -> latestOnToggleHud()
								TapZoneAction.PREV -> {
									val target = (listState.firstVisibleItemIndex - 4).coerceAtLeast(0)
									coroutineScope.launch { listState.animateScrollToItem(target) }
								}
								TapZoneAction.NEXT -> {
									val maxIdx = (latestBlocksSize - 1).coerceAtLeast(0)
									val target = (listState.firstVisibleItemIndex + 4).coerceAtMost(maxIdx)
									coroutineScope.launch { listState.animateScrollToItem(target) }
								}
							}
						},
					)
				}
				.statusBarsPadding(),
		) {
			item(key = "novel_chapter_header") {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(
							imageVector = Icons.Default.MenuBook,
							contentDescription = null,
							tint = accentColor,
							modifier = Modifier.size(16.dp),
						)
						Text(
							text = uiState.title,
							style = MaterialTheme.typography.labelMedium,
							color = accentColor,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					Text(
						text = uiState.chapterTitle,
						style = MaterialTheme.typography.headlineSmall.copy(fontFamily = fontFamily),
						color = textColor,
						fontWeight = FontWeight.Bold,
					)
					HorizontalDivider(color = textColor.copy(alpha = 0.14f), modifier = Modifier.padding(top = 6.dp))
				}
			}

			items(uiState.novelBlocks, key = { it.id }) { block ->
				when (block) {
					is NovelBlock.Heading -> {
						Text(
							text = block.text,
							fontFamily = fontFamily,
							fontSize = ((20 - (block.level - 1) * 1.5f).coerceAtLeast(15f) * (uiState.epubFontSize / 100f)).sp,
							lineHeight = (lineHeightSp.value * 1.1f).sp,
							fontWeight = FontWeight.Bold,
							color = textColor,
							modifier = Modifier.padding(top = 6.dp),
						)
					}

					is NovelBlock.Paragraph -> {
						Text(
							text = block.text,
							fontFamily = fontFamily,
							fontSize = fontSizeSp,
							lineHeight = lineHeightSp,
							color = textColor,
						)
					}

					is NovelBlock.Divider -> {
						HorizontalDivider(
							color = textColor.copy(alpha = 0.2f),
							modifier = Modifier.padding(vertical = 8.dp),
						)
					}

					is NovelBlock.Image -> {
						AsyncImage(
							model = ImageRequest.Builder(context)
								.data(block.url)
								.build(),
							contentDescription = null,
							contentScale = ContentScale.FillWidth,
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(8.dp)),
						)
					}
				}
			}

			item(key = "novel_footer_nav") {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 20.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					if (uiState.hasPrevChapter) {
						Surface(
							color = textColor.copy(alpha = 0.08f),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.goToAdjacentChapter(isNext = false) },
						) {
							Text(
								text = "Previous Chapter",
								style = MaterialTheme.typography.labelLarge,
								color = textColor,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 12.dp),
							)
						}
					}
					if (uiState.hasNextChapter) {
						Surface(
							color = accentColor,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.goToAdjacentChapter(isNext = true) },
						) {
							Text(
								text = "Next Chapter",
								style = MaterialTheme.typography.labelLarge,
								color = if (uiState.epubTheme == "white" || uiState.epubTheme == "gray") Color.White else NemuriTheme.colors.onCyan,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 12.dp),
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ChapterTransitionFooter(
	hasNext: Boolean,
	onNext: () -> Unit,
) {
	if (!hasNext) return
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp, vertical = 28.dp),
		contentAlignment = Alignment.Center,
	) {
		Button(
			onClick = onNext,
			colors = ButtonDefaults.buttonColors(
				containerColor = NemuriTheme.colors.cyan,
				contentColor = NemuriTheme.colors.onCyan,
			),
			shape = RoundedCornerShape(14.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			Text("Continue to Next Chapter")
		}
	}
}

private data class PagerSpreadSlot(
	val primaryIndex: Int,
	val secondaryIndex: Int?,
)

private enum class ReaderNavigatorTab {
	CHAPTERS,
	PAGES,
	BOOKMARKS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderNavigatorModalSheet(
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	onDismiss: () -> Unit,
) {
	val context = LocalContext.current
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	var selectedTab by rememberSaveable { mutableStateOf(ReaderNavigatorTab.CHAPTERS) }
	var isExpandedFullscreen by rememberSaveable { mutableStateOf(false) }
	var chapterSearchQuery by rememberSaveable { mutableStateOf("") }

	val chapterPages = remember(uiState.pages, uiState.currentChapterId) {
		uiState.pages.mapIndexedNotNull { globalIdx, page ->
			if (page.chapterId == uiState.currentChapterId) globalIdx to page else null
		}
	}
	val currentPageInChapterIdx = remember(chapterPages, uiState.currentPageIndex) {
		chapterPages.indexOfFirst { (globalIdx, _) -> globalIdx == uiState.currentPageIndex }.coerceAtLeast(0)
	}
	val displayedChapters = remember(uiState.chapters, uiState.isChaptersReversed, chapterSearchQuery) {
		val base = if (uiState.isChaptersReversed) uiState.chapters.asReversed() else uiState.chapters
		val q = chapterSearchQuery.trim()
		if (q.isEmpty()) {
			base
		} else {
			base.filter { it.title.contains(q, ignoreCase = true) }
		}
	}
	val chapterTitleById = remember(uiState.chapters) {
		uiState.chapters.associate { it.chapter.id to it.title }
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = NemuriTheme.colors.sheet,
		contentColor = NemuriTheme.colors.textPrimary,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.let { mod ->
					if (isExpandedFullscreen) mod.fillMaxHeight(0.95f) else mod.height(510.dp)
				}
				.animateContentSize()
				.navigationBarsPadding()
				.padding(bottom = 16.dp),
		) {
			// Header with Title + [⛶] Expand Fullscreen Toggle + Close
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 18.dp, vertical = 2.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(end = 10.dp),
				) {
					Text(
						text = when (selectedTab) {
							ReaderNavigatorTab.CHAPTERS -> "Chapters"
							ReaderNavigatorTab.PAGES -> if (uiState.isTextMode) "Blocks" else "Pages"
							ReaderNavigatorTab.BOOKMARKS -> "Bookmarks"
						},
						style = MaterialTheme.typography.titleLarge,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						text = uiState.chapterTitle.ifBlank { uiState.title },
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Surface(
						color = if (isExpandedFullscreen) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = CircleShape,
						border = if (isExpandedFullscreen) BorderStroke(1.dp, NemuriTheme.colors.cyan) else null,
						modifier = Modifier
							.size(36.dp)
							.clip(CircleShape)
							.clickable { isExpandedFullscreen = !isExpandedFullscreen },
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = if (isExpandedFullscreen) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
								contentDescription = if (isExpandedFullscreen) "Collapse sheet" else "Expand fullscreen",
								tint = if (isExpandedFullscreen) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.size(17.dp),
							)
						}
					}

					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = CircleShape,
						modifier = Modifier
							.size(36.dp)
							.clip(CircleShape)
							.clickable(onClick = onDismiss),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Close",
								tint = NemuriTheme.colors.textSecondary,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}

			// Top 3 Equal-Width Icon Tabs (☰ Chapters | ⊞ Pages | 🔖 Bookmarks)
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(14.dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 18.dp, vertical = 10.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(3.dp),
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					val tabSpecs = listOf(
						Triple(
							ReaderNavigatorTab.CHAPTERS,
							Icons.Default.FormatListBulleted,
							"Chapters (${uiState.chapters.size})",
						),
						Triple(
							ReaderNavigatorTab.PAGES,
							Icons.Default.GridView,
							if (uiState.isTextMode) "Blocks (${uiState.novelBlocks.size})" else "Pages (${chapterPages.size})",
						),
						Triple(
							ReaderNavigatorTab.BOOKMARKS,
							if (uiState.mangaBookmarks.isNotEmpty()) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
							"Bookmarks (${uiState.mangaBookmarks.size})",
						),
					)
					tabSpecs.forEach { (tab, icon, label) ->
						val selected = selectedTab == tab
						Surface(
							color = if (selected) NemuriTheme.colors.cyan else Color.Transparent,
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(10.dp))
								.clickable { selectedTab = tab },
						) {
							Row(
								modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
								horizontalArrangement = Arrangement.Center,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Icon(
									imageVector = icon,
									contentDescription = label,
									tint = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.size(15.dp),
								)
								Spacer(modifier = Modifier.width(5.dp))
								Text(
									text = label,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.textSecondary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
					}
				}
			}

			when (selectedTab) {
				ReaderNavigatorTab.CHAPTERS -> {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 18.dp, vertical = 2.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						OutlinedTextField(
							value = chapterSearchQuery,
							onValueChange = { chapterSearchQuery = it },
							placeholder = { Text("Search chapters…", style = MaterialTheme.typography.bodySmall) },
							leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
							trailingIcon = {
								if (chapterSearchQuery.isNotEmpty()) {
									IconButton(onClick = { chapterSearchQuery = "" }) {
										Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
									}
								}
							},
							singleLine = true,
							shape = RoundedCornerShape(12.dp),
							colors = OutlinedTextFieldDefaults.colors(
								focusedBorderColor = NemuriTheme.colors.cyan,
								unfocusedBorderColor = NemuriTheme.colors.border,
								focusedContainerColor = NemuriTheme.colors.surfaceElevated,
								unfocusedContainerColor = NemuriTheme.colors.surfaceElevated,
							),
							modifier = Modifier.fillMaxWidth(),
						)

						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							ReaderChapterSortMode.entries.forEach { sortMode ->
								val selected = uiState.chapterSortMode == sortMode
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(10.dp),
									border = BorderStroke(
										1.dp,
										if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(RoundedCornerShape(10.dp))
										.clickable { viewModel.setChapterSortMode(sortMode) },
								) {
									Text(
										text = sortMode.label,
										style = MaterialTheme.typography.labelSmall,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										textAlign = TextAlign.Center,
										maxLines = 1,
										modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
									)
								}
							}

							Surface(
								color = if (uiState.isChaptersReversed) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(
									1.dp,
									if (uiState.isChaptersReversed) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.clip(RoundedCornerShape(10.dp))
									.clickable(onClick = viewModel::toggleChaptersReversed),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Icon(
										imageVector = Icons.Default.SwapVert,
										contentDescription = "Reverse chapter order",
										tint = if (uiState.isChaptersReversed) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										modifier = Modifier.size(14.dp),
									)
									Text(
										text = "Reverse",
										style = MaterialTheme.typography.labelSmall,
										fontWeight = if (uiState.isChaptersReversed) FontWeight.Bold else FontWeight.Medium,
										color = if (uiState.isChaptersReversed) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										maxLines = 1,
									)
								}
							}
						}
					}

					val currentChapterIdx = remember(displayedChapters) {
						displayedChapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
					}
					val chaptersListState = rememberLazyListState(
						initialFirstVisibleItemIndex = (currentChapterIdx - 2).coerceAtLeast(0),
					)
					LaunchedEffect(uiState.chapterSortMode, uiState.isChaptersReversed, uiState.currentChapterId) {
						if (chapterSearchQuery.isBlank()) {
							val idx = displayedChapters.indexOfFirst { it.isCurrent }
							if (idx >= 0) {
								chaptersListState.scrollToItem((idx - 2).coerceAtLeast(0))
							}
						}
					}

					LazyColumn(
						state = chaptersListState,
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f),
						contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						items(displayedChapters, key = { it.chapter.id }) { item ->
							Surface(
								color = if (item.isCurrent) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(
										1.dp,
										if (item.isCurrent) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										RoundedCornerShape(10.dp),
									)
									.clip(RoundedCornerShape(10.dp))
									.clickable {
										viewModel.selectChapter(item.chapter.id)
										onDismiss()
									},
							) {
								Row(
									modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text(
										text = item.title,
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
										color = if (item.isCurrent) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f),
									)
									if (item.isCurrent) {
										Icon(
											imageVector = Icons.Default.Check,
											contentDescription = null,
											tint = NemuriTheme.colors.cyan,
											modifier = Modifier.size(16.dp),
										)
									}
								}
							}
						}
					}
				}

				ReaderNavigatorTab.PAGES -> {
					if (uiState.isTextMode) {
						val blocksListState = rememberLazyListState(
							initialFirstVisibleItemIndex = uiState.novelCurrentBlockIndex.coerceAtLeast(0),
						)
						LazyColumn(
							state = blocksListState,
							modifier = Modifier
								.fillMaxWidth()
								.weight(1f),
							contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp),
						) {
							items(uiState.novelBlocks.size, key = { "blk_$it" }) { idx ->
								val blk = uiState.novelBlocks[idx]
								val isCurrent = idx == uiState.novelCurrentBlockIndex
								val previewText = when (blk) {
									is NovelBlock.Heading -> blk.text
									is NovelBlock.Paragraph -> blk.text
									is NovelBlock.Divider -> "— Section Divider —"
									is NovelBlock.Image -> "[Illustration]"
								}
								Surface(
									color = if (isCurrent) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(10.dp),
									border = BorderStroke(
										1.dp,
										if (isCurrent) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.fillMaxWidth()
										.clip(RoundedCornerShape(10.dp))
										.clickable {
											viewModel.jumpToNovelBlock(idx)
											onDismiss()
										},
								) {
									Row(
										modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(10.dp),
									) {
										Text(
											text = "#${idx + 1}",
											style = MaterialTheme.typography.labelSmall,
											fontWeight = FontWeight.Bold,
											color = NemuriTheme.colors.cyan,
										)
										Text(
											text = previewText,
											style = MaterialTheme.typography.bodySmall,
											color = NemuriTheme.colors.textPrimary,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
											modifier = Modifier.weight(1f),
										)
									}
								}
							}
						}
					} else {
						// Sleek Top Horizontal Step Slider (2..6 Columns) scaling thumbnail grid live
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 18.dp, vertical = 2.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(
								imageVector = Icons.Default.GridView,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(16.dp),
							)
							Text(
								text = "Columns",
								style = MaterialTheme.typography.labelMedium,
								color = NemuriTheme.colors.textSecondary,
							)
							Slider(
								value = uiState.pagesGridColumns.coerceIn(2, 6).toFloat(),
								onValueChange = { viewModel.setPagesGridColumns(it.roundToInt().coerceIn(2, 6)) },
								valueRange = 2f..6f,
								steps = 3,
								colors = SliderDefaults.colors(
									thumbColor = NemuriTheme.colors.cyan,
									activeTrackColor = NemuriTheme.colors.cyan,
									inactiveTrackColor = NemuriTheme.colors.surfaceElevated,
								),
								modifier = Modifier.weight(1f),
							)
							Surface(
								color = NemuriTheme.colors.tealContainer,
								shape = RoundedCornerShape(999.dp),
								border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f)),
							) {
								Text(
									text = "${uiState.pagesGridColumns} / row",
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Bold,
									color = NemuriTheme.colors.cyan,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
								)
							}
						}

						val pagesGridState = rememberLazyGridState(
							initialFirstVisibleItemIndex = currentPageInChapterIdx,
						)
						LaunchedEffect(currentPageInChapterIdx) {
							if (currentPageInChapterIdx in chapterPages.indices) {
								pagesGridState.scrollToItem(currentPageInChapterIdx)
							}
						}

						LazyVerticalGrid(
							state = pagesGridState,
							columns = GridCells.Fixed(uiState.pagesGridColumns.coerceIn(2, 6)),
							modifier = Modifier
								.fillMaxWidth()
								.weight(1f),
							contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							items(chapterPages, key = { (_, page) -> page.id }) { (globalIdx, page) ->
								val isCurrent = globalIdx == uiState.currentPageIndex
								val isPageBookmarked = uiState.mangaBookmarks.any { bm ->
									bm.chapterId == page.chapterId && bm.page in page.index..page.endIndex
								}
								val pageBadge = if (page.endIndex > page.index) {
									"${page.index + 1}–${page.endIndex + 1}"
								} else {
									"${page.index + 1}"
								}
								Box(
									modifier = Modifier
										.fillMaxWidth()
										.aspectRatio(0.70f)
										.clip(RoundedCornerShape(10.dp))
										.background(NemuriTheme.colors.surfaceElevated)
										.border(
											width = if (isCurrent) 2.dp else 1.dp,
											color = if (isCurrent) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											shape = RoundedCornerShape(10.dp),
										)
										.clickable {
											viewModel.jumpToMangaPage(globalIdx)
											onDismiss()
										},
									contentAlignment = Alignment.Center,
								) {
									SubcomposeAsyncImage(
										model = ImageRequest.Builder(context)
											.data(page.toMangaPage())
											.size(CoilSize(320, 460))
											.precision(Precision.INEXACT)
											.build(),
										contentDescription = "Page $pageBadge",
										contentScale = ContentScale.Crop,
										loading = {
											Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
												Text(
													text = pageBadge,
													style = MaterialTheme.typography.labelMedium,
													color = NemuriTheme.colors.textDim,
												)
											}
										},
										error = {
											Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
												Text(
													text = pageBadge,
													style = MaterialTheme.typography.labelMedium,
													color = NemuriTheme.colors.textDim,
												)
											}
										},
										modifier = Modifier.fillMaxSize(),
									)

									if (isPageBookmarked) {
										Icon(
											imageVector = Icons.Default.Bookmark,
											contentDescription = "Bookmarked",
											tint = NemuriTheme.colors.amber,
											modifier = Modifier
												.align(Alignment.TopEnd)
												.padding(5.dp)
												.size(15.dp),
										)
									}

									Surface(
										color = if (isCurrent) NemuriTheme.colors.cyan else Color.Black.copy(alpha = 0.72f),
										shape = RoundedCornerShape(999.dp),
										modifier = Modifier
											.align(Alignment.BottomStart)
											.padding(5.dp),
									) {
										Text(
											text = pageBadge,
											style = MaterialTheme.typography.labelSmall,
											fontSize = 10.sp,
											fontWeight = FontWeight.Bold,
											color = if (isCurrent) NemuriTheme.colors.onCyan else Color.White,
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
										)
									}
								}
							}
						}
					}
				}

				ReaderNavigatorTab.BOOKMARKS -> {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f)
							.padding(horizontal = 18.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Surface(
							color = if (uiState.isBookmarked) NemuriTheme.colors.amber.copy(alpha = 0.16f) else NemuriTheme.colors.tealContainer,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(
								1.dp,
								if (uiState.isBookmarked) NemuriTheme.colors.amber.copy(alpha = 0.6f) else NemuriTheme.colors.cyan.copy(alpha = 0.5f),
							),
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(12.dp))
								.clickable(onClick = viewModel::toggleBookmark),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.Center,
							) {
								Icon(
									imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
									contentDescription = null,
									tint = if (uiState.isBookmarked) NemuriTheme.colors.amber else NemuriTheme.colors.cyan,
									modifier = Modifier.size(17.dp),
								)
								Spacer(modifier = Modifier.width(6.dp))
								Text(
									text = if (uiState.isBookmarked) {
										"Current Page Bookmarked • Tap to Remove"
									} else {
										"Bookmark Current Page"
									},
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.SemiBold,
									color = if (uiState.isBookmarked) NemuriTheme.colors.amber else NemuriTheme.colors.cyan,
								)
							}
						}

						if (uiState.mangaBookmarks.isEmpty()) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.weight(1f),
								contentAlignment = Alignment.Center,
							) {
								Column(
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Icon(
										imageVector = Icons.Default.BookmarkBorder,
										contentDescription = null,
										tint = NemuriTheme.colors.textDim,
										modifier = Modifier.size(32.dp),
									)
									Text(
										text = "No bookmarked pages yet",
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textSecondary,
									)
									Text(
										text = "Tap the bookmark icon or long-press any page to save a quick jump marker.",
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.textMuted,
										textAlign = TextAlign.Center,
									)
								}
							}
						} else {
							LazyColumn(
								modifier = Modifier
									.fillMaxWidth()
									.weight(1f),
								contentPadding = PaddingValues(vertical = 4.dp),
								verticalArrangement = Arrangement.spacedBy(8.dp),
							) {
								items(uiState.mangaBookmarks, key = { "${it.chapterId}_${it.pageId}_${it.page}" }) { bm ->
									val chName = chapterTitleById[bm.chapterId] ?: "Chapter"
									val excerpt = bm.highlightText
									Surface(
										color = NemuriTheme.colors.surfaceElevated,
										shape = RoundedCornerShape(12.dp),
										border = BorderStroke(1.dp, NemuriTheme.colors.border),
										modifier = Modifier
											.fillMaxWidth()
											.clip(RoundedCornerShape(12.dp))
											.clickable {
												viewModel.jumpToBookmark(bm)
												onDismiss()
											},
									) {
										Row(
											modifier = Modifier.padding(10.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(12.dp),
										) {
											Box(
												modifier = Modifier
													.size(width = 44.dp, height = 62.dp)
													.clip(RoundedCornerShape(8.dp))
													.background(NemuriTheme.colors.card),
												contentAlignment = Alignment.Center,
											) {
												SubcomposeAsyncImage(
													model = ImageRequest.Builder(context)
														.data(bm.toMangaPage())
														.size(CoilSize(180, 260))
														.precision(Precision.INEXACT)
														.build(),
													contentDescription = null,
													contentScale = ContentScale.Crop,
													modifier = Modifier.fillMaxSize(),
												)
											}
											Column(
												modifier = Modifier.weight(1f),
												verticalArrangement = Arrangement.spacedBy(2.dp),
											) {
												Text(
													text = "$chName • Page ${bm.page + 1}",
													style = MaterialTheme.typography.titleSmall,
													color = NemuriTheme.colors.textPrimary,
													maxLines = 1,
													overflow = TextOverflow.Ellipsis,
												)
												if (!excerpt.isNullOrBlank()) {
													Text(
														text = "“$excerpt”",
														style = MaterialTheme.typography.bodySmall,
														color = NemuriTheme.colors.textSecondary,
														maxLines = 2,
														overflow = TextOverflow.Ellipsis,
													)
												} else {
													Text(
														text = "Tap to jump straight to this page",
														style = MaterialTheme.typography.labelSmall,
														color = NemuriTheme.colors.cyan,
													)
												}
											}
											IconButton(
												onClick = { viewModel.removeBookmarkItem(bm) },
												modifier = Modifier.size(34.dp),
											) {
												Icon(
													imageVector = Icons.Default.DeleteOutline,
													contentDescription = "Remove bookmark",
													tint = NemuriTheme.colors.danger,
													modifier = Modifier.size(18.dp),
												)
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ReaderPageQuickSheet(
	page: ReaderPage,
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	onOpenCompareSplit: () -> Unit,
	onDismiss: () -> Unit,
) {
	val context = LocalContext.current
	val isVisualDiagram = uiState.selectionMenuStyle == SelectionMenuStyle.VISUAL_DIAGRAM
	val isPageSavedBookmark = remember(uiState.mangaBookmarks, page.chapterId, page.index, page.endIndex) {
		uiState.mangaBookmarks.any { bm ->
			bm.chapterId == page.chapterId && bm.page in page.index..page.endIndex
		}
	}
	val pageLabel = if (page.endIndex > page.index) {
		"Pages ${page.index + 1}–${page.endIndex + 1}"
	} else {
		"Page ${page.index + 1}"
	}
	val isNeuralActive = uiState.isNeuralUpscaleEnabled && uiState.hasReadyNeuralModel
	val isUpscaleActive = uiState.shaderConfig.isActive || isNeuralActive
	val upscaleStatusLabel = remember(uiState.shaderConfig, isNeuralActive, uiState.activeNeuralModelLabel) {
		when {
			isNeuralActive && uiState.shaderConfig.isActive ->
				"Active: Neural 2x + ${uiState.shaderConfig.summaryLabel()}"
			isNeuralActive ->
				"Active: Neural 2x (${uiState.activeNeuralModelLabel})"
			uiState.shaderConfig.isActive ->
				"Active: ${uiState.shaderConfig.summaryLabel()}"
			uiState.hasReadyNeuralModel && uiState.isNeuralUpscaleSupported ->
				"Off • 1-tap Neural 2x (${uiState.activeNeuralModelLabel})"
			UpscaleEffect.isSupported ->
				"Off • 1-tap Anime4K GPU Shader"
			else ->
				"Off • Configure in Mode & Tools"
		}
	}

	NemuriModalSheet(
		title = "$pageLabel • Quick Menu",
		subtitle = uiState.chapterTitle.ifBlank { uiState.title },
		onDismiss = onDismiss,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				ReaderQuickActionTile(
					icon = Icons.Default.Download,
					label = "Save Page",
					onClick = {
						viewModel.savePageToGallery(page)
						onDismiss()
					},
					modifier = Modifier.weight(1f),
				)
				ReaderQuickActionTile(
					icon = Icons.Default.Share,
					label = "Share Page",
					onClick = {
						viewModel.sharePage(page) { chooser ->
							runCatching { context.startActivity(chooser) }
						}
						onDismiss()
					},
					modifier = Modifier.weight(1f),
				)
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				ReaderQuickActionTile(
					icon = Icons.Default.Image,
					label = "Set as Cover",
					onClick = {
						viewModel.setPageAsMangaCover(page)
						onDismiss()
					},
					modifier = Modifier.weight(1f),
				)
				ReaderQuickActionTile(
					icon = if (isPageSavedBookmark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
					label = if (isPageSavedBookmark) "Bookmarked" else "Bookmark Page",
					highlighted = isPageSavedBookmark,
					highlightTint = NemuriTheme.colors.amber,
					onClick = {
						viewModel.toggleBookmarkForPage(page)
						onDismiss()
					},
					modifier = Modifier.weight(1f),
				)
			}

			HorizontalDivider(color = NemuriTheme.colors.border)

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "QUICK READ MODE",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.cyan,
					)
					Text(
						text = "Remembered for this manga",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(999.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
					modifier = Modifier
						.clip(RoundedCornerShape(999.dp))
						.clickable {
							val next = if (isVisualDiagram) {
								SelectionMenuStyle.COMPACT_LIST
							} else {
								SelectionMenuStyle.VISUAL_DIAGRAM
							}
							viewModel.setSelectionMenuStyle(next)
						},
				) {
					Row(
						modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(
							imageVector = if (isVisualDiagram) Icons.Default.GridView else Icons.Default.ViewList,
							contentDescription = "Toggle menu style",
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(13.dp),
						)
						Text(
							text = if (isVisualDiagram) "Visual ↔ Compact" else "Compact ↔ Visual",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = NemuriTheme.colors.cyan,
						)
					}
				}
			}

			if (isVisualDiagram) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					listOf(
						ReaderMode.STANDARD to "Standard",
						ReaderMode.REVERSED to "R-to-L",
						ReaderMode.VERTICAL to "Vertical",
						ReaderMode.WEBTOON to "Webtoon",
					).forEach { (mode, label) ->
						val selected = uiState.readerMode == mode
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(
								width = if (selected) 1.5.dp else 1.dp,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.setReaderMode(mode) },
						) {
							Column(
								modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.spacedBy(5.dp),
							) {
								ReaderModeDiagramCanvas(
									mode = mode,
									selected = selected,
									modifier = Modifier.size(width = 40.dp, height = 30.dp),
								)
								Text(
									text = label,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									textAlign = TextAlign.Center,
									maxLines = 1,
								)
							}
						}
					}
				}
			} else {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					listOf(
						ReaderMode.STANDARD to "Left to Right (Paged)",
						ReaderMode.REVERSED to "Right to Left (Manga)",
						ReaderMode.VERTICAL to "Vertical (Paged)",
						ReaderMode.WEBTOON to "Webtoon (Continuous)",
					).forEach { (mode, label) ->
						ReaderCompactChoiceRow(
							label = label,
							selected = uiState.readerMode == mode,
							onClick = { viewModel.setReaderMode(mode) },
						)
					}
				}
			}

			if (UpscaleEffect.isSupported || uiState.isNeuralUpscaleSupported) {
				HorizontalDivider(color = NemuriTheme.colors.border)

				Surface(
					color = if (isUpscaleActive) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(
						width = if (isUpscaleActive) 1.5.dp else 1.dp,
						color = if (isUpscaleActive) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
					),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable(onClick = viewModel::toggleQuickUpscale),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							modifier = Modifier.weight(1f).padding(end = 8.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Icon(
								imageVector = Icons.Default.AutoAwesome,
								contentDescription = null,
								tint = if (isUpscaleActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.size(18.dp),
							)
							Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
								Text(
									text = "Quick Upscale",
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.SemiBold,
									color = if (isUpscaleActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								)
								Text(
									text = upscaleStatusLabel,
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.textMuted,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
						Switch(
							checked = isUpscaleActive,
							onCheckedChange = { viewModel.toggleQuickUpscale() },
							colors = SwitchDefaults.colors(
								checkedThumbColor = NemuriTheme.colors.onCyan,
								checkedTrackColor = NemuriTheme.colors.cyan,
							),
						)
					}
				}

				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f)),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable(onClick = onOpenCompareSplit),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Icon(
							imageVector = Icons.Default.Compare,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(18.dp),
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = "Compare Split (Before vs After)",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = NemuriTheme.colors.cyan,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ReaderQuickActionTile(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	highlighted: Boolean = false,
	highlightTint: Color = NemuriTheme.colors.cyan,
	enabled: Boolean = true,
) {
	Surface(
		color = when {
			!enabled -> NemuriTheme.colors.surfaceElevated.copy(alpha = 0.45f)
			highlighted -> highlightTint.copy(alpha = 0.16f)
			else -> NemuriTheme.colors.surfaceElevated
		},
		shape = RoundedCornerShape(12.dp),
		border = BorderStroke(
			width = if (highlighted) 1.5.dp else 1.dp,
			color = if (highlighted) highlightTint else NemuriTheme.colors.border,
		),
		modifier = modifier
			.clip(RoundedCornerShape(12.dp))
			.clickable(enabled = enabled, onClick = onClick),
	) {
		Column(
			modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = label,
				tint = when {
					!enabled -> NemuriTheme.colors.textDim
					highlighted -> highlightTint
					else -> NemuriTheme.colors.cyan
				},
				modifier = Modifier.size(20.dp),
			)
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
				color = when {
					!enabled -> NemuriTheme.colors.textDim
					highlighted -> highlightTint
					else -> NemuriTheme.colors.textPrimary
				},
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.labelSmall,
					fontSize = 9.5.sp,
					color = NemuriTheme.colors.textMuted,
					textAlign = TextAlign.Center,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

private fun resolveSnapEdge(normX: Float, normY: Float): ReaderScrubberEdge = when {
	normY < 0.22f -> ReaderScrubberEdge.TOP
	normY > 0.78f -> ReaderScrubberEdge.BOTTOM
	normX < 0.5f -> ReaderScrubberEdge.LEFT
	else -> ReaderScrubberEdge.RIGHT
}

@Composable
private fun ReaderScrubberEdgeSnapOverlay(targetEdge: ReaderScrubberEdge) {
	val cyan = NemuriTheme.colors.cyan
	Box(modifier = Modifier.fillMaxSize()) {
		val barModifier = when (targetEdge) {
			ReaderScrubberEdge.TOP -> Modifier
				.align(Alignment.TopCenter)
				.statusBarsPadding()
				.padding(horizontal = 24.dp, vertical = 56.dp)
				.fillMaxWidth()
				.height(8.dp)
			ReaderScrubberEdge.BOTTOM -> Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(horizontal = 24.dp, vertical = 72.dp)
				.fillMaxWidth()
				.height(8.dp)
			ReaderScrubberEdge.LEFT -> Modifier
				.align(Alignment.CenterStart)
				.padding(start = 10.dp)
				.width(8.dp)
				.height(210.dp)
			ReaderScrubberEdge.RIGHT -> Modifier
				.align(Alignment.CenterEnd)
				.padding(end = 10.dp)
				.width(8.dp)
				.height(210.dp)
		}
		Box(
			modifier = barModifier
				.clip(CircleShape)
				.background(cyan.copy(alpha = 0.72f)),
		)
		Surface(
			color = NemuriTheme.colors.dock.copy(alpha = 0.92f),
			shape = RoundedCornerShape(999.dp),
			border = BorderStroke(1.dp, cyan.copy(alpha = 0.65f)),
			modifier = Modifier.align(Alignment.Center),
		) {
			Text(
				text = "Snap Scrubber: ${targetEdge.label}",
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Bold,
				color = cyan,
				modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
			)
		}
	}
}

@Composable
private fun ReaderMovableCapsuleScrubber(
	edge: ReaderScrubberEdge,
	currentIndex: Int,
	itemCount: Int,
	currentDisplayNumber: Int,
	totalDisplayNumber: Int,
	isRtl: Boolean,
	canGoPrevChapter: Boolean,
	canGoNextChapter: Boolean,
	screenWidthPx: Float,
	screenHeightPx: Float,
	onSelectIndex: (Int) -> Unit,
	onPrevChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onDragStateChange: (Boolean, ReaderScrubberEdge?) -> Unit,
	onSnapEdge: (ReaderScrubberEdge) -> Unit,
) {
	val haptic = LocalHapticFeedback.current
	val capsuleBg = NemuriTheme.colors.dock.copy(alpha = 0.90f)
	val capsuleBorder = BorderStroke(1.dp, NemuriTheme.colors.border.copy(alpha = 0.85f))
	val accentColor = NemuriTheme.colors.cyan

	var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
	var dragDelta by remember { mutableStateOf(Offset.Zero) }
	var dragFingerRoot by remember { mutableStateOf(Offset.Zero) }
	var lastSnapCandidate by remember { mutableStateOf<ReaderScrubberEdge?>(null) }

	val progress = if (itemCount > 1) {
		(currentIndex.toFloat() / (itemCount - 1).toFloat()).coerceIn(0f, 1f)
	} else {
		0f
	}

	val dragSnapModifier = Modifier
		.onGloballyPositioned { coords -> rootTopLeft = coords.positionInRoot() }
		.graphicsLayer {
			translationX = dragDelta.x
			translationY = dragDelta.y
			if (lastSnapCandidate != null) {
				scaleX = 1.05f
				scaleY = 1.05f
			}
		}
		.pointerInput(edge, screenWidthPx, screenHeightPx) {
			detectDragGesturesAfterLongPress(
				onDragStart = { startLocal ->
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					dragDelta = Offset.Zero
					dragFingerRoot = rootTopLeft + startLocal
					val nx = (dragFingerRoot.x / screenWidthPx).coerceIn(0f, 1f)
					val ny = (dragFingerRoot.y / screenHeightPx).coerceIn(0f, 1f)
					val candidate = resolveSnapEdge(nx, ny)
					lastSnapCandidate = candidate
					onDragStateChange(true, candidate)
				},
				onDrag = { change, amount ->
					change.consume()
					dragDelta += amount
					dragFingerRoot += amount
					val nx = (dragFingerRoot.x / screenWidthPx).coerceIn(0f, 1f)
					val ny = (dragFingerRoot.y / screenHeightPx).coerceIn(0f, 1f)
					val candidate = resolveSnapEdge(nx, ny)
					if (candidate != lastSnapCandidate) {
						haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						lastSnapCandidate = candidate
					}
					onDragStateChange(true, candidate)
				},
				onDragEnd = {
					val snapped = lastSnapCandidate
					dragDelta = Offset.Zero
					lastSnapCandidate = null
					onDragStateChange(false, null)
					if (snapped != null && snapped != edge) {
						onSnapEdge(snapped)
					}
				},
				onDragCancel = {
					dragDelta = Offset.Zero
					lastSnapCandidate = null
					onDragStateChange(false, null)
				},
			)
		}

	if (edge.isVertical) {
		Column(
			modifier = dragSnapModifier,
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
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
						contentDescription = "Previous chapter",
						tint = if (canGoPrevChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
						modifier = Modifier
							.size(20.dp)
							.rotate(90f),
					)
				}
			}

			Surface(
				color = capsuleBg,
				shape = RoundedCornerShape(20.dp),
				border = capsuleBorder,
				modifier = Modifier
					.width(40.dp)
					.height(204.dp),
			) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(vertical = 10.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.SpaceBetween,
				) {
					Text(
						text = "$currentDisplayNumber",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = accentColor,
					)

					var trackHeightPx by remember { mutableStateOf(1f) }
					val selectFromY: (Float) -> Unit = { yPx ->
						if (itemCount > 1) {
							val frac = (yPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
							val targetIdx = (frac * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
							onSelectIndex(targetIdx)
						}
					}

					Box(
						modifier = Modifier
							.weight(1f)
							.fillMaxWidth()
							.padding(vertical = 8.dp)
							.onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
							.pointerInput(itemCount) {
								detectTapGestures { offset -> selectFromY(offset.y) }
							}
							.pointerInput(itemCount) {
								detectDragGestures { change, _ ->
									change.consume()
									selectFromY(change.position.y)
								}
							},
						contentAlignment = Alignment.Center,
					) {
						Canvas(modifier = Modifier.fillMaxSize()) {
							val centerX = size.width / 2f
							val trackTop = 4.dp.toPx()
							val trackBottom = (size.height - 4.dp.toPx()).coerceAtLeast(trackTop + 1f)
							val thumbY = trackTop + (trackBottom - trackTop) * progress

							drawLine(
								color = Color.White.copy(alpha = 0.24f),
								start = Offset(centerX, trackTop),
								end = Offset(centerX, trackBottom),
								strokeWidth = 3.dp.toPx(),
								cap = StrokeCap.Round,
							)
							drawLine(
								color = accentColor,
								start = Offset(centerX, trackTop),
								end = Offset(centerX, thumbY),
								strokeWidth = 3.5.dp.toPx(),
								cap = StrokeCap.Round,
							)
							val halfThumbW = 10.dp.toPx()
							drawLine(
								color = accentColor,
								start = Offset(centerX - halfThumbW, thumbY),
								end = Offset(centerX + halfThumbW, thumbY),
								strokeWidth = 3.5.dp.toPx(),
								cap = StrokeCap.Round,
							)
						}
					}

					Text(
						text = "$totalDisplayNumber",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textSecondary,
					)
				}
			}

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
						contentDescription = "Next chapter",
						tint = if (canGoNextChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
						modifier = Modifier
							.size(20.dp)
							.rotate(90f),
					)
				}
			}
		}
	} else {
		Row(
			modifier = dragSnapModifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			val leftEnabled = if (isRtl) canGoNextChapter else canGoPrevChapter
			val rightEnabled = if (isRtl) canGoPrevChapter else canGoNextChapter
			Surface(
				color = capsuleBg,
				shape = RoundedCornerShape(13.dp),
				border = capsuleBorder,
				modifier = Modifier.size(42.dp),
			) {
				IconButton(
					onClick = { if (isRtl) onNextChapter() else onPrevChapter() },
					enabled = leftEnabled,
					modifier = Modifier.fillMaxSize(),
				) {
					Icon(
						imageVector = Icons.Default.SkipPrevious,
						contentDescription = if (isRtl) "Next chapter" else "Previous chapter",
						tint = if (leftEnabled) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
						modifier = Modifier.size(20.dp),
					)
				}
			}

			Surface(
				color = capsuleBg,
				shape = RoundedCornerShape(21.dp),
				border = capsuleBorder,
				modifier = Modifier
					.weight(1f)
					.height(42.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(horizontal = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					val leftLabel = if (isRtl) "$totalDisplayNumber" else "$currentDisplayNumber"
					val rightLabel = if (isRtl) "$currentDisplayNumber" else "$totalDisplayNumber"

					Text(
						text = leftLabel,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = if (!isRtl) FontWeight.Bold else FontWeight.Normal,
						color = if (!isRtl) accentColor else NemuriTheme.colors.textSecondary,
					)

					var trackWidthPx by remember { mutableStateOf(1f) }
					val visualFrac = if (isRtl) 1f - progress else progress
					val selectFromX: (Float) -> Unit = { xPx ->
						if (itemCount > 1) {
							val raw = (xPx / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
							val effective = if (isRtl) 1f - raw else raw
							val targetIdx = (effective * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
							onSelectIndex(targetIdx)
						}
					}

					Box(
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
							.pointerInput(itemCount, isRtl) {
								detectTapGestures { offset -> selectFromX(offset.x) }
							}
							.pointerInput(itemCount, isRtl) {
								detectDragGestures { change, _ ->
									change.consume()
									selectFromX(change.position.x)
								}
							},
						contentAlignment = Alignment.Center,
					) {
						Canvas(modifier = Modifier.fillMaxSize()) {
							val centerY = size.height / 2f
							val trackLeft = 4.dp.toPx()
							val trackRight = (size.width - 4.dp.toPx()).coerceAtLeast(trackLeft + 1f)
							val thumbX = trackLeft + (trackRight - trackLeft) * visualFrac

							drawLine(
								color = Color.White.copy(alpha = 0.24f),
								start = Offset(trackLeft, centerY),
								end = Offset(trackRight, centerY),
								strokeWidth = 3.dp.toPx(),
								cap = StrokeCap.Round,
							)
							if (isRtl) {
								drawLine(
									color = accentColor,
									start = Offset(thumbX, centerY),
									end = Offset(trackRight, centerY),
									strokeWidth = 3.5.dp.toPx(),
									cap = StrokeCap.Round,
								)
							} else {
								drawLine(
									color = accentColor,
									start = Offset(trackLeft, centerY),
									end = Offset(thumbX, centerY),
									strokeWidth = 3.5.dp.toPx(),
									cap = StrokeCap.Round,
								)
							}
							val halfThumbH = 9.dp.toPx()
							drawLine(
								color = accentColor,
								start = Offset(thumbX, centerY - halfThumbH),
								end = Offset(thumbX, centerY + halfThumbH),
								strokeWidth = 3.5.dp.toPx(),
								cap = StrokeCap.Round,
							)
						}
					}

					Text(
						text = rightLabel,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = if (isRtl) FontWeight.Bold else FontWeight.Normal,
						color = if (isRtl) accentColor else NemuriTheme.colors.textSecondary,
					)
				}
			}

			Surface(
				color = capsuleBg,
				shape = RoundedCornerShape(13.dp),
				border = capsuleBorder,
				modifier = Modifier.size(42.dp),
			) {
				IconButton(
					onClick = { if (isRtl) onPrevChapter() else onNextChapter() },
					enabled = rightEnabled,
					modifier = Modifier.fillMaxSize(),
				) {
					Icon(
						imageVector = Icons.Default.SkipNext,
						contentDescription = if (isRtl) "Previous chapter" else "Next chapter",
						tint = if (rightEnabled) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun ReaderSettingsModalSheet(
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	isLandscape: Boolean,
	isOrientationLocked: Boolean,
	onToggleManualRotate: () -> Unit,
	onResetAutoRotate: () -> Unit,
	onAutoScrollStarted: () -> Unit,
	onDismiss: () -> Unit,
) {
	val context = LocalContext.current
	var showShaderPreview by remember { mutableStateOf(false) }
	val isColorCorrectionActive = !uiState.colorFilter.isEmpty || uiState.colorWarmth > 0.01f
	var showColorFilterControls by rememberSaveable { mutableStateOf(isColorCorrectionActive) }
	var showAdvancedLayoutControls by rememberSaveable { mutableStateOf(false) }
	var showNeuralModelsList by rememberSaveable { mutableStateOf(uiState.neuralDownloadState.activeModelId != null) }
	val isVisualDiagram = uiState.selectionMenuStyle == SelectionMenuStyle.VISUAL_DIAGRAM
	val currentPage = uiState.pages.getOrNull(uiState.currentPageIndex)

	NemuriModalSheet(
		title = if (uiState.isTextMode) "Typography & Reader" else "Reader & Enhance",
		subtitle = if (uiState.isTextMode) {
			uiState.chapterTitle
		} else {
			"${uiState.readerMode.name.lowercase().replaceFirstChar { it.uppercase() }} • ${uiState.scrubberEdge.label}"
		},
		onDismiss = onDismiss,
	) {
		Column(
			modifier = Modifier
				.verticalScroll(rememberScrollState())
				.padding(vertical = 4.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			// 1. HIGH-FREQUENCY TOP: Visual Read Mode Selector Cards + Visual <-> Compact Menu Toggle
			if (!uiState.isTextMode) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "READING MODE",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
						)
						Text(
							text = "Remembered for this manga",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(999.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.clickable {
								val next = if (isVisualDiagram) {
									SelectionMenuStyle.COMPACT_LIST
								} else {
									SelectionMenuStyle.VISUAL_DIAGRAM
								}
								viewModel.setSelectionMenuStyle(next)
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(5.dp),
						) {
							Icon(
								imageVector = if (isVisualDiagram) Icons.Default.GridView else Icons.Default.ViewList,
								contentDescription = "Toggle menu style",
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(14.dp),
							)
							Text(
								text = if (isVisualDiagram) "Visual ↔ Compact" else "Compact ↔ Visual",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
							)
						}
					}
				}

				val modeEntries = listOf(
					ReaderMode.STANDARD to "Standard",
					ReaderMode.REVERSED to "R-to-L",
					ReaderMode.VERTICAL to "Vertical",
					ReaderMode.WEBTOON to "Webtoon",
				)
				if (isVisualDiagram) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						modeEntries.forEach { (mode, label) ->
							val selected = uiState.readerMode == mode
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(
									width = if (selected) 1.5.dp else 1.dp,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(12.dp))
									.clickable { viewModel.setReaderMode(mode) },
							) {
								Column(
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									ReaderModeDiagramCanvas(
										mode = mode,
										selected = selected,
										modifier = Modifier.size(width = 44.dp, height = 34.dp),
									)
									Text(
										text = label,
										style = MaterialTheme.typography.labelSmall,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										maxLines = 1,
									)
								}
							}
						}
					}
				} else {
					Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
						listOf(
							ReaderMode.STANDARD to "Left to Right (Paged)",
							ReaderMode.REVERSED to "Right to Left (Manga)",
							ReaderMode.VERTICAL to "Vertical (Paged)",
							ReaderMode.WEBTOON to "Webtoon (Continuous)",
						).forEach { (mode, label) ->
							ReaderCompactChoiceRow(
								label = label,
								selected = uiState.readerMode == mode,
								onClick = { viewModel.setReaderMode(mode) },
							)
						}
					}
				}
			} else {
				Text("PAPER THEME & TYPOGRAPHY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NemuriTheme.colors.cyan)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					listOf(
						"system" to "Night",
						"black" to "AMOLED",
						"gray" to "Sepia",
						"white" to "Light",
					).forEach { (key, label) ->
						val selected = uiState.epubTheme == key
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.weight(1f)
								.border(
									1.dp,
									if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									RoundedCornerShape(10.dp),
								)
								.clip(RoundedCornerShape(10.dp))
								.clickable { viewModel.setEpubTheme(key) },
						) {
							Text(
								text = label,
								style = MaterialTheme.typography.labelMedium,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 8.dp),
							)
						}
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					listOf(
						"serif" to "Serif",
						"sans-serif" to "Sans",
						"monospace" to "Mono",
					).forEach { (key, label) ->
						val selected = uiState.epubFontFamily == key
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.weight(1f)
								.border(
									1.dp,
									if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									RoundedCornerShape(10.dp),
								)
								.clip(RoundedCornerShape(10.dp))
								.clickable { viewModel.setEpubFontFamily(key) },
						) {
							Text(
								text = label,
								style = MaterialTheme.typography.labelMedium,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 8.dp),
							)
						}
					}
				}
				Text("Font Size (${uiState.epubFontSize}%)", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
				Slider(
					value = uiState.epubFontSize.toFloat(),
					onValueChange = { viewModel.setEpubFontSize(it.roundToInt()) },
					valueRange = 70f..200f,
					colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
				)
				Text("Line Spacing (${uiState.epubLineHeight}%)", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
				Slider(
					value = uiState.epubLineHeight.toFloat(),
					onValueChange = { viewModel.setEpubLineHeight(it.roundToInt()) },
					valueRange = 110f..230f,
					colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
				)
				Text("Margins (${uiState.epubHorizontalPadding} dp)", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
				Slider(
					value = uiState.epubHorizontalPadding.toFloat(),
					onValueChange = { viewModel.setEpubHorizontalPadding(it.roundToInt()) },
					valueRange = 8f..56f,
					colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
				)
			}

			HorizontalDivider(color = NemuriTheme.colors.border)

			// 2. 2×3 CONVENIENCE ACTION GRID (DropSauce Style)
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = "QUICK ACTIONS",
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.cyan,
				)
				if (isOrientationLocked) {
					Text(
						text = "Unlock Auto-Rotate",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.amber,
						modifier = Modifier
							.clip(RoundedCornerShape(6.dp))
							.clickable(onClick = onResetAutoRotate)
							.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
			}

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ReaderQuickActionTile(
						icon = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
						label = if (uiState.isBookmarked) "Bookmarked" else "Bookmark",
						subtitle = if (uiState.isBookmarked) "Tap to remove" else "Add marker",
						highlighted = uiState.isBookmarked,
						highlightTint = NemuriTheme.colors.amber,
						onClick = viewModel::toggleBookmark,
						modifier = Modifier.weight(1f),
					)
					ReaderQuickActionTile(
						icon = Icons.Default.SlowMotionVideo,
						label = "Auto-Scroll",
						subtitle = if (uiState.isAutoScrollEnabled) "Active" else "Play pill",
						highlighted = uiState.isAutoScrollEnabled,
						onClick = {
							val turningOn = !uiState.isAutoScrollEnabled
							viewModel.toggleAutoScroll()
							if (turningOn) onAutoScrollStarted()
						},
						modifier = Modifier.weight(1f),
					)
					ReaderQuickActionTile(
						icon = Icons.Default.Download,
						label = "Save Page",
						subtitle = "To Gallery",
						enabled = !uiState.isTextMode && currentPage != null,
						onClick = {
							viewModel.savePageToGallery(currentPage)
							onDismiss()
						},
						modifier = Modifier.weight(1f),
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ReaderQuickActionTile(
						icon = Icons.Default.Share,
						label = "Share Page",
						subtitle = "Send image",
						enabled = !uiState.isTextMode && currentPage != null,
						onClick = {
							viewModel.sharePage(currentPage) { chooser ->
								runCatching { context.startActivity(chooser) }
							}
							onDismiss()
						},
						modifier = Modifier.weight(1f),
					)
					ReaderQuickActionTile(
						icon = Icons.Default.ScreenRotation,
						label = "Rotate Screen",
						subtitle = when {
							isOrientationLocked && isLandscape -> "Locked Landscape"
							isOrientationLocked -> "Locked Portrait"
							isLandscape -> "To Portrait"
							else -> "To Landscape"
						},
						highlighted = isOrientationLocked,
						onClick = onToggleManualRotate,
						modifier = Modifier.weight(1f),
					)
					ReaderQuickActionTile(
						icon = Icons.Default.Palette,
						label = "Color Correction",
						subtitle = if (isColorCorrectionActive) "Active" else "Levels & Warmth",
						enabled = !uiState.isTextMode,
						highlighted = isColorCorrectionActive || showColorFilterControls,
						onClick = { showColorFilterControls = !showColorFilterControls },
						modifier = Modifier.weight(1f),
					)
				}
			}

			// Expandable Color Correction / Image Filter Panel
			if (!uiState.isTextMode && showColorFilterControls) {
				val cf = uiState.colorFilter
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(12.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "COLOR CORRECTION & IMAGE FILTER",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Bold,
								color = NemuriTheme.colors.cyan,
							)
							if (isColorCorrectionActive) {
								Text(
									text = "Reset",
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Bold,
									color = NemuriTheme.colors.amber,
									modifier = Modifier
										.clip(RoundedCornerShape(6.dp))
										.clickable(onClick = viewModel::resetColorFilter)
										.padding(horizontal = 6.dp, vertical = 2.dp),
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text("Brightness", style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textPrimary)
							Text("${(cf.brightness * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan)
						}
						Slider(
							value = cf.brightness,
							onValueChange = { viewModel.setColorFilter(cf.copy(brightness = it)) },
							valueRange = -0.5f..0.5f,
							colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
						)

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text("Contrast", style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textPrimary)
							Text("${(cf.contrast * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan)
						}
						Slider(
							value = cf.contrast,
							onValueChange = { viewModel.setColorFilter(cf.copy(contrast = it)) },
							valueRange = -0.5f..0.8f,
							colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
						)

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text("Warmth / Sepia", style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textPrimary)
							Text("${(uiState.colorWarmth * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan)
						}
						Slider(
							value = uiState.colorWarmth,
							onValueChange = viewModel::setColorWarmth,
							valueRange = 0f..1f,
							colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
						)

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							listOf(
								Triple("Grayscale", cf.isGrayscale) { viewModel.setColorFilter(cf.copy(isGrayscale = !cf.isGrayscale)) },
								Triple("Invert", cf.isInverted) { viewModel.setColorFilter(cf.copy(isInverted = !cf.isInverted)) },
								Triple("Warm Book", uiState.colorWarmth > 0.05f || cf.isBookBackground) {
									val nextWarmth = if (uiState.colorWarmth > 0.05f || cf.isBookBackground) 0f else 0.45f
									viewModel.setColorWarmth(nextWarmth)
								},
							).forEach { (label, active, toggle) ->
								Surface(
									color = if (active) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
									shape = RoundedCornerShape(10.dp),
									border = BorderStroke(
										1.dp,
										if (active) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(RoundedCornerShape(10.dp))
										.clickable(onClick = toggle),
								) {
									Text(
										text = label,
										style = MaterialTheme.typography.labelSmall,
										fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
										color = if (active) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										textAlign = TextAlign.Center,
										maxLines = 1,
										modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
									)
								}
							}
						}
					}
				}
			}

			HorizontalDivider(color = NemuriTheme.colors.border)

			// 3. QUICK READER TOGGLES (Keep Screen On, Volume Keys, Page Numbers, 4-Side Scrubber Edge, Smart Stitch)
			Text(
				text = "QUICK READER TOGGLES",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
				color = NemuriTheme.colors.cyan,
			)
			ReaderInlineSwitchRow(
				title = "Keep Screen On",
				subtitle = "Prevent device sleep while reading",
				checked = uiState.keepScreenOn,
				onCheckedChange = viewModel::setKeepScreenOn,
			)
			ReaderInlineSwitchRow(
				title = "Volume Keys Navigation",
				subtitle = "Turn pages or scroll using hardware volume buttons",
				checked = uiState.volumeButtonsEnabled,
				onCheckedChange = viewModel::setVolumeButtonsEnabled,
			)
			if (!uiState.isTextMode) {
				ReaderInlineSwitchRow(
					title = "Show Page Numbers",
					subtitle = "Display subtle corner page indicator when HUD is hidden",
					checked = uiState.showPageNumbers,
					onCheckedChange = viewModel::setShowPageNumbers,
				)
			}

			Text(
				text = "4-SIDE SCRUBBER EDGE (LONG-PRESS SCRUBBER TO DRAG)",
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.cyan,
			)
			if (isVisualDiagram && !uiState.isTextMode) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ReaderScrubberEdge.entries.forEach { edge ->
						val selected = uiState.scrubberEdge == edge
						val shortName = when (edge) {
							ReaderScrubberEdge.RIGHT -> "Right"
							ReaderScrubberEdge.LEFT -> "Left"
							ReaderScrubberEdge.BOTTOM -> "Bottom"
							ReaderScrubberEdge.TOP -> "Top"
						}
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(
								width = if (selected) 1.5.dp else 1.dp,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.setScrubberEdge(edge) },
						) {
							Column(
								modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.spacedBy(5.dp),
							) {
								ScrubberEdgeDiagramCanvas(
									edge = edge,
									selected = selected,
									modifier = Modifier.size(width = 30.dp, height = 34.dp),
								)
								Text(
									text = shortName,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									maxLines = 1,
								)
							}
						}
					}
				}
			} else {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ReaderScrubberEdge.entries.forEach { edge ->
						val selected = uiState.scrubberEdge == edge
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(10.dp),
							border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(10.dp))
								.clickable { viewModel.setScrubberEdge(edge) },
						) {
							Text(
								text = edge.name.lowercase().replaceFirstChar { it.uppercase() },
								style = MaterialTheme.typography.labelSmall,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								textAlign = TextAlign.Center,
								modifier = Modifier.padding(vertical = 8.dp),
							)
						}
					}
				}
			}

			if (!uiState.isTextMode) {
				ReaderInlineSwitchRow(
					title = "Seamless Smart Stitch",
					subtitle = "Merges uneven sliver slices into single pages with 0px seams",
					checked = uiState.isSeamlessStitchEnabled,
					onCheckedChange = viewModel::setSeamlessStitchEnabled,
				)
			}

			if (!uiState.isTextMode) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable { showAdvancedLayoutControls = !showAdvancedLayoutControls },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Tap Zones, Webtoon & Two-Page Spreads",
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "${uiState.tapZoneMode.label} • Zoom-out ${uiState.defaultWebtoonZoomOutPercent}%",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Icon(
							imageVector = if (showAdvancedLayoutControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
							contentDescription = "Expand layout options",
							tint = NemuriTheme.colors.cyan,
						)
					}
				}

				if (showAdvancedLayoutControls) {
					Text(
						text = "TAP-ZONE LAYOUT",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					if (isVisualDiagram) {
						Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
							ReaderTapZoneMode.entries.chunked(2).forEach { rowModes ->
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									rowModes.forEach { tz ->
										val selected = uiState.tapZoneMode == tz
										Surface(
											color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
											shape = RoundedCornerShape(12.dp),
											border = BorderStroke(
												width = if (selected) 1.5.dp else 1.dp,
												color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											),
											modifier = Modifier
												.weight(1f)
												.clip(RoundedCornerShape(12.dp))
												.clickable { viewModel.setTapZoneMode(tz) },
										) {
											Row(
												modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.spacedBy(10.dp),
											) {
												TapZoneDiagramCanvas(
													mode = tz,
													selected = selected,
													modifier = Modifier.size(width = 30.dp, height = 38.dp),
												)
												Text(
													text = tz.label,
													style = MaterialTheme.typography.labelMedium,
													fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
													color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
													maxLines = 1,
												)
											}
										}
									}
								}
							}
							Text(
								text = uiState.tapZoneMode.description,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
					} else {
						Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
							ReaderTapZoneMode.entries.forEach { tz ->
								ReaderCompactChoiceRow(
									label = "${tz.label} — ${tz.description}",
									selected = uiState.tapZoneMode == tz,
									onClick = { viewModel.setTapZoneMode(tz) },
								)
							}
						}
					}

					ReaderInlineSwitchRow(
						title = "Smart Double-Tap Fill Zoom",
						subtitle = "Snaps pillarboxed/stitched pages edge-to-edge or 2x focal zoom",
						checked = uiState.isDoubleTapSmartZoom,
						onCheckedChange = viewModel::setDoubleTapSmartZoom,
					)
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "Default Webtoon Zoom-Out",
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "${uiState.defaultWebtoonZoomOutPercent}%",
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.cyan,
							)
						}
						Slider(
							value = uiState.defaultWebtoonZoomOutPercent.toFloat(),
							onValueChange = { viewModel.setDefaultWebtoonZoomOutPercent(it.roundToInt()) },
							valueRange = 0f..50f,
							colors = SliderDefaults.colors(
								thumbColor = NemuriTheme.colors.cyan,
								activeTrackColor = NemuriTheme.colors.cyan,
								inactiveTrackColor = NemuriTheme.colors.border,
							),
						)
					}
					ReaderInlineSwitchRow(
						title = "Webtoon Panel Gaps",
						subtitle = "Add 12dp breathing room between vertical webtoon blocks",
						checked = uiState.isWebtoonGapsEnabled,
						onCheckedChange = viewModel::setWebtoonGapsEnabled,
					)
					ReaderInlineSwitchRow(
						title = "Two-Page in Landscape",
						subtitle = "Pair portrait pages into spreads automatically in landscape",
						checked = uiState.isDoubleOnLandscape,
						onCheckedChange = viewModel::setDoubleOnLandscape,
					)
					ReaderInlineSwitchRow(
						title = "Foldable Auto Two-Page",
						subtitle = "Pair portrait pages automatically on unfolded inner screens",
						checked = uiState.isDoubleOnFoldable,
						onCheckedChange = viewModel::setDoubleOnFoldable,
					)
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								text = "Spread Sensitivity",
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "${(uiState.doublePagesSensitivity * 100f).roundToInt()}%",
								style = MaterialTheme.typography.labelLarge,
								color = NemuriTheme.colors.cyan,
							)
						}
						Slider(
							value = uiState.doublePagesSensitivity,
							onValueChange = viewModel::setDoublePagesSensitivity,
							valueRange = 0f..1f,
							colors = SliderDefaults.colors(
								thumbColor = NemuriTheme.colors.cyan,
								activeTrackColor = NemuriTheme.colors.cyan,
								inactiveTrackColor = NemuriTheme.colors.border,
							),
						)
					}
				}

				HorizontalDivider(color = NemuriTheme.colors.border)

				// 4. UPSCALER & NEURAL MODELS (Below everyday controls, with active delegate strip + collapsible model downloader)
				Text(
					text = "UPSCALER & NEURAL SUPER-RESOLUTION",
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.cyan,
				)
				HardwareTelemetryStrip(
					activeDelegateOverride = uiState.activeNeuralDelegate,
				)

				Text(
					text = "Universal GPU Shaders" + if (!UpscaleEffect.isSupported) " (Requires Android 13+)" else "",
					style = MaterialTheme.typography.labelLarge,
					color = NemuriTheme.colors.cyan,
				)
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					ReaderShaderPreset.entries.chunked(2).forEach { rowPresets ->
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							rowPresets.forEach { preset ->
								val selected = uiState.shaderConfig.preset == preset
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(10.dp),
									modifier = Modifier
										.weight(1f)
										.border(
											1.dp,
											if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											RoundedCornerShape(10.dp),
										)
										.clip(RoundedCornerShape(10.dp))
										.clickable(enabled = UpscaleEffect.isSupported) {
											viewModel.setShaderPreset(preset)
										},
								) {
									Text(
										text = preset.label,
										style = MaterialTheme.typography.labelSmall,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										textAlign = TextAlign.Center,
										maxLines = 1,
										modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
									)
								}
							}
						}
					}
				}

				ReaderInlineSwitchRow(
					title = "Halftone Moiré Descreen",
					subtitle = "Smooths halftone dot noise while keeping ink sharp",
					checked = uiState.shaderConfig.descreen,
					enabled = UpscaleEffect.isSupported,
					onCheckedChange = viewModel::setShaderDescreen,
				)
				ReaderInlineSwitchRow(
					title = "Ink Contrast Levels",
					subtitle = "Deepens faded blacks & cleans muddy scan paper",
					checked = uiState.shaderConfig.inkLevels,
					enabled = UpscaleEffect.isSupported,
					onCheckedChange = viewModel::setShaderInkLevels,
				)
				Text(
					text = "Enhancement Intensity (${(uiState.shaderConfig.strength * 100f).roundToInt()}%)",
					style = MaterialTheme.typography.labelLarge,
					color = NemuriTheme.colors.textMuted,
				)
				Slider(
					value = uiState.shaderConfig.strength,
					onValueChange = viewModel::setShaderStrength,
					valueRange = 0.3f..1.0f,
					enabled = UpscaleEffect.isSupported && uiState.shaderConfig.isActive,
					colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
				)

				if ((UpscaleEffect.isSupported || uiState.isNeuralUpscaleSupported) && currentPage != null) {
					Button(
						onClick = { showShaderPreview = true },
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.surfaceElevated,
							contentColor = NemuriTheme.colors.cyan,
						),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
					) {
						Text("Compare Split (Before / After Preview)")
					}
					if (showShaderPreview) {
						ShaderSplitPreviewDialog(
							page = currentPage,
							config = uiState.shaderConfig,
							isNeuralActive = uiState.isNeuralUpscaleEnabled && uiState.hasReadyNeuralModel,
							neuralModelLabel = uiState.activeNeuralModelLabel,
							neuralRevision = uiState.neuralRevision,
							loadOriginalPage = viewModel::loadRawPageImage,
							loadEnhancedPage = viewModel::loadPageImage,
							onDismiss = { showShaderPreview = false },
						)
					}
				}

				HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 2.dp))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NemuriTheme.colors.mergePurple, modifier = Modifier.size(16.dp))
							Text("Neural 2x Super-Resolution", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.mergePurple)
						}
						Text(
							text = if (!uiState.isNeuralUpscaleSupported) {
								"Available in GitHub builds with native NPU/GPU/ONNX runtimes"
							} else {
								"${uiState.activeNeuralDelegate} • Adaptive 256/512px tiled NPU/GPU/CPU"
							},
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					Switch(
						checked = uiState.isNeuralUpscaleEnabled,
						onCheckedChange = viewModel::setNeuralUpscaleEnabled,
						enabled = uiState.isNeuralUpscaleSupported,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.mergePurple,
						),
					)
				}

				if (uiState.isNeuralUpscaleSupported) {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						NeuralAccelerator.entries.forEach { accel ->
							val selected = uiState.neuralAccelerator == accel
							Surface(
								color = if (selected) NemuriTheme.colors.mergePurple.copy(alpha = 0.18f) else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier
									.weight(1f)
									.border(
										1.dp,
										if (selected) NemuriTheme.colors.mergePurple else NemuriTheme.colors.border,
										RoundedCornerShape(10.dp),
									)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setNeuralAccelerator(accel) },
							) {
								Text(
									text = when (accel) {
										NeuralAccelerator.AUTO -> "Auto"
										NeuralAccelerator.NPU -> "NPU"
										NeuralAccelerator.GPU -> "GPU"
										NeuralAccelerator.CPU -> "CPU"
									},
									style = MaterialTheme.typography.labelSmall,
									color = if (selected) NemuriTheme.colors.mergePurple else NemuriTheme.colors.textSecondary,
									textAlign = TextAlign.Center,
									maxLines = 1,
									modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
								)
							}
						}
					}

					val dl = uiState.neuralDownloadState
					val catalogModels = uiState.neuralCatalog.filter { it.isDownloadable || it.isDownloaded }
					val isModelsExpanded = showNeuralModelsList || dl.activeModelId != null
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f)),
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(12.dp))
							.clickable { showNeuralModelsList = !showNeuralModelsList },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = "Model Downloader (${catalogModels.size} models)",
									style = MaterialTheme.typography.labelLarge,
									color = NemuriTheme.colors.textPrimary,
								)
								Text(
									text = "Active: ${uiState.activeNeuralModelLabel}",
									style = MaterialTheme.typography.labelSmall,
									color = if (uiState.hasReadyNeuralModel) NemuriTheme.colors.mergePurple else NemuriTheme.colors.textMuted,
								)
							}
							Icon(
								imageVector = if (isModelsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
								contentDescription = "Toggle neural model catalog",
								tint = NemuriTheme.colors.mergePurple,
							)
						}
					}

					if (isModelsExpanded) {
						catalogModels.forEach { model ->
							val isDownloadingThis = dl.activeModelId == model.id
							Surface(
								color = if (model.isSelected && model.isDownloaded) {
									NemuriTheme.colors.mergePurple.copy(alpha = 0.14f)
								} else {
									NemuriTheme.colors.surfaceElevated
								},
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(
									1.dp,
									if (model.isSelected && model.isDownloaded) NemuriTheme.colors.mergePurple else NemuriTheme.colors.border,
								),
								modifier = Modifier.fillMaxWidth(),
							) {
								Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween,
									) {
										Column(modifier = Modifier.weight(1f)) {
											Text(model.title, style = MaterialTheme.typography.labelMedium, color = NemuriTheme.colors.textPrimary)
											Text(model.subtitle, style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
										}
										if (model.isDownloaded) {
											Row(verticalAlignment = Alignment.CenterVertically) {
												TextButton(onClick = { viewModel.selectNeuralModel(model.id) }) {
													Text(
														text = if (model.isSelected) "Active" else "Use",
														color = if (model.isSelected) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
													)
												}
												IconButton(onClick = { viewModel.deleteNeuralModel(model.id) }, modifier = Modifier.size(28.dp)) {
													Icon(Icons.Default.Delete, contentDescription = "Delete model", tint = NemuriTheme.colors.danger, modifier = Modifier.size(16.dp))
												}
											}
										} else if (isDownloadingThis) {
											IconButton(onClick = viewModel::cancelNeuralDownload, modifier = Modifier.size(28.dp)) {
												Icon(Icons.Default.Close, contentDescription = "Cancel", tint = NemuriTheme.colors.amber, modifier = Modifier.size(16.dp))
											}
										} else {
											TextButton(
												onClick = { viewModel.downloadNeuralModel(model.id) },
												enabled = dl.activeModelId == null,
											) {
												Icon(Icons.Default.Download, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(15.dp))
												Spacer(Modifier.width(4.dp))
												Text(model.sizeLabel, color = NemuriTheme.colors.cyan)
											}
										}
									}
									if (isDownloadingThis) {
										val frac = if (dl.totalBytes > 0L) (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
										LinearProgressIndicator(
											progress = { frac },
											color = NemuriTheme.colors.mergePurple,
											trackColor = NemuriTheme.colors.border,
											modifier = Modifier.fillMaxWidth(),
										)
									}
								}
							}
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "WebP cache: ${String.format(java.util.Locale.US, "%.1f MB / 250.0 MB", uiState.neuralCacheBytes / (1024f * 1024f))}",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
						TextButton(
							onClick = viewModel::clearNeuralCache,
							enabled = uiState.neuralCacheBytes > 0L,
						) {
							Text(
								text = "Clear Upscale Cache",
								color = if (uiState.neuralCacheBytes > 0L) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ReaderCompactChoiceRow(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(10.dp),
		border = BorderStroke(
			1.dp,
			if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
		),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.bodySmall,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
				color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
				modifier = Modifier.weight(1f),
			)
			if (selected) {
				Icon(
					imageVector = Icons.Default.Check,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(16.dp),
				)
			}
		}
	}
}

@Composable
private fun ReaderInlineSwitchRow(
	title: String,
	subtitle: String,
	checked: Boolean,
	enabled: Boolean = true,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				color = if (enabled) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textMuted,
			)
		}
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			enabled = enabled,
			colors = SwitchDefaults.colors(
				checkedThumbColor = NemuriTheme.colors.onCyan,
				checkedTrackColor = NemuriTheme.colors.cyan,
			),
		)
	}
}

@Composable
private fun ReaderModeDiagramCanvas(
	mode: ReaderMode,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val primary = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary
	val frame = if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.55f) else NemuriTheme.colors.border
	Canvas(modifier = modifier) {
		val stroke = Stroke(width = 1.4.dp.toPx())
		val arrowStroke = 1.8.dp.toPx()
		when (mode) {
			ReaderMode.STANDARD, ReaderMode.REVERSED -> {
				val pageW = size.width * 0.42f
				val pageH = size.height * 0.84f
				val top = (size.height - pageH) * 0.5f
				drawRoundRect(
					color = frame,
					topLeft = Offset(size.width * 0.06f, top),
					size = ComposeSize(pageW, pageH),
					cornerRadius = CornerRadius(3.dp.toPx()),
					style = stroke,
				)
				drawRoundRect(
					color = frame,
					topLeft = Offset(size.width * 0.52f, top),
					size = ComposeSize(pageW, pageH),
					cornerRadius = CornerRadius(3.dp.toPx()),
					style = stroke,
				)
				val cy = size.height * 0.5f
				val startX = if (mode == ReaderMode.STANDARD) size.width * 0.24f else size.width * 0.76f
				val endX = if (mode == ReaderMode.STANDARD) size.width * 0.76f else size.width * 0.24f
				drawLine(primary, Offset(startX, cy), Offset(endX, cy), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				val dir = if (mode == ReaderMode.STANDARD) -1f else 1f
				val head = 4.dp.toPx()
				drawLine(primary, Offset(endX, cy), Offset(endX + dir * head, cy - head * 0.8f), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				drawLine(primary, Offset(endX, cy), Offset(endX + dir * head, cy + head * 0.8f), strokeWidth = arrowStroke, cap = StrokeCap.Round)
			}
			ReaderMode.VERTICAL -> {
				val w = size.width * 0.62f
				val left = (size.width - w) * 0.5f
				val h = size.height * 0.40f
				drawRoundRect(frame, Offset(left, size.height * 0.06f), ComposeSize(w, h), CornerRadius(3.dp.toPx()), stroke)
				drawRoundRect(frame, Offset(left, size.height * 0.54f), ComposeSize(w, h), CornerRadius(3.dp.toPx()), stroke)
				val cx = size.width * 0.5f
				val startY = size.height * 0.24f
				val endY = size.height * 0.76f
				drawLine(primary, Offset(cx, startY), Offset(cx, endY), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				val head = 3.8.dp.toPx()
				drawLine(primary, Offset(cx, endY), Offset(cx - head, endY - head), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				drawLine(primary, Offset(cx, endY), Offset(cx + head, endY - head), strokeWidth = arrowStroke, cap = StrokeCap.Round)
			}
			ReaderMode.WEBTOON -> {
				val w = size.width * 0.62f
				val left = (size.width - w) * 0.5f
				drawRoundRect(
					color = frame,
					topLeft = Offset(left, size.height * 0.06f),
					size = ComposeSize(w, size.height * 0.88f),
					cornerRadius = CornerRadius(3.dp.toPx()),
					style = stroke,
				)
				drawLine(frame, Offset(left, size.height * 0.36f), Offset(left + w, size.height * 0.36f), strokeWidth = 1.dp.toPx())
				drawLine(frame, Offset(left, size.height * 0.66f), Offset(left + w, size.height * 0.66f), strokeWidth = 1.dp.toPx())
				val cx = size.width * 0.5f
				val startY = size.height * 0.22f
				val endY = size.height * 0.80f
				drawLine(primary, Offset(cx, startY), Offset(cx, endY), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				val head = 3.8.dp.toPx()
				drawLine(primary, Offset(cx, endY), Offset(cx - head, endY - head), strokeWidth = arrowStroke, cap = StrokeCap.Round)
				drawLine(primary, Offset(cx, endY), Offset(cx + head, endY - head), strokeWidth = arrowStroke, cap = StrokeCap.Round)
			}
		}
	}
}

@Composable
private fun TapZoneDiagramCanvas(
	mode: ReaderTapZoneMode,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val prevCol = NemuriTheme.colors.amber.copy(alpha = if (selected) 0.48f else 0.28f)
	val nextCol = NemuriTheme.colors.cyan.copy(alpha = if (selected) 0.52f else 0.30f)
	val hudCol = NemuriTheme.colors.textMuted.copy(alpha = 0.22f)
	val borderCol = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border
	Canvas(modifier = modifier) {
		val w = size.width
		val h = size.height
		when (mode) {
			ReaderTapZoneMode.DEFAULT -> {
				drawRect(prevCol, Offset(0f, 0f), ComposeSize(w * 0.33f, h))
				drawRect(hudCol, Offset(w * 0.33f, 0f), ComposeSize(w * 0.34f, h))
				drawRect(nextCol, Offset(w * 0.67f, 0f), ComposeSize(w * 0.33f, h))
			}
			ReaderTapZoneMode.L_SHAPED -> {
				val prevPath = Path().apply {
					moveTo(0f, 0f)
					lineTo(w, 0f)
					lineTo(w, h * 0.25f)
					lineTo(w * 0.33f, h * 0.25f)
					lineTo(w * 0.33f, h * 0.75f)
					lineTo(0f, h * 0.75f)
					close()
				}
				drawPath(prevPath, prevCol)
				drawRect(hudCol, Offset(w * 0.33f, h * 0.25f), ComposeSize(w * 0.34f, h * 0.50f))
				val nextPath = Path().apply {
					moveTo(w * 0.67f, h * 0.25f)
					lineTo(w, h * 0.25f)
					lineTo(w, h)
					lineTo(0f, h)
					lineTo(0f, h * 0.75f)
					lineTo(w * 0.67f, h * 0.75f)
					close()
				}
				drawPath(nextPath, nextCol)
			}
			ReaderTapZoneMode.KINDLE -> {
				drawRect(hudCol, Offset(0f, 0f), ComposeSize(w, h * 0.20f))
				drawRect(prevCol, Offset(0f, h * 0.20f), ComposeSize(w * 0.26f, h * 0.80f))
				drawRect(nextCol, Offset(w * 0.26f, h * 0.20f), ComposeSize(w * 0.74f, h * 0.80f))
			}
			ReaderTapZoneMode.EDGE_ONLY -> {
				drawRect(prevCol, Offset(0f, 0f), ComposeSize(w * 0.18f, h))
				drawRect(hudCol, Offset(w * 0.18f, 0f), ComposeSize(w * 0.64f, h))
				drawRect(nextCol, Offset(w * 0.82f, 0f), ComposeSize(w * 0.18f, h))
			}
		}
		drawRoundRect(
			color = borderCol,
			topLeft = Offset.Zero,
			size = size,
			cornerRadius = CornerRadius(4.dp.toPx()),
			style = Stroke(width = 1.2.dp.toPx()),
		)
	}
}

@Composable
private fun ScrubberEdgeDiagramCanvas(
	edge: ReaderScrubberEdge,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val accent = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary
	val frame = if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.45f) else NemuriTheme.colors.border
	Canvas(modifier = modifier) {
		drawRoundRect(
			color = frame,
			topLeft = Offset.Zero,
			size = size,
			cornerRadius = CornerRadius(4.dp.toPx()),
			style = Stroke(width = 1.2.dp.toPx()),
		)
		val pad = 4.dp.toPx()
		val thick = 3.2.dp.toPx()
		when (edge) {
			ReaderScrubberEdge.RIGHT -> drawLine(
				color = accent,
				start = Offset(size.width - pad, pad * 1.4f),
				end = Offset(size.width - pad, size.height - pad * 1.4f),
				strokeWidth = thick,
				cap = StrokeCap.Round,
			)
			ReaderScrubberEdge.LEFT -> drawLine(
				color = accent,
				start = Offset(pad, pad * 1.4f),
				end = Offset(pad, size.height - pad * 1.4f),
				strokeWidth = thick,
				cap = StrokeCap.Round,
			)
			ReaderScrubberEdge.BOTTOM -> drawLine(
				color = accent,
				start = Offset(pad * 1.4f, size.height - pad),
				end = Offset(size.width - pad * 1.4f, size.height - pad),
				strokeWidth = thick,
				cap = StrokeCap.Round,
			)
			ReaderScrubberEdge.TOP -> drawLine(
				color = accent,
				start = Offset(pad * 1.4f, pad),
				end = Offset(size.width - pad * 1.4f, pad),
				strokeWidth = thick,
				cap = StrokeCap.Round,
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderChallengeScreen(
	title: String,
	message: String,
	actionLabel: String,
	onAction: () -> Unit,
	onRetry: () -> Unit,
	onFindAlternative: (() -> Unit)? = null,
	onBack: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(title, style = MaterialTheme.typography.titleMedium, color = NemuriTheme.colors.textPrimary)
		Spacer(modifier = Modifier.height(8.dp))
		Text(message, style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textMuted, textAlign = TextAlign.Center)
		Spacer(modifier = Modifier.height(16.dp))
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Button(
				onClick = onAction,
				colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.cyan, contentColor = NemuriTheme.colors.onCyan),
			) {
				Text(actionLabel)
			}
			TextButton(onClick = onRetry) { Text("Retry", color = NemuriTheme.colors.cyan) }
			if (onFindAlternative != null) {
				TextButton(onClick = onFindAlternative) {
					Text("Find Alternative", color = NemuriTheme.colors.textPrimary)
				}
			}
			TextButton(onClick = onBack) { Text("Close", color = NemuriTheme.colors.textSecondary) }
		}
	}
}

private fun openMigrateFromReader(context: Context, mangaId: Long) {
	if (mangaId == 0L) return
	val intent = Intent(context, MainActivity::class.java)
		.putExtra(EXTRA_OPEN_MIGRATE_MANGA_ID, mangaId)
		.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
	context.startActivity(intent)
	(context as? android.app.Activity)?.finish()
}

private data class AppliedShaderState(
	val shaderKey: Pair<ReaderShaderConfig, Float>?,
	val colorFilter: ReaderColorFilter = ReaderColorFilter.EMPTY,
	val quantizedWarmth: Int = 0,
)

private class SsivHolderState(
	var imageKey: String? = null,
	var shaderState: AppliedShaderState? = null,
)

private fun ssivState(ssiv: SubsamplingScaleImageView): SsivHolderState =
	(ssiv.tag as? SsivHolderState) ?: SsivHolderState().also { ssiv.tag = it }

private fun buildEffectiveColorFilter(
	colorFilter: ReaderColorFilter,
	warmth: Float,
): ColorMatrixColorFilter? {
	val w = warmth.coerceIn(0f, 1f)
	val effectiveWarmth = when {
		w > 0.01f -> w
		colorFilter.isBookBackground -> 0.45f
		else -> 0f
	}
	if (!colorFilter.isGrayscale &&
		!colorFilter.isInverted &&
		colorFilter.brightness == 0f &&
		colorFilter.contrast == 0f &&
		effectiveWarmth <= 0f
	) {
		return null
	}
	val cm = ColorMatrix()
	if (colorFilter.isGrayscale) {
		cm.setSaturation(0f)
	}
	if (colorFilter.isInverted) {
		cm.postConcat(
			ColorMatrix(
				floatArrayOf(
					-1f, 0f, 0f, 0f, 255f,
					0f, -1f, 0f, 0f, 255f,
					0f, 0f, -1f, 0f, 255f,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	if (colorFilter.brightness != 0f) {
		val bScale = (colorFilter.brightness + 1f).coerceAtLeast(0.05f)
		cm.postConcat(ColorMatrix().apply { setScale(bScale, bScale, bScale, 1f) })
	}
	if (colorFilter.contrast != 0f) {
		val cScale = (colorFilter.contrast + 1f).coerceAtLeast(0.05f)
		val translate = (-0.5f * cScale + 0.5f) * 255f
		cm.postConcat(
			ColorMatrix(
				floatArrayOf(
					cScale, 0f, 0f, 0f, translate,
					0f, cScale, 0f, 0f, translate,
					0f, 0f, cScale, 0f, translate,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	if (effectiveWarmth > 0f) {
		val gScale = 1f - (0.09f * effectiveWarmth)
		val bScale = 1f - (0.28f * effectiveWarmth)
		cm.postConcat(
			ColorMatrix(
				floatArrayOf(
					1f, 0f, 0f, 0f, 0f,
					0f, gScale, 0f, 0f, 0f,
					0f, 0f, bScale, 0f, 0f,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	return ColorMatrixColorFilter(cm)
}

private fun applyShaderToSsiv(
	ssiv: SubsamplingScaleImageView,
	config: ReaderShaderConfig,
	colorFilter: ReaderColorFilter = ReaderColorFilter.EMPTY,
	colorWarmth: Float = 0f,
) {
	val pm = ssiv.context.getSystemService(Context.POWER_SERVICE) as? PowerManager
	val isPowerSave = pm?.isPowerSaveMode == true
	val fitScale = if (ssiv.sWidth > 0 && ssiv.width > 0) {
		(ssiv.width.toFloat() / ssiv.sWidth.toFloat()).coerceAtLeast(UpscaleEffect.MIN_SCALE)
	} else {
		UpscaleEffect.MIN_SCALE
	}
	val quantizedWarmth = (colorWarmth.coerceIn(0f, 1f) * 100f).roundToInt()
	val desired = AppliedShaderState(
		shaderKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config.isActive && !isPowerSave) {
			config to fitScale
		} else {
			null
		},
		colorFilter = colorFilter,
		quantizedWarmth = quantizedWarmth,
	)
	val holder = ssivState(ssiv)
	if (holder.shaderState == desired) return
	holder.shaderState = desired

	val matrixFilter = buildEffectiveColorFilter(colorFilter, quantizedWarmth / 100f)
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val cfEffect = matrixFilter?.let { RenderEffect.createColorFilterEffect(it) }
		val upscaleEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && desired.shaderKey != null) {
			UpscaleEffect.create(fitScale, config)
		} else {
			null
		}
		val combined = when {
			cfEffect != null && upscaleEffect != null -> RenderEffect.createChainEffect(cfEffect, upscaleEffect)
			cfEffect != null -> cfEffect
			upscaleEffect != null -> upscaleEffect
			else -> null
		}
		ssiv.setRenderEffect(combined)
	} else {
		if (matrixFilter != null) {
			ssiv.setLayerType(
				View.LAYER_TYPE_HARDWARE,
				Paint().apply { this.colorFilter = matrixFilter },
			)
		} else {
			ssiv.setLayerType(View.LAYER_TYPE_NONE, null)
		}
	}
}

@Composable
private fun ShaderSplitPreviewDialog(
	page: ReaderPage,
	config: ReaderShaderConfig,
	isNeuralActive: Boolean,
	neuralModelLabel: String,
	neuralRevision: Int,
	loadOriginalPage: suspend (ReaderPage) -> Result<Pair<Uri, Rect?>>,
	loadEnhancedPage: suspend (ReaderPage, Boolean) -> Result<Pair<Uri, Rect?>>,
	onDismiss: () -> Unit,
) {
	val context = LocalContext.current
	var origBitmap by remember(page.id) { mutableStateOf<Bitmap?>(null) }
	var enhBitmap by remember(page.id, neuralRevision) { mutableStateOf<Bitmap?>(null) }
	var loadFailed by remember(page.id) { mutableStateOf(false) }
	var splitFraction by remember { mutableStateOf(0.5f) }

	suspend fun decodeUriBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
		runCatching {
			val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
			var sample = 1
			while ((opts.outWidth / sample) > 1440 || (opts.outHeight / sample) > 2200) {
				sample *= 2
			}
			val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
			context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
		}.getOrNull()
	}

	LaunchedEffect(page.id, neuralRevision, isNeuralActive) {
		loadFailed = false
		val origUri = withContext(Dispatchers.IO) { loadOriginalPage(page).getOrNull()?.first }
		val loadedOrig = origUri?.let { decodeUriBitmap(it) }
		origBitmap = loadedOrig
		if (loadedOrig == null) {
			loadFailed = true
			return@LaunchedEffect
		}
		enhBitmap = if (isNeuralActive) {
			val enhUri = withContext(Dispatchers.IO) { loadEnhancedPage(page, false).getOrNull()?.first }
			if (enhUri != null && enhUri != origUri) {
				decodeUriBitmap(enhUri) ?: loadedOrig
			} else {
				loadedOrig
			}
		} else {
			loadedOrig
		}
	}

	val enhancedSummary = remember(config, isNeuralActive, neuralModelLabel) {
		when {
			isNeuralActive && config.isActive -> "Neural 2x + ${config.summaryLabel()}"
			isNeuralActive -> "Neural 2x ($neuralModelLabel)"
			else -> config.summaryLabel()
		}
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			color = NemuriTheme.colors.sheet,
			shape = RoundedCornerShape(18.dp),
			modifier = Modifier
				.fillMaxSize()
				.padding(14.dp),
		) {
			Column(modifier = Modifier.fillMaxSize()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 10.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Upscaler Split Preview", style = MaterialTheme.typography.titleMedium, color = NemuriTheme.colors.textPrimary)
						Text("Left: Original • Right: $enhancedSummary", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					TextButton(onClick = onDismiss) {
						Text("Done", color = NemuriTheme.colors.cyan)
					}
				}

				BoxWithConstraints(
					modifier = Modifier
						.weight(1f)
						.fillMaxWidth()
						.background(Color.Black)
						.pointerInput(Unit) {
							detectHorizontalDragGestures { change, _ ->
								change.consume()
								if (size.width > 0) {
									splitFraction = (change.position.x / size.width.toFloat()).coerceIn(0.1f, 0.9f)
								}
							}
						},
					contentAlignment = Alignment.Center,
				) {
					val leftBmp = origBitmap
					val rightBmp = enhBitmap
					when {
						loadFailed -> {
							Text(
								text = "Unable to decode page preview",
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textMuted,
							)
						}

						leftBmp == null || rightBmp == null -> {
							CircularProgressIndicator(color = NemuriTheme.colors.cyan)
						}

						else -> {
							val leftShape = remember(splitFraction) {
								GenericShape { size, _ ->
									addRect(ComposeRect(0f, 0f, size.width * splitFraction, size.height))
								}
							}
							val rightShape = remember(splitFraction) {
								GenericShape { size, _ ->
									addRect(ComposeRect(size.width * splitFraction, 0f, size.width, size.height))
								}
							}

							AndroidView(
								factory = { ctx ->
									ImageView(ctx).apply {
										scaleType = ImageView.ScaleType.FIT_CENTER
									}
								},
								update = { iv -> iv.setImageBitmap(leftBmp) },
								modifier = Modifier
									.fillMaxSize()
									.clip(leftShape),
							)

							AndroidView(
								factory = { ctx ->
									ImageView(ctx).apply {
										scaleType = ImageView.ScaleType.FIT_CENTER
									}
								},
								update = { iv ->
									iv.setImageBitmap(rightBmp)
									val desired = AppliedShaderState(if (config.isActive) (config to 2.0f) else null)
									if (iv.tag != desired) {
										iv.tag = desired
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config.isActive) {
											iv.setRenderEffect(UpscaleEffect.create(2.0f, config))
										} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
											iv.setRenderEffect(null)
										}
									}
								},
								modifier = Modifier
									.fillMaxSize()
									.clip(rightShape),
							)

							// Split divider line & Original / Enhanced corner labels
							Box(
								modifier = Modifier
									.align(Alignment.TopStart)
									.offset(x = maxWidth * splitFraction - 1.dp)
									.width(2.dp)
									.fillMaxHeight()
									.background(NemuriTheme.colors.cyan),
							)
							Surface(
								color = Color.Black.copy(alpha = 0.65f),
								shape = RoundedCornerShape(6.dp),
								modifier = Modifier
									.align(Alignment.TopStart)
									.padding(10.dp),
							) {
								Text(
									text = "Original",
									style = MaterialTheme.typography.labelSmall,
									color = Color.White,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
							Surface(
								color = NemuriTheme.colors.dock.copy(alpha = 0.85f),
								shape = RoundedCornerShape(6.dp),
								modifier = Modifier
									.align(Alignment.TopEnd)
									.padding(10.dp),
							) {
								Text(
									text = "Enhanced",
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.cyan,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
						}
					}
				}

				Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
					Text(
						text = "Drag split line (${(splitFraction * 100f).roundToInt()}%)",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textMuted,
					)
					Slider(
						value = splitFraction,
						onValueChange = { splitFraction = it },
						valueRange = 0.1f..0.9f,
						colors = SliderDefaults.colors(thumbColor = NemuriTheme.colors.cyan, activeTrackColor = NemuriTheme.colors.cyan),
					)
				}
			}
		}
	}
}
