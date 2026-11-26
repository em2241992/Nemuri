package org.koitharu.kotatsu.reader.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.image.BitmapDecoderCompat
import org.koitharu.kotatsu.core.util.MimeTypes
import org.koitharu.kotatsu.core.util.ext.isPdfUri
import org.koitharu.kotatsu.core.util.ext.isZipUri
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Reassembles unevenly chopped or orphan-sliver manga/webtoon slices into seamless single
 * logical pages before rendering and upscaling, preserving exact chapter progress and bookmarks.
 */
@Singleton
class PageStitcher @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val cacheDir: File
		get() = File(context.cacheDir, "stitched_pages").apply { mkdirs() }

	private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Pair<Uri, Size>?>>()

	/**
	 * Groups adjacent same-chapter slices with matching widths (`±2%`) when a slice is an
	 * orphan fragment/sliver (`height < width * 0.55f`) or split halves whose combined
	 * height stays within the `4096px` GPU texture cap.
	 */
	suspend fun groupChapterSlices(
		chapterId: Long,
		rawPages: List<MangaPage>,
		isWebtoon: Boolean,
		boundsProvider: suspend (MangaPage) -> Size?,
	): List<ReaderPage> {
		if (rawPages.size <= 1) {
			return rawPages.mapIndexed { idx, p -> ReaderPage(p, idx, chapterId) }
		}

		// Inspect header dimensions with bounded parallelism to keep chapter load snappy.
		val semaphore = Semaphore(BOUNDS_CONCURRENCY)
		val boundsList: List<Size?> = coroutineScope {
			rawPages.map { page ->
				async(Dispatchers.IO) {
					semaphore.withPermit {
						runCatching { boundsProvider(page) }.getOrNull()
					}
				}
			}.awaitAll()
		}

		val result = ArrayList<ReaderPage>(rawPages.size)
		var i = 0
		while (i < rawPages.size) {
			val basePage = rawPages[i]
			val baseSize = boundsList[i]
			if (baseSize == null ||
				baseSize.width <= 0 ||
				baseSize.height <= 0 ||
				baseSize.height >= MAX_GPU_TEXTURE_HEIGHT ||
				basePage.url.endsWith(".pdf", ignoreCase = true)
			) {
				result += ReaderPage(basePage, i, chapterId)
				i++
				continue
			}

			val chain = ArrayList<MangaPage>(MAX_CHAIN_LENGTH)
			chain += basePage
			val w0 = baseSize.width
			var accumulatedH = baseSize.height
			var lastH = baseSize.height
			var j = i + 1

			while (j < rawPages.size && chain.size < MAX_CHAIN_LENGTH) {
				val nextPage = rawPages[j]
				if (nextPage.url.endsWith(".pdf", ignoreCase = true)) break
				val nextSize = boundsList[j] ?: break
				val nw = nextSize.width
				val nh = nextSize.height
				if (nw <= 0 || nh <= 0) break

				val widthDiffRatio = abs(nw - w0).toFloat() / maxOf(nw, w0).toFloat()
				if (widthDiffRatio > WIDTH_TOLERANCE_RATIO) break

				val scaledNextH = ((nh.toLong() * w0) / nw).toInt().coerceAtLeast(1)
				val combinedH = accumulatedH + scaledNextH
				if (combinedH > MAX_GPU_TEXTURE_HEIGHT) break

				val tailIsSliver = lastH < w0 * SLIVER_HEIGHT_RATIO
				val nextIsSliver = nh < nw * SLIVER_HEIGHT_RATIO
				val unevenSplitPair = lastH <= w0 * SPLIT_HALF_MAX_RATIO &&
					nh <= nw * SPLIT_HALF_MAX_RATIO &&
					combinedH <= (w0 * COMBINED_PAGE_MAX_RATIO).toInt().coerceAtMost(MAX_GPU_TEXTURE_HEIGHT)
				val webtoonSubPagePair = isWebtoon &&
					lastH < w0 * WEBTOON_SUBSTRIP_RATIO &&
					nh < nw * WEBTOON_SUBSTRIP_RATIO

				if (!(tailIsSliver || nextIsSliver || unevenSplitPair || webtoonSubPagePair)) {
					break
				}

				chain += nextPage
				accumulatedH = combinedH
				lastH = scaledNextH
				j++
			}

			if (chain.size <= 1) {
				result += ReaderPage(basePage, index = i, chapterId = chapterId)
			} else {
				result += ReaderPage(
					id = basePage.id,
					url = basePage.url,
					preview = basePage.preview,
					chapterId = chapterId,
					index = i,
					source = basePage.source,
					isSecondHalf = false,
					endIndex = j - 1,
					stitchedPages = chain,
				)
			}
			i = j
		}
		return result
	}

	/**
	 * Composites adjacent [sliceUris] vertically with `0px` seams into a single lossless WebP
	 * file in `cacheDir/stitched_pages/` so decoding/upscaling runs cleanly once per logical page.
	 */
	suspend fun compositeSlices(
		sliceUris: List<Uri>,
		cacheKeySeed: String,
	): Pair<Uri, Size>? = withContext(Dispatchers.IO) {
		if (sliceUris.size < 2 || sliceUris.any { it.isPdfUri() }) {
			return@withContext null
		}
		val rawKey = buildString {
			append(cacheKeySeed)
			sliceUris.forEach {
				append('|')
				append(it.toString())
			}
		}
		val key = sha256(rawKey)
		val cachedFile = File(cacheDir, "$key.webp")
		if (cachedFile.isFile && cachedFile.length() > MIN_VALID_CACHE_BYTES) {
			cachedFile.setLastModified(System.currentTimeMillis())
			readBoundsOnly(cachedFile.toUri())?.let { size ->
				return@withContext cachedFile.toUri() to size
			}
		}

		inFlight[key]?.let { existing ->
			return@withContext runCatching { existing.await() }.getOrNull()
		}
		val ownDeferred = CompletableDeferred<Pair<Uri, Size>?>()
		val winner = inFlight.putIfAbsent(key, ownDeferred)
		if (winner != null) {
			return@withContext runCatching { winner.await() }.getOrNull()
		}

		val tmpFile = File(cacheDir, "$key.tmp")
		var composite: Bitmap? = null
		try {
			val sliceSizes = sliceUris.map { readBoundsOnly(it) }
			if (sliceSizes.any { it == null || it.width <= 0 || it.height <= 0 }) {
				ownDeferred.complete(null)
				return@withContext null
			}
			val nonNullSizes = sliceSizes.filterNotNull()
			val targetW = nonNullSizes.first().width.coerceIn(1, MAX_TARGET_WIDTH)
			val sliceHeights = nonNullSizes.map { sz ->
				((sz.height.toLong() * targetW) / sz.width).toInt().coerceAtLeast(1)
			}
			val totalH = sliceHeights.sum()
			if (totalH <= 0 || totalH > MAX_GPU_TEXTURE_HEIGHT) {
				ownDeferred.complete(null)
				return@withContext null
			}

			composite = Bitmap.createBitmap(targetW, totalH, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(composite)
			val paint = Paint(Paint.FILTER_BITMAP_FLAG)
			var curY = 0
			for (idx in sliceUris.indices) {
				val sliceH = sliceHeights[idx]
				val sliceBmp = decodeSliceBitmap(sliceUris[idx])
				if (sliceBmp == null) {
					ownDeferred.complete(null)
					return@withContext null
				}
				try {
					val srcRect = Rect(0, 0, sliceBmp.width, sliceBmp.height)
					val dstRect = Rect(0, curY, targetW, curY + sliceH)
					canvas.drawBitmap(sliceBmp, srcRect, dstRect, paint)
				} finally {
					sliceBmp.recycle()
				}
				curY += sliceH
			}

			val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				Bitmap.CompressFormat.WEBP_LOSSLESS
			} else {
				@Suppress("DEPRECATION")
				Bitmap.CompressFormat.WEBP
			}
			tmpFile.delete()
			FileOutputStream(tmpFile).use { out ->
				composite.compress(format, 95, out)
				out.flush()
			}
			composite.recycle()
			composite = null

			if (cachedFile.exists()) cachedFile.delete()
			if (!tmpFile.renameTo(cachedFile)) {
				tmpFile.copyTo(cachedFile, overwrite = true)
				tmpFile.delete()
			}
			trimCacheIfNeeded()
			val outPair = cachedFile.toUri() to Size(targetW, totalH)
			ownDeferred.complete(outPair)
			outPair
		} catch (_: Throwable) {
			tmpFile.delete()
			ownDeferred.complete(null)
			null
		} finally {
			composite?.recycle()
			inFlight.remove(key, ownDeferred)
		}
	}

	private fun readBoundsOnly(uri: Uri): Size? = runCatching {
		val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		if (uri.isZipUri()) {
			val zipPath = uri.schemeSpecificPart ?: return@runCatching null
			val entryName = uri.fragment ?: return@runCatching null
			ZipFile(zipPath).use { zip ->
				val entry = zip.getEntry(entryName) ?: return@runCatching null
				zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, opts) }
			}
		} else {
			context.contentResolver.openInputStream(uri)?.use {
				BitmapFactory.decodeStream(it, null, opts)
			}
		}
		if (opts.outWidth > 0 && opts.outHeight > 0) Size(opts.outWidth, opts.outHeight) else null
	}.getOrNull()

	private fun decodeSliceBitmap(uri: Uri): Bitmap? = runCatching {
		if (uri.isZipUri()) {
			val zipPath = uri.schemeSpecificPart ?: return@runCatching null
			val entryName = uri.fragment ?: return@runCatching null
			ZipFile(zipPath).use { zip ->
				val entry = zip.getEntry(entryName) ?: return@runCatching null
				zip.getInputStream(entry).use { stream ->
					BitmapDecoderCompat.decode(stream, MimeTypes.getMimeTypeFromExtension(entry.name))
				}
			}
		} else {
			context.contentResolver.openInputStream(uri)?.use { stream ->
				BitmapDecoderCompat.decode(stream, null)
			}
		}
	}.getOrNull()

	private fun trimCacheIfNeeded() {
		val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".webp") } ?: return
		var total = files.sumOf { it.length() }
		if (total <= MAX_DISK_CACHE_BYTES) return
		val sorted = files.sortedBy { it.lastModified() }
		for (f in sorted) {
			if (total <= TARGET_AFTER_TRIM_BYTES) break
			val len = f.length()
			if (f.delete()) {
				total -= len
			}
		}
	}

	private fun sha256(raw: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
		return buildString(digest.size * 2) {
			for (b in digest) {
				append(((b.toInt() ushr 4) and 0xF).toString(16))
				append((b.toInt() and 0xF).toString(16))
			}
		}
	}

	companion object {
		private const val BOUNDS_CONCURRENCY = 6
		private const val MAX_CHAIN_LENGTH = 8
		private const val WIDTH_TOLERANCE_RATIO = 0.02f
		private const val SLIVER_HEIGHT_RATIO = 0.55f
		private const val SPLIT_HALF_MAX_RATIO = 1.15f
		private const val COMBINED_PAGE_MAX_RATIO = 1.65f
		private const val WEBTOON_SUBSTRIP_RATIO = 0.90f
		private const val MAX_TARGET_WIDTH = 2200
		const val MAX_GPU_TEXTURE_HEIGHT = 4096
		private const val MIN_VALID_CACHE_BYTES = 1_024L
		private const val MAX_DISK_CACHE_BYTES = 150L * 1024L * 1024L
		private const val TARGET_AFTER_TRIM_BYTES = 110L * 1024L * 1024L
	}
}
