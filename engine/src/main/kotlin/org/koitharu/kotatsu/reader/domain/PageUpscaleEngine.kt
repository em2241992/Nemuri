package org.koitharu.kotatsu.reader.domain

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.image.BitmapDecoderCompat
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.MimeTypes
import org.koitharu.kotatsu.core.util.ext.isPdfUri
import org.koitharu.kotatsu.core.util.ext.isZipUri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in 2x neural reader page upscaling coordinator with LRU disk cache, in-flight
 * deduplication, Battery Saver pause, low-RAM OOM guard, and strict Webtoon strip caps.
 */
@Singleton
class PageUpscaleEngine @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val modelManager: NeuralUpscaleModelManager,
	private val runner: NeuralUpscaleRunner,
) {

	val isSupported: Boolean
		get() = runner.isSupported

	private val cacheDir: File
		get() = File(context.cacheDir, "neural_upscale").apply { mkdirs() }

	private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Uri?>>()

	suspend fun upscalePageIfEnabled(
		sourceUri: Uri,
		cropBounds: Rect?,
		isWebtoon: Boolean,
	): Pair<Uri, Rect?> = withContext(Dispatchers.IO) {
		if (!runner.isSupported || !settings.isNeuralUpscaleEnabled) {
			return@withContext sourceUri to cropBounds
		}
		if (sourceUri.isPdfUri()) {
			return@withContext sourceUri to cropBounds
		}
		val resolvedModel = modelManager.resolveActiveReadyModel()
			?: return@withContext sourceUri to cropBounds
		if (isBatterySaverOrLowRam()) {
			return@withContext sourceUri to cropBounds
		}

		val (modelEntry, modelFile) = resolvedModel
		val key = cacheKey("${sourceUri}|${modelEntry.id}|${modelFile.length()}")
		val cachedFile = File(cacheDir, "$key.webp")

		if (cachedFile.isFile && cachedFile.length() > MIN_VALID_CACHE_BYTES) {
			cachedFile.setLastModified(System.currentTimeMillis())
			return@withContext cachedFile.toUri() to scaleBoundsBy2(cropBounds)
		}

		// Deduplicate concurrent upscaling for the same page (e.g., ahead pre-upscaler + active view)
		inFlight[key]?.let { existingDeferred ->
			val existingUri = runCatching { existingDeferred.await() }.getOrNull()
			if (existingUri != null) {
				return@withContext existingUri to scaleBoundsBy2(cropBounds)
			}
		}

		val ownDeferred = CompletableDeferred<Uri?>()
		val raceWinner = inFlight.putIfAbsent(key, ownDeferred)
		if (raceWinner != null) {
			val existingUri = runCatching { raceWinner.await() }.getOrNull()
			return@withContext if (existingUri != null) {
				existingUri to scaleBoundsBy2(cropBounds)
			} else {
				sourceUri to cropBounds
			}
		}

		var decoded: Bitmap? = null
		var upscaled: Bitmap? = null
		val tmpFile = File(cacheDir, "$key.tmp")

		try {
			decoded = decodeSourceBitmap(sourceUri)
			if (decoded == null || !isSafeDimensions(decoded.width, decoded.height, isWebtoon)) {
				ownDeferred.complete(null)
				return@withContext sourceUri to cropBounds
			}

			val accel = NeuralAccelerator.fromKey(settings.neuralUpscaleAccelerator)
			upscaled = runner.upscaleBitmap2x(decoded, modelFile, accel)
			decoded.recycle()
			decoded = null

			val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				Bitmap.CompressFormat.WEBP_LOSSLESS
			} else {
				@Suppress("DEPRECATION")
				Bitmap.CompressFormat.WEBP
			}
			tmpFile.delete()
			FileOutputStream(tmpFile).use { out ->
				upscaled.compress(format, 95, out)
				out.flush()
			}
			upscaled.recycle()
			upscaled = null

			if (cachedFile.exists()) cachedFile.delete()
			if (!tmpFile.renameTo(cachedFile)) {
				tmpFile.copyTo(cachedFile, overwrite = true)
				tmpFile.delete()
			}
			trimCacheIfNeeded()
			val resultUri = cachedFile.toUri()
			ownDeferred.complete(resultUri)
			resultUri to scaleBoundsBy2(cropBounds)
		} catch (_: Throwable) {
			tmpFile.delete()
			ownDeferred.complete(null)
			sourceUri to cropBounds
		} finally {
			decoded?.recycle()
			upscaled?.recycle()
			inFlight.remove(key, ownDeferred)
		}
	}

	fun computeCacheSizeBytes(): Long =
		cacheDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

	suspend fun clearCache() = withContext(Dispatchers.IO) {
		cacheDir.listFiles()?.forEach { it.delete() }
		runner.closeSession()
	}

	suspend fun releaseSession() {
		runner.closeSession()
	}

	private fun decodeSourceBitmap(uri: Uri): Bitmap? = runCatching {
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

	private fun isSafeDimensions(width: Int, height: Int, isWebtoon: Boolean): Boolean {
		if (width <= 0 || height <= 0) return false
		val pixelCount = width.toLong() * height.toLong()
		if (pixelCount > MAX_SOURCE_PIXELS) return false
		return if (isWebtoon) {
			height <= MAX_WEBTOON_HEIGHT && width <= MAX_PAGED_WIDTH
		} else {
			width <= MAX_PAGED_WIDTH && height <= MAX_PAGED_HEIGHT
		}
	}

	private fun isBatterySaverOrLowRam(): Boolean {
		val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
		if (pm?.isPowerSaveMode == true) return true
		val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
		if (am.isLowRamDevice) return true
		val memInfo = ActivityManager.MemoryInfo()
		am.getMemoryInfo(memInfo)
		return memInfo.lowMemory || memInfo.availMem < MIN_FREE_RAM_BYTES
	}

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

	private fun scaleBoundsBy2(rect: Rect?): Rect? = rect?.let {
		Rect(it.left * 2, it.top * 2, it.right * 2, it.bottom * 2)
	}

	private fun cacheKey(raw: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
		return buildString(digest.size * 2) {
			for (b in digest) {
				append(((b.toInt() ushr 4) and 0xF).toString(16))
				append((b.toInt() and 0xF).toString(16))
			}
		}
	}

	companion object {
		private const val MIN_VALID_CACHE_BYTES = 1_024L
		private const val MIN_FREE_RAM_BYTES = 500L * 1024L * 1024L
		private const val MAX_PAGED_WIDTH = 2200
		private const val MAX_PAGED_HEIGHT = 3600
		private const val MAX_WEBTOON_HEIGHT = 3200
		private const val MAX_SOURCE_PIXELS = 4_200_000L
		private const val MAX_DISK_CACHE_BYTES = 250L * 1024L * 1024L
		private const val TARGET_AFTER_TRIM_BYTES = 180L * 1024L * 1024L
	}
}
