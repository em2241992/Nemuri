package org.nemuri.app

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.internal.platform.PlatformRegistry
import java.util.concurrent.atomic.AtomicBoolean
import org.koitharu.kotatsu.ai.domain.LocalLlmClient
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.logs.AppLogger
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.EngineRuntimeConfig
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.processLifecycleScope
import org.koitharu.kotatsu.lnreader.LnPluginManager
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.index.LocalMangaIndex
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.settings.sources.catalog.EXTENSION_APK_PREFIX
import org.koitharu.kotatsu.settings.work.WorkScheduleManager
import org.nemuri.app.crash.GlobalExceptionHandler
import org.nemuri.app.security.AppLockManager
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class NemuriApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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
	lateinit var workScheduleManager: Provider<WorkScheduleManager>

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	@Inject
	lateinit var coilImageLoader: Provider<ImageLoader>

	@Inject
	lateinit var mihonExtensionManager: Provider<MihonExtensionManager>

	@Inject
	lateinit var lnPluginManager: Provider<LnPluginManager>

	@Inject
	lateinit var pageUpscaleEngine: Provider<PageUpscaleEngine>

	@Inject
	lateinit var appLockManager: Provider<AppLockManager>

	@Inject
	lateinit var localLlmClient: Provider<LocalLlmClient>

	private val extensionsInitStarted = AtomicBoolean(false)

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun newImageLoader(context: PlatformContext): ImageLoader = coilImageLoader.get()

	override fun onCreate() {
		super.onCreate()
		if (isCrashProcess()) {
			return
		}
		GlobalExceptionHandler.install(this)
		EngineRuntimeConfig.init(
			applicationId = BuildConfig.APPLICATION_ID,
			versionCode = BuildConfig.VERSION_CODE,
			versionName = BuildConfig.VERSION_NAME,
		)
		AppRouter.navigator = NemuriEngineNavigator()
		PlatformRegistry.applicationContext = this
		appLogger.setEnabled(settings.isVerboseLoggingEnabled)
		runCatching { appLockManager.get() }
		activityLifecycleCallbacks.forEach(::registerActivityLifecycleCallbacks)
		cleanupDownloadedExtensionApks()
		workScheduleManager.get().init()
		processLifecycleScope.launch(Dispatchers.Default) {
			runCatching {
				pageUpscaleEngine.get().activeDelegateLabel.collect { label ->
					GlobalExceptionHandler.activeDelegateHint = label
				}
			}
		}
		// Fallback safety net if a background worker or ReaderActivity launches before MainActivity's first frame.
		processLifecycleScope.launch(Dispatchers.Default) {
			delay(2_500L)
			ensureExtensionsInitialized()
		}
		processLifecycleScope.launch(Dispatchers.Default) {
			val tracker = database.get().invalidationTracker
			databaseObserversProvider.get().forEach(tracker::addObserver)
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
	}

	fun ensureExtensionsInitialized() {
		if (!extensionsInitStarted.compareAndSet(false, true)) return
		processLifecycleScope.launch(Dispatchers.IO) {
			runCatching { mihonExtensionManager.get().initialize() }
		}
		processLifecycleScope.launch(Dispatchers.IO) {
			runCatching { lnPluginManager.get().initialize() }
		}
	}

	override fun onTrimMemory(level: Int) {
		super.onTrimMemory(level)
		if (!isCrashProcess() && ::localLlmClient.isInitialized) {
			runCatching { localLlmClient.get().onTrimMemory(level) }
		}
	}

	@Suppress("DEPRECATION")
	override fun onLowMemory() {
		super.onLowMemory()
		if (!isCrashProcess() && ::localLlmClient.isInitialized) {
			runCatching { localLlmClient.get().unloadAsync() }
		}
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

	private fun isCrashProcess(): Boolean = runCatching {
		val procName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			Application.getProcessName()
		} else {
			val pid = Process.myPid()
			val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
			am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
		}
		procName.endsWith(":crash")
	}.getOrDefault(false)
}
