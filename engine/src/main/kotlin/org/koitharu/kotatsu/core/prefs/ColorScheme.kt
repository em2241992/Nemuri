package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.google.android.material.color.DynamicColors
import org.koitharu.kotatsu.R

@Keep
enum class ColorScheme(
	@StringRes val titleResId: Int,
) {

	DEFAULT(R.string.theme_name_totoro),
	EXPRESSIVE(R.string.theme_name_expressive),
	YORHA(R.string.theme_name_yorha),
	KYOTO_WASHI(R.string.theme_name_kyoto_washi),
	SUIBOKUGA(R.string.theme_name_suibokuga),
	MIKU(R.string.theme_name_miku),
	RENA(R.string.theme_name_asuka),
	FROG(R.string.theme_name_mion),
	BLUEBERRY(R.string.theme_name_rikka),
	SAKURA(R.string.theme_name_sakura),
	MAMIMI(R.string.theme_name_mamimi),
	KANADE(R.string.theme_name_kanade),
	ITSUKA(R.string.theme_name_itsuka),
	SHANA(R.string.theme_name_shana),
	LIME(R.string.theme_name_lime),
	AMBER(R.string.theme_name_amber),
	SKY(R.string.theme_name_sky),
	;

	companion object {

		val default: ColorScheme
			get() = if (DynamicColors.isDynamicColorAvailable()) {
				EXPRESSIVE
			} else {
				DEFAULT
			}

		fun getAvailableList(): List<ColorScheme> {
			val list = ColorScheme.entries.toMutableList()
			if (!DynamicColors.isDynamicColorAvailable()) {
				list.remove(EXPRESSIVE)
			}
			return list
		}
	}
}
