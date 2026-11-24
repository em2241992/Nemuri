package org.nemuri.app.ui.enhance

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.data.AiSecretStore
import org.koitharu.kotatsu.ai.domain.AiConnectionTestResult
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.NanoAvailability
import org.koitharu.kotatsu.ai.domain.NemuriAiRouter
import org.koitharu.kotatsu.ai.domain.WhereWasIRecapUseCase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class AiSettingsUiState(
	val aiMode: AiMode = AiMode.AUTO,
	val nanoAvailability: NanoAvailability = NanoAvailability.UNAVAILABLE,
	val isCheckingNano: Boolean = true,
	val isDownloadingNano: Boolean = false,
	val nanoDownloadedMb: Long = 0L,
	val remoteUrl: String = "http://127.0.0.1:11434/v1",
	val remoteModel: String = "llama3.2",
	val hasOpenAiKey: Boolean = false,
	val geminiModel: String = "gemini-2.5-flash",
	val hasGeminiKey: Boolean = false,
	val showRecapCard: Boolean = true,
	val includeNovelExcerpt: Boolean = true,
	val isTestingConnection: Boolean = false,
	val testResult: AiConnectionTestResult? = null,
)

private data class AiDynamicState(
	val nanoAvailability: NanoAvailability = NanoAvailability.UNAVAILABLE,
	val isCheckingNano: Boolean = true,
	val isDownloadingNano: Boolean = false,
	val nanoDownloadedMb: Long = 0L,
	val hasOpenAiKey: Boolean = false,
	val hasGeminiKey: Boolean = false,
	val isTestingConnection: Boolean = false,
	val testResult: AiConnectionTestResult? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
	private val settings: AppSettings,
	private val secretStore: AiSecretStore,
	private val aiRouterProvider: Provider<NemuriAiRouter>,
	private val recapUseCaseProvider: Provider<WhereWasIRecapUseCase>,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val dynamicState = MutableStateFlow(
		AiDynamicState(
			hasOpenAiKey = secretStore.hasOpenAiApiKey(),
			hasGeminiKey = secretStore.hasGeminiApiKey(),
		),
	)

	init {
		refreshNanoStatus()
	}

	val uiState: StateFlow<AiSettingsUiState> = combine(
		settings.observeChanges().onStart { emit(null) },
		dynamicState,
	) { _, dyn ->
		AiSettingsUiState(
			aiMode = AiMode.fromKey(settings.aiMode),
			nanoAvailability = dyn.nanoAvailability,
			isCheckingNano = dyn.isCheckingNano,
			isDownloadingNano = dyn.isDownloadingNano,
			nanoDownloadedMb = dyn.nanoDownloadedMb,
			remoteUrl = settings.aiRemoteUrl,
			remoteModel = settings.aiRemoteModel,
			hasOpenAiKey = dyn.hasOpenAiKey,
			geminiModel = settings.aiGeminiModel,
			hasGeminiKey = dyn.hasGeminiKey,
			showRecapCard = settings.isAiShowRecapCard,
			includeNovelExcerpt = settings.isAiIncludeNovelExcerpt,
			isTestingConnection = dyn.isTestingConnection,
			testResult = dyn.testResult,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = AiSettingsUiState(),
	)

	fun setAiMode(mode: AiMode) {
		settings.aiMode = mode.key
		dynamicState.update { it.copy(testResult = null) }
	}

	fun refreshNanoStatus() {
		viewModelScope.launch(Dispatchers.IO) {
			dynamicState.update { it.copy(isCheckingNano = true) }
			val availability = aiRouterProvider.get().checkNanoAvailability()
			dynamicState.update { it.copy(nanoAvailability = availability, isCheckingNano = false) }
		}
	}

	fun downloadNanoModel() {
		if (dynamicState.value.isDownloadingNano) return
		viewModelScope.launch(Dispatchers.IO) {
			dynamicState.update {
				it.copy(
					isDownloadingNano = true,
					nanoAvailability = NanoAvailability.DOWNLOADING,
					nanoDownloadedMb = 0L,
				)
			}
			val result = aiRouterProvider.get().downloadNanoModel { bytes ->
				val mb = bytes / (1024L * 1024L)
				dynamicState.update { it.copy(nanoDownloadedMb = mb) }
			}
			val availability = aiRouterProvider.get().checkNanoAvailability()
			dynamicState.update {
				it.copy(isDownloadingNano = false, nanoAvailability = availability)
			}
			result.onSuccess {
				toastManager.show("Gemini Nano model downloaded")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Gemini Nano download failed")
			}
		}
	}

	fun saveRemoteConfig(url: String, model: String, bearerTokenInput: String?) {
		settings.aiRemoteUrl = url.trim().ifEmpty { "http://127.0.0.1:11434/v1" }
		settings.aiRemoteModel = model.trim().ifEmpty { "llama3.2" }
		if (bearerTokenInput != null) {
			secretStore.setOpenAiApiKey(bearerTokenInput)
		}
		dynamicState.update {
			it.copy(hasOpenAiKey = secretStore.hasOpenAiApiKey(), testResult = null)
		}
		toastManager.show("OpenAI / Ollama endpoint saved")
	}

	fun clearOpenAiApiKey() {
		secretStore.setOpenAiApiKey("")
		dynamicState.update { it.copy(hasOpenAiKey = false, testResult = null) }
		toastManager.show("Bearer token cleared from Keystore")
	}

	fun saveGeminiConfig(model: String, apiKeyInput: String?) {
		settings.aiGeminiModel = model.trim().ifEmpty { "gemini-2.5-flash" }
		if (apiKeyInput != null) {
			secretStore.setGeminiApiKey(apiKeyInput)
		}
		dynamicState.update {
			it.copy(hasGeminiKey = secretStore.hasGeminiApiKey(), testResult = null)
		}
		toastManager.show("Gemini API config saved (encrypted)")
	}

	fun clearGeminiApiKey() {
		secretStore.setGeminiApiKey("")
		dynamicState.update { it.copy(hasGeminiKey = false, testResult = null) }
		toastManager.show("Gemini API key cleared from Keystore")
	}

	fun setShowRecapCard(enabled: Boolean) {
		settings.isAiShowRecapCard = enabled
	}

	fun setIncludeNovelExcerpt(enabled: Boolean) {
		settings.isAiIncludeNovelExcerpt = enabled
	}

	fun testActiveBackend() {
		if (dynamicState.value.isTestingConnection) return
		viewModelScope.launch(Dispatchers.IO) {
			dynamicState.update { it.copy(isTestingConnection = true, testResult = null) }
			val res = aiRouterProvider.get().testActiveBackend()
			dynamicState.update { it.copy(isTestingConnection = false, testResult = res) }
		}
	}

	fun clearAllCachedRecaps() {
		viewModelScope.launch(Dispatchers.IO) {
			recapUseCaseProvider.get().clearAllCaches()
			toastManager.show("Cleared cached AI recaps")
		}
	}
}
