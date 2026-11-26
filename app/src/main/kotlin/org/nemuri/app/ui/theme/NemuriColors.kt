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

@Immutable
data class PalettePreviewSwatch(
	val background: Color,
	val surface: Color,
	val card: Color,
	val dock: Color,
	val accent: Color,
	val accentContainer: Color,
	val onAccent: Color,
	val textPrimary: Color,
	val textMuted: Color,
	val border: Color,
	val isVisorHud: Boolean = false,
)

// 1. DEFAULT — Nemuri Obsidian
val ObsidianExtendedColors = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF090C10),
	surface = Color(0xFF12161F),
	surfaceElevated = Color(0xFF181D29),
	card = Color(0xFF141822),
	dock = Color(0xFF161B24),
	sheet = Color(0xFF121620),
	toast = Color(0xFF202632),
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

// 2. TRUE_AMOLED — Pure OLED Black
val AmoledExtendedColors = ObsidianExtendedColors.copy(
	background = Color(0xFF000000),
	surface = Color(0xFF07080C),
	surfaceElevated = Color(0xFF10131B),
	card = Color(0xFF0B0E14),
	dock = Color(0xFF0D1017),
	sheet = Color(0xFF090C12),
	toast = Color(0xFF161A24),
	border = Color(0xFF1A212E),
)

private val TrueAmoledLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFFFFFFF),
	surface = Color(0xFFF8FAFC),
	surfaceElevated = Color(0xFFEEF2F6),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFF1F5F9),
	sheet = Color(0xFFFFFFFF),
	toast = Color(0xFF0F172A),
	border = Color(0xFFCBD5E1),
	cyan = Color(0xFF0891B2),
	teal = Color(0xFF0E7490),
	tealContainer = Color(0xFFCFFAFE),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF090D16),
	textSecondary = Color(0xFF1E293B),
	textMuted = Color(0xFF475569),
	textDim = Color(0xFF64748B),
	healthy = Color(0xFF059669),
	amber = Color(0xFFD97706),
	danger = Color(0xFFE11D48),
	mergePurple = Color(0xFF7C3AED),
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
	teal = Color(0xFF0F766E),
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

// 3. BEDTIME_AMBER — Bedtime Warm Amber
private val BedtimeAmberDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF0E0B08),
	surface = Color(0xFF14100C),
	surfaceElevated = Color(0xFF1F1812),
	card = Color(0xFF1A140F),
	dock = Color(0xFF231B14),
	sheet = Color(0xFF18120E),
	toast = Color(0xFF2B2118),
	border = Color(0xFF33281E),
	cyan = Color(0xFFE6A15C),
	teal = Color(0xFFD48B42),
	tealContainer = Color(0xFF3B2612),
	onCyan = Color(0xFF1F1205),
	textPrimary = Color(0xFFF2E6D8),
	textSecondary = Color(0xFFD6C3AE),
	textMuted = Color(0xFFA38E78),
	textDim = Color(0xFF756350),
	healthy = Color(0xFF6BB37B),
	amber = Color(0xFFE6A15C),
	danger = Color(0xFFE06C5A),
	mergePurple = Color(0xFFB583D6),
)

private val BedtimeAmberLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFFBF5EC),
	surface = Color(0xFFFFFAF2),
	surfaceElevated = Color(0xFFF2E6D5),
	card = Color(0xFFFFFBF4),
	dock = Color(0xFFF5EBDC),
	sheet = Color(0xFFFFFAF2),
	toast = Color(0xFF2B1F14),
	border = Color(0xFFDFCEB9),
	cyan = Color(0xFF9C5708),
	teal = Color(0xFF854804),
	tealContainer = Color(0xFFF9E2C4),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF261A0E),
	textSecondary = Color(0xFF4A3723),
	textMuted = Color(0xFF6E563E),
	textDim = Color(0xFF8F765C),
	healthy = Color(0xFF2E7D47),
	amber = Color(0xFFB45309),
	danger = Color(0xFFC23B2B),
	mergePurple = Color(0xFF7E46A8),
)

// 4. YORHA — YoRHa Tactical
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
	healthy = Color(0xFF84B07A),
	amber = Color(0xFFD4A459),
	danger = Color(0xFFCC665C),
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
	healthy = Color(0xFF3F6B3B),
	amber = Color(0xFF916820),
	danger = Color(0xFFA63A32),
)

// 5. YORHA_VISOR_V2 — YoRHa Visor v2 (Android HUD Grid)
private val YorhaVisorV2DarkExtended = ObsidianExtendedColors.copy(
	background = Color(0xFF11110E),
	surface = Color(0xFF191814),
	surfaceElevated = Color(0xFF23211B),
	card = Color(0xFF1D1C17),
	dock = Color(0xFF212019),
	sheet = Color(0xFF1B1A15),
	toast = Color(0xFF2B2920),
	border = Color(0xFF4A4638),
	cyan = Color(0xFFDCD4B0),
	teal = Color(0xFFC2BA95),
	tealContainer = Color(0xFF363326),
	onCyan = Color(0xFF14130E),
	textPrimary = Color(0xFFF2EED9),
	textSecondary = Color(0xFFD5CEB5),
	textMuted = Color(0xFFA09A82),
	textDim = Color(0xFF75705C),
	healthy = Color(0xFF8DB580),
	amber = Color(0xFFD9A75A),
	danger = Color(0xFFCF665A),
	mergePurple = Color(0xFFA899C2),
)

private val YorhaVisorV2LightExtended = LightExtendedColors.copy(
	background = Color(0xFFD4CEB8),
	surface = Color(0xFFDFD9C3),
	surfaceElevated = Color(0xFFC8C1AA),
	card = Color(0xFFE3DEC9),
	dock = Color(0xFFCEC7B0),
	sheet = Color(0xFFE5DFCA),
	toast = Color(0xFF2E2C22),
	border = Color(0xFFA69F87),
	cyan = Color(0xFF4A4635),
	teal = Color(0xFF5C5743),
	tealContainer = Color(0xFFBFBA9F),
	onCyan = Color(0xFFF7F4E6),
	textPrimary = Color(0xFF1F1D16),
	textSecondary = Color(0xFF3B382C),
	textMuted = Color(0xFF575343),
	textDim = Color(0xFF75705D),
	healthy = Color(0xFF3D6838),
	amber = Color(0xFF8C631C),
	danger = Color(0xFFA1362E),
	mergePurple = Color(0xFF63537A),
)

// 6. KYOTO_WASHI — Warm Sumi & Vermilion
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

// 7. SUIBOKUGA — Sumi Ink Wash & Slate Mist
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

val LocalNemuriColors = staticCompositionLocalOf { ObsidianExtendedColors }

private data class AccentSpec(
	val darkPrimary: Color,
	val lightPrimary: Color,
	val darkContainer: Color,
	val lightContainer: Color,
	val onDarkPrimary: Color = Color(0xFF041F22),
)

private fun ColorScheme.accentSpec(): AccentSpec = when (this) {
	ColorScheme.DEFAULT,
	ColorScheme.EXPRESSIVE,
	ColorScheme.TRUE_AMOLED,
	-> AccentSpec(
		darkPrimary = Color(0xFF5CE1E6),
		lightPrimary = Color(0xFF0D9488),
		darkContainer = Color(0xFF0F363A),
		lightContainer = Color(0xFFCCFBF1),
	)
	ColorScheme.BEDTIME_AMBER -> AccentSpec(
		darkPrimary = BedtimeAmberDarkExtended.cyan,
		lightPrimary = BedtimeAmberLightExtended.cyan,
		darkContainer = BedtimeAmberDarkExtended.tealContainer,
		lightContainer = BedtimeAmberLightExtended.tealContainer,
		onDarkPrimary = BedtimeAmberDarkExtended.onCyan,
	)
	ColorScheme.YORHA -> AccentSpec(
		darkPrimary = YorhaDarkExtended.cyan,
		lightPrimary = YorhaLightExtended.cyan,
		darkContainer = YorhaDarkExtended.tealContainer,
		lightContainer = YorhaLightExtended.tealContainer,
		onDarkPrimary = YorhaDarkExtended.onCyan,
	)
	ColorScheme.YORHA_VISOR_V2 -> AccentSpec(
		darkPrimary = YorhaVisorV2DarkExtended.cyan,
		lightPrimary = YorhaVisorV2LightExtended.cyan,
		darkContainer = YorhaVisorV2DarkExtended.tealContainer,
		lightContainer = YorhaVisorV2LightExtended.tealContainer,
		onDarkPrimary = YorhaVisorV2DarkExtended.onCyan,
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

private fun NemuriExtendedColors.toAmoledVariant(
	surfaceColor: Color,
	elevatedColor: Color,
	cardColor: Color,
	dockColor: Color,
	sheetColor: Color,
	borderColor: Color,
): NemuriExtendedColors = copy(
	background = Color.Black,
	surface = surfaceColor,
	surfaceElevated = elevatedColor,
	card = cardColor,
	dock = dockColor,
	sheet = sheetColor,
	border = borderColor,
)

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
				surfaceContainerHigh = Color(0xFF10131B),
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
			toast = adjustedMaterial.inverseSurface,
			border = adjustedMaterial.outlineVariant,
			cyan = adjustedMaterial.primary,
			teal = adjustedMaterial.secondary,
			tealContainer = adjustedMaterial.primaryContainer,
			onCyan = adjustedMaterial.onPrimary,
			textPrimary = adjustedMaterial.onSurface,
			textSecondary = adjustedMaterial.onSurfaceVariant,
			textMuted = adjustedMaterial.onSurfaceVariant.copy(alpha = 0.78f),
			textDim = adjustedMaterial.onSurfaceVariant.copy(alpha = 0.56f),
		)
		return adjustedMaterial to dynamicExt
	}

	val ext: NemuriExtendedColors = when (colorScheme) {
		ColorScheme.DEFAULT,
		ColorScheme.EXPRESSIVE,
		-> if (isDark) {
			if (amoled) AmoledExtendedColors else ObsidianExtendedColors
		} else {
			LightExtendedColors
		}

		ColorScheme.TRUE_AMOLED -> if (isDark) {
			AmoledExtendedColors
		} else {
			TrueAmoledLightExtended
		}

		ColorScheme.BEDTIME_AMBER -> if (isDark) {
			if (amoled) {
				BedtimeAmberDarkExtended.toAmoledVariant(
					surfaceColor = Color(0xFF090705),
					elevatedColor = Color(0xFF16110C),
					cardColor = Color(0xFF110D09),
					dockColor = Color(0xFF18120D),
					sheetColor = Color(0xFF0E0B08),
					borderColor = Color(0xFF291F16),
				)
			} else {
				BedtimeAmberDarkExtended
			}
		} else {
			BedtimeAmberLightExtended
		}

		ColorScheme.YORHA -> if (isDark) {
			if (amoled) {
				YorhaDarkExtended.toAmoledVariant(
					surfaceColor = Color(0xFF0B0A08),
					elevatedColor = Color(0xFF171612),
					cardColor = Color(0xFF11100D),
					dockColor = Color(0xFF161511),
					sheetColor = Color(0xFF100F0C),
					borderColor = Color(0xFF2E2C24),
				)
			} else {
				YorhaDarkExtended
			}
		} else {
			YorhaLightExtended
		}

		ColorScheme.YORHA_VISOR_V2 -> if (isDark) {
			if (amoled) {
				YorhaVisorV2DarkExtended.toAmoledVariant(
					surfaceColor = Color(0xFF0A0A08),
					elevatedColor = Color(0xFF161511),
					cardColor = Color(0xFF11100D),
					dockColor = Color(0xFF151410),
					sheetColor = Color(0xFF0E0D0A),
					borderColor = Color(0xFF3A372B),
				)
			} else {
				YorhaVisorV2DarkExtended
			}
		} else {
			YorhaVisorV2LightExtended
		}

		ColorScheme.KYOTO_WASHI -> if (isDark) {
			if (amoled) {
				KyotoWashiDarkExtended.toAmoledVariant(
					surfaceColor = Color(0xFF0B0908),
					elevatedColor = Color(0xFF181411),
					cardColor = Color(0xFF120F0D),
					dockColor = Color(0xFF171310),
					sheetColor = Color(0xFF100D0B),
					borderColor = Color(0xFF302822),
				)
			} else {
				KyotoWashiDarkExtended
			}
		} else {
			KyotoWashiLightExtended
		}

		ColorScheme.SUIBOKUGA -> if (isDark) {
			if (amoled) {
				SuibokugaDarkExtended.toAmoledVariant(
					surfaceColor = Color(0xFF080A0C),
					elevatedColor = Color(0xFF13171B),
					cardColor = Color(0xFF0E1114),
					dockColor = Color(0xFF12161A),
					sheetColor = Color(0xFF0C0F12),
					borderColor = Color(0xFF22282F),
				)
			} else {
				SuibokugaDarkExtended
			}
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

	val onContainerColor = if (isDark) ext.cyan else ext.cyan
	val material = if (isDark) {
		darkColorScheme(
			primary = ext.cyan,
			onPrimary = ext.onCyan,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = onContainerColor,
			secondary = ext.teal,
			onSecondary = ext.onCyan,
			secondaryContainer = ext.tealContainer,
			onSecondaryContainer = ext.textPrimary,
			tertiary = ext.healthy,
			onTertiary = Color(0xFF052014),
			tertiaryContainer = ext.healthy.copy(alpha = 0.22f),
			onTertiaryContainer = ext.textPrimary,
			background = ext.background,
			onBackground = ext.textPrimary,
			surface = ext.surface,
			onSurface = ext.textPrimary,
			surfaceVariant = ext.surfaceElevated,
			onSurfaceVariant = ext.textSecondary,
			surfaceTint = ext.teal,
			inverseSurface = ext.textPrimary,
			inverseOnSurface = ext.background,
			inversePrimary = ext.teal,
			surfaceContainerLowest = ext.background,
			surfaceContainerLow = ext.sheet,
			surfaceContainer = ext.card,
			surfaceContainerHigh = ext.surfaceElevated,
			surfaceContainerHighest = ext.dock,
			outline = ext.border,
			outlineVariant = ext.border.copy(alpha = 0.65f),
			scrim = Color.Black.copy(alpha = 0.68f),
			error = ext.danger,
			onError = Color.White,
			errorContainer = ext.danger.copy(alpha = 0.24f),
			onErrorContainer = ext.textPrimary,
		)
	} else {
		lightColorScheme(
			primary = ext.cyan,
			onPrimary = ext.onCyan,
			primaryContainer = ext.tealContainer,
			onPrimaryContainer = onContainerColor,
			secondary = ext.teal,
			onSecondary = ext.onCyan,
			secondaryContainer = ext.tealContainer,
			onSecondaryContainer = ext.textPrimary,
			tertiary = ext.healthy,
			onTertiary = Color.White,
			tertiaryContainer = ext.healthy.copy(alpha = 0.16f),
			onTertiaryContainer = ext.textPrimary,
			background = ext.background,
			onBackground = ext.textPrimary,
			surface = ext.surface,
			onSurface = ext.textPrimary,
			surfaceVariant = ext.surfaceElevated,
			onSurfaceVariant = ext.textSecondary,
			surfaceTint = ext.teal,
			inverseSurface = ext.toast,
			inverseOnSurface = Color.White,
			inversePrimary = ext.cyan,
			surfaceContainerLowest = ext.background,
			surfaceContainerLow = ext.sheet,
			surfaceContainer = ext.card,
			surfaceContainerHigh = ext.surfaceElevated,
			surfaceContainerHighest = ext.dock,
			outline = ext.border,
			outlineVariant = ext.border.copy(alpha = 0.65f),
			scrim = Color.Black.copy(alpha = 0.45f),
			error = ext.danger,
			onError = Color.White,
			errorContainer = ext.danger.copy(alpha = 0.14f),
			onErrorContainer = ext.danger,
		)
	}
	return material to ext
}

fun paletteDisplayName(scheme: ColorScheme): String = when (scheme) {
	ColorScheme.EXPRESSIVE -> "Dynamic M3"
	ColorScheme.DEFAULT -> "Nemuri Obsidian"
	ColorScheme.TRUE_AMOLED -> "True AMOLED"
	ColorScheme.BEDTIME_AMBER -> "Bedtime Warm Amber"
	ColorScheme.YORHA -> "YoRHa Tactical"
	ColorScheme.YORHA_VISOR_V2 -> "YoRHa Visor v2"
	ColorScheme.KYOTO_WASHI -> "Kyoto Washi"
	ColorScheme.SUIBOKUGA -> "Suibokuga"
	ColorScheme.MIKU -> "Miku"
	ColorScheme.RENA -> "Asuka"
	ColorScheme.FROG -> "Mion"
	ColorScheme.BLUEBERRY -> "Rikka"
	ColorScheme.SAKURA -> "Sakura"
	ColorScheme.MAMIMI -> "Mamimi"
	ColorScheme.KANADE -> "Kanade"
	ColorScheme.ITSUKA -> "Itsuka"
	ColorScheme.SHANA -> "Shana"
	ColorScheme.LIME -> "Remon"
	ColorScheme.AMBER -> "Amber"
	ColorScheme.SKY -> "Sky"
}

fun paletteTagline(scheme: ColorScheme): String = when (scheme) {
	ColorScheme.EXPRESSIVE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		"Wallpaper-synced Material You"
	} else {
		"Obsidian fallback (< Android 12)"
	}
	ColorScheme.DEFAULT -> "Deep slate & luminous cyan"
	ColorScheme.TRUE_AMOLED -> "Zero-glow #000000 OLED contrast"
	ColorScheme.BEDTIME_AMBER -> "Low-blue candlelight & cream"
	ColorScheme.YORHA -> "Bunker sand & warm olive"
	ColorScheme.YORHA_VISOR_V2 -> "Android tactical HUD grid"
	ColorScheme.KYOTO_WASHI -> "Handmade paper & vermilion"
	ColorScheme.SUIBOKUGA -> "Sumi ink wash & slate mist"
	else -> "Classic accent preset"
}

fun palettePreviewSwatch(
	scheme: ColorScheme,
	isDark: Boolean = true,
	amoled: Boolean = false,
	context: Context? = null,
): PalettePreviewSwatch {
	if (scheme == ColorScheme.EXPRESSIVE && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val (_, ext) = buildMaterialColorScheme(context, isDark = isDark, amoled = amoled, colorScheme = scheme)
		return PalettePreviewSwatch(
			background = ext.background,
			surface = ext.surface,
			card = ext.card,
			dock = ext.dock,
			accent = ext.cyan,
			accentContainer = ext.tealContainer,
			onAccent = ext.onCyan,
			textPrimary = ext.textPrimary,
			textMuted = ext.textMuted,
			border = ext.border,
			isVisorHud = false,
		)
	}
	val resolvedExt = when (scheme) {
		ColorScheme.TRUE_AMOLED -> if (isDark) AmoledExtendedColors else TrueAmoledLightExtended
		ColorScheme.BEDTIME_AMBER -> if (isDark) {
			if (amoled) BedtimeAmberDarkExtended.copy(background = Color.Black, surface = Color(0xFF090705)) else BedtimeAmberDarkExtended
		} else {
			BedtimeAmberLightExtended
		}
		ColorScheme.YORHA -> if (isDark) {
			if (amoled) YorhaDarkExtended.copy(background = Color.Black, surface = Color(0xFF0B0A08)) else YorhaDarkExtended
		} else {
			YorhaLightExtended
		}
		ColorScheme.YORHA_VISOR_V2 -> if (isDark) {
			if (amoled) YorhaVisorV2DarkExtended.copy(background = Color.Black, surface = Color(0xFF0A0A08)) else YorhaVisorV2DarkExtended
		} else {
			YorhaVisorV2LightExtended
		}
		ColorScheme.KYOTO_WASHI -> if (isDark) {
			if (amoled) KyotoWashiDarkExtended.copy(background = Color.Black, surface = Color(0xFF0B0908)) else KyotoWashiDarkExtended
		} else {
			KyotoWashiLightExtended
		}
		ColorScheme.SUIBOKUGA -> if (isDark) {
			if (amoled) SuibokugaDarkExtended.copy(background = Color.Black, surface = Color(0xFF080A0C)) else SuibokugaDarkExtended
		} else {
			SuibokugaLightExtended
		}
		else -> {
			val accent = scheme.accentSpec()
			if (isDark) {
				val base = if (amoled) AmoledExtendedColors else ObsidianExtendedColors
				base.copy(
					cyan = accent.darkPrimary,
					tealContainer = accent.darkContainer,
					onCyan = accent.onDarkPrimary,
				)
			} else {
				LightExtendedColors.copy(
					cyan = accent.lightPrimary,
					tealContainer = accent.lightContainer,
					onCyan = Color.White,
				)
			}
		}
	}
	return PalettePreviewSwatch(
		background = resolvedExt.background,
		surface = resolvedExt.surface,
		card = resolvedExt.card,
		dock = resolvedExt.dock,
		accent = resolvedExt.cyan,
		accentContainer = resolvedExt.tealContainer,
		onAccent = resolvedExt.onCyan,
		textPrimary = resolvedExt.textPrimary,
		textMuted = resolvedExt.textMuted,
		border = resolvedExt.border,
		isVisorHud = scheme == ColorScheme.YORHA_VISOR_V2,
	)
}
