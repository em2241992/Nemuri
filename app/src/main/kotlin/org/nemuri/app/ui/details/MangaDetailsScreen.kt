package org.nemuri.app.ui.details

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.domain.RecapDepth
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.ChaptersSortOrder
import org.koitharu.kotatsu.core.prefs.DetailsUiMode
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.favourites.domain.TitleSignatureParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import java.text.DateFormat
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
				val chapterListState = rememberLazyListState()
				val chapterScrollAdapter = rememberNemuriScrollAdapter(chapterListState)
				val chapterIds = remember(uiState.chapters) { uiState.chapters.map { it.chapter.id } }
				val detailsBottomPadding = rememberPushScreenBottomPadding(if (isSelectionMode) 98.dp else 28.dp)

				val hasInlineBanner = uiState.pendingCloudFlare != null ||
					uiState.pendingInteractive != null ||
					uiState.errorMessage != null
				val hasSynopsisOrTags = uiState.descriptionText.isNotBlank() || manga.tags.isNotEmpty()
				val hasAiRecap = uiState.isRecapCardEnabled && uiState.history != null
				val hasBranches = uiState.branches.size > 1
				val baseHeaderItems = if (uiState.detailsUiMode == DetailsUiMode.ULTRA_COMPACT) 1 else 2
				val chaptersHeaderIndex = baseHeaderItems +
					(if (hasInlineBanner) 1 else 0) +
					(if (hasSynopsisOrTags) 1 else 0) +
					(if (hasAiRecap) 1 else 0) +
					(if (hasBranches) 1 else 0)

				val showChaptersQuickJump by remember(
					chapterListState,
					chaptersHeaderIndex,
					uiState.chapters.size,
					isSelectionMode,
				) {
					derivedStateOf {
						if (uiState.chapters.isEmpty() || isSelectionMode) {
							false
						} else {
							val firstIdx = chapterListState.firstVisibleItemIndex
							val headerReached = firstIdx >= chaptersHeaderIndex ||
								(!chapterListState.canScrollForward &&
									chapterListState.layoutInfo.visibleItemsInfo.any { it.key == "details_chapters_header" })
							!headerReached
						}
					}
				}

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
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)

							DetailsUiMode.COMPACT -> DetailsCompactHeader(
								manga = manga,
								uiState = uiState,
								sharedCoverKey = coverKey,
								toolbar = toolbar,
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)

							DetailsUiMode.ULTRA_COMPACT -> DetailsUltraCompactHeader(
								manga = manga,
								uiState = uiState,
								sharedCoverKey = coverKey,
								toolbar = toolbar,
								onStartReading = { requestOpenReader(manga, uiState.primaryChapterId) },
								onToggleLibrary = viewModel::quickToggleLibrary,
								onOpenCategories = { showCategorySheet = true },
								onOpenSourceBrowse = { onOpenSourceBrowse(manga.source.name, null) },
							)
						}
					}

					// Primary CTA + Library Actions (EXPRESSIVE & COMPACT; ULTRA_COMPACT embeds inline)
					if (uiState.detailsUiMode != DetailsUiMode.ULTRA_COMPACT) {
						item(key = "details_actions") {
							when (uiState.detailsUiMode) {
								DetailsUiMode.EXPRESSIVE -> DetailsExpressiveActions(
									uiState = uiState,
									onStartReading = { requestOpenReader(manga, uiState.primaryChapterId) },
									onToggleLibrary = viewModel::quickToggleLibrary,
									onOpenCategories = { showCategorySheet = true },
									onOpenMigrate = { onOpenMigrate(uiState.mangaId) },
								)

								else -> DetailsCompactActions(
									uiState = uiState,
									onStartReading = { requestOpenReader(manga, uiState.primaryChapterId) },
									onStartIncognito = {
										if (uiState.primaryChapterId != null) {
											launchReaderSession(manga, uiState.primaryChapterId, incognito = true)
										} else {
											viewModel.toggleHeaderIncognito()
										}
									},
									onToggleLibrary = viewModel::quickToggleLibrary,
									onOpenCategories = { showCategorySheet = true },
									onOpenMigrate = { onOpenMigrate(uiState.mangaId) },
								)
							}
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
							val isUltra = uiState.detailsUiMode == DetailsUiMode.ULTRA_COMPACT
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = if (isUltra) 3.dp else 6.dp),
								verticalArrangement = Arrangement.spacedBy(if (isUltra) 6.dp else 10.dp),
							) {
								if (uiState.descriptionText.isNotBlank()) {
									val activeSynopsis = if (uiState.showTranslatedSynopsis && !uiState.translatedSynopsis.isNullOrBlank()) {
										uiState.translatedSynopsis!!
									} else {
										uiState.descriptionText
									}
									Column(
										modifier = Modifier
											.fillMaxWidth()
											.animateContentSize(nemuriSpatialSpring()),
										verticalArrangement = Arrangement.spacedBy(4.dp),
									) {
										Text(
											text = activeSynopsis,
											style = MaterialTheme.typography.bodyMedium,
											color = NemuriTheme.colors.textSecondary,
											maxLines = if (descriptionExpanded) Int.MAX_VALUE else if (isUltra) 2 else 4,
											overflow = TextOverflow.Ellipsis,
											modifier = Modifier
												.fillMaxWidth()
												.clip(NemuriTheme.shape(10.dp))
												.clickable { descriptionExpanded = !descriptionExpanded },
										)
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.SpaceBetween,
											verticalAlignment = Alignment.CenterVertically,
										) {
											Text(
												text = if (descriptionExpanded) "Show less" else "Read more",
												style = MaterialTheme.typography.labelSmall,
												color = NemuriTheme.colors.cyan,
												modifier = Modifier
													.clip(RoundedCornerShape(6.dp))
													.clickable { descriptionExpanded = !descriptionExpanded }
													.padding(vertical = 4.dp, horizontal = 2.dp),
											)

											val isTranslatedView = uiState.showTranslatedSynopsis && !uiState.translatedSynopsis.isNullOrBlank()
											Surface(
												color = if (isTranslatedView) {
													NemuriTheme.colors.tealContainer
												} else {
													NemuriTheme.colors.surfaceElevated
												},
												shape = RoundedCornerShape(999.dp),
												border = BorderStroke(
													width = 1.dp,
													color = if (isTranslatedView) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
												),
												modifier = Modifier
													.clip(RoundedCornerShape(999.dp))
													.clickable(enabled = !uiState.isTranslatingSynopsis) {
														viewModel.toggleSynopsisTranslation()
													},
											) {
												Row(
													modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(5.dp),
												) {
													if (uiState.isTranslatingSynopsis) {
														CircularProgressIndicator(
															color = NemuriTheme.colors.cyan,
															strokeWidth = 1.8.dp,
															modifier = Modifier.size(12.dp),
														)
														Text(
															text = "Translating…",
															style = MaterialTheme.typography.labelSmall,
															color = NemuriTheme.colors.cyan,
														)
													} else {
														Icon(
															imageVector = Icons.Default.Translate,
															contentDescription = null,
															tint = NemuriTheme.colors.cyan,
															modifier = Modifier.size(13.dp),
														)
														Text(
															text = if (isTranslatedView) "文 → A Original" else "G → 文 Translate",
															style = MaterialTheme.typography.labelSmall,
															fontWeight = FontWeight.SemiBold,
															color = if (isTranslatedView) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
														)
													}
												}
											}
										}
									}
								}

								if (manga.tags.isNotEmpty()) {
									FlowRow(
										horizontalArrangement = Arrangement.spacedBy(6.dp),
										verticalArrangement = Arrangement.spacedBy(6.dp),
									) {
										manga.tags.forEach { tag ->
											Surface(
												color = NemuriTheme.colors.surfaceElevated,
												shape = RoundedCornerShape(8.dp),
												modifier = Modifier
													.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(8.dp))
													.clip(RoundedCornerShape(8.dp))
													.clickable(enabled = !uiState.isLocalOnly) {
														onOpenSourceBrowse(manga.source.name, tag.title)
													},
											) {
												Text(
													text = tag.title,
													style = MaterialTheme.typography.labelSmall,
													color = NemuriTheme.colors.textSecondary,
													modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
												)
											}
										}
									}
								}
							}
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
										shape = RoundedCornerShape(999.dp),
										modifier = Modifier
											.border(
												1.dp,
												if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
												RoundedCornerShape(999.dp),
											)
											.clip(RoundedCornerShape(999.dp))
											.clickable { viewModel.selectBranch(option.branch) },
									) {
										Text(
											text = "${option.label} (${option.chaptersCount})",
											style = MaterialTheme.typography.labelMedium,
											color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
										)
									}
								}
							}
						}
					}

					// Chapters Header + Search
					item(key = "details_chapters_header") {
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

								if (uiState.newChaptersCount > 0) {
									TextButton(onClick = viewModel::clearNewChapterBadges) {
										Text("Clear New", style = MaterialTheme.typography.labelMedium, color = NemuriTheme.colors.cyan)
									}
								}
								IconButton(onClick = viewModel::toggleChaptersReversed) {
									Icon(
										imageVector = Icons.Default.SwapVert,
										contentDescription = "Reverse order",
										tint = if (uiState.isReversed) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									)
								}
								IconButton(onClick = { showChaptersFilterSheet = true }) {
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

					// Floating "X chapters ↓" Quick-Jump Pill
					AnimatedVisibility(
						visible = showChaptersQuickJump,
						enter = fadeIn() + slideInVertically { it / 2 },
						exit = fadeOut() + slideOutVertically { it / 2 },
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.navigationBarsPadding()
							.padding(end = 16.dp, bottom = 20.dp),
					) {
						Surface(
							color = NemuriTheme.colors.dock.copy(alpha = 0.94f),
							shape = RoundedCornerShape(999.dp),
							border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.65f)),
							shadowElevation = 10.dp,
							modifier = Modifier
								.clip(RoundedCornerShape(999.dp))
								.clickable {
									coroutineScope.launch {
										chapterListState.animateScrollToItem(chaptersHeaderIndex)
									}
								},
						) {
							Text(
								text = "${uiState.chapters.size} chapters ↓",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold,
								color = NemuriTheme.colors.cyan,
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
							)
						}
					}

					val quickButtonsAvoidJumpPill by animateDpAsState(
						targetValue = if (showChaptersQuickJump && !layoutPrefs.isFastScrollLeftHanded) 48.dp else 0.dp,
						animationSpec = nemuriSpatialSpring(),
						label = "detailsQuickButtonsOffset",
					)
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

					NemuriListNavigationOverlay(
						adapter = chapterScrollAdapter,
						hasBottomDock = false,
						visibleOverride = !isSelectionMode,
						extraQuickButtonsBottomOffset = quickButtonsAvoidJumpPill,
						topTrackPadding = 64.dp,
						labelForIndex = chapterCalloutForIndex,
					)
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
						"Centered Large",
						"Atmospheric blurred cover, centered title & expressive progress card",
					),
					Triple(
						DetailsUiMode.COMPACT,
						"Compact",
						"Side-by-side cover, chapter summary chip & split Continue / Incognito bar",
					),
					Triple(
						DetailsUiMode.ULTRA_COMPACT,
						"Ultra Compact",
						"Minimal header strip & 2-line synopsis so chapters start immediately",
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
							.clickable { viewModel.setDetailsUiMode(mode) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsExpressiveHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val coverShape = NemuriTheme.shape(16.dp)
	val pillShape = NemuriTheme.pillShape()

	Box(modifier = Modifier.fillMaxWidth()) {
		MangaCoverImage(
			manga = manga,
			coverUrl = uiState.backdropUrl,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxWidth()
				.height(340.dp)
				.blur(18.dp)
				.alpha(0.38f),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(340.dp)
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
				shadowElevation = 14.dp,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier.size(width = 156.dp, height = 228.dp),
			) {
				MangaCoverImage(
					manga = manga,
					coverUrl = uiState.coverUrl,
					modifier = Modifier
						.fillMaxSize()
						.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 16.dp),
				)
			}

			Spacer(modifier = Modifier.height(14.dp))

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(5.dp),
			) {
				Text(
					text = uiState.title,
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.textPrimary,
					textAlign = TextAlign.Center,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
				if (uiState.altTitle != null) {
					Text(
						text = uiState.altTitle,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
						textAlign = TextAlign.Center,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (uiState.authorsLabel.isNotBlank()) {
					Text(
						text = uiState.authorsLabel,
						style = MaterialTheme.typography.bodyMedium,
						color = NemuriTheme.colors.textSecondary,
						textAlign = TextAlign.Center,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Spacer(modifier = Modifier.height(4.dp))

				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
					verticalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					if (uiState.statusLabel.isNotBlank()) {
						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = pillShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
						) {
							NonWrappingBadgeText(
								text = uiState.statusLabel,
								color = NemuriTheme.colors.textSecondary,
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
							)
						}
					}
					if (uiState.sourceTitle.isNotBlank()) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = pillShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
							modifier = Modifier
								.clip(pillShape)
								.clickable(enabled = !uiState.isLocalOnly, onClick = onOpenSourceBrowse),
						) {
							NonWrappingBadgeText(
								text = uiState.sourceTitle,
								color = NemuriTheme.colors.cyan,
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
							)
						}
					}
					if (uiState.formatBadge != null) {
						Surface(
							color = NemuriTheme.colors.secondaryContainer,
							shape = pillShape,
						) {
							NonWrappingBadgeText(
								text = uiState.formatBadge,
								color = NemuriTheme.colors.secondary,
								modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
							)
						}
					}
					if (uiState.ratingLabel != null) {
						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = pillShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
						) {
							NonWrappingBadgeText(
								text = uiState.ratingLabel,
								color = NemuriTheme.colors.amber,
								modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsExpressiveActions(
	uiState: MangaDetailsUiState,
	onStartReading: () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenMigrate: () -> Unit,
) {
	val btnShape = NemuriTheme.shape(12.dp)
	val cardShape = NemuriTheme.shape(16.dp)
	val pct = (uiState.progressPercent * 100f).roundToInt().coerceIn(0, 100)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		// Add to Library + Merge Row
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				color = if (uiState.isInLibrary) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
				shape = btnShape,
				border = BorderStroke(
					1.dp,
					if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				),
				modifier = Modifier
					.weight(1f)
					.height(44.dp)
					.clip(btnShape)
					.combinedClickable(
						onClick = onToggleLibrary,
						onLongClick = onOpenCategories,
					),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = if (uiState.isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
						contentDescription = null,
						tint = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(17.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					val catCount = uiState.memberCategoryIds.size
					Text(
						text = when {
							catCount > 1 -> "Saved ($catCount)"
							uiState.isInLibrary -> "In Library"
							else -> "Add to Library"
						},
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.SemiBold,
						color = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Surface(
				color = NemuriTheme.colors.card,
				shape = btnShape,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.weight(1f)
					.height(44.dp)
					.clip(btnShape)
					.clickable(onClick = if (uiState.isLocalOnly) onOpenCategories else onOpenMigrate),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = if (uiState.isLocalOnly) Icons.Default.FolderOpen else Icons.Default.SwapHoriz,
						contentDescription = null,
						tint = NemuriTheme.colors.secondary,
						modifier = Modifier.size(17.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = if (uiState.isLocalOnly) "Categories" else "Merge / Migrate",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.SemiBold,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			if (!uiState.isLocalOnly) {
				Surface(
					color = NemuriTheme.colors.card,
					shape = btnShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier
						.size(44.dp)
						.clip(btnShape)
						.clickable(onClick = onOpenCategories),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.FolderOpen,
							contentDescription = "Categories",
							tint = NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}
		}

		// Expressive Progress Card (Chapter X of Y • Z%) + Continue CTA
		Surface(
			color = NemuriTheme.colors.card,
			shape = cardShape,
			border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.35f)),
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier.padding(14.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Column(
						modifier = Modifier.weight(1f),
						verticalArrangement = Arrangement.spacedBy(2.dp),
					) {
						val progressHeadline = if (uiState.currentChapterPosition > 0 && uiState.totalBranchChapters > 0) {
							"Chapter ${uiState.currentChapterPosition} of ${uiState.totalBranchChapters} • $pct%"
						} else if (uiState.totalBranchChapters > 0) {
							"0 of ${uiState.totalBranchChapters} Chapters • 0%"
						} else {
							"No chapters loaded"
						}
						Text(
							text = progressHeadline,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.textPrimary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						val detailMeta = buildList {
							if (uiState.totalBranchChapters > 0) add("${uiState.unreadChaptersCount} unread")
							if (uiState.downloadedCount > 0) add("${uiState.downloadedCount} offline")
						}.joinToString(" • ")
						if (detailMeta.isNotEmpty()) {
							Text(
								text = detailMeta,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
					}

					Button(
						onClick = onStartReading,
						enabled = uiState.primaryChapterId != null,
						shape = btnShape,
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.cyan,
							contentColor = NemuriTheme.colors.onCyan,
						),
						modifier = Modifier.height(40.dp),
					) {
						Icon(
							imageVector = Icons.Default.PlayArrow,
							contentDescription = null,
							modifier = Modifier.size(17.dp),
						)
						Spacer(modifier = Modifier.width(4.dp))
						Text(
							text = if (uiState.history != null) {
								uiState.primaryChapterShortLabel ?: "Continue"
							} else {
								"Start"
							},
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
						)
					}
				}

				LinearProgressIndicator(
					progress = { uiState.progressPercent.coerceIn(0f, 1f) },
					color = NemuriTheme.colors.cyan,
					trackColor = NemuriTheme.colors.border,
					modifier = Modifier
						.fillMaxWidth()
						.height(5.dp)
						.clip(NemuriTheme.shape(3.dp)),
				)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCompactHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val coverShape = NemuriTheme.shape(12.dp)
	val chipShape = NemuriTheme.shape(8.dp)
	val pct = (uiState.progressPercent * 100f).roundToInt().coerceIn(0, 100)

	Box(modifier = Modifier.fillMaxWidth()) {
		MangaCoverImage(
			manga = manga,
			coverUrl = uiState.backdropUrl,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxWidth()
				.height(230.dp)
				.alpha(0.28f),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(230.dp)
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
				horizontalArrangement = Arrangement.spacedBy(14.dp),
				verticalAlignment = Alignment.Top,
			) {
				MangaCoverImage(
					manga = manga,
					coverUrl = uiState.coverUrl,
					modifier = Modifier
						.size(width = 108.dp, height = 158.dp)
						.clip(coverShape)
						.border(1.dp, NemuriTheme.colors.border, coverShape)
						.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 12.dp),
				)

				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Text(
						text = uiState.title,
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 3,
						overflow = TextOverflow.Ellipsis,
					)
					if (uiState.authorsLabel.isNotBlank()) {
						Text(
							text = uiState.authorsLabel,
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}

					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalArrangement = Arrangement.spacedBy(5.dp),
					) {
						if (uiState.sourceTitle.isNotBlank()) {
							Surface(
								color = NemuriTheme.colors.card,
								shape = chipShape,
								border = BorderStroke(1.dp, NemuriTheme.colors.border),
								modifier = Modifier
									.clip(chipShape)
									.clickable(enabled = !uiState.isLocalOnly, onClick = onOpenSourceBrowse),
							) {
								val prefix = if (uiState.isLocalOnly) "" else "EXT "
								NonWrappingBadgeText(
									text = "$prefix${uiState.sourceTitle}",
									color = NemuriTheme.colors.cyan,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
						}
						if (uiState.statusLabel.isNotBlank()) {
							Surface(
								color = NemuriTheme.colors.surfaceElevated,
								shape = chipShape,
								border = BorderStroke(1.dp, NemuriTheme.colors.border),
							) {
								NonWrappingBadgeText(
									text = uiState.statusLabel,
									color = NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
						}
						if (uiState.formatBadge != null) {
							Surface(
								color = NemuriTheme.colors.secondaryContainer,
								shape = chipShape,
							) {
								NonWrappingBadgeText(
									text = uiState.formatBadge,
									color = NemuriTheme.colors.secondary,
									modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
								)
							}
						}
						if (uiState.ratingLabel != null) {
							Text(
								text = uiState.ratingLabel,
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.amber,
								modifier = Modifier.padding(top = 2.dp),
							)
						}
					}

					if (uiState.totalBranchChapters > 0) {
						Surface(
							color = NemuriTheme.colors.tealContainer.copy(alpha = 0.72f),
							shape = chipShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.35f)),
						) {
							Text(
								text = "${uiState.totalBranchChapters} Chapters • ${uiState.unreadChaptersCount} Unread • $pct% Complete",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.SemiBold,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsCompactActions(
	uiState: MangaDetailsUiState,
	onStartReading: () -> Unit,
	onStartIncognito: () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenMigrate: () -> Unit,
) {
	val barShape = NemuriTheme.shape(14.dp)
	val btnShape = NemuriTheme.shape(12.dp)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// Full-width split "▶ Continue Ch. X | Incognito" bar
		Surface(
			shape = barShape,
			border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.55f)),
			color = NemuriTheme.colors.surfaceElevated,
			modifier = Modifier
				.fillMaxWidth()
				.height(48.dp)
				.clip(barShape),
		) {
			Row(
				modifier = Modifier.fillMaxSize(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight()
						.background(
							if (uiState.primaryChapterId != null) {
								NemuriTheme.colors.cyan
							} else {
								NemuriTheme.colors.surfaceElevated
							},
						)
						.clickable(enabled = uiState.primaryChapterId != null, onClick = onStartReading)
						.padding(horizontal = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					val fg = if (uiState.primaryChapterId != null) NemuriTheme.colors.onCyan else NemuriTheme.colors.textDim
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = null,
						tint = fg,
						modifier = Modifier.size(18.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					val splitLabel = when {
						uiState.primaryChapterId == null -> "No Chapters"
						uiState.history != null -> "Continue ${uiState.primaryChapterShortLabel ?: ""}".trim()
						else -> "Start Reading"
					}
					Text(
						text = splitLabel,
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = fg,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Box(
					modifier = Modifier
						.width(1.dp)
						.fillMaxHeight()
						.background(NemuriTheme.colors.border),
				)

				Row(
					modifier = Modifier
						.fillMaxHeight()
						.background(
							if (uiState.isIncognitoActive) {
								NemuriTheme.colors.tealContainer
							} else {
								NemuriTheme.colors.surfaceElevated
							},
						)
						.clickable(onClick = onStartIncognito)
						.padding(horizontal = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Icon(
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = "Read in Incognito",
						tint = if (uiState.isIncognitoActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(16.dp),
					)
					Text(
						text = "Incognito",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = if (uiState.isIncognitoActive) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
						maxLines = 1,
					)
				}
			}
		}

		// Merge / Migrate | + Add to Library Row
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				color = NemuriTheme.colors.card,
				shape = btnShape,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier
					.weight(1f)
					.height(42.dp)
					.clip(btnShape)
					.clickable(onClick = if (uiState.isLocalOnly) onOpenCategories else onOpenMigrate),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = if (uiState.isLocalOnly) Icons.Default.FolderOpen else Icons.Default.SwapHoriz,
						contentDescription = null,
						tint = NemuriTheme.colors.secondary,
						modifier = Modifier.size(16.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = if (uiState.isLocalOnly) "Categories" else "Merge / Migrate",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Surface(
				color = if (uiState.isInLibrary) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
				shape = btnShape,
				border = BorderStroke(
					1.dp,
					if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				),
				modifier = Modifier
					.weight(1f)
					.height(42.dp)
					.clip(btnShape)
					.combinedClickable(
						onClick = onToggleLibrary,
						onLongClick = onOpenCategories,
					),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = if (uiState.isInLibrary) Icons.Default.Favorite else Icons.Default.Add,
						contentDescription = null,
						tint = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(16.dp),
					)
					Spacer(modifier = Modifier.width(5.dp))
					val catCount = uiState.memberCategoryIds.size
					Text(
						text = when {
							catCount > 1 -> "Saved ($catCount)"
							uiState.isInLibrary -> "In Library"
							else -> "Add to Library"
						},
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			if (!uiState.isLocalOnly) {
				Surface(
					color = NemuriTheme.colors.card,
					shape = btnShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier
						.size(42.dp)
						.clip(btnShape)
						.clickable(onClick = onOpenCategories),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.FolderOpen,
							contentDescription = "Categories",
							tint = NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}
		}

		if (uiState.progressPercent > 0f) {
			LinearProgressIndicator(
				progress = { uiState.progressPercent.coerceIn(0f, 1f) },
				color = NemuriTheme.colors.cyan,
				trackColor = NemuriTheme.colors.border,
				modifier = Modifier
					.fillMaxWidth()
					.height(4.dp)
					.clip(NemuriTheme.shape(2.dp)),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsUltraCompactHeader(
	manga: Manga,
	uiState: MangaDetailsUiState,
	sharedCoverKey: String,
	toolbar: @Composable () -> Unit,
	onStartReading: () -> Unit,
	onToggleLibrary: () -> Unit,
	onOpenCategories: () -> Unit,
	onOpenSourceBrowse: () -> Unit,
) {
	val thumbShape = NemuriTheme.shape(8.dp)
	val pillShape = NemuriTheme.shape(10.dp)
	val pct = (uiState.progressPercent * 100f).roundToInt().coerceIn(0, 100)

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
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = uiState.coverUrl,
				modifier = Modifier
					.size(width = 52.dp, height = 74.dp)
					.clip(thumbShape)
					.border(1.dp, NemuriTheme.colors.border, thumbShape)
					.nemuriSharedCover(sharedCoverKey, if (NemuriTheme.isSharpTacticalGeometry) 2.dp else 8.dp),
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = uiState.title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.textPrimary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				val metaLine = buildList {
					if (uiState.sourceTitle.isNotBlank()) add(uiState.sourceTitle)
					if (uiState.statusLabel.isNotBlank()) add(uiState.statusLabel)
					if (uiState.totalBranchChapters > 0) add("${uiState.totalBranchChapters} ch")
					if (pct > 0) add("$pct%")
				}.joinToString(" • ")
				if (metaLine.isNotBlank()) {
					Text(
						text = metaLine,
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.clickable(enabled = !uiState.isLocalOnly, onClick = onOpenSourceBrowse),
					)
				}
				if (uiState.authorsLabel.isNotBlank()) {
					Text(
						text = uiState.authorsLabel,
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textMuted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			// Inline Continue pill + Library icon button
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Surface(
					color = if (uiState.primaryChapterId != null) {
						NemuriTheme.colors.cyan
					} else {
						NemuriTheme.colors.surfaceElevated
					},
					shape = pillShape,
					modifier = Modifier
						.height(36.dp)
						.clip(pillShape)
						.clickable(enabled = uiState.primaryChapterId != null, onClick = onStartReading),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(3.dp),
					) {
						val fg = if (uiState.primaryChapterId != null) NemuriTheme.colors.onCyan else NemuriTheme.colors.textDim
						Icon(
							imageVector = Icons.Default.PlayArrow,
							contentDescription = "Continue reading",
							tint = fg,
							modifier = Modifier.size(16.dp),
						)
						Text(
							text = if (uiState.history != null) {
								uiState.primaryChapterShortLabel ?: "Go"
							} else {
								"Read"
							},
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
							color = fg,
							maxLines = 1,
						)
					}
				}

				Surface(
					color = if (uiState.isInLibrary) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
					shape = pillShape,
					border = BorderStroke(
						1.dp,
						if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
					),
					modifier = Modifier
						.size(36.dp)
						.clip(pillShape)
						.combinedClickable(
							onClick = onToggleLibrary,
							onLongClick = onOpenCategories,
						),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = if (uiState.isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
							contentDescription = if (uiState.isInLibrary) "In library" else "Add to library",
							tint = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(17.dp),
						)
					}
				}
			}
		}

		if (uiState.progressPercent > 0f) {
			LinearProgressIndicator(
				progress = { uiState.progressPercent.coerceIn(0f, 1f) },
				color = NemuriTheme.colors.cyan,
				trackColor = NemuriTheme.colors.border,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp)
					.height(3.dp)
					.clip(NemuriTheme.shape(2.dp)),
			)
		}
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
					modifier = Modifier.height(36.dp),
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
									modifier = Modifier.padding(horizontal = 11.dp),
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
						.height(36.dp)
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
