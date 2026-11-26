package org.koitharu.kotatsu.ai.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

	fun hasReadyModel(): Boolean = isRuntimeSupported && modelManager.resolveActiveReadyModel() != null

	fun activeModelLabel(): String {
		val (entry, _) = modelManager.resolveActiveReadyModel()
			?: return "Local LLM (Not downloaded)"
		val accel = if (loadedEngine != null) {
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
			idleUnloadJob?.cancel()
			try {
				val engine = getOrLoadEngineLocked(entry, modelFile)
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
					val msg = conversation.sendMessage(userPrompt)
					val raw = msg.contents.contents
						.filterIsInstance<Content.Text>()
						.joinToString("") { it.text }
						.trim()
					THINK_BLOCK_REGEX.replace(raw, "").trim()
				}
				if (replyText.isEmpty()) {
					Result.failure(IllegalStateException("Local model returned an empty response"))
				} else {
					Result.success(replyText to "Local ${entry.title} ($loadedBackendLabel)")
				}
			} catch (t: Throwable) {
				closeLoadedEngineLocked()
				Result.failure(t)
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

	private fun getOrLoadEngineLocked(
		entry: LocalLlmCatalogEntry,
		modelFile: File,
	): Engine {
		val requestedAccel = LocalLlmAccelerator.fromKey(settings.aiLocalAccelerator)
		val existing = loadedEngine
		if (existing != null &&
			loadedModelPath == modelFile.absolutePath &&
			loadedRequestedAccel == requestedAccel
		) {
			return existing
		}
		closeLoadedEngineLocked()

		checkAvailableRamOrThrow(entry.minRamMb)

		val cacheDir = File(context.cacheDir, "litertlm_cache").apply { mkdirs() }
		val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

		val (engine, actualBackend) = if (requestedAccel == LocalLlmAccelerator.GPU) {
			val gpuAttempt = runCatching {
				val cfg = EngineConfig(
					modelFile.absolutePath,
					Backend.GPU(),
					null,
					null,
					MAX_CONTEXT_TOKENS,
					null,
					cacheDir.absolutePath,
				)
				Engine(cfg).apply { initialize() }
			}
			if (gpuAttempt.isSuccess) {
				gpuAttempt.getOrThrow() to "GPU"
			} else {
				val cpuCfg = EngineConfig(
					modelFile.absolutePath,
					Backend.CPU(threads, null),
					null,
					null,
					MAX_CONTEXT_TOKENS,
					null,
					cacheDir.absolutePath,
				)
				Engine(cpuCfg).apply { initialize() } to "CPU"
			}
		} else {
			val cpuCfg = EngineConfig(
				modelFile.absolutePath,
				Backend.CPU(threads, null),
				null,
				null,
				MAX_CONTEXT_TOKENS,
				null,
				cacheDir.absolutePath,
			)
			Engine(cpuCfg).apply { initialize() } to "CPU"
		}

		loadedEngine = engine
		loadedModelPath = modelFile.absolutePath
		loadedBackendLabel = actualBackend
		loadedRequestedAccel = requestedAccel
		_isModelLoadedInRam.value = true
		return engine
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
