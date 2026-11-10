package org.koitharu.kotatsu.core.parser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.Locale

data class DiscoveredKotatsuProvider(
	val pkgName: String,
	val authority: String,
	val label: String,
	val versionName: String,
	val versionCode: Long,
	val lang: String,
) {
	val sourceKey: String
		get() = "KOTATSU_${authority}"

	fun toMangaSource(): MangaSource = MangaSource(
		name = sourceKey,
		title = label,
	)
}

object KotatsuExternalProviderScanner {
	const val ACTION_PROVIDE_MANGA = "app.kotatsu.parser.PROVIDE_MANGA"

	fun discoverProviders(context: Context): List<DiscoveredKotatsuProvider> {
		val pm = context.packageManager
		val intent = Intent(ACTION_PROVIDE_MANGA)
		val resolveInfos = runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pm.queryIntentContentProviders(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
			} else {
				@Suppress("DEPRECATION")
				pm.queryIntentContentProviders(intent, PackageManager.GET_META_DATA)
			}
		}.getOrDefault(emptyList())

		val results = mutableListOf<DiscoveredKotatsuProvider>()
		for (ri in resolveInfos) {
			val providerInfo = ri.providerInfo ?: continue
			val pkgName = providerInfo.packageName ?: continue
			if (pkgName == context.packageName) continue
			val authorities = providerInfo.authority?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
			if (authorities.isEmpty()) continue

			val pkgInfo = runCatching {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
				} else {
					@Suppress("DEPRECATION")
					pm.getPackageInfo(pkgName, 0)
				}
			}.getOrNull()

			val versionName = pkgInfo?.versionName ?: "1.0"
			val versionCode = if (pkgInfo != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else {
					@Suppress("DEPRECATION")
					pkgInfo.versionCode.toLong()
				}
			} else {
				1L
			}
			val baseLabel = runCatching { providerInfo.loadLabel(pm).toString() }
				.getOrNull()
				?.takeIf { it.isNotBlank() }
				?: runCatching { providerInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() } }.getOrNull()
				?: pkgName.substringAfterLast('.')
			val lang = providerInfo.metaData?.getString("lang")
				?: providerInfo.metaData?.getString("locale")
				?: "all"

			for (auth in authorities) {
				results += DiscoveredKotatsuProvider(
					pkgName = pkgName,
					authority = auth,
					label = baseLabel.removePrefix("Kotatsu: ").trim(),
					versionName = versionName,
					versionCode = versionCode,
					lang = lang,
				)
			}
		}
		return results.distinctBy { it.authority }
	}
}

class KotatsuContentProviderRepository(
	private val context: Context,
	private val provider: DiscoveredKotatsuProvider,
) : CachingMangaRepository() {

	override val source: MangaSource = provider.toMangaSource()

	override val sortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.ALPHABETICAL)

	override suspend fun getList(offset: Int, query: String?, sortOrder: SortOrder?): List<Manga> =
		withContext(Dispatchers.IO) {
			val uri = Uri.Builder()
				.scheme("content")
				.authority(provider.authority)
				.appendPath("list")
				.appendQueryParameter("offset", offset.toString())
				.apply {
					if (!query.isNullOrBlank()) appendQueryParameter("query", query.trim())
					if (sortOrder != null) appendQueryParameter("order", sortOrder.name)
				}
				.build()

			val items = mutableListOf<Manga>()
			runCatching {
				context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
					while (cursor.moveToNext()) {
						cursor.toMangaOrNull(source)?.let { items += it }
					}
				}
			}
			items
		}

	override suspend fun fetchDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
		val detailsUri = Uri.Builder()
			.scheme("content")
			.authority(provider.authority)
			.appendPath("details")
			.appendQueryParameter("url", manga.url)
			.build()

		var updated = manga
		runCatching {
			context.contentResolver.query(detailsUri, null, null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					cursor.toMangaOrNull(source)?.let { parsed ->
						updated = manga.copy(
							title = parsed.title.ifBlank { manga.title },
							altTitle = parsed.altTitle ?: manga.altTitle,
							publicUrl = parsed.publicUrl.ifBlank { manga.publicUrl },
							rating = if (parsed.rating >= 0f) parsed.rating else manga.rating,
							isNsfw = parsed.isNsfw || manga.isNsfw,
							coverUrl = parsed.coverUrl ?: manga.coverUrl,
							largeCoverUrl = parsed.largeCoverUrl ?: manga.largeCoverUrl,
							description = parsed.description ?: manga.description,
							tags = if (parsed.tags.isNotEmpty()) parsed.tags else manga.tags,
							state = parsed.state ?: manga.state,
							author = parsed.author ?: manga.author,
						)
					}
				}
			}
		}

		val chaptersUri = Uri.Builder()
			.scheme("content")
			.authority(provider.authority)
			.appendPath("chapters")
			.appendQueryParameter("url", manga.url)
			.build()

		val chapters = mutableListOf<MangaChapter>()
		runCatching {
			context.contentResolver.query(chaptersUri, null, null, null, null)?.use { cursor ->
				var idx = 0
				while (cursor.moveToNext()) {
					cursor.toChapterOrNull(source, idx)?.let {
						chapters += it
						idx++
					}
				}
			}
		}

		updated.copy(chapters = if (chapters.isNotEmpty()) chapters else manga.chapters)
	}

	override suspend fun fetchPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val pagesUri = Uri.Builder()
			.scheme("content")
			.authority(provider.authority)
			.appendPath("pages")
			.appendQueryParameter("url", chapter.url)
			.build()

		val pages = mutableListOf<MangaPage>()
		runCatching {
			context.contentResolver.query(pagesUri, null, null, null, null)?.use { cursor ->
				var idx = 0
				while (cursor.moveToNext()) {
					val url = cursor.optString("url").ifBlank { cursor.optString("page_url") }
					if (url.isNotBlank()) {
						val id = cursor.optLong("id", ((source.name.hashCode().toLong() shl 31) xor "${idx}_$url".hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL)
						pages += MangaPage(
							id = id,
							url = url,
							preview = cursor.optString("preview").takeIf { it.isNotBlank() },
							source = source,
						)
						idx++
					}
				}
			}
		}
		pages
	}

	override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
		if (page.url.startsWith("http://") || page.url.startsWith("https://")) {
			return@withContext page.url
		}
		val resolveUri = Uri.Builder()
			.scheme("content")
			.authority(provider.authority)
			.appendPath("page_url")
			.appendQueryParameter("url", page.url)
			.build()

		runCatching {
			context.contentResolver.query(resolveUri, null, null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					cursor.optString("url").takeIf { it.isNotBlank() }
				} else {
					null
				}
			}
		}.getOrNull() ?: page.url
	}

	private fun Cursor.optString(colName: String): String {
		val idx = getColumnIndex(colName)
		if (idx < 0 || isNull(idx)) return ""
		return runCatching { getString(idx).orEmpty() }.getOrDefault("")
	}

	private fun Cursor.optLong(colName: String, fallback: Long = 0L): Long {
		val idx = getColumnIndex(colName)
		if (idx < 0 || isNull(idx)) return fallback
		return runCatching { getLong(idx) }.getOrDefault(fallback)
	}

	private fun Cursor.optFloat(colName: String, fallback: Float = -1f): Float {
		val idx = getColumnIndex(colName)
		if (idx < 0 || isNull(idx)) return fallback
		return runCatching { getFloat(idx) }.getOrDefault(fallback)
	}

	private fun Cursor.toMangaOrNull(src: MangaSource): Manga? {
		val url = optString("url")
		val title = optString("title")
		if (url.isBlank() || title.isBlank()) return null
		val id = optLong(
			"id",
			((src.name.hashCode().toLong() shl 31) xor url.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL,
		)
		val tagsRaw = optString("tags")
		val tags = tagsRaw.split(',', ';')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { t -> MangaTag(title = t, key = t.lowercase(Locale.ROOT), source = src) }
			.toSet()
		val stateStr = optString("state").uppercase(Locale.ROOT)
		val state = runCatching { MangaState.valueOf(stateStr) }.getOrNull()
		return Manga(
			id = id,
			title = title,
			altTitle = optString("alt_title").takeIf { it.isNotBlank() },
			url = url,
			publicUrl = optString("public_url").ifBlank { url },
			rating = optFloat("rating", -1f),
			isNsfw = optLong("nsfw", 0L) == 1L,
			coverUrl = optString("cover_url").takeIf { it.isNotBlank() },
			largeCoverUrl = optString("large_cover_url").takeIf { it.isNotBlank() },
			description = optString("description").takeIf { it.isNotBlank() },
			tags = tags,
			state = state,
			author = optString("author").takeIf { it.isNotBlank() },
			source = src,
		)
	}

	private fun Cursor.toChapterOrNull(src: MangaSource, index: Int): MangaChapter? {
		val url = optString("url")
		if (url.isBlank()) return null
		val name = optString("name").ifBlank { optString("title") }.ifBlank { "Chapter ${index + 1}" }
		val id = optLong(
			"chapter_id",
			optLong("id", ((src.name.hashCode().toLong() shl 31) xor url.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL),
		)
		return MangaChapter(
			id = id,
			name = name,
			number = optFloat("number", (index + 1).toFloat()),
			volume = optLong("volume", 0L).toInt(),
			url = url,
			scanlator = optString("scanlator").takeIf { it.isNotBlank() },
			uploadDate = optLong("upload_date", 0L),
			branch = optString("branch").takeIf { it.isNotBlank() },
			source = src,
		)
	}
}
