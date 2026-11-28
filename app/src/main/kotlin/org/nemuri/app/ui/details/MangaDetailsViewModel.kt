package org.nemuri.app.ui.details

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.koitharu.kotatsu.ai.data.MangaAiRecapEntity
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.NemuriAiRouter
import org.koitharu.kotatsu.ai.domain.RecapDepth
import org.koitharu.kotatsu.ai.domain.WhereWasIRecapUseCase
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.BookmarksRepository
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.getLocalizedTitle
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.MangaIntent
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ChaptersSortOrder
import org.koitharu.kotatsu.core.prefs.DetailsUiMode
import org.koitharu.kotatsu.core.prefs.DuplicateWinnerStrategy
import org.koitharu.kotatsu.core.prefs.QuickScrollButtonsMode
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.domain.DetailsInteractor
import org.koitharu.kotatsu.details.domain.DetailsLoadUseCase
import org.koitharu.kotatsu.details.domain.RelatedMangaUseCase
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.favourites.domain.DuplicatesUseCase
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.domain.MangaDuplicate
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.roundToInt

@Immutable
data class BranchOption(
	val branch: String?,
	val label: String,
	val chaptersCount: Int,
)

@Immutable
data class ChapterRowItem(
	val chapter: MangaChapter,
	val title: String,
	val subtitle: String,
	val isDownloaded: Boolean,
	val isCurrent: Boolean,
	val isRead: Boolean,
	val isNew: Boolean,
	val isBookmarked: Boolean,
)

@Immutable
data class PendingDuplicatePrompt(
	val incoming: Manga,
	val targetCategory: FavouriteCategory,
	val duplicates: List<MangaDuplicate>,
	val incomingChaptersCount: Int?,
	val migrateProgressOnReplace: Boolean = true,
)

@Immutable
data class MangaTitleStats(
	val isLoading: Boolean = false,
	val totalDurationMs: Long = 0L,
	val totalPages: Int = 0,
	val totalChapters: Int = 0,
	val activeDays: Int = 0,
	val firstReadAt: Long = 0L,
	val recentDailyPages: List<Pair<Long, Int>> = emptyList(),
)

@Immutable
data class MangaDetailsUiState(
	val mangaId: Long = 0L,
	val manga: Manga? = null,
	val details: MangaDetails? = null,
	val title: String = "",
	val altTitle: String? = null,
	val coverUrl: String? = null,
	val backdropUrl: String? = null,
	val sourceTitle: String = "",
	val authorsLabel: String = "",
	val statusLabel: String = "",
	val formatBadge: String? = null,
	val ratingLabel: String? = null,
	val descriptionText: String = "",
	val publicUrl: String = "",
	val isLocalOnly: Boolean = false,
	val isNovel: Boolean = false,
	val isInLibrary: Boolean = false,
	val memberCategoryIds: Set<Long> = emptySet(),
	val allCategories: List<FavouriteCategory> = emptyList(),
	val history: MangaHistory? = null,
	val progressPercent: Float = 0f,
	val primaryActionLabel: String = "Start Reading",
	val primaryChapterId: Long? = null,
	val newChaptersCount: Int = 0,
	val branches: List<BranchOption> = emptyList(),
	val selectedBranch: String? = null,
	val totalBranchChapters: Int = 0,
	val downloadedCount: Int = 0,
	val chapters: List<ChapterRowItem> = emptyList(),
	val chapterQuery: String = "",
	val chaptersSortOrder: ChaptersSortOrder = ChaptersSortOrder.SOURCE,
	val isReversed: Boolean = false,
	val downloadedOnly: Boolean = false,
	val selectedChapterIds: Set<Long> = emptySet(),
	val relatedManga: List<Manga> = emptyList(),
	val isLoading: Boolean = true,
	val isRefreshing: Boolean = false,
	val errorMessage: String? = null,
	val pendingCloudFlare: CloudFlareProtectedException? = null,
	val pendingInteractive: InteractiveActionRequiredException? = null,
	val pendingDuplicatePrompt: PendingDuplicatePrompt? = null,
	val isReplacingDuplicate: Boolean = false,
	val titleStats: MangaTitleStats = MangaTitleStats(),
	val aiRecap: MangaAiRecapEntity? = null,
	val isGeneratingRecap: Boolean = false,
	val isRecapCardEnabled: Boolean = true,
	val aiModeLabel: String = "Auto",
	val activeAiEngineLabel: String = "Auto",
	val recapEngineBadgeLabel: String = "Auto",
	val recapDepth: RecapDepth = RecapDepth.DETAILED,
	val translatedSynopsis: String? = null,
	val isTranslatingSynopsis: Boolean = false,
	val showTranslatedSynopsis: Boolean = false,
	val isIncognitoActive: Boolean = false,
	val isSessionIncognito: Boolean = false,
	val incognitoForNsfw: TriStateOption = TriStateOption.ASK,
	val isAdultTitle: Boolean = false,
	val duplicateWinnerStrategy: DuplicateWinnerStrategy = DuplicateWinnerStrategy.MOST_CHAPTERS,
	val detailsUiMode: DetailsUiMode = DetailsUiMode.COMPACT,
	val quickScrollButtonsEnabled: Boolean = false,
	val currentChapterPosition: Int = 0,
	val unreadChaptersCount: Int = 0,
	val primaryChapterShortLabel: String? = null,
)

private data class SynopsisTranslationState(
	val translatedText: String? = null,
	val isTranslating: Boolean = false,
	val showTranslated: Boolean = false,
)

private data class DetailsChapterControls(
	val branch: String? = null,
	val query: String = "",
	val sortOrder: ChaptersSortOrder = ChaptersSortOrder.SOURCE,
	val downloadedOnly: Boolean = false,
)

private data class DetailsUserSnapshot(
	val history: MangaHistory?,
	val memberCategoryIds: Set<Long>,
	val allCategories: List<FavouriteCategory>,
	val newCount: Int,
	val bookmarkedChapterIds: Set<Long>,
	val isReversed: Boolean,
)

@HiltViewModel
class MangaDetailsViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val detailsLoadUseCase: DetailsLoadUseCase,
	private val detailsInteractor: DetailsInteractor,
	private val relatedMangaUseCase: RelatedMangaUseCase,
	private val favouritesRepository: FavouritesRepository,
	private val historyRepository: HistoryRepository,
	private val trackingRepository: TrackingRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val bookmarksRepository: BookmarksRepository,
	private val duplicatesUseCaseProvider: Provider<DuplicatesUseCase>,
	private val migrateUseCaseProvider: Provider<MigrateUseCase>,
	private val statsRepository: StatsRepository,
	private val databaseProvider: Provider<MangaDatabase>,
	private val recapUseCaseProvider: Provider<WhereWasIRecapUseCase>,
	private val aiRouterProvider: Provider<NemuriAiRouter>,
	@BaseHttpClient private val httpClient: OkHttpClient,
	@LocalStorageChanges private val localStorageChanges: SharedFlow<LocalManga?>,
	private val settings: AppSettings,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private var boundMangaId: Long = 0L
	private var loadJob: Job? = null
	private var auxiliaryJob: Job? = null

	private val rawDetailsFlow = MutableStateFlow<MangaDetails?>(null)
	private val controlsFlow = MutableStateFlow(DetailsChapterControls())
	private val selectedChaptersFlow = MutableStateFlow<Set<Long>>(emptySet())
	private val relatedFlow = MutableStateFlow<List<Manga>>(emptyList())
	private val loadingFlow = MutableStateFlow(true)
	private val refreshingFlow = MutableStateFlow(false)
	private val errorFlow = MutableStateFlow<String?>(null)
	private val cfFlow = MutableStateFlow<CloudFlareProtectedException?>(null)
	private val interactiveFlow = MutableStateFlow<InteractiveActionRequiredException?>(null)
	private val duplicatePromptFlow = MutableStateFlow<PendingDuplicatePrompt?>(null)
	private val replacingDuplicateFlow = MutableStateFlow(false)
	private val titleStatsFlow = MutableStateFlow(MangaTitleStats())
	private val generatingRecapFlow = MutableStateFlow(false)
	private val selectedRecapDepthFlow = MutableStateFlow<RecapDepth?>(null)
	private val depthPreviewRecapFlow = MutableStateFlow<MangaAiRecapEntity?>(null)
	private val activeAiEngineLabelFlow = MutableStateFlow(AiMode.fromKey(settings.aiMode).label)
	private val hasReadyLocalModelFlow = MutableStateFlow(false)
	private val translationStateFlow = MutableStateFlow(SynopsisTranslationState())
	private val sessionIncognitoFlow = MutableStateFlow(false)
	private var recapJob: Job? = null
	private var translateJob: Job? = null
	private var aiLabelJob: Job? = null
	private var autoSanitizedStaleRecapMangaId: Long = 0L

	private val _uiState = MutableStateFlow(
		MangaDetailsUiState(
			detailsUiMode = settings.detailsUiMode,
			quickScrollButtonsEnabled = settings.quickScrollButtonsMode != QuickScrollButtonsMode.OFF,
			activeAiEngineLabel = AiMode.fromKey(settings.aiMode).label,
			recapEngineBadgeLabel = AiMode.fromKey(settings.aiMode).label,
		),
	)
	val uiState: StateFlow<MangaDetailsUiState> = _uiState.asStateFlow()

	init {
		refreshActiveAiEngineLabel()
		viewModelScope.launch {
			localStorageChanges.collectLatest { downloaded ->
				rawDetailsFlow.update { current ->
					if (current == null) {
						null
					} else if (downloaded == null) {
						if (!current.isLocal && current.local?.file?.exists() == false) {
							current.copy(localManga = null)
						} else {
							current
						}
					} else {
						detailsInteractor.updateLocal(current, downloaded)
					}
				}
			}
		}
	}

	private fun refreshActiveAiEngineLabel() {
		aiLabelJob?.cancel()
		aiLabelJob = viewModelScope.launch(Dispatchers.IO) {
			val router = aiRouterProvider.get()
			runCatchingCancellable { router.sanitizeDeletedLocalModel() }
			val hasLocal = runCatchingCancellable { router.hasReadyLocalModel() }.getOrDefault(false)
			hasReadyLocalModelFlow.value = hasLocal
			val resolved = runCatchingCancellable { router.resolveActiveEngineDisplayLabel() }
				.getOrElse { AiMode.fromKey(settings.aiMode).label }
			activeAiEngineLabelFlow.value = resolved
		}
	}

	private fun isDeletedLocalEngineLabel(label: String, hasReadyLocal: Boolean): Boolean {
		if (hasReadyLocal) return false
		val lower = label.lowercase()
		return lower.startsWith("local ") || ".litertlm" in lower || ".gguf" in lower
	}

	fun setDetailsUiMode(mode: DetailsUiMode) {
		if (_uiState.value.detailsUiMode != mode) {
			_uiState.value = _uiState.value.copy(detailsUiMode = mode)
		}
		settings.detailsUiMode = mode
	}

	fun setQuickScrollButtonsEnabled(enabled: Boolean) {
		settings.quickScrollButtonsMode = if (enabled) {
			QuickScrollButtonsMode.LONG_LISTS_ONLY
		} else {
			QuickScrollButtonsMode.OFF
		}
	}

	fun bind(mangaId: Long, sourceName: String?) {
		if (boundMangaId == mangaId && rawDetailsFlow.value != null) {
			refreshActiveAiEngineLabel()
			return
		}
		boundMangaId = mangaId
		loadJob?.cancel()
		auxiliaryJob?.cancel()
		recapJob?.cancel()
		translateJob?.cancel()
		generatingRecapFlow.value = false
		selectedRecapDepthFlow.value = null
		depthPreviewRecapFlow.value = null
		autoSanitizedStaleRecapMangaId = 0L
		translationStateFlow.value = SynopsisTranslationState()
		sessionIncognitoFlow.value = false
		selectedChaptersFlow.value = emptySet()
		relatedFlow.value = emptyList()
		refreshActiveAiEngineLabel()

		val cachedManga = MangaTransferCache.get(mangaId)
		val savedOrder = settings.getChaptersSortOrder(mangaId)
		controlsFlow.value = DetailsChapterControls(sortOrder = savedOrder)
		if (cachedManga != null) {
			rawDetailsFlow.value = MangaDetails(cachedManga).withChaptersOrder(savedOrder)
		} else {
			rawDetailsFlow.value = null
		}

		startObserveCombined(mangaId)
		loadDetails(mangaId = mangaId, cachedManga = cachedManga, force = false)

		viewModelScope.launch(Dispatchers.IO) {
			rawDetailsFlow
				.filter { it?.isLoaded == true }
				.mapLatest { details ->
					val manga = details?.toManga() ?: return@mapLatest emptyList()
					if (!settings.isRelatedMangaEnabled) {
						emptyList()
					} else {
						relatedMangaUseCase(manga).orEmpty().distinctBy { it.id }
					}
				}
				.flowOn(Dispatchers.IO)
				.catch { }
				.collectLatest { related ->
					relatedFlow.value = related
				}
		}
	}

	fun retry(force: Boolean = true) {
		val id = boundMangaId
		if (id == 0L) return
		loadDetails(mangaId = id, cachedManga = rawDetailsFlow.value?.toManga() ?: MangaTransferCache.get(id), force = force)
	}

	fun setChapterQuery(query: String) {
		controlsFlow.update { it.copy(query = query) }
	}

	fun selectBranch(branch: String?) {
		controlsFlow.update { it.copy(branch = branch) }
		selectedChaptersFlow.value = emptySet()
	}

	fun setChaptersSortOrder(order: ChaptersSortOrder) {
		val id = boundMangaId
		if (id != 0L) {
			settings.setChaptersSortOrder(id, order)
		}
		controlsFlow.update { it.copy(sortOrder = order) }
		rawDetailsFlow.update { it?.withChaptersOrder(order) }
	}

	fun toggleChaptersReversed() {
		settings.isChaptersReverse = !settings.isChaptersReverse
	}

	fun toggleDownloadedOnly() {
		controlsFlow.update { it.copy(downloadedOnly = !it.downloadedOnly) }
	}

	fun toggleChapterSelection(chapterId: Long) {
		selectedChaptersFlow.update { current ->
			if (chapterId in current) current - chapterId else current + chapterId
		}
	}

	fun selectAllChapters() {
		val ids = uiState.value.chapters.mapTo(HashSet()) { it.chapter.id }
		selectedChaptersFlow.value = ids
	}

	fun invertChapterSelection() {
		val allIds = uiState.value.chapters.map { it.chapter.id }
		val current = selectedChaptersFlow.value
		selectedChaptersFlow.value = allIds.filterTo(HashSet()) { it !in current }
	}

	fun selectChaptersBelow() {
		val orderedIds = uiState.value.chapters.map { it.chapter.id }
		val selected = selectedChaptersFlow.value
		if (orderedIds.isEmpty() || selected.isEmpty()) return
		val anchorIdx = orderedIds.indexOfFirst { it in selected }
		if (anchorIdx < 0) return
		selectedChaptersFlow.value = selected + orderedIds.subList(anchorIdx, orderedIds.size)
	}

	fun selectUnreadChapters() {
		selectedChaptersFlow.value = uiState.value.chapters
			.filter { !it.isRead }
			.mapTo(HashSet()) { it.chapter.id }
	}

	fun selectChapterRange(ids: Set<Long>) {
		selectedChaptersFlow.value = ids
	}

	fun toggleChapterRead(row: ChapterRowItem) {
		val details = rawDetailsFlow.value ?: return
		val branch = uiState.value.selectedBranch
		val branchChapters = details.chapters[branch] ?: details.allChapters
		val idx = branchChapters.indexOfFirst { it.id == row.chapter.id }
		if (idx < 0 || branchChapters.isEmpty()) return
		val manga = details.toManga()
		viewModelScope.launch(Dispatchers.IO) {
			if (row.isRead || row.isCurrent) {
				if (idx == 0 && row.isCurrent) {
					historyRepository.delete(manga)
				} else {
					val targetPercent = (idx.toFloat() / branchChapters.size.toFloat()).coerceIn(0f, 1f)
					historyRepository.addOrUpdate(
						manga = manga,
						chapterId = row.chapter.id,
						page = 0,
						scroll = 0,
						percent = targetPercent,
						force = true,
					)
				}
				toastManager.show("Marked \"${chapterDisplayName(row.chapter)}\" unread")
			} else {
				val nextChapter = branchChapters.getOrNull(idx + 1) ?: row.chapter
				val targetPercent = ((idx + 1).toFloat() / branchChapters.size.toFloat()).coerceIn(0f, 1f)
				historyRepository.addOrUpdate(
					manga = manga,
					chapterId = nextChapter.id,
					page = 0,
					scroll = 0,
					percent = targetPercent,
					force = true,
				)
				toastManager.show("Marked \"${chapterDisplayName(row.chapter)}\" read")
			}
		}
	}

	fun clearChapterSelection() {
		selectedChaptersFlow.value = emptySet()
	}

	fun quickToggleLibrary() {
		val manga = rawDetailsFlow.value?.toManga() ?: return
		val memberIds = uiState.value.memberCategoryIds
		val allCategories = uiState.value.allCategories
		viewModelScope.launch(Dispatchers.IO) {
			if (memberIds.isNotEmpty()) {
				val handle = favouritesRepository.removeFromFavourites(listOf(manga.id))
				toastManager.show(
					message = "Removed from library",
					actionLabel = "Undo",
					onAction = { handle.reverseAsync() },
				)
			} else {
				val targetCategory = allCategories.firstOrNull { it.id == settings.defaultFavouriteCategoryId }
					?: allCategories.firstOrNull()
					?: favouritesRepository.createCategory(
						title = "Reading",
						sortOrder = settings.defaultCategorySortOrder,
						isTrackerEnabled = true,
						isNewChaptersDownloadEnabled = false,
						isVisibleOnShelf = true,
					)
				addWithDuplicateCheck(manga, targetCategory)
			}
		}
	}

	fun toggleCategoryMembership(category: FavouriteCategory) {
		val manga = rawDetailsFlow.value?.toManga() ?: return
		val memberIds = uiState.value.memberCategoryIds
		val isMember = category.id in memberIds
		viewModelScope.launch(Dispatchers.IO) {
			if (isMember) {
				favouritesRepository.removeFromCategory(category.id, listOf(manga.id))
				toastManager.show("Removed from ${category.title}")
			} else if (memberIds.isEmpty()) {
				addWithDuplicateCheck(manga, category)
			} else {
				favouritesRepository.addToCategory(category.id, listOf(manga))
				toastManager.show("Added to ${category.title}")
			}
		}
	}

	private suspend fun addWithDuplicateCheck(manga: Manga, category: FavouriteCategory) {
		if (settings.isDuplicateCheckEnabled) {
			val useCase = duplicatesUseCaseProvider.get()
			val duplicates = runCatchingCancellable { useCase(manga) }
				.getOrDefault(emptyList())
				.filterNot { settings.isDuplicatePairIgnored(manga.id, it.manga.id) }
			if (duplicates.isNotEmpty()) {
				val incomingCount = rawDetailsFlow.value?.allChapters?.size?.takeIf { it > 0 }
					?: useCase.getLocalChaptersCount(manga)
				duplicatePromptFlow.value = PendingDuplicatePrompt(
					incoming = manga,
					targetCategory = category,
					duplicates = duplicates,
					incomingChaptersCount = incomingCount,
					migrateProgressOnReplace = settings.isDuplicateProgressMigrated,
				)
				return
			}
		}
		favouritesRepository.addToCategory(category.id, listOf(manga))
		toastManager.show("Added to ${category.title}")
	}

	fun dismissDuplicatePrompt() {
		duplicatePromptFlow.value = null
	}

	fun addDespiteDuplicates() {
		val prompt = duplicatePromptFlow.value ?: return
		duplicatePromptFlow.value = null
		viewModelScope.launch(Dispatchers.IO) {
			favouritesRepository.addToCategory(prompt.targetCategory.id, listOf(prompt.incoming))
			toastManager.show("Added to ${prompt.targetCategory.title}")
		}
	}

	fun keepBothAndIgnoreDuplicates() {
		val prompt = duplicatePromptFlow.value ?: return
		duplicatePromptFlow.value = null
		viewModelScope.launch(Dispatchers.IO) {
			prompt.duplicates.forEach { dup ->
				settings.ignoreDuplicatePair(prompt.incoming.id, dup.manga.id)
			}
			favouritesRepository.addToCategory(prompt.targetCategory.id, listOf(prompt.incoming))
			toastManager.show("Kept both & ignored duplicate pair")
		}
	}

	fun toggleHeaderIncognito() {
		if (settings.isIncognitoModeEnabled) {
			settings.isIncognitoModeEnabled = false
			sessionIncognitoFlow.value = false
			toastManager.show("Global Incognito disabled")
		} else {
			val next = !sessionIncognitoFlow.value
			sessionIncognitoFlow.value = next
			toastManager.show(if (next) "Incognito active for this session" else "Session Incognito disabled")
		}
	}

	fun setIncognitoForNsfw(option: TriStateOption) {
		settings.incognitoModeForNsfw = option
	}

	fun toggleSynopsisTranslation() {
		val cur = translationStateFlow.value
		if (cur.isTranslating) return
		if (!cur.translatedText.isNullOrBlank()) {
			translationStateFlow.update { it.copy(showTranslated = !it.showTranslated) }
			return
		}
		val rawSynopsis = uiState.value.descriptionText.trim()
		if (rawSynopsis.isBlank()) return

		translateJob?.cancel()
		translateJob = viewModelScope.launch(Dispatchers.IO) {
			translationStateFlow.update { it.copy(isTranslating = true) }
			val appLang = if (!settings.appLocales.isEmpty) settings.appLocales[0]?.language else null
			val targetLang = appLang?.takeIf { it.isNotBlank() }
				?: java.util.Locale.getDefault().language.ifBlank { "en" }

			val primaryHttp = runCatchingCancellable {
				val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${Uri.encode(targetLang)}&dt=t&q=${Uri.encode(rawSynopsis)}"
				val req = Request.Builder()
					.url(url)
					.header("User-Agent", "Mozilla/5.0")
					.get()
					.build()
				httpClient.newCall(req).execute().use { resp ->
					if (!resp.isSuccessful) error("HTTP ${resp.code}")
					val body = resp.body?.string().orEmpty()
					val sentences = JSONArray(body).getJSONArray(0)
					val sb = StringBuilder()
					for (i in 0 until sentences.length()) {
						val chunk = sentences.optJSONArray(i)?.optString(0)
						if (!chunk.isNullOrEmpty()) sb.append(chunk)
					}
					sb.toString().trim().takeIf { it.isNotBlank() } ?: error("Empty translation")
				}
			}.getOrNull()

			val resolvedText = primaryHttp ?: run {
				val aiRouter = aiRouterProvider.get()
				if (aiRouter.isAiEnabled()) {
					val targetDisplay = java.util.Locale.forLanguageTag(targetLang).displayLanguage.ifBlank { targetLang }
					aiRouter.generate(
						systemPrompt = "You are a precise manga and novel synopsis translator. Translate the synopsis naturally and accurately into $targetDisplay ($targetLang). Output ONLY the translated synopsis text.",
						userPrompt = rawSynopsis,
						maxTokens = 512,
					).getOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
				} else {
					null
				}
			}

			if (!resolvedText.isNullOrBlank()) {
				translationStateFlow.value = SynopsisTranslationState(
					translatedText = resolvedText,
					isTranslating = false,
					showTranslated = true,
				)
			} else {
				translationStateFlow.update { it.copy(isTranslating = false) }
				toastManager.show("Translation unavailable right now")
			}
		}
	}

	fun replaceDuplicate(existing: MangaDuplicate) {
		val prompt = duplicatePromptFlow.value ?: return
		if (!existing.canReplace || replacingDuplicateFlow.value) return
		duplicatePromptFlow.value = null
		viewModelScope.launch(Dispatchers.IO) {
			replacingDuplicateFlow.value = true
			val result = runCatchingCancellable {
				migrateUseCaseProvider.get().invoke(
					oldManga = existing.manga,
					newManga = prompt.incoming,
					migrateProgress = settings.isDuplicateProgressMigrated,
				)
			}
			replacingDuplicateFlow.value = false
			result.onSuccess {
				toastManager.show("Replaced ${existing.manga.title} from ${existing.manga.source.getTitle(context)}")
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to replace existing entry")
			}
		}
	}

	fun loadMangaStats() {
		val id = boundMangaId
		if (id == 0L) return
		viewModelScope.launch(Dispatchers.IO) {
			titleStatsFlow.value = MangaTitleStats(isLoading = true)
			val sessions = runCatchingCancellable {
				databaseProvider.get().getStatsDao().findAll(id)
			}.getOrDefault(emptyList())
			val timeline = runCatchingCancellable {
				statsRepository.getMangaTimeline(id)
			}.getOrDefault(java.util.TreeMap())
			val totalDuration = sessions.sumOf { it.duration }
			val totalPages = sessions.sumOf { it.pages }
			val totalChapters = sessions.sumOf { it.chapters }
			val firstReadAt = sessions.minOfOrNull { it.startedAt } ?: 0L
			val dailyList = timeline.entries.toList().takeLast(14).map { it.key to it.value }
			titleStatsFlow.value = MangaTitleStats(
				isLoading = false,
				totalDurationMs = totalDuration,
				totalPages = totalPages,
				totalChapters = totalChapters,
				activeDays = timeline.size,
				firstReadAt = firstReadAt,
				recentDailyPages = dailyList,
			)
		}
	}

	fun setRecapDepth(depth: RecapDepth) {
		selectedRecapDepthFlow.value = depth
		settings.aiRecapDepth = depth.key
		val details = rawDetailsFlow.value
		val state = uiState.value
		if (details != null && state.history != null) {
			val branchChapters = details.chapters[state.selectedBranch] ?: details.allChapters
			val orderedAscending = if (branchChapters.size >= 2 && branchChapters.first().number > branchChapters.last().number) {
				branchChapters.asReversed()
			} else {
				branchChapters
			}
			val currentChapterId = state.history.chapterId
			val rawIndex = orderedAscending.indexOfFirst { it.id == currentChapterId }
			val effectiveIndex = when {
				orderedAscending.isEmpty() -> -1
				rawIndex >= 0 -> rawIndex
				else -> 0
			}
			val readCount = if (effectiveIndex >= 0) effectiveIndex + 1 else 0
			val targetChapterId = if (rawIndex >= 0) orderedAscending[rawIndex].id else currentChapterId
			val cachedForDepth = recapUseCaseProvider.get().getCachedForDepth(
				mangaId = details.toManga().id,
				chapterId = targetChapterId,
				readCount = readCount,
				depth = depth,
			)
			if (cachedForDepth != null) {
				depthPreviewRecapFlow.value = cachedForDepth
			}
			generateRecap(forceRefresh = false, depthOverride = depth)
		}
	}

	fun selectRecapDepth(depth: RecapDepth) = setRecapDepth(depth)

	fun generateRecap(forceRefresh: Boolean = false, depthOverride: RecapDepth? = null) {
		val details = rawDetailsFlow.value ?: return
		val state = uiState.value
		val manga = details.toManga()
		val branchChapters = details.chapters[state.selectedBranch] ?: details.allChapters
		val orderedAscending = if (branchChapters.size >= 2 && branchChapters.first().number > branchChapters.last().number) {
			branchChapters.asReversed()
		} else {
			branchChapters
		}
		val requestedDepth = depthOverride ?: state.recapDepth
		recapJob?.cancel()
		recapJob = viewModelScope.launch(Dispatchers.IO) {
			generatingRecapFlow.value = true
			runCatchingCancellable {
				val router = aiRouterProvider.get()
				router.sanitizeDeletedLocalModel()
				val hasLocal = router.hasReadyLocalModel()
				hasReadyLocalModelFlow.value = hasLocal
				activeAiEngineLabelFlow.value = runCatchingCancellable {
					router.resolveActiveEngineDisplayLabel()
				}.getOrElse { AiMode.fromKey(settings.aiMode).label }
				recapUseCaseProvider.get().generateRecap(
					manga = manga,
					orderedChapters = orderedAscending,
					currentChapterId = state.history?.chapterId,
					descriptionText = details.description,
					forceRefresh = forceRefresh,
					depthOverride = requestedDepth,
				)
			}.onSuccess { generated ->
				depthPreviewRecapFlow.value = generated
			}.onFailure { err ->
				toastManager.show(err.localizedMessage ?: "Failed to build recap")
			}
			generatingRecapFlow.value = false
		}
	}

	fun clearRecap() {
		val id = boundMangaId
		if (id == 0L) return
		depthPreviewRecapFlow.value = null
		viewModelScope.launch(Dispatchers.IO) {
			recapUseCaseProvider.get().clearCache(id)
		}
	}

	fun createAndAddCategory(title: String) {
		val trimmed = title.trim()
		if (trimmed.isEmpty()) return
		val manga = rawDetailsFlow.value?.toManga()
		val memberIds = uiState.value.memberCategoryIds
		viewModelScope.launch(Dispatchers.IO) {
			val created = favouritesRepository.createCategory(
				title = trimmed,
				sortOrder = settings.defaultCategorySortOrder,
				isTrackerEnabled = true,
				isNewChaptersDownloadEnabled = false,
				isVisibleOnShelf = true,
			)
			if (manga != null) {
				if (memberIds.isEmpty()) {
					addWithDuplicateCheck(manga, created)
				} else {
					favouritesRepository.addToCategory(created.id, listOf(manga))
					toastManager.show("Added to \"${created.title}\"")
				}
			} else {
				toastManager.show("Created category \"${created.title}\"")
			}
		}
	}

	fun downloadChapters(chapterIds: Collection<Long>?) {
		val details = rawDetailsFlow.value ?: return
		if (details.isLocal) {
			toastManager.show("Already stored locally")
			return
		}
		val idsArray = chapterIds?.toLongArray()
		if (chapterIds != null && idsArray != null && idsArray.isEmpty()) {
			toastManager.show("No chapters to download")
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			val cleanManga = details.toManga().copy(chapters = null)
			val meteredAllowed = settings.allowDownloadOnMeteredNetwork != TriStateOption.DISABLED
			downloadScheduler.schedule(
				listOf(
					cleanManga to DownloadTask(
						mangaId = cleanManga.id,
						isPaused = false,
						isSilent = false,
						chaptersIds = idsArray,
						destination = null,
						format = null,
						allowMeteredNetwork = meteredAllowed,
					),
				),
			)
			selectedChaptersFlow.value = emptySet()
			toastManager.show(
				if (idsArray == null) {
					"Downloading all chapters…"
				} else {
					"Queued ${idsArray.size} chapter(s)"
				},
			)
		}
	}

	fun downloadUnreadChapters() {
		val rows = uiState.value.chapters
		val ids = rows.filter { !it.isRead && !it.isDownloaded }.map { it.chapter.id }
		if (ids.isEmpty()) {
			toastManager.show("No unread remote chapters remaining")
		} else {
			downloadChapters(ids)
		}
	}

	fun downloadNextChapters(count: Int) {
		val rows = uiState.value.chapters
		val orderedChronological = if (uiState.value.isReversed) rows.asReversed() else rows
		val currentIndex = orderedChronological.indexOfFirst { it.isCurrent }.coerceAtLeast(-1)
		val candidates = orderedChronological
			.drop(currentIndex + 1)
			.filter { !it.isDownloaded }
			.take(count)
			.map { it.chapter.id }
		if (candidates.isEmpty()) {
			toastManager.show("All upcoming chapters are already downloaded")
		} else {
			downloadChapters(candidates)
		}
	}

	fun deleteDownloadedChapters(chapterIds: Set<Long>) {
		val details = rawDetailsFlow.value ?: return
		val localManga = details.local?.manga ?: return
		if (chapterIds.isEmpty()) return
		viewModelScope.launch(Dispatchers.IO) {
			val deleted = runCatchingCancellable {
				localMangaRepository.deleteChapters(localManga, chapterIds)
			}.getOrDefault(false)
			selectedChaptersFlow.value = emptySet()
			if (deleted) {
				toastManager.show("Deleted ${chapterIds.size} downloaded chapter(s)")
				retry(force = true)
			} else {
				toastManager.show("Could not remove downloaded chapters")
			}
		}
	}

	fun markChapterAsCurrent(chapter: MangaChapter) {
		val details = rawDetailsFlow.value ?: return
		val branch = uiState.value.selectedBranch
		val branchChapters = details.chapters[branch] ?: details.allChapters
		val index = branchChapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
		val percent = if (branchChapters.isNotEmpty()) {
			((index + 1).toFloat() / branchChapters.size.toFloat()).coerceIn(0f, 1f)
		} else {
			1f
		}
		viewModelScope.launch(Dispatchers.IO) {
			historyRepository.addOrUpdate(
				manga = details.toManga(),
				chapterId = chapter.id,
				page = 0,
				scroll = 0,
				percent = percent,
				force = true,
			)
			toastManager.show("Marked \"${chapterDisplayName(chapter)}\" as current")
		}
	}

	fun clearNewChapterBadges() {
		val id = boundMangaId
		if (id == 0L) return
		viewModelScope.launch(Dispatchers.IO) {
			trackingRepository.clearUpdates(listOf(id))
			toastManager.show("Cleared new chapter badges")
		}
	}

	suspend fun resolveChapterBrowserUrl(chapter: MangaChapter): String? {
		val details = rawDetailsFlow.value ?: return null
		val onlineManga = details.sourceManga
		return runCatchingCancellable {
			val repo = mangaRepositoryFactory.create(onlineManga.source)
			val onlineChapter = onlineManga.chapters?.firstOrNull { it.id == chapter.id } ?: chapter
			repo.getChapterUrl(onlineManga, onlineChapter)
		}.getOrNull() ?: onlineManga.publicUrl.takeIf { it.isNotBlank() }
	}

	private fun loadDetails(mangaId: Long, cachedManga: Manga?, force: Boolean) {
		loadJob?.cancel()
		val savedState = SavedStateHandle(
			buildMap {
				put(AppRouter.KEY_ID, mangaId)
				if (cachedManga != null) {
					put(AppRouter.KEY_MANGA, ParcelableManga(cachedManga))
				}
			},
		)
		val intent = MangaIntent(savedState)

		loadJob = viewModelScope.launch(Dispatchers.IO) {
			val hasExisting = rawDetailsFlow.value != null
			loadingFlow.value = !hasExisting
			refreshingFlow.value = hasExisting
			errorFlow.value = null
			cfFlow.value = null
			interactiveFlow.value = null

			try {
				detailsLoadUseCase.invoke(intent, force = force).collect { loaded ->
					val ordered = loaded.withChaptersOrder(controlsFlow.value.sortOrder)
					rawDetailsFlow.value = ordered
					MangaTransferCache.put(ordered.toManga())
					loadingFlow.value = false
				}
				refreshingFlow.value = false
			} catch (e: CloudFlareProtectedException) {
				cfFlow.value = e
				errorFlow.value = e.getDisplayMessage(context.resources)
				loadingFlow.value = false
				refreshingFlow.value = false
			} catch (e: InteractiveActionRequiredException) {
				interactiveFlow.value = e
				errorFlow.value = e.getDisplayMessage(context.resources)
				loadingFlow.value = false
				refreshingFlow.value = false
			} catch (e: Throwable) {
				errorFlow.value = e.getDisplayMessage(context.resources)
				loadingFlow.value = false
				refreshingFlow.value = false
			}
		}
	}

	private fun startObserveCombined(mangaId: Long) {
		val settingsPrefFlow = settings.observeChanges()
			.filter {
				it == null ||
					it == AppSettings.KEY_REVERSE_CHAPTERS ||
					it == AppSettings.KEY_INCOGNITO_MODE ||
					it == AppSettings.KEY_INCOGNITO_NSFW ||
					it == AppSettings.KEY_DUPLICATE_WINNER_STRATEGY ||
					it == AppSettings.KEY_DETAILS_UI ||
					it == AppSettings.KEY_QUICK_SCROLL_BUTTONS
			}
			.onStart { emit(null) }

		val bookmarksFlow = rawDetailsFlow.flatMapLatest { details ->
			if (details != null) {
				bookmarksRepository.observeBookmarks(details.toManga()).catch { emit(emptyList<Bookmark>()) }
			} else {
				flowOf(emptyList())
			}
		}

		val userSnapshotFlow = combine(
			historyRepository.observeOne(mangaId),
			favouritesRepository.observeCategories(mangaId),
			favouritesRepository.observeCategoriesForLibrary(),
			detailsInteractor.observeNewChapters(mangaId).catch { emit(0) },
			bookmarksFlow,
		) { history, memberCats, allCats, newCount, bookmarks ->
			DetailsUserSnapshot(
				history = history,
				memberCategoryIds = memberCats.mapTo(HashSet()) { it.id },
				allCategories = allCats,
				newCount = newCount,
				bookmarkedChapterIds = bookmarks.mapTo(HashSet()) { it.chapterId },
				isReversed = settings.isChaptersReverse,
			)
		}.combine(settingsPrefFlow) { snapshot, _ ->
			snapshot.copy(isReversed = settings.isChaptersReverse)
		}

		val aiPrefFlow = settings.observeChanges()
			.filter {
				it == null ||
					it == AppSettings.KEY_AI_MODE ||
					it == AppSettings.KEY_AI_SHOW_RECAP_CARD ||
					it == AppSettings.KEY_AI_RECAP_DEPTH ||
					it == AppSettings.KEY_AI_LOCAL_MODEL_ID ||
					it == AppSettings.KEY_AI_GEMINI_MODEL ||
					it == AppSettings.KEY_AI_REMOTE_URL ||
					it == AppSettings.KEY_AI_REMOTE_MODEL
			}
			.onStart { emit(null) }

		val recapSourcesFlow = combine(
			recapUseCaseProvider.get().observeCached(mangaId).catch { emit(null) },
			depthPreviewRecapFlow,
			generatingRecapFlow,
			selectedRecapDepthFlow,
			aiPrefFlow,
		) { dbRecap, previewRecap, isGen, selectedDepth, changedKey ->
			if (changedKey != null) {
				refreshActiveAiEngineLabel()
			}
			val effectiveDepth = selectedDepth
				?: dbRecap?.parsedDepth()
				?: RecapDepth.fromKey(settings.aiRecapDepth)
			val effectiveRecap = when {
				selectedDepth != null && previewRecap != null && previewRecap.parsedDepth() == selectedDepth -> previewRecap
				else -> dbRecap
			}
			RecapMetaBundle(
				recap = effectiveRecap,
				isGenerating = isGen,
				isCardEnabled = settings.isAiShowRecapCard,
				aiModeLabel = AiMode.fromKey(settings.aiMode).label,
				activeAiEngineLabel = activeAiEngineLabelFlow.value,
				recapEngineBadgeLabel = activeAiEngineLabelFlow.value,
				recapDepth = effectiveDepth,
			)
		}

		val recapBundleFlow = combine(
			recapSourcesFlow,
			activeAiEngineLabelFlow,
			hasReadyLocalModelFlow,
		) { bundle, activeEngineLabel, hasReadyLocal ->
			val rawLabel = bundle.recap?.displayEngineLabel()
			val isGhostLocal = rawLabel != null && isDeletedLocalEngineLabel(rawLabel, hasReadyLocal)
			val sanitizedBadge = if (rawLabel.isNullOrBlank() || isGhostLocal) {
				activeEngineLabel
			} else {
				rawLabel
			}
			if (isGhostLocal &&
				bundle.recap != null &&
				!bundle.isGenerating &&
				autoSanitizedStaleRecapMangaId != mangaId &&
				rawDetailsFlow.value != null
			) {
				autoSanitizedStaleRecapMangaId = mangaId
				generateRecap(forceRefresh = true, depthOverride = bundle.recapDepth)
			}
			bundle.copy(
				activeAiEngineLabel = activeEngineLabel,
				recapEngineBadgeLabel = sanitizedBadge,
			)
		}

		val metaFlow = combine(
			relatedFlow,
			loadingFlow,
			refreshingFlow,
			errorFlow,
			cfFlow,
		) { related, isLoading, isRefreshing, error, cf ->
			Triple(related, isLoading to isRefreshing, error to cf)
		}.combine(interactiveFlow) { triple, interactive ->
			MetaBundle(
				related = triple.first,
				isLoading = triple.second.first,
				isRefreshing = triple.second.second,
				error = triple.third.first,
				cf = triple.third.second,
				interactive = interactive,
			)
		}.combine(duplicatePromptFlow) { meta, dupPrompt ->
			meta.copy(duplicatePrompt = dupPrompt)
		}.combine(replacingDuplicateFlow) { meta, replacing ->
			meta.copy(isReplacingDuplicate = replacing)
		}.combine(titleStatsFlow) { meta, titleStats ->
			meta.copy(titleStats = titleStats)
		}.combine(recapBundleFlow) { meta, recapBundle ->
			meta.copy(
				aiRecap = recapBundle.recap,
				isGeneratingRecap = recapBundle.isGenerating,
				isRecapCardEnabled = recapBundle.isCardEnabled,
				aiModeLabel = recapBundle.aiModeLabel,
				activeAiEngineLabel = recapBundle.activeAiEngineLabel,
				recapEngineBadgeLabel = recapBundle.recapEngineBadgeLabel,
				recapDepth = recapBundle.recapDepth,
			)
		}.combine(translationStateFlow) { meta, trans ->
			meta.copy(translationState = trans)
		}.combine(sessionIncognitoFlow) { meta, sessionIncog ->
			meta.copy(isSessionIncognito = sessionIncog)
		}.combine(settingsPrefFlow) { meta, _ ->
			meta.copy(
				isGlobalIncognito = settings.isIncognitoModeEnabled,
				incognitoForNsfw = settings.incognitoModeForNsfw,
				winnerStrategy = settings.duplicateWinnerStrategy,
				detailsUiMode = settings.detailsUiMode,
				quickScrollButtonsEnabled = settings.quickScrollButtonsMode != QuickScrollButtonsMode.OFF,
			)
		}

		auxiliaryJob = viewModelScope.launch {
			combine(
				rawDetailsFlow,
				controlsFlow,
				userSnapshotFlow,
				selectedChaptersFlow,
				metaFlow,
			) { details, controls, user, selectedIds, meta ->
				val manga = details?.toManga()
				val chaptersMap = details?.chapters.orEmpty()
				val availableBranches = chaptersMap.entries.map { (branch, list) ->
					BranchOption(
						branch = branch,
						label = branch ?: "Default",
						chaptersCount = list.size,
					)
				}
				val resolvedBranch = when {
					chaptersMap.isEmpty() -> null
					controls.branch != null && chaptersMap.containsKey(controls.branch) -> controls.branch
					chaptersMap.size > 1 -> manga?.getPreferredBranch(user.history).takeIf { chaptersMap.containsKey(it) }
						?: chaptersMap.keys.firstOrNull()
					else -> chaptersMap.keys.firstOrNull()
				}
				val branchChapters = (chaptersMap[resolvedBranch] ?: details?.allChapters.orEmpty()).distinctBy { it.id }
				val currentChapterId = user.history?.chapterId
				val currentIndex = branchChapters.indexOfFirst { it.id == currentChapterId }
				val newStartIndex = if (user.newCount > 0 && branchChapters.size >= user.newCount) {
					branchChapters.size - user.newCount
				} else {
					Int.MAX_VALUE
				}

				val trimmedQuery = controls.query.trim()
				val mappedRows = branchChapters.mapIndexedNotNull { index, ch ->
					val downloaded = ch.source == LocalMangaSource
					if (controls.downloadedOnly && !downloaded) {
						return@mapIndexedNotNull null
					}
					val displayName = chapterDisplayName(ch)
					if (trimmedQuery.isNotEmpty() &&
						!displayName.contains(trimmedQuery, ignoreCase = true) &&
						ch.scanlator?.contains(trimmedQuery, ignoreCase = true) != true
					) {
						return@mapIndexedNotNull null
					}
					val dateLabel = if (ch.uploadDate > 0L) {
						DateUtils.getRelativeTimeSpanString(
							ch.uploadDate,
							System.currentTimeMillis(),
							DateUtils.DAY_IN_MILLIS,
							DateUtils.FORMAT_ABBREV_RELATIVE,
						).toString()
					} else {
						null
					}
					val subtitle = listOfNotNull(
						ch.scanlator?.takeIf { it.isNotBlank() },
						dateLabel,
						if (downloaded) "Offline" else null,
					).joinToString(" • ")

					ChapterRowItem(
						chapter = ch,
						title = displayName,
						subtitle = subtitle,
						isDownloaded = downloaded,
						isCurrent = ch.id == currentChapterId,
						isRead = currentIndex >= 0 && index < currentIndex,
						isNew = index >= newStartIndex,
						isBookmarked = ch.id in user.bookmarkedChapterIds,
					)
				}

				val orderedRows = if (user.isReversed) mappedRows.asReversed() else mappedRows
				val progress = user.history?.percent?.takeIf { ReadingProgress.isValid(it) } ?: 0f
				val primaryChapter = when {
					currentChapterId != null && branchChapters.any { it.id == currentChapterId } -> {
						branchChapters.first { it.id == currentChapterId }
					}
					else -> branchChapters.firstOrNull()
				}
				val primaryLabel = when {
					primaryChapter == null -> "No Chapters"
					currentChapterId != null -> {
						val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
						val suffix = if (pct > 0) " ($pct%)" else ""
						"Continue ${chapterDisplayName(primaryChapter)}$suffix"
					}
					else -> "Start Reading"
				}

				val descRaw = details?.description?.toString().orEmpty()
				val cleanDescription = if (descRaw.isBlank()) {
					""
				} else {
					HtmlCompat.fromHtml(descRaw, HtmlCompat.FROM_HTML_MODE_COMPACT)
						.toString()
						.trim()
				}

				val formatBadge = when {
					manga == null -> null
					manga.isEpub -> "EPUB"
					manga.url.endsWith(".pdf", ignoreCase = true) -> "PDF"
					details.isLocal -> "LOCAL"
					manga.source.isNovelSource -> "NOVEL"
					else -> null
				}

				val statusLabel = when (manga?.state?.name) {
					"ONGOING" -> "Ongoing"
					"FINISHED" -> "Completed"
					"ABANDONED" -> "Abandoned"
					"PAUSED" -> "On Hiatus"
					"UPCOMING" -> "Upcoming"
					"RESTRICTED" -> "Restricted"
					else -> ""
				}

				val ratingText = manga?.takeIf { it.hasRating }?.let { m ->
					String.format("%.1f ★", (m.rating * 5f).coerceIn(0f, 5f))
				}

				val isAdultTitle = manga != null && (
					manga.isNsfw() || manga.tags.any { tag ->
						val lower = tag.title.trim().lowercase(java.util.Locale.ROOT)
						lower == "18+" || lower == "r18" || lower == "r-18" ||
							lower.contains("adult") || lower.contains("erotica") ||
							lower.contains("pornographic") || lower.contains("smut") ||
							lower.contains("hentai")
					}
					)

				val currentChapterPosition = if (currentIndex >= 0) currentIndex + 1 else 0
				val unreadChaptersCount = if (currentIndex >= 0) {
					(branchChapters.size - (currentIndex + 1)).coerceAtLeast(0)
				} else {
					branchChapters.size
				}
				val primaryChapterShortLabel = primaryChapter?.let { ch ->
					when {
						ch.number > 0f && ch.number % 1f == 0f -> "Ch. ${ch.number.toInt()}"
						ch.number > 0f -> "Ch. ${ch.number.toBigDecimal().stripTrailingZeros().toPlainString()}"
						else -> chapterDisplayName(ch)
					}
				}

				MangaDetailsUiState(
					mangaId = mangaId,
					manga = manga,
					details = details,
					title = manga?.title.orEmpty(),
					altTitle = manga?.altTitle?.takeIf { it.isNotBlank() && it != manga.title },
					coverUrl = details?.coverUrl(preferLarge = !settings.isBackdropEnabled) ?: manga?.coverUrl,
					backdropUrl = details?.backdropUrl ?: manga?.largeCoverUrl ?: manga?.coverUrl,
					sourceTitle = manga?.source?.getTitle(context).orEmpty(),
					authorsLabel = manga?.authors?.joinToString(", ").orEmpty(),
					statusLabel = statusLabel,
					formatBadge = formatBadge,
					ratingLabel = ratingText,
					descriptionText = cleanDescription,
					publicUrl = details?.sourceManga?.publicUrl.orEmpty(),
					isLocalOnly = details?.isLocal == true,
					isNovel = manga?.source?.isNovelSource == true || manga?.isEpub == true,
					isInLibrary = user.memberCategoryIds.isNotEmpty(),
					memberCategoryIds = user.memberCategoryIds,
					allCategories = user.allCategories,
					history = user.history,
					progressPercent = progress,
					primaryActionLabel = primaryLabel,
					primaryChapterId = primaryChapter?.id,
					newChaptersCount = user.newCount,
					branches = availableBranches,
					selectedBranch = resolvedBranch,
					totalBranchChapters = branchChapters.size,
					downloadedCount = branchChapters.count { it.source == LocalMangaSource },
					chapters = orderedRows,
					chapterQuery = controls.query,
					chaptersSortOrder = controls.sortOrder,
					isReversed = user.isReversed,
					downloadedOnly = controls.downloadedOnly,
					selectedChapterIds = selectedIds,
					relatedManga = meta.related,
					isLoading = meta.isLoading && details == null,
					isRefreshing = meta.isRefreshing,
					errorMessage = meta.error,
					pendingCloudFlare = meta.cf,
					pendingInteractive = meta.interactive,
					pendingDuplicatePrompt = meta.duplicatePrompt,
					isReplacingDuplicate = meta.isReplacingDuplicate,
					titleStats = meta.titleStats,
					aiRecap = meta.aiRecap,
					isGeneratingRecap = meta.isGeneratingRecap,
					isRecapCardEnabled = meta.isRecapCardEnabled,
					aiModeLabel = meta.aiModeLabel,
					activeAiEngineLabel = meta.activeAiEngineLabel,
					recapEngineBadgeLabel = meta.recapEngineBadgeLabel,
					recapDepth = meta.recapDepth,
					translatedSynopsis = meta.translationState.translatedText,
					isTranslatingSynopsis = meta.translationState.isTranslating,
					showTranslatedSynopsis = meta.translationState.showTranslated,
					isIncognitoActive = meta.isGlobalIncognito || meta.isSessionIncognito,
					isSessionIncognito = meta.isSessionIncognito,
					incognitoForNsfw = meta.incognitoForNsfw,
					isAdultTitle = isAdultTitle,
					duplicateWinnerStrategy = meta.winnerStrategy,
					detailsUiMode = meta.detailsUiMode,
					quickScrollButtonsEnabled = meta.quickScrollButtonsEnabled,
					currentChapterPosition = currentChapterPosition,
					unreadChaptersCount = unreadChaptersCount,
					primaryChapterShortLabel = primaryChapterShortLabel,
				)
			}.flowOn(Dispatchers.Default)
				.collect { state ->
					_uiState.value = state
				}
		}
	}

	private fun chapterDisplayName(chapter: MangaChapter): String =
		chapter.getLocalizedTitle(context.resources).ifBlank {
			chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.number.toInt()}"
		}

	private data class MetaBundle(
		val related: List<Manga>,
		val isLoading: Boolean,
		val isRefreshing: Boolean,
		val error: String?,
		val cf: CloudFlareProtectedException?,
		val interactive: InteractiveActionRequiredException?,
		val duplicatePrompt: PendingDuplicatePrompt? = null,
		val isReplacingDuplicate: Boolean = false,
		val titleStats: MangaTitleStats = MangaTitleStats(),
		val aiRecap: MangaAiRecapEntity? = null,
		val isGeneratingRecap: Boolean = false,
		val isRecapCardEnabled: Boolean = true,
		val aiModeLabel: String = "Auto",
		val activeAiEngineLabel: String = "Auto",
		val recapEngineBadgeLabel: String = "Auto",
		val recapDepth: RecapDepth = RecapDepth.DETAILED,
		val translationState: SynopsisTranslationState = SynopsisTranslationState(),
		val isSessionIncognito: Boolean = false,
		val isGlobalIncognito: Boolean = false,
		val incognitoForNsfw: TriStateOption = TriStateOption.ASK,
		val winnerStrategy: DuplicateWinnerStrategy = DuplicateWinnerStrategy.MOST_CHAPTERS,
		val detailsUiMode: DetailsUiMode = DetailsUiMode.COMPACT,
		val quickScrollButtonsEnabled: Boolean = false,
	)

	private data class RecapMetaBundle(
		val recap: MangaAiRecapEntity?,
		val isGenerating: Boolean,
		val isCardEnabled: Boolean,
		val aiModeLabel: String,
		val activeAiEngineLabel: String,
		val recapEngineBadgeLabel: String,
		val recapDepth: RecapDepth,
	)
}
