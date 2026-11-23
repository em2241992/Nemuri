package org.nemuri.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.nemuri.app.R

val PlusJakartaDisplayFamily = FontFamily(
	Font(R.font.plus_jakarta_sans, FontWeight.Normal),
	Font(R.font.plus_jakarta_sans, FontWeight.Medium),
	Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
	Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val InterBodyFamily = FontFamily(
	Font(R.font.inter, FontWeight.Normal),
	Font(R.font.inter, FontWeight.Medium),
	Font(R.font.inter, FontWeight.SemiBold),
	Font(R.font.inter, FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
	Font(R.font.jetbrains_mono, FontWeight.Normal),
	Font(R.font.jetbrains_mono, FontWeight.Medium),
	Font(R.font.jetbrains_mono, FontWeight.Bold),
)

val NemuriTypography = Typography(
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
	labelMedium = TextStyle(
		fontFamily = InterBodyFamily,
		fontWeight = FontWeight.Medium,
		fontSize = 12.sp,
		lineHeight = 16.sp,
	),
	labelSmall = TextStyle(
		fontFamily = JetBrainsMonoFamily,
		fontWeight = FontWeight.Medium,
		fontSize = 11.sp,
		lineHeight = 14.sp,
	),
)
