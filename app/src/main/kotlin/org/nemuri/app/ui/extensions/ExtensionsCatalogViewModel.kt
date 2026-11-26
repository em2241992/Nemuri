package org.nemuri.app.ui.extensions

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.parser.favicon.extensionPackageFaviconUri
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.extensions.install.ExtensionUpdateWorker
import org.koitharu.kotatsu.extensions.install.ShizukuExtensionInstaller
import org.koitharu.kotatsu.extensions.runtime.getExternalExtensionLanguageDisplayName
import org.koitharu.kotatsu.extensions.runtime.normalizeExternalExtensionLangCode
import org.koitharu.kotatsu.lnreader.LnPluginManager
import org.koitharu.kotatsu.lnreader.model.LnMangaSource
import org.koitharu.kotatsu.lnreader.model.languageLabel
import org.koitharu.kotatsu.mihon.MihonExtensionLoader
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.model.MihonLoadResult
import org.koitharu.kotatsu.settings.sources.catalog.EXTENSION_APK_PREFIX
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionInstallMode
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreKind
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreManager
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreState
import org.koitharu.kotatsu.settings.sources.catalog.ExternalExtensionRepoRepository
import org.koitharu.kotatsu.settings.sources.catalog.StoreHealth
import org.koitharu.kotatsu.settings.sources.catalog.canUseStoreCatalogForUpdates
import org.koitharu.kotatsu.settings.sources.catalog.extensionDisplayName
import org.koitharu.kotatsu.settings.sources.catalog.extensionStoreDisplayLabels
import org.koitharu.kotatsu.settings.sources.catalog.extensionStoreKind
import org.koitharu.kotatsu.settings.sources.catalog.isLnPlugin
import org.koitharu.kotatsu.settings.sources.catalog.isNewerPluginVersion
import org.koitharu.kotatsu.settings.sources.catalog.isNewerThan
import org.koitharu.kotatsu.settings.sources.catalog.isNovelExtension
import org.koitharu.kotatsu.settings.sources.catalog.matchesExtensionQuery
import org.nemuri.app.ui.components.NemuriToastManager
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

enum class ExtensionsTab(val label: String) {
	INSTALLED("Installed"),
	UPDATES("Updates"),
	CATALOG("Catalog"),
	STORES("Stores"),
}

enum class ExtensionContentFilter(val label: String) {
	ALL("All"),
	MANGA("Manga"),
	NOVELS("Novels"),
}

@Immutable
data class CatalogExtensionItem(
	val listKey: String,
	val packageName: String,
	val displayName: String,
	val apkName: String,
	val versionName: String,
	val versionCode: Long,
	val langCode: String,
	val langLabel: String,
	val isNovel: Boolean,
	val isLnPlugin: Boolean,
	val contentWarning: ContentWarning,
	val iconUrl: String?,
	val storeId: String,
	val storeIndexUrl: String,
	val storeLabel: String,
	val isInstalled: Boolean,
	val hasUpdate: Boolean,
	val installedVersionName: String?,
	val sourceNames: List<String>,
)

@Immutable
data class InstalledExtensionItem(
	val listKey: String,
	val id: String,
	val displayName: String,
	val versionName: String,
	val versionCode: Long,
	val langCode: String,
	val langLabel: String,
	val isNovel: Boolean,
	val isLnPlugin: Boolean,
	val isShared: Boolean,
	val isHidden: Boolean,
	val storeLabel: String?,
	val iconModel: Any?,
	val updateCandidate: CatalogExtensionItem?,
	val sourceCount: Int,
)

@Immutable
data class LanguageOption(
	val code: String,
	val label: String,
	val count: Int,
)

@Immutable
data class StoreSummaryItem(
	val state: ExtensionStoreState,
	val label: String,
	val kind: ExtensionStoreKind?,
)

@Immutable
data class PendingSystemInstall(
	val apkFile: File,
	val packageName: String,
	val storeId: String,
	val displayName: String,
)

@Immutable
data class ExtensionsCatalogUiState(
	val activeTab: ExtensionsTab = ExtensionsTab.INSTALLED,
	val query: String = "",
	val contentFilter: ExtensionContentFilter = ExtensionContentFilter.ALL,
	val selectedStoreId: String? = null,
	val selectedLangCode: String? = null,
	val installedItems: List<InstalledExtensionItem> = emptyList(),
	val updateItems: List<InstalledExtensionItem> = emptyList(),
	val catalogItems: List<CatalogExtensionItem> = emptyList(),
	val stores: List<StoreSummaryItem> = emptyList(),
	val failedExtensions: List<MihonLoadResult.Error> = emptyList(),
	val availableLanguages: List<LanguageOption> = emptyList(),
	val inProgressIds: Set<String> = emptySet(),
	val isRefreshing: Boolean = false,
	val isAddingStore: Boolean = false,
	val isPrivateMode: Boolean = false,
	val isShizukuEnabled: Boolean = false,
	val isShizukuInstalled: Boolean = false,
	val isShizukuBinderAlive: Boolean = false,
	val isShizukuReady: Boolean = false,
	val isAutoUpdateEnabled: Boolean = false,
	val pendingSystemInstall: PendingSystemInstall? = null,
	val pendingSystemUninstall: String? = null,
)

private data class FilterSelection(
	val activeTab: ExtensionsTab = ExtensionsTab.INSTALLED,
	val query: String = "",
	val contentFilter: ExtensionContentFilter = ExtensionContentFilter.ALL,
	val selectedStoreId: String? = null,
	val selectedLangCode: String? = null,
)

private data class ExtensionSourcesSnapshot(
	val storeStates: List<ExtensionStoreState>,
	val mihonSuccess: List<MihonLoadResult.Success>,
	val mihonFailed: List<MihonLoadResult.Error>,
	val lnSources: List<LnMangaSource>,
	val prefSnapshot: PrefSnapshot,
)

private data class BaseExtensionsSnapshot(
	val installedCombined: List<InstalledExtensionItem>,
	val allCatalogItems: List<CatalogExtensionItem>,
	val storeSummaries: List<StoreSummaryItem>,
	val failedExtensions: List<MihonLoadResult.Error>,
	val isPrivateMode: Boolean,
)

private data class PrefSnapshot(
	val privateMode: Boolean,
	val shizukuEnabled: Boolean,
	val autoUpdateEnabled: Boolean,
	val shizukuStateVersion: Int,
	val hiddenPackages: Set<String>,
	val hiddenLnPlugins: Set<String>,
	val contentFilter: ContentWarning,
)

@HiltViewModel
class ExtensionsCatalogViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val storeManager: ExtensionStoreManager,
	private val repoRepository: ExternalExtensionRepoRepository,
	private val extensionLoader: MihonExtensionLoader,
	private val extensionManager: MihonExtensionManager,
	private val lnPluginManager: LnPluginManager,
	private val shizukuInstaller: ShizukuExtensionInstaller,
	private val sourcesRepository: MangaSourcesRepository,
	private val updateScheduler: ExtensionUpdateWorker.Scheduler,
	@BaseHttpClient private val okHttpClientProvider: Provider<OkHttpClient>,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private val filterSelection = MutableStateFlow(FilterSelection())
	private val inProgressIds = MutableStateFlow<Set<String>>(emptySet())
	private val isRefreshing = MutableStateFlow(false)
	private val isAddingStore = MutableStateFlow(false)
	private val pendingSystemInstall = MutableStateFlow<PendingSystemInstall?>(null)
	private val pendingSystemUninstall = MutableStateFlow<String?>(null)
	private val reloadTrigger = MutableStateFlow(0)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				lnPluginManager.initialize()
				storeManager.initialize(forceRefresh = false)
			}.onFailure {
				Log.w(TAG, "Failed to initialize extension stores", it)
			}
		}
	}

	private val prefSnapshotFlow = combine(
		settings.observeChanges()
			.filter { key ->
				key == null ||
					key == AppSettings.KEY_PRIVATE_INSTALLER ||
					key == AppSettings.KEY_SHIZUKU_INSTALLER ||
					key == AppSettings.KEY_AUTO_UPDATE_EXTENSIONS ||
					key == AppSettings.KEY_MIHON_HIDDEN_PACKAGES ||
					key == AppSettings.KEY_LN_HIDDEN_PLUGINS ||
					key == AppSettings.KEY_CONTENT_FILTER
			}
			.onStart { emit(null) },
		reloadTrigger,
		shizukuInstaller.stateVersion,
	) { _, _, shizukuVer ->
		PrefSnapshot(
			privateMode = settings.isPrivateInstallEnabled,
			shizukuEnabled = settings.isShizukuInstallerEnabled,
			autoUpdateEnabled = settings.isAutoUpdateExtensionsEnabled,
			shizukuStateVersion = shizukuVer,
			hiddenPackages = settings.mihonHiddenPackages,
			hiddenLnPlugins = settings.lnHiddenPlugins,
			contentFilter = settings.contentFilter,
		)
	}

	private val baseSnapshotFlow = combine(
		storeManager.states,
		extensionManager.installedExtensions,
		extensionManager.failedExtensions,
		lnPluginManager.sources,
		prefSnapshotFlow,
	) { stores, installedMihon, failedMihon, lnSources, prefs ->
		buildBaseSnapshot(
			ExtensionSourcesSnapshot(
				storeStates = stores,
				mihonSuccess = installedMihon,
				mihonFailed = failedMihon,
				lnSources = lnSources,
				prefSnapshot = prefs,
			),
		)
	}.flowOn(Dispatchers.IO)

	private val actionStateFlow = combine(
		filterSelection,
		inProgressIds,
		isRefreshing,
		isAddingStore,
		pendingSystemInstall,
	) { filters, progress, refreshing, adding, pendingInstall ->
		ActionStateSnapshot(
			filters = filters,
			inProgressIds = progress,
			isRefreshing = refreshing,
			isAddingStore = adding,
			pendingSystemInstall = pendingInstall,
		)
	}

	val uiState: StateFlow<ExtensionsCatalogUiState> = combine(
		baseSnapshotFlow,
		actionStateFlow,
		pendingSystemUninstall,
	) { base, actions, pendingUninstall ->
		buildUiState(base, actions, pendingUninstall)
	}
		.flowOn(Dispatchers.Default)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000L),
			initialValue = ExtensionsCatalogUiState(
				isPrivateMode = settings.isPrivateInstallEnabled,
				isShizukuEnabled = settings.isShizukuInstallerEnabled,
				isShizukuInstalled = shizukuInstaller.isInstalled,
				isShizukuBinderAlive = shizukuInstaller.isBinderAlive,
				isShizukuReady = shizukuInstaller.isReady,
				isAutoUpdateEnabled = settings.isAutoUpdateExtensionsEnabled,
			),
		)

	private fun buildBaseSnapshot(
		snapshot: ExtensionSourcesSnapshot,
	): BaseExtensionsSnapshot {
		val prefs = snapshot.prefSnapshot
		val mode = if (prefs.privateMode) ExtensionInstallMode.SANDBOX else ExtensionInstallMode.SYSTEM
		val storeRecords = snapshot.storeStates.map { it.store }
		val storeLabels = extensionStoreDisplayLabels(storeRecords)

		val localInfos = extensionLoader.getInstalledExtensions(context, prefs.privateMode)
		val loadedByPkg = snapshot.mihonSuccess.associateBy { it.pkgName }
		val installedMihonByPkg = localInfos.associateBy { it.pkgName }
		val installedLnById = snapshot.lnSources.associateBy { it.pluginId }

		val storeSummaries = snapshot.storeStates.map { state ->
			StoreSummaryItem(
				state = state,
				label = storeLabels[state.store.id] ?: state.store.displayName,
				kind = state.catalog.extensionStoreKind(),
			)
		}

		// Collect every catalog entry across stores so Updates & Catalog share consistent metadata.
		val seenStoreEntries = HashSet<String>()
		val usedCatalogKeys = HashMap<String, Int>()
		val allCatalogItems = buildList {
			for (state in snapshot.storeStates) {
				if (!state.store.enabled) continue
				val storeLabel = storeLabels[state.store.id] ?: state.store.displayName
				for (entry in state.catalog) {
					if (entry.contentWarning > prefs.contentFilter) continue
					val langCode = normalizeExternalExtensionLangCode(entry.lang)
					val dedupeSig = "${state.store.id}:${entry.packageName}:${entry.apkName}:${entry.versionCode}:$langCode"
					if (!seenStoreEntries.add(dedupeSig)) continue
					val baseKey = "${state.store.id}:${entry.packageName}:${entry.apkName}"
					val keyCount = usedCatalogKeys[baseKey] ?: 0
					usedCatalogKeys[baseKey] = keyCount + 1
					val listKey = if (keyCount == 0) baseKey else "$baseKey#$keyCount"

					val localMihon = if (!entry.isLnPlugin) installedMihonByPkg[entry.packageName] else null
					val localLn = if (entry.isLnPlugin) installedLnById[entry.packageName] else null
					val isInstalled = localMihon != null || localLn != null
					val hasUpdate = when {
						!canUseStoreCatalogForUpdates(state.health) -> false
						localLn != null -> isNewerPluginVersion(entry.versionName, localLn.plugin.version)
						localMihon != null -> {
							val owner = storeManager.owner(mode, localMihon)
							(owner == null || owner.id == state.store.id) && entry.isNewerThan(localMihon)
						}
						else -> false
					}
					val iconUrl = entry.iconUrl?.takeIf { it.isNotBlank() }
						?: if (!entry.isLnPlugin) repoRepository.resolveIconUrl(state.store.indexUrl, entry.packageName) else null
					add(
						CatalogExtensionItem(
							listKey = listKey,
							packageName = entry.packageName,
							displayName = extensionDisplayName(entry.name),
							apkName = entry.apkName,
							versionName = entry.versionName,
							versionCode = entry.versionCode,
							langCode = langCode,
							langLabel = getExternalExtensionLanguageDisplayName(langCode),
							isNovel = entry.isNovelExtension,
							isLnPlugin = entry.isLnPlugin,
							contentWarning = entry.contentWarning,
							iconUrl = iconUrl,
							storeId = state.store.id,
							storeIndexUrl = state.store.indexUrl,
							storeLabel = storeLabel,
							isInstalled = isInstalled,
							hasUpdate = hasUpdate,
							installedVersionName = localLn?.plugin?.version ?: localMihon?.versionName,
							sourceNames = entry.sources.map { it.name },
						),
					)
				}
			}
		}

		// Map updatable catalog entries by (packageId, isLnPlugin) prioritizing owning store.
		val mihonUpdatesByPkg = HashMap<String, CatalogExtensionItem>()
		val lnUpdatesById = HashMap<String, CatalogExtensionItem>()
		for (item in allCatalogItems) {
			if (!item.hasUpdate) continue
			if (item.isLnPlugin) {
				val existing = lnUpdatesById[item.packageName]
				val ownerStoreId = installedLnById[item.packageName]?.plugin?.storeId
				if (existing == null || item.storeId == ownerStoreId) {
					lnUpdatesById[item.packageName] = item
				}
			} else {
				val existing = mihonUpdatesByPkg[item.packageName]
				val ownerStoreId = installedMihonByPkg[item.packageName]?.let { storeManager.owner(mode, it)?.id }
				if (existing == null || item.storeId == ownerStoreId) {
					mihonUpdatesByPkg[item.packageName] = item
				}
			}
		}

		val usedInstalledKeys = HashMap<String, Int>()
		val installedCombined = buildList {
			for (info in localInfos) {
				val loaded = loadedByPkg[info.pkgName]
				val catalogueSources = loaded?.catalogueSources.orEmpty()
				val isNovel = info.pkgName.contains("novelextension.") ||
					catalogueSources.any { it.isNovelSource }
				val effectiveLang = normalizeExternalExtensionLangCode(loaded?.lang?.takeIf { it.isNotBlank() } ?: info.lang)
				val ownerStore = storeManager.owner(if (info.isShared) ExtensionInstallMode.SYSTEM else ExtensionInstallMode.SANDBOX, info)
				val baseKey = "apk:${info.pkgName}"
				val keyCount = usedInstalledKeys[baseKey] ?: 0
				usedInstalledKeys[baseKey] = keyCount + 1
				val listKey = if (keyCount == 0) baseKey else "$baseKey#$keyCount"
				val resolvedIcon: Any = if (info.isShared) {
					extensionPackageFaviconUri(info.pkgName)
				} else {
					loadPrivateArchiveIcon(info.apkPath) ?: extensionPackageFaviconUri(info.pkgName)
				}
				add(
					InstalledExtensionItem(
						listKey = listKey,
						id = info.pkgName,
						displayName = extensionDisplayName(info.appName),
						versionName = info.versionName,
						versionCode = info.versionCode,
						langCode = effectiveLang,
						langLabel = getExternalExtensionLanguageDisplayName(effectiveLang),
						isNovel = isNovel,
						isLnPlugin = false,
						isShared = info.isShared,
						isHidden = info.pkgName in prefs.hiddenPackages,
						storeLabel = ownerStore?.let { storeLabels[it.id] ?: it.displayName },
						iconModel = resolvedIcon,
						updateCandidate = mihonUpdatesByPkg[info.pkgName],
						sourceCount = catalogueSources.size.coerceAtLeast(1),
					),
				)
			}
			for (ln in snapshot.lnSources) {
				val code = normalizeExternalExtensionLangCode(ln.plugin.lang)
				val storeLabel = ln.plugin.storeId?.let { id ->
					storeLabels[id] ?: storeRecords.firstOrNull { it.id == id }?.displayName
				}
				val baseKey = "ln:${ln.pluginId}"
				val keyCount = usedInstalledKeys[baseKey] ?: 0
				usedInstalledKeys[baseKey] = keyCount + 1
				val listKey = if (keyCount == 0) baseKey else "$baseKey#$keyCount"
				add(
					InstalledExtensionItem(
						listKey = listKey,
						id = ln.pluginId,
						displayName = ln.displayName,
						versionName = ln.plugin.version,
						versionCode = 0L,
						langCode = code,
						langLabel = ln.plugin.languageLabel.ifBlank { getExternalExtensionLanguageDisplayName(code) },
						isNovel = true,
						isLnPlugin = true,
						isShared = false,
						isHidden = ln.pluginId in prefs.hiddenLnPlugins,
						storeLabel = storeLabel,
						iconModel = ln.plugin.iconUrl.takeIf { it.isNotBlank() },
						updateCandidate = lnUpdatesById[ln.pluginId],
						sourceCount = 1,
					),
				)
			}
		}.sortedBy { it.displayName.lowercase() }

		return BaseExtensionsSnapshot(
			installedCombined = installedCombined,
			allCatalogItems = allCatalogItems,
			storeSummaries = storeSummaries,
			failedExtensions = snapshot.mihonFailed,
			isPrivateMode = prefs.privateMode,
		)
	}

	private fun buildUiState(
		base: BaseExtensionsSnapshot,
		actions: ActionStateSnapshot,
		pendingUninstall: String?,
	): ExtensionsCatalogUiState {
		val filters = actions.filters
		val installedCombined = base.installedCombined
		val allCatalogItems = base.allCatalogItems

		fun matchesContentFilter(isNovel: Boolean): Boolean = when (filters.contentFilter) {
			ExtensionContentFilter.ALL -> true
			ExtensionContentFilter.MANGA -> !isNovel
			ExtensionContentFilter.NOVELS -> isNovel
		}

		val selectedNorm = filters.selectedLangCode?.let(::normalizeExternalExtensionLangCode)
		fun matchesLang(lang: String): Boolean =
			selectedNorm == null || lang.equals(selectedNorm, ignoreCase = true)

		val langCounts = HashMap<String, Int>()
		when (filters.activeTab) {
			ExtensionsTab.INSTALLED -> installedCombined.forEach {
				if (matchesContentFilter(it.isNovel)) langCounts[it.langCode] = (langCounts[it.langCode] ?: 0) + 1
			}
			ExtensionsTab.UPDATES -> installedCombined.filter { it.updateCandidate != null }.forEach {
				if (matchesContentFilter(it.isNovel)) langCounts[it.langCode] = (langCounts[it.langCode] ?: 0) + 1
			}
			ExtensionsTab.CATALOG -> allCatalogItems.forEach {
				if ((filters.selectedStoreId == null || it.storeId == filters.selectedStoreId) &&
					matchesContentFilter(it.isNovel)
				) {
					langCounts[it.langCode] = (langCounts[it.langCode] ?: 0) + 1
				}
			}
			ExtensionsTab.STORES -> Unit
		}
		val languageOptions = langCounts.map { (code, count) ->
			LanguageOption(code = code, label = getExternalExtensionLanguageDisplayName(code), count = count)
		}.sortedBy { it.label.lowercase() }

		val filteredInstalled = installedCombined.filter { item ->
			matchesContentFilter(item.isNovel) &&
				matchesLang(item.langCode) &&
				matchesExtensionQuery(filters.query, item.displayName, item.id)
		}

		val filteredUpdates = installedCombined.filter { item ->
			item.updateCandidate != null &&
				matchesContentFilter(item.isNovel) &&
				matchesLang(item.langCode) &&
				matchesExtensionQuery(filters.query, item.displayName, item.id)
		}

		val filteredCatalog = allCatalogItems.filter { item ->
			(filters.selectedStoreId == null || item.storeId == filters.selectedStoreId) &&
				matchesContentFilter(item.isNovel) &&
				matchesLang(item.langCode) &&
				matchesExtensionQuery(
					filters.query,
					item.displayName,
					item.packageName,
					*item.sourceNames.toTypedArray(),
				)
		}.sortedWith(compareByDescending<CatalogExtensionItem> { it.hasUpdate }.thenBy { it.displayName.lowercase() })

		return ExtensionsCatalogUiState(
			activeTab = filters.activeTab,
			query = filters.query,
			contentFilter = filters.contentFilter,
			selectedStoreId = filters.selectedStoreId,
			selectedLangCode = filters.selectedLangCode,
			installedItems = filteredInstalled,
			updateItems = filteredUpdates,
			catalogItems = filteredCatalog,
			stores = base.storeSummaries,
			failedExtensions = base.failedExtensions,
			availableLanguages = languageOptions,
			inProgressIds = actions.inProgressIds,
			isRefreshing = actions.isRefreshing,
			isAddingStore = actions.isAddingStore,
			isPrivateMode = base.isPrivateMode,
			isShizukuEnabled = settings.isShizukuInstallerEnabled,
			isShizukuInstalled = shizukuInstaller.isInstalled,
			isShizukuBinderAlive = shizukuInstaller.isBinderAlive,
			isShizukuReady = shizukuInstaller.isReady,
			isAutoUpdateEnabled = settings.isAutoUpdateExtensionsEnabled,
			pendingSystemInstall = actions.pendingSystemInstall,
			pendingSystemUninstall = pendingUninstall,
		)
	}

	fun selectTab(tab: ExtensionsTab) {
		filterSelection.update { it.copy(activeTab = tab) }
	}

	fun setQuery(query: String) {
		filterSelection.update { it.copy(query = query) }
	}

	fun setContentFilter(filter: ExtensionContentFilter) {
		filterSelection.update { it.copy(contentFilter = filter) }
	}

	fun selectStoreFilter(storeId: String?) {
		filterSelection.update { it.copy(selectedStoreId = storeId) }
	}

	fun selectLanguageFilter(langCode: String?) {
		filterSelection.update { it.copy(selectedLangCode = langCode) }
	}

	fun refreshStores() {
		viewModelScope.launch(Dispatchers.IO) {
			isRefreshing.value = true
			try {
				storeManager.refresh(forceRefresh = true)
				sourcesRepository.reloadMihonSources()
				toastManager.show("Extension stores refreshed")
			} catch (e: Exception) {
				toastManager.show(e.localizedMessage ?: "Failed to refresh stores")
			} finally {
				isRefreshing.value = false
			}
		}
	}

	fun addStore(rawUrl: String, onResult: (Boolean) -> Unit = {}) {
		val url = rawUrl.trim()
		if (url.isBlank()) {
			toastManager.show("Enter a repository URL")
			onResult(false)
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			isAddingStore.value = true
			val result = storeManager.validateAndAdd(url)
			isAddingStore.value = false
			result.fold(
				onSuccess = { store ->
					toastManager.show("Added store: ${store.displayName}")
					filterSelection.update { it.copy(activeTab = ExtensionsTab.CATALOG, selectedStoreId = store.id) }
					onResult(true)
				},
				onFailure = { error ->
					toastManager.show(error.localizedMessage ?: "Unable to add extension store")
					onResult(false)
				},
			)
		}
	}

	fun removeStore(storeId: String) {
		viewModelScope.launch(Dispatchers.IO) {
			storeManager.removeStore(storeId)
			if (filterSelection.value.selectedStoreId == storeId) {
				filterSelection.update { it.copy(selectedStoreId = null) }
			}
			reloadTrigger.value++
			toastManager.show("Extension store removed")
		}
	}

	fun installOrUpdate(item: CatalogExtensionItem) {
		if (item.packageName in inProgressIds.value) return
		viewModelScope.launch(Dispatchers.IO) {
			markInProgress(item.packageName, true)
			try {
				if (item.isLnPlugin) {
					installLnPlugin(item)
				} else {
					installMihonApk(item)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to install ${item.packageName}", e)
				toastManager.show("Install failed: ${e.localizedMessage ?: item.displayName}")
			} finally {
				markInProgress(item.packageName, false)
			}
		}
	}

	private suspend fun installLnPlugin(item: CatalogExtensionItem) {
		val pluginUrl = repoRepository.resolveApkUrl(item.storeIndexUrl, item.apkName)
		val rawCode = downloadText(pluginUrl)
		lnPluginManager.install(
			pluginId = item.packageName,
			rawCode = rawCode,
			iconUrl = item.iconUrl.orEmpty(),
			lang = item.langCode,
			storeId = item.storeId,
		)
		runCatching { updateScheduler.schedule() }
		reloadTrigger.value++
		toastManager.show("Installed ${item.displayName}")
	}

	private suspend fun installMihonApk(item: CatalogExtensionItem) {
		val apkUrl = repoRepository.resolveApkUrl(item.storeIndexUrl, item.apkName)
		val apkFile = File(context.cacheDir, "$EXTENSION_APK_PREFIX${item.packageName}.apk")
		try {
			downloadFile(apkUrl, apkFile)
			if (!isValidExtensionApk(apkFile, item.packageName)) {
				apkFile.delete()
				toastManager.show("Invalid or unsigned extension APK")
				return
			}
			if (settings.isPrivateInstallEnabled) {
				val ok = MihonExtensionLoader.installPrivateExtensionFile(
					context = context,
					file = apkFile,
					replaceExistingProvider = true,
					expectedPackageName = item.packageName,
				)
				apkFile.delete()
				if (ok) {
					storeManager.setOwner(ExtensionInstallMode.SANDBOX, item.packageName, item.storeId)
					sourcesRepository.reloadMihonSources()
					runCatching { updateScheduler.schedule() }
					reloadTrigger.value++
					toastManager.show("Installed ${item.displayName} (Sandbox)")
				} else {
					toastManager.show("Sandbox install failed for ${item.displayName}")
				}
				return
			}
			if (settings.isShizukuInstallerEnabled) {
				when (val res = shizukuInstaller.install(apkFile, item.packageName)) {
					ShizukuExtensionInstaller.InstallResult.Success -> {
						apkFile.delete()
						storeManager.setOwner(ExtensionInstallMode.SYSTEM, item.packageName, item.storeId)
						sourcesRepository.reloadMihonSources()
						runCatching { updateScheduler.schedule() }
						reloadTrigger.value++
						toastManager.show("Installed ${item.displayName} (Shizuku)")
						return
					}
					ShizukuExtensionInstaller.InstallResult.InvalidPackage -> {
						apkFile.delete()
						toastManager.show("APK package mismatch for ${item.displayName}")
						return
					}
					ShizukuExtensionInstaller.InstallResult.Unavailable -> {
						val detail = if (!shizukuInstaller.isBinderAlive) {
							"Shizuku service is not running"
						} else {
							"Shizuku permission not granted"
						}
						toastManager.show("$detail — opening system installer")
					}
					is ShizukuExtensionInstaller.InstallResult.Failure -> {
						val reason = res.message?.takeIf { it.isNotBlank() }
							?: res.status?.let { "code $it" }
							?: "error"
						toastManager.show("Shizuku install failed ($reason) — opening system installer")
					}
				}
			}
			pendingSystemInstall.value = PendingSystemInstall(
				apkFile = apkFile,
				packageName = item.packageName,
				storeId = item.storeId,
				displayName = item.displayName,
			)
		} catch (e: Exception) {
			apkFile.delete()
			throw e
		}
	}

	fun onSystemInstallFinished(pending: PendingSystemInstall) {
		pendingSystemInstall.compareAndSet(pending, null)
		viewModelScope.launch(Dispatchers.IO) {
			val installedNow = MihonExtensionLoader.isExtensionPackage(context, pending.packageName)
			pending.apkFile.delete()
			if (installedNow) {
				storeManager.setOwner(ExtensionInstallMode.SYSTEM, pending.packageName, pending.storeId)
				sourcesRepository.reloadMihonSources()
				runCatching { updateScheduler.schedule() }
				reloadTrigger.value++
				toastManager.show("Installed ${pending.displayName}")
			} else {
				sourcesRepository.reloadMihonSources()
				reloadTrigger.value++
			}
		}
	}

	fun uninstall(item: InstalledExtensionItem) {
		viewModelScope.launch(Dispatchers.IO) {
			if (item.isLnPlugin) {
				lnPluginManager.uninstall(item.id)
				reloadTrigger.value++
				toastManager.show("Uninstalled ${item.displayName}")
			} else if (!item.isShared) {
				MihonExtensionLoader.uninstallPrivateExtension(context, item.id)
				storeManager.removeOwner(ExtensionInstallMode.SANDBOX, item.id)
				sourcesRepository.reloadMihonSources()
				reloadTrigger.value++
				toastManager.show("Uninstalled ${item.displayName}")
			} else {
				pendingSystemUninstall.value = item.id
			}
		}
	}

	fun onSystemUninstallFinished(packageName: String) {
		pendingSystemUninstall.compareAndSet(packageName, null)
		viewModelScope.launch(Dispatchers.IO) {
			if (!MihonExtensionLoader.isExtensionPackage(context, packageName)) {
				storeManager.removeOwner(ExtensionInstallMode.SYSTEM, packageName)
				toastManager.show("Extension removed")
			}
			sourcesRepository.reloadMihonSources()
			reloadTrigger.value++
		}
	}

	fun toggleHidden(item: InstalledExtensionItem) {
		viewModelScope.launch(Dispatchers.IO) {
			if (item.isLnPlugin) {
				val current = settings.lnHiddenPlugins
				settings.lnHiddenPlugins = if (item.id in current) current - item.id else current + item.id
			} else {
				val current = settings.mihonHiddenPackages
				settings.mihonHiddenPackages = if (item.id in current) current - item.id else current + item.id
			}
			reloadTrigger.value++
			toastManager.show(if (item.isHidden) "Shown in Explore" else "Hidden from Explore")
		}
	}

	fun updateAll() {
		val candidates = uiState.value.updateItems.mapNotNull { it.updateCandidate }
		if (candidates.isEmpty()) return
		viewModelScope.launch(Dispatchers.IO) {
			val canBatchSilent = settings.isPrivateInstallEnabled ||
				(settings.isShizukuInstallerEnabled && shizukuInstaller.isReady)
			for (candidate in candidates) {
				if (candidate.isLnPlugin || canBatchSilent) {
					markInProgress(candidate.packageName, true)
					try {
						if (candidate.isLnPlugin) installLnPlugin(candidate) else installMihonApk(candidate)
					} catch (e: Exception) {
						Log.e(TAG, "Batch update failed for ${candidate.packageName}", e)
					} finally {
						markInProgress(candidate.packageName, false)
					}
				} else {
					installOrUpdate(candidate)
					break
				}
			}
		}
	}

	fun setPrivateInstallEnabled(enabled: Boolean) {
		settings.isPrivateInstallEnabled = enabled
		reloadTrigger.value++
		viewModelScope.launch(Dispatchers.IO) {
			sourcesRepository.reloadMihonSources()
			if (enabled && settings.isAutoUpdateExtensionsEnabled) {
				runCatching { updateScheduler.schedule() }
			}
		}
		toastManager.show(
			if (enabled) {
				"App-isolated mode enabled (existing OS extensions remain active)"
			} else {
				"System installer mode enabled"
			},
		)
	}

	fun setShizukuEnabled(enabled: Boolean) {
		if (!enabled) {
			settings.isShizukuInstallerEnabled = false
			reloadTrigger.value++
			toastManager.show("Shizuku installer disabled")
			return
		}
		if (!shizukuInstaller.isInstalled) {
			toastManager.show("Shizuku app is not installed on this device")
			return
		}
		settings.isShizukuInstallerEnabled = true
		reloadTrigger.value++
		viewModelScope.launch(Dispatchers.IO) {
			val granted = shizukuInstaller.requestPermissionIfNeeded()
			reloadTrigger.value++
			when {
				granted -> toastManager.show("Shizuku silent installer connected and authorized")
				!shizukuInstaller.isBinderAlive ->
					toastManager.show("Shizuku enabled — start the Shizuku service to install silently")
				else ->
					toastManager.show("Shizuku permission not granted — authorize Nemuri in Shizuku")
			}
		}
	}

	fun requestShizukuPermission() {
		viewModelScope.launch(Dispatchers.IO) {
			if (!shizukuInstaller.isInstalled) {
				toastManager.show("Shizuku app is not installed")
				return@launch
			}
			val granted = shizukuInstaller.requestPermissionIfNeeded()
			reloadTrigger.value++
			when {
				granted -> toastManager.show("Shizuku permission granted")
				!shizukuInstaller.isBinderAlive -> toastManager.show("Start the Shizuku service first, then retry")
				else -> toastManager.show("Shizuku permission was denied")
			}
		}
	}

	fun setAutoUpdateEnabled(enabled: Boolean) {
		settings.isAutoUpdateExtensionsEnabled = enabled
		reloadTrigger.value++
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				if (enabled || settings.isExtensionUpdateNotificationsEnabled) {
					updateScheduler.schedule()
				} else {
					updateScheduler.unschedule()
				}
			}
		}
		toastManager.show(
			if (enabled) {
				"Automatic extension updates enabled"
			} else {
				"Automatic extension updates disabled"
			},
		)
	}

	private fun loadPrivateArchiveIcon(apkPath: String): android.graphics.drawable.Drawable? = runCatching {
		val pm = context.packageManager
		val pkgInfo = pm.getPackageArchiveInfo(apkPath, 0) ?: return@runCatching null
		val appInfo = pkgInfo.applicationInfo ?: return@runCatching null
		appInfo.sourceDir = apkPath
		appInfo.publicSourceDir = apkPath
		appInfo.loadIcon(pm)
	}.getOrNull()

	private fun markInProgress(id: String, active: Boolean) {
		inProgressIds.update { if (active) it + id else it - id }
	}

	private fun downloadText(url: String): String {
		val request = Request.Builder().url(url).get().build()
		return okHttpClientProvider.get().newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
			val body = response.body
			if (body.contentLength() > MAX_PLUGIN_BYTES) throw IOException("Plugin exceeds 4 MB limit")
			body.string()
		}
	}

	private fun downloadFile(url: String, destination: File) {
		val tmp = File(destination.parentFile, "${destination.name}.tmp")
		try {
			val request = Request.Builder().url(url).get().build()
			okHttpClientProvider.get().newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
				val body = response.body
				if (body.contentLength() > MAX_APK_BYTES) throw IOException("APK exceeds 100 MB limit")
				body.byteStream().use { input ->
					tmp.outputStream().buffered().use { output ->
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						var total = 0L
						while (true) {
							val count = input.read(buffer)
							if (count < 0) break
							total += count
							if (total > MAX_APK_BYTES) throw IOException("APK exceeds 100 MB limit")
							output.write(buffer, 0, count)
						}
					}
				}
			}
			if (!tmp.renameTo(destination)) {
				tmp.copyTo(destination, overwrite = true)
			}
		} finally {
			tmp.delete()
		}
	}

	@Suppress("DEPRECATION")
	private fun isValidExtensionApk(apk: File, expectedPackage: String): Boolean {
		if (!apk.isFile || apk.length() <= 0L) return false
		val flags = PackageManager.GET_META_DATA or
			PackageManager.GET_CONFIGURATIONS or
			PackageManager.GET_SIGNATURES or
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
		val info: PackageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
		if (info.packageName != expectedPackage) return false
		if (PackageInfoCompat.getLongVersionCode(info) < 0L) return false
		return MihonExtensionLoader.isPackageAnExtensionStatic(info) &&
			MihonExtensionLoader.getSignatures(info).isNotEmpty()
	}

	private data class ActionStateSnapshot(
		val filters: FilterSelection,
		val inProgressIds: Set<String>,
		val isRefreshing: Boolean,
		val isAddingStore: Boolean,
		val pendingSystemInstall: PendingSystemInstall?,
	)

	private companion object {
		const val TAG = "ExtensionsCatalogVM"
		const val MAX_APK_BYTES = 100L * 1024L * 1024L
		const val MAX_PLUGIN_BYTES = 4L * 1024L * 1024L
	}
}
