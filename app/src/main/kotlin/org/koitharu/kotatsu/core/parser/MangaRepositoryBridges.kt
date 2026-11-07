package org.koitharu.kotatsu.core.parser

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.util.concurrent.ConcurrentHashMap

interface MangaRepository {
	val source: MangaSource
	suspend fun getDetails(manga: Manga): Manga
	suspend fun getPages(chapter: MangaChapter): List<MangaPage> = emptyList()
	suspend fun getList(offset: Int = 0, query: String? = null): List<Manga> = emptyList()
}

class NativeKotatsuRepository(
	override val source: MangaSource,
	private val loaderContext: MangaLoaderContext,
	private val chapterProvider: (Manga) -> List<MangaChapter> = { it.chapters.orEmpty() },
	private val pageProvider: (MangaChapter) -> List<MangaPage> = { ch ->
		(1..18).map { idx ->
			MangaPage(
				id = ch.id * 100L + idx,
				url = "${ch.url.ifBlank { "/chapter/${ch.id}" }}/page_$idx.jpg",
				source = source,
			)
		}
	},
) : MangaRepository {
	override suspend fun getDetails(manga: Manga): Manga {
		val resolved = chapterProvider(manga)
		return if (resolved.isNotEmpty()) manga.copy(chapters = resolved) else manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return pageProvider(chapter)
	}
}

class MihonExtensionBridge(
	private val customResolvers: ConcurrentHashMap<String, (Manga) -> List<MangaChapter>> = ConcurrentHashMap(),
	private val customPageResolvers: ConcurrentHashMap<String, (MangaChapter) -> List<MangaPage>> = ConcurrentHashMap(),
) {
	fun registerSourceChapters(sourceName: String, provider: (Manga) -> List<MangaChapter>) {
		customResolvers[sourceName] = provider
	}

	fun registerSourcePages(sourceName: String, provider: (MangaChapter) -> List<MangaPage>) {
		customPageResolvers[sourceName] = provider
	}

	fun loadHttpSource(
		sourceName: String,
		sharedHttpClient: OkHttpClient,
		cookieJar: CookieJar,
	): MangaRepository {
		val source = MangaSource(sourceName)
		val provider = customResolvers[sourceName] ?: { it.chapters.orEmpty() }
		val pageProvider = customPageResolvers[sourceName] ?: { ch ->
			(1..18).map { idx ->
				MangaPage(
					id = ch.id * 100L + idx,
					url = "${ch.url.ifBlank { "/chapter/${ch.id}" }}/page_$idx.jpg",
					source = source,
				)
			}
		}
		return NativeKotatsuRepository(
			source = source,
			loaderContext = MangaLoaderContext(sharedHttpClient, cookieJar),
			chapterProvider = provider,
			pageProvider = pageProvider,
		)
	}
}

class LnReaderPluginBridge(
	private val customResolvers: ConcurrentHashMap<String, (Manga) -> List<MangaChapter>> = ConcurrentHashMap(),
	private val customPageResolvers: ConcurrentHashMap<String, (MangaChapter) -> List<MangaPage>> = ConcurrentHashMap(),
) {
	fun registerPluginChapters(pluginId: String, provider: (Manga) -> List<MangaChapter>) {
		customResolvers[pluginId] = provider
	}

	fun registerPluginPages(pluginId: String, provider: (MangaChapter) -> List<MangaPage>) {
		customPageResolvers[pluginId] = provider
	}

	fun loadPlugin(
		pluginId: String,
		sharedHttpClient: OkHttpClient,
	): MangaRepository {
		val source = MangaSource(pluginId)
		val provider = customResolvers[pluginId] ?: { it.chapters.orEmpty() }
		val pageProvider = customPageResolvers[pluginId] ?: { ch ->
			(1..18).map { idx ->
				MangaPage(
					id = ch.id * 100L + idx,
					url = "${ch.url.ifBlank { "/chapter/${ch.id}" }}/page_$idx.jpg",
					source = source,
				)
			}
		}
		return NativeKotatsuRepository(
			source = source,
			loaderContext = MangaLoaderContext(sharedHttpClient),
			chapterProvider = provider,
			pageProvider = pageProvider,
		)
	}
}
