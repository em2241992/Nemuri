package org.koitharu.kotatsu.reader.domain

import android.content.Context
import android.net.Uri
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Manages lightweight on-demand ONNX and LiteRT (`.tflite`) super-resolution models stored
 * in [Context.filesDir] so zero model weights are bundled inside the APK.
 */
@Singleton
class NeuralUpscaleModelManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient private val okHttpClientProvider: Provider<OkHttpClient>,
	private val settings: AppSettings,
) {

	private val modelsDir: File
		get() = File(context.filesDir, "upscale_models").apply { mkdirs() }

	val catalog: List<NeuralUpscaleModelEntry> = listOf(
		NeuralUpscaleModelEntry(
			id = "span_2x_manga",
			title = "SPAN 2x Manga & Line-Art",
			subtitle = "Recommended fast SPAN halftone & ink restoration • ~1.6 MB",
			fileName = "2xHFA2k_LUDVAE_SPAN_fp32_opset17.onnx",
			downloadUrl = "https://huggingface.co/nesaorg/2xHFA2k_LUDVAE_SPAN_fp32_opset17/resolve/main/2xHFA2k_LUDVAE_SPAN_fp32_opset17.onnx",
			approxSizeBytes = 1_655_053L,
			nativeScale = 2,
			isRecommended = true,
			formatBadge = "ONNX",
		),
		NeuralUpscaleModelEntry(
			id = "quicksr_small_int8_tflite",
			title = "QuickSRNet Compact INT8",
			subtitle = "Hardware-quantized LiteRT .tflite for NPU/GPU • ~44 KB",
			fileName = "quicksrnetsmall_w8a8.tflite",
			downloadUrl = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/quicksrnetsmall/releases/v0.67.0/quicksrnetsmall-tflite-w8a8.zip",
			approxSizeBytes = 43_672L,
			nativeScale = 4,
			formatBadge = "TFLite INT8",
		),
		NeuralUpscaleModelEntry(
			id = "quicksr_small_fp_tflite",
			title = "QuickSRNet Compact FP16",
			subtitle = "Fast LiteRT .tflite NPU/GPU super-resolution • ~134 KB",
			fileName = "quicksrnetsmall_float.tflite",
			downloadUrl = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/quicksrnetsmall/releases/v0.67.0/quicksrnetsmall-tflite-float.zip",
			approxSizeBytes = 136_904L,
			nativeScale = 4,
			formatBadge = "TFLite FP16",
		),
		NeuralUpscaleModelEntry(
			id = "waifu2x_upconv7_2x",
			title = "Waifu2x UpConv-7 Anime 2x",
			subtitle = "Fast 2x manga line restoration + denoise • ~2.1 MB",
			fileName = "waifu2x_upconv7_anime_noise1_2x.onnx",
			downloadUrl = "https://huggingface.co/lookx2/waifu2x_upconv7/resolve/main/waifu2x_upconv7_anime_noise1_2x.onnx",
			approxSizeBytes = 2_212_306L,
			nativeScale = 2,
			formatBadge = "ONNX",
		),
		NeuralUpscaleModelEntry(
			id = "realesr_animevideov3",
			title = "Real-ESRGAN AnimeVideo v3",
			subtitle = "Compact SRVGGNet restoration (clean 2x downsample) • ~2.4 MB",
			fileName = "realesr_animevideov3_fp32.onnx",
			downloadUrl = "https://huggingface.co/skillsafe-ai/realesr-animevideov3/resolve/main/model.onnx",
			approxSizeBytes = 2_492_908L,
			nativeScale = 4,
			formatBadge = "ONNX",
		),
		NeuralUpscaleModelEntry(
			id = "waifu2x_cunet_art_2x",
			title = "Waifu2x CUNet Art 2x",
			subtitle = "High-detail illustration & screentone cleanup • ~4.9 MB",
			fileName = "waifu2x_cunet_art_noise1_scale2x.onnx",
			downloadUrl = "https://huggingface.co/deepghs/waifu2x_onnx/resolve/main/20250502/onnx_models/cunet/art/noise1_scale2x.onnx",
			approxSizeBytes = 5_178_591L,
			nativeScale = 2,
			formatBadge = "ONNX",
		),
		NeuralUpscaleModelEntry(
			id = CUSTOM_MODEL_ID,
			title = "Custom Imported Model (.onnx / .tflite)",
			subtitle = "Import a custom RGB super-resolution .onnx or .tflite model",
			fileName = "custom_upscaler.onnx",
			downloadUrl = null,
			approxSizeBytes = 0L,
			nativeScale = 2,
			formatBadge = "Custom",
		),
	)

	private val _downloadState = MutableStateFlow(NeuralModelDownloadState())
	val downloadState: StateFlow<NeuralModelDownloadState> = _downloadState.asStateFlow()

	private val _inventoryVersion = MutableStateFlow(0)
	val inventoryVersion: StateFlow<Int> = _inventoryVersion.asStateFlow()

	@Volatile
	private var activeCall: Call? = null

	@Volatile
	private var downloadCancelled = false

	private val downloadHttpClient: OkHttpClient by lazy {
		okHttpClientProvider.get().newBuilder()
			.connectTimeout(25L, TimeUnit.SECONDS)
			.readTimeout(60L, TimeUnit.SECONDS)
			.writeTimeout(25L, TimeUnit.SECONDS)
			.followRedirects(true)
			.followSslRedirects(true)
			.build()
	}

	fun findEntry(modelId: String): NeuralUpscaleModelEntry? =
		catalog.firstOrNull { it.id == modelId }

	fun isModelDownloaded(modelId: String): Boolean =
		getModelFile(modelId) != null

	fun getModelFile(modelId: String): File? {
		if (modelId == CUSTOM_MODEL_ID) {
			val customOnnx = File(modelsDir, "custom_upscaler.onnx").takeIf { it.isFile && it.length() > MIN_VALID_MODEL_BYTES }
			val customTflite = File(modelsDir, CUSTOM_TFLITE_FILE_NAME).takeIf { it.isFile && it.length() > MIN_VALID_MODEL_BYTES }
			return listOfNotNull(customOnnx, customTflite).maxByOrNull { it.lastModified() }
		}
		val entry = findEntry(modelId) ?: return null
		val file = File(modelsDir, entry.fileName)
		return file.takeIf { it.isFile && it.length() > MIN_VALID_MODEL_BYTES }
	}

	fun resolveActiveReadyModel(): Pair<NeuralUpscaleModelEntry, File>? {
		val selectedId = settings.neuralUpscaleModelId
		getModelFile(selectedId)?.let { file ->
			findEntry(selectedId)?.let { return it to file }
		}
		for (entry in catalog) {
			getModelFile(entry.id)?.let { return entry to it }
		}
		return null
	}

	suspend fun downloadModel(modelId: String): Result<File> = withContext(Dispatchers.IO) {
		val entry = findEntry(modelId)
			?: return@withContext Result.failure(IllegalArgumentException("Unknown upscaler model: $modelId"))
		val url = entry.downloadUrl
			?: return@withContext Result.failure(IllegalStateException("Custom slot only accepts local file import"))
		if (_downloadState.value.activeModelId != null) {
			return@withContext Result.failure(IllegalStateException("A neural model download is already in progress"))
		}

		val dir = modelsDir
		val availBytes = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
		if (availBytes < entry.approxSizeBytes + 16L * 1024L * 1024L) {
			return@withContext Result.failure(IOException("Not enough free storage to download ${entry.title}"))
		}

		val targetFile = File(dir, entry.fileName)
		val partFile = File(dir, "${entry.fileName}.part")
		val zipPartFile = File(dir, "${entry.fileName}.zip.part")
		partFile.delete()
		zipPartFile.delete()
		downloadCancelled = false

		_downloadState.value = NeuralModelDownloadState(
			activeModelId = modelId,
			downloadedBytes = 0L,
			totalBytes = entry.approxSizeBytes,
			error = null,
		)

		val call = downloadHttpClient.newCall(Request.Builder().url(url).get().build())
		activeCall = call

		runCatching {
			call.execute().use { resp ->
				if (!resp.isSuccessful) {
					error("HTTP ${resp.code} while downloading ${entry.title}")
				}
				val body = resp.body
				val total = body.contentLength().takeIf { it > 0L } ?: entry.approxSizeBytes
				_downloadState.update { it.copy(totalBytes = total) }

				val isZipAsset = url.substringBefore('?').endsWith(".zip", ignoreCase = true)
				val downloadDest = if (isZipAsset) zipPartFile else partFile
				var copied = 0L
				var lastEmit = 0L
				val buffer = ByteArray(32 * 1024)
				body.byteStream().use { input ->
					FileOutputStream(downloadDest).use { output ->
						while (true) {
							if (downloadCancelled) error("Download cancelled")
							val read = input.read(buffer)
							if (read < 0) break
							output.write(buffer, 0, read)
							copied += read
							val now = System.currentTimeMillis()
							if (now - lastEmit >= 120L) {
								lastEmit = now
								_downloadState.update { it.copy(downloadedBytes = copied) }
							}
						}
						output.flush()
					}
				}

				if (isZipAsset) {
					val wantedExt = if (entry.fileName.endsWith(".tflite", ignoreCase = true)) ".tflite" else ".onnx"
					var extracted = 0L
					ZipInputStream(zipPartFile.inputStream().buffered()).use { zis ->
						while (true) {
							val zEntry = zis.nextEntry ?: break
							if (!zEntry.isDirectory && zEntry.name.endsWith(wantedExt, ignoreCase = true)) {
								FileOutputStream(partFile).use { out ->
									extracted = zis.copyTo(out)
									out.flush()
								}
								break
							}
						}
					}
					zipPartFile.delete()
					if (extracted < MIN_VALID_MODEL_BYTES) {
						error("Archive did not contain a valid $wantedExt model")
					}
				} else if (copied < MIN_VALID_MODEL_BYTES) {
					error("Downloaded model file is invalid ($copied bytes)")
				}

				if (targetFile.exists()) targetFile.delete()
				if (!partFile.renameTo(targetFile)) {
					partFile.copyTo(targetFile, overwrite = true)
					partFile.delete()
				}
				settings.neuralUpscaleModelId = modelId
				_inventoryVersion.update { it + 1 }
				_downloadState.value = NeuralModelDownloadState()
				targetFile
			}
		}.onFailure { err ->
			partFile.delete()
			zipPartFile.delete()
			_downloadState.value = NeuralModelDownloadState(
				activeModelId = null,
				error = if (downloadCancelled) null else (err.localizedMessage ?: "Download failed"),
			)
		}.also {
			activeCall = null
		}
	}

	fun cancelDownload() {
		downloadCancelled = true
		activeCall?.cancel()
		modelsDir.listFiles { _, name -> name.endsWith(".part") }?.forEach { it.delete() }
		_downloadState.value = NeuralModelDownloadState()
	}

	suspend fun importModelFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
		val dir = modelsDir
		val stagingFile = File(dir, "custom_import_staging.part")
		stagingFile.delete()
		runCatching {
			val input = context.contentResolver.openInputStream(uri)
				?: error("Unable to open selected model file")
			var copied = 0L
			val header = ByteArray(8)
			var headerLen = 0
			val buffer = ByteArray(32 * 1024)
			input.use { stream ->
				FileOutputStream(stagingFile).use { output ->
					while (true) {
						val read = stream.read(buffer)
						if (read < 0) break
						if (headerLen < header.size) {
							val take = (header.size - headerLen).coerceAtMost(read)
							System.arraycopy(buffer, 0, header, headerLen, take)
							headerLen += take
						}
						output.write(buffer, 0, read)
						copied += read
					}
					output.flush()
				}
			}
			if (copied < MIN_VALID_MODEL_BYTES) {
				error("Selected file is too small to be a valid ONNX or TFLite model")
			}

			val isTfliteMagic = headerLen >= 8 &&
				header[4] == 'T'.code.toByte() &&
				header[5] == 'F'.code.toByte() &&
				header[6] == 'L'.code.toByte() &&
				header[7] == '3'.code.toByte()
			val isTfliteName = uri.toString().contains(".tflite", ignoreCase = true)
			val isTflite = isTfliteMagic || isTfliteName

			val onnxFile = File(dir, "custom_upscaler.onnx")
			val tfliteFile = File(dir, CUSTOM_TFLITE_FILE_NAME)
			val targetFile = if (isTflite) tfliteFile else onnxFile
			val otherFile = if (isTflite) onnxFile else tfliteFile
			otherFile.delete()
			if (targetFile.exists()) targetFile.delete()
			if (!stagingFile.renameTo(targetFile)) {
				stagingFile.copyTo(targetFile, overwrite = true)
				stagingFile.delete()
			}
			settings.neuralUpscaleModelId = CUSTOM_MODEL_ID
			_inventoryVersion.update { it + 1 }
			targetFile
		}.onFailure {
			stagingFile.delete()
		}
	}

	fun deleteModel(modelId: String): Boolean {
		val entry = findEntry(modelId) ?: return false
		val targetFile = File(modelsDir, entry.fileName)
		val partFile = File(modelsDir, "${entry.fileName}.part")
		val altCustom = if (modelId == CUSTOM_MODEL_ID) File(modelsDir, CUSTOM_TFLITE_FILE_NAME) else null
		val deleted = (targetFile.exists() && targetFile.delete()) ||
			(partFile.exists() && partFile.delete()) ||
			(altCustom != null && altCustom.exists() && altCustom.delete())
		if (deleted) {
			if (settings.neuralUpscaleModelId == modelId) {
				val next = catalog.firstOrNull { it.id != modelId && isModelDownloaded(it.id) }
				if (next != null) {
					settings.neuralUpscaleModelId = next.id
				}
			}
			_inventoryVersion.update { it + 1 }
		}
		return deleted
	}

	companion object {
		const val CUSTOM_MODEL_ID = "custom_upscale_onnx"
		private const val CUSTOM_TFLITE_FILE_NAME = "custom_upscaler.tflite"
		private const val MIN_VALID_MODEL_BYTES = 16_384L
	}
}
