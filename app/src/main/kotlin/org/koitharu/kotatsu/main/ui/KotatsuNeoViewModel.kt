package org.koitharu.kotatsu.main.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.ai.AiLibraryEngine
import org.koitharu.kotatsu.backups.BackupFormat
import org.koitharu.kotatsu.backups.BackupRestoreSummary
import org.koitharu.kotatsu.backups.UniversalBackupRepository
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.model.AdvancedSearchFilter
import org.koitharu.kotatsu.core.model.AppNavTab
import org.koitharu.kotatsu.core.model.BatchDeduplicationRule
import org.koitharu.kotatsu.core.model.BrokenMangaFixProposal
import org.koitharu.kotatsu.core.model.DuplicateCandidatePair
import org.koitharu.kotatsu.core.model.ExtensionInstallStatus
import org.koitharu.kotatsu.core.model.ExtensionInstallTask
import org.koitharu.kotatsu.core.model.ExternalExtensionInfo
import org.koitharu.kotatsu.core.model.FeedUpdateItem
import org.koitharu.kotatsu.core.model.LibraryDisplayConfig
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.model.LibrarySortOrder
import org.koitharu.kotatsu.core.model.NavBarStyle
import org.koitharu.kotatsu.core.model.ReaderCustomizationConfig
import org.koitharu.kotatsu.core.parser.KotatsuLibraryRepository
import org.koitharu.kotatsu.core.parser.SqliteEngineDiagnostics
import org.koitharu.kotatsu.core.ai.NemuriAiCoreEngine
import org.koitharu.kotatsu.core.sync.ChapterDownloadTask
import org.koitharu.kotatsu.core.sync.KotatsuSyncAndDownloadCoordinator
import org.koitharu.kotatsu.details.ai.RecapUiState
import org.koitharu.kotatsu.details.ai.WhereWasIRecapUseCase
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.extensions.ExternalExtensionManager
import org.koitharu.kotatsu.search.ai.LibrarySmartQueryAst
import org.koitharu.kotatsu.search.ai.NaturalLanguageLibrarySearchUseCase
import java.io.ByteArrayInputStream

enum class QuickLibraryFilter(val label: String) {
	ALL("All Series"),
	UNREAD("Unread"),
	DOWNLOADED("Downloaded"),
	NEEDS_AI_FIX("AI Fix-It"),
	HAS_DUPLICATE("Duplicates"),
}

data class CheckAndSwapPromptState(
	val candidate: ExtensionCatalogManga,
	val existingMatch: LibraryMangaItem,
	val similarityScore: Float,
)

class KotatsuNeoViewModel(application: Application) : AndroidViewModel(application) {

	companion object {
		const val MIN_NAV_BAR_TABS = 2
		const val MAX_NAV_BAR_TABS = 5
	}

	private val db = MangaDatabase.getInstance(application)
	private val libraryRepo = KotatsuLibraryRepository(db)
	private val extensionManager = ExternalExtensionManager(application, db)
	private val aiEngine = AiLibraryEngine(db, extensionManager)
	private val backupRepo = UniversalBackupRepository(db)
	val nemuriAiCoreEngine = NemuriAiCoreEngine()
	val whereWasIRecapUseCase = WhereWasIRecapUseCase(db, nemuriAiCoreEngine)
	val nlSearchUseCase = NaturalLanguageLibrarySearchUseCase(db, nemuriAiCoreEngine)

	private val _displayConfig = MutableStateFlow(LibraryDisplayConfig())
	val displayConfig: StateFlow<LibraryDisplayConfig> = _displayConfig.asStateFlow()

	private val _readerConfig = MutableStateFlow(ReaderCustomizationConfig())
	val readerConfig: StateFlow<ReaderCustomizationConfig> = _readerConfig.asStateFlow()

	private val _navBarStyle = MutableStateFlow(NavBarStyle.FLOATING_ISLAND)
	val navBarStyle: StateFlow<NavBarStyle> = _navBarStyle.asStateFlow()

	private val _enabledNavTabs = MutableStateFlow(
		listOf(
			AppNavTab.LIBRARY,
			AppNavTab.HISTORY,
			AppNavTab.FEED,
			AppNavTab.EXPLORE,
			AppNavTab.AI_STUDIO,
		),
	)
	val enabledNavTabs: StateFlow<List<AppNavTab>> = _enabledNavTabs.asStateFlow()

	private val _searchQuery = MutableStateFlow("")
	val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

	private val _advancedFilter = MutableStateFlow(AdvancedSearchFilter())
	val advancedFilter: StateFlow<AdvancedSearchFilter> = _advancedFilter.asStateFlow()

	private val _ftsMatchedIds = MutableStateFlow<Set<Long>?>(null)
	private val _ftsSearchLatencyMicros = MutableStateFlow(180L)
	val ftsSearchLatencyMicros: StateFlow<Long> = _ftsSearchLatencyMicros.asStateFlow()

	private val _activeSmartQueryAst = MutableStateFlow<LibrarySmartQueryAst?>(null)
	val activeSmartQueryAst: StateFlow<LibrarySmartQueryAst?> = _activeSmartQueryAst.asStateFlow()

	private val _activeMangaRecapState = MutableStateFlow<RecapUiState>(RecapUiState.Hidden)
	val activeMangaRecapState: StateFlow<RecapUiState> = _activeMangaRecapState.asStateFlow()

	private val _selectedCategoryId = MutableStateFlow<Long?>(null)
	val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

	private val _quickFilter = MutableStateFlow(QuickLibraryFilter.ALL)
	val quickFilter: StateFlow<QuickLibraryFilter> = _quickFilter.asStateFlow()

	private val _selectedMangaDetailId = MutableStateFlow<Long?>(null)
	val selectedMangaDetailId: StateFlow<Long?> = _selectedMangaDetailId.asStateFlow()

	private val _activeReaderMangaId = MutableStateFlow<Long?>(null)
	val activeReaderMangaId: StateFlow<Long?> = _activeReaderMangaId.asStateFlow()

	private val _activeChapterIndex = MutableStateFlow(0)
	val activeChapterIndex: StateFlow<Int> = _activeChapterIndex.asStateFlow()

	private val _activePageIndex = MutableStateFlow(0)
	val activePageIndex: StateFlow<Int> = _activePageIndex.asStateFlow()

	private val _batchRule = MutableStateFlow(BatchDeduplicationRule.MOST_CHAPTERS)
	val batchRule: StateFlow<BatchDeduplicationRule> = _batchRule.asStateFlow()

	private val _checkAndSwapPrompt = MutableStateFlow<CheckAndSwapPromptState?>(null)
	val checkAndSwapPrompt: StateFlow<CheckAndSwapPromptState?> = _checkAndSwapPrompt.asStateFlow()

	private val _lastBackupSummary = MutableStateFlow<BackupRestoreSummary?>(null)
	val lastBackupSummary: StateFlow<BackupRestoreSummary?> = _lastBackupSummary.asStateFlow()

	private val _lastExportedPreview = MutableStateFlow<Pair<BackupFormat, String>?>(null)
	val lastExportedPreview: StateFlow<Pair<BackupFormat, String>?> = _lastExportedPreview.asStateFlow()

	private val _sqliteDiagnostics = MutableStateFlow(SqliteEngineDiagnostics())
	val sqliteDiagnostics: StateFlow<SqliteEngineDiagnostics> = _sqliteDiagnostics.asStateFlow()

	private val _darkTheme = MutableStateFlow(true)
	val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

	private val _themeMode = MutableStateFlow(org.koitharu.kotatsu.core.model.AppThemeMode.DARK)
	val themeMode: StateFlow<org.koitharu.kotatsu.core.model.AppThemeMode> = _themeMode.asStateFlow()

	private val _colorSchemePreset = MutableStateFlow(org.koitharu.kotatsu.core.model.AppColorSchemePreset.KOTATSU_TEAL)
	val colorSchemePreset: StateFlow<org.koitharu.kotatsu.core.model.AppColorSchemePreset> = _colorSchemePreset.asStateFlow()

	private val _pureOledBlack = MutableStateFlow(false)
	val pureOledBlack: StateFlow<Boolean> = _pureOledBlack.asStateFlow()

	private val _statusBanner = MutableStateFlow<String?>(null)
	val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

	private val _discoveredExtensions = MutableStateFlow<List<ExternalExtensionInfo>>(emptyList())
	val discoveredExtensions: StateFlow<List<ExternalExtensionInfo>> = _discoveredExtensions.asStateFlow()

	private val _extensionInstallTasks = MutableStateFlow<Map<String, ExtensionInstallTask>>(emptyMap())
	val extensionInstallTasks: StateFlow<Map<String, ExtensionInstallTask>> = _extensionInstallTasks.asStateFlow()

	val downloadQueue: StateFlow<List<ChapterDownloadTask>> = KotatsuSyncAndDownloadCoordinator.downloadQueue

	private val _extensionCatalog = MutableStateFlow(extensionManager.getExtensionCatalogItems())
	val extensionCatalogFlow: StateFlow<List<ExtensionCatalogManga>> = _extensionCatalog.asStateFlow()
	val extensionCatalog: List<ExtensionCatalogManga>
		get() = _extensionCatalog.value

	val categories: StateFlow<List<FavouriteCategoryEntity>> = db.getFavouritesDao().observeCategories()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val extensionRepos: StateFlow<List<ExtensionRepoEntity>> = db.getAuxiliaryDao().observeExtensionRepos()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	private val rawLibraryItems: StateFlow<List<LibraryMangaItem>> = libraryRepo.observeLibraryItems()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val availableTags: StateFlow<List<String>> = rawLibraryItems.map { items ->
		items.flatMap { item -> item.tags.map { it.title } }
			.distinct()
			.sorted()
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val availableSources: StateFlow<List<String>> = rawLibraryItems.map { items ->
		items.map { it.manga.source }
			.distinct()
			.sorted()
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	private val ignoredDuplicatePairs = db.getAuxiliaryDao().observeIgnoredDuplicates()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val duplicateCandidates: StateFlow<List<DuplicateCandidatePair>> = combine(
		rawLibraryItems,
		ignoredDuplicatePairs,
	) { items, ignored ->
		val ignoredSet = ignored.map { it.mangaIdA to it.mangaIdB }.toSet()
		aiEngine.detectDuplicates(items, ignoredSet)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val brokenMangaProposals: StateFlow<List<BrokenMangaFixProposal>> = rawLibraryItems.combine(
		_discoveredExtensions,
	) { items, _ ->
		aiEngine.diagnoseBrokenLibraryItems(items)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val historyItems: StateFlow<List<LibraryMangaItem>> = rawLibraryItems.map { items ->
		items.filter { it.lastReadAt > 0L || it.lastReadChapterNumber > 0f }
			.sortedByDescending { it.lastReadAt }
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val feedUpdates: StateFlow<List<FeedUpdateItem>> = rawLibraryItems.map { items ->
		val now = System.currentTimeMillis()
		val dayMs = 86_400_000L
		val updates = ArrayList<FeedUpdateItem>(items.size * 3)
		for (item in items) {
			val totalCh = item.chapters.size
			val startIdx = (totalCh - 3).coerceAtLeast(0)
			for (chIndex in totalCh - 1 downTo startIdx) {
				val ch = item.chapters[chIndex]
				val ts = if (ch.uploadDate > 0L) ch.uploadDate else now - (chIndex * 3_600_000L)
				val ageDays = ((now - ts).coerceAtLeast(0L)) / dayMs
				val bucket = when {
					ageDays == 0L -> "Today"
					ageDays == 1L -> "Yesterday"
					else -> "Earlier This Week"
				}
				updates.add(
					FeedUpdateItem(
						id = "${item.manga.id}_${ch.chapterId}",
						mangaItem = item,
						chapter = ch,
						chapterIndex = chIndex,
						timestampMillis = ts,
						dateBucketLabel = bucket,
						isNewRelease = !ch.isRead,
					),
				)
			}
		}
		updates.sortedByDescending { it.timestampMillis }
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val filteredLibraryItems: StateFlow<List<LibraryMangaItem>> = combine(
		rawLibraryItems,
		_displayConfig,
		_selectedCategoryId,
		_quickFilter,
		combine(_ftsMatchedIds, _advancedFilter, duplicateCandidates) { fts, adv, dups -> Triple(fts, adv, dups) },
	) { items, config, catId, qFilter, (ftsIds, adv, dups) ->
		val dupIds = if (qFilter == QuickLibraryFilter.HAS_DUPLICATE || QuickLibraryFilter.HAS_DUPLICATE.name in adv.excludedQuickFilters) {
			dups.flatMap { listOf(it.primary.manga.id, it.secondary.manga.id) }.toSet()
		} else {
			emptySet()
		}

		var filtered = items.filter { item ->
			val matchesCategory = catId == null || catId in item.categoryIds
			val matchesFts = ftsIds == null || item.manga.id in ftsIds
			val isBrokenItem = item.manga.isBroken ||
				item.manga.source.startsWith("DEAD:") ||
				item.manga.source.startsWith("MIHON_UNMAPPED_")
			val matchesQuick = when (qFilter) {
				QuickLibraryFilter.ALL -> true
				QuickLibraryFilter.UNREAD -> item.unreadCount > 0
				QuickLibraryFilter.DOWNLOADED -> item.downloadedCount > 0
				QuickLibraryFilter.NEEDS_AI_FIX -> isBrokenItem
				QuickLibraryFilter.HAS_DUPLICATE -> item.manga.id in dupIds
			}
			val matchesExcludedQuick = adv.excludedQuickFilters.none { excludedName ->
				when (excludedName) {
					QuickLibraryFilter.UNREAD.name -> item.unreadCount > 0
					QuickLibraryFilter.DOWNLOADED.name -> item.downloadedCount > 0
					QuickLibraryFilter.NEEDS_AI_FIX.name -> isBrokenItem
					QuickLibraryFilter.HAS_DUPLICATE.name -> item.manga.id in dupIds
					else -> false
				}
			}

			val itemTagNames = item.tags.map { it.title }.toSet()
			val matchesIncludedTags = adv.includedTags.isEmpty() || adv.includedTags.all { it in itemTagNames }
			val matchesExcludedTags = adv.excludedTags.isEmpty() || adv.excludedTags.none { it in itemTagNames }
			val matchesLimitedCats = adv.limitedCategoryIds.isEmpty() || item.categoryIds.any { it in adv.limitedCategoryIds }
			val matchesSources = adv.selectedSources.isEmpty() || item.manga.source in adv.selectedSources
			val isFinished = item.manga.state.equals("FINISHED", ignoreCase = true)
			val matchesCompleted = when (adv.completionState) {
				org.koitharu.kotatsu.list.ui.CompletionFilterState.ALL -> !adv.onlyCompleted || isFinished
				org.koitharu.kotatsu.list.ui.CompletionFilterState.COMPLETED_ONLY -> isFinished
				org.koitharu.kotatsu.list.ui.CompletionFilterState.ONGOING_ONLY -> !isFinished
			}
			val matchesRating = item.manga.rating >= adv.minimumRating

			matchesCategory &&
				matchesFts &&
				matchesQuick &&
				matchesExcludedQuick &&
				matchesIncludedTags &&
				matchesExcludedTags &&
				matchesLimitedCats &&
				matchesSources &&
				matchesCompleted &&
				matchesRating
		}

		val comparator = when (config.sortOrder) {
			LibrarySortOrder.LAST_READ -> compareByDescending<LibraryMangaItem> { it.lastReadAt }
			LibrarySortOrder.ALPHABETICAL -> compareBy<LibraryMangaItem> { it.manga.title.lowercase() }
			LibrarySortOrder.UNREAD_COUNT -> compareByDescending<LibraryMangaItem> { it.unreadCount }
			LibrarySortOrder.DATE_ADDED -> compareByDescending<LibraryMangaItem> { it.addedAt }
			LibrarySortOrder.LATEST_CHAPTER -> compareByDescending<LibraryMangaItem> { it.totalChapters }
			LibrarySortOrder.RATING -> compareByDescending<LibraryMangaItem> { it.manga.rating }
			LibrarySortOrder.PROGRESS -> compareByDescending<LibraryMangaItem> { it.progressPercent }
		}

		filtered = filtered.sortedWith(comparator)
		if (config.sortAscending && config.sortOrder != LibrarySortOrder.ALPHABETICAL) {
			filtered = filtered.reversed()
		} else if (!config.sortAscending && config.sortOrder == LibrarySortOrder.ALPHABETICAL) {
			filtered = filtered.reversed()
		}
		filtered
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val allLibraryItems: StateFlow<List<LibraryMangaItem>> = rawLibraryItems

	private val prefs = application.getSharedPreferences("kotatsu_neo_prefs", android.content.Context.MODE_PRIVATE)

	init {
		// Restore persisted Appearance, Navigation, Library Display & Reader settings
		runCatching {
			val savedThemeMode = prefs.getString("theme_mode", null)
			if (savedThemeMode != null) {
				val mode = org.koitharu.kotatsu.core.model.AppThemeMode.valueOf(savedThemeMode)
				_themeMode.value = mode
				if (mode == org.koitharu.kotatsu.core.model.AppThemeMode.LIGHT) _darkTheme.value = false
				if (mode == org.koitharu.kotatsu.core.model.AppThemeMode.DARK) _darkTheme.value = true
			}
			val savedPreset = prefs.getString("color_preset", null)
			if (savedPreset != null) {
				_colorSchemePreset.value = org.koitharu.kotatsu.core.model.AppColorSchemePreset.valueOf(savedPreset)
			}
			_pureOledBlack.value = prefs.getBoolean("pure_oled_black", false)
			val savedNavStyle = prefs.getString("nav_bar_style", null)
			if (savedNavStyle != null) {
				_navBarStyle.value = NavBarStyle.valueOf(savedNavStyle)
			}
			val savedLayout = prefs.getString("lib_layout_mode", null)
			val savedSort = prefs.getString("lib_sort_order", null)
			val savedCols = prefs.getInt("lib_grid_cols", _displayConfig.value.gridColumns)
			val savedCheckSwap = prefs.getBoolean("lib_check_swap", _displayConfig.value.checkAndSwapOnAdd)
			_displayConfig.update { cur ->
				cur.copy(
					layoutMode = savedLayout?.let { org.koitharu.kotatsu.core.model.LibraryLayoutMode.valueOf(it) } ?: cur.layoutMode,
					sortOrder = savedSort?.let { LibrarySortOrder.valueOf(it) } ?: cur.sortOrder,
					gridColumns = savedCols.coerceIn(2, 6),
					checkAndSwapOnAdd = savedCheckSwap,
				)
			}
			val savedBookmarks = prefs.getStringSet("bookmarked_page_keys", null)
			if (savedBookmarks != null) {
				_bookmarkedPageKeys.value = savedBookmarks.toSet()
			}
		}
		viewModelScope.launch {
			libraryRepo.ensureSeeded()
			org.koitharu.kotatsu.core.os.pruneOrphanedShortcutIcons(application)
			refreshDiagnostics()
		}
		viewModelScope.launch {
			extensionRepos.collect { repos ->
				_discoveredExtensions.value = extensionManager.discoverExtensions(repos)
			}
		}
		viewModelScope.launch {
			db.getAuxiliaryDao().observePreferences().collect { prefsList ->
				val hydrated = prefsList.associate { pref ->
					val mode = org.koitharu.kotatsu.core.model.ReaderStyleMode.entries.getOrElse(pref.mode) {
						org.koitharu.kotatsu.core.model.ReaderStyleMode.PAGER_RTL
					}
					val upscale = org.koitharu.kotatsu.core.model.AiUpscaleMode.entries.getOrElse(pref.aiUpscaleMode) {
						_readerConfig.value.aiUpscaleMode
					}
					pref.mangaId to _readerConfig.value.copy(
						readingMode = mode,
						brightnessDelta = pref.cfBrightness,
						contrastDelta = pref.cfContrast,
						invertColors = pref.cfInvert,
						grayscale = pref.cfGrayscale,
						aiUpscaleMode = upscale,
					)
				}
				if (hydrated.isNotEmpty()) {
					_seriesReaderProfiles.update { existing -> existing + hydrated }
				}
			}
		}
	}

	fun updateSearchQuery(query: String) {
		_searchQuery.value = query
		viewModelScope.launch {
			if (query.isBlank()) {
				_ftsMatchedIds.value = null
				_activeSmartQueryAst.value = null
			} else {
				val fastAst = nlSearchUseCase.parseFastHeuristic(query)
				val wordCount = query.trim().split(Regex("""\s+""")).size
				if (fastAst.hasStructuredConstraints || wordCount >= 3) {
					_activeSmartQueryAst.value = fastAst
					val startNanos = System.nanoTime()
					val stage1Matches = runCatching {
						nlSearchUseCase.executeSmartSearch(fastAst).map { it.id }.toSet()
					}.getOrDefault(emptySet())
					val fallbackFts = if (stage1Matches.isEmpty()) {
						libraryRepo.searchMangaIdsFast(query).first
					} else {
						stage1Matches
					}
					_ftsMatchedIds.value = fallbackFts
					_ftsSearchLatencyMicros.value = ((System.nanoTime() - startNanos) / 1_000L).coerceAtLeast(1L)

					// Stage 2: Asynchronous Gemini Nano AICore refinement
					val nanoAst = nlSearchUseCase.parseWithGeminiNano(query)
					if (_searchQuery.value == query && nanoAst != fastAst) {
						_activeSmartQueryAst.value = nanoAst
						val stage2Matches = runCatching {
							nlSearchUseCase.executeSmartSearch(nanoAst).map { it.id }.toSet()
						}.getOrDefault(fallbackFts)
						if (stage2Matches.isNotEmpty() || nanoAst.hasStructuredConstraints) {
							_ftsMatchedIds.value = stage2Matches
						}
					}
				} else {
					_activeSmartQueryAst.value = null
					val (ids, micros) = libraryRepo.searchMangaIdsFast(query)
					_ftsMatchedIds.value = ids
					_ftsSearchLatencyMicros.value = micros
				}
			}
		}
	}

	fun updateAdvancedFilter(transform: (AdvancedSearchFilter) -> AdvancedSearchFilter) {
		_advancedFilter.update(transform)
	}

	fun setCompletionFilterState(state: org.koitharu.kotatsu.list.ui.CompletionFilterState) {
		_advancedFilter.update {
			it.copy(
				completionState = state,
				onlyCompleted = state == org.koitharu.kotatsu.list.ui.CompletionFilterState.COMPLETED_ONLY,
			)
		}
	}

	fun markFeedChapterRead(mangaId: Long, chapterId: Long, totalChapters: Int) {
		viewModelScope.launch {
			libraryRepo.markChapterReadFromFeed(mangaId, chapterId, totalChapters)
			postBanner("Chapter marked as read with accurate series progress.")
		}
	}

	fun clearAdvancedFilter() {
		_advancedFilter.value = AdvancedSearchFilter()
	}

	fun selectCategory(categoryId: Long?) {
		_selectedCategoryId.value = categoryId
	}

	fun selectQuickFilter(filter: QuickLibraryFilter) {
		if (filter == QuickLibraryFilter.ALL) {
			_quickFilter.value = QuickLibraryFilter.ALL
			_advancedFilter.update { it.copy(excludedQuickFilters = emptySet()) }
			return
		}
		val currentIncluded = _quickFilter.value == filter
		val currentExcluded = filter.name in _advancedFilter.value.excludedQuickFilters
		when {
			currentIncluded -> {
				// Tap 2: Transition from Include -> Exclude
				_quickFilter.value = QuickLibraryFilter.ALL
				_advancedFilter.update { it.copy(excludedQuickFilters = it.excludedQuickFilters + filter.name) }
			}
			currentExcluded -> {
				// Tap 3: Transition from Exclude -> Reset
				_advancedFilter.update { it.copy(excludedQuickFilters = it.excludedQuickFilters - filter.name) }
			}
			else -> {
				// Tap 1: Transition from Neutral -> Include
				_quickFilter.value = filter
				_advancedFilter.update { it.copy(excludedQuickFilters = it.excludedQuickFilters - filter.name) }
			}
		}
	}

	fun updateDisplayConfig(transform: (LibraryDisplayConfig) -> LibraryDisplayConfig) {
		_displayConfig.update { current ->
			val next = transform(current)
			prefs.edit()
				.putString("lib_layout_mode", next.layoutMode.name)
				.putString("lib_sort_order", next.sortOrder.name)
				.putInt("lib_grid_cols", next.gridColumns)
				.putBoolean("lib_check_swap", next.checkAndSwapOnAdd)
				.apply()
			next
		}
	}

	fun updateReaderConfig(transform: (ReaderCustomizationConfig) -> ReaderCustomizationConfig) {
		_readerConfig.update(transform)
	}

	fun setNavBarStyle(style: NavBarStyle) {
		_navBarStyle.value = style
		prefs.edit().putString("nav_bar_style", style.name).apply()
	}

	fun toggleNavTab(tab: AppNavTab) {
		if (tab == AppNavTab.LIBRARY) {
			postBanner("Library is the primary landing screen and remains pinned.")
			return
		}
		val current = _enabledNavTabs.value.toMutableList()
		if (tab in current) {
			if (current.size <= MIN_NAV_BAR_TABS) {
				postBanner("Navigation bar requires at least $MIN_NAV_BAR_TABS destinations.")
				return
			}
			current.remove(tab)
			_enabledNavTabs.value = current
		} else {
			if (current.size >= MAX_NAV_BAR_TABS) {
				postBanner("Hard limit reached ($MAX_NAV_BAR_TABS icons max). Remove an icon first.")
				return
			}
			current.add(tab)
			_enabledNavTabs.value = current
		}
	}

	fun moveNavTab(tab: AppNavTab, moveLeft: Boolean) {
		val current = _enabledNavTabs.value.toMutableList()
		val idx = current.indexOf(tab)
		if (idx == -1) return
		val targetIdx = if (moveLeft) idx - 1 else idx + 1
		if (targetIdx !in current.indices) return
		val swapped = current[targetIdx]
		current[targetIdx] = tab
		current[idx] = swapped
		_enabledNavTabs.value = current
	}

	fun openMangaDetails(mangaId: Long?) {
		_selectedMangaDetailId.value = mangaId
		if (mangaId == null) {
			_activeMangaRecapState.value = RecapUiState.Hidden
		} else {
			requestWhereWasIRecap(mangaId = mangaId, forceShow = false, forceRegenerate = false)
		}
	}

	fun requestWhereWasIRecap(
		mangaId: Long,
		forceShow: Boolean = true,
		forceRegenerate: Boolean = false,
	) {
		viewModelScope.launch {
			whereWasIRecapUseCase.observeOrGenerateRecap(
				mangaId = mangaId,
				forceShow = forceShow,
				forceRegenerate = forceRegenerate,
			).collect { state ->
				if (_selectedMangaDetailId.value == mangaId) {
					_activeMangaRecapState.value = state
				}
			}
		}
	}

	fun openReader(mangaId: Long, chapterIndex: Int = 0) {
		_seriesReaderProfiles.value[mangaId]?.let { savedProfile ->
			_readerConfig.value = savedProfile
		}
		_activeReaderMangaId.value = mangaId
		_activeChapterIndex.value = chapterIndex.coerceAtLeast(0)
		_activePageIndex.value = 0
	}

	fun closeReader() {
		val currentMangaId = _activeReaderMangaId.value
		val chIdx = _activeChapterIndex.value
		val page = _activePageIndex.value
		if (currentMangaId != null) {
			val item = rawLibraryItems.value.find { it.manga.id == currentMangaId }
			val chapter = item?.chapters?.getOrNull(chIdx)
			if (chapter != null) {
				viewModelScope.launch {
					libraryRepo.markChapterRead(
						mangaId = currentMangaId,
						chapterId = chapter.chapterId,
						page = page + 1,
						isRead = page >= 5 || chapter.isRead,
					)
				}
			}
		}
		_activeReaderMangaId.value = null
	}

	fun setReaderPage(pageIndex: Int, totalPages: Int = 18) {
		val clamped = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
		_activePageIndex.value = clamped
		if (clamped == totalPages - 1) {
			val mangaId = _activeReaderMangaId.value ?: return
			val item = rawLibraryItems.value.find { it.manga.id == mangaId } ?: return
			val ch = item.chapters.getOrNull(_activeChapterIndex.value) ?: return
			viewModelScope.launch {
				libraryRepo.markChapterRead(mangaId, ch.chapterId, totalPages, true)
			}
		}
	}

	fun switchReaderChapter(newChapterIndex: Int) {
		val mangaId = _activeReaderMangaId.value ?: return
		val item = rawLibraryItems.value.find { it.manga.id == mangaId } ?: return
		if (newChapterIndex in item.chapters.indices) {
			_activeChapterIndex.value = newChapterIndex
			_activePageIndex.value = 0
		}
	}

	fun removeHistoryItem(mangaId: Long) {
		viewModelScope.launch {
			db.getHistoryDao().deleteByMangaId(mangaId)
			postBanner("Removed entry from reading history.")
		}
	}

	fun clearAllHistory() {
		viewModelScope.launch {
			db.getHistoryDao().clearAllHistory()
			postBanner("Cleared reading history log.")
		}
	}

	fun refreshFeedFromExtensions() {
		viewModelScope.launch {
			val target = rawLibraryItems.value.firstOrNull { !it.manga.isBroken } ?: return@launch
			val newCh = KotatsuSyncAndDownloadCoordinator.syncLibraryChaptersNow(
				context = getApplication(),
				db = db,
				libraryItems = rawLibraryItems.value,
				notificationsEnabled = true,
			)
			if (newCh != null) {
				postBanner("Synced extensions & dispatched alert: New release Ch. ${newCh.number.toInt()} for '${target.manga.title}'!")
			}
		}
	}

	/**
	 * Adds a catalog manga to the library, running the Check-and-Swap on Add duplicate detector first.
	 */
	fun requestAddCatalogManga(catalogItem: ExtensionCatalogManga) {
		if (_displayConfig.value.checkAndSwapOnAdd) {
			val dupMatch = aiEngine.findExistingDuplicateForCatalogItem(catalogItem, rawLibraryItems.value)
			if (dupMatch != null) {
				_checkAndSwapPrompt.value = CheckAndSwapPromptState(
					candidate = catalogItem,
					existingMatch = dupMatch.first,
					similarityScore = dupMatch.second,
				)
				return
			}
		}
		viewModelScope.launch {
			libraryRepo.addCatalogItemToLibrary(catalogItem)
			refreshDiagnostics()
			postBanner("Added '${catalogItem.title}' from ${catalogItem.sourceName} to Library.")
		}
	}

	fun confirmCheckAndSwap(swapExisting: Boolean) {
		val prompt = _checkAndSwapPrompt.value ?: return
		_checkAndSwapPrompt.value = null
		viewModelScope.launch {
			if (swapExisting) {
				libraryRepo.addCatalogItemToLibrary(
					item = prompt.candidate,
					swapExistingMangaId = prompt.existingMatch.manga.id,
				)
				postBanner("Swapped '${prompt.existingMatch.manga.title}' to ${prompt.candidate.sourceName} & migrated read chapters!")
			} else {
				libraryRepo.addCatalogItemToLibrary(
					item = prompt.candidate,
					swapExistingMangaId = null,
				)
				postBanner("Added '${prompt.candidate.title}' as a separate library entry.")
			}
			refreshDiagnostics()
		}
	}

	fun dismissCheckAndSwap() {
		_checkAndSwapPrompt.value = null
	}

	fun resolveDuplicateOneByOne(pair: DuplicateCandidatePair, keepPrimary: Boolean) {
		viewModelScope.launch {
			val (winner, loser) = if (keepPrimary) {
				pair.primary to pair.secondary
			} else {
				pair.secondary to pair.primary
			}
			aiEngine.mergeDuplicatePair(winner, loser)
			refreshDiagnostics()
			postBanner("Merged '${loser.manga.title}' (${loser.manga.source}) into '${winner.manga.title}' (${winner.manga.source}).")
		}
	}

	fun ignoreDuplicatePair(pair: DuplicateCandidatePair) {
		viewModelScope.launch {
			aiEngine.ignoreDuplicatePair(pair)
			postBanner("Ignored duplicate pair for '${pair.primary.manga.title}'.")
		}
	}

	fun setBatchDedupRule(rule: BatchDeduplicationRule) {
		_batchRule.value = rule
	}

	fun resolveAllDuplicatesEnMasse() {
		val pairs = duplicateCandidates.value
		if (pairs.isEmpty()) return
		viewModelScope.launch {
			val count = aiEngine.resolveAllDuplicatesEnMasse(pairs, _batchRule.value)
			refreshDiagnostics()
			postBanner("En Masse Deduplication complete: Merged $count duplicate series using rule '${_batchRule.value.label}'.")
		}
	}

	fun runSingleAiFixIt(proposal: BrokenMangaFixProposal) {
		viewModelScope.launch {
			aiEngine.applyAiFixProposal(proposal)
			refreshDiagnostics()
			postBanner("DropSauce AI Fix-It repaired '${proposal.brokenItem.manga.title}' -> ${proposal.candidateSource}!")
		}
	}

	fun runAllAiFixItBatch() {
		val list = brokenMangaProposals.value
		if (list.isEmpty()) return
		viewModelScope.launch {
			val fixed = aiEngine.applyAllAiFixProposals(list)
			refreshDiagnostics()
			postBanner("DropSauce AI Fix-It repaired all $fixed broken manga entries across healthy extensions!")
		}
	}

	fun addExtensionRepository(name: String, url: String, repoType: String) {
		viewModelScope.launch {
			val added = extensionManager.addExtensionRepository(name, url, repoType)
			_discoveredExtensions.value = extensionManager.discoverExtensions(extensionRepos.value)
			_extensionCatalog.value = extensionManager.getExtensionCatalogItems()
			postBanner("Added external repository '${added.name}' (${added.extensionCount} extension manifests indexed).")
		}
	}

	fun removeExtensionRepository(repoId: Long) {
		viewModelScope.launch {
			db.getAuxiliaryDao().deleteExtensionRepo(repoId)
			extensionManager.deleteCachedRepoIndex(repoId)
			_discoveredExtensions.value = extensionManager.discoverExtensions(extensionRepos.value)
			_extensionCatalog.value = extensionManager.getExtensionCatalogItems()
			postBanner("Removed extension repository and cleared cached index.")
		}
	}

	fun syncAllExtensionRepositories() {
		viewModelScope.launch {
			val repos = extensionRepos.value
			_discoveredExtensions.value = extensionManager.discoverExtensions(repos)
			_extensionCatalog.value = extensionManager.getExtensionCatalogItems()
			val updatesCount = _discoveredExtensions.value.count { it.hasUpdate }
			postBanner(
				"Synced ${repos.size.coerceAtLeast(1)} repo(s): ${_discoveredExtensions.value.size} extensions indexed ($updatesCount update(s) available).",
			)
		}
	}

	fun installOrUpdateExtension(ext: ExternalExtensionInfo) {
		viewModelScope.launch {
			val wasUpdate = ext.hasUpdate
			val completedTask = extensionManager.downloadAndInstallExtension(ext) { progressTask ->
				_extensionInstallTasks.update { it + (ext.pkgName to progressTask) }
			}
			_extensionInstallTasks.update { it + (ext.pkgName to completedTask) }
			_discoveredExtensions.value = extensionManager.discoverExtensions(extensionRepos.value)
			_extensionCatalog.value = extensionManager.getExtensionCatalogItems()
			val targetVer = ext.latestVersionName ?: ext.versionName
			val actionLabel = if (wasUpdate) "Updated" else "Installed & activated"
			postBanner(
				"$actionLabel '${ext.name.removePrefix("EXT:")}' v$targetVer (FileProvider URI ready).",
			)
		}
	}

	fun uninstallExtension(ext: ExternalExtensionInfo) {
		viewModelScope.launch {
			extensionManager.uninstallExtension(ext)
			_extensionInstallTasks.update { it - ext.pkgName }
			_discoveredExtensions.value = extensionManager.discoverExtensions(extensionRepos.value)
			_extensionCatalog.value = extensionManager.getExtensionCatalogItems()
			postBanner("Uninstalled '${ext.name.removePrefix("EXT:")}' (${ext.pkgName}).")
		}
	}

	fun createBrowserApkDownloadIntent(ext: ExternalExtensionInfo): android.content.Intent =
		extensionManager.createBrowserApkDownloadIntent(ext)

	fun createFileProviderShareIntent(ext: ExternalExtensionInfo): android.content.Intent? =
		extensionManager.createFileProviderShareIntent(ext)

	fun canRequestPackageInstalls(): Boolean =
		extensionManager.canRequestPackageInstalls()

	fun createManageUnknownAppSourcesIntent(): android.content.Intent =
		extensionManager.createManageUnknownAppSourcesIntent()

	fun createApkPackageInstallerIntent(ext: ExternalExtensionInfo): android.content.Intent =
		extensionManager.createApkPackageInstallerIntent(ext)

	fun createUninstallPackageIntent(ext: ExternalExtensionInfo): android.content.Intent =
		extensionManager.createUninstallPackageIntent(ext.pkgName)

	fun importCbzFromUri(uri: Uri?, fallbackName: String = "Lone_Wolf_And_Cub_Vol1.cbz") {
		viewModelScope.launch {
			val app = getApplication<Application>()
			val stream = uri?.let { runCatching { app.contentResolver.openInputStream(it) }.getOrNull() }
				?: ByteArrayInputStream(ByteArray(0))
			val imported = stream.use { extensionManager.importCbzArchive(it, fallbackName) }
			db.getFavouritesDao().upsert(
				org.koitharu.kotatsu.core.db.entity.FavouriteEntity(
					mangaId = imported.id,
					categoryId = 4L,
					sortKey = 0,
					createdAt = System.currentTimeMillis(),
				),
			)
			refreshDiagnostics()
			postBanner("Indexed local archive '${imported.title}' into Local CBZ Vault.")
		}
	}

	fun exportBackup(format: BackupFormat, targetUri: Uri? = null) {
		viewModelScope.launch {
			val app = getApplication<Application>()
			when (format) {
				BackupFormat.KOTATSU_BAK -> {
					val bytes = backupRepo.exportKotatsuBak()
					targetUri?.let { uri ->
						runCatching { app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
					}
					_lastExportedPreview.value = format to "Kotatsu .bak ZIP Archive (${bytes.size} bytes)\nEntries: categories.json, favourites.json, history.json, chapters.json"
					postBanner("Exported Kotatsu .bak backup (${bytes.size / 1024} KB).")
				}
				BackupFormat.MIHON_TACHIBK -> {
					val bytes = backupRepo.exportMihonTachibk()
					targetUri?.let { uri ->
						runCatching { app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
					}
					val hexHeader = bytes.take(16).joinToString(" ") { "%02X".format(it) }
					_lastExportedPreview.value = format to "Mihon .tachibk GZIP Protobuf (${bytes.size} bytes)\nGZIP Header: $hexHeader\nSchema: BackupManga(1), BackupCategory(2), BackupSource(101)"
					postBanner("Exported Mihon .tachibk Protobuf backup (${bytes.size} bytes).")
				}
				BackupFormat.MIHON_JSON -> {
					val json = backupRepo.exportMihonJson()
					targetUri?.let { uri ->
						runCatching { app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
					}
					_lastExportedPreview.value = format to json.take(600) + if (json.length > 600) "\n… (${json.length} chars total)" else ""
					postBanner("Exported Mihon/Neko portable .json backup (${json.length / 1024} KB).")
				}
			}
		}
	}

	fun restoreBackupFromUri(uri: Uri, fileNameHint: String = "") {
		viewModelScope.launch {
			val app = getApplication<Application>()
			val bytes = runCatching {
				app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
			}.getOrNull()
			if (bytes == null || bytes.isEmpty()) {
				postBanner("Could not read backup file.")
				return@launch
			}
			val summary = backupRepo.restoreUniversalBackup(
				bytes = bytes,
				fileNameHint = fileNameHint,
				checkAndSwapDuplicates = _displayConfig.value.checkAndSwapOnAdd,
			)
			_lastBackupSummary.value = summary
			refreshDiagnostics()
			postBanner("Restored ${summary.format.label}: ${summary.mangaRestored} added, ${summary.duplicatesSwappedOrMerged} merged.")
		}
	}

	fun simulateRestoreSampleBackup(format: BackupFormat) {
		viewModelScope.launch {
			val bytes = when (format) {
				BackupFormat.MIHON_TACHIBK -> backupRepo.createSampleMihonTachibkPayload()
				BackupFormat.KOTATSU_BAK -> backupRepo.exportKotatsuBak()
				BackupFormat.MIHON_JSON -> backupRepo.exportMihonJson().toByteArray()
			}
			val summary = backupRepo.restoreUniversalBackup(
				bytes = bytes,
				fileNameHint = "sample.${format.extension}",
				checkAndSwapDuplicates = _displayConfig.value.checkAndSwapOnAdd,
			)
			_lastBackupSummary.value = summary
			refreshDiagnostics()
			postBanner("Restored ${format.label}: +${summary.mangaRestored} series (${summary.unmappedMihonSourcesFlaggedForAiFix} unmapped sources ready for AI Fix-It).")
		}
	}

	fun addCategory(title: String) {
		if (title.isBlank()) return
		viewModelScope.launch {
			val existing = categories.value
			val nextId = (existing.maxOfOrNull { it.categoryId } ?: 0) + 1
			db.getFavouritesDao().upsertCategory(
				FavouriteCategoryEntity(
					categoryId = nextId,
					createdAt = System.currentTimeMillis(),
					sortKey = existing.size,
					title = title.trim(),
					order = "LAST_READ",
					track = true,
					isVisibleInLibrary = true,
				),
			)
			postBanner("Created library category '${title.trim()}'.")
		}
	}

	fun deleteCategory(categoryId: Int) {
		viewModelScope.launch {
			db.getFavouritesDao().deleteCategory(categoryId)
			if (_selectedCategoryId.value == categoryId.toLong()) {
				_selectedCategoryId.value = null
			}
			postBanner("Removed category.")
		}
	}

	private val _bookmarkedPageKeys = MutableStateFlow<Set<String>>(
		setOf("1001_0_0", "1002_0_2"),
	)
	val bookmarkedPageKeys: StateFlow<Set<String>> = _bookmarkedPageKeys.asStateFlow()

	private val _seriesReaderProfiles = MutableStateFlow<Map<Long, ReaderCustomizationConfig>>(emptyMap())
	val seriesReaderProfiles: StateFlow<Map<Long, ReaderCustomizationConfig>> = _seriesReaderProfiles.asStateFlow()

	fun togglePageBookmark(mangaId: Long, chapterIndex: Int, pageIndex: Int, mangaTitle: String) {
		val key = "${mangaId}_${chapterIndex}_${pageIndex}"
		val current = _bookmarkedPageKeys.value
		val pageId = chapterIndex * 1000L + pageIndex
		if (key in current) {
			val next = current - key
			_bookmarkedPageKeys.value = next
			prefs.edit().putStringSet("bookmarked_page_keys", next).apply()
			viewModelScope.launch {
				db.getAuxiliaryDao().deleteBookmark(mangaId, pageId)
			}
			postBanner("Removed bookmark for '$mangaTitle' (Ch. ${chapterIndex + 1}, Page ${pageIndex + 1}).")
		} else {
			val next = current + key
			_bookmarkedPageKeys.value = next
			prefs.edit().putStringSet("bookmarked_page_keys", next).apply()
			viewModelScope.launch {
				db.getAuxiliaryDao().upsertBookmark(
					org.koitharu.kotatsu.core.db.entity.BookmarkEntity(
						mangaId = mangaId,
						pageId = pageId,
						chapterId = mangaId * 1000L + (chapterIndex + 1),
						page = pageIndex,
						imageUrl = "studio_page://$mangaId/$chapterIndex/$pageIndex",
						createdAt = System.currentTimeMillis(),
						percent = (pageIndex.toFloat() / 18f).coerceIn(0f, 1f),
					),
				)
			}
			postBanner("Bookmarked '$mangaTitle' • Ch. ${chapterIndex + 1}, Page ${pageIndex + 1} (Saved to Explore → Bookmarks).")
		}
	}

	fun saveSeriesReaderProfile(mangaId: Long, mangaTitle: String) {
		val cfg = _readerConfig.value
		_seriesReaderProfiles.update { it + (mangaId to cfg) }
		viewModelScope.launch {
			db.getAuxiliaryDao().upsertPreference(
				org.koitharu.kotatsu.core.db.entity.MangaPrefsEntity(
					mangaId = mangaId,
					mode = cfg.readingMode.ordinal,
					cfBrightness = cfg.brightnessDelta,
					cfContrast = cfg.contrastDelta,
					cfInvert = cfg.invertColors,
					cfGrayscale = cfg.grayscale,
					aiUpscaleMode = cfg.aiUpscaleMode.ordinal,
				),
			)
		}
		postBanner("Saved custom Reader & AI profile for '$mangaTitle' (${cfg.readingMode.shortLabel} • ${cfg.aiUpscaleMode.badge}).")
	}

	/**
	 * Mihon-style individual source migration: validates and migrates a single manga to the chosen target source
	 * while preserving all read chapters, history, and categories atomically.
	 */
	fun migrateSingleMangaSource(
		mangaId: Long,
		targetSourceName: String,
		targetChaptersCount: Int,
	) {
		viewModelScope.launch {
			val item = rawLibraryItems.value.find { it.manga.id == mangaId } ?: return@launch
			libraryRepo.migrateMangaSourcesAtomically(
				listOf(Triple(item, targetSourceName, targetChaptersCount)),
			)
			refreshDiagnostics()
			postBanner("Migrated '${item.manga.title}' -> ${targetSourceName.removePrefix("EXT:")} (read progress preserved).")
		}
	}

	/**
	 * Mihon-style En-Masse source migration: migrates all user-validated series to their selected target sources atomically.
	 */
	fun migrateBatchMangaSources(migrations: List<Triple<Long, String, Int>>) {
		if (migrations.isEmpty()) return
		viewModelScope.launch {
			val resolved = migrations.mapNotNull { (mangaId, targetSourceName, targetChCount) ->
				val item = rawLibraryItems.value.find { it.manga.id == mangaId } ?: return@mapNotNull null
				Triple(item, targetSourceName, targetChCount)
			}
			val migratedCount = libraryRepo.migrateMangaSourcesAtomically(resolved)
			refreshDiagnostics()
			postBanner("En-Masse Source Migration complete: Migrated $migratedCount series with 100% progress preservation!")
		}
	}

	/**
	 * Multi-Select Library Batch Actions (Atomic SQLite Transactions)
	 */
	fun batchMoveToCategory(mangaIds: Set<Long>, categoryId: Long) {
		if (mangaIds.isEmpty()) return
		viewModelScope.launch {
			libraryRepo.batchMoveToCategory(mangaIds, categoryId)
			val catName = categories.value.find { it.categoryId.toLong() == categoryId }?.title ?: "Category"
			postBanner("Moved ${mangaIds.size} selected series to '$catName'.")
		}
	}

	fun batchMarkChaptersRead(mangaIds: Set<Long>, markRead: Boolean) {
		if (mangaIds.isEmpty()) return
		viewModelScope.launch {
			libraryRepo.batchMarkChaptersRead(mangaIds, markRead)
			postBanner("Marked all chapters in ${mangaIds.size} selected series as ${if (markRead) "Read" else "Unread"}.")
		}
	}

	fun batchDownloadSeries(mangaIds: Set<Long>) {
		if (mangaIds.isEmpty()) return
		viewModelScope.launch {
			val selectedItems = rawLibraryItems.value.filter { it.manga.id in mangaIds }
			libraryRepo.batchSetDownloadedStatus(mangaIds, true)
			val packed = KotatsuSyncAndDownloadCoordinator.executeBatchCbzDownloads(
				context = getApplication(),
				db = db,
				items = selectedItems,
			)
			postBanner("Packed $packed real .CBZ archive(s) via Foreground DataSync for ${mangaIds.size} selected series.")
		}
	}

	fun batchDeleteDownloads(mangaIds: Set<Long>) {
		if (mangaIds.isEmpty()) return
		viewModelScope.launch {
			libraryRepo.batchSetDownloadedStatus(mangaIds, false)
			KotatsuSyncAndDownloadCoordinator.clearCompletedDownloads(getApplication())
			postBanner("Removed offline downloaded chapters & .CBZ archives for ${mangaIds.size} series.")
		}
	}

	fun batchRemoveFromLibrary(mangaIds: Set<Long>) {
		if (mangaIds.isEmpty()) return
		viewModelScope.launch {
			if (_selectedMangaDetailId.value in mangaIds) {
				_selectedMangaDetailId.value = null
			}
			libraryRepo.removeMangaFromLibrary(mangaIds)
			refreshDiagnostics()
			postBanner("Removed ${mangaIds.size} series from Library.")
		}
	}

	fun optimizeDatabaseStorage() {
		viewModelScope.launch {
			val pruned = libraryRepo.optimizeAndVacuumStorage()
			refreshDiagnostics()
			postBanner("SQLite v28 Storage Optimized: WAL checkpointed, FTS4 rebuilt, $pruned orphaned records pruned.")
		}
	}

	fun toggleDarkTheme(enabled: Boolean) {
		_darkTheme.value = enabled
		val mode = if (enabled) {
			org.koitharu.kotatsu.core.model.AppThemeMode.DARK
		} else {
			org.koitharu.kotatsu.core.model.AppThemeMode.LIGHT
		}
		_themeMode.value = mode
		prefs.edit().putString("theme_mode", mode.name).apply()
	}

	fun setThemeMode(mode: org.koitharu.kotatsu.core.model.AppThemeMode) {
		_themeMode.value = mode
		when (mode) {
			org.koitharu.kotatsu.core.model.AppThemeMode.DARK -> _darkTheme.value = true
			org.koitharu.kotatsu.core.model.AppThemeMode.LIGHT -> _darkTheme.value = false
			org.koitharu.kotatsu.core.model.AppThemeMode.SYSTEM -> Unit
		}
		prefs.edit().putString("theme_mode", mode.name).apply()
	}

	fun setColorSchemePreset(preset: org.koitharu.kotatsu.core.model.AppColorSchemePreset) {
		_colorSchemePreset.value = preset
		prefs.edit().putString("color_preset", preset.name).apply()
		postBanner("Applied '${preset.label}' color theme.")
	}

	fun togglePureOledBlack(enabled: Boolean) {
		_pureOledBlack.value = enabled
		prefs.edit().putBoolean("pure_oled_black", enabled).apply()
	}

	fun refreshDiagnostics() {
		viewModelScope.launch {
			_sqliteDiagnostics.value = libraryRepo.runSqliteDiagnostics()
		}
	}

	fun clearStatusBanner() {
		_statusBanner.value = null
	}

	private fun postBanner(msg: String) {
		_statusBanner.value = msg
	}
}
