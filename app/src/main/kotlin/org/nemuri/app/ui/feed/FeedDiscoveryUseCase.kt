package org.nemuri.app.ui.feed

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.almostEquals
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.suggestions.domain.SuggestionRepository
import org.koitharu.kotatsu.suggestions.domain.TagsBlacklist
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

@Immutable
data class DiscoveryRecommendation(
	val manga: Manga,
	val reasonLabel: String,
	val sourceTitle: String,
	val inLibrary: Boolean = false,
)

@Immutable
data class DiscoverySection(
	val id: String,
	val title: String,
	val subtitle: String? = null,
	val items: List<DiscoveryRecommendation>,
)

@Immutable
data class DiscoveryFeedResult(
	val previewItems: List<DiscoveryRecommendation> = emptyList(),
	val sections: List<DiscoverySection> = emptyList(),
	val matchedGenres: List<String> = emptyList(),
	val activeSourceCount: Int = 0,
)

private val TITLE_STOP_WORDS = setOf(
	"the", "and", "for", "with", "from", "that", "this", "into", "over", "after",
	"manga", "novel", "chapter", "vol", "volume", "story", "tales", "chronicles",
	"world", "life", "love", "time", "days", "reincarnated", "another",
)

@Reusable
class FeedDiscoveryUseCase @Inject constructor(
	@ApplicationContext private val context: Context,
	private val sourcesRepository: MangaSourcesRepository,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
	private val favouritesRepository: FavouritesRepository,
	private val historyRepository: HistoryRepository,
	private val suggestionRepository: SuggestionRepository,
	private val settings: AppSettings,
) {

	suspend fun loadDiscoveryFeed(
		preferredHomeSourceName: String? = settings.exploreHomeSource,
	): DiscoveryFeedResult = coroutineScope {
		val excludeNsfw = settings.isNsfwContentDisabled || settings.isSuggestionsExcludeNsfw
		val rawEnabled = runCatchingCancellable { sourcesRepository.getEnabledSources() }
			.getOrDefault(emptyList())
			.filterNot { excludeNsfw && it.isNsfw() }

		if (rawEnabled.isEmpty()) {
			return@coroutineScope DiscoveryFeedResult(activeSourceCount = 0)
		}

		val pinnedSet = runCatchingCancellable { sourcesRepository.getPinnedSources() }
			.getOrDefault(emptySet())

		val historyManga = runCatchingCancellable { historyRepository.getList(0, 24) }
			.getOrDefault(emptyList())
		val favManga = runCatchingCancellable { favouritesRepository.getAllManga() }
			.getOrDefault(emptyList())

		val libIds = favManga.mapTo(HashSet(favManga.size)) { it.id }
		val libUrls = favManga.mapTo(HashSet(favManga.size)) { it.url }
		val historyIds = historyManga.mapTo(HashSet(historyManga.size)) { it.id }
		val knownIds = HashSet<Long>(libIds.size + historyIds.size).apply {
			addAll(libIds)
			addAll(historyIds)
		}
		val knownTitles = (favManga.asSequence() + historyManga.asSequence())
			.mapTo(HashSet()) { normalizeTitleKey(it.title) }

		val blacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val seedPool = (historyManga + favManga.take(36)).distinctBy { it.id }

		// Rank user's top genres across library & reading history
		val topGenres = seedPool
			.asSequence()
			.flatMap { it.tags.asSequence() }
			.filterNot { it in blacklist }
			.groupingBy { it.title.trim() }
			.eachCount()
			.entries
			.filter { it.key.isNotEmpty() }
			.sortedByDescending { it.value }
			.map { it.key }
			.take(5)

		// Extract author or meaningful title keyword for similarity search
		val topAuthors = seedPool
			.asSequence()
			.flatMap { it.authors.asSequence() }
			.map { it.trim() }
			.filter { it.length in 3..42 }
			.distinct()
			.take(2)
			.toList()

		val seedKeywordInfo: Pair<String, String>? = if (topAuthors.isNotEmpty()) {
			val author = topAuthors.first()
			author to "More by $author"
		} else {
			extractSeedKeyword(seedPool)?.let { (kw, sourceMangaTitle) ->
				kw to "Because you read ${sourceMangaTitle.take(26)}"
			}
		}

		// Order candidate sources: preferred home -> pinned manga -> seed sources -> enabled manga -> novels
		val seedSourceNames = seedPool.mapTo(LinkedHashSet()) { it.source.name }
		val orderedSources = buildList {
			rawEnabled.firstOrNull { it.name == preferredHomeSourceName }?.let(::add)
			rawEnabled.filter { it in pinnedSet && !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.filter { it.name in seedSourceNames && !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.filter { !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.forEach { if (it !in this) add(it) }
		}
		val primarySources = orderedSources.take(3)

		val semaphore = Semaphore(3)
		val sectionsOut = ArrayList<DiscoverySection>(5)
		val globalSeenIds = HashSet<Long>()
		val globalSeenTitles = HashSet<String>()

		fun filterFreshRecommendations(
			items: List<Manga>,
			reason: String,
			allowLibraryFallback: Boolean = false,
		): List<DiscoveryRecommendation> {
			val out = ArrayList<DiscoveryRecommendation>(items.size)
			for (manga in items) {
				if (excludeNsfw && manga.isNsfw()) continue
				if (blacklist.isNotEmpty() && manga in blacklist) continue
				val inLib = manga.id in libIds || manga.url in libUrls
				if (!allowLibraryFallback && (inLib || manga.id in knownIds || normalizeTitleKey(manga.title) in knownTitles)) {
					continue
				}
				val normTitle = normalizeTitleKey(manga.title)
				if (!globalSeenIds.add(manga.id) || !globalSeenTitles.add(normTitle)) continue
				out += DiscoveryRecommendation(
					manga = manga,
					reasonLabel = reason,
					sourceTitle = manga.source.getTitle(context),
					inLibrary = inLib,
				)
			}
			return out
		}

		// Stage 1: Real MangaTag genre discovery across top candidate sources
		if (topGenres.isNotEmpty()) {
			val genreFetchResults = primarySources.take(2).mapIndexed { sourceIdx, source ->
				async {
					semaphore.withPermit {
						withTimeoutOrNull(11_000L) {
							fetchGenreMatchedLists(
								source = source,
								preferredGenres = if (sourceIdx == 0) topGenres.take(2) else topGenres.drop(1).take(2).ifEmpty { topGenres.take(1) },
								blacklist = blacklist,
							)
						}.orEmpty()
					}
				}
			}.awaitAll().flatten()

			val groupedByGenre = LinkedHashMap<String, MutableList<Manga>>()
			for ((genreName, list) in genreFetchResults) {
				groupedByGenre.getOrPut(genreName) { ArrayList() }.addAll(list)
			}
			for ((genreName, list) in groupedByGenre) {
				val recs = filterFreshRecommendations(
					items = list,
					reason = "Top in $genreName",
				).take(14)
				if (recs.isNotEmpty()) {
					sectionsOut += DiscoverySection(
						id = "genre_${genreName.lowercase(Locale.ROOT)}",
						title = "Because You Like $genreName",
						subtitle = "Matched via source genre tags",
						items = recs,
					)
				}
			}
		}

		// Stage 2: Author / Title Keyword Search
		if (seedKeywordInfo != null) {
			val (searchTerm, reasonText) = seedKeywordInfo
			val searchSource = primarySources.firstOrNull()
			if (searchSource != null) {
				val keywordMatches = semaphore.withPermit {
					withTimeoutOrNull(9_000L) {
						fetchKeywordMatches(searchSource, searchTerm)
					}.orEmpty()
				}
				val recs = filterFreshRecommendations(
					items = keywordMatches,
					reason = reasonText,
				).take(12)
				if (recs.isNotEmpty()) {
					sectionsOut += DiscoverySection(
						id = "keyword_match",
						title = reasonText,
						subtitle = "Related titles on ${searchSource.getTitle(context)}",
						items = recs,
					)
				}
			}
		}

		// Stage 3: Stored background SuggestionRepository boost
		val cachedSuggestions = runCatchingCancellable {
			suggestionRepository.observeAll(24, emptySet()).first()
		}.getOrDefault(emptyList())
		if (cachedSuggestions.isNotEmpty()) {
			val cachedRecs = filterFreshRecommendations(
				items = cachedSuggestions,
				reason = "Matched to your library",
			).take(14)
			if (cachedRecs.isNotEmpty()) {
				sectionsOut += DiscoverySection(
					id = "stored_suggestions",
					title = "Matched to Your Taste",
					subtitle = "Curated from your reading profile",
					items = cachedRecs,
				)
			}
		}

		// Stage 4: Guaranteed Popular Non-Library Fallback (NEVER empty when >= 1 source works!)
		val totalCollectedSoFar = sectionsOut.sumOf { it.items.size }
		if (totalCollectedSoFar < 12) {
			val fallbackCandidates = orderedSources.take(4)
			val popularLists = fallbackCandidates.map { source ->
				async {
					semaphore.withPermit {
						withTimeoutOrNull(10_000L) {
							fetchPopularFromSource(source)
						}.orEmpty()
					}
				}
			}.awaitAll()

			val interleaved = interleaveLists(popularLists)
			var fallbackRecs = filterFreshRecommendations(
				items = interleaved,
				reason = if (topGenres.isEmpty()) "Popular pick" else "Trending now",
				allowLibraryFallback = false,
			).take(18)

			// Ultimate safety net: if every single returned title happened to be in library/history already,
			// still surface popular titles so For You is NEVER blank when a source is enabled.
			if (fallbackRecs.isEmpty() && sectionsOut.isEmpty() && interleaved.isNotEmpty()) {
				fallbackRecs = filterFreshRecommendations(
					items = interleaved,
					reason = "Popular on your sources",
					allowLibraryFallback = true,
				).take(16)
			}

			if (fallbackRecs.isNotEmpty()) {
				sectionsOut += DiscoverySection(
					id = "popular_fallback",
					title = if (sectionsOut.isEmpty()) "Popular Picks For You" else "Trending Across Your Sources",
					subtitle = "Fresh discoveries outside your library",
					items = fallbackRecs,
				)
			}
		}

		val previewList = sectionsOut
			.flatMap { it.items }
			.distinctBy { it.manga.id }
			.take(16)

		DiscoveryFeedResult(
			previewItems = previewList,
			sections = sectionsOut,
			matchedGenres = topGenres,
			activeSourceCount = rawEnabled.size,
		)
	}

	private suspend fun fetchGenreMatchedLists(
		source: MangaSource,
		preferredGenres: List<String>,
		blacklist: TagsBlacklist,
	): List<Pair<String, List<Manga>>> = runCatchingCancellable {
		val repo = mangaRepositoryFactoryProvider.get().create(source)
		val availableTags = repo.getFilterOptions().availableTags
		if (availableTags.isEmpty()) return@runCatchingCancellable emptyList()

		val order = when {
			SortOrder.POPULARITY in repo.sortOrders -> SortOrder.POPULARITY
			SortOrder.RATING in repo.sortOrders -> SortOrder.RATING
			SortOrder.UPDATED in repo.sortOrders -> SortOrder.UPDATED
			else -> repo.defaultSortOrder
		}

		val results = ArrayList<Pair<String, List<Manga>>>(2)
		for (genreTitle in preferredGenres) {
			val matchedTag = availableTags.firstOrNull { tag ->
				tag !in blacklist && tag.title.equals(genreTitle, ignoreCase = true)
			} ?: availableTags.firstOrNull { tag ->
				tag !in blacklist && tag.title.almostEquals(genreTitle, 0.35f)
			} ?: continue

			val list = runCatchingCancellable {
				repo.getList(
					offset = 0,
					order = order,
					filter = MangaListFilter(tags = setOf(matchedTag)),
				).take(16)
			}.getOrDefault(emptyList())

			if (list.isNotEmpty()) {
				results += matchedTag.title to list
			}
			if (results.size >= 2) break
		}
		results
	}.getOrDefault(emptyList())

	private suspend fun fetchKeywordMatches(
		source: MangaSource,
		query: String,
	): List<Manga> = runCatchingCancellable {
		val repo = mangaRepositoryFactoryProvider.get().create(source)
		if (!repo.filterCapabilities.isSearchSupported) return@runCatchingCancellable emptyList()
		val order = when {
			SortOrder.RELEVANCE in repo.sortOrders -> SortOrder.RELEVANCE
			SortOrder.POPULARITY in repo.sortOrders -> SortOrder.POPULARITY
			else -> repo.defaultSortOrder
		}
		repo.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(query = query),
		).take(14)
	}.getOrDefault(emptyList())

	private suspend fun fetchPopularFromSource(source: MangaSource): List<Manga> = runCatchingCancellable {
		val repo = mangaRepositoryFactoryProvider.get().create(source)
		val order = when {
			SortOrder.POPULARITY in repo.sortOrders -> SortOrder.POPULARITY
			SortOrder.POPULARITY_WEEK in repo.sortOrders -> SortOrder.POPULARITY_WEEK
			SortOrder.RATING in repo.sortOrders -> SortOrder.RATING
			SortOrder.UPDATED in repo.sortOrders -> SortOrder.UPDATED
			else -> repo.defaultSortOrder
		}
		repo.getList(offset = 0, order = order, filter = MangaListFilter()).take(18)
	}.getOrDefault(emptyList())

	private fun extractSeedKeyword(seedPool: List<Manga>): Pair<String, String>? {
		for (manga in seedPool.take(6)) {
			val token = manga.title
				.split(Regex("[^A-Za-z0-9']+"))
				.map { it.trim('\'') }
				.firstOrNull { word ->
					word.length >= 5 && word.lowercase(Locale.ROOT) !in TITLE_STOP_WORDS
				}
			if (token != null) {
				return token to manga.title
			}
		}
		return null
	}

	private fun interleaveLists(lists: List<List<Manga>>): List<Manga> {
		val maxLen = lists.maxOfOrNull { it.size } ?: return emptyList()
		val out = ArrayList<Manga>(lists.sumOf { it.size })
		for (idx in 0 until maxLen) {
			for (list in lists) {
				if (idx < list.size) {
					out += list[idx]
				}
			}
		}
		return out
	}

	private fun normalizeTitleKey(title: String): String =
		title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
}
