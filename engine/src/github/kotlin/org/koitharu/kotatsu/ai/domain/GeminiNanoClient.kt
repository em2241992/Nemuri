package org.koitharu.kotatsu.ai.domain

import android.content.Context
import android.os.Build
import android.os.Process
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano client (GitHub flavor) probing Google ML Kit GenAI Prompt profiles AND falling back
 * cleanly to task-specific ML Kit GenAI Summarization (`genai-summarization`) on Galaxy S25 (`SM8750`)
 * devices where AICore blocks open-ended prompt profiles.
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
	private var cachedBriefSummarizer: Summarizer? = null
	private var cachedDetailedSummarizer: Summarizer? = null
	private var lastDiagSupportsOpenPrompt: Boolean = false
	private var lastDiagSupportsSummarization: Boolean = false

	suspend fun checkAvailability(): NanoAvailability = checkDiagnostics().availability

	suspend fun checkDiagnostics(): NanoDiagnostics = withContext(Dispatchers.IO) {
		if (Build.SUPPORTED_64_BIT_ABIS.isEmpty() || !Process.is64Bit()) {
			return@withContext NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "Gemini Nano / AICore requires a 64-bit Android device.",
			)
		}

		val pkgInfo = runCatching {
			context.packageManager.getPackageInfo(AICORE_PACKAGE, 0)
		}.getOrNull()
			?: return@withContext NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "Android AICore ($AICORE_PACKAGE) is not installed on this device.",
			)

		val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkgInfo)
		val versionName = pkgInfo.versionName ?: versionCode.toString()
		if (versionName.contains("stub", ignoreCase = true) || versionCode < MIN_AICORE_VERSION_CODE) {
			return@withContext NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "Android AICore is still a factory stub ($versionName). Update 'Android AICore' via Google Play, then reboot.",
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

			// 1. Check open-ended GenAI Prompt profiles
			for (profile in order) {
				val existing = if (profile == cachedProfile) cachedClient else null
				val model = existing ?: runCatching { createPromptClient(profile) }.getOrNull() ?: continue
				val statusRes = runCatching { model.checkStatus() }
				if (statusRes.isFailure) {
					lastGenAiError = formatGenAiError(statusRes.exceptionOrNull())
					if (existing == null) runCatching { model.close() }
					continue
				}
				when (statusRes.getOrThrow()) {
					FeatureStatus.AVAILABLE -> {
						if (existing == null) {
							closeOtherPromptLocked(keep = model)
							cachedClient = model
							cachedProfile = profile
						}
						closeNonMatching(downloadableCandidate?.second, downloadingCandidate?.second, keep = model)
						lastDiagSupportsOpenPrompt = true
						lastDiagSupportsSummarization = true
						return@withContext NanoDiagnostics(
							availability = NanoAvailability.AVAILABLE,
							detailMessage = "AICore $versionName • ${profile.label} ready",
							activeProfileLabel = profile.label,
							aiCoreVersionName = versionName,
							supportsOpenPrompt = true,
							supportsSummarization = true,
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

			// 2. Galaxy S25 (SM8750) fallback: probe task-specific ML Kit GenAI Summarization API
			val summarizer = getOrCreateSummarizerLocked(RecapDepth.DETAILED)
			val sumStatusRes = if (summarizer != null) {
				runCatching { summarizer.checkFeatureStatus().await() }
			} else {
				null
			}
			val sumStatus = sumStatusRes?.getOrNull()
			if (sumStatusRes != null && sumStatusRes.isFailure && lastGenAiError.isNullOrBlank()) {
				lastGenAiError = formatGenAiError(sumStatusRes.exceptionOrNull())
			}

			when (sumStatus) {
				FeatureStatus.AVAILABLE -> {
					closeNonMatching(downloadableCandidate?.second, downloadingCandidate?.second, keep = null)
					lastDiagSupportsOpenPrompt = false
					lastDiagSupportsSummarization = true
					return@withContext NanoDiagnostics(
						availability = NanoAvailability.AVAILABLE,
						detailMessage = "AICore $versionName • Gemini Nano Summarizer ready (Recaps)",
						activeProfileLabel = "Summarizer (Brief 1-Bullet / Detailed 3-Bullets)",
						aiCoreVersionName = versionName,
						supportsOpenPrompt = false,
						supportsSummarization = true,
					)
				}

				FeatureStatus.DOWNLOADING -> {
					closeNonMatching(downloadableCandidate?.second, downloadingCandidate?.second, keep = null)
					lastDiagSupportsOpenPrompt = false
					lastDiagSupportsSummarization = true
					return@withContext NanoDiagnostics(
						availability = NanoAvailability.DOWNLOADING,
						detailMessage = "AICore $versionName • Downloading Gemini Nano Summarizer weights…",
						activeProfileLabel = "Summarizer (Brief 1-Bullet / Detailed 3-Bullets)",
						aiCoreVersionName = versionName,
						supportsOpenPrompt = false,
						supportsSummarization = true,
					)
				}

				FeatureStatus.DOWNLOADABLE -> {
					closeNonMatching(downloadableCandidate?.second, downloadingCandidate?.second, keep = null)
					lastDiagSupportsOpenPrompt = false
					lastDiagSupportsSummarization = true
					return@withContext NanoDiagnostics(
						availability = NanoAvailability.DOWNLOADABLE,
						detailMessage = "AICore $versionName • Gemini Nano Summarizer weights ready to download",
						activeProfileLabel = "Summarizer (Brief 1-Bullet / Detailed 3-Bullets)",
						aiCoreVersionName = versionName,
						supportsOpenPrompt = false,
						supportsSummarization = true,
					)
				}

				else -> Unit
			}

			downloadingCandidate?.let { (profile, model) ->
				closeOtherPromptLocked(keep = model)
				cachedClient = model
				cachedProfile = profile
				if (downloadableCandidate != null && downloadableCandidate.second !== model) {
					runCatching { downloadableCandidate.second.close() }
				}
				lastDiagSupportsOpenPrompt = true
				lastDiagSupportsSummarization = true
				return@withContext NanoDiagnostics(
					availability = NanoAvailability.DOWNLOADING,
					detailMessage = "AICore $versionName • Downloading ${profile.label} model weights…",
					activeProfileLabel = profile.label,
					aiCoreVersionName = versionName,
					supportsOpenPrompt = true,
					supportsSummarization = true,
				)
			}

			downloadableCandidate?.let { (profile, model) ->
				closeOtherPromptLocked(keep = model)
				cachedClient = model
				cachedProfile = profile
				lastDiagSupportsOpenPrompt = true
				lastDiagSupportsSummarization = true
				return@withContext NanoDiagnostics(
					availability = NanoAvailability.DOWNLOADABLE,
					detailMessage = "AICore $versionName • ${profile.label} weights can be downloaded",
					activeProfileLabel = profile.label,
					aiCoreVersionName = versionName,
					supportsOpenPrompt = true,
					supportsSummarization = true,
				)
			}

			lastDiagSupportsOpenPrompt = false
			lastDiagSupportsSummarization = false
			val errorPart = if (!lastGenAiError.isNullOrBlank()) " ($lastGenAiError)" else ""
			val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
			val guidance = if (isSamsung) {
				"Update 'Android AICore' in Google Play and ensure Galaxy AI / system AI services are up to date, or download the universal Gemma local model below."
			} else {
				"Update 'Android AICore' in Google Play (on Pixel, verify Developer options → AICore Settings)."
			}
			NanoDiagnostics(
				availability = NanoAvailability.UNAVAILABLE,
				detailMessage = "AICore $versionName reported no active Gemini Nano profile$errorPart. $guidance",
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
			if (!diag.supportsOpenPrompt && diag.supportsSummarization) {
				val summarizer = getOrCreateSummarizerLocked(RecapDepth.DETAILED)
					?: return@withContext Result.failure(IllegalStateException("Unable to initialize Gemini Nano Summarizer"))
				return@withContext try {
					var failedError: Throwable? = null
					summarizer.downloadFeature(
						object : DownloadCallback {
							override fun onDownloadStarted(bytesToDownload: Long) = Unit

							override fun onDownloadProgress(totalBytesDownloaded: Long) {
								onProgress(totalBytesDownloaded)
							}

							override fun onDownloadFailed(e: GenAiException) {
								failedError = e
							}

							override fun onDownloadCompleted() = Unit
						},
					).await()
					val err = failedError
					if (err != null) Result.failure(err) else Result.success(Unit)
				} catch (t: Throwable) {
					Result.failure(t)
				}
			}

			val model = cachedClient ?: runCatching { createPromptClient(cachedProfile ?: PROFILES.first()) }.getOrElse {
				return@withContext Result.failure(it)
			}.also {
				cachedClient = it
				cachedProfile = cachedProfile ?: PROFILES.first()
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
		if (!diag.supportsOpenPrompt) {
			return@withContext Result.failure(
				IllegalStateException("AICore open-ended prompt is not provisioned on this device (Summarizer-only)."),
			)
		}
		generateWithPromptLocked(prompt)
	}

	/**
	 * Generates a "Where Was I?" recap using open-ended `genai-prompt` when available,
	 * automatically falling back to task-specific `genai-summarization` on Samsung S25 (`SM8750`).
	 */
	suspend fun summarize(
		articleText: String,
		fallbackPrompt: String,
		recapDepth: RecapDepth = RecapDepth.DETAILED,
	): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
		val diag = checkDiagnostics()
		if (diag.availability != NanoAvailability.AVAILABLE) {
			return@withContext Result.failure(IllegalStateException(diag.detailMessage))
		}
		if (diag.supportsOpenPrompt) {
			val promptRes = generateWithPromptLocked(fallbackPrompt)
			if (promptRes.isSuccess) {
				return@withContext Result.success(promptRes.getOrThrow() to "Gemini Nano (On-Device)")
			}
		}
		if (!diag.supportsSummarization) {
			return@withContext Result.failure(IllegalStateException(diag.detailMessage))
		}
		clientMutex.withLock {
			val summarizer = getOrCreateSummarizerLocked(recapDepth)
				?: return@withContext Result.failure(IllegalStateException("Unable to initialize Gemini Nano Summarizer"))
			try {
				val status = summarizer.checkFeatureStatus().await()
				if (status != FeatureStatus.AVAILABLE) {
					return@withContext Result.failure(
						IllegalStateException("Gemini Nano Summarizer is ${if (status == FeatureStatus.DOWNLOADABLE) "not downloaded yet" else "unavailable"}"),
					)
				}
				val safeArticle = ensureMinSummarizerArticle(articleText)
				val request = SummarizationRequest.builder(safeArticle).build()
				val summary = summarizer.runInference(request).await().summary.trim()
				if (summary.isEmpty()) {
					Result.failure(IllegalStateException("Gemini Nano Summarizer returned an empty summary"))
				} else {
					Result.success(summary to "Gemini Nano (AICore Summarizer)")
				}
			} catch (t: Throwable) {
				Result.failure(t)
			}
		}
	}

	/**
	 * Guarantees `>= 500` characters of clean third-person narrative prose with prompt/bullet noise
	 * stripped so ML Kit's `genai-summarization` API on Galaxy S25 (`SM8750`) never throws on `< 400` chars,
	 * including when open-prompt generation fails over to Summarizer.
	 */
	private fun ensureMinSummarizerArticle(rawArticle: String): String {
		val cleanedLines = rawArticle
			.lineSequence()
			.map { line ->
				line.trim()
					.removePrefix("- ")
					.removePrefix("* ")
					.removePrefix("• ")
					.trim()
			}
			.filter { line ->
				line.isNotEmpty() && !META_NOISE_PREFIX_REGEX.containsMatchIn(line)
			}
			.toList()

		val baseProse = cleanedLines
			.joinToString(" ")
			.replace(MULTI_SPACE_REGEX, " ")
			.trim()

		if (baseProse.length >= MIN_SUMMARIZER_ARTICLE_CHARS) {
			return baseProse
		}

		return buildString(MIN_SUMMARIZER_ARTICLE_CHARS + 160) {
			if (baseProse.isNotEmpty()) {
				append(baseProse)
				if (baseProse.last() !in ".!?") append('.')
				append(' ')
			}
			for (segment in NARRATIVE_PAD_SEGMENTS) {
				if (length >= MIN_SUMMARIZER_ARTICLE_CHARS) break
				append(segment)
				append(' ')
			}
		}.trim()
	}

	private fun getOrCreateSummarizerLocked(depth: RecapDepth): Summarizer? = when (depth) {
		RecapDepth.BRIEF -> cachedBriefSummarizer ?: runCatching {
			createSummarizerClient(SummarizerOptions.OutputType.ONE_BULLET)
		}.getOrNull()?.also { cachedBriefSummarizer = it }

		RecapDepth.DETAILED -> cachedDetailedSummarizer ?: runCatching {
			createSummarizerClient(SummarizerOptions.OutputType.THREE_BULLETS)
		}.getOrNull()?.also { cachedDetailedSummarizer = it }
	}

	private suspend fun generateWithPromptLocked(prompt: String): Result<String> = clientMutex.withLock {
		val model = cachedClient ?: runCatching { createPromptClient(cachedProfile ?: PROFILES.first()) }.getOrElse {
			return Result.failure(it)
		}
		try {
			val status = model.checkStatus()
			if (status != FeatureStatus.AVAILABLE) {
				return Result.failure(
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

	private fun createPromptClient(profile: NanoProfile): GenerativeModel {
		val modelCfg = ModelConfig.builder().apply {
			releaseStage = profile.releaseStage
			preference = profile.preference
		}.build()
		val genCfg = GenerationConfig.builder().apply {
			modelConfig = modelCfg
		}.build()
		return Generation.getClient(genCfg)
	}

	private fun createSummarizerClient(outputType: Int): Summarizer {
		val options = SummarizerOptions.builder(context)
			.setInputType(SummarizerOptions.InputType.ARTICLE)
			.setOutputType(outputType)
			.setLanguage(SummarizerOptions.Language.ENGLISH)
			.setLongInputAutoTruncationEnabled(true)
			.build()
		return Summarization.getClient(options)
	}

	private fun closeOtherPromptLocked(keep: GenerativeModel?) {
		val old = cachedClient
		if (old != null && old !== keep) {
			runCatching { old.close() }
		}
	}

	private fun closeNonMatching(a: GenerativeModel?, b: GenerativeModel?, keep: GenerativeModel?) {
		if (a != null && a !== keep && a !== cachedClient) runCatching { a.close() }
		if (b != null && b !== keep && b !== cachedClient) runCatching { b.close() }
	}

	private fun formatGenAiError(err: Throwable?): String? = when (err) {
		is GenAiException -> "GenAiException code=${err.errorCode}, aicore=${err.aiCoreErrorCode}"
		null -> null
		else -> err.localizedMessage ?: err::class.java.simpleName
	}

	private companion object {
		const val AICORE_PACKAGE = "com.google.android.aicore"
		const val MIN_AICORE_VERSION_CODE = 193_575L
		const val MIN_SUMMARIZER_ARTICLE_CHARS = 540
		val MULTI_SPACE_REGEX = Regex("\\s+")
		val META_NOISE_PREFIX_REGEX = Regex(
			"""^(task|system|instruction|respond strictly|you are\b|critical anti-spoiler|never mention|avoid spoilers)\b""",
			RegexOption.IGNORE_CASE,
		)
		val NARRATIVE_PAD_SEGMENTS = listOf(
			"Throughout the opening chapters of this serialized chronicle, the central cast confronts escalating trials across a shifting world while forging uneasy alliances and uncovering the deeper history behind their journey.",
			"Each chapter builds upon earlier turning points as rivals, companions, and competing factions test the protagonist's resolve, leading directly to the present checkpoint where the current story arc pauses.",
			"With the early conflicts established and the stakes of the expedition clear, the companions gather their strength at this milestone before the next stage of the narrative unfolds.",
		)

		val PROFILES = listOf(
			NanoProfile("Stable • Full", ModelReleaseStage.STABLE, ModelPreference.FULL),
			NanoProfile("Stable • Fast", ModelReleaseStage.STABLE, ModelPreference.FAST),
			NanoProfile("Preview • Full", ModelReleaseStage.PREVIEW, ModelPreference.FULL),
			NanoProfile("Preview • Fast", ModelReleaseStage.PREVIEW, ModelPreference.FAST),
		)
	}
}
