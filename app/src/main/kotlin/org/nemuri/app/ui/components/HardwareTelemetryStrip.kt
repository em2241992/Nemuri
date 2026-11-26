package org.nemuri.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.koitharu.kotatsu.core.os.HardwareTelemetryProvider
import org.koitharu.kotatsu.reader.domain.PageUpscaleEngine
import org.nemuri.app.ui.theme.NemuriTheme

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HardwareTelemetryEntryPoint {
	fun telemetryProvider(): HardwareTelemetryProvider
	fun pageUpscaleEngine(): PageUpscaleEngine
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareTelemetryStrip(
	modifier: Modifier = Modifier,
	activeDelegateOverride: String? = null,
) {
	val appContext = LocalContext.current.applicationContext
	val entryPoint = remember(appContext) {
		EntryPointAccessors.fromApplication(appContext, HardwareTelemetryEntryPoint::class.java)
	}
	val snapshot by entryPoint.telemetryProvider().snapshot.collectAsState()
	val upscaleDelegate by entryPoint.pageUpscaleEngine().activeDelegateLabel.collectAsState()

	val rawDelegate = activeDelegateOverride?.takeIf { it.isNotBlank() } ?: upscaleDelegate
	val delegatePill = remember(rawDelegate) {
		if (rawDelegate.startsWith("Active:", ignoreCase = true) ||
			rawDelegate.startsWith("Idle", ignoreCase = true)
		) {
			rawDelegate
		} else {
			"Active: $rawDelegate"
		}
	}
	val isLiveActive = remember(delegatePill) {
		delegatePill.startsWith("Active:", ignoreCase = true) &&
			!delegatePill.contains("Idle", ignoreCase = true) &&
			!delegatePill.contains("Disabled", ignoreCase = true) &&
			!delegatePill.contains("Off", ignoreCase = true)
	}

	val ext = NemuriTheme.colors
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(14.dp),
		color = ext.surfaceElevated.copy(alpha = 0.85f),
		border = BorderStroke(1.dp, ext.border.copy(alpha = 0.7f)),
	) {
		Column(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth(),
			) {
				Icon(
					imageVector = Icons.Outlined.Memory,
					contentDescription = null,
					tint = ext.cyan,
					modifier = Modifier.size(15.dp),
				)
				Spacer(modifier = Modifier.width(6.dp))
				Text(
					text = snapshot.summaryLine,
					style = MaterialTheme.typography.labelSmall.copy(
						fontSize = 11.5.sp,
						fontWeight = FontWeight.SemiBold,
					),
					color = ext.textPrimary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
			}

			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Surface(
					shape = CircleShape,
					color = if (isLiveActive) ext.tealContainer.copy(alpha = 0.85f) else ext.card,
					border = BorderStroke(
						width = 1.dp,
						color = if (isLiveActive) ext.cyan.copy(alpha = 0.6f) else ext.border,
					),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					) {
						Box(
							modifier = Modifier
								.size(6.dp)
								.background(
									color = if (isLiveActive) ext.cyan else ext.textMuted,
									shape = CircleShape,
								),
						)
						Spacer(modifier = Modifier.width(5.dp))
						Text(
							text = delegatePill,
							style = MaterialTheme.typography.labelSmall.copy(
								fontSize = 10.5.sp,
								fontWeight = FontWeight.Medium,
							),
							color = if (isLiveActive) ext.cyan else ext.textSecondary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}
}
