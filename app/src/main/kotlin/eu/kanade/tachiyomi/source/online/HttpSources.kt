package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.security.MessageDigest

abstract class HttpSource : CatalogueSource {

	protected val network: NetworkHelper by injectLazy()

	abstract val baseUrl: String

	open val versionId: Int = 1

	override val id: Long by lazy {
		val key = "${name.lowercase()}/$lang/$versionId"
		val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
		(0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
	}

	val headers: Headers by lazy { headersBuilder().build() }

	open val client: OkHttpClient
		get() = network.client

	protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
		add("User-Agent", network.defaultUserAgent())
	}

	override fun toString(): String = "$name (${lang.uppercase()})"

	@Deprecated("Use getPopularManga instead")
	override fun fetchPopularManga(page: Int): Observable<MangasPage> {
		return client.newCall(popularMangaRequest(page))
			.asObservableSuccess()
			.map { response -> popularMangaParse(response) }
	}

	protected abstract fun popularMangaRequest(page: Int): Request
	protected abstract fun popularMangaParse(response: Response): MangasPage

	@Deprecated("Use getSearchManga instead")
	override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
		return Observable.defer {
			try {
				client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
			} catch (e: NoClassDefFoundError) {
				throw RuntimeException(e)
			}
		}.map { response -> searchMangaParse(response) }
	}

	protected abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
	protected abstract fun searchMangaParse(response: Response): MangasPage

	@Deprecated("Use getLatestUpdates instead")
	override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
		return client.newCall(latestUpdatesRequest(page))
			.asObservableSuccess()
			.map { response -> latestUpdatesParse(response) }
	}

	protected abstract fun latestUpdatesRequest(page: Int): Request
	protected abstract fun latestUpdatesParse(response: Response): MangasPage

	override suspend fun getMangaDetails(manga: SManga): SManga {
		return fetchMangaDetails(manga).toBlocking().first()
	}

	@Deprecated("Use getMangaDetails instead")
	override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
		return client.newCall(mangaDetailsRequest(manga))
			.asObservableSuccess()
			.map { response ->
				mangaDetailsParse(response).apply { initialized = true }
			}
	}

	open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
	protected abstract fun mangaDetailsParse(response: Response): SManga

	open fun getMangaUrl(manga: SManga): String {
		return mangaDetailsRequest(manga).url.toString()
	}

	override suspend fun getChapterList(manga: SManga): List<SChapter> {
		return fetchChapterList(manga).toBlocking().first()
	}

	@Deprecated("Use getChapterList instead")
	override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
		return client.newCall(chapterListRequest(manga))
			.asObservableSuccess()
			.map { response -> chapterListParse(response) }
	}

	protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
	protected abstract fun chapterListParse(response: Response): List<SChapter>

	open fun getChapterUrl(chapter: SChapter): String {
		return pageListRequest(chapter).url.toString()
	}

	override suspend fun getPageList(chapter: SChapter): List<Page> {
		return fetchPageList(chapter).toBlocking().first()
	}

	@Deprecated("Use getPageList instead")
	override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
		return client.newCall(pageListRequest(chapter))
			.asObservableSuccess()
			.map { response -> pageListParse(response) }
	}

	protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
	protected abstract fun pageListParse(response: Response): List<Page>

	open suspend fun getImageUrl(page: Page): String {
		return fetchImageUrl(page).toBlocking().first()
	}

	@Deprecated("Use getImageUrl instead")
	open fun fetchImageUrl(page: Page): Observable<String> {
		return client.newCall(imageUrlRequest(page))
			.asObservableSuccess()
			.map { imageUrlParse(it) }
	}

	protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)
	protected abstract fun imageUrlParse(response: Response): String

	open suspend fun getImage(page: Page): Response {
		return client.newCall(imageRequest(page)).awaitSuccess()
	}

	open fun imageRequest(page: Page): Request {
		val target = page.imageUrl?.takeIf { it.isNotBlank() } ?: page.url
		return GET(target, headers)
	}

	fun SChapter.setUrlWithoutDomain(url: String) {
		this.url = getUrlWithoutDomain(url)
	}

	fun SManga.setUrlWithoutDomain(url: String) {
		this.url = getUrlWithoutDomain(url)
	}

	private fun getUrlWithoutDomain(orig: String): String {
		return try {
			val uri = URI(orig.replace(" ", "%20"))
			var out = uri.path.orEmpty()
			if (uri.query != null) {
				out += "?" + uri.query
			}
			if (uri.fragment != null) {
				out += "#" + uri.fragment
			}
			out
		} catch (_: Exception) {
			orig
		}
	}
}

abstract class ParsedHttpSource : HttpSource() {

	protected fun Response.asJsoup(html: String? = null): Document {
		return Jsoup.parse(html ?: body!!.string(), request.url.toString())
	}

	override fun popularMangaParse(response: Response): MangasPage {
		val document = response.asJsoup()
		val mangas = document.select(popularMangaSelector()).map { element ->
			popularMangaFromElement(element)
		}
		val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
			document.select(selector).first()
		} != null
		return MangasPage(mangas, hasNextPage)
	}

	protected abstract fun popularMangaSelector(): String
	protected abstract fun popularMangaFromElement(element: Element): SManga
	protected abstract fun popularMangaNextPageSelector(): String?

	override fun searchMangaParse(response: Response): MangasPage {
		val document = response.asJsoup()
		val mangas = document.select(searchMangaSelector()).map { element ->
			searchMangaFromElement(element)
		}
		val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
			document.select(selector).first()
		} != null
		return MangasPage(mangas, hasNextPage)
	}

	protected abstract fun searchMangaSelector(): String
	protected abstract fun searchMangaFromElement(element: Element): SManga
	protected abstract fun searchMangaNextPageSelector(): String?

	override fun latestUpdatesParse(response: Response): MangasPage {
		val document = response.asJsoup()
		val mangas = document.select(latestUpdatesSelector()).map { element ->
			latestUpdatesFromElement(element)
		}
		val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
			document.select(selector).first()
		} != null
		return MangasPage(mangas, hasNextPage)
	}

	protected abstract fun latestUpdatesSelector(): String
	protected abstract fun latestUpdatesFromElement(element: Element): SManga
	protected abstract fun latestUpdatesNextPageSelector(): String?

	override fun mangaDetailsParse(response: Response): SManga {
		return mangaDetailsParse(response.asJsoup())
	}

	protected abstract fun mangaDetailsParse(document: Document): SManga

	override fun chapterListParse(response: Response): List<SChapter> {
		val document = response.asJsoup()
		return document.select(chapterListSelector()).map { chapterFromElement(it) }
	}

	protected abstract fun chapterListSelector(): String
	protected abstract fun chapterFromElement(element: Element): SChapter

	override fun pageListParse(response: Response): List<Page> {
		return pageListParse(response.asJsoup())
	}

	protected abstract fun pageListParse(document: Document): List<Page>

	override fun imageUrlParse(response: Response): String {
		return imageUrlParse(response.asJsoup())
	}

	protected abstract fun imageUrlParse(document: Document): String
}
