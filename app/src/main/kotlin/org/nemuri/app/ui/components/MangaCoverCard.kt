package org.nemuri.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import org.koitharu.kotatsu.core.util.ext.mangaExtra
import org.koitharu.kotatsu.core.util.ext.stableMangaCoverKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.ui.theme.NemuriTheme
import org.nemuri.app.ui.theme.nemuriFastSpatialSpring
import org.nemuri.app.ui.theme.nemuriSharedCover
import kotlin.math.max

fun cornerSafeHorizontalPadding(cornerRadiusDp: Int): Dp = max(10f, cornerRadiusDp * 0.60f).dp

fun cornerSafeBottomPadding(cornerRadiusDp: Int): Dp = max(8f, cornerRadiusDp * 0.45f).dp

fun cornerSafeBadgeInset(cornerRadiusDp: Int): Dp = max(6f, cornerRadiusDp * 0.42f).dp

/**
 * Corner-safe inset reading progress capsule (`height = 3.dp`, `CircleShape` ends,
 * `bottomPadding = 5.dp`, `horizontalPadding = max(8.dp, cardCornerRadius * 0.50f)`)
 * over a subtle dark backing track so even 2% or 100% progress stays 100% visible
 * inside any card corner radius (`0.dp`–`24.dp`).
 */
@Composable
fun CornerSafeCoverProgressBar(
	progress: Float,
	cornerRadiusDp: Int,
	modifier: Modifier = Modifier,
	fillColor: Color = MaterialTheme.colorScheme.primary,
	trackColor: Color = Color.Black.copy(alpha = 0.52f),
) {
	val clamped = progress.coerceIn(0f, 1f)
	if (clamped <= 0f) return
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val horizontalPad = max(8f, safeCorner * 0.50f).dp
	Box(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = horizontalPad, end = horizontalPad, bottom = 5.dp)
			.height(3.dp)
			.clip(CircleShape)
			.background(trackColor, CircleShape),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(clamped.coerceAtLeast(0.04f))
				.fillMaxHeight()
				.clip(CircleShape)
				.background(fillColor, CircleShape),
		)
	}
}

@Composable
fun NonWrappingBadgeText(
	text: String,
	color: Color,
	modifier: Modifier = Modifier,
	fontSizeSp: Float = 10f,
) {
	Text(
		text = text,
		color = color,
		style = MaterialTheme.typography.labelSmall,
		fontSize = fontSizeSp.sp,
		fontWeight = FontWeight.Bold,
		maxLines = 1,
		softWrap = false,
		overflow = TextOverflow.Ellipsis,
		modifier = modifier,
	)
}

@Composable
fun MangaCoverImage(
	manga: Manga?,
	coverUrl: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Crop,
) {
	val context = LocalContext.current
	val monogram = manga?.title
		?.trim()
		?.split(' ')
		?.filter { it.isNotEmpty() }
		?.take(2)
		?.joinToString("") { it.first().uppercase() }
		?.ifEmpty { "?" }
		?: "?"

	Box(
		modifier = modifier.background(
			Brush.verticalGradient(
				colors = listOf(Color(0xFF141824), Color(0xFF0B0E14)),
			),
		),
		contentAlignment = Alignment.Center,
	) {
		if (coverUrl.isNullOrBlank()) {
			Text(
				text = monogram,
				style = MaterialTheme.typography.titleLarge,
				color = NemuriTheme.colors.textDim,
			)
		} else {
			SubcomposeAsyncImage(
				model = ImageRequest.Builder(context)
					.data(coverUrl)
					.size(Size(420, 600))
					.precision(Precision.INEXACT)
					.mangaExtra(manga)
					.stableMangaCoverKey(manga, coverUrl)
					.build(),
				contentDescription = manga?.title,
				contentScale = contentScale,
				loading = {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = monogram,
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.textDim,
						)
					}
				},
				error = {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = monogram,
							style = MaterialTheme.typography.titleMedium,
							color = NemuriTheme.colors.textDim,
						)
					}
				},
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCompactGridCard(
	manga: Manga,
	onClick: () -> Unit,
	unreadCount: Int = 0,
	isCaughtUp: Boolean = false,
	isDownloaded: Boolean = false,
	sourceBadge: String? = null,
	progress: Float = 0f,
	isSelected: Boolean = false,
	inLibraryBadge: Boolean = false,
	onLongClick: (() -> Unit)? = null,
	aspectRatio: Float = LocalLayoutStudioPrefs.current.coverAspectRatio.ratio,
	cornerRadiusDp: Int = LocalLayoutStudioPrefs.current.cardCornerRadiusDp,
	showUnreadBadge: Boolean = LocalLayoutStudioPrefs.current.showUnreadBadge,
	showDownloadedBadge: Boolean = LocalLayoutStudioPrefs.current.showDownloadedBadge,
	showSourceBadge: Boolean = LocalLayoutStudioPrefs.current.showSourceBadge,
	showProgressBar: Boolean = LocalLayoutStudioPrefs.current.showBottomProgressBar,
	sharedCoverKey: String? = null,
	modifier: Modifier = Modifier,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val shape = RoundedCornerShape(safeCorner.dp)
	val borderColor by animateColorAsState(
		targetValue = if (isSelected) NemuriTheme.colors.cyan else Color.White.copy(alpha = 0.1f),
		label = "compactCardBorder",
	)
	val scale by animateFloatAsState(
		targetValue = if (isSelected) 0.96f else 1f,
		animationSpec = nemuriFastSpatialSpring(),
		label = "compactCardScale",
	)
	val hInset = cornerSafeHorizontalPadding(safeCorner)
	val bInset = cornerSafeBottomPadding(safeCorner)
	val badgeInset = cornerSafeBadgeInset(safeCorner)
	val effectiveProgress = if (showProgressBar) progress.coerceIn(0f, 1f) else 0f

	Box(
		modifier = modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.fillMaxWidth()
			.aspectRatio(aspectRatio)
			.clip(shape)
			.border(if (isSelected) 2.5.dp else 1.dp, borderColor, shape)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		MangaCoverImage(
			manga = manga,
			coverUrl = manga.coverUrl,
			modifier = Modifier
				.fillMaxSize()
				.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
		)

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.align(Alignment.BottomCenter)
				.background(
					Brush.verticalGradient(
						colors = listOf(
							Color.Transparent,
							Color.Black.copy(alpha = 0.72f),
							Color.Black.copy(alpha = 0.92f),
						),
					),
				)
				.padding(
					start = hInset,
					end = hInset,
					top = 22.dp,
					bottom = bInset + if (effectiveProgress > 0f) 6.dp else 0.dp,
				),
		) {
			Text(
				text = manga.title,
				style = MaterialTheme.typography.titleSmall,
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
				color = Color.White,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}

		// Top-left badge cluster
		Row(
			modifier = Modifier
				.align(Alignment.TopStart)
				.padding(badgeInset),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			when {
				inLibraryBadge -> Surface(
					color = NemuriTheme.colors.cyan,
					shape = RoundedCornerShape(6.dp),
				) {
					NonWrappingBadgeText(
						text = "In Library",
						color = NemuriTheme.colors.onCyan,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
				showUnreadBadge && unreadCount > 0 -> Surface(
					color = MaterialTheme.colorScheme.primary,
					shape = RoundedCornerShape(6.dp),
				) {
					NonWrappingBadgeText(
						text = unreadCount.toString(),
						color = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
				showUnreadBadge && isCaughtUp -> Surface(
					color = NemuriTheme.colors.healthy.copy(alpha = 0.92f),
					shape = CircleShape,
					modifier = Modifier.size(20.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.Check,
							contentDescription = "Caught up",
							tint = Color.White,
							modifier = Modifier.size(13.dp),
						)
					}
				}
			}
		}

		// Top-right badge cluster
		Row(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(badgeInset),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (isSelected) {
				Surface(
					color = NemuriTheme.colors.cyan,
					shape = CircleShape,
					modifier = Modifier.size(22.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.CheckCircle,
							contentDescription = "Selected",
							tint = NemuriTheme.colors.onCyan,
							modifier = Modifier.size(16.dp),
						)
					}
				}
			}
			if (showDownloadedBadge && isDownloaded) {
				Surface(
					color = Color.Black.copy(alpha = 0.68f),
					shape = CircleShape,
					modifier = Modifier.size(20.dp),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.CloudDone,
							contentDescription = "Downloaded",
							tint = NemuriTheme.colors.healthy,
							modifier = Modifier.size(12.dp),
						)
					}
				}
			}
			if (showSourceBadge && !sourceBadge.isNullOrBlank()) {
				Surface(
					color = Color.Black.copy(alpha = 0.7f),
					shape = RoundedCornerShape(6.dp),
				) {
					NonWrappingBadgeText(
						text = sourceBadge,
						color = NemuriTheme.colors.cyan,
						fontSizeSp = 9f,
						modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
					)
				}
			}
		}

		if (effectiveProgress > 0f) {
			CornerSafeCoverProgressBar(
				progress = effectiveProgress,
				cornerRadiusDp = safeCorner,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}
	}
}

/**
 * Comfortable grid card: clips rounded corners strictly to the cover image Box so
 * curved borders never shave or clip the 2-line title below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaComfortableGridCard(
	manga: Manga,
	onClick: () -> Unit,
	unreadCount: Int = 0,
	isCaughtUp: Boolean = false,
	isDownloaded: Boolean = false,
	sourceBadge: String? = null,
	progress: Float = 0f,
	isSelected: Boolean = false,
	inLibraryBadge: Boolean = false,
	onLongClick: (() -> Unit)? = null,
	aspectRatio: Float = LocalLayoutStudioPrefs.current.coverAspectRatio.ratio,
	cornerRadiusDp: Int = LocalLayoutStudioPrefs.current.cardCornerRadiusDp,
	showUnreadBadge: Boolean = LocalLayoutStudioPrefs.current.showUnreadBadge,
	showDownloadedBadge: Boolean = LocalLayoutStudioPrefs.current.showDownloadedBadge,
	showSourceBadge: Boolean = LocalLayoutStudioPrefs.current.showSourceBadge,
	showProgressBar: Boolean = LocalLayoutStudioPrefs.current.showBottomProgressBar,
	sharedCoverKey: String? = null,
	modifier: Modifier = Modifier,
) {
	val safeCorner = cornerRadiusDp.coerceIn(0, 24)
	val coverShape = RoundedCornerShape(safeCorner.dp)
	val borderColor by animateColorAsState(
		targetValue = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
		label = "comfortableCardBorder",
	)
	val scale by animateFloatAsState(
		targetValue = if (isSelected) 0.96f else 1f,
		animationSpec = nemuriFastSpatialSpring(),
		label = "comfortableCardScale",
	)
	val badgeInset = cornerSafeBadgeInset(safeCorner)
	val effectiveProgress = if (showProgressBar) progress.coerceIn(0f, 1f) else 0f

	Column(
		modifier = modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.fillMaxWidth()
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(aspectRatio)
				.clip(coverShape)
				.border(if (isSelected) 2.5.dp else 1.dp, borderColor, coverShape),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.fillMaxSize()
					.nemuriSharedCover(sharedCoverKey, safeCorner.dp),
			)

			Row(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(badgeInset),
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				when {
					inLibraryBadge -> Surface(
						color = NemuriTheme.colors.cyan,
						shape = RoundedCornerShape(6.dp),
					) {
						NonWrappingBadgeText(
							text = "In Library",
							color = NemuriTheme.colors.onCyan,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
					showUnreadBadge && unreadCount > 0 -> Surface(
						color = MaterialTheme.colorScheme.primary,
						shape = RoundedCornerShape(6.dp),
					) {
						NonWrappingBadgeText(
							text = unreadCount.toString(),
							color = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
						)
					}
					showUnreadBadge && isCaughtUp -> Surface(
						color = NemuriTheme.colors.healthy.copy(alpha = 0.92f),
						shape = CircleShape,
						modifier = Modifier.size(20.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.Check,
								contentDescription = "Caught up",
								tint = Color.White,
								modifier = Modifier.size(13.dp),
							)
						}
					}
				}
			}

			Row(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(badgeInset),
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (isSelected) {
					Surface(
						color = NemuriTheme.colors.cyan,
						shape = CircleShape,
						modifier = Modifier.size(22.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.CheckCircle,
								contentDescription = "Selected",
								tint = NemuriTheme.colors.onCyan,
								modifier = Modifier.size(16.dp),
							)
						}
					}
				}
				if (showDownloadedBadge && isDownloaded) {
					Surface(
						color = Color.Black.copy(alpha = 0.68f),
						shape = CircleShape,
						modifier = Modifier.size(20.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Default.CloudDone,
								contentDescription = "Downloaded",
								tint = NemuriTheme.colors.healthy,
								modifier = Modifier.size(12.dp),
							)
						}
					}
				}
				if (showSourceBadge && !sourceBadge.isNullOrBlank()) {
					Surface(
						color = Color.Black.copy(alpha = 0.7f),
						shape = RoundedCornerShape(6.dp),
					) {
						NonWrappingBadgeText(
							text = sourceBadge,
							color = NemuriTheme.colors.cyan,
							fontSizeSp = 9f,
							modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
						)
					}
				}
			}

			if (effectiveProgress > 0f) {
				CornerSafeCoverProgressBar(
					progress = effectiveProgress,
					cornerRadiusDp = safeCorner,
					modifier = Modifier.align(Alignment.BottomCenter),
				)
			}
		}

		Text(
			text = manga.title,
			style = MaterialTheme.typography.titleSmall,
			fontSize = 12.sp,
			lineHeight = 16.sp,
			fontWeight = FontWeight.SemiBold,
			color = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
			minLines = 2,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 2.dp),
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaListRow(
	manga: Manga,
	subtitle: String,
	onClick: () -> Unit,
	unreadCount: Int = 0,
	progress: Float = 0f,
	onQuickActionClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	isSelected: Boolean = false,
	isDownloaded: Boolean = false,
	tagsSummary: String? = null,
	cornerRadiusDp: Int = LocalLayoutStudioPrefs.current.cardCornerRadiusDp,
	showUnreadBadge: Boolean = LocalLayoutStudioPrefs.current.showUnreadBadge,
	showDownloadedBadge: Boolean = LocalLayoutStudioPrefs.current.showDownloadedBadge,
	showProgressBar: Boolean = LocalLayoutStudioPrefs.current.showBottomProgressBar,
	sharedCoverKey: String? = null,
	modifier: Modifier = Modifier,
) {
	val safeRowCorner = cornerRadiusDp.coerceIn(8, 18)
	val thumbCorner = (safeRowCorner - 2).coerceIn(4, 14)
	Surface(
		color = if (isSelected) NemuriTheme.colors.tealContainer else NemuriTheme.colors.card,
		shape = RoundedCornerShape(safeRowCorner.dp),
		modifier = modifier
			.fillMaxWidth()
			.border(
				width = if (isSelected) 2.dp else 1.dp,
				color = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.border,
				shape = RoundedCornerShape(safeRowCorner.dp),
			)
			.clip(RoundedCornerShape(safeRowCorner.dp))
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			),
	) {
		Row(
			modifier = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			MangaCoverImage(
				manga = manga,
				coverUrl = manga.coverUrl,
				modifier = Modifier
					.size(width = 52.dp, height = 74.dp)
					.clip(RoundedCornerShape(thumbCorner.dp))
					.nemuriSharedCover(sharedCoverKey, thumbCorner.dp),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = manga.title,
					style = MaterialTheme.typography.titleMedium,
					color = if (isSelected) NemuriTheme.colors.cyan else NemuriTheme.colors.textPrimary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = subtitle,
					style = MaterialTheme.typography.labelSmall,
					color = NemuriTheme.colors.textMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (!tagsSummary.isNullOrBlank()) {
					Text(
						text = tagsSummary,
						style = MaterialTheme.typography.labelSmall,
						color = NemuriTheme.colors.textDim,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (showProgressBar && progress > 0f) {
					Spacer(modifier = Modifier.height(2.dp))
					LinearProgressIndicator(
						progress = { progress.coerceIn(0f, 1f) },
						color = NemuriTheme.colors.cyan,
						trackColor = NemuriTheme.colors.border,
						modifier = Modifier
							.fillMaxWidth()
							.height(3.dp)
							.clip(RoundedCornerShape(2.dp)),
					)
				}
			}
			if (isSelected) {
				Icon(
					imageVector = Icons.Default.CheckCircle,
					contentDescription = "Selected",
					tint = NemuriTheme.colors.cyan,
					modifier = Modifier.size(20.dp),
				)
			}
			if (showDownloadedBadge && isDownloaded) {
				Icon(
					imageVector = Icons.Default.CloudDone,
					contentDescription = "Downloaded",
					tint = NemuriTheme.colors.healthy,
					modifier = Modifier.size(16.dp),
				)
			}
			if (showUnreadBadge && unreadCount > 0) {
				Surface(
					color = MaterialTheme.colorScheme.primary,
					shape = RoundedCornerShape(8.dp),
				) {
					NonWrappingBadgeText(
						text = unreadCount.toString(),
						color = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
					)
				}
			}
			if (onQuickActionClick != null) {
				Surface(
					color = NemuriTheme.colors.tealContainer,
					shape = RoundedCornerShape(10.dp),
					modifier = Modifier
						.size(36.dp)
						.clip(RoundedCornerShape(10.dp))
						.clickable(onClick = onQuickActionClick),
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(
							imageVector = Icons.Default.PlayArrow,
							contentDescription = "Open",
							tint = NemuriTheme.colors.cyan,
							modifier = Modifier.size(18.dp),
						)
					}
				}
			}
		}
	}
}
