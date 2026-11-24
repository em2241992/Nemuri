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
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.ReadingStats
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

@Immutable
data class ReadingStatsUiState(
	val isLoading: Boolean = true,
	val period: StatsPeriod = StatsPeriod.ALL,
	val selectedCategoryIds: Set<Long> = emptySet(),
	val categories: List<FavouriteCategory> = emptyList(),
	val stats: ReadingStats = ReadingStats(),
	val isStatsEnabled: Boolean = true,
)

@HiltViewModel
class ReadingStatsViewModel @Inject constructor(
	private val statsRepository: StatsRepository,
	favouritesRepository: FavouritesRepository,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val periodFlow = MutableStateFlow(StatsPeriod.ALL)
	private val categoriesFilterFlow = MutableStateFlow<Set<Long>>(emptySet())
	private val snapshotFlow = MutableStateFlow<Pair<Boolean, ReadingStats>>(true to ReadingStats())

	init {
		reloadStats()
	}

	val uiState: StateFlow<ReadingStatsUiState> = combine(
		periodFlow,
		categoriesFilterFlow,
		favouritesRepository.observeCategories(),
		snapshotFlow,
		settings.observeChanges().onStart { emit(null) },
	) { period, selectedCats, categories, (loading, stats), _ ->
		ReadingStatsUiState(
			isLoading = loading,
			period = period,
			selectedCategoryIds = selectedCats,
			categories = categories,
			stats = stats,
			isStatsEnabled = settings.isStatsEnabled,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = ReadingStatsUiState(),
	)

	fun selectPeriod(period: StatsPeriod) {
		periodFlow.value = period
		reloadStats()
	}

	fun toggleCategoryFilter(categoryId: Long) {
		val current = categoriesFilterFlow.value.toMutableSet()
		if (categoryId in current) current.remove(categoryId) else current.add(categoryId)
		categoriesFilterFlow.value = current
		reloadStats()
	}

	fun clearCategoryFilter() {
		categoriesFilterFlow.value = emptySet()
		reloadStats()
	}

	fun setStatsEnabled(enabled: Boolean) {
		settings.setStatsEnabled(enabled)
		toastManager.show(if (enabled) "Reading statistics recording enabled" else "Reading statistics paused")
	}

	fun clearAllStats() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatchingCancellable {
				statsRepository.clearStats()
			}
			toastManager.show("Reading statistics cleared")
			reloadStats()
		}
	}

	fun reloadStats() {
		val period = periodFlow.value
		val cats = categoriesFilterFlow.value
		viewModelScope.launch(Dispatchers.IO) {
			snapshotFlow.value = true to snapshotFlow.value.second
			val result = runCatchingCancellable {
				statsRepository.getStatsSnapshot(period, cats)
			}.getOrDefault(ReadingStats(period = period))
			snapshotFlow.value = false to result
		}
	}
}
