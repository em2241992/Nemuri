package org.koitharu.kotatsu.ai.domain

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoClient @Inject constructor() {

	suspend fun checkAvailability(): NanoAvailability = withContext(Dispatchers.IO) {
		val model = runCatching { Generation.getClient() }.getOrNull()
			?: return@withContext NanoAvailability.UNAVAILABLE
		try {
			when (model.checkStatus()) {
				FeatureStatus.AVAILABLE -> NanoAvailability.AVAILABLE
				FeatureStatus.DOWNLOADABLE -> NanoAvailability.DOWNLOADABLE
				FeatureStatus.DOWNLOADING -> NanoAvailability.DOWNLOADING
				else -> NanoAvailability.UNAVAILABLE
			}
		} catch (_: Throwable) {
			NanoAvailability.UNAVAILABLE
		} finally {
			runCatching { model.close() }
		}
	}

	suspend fun triggerDownload(onProgress: (Long) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
		val model = runCatching { Generation.getClient() }.getOrElse {
			return@withContext Result.failure(it)
		}
		try {
			var failedError: Throwable? = null
			model.download().collect { status ->
				when (status) {
					is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded)
					is DownloadStatus.DownloadFailed -> failedError = status.e
					else -> Unit
				}
			}
			val err = failedError
			if (err != null) Result.failure(err) else Result.success(Unit)
		} catch (t: Throwable) {
			Result.failure(t)
		} finally {
			runCatching { model.close() }
		}
	}

	suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
		val model = runCatching { Generation.getClient() }.getOrElse {
			return@withContext Result.failure(it)
		}
		try {
			val status = model.checkStatus()
			if (status != FeatureStatus.AVAILABLE) {
				return@withContext Result.failure(
					IllegalStateException("Gemini Nano is ${if (status == FeatureStatus.DOWNLOADABLE) "not downloaded yet" else "unavailable"}"),
				)
			}
			val response = model.generateContent(prompt)
			val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
			if (text.isEmpty()) {
				Result.failure(IllegalStateException("Gemini Nano returned an empty response"))
			} else {
				Result.success(text)
			}
		} catch (t: Throwable) {
			Result.failure(t)
		} finally {
			runCatching { model.close() }
		}
	}
}
