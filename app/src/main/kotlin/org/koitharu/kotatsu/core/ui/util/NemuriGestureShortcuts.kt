package org.koitharu.kotatsu.core.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.util.TitleNormalizer

/**
 * 1. Back Button Long-Press -> Jump to Home Root:
 * Single tap pops the back stack (`onBackTap()`); long-press triggers `HapticFeedbackType.LongPress`
 * and pops directly to the root Home/Library destination (`onLongPressHomeRoot()`).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.nemuriBackNavigationGesture(
	onBackTap: () -> Unit,
	onLongPressHomeRoot: () -> Unit,
): Modifier = composed {
	val haptics = LocalHapticFeedback.current
	this.combinedClickable(
		onClick = onBackTap,
		onLongClick = {
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			onLongPressHomeRoot()
		},
	)
}

/**
 * 2. Continue Reading Button Long-Press -> Open Details:
 * Single tap on "Continue" launches the Reader directly (`onTapOpenReader()`);
 * long-pressing triggers `HapticFeedbackType.LongPress` and opens `MangaDetailsScreen` (`onLongPressOpenDetails()`).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.nemuriContinueReadingGesture(
	onTapOpenReader: () -> Unit,
	onLongPressOpenDetails: () -> Unit,
): Modifier = composed {
	val haptics = LocalHapticFeedback.current
	this.combinedClickable(
		onClick = onTapOpenReader,
		onLongClick = {
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			onLongPressOpenDetails()
		},
	)
}

/**
 * 3. Details Title Long-Press -> Cross-Source Search Sheet:
 * Long-pressing the Manga Title on `MangaDetailsScreen` triggers `HapticFeedbackType.LongPress`
 * and opens a modal bottom sheet pre-populated with `TitleNormalizer.toNormalizedSlug(mangaTitle)`.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.nemuriTitleCrossSourceSearchGesture(
	mangaTitle: String,
	onClickTitle: () -> Unit = {},
	onLongPressOpenCrossSourceSearch: (normalizedSlug: String) -> Unit,
): Modifier = composed {
	val haptics = LocalHapticFeedback.current
	this.combinedClickable(
		onClick = onClickTitle,
		onLongClick = {
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			val slug = TitleNormalizer.toNormalizedSlug(mangaTitle)
			onLongPressOpenCrossSourceSearch(slug)
		},
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossSourceMergeSearchSheet(
	parentManga: MangaEntity,
	normalizedSlugQuery: String,
	matchingCandidates: List<MangaEntity>,
	onMergeCandidateIntoParent: (candidate: MangaEntity) -> Unit,
	onDismiss: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		modifier = Modifier.testTag("cross_source_merge_search_sheet"),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
		) {
			Text(
				text = "Cross-Source Merge Search",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
			)
			Text(
				text = "Normalized Slug: $normalizedSlugQuery",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.height(12.dp))

			LazyColumn(
				verticalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				items(matchingCandidates, key = { it.id }) { candidate ->
					Card(
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = candidate.title,
									style = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold,
								)
								Text(
									text = candidate.source,
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.primary,
								)
							}
							Button(
								onClick = { onMergeCandidateIntoParent(candidate) },
								modifier = Modifier.testTag("merge_candidate_${candidate.id}"),
							) {
								Text("Merge into this Manga")
							}
						}
					}
				}
			}
			Spacer(modifier = Modifier.height(16.dp))
		}
	}
}
