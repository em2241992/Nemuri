package org.nemuri.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.nemuri.app.ui.components.NemuriBrandLogo
import org.nemuri.app.ui.settings.BackupSettingsUiState
import org.nemuri.app.ui.settings.BackupSettingsViewModel
import org.nemuri.app.ui.settings.MihonImportOptionsDialog
import org.nemuri.app.ui.settings.NemuriRestoreSectionsDialog
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.PalettePreviewSwatch
import org.nemuri.app.ui.theme.paletteDisplayName
import org.nemuri.app.ui.theme.palettePreviewSwatch
import org.nemuri.app.ui.theme.paletteTagline
import org.nemuri.app.ui.theme.yorhaTacticalBrackets

private const val STEP_COUNT = 4

@Immutable
private data class WizardPermissionsSnapshot(
	val canInstallUnknownApps: Boolean,
	val hasPostNotifications: Boolean,
	val isIgnoringBatteryOptimizations: Boolean,
) {
	val grantedCount: Int
		get() = listOf(
			canInstallUnknownApps,
			hasPostNotifications,
			isIgnoringBatteryOptimizations,
		).count { it }
}

private fun readPermissionsSnapshot(context: Context): WizardPermissionsSnapshot {
	val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
	} else {
		true
	}
	val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		ContextCompat.checkSelfPermission(
			context,
			Manifest.permission.POST_NOTIFICATIONS,
		) == PackageManager.PERMISSION_GRANTED
	} else {
		true
	}
	val pm = context.getSystemService(PowerManager::class.java)
	val hasBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
	return WizardPermissionsSnapshot(
		canInstallUnknownApps = canInstall,
		hasPostNotifications = hasNotif,
		isIgnoringBatteryOptimizations = hasBattery,
	)
}

@Composable
fun OnboardingScreen(
	onOpenLibrary: () -> Unit,
	onBrowseExtensions: () -> Unit,
	canDismiss: Boolean = false,
	onDismiss: () -> Unit = onOpenLibrary,
	viewModel: OnboardingViewModel = viewModel(),
	backupViewModel: BackupSettingsViewModel = viewModel(),
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val scope = rememberCoroutineScope()

	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

	var permissions by remember { mutableStateOf(readPermissionsSnapshot(context)) }
	val refreshPermissions = remember(context) {
		{
			permissions = readPermissionsSnapshot(context)
			viewModel.refreshStorageSummary()
			viewModel.refreshSyncStatus()
		}
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				refreshPermissions()
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	// Launchers for Step 2 (SAF + 3 Permissions)
	val folderTreeLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree(),
	) { uri ->
		if (uri != null) {
			viewModel.selectStorageDirectory(uri)
		}
	}

	val installPermLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { refreshPermissions() }

	val batteryPermLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { refreshPermissions() }

	val notificationPermLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) { granted ->
		refreshPermissions()
		if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			runCatching {
				context.startActivity(
					Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
						putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
					},
				)
			}
		}
	}

	// Launchers for Step 3 (Backups + Google Drive Sync)
	val openNemuriRestoreLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri -> uri?.let(backupViewModel::beginNemuriRestore) }

	val openMihonImportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri -> uri?.let(backupViewModel::analyzeMihonBackup) }

	val googleSignInLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult(),
	) { result ->
		viewModel.handleGoogleSignInResult(result.resultCode, result.data)
	}

	val pagerState = rememberPagerState(pageCount = { STEP_COUNT })
	val isLastStep by remember { derivedStateOf { pagerState.currentPage == STEP_COUNT - 1 } }
	val canStepBack by remember { derivedStateOf { pagerState.currentPage > 0 || canDismiss } }

	var backProgress by remember { mutableFloatStateOf(0f) }
	var isBackFromLeft by remember { mutableStateOf(true) }
	val animatedBackProgress by animateFloatAsState(
		targetValue = backProgress,
		animationSpec = spring(stiffness = Spring.StiffnessHigh),
		label = "onboardingBackPreview",
	)

	PredictiveBackHandler(enabled = canStepBack) { events ->
		try {
			events.collect { ev ->
				isBackFromLeft = ev.swipeEdge == BackEventCompat.EDGE_LEFT
				backProgress = ev.progress.coerceIn(0f, 1f)
			}
			backProgress = 0f
			if (pagerState.currentPage > 0) {
				pagerState.animateScrollToPage(pagerState.currentPage - 1)
			} else if (canDismiss) {
				onDismiss()
			}
		} catch (_: CancellationException) {
			backProgress = 0f
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.graphicsLayer {
				val p = animatedBackProgress
				if (p > 0f) {
					val s = 1f - 0.08f * p
					scaleX = s
					scaleY = s
					translationX = (if (isBackFromLeft) 1f else -1f) * 10.dp.toPx() * p
					transformOrigin = TransformOrigin(if (isBackFromLeft) 1f else 0f, 0.5f)
				}
			},
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.windowInsetsPadding(WindowInsets.statusBars),
		) {
			// Top utility row: step badge + skip/close
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(999.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.border),
					modifier = Modifier.yorhaTacticalBrackets(),
				) {
					Text(
						text = "STEP ${pagerState.currentPage + 1} OF $STEP_COUNT",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
					)
				}

				if (canDismiss) {
					IconButton(onClick = onDismiss) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "Close setup wizard",
							tint = NemuriTheme.colors.textMuted,
						)
					}
				} else if (!isLastStep) {
					TextButton(
						onClick = {
							scope.launch { pagerState.animateScrollToPage(STEP_COUNT - 1) }
						},
					) {
						Text(
							text = "Skip to Finish",
							style = MaterialTheme.typography.labelLarge,
							color = NemuriTheme.colors.textMuted,
						)
					}
				} else {
					Spacer(modifier = Modifier.height(36.dp))
				}
			}

			HorizontalPager(
				state = pagerState,
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth(),
			) { page ->
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(rememberScrollState())
						.padding(horizontal = 20.dp, vertical = 8.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					when (page) {
						0 -> Step1ThemeStudio(
							state = state,
							onThemeModeChange = viewModel::setThemeMode,
							onColorSchemeChange = viewModel::setColorScheme,
							onAmoledChange = viewModel::setAmoled,
						)

						1 -> Step2StorageAndPermissions(
							storageSummary = state.storageSummary,
							isCustomStorageSelected = state.isCustomStorageSelected,
							permissions = permissions,
							onPickStorageFolder = { folderTreeLauncher.launch(null) },
							onRequestInstallUnknown = {
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
									runCatching {
										installPermLauncher.launch(
											Intent(
												Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
												Uri.parse("package:${context.packageName}"),
											),
										)
									}
								}
							},
							onRequestNotifications = {
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
									notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
								}
							},
							onRequestBatteryUnrestricted = {
								runCatching {
									batteryPermLauncher.launch(
										Intent(
											Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
											Uri.parse("package:${context.packageName}"),
										),
									)
								}.onFailure {
									runCatching {
										batteryPermLauncher.launch(
											Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
										)
									}
								}
							},
						)

						2 -> Step3SyncAndBackup(
							state = state,
							backupState = backupState,
							onRestoreNemuri = {
								openNemuriRestoreLauncher.launch(
									arrayOf("application/zip", "application/octet-stream", "*/*"),
								)
							},
							onImportMihon = {
								openMihonImportLauncher.launch(arrayOf("*/*"))
							},
							onGoogleSignIn = {
								viewModel.beginGoogleSignIn { req ->
									googleSignInLauncher.launch(req)
								}
							},
							onSyncNow = viewModel::triggerSyncNow,
						)

						else -> Step4ReadyToRead(
							state = state,
							permissions = permissions,
							onBrowseExtensions = {
								viewModel.completeOnboarding()
								onBrowseExtensions()
							},
							onOpenLibrary = {
								viewModel.completeOnboarding()
								onOpenLibrary()
							},
						)
					}
					Spacer(modifier = Modifier.height(20.dp))
				}
			}

			// Bottom navigation bar: back button + animated pill dots + Next/Finish CTA
			Surface(
				color = NemuriTheme.colors.surface.copy(alpha = 0.94f),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.fillMaxWidth()) {
					HorizontalDivider(color = NemuriTheme.colors.border)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.windowInsetsPadding(WindowInsets.navigationBars)
							.padding(horizontal = 20.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							if (pagerState.currentPage > 0) {
								OutlinedButton(
									onClick = {
										scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
									},
									shape = RoundedCornerShape(14.dp),
									border = BorderStroke(1.dp, NemuriTheme.colors.border),
								) {
									Icon(
										imageVector = Icons.AutoMirrored.Filled.ArrowBack,
										contentDescription = "Previous step",
										tint = NemuriTheme.colors.textPrimary,
										modifier = Modifier.size(16.dp),
									)
								}
							}

							Row(
								horizontalArrangement = Arrangement.spacedBy(7.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								repeat(STEP_COUNT) { idx ->
									val active = idx == pagerState.currentPage
									val dotWidth by animateDpAsState(
										targetValue = if (active) 24.dp else 8.dp,
										animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
										label = "wizardDotWidth_$idx",
									)
									val dotColor by animateColorAsState(
										targetValue = if (active) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
										label = "wizardDotColor_$idx",
									)
									Box(
										modifier = Modifier
											.size(width = dotWidth, height = 8.dp)
											.clip(CircleShape)
											.background(dotColor)
											.clickable {
												scope.launch { pagerState.animateScrollToPage(idx) }
											},
									)
								}
							}
						}

						Button(
							onClick = {
								if (isLastStep) {
									viewModel.completeOnboarding()
									onOpenLibrary()
								} else {
									scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
								}
							},
							shape = RoundedCornerShape(16.dp),
							colors = ButtonDefaults.buttonColors(
								containerColor = NemuriTheme.colors.cyan,
								contentColor = NemuriTheme.colors.onCyan,
							),
						) {
							Text(
								text = if (isLastStep) "Finish Setup" else "Continue",
								fontWeight = FontWeight.Bold,
							)
							Spacer(modifier = Modifier.width(6.dp))
							Icon(
								imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
								contentDescription = null,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}
		}
	}

	if (backupState.pendingNemuriRestoreUri != null) {
		NemuriRestoreSectionsDialog(
			onConfirm = backupViewModel::confirmNemuriRestore,
			onDismiss = backupViewModel::cancelNemuriRestore,
		)
	}

	backupState.pendingMihonPreview?.let { preview ->
		MihonImportOptionsDialog(
			preview = preview,
			onConfirm = backupViewModel::confirmMihonImport,
			onDismiss = backupViewModel::cancelMihonImport,
		)
	}
}

@Composable
private fun Step1ThemeStudio(
	state: OnboardingUiState,
	onThemeModeChange: (Int) -> Unit,
	onColorSchemeChange: (ColorScheme) -> Unit,
	onAmoledChange: (Boolean) -> Unit,
) {
	val context = LocalContext.current
	val systemDark = isSystemInDarkTheme()
	val effectiveDark = when (state.themeMode) {
		AppCompatDelegate.MODE_NIGHT_NO -> false
		AppCompatDelegate.MODE_NIGHT_YES -> true
		else -> systemDark
	}
	val palettes = ColorScheme.nemuriCuratedPalettes

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		NemuriBrandLogo(modifier = Modifier.size(72.dp))

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = "Welcome to Nemuri",
				style = MaterialTheme.typography.headlineMedium,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)
			Text(
				text = "Choose your reading atmosphere. Every surface, sheet, and dock adapts live.",
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)
		}

		// Mode + OLED Card
		Surface(
			color = NemuriTheme.colors.card,
			shape = RoundedCornerShape(20.dp),
			border = BorderStroke(1.dp, NemuriTheme.colors.border),
			modifier = Modifier
				.fillMaxWidth()
				.yorhaTacticalBrackets(),
		) {
			Column(
				modifier = Modifier.padding(14.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Icon(
						imageVector = Icons.Default.Palette,
						contentDescription = null,
						tint = NemuriTheme.colors.cyan,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = "APPEARANCE MODE",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
				}

				ConnectedThemeModeSelector(
					selectedMode = state.themeMode,
					onSelectMode = onThemeModeChange,
				)

				HorizontalDivider(color = NemuriTheme.colors.border)

				val oledAlpha = if (effectiveDark) 1f else 0.42f
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable(enabled = effectiveDark) { onAmoledChange(!state.isAmoled) }
						.padding(vertical = 2.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						modifier = Modifier.weight(1f),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Icon(
							imageVector = Icons.Default.DarkMode,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan.copy(alpha = oledAlpha),
							modifier = Modifier.size(20.dp),
						)
						Column {
							Text(
								text = "Full black for OLED",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary.copy(alpha = oledAlpha),
							)
							Text(
								text = "Pitch-black #000000 canvas in dark mode",
								style = MaterialTheme.typography.bodySmall,
								color = NemuriTheme.colors.textMuted.copy(alpha = oledAlpha),
							)
						}
					}
					Switch(
						checked = (state.isAmoled || state.colorScheme == ColorScheme.TRUE_AMOLED) && effectiveDark,
						onCheckedChange = { if (effectiveDark) onAmoledChange(it) },
						enabled = effectiveDark && state.colorScheme != ColorScheme.TRUE_AMOLED,
					)
				}
			}
		}

		// 8 Curated Palettes Studio Grid (2 columns x 4 rows)
		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Text(
				text = "CURATED PALETTES (${palettes.size})",
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier.padding(start = 2.dp),
			)

			palettes.chunked(2).forEach { rowSchemes ->
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					rowSchemes.forEach { scheme ->
						val swatch = remember(scheme, effectiveDark, state.isAmoled) {
							palettePreviewSwatch(
								scheme = scheme,
								isDark = effectiveDark,
								amoled = state.isAmoled || scheme == ColorScheme.TRUE_AMOLED,
								context = context,
							)
						}
						PaletteStudioPreviewCard(
							scheme = scheme,
							swatch = swatch,
							selected = state.colorScheme == scheme,
							onClick = { onColorSchemeChange(scheme) },
							modifier = Modifier.weight(1f),
						)
					}
					if (rowSchemes.size == 1) {
						Spacer(modifier = Modifier.weight(1f))
					}
				}
			}
		}
	}
}

@Composable
private fun ConnectedThemeModeSelector(
	selectedMode: Int,
	onSelectMode: (Int) -> Unit,
) {
	val options = listOf(
		AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to "System",
		AppCompatDelegate.MODE_NIGHT_NO to "Light",
		AppCompatDelegate.MODE_NIGHT_YES to "Dark",
	)
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(3.dp),
	) {
		options.forEachIndexed { index, (mode, label) ->
			val selected = selectedMode == mode
			val shape = RoundedCornerShape(
				topStart = if (index == 0) 24.dp else 8.dp,
				bottomStart = if (index == 0) 24.dp else 8.dp,
				topEnd = if (index == options.lastIndex) 24.dp else 8.dp,
				bottomEnd = if (index == options.lastIndex) 24.dp else 8.dp,
			)
			val container by animateColorAsState(
				targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else NemuriTheme.colors.surfaceElevated,
				label = "themeSegBg_$index",
			)
			val fg by animateColorAsState(
				targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else NemuriTheme.colors.textSecondary,
				label = "themeSegFg_$index",
			)
			Surface(
				onClick = { onSelectMode(mode) },
				color = container,
				shape = shape,
				border = BorderStroke(
					1.dp,
					if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.6f) else NemuriTheme.colors.border,
				),
				modifier = Modifier
					.weight(1f)
					.height(42.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Text(
						text = label,
						style = MaterialTheme.typography.labelLarge,
						fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
						color = fg,
						maxLines = 1,
					)
				}
			}
		}
	}
}

@Composable
private fun PaletteStudioPreviewCard(
	scheme: ColorScheme,
	swatch: PalettePreviewSwatch,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val shape = RoundedCornerShape(16.dp)
	Surface(
		onClick = onClick,
		color = NemuriTheme.colors.card,
		shape = shape,
		border = BorderStroke(
			width = if (selected) 2.dp else 1.dp,
			color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
		),
		modifier = modifier,
	) {
		Column(
			modifier = Modifier.padding(10.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			// Miniature live UI canvas
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(62.dp)
					.clip(RoundedCornerShape(11.dp))
					.background(swatch.background)
					.border(1.dp, swatch.border, RoundedCornerShape(11.dp)),
			) {
				if (swatch.isVisorHud) {
					Canvas(modifier = Modifier.fillMaxSize()) {
						val step = 10.dp.toPx()
						val gridCol = swatch.border.copy(alpha = 0.42f)
						var x = step
						while (x < size.width) {
							drawLine(gridCol, Offset(x, 0f), Offset(x, size.height), 1f)
							x += step
						}
						var y = step
						while (y < size.height) {
							drawLine(gridCol, Offset(0f, y), Offset(size.width, y), 1f)
							y += step
						}
						// Mini [ ] corner brackets
						val arm = 6.dp.toPx()
						val pad = 4.dp.toPx()
						val brk = swatch.accent.copy(alpha = 0.78f)
						val stroke = 1.3.dp.toPx()
						drawLine(brk, Offset(pad, pad), Offset(pad + arm, pad), stroke)
						drawLine(brk, Offset(pad, pad), Offset(pad, pad + arm), stroke)
						drawLine(brk, Offset(size.width - pad - arm, pad), Offset(size.width - pad, pad), stroke)
						drawLine(brk, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), stroke)
						drawLine(brk, Offset(pad, size.height - pad - arm), Offset(pad, size.height - pad), stroke)
						drawLine(brk, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), stroke)
						drawLine(brk, Offset(size.width - pad, size.height - pad - arm), Offset(size.width - pad, size.height - pad), stroke)
						drawLine(brk, Offset(size.width - pad - arm, size.height - pad), Offset(size.width - pad, size.height - pad), stroke)
					}
				}

				// Mini cards row + mini dock bar inside preview
				Row(
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(horizontal = 8.dp, vertical = 7.dp),
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					repeat(3) { i ->
						Box(
							modifier = Modifier
								.size(width = 22.dp, height = 28.dp)
								.clip(RoundedCornerShape(5.dp))
								.background(if (i == 0) swatch.accentContainer else swatch.card)
								.border(
									1.dp,
									if (i == 0) swatch.accent.copy(alpha = 0.7f) else swatch.border,
									RoundedCornerShape(5.dp),
								),
						)
					}
				}

				Row(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(horizontal = 8.dp, vertical = 6.dp)
						.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Box(
						modifier = Modifier
							.weight(1f)
							.height(12.dp)
							.clip(RoundedCornerShape(999.dp))
							.background(swatch.dock)
							.border(0.8.dp, swatch.border, RoundedCornerShape(999.dp)),
					) {
						Box(
							modifier = Modifier
								.padding(2.dp)
								.width(22.dp)
								.height(8.dp)
								.clip(RoundedCornerShape(999.dp))
								.background(swatch.accent),
						)
					}
					Spacer(modifier = Modifier.width(5.dp))
					Box(
						modifier = Modifier
							.size(12.dp)
							.clip(CircleShape)
							.background(swatch.accent),
					)
				}

				if (selected) {
					Surface(
						color = swatch.accent,
						shape = CircleShape,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(5.dp)
							.size(18.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Check,
								contentDescription = "Selected",
								tint = swatch.onAccent,
								modifier = Modifier.size(12.dp),
							)
						}
					}
				}
			}

			Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Text(
						text = paletteDisplayName(scheme),
						style = MaterialTheme.typography.titleSmall,
						fontSize = 12.5.sp,
						color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (scheme == ColorScheme.YORHA_VISOR_V2) {
						Surface(
							color = NemuriTheme.colors.tealContainer,
							shape = RoundedCornerShape(4.dp),
						) {
							Text(
								text = "HUD",
								style = MaterialTheme.typography.labelSmall,
								fontSize = 9.sp,
								color = NemuriTheme.colors.cyan,
								modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
							)
						}
					}
				}
				Text(
					text = paletteTagline(scheme),
					style = MaterialTheme.typography.bodySmall,
					fontSize = 11.sp,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

@Composable
private fun Step2StorageAndPermissions(
	storageSummary: String?,
	isCustomStorageSelected: Boolean,
	permissions: WizardPermissionsSnapshot,
	onPickStorageFolder: () -> Unit,
	onRequestInstallUnknown: () -> Unit,
	onRequestNotifications: () -> Unit,
	onRequestBatteryUnrestricted: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		StepHeroIcon(icon = Icons.Default.Storage)

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = "Storage & Permissions",
				style = MaterialTheme.typography.headlineMedium,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)
			Text(
				text = "Pick where downloaded chapters live and enable smooth background updates & extension installs.",
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)
		}

		// SAF Downloads Folder Card
		Surface(
			color = NemuriTheme.colors.card,
			shape = RoundedCornerShape(20.dp),
			border = BorderStroke(
				1.dp,
				if (isCustomStorageSelected) NemuriTheme.colors.healthy.copy(alpha = 0.55f) else NemuriTheme.colors.border,
			),
			modifier = Modifier
				.fillMaxWidth()
				.yorhaTacticalBrackets(),
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = Icons.Default.FolderOpen,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(20.dp),
						)
						Text(
							text = "Downloads & Local Manga Folder",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
					}
					if (isCustomStorageSelected) {
						Icon(
							imageVector = Icons.Default.CheckCircle,
							contentDescription = "Configured",
							tint = NemuriTheme.colors.healthy,
							modifier = Modifier.size(18.dp),
						)
					}
				}

				Text(
					text = storageSummary ?: "Using app-managed internal manga folder (default)",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textSecondary,
				)

				Button(
					onClick = onPickStorageFolder,
					shape = RoundedCornerShape(14.dp),
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
					),
					modifier = Modifier.fillMaxWidth(),
				) {
					Icon(
						imageVector = Icons.Default.FolderOpen,
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = if (isCustomStorageSelected) "Change Downloads Folder" else "Choose Downloads Folder",
						fontWeight = FontWeight.SemiBold,
					)
				}
			}
		}

		// 1-Tap Permission Cards
		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Text(
				text = "RECOMMENDED PERMISSIONS (${permissions.grantedCount}/3)",
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier.padding(start = 2.dp),
			)

			PermissionCardItem(
				icon = Icons.Default.InstallMobile,
				title = "Install Unknown Sources",
				subtitle = "Required to install & update Mihon / Tachiyomi source extension APKs",
				granted = permissions.canInstallUnknownApps,
				onClick = onRequestInstallUnknown,
			)

			PermissionCardItem(
				icon = Icons.Default.NotificationsActive,
				title = "Notifications",
				subtitle = "Show download progress, new chapter alerts, and extension updates",
				granted = permissions.hasPostNotifications,
				onClick = onRequestNotifications,
			)

			PermissionCardItem(
				icon = Icons.Default.BatteryChargingFull,
				title = "Unrestricted Battery / Background Sync",
				subtitle = "Keep scheduled library chapter checks & downloads reliable overnight",
				granted = permissions.isIgnoringBatteryOptimizations,
				onClick = onRequestBatteryUnrestricted,
			)
		}
	}
}

@Composable
private fun PermissionCardItem(
	icon: ImageVector,
	title: String,
	subtitle: String,
	granted: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		onClick = { if (!granted) onClick() },
		color = if (granted) NemuriTheme.colors.healthy.copy(alpha = 0.08f) else NemuriTheme.colors.card,
		shape = RoundedCornerShape(18.dp),
		border = BorderStroke(
			1.dp,
			if (granted) NemuriTheme.colors.healthy.copy(alpha = 0.55f) else NemuriTheme.colors.border,
		),
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Surface(
				color = if (granted) NemuriTheme.colors.healthy.copy(alpha = 0.18f) else NemuriTheme.colors.surfaceElevated,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.size(40.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = if (granted) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
						modifier = Modifier.size(20.dp),
					)
				}
			}

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleSmall,
					color = NemuriTheme.colors.textPrimary,
				)
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}

			if (granted) {
				Surface(
					color = NemuriTheme.colors.healthy.copy(alpha = 0.18f),
					shape = RoundedCornerShape(999.dp),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(
							imageVector = Icons.Default.Check,
							contentDescription = "Granted",
							tint = NemuriTheme.colors.healthy,
							modifier = Modifier.size(13.dp),
						)
						Text(
							text = "Granted",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.healthy,
						)
					}
				}
			} else {
				Surface(
					color = MaterialTheme.colorScheme.primaryContainer,
					shape = RoundedCornerShape(999.dp),
				) {
					Text(
						text = "Grant",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onPrimaryContainer,
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun Step3SyncAndBackup(
	state: OnboardingUiState,
	backupState: BackupSettingsUiState,
	onRestoreNemuri: () -> Unit,
	onImportMihon: () -> Unit,
	onGoogleSignIn: () -> Unit,
	onSyncNow: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		StepHeroIcon(icon = Icons.Default.CloudSync)

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = "Sync & Backup Restore",
				style = MaterialTheme.typography.headlineMedium,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)
			Text(
				text = "Moving from another device or app? Bring your library, reading progress, repos, and trackers over in one tap.",
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)
		}

		if (backupState.isWorking) {
			Surface(
				color = NemuriTheme.colors.tealContainer,
				shape = RoundedCornerShape(16.dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.cyan),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(
					modifier = Modifier.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = backupState.workingMessage ?: "Restoring backup…",
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.cyan,
					)
					val fraction = backupState.progressFraction
					if (fraction != null) {
						LinearProgressIndicator(
							progress = { fraction },
							color = NemuriTheme.colors.cyan,
							trackColor = NemuriTheme.colors.surfaceElevated,
							modifier = Modifier.fillMaxWidth(),
						)
					} else {
						LinearProgressIndicator(
							color = NemuriTheme.colors.cyan,
							trackColor = NemuriTheme.colors.surfaceElevated,
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			}
		}

		if (!backupState.lastSummary.isNullOrBlank()) {
			val accent = if (backupState.isErrorSummary) NemuriTheme.colors.danger else NemuriTheme.colors.healthy
			Surface(
				color = accent.copy(alpha = 0.12f),
				shape = RoundedCornerShape(16.dp),
				border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(
					text = backupState.lastSummary.orEmpty(),
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textPrimary,
					modifier = Modifier.padding(14.dp),
				)
			}
		}

		// 1. Nemuri .bk.zip Restore
		RestoreActionCard(
			icon = Icons.Default.Restore,
			title = "Restore Nemuri / Kotatsu Backup",
			subtitle = "Import library, history, bookmarks, trackers & settings from a .bk.zip archive",
			actionLabel = "Select .bk.zip",
			enabled = !backupState.isWorking,
			onClick = onRestoreNemuri,
		)

		// 2. Mihon / Tachiyomi .tachibk / .proto.gz Import
		RestoreActionCard(
			icon = Icons.Default.FileOpen,
			title = "Import Mihon / Tachiyomi Backup",
			subtitle = "Migrate .tachibk or .proto.gz library entries, read chapters, repos & trackers",
			actionLabel = "Select .tachibk",
			enabled = !backupState.isWorking,
			onClick = onImportMihon,
		)

		// 3. Google Drive Cloud Sync
		if (state.syncAvailable) {
			val signedIn = state.syncSnapshot.isSignedIn
			val accountLabel = state.syncSnapshot.accountEmail
				?: state.syncSnapshot.accountName
				?: "Connected to Google Drive"
			Surface(
				color = if (signedIn) NemuriTheme.colors.healthy.copy(alpha = 0.08f) else NemuriTheme.colors.card,
				shape = RoundedCornerShape(20.dp),
				border = BorderStroke(
					1.dp,
					if (signedIn) NemuriTheme.colors.healthy.copy(alpha = 0.55f) else NemuriTheme.colors.border,
				),
				modifier = Modifier
					.fillMaxWidth()
					.yorhaTacticalBrackets(),
			) {
				Column(
					modifier = Modifier.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Surface(
							color = if (signedIn) NemuriTheme.colors.healthy.copy(alpha = 0.18f) else NemuriTheme.colors.surfaceElevated,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.size(40.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								if (state.isSyncBusy || state.isSyncing) {
									CircularProgressIndicator(
										color = NemuriTheme.colors.cyan,
										strokeWidth = 2.dp,
										modifier = Modifier.size(18.dp),
									)
								} else {
									Icon(
										imageVector = if (signedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
										contentDescription = null,
										tint = if (signedIn) NemuriTheme.colors.healthy else NemuriTheme.colors.cyan,
										modifier = Modifier.size(20.dp),
									)
								}
							}
						}
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Google Drive Cloud Sync",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = if (signedIn) accountLabel else "Sync library, history & feed automatically across devices",
								style = MaterialTheme.typography.bodySmall,
								color = if (signedIn) NemuriTheme.colors.healthy else NemuriTheme.colors.textMuted,
							)
						}
					}

					Button(
						onClick = if (signedIn) onSyncNow else onGoogleSignIn,
						enabled = !state.isSyncBusy && !state.isSyncing,
						shape = RoundedCornerShape(14.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.primaryContainer,
							contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
						),
						modifier = Modifier.fillMaxWidth(),
					) {
						Icon(
							imageVector = if (signedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
							contentDescription = null,
							modifier = Modifier.size(18.dp),
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = when {
								state.isSyncing -> "Syncing with Google Drive…"
								signedIn -> "Sync Now"
								else -> "Sign in with Google Drive"
							},
							fontWeight = FontWeight.SemiBold,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun RestoreActionCard(
	icon: ImageVector,
	title: String,
	subtitle: String,
	actionLabel: String,
	enabled: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(20.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.border),
		modifier = Modifier
			.fillMaxWidth()
			.yorhaTacticalBrackets(),
	) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.size(40.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = icon,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(20.dp),
						)
					}
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = title,
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
					)
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
			}

			Button(
				onClick = onClick,
				enabled = enabled,
				shape = RoundedCornerShape(14.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.primaryContainer,
					contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
				),
				modifier = Modifier.fillMaxWidth(),
			) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					modifier = Modifier.size(18.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
			}
		}
	}
}

@Composable
private fun Step4ReadyToRead(
	state: OnboardingUiState,
	permissions: WizardPermissionsSnapshot,
	onBrowseExtensions: () -> Unit,
	onOpenLibrary: () -> Unit,
) {
	val modeLabel = when (state.themeMode) {
		AppCompatDelegate.MODE_NIGHT_NO -> "Light"
		AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
		else -> "System"
	}
	val themeSummary = buildString {
		append(paletteDisplayName(state.colorScheme))
		append(" • ")
		append(modeLabel)
		if (state.isAmoled || state.colorScheme == ColorScheme.TRUE_AMOLED) {
			append(" + OLED")
		}
	}

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		StepHeroIcon(icon = Icons.Default.TaskAlt)

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = "Ready to Read",
				style = MaterialTheme.typography.headlineMedium,
				color = NemuriTheme.colors.textPrimary,
				textAlign = TextAlign.Center,
			)
			Text(
				text = "Your Nemuri workspace is configured. You can adjust any setting or re-run this wizard anytime in Settings.",
				style = MaterialTheme.typography.bodyMedium,
				color = NemuriTheme.colors.textMuted,
				textAlign = TextAlign.Center,
			)
		}

		// Live Setup Summary Card
		Surface(
			color = NemuriTheme.colors.card,
			shape = RoundedCornerShape(20.dp),
			border = BorderStroke(1.dp, NemuriTheme.colors.border),
			modifier = Modifier
				.fillMaxWidth()
				.yorhaTacticalBrackets(),
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = "LIVE SETUP SUMMARY",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)

				SummaryStatusRow(label = "Visual Palette", value = themeSummary)
				HorizontalDivider(color = NemuriTheme.colors.border)
				SummaryStatusRow(
					label = "Downloads Folder",
					value = state.storageSummary ?: "Default app storage",
				)
				HorizontalDivider(color = NemuriTheme.colors.border)
				SummaryStatusRow(
					label = "Device Permissions",
					value = "${permissions.grantedCount} of 3 granted",
				)
				HorizontalDivider(color = NemuriTheme.colors.border)
				SummaryStatusRow(
					label = "Library & Sources",
					value = "${state.libraryCount} title(s) • ${state.enabledSourcesCount} source(s) active",
				)
				if (state.syncAvailable) {
					HorizontalDivider(color = NemuriTheme.colors.border)
					SummaryStatusRow(
						label = "Google Drive Sync",
						value = if (state.syncSnapshot.isSignedIn) {
							state.syncSnapshot.accountEmail ?: state.syncSnapshot.accountName ?: "Connected"
						} else {
							"Not signed in"
						},
					)
				}
			}
		}

		// Quick Jump Actions
		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Button(
				onClick = onBrowseExtensions,
				shape = RoundedCornerShape(16.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = NemuriTheme.colors.cyan,
					contentColor = NemuriTheme.colors.onCyan,
				),
				modifier = Modifier
					.fillMaxWidth()
					.height(52.dp),
			) {
				Icon(
					imageVector = Icons.Default.Extension,
					contentDescription = null,
					modifier = Modifier.size(20.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = "Browse Extensions",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
				)
			}

			OutlinedButton(
				onClick = onOpenLibrary,
				shape = RoundedCornerShape(16.dp),
				border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.6f)),
				modifier = Modifier
					.fillMaxWidth()
					.height(52.dp),
			) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.MenuBook,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(20.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = "Open Library",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					color = NemuriTheme.colors.cyan,
				)
			}
		}
	}
}

@Composable
private fun SummaryStatusRow(
	label: String,
	value: String,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodySmall,
			color = NemuriTheme.colors.textMuted,
		)
		Spacer(modifier = Modifier.width(12.dp))
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = NemuriTheme.colors.textPrimary,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.End,
			modifier = Modifier.weight(1f, fill = false),
		)
	}
}

@Composable
private fun StepHeroIcon(icon: ImageVector) {
	Surface(
		color = MaterialTheme.colorScheme.primaryContainer,
		shape = RoundedCornerShape(24.dp),
		border = BorderStroke(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.4f)),
		modifier = Modifier
			.size(72.dp)
			.yorhaTacticalBrackets(),
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimaryContainer,
				modifier = Modifier.size(34.dp),
			)
		}
	}
}
