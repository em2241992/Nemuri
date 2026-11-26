package org.nemuri.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.lnreader.LnPluginManager
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreManager
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

data class NemuriShellUiState(
	val themeMode: Int,
	val isAmoled: Boolean,
	val colorScheme: ColorScheme,
	val isIncognito: Boolean,
	val isOnboardingCompleted: Boolean = true,
	val unreadFeedCount: Int = 0,
	val hasExtensionUpdates: Boolean = false,
	val recentReads: List<Manga> = emptyList(),
	val libraryCount: Int = 0,
	val enabledSourcesCount: Int = 0,
	val installedExtensionsCount: Int = 0,
	val failedExtensionsCount: Int = 0,
	val installedLnPluginsCount: Int = 0,
	val extensionStoresCount: Int = 0,
	val roomSchemaVersion: Int = 0,
	val syncAvailable: Boolean = SyncFeature.IS_AVAILABLE,
)

private data class ThemeAndBehaviorPrefs(
	val themeMode: Int,
	val isAmoled: Boolean,
	val colorScheme: ColorScheme,
	val isIncognito: Boolean,
	val isOnboardingCompleted: Boolean,
)

private data class ExtensionTelemetry(
	val enabledSourcesCount: Int,
	val installedExtensionsCount: Int,
	val failedExtensionsCount: Int,
	val installedLnPluginsCount: Int,
	val extensionStoresCount: Int,
)

@HiltViewModel
class NemuriShellViewModel @Inject constructor(
	private val settings: AppSettings,
	private val databaseProvider: Provider<MangaDatabase>,
	trackingRepository: TrackingRepository,
	extensionStoreManager: ExtensionStoreManager,
	historyRepository: HistoryRepository,
	favouritesRepository: FavouritesRepository,
	sourcesRepository: MangaSourcesRepository,
	mihonExtensionManager: MihonExtensionManager,
	lnPluginManager: LnPluginManager,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val schemaVersionFlow = MutableStateFlow(0)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				databaseProvider.get().openHelper.readableDatabase.version
			}.onSuccess { version ->
				schemaVersionFlow.value = version
			}
		}
	}

	private val prefStateFlow = settings.observeChanges()
		.filter { key ->
			key == null ||
				key == AppSettings.KEY_THEME ||
				key == AppSettings.KEY_THEME_AMOLED ||
				key == AppSettings.KEY_COLOR_THEME ||
				key == AppSettings.KEY_INCOGNITO_MODE ||
				key == AppSettings.KEY_ONBOARDING_COMPLETED ||
				key == AppSettings.KEY_ONBOARDING_INSTALL_ID
		}
		.onStart { emit(null) }
		.map {
			ThemeAndBehaviorPrefs(
				themeMode = settings.theme,
				isAmoled = settings.isAmoledTheme,
				colorScheme = settings.colorScheme,
				isIncognito = settings.isIncognitoModeEnabled,
				isOnboardingCompleted = settings.isOnboardingCompleted,
			)
		}

	private val extensionTelemetryFlow = combine(
		sourcesRepository.observeEnabledSources(),
		mihonExtensionManager.installedExtensions,
		mihonExtensionManager.failedExtensions,
		lnPluginManager.sources,
		extensionStoreManager.states,
	) { enabledSources, installedMihon, failedMihon, lnSources, stores ->
		ExtensionTelemetry(
			enabledSourcesCount = enabledSources.size,
			installedExtensionsCount = installedMihon.size,
			failedExtensionsCount = failedMihon.size,
			installedLnPluginsCount = lnSources.size,
			extensionStoresCount = stores.size,
		)
	}

	private val contentStatsFlow = combine(
		trackingRepository.observeUnreadUpdatesCount(),
		extensionStoreManager.hasUpdates,
		historyRepository.observeAll().map { it.take(4) },
		favouritesRepository.observeMangaCount(),
		schemaVersionFlow,
	) { unreadFeed, hasUpdates, recent, libCount, schemaVer ->
		ContentTelemetry(
			unreadFeedCount = unreadFeed,
			hasExtensionUpdates = hasUpdates,
			recentReads = recent,
			libraryCount = libCount,
			roomSchemaVersion = schemaVer,
		)
	}

	val uiState: StateFlow<NemuriShellUiState> = combine(
		prefStateFlow,
		extensionTelemetryFlow,
		contentStatsFlow,
	) { prefs, ext, content ->
		NemuriShellUiState(
			themeMode = prefs.themeMode,
			isAmoled = prefs.isAmoled,
			colorScheme = prefs.colorScheme,
			isIncognito = prefs.isIncognito,
			isOnboardingCompleted = prefs.isOnboardingCompleted,
			unreadFeedCount = content.unreadFeedCount,
			hasExtensionUpdates = content.hasExtensionUpdates,
			recentReads = content.recentReads,
			libraryCount = content.libraryCount,
			enabledSourcesCount = ext.enabledSourcesCount,
			installedExtensionsCount = ext.installedExtensionsCount,
			failedExtensionsCount = ext.failedExtensionsCount,
			installedLnPluginsCount = ext.installedLnPluginsCount,
			extensionStoresCount = ext.extensionStoresCount,
			roomSchemaVersion = content.roomSchemaVersion,
			syncAvailable = SyncFeature.IS_AVAILABLE,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = NemuriShellUiState(
				themeMode = settings.theme,
				isAmoled = settings.isAmoledTheme,
				colorScheme = settings.colorScheme,
				isIncognito = settings.isIncognitoModeEnabled,
				isOnboardingCompleted = settings.isOnboardingCompleted,
			),
		)

	fun completeOnboarding() {
		settings.isOnboardingCompleted = true
	}

	fun resetOnboarding() {
		settings.isOnboardingCompleted = false
	}

	fun toggleIncognito() {
		val next = !settings.isIncognitoModeEnabled
		settings.isIncognitoModeEnabled = next
		toastManager.show(if (next) "Incognito mode enabled" else "Incognito mode disabled")
	}

	fun showToast(message: String) {
		toastManager.show(message)
	}

	private data class ContentTelemetry(
		val unreadFeedCount: Int,
		val hasExtensionUpdates: Boolean,
		val recentReads: List<Manga>,
		val libraryCount: Int,
		val roomSchemaVersion: Int,
	)
}
