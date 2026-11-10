package org.koitharu.kotatsu

import android.app.Application
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.parser.GlobalParserContextHolder
import org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator
import uy.kohesive.injekt.MihonInjektBridge

class KotatsuApp : Application() {
	override fun onCreate() {
		super.onCreate()
		GlobalParserContextHolder.applicationContext = this
		MihonInjektBridge.ensureInitialized(this)
		MangaDatabase.getInstance(this)
		KotatsuSyncAndDownloadCoordinator.ensureNotificationChannels(this)
	}
}
