package org.koitharu.kotatsu.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.ai.AiBackendTier
import org.koitharu.kotatsu.core.ai.AiEngineConfig
import org.koitharu.kotatsu.core.ai.CustomEndpointSubTab
import org.koitharu.kotatsu.core.ai.GeminiCloudModel
import org.koitharu.kotatsu.core.ai.OllamaEndpointPreset
import org.koitharu.kotatsu.ui.theme.JetBrainsMonoFamily
import org.koitharu.kotatsu.ui.theme.PlusJakartaDisplayFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEngineSettingsModalSheet(
	config: AiEngineConfig,
	isAiCoreHardwareAvailable: Boolean,
	onUpdateConfig: ((AiEngineConfig) -> AiEngineConfig) -> Unit,
	onTestConnection: () -> Unit,
	onDownloadLiteRtModel: () -> Unit,
	onDismiss: () -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = Color(0xFF12151E),
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(top = 10.dp, bottom = 6.dp)
					.width(36.dp)
					.height(4.dp)
					.clip(CircleShape)
					.background(Color.White.copy(alpha = 0.28f)),
			)
		},
	) {
		AiEngineSettingsContent(
			config = config,
			isAiCoreHardwareAvailable = isAiCoreHardwareAvailable,
			onUpdateConfig = onUpdateConfig,
			onTestConnection = onTestConnection,
			onDownloadLiteRtModel = onDownloadLiteRtModel,
			onClose = onDismiss,
			modifier = Modifier
				.fillMaxWidth()
				.navigationBarsPadding(),
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiEngineSettingsContent(
	config: AiEngineConfig,
	isAiCoreHardwareAvailable: Boolean,
	onUpdateConfig: ((AiEngineConfig) -> AiEngineConfig) -> Unit,
	onTestConnection: () -> Unit,
	onDownloadLiteRtModel: () -> Unit,
	onClose: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val clipboardManager = LocalClipboardManager.current
	var showGeminiKey by rememberSaveable { mutableStateOf(false) }
	var showOpenAiKey by rememberSaveable { mutableStateOf(false) }

	val liteRtFileLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "gemma-3-1b-it-int4.litertlm"
			onUpdateConfig {
				it.copy(
					selectedTier = AiBackendTier.LOCAL_LITERT_FILE,
					liteRtModelFileName = fileName,
					liteRtModelDownloaded = true,
				)
			}
		}
	}

	Column(
		modifier = modifier
			.verticalScroll(rememberScrollState())
			.navigationBarsPadding()
			.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 40.dp)
			.testTag("ai_engine_settings_sheet"),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		// Sheet Header
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.weight(1f),
			) {
				Surface(
					color = accentCyan.copy(alpha = 0.16f),
					shape = CircleShape,
					modifier = Modifier.size(38.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.AutoAwesome,
							contentDescription = null,
							tint = accentCyan,
							modifier = Modifier.size(20.dp),
						)
					}
				}
				Column {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = "AI Engine & Router",
							style = MaterialTheme.typography.titleLarge,
							fontFamily = PlusJakartaDisplayFamily,
							fontWeight = FontWeight.Bold,
							color = Color.White,
						)
						Surface(
							color = accentCyan.copy(alpha = 0.18f),
							shape = RoundedCornerShape(50),
							modifier = Modifier.testTag("ai_engine_active_badge_pill"),
						) {
							Text(
								text = config.activeAttributionBadge(),
								fontFamily = JetBrainsMonoFamily,
								fontWeight = FontWeight.Bold,
								fontSize = 10.sp,
								color = accentCyan,
								modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
							)
						}
					}
					Text(
						text = "Pluggable 4-Tier LLM Backend • Zero Cloud Lock-In",
						style = MaterialTheme.typography.bodySmall,
						color = Color(0xFF9EA4B0),
					)
				}
			}

			if (onClose != null) {
				IconButton(
					onClick = onClose,
					modifier = Modifier
						.size(36.dp)
						.testTag("ai_engine_settings_close_button"),
				) {
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = "Close AI Engine Settings",
						tint = Color(0xFFCBD5E1),
					)
				}
			}
		}

		// 1. Master Generative AI Toggle (`Enable Generative AI Features: ON / OFF`)
		Surface(
			color = Color(0xFF1A1E2B),
			shape = RoundedCornerShape(18.dp),
			border = BorderStroke(
				1.dp,
				if (config.masterAiEnabled) accentCyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
			),
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 14.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(
					modifier = Modifier
						.weight(1f)
						.padding(end = 12.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Text(
						text = "Enable Generative AI Features: ${if (config.masterAiEnabled) "ON" else "OFF"}",
						style = MaterialTheme.typography.titleSmall,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = Color.White,
					)
					Text(
						text = if (config.masterAiEnabled) {
							"Active: '✦ Where Was I?' story recaps on Manga Details & LLM natural-language search expansion."
						} else {
							"Generative LLM entry points hidden. Deterministic tools in Enhance (Duplicates, Repair, and Upscaler) remain 100% active."
						},
						style = MaterialTheme.typography.bodySmall,
						color = Color(0xFF9EA4B0),
						fontSize = 11.sp,
					)
				}
				Switch(
					checked = config.masterAiEnabled,
					onCheckedChange = { enabled ->
						onUpdateConfig { it.copy(masterAiEnabled = enabled) }
					},
					modifier = Modifier.testTag("master_generative_ai_toggle"),
				)
			}
		}

		// 2. 4-Tier Pluggable Backend Selector (Active when Master Toggle is ON)
		AnimatedVisibility(visible = config.masterAiEnabled) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text = "SELECT AI BACKEND TIER",
					fontFamily = JetBrainsMonoFamily,
					fontWeight = FontWeight.Bold,
					fontSize = 11.sp,
					color = accentCyan,
				)

				// TIER 1: On-Device Gemini Nano (Android AICore)
				TierOptionCard(
					tier = AiBackendTier.GEMINI_NANO_AICORE,
					selected = config.selectedTier == AiBackendTier.GEMINI_NANO_AICORE,
					onSelect = {
						onUpdateConfig { it.copy(selectedTier = AiBackendTier.GEMINI_NANO_AICORE) }
					},
					testTag = "ai_tier_1_gemini_nano",
				) {
					val pillText = config.hardwareStatusPillText(isAiCoreHardwareAvailable)
					Surface(
						color = if (isAiCoreHardwareAvailable) {
							Color(0xFF10B981).copy(alpha = 0.16f)
						} else {
							Color.White.copy(alpha = 0.08f)
						},
						shape = RoundedCornerShape(50),
						modifier = Modifier
							.padding(top = 6.dp)
							.testTag("ai_tier_1_hardware_status_pill"),
					) {
						Text(
							text = pillText,
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.SemiBold,
							fontSize = 10.sp,
							color = if (isAiCoreHardwareAvailable) Color(0xFF34D399) else Color(0xFFCBD5E1),
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
						)
					}
				}

				// TIER 2: API Key / Custom Endpoint (Gemini, Ollama, Llama, OpenAI)
				TierOptionCard(
					tier = AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT,
					selected = config.selectedTier == AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT,
					onSelect = {
						onUpdateConfig { it.copy(selectedTier = AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT) }
					},
					testTag = "ai_tier_2_custom_endpoint",
				) {
					AnimatedVisibility(visible = config.selectedTier == AiBackendTier.CLOUD_OR_CUSTOM_ENDPOINT) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 10.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp),
						) {
							// 2-Pill Provider Sub-Selector at the top
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(12.dp))
									.background(Color(0xFF11141D))
									.padding(4.dp),
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								CustomEndpointSubTab.entries.forEach { subTab ->
									val isSubSelected = config.activeSubTab == subTab
									Surface(
										onClick = {
											onUpdateConfig {
												it.copy(
													activeSubTab = subTab,
													connectionVerified = false,
													lastConnectionStatusMessage = null,
												)
											}
										},
										color = if (isSubSelected) accentCyan else Color.Transparent,
										shape = RoundedCornerShape(9.dp),
										modifier = Modifier
											.weight(1f)
											.testTag("ai_subtab_${subTab.name.lowercase()}"),
									) {
										Text(
											text = subTab.label,
											fontSize = 11.sp,
											fontWeight = FontWeight.Bold,
											color = if (isSubSelected) Color(0xFF0B0E14) else Color(0xFFCBD5E1),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
										)
									}
								}
							}

							when (config.activeSubTab) {
								CustomEndpointSubTab.GOOGLE_GEMINI_API -> {
									// Sub-Tab A: Google Gemini API (Default)
									Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
										Text(
											text = "Google Gemini API Key",
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.Bold,
											color = Color.White,
										)
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.spacedBy(8.dp),
											verticalAlignment = Alignment.CenterVertically,
										) {
											OutlinedTextField(
												value = config.geminiApiKey,
												onValueChange = { key ->
													onUpdateConfig {
														it.copy(
															geminiApiKey = key,
															connectionVerified = false,
														)
													}
												},
												placeholder = {
													Text("AIzaSy...", fontSize = 12.sp, color = Color(0xFF64748B))
												},
												singleLine = true,
												visualTransformation = if (showGeminiKey) {
													VisualTransformation.None
												} else {
													PasswordVisualTransformation()
												},
												trailingIcon = {
													IconButton(
														onClick = { showGeminiKey = !showGeminiKey },
														modifier = Modifier.testTag("toggle_gemini_key_visibility"),
													) {
														Icon(
															imageVector = if (showGeminiKey) {
																Icons.Default.VisibilityOff
															} else {
																Icons.Default.Visibility
															},
															contentDescription = if (showGeminiKey) "Hide API Key" else "Show API Key",
														)
													}
												},
												modifier = Modifier
													.weight(1f)
													.testTag("gemini_api_key_input"),
											)

											FilledTonalButton(
												onClick = {
													val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
													if (clipText.isNotEmpty()) {
														onUpdateConfig {
															it.copy(
																geminiApiKey = clipText,
																connectionVerified = false,
															)
														}
													}
												},
												contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
												modifier = Modifier.testTag("paste_gemini_api_key_button"),
											) {
												Icon(
													imageVector = Icons.Default.ContentPaste,
													contentDescription = "Paste API Key",
													modifier = Modifier.size(16.dp),
												)
												Spacer(modifier = Modifier.width(4.dp))
												Text("Paste", fontSize = 12.sp)
											}
										}

										Text(
											text = "Model Picker",
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.Bold,
											color = Color.White,
										)
										FlowRow(
											horizontalArrangement = Arrangement.spacedBy(8.dp),
											verticalArrangement = Arrangement.spacedBy(8.dp),
										) {
											GeminiCloudModel.entries.forEach { modelOpt ->
												val isModelSelected = config.geminiModel == modelOpt.modelId
												FilterChip(
													selected = isModelSelected,
													onClick = {
														onUpdateConfig {
															it.copy(
																geminiModel = modelOpt.modelId,
																connectionVerified = false,
															)
														}
													},
													label = {
														Text(modelOpt.displayChip, fontSize = 11.sp)
													},
													modifier = Modifier.testTag("gemini_model_chip_${modelOpt.modelId}"),
												)
											}
										}
									}
								}

								CustomEndpointSubTab.OLLAMA_OPENAI_COMPATIBLE -> {
									// Sub-Tab B: Ollama / Llama / OpenAI-Compatible
									Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
										Text(
											text = "Quick Preset Chips",
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.Bold,
											color = Color.White,
										)
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.horizontalScroll(rememberScrollState()),
											horizontalArrangement = Arrangement.spacedBy(8.dp),
										) {
											OllamaEndpointPreset.entries.forEach { preset ->
												val isPresetActive = config.openAiBaseUrl == preset.baseUrl &&
													config.openAiModelId == preset.defaultModelId
												FilterChip(
													selected = isPresetActive,
													onClick = {
														onUpdateConfig {
															it.copy(
																openAiBaseUrl = preset.baseUrl,
																openAiModelId = preset.defaultModelId,
																connectionVerified = false,
																lastConnectionStatusMessage = null,
															)
														}
													},
													label = { Text(preset.label, fontSize = 11.sp) },
													modifier = Modifier.testTag("ollama_preset_${preset.name.lowercase()}"),
												)
											}
										}

										OutlinedTextField(
											value = config.openAiBaseUrl,
											onValueChange = { url ->
												onUpdateConfig {
													it.copy(
														openAiBaseUrl = url,
														connectionVerified = false,
													)
												}
											},
											label = { Text("Base URL (.../v1)", fontSize = 11.sp) },
											placeholder = { Text("http://127.0.0.1:11434/v1", fontSize = 12.sp) },
											singleLine = true,
											modifier = Modifier
												.fillMaxWidth()
												.testTag("ollama_base_url_input"),
										)

										OutlinedTextField(
											value = config.openAiModelId,
											onValueChange = { model ->
												onUpdateConfig {
													it.copy(
														openAiModelId = model,
														connectionVerified = false,
													)
												}
											},
											label = { Text("Model ID (llama3.2, gemma3:4b, qwen2.5:7b, gpt-4o-mini)", fontSize = 11.sp) },
											singleLine = true,
											modifier = Modifier
												.fillMaxWidth()
												.testTag("ollama_model_id_input"),
										)

										OutlinedTextField(
											value = config.openAiApiKey,
											onValueChange = { key ->
												onUpdateConfig {
													it.copy(
														openAiApiKey = key,
														connectionVerified = false,
													)
												}
											},
											label = {
												Text(
													"API Key (Optional for Local/LAN Ollama, required for OpenAI/OpenRouter)",
													fontSize = 10.sp,
												)
											},
											singleLine = true,
											visualTransformation = if (showOpenAiKey) {
												VisualTransformation.None
											} else {
												PasswordVisualTransformation()
											},
											trailingIcon = {
												IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
													Icon(
														imageVector = if (showOpenAiKey) {
															Icons.Default.VisibilityOff
														} else {
															Icons.Default.Visibility
														},
														contentDescription = "Toggle API key visibility",
													)
												}
											},
											modifier = Modifier
												.fillMaxWidth()
												.testTag("ollama_api_key_input"),
										)
									}
								}
							}

							// Test Connection Button + Verified Status Badge
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Button(
									onClick = onTestConnection,
									enabled = !config.isTestingConnection,
									colors = ButtonDefaults.buttonColors(
										containerColor = accentCyan,
										contentColor = Color(0xFF0A0E14),
									),
									shape = RoundedCornerShape(12.dp),
									modifier = Modifier.testTag("ai_test_connection_button"),
								) {
									if (config.isTestingConnection) {
										CircularProgressIndicator(
											modifier = Modifier.size(16.dp),
											strokeWidth = 2.dp,
											color = Color(0xFF0A0E14),
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text("Testing…", fontWeight = FontWeight.Bold, fontSize = 12.sp)
									} else {
										Icon(
											imageVector = Icons.Default.Check,
											contentDescription = null,
											modifier = Modifier.size(16.dp),
										)
										Spacer(modifier = Modifier.width(6.dp))
										Text("Test Connection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
									}
								}

								val statusMsg = config.lastConnectionStatusMessage
									?: if (config.connectionVerified) "✓ Connected & Verified" else null
								if (statusMsg != null) {
									Surface(
										color = if (config.connectionVerified) {
											Color(0xFF10B981).copy(alpha = 0.18f)
										} else {
											Color(0xFFF59E0B).copy(alpha = 0.18f)
										},
										shape = RoundedCornerShape(10.dp),
										modifier = Modifier
											.weight(1f)
											.testTag("ai_connection_status_badge"),
									) {
										Text(
											text = statusMsg,
											fontFamily = JetBrainsMonoFamily,
											fontWeight = FontWeight.Bold,
											fontSize = 11.sp,
											color = if (config.connectionVerified) Color(0xFF34D399) else Color(0xFFFBBF24),
											modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
										)
									}
								}
							}
						}
					}
				}

				// TIER 3: Local Model File (LiteRT-LM / MediaPipe .litertlm)
				TierOptionCard(
					tier = AiBackendTier.LOCAL_LITERT_FILE,
					selected = config.selectedTier == AiBackendTier.LOCAL_LITERT_FILE,
					onSelect = {
						onUpdateConfig { it.copy(selectedTier = AiBackendTier.LOCAL_LITERT_FILE) }
					},
					testTag = "ai_tier_3_local_litert",
				) {
					AnimatedVisibility(visible = config.selectedTier == AiBackendTier.LOCAL_LITERT_FILE) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 10.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Surface(
								color = Color(0xFF11141D),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier.fillMaxWidth(),
							) {
								Column(
									modifier = Modifier.padding(12.dp),
									verticalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween,
										verticalAlignment = Alignment.CenterVertically,
									) {
										Column(modifier = Modifier.weight(1f)) {
											Text(
												text = config.liteRtModelFileName ?: "Gemma-3-1B-IT-int4 • 460 MB",
												style = MaterialTheme.typography.labelLarge,
												fontWeight = FontWeight.Bold,
												color = Color.White,
											)
											Text(
												text = if (config.liteRtModelDownloaded) {
													"✓ Local LiteRT-LM weights ready for offline inference"
												} else {
													"Offline MediaPipe / LiteRT-LM runtime for devices without Android AICore"
												},
												style = MaterialTheme.typography.bodySmall,
												fontSize = 11.sp,
												color = if (config.liteRtModelDownloaded) Color(0xFF34D399) else Color(0xFF9EA4B0),
											)
										}
									}

									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.spacedBy(8.dp),
									) {
										OutlinedButton(
											onClick = { liteRtFileLauncher.launch(arrayOf("*/*")) },
											shape = RoundedCornerShape(10.dp),
											modifier = Modifier
												.weight(1f)
												.testTag("import_litertlm_file_button"),
										) {
											Icon(
												imageVector = Icons.Default.FolderOpen,
												contentDescription = null,
												modifier = Modifier.size(16.dp),
											)
											Spacer(modifier = Modifier.width(6.dp))
											Text("Import .litertlm / .task File", fontSize = 11.sp, maxLines = 1)
										}

										FilledTonalButton(
											onClick = onDownloadLiteRtModel,
											shape = RoundedCornerShape(10.dp),
											modifier = Modifier
												.weight(1f)
												.testTag("download_gemma_litert_button"),
										) {
											Icon(
												imageVector = if (config.liteRtModelDownloaded) {
													Icons.Default.CheckCircle
												} else {
													Icons.Default.CloudDownload
												},
												contentDescription = null,
												modifier = Modifier.size(16.dp),
											)
											Spacer(modifier = Modifier.width(6.dp))
											Text(
												text = if (config.liteRtModelDownloaded) {
													"Downloaded (460 MB)"
												} else {
													"Download (460 MB)"
												},
												fontSize = 11.sp,
												maxLines = 1,
											)
										}
									}
								}
							}
						}
					}
				}

				// TIER 4: Deterministic Extractive Mode (Zero LLM)
				TierOptionCard(
					tier = AiBackendTier.DETERMINISTIC_EXTRACTIVE,
					selected = config.selectedTier == AiBackendTier.DETERMINISTIC_EXTRACTIVE,
					onSelect = {
						onUpdateConfig { it.copy(selectedTier = AiBackendTier.DETERMINISTIC_EXTRACTIVE) }
					},
					testTag = "ai_tier_4_deterministic",
				) {
					Surface(
						color = accentCyan.copy(alpha = 0.12f),
						shape = RoundedCornerShape(50),
						modifier = Modifier.padding(top = 6.dp),
					) {
						Text(
							text = "⚡ 0 RAM / 0 Battery • Instant 3-Bullet Reading Trail & FTS5",
							fontFamily = JetBrainsMonoFamily,
							fontWeight = FontWeight.SemiBold,
							fontSize = 10.sp,
							color = accentCyan,
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
						)
					}
				}
			}
		}

		Spacer(modifier = Modifier.height(12.dp))
	}
}

@Composable
private fun TierOptionCard(
	tier: AiBackendTier,
	selected: Boolean,
	onSelect: () -> Unit,
	testTag: String,
	extraContent: @Composable () -> Unit = {},
) {
	val accentCyan = Color(0xFF5CE1E6)
	Card(
		onClick = onSelect,
		colors = CardDefaults.cardColors(
			containerColor = if (selected) Color(0xFF1B2333) else Color(0xFF161922),
		),
		border = BorderStroke(
			width = if (selected) 1.5.dp else 1.dp,
			color = if (selected) accentCyan else Color.White.copy(alpha = 0.08f),
		),
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier
			.fillMaxWidth()
			.testTag(testTag),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(14.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				RadioButton(
					selected = selected,
					onClick = onSelect,
					colors = RadioButtonDefaults.colors(
						selectedColor = accentCyan,
						unselectedColor = Color(0xFF9EA4B0),
					),
				)
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = tier.title,
						style = MaterialTheme.typography.titleSmall,
						fontFamily = PlusJakartaDisplayFamily,
						fontWeight = FontWeight.Bold,
						color = if (selected) Color.White else Color(0xFFE2E8F0),
					)
					Text(
						text = tier.subtitle,
						style = MaterialTheme.typography.bodySmall,
						fontSize = 11.sp,
						color = Color(0xFF9EA4B0),
					)
				}
			}
			extraContent()
		}
	}
}
