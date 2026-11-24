package org.nemuri.app.ui.settings

import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatDelegate
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
import org.koitharu.kotatsu.core.logs.AppLogger
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.core.network.DoHProvider
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.prefs.DownloadFormat
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.koitharu.kotatsu.core.prefs.TrackerDownloadStrategy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.suggestions.ui.SuggestionsWorker
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.sync.SyncStatusSnapshot
import org.koitharu.kotatsu.tracker.work.TrackWorker
import org.nemuri.app.ui.components.NemuriToastManager
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Provider

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
	// Appearance
	val themeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
	val isAmoled: Boolean = false,
	val colorScheme: ColorScheme = ColorScheme.DEFAULT,
	val uiScalePercent: Int = 100,
	val isTitleOverCover: Boolean = true,
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
	val roomSchemaVersion: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val settings: AppSettings,
	private val storageManager: LocalStorageManager,
	private val trackScheduler: TrackWorker.Scheduler,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	private val appLogger: AppLogger,
	private val cookieJarProvider: Provider<MutableCookieJar>,
	private val databaseProvider: Provider<MangaDatabase>,
	private val syncFeature: SyncFeature,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val storageStatsFlow = MutableStateFlow(StorageStats())
	private val schemaVersionFlow = MutableStateFlow(0)

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
		schemaVersionFlow,
	) { _, isTrackerRunning, storageStats, schemaVersion ->
		SettingsUiState(
			themeMode = settings.theme,
			isAmoled = settings.isAmoledTheme,
			colorScheme = settings.colorScheme,
			uiScalePercent = settings.uiScalePercent,
			isTitleOverCover = settings.isTitleOverCover,
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
			trackerFrequencyFactor = settings.trackerFrequencyFactor,
			isTrackerNsfwDisabled = settings.isTrackerNsfwDisabled,
			trackerDownloadStrategy = settings.trackerDownloadStrategy,
			isTrackerRunning = isTrackerRunning,
			isFeedUnreadOnly = settings.isFeedUnreadOnly,
			isSuggestionsEnabled = settings.isSuggestionsEnabled,
			isSuggestionsWifiOnly = settings.isSuggestionsWiFiOnly,
			isSuggestionsExcludeNsfw = settings.isSuggestionsExcludeNsfw,
			isSuggestionsExcludeNovels = settings.isSuggestionsExcludeNovels,
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
			roomSchemaVersion = schemaVersion,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = SettingsUiState(),
	)

	// Appearance
	fun setThemeMode(mode: Int) = settings.setTheme(mode)
	fun setAmoled(enabled: Boolean) = settings.setAmoledTheme(enabled)
	fun setColorScheme(scheme: ColorScheme) = settings.setColorScheme(scheme)
	fun setUiScale(percent: Int) {
		settings.uiScalePercent = percent.coerceIn(75, 125)
	}
	fun setTitleOverCover(enabled: Boolean) {
		settings.isTitleOverCover = enabled
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
	fun setTrackerEnabled(enabled: Boolean) = settings.setTrackerEnabled(enabled)
	fun setTrackerWifiOnly(enabled: Boolean) = settings.setTrackerWifiOnly(enabled)
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
}
