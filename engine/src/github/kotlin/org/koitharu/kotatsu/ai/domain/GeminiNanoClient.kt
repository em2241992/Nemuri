package org.koitharu.kotatsu.ai.domain

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano client backed by Google ML Kit GenAI Prompt (`1.0.0-beta5`), probing all
 * AICore feature profiles (Stable/Preview × Full/Fast) and keeping the active client warm
 * so Samsung One UI / S25 devices with Fast or Preview provisioning are detected properly.
 */
@Singleton
class GeminiNanoClient @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private data class NanoProfile(
		val label: String,
		val releaseStage: Int,
		val preference: Int,
	)

	private val clientMutex = Mutex()
	private var cachedProfile: NanoProfile? = null
	private var cachedClient: GenerativeModel? = null

	suspend fun checkAvailability(): NanoAvailability = checkDiagnostics().availability

	suspend fun checkDiagnostics(): NanoDiagnostics = withContext(Dispatchers.IO) {
		val pkgInfo = runCatching {
			context.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
		}.getOrNull()
			?: return@withContext NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "Android AICore ($AICORE_PACKAGE) is not installed on this device.",
			)

		val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
		val versionName = pkgInfo.versionName ?: versionCode.toString()
		if (versionName.contains("stub", ignoreCase = true) || versionCode < MIN_AICORE_VERSION_CODE) {
			return@withContext NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "Android AICore is still a factory stub ($versionName). Update 'Android AICore' via Google Play / Settings → Google → System services, then reboot.",
				aiCoreVersionName = versionName,
			)
		}

		clientMutex.withLock {
			var downloadableCandidate: Pair<NanoProfile, GenerativeModel>? = null
			var downloadingCandidate: Pair<NanoProfile, GenerativeModel>? = null
			var lastGenAiError: String? = null

			val order = buildList {
				cachedProfile?.let(::add)
				for (p in PROFILES) {
					if (p != cachedProfile) add(p)
				}
			}

			for (profile in order) {
				val existing = if (profile == cachedProfile) cachedClient else null
				val model = existing ?: runCatching { createClient(profile) }.getOrNull() ?: continue
				val statusRes = runCatching { model.checkStatus() }
				if (statusRes.isFailure) {
					val err = statusRes.exceptionOrNull()
					lastGenAiError = formatGenAiError(err)
					if (existing == null) runCatching { model.close() }
					continue
				}
				when (statusRes.getOrThrow()) {
					FeatureStatus.AVAILABLE -> {
						if (existing == null) {
							closeOtherClientLocked(keep = model)
							cachedClient = model
							cachedProfile = profile
						}
						if (downloadableCandidate != null && downloadableCandidate.second !== model) {
							runCatching { downloadableCandidate.second.close() }
						}
						if (downloadingCandidate != null && downloadingCandidate.second !== model) {
							runCatching { downloadingCandidate.second.close() }
						}
						return@withContext NanoDiagnostics(
							availability = NanoAvailability.AVAILABLE,
							detailMessage = "AICore $versionName • ${profile.label} ready",
							activeProfileLabel = profile.label,
							aiCoreVersionName = versionName,
						)
					}

					FeatureStatus.DOWNLOADING -> {
						if (downloadingCandidate == null) {
							downloadingCandidate = profile to model
						} else if (existing == null) {
							runCatching { model.close() }
						}
					}

					FeatureStatus.DOWNLOADABLE -> {
						if (downloadableCandidate == null) {
							downloadableCandidate = profile to model
						} else if (existing == null) {
							runCatching { model.close() }
						}
					}

					else -> {
						if (existing == null) runCatching { model.close() }
					}
				}
			}

			downloadingCandidate?.let { (profile, model) ->
				closeOtherClientLocked(keep = model)
				cachedClient = model
				cachedProfile = profile
				if (downloadableCandidate != null && downloadableCandidate.second !== model) {
					runCatching { downloadableCandidate.second.close() }
				}
				return@withContext NanoDiagnostics(
					availability = NanoAvailability.DOWNLOADING,
					detailMessage = "AICore $versionName • Downloading ${profile.label} model weights…",
					activeProfileLabel = profile.label,
					aiCoreVersionName = versionName,
				)
			}

			downloadableCandidate?.let { (profile, model) ->
				closeOtherClientLocked(keep = model)
				cachedClient = model
				cachedProfile = profile
				return@withContext NanoDiagnostics(
					availability = NanoAvailability.DOWNLOADABLE,
					detailMessage = "AICore $versionName • ${profile.label} weights can be downloaded",
					activeProfileLabel = profile.label,
					aiCoreVersionName = versionName,
				)
			}

			val errorPart = if (!lastGenAiError.isNullOrBlank()) " ($lastGenAiError)" else ""
			NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "AICore $versionName reported no active prompt profile$errorPart. On Galaxy S25: update Android AICore in Play Store and enable Developer options → AICore Settings → Enable on-device GenAI features.",
				aiCoreVersionName = versionName,
			)
		}
	}

	suspend fun triggerDownload(onProgress: (Long) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
		val diag = checkDiagnostics()
		if (diag.availability == NanoAvailability.UNAVAILABLE) {
			return@withContext Result.failure(IllegalStateException(diag.detailMessage))
		}
		clientMutex.withLock {
			val model = cachedClient ?: runCatching { createClient(PROFILES.first()) }.getOrElse {
				return@withContext Result.failure(it)
			}.also {
				cachedClient = it
				cachedProfile = PROFILES.first()
			}
			try {
				var failedError: Throwable? = null
				model.download().collect { status ->
					when (status) {
						is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded)
						is DownloadStatus.DownloadFailed -> failedError = status.e
						else -> Unit
					}
				}
				val err = failedError
				if (err != null) Result.failure(err) else Result.success(Unit)
			} catch (t: Throwable) {
				Result.failure(t)
			}
		}
	}

	suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
		val diag = checkDiagnostics()
		if (diag.availability != NanoAvailability.AVAILABLE) {
			return@withContext Result.failure(IllegalStateException(diag.detailMessage))
		}
		clientMutex.withLock {
			val model = cachedClient ?: runCatching { createClient(cachedProfile ?: PROFILES.first()) }.getOrElse {
				return@withContext Result.failure(it)
			}
			try {
				val status = model.checkStatus()
				if (status != FeatureStatus.AVAILABLE) {
					return@withContext Result.failure(
						IllegalStateException("Gemini Nano is ${if (status == FeatureStatus.DOWNLOADABLE) "not downloaded yet" else "unavailable"}"),
					)
				}
				val response = model.generateContent(prompt)
				val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
				if (text.isEmpty()) {
					Result.failure(IllegalStateException("Gemini Nano returned an empty response"))
				} else {
					Result.success(text)
				}
			} catch (t: Throwable) {
				Result.failure(t)
			}
		}
	}

	private fun createClient(profile: NanoProfile): GenerativeModel {
		val modelCfg = ModelConfig.builder().apply {
			releaseStage = profile.releaseStage
			preference = profile.preference
		}.build()
		val genCfg = GenerationConfig.builder().apply {
			modelConfig = modelCfg
		}.build()
		return Generation.getClient(genCfg)
	}

	private fun closeOtherClientLocked(keep: GenerativeModel?) {
		val old = cachedClient
		if (old != null && old !== keep) {
			runCatching { old.close() }
		}
	}

	private fun formatGenAiError(err: Throwable?): String? = when (err) {
		is GenAiException -> "GenAiException code=${err.errorCode}, aicore=${err.aiCoreErrorCode}"
		null -> null
		else -> err.localizedMessage ?: err::class.java.simpleName
	}

	private companion object {
		const val AICORE_PACKAGE = "com.google.android.aicore"
		const val MIN_AICORE_VERSION_CODE = 193_575L

		val PROFILES = listOf(
			NanoProfile("Stable • Full", ModelReleaseStage.STABLE, ModelPreference.FULL),
			NanoProfile("Stable • Fast", ModelReleaseStage.STABLE, ModelPreference.FAST),
			NanoProfile("Preview • Full", ModelReleaseStage.PREVIEW, ModelPreference.FULL),
			NanoProfile("Preview • Fast", ModelReleaseStage.PREVIEW, ModelPreference.FAST),
		)
	}
}
