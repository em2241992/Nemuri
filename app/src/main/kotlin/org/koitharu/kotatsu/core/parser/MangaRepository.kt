package org.koitharu.kotatsu.core.parser

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.lnreader.LnMangaRepository
import org.koitharu.kotatsu.lnreader.LnPluginManager
import org.koitharu.kotatsu.mihon.LazyMihonMangaRepository
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.mihon.MihonMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

interface MangaRepository {

	val source: MangaSource

	val sortOrders: Set<SortOrder>

	suspend fun getList(
		offset: Int,
		query: String? = null,
		sortOrder: SortOrder? = null,
	): List<Manga>

	suspend fun getDetails(manga: Manga): Manga

	suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	suspend fun getPageUrl(page: MangaPage): String

	@Singleton
	class Factory @Inject constructor(
		private val appContext: Context? = null,
	) {
		private val cache = ConcurrentHashMap<String, WeakReference<MangaRepository>>()

		fun create(source: MangaSource): MangaRepository {
			cache[source.name]?.get()?.let { return it }
			val created = createInstance(source)
			if (created !is EmptyMangaRepository) {
				cache[source.name] = WeakReference(created)
			}
			return created
		}

		suspend fun createAsync(source: MangaSource): MangaRepository {
			cache[source.name]?.get()?.let { return it }
			val ctx = appContext ?: GlobalParserContextHolder.applicationContext
			if (ctx != null) {
				val mihonMgr = MihonExtensionManager.getInstance(ctx)
				if (mihonMgr.mihonSources.value.isEmpty() &&
					(source.name.startsWith("MIHON_", ignoreCase = true) || source.name.startsWith("EXT:", ignoreCase = true))
				) {
					mihonMgr.refreshExtensions()
				}
			}
			val created = createInstance(source)
			if (created !is EmptyMangaRepository) {
				cache[source.name] = WeakReference(created)
			}
			return created
		}

		fun invalidateCache() {
			cache.clear()
		}

		private fun createInstance(source: MangaSource): MangaRepository {
			val rawName = source.name.trim()
			val cleanTitle = source.title.trim()
			val ctx = appContext ?: GlobalParserContextHolder.applicationContext

			// 1. Local CBZ / ZIP / Directory Storage
			if (rawName.equals("LOCAL", ignoreCase = true) ||
				rawName.startsWith("LOCAL:", ignoreCase = true) ||
				rawName.startsWith("cbz://", ignoreCase = true) ||
				rawName.startsWith("file://", ignoreCase = true)
			) {
				return LocalMangaRepository(ctx)
			}

			if (ctx != null) {
				// 2. LNReader JavaScript Novel Plugins
				val lnManager = LnPluginManager.getInstance(ctx)
				val lnPlugin = lnManager.findPlugin(rawName) ?: lnManager.findPlugin(cleanTitle)
				if (lnPlugin != null) {
					return LnMangaRepository(lnPlugin, lnManager)
				}

				// 3. Mihon / Tachiyomi Installed Extensions
				val mihonManager = MihonExtensionManager.getInstance(ctx)
				val mihonSource = mihonManager.findSourceByNameOrId(rawName)
					?: mihonManager.findSourceByNameOrId(cleanTitle)
				if (mihonSource != null) {
					return MihonMangaRepository(mihonSource)
				}
				if (rawName.startsWith("MIHON_", ignoreCase = true)) {
					return LazyMihonMangaRepository(source, mihonManager)
				}

				// 4. Kotatsu External ContentProvider Extensions (app.kotatsu.parser.PROVIDE_MANGA)
				val kotatsuProviders = KotatsuExternalProviderScanner.discoverProviders(ctx)
				val matchedProvider = kotatsuProviders.firstOrNull { p ->
					p.sourceKey.equals(rawName, ignoreCase = true) ||
						p.authority.equals(rawName.removePrefix("KOTATSU_"), ignoreCase = true) ||
						p.pkgName.equals(rawName, ignoreCase = true) ||
						p.label.equals(cleanTitle, ignoreCase = true)
				}
				if (matchedProvider != null) {
					return KotatsuContentProviderRepository(ctx, matchedProvider)
				}
			}

			// 5. Strict Empty Fallback (zero fake or MangaDex cross-source data)
			return EmptyMangaRepository(source)
		}
	}
}

object GlobalParserContextHolder {
	@Volatile
	var applicationContext: Context? = null
}

abstract class CachingMangaRepository : MangaRepository {
	private val detailsCache = LruCache<Long, Manga>(48)
	private val pagesCache = LruCache<Long, List<MangaPage>>(32)
	private val mutex = Mutex()

	final override suspend fun getDetails(manga: Manga): Manga {
		detailsCache.get(manga.id)?.let { return it }
		return mutex.withLock {
			detailsCache.get(manga.id)?.let { return@withLock it }
			val fetched = fetchDetailsImpl(manga)
			detailsCache.put(manga.id, fetched)
			fetched
		}
	}

	final override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		pagesCache.get(chapter.id)?.let { return it }
		return mutex.withLock {
			pagesCache.get(chapter.id)?.let { return@withLock it }
			val fetched = fetchPagesImpl(chapter)
			if (fetched.isNotEmpty()) {
				pagesCache.put(chapter.id, fetched)
			}
			fetched
		}
	}

	protected abstract suspend fun fetchDetailsImpl(manga: Manga): Manga
	protected abstract suspend fun fetchPagesImpl(chapter: MangaChapter): List<MangaPage>
}

class EmptyMangaRepository(
	override val source: MangaSource,
) : MangaRepository {
	override val sortOrders: Set<SortOrder> = emptySet()

	override suspend fun getList(offset: Int, query: String?, sortOrder: SortOrder?): List<Manga> = emptyList()

	override suspend fun getDetails(manga: Manga): Manga = manga

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = emptyList()

	override suspend fun getPageUrl(page: MangaPage): String = page.url
}

class LocalMangaRepository(
	private val context: Context? = null,
) : MangaRepository {

	override val source: MangaSource = MangaSource("LOCAL", "Local Storage")

	override val sortOrders: Set<SortOrder> = setOf(SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

	override suspend fun getList(offset: Int, query: String?, sortOrder: SortOrder?): List<Manga> =
		withContext(Dispatchers.IO) {
			if (offset > 0) return@withContext emptyList()
			val roots = buildList {
				context?.filesDir?.let {
					add(File(it, "local_cbz"))
					add(File(it, "cbz_imports"))
				}
				context?.getExternalFilesDir(null)?.let { add(it) }
			}.filter { it.exists() && it.isDirectory }

			val archives = roots.flatMap { dir ->
				dir.listFiles().orEmpty().filter { f ->
					f.isDirectory || f.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip")
				}
			}.distinctBy { it.absolutePath }

			val parsed = archives.mapNotNull { fileToMangaOrNull(it) }
			val filtered = if (query.isNullOrBlank()) {
				parsed
			} else {
				parsed.filter { it.title.contains(query.trim(), ignoreCase = true) }
			}
			when (sortOrder) {
				SortOrder.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
				else -> filtered
			}
		}

	override suspend fun getDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
		val path = manga.url.removePrefix("file://").removePrefix("cbz://")
		val f = File(path)
		if (!f.exists()) return@withContext manga
		fileToMangaOrNull(f) ?: manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val rawPath = chapter.url.removePrefix("file://").removePrefix("cbz://").substringBefore('#')
		val f = File(rawPath)
		if (!f.exists()) return@withContext emptyList()

		if (f.isDirectory) {
			val images = f.walkTopDown()
				.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions }
				.sortedBy { it.name }
				.toList()
			images.mapIndexed { idx, img ->
				MangaPage(
					id = (f.absolutePath.hashCode().toLong() shl 16) + idx,
					url = "file://${img.absolutePath}",
					preview = "file://${img.absolutePath}",
					source = source,
				)
			}
		} else if (f.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip")) {
			runCatching {
				ZipFile(f).use { zip ->
					val entries = zip.entries().asSequence()
						.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in imageExtensions }
						.sortedBy { it.name }
						.toList()
					entries.mapIndexed { idx, entry ->
						MangaPage(
							id = (f.absolutePath.hashCode().toLong() shl 16) + idx,
							url = "cbz://${f.absolutePath}#${entry.name}",
							preview = null,
							source = source,
						)
					}
				}
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	private fun fileToMangaOrNull(f: File): Manga? {
		if (!f.exists()) return null
		val cleanTitle = f.nameWithoutExtension
			.replace('_', ' ')
			.replace('-', ' ')
			.trim()
			.ifBlank { f.name }
		val id = (f.absolutePath.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL)
		val uriStr = if (f.isDirectory) "file://${f.absolutePath}" else "cbz://${f.absolutePath}"
		val chapter = MangaChapter(
			id = (id xor 0x101L),
			name = cleanTitle,
			number = 1f,
			volume = 1,
			url = uriStr,
			scanlator = "Local Archive",
			uploadDate = f.lastModified(),
			branch = null,
			source = source,
		)
		val coverUri = if (f.isDirectory) {
			f.walkTopDown()
				.firstOrNull { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions }
				?.let { "file://${it.absolutePath}" }
		} else {
			null
		}
		return Manga(
			id = id,
			title = cleanTitle,
			altTitle = null,
			url = uriStr,
			publicUrl = uriStr,
			rating = -1f,
			isNsfw = false,
			coverUrl = coverUri,
			largeCoverUrl = coverUri,
			description = "Imported local archive (${f.name})",
			tags = emptySet(),
			state = MangaState.FINISHED,
			author = null,
			chapters = listOf(chapter),
			source = source,
		)
	}
}
