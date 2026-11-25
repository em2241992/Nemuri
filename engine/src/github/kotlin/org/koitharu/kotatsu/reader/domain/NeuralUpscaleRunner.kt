package org.koitharu.kotatsu.reader.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Tiled ONNX Runtime super-resolution runner (GitHub flavor) supporting NNAPI (NPU/GPU)
 * with automatic XNNPACK CPU fallback and 90s idle session cleanup.
 */
@Singleton
class NeuralUpscaleRunner @Inject constructor() {

	val isSupported: Boolean = true

	private val sessionMutex = Mutex()
	private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var idleCloseJob: Job? = null

	private var cachedEnv: OrtEnvironment? = null
	private var cachedSession: OrtSession? = null
	private var cachedModelPath: String? = null
	private var cachedAccel: NeuralAccelerator? = null
	private var cachedUsingNnapi: Boolean = false

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

		sessionMutex.withLock {
			idleCloseJob?.cancel()
			try {
				val env = cachedEnv ?: OrtEnvironment.getEnvironment().also { cachedEnv = it }
				var session = getOrCreateSessionLocked(env, modelFile, accelerator, allowNnapi = accelerator != NeuralAccelerator.XNNPACK)
				var inputName = session.inputNames.firstOrNull() ?: "input"

				val tilesX = (srcW + CORE_TILE - 1) / CORE_TILE
				val tilesY = (srcH + CORE_TILE - 1) / CORE_TILE

				for (ty in 0 until tilesY) {
					val y0 = ty * CORE_TILE
					val ch = (srcH - y0).coerceAtMost(CORE_TILE)
					for (tx in 0 until tilesX) {
						coroutineContext.ensureActive()
						val x0 = tx * CORE_TILE
						val cw = (srcW - x0).coerceAtMost(CORE_TILE)

						// Round padded dimensions to multiples of 16 for UNet/CUNet skip-connection compatibility
						val inW = roundUp16(cw + PAD * 2)
						val inH = roundUp16(ch + PAD * 2)
						val planeSize = inW * inH
						val byteCap = planeSize * 3 * 4
						val floatBuf = ByteBuffer.allocateDirect(byteCap)
							.order(ByteOrder.nativeOrder())
							.asFloatBuffer()

						val rArr = FloatArray(planeSize)
						val gArr = FloatArray(planeSize)
						val bArr = FloatArray(planeSize)

						var idx = 0
						for (py in 0 until inH) {
							val sy = reflectCoord(y0 - PAD + py, srcH)
							val rowOffset = sy * srcW
							for (px in 0 until inW) {
								val sx = reflectCoord(x0 - PAD + px, srcW)
								val pixel = srcPixels[rowOffset + sx]
								rArr[idx] = ((pixel ushr 16) and 0xFF) * INV_255
								gArr[idx] = ((pixel ushr 8) and 0xFF) * INV_255
								bArr[idx] = (pixel and 0xFF) * INV_255
								idx++
							}
						}
						floatBuf.put(rArr)
						floatBuf.put(gArr)
						floatBuf.put(bArr)
						floatBuf.rewind()

						val shape = longArrayOf(1L, 3L, inH.toLong(), inW.toLong())
						var tileDone = false
						var retriedCpu = false

						while (!tileDone) {
							val inputTensor = OnnxTensor.createTensor(env, floatBuf, shape)
							try {
								session.run(Collections.singletonMap(inputName, inputTensor)).use { results ->
									val outTensor = results.iterator().next().value as? OnnxTensor
										?: error("Model output is not an OnnxTensor")
									val outShape = (outTensor.info as? TensorInfo)?.shape
										?: longArrayOf(1L, 3L, (inH * TARGET_SCALE).toLong(), (inW * TARGET_SCALE).toLong())
									val outH = outShape.getOrNull(2)?.toInt() ?: (inH * TARGET_SCALE)
									val outW = outShape.getOrNull(3)?.toInt() ?: (inW * TARGET_SCALE)
									val modelScale = detectScale(inH, outH)
									val cropX = ((inW * modelScale - outW) / 2).coerceAtLeast(0)
									val cropY = ((inH * modelScale - outH) / 2).coerceAtLeast(0)
									val coreStartX = (PAD * modelScale - cropX).coerceAtLeast(0)
									val coreStartY = (PAD * modelScale - cropY).coerceAtLeast(0)

									val outPlane = outW * outH
									val outFloat = FloatArray(outPlane * 3)
									outTensor.floatBuffer.apply {
										rewind()
										get(outFloat)
									}

									writeTileIntoDestination(
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
										dstPixels = outPixels,
									)
								}
								tileDone = true
							} catch (t: Throwable) {
								if (!retriedCpu && cachedUsingNnapi) {
									// Some mobile NNAPI drivers fail on specific padding/PReLU ops; fall back cleanly to XNNPACK CPU.
									retriedCpu = true
									closeSessionLocked()
									session = getOrCreateSessionLocked(env, modelFile, accelerator, allowNnapi = false)
									inputName = session.inputNames.firstOrNull() ?: "input"
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

	private fun getOrCreateSessionLocked(
		env: OrtEnvironment,
		modelFile: File,
		accelerator: NeuralAccelerator,
		allowNnapi: Boolean,
	): OrtSession {
		val existing = cachedSession
		if (existing != null &&
			cachedModelPath == modelFile.absolutePath &&
			cachedAccel == accelerator &&
			(allowNnapi || !cachedUsingNnapi)
		) {
			return existing
		}
		closeSessionLocked()

		val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
		var usedNnapi = false
		val session = if (allowNnapi) {
			val hwAttempt = runCatching {
				val opts = OrtSession.SessionOptions().apply {
					setIntraOpNumThreads(threads)
					setInterOpNumThreads(1)
					setMemoryPatternOptimization(true)
					addNnapi()
					addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
				}
				usedNnapi = true
				opts.use { env.createSession(modelFile.absolutePath, it) }
			}
			hwAttempt.getOrElse {
				usedNnapi = false
				createCpuSession(env, modelFile, threads)
			}
		} else {
			createCpuSession(env, modelFile, threads)
		}

		cachedSession = session
		cachedModelPath = modelFile.absolutePath
		cachedAccel = accelerator
		cachedUsingNnapi = usedNnapi
		return session
	}

	private fun createCpuSession(
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

	private fun closeSessionLocked() {
		runCatching { cachedSession?.close() }
		cachedSession = null
		cachedModelPath = null
		cachedAccel = null
		cachedUsingNnapi = false
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

	private fun writeTileIntoDestination(
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
				// Downsample 4x output directly by 2x2 area averaging into the 2x destination buffer.
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

	private fun detectScale(inH: Int, outH: Int): Int {
		val approx = (outH + inH / 2) / inH
		return when {
			approx >= 3 -> 4
			approx >= 2 -> 2
			else -> 1
		}
	}

	private fun roundUp16(value: Int): Int = ((value + 15) / 16) * 16

	private fun reflectCoord(coord: Int, size: Int): Int {
		if (size <= 1) return 0
		var c = coord
		if (c < 0) c = -c
		if (c >= size) c = (2 * size - 2 - c).coerceIn(0, size - 1)
		return c.coerceIn(0, size - 1)
	}

	companion object {
		private const val CORE_TILE = 224
		private const val PAD = 24
		private const val TARGET_SCALE = 2
		private const val INV_255 = 1f / 255f
		private const val IDLE_SESSION_CLOSE_MS = 90_000L
	}
}
