package org.nemuri.app.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.ReaderShaderConfig
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.domain.UpscaleEffect
import org.nemuri.app.ui.theme.NemuriTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AiUpscalePreviewPreset(
	val title: String,
	val shortBadge: String,
	val subtitle: String,
) {
	OFF(
		title = "Off (Original)",
		shortBadge = "Original",
		subtitle = "Native decode",
	),
	FAST_ANIME4K(
		title = "1.5x Fast Anime4K",
		shortBadge = "1.5x Fast Anime4K",
		subtitle = "Crisp GPU shader",
	),
	HQ_LINE_ART(
		title = "2.0x HQ Line-Art",
		shortBadge = "2.0x HQ Line-Art",
		subtitle = "ArtCNN + Neural",
	),
	BEDTIME_WARM(
		title = "Bedtime Warm Contrast",
		shortBadge = "Bedtime Warm",
		subtitle = "Warm ink + glare guard",
	),
}

@Composable
fun AiUpscalePreviewSheet(
	shaderConfig: ReaderShaderConfig,
	isNeuralUpscaleEnabled: Boolean,
	isNeuralUpscaleSupported: Boolean,
	neuralAccelerator: NeuralAccelerator,
	activeDelegateLabel: String = "",
	hasReadyNeuralModel: Boolean = false,
	isWarmBedtimeActive: Boolean = false,
	previewUri: Uri? = null,
	enhancedPreviewUri: Uri? = null,
	previewModel: Any? = null,
	onSelectShaderPreset: (ReaderShaderPreset) -> Unit,
	onSetInkLevels: (Boolean) -> Unit = {},
	onSetDescreen: (Boolean) -> Unit = {},
	onSetNeuralUpscaleEnabled: (Boolean) -> Unit = {},
	onSelectNeuralAccelerator: (NeuralAccelerator) -> Unit,
	onApplyBedtimeWarmPreset: ((Boolean) -> Unit)? = null,
	onOpenUpscalingSettings: () -> Unit,
	onDismiss: () -> Unit,
) {
	val ext = NemuriTheme.colors
	val accentCyan = ext.cyan
	val isSharp = NemuriTheme.isSharpTacticalGeometry
	val sheetShape = NemuriTheme.shape(24.dp)
	val frameShape = NemuriTheme.shape(16.dp)
	val pillShape = NemuriTheme.pillShape()
	val cardShape = NemuriTheme.shape(12.dp)
	val handleShape = if (isSharp) RoundedCornerShape(2.dp) else CircleShape

	var splitRatio by remember { mutableFloatStateOf(0.50f) }
	var lastNonOffPreset by remember { mutableStateOf(AiUpscalePreviewPreset.FAST_ANIME4K) }

	val activePreset = remember(shaderConfig, isNeuralUpscaleEnabled, isWarmBedtimeActive) {
		when {
			isWarmBedtimeActive -> AiUpscalePreviewPreset.BEDTIME_WARM
			shaderConfig.preset == ReaderShaderPreset.ARTCNN ||
				(isNeuralUpscaleEnabled && shaderConfig.preset != ReaderShaderPreset.ANIME4K) ->
				AiUpscalePreviewPreset.HQ_LINE_ART
			shaderConfig.preset == ReaderShaderPreset.ANIME4K || shaderConfig.isActive ->
				AiUpscalePreviewPreset.FAST_ANIME4K
			else -> AiUpscalePreviewPreset.OFF
		}
	}
	if (activePreset != AiUpscalePreviewPreset.OFF) {
		lastNonOffPreset = activePreset
	}
	val isUpscalingActive = activePreset != AiUpscalePreviewPreset.OFF || shaderConfig.isActive || isNeuralUpscaleEnabled
	val previewVisualPreset = if (activePreset == AiUpscalePreviewPreset.OFF) {
		AiUpscalePreviewPreset.FAST_ANIME4K
	} else {
		activePreset
	}

	val socModel = remember { resolveDeviceSocLabel() }
	val delegateStatus = remember(neuralAccelerator, activeDelegateLabel, isNeuralUpscaleSupported, socModel) {
		resolveComputeDelegateStatus(
			selected = neuralAccelerator,
			activeDelegateLabel = activeDelegateLabel,
			isNeuralSupported = isNeuralUpscaleSupported,
			socModel = socModel,
		)
	}

	fun applyPreset(preset: AiUpscalePreviewPreset) {
		when (preset) {
			AiUpscalePreviewPreset.OFF -> {
				onSelectShaderPreset(ReaderShaderPreset.OFF)
				onSetDescreen(false)
				onSetInkLevels(false)
				if (isNeuralUpscaleEnabled) {
					onSetNeuralUpscaleEnabled(false)
				}
				onApplyBedtimeWarmPreset?.invoke(false)
			}

			AiUpscalePreviewPreset.FAST_ANIME4K -> {
				onApplyBedtimeWarmPreset?.invoke(false)
				if (UpscaleEffect.isSupported) {
					onSelectShaderPreset(ReaderShaderPreset.ANIME4K)
					onSetDescreen(false)
					onSetInkLevels(false)
				}
				if (isNeuralUpscaleEnabled) {
					onSetNeuralUpscaleEnabled(false)
				}
			}

			AiUpscalePreviewPreset.HQ_LINE_ART -> {
				onApplyBedtimeWarmPreset?.invoke(false)
				if (UpscaleEffect.isSupported) {
					onSelectShaderPreset(ReaderShaderPreset.ARTCNN)
					onSetDescreen(true)
					onSetInkLevels(true)
				}
				if (isNeuralUpscaleSupported && hasReadyNeuralModel && !isNeuralUpscaleEnabled) {
					onSetNeuralUpscaleEnabled(true)
				}
			}

			AiUpscalePreviewPreset.BEDTIME_WARM -> {
				if (isNeuralUpscaleEnabled) {
					onSetNeuralUpscaleEnabled(false)
				}
				if (onApplyBedtimeWarmPreset != null) {
					onApplyBedtimeWarmPreset(true)
				} else if (UpscaleEffect.isSupported) {
					onSelectShaderPreset(ReaderShaderPreset.FSR_1)
					onSetDescreen(false)
					onSetInkLevels(true)
				}
			}
		}
	}

	val previewShaderConfig = remember(previewVisualPreset, shaderConfig) {
		when (previewVisualPreset) {
			AiUpscalePreviewPreset.FAST_ANIME4K -> ReaderShaderConfig(
				preset = ReaderShaderPreset.ANIME4K,
				descreen = false,
				inkLevels = false,
				strength = shaderConfig.strength.coerceAtLeast(0.75f),
			)
			AiUpscalePreviewPreset.HQ_LINE_ART -> ReaderShaderConfig(
				preset = ReaderShaderPreset.ARTCNN,
				descreen = true,
				inkLevels = true,
				strength = shaderConfig.strength.coerceAtLeast(0.85f),
			)
			AiUpscalePreviewPreset.BEDTIME_WARM -> ReaderShaderConfig(
				preset = ReaderShaderPreset.FSR_1,
				descreen = false,
				inkLevels = true,
				strength = shaderConfig.strength.coerceAtLeast(0.70f),
			)
			AiUpscalePreviewPreset.OFF -> ReaderShaderConfig()
		}
	}

	val rightColorFilter = remember(previewVisualPreset) {
		buildPreviewColorFilter(previewVisualPreset)
	}

	val rightRenderEffect = remember(previewShaderConfig) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && previewShaderConfig.isActive) {
			UpscaleEffect.create(2.0f, previewShaderConfig)?.asComposeRenderEffect()
		} else {
			null
		}
	}

	val rightBadgeLabel = remember(activePreset, previewVisualPreset, delegateStatus.shortBadge) {
		if (activePreset == AiUpscalePreviewPreset.OFF) {
			"${previewVisualPreset.shortBadge} Preview • ${delegateStatus.shortBadge}"
		} else {
			"${activePreset.shortBadge} • ${delegateStatus.shortBadge}"
		}
	}

	val leftModel: Any? = previewUri ?: previewModel
	val rightModel: Any? = enhancedPreviewUri ?: previewUri ?: previewModel

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			color = ext.sheet,
			shape = sheetShape,
			tonalElevation = 12.dp,
			shadowElevation = 24.dp,
			modifier = Modifier
				.fillMaxWidth(0.93f)
				.border(1.dp, accentCyan.copy(alpha = 0.28f), sheetShape),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 18.dp, vertical = 16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				// 1. Header: Left cyan ✨ + bold "AI upscaling preview"; right close button (✕)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(9.dp),
						modifier = Modifier.weight(1f),
					) {
						Icon(
							imageVector = Icons.Default.AutoAwesome,
							contentDescription = null,
							tint = accentCyan,
							modifier = Modifier.size(21.dp),
						)
						Text(
							text = "AI upscaling preview",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = ext.textPrimary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}

					Surface(
						color = ext.surfaceElevated,
						shape = pillShape,
						border = BorderStroke(1.dp, ext.border),
						modifier = Modifier
							.size(34.dp)
							.clip(pillShape)
							.clickable(onClick = onDismiss),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Close AI upscaling preview",
								tint = ext.textPrimary,
								modifier = Modifier.size(17.dp),
							)
						}
					}
				}

				// 2. Interactive Split Preview Box (aspectRatio 0.84f)
				var frameWidthPx by remember { mutableFloatStateOf(1f) }
				val density = LocalDensity.current
				val context = LocalContext.current

				val leftClipShape = remember(splitRatio) {
					GenericShape { size, _ ->
						addRect(Rect(0f, 0f, size.width * splitRatio, size.height))
					}
				}
				val rightClipShape = remember(splitRatio) {
					GenericShape { size, _ ->
						addRect(Rect(size.width * splitRatio, 0f, size.width, size.height))
					}
				}

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(0.84f)
						.clip(frameShape)
						.background(Color(0xFF0A0D13))
						.border(1.dp, ext.border.copy(alpha = 0.85f), frameShape)
						.onSizeChanged { frameWidthPx = it.width.toFloat().coerceAtLeast(1f) }
						.pointerInput(Unit) {
							detectTapGestures { tapOffset ->
								splitRatio = (tapOffset.x / frameWidthPx).coerceIn(0.08f, 0.92f)
							}
						}
						.pointerInput(Unit) {
							detectHorizontalDragGestures { change, _ ->
								change.consume()
								splitRatio = (change.position.x / frameWidthPx).coerceIn(0.08f, 0.92f)
							}
						},
				) {
					if (leftModel != null) {
						// Original side (left)
						SubcomposeAsyncImage(
							model = ImageRequest.Builder(context)
								.data(leftModel)
								.build(),
							contentDescription = "Original preview",
							contentScale = ContentScale.Crop,
							loading = {
								SampleMangaLineArtCanvas(
									isEnhanced = false,
									preset = previewVisualPreset,
									modifier = Modifier.fillMaxSize(),
								)
							},
							error = {
								SampleMangaLineArtCanvas(
									isEnhanced = false,
									preset = previewVisualPreset,
									modifier = Modifier.fillMaxSize(),
								)
							},
							modifier = Modifier
								.fillMaxSize()
								.clip(leftClipShape),
						)

						// Live Enhanced Output side (right)
						SubcomposeAsyncImage(
							model = ImageRequest.Builder(context)
								.data(rightModel ?: leftModel)
								.build(),
							contentDescription = "Enhanced preview",
							contentScale = ContentScale.Crop,
							colorFilter = rightColorFilter,
							loading = {
								SampleMangaLineArtCanvas(
									isEnhanced = true,
									preset = previewVisualPreset,
									modifier = Modifier.fillMaxSize(),
								)
							},
							error = {
								SampleMangaLineArtCanvas(
									isEnhanced = true,
									preset = previewVisualPreset,
									modifier = Modifier.fillMaxSize(),
								)
							},
							modifier = Modifier
								.fillMaxSize()
								.clip(rightClipShape)
								.graphicsLayer {
									if (rightRenderEffect != null) {
										renderEffect = rightRenderEffect
									}
								},
						)
					} else {
						// Crisp vector manga line-art sample canvas fallback (never looks blank)
						SampleMangaLineArtCanvas(
							isEnhanced = false,
							preset = previewVisualPreset,
							modifier = Modifier
								.fillMaxSize()
								.clip(leftClipShape),
						)
						SampleMangaLineArtCanvas(
							isEnhanced = true,
							preset = previewVisualPreset,
							modifier = Modifier
								.fillMaxSize()
								.clip(rightClipShape),
						)
					}

					// Top-left pill: Original
					Surface(
						color = Color.Black.copy(alpha = 0.72f),
						shape = pillShape,
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(10.dp),
					) {
						Text(
							text = "Original",
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
							modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
						)
					}

					// Top-right pill: Active preset + Active delegate badge
					Surface(
						color = ext.dock.copy(alpha = 0.88f),
						shape = pillShape,
						border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.65f)),
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(10.dp),
					) {
						Text(
							text = rightBadgeLabel,
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = accentCyan,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
						)
					}

					// Draggable vertical cyan divider line (2.dp) + centered 36.dp handle
					val handleXOffsetPx = (frameWidthPx * splitRatio).roundToInt()
					val halfHandlePx = with(density) { 18.dp.roundToPx() }
					val halfLinePx = with(density) { 1.dp.roundToPx() }

					Box(
						modifier = Modifier
							.offset { IntOffset(handleXOffsetPx - halfLinePx, 0) }
							.width(2.dp)
							.fillMaxHeight()
							.background(accentCyan),
					)

					Surface(
						color = accentCyan,
						shape = handleShape,
						shadowElevation = 8.dp,
						modifier = Modifier
							.align(Alignment.CenterStart)
							.offset { IntOffset(handleXOffsetPx - halfHandlePx, 0) }
							.size(36.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.SwapHoriz,
								contentDescription = "Drag left or right to compare Original vs Enhanced",
								tint = ext.onCyan,
								modifier = Modifier.size(20.dp),
							)
						}
					}
				}

				// 3. 2x2 Preset Grid
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					val presetRows = listOf(
						AiUpscalePreviewPreset.OFF to AiUpscalePreviewPreset.FAST_ANIME4K,
						AiUpscalePreviewPreset.HQ_LINE_ART to AiUpscalePreviewPreset.BEDTIME_WARM,
					)
					presetRows.forEach { (leftPreset, rightPreset) ->
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							listOf(leftPreset, rightPreset).forEach { preset ->
								val selected = activePreset == preset
								Surface(
									color = if (selected) ext.tealContainer else ext.surfaceElevated,
									shape = cardShape,
									border = BorderStroke(
										width = if (selected) 1.5.dp else 1.dp,
										color = if (selected) accentCyan else ext.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(cardShape)
										.clickable { applyPreset(preset) },
								) {
									Column(
										modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
										verticalArrangement = Arrangement.spacedBy(2.dp),
									) {
										Text(
											text = preset.title,
											style = MaterialTheme.typography.labelMedium,
											fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
											color = if (selected) accentCyan else ext.textPrimary,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
										Text(
											text = preset.subtitle,
											style = MaterialTheme.typography.labelSmall,
											fontSize = 10.5.sp,
											color = if (selected) accentCyan.copy(alpha = 0.85f) else ext.textMuted,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
								}
							}
						}
					}
				}

				// 4. Compute Delegate Selector (Header + Active pill + 4-pill row + real hardware capability subtitle)
				Surface(
					color = ext.surfaceElevated.copy(alpha = 0.72f),
					shape = cardShape,
					border = BorderStroke(1.dp, ext.border),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(11.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = "Compute delegate",
								style = MaterialTheme.typography.labelLarge,
								fontWeight = FontWeight.SemiBold,
								color = ext.textPrimary,
							)
							Surface(
								color = ext.tealContainer,
								shape = pillShape,
								border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.55f)),
							) {
								Text(
									text = "Active: ${delegateStatus.shortBadge}",
									style = MaterialTheme.typography.labelSmall,
									fontWeight = FontWeight.Bold,
									color = accentCyan,
									modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							NeuralAccelerator.entries.forEach { accel ->
								val selected = neuralAccelerator == accel
								val label = when (accel) {
									NeuralAccelerator.AUTO -> "Auto"
									NeuralAccelerator.NPU -> "NPU"
									NeuralAccelerator.GPU -> "GPU"
									NeuralAccelerator.CPU -> "CPU"
								}
								Surface(
									color = if (selected) accentCyan else ext.card,
									shape = pillShape,
									border = BorderStroke(
										width = 1.dp,
										color = if (selected) accentCyan else ext.border,
									),
									modifier = Modifier
										.weight(1f)
										.clip(pillShape)
										.clickable { onSelectNeuralAccelerator(accel) },
								) {
									Text(
										text = label,
										style = MaterialTheme.typography.labelMedium,
										fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
										color = if (selected) ext.onCyan else ext.textSecondary,
										textAlign = TextAlign.Center,
										maxLines = 1,
										modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
									)
								}
							}
						}

						Text(
							text = delegateStatus.capabilitySubtitle,
							style = MaterialTheme.typography.bodySmall,
							fontSize = 11.sp,
							lineHeight = 15.sp,
							color = ext.textMuted,
						)
					}
				}

				// 5. Bottom Bar: ⚙️ Upscaling settings link on left + Active master switch on right
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 2.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						modifier = Modifier
							.clip(NemuriTheme.shape(8.dp))
							.clickable {
								onDismiss()
								onOpenUpscalingSettings()
							}
							.padding(vertical = 6.dp, horizontal = 4.dp),
					) {
						Icon(
							imageVector = Icons.Default.Settings,
							contentDescription = null,
							tint = accentCyan,
							modifier = Modifier.size(16.dp),
						)
						Text(
							text = "Upscaling settings",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
							color = accentCyan,
						)
					}

					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Text(
							text = "Active",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
							color = ext.textPrimary,
						)
						Switch(
							checked = isUpscalingActive,
							onCheckedChange = { enabled ->
								if (enabled) {
									applyPreset(lastNonOffPreset)
								} else {
									applyPreset(AiUpscalePreviewPreset.OFF)
								}
							},
							colors = SwitchDefaults.colors(
								checkedThumbColor = ext.onCyan,
								checkedTrackColor = accentCyan,
							),
						)
					}
				}
			}
		}
	}
}

private data class ComputeDelegateStatus(
	val shortBadge: String,
	val capabilitySubtitle: String,
)

private fun resolveDeviceSocLabel(): String {
	val socRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		Build.SOC_MODEL.orEmpty().trim()
	} else {
		Build.HARDWARE.orEmpty().trim()
	}
	return socRaw
		.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
		?: Build.HARDWARE.orEmpty().trim().takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
		?: Build.MODEL.orEmpty().ifBlank { "this device" }
}

private fun resolveComputeDelegateStatus(
	selected: NeuralAccelerator,
	activeDelegateLabel: String,
	isNeuralSupported: Boolean,
	socModel: String,
): ComputeDelegateStatus {
	val lower = activeDelegateLabel.lowercase()
	val npuReportedActive = ("npu" in lower || "qnn" in lower || "htp" in lower) &&
		"unavailable" !in lower &&
		"fallback" !in lower
	val cpuReportedActive = "cpu" in lower || "xnnpack" in lower

	if (!isNeuralSupported) {
		return ComputeDelegateStatus(
			shortBadge = if (UpscaleEffect.isSupported) "GPU" else "CPU",
			capabilitySubtitle = if (UpscaleEffect.isSupported) {
				"On-device AGSL GPU shaders active on $socModel • Neural NPU runtime unavailable in FOSS build"
			} else {
				"NPU runtime unavailable in FOSS build on $socModel — using CPU pipeline"
			},
		)
	}

	return when (selected) {
		NeuralAccelerator.AUTO -> when {
			npuReportedActive -> ComputeDelegateStatus(
				shortBadge = "NPU",
				capabilitySubtitle = "Hardware NPU active on $socModel ($activeDelegateLabel)",
			)
			cpuReportedActive -> ComputeDelegateStatus(
				shortBadge = "CPU",
				capabilitySubtitle = "NPU/GPU delegate unavailable on $socModel — Auto using CPU",
			)
			else -> ComputeDelegateStatus(
				shortBadge = "GPU",
				capabilitySubtitle = "NPU unavailable on $socModel — Auto using GPU",
			)
		}

		NeuralAccelerator.NPU -> if (npuReportedActive) {
			ComputeDelegateStatus(
				shortBadge = "NPU",
				capabilitySubtitle = "Qualcomm/LiteRT NPU active on $socModel ($activeDelegateLabel)",
			)
		} else {
			ComputeDelegateStatus(
				shortBadge = "GPU",
				capabilitySubtitle = "NPU unavailable on $socModel — falling back to GPU",
			)
		}

		NeuralAccelerator.GPU -> ComputeDelegateStatus(
			shortBadge = "GPU",
			capabilitySubtitle = "GPU shader & OpenCL/GL FP16 pipeline active on $socModel",
		)

		NeuralAccelerator.CPU -> ComputeDelegateStatus(
			shortBadge = "CPU",
			capabilitySubtitle = "Multi-core ARM NEON / XNNPACK CPU execution on $socModel",
		)
	}
}

private fun buildPreviewColorFilter(preset: AiUpscalePreviewPreset): ColorFilter? = when (preset) {
	AiUpscalePreviewPreset.OFF -> null
	AiUpscalePreviewPreset.FAST_ANIME4K -> {
		val c = 1.15f
		val t = (-0.5f * c + 0.5f) * 255f
		ColorFilter.colorMatrix(
			ColorMatrix(
				floatArrayOf(
					c, 0f, 0f, 0f, t,
					0f, c, 0f, 0f, t,
					0f, 0f, c, 0f, t,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}

	AiUpscalePreviewPreset.HQ_LINE_ART -> {
		val c = 1.26f
		val t = (-0.5f * c + 0.5f) * 255f
		ColorFilter.colorMatrix(
			ColorMatrix(
				floatArrayOf(
					c, 0f, 0f, 0f, t,
					0f, c, 0f, 0f, t,
					0f, 0f, c, 0f, t,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}

	AiUpscalePreviewPreset.BEDTIME_WARM -> {
		val c = 1.14f
		val t = (-0.5f * c + 0.5f) * 255f
		ColorFilter.colorMatrix(
			ColorMatrix(
				floatArrayOf(
					c * 1.02f, 0f, 0f, 0f, t + 6f,
					0f, c * 0.94f, 0f, 0f, t + 2f,
					0f, 0f, c * 0.78f, 0f, t,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
}

/**
 * Crisp vector manga line-art sample canvas used whenever no live page or library cover is available,
 * clearly demonstrating low-res muted scan ink (left) vs sharpened high-contrast / warm ink (right).
 */
@Composable
private fun SampleMangaLineArtCanvas(
	isEnhanced: Boolean,
	preset: AiUpscalePreviewPreset,
	modifier: Modifier = Modifier,
) {
	Canvas(modifier = modifier) {
		val w = size.width
		val h = size.height
		val isWarm = isEnhanced && preset == AiUpscalePreviewPreset.BEDTIME_WARM
		val isHq = isEnhanced && preset == AiUpscalePreviewPreset.HQ_LINE_ART

		val paperBg = when {
			isWarm -> Color(0xFFF3E7D2)
			isEnhanced -> Color(0xFFF8FAFC)
			else -> Color(0xFFDCDFE5)
		}
		val inkColor = when {
			isWarm -> Color(0xFF1F1812)
			isEnhanced -> Color(0xFF07090D)
			else -> Color(0xFF4B5260)
		}
		val toneColor = when {
			isWarm -> Color(0xFF5A493A)
			isEnhanced -> Color(0xFF1E293B)
			else -> Color(0xFF838A98)
		}

		// Paper sheet background
		drawRect(color = paperBg, size = size)

		// Outer manga panel border
		val pad = w * 0.06f
		val panelW = w - pad * 2f
		val panelH = h - pad * 2f
		drawRect(
			brush = Brush.verticalGradient(
				colors = listOf(
					paperBg,
					if (isEnhanced) toneColor.copy(alpha = 0.14f) else toneColor.copy(alpha = 0.24f),
				),
				startY = pad,
				endY = pad + panelH,
			),
			topLeft = Offset(pad, pad),
			size = Size(panelW, panelH),
		)

		// Screentone halftone dot field in upper sky
		val step = (w / if (isEnhanced) 26f else 18f).coerceAtLeast(8f)
		val dotRadius = if (isHq) 1.35.dp.toPx() else if (isEnhanced) 1.6.dp.toPx() else 2.2.dp.toPx()
		var y = pad + step * 0.7f
		while (y < pad + panelH * 0.44f) {
			var x = pad + step * 0.7f
			while (x < pad + panelW - step * 0.5f) {
				drawCircle(
					color = toneColor.copy(alpha = if (isEnhanced) 0.16f else 0.25f),
					radius = dotRadius,
					center = Offset(x, y),
				)
				x += step
			}
			y += step
		}

		// Dramatic radial manga action speedlines
		val center = Offset(w * 0.50f, h * 0.48f)
		val rayCount = 36
		val lineStroke = if (isEnhanced) 1.5.dp.toPx() else 2.4.dp.toPx()
		for (i in 0 until rayCount) {
			val angle = (i * (360f / rayCount)) * (Math.PI / 180.0)
			val innerR = w * (if (i % 3 == 0) 0.22f else 0.30f)
			val outerR = maxOf(w, h) * 0.62f
			val sx = center.x + (cos(angle) * innerR).toFloat()
			val sy = center.y + (sin(angle) * innerR).toFloat()
			val ex = center.x + (cos(angle) * outerR).toFloat()
			val ey = center.y + (sin(angle) * outerR).toFloat()
			drawLine(
				color = inkColor.copy(alpha = if (isEnhanced) 0.36f else 0.22f),
				start = Offset(sx.coerceIn(pad, w - pad), sy.coerceIn(pad, h - pad)),
				end = Offset(ex.coerceIn(pad, w - pad), ey.coerceIn(pad, h - pad)),
				strokeWidth = lineStroke,
				cap = StrokeCap.Round,
			)
		}

		// Moon / celestial disc behind shrine torii silhouette
		drawCircle(
			color = if (isWarm) Color(0xFFFFF4DC) else Color.White,
			radius = w * 0.22f,
			center = Offset(w * 0.50f, h * 0.44f),
		)
		drawCircle(
			color = inkColor,
			radius = w * 0.22f,
			center = Offset(w * 0.50f, h * 0.44f),
			style = Stroke(width = if (isEnhanced) 2.4.dp.toPx() else 3.2.dp.toPx()),
		)

		// Stylized Torii gate / manga architectural ink silhouette
		val gateStroke = if (isEnhanced) 5.5.dp.toPx() else 6.8.dp.toPx()
		val topBeamY = h * 0.46f
		val secondBeamY = h * 0.525f
		val baseY = h * 0.80f
		drawLine(
			color = inkColor,
			start = Offset(w * 0.23f, topBeamY),
			end = Offset(w * 0.77f, topBeamY),
			strokeWidth = gateStroke * 1.15f,
			cap = StrokeCap.Round,
		)
		drawLine(
			color = inkColor,
			start = Offset(w * 0.29f, secondBeamY),
			end = Offset(w * 0.71f, secondBeamY),
			strokeWidth = gateStroke * 0.85f,
			cap = StrokeCap.Round,
		)
		drawLine(
			color = inkColor,
			start = Offset(w * 0.36f, topBeamY),
			end = Offset(w * 0.34f, baseY),
			strokeWidth = gateStroke,
			cap = StrokeCap.Round,
		)
		drawLine(
			color = inkColor,
			start = Offset(w * 0.64f, topBeamY),
			end = Offset(w * 0.66f, baseY),
			strokeWidth = gateStroke,
			cap = StrokeCap.Round,
		)

		// Sweeping diagonal slash arc for fine line-art contrast inspection
		val slashPath = Path().apply {
			moveTo(w * 0.14f, h * 0.76f)
			quadraticTo(w * 0.48f, h * 0.56f, w * 0.86f, h * 0.26f)
		}
		drawPath(
			path = slashPath,
			color = if (isEnhanced) Color(0xFF5CE1E6) else Color(0xFF7CA4AB),
			style = Stroke(
				width = if (isEnhanced) 3.2.dp.toPx() else 5.2.dp.toPx(),
				cap = StrokeCap.Round,
			),
		)

		// Crisp speech bubble in lower-left corner
		val bubbleLeft = w * 0.12f
		val bubbleTop = h * 0.72f
		val bubbleW = w * 0.36f
		val bubbleH = h * 0.14f
		drawRoundRect(
			color = paperBg,
			topLeft = Offset(bubbleLeft, bubbleTop),
			size = Size(bubbleW, bubbleH),
			cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
		)
		drawRoundRect(
			color = inkColor,
			topLeft = Offset(bubbleLeft, bubbleTop),
			size = Size(bubbleW, bubbleH),
			cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
			style = Stroke(width = if (isEnhanced) 2.2.dp.toPx() else 3.0.dp.toPx()),
		)
		// Simulated vertical manga dialogue ink strokes inside bubble
		val textLineW = if (isEnhanced) 2.dp.toPx() else 3.dp.toPx()
		for (col in 0..3) {
			val tx = bubbleLeft + bubbleW * (0.22f + col * 0.18f)
			drawLine(
				color = inkColor,
				start = Offset(tx, bubbleTop + bubbleH * 0.24f),
				end = Offset(tx, bubbleTop + bubbleH * (if (col % 2 == 0) 0.76f else 0.62f)),
				strokeWidth = textLineW,
				cap = StrokeCap.Round,
			)
		}

		// Heavy manga frame stroke
		drawRect(
			color = inkColor,
			topLeft = Offset(pad, pad),
			size = Size(panelW, panelH),
			style = Stroke(width = if (isEnhanced) 3.dp.toPx() else 4.dp.toPx()),
		)
	}
}
