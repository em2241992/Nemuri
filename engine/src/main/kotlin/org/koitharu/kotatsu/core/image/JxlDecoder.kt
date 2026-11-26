package org.koitharu.kotatsu.core.image

import android.graphics.Bitmap

/** The bundled JPEG XL decoder (native/jxl, decoder-only libjxl): Android has none of its own. */
object JxlDecoder {

	val isAvailable: Boolean = runCatching {
		System.loadLibrary("jxldec")
		true
	}.getOrDefault(false)

	fun decodeSafe(bytes: ByteArray): Bitmap? = if (!isAvailable) {
		null
	} else {
		runCatching { decode(bytes) }.getOrNull()
	}

	fun decodeBoundsSafe(bytes: ByteArray): IntArray? = if (!isAvailable) {
		null
	} else {
		runCatching { decodeBounds(bytes) }.getOrNull()
	}

	/** The first frame as an sRGB ARGB_8888 bitmap, or null if the data cannot be decoded. */
	external fun decode(bytes: ByteArray): Bitmap?

	/** Width and height (`intArrayOf(width, height)`) from the JXL header, or null on invalid data. */
	external fun decodeBounds(bytes: ByteArray): IntArray?
}
