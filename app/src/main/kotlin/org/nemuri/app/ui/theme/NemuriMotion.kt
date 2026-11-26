package org.nemuri.app.ui.theme

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalReducedMotion = staticCompositionLocalOf { false }

fun <T> nemuriSpatialSpring() = spring<T>(
	dampingRatio = 0.8f,
	stiffness = 380f,
)

fun <T> nemuriFastSpatialSpring() = spring<T>(
	dampingRatio = 0.78f,
	stiffness = 560f,
)

fun <T> nemuriEffectsSpring() = spring<T>(
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness = 600f,
)

@Composable
fun rememberShouldReduceMotion(): Boolean {
	val context = LocalContext.current
	return remember(context) {
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
		val isPowerSave = powerManager?.isPowerSaveMode == true
		val animatorScale = runCatching {
			Settings.Global.getFloat(
				context.contentResolver,
				Settings.Global.ANIMATOR_DURATION_SCALE,
				1f,
			)
		}.getOrDefault(1f)
		isPowerSave || animatorScale == 0f
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.nemuriSharedCover(
	key: String?,
	cornerRadiusDp: Dp,
): Modifier = composed {
	if (key.isNullOrBlank() || LocalReducedMotion.current) {
		return@composed this
	}
	val sharedScope = LocalSharedTransitionScope.current ?: return@composed this
	val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return@composed this
	with(sharedScope) {
		this@composed
			.sharedElement(
				sharedContentState = rememberSharedContentState(key = key),
				animatedVisibilityScope = animatedScope,
				clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerRadiusDp)),
			)
			.clip(RoundedCornerShape(cornerRadiusDp))
	}
}
