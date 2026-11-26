package org.nemuri.app.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.sync.SyncStatusSnapshot
import org.nemuri.app.ui.components.NemuriToastManager
import java.io.IOException
import javax.inject.Inject

@Immutable
data class OnboardingUiState(
	val themeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
	val colorScheme: ColorScheme = ColorScheme.DEFAULT,
	val isAmoled: Boolean = false,
	val storageSummary: String? = null,
	val isCustomStorageSelected: Boolean = false,
	val syncAvailable: Boolean = SyncFeature.IS_AVAILABLE,
	val syncSnapshot: SyncStatusSnapshot = SyncStatusSnapshot(isAvailable = SyncFeature.IS_AVAILABLE),
	val isSyncBusy: Boolean = false,
	val isSyncing: Boolean = false,
	val syncCompletedCount: Int = 0,
	val libraryCount: Int = 0,
	val enabledSourcesCount: Int = 0,
)

private data class OnboardingThemeSnapshot(
	val themeMode: Int,
	val colorScheme: ColorScheme,
	val isAmoled: Boolean,
	val hasCustomStorage: Boolean,
)

private data class OnboardingStorageState(
	val summary: String? = null,
	val isCustom: Boolean = false,
)

private data class OnboardingSyncInternalState(
	val snapshot: SyncStatusSnapshot,
	val isBusy: Boolean = false,
	val completedCount: Int = 0,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val localStorageManager: LocalStorageManager,
	private val syncFeature: SyncFeature,
	favouritesRepository: FavouritesRepository,
	sourcesRepository: MangaSourcesRepository,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val storageStateFlow = MutableStateFlow(
		OnboardingStorageState(isCustom = settings.mangaStorageDir != null),
	)
	private val syncInternalFlow = MutableStateFlow(
		OnboardingSyncInternalState(snapshot = syncFeature.statusSnapshot()),
	)

	private val prefsFlow = settings.observeChanges()
		.filter { key ->
			key == null ||
				key == AppSettings.KEY_THEME ||
				key == AppSettings.KEY_COLOR_THEME ||
				key == AppSettings.KEY_THEME_AMOLED ||
				key == AppSettings.KEY_LOCAL_STORAGE
		}
		.onStart { emit(null) }
		.map {
			OnboardingThemeSnapshot(
				themeMode = settings.theme,
				colorScheme = settings.colorScheme,
				isAmoled = settings.isAmoledTheme,
				hasCustomStorage = settings.mangaStorageDir != null,
			)
		}

	private val countsFlow = combine(
		favouritesRepository.observeMangaCount(),
		sourcesRepository.observeEnabledSources().map { it.size },
		syncFeature.observeIsSyncing(),
	) { libCount, sourcesCount, isSyncing ->
		Triple(libCount, sourcesCount, isSyncing)
	}

	val uiState: StateFlow<OnboardingUiState> = combine(
		prefsFlow,
		storageStateFlow,
		syncInternalFlow,
		countsFlow,
	) { prefs, storage, syncInternal, (libCount, sourcesCount, isSyncing) ->
		OnboardingUiState(
			themeMode = prefs.themeMode,
			colorScheme = prefs.colorScheme,
			isAmoled = prefs.isAmoled,
			storageSummary = storage.summary,
			isCustomStorageSelected = storage.isCustom || prefs.hasCustomStorage,
			syncAvailable = SyncFeature.IS_AVAILABLE,
			syncSnapshot = syncInternal.snapshot,
			isSyncBusy = syncInternal.isBusy,
			isSyncing = isSyncing,
			syncCompletedCount = syncInternal.completedCount,
			libraryCount = libCount,
			enabledSourcesCount = sourcesCount,
		)
	}
		.flowOn(Dispatchers.IO)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = OnboardingUiState(
				themeMode = settings.theme,
				colorScheme = settings.colorScheme,
				isAmoled = settings.isAmoledTheme,
				isCustomStorageSelected = settings.mangaStorageDir != null,
				syncSnapshot = syncFeature.statusSnapshot(),
			),
		)

	init {
		refreshStorageSummary()
	}

	fun setThemeMode(mode: Int) {
		settings.setTheme(mode)
		if (mode == AppCompatDelegate.MODE_NIGHT_NO && settings.isAmoledTheme) {
			settings.setAmoledTheme(false)
		}
		AppCompatDelegate.setDefaultNightMode(mode)
	}

	fun setColorScheme(scheme: ColorScheme) {
		settings.setColorScheme(scheme)
	}

	fun setAmoled(enabled: Boolean) {
		settings.setAmoledTheme(enabled)
	}

	fun refreshStorageSummary() {
		viewModelScope.launch(Dispatchers.IO) {
			val customDir = settings.mangaStorageDir
			val resolvedDir = localStorageManager.getDefaultWriteableDir()
			val display = resolvedDir?.let { dir ->
				localStorageManager.getDirectoryDisplayName(dir, isFullPath = true)
			}
			storageStateFlow.value = OnboardingStorageState(
				summary = display,
				isCustom = customDir != null,
			)
		}
	}

	fun selectStorageDirectory(uri: Uri) {
		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					localStorageManager.takePermissions(uri)
					val dir = localStorageManager.resolveUri(uri)
					if (!dir.exists() && !dir.mkdirs()) {
						throw IOException("Could not create or access selected folder")
					}
					if (!dir.canWrite()) {
						throw IOException("Selected folder is read-only")
					}
					runCatching { localStorageManager.setDirIsNoMedia(dir) }
					settings.mangaStorageDir = dir
					localStorageManager.getDirectoryDisplayName(dir, isFullPath = true)
				}
			}
			result.onSuccess { pathLabel ->
				storageStateFlow.value = OnboardingStorageState(
					summary = pathLabel,
					isCustom = true,
				)
				toastManager.show("Downloads folder set to $pathLabel")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Could not use selected folder")
				refreshStorageSummary()
			}
		}
	}

	fun refreshSyncStatus() {
		syncInternalFlow.value = syncInternalFlow.value.copy(
			snapshot = syncFeature.statusSnapshot(),
		)
	}

	fun beginGoogleSignIn(onLaunchIntentSender: (IntentSenderRequest) -> Unit) {
		if (!SyncFeature.IS_AVAILABLE || syncInternalFlow.value.isBusy) return
		viewModelScope.launch {
			syncInternalFlow.value = syncInternalFlow.value.copy(isBusy = true)
			runCatching {
				val pendingIntent = syncFeature.beginSignIn()
				if (pendingIntent != null) {
					onLaunchIntentSender(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
				} else {
					syncFeature.onSignedIn()
					syncInternalFlow.value = syncInternalFlow.value.copy(
						completedCount = syncInternalFlow.value.completedCount + 1,
					)
					toastManager.show("Google Drive sync connected")
				}
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Google Drive sign-in failed")
			}
			syncInternalFlow.value = syncInternalFlow.value.copy(
				isBusy = false,
				snapshot = syncFeature.statusSnapshot(),
			)
		}
	}

	fun handleGoogleSignInResult(resultCode: Int, data: Intent?) {
		if (resultCode != Activity.RESULT_OK) {
			toastManager.show("Google Drive sign-in cancelled")
			return
		}
		viewModelScope.launch {
			syncInternalFlow.value = syncInternalFlow.value.copy(isBusy = true)
			runCatching {
				syncFeature.finishSignIn(data)
				syncFeature.onSignedIn()
				syncInternalFlow.value = syncInternalFlow.value.copy(
					completedCount = syncInternalFlow.value.completedCount + 1,
				)
				toastManager.show("Google Drive sync enabled")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Could not complete Google Drive sign-in")
			}
			syncInternalFlow.value = syncInternalFlow.value.copy(
				isBusy = false,
				snapshot = syncFeature.statusSnapshot(),
			)
		}
	}

	fun triggerSyncNow() {
		if (!SyncFeature.IS_AVAILABLE || syncInternalFlow.value.isBusy) return
		viewModelScope.launch {
			syncInternalFlow.value = syncInternalFlow.value.copy(isBusy = true)
			val err = runCatching { syncFeature.syncNow() }
				.getOrElse { it.localizedMessage ?: "Sync failed" }
			toastManager.show(err ?: "Google Drive sync completed")
			syncInternalFlow.value = syncInternalFlow.value.copy(
				isBusy = false,
				snapshot = syncFeature.statusSnapshot(),
			)
		}
	}

	fun completeOnboarding() {
		settings.isOnboardingCompleted = true
	}
}
