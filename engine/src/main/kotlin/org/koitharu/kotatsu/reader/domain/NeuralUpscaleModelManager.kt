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
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Manages lightweight on-demand ONNX super-resolution models stored in [Context.filesDir]
 * so zero model weights are bundled inside the APK.
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
			id = "waifu2x_upconv7_2x",
			title = "Waifu2x UpConv-7 Anime 2x",
			subtitle = "Fast 2x manga line restoration + denoise • ~2.1 MB",
			fileName = "waifu2x_upconv7_anime_noise1_2x.onnx",
			downloadUrl = "https://huggingface.co/lookx2/waifu2x_upconv7/resolve/main/waifu2x_upconv7_anime_noise1_2x.onnx",
			approxSizeBytes = 2_212_306L,
			nativeScale = 2,
		),
		NeuralUpscaleModelEntry(
			id = "realesr_animevideov3",
			title = "Real-ESRGAN AnimeVideo v3",
			subtitle = "Compact SRVGGNet restoration (clean 2x downsample) • ~2.4 MB",
			fileName = "realesr_animevideov3_fp32.onnx",
			downloadUrl = "https://huggingface.co/skillsafe-ai/realesr-animevideov3/resolve/main/model.onnx",
			approxSizeBytes = 2_492_908L,
			nativeScale = 4,
		),
		NeuralUpscaleModelEntry(
			id = "waifu2x_cunet_art_2x",
			title = "Waifu2x CUNet Art 2x",
			subtitle = "High-detail illustration & screentone cleanup • ~4.9 MB",
			fileName = "waifu2x_cunet_art_noise1_scale2x.onnx",
			downloadUrl = "https://huggingface.co/deepghs/waifu2x_onnx/resolve/main/20250502/onnx_models/cunet/art/noise1_scale2x.onnx",
			approxSizeBytes = 5_178_591L,
			nativeScale = 2,
		),
		NeuralUpscaleModelEntry(
			id = CUSTOM_MODEL_ID,
			title = "Custom Imported ONNX (.onnx)",
			subtitle = "Import any NCHW RGB [0..1] super-resolution .onnx model",
			fileName = "custom_upscaler.onnx",
			downloadUrl = null,
			approxSizeBytes = 0L,
			nativeScale = 2,
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
		val entry = findEntry(modelId) ?: return null
		val file = File(modelsDir, entry.fileName)
		return file.takeIf { it.isFile && it.length() > MIN_VALID_ONNX_BYTES }
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
		partFile.delete()
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

				var copied = 0L
				var lastEmit = 0L
				val buffer = ByteArray(32 * 1024)
				body.byteStream().use { input ->
					FileOutputStream(partFile).use { output ->
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
				if (copied < MIN_VALID_ONNX_BYTES) {
					error("Downloaded ONNX file is invalid ($copied bytes)")
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
		val customEntry = findEntry(CUSTOM_MODEL_ID)!!
		val dir = modelsDir
		val targetFile = File(dir, customEntry.fileName)
		val partFile = File(dir, "${customEntry.fileName}.part")
		partFile.delete()
		runCatching {
			val input = context.contentResolver.openInputStream(uri)
				?: error("Unable to open selected ONNX file")
			var copied = 0L
			val buffer = ByteArray(32 * 1024)
			input.use { stream ->
				FileOutputStream(partFile).use { output ->
					while (true) {
						val read = stream.read(buffer)
						if (read < 0) break
						output.write(buffer, 0, read)
						copied += read
					}
					output.flush()
				}
			}
			if (copied < MIN_VALID_ONNX_BYTES) {
				error("Selected file is too small to be a valid ONNX model")
			}
			if (targetFile.exists()) targetFile.delete()
			if (!partFile.renameTo(targetFile)) {
				partFile.copyTo(targetFile, overwrite = true)
				partFile.delete()
			}
			settings.neuralUpscaleModelId = CUSTOM_MODEL_ID
			_inventoryVersion.update { it + 1 }
			targetFile
		}.onFailure {
			partFile.delete()
		}
	}

	fun deleteModel(modelId: String): Boolean {
		val entry = findEntry(modelId) ?: return false
		val targetFile = File(modelsDir, entry.fileName)
		val partFile = File(modelsDir, "${entry.fileName}.part")
		val deleted = (targetFile.exists() && targetFile.delete()) || (partFile.exists() && partFile.delete())
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
		private const val MIN_VALID_ONNX_BYTES = 16_384L
	}
}
