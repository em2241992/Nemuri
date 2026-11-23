package org.nemuri.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.AppTab
import org.nemuri.app.ui.theme.NemuriTheme

private fun AppTab.icon(): ImageVector = when (this) {
	AppTab.LIBRARY -> Icons.Default.LocalLibrary
	AppTab.HISTORY -> Icons.Default.History
	AppTab.FEED -> Icons.Default.RssFeed
	AppTab.EXPLORE -> Icons.Default.Explore
	AppTab.ENHANCE -> Icons.Default.AutoAwesome
}

@Composable
fun NemuriFloatingDockBar(
	currentTab: AppTab,
	unreadFeedCount: Int,
	hasExtensionUpdates: Boolean,
	isIncognito: Boolean,
	recentReads: List<Manga>,
	onSelectTab: (AppTab) -> Unit,
	onContinueReading: (Manga?) -> Unit,
	onToggleIncognito: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var showContinuePeek by remember { mutableStateOf(false) }

	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.BottomCenter,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(52.dp)
				.align(Alignment.BottomCenter)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color.Transparent,
							NemuriTheme.colors.background.copy(alpha = 0.94f),
						),
					),
				),
		)

		Column(
			modifier = Modifier
				.fillMaxWidth()
				.navigationBarsPadding()
				.padding(horizontal = 12.dp, vertical = 12.dp),
			horizontalAlignment = Alignment.End,
		) {
			AnimatedVisibility(
				visible = showContinuePeek,
				enter = fadeIn(),
				exit = fadeOut(),
			) {
				ContinuePeekCard(
					isIncognito = isIncognito,
					recentReads = recentReads,
					onToggleIncognito = onToggleIncognito,
					onOpenManga = { manga ->
						showContinuePeek = false
						onContinueReading(manga)
					},
					onDismiss = { showContinuePeek = false },
					modifier = Modifier.padding(bottom = 10.dp),
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Surface(
					color = NemuriTheme.colors.dock.copy(alpha = 0.96f),
					tonalElevation = 8.dp,
					shadowElevation = 14.dp,
					shape = RoundedCornerShape(32.dp),
					modifier = Modifier
						.height(56.dp)
						.border(1.dp, Color(0xFF364044), RoundedCornerShape(32.dp)),
				) {
					Row(
						modifier = Modifier.padding(6.dp),
						horizontalArrangement = Arrangement.spacedBy(4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						AppTab.entries.forEach { tab ->
							val selected = tab == currentTab
							DockTabPill(
								tab = tab,
								selected = selected,
								badgeCount = if (tab == AppTab.FEED) unreadFeedCount else 0,
								showDot = tab == AppTab.EXPLORE && hasExtensionUpdates,
								onClick = {
									showContinuePeek = false
									onSelectTab(tab)
								},
							)
						}
					}
				}

				Spacer(modifier = Modifier.width(10.dp))

				ContinueReadingFab(
					isIncognito = isIncognito,
					onClick = {
						showContinuePeek = false
						onContinueReading(recentReads.firstOrNull())
					},
					onLongClick = { showContinuePeek = !showContinuePeek },
				)
			}
		}
	}
}

@Composable
private fun DockTabPill(
	tab: AppTab,
	selected: Boolean,
	badgeCount: Int,
	showDot: Boolean,
	onClick: () -> Unit,
) {
	val containerColor = if (selected) {
		MaterialTheme.colorScheme.primary
	} else {
		Color.Transparent
	}
	val contentTint = if (selected) {
		MaterialTheme.colorScheme.onPrimary
	} else {
		Color(0xFFC4CDD0)
	}

	Surface(
		color = containerColor,
		shape = RoundedCornerShape(24.dp),
		modifier = Modifier
			.height(44.dp)
			.clip(RoundedCornerShape(24.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(
				horizontal = if (selected) 13.dp else 10.dp,
				vertical = 6.dp,
			),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			BadgedBox(
				badge = {
					when {
						badgeCount > 0 -> Badge(
							containerColor = Color(0xFFF6B7B9),
							contentColor = Color(0xFF4A1215),
						) {
							Text(
								text = if (badgeCount > 99) "99+" else badgeCount.toString(),
								fontSize = 9.sp,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
							)
						}
						showDot -> Badge(
							containerColor = NemuriTheme.colors.cyan,
							modifier = Modifier.size(7.dp),
						)
					}
				},
			) {
				Icon(
					imageVector = tab.icon(),
					contentDescription = tab.label,
					tint = contentTint,
					modifier = Modifier.size(20.dp),
				)
			}
			if (selected) {
				Text(
					text = tab.label,
					color = contentTint,
					fontSize = 13.sp,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					softWrap = false,
				)
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingFab(
	isIncognito: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val fill = if (isIncognito) Color(0xFF19242E) else MaterialTheme.colorScheme.primary
	val iconTint = if (isIncognito) NemuriTheme.colors.cyan else MaterialTheme.colorScheme.onPrimary
	val borderColor = if (isIncognito) NemuriTheme.colors.cyan else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)

	Box {
		Surface(
			color = fill,
			shape = RoundedCornerShape(32.dp),
			shadowElevation = 12.dp,
			modifier = Modifier
				.size(56.dp)
				.border(if (isIncognito) 2.dp else 1.dp, borderColor, RoundedCornerShape(32.dp))
				.clip(RoundedCornerShape(32.dp))
				.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
				),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.MenuBook,
					contentDescription = "Continue reading",
					tint = iconTint,
					modifier = Modifier.size(24.dp),
				)
			}
		}
		if (isIncognito) {
			Surface(
				color = NemuriTheme.colors.cyan,
				shape = CircleShape,
				modifier = Modifier
					.size(20.dp)
					.align(Alignment.TopEnd),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = "Incognito",
						tint = NemuriTheme.colors.onCyan,
						modifier = Modifier.size(12.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun ContinuePeekCard(
	isIncognito: Boolean,
	recentReads: List<Manga>,
	onToggleIncognito: () -> Unit,
	onOpenManga: (Manga?) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = Color(0xFF161C22).copy(alpha = 0.98f),
		shape = RoundedCornerShape(22.dp),
		shadowElevation = 18.dp,
		modifier = modifier
			.widthIn(min = 276.dp, max = 328.dp)
			.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
	) {
		Column(
			modifier = Modifier.padding(14.dp),
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
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = null,
						tint = if (isIncognito) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = "Incognito mode",
						style = MaterialTheme.typography.titleSmall,
						color = NemuriTheme.colors.textPrimary,
					)
				}
				Switch(
					checked = isIncognito,
					onCheckedChange = { onToggleIncognito() },
				)
			}

			HorizontalDivider(color = NemuriTheme.colors.border)

			val latest = recentReads.firstOrNull()
			if (latest != null) {
				Button(
					onClick = { onOpenManga(latest) },
					colors = ButtonDefaults.buttonColors(
						containerColor = NemuriTheme.colors.cyan,
						contentColor = NemuriTheme.colors.onCyan,
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Icon(
						imageVector = Icons.Default.PlayArrow,
						contentDescription = null,
						modifier = Modifier.size(18.dp),
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(
						text = "Resume: ${latest.title}",
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			} else {
				Text(
					text = "No recent reading history yet.",
					style = MaterialTheme.typography.bodySmall,
					color = NemuriTheme.colors.textMuted,
					modifier = Modifier.clickable(onClick = onDismiss),
				)
			}

			if (recentReads.size > 1) {
				Text(
					text = "RECENT READS",
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textDim,
				)
				recentReads.drop(1).take(3).forEach { manga ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(10.dp))
							.clickable { onOpenManga(manga) }
							.padding(vertical = 4.dp, horizontal = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						MangaCoverImage(
							manga = manga,
							coverUrl = manga.coverUrl,
							modifier = Modifier
								.size(width = 28.dp, height = 38.dp)
								.clip(RoundedCornerShape(6.dp)),
						)
						Text(
							text = manga.title,
							style = MaterialTheme.typography.bodySmall,
							color = NemuriTheme.colors.textSecondary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}
}

@Composable
fun NemuriNavigationRail(
	currentTab: AppTab,
	unreadFeedCount: Int,
	hasExtensionUpdates: Boolean,
	onSelectTab: (AppTab) -> Unit,
	modifier: Modifier = Modifier,
) {
	NavigationRail(
		containerColor = NemuriTheme.colors.surface,
		contentColor = NemuriTheme.colors.textPrimary,
		modifier = modifier,
	) {
		Spacer(modifier = Modifier.height(16.dp))
		NemuriBrandLogo(
			modifier = Modifier
				.size(36.dp)
				.padding(bottom = 12.dp),
		)
		AppTab.entries.forEach { tab ->
			NavigationRailItem(
				selected = tab == currentTab,
				onClick = { onSelectTab(tab) },
				icon = {
					BadgedBox(
						badge = {
							when {
								tab == AppTab.FEED && unreadFeedCount > 0 -> Badge(
									containerColor = Color(0xFFF6B7B9),
									contentColor = Color(0xFF4A1215),
								) {
									Text(
										text = if (unreadFeedCount > 99) "99+" else unreadFeedCount.toString(),
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
									)
								}
								tab == AppTab.EXPLORE && hasExtensionUpdates -> Badge(
									containerColor = NemuriTheme.colors.cyan,
									modifier = Modifier.size(7.dp),
								)
							}
						},
					) {
						Icon(tab.icon(), contentDescription = tab.label)
					}
				},
				label = { Text(tab.label, maxLines = 1) },
				colors = NavigationRailItemDefaults.colors(
					selectedIconColor = MaterialTheme.colorScheme.onPrimary,
					selectedTextColor = MaterialTheme.colorScheme.primary,
					indicatorColor = MaterialTheme.colorScheme.primary,
					unselectedIconColor = NemuriTheme.colors.textMuted,
					unselectedTextColor = NemuriTheme.colors.textMuted,
				),
			)
		}
	}
}
