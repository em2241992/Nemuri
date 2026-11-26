package org.nemuri.app.ui.bookmarks

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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.MangaTransferCache
import org.nemuri.app.ui.components.MangaCoverImage
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.components.rememberPushScreenBottomPadding
import org.nemuri.app.ui.components.rememberSyncedTextField
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun BookmarksScreen(
	onBack: () -> Unit,
	onOpenDetails: (Manga) -> Unit,
	onOpenBookmark: (Bookmark) -> Unit,
	viewModel: BookmarksViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val bottomPadding = rememberPushScreenBottomPadding(24.dp)

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
					text = "Bookmarks",
					style = MaterialTheme.typography.titleLarge,
					color = NemuriTheme.colors.textPrimary,
				)
				if (uiState.totalBookmarks > 0) {
					Text(
						text = "${uiState.totalBookmarks} saved across ${uiState.groups.size} title(s)",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
			}
		}

		val queryField = rememberSyncedTextField(uiState.query, viewModel::setQuery)
		OutlinedTextField(
			value = queryField.fieldValue,
			onValueChange = queryField::onValueChange,
			placeholder = { Text("Search bookmarks or highlights…") },
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
			!uiState.isLoaded -> {
				NemuriLoadingState(message = "Loading bookmarks…")
			}

			uiState.groups.isEmpty() -> {
				NemuriEmptyState(
					icon = Icons.Default.BookmarkBorder,
					title = if (uiState.query.isBlank()) "No bookmarks yet" else "No matching bookmarks",
					subtitle = if (uiState.query.isBlank()) {
						"Save pages or text highlights inside the reader to jump straight back to them."
					} else {
						"Try a different title or highlight keyword."
					},
				)
			}

			else -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					uiState.groups.forEach { group ->
						item(key = "bm_header_${group.manga.id}") {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(top = 4.dp, start = 2.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(
									modifier = Modifier
										.weight(1f)
										.clickable {
											MangaTransferCache.put(group.manga)
											onOpenDetails(group.manga)
										},
								) {
									Text(
										text = group.manga.title,
										style = MaterialTheme.typography.titleSmall,
										fontWeight = FontWeight.SemiBold,
										color = NemuriTheme.colors.cyan,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Text(
										text = "${group.sourceTitle} • ${group.items.size} bookmark(s)",
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.textMuted,
									)
								}
								TextButton(onClick = { viewModel.removeGroupBookmarks(group) }) {
									Text(
										text = "Clear",
										style = MaterialTheme.typography.labelMedium,
										color = NemuriTheme.colors.danger,
									)
								}
							}
						}

						items(group.items, key = { it.bookmark.pageId }) { item ->
							BookmarkItemCard(
								item = item,
								onOpen = {
									MangaTransferCache.put(item.bookmark.manga)
									onOpenBookmark(item.bookmark)
								},
								onRemove = { viewModel.removeBookmark(item.bookmark) },
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun BookmarkItemCard(
	item: BookmarkEntryItem,
	onOpen: () -> Unit,
	onRemove: () -> Unit,
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
				manga = item.bookmark.manga,
				coverUrl = item.thumbnailUrl,
				modifier = Modifier
					.size(width = 48.dp, height = 68.dp)
					.clip(RoundedCornerShape(9.dp)),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(3.dp),
			) {
				Text(
					text = item.pageLabel,
					style = MaterialTheme.typography.titleSmall,
					color = NemuriTheme.colors.textPrimary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (!item.excerpt.isNullOrBlank()) {
					Text(
						text = "“${item.excerpt}”",
						style = MaterialTheme.typography.bodySmall,
						fontStyle = FontStyle.Italic,
						color = NemuriTheme.colors.textSecondary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
				Text(
					text = item.timeLabel,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
			IconButton(
				onClick = onRemove,
				modifier = Modifier.size(32.dp),
			) {
				Icon(
					imageVector = Icons.Default.DeleteOutline,
					contentDescription = "Remove bookmark",
					tint = NemuriTheme.colors.textDim,
					modifier = Modifier.size(18.dp),
				)
			}
		}
	}
}
