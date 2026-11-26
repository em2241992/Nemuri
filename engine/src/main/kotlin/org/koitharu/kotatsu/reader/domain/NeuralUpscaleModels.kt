package org.koitharu.kotatsu.reader.domain

enum class NeuralAccelerator(val key: String, val label: String, val subtitle: String) {
	AUTO("AUTO", "Auto (NPU → GPU → CPU)", "Tries NPU (QNN/LiteRT/NNAPI), then GPU (FP16), then XNNPACK CPU"),
	NPU("NPU", "NPU", "Qualcomm QNN HTP / LiteRT NPU / NNAPI with safe GPU & CPU fallback"),
	GPU("GPU", "GPU", "LiteRT OpenCL/GL FP16 / NNAPI GPU delegate with CPU fallback"),
	CPU("CPU", "CPU", "Multi-core ARM NEON / XNNPACK execution"),
	;

	companion object {
		fun fromKey(key: String?): NeuralAccelerator = when (key?.uppercase()) {
			"NPU", "NNAPI" -> NPU
			"GPU" -> GPU
			"CPU", "XNNPACK" -> CPU
			else -> AUTO
		}
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
	val isRecommended: Boolean = false,
	val formatBadge: String = "ONNX",
)

data class NeuralModelDownloadState(
	val activeModelId: String? = null,
	val downloadedBytes: Long = 0L,
	val totalBytes: Long = 0L,
	val error: String? = null,
)

