package org.koitharu.kotatsu.reader.domain

enum class NeuralAccelerator(val key: String, val label: String, val subtitle: String) {
	AUTO("AUTO", "Auto (NPU / GPU)", "Uses Android NNAPI hardware acceleration with XNNPACK CPU fallback"),
	NNAPI("NNAPI", "NPU / GPU (NNAPI)", "Prioritizes SoC NPU / DSP / GPU delegate"),
	XNNPACK("XNNPACK", "CPU (XNNPACK)", "Multi-core ARM NEON / SIMD CPU execution"),
	;

	companion object {
		fun fromKey(key: String?): NeuralAccelerator =
			entries.firstOrNull { it.key == key } ?: AUTO
	}
}

data class NeuralUpscaleModelEntry(
	val id: String,
	val title: String,
	val subtitle: String,
	val fileName: String,
	val downloadUrl: String?,
	val approxSizeBytes: Long,
	val nativeScale: Int,
	val targetOutputScale: Int = 2,
)

data class NeuralModelDownloadState(
	val activeModelId: String? = null,
	val downloadedBytes: Long = 0L,
	val totalBytes: Long = 0L,
	val error: String? = null,
)
