package org.nemuri.app.security

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.nemuri.app.ui.components.rememberAppSettings
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun AppLockShieldOverlay(
	isLocked: Boolean,
	onUnlocked: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (!isLocked) return

	val context = LocalContext.current
	val settings = rememberAppSettings()
	val activity = context as? FragmentActivity
	val lifecycleOwner = LocalLifecycleOwner.current
	val ext = NemuriTheme.colors

	var authError by remember { mutableStateOf<String?>(null) }
	var pinInput by rememberSaveable { mutableStateOf("") }
	var autoPromptFired by remember { mutableStateOf(false) }

	val hasCustomPin = remember(settings) { settings.isAppPasswordSet }
	val canUseBiometricOrDevice = remember(context) {
		AppLockManager.from(context).canAuthenticateDevice(context)
	}

	fun triggerSystemPrompt() {
		val host = activity ?: return
		if (!canUseBiometricOrDevice) {
			if (!hasCustomPin) {
				// Device lock screen was removed by user in OS settings; unlock gracefully rather than bricking access
				onUnlocked()
			}
			return
		}
		runCatching {
			val executor = ContextCompat.getMainExecutor(host)
			val prompt = BiometricPrompt(
				host,
				executor,
				object : BiometricPrompt.AuthenticationCallback() {
					override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
						authError = null
						pinInput = ""
						onUnlocked()
					}

					override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
						if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
							errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
							errorCode != BiometricPrompt.ERROR_CANCELED
						) {
							authError = errString.toString()
						}
					}

					override fun onAuthenticationFailed() {
						authError = "Authentication not recognized. Try again."
					}
				},
			)
			val promptInfo = BiometricPrompt.PromptInfo.Builder()
				.setTitle("Unlock Nemuri")
				.setSubtitle("Verify your identity to view your library and reader")
				.setAllowedAuthenticators(AppLockManager.ALLOWED_AUTHENTICATORS)
				.build()
			prompt.authenticate(promptInfo)
		}.onFailure { err ->
			authError = err.message ?: "Unable to open biometric prompt"
		}
	}

	BackHandler(enabled = true) {
		activity?.moveTaskToBack(true)
	}

	DisposableEffect(lifecycleOwner, isLocked) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME && !autoPromptFired && canUseBiometricOrDevice) {
				autoPromptFired = true
				triggerSystemPrompt()
			} else if (event == Lifecycle.Event.ON_STOP) {
				autoPromptFired = false
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	LaunchedEffect(isLocked) {
		if (isLocked && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !autoPromptFired && canUseBiometricOrDevice) {
			autoPromptFired = true
			triggerSystemPrompt()
		}
	}

	Surface(
		color = ext.background,
		modifier = modifier
			.fillMaxSize()
			.zIndex(999f)
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						val event = awaitPointerEvent()
						event.changes.forEach { it.consume() }
					}
				}
			},
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.statusBarsPadding()
				.navigationBarsPadding()
				.padding(24.dp),
			contentAlignment = Alignment.Center,
		) {
			Surface(
				color = ext.card,
				shape = RoundedCornerShape(24.dp),
				border = BorderStroke(1.dp, ext.border),
				modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
			) {
				Column(
					modifier = Modifier.padding(24.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					Surface(
						color = ext.tealContainer,
						shape = CircleShape,
						border = BorderStroke(1.dp, ext.cyan.copy(alpha = 0.6f)),
						modifier = Modifier.size(64.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Lock,
								contentDescription = null,
								tint = ext.cyan,
								modifier = Modifier.size(30.dp),
							)
						}
					}

					Text(
						text = "Nemuri is Locked",
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.Bold,
						color = ext.textPrimary,
					)
					Text(
						text = "Authenticate with fingerprint, face unlock, or your device PIN to continue reading.",
						style = MaterialTheme.typography.bodySmall,
						color = ext.textMuted,
						textAlign = TextAlign.Center,
					)

					if (hasCustomPin) {
						val submitPin = {
							if (settings.verifyAppPassword(pinInput.trim())) {
								authError = null
								pinInput = ""
								onUnlocked()
							} else {
								authError = "Incorrect PIN"
							}
						}
						OutlinedTextField(
							value = pinInput,
							onValueChange = {
								pinInput = it
								authError = null
							},
							label = { Text("App PIN") },
							singleLine = true,
							visualTransformation = PasswordVisualTransformation(),
							keyboardOptions = KeyboardOptions(
								keyboardType = KeyboardType.NumberPassword,
								imeAction = ImeAction.Done,
							),
							keyboardActions = KeyboardActions(onDone = { submitPin() }),
							modifier = Modifier.fillMaxWidth(),
						)
						Button(
							onClick = submitPin,
							colors = ButtonDefaults.buttonColors(
								containerColor = ext.surfaceElevated,
								contentColor = ext.textPrimary,
							),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Text("Unlock with App PIN")
						}
					}

					if (!authError.isNullOrBlank()) {
						Text(
							text = authError!!,
							style = MaterialTheme.typography.labelSmall,
							color = ext.danger,
							textAlign = TextAlign.Center,
						)
					}

					if (canUseBiometricOrDevice) {
						Button(
							onClick = { triggerSystemPrompt() },
							colors = ButtonDefaults.buttonColors(
								containerColor = ext.cyan,
								contentColor = ext.onCyan,
							),
							shape = RoundedCornerShape(14.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(48.dp),
						) {
							Icon(
								imageVector = Icons.Default.Fingerprint,
								contentDescription = null,
								modifier = Modifier.size(20.dp),
							)
							Spacer(modifier = Modifier.width(8.dp))
							Text("Unlock with Biometrics / PIN", fontWeight = FontWeight.Bold)
						}
					}
				}
			}
		}
	}
}
