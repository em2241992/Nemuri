package org.nemuri.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.nemuri.app.ui.theme.NemuriTheme

@Composable
fun NemuriTopSearchHeader(
	query: String,
	onQueryChange: (String) -> Unit,
	placeholder: String,
	activeFilterCount: Int = 0,
	onSearchSubmit: () -> Unit = {},
	onSearchIconClick: () -> Unit = onSearchSubmit,
	pillTrailingIcon: ImageVector? = null,
	pillTrailingContentDescription: String? = null,
	onPillTrailingClick: (() -> Unit)? = null,
	onTuneClick: (() -> Unit)? = null,
	onSettingsClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.background,
		modifier = modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.statusBarsPadding()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			val hasActiveFilters = activeFilterCount > 0
			Surface(
				color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.78f),
				shape = RoundedCornerShape(24.dp),
				modifier = Modifier
					.weight(1f)
					.height(44.dp)
					.border(
						width = 1.dp,
						color = if (hasActiveFilters) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
						shape = RoundedCornerShape(24.dp),
					),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Box(
						modifier = Modifier
							.size(34.dp)
							.clip(RoundedCornerShape(17.dp))
							.clickable(onClick = onSearchIconClick),
						contentAlignment = Alignment.Center,
					) {
						BadgedBox(
							badge = {
								if (hasActiveFilters) {
									Badge(
										containerColor = NemuriTheme.colors.cyan,
										contentColor = NemuriTheme.colors.onCyan,
									) {
										Text(activeFilterCount.toString())
									}
								}
							},
						) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = "Search",
								tint = if (hasActiveFilters) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
								modifier = Modifier.size(20.dp),
							)
						}
					}

					Box(
						modifier = Modifier
							.weight(1f)
							.padding(horizontal = 6.dp),
						contentAlignment = Alignment.CenterStart,
					) {
						if (query.isEmpty()) {
							Text(
								text = placeholder,
								style = MaterialTheme.typography.bodyMedium,
								color = NemuriTheme.colors.textDim,
								maxLines = 1,
							)
						}
						BasicTextField(
							value = query,
							onValueChange = onQueryChange,
							singleLine = true,
							textStyle = MaterialTheme.typography.bodyMedium.copy(
								color = NemuriTheme.colors.textPrimary,
							),
							cursorBrush = SolidColor(NemuriTheme.colors.cyan),
							keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
							keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
							modifier = Modifier.fillMaxWidth(),
						)
					}

					if (query.isNotEmpty()) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(RoundedCornerShape(16.dp))
								.clickable { onQueryChange("") },
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Default.Close,
								contentDescription = "Clear search",
								tint = NemuriTheme.colors.textMuted,
								modifier = Modifier.size(18.dp),
							)
						}
					}

					if (pillTrailingIcon != null && onPillTrailingClick != null) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(RoundedCornerShape(16.dp))
								.clickable(onClick = onPillTrailingClick),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = pillTrailingIcon,
								contentDescription = pillTrailingContentDescription,
								tint = NemuriTheme.colors.textMuted,
								modifier = Modifier.size(18.dp),
							)
						}
					}
				}
			}

			if (onTuneClick != null) {
				HeaderSquareAction(
					icon = Icons.Default.Tune,
					contentDescription = "Display & filter options",
					onClick = onTuneClick,
				)
			}

			HeaderSquareAction(
				icon = Icons.Default.Settings,
				contentDescription = "Settings",
				onClick = onSettingsClick,
			)
		}
	}
}

@Composable
fun HeaderSquareAction(
	icon: ImageVector,
	contentDescription: String,
	onClick: () -> Unit,
	showBadgeDot: Boolean = false,
	tint: androidx.compose.ui.graphics.Color = NemuriTheme.colors.textSecondary,
) {
	Surface(
		color = NemuriTheme.colors.surfaceElevated.copy(alpha = 0.72f),
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.size(40.dp)
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
	) {
		Box(contentAlignment = Alignment.Center) {
			BadgedBox(
				badge = {
					if (showBadgeDot) {
						Badge(
							containerColor = NemuriTheme.colors.cyan,
							modifier = Modifier.size(6.dp),
						)
					}
				},
			) {
				Icon(
					imageVector = icon,
					contentDescription = contentDescription,
					tint = tint,
					modifier = Modifier.size(19.dp),
				)
			}
		}
	}
}
