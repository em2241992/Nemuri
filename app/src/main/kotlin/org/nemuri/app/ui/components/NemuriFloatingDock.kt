package org.nemuri.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.RssFeed
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koitharu.kotatsu.core.prefs.NavLabelVisibility
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.navigation.AppTab
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriEffectsSpring
import org.nemuri.app.ui.theme.nemuriFastSpatialSpring
import org.nemuri.app.ui.theme.yorhaTacticalBrackets

private fun AppTab.icon(selected: Boolean): ImageVector = when (this) {
	AppTab.LIBRARY -> if (selected) Icons.Filled.LocalLibrary else Icons.Outlined.LocalLibrary
	AppTab.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
	AppTab.FEED -> if (selected) Icons.Filled.RssFeed else Icons.Outlined.RssFeed
	AppTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
	AppTab.ENHANCE -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
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
	isCompactHeight: Boolean = LocalLayoutStudioPrefs.current.isNavBarCompact,
	labelVisibility: NavLabelVisibility = LocalLayoutStudioPrefs.current.navLabelVisibility,
	modifier: Modifier = Modifier,
) {
	var showContinuePeek by remember { mutableStateOf(false) }
	val isAlwaysStacked = labelVisibility == NavLabelVisibility.ALWAYS
	val targetCapsuleHeight = when {
		isAlwaysStacked && isCompactHeight -> 50.dp
		isAlwaysStacked -> 56.dp
		isCompactHeight -> 46.dp
		else -> 52.dp
	}
	val barHeight by animateDpAsState(
		targetValue = targetCapsuleHeight,
		animationSpec = nemuriFastSpatialSpring(),
		label = "dockBarHeight",
	)
	val pillHeight = when {
		isAlwaysStacked && isCompactHeight -> 42.dp
		isAlwaysStacked -> 46.dp
		isCompactHeight -> 36.dp
		else -> 42.dp
	}
	val fabSize = if (isCompactHeight) 46.dp else 52.dp
	val dockSurface = NemuriTheme.colors.dock

	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.BottomCenter,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(54.dp)
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
				.padding(horizontal = 12.dp, vertical = 10.dp),
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
					color = dockSurface.copy(alpha = 0.96f),
					tonalElevation = 4.dp,
					shadowElevation = 12.dp,
					shape = RoundedCornerShape(28.dp),
					modifier = Modifier
						.weight(1f, fill = isAlwaysStacked)
						.height(barHeight)
						.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(28.dp))
						.yorhaTacticalBrackets(inset = 4.dp),
				) {
					Row(
						modifier = Modifier
							.let { if (isAlwaysStacked) it.fillMaxWidth() else it }
							.padding(horizontal = 5.dp, vertical = 4.dp),
						horizontalArrangement = Arrangement.spacedBy(if (isAlwaysStacked) 2.dp else 3.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						AppTab.entries.forEach { tab ->
							val selected = tab == currentTab
							DockTabPill(
								tab = tab,
								selected = selected,
								badgeCount = if (tab == AppTab.FEED) unreadFeedCount else 0,
								showDot = tab == AppTab.EXPLORE && hasExtensionUpdates,
								pillHeight = pillHeight,
								labelVisibility = labelVisibility,
								dockSurface = dockSurface,
								onClick = {
									showContinuePeek = false
									onSelectTab(tab)
								},
								modifier = if (isAlwaysStacked) Modifier.weight(1f) else Modifier,
							)
						}
					}
				}

				Spacer(modifier = Modifier.width(8.dp))

				ContinueReadingFab(
					isIncognito = isIncognito,
					fabSize = fabSize,
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
fun NemuriClassicBottomBar(
	currentTab: AppTab,
	unreadFeedCount: Int,
	hasExtensionUpdates: Boolean,
	isIncognito: Boolean,
	recentReads: List<Manga>,
	onSelectTab: (AppTab) -> Unit,
	onContinueReading: (Manga?) -> Unit,
	onToggleIncognito: () -> Unit,
	isCompactHeight: Boolean = LocalLayoutStudioPrefs.current.isNavBarCompact,
	labelVisibility: NavLabelVisibility = LocalLayoutStudioPrefs.current.navLabelVisibility,
	modifier: Modifier = Modifier,
) {
	var showContinuePeek by remember { mutableStateOf(false) }
	val barHeight = if (isCompactHeight) 58.dp else 66.dp
	val fabSize = if (isCompactHeight) 46.dp else 52.dp
	val dockSurface = NemuriTheme.colors.dock

	Column(
		modifier = modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.End,
	) {
		Column(
			modifier = Modifier.padding(end = 14.dp, bottom = 10.dp),
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

			ContinueReadingFab(
				isIncognito = isIncognito,
				fabSize = fabSize,
				onClick = {
					showContinuePeek = false
					onContinueReading(recentReads.firstOrNull())
				},
				onLongClick = { showContinuePeek = !showContinuePeek },
			)
		}

		Surface(
			color = dockSurface.copy(alpha = 0.98f),
			tonalElevation = 3.dp,
			shadowElevation = 10.dp,
			modifier = Modifier
				.fillMaxWidth()
				.yorhaTacticalBrackets(inset = 3.dp),
		) {
			Column(modifier = Modifier.fillMaxWidth()) {
				HorizontalDivider(
					thickness = 1.dp,
					color = NemuriTheme.colors.border,
				)
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.navigationBarsPadding()
						.height(barHeight)
						.padding(horizontal = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					AppTab.entries.forEach { tab ->
						val selected = tab == currentTab
						ClassicBarTabItem(
							tab = tab,
							selected = selected,
							badgeCount = if (tab == AppTab.FEED) unreadFeedCount else 0,
							showDot = tab == AppTab.EXPLORE && hasExtensionUpdates,
							labelVisibility = labelVisibility,
							dockSurface = dockSurface,
							onClick = {
								showContinuePeek = false
								onSelectTab(tab)
							},
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
						)
					}
				}
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
	pillHeight: Dp,
	labelVisibility: NavLabelVisibility,
	dockSurface: Color,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val containerColor by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
		animationSpec = nemuriEffectsSpring(),
		label = "dockPillContainer",
	)
	val borderColor by animateColorAsState(
		targetValue = if (selected) NemuriTheme.colors.cyan.copy(alpha = 0.42f) else Color.Transparent,
		animationSpec = nemuriEffectsSpring(),
		label = "dockPillBorder",
	)
	val contentTint by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else NemuriTheme.colors.textMuted,
		animationSpec = nemuriEffectsSpring(),
		label = "dockPillTint",
	)
	val isAlwaysStacked = labelVisibility == NavLabelVisibility.ALWAYS
	val showInlineLabel = labelVisibility == NavLabelVisibility.SELECTED_ONLY && selected
	val pillShape = RoundedCornerShape(22.dp)

	// Unclipped outer Box so top-right corner badges NEVER sit between icon & label or get clipped
	Box(
		modifier = modifier.height(pillHeight),
		contentAlignment = Alignment.Center,
	) {
		Surface(
			color = containerColor,
			shape = pillShape,
			modifier = Modifier
				.let { if (isAlwaysStacked) it.fillMaxWidth() else it }
				.height(pillHeight)
				.border(1.dp, borderColor, pillShape)
				.clip(pillShape)
				.clickable(onClick = onClick),
		) {
			if (isAlwaysStacked) {
				Column(
					modifier = Modifier
						.fillMaxHeight()
						.padding(horizontal = 2.dp, vertical = 2.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = tab.icon(selected = selected),
						contentDescription = tab.label,
						tint = contentTint,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = tab.label,
						color = contentTint,
						fontSize = 10.sp,
						lineHeight = 11.sp,
						fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
						maxLines = 1,
						softWrap = false,
						overflow = TextOverflow.Ellipsis,
					)
				}
			} else {
				Row(
					modifier = Modifier.padding(
						horizontal = if (showInlineLabel) 12.dp else 11.dp,
						vertical = 4.dp,
					),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Icon(
						imageVector = tab.icon(selected = selected),
						contentDescription = tab.label,
						tint = contentTint,
						modifier = Modifier.size(20.dp),
					)
					AnimatedVisibility(
						visible = showInlineLabel,
						enter = fadeIn(nemuriEffectsSpring()) + expandHorizontally(nemuriFastSpatialSpring()),
						exit = fadeOut(nemuriEffectsSpring()) + shrinkHorizontally(nemuriFastSpatialSpring()),
					) {
						Text(
							text = tab.label,
							color = contentTint,
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
							maxLines = 1,
							softWrap = false,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}

		NavCornerBadge(
			badgeCount = badgeCount,
			showDot = showDot,
			borderRingColor = dockSurface,
			modifier = Modifier
				.align(Alignment.TopEnd)
				.offset(
					x = if (isAlwaysStacked) (-2).dp else 2.dp,
					y = (-2).dp,
				),
		)
	}
}

@Composable
private fun ClassicBarTabItem(
	tab: AppTab,
	selected: Boolean,
	badgeCount: Int,
	showDot: Boolean,
	labelVisibility: NavLabelVisibility,
	dockSurface: Color,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val showLabel = when (labelVisibility) {
		NavLabelVisibility.ALWAYS -> true
		NavLabelVisibility.SELECTED_ONLY -> selected
		NavLabelVisibility.HIDDEN -> false
	}
	val indicatorColor by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
		animationSpec = nemuriEffectsSpring(),
		label = "classicIndicatorColor",
	)
	val iconTint by animateColorAsState(
		targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else NemuriTheme.colors.textMuted,
		animationSpec = nemuriEffectsSpring(),
		label = "classicIconTint",
	)
	val labelTint by animateColorAsState(
		targetValue = if (selected) NemuriTheme.colors.cyan else NemuriTheme.colors.textMuted,
		animationSpec = nemuriEffectsSpring(),
		label = "classicLabelTint",
	)
	val pillWidth = if (showLabel) 54.dp else 48.dp
	val pillHeight = if (showLabel) 28.dp else 34.dp

	Column(
		modifier = modifier
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		// Unclipped outer Box around the tonal indicator pill for top-right corner badge
		Box(contentAlignment = Alignment.Center) {
			Box(
				modifier = Modifier
					.size(width = pillWidth, height = pillHeight)
					.clip(RoundedCornerShape(16.dp))
					.background(indicatorColor),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = tab.icon(selected = selected),
					contentDescription = tab.label,
					tint = iconTint,
					modifier = Modifier.size(20.dp),
				)
			}

			NavCornerBadge(
				badgeCount = badgeCount,
				showDot = showDot,
				borderRingColor = dockSurface,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.offset(x = 3.dp, y = (-3).dp),
			)
		}

		if (showLabel) {
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = tab.label,
				color = labelTint,
				fontSize = 11.sp,
				lineHeight = 13.sp,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
				maxLines = 1,
				softWrap = false,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun NavCornerBadge(
	badgeCount: Int,
	showDot: Boolean,
	borderRingColor: Color,
	modifier: Modifier = Modifier,
) {
	when {
		badgeCount > 0 -> {
			val label = if (badgeCount > 99) "99+" else badgeCount.toString()
			Box(
				modifier = modifier
					.defaultMinSize(minWidth = 17.dp, minHeight = 16.dp)
					.background(NemuriTheme.colors.cyan, RoundedCornerShape(999.dp))
					.border(1.5.dp, borderRingColor, RoundedCornerShape(999.dp))
					.padding(horizontal = 4.dp, vertical = 1.dp),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = label,
					color = NemuriTheme.colors.onCyan,
					fontSize = 9.sp,
					lineHeight = 10.sp,
					fontWeight = FontWeight.ExtraBold,
					maxLines = 1,
					softWrap = false,
				)
			}
		}

		showDot -> {
			Box(
				modifier = modifier
					.size(9.dp)
					.background(NemuriTheme.colors.cyan, CircleShape)
					.border(1.5.dp, borderRingColor, CircleShape),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueReadingFab(
	isIncognito: Boolean,
	fabSize: Dp,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val fill = if (isIncognito) NemuriTheme.colors.surfaceElevated else MaterialTheme.colorScheme.primary
	val iconTint = if (isIncognito) NemuriTheme.colors.cyan else MaterialTheme.colorScheme.onPrimary
	val borderColor = if (isIncognito) NemuriTheme.colors.cyan else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)

	Box(modifier = modifier) {
		Surface(
			color = fill,
			shape = RoundedCornerShape(28.dp),
			shadowElevation = 12.dp,
			modifier = Modifier
				.size(fabSize)
				.border(if (isIncognito) 2.dp else 1.dp, borderColor, RoundedCornerShape(28.dp))
				.clip(RoundedCornerShape(28.dp))
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
					modifier = Modifier.size(22.dp),
				)
			}
		}
		if (isIncognito) {
			Surface(
				color = NemuriTheme.colors.cyan,
				shape = CircleShape,
				modifier = Modifier
					.size(18.dp)
					.align(Alignment.TopEnd)
					.border(1.5.dp, NemuriTheme.colors.dock, CircleShape),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Default.VisibilityOff,
						contentDescription = "Incognito",
						tint = NemuriTheme.colors.onCyan,
						modifier = Modifier.size(11.dp),
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
		color = NemuriTheme.colors.sheet.copy(alpha = 0.98f),
		shape = RoundedCornerShape(22.dp),
		shadowElevation = 18.dp,
		modifier = modifier
			.widthIn(min = 276.dp, max = 328.dp)
			.border(1.dp, NemuriTheme.colors.cyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
			.yorhaTacticalBrackets(),
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
	val railSurface = NemuriTheme.colors.surface
	NavigationRail(
		containerColor = railSurface,
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
			val selected = tab == currentTab
			NavigationRailItem(
				selected = selected,
				onClick = { onSelectTab(tab) },
				icon = {
					Box(contentAlignment = Alignment.Center) {
						Icon(tab.icon(selected = selected), contentDescription = tab.label)
						NavCornerBadge(
							badgeCount = if (tab == AppTab.FEED) unreadFeedCount else 0,
							showDot = tab == AppTab.EXPLORE && hasExtensionUpdates,
							borderRingColor = railSurface,
							modifier = Modifier
								.align(Alignment.TopEnd)
								.offset(x = 8.dp, y = (-5).dp),
						)
					}
				},
				label = { Text(tab.label, maxLines = 1) },
				colors = NavigationRailItemDefaults.colors(
					selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
					selectedTextColor = NemuriTheme.colors.cyan,
					indicatorColor = MaterialTheme.colorScheme.primaryContainer,
					unselectedIconColor = NemuriTheme.colors.textMuted,
					unselectedTextColor = NemuriTheme.colors.textMuted,
				),
			)
		}
	}
}
