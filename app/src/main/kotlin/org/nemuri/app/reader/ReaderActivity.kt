package org.nemuri.app.reader

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.core.image.AvifCapableImageDecoder
import org.koitharu.kotatsu.core.image.AvifCapableRegionDecoder
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.ReaderIntent
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isPdfUri
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
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
		viewModel.flushProgress()
		super.onPause()
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
					actionLabel = "Verify in Browser",
					onAction = { cfLauncher.launch(cf) },
					onRetry = viewModel::retry,
					onBack = onBack,
				)
			}

			uiState.pendingInteractive != null -> {
				val interactive = uiState.pendingInteractive
				ReaderChallengeScreen(
					title = "Sign-in required",
					message = uiState.errorMessage ?: "Complete login or action in WebView, then retry.",
					actionLabel = "Open Browser",
					onAction = { browserLauncher.launch(interactive) },
					onRetry = viewModel::retry,
					onBack = onBack,
				)
			}

			uiState.errorMessage != null && !uiState.isChapterLoading &&
				((!uiState.isTextMode && uiState.pages.isEmpty()) || (uiState.isTextMode && uiState.novelBlocks.isEmpty())) -> {
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
					val itemCount = if (uiState.isTextMode) uiState.novelBlocks.size else uiState.pages.size
					val currentIdx = if (uiState.isTextMode) uiState.novelCurrentBlockIndex else uiState.currentPageIndex
					if (itemCount > 1) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Text(
								text = "${(currentIdx + 1).coerceAtMost(itemCount)} / $itemCount",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
							Slider(
								value = currentIdx.coerceIn(0, itemCount - 1).toFloat(),
								onValueChange = { value ->
									if (uiState.isTextMode) {
										viewModel.jumpToNovelBlock(value.roundToInt())
									} else {
										viewModel.jumpToMangaPage(value.roundToInt())
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

	val coroutineScope = rememberCoroutineScope()
	val bitmapConfig = if (uiState.is32BitColors) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

	if (uiState.readerMode == ReaderMode.WEBTOON) {
		val listState = rememberLazyListState()

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

		LazyColumn(
			state = listState,
			modifier = Modifier
				.fillMaxSize()
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = onToggleHud,
				),
		) {
			items(pages, key = { it.id }) { page ->
				SsivPageView(
					page = page,
					bitmapConfig = bitmapConfig,
					isWebtoon = true,
					loadPage = viewModel::loadPageImage,
					onTap = onToggleHud,
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
	} else {
		val pagerState = rememberPagerState(
			initialPage = uiState.currentPageIndex.coerceIn(0, pages.lastIndex),
			pageCount = { pages.size },
		)

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
						loadPage = viewModel::loadPageImage,
						onTap = onToggleHud,
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
						loadPage = viewModel::loadPageImage,
						onTap = onToggleHud,
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
						loadPage = viewModel::loadPageImage,
						onTap = onToggleHud,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
		}
	}
}

@Composable
private fun SsivPageView(
	page: ReaderPage,
	bitmapConfig: Bitmap.Config,
	isWebtoon: Boolean,
	loadPage: suspend (ReaderPage, Boolean) -> Result<Pair<Uri, Rect?>>,
	onTap: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	var retryToken by remember(page.id) { mutableStateOf(0) }
	var resolved by remember(page.id) { mutableStateOf<Pair<Uri, Rect?>?>(null) }
	var errorText by remember(page.id) { mutableStateOf<String?>(null) }
	var imageReady by remember(page.id) { mutableStateOf(false) }

	LaunchedEffect(page.id, retryToken) {
		errorText = null
		imageReady = false
		loadPage(page, retryToken > 0)
			.onSuccess { resolved = it }
			.onFailure { err -> errorText = err.getDisplayMessage(context.resources) }
	}

	val aspectRatio = remember(resolved) {
		val bounds = resolved?.second
		if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
			(bounds.width().toFloat() / bounds.height().toFloat()).coerceIn(0.25f, 3.5f)
		} else {
			0.7f
		}
	}

	val boxModifier = if (isWebtoon) {
		modifier
			.fillMaxWidth()
			.aspectRatio(aspectRatio)
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
						minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
						maxScale = 4f
						isZoomEnabled = !isWebtoon
						isPanEnabled = !isWebtoon
						bitmapDecoderFactory = AvifCapableImageDecoder.Factory(bitmapConfig)
						regionDecoderFactory = AvifCapableRegionDecoder.Factory(bitmapConfig, isLowRam)
						setOnClickListener { onTap() }
						addOnImageEventListener(
							object : DefaultOnImageEventListener {
								override fun onReady() {
									imageReady = true
								}

								override fun onImageLoadError(e: Throwable) {
									errorText = e.localizedMessage ?: "Failed to decode page"
								}
							},
						)
					}
				},
				update = { ssiv ->
					val tagKey = "${uri}_${bitmapConfig.name}"
					if (ssiv.tag != tagKey) {
						ssiv.tag = tagKey
						val isLowRam = (ssiv.context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
							?.isLowRamDevice == true
						ssiv.bitmapDecoderFactory = AvifCapableImageDecoder.Factory(bitmapConfig)
						ssiv.regionDecoderFactory = AvifCapableRegionDecoder.Factory(bitmapConfig, isLowRam)
						val source = ImageSource.uri(uri).let { src ->
							if (uri.isPdfUri() && bounds != null) src.region(bounds) else src
						}
						ssiv.setImage(source)
					}
				},
				onRelease = { it.recycle() },
				modifier = Modifier.fillMaxSize(),
			)
		}

		if (errorText != null) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.padding(24.dp),
			) {
				Text(
					text = errorText!!,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textSecondary,
					textAlign = TextAlign.Center,
				)
				Button(
					onClick = { retryToken++ },
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) {
					Text("Retry")
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
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = onToggleHud,
				),
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
	NemuriModalSheet(
		title = if (uiState.isTextMode) "Typography & Theme" else "Reading Mode",
		onDismiss = onDismiss,
	) {
		Column(
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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

@Composable
private fun ReaderChallengeScreen(
	title: String,
	message: String,
	actionLabel: String,
	onAction: () -> Unit,
	onRetry: () -> Unit,
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
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Button(
				onClick = onAction,
				colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.cyan, contentColor = NemuriTheme.colors.onCyan),
			) {
				Text(actionLabel)
			}
			TextButton(onClick = onRetry) { Text("Retry", color = NemuriTheme.colors.cyan) }
			TextButton(onClick = onBack) { Text("Close", color = NemuriTheme.colors.textSecondary) }
		}
	}
}
