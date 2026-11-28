package org.nemuri.app.ui.details

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.data.MangaAiRecapEntity
import org.koitharu.kotatsu.ai.domain.RecapDepth
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.favicon.faviconUri
import org.koitharu.kotatsu.core.prefs.ChaptersSortOrder
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.prefs.DetailsUiMode
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.favourites.domain.TitleSignatureParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.browser.isLikelyChallengeErrorMessage
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.reader.ReaderActivity
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriErrorState
import org.nemuri.app.ui.components.NemuriListNavigationOverlay
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.SnappingSwipeRow
import org.nemuri.app.ui.components.listDragRangeSelect
import org.nemuri.app.ui.components.rememberNemuriScrollAdapter
import org.nemuri.app.ui.components.rememberPushScreenBottomPadding
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriFastSpatialSpring
import org.nemuri.app.ui.theme.nemuriSharedCover
import org.nemuri.app.ui.theme.nemuriSpatialSpring

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MangaDetailsScreen(
	mangaId: Long,
	sourceName: String?,
	onBack: () -> Unit,
	onStartReader: (Manga, Long?, String?) -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onOpenSourceBrowse: (String, String?) -> Unit,
	onOpenMigrate: (Long) -> Unit = {},
	onOpenAiSettings: () -> Unit = {},
	sharedCoverKey: String? = null,
	viewModel: MangaDetailsViewModel = viewModel(),
) {
	val context = LocalContext.current
	val coroutineScope = rememberCoroutineScope()
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(mangaId, sourceName) {
		viewModel.bind(mangaId, sourceName)
	}

	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { solved ->
		if (solved) viewModel.retry(force = true)
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { completed ->
		if (completed) viewModel.retry(force = true)
	}

	var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
	var showCategorySheet by remember { mutableStateOf(false) }
	var showDownloadSheet by remember { mutableStateOf(false) }
	var showChaptersFilterSheet by remember { mutableStateOf(false) }
	var showDisplayOptionsSheet by remember { mutableStateOf(false) }
	var showStatsSheet by remember { mutableStateOf(false) }
	var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
	var newCategoryName by rememberSaveable { mutableStateOf("") }
	var chapterMenuTarget by remember { mutableStateOf<ChapterRowItem?>(null) }
	var pendingNsfwLaunch by remember { mutableStateOf<PendingNsfwLaunch?>(null) }

	fun launchReaderSession(targetManga: Manga, chapterId: Long?, incognito: Boolean) {
		runCatching {
			MangaTransferCache.put(targetManga)
			if (incognito) {
				context.startActivity(
					ReaderActivity.newIntent(
						context = context,
						manga = targetManga,
						chapterId = chapterId,
						branch = uiState.selectedBranch,
						incognito = true,
					),
				)
			} else {
				onStartReader(targetManga, chapterId, uiState.selectedBranch)
			}
		}
	}

	fun requestOpenReader(targetManga: Manga, chapterId: Long?) {
		when {
			uiState.isIncognitoActive -> launchReaderSession(targetManga, chapterId, incognito = true)
			uiState.isAdultTitle -> when (uiState.incognitoForNsfw) {
				TriStateOption.ENABLED -> launchReaderSession(targetManga, chapterId, incognito = true)
				TriStateOption.DISABLED -> launchReaderSession(targetManga, chapterId, incognito = false)
				TriStateOption.ASK -> pendingNsfwLaunch = PendingNsfwLaunch(chapterId)
			}
			else -> launchReaderSession(targetManga, chapterId, incognito = false)
		}
	}

	val isSelectionMode = uiState.selectedChapterIds.isNotEmpty()
	val chapterListState = rememberLazyListState()
	var pendingModeSwitchAnchor by remember { mutableStateOf<Pair<Any?, Int>?>(null) }
	val handleDetailsUiModeChange: (DetailsUiMode) -> Unit = { targetMode ->
		if (targetMode != uiState.detailsUiMode) {
			val firstVisible = chapterListState.layoutInfo.visibleItemsInfo.firstOrNull()
			pendingModeSwitchAnchor = (firstVisible?.key to chapterListState.firstVisibleItemScrollOffset)
			viewModel.setDetailsUiMode(targetMode)
		}
	}
	BackHandler(enabled = isSelectionMode) {
		viewModel.clearChapterSelection()
	}

	Box(modifier = Modifier.fillMaxSize()) {
		when {
			uiState.isLoading -> {
				Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
					Row(modifier = Modifier.padding(6.dp)) {
						IconButton(onClick = onBack) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
						}
					}
					NemuriLoadingState(message = "Loading details…")
				}
			}

			uiState.manga == null && uiState.pendingCloudFlare != null -> {
				val cf = uiState.pendingCloudFlare!!
				Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
					Row(modifier = Modifier.padding(6.dp)) {
						IconButton(onClick = onBack) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
						}
					}
					DetailsChallengeView(
						title = "Cloudflare verification required",
						message = uiState.errorMessage ?: "Verify in WebView to load this title.",
						actionLabel = "Solve in WebView",
						onAction = { cfLauncher.launch(cf) },
						onRetry = { viewModel.retry(force = true) },
						onMigrate = { onOpenMigrate(mangaId) },
					)
				}
			}

			uiState.manga == null && uiState.pendingInteractive != null -> {
				val interactive = uiState.pendingInteractive!!
				Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
					Row(modifier = Modifier.padding(6.dp)) {
						IconButton(onClick = onBack) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
						}
					}
					DetailsChallengeView(
						title = "Source action required",
						message = uiState.errorMessage ?: "Complete login or prompt in WebView, then retry.",
						actionLabel = "Solve in WebView",
						onAction = { browserLauncher.launch(interactive) },
						onRetry = { viewModel.retry(force = true) },
						onMigrate = { onOpenMigrate(mangaId) },
					)
				}
			}

			uiState.manga == null -> {
				val challengeUrl = uiState.publicUrl.takeIf {
					it.isNotBlank() && uiState.errorMessage.isLikelyChallengeErrorMessage()
				}
				Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
					Row(modifier = Modifier.padding(6.dp)) {
						IconButton(onClick = onBack) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
						}
					}
					if (challengeUrl != null) {
						DetailsChallengeView(
							title = "Access blocked or verification required",
							message = uiState.errorMessage ?: "Solve verification in WebView, retry, or migrate.",
							actionLabel = "Solve in WebView",
							onAction = {
								context.startActivity(AppRouter.browserIntent(context, challengeUrl, null, uiState.title))
							},
							onRetry = { viewModel.retry(force = true) },
							onMigrate = { onOpenMigrate(mangaId) },
						)
					} else {
						NemuriErrorState(
							message = uiState.errorMessage ?: "Title unavailable.",
							onRetry = { viewModel.retry(force = true) },
						)
					}
				}
			}

			else -> {
				val manga = uiState.manga!!
				val layoutPrefs = LocalLayoutStudioPrefs.current
				val chapterScrollAdapter = rememberNemuriScrollAdapter(chapterListState)
				val chapterIds = remember(uiState.chapters) { uiState.chapters.map { it.chapter.id } }
				val detailsBottomPadding = rememberPushScreenBottomPadding(if (isSelectionMode) 108.dp else 88.dp)

				val hasInlineBanner = uiState.pendingCloudFlare != null ||
					uiState.pendingInteractive != null ||
					uiState.errorMessage != null
				val hasSynopsisOrTags = uiState.descriptionText.isNotBlank() || manga.tags.isNotEmpty()
				val hasAiRecap = uiState.isRecapCardEnabled && uiState.history != null
				val hasBranches = uiState.branches.size > 1
				val chaptersHeaderIndex = 1 +
					(if (hasInlineBanner) 1 else 0) +
					(if (hasSynopsisOrTags) 1 else 0) +
					(if (hasAiRecap) 1 else 0) +
					(if (hasBranches) 1 else 0)

				var lastRenderedDetailsUiMode by remember(manga.id) { mutableStateOf(uiState.detailsUiMode) }
				LaunchedEffect(uiState.detailsUiMode, manga.id) {
					if (uiState.detailsUiMode != lastRenderedDetailsUiMode) {
						val (anchorKey, anchorOffset) = pendingModeSwitchAnchor ?: run {
							val firstVisible = chapterListState.layoutInfo.visibleItemsInfo.firstOrNull()
							firstVisible?.key to chapterListState.firstVisibleItemScrollOffset
						}
						pendingModeSwitchAnchor = null
						val target = resolveDetailsModeSwitchScrollTarget(
							anchorKey = anchorKey,
							anchorOffset = anchorOffset,
							targetMode = uiState.detailsUiMode,
							hasInlineBanner = hasInlineBanner,
							hasSynopsisOrTags = hasSynopsisOrTags,
							hasAiRecap = hasAiRecap,
							hasBranches = hasBranches,
							chapterIds = chapterIds,
							hasRelatedManga = uiState.relatedManga.isNotEmpty(),
						)
						chapterListState.scrollToItem(target.index, target.scrollOffset)
						lastRenderedDetailsUiMode = uiState.detailsUiMode
					} else {
						pendingModeSwitchAnchor = null
					}
				}

				val isContinueFabExpanded = chapterListState.isScrollingUp()

				Box(modifier = Modifier.fillMaxSize()) {
					LazyColumn(
						state = chapterListState,
						modifier = Modifier
							.fillMaxSize()
							.listDragRangeSelect(
								listState = chapterListState,
								orderedIds = chapterIds,
								selectedIds = uiState.selectedChapterIds,
								enabled = true,
								keyMapper = { it as? Long },
								onSelectionChange = viewModel::selectChapterRange,
							),
						contentPadding = PaddingValues(bottom = detailsBottomPadding),
					) {
					// Top Hero Header (adapts across Centered Large / Compact / Ultra Compact)
					item(key = "details_hero") {
						val coverKey = sharedCoverKey ?: "details-${manga.id}"
						val toolbar: @Composable () -> Unit = {
							DetailsTopToolbar(
								uiState = uiState,
								onBack = onBack,
								onToggleIncognito = viewModel::toggleHeaderIncognito,
								onRefresh = { viewModel.retry(force = true) },
								onOpenBrowser = {
									runCatching {
										context.startActivity(
											AppRouter.browserIntent(
												context = context,
												url = uiState.publicUrl,
												source = manga.source,
												title = manga.title,
											),
										)
									}
								},
								onOpenDownloads = { showDownloadSheet = true },
								onOpenStats = {
									viewModel.loadMangaStats()
									showStatsSheet = true
								},
								onOpenMigrate = { onOpenMigrate(uiState.mangaId) },
								onOpenDisplayOptions = { showDisplayOptionsSheet = true },
							)
						}

						when (uiState.detailsUiMode) {
							DetailsUiMode.EXPRESSIVE -> DetailsExpressiveHeader(
								manga = manga,
								uiState = uiState,
								sharedCoverKey = coverKey,
								toolbar = toolbar,
								onToggleLibrary = viewModel::quickToggleLibrary,
								onOpenCategories = { showCategorySheet = true },
								onOpenMigrate = { onOpenMigrate(uiState.mangaId) },
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)

							DetailsUiMode.COMPACT -> DetailsCompactHeader(
								manga = manga,
								uiState = uiState,
								sharedCoverKey = coverKey,
								toolbar = toolbar,
								onToggleLibrary = viewModel::quickToggleLibrary,
								onOpenCategories = { showCategorySheet = true },
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)

							DetailsUiMode.ULTRA_COMPACT -> DetailsUltraCompactHeader(
								manga = manga,
								uiState = uiState,
								sharedCoverKey = coverKey,
								toolbar = toolbar,
								onToggleLibrary = viewModel::quickToggleLibrary,
								onOpenCategories = { showCategorySheet = true },
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)
						}
					}

					// Challenge / Error inline banner if details loaded partially from cache
					if (uiState.pendingCloudFlare != null) {
						item(key = "details_cf_banner") {
							val cf = uiState.pendingCloudFlare!!
							DetailsInlineChallengeBanner(
								title = "Cloudflare protection active",
								message = uiState.errorMessage ?: "Solve verification in WebView to refresh chapters.",
								actionLabel = "Solve in WebView",
								onAction = { cfLauncher.launch(cf) },
								onRetry = { viewModel.retry(force = true) },
								onMigrate = { onOpenMigrate(manga.id) },
							)
						}
					} else if (uiState.pendingInteractive != null) {
						val interactive = uiState.pendingInteractive!!
						item(key = "details_interactive_banner") {
							DetailsInlineChallengeBanner(
								title = "Sign-in required",
								message = uiState.errorMessage ?: "Complete verification in WebView and refresh.",
								actionLabel = "Solve in WebView",
								onAction = { browserLauncher.launch(interactive) },
								onRetry = { viewModel.retry(force = true) },
								onMigrate = { onOpenMigrate(manga.id) },
							)
						}
					} else if (uiState.errorMessage != null) {
						val challengeUrl = uiState.publicUrl.takeIf {
							it.isNotBlank() && uiState.errorMessage.isLikelyChallengeErrorMessage()
						}
						item(key = "details_err_banner") {
							DetailsInlineChallengeBanner(
								title = if (challengeUrl != null) "HTTP 403 / Verification required" else "Refresh failed",
								message = uiState.errorMessage!!,
								actionLabel = if (challengeUrl != null) "Solve in WebView" else "Retry",
								onAction = {
									if (challengeUrl != null) {
										context.startActivity(AppRouter.browserIntent(context, challengeUrl, manga.source, manga.title))
									} else {
										viewModel.retry(force = true)
									}
								},
								onRetry = if (challengeUrl != null) ({ viewModel.retry(force = true) }) else null,
								onMigrate = { onOpenMigrate(manga.id) },
							)
						}
					}

					// Description & Tags
					if (uiState.descriptionText.isNotBlank() || manga.tags.isNotEmpty()) {
						item(key = "details_synopsis") {
							DetailsSynopsisSection(
								manga = manga,
								uiState = uiState,
								descriptionExpanded = descriptionExpanded,
								onToggleExpanded = { descriptionExpanded = !descriptionExpanded },
								onToggleTranslation = viewModel::toggleSynopsisTranslation,
								onOpenSourceBrowse = onOpenSourceBrowse,
							)
						}
					}

					// "Where Was I?" Spoiler-Quarantined Recap Card
					if (uiState.isRecapCardEnabled && uiState.history != null) {
						item(key = "details_ai_recap") {
							WhereWasIRecapCard(
								recap = uiState.aiRecap,
								isGenerating = uiState.isGeneratingRecap,
								engineBadgeLabel = uiState.recapEngineBadgeLabel,
								recapDepth = uiState.recapDepth,
								onSelectDepth = viewModel::setRecapDepth,
								onGenerate = { viewModel.generateRecap(forceRefresh = uiState.aiRecap != null, depthOverride = uiState.recapDepth) },
								onOpenSettings = onOpenAiSettings,
							)
						}
					}

					// Branches (Scanlators / Languages)
					if (uiState.branches.size > 1) {
						item(key = "details_branches") {
							val pillShape = NemuriTheme.pillShape()
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.horizontalScroll(rememberScrollState())
									.padding(horizontal = 16.dp, vertical = 6.dp),
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								uiState.branches.forEach { option ->
									val selected = option.branch == uiState.selectedBranch
									Surface(
										color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
										shape = pillShape,
										border = BorderStroke(
											1.dp,
											if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										),
										modifier = Modifier
											.height(DETAILS_COMPACT_CONTROL_HEIGHT)
											.clip(pillShape)
											.clickable { viewModel.selectBranch(option.branch) },
									) {
										Box(
											contentAlignment = Alignment.Center,
											modifier = Modifier
												.fillMaxHeight()
												.padding(horizontal = 12.dp),
										) {
											Text(
												text = "${option.label} (${option.chaptersCount})",
												style = MaterialTheme.typography.labelMedium,
												color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
												maxLines = 1,
											)
										}
									}
								}
							}
						}
					}

					// Chapters Header + Search
					item(key = "details_chapters_header") {
						val pillShape = NemuriTheme.pillShape()
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							HorizontalDivider(color = NemuriTheme.colors.border)
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = "${uiState.chapters.size} Chapter(s)",
										style = MaterialTheme.typography.titleMedium,
										color = NemuriTheme.colors.textPrimary,
									)
									val metaLine = buildList {
										if (uiState.downloadedCount > 0) add("${uiState.downloadedCount} downloaded")
										if (uiState.newChaptersCount > 0) add("${uiState.newChaptersCount} new")
									}.joinToString(" • ")
									if (metaLine.isNotEmpty()) {
										Text(
											text = metaLine,
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.cyan,
										)
									}
								}

								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									if (uiState.newChaptersCount > 0) {
										Surface(
											color = NemuriTheme.colors.tealContainer,
											shape = pillShape,
											border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
											modifier = Modifier
												.height(DETAILS_COMPACT_CONTROL_HEIGHT)
												.clip(pillShape)
												.clickable(onClick = viewModel::clearNewChapterBadges),
										) {
											Box(
												contentAlignment = Alignment.Center,
												modifier = Modifier
													.fillMaxHeight()
													.padding(horizontal = 10.dp),
											) {
												Text(
													text = "Clear New",
													style = MaterialTheme.typography.labelMedium,
													fontWeight = FontWeight.SemiBold,
													color = NemuriTheme.colors.cyan,
													maxLines = 1,
												)
											}
										}
									}
									IconButton(
										onClick = viewModel::toggleChaptersReversed,
										modifier = Modifier.size(DETAILS_COMPACT_CONTROL_HEIGHT),
									) {
										Icon(
											imageVector = Icons.Default.SwapVert,
											contentDescription = "Reverse order",
											tint = if (uiState.isReversed) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										)
									}
									IconButton(
										onClick = { showChaptersFilterSheet = true },
										modifier = Modifier.size(DETAILS_COMPACT_CONTROL_HEIGHT),
									) {
										Icon(
											imageVector = Icons.Default.FilterList,
											contentDescription = "Sort and filter chapters",
											tint = if (uiState.downloadedOnly || uiState.chaptersSortOrder != ChaptersSortOrder.SOURCE) {
												NemuriTheme.colors.cyan
											} else {
												NemuriTheme.colors.textSecondary
											},
										)
									}
								}
							}

							if (uiState.totalBranchChapters > 12 || uiState.chapterQuery.isNotEmpty()) {
								val chapterQueryField = rememberSyncedTextField(uiState.chapterQuery, viewModel::setChapterQuery)
								OutlinedTextField(
									value = chapterQueryField.fieldValue,
									onValueChange = chapterQueryField::onValueChange,
									placeholder = { Text("Filter chapters…") },
									leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
									trailingIcon = {
										if (chapterQueryField.fieldValue.text.isNotEmpty()) {
											IconButton(onClick = { chapterQueryField.clear() }) {
												Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
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
							}
						}
					}

					if (uiState.chapters.isEmpty()) {
						item(key = "details_chapters_empty") {
							Text(
								text = if (uiState.chapterQuery.isNotBlank() || uiState.downloadedOnly) {
									"No chapters match the current filter."
								} else {
									"No chapters found."
								},
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textMuted,
								modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
							)
						}
					} else {
						items(uiState.chapters, key = { it.chapter.id }) { row ->
							val isSelected = row.chapter.id in uiState.selectedChapterIds
							SnappingSwipeRow(
								modifier = Modifier.animateItem(),
								enabled = !isSelectionMode,
								rightLabel = if (row.isRead || row.isCurrent) "Unread" else "Read",
								rightIcon = if (row.isRead || row.isCurrent) Icons.Default.Undo else Icons.Default.DoneAll,
								onSwipeRight = { viewModel.toggleChapterRead(row) },
								leftLabel = if (row.isDownloaded) "Delete" else "Download",
								leftIcon = if (row.isDownloaded) Icons.Default.DeleteOutline else Icons.Default.Download,
								leftTint = if (row.isDownloaded) NemuriTheme.colors.danger else NemuriTheme.colors.healthy,
								onSwipeLeft = if (uiState.isLocalOnly && !row.isDownloaded) {
									null
								} else {
									{
										if (row.isDownloaded) {
											viewModel.deleteDownloadedChapters(setOf(row.chapter.id))
										} else {
											viewModel.downloadChapters(listOf(row.chapter.id))
										}
									}
								},
							) {
								ChapterRowView(
									row = row,
									isSelectionMode = isSelectionMode,
									isSelected = isSelected,
									showDownloadButton = !uiState.isLocalOnly,
									onClick = {
										if (isSelectionMode) {
											viewModel.toggleChapterSelection(row.chapter.id)
										} else {
											requestOpenReader(manga, row.chapter.id)
										}
									},
									onLongClick = {
										if (isSelectionMode) {
											viewModel.toggleChapterSelection(row.chapter.id)
										} else {
											chapterMenuTarget = row
										}
									},
									onDownloadClick = { viewModel.downloadChapters(listOf(row.chapter.id)) },
									onDeleteLocalClick = { viewModel.deleteDownloadedChapters(setOf(row.chapter.id)) },
								)
							}
						}
					}

					// Related Manga
					if (uiState.relatedManga.isNotEmpty()) {
						item(key = "details_related") {
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(top = 18.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								Text(
									text = "Related Titles",
									style = MaterialTheme.typography.titleMedium,
									color = NemuriTheme.colors.textPrimary,
									modifier = Modifier.padding(horizontal = 16.dp),
								)
								LazyRow(
									contentPadding = PaddingValues(horizontal = 16.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									items(uiState.relatedManga, key = { it.id }) { related ->
										MangaCompactGridCard(
											manga = related,
											onClick = {
												MangaTransferCache.put(related)
												onOpenDetails(related)
											},
											modifier = Modifier.width(114.dp),
										)
									}
								}
							}
						}
					}
					}

					val chapterCalloutForIndex: (Int) -> String? = remember(uiState.chapters, chaptersHeaderIndex) {
						{ idx ->
							val firstChapterIdx = chaptersHeaderIndex + 1
							when {
								uiState.chapters.isEmpty() -> null
								idx < chaptersHeaderIndex -> "Top"
								idx == chaptersHeaderIndex -> "Chapters"
								idx in firstChapterIdx until (firstChapterIdx + uiState.chapters.size) -> {
									val row = uiState.chapters[idx - firstChapterIdx]
									val num = row.chapter.number
									when {
										num > 0f && num % 1f == 0f -> "Ch. ${num.toInt()}"
										num > 0f -> "Ch. ${num.toBigDecimal().stripTrailingZeros().toPlainString()}"
										else -> row.title.take(14)
									}
								}
								else -> "Related"
							}
						}
					}

					val targetContinueChapterId = uiState.primaryChapterId
						?.takeIf { uiState.chapters.isNotEmpty() && uiState.totalBranchChapters > 0 }
					val showContinueFab = !isSelectionMode && targetContinueChapterId != null

					NemuriListNavigationOverlay(
						adapter = chapterScrollAdapter,
						hasBottomDock = false,
						visibleOverride = !isSelectionMode,
						extraQuickButtonsBottomOffset = if (!layoutPrefs.isFastScrollLeftHanded && showContinueFab) 72.dp else 0.dp,
						topTrackPadding = 64.dp,
						labelForIndex = chapterCalloutForIndex,
					)

					val firstChapterId = remember(uiState.details, uiState.selectedBranch, uiState.chapters, uiState.isReversed) {
						val branchChapters = uiState.details?.chapters?.get(uiState.selectedBranch)
							?: uiState.details?.allChapters
						branchChapters?.firstOrNull()?.id
							?: (if (uiState.isReversed) uiState.chapters.lastOrNull() else uiState.chapters.firstOrNull())?.chapter?.id
					}
					val chapterShortLabel = uiState.primaryChapterShortLabel?.takeIf { it.isNotBlank() } ?: "Ch. 1"
					val continueFabLabel = when {
						uiState.isIncognitoActive -> "Incognito $chapterShortLabel"
						uiState.history == null -> {
							val firstLabel = uiState.primaryChapterShortLabel
								?.takeIf { it.isNotBlank() && it.startsWith("Ch.", ignoreCase = true) }
								?: "Ch. 1"
							"Start $firstLabel"
						}
						uiState.unreadChaptersCount == 0 && uiState.progressPercent >= 0.99f -> "Re-read $chapterShortLabel"
						else -> {
							val targetLabel = uiState.primaryChapterShortLabel?.takeIf { it.isNotBlank() } ?: "Reading"
							"Continue $targetLabel"
						}
					}
					AnimatedVisibility(
						visible = showContinueFab,
						enter = fadeIn() + slideInVertically { it / 2 },
						exit = fadeOut() + slideOutVertically { it / 2 },
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.navigationBarsPadding()
							.padding(end = 16.dp, bottom = 16.dp),
					) {
						DetailsContinueSplitButton(
							label = continueFabLabel,
							targetChapterShortLabel = chapterShortLabel,
							isIncognitoActive = uiState.isIncognitoActive,
							expanded = isContinueFabExpanded,
							canStartFromChapterOne = firstChapterId != null,
							onPrimaryClick = {
								val chapterId = targetContinueChapterId ?: return@DetailsContinueSplitButton
								requestOpenReader(manga, chapterId)
							},
							onReadInIncognito = {
								val chapterId = targetContinueChapterId ?: return@DetailsContinueSplitButton
								launchReaderSession(manga, chapterId, incognito = true)
							},
							onToggleIncognitoMode = viewModel::toggleHeaderIncognito,
							onStartFromChapterOne = {
								val chapterId = firstChapterId ?: return@DetailsContinueSplitButton
								requestOpenReader(manga, chapterId)
							},
						)
					}
				}
			}
		}

		// Multi-select Chapter Bottom Bar
		if (isSelectionMode) {
			Surface(
				color = NemuriTheme.colors.sheet,
				shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
					.navigationBarsPadding(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						IconButton(onClick = viewModel::clearChapterSelection) {
							Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = NemuriTheme.colors.textPrimary)
						}
						Text(
							text = "${uiState.selectedChapterIds.size} selected",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
					}
					Row(
						modifier = Modifier.horizontalScroll(rememberScrollState()),
						verticalAlignment = Alignment.CenterVertically,
					) {
						IconButton(onClick = viewModel::selectAllChapters) {
							Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = NemuriTheme.colors.textSecondary)
						}
						TextButton(onClick = viewModel::invertChapterSelection) {
							Text("Invert", color = NemuriTheme.colors.textSecondary)
						}
						IconButton(onClick = viewModel::selectChaptersBelow) {
							Icon(Icons.Default.VerticalAlignBottom, contentDescription = "Select below", tint = NemuriTheme.colors.textSecondary)
						}
						TextButton(onClick = viewModel::selectUnreadChapters) {
							Text("Unread", color = NemuriTheme.colors.cyan)
						}
						if (!uiState.isLocalOnly) {
							IconButton(onClick = { viewModel.downloadChapters(uiState.selectedChapterIds) }) {
								Icon(Icons.Default.Download, contentDescription = "Download selected", tint = NemuriTheme.colors.cyan)
							}
						}
						val selectedDownloaded = uiState.chapters
							.filter { it.chapter.id in uiState.selectedChapterIds && it.isDownloaded }
							.mapTo(HashSet()) { it.chapter.id }
						if (selectedDownloaded.isNotEmpty()) {
							IconButton(onClick = { viewModel.deleteDownloadedChapters(selectedDownloaded) }) {
								Icon(Icons.Default.DeleteOutline, contentDescription = "Delete selected", tint = NemuriTheme.colors.danger)
							}
						}
					}
				}
			}
		}
	}

	// Chapter Context Menu Dialog
	val menuChapter = chapterMenuTarget
	if (menuChapter != null) {
		NemuriModalSheet(
			title = menuChapter.title,
			subtitle = menuChapter.subtitle.ifBlank { null },
			onDismiss = { chapterMenuTarget = null },
		) {
			Column(
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				SheetActionRow(
					icon = Icons.Default.CheckBox,
					label = "Mark read up to here",
					onClick = {
						viewModel.markChapterAsCurrent(menuChapter.chapter)
						chapterMenuTarget = null
					},
				)
				if (!uiState.isLocalOnly && !menuChapter.isDownloaded) {
					SheetActionRow(
						icon = Icons.Default.Download,
						label = "Download chapter",
						onClick = {
							viewModel.downloadChapters(listOf(menuChapter.chapter.id))
							chapterMenuTarget = null
						},
					)
				}
				if (menuChapter.isDownloaded) {
					SheetActionRow(
						icon = Icons.Default.DeleteOutline,
						label = "Delete downloaded chapter",
						tint = NemuriTheme.colors.danger,
						onClick = {
							viewModel.deleteDownloadedChapters(setOf(menuChapter.chapter.id))
							chapterMenuTarget = null
						},
					)
				}
				SheetActionRow(
					icon = Icons.Default.SelectAll,
					label = "Select multiple chapters",
					onClick = {
						viewModel.toggleChapterSelection(menuChapter.chapter.id)
						chapterMenuTarget = null
					},
				)
				if (!uiState.isLocalOnly && uiState.manga != null) {
					SheetActionRow(
						icon = Icons.Default.OpenInBrowser,
						label = "Open in WebView",
						onClick = {
							val target = menuChapter.chapter
							val m = uiState.manga!!
							chapterMenuTarget = null
							coroutineScope.launch {
								val url = viewModel.resolveChapterBrowserUrl(target)
								if (!url.isNullOrBlank()) {
									runCatching {
										context.startActivity(
											AppRouter.browserIntent(context, url, m.source, menuChapter.title),
										)
									}
								}
							}
						},
					)
				}
			}
		}
	}

	// Category Picker Sheet
	if (showCategorySheet) {
		NemuriModalSheet(
			title = "Library Categories",
			subtitle = uiState.title,
			onDismiss = { showCategorySheet = false },
		) {
			Column(
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				uiState.allCategories.forEach { category ->
					val checked = category.id in uiState.memberCategoryIds
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { viewModel.toggleCategoryMembership(category) }
							.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Checkbox(
							checked = checked,
							onCheckedChange = { viewModel.toggleCategoryMembership(category) },
							colors = CheckboxDefaults.colors(
								checkedColor = NemuriTheme.colors.cyan,
								checkmarkColor = NemuriTheme.colors.onCyan,
							),
						)
						Text(
							text = category.title,
							style = MaterialTheme.typography.bodyLarge,
							color = NemuriTheme.colors.textPrimary,
							modifier = Modifier.padding(start = 6.dp),
						)
					}
				}
				HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 4.dp))
				SheetActionRow(
					icon = Icons.Default.Add,
					label = "New Category",
					tint = NemuriTheme.colors.cyan,
					onClick = {
						newCategoryName = ""
						showCreateCategoryDialog = true
					},
				)
				if (uiState.isInLibrary) {
					SheetActionRow(
						icon = Icons.Default.DeleteOutline,
						label = "Remove from Library",
						tint = NemuriTheme.colors.danger,
						onClick = {
							viewModel.quickToggleLibrary()
							showCategorySheet = false
						},
					)
				}
			}
		}
	}

	// Download Sheet
	if (showDownloadSheet) {
		NemuriModalSheet(
			title = "Download Chapters",
			subtitle = "${uiState.totalBranchChapters} in current list",
			onDismiss = { showDownloadSheet = false },
		) {
			Column(
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				SheetActionRow(
					icon = Icons.Default.Download,
					label = "Download next unread chapter",
					onClick = {
						viewModel.downloadNextChapters(1)
						showDownloadSheet = false
					},
				)
				SheetActionRow(
					icon = Icons.Default.Download,
					label = "Download next 5 chapters",
					onClick = {
						viewModel.downloadNextChapters(5)
						showDownloadSheet = false
					},
				)
				SheetActionRow(
					icon = Icons.Default.DoneAll,
					label = "Download all unread chapters",
					onClick = {
						viewModel.downloadUnreadChapters()
						showDownloadSheet = false
					},
				)
				SheetActionRow(
					icon = Icons.Default.Download,
					label = "Download all chapters",
					onClick = {
						viewModel.downloadChapters(null)
						showDownloadSheet = false
					},
				)
				if (uiState.downloadedCount > 0) {
					HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(vertical = 4.dp))
					SheetActionRow(
						icon = Icons.Default.DeleteOutline,
						label = "Delete all downloaded chapters (${uiState.downloadedCount})",
						tint = NemuriTheme.colors.danger,
						onClick = {
							val allDlIds = uiState.chapters
								.filter { it.isDownloaded }
								.mapTo(HashSet()) { it.chapter.id }
							viewModel.deleteDownloadedChapters(allDlIds)
							showDownloadSheet = false
						},
					)
				}
			}
		}
	}

	// Sort & Filter Chapters Sheet
	if (showChaptersFilterSheet) {
		NemuriModalSheet(
			title = "Chapters Display",
			onDismiss = { showChaptersFilterSheet = false },
		) {
			Column(
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Text("Reverse chapter order", style = MaterialTheme.typography.bodyLarge, color = NemuriTheme.colors.textPrimary)
					Switch(
						checked = uiState.isReversed,
						onCheckedChange = { viewModel.toggleChaptersReversed() },
						colors = SwitchDefaults.colors(checkedThumbColor = NemuriTheme.colors.onCyan, checkedTrackColor = NemuriTheme.colors.cyan),
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Text("Downloaded chapters only", style = MaterialTheme.typography.bodyLarge, color = NemuriTheme.colors.textPrimary)
					Switch(
						checked = uiState.downloadedOnly,
						onCheckedChange = { viewModel.toggleDownloadedOnly() },
						colors = SwitchDefaults.colors(checkedThumbColor = NemuriTheme.colors.onCyan, checkedTrackColor = NemuriTheme.colors.cyan),
					)
				}
				HorizontalDivider(color = NemuriTheme.colors.border)
				Text("Sort By", style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textMuted)
				listOf(
					ChaptersSortOrder.SOURCE to "Source order",
					ChaptersSortOrder.NAME to "Chapter name",
					ChaptersSortOrder.DATE to "Upload date",
				).forEach { (order, label) ->
					val selected = uiState.chaptersSortOrder == order
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable {
								viewModel.setChaptersSortOrder(order)
								showChaptersFilterSheet = false
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
			}
		}
	}

	if (showCreateCategoryDialog) {
		AlertDialog(
			onDismissRequest = { showCreateCategoryDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			title = { Text("New Category", color = NemuriTheme.colors.textPrimary) },
			text = {
				OutlinedTextField(
					value = newCategoryName,
					onValueChange = { newCategoryName = it },
					placeholder = { Text("Category name") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.createAndAddCategory(newCategoryName)
						showCreateCategoryDialog = false
					},
				) {
					Text("Create", color = NemuriTheme.colors.cyan)
				}
			},
			dismissButton = {
				TextButton(onClick = { showCreateCategoryDialog = false }) {
					Text("Cancel", color = NemuriTheme.colors.textSecondary)
				}
			},
		)
	}

	val activeNsfwLaunch = pendingNsfwLaunch
	if (activeNsfwLaunch != null && uiState.manga != null) {
		val nsfwManga = uiState.manga!!
		NemuriModalSheet(
			title = "18+ Reading Privacy",
			subtitle = "Open \"${uiState.title}\" in Incognito mode?",
			onDismiss = { pendingNsfwLaunch = null },
		) {
			Column(
				modifier = Modifier.padding(vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					text = "Incognito pauses reading history, streaks, and external tracker sync for this session.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
				Button(
					onClick = {
						val chId = activeNsfwLaunch.chapterId
						pendingNsfwLaunch = null
						launchReaderSession(nsfwManga, chId, incognito = true)
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(17.dp))
					Spacer(Modifier.width(8.dp))
					Text("Read in Incognito", fontWeight = FontWeight.Bold)
				}
				Button(
					onClick = {
						val chId = activeNsfwLaunch.chapterId
						pendingNsfwLaunch = null
						launchReaderSession(nsfwManga, chId, incognito = false)
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.surfaceElevated,
						contentColor = NemuriTheme.colors.textPrimary,
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
				) {
					Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
					Spacer(Modifier.width(6.dp))
					Text("Read Normally")
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					TextButton(
						onClick = {
							val chId = activeNsfwLaunch.chapterId
							viewModel.setIncognitoForNsfw(TriStateOption.ENABLED)
							pendingNsfwLaunch = null
							launchReaderSession(nsfwManga, chId, incognito = true)
						},
					) {
						Text("Always enable for 18+", color = NemuriTheme.colors.cyan)
					}
					TextButton(
						onClick = {
							val chId = activeNsfwLaunch.chapterId
							viewModel.setIncognitoForNsfw(TriStateOption.DISABLED)
							pendingNsfwLaunch = null
							launchReaderSession(nsfwManga, chId, incognito = false)
						},
					) {
						Text("Don't ask again", color = NemuriTheme.colors.textMuted)
					}
				}
			}
		}
	}

	uiState.pendingDuplicatePrompt?.let { prompt ->
		val incomingSig = remember(prompt.incoming.id, prompt.incoming.title, prompt.incoming.tags) {
			TitleSignatureParser.parse(prompt.incoming.title, prompt.incoming.tags.map { it.title })
		}
		val strategy = uiState.duplicateWinnerStrategy
		NemuriModalSheet(
			title = "Check & Swap: Duplicate Detected",
			subtitle = "Compare editions & winner strategy (${strategy.shortLabel})",
			onDismiss = viewModel::dismissDuplicatePrompt,
		) {
			Column(
				modifier = Modifier
					.verticalScroll(rememberScrollState())
					.padding(vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Surface(
					color = NemuriTheme.colors.tealContainer.copy(alpha = 0.45f),
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.55f)),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(12.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = "INCOMING ENTRY",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
						)
						Text(
							text = prompt.incoming.title,
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = "${prompt.incoming.source.getTitle(context)}" +
								(prompt.incomingChaptersCount?.let { " • $it ch" } ?: ""),
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
						)
						if (incomingSig.editionFlags.isNotEmpty()) {
							FlowRow(
								horizontalArrangement = Arrangement.spacedBy(6.dp),
								verticalArrangement = Arrangement.spacedBy(4.dp),
							) {
								incomingSig.editionFlags.forEach { flag ->
									Surface(
										color = NemuriTheme.colors.surfaceElevated,
										shape = RoundedCornerShape(6.dp),
										border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
									) {
										Text(
											text = flag.badgeText,
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.cyan,
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
										)
									}
								}
							}
						}
					}
				}

				prompt.duplicates.forEach { dup ->
					val dupSig = remember(dup.manga.id, dup.manga.title, dup.manga.tags) {
						TitleSignatureParser.parse(dup.manga.title, dup.manga.tags.map { it.title })
					}
					val hasEditionDiff = TitleSignatureParser.hasEditionDifference(incomingSig, dupSig)
					val incomingCh = prompt.incomingChaptersCount ?: 0
					val incomingWins = when (strategy) {
						DuplicateWinnerStrategy.MOST_CHAPTERS -> incomingCh > dup.chaptersCount
						DuplicateWinnerStrategy.FURTHEST_READ -> dup.progress <= 0f && incomingCh >= dup.chaptersCount
						DuplicateWinnerStrategy.HEALTHY_SOURCE -> !prompt.incoming.source.isLocal && incomingCh >= dup.chaptersCount
						DuplicateWinnerStrategy.HIGHEST_RATING -> prompt.incoming.rating > dup.manga.rating
					}
					val chDelta = incomingCh - dup.chaptersCount

					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(
								1.dp,
								if (incomingWins) NemuriTheme.colors.cyan.copy(alpha = 0.6f) else NemuriTheme.colors.border,
								RoundedCornerShape(14.dp),
							),
					) {
						Column(
							modifier = Modifier.padding(12.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Row(
								horizontalArrangement = Arrangement.spacedBy(10.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								MangaCoverImage(
									manga = dup.manga,
									coverUrl = dup.manga.coverUrl,
									modifier = Modifier
										.size(width = 48.dp, height = 68.dp)
										.clip(RoundedCornerShape(8.dp)),
								)
								Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
									) {
										Surface(
											color = if (incomingWins) {
												NemuriTheme.colors.cyan.copy(alpha = 0.16f)
											} else {
												NemuriTheme.colors.healthy.copy(alpha = 0.16f)
											},
											shape = RoundedCornerShape(6.dp),
										) {
											Text(
												text = if (incomingWins) {
													"★ Incoming Wins (${strategy.shortLabel})"
												} else {
													"★ Existing Wins (${strategy.shortLabel})"
												},
												style = MaterialTheme.typography.labelSmall,
												color = if (incomingWins) NemuriTheme.colors.cyan else NemuriTheme.colors.healthy,
												modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
											)
										}
									}
									Text(
										text = dup.manga.title,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis,
									)
									val deltaText = when {
										prompt.incomingChaptersCount == null -> ""
										chDelta > 0 -> " (Incoming +$chDelta ch)"
										chDelta < 0 -> " (Existing +${-chDelta} ch)"
										else -> " (Same ch count)"
									}
									Text(
										text = "${dup.manga.source.getTitle(context)} • ${dup.chaptersCount} ch$deltaText",
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.textMuted,
									)
									if (dupSig.editionFlags.isNotEmpty() || hasEditionDiff) {
										FlowRow(
											horizontalArrangement = Arrangement.spacedBy(6.dp),
											verticalArrangement = Arrangement.spacedBy(4.dp),
										) {
											dupSig.editionFlags.forEach { flag ->
												Surface(
													color = NemuriTheme.colors.card,
													shape = RoundedCornerShape(6.dp),
													border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.5f)),
												) {
													Text(
														text = flag.badgeText,
														style = MaterialTheme.typography.labelSmall,
														color = NemuriTheme.colors.amber,
														modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													)
												}
											}
											if (hasEditionDiff) {
												Surface(
													color = NemuriTheme.colors.amber.copy(alpha = 0.15f),
													shape = RoundedCornerShape(6.dp),
												) {
													Text(
														text = "Edition Variant",
														style = MaterialTheme.typography.labelSmall,
														color = NemuriTheme.colors.amber,
														modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													)
												}
											}
										}
									}
									if (dup.categories.isNotEmpty()) {
										Text(
											text = "Categories: ${dup.categories.joinToString()}",
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.textDim,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
									if (dup.progress >= 0f) {
										Text(
											text = "Progress: ${(dup.progress * 100f).roundToInt().coerceIn(0, 100)}%",
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.healthy,
										)
									}
								}
							}
							if (dup.canReplace) {
								Button(
									onClick = { viewModel.replaceDuplicate(dup) },
									colors = ButtonDefaults.buttonColors(
										containerColor = NemuriTheme.colors.cyan,
										contentColor = NemuriTheme.colors.onCyan,
									),
									shape = RoundedCornerShape(10.dp),
									modifier = Modifier.fillMaxWidth(),
								) {
									Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(17.dp))
									Spacer(Modifier.width(6.dp))
									Text(
										text = if (prompt.migrateProgressOnReplace) {
											"Swap & Migrate (Carry Progress)"
										} else {
											"Swap & Migrate"
										},
										fontWeight = FontWeight.Bold,
									)
								}
							}
						}
					}
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					TextButton(
						onClick = viewModel::dismissDuplicatePrompt,
						modifier = Modifier.weight(0.8f),
					) {
						Text("Cancel", color = NemuriTheme.colors.textMuted)
					}
					Button(
						onClick = viewModel::keepBothAndIgnoreDuplicates,
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.surfaceElevated,
							contentColor = NemuriTheme.colors.textPrimary,
						),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier
							.weight(1.4f)
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(10.dp)),
					) {
						Text("Keep Both (Ignore Pair)")
					}
				}
			}
		}
	}

	if (showDisplayOptionsSheet) {
		val optionShape = NemuriTheme.shape(12.dp)
		NemuriModalSheet(
			title = "Details Display",
			subtitle = "Header layout & quick-scroll controls",
			onDismiss = { showDisplayOptionsSheet = false },
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 4.dp, vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = "DETAILS LAYOUT",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				listOf(
					Triple(
						DetailsUiMode.EXPRESSIVE,
						"Centered",
						"Atmospheric blurred cover, centered metadata & unified Continue / Library row",
					),
					Triple(
						DetailsUiMode.COMPACT,
						"Compact",
						"Side-by-side cover, chapter summary chip & balanced Continue / Library row",
					),
					Triple(
						DetailsUiMode.ULTRA_COMPACT,
						"Ultra Compact",
						"Condensed summary header & collapsible synopsis so chapters start immediately",
					),
				).forEach { (mode, title, subtitle) ->
					val selected = uiState.detailsUiMode == mode
					Surface(
						color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = optionShape,
						border = BorderStroke(
							width = if (selected) 1.5.dp else 1.dp,
							color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
						),
						modifier = Modifier
							.fillMaxWidth()
							.clip(optionShape)
							.clickable { handleDetailsUiModeChange(mode) },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(
								modifier = Modifier
									.weight(1f)
									.padding(end = 10.dp),
								verticalArrangement = Arrangement.spacedBy(2.dp),
							) {
								Text(
									text = title,
									style = MaterialTheme.typography.titleSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
								)
								Text(
									text = subtitle,
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
							if (selected) {
								Icon(
									imageVector = Icons.Default.Check,
									contentDescription = null,
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}

				HorizontalDivider(color = NemuriTheme.colors.border)

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(optionShape)
						.clickable { viewModel.setQuickScrollButtonsEnabled(!uiState.quickScrollButtonsEnabled) }
						.padding(vertical = 2.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(
						modifier = Modifier
							.weight(1f)
							.padding(end = 12.dp),
						verticalArrangement = Arrangement.spacedBy(2.dp),
					) {
						Text(
							text = "Show Floating ▲ / ▼ Scroll Buttons",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Medium,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Display quick jump-to-top / bottom capsule on long chapter lists",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					Switch(
						checked = uiState.quickScrollButtonsEnabled,
						onCheckedChange = viewModel::setQuickScrollButtonsEnabled,
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}
			}
		}
	}

	if (showStatsSheet) {
		val stats = uiState.titleStats
		NemuriModalSheet(
			title = "Title Reading Stats",
			subtitle = uiState.title,
			onDismiss = { showStatsSheet = false },
		) {
			if (stats.isLoading) {
				NemuriLoadingState(message = "Loading reading history…")
			} else if (stats.totalDurationMs == 0L && stats.totalPages == 0) {
				Column(
					modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text("No reading sessions recorded yet", style = MaterialTheme.typography.titleSmall, color = NemuriTheme.colors.textPrimary)
					Text("Sessions are tracked automatically while reading with Statistics enabled.", style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textMuted)
				}
			} else {
				Column(
					modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						MiniStatBox("Time Read", formatDurationShort(stats.totalDurationMs), Modifier.weight(1f))
						MiniStatBox("Chapters", "${stats.totalChapters}", Modifier.weight(1f))
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						MiniStatBox("Pages", "${stats.totalPages}", Modifier.weight(1f))
						MiniStatBox("Active Days", "${stats.activeDays}", Modifier.weight(1f))
					}
					if (stats.firstReadAt > 0L) {
						Text(
							text = "First read: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(stats.firstReadAt))}",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					if (stats.recentDailyPages.isNotEmpty()) {
						val maxPages = stats.recentDailyPages.maxOf { it.second }.coerceAtLeast(1)
						Text("Recent Daily Activity (Pages)", style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.cyan)
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.height(64.dp),
							horizontalArrangement = Arrangement.spacedBy(4.dp),
							verticalAlignment = Alignment.Bottom,
						) {
							stats.recentDailyPages.forEach { (_, count) ->
								val frac = (count.toFloat() / maxPages).coerceIn(0.12f, 1f)
								Box(
									modifier = Modifier
										.weight(1f)
										.height((56 * frac).dp)
										.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
										.background(NemuriTheme.colors.cyan),
								)
							}
						}
					}
				}
			}
		}
	}
}

private data class PendingNsfwLaunch(val chapterId: Long?)

@Composable
private fun DetailsTopToolbar(
	uiState: MangaDetailsUiState,
	onBack: () -> Unit,
	onToggleIncognito: () -> Unit,
	onRefresh: () -> Unit,
	onOpenBrowser: () -> Unit,
	onOpenDownloads: () -> Unit,
	onOpenStats: () -> Unit,
	onOpenMigrate: () -> Unit,
	onOpenDisplayOptions: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 6.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
			Icon(
				imageVector = Icons.AutoMirrored.Filled.ArrowBack,
				contentDescription = "Back",
				tint = NemuriTheme.colors.textPrimary,
			)
		}
		Spacer(modifier = Modifier.weight(1f))
		IconButton(onClick = onToggleIncognito, modifier = Modifier.size(40.dp)) {
			Surface(
				color = if (uiState.isIncognitoActive) {
					NemuriTheme.colors.cyan.copy(alpha = 0.22f)
				} else {
					Color.Transparent
				},
				shape = NemuriTheme.pillShape(),
				border = if (uiState.isIncognitoActive) {
					BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.7f))
				} else {
					null
				},
				modifier = Modifier.size(32.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = "Toggle Incognito",
						tint = if (uiState.isIncognitoActive) {
							NemuriTheme.colors.cyan
						} else {
							NemuriTheme.colors.textSecondary
						},
						modifier = Modifier.size(18.dp),
					)
				}
			}
		}
		if (uiState.isRefreshing) {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier.size(40.dp),
			) {
				CircularProgressIndicator(
					color = NemuriTheme.colors.cyan,
					strokeWidth = 2.dp,
					modifier = Modifier.size(18.dp),
				)
			}
		} else {
			IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
				Icon(
					imageVector = Icons.Default.Refresh,
					contentDescription = "Refresh",
					tint = NemuriTheme.colors.textSecondary,
					modifier = Modifier.size(20.dp),
				)
			}
		}
		if (!uiState.isLocalOnly && uiState.publicUrl.isNotBlank()) {
			IconButton(onClick = onOpenBrowser, modifier = Modifier.size(40.dp)) {
				Icon(
					imageVector = Icons.Default.OpenInBrowser,
					contentDescription = "Open in WebView",
					tint = NemuriTheme.colors.textSecondary,
					modifier = Modifier.size(20.dp),
				)
			}
		}
		if (!uiState.isLocalOnly && uiState.totalBranchChapters > 0) {
			IconButton(onClick = onOpenDownloads, modifier = Modifier.size(40.dp)) {
				Icon(
					imageVector = Icons.Default.Download,
					contentDescription = "Download options",
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(20.dp),
				)
			}
		}
		IconButton(onClick = onOpenStats, modifier = Modifier.size(40.dp)) {
			Icon(
				imageVector = Icons.Default.BarChart,
				contentDescription = "Reading stats",
				tint = NemuriTheme.colors.textSecondary,
				modifier = Modifier.size(20.dp),
			)
		}
		if (!uiState.isLocalOnly) {
			IconButton(onClick = onOpenMigrate, modifier = Modifier.size(40.dp)) {
				Icon(
					imageVector = Icons.Default.SwapHoriz,
					contentDescription = "Migrate source",
					tint = NemuriTheme.colors.textSecondary,
					modifier = Modifier.size(20.dp),
				)
			}
		}
		IconButton(onClick = onOpenDisplayOptions, modifier = Modifier.size(40.dp)) {
			Icon(
				imageVector = Icons.Default.Tune,
				contentDescription = "Display options",
				tint = NemuriTheme.colors.cyan,
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsExpressiveHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenMigrate: () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val coverShape = NemuriTheme.shape(12.dp)
	val actionShape = NemuriTheme.shape(8.dp)

	Box(modifier = Modifier.fillMaxWidth()) {
		MangaCoverImage(
			manga = manga,
			coverUrl = uiState.backdropUrl,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxWidth()
				.height(260.dp)
				.blur(18.dp)
				.alpha(0.32f),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(260.dp)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							NemuriTheme.colors.background.copy(alpha = 0.20f),
							NemuriTheme.colors.background.copy(alpha = 0.72f),
							NemuriTheme.colors.background,
						),
					),
				),
		)

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding(),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			toolbar()

			Spacer(modifier = Modifier.height(6.dp))

			Surface(
				shape = coverShape,
				shadowElevation = 10.dp,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
				modifier = Modifier.size(width = 118.dp, height = 166.dp),
			) {
				MangaCoverImage(
					manga = manga,
					coverUrl = uiState.coverUrl,
					modifier = Modifier
						.fillMaxSize()
						.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 12.dp),
				)
			}

			Spacer(modifier = Modifier.height(10.dp))

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(3.dp),
			) {
				Text(
					text = uiState.title,
					fontSize = 18.sp,
					lineHeight = 22.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
					textAlign = TextAlign.Center,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				if (uiState.altTitle != null) {
					Text(
						text = uiState.altTitle,
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (uiState.authorsLabel.isNotBlank()) {
					Text(
						text = uiState.authorsLabel,
						fontSize = 12.5.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Spacer(modifier = Modifier.height(10.dp))

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Surface(
					color = if (uiState.isInLibrary) {
						MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
					} else {
						MaterialTheme.colorScheme.surfaceContainer
					},
					shape = actionShape,
					border = BorderStroke(
						width = 1.dp,
						color = if (uiState.isInLibrary) {
							MaterialTheme.colorScheme.secondary
						} else {
							MaterialTheme.colorScheme.outlineVariant
						},
					),
					modifier = Modifier
						.weight(1f)
						.height(38.dp)
						.clip(actionShape)
						.combinedClickable(
							onClick = onToggleLibrary,
							onLongClick = onOpenCategories,
						),
				) {
					Row(
						modifier = Modifier
							.fillMaxSize()
							.padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Icon(
							imageVector = if (uiState.isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
							contentDescription = null,
							tint = if (uiState.isInLibrary) {
								MaterialTheme.colorScheme.secondary
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
							modifier = Modifier.size(16.dp),
						)
						Spacer(modifier = Modifier.width(6.dp))
						Text(
							text = if (uiState.isInLibrary) "In Library" else "Add to Library",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}

				Surface(
					color = MaterialTheme.colorScheme.surfaceContainer,
					shape = actionShape,
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
					modifier = Modifier
						.weight(1f)
						.height(38.dp)
						.clip(actionShape)
						.clickable(enabled = !uiState.isLocalOnly, onClick = onOpenMigrate),
				) {
					Row(
						modifier = Modifier
							.fillMaxSize()
							.padding(horizontal = 10.dp)
							.alpha(if (uiState.isLocalOnly) 0.45f else 1f),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.Center,
					) {
						Icon(
							imageVector = Icons.Default.SwapHoriz,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(16.dp),
						)
						Spacer(modifier = Modifier.width(6.dp))
						Text(
							text = "Migrate",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}

				Surface(
					color = MaterialTheme.colorScheme.surfaceContainer,
					shape = actionShape,
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
					modifier = Modifier
						.size(38.dp)
						.clip(actionShape)
						.clickable(onClick = onOpenCategories),
				) {
					Box(
						contentAlignment = Alignment.Center,
						modifier = Modifier.fillMaxSize(),
					) {
						Icon(
							imageVector = Icons.Default.Folder,
							contentDescription = "Categories",
							tint = if (uiState.isInLibrary) {
								MaterialTheme.colorScheme.secondary
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(10.dp))

			MetadataProgressCard(
				manga = manga,
				uiState = uiState,
				onOpenSourceBrowse = onOpenSourceBrowse,
			)

			Spacer(modifier = Modifier.height(4.dp))
		}
	}
}

private fun formatStarRating(ratingLabel: String?): String? {
	val cleaned = ratingLabel?.replace("★", "")?.trim().orEmpty()
	return cleaned.takeIf { it.isNotEmpty() }?.let { "★ $it" }
}

@Composable
private fun DetailsSourceIcon(
	manga: Manga,
	isLocalOnly: Boolean,
	size: Dp,
) {
	val fallbackIcon: @Composable () -> Unit = {
		Icon(
			imageVector = if (isLocalOnly) Icons.Default.Folder else Icons.Default.Public,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(size),
		)
	}
	if (LocalInspectionMode.current || isLocalOnly || manga.source == LocalMangaSource) {
		fallbackIcon()
	} else {
		SubcomposeAsyncImage(
			model = manga.source.faviconUri(),
			contentDescription = null,
			contentScale = ContentScale.Crop,
			loading = { fallbackIcon() },
			error = { fallbackIcon() },
			modifier = Modifier
				.size(size)
				.clip(RoundedCornerShape(3.dp)),
		)
	}
}

@Composable
private fun MetadataProgressCard(
	manga: Manga,
	uiState: MangaDetailsUiState,
	onOpenSourceBrowse: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val cardShape = NemuriTheme.shape(12.dp)
	val labelWidth = 68.dp
	val pct = (uiState.progressPercent * 100f).roundToInt().coerceIn(0, 100)
	val sourceEndMeta = listOfNotNull(
		uiState.formatBadge?.takeIf { it.isNotBlank() },
		formatStarRating(uiState.ratingLabel),
	).joinToString(" • ")
	val chaptersValue = when {
		uiState.currentChapterPosition > 0 && uiState.totalBranchChapters > 0 ->
			"Chapter ${uiState.currentChapterPosition} of ${uiState.totalBranchChapters}"
		uiState.totalBranchChapters > 0 ->
			"${uiState.totalBranchChapters} Chapters"
		else -> "No Chapters"
	}
	val chaptersEndMeta = buildList {
		if (uiState.unreadChaptersCount > 0) add("${uiState.unreadChaptersCount} unread")
		if (uiState.downloadedCount > 0) add("${uiState.downloadedCount} offline")
	}.joinToString(" • ")

	Surface(
		color = MaterialTheme.colorScheme.surfaceContainer,
		shape = cardShape,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Source",
					fontSize = 12.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					modifier = Modifier.width(labelWidth),
				)
				Row(
					modifier = Modifier
						.weight(1f)
						.clip(NemuriTheme.shape(4.dp))
						.clickable(
							enabled = !uiState.isLocalOnly && uiState.sourceTitle.isNotBlank(),
							onClick = onOpenSourceBrowse,
						),
					verticalAlignment = Alignment.CenterVertically,
				) {
					DetailsSourceIcon(
						manga = manga,
						isLocalOnly = uiState.isLocalOnly,
						size = 14.dp,
					)
					Spacer(modifier = Modifier.width(5.dp))
					Text(
						text = uiState.sourceTitle.ifBlank { "Local" },
						fontSize = 12.5.sp,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (sourceEndMeta.isNotEmpty()) {
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = sourceEndMeta,
						style = MaterialTheme.typography.bodySmall.copy(
							fontSize = 12.sp,
							fontFeatureSettings = "tnum",
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Status",
					fontSize = 12.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					modifier = Modifier.width(labelWidth),
				)
				Text(
					text = uiState.statusLabel.ifBlank { "Unknown" },
					fontSize = 12.5.sp,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Chapters",
					fontSize = 12.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					modifier = Modifier.width(labelWidth),
				)
				Text(
					text = chaptersValue,
					style = MaterialTheme.typography.bodySmall.copy(
						fontSize = 12.5.sp,
						fontWeight = FontWeight.Medium,
						fontFeatureSettings = "tnum",
					),
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				if (chaptersEndMeta.isNotEmpty()) {
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = chaptersEndMeta,
						style = MaterialTheme.typography.bodySmall.copy(
							fontSize = 12.sp,
							fontFeatureSettings = "tnum",
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Progress",
					fontSize = 12.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					modifier = Modifier.width(labelWidth),
				)
				LinearProgressIndicator(
					progress = { uiState.progressPercent.coerceIn(0f, 1f) },
					color = MaterialTheme.colorScheme.primary,
					trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
					modifier = Modifier
						.weight(1f)
						.height(6.dp)
						.clip(NemuriTheme.shape(3.dp)),
				)
				Spacer(modifier = Modifier.width(10.dp))
				Text(
					text = "$pct%",
					style = MaterialTheme.typography.bodySmall.copy(
						fontSize = 12.sp,
						fontWeight = FontWeight.Medium,
						fontFeatureSettings = "tnum",
					),
					color = MaterialTheme.colorScheme.onSurface,
					textAlign = TextAlign.End,
					maxLines = 1,
					modifier = Modifier.width(36.dp),
				)
			}
		}
	}
}

private val DETAILS_COMPACT_CONTROL_HEIGHT = 36.dp
private val DETAILS_LIBRARY_CHIP_HEIGHT = 34.dp
private val DETAILS_ULTRA_LIBRARY_ICON_SIZE = 36.dp

private fun resolveActiveLibraryCategoryName(uiState: MangaDetailsUiState): String? {
	if (uiState.memberCategoryIds.size != 1) return null
	val categoryId = uiState.memberCategoryIds.first()
	return uiState.allCategories.firstOrNull { it.id == categoryId }?.title?.takeIf { it.isNotBlank() }
}

@Composable
private fun DetailsContinueSplitButton(
	label: String,
	targetChapterShortLabel: String,
	isIncognitoActive: Boolean,
	expanded: Boolean,
	canStartFromChapterOne: Boolean,
	onPrimaryClick: () -> Unit,
	onReadInIncognito: () -> Unit,
	onToggleIncognitoMode: () -> Unit,
	onStartFromChapterOne: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var menuExpanded by remember { mutableStateOf(false) }
	val isSharp = NemuriTheme.isSharpTacticalGeometry
	val leftShape = if (isSharp) {
		RoundedCornerShape(2.dp)
	} else {
		RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp)
	}
	val rightShape = if (isSharp) {
		RoundedCornerShape(2.dp)
	} else {
		RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp)
	}

	Box(modifier = modifier) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				onClick = onPrimaryClick,
				shape = leftShape,
				color = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary,
				shadowElevation = 6.dp,
				modifier = Modifier.height(44.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxHeight()
						.animateContentSize()
						.padding(
							start = if (expanded) 16.dp else 14.dp,
							end = 14.dp,
						),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Icon(
						imageVector = if (isIncognitoActive) Icons.Default.VisibilityOff else Icons.Default.PlayArrow,
						contentDescription = label,
						modifier = Modifier.size(if (isIncognitoActive) 18.dp else 19.dp),
					)
					if (expanded) {
						Spacer(modifier = Modifier.width(6.dp))
						Text(
							text = label,
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}

			Spacer(modifier = Modifier.width(2.dp))

			Surface(
				onClick = { menuExpanded = true },
				shape = rightShape,
				color = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary,
				shadowElevation = 6.dp,
				modifier = Modifier
					.width(38.dp)
					.height(44.dp),
			) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier.fillMaxSize(),
				) {
					Icon(
						imageVector = Icons.Default.KeyboardArrowDown,
						contentDescription = "More reading options",
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}

		DropdownMenu(
			expanded = menuExpanded,
			onDismissRequest = { menuExpanded = false },
			offset = DpOffset(0.dp, (-6).dp),
			containerColor = NemuriTheme.colors.sheet,
			shape = NemuriTheme.shape(14.dp),
		) {
			DropdownMenuItem(
				text = {
					Text(
						text = "Read $targetChapterShortLabel in Incognito",
						color = NemuriTheme.colors.textPrimary,
					)
				},
				leadingIcon = {
					Icon(
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(18.dp),
					)
				},
				onClick = {
					menuExpanded = false
					onReadInIncognito()
				},
			)
			DropdownMenuItem(
				text = {
					Text(
						text = if (isIncognitoActive) "Disable Incognito Mode" else "Enable Incognito Mode",
						color = NemuriTheme.colors.textPrimary,
					)
				},
				leadingIcon = {
					Icon(
						imageVector = if (isIncognitoActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
						contentDescription = null,
						tint = NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(18.dp),
					)
				},
				onClick = {
					menuExpanded = false
					onToggleIncognitoMode()
				},
			)
			DropdownMenuItem(
				text = {
					Text(
						text = "Start from Chapter 1",
						color = if (canStartFromChapterOne) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textMuted,
					)
				},
				leadingIcon = {
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = null,
						tint = if (canStartFromChapterOne) MaterialTheme.colorScheme.primary else NemuriTheme.colors.textMuted,
						modifier = Modifier.size(18.dp),
					)
				},
				enabled = canStartFromChapterOne,
				onClick = {
					menuExpanded = false
					onStartFromChapterOne()
				},
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsLibraryButton(
	isInLibrary: Boolean,
	memberCategoriesCount: Int,
	activeCategoryName: String?,
	height: Dp = DETAILS_LIBRARY_CHIP_HEIGHT,
	iconOnly: Boolean = false,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val chipShape = NemuriTheme.shape(8.dp)
	val label = when {
		!isInLibrary -> "Favourite this"
		memberCategoriesCount > 1 -> "In Library ($memberCategoriesCount)"
		!activeCategoryName.isNullOrBlank() -> activeCategoryName
		else -> "In Library"
	}
	val sizeModifier = if (iconOnly) {
		modifier.size(height)
	} else {
		modifier.height(height)
	}

	Surface(
		color = if (isInLibrary) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else Color.Transparent,
		shape = chipShape,
		border = BorderStroke(
			width = 1.dp,
			color = if (isInLibrary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
		),
		modifier = sizeModifier
			.clip(chipShape)
			.combinedClickable(
				onClick = {
					if (isInLibrary) {
						onOpenCategories()
					} else {
						onToggleLibrary()
					}
				},
				onLongClick = onOpenCategories,
			),
	) {
		if (iconOnly) {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier.fillMaxSize(),
			) {
				Icon(
					imageVector = if (isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
					contentDescription = label,
					tint = if (isInLibrary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(18.dp),
				)
			}
		} else {
			Row(
				modifier = Modifier
					.fillMaxHeight()
					.padding(start = 10.dp, end = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.Center,
			) {
				Icon(
					imageVector = if (isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
					contentDescription = null,
					tint = if (isInLibrary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(16.dp),
				)
				Spacer(modifier = Modifier.width(6.dp))
				Text(
					text = label,
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.widthIn(max = 180.dp),
				)
				Spacer(modifier = Modifier.width(2.dp))
				Icon(
					imageVector = Icons.Default.ArrowDropDown,
					contentDescription = null,
					tint = if (isInLibrary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(18.dp),
				)
			}
		}
	}
}

@Composable
private fun DetailsCompactHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val coverShape = NemuriTheme.shape(10.dp)

	Box(modifier = Modifier.fillMaxWidth()) {
		MangaCoverImage(
			manga = manga,
			coverUrl = uiState.backdropUrl,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxWidth()
				.height(210.dp)
				.alpha(0.24f),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(210.dp)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							NemuriTheme.colors.background.copy(alpha = 0.25f),
							NemuriTheme.colors.background.copy(alpha = 0.85f),
							NemuriTheme.colors.background,
						),
					),
				),
		)

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding(),
		) {
			toolbar()

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 6.dp),
				verticalAlignment = Alignment.Top,
			) {
				MangaCoverImage(
					manga = manga,
					coverUrl = uiState.coverUrl,
					modifier = Modifier
						.size(width = 94.dp, height = 132.dp)
						.clip(coverShape)
						.border(1.dp, MaterialTheme.colorScheme.outlineVariant, coverShape)
						.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 10.dp),
				)

				Column(
					modifier = Modifier
						.weight(1f)
						.height(132.dp)
						.padding(start = 12.dp),
					verticalArrangement = Arrangement.SpaceBetween,
				) {
					Column(
						modifier = Modifier.weight(1f, fill = false),
					) {
						Text(
							text = uiState.title,
							fontSize = 16.5.sp,
							lineHeight = 20.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 3,
							overflow = TextOverflow.Ellipsis,
						)
						if (uiState.authorsLabel.isNotBlank()) {
							Spacer(modifier = Modifier.height(3.dp))
							Text(
								text = uiState.authorsLabel,
								fontSize = 12.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}

					DetailsLibraryButton(
						isInLibrary = uiState.isInLibrary,
						memberCategoriesCount = uiState.memberCategoryIds.size,
						activeCategoryName = resolveActiveLibraryCategoryName(uiState),
						height = 36.dp,
						onToggleLibrary = onToggleLibrary,
						onOpenCategories = onOpenCategories,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}

			Spacer(modifier = Modifier.height(4.dp))

			MetadataProgressCard(
				manga = manga,
				uiState = uiState,
				onOpenSourceBrowse = onOpenSourceBrowse,
			)

			Spacer(modifier = Modifier.height(4.dp))
		}
	}
}

@Composable
private fun DetailsUltraCompactHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val thumbShape = NemuriTheme.shape(8.dp)
	val barShape = NemuriTheme.shape(8.dp)
	val pct = (uiState.progressPercent * 100f).roundToInt().coerceIn(0, 100)
	val starRating = formatStarRating(uiState.ratingLabel)
	val chapterRatioText = if (uiState.totalBranchChapters > 0) {
		"${uiState.currentChapterPosition}/${uiState.totalBranchChapters} • $pct%"
	} else {
		"0 ch"
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.statusBarsPadding(),
	) {
		toolbar()

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = uiState.coverUrl,
				modifier = Modifier
					.size(width = 48.dp, height = 68.dp)
					.clip(thumbShape)
					.border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape)
					.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 8.dp),
			)

			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = 10.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = uiState.title,
					fontSize = 15.sp,
					lineHeight = 19.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				if (uiState.authorsLabel.isNotBlank()) {
					Text(
						text = uiState.authorsLabel,
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(8.dp))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceContainer,
				shape = barShape,
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
				modifier = Modifier
					.weight(1f)
					.height(36.dp),
			) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.clip(barShape),
				) {
					Row(
						modifier = Modifier
							.fillMaxSize()
							.padding(horizontal = 8.dp)
							.padding(bottom = 2.5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Row(
							modifier = Modifier
								.weight(1f)
								.clickable(
									enabled = !uiState.isLocalOnly && uiState.sourceTitle.isNotBlank(),
									onClick = onOpenSourceBrowse,
								),
							verticalAlignment = Alignment.CenterVertically,
						) {
							DetailsSourceIcon(
								manga = manga,
								isLocalOnly = uiState.isLocalOnly,
								size = 13.dp,
							)
							Spacer(modifier = Modifier.width(4.dp))
							Text(
								text = uiState.sourceTitle.ifBlank { "Local" },
								fontSize = 11.5.sp,
								fontWeight = FontWeight.Medium,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						if (uiState.statusLabel.isNotBlank()) {
							Box(
								modifier = Modifier
									.width(1.dp)
									.height(14.dp)
									.background(MaterialTheme.colorScheme.outlineVariant),
							)
							Text(
								text = uiState.statusLabel,
								fontSize = 11.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
							)
						}

						if (starRating != null) {
							Box(
								modifier = Modifier
									.width(1.dp)
									.height(14.dp)
									.background(MaterialTheme.colorScheme.outlineVariant),
							)
							Text(
								text = starRating,
								style = MaterialTheme.typography.bodySmall.copy(
									fontSize = 11.5.sp,
									fontFeatureSettings = "tnum",
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
							)
						}

						Box(
							modifier = Modifier
								.width(1.dp)
								.height(14.dp)
								.background(MaterialTheme.colorScheme.outlineVariant),
						)
						Text(
							text = chapterRatioText,
							style = MaterialTheme.typography.bodySmall.copy(
								fontSize = 11.5.sp,
								fontWeight = FontWeight.Medium,
								fontFeatureSettings = "tnum",
							),
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
						)
					}

					Box(
						modifier = Modifier
							.align(Alignment.BottomStart)
							.fillMaxWidth()
							.height(2.5.dp)
							.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
					) {
						val fraction = uiState.progressPercent.coerceIn(0f, 1f)
						if (fraction > 0f) {
							Box(
								modifier = Modifier
									.fillMaxWidth(fraction)
									.fillMaxHeight()
									.background(MaterialTheme.colorScheme.primary),
							)
						}
					}
				}
			}

			DetailsLibraryButton(
				isInLibrary = uiState.isInLibrary,
				memberCategoriesCount = uiState.memberCategoryIds.size,
				activeCategoryName = null,
				height = DETAILS_ULTRA_LIBRARY_ICON_SIZE,
				iconOnly = true,
				onToggleLibrary = onToggleLibrary,
				onOpenCategories = onOpenCategories,
			)
		}

		Spacer(modifier = Modifier.height(5.dp))
	}
}

@Composable
private fun WhereWasIRecapCard(
	recap: org.koitharu.kotatsu.ai.data.MangaAiRecapEntity?,
	isGenerating: Boolean,
	engineBadgeLabel: String,
	recapDepth: RecapDepth,
	onSelectDepth: (RecapDepth) -> Unit,
	onGenerate: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	val cardShape = NemuriTheme.shape(16.dp)
	val pillShape = NemuriTheme.pillShape()
	val actionShape = NemuriTheme.shape(10.dp)
	val innerSegmentShape = NemuriTheme.shape(7.dp)

	Surface(
		color = NemuriTheme.colors.card,
		shape = cardShape,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp)
			.border(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f), cardShape),
	) {
		Column(
			modifier = Modifier.padding(14.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = Icons.Default.AutoAwesome,
						contentDescription = null,
						tint = NemuriTheme.colors.mergePurple,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = "Where Was I?",
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
					)
					Surface(
						color = if (recap?.isAiGenerated == true) {
							NemuriTheme.colors.mergePurple.copy(alpha = 0.18f)
						} else {
							NemuriTheme.colors.surfaceElevated
						},
						shape = pillShape,
					) {
						Text(
							text = engineBadgeLabel,
							style = MaterialTheme.typography.labelSmall,
							color = if (recap?.isAiGenerated == true) NemuriTheme.colors.mergePurple else NemuriTheme.colors.textMuted,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
						)
					}
				}
				IconButton(
					onClick = onOpenSettings,
					modifier = Modifier.size(28.dp),
				) {
					Icon(
						imageVector = Icons.Default.Tune,
						contentDescription = "AI Settings",
						tint = NemuriTheme.colors.textMuted,
						modifier = Modifier.size(16.dp),
					)
				}
			}

			if (recap != null) {
				Text(
					text = recap.summaryText,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textSecondary,
				)
			} else {
				Text(
					text = "Spoiler-free recap up to your current read chapter. Unread chapters remain 100% quarantined.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = actionShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier.height(DETAILS_COMPACT_CONTROL_HEIGHT),
				) {
					Row(
						modifier = Modifier
							.fillMaxHeight()
							.padding(3.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(3.dp),
					) {
						RecapDepth.entries.forEach { depth ->
							val selected = recapDepth == depth
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else Color.Transparent,
								shape = innerSegmentShape,
								modifier = Modifier
									.fillMaxHeight()
									.clip(innerSegmentShape)
									.clickable { onSelectDepth(depth) },
							) {
								Box(
									contentAlignment = Alignment.Center,
									modifier = Modifier
										.fillMaxHeight()
										.padding(horizontal = 11.dp),
								) {
									Text(
										text = depth.shortLabel,
										style = MaterialTheme.typography.labelMedium,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
										maxLines = 1,
									)
								}
							}
						}
					}
				}

				Surface(
					color = NemuriTheme.colors.tealContainer,
					shape = actionShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
					modifier = Modifier
						.height(DETAILS_COMPACT_CONTROL_HEIGHT)
						.clip(actionShape)
						.clickable(enabled = !isGenerating, onClick = onGenerate),
				) {
					Row(
						modifier = Modifier
							.fillMaxHeight()
							.padding(horizontal = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						if (isGenerating) {
							CircularProgressIndicator(
								color = NemuriTheme.colors.cyan,
								strokeWidth = 2.dp,
								modifier = Modifier.size(14.dp),
							)
							Text(
								text = "Generating…",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
							)
						} else {
							Icon(
								imageVector = Icons.Default.Refresh,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(15.dp),
							)
							Text(
								text = if (recap == null) "Catch Me Up" else "Refresh",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun MiniStatBox(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = modifier.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
	) {
		Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(value, style = MaterialTheme.typography.titleMedium, color = NemuriTheme.colors.cyan)
			Text(label, style = MaterialTheme.typography.labelSmall, color = NemuriTheme.colors.textMuted)
		}
	}
}

private fun formatDurationShort(durationMs: Long): String {
	val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
	val hours = totalMinutes / 60
	val mins = totalMinutes % 60
	return when {
		hours > 0 -> "${hours}h ${mins}m"
		totalMinutes > 0 -> "${totalMinutes}m"
		else -> "${TimeUnit.MILLISECONDS.toSeconds(durationMs)}s"
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRowView(
	row: ChapterRowItem,
	isSelectionMode: Boolean,
	isSelected: Boolean,
	showDownloadButton: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onDownloadClick: () -> Unit,
	onDeleteLocalClick: () -> Unit,
) {
	Surface(
		color = when {
			isSelected -> NemuriTheme.colors.tealContainer
			row.isCurrent -> NemuriTheme.colors.surfaceElevated
			else -> NemuriTheme.colors.card
		},
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 3.dp)
			.border(
				width = if (isSelected || row.isCurrent) 1.2.dp else 1.dp,
				color = when {
					isSelected -> NemuriTheme.colors.cyan
					row.isCurrent -> NemuriTheme.colors.cyan.copy(alpha = 0.55f)
					else -> NemuriTheme.colors.border
				},
				shape = RoundedCornerShape(12.dp),
			)
			.clip(RoundedCornerShape(12.dp))
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			if (isSelectionMode) {
				Icon(
					imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
					contentDescription = null,
					tint = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
					modifier = Modifier.size(20.dp),
				)
			} else if (row.isNew) {
				Box(
					modifier = Modifier
						.size(8.dp)
						.background(NemuriTheme.colors.cyan, CircleShape),
				)
			}

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = row.title,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Medium,
						color = when {
							row.isCurrent -> NemuriTheme.colors.cyan
							row.isRead -> NemuriTheme.colors.textDim
							else -> NemuriTheme.colors.textPrimary
						},
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (row.isBookmarked) {
						Icon(
							imageVector = Icons.Default.Bookmark,
							contentDescription = "Bookmarked",
							tint = NemuriTheme.colors.amber,
							modifier = Modifier.size(14.dp),
						)
					}
				}
				if (row.subtitle.isNotBlank()) {
					Text(
						text = row.subtitle,
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textMuted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			if (!isSelectionMode) {
				when {
					row.isDownloaded -> {
						IconButton(
							onClick = onDeleteLocalClick,
							modifier = Modifier.size(32.dp),
						) {
							Icon(
								imageVector = Icons.Default.CloudDone,
								contentDescription = "Downloaded (tap to remove)",
								tint = NemuriTheme.colors.healthy,
								modifier = Modifier.size(18.dp),
							)
						}
					}

					showDownloadButton -> {
						IconButton(
							onClick = onDownloadClick,
							modifier = Modifier.size(32.dp),
						) {
							Icon(
								imageVector = Icons.Default.Download,
								contentDescription = "Download chapter",
								tint = NemuriTheme.colors.textSecondary,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SheetActionRow(
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	label: String,
	tint: Color = NemuriTheme.colors.textPrimary,
	onClick: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
			Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
		}
	}
}

@Composable
private fun DetailsChallengeView(
	title: String,
	message: String,
	actionLabel: String,
	onAction: () -> Unit,
	onRetry: () -> Unit,
	onMigrate: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(28.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(title, style = MaterialTheme.typography.titleMedium, color = NemuriTheme.colors.textPrimary)
		Spacer(modifier = Modifier.height(8.dp))
		Text(message, style = MaterialTheme.typography.bodyMedium, color = NemuriTheme.colors.textMuted)
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
			TextButton(onClick = onRetry) {
				Text("Retry", color = NemuriTheme.colors.cyan)
			}
			TextButton(onClick = onMigrate) {
				Text("Find Alternative", color = NemuriTheme.colors.textSecondary)
			}
		}
	}
}

@Composable
private fun DetailsInlineChallengeBanner(
	title: String,
	message: String,
	actionLabel: String,
	onAction: () -> Unit,
	onRetry: (() -> Unit)? = null,
	onMigrate: (() -> Unit)? = null,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 6.dp)
			.border(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(title, style = MaterialTheme.typography.titleSmall, color = NemuriTheme.colors.amber)
			Text(message, style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				TextButton(onClick = onAction) {
					Text(actionLabel, color = NemuriTheme.colors.cyan)
				}
				if (onRetry != null) {
					TextButton(onClick = onRetry) {
						Text("Retry", color = NemuriTheme.colors.textPrimary)
					}
				}
				if (onMigrate != null) {
					TextButton(onClick = onMigrate) {
						Text("Find Alternative", color = NemuriTheme.colors.textSecondary)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSynopsisSection(
	manga: Manga,
	uiState: MangaDetailsUiState,
	descriptionExpanded: Boolean,
	onToggleExpanded: () -> Unit,
	onToggleTranslation: () -> Unit,
	onOpenSourceBrowse: (String, String?) -> Unit,
) {
	val isUltra = uiState.detailsUiMode == DetailsUiMode.ULTRA_COMPACT
	val activeSynopsis = if (uiState.showTranslatedSynopsis && !uiState.translatedSynopsis.isNullOrBlank()) {
		uiState.translatedSynopsis!!
	} else {
		uiState.descriptionText
	}
	val tagShape = NemuriTheme.shape(6.dp)
	val pillShape = NemuriTheme.pillShape()

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.animateContentSize(nemuriSpatialSpring())
			.padding(horizontal = 16.dp, vertical = if (isUltra) 3.dp else 6.dp),
		verticalArrangement = Arrangement.spacedBy(if (isUltra) 6.dp else 8.dp),
	) {
		if (isUltra && !descriptionExpanded) {
			val cardShape = NemuriTheme.shape(10.dp)
			val previewText = activeSynopsis.takeIf { it.isNotBlank() }
				?: "Synopsis & ${manga.tags.size} tags"
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = cardShape,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = DETAILS_COMPACT_CONTROL_HEIGHT)
					.clip(cardShape)
					.clickable(onClick = onToggleExpanded),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = previewText,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textSecondary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f),
					)
					if (manga.tags.isNotEmpty()) {
						Surface(
							color = NemuriTheme.colors.card,
							shape = pillShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
						) {
							Text(
								text = "${manga.tags.size} tags",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
							)
						}
					}
					Icon(
						imageVector = Icons.Default.ExpandMore,
						contentDescription = "Expand synopsis and tags",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(18.dp),
					)
				}
			}
		} else {
			if (uiState.descriptionText.isNotBlank()) {
				val isTranslatedView = uiState.showTranslatedSynopsis && !uiState.translatedSynopsis.isNullOrBlank()
				val translateShape = NemuriTheme.shape(6.dp)
				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "Description",
							fontSize = 13.sp,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.onSurface,
						)
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Surface(
								color = if (isTranslatedView) {
									MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
								} else {
									Color.Transparent
								},
								shape = translateShape,
								border = BorderStroke(
									width = 1.dp,
									color = if (isTranslatedView) {
										MaterialTheme.colorScheme.primary
									} else {
										MaterialTheme.colorScheme.outlineVariant
									},
								),
								modifier = Modifier
									.height(24.dp)
									.clip(translateShape)
									.clickable(enabled = !uiState.isTranslatingSynopsis, onClick = onToggleTranslation),
							) {
								Row(
									modifier = Modifier
										.fillMaxHeight()
										.padding(horizontal = 6.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									if (uiState.isTranslatingSynopsis) {
										CircularProgressIndicator(
											color = MaterialTheme.colorScheme.primary,
											strokeWidth = 1.6.dp,
											modifier = Modifier.size(11.dp),
										)
									}
									Text(
										text = if (isTranslatedView) "文→A" else "G→文",
										fontSize = 11.5.sp,
										fontWeight = FontWeight.SemiBold,
										color = if (isTranslatedView) {
											MaterialTheme.colorScheme.primary
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
										maxLines = 1,
									)
								}
							}
							Text(
								text = if (descriptionExpanded) "Less ▴" else "More ▾",
								fontSize = 12.5.sp,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.primary,
								modifier = Modifier
									.clip(NemuriTheme.shape(4.dp))
									.clickable(onClick = onToggleExpanded)
									.padding(horizontal = 2.dp, vertical = 2.dp),
							)
						}
					}
					Text(
						text = activeSynopsis,
						fontSize = 13.sp,
						lineHeight = 18.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier
							.fillMaxWidth()
							.clip(NemuriTheme.shape(8.dp))
							.clickable(onClick = onToggleExpanded),
					)
				}
			}

			if (manga.tags.isNotEmpty()) {
				val renderTagChip: @Composable (MangaTag) -> Unit = { tag ->
					Surface(
						color = MaterialTheme.colorScheme.surfaceContainer,
						shape = tagShape,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
						modifier = Modifier
							.height(26.dp)
							.clip(tagShape)
							.clickable(enabled = !uiState.isLocalOnly) {
								onOpenSourceBrowse(manga.source.name, tag.title)
							},
					) {
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier
								.fillMaxHeight()
								.padding(horizontal = 9.dp),
						) {
							Text(
								text = tag.title,
								fontSize = 11.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
							)
						}
					}
				}

				if (descriptionExpanded) {
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						manga.tags.forEach { tag -> renderTagChip(tag) }
					}
				} else {
					LazyRow(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						items(manga.tags.toList(), key = { it.key }) { tag ->
							renderTagChip(tag)
						}
					}
				}
			}

			if (isUltra && descriptionExpanded && uiState.descriptionText.isBlank() && manga.tags.isNotEmpty()) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End,
				) {
					Text(
						text = "Less ▴",
						fontSize = 12.5.sp,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier
							.clip(NemuriTheme.shape(4.dp))
							.clickable(onClick = onToggleExpanded)
							.padding(horizontal = 4.dp, vertical = 2.dp),
					)
				}
			}
		}
	}
}

internal data class DetailsScrollTarget(
	val index: Int,
	val scrollOffset: Int,
)

internal fun resolveDetailsModeSwitchScrollTarget(
	anchorKey: Any?,
	anchorOffset: Int,
	targetMode: DetailsUiMode,
	hasInlineBanner: Boolean,
	hasSynopsisOrTags: Boolean,
	hasAiRecap: Boolean,
	hasBranches: Boolean,
	chapterIds: List<Long>,
	hasRelatedManga: Boolean,
): DetailsScrollTarget {
	if (anchorKey == null || anchorKey == "details_hero" || anchorKey == "details_actions") {
		return DetailsScrollTarget(index = 0, scrollOffset = 0)
	}
	val clampedOffset = anchorOffset.coerceAtLeast(0)
	var idx = 1
	if (hasInlineBanner) {
		if (anchorKey == "details_cf_banner" || anchorKey == "details_interactive_banner" || anchorKey == "details_err_banner") {
			return DetailsScrollTarget(idx, clampedOffset)
		}
		idx++
	}
	if (hasSynopsisOrTags) {
		if (anchorKey == "details_synopsis") return DetailsScrollTarget(idx, clampedOffset)
		idx++
	}
	if (hasAiRecap) {
		if (anchorKey == "details_ai_recap") return DetailsScrollTarget(idx, clampedOffset)
		idx++
	}
	if (hasBranches) {
		if (anchorKey == "details_branches") return DetailsScrollTarget(idx, clampedOffset)
		idx++
	}
	if (anchorKey == "details_chapters_header") {
		return DetailsScrollTarget(idx, clampedOffset)
	}
	idx++
	if (chapterIds.isEmpty()) {
		if (anchorKey == "details_chapters_empty") return DetailsScrollTarget(idx, clampedOffset)
		idx++
	} else {
		if (anchorKey is Long) {
			val chIdx = chapterIds.indexOf(anchorKey)
			if (chIdx >= 0) return DetailsScrollTarget(idx + chIdx, clampedOffset)
		}
		idx += chapterIds.size
	}
	if (hasRelatedManga && anchorKey == "details_related") {
		return DetailsScrollTarget(idx, clampedOffset)
	}
	return DetailsScrollTarget(index = 0, scrollOffset = 0)
}

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
	var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
	var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
	return remember(this) {
		derivedStateOf {
			if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
				true
			} else if (previousIndex != firstVisibleItemIndex) {
				previousIndex > firstVisibleItemIndex
			} else {
				previousScrollOffset >= firstVisibleItemScrollOffset
			}.also {
				previousIndex = firstVisibleItemIndex
				previousScrollOffset = firstVisibleItemScrollOffset
			}
		}
	}.value
}

private fun previewMangaDetailsSample(mode: DetailsUiMode): Pair<Manga, MangaDetailsUiState> {
	val sampleChapters = listOf(
		MangaChapter(
			id = 20L,
			title = "Vol.3 Ch.20: Rain at the Station After the Long Autumn Festival Fireworks",
			number = 20f,
			volume = 3,
			url = "/ch/20",
			scanlator = "Sleepy Scans",
			uploadDate = 1_760_000_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
		MangaChapter(
			id = 19L,
			title = "Vol.3 Ch.19: Unspoken Promise",
			number = 19f,
			volume = 3,
			url = "/ch/19",
			scanlator = "Sleepy Scans",
			uploadDate = 1_759_300_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
		MangaChapter(
			id = 18L,
			title = "Vol.3 Ch.18: Infirmary Confession",
			number = 18f,
			volume = 3,
			url = "/ch/18",
			scanlator = "Sleepy Scans",
			uploadDate = 1_758_600_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
		MangaChapter(
			id = 17L,
			title = "Vol.3 Ch.17: After School Rain",
			number = 17f,
			volume = 3,
			url = "/ch/17",
			scanlator = "Sleepy Scans",
			uploadDate = 1_757_900_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
		MangaChapter(
			id = 16L,
			title = "Vol.2 Ch.16: Rooftop Lunchtime Strategy",
			number = 16f,
			volume = 2,
			url = "/ch/16",
			scanlator = "Sleepy Scans",
			uploadDate = 1_757_200_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
		MangaChapter(
			id = 15L,
			title = "Vol.2 Ch.15: Notes Passed Across the Hallway",
			number = 15f,
			volume = 2,
			url = "/ch/15",
			scanlator = "Sleepy Scans",
			uploadDate = 1_756_500_000_000L,
			branch = null,
			source = LocalMangaSource,
		),
	)
	val sampleTags = setOf(
		MangaTag(title = "Drama", key = "drama", source = LocalMangaSource),
		MangaTag(title = "Slice of Life", key = "sol", source = LocalMangaSource),
		MangaTag(title = "Psychological", key = "psych", source = LocalMangaSource),
		MangaTag(title = "School Life", key = "school", source = LocalMangaSource),
		MangaTag(title = "Romance", key = "romance", source = LocalMangaSource),
		MangaTag(title = "Coming of Age", key = "coming_of_age", source = LocalMangaSource),
		MangaTag(title = "Award Winning", key = "award_winning", source = LocalMangaSource),
	)
	val sampleManga = Manga(
		id = 42_001L,
		title = "Asahina Wakaba & Her Chubby-Cheeked Boyfriend: The Complete After-School Chronicles & Deluxe Epilogue Edition",
		altTitles = setOf("Asahina Wakaba to Marumaru na Kareshi • 朝比奈若葉と○○な彼氏"),
		url = "/title/42001",
		publicUrl = "https://example.invalid/title/42001",
		rating = 0.87f,
		contentRating = null,
		coverUrl = null,
		tags = sampleTags,
		state = MangaState.ONGOING,
		authors = setOf("Haruaki Kurosawa", "Hikaru Sakurai"),
		largeCoverUrl = null,
		description = "Tired of playing someone else's role just to keep the peace in her classroom, Wakaba finds an unexpected confidant in Haruto after an impulsive dare leads to an honest conversation behind the gymnasium. What begins as a clumsy fake-dating pact slowly untangles years of unspoken anxiety, quiet bullying, and the courage it takes to choose sincerity over appearances.",
		chapters = sampleChapters,
		source = LocalMangaSource,
	)
	val sampleRows = listOf(
		ChapterRowItem(
			chapter = sampleChapters[0],
			title = "Ch. 20 — Rain at the Station After the Long Autumn Festival Fireworks",
			subtitle = "Sleepy Scans • 2 days ago",
			isDownloaded = false,
			isCurrent = false,
			isRead = false,
			isNew = true,
			isBookmarked = false,
		),
		ChapterRowItem(
			chapter = sampleChapters[1],
			title = "Ch. 19 — Unspoken Promise",
			subtitle = "Sleepy Scans • 5 days ago",
			isDownloaded = true,
			isCurrent = false,
			isRead = false,
			isNew = true,
			isBookmarked = false,
		),
		ChapterRowItem(
			chapter = sampleChapters[2],
			title = "Ch. 18 — Infirmary Confession",
			subtitle = "Sleepy Scans • Reading p. 6",
			isDownloaded = true,
			isCurrent = true,
			isRead = false,
			isNew = false,
			isBookmarked = true,
		),
		ChapterRowItem(
			chapter = sampleChapters[3],
			title = "Ch. 17 — After School Rain",
			subtitle = "Sleepy Scans • Completed",
			isDownloaded = true,
			isCurrent = false,
			isRead = true,
			isNew = false,
			isBookmarked = false,
		),
		ChapterRowItem(
			chapter = sampleChapters[4],
			title = "Ch. 16 — Rooftop Lunchtime Strategy",
			subtitle = "Sleepy Scans • Completed",
			isDownloaded = true,
			isCurrent = false,
			isRead = true,
			isNew = false,
			isBookmarked = false,
		),
		ChapterRowItem(
			chapter = sampleChapters[5],
			title = "Ch. 15 — Notes Passed Across the Hallway",
			subtitle = "Sleepy Scans • Completed",
			isDownloaded = false,
			isCurrent = false,
			isRead = true,
			isNew = false,
			isBookmarked = false,
		),
	)
	val state = MangaDetailsUiState(
		mangaId = sampleManga.id,
		manga = sampleManga,
		title = sampleManga.title,
		altTitle = "Asahina Wakaba to Marumaru na Kareshi • 朝比奈若葉と○○な彼氏",
		sourceTitle = "MangaDex",
		authorsLabel = "Haruaki Kurosawa, Hikaru Sakurai",
		statusLabel = "Ongoing",
		formatBadge = "Manga",
		ratingLabel = "★ 8.7",
		descriptionText = sampleManga.description.orEmpty(),
		publicUrl = sampleManga.publicUrl,
		isLocalOnly = false,
		isInLibrary = true,
		memberCategoryIds = setOf(1L),
		history = MangaHistory(
			createdAt = Instant.EPOCH,
			updatedAt = Instant.EPOCH,
			chapterId = 18L,
			page = 6,
			scroll = 0,
			percent = 0.43f,
			chaptersCount = 42,
		),
		progressPercent = 0.43f,
		primaryActionLabel = "Continue Ch. 18",
		primaryChapterId = 18L,
		primaryChapterShortLabel = "Ch. 18",
		currentChapterPosition = 18,
		totalBranchChapters = 42,
		unreadChaptersCount = 24,
		downloadedCount = 6,
		newChaptersCount = 2,
		chapters = sampleRows,
		isLoading = false,
		aiRecap = MangaAiRecapEntity(
			mangaId = sampleManga.id,
			lastChapterId = 18L,
			chaptersReadCount = 18,
			summaryText = "Wakaba confided in Haruto after the infirmary confrontation, shifting their faux-dating arrangement into genuine trust. Paused at Ch. 18.",
			engineUsed = "Gemini Nano • Brief",
			isAiGenerated = true,
			updatedAt = 0L,
		),
		recapEngineBadgeLabel = "Gemini Nano",
		recapDepth = RecapDepth.BRIEF,
		detailsUiMode = mode,
	)
	return sampleManga to state
}

@Composable
private fun MangaDetailsPreviewCanvas(
	initialMode: DetailsUiMode,
	modifier: Modifier = Modifier,
) {
	val (manga, baseState) = remember(initialMode) { previewMangaDetailsSample(initialMode) }
	var uiState by remember(initialMode) { mutableStateOf(baseState) }
	var descriptionExpanded by remember(initialMode) { mutableStateOf(false) }
	val listState = rememberLazyListState()
	val chapterIds = remember(uiState.chapters) { uiState.chapters.map { it.chapter.id } }

	var pendingAnchor by remember { mutableStateOf<Pair<Any?, Int>?>(null) }
	var lastMode by remember(initialMode) { mutableStateOf(uiState.detailsUiMode) }
	LaunchedEffect(uiState.detailsUiMode) {
		if (uiState.detailsUiMode != lastMode) {
			val (anchorKey, anchorOffset) = pendingAnchor ?: run {
				val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
				firstVisible?.key to listState.firstVisibleItemScrollOffset
			}
			pendingAnchor = null
			val target = resolveDetailsModeSwitchScrollTarget(
				anchorKey = anchorKey,
				anchorOffset = anchorOffset,
				targetMode = uiState.detailsUiMode,
				hasInlineBanner = false,
				hasSynopsisOrTags = true,
				hasAiRecap = true,
				hasBranches = false,
				chapterIds = chapterIds,
				hasRelatedManga = false,
			)
			listState.scrollToItem(target.index, target.scrollOffset)
			lastMode = uiState.detailsUiMode
		}
	}

	val toolbar: @Composable () -> Unit = {
		DetailsTopToolbar(
			uiState = uiState,
			onBack = {},
			onToggleIncognito = { uiState = uiState.copy(isIncognitoActive = !uiState.isIncognitoActive) },
			onRefresh = {},
			onOpenBrowser = {},
			onOpenDownloads = {},
			onOpenStats = {},
			onOpenMigrate = {},
			onOpenDisplayOptions = {
				val nextMode = when (uiState.detailsUiMode) {
					DetailsUiMode.EXPRESSIVE -> DetailsUiMode.COMPACT
					DetailsUiMode.COMPACT -> DetailsUiMode.ULTRA_COMPACT
					DetailsUiMode.ULTRA_COMPACT -> DetailsUiMode.EXPRESSIVE
				}
				val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
				pendingAnchor = (firstVisible?.key to listState.firstVisibleItemScrollOffset)
				uiState = uiState.copy(detailsUiMode = nextMode)
			},
		)
	}

	val isContinueFabExpanded = listState.isScrollingUp()
	val chapterShortLabel = uiState.primaryChapterShortLabel?.takeIf { it.isNotBlank() } ?: "Ch. 18"
	val continueFabLabel = if (uiState.isIncognitoActive) {
		"Incognito $chapterShortLabel"
	} else {
		"Continue $chapterShortLabel"
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background),
	) {
		LazyColumn(
			state = listState,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(bottom = 88.dp),
		) {
			item(key = "details_hero") {
				when (uiState.detailsUiMode) {
					DetailsUiMode.EXPRESSIVE -> DetailsExpressiveHeader(
						manga = manga,
						uiState = uiState,
						sharedCoverKey = "preview-centered",
						toolbar = toolbar,
						onToggleLibrary = { uiState = uiState.copy(isInLibrary = !uiState.isInLibrary) },
						onOpenCategories = {},
						onOpenMigrate = {},
						onOpenSourceBrowse = {},
					)

					DetailsUiMode.COMPACT -> DetailsCompactHeader(
						manga = manga,
						uiState = uiState,
						sharedCoverKey = "preview-compact",
						toolbar = toolbar,
						onToggleLibrary = { uiState = uiState.copy(isInLibrary = !uiState.isInLibrary) },
						onOpenCategories = {},
						onOpenSourceBrowse = {},
					)

					DetailsUiMode.ULTRA_COMPACT -> DetailsUltraCompactHeader(
						manga = manga,
						uiState = uiState,
						sharedCoverKey = "preview-ultra",
						toolbar = toolbar,
						onToggleLibrary = { uiState = uiState.copy(isInLibrary = !uiState.isInLibrary) },
						onOpenCategories = {},
						onOpenSourceBrowse = {},
					)
				}
			}

			item(key = "details_synopsis") {
				DetailsSynopsisSection(
					manga = manga,
					uiState = uiState,
					descriptionExpanded = descriptionExpanded,
					onToggleExpanded = { descriptionExpanded = !descriptionExpanded },
					onToggleTranslation = {},
					onOpenSourceBrowse = { _, _ -> },
				)
			}

			item(key = "details_ai_recap") {
				WhereWasIRecapCard(
					recap = uiState.aiRecap,
					isGenerating = false,
					engineBadgeLabel = uiState.recapEngineBadgeLabel,
					recapDepth = uiState.recapDepth,
					onSelectDepth = { uiState = uiState.copy(recapDepth = it) },
					onGenerate = {},
					onOpenSettings = {},
				)
			}

			item(key = "details_chapters_header") {
				val pillShape = NemuriTheme.pillShape()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					HorizontalDivider(color = NemuriTheme.colors.border)
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "${uiState.totalBranchChapters} Chapter(s)",
								style = MaterialTheme.typography.titleMedium,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "${uiState.downloadedCount} downloaded • ${uiState.newChaptersCount} new",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.cyan,
							)
						}
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Surface(
								color = NemuriTheme.colors.tealContainer,
								shape = pillShape,
								border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
								modifier = Modifier.height(DETAILS_COMPACT_CONTROL_HEIGHT),
							) {
								Box(
									contentAlignment = Alignment.Center,
									modifier = Modifier
										.fillMaxHeight()
										.padding(horizontal = 10.dp),
								) {
									Text(
										text = "Clear New",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = NemuriTheme.colors.cyan,
										maxLines = 1,
									)
								}
							}
							IconButton(
								onClick = {},
								modifier = Modifier.size(DETAILS_COMPACT_CONTROL_HEIGHT),
							) {
								Icon(
									imageVector = Icons.Default.SwapVert,
									contentDescription = "Reverse order",
									tint = NemuriTheme.colors.textSecondary,
								)
							}
							IconButton(
								onClick = {},
								modifier = Modifier.size(DETAILS_COMPACT_CONTROL_HEIGHT),
							) {
								Icon(
									imageVector = Icons.Default.FilterList,
									contentDescription = "Sort and filter chapters",
									tint = NemuriTheme.colors.textSecondary,
								)
							}
						}
					}
				}
			}

			items(uiState.chapters, key = { it.chapter.id }) { row ->
				ChapterRowView(
					row = row,
					isSelectionMode = false,
					isSelected = false,
					showDownloadButton = true,
					onClick = {},
					onLongClick = {},
					onDownloadClick = {},
					onDeleteLocalClick = {},
				)
			}
		}

		DetailsContinueSplitButton(
			label = continueFabLabel,
			targetChapterShortLabel = chapterShortLabel,
			isIncognitoActive = uiState.isIncognitoActive,
			expanded = isContinueFabExpanded,
			canStartFromChapterOne = true,
			onPrimaryClick = {},
			onReadInIncognito = {},
			onToggleIncognitoMode = { uiState = uiState.copy(isIncognitoActive = !uiState.isIncognitoActive) },
			onStartFromChapterOne = {},
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(end = 16.dp, bottom = 16.dp),
		)
	}
}

@Preview(
	name = "Centered — Veri-Lys Light",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_NO,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFFE2EFFB,
)
@Preview(
	name = "Centered — Veri-Lys Dark",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_YES,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFF0E1624,
)
@Composable
private fun MangaDetailsCenteredPreview() {
	NemuriTheme(
		themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
		colorScheme = ColorScheme.VERI_LYS_ICY_BLUE,
	) {
		MangaDetailsPreviewCanvas(initialMode = DetailsUiMode.EXPRESSIVE)
	}
}

@Preview(
	name = "Compact — Veri-Lys Light",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_NO,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFFE2EFFB,
)
@Preview(
	name = "Compact — Veri-Lys Dark",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_YES,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFF0E1624,
)
@Composable
private fun MangaDetailsCompactPreview() {
	NemuriTheme(
		themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
		colorScheme = ColorScheme.VERI_LYS_ICY_BLUE,
	) {
		MangaDetailsPreviewCanvas(initialMode = DetailsUiMode.COMPACT)
	}
}

@Preview(
	name = "Ultra Compact — Veri-Lys Light",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_NO,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFFE2EFFB,
)
@Preview(
	name = "Ultra Compact — Veri-Lys Dark",
	group = "Veri-Lys Details Modes",
	uiMode = Configuration.UI_MODE_NIGHT_YES,
	widthDp = 412,
	heightDp = 892,
	showBackground = true,
	backgroundColor = 0xFF0E1624,
)
@Composable
private fun MangaDetailsUltraCompactPreview() {
	NemuriTheme(
		themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
		colorScheme = ColorScheme.VERI_LYS_ICY_BLUE,
	) {
		MangaDetailsPreviewCanvas(initialMode = DetailsUiMode.ULTRA_COMPACT)
	}
}

@Composable
private fun MangaDetailsThreeModeComparisonContent(colorScheme: ColorScheme) {
	NemuriTheme(
		themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
		colorScheme = colorScheme,
	) {
		val frameShape = NemuriTheme.shape(18.dp)
		val canvasBackground = if (NemuriTheme.colors.isDark) Color(0xFF080D16) else Color(0xFFD2E4F7)
		Row(
			modifier = Modifier
				.fillMaxSize()
				.background(canvasBackground)
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			listOf(
				DetailsUiMode.EXPRESSIVE to "Centered",
				DetailsUiMode.COMPACT to "Compact",
				DetailsUiMode.ULTRA_COMPACT to "Ultra Compact",
			).forEach { (mode, label) ->
				Column(
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight(),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Surface(
						color = NemuriTheme.colors.tealContainer,
						shape = NemuriTheme.pillShape(),
						border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.55f)),
					) {
						Text(
							text = label.uppercase(),
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
						)
					}
					Surface(
						color = NemuriTheme.colors.background,
						shape = frameShape,
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
						modifier = Modifier
							.weight(1f)
							.fillMaxWidth()
							.clip(frameShape),
					) {
						MangaDetailsPreviewCanvas(initialMode = mode)
					}
				}
			}
		}
	}
}

@Preview(
	name = "Icy Blue 3-Mode —  Light",
	group = "Side-by-Side Comparison",
	uiMode = Configuration.UI_MODE_NIGHT_NO,
	widthDp = 1280,
	heightDp = 900,
	showBackground = true,
	backgroundColor = 0xFFD2E4F7,
)
@Preview(
	name = "Icy Blue 3-Mode —  Dark",
	group = "Side-by-Side Comparison",
	uiMode = Configuration.UI_MODE_NIGHT_YES,
	widthDp = 1280,
	heightDp = 900,
	showBackground = true,
	backgroundColor = 0xFF080D16,
)
@Composable
private fun MangaDetailsVeriLysIcyBlueComparisonPreview() {
	MangaDetailsThreeModeComparisonContent(colorScheme = ColorScheme.VERI_LYS_ICY_BLUE)
}

@Preview(
	name = "Wisteria Mist 3-Mode — Light",
	group = "Side-by-Side Comparison",
	uiMode = Configuration.UI_MODE_NIGHT_NO,
	widthDp = 1280,
	heightDp = 900,
	showBackground = true,
	backgroundColor = 0xFFD2E4F7,
)
@Preview(
	name = "Wisteria Mist 3-Mode — Dark",
	group = "Side-by-Side Comparison",
	uiMode = Configuration.UI_MODE_NIGHT_YES,
	widthDp = 1280,
	heightDp = 900,
	showBackground = true,
	backgroundColor = 0xFF080D16,
)
@Composable
private fun MangaDetailsVeriLysWisteriaComparisonPreview() {
	MangaDetailsThreeModeComparisonContent(colorScheme = ColorScheme.VERI_LYS_WISTERIA_MIST)
}
