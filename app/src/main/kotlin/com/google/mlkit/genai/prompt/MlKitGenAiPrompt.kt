package com.google.mlkit.genai.prompt

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object FeatureStatus {
	const val UNAVAILABLE: Int = 0
	const val DOWNLOADABLE: Int = 1
	const val DOWNLOADING: Int = 2
	const val AVAILABLE: Int = 3
}

sealed class DownloadStatus {
	data object DownloadStarted : DownloadStatus()
	data class DownloadProgress(val totalBytesDownloaded: Long) : DownloadStatus()
	data object DownloadCompleted : DownloadStatus()
	data class DownloadFailed(val e: Throwable) : DownloadStatus()
}

data class PromptPrefix(val text: String)

sealed interface ContentPart

data class SystemInstruction(val text: String) : ContentPart

data class TextPart(val text: String) : ContentPart

class GenerateContentRequestBuilder(
	val parts: List<ContentPart>,
) {
	var promptPrefix: PromptPrefix? = null
	var temperature: Float? = null
	var maxOutputTokens: Int? = null
	var topK: Int? = null
	var seed: Int? = null
	var enableThinking: Boolean = false

	fun build(): GenerateContentRequest = GenerateContentRequest(
		parts = parts,
		promptPrefix = promptPrefix,
		temperature = temperature,
		maxOutputTokens = maxOutputTokens,
		topK = topK,
		seed = seed,
		enableThinking = enableThinking,
	)
}

data class GenerateContentRequest(
	val parts: List<ContentPart>,
	val promptPrefix: PromptPrefix?,
	val temperature: Float?,
	val maxOutputTokens: Int?,
	val topK: Int?,
	val seed: Int?,
	val enableThinking: Boolean,
) {
	val combinedPromptText: String
		get() = buildString {
			promptPrefix?.text?.takeIf { it.isNotBlank() }?.let {
				appendLine(it)
			}
			for (part in parts) {
				when (part) {
					is SystemInstruction -> appendLine(part.text)
					is TextPart -> appendLine(part.text)
				}
			}
		}.trim()
}

fun generateContentRequest(
	vararg parts: ContentPart,
	init: GenerateContentRequestBuilder.() -> Unit = {},
): GenerateContentRequest {
	val builder = GenerateContentRequestBuilder(parts.toList())
	builder.init()
	return builder.build()
}

data class Candidate(
	val text: String?,
)

data class GenerateContentResponse(
	val candidates: List<Candidate>,
)

interface GenerativeModel {
	suspend fun checkStatus(): Int
	fun download(): Flow<DownloadStatus>
	fun generateContentStream(request: GenerateContentRequest): Flow<String>
	suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse
	fun close()
}

object Generation {
	@Volatile
	var customModelOverride: GenerativeModel? = null

	fun getClient(): GenerativeModel {
		return DelegatingAiCoreGenerativeModel()
	}
}

private class DelegatingAiCoreGenerativeModel : GenerativeModel {
	@Volatile
	private var currentStatus: Int = FeatureStatus.UNAVAILABLE

	override suspend fun checkStatus(): Int {
		Generation.customModelOverride?.let { return it.checkStatus() }
		return currentStatus
	}

	override fun download(): Flow<DownloadStatus> {
		Generation.customModelOverride?.let { return it.download() }
		return flow {
			emit(DownloadStatus.DownloadStarted)
			delay(10L)
			emit(DownloadStatus.DownloadProgress(8_388_608L))
			delay(10L)
			currentStatus = FeatureStatus.AVAILABLE
			emit(DownloadStatus.DownloadCompleted)
		}
	}

	override fun generateContentStream(request: GenerateContentRequest): Flow<String> {
		Generation.customModelOverride?.let { return it.generateContentStream(request) }
		return flow {
			val response = generateContent(request)
			val text = response.candidates.firstOrNull()?.text.orEmpty()
			if (text.isNotEmpty()) {
				val chunks = text.chunked(24)
				for (chunk in chunks) {
					emit(chunk)
				}
			}
		}
	}

	override suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse {
		Generation.customModelOverride?.let { return it.generateContent(request) }
		return GenerateContentResponse(candidates = emptyList())
	}

	override fun close() {
		Generation.customModelOverride?.close()
	}
}
