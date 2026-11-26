package org.nemuri.app.crash

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object GlobalExceptionHandler {

	private val installed = AtomicBoolean(false)

	@Volatile
	var activeDelegateHint: String = "Idle"

	fun install(context: Context) {
		if (!installed.compareAndSet(false, true)) return
		val appContext = context.applicationContext
		val startElapsedMs = SystemClock.elapsedRealtime()
		val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

		// Clear fast-crash loop counter once the process survives past the 4s startup window
		Handler(Looper.getMainLooper()).postDelayed(
			{ CrashLogStore.resetFastCrashCounter(appContext) },
			4_500L,
		)

		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			try {
				val elapsed = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
				CrashLogStore.buildAndSaveCrashReport(
					context = appContext,
					threadName = thread.name,
					throwable = throwable,
					activeDelegateHint = activeDelegateHint,
				)
				val canShowRecoveryUi = CrashLogStore.recordCrashAndShouldLaunchUi(appContext, elapsed)
				if (canShowRecoveryUi) {
					val recoveryIntent = Intent(appContext, CrashReportActivity::class.java).apply {
						addFlags(
							Intent.FLAG_ACTIVITY_NEW_TASK or
								Intent.FLAG_ACTIVITY_CLEAR_TASK or
								Intent.FLAG_ACTIVITY_NO_ANIMATION,
						)
					}
					appContext.startActivity(recoveryIntent)
					runCatching { Thread.sleep(160L) }
					Process.killProcess(Process.myPid())
					exitProcess(10)
				} else {
					previousHandler?.uncaughtException(thread, throwable)
				}
			} catch (_: Throwable) {
				previousHandler?.uncaughtException(thread, throwable)
			}
		}
	}
}
