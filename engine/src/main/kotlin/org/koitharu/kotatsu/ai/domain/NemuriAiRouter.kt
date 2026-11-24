package org.koitharu.kotatsu.ai.domain

import org.koitharu.kotatsu.ai.data.AiSecretStore
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NemuriAiRouter @Inject constructor(
	private val settings: AppSettings,
	private val secretStore: AiSecretStore,
	private val geminiNanoClient: GeminiNanoClient,
	private val remoteAiClient: RemoteAiClient,
) {

	fun currentMode(): AiMode = AiMode.fromKey(settings.aiMode)

	fun isAiEnabled(): Boolean = currentMode() != AiMode.OFF

	suspend fun checkNanoAvailability(): NanoAvailability = geminiNanoClient.checkAvailability()

	suspend fun downloadNanoModel(onProgress: (Long) -> Unit = {}): Result<Unit> =
		geminiNanoClient.triggerDownload(onProgress)

	suspend fun generate(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
	): Result<AiGenerationResult> = when (currentMode()) {
		AiMode.OFF -> Result.failure(IllegalStateException("AI features are turned off"))

		AiMode.GEMINI_NANO -> geminiNanoClient.generate("$systemPrompt\n\n$userPrompt").map { text ->
			AiGenerationResult(text = text, engineUsed = "Gemini Nano (On-Device)", isAiGenerated = true)
		}

		AiMode.REMOTE_OPENAI -> remoteAiClient.generateOpenAiCompatible(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = remoteEngineLabel(), isAiGenerated = true)
		}

		AiMode.GEMINI_API -> remoteAiClient.generateGeminiRest(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = "Gemini (${settings.aiGeminiModel})", isAiGenerated = true)
		}

		AiMode.AUTO -> generateAuto(systemPrompt, userPrompt, maxTokens)
	}

	private suspend fun generateAuto(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int,
	): Result<AiGenerationResult> {
		var lastError: Throwable? = null

		// 1. Primary: On-device Gemini Nano via AICore when immediately available
		if (geminiNanoClient.checkAvailability() == NanoAvailability.AVAILABLE) {
			val nanoRes = geminiNanoClient.generate("$systemPrompt\n\n$userPrompt")
			if (nanoRes.isSuccess) {
				return Result.success(
					AiGenerationResult(
						text = nanoRes.getOrThrow(),
						engineUsed = "Gemini Nano (On-Device)",
						isAiGenerated = true,
					),
				)
			}
			lastError = nanoRes.exceptionOrNull()
		}

		// 2. Configured Gemini Cloud REST API key (encrypted)
		if (secretStore.hasGeminiApiKey()) {
			val geminiRes = remoteAiClient.generateGeminiRest(systemPrompt, userPrompt, maxTokens)
			if (geminiRes.isSuccess) {
				return Result.success(
					AiGenerationResult(
						text = geminiRes.getOrThrow(),
						engineUsed = "Gemini (${settings.aiGeminiModel})",
						isAiGenerated = true,
					),
				)
			}
			lastError = geminiRes.exceptionOrNull()
		}

		// 3. Configured Ollama / OpenAI-compatible endpoint
		if (hasRemoteConfigured()) {
			val remoteRes = remoteAiClient.generateOpenAiCompatible(systemPrompt, userPrompt, maxTokens)
			if (remoteRes.isSuccess) {
				return Result.success(
					AiGenerationResult(
						text = remoteRes.getOrThrow(),
						engineUsed = remoteEngineLabel(),
						isAiGenerated = true,
					),
				)
			}
			lastError = remoteRes.exceptionOrNull()
		}

		return Result.failure(lastError ?: IllegalStateException("No AI backend available in Auto mode"))
	}

	suspend fun testActiveBackend(): AiConnectionTestResult {
		val mode = currentMode()
		if (mode == AiMode.OFF) {
			return AiConnectionTestResult(
				ok = false,
				engineName = "Off",
				message = "AI Mode is Off. Select Auto or a specific backend first.",
				latencyMs = 0L,
			)
		}
		val start = System.currentTimeMillis()
		val res = generate(
			systemPrompt = "Respond strictly with the word OK.",
			userPrompt = "Health check",
			maxTokens = 16,
		)
		val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
		return res.fold(
			onSuccess = { out ->
				AiConnectionTestResult(
					ok = true,
					engineName = out.engineUsed,
					message = "Connected (${elapsed}ms) • Reply: ${out.text.take(60)}",
					latencyMs = elapsed,
				)
			},
			onFailure = { err ->
				val nanoNote = if (mode == AiMode.AUTO || mode == AiMode.GEMINI_NANO) {
					val st = checkNanoAvailability()
					" (Gemini Nano: ${st.label})"
				} else {
					""
				}
				AiConnectionTestResult(
					ok = false,
					engineName = mode.label,
					message = "${err.message ?: "Connection failed"}$nanoNote",
					latencyMs = elapsed,
				)
			},
		)
	}

	private fun hasRemoteConfigured(): Boolean {
		val url = settings.aiRemoteUrl.trim()
		val model = settings.aiRemoteModel.trim()
		return url.isNotBlank() && model.isNotBlank()
	}

	private fun remoteEngineLabel(): String {
		val url = settings.aiRemoteUrl.lowercase()
		val model = settings.aiRemoteModel.ifBlank { "remote" }
		return when {
			":11434" in url || "ollama" in url -> "Ollama ($model)"
			":1234" in url -> "LM Studio ($model)"
			else -> "OpenAI API ($model)"
		}
	}
}
