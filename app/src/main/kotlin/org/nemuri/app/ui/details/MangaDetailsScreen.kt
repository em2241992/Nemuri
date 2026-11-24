package org.nemuri.app.ui.details

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.ChaptersSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriErrorState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.theme.NemuriTheme

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
	var showStatsSheet by remember { mutableStateOf(false) }
	var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
	var newCategoryName by rememberSaveable { mutableStateOf("") }
	var chapterMenuTarget by remember { mutableStateOf<ChapterRowItem?>(null) }

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
						actionLabel = "Verify in Browser",
						onAction = { cfLauncher.launch(cf) },
						onRetry = { viewModel.retry(force = true) },
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
						message = uiState.errorMessage ?: "Complete login or prompt in the browser, then retry.",
						actionLabel = "Open Browser",
						onAction = { browserLauncher.launch(interactive) },
						onRetry = { viewModel.retry(force = true) },
					)
				}
			}

			uiState.manga == null -> {
				Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
					Row(modifier = Modifier.padding(6.dp)) {
						IconButton(onClick = onBack) {
							Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NemuriTheme.colors.textPrimary)
						}
					}
					NemuriErrorState(
						message = uiState.errorMessage ?: "Title unavailable.",
						onRetry = { viewModel.retry(force = true) },
					)
				}
			}

			else -> {
				val manga = uiState.manga!!
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(bottom = if (isSelectionMode) 110.dp else 44.dp),
				) {
					// Top Hero Backdrop + Poster Header
					item(key = "details_hero") {
						Box(modifier = Modifier.fillMaxWidth()) {
							MangaCoverImage(
								manga = manga,
								coverUrl = uiState.backdropUrl,
								contentScale = ContentScale.Crop,
								modifier = Modifier
									.fillMaxWidth()
									.height(250.dp)
									.alpha(0.32f),
							)
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(250.dp)
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
								// Top toolbar
								Row(
									modifier = Modifier
										.fillMaxWidth()
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
									Spacer(modifier = Modifier.weight(1f))
									if (uiState.isRefreshing) {
										CircularProgressIndicator(
											color = NemuriTheme.colors.cyan,
											strokeWidth = 2.dp,
											modifier = Modifier
												.padding(end = 8.dp)
												.size(18.dp),
										)
									} else {
										IconButton(onClick = { viewModel.retry(force = true) }) {
											Icon(
												imageVector = Icons.Default.Refresh,
												contentDescription = "Refresh",
												tint = NemuriTheme.colors.textSecondary,
											)
										}
									}
									if (!uiState.isLocalOnly && uiState.publicUrl.isNotBlank()) {
										IconButton(
											onClick = {
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
										) {
											Icon(
												imageVector = Icons.Default.OpenInBrowser,
												contentDescription = "Open in WebView",
												tint = NemuriTheme.colors.textSecondary,
											)
										}
									}
									if (!uiState.isLocalOnly && uiState.totalBranchChapters > 0) {
										IconButton(onClick = { showDownloadSheet = true }) {
											Icon(
												imageVector = Icons.Default.Download,
												contentDescription = "Download options",
												tint = NemuriTheme.colors.cyan,
											)
										}
									}
									IconButton(
										onClick = {
											viewModel.loadMangaStats()
											showStatsSheet = true
										},
									) {
										Icon(
											imageVector = Icons.Default.BarChart,
											contentDescription = "Reading stats",
											tint = NemuriTheme.colors.textSecondary,
										)
									}
									if (!uiState.isLocalOnly) {
										IconButton(onClick = { onOpenMigrate(uiState.mangaId) }) {
											Icon(
												imageVector = Icons.Default.SwapHoriz,
												contentDescription = "Migrate source",
												tint = NemuriTheme.colors.textSecondary,
											)
										}
									}
								}

								// Poster + Meta
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
											.size(width = 114.dp, height = 166.dp)
											.clip(RoundedCornerShape(14.dp))
											.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
									)

									Column(
										modifier = Modifier.weight(1f),
										verticalArrangement = Arrangement.spacedBy(5.dp),
									) {
										Row(
											horizontalArrangement = Arrangement.spacedBy(6.dp),
											verticalAlignment = Alignment.CenterVertically,
										) {
											if (uiState.formatBadge != null) {
												Surface(
													color = NemuriTheme.colors.tealContainer,
													shape = RoundedCornerShape(6.dp),
												) {
													NonWrappingBadgeText(
														text = uiState.formatBadge!!,
														color = NemuriTheme.colors.cyan,
														modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													)
												}
											}
											if (uiState.statusLabel.isNotBlank()) {
												Surface(
													color = NemuriTheme.colors.surfaceElevated,
													shape = RoundedCornerShape(6.dp),
												) {
													NonWrappingBadgeText(
														text = uiState.statusLabel,
														color = NemuriTheme.colors.textSecondary,
														modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
													)
												}
											}
											if (uiState.ratingLabel != null) {
												Text(
													text = uiState.ratingLabel!!,
													style = MaterialTheme.typography.labelSmall,
													color = NemuriTheme.colors.amber,
												)
											}
										}

										Text(
											text = uiState.title,
											style = MaterialTheme.typography.titleLarge,
											color = NemuriTheme.colors.textPrimary,
											maxLines = 3,
											overflow = TextOverflow.Ellipsis,
										)
										if (uiState.altTitle != null) {
											Text(
												text = uiState.altTitle!!,
												style = MaterialTheme.typography.bodySmall,
												color = NemuriTheme.colors.textMuted,
												maxLines = 2,
												overflow = TextOverflow.Ellipsis,
											)
										}
										if (uiState.authorsLabel.isNotBlank()) {
											Text(
												text = uiState.authorsLabel,
												style = MaterialTheme.typography.bodySmall,
												color = NemuriTheme.colors.textSecondary,
												maxLines = 2,
												overflow = TextOverflow.Ellipsis,
											)
										}
										if (uiState.sourceTitle.isNotBlank()) {
											Surface(
												color = NemuriTheme.colors.card,
												shape = RoundedCornerShape(8.dp),
												modifier = Modifier
													.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(8.dp))
													.clip(RoundedCornerShape(8.dp))
													.clickable(enabled = !uiState.isLocalOnly) {
														onOpenSourceBrowse(manga.source.name, null)
													},
											) {
												Text(
													text = uiState.sourceTitle,
													style = MaterialTheme.typography.labelSmall,
													color = NemuriTheme.colors.cyan,
													modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
												)
											}
										}
									}
								}
							}
						}
					}

					// Primary CTA + Library Actions
					item(key = "details_actions") {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 10.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Button(
									onClick = {
										MangaTransferCache.put(manga)
										onStartReader(manga, uiState.primaryChapterId, uiState.selectedBranch)
									},
									enabled = uiState.primaryChapterId != null,
									shape = RoundedCornerShape(14.dp),
									colors = ButtonDefaults.buttonColors(
										containerColor = NemuriTheme.colors.cyan,
										contentColor = NemuriTheme.colors.onCyan,
									),
									modifier = Modifier
										.weight(1f)
										.height(48.dp),
								) {
									Icon(
										imageVector = Icons.Default.PlayArrow,
										contentDescription = null,
										modifier = Modifier.size(18.dp),
									)
									Spacer(modifier = Modifier.width(6.dp))
									Text(
										text = uiState.primaryActionLabel,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}

								Surface(
									color = if (uiState.isInLibrary) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
									shape = RoundedCornerShape(14.dp),
									modifier = Modifier
										.height(48.dp)
										.border(
											1.dp,
											if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
											RoundedCornerShape(14.dp),
										)
										.clip(RoundedCornerShape(14.dp))
										.combinedClickable(
											onClick = viewModel::quickToggleLibrary,
											onLongClick = { showCategorySheet = true },
										),
								) {
									Row(
										modifier = Modifier.padding(horizontal = 14.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
									) {
										Icon(
											imageVector = if (uiState.isInLibrary) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
											contentDescription = "Library",
											tint = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
											modifier = Modifier.size(18.dp),
										)
										val catCount = uiState.memberCategoryIds.size
										Text(
											text = if (catCount > 1) "Saved ($catCount)" else if (uiState.isInLibrary) "In Library" else "Library",
											style = MaterialTheme.typography.labelLarge,
											color = if (uiState.isInLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
										)
									}
								}

								Surface(
									color = NemuriTheme.colors.card,
									shape = RoundedCornerShape(14.dp),
									modifier = Modifier
										.size(48.dp)
										.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
										.clip(RoundedCornerShape(14.dp))
										.clickable { showCategorySheet = true },
								) {
									Box(contentAlignment = Alignment.Center) {
										Icon(
											imageVector = Icons.Default.FolderOpen,
											contentDescription = "Categories",
											tint = NemuriTheme.colors.textSecondary,
											modifier = Modifier.size(20.dp),
										)
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
										.clip(RoundedCornerShape(2.dp)),
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
								message = uiState.errorMessage ?: "Verify in WebView to refresh chapters.",
								actionLabel = "Verify",
								onAction = { cfLauncher.launch(cf) },
							)
						}
					} else if (uiState.pendingInteractive != null) {
						val interactive = uiState.pendingInteractive!!
						item(key = "details_interactive_banner") {
							DetailsInlineChallengeBanner(
								title = "Sign-in required",
								message = uiState.errorMessage ?: "Open browser to sign in and refresh.",
								actionLabel = "Open Browser",
								onAction = { browserLauncher.launch(interactive) },
							)
						}
					} else if (uiState.errorMessage != null) {
						item(key = "details_err_banner") {
							DetailsInlineChallengeBanner(
								title = "Refresh failed",
								message = uiState.errorMessage!!,
								actionLabel = "Retry",
								onAction = { viewModel.retry(force = true) },
							)
						}
					}

					// Description & Tags
					if (uiState.descriptionText.isNotBlank() || manga.tags.isNotEmpty()) {
						item(key = "details_synopsis") {
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 6.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								if (uiState.descriptionText.isNotBlank()) {
									Column(
										modifier = Modifier
											.fillMaxWidth()
											.clip(RoundedCornerShape(12.dp))
											.clickable { descriptionExpanded = !descriptionExpanded },
									) {
										Text(
											text = uiState.descriptionText,
											style = MaterialTheme.typography.bodyMedium,
											color = NemuriTheme.colors.textSecondary,
											maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
											overflow = TextOverflow.Ellipsis,
										)
										Text(
											text = if (descriptionExpanded) "Show less" else "Read more",
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.cyan,
											modifier = Modifier.padding(top = 4.dp),
										)
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
								aiModeLabel = uiState.aiModeLabel,
								onGenerate = { viewModel.generateRecap(forceRefresh = uiState.aiRecap != null) },
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
								OutlinedTextField(
									value = uiState.chapterQuery,
									onValueChange = viewModel::setChapterQuery,
									placeholder = { Text("Filter chapters…") },
									leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
									trailingIcon = {
										if (uiState.chapterQuery.isNotEmpty()) {
											IconButton(onClick = { viewModel.setChapterQuery("") }) {
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
							ChapterRowView(
								row = row,
								isSelectionMode = isSelectionMode,
								isSelected = isSelected,
								showDownloadButton = !uiState.isLocalOnly,
								onClick = {
									if (isSelectionMode) {
										viewModel.toggleChapterSelection(row.chapter.id)
									} else {
										MangaTransferCache.put(manga)
										onStartReader(manga, row.chapter.id, uiState.selectedBranch)
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
					Row(verticalAlignment = Alignment.CenterVertically) {
						IconButton(onClick = viewModel::selectAllChapters) {
							Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = NemuriTheme.colors.textSecondary)
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

	uiState.pendingDuplicatePrompt?.let { prompt ->
		NemuriModalSheet(
			title = "Possible Duplicate in Library",
			subtitle = "Choose whether to replace an existing entry or add anyway",
			onDismiss = viewModel::dismissDuplicatePrompt,
		) {
			Column(
				modifier = Modifier
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					text = "Adding: ${prompt.incoming.title} (${prompt.incoming.source.getTitle(context)}" +
						(prompt.incomingChaptersCount?.let { " • $it ch" } ?: "") + ")",
					style = MaterialTheme.typography.labelLarge,
					color = NemuriTheme.colors.cyan,
				)
				prompt.duplicates.forEach { dup ->
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier
							.fillMaxWidth()
							.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
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
								Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
									Text(
										text = dup.manga.title,
										style = MaterialTheme.typography.titleSmall,
										color = NemuriTheme.colors.textPrimary,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "${dup.manga.source.getTitle(context)} • ${dup.chaptersCount} ch",
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.textMuted,
									)
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
									Text(
										if (prompt.migrateProgressOnReplace) {
											"Replace This Entry (Carry Progress)"
										} else {
											"Replace This Entry"
										},
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
						modifier = Modifier.weight(1f),
					) {
						Text("Skip", color = NemuriTheme.colors.textMuted)
					}
					Button(
						onClick = viewModel::addDespiteDuplicates,
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.surfaceElevated,
							contentColor = NemuriTheme.colors.textPrimary,
						),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.weight(1f),
					) {
						Text("Add Anyway")
					}
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

@Composable
private fun WhereWasIRecapCard(
	recap: org.koitharu.kotatsu.ai.data.MangaAiRecapEntity?,
	isGenerating: Boolean,
	aiModeLabel: String,
	onGenerate: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp)
			.border(1.dp, NemuriTheme.colors.mergePurple.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
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
						shape = RoundedCornerShape(999.dp),
					) {
						Text(
							text = recap?.engineUsed ?: aiModeLabel,
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
					text = "Spoiler-free recap up to your current read chapter. Never passes unread chapters.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				Button(
					onClick = onGenerate,
					enabled = !isGenerating,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.tealContainer,
						contentColor = NemuriTheme.colors.cyan,
					),
					shape = RoundedCornerShape(10.dp),
				) {
					if (isGenerating) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.cyan,
							strokeWidth = 2.dp,
							modifier = Modifier.size(14.dp),
						)
						Spacer(Modifier.width(6.dp))
						Text("Generating…")
					} else {
						Icon(
							imageVector = Icons.Default.Refresh,
							contentDescription = null,
							modifier = Modifier.size(15.dp),
						)
						Spacer(Modifier.width(5.dp))
						Text(if (recap == null) "Generate Recap" else "Refresh Recap")
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
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Button(
				onClick = onAction,
				colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.cyan, contentColor = NemuriTheme.colors.onCyan),
			) {
				Text(actionLabel)
			}
			TextButton(onClick = onRetry) {
				Text("Retry", color = NemuriTheme.colors.textSecondary)
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
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 6.dp)
			.border(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
	) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text(title, style = MaterialTheme.typography.titleSmall, color = NemuriTheme.colors.amber)
				Text(message, style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
			TextButton(onClick = onAction) {
				Text(actionLabel, color = NemuriTheme.colors.cyan)
			}
		}
	}
}
