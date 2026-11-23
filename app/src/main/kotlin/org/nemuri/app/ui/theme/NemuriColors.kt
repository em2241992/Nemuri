package org.nemuri.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme as MaterialColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.koitharu.kotatsu.core.prefs.ColorScheme

@Immutable
data class NemuriExtendedColors(
	val isDark: Boolean,
	val background: Color,
	val surface: Color,
	val surfaceElevated: Color,
	val card: Color,
	val dock: Color,
	val sheet: Color,
	val toast: Color,
	val border: Color,
	val cyan: Color,
	val teal: Color,
	val tealContainer: Color,
	val onCyan: Color,
	val textPrimary: Color,
	val textSecondary: Color,
	val textMuted: Color,
	val textDim: Color,
	val healthy: Color,
	val amber: Color,
	val danger: Color,
	val mergePurple: Color,
)

val ObsidianExtendedColors = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF090C10),
	surface = Color(0xFF12161F),
	surfaceElevated = Color(0xFF181D29),
	card = Color(0xFF141822),
	dock = Color(0xFF1C2224),
	sheet = Color(0xFF121620),
	toast = Color(0xFF232830),
	border = Color(0xFF232B3A),
	cyan = Color(0xFF5CE1E6),
	teal = Color(0xFF4CCAD0),
	tealContainer = Color(0xFF0F363A),
	onCyan = Color(0xFF071318),
	textPrimary = Color(0xFFF1F5F9),
	textSecondary = Color(0xFFCBD5E1),
	textMuted = Color(0xFF94A3B8),
	textDim = Color(0xFF64748B),
	healthy = Color(0xFF10B981),
	amber = Color(0xFFF59E0B),
	danger = Color(0xFFF43F5E),
	mergePurple = Color(0xFFA855F7),
)

val AmoledExtendedColors = ObsidianExtendedColors.copy(
	background = Color(0xFF000000),
	surface = Color(0xFF07080C),
	surfaceElevated = Color(0xFF10131B),
	card = Color(0xFF0C0E14),
	dock = Color(0xFF0D1014),
	sheet = Color(0xFF0A0C12),
	toast = Color(0xFF181C24),
	border = Color(0xFF1A202C),
)

val LightExtendedColors = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFFAF8F5),
	surface = Color(0xFFFFFFFF),
	surfaceElevated = Color(0xFFF1ECE4),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFF4F2EE),
	sheet = Color(0xFFFFFFFF),
	toast = Color(0xFF1E242C),
	border = Color(0xFFD6D1C7),
	cyan = Color(0xFF0D9488),
	teal = Color(0xFF0D9488),
	tealContainer = Color(0xFFCCFBF1),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF18181B),
	textSecondary = Color(0xFF3F3F46),
	textMuted = Color(0xFF52525B),
	textDim = Color(0xFF71717A),
	healthy = Color(0xFF059669),
	amber = Color(0xFFD97706),
	danger = Color(0xFFE11D48),
	mergePurple = Color(0xFF7C3AED),
)

val LocalNemuriColors = staticCompositionLocalOf { ObsidianExtendedColors }

private data class AccentSpec(
	val darkPrimary: Color,
	val lightPrimary: Color,
	val darkContainer: Color,
	val lightContainer: Color,
	val onDarkPrimary: Color = Color(0xFF041F22),
)

private fun ColorScheme.accentSpec(): AccentSpec = when (this) {
	ColorScheme.DEFAULT, ColorScheme.EXPRESSIVE -> AccentSpec(
		darkPrimary = Color(0xFF5CE1E6),
		lightPrimary = Color(0xFF0D9488),
		darkContainer = Color(0xFF0F363A),
		lightContainer = Color(0xFFCCFBF1),
	)
	ColorScheme.MIKU -> AccentSpec(
		darkPrimary = Color(0xFF4CCAD0),
		lightPrimary = Color(0xFF0F766E),
		darkContainer = Color(0xFF0E3A3D),
		lightContainer = Color(0xFFCCFBF1),
	)
	ColorScheme.RENA, ColorScheme.SHANA -> AccentSpec(
		darkPrimary = Color(0xFFFF4D6D),
		lightPrimary = Color(0xFFE11D48),
		darkContainer = Color(0xFF4C1121),
		lightContainer = Color(0xFFFFE4E6),
		onDarkPrimary = Color.White,
	)
	ColorScheme.FROG -> AccentSpec(
		darkPrimary = Color(0xFF34D399),
		lightPrimary = Color(0xFF059669),
		darkContainer = Color(0xFF0C3B2E),
		lightContainer = Color(0xFFD1FAE5),
	)
	ColorScheme.BLUEBERRY, ColorScheme.SKY -> AccentSpec(
		darkPrimary = Color(0xFF60A5FA),
		lightPrimary = Color(0xFF2563EB),
		darkContainer = Color(0xFF162C52),
		lightContainer = Color(0xFFDBEAFE),
	)
	ColorScheme.SAKURA, ColorScheme.MAMIMI -> AccentSpec(
		darkPrimary = Color(0xFFF472B6),
		lightPrimary = Color(0xFFDB2777),
		darkContainer = Color(0xFF4A1836),
		lightContainer = Color(0xFFFCE7F3),
		onDarkPrimary = Color(0xFF28081B),
	)
	ColorScheme.KANADE, ColorScheme.ITSUKA -> AccentSpec(
		darkPrimary = Color(0xFFA78BFA),
		lightPrimary = Color(0xFF7C3AED),
		darkContainer = Color(0xFF2B1E57),
		lightContainer = Color(0xFFEDE9FE),
	)
	ColorScheme.LIME -> AccentSpec(
		darkPrimary = Color(0xFF84CC16),
		lightPrimary = Color(0xFF4D7C0F),
		darkContainer = Color(0xFF243B0B),
		lightContainer = Color(0xFFECFCCB),
	)
	ColorScheme.AMBER -> AccentSpec(
		darkPrimary = Color(0xFFFBBF24),
		lightPrimary = Color(0xFFD97706),
		darkContainer = Color(0xFF452A06),
		lightContainer = Color(0xFFFEF3C7),
	)
}

fun buildMaterialColorScheme(
	context: Context,
	isDark: Boolean,
	amoled: Boolean,
	colorScheme: ColorScheme,
): Pair<MaterialColorScheme, NemuriExtendedColors> {
	if (colorScheme == ColorScheme.EXPRESSIVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		val extBase = if (isDark) {
			if (amoled) AmoledExtendedColors else ObsidianExtendedColors
		} else {
			LightExtendedColors
		}
		val adjustedMaterial = if (isDark && amoled) {
			base.copy(
				background = Color.Black,
				surface = Color(0xFF07080C),
				surfaceVariant = Color(0xFF10131B),
			)
		} else {
			base
		}
		return adjustedMaterial to extBase.copy(
			cyan = adjustedMaterial.primary,
			teal = adjustedMaterial.primary,
			tealContainer = adjustedMaterial.primaryContainer,
			onCyan = adjustedMaterial.onPrimary,
		)
	}

	val accent = colorScheme.accentSpec()
	val ext = if (isDark) {
		val base = if (amoled) AmoledExtendedColors else ObsidianExtendedColors
		base.copy(
			cyan = accent.darkPrimary,
			teal = accent.darkPrimary,
			tealContainer = accent.darkContainer,
			onCyan = accent.onDarkPrimary,
		)
	} else {
		LightExtendedColors.copy(
			cyan = accent.lightPrimary,
			teal = accent.lightPrimary,
			tealContainer = accent.lightContainer,
			onCyan = Color.White,
		)
	}

	val material = if (isDark) {
		darkColorScheme(
			primary = ext.cyan,
			onPrimary = ext.onCyan,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = Color(0xFFF1F5F9),
			secondary = Color(0xFF2DD4BF),
			onSecondary = Color(0xFF042220),
			secondaryContainer = Color(0xFF132D33),
			onSecondaryContainer = Color(0xFFCCFAF5),
			tertiary = ext.healthy,
			background = ext.background,
			onBackground = ext.textPrimary,
			surface = ext.surface,
			onSurface = ext.textPrimary,
			surfaceVariant = ext.surfaceElevated,
			onSurfaceVariant = ext.textMuted,
			outline = ext.border,
			outlineVariant = ext.border.copy(alpha = 0.65f),
			error = ext.danger,
			onError = Color.White,
			errorContainer = Color(0xFF4A1220),
			onErrorContainer = Color(0xFFFFD9DF),
		)
	} else {
		lightColorScheme(
			primary = ext.cyan,
			onPrimary = Color.White,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = Color(0xFF0F172A),
			secondary = Color(0xFF0F766E),
			onSecondary = Color.White,
			secondaryContainer = Color(0xFFCCFBF1),
			onSecondaryContainer = Color(0xFF0F172A),
			tertiary = ext.healthy,
			background = ext.background,
			onBackground = ext.textPrimary,
			surface = ext.surface,
			onSurface = ext.textPrimary,
			surfaceVariant = ext.surfaceElevated,
			onSurfaceVariant = ext.textMuted,
			outline = ext.border,
			outlineVariant = ext.border.copy(alpha = 0.65f),
			error = ext.danger,
			onError = Color.White,
		)
	}
	return material to ext
}
