package org.nemuri.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nemuri.app.ui.theme.NemuriTheme
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class NemuriToast(
	val id: Long,
	val message: String,
	val actionLabel: String? = null,
	val onAction: (() -> Unit)? = null,
)

@Singleton
class NemuriToastManager @Inject constructor() {

	private val nextId = AtomicLong(1L)
	private val _currentToast = MutableStateFlow<NemuriToast?>(null)
	val currentToast: StateFlow<NemuriToast?> = _currentToast.asStateFlow()

	fun show(
		message: String,
		actionLabel: String? = null,
		onAction: (() -> Unit)? = null,
	) {
		_currentToast.value = NemuriToast(
			id = nextId.getAndIncrement(),
			message = message,
			actionLabel = actionLabel,
			onAction = onAction,
		)
	}

	fun dismiss() {
		_currentToast.value = null
	}
}

@Composable
fun NemuriCapsuleToastHost(
	toastManager: NemuriToastManager,
	bottomPadding: Dp = 92.dp,
	modifier: Modifier = Modifier,
) {
	val toast by toastManager.currentToast.collectAsStateWithLifecycle()

	LaunchedEffect(toast?.id) {
		val current = toast ?: return@LaunchedEffect
		delay(if (current.actionLabel != null) 4500L else 2600L)
		if (toastManager.currentToast.value?.id == current.id) {
			toastManager.dismiss()
		}
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.navigationBarsPadding()
			.padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
			.zIndex(9999f),
		contentAlignment = Alignment.BottomCenter,
	) {
		val easeOutCubic = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
		AnimatedVisibility(
			visible = toast != null,
			enter = fadeIn(tween(240, easing = easeOutCubic)) +
				slideInVertically(tween(240, easing = easeOutCubic)) { it / 2 },
			exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
		) {
			val item = toast ?: return@AnimatedVisibility
			Surface(
				color = NemuriTheme.colors.toast,
				shape = RoundedCornerShape(999.dp),
				shadowElevation = 16.dp,
				modifier = Modifier
					.widthIn(max = 360.dp)
					.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
					.clickable { toastManager.dismiss() },
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					NemuriBrandLogo(modifier = Modifier.size(20.dp))
					Text(
						text = item.message,
						style = MaterialTheme.typography.labelLarge,
						color = Color(0xFFF1F5F9),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (item.actionLabel != null && item.onAction != null) {
						Surface(
							color = NemuriTheme.colors.cyan.copy(alpha = 0.16f),
							shape = RoundedCornerShape(999.dp),
							modifier = Modifier.clickable {
								item.onAction.invoke()
								toastManager.dismiss()
							},
						) {
							Text(
								text = item.actionLabel,
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.Bold,
								color = NemuriTheme.colors.cyan,
								maxLines = 1,
								modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}
	}
}
