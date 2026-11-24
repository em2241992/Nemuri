package org.koitharu.kotatsu.sync

data class SyncStatusSnapshot(
	val isAvailable: Boolean = false,
	val isSignedIn: Boolean = false,
	val accountName: String? = null,
	val accountEmail: String? = null,
	val accountPhotoUrl: String? = null,
	val isEmailHidden: Boolean = false,
	val intervalMinutes: Int = 360,
	val isWifiOnly: Boolean = false,
	val enabledContent: Set<String> = DEFAULT_SYNC_CONTENT,
	val lastSyncTimestamp: Long = 0L,
	val lastSyncError: String? = null,
) {
	companion object {
		val DEFAULT_SYNC_CONTENT: Set<String> = linkedSetOf(
			"favourites",
			"history",
			"bookmarks",
			"feed",
			"tracking",
			"stats",
			"settings",
			"custom_covers",
		)
	}
}
