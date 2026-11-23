package org.nemuri.app.ui.extensions

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.settings.sources.catalog.ExtensionStoreKind
import org.koitharu.kotatsu.settings.sources.catalog.StoreHealth
import org.nemuri.app.ui.components.NemuriEmptyState
import org.nemuri.app.ui.components.NemuriModalSheet
import org.nemuri.app.ui.components.NonWrappingBadgeText
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun ExtensionsCatalogScreen(
	initialRepoUrl: String? = null,
	onBack: () -> Unit,
	viewModel: ExtensionsCatalogViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	var showAddStoreDialog by rememberSaveable { mutableStateOf(false) }
	var pendingRepoInput by rememberSaveable { mutableStateOf("") }
	var showLanguageSheet by remember { mutableStateOf(false) }
	var showFailedExtensions by remember { mutableStateOf(false) }

	// Consume repo deep-link URLs by opening the Add Store dialog prefilled.
	LaunchedEffect(initialRepoUrl) {
		if (!initialRepoUrl.isNullOrBlank()) {
			pendingRepoInput = initialRepoUrl
			showAddStoreDialog = true
		}
	}

	var activeSystemInstall by remember { mutableStateOf<PendingSystemInstall?>(null) }
	var awaitingUnknownSourcesInstall by remember { mutableStateOf<PendingSystemInstall?>(null) }

	val systemInstallLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		val completed = activeSystemInstall ?: return@rememberLauncherForActivityResult
		activeSystemInstall = null
		viewModel.onSystemInstallFinished(completed)
	}

	@Suppress("DEPRECATION")
	fun launchApkInstaller(pending: PendingSystemInstall) {
		val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", pending.apkFile)
		val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			.setDataAndType(apkUri, MIME_APK)
			.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
			.putExtra(Intent.EXTRA_RETURN_RESULT, true)
		val installerTargets = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
		for (info in installerTargets) {
			val targetPkg = info.activityInfo?.packageName ?: continue
			context.grantUriPermission(targetPkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		activeSystemInstall = pending
		runCatching {
			systemInstallLauncher.launch(intent)
		}.onFailure {
			val fallback = Intent(Intent.ACTION_VIEW)
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				.setDataAndType(apkUri, MIME_APK)
			runCatching {
				systemInstallLauncher.launch(fallback)
			}.onFailure {
				activeSystemInstall = null
				viewModel.onSystemInstallFinished(pending)
			}
		}
	}

	val unknownSourcesLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		val pending = awaitingUnknownSourcesInstall ?: return@rememberLauncherForActivityResult
		awaitingUnknownSourcesInstall = null
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
			launchApkInstaller(pending)
		} else {
			viewModel.onSystemInstallFinished(pending)
		}
	}

	LaunchedEffect(uiState.pendingSystemInstall) {
		val pending = uiState.pendingSystemInstall ?: return@LaunchedEffect
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
			awaitingUnknownSourcesInstall = pending
			val permIntent = Intent(
				Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
				"package:${context.packageName}".toUri(),
			)
			runCatching { unknownSourcesLauncher.launch(permIntent) }.onFailure {
				awaitingUnknownSourcesInstall = null
				viewModel.onSystemInstallFinished(pending)
			}
		} else {
			launchApkInstaller(pending)
		}
	}

	var activeUninstallPackage by remember { mutableStateOf<String?>(null) }
	val systemUninstallLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		val pkg = activeUninstallPackage ?: return@rememberLauncherForActivityResult
		activeUninstallPackage = null
		viewModel.onSystemUninstallFinished(pkg)
	}

	LaunchedEffect(uiState.pendingSystemUninstall) {
		val pkg = uiState.pendingSystemUninstall ?: return@LaunchedEffect
		activeUninstallPackage = pkg
		val uninstallIntent = Intent(Intent.ACTION_DELETE, "package:$pkg".toUri())
		runCatching {
			systemUninstallLauncher.launch(uninstallIntent)
		}.onFailure {
			activeUninstallPackage = null
			viewModel.onSystemUninstallFinished(pkg)
		}
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(NemuriTheme.colors.background)
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
		// Top Bar
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back",
					tint = NemuriTheme.colors.textPrimary,
				)
			}
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Extensions",
					style = MaterialTheme.typography.titleLarge,
					color = NemuriTheme.colors.textPrimary,
				)
				Text(
					text = "${uiState.installedItems.size} installed • ${uiState.stores.size} stores",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
			IconButton(
				onClick = viewModel::refreshStores,
				enabled = !uiState.isRefreshing,
			) {
				if (uiState.isRefreshing) {
					CircularProgressIndicator(
						color = NemuriTheme.colors.cyan,
						strokeWidth = 2.dp,
						modifier = Modifier.size(18.dp),
					)
				} else {
					Icon(
						imageVector = Icons.Default.Refresh,
						contentDescription = "Refresh stores",
						tint = NemuriTheme.colors.textSecondary,
					)
				}
			}
			Surface(
				color = NemuriTheme.colors.cyan,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier
					.padding(end = 6.dp)
					.clip(RoundedCornerShape(12.dp))
					.clickable {
						pendingRepoInput = ""
						showAddStoreDialog = true
					},
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Icon(
						imageVector = Icons.Default.Add,
						contentDescription = null,
						tint = NemuriTheme.colors.onCyan,
						modifier = Modifier.size(16.dp),
					)
					Text(
						text = "Store",
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.onCyan,
					)
				}
			}
		}

		// Tab Bar
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			ExtensionsTab.entries.forEach { tab ->
				val selected = uiState.activeTab == tab
				val countBadge = when (tab) {
					ExtensionsTab.UPDATES -> uiState.updateItems.size
					ExtensionsTab.STORES -> uiState.stores.size
					else -> 0
				}
				Surface(
					color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.weight(1f)
						.border(
							1.dp,
							if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
							RoundedCornerShape(14.dp),
						)
						.clip(RoundedCornerShape(14.dp))
						.clickable { viewModel.selectTab(tab) },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
						horizontalArrangement = Arrangement.Center,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = tab.label,
							style = MaterialTheme.typography.labelMedium,
							color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						if (countBadge > 0) {
							Spacer(modifier = Modifier.width(4.dp))
							Badge(
								containerColor = if (tab == ExtensionsTab.UPDATES) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								contentColor = if (tab == ExtensionsTab.UPDATES) NemuriTheme.colors.onCyan else NemuriTheme.colors.textPrimary,
							) {
								Text(countBadge.toString())
							}
						}
					}
				}
			}
		}

		// Search & Filter Controls for non-Store tabs
		if (uiState.activeTab != ExtensionsTab.STORES) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 12.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Surface(
					color = NemuriTheme.colors.surfaceElevated,
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.weight(1f)
						.height(42.dp)
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp)),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = null,
							tint = NemuriTheme.colors.textMuted,
							modifier = Modifier.size(18.dp),
						)
						Box(modifier = Modifier.weight(1f)) {
							if (uiState.query.isEmpty()) {
								Text(
									text = "Filter extensions or sources…",
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textDim,
								)
							}
							BasicTextField(
								value = uiState.query,
								onValueChange = viewModel::setQuery,
								singleLine = true,
								textStyle = MaterialTheme.typography.bodySmall.copy(color = NemuriTheme.colors.textPrimary),
								cursorBrush = SolidColor(NemuriTheme.colors.cyan),
								modifier = Modifier.fillMaxWidth(),
							)
						}
						if (uiState.query.isNotEmpty()) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear",
								tint = NemuriTheme.colors.textMuted,
								modifier = Modifier
									.size(18.dp)
									.clickable { viewModel.setQuery("") },
							)
						}
					}
				}

				if (uiState.availableLanguages.isNotEmpty()) {
					Surface(
						color = if (uiState.selectedLangCode != null) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier
							.height(42.dp)
							.border(
								1.dp,
								if (uiState.selectedLangCode != null) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
								RoundedCornerShape(12.dp),
							)
							.clip(RoundedCornerShape(12.dp))
							.clickable { showLanguageSheet = true },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Icon(
								imageVector = Icons.Default.Language,
								contentDescription = "Language filter",
								tint = if (uiState.selectedLangCode != null) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
								modifier = Modifier.size(16.dp),
							)
							Text(
								text = uiState.selectedLangCode?.uppercase() ?: "Lang",
								style = MaterialTheme.typography.labelMedium,
								color = if (uiState.selectedLangCode != null) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
							)
						}
					}
				}
			}

			// Type + Store filter row
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 12.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				ExtensionContentFilter.entries.forEach { filter ->
					val selected = uiState.contentFilter == filter
					FilterPillChip(
						label = filter.label,
						selected = selected,
						onClick = { viewModel.setContentFilter(filter) },
					)
				}
				if (uiState.activeTab == ExtensionsTab.CATALOG && uiState.stores.size > 1) {
					FilterPillChip(
						label = "All stores",
						selected = uiState.selectedStoreId == null,
						onClick = { viewModel.selectStoreFilter(null) },
					)
					uiState.stores.forEach { storeSummary ->
						FilterPillChip(
							label = storeSummary.label,
							selected = uiState.selectedStoreId == storeSummary.state.store.id,
							onClick = { viewModel.selectStoreFilter(storeSummary.state.store.id) },
						)
					}
				}
			}
		}

		HorizontalDivider(color = NemuriTheme.colors.border, modifier = Modifier.padding(top = 4.dp))

		when (uiState.activeTab) {
			ExtensionsTab.INSTALLED -> InstalledExtensionsList(
				items = uiState.installedItems,
				failedCount = uiState.failedExtensions.size,
				showFailedDetails = showFailedExtensions,
				onToggleFailedDetails = { showFailedExtensions = !showFailedExtensions },
				failedMessages = uiState.failedExtensions.map { "${it.pkgName}: ${it.message}" },
				inProgressIds = uiState.inProgressIds,
				onUpdate = { candidate -> viewModel.installOrUpdate(candidate) },
				onToggleHidden = viewModel::toggleHidden,
				onUninstall = viewModel::uninstall,
				onOpenCatalog = { viewModel.selectTab(ExtensionsTab.CATALOG) },
			)
			ExtensionsTab.UPDATES -> UpdatesExtensionsList(
				items = uiState.updateItems,
				inProgressIds = uiState.inProgressIds,
				onUpdateAll = viewModel::updateAll,
				onUpdateSingle = { candidate -> viewModel.installOrUpdate(candidate) },
			)
			ExtensionsTab.CATALOG -> CatalogExtensionsList(
				items = uiState.catalogItems,
				hasStores = uiState.stores.isNotEmpty(),
				inProgressIds = uiState.inProgressIds,
				onInstallOrUpdate = viewModel::installOrUpdate,
				onAddStore = {
					pendingRepoInput = ""
					showAddStoreDialog = true
				},
			)
			ExtensionsTab.STORES -> StoresTabList(
				stores = uiState.stores,
				isPrivateMode = uiState.isPrivateMode,
				isShizukuEnabled = uiState.isShizukuEnabled,
				isShizukuInstalled = uiState.isShizukuInstalled,
				isShizukuReady = uiState.isShizukuReady,
				onSetPrivateMode = viewModel::setPrivateInstallEnabled,
				onSetShizuku = viewModel::setShizukuEnabled,
				onAddStore = {
					pendingRepoInput = ""
					showAddStoreDialog = true
				},
				onRefresh = viewModel::refreshStores,
				onRemoveStore = viewModel::removeStore,
			)
		}
	}

	if (showAddStoreDialog) {
		AlertDialog(
			onDismissRequest = {
				if (!uiState.isAddingStore) showAddStoreDialog = false
			},
			containerColor = NemuriTheme.colors.sheet,
			titleContentColor = NemuriTheme.colors.textPrimary,
			textContentColor = NemuriTheme.colors.textSecondary,
			title = { Text("Add Extension Repository") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(
						text = "Supports HTTPS Mihon / Tachiyomi extension repos (index.min.json / index.pb), Tsundoku novel repos, and LNReader JS plugin repos.",
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
					OutlinedTextField(
						value = pendingRepoInput,
						onValueChange = { pendingRepoInput = it },
						label = { Text("HTTPS index URL") },
						placeholder = { Text("https://example.com/repo/index.min.json") },
						singleLine = true,
						enabled = !uiState.isAddingStore,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			},
			confirmButton = {
				Button(
					onClick = {
						viewModel.addStore(pendingRepoInput) { added ->
							if (added) {
								showAddStoreDialog = false
							}
						}
					},
					enabled = !uiState.isAddingStore && pendingRepoInput.isNotBlank(),
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
				) {
					if (uiState.isAddingStore) {
						CircularProgressIndicator(
							color = NemuriTheme.colors.onCyan,
							strokeWidth = 2.dp,
							modifier = Modifier.size(16.dp),
						)
					} else {
						Text("Validate & Add")
					}
				}
			},
			dismissButton = {
				TextButton(
					onClick = { showAddStoreDialog = false },
					enabled = !uiState.isAddingStore,
				) {
					Text("Cancel", color = NemuriTheme.colors.textSecondary)
				}
			},
		)
	}

	if (showLanguageSheet) {
		NemuriModalSheet(
			title = "Extension Language",
			subtitle = "Filter by catalog or installed language",
			onDismiss = { showLanguageSheet = false },
		) {
			LazyColumn(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				contentPadding = PaddingValues(bottom = 12.dp),
			) {
				item {
					LanguageRow(
						label = "All Languages",
						countText = null,
						selected = uiState.selectedLangCode == null,
						onClick = {
							viewModel.selectLanguageFilter(null)
							showLanguageSheet = false
						},
					)
				}
				items(uiState.availableLanguages, key = { it.code }) { option ->
					LanguageRow(
						label = option.label,
						countText = option.count.toString(),
						selected = uiState.selectedLangCode.equals(option.code, ignoreCase = true),
						onClick = {
							viewModel.selectLanguageFilter(option.code)
							showLanguageSheet = false
						},
					)
				}
			}
		}
	}
}

@Composable
private fun InstalledExtensionsList(
	items: List<InstalledExtensionItem>,
	failedCount: Int,
	showFailedDetails: Boolean,
	onToggleFailedDetails: () -> Unit,
	failedMessages: List<String>,
	inProgressIds: Set<String>,
	onUpdate: (CatalogExtensionItem) -> Unit,
	onToggleHidden: (InstalledExtensionItem) -> Unit,
	onUninstall: (InstalledExtensionItem) -> Unit,
	onOpenCatalog: () -> Unit,
) {
	if (items.isEmpty() && failedCount == 0) {
		NemuriEmptyState(
			icon = Icons.Default.Extension,
			title = "No extensions installed",
			subtitle = "Add an extension repository in Stores or browse Catalog to install manga & novel sources.",
			actionLabel = "Browse Catalog",
			onAction = onOpenCatalog,
		)
		return
	}

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(14.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		if (failedCount > 0) {
			item {
				Surface(
					color = NemuriTheme.colors.card,
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
						.clickable(onClick = onToggleFailedDetails),
				) {
					Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							Icon(
								imageVector = Icons.Default.WarningAmber,
								contentDescription = null,
								tint = NemuriTheme.colors.amber,
								modifier = Modifier.size(18.dp),
							)
							Text(
								text = "$failedCount extension(s) failed to load",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.amber,
							)
						}
						if (showFailedDetails) {
							failedMessages.forEach { msg ->
								Text(
									text = msg,
									style = MaterialTheme.typography.bodySmall,
									color = NemuriTheme.colors.textMuted,
								)
							}
						}
					}
				}
			}
		}

		items(items, key = { "${if (it.isLnPlugin) "ln" else "apk"}:${it.id}" }) { item ->
			val busy = item.id in inProgressIds
			Surface(
				color = NemuriTheme.colors.card,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
			) {
				Row(
					modifier = Modifier.padding(12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					ExtensionIconBox(iconModel = item.iconModel, fallbackName = item.displayName, isNovel = item.isNovel)
					Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							Text(
								text = item.displayName,
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.weight(1f, fill = false),
							)
							if (item.isNovel) {
								PillBadge(text = if (item.isLnPlugin) "JS NOVEL" else "NOVEL", color = NemuriTheme.colors.cyan)
							}
						}
						Text(
							text = buildString {
								append(item.langLabel)
								append(" • v")
								append(item.versionName)
								if (!item.storeLabel.isNullOrBlank()) {
									append(" • ")
									append(item.storeLabel)
								}
								if (!item.isShared && !item.isLnPlugin) {
									append(" • Sandbox")
								}
							},
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					if (item.updateCandidate != null) {
						Surface(
							color = NemuriTheme.colors.cyan,
							shape = RoundedCornerShape(10.dp),
							modifier = Modifier
								.clip(RoundedCornerShape(10.dp))
								.clickable(enabled = !busy) { onUpdate(item.updateCandidate) },
						) {
							Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
								if (busy) {
									CircularProgressIndicator(
										color = NemuriTheme.colors.onCyan,
										strokeWidth = 2.dp,
										modifier = Modifier.size(16.dp),
									)
								} else {
									Icon(
										imageVector = Icons.Default.SystemUpdateAlt,
										contentDescription = "Update",
										tint = NemuriTheme.colors.onCyan,
										modifier = Modifier.size(16.dp),
									)
								}
							}
						}
					}
					IconButton(onClick = { onToggleHidden(item) }) {
						Icon(
							imageVector = if (item.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
							contentDescription = if (item.isHidden) "Show in Explore" else "Hide from Explore",
							tint = if (item.isHidden) NemuriTheme.colors.amber else NemuriTheme.colors.textSecondary,
							modifier = Modifier.size(20.dp),
						)
					}
					IconButton(onClick = { onUninstall(item) }, enabled = !busy) {
						Icon(
							imageVector = Icons.Default.DeleteOutline,
							contentDescription = "Uninstall",
							tint = NemuriTheme.colors.danger,
							modifier = Modifier.size(20.dp),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun UpdatesExtensionsList(
	items: List<InstalledExtensionItem>,
	inProgressIds: Set<String>,
	onUpdateAll: () -> Unit,
	onUpdateSingle: (CatalogExtensionItem) -> Unit,
) {
	if (items.isEmpty()) {
		NemuriEmptyState(
			icon = Icons.Default.SystemUpdateAlt,
			title = "All extensions up to date",
			subtitle = "Pull to refresh stores above to check repositories for newer builds.",
		)
		return
	}

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(14.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		item {
			Surface(
				color = NemuriTheme.colors.tealContainer,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
			) {
				Row(
					modifier = Modifier.padding(14.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "${items.size} update(s) ready",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Tap Update All to install available extension builds.",
							style = MaterialTheme.typography.labelSmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
					Button(
						onClick = onUpdateAll,
						colors = ButtonDefaults.buttonColors(
							containerColor = NemuriTheme.colors.cyan,
							contentColor = NemuriTheme.colors.onCyan,
						),
						shape = RoundedCornerShape(12.dp),
					) {
						Text("Update All")
					}
				}
			}
		}

		items(items, key = { "${if (it.isLnPlugin) "ln" else "apk"}:${it.id}" }) { item ->
			val candidate = item.updateCandidate ?: return@items
			val busy = item.id in inProgressIds
			CatalogExtensionRow(
				item = candidate,
				busy = busy,
				onClick = { onUpdateSingle(candidate) },
			)
		}
	}
}

@Composable
private fun CatalogExtensionsList(
	items: List<CatalogExtensionItem>,
	hasStores: Boolean,
	inProgressIds: Set<String>,
	onInstallOrUpdate: (CatalogExtensionItem) -> Unit,
	onAddStore: () -> Unit,
) {
	if (!hasStores) {
		NemuriEmptyState(
			icon = Icons.Default.Storefront,
			title = "No extension stores configured",
			subtitle = "Add an HTTPS Mihon, Tachiyomi, Tsundoku, or LNReader repository URL to browse extensions.",
			actionLabel = "Add Store",
			onAction = onAddStore,
		)
		return
	}
	if (items.isEmpty()) {
		NemuriEmptyState(
			icon = Icons.Default.Search,
			title = "No matching extensions",
			subtitle = "Adjust your search query, content filter, or selected repository.",
		)
		return
	}

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(14.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		items(items, key = { "${it.storeId}:${it.packageName}:${it.apkName}" }) { item ->
			CatalogExtensionRow(
				item = item,
				busy = item.packageName in inProgressIds,
				onClick = { onInstallOrUpdate(item) },
			)
		}
	}
}

@Composable
private fun CatalogExtensionRow(
	item: CatalogExtensionItem,
	busy: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
	) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			ExtensionIconBox(iconModel = item.iconUrl, fallbackName = item.displayName, isNovel = item.isNovel)
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(
						text = item.displayName,
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (item.isNovel) {
						PillBadge(text = if (item.isLnPlugin) "JS" else "NOVEL", color = NemuriTheme.colors.cyan)
					}
					if (item.contentWarning.name != "SAFE") {
						PillBadge(
							text = if (item.contentWarning.name == "NSFW") "18+" else "MIXED",
							color = NemuriTheme.colors.amber,
						)
					}
				}
				Text(
					text = buildString {
						append(item.langLabel)
						append(" • v")
						append(item.versionName)
						if (item.hasUpdate && !item.installedVersionName.isNullOrBlank()) {
							append(" (from ")
							append(item.installedVersionName)
							append(")")
						}
						append(" • ")
						append(item.storeLabel)
					},
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			val actionColor = when {
				item.hasUpdate -> NemuriTheme.colors.cyan
				item.isInstalled -> NemuriTheme.colors.healthy
				else -> NemuriTheme.colors.cyan
			}
			OutlinedButton(
				onClick = onClick,
				enabled = !busy && (!item.isInstalled || item.hasUpdate),
				shape = RoundedCornerShape(10.dp),
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
			) {
				if (busy) {
					CircularProgressIndicator(
						color = NemuriTheme.colors.cyan,
						strokeWidth = 2.dp,
						modifier = Modifier.size(16.dp),
					)
				} else {
					when {
						item.hasUpdate -> {
							Icon(
								imageVector = Icons.Default.SystemUpdateAlt,
								contentDescription = null,
								tint = actionColor,
								modifier = Modifier.size(15.dp),
							)
							Spacer(modifier = Modifier.width(4.dp))
							Text("Update", color = actionColor, style = MaterialTheme.typography.labelMedium)
						}
						item.isInstalled -> {
							Text("Installed", color = actionColor, style = MaterialTheme.typography.labelMedium)
						}
						else -> {
							Icon(
								imageVector = Icons.Default.Download,
								contentDescription = null,
								tint = actionColor,
								modifier = Modifier.size(15.dp),
							)
							Spacer(modifier = Modifier.width(4.dp))
							Text("Install", color = actionColor, style = MaterialTheme.typography.labelMedium)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun StoresTabList(
	stores: List<StoreSummaryItem>,
	isPrivateMode: Boolean,
	isShizukuEnabled: Boolean,
	isShizukuInstalled: Boolean,
	isShizukuReady: Boolean,
	onSetPrivateMode: (Boolean) -> Unit,
	onSetShizuku: (Boolean) -> Unit,
	onAddStore: () -> Unit,
	onRefresh: () -> Unit,
	onRemoveStore: (String) -> Unit,
) {
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(14.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		item {
			Surface(
				color = NemuriTheme.colors.card,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
			) {
				Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Text(
						text = "APK INSTALLER BACKEND",
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.cyan,
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
							Text(
								text = "Private Sandbox Mode",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = "Loads extension APKs inside Nemuri without OS package installation.",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Switch(
							checked = isPrivateMode,
							onCheckedChange = onSetPrivateMode,
							colors = SwitchDefaults.colors(checkedTrackColor = NemuriTheme.colors.cyan),
						)
					}
					HorizontalDivider(color = NemuriTheme.colors.border)
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
							Text(
								text = "Shizuku Silent Installer",
								style = MaterialTheme.typography.titleSmall,
								color = NemuriTheme.colors.textPrimary,
							)
							Text(
								text = when {
									!isShizukuInstalled -> "Shizuku app is not installed."
									!isShizukuReady -> "Shizuku service running or permission not granted yet."
									else -> "Shizuku binder connected and authorized."
								},
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
							)
						}
						Switch(
							checked = isShizukuEnabled,
							onCheckedChange = onSetShizuku,
							enabled = isShizukuInstalled,
							colors = SwitchDefaults.colors(checkedTrackColor = NemuriTheme.colors.cyan),
						)
					}
				}
			}
		}

		item {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = "CONFIGURED REPOSITORIES",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.cyan,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = onRefresh) {
						Text("Refresh", color = NemuriTheme.colors.textSecondary)
					}
					TextButton(onClick = onAddStore) {
						Text("+ Add Store", color = NemuriTheme.colors.cyan)
					}
				}
			}
		}

		if (stores.isEmpty()) {
			item {
				Surface(
					color = NemuriTheme.colors.card,
					shape = RoundedCornerShape(16.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
				) {
					Column(
						modifier = Modifier.padding(20.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = "No repositories added yet",
							style = MaterialTheme.typography.titleSmall,
							color = NemuriTheme.colors.textPrimary,
						)
						Text(
							text = "Tap + Add Store or open a mihon:// / tachiyomi:// / kotatsu:// / lnreader:// repo link.",
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textMuted,
						)
					}
				}
			}
		} else {
			items(stores, key = { it.state.store.id }) { summary ->
				val st = summary.state
				Surface(
					color = NemuriTheme.colors.card,
					shape = RoundedCornerShape(16.dp),
					modifier = Modifier
						.fillMaxWidth()
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(16.dp)),
				) {
					Row(
						modifier = Modifier.padding(14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Text(
									text = summary.label,
									style = MaterialTheme.typography.titleSmall,
									color = NemuriTheme.colors.textPrimary,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								val (healthLabel, healthColor) = when (st.health) {
									StoreHealth.AVAILABLE -> "OK" to NemuriTheme.colors.healthy
									StoreHealth.CHECKING -> "SYNCING" to NemuriTheme.colors.amber
									StoreHealth.UNAVAILABLE -> "ERROR" to NemuriTheme.colors.danger
								}
								PillBadge(text = healthLabel, color = healthColor)
								summary.kind?.let { kind ->
									PillBadge(
										text = when (kind) {
											ExtensionStoreKind.MANGA -> "MANGA"
											ExtensionStoreKind.NOVEL -> "NOVEL"
											ExtensionStoreKind.MIXED -> "MIXED"
										},
										color = NemuriTheme.colors.cyan,
									)
								}
							}
							Text(
								text = "${st.catalog.size} extensions • ${st.store.indexUrl}",
								style = MaterialTheme.typography.labelSmall,
								color = NemuriTheme.colors.textMuted,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							st.error?.localizedMessage?.let { err ->
								Text(
									text = err,
									style = MaterialTheme.typography.labelSmall,
									color = NemuriTheme.colors.danger,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}
						IconButton(onClick = { onRemoveStore(st.store.id) }) {
							Icon(
								imageVector = Icons.Default.DeleteOutline,
								contentDescription = "Remove store",
								tint = NemuriTheme.colors.danger,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ExtensionIconBox(
	iconModel: Any?,
	fallbackName: String,
	isNovel: Boolean,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.size(44.dp)
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(12.dp)),
	) {
		if (iconModel == null) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					imageVector = if (isNovel) Icons.Default.MenuBook else Icons.Default.Extension,
					contentDescription = null,
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(22.dp),
				)
			}
		} else {
			SubcomposeAsyncImage(
				model = iconModel,
				contentDescription = fallbackName,
				contentScale = ContentScale.Crop,
				error = {
					Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
						Icon(
							imageVector = if (isNovel) Icons.Default.MenuBook else Icons.Default.Extension,
							contentDescription = null,
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(20.dp),
						)
					}
				},
				modifier = Modifier
					.fillMaxSize()
					.clip(RoundedCornerShape(12.dp)),
			)
		}
	}
}

@Composable
private fun FilterPillChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(999.dp),
		modifier = Modifier
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				RoundedCornerShape(999.dp),
			)
			.clip(RoundedCornerShape(999.dp))
			.clickable(onClick = onClick),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textSecondary,
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
		)
	}
}

@Composable
private fun PillBadge(
	text: String,
	color: Color,
) {
	Surface(
		color = color.copy(alpha = 0.16f),
		shape = RoundedCornerShape(6.dp),
	) {
		NonWrappingBadgeText(
			text = text,
			color = color,
			fontSizeSp = 9f,
			modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
		)
	}
}

@Composable
private fun LanguageRow(
	label: String,
	countText: String?,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = if (selected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.surfaceElevated,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.border(
				1.dp,
				if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				RoundedCornerShape(12.dp),
			)
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.titleSmall,
				color = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
			)
			if (countText != null) {
				Text(
					text = countText,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
		}
	}
}

private const val MIME_APK = "application/vnd.android.package-archive"
