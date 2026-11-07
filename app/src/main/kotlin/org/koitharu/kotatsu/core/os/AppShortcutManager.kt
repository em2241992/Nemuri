package org.koitharu.kotatsu.core.os

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppShortcutManager {
	suspend fun pruneOrphanedShortcutIcons(context: Context) {
		org.koitharu.kotatsu.core.os.pruneOrphanedShortcutIcons(context)
	}
}

suspend fun pruneOrphanedShortcutIcons(context: Context) = withContext(Dispatchers.IO) {
	val activeIds = ShortcutManagerCompat.getDynamicShortcuts(context)
		.map { it.id }
		.toSet()

	// Clean up any cached shortcut bitmaps not currently pinned in the top 4 dynamic shortcuts
	val shortcutDir = File(context.filesDir, "shortcuts")
	if (shortcutDir.exists() && shortcutDir.isDirectory) {
		shortcutDir.listFiles()?.forEach { file ->
			val shortcutId = file.nameWithoutExtension
			if (shortcutId !in activeIds) {
				file.delete()
			}
		}
	}
}
