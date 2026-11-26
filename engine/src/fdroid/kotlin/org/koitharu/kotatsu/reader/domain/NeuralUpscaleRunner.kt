package org.koitharu.kotatsu.reader.domain

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor stub keeping FOSS builds free of prebuilt ONNX Runtime, LiteRT, and QNN native libraries.
 */
@Singleton
class NeuralUpscaleRunner @Inject constructor() {

	val isSupported: Boolean = false

	private val _activeDelegateLabel = MutableStateFlow("Unavailable (F-Droid FOSS build)")
	val activeDelegateLabel: StateFlow<String> = _activeDelegateLabel.asStateFlow()

	suspend fun upscaleBitmap2x(
		source: Bitmap,
		modelFile: File,
		accelerator: NeuralAccelerator,
	): Bitmap = throw UnsupportedOperationException("Neural upscaler requires native runtime (GitHub build)")

	suspend fun closeSession() = Unit
}
