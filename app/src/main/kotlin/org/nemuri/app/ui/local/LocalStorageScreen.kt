package org.nemuri.app.ui.local

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.rememberPushScreenBottomPadding
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun LocalStorageScreen(
	onBack: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	viewModel: LocalStorageViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val bottomPadding = rememberPushScreenBottomPadding(24.dp)
	var pendingDelete by remember { mutableStateOf<Manga?>(null) }

	val importPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			viewModel.importDocuments(uris)
		}
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Local Storage",
					style = MaterialTheme.typography.titleLarge,
					color = NemuriTheme.colors.textPrimary,
				)
				val summary = buildString {
					append("${uiState.items.size} title(s)")
					if (uiState.usedStorageLabel.isNotBlank()) {
						append(" • Used ${uiState.usedStorageLabel}")
					}
					if (uiState.freeStorageLabel.isNotBlank()) {
						append(" • Free ${uiState.freeStorageLabel}")
					}
				}
				Text(
					text = summary,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			IconButton(onClick = viewModel::refresh) {
				Icon(
					imageVector = Icons.Default.Refresh,
					contentDescription = "Rescan local library",
					tint = NemuriTheme.colors.textSecondary,
				)
			}
			Surface(
				color = NemuriTheme.colors.cyan,
				shape = RoundedCornerShape(10.dp),
				modifier = Modifier
					.padding(end = 8.dp)
					.clip(RoundedCornerShape(10.dp))
					.clickable { importPicker.launch(arrayOf("*/*")) },
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Icon(
						imageVector = Icons.Default.Add,
						contentDescription = null,
						tint = NemuriTheme.colors.onCyan,
						modifier = Modifier.size(16.dp),
					)
					Text(
						text = "Import",
						style = MaterialTheme.typography.labelMedium,
						color = NemuriTheme.colors.onCyan,
					)
				}
			}
		}

		val queryField = rememberSyncedTextField(uiState.query, viewModel::setQuery)
		OutlinedTextField(
			value = queryField.fieldValue,
			onValueChange = queryField::onValueChange,
			placeholder = { Text("Filter CBZ, EPUB & PDF files…") },
			leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
			trailingIcon = {
				if (queryField.fieldValue.text.isNotEmpty()) {
					IconButton(onClick = queryField::clear) {
						Icon(Icons.Default.Close, contentDescription = "Clear")
					}
				}
			},
			singleLine = true,
			shape = RoundedCornerShape(14.dp),
			colors = OutlinedTextFieldDefaults.colors(
				focusedBorderColor = NemuriTheme.colors.cyan,
				unfocusedBorderColor = NemuriTheme.colors.border,
				focusedContainerColor = NemuriTheme.colors.surfaceElevated,
				unfocusedContainerColor = NemuriTheme.colors.surfaceElevated,
			),
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 4.dp),
		)

		HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(top = 6.dp))

		when {
			uiState.isLoading -> {
				NemuriLoadingState(message = "Scanning local storage…")
			}

			uiState.items.isEmpty() -> {
				NemuriEmptyState(
					icon = Icons.Default.FolderOpen,
					title = if (uiState.query.isBlank()) "No local files yet" else "No matching local files",
					subtitle = if (uiState.query.isBlank()) {
						"Import CBZ, ZIP, EPUB, or PDF files from device storage, or download chapters offline."
					} else {
						"Try clearing the filter to view all local titles."
					},
					actionLabel = if (uiState.query.isBlank()) "Import Files" else null,
					onAction = if (uiState.query.isBlank()) {
						{ importPicker.launch(arrayOf("*/*")) }
					} else {
						null
					},
				)
			}

			else -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					items(uiState.items, key = { it.manga.id }) { entry ->
						LocalMangaRow(
							entry = entry,
							onOpen = {
								MangaTransferCache.put(entry.manga)
								onOpenDetails(entry.manga)
							},
							onDelete = { pendingDelete = entry.manga },
						)
					}
				}
			}
		}
	}

	val deleteTarget = pendingDelete
	if (deleteTarget != null) {
		AlertDialog(
			onDismissRequest = { pendingDelete = null },
			containerColor = NemuriTheme.colors.sheet,
			title = { Text("Delete local files", color = NemuriTheme.colors.textPrimary) },
			text = {
				Text(
					"Permanently delete \"${deleteTarget.title}\" from device storage?",
					color = NemuriTheme.colors.textSecondary,
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						viewModel.deleteManga(deleteTarget)
						pendingDelete = null
					},
					colors = ButtonDefaults.textButtonColors(contentColor = NemuriTheme.colors.danger),
				) {
					Text("Delete")
				}
			},
			dismissButton = {
				TextButton(onClick = { pendingDelete = null }) {
					Text("Cancel", color = NemuriTheme.colors.textSecondary)
				}
			},
		)
	}
}

@Composable
private fun LocalMangaRow(
	entry: LocalMangaEntry,
	onOpen: () -> Unit,
	onDelete: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onOpen),
	) {
		Row(
			modifier = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverImage(
				manga = entry.manga,
				coverUrl = entry.manga.coverUrl,
				modifier = Modifier
					.size(width = 50.dp, height = 72.dp)
					.clip(RoundedCornerShape(9.dp)),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = entry.manga.title,
						style = MaterialTheme.typography.titleMedium,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					Surface(
						color = NemuriTheme.colors.tealContainer,
						shape = RoundedCornerShape(6.dp),
					) {
						NonWrappingBadgeText(
							text = entry.formatBadge,
							color = NemuriTheme.colors.cyan,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
				}
				Text(
					text = entry.subtitle,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			IconButton(onClick = onDelete) {
				Icon(
					imageVector = Icons.Default.DeleteOutline,
					contentDescription = "Delete",
					tint = NemuriTheme.colors.danger.copy(alpha = 0.85f),
					modifier = Modifier.size(20.dp),
				)
			}
		}
	}
}
