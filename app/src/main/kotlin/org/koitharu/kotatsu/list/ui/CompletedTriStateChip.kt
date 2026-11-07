package org.koitharu.kotatsu.list.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class CompletionFilterState {
	ALL,
	COMPLETED_ONLY,
	ONGOING_ONLY;

	fun next(): CompletionFilterState = when (this) {
		ALL -> COMPLETED_ONLY
		COMPLETED_ONLY -> ONGOING_ONLY
		ONGOING_ONLY -> ALL
	}
}

@Composable
fun CompletedTriStateFilterChip(
	state: CompletionFilterState,
	onStateChange: (CompletionFilterState) -> Unit,
	modifier: Modifier = Modifier,
) {
	val isSelected = state != CompletionFilterState.ALL
	val isExcluded = state == CompletionFilterState.ONGOING_ONLY

	FilterChip(
		selected = isSelected,
		onClick = { onStateChange(state.next()) },
		modifier = modifier.testTag("completed_tri_state_chip"),
		label = {
			Text(
				text = when (state) {
					CompletionFilterState.ALL -> "Completed"
					CompletionFilterState.COMPLETED_ONLY -> "Completed"
					CompletionFilterState.ONGOING_ONLY -> "Not Completed"
				},
			)
		},
		leadingIcon = if (isSelected) {
			{
				Icon(
					imageVector = if (isExcluded) Icons.Rounded.Close else Icons.Rounded.Check,
					contentDescription = null,
				)
			}
		} else {
			null
		},
		colors = if (isExcluded) {
			FilterChipDefaults.filterChipColors(
				selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
				selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
				selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
			)
		} else {
			FilterChipDefaults.filterChipColors()
		},
	)
}
