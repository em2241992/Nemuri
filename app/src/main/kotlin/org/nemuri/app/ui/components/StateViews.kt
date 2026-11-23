package org.nemuri.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun NemuriLoadingState(
	message: String = "Loading…",
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			CircularProgressIndicator(
				color = NemuriTheme.colors.cyan,
				strokeWidth = 3.dp,
				modifier = Modifier.size(34.dp),
			)
			Text(
				text = message,
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textMuted,
			)
		}
	}
}

@Composable
fun NemuriEmptyState(
	icon: ImageVector,
	title: String,
	subtitle: String,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.fillMaxSize()
			.padding(32.dp),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = NemuriTheme.colors.textDim,
				modifier = Modifier.size(48.dp),
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)
			if (actionLabel != null && onAction != null) {
				Spacer(modifier = Modifier.height(8.dp))
				Button(
					onClick = onAction,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
				) {
					Text(actionLabel)
				}
			}
		}
	}
}

@Composable
fun NemuriErrorState(
	message: String,
	retryLabel: String = "Retry",
	onRetry: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	NemuriEmptyState(
		icon = Icons.Default.ErrorOutline,
		title = "Something went wrong",
		subtitle = message,
		actionLabel = if (onRetry != null) retryLabel else null,
		onAction = onRetry,
		modifier = modifier,
	)
}
