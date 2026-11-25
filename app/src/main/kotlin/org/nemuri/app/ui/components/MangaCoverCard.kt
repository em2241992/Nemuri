package org.nemuri.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import org.koitharu.kotatsu.core.util.ext.mangaExtra
import org.koitharu.kotatsu.core.util.ext.stableMangaCoverKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.nemuri.app.ui.theme.NemuriTheme

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
	aspectRatio: Float = 0.67f,
	cornerRadiusDp: Int = 14,
	modifier: Modifier = Modifier,
) {
	val shape = RoundedCornerShape(cornerRadiusDp.dp)
	val borderColor = if (isSelected) {
		NemuriTheme.colors.cyan
	} else {
		Color.White.copy(alpha = 0.1f)
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.aspectRatio(aspectRatio)
			.clip(shape)
			.border(if (isSelected) 2.5.dp else 1.dp, borderColor, shape)
			.clickable(onClick = onClick),
	) {
		MangaCoverImage(
			manga = manga,
			coverUrl = manga.coverUrl,
			modifier = Modifier.fillMaxSize(),
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
				.padding(start = 11.dp, end = 11.dp, top = 22.dp, bottom = if (progress > 0f) 12.dp else 10.dp),
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
				.padding(6.dp),
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
				unreadCount > 0 -> Surface(
					color = MaterialTheme.colorScheme.primary,
					shape = RoundedCornerShape(6.dp),
				) {
					NonWrappingBadgeText(
						text = unreadCount.toString(),
						color = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
					)
				}
				isCaughtUp -> Surface(
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
				.padding(6.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (isDownloaded) {
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
			if (!sourceBadge.isNullOrBlank()) {
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

		if (progress > 0f) {
			LinearProgressIndicator(
				progress = { progress.coerceIn(0f, 1f) },
				color = MaterialTheme.colorScheme.primary,
				trackColor = Color.Black.copy(alpha = 0.4f),
				modifier = Modifier
					.fillMaxWidth()
					.height(3.dp)
					.align(Alignment.BottomCenter),
			)
		}
	}
}

@Composable
fun MangaListRow(
	manga: Manga,
	subtitle: String,
	onClick: () -> Unit,
	unreadCount: Int = 0,
	progress: Float = 0f,
	onQuickActionClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	Surface(
		color = NemuriTheme.colors.card,
		shape = RoundedCornerShape(14.dp),
		modifier = modifier
			.fillMaxWidth()
			.border(1.dp, NemuriTheme.colors.border, RoundedCornerShape(14.dp))
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick),
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
					.clip(RoundedCornerShape(10.dp)),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = manga.title,
					style = MaterialTheme.typography.titleMedium,
					color = NemuriTheme.colors.textPrimary,
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
				if (progress > 0f) {
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
			if (unreadCount > 0) {
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
