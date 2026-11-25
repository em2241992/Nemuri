package org.nemuri.app.ui.enhance

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.alternatives.domain.AutoFixUseCase
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.model.chaptersCount
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider

enum class BrokenWizardStep {
	SOURCES,
	TARGETS,
	REVIEW,
}

@Immutable
data class LibrarySourceItem(
	val sourceName: String,
	val displayTitle: String,
	val mangaCount: Int,
	val isUnavailable: Boolean,
)

@Immutable
data class SmartReplaceProposal(
	val seed: Manga,
	val seedSourceTitle: String,
	val seedChaptersCount: Int,
	val workingReplacement: Manga?,
	val replacementSourceTitle: String?,
	val replacementChaptersCount: Int,
	val alternateCandidates: List<Manga> = emptyList(),
	val isSelected: Boolean = false,
	val isApplied: Boolean = false,
	val errorMessage: String? = null,
)

@Immutable
data class BrokenSourcesUiState(
	val step: BrokenWizardStep = BrokenWizardStep.SOURCES,
	val librarySources: List<LibrarySourceItem> = emptyList(),
	val selectedSourceNames: Set<String> = emptySet(),
	val orderedTargets: List<MigrationTargetSource> = emptyList(),
	val selectedTargetNames: Set<String> = emptySet(),
	val isScanning: Boolean = false,
	val scanDone: Int = 0,
	val scanTotal: Int = 0,
	val proposals: List<SmartReplaceProposal> = emptyList(),
	val isApplying: Boolean = false,
	val showBatchConfirmDialog: Boolean = false,
)

@HiltViewModel
class BrokenSourcesViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val databaseProvider: Provider<MangaDatabase>,
	sourcesRepository: MangaSourcesRepository,
	private val autoFixUseCaseProvider: Provider<AutoFixUseCase>,
	private val migrateUseCaseProvider: Provider<MigrateUseCase>,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val stepFlow = MutableStateFlow(BrokenWizardStep.SOURCES)
	private val selectedSourcesFlow = MutableStateFlow<Set<String>>(emptySet())
	private val targetOrderFlow = MutableStateFlow<List<MigrationTargetSource>>(emptyList())
	private val selectedTargetsFlow = MutableStateFlow<Set<String>>(emptySet())
	private val proposalsStateFlow = MutableStateFlow(ProposalsBundle())

	private var initializedSourceDefaults = false
	private var initializedTargetDefaults = false
	private var scanJob: Job? = null

	val uiState: StateFlow<BrokenSourcesUiState> = combine(
		databaseProvider.get().getMangaDao().observeLibrarySourceUsage(),
		sourcesRepository.observeEnabledSources(),
		stepFlow,
		combine(selectedSourcesFlow, targetOrderFlow, selectedTargetsFlow) { s, o, t -> Triple(s, o, t) },
		proposalsStateFlow,
	) { usages, enabledSources, step, (selSources, orderedTargets, selTargets), propBundle ->
		val enabledNames = enabledSources.mapTo(HashSet(enabledSources.size)) { it.name }
		val items = usages.distinctBy { it.source }.map { usage ->
			val matchingInfo = enabledSources.firstOrNull { it.name == usage.source }
			LibrarySourceItem(
				sourceName = usage.source,
				displayTitle = matchingInfo?.getTitle(context) ?: usage.sourceTitle ?: usage.source,
				mangaCount = usage.mangaCount,
				isUnavailable = usage.source !in enabledNames,
			)
		}.sortedWith(compareByDescending<LibrarySourceItem> { it.isUnavailable }.thenBy { it.displayTitle })

		if (!initializedSourceDefaults && usages.isNotEmpty() && enabledSources.isNotEmpty()) {
			initializedSourceDefaults = true
			selectedSourcesFlow.value = items.filter { it.isUnavailable }.mapTo(HashSet()) { it.sourceName }
		}
		if (!initializedTargetDefaults && enabledSources.isNotEmpty()) {
			initializedTargetDefaults = true
			val targets = enabledSources.map { MigrationTargetSource(it.name, it.getTitle(context)) }
			targetOrderFlow.value = targets
			selectedTargetsFlow.value = targets.take(6).mapTo(HashSet()) { it.name }
		} else if (orderedTargets.isEmpty() && enabledSources.isNotEmpty()) {
			targetOrderFlow.value = enabledSources.map { MigrationTargetSource(it.name, it.getTitle(context)) }
		}

		BrokenSourcesUiState(
			step = step,
			librarySources = items,
			selectedSourceNames = selSources,
			orderedTargets = targetOrderFlow.value,
			selectedTargetNames = selTargets,
			isScanning = propBundle.isScanning,
			scanDone = propBundle.scanDone,
			scanTotal = propBundle.scanTotal,
			proposals = propBundle.proposals,
			isApplying = propBundle.isApplying,
			showBatchConfirmDialog = propBundle.showConfirmDialog,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000L),
		initialValue = BrokenSourcesUiState(),
	)

	fun toggleSourceSelection(sourceName: String) {
		selectedSourcesFlow.update { cur ->
			if (sourceName in cur) cur - sourceName else cur + sourceName
		}
	}

	fun goToTargets() {
		if (selectedSourcesFlow.value.isEmpty()) {
			toastManager.show("Select at least one source to inspect")
			return
		}
		stepFlow.value = BrokenWizardStep.TARGETS
	}

	fun goToSources() {
		scanJob?.cancel()
		stepFlow.value = BrokenWizardStep.SOURCES
	}

	fun toggleTarget(sourceName: String) {
		selectedTargetsFlow.update { cur ->
			if (sourceName in cur) cur - sourceName else cur + sourceName
		}
	}

	fun moveTarget(index: Int, delta: Int) {
		val list = targetOrderFlow.value.toMutableList()
		val targetIdx = index + delta
		if (index !in list.indices || targetIdx !in list.indices) return
		val item = list.removeAt(index)
		list.add(targetIdx, item)
		targetOrderFlow.value = list
	}

	fun startSmartReplaceScan() {
		val chosenSources = selectedSourcesFlow.value.toList()
		val priorityTargets = targetOrderFlow.value
			.map { it.name }
			.filter { it in selectedTargetsFlow.value }

		if (chosenSources.isEmpty() || priorityTargets.isEmpty()) {
			toastManager.show("Select at least one target source")
			return
		}

		stepFlow.value = BrokenWizardStep.REVIEW
		scanJob?.cancel()
		scanJob = viewModelScope.launch(Dispatchers.IO) {
			val entities = databaseProvider.get().getMangaDao().findLibraryMangaBySources(chosenSources).distinctBy { it.manga.id }
			val total = entities.size
			proposalsStateFlow.value = ProposalsBundle(isScanning = true, scanDone = 0, scanTotal = total)
			val autoFix = autoFixUseCaseProvider.get()
			val results = ArrayList<SmartReplaceProposal>(total)

			for ((idx, entity) in entities.withIndex()) {
				val baseManga = entity.toManga()
				val seed = runCatchingCancellable { autoFix.loadSeed(baseManga.id) }.getOrDefault(baseManga)
				val sameKindTargets = priorityTargets.filterNot { it == seed.source.name }
				val candidates = runCatchingCancellable {
					autoFix.findCandidates(seed, sameKindTargets)
				}.getOrDefault(emptyList())
					.filter { it.source.isNovelSource == seed.source.isNovelSource }
				val working = runCatchingCancellable { autoFix.pickWorking(candidates) }.getOrNull()

				results += SmartReplaceProposal(
					seed = seed,
					seedSourceTitle = seed.source.getTitle(context),
					seedChaptersCount = seed.chaptersCount(),
					workingReplacement = working,
					replacementSourceTitle = working?.source?.getTitle(context),
					replacementChaptersCount = working?.chaptersCount() ?: 0,
					alternateCandidates = candidates,
					isSelected = working != null,
				)
				proposalsStateFlow.value = proposalsStateFlow.value.copy(
					scanDone = idx + 1,
					scanTotal = total,
					proposals = results.toList(),
				)
			}
			proposalsStateFlow.value = proposalsStateFlow.value.copy(isScanning = false)
		}
	}

	fun cancelScan() {
		scanJob?.cancel()
		proposalsStateFlow.update { it.copy(isScanning = false) }
	}

	fun toggleProposalSelected(mangaId: Long) {
		proposalsStateFlow.update { bundle ->
			bundle.copy(
				proposals = bundle.proposals.map { p ->
					if (p.seed.id == mangaId && p.workingReplacement != null && !p.isApplied) {
						p.copy(isSelected = !p.isSelected)
					} else {
						p
					}
				},
			)
		}
	}

	fun selectCandidateForProposal(mangaId: Long, candidate: Manga) {
		proposalsStateFlow.update { bundle ->
			bundle.copy(
				proposals = bundle.proposals.map { p ->
					if (p.seed.id == mangaId && !p.isApplied) {
						p.copy(
							workingReplacement = candidate,
							replacementSourceTitle = candidate.source.getTitle(context),
							replacementChaptersCount = candidate.chaptersCount(),
							isSelected = true,
						)
					} else {
						p
					}
				},
			)
		}
	}

	fun confirmSingleProposal(proposal: SmartReplaceProposal) {
		val replacement = proposal.workingReplacement ?: return
		if (proposal.isApplied || proposalsStateFlow.value.isApplying) return
		viewModelScope.launch(Dispatchers.IO) {
			proposalsStateFlow.update { it.copy(isApplying = true) }
			val res = runCatchingCancellable {
				migrateUseCaseProvider.get().invoke(
					oldManga = proposal.seed,
					newManga = replacement,
					migrateProgress = true,
				)
			}
			proposalsStateFlow.update { bundle ->
				bundle.copy(
					isApplying = false,
					proposals = bundle.proposals.map { p ->
						if (p.seed.id == proposal.seed.id) {
							res.fold(
								onSuccess = { p.copy(isApplied = true, isSelected = false, errorMessage = null) },
								onFailure = { e -> p.copy(errorMessage = e.localizedMessage ?: "Migration failed") },
							)
						} else {
							p
						}
					},
				)
			}
			res.onSuccess {
				toastManager.show("Replaced ${proposal.seed.title}")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Migration failed")
			}
		}
	}

	fun openBatchConfirmDialog() {
		proposalsStateFlow.update { it.copy(showConfirmDialog = true) }
	}

	fun dismissBatchConfirmDialog() {
		proposalsStateFlow.update { it.copy(showConfirmDialog = false) }
	}

	fun confirmBatchMigration() {
		val toApply = proposalsStateFlow.value.proposals.filter {
			it.isSelected && !it.isApplied && it.workingReplacement != null
		}
		proposalsStateFlow.update { it.copy(showConfirmDialog = false) }
		if (toApply.isEmpty()) return

		viewModelScope.launch(Dispatchers.IO) {
			proposalsStateFlow.update { it.copy(isApplying = true) }
			val migrator = migrateUseCaseProvider.get()
			var okCount = 0
			for (item in toApply) {
				val replacement = item.workingReplacement ?: continue
				val res = runCatchingCancellable {
					migrator.invoke(
						oldManga = item.seed,
						newManga = replacement,
						migrateProgress = true,
					)
				}
				if (res.isSuccess) okCount++
				proposalsStateFlow.update { bundle ->
					bundle.copy(
						proposals = bundle.proposals.map { p ->
							if (p.seed.id == item.seed.id) {
								res.fold(
									onSuccess = { p.copy(isApplied = true, isSelected = false, errorMessage = null) },
									onFailure = { e -> p.copy(errorMessage = e.localizedMessage ?: "Migration failed") },
								)
							} else {
								p
							}
						},
					)
				}
			}
			proposalsStateFlow.update { it.copy(isApplying = false) }
			toastManager.show("Replaced $okCount of ${toApply.size} title(s)")
		}
	}

	private data class ProposalsBundle(
		val isScanning: Boolean = false,
		val scanDone: Int = 0,
		val scanTotal: Int = 0,
		val proposals: List<SmartReplaceProposal> = emptyList(),
		val isApplying: Boolean = false,
		val showConfirmDialog: Boolean = false,
	)
}
