package org.nemuri.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.core.github.AppVersion
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.yorhaTacticalBrackets

/**
 * Donor tonal In-App GitHub Release Update Banner displayed cleanly below the top search bar
 * when a newer Nemuri GitHub release APK is available for the device's flavor/ABI.
 */
@Composable
fun NemuriUpdateBanner(
	version: AppVersion,
	isDownloading: Boolean,
	downloadProgress: Float?,
	onInstall: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val containerShape = NemuriTheme.shape(16.dp)
	val pillShape = NemuriTheme.pillShape()

	Surface(
		color = NemuriTheme.colors.tealContainer.copy(alpha = 0.86f),
		shape = containerShape,
		border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp)
			.yorhaTacticalBrackets(inset = 3.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 9.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Icon(
					imageVector = Icons.Default.CloudDownload,
					contentDescription = "Update available",
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(18.dp),
				)
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "Update available • v${version.name}",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					if (isDownloading) {
						val pctLabel = downloadProgress
							?.takeIf { it.isFinite() }
							?.let { "${(it * 100f).toInt().coerceIn(0, 100)}%" }
							?: "Downloading…"
						Text(
							text = "Downloading installer ($pctLabel)",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.cyan,
							maxLines = 1,
						)
					}
				}
				Button(
					onClick = onInstall,
					enabled = !isDownloading,
					shape = pillShape,
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					modifier = Modifier.height(32.dp),
				) {
					if (isDownloading) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.onCyan,
							strokeWidth = 2.dp,
							modifier = Modifier.size(14.dp),
						)
					} else {
						Text(
							text = "Install",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
						)
					}
				}
				IconButton(
					onClick = onDismiss,
					enabled = !isDownloading,
					modifier = Modifier.size(28.dp),
				) {
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = "Dismiss update banner",
						tint = NemuriTheme.colors.textMuted,
						modifier = Modifier.size(16.dp),
					)
				}
			}

			if (isDownloading) {
				val fraction = downloadProgress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
				if (fraction != null) {
					LinearProgressIndicator(
						progress = { fraction },
						color = NemuriTheme.colors.cyan,
						trackColor = NemuriTheme.colors.border,
						modifier = Modifier
							.fillMaxWidth()
							.height(3.dp),
					)
				} else {
					LinearProgressIndicator(
						color = NemuriTheme.colors.cyan,
						trackColor = NemuriTheme.colors.border,
						modifier = Modifier
							.fillMaxWidth()
							.height(3.dp),
					)
				}
			}
		}
	}
}

/**
 * Donor personalized manga suggestions opt-in card shown once until the user chooses Enable or No thanks.
 */
@Composable
fun NemuriSuggestionsOptInCard(
	onEnable: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val cardShape = NemuriTheme.shape(20.dp)
	val badgeShape = NemuriTheme.shape(14.dp)
	val buttonShape = NemuriTheme.shape(12.dp)

	Surface(
		color = NemuriTheme.colors.card,
		shape = cardShape,
		border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.35f)),
		tonalElevation = 2.dp,
		modifier = modifier
			.fillMaxWidth()
			.yorhaTacticalBrackets(inset = 4.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(18.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Surface(
				color = NemuriTheme.colors.tealContainer,
				shape = badgeShape,
				border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f)),
				modifier = Modifier.size(44.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Default.Lightbulb,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(24.dp),
					)
				}
			}

			Text(
				text = "Do you want to receive personalized manga suggestions?",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)

			Text(
				text = "Nemuri analyzes your library genres and reading history locally on-device to curate tailored picks in For You and Hub.",
				style = MaterialTheme.typography.bodySmall,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)

			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Button(
					onClick = onEnable,
					shape = buttonShape,
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					modifier = Modifier
						.fillMaxWidth()
						.height(44.dp),
				) {
					Text(
						text = "Enable",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
					)
				}

				OutlinedButton(
					onClick = onDismiss,
					shape = buttonShape,
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier
						.fillMaxWidth()
						.height(42.dp),
				) {
					Text(
						text = "No thanks",
						style = MaterialTheme.typography.labelLarge,
						color = NemuriTheme.colors.textSecondary,
					)
				}
			}
		}
	}
}
