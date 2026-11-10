package eu.kanade.tachiyomi.source

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rx.Observable

interface Source {
	val id: Long
	val name: String
	val lang: String
		get() = ""

	@Suppress("DEPRECATION")
	@Deprecated("Use getMangaDetails instead")
	fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.empty()

	@Suppress("DEPRECATION")
	@Deprecated("Use getChapterList instead")
	fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.empty()

	@Suppress("DEPRECATION")
	@Deprecated("Use getPageList instead")
	fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.empty()

	@Suppress("DEPRECATION")
	suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
		fetchMangaDetails(manga).toBlocking().first()
	}

	@Suppress("DEPRECATION")
	suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
		fetchChapterList(manga).toBlocking().first()
	}

	@Suppress("DEPRECATION")
	suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
		fetchPageList(chapter).toBlocking().first()
	}
}

interface CatalogueSource : Source {
	override val lang: String
	val supportsLatest: Boolean

	@Suppress("DEPRECATION")
	@Deprecated("Use getPopularManga instead")
	fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.empty()

	@Suppress("DEPRECATION")
	@Deprecated("Use getSearchManga instead")
	fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.empty()

	@Suppress("DEPRECATION")
	@Deprecated("Use getLatestUpdates instead")
	fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.empty()

	@Suppress("DEPRECATION")
	suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
		fetchPopularManga(page).toBlocking().first()
	}

	@Suppress("DEPRECATION")
	suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withContext(Dispatchers.IO) {
		fetchSearchManga(page, query, filters).toBlocking().first()
	}

	@Suppress("DEPRECATION")
	suspend fun getLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
		fetchLatestUpdates(page).toBlocking().first()
	}

	fun getFilterList(): FilterList = FilterList()
}

interface ConfigurableSource : Source {
	fun setupPreferenceScreen(screen: PreferenceScreen)
}

interface SourceFactory {
	fun createSources(): List<Source>
}
