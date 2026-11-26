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
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.domain.AiConnectionTestResult
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.LocalLlmClient
import org.koitharu.kotatsu.ai.domain.NemuriAiRouter
import org.koitharu.kotatsu.ai.domain.RecapDepth
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class EnhanceHubUiState(
	val libraryCount: Int = 0,
	val brokenSourcesCount: Int = 0,
	val brokenTitlesCount: Int = 0,
	val brokenSourceEntries: List<Pair<String, Int>> = emptyList(),
	val duplicatePairsCount: Int = 0,
	val editionVariantPairsCount: Int = 0,
	val needsReviewPairsCount: Int = 0,
	val totalReadingTimeMs: Long = 0L,
	val currentStreakDays: Int = 0,
	val isReaderSeamlessStitchEnabled: Boolean = true,
	val readerShaderPreset: ReaderShaderPreset = ReaderShaderPreset.OFF,
	val isReaderDescreenEnabled: Boolean = false,
	val isReaderInkLevelsEnabled: Boolean = false,
	val activeShaderLabel: String = "Off",
	val isNeuralUpscaleSupported: Boolean = false,
	val activeNeuralDelegateLabel: String = "",
	val isNeuralUpscaleEnabled: Boolean = false,
	val isNeuralPreloadEnabled: Boolean = true,
	val neuralCacheBytes: Long = 0L,
	val aiMode: AiMode = AiMode.AUTO,
	val aiModeLabel: String = "Auto",
	val aiRecapDepth: RecapDepth = RecapDepth.DETAILED,
	val isAiShowRecapCard: Boolean = true,
	val isAiIncludeNovelExcerpt: Boolean = true,
	val isLocalModelLoadedInRam: Boolean = false,
	val isTestingAi: Boolean = false,
	val aiTestResult: AiConnectionTestResult? = null,
	val isDuplicateCheckOnAdd: Boolean = true,
	val isDuplicateProgressMigrated: Boolean = true,
	val duplicateWinnerStrategy: DuplicateWinnerStrategy = DuplicateWinnerStrategy.MOST_CHAPTERS,
	val ignoredDuplicatePairsCount: Int = 0,
	val isStatsEnabled: Boolean = true,
	val isScanningHealth: Boolean = true,
)

private data class EnhanceStatsResult(
	val isScanning: Boolean = true,
	val totalReadingTimeMs: Long = 0L,
	val currentStreakDays: Int = 0,
)

private data class EnhanceAiTestState(
	val isTesting: Boolean = false,
	val result: AiConnectionTestResult? = null,
)

@HiltViewModel
class EnhanceHubViewModel @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	private val databaseProvider: Provider<MangaDatabase>,
	private val sourcesRepository: MangaSourcesRepository,
	private val statsRepository: StatsRepository,
	private val duplicateCache: LibraryDuplicateCache,
	private val pageUpscaleEngine: PageUpscaleEngine,
	private val aiRouterProvider: Provider<NemuriAiRouter>,
	private val localLlmClientProvider: Provider<LocalLlmClient>,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val statsResultFlow = MutableStateFlow(EnhanceStatsResult())
	private val aiTestStateFlow = MutableStateFlow(EnhanceAiTestState())

	init {
		refreshDiagnostics()
	}

	val uiState: StateFlow<EnhanceHubUiState> = combine(
		favouritesRepository.observeMangaCount(),
		databaseProvider.get().getMangaDao().observeLibrarySourceUsage(),
		sourcesRepository.observeEnabledSources(),
		statsResultFlow,
		duplicateCache.snapshot,
		combine(
			pageUpscaleEngine.activeDelegateLabel,
			pageUpscaleEngine.cacheBytesFlow,
		) { label, bytes -> label to bytes },
		combine(
			aiTestStateFlow,
			localLlmClientProvider.get().isModelLoadedInRam,
		) { testState, loadedInRam -> testState to loadedInRam },
		settings.observeChanges().onStart { emit(null) },
	) { values ->
		@Suppress("UNCHECKED_CAST")
		val libraryCount = values[0] as Int
		@Suppress("UNCHECKED_CAST")
		val usages = values[1] as List<org.koitharu.kotatsu.core.db.dao.LibrarySourceUsage>
		@Suppress("UNCHECKED_CAST")
		val enabledSources = values[2] as List<org.koitharu.kotatsu.parsers.model.MangaSource>
		val stats = values[3] as EnhanceStatsResult
		val dupSnap = values[4] as DuplicateScanSnapshot
		@Suppress("UNCHECKED_CAST")
		val neuralPair = values[5] as Pair<String, Long>
		val delegateLabel = neuralPair.first
		val neuralCacheBytes = neuralPair.second
		@Suppress("UNCHECKED_CAST")
		val aiRuntimePair = values[6] as Pair<EnhanceAiTestState, Boolean>
		val aiTestState = aiRuntimePair.first
		val isLocalLoaded = aiRuntimePair.second

		val enabledNames = enabledSources.mapTo(HashSet(enabledSources.size)) { it.name }
		val broken = usages.filter { it.source !in enabledNames }
		val shaderPreset = ReaderShaderPreset.fromKey(settings.readerShaderMode)
		val shaderCfg = ReaderShaderConfig(
			preset = shaderPreset,
			descreen = settings.isReaderDescreenEnabled,
			inkLevels = settings.isReaderInkLevelsEnabled,
			strength = settings.readerShaderStrength,
		)
		val shaderSummary = shaderCfg.summaryLabel()
		val neuralActive = settings.isNeuralUpscaleEnabled
		val activeUpscalerLabel = when {
			neuralActive && shaderCfg.isActive -> "Neural 2x + $shaderSummary"
			neuralActive -> "Neural 2x"
			else -> shaderSummary
		}
		val currentAiMode = AiMode.fromKey(settings.aiMode)
		EnhanceHubUiState(
			libraryCount = libraryCount,
			brokenSourcesCount = broken.size,
			brokenTitlesCount = broken.sumOf { it.mangaCount },
			brokenSourceEntries = broken.map { it.source to it.mangaCount },
			duplicatePairsCount = dupSnap.exactPairCount,
			editionVariantPairsCount = dupSnap.editionVariantCount,
			needsReviewPairsCount = dupSnap.needsReviewCount,
			totalReadingTimeMs = stats.totalReadingTimeMs,
			currentStreakDays = stats.currentStreakDays,
			isReaderSeamlessStitchEnabled = settings.isReaderSeamlessStitchEnabled,
			readerShaderPreset = shaderPreset,
			isReaderDescreenEnabled = settings.isReaderDescreenEnabled,
			isReaderInkLevelsEnabled = settings.isReaderInkLevelsEnabled,
			activeShaderLabel = activeUpscalerLabel,
			isNeuralUpscaleSupported = pageUpscaleEngine.isSupported,
			activeNeuralDelegateLabel = delegateLabel,
			isNeuralUpscaleEnabled = neuralActive,
			isNeuralPreloadEnabled = settings.isNeuralPreloadEnabled,
			neuralCacheBytes = neuralCacheBytes,
			aiMode = currentAiMode,
			aiModeLabel = currentAiMode.label,
			aiRecapDepth = RecapDepth.fromKey(settings.aiRecapDepth),
			isAiShowRecapCard = settings.isAiShowRecapCard,
			isAiIncludeNovelExcerpt = settings.isAiIncludeNovelExcerpt,
			isLocalModelLoadedInRam = isLocalLoaded,
			isTestingAi = aiTestState.isTesting,
			aiTestResult = aiTestState.result,
			isDuplicateCheckOnAdd = settings.isDuplicateCheckEnabled,
			isDuplicateProgressMigrated = settings.isDuplicateProgressMigrated,
			duplicateWinnerStrategy = settings.duplicateWinnerStrategy,
			ignoredDuplicatePairsCount = settings.ignoredDuplicatePairs.size,
			isStatsEnabled = settings.isStatsEnabled,
			isScanningHealth = stats.isScanning || dupSnap.isScanningTier1,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = EnhanceHubUiState(),
	)

	fun refreshDiagnostics() {
		duplicateCache.ensureScanned(forceRefresh = true)
		viewModelScope.launch(Dispatchers.IO) {
			statsResultFlow.value = statsResultFlow.value.copy(isScanning = true)
			val stats = runCatchingCancellable {
				statsRepository.getStatsSnapshot(StatsPeriod.ALL, emptySet())
			}.getOrNull()
			statsResultFlow.value = EnhanceStatsResult(
				isScanning = false,
				totalReadingTimeMs = stats?.totalDuration ?: 0L,
				currentStreakDays = stats?.currentStreak ?: 0,
			)
		}
	}

	fun setDuplicateCheckOnAdd(enabled: Boolean) {
		settings.isDuplicateCheckEnabled = enabled
	}

	fun setDuplicateProgressMigrated(enabled: Boolean) {
		settings.isDuplicateProgressMigrated = enabled
	}

	fun setDuplicateWinnerStrategy(strategy: DuplicateWinnerStrategy) {
		settings.duplicateWinnerStrategy = strategy
	}

	fun resetIgnoredDuplicatePairs() {
		settings.clearIgnoredDuplicatePairs()
		duplicateCache.ensureScanned(forceRefresh = true)
		toastManager.show("Ignored duplicate pairs reset")
	}

	fun setReaderSeamlessStitchEnabled(enabled: Boolean) {
		settings.isReaderSeamlessStitchEnabled = enabled
	}

	fun setReaderShaderPreset(preset: ReaderShaderPreset) {
		settings.readerShaderMode = preset.key
	}

	fun setReaderDescreenEnabled(enabled: Boolean) {
		settings.isReaderDescreenEnabled = enabled
	}

	fun setReaderInkLevelsEnabled(enabled: Boolean) {
		settings.isReaderInkLevelsEnabled = enabled
	}

	fun setNeuralUpscaleEnabled(enabled: Boolean) {
		settings.isNeuralUpscaleEnabled = enabled
	}

	fun setNeuralPreloadEnabled(enabled: Boolean) {
		settings.isNeuralPreloadEnabled = enabled
	}

	fun clearUpscaleCache() {
		viewModelScope.launch(Dispatchers.IO) {
			pageUpscaleEngine.clearCache()
			toastManager.show("Cleared neural upscale cache")
		}
	}

	fun setAiMode(mode: AiMode) {
		settings.aiMode = mode.key
		aiTestStateFlow.value = EnhanceAiTestState()
	}

	fun setAiRecapDepth(depth: RecapDepth) {
		settings.aiRecapDepth = depth.key
	}

	fun setAiShowRecapCard(enabled: Boolean) {
		settings.isAiShowRecapCard = enabled
	}

	fun setAiIncludeNovelExcerpt(enabled: Boolean) {
		settings.isAiIncludeNovelExcerpt = enabled
	}

	fun unloadLocalModelFromRam() {
		viewModelScope.launch(Dispatchers.IO) {
			localLlmClientProvider.get().unloadNow()
			toastManager.show("Unloaded local LLM from RAM")
		}
	}

	fun testAiBackend() {
		if (aiTestStateFlow.value.isTesting) return
		viewModelScope.launch(Dispatchers.IO) {
			aiTestStateFlow.value = EnhanceAiTestState(isTesting = true, result = null)
			val res = runCatchingCancellable {
				aiRouterProvider.get().testActiveBackend()
			}.getOrElse { err ->
				AiConnectionTestResult(
					ok = false,
					engineName = AiMode.fromKey(settings.aiMode).label,
					message = err.localizedMessage ?: "Backend health check failed",
					latencyMs = 0L,
				)
			}
			aiTestStateFlow.value = EnhanceAiTestState(isTesting = false, result = res)
			toastManager.show(
				if (res.ok) "AI Ready: ${res.engineName} (${res.latencyMs}ms)" else res.message,
			)
		}
	}
}
