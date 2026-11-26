package org.nemuri.app.crash

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.nemuri.app.MainActivity
import org.nemuri.app.ui.theme.NemuriTheme
import kotlin.system.exitProcess

/**
 * Runs in an isolated `:crash` process so even database, native JNI, or Hilt startup crashes
 * render a clean, themed recovery screen without re-triggering the crash.
 */
class CrashReportActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		val prefs = runCatching { PreferenceManager.getDefaultSharedPreferences(this) }.getOrNull()
		val themeMode = runCatching {
			prefs?.getString(AppSettings.KEY_THEME, null)?.toIntOrNull()
		}.getOrNull() ?: AppCompatDelegate.MODE_NIGHT_YES
		val isAmoled = runCatching {
			prefs?.getBoolean(AppSettings.KEY_THEME_AMOLED, false) ?: false
		}.getOrDefault(false)
		val colorScheme = runCatching {
			val raw = prefs?.getString(AppSettings.KEY_COLOR_THEME, null)
			if (raw != null) ColorScheme.entries.firstOrNull { it.name == raw } ?: ColorScheme.DEFAULT
			else ColorScheme.DEFAULT
		}.getOrDefault(ColorScheme.DEFAULT)

		val reportText = CrashLogStore.readLastCrashLog(this)
			?: "No crash trace found on disk."

		setContent {
			NemuriTheme(
				themeMode = themeMode,
				amoled = isAmoled,
				colorScheme = colorScheme,
			) {
				CrashReportScreen(
					reportText = reportText,
					onCopyLog = {
						val ok = CrashLogStore.copyToClipboard(this, reportText)
						Toast.makeText(
							this,
							if (ok) "Crash log copied to clipboard" else "Unable to access clipboard",
							Toast.LENGTH_SHORT,
						).show()
					},
					onShareLog = {
						runCatching {
							startActivity(CrashLogStore.createShareIntent(this, reportText))
						}
					},
					onRestartApp = {
						CrashLogStore.resetFastCrashCounter(this)
						val restartIntent = Intent(this, MainActivity::class.java).apply {
							addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
						}
						startActivity(restartIntent)
						finish()
						exitProcess(0)
					},
				)
			}
		}
	}
}

@Composable
private fun CrashReportScreen(
	reportText: String,
	onCopyLog: () -> Unit,
	onShareLog: () -> Unit,
	onRestartApp: () -> Unit,
) {
	val ext = NemuriTheme.colors
	var isStackExpanded by rememberSaveable { mutableStateOf(true) }
	val exceptionHeadline = remember(reportText) {
		reportText.lineSequence()
			.firstOrNull { it.startsWith("Exception: ") }
			?.removePrefix("Exception: ")
			?.trim()
			.orEmpty()
			.ifBlank { "Uncaught runtime exception saved locally to filesDir/last_crash.txt" }
	}

	Surface(
		color = ext.background,
		modifier = Modifier.fillMaxSize(),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.statusBarsPadding()
				.navigationBarsPadding()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Surface(
					color = ext.danger.copy(alpha = 0.16f),
					shape = CircleShape,
					border = BorderStroke(1.dp, ext.danger.copy(alpha = 0.55f)),
					modifier = Modifier.size(52.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.BugReport,
							contentDescription = null,
							tint = ext.danger,
							modifier = Modifier.size(28.dp),
						)
					}
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = "Nemuri hit an unexpected error",
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.Bold,
						color = ext.textPrimary,
					)
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = "100% local crash log — nothing is sent anywhere unless you share it.",
						style = MaterialTheme.typography.bodySmall,
						color = ext.textMuted,
					)
				}
			}

			Surface(
				color = ext.card,
				shape = RoundedCornerShape(16.dp),
				border = BorderStroke(1.dp, ext.border),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { isStackExpanded = !isStackExpanded }
							.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
							Text(
								text = "CRASH DIAGNOSTIC LOG",
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Bold,
								color = ext.cyan,
							)
							Text(
								text = exceptionHeadline,
								style = MaterialTheme.typography.bodySmall,
								color = ext.textSecondary,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)
						}
						Icon(
							imageVector = if (isStackExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
							contentDescription = if (isStackExpanded) "Collapse stack trace" else "Expand stack trace",
							tint = ext.cyan,
						)
					}

					AnimatedVisibility(visible = isStackExpanded) {
						Surface(
							color = ext.surface,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(1.dp, ext.border.copy(alpha = 0.8f)),
							modifier = Modifier
								.fillMaxWidth()
								.heightIn(min = 140.dp, max = 340.dp),
						) {
							SelectionContainer {
								Box(
									modifier = Modifier
										.verticalScroll(rememberScrollState())
										.horizontalScroll(rememberScrollState())
										.padding(12.dp),
								) {
									Text(
										text = reportText,
										fontFamily = FontFamily.Monospace,
										fontSize = 11.sp,
										lineHeight = 15.5.sp,
										color = ext.textSecondary,
									)
								}
							}
						}
					}
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				OutlinedButton(
					onClick = onCopyLog,
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, ext.cyan.copy(alpha = 0.6f)),
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = Icons.Default.ContentCopy,
						contentDescription = null,
						tint = ext.cyan,
						modifier = Modifier.size(17.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text("Copy Crash Log", color = ext.cyan, maxLines = 1)
				}

				OutlinedButton(
					onClick = onShareLog,
					shape = RoundedCornerShape(14.dp),
					border = BorderStroke(1.dp, ext.border),
					modifier = Modifier.weight(1f),
				) {
					Icon(
						imageVector = Icons.Default.Share,
						contentDescription = null,
						tint = ext.textPrimary,
						modifier = Modifier.size(17.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text("Share Crash Log", color = ext.textPrimary, maxLines = 1)
				}
			}

			Button(
				onClick = onRestartApp,
				colors = ButtonDefaults.buttonColors(
					containerColor = ext.cyan,
					contentColor = ext.onCyan,
				),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(50.dp),
			) {
				Icon(
					imageVector = Icons.Default.Refresh,
					contentDescription = null,
					modifier = Modifier.size(19.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = "Restart Nemuri",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
				)
			}
		}
	}
}
