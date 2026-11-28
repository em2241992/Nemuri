package org.koitharu.kotatsu.ai.domain

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.ai.data.LocalModelManager
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device local LLM inference powered by LiteRT-LM (`.litertlm`), featuring lazy native loading,
 * GPU -> CPU automatic fallback, low-RAM OOM protection, and 60-second idle RAM auto-unload.
 */
@Singleton
class LocalLlmClient @Inject constructor(
	@ApplicationContext private val context: Context,
	private val modelManager: LocalModelManager,
	private val settings: AppSettings,
) {

	val isRuntimeSupported: Boolean
		get() = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && Process.is64Bit()

	private val _isModelLoadedInRam = MutableStateFlow(false)
	val isModelLoadedInRam: StateFlow<Boolean> = _isModelLoadedInRam.asStateFlow()

	private val engineMutex = Mutex()
	private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var idleUnloadJob: Job? = null

	private var loadedEngine: Engine? = null
	private var loadedModelPath: String? = null
	private var loadedBackendLabel: String = "CPU"
	private var loadedRequestedAccel: LocalLlmAccelerator? = null

	fun hasReadyModel(): Boolean {
		if (!isRuntimeSupported) return false
		val loadedPath = loadedModelPath
		if (loadedPath != null && !File(loadedPath).isFile) {
			idleScope.launch { unloadNow() }
		}
		val resolved = modelManager.resolveActiveReadyModel()
		return resolved != null && resolved.second.isFile
	}

	fun activeModelLabel(): String {
		val (entry, file) = modelManager.resolveActiveReadyModel()
			?.takeIf { it.second.isFile }
			?: return "Local LLM (Not downloaded)"
		val accel = if (loadedEngine != null && loadedModelPath == file.absolutePath) {
			loadedBackendLabel
		} else {
			LocalLlmAccelerator.fromKey(settings.aiLocalAccelerator).name
		}
		return "Local ${entry.title} ($accel)"
	}

	suspend fun generate(
		systemPrompt: String,
		userPrompt: String,
		maxTokens: Int = 320,
		preferGpuOverride: Boolean = false,
	): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
		if (!isRuntimeSupported) {
			return@withContext Result.failure(
				UnsupportedOperationException("On-device LiteRT-LM requires a 64-bit ARM/x86_64 CPU"),
			)
		}
		val resolved = modelManager.resolveActiveReadyModel()
			?: return@withContext Result.failure(
				IllegalStateException("No local .litertlm model downloaded or imported yet"),
			)
		val (entry, modelFile) = resolved

		engineMutex.withLock {
			currentCoroutineContext().ensureActive()
			idleUnloadJob?.cancel()
			if (!modelFile.isFile) {
				closeLoadedEngineLocked()
				modelManager.resolveActiveReadyModel()
				return@withContext Result.failure(
					IllegalStateException("Selected local .litertlm file is missing — switched back to Auto (Gemini Nano)"),
				)
			}
			try {
				val engine = getOrLoadEngineLocked(entry, modelFile, preferGpuOverride)
				currentCoroutineContext().ensureActive()
				val convConfig = ConversationConfig(
					Contents.of(systemPrompt),
					emptyList(),
					emptyList(),
					SamplerConfig(40, 0.9, 0.3, 42),
					false,
					emptyList(),
					emptyMap(),
					null,
					false,
					maxTokens,
					ThinkingConfig(false),
				)
				val replyText = engine.createConversation(convConfig).use { conversation ->
					currentCoroutineContext().ensureActive()
					val msg = conversation.sendMessage(userPrompt)
					val raw = msg.contents.contents
						.filterIsInstance<Content.Text>()
						.joinToString("") { it.text }
						.trim()
					THINK_BLOCK_REGEX.replace(raw, "").trim()
				}
				currentCoroutineContext().ensureActive()
				if (replyText.isEmpty()) {
					Result.failure(IllegalStateException("Local model returned an empty response"))
				} else {
					Result.success(replyText to "Local ${entry.title} ($loadedBackendLabel)")
				}
			} catch (ce: CancellationException) {
				throw ce
			} catch (t: Throwable) {
				closeLoadedEngineLocked()
				Result.failure(mapFriendlyLiteRtError(t))
			} finally {
				if (loadedEngine != null) {
					scheduleIdleUnloadLocked()
				}
			}
		}
	}

	suspend fun unloadNow() {
		withContext(Dispatchers.IO) {
			engineMutex.withLock {
				idleUnloadJob?.cancel()
				closeLoadedEngineLocked()
			}
		}
	}

	fun unloadAsync() {
		if (!_isModelLoadedInRam.value && !engineMutex.isLocked) return
		idleScope.launch {
			unloadNow()
		}
	}

	@Suppress("DEPRECATION")
	fun onTrimMemory(level: Int) {
		if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
			unloadAsync()
		}
	}

	private fun getOrLoadEngineLocked(
		entry: LocalLlmCatalogEntry,
		modelFile: File,
		preferGpuOverride: Boolean = false,
	): Engine {
		if (!modelFile.isFile) {
			throw IllegalStateException("Local .litertlm file not found on disk")
		}
		val configuredAccel = LocalLlmAccelerator.fromKey(settings.aiLocalAccelerator)
		val effectiveAccel = if (preferGpuOverride && entry.requiredSocModel.isNullOrBlank()) {
			LocalLlmAccelerator.GPU
		} else {
			configuredAccel
		}
		val existing = loadedEngine
		if (existing != null &&
			loadedModelPath == modelFile.absolutePath &&
			loadedRequestedAccel == effectiveAccel
		) {
			return existing
		}
		closeLoadedEngineLocked()

		checkAvailableRamOrThrow(entry.minRamMb)

		val cacheDir = File(context.cacheDir, "litertlm_cache").apply { mkdirs() }
		val nativeLibDir = context.applicationInfo.nativeLibraryDir.orEmpty()
		if (nativeLibDir.isNotBlank()) {
			runCatching {
				Os.setenv(
					"ADSP_LIBRARY_PATH",
					"$nativeLibDir;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp",
					true,
				)
			}
		}
		val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
		val preferNpu = !preferGpuOverride &&
			(effectiveAccel == LocalLlmAccelerator.NPU || !entry.requiredSocModel.isNullOrBlank())
		var firstBackendFailure: Throwable? = null

		fun buildCpuEngine(): Pair<Engine, String> {
			val cpuCfg = EngineConfig(
				modelFile.absolutePath,
				Backend.CPU(threads, null),
				null,
				null,
				MAX_CONTEXT_TOKENS,
				null,
				cacheDir.absolutePath,
			)
			return try {
				Engine(cpuCfg).apply { initialize() } to "CPU"
			} catch (t: Throwable) {
				throw mapFriendlyLiteRtError(firstBackendFailure ?: t)
			}
		}

		fun tryGpuEngine(): Pair<Engine, String>? = runCatching {
			val cfg = EngineConfig(
				modelFile.absolutePath,
				Backend.GPU(),
				null,
				null,
				MAX_CONTEXT_TOKENS,
				null,
				cacheDir.absolutePath,
			)
			Engine(cfg).apply { initialize() } to "GPU"
		}.onFailure { err ->
			if (firstBackendFailure == null) firstBackendFailure = err
		}.getOrNull()

		fun tryNpuEngine(): Pair<Engine, String>? {
			if (nativeLibDir.isBlank()) return null
			return runCatching {
				val cfg = EngineConfig(
					modelFile.absolutePath,
					Backend.NPU(nativeLibraryDir = nativeLibDir),
					null,
					null,
					MAX_CONTEXT_TOKENS,
					null,
					cacheDir.absolutePath,
				)
				Engine(cfg).apply { initialize() } to "Hexagon NPU"
			}.onFailure { err ->
				if (firstBackendFailure == null) firstBackendFailure = err
			}.getOrNull()
		}

		val (engine, actualBackend) = when {
			preferNpu -> tryNpuEngine() ?: tryGpuEngine() ?: buildCpuEngine()
			effectiveAccel == LocalLlmAccelerator.GPU -> tryGpuEngine() ?: buildCpuEngine()
			else -> buildCpuEngine()
		}

		loadedEngine = engine
		loadedModelPath = modelFile.absolutePath
		loadedBackendLabel = actualBackend
		loadedRequestedAccel = effectiveAccel
		_isModelLoadedInRam.value = true
		return engine
	}

	private fun mapFriendlyLiteRtError(t: Throwable): Throwable {
		var cur: Throwable? = t
		var depth = 0
		while (cur != null && depth < 6) {
			val msg = cur.message.orEmpty()
			if (msg.contains("Input tensor not found", ignoreCase = true) ||
				msg.contains("NOT_FOUND", ignoreCase = true)
			) {
				return IllegalStateException(
					"Incompatible .litertlm tensor format — use Gemma 4 E2B .litertlm on GPU or Auto (Gemini Nano)",
					t,
				)
			}
			cur = cur.cause
			depth++
		}
		return t
	}

	private fun checkAvailableRamOrThrow(minRequiredMb: Int) {
		val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
		if (am.isLowRamDevice) {
			error("Device reports low-RAM configuration; skipping local LLM load")
		}
		val memInfo = ActivityManager.MemoryInfo()
		am.getMemoryInfo(memInfo)
		val availMb = memInfo.availMem / (1024L * 1024L)
		if (memInfo.lowMemory || availMb < minRequiredMb.toLong()) {
			error("Insufficient free RAM (${availMb} MB free, need ~${minRequiredMb} MB) to load local model safely")
		}
	}

	private fun scheduleIdleUnloadLocked() {
		idleUnloadJob?.cancel()
		idleUnloadJob = idleScope.launch {
			delay(IDLE_UNLOAD_DELAY_MS)
			engineMutex.withLock {
				closeLoadedEngineLocked()
			}
		}
	}

	private fun closeLoadedEngineLocked() {
		runCatching { loadedEngine?.close() }
		loadedEngine = null
		loadedModelPath = null
		loadedRequestedAccel = null
		_isModelLoadedInRam.value = false
	}

	companion object {
		private const val IDLE_UNLOAD_DELAY_MS = 60_000L
		private const val MAX_CONTEXT_TOKENS = 2048
		private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
	}
}
