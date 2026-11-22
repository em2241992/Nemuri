package org.nemuri.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.sync.SyncFeature
import javax.inject.Inject
import javax.inject.Provider

private val ObsidianBg = Color(0xFF090C10)
private val ObsidianSurface = Color(0xFF12161F)
private val ObsidianBorder = Color(0xFF1E2634)
private val NemuriCyan = Color(0xFF5CE1E6)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

private val NemuriDarkColors = darkColorScheme(
	primary = NemuriCyan,
	onPrimary = ObsidianBg,
	background = ObsidianBg,
	onBackground = TextPrimary,
	surface = ObsidianSurface,
	onSurface = TextPrimary,
	surfaceVariant = ObsidianSurface,
	onSurfaceVariant = TextSecondary,
)

private data class EngineDiagnostics(
	val roomSchemaVersion: Int,
	val installedMihonExtensions: Int,
	val enabledSources: Int,
	val driveSyncAvailable: Boolean,
	val gridSizePercent: Int,
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var mihonExtensionManager: MihonExtensionManager

	@Inject
	lateinit var sourcesRepository: MangaSourcesRepository

	@Inject
	lateinit var syncFeature: SyncFeature

	private var currentRoute by mutableStateOf<String?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		currentRoute = intent?.getStringExtra(AppRouter.KEY_ROUTE)
		setContent {
			MaterialTheme(colorScheme = NemuriDarkColors) {
				var diagnostics by androidx.compose.runtime.remember { mutableStateOf<EngineDiagnostics?>(null) }
				LaunchedEffect(Unit) {
					diagnostics = withContext(Dispatchers.IO) {
						EngineDiagnostics(
							roomSchemaVersion = database.get().openHelper.readableDatabase.version,
							installedMihonExtensions = mihonExtensionManager.installedExtensions.value.size,
							enabledSources = sourcesRepository.getEnabledSources().size,
							driveSyncAvailable = SyncFeature.IS_AVAILABLE,
							gridSizePercent = settings.gridSize,
						)
					}
				}
				EngineBootstrapScreen(
					route = currentRoute,
					diagnostics = diagnostics,
				)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		currentRoute = intent.getStringExtra(AppRouter.KEY_ROUTE)
	}
}

@Composable
private fun EngineBootstrapScreen(
	route: String?,
	diagnostics: EngineDiagnostics?,
) {
	Surface(
		modifier = Modifier
			.fillMaxSize()
			.background(ObsidianBg)
			.systemBarsPadding(),
		color = ObsidianBg,
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(
					text = "NEMURI",
					color = NemuriCyan,
					fontSize = 26.sp,
					fontWeight = FontWeight.Bold,
					letterSpacing = 3.sp,
				)
				Text(
					text = "Headless Engine Shell • Obsidian Diagnostics",
					color = TextSecondary,
					fontSize = 13.sp,
				)
			}

			DiagnosticsCard(title = "Application & Flavor") {
				DiagnosticRow("Application ID", BuildConfig.APPLICATION_ID)
				DiagnosticRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
				DiagnosticRow("Flavor / Type", "${BuildConfig.FLAVOR} / ${BuildConfig.BUILD_TYPE}")
				DiagnosticRow("Incoming Route", route ?: "launcher")
			}

			DiagnosticsCard(title = ":engine Hilt Telemetry") {
				if (diagnostics == null) {
					Text(
						text = "Initializing engine singletons…",
						color = TextSecondary,
						fontSize = 13.sp,
					)
				} else {
					DiagnosticRow("Room Schema", "v${diagnostics.roomSchemaVersion}")
					DiagnosticRow("Enabled Sources", diagnostics.enabledSources.toString())
					DiagnosticRow("Mihon Extensions", diagnostics.installedMihonExtensions.toString())
					DiagnosticRow("Drive Sync Available", diagnostics.driveSyncAvailable.toString())
					DiagnosticRow("Grid Size Setting", "${diagnostics.gridSizePercent}%")
				}
			}

			Spacer(modifier = Modifier.height(8.dp))
		}
	}
}

@Composable
private fun DiagnosticsCard(
	title: String,
	content: @Composable () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
			.background(ObsidianSurface, RoundedCornerShape(14.dp))
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Text(
			text = title,
			color = NemuriCyan,
			fontSize = 13.sp,
			fontWeight = FontWeight.SemiBold,
			letterSpacing = 0.5.sp,
		)
		HorizontalDivider(color = ObsidianBorder)
		content()
	}
}

@Composable
private fun DiagnosticRow(
	label: String,
	value: String,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = label,
			color = TextSecondary,
			fontSize = 13.sp,
		)
		Text(
			text = value,
			color = TextPrimary,
			fontSize = 13.sp,
			fontFamily = FontFamily.Monospace,
		)
	}
}
