package org.koitharu.kotatsu.ai.data

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
import org.koitharu.kotatsu.ai.domain.LocalLlmCatalogEntry
import org.koitharu.kotatsu.ai.domain.LocalModelDownloadState
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
 * Manages user-downloaded and imported on-device `.litertlm` models stored in [Context.filesDir]
 * so weights are never bundled in the APK and are never silently evicted by OS cache cleanup.
 */
@Singleton
class LocalModelManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient private val okHttpClientProvider: Provider<OkHttpClient>,
	private val settings: AppSettings,
) {

	private val modelsDir: File
		get() = File(context.filesDir, "ai_models").apply { mkdirs() }

	val catalog: List<LocalLlmCatalogEntry> = listOf(
		LocalLlmCatalogEntry(
			id = "smollm2_360m_it",
			title = "SmolLM2 360M Instruct",
			subtitle = "Ultra-light fallback • ~356 MB • 6 GB+ RAM",
			fileName = "SmolLM2_360M_instruct.litertlm",
			downloadUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/main/SmolLM2_360M_instruct.litertlm",
			approxSizeBytes = 373_719_040L,
			minRamMb = 900,
		),
		LocalLlmCatalogEntry(
			id = "qwen2_5_coder_1_5b_int4",
			title = "Qwen 2.5 1.5B INT4",
			subtitle = "Balanced 4-bit • ~1.04 GB • 8 GB+ RAM",
			fileName = "Qwen2.5-Coder-1.5B-Instruct_int4.litertlm",
			downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-Coder-1.5B-Instruct/resolve/main/Qwen2.5-Coder-1.5B-Instruct_int4.litertlm",
			approxSizeBytes = 1_118_076_928L,
			minRamMb = 1400,
		),
		LocalLlmCatalogEntry(
			id = "lfm3_1_7b_int4",
			title = "Liquid LFM3 1.7B INT4",
			subtitle = "High-quality recap synthesis • ~1.34 GB • 8 GB+ RAM",
			fileName = "LFM3-1.7B_mixed_int4_ekv2048.litertlm",
			downloadUrl = "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT/resolve/main/LFM3-1.7B_mixed_int4_ekv2048.litertlm",
			approxSizeBytes = 1_441_620_928L,
			minRamMb = 1600,
		),
		LocalLlmCatalogEntry(
			id = "qwen2_5_1_5b_it_q8",
			title = "Qwen 2.5 1.5B Instruct Q8",
			subtitle = "High precision 8-bit • ~1.49 GB • 8–12 GB+ RAM",
			fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
			downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
			approxSizeBytes = 1_597_922_856L,
			minRamMb = 1800,
		),
		LocalLlmCatalogEntry(
			id = CUSTOM_MODEL_ID,
			title = "Custom Imported Model (.litertlm)",
			subtitle = "Import a user-provided .litertlm model from device storage",
			fileName = "custom_user_model.litertlm",
			downloadUrl = null,
			approxSizeBytes = 0L,
			minRamMb = 1100,
		),
	)

	private val _downloadState = MutableStateFlow(LocalModelDownloadState())
	val downloadState: StateFlow<LocalModelDownloadState> = _downloadState.asStateFlow()

	private val _inventoryVersion = MutableStateFlow(0)
	val inventoryVersion: StateFlow<Int> = _inventoryVersion.asStateFlow()

	@Volatile
	private var activeCall: Call? = null

	@Volatile
	private var downloadCancelled = false

	private val downloadHttpClient: OkHttpClient by lazy {
		okHttpClientProvider.get().newBuilder()
			.connectTimeout(30L, TimeUnit.SECONDS)
			.readTimeout(120L, TimeUnit.SECONDS)
			.writeTimeout(30L, TimeUnit.SECONDS)
			.followRedirects(true)
			.followSslRedirects(true)
			.build()
	}

	fun findEntry(modelId: String): LocalLlmCatalogEntry? =
		catalog.firstOrNull { it.id == modelId }

	fun isModelDownloaded(modelId: String): Boolean =
		getModelFile(modelId) != null

	fun getModelFile(modelId: String): File? {
		val entry = findEntry(modelId) ?: return null
		val file = File(modelsDir, entry.fileName)
		return file.takeIf { it.isFile && it.length() > MIN_VALID_MODEL_BYTES }
	}

	fun getDownloadedSizeBytes(modelId: String): Long =
		getModelFile(modelId)?.length() ?: 0L

	fun totalDiskUsageBytes(): Long =
		modelsDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

	fun resolveActiveReadyModel(): Pair<LocalLlmCatalogEntry, File>? {
		val selectedId = settings.aiLocalModelId
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
			?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))
		val url = entry.downloadUrl
			?: return@withContext Result.failure(IllegalStateException("This slot only supports local file import"))
		if (_downloadState.value.activeModelId != null) {
			return@withContext Result.failure(IllegalStateException("Another model download is already in progress"))
		}

		val dir = modelsDir
		val requiredBytes = entry.approxSizeBytes + 64L * 1024L * 1024L
		val availBytes = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
		if (availBytes < requiredBytes) {
			return@withContext Result.failure(
				IOException("Not enough free storage (${availBytes / (1024 * 1024)} MB free, need ~${requiredBytes / (1024 * 1024)} MB)"),
			)
		}

		val targetFile = File(dir, entry.fileName)
		val partFile = File(dir, "${entry.fileName}.part")
		partFile.delete()
		downloadCancelled = false

		_downloadState.value = LocalModelDownloadState(
			activeModelId = modelId,
			downloadedBytes = 0L,
			totalBytes = entry.approxSizeBytes,
			error = null,
		)

		val call = downloadHttpClient.newCall(
			Request.Builder().url(url).get().build(),
		)
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
				var lastEmitAt = 0L
				val buffer = ByteArray(64 * 1024)
				body.byteStream().use { input ->
					FileOutputStream(partFile).use { output ->
						while (true) {
							if (downloadCancelled) {
								error("Download cancelled")
							}
							val read = input.read(buffer)
							if (read < 0) break
							output.write(buffer, 0, read)
							copied += read
							val now = System.currentTimeMillis()
							if (now - lastEmitAt >= 150L) {
								lastEmitAt = now
								_downloadState.update { it.copy(downloadedBytes = copied) }
							}
						}
						output.flush()
					}
				}
				if (copied < MIN_VALID_MODEL_BYTES) {
					error("Downloaded file is unexpectedly small ($copied bytes)")
				}
				if (targetFile.exists()) {
					targetFile.delete()
				}
				if (!partFile.renameTo(targetFile)) {
					partFile.copyTo(targetFile, overwrite = true)
					partFile.delete()
				}
				settings.aiLocalModelId = modelId
				_inventoryVersion.update { it + 1 }
				_downloadState.value = LocalModelDownloadState()
				targetFile
			}
		}.onFailure { err ->
			partFile.delete()
			_downloadState.value = LocalModelDownloadState(
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
		_downloadState.value = LocalModelDownloadState()
	}

	suspend fun importModelFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
		val customEntry = findEntry(CUSTOM_MODEL_ID)!!
		val dir = modelsDir
		val targetFile = File(dir, customEntry.fileName)
		val partFile = File(dir, "${customEntry.fileName}.part")
		partFile.delete()
		runCatching {
			val input = context.contentResolver.openInputStream(uri)
				?: error("Unable to open selected file")
			var copied = 0L
			val buffer = ByteArray(64 * 1024)
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
			if (copied < MIN_VALID_MODEL_BYTES) {
				error("Selected file is smaller than 1 MB and is not a valid LLM model")
			}
			if (targetFile.exists()) targetFile.delete()
			if (!partFile.renameTo(targetFile)) {
				partFile.copyTo(targetFile, overwrite = true)
				partFile.delete()
			}
			settings.aiLocalModelId = CUSTOM_MODEL_ID
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
			if (settings.aiLocalModelId == modelId) {
				val replacement = catalog.firstOrNull { it.id != modelId && isModelDownloaded(it.id) }
				if (replacement != null) {
					settings.aiLocalModelId = replacement.id
				}
			}
			_inventoryVersion.update { it + 1 }
		}
		return deleted
	}

	companion object {
		const val CUSTOM_MODEL_ID = "custom_imported"
		private const val MIN_VALID_MODEL_BYTES = 1_048_576L
	}
}
