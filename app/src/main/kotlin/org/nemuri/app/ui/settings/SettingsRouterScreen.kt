package org.nemuri.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nemuri.app.navigation.SettingsSection

@Composable
fun SettingsRouterScreen(
	section: SettingsSection,
	onNavigateSection: (SettingsSection) -> Unit,
	onOpenExtensionsCatalog: () -> Unit,
	onBack: () -> Unit,
	onOpenOnboarding: (() -> Unit)? = null,
	settingsViewModel: SettingsViewModel = viewModel(),
) {
	val handleRerunSetupWizard: () -> Unit = {
		settingsViewModel.rerunSetupWizard()
		onOpenOnboarding?.invoke()
	}

	when (section) {
		SettingsSection.ROOT -> SettingsRootScreen(
			viewModel = settingsViewModel,
			onNavigateSection = onNavigateSection,
			onBack = onBack,
			onRerunSetupWizard = handleRerunSetupWizard,
		)

		SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
		)

		SettingsSection.EXTENSIONS -> ExtensionsSettingsScreen(
			viewModel = settingsViewModel,
			onOpenExtensionsCatalog = onOpenExtensionsCatalog,
			onBack = onBack,
		)

		SettingsSection.READING -> ReadingSettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
		)

		SettingsSection.DOWNLOADS_STORAGE -> DownloadsStorageSettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
		)

		SettingsSection.UPDATES -> UpdatesSettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
		)

		SettingsSection.BACKUP_RESTORE -> BackupSettingsScreen(
			onBack = onBack,
		)

		SettingsSection.TRACKERS -> TrackersScreen(
			onBack = onBack,
		)

		SettingsSection.CLOUD_SYNC -> CloudSyncScreen(
			onOpenLocalBackups = { onNavigateSection(SettingsSection.BACKUP_RESTORE) },
			onBack = onBack,
		)

		SettingsSection.PRIVACY -> PrivacySettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
		)

		SettingsSection.ABOUT -> AboutSettingsScreen(
			viewModel = settingsViewModel,
			onBack = onBack,
			onRerunSetupWizard = handleRerunSetupWizard,
		)
	}
}
