package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.google.android.material.color.DynamicColors
import org.koitharu.kotatsu.R

@Keep
enum class ColorScheme(
	@StringRes val titleResId: Int,
	val isSignaturePersona: Boolean = false,
) {

	// 1. Signature & Persona Themes
	EXPRESSIVE(R.string.theme_name_expressive, isSignaturePersona = true),
	DEFAULT(R.string.theme_name_nemuri_obsidian, isSignaturePersona = true),
	TRUE_AMOLED(R.string.theme_name_true_amoled, isSignaturePersona = true),
	BEDTIME_AMBER(R.string.theme_name_bedtime_amber, isSignaturePersona = true),
	YORHA(R.string.theme_name_yorha, isSignaturePersona = true),
	YORHA_VISOR_V2(R.string.theme_name_yorha_visor_v2, isSignaturePersona = true),
	KYOTO_WASHI(R.string.theme_name_kyoto_washi, isSignaturePersona = true),
	SUIBOKUGA(R.string.theme_name_suibokuga, isSignaturePersona = true),
	NORD(R.string.theme_name_nord, isSignaturePersona = true),
	CATPPUCCIN(R.string.theme_name_catppuccin, isSignaturePersona = true),
	CRIMSON_INK(R.string.theme_name_crimson_ink, isSignaturePersona = true),
	MIDNIGHT_INDIGO(R.string.theme_name_midnight_indigo, isSignaturePersona = true),
	MONOCHROME_E_INK(R.string.theme_name_monochrome_eink, isSignaturePersona = true),

	// 2. Classic Accent Palettes (Kotatsu / DropSauce)
	TOTORO(R.string.theme_name_totoro),
	MIKU(R.string.theme_name_miku),
	RENA(R.string.theme_name_asuka),
	MAKI(R.string.theme_name_maki),
	MAMIMI(R.string.theme_name_mamimi),
	KANO(R.string.theme_name_kano),
	BLUEBERRY(R.string.theme_name_rikka),
	SAKURA(R.string.theme_name_sakura),
	ITACHI(R.string.theme_name_itachi),
	FROG(R.string.theme_name_mion),
	KANADE(R.string.theme_name_kanade),
	ITSUKA(R.string.theme_name_itsuka),
	SHANA(R.string.theme_name_shana),
	LIME(R.string.theme_name_lime),
	AMBER(R.string.theme_name_amber),
	SKY(R.string.theme_name_sky),
	;

	companion object {

		val default: ColorScheme
			get() = DEFAULT

		val signaturePersonas: List<ColorScheme>
			get() = entries.filter { it.isSignaturePersona }

		val classicAccents: List<ColorScheme>
			get() = entries.filterNot { it.isSignaturePersona }

		val nemuriCuratedPalettes: List<ColorScheme>
			get() = signaturePersonas

		fun getAvailableList(): List<ColorScheme> {
			val list = entries.toMutableList()
			if (!DynamicColors.isDynamicColorAvailable()) {
				list.remove(EXPRESSIVE)
			}
			return list
		}
	}
}
