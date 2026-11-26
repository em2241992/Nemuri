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
import org.koitharu.kotatsu.core.model.isLocal
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
	val matchedGenres: Set<String> = emptySet(),
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
	val discoveryGridItems: List<DiscoveryRecommendation> = emptyList(),
	val matchedGenres: List<String> = emptyList(),
	val activeSourceCount: Int = 0,
)

private val TITLE_STOP_WORDS = setOf(
	"the", "and", "for", "with", "from", "that", "this", "into", "over", "after",
	"manga", "novel", "chapter", "vol", "volume", "story", "tales", "chronicles",
	"world", "life", "love", "time", "days", "reincarnated", "another", "edition",
	"official", "colored", "digital", "part", "season",
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
		val profileGenres = seedPool
			.asSequence()
			.flatMap { it.tags.asSequence() }
			.filterNot { it in blacklist }
			.groupingBy { it.title.trim() }
			.eachCount()
			.entries
			.filter { it.key.isNotEmpty() }
			.sortedByDescending { it.value }
			.map { it.key }
			.take(6)

		// Order candidate sources: preferred home -> pinned manga -> seed sources -> enabled manga -> novels
		val seedSourceNames = seedPool.mapTo(LinkedHashSet()) { it.source.name }
		val orderedSources = buildList {
			rawEnabled.firstOrNull { it.name == preferredHomeSourceName }?.let(::add)
			rawEnabled.filter { it in pinnedSet && !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.filter { it.name in seedSourceNames && !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.filter { !it.isNovelSource }.forEach { if (it !in this) add(it) }
			rawEnabled.forEach { if (it !in this) add(it) }
		}
		val primarySources = orderedSources.take(4)

		val semaphore = Semaphore(4)
		val sectionsOut = ArrayList<DiscoverySection>(6)
		val carouselSeenIds = HashSet<Long>()
		val carouselSeenTitles = HashSet<String>()
		val overflowPool = ArrayList<DiscoveryRecommendation>(64)

		fun inferGenresForManga(manga: Manga, explicitGenres: Set<String> = emptySet()): Set<String> {
			if (explicitGenres.isEmpty() && manga.tags.isEmpty()) return emptySet()
			val out = LinkedHashSet<String>(explicitGenres.size + manga.tags.size)
			out.addAll(explicitGenres)
			for (tag in manga.tags) {
				val t = tag.title.trim()
				if (t.isNotEmpty()) out.add(t)
			}
			return out
		}

		fun buildRecommendations(
			items: List<Manga>,
			reason: String,
			explicitGenres: Set<String> = emptySet(),
			seenIds: MutableSet<Long>,
			seenTitles: MutableSet<String>,
			allowLibraryFallback: Boolean = false,
		): List<DiscoveryRecommendation> {
			val out = ArrayList<DiscoveryRecommendation>(items.size)
			for (manga in items) {
				if (excludeNsfw && manga.isNsfw()) continue
				if (blacklist.isNotEmpty() && manga in blacklist) continue
				val inLib = manga.id in libIds || manga.url in libUrls
				val normTitle = normalizeTitleKey(manga.title)
				if (normTitle.isEmpty()) continue
				if (!allowLibraryFallback && (inLib || manga.id in knownIds || normTitle in knownTitles)) {
					continue
				}
				if (!seenIds.add(manga.id) || !seenTitles.add(normTitle)) continue
				out += DiscoveryRecommendation(
					manga = manga,
					reasonLabel = reason,
					sourceTitle = manga.source.getTitle(context),
					inLibrary = inLib,
					matchedGenres = inferGenresForManga(manga, explicitGenres),
				)
			}
			return out
		}

		// Kick off parallel network/cache fetches so total latency stays fast:
		// 1) Stored SuggestionRepository items
		val cachedSuggestionsDeferred = async {
			runCatchingCancellable {
				suggestionRepository.observeAll(36, emptySet()).first()
			}.getOrDefault(emptyList())
		}

		// 2) Genre-matched lists across primary sources
		val genreListsDeferred = async {
			if (profileGenres.isEmpty()) {
				emptyList()
			} else {
				primarySources.take(3).mapIndexed { sourceIdx, source ->
					async {
						semaphore.withPermit {
							withTimeoutOrNull(11_000L) {
								val slice = when (sourceIdx) {
									0 -> profileGenres.take(2)
									1 -> profileGenres.drop(2).take(2).ifEmpty { profileGenres.take(2) }
									else -> profileGenres.drop(1).take(2).ifEmpty { profileGenres.take(1) }
								}
								fetchGenreMatchedLists(
									source = source,
									preferredGenres = slice,
									blacklist = blacklist,
								)
							}.orEmpty()
						}
					}
				}.awaitAll().flatten()
			}
		}

		// 3) Concrete "Because You Read <Series>" seeds (1-2 series from recent history / favourites)
		val concreteSeeds = (historyManga.take(4) + favManga.take(4))
			.filter { it.title.isNotBlank() }
			.distinctBy { normalizeTitleKey(it.title) }
			.take(2)

		val becauseYouReadDeferred = concreteSeeds.mapIndexed { idx, seedManga ->
			async {
				val targetSource = orderedSources.firstOrNull {
					!it.isLocal && it.name == seedManga.source.name
				} ?: primarySources.getOrElse(idx % primarySources.size) { primarySources.first() }

				val seedTags = seedManga.tags
					.filterNot { it in blacklist }
					.map { it.title.trim() }
					.filter { it.isNotEmpty() }
					.take(2)
				val authorOrKeyword = seedManga.authors
					.map { it.trim() }
					.firstOrNull { it.length in 3..40 }
					?: extractSingleMangaKeyword(seedManga)

				val tagMatches = if (seedTags.isNotEmpty()) {
					semaphore.withPermit {
						withTimeoutOrNull(10_000L) {
							fetchGenreMatchedLists(targetSource, seedTags, blacklist)
								.flatMap { it.second }
						}.orEmpty()
					}
				} else {
					emptyList()
				}

				val keywordMatches = if (authorOrKeyword != null) {
					semaphore.withPermit {
						withTimeoutOrNull(9_000L) {
							fetchKeywordMatches(targetSource, authorOrKeyword)
						}.orEmpty()
					}
				} else {
					emptyList()
				}

				// Fallback to primary source if seedManga's own source yielded nothing
				val combined = (keywordMatches + tagMatches).ifEmpty {
					val backupSource = primarySources.firstOrNull { it != targetSource }
					if (backupSource != null && seedTags.isNotEmpty()) {
						semaphore.withPermit {
							withTimeoutOrNull(9_000L) {
								fetchGenreMatchedLists(backupSource, seedTags.take(1), blacklist)
									.flatMap { it.second }
							}.orEmpty()
						}
					} else {
						emptyList()
					}
				}

				Triple(seedManga, seedTags, combined)
			}
		}

		// 4) Broad multi-source discovery catalog for the 24-48 item vertical grid
		val broadSourceListsDeferred = primarySources.map { source ->
			async {
				semaphore.withPermit {
					withTimeoutOrNull(11_000L) {
						source to fetchExtendedDiscoveryFromSource(source)
					} ?: (source to emptyList())
				}
			}
		}

		val cachedSuggestions = cachedSuggestionsDeferred.await()
		val genreFetchResults = genreListsDeferred.await()
		val becauseYouReadResults = becauseYouReadDeferred.awaitAll()
		val broadSourceLists = broadSourceListsDeferred.awaitAll()
		val interleavedBroad = interleaveLists(broadSourceLists.map { it.second })

		// Group genre results
		val groupedByGenre = LinkedHashMap<String, MutableList<Manga>>()
		for ((genreName, list) in genreFetchResults) {
			groupedByGenre.getOrPut(genreName) { ArrayList() }.addAll(list)
		}

		// SECTION 1: Explicit "Top Picks For Your Taste" Carousel
		val topTasteCandidates = buildList {
			addAll(cachedSuggestions)
			groupedByGenre.values.forEach { addAll(it.take(6)) }
			if (size < 14) {
				addAll(interleavedBroad.take(18))
			}
		}
		val topTasteAll = buildRecommendations(
			items = topTasteCandidates,
			reason = if (profileGenres.isNotEmpty()) {
				"Matched to ${profileGenres.first()}"
			} else {
				"Top pick for you"
			},
			explicitGenres = profileGenres.take(2).toSet(),
			seenIds = carouselSeenIds,
			seenTitles = carouselSeenTitles,
		)
		if (topTasteAll.isNotEmpty()) {
			sectionsOut += DiscoverySection(
				id = "top_picks",
				title = "Top Picks For Your Taste",
				subtitle = if (profileGenres.isNotEmpty()) {
					"Curated from ${profileGenres.take(3).joinToString(", ")}"
				} else {
					"Curated across your enabled sources"
				},
				items = topTasteAll.take(14),
			)
			overflowPool += topTasteAll.drop(14)
		}

		// SECTION 2: Explicit "Because You Read <Series>" Carousel(s)
		for ((idx, triple) in becauseYouReadResults.withIndex()) {
			val (seedManga, seedTags, matches) = triple
			if (matches.isEmpty()) continue
			val shortSeries = shortenSeriesTitle(seedManga.title)
			val seedRecs = buildRecommendations(
				items = matches,
				reason = "Because you read $shortSeries",
				explicitGenres = seedTags.toSet(),
				seenIds = carouselSeenIds,
				seenTitles = carouselSeenTitles,
			)
			if (seedRecs.isNotEmpty()) {
				sectionsOut += DiscoverySection(
					id = "because_read_$idx",
					title = "Because You Read $shortSeries",
					subtitle = if (seedTags.isNotEmpty()) {
						"Similar ${seedTags.take(2).joinToString(" & ")} picks"
					} else {
						"Related titles from your sources"
					},
					items = seedRecs.take(14),
				)
				overflowPool += seedRecs.drop(14)
			}
		}

		// SECTION 3: Genre-Matched Carousels ("Because You Like <Genre>")
		for ((genreName, list) in groupedByGenre) {
			val genreRecs = buildRecommendations(
				items = list,
				reason = "Top in $genreName",
				explicitGenres = setOf(genreName),
				seenIds = carouselSeenIds,
				seenTitles = carouselSeenTitles,
			)
			if (genreRecs.isNotEmpty()) {
				if (sectionsOut.size < 5) {
					sectionsOut += DiscoverySection(
						id = "genre_${genreName.lowercase(Locale.ROOT)}",
						title = "Because You Like $genreName",
						subtitle = "Matched via source genre tags",
						items = genreRecs.take(14),
					)
					overflowPool += genreRecs.drop(14)
				} else {
					overflowPool += genreRecs
				}
			}
		}

		// Fallback carousel if user has zero library/history and sectionsOut is still empty
		if (sectionsOut.isEmpty() && interleavedBroad.isNotEmpty()) {
			var popularRecs = buildRecommendations(
				items = interleavedBroad,
				reason = "Trending pick",
				seenIds = carouselSeenIds,
				seenTitles = carouselSeenTitles,
				allowLibraryFallback = false,
			)
			if (popularRecs.isEmpty()) {
				popularRecs = buildRecommendations(
					items = interleavedBroad,
					reason = "Popular on your sources",
					seenIds = carouselSeenIds,
					seenTitles = carouselSeenTitles,
					allowLibraryFallback = true,
				)
			}
			if (popularRecs.isNotEmpty()) {
				sectionsOut += DiscoverySection(
					id = "top_picks_popular",
					title = "Top Picks For Your Taste",
					subtitle = "Trending discoveries across your sources",
					items = popularRecs.take(14),
				)
				overflowPool += popularRecs.drop(14)
			}
		}

		// BUILD RICH 24-48 ITEM VERTICAL DISCOVERY GRID
		val gridSeenIds = HashSet<Long>(64)
		val gridSeenTitles = HashSet<String>(64)
		val gridItems = ArrayList<DiscoveryRecommendation>(48)

		fun addCandidateToGrid(
			manga: Manga,
			defaultReason: String,
			explicitGenres: Set<String> = emptySet(),
			skipIfInCarousels: Boolean,
			allowInLibrary: Boolean = false,
		) {
			if (gridItems.size >= 48) return
			if (excludeNsfw && manga.isNsfw()) return
			if (blacklist.isNotEmpty() && manga in blacklist) return
			val normTitle = normalizeTitleKey(manga.title)
			if (normTitle.isEmpty()) return
			if (skipIfInCarousels && (manga.id in carouselSeenIds || normTitle in carouselSeenTitles)) return
			val inLib = manga.id in libIds || manga.url in libUrls
			if (!allowInLibrary && (inLib || manga.id in knownIds || normTitle in knownTitles)) return
			if (!gridSeenIds.add(manga.id) || !gridSeenTitles.add(normTitle)) return

			val itemGenres = inferGenresForManga(manga, explicitGenres)
			val matchedProfileGenre = profileGenres.firstOrNull { pg ->
				itemGenres.any { g -> g.equals(pg, ignoreCase = true) }
			}
			val srcTitle = manga.source.getTitle(context)
			val reason = when {
				matchedProfileGenre != null -> "Matches $matchedProfileGenre"
				defaultReason.isNotBlank() -> defaultReason
				else -> "Popular on $srcTitle"
			}
			gridItems += DiscoveryRecommendation(
				manga = manga,
				reasonLabel = reason,
				sourceTitle = srcTitle,
				inLibrary = inLib,
				matchedGenres = itemGenres,
			)
		}

		// Pass 1: Overflow recommendations from genre/suggestion/seed queries not shown in carousels
		for (rec in overflowPool) {
			if (gridItems.size >= 48) break
			val normTitle = normalizeTitleKey(rec.manga.title)
			if (normTitle.isNotEmpty() && gridSeenIds.add(rec.manga.id) && gridSeenTitles.add(normTitle)) {
				gridItems += rec
			}
		}

		// Pass 2: Broad multi-source Popular & Latest discoveries not already in carousels
		for (manga in interleavedBroad) {
			if (gridItems.size >= 48) break
			addCandidateToGrid(
				manga = manga,
				defaultReason = "Discover on ${manga.source.getTitle(context)}",
				skipIfInCarousels = true,
				allowInLibrary = false,
			)
		}

		// Pass 3: If fewer than 24 unique grid items (e.g. single small catalog), backfill cleanly
		if (gridItems.size < 24) {
			for (manga in interleavedBroad) {
				if (gridItems.size >= 36) break
				addCandidateToGrid(
					manga = manga,
					defaultReason = "Trending on ${manga.source.getTitle(context)}",
					skipIfInCarousels = false,
					allowInLibrary = false,
				)
			}
		}
		if (gridItems.isEmpty() && interleavedBroad.isNotEmpty()) {
			for (manga in interleavedBroad) {
				if (gridItems.size >= 24) break
				addCandidateToGrid(
					manga = manga,
					defaultReason = "Popular on ${manga.source.getTitle(context)}",
					skipIfInCarousels = false,
					allowInLibrary = true,
				)
			}
		}

		// Derive effective genres so interactive TASTE PROFILE chips work even for brand-new users
		val effectiveGenres = if (profileGenres.isNotEmpty()) {
			profileGenres
		} else {
			(sectionsOut.asSequence().flatMap { it.items.asSequence() } + gridItems.asSequence())
				.flatMap { it.matchedGenres.asSequence() }
				.filter { it.isNotEmpty() }
				.groupingBy { it }
				.eachCount()
				.entries
				.sortedByDescending { it.value }
				.map { it.key }
				.take(6)
		}

		val previewList = (sectionsOut.asSequence().flatMap { it.items.asSequence() } + gridItems.asSequence())
			.distinctBy { it.manga.id }
			.take(16)
			.toList()

		DiscoveryFeedResult(
			previewItems = previewList,
			sections = sectionsOut,
			discoveryGridItems = gridItems,
			matchedGenres = effectiveGenres,
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
				).take(20)
			}.getOrDefault(emptyList())

			if (list.isNotEmpty()) {
				results += genreTitle to list
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
		).take(16)
	}.getOrDefault(emptyList())

	private suspend fun fetchExtendedDiscoveryFromSource(source: MangaSource): List<Manga> = coroutineScope {
		runCatchingCancellable {
			val repo = mangaRepositoryFactoryProvider.get().create(source)
			val primaryOrder = when {
				SortOrder.POPULARITY in repo.sortOrders -> SortOrder.POPULARITY
				SortOrder.POPULARITY_WEEK in repo.sortOrders -> SortOrder.POPULARITY_WEEK
				SortOrder.RATING in repo.sortOrders -> SortOrder.RATING
				SortOrder.UPDATED in repo.sortOrders -> SortOrder.UPDATED
				else -> repo.defaultSortOrder
			}
			val secondaryOrder = when {
				SortOrder.RATING in repo.sortOrders && primaryOrder != SortOrder.RATING -> SortOrder.RATING
				SortOrder.UPDATED in repo.sortOrders && primaryOrder != SortOrder.UPDATED -> SortOrder.UPDATED
				SortOrder.NEWEST in repo.sortOrders && primaryOrder != SortOrder.NEWEST -> SortOrder.NEWEST
				else -> null
			}

			val primaryJob = async {
				runCatchingCancellable {
					repo.getList(offset = 0, order = primaryOrder, filter = MangaListFilter()).take(24)
				}.getOrDefault(emptyList())
			}
			val secondaryJob = secondaryOrder?.let { ord ->
				async {
					runCatchingCancellable {
						repo.getList(offset = 0, order = ord, filter = MangaListFilter()).take(18)
					}.getOrDefault(emptyList())
				}
			}

			val primary = primaryJob.await()
			val secondary = secondaryJob?.await().orEmpty()
			interleaveLists(listOf(primary, secondary)).distinctBy { it.id }
		}.getOrDefault(emptyList())
	}

	private fun extractSingleMangaKeyword(manga: Manga): String? {
		return manga.title
			.split(Regex("[^A-Za-z0-9']+"))
			.map { it.trim('\'') }
			.firstOrNull { word ->
				word.length >= 5 && word.lowercase(Locale.ROOT) !in TITLE_STOP_WORDS
			}
	}

	private fun shortenSeriesTitle(rawTitle: String): String {
		val cleaned = rawTitle.trim().replace(Regex("\\s+"), " ")
		if (cleaned.length <= 30) return cleaned
		val cut = cleaned.take(29)
		val lastSpace = cut.lastIndexOf(' ')
		return if (lastSpace >= 14) {
			"${cut.substring(0, lastSpace)}…"
		} else {
			"$cut…"
		}
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
