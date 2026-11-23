package org.nemuri.app.navigation

import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koitharu.kotatsu.core.nav.AppRouter

@Stable
class NemuriNavController(initialTab: AppTab = AppTab.LIBRARY) {

	var currentTab by mutableStateOf(initialTab)
		private set

	val backStack = mutableStateListOf<AppDestination>(AppDestination.TabRoot(initialTab))

	val currentDestination: AppDestination
		get() = backStack.lastOrNull() ?: AppDestination.TabRoot(currentTab)

	val showBottomDock: Boolean
		get() = currentDestination is AppDestination.TabRoot

	fun selectTab(tab: AppTab) {
		currentTab = tab
		backStack.clear()
		backStack.add(AppDestination.TabRoot(tab))
	}

	fun navigate(destination: AppDestination) {
		if (destination is AppDestination.TabRoot) {
			selectTab(destination.tab)
			return
		}
		backStack.add(destination)
	}

	fun popBackStack(): Boolean {
		if (backStack.size > 1) {
			backStack.removeAt(backStack.lastIndex)
			return true
		}
		val root = backStack.firstOrNull()
		if (root is AppDestination.TabRoot && root.tab != AppTab.LIBRARY) {
			selectTab(AppTab.LIBRARY)
			return true
		}
		return false
	}

	fun popToRoot(tab: AppTab = currentTab) {
		selectTab(tab)
	}

	fun consumeIntent(intent: Intent?) {
		if (intent == null) return
		val data = intent.data
		if (intent.action == Intent.ACTION_VIEW && data != null) {
			val scheme = data.scheme?.lowercase()
			val host = data.host?.lowercase()
			val path = data.path?.lowercase()
			when {
				(scheme == "kotatsu" && host == "add-repo") ||
					(scheme == "tachiyomi" && host == "add-repo") ||
					(scheme == "mihon" && host == "extension-store") ||
					(scheme == "lnreader" && host == "repo" && path == "/add") -> {
					val repoUrl = data.getQueryParameter("url")?.trim().orEmpty()
					navigate(AppDestination.ExtensionsCatalog(initialRepoUrl = repoUrl.ifBlank { null }))
					return
				}
			}
		}

		when (intent.getStringExtra(AppRouter.KEY_ROUTE)) {
			"home" -> selectTab(AppTab.LIBRARY)
			"details" -> {
				val mangaId = intent.getLongExtra(AppRouter.KEY_ID, 0L)
				if (mangaId != 0L) {
					navigate(
						AppDestination.MangaDetails(
							mangaId = mangaId,
							sourceName = intent.getStringExtra(AppRouter.KEY_SOURCE),
						),
					)
				}
			}
			"list" -> {
				val sourceName = intent.getStringExtra(AppRouter.KEY_SOURCE)
				if (!sourceName.isNullOrBlank()) {
					navigate(AppDestination.SourceBrowse(sourceName = sourceName))
				}
			}
			"reader" -> {
				val mangaId = intent.getLongExtra(AppRouter.KEY_ID, 0L)
				if (mangaId != 0L) {
					navigate(AppDestination.Reader(mangaId = mangaId))
				}
			}
			"downloads" -> navigate(AppDestination.Downloads)
			"extensions" -> navigate(AppDestination.ExtensionsCatalog())
			"updates" -> selectTab(AppTab.FEED)
			"settings_sync" -> navigate(AppDestination.Settings(SettingsSection.CLOUD_SYNC))
			"settings_tracker" -> navigate(AppDestination.Settings(SettingsSection.TRACKERS))
			"settings_suggestions" -> navigate(AppDestination.Settings(SettingsSection.UPDATES))
			"tracker_debug" -> navigate(AppDestination.Settings(SettingsSection.UPDATES))
		}
	}
}
