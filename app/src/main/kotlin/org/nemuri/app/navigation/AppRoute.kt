package org.nemuri.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.parsers.model.Manga
import java.util.concurrent.ConcurrentHashMap

enum class AppTab(val label: String) {
	LIBRARY("Library"),
	HISTORY("History"),
	FEED("Feed"),
	EXPLORE("Explore"),
	ENHANCE("Enhance"),
}

enum class SettingsSection(val title: String) {
	ROOT("Settings"),
	APPEARANCE("Appearance"),
	EXTENSIONS("Extensions & Repos"),
	READING("Reader & Novel"),
	DOWNLOADS_STORAGE("Downloads & Storage"),
	UPDATES("Library Updates & Feed"),
	BACKUP_RESTORE("Backup & Restore"),
	TRACKERS("Trackers & Discord RPC"),
	CLOUD_SYNC("Google Drive Sync"),
	PRIVACY("Privacy & Security"),
	ABOUT("About & Diagnostics"),
}

sealed interface AppDestination {
	data class TabRoot(val tab: AppTab) : AppDestination
	data class ExtensionsCatalog(val initialRepoUrl: String? = null) : AppDestination
	data class SourceBrowse(val sourceName: String, val initialQuery: String? = null) : AppDestination
	data class GlobalSearch(val initialQuery: String = "") : AppDestination
	data object LocalStorage : AppDestination
	data object Bookmarks : AppDestination
	data object Downloads : AppDestination
	data object ReadingStats : AppDestination
	data object DuplicateScanner : AppDestination
	data object BrokenSourcesWizard : AppDestination
	data object AiSettings : AppDestination
	data class MigrateManga(val mangaId: Long) : AppDestination
	data class MangaDetails(
		val mangaId: Long,
		val sourceName: String? = null,
		val sharedCoverKey: String? = null,
	) : AppDestination
	data class Reader(val mangaId: Long, val chapterId: Long? = null) : AppDestination
	data class Settings(val section: SettingsSection = SettingsSection.ROOT) : AppDestination
}

object MangaTransferCache {

	private const val MAX_ENTRIES = 250
	private val cache = ConcurrentHashMap<Long, Manga>()

	fun put(manga: Manga) {
		if (cache.size >= MAX_ENTRIES) {
			val oldestKey = cache.keys().asSequence().firstOrNull()
			if (oldestKey != null) {
				cache.remove(oldestKey)
			}
		}
		cache[manga.id] = manga
	}

	fun get(mangaId: Long): Manga? = cache[mangaId]
}

object PendingScrobblerAuth {

	private val _pending = MutableStateFlow<Pair<Int, String>?>(null)
	val pending = _pending.asStateFlow()

	fun post(serviceId: Int, authCode: String) {
		_pending.value = serviceId to authCode
	}

	fun clear() {
		_pending.value = null
	}
}
