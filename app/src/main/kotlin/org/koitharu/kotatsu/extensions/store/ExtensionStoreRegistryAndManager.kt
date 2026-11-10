package org.koitharu.kotatsu.extensions.store

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * ExtensionStoreRegistry + ExtensionStoreManager:
 * Persists store records, extension ownership records, store health, and paginated catalog search.
 */
class ExtensionStoreRegistry(
    context: Context,
) {
    private val storageFile = File(context.filesDir, "extension_stores/registry.json").apply {
        parentFile?.mkdirs()
    }
    private val mutex = Mutex()

    suspend fun readState(): ExtensionStoreRegistryState = mutex.withLock {
        readStateUnlocked()
    }

    suspend fun writeState(state: ExtensionStoreRegistryState) = mutex.withLock {
        writeStateUnlocked(state)
    }

    suspend fun updateState(
        transform: (ExtensionStoreRegistryState) -> ExtensionStoreRegistryState,
    ): ExtensionStoreRegistryState = mutex.withLock {
        val current = readStateUnlocked()
        val updated = transform(current)
        writeStateUnlocked(updated)
        updated
    }

    private fun readStateUnlocked(): ExtensionStoreRegistryState {
        if (!storageFile.exists()) return ExtensionStoreRegistryState()
        return runCatching {
            val root = JSONObject(storageFile.readText())
            val stores = mutableListOf<ExtensionStoreRecord>()
            val storesArr = root.optJSONArray("stores")
            if (storesArr != null) {
                for (i in 0 until storesArr.length()) {
                    val obj = storesArr.optJSONObject(i) ?: continue
                    stores += ExtensionStoreRecord(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        url = obj.optString("url"),
                        website = obj.optString("website").takeIf { it.isNotBlank() },
                        signingKeyFingerprint = obj.optString("signingKeyFingerprint").takeIf { it.isNotBlank() },
                        extensionCount = obj.optInt("extensionCount", 0),
                        enabled = obj.optBoolean("enabled", true),
                        createdAt = obj.optLong("createdAt", 0L),
                        updatedAt = obj.optLong("updatedAt", 0L),
                        lastSyncedAt = obj.optLong("lastSyncedAt", 0L),
                        lastError = obj.optString("lastError").takeIf { it.isNotBlank() },
                    )
                }
            }
            val ownership = LinkedHashMap<String, ExtensionOwnershipRecord>()
            val ownershipObj = root.optJSONObject("ownership")
            if (ownershipObj != null) {
                val keys = ownershipObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val rec = ownershipObj.optJSONObject(pkg) ?: continue
                    val modeStr = rec.optString("installMode", ExtensionInstallMode.EXTERNAL.name)
                    val mode = runCatching { ExtensionInstallMode.valueOf(modeStr) }
                        .getOrDefault(ExtensionInstallMode.EXTERNAL)
                    ownership[pkg] = ExtensionOwnershipRecord(
                        pkgName = pkg,
                        storeId = rec.optString("storeId").takeIf { it.isNotBlank() },
                        storeName = rec.optString("storeName").takeIf { it.isNotBlank() },
                        storeUrl = rec.optString("storeUrl").takeIf { it.isNotBlank() },
                        apkUrl = rec.optString("apkUrl").takeIf { it.isNotBlank() },
                        installedVersionName = rec.optString("installedVersionName").takeIf { it.isNotBlank() },
                        installedVersionCode = rec.optLong("installedVersionCode", 0L),
                        installMode = mode,
                        updatedAt = rec.optLong("updatedAt", 0L),
                    )
                }
            }
            ExtensionStoreRegistryState(stores = stores, ownership = ownership)
        }.getOrDefault(ExtensionStoreRegistryState())
    }

    private fun writeStateUnlocked(state: ExtensionStoreRegistryState) {
        runCatching {
            val root = JSONObject()
            val storesArr = JSONArray()
            state.stores.forEach { s ->
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("url", s.url)
                s.website?.let { obj.put("website", it) }
                s.signingKeyFingerprint?.let { obj.put("signingKeyFingerprint", it) }
                obj.put("extensionCount", s.extensionCount)
                obj.put("enabled", s.enabled)
                obj.put("createdAt", s.createdAt)
                obj.put("updatedAt", s.updatedAt)
                obj.put("lastSyncedAt", s.lastSyncedAt)
                s.lastError?.let { obj.put("lastError", it) }
                storesArr.put(obj)
            }
            root.put("stores", storesArr)

            val ownershipObj = JSONObject()
            state.ownership.forEach { (pkg, rec) ->
                val obj = JSONObject()
                obj.put("pkgName", rec.pkgName)
                rec.storeId?.let { obj.put("storeId", it) }
                rec.storeName?.let { obj.put("storeName", it) }
                rec.storeUrl?.let { obj.put("storeUrl", it) }
                rec.apkUrl?.let { obj.put("apkUrl", it) }
                rec.installedVersionName?.let { obj.put("installedVersionName", it) }
                obj.put("installedVersionCode", rec.installedVersionCode)
                obj.put("installMode", rec.installMode.name)
                obj.put("updatedAt", rec.updatedAt)
                ownershipObj.put(pkg, obj)
            }
            root.put("ownership", ownershipObj)
            storageFile.writeText(root.toString())
        }
    }
}

class ExtensionStoreManager(
    context: Context,
    val repoRepository: ExtensionRepoRepository = ExtensionRepoRepository(context),
    private val registry: ExtensionStoreRegistry = ExtensionStoreRegistry(context),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ExtensionStoreState())
    val state: StateFlow<ExtensionStoreState> = _state.asStateFlow()

    suspend fun initializeDefaultStoresIfNeeded(defaultRepoUrls: List<Pair<String, String>>) {
        val current = registry.readState()
        if (current.stores.isEmpty()) {
            val now = System.currentTimeMillis()
            val seeded = defaultRepoUrls.map { (name, rawUrl) ->
                val normalized = normalizeExtensionStoreUrl(rawUrl)
                ExtensionStoreRecord(
                    id = stableExtensionStoreId(normalized),
                    name = name,
                    url = normalized,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            val next = current.copy(stores = seeded)
            registry.writeState(next)
            publishRegistryState(next)
        } else {
            publishRegistryState(current)
        }
    }

    suspend fun addStore(rawUrl: String, customName: String? = null): Result<ValidatedExtensionStore> {
        val normalizedUrl = normalizeExtensionStoreUrl(rawUrl)
        if (normalizedUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Repository URL cannot be blank"))
        }
        val storeId = stableExtensionStoreId(normalizedUrl)
        setStoreSyncing(storeId, true)

        val validationResult = repoRepository.validateStore(normalizedUrl, forceRefresh = true)
        return validationResult.onSuccess { validated ->
            val now = System.currentTimeMillis()
            val record = ExtensionStoreRecord(
                id = storeId,
                name = customName?.takeIf { it.isNotBlank() } ?: validated.displayName,
                url = validated.normalizedUrl,
                website = validated.website,
                signingKeyFingerprint = validated.signingKeyFingerprint,
                extensionCount = validated.extensionCount,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                lastSyncedAt = now,
                lastError = null,
            )
            val next = registry.updateState { existing ->
                val filtered = existing.stores.filterNot { it.id == storeId || it.url.equals(validated.normalizedUrl, ignoreCase = true) }
                existing.copy(stores = filtered + record)
            }
            publishRegistryState(next, activeStoreId = storeId)
        }.onFailure { error ->
            setStoreSyncing(storeId, false, error.message)
        }
    }

    suspend fun refreshAllEnabledStores(forceRefresh: Boolean = false): List<ExternalExtensionRepoEntry> {
        val regState = registry.readState()
        val allEntries = LinkedHashMap<String, ExternalExtensionRepoEntry>()
        for (store in regState.stores.filter { it.enabled }) {
            setStoreSyncing(store.id, true)
            val result = repoRepository.getAvailableExtensions(store.url, forceRefresh = forceRefresh)
            result.onSuccess { entries ->
                entries.forEach { entry ->
                    val existing = allEntries[entry.pkg]
                    if (existing == null || entry.code > existing.code) {
                        allEntries[entry.pkg] = entry
                    }
                }
                val now = System.currentTimeMillis()
                val next = registry.updateState { st ->
                    st.copy(
                        stores = st.stores.map { s ->
                            if (s.id == store.id) {
                                s.copy(
                                    extensionCount = entries.size,
                                    updatedAt = now,
                                    lastSyncedAt = now,
                                    lastError = null,
                                )
                            } else {
                                s
                            }
                        },
                    )
                }
                publishRegistryState(next)
            }.onFailure { err ->
                setStoreSyncing(store.id, false, err.message)
            }
        }
        return allEntries.values.toList()
    }

    suspend fun recordExtensionInstalled(
        entry: ExternalExtensionRepoEntry,
        storeUrl: String?,
        installMode: ExtensionInstallMode = ExtensionInstallMode.PRIVATE,
    ) {
        val normalizedStoreUrl = storeUrl?.let(::normalizeExtensionStoreUrl)
        val storeId = normalizedStoreUrl?.let(::stableExtensionStoreId)
        val now = System.currentTimeMillis()
        val ownership = ExtensionOwnershipRecord(
            pkgName = entry.pkg,
            storeId = storeId,
            storeName = normalizedStoreUrl?.let(::defaultExtensionStoreName),
            storeUrl = normalizedStoreUrl,
            apkUrl = entry.apkUrl,
            installedVersionName = entry.version,
            installedVersionCode = entry.code,
            installMode = installMode,
            updatedAt = now,
        )
        val next = registry.updateState { existing ->
            existing.copy(ownership = existing.ownership + (entry.pkg to ownership))
        }
        publishRegistryState(next)
    }

    suspend fun recordExtensionUninstalled(pkgName: String) {
        val next = registry.updateState { existing ->
            existing.copy(ownership = existing.ownership - pkgName)
        }
        publishRegistryState(next)
    }

    fun getCatalogPage(
        storeUrl: String,
        query: String = "",
        lang: String? = null,
        includeNsfw: Boolean = true,
        offset: Int = 0,
        limit: Int = 60,
    ): ExtensionCatalogPage {
        val all = repoRepository.getCachedExtensions(storeUrl)
        val normalizedLang = lang?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "all" }
        val filtered = all.filter { entry ->
            (includeNsfw || !entry.isNsfw) &&
                (normalizedLang == null || entry.lang.equals(normalizedLang, ignoreCase = true) || entry.lang.equals("all", ignoreCase = true)) &&
                matchesExtensionQuery(entry, query)
        }.sortedWith(
            compareBy<ExternalExtensionRepoEntry> { extensionDisplayName(it).lowercase(Locale.ROOT) }
                .thenBy { it.lang },
        )
        val safeOffset = offset.coerceAtLeast(0).coerceAtMost(filtered.size)
        val safeEnd = (safeOffset + limit).coerceAtMost(filtered.size)
        val slice = filtered.subList(safeOffset, safeEnd)
        return ExtensionCatalogPage(
            items = slice,
            totalCount = filtered.size,
            hasMore = safeEnd < filtered.size,
        )
    }

    private fun setStoreSyncing(storeId: String, isSyncing: Boolean, errorMessage: String? = null) {
        val current = _state.value
        val existingHealth = current.storeHealthById[storeId] ?: StoreHealth(storeId = storeId)
        val updatedHealth = existingHealth.copy(
            isSyncing = isSyncing,
            lastSyncedAt = if (!isSyncing && errorMessage == null) System.currentTimeMillis() else existingHealth.lastSyncedAt,
            lastError = errorMessage,
        )
        _state.value = current.copy(
            storeHealthById = current.storeHealthById + (storeId to updatedHealth),
            isRefreshingCatalog = isSyncing,
        )
    }

    private fun publishRegistryState(regState: ExtensionStoreRegistryState, activeStoreId: String? = null) {
        val current = _state.value
        val resolvedActive = activeStoreId
            ?: current.activeStoreId?.takeIf { id -> regState.stores.any { it.id == id } }
            ?: regState.stores.firstOrNull { it.enabled }?.id
            ?: regState.stores.firstOrNull()?.id
        val healthMap = regState.stores.associate { store ->
            store.id to StoreHealth(
                storeId = store.id,
                isSyncing = false,
                lastSyncedAt = store.lastSyncedAt,
                lastError = store.lastError,
            )
        }
        _state.value = current.copy(
            stores = regState.stores,
            activeStoreId = resolvedActive,
            ownershipByPackage = regState.ownership,
            storeHealthById = healthMap,
            isRefreshingCatalog = false,
        )
    }
}
