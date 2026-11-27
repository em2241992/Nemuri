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
import androidx.compose.ui.graphics.lerp
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
	val tertiary: Color = Color(0xFF10B981),
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

// 2. TRUE_AMOLED — Pure OLED Black (forces #000000 on background AND cards)
val AmoledExtendedColors = ObsidianExtendedColors.copy(
	background = Color.Black,
	surface = Color(0xFF040507),
	surfaceElevated = Color(0xFF0D1017),
	card = Color.Black,
	dock = Color(0xFF090C12),
	sheet = Color(0xFF06080B),
	toast = Color(0xFF151923),
	border = Color(0xFF19202D),
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
	healthy = Color(0xFF74B07A),
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
	healthy = Color(0xFF3A7D44),
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
	healthy = Color(0xFF6EA899),
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
	healthy = Color(0xFF2F6F62),
)

// 8. NORD — Arctic Polar Night & Frost
private val NordDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF1B2028),
	surface = Color(0xFF242B35),
	surfaceElevated = Color(0xFF2E3440),
	card = Color(0xFF262D38),
	dock = Color(0xFF2B3340),
	sheet = Color(0xFF222832),
	toast = Color(0xFF3B4252),
	border = Color(0xFF3B4252),
	cyan = Color(0xFF88C0D0),
	teal = Color(0xFF81A1C1),
	tealContainer = Color(0xFF243B47),
	onCyan = Color(0xFF0E1A20),
	textPrimary = Color(0xFFECEFF4),
	textSecondary = Color(0xFFD8DEE9),
	textMuted = Color(0xFF9AA5B8),
	textDim = Color(0xFF6C788E),
	healthy = Color(0xFFA3BE8C),
	amber = Color(0xFFEBCB8B),
	danger = Color(0xFFBF616A),
	mergePurple = Color(0xFFB48EAD),
)

private val NordLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFECEFF4),
	surface = Color(0xFFF5F7FA),
	surfaceElevated = Color(0xFFE5E9F0),
	card = Color(0xFFF8FAFC),
	dock = Color(0xFFE5E9F0),
	sheet = Color(0xFFF5F7FA),
	toast = Color(0xFF2E3440),
	border = Color(0xFFD0D7E2),
	cyan = Color(0xFF5E81AC),
	teal = Color(0xFF4C6A92),
	tealContainer = Color(0xFFD8E4F2),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF2E3440),
	textSecondary = Color(0xFF3B4252),
	textMuted = Color(0xFF4C566A),
	textDim = Color(0xFF7B88A1),
	healthy = Color(0xFF4F7A3E),
	amber = Color(0xFFB47B22),
	danger = Color(0xFFA53F49),
	mergePurple = Color(0xFF885A89),
)

// 9. CATPPUCCIN — Mocha (Dark) & Latte (Light)
private val CatppuccinDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF181825),
	surface = Color(0xFF1E1E2E),
	surfaceElevated = Color(0xFF313244),
	card = Color(0xFF242536),
	dock = Color(0xFF2A2B3D),
	sheet = Color(0xFF1E1E2E),
	toast = Color(0xFF36384C),
	border = Color(0xFF45475A),
	cyan = Color(0xFFCBA6F7),
	teal = Color(0xFFB4BEFE),
	tealContainer = Color(0xFF36274D),
	onCyan = Color(0xFF1E112A),
	textPrimary = Color(0xFFCDD6F4),
	textSecondary = Color(0xFFBAC2DE),
	textMuted = Color(0xFFA6ADC8),
	textDim = Color(0xFF6C7086),
	healthy = Color(0xFFA6E3A1),
	amber = Color(0xFFF9E2AF),
	danger = Color(0xFFF38BA8),
	mergePurple = Color(0xFFF5C2E7),
)

private val CatppuccinLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFEFF1F5),
	surface = Color(0xFFF8F9FC),
	surfaceElevated = Color(0xFFE6E9EF),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFE6E9EF),
	sheet = Color(0xFFF8F9FC),
	toast = Color(0xFF4C4F69),
	border = Color(0xFFCCD0DA),
	cyan = Color(0xFF8839EF),
	teal = Color(0xFF7287FD),
	tealContainer = Color(0xFFEAD9FF),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF4C4F69),
	textSecondary = Color(0xFF5C5F77),
	textMuted = Color(0xFF6C6F85),
	textDim = Color(0xFF8C8FA1),
	healthy = Color(0xFF40A02B),
	amber = Color(0xFFDF8E1D),
	danger = Color(0xFFD20F39),
	mergePurple = Color(0xFFEA76CB),
)

// 10. CRIMSON_INK — Velvet Obsidian & Scarlet Lacquer
private val CrimsonInkDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF0B090D),
	surface = Color(0xFF151018),
	surfaceElevated = Color(0xFF211926),
	card = Color(0xFF19131D),
	dock = Color(0xFF1E1723),
	sheet = Color(0xFF16111A),
	toast = Color(0xFF2B2030),
	border = Color(0xFF35263C),
	cyan = Color(0xFFFF3366),
	teal = Color(0xFFE11D48),
	tealContainer = Color(0xFF471222),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFFF8F0F4),
	textSecondary = Color(0xFFDDCAD4),
	textMuted = Color(0xFFA68F9C),
	textDim = Color(0xFF735E6B),
	healthy = Color(0xFF34D399),
	amber = Color(0xFFFBBF24),
	danger = Color(0xFFFF4D6D),
	mergePurple = Color(0xFFC084FC),
)

private val CrimsonInkLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFFDF7F8),
	surface = Color(0xFFFFFFFF),
	surfaceElevated = Color(0xFFF6E8EC),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFF8ECF0),
	sheet = Color(0xFFFFFFFF),
	toast = Color(0xFF26151C),
	border = Color(0xFFE5CDD4),
	cyan = Color(0xFFBE123C),
	teal = Color(0xFF9F1239),
	tealContainer = Color(0xFFFFE4E8),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF241118),
	textSecondary = Color(0xFF4A2833),
	textMuted = Color(0xFF6E4754),
	textDim = Color(0xFF946B78),
	healthy = Color(0xFF059669),
	amber = Color(0xFFD97706),
	danger = Color(0xFFE11D48),
	mergePurple = Color(0xFF9333EA),
)

// 11. MIDNIGHT_INDIGO — Astral Navy & Starlight Indigo
private val MidnightIndigoDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF0A0C18),
	surface = Color(0xFF121528),
	surfaceElevated = Color(0xFF1B203B),
	card = Color(0xFF151930),
	dock = Color(0xFF191E38),
	sheet = Color(0xFF13172B),
	toast = Color(0xFF23294A),
	border = Color(0xFF293159),
	cyan = Color(0xFF818CF8),
	teal = Color(0xFF6366F1),
	tealContainer = Color(0xFF1E2254),
	onCyan = Color(0xFF090B24),
	textPrimary = Color(0xFFEEF2FF),
	textSecondary = Color(0xFFC7D2FE),
	textMuted = Color(0xFF8E99C9),
	textDim = Color(0xFF5D6796),
	healthy = Color(0xFF2DD4BF),
	amber = Color(0xFFFBBF24),
	danger = Color(0xFFF43F5E),
	mergePurple = Color(0xFFA78BFA),
)

private val MidnightIndigoLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFF4F5FC),
	surface = Color(0xFFFFFFFF),
	surfaceElevated = Color(0xFFE6E8F7),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFECEEF9),
	sheet = Color(0xFFFFFFFF),
	toast = Color(0xFF161A36),
	border = Color(0xFFCFD4EC),
	cyan = Color(0xFF4F46E5),
	teal = Color(0xFF4338CA),
	tealContainer = Color(0xFFE0E7FF),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF14172E),
	textSecondary = Color(0xFF313659),
	textMuted = Color(0xFF535980),
	textDim = Color(0xFF777EA6),
	healthy = Color(0xFF0D9488),
	amber = Color(0xFFD97706),
	danger = Color(0xFFE11D48),
	mergePurple = Color(0xFF7C3AED),
)

// 12. MONOCHROME_E_INK — High-Contrast Paper & Pure Ink
private val MonochromeEInkDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF101010),
	surface = Color(0xFF1A1A1A),
	surfaceElevated = Color(0xFF242424),
	card = Color(0xFF1E1E1E),
	dock = Color(0xFF222222),
	sheet = Color(0xFF181818),
	toast = Color(0xFF2C2C2C),
	border = Color(0xFF3A3A3A),
	cyan = Color(0xFFE4E4E7),
	teal = Color(0xFFD4D4D8),
	tealContainer = Color(0xFF2E2E32),
	onCyan = Color(0xFF111113),
	textPrimary = Color(0xFFFAFAFA),
	textSecondary = Color(0xFFD4D4D8),
	textMuted = Color(0xFFA1A1AA),
	textDim = Color(0xFF71717A),
	healthy = Color(0xFFA1A1AA),
	amber = Color(0xFFD4D4D8),
	danger = Color(0xFFF87171),
	mergePurple = Color(0xFFD4D4D8),
)

private val MonochromeEInkLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFFAFAFA),
	surface = Color(0xFFFFFFFF),
	surfaceElevated = Color(0xFFEDEDED),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFF2F2F2),
	sheet = Color(0xFFFFFFFF),
	toast = Color(0xFF18181B),
	border = Color(0xFFD4D4D8),
	cyan = Color(0xFF18181B),
	teal = Color(0xFF27272A),
	tealContainer = Color(0xFFE4E4E7),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF09090B),
	textSecondary = Color(0xFF27272A),
	textMuted = Color(0xFF52525B),
	textDim = Color(0xFF71717A),
	healthy = Color(0xFF52525B),
	amber = Color(0xFF52525B),
	danger = Color(0xFFDC2626),
	mergePurple = Color(0xFF3F3F46),
)

// 13. PERIWINKLE_ALLEGEDLY — #6395EE Cornflower Blue (or Periwinkle, if she says so)
private val PeriwinkleAllegedlyDarkExtended = NemuriExtendedColors(
	isDark = true,
	background = Color(0xFF0A0D16),
	surface = Color(0xFF0F1422),
	surfaceElevated = Color(0xFF1B243A),
	card = Color(0xFF151C2E),
	dock = Color(0xFF172034),
	sheet = Color(0xFF111828),
	toast = Color(0xFF202B44),
	border = Color(0xFF283757),
	cyan = Color(0xFF6395EE),
	teal = Color(0xFF8AB4F8),
	tealContainer = Color(0xFF1A2D4F),
	onCyan = Color(0xFF061024),
	textPrimary = Color(0xFFEFF4FF),
	textSecondary = Color(0xFFC7D7F5),
	textMuted = Color(0xFF8FA5CE),
	textDim = Color(0xFF63769C),
	healthy = Color(0xFF5EEAD4),
	amber = Color(0xFFFBBF24),
	danger = Color(0xFFFB7185),
	mergePurple = Color(0xFFA5B4FC),
)

private val PeriwinkleAllegedlyLightExtended = NemuriExtendedColors(
	isDark = false,
	background = Color(0xFFF5F8FF),
	surface = Color(0xFFEDF2FE),
	surfaceElevated = Color(0xFFE2EBFC),
	card = Color(0xFFFFFFFF),
	dock = Color(0xFFE8F0FE),
	sheet = Color(0xFFF9FBFF),
	toast = Color(0xFF151C2E),
	border = Color(0xFFCBD9F5),
	cyan = Color(0xFF3B6FD8),
	teal = Color(0xFF6395EE),
	tealContainer = Color(0xFFDCE8FD),
	onCyan = Color(0xFFFFFFFF),
	textPrimary = Color(0xFF101A33),
	textSecondary = Color(0xFF32436B),
	textMuted = Color(0xFF5C6F99),
	textDim = Color(0xFF8395BA),
	healthy = Color(0xFF0D9488),
	amber = Color(0xFFD97706),
	danger = Color(0xFFE11D48),
	mergePurple = Color(0xFF6366F1),
)

val LocalNemuriColors = staticCompositionLocalOf { ObsidianExtendedColors }

private data class AccentSpec(
	val darkPrimary: Color,
	val lightPrimary: Color,
	val darkContainer: Color,
	val lightContainer: Color,
	val onDarkPrimary: Color = Color(0xFF041F22),
	val darkTertiary: Color = Color(0xFF10B981),
	val lightTertiary: Color = Color(0xFF059669),
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
		onDarkPrimary = Color(0xFF071318),
		darkTertiary = Color(0xFF10B981),
		lightTertiary = Color(0xFF059669),
	)

	ColorScheme.BEDTIME_AMBER -> AccentSpec(
		darkPrimary = BedtimeAmberDarkExtended.cyan,
		lightPrimary = BedtimeAmberLightExtended.cyan,
		darkContainer = BedtimeAmberDarkExtended.tealContainer,
		lightContainer = BedtimeAmberLightExtended.tealContainer,
		onDarkPrimary = BedtimeAmberDarkExtended.onCyan,
		darkTertiary = BedtimeAmberDarkExtended.healthy,
		lightTertiary = BedtimeAmberLightExtended.healthy,
	)

	ColorScheme.YORHA -> AccentSpec(
		darkPrimary = YorhaDarkExtended.cyan,
		lightPrimary = YorhaLightExtended.cyan,
		darkContainer = YorhaDarkExtended.tealContainer,
		lightContainer = YorhaLightExtended.tealContainer,
		onDarkPrimary = YorhaDarkExtended.onCyan,
		darkTertiary = YorhaDarkExtended.healthy,
		lightTertiary = YorhaLightExtended.healthy,
	)

	ColorScheme.YORHA_VISOR_V2 -> AccentSpec(
		darkPrimary = YorhaVisorV2DarkExtended.cyan,
		lightPrimary = YorhaVisorV2LightExtended.cyan,
		darkContainer = YorhaVisorV2DarkExtended.tealContainer,
		lightContainer = YorhaVisorV2LightExtended.tealContainer,
		onDarkPrimary = YorhaVisorV2DarkExtended.onCyan,
		darkTertiary = YorhaVisorV2DarkExtended.healthy,
		lightTertiary = YorhaVisorV2LightExtended.healthy,
	)

	ColorScheme.KYOTO_WASHI -> AccentSpec(
		darkPrimary = KyotoWashiDarkExtended.cyan,
		lightPrimary = KyotoWashiLightExtended.cyan,
		darkContainer = KyotoWashiDarkExtended.tealContainer,
		lightContainer = KyotoWashiLightExtended.tealContainer,
		onDarkPrimary = KyotoWashiDarkExtended.onCyan,
		darkTertiary = KyotoWashiDarkExtended.healthy,
		lightTertiary = KyotoWashiLightExtended.healthy,
	)

	ColorScheme.SUIBOKUGA -> AccentSpec(
		darkPrimary = SuibokugaDarkExtended.cyan,
		lightPrimary = SuibokugaLightExtended.cyan,
		darkContainer = SuibokugaDarkExtended.tealContainer,
		lightContainer = SuibokugaLightExtended.tealContainer,
		onDarkPrimary = SuibokugaDarkExtended.onCyan,
		darkTertiary = SuibokugaDarkExtended.healthy,
		lightTertiary = SuibokugaLightExtended.healthy,
	)

	ColorScheme.NORD -> AccentSpec(
		darkPrimary = NordDarkExtended.cyan,
		lightPrimary = NordLightExtended.cyan,
		darkContainer = NordDarkExtended.tealContainer,
		lightContainer = NordLightExtended.tealContainer,
		onDarkPrimary = NordDarkExtended.onCyan,
		darkTertiary = NordDarkExtended.healthy,
		lightTertiary = NordLightExtended.healthy,
	)

	ColorScheme.CATPPUCCIN -> AccentSpec(
		darkPrimary = CatppuccinDarkExtended.cyan,
		lightPrimary = CatppuccinLightExtended.cyan,
		darkContainer = CatppuccinDarkExtended.tealContainer,
		lightContainer = CatppuccinLightExtended.tealContainer,
		onDarkPrimary = CatppuccinDarkExtended.onCyan,
		darkTertiary = CatppuccinDarkExtended.healthy,
		lightTertiary = CatppuccinLightExtended.healthy,
	)

	ColorScheme.CRIMSON_INK -> AccentSpec(
		darkPrimary = CrimsonInkDarkExtended.cyan,
		lightPrimary = CrimsonInkLightExtended.cyan,
		darkContainer = CrimsonInkDarkExtended.tealContainer,
		lightContainer = CrimsonInkLightExtended.tealContainer,
		onDarkPrimary = CrimsonInkDarkExtended.onCyan,
		darkTertiary = CrimsonInkDarkExtended.healthy,
		lightTertiary = CrimsonInkLightExtended.healthy,
	)

	ColorScheme.MIDNIGHT_INDIGO -> AccentSpec(
		darkPrimary = MidnightIndigoDarkExtended.cyan,
		lightPrimary = MidnightIndigoLightExtended.cyan,
		darkContainer = MidnightIndigoDarkExtended.tealContainer,
		lightContainer = MidnightIndigoLightExtended.tealContainer,
		onDarkPrimary = MidnightIndigoDarkExtended.onCyan,
		darkTertiary = MidnightIndigoDarkExtended.healthy,
		lightTertiary = MidnightIndigoLightExtended.healthy,
	)

	ColorScheme.MONOCHROME_E_INK -> AccentSpec(
		darkPrimary = MonochromeEInkDarkExtended.cyan,
		lightPrimary = MonochromeEInkLightExtended.cyan,
		darkContainer = MonochromeEInkDarkExtended.tealContainer,
		lightContainer = MonochromeEInkLightExtended.tealContainer,
		onDarkPrimary = MonochromeEInkDarkExtended.onCyan,
		darkTertiary = MonochromeEInkDarkExtended.healthy,
		lightTertiary = MonochromeEInkLightExtended.healthy,
	)

	ColorScheme.PERIWINKLE_ALLEGEDLY -> AccentSpec(
		darkPrimary = PeriwinkleAllegedlyDarkExtended.cyan,
		lightPrimary = PeriwinkleAllegedlyLightExtended.cyan,
		darkContainer = PeriwinkleAllegedlyDarkExtended.tealContainer,
		lightContainer = PeriwinkleAllegedlyLightExtended.tealContainer,
		onDarkPrimary = PeriwinkleAllegedlyDarkExtended.onCyan,
		darkTertiary = PeriwinkleAllegedlyDarkExtended.mergePurple,
		lightTertiary = PeriwinkleAllegedlyLightExtended.mergePurple,
	)

	// 16 Classic Accents — each with unique primary, container, and tertiary swatch tokens
	ColorScheme.TOTORO -> AccentSpec(
		darkPrimary = Color(0xFF2DD4BF),
		lightPrimary = Color(0xFF0D9488),
		darkContainer = Color(0xFF0F3834),
		lightContainer = Color(0xFFCCFBF1),
		onDarkPrimary = Color(0xFF04221E),
		darkTertiary = Color(0xFF86EFAC),
		lightTertiary = Color(0xFF15803D),
	)

	ColorScheme.MIKU -> AccentSpec(
		darkPrimary = Color(0xFF39C5BB),
		lightPrimary = Color(0xFF0E7490),
		darkContainer = Color(0xFF0D3739),
		lightContainer = Color(0xFFCFFAFE),
		onDarkPrimary = Color(0xFF042022),
		darkTertiary = Color(0xFFF472B6),
		lightTertiary = Color(0xFFDB2777),
	)

	ColorScheme.RENA -> AccentSpec(
		darkPrimary = Color(0xFFFF4D4F),
		lightPrimary = Color(0xFFDC2626),
		darkContainer = Color(0xFF4A1215),
		lightContainer = Color(0xFFFEE2E2),
		onDarkPrimary = Color.White,
		darkTertiary = Color(0xFFFBBF24),
		lightTertiary = Color(0xFFD97706),
	)

	ColorScheme.MAKI -> AccentSpec(
		darkPrimary = Color(0xFFF43F5E),
		lightPrimary = Color(0xFFE11D48),
		darkContainer = Color(0xFF4C1120),
		lightContainer = Color(0xFFFFE4E6),
		onDarkPrimary = Color.White,
		darkTertiary = Color(0xFFC084FC),
		lightTertiary = Color(0xFF7E22CE),
	)

	ColorScheme.MAMIMI -> AccentSpec(
		darkPrimary = Color(0xFFFB7185),
		lightPrimary = Color(0xFFF43F5E),
		darkContainer = Color(0xFF4B1924),
		lightContainer = Color(0xFFFFE4E6),
		onDarkPrimary = Color(0xFF2B070F),
		darkTertiary = Color(0xFFFDBA74),
		lightTertiary = Color(0xFFEA580C),
	)

	ColorScheme.KANO -> AccentSpec(
		darkPrimary = Color(0xFFFB923C),
		lightPrimary = Color(0xFFEA580C),
		darkContainer = Color(0xFF48230A),
		lightContainer = Color(0xFFFFEDD5),
		onDarkPrimary = Color(0xFF261002),
		darkTertiary = Color(0xFFFDE047),
		lightTertiary = Color(0xFFCA8A04),
	)

	ColorScheme.BLUEBERRY -> AccentSpec(
		darkPrimary = Color(0xFF60A5FA),
		lightPrimary = Color(0xFF2563EB),
		darkContainer = Color(0xFF162C52),
		lightContainer = Color(0xFFDBEAFE),
		onDarkPrimary = Color(0xFF07162E),
		darkTertiary = Color(0xFFA78BFA),
		lightTertiary = Color(0xFF6D28D9),
	)

	ColorScheme.SAKURA -> AccentSpec(
		darkPrimary = Color(0xFFF472B6),
		lightPrimary = Color(0xFFDB2777),
		darkContainer = Color(0xFF4A1836),
		lightContainer = Color(0xFFFCE7F3),
		onDarkPrimary = Color(0xFF28081B),
		darkTertiary = Color(0xFFF9A8D4),
		lightTertiary = Color(0xFFBE185D),
	)

	ColorScheme.ITACHI -> AccentSpec(
		darkPrimary = Color(0xFFEF4444),
		lightPrimary = Color(0xFFB91C1C),
		darkContainer = Color(0xFF450E0E),
		lightContainer = Color(0xFFFEE2E2),
		onDarkPrimary = Color.White,
		darkTertiary = Color(0xFF94A3B8),
		lightTertiary = Color(0xFF475569),
	)

	ColorScheme.FROG -> AccentSpec(
		darkPrimary = Color(0xFF34D399),
		lightPrimary = Color(0xFF059669),
		darkContainer = Color(0xFF0C3B2E),
		lightContainer = Color(0xFFD1FAE5),
		onDarkPrimary = Color(0xFF042219),
		darkTertiary = Color(0xFF2DD4BF),
		lightTertiary = Color(0xFF0F766E),
	)

	ColorScheme.KANADE -> AccentSpec(
		darkPrimary = Color(0xFFA78BFA),
		lightPrimary = Color(0xFF7C3AED),
		darkContainer = Color(0xFF2B1E57),
		lightContainer = Color(0xFFEDE9FE),
		onDarkPrimary = Color(0xFF160D33),
		darkTertiary = Color(0xFF93C5FD),
		lightTertiary = Color(0xFF2563EB),
	)

	ColorScheme.ITSUKA -> AccentSpec(
		darkPrimary = Color(0xFFD946EF),
		lightPrimary = Color(0xFFA21CAF),
		darkContainer = Color(0xFF44134D),
		lightContainer = Color(0xFFFAE8FF),
		onDarkPrimary = Color.White,
		darkTertiary = Color(0xFFF472B6),
		lightTertiary = Color(0xFFDB2777),
	)

	ColorScheme.SHANA -> AccentSpec(
		darkPrimary = Color(0xFFFF6B4A),
		lightPrimary = Color(0xFFC2410C),
		darkContainer = Color(0xFF4A1B10),
		lightContainer = Color(0xFFFFEDD5),
		onDarkPrimary = Color(0xFF290903),
		darkTertiary = Color(0xFFFBBF24),
		lightTertiary = Color(0xFFB45309),
	)

	ColorScheme.LIME -> AccentSpec(
		darkPrimary = Color(0xFF84CC16),
		lightPrimary = Color(0xFF4D7C0F),
		darkContainer = Color(0xFF243B0B),
		lightContainer = Color(0xFFECFCCB),
		onDarkPrimary = Color(0xFF122103),
		darkTertiary = Color(0xFFFACC15),
		lightTertiary = Color(0xFFA16207),
	)

	ColorScheme.AMBER -> AccentSpec(
		darkPrimary = Color(0xFFFBBF24),
		lightPrimary = Color(0xFFD97706),
		darkContainer = Color(0xFF452A06),
		lightContainer = Color(0xFFFEF3C7),
		onDarkPrimary = Color(0xFF241502),
		darkTertiary = Color(0xFFFB923C),
		lightTertiary = Color(0xFFC2410C),
	)

	ColorScheme.SKY -> AccentSpec(
		darkPrimary = Color(0xFF38BDF8),
		lightPrimary = Color(0xFF0284C7),
		darkContainer = Color(0xFF0C344D),
		lightContainer = Color(0xFFE0F2FE),
		onDarkPrimary = Color(0xFF041C2B),
		darkTertiary = Color(0xFF67E8F9),
		lightTertiary = Color(0xFF0891B2),
	)
}

/**
 * Smart AMOLED surface transformation for non-TRUE_AMOLED dark palettes:
 * Sets root [NemuriExtendedColors.background] to `#000000` while retaining subtle
 * persona/accent-tinted surface, card, elevated, dock, sheet, and border tokens.
 */
private fun NemuriExtendedColors.toSmartAmoled(): NemuriExtendedColors = copy(
	background = Color.Black,
	surface = lerp(Color.Black, surface, 0.54f),
	surfaceElevated = lerp(Color.Black, surfaceElevated, 0.64f),
	card = lerp(Color.Black, card, 0.58f),
	dock = lerp(Color.Black, dock, 0.62f),
	sheet = lerp(Color.Black, sheet, 0.56f),
	border = lerp(Color.Black, border, 0.76f),
)

private fun resolveStaticExtendedColors(
	colorScheme: ColorScheme,
	isDark: Boolean,
	amoled: Boolean,
): NemuriExtendedColors = when (colorScheme) {
	ColorScheme.TRUE_AMOLED -> if (isDark) {
		AmoledExtendedColors
	} else {
		TrueAmoledLightExtended
	}

	ColorScheme.DEFAULT,
	ColorScheme.EXPRESSIVE,
	-> if (isDark) {
		if (amoled) ObsidianExtendedColors.toSmartAmoled() else ObsidianExtendedColors
	} else {
		LightExtendedColors
	}

	ColorScheme.BEDTIME_AMBER -> if (isDark) {
		if (amoled) BedtimeAmberDarkExtended.toSmartAmoled() else BedtimeAmberDarkExtended
	} else {
		BedtimeAmberLightExtended
	}

	ColorScheme.YORHA -> if (isDark) {
		if (amoled) YorhaDarkExtended.toSmartAmoled() else YorhaDarkExtended
	} else {
		YorhaLightExtended
	}

	ColorScheme.YORHA_VISOR_V2 -> if (isDark) {
		if (amoled) YorhaVisorV2DarkExtended.toSmartAmoled() else YorhaVisorV2DarkExtended
	} else {
		YorhaVisorV2LightExtended
	}

	ColorScheme.KYOTO_WASHI -> if (isDark) {
		if (amoled) KyotoWashiDarkExtended.toSmartAmoled() else KyotoWashiDarkExtended
	} else {
		KyotoWashiLightExtended
	}

	ColorScheme.SUIBOKUGA -> if (isDark) {
		if (amoled) SuibokugaDarkExtended.toSmartAmoled() else SuibokugaDarkExtended
	} else {
		SuibokugaLightExtended
	}

	ColorScheme.NORD -> if (isDark) {
		if (amoled) NordDarkExtended.toSmartAmoled() else NordDarkExtended
	} else {
		NordLightExtended
	}

	ColorScheme.CATPPUCCIN -> if (isDark) {
		if (amoled) CatppuccinDarkExtended.toSmartAmoled() else CatppuccinDarkExtended
	} else {
		CatppuccinLightExtended
	}

	ColorScheme.CRIMSON_INK -> if (isDark) {
		if (amoled) CrimsonInkDarkExtended.toSmartAmoled() else CrimsonInkDarkExtended
	} else {
		CrimsonInkLightExtended
	}

	ColorScheme.MIDNIGHT_INDIGO -> if (isDark) {
		if (amoled) MidnightIndigoDarkExtended.toSmartAmoled() else MidnightIndigoDarkExtended
	} else {
		MidnightIndigoLightExtended
	}

	ColorScheme.MONOCHROME_E_INK -> if (isDark) {
		if (amoled) MonochromeEInkDarkExtended.toSmartAmoled() else MonochromeEInkDarkExtended
	} else {
		MonochromeEInkLightExtended
	}

	ColorScheme.PERIWINKLE_ALLEGEDLY -> if (isDark) {
		if (amoled) PeriwinkleAllegedlyDarkExtended.toSmartAmoled() else PeriwinkleAllegedlyDarkExtended
	} else {
		PeriwinkleAllegedlyLightExtended
	}

	// Classic Accents — accent-tinted Obsidian dark (and Smart AMOLED) + clean paper light
	ColorScheme.TOTORO,
	ColorScheme.MIKU,
	ColorScheme.RENA,
	ColorScheme.MAKI,
	ColorScheme.MAMIMI,
	ColorScheme.KANO,
	ColorScheme.BLUEBERRY,
	ColorScheme.SAKURA,
	ColorScheme.ITACHI,
	ColorScheme.FROG,
	ColorScheme.KANADE,
	ColorScheme.ITSUKA,
	ColorScheme.SHANA,
	ColorScheme.LIME,
	ColorScheme.AMBER,
	ColorScheme.SKY,
	-> {
		val accent = colorScheme.accentSpec()
		if (isDark) {
			val tintedDark = ObsidianExtendedColors.copy(
				surface = lerp(ObsidianExtendedColors.surface, accent.darkContainer, 0.22f),
				surfaceElevated = lerp(ObsidianExtendedColors.surfaceElevated, accent.darkContainer, 0.28f),
				card = lerp(ObsidianExtendedColors.card, accent.darkContainer, 0.24f),
				dock = lerp(ObsidianExtendedColors.dock, accent.darkContainer, 0.26f),
				sheet = lerp(ObsidianExtendedColors.sheet, accent.darkContainer, 0.20f),
				border = lerp(ObsidianExtendedColors.border, accent.darkPrimary, 0.16f),
				cyan = accent.darkPrimary,
				teal = accent.darkPrimary,
				tealContainer = accent.darkContainer,
				onCyan = accent.onDarkPrimary,
				healthy = accent.darkTertiary,
			)
			if (amoled) tintedDark.toSmartAmoled() else tintedDark
		} else {
			LightExtendedColors.copy(
				surfaceElevated = lerp(LightExtendedColors.surfaceElevated, accent.lightContainer, 0.28f),
				dock = lerp(LightExtendedColors.dock, accent.lightContainer, 0.24f),
				border = lerp(LightExtendedColors.border, accent.lightPrimary, 0.14f),
				cyan = accent.lightPrimary,
				teal = accent.lightPrimary,
				tealContainer = accent.lightContainer,
				onCyan = Color.White,
				healthy = accent.lightTertiary,
			)
		}
	}
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
			if (amoled) ObsidianExtendedColors.toSmartAmoled() else ObsidianExtendedColors
		} else {
			LightExtendedColors
		}
		val adjustedMaterial = if (isDark && amoled) {
			base.copy(
				background = Color.Black,
				surface = lerp(Color.Black, base.surface, 0.56f),
				surfaceVariant = lerp(Color.Black, base.surfaceVariant, 0.64f),
				surfaceContainerLowest = Color.Black,
				surfaceContainerLow = lerp(Color.Black, base.surfaceContainerLow, 0.58f),
				surfaceContainer = lerp(Color.Black, base.surfaceContainer, 0.62f),
				surfaceContainerHigh = lerp(Color.Black, base.surfaceContainerHigh, 0.66f),
				surfaceContainerHighest = lerp(Color.Black, base.surfaceContainerHighest, 0.70f),
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
			healthy = adjustedMaterial.tertiary,
			textPrimary = adjustedMaterial.onSurface,
			textSecondary = adjustedMaterial.onSurfaceVariant,
			textMuted = adjustedMaterial.onSurfaceVariant.copy(alpha = 0.78f),
			textDim = adjustedMaterial.onSurfaceVariant.copy(alpha = 0.56f),
		)
		return adjustedMaterial to dynamicExt
	}

	val ext = resolveStaticExtendedColors(
		colorScheme = colorScheme,
		isDark = isDark,
		amoled = amoled,
	)

	val onContainerColor = ext.cyan
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
	ColorScheme.NORD -> "Nord Frost"
	ColorScheme.CATPPUCCIN -> "Catppuccin Mocha"
	ColorScheme.CRIMSON_INK -> "Crimson Ink"
	ColorScheme.MIDNIGHT_INDIGO -> "Midnight Indigo"
	ColorScheme.MONOCHROME_E_INK -> "Monochrome E-Ink"
	ColorScheme.PERIWINKLE_ALLEGEDLY -> "Veri-lys"
	ColorScheme.TOTORO -> "Default (Totoro)"
	ColorScheme.MIKU -> "Miku"
	ColorScheme.RENA -> "Asuka"
	ColorScheme.MAKI -> "Maki"
	ColorScheme.MAMIMI -> "Mamimi"
	ColorScheme.KANO -> "Kano"
	ColorScheme.BLUEBERRY -> "Rikka"
	ColorScheme.SAKURA -> "Sakura"
	ColorScheme.ITACHI -> "Itachi"
	ColorScheme.FROG -> "Mion"
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
	ColorScheme.NORD -> "Arctic polar night & frost blue"
	ColorScheme.CATPPUCCIN -> "Cozy pastel Mocha & Latte"
	ColorScheme.CRIMSON_INK -> "Velvet obsidian & scarlet lacquer"
	ColorScheme.MIDNIGHT_INDIGO -> "Astral navy & starlight indigo"
	ColorScheme.MONOCHROME_E_INK -> "High-contrast paper & pure ink"
	ColorScheme.PERIWINKLE_ALLEGEDLY -> "#6395EE — Cornflower blue, or periwinkle if she says so"
	ColorScheme.TOTORO -> "Forest spirit teal-emerald"
	ColorScheme.MIKU -> "Vocaloid turquoise cyan"
	ColorScheme.RENA -> "Plugsuit scarlet vermilion"
	ColorScheme.MAKI -> "Idol crimson-rose"
	ColorScheme.MAMIMI -> "Sunset coral blush"
	ColorScheme.KANO -> "Vibrant tangerine glow"
	ColorScheme.BLUEBERRY -> "Cobalt sapphire blue"
	ColorScheme.SAKURA -> "Spring cherry blossom pink"
	ColorScheme.ITACHI -> "Sharingan blood-crimson"
	ColorScheme.FROG -> "Fresh emerald mint"
	ColorScheme.KANADE -> "Soft angel lavender"
	ColorScheme.ITSUKA -> "Orchid magenta-violet"
	ColorScheme.SHANA -> "Flame Haze ember ruby"
	ColorScheme.LIME -> "Crisp citrus chartreuse"
	ColorScheme.AMBER -> "Golden honey glow"
	ColorScheme.SKY -> "Clear horizon azure"
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
			tertiary = ext.healthy,
			isVisorHud = false,
		)
	}
	val resolvedExt = resolveStaticExtendedColors(
		colorScheme = scheme,
		isDark = isDark,
		amoled = amoled,
	)
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
		tertiary = resolvedExt.healthy,
		isVisorHud = scheme == ColorScheme.YORHA_VISOR_V2,
	)
}
