package org.koitharu.kotatsu.core.ui.image

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CoverImageLoader

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PageImageLoader

@Module
@InstallIn(SingletonComponent::class)
object NemuriImageLoaderModule {

	private const val COVER_DISK_CACHE_BYTES = 500L * 1024L * 1024L // 500 MB in filesDir
	private const val PAGE_DISK_CACHE_BYTES = 1536L * 1024L * 1024L // 1.5 GB in cacheDir

	/**
	 * 1. Cover Thumbnail Cache (@CoverImageLoader):
	 * - Directory: context.filesDir.resolve("covers_persistent_cache")
	 * - Max Size: 500 MB
	 * - Memory Cache: 20% of available app heap (0.20)
	 * - Decoder Settings: allowHardware(true) and bitmapConfig(Bitmap.Config.HARDWARE)
	 */
	@Provides
	@Singleton
	@CoverImageLoader
	fun provideCoverImageLoader(
		@ApplicationContext context: Context,
		loaderContext: MangaLoaderContext = MangaLoaderContext(),
	): ImageLoader {
		return ImageLoader.Builder(context)
			.memoryCache {
				MemoryCache.Builder()
					.maxSizePercent(context, 0.20)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(context.filesDir.resolve("covers_persistent_cache"))
					.maxSizeBytes(COVER_DISK_CACHE_BYTES)
					.build()
			}
			.allowHardware(true)
			.bitmapConfig(Bitmap.Config.HARDWARE)
			.crossfade(false)
			.httpClient(loaderContext.httpClient)
			.build()
	}

	/**
	 * 2. Reader Page Cache (@PageImageLoader):
	 * - Directory: context.cacheDir.resolve("reader_pages_lru")
	 * - Max Size: 1.5 GB (1536 MB)
	 * - Memory Cache: 25% of available app heap (0.25)
	 * - Network Interceptor: Shares MangaLoaderContext.httpClient for Cloudflare clearance cookies & headers
	 */
	@Provides
	@Singleton
	@PageImageLoader
	fun providePageImageLoader(
		@ApplicationContext context: Context,
		loaderContext: MangaLoaderContext = MangaLoaderContext(),
	): ImageLoader {
		return ImageLoader.Builder(context)
			.memoryCache {
				MemoryCache.Builder()
					.maxSizePercent(context, 0.25)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(context.cacheDir.resolve("reader_pages_lru"))
					.maxSizeBytes(PAGE_DISK_CACHE_BYTES)
					.build()
			}
			.allowHardware(true)
			.bitmapConfig(Bitmap.Config.HARDWARE)
			.crossfade(false)
			.httpClient(loaderContext.httpClient)
			.build()
	}
}
