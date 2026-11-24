package org.nemuri.app.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.sync.SyncFeature
import org.koitharu.kotatsu.sync.SyncStatusSnapshot
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

data class CloudSyncUiState(
	val snapshot: SyncStatusSnapshot = SyncStatusSnapshot(isAvailable = SyncFeature.IS_AVAILABLE),
	val isSyncing: Boolean = false,
	val isBusy: Boolean = false,
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
	private val syncFeature: SyncFeature,
	val toastManager: NemuriToastManager,
) : ViewModel() {

	private val snapshotFlow = MutableStateFlow(syncFeature.statusSnapshot())
	private val busyFlow = MutableStateFlow(false)

	val uiState: StateFlow<CloudSyncUiState> = combine(
		snapshotFlow,
		syncFeature.observeIsSyncing(),
		busyFlow,
	) { snapshot, isSyncing, isBusy ->
		CloudSyncUiState(
			snapshot = snapshot,
			isSyncing = isSyncing,
			isBusy = isBusy,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = CloudSyncUiState(snapshot = syncFeature.statusSnapshot()),
	)

	fun refresh() {
		snapshotFlow.value = syncFeature.statusSnapshot()
	}

	fun beginSignIn(onLaunchIntentSender: (IntentSenderRequest) -> Unit) {
		if (!SyncFeature.IS_AVAILABLE || busyFlow.value) return
		viewModelScope.launch {
			busyFlow.value = true
			runCatching {
				val pendingIntent = syncFeature.beginSignIn()
				if (pendingIntent != null) {
					onLaunchIntentSender(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
				} else {
					syncFeature.onSignedIn()
					toastManager.show("Google Drive sync connected")
				}
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Google Drive sign-in failed")
			}
			busyFlow.value = false
			refresh()
		}
	}

	fun handleSignInResult(resultCode: Int, data: Intent?) {
		if (resultCode != Activity.RESULT_OK) {
			toastManager.show("Google Drive sign-in cancelled")
			return
		}
		viewModelScope.launch {
			busyFlow.value = true
			runCatching {
				syncFeature.finishSignIn(data)
				syncFeature.onSignedIn()
				toastManager.show("Google Drive sync enabled")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Could not complete Google Drive sign-in")
			}
			busyFlow.value = false
			refresh()
		}
	}

	fun syncNow() {
		if (!SyncFeature.IS_AVAILABLE || busyFlow.value) return
		viewModelScope.launch {
			busyFlow.value = true
			val error = runCatching { syncFeature.syncNow() }
				.getOrElse { it.localizedMessage ?: "Sync failed" }
			if (error == null) {
				toastManager.show("Google Drive sync completed")
			} else {
				toastManager.show(error)
			}
			busyFlow.value = false
			refresh()
		}
	}

	fun signOut() {
		viewModelScope.launch {
			busyFlow.value = true
			runCatching { syncFeature.signOut() }
			busyFlow.value = false
			refresh()
			toastManager.show("Signed out of Google Drive")
		}
	}

	fun deleteRemoteData() {
		viewModelScope.launch {
			busyFlow.value = true
			val error = runCatching { syncFeature.deleteRemoteData() }
				.getOrElse { it.localizedMessage ?: "Failed to delete cloud data" }
			busyFlow.value = false
			refresh()
			toastManager.show(error ?: "Cloud sync data deleted from Google Drive")
		}
	}

	fun setIntervalMinutes(minutes: Int) {
		viewModelScope.launch {
			runCatching { syncFeature.setIntervalMinutes(minutes) }
			refresh()
		}
	}

	fun setWifiOnly(wifiOnly: Boolean) {
		viewModelScope.launch {
			runCatching { syncFeature.setWifiOnly(wifiOnly) }
			refresh()
		}
	}

	fun setEmailHidden(hidden: Boolean) {
		syncFeature.setEmailHidden(hidden)
		refresh()
	}

	fun toggleContent(key: String) {
		val current = snapshotFlow.value.enabledContent.toMutableSet()
		if (key in current) current.remove(key) else current.add(key)
		syncFeature.setEnabledContent(current)
		refresh()
	}
}
