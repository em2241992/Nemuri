package org.nemuri.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nemuri.app.ui.theme.NemuriTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScaffold(
	title: String,
	onBack: () -> Unit,
	subtitle: String? = null,
	actions: @Composable RowScope.() -> Unit = {},
	content: @Composable (PaddingValues) -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.statusBarsPadding()
			.navigationBarsPadding(),
	) {
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
					text = title,
					style = MaterialTheme.typography.titleLarge,
					color = NemuriTheme.colors.textPrimary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (!subtitle.isNullOrBlank()) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
			actions()
		}
		HorizontalDivider(color = NemuriTheme.colors.border)
		Box(modifier = Modifier.fillMaxSize()) {
			content(PaddingValues(horizontal = 16.dp, vertical = 14.dp))
		}
	}
}

@Composable
fun SettingsLazyPage(
	title: String,
	onBack: () -> Unit,
	subtitle: String? = null,
	actions: @Composable RowScope.() -> Unit = {},
	content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
	SettingsScaffold(
		title = title,
		onBack = onBack,
		subtitle = subtitle,
		actions = actions,
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp,
				end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = 28.dp,
			),
			verticalArrangement = Arrangement.spacedBy(16.dp),
			content = content,
		)
	}
}

@Composable
fun SettingsGroupCard(
	title: String? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		if (!title.isNullOrBlank()) {
			Text(
				text = title.uppercase(Locale.ROOT),
				style = MaterialTheme.typography.labelSmall,
				color = NemuriTheme.colors.textDim,
				modifier = Modifier.padding(horizontal = 4.dp),
			)
		}
		Surface(
			color = NemuriTheme.colors.card,
			shape = RoundedCornerShape(16.dp),
			border = BorderStroke(1.dp, NemuriTheme.colors.border),
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(modifier = Modifier.fillMaxWidth(), content = content)
		}
	}
}

@Composable
fun SettingsRowDivider() {
	HorizontalDivider(
		color = NemuriTheme.colors.border.copy(alpha = 0.65f),
		modifier = Modifier.padding(horizontal = 16.dp),
	)
}

@Composable
fun SettingsNavRow(
	icon: ImageVector,
	title: String,
	subtitle: String? = null,
	iconTint: Color = NemuriTheme.colors.cyan,
	badgeText: String? = null,
	badgeColor: Color = NemuriTheme.colors.tealContainer,
	badgeTextColor: Color = NemuriTheme.colors.cyan,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Surface(
			color = iconTint.copy(alpha = 0.14f),
			shape = RoundedCornerShape(10.dp),
			modifier = Modifier.size(36.dp),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = iconTint,
					modifier = Modifier.size(20.dp),
				)
			}
		}
		Spacer(Modifier.width(14.dp))
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				color = NemuriTheme.colors.textPrimary,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
		if (!badgeText.isNullOrBlank()) {
			Surface(
				color = badgeColor,
				shape = RoundedCornerShape(999.dp),
				modifier = Modifier.padding(end = 6.dp),
			) {
				Text(
					text = badgeText,
					style = MaterialTheme.typography.labelSmall,
					color = badgeTextColor,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
				)
			}
		}
		Icon(
			imageVector = Icons.Default.ChevronRight,
			contentDescription = null,
			tint = NemuriTheme.colors.textDim,
		)
	}
}

@Composable
fun SettingsToggleRow(
	title: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	subtitle: String? = null,
	icon: ImageVector? = null,
	enabled: Boolean = true,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = enabled) { onCheckedChange(!checked) }
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (icon != null) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = if (enabled) NemuriTheme.colors.cyan else NemuriTheme.colors.textDim,
				modifier = Modifier.size(22.dp),
			)
			Spacer(Modifier.width(14.dp))
		}
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
				color = if (enabled) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
		}
		Spacer(Modifier.width(12.dp))
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			enabled = enabled,
			colors = SwitchDefaults.colors(
				checkedThumbColor = NemuriTheme.colors.onCyan,
				checkedTrackColor = NemuriTheme.colors.cyan,
				uncheckedThumbColor = NemuriTheme.colors.textMuted,
				uncheckedTrackColor = NemuriTheme.colors.surfaceElevated,
				uncheckedBorderColor = NemuriTheme.colors.border,
			),
		)
	}
}

@Composable
fun SettingsChoiceRow(
	title: String,
	currentLabel: String,
	onClick: () -> Unit,
	subtitle: String? = null,
	icon: ImageVector? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (icon != null) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = NemuriTheme.colors.cyan,
				modifier = Modifier.size(22.dp),
			)
			Spacer(Modifier.width(14.dp))
		}
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
				color = NemuriTheme.colors.textPrimary,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
		}
		Spacer(Modifier.width(10.dp))
		Surface(
			color = NemuriTheme.colors.surfaceElevated,
			shape = RoundedCornerShape(8.dp),
			border = BorderStroke(1.dp, NemuriTheme.colors.border),
		) {
			Text(
				text = currentLabel,
				style = MaterialTheme.typography.labelMedium,
				color = NemuriTheme.colors.cyan,
				modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
			)
		}
	}
}

@Composable
fun SettingsActionRow(
	icon: ImageVector,
	title: String,
	subtitle: String? = null,
	tint: Color = NemuriTheme.colors.cyan,
	enabled: Boolean = true,
	trailing: @Composable (() -> Unit)? = null,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			tint = if (enabled) tint else NemuriTheme.colors.textDim,
			modifier = Modifier.size(22.dp),
		)
		Spacer(Modifier.width(14.dp))
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
				color = if (enabled) NemuriTheme.colors.textPrimary else NemuriTheme.colors.textDim,
			)
			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
				)
			}
		}
		if (trailing != null) {
			Spacer(Modifier.width(10.dp))
			trailing()
		}
	}
}

@Composable
fun SettingsSliderRow(
	title: String,
	valueLabel: String,
	value: Float,
	valueRange: ClosedFloatingPointRange<Float>,
	steps: Int = 0,
	subtitle: String? = null,
	onValueChange: (Float) -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 12.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
					color = NemuriTheme.colors.textPrimary,
				)
				if (!subtitle.isNullOrBlank()) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = NemuriTheme.colors.textMuted,
					)
				}
			}
			Text(
				text = valueLabel,
				style = MaterialTheme.typography.labelLarge,
				color = NemuriTheme.colors.cyan,
			)
		}
		Spacer(Modifier.height(4.dp))
		Slider(
			value = value,
			onValueChange = onValueChange,
			valueRange = valueRange,
			steps = steps,
			colors = SliderDefaults.colors(
				thumbColor = NemuriTheme.colors.cyan,
				activeTrackColor = NemuriTheme.colors.cyan,
				inactiveTrackColor = NemuriTheme.colors.surfaceElevated,
			),
		)
	}
}

@Composable
fun <T> SettingsOptionDialog(
	title: String,
	options: List<Pair<T, String>>,
	selected: T,
	onSelect: (T) -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = NemuriTheme.colors.sheet,
		titleContentColor = NemuriTheme.colors.textPrimary,
		title = { Text(title) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				options.forEach { (option, label) ->
					val isSelected = option == selected
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable {
								onSelect(option)
								onDismiss()
							}
							.padding(vertical = 8.dp, horizontal = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						RadioButton(
							selected = isSelected,
							onClick = {
								onSelect(option)
								onDismiss()
							},
							colors = RadioButtonDefaults.colors(
								selectedColor = NemuriTheme.colors.cyan,
								unselectedColor = NemuriTheme.colors.textMuted,
							),
						)
						Spacer(Modifier.width(8.dp))
						Text(
							text = label,
							style = MaterialTheme.typography.bodyLarge,
							color = NemuriTheme.colors.textPrimary,
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text("Close", color = NemuriTheme.colors.cyan)
			}
		},
	)
}

fun formatByteSize(bytes: Long): String {
	if (bytes <= 0L) return "0 B"
	val kb = 1024.0
	val mb = kb * 1024.0
	val gb = mb * 1024.0
	return when {
		bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
		bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
		bytes >= kb -> "${(bytes / kb).roundToInt()} KB"
		else -> "$bytes B"
	}
}
