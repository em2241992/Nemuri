package org.koitharu.kotatsu.ai.domain

import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.ai.data.AiSecretStore
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NemuriAiRouter @Inject constructor(
	private val settings: AppSettings,
	private val secretStore: AiSecretStore,
	private val geminiNanoClient: GeminiNanoClient,
	private val localLlmClient: LocalLlmClient,
	private val remoteAiClient: RemoteAiClient,
) {

	fun hasReadyLocalModel(): Boolean = localLlmClient.hasReadyModel()

	/**
	 * Verifies that if LOCAL_MODEL is selected, a real `.litertlm` file exists on disk.
	 * If the user deleted the model file, automatically clears stale mode and falls back to AUTO.
	 */
	fun sanitizeDeletedLocalModel(): AiMode {
		val raw = AiMode.fromKey(settings.aiMode)
		if (raw == AiMode.LOCAL_MODEL && !localLlmClient.hasReadyModel()) {
			settings.aiMode = AiMode.AUTO.key
			return AiMode.AUTO
		}
		return raw
	}

	fun currentMode(): AiMode = sanitizeDeletedLocalModel()

	fun isAiEnabled(): Boolean = currentMode() != AiMode.OFF

	suspend fun resolveActiveEngineDisplayLabel(): String = when (currentMode()) {
		AiMode.OFF -> "Off"
		AiMode.GEMINI_NANO -> {
			val diag = safeCheckNanoDiagnostics()
			if (diag.availability == NanoAvailability.AVAILABLE) {
				if (diag.supportsOpenPrompt) "Gemini Nano (On-Device)" else "Gemini Nano Summarizer"
			} else {
				"Gemini Nano (${diag.availability.name.lowercase().replaceFirstChar { it.uppercase() }})"
			}
		}
		AiMode.LOCAL_MODEL -> localLlmClient.activeModelLabel()
		AiMode.REMOTE_OPENAI -> remoteEngineLabel()
		AiMode.GEMINI_API -> "Gemini (${settings.aiGeminiModel})"
		AiMode.AUTO -> {
			val diag = safeCheckNanoDiagnostics()
			when {
				diag.availability == NanoAvailability.AVAILABLE && diag.supportsOpenPrompt ->
					"Gemini Nano (On-Device)"
				diag.availability == NanoAvailability.AVAILABLE && diag.supportsSummarization ->
					"Gemini Nano Summarizer"
				secretStore.hasGeminiApiKey() ->
					"Gemini (${settings.aiGeminiModel})"
				hasRemoteConfigured() ->
					remoteEngineLabel()
				localLlmClient.hasReadyModel() ->
					localLlmClient.activeModelLabel()
				else -> "Auto (Reading Trail)"
			}
		}
	}

	suspend fun checkNanoAvailability(): NanoAvailability = safeCheckNanoAvailability()

	suspend fun checkNanoDiagnostics(): NanoDiagnostics = safeCheckNanoDiagnostics()

	suspend fun downloadNanoModel(onProgress: (Long) -> Unit = {}): Result<Unit> =
		safeDownloadNanoModel(onProgress)

	suspend fun generate(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
	): Result<AiGenerationResult> = when (currentMode()) {
		AiMode.OFF -> Result.failure(IllegalStateException("AI features are turned off"))

		AiMode.GEMINI_NANO -> safeNanoGenerate("$systemPrompt\n\n$userPrompt").map { text ->
			AiGenerationResult(text = text, engineUsed = "Gemini Nano (On-Device)", isAiGenerated = true)
		}

		AiMode.LOCAL_MODEL -> localLlmClient.generate(systemPrompt, userPrompt, maxTokens).map { (text, label) ->
			AiGenerationResult(text = text, engineUsed = label, isAiGenerated = true)
		}

		AiMode.REMOTE_OPENAI -> remoteAiClient.generateOpenAiCompatible(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = remoteEngineLabel(), isAiGenerated = true)
		}

		AiMode.GEMINI_API -> remoteAiClient.generateGeminiRest(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = "Gemini (${settings.aiGeminiModel})", isAiGenerated = true)
		}

		AiMode.AUTO -> generateAuto(systemPrompt, userPrompt, maxTokens)
	}

	/**
	 * Specialized recap routing supporting `genai-prompt` first and falling back to
	 * task-specific ML Kit `genai-summarization` on Samsung Galaxy S25 (`SM8750`).
	 */
	suspend fun summarizeRecap(
		systemPrompt: String,
		userPrompt: String,
		articleText: String,
		maxTokens: Int = 280,
		recapDepth: RecapDepth = RecapDepth.DETAILED,
	): Result<AiGenerationResult> = when (currentMode()) {
		AiMode.OFF -> Result.failure(IllegalStateException("AI features are turned off"))

		AiMode.GEMINI_NANO -> safeNanoSummarize(
			articleText = articleText,
			fallbackPrompt = "$systemPrompt\n\n$userPrompt",
			recapDepth = recapDepth,
		).map { (text, label) ->
			AiGenerationResult(text = text, engineUsed = label, isAiGenerated = true)
		}

		AiMode.LOCAL_MODEL -> localLlmClient.generate(systemPrompt, userPrompt, maxTokens).map { (text, label) ->
			AiGenerationResult(text = text, engineUsed = label, isAiGenerated = true)
		}

		AiMode.REMOTE_OPENAI -> remoteAiClient.generateOpenAiCompatible(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = remoteEngineLabel(), isAiGenerated = true)
		}

		AiMode.GEMINI_API -> remoteAiClient.generateGeminiRest(systemPrompt, userPrompt, maxTokens).map { text ->
			AiGenerationResult(text = text, engineUsed = "Gemini (${settings.aiGeminiModel})", isAiGenerated = true)
		}

		AiMode.AUTO -> summarizeAuto(systemPrompt, userPrompt, articleText, maxTokens, recapDepth)
	}

	private suspend fun generateAuto(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int,
	): Result<AiGenerationResult> {
		var lastError: Throwable? = null

		// 1. Open-ended Gemini Nano via AICore when provisioned
		val diag = safeCheckNanoDiagnostics()
		if (diag.availability == NanoAvailability.AVAILABLE && diag.supportsOpenPrompt) {
			val nanoRes = safeNanoGenerate("$systemPrompt\n\n$userPrompt")
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

		return fallbackAfterNano(systemPrompt, userPrompt, maxTokens, lastError)
	}

	private suspend fun summarizeAuto(
		systemPrompt: String,
		userPrompt: String,
		articleText: String,
		maxTokens: Int,
		recapDepth: RecapDepth,
	): Result<AiGenerationResult> {
		var lastError: Throwable? = null
		var attemptedLocal = false

		// Hybrid routing in Auto mode:
		// - Detailed recaps prefer loaded local GPU model (e.g. Gemma 4 E2B .litertlm) when available,
		//   falling back to onboard Gemini Nano (AICore).
		// - Brief recaps prefer instant zero-RAM Gemini Nano (AICore) first.
		if (recapDepth == RecapDepth.DETAILED && localLlmClient.hasReadyModel()) {
			attemptedLocal = true
			val localRes = localLlmClient.generate(
				systemPrompt = systemPrompt,
				userPrompt = userPrompt,
				maxTokens = maxTokens,
				preferGpuOverride = true,
			)
			if (localRes.isSuccess) {
				val (text, label) = localRes.getOrThrow()
				return Result.success(
					AiGenerationResult(
						text = text,
						engineUsed = label,
						isAiGenerated = true,
					),
				)
			}
			lastError = localRes.exceptionOrNull()
		}

		// On-device Gemini Nano (Prompt -> Summarizer fallback for S25 / SM8750)
		val diag = safeCheckNanoDiagnostics()
		if (diag.availability == NanoAvailability.AVAILABLE && (diag.supportsOpenPrompt || diag.supportsSummarization)) {
			val nanoRes = safeNanoSummarize(
				articleText = articleText,
				fallbackPrompt = "$systemPrompt\n\n$userPrompt",
				recapDepth = recapDepth,
			)
			if (nanoRes.isSuccess) {
				val (text, label) = nanoRes.getOrThrow()
				return Result.success(
					AiGenerationResult(
						text = text,
						engineUsed = label,
						isAiGenerated = true,
					),
				)
			}
			lastError = nanoRes.exceptionOrNull()
		}

		return fallbackAfterNano(
			systemPrompt = systemPrompt,
			userPrompt = userPrompt,
			maxTokens = maxTokens,
			initialError = lastError,
			skipLocal = attemptedLocal,
		)
	}

	private suspend fun fallbackAfterNano(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int,
		initialError: Throwable?,
		skipLocal: Boolean = false,
	): Result<AiGenerationResult> {
		var lastError: Throwable? = initialError

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

		// 3. Configured Ollama / OpenAI-compatible endpoint (only when explicitly enabled/configured)
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

		// 4. Downloaded On-Device Local LLM fallback (LiteRT-LM GPU -> CPU)
		if (!skipLocal && localLlmClient.hasReadyModel()) {
			val localRes = localLlmClient.generate(
				systemPrompt = systemPrompt,
				userPrompt = userPrompt,
				maxTokens = maxTokens,
				preferGpuOverride = true,
			)
			if (localRes.isSuccess) {
				val (text, label) = localRes.getOrThrow()
				return Result.success(
					AiGenerationResult(
						text = text,
						engineUsed = label,
						isAiGenerated = true,
					),
				)
			}
			lastError = localRes.exceptionOrNull()
		}

		return Result.failure(lastError ?: IllegalStateException("No active AI backend ready in Auto mode"))
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
		val nanoDiag = if (mode == AiMode.AUTO || mode == AiMode.GEMINI_NANO) checkNanoDiagnostics() else null
		val useSummarizerHealthCheck = nanoDiag != null &&
			nanoDiag.availability == NanoAvailability.AVAILABLE &&
			!nanoDiag.supportsOpenPrompt &&
			nanoDiag.supportsSummarization

		val res = if (useSummarizerHealthCheck || mode == AiMode.GEMINI_NANO) {
			summarizeRecap(
				systemPrompt = "Respond strictly with the word OK.",
				userPrompt = "Health check",
				articleText = HEALTH_CHECK_ARTICLE,
				maxTokens = 24,
				recapDepth = RecapDepth.BRIEF,
			)
		} else {
			generate(
				systemPrompt = "Respond strictly with the word OK.",
				userPrompt = "Health check",
				maxTokens = 16,
			)
		}
		val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
		return res.fold(
			onSuccess = { out ->
				val snippet = out.text.replace('\n', ' ').trim().take(60)
				AiConnectionTestResult(
					ok = true,
					engineName = out.engineUsed,
					message = "✓ Connected — ${out.engineUsed} (${elapsed}ms) • Reply: $snippet",
					latencyMs = elapsed,
				)
			},
			onFailure = { err ->
				val baseMsg = err.message?.trim().orEmpty().ifBlank { "Connection failed" }
				val nanoNote = if ((mode == AiMode.AUTO || mode == AiMode.GEMINI_NANO) && nanoDiag != null) {
					val diagMsg = nanoDiag.detailMessage.trim()
					if (diagMsg.isNotBlank() &&
						!baseMsg.contains(diagMsg, ignoreCase = true) &&
						!diagMsg.contains(baseMsg, ignoreCase = true)
					) {
						" (Gemini Nano: $diagMsg)"
					} else {
						""
					}
				} else {
					""
				}
				AiConnectionTestResult(
					ok = false,
					engineName = mode.label,
					message = "$baseMsg$nanoNote",
					latencyMs = elapsed,
				)
			},
		)
	}

	private suspend fun safeCheckNanoDiagnostics(): NanoDiagnostics = runCatching {
		geminiNanoClient.checkDiagnostics()
	}.getOrElse { err ->
		if (err is CancellationException) throw err
		NanoDiagnostics(
			availability = NanoAvailability.UNAVAILABLE,
			detailMessage = err.message?.trim().orEmpty().ifBlank { "AICore unavailable on this device" },
			supportsOpenPrompt = false,
			supportsSummarization = false,
		)
	}

	private suspend fun safeCheckNanoAvailability(): NanoAvailability = runCatching {
		geminiNanoClient.checkAvailability()
	}.getOrElse { err ->
		if (err is CancellationException) throw err
		NanoAvailability.UNAVAILABLE
	}

	private suspend fun safeDownloadNanoModel(onProgress: (Long) -> Unit): Result<Unit> = runCatching {
		geminiNanoClient.triggerDownload(onProgress)
	}.getOrElse { err ->
		if (err is CancellationException) throw err
		Result.failure(err)
	}

	private suspend fun safeNanoGenerate(prompt: String): Result<String> = runCatching {
		geminiNanoClient.generate(prompt)
	}.getOrElse { err ->
		if (err is CancellationException) throw err
		Result.failure(err)
	}

	private suspend fun safeNanoSummarize(
		articleText: String,
		fallbackPrompt: String,
		recapDepth: RecapDepth,
	): Result<Pair<String, String>> = runCatching {
		geminiNanoClient.summarize(
			articleText = articleText,
			fallbackPrompt = fallbackPrompt,
			recapDepth = recapDepth,
		)
	}.getOrElse { err ->
		if (err is CancellationException) throw err
		Result.failure(err)
	}

	private fun hasRemoteConfigured(): Boolean {
		val url = settings.aiRemoteUrl.trim()
		val model = settings.aiRemoteModel.trim()
		if (url.isBlank() || model.isBlank()) return false
		return settings.isAiRemoteConfiguredByUser ||
			secretStore.hasOpenAiApiKey() ||
			!url.equals(DEFAULT_LOCAL_OLLAMA_URL, ignoreCase = true)
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

	private companion object {
		const val DEFAULT_LOCAL_OLLAMA_URL = "http://127.0.0.1:11434/v1"
		const val HEALTH_CHECK_ARTICLE =
			"High above the sunken kingdom of Veridia, a young cartographer named Ren and his clockwork hawk chart the drifting sky islands left behind after the Great Sundering. Across the opening arc, Ren discovers an ancient brass astrolabe hidden inside the library of Port Halcyon, drawing the attention of the Iron Fleet and their ruthless commander, Lady Vaelen. Joined by Mira, an exiled swordswoman guarding a sealed relic, and Toren, a runaway alchemist, the small crew repairs a battered wind-skiff and crosses the Tempest Wall. By Chapter 42, the companions reach the Spire Harbor citadel, where an old guild master recognizes the astrolabe's star-map."
	}
}
