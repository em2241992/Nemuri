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
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class EnhanceHubUiState(
	val libraryCount: Int = 0,
	val brokenSourcesCount: Int = 0,
	val brokenTitlesCount: Int = 0,
	val duplicatePairsCount: Int = 0,
	val totalReadingTimeMs: Long = 0L,
	val currentStreakDays: Int = 0,
	val activeShaderLabel: String = "Off",
	val activeNeuralDelegateLabel: String = "",
	val isNeuralUpscaleEnabled: Boolean = false,
	val neuralCacheBytes: Long = 0L,
	val aiModeLabel: String = "Auto",
	val isDuplicateCheckOnAdd: Boolean = true,
	val isDuplicateProgressMigrated: Boolean = true,
	val isStatsEnabled: Boolean = true,
	val isScanningHealth: Boolean = true,
)

private data class EnhanceStatsResult(
	val isScanning: Boolean = true,
	val totalReadingTimeMs: Long = 0L,
	val currentStreakDays: Int = 0,
)

@HiltViewModel
class EnhanceHubViewModel @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	private val databaseProvider: Provider<MangaDatabase>,
	private val sourcesRepository: MangaSourcesRepository,
	private val statsRepository: StatsRepository,
	private val duplicateCache: LibraryDuplicateCache,
	private val pageUpscaleEngine: PageUpscaleEngine,
	private val settings: AppSettings,
) : ViewModel() {

	private val statsResultFlow = MutableStateFlow(EnhanceStatsResult())

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

		val enabledNames = enabledSources.mapTo(HashSet(enabledSources.size)) { it.name }
		val broken = usages.filter { it.source !in enabledNames }
		val shaderCfg = ReaderShaderConfig(
			preset = ReaderShaderPreset.fromKey(settings.readerShaderMode),
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
		EnhanceHubUiState(
			libraryCount = libraryCount,
			brokenSourcesCount = broken.size,
			brokenTitlesCount = broken.sumOf { it.mangaCount },
			duplicatePairsCount = dupSnap.exactPairCount,
			totalReadingTimeMs = stats.totalReadingTimeMs,
			currentStreakDays = stats.currentStreakDays,
			activeShaderLabel = activeUpscalerLabel,
			activeNeuralDelegateLabel = delegateLabel,
			isNeuralUpscaleEnabled = neuralActive,
			neuralCacheBytes = neuralCacheBytes,
			aiModeLabel = AiMode.fromKey(settings.aiMode).label,
			isDuplicateCheckOnAdd = settings.isDuplicateCheckEnabled,
			isDuplicateProgressMigrated = settings.isDuplicateProgressMigrated,
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

	fun clearUpscaleCache() {
		viewModelScope.launch(Dispatchers.IO) {
			pageUpscaleEngine.clearCache()
		}
	}
}
