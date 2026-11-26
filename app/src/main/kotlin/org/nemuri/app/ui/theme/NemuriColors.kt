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

private val YorhaDarkExtended = ObsidianExtendedColors.copy(
	background = Color(0xFF161512),
	surface = Color(0xFF1E1D19),
	surfaceElevated = Color(0xFF282620),
	card = Color(0xFF21201B),
	dock = Color(0xFF26241F),
	sheet = Color(0xFF24221D),
	toast = Color(0xFF2E2B24),
	border = Color(0xFF3E3B31),
	cyan = Color(0xFFD0C59B),
	teal = Color(0xFFB5A97E),
	tealContainer = Color(0xFF383428),
	onCyan = Color(0xFF191813),
	textPrimary = Color(0xFFEDE8D5),
	textSecondary = Color(0xFFCFC8B0),
	textMuted = Color(0xFF9C9580),
	textDim = Color(0xFF716B59),
)

private val YorhaLightExtended = LightExtendedColors.copy(
	background = Color(0xFFDAD4BE),
	surface = Color(0xFFE4DEC8),
	surfaceElevated = Color(0xFFCEC7AF),
	card = Color(0xFFE7E1CC),
	dock = Color(0xFFD5CEB7),
	sheet = Color(0xFFEAE5D2),
	toast = Color(0xFF3A372D),
	border = Color(0xFFB0A88F),
	cyan = Color(0xFF57523E),
	teal = Color(0xFF6B654E),
	tealContainer = Color(0xFFC6BF9E),
	onCyan = Color(0xFFFDFBEF),
	textPrimary = Color(0xFF25231C),
	textSecondary = Color(0xFF423F34),
	textMuted = Color(0xFF5B5748),
	textDim = Color(0xFF7A7564),
)

private val KyotoWashiDarkExtended = ObsidianExtendedColors.copy(
	background = Color(0xFF151210),
	surface = Color(0xFF1D1916),
	surfaceElevated = Color(0xFF27211D),
	card = Color(0xFF201C18),
	dock = Color(0xFF25201B),
	sheet = Color(0xFF221D19),
	toast = Color(0xFF2E2721),
	border = Color(0xFF3F352E),
	cyan = Color(0xFFE06D53),
	teal = Color(0xFFC85840),
	tealContainer = Color(0xFF422119),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFFF6EFE6),
	textSecondary = Color(0xFFD8CCC0),
	textMuted = Color(0xFFA7998B),
	textDim = Color(0xFF796B5E),
)

private val KyotoWashiLightExtended = LightExtendedColors.copy(
	background = Color(0xFFF6F0E6),
	surface = Color(0xFFFCF8F0),
	surfaceElevated = Color(0xFFECE3D3),
	card = Color(0xFFFCF8F1),
	dock = Color(0xFFF2E9DA),
	sheet = Color(0xFFFCF8F1),
	toast = Color(0xFF2C231D),
	border = Color(0xFFD7CBB8),
	cyan = Color(0xFFC84B31),
	teal = Color(0xFFB33E26),
	tealContainer = Color(0xFFF9DCD4),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF241B15),
	textSecondary = Color(0xFF473A30),
	textMuted = Color(0xFF665649),
	textDim = Color(0xFF877668),
)

private val SuibokugaDarkExtended = ObsidianExtendedColors.copy(
	background = Color(0xFF0E1012),
	surface = Color(0xFF14171A),
	surfaceElevated = Color(0xFF1D2227),
	card = Color(0xFF171B1F),
	dock = Color(0xFF1B2025),
	sheet = Color(0xFF181C21),
	toast = Color(0xFF23282F),
	border = Color(0xFF2C333B),
	cyan = Color(0xFF8FB0C5),
	teal = Color(0xFF7395AB),
	tealContainer = Color(0xFF21313C),
	onCyan = Color(0xFF0C1419),
	textPrimary = Color(0xFFECEFF2),
	textSecondary = Color(0xFFC6CFD7),
	textMuted = Color(0xFF8D99A4),
	textDim = Color(0xFF606B75),
)

private val SuibokugaLightExtended = LightExtendedColors.copy(
	background = Color(0xFFF0F3F5),
	surface = Color(0xFFF9FAFB),
	surfaceElevated = Color(0xFFE3E8EC),
	card = Color(0xFFF9FAFB),
	dock = Color(0xFFE8EDF0),
	sheet = Color(0xFFF8FAFB),
	toast = Color(0xFF1B2126),
	border = Color(0xFFC8D1D8),
	cyan = Color(0xFF3E5B6E),
	teal = Color(0xFF324B5B),
	tealContainer = Color(0xFFD5E2EA),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF14191E),
	textSecondary = Color(0xFF333D45),
	textMuted = Color(0xFF54616C),
	textDim = Color(0xFF74828E),
)

private fun ColorScheme.accentSpec(): AccentSpec = when (this) {
	ColorScheme.DEFAULT, ColorScheme.EXPRESSIVE -> AccentSpec(
		darkPrimary = Color(0xFF5CE1E6),
		lightPrimary = Color(0xFF0D9488),
		darkContainer = Color(0xFF0F363A),
		lightContainer = Color(0xFFCCFBF1),
	)
	ColorScheme.YORHA -> AccentSpec(
		darkPrimary = YorhaDarkExtended.cyan,
		lightPrimary = YorhaLightExtended.cyan,
		darkContainer = YorhaDarkExtended.tealContainer,
		lightContainer = YorhaLightExtended.tealContainer,
		onDarkPrimary = YorhaDarkExtended.onCyan,
	)
	ColorScheme.KYOTO_WASHI -> AccentSpec(
		darkPrimary = KyotoWashiDarkExtended.cyan,
		lightPrimary = KyotoWashiLightExtended.cyan,
		darkContainer = KyotoWashiDarkExtended.tealContainer,
		lightContainer = KyotoWashiLightExtended.tealContainer,
		onDarkPrimary = KyotoWashiDarkExtended.onCyan,
	)
	ColorScheme.SUIBOKUGA -> AccentSpec(
		darkPrimary = SuibokugaDarkExtended.cyan,
		lightPrimary = SuibokugaLightExtended.cyan,
		darkContainer = SuibokugaDarkExtended.tealContainer,
		lightContainer = SuibokugaLightExtended.tealContainer,
		onDarkPrimary = SuibokugaDarkExtended.onCyan,
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
				surfaceContainerLowest = Color.Black,
				surfaceContainerLow = Color(0xFF0A0C12),
				surfaceContainer = Color(0xFF0C0E14),
				surfaceContainerHigh = Color(0xFF0E1118),
				surfaceContainerHighest = Color(0xFF141822),
			)
		} else {
			base
		}
		val dynamicExt = extBase.copy(
			background = adjustedMaterial.background,
			surface = adjustedMaterial.surface,
			surfaceElevated = adjustedMaterial.surfaceContainerHigh,
			card = adjustedMaterial.surfaceContainer,
			dock = adjustedMaterial.surfaceContainerHighest,
			sheet = adjustedMaterial.surfaceContainerLow,
			border = adjustedMaterial.outlineVariant,
			cyan = adjustedMaterial.primary,
			teal = adjustedMaterial.primary,
			tealContainer = adjustedMaterial.primaryContainer,
			onCyan = adjustedMaterial.onPrimary,
			textPrimary = adjustedMaterial.onSurface,
			textSecondary = adjustedMaterial.onSurfaceVariant,
			textMuted = adjustedMaterial.onSurfaceVariant.copy(alpha = 0.78f),
		)
		return adjustedMaterial to dynamicExt
	}

	val ext: NemuriExtendedColors = when (colorScheme) {
		ColorScheme.YORHA -> if (isDark) {
			if (amoled) YorhaDarkExtended.copy(background = Color.Black, surface = Color(0xFF0B0A08), sheet = Color(0xFF12110E)) else YorhaDarkExtended
		} else {
			YorhaLightExtended
		}
		ColorScheme.KYOTO_WASHI -> if (isDark) {
			if (amoled) KyotoWashiDarkExtended.copy(background = Color.Black, surface = Color(0xFF0B0908), sheet = Color(0xFF13100E)) else KyotoWashiDarkExtended
		} else {
			KyotoWashiLightExtended
		}
		ColorScheme.SUIBOKUGA -> if (isDark) {
			if (amoled) SuibokugaDarkExtended.copy(background = Color.Black, surface = Color(0xFF080A0C), sheet = Color(0xFF101316)) else SuibokugaDarkExtended
		} else {
			SuibokugaLightExtended
		}
		else -> {
			val accent = colorScheme.accentSpec()
			if (isDark) {
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
		}
	}

	val material = if (isDark) {
		darkColorScheme(
			primary = ext.cyan,
			onPrimary = ext.onCyan,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = ext.textPrimary,
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
			onSurfaceVariant = ext.textSecondary,
			surfaceContainerLowest = ext.background,
			surfaceContainerLow = ext.sheet,
			surfaceContainer = ext.card,
			surfaceContainerHigh = ext.sheet,
			surfaceContainerHighest = ext.surfaceElevated,
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
			onPrimary = ext.onCyan,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = ext.textPrimary,
			secondary = Color(0xFF0F766E),
			onSecondary = Color.White,
			secondaryContainer = Color(0xFFCCFBF1),
			onSecondaryContainer = ext.textPrimary,
			tertiary = ext.healthy,
			background = ext.background,
			onBackground = ext.textPrimary,
			surface = ext.surface,
			onSurface = ext.textPrimary,
			surfaceVariant = ext.surfaceElevated,
			onSurfaceVariant = ext.textSecondary,
			surfaceContainerLowest = ext.background,
			surfaceContainerLow = ext.sheet,
			surfaceContainer = ext.card,
			surfaceContainerHigh = ext.sheet,
			surfaceContainerHighest = ext.surfaceElevated,
			outline = ext.border,
			outlineVariant = ext.border.copy(alpha = 0.65f),
			error = ext.danger,
			onError = Color.White,
		)
	}
	return material to ext
}
