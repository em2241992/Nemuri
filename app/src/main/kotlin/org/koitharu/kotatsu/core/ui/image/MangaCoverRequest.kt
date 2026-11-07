package org.koitharu.kotatsu.core.ui.image

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Size
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.parsers.model.Manga

object MangaCoverRequest {
	fun build(context: Context, manga: Manga): ImageRequest {
		val stableKey = "nemuri_cover_${manga.id}"
		return ImageRequest.Builder(context)
			.data(manga.coverUrl)
			.memoryCacheKey(stableKey)
			.diskCacheKey(manga.coverUrl ?: stableKey)
			.placeholderMemoryCacheKey(stableKey) // Prevents single-frame blink when Room Flow re-emits
			.size(Size(450, 675))                 // Pre-downsample for 120Hz grid flings
			.allowHardware(true)
			.crossfade(false)
			.build()
	}

	fun build(context: Context, manga: MangaEntity): ImageRequest {
		val stableKey = "nemuri_cover_${manga.id}"
		return ImageRequest.Builder(context)
			.data(manga.coverUrl)
			.memoryCacheKey(stableKey)
			.diskCacheKey(manga.coverUrl.ifBlank { stableKey })
			.placeholderMemoryCacheKey(stableKey)
			.size(Size(450, 675))
			.allowHardware(true)
			.crossfade(false)
			.build()
	}
}

fun buildStableCoverRequest(context: Context, manga: Manga): ImageRequest =
	MangaCoverRequest.build(context, manga)

fun buildStableCoverRequest(context: Context, manga: MangaEntity): ImageRequest =
	MangaCoverRequest.build(context, manga)
