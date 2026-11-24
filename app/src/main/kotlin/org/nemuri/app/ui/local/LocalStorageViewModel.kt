package org.nemuri.app.ui.local

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.util.FileSize
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.local.ui.ImportService
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.nemuri.app.ui.components.NemuriToastManager
import javax.inject.Inject

@Immutable
data class LocalMangaEntry(
	val manga: Manga,
	val formatBadge: String,
	val subtitle: String,
)

@Immutable
data class LocalStorageUiState(
	val items: List<LocalMangaEntry> = emptyList(),
	val query: String = "",
	val usedStorageLabel: String = "",
	val freeStorageLabel: String = "",
	val isLoading: Boolean = true,
)

@HiltViewModel
class LocalStorageViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val localMangaRepository: LocalMangaRepository,
	private val localStorageManager: LocalStorageManager,
	@LocalStorageChanges private val localStorageChanges: SharedFlow<LocalManga?>,
	private val toastManager: NemuriToastManager,
) : ViewModel() {

	private var allItems: List<Manga> = emptyList()
	private val _uiState = MutableStateFlow(LocalStorageUiState())
	val uiState: StateFlow<LocalStorageUiState> = _uiState.asStateFlow()

	init {
		refresh()
		viewModelScope.launch {
			localStorageChanges.collectLatest {
				refresh()
			}
		}
	}

	fun refresh() {
		viewModelScope.launch(Dispatchers.IO) {
			_uiState.update { it.copy(isLoading = allItems.isEmpty()) }
			val list = runCatchingCancellable {
				localMangaRepository.getList(offset = 0, order = SortOrder.NEWEST, filter = null)
			}.getOrElse { emptyList() }
			allItems = list

			val usedBytes = runCatchingCancellable { localStorageManager.computeStorageSize() }.getOrDefault(0L)
			val freeBytes = runCatchingCancellable { localStorageManager.computeAvailableSize() }.getOrDefault(0L)
			val usedLabel = if (usedBytes > 0L) FileSize.BYTES.format(context, usedBytes) else "0 B"
			val freeLabel = if (freeBytes > 0L) FileSize.BYTES.format(context, freeBytes) else ""

			_uiState.update { current ->
				current.copy(
					items = filterEntries(list, current.query),
					usedStorageLabel = usedLabel,
					freeStorageLabel = freeLabel,
					isLoading = false,
				)
			}
		}
	}

	fun setQuery(query: String) {
		_uiState.update { current ->
			current.copy(
				query = query,
				items = filterEntries(allItems, query),
			)
		}
	}

	fun importDocuments(uris: List<Uri>) {
		if (uris.isEmpty()) return
		viewModelScope.launch(Dispatchers.IO) {
			uris.forEach { uri ->
				runCatchingCancellable { localStorageManager.takePermissions(uri) }
			}
			val started = ImportService.start(context, uris)
			if (started) {
				toastManager.show("Importing ${uris.size} file(s)…")
			} else {
				toastManager.show("Could not start local file import")
			}
		}
	}

	fun deleteManga(manga: Manga) {
		viewModelScope.launch(Dispatchers.IO) {
			val deleted = runCatchingCancellable { localMangaRepository.delete(manga) }.getOrDefault(false)
			if (deleted) {
				toastManager.show("Deleted \"${manga.title}\"")
				refresh()
			} else {
				toastManager.show("Failed to delete \"${manga.title}\"")
			}
		}
	}

	private fun filterEntries(sourceList: List<Manga>, query: String): List<LocalMangaEntry> {
		val trimmed = query.trim()
		return sourceList
			.asSequence()
			.filter { manga ->
				trimmed.isEmpty() ||
					manga.title.contains(trimmed, ignoreCase = true) ||
					manga.authors.any { it.contains(trimmed, ignoreCase = true) }
			}
			.map { manga ->
				val format = when {
					manga.isEpub -> "EPUB"
					manga.url.endsWith(".pdf", ignoreCase = true) -> "PDF"
					else -> "CBZ"
				}
				val authors = manga.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")
				val subtitle = listOfNotNull(format, authors, manga.state?.name?.lowercase()?.replaceFirstChar { it.uppercase() })
					.joinToString(" • ")
				LocalMangaEntry(
					manga = manga,
					formatBadge = format,
					subtitle = subtitle,
				)
			}
			.toList()
	}
}
