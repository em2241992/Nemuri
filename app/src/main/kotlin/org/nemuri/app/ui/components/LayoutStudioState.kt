package org.nemuri.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.CoverAspectRatio
import org.koitharu.kotatsu.core.prefs.GridGutterDensity
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.core.prefs.NavLabelVisibility
import org.koitharu.kotatsu.core.prefs.SelectionMenuStyle

@Immutable
data class LayoutStudioPrefs(
	val cardStyle: MangaCardStyle = MangaCardStyle.COMPACT,
	val gridColumnsPortrait: Int = 3,
	val gridColumnsLandscape: Int = 5,
	val cardCornerRadiusDp: Int = 12,
	val gutterDensity: GridGutterDensity = GridGutterDensity.STANDARD,
	val coverAspectRatio: CoverAspectRatio = CoverAspectRatio.STANDARD_2_3,
	val showUnreadBadge: Boolean = true,
	val showDownloadedBadge: Boolean = true,
	val showSourceBadge: Boolean = false,
	val showBottomProgressBar: Boolean = true,
	val isNavBarCompact: Boolean = false,
	val navLabelVisibility: NavLabelVisibility = NavLabelVisibility.SELECTED_ONLY,
	val isSwipeActionsEnabled: Boolean = true,
	val uiScalePercent: Int = 100,
	val selectionMenuStyle: SelectionMenuStyle = SelectionMenuStyle.VISUAL_DIAGRAM,
	val isClassicBottomBar: Boolean = false,
) {
	fun columnsForOrientation(isLandscape: Boolean): Int {
		return if (isLandscape) gridColumnsLandscape else gridColumnsPortrait
	}
}

val LocalLayoutStudioPrefs = compositionLocalOf { LayoutStudioPrefs() }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LayoutStudioEntryPoint {
	fun appSettings(): AppSettings
}

@Composable
fun rememberAppSettings(): AppSettings {
	val context = LocalContext.current.applicationContext
	return remember(context) {
		EntryPointAccessors.fromApplication(context, LayoutStudioEntryPoint::class.java).appSettings()
	}
}

fun AppSettings.toLayoutStudioPrefs(): LayoutStudioPrefs {
	val resolvedCardStyle = if (!isTitleOverCover && mangaCardStyle == MangaCardStyle.COMPACT) {
		MangaCardStyle.COMFORTABLE
	} else {
		mangaCardStyle
	}
	return LayoutStudioPrefs(
		cardStyle = resolvedCardStyle,
		gridColumnsPortrait = gridColumnsPortrait,
		gridColumnsLandscape = gridColumnsLandscape,
		cardCornerRadiusDp = cardCornerRadiusDp,
		gutterDensity = gridGutterDensity,
		coverAspectRatio = coverAspectRatio,
		showUnreadBadge = showUnreadBadge,
		showDownloadedBadge = showDownloadedBadge,
		showSourceBadge = showSourceBadge,
		showBottomProgressBar = showBottomProgressBar,
		isNavBarCompact = isNavBarCompact,
		navLabelVisibility = navLabelVisibility,
		isSwipeActionsEnabled = isSwipeActionsEnabled,
		uiScalePercent = uiScalePercent,
		selectionMenuStyle = selectionMenuStyle,
		isClassicBottomBar = isLegacyNavigationBar,
	)
}

@Composable
fun rememberObservedLayoutStudioPrefs(): LayoutStudioPrefs {
	val settings = rememberAppSettings()
	val flow = remember(settings) {
		settings.observeChanges()
			.onStart { emit(null) }
			.map { settings.toLayoutStudioPrefs() }
	}
	val prefs by flow.collectAsState(initial = remember(settings) { settings.toLayoutStudioPrefs() })
	return prefs
}

@Composable
fun rememberRootTabBottomPadding(
	hasBottomDock: Boolean = true,
	extraBottom: Dp = 16.dp,
): Dp {
	val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
	val studio = LocalLayoutStudioPrefs.current
	val dockHeight = if (hasBottomDock) {
		when {
			studio.isClassicBottomBar -> if (studio.isNavBarCompact) 58.dp else 66.dp
			studio.isNavBarCompact -> 48.dp + 18.dp
			else -> 56.dp + 20.dp
		}
	} else {
		0.dp
	}
	return navBottom + dockHeight + extraBottom
}

@Composable
fun rememberPushScreenBottomPadding(extraBottom: Dp = 20.dp): Dp {
	val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
	return navBottom + extraBottom
}
