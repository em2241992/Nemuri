package org.koitharu.kotatsu.sync

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import org.koitharu.kotatsu.sync.data.SyncSettings
import org.koitharu.kotatsu.sync.domain.GoogleDriveSyncRepository
import org.koitharu.kotatsu.sync.domain.SyncResult
import org.koitharu.kotatsu.sync.work.SyncTrigger
import org.koitharu.kotatsu.sync.work.SyncWorker
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Google Drive sync as the shared code sees it. GitHub version only: the F-Droid version has its
 * own SyncFeature without sync, because Drive sign-in needs Google Play Services.
 */
@Singleton
class SyncFeature @Inject constructor(
	private val syncSettings: SyncSettings,
	private val scheduler: SyncWorker.Scheduler,
	private val trigger: SyncTrigger,
	private val repository: Provider<GoogleDriveSyncRepository>,
) {

	val periodicScheduler: PeriodicWorkScheduler? get() = scheduler

	val isPeriodicEnabled: Boolean get() = syncSettings.isSignedIn && syncSettings.intervalMinutes > 0

	/** Syncs on foreground and every few minutes while the app is open (see [SyncTrigger]). */
	fun startTriggers() = trigger.init()

	/** The consent screen to launch, or null when access was already granted. */
	suspend fun beginSignIn(): PendingIntent? = withContext(Dispatchers.IO) {
		repository.get().beginSignIn()
	}

	suspend fun finishSignIn(data: Intent?) = withContext(Dispatchers.IO) {
		repository.get().finishSignIn(data)
	}

	suspend fun onSignedIn() {
		scheduler.schedule()
		scheduler.syncSoon() // bring the library over now, not at the next periodic run
	}

	fun statusSnapshot(): SyncStatusSnapshot = SyncStatusSnapshot(
		isAvailable = true,
		isSignedIn = syncSettings.isSignedIn,
		accountName = syncSettings.accountName,
		accountEmail = syncSettings.accountEmail,
		accountPhotoUrl = syncSettings.accountPhotoUrl,
		isEmailHidden = syncSettings.isEmailHidden,
		intervalMinutes = syncSettings.intervalMinutes,
		isWifiOnly = syncSettings.isWifiOnly,
		enabledContent = syncSettings.enabledContent.toSet(),
		lastSyncTimestamp = syncSettings.lastSyncTimestamp,
		lastSyncError = syncSettings.lastSyncError,
	)

	fun observeIsSyncing(): Flow<Boolean> = flow {
		val repo = withContext(Dispatchers.IO) { repository.get() }
		emitAll(repo.isSyncing)
	}

	suspend fun syncNow(): String? = withContext(Dispatchers.IO) {
		when (val result = repository.get().sync(force = true)) {
			is SyncResult.Success -> null
			is SyncResult.SignInRequired -> "Sign-in required"
			is SyncResult.Error -> result.message ?: "Sync failed"
		}
	}

	suspend fun signOut() = withContext(Dispatchers.IO) {
		scheduler.unschedule()
		repository.get().signOut()
	}

	suspend fun deleteRemoteData(): String? = withContext(Dispatchers.IO) {
		when (val result = repository.get().deleteRemoteData()) {
			is SyncResult.Success -> null
			is SyncResult.SignInRequired -> "Sign-in required"
			is SyncResult.Error -> result.message ?: "Failed to delete cloud data"
		}
	}

	suspend fun setIntervalMinutes(minutes: Int) {
		syncSettings.intervalMinutes = minutes
		scheduler.schedule()
	}

	suspend fun setWifiOnly(wifiOnly: Boolean) {
		syncSettings.isWifiOnly = wifiOnly
		scheduler.schedule()
	}

	fun setEmailHidden(hidden: Boolean) {
		syncSettings.isEmailHidden = hidden
	}

	fun setEnabledContent(keys: Set<String>) {
		syncSettings.enabledContent = keys
	}

	companion object {

		const val IS_AVAILABLE = true
	}
}
