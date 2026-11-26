package org.nemuri.app.security

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppLockEntryPoint {
	fun appLockManager(): AppLockManager
}

@Singleton
class AppLockManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) : DefaultLifecycleObserver {

	private val _isLocked = MutableStateFlow(settings.isAppProtectionEnabled)
	val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

	@Volatile
	private var lastBackgroundElapsedMs: Long = -1L

	init {
		Handler(Looper.getMainLooper()).post {
			ProcessLifecycleOwner.get().lifecycle.addObserver(this)
		}
	}

	override fun onStop(owner: LifecycleOwner) {
		if (!settings.isAppProtectionEnabled) {
			lastBackgroundElapsedMs = -1L
			return
		}
		lastBackgroundElapsedMs = SystemClock.elapsedRealtime()
		if (settings.appProtectionTimeoutMillis <= 0L) {
			_isLocked.value = true
		}
	}

	override fun onStart(owner: LifecycleOwner) {
		if (!settings.isAppProtectionEnabled) {
			_isLocked.value = false
			lastBackgroundElapsedMs = -1L
			return
		}
		val bgAt = lastBackgroundElapsedMs
		if (bgAt > 0L) {
			val elapsedAway = (SystemClock.elapsedRealtime() - bgAt).coerceAtLeast(0L)
			if (elapsedAway >= settings.appProtectionTimeoutMillis) {
				_isLocked.value = true
			}
			lastBackgroundElapsedMs = -1L
		}
	}

	fun unlock() {
		lastBackgroundElapsedMs = -1L
		_isLocked.value = false
	}

	fun lockNowIfEnabled() {
		if (settings.isAppProtectionEnabled) {
			_isLocked.value = true
		}
	}

	fun onAppLockPreferenceChanged(enabled: Boolean) {
		settings.isAppProtectionEnabled = enabled
		if (!enabled) {
			lastBackgroundElapsedMs = -1L
			_isLocked.value = false
		}
	}

	fun canAuthenticateDevice(checkContext: Context = context): Boolean {
		val bm = BiometricManager.from(checkContext)
		val status = bm.canAuthenticate(ALLOWED_AUTHENTICATORS)
		return status == BiometricManager.BIOMETRIC_SUCCESS
	}

	companion object {
		const val ALLOWED_AUTHENTICATORS: Int =
			BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

		fun from(context: Context): AppLockManager =
			EntryPointAccessors.fromApplication(context.applicationContext, AppLockEntryPoint::class.java)
				.appLockManager()
	}
}
