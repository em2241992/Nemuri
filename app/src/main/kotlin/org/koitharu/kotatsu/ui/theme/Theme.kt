package org.koitharu.kotatsu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.R

val PlusJakartaDisplayFamily = FontFamily(
	Font(R.font.plus_jakarta_sans, FontWeight.Normal),
	Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
	Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val InterBodyFamily = FontFamily(
	Font(R.font.inter, FontWeight.Normal),
	Font(R.font.inter, FontWeight.Medium),
	Font(R.font.inter, FontWeight.SemiBold),
)

val JetBrainsMonoFamily = FontFamily(
	Font(R.font.jetbrains_mono, FontWeight.Normal),
	Font(R.font.jetbrains_mono, FontWeight.Medium),
)

val CrimsonInk = Color(0xFFFF3366)
val AiIndigo = Color(0xFF6366F1)
val EmeraldHealthy = Color(0xFF10B981)
val AmberDuplicate = Color(0xFFF59E0B)
val RoseBroken = Color(0xFFF43F5E)

private val ObsidianDarkScheme = darkColorScheme(
	primary = CrimsonInk,
	onPrimary = Color.White,
	primaryContainer = Color(0xFF3B1022),
	onPrimaryContainer = Color(0xFFFFD9E1),
	secondary = AiIndigo,
	onSecondary = Color.White,
	secondaryContainer = Color(0xFF1E1F47),
	onSecondaryContainer = Color(0xFFE0E0FF),
	tertiary = EmeraldHealthy,
	background = Color(0xFF0B0C10),
	onBackground = Color(0xFFF1F5F9),
	surface = Color(0xFF13151C),
	onSurface = Color(0xFFF1F5F9),
	surfaceVariant = Color(0xFF1D202B),
	onSurfaceVariant = Color(0xFF94A3B8),
	outline = Color(0xFF2E3346),
)

private val PureOledDarkScheme = ObsidianDarkScheme.copy(
	background = Color(0xFF000000),
	surface = Color(0xFF07080C),
	surfaceVariant = Color(0xFF12141C),
)

private val WarmPaperLightScheme = lightColorScheme(
	primary = Color(0xFFD81B54),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFFFD9E1),
	onPrimaryContainer = Color(0xFF3E0016),
	secondary = Color(0xFF4F46E5),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFE0E0FF),
	onSecondaryContainer = Color(0xFF130868),
	tertiary = Color(0xFF059669),
	background = Color(0xFFFAF8F5),
	onBackground = Color(0xFF18181B),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF18181B),
	surfaceVariant = Color(0xFFF1ECE4),
	onSurfaceVariant = Color(0xFF52525B),
	outline = Color(0xFFD6D1C7),
)

val KotatsuTypography = Typography(
	headlineLarge = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.Bold,
		fontSize = 28.sp,
		lineHeight = 34.sp,
		letterSpacing = (-0.5).sp,
	),
	headlineMedium = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.Bold,
		fontSize = 22.sp,
		lineHeight = 28.sp,
		letterSpacing = (-0.25).sp,
	),
	titleLarge = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.Bold,
		fontSize = 18.sp,
		lineHeight = 24.sp,
	),
	titleMedium = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.SemiBold,
		fontSize = 15.sp,
		lineHeight = 20.sp,
	),
	titleSmall = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.SemiBold,
		fontSize = 13.sp,
		lineHeight = 18.sp,
	),
	bodyLarge = TextStyle(
		fontFamily = InterBodyFamily,
		fontWeight = FontWeight.Normal,
		fontSize = 15.sp,
		lineHeight = 22.sp,
	),
	bodyMedium = TextStyle(
		fontFamily = InterBodyFamily,
		fontWeight = FontWeight.Normal,
		fontSize = 13.sp,
		lineHeight = 19.sp,
	),
	bodySmall = TextStyle(
		fontFamily = InterBodyFamily,
		fontWeight = FontWeight.Normal,
		fontSize = 12.sp,
		lineHeight = 16.sp,
	),
	labelLarge = TextStyle(
		fontFamily = PlusJakartaDisplayFamily,
		fontWeight = FontWeight.SemiBold,
		fontSize = 13.sp,
		lineHeight = 18.sp,
	),
	labelSmall = TextStyle(
		fontFamily = JetBrainsMonoFamily,
		fontWeight = FontWeight.Medium,
		fontSize = 11.sp,
		lineHeight = 14.sp,
	),
)

@Composable
fun KotatsuNeoTheme(
	darkTheme: Boolean = true,
	themeMode: org.koitharu.kotatsu.core.model.AppThemeMode = org.koitharu.kotatsu.core.model.AppThemeMode.DARK,
	colorSchemePreset: org.koitharu.kotatsu.core.model.AppColorSchemePreset = org.koitharu.kotatsu.core.model.AppColorSchemePreset.KOTATSU_TEAL,
	pureOledBlack: Boolean = false,
	useDynamicColor: Boolean = false,
	content: @Composable () -> Unit,
) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
	val useDark = when (themeMode) {
		org.koitharu.kotatsu.core.model.AppThemeMode.SYSTEM -> systemDark
		org.koitharu.kotatsu.core.model.AppThemeMode.LIGHT -> false
		org.koitharu.kotatsu.core.model.AppThemeMode.DARK -> darkTheme
	}

	val colorScheme = if (useDynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
		val dynamicBase = if (useDark) {
			androidx.compose.material3.dynamicDarkColorScheme(context)
		} else {
			androidx.compose.material3.dynamicLightColorScheme(context)
		}
		if (useDark && pureOledBlack) {
			dynamicBase.copy(
				background = Color(0xFF000000),
				surface = Color(0xFF07080C),
				surfaceVariant = Color(0xFF12141C),
			)
		} else {
			dynamicBase
		}
	} else {
		val primaryColor = Color(
			if (useDark) colorSchemePreset.darkPrimaryHex else colorSchemePreset.lightPrimaryHex,
		)
		val primaryContainerColor = Color(
			if (useDark) colorSchemePreset.darkContainerHex else colorSchemePreset.lightContainerHex,
		)
		val secondaryColor = Color(colorSchemePreset.secondarySwatchHex)
		val onPrimaryColor = if (useDark && colorSchemePreset != org.koitharu.kotatsu.core.model.AppColorSchemePreset.CRIMSON_INK) {
			Color(0xFF041F22)
		} else {
			Color.White
		}

		val baseScheme = when {
			!useDark -> WarmPaperLightScheme
			pureOledBlack -> PureOledDarkScheme
			else -> ObsidianDarkScheme
		}

		baseScheme.copy(
			primary = primaryColor,
			onPrimary = onPrimaryColor,
			primaryContainer = primaryContainerColor,
			onPrimaryContainer = if (useDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
			secondary = secondaryColor,
		)
	}

	MaterialTheme(
		colorScheme = colorScheme,
		typography = KotatsuTypography,
		content = content,
	)
}
