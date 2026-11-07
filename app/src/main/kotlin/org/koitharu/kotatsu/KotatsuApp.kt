package org.koitharu.kotatsu

import android.app.Application
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator

class KotatsuApp : Application() {
	override fun onCreate() {
		super.onCreate()
		MangaDatabase.getInstance(this)
		KotatsuSyncAndDownloadCoordinator.ensureNotificationChannels(this)
	}
}
