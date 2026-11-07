package coil3

import android.content.Context
import android.graphics.Bitmap
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import okhttp3.OkHttpClient

interface Disposable {
	val isDisposed: Boolean
	fun dispose()
}

class ImageLoader(
	val context: Context,
	val memoryCache: MemoryCache?,
	val diskCache: DiskCache?,
	val allowHardware: Boolean = true,
	val bitmapConfig: Bitmap.Config = Bitmap.Config.HARDWARE,
	val crossfadeEnabled: Boolean = false,
	val httpClient: OkHttpClient? = null,
) {
	fun enqueue(request: ImageRequest): Disposable {
		if (request.memoryCachePolicy.writeEnabled && request.memoryCacheKey != null) {
			memoryCache?.put(request.memoryCacheKey, request.data ?: request.memoryCacheKey)
		}
		if (request.diskCachePolicy.writeEnabled) {
			val dKey = request.diskCacheKey ?: request.data?.toString()
			if (dKey != null) {
				diskCache?.recordKey(dKey)
			}
		}
		return object : Disposable {
			override var isDisposed: Boolean = false
			override fun dispose() {
				isDisposed = true
			}
		}
	}

	suspend fun execute(request: ImageRequest): ImageRequest {
		enqueue(request)
		return request
	}

	class Builder(private val context: Context) {
		private var memoryCacheProvider: (() -> MemoryCache?)? = null
		private var diskCacheProvider: (() -> DiskCache?)? = null
		private var allowHardwareVal: Boolean = true
		private var bitmapConfigVal: Bitmap.Config = Bitmap.Config.HARDWARE
		private var crossfadeVal: Boolean = false
		private var httpClientVal: OkHttpClient? = null

		fun memoryCache(initializer: () -> MemoryCache?): Builder = apply {
			this.memoryCacheProvider = initializer
		}

		fun diskCache(initializer: () -> DiskCache?): Builder = apply {
			this.diskCacheProvider = initializer
		}

		fun allowHardware(enable: Boolean): Builder = apply {
			this.allowHardwareVal = enable
		}

		fun bitmapConfig(config: Bitmap.Config): Builder = apply {
			this.bitmapConfigVal = config
		}

		fun crossfade(enable: Boolean): Builder = apply {
			this.crossfadeVal = enable
		}

		fun httpClient(client: OkHttpClient): Builder = apply {
			this.httpClientVal = client
		}

		fun build(): ImageLoader = ImageLoader(
			context = context,
			memoryCache = memoryCacheProvider?.invoke(),
			diskCache = diskCacheProvider?.invoke(),
			allowHardware = allowHardwareVal,
			bitmapConfig = bitmapConfigVal,
			crossfadeEnabled = crossfadeVal,
			httpClient = httpClientVal,
		)
	}
}
