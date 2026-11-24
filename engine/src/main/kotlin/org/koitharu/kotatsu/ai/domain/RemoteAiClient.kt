package org.koitharu.kotatsu.ai.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.ai.data.AiSecretStore
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RemoteAiClient @Inject constructor(
	@BaseHttpClient private val okHttpClientProvider: Provider<OkHttpClient>,
	private val secretStore: AiSecretStore,
	private val settings: AppSettings,
) {

	private val aiHttpClient: OkHttpClient by lazy {
		okHttpClientProvider.get().newBuilder()
			.connectTimeout(20L, TimeUnit.SECONDS)
			.readTimeout(90L, TimeUnit.SECONDS)
			.writeTimeout(30L, TimeUnit.SECONDS)
			.build()
	}

	suspend fun generateOpenAiCompatible(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
	): Result<String> = withContext(Dispatchers.IO) {
		val baseUrl = settings.aiRemoteUrl.trim().trimEnd('/')
		val model = settings.aiRemoteModel.trim()
		if (baseUrl.isEmpty() || model.isEmpty()) {
			return@withContext Result.failure(IllegalStateException("Remote endpoint URL or model is empty"))
		}
		val endpoint = if (baseUrl.endsWith("/chat/completions", ignoreCase = true)) {
			baseUrl
		} else {
			"$baseUrl/chat/completions"
		}
		val payload = JSONObject().apply {
			put("model", model)
			put("temperature", 0.3)
			put("max_tokens", maxTokens)
			put("stream", false)
			put(
				"messages",
				JSONArray().apply {
					put(JSONObject().put("role", "system").put("content", systemPrompt))
					put(JSONObject().put("role", "user").put("content", userPrompt))
				},
			)
		}
		val reqBuilder = Request.Builder()
			.url(endpoint)
			.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
			.header("Accept", "application/json")
		val apiKey = secretStore.getOpenAiApiKey()
		if (apiKey.isNotBlank()) {
			reqBuilder.header("Authorization", "Bearer $apiKey")
		}
		executeJsonPost(reqBuilder.build()) { body ->
			val root = JSONObject(body)
			val choices = root.optJSONArray("choices") ?: return@executeJsonPost null
			if (choices.length() == 0) return@executeJsonPost null
			val message = choices.optJSONObject(0)?.optJSONObject("message")
			message?.optString("content")?.trim()?.takeIf { it.isNotEmpty() }
		}
	}

	suspend fun generateGeminiRest(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
	): Result<String> = withContext(Dispatchers.IO) {
		val apiKey = secretStore.getGeminiApiKey()
		if (apiKey.isBlank()) {
			return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
		}
		val model = settings.aiGeminiModel.trim().ifEmpty { "gemini-2.5-flash" }
		val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
		val payload = JSONObject().apply {
			put(
				"systemInstruction",
				JSONObject().put(
					"parts",
					JSONArray().put(JSONObject().put("text", systemPrompt)),
				),
			)
			put(
				"contents",
				JSONArray().put(
					JSONObject()
						.put("role", "user")
						.put("parts", JSONArray().put(JSONObject().put("text", userPrompt))),
				),
			)
			put(
				"generationConfig",
				JSONObject()
					.put("temperature", 0.3)
					.put("maxOutputTokens", maxTokens),
			)
		}
		val req = Request.Builder()
			.url(endpoint)
			.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
			.header("Accept", "application/json")
			.header("x-goog-api-key", apiKey)
			.build()
		executeJsonPost(req) { body ->
			val root = JSONObject(body)
			val candidates = root.optJSONArray("candidates") ?: return@executeJsonPost null
			if (candidates.length() == 0) return@executeJsonPost null
			val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
				?: return@executeJsonPost null
			buildString {
				for (i in 0 until parts.length()) {
					val partText = parts.optJSONObject(i)?.optString("text")
					if (!partText.isNullOrEmpty()) append(partText)
				}
			}.trim().takeIf { it.isNotEmpty() }
		}
	}

	private fun executeJsonPost(
		request: Request,
		extractText: (String) -> String?,
	): Result<String> = runCatching {
		aiHttpClient.newCall(request).execute().use { resp ->
			val body = resp.body.string()
			if (!resp.isSuccessful) {
				val snippet = extractErrorMessage(body) ?: body.take(180).trim()
				error("HTTP ${resp.code}: $snippet")
			}
			extractText(body) ?: error("Provider returned an empty response")
		}
	}

	private fun extractErrorMessage(raw: String): String? = runCatching {
		val obj = JSONObject(raw)
		obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
			?: obj.optString("message").takeIf { it.isNotBlank() }
	}.getOrNull()

	private companion object {
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}
