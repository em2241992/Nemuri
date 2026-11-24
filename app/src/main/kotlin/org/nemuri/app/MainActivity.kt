package org.nemuri.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.core.nav.ReaderIntent
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.nemuri.app.navigation.AppDestination
import org.nemuri.app.navigation.AppTab
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.navigation.NemuriNavController
import org.nemuri.app.navigation.NemuriShellUiState
import org.nemuri.app.navigation.NemuriShellViewModel
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.reader.ReaderActivity
import org.nemuri.app.ui.bookmarks.BookmarksScreen
import org.nemuri.app.ui.components.NemuriCapsuleToastHost
import org.nemuri.app.ui.components.NemuriFloatingDockBar
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriNavigationRail
import org.nemuri.app.ui.details.MangaDetailsScreen
import org.nemuri.app.ui.downloads.DownloadsScreen
import org.nemuri.app.ui.enhance.AiSettingsScreen
import org.nemuri.app.ui.enhance.BrokenSourcesScreen
import org.nemuri.app.ui.enhance.DuplicateScannerScreen
import org.nemuri.app.ui.enhance.EnhanceHubScreen
import org.nemuri.app.ui.enhance.MigrateMangaScreen
import org.nemuri.app.ui.enhance.ReadingStatsScreen
import org.nemuri.app.ui.explore.ExploreScreen
import org.nemuri.app.ui.explore.GlobalSearchScreen
import org.nemuri.app.ui.explore.SourceBrowseScreen
import org.nemuri.app.ui.extensions.ExtensionsCatalogScreen
import org.nemuri.app.ui.feed.FeedScreen
import org.nemuri.app.ui.history.HistoryScreen
import org.nemuri.app.ui.library.LibraryScreen
import org.nemuri.app.ui.local.LocalStorageScreen
import org.nemuri.app.ui.settings.SettingsRouterScreen
import org.nemuri.app.ui.theme.NemuriTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	private var latestIntent by mutableStateOf<Intent?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		latestIntent = intent
		setContent {
			val shellViewModel: NemuriShellViewModel = viewModel()
			val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
			val navController = remember { NemuriNavController() }

			LaunchedEffect(latestIntent) {
				latestIntent?.let { incoming ->
					navController.consumeIntent(incoming)
					latestIntent = null
				}
			}

			BackHandler(
				enabled = navController.backStack.size > 1 || navController.currentTab != AppTab.LIBRARY,
			) {
				navController.popBackStack()
			}

			NemuriTheme(
				themeMode = shellState.themeMode,
				amoled = shellState.isAmoled,
				colorScheme = shellState.colorScheme,
			) {
				NemuriAppRoot(
					navController = navController,
					shellState = shellState,
					shellViewModel = shellViewModel,
				)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		latestIntent = intent
	}
}

@Composable
private fun NemuriAppRoot(
	navController: NemuriNavController,
	shellState: NemuriShellUiState,
	shellViewModel: NemuriShellViewModel,
) {
	val context = LocalContext.current
	val isWideLayout = LocalConfiguration.current.screenWidthDp >= 700
	val destination = navController.currentDestination

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background),
	) {
		Row(modifier = Modifier.fillMaxSize()) {
			if (isWideLayout && navController.showBottomDock) {
				NemuriNavigationRail(
					currentTab = navController.currentTab,
					unreadFeedCount = shellState.unreadFeedCount,
					hasExtensionUpdates = shellState.hasExtensionUpdates,
					onSelectTab = navController::selectTab,
					modifier = Modifier.fillMaxHeight(),
				)
			}

			Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
				when (destination) {
					is AppDestination.TabRoot -> when (destination.tab) {
						AppTab.LIBRARY -> LibraryScreen(
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
							},
							onOpenExplore = { navController.selectTab(AppTab.EXPLORE) },
							onOpenSettings = { navController.navigate(AppDestination.Settings()) },
						)

						AppTab.FEED -> FeedScreen(
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
							},
							onOpenChapter = { manga, chapterId ->
								MangaTransferCache.put(manga)
								context.startActivity(
									ReaderActivity.newIntent(context, manga = manga, chapterId = chapterId),
								)
							},
							onResumeReading = { manga, history ->
								MangaTransferCache.put(manga)
								context.startActivity(
									ReaderActivity.newIntent(
										context = context,
										manga = manga,
										chapterId = history.chapterId,
										page = history.page,
										scroll = history.scroll,
									),
								)
							},
							onOpenSettings = { navController.navigate(AppDestination.Settings()) },
						)

						AppTab.HISTORY -> HistoryScreen(
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
							},
							onResumeReading = { manga, history ->
								MangaTransferCache.put(manga)
								context.startActivity(
									ReaderActivity.newIntent(
										context = context,
										manga = manga,
										chapterId = history.chapterId,
										page = history.page,
										scroll = history.scroll,
									),
								)
							},
							onOpenSettings = { navController.navigate(AppDestination.Settings()) },
						)

						AppTab.EXPLORE -> ExploreScreen(
							onOpenExtensions = { navController.navigate(AppDestination.ExtensionsCatalog()) },
							onOpenLocalFiles = { navController.navigate(AppDestination.LocalStorage) },
							onOpenBookmarks = { navController.navigate(AppDestination.Bookmarks) },
							onOpenDownloads = { navController.navigate(AppDestination.Downloads) },
							onOpenSourceBrowse = { sourceName ->
								navController.navigate(AppDestination.SourceBrowse(sourceName))
							},
							onOpenGlobalSearch = { query ->
								navController.navigate(AppDestination.GlobalSearch(query))
							},
							onOpenDetails = { manga ->
								MangaTransferCache.put(manga)
								navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
							},
							onOpenSettings = { navController.navigate(AppDestination.Settings()) },
						)

						AppTab.ENHANCE -> EnhanceHubScreen(
							onOpenBrokenSources = { navController.navigate(AppDestination.BrokenSourcesWizard) },
							onOpenDuplicates = { navController.navigate(AppDestination.DuplicateScanner) },
							onOpenStats = { navController.navigate(AppDestination.ReadingStats) },
							onOpenReadingSettings = {
								navController.navigate(AppDestination.Settings(SettingsSection.READING))
							},
							onOpenAiSettings = { navController.navigate(AppDestination.AiSettings) },
							onOpenSettings = { navController.navigate(AppDestination.Settings()) },
						)
					}

					is AppDestination.ExtensionsCatalog -> ExtensionsCatalogScreen(
						initialRepoUrl = destination.initialRepoUrl,
						onBack = { navController.popBackStack() },
					)

					is AppDestination.SourceBrowse -> SourceBrowseScreen(
						sourceName = destination.sourceName,
						initialQuery = destination.initialQuery,
						onBack = { navController.popBackStack() },
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
					)

					is AppDestination.GlobalSearch -> GlobalSearchScreen(
						initialQuery = destination.initialQuery,
						onBack = { navController.popBackStack() },
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
						onOpenSourceBrowse = { sourceName, query ->
							navController.navigate(AppDestination.SourceBrowse(sourceName, query))
						},
					)

					is AppDestination.LocalStorage -> LocalStorageScreen(
						onBack = { navController.popBackStack() },
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
					)

					is AppDestination.Bookmarks -> BookmarksScreen(
						onBack = { navController.popBackStack() },
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
						onOpenBookmark = { bookmark ->
							MangaTransferCache.put(bookmark.manga)
							context.startActivity(ReaderActivity.newBookmarkIntent(context, bookmark))
						},
					)

					is AppDestination.Downloads -> DownloadsScreen(
						onBack = { navController.popBackStack() },
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
					)

					is AppDestination.ReadingStats -> ReadingStatsScreen(
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
						onBack = { navController.popBackStack() },
					)

					is AppDestination.DuplicateScanner -> DuplicateScannerScreen(
						onOpenDetails = { manga ->
							MangaTransferCache.put(manga)
							navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
						},
						onBack = { navController.popBackStack() },
					)

					is AppDestination.BrokenSourcesWizard -> BrokenSourcesScreen(
						onOpenManualMigrate = { id ->
							navController.navigate(AppDestination.MigrateManga(id))
						},
						onBack = { navController.popBackStack() },
					)

					is AppDestination.AiSettings -> AiSettingsScreen(
						onBack = { navController.popBackStack() },
					)

					is AppDestination.MigrateManga -> MigrateMangaScreen(
						mangaId = destination.mangaId,
						onMigrated = { migrated ->
							MangaTransferCache.put(migrated)
							navController.popBackStack()
							if (navController.currentDestination is AppDestination.MangaDetails) {
								navController.popBackStack()
							}
							navController.navigate(AppDestination.MangaDetails(migrated.id, migrated.source.name))
						},
						onBack = { navController.popBackStack() },
					)

					is AppDestination.MangaDetails -> MangaDetailsScreen(
						mangaId = destination.mangaId,
						sourceName = destination.sourceName,
						onBack = { navController.popBackStack() },
						onStartReader = { manga, chapterId, branch ->
							MangaTransferCache.put(manga)
							context.startActivity(
								ReaderActivity.newIntent(
									context = context,
									manga = manga,
									chapterId = chapterId,
									branch = branch,
								),
							)
						},
						onOpenDetails = { relatedManga ->
							MangaTransferCache.put(relatedManga)
							navController.navigate(AppDestination.MangaDetails(relatedManga.id, relatedManga.source.name))
						},
						onOpenSourceBrowse = { sourceName, query ->
							navController.navigate(AppDestination.SourceBrowse(sourceName, query))
						},
						onOpenMigrate = { id ->
							navController.navigate(AppDestination.MigrateManga(id))
						},
						onOpenAiSettings = {
							navController.navigate(AppDestination.AiSettings)
						},
					)

					is AppDestination.Reader -> {
						LaunchedEffect(destination.mangaId, destination.chapterId) {
							val cached = MangaTransferCache.get(destination.mangaId)
							val intent = ReaderIntent.Builder(context)
								.apply {
									if (cached != null) manga(cached) else mangaId(destination.mangaId)
									destination.chapterId?.let { chId ->
										state(ReaderState(chapterId = chId, page = 0, scroll = 0))
									}
								}
								.build()
								.intent
							context.startActivity(intent)
							navController.popBackStack()
						}
						NemuriLoadingState(message = "Opening reader…")
					}

					is AppDestination.Settings -> SettingsRouterScreen(
						section = destination.section,
						onNavigateSection = { targetSection ->
							navController.navigate(AppDestination.Settings(targetSection))
						},
						onOpenExtensionsCatalog = {
							navController.navigate(AppDestination.ExtensionsCatalog())
						},
						onBack = { navController.popBackStack() },
					)
				}
			}
		}

		if (!isWideLayout && navController.showBottomDock) {
			NemuriFloatingDockBar(
				currentTab = navController.currentTab,
				unreadFeedCount = shellState.unreadFeedCount,
				hasExtensionUpdates = shellState.hasExtensionUpdates,
				isIncognito = shellState.isIncognito,
				recentReads = shellState.recentReads,
				onSelectTab = navController::selectTab,
				onContinueReading = { manga ->
					if (manga != null) {
						MangaTransferCache.put(manga)
						context.startActivity(ReaderActivity.newIntent(context, manga))
					} else {
						shellViewModel.showToast("No reading history yet")
					}
				},
				onToggleIncognito = shellViewModel::toggleIncognito,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}

		NemuriCapsuleToastHost(
			toastManager = shellViewModel.toastManager,
			bottomPadding = if (!isWideLayout && navController.showBottomDock) 92.dp else 20.dp,
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}
}
