package org.nemuri.app.reader

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
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
import coil3.request.ImageRequest
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.core.image.AvifCapableImageDecoder
import org.koitharu.kotatsu.core.image.AvifCapableRegionDecoder
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.ReaderIntent
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ReaderTapZoneMode
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isPdfUri
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
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
import org.nemuri.app.ui.components.NemuriCapsuleToastHost
import org.nemuri.app.ui.components.NemuriErrorState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriToastManager
import org.nemuri.app.ui.theme.NemuriTheme
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

	@Inject
	lateinit var toastManager: NemuriToastManager

	private val viewModel: ReaderViewModel by viewModels()
	private var volumeKeyCallback: ((Boolean) -> Unit)? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			NemuriTheme {
				val uiState by viewModel.uiState.collectAsStateWithLifecycle()

				LaunchedEffect(uiState.keepScreenOn) {
					if (uiState.keepScreenOn) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					} else {
						window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					}
				}

				ReaderRootScreen(
					uiState = uiState,
					toastManager = toastManager,
					viewModel = viewModel,
					onRegisterVolumeHandler = { volumeKeyCallback = it },
					onBack = { finish() },
				)
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
		): Intent {
			val state = chapterId?.let { ReaderState(chapterId = it, page = page, scroll = scroll) }
			return ReaderIntent.Builder(context)
				.manga(manga)
				.apply {
					if (branch != null) branch(branch)
					if (state != null) state(state)
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
	onRegisterVolumeHandler: (((Boolean) -> Unit)?) -> Unit,
	onBack: () -> Unit,
) {
	val context = LocalContext.current
	var showHud by remember { mutableStateOf(false) }
	var showChaptersSheet by remember { mutableStateOf(false) }
	var showSettingsSheet by remember { mutableStateOf(false) }

	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { solved ->
		if (solved) viewModel.retry()
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { solved ->
		if (solved) viewModel.retry()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black),
	) {
		when {
			uiState.isLoading -> {
				NemuriLoadingState(message = "Opening reader…")
			}

			uiState.pendingCloudFlare != null -> {
				val cf = uiState.pendingCloudFlare
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

			uiState.pendingInteractive != null -> {
				val interactive = uiState.pendingInteractive
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

			uiState.errorMessage != null && !uiState.isChapterLoading &&
				((!uiState.isTextMode && uiState.pages.isEmpty()) || (uiState.isTextMode && uiState.novelBlocks.isEmpty())) -> {
				val challengeUrl = uiState.pendingChallengeUrl
				if (!challengeUrl.isNullOrBlank()) {
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
				} else {
					Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
						IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
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
				)
			}
		}

		// Top HUD
		AnimatedVisibility(
			visible = showHud || uiState.isChapterLoading,
			enter = fadeIn() + slideInVertically { -it },
			exit = fadeOut() + slideOutVertically { -it },
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			Surface(
				color = NemuriTheme.colors.background.copy(alpha = 0.92f),
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
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
					}
					Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
						Text(
							text = uiState.title,
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.textPrimary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = buildString {
								append(uiState.chapterTitle)
								if (uiState.isIncognito) append(" • Incognito")
							},
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
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
					IconButton(onClick = { showSettingsSheet = true }) {
						Icon(
							imageVector = Icons.Default.Tune,
							contentDescription = "Reader settings",
							tint = NemuriTheme.colors.cyan,
						)
					}
				}
			}
		}

		// Bottom HUD
		AnimatedVisibility(
			visible = showHud && !uiState.isLoading,
			enter = fadeIn() + slideInVertically { it },
			exit = fadeOut() + slideOutVertically { it },
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			Surface(
				color = NemuriTheme.colors.background.copy(alpha = 0.94f),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.navigationBarsPadding()
						.padding(horizontal = 12.dp, vertical = 8.dp),
				) {
					val itemCount = if (uiState.isTextMode) uiState.novelBlocks.size else uiState.chapterLogicalCount.coerceAtLeast(uiState.pages.size)
					val currentIdx = if (uiState.isTextMode) {
						uiState.novelCurrentBlockIndex
					} else {
						(uiState.currentPageIndex - uiState.chapterStartGlobalIndex).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
					}
					val pageCounterText = if (uiState.isTextMode) {
						"${(currentIdx + 1).coerceAtMost(itemCount)} / $itemCount"
					} else if (uiState.chapterEndPageNumber > uiState.chapterStartPageNumber) {
						"${uiState.chapterStartPageNumber}–${uiState.chapterEndPageNumber} / ${uiState.chapterTotalPages.coerceAtLeast(1)}"
					} else {
						"${uiState.chapterStartPageNumber} / ${uiState.chapterTotalPages.coerceAtLeast(1)}"
					}
					if (itemCount > 1) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Text(
								text = pageCounterText,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
							Slider(
								value = currentIdx.coerceIn(0, itemCount - 1).toFloat(),
								onValueChange = { value ->
									if (uiState.isTextMode) {
										viewModel.jumpToNovelBlock(value.roundToInt())
									} else {
										val targetGlobal = uiState.chapterStartGlobalIndex + value.roundToInt()
										viewModel.jumpToMangaPage(targetGlobal)
									}
								},
								valueRange = 0f..(itemCount - 1).toFloat(),
								colors = SliderDefaults.colors(
									thumbColor = NemuriTheme.colors.cyan,
									activeTrackColor = NemuriTheme.colors.cyan,
									inactiveTrackColor = NemuriTheme.colors.border,
								),
								modifier = Modifier.weight(1f),
							)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						IconButton(
							onClick = { viewModel.goToAdjacentChapter(isNext = false) },
							enabled = uiState.hasPrevChapter,
						) {
							Icon(
								imageVector = Icons.Default.ChevronLeft,
								contentDescription = "Previous chapter",
								tint = if (uiState.hasPrevChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
							)
						}

						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(999.dp),
							modifier = Modifier
								.clip(RoundedCornerShape(999.dp))
								.clickable { showChaptersSheet = true },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Icon(
									imageVector = Icons.Default.FormatListBulleted,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(16.dp),
								)
								Text(
									text = "Chapters (${uiState.chapters.size})",
									style = MaterialTheme.typography.labelMedium,
									color = NemuriTheme.colors.textPrimary,
								)
							}
						}

						IconButton(
							onClick = { viewModel.goToAdjacentChapter(isNext = true) },
							enabled = uiState.hasNextChapter,
						) {
							Icon(
								imageVector = Icons.Default.ChevronRight,
								contentDescription = "Next chapter",
								tint = if (uiState.hasNextChapter) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
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
			bottomPadding = if (showHud) 128.dp else 24.dp,
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}

	if (showChaptersSheet) {
		NemuriModalSheet(
			title = "Chapters",
			subtitle = uiState.title,
			onDismiss = { showChaptersSheet = false },
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.height(420.dp),
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				items(uiState.chapters, key = { it.chapter.id }) { item ->
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
								showChaptersSheet = false
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
								Icon(Icons.Default.Check, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(16.dp))
							}
						}
					}
				}
			}
		}
	}

	if (showSettingsSheet) {
		ReaderSettingsModalSheet(
			uiState = uiState,
			viewModel = viewModel,
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
) {
	val pages = uiState.pages
	if (pages.isEmpty()) {
		if (uiState.isChapterLoading) {
			NemuriLoadingState(message = "Loading pages…")
		}
		return
	}

	val context = LocalContext.current
	val coroutineScope = rememberCoroutineScope()
	val migrateAction: (() -> Unit)? = uiState.manga?.id?.takeIf { it != 0L }?.let { id ->
		{ openMigrateFromReader(context, id) }
	}
	val bitmapConfig = if (uiState.is32BitColors) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
	val currentTapZoneMode by rememberUpdatedState(uiState.tapZoneMode)
	val currentSmartZoom by rememberUpdatedState(uiState.isDoubleTapSmartZoom)

	if (uiState.readerMode == ReaderMode.WEBTOON) {
		val listState = rememberLazyListState()
		var webtoonZoom by remember { mutableStateOf(1f) }
		var webtoonPanX by remember { mutableStateOf(0f) }

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
			when (resolveTapZoneAction(normX, normY, currentTapZoneMode, isRtl = false, isWebtoon = true)) {
				TapZoneAction.TOGGLE_HUD -> onToggleHud()
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
				.pointerInput(currentTapZoneMode) {
					detectTapGestures(
						onDoubleTap = { offset ->
							if (currentSmartZoom) {
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
						},
						onTap = { offset ->
							val nx = if (size.width > 0) offset.x / size.width.toFloat() else 0.5f
							val ny = if (size.height > 0) offset.y / size.height.toFloat() else 0.5f
							handleWebtoonTap(nx, ny)
						},
					)
				},
		) {
			LazyColumn(
				state = listState,
				verticalArrangement = Arrangement.spacedBy(0.dp),
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
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = ::handleWebtoonTap,
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
		val pagerState = rememberPagerState(
			initialPage = uiState.currentPageIndex.coerceIn(0, pages.lastIndex),
			pageCount = { pages.size },
		)
		val isRtl = uiState.readerMode == ReaderMode.REVERSED

		LaunchedEffect(uiState.pageJumpToken) {
			val target = uiState.currentPageIndex.coerceIn(0, pages.lastIndex)
			if (pagerState.currentPage != target) {
				pagerState.scrollToPage(target)
			}
		}

		LaunchedEffect(pagerState, pages) {
			snapshotFlow { pagerState.currentPage }
				.distinctUntilChanged()
				.collect { pageIdx ->
					if (pageIdx in pages.indices) {
						viewModel.onMangaPageSelected(pageIdx)
					}
				}
		}

		LaunchedEffect(pagerState, pages) {
			onRegisterVolumeHandler { forward ->
				val delta = if (forward) 1 else -1
				val target = (pagerState.currentPage + delta).coerceIn(0, pages.lastIndex)
				coroutineScope.launch { pagerState.animateScrollToPage(target) }
			}
		}

		fun handlePagedTap(normX: Float, normY: Float) {
			when (resolveTapZoneAction(normX, normY, currentTapZoneMode, isRtl = isRtl, isWebtoon = false)) {
				TapZoneAction.TOGGLE_HUD -> onToggleHud()
				TapZoneAction.PREV -> {
					val target = pagerState.currentPage - 1
					if (target >= 0) {
						coroutineScope.launch { pagerState.animateScrollToPage(target) }
					} else if (uiState.hasPrevChapter) {
						viewModel.goToAdjacentChapter(isNext = false)
					} else {
						onToggleHud()
					}
				}
				TapZoneAction.NEXT -> {
					val target = pagerState.currentPage + 1
					if (target <= pages.lastIndex) {
						coroutineScope.launch { pagerState.animateScrollToPage(target) }
					} else if (uiState.hasNextChapter) {
						viewModel.goToAdjacentChapter(isNext = true)
					} else {
						onToggleHud()
					}
				}
			}
		}

		when (uiState.readerMode) {
			ReaderMode.VERTICAL -> {
				VerticalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { pageIdx ->
					val page = pages.getOrNull(pageIdx) ?: return@VerticalPager
					SsivPageView(
						page = page,
						bitmapConfig = bitmapConfig,
						isWebtoon = false,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = ::handlePagedTap,
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			ReaderMode.REVERSED -> {
				HorizontalPager(
					state = pagerState,
					reverseLayout = true,
					modifier = Modifier.fillMaxSize(),
				) { pageIdx ->
					val page = pages.getOrNull(pageIdx) ?: return@HorizontalPager
					SsivPageView(
						page = page,
						bitmapConfig = bitmapConfig,
						isWebtoon = false,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = ::handlePagedTap,
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			else -> {
				HorizontalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { pageIdx ->
					val page = pages.getOrNull(pageIdx) ?: return@HorizontalPager
					SsivPageView(
						page = page,
						bitmapConfig = bitmapConfig,
						isWebtoon = false,
						isDoubleTapSmartZoom = currentSmartZoom,
						shaderConfig = uiState.shaderConfig,
						neuralRevision = uiState.neuralRevision,
						loadPage = viewModel::loadPageImage,
						onTapNormalized = ::handlePagedTap,
						fallbackUrl = uiState.manga?.publicUrl,
						onFindAlternative = migrateAction,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
		}
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
	neuralRevision: Int,
	loadPage: suspend (ReaderPage, Boolean) -> Result<Pair<Uri, Rect?>>,
	onTapNormalized: (Float, Float) -> Unit,
	fallbackUrl: String? = null,
	onFindAlternative: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val latestShaderConfig by rememberUpdatedState(shaderConfig)
	val latestOnTap by rememberUpdatedState(onTapNormalized)
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
			.onSuccess { resolved = it }
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

	Box(
		modifier = boxModifier.background(Color.Black),
		contentAlignment = Alignment.Center,
	) {
		val pair = resolved
		if (pair != null) {
			val (uri, bounds) = pair
			AndroidView(
				factory = { ctx ->
					val isLowRam = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
						?.isLowRamDevice == true
					SubsamplingScaleImageView(ctx).apply {
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
									return false
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
									configureSsivScaleAndZoom(this@apply, isWebtoon, latestSmartZoom)
									applyShaderToSsiv(this@apply, latestShaderConfig)
								}

								override fun onImageLoadError(e: Throwable) {
									errorText = e.localizedMessage ?: "Failed to decode page"
								}
							},
						)
					}
				},
				update = { ssiv ->
					val holder = ssivState(ssiv)
					val tagKey = "${uri}_${bitmapConfig.name}"
					if (holder.imageKey != tagKey) {
						holder.imageKey = tagKey
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
					applyShaderToSsiv(ssiv, shaderConfig)
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
	val listState = rememberLazyListState()
	val coroutineScope = rememberCoroutineScope()

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
				.statusBarsPadding()
				.pointerInput(Unit) {
					detectTapGestures(
						onTap = { offset ->
							val nx = if (size.width > 0) offset.x / size.width.toFloat() else 0.5f
							val ny = if (size.height > 0) offset.y / size.height.toFloat() else 0.5f
							when (resolveTapZoneAction(nx, ny, currentTapZoneMode, isRtl = false, isWebtoon = true)) {
								TapZoneAction.TOGGLE_HUD -> onToggleHud()
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
				},
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

@Composable
private fun ReaderSettingsModalSheet(
	uiState: ReaderUiState,
	viewModel: ReaderViewModel,
	onDismiss: () -> Unit,
) {
	var showShaderPreview by remember { mutableStateOf(false) }

	NemuriModalSheet(
		title = if (uiState.isTextMode) "Typography & Theme" else "Reading Mode & GPU Shaders",
		onDismiss = onDismiss,
	) {
		Column(
			modifier = Modifier
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			if (!uiState.isTextMode) {
				listOf(
					ReaderMode.STANDARD to "Left to Right (Paged)",
					ReaderMode.REVERSED to "Right to Left (Manga)",
					ReaderMode.VERTICAL to "Vertical (Paged)",
					ReaderMode.WEBTOON to "Webtoon (Continuous)",
				).forEach { (mode, label) ->
					val selected = uiState.readerMode == mode
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(12.dp),
							)
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								viewModel.setReaderMode(mode)
								onDismiss()
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(label, style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textPrimary)
							if (selected) {
								Icon(Icons.Default.Check, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(18.dp))
							}
						}
					}
				}

				HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 2.dp))

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

				HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 2.dp))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Seamless Slice Stitching", style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textPrimary)
						Text("Merges uneven sliver slices into single pages with 0px seams", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
					}
					Switch(
						checked = uiState.isSeamlessStitchEnabled,
						onCheckedChange = viewModel::setSeamlessStitchEnabled,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Smart Double-Tap Zoom", style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textPrimary)
						Text("Snaps pillarboxed/stitched pages edge-to-edge or 2x focal zoom", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
					}
					Switch(
						checked = uiState.isDoubleTapSmartZoom,
						onCheckedChange = viewModel::setDoubleTapSmartZoom,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}

				Text("Tap-Zone Layout", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.cyan)
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
									shape = RoundedCornerShape(10.dp),
									modifier = Modifier
										.weight(1f)
										.border(
											1.dp,
											if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											RoundedCornerShape(10.dp),
										)
										.clip(RoundedCornerShape(10.dp))
										.clickable { viewModel.setTapZoneMode(tz) },
								) {
									Text(
										text = tz.label,
										style = MaterialTheme.typography.labelSmall,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										textAlign = TextAlign.Center,
										maxLines = 1,
										modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
									)
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

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Screentone Descreen", style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textPrimary)
						Text("Smooths halftone dot noise while keeping ink sharp", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
					}
					Switch(
						checked = uiState.shaderConfig.descreen,
						onCheckedChange = viewModel::setShaderDescreen,
						enabled = UpscaleEffect.isSupported,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text("Crisp Ink & Paper Levels", style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textPrimary)
						Text("Deepens faded blacks & cleans muddy scan paper", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
					}
					Switch(
						checked = uiState.shaderConfig.inkLevels,
						onCheckedChange = viewModel::setShaderInkLevels,
						enabled = UpscaleEffect.isSupported,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}

				Text(
					text = "Shader Strength (${(uiState.shaderConfig.strength * 100f).roundToInt()}%)",
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
								"${uiState.activeNeuralDelegate} • Adaptive 256/512px tiled NPU/GPU/CPU • Skips >4096px webtoons & Battery Saver"
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
					uiState.neuralCatalog.filter { it.isDownloadable || it.isDownloaded }.forEach { model ->
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

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "Upscale cache: ${String.format(java.util.Locale.US, "%.1f MB", uiState.neuralCacheBytes / (1024f * 1024f))} / 250 MB",
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

				val currentPage = uiState.pages.getOrNull(uiState.currentPageIndex)
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
						Text("Before / After Split Preview")
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
			} else {
				Text("Theme", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
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

				Text("Font Family", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
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

private data class AppliedShaderState(val key: Pair<ReaderShaderConfig, Float>?)

private class SsivHolderState(
	var imageKey: String? = null,
	var shaderState: AppliedShaderState? = null,
)

private fun ssivState(ssiv: SubsamplingScaleImageView): SsivHolderState =
	(ssiv.tag as? SsivHolderState) ?: SsivHolderState().also { ssiv.tag = it }

private fun applyShaderToSsiv(
	ssiv: SubsamplingScaleImageView,
	config: ReaderShaderConfig,
) {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
	val pm = ssiv.context.getSystemService(Context.POWER_SERVICE) as? PowerManager
	val isPowerSave = pm?.isPowerSaveMode == true
	val fitScale = if (ssiv.sWidth > 0 && ssiv.width > 0) {
		(ssiv.width.toFloat() / ssiv.sWidth.toFloat()).coerceAtLeast(UpscaleEffect.MIN_SCALE)
	} else {
		UpscaleEffect.MIN_SCALE
	}
	val desired = AppliedShaderState(
		key = if (!config.isActive || isPowerSave) null else (config to fitScale),
	)
	val holder = ssivState(ssiv)
	if (holder.shaderState == desired) return
	holder.shaderState = desired
	if (desired.key == null) {
		ssiv.setRenderEffect(null)
	} else {
		ssiv.setRenderEffect(UpscaleEffect.create(fitScale, config))
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
										} else {
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
