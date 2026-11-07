package org.koitharu.kotatsu.reader.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import java.io.File

enum class WidePageFitMode {
	NONE,
	SPLIT_INTO_TWO,
	ROTATE_90_CCW,
}

object WidePageProcessor {
	private const val WIDE_ASPECT_THRESHOLD = 1.25f

	fun isWideSpread(imageFile: File): Boolean {
		val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeFile(imageFile.absolutePath, opts)
		return opts.outWidth > 0 &&
			opts.outHeight > 0 &&
			(opts.outWidth.toFloat() / opts.outHeight.toFloat()) > WIDE_ASPECT_THRESHOLD
	}

	/**
	 * Slices a double-page manga spread into two sequential pages in RTL (Right half first)
	 * or LTR (Left half first) order using BitmapRegionDecoder without loading the whole bitmap twice.
	 */
	@Suppress("DEPRECATION")
	fun decodeSplitHalf(
		imageFile: File,
		isFirstHalfInReadingOrder: Boolean,
		isRtlReading: Boolean,
	): Bitmap? {
		val decoder = BitmapRegionDecoder.newInstance(imageFile.absolutePath, false) ?: return null
		return try {
			val w = decoder.width
			val h = decoder.height
			val halfW = w / 2

			// In Manga (RTL), the first page read is the RIGHT half of the spread
			val decodeRightSide = if (isRtlReading) isFirstHalfInReadingOrder else !isFirstHalfInReadingOrder
			val rect = if (decodeRightSide) {
				Rect(halfW, 0, w, h)
			} else {
				Rect(0, 0, halfW, h)
			}
			decoder.decodeRegion(
				rect,
				BitmapFactory.Options().apply {
					inPreferredConfig = Bitmap.Config.RGB_565
				},
			)
		} finally {
			decoder.recycle()
		}
	}

	fun rotateWideBitmap(source: Bitmap): Bitmap {
		val matrix = Matrix().apply { postRotate(90f) }
		return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
	}
}
