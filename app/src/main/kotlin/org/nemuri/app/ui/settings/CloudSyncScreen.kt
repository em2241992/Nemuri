package org.nemuri.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.koitharu.kotatsu.sync.SyncFeature
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.ui.theme.NemuriTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSyncScreen(
	onOpenLocalBackups: () -> Unit,
	onBack: () -> Unit,
	viewModel: CloudSyncViewModel = viewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showIntervalDialog by remember { mutableStateOf(false) }
	var showSignOutConfirm by remember { mutableStateOf(false) }
	var showDeleteCloudConfirm by remember { mutableStateOf(false) }

	val signInLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult(),
	) { result ->
		viewModel.handleSignInResult(result.resultCode, result.data)
	}

	val snap = state.snapshot
	val activeSync = state.isSyncing || state.isBusy

	SettingsLazyPage(
		title = SettingsSection.CLOUD_SYNC.title,
		onBack = onBack,
	) {
		if (!SyncFeature.IS_AVAILABLE) {
			item {
				SettingsGroupCard("Unavailable in F-Droid Build") {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(18.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.CloudOff,
							contentDescription = null,
							tint = NemuriTheme.colors.amber,
							modifier = Modifier.size(32.dp),
						)
						Text(
							text = "Google Drive Sync requires Google Play Services",
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "This F-Droid build excludes proprietary Play Services SDKs. Use the GitHub release build for Google Drive sync, or back up locally with .bk.zip / .tachibk archives.",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
						Button(
							onClick = onOpenLocalBackups,
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
							shape = RoundedCornerShape(12.dp),
						) {
							Text("Open Local Backup & Restore")
						}
					}
				}
			}
			return@SettingsLazyPage
		}

		if (!snap.isSignedIn) {
			item {
				SettingsGroupCard("Connect Google Drive") {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(18.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Icon(
							imageVector = Icons.Default.CloudDone,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(34.dp),
						)
						Text(
							text = "Two-Way App Data Sync",
							style = MaterialTheme.typography.titleLarge,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Keep your favourites, reading history, bookmarks, feed, tracking links, stats, settings, and custom covers synchronized in your private Google Drive appDataFolder.",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
						Button(
							onClick = { viewModel.beginSignIn(signInLauncher::launch) },
							enabled = !activeSync,
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							if (activeSync) {
								CircularProgressIndicator(
									modifier = Modifier.size(18.dp),
									strokeWidth = 2.dp,
									color = NemuriTheme.colors.onCyan,
								)
								Spacer(Modifier.width(8.dp))
							}
							Text("Sign in with Google Drive")
						}
					}
				}
			}
			return@SettingsLazyPage
		}

		item {
			SettingsGroupCard("Account") {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (!snap.accountPhotoUrl.isNullOrBlank()) {
						AsyncImage(
							model = snap.accountPhotoUrl,
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.size(44.dp)
								.clip(CircleShape),
						)
					} else {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = CircleShape,
							modifier = Modifier.size(44.dp),
						) {
							Icon(
								imageVector = Icons.Default.CloudDone,
								contentDescription = null,
								tint = NemuriTheme.colors.cyan,
								modifier = Modifier.padding(10.dp),
							)
						}
					}
					Spacer(Modifier.width(12.dp))
					Column(
						modifier = Modifier
							.weight(1f)
							.then(if (snap.isEmailHidden) Modifier.blur(6.dp) else Modifier),
						verticalArrangement = Arrangement.spacedBy(2.dp),
					) {
						Text(
							text = snap.accountName ?: "Google Account",
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = snap.accountEmail.orEmpty(),
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					OutlinedButton(
						onClick = { showSignOutConfirm = true },
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
					) {
						Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = NemuriTheme.colors.textSecondary)
						Spacer(Modifier.width(4.dp))
						Text("Sign out", color = NemuriTheme.colors.textSecondary)
					}
				}
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Blur Account Details",
					subtitle = "Hide name and email on this screen for screenshots or streams",
					checked = snap.isEmailHidden,
					onCheckedChange = viewModel::setEmailHidden,
				)
			}
		}

		item {
			SettingsGroupCard("Sync Status") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					val lastText = if (snap.lastSyncTimestamp > 0L) {
						"Last synced: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snap.lastSyncTimestamp))}"
					} else {
						"Never synced yet"
					}
					Text(
						text = lastText,
						style = MaterialTheme.typography.bodyMedium,
						color = NemuriTheme.colors.textSecondary,
					)
					if (!snap.lastSyncError.isNullOrBlank()) {
						Surface(
							color = NemuriTheme.colors.danger.copy(alpha = 0.14f),
							shape = RoundedCornerShape(10.dp),
							border = BorderStroke(1.dp, NemuriTheme.colors.danger.copy(alpha = 0.4f)),
							modifier = Modifier.fillMaxWidth(),
						) {
							Text(
								text = snap.lastSyncError.orEmpty(),
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.danger,
								modifier = Modifier.padding(10.dp),
							)
						}
					}
					Button(
						onClick = viewModel::syncNow,
						enabled = !activeSync,
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.cyan,
							contentColor = NemuriTheme.colors.onCyan,
						),
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						if (activeSync) {
							CircularProgressIndicator(
								modifier = Modifier.size(18.dp),
								strokeWidth = 2.dp,
								color = NemuriTheme.colors.onCyan,
							)
							Spacer(Modifier.width(8.dp))
							Text("Syncing…")
						} else {
							Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
							Spacer(Modifier.width(8.dp))
							Text("Sync Now")
						}
					}
				}
			}
		}

		item {
			SettingsGroupCard("Schedule") {
				SettingsChoiceRow(
					title = "Background Sync Interval",
					currentLabel = syncIntervalLabel(snap.intervalMinutes),
					onClick = { showIntervalDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Sync on Wi-Fi Only",
					subtitle = "Wait for an unmetered connection before syncing replicas",
					checked = snap.isWifiOnly,
					onCheckedChange = viewModel::setWifiOnly,
				)
			}
		}

		item {
			SettingsGroupCard("Synced Data") {
				val contentEntries = listOf(
					"favourites" to "Favourites & Categories",
					"history" to "Reading History & Progress",
					"bookmarks" to "Bookmarks",
					"feed" to "Feed Logs & Tracking State",
					"tracking" to "External Tracker Links",
					"stats" to "Reading Statistics",
					"settings" to "App Settings",
					"custom_covers" to "Custom Covers & Manga Preferences",
				)
				contentEntries.forEachIndexed { index, (key, title) ->
					if (index > 0) SettingsRowDivider()
					SettingsToggleRow(
						title = title,
						checked = key in snap.enabledContent,
						onCheckedChange = { viewModel.toggleContent(key) },
					)
				}
			}
		}

		item {
			SettingsGroupCard("Danger Zone") {
				SettingsActionRow(
					icon = Icons.Default.DeleteForever,
					title = "Delete Cloud Sync Data",
					subtitle = "Permanently remove all synced Nemuri replicas from Google Drive",
					tint = NemuriTheme.colors.danger,
					enabled = !activeSync,
					onClick = { showDeleteCloudConfirm = true },
				)
			}
		}
	}

	if (showIntervalDialog) {
		SettingsOptionDialog(
			title = "Background Sync Interval",
			options = listOf(
				0 to "Off (manual & foreground only)",
				60 to "Every 1 hour",
				180 to "Every 3 hours",
				360 to "Every 6 hours (Default)",
				720 to "Every 12 hours",
				1440 to "Daily",
			),
			selected = snap.intervalMinutes,
			onSelect = viewModel::setIntervalMinutes,
			onDismiss = { showIntervalDialog = false },
		)
	}

	if (showSignOutConfirm) {
		AlertDialog(
			onDismissRequest = { showSignOutConfirm = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Sign Out of Google Drive?") },
			text = { Text("Local data remains untouched. Background sync will pause until you sign in again.", color = NemuriTheme.colors.textMuted) },
			confirmButton = {
				Button(
					onClick = {
						showSignOutConfirm = false
						viewModel.signOut()
					},
					colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.amber),
				) { Text("Sign out") }
			},
			dismissButton = {
				TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel", color = NemuriTheme.colors.textMuted) }
			},
		)
	}

	if (showDeleteCloudConfirm) {
		AlertDialog(
			onDismissRequest = { showDeleteCloudConfirm = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Delete Remote Cloud Data?") },
			text = { Text("This removes your synced replicas from Google Drive across all devices. Local data on this phone will stay intact.", color = NemuriTheme.colors.textMuted) },
			confirmButton = {
				Button(
					onClick = {
						showDeleteCloudConfirm = false
						viewModel.deleteRemoteData()
					},
					colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.danger),
				) { Text("Delete Remote Data") }
			},
			dismissButton = {
				TextButton(onClick = { showDeleteCloudConfirm = false }) { Text("Cancel", color = NemuriTheme.colors.textMuted) }
			},
		)
	}
}

private fun syncIntervalLabel(minutes: Int): String = when (minutes) {
	0 -> "Off"
	60 -> "1 hour"
	180 -> "3 hours"
	360 -> "6 hours"
	720 -> "12 hours"
	1440 -> "Daily"
	else -> "$minutes min"
}
