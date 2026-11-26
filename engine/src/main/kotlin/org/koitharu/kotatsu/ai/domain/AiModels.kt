package org.koitharu.kotatsu.ai.domain

enum class RecapDepth(
	val key: String,
	val shortLabel: String,
	val label: String,
	val description: String,
) {
	BRIEF(
		key = "BRIEF",
		shortLabel = "Brief",
		label = "Brief (Safe Teaser)",
		description = "Atmospheric reminder of premise and arc position without major plot twists or character fates",
	),
	DETAILED(
		key = "DETAILED",
		shortLabel = "Detailed",
		label = "Detailed (Story & Characters So Far)",
		description = "Names characters, key relationships, faction conflicts, and events strictly at or before your last read chapter",
	),
	;

	companion object {
		fun fromKey(key: String?): RecapDepth =
			entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DETAILED
	}
}

enum class AiMode(val key: String, val label: String) {
	OFF("OFF", "Off"),
	AUTO("AUTO", "Auto"),
	GEMINI_NANO("GEMINI_NANO", "Gemini Nano"),
	LOCAL_MODEL("LOCAL_MODEL", "On-Device CPU/GPU"),
	REMOTE_OPENAI("REMOTE_OPENAI", "Ollama / OpenAI"),
	GEMINI_API("GEMINI_API", "Gemini API"),
	;

	companion object {
		fun fromKey(key: String?): AiMode = entries.firstOrNull { it.key == key } ?: AUTO
	}
}

enum class LocalLlmAccelerator(val key: String, val label: String) {
	GPU("GPU", "GPU (OpenCL/Vulkan • Recommended)"),
	CPU("CPU", "CPU (Multi-Core XNNPACK)"),
	;

	companion object {
		fun fromKey(key: String?): LocalLlmAccelerator =
			entries.firstOrNull { it.key == key } ?: GPU
	}
}

data class LocalLlmCatalogEntry(
	val id: String,
	val title: String,
	val subtitle: String,
	val fileName: String,
	val downloadUrl: String?,
	val approxSizeBytes: Long,
	val minRamMb: Int,
	val isRecommended: Boolean = false,
	val requiredSocModel: String? = null,
)

data class LocalModelDownloadState(
	val activeModelId: String? = null,
	val downloadedBytes: Long = 0L,
	val totalBytes: Long = 0L,
	val error: String? = null,
)

enum class NanoAvailability(val label: String) {
	AVAILABLE("Ready (AICore)"),
	DOWNLOADABLE("Downloadable (AICore)"),
	DOWNLOADING("Downloading…"),
	UNAVAILABLE("Unavailable on device"),
}

data class NanoDiagnostics(
	val availability: NanoAvailability,
	val detailMessage: String,
	val activeProfileLabel: String? = null,
	val aiCoreVersionName: String? = null,
	val supportsOpenPrompt: Boolean = false,
	val supportsSummarization: Boolean = false,
)

data class AiGenerationResult(
	val text: String,
	val engineUsed: String,
	val isAiGenerated: Boolean,
)

data class AiConnectionTestResult(
	val ok: Boolean,
	val engineName: String,
	val message: String,
	val latencyMs: Long,
)
