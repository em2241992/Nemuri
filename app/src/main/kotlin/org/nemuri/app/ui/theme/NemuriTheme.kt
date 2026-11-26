package org.nemuri.app.ui.theme

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.koitharu.kotatsu.core.prefs.ColorScheme

val LocalIsYorhaVisor: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

object NemuriTheme {
	val colors: NemuriExtendedColors
		@Composable
		@ReadOnlyComposable
		get() = LocalNemuriColors.current

	val isYorhaVisor: Boolean
		@Composable
		@ReadOnlyComposable
		get() = LocalIsYorhaVisor.current
}

@Composable
fun NemuriTheme(
	themeMode: Int = AppCompatDelegate.MODE_NIGHT_YES,
	amoled: Boolean = false,
	colorScheme: ColorScheme = ColorScheme.DEFAULT,
	content: @Composable () -> Unit,
) {
	val systemDark = isSystemInDarkTheme()
	val isDark = when (themeMode) {
		AppCompatDelegate.MODE_NIGHT_NO -> false
		AppCompatDelegate.MODE_NIGHT_YES -> true
		else -> systemDark || themeMode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
	}
	val context = LocalContext.current
	val (materialColors, extendedColors) = buildMaterialColorScheme(
		context = context,
		isDark = isDark,
		amoled = amoled || colorScheme == ColorScheme.TRUE_AMOLED,
		colorScheme = colorScheme,
	)
	val isYorhaVisor = colorScheme == ColorScheme.YORHA_VISOR_V2

	val view = LocalView.current
	val reduceMotion = rememberShouldReduceMotion()
	if (!view.isInEditMode) {
		SideEffect {
			val window = (view.context as? Activity)?.window ?: return@SideEffect
			val insetsController = WindowCompat.getInsetsController(window, view)
			insetsController.isAppearanceLightStatusBars = !isDark
			insetsController.isAppearanceLightNavigationBars = !isDark
		}
	}

	CompositionLocalProvider(
		LocalNemuriColors provides extendedColors,
		LocalIsYorhaVisor provides isYorhaVisor,
		LocalReducedMotion provides reduceMotion,
	) {
		MaterialTheme(
			colorScheme = materialColors,
			motionScheme = if (reduceMotion) androidx.compose.material3.MotionScheme.standard() else androidx.compose.material3.MotionScheme.expressive(),
			typography = NemuriTypography,
			content = content,
		)
	}
}
