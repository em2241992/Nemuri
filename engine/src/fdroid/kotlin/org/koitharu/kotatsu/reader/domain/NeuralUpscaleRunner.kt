package org.koitharu.kotatsu.reader.domain

import android.graphics.Bitmap
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor stub keeping FOSS builds free of prebuilt ONNX Runtime native libraries.
 */
@Singleton
class NeuralUpscaleRunner @Inject constructor() {

	val isSupported: Boolean = false

	suspend fun upscaleBitmap2x(
		source: Bitmap,
		modelFile: File,
		accelerator: NeuralAccelerator,
	): Bitmap = throw UnsupportedOperationException("Neural upscaler requires ONNX Runtime (GitHub build)")

	suspend fun closeSession() = Unit
}
