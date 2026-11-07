package org.koitharu.kotatsu.core.parser

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridMangaRepositoryFactory @Inject constructor(
	private val loaderContext: MangaLoaderContext = MangaLoaderContext(),
	private val mihonExtensionBridge: MihonExtensionBridge = MihonExtensionBridge(),
	private val lnReaderJsBridge: LnReaderPluginBridge = LnReaderPluginBridge(),
) {
	private val activeRepositories = ConcurrentHashMap<String, MangaRepository>()
	private val classLoaderMutex = Mutex()

	// Bound external APK / JS initialization to 4 threads so native Kotatsu parsers never starve
	private val extensionInitDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

	/**
	 * Fast-path synchronous lookup for Tier-1 Native Kotatsu Parsers.
	 */
	fun getOrCreateFast(source: MangaSource): MangaRepository? {
		activeRepositories[source.name]?.let { return it }
		if (source.isNativeKotatsuParser()) {
			return NativeKotatsuRepository(source, loaderContext).also {
				activeRepositories[source.name] = it
			}
		}
		return null
	}

	/**
	 * Suspending lookup for Tier-2 External Extensions (Mihon APKs & LNReader JS).
	 * Shares loaderContext.httpClient so extensions reuse the core OkHttp connection pool & Cloudflare cookies.
	 */
	suspend fun getOrCreateAsync(source: MangaSource): MangaRepository {
		getOrCreateFast(source)?.let { return it }

		return withContext(extensionInitDispatcher) {
			classLoaderMutex.withLock {
				activeRepositories[source.name]?.let { return@withLock it }

				val repo = when {
					source.name.startsWith("MIHON_") -> {
						mihonExtensionBridge.loadHttpSource(
							sourceName = source.name,
							sharedHttpClient = loaderContext.httpClient,
							cookieJar = loaderContext.cookieJar,
						)
					}
					source.name.startsWith("LNREADER_") -> {
						lnReaderJsBridge.loadPlugin(
							pluginId = source.name,
							sharedHttpClient = loaderContext.httpClient,
						)
					}
					else -> NativeKotatsuRepository(source, loaderContext)
				}
				activeRepositories[source.name] = repo
				repo
			}
		}
	}

	fun evictSource(sourceName: String) {
		activeRepositories.remove(sourceName)
	}
}
