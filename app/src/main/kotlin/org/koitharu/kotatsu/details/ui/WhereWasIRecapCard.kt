package org.koitharu.kotatsu.details.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.details.ai.RecapUiState

@Composable
fun WhereWasIRecapCard(
	state: RecapUiState,
	onRegenerateClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (state is RecapUiState.Hidden) return

	val (bodyText, daysStalled, isStreaming) = when (state) {
		is RecapUiState.Streaming -> Triple(state.partialText, state.daysStalled, true)
		is RecapUiState.Ready -> Triple(state.markdown, state.daysStalled, false)
		RecapUiState.Hidden -> return
	}

	ElevatedCard(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
			.animateContentSize()
			.testTag("where_was_i_recap_card"),
		shape = RoundedCornerShape(24.dp), // Material 3 Expressive hero curve
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(
						imageVector = Icons.Rounded.AutoAwesome,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = "Where Was I?",
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
					Spacer(modifier = Modifier.width(8.dp))
					Surface(
						color = MaterialTheme.colorScheme.secondaryContainer,
						shape = RoundedCornerShape(50),
					) {
						Text(
							text = if (daysStalled > 0) "${daysStalled}d ago • Spoiler-Free" else "Spoiler-Free",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSecondaryContainer,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
						)
					}
				}
				IconButton(
					onClick = onRegenerateClick,
					enabled = !isStreaming,
					modifier = Modifier.testTag("refresh_recap_button"),
				) {
					Icon(
						imageVector = Icons.Rounded.Refresh,
						contentDescription = "Refresh Recap",
					)
				}
			}
			Text(
				text = bodyText,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier
					.padding(top = 8.dp)
					.testTag("where_was_i_recap_body"),
			)
		}
	}
}
