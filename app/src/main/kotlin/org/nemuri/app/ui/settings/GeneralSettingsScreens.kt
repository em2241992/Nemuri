package org.nemuri.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.core.network.DoHProvider
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.prefs.CoverAspectRatio
import org.koitharu.kotatsu.core.prefs.DownloadFormat
import org.koitharu.kotatsu.core.prefs.GridGutterDensity
import org.koitharu.kotatsu.core.prefs.MangaCardStyle
import org.koitharu.kotatsu.core.prefs.NavLabelVisibility
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.prefs.ReaderTapZoneMode
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.koitharu.kotatsu.core.prefs.TrackerDownloadStrategy
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.reader.domain.NeuralAccelerator
import org.koitharu.kotatsu.reader.domain.ReaderShaderPreset
import org.koitharu.kotatsu.reader.domain.UpscaleEffect
import org.nemuri.app.BuildConfig
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.components.cornerSafeBadgeInset
import org.nemuri.app.ui.components.cornerSafeBottomPadding
import org.nemuri.app.ui.components.cornerSafeHorizontalPadding
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriSpatialSpring
import java.net.Proxy
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val schemes = remember { ColorScheme.getAvailableList() }

	SettingsLazyPage(
		title = SettingsSection.APPEARANCE.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Theme Mode") {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					listOf(
						AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to "System",
						AppCompatDelegate.MODE_NIGHT_NO to "Light",
						AppCompatDelegate.MODE_NIGHT_YES to "Dark",
					).forEach { (mode, label) ->
						val selected = state.themeMode == mode
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.setThemeMode(mode) },
						) {
							Text(
								text = label,
								style = MaterialTheme.typography.labelLarge,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.padding(vertical = 10.dp),
								textAlign = androidx.compose.ui.text.style.TextAlign.Center,
							)
						}
					}
				}
				SettingsRowDivider()
				SettingsToggleRow(
					title = "AMOLED Pure Black",
					subtitle = "Use true #000000 backgrounds in dark mode",
					checked = state.isAmoled,
					onCheckedChange = viewModel::setAmoled,
				)
			}
		}

		item {
			SettingsGroupCard("Accent Color Scheme") {
				FlowRow(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					schemes.forEach { scheme ->
						val selected = state.colorScheme == scheme
						val dotColor = colorSchemeSwatchColor(scheme)
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
							modifier = Modifier
								.clip(RoundedCornerShape(12.dp))
								.clickable { viewModel.setColorScheme(scheme) },
						) {
							Row(
								modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Box(
									modifier = Modifier
										.size(12.dp)
										.clip(CircleShape)
										.background(dotColor),
								)
								Text(
									text = stringResource(scheme.titleResId),
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								)
							}
						}
					}
				}
			}
		}

		item {
			SettingsGroupCard("Live Studio Preview") {
				AppearanceLivePreview(state = state)
			}
		}

		item {
			SettingsGroupCard("Library & Browse Cards") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = "Card Style",
						style = MaterialTheme.typography.labelLarge,
						color = NemuriTheme.colors.textPrimary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						MangaCardStyle.entries.forEach { style ->
							val selected = state.mangaCardStyle == style
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(12.dp))
									.clickable { viewModel.setMangaCardStyle(style) },
							) {
								Text(
									text = when (style) {
										MangaCardStyle.COMPACT -> "Compact"
										MangaCardStyle.COMFORTABLE -> "Comfortable"
										MangaCardStyle.DETAILED_LIST -> "List Row"
									},
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 10.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}
				}
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Corner Radius",
					subtitle = "Binds corner-safe title padding mathematically to rounded card edges",
					valueLabel = "${state.cardCornerRadiusDp} dp",
					value = state.cardCornerRadiusDp.toFloat(),
					valueRange = 0f..24f,
					steps = 11,
					onValueChange = { viewModel.setCardCornerRadiusDp(it.roundToInt()) },
				)
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Portrait Columns",
					subtitle = "Pinch the Library grid with two fingers anytime to resize live",
					valueLabel = "${state.gridColumnsPortrait} cols",
					value = state.gridColumnsPortrait.toFloat(),
					valueRange = 2f..6f,
					steps = 3,
					onValueChange = { viewModel.setGridColumnsPortrait(it.roundToInt()) },
				)
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Landscape Columns",
					subtitle = "Columns shown in landscape or tablet orientation",
					valueLabel = "${state.gridColumnsLandscape} cols",
					value = state.gridColumnsLandscape.toFloat(),
					valueRange = 4f..9f,
					steps = 4,
					onValueChange = { viewModel.setGridColumnsLandscape(it.roundToInt()) },
				)
				SettingsRowDivider()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = "Grid Spacing & Cover Aspect",
						style = MaterialTheme.typography.labelLarge,
						color = NemuriTheme.colors.textPrimary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						GridGutterDensity.entries.forEach { density ->
							val selected = state.gridGutterDensity == density
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setGridGutterDensity(density) },
							) {
								Text(
									text = density.label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 8.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						CoverAspectRatio.entries.forEach { ratio ->
							val selected = state.coverAspectRatio == ratio
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setCoverAspectRatio(ratio) },
							) {
								Text(
									text = ratio.label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 8.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}
				}
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Interface Density Scale",
					subtitle = "Scales app typography spacing and controls globally",
					valueLabel = "${state.uiScalePercent}%",
					value = state.uiScalePercent.toFloat(),
					valueRange = 85f..115f,
					steps = 5,
					onValueChange = { viewModel.setUiScale(it.roundToInt()) },
				)
			}
		}

		item {
			SettingsGroupCard("Card Badges & Indicators") {
				SettingsToggleRow(
					title = "Show Unread / Caught-Up Badge",
					checked = state.showUnreadBadge,
					onCheckedChange = viewModel::setShowUnreadBadge,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Show Downloaded Badge",
					checked = state.showDownloadedBadge,
					onCheckedChange = viewModel::setShowDownloadedBadge,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Show Source Name Badge",
					checked = state.showSourceBadge,
					onCheckedChange = viewModel::setShowSourceBadge,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Show Reading Progress Bar",
					checked = state.showBottomProgressBar,
					onCheckedChange = viewModel::setShowBottomProgressBar,
				)
			}
		}

		item {
			SettingsGroupCard("Navigation Bar & Gestures") {
				SettingsToggleRow(
					title = "Compact Navigation Dock",
					subtitle = "Reduces floating dock pill height for extra vertical screen space",
					checked = state.isNavBarCompact,
					onCheckedChange = viewModel::setNavBarCompact,
				)
				SettingsRowDivider()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = "Navigation Label Visibility",
						style = MaterialTheme.typography.labelLarge,
						color = NemuriTheme.colors.textPrimary,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						NavLabelVisibility.entries.forEach { vis ->
							val selected = state.navLabelVisibility == vis
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setNavLabelVisibility(vis) },
							) {
								Text(
									text = vis.label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 8.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}
				}
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Horizontal Row Swipe Actions",
					subtitle = "Swipe Feed entries & Chapter rows right to mark read or left to download",
					checked = state.isSwipeActionsEnabled,
					onCheckedChange = viewModel::setSwipeActionsEnabled,
				)
			}
		}
	}
}

@Composable
private fun AppearanceLivePreview(state: SettingsUiState) {
	val corner = state.cardCornerRadiusDp.coerceIn(0, 24)
	val shape = RoundedCornerShape(corner.dp)
	val hInset = cornerSafeHorizontalPadding(corner)
	val bInset = cornerSafeBottomPadding(corner)
	val badgeInset = cornerSafeBadgeInset(corner)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(16.dp)
			.animateContentSize(nemuriSpatialSpring()),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = "${state.mangaCardStyle.label} • ${state.gridColumnsPortrait} cols • ${corner}dp corner • ${state.coverAspectRatio.label}",
			style = MaterialTheme.typography.labelSmall,
			color = NemuriTheme.colors.cyan,
		)
		if (state.mangaCardStyle == MangaCardStyle.DETAILED_LIST) {
			Surface(
				color = NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(corner.coerceIn(8, 18).dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.border),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier.padding(10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Box(
						modifier = Modifier
							.size(width = 50.dp, height = 70.dp)
							.clip(RoundedCornerShape((corner - 2).coerceIn(4, 14).dp))
							.background(
								Brush.linearGradient(
									listOf(
										NemuriTheme.colors.tealContainer,
										NemuriTheme.colors.cyan.copy(alpha = 0.35f),
									),
								),
							),
					)
					Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(
							text = "A Long Chronicle of the Corner-Safe Library Card",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							text = "MangaDex • Action, Fantasy",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
						if (state.showBottomProgressBar) {
							LinearProgressIndicator(
								progress = { 0.65f },
								color = NemuriTheme.colors.cyan,
								trackColor = NemuriTheme.colors.border,
								modifier = Modifier.fillMaxWidth().height(3.dp),
							)
						}
					}
					if (state.showDownloadedBadge) {
						Icon(Icons.Default.CloudDone, contentDescription = null, tint = NemuriTheme.colors.healthy, modifier = Modifier.size(16.dp))
					}
					if (state.showUnreadBadge) {
						Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
							NonWrappingBadgeText(
								text = "14",
								color = MaterialTheme.colorScheme.onPrimary,
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
							)
						}
					}
				}
			}
		} else {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.Center,
			) {
				Column(
					modifier = Modifier.width(126.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(state.coverAspectRatio.ratio)
							.clip(shape)
							.border(1.dp, NemuriTheme.colors.border, shape)
							.background(
								Brush.linearGradient(
									listOf(
										NemuriTheme.colors.surfaceElevated,
										NemuriTheme.colors.tealContainer,
									),
								),
							),
					) {
						Row(
							modifier = Modifier
								.align(Alignment.TopStart)
								.padding(badgeInset),
						) {
							if (state.showUnreadBadge) {
								Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
									NonWrappingBadgeText(
										text = "14",
										color = MaterialTheme.colorScheme.onPrimary,
										modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
									)
								}
							}
						}
						Row(
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(badgeInset),
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							if (state.showDownloadedBadge) {
								Surface(color = Color.Black.copy(alpha = 0.65f), shape = CircleShape, modifier = Modifier.size(18.dp)) {
									Box(contentAlignment = Alignment.Center) {
										Icon(Icons.Default.CloudDone, null, tint = NemuriTheme.colors.healthy, modifier = Modifier.size(11.dp))
									}
								}
							}
							if (state.showSourceBadge) {
								Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(6.dp)) {
									NonWrappingBadgeText(
										text = "MD",
										color = NemuriTheme.colors.cyan,
										fontSizeSp = 8.5f,
										modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
									)
								}
							}
						}
						if (state.mangaCardStyle == MangaCardStyle.COMPACT) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.align(Alignment.BottomCenter)
									.background(
										Brush.verticalGradient(
											listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
										),
									)
									.padding(
										start = hInset,
										end = hInset,
										top = 18.dp,
										bottom = bInset + if (state.showBottomProgressBar) 4.dp else 0.dp,
									),
							) {
								Text(
									text = "Corner-Safe Manga Title Never Clips",
									style = MaterialTheme.typography.titleSmall,
									fontSize = 11.5.sp,
									fontWeight = FontWeight.Bold,
									color = Color.White,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
						if (state.showBottomProgressBar) {
							LinearProgressIndicator(
								progress = { 0.65f },
								color = MaterialTheme.colorScheme.primary,
								trackColor = Color.Black.copy(alpha = 0.35f),
								modifier = Modifier
									.fillMaxWidth()
									.height(3.dp)
									.align(Alignment.BottomCenter),
							)
						}
					}
					if (state.mangaCardStyle == MangaCardStyle.COMFORTABLE) {
						Text(
							text = "Unclipped Two-Line Card Title",
							style = MaterialTheme.typography.titleSmall,
							fontSize = 11.5.sp,
							lineHeight = 15.sp,
							color = NemuriTheme.colors.textPrimary,
							minLines = 2,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(horizontal = 2.dp),
						)
					}
				}
			}
		}
	}
}

@Composable
fun ExtensionsSettingsScreen(
	viewModel: SettingsViewModel,
	onOpenExtensionsCatalog: () -> Unit,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showFilterDialog by remember { mutableStateOf(false) }
	var showUaDialog by remember { mutableStateOf(false) }

	SettingsLazyPage(
		title = SettingsSection.EXTENSIONS.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Catalog") {
				SettingsNavRow(
					icon = Icons.Default.Extension,
					title = "Open Extensions & Repositories",
					subtitle = "Manage Mihon APKs, LNReader plugins, and store URLs",
					onClick = onOpenExtensionsCatalog,
				)
			}
		}

		item {
			SettingsGroupCard("Installer & Updates") {
				SettingsToggleRow(
					title = "Private Extension Sandbox",
					subtitle = "Install Mihon extensions privately without Android package installer prompts",
					checked = state.isPrivateInstall,
					onCheckedChange = viewModel::setPrivateInstall,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Shizuku Installer",
					subtitle = "Use Shizuku for silent system extension installs and updates",
					checked = state.isShizukuInstaller,
					enabled = !state.isPrivateInstall,
					onCheckedChange = viewModel::setShizukuInstaller,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Auto-Update Extensions",
					subtitle = "Automatically update extensions in the background via Shizuku or Sandbox",
					checked = state.isAutoUpdateExtensions,
					onCheckedChange = viewModel::setAutoUpdateExtensions,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Extension Update Notifications",
					subtitle = "Notify when extension repositories publish new versions",
					checked = state.isExtensionUpdateNotifications,
					onCheckedChange = viewModel::setExtensionUpdateNotifications,
				)
			}
		}

		item {
			SettingsGroupCard("Filtering & Network Identity") {
				SettingsChoiceRow(
					title = "Catalog Content Filter",
					subtitle = "Controls which rated sources appear across Explore & Search",
					currentLabel = stringResource(state.contentFilter.filterTitleResId),
					onClick = { showFilterDialog = true },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Mihon Extension User-Agent",
					subtitle = state.mihonUserAgentOverride ?: "Using WebView default User-Agent",
					currentLabel = if (state.mihonUserAgentOverride.isNullOrBlank()) "Default" else "Custom",
					onClick = { showUaDialog = true },
				)
			}
		}
	}

	if (showFilterDialog) {
		val options = ContentWarning.entries.map { it to stringResource(it.filterTitleResId) }
		SettingsOptionDialog(
			title = "Catalog Content Filter",
			options = options,
			selected = state.contentFilter,
			onSelect = viewModel::setContentFilter,
			onDismiss = { showFilterDialog = false },
		)
	}

	if (showUaDialog) {
		var draft by remember(state.mihonUserAgentOverride) {
			mutableStateOf(state.mihonUserAgentOverride.orEmpty())
		}
		AlertDialog(
			onDismissRequest = { showUaDialog = false },
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			title = { Text("Mihon User-Agent Override") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = "Leave empty to use the system WebView User-Agent automatically.",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
					OutlinedTextField(
						value = draft,
						onValueChange = { draft = it },
						label = { Text("Custom User-Agent") },
						maxLines = 4,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			},
			confirmButton = {
				Button(
					onClick = {
						viewModel.setMihonUserAgent(draft)
						showUaDialog = false
					},
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) { Text("Save") }
			},
			dismissButton = {
				TextButton(onClick = {
					viewModel.setMihonUserAgent(null)
					showUaDialog = false
				}) { Text("Reset", color = NemuriTheme.colors.textMuted) }
			},
		)
	}
}

@Composable
fun ReadingSettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showModeDialog by remember { mutableStateOf(false) }
	var showTapZoneDialog by remember { mutableStateOf(false) }
	var showShaderDialog by remember { mutableStateOf(false) }
	var showAccelDialog by remember { mutableStateOf(false) }
	val importOnnxLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			viewModel.importNeuralModelUri(uri)
		}
	}

	SettingsLazyPage(
		title = SettingsSection.READING.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Manga Reader Defaults") {
				SettingsChoiceRow(
					title = "Default Reading Mode",
					subtitle = "Used when opening manga without a saved mode override",
					currentLabel = readerModeLabel(state.defaultReaderMode),
					onClick = { showModeDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Auto-Detect Webtoon Format",
					subtitle = "Automatically switch to vertical webtoon mode for tall strips",
					checked = state.isReaderModeDetectionEnabled,
					onCheckedChange = viewModel::setReaderModeDetection,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Keep Screen On",
					subtitle = "Prevent device sleep while actively reading",
					checked = state.isReaderKeepScreenOn,
					onCheckedChange = viewModel::setKeepScreenOn,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Volume Keys Navigation",
					subtitle = "Turn pages or scroll using hardware volume buttons",
					checked = state.isReaderVolumeButtonsEnabled,
					onCheckedChange = viewModel::setVolumeButtons,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "32-Bit Color Decoding",
					subtitle = "Higher fidelity gradients with slightly higher memory usage",
					checked = state.is32BitColorsEnabled,
					onCheckedChange = viewModel::set32BitColors,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Show Page Numbers Overlay",
					subtitle = "Display subtle page counter while reading",
					checked = state.isPagesNumbersEnabled,
					onCheckedChange = viewModel::setPagesNumbers,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Seamless Page Stitching",
					subtitle = "Reassemble uneven chopped slivers into whole pages before rendering",
					checked = state.isReaderSeamlessStitchEnabled,
					onCheckedChange = viewModel::setReaderSeamlessStitchEnabled,
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Tap-Zone Layout",
					subtitle = state.readerTapZoneMode.description,
					currentLabel = state.readerTapZoneMode.label,
					onClick = { showTapZoneDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Smart Double-Tap Fill Zoom",
					subtitle = "Zoom to screen fill width/height when double-tapping a page",
					checked = state.isReaderDoubleTapFill,
					onCheckedChange = viewModel::setReaderDoubleTapFill,
				)
			}
		}

		item {
			SettingsGroupCard("Universal GPU Shaders") {
				SettingsChoiceRow(
					title = "Primary Upscale Shader",
					subtitle = if (UpscaleEffect.isSupported) {
						"Runs via AGSL GPU across Adreno, Mali, and Xclipse"
					} else {
						"Requires Android 13+ for RuntimeShader GPU effects"
					},
					currentLabel = state.readerShaderPreset.label,
					onClick = { if (UpscaleEffect.isSupported) showShaderDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Screentone Descreen",
					subtitle = "Smooths halftone dot noise while keeping line art sharp",
					checked = state.isReaderDescreenEnabled,
					onCheckedChange = viewModel::setReaderDescreen,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Crisp Ink & Paper Levels",
					subtitle = "Deepens faded blacks and cleans muddy scan backgrounds",
					checked = state.isReaderInkLevelsEnabled,
					onCheckedChange = viewModel::setReaderInkLevels,
				)
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Shader Strength",
					valueLabel = "${(state.readerShaderStrength * 100f).roundToInt()}%",
					value = state.readerShaderStrength,
					valueRange = 0.3f..1.0f,
					steps = 6,
					onValueChange = viewModel::setReaderShaderStrength,
				)
			}
		}

		item {
			SettingsGroupCard("Neural 2x Page Upscaler (ONNX)") {
				SettingsToggleRow(
					title = "Enable Neural 2x Upscaling",
					subtitle = if (state.isNeuralUpscaleSupported) {
						"Tiled 256px super-resolution; skips ultra-tall webtoon strips & Battery Saver"
					} else {
						"Available in GitHub builds with ONNX Runtime"
					},
					checked = state.isNeuralUpscaleEnabled,
					enabled = state.isNeuralUpscaleSupported,
					onCheckedChange = viewModel::setNeuralUpscaleEnabled,
				)
				if (state.isNeuralUpscaleSupported) {
					SettingsRowDivider()
					SettingsChoiceRow(
						title = "Hardware Accelerator",
						subtitle = "${state.neuralAccelerator.subtitle} • Active: ${state.activeNeuralDelegateLabel}",
						currentLabel = state.neuralAccelerator.label,
						onClick = { showAccelDialog = true },
					)
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Pre-Upscale Ahead",
						subtitle = "Background-upscale 1–2 upcoming paged pages into disk cache",
						checked = state.isNeuralPreloadEnabled,
						onCheckedChange = viewModel::setNeuralPreloadEnabled,
					)
					SettingsRowDivider()
					Column(
						modifier = Modifier.padding(14.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						val dl = state.neuralDownloadState
						state.neuralCatalog.forEach { item ->
							val isDownloadingThis = dl.activeModelId == item.id
							Surface(
								color = if (item.isSelected && item.isDownloaded) {
									NemuriTheme.colors.tealContainer.copy(alpha = 0.55f)
								} else {
									NemuriTheme.colors.surfaceElevated
								},
								shape = RoundedCornerShape(12.dp),
								border = BorderStroke(
									1.dp,
									if (item.isSelected && item.isDownloaded) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								),
								modifier = Modifier.fillMaxWidth(),
							) {
								Column(
									modifier = Modifier.padding(10.dp),
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween,
									) {
										Column(modifier = Modifier.weight(1f)) {
											Text(item.title, style = MaterialTheme.typography.labelLarge, color = NemuriTheme.colors.textPrimary)
											Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = NemuriTheme.colors.textMuted)
										}
										if (item.isDownloaded) {
											Row(verticalAlignment = Alignment.CenterVertically) {
												TextButton(onClick = { viewModel.selectNeuralModel(item.id) }) {
													Text(
														text = if (item.isSelected) "Active" else "Use",
														color = if (item.isSelected) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
													)
												}
												IconButton(onClick = { viewModel.deleteNeuralModel(item.id) }, modifier = Modifier.size(30.dp)) {
													Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NemuriTheme.colors.danger, modifier = Modifier.size(17.dp))
												}
											}
										} else if (isDownloadingThis) {
											IconButton(onClick = viewModel::cancelNeuralDownload, modifier = Modifier.size(30.dp)) {
												Icon(Icons.Default.Close, contentDescription = "Cancel", tint = NemuriTheme.colors.amber, modifier = Modifier.size(17.dp))
											}
										} else if (item.isDownloadable) {
											OutlinedButton(
												onClick = { viewModel.downloadNeuralModel(item.id) },
												enabled = dl.activeModelId == null,
												border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)),
											) {
												Icon(Icons.Default.Download, contentDescription = null, tint = NemuriTheme.colors.cyan, modifier = Modifier.size(16.dp))
												Spacer(Modifier.width(4.dp))
												Text(item.sizeLabel, color = NemuriTheme.colors.cyan)
											}
										} else {
											OutlinedButton(
												onClick = { importOnnxLauncher.launch(arrayOf("*/*")) },
												enabled = dl.activeModelId == null,
												border = BorderStroke(1.dp, NemuriTheme.colors.border),
											) {
												Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NemuriTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
												Spacer(Modifier.width(4.dp))
												Text("Import", color = NemuriTheme.colors.textSecondary)
											}
										}
									}
									if (isDownloadingThis) {
										val frac = if (dl.totalBytes > 0L) (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
										LinearProgressIndicator(
											progress = { frac },
											color = NemuriTheme.colors.cyan,
											trackColor = NemuriTheme.colors.border,
											modifier = Modifier.fillMaxWidth(),
										)
									}
								}
							}
						}
						OutlinedButton(
							onClick = viewModel::clearNeuralCache,
							enabled = state.neuralCacheBytes > 0L,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
							modifier = Modifier.fillMaxWidth(),
						) {
							Icon(Icons.Default.CleaningServices, contentDescription = null, tint = NemuriTheme.colors.textSecondary, modifier = Modifier.size(17.dp))
							Spacer(Modifier.width(8.dp))
							Text(
								text = "Clear Upscale Cache (${formatByteSize(state.neuralCacheBytes)} / 250 MB)",
								color = if (state.neuralCacheBytes > 0L) NemuriTheme.colors.textSecondary else NemuriTheme.colors.textDim,
							)
						}
					}
				}
			}
		}

		item {
			SettingsGroupCard("Novel & EPUB Defaults") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Text(
						text = "Paper Theme",
						style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
						color = NemuriTheme.colors.textPrimary,
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf(
							"system" to "Night",
							"black" to "AMOLED",
							"gray" to "Sepia",
							"white" to "Light",
						).forEach { (key, label) ->
							val selected = state.epubTheme == key
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setEpubTheme(key) },
							) {
								Text(
									text = label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 8.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}

					Text(
						text = "Font Family",
						style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
						color = NemuriTheme.colors.textPrimary,
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						listOf(
							"serif" to "Serif",
							"sans-serif" to "Sans",
							"monospace" to "Mono",
						).forEach { (key, label) ->
							val selected = state.epubFontFamily == key
							Surface(
								color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
								shape = RoundedCornerShape(10.dp),
								border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
								modifier = Modifier
									.weight(1f)
									.clip(RoundedCornerShape(10.dp))
									.clickable { viewModel.setEpubFontFamily(key) },
							) {
								Text(
									text = label,
									style = MaterialTheme.typography.labelMedium,
									color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
									modifier = Modifier.padding(vertical = 8.dp),
									textAlign = androidx.compose.ui.text.style.TextAlign.Center,
								)
							}
						}
					}
				}
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Novel Text Size",
					valueLabel = "${state.epubFontSize}%",
					value = state.epubFontSize.toFloat(),
					valueRange = 70f..180f,
					steps = 10,
					onValueChange = { viewModel.setEpubFontSize(it.roundToInt()) },
				)
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Line Height",
					valueLabel = "${state.epubLineHeight}%",
					value = state.epubLineHeight.toFloat(),
					valueRange = 120f..220f,
					steps = 9,
					onValueChange = { viewModel.setEpubLineHeight(it.roundToInt()) },
				)
				SettingsRowDivider()
				SettingsSliderRow(
					title = "Horizontal Margins",
					valueLabel = "${state.epubHorizontalPadding} dp",
					value = state.epubHorizontalPadding.toFloat(),
					valueRange = 8f..40f,
					steps = 7,
					onValueChange = { viewModel.setEpubHorizontalPadding(it.roundToInt()) },
				)
			}
		}
	}

	if (showModeDialog) {
		SettingsOptionDialog(
			title = "Default Reading Mode",
			options = ReaderMode.entries.map { it to readerModeLabel(it) },
			selected = state.defaultReaderMode,
			onSelect = viewModel::setDefaultReaderMode,
			onDismiss = { showModeDialog = false },
		)
	}

	if (showShaderDialog) {
		SettingsOptionDialog(
			title = "Primary Upscale Shader",
			options = ReaderShaderPreset.entries.map { it to it.label },
			selected = state.readerShaderPreset,
			onSelect = viewModel::setReaderShaderPreset,
			onDismiss = { showShaderDialog = false },
		)
	}

	if (showTapZoneDialog) {
		SettingsOptionDialog(
			title = "Tap-Zone Layout",
			options = ReaderTapZoneMode.entries.map { it to "${it.label} — ${it.description}" },
			selected = state.readerTapZoneMode,
			onSelect = viewModel::setReaderTapZoneMode,
			onDismiss = { showTapZoneDialog = false },
		)
	}

	if (showAccelDialog) {
		SettingsOptionDialog(
			title = "Neural Accelerator",
			options = NeuralAccelerator.entries.map { it to it.label },
			selected = state.neuralAccelerator,
			onSelect = viewModel::setNeuralAccelerator,
			onDismiss = { showAccelDialog = false },
		)
	}
}

@Composable
fun DownloadsStorageSettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showFormatDialog by remember { mutableStateOf(false) }
	var showMeteredDialog by remember { mutableStateOf(false) }

	SettingsLazyPage(
		title = SettingsSection.DOWNLOADS_STORAGE.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Storage Overview") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column {
							Text(
								text = if (state.storageStats.isLoading) "Calculating…" else formatByteSize(state.storageStats.downloadsBytes),
								style = MaterialTheme.typography.headlineMedium,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "Saved manga & novel chapters",
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						OutlinedButton(
							onClick = viewModel::refreshStorageStats,
							border = BorderStroke(1.dp, NemuriTheme.colors.border),
						) {
							Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = NemuriTheme.colors.cyan)
							Spacer(Modifier.width(6.dp))
							Text("Refresh", color = NemuriTheme.colors.cyan)
						}
					}
					Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
						Text(
							text = "Total Cache: ${formatByteSize(state.storageStats.totalCacheBytes)}",
							style = MaterialTheme.typography.labelMedium,
							color = NemuriTheme.colors.textSecondary,
						)
						Text(
							text = "Free Space: ${formatByteSize(state.storageStats.availableBytes)}",
							style = MaterialTheme.typography.labelMedium,
							color = NemuriTheme.colors.healthy,
						)
					}
				}
			}
		}

		item {
			SettingsGroupCard("Cache Management") {
				CacheClearRow(
					title = "Thumbnails Cache",
					sizeBytes = state.storageStats.thumbsBytes,
					onClear = { viewModel.clearCache(CacheDir.THUMBS) },
				)
				SettingsRowDivider()
				CacheClearRow(
					title = "Source Favicons Cache",
					sizeBytes = state.storageStats.faviconsBytes,
					onClear = { viewModel.clearCache(CacheDir.FAVICONS) },
				)
				SettingsRowDivider()
				CacheClearRow(
					title = "Reader Pages Cache",
					sizeBytes = state.storageStats.pagesBytes,
					onClear = { viewModel.clearCache(CacheDir.PAGES) },
				)
				SettingsRowDivider()
				SettingsActionRow(
					icon = Icons.Default.DeleteSweep,
					title = "Clear All Caches",
					subtitle = "Frees ${formatByteSize(state.storageStats.totalCacheBytes)} without deleting library downloads",
					tint = NemuriTheme.colors.amber,
					onClick = viewModel::clearAllCaches,
				)
			}
		}

		item {
			SettingsGroupCard("Download Preferences") {
				SettingsChoiceRow(
					title = "Archive Format",
					subtitle = "Container used when saving chapters to device storage",
					currentLabel = when (state.preferredDownloadFormat) {
						DownloadFormat.AUTOMATIC -> "Automatic"
						DownloadFormat.SINGLE_CBZ -> "Single CBZ"
						DownloadFormat.MULTIPLE_CBZ -> "Per-Chapter CBZ"
					},
					onClick = { showFormatDialog = true },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Download on Metered Network",
					subtitle = "Behavior when only cellular / metered Wi-Fi is active",
					currentLabel = triStateLabel(state.meteredNetworkOption),
					onClick = { showMeteredDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Auto-Delete Read Chapters",
					subtitle = "Remove older downloaded chapters automatically after reading",
					checked = state.isAutoCleanupEnabled,
					onCheckedChange = viewModel::setAutoCleanup,
				)
				if (state.isAutoCleanupEnabled) {
					SettingsRowDivider()
					SettingsSliderRow(
						title = "Keep Chapters Behind",
						subtitle = "0 deletes immediately when advancing to the next chapter",
						valueLabel = "${state.autoCleanupKeepCount}",
						value = state.autoCleanupKeepCount.toFloat(),
						valueRange = 0f..3f,
						steps = 2,
						onValueChange = { viewModel.setAutoCleanupKeep(it.roundToInt()) },
					)
				}
			}
		}
	}

	if (showFormatDialog) {
		SettingsOptionDialog(
			title = "Archive Format",
			options = listOf(
				DownloadFormat.AUTOMATIC to "Automatic",
				DownloadFormat.SINGLE_CBZ to "Single CBZ archive",
				DownloadFormat.MULTIPLE_CBZ to "Separate CBZ per chapter",
			),
			selected = state.preferredDownloadFormat,
			onSelect = viewModel::setDownloadFormat,
			onDismiss = { showFormatDialog = false },
		)
	}

	if (showMeteredDialog) {
		SettingsOptionDialog(
			title = "Metered Network Downloads",
			options = TriStateOption.entries.map { it to triStateLabel(it) },
			selected = state.meteredNetworkOption,
			onSelect = viewModel::setMeteredNetwork,
			onDismiss = { showMeteredDialog = false },
		)
	}
}

@Composable
private fun CacheClearRow(
	title: String,
	sizeBytes: Long,
	onClear: () -> Unit,
) {
	SettingsActionRow(
		icon = Icons.Default.CleaningServices,
		title = title,
		subtitle = formatByteSize(sizeBytes),
		trailing = {
			TextButton(onClick = onClear) {
				Text("Clear", color = NemuriTheme.colors.cyan)
			}
		},
		onClick = onClear,
	)
}

@Composable
fun UpdatesSettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showFreqDialog by remember { mutableStateOf(false) }
	var showDownloadStrategyDialog by remember { mutableStateOf(false) }

	SettingsLazyPage(
		title = SettingsSection.UPDATES.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Manual Check") {
				SettingsActionRow(
					icon = if (state.isTrackerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
					title = if (state.isTrackerRunning) "Library Check Running" else "Check Library for Updates Now",
					subtitle = if (state.isTrackerRunning) "Tap to stop the active check" else "Scan tracked categories for newly released chapters",
					tint = if (state.isTrackerRunning) NemuriTheme.colors.amber else NemuriTheme.colors.cyan,
					trailing = {
						if (state.isTrackerRunning) {
							CircularProgressIndicator(
								modifier = Modifier.size(20.dp),
								strokeWidth = 2.dp,
								color = NemuriTheme.colors.amber,
							)
						}
					},
					onClick = {
						if (state.isTrackerRunning) viewModel.stopUpdatesNow() else viewModel.checkUpdatesNow()
					},
				)
			}
		}

		item {
			SettingsGroupCard("Library Tracker") {
				SettingsToggleRow(
					title = "Enable Background Updates",
					subtitle = "Periodically check library titles for new chapters",
					checked = state.isTrackerEnabled,
					onCheckedChange = viewModel::setTrackerEnabled,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Check on Wi-Fi Only",
					subtitle = "Pause automatic chapter checks on cellular connections",
					checked = state.isTrackerWifiOnly,
					enabled = state.isTrackerEnabled,
					onCheckedChange = viewModel::setTrackerWifiOnly,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Skip 18+ Titles",
					subtitle = "Exclude adult titles from scheduled background update checks",
					checked = state.isTrackerNsfwDisabled,
					enabled = state.isTrackerEnabled,
					onCheckedChange = viewModel::setTrackerNsfwDisabled,
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Check Frequency",
					subtitle = "Multiplier applied to the background check interval",
					currentLabel = frequencyLabel(state.trackerFrequencyFactor),
					onClick = { showFreqDialog = true },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Auto-Download New Chapters",
					subtitle = "Automatically queue newly discovered chapters for offline reading",
					currentLabel = when (state.trackerDownloadStrategy) {
						TrackerDownloadStrategy.DISABLED -> "Disabled"
						TrackerDownloadStrategy.DOWNLOADED -> "When already downloaded"
					},
					onClick = { showDownloadStrategyDialog = true },
				)
			}
		}

		item {
			SettingsGroupCard("Feed & Suggestions") {
				SettingsToggleRow(
					title = "Unread Only by Default",
					subtitle = "Filter the Feed tab to show only unread updates on launch",
					checked = state.isFeedUnreadOnly,
					onCheckedChange = viewModel::setFeedUnreadOnly,
				)
				SettingsRowDivider()
				SettingsToggleRow(
					title = "Enable Personalized Suggestions",
					subtitle = "Recommend titles based on your library tags and reading history",
					checked = state.isSuggestionsEnabled,
					onCheckedChange = viewModel::setSuggestionsEnabled,
				)
				if (state.isSuggestionsEnabled) {
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Refresh on Wi-Fi Only",
						checked = state.isSuggestionsWifiOnly,
						onCheckedChange = viewModel::setSuggestionsWifiOnly,
					)
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Exclude 18+ Suggestions",
						checked = state.isSuggestionsExcludeNsfw,
						onCheckedChange = viewModel::setSuggestionsExcludeNsfw,
					)
					SettingsRowDivider()
					SettingsToggleRow(
						title = "Exclude Novels from Suggestions",
						checked = state.isSuggestionsExcludeNovels,
						onCheckedChange = viewModel::setSuggestionsExcludeNovels,
					)
					SettingsRowDivider()
					SettingsActionRow(
						icon = Icons.Default.Refresh,
						title = "Refresh Suggestions Now",
						subtitle = "Queue an immediate background suggestion refresh",
						onClick = viewModel::refreshSuggestionsNow,
					)
				}
			}
		}
	}

	if (showFreqDialog) {
		SettingsOptionDialog(
			title = "Check Frequency",
			options = listOf(
				0.5f to "Less often (0.5x)",
				1f to "Normal (1x)",
				2f to "Frequent (2x)",
				4f to "Very frequent (4x)",
			),
			selected = state.trackerFrequencyFactor,
			onSelect = viewModel::setTrackerFrequency,
			onDismiss = { showFreqDialog = false },
		)
	}

	if (showDownloadStrategyDialog) {
		SettingsOptionDialog(
			title = "Auto-Download New Chapters",
			options = listOf(
				TrackerDownloadStrategy.DISABLED to "Disabled",
				TrackerDownloadStrategy.DOWNLOADED to "Only for already downloaded titles",
			),
			selected = state.trackerDownloadStrategy,
			onSelect = viewModel::setTrackerDownloadStrategy,
			onDismiss = { showDownloadStrategyDialog = false },
		)
	}
}

@Composable
fun PrivacySettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var showIncognitoNsfwDialog by remember { mutableStateOf(false) }
	var showScreenshotsDialog by remember { mutableStateOf(false) }
	var showDohDialog by remember { mutableStateOf(false) }
	var showProxyDialog by remember { mutableStateOf(false) }

	SettingsLazyPage(
		title = SettingsSection.PRIVACY.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Reading Privacy") {
				SettingsToggleRow(
					title = "Incognito Mode",
					subtitle = "Pause reading history and tracker progress updates",
					checked = state.isIncognitoEnabled,
					onCheckedChange = viewModel::setIncognito,
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Auto-Incognito for 18+ Titles",
					subtitle = "Automatically protect reading sessions on adult entries",
					currentLabel = triStateLabel(state.incognitoForNsfw),
					onClick = { showIncognitoNsfwDialog = true },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					title = "Screenshots Policy",
					subtitle = "Controls FLAG_SECURE protection in reader and app views",
					currentLabel = screenshotsPolicyLabel(state.screenshotsPolicy),
					onClick = { showScreenshotsDialog = true },
				)
			}
		}

		item {
			SettingsGroupCard("Network & WebView Security") {
				SettingsToggleRow(
					icon = Icons.Default.Shield,
					title = "WebView AdBlock",
					subtitle = "Filter known ad and tracker hosts in built-in browser",
					checked = state.isAdBlockEnabled,
					onCheckedChange = viewModel::setAdBlock,
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					icon = Icons.Default.Dns,
					title = "DNS over HTTPS (DoH)",
					subtitle = "Encrypt DNS lookups to bypass ISP DNS blocking",
					currentLabel = state.dnsOverHttps.name,
					onClick = { showDohDialog = true },
				)
				SettingsRowDivider()
				SettingsChoiceRow(
					icon = Icons.Default.Public,
					title = "Network Proxy",
					subtitle = if (state.proxyType == Proxy.Type.DIRECT) {
						"Direct connection"
					} else {
						"${state.proxyType.name} • ${state.proxyAddress}:${state.proxyPort}"
					},
					currentLabel = state.proxyType.name,
					onClick = { showProxyDialog = true },
				)
				SettingsRowDivider()
				SettingsToggleRow(
					icon = Icons.Default.Security,
					title = "Bypass SSL Certificate Checks",
					subtitle = "Warning: reduces transport security. Use only for broken source certificates.",
					checked = state.isSslBypassEnabled,
					onCheckedChange = viewModel::setSslBypass,
				)
			}
		}

		item {
			SettingsGroupCard("Browser Data") {
				SettingsActionRow(
					icon = Icons.Default.Cookie,
					title = "Clear Cookies",
					subtitle = "Clear HTTP & WebView cookies (Cloudflare clearance tokens)",
					tint = NemuriTheme.colors.amber,
					onClick = viewModel::clearWebViewCookies,
				)
			}
		}
	}

	if (showIncognitoNsfwDialog) {
		SettingsOptionDialog(
			title = "Auto-Incognito for 18+",
			options = TriStateOption.entries.map { it to triStateLabel(it) },
			selected = state.incognitoForNsfw,
			onSelect = viewModel::setIncognitoForNsfw,
			onDismiss = { showIncognitoNsfwDialog = false },
		)
	}

	if (showScreenshotsDialog) {
		SettingsOptionDialog(
			title = "Screenshots Policy",
			options = ScreenshotsPolicy.entries.map { it to screenshotsPolicyLabel(it) },
			selected = state.screenshotsPolicy,
			onSelect = viewModel::setScreenshotsPolicy,
			onDismiss = { showScreenshotsDialog = false },
		)
	}

	if (showDohDialog) {
		SettingsOptionDialog(
			title = "DNS over HTTPS Provider",
			options = DoHProvider.entries.map { it to it.name.replace('_', ' ') },
			selected = state.dnsOverHttps,
			onSelect = viewModel::setDoH,
			onDismiss = { showDohDialog = false },
		)
	}

	if (showProxyDialog) {
		ProxyConfigDialog(
			initialType = state.proxyType,
			initialAddress = state.proxyAddress,
			initialPort = state.proxyPort,
			initialLogin = state.proxyLogin,
			initialPassword = state.proxyPassword,
			onSave = { type, address, port, login, pass ->
				viewModel.setProxy(type, address, port, login, pass)
				showProxyDialog = false
			},
			onDismiss = { showProxyDialog = false },
		)
	}
}

@Composable
private fun ProxyConfigDialog(
	initialType: Proxy.Type,
	initialAddress: String,
	initialPort: Int,
	initialLogin: String,
	initialPassword: String,
	onSave: (Proxy.Type, String?, Int, String?, String?) -> Unit,
	onDismiss: () -> Unit,
) {
	var selectedType by remember { mutableStateOf(initialType) }
	var address by remember { mutableStateOf(initialAddress) }
	var portText by remember { mutableStateOf(if (initialPort > 0) initialPort.toString() else "") }
	var login by remember { mutableStateOf(initialLogin) }
	var password by remember { mutableStateOf(initialPassword) }

	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text("Proxy Configuration") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					listOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
						val selected = selectedType == type
						Surface(
							color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(10.dp),
							border = BorderStroke(1.dp, if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border),
							modifier = Modifier
								.weight(1f)
								.clip(RoundedCornerShape(10.dp))
								.clickable { selectedType = type },
						) {
							Text(
								text = type.name,
								style = MaterialTheme.typography.labelMedium,
								color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.padding(vertical = 8.dp),
								textAlign = androidx.compose.ui.text.style.TextAlign.Center,
							)
						}
					}
				}
				if (selectedType != Proxy.Type.DIRECT) {
					OutlinedTextField(
						value = address,
						onValueChange = { address = it },
						label = { Text("Host / IP") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = portText,
						onValueChange = { portText = it.filter(Char::isDigit) },
						label = { Text("Port") },
						singleLine = true,
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = login,
						onValueChange = { login = it },
						label = { Text("Username (optional)") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = password,
						onValueChange = { password = it },
						label = { Text("Password (optional)") },
						singleLine = true,
						visualTransformation = PasswordVisualTransformation(),
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					val port = portText.toIntOrNull() ?: 0
					onSave(selectedType, address, port, login, password)
				},
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
			) { Text("Save") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel", color = NemuriTheme.colors.textMuted)
			}
		},
	)
}

@Composable
fun AboutSettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	SettingsLazyPage(
		title = SettingsSection.ABOUT.title,
		onBack = onBack,
	) {
		item {
			SettingsGroupCard("Application") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = "Nemuri",
						style = MaterialTheme.typography.headlineMedium,
						color = NemuriTheme.colors.cyan,
					)
					Text(
						text = "Version ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
						style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
						color = NemuriTheme.colors.textPrimary,
					)
					Text(
						text = "Flavor: ${BuildConfig.FLAVOR} • Room DB Schema v${state.roomSchemaVersion}",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
			}
		}

		item {
			SettingsGroupCard("Diagnostics") {
				SettingsToggleRow(
					title = "Verbose Session Logging",
					subtitle = "Record redacted runtime logs to local app storage for troubleshooting",
					checked = state.isVerboseLoggingEnabled,
					onCheckedChange = viewModel::setVerboseLogging,
				)
			}
		}

		item {
			SettingsGroupCard("Engine & Compatibility") {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CreditLine("Core Engine", "Kotatsu open-source manga engine & parsers")
					CreditLine("Extensions", "Mihon / Tachiyomi APK compatibility & LNReader JavaScript plugin runtime")
					CreditLine("Reader Stack", "Jetpack Compose UI + Subsampling Scale Image View + Android PDFRenderer")
					CreditLine("Sync & Backups", "Google Drive appDataFolder replica sync, Kotatsu .bk.zip, and Mihon .tachibk")
				}
			}
		}
	}
}

@Composable
private fun CreditLine(title: String, detail: String) {
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.labelLarge,
			color = NemuriTheme.colors.textPrimary,
		)
		Text(
			text = detail,
			style = MaterialTheme.typography.bodySmall,
			color = NemuriTheme.colors.textMuted,
		)
	}
}

private fun colorSchemeSwatchColor(scheme: ColorScheme): Color = when (scheme) {
	ColorScheme.DEFAULT, ColorScheme.EXPRESSIVE -> Color(0xFF5CE1E6)
	ColorScheme.YORHA -> Color(0xFFC8B88A)
	ColorScheme.KYOTO_WASHI -> Color(0xFFC84B31)
	ColorScheme.SUIBOKUGA -> Color(0xFF7C98AB)
	ColorScheme.MIKU -> Color(0xFF4CCAD0)
	ColorScheme.RENA, ColorScheme.SHANA -> Color(0xFFFF4D6D)
	ColorScheme.FROG -> Color(0xFF34D399)
	ColorScheme.BLUEBERRY, ColorScheme.SKY -> Color(0xFF60A5FA)
	ColorScheme.SAKURA, ColorScheme.MAMIMI -> Color(0xFFF472B6)
	ColorScheme.KANADE, ColorScheme.ITSUKA -> Color(0xFFA78BFA)
	ColorScheme.LIME -> Color(0xFF84CC16)
	ColorScheme.AMBER -> Color(0xFFFBBF24)
}

private fun readerModeLabel(mode: ReaderMode): String = when (mode) {
	ReaderMode.STANDARD -> "Paged (LTR)"
	ReaderMode.REVERSED -> "Paged (RTL)"
	ReaderMode.VERTICAL -> "Vertical Paged"
	ReaderMode.WEBTOON -> "Webtoon"
}

private fun triStateLabel(option: TriStateOption): String = when (option) {
	TriStateOption.ENABLED -> "Always"
	TriStateOption.ASK -> "Ask"
	TriStateOption.DISABLED -> "Never"
}

private fun frequencyLabel(factor: Float): String = when {
	factor <= 0.5f -> "0.5x (Slower)"
	factor < 1.5f -> "1x (Default)"
	factor < 3f -> "2x (Faster)"
	else -> "4x (Frequent)"
}

private fun screenshotsPolicyLabel(policy: ScreenshotsPolicy): String = when (policy) {
	ScreenshotsPolicy.ALLOW -> "Allow everywhere"
	ScreenshotsPolicy.BLOCK_NSFW -> "Block on 18+ titles"
	ScreenshotsPolicy.BLOCK_INCOGNITO -> "Block in Incognito"
	ScreenshotsPolicy.BLOCK_ALL -> "Block everywhere"
}
