package org.koitharu.kotatsu.ai.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoClient @Inject constructor() {

	suspend fun checkAvailability(): NanoAvailability = NanoAvailability.UNAVAILABLE

	suspend fun triggerDownload(onProgress: (Long) -> Unit = {}): Result<Unit> =
		Result.failure(UnsupportedOperationException("Gemini Nano requires AICore (GitHub build)"))

	suspend fun generate(prompt: String): Result<String> =
		Result.failure(UnsupportedOperationException("Gemini Nano requires AICore (GitHub build)"))
}
