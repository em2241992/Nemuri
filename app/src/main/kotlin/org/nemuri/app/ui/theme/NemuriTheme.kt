package org.nemuri.app.ui.theme

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.koitharu.kotatsu.core.prefs.ColorScheme

val LocalIsYorhaVisor: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
val LocalIsSharpTacticalGeometry: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

private val SharpTacticalShapes = Shapes(
	extraSmall = RoundedCornerShape(2.dp),
	small = RoundedCornerShape(2.dp),
	medium = RoundedCornerShape(2.dp),
	large = RoundedCornerShape(2.dp),
	extraLarge = RoundedCornerShape(2.dp),
)

object NemuriTheme {
	val colors: NemuriExtendedColors
		@Composable
		@ReadOnlyComposable
		get() = LocalNemuriColors.current

	val isYorhaVisor: Boolean
		@Composable
		@ReadOnlyComposable
		get() = LocalIsYorhaVisor.current

	val isSharpTacticalGeometry: Boolean
		@Composable
		@ReadOnlyComposable
		get() = LocalIsSharpTacticalGeometry.current

	@Composable
	@ReadOnlyComposable
	fun shape(defaultRadius: Dp): RoundedCornerShape =
		if (LocalIsSharpTacticalGeometry.current) RoundedCornerShape(2.dp) else RoundedCornerShape(defaultRadius)

	@Composable
	@ReadOnlyComposable
	fun pillShape(): RoundedCornerShape =
		if (LocalIsSharpTacticalGeometry.current) RoundedCornerShape(2.dp) else RoundedCornerShape(999.dp)
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
		amoled = amoled,
		colorScheme = colorScheme,
	)
	val isYorhaVisor = colorScheme == ColorScheme.YORHA_VISOR_V2
	val isSharpTacticalGeometry = colorScheme == ColorScheme.YORHA || colorScheme == ColorScheme.YORHA_VISOR_V2

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
		LocalIsSharpTacticalGeometry provides isSharpTacticalGeometry,
		LocalReducedMotion provides reduceMotion,
	) {
		MaterialTheme(
			colorScheme = materialColors,
			shapes = if (isSharpTacticalGeometry) SharpTacticalShapes else MaterialTheme.shapes,
			motionScheme = if (reduceMotion) androidx.compose.material3.MotionScheme.standard() else androidx.compose.material3.MotionScheme.expressive(),
			typography = NemuriTypography,
			content = content,
		)
	}
}
