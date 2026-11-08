package org.koitharu.kotatsu.reader.domain

import android.graphics.Bitmap

/**
 * Nemuri Wide-Page / Double-Spread Splitter:
 * Detects landscape double-page spreads (`width > height * aspectThreshold`) and splits them
 * into ordered single pages according to the reader's reading direction (RTL Manga vs LTR Comic).
 */
object WidePageSplitter {

	private const val DEFAULT_WIDE_ASPECT_THRESHOLD = 1.25f

	fun isWideSpread(bitmap: Bitmap, aspectThreshold: Float = DEFAULT_WIDE_ASPECT_THRESHOLD): Boolean {
		if (bitmap.width <= 0 || bitmap.height <= 0) return false
		return bitmap.width.toFloat() / bitmap.height.toFloat() >= aspectThreshold
	}

	/**
	 * Splits a wide double-page spread into two half-page bitmaps in reading order:
	 * - If `isRtl == true` (Japanese Manga): `[rightHalf, leftHalf]`
	 * - If `isRtl == false` (LTR Manhwa/Comic): `[leftHalf, rightHalf]`
	 * If the bitmap is not a wide spread, returns `listOf(bitmap)`.
	 */
	fun splitIfWide(
		bitmap: Bitmap,
		isRtl: Boolean = true,
		aspectThreshold: Float = DEFAULT_WIDE_ASPECT_THRESHOLD,
	): List<Bitmap> {
		if (!isWideSpread(bitmap, aspectThreshold)) {
			return listOf(bitmap)
		}
		val midX = bitmap.width / 2
		if (midX <= 0) return listOf(bitmap)

		val leftHalf = Bitmap.createBitmap(bitmap, 0, 0, midX, bitmap.height)
		val rightHalf = Bitmap.createBitmap(bitmap, midX, 0, bitmap.width - midX, bitmap.height)
		return if (isRtl) {
			listOf(rightHalf, leftHalf)
		} else {
			listOf(leftHalf, rightHalf)
		}
	}
}
