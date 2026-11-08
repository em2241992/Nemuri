package org.koitharu.kotatsu.details.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.ai.PluggableAiRouter
import org.koitharu.kotatsu.details.ai.RecapUiState
import org.koitharu.kotatsu.ui.components.DismissibleCard
import org.koitharu.kotatsu.ui.components.NonWrappingBadgeText
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily

@Composable
fun WhereWasIRecapCard(
	state: RecapUiState,
	onRegenerateClick: () -> Unit,
	onCloseClick: () -> Unit = {},
	activeEngineBadge: String = PluggableAiRouter.currentConfig.activeAttributionBadge(),
	modifier: Modifier = Modifier,
) {
	if (state is RecapUiState.Hidden) return

	val (bodyText, _, isStreaming) = when (state) {
		is RecapUiState.Streaming -> Triple(state.partialText, state.daysStalled, true)
		is RecapUiState.Ready -> Triple(state.markdown, state.daysStalled, false)
		RecapUiState.Hidden -> return
	}

	val accentCyan = Color(0xFF5CE1E6)
	val cleanEngineName = activeEngineBadge.removePrefix("✦ ").trim().ifEmpty { "Gemini Nano" }

	DismissibleCard(
		onClose = onCloseClick,
		modifier = modifier
			.fillMaxWidth()
			.animateContentSize()
			.testTag("where_was_i_recap_card"),
		containerColor = Color(0xFF151A26),
		borderColor = accentCyan.copy(alpha = 0.25f),
		cornerRadius = 16.dp,
		closeButtonTestTag = "close_recap_button",
		headerLeadingContent = {
			Icon(
				imageVector = Icons.Rounded.AutoAwesome,
				contentDescription = null,
				tint = accentCyan,
				modifier = Modifier.size(18.dp),
			)
			NonWrappingBadgeText(
				text = "Where Was I?",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Bold,
				color = Color.White,
			)
			Surface(
				color = accentCyan.copy(alpha = 0.16f),
				border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.38f)),
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier.testTag("where_was_i_engine_badge"),
			) {
				NonWrappingBadgeText(
					text = "$cleanEngineName • Spoiler-Free",
					style = MaterialTheme.typography.labelSmall.copy(
						fontFamily = JetBrainsMonoFamily,
						fontSize = 11.sp,
					),
					fontWeight = FontWeight.Bold,
					color = accentCyan,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
				)
			}
		},
		headerTrailingActions = {
			IconButton(
				onClick = onRegenerateClick,
				enabled = !isStreaming,
				modifier = Modifier
					.size(32.dp)
					.testTag("refresh_recap_button"),
			) {
				Icon(
					imageVector = Icons.Rounded.Refresh,
					contentDescription = "Refresh Recap",
					tint = Color(0xFFCBD5E1),
					modifier = Modifier.size(18.dp),
				)
			}
		},
	) {
		Text(
			text = bodyText,
			style = MaterialTheme.typography.bodyMedium,
			color = Color(0xFFE2E8F0),
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 8.dp)
				.testTag("where_was_i_recap_body"),
		)
	}
}
