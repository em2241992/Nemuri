package org.nemuri.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val YorhaVisorIvory = Color(0xFFDAD4BB)
private val YorhaVisorSepiaInk = Color(0xFF3D3A33)

/**
 * Polarity-aware zero-allocation 120Hz Android tactical HUD background overlay for
 * [org.koitharu.kotatsu.core.prefs.ColorScheme.YORHA_VISOR_V2].
 *
 * - Dark & AMOLED (`isDark == true`): warm YoRHa Ivory (`#DAD4BB`) at `0.09f` minor `24.dp` grid,
 *   `0.16f` major `96.dp` grid & `+` crosshairs, `0.03f` scanlines, and `0.26f` `[ ]` corner reticles.
 * - Light (`isDark == false`): dark tactical sepia/charcoal ink (`#3D3A33`) at `0.08f` minor `24.dp` grid,
 *   `0.14f` major `96.dp` grid & `+` crosshairs, `0.03f` scanlines, and `0.24f` `[ ]` corner reticles.
 */
@Composable
fun YorhaVisorHudBackground(
	modifier: Modifier = Modifier,
	enabled: Boolean = LocalIsYorhaVisor.current,
	content: @Composable BoxScope.() -> Unit = {},
) {
	val ext = NemuriTheme.colors
	val hudModifier = if (enabled) {
		modifier.yorhaVisorHudCanvas(
			hudTint = ext.cyan,
			borderTint = ext.border,
			isDark = ext.isDark,
		)
	} else {
		modifier
	}
	Box(
		modifier = hudModifier,
		content = content,
	)
}

@Suppress("UNUSED_PARAMETER")
fun Modifier.yorhaVisorHudCanvas(
	hudTint: Color = YorhaVisorIvory,
	borderTint: Color = YorhaVisorIvory,
	isDark: Boolean,
): Modifier = this.drawWithCache {
	val gridStepPx = 24.dp.toPx().coerceAtLeast(8f)
	val scanlineStepPx = 3.dp.toPx().coerceAtLeast(2f)
	val hairlinePx = 1.dp.toPx().coerceAtMost(1.4f)
	val crosshairStrokePx = 1.2.dp.toPx().coerceAtMost(1.65f)
	val tickHalfPx = 4.dp.toPx()
	val reticleInsetX = 10.dp.toPx()
	val reticleInsetTop = 38.dp.toPx()
	val reticleInsetBottom = 22.dp.toPx()
	val reticleArmPx = 14.dp.toPx()
	val reticleStrokePx = 1.35.dp.toPx()

	val baseVisorInk = if (isDark) YorhaVisorIvory else YorhaVisorSepiaInk
	val minorGridColor = baseVisorInk.copy(alpha = if (isDark) 0.09f else 0.08f)
	val majorGridColor = baseVisorInk.copy(alpha = if (isDark) 0.16f else 0.14f)
	val tickColor = baseVisorInk.copy(alpha = if (isDark) 0.16f else 0.14f)
	val scanlineColor = baseVisorInk.copy(alpha = 0.03f)
	val reticleColor = baseVisorInk.copy(alpha = if (isDark) 0.26f else 0.24f)

	onDrawBehind {
		val w = size.width
		val h = size.height
		if (w <= 0f || h <= 0f) return@onDrawBehind

		// 1. 24.dp minor coordinate hairlines + 96.dp (every 4th cell) major grid lines
		var col = 0
		var x = gridStepPx * 0.5f
		while (x < w) {
			val isMajorCol = col % 4 == 0
			drawLine(
				color = if (isMajorCol) majorGridColor else minorGridColor,
				start = Offset(x, 0f),
				end = Offset(x, h),
				strokeWidth = hairlinePx,
			)
			x += gridStepPx
			col++
		}

		var row = 0
		var y = gridStepPx * 0.5f
		while (y < h) {
			val isMajorRow = row % 4 == 0
			drawLine(
				color = if (isMajorRow) majorGridColor else minorGridColor,
				start = Offset(0f, y),
				end = Offset(w, y),
				strokeWidth = hairlinePx,
			)
			// + Crosshair reticles at every 96.dp major intersection
			if (isMajorRow) {
				var tickCol = 0
				var tx = gridStepPx * 0.5f
				while (tx < w) {
					if (tickCol % 4 == 0) {
						drawLine(
							color = tickColor,
							start = Offset(tx - tickHalfPx, y),
							end = Offset(tx + tickHalfPx, y),
							strokeWidth = crosshairStrokePx,
						)
						drawLine(
							color = tickColor,
							start = Offset(tx, y - tickHalfPx),
							end = Offset(tx, y + tickHalfPx),
							strokeWidth = crosshairStrokePx,
						)
					}
					tx += gridStepPx
					tickCol++
				}
			}
			y += gridStepPx
			row++
		}

		// 2. Faint horizontal visor scanlines (3% alpha every 3.dp)
		var sy = 0f
		while (sy < h) {
			drawLine(
				color = scanlineColor,
				start = Offset(0f, sy),
				end = Offset(w, sy),
				strokeWidth = 1f,
			)
			sy += scanlineStepPx
		}

		// 3. Perimeter [ ] tactical HUD corner reticles
		val left = reticleInsetX
		val right = (w - reticleInsetX).coerceAtLeast(left)
		val top = reticleInsetTop.coerceAtMost(h * 0.25f)
		val bottom = (h - reticleInsetBottom).coerceAtLeast(top)
		if (right - left > reticleArmPx * 2.5f && bottom - top > reticleArmPx * 2.5f) {
			// Top-Left [
			drawLine(reticleColor, Offset(left, top), Offset(left + reticleArmPx, top), reticleStrokePx)
			drawLine(reticleColor, Offset(left, top), Offset(left, top + reticleArmPx), reticleStrokePx)
			// Top-Right ]
			drawLine(reticleColor, Offset(right - reticleArmPx, top), Offset(right, top), reticleStrokePx)
			drawLine(reticleColor, Offset(right, top), Offset(right, top + reticleArmPx), reticleStrokePx)
			// Bottom-Left [
			drawLine(reticleColor, Offset(left, bottom - reticleArmPx), Offset(left, bottom), reticleStrokePx)
			drawLine(reticleColor, Offset(left, bottom), Offset(left + reticleArmPx, bottom), reticleStrokePx)
			// Bottom-Right ]
			drawLine(reticleColor, Offset(right, bottom - reticleArmPx), Offset(right, bottom), reticleStrokePx)
			drawLine(reticleColor, Offset(right - reticleArmPx, bottom), Offset(right, bottom), reticleStrokePx)
		}
	}
}

/**
 * Draws subtle `[ ]` YoRHa tactical bracket accents on section headers, cards, or the navigation dock.
 * Zero-cost no-op unless [LocalIsYorhaVisor] is active (or [forceEnabled] is true for theme previews).
 */
@Composable
fun Modifier.yorhaTacticalBrackets(
	color: Color = (if (NemuriTheme.colors.isDark) YorhaVisorIvory else YorhaVisorSepiaInk).copy(
		alpha = if (NemuriTheme.colors.isDark) 0.50f else 0.44f,
	),
	bracketLength: Dp = 7.dp,
	strokeWidth: Dp = 1.25.dp,
	inset: Dp = 1.5.dp,
	forceEnabled: Boolean = false,
): Modifier {
	if (!forceEnabled && !LocalIsYorhaVisor.current) {
		return this
	}
	return this.drawWithCache {
		val arm = bracketLength.toPx()
		val stroke = strokeWidth.toPx()
		val pad = inset.toPx()
		onDrawBehind {
			val left = pad
			val right = (size.width - pad).coerceAtLeast(left)
			val top = pad
			val bottom = (size.height - pad).coerceAtLeast(top)
			val hArm = arm.coerceAtMost((right - left) * 0.3f)
			val vArm = arm.coerceAtMost((bottom - top) * 0.42f)
			if (hArm <= 0f || vArm <= 0f) return@onDrawBehind

			// Left [ bracket
			drawLine(color, Offset(left, top), Offset(left + hArm, top), stroke)
			drawLine(color, Offset(left, top), Offset(left, top + vArm), stroke)
			drawLine(color, Offset(left, bottom - vArm), Offset(left, bottom), stroke)
			drawLine(color, Offset(left, bottom), Offset(left + hArm, bottom), stroke)

			// Right ] bracket
			drawLine(color, Offset(right - hArm, top), Offset(right, top), stroke)
			drawLine(color, Offset(right, top), Offset(right, top + vArm), stroke)
			drawLine(color, Offset(right, bottom - vArm), Offset(right, bottom), stroke)
			drawLine(color, Offset(right - hArm, bottom), Offset(right, bottom), stroke)
		}
	}
}
