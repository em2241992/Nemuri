package org.nemuri.app.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.CoverAspectRatio
import org.koitharu.kotatsu.core.prefs.FeedScreenMode
import org.koitharu.kotatsu.core.prefs.GridGutterDensity
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.core.prefs.QuickScrollButtonsMode
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.browser.CloudFlareActivity
import org.nemuri.app.browser.NemuriBrowserActivity
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.CornerSafeCoverProgressBar
import org.nemuri.app.ui.components.LayoutStudioPrefs
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.MangaComfortableGridCard
import org.nemuri.app.ui.components.MangaCompactGridCard
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriListNavigationOverlay
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NemuriSuggestionsOptInCard
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.components.NemuriUpdateBanner
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.SnappingSwipeRow
import org.nemuri.app.ui.components.cornerSafeBadgeInset
import org.nemuri.app.ui.components.cornerSafeBottomPadding
import org.nemuri.app.ui.components.cornerSafeHorizontalPadding
import org.nemuri.app.ui.components.pinchToStepColumns
import org.nemuri.app.ui.components.rememberAppSettings
import org.nemuri.app.ui.components.rememberNemuriScrollAdapter
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriSharedCover
import org.nemuri.app.ui.theme.yorhaTacticalBrackets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onOpenSettings: () -> Unit,
	onResumeReading: (Manga, MangaHistory) -> Unit = { manga, history ->
		onOpenChapter(manga, history.chapterId)
	},
	onOpenSourceBrowse: (String) -> Unit = {},
	onOpenExplore: () -> Unit = {},
	viewModel: FeedViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val layoutPrefs = LocalLayoutStudioPrefs.current
	val appSettings = rememberAppSettings()
	val config = LocalConfiguration.current
	val isLandscape = config.screenWidthDp > config.screenHeightDp
	val libraryCols = layoutPrefs.columnsForOrientation(isLandscape)
	val feedGridColumns = layoutPrefs.feedColumnsForOrientation(isLandscape)

	val baseBottomPadding = rememberRootTabBottomPadding()
	val actionPillBottomPadding = rememberRootTabBottomPadding(extraBottom = 64.dp)

	var showClearDialog by remember { mutableStateOf(false) }
	var showHomeSourcePicker by remember { mutableStateOf(false) }
	var showFeedDisplaySheet by remember { mutableStateOf(false) }

	val cfLauncher = rememberLauncherForActivityResult(CloudFlareActivity.Contract()) { solved ->
		if (solved) viewModel.refreshHomeSource()
	}
	val browserLauncher = rememberLauncherForActivityResult(NemuriBrowserActivity.Contract()) { solved ->
		if (solved) viewModel.refreshHomeSource()
	}

	val searchPlaceholder = when (uiState.mode) {
		FeedScreenMode.HUB -> "Search hub, library & updates…"
		FeedScreenMode.UPDATES -> "Search chapter updates…"
		FeedScreenMode.GALLERY -> "Filter visual drop stream…"
		FeedScreenMode.FOR_YOU -> "Filter recommendations…"
	}

	val activeTuneCount = (if (layoutPrefs.feedGridColumnsOverride in 2..6) 1 else 0) +
		(if (uiState.mode == FeedScreenMode.FOR_YOU && uiState.selectedGenre != null) 1 else 0)

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = uiState.query,
			onQueryChange = viewModel::setQuery,
			placeholder = searchPlaceholder,
			activeFilterCount = activeTuneCount,
			onTuneClick = { showFeedDisplaySheet = true },
			onSettingsClick = onOpenSettings,
		)

		uiState.availableUpdate?.let { update ->
			NemuriUpdateBanner(
				version = update,
				isDownloading = uiState.isDownloadingUpdate,
				downloadProgress = uiState.updateDownloadProgress,
				onInstall = viewModel::installAvailableUpdate,
				onDismiss = viewModel::dismissAvailableUpdate,
			)
		}

		// Row 1: 4 Equal-Width Persisted Mode Tabs (Hub | Updates | Gallery | For You)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			FeedModePill(
				label = "Hub",
				badgeCount = 0,
				selected = uiState.mode == FeedScreenMode.HUB,
				onClick = { viewModel.setMode(FeedScreenMode.HUB) },
				modifier = Modifier.weight(1f),
			)
			FeedModePill(
				label = "Updates",
				badgeCount = uiState.unreadCount,
				selected = uiState.mode == FeedScreenMode.UPDATES,
				onClick = { viewModel.setMode(FeedScreenMode.UPDATES) },
				modifier = Modifier.weight(1f),
			)
			FeedModePill(
				label = "Gallery",
				badgeCount = 0,
				selected = uiState.mode == FeedScreenMode.GALLERY,
				onClick = { viewModel.setMode(FeedScreenMode.GALLERY) },
				modifier = Modifier.weight(1f),
			)
			FeedModePill(
				label = "For You",
				badgeCount = 0,
				selected = uiState.mode == FeedScreenMode.FOR_YOU,
				onClick = { viewModel.setMode(FeedScreenMode.FOR_YOU) },
				modifier = Modifier.weight(1f),
			)
		}

		// Row 2: Decluttered Full-Width Secondary Filter Chips (All | Unread | Downloaded | Not in Library)
		LazyRow(
			modifier = Modifier
				.fillMaxWidth()
				.clipToBounds()
				.padding(vertical = 2.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
			contentPadding = PaddingValues(horizontal = 14.dp),
		) {
			items(FeedSecondaryFilter.entries, key = { it.name }) { filter ->
				val selected = uiState.secondaryFilter == filter
				val chipLabel = if (filter == FeedSecondaryFilter.UNREAD && uiState.unreadCount > 0) {
					"Unread (${uiState.unreadCount})"
				} else {
					filter.label
				}
				FeedFilterChip(
					label = chipLabel,
					selected = selected,
					onClick = { viewModel.setSecondaryFilter(filter) },
				)
			}
		}

		when {
			!uiState.isLoaded -> {
				NemuriLoadingState(message = "Loading reading feed…")
			}

			uiState.mode == FeedScreenMode.HUB -> {
				PullToRefreshBox(
					isRefreshing = uiState.isTrackerRunning,
					onRefresh = viewModel::checkForUpdatesNow,
					modifier = Modifier.fillMaxSize(),
				) {
					FeedHubView(
						uiState = uiState,
						cornerRadiusDp = layoutPrefs.cardCornerRadiusDp,
						aspectRatio = layoutPrefs.coverAspectRatio.ratio,
						swipeEnabled = layoutPrefs.isSwipeActionsEnabled,
						bottomPadding = actionPillBottomPadding,
						onOpenDetails = onOpenDetails,
						onOpenChapter = onOpenChapter,
						onResumeReading = onResumeReading,
						onOpenSourceBrowse = onOpenSourceBrowse,
						onOpenExplore = onOpenExplore,
						onPickHomeSource = { showHomeSourcePicker = true },
						onRefreshHomeSource = viewModel::refreshHomeSource,
						onSolveChallenge = {
							val home = uiState.homeSource
							val cf = uiState.homeSourceCloudFlare
							val interactive = uiState.homeSourceInteractive
							val browserUrl = uiState.homeSourceBrowserUrl ?: home?.homeUrl
							when {
								cf != null -> cfLauncher.launch(cf)
								interactive != null -> browserLauncher.launch(interactive)
								home != null && !browserUrl.isNullOrBlank() -> runCatching {
									context.startActivity(
										AppRouter.browserIntent(context, browserUrl, home.sourceInfo.mangaSource, home.title),
									)
								}
							}
						},
						onSeeAllUpdates = { viewModel.setMode(FeedScreenMode.UPDATES) },
						onSeeAllForYou = { viewModel.setMode(FeedScreenMode.FOR_YOU) },
						onCheckUpdatesNow = viewModel::checkForUpdatesNow,
						onMarkAllRead = viewModel::markAllRead,
						onClearReadEntries = viewModel::clearReadFeedEntries,
						onOpenClearAllDialog = { showClearDialog = true },
						onDownloadEntry = viewModel::downloadUpdates,
						onMarkOrDismissEntry = { entry ->
							if (entry.logItem.isNew) {
								viewModel.markMangaRead(entry.logItem.manga.id)
							} else {
								viewModel.removeLogEntry(entry.logId)
							}
						},
						onEnableSuggestions = viewModel::enablePersonalizedSuggestions,
						onDismissSuggestions = viewModel::dismissSuggestionsPrompt,
					)
				}
			}

			uiState.mode == FeedScreenMode.UPDATES -> {
				PullToRefreshBox(
					isRefreshing = uiState.isTrackerRunning,
					onRefresh = viewModel::checkForUpdatesNow,
					modifier = Modifier.fillMaxSize(),
				) {
					FeedUpdatesListView(
						uiState = uiState,
						swipeEnabled = layoutPrefs.isSwipeActionsEnabled,
						bottomPadding = actionPillBottomPadding,
						onOpenDetails = onOpenDetails,
						onOpenChapter = onOpenChapter,
						onCheckNow = viewModel::checkForUpdatesNow,
						onMarkAllRead = viewModel::markAllRead,
						onClearReadEntries = viewModel::clearReadFeedEntries,
						onOpenClearAllDialog = { showClearDialog = true },
						onDownload = viewModel::downloadUpdates,
						onMarkOrDismiss = { entry ->
							if (entry.logItem.isNew) {
								viewModel.markMangaRead(entry.logItem.manga.id)
							} else {
								viewModel.removeLogEntry(entry.logId)
							}
						},
					)
				}
			}

			uiState.mode == FeedScreenMode.GALLERY -> {
				PullToRefreshBox(
					isRefreshing = uiState.isTrackerRunning,
					onRefresh = viewModel::checkForUpdatesNow,
					modifier = Modifier.fillMaxSize(),
				) {
					FeedGalleryStreamView(
						uiState = uiState,
						layoutPrefs = layoutPrefs,
						gridColumns = feedGridColumns,
						bottomPadding = baseBottomPadding,
						onStepColumns = { nextCols -> appSettings.feedGridColumnsOverride = nextCols },
						onOpenDetails = onOpenDetails,
						onOpenChapter = onOpenChapter,
						onCheckNow = viewModel::checkForUpdatesNow,
					)
				}
			}

			uiState.mode == FeedScreenMode.FOR_YOU -> {
				PullToRefreshBox(
					isRefreshing = uiState.isDiscoveryLoading,
					onRefresh = { viewModel.refreshDiscovery(force = true) },
					modifier = Modifier.fillMaxSize(),
				) {
					FeedForYouView(
						uiState = uiState,
						layoutPrefs = layoutPrefs,
						gridColumns = feedGridColumns,
						bottomPadding = baseBottomPadding,
						onStepColumns = { nextCols -> appSettings.feedGridColumnsOverride = nextCols },
						onSelectGenre = viewModel::setSelectedGenre,
						onOpenDetails = onOpenDetails,
						onOpenExplore = onOpenExplore,
						onRefresh = { viewModel.refreshDiscovery(force = true) },
						onEnableSuggestions = viewModel::enablePersonalizedSuggestions,
						onDismissSuggestions = viewModel::dismissSuggestionsPrompt,
					)
				}
			}
		}
	}

	if (showFeedDisplaySheet) {
		val isMatchingLibrary = layoutPrefs.feedGridColumnsOverride !in 2..6
		NemuriModalSheet(
			title = "Feed Grid & Display",
			subtitle = if (isMatchingLibrary) {
				"Synced with Library ($libraryCols cols)"
			} else {
				"Feed override: ${layoutPrefs.feedGridColumnsOverride} cols (Library: $libraryCols cols)"
			},
			onDismiss = { showFeedDisplaySheet = false },
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = "GALLERY & FOR YOU GRID DENSITY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)

				Surface(
					color = if (isMatchingLibrary) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					border = BorderStroke(
						1.dp,
						if (isMatchingLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
					),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable { appSettings.feedGridColumnsOverride = 0 },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Match Library ($libraryCols cols)",
								style = MaterialTheme.typography.titleSmall,
								color = if (isMatchingLibrary) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "Automatically follows portrait & landscape Library column count",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						if (isMatchingLibrary) {
							Icon(
								imageVector = Icons.Default.Check,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					(2..6).forEach { cols ->
						val selected = layoutPrefs.feedGridColumnsOverride == cols
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
								.clickable { appSettings.feedGridColumnsOverride = cols },
						) {
							Box(
								contentAlignment = Alignment.Center,
								modifier = Modifier.padding(vertical = 10.dp),
							) {
								Text(
									text = "$cols col",
									style = MaterialTheme.typography.labelMedium,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									maxLines = 1,
									softWrap = false,
								)
							}
						}
					}
				}

				Text(
					text = "COVER ASPECT RATIO (SHARED WITH LIBRARY)",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CoverAspectRatio.entries.forEach { ratio ->
						val selected = layoutPrefs.coverAspectRatio == ratio
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(999.dp),
							border = BorderStroke(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(999.dp))
								.clickable { appSettings.coverAspectRatio = ratio },
						) {
							Box(
								contentAlignment = Alignment.Center,
								modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
							) {
								Text(
									text = ratio.label,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
					}
				}

				Text(
					text = "GUTTER DENSITY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					GridGutterDensity.entries.forEach { density ->
						val selected = layoutPrefs.gutterDensity == density
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(999.dp),
							border = BorderStroke(
								1.dp,
								if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(999.dp))
								.clickable { appSettings.gridGutterDensity = density },
						) {
							Box(
								contentAlignment = Alignment.Center,
								modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
							) {
								Text(
									text = density.label,
									style = MaterialTheme.typography.labelSmall,
									fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									maxLines = 1,
								)
							}
						}
					}
				}

				val quickButtonsEnabled = layoutPrefs.quickScrollButtonsMode != QuickScrollButtonsMode.OFF
				HorizontalDivider(color = NemuriTheme.colors.border)
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(NemuriTheme.shape(10.dp))
						.clickable {
							appSettings.quickScrollButtonsMode = if (quickButtonsEnabled) {
								QuickScrollButtonsMode.OFF
							} else {
								QuickScrollButtonsMode.LONG_LISTS_ONLY
							}
						}
						.padding(vertical = 2.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
						Text(
							text = "Floating ▲ / ▼ Scroll Buttons",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Medium,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Show quick jump-to-top / bottom capsule on long lists",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					Switch(
						checked = quickButtonsEnabled,
						onCheckedChange = { enabled ->
							appSettings.quickScrollButtonsMode = if (enabled) {
								QuickScrollButtonsMode.LONG_LISTS_ONLY
							} else {
								QuickScrollButtonsMode.OFF
							}
						},
						colors = SwitchDefaults.colors(
							checkedThumbColor = NemuriTheme.colors.onCyan,
							checkedTrackColor = NemuriTheme.colors.cyan,
						),
					)
				}

				if (uiState.enabledSources.isNotEmpty() || uiState.totalCount > 0) {
					HorizontalDivider(color = NemuriTheme.colors.border)
				}

				if (uiState.enabledSources.isNotEmpty()) {
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								showFeedDisplaySheet = false
								showHomeSourcePicker = true
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(Icons.Default.PushPin, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(18.dp))
							Text(
								text = "Pin Hub Home Source (${uiState.homeSource?.title ?: "Auto"})",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}

				if (uiState.totalCount > 0 || uiState.readFeedCount > 0) {
					Surface(
						color = NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						border = BorderStroke(1.dp, NemuriTheme.colors.danger.copy(alpha = 0.45f)),
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(12.dp))
							.clickable {
								showFeedDisplaySheet = false
								showClearDialog = true
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = NemuriTheme.colors.danger, modifier = Modifier.size(18.dp))
							Text(
								text = "Clear All Feed History…",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.danger,
							)
						}
					}
				}
			}
		}
	}

	if (showHomeSourcePicker) {
		var pickerQuery by remember { mutableStateOf("") }
		val filteredSources = uiState.enabledSources.filter {
			pickerQuery.isBlank() || it.title.contains(pickerQuery.trim(), ignoreCase = true)
		}
		NemuriModalSheet(
			title = "Pin Hub Home Source",
			subtitle = "Choose which source powers Popular & Latest Updates on your Feed Hub",
			onDismiss = { showHomeSourcePicker = false },
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = pickerQuery,
					onValueChange = { pickerQuery = it },
					placeholder = { Text("Search enabled sources…") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
				LazyColumn(
					modifier = Modifier
						.fillMaxWidth()
						.height(320.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					items(filteredSources, key = { "hub_pick:${it.sourceName}" }) { opt ->
						val isSelected = opt.sourceName == uiState.homeSource?.sourceName
						Surface(
							color = if (isSelected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.fillMaxWidth()
								.border(
									1.dp,
									if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									RoundedCornerShape(12.dp),
								)
								.clip(RoundedCornerShape(12.dp))
								.clickable {
									viewModel.setHomeSource(opt.sourceName)
									showHomeSourcePicker = false
								},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = opt.title,
										style = MaterialTheme.typography.titleSmall,
										color = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									if (!opt.summary.isNullOrBlank()) {
										Text(
											text = opt.summary,
											style = MaterialTheme.typography.labelSmall,
											color = NemuriTheme.colors.textMuted,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
								}
								if (isSelected) {
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
				}
			}
		}
	}

	if (showClearDialog) {
		AlertDialog(
			onDismissRequest = { showClearDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			title = { Text("Clear updates feed", color = NemuriTheme.colors.textPrimary) },
			text = {
				Text(
					"Remove feed entries? Clearing read entries keeps unread drops intact, while Clear All empties the entire log.",
					color = NemuriTheme.colors.textSecondary,
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.clearFeedHistory()
						showClearDialog = false
					},
					colors = ButtonDefaults.textButtonColors(contentColor = NemuriTheme.colors.danger),
				) {
					Text("Clear All")
				}
			},
			dismissButton = {
				Row {
					if (uiState.readFeedCount > 0) {
						TextButton(
							onClick = {
								viewModel.clearReadFeedEntries()
								showClearDialog = false
							},
						) {
							Text("Read Only (${uiState.readFeedCount})", color = NemuriTheme.colors.cyan)
						}
					}
					TextButton(onClick = { showClearDialog = false }) {
						Text("Cancel", color = NemuriTheme.colors.textSecondary)
					}
				}
			},
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedThumbActionPill(
	isTrackerRunning: Boolean,
	unreadCount: Int,
	readFeedCount: Int,
	showClearWhenAllUnread: Boolean,
	onCheckNow: () -> Unit,
	onMarkAllRead: () -> Unit,
	onClearRead: () -> Unit,
	onOpenClearAllDialog: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val hasClearAction = readFeedCount > 0 || showClearWhenAllUnread
	val capsuleShape = NemuriTheme.pillShape()
	Surface(
		color = NemuriTheme.colors.dock.copy(alpha = 0.95f),
		shape = capsuleShape,
		shadowElevation = 10.dp,
		border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
		modifier = modifier.clip(capsuleShape),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(2.dp),
		) {
			// 1. Check / Syncing
			Row(
				modifier = Modifier
					.clip(capsuleShape)
					.clickable(onClick = onCheckNow)
					.padding(horizontal = 10.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(5.dp),
			) {
				if (isTrackerRunning) {
					CircularProgressIndicator(
						color = NemuriTheme.colors.cyan,
						strokeWidth = 2.dp,
						modifier = Modifier.size(13.dp),
					)
				} else {
					Icon(
						imageVector = Icons.Default.Refresh,
						contentDescription = "Check updates",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(15.dp),
					)
				}
				Text(
					text = if (isTrackerRunning) "Syncing" else "Check",
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					color = if (isTrackerRunning) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
					maxLines = 1,
					softWrap = false,
				)
			}

			// 2. ✓✓ Mark All Read (when unreadCount > 0)
			if (unreadCount > 0) {
				Box(
					modifier = Modifier
						.width(1.dp)
						.height(16.dp)
						.background(NemuriTheme.colors.border),
				)
				Row(
					modifier = Modifier
						.clip(capsuleShape)
						.clickable(onClick = onMarkAllRead)
						.padding(horizontal = 9.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Icon(
						imageVector = Icons.Default.DoneAll,
						contentDescription = "Mark all read",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(15.dp),
					)
					Text(
						text = "✓✓",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.ExtraBold,
						color = NemuriTheme.colors.cyan,
						maxLines = 1,
						softWrap = false,
					)
				}
			}

			// 3. Clear Read (or Clear dialog fallback)
			if (hasClearAction) {
				Box(
					modifier = Modifier
						.width(1.dp)
						.height(16.dp)
						.background(NemuriTheme.colors.border),
				)
				Row(
					modifier = Modifier
						.clip(capsuleShape)
						.combinedClickable(
							onClick = {
								if (readFeedCount > 0) onClearRead() else onOpenClearAllDialog()
							},
							onLongClick = onOpenClearAllDialog,
						)
						.padding(horizontal = 9.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Icon(
						imageVector = Icons.Default.DeleteSweep,
						contentDescription = "Clear read feed entries",
						tint = NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(15.dp),
					)
					Text(
						text = if (readFeedCount > 0) "Clear Read" else "Clear",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold,
						color = NemuriTheme.colors.textSecondary,
						maxLines = 1,
						softWrap = false,
					)
				}
			}
		}
	}
}

@Composable
private fun FeedHubView(
	uiState: FeedUiState,
	cornerRadiusDp: Int,
	aspectRatio: Float,
	swipeEnabled: Boolean,
	bottomPadding: Dp,
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onResumeReading: (Manga, MangaHistory) -> Unit,
	onOpenSourceBrowse: (String) -> Unit,
	onOpenExplore: () -> Unit,
	onPickHomeSource: () -> Unit,
	onRefreshHomeSource: () -> Unit,
	onSolveChallenge: () -> Unit,
	onSeeAllUpdates: () -> Unit,
	onSeeAllForYou: () -> Unit,
	onCheckUpdatesNow: () -> Unit,
	onMarkAllRead: () -> Unit,
	onClearReadEntries: () -> Unit,
	onOpenClearAllDialog: () -> Unit,
	onDownloadEntry: (FeedEntryItem) -> Unit,
	onMarkOrDismissEntry: (FeedEntryItem) -> Unit,
	onEnableSuggestions: () -> Unit,
	onDismissSuggestions: () -> Unit,
) {
	val hasAnyContent = uiState.showSuggestionsOptIn ||
		uiState.continueReading.isNotEmpty() ||
		uiState.hubUpdates.isNotEmpty() ||
		uiState.homeSource != null ||
		uiState.discoveryPreview.isNotEmpty() ||
		uiState.recentlyAdded.isNotEmpty()

	val listState = rememberLazyListState()
	val scrollAdapter = rememberNemuriScrollAdapter(listState)

	Box(modifier = Modifier.fillMaxSize()) {
		if (!hasAnyContent) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.verticalScroll(rememberScrollState())
					.padding(bottom = bottomPadding),
				contentAlignment = Alignment.Center,
			) {
				NemuriEmptyState(
					icon = Icons.Default.DynamicFeed,
					title = if (uiState.query.isNotBlank()) "No hub matches" else "Your Reading Hub is ready",
					subtitle = if (uiState.query.isNotBlank()) {
						"No history, updates, or source titles match \"${uiState.query}\"."
					} else {
						"Add sources in Explore or save manga to your library to populate Continue Reading, Suggested For You, and Live Source Carousels."
					},
					actionLabel = if (uiState.enabledSources.isEmpty()) "Browse Sources" else "Check Updates Now",
					onAction = if (uiState.enabledSources.isEmpty()) onOpenExplore else onCheckUpdatesNow,
				)
			}
		} else {
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
				verticalArrangement = Arrangement.spacedBy(18.dp),
			) {
				if (uiState.showSuggestionsOptIn) {
					item(key = "hub_suggestions_optin") {
						NemuriSuggestionsOptInCard(
							onEnable = onEnableSuggestions,
							onDismiss = onDismissSuggestions,
							modifier = Modifier.padding(horizontal = 16.dp),
						)
					}
				}
				// 1. Continue Reading Row
				if (uiState.continueReading.isNotEmpty()) {
					item(key = "hub_continue_header") {
						HubSectionHeader(
							title = "CONTINUE READING",
							subtitle = "Tap cover to jump back into your chapter",
						)
					}
					item(key = "hub_continue_row") {
						LazyRow(
							contentPadding = PaddingValues(horizontal = 16.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							items(uiState.continueReading, key = { "cont:${it.manga.id}" }) { item ->
								val shareKey = "feed-cont-${item.manga.id}"
								ContinueReadingCard(
									item = item,
									cornerRadiusDp = cornerRadiusDp,
									aspectRatio = aspectRatio,
									sharedCoverKey = shareKey,
									onResume = {
										MangaTransferCache.put(item.manga)
										onResumeReading(item.manga, item.history)
									},
									onOpenDetails = {
										MangaTransferCache.put(item.manga)
										onOpenDetails(item.manga, shareKey)
									},
								)
							}
						}
					}
				}

				// 2. Compact Updates Preview ("See all ->" jumps to Updates)
				if (uiState.hubUpdates.isNotEmpty()) {
					item(key = "hub_updates_header") {
						HubSectionHeader(
							title = if (uiState.unreadCount > 0) "UPDATES (${uiState.unreadCount} NEW)" else "RECENT UPDATES",
							actionLabel = "See all →",
							onAction = onSeeAllUpdates,
						)
					}
					items(uiState.hubUpdates, key = { "hub_upd:${it.logId}" }) { entry ->
						val shareKey = "feed-hub-${entry.logId}"
						Box(
							modifier = Modifier
								.animateItem()
								.padding(horizontal = 16.dp),
						) {
							if (swipeEnabled) {
								SnappingSwipeRow(
									rightLabel = if (entry.logItem.isNew) "Mark Read" else "Dismiss",
									rightIcon = Icons.Default.MarkEmailRead,
									onSwipeRight = { onMarkOrDismissEntry(entry) },
									leftLabel = "Download",
									onSwipeLeft = { onDownloadEntry(entry) },
								) {
									FeedEntryCard(
										entry = entry,
										sharedCoverKey = shareKey,
										onOpenDetails = {
											MangaTransferCache.put(entry.logItem.manga)
											onOpenDetails(entry.logItem.manga, shareKey)
										},
										onOpenLatestChapter = {
											val chId = entry.firstChapterId
											MangaTransferCache.put(entry.logItem.manga)
											if (chId != null) onOpenChapter(entry.logItem.manga, chId)
											else onOpenDetails(entry.logItem.manga, shareKey)
										},
										onDownload = { onDownloadEntry(entry) },
										onMarkRead = { onMarkOrDismissEntry(entry) },
									)
								}
							} else {
								FeedEntryCard(
									entry = entry,
									sharedCoverKey = shareKey,
									onOpenDetails = {
										MangaTransferCache.put(entry.logItem.manga)
										onOpenDetails(entry.logItem.manga, shareKey)
									},
									onOpenLatestChapter = {
										val chId = entry.firstChapterId
										MangaTransferCache.put(entry.logItem.manga)
										if (chId != null) onOpenChapter(entry.logItem.manga, chId)
										else onOpenDetails(entry.logItem.manga, shareKey)
									},
									onDownload = { onDownloadEntry(entry) },
									onMarkRead = { onMarkOrDismissEntry(entry) },
								)
							}
						}
					}
				}

				// 3. Prominent "SUGGESTED FOR YOU" Carousel ("See all ->" switches to FeedScreenMode.FOR_YOU)
				if (uiState.discoveryPreview.isNotEmpty() || (uiState.isDiscoveryLoading && uiState.enabledSources.isNotEmpty())) {
					item(key = "hub_foryou_header") {
						HubSectionHeader(
							title = "SUGGESTED FOR YOU",
							subtitle = if (uiState.discoveryGenres.isNotEmpty()) {
								"Matched to ${uiState.discoveryGenres.take(3).joinToString(", ")}"
							} else {
								"Personalized picks across your enabled sources"
							},
							actionLabel = "See all →",
							onAction = onSeeAllForYou,
						)
					}
					if (uiState.discoveryPreview.isNotEmpty()) {
						item(key = "hub_foryou_row") {
							LazyRow(
								contentPadding = PaddingValues(horizontal = 16.dp),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								items(uiState.discoveryPreview, key = { "fy_prev:${it.manga.id}" }) { rec ->
									val shareKey = "feed-fy-prev-${rec.manga.id}"
									HubDiscoveryPosterCard(
										manga = rec.manga,
										subtitle = rec.reasonLabel,
										inLibrary = rec.inLibrary,
										cornerRadiusDp = cornerRadiusDp,
										aspectRatio = aspectRatio,
										sharedCoverKey = shareKey,
										onClick = {
											MangaTransferCache.put(rec.manga)
											onOpenDetails(rec.manga, shareKey)
										},
									)
								}
							}
						}
					} else {
						item(key = "hub_foryou_loading") {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 6.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								CircularProgressIndicator(
									color = NemuriTheme.colors.cyan,
									strokeWidth = 2.dp,
									modifier = Modifier.size(14.dp),
								)
								Text(
									text = "Curating personalized suggestions…",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
						}
					}
				}

				// 4. Pinned Home Source (Popular & Latest Updates) Carousels
				if (uiState.homeSource != null) {
					val home = uiState.homeSource
					item(key = "hub_home_source_bar") {
						HubHomeSourceBar(
							homeSource = home,
							isExplicitlyPinned = uiState.isHomeSourceExplicitlyPinned,
							isLoading = uiState.isHomeSourceLoading,
							canSwitch = uiState.enabledSources.size > 1,
							onPickHomeSource = onPickHomeSource,
							onRefresh = onRefreshHomeSource,
						)
					}

					if (uiState.isHomeSourceLoading && uiState.homeCarousels.isEmpty()) {
						item(key = "hub_home_loading") {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(vertical = 12.dp),
								horizontalArrangement = Arrangement.Center,
								verticalAlignment = Alignment.CenterVertically,
							) {
								CircularProgressIndicator(
									color = NemuriTheme.colors.cyan,
									strokeWidth = 2.dp,
									modifier = Modifier.size(16.dp),
								)
								Spacer(modifier = Modifier.width(8.dp))
								Text(
									text = "Loading ${home.title} feeds…",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
						}
					} else if (!uiState.homeSourceError.isNullOrBlank() && uiState.homeCarousels.isEmpty()) {
						item(key = "hub_home_error") {
							Surface(
								color = NemuriTheme.colors.card,
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.padding(horizontal = 16.dp)
									.fillMaxWidth()
									.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
							) {
								Column(
									modifier = Modifier.padding(12.dp),
									verticalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Text(
										text = uiState.homeSourceError,
										style = MaterialTheme.typography.bodySmall,
										color = NemuriTheme.colors.textMuted,
									)
									Row(
										horizontalArrangement = Arrangement.spacedBy(8.dp),
										verticalAlignment = Alignment.CenterVertically,
									) {
										val hasChallenge = uiState.homeSourceCloudFlare != null ||
											uiState.homeSourceInteractive != null ||
											!uiState.homeSourceBrowserUrl.isNullOrBlank() ||
											!home.homeUrl.isNullOrBlank()
										if (hasChallenge) {
											Surface(
												color = NemuriTheme.colors.tealContainer,
												shape = RoundedCornerShape(8.dp),
												modifier = Modifier
													.clip(RoundedCornerShape(8.dp))
													.clickable(onClick = onSolveChallenge),
											) {
												Text(
													text = "Solve in WebView",
													style = MaterialTheme.typography.labelMedium,
													color = NemuriTheme.colors.cyan,
													modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
												)
											}
										}
										Text(
											text = "Retry",
											style = MaterialTheme.typography.labelMedium,
											color = NemuriTheme.colors.cyan,
											modifier = Modifier
												.clip(RoundedCornerShape(8.dp))
												.clickable(onClick = onRefreshHomeSource)
												.padding(horizontal = 8.dp, vertical = 5.dp),
										)
										if (uiState.enabledSources.size > 1) {
											Text(
												text = "Switch Source",
												style = MaterialTheme.typography.labelMedium,
												color = NemuriTheme.colors.textSecondary,
												modifier = Modifier
													.clip(RoundedCornerShape(8.dp))
													.clickable(onClick = onPickHomeSource)
													.padding(horizontal = 8.dp, vertical = 5.dp),
											)
										}
									}
								}
							}
						}
					} else {
						uiState.homeCarousels.forEach { carousel ->
							item(key = "hub_carousel_hdr_${carousel.id}") {
								HubSectionHeader(
									title = carousel.title.uppercase(),
									actionLabel = "Browse →",
									onAction = { onOpenSourceBrowse(home.sourceName) },
								)
							}
							item(key = "hub_carousel_row_${carousel.id}") {
								LazyRow(
									contentPadding = PaddingValues(horizontal = 16.dp),
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									items(carousel.items, key = { "${carousel.id}:${it.id}" }) { manga ->
										val inLib = manga.id in uiState.libraryMangaIds || manga.url in uiState.libraryMangaUrls
										val coverKey = "hub-${carousel.id}-${manga.id}"
										HubDiscoveryPosterCard(
											manga = manga,
											subtitle = null,
											inLibrary = inLib,
											cornerRadiusDp = cornerRadiusDp,
											aspectRatio = aspectRatio,
											sharedCoverKey = coverKey,
											onClick = {
												MangaTransferCache.put(manga)
												onOpenDetails(manga, coverKey)
											},
										)
									}
								}
							}
						}
					}
				}

				// 5. Recently Added Library Row
				if (uiState.recentlyAdded.isNotEmpty()) {
					item(key = "hub_recent_header") {
						HubSectionHeader(
							title = "RECENTLY ADDED",
							subtitle = "Latest titles saved to your library",
						)
					}
					item(key = "hub_recent_row") {
						LazyRow(
							contentPadding = PaddingValues(horizontal = 16.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							items(uiState.recentlyAdded, key = { "rec:${it.id}" }) { manga ->
								val shareKey = "feed-rec-${manga.id}"
								HubDiscoveryPosterCard(
									manga = manga,
									subtitle = null,
									inLibrary = false,
									cornerRadiusDp = cornerRadiusDp,
									aspectRatio = aspectRatio,
									sharedCoverKey = shareKey,
									onClick = {
										MangaTransferCache.put(manga)
										onOpenDetails(manga, shareKey)
									},
								)
							}
						}
					}
				}
			}
		}

		NemuriListNavigationOverlay(
			adapter = scrollAdapter,
			hasBottomDock = true,
			bottomCenterAccessory = {
				FeedThumbActionPill(
					isTrackerRunning = uiState.isTrackerRunning,
					unreadCount = uiState.unreadCount,
					readFeedCount = uiState.readFeedCount,
					showClearWhenAllUnread = false,
					onCheckNow = onCheckUpdatesNow,
					onMarkAllRead = onMarkAllRead,
					onClearRead = onClearReadEntries,
					onOpenClearAllDialog = onOpenClearAllDialog,
				)
			},
		)
	}
}

@Composable
private fun HubHomeSourceBar(
	homeSource: FeedSourceOption,
	isExplicitlyPinned: Boolean,
	isLoading: Boolean,
	canSwitch: Boolean,
	onPickHomeSource: () -> Unit,
	onRefresh: () -> Unit,
) {
	val sourcePillShape = NemuriTheme.pillShape()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Surface(
			color = if (isExplicitlyPinned) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
			shape = sourcePillShape,
			modifier = Modifier
				.weight(1f, fill = false)
				.border(
					1.dp,
					if (isExplicitlyPinned) NemuriTheme.colors.cyan.copy(alpha = 0.6f) else NemuriTheme.colors.border,
					sourcePillShape,
				)
				.clip(sourcePillShape)
				.clickable(onClick = onPickHomeSource),
		) {
			Row(
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Icon(
					imageVector = if (isExplicitlyPinned) Icons.Default.Home else Icons.Default.PushPin,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(14.dp),
				)
				Text(
					text = if (isExplicitlyPinned) {
						"Home: ${homeSource.title}"
					} else {
						"Source: ${homeSource.title} • Tap to pin"
					},
					style = MaterialTheme.typography.labelMedium,
					color = NemuriTheme.colors.cyan,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (canSwitch) {
					Icon(
						imageVector = Icons.Default.ArrowDropDown,
						contentDescription = "Choose Home Source",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(16.dp),
					)
				}
			}
		}

		IconButton(
			onClick = onRefresh,
			enabled = !isLoading,
			modifier = Modifier.size(32.dp),
		) {
			if (isLoading) {
				CircularProgressIndicator(
					color = NemuriTheme.colors.cyan,
					strokeWidth = 2.dp,
					modifier = Modifier.size(15.dp),
				)
			} else {
				Icon(
					imageVector = Icons.Default.Refresh,
					contentDescription = "Reload Home Source",
					tint = NemuriTheme.colors.textSecondary,
					modifier = Modifier.size(17.dp),
				)
			}
		}
	}
}

@Composable
private fun FeedUpdatesListView(
	uiState: FeedUiState,
	swipeEnabled: Boolean,
	bottomPadding: Dp,
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onCheckNow: () -> Unit,
	onMarkAllRead: () -> Unit,
	onClearReadEntries: () -> Unit,
	onOpenClearAllDialog: () -> Unit,
	onDownload: (FeedEntryItem) -> Unit,
	onMarkOrDismiss: (FeedEntryItem) -> Unit,
) {
	val listState = rememberLazyListState()
	val scrollAdapter = rememberNemuriScrollAdapter(listState)
	val updatesDateLabels = remember(uiState.groups) {
		buildList {
			uiState.groups.forEach { (dateLabel, entries) ->
				val shortDate = dateLabel.substringBefore(',')
				add(shortDate)
				repeat(entries.size) { add(shortDate) }
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		if (uiState.totalCount == 0) {
			val msg = when {
				uiState.query.isNotBlank() -> "No updates match \"${uiState.query}\"."
				uiState.secondaryFilter == FeedSecondaryFilter.UNREAD -> "You're caught up on all unread chapter drops."
				uiState.secondaryFilter != FeedSecondaryFilter.ALL -> "No feed entries match the ${uiState.secondaryFilter.label} filter."
				else -> "New chapters detected for tracked library titles appear here."
			}
			Box(
				modifier = Modifier
					.fillMaxSize()
					.verticalScroll(rememberScrollState())
					.padding(bottom = bottomPadding),
				contentAlignment = Alignment.Center,
			) {
				NemuriEmptyState(
					icon = Icons.Default.DynamicFeed,
					title = "No updates found",
					subtitle = msg,
					actionLabel = if (!uiState.isTrackerRunning && uiState.query.isBlank() && uiState.secondaryFilter == FeedSecondaryFilter.ALL) {
						"Check Now"
					} else {
						null
					},
					onAction = if (!uiState.isTrackerRunning && uiState.query.isBlank() && uiState.secondaryFilter == FeedSecondaryFilter.ALL) {
						onCheckNow
					} else {
						null
					},
				)
			}
		} else {
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				uiState.groups.forEach { (dateLabel, entries) ->
					item(key = "feed_header_$dateLabel") {
						Text(
							text = "$dateLabel (${entries.size})",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = NemuriTheme.colors.cyan,
							modifier = Modifier
								.padding(top = 6.dp, bottom = 2.dp)
								.yorhaTacticalBrackets()
								.padding(horizontal = 4.dp, vertical = 2.dp),
						)
					}

					items(entries, key = { it.logId }) { entry ->
						val shareKey = "feed-upd-${entry.logId}"
						Box(modifier = Modifier.animateItem()) {
							if (swipeEnabled) {
								SnappingSwipeRow(
									rightLabel = if (entry.logItem.isNew) "Mark Read" else "Dismiss",
									rightIcon = Icons.Default.MarkEmailRead,
									onSwipeRight = { onMarkOrDismiss(entry) },
									leftLabel = "Download",
									onSwipeLeft = { onDownload(entry) },
								) {
									FeedEntryCard(
										entry = entry,
										sharedCoverKey = shareKey,
										onOpenDetails = {
											MangaTransferCache.put(entry.logItem.manga)
											onOpenDetails(entry.logItem.manga, shareKey)
										},
										onOpenLatestChapter = {
											val chId = entry.firstChapterId
											MangaTransferCache.put(entry.logItem.manga)
											if (chId != null) onOpenChapter(entry.logItem.manga, chId)
											else onOpenDetails(entry.logItem.manga, shareKey)
										},
										onDownload = { onDownload(entry) },
										onMarkRead = { onMarkOrDismiss(entry) },
									)
								}
							} else {
								FeedEntryCard(
									entry = entry,
									sharedCoverKey = shareKey,
									onOpenDetails = {
										MangaTransferCache.put(entry.logItem.manga)
										onOpenDetails(entry.logItem.manga, shareKey)
									},
									onOpenLatestChapter = {
										val chId = entry.firstChapterId
										MangaTransferCache.put(entry.logItem.manga)
										if (chId != null) onOpenChapter(entry.logItem.manga, chId)
										else onOpenDetails(entry.logItem.manga, shareKey)
									},
									onDownload = { onDownload(entry) },
									onMarkRead = { onMarkOrDismiss(entry) },
								)
							}
						}
					}
				}
			}
		}

		NemuriListNavigationOverlay(
			adapter = scrollAdapter,
			hasBottomDock = true,
			labelForIndex = { idx -> updatesDateLabels.getOrNull(idx) },
			bottomCenterAccessory = {
				FeedThumbActionPill(
					isTrackerRunning = uiState.isTrackerRunning,
					unreadCount = uiState.unreadCount,
					readFeedCount = uiState.readFeedCount,
					showClearWhenAllUnread = uiState.totalCount > 0,
					onCheckNow = onCheckNow,
					onMarkAllRead = onMarkAllRead,
					onClearRead = onClearReadEntries,
					onOpenClearAllDialog = onOpenClearAllDialog,
				)
			},
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedGalleryStreamView(
	uiState: FeedUiState,
	layoutPrefs: LayoutStudioPrefs,
	gridColumns: Int,
	bottomPadding: Dp,
	onStepColumns: (Int) -> Unit,
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenChapter: (Manga, Long) -> Unit,
	onCheckNow: () -> Unit,
) {
	if (uiState.galleryEntries.isEmpty()) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState()),
			contentAlignment = Alignment.Center,
		) {
			NemuriEmptyState(
				icon = Icons.Default.DynamicFeed,
				title = "No chapter drops in Gallery",
				subtitle = if (uiState.secondaryFilter != FeedSecondaryFilter.ALL) {
					"Try switching the filter chip back to All."
				} else {
					"Visual cover cards for new chapter releases appear here automatically."
				},
				actionLabel = if (!uiState.isTrackerRunning && uiState.secondaryFilter == FeedSecondaryFilter.ALL) "Check Now" else null,
				onAction = if (!uiState.isTrackerRunning && uiState.secondaryFilter == FeedSecondaryFilter.ALL) onCheckNow else null,
			)
		}
		return
	}

	val safeCorner = layoutPrefs.cardCornerRadiusDp.coerceIn(0, 24)
	val aspectRatio = layoutPrefs.coverAspectRatio.ratio
	val outerPad = layoutPrefs.gutterDensity.outerPaddingDp.dp
	val gridGap = layoutPrefs.gutterDensity.spacingDp.dp
	val cardShape = RoundedCornerShape(safeCorner.dp)
	val badgeInset = cornerSafeBadgeInset(safeCorner)
	val hInset = cornerSafeHorizontalPadding(safeCorner)
	val bInset = cornerSafeBottomPadding(safeCorner)
	val gridState = rememberLazyGridState()
	val gridAdapter = rememberNemuriScrollAdapter(gridState)

	Box(modifier = Modifier.fillMaxSize()) {
		LazyVerticalGrid(
			state = gridState,
			columns = GridCells.Fixed(gridColumns),
			modifier = Modifier
				.fillMaxSize()
				.pinchToStepColumns(
					currentColumns = gridColumns,
					minColumns = 2,
					maxColumns = 6,
					onStepColumns = onStepColumns,
				),
			contentPadding = PaddingValues(start = outerPad, end = outerPad, top = 8.dp, bottom = bottomPadding),
			horizontalArrangement = Arrangement.spacedBy(gridGap),
			verticalArrangement = Arrangement.spacedBy(gridGap),
		) {
			items(uiState.galleryEntries, key = { "gal:${it.logId}" }) { entry ->
				val manga = entry.logItem.manga
				val isNew = entry.logItem.isNew
				val shareKey = "feed-gal-${entry.logId}"

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(aspectRatio)
						.clip(cardShape)
						.border(
							1.dp,
							if (isNew) NemuriTheme.colors.cyan.copy(alpha = 0.55f) else NemuriTheme.colors.border,
							cardShape,
						)
						.combinedClickable(
							onClick = {
								val chId = entry.firstChapterId
								MangaTransferCache.put(manga)
								if (chId != null) onOpenChapter(manga, chId)
								else onOpenDetails(manga, shareKey)
							},
							onLongClick = {
								MangaTransferCache.put(manga)
								onOpenDetails(manga, shareKey)
							},
						),
				) {
					MangaCoverImage(
						manga = manga,
						coverUrl = manga.coverUrl,
						modifier = Modifier
							.fillMaxSize()
							.nemuriSharedCover(shareKey, safeCorner.dp),
					)

					// Top-left unread dot + +N new chapters badge
					Row(
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(badgeInset),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						if (isNew) {
							Box(
								modifier = Modifier
									.size(8.dp)
									.background(NemuriTheme.colors.cyan, CircleShape)
									.border(1.dp, NemuriTheme.colors.dock, CircleShape),
							)
						}
						if (entry.newChaptersCount > 0) {
							Surface(
								color = if (isNew) NemuriTheme.colors.cyan else NemuriTheme.colors.dock.copy(alpha = 0.88f),
								shape = RoundedCornerShape(6.dp),
							) {
								NonWrappingBadgeText(
									text = "+${entry.newChaptersCount}",
									color = if (isNew) NemuriTheme.colors.onCyan else NemuriTheme.colors.textPrimary,
									fontSizeSp = 9.5f,
									modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
								)
							}
						}
					}

					// Bottom chapter + title scrim
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.align(Alignment.BottomCenter)
							.background(
								Brush.verticalGradient(
									colors = listOf(
										Color.Transparent,
										Color.Black.copy(alpha = 0.76f),
										Color.Black.copy(alpha = 0.94f),
									),
								),
							)
							.padding(
								start = hInset,
								end = hInset,
								top = 20.dp,
								bottom = bInset,
							),
						verticalArrangement = Arrangement.spacedBy(1.dp),
					) {
						Text(
							text = entry.shortChapterLabel,
							style = MaterialTheme.typography.labelSmall,
							fontSize = 10.sp,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.cyan,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = manga.title,
							style = MaterialTheme.typography.labelMedium,
							fontSize = 11.5.sp,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}

		NemuriListNavigationOverlay(
			adapter = gridAdapter,
			hasBottomDock = true,
			labelForIndex = { idx -> uiState.galleryEntries.getOrNull(idx)?.dateGroup?.substringBefore(',') },
		)
	}
}

@Composable
private fun FeedForYouView(
	uiState: FeedUiState,
	layoutPrefs: LayoutStudioPrefs,
	gridColumns: Int,
	bottomPadding: Dp,
	onStepColumns: (Int) -> Unit,
	onSelectGenre: (String?) -> Unit,
	onOpenDetails: (Manga, String?) -> Unit,
	onOpenExplore: () -> Unit,
	onRefresh: () -> Unit,
	onEnableSuggestions: () -> Unit,
	onDismissSuggestions: () -> Unit,
) {
	if (uiState.enabledSources.isEmpty()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(bottom = bottomPadding),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			if (uiState.showSuggestionsOptIn) {
				NemuriSuggestionsOptInCard(
					onEnable = onEnableSuggestions,
					onDismiss = onDismissSuggestions,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
				)
			}
			NemuriEmptyState(
				icon = Icons.Default.Explore,
				title = "Enable a source to unlock For You",
				subtitle = "Install or enable at least one manga or novel source in Explore to generate personalized recommendations.",
				actionLabel = "Open Explore",
				onAction = onOpenExplore,
			)
		}
		return
	}

	if (uiState.isDiscoveryLoading && uiState.discoverySections.isEmpty() && uiState.discoveryGridItems.isEmpty() && !uiState.showSuggestionsOptIn) {
		NemuriLoadingState(message = "Curating personalized picks across your sources…")
		return
	}

	val hasContent = uiState.discoverySections.isNotEmpty() || uiState.discoveryGridItems.isNotEmpty()
	val cornerRadiusDp = layoutPrefs.cardCornerRadiusDp
	val aspectRatio = layoutPrefs.coverAspectRatio.ratio
	val outerPad = layoutPrefs.gutterDensity.outerPaddingDp.dp
	val gridGap = layoutPrefs.gutterDensity.spacingDp.dp
	val useComfortableGridCard = layoutPrefs.cardStyle == MangaCardStyle.COMFORTABLE

	val gridRows = remember(uiState.discoveryGridItems, gridColumns) {
		uiState.discoveryGridItems.chunked(gridColumns.coerceIn(2, 6))
	}

	val listState = rememberLazyListState()
	val scrollAdapter = rememberNemuriScrollAdapter(listState)
	val forYouCalloutLabels = remember(uiState.showSuggestionsOptIn, uiState.discoveryGenres, uiState.discoverySections, gridRows) {
		buildList {
			if (uiState.showSuggestionsOptIn) {
				add("Suggestions")
			}
			if (uiState.discoveryGenres.isNotEmpty()) {
				add("Taste")
			}
			uiState.discoverySections.forEach { sec ->
				val short = sec.title.take(16)
				add(short)
				add(short)
			}
			if (gridRows.isNotEmpty()) {
				add("Discover")
				repeat(gridRows.size) { rowIdx ->
					val firstTitle = gridRows[rowIdx].firstOrNull()?.manga?.title?.trim().orEmpty()
					val initial = firstTitle.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
					add(if (initial != null && initial.isLetter()) "Discover • $initial" else "Discover")
				}
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		LazyColumn(
			state = listState,
			modifier = Modifier
				.fillMaxSize()
				.pinchToStepColumns(
					currentColumns = gridColumns,
					minColumns = 2,
					maxColumns = 6,
					onStepColumns = onStepColumns,
				),
			contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
		) {
			if (uiState.showSuggestionsOptIn) {
				item(key = "fy_suggestions_optin") {
					NemuriSuggestionsOptInCard(
						onEnable = onEnableSuggestions,
						onDismiss = onDismissSuggestions,
						modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
					)
				}
			}
			// Interactive TASTE PROFILE Genre Filter Row
			if (uiState.discoveryGenres.isNotEmpty()) {
				item(key = "fy_profile_banner") {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.horizontalScroll(rememberScrollState())
							.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "TASTE PROFILE:",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = NemuriTheme.colors.textMuted,
						)

						val isAllSelected = uiState.selectedGenre == null
						TasteGenreFilterChip(
							label = "All",
							selected = isAllSelected,
							onClick = { onSelectGenre(null) },
						)

						uiState.discoveryGenres.forEach { genre ->
							val selected = uiState.selectedGenre.equals(genre, ignoreCase = true)
							TasteGenreFilterChip(
								label = genre,
								selected = selected,
								onClick = { onSelectGenre(genre) },
							)
						}

						Surface(
							color = NemuriTheme.colors.surfaceElevated,
							shape = CircleShape,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
							modifier = Modifier
								.clip(CircleShape)
								.clickable(enabled = !uiState.isDiscoveryLoading, onClick = onRefresh),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(
									imageVector = Icons.Default.Refresh,
									contentDescription = "Refresh recommendations",
									tint = NemuriTheme.colors.cyan,
									modifier = Modifier.size(13.dp),
								)
								Text(
									text = "Refresh",
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.cyan,
									maxLines = 1,
									softWrap = false,
								)
							}
						}
					}
				}
			}

			if (!hasContent) {
				item(key = "fy_empty_filtered") {
					NemuriEmptyState(
						icon = Icons.Default.AutoAwesome,
						title = if (uiState.selectedGenre != null) {
							"No picks for \"${uiState.selectedGenre}\""
						} else if (uiState.query.isNotBlank()) {
							"No matching recommendations"
						} else {
							"Couldn't reach recommendation sources"
						},
						subtitle = if (uiState.selectedGenre != null) {
							"Tap All in your Taste Profile to view all curated picks."
						} else if (uiState.query.isNotBlank()) {
							"Try clearing your search or filter chip."
						} else {
							"Pull down or tap Refresh to query your enabled catalogs."
						},
						actionLabel = if (uiState.selectedGenre != null) "Show All Genres" else "Refresh Picks",
						onAction = if (uiState.selectedGenre != null) ({ onSelectGenre(null) }) else onRefresh,
					)
				}
			} else {
				// Personalized Carousels ("Top Picks For Your Taste", "Because You Read <Series>", etc.)
				uiState.discoverySections.forEach { section ->
					item(key = "fy_sec_hdr_${section.id}") {
						Box(modifier = Modifier.padding(bottom = 8.dp)) {
							HubSectionHeader(
								title = section.title.uppercase(),
								subtitle = section.subtitle,
							)
						}
					}
					item(key = "fy_sec_row_${section.id}") {
						LazyRow(
							contentPadding = PaddingValues(horizontal = 16.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
							modifier = Modifier.padding(bottom = 18.dp),
						) {
							items(section.items, key = { "${section.id}:${it.manga.id}" }) { rec ->
								val shareKey = "fy-${section.id}-${rec.manga.id}"
								HubDiscoveryPosterCard(
									manga = rec.manga,
									subtitle = "${rec.sourceTitle} • ${rec.reasonLabel}",
									inLibrary = rec.inLibrary,
									cornerRadiusDp = cornerRadiusDp,
									aspectRatio = aspectRatio,
									sharedCoverKey = shareKey,
									onClick = {
										MangaTransferCache.put(rec.manga)
										onOpenDetails(rec.manga, shareKey)
									},
								)
							}
						}
					}
				}

				// Full Multi-Column Vertical Discovery Grid (24-48 deduplicated picks, zero empty space)
				if (gridRows.isNotEmpty()) {
					item(key = "fy_discover_grid_hdr") {
						Box(modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)) {
							HubSectionHeader(
								title = if (uiState.selectedGenre != null) {
									"DISCOVER IN ${uiState.selectedGenre.uppercase()}"
								} else {
									"DISCOVER MORE FOR YOU"
								},
								subtitle = "${uiState.discoveryGridItems.size} fresh picks across sources • $gridColumns cols (pinch to resize)",
							)
						}
					}

					itemsIndexed(
						items = gridRows,
						key = { rowIdx, row -> "fy_grid_row_${rowIdx}_${row.first().manga.id}" },
					) { _, row ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(start = outerPad, end = outerPad, bottom = gridGap),
							horizontalArrangement = Arrangement.spacedBy(gridGap),
						) {
							row.forEach { rec ->
								val shareKey = "fy-grid-${rec.manga.id}"
								Box(modifier = Modifier.weight(1f)) {
									if (useComfortableGridCard) {
										MangaComfortableGridCard(
											manga = rec.manga,
											inLibraryBadge = rec.inLibrary,
											sourceBadge = rec.sourceTitle.take(6).uppercase(),
											aspectRatio = aspectRatio,
											cornerRadiusDp = cornerRadiusDp,
											showSourceBadge = true,
											sharedCoverKey = shareKey,
											onClick = {
												MangaTransferCache.put(rec.manga)
												onOpenDetails(rec.manga, shareKey)
											},
										)
									} else {
										MangaCompactGridCard(
											manga = rec.manga,
											inLibraryBadge = rec.inLibrary,
											sourceBadge = rec.sourceTitle.take(6).uppercase(),
											aspectRatio = aspectRatio,
											cornerRadiusDp = cornerRadiusDp,
											showSourceBadge = true,
											sharedCoverKey = shareKey,
											onClick = {
												MangaTransferCache.put(rec.manga)
												onOpenDetails(rec.manga, shareKey)
											},
										)
									}
								}
							}
							repeat((gridColumns - row.size).coerceAtLeast(0)) {
								Spacer(modifier = Modifier.weight(1f))
							}
						}
					}
				}
			}
		}

		NemuriListNavigationOverlay(
			adapter = scrollAdapter,
			hasBottomDock = true,
			labelForIndex = { idx -> forYouCalloutLabels.getOrNull(idx) },
		)
	}
}

@Composable
private fun TasteGenreFilterChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val chipShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.tealContainer,
		shape = chipShape,
		border = BorderStroke(
			1.dp,
			if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.cyan.copy(alpha = 0.42f),
		),
		modifier = Modifier
			.clip(chipShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			if (selected) {
				Icon(
					imageVector = Icons.Default.Check,
					contentDescription = null,
					tint = NemuriTheme.colors.onCyan,
					modifier = Modifier.size(12.dp),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.cyan,
				maxLines = 1,
				softWrap = false,
			)
		}
	}
}

@Composable
private fun HubSectionHeader(
	title: String,
	subtitle: String? = null,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier
					.yorhaTacticalBrackets()
					.padding(horizontal = 3.dp, vertical = 1.dp),
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textDim,
				)
			}
		}
		if (!actionLabel.isNullOrBlank() && onAction != null) {
			Text(
				text = actionLabel,
				style = MaterialTheme.typography.labelMedium,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier
					.clip(RoundedCornerShape(8.dp))
					.clickable(onClick = onAction)
					.padding(horizontal = 6.dp, vertical = 4.dp),
			)
		}
	}
}

@Composable
private fun ContinueReadingCard(
	item: ContinueReadingItem,
	cornerRadiusDp: Int,
	aspectRatio: Float,
	sharedCoverKey: String,
	onResume: () -> Unit,
	onOpenDetails: () -> Unit,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val coverShape = RoundedCornerShape(safeCorner.dp)
	Column(
		modifier = Modifier
			.width(128.dp)
			.clickable(onClick = onResume),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(aspectRatio)
				.clip(coverShape)
				.border(1.dp, NemuriTheme.colors.border, coverShape),
		) {
			MangaCoverImage(
				manga = item.manga,
				coverUrl = item.manga.coverUrl,
				modifier = Modifier
					.fillMaxSize()
					.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
			)
			Surface(
				color = NemuriTheme.colors.dock.copy(alpha = 0.90f),
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(
						end = cornerSafeBadgeInset(safeCorner),
						bottom = cornerSafeBadgeInset(safeCorner) + 6.dp,
					)
					.border(0.8.dp, NemuriTheme.colors.border, RoundedCornerShape(999.dp)),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(3.dp),
				) {
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = "Resume",
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(12.dp),
					)
					Text(
						text = item.progressPercentLabel,
						style = MaterialTheme.typography.labelSmall,
						fontSize = 10.sp,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						softWrap = false,
					)
				}
			}
			CornerSafeCoverProgressBar(
				progress = item.progressBarFraction.coerceIn(0.04f, 1f),
				cornerRadiusDp = safeCorner,
				fillColor = NemuriTheme.colors.cyan,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(8.dp))
				.clickable(onClick = onOpenDetails)
				.padding(horizontal = 2.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = item.manga.title,
				style = MaterialTheme.typography.labelMedium,
				color = NemuriTheme.colors.textPrimary,
				minLines = 2,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = item.sourceTitle,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textDim,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun HubDiscoveryPosterCard(
	manga: Manga,
	subtitle: String?,
	inLibrary: Boolean,
	cornerRadiusDp: Int,
	aspectRatio: Float,
	sharedCoverKey: String,
	onClick: () -> Unit,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val coverShape = RoundedCornerShape(safeCorner.dp)
	Column(
		modifier = Modifier
			.width(114.dp)
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(5.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(aspectRatio)
				.clip(coverShape)
				.border(
					1.dp,
					if (inLibrary) NemuriTheme.colors.healthy.copy(alpha = 0.65f) else NemuriTheme.colors.border,
					coverShape,
				),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.fillMaxSize()
					.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
			)
			if (inLibrary) {
				Surface(
					color = NemuriTheme.colors.healthy.copy(alpha = 0.92f),
					shape = RoundedCornerShape(6.dp),
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(cornerSafeBadgeInset(safeCorner)),
				) {
					NonWrappingBadgeText(
						text = "SAVED",
						color = NemuriTheme.colors.onCyan,
						fontSizeSp = 8.5f,
						modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
					)
				}
			}
		}
		Text(
			text = manga.title,
			style = MaterialTheme.typography.labelMedium,
			color = NemuriTheme.colors.textPrimary,
			minLines = 2,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 2.dp),
		)
		if (!subtitle.isNullOrBlank()) {
			Text(
				text = subtitle,
				style = MaterialTheme.typography.labelSmall,
				fontSize = 10.sp,
				color = NemuriTheme.colors.textDim,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(horizontal = 2.dp),
			)
		}
	}
}

@Composable
private fun FeedModePill(
	label: String,
	badgeCount: Int,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val pillShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) MaterialTheme.colorScheme.primaryContainer else NemuriTheme.colors.surfaceElevated,
		shape = pillShape,
		modifier = modifier
			.height(36.dp)
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				pillShape,
			)
			.clip(pillShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 6.dp),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else NemuriTheme.colors.textSecondary,
				maxLines = 1,
				softWrap = false,
				overflow = TextOverflow.Ellipsis,
			)
			if (badgeCount > 0) {
				Spacer(modifier = Modifier.width(4.dp))
				Surface(
					color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.tealContainer,
					shape = pillShape,
				) {
					Text(
						text = if (badgeCount > 99) "99+" else badgeCount.toString(),
						style = MaterialTheme.typography.labelSmall,
						fontSize = 9.5.sp,
						fontWeight = FontWeight.Bold,
						color = if (selected) NemuriTheme.colors.onCyan else NemuriTheme.colors.cyan,
						maxLines = 1,
						softWrap = false,
						modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun FeedFilterChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val chipShape = NemuriTheme.pillShape()
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
		shape = chipShape,
		modifier = Modifier
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.7f) else NemuriTheme.colors.border,
				chipShape,
			)
			.clip(chipShape)
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			if (selected) {
				Icon(
					imageVector = Icons.Default.Check,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(12.dp),
				)
			}
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
				color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
				maxLines = 1,
				softWrap = false,
			)
		}
	}
}

@Composable
private fun FeedEntryCard(
	entry: FeedEntryItem,
	sharedCoverKey: String,
	onOpenDetails: () -> Unit,
	onOpenLatestChapter: () -> Unit,
	onDownload: () -> Unit,
	onMarkRead: () -> Unit,
) {
	val manga = entry.logItem.manga
	val isNew = entry.logItem.isNew
	val cardShape = NemuriTheme.shape(14.dp)
	Surface(
		color = NemuriTheme.colors.card,
		shape = cardShape,
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				if (isNew) NemuriTheme.colors.cyan.copy(alpha = 0.45f) else NemuriTheme.colors.border,
				cardShape,
			)
			.clip(cardShape)
			.clickable(onClick = onOpenLatestChapter),
	) {
		Row(
			modifier = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.size(width = 52.dp, height = 74.dp)
					.clip(RoundedCornerShape(10.dp))
					.nemuriSharedCover(sharedCoverKey, 10.dp)
					.clickable(onClick = onOpenDetails),
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(3.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = manga.title,
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (isNew && entry.newChaptersCount > 0) {
						Surface(
							color = NemuriTheme.colors.cyan,
							shape = RoundedCornerShape(6.dp),
						) {
							NonWrappingBadgeText(
								text = "+${entry.newChaptersCount}",
								color = NemuriTheme.colors.onCyan,
								modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
							)
						}
					}
				}
				Text(
					text = entry.chaptersSummary,
					style = MaterialTheme.typography.bodySmall,
					color = if (isNew) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textSecondary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "${entry.sourceTitle} • ${entry.timeLabel}",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Column(
				horizontalAlignment = Alignment.End,
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				IconButton(
					onClick = onDownload,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						imageVector = Icons.Default.Download,
						contentDescription = "Download chapters",
						tint = NemuriTheme.colors.textSecondary,
						modifier = Modifier.size(18.dp),
					)
				}
				IconButton(
					onClick = onMarkRead,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						imageVector = Icons.Default.Check,
						contentDescription = if (isNew) "Mark read" else "Remove from feed",
						tint = if (isNew) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
						modifier = Modifier.size(18.dp),
					)
				}
			}
		}
	}
}
