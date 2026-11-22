package eu.kanade.tachiyomi

import org.koitharu.kotatsu.core.nav.EngineRuntimeConfig

/**
 * Provides host application info for extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
	fun getVersionCode(): Int = EngineRuntimeConfig.versionCode
	fun getVersionName(): String = EngineRuntimeConfig.versionName

	/**
	 * A list of image MIME types supported by the reader.
	 *
	 * @since extension-lib 1.5
	 */
	fun getSupportedImageMimeTypes(): List<String> = listOf(
		"image/jpeg",
		"image/png",
		"image/gif",
		"image/webp",
		"image/avif",
		"image/jxl",
		"image/heif",
	)
}
