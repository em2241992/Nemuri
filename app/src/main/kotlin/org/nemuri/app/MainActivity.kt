package org.nemuri.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import org.nemuri.app.navigation.AppDestination
import org.nemuri.app.navigation.AppTab
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.navigation.NemuriNavController
import org.nemuri.app.navigation.NemuriShellUiState
import org.nemuri.app.navigation.NemuriShellViewModel
import org.nemuri.app.ui.components.MangaListRow
import org.nemuri.app.ui.components.NemuriBrandLogo
import org.nemuri.app.ui.components.NemuriCapsuleToastHost
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriFloatingDockBar
import org.nemuri.app.ui.components.NemuriNavigationRail
import org.nemuri.app.ui.components.NemuriTopSearchHeader
import org.nemuri.app.ui.explore.ExploreScreen
import org.nemuri.app.ui.explore.GlobalSearchScreen
import org.nemuri.app.ui.explore.SourceBrowseScreen
import org.nemuri.app.ui.extensions.ExtensionsCatalogScreen
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
					is AppDestination.TabRoot -> if (destination.tab == AppTab.EXPLORE) {
						ExploreScreen(
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
					} else {
						TabShellScreen(
							tab = destination.tab,
							shellState = shellState,
							navController = navController,
							onShowToast = shellViewModel::showToast,
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
					is AppDestination.LocalStorage -> SubRouteScaffold(
						title = "Local Storage",
						subtitle = "Phase 2/3 local CBZ, EPUB & PDF library target.",
						onBack = { navController.popBackStack() },
					)
					is AppDestination.Bookmarks -> SubRouteScaffold(
						title = "Bookmarks",
						subtitle = "Phase 3 bookmarks target.",
						onBack = { navController.popBackStack() },
					)
					is AppDestination.Downloads -> SubRouteScaffold(
						title = "Downloads",
						subtitle = "Phase 3 DownloadWorker queue & offline controls.",
						onBack = { navController.popBackStack() },
					)
					is AppDestination.MangaDetails -> {
						val cached = MangaTransferCache.get(destination.mangaId)
						SubRouteScaffold(
							title = cached?.title ?: "Manga #${destination.mangaId}",
							subtitle = "Source: ${destination.sourceName ?: cached?.source?.name ?: "Database"} — Phase 3 Details target.",
							onBack = { navController.popBackStack() },
						)
					}
					is AppDestination.Reader -> SubRouteScaffold(
						title = "Reader",
						subtitle = "Manga #${destination.mangaId} — Phase 3 Manga/Novel/EPUB/PDF Reader target.",
						onBack = { navController.popBackStack() },
					)
					is AppDestination.Settings -> SubRouteScaffold(
						title = destination.section.title,
						subtitle = "Phase 4 Settings & Backup/Sync hierarchy.",
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
						navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
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

@Composable
private fun TabShellScreen(
	tab: AppTab,
	shellState: NemuriShellUiState,
	navController: NemuriNavController,
	onShowToast: (String) -> Unit,
) {
	var searchQuery by rememberSaveable(tab.name) { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxSize()) {
		NemuriTopSearchHeader(
			query = searchQuery,
			onQueryChange = { searchQuery = it },
			placeholder = when (tab) {
				AppTab.LIBRARY -> "Search library"
				AppTab.HISTORY -> "Search history"
				AppTab.FEED -> "Search feed"
				AppTab.EXPLORE -> "Search across all sources…"
				AppTab.ENHANCE -> "Search enhancements"
			},
			onSearchSubmit = {
				if (tab == AppTab.EXPLORE && searchQuery.isNotBlank()) {
					navController.navigate(AppDestination.GlobalSearch(searchQuery.trim()))
				} else if (searchQuery.isNotBlank()) {
					onShowToast("Search filter ready in Phase 2/3")
				}
			},
			pillTrailingIcon = if (tab == AppTab.FEED) Icons.Default.Refresh else null,
			pillTrailingContentDescription = if (tab == AppTab.FEED) "Refresh feed" else null,
			onPillTrailingClick = if (tab == AppTab.FEED) {
				{ onShowToast("Feed sync wires in Phase 3") }
			} else {
				null
			},
			onTuneClick = if (tab == AppTab.LIBRARY || tab == AppTab.HISTORY || tab == AppTab.FEED) {
				{ onShowToast("${tab.label} display options arrive in Phase 3") }
			} else {
				null
			},
			onSettingsClick = { navController.navigate(AppDestination.Settings()) },
		)

		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			item {
				ShellHeroCard(
					tab = tab,
					shellState = shellState,
					onShowToast = onShowToast,
				)
			}

			when (tab) {
				AppTab.LIBRARY -> {
					if (shellState.libraryCount == 0 && shellState.recentReads.isEmpty()) {
						item {
							NemuriEmptyState(
								icon = Icons.Default.LocalLibrary,
								title = "Your library is empty",
								subtitle = "Install extensions or import local CBZ/EPUB/PDF files in Explore once Phase 2 lands.",
								actionLabel = "Go to Explore",
								onAction = { navController.selectTab(AppTab.EXPLORE) },
							)
						}
					} else {
						item {
							SectionLabel("RECENT ENGINE ENTRIES")
						}
						items(shellState.recentReads, key = { it.id }) { manga ->
							MangaListRow(
								manga = manga,
								subtitle = manga.source.name,
								onClick = {
									MangaTransferCache.put(manga)
									navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
								},
							)
						}
					}
				}
				AppTab.HISTORY -> {
					if (shellState.recentReads.isEmpty()) {
						item {
							NemuriEmptyState(
								icon = Icons.Default.History,
								title = "No reading history yet",
								subtitle = "Chapters you read will sync here with Room & scrobblers.",
							)
						}
					} else {
						item { SectionLabel("RECENTLY READ") }
						items(shellState.recentReads, key = { it.id }) { manga ->
							MangaListRow(
								manga = manga,
								subtitle = manga.source.name,
								onClick = {
									MangaTransferCache.put(manga)
									navController.navigate(AppDestination.MangaDetails(manga.id, manga.source.name))
								},
							)
						}
					}
				}
				AppTab.FEED -> {
					item {
						NemuriEmptyState(
							icon = Icons.Default.RssFeed,
							title = "Updates Feed",
							subtitle = if (shellState.unreadFeedCount == 0) {
								"No unread chapter updates yet."
							} else {
								"${shellState.unreadFeedCount} unread updates tracked in Room."
							},
						)
					}
				}
				AppTab.EXPLORE -> Unit
				AppTab.ENHANCE -> {
					item {
						NemuriEmptyState(
							icon = Icons.Default.AutoAwesome,
							title = "Enhance Hub — Reserved for Phase 5",
							subtitle = "Deduplication, Neko multi-source merge, Repair, Upscaler, and AI Recaps will be planned after Phases 1–4.",
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ShellHeroCard(
	tab: AppTab,
	shellState: NemuriShellUiState,
	onShowToast: (String) -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(18.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(18.dp)),
	) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				NemuriBrandLogo(modifier = Modifier.size(36.dp))
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "Nemuri • ${tab.label}",
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
					)
					Text(
						text = "Room v${shellState.roomSchemaVersion} • ${BuildConfig.FLAVOR} (${BuildConfig.VERSION_NAME})",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
				}
				OutlinedButton(
					onClick = { onShowToast("Obsidian capsule toast verified") },
					shape = RoundedCornerShape(10.dp),
				) {
					Text("Toast", maxLines = 1)
				}
			}

			HorizontalDivider(color = NemuriTheme.colors.border)

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				MetricItem("Library", shellState.libraryCount.toString())
				MetricItem("Sources", shellState.enabledSourcesCount.toString())
				MetricItem(
					"Ext / LN",
					"${shellState.installedExtensionsCount}/${shellState.installedLnPluginsCount}",
				)
				MetricItem("Stores", shellState.extensionStoresCount.toString())
				MetricItem("Drive", if (shellState.syncAvailable) "On" else "F-Droid")
			}
		}
	}
}

@Composable
private fun MetricItem(label: String, value: String) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Text(
			text = value,
			style = MaterialTheme.typography.titleSmall,
			color = NemuriTheme.colors.textPrimary,
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = NemuriTheme.colors.textMuted,
		)
	}
}

@Composable
private fun SectionLabel(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		color = NemuriTheme.colors.cyan,
		modifier = Modifier.padding(top = 4.dp),
	)
}

@Composable
private fun SubRouteScaffold(
	title: String,
	subtitle: String,
	onBack: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background)
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				color = NemuriTheme.colors.textPrimary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		HorizontalDivider(color = NemuriTheme.colors.border)
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(24.dp),
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodyMedium,
					color = NemuriTheme.colors.textMuted,
				)
				Button(
					onClick = onBack,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
				) {
					Text("Go Back")
				}
			}
		}
	}
}
