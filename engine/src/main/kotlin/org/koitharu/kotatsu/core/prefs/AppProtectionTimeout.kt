package org.koitharu.kotatsu.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import org.koitharu.kotatsu.R

@Keep
enum class AppProtectionTimeout(
	@StringRes val titleResId: Int,
	val timeoutMillis: Long,
) {
	INSTANT(R.string.lock_timeout_instant, 0L),
	SECONDS_10(R.string.lock_timeout_10_seconds, 10_000L),
	MINUTE_1(R.string.lock_timeout_1_minute, 60_000L),
	MINUTES_5(R.string.lock_timeout_5_minutes, 300_000L),
	MINUTES_15(R.string.lock_timeout_15_minutes, 900_000L),
}

