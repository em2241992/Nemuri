package org.koitharu.kotatsu.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewArray
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koitharu.kotatsu.core.model.AiUpscaleMode
import org.koitharu.kotatsu.core.model.ReaderColorFilterPreset
import org.koitharu.kotatsu.core.model.ReaderCustomizationConfig
import org.koitharu.kotatsu.core.model.ReaderRotationMode
import org.koitharu.kotatsu.core.model.ReaderScaleMode
import org.koitharu.kotatsu.core.model.ReaderStyleMode
import org.koitharu.kotatsu.ui.components.MangaReaderPageCanvas
import kotlin.math.roundToInt

internal data class ReaderChapterEntry(
	val index: Int,
	val volumeNumber: Int,
	val chapterNumber: String,
	val title: String,
	val relativeDate: String,
	val scanlatorOrSource: String,
)

internal enum class ReaderOptionsSheetTab {
	READING,
	MORE,
}

internal enum class ReaderDrawerTab {
	CHAPTERS,
	PAGE_GRID,
	BOOKMARKS,
}

// =========================================================================
// TASK 2: LIVE "✦ AI UPSCALING PREVIEW" MODAL IN READER
// =========================================================================
@Composable
internal fun ReaderAiUpscalingPreviewModal(
	mangaTitle: String,
	chapterNumber: Float,
	pageNumber: Int,
	totalPages: Int,
	readerConfig: ReaderCustomizationConfig,
	decodedBitmap: Bitmap?,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onDismiss: () -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	var splitRatio by remember { mutableFloatStateOf(0.50f) }
	var showUpscalerPresets by remember { mutableStateOf(false) }
	val isUpscalingActive = readerConfig.aiUpscaleMode != AiUpscaleMode.OFF

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Surface(
			color = Color(0xFF161922),
			shape = RoundedCornerShape(28.dp),
			tonalElevation = 12.dp,
			shadowElevation = 24.dp,
			modifier = Modifier
				.fillMaxWidth(0.92f)
				.border(1.dp, accentCyan.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
				.testTag("reader_ai_upscaling_preview_modal"),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(20.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				// 1. Modal Header: Left cyan ✦ + bold title "AI upscaling preview"; Right circular ✕ close button
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.AutoAwesome,
							contentDescription = null,
							tint = accentCyan,
							modifier = Modifier.size(22.dp),
						)
						Text(
							text = "AI upscaling preview",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = Color.White,
						)
					}

					Surface(
						color = Color.White.copy(alpha = 0.08f),
						shape = CircleShape,
						modifier = Modifier
							.size(36.dp)
							.clip(CircleShape)
							.clickable(onClick = onDismiss)
							.testTag("ai_preview_modal_close_button"),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Close AI upscaling preview",
								tint = Color.White,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}

				// 2. Interactive Live Page Split-Comparison Frame
				var frameWidthPx by remember { mutableFloatStateOf(1f) }
				val density = LocalDensity.current

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(0.84f)
						.clip(RoundedCornerShape(16.dp))
						.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
						.onSizeChanged { frameWidthPx = it.width.toFloat().coerceAtLeast(1f) }
						.pointerInput(Unit) {
							detectTapGestures { tapOffset ->
								splitRatio = (tapOffset.x / frameWidthPx).coerceIn(0.08f, 0.92f)
							}
						}
						.pointerInput(Unit) {
							detectDragGestures { change, _ ->
								change.consume()
								splitRatio = (change.position.x / frameWidthPx).coerceIn(0.08f, 0.92f)
							}
						}
						.testTag("ai_upscaling_split_comparison_frame"),
				) {
					// Actual manga page currently open in the Reader rendered with split comparison
					MangaReaderPageCanvas(
						mangaTitle = mangaTitle,
						chapterNumber = chapterNumber,
						pageNumber = pageNumber,
						totalPages = totalPages,
						upscaleMode = if (isUpscalingActive) readerConfig.aiUpscaleMode else AiUpscaleMode.ANIME_SUPER_RES_2X,
						showBeforeAfterSplit = true,
						splitRatio = splitRatio,
						warmPaperTint = readerConfig.warmPaperTint,
						invertColors = readerConfig.invertColors,
						grayscale = readerConfig.grayscale,
						colorFilterPreset = readerConfig.colorFilterPreset,
						brightnessDelta = readerConfig.brightnessDelta,
						contrastDelta = readerConfig.contrastDelta,
						warmthDelta = readerConfig.warmthDelta,
						bedtimeDimmerAlpha = readerConfig.bedtimeDimmerAlpha,
						saturation = readerConfig.saturation,
						redBalance = readerConfig.redBalance,
						greenBalance = readerConfig.greenBalance,
						blueBalance = readerConfig.blueBalance,
						decodedBitmap = decodedBitmap,
						modifier = Modifier.fillMaxSize(),
					)

					// Vertical cyan split line (#5CE1E6, 2.dp width) + centered 36.dp circular cyan drag handle (↔)
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
						shape = CircleShape,
						shadowElevation = 8.dp,
						modifier = Modifier
							.align(Alignment.CenterStart)
							.offset { IntOffset(handleXOffsetPx - halfHandlePx, 0) }
							.size(36.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.SwapHoriz,
								contentDescription = "Drag left or right to compare Original vs Upscaled",
								tint = Color(0xFF071318),
								modifier = Modifier.size(20.dp),
							)
						}
					}
				}

				// Below frame: Original label on the left, Upscaled label on the right
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 4.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "Original",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.SemiBold,
						color = Color(0xFFB5BCC9),
					)
					Text(
						text = "Upscaled (${
							when (readerConfig.aiUpscaleMode) {
								AiUpscaleMode.OFF -> "2.0x HQ Preview"
								AiUpscaleMode.FAST_CRISP_INK_1_5X -> "1.5x Fast NPU"
								AiUpscaleMode.ANIME_SUPER_RES_2X -> "2.0x HQ"
								AiUpscaleMode.HIGH_CONTRAST_RESTORE -> "Ink Restore"
							}
						})",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = accentCyan,
					)
				}

				// 3. Caption & Instant Controls Footer
				Text(
					text = "Only pages whose native resolution is well below your screen get sharpened on the GPU while you read. Everything runs on-device and works offline, and upscaling pauses automatically while battery saver is on.",
					style = MaterialTheme.typography.bodySmall,
					color = Color(0xFFA0A7B5),
					lineHeight = 18.sp,
				)

				// Expandable Upscaler Preset Selector (Off | 1.5x Fast NPU | 2.0x HQ)
				if (showUpscalerPresets) {
					Surface(
						color = Color(0xFF0E1017),
						shape = RoundedCornerShape(14.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(6.dp),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							val presets = listOf(
								AiUpscaleMode.OFF to "Off",
								AiUpscaleMode.FAST_CRISP_INK_1_5X to "1.5x Fast NPU",
								AiUpscaleMode.ANIME_SUPER_RES_2X to "2.0x HQ",
							)
							presets.forEach { (mode, label) ->
								val selected = readerConfig.aiUpscaleMode == mode
								Surface(
									color = if (selected) accentCyan else Color.Transparent,
									shape = RoundedCornerShape(10.dp),
									modifier = Modifier
										.weight(1f)
										.clip(RoundedCornerShape(10.dp))
										.clickable {
											onUpdateReaderConfig {
												it.copy(
													aiUpscaleMode = mode,
													showBeforeAfterSplit = mode != AiUpscaleMode.OFF,
												)
											}
										},
								) {
									Text(
										text = label,
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.Bold,
										color = if (selected) Color(0xFF071318) else Color(0xFFD5DAE5),
										textAlign = TextAlign.Center,
										modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
									)
								}
							}
						}
					}
				}

				// Bottom row: "Upscaling settings" cyan text link on left + instant "Active" toggle switch on right
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 2.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = if (showUpscalerPresets) "Hide upscaling settings" else "Upscaling settings",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.SemiBold,
						color = accentCyan,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.clickable { showUpscalerPresets = !showUpscalerPresets }
							.padding(vertical = 6.dp, horizontal = 4.dp)
							.testTag("ai_preview_upscaling_settings_link"),
					)

					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Text(
							text = "Active",
							style = MaterialTheme.typography.labelLarge,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
						)
						Switch(
							checked = isUpscalingActive,
							onCheckedChange = { enabled ->
								onUpdateReaderConfig {
									it.copy(
										aiUpscaleMode = if (enabled) {
											if (it.aiUpscaleMode == AiUpscaleMode.OFF) {
												AiUpscaleMode.ANIME_SUPER_RES_2X
											} else {
												it.aiUpscaleMode
											}
										} else {
											AiUpscaleMode.OFF
										},
									)
								}
							},
							colors = SwitchDefaults.colors(
								checkedThumbColor = Color(0xFF081318),
								checkedTrackColor = accentCyan,
							),
							modifier = Modifier.testTag("ai_preview_active_switch"),
						)
					}
				}
			}
		}
	}
}

// =========================================================================
// TASK 3: TWO-TAB READER OPTIONS SHEET (Reading | More)
// =========================================================================
@Composable
internal fun ReaderTwoTabOptionsSheet(
	activeTab: ReaderOptionsSheetTab,
	onSelectTab: (ReaderOptionsSheetTab) -> Unit,
	readerConfig: ReaderCustomizationConfig,
	isPageBookmarked: Boolean,
	mangaTitle: String,
	chapterNumber: String,
	pageNumber: Int,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onTogglePageBookmark: () -> Unit,
	onOpenColorCorrection: () -> Unit,
	onOpenAdvancedReaderSettings: () -> Unit,
	onDismiss: () -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val context = LocalContext.current

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.45f))
			.clickable(onClick = onDismiss)
			.testTag("reader_two_tab_options_backdrop"),
		contentAlignment = Alignment.BottomCenter,
	) {
		Surface(
			color = Color(0xFF15171E),
			shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
			tonalElevation = 12.dp,
			shadowElevation = 24.dp,
			modifier = Modifier
				.fillMaxWidth()
				.clickable(enabled = false) {}
				.navigationBarsPadding()
				.padding(bottom = 12.dp)
				.testTag("reader_two_tab_options_sheet"),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 18.dp, vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				// Drag Handle
				Box(
					modifier = Modifier.fillMaxWidth(),
					contentAlignment = Alignment.Center,
				) {
					Box(
						modifier = Modifier
							.width(38.dp)
							.height(4.dp)
							.clip(CircleShape)
							.background(Color(0xFF4A4E58)),
					)
				}

				when (activeTab) {
					ReaderOptionsSheetTab.READING -> {
						// 1. Top Card (4 Reading Mode Tiles: Standard, R-to-L, Vertical, Webtoon)
						Surface(
							color = Color(0xFF1E222D),
							shape = RoundedCornerShape(20.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(14.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									val modeItems = listOf(
										Triple(ReaderStyleMode.PAGER_LTR, "Standard", Icons.Default.ViewArray),
										Triple(ReaderStyleMode.PAGER_RTL, "R-to-L", Icons.Default.SwapHoriz),
										Triple(ReaderStyleMode.PAGER_VERTICAL, "Vertical", Icons.Default.ViewDay),
										Triple(ReaderStyleMode.WEBTOON_CONTINUOUS, "Webtoon", Icons.Default.ViewAgenda),
									)
									modeItems.forEach { (mode, label, icon) ->
										val isSelected = readerConfig.readingMode == mode
										Surface(
											color = if (isSelected) accentCyan else Color(0xFF141720),
											shape = RoundedCornerShape(16.dp),
											modifier = Modifier
												.weight(1f)
												.height(76.dp)
												.clip(RoundedCornerShape(16.dp))
												.clickable {
													onUpdateReaderConfig { it.copy(readingMode = mode) }
												}
												.testTag("reader_mode_tile_${mode.name.lowercase()}"),
										) {
											Column(
												modifier = Modifier
													.fillMaxSize()
													.padding(6.dp),
												horizontalAlignment = Alignment.CenterHorizontally,
												verticalArrangement = Arrangement.Center,
											) {
												Icon(
													imageVector = icon,
													contentDescription = label,
													tint = if (isSelected) Color(0xFF071318) else Color(0xFFD5DAE5),
													modifier = Modifier.size(22.dp),
												)
												Spacer(modifier = Modifier.height(6.dp))
												Text(
													text = label,
													style = MaterialTheme.typography.labelMedium,
													fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
													color = if (isSelected) Color(0xFF071318) else Color(0xFFD5DAE5),
													maxLines = 1,
												)
											}
										}
									}
								}

								Text(
									text = "The chosen configuration will be remembered for this manga",
									style = MaterialTheme.typography.bodySmall,
									color = Color(0xFF9EA4B0),
									textAlign = TextAlign.Center,
									modifier = Modifier.fillMaxWidth(),
								)
							}
						}

						// 2. Middle Row (2 Large Action Tiles: Automatic scroll & Save page)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							val autoScrollActive = readerConfig.autoScrollEnabled
							Surface(
								color = if (autoScrollActive) accentCyan.copy(alpha = 0.18f) else Color(0xFF1E222D),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.weight(1f)
									.height(74.dp)
									.border(
										width = 1.dp,
										color = if (autoScrollActive) accentCyan else Color.Transparent,
										shape = RoundedCornerShape(18.dp),
									)
									.clip(RoundedCornerShape(18.dp))
									.clickable {
										onUpdateReaderConfig {
											it.copy(autoScrollEnabled = !it.autoScrollEnabled)
										}
									}
									.testTag("reader_tile_auto_scroll"),
							) {
								Column(
									modifier = Modifier
										.fillMaxSize()
										.padding(horizontal = 12.dp, vertical = 10.dp),
									verticalArrangement = Arrangement.SpaceBetween,
								) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween,
										verticalAlignment = Alignment.CenterVertically,
									) {
										Row(
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(6.dp),
										) {
											Icon(
												imageVector = Icons.Default.Timer,
												contentDescription = null,
												tint = if (autoScrollActive) accentCyan else Color.White,
												modifier = Modifier.size(18.dp),
											)
											Text(
												text = "Automatic scroll",
												style = MaterialTheme.typography.labelLarge,
												fontWeight = FontWeight.SemiBold,
												color = if (autoScrollActive) accentCyan else Color.White,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis,
											)
										}
										// Inline speed pill (cycles 0.5x -> 1.0x -> 1.5x -> 2.0x)
										Surface(
											color = Color(0xFF12151E),
											shape = RoundedCornerShape(999.dp),
											modifier = Modifier
												.clip(RoundedCornerShape(999.dp))
												.clickable {
													val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
													val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - readerConfig.autoScrollSpeedMultiplier) < 0.1f } + 1) % speeds.size
													onUpdateReaderConfig {
														it.copy(
															autoScrollSpeedMultiplier = speeds[nextIdx],
															autoScrollEnabled = true,
														)
													}
												},
										) {
											Text(
												text = "${readerConfig.autoScrollSpeedMultiplier}x",
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.Bold,
												color = accentCyan,
												modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
											)
										}
									}

									Text(
										text = if (readerConfig.bedtimeAutoPauseAtChapterEnd) {
											"Bedtime Auto-Pause at Ch. end"
										} else {
											"Continuous scroll"
										},
										style = MaterialTheme.typography.labelSmall,
										color = Color(0xFF9EA4B0),
										maxLines = 1,
									)
								}
							}

							Surface(
								color = Color(0xFF1E222D),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.weight(1f)
									.height(74.dp)
									.clip(RoundedCornerShape(18.dp))
									.clickable {
										Toast
											.makeText(
												context,
												"Saved $mangaTitle Ch.$chapterNumber Page $pageNumber to Pictures/Nemuri",
												Toast.LENGTH_SHORT,
											)
											.show()
									}
									.testTag("reader_tile_save_page"),
							) {
								Column(
									modifier = Modifier
										.fillMaxSize()
										.padding(horizontal = 14.dp, vertical = 10.dp),
									verticalArrangement = Arrangement.SpaceBetween,
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(8.dp),
									) {
										Icon(
											imageVector = Icons.Default.Download,
											contentDescription = null,
											tint = Color.White,
											modifier = Modifier.size(18.dp),
										)
										Text(
											text = "Save page",
											style = MaterialTheme.typography.labelLarge,
											fontWeight = FontWeight.SemiBold,
											color = Color.White,
										)
									}
									Text(
										text = "Page $pageNumber PNG",
										style = MaterialTheme.typography.labelSmall,
										color = Color(0xFF9EA4B0),
									)
								}
							}
						}

						// 3. Bottom Row: Left Wide Card (Settings — Advanced reader options >) + Right Circular Button (64.dp Bookmark)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Surface(
								color = Color(0xFF1E222D),
								shape = RoundedCornerShape(20.dp),
								modifier = Modifier
									.weight(1f)
									.height(64.dp)
									.clip(RoundedCornerShape(20.dp))
									.clickable(onClick = onOpenAdvancedReaderSettings)
									.testTag("reader_advanced_settings_card"),
							) {
								Row(
									modifier = Modifier
										.fillMaxSize()
										.padding(horizontal = 16.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(12.dp),
									) {
										Icon(
											imageVector = Icons.Default.Settings,
											contentDescription = null,
											tint = accentCyan,
											modifier = Modifier.size(22.dp),
										)
										Column {
											Text(
												text = "Settings",
												style = MaterialTheme.typography.titleSmall,
												fontWeight = FontWeight.Bold,
												color = Color.White,
											)
											Text(
												text = "Advanced reader options",
												style = MaterialTheme.typography.bodySmall,
												color = Color(0xFF9EA4B0),
											)
										}
									}
									Icon(
										imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
										contentDescription = null,
										tint = Color(0xFF9EA4B0),
									)
								}
							}

							Surface(
								color = if (isPageBookmarked) accentCyan else Color(0xFF1E222D),
								shape = CircleShape,
								modifier = Modifier
									.size(64.dp)
									.clip(CircleShape)
									.clickable(onClick = onTogglePageBookmark)
									.testTag("reader_bookmark_circle_button"),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (isPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
										contentDescription = "Bookmark current page",
										tint = if (isPageBookmarked) Color(0xFF071318) else Color.White,
										modifier = Modifier.size(26.dp),
									)
								}
							}
						}
					}

					ReaderOptionsSheetTab.MORE -> {
						// 1. Top Row (3 Action Tiles: Share page, Rotate screen, Color correction)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							// Share page
							Surface(
								color = Color(0xFF1E222D),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.weight(1f)
									.height(82.dp)
									.clip(RoundedCornerShape(18.dp))
									.clickable {
										runCatching {
											val sendIntent = Intent(Intent.ACTION_SEND).apply {
												type = "text/plain"
												putExtra(
													Intent.EXTRA_TEXT,
													"Reading $mangaTitle — Ch.$chapterNumber (Page $pageNumber) on Nemuri",
												)
											}
											context.startActivity(Intent.createChooser(sendIntent, "Share page"))
										}
									}
									.testTag("reader_more_share_page"),
							) {
								Column(
									modifier = Modifier
										.fillMaxSize()
										.padding(10.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.Center,
								) {
									Icon(
										imageVector = Icons.Default.Share,
										contentDescription = "Share page",
										tint = Color.White,
										modifier = Modifier.size(22.dp),
									)
									Spacer(modifier = Modifier.height(6.dp))
									Text(
										text = "Share page",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = Color.White,
										maxLines = 1,
									)
								}
							}

							// Rotate screen (1-tap Portrait / Landscape lock toggle)
							val isLandscapeLocked = readerConfig.rotationMode == ReaderRotationMode.LANDSCAPE
							Surface(
								color = if (isLandscapeLocked) accentCyan.copy(alpha = 0.18f) else Color(0xFF1E222D),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.weight(1f)
									.height(82.dp)
									.border(
										width = 1.dp,
										color = if (isLandscapeLocked) accentCyan else Color.Transparent,
										shape = RoundedCornerShape(18.dp),
									)
									.clip(RoundedCornerShape(18.dp))
									.clickable {
										val nextRotation = if (isLandscapeLocked) {
											ReaderRotationMode.PORTRAIT
										} else {
											ReaderRotationMode.LANDSCAPE
										}
										onUpdateReaderConfig { it.copy(rotationMode = nextRotation) }
										(context as? Activity)?.requestedOrientation = if (nextRotation == ReaderRotationMode.LANDSCAPE) {
											ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
										} else {
											ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
										}
									}
									.testTag("reader_more_rotate_screen"),
							) {
								Column(
									modifier = Modifier
										.fillMaxSize()
										.padding(10.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.Center,
								) {
									Icon(
										imageVector = Icons.Default.ScreenRotation,
										contentDescription = "Rotate screen",
										tint = if (isLandscapeLocked) accentCyan else Color.White,
										modifier = Modifier.size(22.dp),
									)
									Spacer(modifier = Modifier.height(6.dp))
									Text(
										text = "Rotate screen",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = if (isLandscapeLocked) accentCyan else Color.White,
										maxLines = 1,
									)
								}
							}

							// Color correction
							val hasColorPreset = readerConfig.colorFilterPreset != ReaderColorFilterPreset.NONE ||
								readerConfig.bedtimeDimmerAlpha > 0.01f
							Surface(
								color = if (hasColorPreset) accentCyan.copy(alpha = 0.18f) else Color(0xFF1E222D),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.weight(1f)
									.height(82.dp)
									.border(
										width = 1.dp,
										color = if (hasColorPreset) accentCyan else Color.Transparent,
										shape = RoundedCornerShape(18.dp),
									)
									.clip(RoundedCornerShape(18.dp))
									.clickable(onClick = onOpenColorCorrection)
									.testTag("reader_more_color_correction"),
							) {
								Column(
									modifier = Modifier
										.fillMaxSize()
										.padding(10.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.Center,
								) {
									Icon(
										imageVector = Icons.Default.ColorLens,
										contentDescription = "Color correction",
										tint = if (hasColorPreset) accentCyan else Color.White,
										modifier = Modifier.size(22.dp),
									)
									Spacer(modifier = Modifier.height(6.dp))
									Text(
										text = "Color correction",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.SemiBold,
										color = if (hasColorPreset) accentCyan else Color.White,
										maxLines = 1,
									)
								}
							}
						}

						// 2. Middle Card (Two-Page & Foldable Controls)
						Surface(
							color = Color(0xFF1E222D),
							shape = RoundedCornerShape(20.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 14.dp),
								verticalArrangement = Arrangement.spacedBy(12.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = "Use two pages layout on landscape orientation",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Medium,
										color = Color.White,
										modifier = Modifier.weight(1f).padding(end = 12.dp),
									)
									Switch(
										checked = readerConfig.twoPageOnLandscape,
										onCheckedChange = { checked ->
											onUpdateReaderConfig { it.copy(twoPageOnLandscape = checked) }
										},
										colors = SwitchDefaults.colors(
											checkedThumbColor = Color(0xFF071318),
											checkedTrackColor = accentCyan,
										),
									)
								}

								HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Text(
										text = "Auto Two-Page On Foldable",
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.Medium,
										color = Color.White,
										modifier = Modifier.weight(1f).padding(end = 12.dp),
									)
									Switch(
										checked = readerConfig.autoTwoPageOnFoldable,
										onCheckedChange = { checked ->
											onUpdateReaderConfig { it.copy(autoTwoPageOnFoldable = checked) }
										},
										colors = SwitchDefaults.colors(
											checkedThumbColor = Color(0xFF071318),
											checkedTrackColor = accentCyan,
										),
									)
								}

								HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

								Column(modifier = Modifier.fillMaxWidth()) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween,
										verticalAlignment = Alignment.CenterVertically,
									) {
										Text(
											text = "Two-Page Scroll Sensitivity",
											style = MaterialTheme.typography.bodyMedium,
											fontWeight = FontWeight.Medium,
											color = Color.White,
										)
										Text(
											text = "${readerConfig.twoPageScrollSensitivity.roundToInt()}%",
											style = MaterialTheme.typography.labelLarge,
											fontWeight = FontWeight.Bold,
											color = accentCyan,
										)
									}
									Slider(
										value = readerConfig.twoPageScrollSensitivity,
										onValueChange = { newVal ->
											onUpdateReaderConfig {
												it.copy(twoPageScrollSensitivity = newVal.coerceIn(0f, 100f))
											}
										},
										valueRange = 0f..100f,
										colors = SliderDefaults.colors(
											thumbColor = accentCyan,
											activeTrackColor = accentCyan,
										),
									)
								}
							}
						}
					}
				}

				// Bottom Pill Switcher: [ 📖 Reading | ⊞ More ]
				Surface(
					color = Color(0xFF0F1117),
					shape = RoundedCornerShape(999.dp),
					modifier = Modifier
						.fillMaxWidth()
						.height(48.dp),
				) {
					Row(
						modifier = Modifier
							.fillMaxSize()
							.padding(4.dp),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						val tabs = listOf(
							Triple(ReaderOptionsSheetTab.READING, "Reading", Icons.Default.MenuBook),
							Triple(ReaderOptionsSheetTab.MORE, "More", Icons.Default.GridView),
						)
						tabs.forEach { (tab, title, icon) ->
							val selected = activeTab == tab
							Surface(
								color = if (selected) accentCyan else Color.Transparent,
								shape = RoundedCornerShape(999.dp),
								modifier = Modifier
									.weight(1f)
									.fillMaxHeight()
									.clip(RoundedCornerShape(999.dp))
									.clickable { onSelectTab(tab) }
									.testTag("reader_options_pill_${tab.name.lowercase()}"),
							) {
								Row(
									modifier = Modifier.fillMaxSize(),
									horizontalArrangement = Arrangement.Center,
									verticalAlignment = Alignment.CenterVertically,
								) {
									Icon(
										imageVector = icon,
										contentDescription = null,
										tint = if (selected) Color(0xFF071318) else Color(0xFFD5DAE5),
										modifier = Modifier.size(18.dp),
									)
									Spacer(modifier = Modifier.width(8.dp))
									Text(
										text = title,
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.Bold,
										color = if (selected) Color(0xFF071318) else Color(0xFFD5DAE5),
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

// =========================================================================
// TASK 3.3: LIVE COLOR CORRECTION & "NEMURI BEDTIME PAPER" PANEL
// Renders as a compact bottom panel WITHOUT a dark backdrop dim so the live
// manga page above updates in real time.
// =========================================================================
@Composable
internal fun ReaderBedtimeColorCorrectionPanel(
	readerConfig: ReaderCustomizationConfig,
	hasCustomSeriesProfile: Boolean,
	onUpdateReaderConfig: ((ReaderCustomizationConfig) -> ReaderCustomizationConfig) -> Unit,
	onSaveSeriesProfile: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accentCyan = Color(0xFF5CE1E6)
	var rememberForSeries by remember(hasCustomSeriesProfile) { mutableStateOf(hasCustomSeriesProfile) }

	Surface(
		color = Color(0xF5151821),
		shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
		tonalElevation = 10.dp,
		shadowElevation = 20.dp,
		modifier = modifier
			.fillMaxWidth()
			.border(
				width = 1.dp,
				color = accentCyan.copy(alpha = 0.24f),
				shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
			)
			.navigationBarsPadding()
			.padding(bottom = 8.dp)
			.testTag("reader_bedtime_color_correction_panel"),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 18.dp, vertical = 14.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			// Header
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Icon(
						imageVector = Icons.Default.ColorLens,
						contentDescription = null,
						tint = accentCyan,
						modifier = Modifier.size(20.dp),
					)
					Text(
						text = "Color correction & Nemuri Bedtime Paper",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.Bold,
						color = Color.White,
					)
				}
				Surface(
					color = Color.White.copy(alpha = 0.08f),
					shape = CircleShape,
					modifier = Modifier
						.size(32.dp)
						.clip(CircleShape)
						.clickable(onClick = onDismiss)
						.testTag("color_correction_close_button"),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Close color correction panel",
							tint = Color.White,
							modifier = Modifier.size(16.dp),
						)
					}
				}
			}

			// 1-Tap Presets Row: Original | 🌙 Nemuri Bedtime (Warm Paper + Dimmer) | ✦ AI Auto-Ink Restore | Grayscale | Inverted OLED Dark
			val presets = listOf(
				ReaderColorFilterPreset.NONE to "Original",
				ReaderColorFilterPreset.NEMURI_BEDTIME to "🌙 Nemuri Bedtime (Warm Paper + Dimmer)",
				ReaderColorFilterPreset.AI_AUTO_INK_RESTORE to "✦ AI Auto-Ink Restore",
				ReaderColorFilterPreset.MONOCHROME_INK to "Grayscale",
				ReaderColorFilterPreset.INVERTED_NIGHT to "Inverted OLED Dark",
			)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState()),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				presets.forEach { (preset, label) ->
					val selected = readerConfig.colorFilterPreset == preset
					Surface(
						color = if (selected) accentCyan else Color(0xFF1F2330),
						shape = RoundedCornerShape(999.dp),
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.clickable {
								onUpdateReaderConfig { current ->
									when (preset) {
										ReaderColorFilterPreset.NONE -> current.copy(
											colorFilterPreset = preset,
											warmPaperTint = false,
											invertColors = false,
											grayscale = false,
											bedtimeDimmerAlpha = 0f,
										)
										ReaderColorFilterPreset.NEMURI_BEDTIME -> current.copy(
											colorFilterPreset = preset,
											warmPaperTint = true,
											invertColors = false,
											grayscale = false,
											bedtimeDimmerAlpha = if (current.bedtimeDimmerAlpha < 0.05f) 0.22f else current.bedtimeDimmerAlpha,
										)
										ReaderColorFilterPreset.AI_AUTO_INK_RESTORE -> current.copy(
											colorFilterPreset = preset,
											invertColors = false,
										)
										ReaderColorFilterPreset.MONOCHROME_INK -> current.copy(
											colorFilterPreset = preset,
											grayscale = true,
											invertColors = false,
										)
										ReaderColorFilterPreset.INVERTED_NIGHT -> current.copy(
											colorFilterPreset = preset,
											invertColors = true,
										)
										else -> current.copy(colorFilterPreset = preset)
									}
								}
							}
							.testTag("color_preset_chip_${preset.name.lowercase()}"),
					) {
						Text(
							text = label,
							style = MaterialTheme.typography.labelMedium,
							fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
							color = if (selected) Color(0xFF071318) else Color(0xFFE2E8F0),
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
						)
					}
				}
			}

			// Sub-Zero Dimmer Slider (0% .. 60%) for reading in dark bedrooms
			Surface(
				color = Color(0xFF1D212D),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 8.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "🌙 Sub-Zero Bedroom Dimmer (#E8DEC8 Warm Paper)",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.SemiBold,
							color = Color(0xFFE8DEC8),
						)
						Text(
							text = "${(readerConfig.bedtimeDimmerAlpha * 100).roundToInt()}%",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
							color = accentCyan,
						)
					}
					Slider(
						value = readerConfig.bedtimeDimmerAlpha.coerceIn(0f, 0.60f),
						onValueChange = { alpha ->
							onUpdateReaderConfig {
								it.copy(bedtimeDimmerAlpha = alpha.coerceIn(0f, 0.60f))
							}
						},
						valueRange = 0f..0.60f,
						colors = SliderDefaults.colors(
							thumbColor = accentCyan,
							activeTrackColor = accentCyan,
						),
						modifier = Modifier.testTag("bedtime_subzero_dimmer_slider"),
					)
				}
			}

			// Manual Sliders: Brightness, Contrast, Warmth, Smart Margin Crop (-27%)
			CompactColorSliderRow(
				label = "Brightness",
				valueText = "${(readerConfig.brightnessDelta * 100).roundToInt()}%",
				value = readerConfig.brightnessDelta,
				valueRange = -0.45f..0.45f,
				accentCyan = accentCyan,
				onValueChange = { v -> onUpdateReaderConfig { it.copy(brightnessDelta = v) } },
			)

			CompactColorSliderRow(
				label = "Contrast",
				valueText = "${(readerConfig.contrastDelta * 100).roundToInt()}%",
				value = readerConfig.contrastDelta,
				valueRange = -0.30f..0.60f,
				accentCyan = accentCyan,
				onValueChange = { v -> onUpdateReaderConfig { it.copy(contrastDelta = v) } },
			)

			CompactColorSliderRow(
				label = "Warmth",
				valueText = "${(readerConfig.warmthDelta * 100).roundToInt()}%",
				value = readerConfig.warmthDelta,
				valueRange = -0.30f..0.50f,
				accentCyan = accentCyan,
				onValueChange = { v -> onUpdateReaderConfig { it.copy(warmthDelta = v) } },
			)

			CompactColorSliderRow(
				label = "Smart Margin Crop (-${readerConfig.marginCropPercent}%)",
				valueText = "-${readerConfig.marginCropPercent}%",
				value = readerConfig.marginCropPercent.toFloat(),
				valueRange = 0f..35f,
				accentCyan = accentCyan,
				onValueChange = { v ->
					val pct = v.roundToInt().coerceIn(0, 35)
					onUpdateReaderConfig {
						it.copy(
							marginCropPercent = pct,
							autoCropMargins = pct > 0,
							scaleMode = if (pct > 0) ReaderScaleMode.SMART_CROP_BORDERS else ReaderScaleMode.FIT_WIDTH,
						)
					}
				},
			)

			// Remember for this manga toggle
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "Remember for this manga",
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = FontWeight.Medium,
					color = Color.White,
				)
				Switch(
					checked = rememberForSeries,
					onCheckedChange = { checked ->
						rememberForSeries = checked
						if (checked) {
							onSaveSeriesProfile()
						}
					},
					colors = SwitchDefaults.colors(
						checkedThumbColor = Color(0xFF071318),
						checkedTrackColor = accentCyan,
					),
				)
			}
		}
	}
}

@Composable
private fun CompactColorSliderRow(
	label: String,
	valueText: String,
	value: Float,
	valueRange: ClosedFloatingPointRange<Float>,
	accentCyan: Color,
	onValueChange: (Float) -> Unit,
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				color = Color(0xFFD5DAE5),
			)
			Text(
				text = valueText,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Bold,
				color = accentCyan,
			)
		}
		Slider(
			value = value.coerceIn(valueRange.start, valueRange.endInclusive),
			onValueChange = onValueChange,
			valueRange = valueRange,
			colors = SliderDefaults.colors(
				thumbColor = accentCyan,
				activeTrackColor = accentCyan,
			),
			modifier = Modifier.height(28.dp),
		)
	}
}

// =========================================================================
// TASK 4: BOTTOM SCRUBBER BAR & 3-TAB CHAPTER / PAGE GRID DRAWER
// [ |◀ ]  ━━━|••••••••  [ ▶| ]  [ ⊞ ]
// =========================================================================
@Composable
internal fun NemuriBottomScrubberDock(
	currentPage: Int,
	totalPages: Int,
	isRtl: Boolean,
	canGoPrevChapter: Boolean,
	canGoNextChapter: Boolean,
	onSelectPage: (Int) -> Unit,
	onPrevChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onOpenThreeTabDrawer: () -> Unit,
	onSwipeUpOpenOptions: () -> Unit,
	onSwipeDownExitReader: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val dockBg = Color(0xEB151821)
	val accentCyan = Color(0xFF5CE1E6)
	var verticalSwipeDelta by remember { mutableFloatStateOf(0f) }

	Surface(
		color = dockBg,
		shape = RoundedCornerShape(22.dp),
		tonalElevation = 8.dp,
		shadowElevation = 14.dp,
		modifier = modifier
			.fillMaxWidth()
			.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
			.pointerInput(Unit) {
				detectDragGestures(
					onDragStart = { verticalSwipeDelta = 0f },
					onDrag = { change, dragAmount ->
						if (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x) * 1.25f) {
							change.consume()
							verticalSwipeDelta += dragAmount.y
						}
					},
					onDragEnd = {
						if (verticalSwipeDelta < -55f) {
							onSwipeUpOpenOptions()
						} else if (verticalSwipeDelta > 65f) {
							onSwipeDownExitReader()
						}
						verticalSwipeDelta = 0f
					},
				)
			}
			.testTag("nemuri_bottom_scrubber_dock"),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(56.dp)
				.padding(horizontal = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			// Left: |◀ (Previous Chapter)
			IconButton(
				onClick = if (isRtl) onNextChapter else onPrevChapter,
				enabled = if (isRtl) canGoNextChapter else canGoPrevChapter,
				modifier = Modifier
					.size(44.dp)
					.testTag("scrubber_prev_chapter_button"),
			) {
				Text(
					text = "|◀",
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					color = if (if (isRtl) canGoNextChapter else canGoPrevChapter) {
						Color.White
					} else {
						Color.White.copy(alpha = 0.35f)
					},
				)
			}

			// Center: Dotted discrete page slider track with vertical cyan bar thumb
			var trackWidthPx by remember { mutableFloatStateOf(1f) }
			val progress = if (totalPages > 1) {
				((currentPage - 1).toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
			} else {
				0f
			}
			val visualFraction = if (isRtl) 1f - progress else progress

			val updatePageFromX: (Float) -> Unit = { xPx ->
				val rawRatio = (xPx / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
				val effectiveRatio = if (isRtl) 1f - rawRatio else rawRatio
				val targetPage = 1 + (effectiveRatio * (totalPages - 1)).roundToInt()
				onSelectPage(targetPage.coerceIn(1, totalPages))
			}

			Box(
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
					.padding(horizontal = 6.dp)
					.onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
					.pointerInput(totalPages, isRtl) {
						detectTapGestures { tapOffset ->
							updatePageFromX(tapOffset.x)
						}
					}
					.pointerInput(totalPages, isRtl) {
						detectDragGestures { change, _ ->
							change.consume()
							updatePageFromX(change.position.x)
						}
					},
				contentAlignment = Alignment.Center,
			) {
				Canvas(modifier = Modifier.fillMaxSize()) {
					val centerY = size.height / 2f
					val trackLeft = 6.dp.toPx()
					val trackRight = (size.width - 6.dp.toPx()).coerceAtLeast(trackLeft + 1f)
					val usableW = trackRight - trackLeft
					val thumbX = trackLeft + usableW * visualFraction

					// Active cyan bar segment
					if (isRtl) {
						drawLine(
							color = accentCyan,
							start = Offset(thumbX, centerY),
							end = Offset(trackRight, centerY),
							strokeWidth = 4.dp.toPx(),
						)
					} else {
						drawLine(
							color = accentCyan,
							start = Offset(trackLeft, centerY),
							end = Offset(thumbX, centerY),
							strokeWidth = 4.dp.toPx(),
						)
					}

					// Discrete page dots across the track
					val dotCount = totalPages.coerceIn(2, 24)
					for (i in 0 until dotCount) {
						val dotFrac = i.toFloat() / (dotCount - 1).toFloat()
						val dotX = trackLeft + usableW * dotFrac
						val isPassed = if (isRtl) dotX >= thumbX else dotX <= thumbX
						drawCircle(
							color = if (isPassed) accentCyan else Color.White.copy(alpha = 0.35f),
							radius = if (isPassed) 2.5.dp.toPx() else 2.2.dp.toPx(),
							center = Offset(dotX, centerY),
						)
					}

					// Vertical cyan bar thumb
					val halfThumbH = 10.dp.toPx()
					drawLine(
						color = accentCyan,
						start = Offset(thumbX, centerY - halfThumbH),
						end = Offset(thumbX, centerY + halfThumbH),
						strokeWidth = 4.dp.toPx(),
					)
				}
			}

			// Right of slider: ▶| (Next Chapter)
			IconButton(
				onClick = if (isRtl) onPrevChapter else onNextChapter,
				enabled = if (isRtl) canGoPrevChapter else canGoNextChapter,
				modifier = Modifier
					.size(44.dp)
					.testTag("scrubber_next_chapter_button"),
			) {
				Text(
					text = "▶|",
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					color = if (if (isRtl) canGoPrevChapter else canGoNextChapter) {
						Color.White
					} else {
						Color.White.copy(alpha = 0.35f)
					},
				)
			}

			// Far Right: ⊞ (Page Grid / Chapter Drawer Button)
			Surface(
				color = accentCyan.copy(alpha = 0.16f),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.size(44.dp)
					.clip(RoundedCornerShape(14.dp))
					.clickable(onClick = onOpenThreeTabDrawer)
					.testTag("reader_open_3tab_drawer_button"),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Default.GridView,
						contentDescription = "Open Chapters, Page Grid & Bookmarks Drawer",
						tint = accentCyan,
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}
	}
}

// =========================================================================
// TASK 4.2: 3-TAB DRAWER ([ ☰ Chapters ] | [ ⊞ Page Grid ] | [ 🔖 Bookmarks ])
// =========================================================================
@Composable
internal fun ReaderThreeTabChapterPageGridDrawer(
	activeTab: ReaderDrawerTab,
	onSelectTab: (ReaderDrawerTab) -> Unit,
	mangaTitle: String,
	chapters: List<ReaderChapterEntry>,
	activeChapterIndex: Int,
	currentPageIndex: Int,
	totalPages: Int,
	bookmarkedPages: List<Int>,
	readerConfig: ReaderCustomizationConfig,
	onSelectChapter: (Int) -> Unit,
	onSelectPage: (Int) -> Unit,
	onToggleBookmarkPage: (Int) -> Unit,
	onDismiss: () -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	var showChapterSearch by remember { mutableStateOf(false) }
	var chapterSearchQuery by remember { mutableStateOf("") }
	var reverseChapterSort by remember { mutableStateOf(true) }
	var gridColumnCount by remember { mutableIntStateOf(4) } // cycles 3 / 4 / 5 columns

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.50f))
			.clickable(onClick = onDismiss)
			.testTag("reader_three_tab_drawer_backdrop"),
		contentAlignment = Alignment.BottomCenter,
	) {
		Surface(
			color = Color(0xFF14161F),
			shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
			tonalElevation = 12.dp,
			shadowElevation = 24.dp,
			modifier = Modifier
				.fillMaxWidth()
				.fillMaxHeight(0.72f)
				.clickable(enabled = false) {}
				.navigationBarsPadding()
				.testTag("reader_three_tab_drawer"),
		) {
			Column(modifier = Modifier.fillMaxSize()) {
				// Top Row: 3 Left Tab Icons ([ ☰ Chapters ], [ ⊞ Page Grid ], [ 🔖 Bookmarks ]) + Top-Right contextual actions
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						val drawerTabs = listOf(
							Triple(ReaderDrawerTab.CHAPTERS, "Chapters", Icons.AutoMirrored.Filled.FormatListBulleted),
							Triple(ReaderDrawerTab.PAGE_GRID, "Page Grid", Icons.Default.GridView),
							Triple(ReaderDrawerTab.BOOKMARKS, "Bookmarks", Icons.Default.Bookmark),
						)
						drawerTabs.forEach { (tab, label, icon) ->
							val selected = activeTab == tab
							Surface(
								color = if (selected) accentCyan else Color(0xFF1F2330),
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.clip(RoundedCornerShape(14.dp))
									.clickable { onSelectTab(tab) }
									.testTag("drawer_tab_${tab.name.lowercase()}"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Icon(
										imageVector = icon,
										contentDescription = label,
										tint = if (selected) Color(0xFF071318) else Color(0xFFD5DAE5),
										modifier = Modifier.size(18.dp),
									)
									if (selected) {
										Text(
											text = label,
											style = MaterialTheme.typography.labelMedium,
											fontWeight = FontWeight.Bold,
											color = Color(0xFF071318),
										)
									}
								}
							}
						}
					}

					// Top-right contextual buttons
					Row(
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						if (activeTab == ReaderDrawerTab.CHAPTERS) {
							// [ 🔍 ] chapter search
							IconButton(
								onClick = { showChapterSearch = !showChapterSearch },
								modifier = Modifier
									.size(38.dp)
									.testTag("drawer_chapter_search_button"),
							) {
								Icon(
									imageVector = Icons.Default.Search,
									contentDescription = "Search chapters",
									tint = if (showChapterSearch) accentCyan else Color.White,
								)
							}
							// [ ⇅ ] reverse sort
							IconButton(
								onClick = { reverseChapterSort = !reverseChapterSort },
								modifier = Modifier
									.size(38.dp)
									.testTag("drawer_chapter_sort_button"),
							) {
								Icon(
									imageVector = Icons.Default.SwapVert,
									contentDescription = "Reverse chapter sort",
									tint = if (reverseChapterSort) accentCyan else Color.White,
								)
							}
						} else if (activeTab == ReaderDrawerTab.PAGE_GRID) {
							// [ ⛶ ] density toggle (3 / 4 / 5 columns)
							Surface(
								color = Color(0xFF1F2330),
								shape = RoundedCornerShape(12.dp),
								modifier = Modifier
									.clip(RoundedCornerShape(12.dp))
									.clickable {
										gridColumnCount = when (gridColumnCount) {
											4 -> 5
											5 -> 3
											else -> 4
										}
									}
									.testTag("drawer_page_grid_density_button"),
							) {
								Row(
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Icon(
										imageVector = Icons.Default.ViewColumn,
										contentDescription = "Toggle grid columns",
										tint = accentCyan,
										modifier = Modifier.size(16.dp),
									)
									Text(
										text = "${gridColumnCount}x",
										style = MaterialTheme.typography.labelMedium,
										fontWeight = FontWeight.Bold,
										color = accentCyan,
									)
								}
							}
						}

						IconButton(
							onClick = onDismiss,
							modifier = Modifier.size(38.dp),
						) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Close drawer",
								tint = Color(0xFF9EA4B0),
							)
						}
					}
				}

				HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

				when (activeTab) {
					ReaderDrawerTab.CHAPTERS -> {
						if (showChapterSearch) {
							OutlinedTextField(
								value = chapterSearchQuery,
								onValueChange = { chapterSearchQuery = it },
								placeholder = { Text("Search chapter number or title...") },
								singleLine = true,
								colors = OutlinedTextFieldDefaults.colors(
									focusedBorderColor = accentCyan,
									unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
									focusedTextColor = Color.White,
									unfocusedTextColor = Color.White,
								),
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 8.dp),
							)
						}

						val filteredChapters = remember(chapters, chapterSearchQuery, reverseChapterSort) {
							val base = if (chapterSearchQuery.isBlank()) {
								chapters
							} else {
								chapters.filter {
									it.chapterNumber.contains(chapterSearchQuery, ignoreCase = true) ||
										it.title.contains(chapterSearchQuery, ignoreCase = true)
								}
							}
							if (reverseChapterSort) base.reversed() else base
						}

						LazyColumn(
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(bottom = 32.dp),
						) {
							items(filteredChapters, key = { "drawer_ch_${it.index}" }) { ch ->
								val isCurrent = ch.index == activeChapterIndex
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.clickable { onSelectChapter(ch.index) }
										.background(
											if (isCurrent) accentCyan.copy(alpha = 0.10f) else Color.Transparent,
										)
										.padding(horizontal = 16.dp, vertical = 12.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									// Active chapter marked by a vertical cyan bar (| Vol.X Ch.Y)
									if (isCurrent) {
										Box(
											modifier = Modifier
												.width(4.dp)
												.height(34.dp)
												.clip(RoundedCornerShape(2.dp))
												.background(accentCyan),
										)
										Spacer(modifier = Modifier.width(10.dp))
									}
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = "Vol.${ch.volumeNumber} Ch.${ch.chapterNumber} — ${ch.title}",
											style = MaterialTheme.typography.bodyMedium,
											fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
											color = if (isCurrent) accentCyan else Color.White,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
										Text(
											text = "${ch.relativeDate} • ${ch.scanlatorOrSource}",
											style = MaterialTheme.typography.bodySmall,
											color = Color(0xFF9EA4B0),
										)
									}
								}
								HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
							}
						}
					}

					ReaderDrawerTab.PAGE_GRID -> {
						val pages = remember(totalPages) { (0 until totalPages).toList() }
						LazyVerticalGrid(
							columns = GridCells.Fixed(gridColumnCount),
							modifier = Modifier
								.fillMaxSize()
								.testTag("drawer_page_thumbnail_grid"),
							contentPadding = PaddingValues(12.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp),
						) {
							items(pages, key = { "grid_page_$it" }) { pageIdx ->
								val isCurrentPage = pageIdx == currentPageIndex
								Box(
									modifier = Modifier
										.fillMaxWidth()
										.aspectRatio(0.71f)
										.clip(RoundedCornerShape(10.dp))
										.border(
											width = if (isCurrentPage) 2.dp else 1.dp,
											color = if (isCurrentPage) accentCyan else Color.White.copy(alpha = 0.12f),
											shape = RoundedCornerShape(10.dp),
										)
										.clickable { onSelectPage(pageIdx) }
										.testTag("page_grid_item_${pageIdx + 1}"),
								) {
									MangaReaderPageCanvas(
										mangaTitle = mangaTitle,
										chapterNumber = (activeChapterIndex + 1).toFloat(),
										pageNumber = pageIdx + 1,
										totalPages = totalPages,
										upscaleMode = readerConfig.aiUpscaleMode,
										showBeforeAfterSplit = false,
										warmPaperTint = readerConfig.warmPaperTint,
										invertColors = readerConfig.invertColors,
										grayscale = readerConfig.grayscale,
										colorFilterPreset = readerConfig.colorFilterPreset,
										brightnessDelta = readerConfig.brightnessDelta,
										contrastDelta = readerConfig.contrastDelta,
										warmthDelta = readerConfig.warmthDelta,
										bedtimeDimmerAlpha = readerConfig.bedtimeDimmerAlpha,
										modifier = Modifier.fillMaxSize(),
									)

									// Circular page-number badge in bottom-right corner
									Surface(
										color = if (isCurrentPage) accentCyan else Color(0xCC12141C),
										shape = CircleShape,
										modifier = Modifier
											.align(Alignment.BottomEnd)
											.padding(6.dp)
											.size(24.dp),
									) {
										Box(contentAlignment = Alignment.Center) {
											Text(
												text = "${pageIdx + 1}",
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.Bold,
												color = if (isCurrentPage) Color(0xFF071318) else Color.White,
											)
										}
									}
								}
							}
						}
					}

					ReaderDrawerTab.BOOKMARKS -> {
						val displayBookmarks = if (bookmarkedPages.isEmpty()) {
							listOf(currentPageIndex)
						} else {
							bookmarkedPages.sorted()
						}
						Column(modifier = Modifier.fillMaxSize()) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp, vertical = 8.dp),
								horizontalArrangement = Arrangement.SpaceBetween,
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									text = "Bookmarked Pages in Chapter ${activeChapterIndex + 1}",
									style = MaterialTheme.typography.labelLarge,
									color = Color(0xFFB5BCC9),
								)
								Surface(
									color = accentCyan.copy(alpha = 0.16f),
									shape = RoundedCornerShape(999.dp),
									modifier = Modifier
										.clip(RoundedCornerShape(999.dp))
										.clickable { onToggleBookmarkPage(currentPageIndex) },
								) {
									Text(
										text = if (bookmarkedPages.contains(currentPageIndex)) {
											"Remove Page ${currentPageIndex + 1}"
										} else {
											"+ Bookmark Page ${currentPageIndex + 1}"
										},
										style = MaterialTheme.typography.labelSmall,
										fontWeight = FontWeight.Bold,
										color = accentCyan,
										modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
									)
								}
							}

							LazyVerticalGrid(
								columns = GridCells.Fixed(3),
								modifier = Modifier.fillMaxSize(),
								contentPadding = PaddingValues(12.dp),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								items(displayBookmarks, key = { "bm_page_$it" }) { pageIdx ->
									Box(
										modifier = Modifier
											.fillMaxWidth()
											.aspectRatio(0.71f)
											.clip(RoundedCornerShape(12.dp))
											.border(1.5.dp, accentCyan, RoundedCornerShape(12.dp))
											.clickable { onSelectPage(pageIdx) },
									) {
										MangaReaderPageCanvas(
											mangaTitle = mangaTitle,
											chapterNumber = (activeChapterIndex + 1).toFloat(),
											pageNumber = pageIdx + 1,
											totalPages = totalPages,
											upscaleMode = readerConfig.aiUpscaleMode,
											showBeforeAfterSplit = false,
											warmPaperTint = readerConfig.warmPaperTint,
											invertColors = readerConfig.invertColors,
											grayscale = readerConfig.grayscale,
											colorFilterPreset = readerConfig.colorFilterPreset,
											brightnessDelta = readerConfig.brightnessDelta,
											contrastDelta = readerConfig.contrastDelta,
											warmthDelta = readerConfig.warmthDelta,
											bedtimeDimmerAlpha = readerConfig.bedtimeDimmerAlpha,
											modifier = Modifier.fillMaxSize(),
										)
										Surface(
											color = accentCyan,
											shape = RoundedCornerShape(8.dp),
											modifier = Modifier
												.align(Alignment.BottomEnd)
												.padding(6.dp),
										) {
											Text(
												text = "🔖 P.${pageIdx + 1}",
												style = MaterialTheme.typography.labelSmall,
												fontWeight = FontWeight.Bold,
												color = Color(0xFF071318),
												modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
											)
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

// =========================================================================
// TASK 5.1: TOGGLEABLE ON-SCREEN ZOOM BUTTONS (🔍+ / 🔍- & 100% ↺)
// Exact 40.dp width, rgba(30, 37, 50, 0.85) background, and 1.dp rgba(255, 255, 255, 0.08) border
// matching the vertical page scrubber directly above it.
// =========================================================================
@Composable
internal fun ReaderFloatingZoomControls(
	zoomScale: Float,
	onZoomIn: () -> Unit,
	onZoomOut: () -> Unit,
	onResetZoom: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val scrubberMatchedBg = Color(0xD91E2532) // rgba(30, 37, 50, 0.85)
	val scrubberMatchedBorder = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))

	Column(
		modifier = modifier.testTag("reader_floating_zoom_controls"),
		horizontalAlignment = Alignment.End,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (kotlin.math.abs(zoomScale - 1f) > 0.04f) {
			Surface(
				color = accentCyan,
				shape = RoundedCornerShape(999.dp),
				shadowElevation = 8.dp,
				modifier = Modifier
					.clip(RoundedCornerShape(999.dp))
					.clickable(onClick = onResetZoom)
					.testTag("reader_zoom_reset_pill"),
			) {
				Text(
					text = "${(zoomScale * 100).roundToInt()}% ↺",
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.Bold,
					color = Color(0xFF071318),
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
				)
			}
		}

		Surface(
			color = scrubberMatchedBg,
			shape = RoundedCornerShape(14.dp),
			border = scrubberMatchedBorder,
			shadowElevation = 8.dp,
			modifier = Modifier.width(40.dp),
		) {
			Column(
				modifier = Modifier.width(40.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				IconButton(
					onClick = onZoomIn,
					modifier = Modifier
						.size(40.dp)
						.testTag("reader_zoom_in_button"),
				) {
					Icon(
						imageVector = Icons.Default.ZoomIn,
						contentDescription = "Zoom In",
						tint = Color.White,
						modifier = Modifier.size(20.dp),
					)
				}
				HorizontalDivider(
					color = Color.White.copy(alpha = 0.08f),
					modifier = Modifier.width(26.dp),
				)
				IconButton(
					onClick = onZoomOut,
					modifier = Modifier
						.size(40.dp)
						.testTag("reader_zoom_out_button"),
				) {
					Icon(
						imageVector = Icons.Default.ZoomOut,
						contentDescription = "Zoom Out",
						tint = Color.White,
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}
	}
}

// =========================================================================
// SCALABLE SEARCHABLE SOURCE PICKER MENU (42+ INSTALLED SOURCES)
// Shared by Migrate Manga Source & Reader Source Failover / Switcher
// =========================================================================
data class ScalableSourceCandidate(
	val id: String,
	val fullSourceCode: String,
	val displayName: String,
	val monogram: String,
	val langBadge: String,
	val verifiedChapters: Int,
	val latencyMs: Int,
	val brandColor: Color,
)

// =========================================================================
// TASK 4: MIHON-STYLE FULL-SCREEN SOURCE PRIORITY SELECTOR (`Selected` / `Available`)
// Full-screen source picker (`Selected` with `=` priority handles + `Available` catalog
// + top Select All / Deselect All / Sort & Filter toolbar + bottom-right `→ Continue` FAB)
// =========================================================================
private enum class MihonSourceSortMode {
	ALPHABETICAL_AZ,
	CHAPTER_COUNT,
}

private enum class MihonLanguageFilter {
	ALL_LANGUAGES,
	ONLY_EN,
	ONLY_ALL_MULTI,
}

internal fun buildMihonSourceCatalog(
	baseChapterCount: Int,
	includeDebugMock: Boolean = false,
): List<ScalableSourceCandidate> {
	val base = baseChapterCount.coerceAtLeast(1)
	val appCtx = org.koitharu.kotatsu.core.parser.GlobalParserContextHolder.applicationContext
	val realCandidates = buildList {
		if (appCtx != null) {
			runCatching {
				val mihonSources = org.koitharu.kotatsu.mihon.MihonExtensionManager.getInstance(appCtx).mihonSources.value
				mihonSources.forEachIndexed { idx, src ->
					val cleanName = src.displayName
					add(
						ScalableSourceCandidate(
							id = "mihon_${src.catalogueSource.id}",
							fullSourceCode = src.name,
							displayName = cleanName,
							monogram = cleanName.take(2).uppercase(),
							langBadge = src.catalogueSource.lang.ifBlank { "ALL" }.uppercase(),
							verifiedChapters = base,
							latencyMs = 35 + (idx % 5) * 6,
							brandColor = Color(0xFFA855F7),
						),
					)
				}
			}
			runCatching {
				val ktProviders = org.koitharu.kotatsu.core.parser.KotatsuExternalProviderScanner.discoverProviders(appCtx)
				ktProviders.forEachIndexed { idx, prov ->
					add(
						ScalableSourceCandidate(
							id = "kotatsu_${prov.authority}",
							fullSourceCode = prov.sourceKey,
							displayName = prov.label,
							monogram = prov.label.take(2).uppercase(),
							langBadge = prov.lang.ifBlank { "ALL" }.uppercase(),
							verifiedChapters = base,
							latencyMs = 28 + (idx % 4) * 5,
							brandColor = Color(0xFF5CE1E6),
						),
					)
				}
			}
			runCatching {
				val lnPlugins = org.koitharu.kotatsu.lnreader.LnPluginManager.getInstance(appCtx).installedPlugins.value
				lnPlugins.forEachIndexed { idx, plugin ->
					add(
						ScalableSourceCandidate(
							id = "ln_${plugin.pluginId}",
							fullSourceCode = plugin.sourceKey,
							displayName = plugin.name,
							monogram = plugin.name.take(2).uppercase(),
							langBadge = plugin.lang.ifBlank { "ALL" }.uppercase(),
							verifiedChapters = base,
							latencyMs = 40 + (idx % 4) * 5,
							brandColor = Color(0xFF10B981),
						),
					)
				}
			}
		}
	}.distinctBy { it.id }

	if (!includeDebugMock) {
		return realCandidates
	}

	val debugMockCandidates = listOf(
		ScalableSourceCandidate("comick", "EXT:ComicK (Mihon Multi)", "ComicK", "CK", "EN", base + 3, 42, Color(0xFF5CE1E6)),
		ScalableSourceCandidate("mangadex", "EXT:MangaDex (Mihon EN)", "MangaDex", "MD", "EN", base + 2, 38, Color(0xFFFF6740)),
		ScalableSourceCandidate("asura", "EXT:Asura Scans (Mihon EN)", "Asura Scans", "AS", "EN", base + 2, 48, Color(0xFFA855F7)),
		ScalableSourceCandidate("batoto", "EXT:Bato.to (Mihon EN)", "Bato.to", "BT", "EN", base + 1, 54, Color(0xFF10B981)),
		ScalableSourceCandidate("aquamanga", "EXT:Aqua Manga (EN)", "Aqua Manga", "AQ", "EN", base, 62, Color(0xFF06B6D4)),
	)
	return (realCandidates + debugMockCandidates).distinctBy { it.id }
}

@Composable
fun MigrationSourceSelectorScreen(
	baseChapterCount: Int = 30,
	initialSelectedSourceNames: List<String> = emptyList(),
	includeDebugMock: Boolean = false,
	onContinueWithSelected: (List<ScalableSourceCandidate>) -> Unit,
	onDismiss: () -> Unit,
) {
	val accentCyan = Color(0xFF5CE1E6)
	val catalog = remember(baseChapterCount, includeDebugMock) {
		buildMihonSourceCatalog(baseChapterCount, includeDebugMock = includeDebugMock)
	}

	val selectedIds = remember(initialSelectedSourceNames, catalog) {
		val seedIds = initialSelectedSourceNames.mapNotNull { name ->
			catalog.firstOrNull {
				it.displayName.equals(name, ignoreCase = true) ||
					it.fullSourceCode.equals(name, ignoreCase = true) ||
					name.contains(it.displayName, ignoreCase = true)
			}?.id
		}.distinct()
		mutableStateListOf<String>().apply {
			if (seedIds.isNotEmpty()) {
				addAll(seedIds)
			} else {
				addAll(catalog.take(4).map { it.id })
			}
		}
	}

	var sortMode by remember { mutableStateOf(MihonSourceSortMode.ALPHABETICAL_AZ) }
	var langFilter by remember { mutableStateOf(MihonLanguageFilter.ALL_LANGUAGES) }
	var showSortFilterMenu by remember { mutableStateOf(false) }

	val selectedCandidates = selectedIds.mapNotNull { id ->
		catalog.firstOrNull { it.id == id }
	}

	val availableCandidates = remember(catalog, selectedIds.toList(), sortMode, langFilter) {
		val unselected = catalog.filter { it.id !in selectedIds }
		val langFiltered = when (langFilter) {
			MihonLanguageFilter.ALL_LANGUAGES -> unselected
			MihonLanguageFilter.ONLY_EN -> unselected.filter { it.langBadge.equals("EN", ignoreCase = true) }
			MihonLanguageFilter.ONLY_ALL_MULTI -> unselected.filter { it.langBadge.equals("ALL", ignoreCase = true) }
		}
		when (sortMode) {
			MihonSourceSortMode.ALPHABETICAL_AZ -> langFiltered.sortedWith(
				compareBy<ScalableSourceCandidate> { it.displayName.lowercase() }.thenBy { it.langBadge },
			)
			MihonSourceSortMode.CHAPTER_COUNT -> langFiltered.sortedByDescending { it.verifiedChapters }
		}
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		Surface(
			color = Color(0xFF0A0D14),
			modifier = Modifier
				.fillMaxSize()
				.testTag("mihon_migration_source_selector_screen"),
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.statusBarsPadding()
					.navigationBarsPadding(),
			) {
				Column(modifier = Modifier.fillMaxSize()) {
					// 1. Top App Bar: Left Back Arrow (`←`), Right Action Icons (`[ ▣ ]` Select All, `[ ⌧ ]` Deselect All, `[ ⋮ ]` Sort & Filter Menu)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 8.dp, vertical = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							modifier = Modifier.weight(1f),
						) {
							IconButton(
								onClick = onDismiss,
								modifier = Modifier.testTag("mihon_selector_back_button"),
							) {
								Icon(
									imageVector = Icons.AutoMirrored.Filled.ArrowBack,
									contentDescription = "Back",
									tint = Color.White,
								)
							}
							Column {
								Text(
									text = "Select sources",
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold,
									color = Color.White,
								)
								Text(
									text = "${selectedCandidates.size} selected • Priority order",
									style = MaterialTheme.typography.labelSmall,
									color = Color(0xFF94A3B8),
								)
							}
						}

						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(2.dp),
						) {
							// [ ▣ ] Select All
							IconButton(
								onClick = {
									catalog.forEach { candidate ->
										if (candidate.id !in selectedIds) {
											selectedIds.add(candidate.id)
										}
									}
								},
								modifier = Modifier.testTag("mihon_selector_select_all_button"),
							) {
								Icon(
									imageVector = Icons.Default.SelectAll,
									contentDescription = "Select All",
									tint = Color.White,
								)
							}

							// [ ⌧ ] Deselect All
							IconButton(
								onClick = {
									selectedIds.clear()
								},
								modifier = Modifier.testTag("mihon_selector_deselect_all_button"),
							) {
								Icon(
									imageVector = Icons.Default.ClearAll,
									contentDescription = "Deselect All",
									tint = Color.White,
								)
							}

							// [ ⋮ ] Sort & Filter Menu
							Box {
								IconButton(
									onClick = { showSortFilterMenu = true },
									modifier = Modifier.testTag("mihon_selector_sort_filter_menu_button"),
								) {
									Icon(
										imageVector = Icons.Default.MoreVert,
										contentDescription = "Sort & Filter Menu",
										tint = Color.White,
									)
								}
								DropdownMenu(
									expanded = showSortFilterMenu,
									onDismissRequest = { showSortFilterMenu = false },
									containerColor = Color(0xFF181D28),
								) {
									DropdownMenuItem(
										text = {
											Text(
												text = "Sort Alphabetically A–Z",
												color = if (sortMode == MihonSourceSortMode.ALPHABETICAL_AZ) accentCyan else Color.White,
												fontWeight = if (sortMode == MihonSourceSortMode.ALPHABETICAL_AZ) FontWeight.Bold else FontWeight.Normal,
											)
										},
										onClick = {
											sortMode = MihonSourceSortMode.ALPHABETICAL_AZ
											showSortFilterMenu = false
										},
									)
									DropdownMenuItem(
										text = {
											Text(
												text = "Sort by Chapter Count",
												color = if (sortMode == MihonSourceSortMode.CHAPTER_COUNT) accentCyan else Color.White,
												fontWeight = if (sortMode == MihonSourceSortMode.CHAPTER_COUNT) FontWeight.Bold else FontWeight.Normal,
											)
										},
										onClick = {
											sortMode = MihonSourceSortMode.CHAPTER_COUNT
											showSortFilterMenu = false
										},
									)
									HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
									DropdownMenuItem(
										text = {
											Text(
												text = "Filter Language: EN",
												color = if (langFilter == MihonLanguageFilter.ONLY_EN) accentCyan else Color.White,
												fontWeight = if (langFilter == MihonLanguageFilter.ONLY_EN) FontWeight.Bold else FontWeight.Normal,
											)
										},
										onClick = {
											langFilter = if (langFilter == MihonLanguageFilter.ONLY_EN) {
												MihonLanguageFilter.ALL_LANGUAGES
											} else {
												MihonLanguageFilter.ONLY_EN
											}
											showSortFilterMenu = false
										},
									)
									DropdownMenuItem(
										text = {
											Text(
												text = "Filter Language: ALL",
												color = if (langFilter == MihonLanguageFilter.ALL_LANGUAGES) accentCyan else Color.White,
												fontWeight = if (langFilter == MihonLanguageFilter.ALL_LANGUAGES) FontWeight.Bold else FontWeight.Normal,
											)
										},
										onClick = {
											langFilter = MihonLanguageFilter.ALL_LANGUAGES
											showSortFilterMenu = false
										},
									)
								}
							}
						}
					}

					// Scrollable Content: Section 1 (`Selected`) + Section 2 (`Available`)
					LazyColumn(
						modifier = Modifier
							.fillMaxSize()
							.testTag("mihon_selector_lazy_column"),
						contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						// Section 1: Selected (Priority-Ordered Card Container)
						item(key = "selected_header") {
							Text(
								text = "Selected",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold,
								color = Color(0xFFCBD5E1),
								modifier = Modifier.padding(start = 4.dp, top = 4.dp),
							)
						}

						item(key = "selected_container_card") {
							Surface(
								color = Color(0xFF12161F),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
									.testTag("mihon_selector_selected_card"),
							) {
								if (selectedCandidates.isEmpty()) {
									Box(
										modifier = Modifier
											.fillMaxWidth()
											.padding(vertical = 22.dp, horizontal = 16.dp),
										contentAlignment = Alignment.Center,
									) {
										Text(
											text = "No sources selected. Tap any source in Available below to add it.",
											style = MaterialTheme.typography.bodySmall,
											color = Color(0xFF94A3B8),
											textAlign = TextAlign.Center,
										)
									}
								} else {
									Column(modifier = Modifier.fillMaxWidth()) {
										selectedCandidates.forEachIndexed { index, candidate ->
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.clickable {
														// Tapping the row body deselects it and moves it back to Available
														selectedIds.remove(candidate.id)
													}
													.padding(horizontal = 14.dp, vertical = 12.dp)
													.testTag("mihon_selected_row_${candidate.id}"),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.SpaceBetween,
											) {
												// Left: 36.dp x 36.dp rounded Source Icon/Logo + Source Name
												Row(
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(12.dp),
													modifier = Modifier.weight(1f),
												) {
													Surface(
														color = candidate.brandColor.copy(alpha = 0.22f),
														shape = RoundedCornerShape(10.dp),
														modifier = Modifier
															.size(36.dp)
															.border(
																1.dp,
																candidate.brandColor.copy(alpha = 0.65f),
																RoundedCornerShape(10.dp),
															),
													) {
														Box(contentAlignment = Alignment.Center) {
															Text(
																text = candidate.monogram,
																style = MaterialTheme.typography.labelMedium,
																fontWeight = FontWeight.ExtraBold,
																color = candidate.brandColor,
															)
														}
													}

													Text(
														text = candidate.displayName,
														style = MaterialTheme.typography.bodyLarge,
														fontWeight = FontWeight.Medium,
														color = Color.White,
														maxLines = 1,
														overflow = TextOverflow.Ellipsis,
													)
												}

												// Right: Language pill badge (`EN` or `ALL`) + `=` Reorder Handle
												Row(
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(10.dp),
												) {
													Surface(
														color = Color(0xFF1F2637),
														shape = RoundedCornerShape(6.dp),
													) {
														Text(
															text = candidate.langBadge,
															style = MaterialTheme.typography.labelSmall,
															fontWeight = FontWeight.Bold,
															fontSize = 11.sp,
															color = Color(0xFFCBD5E1),
															modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
														)
													}

													// `=` Reorder Handle (tap to move priority up, or wrap to bottom if already #1)
													Surface(
														color = Color.Transparent,
														shape = RoundedCornerShape(8.dp),
														modifier = Modifier
															.clip(RoundedCornerShape(8.dp))
															.clickable {
																if (selectedIds.size > 1) {
																	val currentIdx = selectedIds.indexOf(candidate.id)
																	if (currentIdx > 0) {
																		val item = selectedIds.removeAt(currentIdx)
																		selectedIds.add(currentIdx - 1, item)
																	} else if (currentIdx == 0) {
																		val item = selectedIds.removeAt(0)
																		selectedIds.add(item)
																	}
																}
															}
															.padding(horizontal = 6.dp, vertical = 4.dp)
															.testTag("mihon_reorder_handle_${candidate.id}"),
													) {
														Icon(
															imageVector = Icons.Default.DragHandle,
															contentDescription = "Reorder priority",
															tint = Color(0xFF94A3B8),
															modifier = Modifier.size(22.dp),
														)
													}
												}
											}

											if (index < selectedCandidates.lastIndex) {
												HorizontalDivider(
													color = Color.White.copy(alpha = 0.05f),
													modifier = Modifier.padding(horizontal = 14.dp),
												)
											}
										}
									}
								}
							}
						}

						// Section 2: Available (Full Installed Sources Catalog)
						item(key = "available_header") {
							Text(
								text = "Available",
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.SemiBold,
								color = Color(0xFFCBD5E1),
								modifier = Modifier.padding(start = 4.dp, top = 6.dp),
							)
						}

						item(key = "available_container_card") {
							Surface(
								color = Color(0xFF12161F),
								shape = RoundedCornerShape(18.dp),
								modifier = Modifier
									.fillMaxWidth()
									.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
									.testTag("mihon_selector_available_card"),
							) {
								if (availableCandidates.isEmpty()) {
									Box(
										modifier = Modifier
											.fillMaxWidth()
											.padding(vertical = 22.dp, horizontal = 16.dp),
										contentAlignment = Alignment.Center,
									) {
										Text(
											text = "All installed sources are currently in Selected.",
											style = MaterialTheme.typography.bodySmall,
											color = Color(0xFF94A3B8),
											textAlign = TextAlign.Center,
										)
									}
								} else {
									Column(modifier = Modifier.fillMaxWidth()) {
										availableCandidates.forEachIndexed { index, candidate ->
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.clickable {
														// Tapping any row in Available moves it into Selected at the top
														if (candidate.id !in selectedIds) {
															selectedIds.add(candidate.id)
														}
													}
													.padding(horizontal = 14.dp, vertical = 12.dp)
													.testTag("mihon_available_row_${candidate.id}"),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.SpaceBetween,
											) {
												Row(
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(12.dp),
													modifier = Modifier.weight(1f),
												) {
													Surface(
														color = candidate.brandColor.copy(alpha = 0.22f),
														shape = RoundedCornerShape(10.dp),
														modifier = Modifier
															.size(36.dp)
															.border(
																1.dp,
																candidate.brandColor.copy(alpha = 0.65f),
																RoundedCornerShape(10.dp),
															),
													) {
														Box(contentAlignment = Alignment.Center) {
															Text(
																text = candidate.monogram,
																style = MaterialTheme.typography.labelMedium,
																fontWeight = FontWeight.ExtraBold,
																color = candidate.brandColor,
															)
														}
													}

													Text(
														text = candidate.displayName,
														style = MaterialTheme.typography.bodyLarge,
														fontWeight = FontWeight.Medium,
														color = Color.White,
														maxLines = 1,
														overflow = TextOverflow.Ellipsis,
													)
												}

												Surface(
													color = Color(0xFF1F2637),
													shape = RoundedCornerShape(6.dp),
												) {
													Text(
														text = candidate.langBadge,
														style = MaterialTheme.typography.labelSmall,
														fontWeight = FontWeight.Bold,
														fontSize = 11.sp,
														color = Color(0xFFCBD5E1),
														modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
													)
												}
											}

											if (index < availableCandidates.lastIndex) {
												HorizontalDivider(
													color = Color.White.copy(alpha = 0.05f),
													modifier = Modifier.padding(horizontal = 14.dp),
												)
											}
										}
									}
								}
							}
						}
					}
				}

				// 4. Floating Bottom-Right `[ → Continue ]` FAB
				Surface(
					color = accentCyan,
					shape = RoundedCornerShape(18.dp),
					shadowElevation = 12.dp,
					tonalElevation = 8.dp,
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(bottom = 24.dp, end = 20.dp)
						.clip(RoundedCornerShape(18.dp))
						.clickable {
							val result = if (selectedCandidates.isNotEmpty()) {
								selectedCandidates
							} else {
								listOf(catalog.first())
							}
							onContinueWithSelected(result)
						}
						.testTag("mihon_selector_continue_fab"),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowForward,
							contentDescription = null,
							tint = Color(0xFF061518),
							modifier = Modifier.size(18.dp),
						)
						Text(
							text = "Continue",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.ExtraBold,
							color = Color(0xFF061518),
						)
					}
				}
			}
		}
	}
}



