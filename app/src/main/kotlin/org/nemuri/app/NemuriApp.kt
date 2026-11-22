package org.nemuri.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.internal.platform.PlatformRegistry
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.logs.AppLogger
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.EngineRuntimeConfig
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.processLifecycleScope
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.index.LocalMangaIndex
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.settings.sources.catalog.EXTENSION_APK_PREFIX
import org.koitharu.kotatsu.settings.work.WorkScheduleManager
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class NemuriApp : Application(), Configuration.Provider {

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var databaseObserversProvider: Provider<Set<@JvmSuppressWildcards InvalidationTracker.Observer>>

	@Inject
	lateinit var activityLifecycleCallbacks: Set<@JvmSuppressWildcards ActivityLifecycleCallbacks>

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var appLogger: AppLogger

	@Inject
	lateinit var workScheduleManager: WorkScheduleManager

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun onCreate() {
		super.onCreate()
		EngineRuntimeConfig.init(
			applicationId = BuildConfig.APPLICATION_ID,
			versionCode = BuildConfig.VERSION_CODE,
			versionName = BuildConfig.VERSION_NAME,
		)
		AppRouter.navigator = NemuriEngineNavigator()
		PlatformRegistry.applicationContext = this
		appLogger.setEnabled(settings.isVerboseLoggingEnabled)
		activityLifecycleCallbacks.forEach(::registerActivityLifecycleCallbacks)
		cleanupDownloadedExtensionApks()
		processLifecycleScope.launch(Dispatchers.Default) {
			val tracker = database.get().invalidationTracker
			databaseObserversProvider.get().forEach(tracker::addObserver)
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
		workScheduleManager.init()
	}

	private fun cleanupDownloadedExtensionApks() {
		runCatching {
			cacheDir.listFiles()?.forEach { file ->
				if (file.isFile && file.name.startsWith(EXTENSION_APK_PREFIX)) {
					file.delete()
				}
			}
		}
	}
}
