package org.koitharu.kotatsu.ai.domain

enum class AiMode(val key: String, val label: String) {
	OFF("OFF", "Off"),
	AUTO("AUTO", "Auto"),
	GEMINI_NANO("GEMINI_NANO", "Gemini Nano"),
	REMOTE_OPENAI("REMOTE_OPENAI", "Ollama / OpenAI"),
	GEMINI_API("GEMINI_API", "Gemini API"),
	;

	companion object {
		fun fromKey(key: String?): AiMode = entries.firstOrNull { it.key == key } ?: AUTO
	}
}

enum class NanoAvailability(val label: String) {
	AVAILABLE("Ready (AICore)"),
	DOWNLOADABLE("Downloadable (AICore)"),
	DOWNLOADING("Downloading…"),
	UNAVAILABLE("Unavailable on device"),
}

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
