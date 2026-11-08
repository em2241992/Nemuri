package org.koitharu.kotatsu.main.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koitharu.kotatsu.core.ai.NemuriAiStatus
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.ui.components.GitHubUpdateChecker
import org.koitharu.kotatsu.ui.components.MangaCoverArtwork
import org.koitharu.kotatsu.ui.components.NemuriBiometricLockOverlay
import org.koitharu.kotatsu.ui.components.NemuriBrandLogo
import org.koitharu.kotatsu.ui.components.NemuriGitHubUpdateBanner
import org.koitharu.kotatsu.ui.components.NemuriPrivacySecurityStore
import org.koitharu.kotatsu.ui.components.RecentsBlurMode
import org.koitharu.kotatsu.ui.screens.AiEngineSettingsModalSheet
import org.koitharu.kotatsu.ui.screens.AiStudioScreen
import org.koitharu.kotatsu.ui.screens.BackupsAndSettingsScreen
import org.koitharu.kotatsu.ui.screens.ExploreScreen
import org.koitharu.kotatsu.ui.screens.FeedScreen
import org.koitharu.kotatsu.ui.screens.HistoryScreen
import org.koitharu.kotatsu.ui.screens.LibraryScreen
import org.koitharu.kotatsu.ui.screens.MangaDetailsScreen
import org.koitharu.kotatsu.ui.screens.ReaderScreen
import org.koitharu.kotatsu.ui.screens.SourceMigrationModalSheet
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
	private val _volumeKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 16)
	val volumeKeyEvents: SharedFlow<Int> = _volumeKeyEvents.asSharedFlow()

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
		if (
			isVolumeKey &&
			viewModel.activeReaderMangaId.value != null &&
			viewModel.readerConfig.value.switchPagesWithVolumeKeys
		) {
			_volumeKeyEvents.tryEmit(keyCode)
			return true
		}
		return super.onKeyDown(keyCode, event)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
			val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
			val colorSchemePreset by viewModel.colorSchemePreset.collectAsStateWithLifecycle()
			val pureOledBlack by viewModel.pureOledBlack.collectAsStateWithLifecycle()
			val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()

			KotatsuNeoTheme(
				darkTheme = darkTheme,
				themeMode = themeMode,
				colorSchemePreset = colorSchemePreset,
				pureOledBlack = pureOledBlack,
				useDynamicColor = useDynamicColor,
			) {
				KotatsuNeoRootApp(
					viewModel = viewModel,
					volumeKeyEvents = volumeKeyEvents,
				)
			}
		}
	}
}

@Composable
fun KotatsuNeoRootApp(
	viewModel: KotatsuNeoViewModel,
	volumeKeyEvents: kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow(),
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
	val defaultFeedView by viewModel.defaultFeedView.collectAsStateWithLifecycle()
	val defaultStartupTab by viewModel.defaultStartupTab.collectAsStateWithLifecycle()
	val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
	val duplicateCandidates by viewModel.duplicateCandidates.collectAsStateWithLifecycle()
	val brokenProposals by viewModel.brokenMangaProposals.collectAsStateWithLifecycle()
	val batchRule by viewModel.batchRule.collectAsStateWithLifecycle()
	val selectedMangaDetailId by viewModel.selectedMangaDetailId.collectAsStateWithLifecycle()
	val activeReaderMangaId by viewModel.activeReaderMangaId.collectAsStateWithLifecycle()
	val activeChapterIndex by viewModel.activeChapterIndex.collectAsStateWithLifecycle()
	val activePageIndex by viewModel.activePageIndex.collectAsStateWithLifecycle()
	val isIncognitoSessionActive by viewModel.isIncognitoSessionActive.collectAsStateWithLifecycle()
	val alwaysIncognitoMangaIds by viewModel.alwaysIncognitoMangaIds.collectAsStateWithLifecycle()
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
	val aiEngineConfig by viewModel.aiEngineConfig.collectAsStateWithLifecycle()
	val showAiEngineSettingsSheet by viewModel.showAiEngineSettingsSheet.collectAsStateWithLifecycle()
	val mergedSourcesByMangaId by viewModel.mergedSourcesByMangaId.collectAsStateWithLifecycle()
	val repairAuditLog by viewModel.repairAuditLog.collectAsStateWithLifecycle()
	val aiCoreStatus by viewModel.nemuriAiCoreEngine.status.collectAsStateWithLifecycle()
	val latestGitHubUpdate by GitHubUpdateChecker.latestUpdate.collectAsStateWithLifecycle()
	val isUpdateDismissed by GitHubUpdateChecker.dismissedForSession.collectAsStateWithLifecycle()
	val recentsBlurMode by NemuriPrivacySecurityStore.recentsBlurMode.collectAsStateWithLifecycle()
	val isLockScreenShowing by NemuriPrivacySecurityStore.isLockScreenShowing.collectAsStateWithLifecycle()
	val context = LocalContext.current

	var currentTab by rememberSaveable { mutableStateOf(defaultStartupTab) }
	var isSettingsDirectOpen by rememberSaveable { mutableStateOf(false) }
	var showContinuePeekCard by remember { mutableStateOf(false) }
	var detailMigrationCandidate by remember { mutableStateOf<LibraryMangaItem?>(null) }

	// Poll GitHub em2241992/Nemuri on startup if auto-check is enabled
	LaunchedEffect(Unit) {
		if (GitHubUpdateChecker.isAutoCheckOnStartupEnabled(context)) {
			GitHubUpdateChecker.checkForUpdates(context, forceShowPrompt = false)
		}
	}

	val activeReaderManga = allItems.find { it.manga.id == activeReaderMangaId }
	val selectedDetailManga = allItems.find { it.manga.id == selectedMangaDetailId }

	// Apply WindowManager.LayoutParams.FLAG_SECURE when Incognito / 18+ manga is active or Always Blur is enabled
	val isMatureOrIncognitoContext = isIncognitoSessionActive ||
		activeReaderManga?.hasMatureIncognitoTag == true ||
		selectedDetailManga?.hasMatureIncognitoTag == true
	val shouldApplyFlagSecure = when (recentsBlurMode) {
		RecentsBlurMode.ALWAYS -> true
		RecentsBlurMode.WHEN_INCOGNITO -> isMatureOrIncognitoContext
		RecentsBlurMode.OFF -> false
	}
	LaunchedEffect(shouldApplyFlagSecure) {
		val window = (context as? Activity)?.window ?: return@LaunchedEffect
		if (shouldApplyFlagSecure) {
			window.setFlags(
				WindowManager.LayoutParams.FLAG_SECURE,
				WindowManager.LayoutParams.FLAG_SECURE,
			)
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
		}
	}

	if (isLockScreenShowing) {
		NemuriBiometricLockOverlay(
			onUnlock = { NemuriPrivacySecurityStore.unlockApp() },
		)
		return
	}

	// If Reader is explicitly opened from a manga card, show full-screen Reader
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
			volumeKeyEvents = volumeKeyEvents,
			isIncognitoSession = isIncognitoSessionActive,
		)
		return
	}

	// Dedicated Full-Screen Manga Details Page (covers 100% of viewport & hides floating bottom navigation dock)
	if (selectedDetailManga != null) {
		val brokenProposal = brokenProposals.find { it.brokenItem.manga.id == selectedDetailManga.manga.id }
		val mergedSources = mergedSourcesByMangaId[selectedDetailManga.manga.id].orEmpty()
		MangaDetailsScreen(
			item = selectedDetailManga,
			brokenProposal = brokenProposal,
			mergedSecondarySources = mergedSources,
			bookmarkedPageKeys = bookmarkedPageKeys,
			categories = categories,
			masterAiEnabled = aiEngineConfig.masterAiEnabled,
			activeAiEngineBadge = aiEngineConfig.activeAttributionBadge(),
			recapState = activeMangaRecapState,
			onBack = { viewModel.openMangaDetails(null) },
			onJumpToLibraryRoot = {
				viewModel.openMangaDetails(null)
				isSettingsDirectOpen = false
				currentTab = AppNavTab.LIBRARY
			},
			onStartReading = { chIdx ->
				viewModel.openReader(selectedDetailManga.manga.id, chIdx)
			},
			isAlwaysIncognitoForSeries = selectedDetailManga.manga.id in alwaysIncognitoMangaIds,
			autoIncognitoMatureTags = readerConfig.autoIncognitoMatureTags,
			onStartReadingWithIncognito = { chIdx, incognito ->
				viewModel.openReader(selectedDetailManga.manga.id, chIdx, incognitoOverride = incognito)
			},
			onToggleAlwaysIncognito = {
				viewModel.toggleAlwaysIncognitoForSeries(
					selectedDetailManga.manga.id,
					selectedDetailManga.manga.title,
				)
			},
			onRemoveFromHistory = {
				viewModel.removeHistoryItem(selectedDetailManga.manga.id)
			},
			onRequestRecap = { forceRegenerate ->
				viewModel.requestWhereWasIRecap(
					mangaId = selectedDetailManga.manga.id,
					forceShow = true,
					forceRegenerate = forceRegenerate,
				)
			},
			onOpenMigrateSource = {
				detailMigrationCandidate = selectedDetailManga
			},
			onRunAiFix = { proposal ->
				viewModel.runSingleAiFixIt(proposal)
			},
			onFilterByAuthor = { author ->
				viewModel.openMangaDetails(null)
				isSettingsDirectOpen = false
				currentTab = AppNavTab.LIBRARY
				viewModel.updateSearchQuery(author)
			},
			onFilterByTag = { tag ->
				viewModel.openMangaDetails(null)
				isSettingsDirectOpen = false
				currentTab = AppNavTab.LIBRARY
				viewModel.updateAdvancedFilter { it.copy(includedTags = setOf(tag)) }
			},
			onToggleLibraryMembership = { categoryId ->
				if (categoryId != null) {
					viewModel.batchMoveToCategory(setOf(selectedDetailManga.manga.id), categoryId)
				} else {
					val targetId = selectedDetailManga.manga.id
					viewModel.openMangaDetails(null)
					viewModel.batchRemoveFromLibrary(setOf(targetId))
				}
			},
			onSaveMetadataOverride = { newTitle, newAuthor, newSynopsis ->
				viewModel.updateMangaMetadataOverride(
					mangaId = selectedDetailManga.manga.id,
					newTitle = newTitle,
					newAuthor = newAuthor,
					newSynopsis = newSynopsis,
				)
			},
			onDownloadSingleChapter = { _ ->
				viewModel.batchDownloadSeries(setOf(selectedDetailManga.manga.id))
			},
			onToggleChapterBookmark = { chIdx ->
				viewModel.togglePageBookmark(
					selectedDetailManga.manga.id,
					chIdx,
					0,
					selectedDetailManga.manga.title,
				)
			},
		)

		val migTarget = detailMigrationCandidate
		if (migTarget != null) {
			val liveTarget = allItems.find { it.manga.id == migTarget.manga.id } ?: selectedDetailManga
			org.koitharu.kotatsu.ui.components.UniversalSheet(
				onClose = { detailMigrationCandidate = null },
				containerColor = Color(0xFF121620),
				sheetTestTag = "detail_source_manager_universal_sheet",
			) {
				SourceMigrationModalSheet(
					itemsToMigrate = listOf(liveTarget),
					onMigrateSingle = { mangaId, targetSource, targetChapters ->
						viewModel.migrateSingleMangaSource(mangaId, targetSource, targetChapters)
						detailMigrationCandidate = null
					},
					onMigrateAllSelected = { migrations ->
						viewModel.migrateBatchMangaSources(migrations)
						detailMigrationCandidate = null
					},
					onClose = { detailMigrationCandidate = null },
					mergedSourcesByMangaId = mergedSourcesByMangaId,
					onMergeSecondarySource = viewModel::mergeSecondarySource,
					onUnlinkMergedSource = viewModel::unlinkMergedSource,
				)
			}
		}
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
	var isGlobalIncognitoModeEnabled by rememberSaveable { mutableStateOf(false) }
	val continuableMangaItem = historyItems.firstOrNull() ?: allItems.firstOrNull()
	val quickJumpRecentReads = remember(historyItems, allItems) {
		val combined = (historyItems + allItems).distinctBy { it.manga.id }
		combined.take(2)
	}
	val launchContinueReading: (Boolean?) -> Unit = { incognitoOverride ->
		showContinuePeekCard = false
		val effectiveIncognito = incognitoOverride ?: if (isGlobalIncognitoModeEnabled) true else null
		val lastHistory = historyItems.firstOrNull()
		if (lastHistory != null) {
			viewModel.openReader(
				lastHistory.manga.id,
				(lastHistory.lastReadChapterNumber.toInt() - 1).coerceAtLeast(0),
				incognitoOverride = effectiveIncognito,
			)
		} else {
			val fallbackManga = allItems.firstOrNull()
			if (fallbackManga != null) {
				viewModel.openReader(
					fallbackManga.manga.id,
					0,
					incognitoOverride = effectiveIncognito,
				)
			}
		}
	}
	val onContinueReading: () -> Unit = {
		launchContinueReading(null)
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
									aiEnginePillLabel = aiEngineConfig.compactHeaderPillLabel(),
									onOpenAiEngineSettings = viewModel::openAiEngineSettings,
									useDynamicColor = useDynamicColor,
									onSetUseDynamicColor = viewModel::setUseDynamicColor,
									defaultFeedView = defaultFeedView,
									onSetDefaultFeedView = viewModel::setDefaultFeedView,
									defaultStartupTab = defaultStartupTab,
									onSetDefaultStartupTab = viewModel::setDefaultStartupTab,
									libraryItems = allItems,
									onMigrateSingleSource = viewModel::migrateSingleMangaSource,
									onMigrateBatchSources = viewModel::migrateBatchMangaSources,
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
											activeAiEngineBadge = aiEngineConfig.activeAttributionBadge(),
										)
									}

									AppNavTab.HISTORY -> {
										HistoryScreen(
											historyItems = historyItems,
											onResumeReading = viewModel::openReader,
											onOpenDetails = { id ->
												viewModel.openMangaDetails(id)
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
											},
											onOpenReader = viewModel::openReader,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
											onOpenAiEngineSettings = viewModel::openAiEngineSettings,
											isAiActive = aiEngineConfig.masterAiEnabled,
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
											aiEnginePillLabel = aiEngineConfig.compactHeaderPillLabel(),
											onOpenAiEngineSettings = viewModel::openAiEngineSettings,
											onOpenSettingsScreen = { isSettingsDirectOpen = true },
											isAiActive = aiEngineConfig.masterAiEnabled,
											libraryItems = allItems,
											onMigrateSingleSource = viewModel::migrateSingleMangaSource,
											onMigrateBatchSources = viewModel::migrateBatchMangaSources,
											repairAuditLog = repairAuditLog,
											onRunManualHealthScan = viewModel::runManualSourceHealthScan,
											onOpenMangaDetails = { id -> viewModel.openMangaDetails(id) },
											onRevertRepair = viewModel::revertRepairAuditEntry,
										)
									}

									AppNavTab.BACKUPS_DB -> Unit
								}
							}
						}
					}
				}

				// Bottom Toast Notification for GitHub Commit Sync
				val installToastMessage by GitHubUpdateChecker.installToastMessage.collectAsStateWithLifecycle()
				LaunchedEffect(installToastMessage) {
					if (installToastMessage != null) {
						kotlinx.coroutines.delay(3800L)
						GitHubUpdateChecker.clearInstallToast()
					}
				}
				AnimatedVisibility(
					visible = installToastMessage != null,
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.navigationBarsPadding()
						.padding(bottom = 86.dp, start = 16.dp, end = 16.dp),
				) {
					Surface(
						color = Color(0xFF133840),
						shape = RoundedCornerShape(12.dp),
						shadowElevation = 10.dp,
						modifier = Modifier
							.clickable { GitHubUpdateChecker.clearInstallToast() }
							.testTag("github_update_toast_pill"),
					) {
						Row(
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Text(
								text = installToastMessage.orEmpty(),
								style = MaterialTheme.typography.labelLarge,
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								color = Color(0xFF5CE1E6),
							)
						}
					}
				}

				// Long-Press Incognito Switcher & Quick-Jump Popup Menu above the Continue FAB (📖)
				if (showContinuePeekCard && continuableMangaItem != null && !isSettingsDirectOpen && selectedMangaDetailId == null) {
					val resumeChapterNum = continuableMangaItem.lastReadChapterNumber.toInt().coerceAtLeast(1)
					val accentCyan = Color(0xFF5CE1E6)
					Card(
						colors = CardDefaults.cardColors(
							containerColor = Color(0xFF161C22).copy(alpha = 0.98f),
						),
						shape = RoundedCornerShape(22.dp),
						elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.navigationBarsPadding()
							.padding(
								end = 16.dp,
								bottom = 78.dp,
							)
							.widthIn(min = 285.dp, max = 328.dp)
							.border(
								width = 1.dp,
								color = if (isGlobalIncognitoModeEnabled) accentCyan else Color(0xFF4CCAD0).copy(alpha = 0.45f),
								shape = RoundedCornerShape(22.dp),
							)
							.testTag("continue_peek_preview_card"),
					) {
						Column(
							modifier = Modifier.padding(14.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
						) {
							// 1. Global Incognito Switcher Row: 🕵️ Incognito Mode with instant ON / OFF pill toggle
							Surface(
								color = if (isGlobalIncognitoModeEnabled) {
									accentCyan.copy(alpha = 0.16f)
								} else {
									Color(0xFF212830)
								},
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(14.dp))
									.clickable { isGlobalIncognitoModeEnabled = !isGlobalIncognitoModeEnabled }
									.testTag("continue_fab_global_incognito_toggle_row"),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = "🕵️ Incognito Mode: ${if (isGlobalIncognitoModeEnabled) "ON" else "OFF"}",
											style = MaterialTheme.typography.titleSmall,
											fontWeight = FontWeight.Bold,
											color = if (isGlobalIncognitoModeEnabled) accentCyan else Color.White,
										)
										Text(
											text = if (isGlobalIncognitoModeEnabled) {
												"History, streaks & trackers paused"
											} else {
												"Standard reading telemetry active"
											},
											style = MaterialTheme.typography.labelSmall,
											fontSize = 10.sp,
											color = Color(0xFFA9B4BE),
										)
									}
									Surface(
										color = if (isGlobalIncognitoModeEnabled) accentCyan else Color(0xFF323C47),
										shape = RoundedCornerShape(999.dp),
										modifier = Modifier.testTag("continue_fab_incognito_pill_toggle"),
									) {
										Text(
											text = if (isGlobalIncognitoModeEnabled) "ON" else "OFF",
											fontFamily = JetBrainsMonoFamily,
											fontWeight = FontWeight.Bold,
											fontSize = 11.sp,
											color = if (isGlobalIncognitoModeEnabled) Color(0xFF061518) else Color(0xFFD5DEE5),
											modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
										)
									}
								}
							}

							// Current Manga Summary Header
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								MangaCoverArtwork(
									title = continuableMangaItem.manga.title,
									coverUrl = continuableMangaItem.manga.coverUrl,
									modifier = Modifier
										.width(42.dp)
										.aspectRatio(0.7f)
										.clip(RoundedCornerShape(8.dp)),
								)
								Column(modifier = Modifier.weight(1f)) {
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
									modifier = Modifier.size(26.dp),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Close preview",
										tint = Color(0xFFBDC7CA),
										modifier = Modifier.size(15.dp),
									)
								}
							}

							// 2. 🕵️ Continue in Incognito
							Surface(
								color = accentCyan.copy(alpha = 0.18f),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(1.dp, accentCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
									.clip(RoundedCornerShape(12.dp))
									.clickable { launchContinueReading(true) }
									.testTag("continue_fab_launch_incognito_button"),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.Center,
								) {
									Text(
										text = "🕵️ Continue in Incognito",
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.Bold,
										fontSize = 12.sp,
										color = accentCyan,
									)
								}
							}

							// 3. ▶ Continue Normally
							Surface(
								color = Color(0xFF232B34),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(12.dp))
									.clickable { launchContinueReading(false) }
									.testTag("continue_fab_launch_normal_button"),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.Center,
								) {
									Text(
										text = "▶ Continue Normally",
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.SemiBold,
										fontSize = 12.sp,
										color = Color.White,
									)
								}
							}

							// 4. Quick Jump List: Compact 2-item list of the most recent reads to switch series with one thumb
							if (quickJumpRecentReads.isNotEmpty()) {
								HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
								Text(
									text = "QUICK JUMP RECENT READS",
									fontFamily = JetBrainsMonoFamily,
									fontWeight = FontWeight.Bold,
									fontSize = 9.sp,
									color = Color(0xFF8CA0AB),
								)
								Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
									quickJumpRecentReads.forEach { recentItem ->
										val chNum = recentItem.lastReadChapterNumber.toInt().coerceAtLeast(1)
										Surface(
											color = Color(0xFF1F262E),
											shape = RoundedCornerShape(10.dp),
											modifier = Modifier
												.fillMaxWidth()
												.clip(RoundedCornerShape(10.dp))
												.clickable {
													showContinuePeekCard = false
													viewModel.openReader(
														recentItem.manga.id,
														(chNum - 1).coerceAtLeast(0),
														incognitoOverride = if (isGlobalIncognitoModeEnabled) true else null,
													)
												}
												.testTag("quick_jump_recent_${recentItem.manga.id}"),
										) {
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.padding(horizontal = 10.dp, vertical = 7.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.spacedBy(8.dp),
											) {
												MangaCoverArtwork(
													title = recentItem.manga.title,
													coverUrl = recentItem.manga.coverUrl,
													modifier = Modifier
														.width(24.dp)
														.aspectRatio(0.72f)
														.clip(RoundedCornerShape(4.dp)),
												)
												Column(modifier = Modifier.weight(1f)) {
													Text(
														text = UiSourceAndTitleFormatter.formatDisplayTitle(recentItem.manga.title),
														style = MaterialTheme.typography.labelMedium,
														fontWeight = FontWeight.SemiBold,
														color = Color.White,
														maxLines = 1,
														overflow = TextOverflow.Ellipsis,
													)
													Text(
														text = "Ch. $chNum • ${UiSourceAndTitleFormatter.formatSourceName(recentItem.manga.source)}",
														fontFamily = JetBrainsMonoFamily,
														fontSize = 9.sp,
														color = Color(0xFF9FB0BA),
													)
												}
												Text(
													text = if (isGlobalIncognitoModeEnabled) "🕵️ Read" else "▶ Read",
													fontFamily = JetBrainsMonoFamily,
													fontWeight = FontWeight.Bold,
													fontSize = 10.sp,
													color = accentCyan,
												)
											}
										}
									}
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
						isIncognitoActive = isGlobalIncognitoModeEnabled,
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
								isIncognitoActive = isGlobalIncognitoModeEnabled,
							)
						}
					}
				}
			}
		}
	}

	if (showAiEngineSettingsSheet) {
		AiEngineSettingsModalSheet(
			config = aiEngineConfig,
			isAiCoreHardwareAvailable = aiCoreStatus == NemuriAiStatus.Available,
			onUpdateConfig = viewModel::updateAiEngineConfig,
			onTestConnection = viewModel::testAiEndpointConnection,
			onDownloadLiteRtModel = viewModel::downloadLiteRtModel,
			onDismiss = viewModel::closeAiEngineSettings,
		)
	}
}

/**
 * Restored Kotatsu "Continue" Floating Button:
 * - When `iconOnly = true` (Floating Island mode):
 *   renders a rounder 56.dp circular/capsule icon-only button without words using `RoundedCornerShape(32.dp)` (the exact same border radius and height as `FloatingIslandNavigationBar`) in bright aqua (#4CCAD0) with a dark teal (#07383B) open-book icon.
 * - When `iconOnly = false` (Classic Docked mode):
 *   renders the extended floating button with the reader icon + "Continue" label above the bottom navigation bar on the right.
 * - Supports Long-Press (`onLongClick`) to open the Incognito Switcher & Quick-Jump menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KotatsuContinueFloatingButton(
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	iconOnly: Boolean,
	isIncognitoActive: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val primaryColor = MaterialTheme.colorScheme.primary
	val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
	val incognitoContainerColor = Color(0xFF19242E)
	val incognitoRingColor = Color(0xFF5CE1E6)

	if (iconOnly) {
		val islandMatchingShape = RoundedCornerShape(32.dp)
		Box(modifier = modifier.size(56.dp)) {
			Surface(
				color = if (isIncognitoActive) incognitoContainerColor else primaryColor,
				contentColor = if (isIncognitoActive) incognitoRingColor else onPrimaryColor,
				tonalElevation = 8.dp,
				shadowElevation = 14.dp,
				shape = islandMatchingShape,
				modifier = Modifier
					.fillMaxSize()
					.clip(islandMatchingShape)
					.combinedClickable(
						onClick = onClick,
						onLongClick = onLongClick,
					)
					.border(
						width = if (isIncognitoActive) 2.dp else 1.dp,
						color = if (isIncognitoActive) incognitoRingColor else primaryColor.copy(alpha = 0.65f),
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
						contentDescription = "Continue reading (Long-press for Incognito & Quick-Jump)",
						tint = if (isIncognitoActive) incognitoRingColor else onPrimaryColor,
						modifier = Modifier.size(24.dp),
					)
				}
			}
			if (isIncognitoActive) {
				Surface(
					color = incognitoRingColor,
					shape = CircleShape,
					modifier = Modifier
						.align(Alignment.TopEnd)
						.size(20.dp)
						.testTag("continue_fab_incognito_badge"),
				) {
					Box(contentAlignment = Alignment.Center) {
						Text(
							text = "🕵️",
							fontSize = 10.sp,
						)
					}
				}
			}
		}
	} else {
		val classicShape = RoundedCornerShape(18.dp)
		Surface(
			color = if (isIncognitoActive) incognitoContainerColor else MaterialTheme.colorScheme.primaryContainer,
			contentColor = if (isIncognitoActive) incognitoRingColor else MaterialTheme.colorScheme.onPrimaryContainer,
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
					width = if (isIncognitoActive) 2.dp else 1.dp,
					color = if (isIncognitoActive) incognitoRingColor else primaryColor.copy(alpha = 0.35f),
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
					contentDescription = "Continue reading (Long-press for Incognito & Quick-Jump)",
					tint = if (isIncognitoActive) incognitoRingColor else primaryColor,
					modifier = Modifier.size(22.dp),
				)
				Text(
					text = if (isIncognitoActive) "🕵️ Continue" else "Continue",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					fontSize = 15.sp,
					maxLines = 1,
					softWrap = false,
					color = if (isIncognitoActive) incognitoRingColor else MaterialTheme.colorScheme.onPrimaryContainer,
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
