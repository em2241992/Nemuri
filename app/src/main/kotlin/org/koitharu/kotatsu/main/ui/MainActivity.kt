package org.koitharu.kotatsu.main.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.screens.AiStudioScreen
import org.koitharu.kotatsu.ui.screens.BackupsAndSettingsScreen
import org.koitharu.kotatsu.ui.screens.ExploreScreen
import org.koitharu.kotatsu.ui.screens.FeedScreen
import org.koitharu.kotatsu.ui.screens.HistoryScreen
import org.koitharu.kotatsu.ui.screens.LibraryScreen
import org.koitharu.kotatsu.ui.screens.ReaderScreen
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.KotatsuNeoTheme
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

fun AppNavTab.icon(): ImageVector = when (this) {
	AppNavTab.LIBRARY -> Icons.Default.LocalLibrary
	AppNavTab.HISTORY -> Icons.Default.History
	AppNavTab.FEED -> Icons.Default.RssFeed
	AppNavTab.EXPLORE -> Icons.Default.Explore
	AppNavTab.AI_STUDIO -> Icons.Default.AutoAwesome
	AppNavTab.BACKUPS_DB -> Icons.Default.Backup
}

class MainActivity : ComponentActivity() {

	private val viewModel: KotatsuNeoViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
			val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
			val colorSchemePreset by viewModel.colorSchemePreset.collectAsStateWithLifecycle()
			val pureOledBlack by viewModel.pureOledBlack.collectAsStateWithLifecycle()

			KotatsuNeoTheme(
				darkTheme = darkTheme,
				themeMode = themeMode,
				colorSchemePreset = colorSchemePreset,
				pureOledBlack = pureOledBlack,
			) {
				KotatsuNeoRootApp(viewModel = viewModel)
			}
		}
	}
}

@Composable
fun KotatsuNeoRootApp(
	viewModel: KotatsuNeoViewModel,
) {
	val filteredItems by viewModel.filteredLibraryItems.collectAsStateWithLifecycle()
	val allItems by viewModel.allLibraryItems.collectAsStateWithLifecycle()
	val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
	val feedUpdates by viewModel.feedUpdates.collectAsStateWithLifecycle()
	val categories by viewModel.categories.collectAsStateWithLifecycle()
	val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
	val quickFilter by viewModel.quickFilter.collectAsStateWithLifecycle()
	val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
	val advancedFilter by viewModel.advancedFilter.collectAsStateWithLifecycle()
	val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
	val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()
	val displayConfig by viewModel.displayConfig.collectAsStateWithLifecycle()
	val readerConfig by viewModel.readerConfig.collectAsStateWithLifecycle()
	val navBarStyle by viewModel.navBarStyle.collectAsStateWithLifecycle()
	val enabledNavTabs by viewModel.enabledNavTabs.collectAsStateWithLifecycle()
	val duplicateCandidates by viewModel.duplicateCandidates.collectAsStateWithLifecycle()
	val brokenProposals by viewModel.brokenMangaProposals.collectAsStateWithLifecycle()
	val batchRule by viewModel.batchRule.collectAsStateWithLifecycle()
	val selectedMangaDetailId by viewModel.selectedMangaDetailId.collectAsStateWithLifecycle()
	val activeReaderMangaId by viewModel.activeReaderMangaId.collectAsStateWithLifecycle()
	val activeChapterIndex by viewModel.activeChapterIndex.collectAsStateWithLifecycle()
	val activePageIndex by viewModel.activePageIndex.collectAsStateWithLifecycle()
	val bookmarkedPageKeys by viewModel.bookmarkedPageKeys.collectAsStateWithLifecycle()
	val seriesReaderProfiles by viewModel.seriesReaderProfiles.collectAsStateWithLifecycle()
	val checkAndSwapPrompt by viewModel.checkAndSwapPrompt.collectAsStateWithLifecycle()
	val extensionRepos by viewModel.extensionRepos.collectAsStateWithLifecycle()
	val discoveredExtensions by viewModel.discoveredExtensions.collectAsStateWithLifecycle()
	val extensionInstallTasks by viewModel.extensionInstallTasks.collectAsStateWithLifecycle()
	val extensionCatalog by viewModel.extensionCatalogFlow.collectAsStateWithLifecycle()
	val sqliteDiagnostics by viewModel.sqliteDiagnostics.collectAsStateWithLifecycle()
	val lastBackupSummary by viewModel.lastBackupSummary.collectAsStateWithLifecycle()
	val lastExportedPreview by viewModel.lastExportedPreview.collectAsStateWithLifecycle()
	val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
	val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
	val colorSchemePreset by viewModel.colorSchemePreset.collectAsStateWithLifecycle()
	val pureOledBlack by viewModel.pureOledBlack.collectAsStateWithLifecycle()
	val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
	val activeSmartQueryAst by viewModel.activeSmartQueryAst.collectAsStateWithLifecycle()
	val activeMangaRecapState by viewModel.activeMangaRecapState.collectAsStateWithLifecycle()
	val context = LocalContext.current

	var currentTab by rememberSaveable { mutableStateOf(AppNavTab.LIBRARY) }
	var isSettingsDirectOpen by rememberSaveable { mutableStateOf(false) }
	var showContinuePeekCard by remember { mutableStateOf(false) }

	// If Reader is explicitly opened from a manga card, show full-screen Reader
	val activeReaderManga = allItems.find { it.manga.id == activeReaderMangaId }
	if (activeReaderManga != null) {
		val isBookmarked = "${activeReaderManga.manga.id}_${activeChapterIndex}_${activePageIndex}" in bookmarkedPageKeys
		ReaderScreen(
			mangaItem = activeReaderManga,
			chapterIndex = activeChapterIndex,
			pageIndex = activePageIndex,
			readerConfig = readerConfig,
			isPageBookmarked = isBookmarked,
			hasCustomSeriesProfile = activeReaderManga.manga.id in seriesReaderProfiles,
			onPageChange = viewModel::setReaderPage,
			onChapterChange = viewModel::switchReaderChapter,
			onUpdateReaderConfig = viewModel::updateReaderConfig,
			onTogglePageBookmark = {
				viewModel.togglePageBookmark(
					activeReaderManga.manga.id,
					activeChapterIndex,
					activePageIndex,
					activeReaderManga.manga.title,
				)
			},
			onSaveSeriesProfile = {
				viewModel.saveSeriesReaderProfile(
					activeReaderManga.manga.id,
					activeReaderManga.manga.title,
				)
			},
			onCloseReader = viewModel::closeReader,
		)
		return
	}

	// Ensure Back button from Settings or secondary tabs returns to Library
	if (isSettingsDirectOpen) {
		BackHandler {
			isSettingsDirectOpen = false
		}
	} else if (currentTab != AppNavTab.LIBRARY) {
		BackHandler {
			currentTab = AppNavTab.LIBRARY
		}
	}

	val aiBadgeCount = duplicateCandidates.size + brokenProposals.size
	val unreadFeedCount = feedUpdates.count { it.isNewRelease }

	// Resolve most recent manga/chapter for Kotatsu's "Continue" floating button
	val continuableMangaItem = historyItems.firstOrNull() ?: allItems.firstOrNull()
	val onContinueReading: () -> Unit = {
		showContinuePeekCard = false
		val lastHistory = historyItems.firstOrNull()
		if (lastHistory != null) {
			viewModel.openReader(
				lastHistory.manga.id,
				(lastHistory.lastReadChapterNumber.toInt() - 1).coerceAtLeast(0),
			)
		} else {
			val fallbackManga = allItems.firstOrNull()
			if (fallbackManga != null) {
				viewModel.openReader(fallbackManga.manga.id, 0)
			}
		}
	}
	val hasContinuableManga = continuableMangaItem != null

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val isExpandedScreen = maxWidth >= 700.dp

		Scaffold(
			contentWindowInsets = WindowInsets.statusBars,
			bottomBar = {
				if (!isExpandedScreen && navBarStyle == NavBarStyle.DEFAULT_DOCKED && !isSettingsDirectOpen) {
					NavigationBar(
						modifier = Modifier.testTag("bottom_navigation_bar_docked"),
					) {
						enabledNavTabs.forEach { tab ->
							val badgeCount = when (tab) {
								AppNavTab.AI_STUDIO -> aiBadgeCount
								AppNavTab.FEED -> unreadFeedCount
								else -> 0
							}
							NavigationBarItem(
								selected = currentTab == tab,
								onClick = {
									isSettingsDirectOpen = false
									currentTab = tab
								},
								icon = {
									if (badgeCount > 0) {
										BadgedBox(
											badge = { Badge { Text("$badgeCount") } },
										) {
											Icon(
												imageVector = tab.icon(),
												contentDescription = tab.label,
											)
										}
									} else {
										Icon(
											imageVector = tab.icon(),
											contentDescription = tab.label,
										)
									}
								},
								label = {
									Text(
										text = tab.label,
										maxLines = 1,
										softWrap = false,
										overflow = TextOverflow.Ellipsis,
										fontSize = 11.sp,
									)
								},
								modifier = Modifier.testTag("nav_tab_${tab.id}"),
							)
						}
					}
				}
			},
		) { innerPadding ->
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding),
			) {
				Row(modifier = Modifier.fillMaxSize()) {
					if (isExpandedScreen) {
						NavigationRail(
							modifier = Modifier.testTag("side_navigation_rail"),
						) {
							enabledNavTabs.forEach { tab ->
								NavigationRailItem(
									selected = !isSettingsDirectOpen && currentTab == tab,
									onClick = {
										isSettingsDirectOpen = false
										currentTab = tab
									},
									icon = {
										Icon(
											imageVector = tab.icon(),
											contentDescription = tab.label,
										)
									},
									label = { Text(tab.label, maxLines = 1, softWrap = false) },
									modifier = Modifier.testTag("rail_tab_${tab.id}"),
								)
							}
						}
					}

					Column(modifier = Modifier.weight(1f)) {
						// Floating Status Notification Banner
						AnimatedVisibility(visible = statusBanner != null) {
							Surface(
								color = MaterialTheme.colorScheme.primaryContainer,
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 6.dp)
									.testTag("status_notification_banner"),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 6.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = statusBanner.orEmpty(),
										style = MaterialTheme.typography.bodySmall,
										fontWeight = FontWeight.Medium,
										color = MaterialTheme.colorScheme.onPrimaryContainer,
										modifier = Modifier.weight(1f),
									)
									IconButton(onClick = viewModel::clearStatusBanner) {
										Icon(
											imageVector = Icons.Default.Close,
											contentDescription = "Dismiss notification",
											tint = MaterialTheme.colorScheme.onPrimaryContainer,
										)
									}
								}
							}
						}

						Box(modifier = Modifier.fillMaxSize()) {
							if (isSettingsDirectOpen || currentTab == AppNavTab.BACKUPS_DB) {
								BackupsAndSettingsScreen(
									navBarStyle = navBarStyle,
									enabledNavTabs = enabledNavTabs,
									sqliteDiagnostics = sqliteDiagnostics,
									lastBackupSummary = lastBackupSummary,
									lastExportedPreview = lastExportedPreview,
									darkTheme = darkTheme,
									pureOledBlack = pureOledBlack,
									themeMode = themeMode,
									colorSchemePreset = colorSchemePreset,
									displayConfig = displayConfig,
									readerConfig = readerConfig,
									extensionRepos = extensionRepos,
									discoveredExtensions = discoveredExtensions,
									extensionInstallTasks = extensionInstallTasks,
									canRequestPackageInstalls = viewModel.canRequestPackageInstalls(),
									onSetNavBarStyle = viewModel::setNavBarStyle,
									onToggleNavTab = viewModel::toggleNavTab,
									onMoveNavTab = viewModel::moveNavTab,
									onExportBackup = viewModel::exportBackup,
									onRestoreFromUri = viewModel::restoreBackupFromUri,
									onSimulateRestoreSample = viewModel::simulateRestoreSampleBackup,
									onRefreshDiagnostics = viewModel::optimizeDatabaseStorage,
									onToggleDarkTheme = viewModel::toggleDarkTheme,
									onTogglePureOledBlack = viewModel::togglePureOledBlack,
									onSetThemeMode = viewModel::setThemeMode,
									onSetColorSchemePreset = viewModel::setColorSchemePreset,
									onUpdateDisplayConfig = viewModel::updateDisplayConfig,
									onUpdateReaderConfig = viewModel::updateReaderConfig,
									onAddRepository = viewModel::addExtensionRepository,
									onRemoveRepository = viewModel::removeExtensionRepository,
									onInstallOrUpdateExtension = viewModel::installOrUpdateExtension,
									onUninstallExtension = viewModel::uninstallExtension,
									onSyncAllRepositories = viewModel::syncAllExtensionRepositories,
									onOpenUnknownSourcesSettings = {
										runCatching {
											context.startActivity(viewModel.createManageUnknownAppSourcesIntent())
										}
									},
									onLaunchApkPackageInstaller = { ext ->
										runCatching {
											if (viewModel.canRequestPackageInstalls()) {
												viewModel.createApkPackageInstallerIntent(ext)?.let { context.startActivity(it) }
											} else {
												context.startActivity(viewModel.createManageUnknownAppSourcesIntent())
											}
										}
									},
									onLaunchBrowserApkDownload = { ext ->
										runCatching {
											context.startActivity(viewModel.createBrowserApkDownloadIntent(ext))
										}
									},
									onShareExtensionFileProvider = { ext ->
										runCatching {
											viewModel.createFileProviderShareIntent(ext)?.let { context.startActivity(it) }
										}
									},
									onRefreshFeed = viewModel::refreshFeedFromExtensions,
									onBackToLibrary = {
										isSettingsDirectOpen = false
										currentTab = AppNavTab.LIBRARY
									},
								)
							} else {
								when (currentTab) {
									AppNavTab.LIBRARY -> {
										LibraryScreen(
											items = filteredItems,
											allItemsCount = allItems.size,
											categories = categories,
											selectedCategoryId = selectedCategoryId,
											quickFilter = quickFilter,
											searchQuery = searchQuery,
											advancedFilter = advancedFilter,
											availableTags = availableTags,
											availableSources = availableSources,
											displayConfig = displayConfig,
											brokenProposals = brokenProposals,
											selectedDetailManga = allItems.find { it.manga.id == selectedMangaDetailId },
											checkAndSwapPrompt = checkAndSwapPrompt,
											bookmarkedPageKeys = bookmarkedPageKeys,
											onSearchQueryChange = viewModel::updateSearchQuery,
											onUpdateAdvancedFilter = viewModel::updateAdvancedFilter,
											onClearAdvancedFilter = viewModel::clearAdvancedFilter,
											onSelectCategory = viewModel::selectCategory,
											onSelectQuickFilter = viewModel::selectQuickFilter,
											onUpdateDisplayConfig = viewModel::updateDisplayConfig,
											onOpenMangaDetails = viewModel::openMangaDetails,
											onOpenReader = viewModel::openReader,
											onRunAiFixSingle = viewModel::runSingleAiFixIt,
											onMigrateSingleSource = viewModel::migrateSingleMangaSource,
											onMigrateBatchSources = viewModel::migrateBatchMangaSources,
											onBatchMoveToCategory = viewModel::batchMoveToCategory,
											onBatchMarkChaptersRead = viewModel::batchMarkChaptersRead,
											onBatchDownloadSeries = viewModel::batchDownloadSeries,
											onBatchDeleteDownloads = viewModel::batchDeleteDownloads,
											onBatchRemoveFromLibrary = viewModel::batchRemoveFromLibrary,
											onConfirmCheckAndSwap = viewModel::confirmCheckAndSwap,
											onDismissCheckAndSwap = viewModel::dismissCheckAndSwap,
											onAddCategory = viewModel::addCategory,
											onDeleteCategory = viewModel::deleteCategory,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
											onNavigateToAiStudio = { currentTab = AppNavTab.AI_STUDIO },
											activeSmartQueryAst = activeSmartQueryAst,
											activeMangaRecapState = activeMangaRecapState,
											onRequestWhereWasIRecap = { mangaId, forceRegenerate ->
												viewModel.requestWhereWasIRecap(
													mangaId = mangaId,
													forceShow = true,
													forceRegenerate = forceRegenerate,
												)
											},
										)
									}

									AppNavTab.HISTORY -> {
										HistoryScreen(
											historyItems = historyItems,
											onResumeReading = viewModel::openReader,
											onOpenDetails = { id ->
												viewModel.openMangaDetails(id)
												currentTab = AppNavTab.LIBRARY
											},
											onRemoveHistoryItem = viewModel::removeHistoryItem,
											onClearAllHistory = viewModel::clearAllHistory,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
										)
									}

									AppNavTab.FEED -> {
										FeedScreen(
											feedUpdates = feedUpdates,
											libraryItems = allItems,
											extensionCatalog = extensionCatalog,
											onOpenReader = viewModel::openReader,
											onOpenDetails = { id ->
												viewModel.openMangaDetails(id)
												currentTab = AppNavTab.LIBRARY
											},
											onRequestAddCatalogManga = { candidate ->
												viewModel.requestAddCatalogManga(candidate)
												currentTab = AppNavTab.LIBRARY
											},
											onRefreshFeed = viewModel::refreshFeedFromExtensions,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
											onMarkFeedChapterRead = viewModel::markFeedChapterRead,
										)
									}

									AppNavTab.EXPLORE -> {
										ExploreScreen(
											repos = extensionRepos,
											discoveredExtensions = discoveredExtensions,
											extensionCatalog = extensionCatalog,
											libraryItems = allItems,
											feedUpdates = feedUpdates,
											onAddRepository = viewModel::addExtensionRepository,
											onRemoveRepository = viewModel::removeExtensionRepository,
											onImportCbzUri = viewModel::importCbzFromUri,
											onRequestAddCatalogManga = { candidate ->
												viewModel.requestAddCatalogManga(candidate)
												currentTab = AppNavTab.LIBRARY
											},
											onOpenDetails = { id ->
												viewModel.openMangaDetails(id)
												currentTab = AppNavTab.LIBRARY
											},
											onOpenReader = viewModel::openReader,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
											onInstallOrUpdateExtension = viewModel::installOrUpdateExtension,
											onUninstallExtension = viewModel::uninstallExtension,
										)
									}

									AppNavTab.AI_STUDIO -> {
										AiStudioScreen(
											duplicateCandidates = duplicateCandidates,
											brokenProposals = brokenProposals,
											batchRule = batchRule,
											displayConfig = displayConfig,
											readerConfig = readerConfig,
											extensionCatalog = extensionCatalog,
											onSetBatchRule = viewModel::setBatchDedupRule,
											onResolveDuplicateOneByOne = viewModel::resolveDuplicateOneByOne,
											onIgnoreDuplicate = viewModel::ignoreDuplicatePair,
											onResolveAllDuplicatesEnMasse = viewModel::resolveAllDuplicatesEnMasse,
											onRunSingleAiFix = viewModel::runSingleAiFixIt,
											onRunAllAiFixBatch = viewModel::runAllAiFixItBatch,
											onUpdateDisplayConfig = viewModel::updateDisplayConfig,
											onUpdateReaderConfig = viewModel::updateReaderConfig,
											onTestCheckAndSwapAdd = { candidate ->
												viewModel.requestAddCatalogManga(candidate)
												currentTab = AppNavTab.LIBRARY
											},
										)
									}

									AppNavTab.BACKUPS_DB -> Unit
								}
							}
						}
					}
				}

				// Long-Press Peek Card above the Continue Button
				if (showContinuePeekCard && continuableMangaItem != null && !isSettingsDirectOpen && selectedMangaDetailId == null) {
					val resumeChapterNum = continuableMangaItem.lastReadChapterNumber.toInt().coerceAtLeast(1)
					Card(
						colors = CardDefaults.cardColors(
							containerColor = Color(0xFF1C2224).copy(alpha = 0.98f),
						),
						shape = RoundedCornerShape(22.dp),
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.navigationBarsPadding()
							.padding(
								end = 16.dp,
								bottom = if (navBarStyle == NavBarStyle.FLOATING_ISLAND) 78.dp else 78.dp,
							)
							.widthIn(max = 310.dp)
							.border(1.dp, Color(0xFF4CCAD0).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
							.testTag("continue_peek_preview_card"),
					) {
						Column(
							modifier = Modifier.padding(14.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								MangaCoverArtwork(
									title = continuableMangaItem.manga.title,
									coverUrl = continuableMangaItem.manga.coverUrl,
									modifier = Modifier
										.width(48.dp)
										.aspectRatio(0.7f)
										.clip(RoundedCornerShape(8.dp)),
								)
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = "CONTINUE READING",
										fontFamily = JetBrainsMonoFamily,
										fontWeight = FontWeight.Bold,
										fontSize = 9.sp,
										color = Color(0xFF4CCAD0),
									)
									Text(
										text = UiSourceAndTitleFormatter.formatDisplayTitle(continuableMangaItem.manga.title),
										style = MaterialTheme.typography.titleSmall,
										fontFamily = PlusJakartaDisplayFamily,
										fontWeight = FontWeight.Bold,
										color = Color.White,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "Ch. $resumeChapterNum / ${continuableMangaItem.totalChapters} • ${UiSourceAndTitleFormatter.formatSourceName(continuableMangaItem.manga.source)}",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = Color(0xFFBDC7CA),
									)
								}
								IconButton(
									onClick = { showContinuePeekCard = false },
									modifier = Modifier.size(28.dp),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Close preview",
										tint = Color(0xFFBDC7CA),
										modifier = Modifier.size(16.dp),
									)
								}
							}
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Button(
									onClick = onContinueReading,
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
									modifier = Modifier.weight(1f),
								) {
									Text("Resume Ch. $resumeChapterNum", fontSize = 11.sp)
								}
								FilledTonalButton(
									onClick = {
										showContinuePeekCard = false
										viewModel.openMangaDetails(continuableMangaItem.manga.id)
										currentTab = AppNavTab.LIBRARY
									},
									contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
								) {
									Text("Details", fontSize = 11.sp)
								}
							}
						}
					}
				}

				// Classic Docked Navigation Mode: Kotatsu "Continue" Floating Button with words appears ABOVE on the right (matching Screenshot 1)
				if (
					!isExpandedScreen &&
					navBarStyle == NavBarStyle.DEFAULT_DOCKED &&
					!isSettingsDirectOpen &&
					selectedMangaDetailId == null &&
					hasContinuableManga
				) {
					KotatsuContinueFloatingButton(
						onClick = onContinueReading,
						onLongClick = { showContinuePeekCard = !showContinuePeekCard },
						iconOnly = false,
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(end = 16.dp, bottom = 14.dp),
					)
				}

				// Floating Island Navigation Mode: Non-clickable 48.dp Bottom Edge Gradient Scrim + Sleek Capsule Island + Rounder Icon-Only "Continue" Button
				if (!isExpandedScreen && navBarStyle == NavBarStyle.FLOATING_ISLAND && !isSettingsDirectOpen) {
					val bgScrimColor = MaterialTheme.colorScheme.background
					Box(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.fillMaxWidth()
							.height(48.dp)
							.background(
								Brush.verticalGradient(
									colors = listOf(
										Color.Transparent,
										bgScrimColor.copy(alpha = 0.92f),
									),
								),
							)
							.testTag("bottom_dock_gradient_scrim"),
					)

					Row(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.navigationBarsPadding()
							.padding(horizontal = 12.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
					) {
						FloatingIslandNavigationBar(
							tabs = enabledNavTabs,
							currentTab = currentTab,
							aiBadgeCount = aiBadgeCount,
							feedBadgeCount = unreadFeedCount,
							onSelectTab = { selected ->
								isSettingsDirectOpen = false
								currentTab = selected
							},
						)

						if (selectedMangaDetailId == null && hasContinuableManga) {
							KotatsuContinueFloatingButton(
								onClick = onContinueReading,
								onLongClick = { showContinuePeekCard = !showContinuePeekCard },
								iconOnly = true,
							)
						}
					}
				}
			}
		}
	}
}

/**
 * Restored Kotatsu "Continue" Floating Button:
 * - When `iconOnly = true` (Floating Island mode):
 *   renders a rounder 56.dp circular/capsule icon-only button without words using `RoundedCornerShape(32.dp)` (the exact same border radius and height as `FloatingIslandNavigationBar`) in bright aqua (#4CCAD0) with a dark teal (#07383B) open-book icon.
 * - When `iconOnly = false` (Classic Docked mode):
 *   renders the extended floating button with the reader icon + "Continue" label above the bottom navigation bar on the right.
 * - Supports Long-Press (`onLongClick`) to peek at the manga cover, title, and chapter before opening the Reader.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KotatsuContinueFloatingButton(
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	iconOnly: Boolean,
	modifier: Modifier = Modifier,
) {
	val primaryColor = MaterialTheme.colorScheme.primary
	val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

	if (iconOnly) {
		val islandMatchingShape = RoundedCornerShape(32.dp)
		Surface(
			color = primaryColor,
			contentColor = onPrimaryColor,
			tonalElevation = 8.dp,
			shadowElevation = 14.dp,
			shape = islandMatchingShape,
			modifier = modifier
				.size(56.dp)
				.clip(islandMatchingShape)
				.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
				)
				.border(
					width = 1.dp,
					color = primaryColor.copy(alpha = 0.65f),
					shape = islandMatchingShape,
				)
				.testTag("floating_continue_button"),
		) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.MenuBook,
					contentDescription = "Continue reading (Long-press to peek)",
					tint = onPrimaryColor,
					modifier = Modifier.size(24.dp),
				)
			}
		}
	} else {
		val classicShape = RoundedCornerShape(18.dp)
		Surface(
			color = MaterialTheme.colorScheme.primaryContainer,
			contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			tonalElevation = 8.dp,
			shadowElevation = 12.dp,
			shape = classicShape,
			modifier = modifier
				.height(56.dp)
				.clip(classicShape)
				.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
				)
				.border(
					width = 1.dp,
					color = primaryColor.copy(alpha = 0.35f),
					shape = classicShape,
				)
				.testTag("floating_continue_button"),
		) {
			Row(
				modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.MenuBook,
					contentDescription = "Continue reading (Long-press to peek)",
					tint = primaryColor,
					modifier = Modifier.size(22.dp),
				)
				Text(
					text = "Continue",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					fontSize = 15.sp,
					maxLines = 1,
					softWrap = false,
					color = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}
		}
	}
}

@Composable
private fun FloatingIslandNavigationBar(
	tabs: List<AppNavTab>,
	currentTab: AppNavTab,
	aiBadgeCount: Int,
	feedBadgeCount: Int,
	onSelectTab: (AppNavTab) -> Unit,
	modifier: Modifier = Modifier,
) {
	val primaryColor = MaterialTheme.colorScheme.primary
	val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

	Surface(
		color = Color(0xFF1C2224).copy(alpha = 0.96f),
		tonalElevation = 8.dp,
		shadowElevation = 14.dp,
		shape = RoundedCornerShape(32.dp),
		modifier = modifier
			.height(56.dp)
			.border(
				width = 1.dp,
				color = Color(0xFF364044),
				shape = RoundedCornerShape(32.dp),
			)
			.testTag("floating_island_navigation_bar"),
	) {
		Row(
			modifier = Modifier
				.padding(horizontal = 6.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			tabs.forEach { tab ->
				val isSelected = currentTab == tab
				val badgeCount = when (tab) {
					AppNavTab.AI_STUDIO -> aiBadgeCount
					AppNavTab.FEED -> feedBadgeCount
					else -> 0
				}

				Box(
					modifier = Modifier
						.height(44.dp)
						.clip(RoundedCornerShape(24.dp))
						.background(
							if (isSelected) {
								primaryColor
							} else {
								Color.Transparent
							},
						)
						.clickable { onSelectTab(tab) }
						.padding(
							horizontal = if (isSelected) 14.dp else 11.dp,
							vertical = 6.dp,
						)
						.testTag("nav_tab_${tab.id}"),
					contentAlignment = Alignment.Center,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(7.dp),
					) {
						val iconTint = if (isSelected) {
							onPrimaryColor
						} else {
							Color(0xFFC4CDD0)
						}

						if (badgeCount > 0) {
							BadgedBox(
								badge = {
									Badge(
										containerColor = Color(0xFFF6B7B9),
										contentColor = Color(0xFF4A1215),
									) {
										Text(
											text = if (badgeCount > 99) "99+" else "$badgeCount",
											fontSize = 9.sp,
											fontWeight = FontWeight.Bold,
										)
									}
								},
							) {
								Icon(
									imageVector = tab.icon(),
									contentDescription = tab.label,
									tint = iconTint,
									modifier = Modifier.size(20.dp),
								)
							}
						} else {
							Icon(
								imageVector = tab.icon(),
								contentDescription = tab.label,
								tint = iconTint,
								modifier = Modifier.size(20.dp),
							)
						}

						if (isSelected) {
							Text(
								text = tab.label,
								fontSize = 13.sp,
								fontWeight = FontWeight.SemiBold,
								maxLines = 1,
								softWrap = false,
								overflow = TextOverflow.Ellipsis,
								color = onPrimaryColor,
							)
						}
					}
				}
			}
		}
	}
}
