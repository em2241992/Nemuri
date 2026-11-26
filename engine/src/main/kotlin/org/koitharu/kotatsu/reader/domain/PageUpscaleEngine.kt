package org.koitharu.kotatsu.reader.domain

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Size
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Opt-in 2x neural reader page upscaling coordinator featuring:
 * - Pre-decode bounds check (`height > 4096` Webtoon strips skip bitmap decode and route directly to real-time GPU shaders)
 * - Scope-isolated in-flight deduplication + instant stale lookahead cancellation when flipping past pages
 * - LRU disk cache, Battery Saver pause, and low-RAM OOM guard
 * - Live active hardware delegate reporting ([activeDelegateLabel])
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

	val activeDelegateLabel: StateFlow<String>
		get() = runner.activeDelegateLabel

	private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val cacheDir: File
		get() = File(context.cacheDir, "neural_upscale").apply { mkdirs() }

	private val _cacheBytesFlow = MutableStateFlow(computeCacheSizeBytes())
	val cacheBytesFlow: StateFlow<Long> = _cacheBytesFlow.asStateFlow()

	private val inFlight = ConcurrentHashMap<String, InFlightTask>()

	private data class InFlightTask(
		val pageIndex: Int,
		val deferred: CompletableDeferred<Uri?>,
		var job: Job? = null,
	)

	/**
	 * Immediately cancels queued/running neural lookahead tasks whose raw/logical page index
	 * falls outside [keepRange] (e.g. pages already skipped behind the user).
	 */
	fun cancelStaleLookahead(activeIndex: Int, keepRange: IntRange = (activeIndex - 1)..(activeIndex + 2)) {
		if (activeIndex < 0) return
		inFlight.values.forEach { task ->
			if (task.pageIndex >= 0 && task.pageIndex !in keepRange) {
				task.job?.cancel()
				task.deferred.complete(null)
			}
		}
	}

	suspend fun upscalePageIfEnabled(
		sourceUri: Uri,
		cropBounds: Rect?,
		isWebtoon: Boolean,
		pageIndex: Int = -1,
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

		// Header-only dimension check BEFORE allocating any Bitmap in memory.
		// Ultra-tall Webtoon strips (> 4096px) are bypassed cleanly to real-time GPU shaders.
		val preBounds = readBoundsOnly(sourceUri)
		if (preBounds != null && !isSafeDimensions(preBounds.width, preBounds.height, isWebtoon)) {
			return@withContext sourceUri to cropBounds
		}

		val (modelEntry, modelFile) = resolvedModel
		val accel = NeuralAccelerator.fromKey(settings.neuralUpscaleAccelerator)
		val key = cacheKey("${sourceUri}|${modelEntry.id}|${modelFile.length()}")
		val cachedFile = File(cacheDir, "$key.webp")

		if (cachedFile.isFile && cachedFile.length() > MIN_VALID_CACHE_BYTES) {
			cachedFile.setLastModified(System.currentTimeMillis())
			return@withContext cachedFile.toUri() to scaleBoundsBy2(cropBounds)
		}

		inFlight[key]?.let { existingTask ->
			val existingUri = runCatching { existingTask.deferred.await() }.getOrNull()
			if (existingUri != null) {
				return@withContext existingUri to scaleBoundsBy2(cropBounds)
			}
		}

		val ownTask = InFlightTask(pageIndex = pageIndex, deferred = CompletableDeferred())
		val raceWinner = inFlight.putIfAbsent(key, ownTask)
		if (raceWinner != null) {
			val existingUri = runCatching { raceWinner.deferred.await() }.getOrNull()
			return@withContext if (existingUri != null) {
				existingUri to scaleBoundsBy2(cropBounds)
			} else {
				sourceUri to cropBounds
			}
		}

		// Execute actual upscale on internal engineScope so cancelling a lookahead coroutine
		// when advancing onto page N+1 never aborts the active N+1 job that display awaits.
		val workerJob = engineScope.launch {
			var decoded: Bitmap? = null
			var upscaled: Bitmap? = null
			val tmpFile = File(cacheDir, "$key.tmp")
			try {
				decoded = decodeSourceBitmap(sourceUri)
				if (decoded == null || !isSafeDimensions(decoded.width, decoded.height, isWebtoon)) {
					ownTask.deferred.complete(null)
					return@launch
				}

				upscaled = runner.upscaleBitmap2x(decoded, modelFile, accel)
				decoded.recycle()
				decoded = null

				val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					Bitmap.CompressFormat.WEBP_LOSSY
				} else {
					@Suppress("DEPRECATION")
					Bitmap.CompressFormat.WEBP
				}
				tmpFile.delete()
				FileOutputStream(tmpFile).use { out ->
					upscaled.compress(format, WEBP_CACHE_QUALITY, out)
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
				_cacheBytesFlow.value = computeCacheSizeBytes()
				ownTask.deferred.complete(cachedFile.toUri())
			} catch (_: Throwable) {
				tmpFile.delete()
				ownTask.deferred.complete(null)
			} finally {
				decoded?.recycle()
				upscaled?.recycle()
				inFlight.remove(key, ownTask)
			}
		}
		ownTask.job = workerJob

		val resultUri = runCatching { ownTask.deferred.await() }.getOrNull()
		if (resultUri != null) {
			resultUri to scaleBoundsBy2(cropBounds)
		} else {
			sourceUri to cropBounds
		}
	}

	fun computeCacheSizeBytes(): Long =
		cacheDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

	suspend fun clearCache() = withContext(Dispatchers.IO) {
		inFlight.values.forEach {
			it.job?.cancel()
			it.deferred.complete(null)
		}
		inFlight.clear()
		cacheDir.listFiles()?.forEach { it.delete() }
		runner.closeSession()
		_cacheBytesFlow.value = 0L
	}

	suspend fun releaseSession() {
		inFlight.values.forEach {
			it.job?.cancel()
			it.deferred.complete(null)
		}
		inFlight.clear()
		runner.closeSession()
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
		val allFiles = cacheDir.listFiles()?.filter { it.isFile } ?: return
		val staleTmpCutoff = System.currentTimeMillis() - 10 * 60 * 1000L
		val activeFiles = ArrayList<File>(allFiles.size)
		for (file in allFiles) {
			if (file.name.endsWith(".tmp") && file.lastModified() < staleTmpCutoff) {
				file.delete()
			} else {
				activeFiles += file
			}
		}
		var total = activeFiles.sumOf { it.length() }
		if (total <= MAX_DISK_CACHE_BYTES) return
		val sorted = activeFiles.sortedBy { it.lastModified() }
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
		private const val WEBP_CACHE_QUALITY = 92
		private const val MAX_PAGED_WIDTH = 2200
		private const val MAX_PAGED_HEIGHT = 4096
		private const val MAX_WEBTOON_HEIGHT = 4096
		private const val MAX_SOURCE_PIXELS = 4_800_000L
		private const val MAX_DISK_CACHE_BYTES = 250L * 1024L * 1024L
		private const val TARGET_AFTER_TRIM_BYTES = 180L * 1024L * 1024L
	}
}
