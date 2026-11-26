package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep

@Keep
enum class TrackerCheckInterval(
	val key: String,
	val hours: Int,
	val label: String,
	val subtitle: String,
) {
	MANUAL_ONLY(
		key = "manual",
		hours = 0,
		label = "Manual only",
		subtitle = "Background periodic update checks disabled",
	),
	EVERY_2_HOURS(
		key = "2h",
		hours = 2,
		label = "Every 2 hours",
		subtitle = "Checks tracked library titles every 2 hours",
	),
	EVERY_6_HOURS(
		key = "6h",
		hours = 6,
		label = "Every 6 hours",
		subtitle = "Checks tracked library titles every 6 hours",
	),
	EVERY_12_HOURS(
		key = "12h",
		hours = 12,
		label = "Every 12 hours",
		subtitle = "Checks tracked library titles twice daily (Default)",
	),
	DAILY(
		key = "24h",
		hours = 24,
		label = "Daily (Every 24 hours)",
		subtitle = "Checks tracked library titles once every 24 hours",
	),
	EVERY_2_DAYS(
		key = "48h",
		hours = 48,
		label = "Every 2 days",
		subtitle = "Checks tracked library titles every 48 hours",
	),
	;

	companion object {
		fun fromKeyOrNull(key: String?): TrackerCheckInterval? =
			entries.firstOrNull { it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }

		fun fromLegacyFactor(enabled: Boolean, factor: Float): TrackerCheckInterval = when {
			!enabled || factor <= 0f -> MANUAL_ONLY
			factor >= 3.5f -> EVERY_2_HOURS
			factor >= 1.8f -> EVERY_6_HOURS
			factor >= 0.9f -> EVERY_12_HOURS
			else -> DAILY
		}
	}
}
