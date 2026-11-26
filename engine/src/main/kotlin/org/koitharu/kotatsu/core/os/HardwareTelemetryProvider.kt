package org.koitharu.kotatsu.core.os

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class HardwareTelemetrySnapshot(
	val deviceModel: String,
	val socModel: String,
	val cpuThreads: Int,
	val gpuLabel: String,
	val hasNpu: Boolean,
	val is64Bit: Boolean,
) {
	val summaryLine: String
		get() = buildString {
			append(deviceModel)
			if (socModel.isNotBlank()) {
				append(" • ")
				append(socModel)
			}
			append(" • ")
			append(cpuThreads)
			append("T CPU • ")
			append(gpuLabel)
			if (hasNpu) {
				append(" • NPU")
			}
		}
}

@Singleton
class HardwareTelemetryProvider @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val _snapshot = MutableStateFlow(buildInitialSnapshot(gpuLabel = "GPU"))
	val snapshot: StateFlow<HardwareTelemetrySnapshot> = _snapshot.asStateFlow()

	init {
		scope.launch {
			val probedGpu = runCatching { queryOpenGlRenderer() }.getOrNull()
				?.takeIf { it.isNotBlank() }
				?: "GPU"
			_snapshot.value = buildInitialSnapshot(gpuLabel = probedGpu)
		}
	}

	private fun buildInitialSnapshot(gpuLabel: String): HardwareTelemetrySnapshot {
		val manufacturer = Build.MANUFACTURER.orEmpty().trim()
		val rawModel = Build.MODEL.orEmpty().trim().ifBlank { "Android Device" }
		val deviceModel = if (manufacturer.isNotBlank() &&
			!rawModel.startsWith(manufacturer, ignoreCase = true) &&
			manufacturer.length <= 12
		) {
			"${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }} $rawModel"
		} else {
			rawModel
		}

		val socRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Build.SOC_MODEL.orEmpty().trim()
		} else {
			Build.HARDWARE.orEmpty().trim()
		}
		val socModel = if (socRaw.isBlank() || socRaw.equals("unknown", ignoreCase = true)) {
			Build.BOARD.orEmpty().trim().takeUnless { it.equals("unknown", ignoreCase = true) }.orEmpty()
		} else {
			socRaw
		}

		val cpuThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
		val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && runCatching { Process.is64Bit() }.getOrDefault(true)
		val aiCoreInstalled = runCatching {
			context.packageManager.getPackageInfo("com.google.android.aicore", 0) != null
		}.getOrDefault(false)
		val socUpper = socModel.uppercase(Locale.ROOT)
		val hasNpu = is64Bit && (
			aiCoreInstalled ||
				socUpper.startsWith("SM8") ||
				socUpper.startsWith("SM7") ||
				socUpper.contains("TENSOR") ||
				socUpper.startsWith("MT68") ||
				socUpper.startsWith("MT69") ||
				socUpper.startsWith("S5E") ||
				socUpper.contains("QCS")
			)

		return HardwareTelemetrySnapshot(
			deviceModel = deviceModel,
			socModel = socModel,
			cpuThreads = cpuThreads,
			gpuLabel = gpuLabel,
			hasNpu = hasNpu,
			is64Bit = is64Bit,
		)
	}

	private fun queryOpenGlRenderer(): String {
		val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
		if (display == EGL14.EGL_NO_DISPLAY) return "GPU"
		val version = IntArray(2)
		if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "GPU"

		var surface = EGL14.EGL_NO_SURFACE
		var eglContext = EGL14.EGL_NO_CONTEXT
		return try {
			val configAttribs = intArrayOf(
				EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
				EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
				EGL14.EGL_RED_SIZE, 8,
				EGL14.EGL_GREEN_SIZE, 8,
				EGL14.EGL_BLUE_SIZE, 8,
				EGL14.EGL_NONE,
			)
			val configs = arrayOfNulls<EGLConfig>(1)
			val numConfigs = IntArray(1)
			if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
				return "GPU"
			}
			val config = configs[0] ?: return "GPU"
			val contextAttribs = intArrayOf(
				EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
				EGL14.EGL_NONE,
			)
			eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
			if (eglContext == EGL14.EGL_NO_CONTEXT) return "GPU"

			val surfaceAttribs = intArrayOf(
				EGL14.EGL_WIDTH, 1,
				EGL14.EGL_HEIGHT, 1,
				EGL14.EGL_NONE,
			)
			surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
			if (surface == EGL14.EGL_NO_SURFACE) return "GPU"

			if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return "GPU"
			val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty().trim()
			formatGpuRenderer(renderer)
		} finally {
			runCatching {
				EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
				if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
				if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
				EGL14.eglTerminate(display)
			}
		}
	}

	private fun formatGpuRenderer(raw: String): String {
		if (raw.isBlank()) return "GPU"
		val cleaned = raw
			.replace("(TM)", "", ignoreCase = true)
			.replace("(R)", "", ignoreCase = true)
			.replace("Qualcomm", "", ignoreCase = true)
			.replace("ARM", "", ignoreCase = true)
			.replace(Regex("""\s+"""), " ")
			.trim()
		return if (cleaned.contains("GPU", ignoreCase = true)) cleaned else "$cleaned GPU"
	}
}
