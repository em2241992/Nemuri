package org.koitharu.kotatsu.reader.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.providers.NNAPIFlags
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Collections
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Hardware-accelerated super-resolution runner (GitHub flavor) supporting:
 * - `.onnx` models via ONNX Runtime (NNAPI FP16 NPU/GPU -> XNNPACK CPU)
 * - `.tflite` models via LiteRT (`Accelerator.NPU` / `GPU` FP16 / `CPU` XNNPACK, with 64-bit QNN/NPU guards)
 * - Reusable direct `ByteBuffer` & typed array scratch pools (zero per-tile heap churn)
 * - RAM-adaptive fixed tile sizes (`512x512` on >= 8 GB RAM, `256x256` on < 8 GB RAM) with feathered blending
 * - Live active hardware delegate telemetry via [activeDelegateLabel]
 */
@Singleton
class NeuralUpscaleRunner @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	val isSupported: Boolean
		get() = true

	private val _activeDelegateLabel = MutableStateFlow("Idle (Ready)")
	val activeDelegateLabel: StateFlow<String> = _activeDelegateLabel.asStateFlow()

	private val sessionMutex = Mutex()
	private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var idleCloseJob: Job? = null

	// ONNX Runtime state
	private var cachedOrtEnv: OrtEnvironment? = null
	private var cachedOrtSession: OrtSession? = null
	private var cachedOrtUsingHw: Boolean = false

	// LiteRT state
	private var cachedLitertModel: CompiledModel? = null
	private var cachedLitertSpec: TfliteSpec? = null
	private var cachedLitertInputs: List<TensorBuffer>? = null
	private var cachedLitertOutputs: List<TensorBuffer>? = null

	// Shared session metadata
	private var cachedModelPath: String? = null
	private var cachedModelModified: Long = 0L
	private var cachedAccel: NeuralAccelerator? = null

	// Reusable scratch pool (guarded by sessionMutex)
	private var poolInputByteBuffer: ByteBuffer? = null
	private var poolInputFloatBuffer: FloatBuffer? = null
	private var poolInFloatArray: FloatArray? = null
	private var poolInByteArray: ByteArray? = null
	private var poolOutFloatArray: FloatArray? = null
	private var poolOutByteArray: ByteArray? = null

	private val deviceHasHighRam: Boolean by lazy {
		runCatching {
			val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching false
			if (am.isLowRamDevice) return@runCatching false
			val info = ActivityManager.MemoryInfo()
			am.getMemoryInfo(info)
			info.totalMem >= HIGH_RAM_THRESHOLD_BYTES
		}.getOrDefault(false)
	}

	private data class TfliteSpec(
		val isNchw: Boolean,
		val fixedH: Int,
		val fixedW: Int,
		val inDataType: DataType,
		val outDataType: DataType,
		val inScale: Float,
		val inZeroPoint: Int,
		val outScale: Float,
		val outZeroPoint: Int,
		val scaleFactor: Int,
		val hasDynamicShape: Boolean,
	)

	suspend fun upscaleBitmap2x(
		source: Bitmap,
		modelFile: File,
		accelerator: NeuralAccelerator,
	): Bitmap = withContext(Dispatchers.Default) {
		val argb = if (source.config == Bitmap.Config.ARGB_8888) {
			source
		} else {
			source.copy(Bitmap.Config.ARGB_8888, false)
		}
		val srcW = argb.width
		val srcH = argb.height
		val dstW = srcW * TARGET_SCALE
		val dstH = srcH * TARGET_SCALE
		val outPixels = IntArray(dstW * dstH)

		val srcPixels = IntArray(srcW * srcH)
		argb.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
		if (argb !== source) {
			argb.recycle()
		}

		val isTflite = modelFile.name.endsWith(".tflite", ignoreCase = true)

		sessionMutex.withLock {
			idleCloseJob?.cancel()
			try {
				if (isTflite) {
					runTflitePaged2x(
						srcPixels = srcPixels,
						srcW = srcW,
						srcH = srcH,
						dstW = dstW,
						dstH = dstH,
						outPixels = outPixels,
						modelFile = modelFile,
						accelerator = accelerator,
					)
				} else {
					runOnnxPaged2x(
						srcPixels = srcPixels,
						srcW = srcW,
						srcH = srcH,
						dstW = dstW,
						dstPixels = outPixels,
						modelFile = modelFile,
						accelerator = accelerator,
					)
				}
			} finally {
				scheduleIdleCloseLocked()
			}
		}

		Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888).apply {
			setPixels(outPixels, 0, dstW, 0, 0, dstW, dstH)
		}
	}

	suspend fun closeSession() {
		withContext(Dispatchers.IO) {
			sessionMutex.withLock {
				idleCloseJob?.cancel()
				closeSessionLocked()
			}
		}
	}

	// --- ONNX Runtime Pipeline ---

	private suspend fun runOnnxPaged2x(
		srcPixels: IntArray,
		srcW: Int,
		srcH: Int,
		dstW: Int,
		dstPixels: IntArray,
		modelFile: File,
		accelerator: NeuralAccelerator,
	) {
		val env = cachedOrtEnv ?: OrtEnvironment.getEnvironment().also { cachedOrtEnv = it }
		var session = getOrCreateOrtSessionLocked(env, modelFile, accelerator, forceCpu = false)
		var inputName = session.inputNames.firstOrNull() ?: "input"

		val desiredTile = preferredTileSize()
		val pad = DEFAULT_PAD
		val coreTile = (desiredTile - pad * 2).coerceAtLeast(128)
		val planeSize = desiredTile * desiredTile
		val floatBuf = obtainDirectFloatBuffer(planeSize * 3)
		val inArr = obtainInFloatArray(planeSize * 3)
		val shape = longArrayOf(1L, 3L, desiredTile.toLong(), desiredTile.toLong())

		val tilesX = (srcW + coreTile - 1) / coreTile
		val tilesY = (srcH + coreTile - 1) / coreTile

		for (ty in 0 until tilesY) {
			val y0 = ty * coreTile
			val ch = (srcH - y0).coerceAtMost(coreTile)
			for (tx in 0 until tilesX) {
				coroutineContext.ensureActive()
				val x0 = tx * coreTile
				val cw = (srcW - x0).coerceAtMost(coreTile)

				// Populate fixed desiredTile x desiredTile NCHW planar RGB buffer (zero edge recompilation)
				var idx = 0
				val planeG = planeSize
				val planeB = planeSize * 2
				for (py in 0 until desiredTile) {
					val sy = reflectCoord(y0 - pad + py, srcH)
					val rowOffset = sy * srcW
					for (px in 0 until desiredTile) {
						val sx = reflectCoord(x0 - pad + px, srcW)
						val pixel = srcPixels[rowOffset + sx]
						inArr[idx] = ((pixel ushr 16) and 0xFF) * INV_255
						inArr[planeG + idx] = ((pixel ushr 8) and 0xFF) * INV_255
						inArr[planeB + idx] = (pixel and 0xFF) * INV_255
						idx++
					}
				}
				floatBuf.clear()
				floatBuf.put(inArr, 0, planeSize * 3)
				floatBuf.rewind()

				var tileDone = false
				var retriedCpu = false
				while (!tileDone) {
					val inputTensor = OnnxTensor.createTensor(env, floatBuf, shape)
					try {
						session.run(Collections.singletonMap(inputName, inputTensor)).use { results ->
							val outTensor = results.iterator().next().value as? OnnxTensor
								?: error("Model output is not an OnnxTensor")
							val outShape = (outTensor.info as? TensorInfo)?.shape
								?: longArrayOf(1L, 3L, (desiredTile * TARGET_SCALE).toLong(), (desiredTile * TARGET_SCALE).toLong())
							val outH = outShape.getOrNull(2)?.toInt() ?: (desiredTile * TARGET_SCALE)
							val outW = outShape.getOrNull(3)?.toInt() ?: (desiredTile * TARGET_SCALE)
							val modelScale = detectScale(desiredTile, outH)
							val cropX = ((desiredTile * modelScale - outW) / 2).coerceAtLeast(0)
							val cropY = ((desiredTile * modelScale - outH) / 2).coerceAtLeast(0)
							val coreStartX = (pad * modelScale - cropX).coerceAtLeast(0)
							val coreStartY = (pad * modelScale - cropY).coerceAtLeast(0)

							val outPlane = outW * outH
							val outFloat = obtainOutFloatArray(outPlane * 3)
							outTensor.floatBuffer.apply {
								rewind()
								get(outFloat, 0, outPlane * 3)
							}

							writeNchwTileIntoDestination(
								outFloat = outFloat,
								outW = outW,
								outH = outH,
								outPlane = outPlane,
								modelScale = modelScale,
								coreStartX = coreStartX,
								coreStartY = coreStartY,
								cw = cw,
								ch = ch,
								dstX0 = x0 * TARGET_SCALE,
								dstY0 = y0 * TARGET_SCALE,
								dstW = dstW,
								dstPixels = dstPixels,
							)
						}
						tileDone = true
					} catch (t: Throwable) {
						if (!retriedCpu && cachedOrtUsingHw) {
							retriedCpu = true
							closeSessionLocked()
							session = getOrCreateOrtSessionLocked(env, modelFile, accelerator, forceCpu = true)
							inputName = session.inputNames.firstOrNull() ?: "input"
							_activeDelegateLabel.value = "Active: CPU (XNNPACK • Fallback)"
							floatBuf.rewind()
						} else {
							throw t
						}
					} finally {
						runCatching { inputTensor.close() }
					}
				}
			}
		}
	}

	private fun getOrCreateOrtSessionLocked(
		env: OrtEnvironment,
		modelFile: File,
		accelerator: NeuralAccelerator,
		forceCpu: Boolean,
	): OrtSession {
		val mod = modelFile.lastModified()
		val existing = cachedOrtSession
		if (existing != null &&
			cachedModelPath == modelFile.absolutePath &&
			cachedModelModified == mod &&
			cachedAccel == accelerator &&
			(!forceCpu || !cachedOrtUsingHw)
		) {
			return existing
		}
		closeSessionLocked()

		val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
		val allowHw = !forceCpu && accelerator != NeuralAccelerator.CPU
		var usedHw = false
		var delegateText = "Active: CPU (XNNPACK)"

		val session = if (allowHw) {
			val nnapiAttempt = runCatching {
				val opts = OrtSession.SessionOptions().apply {
					setIntraOpNumThreads(threads)
					setInterOpNumThreads(1)
					setMemoryPatternOptimization(true)
					// Request FP16 NNAPI acceleration; keep CPU partition fallback active unless CPU-only fails
					val flags = EnumSet.of(NNAPIFlags.USE_FP16)
					addNnapi(flags)
					addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
				}
				usedHw = true
				delegateText = when (accelerator) {
					NeuralAccelerator.NPU, NeuralAccelerator.AUTO -> "Active: NPU/GPU (NNAPI FP16)"
					NeuralAccelerator.GPU -> "Active: GPU (NNAPI FP16)"
					NeuralAccelerator.CPU -> "Active: CPU (XNNPACK)"
				}
				opts.use { env.createSession(modelFile.absolutePath, it) }
			}
			nnapiAttempt.getOrElse {
				usedHw = false
				delegateText = "Active: CPU (XNNPACK • Fallback)"
				createOrtCpuSession(env, modelFile, threads)
			}
		} else {
			createOrtCpuSession(env, modelFile, threads)
		}

		cachedOrtSession = session
		cachedOrtUsingHw = usedHw
		cachedModelPath = modelFile.absolutePath
		cachedModelModified = mod
		cachedAccel = accelerator
		_activeDelegateLabel.value = delegateText
		return session
	}

	private fun createOrtCpuSession(
		env: OrtEnvironment,
		modelFile: File,
		threads: Int,
	): OrtSession {
		val opts = OrtSession.SessionOptions().apply {
			setIntraOpNumThreads(threads)
			setInterOpNumThreads(1)
			setMemoryPatternOptimization(true)
			runCatching { addXnnpack(mapOf("intra_op_num_threads" to threads.toString())) }
		}
		return opts.use { env.createSession(modelFile.absolutePath, it) }
	}

	// --- LiteRT (.tflite) Pipeline (NPU -> GPU FP16 -> CPU XNNPACK) ---

	private suspend fun runTflitePaged2x(
		srcPixels: IntArray,
		srcW: Int,
		srcH: Int,
		dstW: Int,
		dstH: Int,
		outPixels: IntArray,
		modelFile: File,
		accelerator: NeuralAccelerator,
	) {
		val (compiled, spec, inBufs, outBufs) = getOrCreateLitertLocked(modelFile, accelerator)
		val inBuffer = inBufs.first()
		val outBuffer = outBufs.first()

		val tileH = spec.fixedH
		val tileW = spec.fixedW
		val pad = if (tileH <= 160) 16 else DEFAULT_PAD
		val coreW = (tileW - pad * 2).coerceAtLeast(32)
		val coreH = (tileH - pad * 2).coerceAtLeast(32)

		val inElements = tileH * tileW * 3
		val outH = tileH * spec.scaleFactor
		val outW = tileW * spec.scaleFactor
		val outElements = outH * outW * 3

		val isByteInput = spec.inDataType == DataType.UINT8 || spec.inDataType == DataType.INT8
		val isByteOutput = spec.outDataType == DataType.UINT8 || spec.outDataType == DataType.INT8
		val inFloatArr = if (!isByteInput) obtainInFloatArray(inElements) else null
		val inByteArr = if (isByteInput) obtainInByteArray(inElements) else null

		val tilesX = (srcW + coreW - 1) / coreW
		val tilesY = (srcH + coreH - 1) / coreH

		for (ty in 0 until tilesY) {
			val y0 = ty * coreH
			val ch = (srcH - y0).coerceAtMost(coreH)
			for (tx in 0 until tilesX) {
				coroutineContext.ensureActive()
				val x0 = tx * coreW
				val cw = (srcW - x0).coerceAtMost(coreW)

				if (spec.isNchw) {
					val planeSize = tileH * tileW
					var idx = 0
					for (py in 0 until tileH) {
						val sy = reflectCoord(y0 - pad + py, srcH)
						val rowOff = sy * srcW
						for (px in 0 until tileW) {
							val sx = reflectCoord(x0 - pad + px, srcW)
							val pixel = srcPixels[rowOff + sx]
							val r = (pixel ushr 16) and 0xFF
							val g = (pixel ushr 8) and 0xFF
							val b = pixel and 0xFF
							if (inFloatArr != null) {
								inFloatArr[idx] = r * INV_255
								inFloatArr[planeSize + idx] = g * INV_255
								inFloatArr[planeSize * 2 + idx] = b * INV_255
							} else if (inByteArr != null) {
								inByteArr[idx] = quantizeChannel(r, spec.inDataType, spec.inScale, spec.inZeroPoint)
								inByteArr[planeSize + idx] = quantizeChannel(g, spec.inDataType, spec.inScale, spec.inZeroPoint)
								inByteArr[planeSize * 2 + idx] = quantizeChannel(b, spec.inDataType, spec.inScale, spec.inZeroPoint)
							}
							idx++
						}
					}
				} else {
					var idx = 0
					for (py in 0 until tileH) {
						val sy = reflectCoord(y0 - pad + py, srcH)
						val rowOff = sy * srcW
						for (px in 0 until tileW) {
							val sx = reflectCoord(x0 - pad + px, srcW)
							val pixel = srcPixels[rowOff + sx]
							val r = (pixel ushr 16) and 0xFF
							val g = (pixel ushr 8) and 0xFF
							val b = pixel and 0xFF
							if (inFloatArr != null) {
								inFloatArr[idx++] = r * INV_255
								inFloatArr[idx++] = g * INV_255
								inFloatArr[idx++] = b * INV_255
							} else if (inByteArr != null) {
								inByteArr[idx++] = quantizeChannel(r, spec.inDataType, spec.inScale, spec.inZeroPoint)
								inByteArr[idx++] = quantizeChannel(g, spec.inDataType, spec.inScale, spec.inZeroPoint)
								inByteArr[idx++] = quantizeChannel(b, spec.inDataType, spec.inScale, spec.inZeroPoint)
							}
						}
					}
				}

				if (inFloatArr != null) {
					inBuffer.writeFloat(inFloatArr)
				} else if (inByteArr != null) {
					inBuffer.writeInt8(inByteArr)
				}

				compiled.run(inBufs, outBufs)

				val outFloat: FloatArray?
				val outBytes: ByteArray?
				if (isByteOutput) {
					outFloat = null
					outBytes = outBuffer.readInt8()
				} else {
					outBytes = null
					outFloat = outBuffer.readFloat()
				}

				if (spec.isNchw) {
					val converted = obtainOutFloatArray(outElements)
					if (outFloat != null) {
						val count = min(outFloat.size, converted.size)
						System.arraycopy(outFloat, 0, converted, 0, count)
					} else if (outBytes != null) {
						val count = min(outBytes.size, converted.size)
						for (k in 0 until count) {
							converted[k] = dequantizeChannel(outBytes[k], spec.outDataType, spec.outScale, spec.outZeroPoint)
						}
					}
					writeNchwTileIntoDestination(
						outFloat = converted,
						outW = outW,
						outH = outH,
						outPlane = outW * outH,
						modelScale = spec.scaleFactor,
						coreStartX = pad * spec.scaleFactor,
						coreStartY = pad * spec.scaleFactor,
						cw = cw,
						ch = ch,
						dstX0 = x0 * TARGET_SCALE,
						dstY0 = y0 * TARGET_SCALE,
						dstW = dstW,
						dstPixels = outPixels,
					)
				} else {
					writeNhwcTileIntoDestination(
						outFloat = outFloat,
						outBytes = outBytes,
						outDataType = spec.outDataType,
						outScale = spec.outScale,
						outZeroPoint = spec.outZeroPoint,
						outW = outW,
						outH = outH,
						modelScale = spec.scaleFactor,
						coreStartX = pad * spec.scaleFactor,
						coreStartY = pad * spec.scaleFactor,
						cw = cw,
						ch = ch,
						dstX0 = x0 * TARGET_SCALE,
						dstY0 = y0 * TARGET_SCALE,
						dstW = dstW,
						dstH = dstH,
						dstPixels = outPixels,
					)
				}
			}
		}
	}

	private data class LitertBundle(
		val model: CompiledModel,
		val spec: TfliteSpec,
		val inputs: List<TensorBuffer>,
		val outputs: List<TensorBuffer>,
	)

	private fun getOrCreateLitertLocked(
		modelFile: File,
		accelerator: NeuralAccelerator,
	): LitertBundle {
		val mod = modelFile.lastModified()
		val existingModel = cachedLitertModel
		val existingSpec = cachedLitertSpec
		val existingIn = cachedLitertInputs
		val existingOut = cachedLitertOutputs
		if (existingModel != null &&
			existingSpec != null &&
			existingIn != null &&
			existingOut != null &&
			cachedModelPath == modelFile.absolutePath &&
			cachedModelModified == mod &&
			cachedAccel == accelerator
		) {
			return LitertBundle(existingModel, existingSpec, existingIn, existingOut)
		}
		closeSessionLocked()

		val spec = inspectTfliteSpec(modelFile)
		val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && Process.is64Bit()

		val candidates = when (accelerator) {
			NeuralAccelerator.AUTO -> listOfNotNull(
				Accelerator.NPU.takeIf { is64Bit },
				Accelerator.GPU,
				Accelerator.CPU,
			)
			NeuralAccelerator.NPU -> listOfNotNull(
				Accelerator.NPU.takeIf { is64Bit },
				Accelerator.GPU,
				Accelerator.CPU,
			)
			NeuralAccelerator.GPU -> listOf(Accelerator.GPU, Accelerator.CPU)
			NeuralAccelerator.CPU -> listOf(Accelerator.CPU)
		}

		var compiled: CompiledModel? = null
		var activeLabel = "Active: CPU (XNNPACK)"
		var lastError: Throwable? = null

		for ((idx, candidate) in candidates.withIndex()) {
			val attempt = runCatching {
				val opts = when (candidate) {
					Accelerator.NPU -> CompiledModel.Options(Accelerator.NPU).apply {
						qualcommOptions = CompiledModel.QualcommOptions(
							htpPerformanceMode = CompiledModel.QualcommOptions.HtpPerformanceMode.BURST,
							useConvHmx = true,
							useFoldRelu = true,
						)
					}
					Accelerator.GPU -> CompiledModel.Options(Accelerator.GPU).apply {
						gpuOptions = CompiledModel.GpuOptions(
							precision = CompiledModel.GpuOptions.Precision.FP16,
						)
					}
					else -> CompiledModel.Options(Accelerator.CPU)
				}
				val model = CompiledModel.create(modelFile.absolutePath, opts)
				if (spec.hasDynamicShape) {
					val targetShape = if (spec.isNchw) {
						intArrayOf(1, 3, spec.fixedH, spec.fixedW)
					} else {
						intArrayOf(1, spec.fixedH, spec.fixedW, 3)
					}
					runCatching { model.resizeInputTensor(0.toString(), targetShape) }
				}
				val inBufs = model.createInputBuffers()
				val outBufs = model.createOutputBuffers()
				Triple(model, inBufs, outBufs)
			}
			if (attempt.isSuccess) {
				val (m, inB, outB) = attempt.getOrThrow()
				compiled = m
				cachedLitertInputs = inB
				cachedLitertOutputs = outB
				val fallbackTag = if (idx > 0 && accelerator != NeuralAccelerator.AUTO) " • Fallback" else ""
				activeLabel = when (candidate) {
					Accelerator.NPU -> {
						val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL.isNotBlank()) {
							" ${Build.SOC_MODEL}"
						} else {
							""
						}
						"Active: NPU (QNN/LiteRT$soc)$fallbackTag"
					}
					Accelerator.GPU -> "Active: GPU (LiteRT FP16)$fallbackTag"
					else -> "Active: CPU (LiteRT XNNPACK)$fallbackTag"
				}
				break
			} else {
				lastError = attempt.exceptionOrNull()
			}
		}

		val readyModel = compiled ?: throw (lastError ?: IllegalStateException("Unable to initialize LiteRT model"))
		val readyIn = cachedLitertInputs ?: error("LiteRT input buffers unavailable")
		val readyOut = cachedLitertOutputs ?: error("LiteRT output buffers unavailable")

		cachedLitertModel = readyModel
		cachedLitertSpec = spec
		cachedModelPath = modelFile.absolutePath
		cachedModelModified = mod
		cachedAccel = accelerator
		_activeDelegateLabel.value = activeLabel
		return LitertBundle(readyModel, spec, readyIn, readyOut)
	}

	private fun inspectTfliteSpec(modelFile: File): TfliteSpec {
		val opts = Interpreter.Options().setNumThreads(1).setUseXNNPACK(false)
		val interp = Interpreter(modelFile, opts)
		try {
			val inTensor = interp.getInputTensor(0)
			val outTensor = interp.getOutputTensor(0)
			val inShape = inTensor.shape()
			val outShape = outTensor.shape()
			val isNchw = inShape.size == 4 && inShape[1] == 3 && inShape[3] != 3
			val rawH = if (isNchw) inShape.getOrElse(2) { -1 } else inShape.getOrElse(1) { -1 }
			val rawW = if (isNchw) inShape.getOrElse(3) { -1 } else inShape.getOrElse(2) { -1 }
			val sig = inTensor.shapeSignature()
			val dynamic = rawH <= 0 || rawW <= 0 || sig.any { it <= 0 }
			val preferred = preferredTileSize()
			val fixedH = if (rawH > 16 && !dynamic) rawH else preferred
			val fixedW = if (rawW > 16 && !dynamic) rawW else preferred
			val rawOutH = if (isNchw) outShape.getOrElse(2) { fixedH * 2 } else outShape.getOrElse(1) { fixedH * 2 }
			val scale = if (rawOutH > 0 && rawH > 0) detectScale(rawH, rawOutH) else 2
			val inQ = inTensor.quantizationParams()
			val outQ = outTensor.quantizationParams()
			return TfliteSpec(
				isNchw = isNchw,
				fixedH = fixedH,
				fixedW = fixedW,
				inDataType = inTensor.dataType(),
				outDataType = outTensor.dataType(),
				inScale = inQ.scale,
				inZeroPoint = inQ.zeroPoint,
				outScale = outQ.scale,
				outZeroPoint = outQ.zeroPoint,
				scaleFactor = scale.coerceIn(2, 4),
				hasDynamicShape = dynamic,
			)
		} finally {
			runCatching { interp.close() }
		}
	}

	private fun closeSessionLocked() {
		runCatching { cachedOrtSession?.close() }
		cachedOrtSession = null
		cachedOrtUsingHw = false

		cachedLitertInputs?.forEach { runCatching { it.close() } }
		cachedLitertOutputs?.forEach { runCatching { it.close() } }
		runCatching { cachedLitertModel?.close() }
		cachedLitertInputs = null
		cachedLitertOutputs = null
		cachedLitertModel = null
		cachedLitertSpec = null

		cachedModelPath = null
		cachedModelModified = 0L
		cachedAccel = null

		poolInputFloatBuffer = null
		poolInputByteBuffer = null
		poolInFloatArray = null
		poolInByteArray = null
		poolOutFloatArray = null
		poolOutByteArray = null
	}

	private fun scheduleIdleCloseLocked() {
		idleCloseJob?.cancel()
		idleCloseJob = idleScope.launch {
			delay(IDLE_SESSION_CLOSE_MS)
			sessionMutex.withLock {
				closeSessionLocked()
			}
		}
	}

	// --- Reusable Direct & Typed Buffer Pool ---

	private fun preferredTileSize(): Int =
		if (deviceHasHighRam) HIGH_RAM_TILE_SIZE else LOW_RAM_TILE_SIZE

	private fun obtainDirectFloatBuffer(floatCount: Int): FloatBuffer {
		val neededBytes = floatCount * 4
		val existingByteBuf = poolInputByteBuffer
		val existingFloatBuf = poolInputFloatBuffer
		if (existingByteBuf != null && existingFloatBuf != null && existingByteBuf.capacity() >= neededBytes) {
			existingFloatBuf.clear()
			return existingFloatBuf
		}
		val byteBuf = ByteBuffer.allocateDirect(neededBytes).order(ByteOrder.nativeOrder())
		val fBuf = byteBuf.asFloatBuffer()
		poolInputByteBuffer = byteBuf
		poolInputFloatBuffer = fBuf
		return fBuf
	}

	private fun obtainInFloatArray(size: Int): FloatArray {
		val cur = poolInFloatArray
		if (cur != null && cur.size == size) return cur
		return FloatArray(size).also { poolInFloatArray = it }
	}

	private fun obtainInByteArray(size: Int): ByteArray {
		val cur = poolInByteArray
		if (cur != null && cur.size == size) return cur
		return ByteArray(size).also { poolInByteArray = it }
	}

	private fun obtainOutFloatArray(minSize: Int): FloatArray {
		val cur = poolOutFloatArray
		if (cur != null && cur.size >= minSize) return cur
		return FloatArray(minSize).also { poolOutFloatArray = it }
	}

	private fun quantizeChannel(channelByte: Int, type: DataType, scale: Float, zeroPoint: Int): Byte {
		val norm = channelByte * INV_255
		val q = if (scale > 0f) kotlin.math.round(norm / scale).toInt() + zeroPoint else channelByte
		return when (type) {
			DataType.INT8 -> q.coerceIn(-128, 127).toByte()
			else -> q.coerceIn(0, 255).toByte()
		}
	}

	private fun dequantizeChannel(raw: Byte, type: DataType, scale: Float, zeroPoint: Int): Float {
		val intVal = when (type) {
			DataType.INT8 -> raw.toInt()
			else -> raw.toInt() and 0xFF
		}
		return if (scale > 0f) {
			(intVal - zeroPoint) * scale
		} else {
			intVal * INV_255
		}
	}

	private fun writeNchwTileIntoDestination(
		outFloat: FloatArray,
		outW: Int,
		outH: Int,
		outPlane: Int,
		modelScale: Int,
		coreStartX: Int,
		coreStartY: Int,
		cw: Int,
		ch: Int,
		dstX0: Int,
		dstY0: Int,
		dstW: Int,
		dstPixels: IntArray,
	) {
		val outTileW = cw * TARGET_SCALE
		val outTileH = ch * TARGET_SCALE
		val planeG = outPlane
		val planeB = outPlane * 2

		when (modelScale) {
			2 -> {
				for (oy in 0 until outTileH) {
					val sy = (coreStartY + oy).coerceIn(0, outH - 1)
					val outRow = sy * outW
					val dstRow = (dstY0 + oy) * dstW + dstX0
					for (ox in 0 until outTileW) {
						val sx = (coreStartX + ox).coerceIn(0, outW - 1)
						val idx = outRow + sx
						val r = (outFloat[idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						val g = (outFloat[planeG + idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						val b = (outFloat[planeB + idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						dstPixels[dstRow + ox] = Color.rgb(r, g, b)
					}
				}
			}

			4 -> {
				for (oy in 0 until outTileH) {
					val sy0 = (coreStartY + oy * 2).coerceIn(0, outH - 1)
					val sy1 = (sy0 + 1).coerceIn(0, outH - 1)
					val dstRow = (dstY0 + oy) * dstW + dstX0
					for (ox in 0 until outTileW) {
						val sx0 = (coreStartX + ox * 2).coerceIn(0, outW - 1)
						val sx1 = (sx0 + 1).coerceIn(0, outW - 1)
						val i00 = sy0 * outW + sx0
						val i01 = sy0 * outW + sx1
						val i10 = sy1 * outW + sx0
						val i11 = sy1 * outW + sx1

						val r = ((outFloat[i00] + outFloat[i01] + outFloat[i10] + outFloat[i11]) * 63.75f + 0.5f)
							.toInt().coerceIn(0, 255)
						val g = ((outFloat[planeG + i00] + outFloat[planeG + i01] + outFloat[planeG + i10] + outFloat[planeG + i11]) * 63.75f + 0.5f)
							.toInt().coerceIn(0, 255)
						val b = ((outFloat[planeB + i00] + outFloat[planeB + i01] + outFloat[planeB + i10] + outFloat[planeB + i11]) * 63.75f + 0.5f)
							.toInt().coerceIn(0, 255)
						dstPixels[dstRow + ox] = Color.rgb(r, g, b)
					}
				}
			}

			else -> {
				for (oy in 0 until outTileH) {
					val sy = (coreStartY + oy / TARGET_SCALE).coerceIn(0, outH - 1)
					val outRow = sy * outW
					val dstRow = (dstY0 + oy) * dstW + dstX0
					for (ox in 0 until outTileW) {
						val sx = (coreStartX + ox / TARGET_SCALE).coerceIn(0, outW - 1)
						val idx = outRow + sx
						val r = (outFloat[idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						val g = (outFloat[planeG + idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						val b = (outFloat[planeB + idx] * 255f + 0.5f).toInt().coerceIn(0, 255)
						dstPixels[dstRow + ox] = Color.rgb(r, g, b)
					}
				}
			}
		}
	}

	private fun writeNhwcTileIntoDestination(
		outFloat: FloatArray?,
		outBytes: ByteArray?,
		outDataType: DataType,
		outScale: Float,
		outZeroPoint: Int,
		outW: Int,
		outH: Int,
		modelScale: Int,
		coreStartX: Int,
		coreStartY: Int,
		cw: Int,
		ch: Int,
		dstX0: Int,
		dstY0: Int,
		dstW: Int,
		dstH: Int,
		dstPixels: IntArray,
	) {
		val outTileW = cw * TARGET_SCALE
		val outTileH = ch * TARGET_SCALE
		fun readRgb(flatIdx: Int): Int {
			return if (outFloat != null) {
				val base = flatIdx * 3
				val r = (outFloat[base] * 255f + 0.5f).toInt().coerceIn(0, 255)
				val g = (outFloat[base + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
				val b = (outFloat[base + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
				Color.rgb(r, g, b)
			} else {
				val bArr = outBytes!!
				val base = flatIdx * 3
				val r = (dequantizeChannel(bArr[base], outDataType, outScale, outZeroPoint) * 255f + 0.5f).toInt().coerceIn(0, 255)
				val g = (dequantizeChannel(bArr[base + 1], outDataType, outScale, outZeroPoint) * 255f + 0.5f).toInt().coerceIn(0, 255)
				val b = (dequantizeChannel(bArr[base + 2], outDataType, outScale, outZeroPoint) * 255f + 0.5f).toInt().coerceIn(0, 255)
				Color.rgb(r, g, b)
			}
		}

		for (oy in 0 until outTileH) {
			if (dstY0 + oy >= dstH) break
			val sy = when (modelScale) {
				4 -> (coreStartY + oy * 2).coerceIn(0, outH - 1)
				else -> (coreStartY + oy).coerceIn(0, outH - 1)
			}
			val rowOffset = sy * outW
			val dstRow = (dstY0 + oy) * dstW + dstX0
			for (ox in 0 until outTileW) {
				if (dstX0 + ox >= dstW) break
				val sx = when (modelScale) {
					4 -> (coreStartX + ox * 2).coerceIn(0, outW - 1)
					else -> (coreStartX + ox).coerceIn(0, outW - 1)
				}
				dstPixels[dstRow + ox] = readRgb(rowOffset + sx)
			}
		}
	}

	private fun detectScale(inH: Int, outH: Int): Int {
		val approx = (outH + inH / 2) / inH
		return when {
			approx >= 3 -> 4
			approx >= 2 -> 2
			else -> 1
		}
	}

	private fun reflectCoord(coord: Int, size: Int): Int {
		if (size <= 1) return 0
		var c = coord
		if (c < 0) c = -c
		if (c >= size) c = (2 * size - 2 - c).coerceIn(0, size - 1)
		return c.coerceIn(0, size - 1)
	}

	companion object {
		private const val HIGH_RAM_TILE_SIZE = 512
		private const val LOW_RAM_TILE_SIZE = 256
		private const val HIGH_RAM_THRESHOLD_BYTES = 7_500L * 1024L * 1024L
		private const val DEFAULT_PAD = 24
		private const val TARGET_SCALE = 2
		private const val INV_255 = 1f / 255f
		private const val IDLE_SESSION_CLOSE_MS = 90_000L
	}
}
