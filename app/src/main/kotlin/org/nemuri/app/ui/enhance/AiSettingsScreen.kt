package org.nemuri.app.ui.enhance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koitharu.kotatsu.ai.domain.AiMode
import org.koitharu.kotatsu.ai.domain.NanoAvailability
import org.nemuri.app.ui.settings.SettingsGroupCard
import org.nemuri.app.ui.settings.SettingsRowDivider
import org.nemuri.app.ui.settings.SettingsScaffold
import org.nemuri.app.ui.settings.SettingsToggleRow
import org.nemuri.app.ui.theme.NemuriTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
	onBack: () -> Unit,
	viewModel: AiSettingsViewModel = viewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	var remoteUrlDraft by remember { mutableStateOf(state.remoteUrl) }
	var remoteModelDraft by remember { mutableStateOf(state.remoteModel) }
	var openAiKeyDraft by remember { mutableStateOf("") }
	var openAiKeyEdited by remember { mutableStateOf(false) }
	var showOpenAiKey by remember { mutableStateOf(false) }

	var geminiModelDraft by remember { mutableStateOf(state.geminiModel) }
	var geminiKeyDraft by remember { mutableStateOf("") }
	var geminiKeyEdited by remember { mutableStateOf(false) }
	var showGeminiKey by remember { mutableStateOf(false) }

	LaunchedEffect(state.remoteUrl, state.remoteModel, state.geminiModel) {
		remoteUrlDraft = state.remoteUrl
		remoteModelDraft = state.remoteModel
		geminiModelDraft = state.geminiModel
	}

	SettingsScaffold(
		title = "AI Engine & Recaps",
		subtitle = "Pluggable on-device, LAN Ollama, or Cloud API",
		onBack = onBack,
	) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item {
				SettingsGroupCard("Routing Mode") {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Text(
							text = when (state.aiMode) {
								AiMode.OFF -> "AI calls and recap badges are disabled cleanly (Reading Trail remains local)."
								AiMode.AUTO -> "Priority: Gemini Nano (AICore) → Gemini API Key → Ollama / OpenAI → Local Reading Trail."
								AiMode.GEMINI_NANO -> "Pinned to on-device Gemini Nano via Android AICore."
								AiMode.REMOTE_OPENAI -> "Pinned to your configured Ollama, LM Studio, or OpenAI-compatible endpoint."
								AiMode.GEMINI_API -> "Pinned to Google Gemini REST API using your encrypted key."
							},
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							AiMode.entries.forEach { mode ->
								val selected = state.aiMode == mode
								Surface(
									color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(999.dp),
									border = BorderStroke(
										1.dp,
										if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
									),
									modifier = Modifier
										.clip(RoundedCornerShape(999.dp))
										.clickable { viewModel.setAiMode(mode) },
								) {
									Row(
										modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(4.dp),
									) {
										if (selected) {
											Icon(Icons.Default.Check, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(14.dp))
										}
										Text(
											text = mode.label,
											style = MaterialTheme.typography.labelMedium,
											color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
										)
									}
								}
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard("Gemini Nano (On-Device AICore)") {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = state.nanoAvailability.label,
									style = MaterialTheme.typography.titleSmall,
									color = when (state.nanoAvailability) {
										NanoAvailability.AVAILABLE -> NemuriTheme.colors.healthy
										NanoAvailability.DOWNLOADABLE, NanoAvailability.DOWNLOADING -> NemuriTheme.colors.cyan
										NanoAvailability.UNAVAILABLE -> NemuriTheme.colors.textMuted
									},
								)
								Text(
									text = if (state.isDownloadingNano) {
										"Downloading weights (${state.nanoDownloadedMb} MB)…"
									} else {
										"Requires supported AICore device (Pixel 8+/9+/S24+ etc.)"
									},
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
							IconButton(
								onClick = viewModel::refreshNanoStatus,
								enabled = !state.isCheckingNano && !state.isDownloadingNano,
							) {
								if (state.isCheckingNano) {
									CircularProgressIndicator(color = NemuriTheme.colors.cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
								} else {
									Icon(Icons.Default.Refresh, contentDescription = "Recheck AICore", tint = NemuriTheme.colors.cyan)
								}
							}
						}
						if (state.nanoAvailability == NanoAvailability.DOWNLOADABLE) {
							Button(
								onClick = viewModel::downloadNanoModel,
								enabled = !state.isDownloadingNano,
								colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.cyan, contentColor = NemuriTheme.colors.onCyan),
								shape = RoundedCornerShape(10.dp),
								modifier = Modifier.fillMaxWidth(),
							) {
								Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
								Spacer(Modifier.width(8.dp))
								Text("Download Gemini Nano Model")
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard("Ollama / OpenAI-Compatible Endpoint") {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.horizontalScroll(rememberScrollState()),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							listOf(
								Triple("Ollama Local", "http://127.0.0.1:11434/v1", "llama3.2"),
								Triple("LM Studio", "http://192.168.1.10:1234/v1", "local-model"),
								Triple("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-2.0-flash-001"),
								Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
							).forEach { (label, presetUrl, presetModel) ->
								Surface(
									color = NemuriTheme.colors.surfaceElevated,
									shape = RoundedCornerShape(999.dp),
									border = BorderStroke(1.dp, NemuriTheme.colors.border),
									modifier = Modifier
										.clip(RoundedCornerShape(999.dp))
										.clickable {
											remoteUrlDraft = presetUrl
											remoteModelDraft = presetModel
										},
								) {
									Text(
										text = label,
										style = MaterialTheme.typography.labelSmall,
										color = NemuriTheme.colors.cyan,
										modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
									)
								}
							}
						}
						OutlinedTextField(
							value = remoteUrlDraft,
							onValueChange = { remoteUrlDraft = it },
							label = { Text("Base URL (HTTP LAN/localhost supported)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
						)
						OutlinedTextField(
							value = remoteModelDraft,
							onValueChange = { remoteModelDraft = it },
							label = { Text("Model Name") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
						)
						OutlinedTextField(
							value = openAiKeyDraft,
							onValueChange = {
								openAiKeyDraft = it
								openAiKeyEdited = true
							},
							label = {
								Text(if (state.hasOpenAiKey) "Bearer Token (stored encrypted — leave blank to keep)" else "Bearer Token (optional for Ollama)")
							},
							singleLine = true,
							visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
							trailingIcon = {
								IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
									Icon(
										if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
										contentDescription = "Toggle token visibility",
									)
								}
							},
							modifier = Modifier.fillMaxWidth(),
						)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							if (state.hasOpenAiKey) {
								TextButton(onClick = {
									openAiKeyDraft = ""
									openAiKeyEdited = false
									viewModel.clearOpenAiApiKey()
								}) {
									Text("Clear Key", color = NemuriTheme.colors.danger)
								}
							}
							Spacer(Modifier.weight(1f))
							Button(
								onClick = {
									viewModel.saveRemoteConfig(
										url = remoteUrlDraft,
										model = remoteModelDraft,
										bearerTokenInput = if (openAiKeyEdited) openAiKeyDraft else null,
									)
									openAiKeyDraft = ""
									openAiKeyEdited = false
								},
								colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.tealContainer, contentColor = NemuriTheme.colors.cyan),
								shape = RoundedCornerShape(10.dp),
							) {
								Text("Save Endpoint")
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard("Google Gemini REST API (Encrypted)") {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						OutlinedTextField(
							value = geminiModelDraft,
							onValueChange = { geminiModelDraft = it },
							label = { Text("Gemini Model ID (e.g. gemini-2.5-flash)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth(),
						)
						OutlinedTextField(
							value = geminiKeyDraft,
							onValueChange = {
								geminiKeyDraft = it
								geminiKeyEdited = true
							},
							label = {
								Text(if (state.hasGeminiKey) "Gemini API Key (encrypted in Keystore)" else "Gemini API Key (AI Studio)")
							},
							singleLine = true,
							visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
							trailingIcon = {
								IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
									Icon(
										if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
										contentDescription = "Toggle key visibility",
									)
								}
							},
							modifier = Modifier.fillMaxWidth(),
						)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							if (state.hasGeminiKey) {
								TextButton(onClick = {
									geminiKeyDraft = ""
									geminiKeyEdited = false
									viewModel.clearGeminiApiKey()
								}) {
									Text("Clear Key", color = NemuriTheme.colors.danger)
								}
							}
							Spacer(Modifier.weight(1f))
							Button(
								onClick = {
									viewModel.saveGeminiConfig(
										model = geminiModelDraft,
										apiKeyInput = if (geminiKeyEdited) geminiKeyDraft else null,
									)
									geminiKeyDraft = ""
									geminiKeyEdited = false
								},
								colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.tealContainer, contentColor = NemuriTheme.colors.cyan),
								shape = RoundedCornerShape(10.dp),
							) {
								Text("Save Key")
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard("Connection Diagnostics") {
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Button(
							onClick = viewModel::testActiveBackend,
							enabled = !state.isTestingConnection,
							colors = ButtonDefaults.buttonColors(containerColor = NemuriTheme.colors.cyan, contentColor = NemuriTheme.colors.onCyan),
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							if (state.isTestingConnection) {
								CircularProgressIndicator(color = NemuriTheme.colors.onCyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
								Spacer(Modifier.width(8.dp))
								Text("Testing (up to 90s cold start)…")
							} else {
								Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
								Spacer(Modifier.width(8.dp))
								Text("Test Active AI Backend")
							}
						}
						state.testResult?.let { res ->
							Surface(
								color = if (res.ok) NemuriTheme.colors.healthy.copy(alpha = 0.14f) else NemuriTheme.colors.danger.copy(alpha = 0.14f),
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (res.ok) NemuriTheme.colors.healthy.copy(alpha = 0.5f) else NemuriTheme.colors.danger.copy(alpha = 0.5f)),
								modifier = Modifier.fillMaxWidth(),
							) {
								Row(
									modifier = Modifier.padding(10.dp),
									horizontalArrangement = Arrangement.spacedBy(8.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									Icon(
										imageVector = if (res.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
										contentDescription = null,
										tint = if (res.ok) NemuriTheme.colors.healthy else NemuriTheme.colors.danger,
										modifier = Modifier.size(18.dp),
									)
									Column(modifier = Modifier.weight(1f)) {
										Text(res.engineName, style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textPrimary)
										Text(res.message, style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textSecondary)
									}
								}
							}
						}
					}
				}
			}

			item {
				SettingsGroupCard("Where Was I? & Privacy") {
					SettingsToggleRow(
						title = "Show Recap Card on Details",
						subtitle = "Strictly spoiler-quarantined up to your current read chapter",
						checked = state.showRecapCard,
						onCheckedChange = viewModel::setShowRecapCard,
					)
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Include Read Novel Excerpt",
						subtitle = "Pass a short snippet from your last read chapter for richer novel recaps",
						checked = state.includeNovelExcerpt,
						onCheckedChange = viewModel::setIncludeNovelExcerpt,
					)
					SettingsRowDivider()
					OutlinedButton(
						onClick = viewModel::clearAllCachedRecaps,
						border = BorderStroke(1.dp, NemuriTheme.colors.border),
						modifier = Modifier
							.fillMaxWidth()
							.padding(12.dp),
					) {
						Icon(Icons.Default.ClearAll, contentDescription = null, tint = NemuriTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
						Spacer(Modifier.width(8.dp))
						Text("Clear Cached Recaps", color = NemuriTheme.colors.textSecondary)
					}
				}
			}
		}
	}
}
