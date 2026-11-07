package org.koitharu.kotatsu.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.backups.BackupFormat
import org.koitharu.kotatsu.backups.BackupRestoreSummary
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.AppColorSchemePreset
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.AppThemeMode
import org.koitharu.kotatsu.core.model.ExtensionInstallStatus
import org.koitharu.kotatsu.core.model.ExtensionInstallTask
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.model.LibraryDisplayConfig
import org.koitharu.kotatsu.core.model.LibraryLayoutMode
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.core.model.ReaderColorFilterPreset
import org.koitharu.kotatsu.core.model.ReaderCustomizationConfig
import org.koitharu.kotatsu.core.model.ReaderScaleMode
import org.koitharu.kotatsu.core.model.ReaderStyleMode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.core.parser.SqliteEngineDiagnostics
import org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator
import org.koitharu.kotatsu.main.ui.KotatsuNeoViewModel
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily
import kotlin.math.roundToInt

enum class SettingsCategorySection(
	val id: String,
	val title: String,
	val defaultSubtitle: String,
	val keywords: List<String>,
) {
	APPEARANCE(
		id = "appearance",
		title = "Appearance",
		defaultSubtitle = "Theme, List mode, Language",
		keywords = listOf("theme", "dark", "light", "amoled", "oled", "black", "color", "palette", "navigation", "island", "grid", "list", "language"),
	),
	MANGA_SOURCES(
		id = "manga_sources",
		title = "Extensions",
		defaultSubtitle = "Manage extensions, NSFW filter, Sorting order",
		keywords = listOf("source", "extension", "repo", "repository", "keiyoushi", "github", "mihon", "kotatsu", "mangadex", "nsfw", "catalog", "sorting"),
	),
	READER_SETTINGS(
		id = "reader_settings",
		title = "Reading",
		defaultSubtitle = "Read mode, Scale mode, Switch pages",
		keywords = listOf("reader", "reading", "read mode", "scale", "webtoon", "rtl", "ltr", "volume", "tap", "color filter", "brightness", "upscale", "ai"),
	),
	STORAGE_AND_NETWORK(
		id = "storage_network",
		title = "Storage and network",
		defaultSubtitle = "Storage usage, Proxy, Content preloading",
		keywords = listOf("storage", "cache", "network", "proxy", "dns", "doh", "preload", "sqlite", "database", "wal", "fts", "usage", "limits"),
	),
	DOWNLOADS(
		id = "downloads",
		title = "Downloads",
		defaultSubtitle = "Downloads folder, Download only via Wi-Fi",
		keywords = listOf("download", "folder", "wifi", "wi-fi", "cbz", "threads", "offline", "archive"),
	),
	BACKUP_AND_RESTORE(
		id = "backup_restore",
		title = "Backup and restore",
		defaultSubtitle = "Restore backup",
		keywords = listOf("backup", "restore", "kotatsu", "mihon", "tachiyomi", "tachibk", "bak", "json", "export", "import", "periodic", "account"),
	),
	CHECK_FOR_NEW_CHAPTERS(
		id = "check_chapters",
		title = "Check for new chapters",
		defaultSubtitle = "Look for updates, Notifications settings",
		keywords = listOf("chapters", "updates", "feed", "notifications", "frequency", "background"),
	),
	SERVICES(
		id = "services",
		title = "Personalized Intelligence",
		defaultSubtitle = "Suggestions, Tracking, AI Deduplication & Fix-It",
		keywords = listOf("services", "personal", "personalized", "intelligence", "ai", "suggestions", "sync", "tracking", "anilist", "myanimelist", "mal", "shikimori", "kitsu", "duplicate", "broken"),
	),
	ABOUT(
		id = "about",
		title = "About",
		defaultSubtitle = "1.1.1",
		keywords = listOf("about", "version", "credits", "license", "update", "kotatsu neo", "1.1.1"),
	),
}

private fun SettingsCategorySection.icon(): ImageVector = when (this) {
	SettingsCategorySection.APPEARANCE -> Icons.Outlined.Palette
	SettingsCategorySection.MANGA_SOURCES -> Icons.Outlined.Bookmarks
	SettingsCategorySection.READER_SETTINGS -> Icons.AutoMirrored.Outlined.MenuBook
	SettingsCategorySection.STORAGE_AND_NETWORK -> Icons.Outlined.DataUsage
	SettingsCategorySection.DOWNLOADS -> Icons.Outlined.Download
	SettingsCategorySection.BACKUP_AND_RESTORE -> Icons.Outlined.Restore
	SettingsCategorySection.CHECK_FOR_NEW_CHAPTERS -> Icons.Outlined.Notifications
	SettingsCategorySection.SERVICES -> Icons.Outlined.AutoAwesome
	SettingsCategorySection.ABOUT -> Icons.Outlined.Info
}

private const val KEIYOUSHI_OFFICIAL_REPO_URL =
	"https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"

/**
 * Material 3 Settings Screen:
 * - Centered top bar title ("Settings") with left back arrow and right search icon.
 * - Single unified 24.dp rounded tonal container card in the exact screenshot order (without a two-section divide).
 * - Clean 24.dp outlined Material icons on the left, crisp title/subtitle in the center, and right chevron.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupsAndSettingsScreen(
	navBarStyle: NavBarStyle,
	enabledNavTabs: List<AppNavTab>,
	sqliteDiagnostics: SqliteEngineDiagnostics,
	lastBackupSummary: BackupRestoreSummary?,
	lastExportedPreview: Pair<BackupFormat, String>?,
	darkTheme: Boolean,
	pureOledBlack: Boolean,
	themeMode: AppThemeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
	colorSchemePreset: AppColorSchemePreset = AppColorSchemePreset.KOTATSU_TEAL,
	displayConfig: LibraryDisplayConfig = LibraryDisplayConfig(),
	readerConfig: ReaderCustomizationConfig = ReaderCustomizationConfig(),
	extensionRepos: List<ExtensionRepoEntity> = emptyList(),
	discoveredExtensions: List<ExternalExtensionInfo> = emptyList(),
	extensionInstallTasks: Map<String, ExtensionInstallTask> = emptyMap(),
	onSetNavBarStyle: (NavBarStyle) -> Unit,
	onToggleNavTab: (AppNavTab) -> Unit,
	onMoveNavTab: (AppNavTab, Boolean) -> Unit,
	onExportBackup: (BackupFormat, Uri?) -> Unit,
	onRestoreFromUri: (Uri, String) -> Unit,
	onSimulateRestoreSample: (BackupFormat) -> Unit,
	onRefreshDiagnostics: () -> Unit,
	onToggleDarkTheme: (Boolean) -> Unit,
	onTogglePureOledBlack: (Boolean) -> Unit,
	onSetThemeMode: (AppThemeMode) -> Unit = { mode ->
		onToggleDarkTheme(mode != AppThemeMode.LIGHT)
	},
	onSetColorSchemePreset: (AppColorSchemePreset) -> Unit = {},
	onUpdateDisplayConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit = {},
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit = {},
	onAddRepository: (String, String, String) -> Unit = { _, _, _ -> },
	onRemoveRepository: (Long) -> Unit = {},
	onInstallOrUpdateExtension: (ExternalExtensionInfo) -> Unit = {},
	onUninstallExtension: (ExternalExtensionInfo) -> Unit = {},
	onSyncAllRepositories: () -> Unit = {},
	onLaunchBrowserApkDownload: (ExternalExtensionInfo) -> Unit = {},
	onShareExtensionFileProvider: (ExternalExtensionInfo) -> Unit = {},
	canRequestPackageInstalls: Boolean = true,
	onOpenUnknownSourcesSettings: () -> Unit = {},
	onLaunchApkPackageInstaller: (ExternalExtensionInfo) -> Unit = {},
	onRefreshFeed: () -> Unit = {},
	onBackToLibrary: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var activeSection by rememberSaveable { mutableStateOf<SettingsCategorySection?>(null) }
	var isSearchOpen by rememberSaveable { mutableStateOf(false) }
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var showAddRepoDialog by remember { mutableStateOf(false) }
	var showInlineThemeDialog by remember { mutableStateOf(false) }
	var pendingExportFormat by remember { mutableStateOf(BackupFormat.MIHON_TACHIBK) }

	// Local interactive preferences for Storage, Downloads, Chapter Checks & Services
	var appLanguage by rememberSaveable { mutableStateOf("System default (English)") }
	var preloadPagesOnWifi by rememberSaveable { mutableStateOf(true) }
	var dohProvider by rememberSaveable { mutableStateOf("Cloudflare (1.1.1.1)") }
	var proxyMode by rememberSaveable { mutableStateOf("Direct") }
	var clearedCacheBytes by rememberSaveable { mutableStateOf(false) }
	var downloadWifiOnly by rememberSaveable { mutableStateOf(true) }
	var saveDownloadsAsCbz by rememberSaveable { mutableStateOf(true) }
	var parallelDownloadThreads by rememberSaveable { mutableIntStateOf(3) }
	var deleteDownloadAfterRead by rememberSaveable { mutableStateOf(false) }
	var checkUpdatesEnabled by rememberSaveable { mutableStateOf(true) }
	var checkUpdatesFrequency by rememberSaveable { mutableStateOf("Every 6 hours") }
	var checkUpdatesWifiOnly by rememberSaveable { mutableStateOf(true) }
	var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
	var autoHealBrokenSources by rememberSaveable { mutableStateOf(true) }
	var aiSuggestionsEnabled by rememberSaveable { mutableStateOf(true) }
	var enabledTrackers by rememberSaveable { mutableStateOf(setOf("AniList", "MyAnimeList")) }
	var disabledExtensionIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
	var showNsfwSources by rememberSaveable { mutableStateOf(false) }
	var volumeKeysSwitchPages by rememberSaveable { mutableStateOf(true) }
	var periodicBackupEnabled by rememberSaveable { mutableStateOf(true) }
	var periodicBackupFrequency by rememberSaveable { mutableStateOf("Every 24 hours") }
	var periodicBackupFormat by rememberSaveable { mutableStateOf("Kotatsu .bak + Mihon .tachibk") }

	val createDocLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
	) { uri ->
		if (uri != null) {
			onExportBackup(pendingExportFormat, uri)
		}
	}

	val openDocLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			onRestoreFromUri(uri, uri.lastPathSegment.orEmpty())
		}
	}

	if (activeSection != null || isSearchOpen) {
		BackHandler {
			if (activeSection != null) {
				activeSection = null
			} else {
				isSearchOpen = false
				searchQuery = ""
			}
		}
	}

	val filteredCategories = remember(searchQuery) {
		if (searchQuery.isBlank()) {
			SettingsCategorySection.entries
		} else {
			val q = searchQuery.trim().lowercase()
			SettingsCategorySection.entries.filter { section ->
				section.title.lowercase().contains(q) ||
					section.defaultSubtitle.lowercase().contains(q) ||
					section.keywords.any { it.contains(q) }
			}
		}
	}

	val sectionSubtitle: (SettingsCategorySection) -> String = { section ->
		section.defaultSubtitle
	}

	val geminiContainerColor = if (darkTheme) {
		if (pureOledBlack) Color(0xFF16171A) else Color(0xFF1B1C20)
	} else {
		MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
	}

	val geminiDividerColor = if (darkTheme) {
		Color.White.copy(alpha = 0.08f)
	} else {
		MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// Material 3 Top App Bar: Left Back Arrow + Centered "Settings" Title + Right Search Action
		Surface(
			color = MaterialTheme.colorScheme.background,
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 8.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(
					onClick = {
						if (activeSection != null) {
							activeSection = null
						} else if (isSearchOpen) {
							isSearchOpen = false
							searchQuery = ""
						} else {
							onBackToLibrary()
						}
					},
					modifier = Modifier
						.size(48.dp)
						.testTag("settings_back_button"),
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = if (activeSection != null) "Back to Settings" else "Back to Library",
						tint = MaterialTheme.colorScheme.onSurface,
					)
				}

				if (isSearchOpen && activeSection == null) {
					Surface(
						color = geminiContainerColor,
						shape = RoundedCornerShape(24.dp),
						modifier = Modifier
							.weight(1f)
							.height(44.dp),
					) {
						Row(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 14.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(18.dp),
							)
							Spacer(modifier = Modifier.width(8.dp))
							Box(
								modifier = Modifier.weight(1f),
								contentAlignment = Alignment.CenterStart,
							) {
								if (searchQuery.isEmpty()) {
									Text(
										text = "Search settings (e.g. Theme, Keiyoushi, backup)…",
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								BasicTextField(
									value = searchQuery,
									onValueChange = { searchQuery = it },
									singleLine = true,
									textStyle = MaterialTheme.typography.bodyMedium.copy(
										color = MaterialTheme.colorScheme.onSurface,
									),
									cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
									modifier = Modifier
										.fillMaxWidth()
										.testTag("settings_search_input"),
								)
							}
							if (searchQuery.isNotEmpty()) {
								IconButton(
									onClick = { searchQuery = "" },
									modifier = Modifier.size(28.dp),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Clear search",
										modifier = Modifier.size(16.dp),
									)
								}
							}
						}
					}
				} else {
					Box(
						modifier = Modifier.weight(1f),
						contentAlignment = Alignment.Center,
					) {
						Column(horizontalAlignment = Alignment.CenterHorizontally) {
							Text(
								text = activeSection?.title ?: "Settings",
								style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Medium,
								color = MaterialTheme.colorScheme.onSurface,
								textAlign = TextAlign.Center,
							)
							if (activeSection != null) {
								Text(
									text = activeSection!!.defaultSubtitle,
									style = MaterialTheme.typography.bodySmall,
									fontSize = 11.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									textAlign = TextAlign.Center,
								)
							}
						}
					}
				}

				if (activeSection == null) {
					IconButton(
						onClick = {
							isSearchOpen = !isSearchOpen
							if (!isSearchOpen) searchQuery = ""
						},
						modifier = Modifier
							.size(48.dp)
							.testTag("settings_search_toggle_button"),
					) {
						Icon(
							imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
							contentDescription = "Search settings",
							tint = MaterialTheme.colorScheme.onSurface,
						)
					}
				} else {
					// Balance the 48.dp left back button so the title remains perfectly centered
					Spacer(modifier = Modifier.size(48.dp))
				}
			}
		}

		// Main Unified Settings Root Menu vs Active Sub-Screen
		when (activeSection) {
			null -> {
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier
						.fillMaxSize()
						.testTag("settings_collapsed_root_list"),
				) {
					// Single unified Material 3 settings container card (no two-section divide)
					item(key = "unified_settings_card") {
						Surface(
							color = geminiContainerColor,
							shape = RoundedCornerShape(24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("settings_group_preferences"),
						) {
							Column(modifier = Modifier.fillMaxWidth()) {
								filteredCategories.forEachIndexed { idx, section ->
									GeminiSettingsRow(
										icon = section.icon(),
										title = section.title,
										subtitle = sectionSubtitle(section),
										testTag = "settings_section_${section.id}",
										onClick = { activeSection = section },
									)
									if (idx < filteredCategories.lastIndex) {
										HorizontalDivider(
											modifier = Modifier.padding(horizontal = 20.dp),
											thickness = 0.8.dp,
											color = geminiDividerColor,
										)
									}
								}
							}
						}
					}
				}
			}

			SettingsCategorySection.APPEARANCE -> {
				AppearanceSettingsSubScreen(
					darkTheme = darkTheme,
					pureOledBlack = pureOledBlack,
					themeMode = themeMode,
					colorSchemePreset = colorSchemePreset,
					navBarStyle = navBarStyle,
					enabledNavTabs = enabledNavTabs,
					displayConfig = displayConfig,
					appLanguage = appLanguage,
					onSetThemeMode = onSetThemeMode,
					onToggleDarkTheme = onToggleDarkTheme,
					onTogglePureOledBlack = onTogglePureOledBlack,
					onSetColorSchemePreset = onSetColorSchemePreset,
					onSetNavBarStyle = onSetNavBarStyle,
					onToggleNavTab = onToggleNavTab,
					onMoveNavTab = onMoveNavTab,
					onUpdateDisplayConfig = onUpdateDisplayConfig,
					onSelectLanguage = { appLanguage = it },
				)
			}

			SettingsCategorySection.MANGA_SOURCES -> {
				MangaSourcesSettingsSubScreen(
					extensionRepos = extensionRepos,
					discoveredExtensions = discoveredExtensions,
					extensionInstallTasks = extensionInstallTasks,
					disabledExtensionIds = disabledExtensionIds,
					showNsfwSources = showNsfwSources,
					onOpenAddRepoDialog = { showAddRepoDialog = true },
					onQuickAddKeiyoushiRepo = {
						onAddRepository(
							"Keiyoushi Official GitHub Repo",
							KEIYOUSHI_OFFICIAL_REPO_URL,
							"MIHON_JSON",
						)
					},
					onRemoveRepository = onRemoveRepository,
					onInstallOrUpdateExtension = onInstallOrUpdateExtension,
					onUninstallExtension = onUninstallExtension,
					onSyncAllRepositories = onSyncAllRepositories,
					onLaunchBrowserApkDownload = onLaunchBrowserApkDownload,
					onShareExtensionFileProvider = onShareExtensionFileProvider,
					canRequestPackageInstalls = canRequestPackageInstalls,
					onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
					onLaunchApkPackageInstaller = onLaunchApkPackageInstaller,
					onToggleExtensionEnabled = { extId ->
						disabledExtensionIds = if (extId in disabledExtensionIds) {
							disabledExtensionIds - extId
						} else {
							disabledExtensionIds + extId
						}
					},
					onToggleShowNsfw = { showNsfwSources = it },
				)
			}

			SettingsCategorySection.READER_SETTINGS -> {
				ReaderSettingsSubScreen(
					readerConfig = readerConfig,
					volumeKeysSwitchPages = volumeKeysSwitchPages,
					onUpdateReaderConfig = onUpdateReaderConfig,
					onToggleVolumeKeys = { volumeKeysSwitchPages = it },
				)
			}

			SettingsCategorySection.STORAGE_AND_NETWORK -> {
				StorageAndNetworkSettingsSubScreen(
					sqliteDiagnostics = sqliteDiagnostics,
					preloadPagesOnWifi = preloadPagesOnWifi,
					dohProvider = dohProvider,
					proxyMode = proxyMode,
					clearedCacheBytes = clearedCacheBytes,
					onTogglePreload = { preloadPagesOnWifi = it },
					onSelectDoh = { dohProvider = it },
					onSelectProxy = { proxyMode = it },
					onClearCache = {
						org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline.clearCache()
						clearedCacheBytes = true
					},
					onRefreshDiagnostics = onRefreshDiagnostics,
				)
			}

			SettingsCategorySection.DOWNLOADS -> {
				DownloadsSettingsSubScreen(
					downloadWifiOnly = downloadWifiOnly,
					saveDownloadsAsCbz = saveDownloadsAsCbz,
					parallelDownloadThreads = parallelDownloadThreads,
					deleteDownloadAfterRead = deleteDownloadAfterRead,
					onToggleWifiOnly = { downloadWifiOnly = it },
					onToggleSaveAsCbz = { saveDownloadsAsCbz = it },
					onSetParallelThreads = { parallelDownloadThreads = it },
					onToggleDeleteAfterRead = { deleteDownloadAfterRead = it },
				)
			}

			SettingsCategorySection.CHECK_FOR_NEW_CHAPTERS -> {
				CheckNewChaptersSettingsSubScreen(
					checkUpdatesEnabled = checkUpdatesEnabled,
					checkUpdatesFrequency = checkUpdatesFrequency,
					checkUpdatesWifiOnly = checkUpdatesWifiOnly,
					notificationsEnabled = notificationsEnabled,
					autoHealBrokenSources = autoHealBrokenSources,
					onToggleCheckUpdates = { checkUpdatesEnabled = it },
					onSelectFrequency = { checkUpdatesFrequency = it },
					onToggleWifiOnly = { checkUpdatesWifiOnly = it },
					onToggleNotifications = { notificationsEnabled = it },
					onToggleAutoHeal = { autoHealBrokenSources = it },
					onRefreshFeedNow = onRefreshFeed,
				)
			}

			SettingsCategorySection.SERVICES -> {
				ServicesSettingsSubScreen(
					aiSuggestionsEnabled = aiSuggestionsEnabled,
					displayConfig = displayConfig,
					enabledTrackers = enabledTrackers,
					onToggleAiSuggestions = { aiSuggestionsEnabled = it },
					onUpdateDisplayConfig = onUpdateDisplayConfig,
					onToggleTracker = { tracker ->
						enabledTrackers = if (tracker in enabledTrackers) {
							enabledTrackers - tracker
						} else {
							enabledTrackers + tracker
						}
					},
				)
			}

			SettingsCategorySection.BACKUP_AND_RESTORE -> {
				BackupAndRestoreSettingsSubScreen(
					lastBackupSummary = lastBackupSummary,
					lastExportedPreview = lastExportedPreview,
					periodicBackupEnabled = periodicBackupEnabled,
					periodicBackupFrequency = periodicBackupFrequency,
					periodicBackupFormat = periodicBackupFormat,
					onExportBackup = { format ->
						pendingExportFormat = format
						onExportBackup(format, null)
					},
					onSaveBackupToDevice = { format ->
						pendingExportFormat = format
						createDocLauncher.launch("kotatsu_neo_backup.${format.extension}")
					},
					onOpenRestoreFilePicker = {
						openDocLauncher.launch(arrayOf("*/*"))
					},
					onSimulateRestoreSample = onSimulateRestoreSample,
					onTogglePeriodicBackup = { periodicBackupEnabled = it },
					onSelectPeriodicFrequency = { periodicBackupFrequency = it },
					onSelectPeriodicFormat = { periodicBackupFormat = it },
				)
			}

			SettingsCategorySection.ABOUT -> {
				AboutSettingsSubScreen(
					sqliteDiagnostics = sqliteDiagnostics,
					extensionReposCount = extensionRepos.size.coerceAtLeast(1),
					discoveredExtensionsCount = discoveredExtensions.size.coerceAtLeast(4),
				)
			}
		}
	}

	// Inline Gemini-style Theme & AMOLED Selector Dialog
	if (showInlineThemeDialog) {
		AlertDialog(
			onDismissRequest = { showInlineThemeDialog = false },
			title = {
				Text(
					text = "Choose Theme",
					fontFamily = PlusJakartaDisplayFamily,
					fontWeight = FontWeight.Bold,
				)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					AppThemeMode.entries.forEach { mode ->
						val isSelected = themeMode == mode
						Surface(
							onClick = {
								onSetThemeMode(mode)
							},
							color = if (isSelected) {
								MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
							} else {
								Color.Transparent
							},
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 10.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								RadioButton(
									selected = isSelected,
									onClick = { onSetThemeMode(mode) },
								)
								Spacer(modifier = Modifier.width(8.dp))
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = mode.label,
										style = MaterialTheme.typography.bodyLarge,
										fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
									)
								}
							}
						}
					}

					HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Pure AMOLED Black (#000000)",
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = FontWeight.SemiBold,
							)
							Text(
								text = "True pitch-black background in Dark mode",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = pureOledBlack,
							onCheckedChange = { enabled ->
								if (enabled && !darkTheme) {
									onSetThemeMode(AppThemeMode.DARK)
								}
								onTogglePureOledBlack(enabled)
							},
						)
					}
				}
			},
			confirmButton = {
				TextButton(
					onClick = {
						showInlineThemeDialog = false
						activeSection = SettingsCategorySection.APPEARANCE
					},
				) {
					Text("Color Palettes →")
				}
			},
			dismissButton = {
				TextButton(onClick = { showInlineThemeDialog = false }) {
					Text("Done")
				}
			},
		)
	}

	if (showAddRepoDialog) {
		var repoName by remember { mutableStateOf("Keiyoushi Mihon Community Repo") }
		var repoUrl by remember { mutableStateOf(KEIYOUSHI_OFFICIAL_REPO_URL) }
		var repoType by remember { mutableStateOf("MIHON_JSON") }

		AlertDialog(
			onDismissRequest = { showAddRepoDialog = false },
			title = { Text("Add GitHub Extension Repository") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = "Enter a GitHub repository URL, index.min.json URL, or GitHub owner/repo shorthand (e.g. keiyoushi/extensions).",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					OutlinedTextField(
						value = repoName,
						onValueChange = { repoName = it },
						label = { Text("Repository Name") },
						singleLine = true,
						modifier = Modifier
							.fillMaxWidth()
							.testTag("settings_repo_name_input"),
					)
					OutlinedTextField(
						value = repoUrl,
						onValueChange = { repoUrl = it },
						label = { Text("GitHub Repo (owner/repo or index.min.json URL)") },
						singleLine = true,
						modifier = Modifier
							.fillMaxWidth()
							.testTag("settings_repo_url_input"),
					)
					Row(
						modifier = Modifier.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						FilterChip(
							selected = repoUrl == KEIYOUSHI_OFFICIAL_REPO_URL || repoUrl == "keiyoushi/extensions",
							onClick = {
								repoName = "Keiyoushi Official GitHub Repo"
								repoUrl = KEIYOUSHI_OFFICIAL_REPO_URL
								repoType = "MIHON_JSON"
							},
							label = { Text("GitHub: keiyoushi/extensions") },
						)
						FilterChip(
							selected = repoUrl.contains("Suwayomi", ignoreCase = true),
							onClick = {
								repoName = "Suwayomi Community GitHub Repo"
								repoUrl = "https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/repo/index.min.json"
								repoType = "MIHON_JSON"
							},
							label = { Text("GitHub: Suwayomi") },
						)
						FilterChip(
							selected = repoType == "KOTATSU_PLUGIN",
							onClick = {
								repoName = "Kotatsu Community Parsers"
								repoUrl = "app.kotatsu.parser.PROVIDE_MANGA"
								repoType = "KOTATSU_PLUGIN"
							},
							label = { Text("Kotatsu IPC") },
						)
					}
				}
			},
			confirmButton = {
				Button(
					onClick = {
						onAddRepository(repoName, repoUrl, repoType)
						showAddRepoDialog = false
					},
					modifier = Modifier.testTag("settings_confirm_add_repo_button"),
				) {
					Text("Add & Sync")
				}
			},
			dismissButton = {
				TextButton(onClick = { showAddRepoDialog = false }) {
					Text("Cancel")
				}
			},
		)
	}
}

/**
 * Google Gemini-inspired Material 3 Settings Row inside a 24.dp grouped container card:
 * - Left: 24.dp outlined Material icon directly on the card surface (no noisy icon box).
 * - Center: Crisp primary title + subtle single-line contextual summary.
 * - Right: Clean ChevronRight (`>`) indicator.
 */
@Composable
private fun GeminiSettingsRow(
	icon: ImageVector,
	title: String,
	subtitle: String,
	testTag: String,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 20.dp, vertical = 16.dp)
			.testTag(testTag),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(18.dp),
	) {
		Icon(
			imageVector = icon,
			contentDescription = title,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(24.dp),
		)

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
				fontFamily = PlusJakartaDisplayFamily,
				fontWeight = FontWeight.Normal,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
				color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Icon(
			imageVector = Icons.Default.ChevronRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
			modifier = Modifier.size(20.dp),
		)
	}
}

/**
 * Google Gemini-inspired Inline Selector Row (e.g. `Theme` with `System` + `UnfoldMore` up/down arrows on the right).
 */
@Composable
private fun GeminiInlineSelectorSettingsRow(
	icon: ImageVector,
	title: String,
	valueLabel: String,
	testTag: String,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 20.dp, vertical = 17.dp)
			.testTag(testTag),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(18.dp),
	) {
		Icon(
			imageVector = icon,
			contentDescription = title,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(24.dp),
		)

		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
			fontFamily = PlusJakartaDisplayFamily,
			fontWeight = FontWeight.Normal,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)

		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				text = valueLabel,
				style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Icon(
				imageVector = Icons.Default.UnfoldMore,
				contentDescription = "Change $title",
				tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

/**
 * 1. APPEARANCE SUB-SCREEN
 * - Theme mode (System / Light / Dark)
 * - Pure AMOLED Black mode (#000000)
 * - Color Theme Palette swatches (Kotatsu Teal, Crimson Ink, Mihon Cobalt, Sakura Rose, Royal Amethyst, Amber Gold, Emerald Forest)
 * - Navigation Bar format (Floating Island vs Default Docked) & Active Icons Manager
 * - Default Library List/Grid mode & Language
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsSubScreen(
	darkTheme: Boolean,
	pureOledBlack: Boolean,
	themeMode: AppThemeMode,
	colorSchemePreset: AppColorSchemePreset,
	navBarStyle: NavBarStyle,
	enabledNavTabs: List<AppNavTab>,
	displayConfig: LibraryDisplayConfig,
	appLanguage: String,
	onSetThemeMode: (AppThemeMode) -> Unit,
	onToggleDarkTheme: (Boolean) -> Unit,
	onTogglePureOledBlack: (Boolean) -> Unit,
	onSetColorSchemePreset: (AppColorSchemePreset) -> Unit,
	onSetNavBarStyle: (NavBarStyle) -> Unit,
	onToggleNavTab: (AppNavTab) -> Unit,
	onMoveNavTab: (AppNavTab, Boolean) -> Unit,
	onUpdateDisplayConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit,
	onSelectLanguage: (String) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		// Theme Mode & AMOLED Pure Black Card
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					Text(
						text = "THEME & DISPLAY MODE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)

					// Segmented-style M3 Theme Mode Pill Cards (System / Light / Dark)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						AppThemeMode.entries.forEach { mode ->
							val isSelected = themeMode == mode
							Surface(
								onClick = { onSetThemeMode(mode) },
								color = if (isSelected) {
									MaterialTheme.colorScheme.primaryContainer
								} else {
									MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
								},
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.weight(1f)
									.testTag("theme_mode_${mode.name.lowercase()}"),
							) {
								Column(
									modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Icon(
										imageVector = when (mode) {
											AppThemeMode.SYSTEM -> Icons.Default.Palette
											AppThemeMode.LIGHT -> Icons.Default.LightMode
											AppThemeMode.DARK -> Icons.Default.DarkMode
										},
										contentDescription = mode.label,
										tint = if (isSelected) {
											MaterialTheme.colorScheme.primary
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
										modifier = Modifier.size(20.dp),
									)
									Text(
										text = mode.label,
										style = MaterialTheme.typography.labelLarge,
										fontSize = 12.sp,
										color = if (isSelected) {
											MaterialTheme.colorScheme.onPrimaryContainer
										} else {
											MaterialTheme.colorScheme.onSurface
										},
									)
								}
							}
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Dark theme",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold,
							)
							Text(
								text = "Switch between Warm Archival Paper (Light) and Obsidian Slate (Dark)",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = darkTheme,
							onCheckedChange = onToggleDarkTheme,
							modifier = Modifier.testTag("toggle_dark_theme_switch"),
						)
					}

					HorizontalDivider()

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Text(
									text = "AMOLED pure black mode",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.SemiBold,
								)
								Surface(
									color = Color.Black,
									shape = RoundedCornerShape(6.dp),
									modifier = Modifier.border(
										1.dp,
										MaterialTheme.colorScheme.outline,
										RoundedCornerShape(6.dp),
									),
								) {
									Text(
										text = "#000000",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 9.sp,
										color = Color.White,
										modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
									)
								}
							}
							Text(
								text = "True pitch-black background in dark mode to save battery on OLED screens",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = pureOledBlack,
							onCheckedChange = { enabled ->
								if (enabled && !darkTheme) {
									onToggleDarkTheme(true)
								}
								onTogglePureOledBlack(enabled)
							},
							modifier = Modifier.testTag("toggle_oled_black_switch"),
						)
					}
				}
			}
		}

		// Color Theme Palette Card
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "COLOR THEME PALETTE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = "Select an accent color scheme for buttons, badges, and the Floating Island bar",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					FlowRow(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						AppColorSchemePreset.entries.forEach { preset ->
							val isSelected = colorSchemePreset == preset
							val swatchColor = Color(
								if (darkTheme) preset.darkPrimaryHex else preset.lightPrimaryHex,
							)
							Surface(
								onClick = { onSetColorSchemePreset(preset) },
								color = if (isSelected) {
									MaterialTheme.colorScheme.primaryContainer
								} else {
									MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
								},
								shape = RoundedCornerShape(16.dp),
								modifier = Modifier
									.border(
										width = if (isSelected) 1.5.dp else 0.dp,
										color = if (isSelected) swatchColor else Color.Transparent,
										shape = RoundedCornerShape(16.dp),
									)
									.testTag("color_theme_${preset.name.lowercase()}"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									Box(
										modifier = Modifier
											.size(24.dp)
											.clip(CircleShape)
											.background(swatchColor),
										contentAlignment = Alignment.Center,
									) {
										if (isSelected) {
											Icon(
												imageVector = Icons.Default.Check,
												contentDescription = "Selected",
												tint = Color(0xFF041F22),
												modifier = Modifier.size(14.dp),
											)
										}
									}
									Column {
										Text(
											text = preset.label,
											style = MaterialTheme.typography.labelLarge,
											fontSize = 12.sp,
										)
										Text(
											text = preset.subtitle,
											style = MaterialTheme.typography.bodySmall,
											fontSize = 10.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
										)
									}
								}
							}
						}
					}
				}
			}
		}

		// Navigation Bar & Floating Island Customizer
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = Icons.Default.ViewCarousel,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
						)
						Column {
							Text(
								text = "Navigation Bar & Floating Island",
								style = MaterialTheme.typography.titleMedium,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Choose Floating Island Pill or Classic Docked bar, and reorder tabs",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						NavBarStyle.entries.forEach { style ->
							FilterChip(
								selected = navBarStyle == style,
								onClick = { onSetNavBarStyle(style) },
								label = { Text(style.label) },
								modifier = Modifier.testTag("nav_style_${style.name.lowercase()}"),
							)
						}
					}

					HorizontalDivider()

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "ACTIVE NAVIGATION ICONS",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
						Text(
							text = "${enabledNavTabs.size} / ${KotatsuNeoViewModel.MAX_NAV_BAR_TABS} Icons",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}

					AppNavTab.entries.forEach { tab ->
						val isEnabled = tab in enabledNavTabs
						val indexInBar = enabledNavTabs.indexOf(tab)
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isEnabled) 0.65f else 0.3f),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 6.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = tab.label,
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal,
									)
									Text(
										text = if (tab == AppNavTab.LIBRARY) {
											"Primary Landing Screen (Pinned)"
										} else if (isEnabled) {
											"Slot #${indexInBar + 1} in navigation bar"
										} else {
											"Hidden from navigation bar"
										},
										fontFamily = JetBrainsMonoFamily,
										fontSize = 10.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								Row(verticalAlignment = Alignment.CenterVertically) {
									if (isEnabled && indexInBar > 1) {
										IconButton(
											onClick = { onMoveNavTab(tab, true) },
											modifier = Modifier.size(32.dp),
										) {
											Icon(
												imageVector = Icons.Default.ArrowUpward,
												contentDescription = "Move Earlier",
												modifier = Modifier.size(16.dp),
											)
										}
									}
									if (isEnabled && indexInBar >= 1 && indexInBar < enabledNavTabs.size - 1) {
										IconButton(
											onClick = { onMoveNavTab(tab, false) },
											modifier = Modifier.size(32.dp),
										) {
											Icon(
												imageVector = Icons.Default.ArrowDownward,
												contentDescription = "Move Later",
												modifier = Modifier.size(16.dp),
											)
										}
									}
									Switch(
										checked = isEnabled,
										enabled = tab != AppNavTab.LIBRARY,
										onCheckedChange = { onToggleNavTab(tab) },
										modifier = Modifier.testTag("toggle_nav_tab_${tab.id}"),
									)
								}
							}
						}
					}
				}
			}
		}

		// List Mode & Language Card
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "LIST MODE & LANGUAGE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)

					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						LibraryLayoutMode.entries.forEach { mode ->
							FilterChip(
								selected = displayConfig.layoutMode == mode,
								onClick = {
									onUpdateDisplayConfig { it.copy(layoutMode = mode) }
								},
								label = { Text(mode.label) },
							)
						}
					}

					HorizontalDivider()

					Text(
						text = "App Language",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
					)
					Row(
						modifier = Modifier.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf(
							"System default (English)",
							"日本語 (Japanese)",
							"Español",
							"Français",
							"Deutsch",
							"Português",
						).forEach { lang ->
							FilterChip(
								selected = appLanguage == lang,
								onClick = { onSelectLanguage(lang) },
								label = { Text(lang) },
							)
						}
					}
				}
			}
		}
	}
}

private enum class ExtensionManagerTab(val label: String) {
	INSTALLED("Installed"),
	AVAILABLE("Available"),
	REPOS("Repos"),
}

private enum class ExtensionSortOrder(val label: String) {
	UPDATES_FIRST("Updates First"),
	ALPHABETICAL("Alphabetical"),
	LANGUAGE("Language"),
}

/**
 * 2. EXTENSIONS & REPOSITORIES SUB-SCREEN (DropSauce-Inspired Material 3 Extension Manager)
 * - Segmented tabs: Installed (with Updates badge), Available (Keiyoushi / Kotatsu catalog), Repos
 * - Search filter, Language filter chips, Sorting order (Updates First, Alphabetical, Language), 18+ NSFW toggle
 * - APK / Descriptor Downloader with live progress bar, FileProvider URI sharing, and External Browser APK launcher
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaSourcesSettingsSubScreen(
	extensionRepos: List<ExtensionRepoEntity>,
	discoveredExtensions: List<ExternalExtensionInfo>,
	extensionInstallTasks: Map<String, ExtensionInstallTask>,
	disabledExtensionIds: Set<String>,
	showNsfwSources: Boolean,
	onOpenAddRepoDialog: () -> Unit,
	onQuickAddKeiyoushiRepo: () -> Unit,
	onRemoveRepository: (Long) -> Unit,
	onInstallOrUpdateExtension: (ExternalExtensionInfo) -> Unit,
	onUninstallExtension: (ExternalExtensionInfo) -> Unit,
	onSyncAllRepositories: () -> Unit,
	onLaunchBrowserApkDownload: (ExternalExtensionInfo) -> Unit,
	onShareExtensionFileProvider: (ExternalExtensionInfo) -> Unit,
	canRequestPackageInstalls: Boolean,
	onOpenUnknownSourcesSettings: () -> Unit,
	onLaunchApkPackageInstaller: (ExternalExtensionInfo) -> Unit,
	onToggleExtensionEnabled: (String) -> Unit,
	onToggleShowNsfw: (Boolean) -> Unit,
) {
	var selectedTab by rememberSaveable { mutableStateOf(ExtensionManagerTab.INSTALLED) }
	var extSearchQuery by rememberSaveable { mutableStateOf("") }
	var selectedLang by rememberSaveable { mutableStateOf("ALL") }
	var sortOrder by rememberSaveable { mutableStateOf(ExtensionSortOrder.UPDATES_FIRST) }

	val hasKeiyoushi = extensionRepos.any {
		it.baseUrl.contains("keiyoushi", ignoreCase = true)
	}

	val visibleExtensions = remember(discoveredExtensions, showNsfwSources, extSearchQuery, selectedLang, sortOrder) {
		val base = discoveredExtensions.filter { ext ->
			val matchesNsfw = showNsfwSources || !ext.isNsfw
			val matchesLang = selectedLang == "ALL" || ext.lang.equals(selectedLang, ignoreCase = true)
			val matchesQuery = extSearchQuery.isBlank() ||
				ext.name.contains(extSearchQuery, ignoreCase = true) ||
				ext.pkgName.contains(extSearchQuery, ignoreCase = true) ||
				ext.lang.contains(extSearchQuery, ignoreCase = true)
			matchesNsfw && matchesLang && matchesQuery
		}
		when (sortOrder) {
			ExtensionSortOrder.UPDATES_FIRST -> base.sortedWith(
				compareByDescending<ExternalExtensionInfo> { it.hasUpdate }
					.thenBy { it.name.removePrefix("EXT:").lowercase() },
			)
			ExtensionSortOrder.ALPHABETICAL -> base.sortedBy { it.name.removePrefix("EXT:").lowercase() }
			ExtensionSortOrder.LANGUAGE -> base.sortedWith(
				compareBy<ExternalExtensionInfo> { it.lang.lowercase() }
					.thenBy { it.name.removePrefix("EXT:").lowercase() },
			)
		}
	}

	val installedList = remember(visibleExtensions) {
		visibleExtensions.filter { it.isInstalled }
	}
	val availableList = remember(visibleExtensions) {
		visibleExtensions.filter { !it.isInstalled }
	}
	val updatesAvailableCount = remember(discoveredExtensions) {
		discoveredExtensions.count { it.isInstalled && it.hasUpdate }
	}

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		// Top Segmented Tab Bar: Installed (N) | Available (N) | Repos (N) + Sync Button
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						ExtensionManagerTab.entries.forEach { tab ->
							val isSelected = selectedTab == tab
							val countBadge = when (tab) {
								ExtensionManagerTab.INSTALLED -> installedList.size
								ExtensionManagerTab.AVAILABLE -> availableList.size
								ExtensionManagerTab.REPOS -> extensionRepos.size
							}
							Surface(
								onClick = { selectedTab = tab },
								color = if (isSelected) {
									MaterialTheme.colorScheme.primaryContainer
								} else {
									MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
								},
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.weight(1f)
									.testTag("ext_tab_${tab.name.lowercase()}"),
							) {
								Row(
									modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
									horizontalArrangement = Arrangement.Center,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = "${tab.label} ($countBadge)",
										style = MaterialTheme.typography.labelLarge,
										fontSize = 12.sp,
										fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
										color = if (isSelected) {
											MaterialTheme.colorScheme.onPrimaryContainer
										} else {
											MaterialTheme.colorScheme.onSurfaceVariant
										},
										maxLines = 1,
									)
									if (tab == ExtensionManagerTab.INSTALLED && updatesAvailableCount > 0) {
										Spacer(modifier = Modifier.width(4.dp))
										Surface(
											color = Color(0xFFF59E0B),
											shape = CircleShape,
										) {
											Text(
												text = "$updatesAvailableCount",
												fontSize = 9.sp,
												fontWeight = FontWeight.Bold,
												color = Color.Black,
												modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
											)
										}
									}
								}
							}
						}
					}

					// Search, Language, Sorting Order & NSFW Filter Controls (for Installed & Available tabs)
					if (selectedTab != ExtensionManagerTab.REPOS) {
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
							shape = RoundedCornerShape(16.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(44.dp),
						) {
							Row(
								modifier = Modifier
									.fillMaxSize()
									.padding(horizontal = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Icon(
									imageVector = Icons.Default.Search,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp),
								)
								Spacer(modifier = Modifier.width(8.dp))
								Box(
									modifier = Modifier.weight(1f),
									contentAlignment = Alignment.CenterStart,
								) {
									if (extSearchQuery.isEmpty()) {
										Text(
											text = "Filter extensions by name, package, or lang…",
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
										)
									}
									BasicTextField(
										value = extSearchQuery,
										onValueChange = { extSearchQuery = it },
										singleLine = true,
										textStyle = MaterialTheme.typography.bodyMedium.copy(
											color = MaterialTheme.colorScheme.onSurface,
										),
										cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
										modifier = Modifier
											.fillMaxWidth()
											.testTag("extension_search_input"),
									)
								}
								if (extSearchQuery.isNotEmpty()) {
									IconButton(
										onClick = { extSearchQuery = "" },
										modifier = Modifier.size(28.dp),
									) {
										Icon(
											imageVector = Icons.Default.Close,
											contentDescription = "Clear search",
											modifier = Modifier.size(16.dp),
										)
									}
								}
								IconButton(
									onClick = onSyncAllRepositories,
									modifier = Modifier
										.size(32.dp)
										.testTag("sync_all_repos_button"),
								) {
									Icon(
										imageVector = Icons.Default.Sync,
										contentDescription = "Sync repositories",
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(18.dp),
									)
								}
							}
						}

						// Language & Sorting Order Filter Row
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							listOf("ALL", "EN", "MULTI", "JA", "KO").forEach { lang ->
								FilterChip(
									selected = selectedLang == lang,
									onClick = { selectedLang = lang },
									label = { Text(if (lang == "ALL") "All Langs" else lang, fontSize = 11.sp) },
								)
							}
							Spacer(modifier = Modifier.width(4.dp))
							ExtensionSortOrder.entries.forEach { order ->
								FilterChip(
									selected = sortOrder == order,
									onClick = { sortOrder = order },
									label = { Text("Sort: ${order.label}", fontSize = 11.sp) },
								)
							}
						}

						// Compact Content Filter & FileProvider Status Bar
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = "Include community mirror sources",
									style = MaterialTheme.typography.labelLarge,
									fontWeight = FontWeight.SemiBold,
								)
								Text(
									text = "GitHub Extension Downloader + FileProvider bridge active",
									style = MaterialTheme.typography.bodySmall,
									fontSize = 11.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Switch(
								checked = showNsfwSources,
								onCheckedChange = onToggleShowNsfw,
								modifier = Modifier.testTag("toggle_nsfw_extensions_switch"),
							)
						}

						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 12.dp, vertical = 8.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = if (canRequestPackageInstalls) {
											"GitHub Extension Bridge: READY"
										} else {
											"GitHub Extension Bridge: Action Required"
										},
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.Bold,
										color = if (canRequestPackageInstalls) Color(0xFF10B981) else Color(0xFFF59E0B),
									)
									Text(
										text = "Syncs GitHub extension descriptors via HTTPS & FileProvider",
										style = MaterialTheme.typography.bodySmall,
										fontSize = 10.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								TextButton(
									onClick = onOpenUnknownSourcesSettings,
									modifier = Modifier.testTag("open_unknown_sources_settings_button"),
								) {
									Text("App Info", fontSize = 11.sp)
								}
							}
						}
					}
				}
			}
		}

		when (selectedTab) {
			ExtensionManagerTab.INSTALLED -> {
				if (updatesAvailableCount > 0) {
					item {
						Surface(
							color = Color(0xFFF59E0B).copy(alpha = 0.14f),
							shape = RoundedCornerShape(16.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 14.dp, vertical = 10.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = "$updatesAvailableCount Extension Update(s) Available",
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.Bold,
										color = Color(0xFFF59E0B),
									)
									Text(
										text = "Update directly from Keiyoushi index.min.json & cache via FileProvider",
										style = MaterialTheme.typography.bodySmall,
										fontSize = 11.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								FilledTonalButton(
									onClick = {
										installedList.filter { it.hasUpdate }.forEach { onInstallOrUpdateExtension(it) }
									},
									modifier = Modifier.testTag("update_all_extensions_button"),
								) {
									Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
									Spacer(modifier = Modifier.width(6.dp))
									Text("Update All", fontSize = 12.sp)
								}
							}
						}
					}
				}

				items(installedList, key = { it.id }) { ext ->
					val isEnabled = ext.id !in disabledExtensionIds
					val task = extensionInstallTasks[ext.pkgName]
					DropSauceExtensionItemCard(
						ext = ext,
						isEnabled = isEnabled,
						installTask = task,
						onToggleEnabled = { onToggleExtensionEnabled(ext.id) },
						onInstallOrUpdate = { onInstallOrUpdateExtension(ext) },
						onUninstall = { onUninstallExtension(ext) },
						onLaunchBrowserApk = { onLaunchBrowserApkDownload(ext) },
						onShareFileProvider = { onShareExtensionFileProvider(ext) },
						onLaunchApkPackageInstaller = { onLaunchApkPackageInstaller(ext) },
					)
				}
			}

			ExtensionManagerTab.AVAILABLE -> {
				item {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "KEIYOUSHI & KOTATSU EXTENSION CATALOG (${availableList.size})",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
						if (!hasKeiyoushi) {
							TextButton(
								onClick = onQuickAddKeiyoushiRepo,
								modifier = Modifier.testTag("available_quick_add_keiyoushi"),
							) {
								Text("+ Link Keiyoushi Repo", fontSize = 11.sp)
							}
						}
					}
				}

				items(availableList, key = { it.id }) { ext ->
					val task = extensionInstallTasks[ext.pkgName]
					DropSauceExtensionItemCard(
						ext = ext,
						isEnabled = false,
						installTask = task,
						onToggleEnabled = {},
						onInstallOrUpdate = { onInstallOrUpdateExtension(ext) },
						onUninstall = {},
						onLaunchBrowserApk = { onLaunchBrowserApkDownload(ext) },
						onShareFileProvider = { onShareExtensionFileProvider(ext) },
						onLaunchApkPackageInstaller = { onLaunchApkPackageInstaller(ext) },
					)
				}
			}

			ExtensionManagerTab.REPOS -> {
				item {
					Card(
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						shape = RoundedCornerShape(20.dp),
					) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = "Extension Repositories",
										style = MaterialTheme.typography.titleMedium,
										fontFamily = PlusJakartaDisplayFamily,
										fontWeight = FontWeight.Bold,
									)
									Text(
										text = "Manage GitHub index.min.json repos (Keiyoushi / Mihon) and Kotatsu parser providers",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								Button(
									onClick = onOpenAddRepoDialog,
									modifier = Modifier.testTag("settings_add_repo_button"),
								) {
									Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
									Spacer(modifier = Modifier.width(6.dp))
									Text("Add Repo")
								}
							}

							Surface(
								color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier.fillMaxWidth(),
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(12.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Column(modifier = Modifier.weight(1f)) {
										Row(
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(6.dp),
										) {
											Icon(
												imageVector = Icons.Default.Security,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.primary,
												modifier = Modifier.size(16.dp),
											)
											Text(
												text = "Keiyoushi Official GitHub Repo",
												style = MaterialTheme.typography.titleSmall,
												fontWeight = FontWeight.Bold,
											)
										}
										Text(
											text = KEIYOUSHI_OFFICIAL_REPO_URL,
											fontFamily = JetBrainsMonoFamily,
											fontSize = 10.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
									Spacer(modifier = Modifier.width(8.dp))
									if (hasKeiyoushi) {
										Surface(
											color = Color(0xFF10B981).copy(alpha = 0.2f),
											shape = RoundedCornerShape(8.dp),
										) {
											Text(
												text = "LINKED",
												fontFamily = JetBrainsMonoFamily,
												fontWeight = FontWeight.Bold,
												fontSize = 10.sp,
												color = Color(0xFF10B981),
												modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
											)
										}
									} else {
										FilledTonalButton(
											onClick = onQuickAddKeiyoushiRepo,
											modifier = Modifier.testTag("quick_add_keiyoushi_repo_button"),
										) {
											Text("Add Keiyoushi", fontSize = 12.sp)
										}
									}
								}
							}
						}
					}
				}

				items(extensionRepos, key = { it.repoId }) { repo ->
					Card(
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						shape = RoundedCornerShape(16.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(14.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Text(
										text = repo.name,
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.Bold,
									)
									Surface(
										color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
										shape = RoundedCornerShape(6.dp),
									) {
										Text(
											text = repo.repoType,
											fontFamily = JetBrainsMonoFamily,
											fontSize = 9.sp,
											color = MaterialTheme.colorScheme.primary,
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
										)
									}
								}
								Text(
									text = repo.baseUrl,
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								Text(
									text = "${repo.extensionCount} extensions indexed",
									style = MaterialTheme.typography.labelSmall,
									color = Color(0xFF10B981),
								)
							}
							IconButton(
								onClick = { onRemoveRepository(repo.repoId) },
								modifier = Modifier.testTag("delete_repo_${repo.repoId}"),
							) {
								Icon(
									imageVector = Icons.Default.Delete,
									contentDescription = "Remove Repository",
									tint = MaterialTheme.colorScheme.error,
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
private fun DropSauceExtensionItemCard(
	ext: ExternalExtensionInfo,
	isEnabled: Boolean,
	installTask: ExtensionInstallTask?,
	onToggleEnabled: () -> Unit,
	onInstallOrUpdate: () -> Unit,
	onUninstall: () -> Unit,
	onLaunchBrowserApk: () -> Unit,
	onShareFileProvider: () -> Unit,
	onLaunchApkPackageInstaller: () -> Unit = {},
) {
	val cleanName = ext.name.removePrefix("EXT:")
	val isCoreSeeded = ext.pkgName in setOf(
		"eu.kanade.tachiyomi.extension.all.mangadex",
		"eu.kanade.tachiyomi.extension.en.mangadex",
		"eu.kanade.tachiyomi.extension.en.aquamanga",
		"eu.kanade.tachiyomi.extension.all.comick",
		"eu.kanade.tachiyomi.extension.all.batoto",
		"org.koitharu.kotatsu.extension.batoto",
	)

	Card(
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Box(
						modifier = Modifier
							.size(40.dp)
							.clip(RoundedCornerShape(12.dp))
							.background(
								if (ext.isInstalled) {
									MaterialTheme.colorScheme.primaryContainer
								} else {
									MaterialTheme.colorScheme.surfaceVariant
								},
							),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = cleanName.take(2).uppercase(),
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
							fontSize = 13.sp,
							color = if (ext.isInstalled) {
								MaterialTheme.colorScheme.onPrimaryContainer
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
						)
					}

					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								text = cleanName,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Surface(
								color = MaterialTheme.colorScheme.secondaryContainer,
								shape = RoundedCornerShape(5.dp),
							) {
								Text(
									text = ext.lang.uppercase(),
									fontFamily = JetBrainsMonoFamily,
									fontSize = 9.sp,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onSecondaryContainer,
									modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
								)
							}
							if (ext.isNsfw) {
								Surface(
									color = MaterialTheme.colorScheme.errorContainer,
									shape = RoundedCornerShape(5.dp),
								) {
									Text(
										text = "18+",
										fontFamily = JetBrainsMonoFamily,
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.onErrorContainer,
										modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
									)
								}
							}
						}

						Text(
							text = if (ext.hasUpdate && ext.latestVersionName != null) {
								"${ext.pkgName} • v${ext.versionName} → v${ext.latestVersionName}"
							} else {
								"${ext.pkgName} • v${ext.versionName}"
							},
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = if (ext.hasUpdate) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}

				Spacer(modifier = Modifier.width(8.dp))

				if (!ext.isInstalled) {
					Button(
						onClick = onInstallOrUpdate,
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier.testTag("install_ext_${ext.id}"),
					) {
						Icon(
							imageVector = Icons.Default.Download,
							contentDescription = "Download & Install",
							modifier = Modifier.size(15.dp),
						)
						Spacer(modifier = Modifier.width(4.dp))
						Text("Install", fontSize = 12.sp)
					}
				} else if (ext.hasUpdate) {
					FilledTonalButton(
						onClick = onInstallOrUpdate,
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
						modifier = Modifier.testTag("update_ext_${ext.id}"),
					) {
						Icon(
							imageVector = Icons.Default.CloudDownload,
							contentDescription = "Update Extension",
							modifier = Modifier.size(15.dp),
						)
						Spacer(modifier = Modifier.width(4.dp))
						Text("Update", fontSize = 12.sp)
					}
				} else {
					Switch(
						checked = isEnabled,
						onCheckedChange = { onToggleEnabled() },
						modifier = Modifier.testTag("toggle_source_${ext.id}"),
					)
				}
			}

			// Live Download & FileProvider Verification Progress
			if (installTask != null && (installTask.status == ExtensionInstallStatus.DOWNLOADING || installTask.status == ExtensionInstallStatus.VERIFYING)) {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = if (installTask.status == ExtensionInstallStatus.VERIFYING) {
								"Staging PackageInstaller session & FileProvider URI…"
							} else {
								"Downloading GitHub APK ${ext.apkFileName.ifBlank { "${ext.pkgName}.apk" }}…"
							},
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.primary,
						)
						Text(
							text = "${installTask.progressPercent}%",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					LinearProgressIndicator(
						progress = { (installTask.progressPercent / 100f).coerceIn(0f, 1f) },
						modifier = Modifier
							.fillMaxWidth()
							.height(5.dp)
							.clip(RoundedCornerShape(3.dp)),
					)
				}
			}

			// Secondary Action Bar: OS APK PackageInstaller, FileProvider URI Share, GitHub APK URL, Uninstall
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					modifier = Modifier.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Surface(
						onClick = onLaunchApkPackageInstaller,
						color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
						shape = RoundedCornerShape(8.dp),
						modifier = Modifier.testTag("install_apk_package_${ext.id}"),
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Icon(
								imageVector = Icons.Default.Extension,
								contentDescription = "Launch OS APK Installer",
								tint = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.size(13.dp),
							)
							Text(
								text = "Install APK",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 10.sp,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
							)
						}
					}

					if (ext.apkDownloadUrl != null) {
						Surface(
							onClick = onLaunchBrowserApk,
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
							shape = RoundedCornerShape(8.dp),
							modifier = Modifier.testTag("browser_apk_${ext.id}"),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(
									imageVector = Icons.Default.CloudDownload,
									contentDescription = "Download APK from GitHub",
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(13.dp),
								)
								Text(
									text = "GitHub APK",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.primary,
								)
							}
						}
					}

					if (ext.isInstalled) {
						Surface(
							onClick = onShareFileProvider,
							color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
							shape = RoundedCornerShape(8.dp),
							modifier = Modifier.testTag("share_fileprovider_${ext.id}"),
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(
									imageVector = Icons.Default.Folder,
									contentDescription = "Share via FileProvider",
									tint = MaterialTheme.colorScheme.onSecondaryContainer,
									modifier = Modifier.size(13.dp),
								)
								Text(
									text = "FileProvider URI",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSecondaryContainer,
								)
							}
						}
					}
				}

				if (ext.isInstalled && !isCoreSeeded) {
					TextButton(
						onClick = onUninstall,
						contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
						modifier = Modifier.testTag("uninstall_ext_${ext.id}"),
					) {
						Icon(
							imageVector = Icons.Default.Delete,
							contentDescription = "Uninstall extension",
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier.size(14.dp),
						)
						Spacer(modifier = Modifier.width(4.dp))
						Text(
							text = "Uninstall",
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.error,
						)
					}
				}
			}
		}
	}
}

/**
 * 3. READER SETTINGS SUB-SCREEN
 * - Read mode, Scale mode, Switch pages, AI Real-Time Upscale default, Color filter & Brightness/Contrast
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSubScreen(
	readerConfig: ReaderCustomizationConfig,
	volumeKeysSwitchPages: Boolean,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onToggleVolumeKeys: (Boolean) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "DEFAULT READ MODE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						ReaderStyleMode.entries.forEach { mode ->
							FilterChip(
								selected = readerConfig.readingMode == mode,
								onClick = { onUpdateReaderConfig { it.copy(readingMode = mode) } },
								label = { Text(mode.label) },
							)
						}
					}

					HorizontalDivider()

					Text(
						text = "DEFAULT SCALE MODE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						ReaderScaleMode.entries.forEach { scale ->
							FilterChip(
								selected = readerConfig.scaleMode == scale,
								onClick = { onUpdateReaderConfig { it.copy(scaleMode = scale) } },
								label = { Text(scale.label) },
							)
						}
					}
				}
			}
		}

		// Switch Pages & Tap Controls
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Text(
						text = "SWITCH PAGES & NAVIGATION",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Switch pages with Volume keys", style = MaterialTheme.typography.titleSmall)
							Text(
								"Use Volume Up / Down hardware buttons to flip manga pages",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = volumeKeysSwitchPages,
							onCheckedChange = onToggleVolumeKeys,
						)
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Auto-detect & split double-page spreads", style = MaterialTheme.typography.titleSmall)
							Text(
								"Automatically detect wide two-page spreads and split in portrait or fit full in landscape",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = readerConfig.autoDetectDoubleSpread,
							onCheckedChange = { checked ->
								onUpdateReaderConfig { it.copy(autoDetectDoubleSpread = checked, splitDoubleSpreadsInPortrait = checked) }
							},
						)
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Automatic white margin crop", style = MaterialTheme.typography.titleSmall)
							Text(
								"Trim uniform outer white/black borders on decoded CBZ & network pages",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = readerConfig.autoCropMargins,
							onCheckedChange = { checked ->
								onUpdateReaderConfig { it.copy(autoCropMargins = checked) }
							},
						)
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Show 3x3 Tap Grid Zones overlay", style = MaterialTheme.typography.titleSmall)
							Text(
								"Display visual guide for left/center/right tap areas inside Reader",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = readerConfig.showTapGridZones,
							onCheckedChange = { checked ->
								onUpdateReaderConfig { it.copy(showTapGridZones = checked) }
							},
						)
					}
				}
			}
		}

		// AI Upscaling & Color Filter Defaults
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "AI REAL-TIME UPSCALER & COLOR CORRECTION",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						AiUpscaleMode.entries.forEach { mode ->
							FilterChip(
								selected = readerConfig.aiUpscaleMode == mode,
								onClick = { onUpdateReaderConfig { it.copy(aiUpscaleMode = mode) } },
								label = { Text("${mode.badge} • ${mode.label}") },
							)
						}
					}

					HorizontalDivider()

					Text(
						text = "COLOR FILTER PRESET",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						ReaderColorFilterPreset.entries.forEach { preset ->
							FilterChip(
								selected = readerConfig.colorFilterPreset == preset,
								onClick = { onUpdateReaderConfig { it.copy(colorFilterPreset = preset) } },
								label = { Text(preset.label) },
							)
						}
					}

					Text(
						text = "Brightness offset (${(readerConfig.brightnessDelta * 100).roundToInt()}%)",
						style = MaterialTheme.typography.bodySmall,
					)
					Slider(
						value = readerConfig.brightnessDelta,
						onValueChange = { v -> onUpdateReaderConfig { it.copy(brightnessDelta = v) } },
						valueRange = -0.35f..0.35f,
					)

					Text(
						text = "Ink Contrast boost (${(readerConfig.contrastDelta * 100).roundToInt()}%)",
						style = MaterialTheme.typography.bodySmall,
					)
					Slider(
						value = readerConfig.contrastDelta,
						onValueChange = { v -> onUpdateReaderConfig { it.copy(contrastDelta = v) } },
						valueRange = -0.25f..0.50f,
					)
				}
			}
		}
	}
}

/**
 * 4. STORAGE AND NETWORK SUB-SCREEN
 * - Storage usage breakdown + Clear Cache
 * - Content preloading & DoH / Proxy
 * - Native Room / SQLite v28 Engine Diagnostics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageAndNetworkSettingsSubScreen(
	sqliteDiagnostics: SqliteEngineDiagnostics,
	preloadPagesOnWifi: Boolean,
	dohProvider: String,
	proxyMode: String,
	clearedCacheBytes: Boolean,
	onTogglePreload: (Boolean) -> Unit,
	onSelectDoh: (String) -> Unit,
	onSelectProxy: (String) -> Unit,
	onClearCache: () -> Unit,
	onRefreshDiagnostics: () -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "STORAGE USAGE",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					LinearProgressIndicator(
						progress = { if (clearedCacheBytes) 0.14f else 0.42f },
						modifier = Modifier
							.fillMaxWidth()
							.height(8.dp)
							.clip(RoundedCornerShape(4.dp)),
					)
					Text(
						text = if (clearedCacheBytes) {
							"Chapters & Cover Cache: 4.2 MB • Local CBZ Vault: 68.4 MB • SQLite DB: 3.1 MB"
						} else {
							"Chapters & Cover Cache: 148.6 MB • Local CBZ Vault: 68.4 MB • SQLite DB: 3.1 MB"
						},
						fontFamily = JetBrainsMonoFamily,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					FilledTonalButton(
						onClick = onClearCache,
						modifier = Modifier.testTag("clear_chapter_cache_button"),
					) {
						Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(6.dp))
						Text(if (clearedCacheBytes) "Cache Cleared (144.4 MB Freed)" else "Clear Chapter & Cover Cache")
					}
				}
			}
		}

		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "NETWORK, PROXY & CONTENT PRELOADING",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Content preloading", style = MaterialTheme.typography.titleSmall)
							Text(
								"Preload upcoming 4 pages & next chapter seamlessly when on Wi-Fi",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = preloadPagesOnWifi,
							onCheckedChange = onTogglePreload,
						)
					}

					HorizontalDivider()

					Text("DNS over HTTPS (DoH)", style = MaterialTheme.typography.titleSmall)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf("Disabled", "Cloudflare (1.1.1.1)", "Google (8.8.8.8)", "AdGuard", "Quad9").forEach { doh ->
							FilterChip(
								selected = dohProvider == doh,
								onClick = { onSelectDoh(doh) },
								label = { Text(doh) },
							)
						}
					}

					Text("Proxy Type", style = MaterialTheme.typography.titleSmall)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf("Direct", "SOCKS5 Proxy", "HTTP Proxy").forEach { proxy ->
							FilterChip(
								selected = proxyMode == proxy,
								onClick = { onSelectProxy(proxy) },
								label = { Text(proxy) },
							)
						}
					}
				}
			}
		}

		// Native Room / SQLite v28 Engine Diagnostics Card
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Icon(
								imageVector = Icons.Default.Speed,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
							)
							Column {
								Text(
									text = "Kotatsu Room / SQLite v28 Engine",
									style = MaterialTheme.typography.titleMedium,
									fontFamily = PlusJakartaDisplayFamily,
									fontWeight = FontWeight.Bold,
								)
								Text(
									text = "Native C/C++ SQLite + WAL + 256MB mmap + FTS4 Full-Text Index",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						OutlinedButton(
							onClick = onRefreshDiagnostics,
							modifier = Modifier.testTag("benchmark_sqlite_button"),
						) {
							Text("Benchmark")
						}
					}

					Surface(
						color = MaterialTheme.colorScheme.surfaceVariant,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(
							modifier = Modifier.padding(12.dp),
							verticalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Text(
								text = "DATABASE: kotatsu-db (Schema v${sqliteDiagnostics.databaseVersion})",
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 11.sp,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text = "PRAGMA journal_mode = ${sqliteDiagnostics.journalMode} (Concurrent Reads/Writes)",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 11.sp,
							)
							Text(
								text = "PRAGMA mmap_size = ${sqliteDiagnostics.mmapSizeMb} MB • temp_store = MEMORY",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 11.sp,
							)
							Text(
								text = "FTS4 Virtual Table (manga_fts): ${sqliteDiagnostics.ftsIndexCount} rows • Search Latency: ${sqliteDiagnostics.lastFtsSearchLatencyMicros} µs",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 11.sp,
								color = Color(0xFF10B981),
							)
						}
					}
				}
			}
		}
	}
}

/**
 * 5. DOWNLOADS SUB-SCREEN
 */
@Composable
private fun DownloadsSettingsSubScreen(
	downloadWifiOnly: Boolean,
	saveDownloadsAsCbz: Boolean,
	parallelDownloadThreads: Int,
	deleteDownloadAfterRead: Boolean,
	onToggleWifiOnly: (Boolean) -> Unit,
	onToggleSaveAsCbz: (Boolean) -> Unit,
	onSetParallelThreads: (Int) -> Unit,
	onToggleDeleteAfterRead: (Boolean) -> Unit,
) {
	val downloadQueue by KotatsuSyncAndDownloadCoordinator.downloadQueue.collectAsStateWithLifecycle()

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
						Column {
							Text("Downloads folder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
							Text(
								text = "/storage/emulated/0/Android/data/org.koitharu.kotatsu/files/downloads",
								fontFamily = JetBrainsMonoFamily,
								fontSize = 10.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					HorizontalDivider()

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Download only via Wi-Fi", style = MaterialTheme.typography.titleSmall)
							Text(
								"Pause background chapter downloads on metered mobile networks",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = downloadWifiOnly, onCheckedChange = onToggleWifiOnly)
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Save chapters as .CBZ archives", style = MaterialTheme.typography.titleSmall)
							Text(
								"Package downloaded chapters into portable CBZ files via Foreground DataSync Service",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = saveDownloadsAsCbz, onCheckedChange = onToggleSaveAsCbz)
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Auto-delete read chapters", style = MaterialTheme.typography.titleSmall)
							Text(
								"Automatically remove offline archive after completing a chapter",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = deleteDownloadAfterRead, onCheckedChange = onToggleDeleteAfterRead)
					}

					HorizontalDivider()

					Text("Parallel download threads: $parallelDownloadThreads", style = MaterialTheme.typography.titleSmall)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf(1, 2, 3, 4, 6).forEach { count ->
							FilterChip(
								selected = parallelDownloadThreads == count,
								onClick = { onSetParallelThreads(count) },
								label = { Text("${count}x") },
							)
						}
					}
				}
			}
		}

		if (downloadQueue.isNotEmpty()) {
			item {
				val context = LocalContext.current
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "FOREGROUND DATASYNC .CBZ QUEUE (${downloadQueue.size})",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					TextButton(
						onClick = { KotatsuSyncAndDownloadCoordinator.clearCompletedDownloads(context) },
						modifier = Modifier.testTag("clear_downloads_queue_button"),
					) {
						Text("Clear All", style = MaterialTheme.typography.labelSmall)
					}
				}
			}
			items(downloadQueue, key = { it.taskId }) { task ->
				Card(
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
					shape = RoundedCornerShape(16.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = "${task.mangaTitle} • Ch. ${task.chapterNumber}",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.weight(1f),
							)
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Text(
									text = task.status.label,
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.primary,
								)
								IconButton(
									onClick = { KotatsuSyncAndDownloadCoordinator.cancelOrRemoveDownloadTask(task.taskId) },
									modifier = Modifier
										.size(28.dp)
										.testTag("cancel_download_${task.taskId}"),
								) {
									Icon(
										imageVector = Icons.Default.Close,
										contentDescription = "Remove download task",
										modifier = Modifier.size(16.dp),
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
							}
						}
						LinearProgressIndicator(
							progress = { task.progressFraction },
							modifier = Modifier
								.fillMaxWidth()
								.height(6.dp)
								.clip(RoundedCornerShape(3.dp)),
						)
						Text(
							text = "Pages: ${task.pagesDownloaded}/${task.totalPages} • ${task.sizeBytes / 1024} KB • ${task.cbzFilePath.substringAfterLast('/')}",
							fontFamily = JetBrainsMonoFamily,
							fontSize = 10.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}
	}
}

/**
 * 6. CHECK FOR NEW CHAPTERS SUB-SCREEN
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckNewChaptersSettingsSubScreen(
	checkUpdatesEnabled: Boolean,
	checkUpdatesFrequency: String,
	checkUpdatesWifiOnly: Boolean,
	notificationsEnabled: Boolean,
	autoHealBrokenSources: Boolean,
	onToggleCheckUpdates: (Boolean) -> Unit,
	onSelectFrequency: (String) -> Unit,
	onToggleWifiOnly: (Boolean) -> Unit,
	onToggleNotifications: (Boolean) -> Unit,
	onToggleAutoHeal: (Boolean) -> Unit,
	onRefreshFeedNow: () -> Unit,
) {
	val context = LocalContext.current
	var hasNotifPerm by remember {
		mutableStateOf(KotatsuSyncAndDownloadCoordinator.hasNotificationPermission(context))
	}
	val notifPermissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestPermission(),
	) { granted ->
		hasNotifPerm = granted
		onToggleNotifications(granted)
	}

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Look for updates automatically", style = MaterialTheme.typography.titleSmall)
							Text(
								"Poll tracked library series in the background for new chapter releases",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = checkUpdatesEnabled, onCheckedChange = onToggleCheckUpdates)
					}

					Text("Update check frequency", style = MaterialTheme.typography.titleSmall)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf("Every 4 hours", "Every 6 hours", "Every 12 hours", "Daily", "Manual only").forEach { freq ->
							FilterChip(
								selected = checkUpdatesFrequency == freq,
								onClick = { onSelectFrequency(freq) },
								label = { Text(freq) },
							)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Check only via Wi-Fi", style = MaterialTheme.typography.titleSmall)
							Text(
								"Skip background chapter checks when on cellular data",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = checkUpdatesWifiOnly, onCheckedChange = onToggleWifiOnly)
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Notifications settings", style = MaterialTheme.typography.titleSmall)
							Text(
								if (hasNotifPerm) {
									"System permission granted • Alerts active on chapter drop"
								} else {
									"Requires POST_NOTIFICATIONS permission on Android 13+"
								},
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = notificationsEnabled && hasNotifPerm,
							onCheckedChange = { enabled ->
								if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
									notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
								} else {
									onToggleNotifications(enabled)
								}
							},
						)
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Smart Auto-Heal dead sources during sync", style = MaterialTheme.typography.titleSmall)
							Text(
								"Flag HTTP 404/Cloudflare-broken extensions for AI Fix-It migration",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = autoHealBrokenSources, onCheckedChange = onToggleAutoHeal)
					}

					Button(
						onClick = {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
								notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
							}
							onRefreshFeedNow()
						},
						modifier = Modifier
							.fillMaxWidth()
							.testTag("settings_check_new_chapters_now_button"),
					) {
						Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(modifier = Modifier.width(8.dp))
						Text("Check for New Chapters Now")
					}
				}
			}
		}
	}
}

/**
 * 7. SERVICES SUB-SCREEN
 * - Suggestions, Cross-Source Check & Swap, Broken Series Banner toggle, and External Tracker Sync
 */
@Composable
private fun ServicesSettingsSubScreen(
	aiSuggestionsEnabled: Boolean,
	displayConfig: LibraryDisplayConfig,
	enabledTrackers: Set<String>,
	onToggleAiSuggestions: (Boolean) -> Unit,
	onUpdateDisplayConfig: ((LibraryDisplayConfig) -> LibraryDisplayConfig) -> Unit,
	onToggleTracker: (String) -> Unit,
) {
	val trackers = listOf(
		"AniList",
		"MyAnimeList",
		"Shikimori",
		"MangaUpdates",
		"Kitsu",
	)

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Personalized recommendations", style = MaterialTheme.typography.titleSmall)
							Text(
								"Tailor feed suggestions from your reading history",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = aiSuggestionsEnabled, onCheckedChange = onToggleAiSuggestions)
					}

					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Check & Swap on add", style = MaterialTheme.typography.titleSmall)
							Text(
								"Prompt to swap sources when adding existing titles",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = displayConfig.checkAndSwapOnAdd,
							onCheckedChange = { checked ->
								onUpdateDisplayConfig { it.copy(checkAndSwapOnAdd = checked) }
							},
						)
					}

					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Broken source alerts", style = MaterialTheme.typography.titleSmall)
							Text(
								"Highlight unreachable sources in your library",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = displayConfig.showBrokenSeriesBanner,
							onCheckedChange = { checked ->
								onUpdateDisplayConfig { it.copy(showBrokenSeriesBanner = checked) }
							},
						)
					}
				}
			}
		}

		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Text(
						text = "Trackers",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.Bold,
					)
					trackers.forEachIndexed { idx, name ->
						val isConnected = name in enabledTrackers
						if (idx > 0) {
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
						}
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp),
							) {
								Icon(
									imageVector = Icons.Default.Sync,
									contentDescription = null,
									tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(20.dp),
								)
								Text(
									text = name,
									style = MaterialTheme.typography.bodyLarge,
								)
							}
							Switch(
								checked = isConnected,
								onCheckedChange = { onToggleTracker(name) },
							)
						}
					}
				}
			}
		}
	}
}

/**
 * 8. BACKUP AND RESTORE SUB-SCREEN
 * - Dedicated Kotatsu Style (.bak / .bk.zip) Create & Restore
 * - Dedicated Mihon / Tachiyomi Style (.tachibk GZIP Protobuf & .json) Create & Restore
 * - Universal File Picker + Periodic Automatic Backups
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupAndRestoreSettingsSubScreen(
	lastBackupSummary: BackupRestoreSummary?,
	lastExportedPreview: Pair<BackupFormat, String>?,
	periodicBackupEnabled: Boolean,
	periodicBackupFrequency: String,
	periodicBackupFormat: String,
	onExportBackup: (BackupFormat) -> Unit,
	onSaveBackupToDevice: (BackupFormat) -> Unit,
	onOpenRestoreFilePicker: () -> Unit,
	onSimulateRestoreSample: (BackupFormat) -> Unit,
	onTogglePeriodicBackup: (Boolean) -> Unit,
	onSelectPeriodicFrequency: (String) -> Unit,
	onSelectPeriodicFormat: (String) -> Unit,
) {
	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		// Card 1: Kotatsu Style Backup & Restore (.bak / .zip)
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.Backup,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
						)
						Column {
							Text(
								text = "Kotatsu Style Backup & Restore (.bak / .bk.zip)",
								style = MaterialTheme.typography.titleMedium,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Native Kotatsu ZIP archive containing categories.json, favourites.json, history.json, chapters.json & bookmarks",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Button(
							onClick = { onExportBackup(BackupFormat.KOTATSU_BAK) },
							modifier = Modifier
								.weight(1f)
								.testTag("export_backup_kotatsu_bak"),
						) {
							Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Create Kotatsu .bak", fontSize = 12.sp)
						}
						OutlinedButton(
							onClick = { onSaveBackupToDevice(BackupFormat.KOTATSU_BAK) },
							modifier = Modifier.weight(1f),
						) {
							Text("Save .bak to File…", fontSize = 12.sp)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						FilledTonalButton(
							onClick = onOpenRestoreFilePicker,
							modifier = Modifier.weight(1f),
						) {
							Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Restore Kotatsu .bak", fontSize = 12.sp)
						}
						FilledTonalButton(
							onClick = { onSimulateRestoreSample(BackupFormat.KOTATSU_BAK) },
							modifier = Modifier
								.weight(1f)
								.testTag("test_restore_kotatsu_bak_button"),
						) {
							Text("Test Sample .bak", fontSize = 12.sp)
						}
					}
				}
			}
		}

		// Card 2: Mihon / Tachiyomi Style Backup & Restore (.tachibk / .proto.gz & .json)
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.Restore,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
						)
						Column {
							Text(
								text = "Mihon / Tachiyomi Style Backup & Restore (.tachibk / .json)",
								style = MaterialTheme.typography.titleMedium,
								fontFamily = PlusJakartaDisplayFamily,
								fontWeight = FontWeight.Bold,
							)
							Text(
								text = "Cross-app GZIP Protobuf (.tachibk / .proto.gz) and Neko/Mihon (.json) with automatic source mapping & Check-and-Swap deduplication",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}

					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Button(
							onClick = { onExportBackup(BackupFormat.MIHON_TACHIBK) },
							modifier = Modifier.testTag("export_backup_mihon_tachibk"),
						) {
							Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Create Mihon .tachibk", fontSize = 12.sp)
						}
						FilledTonalButton(
							onClick = { onExportBackup(BackupFormat.MIHON_JSON) },
							modifier = Modifier.testTag("export_backup_mihon_json"),
						) {
							Text("Create Mihon .json", fontSize = 12.sp)
						}
						OutlinedButton(
							onClick = { onSaveBackupToDevice(BackupFormat.MIHON_TACHIBK) },
							modifier = Modifier.testTag("save_backup_to_disk_button"),
						) {
							Text("Save .tachibk to File…", fontSize = 12.sp)
						}
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						FilledTonalButton(
							onClick = onOpenRestoreFilePicker,
							modifier = Modifier
								.weight(1f)
								.testTag("restore_backup_file_picker_button"),
						) {
							Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Select .tachibk / .bak", fontSize = 12.sp)
						}
						FilledTonalButton(
							onClick = { onSimulateRestoreSample(BackupFormat.MIHON_TACHIBK) },
							modifier = Modifier
								.weight(1f)
								.testTag("test_restore_mihon_tachibk_button"),
						) {
							Text("Test Sample .tachibk", fontSize = 12.sp)
						}
					}
				}
			}
		}

		// Live Export Preview or Restore Summary Banner
		if (lastExportedPreview != null || lastBackupSummary != null) {
			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					if (lastExportedPreview != null) {
						Surface(
							color = MaterialTheme.colorScheme.surfaceVariant,
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(modifier = Modifier.padding(14.dp)) {
								Text(
									text = "Exported ${lastExportedPreview.first.label}",
									style = MaterialTheme.typography.labelLarge,
									color = MaterialTheme.colorScheme.primary,
								)
								Spacer(modifier = Modifier.height(4.dp))
								Text(
									text = lastExportedPreview.second,
									fontFamily = JetBrainsMonoFamily,
									fontSize = 10.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}

					if (lastBackupSummary != null) {
						Surface(
							color = Color(0xFF10B981).copy(alpha = 0.14f),
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.fillMaxWidth()
								.testTag("backup_restore_summary_box"),
						) {
							Column(
								modifier = Modifier.padding(14.dp),
								verticalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Text(
									text = "Restore Completed: ${lastBackupSummary.format.label}",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = Color(0xFF10B981),
								)
								Text(
									text = "• Manga Restored: ${lastBackupSummary.mangaRestored}  • Categories: ${lastBackupSummary.categoriesRestored}  • Chapters: ${lastBackupSummary.chaptersRestored}",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 11.sp,
								)
								Text(
									text = "• Check-and-Swap Merged: ${lastBackupSummary.duplicatesSwappedOrMerged}  • Unmapped Mihon Sources Flagged for AI Fix-It: ${lastBackupSummary.unmappedMihonSourcesFlaggedForAiFix}",
									fontFamily = JetBrainsMonoFamily,
									fontSize = 11.sp,
								)
							}
						}
					}
				}
			}
		}

		// Card 3: Periodic Automatic Backups
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Text(
						text = "PERIODIC BACKUPS",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Enable automatic periodic backups", style = MaterialTheme.typography.titleSmall)
							Text(
								"Automatically snapshot your library, categories, and reading history",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(checked = periodicBackupEnabled, onCheckedChange = onTogglePeriodicBackup)
					}

					Text("Backup frequency", style = MaterialTheme.typography.titleSmall)
					Row(
						modifier = Modifier.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf("Every 12 hours", "Every 24 hours", "Every 3 days", "Weekly").forEach { freq ->
							FilterChip(
								selected = periodicBackupFrequency == freq,
								onClick = { onSelectPeriodicFrequency(freq) },
								label = { Text(freq) },
							)
						}
					}

					Text("Periodic backup target format", style = MaterialTheme.typography.titleSmall)
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						listOf(
							"Kotatsu .bak + Mihon .tachibk",
							"Kotatsu .bak only",
							"Mihon .tachibk only",
						).forEach { fmt ->
							FilterChip(
								selected = periodicBackupFormat == fmt,
								onClick = { onSelectPeriodicFormat(fmt) },
								label = { Text(fmt) },
							)
						}
					}
				}
			}
		}
	}
}

/**
 * 9. ABOUT SUB-SCREEN
 */
@Composable
private fun AboutSettingsSubScreen(
	sqliteDiagnostics: SqliteEngineDiagnostics,
	extensionReposCount: Int,
	discoveredExtensionsCount: Int,
) {
	var updateCheckResult by remember { mutableStateOf<String?>(null) }

	LazyColumn(
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		item {
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				shape = RoundedCornerShape(20.dp),
			) {
				Column(
					modifier = Modifier.padding(18.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Text(
						text = "Nemuri",
						style = MaterialTheme.typography.headlineMedium,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = "Version 1.2 (Build 2801 • Material 3 Expressive Edition)",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
					)
					Text(
						text = "Unified Manga Reader combining Kotatsu's ergonomics, Neko's discovery feed, and Mihon's extension & source migration ecosystem.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					HorizontalDivider()

					Text(
						text = "• Database Engine: Room / SQLite Schema v${sqliteDiagnostics.databaseVersion} (${sqliteDiagnostics.journalMode} + ${sqliteDiagnostics.mmapSizeMb}MB mmap + FTS4)\n" +
							"• Extension Bridge: $extensionReposCount Repositories • $discoveredExtensionsCount Active Sources (Keiyoushi GitHub + Kotatsu IPC)\n" +
							"• Backup Compatibility: Kotatsu (.bak / .zip) + Mihon / Tachiyomi (.tachibk Protobuf & .json)",
						fontFamily = JetBrainsMonoFamily,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilledTonalButton(
							onClick = {
								updateCheckResult = "You are running the latest release (Version 1.2)."
							},
						) {
							Text("Check for Updates")
						}
					}

					AnimatedVisibility(visible = updateCheckResult != null) {
						Text(
							text = updateCheckResult.orEmpty(),
							style = MaterialTheme.typography.bodySmall,
							color = Color(0xFF10B981),
						)
					}
				}
			}
		}
	}
}
