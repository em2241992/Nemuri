package org.koitharu.kotatsu.extensions.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit non-exported [BroadcastReceiver] for `PackageInstaller.Session.commit` callbacks
 * initiated through Shizuku. Android 14+ (`targetSdk >= 34`) throws `IllegalArgumentException`
 * if a `FLAG_MUTABLE` `PendingIntent` wraps an implicit `Intent`.
 */
class ShizukuInstallResultReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context?, intent: Intent?) {
		if (intent == null) return
		val reqId = intent.getIntExtra(EXTRA_REQ_ID, -1)
		val status = intent.getIntExtra(
			PackageInstaller.EXTRA_STATUS,
			PackageInstaller.STATUS_FAILURE,
		)

		if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && context != null) {
			@Suppress("DEPRECATION")
			val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
			if (confirmIntent != null) {
				val launched = runCatching {
					context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				}.isSuccess
				if (launched) {
					// Keep the session's CompletableDeferred registered until the final status arrives.
					return
				}
			}
		}

		val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
		val result = if (status == PackageInstaller.STATUS_SUCCESS) {
			ShizukuExtensionInstaller.InstallResult.Success
		} else {
			ShizukuExtensionInstaller.InstallResult.Failure(status = status, message = message)
		}

		val deferred = if (reqId != -1) {
			pendingSessions.remove(reqId)
		} else {
			pendingSessions.keys.firstOrNull()?.let(pendingSessions::remove)
		}
		deferred?.complete(result)
	}

	internal companion object {
		const val EXTRA_REQ_ID = "org.koitharu.kotatsu.extensions.install.EXTRA_REQ_ID"

		private val pendingSessions =
			ConcurrentHashMap<Int, CompletableDeferred<ShizukuExtensionInstaller.InstallResult>>()

		fun register(
			reqId: Int,
			deferred: CompletableDeferred<ShizukuExtensionInstaller.InstallResult>,
		) {
			pendingSessions[reqId] = deferred
		}

		fun unregister(reqId: Int) {
			pendingSessions.remove(reqId)
		}
	}
}
