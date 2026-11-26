package org.nemuri.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriSpatialSpring
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal swipe wrapper with snapping spring-back and threshold haptics.
 * - Swipe Right (> 88dp) triggers [onSwipeRight] (e.g., Toggle Read / Clear Update)
 * - Swipe Left (< -88dp) triggers [onSwipeLeft] (e.g., Download)
 */
@Composable
fun SnappingSwipeRow(
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	rightLabel: String = "Read",
	rightIcon: ImageVector = Icons.Default.DoneAll,
	rightTint: Color = NemuriTheme.colors.cyan,
	onSwipeRight: (() -> Unit)? = null,
	leftLabel: String = "Download",
	leftIcon: ImageVector = Icons.Default.Download,
	leftTint: Color = NemuriTheme.colors.healthy,
	onSwipeLeft: (() -> Unit)? = null,
	content: @Composable () -> Unit,
) {
	val prefs = LocalLayoutStudioPrefs.current
	val active = enabled && prefs.isSwipeActionsEnabled && (onSwipeRight != null || onSwipeLeft != null)
	if (!active) {
		Box(modifier = modifier) {
			content()
		}
		return
	}

	val density = LocalDensity.current
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val thresholdPx = with(density) { 84.dp.toPx() }
	val maxOffsetPx = with(density) { 126.dp.toPx() }
	val offsetX = remember { Animatable(0f) }
	var armedDirection by remember { mutableStateOf(0) }

	val dragModifier = Modifier.pointerInput(onSwipeRight != null, onSwipeLeft != null) {
		detectHorizontalDragGestures(
			onDragStart = {
				armedDirection = 0
			},
			onDragCancel = {
				armedDirection = 0
				scope.launch {
					offsetX.animateTo(0f, nemuriSpatialSpring())
				}
			},
			onDragEnd = {
				val current = offsetX.value
				when {
					current >= thresholdPx && onSwipeRight != null -> onSwipeRight.invoke()
					current <= -thresholdPx && onSwipeLeft != null -> onSwipeLeft.invoke()
				}
				armedDirection = 0
				scope.launch {
					offsetX.animateTo(0f, nemuriSpatialSpring())
				}
			},
			onHorizontalDrag = { change, dragAmount ->
				change.consume()
				val minTarget = if (onSwipeLeft != null) -maxOffsetPx else 0f
				val maxTarget = if (onSwipeRight != null) maxOffsetPx else 0f
				val target = (offsetX.value + dragAmount).coerceIn(minTarget, maxTarget)
				val nextDir = when {
					target >= thresholdPx && onSwipeRight != null -> 1
					target <= -thresholdPx && onSwipeLeft != null -> -1
					else -> 0
				}
				if (nextDir != 0 && nextDir != armedDirection) {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				}
				armedDirection = nextDir
				scope.launch {
					offsetX.snapTo(target)
				}
			},
		)
	}

	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center,
	) {
		val currentPx = offsetX.value
		if (abs(currentPx) > 2f) {
			val isRight = currentPx > 0f
			val accent = if (isRight) rightTint else leftTint
			val armed = abs(currentPx) >= thresholdPx
			Box(
				modifier = Modifier
					.matchParentSize()
					.padding(horizontal = 4.dp, vertical = 3.dp)
					.clip(RoundedCornerShape(14.dp))
					.background(accent.copy(alpha = if (armed) 0.24f else 0.11f))
					.padding(horizontal = 18.dp),
				contentAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (!isRight) {
						Text(
							text = leftLabel,
							style = MaterialTheme.typography.labelMedium,
							color = accent,
						)
					}
					Icon(
						imageVector = if (isRight) rightIcon else leftIcon,
						contentDescription = null,
						tint = accent,
						modifier = Modifier.size(18.dp),
					)
					if (isRight) {
						Spacer(modifier = Modifier.width(2.dp))
						Text(
							text = rightLabel,
							style = MaterialTheme.typography.labelMedium,
							color = accent,
						)
					}
				}
			}
		}

		Box(
			modifier = Modifier
				.offset { IntOffset(offsetX.value.roundToInt(), 0) }
				.then(dragModifier),
		) {
			content()
		}
	}
}
