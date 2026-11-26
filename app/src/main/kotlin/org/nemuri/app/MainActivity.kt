package org.nemuri.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
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
import org.nemuri.app.ui.components.LocalLayoutStudioPrefs
import org.nemuri.app.ui.components.NemuriCapsuleToastHost
import org.nemuri.app.ui.components.NemuriFloatingDockBar
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NemuriNavigationRail
import org.nemuri.app.ui.components.rememberObservedLayoutStudioPrefs
import org.nemuri.app.ui.components.rememberRootTabBottomPadding
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
import org.nemuri.app.ui.theme.LocalNavAnimatedVisibilityScope
import org.nemuri.app.ui.theme.LocalReducedMotion
import org.nemuri.app.ui.theme.LocalSharedTransitionScope
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriEffectsSpring
import org.nemuri.app.ui.theme.nemuriSpatialSpring

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

			LaunchedEffect(Unit) {
				withFrameNanos { }
				(applicationContext as? NemuriApp)?.ensureExtensionsInitialized()
			}

			LaunchedEffect(latestIntent) {
				latestIntent?.let { incoming ->
					navController.consumeIntent(incoming)
					latestIntent = null
				}
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NemuriAppRoot(
	navController: NemuriNavController,
	shellState: NemuriShellUiState,
	shellViewModel: NemuriShellViewModel,
) {
	val context = LocalContext.current
	val isWideLayout = LocalConfiguration.current.screenWidthDp >= 700
	val destination = navController.currentDestination
	val layoutPrefs = rememberObservedLayoutStudioPrefs()
	val baseDensity = LocalDensity.current
	val scaleFactor = (layoutPrefs.uiScalePercent / 100f).coerceIn(0.75f, 1.25f)
	val scaledDensity = remember(baseDensity, scaleFactor) {
		Density(
			density = baseDensity.density * scaleFactor,
			fontScale = baseDensity.fontScale,
		)
	}
	val reduceMotion = LocalReducedMotion.current
	val saveableStateHolder = rememberSaveableStateHolder()
	var backGestureProgress by remember { mutableFloatStateOf(0f) }

	PredictiveBackHandler(
		enabled = navController.backStack.size > 1 || navController.currentTab != AppTab.LIBRARY,
	) { progressFlow ->
		try {
			progressFlow.collect { backEvent ->
				if (!reduceMotion) {
					backGestureProgress = backEvent.progress.coerceIn(0f, 1f)
				}
			}
			backGestureProgress = 0f
			navController.popBackStack()
		} catch (_: CancellationException) {
			backGestureProgress = 0f
		}
	}

	SharedTransitionLayout {
		CompositionLocalProvider(
			LocalSharedTransitionScope provides this,
			LocalLayoutStudioPrefs provides layoutPrefs,
			LocalDensity provides scaledDensity,
		) {
			val dockToastBottom = if (!isWideLayout && navController.showBottomDock) {
				rememberRootTabBottomPadding(hasBottomDock = true, extraBottom = 4.dp)
			} else {
				20.dp
			}

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

					Box(
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.graphicsLayer {
								val progress = backGestureProgress
								if (progress > 0f) {
									val s = 1f - progress * 0.06f
									scaleX = s
									scaleY = s
									translationX = progress * 42.dp.toPx()
									alpha = 1f - progress * 0.12f
								}
							},
					) {
						AnimatedContent(
							targetState = destination,
							transitionSpec = {
								when {
									reduceMotion -> {
										fadeIn(tween(0)) togetherWith fadeOut(tween(0))
									}

									initialState is AppDestination.TabRoot && targetState is AppDestination.TabRoot -> {
										fadeIn(nemuriEffectsSpring()) togetherWith fadeOut(nemuriEffectsSpring())
									}

									targetState !is AppDestination.TabRoot && initialState is AppDestination.TabRoot -> {
										(slideInHorizontally(nemuriSpatialSpring()) { it / 6 } + fadeIn(nemuriEffectsSpring())) togetherWith
											fadeOut(nemuriEffectsSpring())
									}

									initialState !is AppDestination.TabRoot && targetState is AppDestination.TabRoot -> {
										fadeIn(nemuriEffectsSpring()) togetherWith
											(slideOutHorizontally(nemuriSpatialSpring()) { it / 6 } + fadeOut(nemuriEffectsSpring()))
									}

									else -> {
										fadeIn(nemuriEffectsSpring()) togetherWith fadeOut(nemuriEffectsSpring())
									}
								}
							},
							label = "nemuriNavTransition",
						) { targetDest ->
							CompositionLocalProvider(
								LocalNavAnimatedVisibilityScope provides this@AnimatedContent,
							) {
								val stateKey = remember(targetDest) {
									if (targetDest is AppDestination.TabRoot) {
										"tab-${targetDest.tab.name}"
									} else {
										"dest-${targetDest.hashCode()}"
									}
								}
								saveableStateHolder.SaveableStateProvider(stateKey) {
									when (targetDest) {
										is AppDestination.TabRoot -> when (targetDest.tab) {
											AppTab.LIBRARY -> LibraryScreen(
												onOpenDetails = { manga, sharedKey ->
													MangaTransferCache.put(manga)
													navController.navigate(
														AppDestination.MangaDetails(manga.id, manga.source.name, sharedKey),
													)
												},
												onOpenMigrate = { mangaId ->
													navController.navigate(AppDestination.MigrateManga(mangaId))
												},
												onOpenExplore = { navController.selectTab(AppTab.EXPLORE) },
												onOpenSettings = { navController.navigate(AppDestination.Settings()) },
											)

											AppTab.FEED -> FeedScreen(
												onOpenDetails = { manga, sharedKey ->
													MangaTransferCache.put(manga)
													navController.navigate(
														AppDestination.MangaDetails(manga.id, manga.source.name, sharedKey),
													)
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
												onOpenDetails = { manga, sharedKey ->
													MangaTransferCache.put(manga)
													navController.navigate(
														AppDestination.MangaDetails(manga.id, manga.source.name, sharedKey),
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
												onOpenDetails = { manga, sharedKey ->
													MangaTransferCache.put(manga)
													navController.navigate(
														AppDestination.MangaDetails(manga.id, manga.source.name, sharedKey),
													)
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
											initialRepoUrl = targetDest.initialRepoUrl,
											onBack = { navController.popBackStack() },
										)

										is AppDestination.SourceBrowse -> SourceBrowseScreen(
											sourceName = targetDest.sourceName,
											initialQuery = targetDest.initialQuery,
											onBack = { navController.popBackStack() },
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "browse-${manga.id}"),
												)
											},
											onFindAlternative = { query ->
												navController.navigate(AppDestination.GlobalSearch(query))
											},
										)

										is AppDestination.GlobalSearch -> GlobalSearchScreen(
											initialQuery = targetDest.initialQuery,
											onBack = { navController.popBackStack() },
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "search-${manga.id}"),
												)
											},
											onOpenSourceBrowse = { sourceName, query ->
												navController.navigate(AppDestination.SourceBrowse(sourceName, query))
											},
										)

										is AppDestination.LocalStorage -> LocalStorageScreen(
											onBack = { navController.popBackStack() },
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "local-${manga.id}"),
												)
											},
										)

										is AppDestination.Bookmarks -> BookmarksScreen(
											onBack = { navController.popBackStack() },
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "bookmark-${manga.id}"),
												)
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
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "dl-${manga.id}"),
												)
											},
										)

										is AppDestination.ReadingStats -> ReadingStatsScreen(
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "stats-${manga.id}"),
												)
											},
											onBack = { navController.popBackStack() },
										)

										is AppDestination.DuplicateScanner -> DuplicateScannerScreen(
											onOpenDetails = { manga ->
												MangaTransferCache.put(manga)
												navController.navigate(
													AppDestination.MangaDetails(manga.id, manga.source.name, "dup-${manga.id}"),
												)
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
											mangaId = targetDest.mangaId,
											onMigrated = { migrated ->
												MangaTransferCache.put(migrated)
												navController.popBackStack()
												if (navController.currentDestination is AppDestination.MangaDetails) {
													navController.popBackStack()
												}
												navController.navigate(
													AppDestination.MangaDetails(migrated.id, migrated.source.name),
												)
											},
											onBack = { navController.popBackStack() },
										)

										is AppDestination.MangaDetails -> MangaDetailsScreen(
											mangaId = targetDest.mangaId,
											sourceName = targetDest.sourceName,
											sharedCoverKey = targetDest.sharedCoverKey,
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
												navController.navigate(
													AppDestination.MangaDetails(
														relatedManga.id,
														relatedManga.source.name,
														"related-${relatedManga.id}",
													),
												)
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
											LaunchedEffect(targetDest.mangaId, targetDest.chapterId) {
												val cached = MangaTransferCache.get(targetDest.mangaId)
												val intent = ReaderIntent.Builder(context)
													.apply {
														if (cached != null) manga(cached) else mangaId(targetDest.mangaId)
														targetDest.chapterId?.let { chId ->
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
											section = targetDest.section,
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
						}
					}
				}

				AnimatedVisibility(
					visible = !isWideLayout && navController.showBottomDock,
					enter = slideInVertically(nemuriSpatialSpring()) { it / 2 } + fadeIn(nemuriEffectsSpring()),
					exit = slideOutVertically(nemuriSpatialSpring()) { it / 2 } + fadeOut(nemuriEffectsSpring()),
					modifier = Modifier.align(Alignment.BottomCenter),
				) {
					NemuriFloatingDockBar(
						currentTab = navController.currentTab,
						unreadFeedCount = shellState.unreadFeedCount,
						hasExtensionUpdates = shellState.hasExtensionUpdates,
						isIncognito = shellState.isIncognito,
						recentReads = shellState.recentReads,
						isCompactHeight = layoutPrefs.isNavBarCompact,
						labelVisibility = layoutPrefs.navLabelVisibility,
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
					)
				}

				NemuriCapsuleToastHost(
					toastManager = shellViewModel.toastManager,
					bottomPadding = dockToastBottom,
					modifier = Modifier.align(Alignment.BottomCenter),
				)
			}
		}
	}
}
