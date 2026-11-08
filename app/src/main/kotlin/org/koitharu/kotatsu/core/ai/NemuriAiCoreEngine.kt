package org.koitharu.kotatsu.core.ai

import com.google.mlkit.genai.prompt.DownloadStatus
import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NemuriAiStatus {
	data object Unchecked : NemuriAiStatus
	data object Available : NemuriAiStatus
	data class Downloading(val bytesDownloaded: Long) : NemuriAiStatus
	data class Unavailable(val reason: String) : NemuriAiStatus
}

@Singleton
class NemuriAiCoreEngine @Inject constructor() {

	private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	// Serialize NPU inference requests so concurrent UI actions never collide in AICore
	private val npuSemaphore = Semaphore(permits = 1)

	private val generativeModel: GenerativeModel by lazy {
		Generation.getClient()
	}

	private val _status = MutableStateFlow<NemuriAiStatus>(NemuriAiStatus.Unchecked)
	val status: StateFlow<NemuriAiStatus> = _status.asStateFlow()

	companion object {
		// Cached in Hexagon NPU KV-cache across requests via PromptPrefix
		val RECAP_PROMPT_PREFIX = PromptPrefix(
			"""
			You are Nemuri's local on-device reading assistant.
			Task: Write a concise, atmospheric 3-bullet "Where Was I?" recap for a reader returning to a manga.
			Rules:
			1. STRICT SPOILER QUARANTINE: Rely ONLY on the provided premise and read chapter titles. Never guess or mention future events beyond the user's current chapter.
			2. Bullet 1 (Core Premise & Tone): 1 sentence reminding the reader of the protagonist and core hook.
			3. Bullet 2 (Recent Arc Progression): 1-2 sentences synthesizing the progression across the most recently read chapter titles.
			4. Bullet 3 (Stopping Point): State the exact chapter number and title where the reader paused.
			5. Keep total output under 95 words. No introductory filler.
			""".trimIndent(),
		)

		val NL_SEARCH_PROMPT_PREFIX = PromptPrefix(
			"""
			You are a deterministic query compiler for a manga library SQLite database.
			Convert the user's natural language search request into a single raw JSON object (no markdown fences, no explanation) matching this exact schema:
			{
			  "ftsKeywords": ["string"],
			  "includedTags": ["string"],
			  "excludedTags": ["string"],
			  "completionState": "ALL" | "COMPLETED_ONLY" | "ONGOING_ONLY",
			  "minUnread": null | number,
			  "maxUnread": null | number,
			  "stalledDaysMin": null | number,
			  "sortOrder": "RELEVANCE" | "UNREAD_ASC" | "UNREAD_DESC" | "STALLED_OLDEST" | "LATEST_RELEASE"
			}
			Expand thematic concepts into standard manga genres/tags in includedTags (e.g., "revenge dungeon" -> ["Revenge", "Dungeon", "Fantasy", "Action"]).
			""".trimIndent(),
		)
	}

	init {
		engineScope.launch { ensureModelReady() }
	}

	suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
		runCatching {
			when (generativeModel.checkStatus()) {
				FeatureStatus.AVAILABLE -> {
					_status.value = NemuriAiStatus.Available
					true
				}
				FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
					generativeModel.download().collect { dlStatus ->
						when (dlStatus) {
							is DownloadStatus.DownloadStarted -> {
								_status.value = NemuriAiStatus.Downloading(0L)
							}
							is DownloadStatus.DownloadProgress -> {
								_status.value = NemuriAiStatus.Downloading(dlStatus.totalBytesDownloaded)
							}
							is DownloadStatus.DownloadCompleted -> {
								_status.value = NemuriAiStatus.Available
							}
							is DownloadStatus.DownloadFailed -> {
								_status.value = NemuriAiStatus.Unavailable(
									dlStatus.e.message ?: "AICore download failed",
								)
							}
						}
					}
					_status.value == NemuriAiStatus.Available
				}
				else -> {
					_status.value = NemuriAiStatus.Unavailable("AICore unavailable on this device/OS")
					false
				}
			}
		}.getOrElse { e ->
			_status.value = NemuriAiStatus.Unavailable(e.message ?: "AICore init error")
			false
		}
	}

	/**
	 * Streams tokens from the active Pluggable AI Backend (Gemini Nano AICore, Google Gemini REST API,
	 * Ollama/Llama/OpenAI endpoint, or LiteRT Local) for the "Where Was I?" card while holding the 1-permit NPU semaphore.
	 */
	fun streamRecap(contextPayload: String): Flow<String> {
		val config = PluggableAiRouter.currentConfig
		if (Generation.customModelOverride == null) {
			if (!config.masterAiEnabled || config.selectedTier == AiBackendTier.DETERMINISTIC_EXTRACTIVE) {
				return emptyFlow()
			}
			if (config.selectedTier == AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT) {
				return flow {
					val remoteText = when (config.activeSubTab) {
						CustomEndpointSubTab.GOOGLE_GEMINI_API -> {
							PluggableAiRouter.callGoogleGeminiApi(
								apiKey = config.geminiApiKey,
								model = config.geminiModel,
								systemInstruction = RECAP_PROMPT_PREFIX.text,
								userPrompt = contextPayload,
							)
						}
						CustomEndpointSubTab.OLLAMA_OPENAI_COMPATIBLE -> {
							PluggableAiRouter.callOpenAiCompatibleApi(
								baseUrl = config.openAiBaseUrl,
								modelId = config.openAiModelId,
								apiKey = config.openAiApiKey,
								systemPrompt = RECAP_PROMPT_PREFIX.text,
								userPrompt = contextPayload,
							)
						}
					}
					if (!remoteText.isNullOrBlank()) {
						emit(remoteText)
					}
				}
			}
			if (config.selectedTier == AiBackendTier.LOCAL_LITERT_FILE) {
				if (!config.liteRtModelDownloaded && config.liteRtModelFileName == null) {
					return emptyFlow()
				}
			}
		}

		if (_status.value != NemuriAiStatus.Available && Generation.customModelOverride == null) return emptyFlow()
		val request = generateContentRequest(TextPart(contextPayload)) {
			promptPrefix = RECAP_PROMPT_PREFIX
			temperature = 0.25f
			maxOutputTokens = 220
			enableThinking = false
		}
		return flow {
			if (!ensureModelReady()) return@flow
			npuSemaphore.withPermit {
				generativeModel.generateContentStream(request).collect { chunk ->
					emit(chunk)
				}
			}
		}
	}

	/**
	 * Executes low-temperature structured generation for Natural Language Library Search
	 * across the active Pluggable AI Backend.
	 */
	suspend fun generateSearchAstJson(userQuery: String): String? = withContext(Dispatchers.Default) {
		val config = PluggableAiRouter.currentConfig
		if (Generation.customModelOverride == null) {
			if (!config.masterAiEnabled || config.selectedTier == AiBackendTier.DETERMINISTIC_EXTRACTIVE) {
				return@withContext null
			}
			if (config.selectedTier == AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT) {
				val sysPrompt = "${NL_SEARCH_PROMPT_PREFIX.text}\nOutput raw valid JSON only. Never wrap in markdown code blocks."
				val prompt = "User Query: \"$userQuery\"\nJSON:"
				val remoteJson = when (config.activeSubTab) {
					CustomEndpointSubTab.GOOGLE_GEMINI_API -> {
						PluggableAiRouter.callGoogleGeminiApi(
							apiKey = config.geminiApiKey,
							model = config.geminiModel,
							systemInstruction = sysPrompt,
							userPrompt = prompt,
						)
					}
					CustomEndpointSubTab.OLLAMA_OPENAI_COMPATIBLE -> {
						PluggableAiRouter.callOpenAiCompatibleApi(
							baseUrl = config.openAiBaseUrl,
							modelId = config.openAiModelId,
							apiKey = config.openAiApiKey,
							systemPrompt = sysPrompt,
							userPrompt = prompt,
						)
					}
				}
				if (!remoteJson.isNullOrBlank()) {
					return@withContext remoteJson
						.removePrefix("```json")
						.removePrefix("```")
						.removeSuffix("```")
						.trim()
				}
			}
		}

		if (!ensureModelReady()) return@withContext null
		npuSemaphore.withPermit {
			runCatching {
				val request = generateContentRequest(
					SystemInstruction("Output raw valid JSON only. Never wrap in markdown code blocks."),
					TextPart("User Query: \"$userQuery\"\nJSON:"),
				) {
					promptPrefix = NL_SEARCH_PROMPT_PREFIX
					temperature = 0.05f
					maxOutputTokens = 180
					seed = 42
					enableThinking = false
				}
				val response = generativeModel.generateContent(request)
				response.candidates.firstOrNull()?.text?.trim()
			}.getOrNull()
		}
	}

	fun close() {
		runCatching { generativeModel.close() }
	}
}
