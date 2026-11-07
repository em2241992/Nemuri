package coil3.request

import android.content.Context
import android.graphics.Bitmap
import coil3.size.Size

enum class CachePolicy(
	val readEnabled: Boolean,
	val writeEnabled: Boolean,
) {
	ENABLED(true, true),
	READ_ONLY(true, false),
	WRITE_ONLY(false, true),
	DISABLED(false, false),
}

data class ImageRequest(
	val context: Context,
	val data: Any?,
	val memoryCacheKey: String?,
	val diskCacheKey: String?,
	val placeholderMemoryCacheKey: String?,
	val size: Size = Size.ORIGINAL,
	val allowHardware: Boolean = true,
	val crossfadeEnabled: Boolean = false,
	val bitmapConfig: Bitmap.Config = Bitmap.Config.HARDWARE,
	val memoryCachePolicy: CachePolicy = CachePolicy.ENABLED,
	val diskCachePolicy: CachePolicy = CachePolicy.ENABLED,
) {
	class Builder(private val context: Context) {
		internal var dataValue: Any? = null
		internal var memoryCacheKeyValue: String? = null
		internal var diskCacheKeyValue: String? = null
		internal var placeholderMemoryCacheKeyValue: String? = null
		internal var sizeValue: Size = Size.ORIGINAL
		internal var allowHardwareValue: Boolean = true
		internal var crossfadeValue: Boolean = false
		internal var bitmapConfigValue: Bitmap.Config = Bitmap.Config.HARDWARE
		internal var memoryCachePolicyValue: CachePolicy = CachePolicy.ENABLED
		internal var diskCachePolicyValue: CachePolicy = CachePolicy.ENABLED

		fun data(data: Any?): Builder = apply { this.dataValue = data }
		fun memoryCacheKey(key: String?): Builder = apply { this.memoryCacheKeyValue = key }
		fun diskCacheKey(key: String?): Builder = apply { this.diskCacheKeyValue = key }
		fun placeholderMemoryCacheKey(key: String?): Builder = apply { this.placeholderMemoryCacheKeyValue = key }
		fun size(size: Size): Builder = apply { this.sizeValue = size }
		fun size(width: Int, height: Int): Builder = apply { this.sizeValue = Size(width, height) }
		fun memoryCachePolicy(policy: CachePolicy): Builder = apply { this.memoryCachePolicyValue = policy }
		fun diskCachePolicy(policy: CachePolicy): Builder = apply { this.diskCachePolicyValue = policy }

		fun build(): ImageRequest = ImageRequest(
			context = context,
			data = dataValue,
			memoryCacheKey = memoryCacheKeyValue,
			diskCacheKey = diskCacheKeyValue,
			placeholderMemoryCacheKey = placeholderMemoryCacheKeyValue,
			size = sizeValue,
			allowHardware = allowHardwareValue,
			crossfadeEnabled = crossfadeValue,
			bitmapConfig = bitmapConfigValue,
			memoryCachePolicy = memoryCachePolicyValue,
			diskCachePolicy = diskCachePolicyValue,
		)
	}
}

fun ImageRequest.Builder.allowHardware(enable: Boolean): ImageRequest.Builder = apply {
	this.allowHardwareValue = enable
}

fun ImageRequest.Builder.crossfade(enable: Boolean): ImageRequest.Builder = apply {
	this.crossfadeValue = enable
}

fun ImageRequest.Builder.bitmapConfig(config: Bitmap.Config): ImageRequest.Builder = apply {
	this.bitmapConfigValue = config
}
