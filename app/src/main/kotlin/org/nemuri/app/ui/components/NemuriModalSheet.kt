package org.nemuri.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NemuriModalSheet(
	title: String,
	onDismiss: () -> Unit,
	subtitle: String? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = NemuriTheme.colors.sheet,
		contentColor = NemuriTheme.colors.textPrimary,
		shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.navigationBarsPadding()
				.padding(bottom = 24.dp),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(end = 12.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.titleLarge,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					if (!subtitle.isNullOrBlank()) {
						Text(
							text = subtitle,
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = CircleShape,
					modifier = Modifier
						.size(36.dp)
						.clip(CircleShape)
						.clickable(onClick = onDismiss),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Close",
							tint = NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}

			HorizontalDivider(
				color = NemuriTheme.colors.border,
				modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
			)

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp),
				content = content,
			)
		}
	}
}
