package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep

@Keep
enum class QuickScrollButtonsMode(
	val key: String,
	val label: String,
	val subtitle: String,
) {
	OFF(
		key = "off",
		label = "Off",
		subtitle = "Hide floating ▲ / ▼ quick-scroll capsule",
	),
	LONG_LISTS_ONLY(
		key = "long_lists",
		label = "Long Lists Only",
		subtitle = "Show ▲ / ▼ capsule automatically on lists with 24+ items",
	),
	ALWAYS(
		key = "always",
		label = "Always",
		subtitle = "Always show ▲ / ▼ quick-scroll capsule on scrollable lists",
	),
	;

	companion object {
		fun fromKey(key: String?): QuickScrollButtonsMode =
			entries.firstOrNull { it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
				?: LONG_LISTS_ONLY
	}
}
