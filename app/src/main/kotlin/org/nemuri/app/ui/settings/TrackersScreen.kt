package org.nemuri.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.ui.components.NemuriLoadingState
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun TrackersScreen(
	onBack: () -> Unit,
	viewModel: TrackersViewModel = viewModel(),
) {
	val context = LocalContext.current
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showKitsuDialog by remember { mutableStateOf(false) }
	var showDiscordTokenDialog by remember { mutableStateOf(false) }

	SettingsLazyPage(
		title = SettingsSection.TRACKERS.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Reading Trackers") {
				if (state.isLoading) {
					NemuriLoadingState(message = "Loading tracker services…")
				} else {
					state.trackers.forEachIndexed { index, item ->
						if (index > 0) SettingsRowDivider()
						TrackerServiceRow(
							item = item,
							onConnect = {
								if (item.service == ScrobblerService.KITSU) {
									showKitsuDialog = true
								} else {
									val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.oauthUrl))
									runCatching { context.startActivity(intent) }.onFailure {
										viewModel.toastManager.show("No browser available for login")
									}
								}
							},
							onLogout = { viewModel.logout(item.service) },
						)
					}
				}
			}
		}

		item {
			SettingsGroupCard("Discord Rich Presence") {
				SettingsToggleRow(
					title = "Enable Discord Rich Presence",
					subtitle = "Show your currently active manga or novel on your Discord profile",
					checked = state.isDiscordRpcEnabled,
					onCheckedChange = viewModel::setDiscordRpcEnabled,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Hide 18+ Titles from RPC",
					subtitle = "Do not broadcast adult titles to Discord",
					checked = state.isDiscordRpcSkipNsfw,
					enabled = state.isDiscordRpcEnabled,
					onCheckedChange = viewModel::setDiscordRpcSkipNsfw,
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					icon = Icons.Default.Key,
					title = "Discord User Token",
					subtitle = if (state.hasDiscordToken) "Token configured on device" else "Required to send Rich Presence updates",
					currentLabel = if (state.hasDiscordToken) "Set" else "Not set",
					onClick = { showDiscordTokenDialog = true },
				)
			}
		}
	}

	if (showKitsuDialog) {
		KitsuLoginDialog(
			onLogin = { email, password ->
				showKitsuDialog = false
				viewModel.authorizeKitsu(email, password)
			},
			onDismiss = { showKitsuDialog = false },
		)
	}

	if (showDiscordTokenDialog) {
		DiscordTokenDialog(
			initialToken = state.discordToken,
			onSave = { token ->
				viewModel.setDiscordToken(token)
				showDiscordTokenDialog = false
			},
			onClear = {
				viewModel.setDiscordToken(null)
				showDiscordTokenDialog = false
			},
			onDismiss = { showDiscordTokenDialog = false },
		)
	}
}

@Composable
private fun TrackerServiceRow(
	item: TrackerAccountItem,
	onConnect: () -> Unit,
	onLogout: () -> Unit,
) {
	val serviceTitle = stringResource(item.service.titleResId)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Surface(
			color = NemuriTheme.colors.surfaceElevated,
			shape = RoundedCornerShape(10.dp),
			border = BorderStroke(1.dp, NemuriTheme.colors.border),
			modifier = Modifier.size(40.dp),
		) {
			if (!item.userAvatarUrl.isNullOrBlank()) {
				AsyncImage(
					model = item.userAvatarUrl,
					contentDescription = serviceTitle,
					modifier = Modifier
						.size(40.dp)
						.clip(RoundedCornerShape(10.dp)),
				)
			} else if (item.service.isBitmapIcon) {
				Image(
					painter = painterResource(item.service.iconResId),
					contentDescription = serviceTitle,
					modifier = Modifier.padding(8.dp),
				)
			} else {
				Icon(
					painter = painterResource(item.service.iconResId),
					contentDescription = serviceTitle,
					tint = if (item.isAuthorized) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
					modifier = Modifier.padding(8.dp),
				)
			}
		}
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = serviceTitle,
				style = MaterialTheme.typography.titleMedium,
				color = NemuriTheme.colors.textPrimary,
			)
			Text(
				text = if (item.isAuthorized) {
					"Connected" + (item.userNickname?.let { " as $it" } ?: "")
				} else {
					"Not connected"
				},
				style = MaterialTheme.typography.bodySmall,
				color = if (item.isAuthorized) NemuriTheme.colors.healthy else NemuriTheme.colors.textMuted,
			)
		}
		Spacer(Modifier.width(8.dp))
		if (item.isBusy) {
			CircularProgressIndicator(
				modifier = Modifier.size(22.dp),
				strokeWidth = 2.dp,
				color = NemuriTheme.colors.cyan,
			)
		} else if (item.isAuthorized) {
			OutlinedButton(
				onClick = onLogout,
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
			) {
				Text("Disconnect", color = NemuriTheme.colors.danger)
			}
		} else {
			Button(
				onClick = onConnect,
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.tealContainer,
					contentColor = NemuriTheme.colors.cyan,
				),
				shape = RoundedCornerShape(10.dp),
			) {
				Text("Connect")
			}
		}
	}
}

@Composable
private fun KitsuLoginDialog(
	onLogin: (String, String) -> Unit,
	onDismiss: () -> Unit,
) {
	var email by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }

	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text("Sign in to Kitsu") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				OutlinedTextField(
					value = email,
					onValueChange = { email = it },
					label = { Text("Email") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
					modifier = Modifier.fillMaxWidth(),
				)
				OutlinedTextField(
					value = password,
					onValueChange = { password = it },
					label = { Text("Password") },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier.fillMaxWidth(),
				)
			}
		},
		confirmButton = {
			Button(
				onClick = { onLogin(email, password) },
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
			) { Text("Sign in") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel", color = NemuriTheme.colors.textMuted)
			}
		},
	)
}

@Composable
private fun DiscordTokenDialog(
	initialToken: String,
	onSave: (String) -> Unit,
	onClear: () -> Unit,
	onDismiss: () -> Unit,
) {
	var token by remember { mutableStateOf(initialToken) }

	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text("Discord Token") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					text = "Stored locally on this device and excluded from cloud sync and backups.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
				OutlinedTextField(
					value = token,
					onValueChange = { token = it },
					label = { Text("Token") },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier.fillMaxWidth(),
				)
			}
		},
		confirmButton = {
			Button(
				onClick = { onSave(token) },
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
			) { Text("Save") }
		},
		dismissButton = {
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				if (initialToken.isNotBlank()) {
					TextButton(onClick = onClear) {
						Text("Clear", color = NemuriTheme.colors.danger)
					}
				}
				TextButton(onClick = onDismiss) {
					Text("Cancel", color = NemuriTheme.colors.textMuted)
				}
			}
		},
	)
}
