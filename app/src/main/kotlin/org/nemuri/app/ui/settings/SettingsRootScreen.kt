package org.nemuri.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.sync.SyncFeature
import org.nemuri.app.BuildConfig
import org.nemuri.app.navigation.SettingsSection
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun SettingsRootScreen(
	viewModel: SettingsViewModel,
	onNavigateSection: (SettingsSection) -> Unit,
	onBack: () -> Unit,
	onRerunSetupWizard: () -> Unit = { viewModel.rerunSetupWizard() },
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	val themeLabel = when (state.themeMode) {
		AppCompatDelegate.MODE_NIGHT_NO -> "Light"
		AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
		else -> "System"
	}
	val schemeName = colorSchemeDisplayLabel(state.colorScheme)
	val appearanceSubtitle = buildString {
		append(themeLabel)
		if (state.isAmoled) append(" + AMOLED")
		append(" • ")
		append(schemeName)
	}

	val syncSubtitle = when {
		!SyncFeature.IS_AVAILABLE -> "Requires Google Play Services (GitHub build)"
		state.syncSnapshot.isSignedIn -> state.syncSnapshot.accountName ?: state.syncSnapshot.accountEmail ?: "Connected"
		else -> "Not signed in — multi-device library sync"
	}

	val isBetaBuild = BuildConfig.DEBUG || BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)

	SettingsLazyPage(
		title = SettingsSection.ROOT.title,
		onBack = onBack,
		actions = {
			if (isBetaBuild) {
				Surface(
					color = NemuriTheme.colors.amber.copy(alpha = 0.16f),
					shape = RoundedCornerShape(999.dp),
					border = BorderStroke(1.dp, NemuriTheme.colors.amber.copy(alpha = 0.6f)),
					modifier = Modifier.padding(end = 8.dp),
				) {
					Text(
						text = "BETA",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Bold,
						color = NemuriTheme.colors.amber,
						modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
					)
				}
			}
		},
	) {
		item {
			SettingsGroupCard("Personalization & Reading") {
				SettingsNavRow(
					icon = Icons.Default.Palette,
					title = SettingsSection.APPEARANCE.title,
					subtitle = appearanceSubtitle,
					onClick = { onNavigateSection(SettingsSection.APPEARANCE) },
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.AutoMirrored.Filled.MenuBook,
					title = SettingsSection.READING.title,
					subtitle = "Mode: ${state.defaultReaderMode.name.lowercase().replaceFirstChar { it.uppercase() }} • Novel font ${state.epubFontSize}%",
					onClick = { onNavigateSection(SettingsSection.READING) },
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.Default.Extension,
					title = SettingsSection.EXTENSIONS.title,
					subtitle = if (state.isPrivateInstall) "Private sandbox mode" else "System package installer",
					onClick = { onNavigateSection(SettingsSection.EXTENSIONS) },
				)
			}
		}

		item {
			SettingsGroupCard("Library & Content") {
				SettingsNavRow(
					icon = Icons.Default.Download,
					title = SettingsSection.DOWNLOADS_STORAGE.title,
					subtitle = if (state.storageStats.isLoading) {
						"Downloads, formats & cache sizes"
					} else {
						"Downloads ${formatByteSize(state.storageStats.downloadsBytes)} • Cache ${formatByteSize(state.storageStats.totalCacheBytes)}"
					},
					onClick = { onNavigateSection(SettingsSection.DOWNLOADS_STORAGE) },
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.Default.Update,
					title = SettingsSection.UPDATES.title,
					subtitle = if (state.isTrackerEnabled) "Background checks active • ${state.trackerFrequencyFactor}x" else "Background checks paused",
					onClick = { onNavigateSection(SettingsSection.UPDATES) },
				)
			}
		}

		item {
			SettingsGroupCard("Data, Sync & Trackers") {
				SettingsNavRow(
					icon = Icons.Default.Backup,
					title = SettingsSection.BACKUP_RESTORE.title,
					subtitle = "Kotatsu .bk.zip & Mihon .tachibk import/export",
					onClick = { onNavigateSection(SettingsSection.BACKUP_RESTORE) },
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.Default.CloudSync,
					title = SettingsSection.CLOUD_SYNC.title,
					subtitle = syncSubtitle,
					badgeText = if (SyncFeature.IS_AVAILABLE && state.syncSnapshot.isSignedIn) "Active" else null,
					badgeColor = NemuriTheme.colors.healthy.copy(alpha = 0.16f),
					badgeTextColor = NemuriTheme.colors.healthy,
					onClick = { onNavigateSection(SettingsSection.CLOUD_SYNC) },
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.Default.Sync,
					title = SettingsSection.TRACKERS.title,
					subtitle = "AniList, MAL, Shikimori, Kitsu, MangaBaka, Comick & Discord RPC",
					onClick = { onNavigateSection(SettingsSection.TRACKERS) },
				)
			}
		}

		item {
			SettingsGroupCard("Security & App") {
				SettingsNavRow(
					icon = Icons.Default.Security,
					title = SettingsSection.PRIVACY.title,
					subtitle = buildString {
						if (state.isIncognitoEnabled) append("Incognito ON • ")
						append("DoH: ${dohProviderLabel(state.dnsOverHttps)}")
					},
					onClick = { onNavigateSection(SettingsSection.PRIVACY) },
				)
				SettingsRowDivider()
				SettingsActionRow(
					icon = Icons.Default.AutoAwesome,
					title = "Re-run Setup Wizard",
					subtitle = "Launch the Nemuri welcome & configuration tour again",
					onClick = onRerunSetupWizard,
				)
				SettingsRowDivider()
				SettingsNavRow(
					icon = Icons.Default.Info,
					title = SettingsSection.ABOUT.title,
					subtitle = "Nemuri v${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
					onClick = { onNavigateSection(SettingsSection.ABOUT) },
				)
			}
		}

		item {
			Text(
				text = "Nemuri v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE} (${BuildConfig.FLAVOR})",
				style = MaterialTheme.typography.bodySmall,
				color = NemuriTheme.colors.textDim,
				textAlign = TextAlign.Center,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp),
			)
		}
	}
}
