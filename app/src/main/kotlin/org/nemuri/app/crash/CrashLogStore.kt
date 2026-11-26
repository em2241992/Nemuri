package org.nemuri.app.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import org.nemuri.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CrashLogStore {

	private const val CRASH_FILE_NAME = "last_crash.txt"
	private const val GUARD_FILE_NAME = "last_crash_guard.txt"
	private const val FAST_CRASH_WINDOW_MS = 4_000L
	private const val GUARD_COOLDOWN_MS = 30_000L
	private const val MAX_STACK_CHARS = 48_000

	private val SENSITIVE_QUERY_REGEX = Regex(
		"""(?i)([?&](?:api[_-]?key|key|token|access[_-]?token|refresh[_-]?token|password|passwd|secret|auth|code|session)=)([^&\s"']+)""",
	)
	private val SENSITIVE_HEADER_REGEX = Regex(
		"""(?i)((?:Authorization|Bearer|X-Api-Key|Cookie|Set-Cookie)\s*[:=]\s*)([^\r\n"']+)""",
	)
	private val DISCORD_OR_BEARER_TOKEN_REGEX = Regex(
		"""\b(AIza[0-9A-Za-z_-]{20,}|sk-[0-9A-Za-z_-]{20,}|ya29\.[0-9A-Za-z_-]{20,})\b""",
	)

	fun getCrashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

	private fun getGuardFile(context: Context): File = File(context.filesDir, GUARD_FILE_NAME)

	fun hasLastCrashLog(context: Context): Boolean = runCatching {
		val file = getCrashFile(context)
		file.isFile && file.length() > 0L
	}.getOrDefault(false)

	fun readLastCrashLog(context: Context): String? = runCatching {
		val file = getCrashFile(context)
		if (file.isFile && file.length() > 0L) file.readText() else null
	}.getOrNull()

	fun clearLastCrashLog(context: Context) {
		runCatching { getCrashFile(context).delete() }
	}

	fun resetFastCrashCounter(context: Context) {
		runCatching { getGuardFile(context).delete() }
	}

	/**
	 * Returns `true` if `CrashReportActivity` is safe to launch, or `false` if the app crashed
	 * within 4 seconds of startup twice in a row (preventing infinite relaunch loops).
	 */
	fun recordCrashAndShouldLaunchUi(context: Context, elapsedSinceStartMs: Long): Boolean = runCatching {
		val guardFile = getGuardFile(context)
		val now = System.currentTimeMillis()
		if (elapsedSinceStartMs >= FAST_CRASH_WINDOW_MS) {
			guardFile.delete()
			return@runCatching true
		}
		var prevCount = 0
		var prevEpoch = 0L
		if (guardFile.isFile) {
			val parts = guardFile.readText().trim().split('|')
			prevCount = parts.getOrNull(0)?.toIntOrNull() ?: 0
			prevEpoch = parts.getOrNull(1)?.toLongOrNull() ?: 0L
		}
		val nextCount = if (now - prevEpoch <= GUARD_COOLDOWN_MS) prevCount + 1 else 1
		guardFile.parentFile?.mkdirs()
		guardFile.writeText("$nextCount|$now")
		nextCount < 2
	}.getOrDefault(false)

	fun buildAndSaveCrashReport(
		context: Context,
		threadName: String,
		throwable: Throwable,
		activeDelegateHint: String?,
	): String {
		val utcFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
			timeZone = TimeZone.getTimeZone("UTC")
		}
		val nowDate = Date()
		val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Build.SOC_MODEL.orEmpty().trim().ifBlank { Build.HARDWARE.orEmpty() }
		} else {
			Build.HARDWARE.orEmpty()
		}
		val delegateSummary = runCatching {
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			val aiMode = prefs.getString("ai_mode", "AUTO") ?: "AUTO"
			val neuralOn = prefs.getBoolean("neural_upscale_enabled", false)
			val neuralAcc = prefs.getString("neural_upscale_accelerator", "AUTO") ?: "AUTO"
			val shader = prefs.getString("reader_shader_mode", "OFF") ?: "OFF"
			val live = activeDelegateHint?.takeIf { it.isNotBlank() } ?: "Idle"
			"AI=$aiMode • Upscale=${if (neuralOn) neuralAcc else "OFF"} ($live) • Shader=$shader"
		}.getOrDefault(activeDelegateHint ?: "Unknown")

		val rawStack = runCatching { throwable.stackTraceToString() }
			.getOrElse { "${throwable.javaClass.name}: ${throwable.message}" }
			.take(MAX_STACK_CHARS)

		val sanitizedStack = redactSensitive(rawStack)
		val report = buildString(sanitizedStack.length + 512) {
			appendLine("=== Nemuri Local Crash Report ===")
			appendLine("Timestamp (UTC): ${utcFmt.format(nowDate)}")
			appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
			appendLine("Build Flavor: ${BuildConfig.FLAVOR} (${BuildConfig.BUILD_TYPE})")
			appendLine("Android SDK: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
			appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
			appendLine("SoC: $socModel")
			appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
			appendLine("Active AI/Upscale Delegate: $delegateSummary")
			appendLine("Thread: $threadName")
			appendLine("Exception: ${throwable.javaClass.name}: ${redactSensitive(throwable.message.orEmpty())}")
			appendLine()
			appendLine("--- Stack Trace ---")
			appendLine(sanitizedStack)
		}

		runCatching {
			val file = getCrashFile(context)
			file.parentFile?.mkdirs()
			file.writeText(report)
		}
		return report
	}

	fun formatAsMarkdown(reportText: String): String = buildString(reportText.length + 32) {
		appendLine("```text")
		appendLine(reportText.trimEnd())
		appendLine("```")
	}

	fun copyToClipboard(context: Context, reportText: String): Boolean = runCatching {
		val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
		cm.setPrimaryClip(ClipData.newPlainText("Nemuri Crash Log", formatAsMarkdown(reportText)))
		true
	}.getOrDefault(false)

	fun createShareIntent(context: Context, reportText: String): Intent {
		val crashFile = getCrashFile(context)
		if (!crashFile.isFile) {
			runCatching {
				crashFile.parentFile?.mkdirs()
				crashFile.writeText(reportText)
			}
		}
		val sendIntent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_SUBJECT, "Nemuri Crash Report (${BuildConfig.VERSION_NAME})")
			putExtra(Intent.EXTRA_TEXT, formatAsMarkdown(reportText))
			runCatching {
				val fileUri = FileProvider.getUriForFile(
					context,
					"${context.packageName}.files",
					crashFile,
				)
				putExtra(Intent.EXTRA_STREAM, fileUri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		}
		return Intent.createChooser(sendIntent, "Share Crash Log")
	}

	fun redactSensitive(input: String): String {
		if (input.isEmpty()) return input
		return input
			.replace(SENSITIVE_QUERY_REGEX, "$1[REDACTED]")
			.replace(SENSITIVE_HEADER_REGEX, "$1[REDACTED]")
			.replace(DISCORD_OR_BEARER_TOKEN_REGEX, "[REDACTED_KEY]")
	}
}
