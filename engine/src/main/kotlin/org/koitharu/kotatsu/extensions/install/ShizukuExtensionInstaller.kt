package org.koitharu.kotatsu.extensions.install

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.extensions.install.shizuku.IShizukuInstallerService
import org.koitharu.kotatsu.extensions.install.shizuku.ShizukuInstallerService
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ShizukuExtensionInstaller @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val installMutex = Mutex()
	private val nextRequestId = AtomicInteger(INSTALL_REQUEST_CODE_BASE)
	private val nextPermissionCode = AtomicInteger(PERMISSION_REQUEST_CODE_BASE)

	private val _stateVersion = MutableStateFlow(0)
	val stateVersion: StateFlow<Int> = _stateVersion.asStateFlow()

	private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
		_stateVersion.update { it + 1 }
	}
	private val binderDeadListener = Shizuku.OnBinderDeadListener {
		_stateVersion.update { it + 1 }
	}
	private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
		_stateVersion.update { it + 1 }
	}

	init {
		runCatching {
			Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
			Shizuku.addBinderDeadListener(binderDeadListener)
			Shizuku.addRequestPermissionResultListener(permissionResultListener)
		}
	}

	val isInstalled: Boolean
		get() = runCatching {
			context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
			true
		}.getOrDefault(false)

	val isBinderAlive: Boolean
		get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

	val isReady: Boolean
		get() = runCatching {
			Shizuku.pingBinder() &&
				!Shizuku.isPreV11() &&
				Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
		}.getOrDefault(false)

	private suspend fun awaitBinder(): Boolean {
		if (isBinderAlive) return true
		return runCatching {
			withTimeout(BINDER_READY_TIMEOUT_MS) {
				suspendCancellableCoroutine { continuation ->
					val listener = object : Shizuku.OnBinderReceivedListener {
						override fun onBinderReceived() {
							Shizuku.removeBinderReceivedListener(this)
							if (continuation.isActive) {
								continuation.resume(isBinderAlive)
							}
						}
					}
					continuation.invokeOnCancellation {
						runCatching { Shizuku.removeBinderReceivedListener(listener) }
					}
					Shizuku.addBinderReceivedListenerSticky(listener)
				}
			}
		}.getOrDefault(false)
	}

	suspend fun awaitReady(): Boolean {
		if (isReady) return true
		if (!awaitBinder()) return false
		return isReady
	}

	suspend fun requestPermissionIfNeeded(): Boolean {
		if (isReady) return true
		if (!awaitBinder()) return false
		if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
			return false
		}
		if (runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
			_stateVersion.update { it + 1 }
			return true
		}
		return runCatching {
			withTimeout(PERMISSION_TIMEOUT_MS) {
				val requestCode = nextPermissionCode.incrementAndGet()
				suspendCancellableCoroutine { continuation ->
					val listener = object : Shizuku.OnRequestPermissionResultListener {
						override fun onRequestPermissionResult(code: Int, grantResult: Int) {
							if (code != requestCode) return
							Shizuku.removeRequestPermissionResultListener(this)
							_stateVersion.update { it + 1 }
							if (continuation.isActive) {
								continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
							}
						}
					}
					continuation.invokeOnCancellation {
						runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
					}
					Shizuku.addRequestPermissionResultListener(listener)
					Shizuku.requestPermission(requestCode)
				}
			}
		}.getOrDefault(isReady)
	}

	suspend fun install(apk: File, expectedPackage: String): InstallResult = installMutex.withLock {
		withContext(Dispatchers.IO) {
			if (!isReady) {
				if (awaitBinder()) {
					requestPermissionIfNeeded()
				}
				if (!isReady) {
					return@withContext InstallResult.Unavailable
				}
			}
			if (!apk.isFile || apk.length() <= 0L) {
				return@withContext InstallResult.InvalidPackage
			}
			val actualPackage = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
			if (actualPackage == null || actualPackage != expectedPackage) {
				return@withContext InstallResult.InvalidPackage
			}

			val args = createUserServiceArgs()
			var connection: ServiceConnection? = null
			try {
				val bound = withTimeout(BIND_TIMEOUT_MS) {
					suspendCancellableCoroutine { continuation ->
						val callback = object : ServiceConnection {
							override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
								if (continuation.isActive) {
									continuation.resume(IShizukuInstallerService.Stub.asInterface(binder))
								}
							}

							override fun onServiceDisconnected(name: ComponentName?) = Unit
						}
						connection = callback
						continuation.invokeOnCancellation {
							runCatching { Shizuku.unbindUserService(args, callback, true) }
						}
						Shizuku.bindUserService(args, callback)
					}
				}
				awaitInstallResult { intentSender ->
					ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
						AssetFileDescriptor(descriptor, 0, apk.length()).use { asset ->
							bound.install(
								asset,
								Process.myUserHandle().hashCode(),
								expectedPackage,
								context.packageName,
								intentSender,
							)
						}
					}
				}
			} catch (e: Exception) {
				InstallResult.Failure(status = null, message = e.message)
			} finally {
				connection?.let { callback ->
					runCatching {
						if (Shizuku.pingBinder()) {
							Shizuku.unbindUserService(args, callback, true)
						}
					}
				}
			}
		}
	}

	private suspend fun awaitInstallResult(start: (IntentSender) -> Unit): InstallResult {
		val reqId = nextRequestId.incrementAndGet()
		val deferred = CompletableDeferred<InstallResult>()
		ShizukuInstallResultReceiver.register(reqId, deferred)
		return try {
			val explicitIntent = Intent(context, ShizukuInstallResultReceiver::class.java)
				.putExtra(ShizukuInstallResultReceiver.EXTRA_REQ_ID, reqId)
			val statusIntent = PendingIntent.getBroadcast(
				context,
				reqId,
				explicitIntent,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
			)
			start(statusIntent.intentSender)
			withTimeout(INSTALL_TIMEOUT_MS) {
				deferred.await()
			}
		} catch (e: Exception) {
			InstallResult.Failure(status = null, message = e.message)
		} finally {
			ShizukuInstallResultReceiver.unregister(reqId)
		}
	}

	private fun createUserServiceArgs() = Shizuku.UserServiceArgs(
		ComponentName(context, ShizukuInstallerService::class.java),
	)
		.tag(USER_SERVICE_TAG)
		.processNameSuffix(USER_SERVICE_TAG)
		.debuggable(BuildConfig.DEBUG)
		.daemon(false)

	sealed interface InstallResult {
		data object Success : InstallResult
		data object Unavailable : InstallResult
		data object InvalidPackage : InstallResult
		data class Failure(val status: Int?, val message: String?) : InstallResult
	}

	private companion object {
		const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
		const val USER_SERVICE_TAG = "extension_installer"
		const val INSTALL_REQUEST_CODE_BASE = 14046
		const val PERMISSION_REQUEST_CODE_BASE = 24046
		const val BINDER_READY_TIMEOUT_MS = 5_000L
		const val PERMISSION_TIMEOUT_MS = 25_000L
		const val BIND_TIMEOUT_MS = 15_000L
		const val INSTALL_TIMEOUT_MS = 180_000L
	}
}
