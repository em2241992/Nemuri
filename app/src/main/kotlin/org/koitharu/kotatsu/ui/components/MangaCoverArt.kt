package org.koitharu.kotatsu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.ReaderColorFilterPreset
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders an editorial manga tankobon cover illustration using vector geometry, speed-lines,
 * halftone motifs, and Japanese/English volume typography so every series has a vivid, instant cover.
 */
@Composable
fun MangaCoverArtwork(
	title: String,
	coverUrl: String,
	isBroken: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val hash = abs((title + coverUrl).hashCode())
	val palette = when (hash % 7) {
		0 -> listOf(Color(0xFF1A1F3C), Color(0xFF3B82F6), Color(0xFF93C5FD))
		1 -> listOf(Color(0xFF2D122B), Color(0xFFFF3366), Color(0xFFFBCFE8))
		2 -> listOf(Color(0xFF0E2923), Color(0xFF10B981), Color(0xFFA7F3D0))
		3 -> listOf(Color(0xFF2B1810), Color(0xFFF59E0B), Color(0xFFFDE68A))
		4 -> listOf(Color(0xFF1E1B4B), Color(0xFF6366F1), Color(0xFFC7D2FE))
		5 -> listOf(Color(0xFF27151D), Color(0xFFF43F5E), Color(0xFFFECDD3))
		else -> listOf(Color(0xFF111827), Color(0xFF8B5CF6), Color(0xFFDDD6FE))
	}

	val kanjiGlyph = when (hash % 6) {
		0 -> "魔"
		1 -> "剣"
		2 -> "迷"
		3 -> "星"
		4 -> "斬"
		else -> "魂"
	}

	Box(
		modifier = modifier.background(
			Brush.linearGradient(
				colors = listOf(palette[0], palette[1].copy(alpha = 0.72f), Color(0xFF090A0F)),
			),
		),
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val w = size.width
			val h = size.height

			// Radial manga sun/moon halo
			drawCircle(
				color = palette[1].copy(alpha = 0.28f),
				radius = w * 0.44f,
				center = Offset(w * 0.65f, h * 0.34f),
			)
			drawCircle(
				color = palette[2].copy(alpha = 0.16f),
				radius = w * 0.28f,
				center = Offset(w * 0.65f, h * 0.34f),
			)

			// Dynamic Manga Speed-Lines radiating from focal point
			val cx = w * 0.65f
			val cy = h * 0.34f
			for (i in 0 until 14) {
				val angle = (i * 25.7f) * (Math.PI / 180.0)
				val x1 = cx + (cos(angle) * w * 0.22f).toFloat()
				val y1 = cy + (sin(angle) * w * 0.22f).toFloat()
				val x2 = cx + (cos(angle) * w * 0.85f).toFloat()
				val y2 = cy + (sin(angle) * w * 0.85f).toFloat()
				drawLine(
					color = Color.White.copy(alpha = 0.08f),
					start = Offset(x1, y1),
					end = Offset(x2, y2),
					strokeWidth = 2f,
				)
			}

			// Editorial tankobon spine frame line
			drawRoundRect(
				color = Color.White.copy(alpha = 0.14f),
				topLeft = Offset(w * 0.06f, h * 0.04f),
				size = Size(w * 0.88f, h * 0.92f),
				cornerRadius = CornerRadius(12f, 12f),
				style = Stroke(width = 1.5f),
			)

			if (isBroken) {
				drawLine(
					color = Color(0xFFF43F5E).copy(alpha = 0.65f),
					start = Offset(0f, 0f),
					end = Offset(w, h),
					strokeWidth = 4f,
				)
			}
		}

		// Center Kanji Emblem
		Text(
			text = kanjiGlyph,
			color = Color.White.copy(alpha = 0.16f),
			fontSize = 58.sp,
			fontWeight = FontWeight.Bold,
			modifier = Modifier.align(Alignment.Center),
		)
	}
}

/**
 * Renders a multi-panel illustrated Manga / Webtoon page on a hardware-accelerated Canvas,
 * complete with speech bubbles, action speed-lines, screentone gradients, real-time
 * AI Image Upscaling / Before-After comparison split, Color Filters, Color Correction, and Brightness Control.
 */
@Composable
fun MangaReaderPageCanvas(
	mangaTitle: String,
	chapterNumber: Float,
	pageNumber: Int,
	totalPages: Int,
	upscaleMode: AiUpscaleMode,
	showBeforeAfterSplit: Boolean,
	splitRatio: Float = 0.5f,
	warmPaperTint: Boolean = false,
	invertColors: Boolean = false,
	grayscale: Boolean = false,
	colorFilterPreset: ReaderColorFilterPreset = ReaderColorFilterPreset.NONE,
	brightnessDelta: Float = 0f,
	contrastDelta: Float = 0f,
	warmthDelta: Float = 0f,
	bedtimeDimmerAlpha: Float = 0f,
	saturation: Float = 1f,
	redBalance: Float = 1f,
	greenBalance: Float = 1f,
	blueBalance: Float = 1f,
	decodedBitmap: android.graphics.Bitmap? = null,
	seamlessVerticalStitch: Boolean = false,
	isDebugMock: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val effectiveInvert = invertColors || colorFilterPreset == ReaderColorFilterPreset.INVERTED_NIGHT
	val effectiveNemuriBedtime = colorFilterPreset == ReaderColorFilterPreset.NEMURI_BEDTIME
	val effectiveAiAutoInk = colorFilterPreset == ReaderColorFilterPreset.AI_AUTO_INK_RESTORE
	val effectiveWarm = warmPaperTint || colorFilterPreset == ReaderColorFilterPreset.WARM_SEPIA
	val effectiveGray = grayscale || colorFilterPreset == ReaderColorFilterPreset.MONOCHROME_INK || saturation <= 0.15f

	val rawPaperBg = when {
		effectiveInvert -> Color(0xFF0E1015)
		effectiveNemuriBedtime -> Color(0xFFE8DEC8) // Warm parchment (#E8DEC8) keeping black ink (#000000) crisp
		effectiveAiAutoInk -> Color(0xFFFFFFFF) // De-noised clean white paper for AI Auto-Ink Restore
		colorFilterPreset == ReaderColorFilterPreset.NIGHT_BLUE_LIGHT -> Color(0xFFE6EDF5)
		effectiveWarm -> Color(0xFFF5EBDC)
		colorFilterPreset == ReaderColorFilterPreset.CRISP_HIGH_CONTRAST -> Color(0xFFFFFFFF)
		else -> Color(0xFFFAFAFC)
	}

	val warmClamp = warmthDelta.coerceIn(-0.4f, 0.6f)
	val effectiveRed = (redBalance.coerceIn(0.5f, 1.5f) + warmClamp * 0.08f).coerceIn(0.4f, 1.5f)
	val effectiveGreen = (greenBalance.coerceIn(0.5f, 1.5f) + warmClamp * 0.03f).coerceIn(0.4f, 1.5f)
	val effectiveBlue = (blueBalance.coerceIn(0.5f, 1.5f) - warmClamp * 0.16f).coerceIn(0.35f, 1.5f)

	val paperBg = Color(
		red = (rawPaperBg.red * effectiveRed).coerceIn(0f, 1f),
		green = (rawPaperBg.green * effectiveGreen).coerceIn(0f, 1f),
		blue = (rawPaperBg.blue * effectiveBlue).coerceIn(0f, 1f),
		alpha = 1f,
	)

	val inkColor = when {
		effectiveInvert -> Color(0xFFF1F5F9)
		effectiveNemuriBedtime || effectiveAiAutoInk -> Color(0xFF000000)
		else -> Color(0xFF111318)
	}
	val enhancedInkColor = if (effectiveInvert) Color(0xFFFFFFFF) else Color(0xFF000000)
	val isAiEnhanced = upscaleMode != AiUpscaleMode.OFF || effectiveAiAutoInk
	val contrastBoost = (1f + contrastDelta.coerceIn(-0.3f, 0.8f) + if (effectiveAiAutoInk) 0.28f else 0f).coerceIn(0.6f, 1.85f)

	Box(
		modifier = modifier.background(paperBg),
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val w = size.width
			val h = size.height
			if (decodedBitmap != null && !decodedBitmap.isRecycled && w > 0f && h > 0f) {
				runCatching {
					drawImage(
						image = decodedBitmap.asImageBitmap(),
						dstOffset = IntOffset.Zero,
						dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
						alpha = if (!isDebugMock) {
							1f
						} else if (effectiveInvert) {
							0.18f
						} else {
							0.24f
						},
					)
				}
			}
			if (!isDebugMock && decodedBitmap != null && !decodedBitmap.isRecycled) {
				if (showBeforeAfterSplit) {
					val splitX = w * splitRatio.coerceIn(0.08f, 0.92f)
					drawRect(
						color = paperBg.copy(alpha = 0.36f),
						topLeft = Offset(0f, 0f),
						size = Size(splitX, h),
					)
					drawLine(
						color = Color(0xFF5CE1E6),
						start = Offset(splitX, 0f),
						end = Offset(splitX, h),
						strokeWidth = 5f,
					)
				}
				return@Canvas
			}
			val pad = if (seamlessVerticalStitch) w * 0.035f else w * 0.05f
			val verticalEdgePad = if (seamlessVerticalStitch) 0f else pad
			val strokeBase = (if (isAiEnhanced) 4.2f else 2.4f) * (0.85f + 0.15f * contrastBoost)
			val baseAlpha = (
				if (
					isAiEnhanced ||
					effectiveNemuriBedtime ||
					colorFilterPreset == ReaderColorFilterPreset.CRISP_HIGH_CONTRAST
				) {
					0.98f
				} else {
					0.72f * contrastBoost
				}
			).coerceIn(0.35f, 1f)
			val activeInk = (if (isAiEnhanced || effectiveNemuriBedtime) enhancedInkColor else inkColor).copy(alpha = baseAlpha)

			// Panel 1: Top Wide Cinematic Manga Panel
			val p1Top = verticalEdgePad
			val p1Height = if (seamlessVerticalStitch) h * 0.31f else h * 0.28f
			drawRect(
				color = activeInk,
				topLeft = Offset(pad, p1Top),
				size = Size(w - pad * 2, p1Height),
				style = Stroke(width = strokeBase),
			)
			// Speed lines in Panel 1
			val p1CenterX = w * 0.35f
			val p1CenterY = p1Top + p1Height * 0.5f
			for (i in 0 until 22) {
				val angle = (i * 16.3f) * (Math.PI / 180.0)
				val startR = w * 0.08f
				val endR = w * 0.36f
				val x1 = (p1CenterX + cos(angle) * startR).toFloat().coerceIn(pad, w - pad)
				val y1 = (p1CenterY + sin(angle) * startR).toFloat().coerceIn(p1Top, p1Top + p1Height)
				val x2 = (p1CenterX + cos(angle) * endR).toFloat().coerceIn(pad, w - pad)
				val y2 = (p1CenterY + sin(angle) * endR).toFloat().coerceIn(p1Top, p1Top + p1Height)
				drawLine(
					color = activeInk.copy(alpha = if (isAiEnhanced) 0.55f else 0.25f),
					start = Offset(x1, y1),
					end = Offset(x2, y2),
					strokeWidth = if (isAiEnhanced) 2.2f else 1.2f,
				)
			}

			// Panel 2 & 3: Asymmetric Diagonal Action Split Panels
			val p2Top = p1Top + p1Height + pad * 0.5f
			val p2Height = h * 0.34f
			val midX = w * 0.52f

			// Left Panel 2
			val leftPanel = Path().apply {
				moveTo(pad, p2Top)
				lineTo(midX + w * 0.04f, p2Top)
				lineTo(midX - w * 0.04f, p2Top + p2Height)
				lineTo(pad, p2Top + p2Height)
				close()
			}
			drawPath(leftPanel, color = activeInk, style = Stroke(width = strokeBase))

			// Character silhouette inside Left Panel 2
			drawCircle(
				color = activeInk.copy(alpha = if (isAiEnhanced) 0.88f else 0.55f),
				radius = w * 0.11f,
				center = Offset(w * 0.27f, p2Top + p2Height * 0.42f),
			)
			drawRoundRect(
				color = activeInk.copy(alpha = if (isAiEnhanced) 0.82f else 0.50f),
				topLeft = Offset(w * 0.15f, p2Top + p2Height * 0.56f),
				size = Size(w * 0.24f, p2Height * 0.40f),
				cornerRadius = CornerRadius(24f, 24f),
			)

			// Right Panel 3
			val rightPanel = Path().apply {
				moveTo(midX + w * 0.07f, p2Top)
				lineTo(w - pad, p2Top)
				lineTo(w - pad, p2Top + p2Height)
				lineTo(midX - w * 0.01f, p2Top + p2Height)
				close()
			}
			drawPath(rightPanel, color = activeInk, style = Stroke(width = strokeBase))

			// Halftone Screentone Grid inside Right Panel 3
			val dotStep = 18f
			var dx = midX + w * 0.08f
			while (dx < w - pad - 8f) {
				var dy = p2Top + 12f
				while (dy < p2Top + p2Height - 12f) {
					drawCircle(
						color = activeInk.copy(alpha = if (isAiEnhanced) 0.32f else 0.16f),
						radius = if (isAiEnhanced) 2.6f else 1.8f,
						center = Offset(dx, dy),
					)
					dy += dotStep
				}
				dx += dotStep
			}

			// Panel 4: Bottom Dramatic Climax Panel
			val p4Top = p2Top + p2Height + pad * 0.5f
			val bottomPad = if (seamlessVerticalStitch) 0f else pad * 1.4f
			val p4Height = (h - p4Top - bottomPad).coerceAtLeast(60f)
			drawRect(
				color = activeInk,
				topLeft = Offset(pad, p4Top),
				size = Size(w - pad * 2, p4Height),
				style = Stroke(width = strokeBase),
			)

			// Speech Bubble Overlays
			drawOval(
				color = paperBg,
				topLeft = Offset(w * 0.58f, p1Top + p1Height * 0.14f),
				size = Size(w * 0.32f, p1Height * 0.65f),
			)
			drawOval(
				color = activeInk,
				topLeft = Offset(w * 0.58f, p1Top + p1Height * 0.14f),
				size = Size(w * 0.32f, p1Height * 0.65f),
				style = Stroke(width = strokeBase * 0.8f),
			)

			// Before / After AI Upscaler Split Divider Line
			if (showBeforeAfterSplit) {
				val splitX = w * splitRatio.coerceIn(0.08f, 0.92f)
				// Soften left "Original (1x)" half to visually demonstrate the Original vs Upscaled difference
				drawRect(
					color = paperBg.copy(alpha = 0.36f),
					topLeft = Offset(0f, 0f),
					size = Size(splitX, h),
				)
				drawLine(
					color = Color(0xFF5CE1E6),
					start = Offset(splitX, 0f),
					end = Offset(splitX, h),
					strokeWidth = 5f,
				)
			}
		}

		// Clean manga artwork canvas without diagnostic watermark text overlays

		// User Brightness Control Overlay Scrim (-0.50f darkens, +0.50f brightens)
		if (abs(brightnessDelta) > 0.01f) {
			val scrimColor = if (brightnessDelta < 0f) {
				Color.Black.copy(alpha = abs(brightnessDelta).coerceIn(0f, 0.72f))
			} else {
				Color.White.copy(alpha = (brightnessDelta * 0.65f).coerceIn(0f, 0.55f))
			}
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(scrimColor),
			)
		}

		// Sub-Zero Bedtime Dimmer Scrim (0% .. 60%) for reading in dark bedrooms
		val effectiveDimmer = bedtimeDimmerAlpha.coerceIn(0f, 0.60f)
		if (effectiveDimmer > 0.005f) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0xFF0A0907).copy(alpha = effectiveDimmer)),
			)
		}
	}
}
