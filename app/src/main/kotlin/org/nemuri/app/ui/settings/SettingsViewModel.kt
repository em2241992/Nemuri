package org.nemuri.app.ui.settings

import android.net.Uri
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.github.AppUpdateRepository
import org.koitharu.kotatsu.core.github.AppVersion
import org.koitharu.kotatsu.core.logs.AppLogger
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.core.network.DoHProvider
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.prefs.CoverAspectRatio
import org.koitharu.kotatsu.core.prefs.DetailsUiMode
import org.koitharu.kotatsu.core.prefs.DownloadFormat
import org.koitharu.kotatsu.core.prefs.GridGutterDensity
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.core.prefs.NavLabelVisibility
import org.koitharu.kotatsu.core.prefs.QuickScrollButtonsMode
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ReaderScrubberEdge
import org.koitharu.kotatsu.core.prefs.ReaderTapZoneMode
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.koitharu.kotatsu.core.prefs.SelectionMenuStyle
import org.koitharu.kotatsu.core.prefs.TrackerCheckInterval
import org.koitharu.kotatsu.core.prefs.TrackerDownloadStrategy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.NeuralModelDownloadState
import org.koitharu.kotatsu.reader.domain.NeuralUpscaleModelManager
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.suggestions.ui.SuggestionsWorker
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.sync.SyncStatusSnapshot
import org.koitharu.kotatsu.tracker.work.TrackWorker
import org.nemuri.app.ui.components.NemuriToastManager
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class NeuralModelUiItem(
	val id: String,
	val title: String,
	val subtitle: String,
	val isDownloadable: Boolean,
	val isDownloaded: Boolean,
	val isSelected: Boolean,
	val sizeLabel: String,
)

data class StorageStats(
	val isLoading: Boolean = true,
	val downloadsBytes: Long = 0L,
	val availableBytes: Long = 0L,
	val thumbsBytes: Long = 0L,
	val faviconsBytes: Long = 0L,
	val pagesBytes: Long = 0L,
) {
	val totalCacheBytes: Long
		get() = thumbsBytes + faviconsBytes + pagesBytes
}

data class SettingsUiState(
	// Appearance & Layout Studio
	val themeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
	val isAmoled: Boolean = false,
	val colorScheme: ColorScheme = ColorScheme.DEFAULT,
	val uiScalePercent: Int = 100,
	val isTitleOverCover: Boolean = true,
	val mangaCardStyle: MangaCardStyle = MangaCardStyle.COMPACT,
	val detailsUiMode: DetailsUiMode = DetailsUiMode.COMPACT,
	val gridColumnsPortrait: Int = 3,
	val gridColumnsLandscape: Int = 5,
	val cardCornerRadiusDp: Int = 12,
	val gridGutterDensity: GridGutterDensity = GridGutterDensity.STANDARD,
	val coverAspectRatio: CoverAspectRatio = CoverAspectRatio.STANDARD_2_3,
	val showUnreadBadge: Boolean = true,
	val showDownloadedBadge: Boolean = true,
	val showSourceBadge: Boolean = false,
	val showBottomProgressBar: Boolean = true,
	val isNavBarCompact: Boolean = false,
	val isLegacyNavigationBar: Boolean = false,
	val visibleNavTabs: Set<String> = AppSettings.DEFAULT_VISIBLE_NAV_TABS,
	val selectionMenuStyle: SelectionMenuStyle = SelectionMenuStyle.VISUAL_DIAGRAM,
	val navLabelVisibility: NavLabelVisibility = NavLabelVisibility.SELECTED_ONLY,
	val isSwipeActionsEnabled: Boolean = true,
	val isFastScrollEnabled: Boolean = true,
	val isFastScrollLeftHanded: Boolean = false,
	val quickScrollButtonsMode: QuickScrollButtonsMode = QuickScrollButtonsMode.OFF,
	val isLastPositionPillEnabled: Boolean = true,
	// Extensions
	val isPrivateInstall: Boolean = false,
	val isShizukuInstaller: Boolean = false,
	val isAutoUpdateExtensions: Boolean = false,
	val isExtensionUpdateNotifications: Boolean = true,
	val contentFilter: ContentWarning = ContentWarning.NSFW,
	val mihonUserAgentOverride: String? = null,
	// Reader & Novel
	val defaultReaderMode: ReaderMode = ReaderMode.STANDARD,
	val isReaderModeDetectionEnabled: Boolean = true,
	val isReaderKeepScreenOn: Boolean = true,
	val isReaderVolumeButtonsEnabled: Boolean = false,
	val is32BitColorsEnabled: Boolean = false,
	val isPagesNumbersEnabled: Boolean = false,
	val isReaderSeamlessStitchEnabled: Boolean = true,
	val readerTapZoneMode: ReaderTapZoneMode = ReaderTapZoneMode.DEFAULT,
	val readerScrubberEdge: ReaderScrubberEdge = ReaderScrubberEdge.RIGHT,
	val isWebtoonGapsEnabled: Boolean = false,
	val defaultWebtoonZoomOutPercent: Int = 0,
	val isReaderDoubleTapFill: Boolean = false,
	val isReaderZoomButtonsEnabled: Boolean = false,
	val readerShaderPreset: ReaderShaderPreset = ReaderShaderPreset.OFF,
	val isReaderDescreenEnabled: Boolean = false,
	val isReaderInkLevelsEnabled: Boolean = false,
	val readerShaderStrength: Float = 0.75f,
	val isNeuralUpscaleSupported: Boolean = false,
	val isNeuralUpscaleEnabled: Boolean = false,
	val neuralAccelerator: NeuralAccelerator = NeuralAccelerator.AUTO,
	val activeNeuralDelegateLabel: String = "Idle",
	val isNeuralPreloadEnabled: Boolean = true,
	val neuralCatalog: List<NeuralModelUiItem> = emptyList(),
	val neuralDownloadState: NeuralModelDownloadState = NeuralModelDownloadState(),
	val neuralCacheBytes: Long = 0L,
	val epubTheme: String = "system",
	val epubFontFamily: String = "serif",
	val epubFontSize: Int = 100,
	val epubLineHeight: Int = 160,
	val epubHorizontalPadding: Int = 20,
	// Downloads & Storage
	val preferredDownloadFormat: DownloadFormat = DownloadFormat.AUTOMATIC,
	val meteredNetworkOption: TriStateOption = TriStateOption.ASK,
	val isAutoCleanupEnabled: Boolean = false,
	val autoCleanupKeepCount: Int = 2,
	val storageStats: StorageStats = StorageStats(),
	// Updates & Feed
	val isTrackerEnabled: Boolean = true,
	val isTrackerWifiOnly: Boolean = false,
	val trackerCheckInterval: TrackerCheckInterval = TrackerCheckInterval.EVERY_12_HOURS,
	val trackerFrequencyFactor: Float = 1f,
	val isTrackerNsfwDisabled: Boolean = false,
	val trackerDownloadStrategy: TrackerDownloadStrategy = TrackerDownloadStrategy.DISABLED,
	val isTrackerRunning: Boolean = false,
	val isFeedUnreadOnly: Boolean = false,
	val isSuggestionsEnabled: Boolean = false,
	val isSuggestionsWifiOnly: Boolean = false,
	val isSuggestionsExcludeNsfw: Boolean = false,
	val isSuggestionsExcludeNovels: Boolean = true,
	// Privacy & Network
	val isAppLockEnabled: Boolean = false,
	val appLockTimeout: org.koitharu.kotatsu.core.prefs.AppProtectionTimeout = org.koitharu.kotatsu.core.prefs.AppProtectionTimeout.INSTANT,
	val isIncognitoEnabled: Boolean = false,
	val incognitoForNsfw: TriStateOption = TriStateOption.ASK,
	val screenshotsPolicy: ScreenshotsPolicy = ScreenshotsPolicy.BLOCK_INCOGNITO,
	val isAdBlockEnabled: Boolean = false,
	val isSslBypassEnabled: Boolean = false,
	val dnsOverHttps: DoHProvider = DoHProvider.NONE,
	val proxyType: Proxy.Type = Proxy.Type.DIRECT,
	val proxyAddress: String = "",
	val proxyPort: Int = 0,
	val proxyLogin: String = "",
	val proxyPassword: String = "",
	// Cloud & About summaries
	val syncSnapshot: SyncStatusSnapshot = SyncStatusSnapshot(),
	val isVerboseLoggingEnabled: Boolean = false,
	val hasLastCrashLog: Boolean = false,
	val roomSchemaVersion: Int = 0,
	// GitHub App Updater
	val isAppUpdateSupported: Boolean = false,
	val isCheckingAppUpdate: Boolean = false,
	val appUpdateStatus: String? = null,
	val availableAppUpdate: AppVersion? = null,
	val isDownloadingAppUpdate: Boolean = false,
	val appUpdateDownloadProgress: Float? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
	@dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
	private val settings: AppSettings,
	private val appLockManager: org.nemuri.app.security.AppLockManager,
	private val storageManager: LocalStorageManager,
	private val trackScheduler: TrackWorker.Scheduler,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	private val appLogger: AppLogger,
	private val cookieJarProvider: Provider<MutableCookieJar>,
	private val databaseProvider: Provider<MangaDatabase>,
	private val syncFeature: SyncFeature,
	private val upscaleModelManager: NeuralUpscaleModelManager,
	private val pageUpscaleEngine: PageUpscaleEngine,
	private val appUpdateRepository: AppUpdateRepository,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val storageStatsFlow = MutableStateFlow(StorageStats())
	private val schemaVersionFlow = MutableStateFlow(0)
	private val neuralCacheVersion = MutableStateFlow(0)
	private val crashLogVersionFlow = MutableStateFlow(0)
	private val isCheckingAppUpdateFlow = MutableStateFlow(false)
	private val appUpdateStatusFlow = MutableStateFlow<String?>(null)

	private data class AppUpdateUiSnapshot(
		val isChecking: Boolean,
		val status: String?,
		val available: AppVersion?,
		val isDownloading: Boolean,
		val progress: Float?,
	)

	private val appUpdateSnapshotFlow = combine(
		isCheckingAppUpdateFlow,
		appUpdateStatusFlow,
		appUpdateRepository.observeAvailableUpdate(),
		appUpdateRepository.observeIsDownloading(),
		appUpdateRepository.observeDownloadProgress(),
	) { checking, status, available, downloading, progress ->
		AppUpdateUiSnapshot(checking, status, available, downloading, progress)
	}

	init {
		refreshStorageStats()
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				databaseProvider.get().openHelper.readableDatabase.version
			}.onSuccess { version ->
				schemaVersionFlow.value = version
			}
		}
	}

	val uiState: StateFlow<SettingsUiState> = combine(
		settings.observeChanges().onStart { emit(null) },
		trackScheduler.observeIsRunning(),
		storageStatsFlow,
		combine(schemaVersionFlow, appUpdateSnapshotFlow) { v, u -> v to u },
		combine(
			upscaleModelManager.downloadState,
			upscaleModelManager.inventoryVersion,
			combine(neuralCacheVersion, crashLogVersionFlow) { a, b -> a + b },
			pageUpscaleEngine.activeDelegateLabel,
			pageUpscaleEngine.cacheBytesFlow,
		) { dl, _, _, activeLabel, cacheBytes -> Triple(dl, activeLabel, cacheBytes) },
	) { _, isTrackerRunning, storageStats, schemaAndUpdate, neuralTriple ->
		val (schemaVersion, updateSnap) = schemaAndUpdate
		val (neuralDl, activeDelegateLabel, neuralCacheBytes) = neuralTriple
		val selectedNeuralId = settings.neuralUpscaleModelId
		val neuralItems = upscaleModelManager.catalog.map { entry ->
			val file = upscaleModelManager.getModelFile(entry.id)
			val bytes = file?.length() ?: entry.approxSizeBytes
			NeuralModelUiItem(
				id = entry.id,
				title = entry.title,
				subtitle = entry.subtitle,
				isDownloadable = entry.downloadUrl != null,
				isDownloaded = file != null,
				isSelected = selectedNeuralId == entry.id,
				sizeLabel = if (bytes <= 0L) "Custom" else String.format("%.1f MB", bytes / (1024f * 1024f)),
			)
		}
		SettingsUiState(
			themeMode = settings.theme,
			isAmoled = settings.isAmoledTheme,
			colorScheme = settings.colorScheme,
			uiScalePercent = settings.uiScalePercent,
			isTitleOverCover = settings.isTitleOverCover,
			mangaCardStyle = settings.mangaCardStyle,
			detailsUiMode = settings.detailsUiMode,
			gridColumnsPortrait = settings.gridColumnsPortrait,
			gridColumnsLandscape = settings.gridColumnsLandscape,
			cardCornerRadiusDp = settings.cardCornerRadiusDp,
			gridGutterDensity = settings.gridGutterDensity,
			coverAspectRatio = settings.coverAspectRatio,
			showUnreadBadge = settings.showUnreadBadge,
			showDownloadedBadge = settings.showDownloadedBadge,
			showSourceBadge = settings.showSourceBadge,
			showBottomProgressBar = settings.showBottomProgressBar,
			isNavBarCompact = settings.isNavBarCompact,
			isLegacyNavigationBar = settings.isLegacyNavigationBar,
			visibleNavTabs = settings.visibleNavTabs,
			selectionMenuStyle = settings.selectionMenuStyle,
			navLabelVisibility = settings.navLabelVisibility,
			isSwipeActionsEnabled = settings.isSwipeActionsEnabled,
			isFastScrollEnabled = settings.isFastScrollEnabled,
			isFastScrollLeftHanded = settings.isFastScrollLeftHanded,
			quickScrollButtonsMode = settings.quickScrollButtonsMode,
			isLastPositionPillEnabled = settings.isLastPositionPillEnabled,
			isPrivateInstall = settings.isPrivateInstallEnabled,
			isShizukuInstaller = settings.isShizukuInstallerEnabled,
			isAutoUpdateExtensions = settings.isAutoUpdateExtensionsEnabled,
			isExtensionUpdateNotifications = settings.isExtensionUpdateNotificationsEnabled,
			contentFilter = settings.contentFilter,
			mihonUserAgentOverride = settings.mihonUserAgentOverride,
			defaultReaderMode = settings.defaultReaderMode,
			isReaderModeDetectionEnabled = settings.isReaderModeDetectionEnabled,
			isReaderKeepScreenOn = settings.isReaderKeepScreenOn,
			isReaderVolumeButtonsEnabled = settings.isReaderVolumeButtonsEnabled,
			is32BitColorsEnabled = settings.is32BitColorsEnabled,
			isPagesNumbersEnabled = settings.isPagesNumbersEnabled,
			isReaderSeamlessStitchEnabled = settings.isReaderSeamlessStitchEnabled,
			readerTapZoneMode = ReaderTapZoneMode.fromKey(settings.readerTapZoneMode),
			readerScrubberEdge = settings.readerScrubberEdge,
			isWebtoonGapsEnabled = settings.isWebtoonGapsEnabled,
			defaultWebtoonZoomOutPercent = settings.defaultWebtoonZoomOutPercent,
			isReaderDoubleTapFill = settings.isReaderDoubleTapFill,
			isReaderZoomButtonsEnabled = settings.isReaderZoomButtonsEnabled,
			readerShaderPreset = ReaderShaderPreset.fromKey(settings.readerShaderMode),
			isReaderDescreenEnabled = settings.isReaderDescreenEnabled,
			isReaderInkLevelsEnabled = settings.isReaderInkLevelsEnabled,
			readerShaderStrength = settings.readerShaderStrength,
			isNeuralUpscaleSupported = pageUpscaleEngine.isSupported,
			isNeuralUpscaleEnabled = settings.isNeuralUpscaleEnabled,
			neuralAccelerator = NeuralAccelerator.fromKey(settings.neuralUpscaleAccelerator),
			activeNeuralDelegateLabel = activeDelegateLabel,
			isNeuralPreloadEnabled = settings.isNeuralPreloadEnabled,
			neuralCatalog = neuralItems,
			neuralDownloadState = neuralDl,
			neuralCacheBytes = neuralCacheBytes,
			epubTheme = settings.epubTheme,
			epubFontFamily = settings.epubFontFamily,
			epubFontSize = settings.epubFontSize,
			epubLineHeight = settings.epubLineHeight,
			epubHorizontalPadding = settings.epubHorizontalPadding,
			preferredDownloadFormat = settings.preferredDownloadFormat,
			meteredNetworkOption = settings.allowDownloadOnMeteredNetwork,
			isAutoCleanupEnabled = settings.isAutoLocalChaptersCleanupEnabled,
			autoCleanupKeepCount = settings.localChaptersCleanupKeep,
			storageStats = storageStats,
			isTrackerEnabled = settings.isTrackerEnabled,
			isTrackerWifiOnly = settings.isTrackerWifiOnly,
			trackerCheckInterval = settings.trackerCheckInterval,
			trackerFrequencyFactor = settings.trackerFrequencyFactor,
			isTrackerNsfwDisabled = settings.isTrackerNsfwDisabled,
			trackerDownloadStrategy = settings.trackerDownloadStrategy,
			isTrackerRunning = isTrackerRunning,
			isFeedUnreadOnly = settings.isFeedUnreadOnly,
			isSuggestionsEnabled = settings.isSuggestionsEnabled,
			isSuggestionsWifiOnly = settings.isSuggestionsWiFiOnly,
			isSuggestionsExcludeNsfw = settings.isSuggestionsExcludeNsfw,
			isSuggestionsExcludeNovels = settings.isSuggestionsExcludeNovels,
			isAppLockEnabled = settings.isAppProtectionEnabled,
			appLockTimeout = settings.appProtectionTimeout,
			isIncognitoEnabled = settings.isIncognitoModeEnabled,
			incognitoForNsfw = settings.incognitoModeForNsfw,
			screenshotsPolicy = settings.screenshotsPolicy,
			isAdBlockEnabled = settings.isAdBlockEnabled,
			isSslBypassEnabled = settings.isSSLBypassEnabled,
			dnsOverHttps = settings.dnsOverHttps,
			proxyType = settings.proxyType,
			proxyAddress = settings.proxyAddress.orEmpty(),
			proxyPort = settings.proxyPort,
			proxyLogin = settings.proxyLogin.orEmpty(),
			proxyPassword = settings.proxyPassword.orEmpty(),
			syncSnapshot = syncFeature.statusSnapshot(),
			isVerboseLoggingEnabled = settings.isVerboseLoggingEnabled,
			hasLastCrashLog = org.nemuri.app.crash.CrashLogStore.hasLastCrashLog(appContext),
			roomSchemaVersion = schemaVersion,
			isAppUpdateSupported = appUpdateRepository.isUpdateSupported(),
			isCheckingAppUpdate = updateSnap.isChecking,
			appUpdateStatus = updateSnap.status,
			availableAppUpdate = updateSnap.available,
			isDownloadingAppUpdate = updateSnap.isDownloading,
			appUpdateDownloadProgress = updateSnap.progress,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = SettingsUiState(),
	)

	// Appearance & Layout Studio
	fun setThemeMode(mode: Int) = settings.setTheme(mode)
	fun setAmoled(enabled: Boolean) = settings.setAmoledTheme(enabled)
	fun setColorScheme(scheme: ColorScheme) = settings.setColorScheme(scheme)
	fun setUiScale(percent: Int) {
		settings.uiScalePercent = percent.coerceIn(75, 125)
	}
	fun setTitleOverCover(enabled: Boolean) {
		settings.isTitleOverCover = enabled
		settings.mangaCardStyle = if (enabled) MangaCardStyle.COMPACT else MangaCardStyle.COMFORTABLE
	}
	fun setMangaCardStyle(style: MangaCardStyle) {
		settings.mangaCardStyle = style
		settings.isTitleOverCover = style != MangaCardStyle.COMFORTABLE
	}
	fun setDetailsUiMode(mode: DetailsUiMode) {
		settings.detailsUiMode = mode
	}
	fun setGridColumnsPortrait(columns: Int) {
		settings.gridColumnsPortrait = columns.coerceIn(2, 6)
	}
	fun setGridColumnsLandscape(columns: Int) {
		settings.gridColumnsLandscape = columns.coerceIn(4, 9)
	}
	fun setCardCornerRadiusDp(dp: Int) {
		settings.cardCornerRadiusDp = dp.coerceIn(0, 24)
	}
	fun setGridGutterDensity(density: GridGutterDensity) {
		settings.gridGutterDensity = density
	}
	fun setCoverAspectRatio(ratio: CoverAspectRatio) {
		settings.coverAspectRatio = ratio
	}
	fun setShowUnreadBadge(show: Boolean) {
		settings.showUnreadBadge = show
	}
	fun setShowDownloadedBadge(show: Boolean) {
		settings.showDownloadedBadge = show
	}
	fun setShowSourceBadge(show: Boolean) {
		settings.showSourceBadge = show
	}
	fun setShowBottomProgressBar(show: Boolean) {
		settings.showBottomProgressBar = show
	}
	fun setNavBarCompact(compact: Boolean) {
		settings.isNavBarCompact = compact
	}
	fun setLegacyNavigationBar(legacy: Boolean) {
		settings.isLegacyNavigationBar = legacy
	}
	fun setSelectionMenuStyle(style: SelectionMenuStyle) {
		settings.selectionMenuStyle = style
	}
	fun setNavLabelVisibility(visibility: NavLabelVisibility) {
		settings.navLabelVisibility = visibility
	}
	fun setSwipeActionsEnabled(enabled: Boolean) {
		settings.isSwipeActionsEnabled = enabled
	}
	fun setFastScrollEnabled(enabled: Boolean) {
		settings.isFastScrollEnabled = enabled
	}
	fun setFastScrollLeftHanded(leftHanded: Boolean) {
		settings.isFastScrollLeftHanded = leftHanded
	}
	fun setQuickScrollButtonsMode(mode: QuickScrollButtonsMode) {
		settings.quickScrollButtonsMode = mode
	}
	fun setLastPositionPillEnabled(enabled: Boolean) {
		settings.isLastPositionPillEnabled = enabled
	}

	// Extensions
	fun setPrivateInstall(enabled: Boolean) {
		settings.isPrivateInstallEnabled = enabled
	}
	fun setShizukuInstaller(enabled: Boolean) {
		settings.isShizukuInstallerEnabled = enabled
	}
	fun setAutoUpdateExtensions(enabled: Boolean) {
		settings.isAutoUpdateExtensionsEnabled = enabled
	}
	fun setExtensionUpdateNotifications(enabled: Boolean) {
		settings.isExtensionUpdateNotificationsEnabled = enabled
	}
	fun setContentFilter(filter: ContentWarning) {
		settings.contentFilter = filter
	}
	fun setMihonUserAgent(ua: String?) {
		settings.setMihonUserAgentOverride(ua)
		toastManager.show(if (ua.isNullOrBlank()) "User-Agent reset to default" else "User-Agent updated")
	}

	// Reader & Novel
	fun setDefaultReaderMode(mode: ReaderMode) = settings.setDefaultReaderMode(mode)
	fun setReaderModeDetection(enabled: Boolean) = settings.setReaderModeDetectionEnabled(enabled)
	fun setKeepScreenOn(enabled: Boolean) = settings.setReaderKeepScreenOn(enabled)
	fun setVolumeButtons(enabled: Boolean) = settings.setReaderVolumeButtonsEnabled(enabled)
	fun set32BitColors(enabled: Boolean) = settings.set32BitColorsEnabled(enabled)
	fun setPagesNumbers(enabled: Boolean) = settings.setPagesNumbersEnabled(enabled)
	fun setReaderSeamlessStitchEnabled(enabled: Boolean) {
		settings.isReaderSeamlessStitchEnabled = enabled
	}
	fun setReaderTapZoneMode(mode: ReaderTapZoneMode) {
		settings.readerTapZoneMode = mode.key
	}
	fun setReaderScrubberEdge(edge: ReaderScrubberEdge) {
		settings.readerScrubberEdge = edge
	}
	fun setWebtoonGapsEnabled(enabled: Boolean) {
		settings.isWebtoonGapsEnabled = enabled
	}
	fun setDefaultWebtoonZoomOutPercent(percent: Int) {
		settings.defaultWebtoonZoomOutPercent = percent.coerceIn(0, 50)
	}
	fun setReaderDoubleTapFill(enabled: Boolean) {
		settings.isReaderDoubleTapFill = enabled
	}
	fun setReaderZoomButtonsEnabled(enabled: Boolean) {
		settings.isReaderZoomButtonsEnabled = enabled
	}
	fun setReaderShaderPreset(preset: ReaderShaderPreset) {
		settings.readerShaderMode = preset.key
		settings.setReaderUpscaleEnabled(
			preset != ReaderShaderPreset.OFF ||
				settings.isReaderDescreenEnabled ||
				settings.isReaderInkLevelsEnabled,
		)
	}
	fun setReaderDescreen(enabled: Boolean) {
		settings.isReaderDescreenEnabled = enabled
		settings.setReaderUpscaleEnabled(
			enabled ||
				settings.readerShaderMode != ReaderShaderPreset.OFF.key ||
				settings.isReaderInkLevelsEnabled,
		)
	}
	fun setReaderInkLevels(enabled: Boolean) {
		settings.isReaderInkLevelsEnabled = enabled
		settings.setReaderUpscaleEnabled(
			enabled ||
				settings.readerShaderMode != ReaderShaderPreset.OFF.key ||
				settings.isReaderDescreenEnabled,
		)
	}
	fun setReaderShaderStrength(strength: Float) {
		settings.readerShaderStrength = strength.coerceIn(0.3f, 1.0f)
	}
	fun setNeuralUpscaleEnabled(enabled: Boolean) {
		settings.isNeuralUpscaleEnabled = enabled
	}
	fun setNeuralAccelerator(accelerator: NeuralAccelerator) {
		settings.neuralUpscaleAccelerator = accelerator.key
	}
	fun setNeuralPreloadEnabled(enabled: Boolean) {
		settings.isNeuralPreloadEnabled = enabled
	}
	fun selectNeuralModel(modelId: String) {
		settings.neuralUpscaleModelId = modelId
		neuralCacheVersion.value += 1
	}
	fun downloadNeuralModel(modelId: String) {
		if (upscaleModelManager.downloadState.value.activeModelId != null) return
		viewModelScope.launch(Dispatchers.IO) {
			upscaleModelManager.downloadModel(modelId).onSuccess {
				if (!settings.isNeuralUpscaleEnabled) {
					settings.isNeuralUpscaleEnabled = true
				}
				neuralCacheVersion.value += 1
				toastManager.show("Neural upscaler model downloaded")
			}.onFailure { err ->
				val msg = err.localizedMessage
				if (!msg.isNullOrBlank() && !msg.contains("cancel", ignoreCase = true)) {
					toastManager.show(msg)
				}
			}
		}
	}
	fun cancelNeuralDownload() {
		upscaleModelManager.cancelDownload()
		toastManager.show("Download cancelled")
	}
	fun importNeuralModelUri(uri: Uri) {
		viewModelScope.launch(Dispatchers.IO) {
			upscaleModelManager.importModelFromUri(uri).onSuccess {
				settings.isNeuralUpscaleEnabled = true
				neuralCacheVersion.value += 1
				toastManager.show("Custom ONNX upscaler imported")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to import ONNX file")
			}
		}
	}
	fun deleteNeuralModel(modelId: String) {
		viewModelScope.launch(Dispatchers.IO) {
			if (upscaleModelManager.deleteModel(modelId)) {
				pageUpscaleEngine.releaseSession()
				neuralCacheVersion.value += 1
				toastManager.show("Deleted neural model")
			}
		}
	}
	fun clearNeuralCache() {
		viewModelScope.launch(Dispatchers.IO) {
			pageUpscaleEngine.clearCache()
			neuralCacheVersion.value += 1
			toastManager.show("Cleared neural upscale cache")
		}
	}
	fun setEpubTheme(theme: String) {
		settings.epubTheme = theme
	}
	fun setEpubFontFamily(family: String) {
		settings.epubFontFamily = family
	}
	fun setEpubFontSize(size: Int) {
		settings.epubFontSize = size
	}
	fun setEpubLineHeight(lineHeight: Int) {
		settings.epubLineHeight = lineHeight
	}
	fun setEpubHorizontalPadding(padding: Int) {
		settings.epubHorizontalPadding = padding
	}

	// Downloads & Storage
	fun setDownloadFormat(format: DownloadFormat) = settings.setPreferredDownloadFormat(format)
	fun setMeteredNetwork(option: TriStateOption) {
		settings.allowDownloadOnMeteredNetwork = option
	}
	fun setAutoCleanup(enabled: Boolean) = settings.setAutoLocalChaptersCleanupEnabled(enabled)
	fun setAutoCleanupKeep(count: Int) = settings.setLocalChaptersCleanupKeep(count)

	fun refreshStorageStats() {
		viewModelScope.launch(Dispatchers.IO) {
			storageStatsFlow.value = storageStatsFlow.value.copy(isLoading = true)
			val downloads = runCatching { storageManager.computeStorageSize() }.getOrDefault(0L)
			val available = runCatching { storageManager.computeAvailableSize() }.getOrDefault(0L)
			val thumbs = runCatching { storageManager.computeCacheSize(CacheDir.THUMBS) }.getOrDefault(0L)
			val favicons = runCatching { storageManager.computeCacheSize(CacheDir.FAVICONS) }.getOrDefault(0L)
			val pages = runCatching { storageManager.computeCacheSize(CacheDir.PAGES) }.getOrDefault(0L)
			storageStatsFlow.value = StorageStats(
				isLoading = false,
				downloadsBytes = downloads,
				availableBytes = available,
				thumbsBytes = thumbs,
				faviconsBytes = favicons,
				pagesBytes = pages,
			)
		}
	}

	fun clearCache(cacheDir: CacheDir) {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching { storageManager.clearCache(cacheDir) }
			toastManager.show("Cache cleared")
			refreshStorageStats()
		}
	}

	fun clearAllCaches() {
		viewModelScope.launch(Dispatchers.IO) {
			CacheDir.entries.forEach { dir ->
				runCatching { storageManager.clearCache(dir) }
			}
			toastManager.show("All caches cleared")
			refreshStorageStats()
		}
	}

	// Updates & Feed
	fun setTrackerEnabled(enabled: Boolean) {
		if (enabled && settings.trackerCheckInterval == TrackerCheckInterval.MANUAL_ONLY) {
			settings.trackerCheckInterval = TrackerCheckInterval.EVERY_12_HOURS
		} else {
			settings.setTrackerEnabled(enabled)
		}
		viewModelScope.launch(Dispatchers.IO) {
			trackScheduler.schedule()
		}
	}
	fun setTrackerWifiOnly(enabled: Boolean) {
		settings.setTrackerWifiOnly(enabled)
		viewModelScope.launch(Dispatchers.IO) {
			trackScheduler.schedule()
		}
	}
	fun setTrackerCheckInterval(interval: TrackerCheckInterval) {
		settings.trackerCheckInterval = interval
		viewModelScope.launch(Dispatchers.IO) {
			trackScheduler.schedule()
		}
	}
	fun setTrackerFrequency(factor: Float) = settings.setTrackerFrequencyFactor(factor)
	fun setTrackerNsfwDisabled(disabled: Boolean) = settings.setTrackerNsfwDisabled(disabled)
	fun setTrackerDownloadStrategy(strategy: TrackerDownloadStrategy) = settings.setTrackerDownloadStrategy(strategy)

	fun checkUpdatesNow() {
		trackScheduler.startNow()
		toastManager.show("Checking library for updates…")
	}

	fun stopUpdatesNow() {
		viewModelScope.launch {
			trackScheduler.stopNow()
			toastManager.show("Update check stopped")
		}
	}

	fun setFeedUnreadOnly(unreadOnly: Boolean) {
		settings.isFeedUnreadOnly = unreadOnly
	}
	fun setSuggestionsEnabled(enabled: Boolean) {
		settings.isSuggestionsEnabled = enabled
	}
	fun setSuggestionsWifiOnly(enabled: Boolean) = settings.setSuggestionsWiFiOnly(enabled)
	fun setSuggestionsExcludeNsfw(enabled: Boolean) = settings.setSuggestionsExcludeNsfw(enabled)
	fun setSuggestionsExcludeNovels(enabled: Boolean) = settings.setSuggestionsExcludeNovels(enabled)

	fun refreshSuggestionsNow() {
		viewModelScope.launch {
			suggestionsScheduler.startNow()
			toastManager.show("Refreshing personalized suggestions…")
		}
	}

	// Privacy & Network
	fun setAppLockEnabled(enabled: Boolean) {
		if (enabled && !appLockManager.canAuthenticateDevice(appContext) && !settings.isAppPasswordSet) {
			toastManager.show("Set up a fingerprint, face unlock, or screen lock PIN in Android Settings first")
			return
		}
		appLockManager.onAppLockPreferenceChanged(enabled)
		toastManager.show(if (enabled) "Biometric / PIN app lock enabled" else "App lock disabled")
	}

	fun setAppLockTimeout(timeout: org.koitharu.kotatsu.core.prefs.AppProtectionTimeout) {
		settings.appProtectionTimeout = timeout
	}

	fun setIncognito(enabled: Boolean) {
		settings.isIncognitoModeEnabled = enabled
	}
	fun setIncognitoForNsfw(option: TriStateOption) {
		settings.incognitoModeForNsfw = option
	}
	fun setScreenshotsPolicy(policy: ScreenshotsPolicy) = settings.setScreenshotsPolicy(policy)
	fun setAdBlock(enabled: Boolean) = settings.setAdBlockEnabled(enabled)
	fun setSslBypass(enabled: Boolean) {
		settings.isSSLBypassEnabled = enabled
	}
	fun setDoH(provider: DoHProvider) = settings.setDnsOverHttps(provider)
	fun setProxy(type: Proxy.Type, address: String?, port: Int, login: String?, password: String?) {
		settings.setProxyConfig(type, address, port, login, password)
		toastManager.show("Proxy configuration saved")
	}

	fun clearWebViewCookies() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching { cookieJarProvider.get().clear() }
			runCatching { CookieManager.getInstance().removeAllCookies(null) }
			toastManager.show("Cookies cleared")
		}
	}

	// About & Diagnostics
	fun setVerboseLogging(enabled: Boolean) {
		settings.isVerboseLoggingEnabled = enabled
		appLogger.setEnabled(enabled)
		toastManager.show(if (enabled) "Verbose logging enabled" else "Verbose logging disabled")
	}

	fun rerunSetupWizard() {
		settings.isOnboardingCompleted = false
		toastManager.show("Setup wizard reopened")
	}

	fun readLastCrashLog(): String? = org.nemuri.app.crash.CrashLogStore.readLastCrashLog(appContext)

	fun clearLastCrashLog() {
		org.nemuri.app.crash.CrashLogStore.clearLastCrashLog(appContext)
		crashLogVersionFlow.value += 1
		toastManager.show("Crash log cleared")
	}

	fun toggleVisibleNavTab(tabName: String) {
		val key = tabName.uppercase()
		if (key == "LIBRARY") {
			toastManager.show("Library tab is always pinned")
			return
		}
		val current = settings.visibleNavTabs.toMutableSet()
		if (key in current) {
			if (current.size <= 2) {
				toastManager.show("Keep at least 2 navigation tabs visible")
				return
			}
			current.remove(key)
		} else {
			current.add(key)
		}
		settings.visibleNavTabs = current
	}

	fun checkForAppUpdateNow() {
		if (!appUpdateRepository.isUpdateSupported()) {
			toastManager.show("App updates are managed by F-Droid on this flavor")
			return
		}
		if (isCheckingAppUpdateFlow.value) return
		viewModelScope.launch(Dispatchers.IO) {
			isCheckingAppUpdateFlow.value = true
			appUpdateStatusFlow.value = "Checking GitHub releases…"
			val update = appUpdateRepository.fetchUpdate(force = true)
			isCheckingAppUpdateFlow.value = false
			if (update != null) {
				appUpdateStatusFlow.value = "Update available: v${update.name}"
				toastManager.show("Update available: v${update.name}")
			} else {
				appUpdateStatusFlow.value = "You are on the latest release"
				toastManager.show("Nemuri is up to date")
			}
		}
	}

	fun installAvailableAppUpdate() {
		val update = appUpdateRepository.observeAvailableUpdate().value ?: return
		viewModelScope.launch(Dispatchers.IO) {
			appUpdateRepository.downloadAndInstallUpdate(update).onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to download update")
			}
		}
	}
}
