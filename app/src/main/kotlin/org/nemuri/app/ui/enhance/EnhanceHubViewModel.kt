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
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
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
	val isDuplicateCheckOnAdd: Boolean = true,
	val isDuplicateProgressMigrated: Boolean = true,
	val isStatsEnabled: Boolean = true,
	val isScanningHealth: Boolean = true,
)

private data class EnhanceScanResult(
	val isScanning: Boolean = true,
	val duplicatePairs: Int = 0,
	val totalReadingTimeMs: Long = 0L,
	val currentStreakDays: Int = 0,
)

@HiltViewModel
class EnhanceHubViewModel @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	private val databaseProvider: Provider<MangaDatabase>,
	private val sourcesRepository: MangaSourcesRepository,
	private val statsRepository: StatsRepository,
	private val settings: AppSettings,
) : ViewModel() {

	private val scanResultFlow = MutableStateFlow(EnhanceScanResult())

	init {
		refreshDiagnostics()
	}

	val uiState: StateFlow<EnhanceHubUiState> = combine(
		favouritesRepository.observeMangaCount(),
		databaseProvider.get().getMangaDao().observeLibrarySourceUsage(),
		sourcesRepository.observeEnabledSources(),
		scanResultFlow,
		settings.observeChanges().onStart { emit(null) },
	) { libraryCount, usages, enabledSources, scan, _ ->
		val enabledNames = enabledSources.mapTo(HashSet(enabledSources.size)) { it.name }
		val broken = usages.filter { it.source !in enabledNames }
		val shaderCfg = ReaderShaderConfig(
			preset = ReaderShaderPreset.fromKey(settings.readerShaderMode),
			descreen = settings.isReaderDescreenEnabled,
			inkLevels = settings.isReaderInkLevelsEnabled,
			strength = settings.readerShaderStrength,
		)
		EnhanceHubUiState(
			libraryCount = libraryCount,
			brokenSourcesCount = broken.size,
			brokenTitlesCount = broken.sumOf { it.mangaCount },
			duplicatePairsCount = scan.duplicatePairs,
			totalReadingTimeMs = scan.totalReadingTimeMs,
			currentStreakDays = scan.currentStreakDays,
			activeShaderLabel = shaderCfg.summaryLabel(),
			isDuplicateCheckOnAdd = settings.isDuplicateCheckEnabled,
			isDuplicateProgressMigrated = settings.isDuplicateProgressMigrated,
			isStatsEnabled = settings.isStatsEnabled,
			isScanningHealth = scan.isScanning,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = EnhanceHubUiState(),
	)

	fun refreshDiagnostics() {
		viewModelScope.launch(Dispatchers.IO) {
			scanResultFlow.value = scanResultFlow.value.copy(isScanning = true)
			val stats = runCatchingCancellable {
				statsRepository.getStatsSnapshot(StatsPeriod.ALL, emptySet())
			}.getOrNull()
			val dupesCount = runCatchingCancellable {
				countQuickDuplicates()
			}.getOrDefault(0)
			scanResultFlow.value = EnhanceScanResult(
				isScanning = false,
				duplicatePairs = dupesCount,
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

	private suspend fun countQuickDuplicates(): Int {
		val all = favouritesRepository.getAllManga()
		if (all.size < 2) return 0
		val byNormalizedKey = HashMap<String, MutableList<Long>>()
		for (manga in all) {
			val kind = if (manga.source.isNovelSource || manga.isEpub) "N" else "M"
			val norm = buildString {
				for (c in manga.title) {
					if (c.isLetterOrDigit()) append(c.lowercaseChar())
				}
			}
			if (norm.length >= 4) {
				byNormalizedKey.getOrPut("$kind:$norm") { ArrayList(2) }.add(manga.id)
			}
		}
		var pairCount = 0
		for (ids in byNormalizedKey.values) {
			val distinctCount = ids.distinct().size
			if (distinctCount >= 2) {
				pairCount += distinctCount - 1
			}
		}
		return pairCount
	}
}
