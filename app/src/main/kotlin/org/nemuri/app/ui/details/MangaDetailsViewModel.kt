package org.nemuri.app.ui.details

import android.content.Context
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
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.BookmarksRepository
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.getLocalizedTitle
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.MangaIntent
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ChaptersSortOrder
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.core.ui.util.reverseAsync
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.domain.DetailsInteractor
import org.koitharu.kotatsu.details.domain.DetailsLoadUseCase
import org.koitharu.kotatsu.details.domain.RelatedMangaUseCase
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject
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

	private val _uiState = MutableStateFlow(MangaDetailsUiState())
	val uiState: StateFlow<MangaDetailsUiState> = _uiState.asStateFlow()

	init {
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

	fun bind(mangaId: Long, sourceName: String?) {
		if (boundMangaId == mangaId && rawDetailsFlow.value != null) {
			return
		}
		boundMangaId = mangaId
		loadJob?.cancel()
		auxiliaryJob?.cancel()
		selectedChaptersFlow.value = emptySet()
		relatedFlow.value = emptyList()

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
						relatedMangaUseCase(manga).orEmpty()
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
				favouritesRepository.addToCategory(targetCategory.id, listOf(manga))
				toastManager.show("Added to ${targetCategory.title}")
			}
		}
	}

	fun toggleCategoryMembership(category: FavouriteCategory) {
		val manga = rawDetailsFlow.value?.toManga() ?: return
		val isMember = category.id in uiState.value.memberCategoryIds
		viewModelScope.launch(Dispatchers.IO) {
			if (isMember) {
				favouritesRepository.removeFromCategory(category.id, listOf(manga.id))
				toastManager.show("Removed from ${category.title}")
			} else {
				favouritesRepository.addToCategory(category.id, listOf(manga))
				toastManager.show("Added to ${category.title}")
			}
		}
	}

	fun createAndAddCategory(title: String) {
		val trimmed = title.trim()
		if (trimmed.isEmpty()) return
		val manga = rawDetailsFlow.value?.toManga()
		viewModelScope.launch(Dispatchers.IO) {
			val created = favouritesRepository.createCategory(
				title = trimmed,
				sortOrder = settings.defaultCategorySortOrder,
				isTrackerEnabled = true,
				isNewChaptersDownloadEnabled = false,
				isVisibleOnShelf = true,
			)
			if (manga != null) {
				favouritesRepository.addToCategory(created.id, listOf(manga))
				toastManager.show("Added to \"${created.title}\"")
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
		val reversedPrefFlow = settings.observeChanges()
			.filter { it == null || it == AppSettings.KEY_REVERSE_CHAPTERS }
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
		}.combine(reversedPrefFlow) { snapshot, _ ->
			snapshot.copy(isReversed = settings.isChaptersReverse)
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
				val branchChapters = chaptersMap[resolvedBranch] ?: details?.allChapters.orEmpty()
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
					manga.source.isNovelSource() -> "NOVEL"
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
					isNovel = manga?.source?.isNovelSource() == true || manga?.isEpub == true,
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
	)
}
