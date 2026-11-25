package org.koitharu.kotatsu.ai.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor stub keeping FOSS builds free of prebuilt LiteRT-LM native runtimes.
 */
@Singleton
class LocalLlmClient @Inject constructor() {

	val isRuntimeSupported: Boolean = false

	private val _isModelLoadedInRam = MutableStateFlow(false)
	val isModelLoadedInRam: StateFlow<Boolean> = _isModelLoadedInRam.asStateFlow()

	fun hasReadyModel(): Boolean = false

	fun activeModelLabel(): String = "Local LLM (Unavailable on F-Droid)"

	suspend fun generate(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
	): Result<Pair<String, String>> = Result.failure(
		UnsupportedOperationException("On-device LiteRT-LM runtime is only included in the GitHub build"),
	)

	suspend fun unloadNow() = Unit
}
