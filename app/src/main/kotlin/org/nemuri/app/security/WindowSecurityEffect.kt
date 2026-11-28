package org.nemuri.app.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.nemuri.app.ui.components.rememberAppSettings

private data class WindowSecurityConfig(
	val policy: ScreenshotsPolicy,
	val globalIncognito: Boolean,
	val appProtectionEnabled: Boolean,
)

/**
 * Dynamically applies or clears [WindowManager.LayoutParams.FLAG_SECURE] and Android 13+
 * Recents task-snapshot protection whenever [ScreenshotsPolicy], Incognito state, or App Lock changes.
 */
@Composable
fun ObserveWindowSecurity(
	isSessionIncognito: Boolean = false,
	isNsfwContent: Boolean = false,
	isAppLocked: Boolean = false,
	allowScreenshotsOverride: Boolean = false,
) {
	val context = LocalContext.current
	val activity = context as? Activity ?: return
	val settings = rememberAppSettings()

	val configFlow = remember(settings) {
		settings.observeChanges()
			.filter { key ->
				key == null ||
					key == AppSettings.KEY_SCREENSHOTS_POLICY ||
					key == AppSettings.KEY_INCOGNITO_MODE ||
					key == AppSettings.KEY_PROTECT_APP
			}
			.onStart { emit(null) }
			.map {
				WindowSecurityConfig(
					policy = settings.screenshotsPolicy,
					globalIncognito = settings.isIncognitoModeEnabled,
					appProtectionEnabled = settings.isAppProtectionEnabled,
				)
			}
	}

	val config by configFlow.collectAsState(
		initial = remember(settings) {
			WindowSecurityConfig(
				policy = settings.screenshotsPolicy,
				globalIncognito = settings.isIncognitoModeEnabled,
				appProtectionEnabled = settings.isAppProtectionEnabled,
			)
		},
	)

	val effectiveIncognito = isSessionIncognito || config.globalIncognito
	val shouldBlockScreenshotsAndRecents = !allowScreenshotsOverride && (isAppLocked || when (config.policy) {
		ScreenshotsPolicy.ALLOW -> false
		ScreenshotsPolicy.BLOCK_INCOGNITO -> effectiveIncognito
		ScreenshotsPolicy.BLOCK_NSFW -> effectiveIncognito || isNsfwContent
		ScreenshotsPolicy.BLOCK_ALL -> true
	})
	val shouldHideRecentsPreview = shouldBlockScreenshotsAndRecents || config.appProtectionEnabled

	LaunchedEffect(shouldBlockScreenshotsAndRecents, shouldHideRecentsPreview) {
		if (shouldBlockScreenshotsAndRecents) {
			activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
		} else {
			activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			runCatching {
				activity.setRecentsScreenshotEnabled(!shouldHideRecentsPreview)
			}
		}
	}
}
