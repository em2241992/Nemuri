package org.nemuri.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser

@Composable
fun NemuriBrandLogo(
	modifier: Modifier = Modifier,
) {
	val moonPath = remember {
		PathParser().parsePathString(
			"M 188 98 A 56 56 0 1 0 242 168 A 46 46 0 1 1 188 98 Z",
		).toPath()
	}
	val sleepingCatPath = remember {
		PathParser().parsePathString(
			"M 168 292 C 168 228, 224 196, 286 204 L 304 168 L 328 202 L 354 172 L 366 218 C 386 242, 382 278, 358 292 Z",
		).toPath()
	}
	val leftEyePath = remember {
		PathParser().parsePathString("M 292 244 Q 306 256 320 244").toPath()
	}
	val rightEyePath = remember {
		PathParser().parsePathString("M 332 244 Q 346 256 360 244").toPath()
	}
	val openBookBasePath = remember {
		PathParser().parsePathString(
			"M 96 312 Q 176 284 256 318 Q 336 284 416 312 L 402 384 Q 330 358 256 392 Q 182 358 110 384 Z",
		).toPath()
	}

	Canvas(modifier = modifier) {
		val scaleX = size.width / 512f
		val scaleY = size.height / 512f
		val strokeScale = minOf(scaleX, scaleY)

		val bgGradient = Brush.linearGradient(
			0f to Color(0xFF0B0F17),
			1f to Color(0xFF141829),
			start = Offset.Zero,
			end = Offset(size.width, size.height),
		)
		val cyanGradient = Brush.linearGradient(
			0f to Color(0xFF5CE1E6),
			1f to Color(0xFF7F7FD5),
			start = Offset.Zero,
			end = Offset(size.width, size.height),
		)

		drawRoundRect(
			brush = bgGradient,
			topLeft = Offset.Zero,
			size = Size(size.width, size.height),
			cornerRadius = CornerRadius(116f * scaleX, 116f * scaleY),
		)

		fun scaledPath(src: Path): Path = Path().apply {
			addPath(src)
			transform(Matrix().apply { scale(scaleX, scaleY, 1f) })
		}

		drawPath(
			path = scaledPath(moonPath),
			color = Color(0xFF5CE1E6),
		)

		val catScaled = scaledPath(sleepingCatPath)
		drawPath(
			path = catScaled,
			color = Color(0xFF1E2638),
		)
		drawPath(
			path = catScaled,
			brush = cyanGradient,
			style = Stroke(width = 8f * strokeScale, join = StrokeJoin.Round, cap = StrokeCap.Round),
		)

		drawPath(
			path = scaledPath(leftEyePath),
			color = Color(0xFF5CE1E6),
			style = Stroke(width = 5f * strokeScale, cap = StrokeCap.Round),
		)
		drawPath(
			path = scaledPath(rightEyePath),
			color = Color(0xFF5CE1E6),
			style = Stroke(width = 5f * strokeScale, cap = StrokeCap.Round),
		)

		drawPath(
			path = scaledPath(openBookBasePath),
			brush = cyanGradient,
		)

		drawLine(
			color = Color(0xFF0B0F17),
			start = Offset(256f * scaleX, 318f * scaleY),
			end = Offset(256f * scaleX, 392f * scaleY),
			strokeWidth = 8f * strokeScale,
			cap = StrokeCap.Round,
		)
	}
}
