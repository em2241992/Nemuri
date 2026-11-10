package org.koitharu.kotatsu.tracker.work

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TrackEntity
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class SmartFeedSyncResult(
	val totalLibraryCount: Int,
	val skippedByCadenceCount: Int,
	val mangaDexBatchesPolled: Int,
	val updatedMangaCount: Int,
	val newChaptersInserted: Int,
)

@Singleton
class SmartFeedSyncCoordinator @Inject constructor(
	private val db: MangaDatabase,
	private val repositoryFactory: MangaRepository.Factory = MangaRepository.Factory(),
	private val loaderContext: MangaLoaderContext = MangaLoaderContext(),
) {
	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private val syncDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(6)
	private val hostSemaphores = ConcurrentHashMap<String, Semaphore>()

	private fun semaphoreForHost(sourceName: String): Semaphore =
		hostSemaphores.computeIfAbsent(sourceName) { Semaphore(permits = 2) }

	/**
	 * Determines whether a library manga should be skipped during routine background polling
	 * (`isManualForceRefresh == false`) based on Smart Cadence rules:
	 * 1. `manga.state == FINISHED || manga.state == ABANDONED`
	 * 2. `fastView.unreadCount >= 15` AND `(now - fastView.lastPolledAt) < 24.hours`
	 * 3. `fastView.avgReleaseIntervalDays >= 25` AND `(now - fastView.latestChapterDate) < 20.days`
	 */
	fun shouldSkipBySmartCadence(
		manga: MangaEntity,
		fastView: LibraryFastViewEntity?,
		nowMillis: Long,
		isManualForceRefresh: Boolean,
	): Boolean {
		if (isManualForceRefresh) return false

		val stateUpper = manga.state?.uppercase().orEmpty()
		if (stateUpper == MangaState.FINISHED.name || stateUpper == MangaState.ABANDONED.name) {
			return true
		}

		if (fastView != null) {
			if (fastView.unreadCount >= 15 &&
				fastView.lastPolledAt > 0L &&
				(nowMillis - fastView.lastPolledAt) < 24.hours.inWholeMilliseconds
			) {
				return true
			}

			if (fastView.avgReleaseIntervalDays >= 25 &&
				fastView.latestChapterDate > 0L &&
				(nowMillis - fastView.latestChapterDate) < 20.days.inWholeMilliseconds
			) {
				return true
			}
		}

		return false
	}

	/**
	 * Executes a Smart Cadence Feed Sync across the library:
	 * - Filters out completed/abandoned, high-unread, and slow-cadence series unless `isManualForceRefresh == true`.
	 * - Polls MangaDex series in batched chunks of 100 UUIDs per request.
	 * - Polls non-MangaDex sources with bounded per-host concurrency (`Semaphore(2)` inside `limitedParallelism(6)`).
	 * - Commits discovered chapters and updates `library_fast_view` in chunked `@Transaction` blocks of 25 manga.
	 */
	suspend fun syncLibraryFeed(
		isManualForceRefresh: Boolean = false,
		configuredLanguages: List<String> = listOf("en"),
		nowMillis: Long = System.currentTimeMillis(),
	): SmartFeedSyncResult = withContext(syncDispatcher) {
		val allMangaWithTags = db.getMangaDao().getAllWithTags()
		val fastViewsById = db.getAuxiliaryDao().observeAllFastViews().first().associateBy { it.mangaId }
		val mergeLinks = db.getAuxiliaryDao().observeAllMergeLinks().first()

		val eligibleManga = ArrayList<MangaEntity>(allMangaWithTags.size)
		var skippedCount = 0

		for (mwt in allMangaWithTags) {
			val manga = mwt.manga
			val fastView = fastViewsById[manga.id]
			if (shouldSkipBySmartCadence(manga, fastView, nowMillis, isManualForceRefresh)) {
				skippedCount++
			} else {
				eligibleManga.add(manga)
			}
		}

		// Include linked secondary merge children whose parent is eligible
		val eligibleIds = eligibleManga.map { it.id }.toSet()
		val allMangaById = allMangaWithTags.associate { it.manga.id to it.manga }
		for (link in mergeLinks) {
			if (link.parentMangaId in eligibleIds) {
				val child = allMangaById[link.childMangaId]
				if (child != null && child.id !in eligibleIds) {
					eligibleManga.add(child)
				}
			}
		}

		// Partition into explicit MangaDex UUID entries vs all extension sources
		val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
		val (mangaDexList, otherSourcesList) = eligibleManga.partition { entity ->
			entity.source.contains("MANGADEX", ignoreCase = true) &&
				uuidRegex.matches(extractMangaDexUuid(entity))
		}

		val discoveredUpdates = ConcurrentHashMap<Long, List<MangaChapter>>()
		var mangaDexBatches = 0

		// 2. MangaDex Batch Polling (100 UUIDs per Request) when a real MangaDex extension entry is in library
		if (mangaDexList.isNotEmpty()) {
			val chunks = mangaDexList.chunked(100)
			mangaDexBatches = chunks.size
			for (chunk in chunks) {
				val updatedIdsInBatch = pollMangaDexBatch100(chunk, fastViewsById, configuredLanguages)
				val subsetToFetch = chunk.filter { it.id in updatedIdsInBatch }
				coroutineScope {
					subsetToFetch.map { mangaEntity ->
						async {
							val sem = semaphoreForHost("MANGADEX")
							sem.withPermit {
								runCatching {
									val domainManga = mangaEntity.toDomainManga()
									val repo = repositoryFactory.createAsync(domainManga.source)
									val details = repo.getDetails(domainManga)
									val chapters = details.chapters.orEmpty()
									if (chapters.isNotEmpty()) {
										discoveredUpdates[mangaEntity.id] = chapters
									}
								}
							}
						}
					}.awaitAll()
				}
			}
		}

		// 3. Bounded Per-Host Concurrency for Mihon, LNReader & Kotatsu Extension Sources
		val groupedBySource = otherSourcesList.groupBy { it.source }
		coroutineScope {
			groupedBySource.flatMap { (sourceName, mangaGroup) ->
				val hostSem = semaphoreForHost(sourceName)
				mangaGroup.map { mangaEntity ->
					async {
						hostSem.withPermit {
							runCatching {
								val domainManga = mangaEntity.toDomainManga()
								val repo = repositoryFactory.createAsync(domainManga.source)
								val details = repo.getDetails(domainManga)
								val chapters = details.chapters.orEmpty()
								if (chapters.isNotEmpty()) {
									discoveredUpdates[mangaEntity.id] = chapters
								}
							}
						}
					}
				}
			}.awaitAll()
		}

		// 4. Commit discovered chapters & update library_fast_view in chunked @Transaction blocks of 25 manga
		var updatedMangaCount = 0
		var newChaptersCount = 0

		for (batch25 in eligibleManga.chunked(25)) {
			db.withTransaction {
				for (mangaEntity in batch25) {
					val existingChapters = db.getChaptersDao().findAll(mangaEntity.id)
					val fetchedChapters = discoveredUpdates[mangaEntity.id]

					val mergedChapters: List<ChapterEntity> = if (!fetchedChapters.isNullOrEmpty()) {
						val existingById = existingChapters.associateBy { it.chapterId }
						val converted = fetchedChapters.mapIndexed { idx, ch ->
							val prev = existingById[ch.id]
							if (prev == null) newChaptersCount++
							ChapterEntity(
								chapterId = ch.id,
								mangaId = mangaEntity.id,
								title = ch.name,
								number = ch.number,
								volume = ch.volume,
								url = ch.url,
								scanlator = ch.scanlator,
								uploadDate = if (ch.uploadDate > 0L) ch.uploadDate else nowMillis,
								branch = ch.branch,
								index = idx,
								isRead = prev?.isRead ?: false,
								isDownloaded = prev?.isDownloaded ?: false,
								lastPageRead = prev?.lastPageRead ?: 0,
							)
						}
						db.getChaptersDao().upsertAll(converted)
						updatedMangaCount++
						converted
					} else {
						existingChapters
					}

					val totalCh = mergedChapters.size
					val readCh = mergedChapters.count { it.isRead }
					val unreadCh = (totalCh - readCh).coerceAtLeast(0)
					val latestDate = mergedChapters.maxOfOrNull { it.uploadDate } ?: 0L
					val prevFastView = fastViewsById[mangaEntity.id]
					val rollingIntervalDays = computeRollingReleaseIntervalDays(
						chapters = mergedChapters,
						fallbackDays = prevFastView?.avgReleaseIntervalDays ?: 7,
					)

					db.getAuxiliaryDao().upsertFastView(
						LibraryFastViewEntity(
							mangaId = mangaEntity.id,
							totalChapters = totalCh,
							readChapters = readCh,
							unreadCount = unreadCh,
							lastReadAt = prevFastView?.lastReadAt ?: 0L,
							latestChapterDate = latestDate,
							avgReleaseIntervalDays = rollingIntervalDays,
							lastPolledAt = nowMillis,
						),
					)

					if (totalCh > 0) {
						db.getAuxiliaryDao().upsertTrack(
							TrackEntity(
								mangaId = mangaEntity.id,
								totalChapters = totalCh,
								lastChapterId = mergedChapters.last().chapterId,
								newChapters = unreadCh,
								lastCheckTime = nowMillis,
								lastChapterDate = latestDate,
							),
						)
					}
				}
			}
		}

		SmartFeedSyncResult(
			totalLibraryCount = allMangaWithTags.size,
			skippedByCadenceCount = skippedCount,
			mangaDexBatchesPolled = mangaDexBatches,
			updatedMangaCount = updatedMangaCount,
			newChaptersInserted = newChaptersCount,
		)
	}

	/**
	 * Queries MangaDex `/chapter?limit=100&manga[]=...&Order[publishAt]=desc` for a batch of up to 100 manga
	 * and returns the subset of `mangaId`s that have chapters newer than `latest_chapter_date`.
	 */
	private fun pollMangaDexBatch100(
		batch: List<MangaEntity>,
		fastViewsById: Map<Long, LibraryFastViewEntity>,
		configuredLanguages: List<String>,
	): Set<Long> {
		val uuidToManga = batch.associateBy { extractMangaDexUuid(it) }
		val queryParams = buildString {
			append("limit=100")
			for (uuid in uuidToManga.keys) {
				append("&manga[]=").append(uuid)
			}
			for (lang in configuredLanguages) {
				append("&translatedLanguage[]=").append(lang)
			}
			append("&order[publishAt]=desc")
		}
		val url = "https://api.mangadex.org/chapter?$queryParams"
		val updatedIds = HashSet<Long>()

		runCatching {
			val request = Request.Builder()
				.url(url)
				.header("User-Agent", "Nemuri/1.2 (Android; Batch-Sync)")
				.get()
				.build()
			loaderContext.httpClient.newCall(request).execute().use { response ->
				if (response.isSuccessful) {
					val body = response.body?.string().orEmpty()
					val root = JSONObject(body)
					val dataArr = root.optJSONArray("data")
					if (dataArr != null) {
						for (i in 0 until dataArr.length()) {
							val item = dataArr.optJSONObject(i) ?: continue
							val rels = item.optJSONArray("relationships") ?: continue
							for (r in 0 until rels.length()) {
								val rel = rels.optJSONObject(r) ?: continue
								if (rel.optString("type") == "manga") {
									val mdId = rel.optString("id")
									val matched = uuidToManga[mdId]
									if (matched != null) {
										updatedIds.add(matched.id)
									}
								}
							}
						}
					}
				}
			}
		}

		// Also include any series in the batch that already has newer chapters registered in its repository
		for (m in batch) {
			val fv = fastViewsById[m.id]
			if (fv == null || fv.lastPolledAt == 0L) {
				updatedIds.add(m.id)
			}
		}
		return updatedIds
	}

	private fun extractMangaDexUuid(manga: MangaEntity): String {
		val canonical = manga.canonicalId
		if (!canonical.isNullOrBlank() && canonical.startsWith("md:")) {
			return canonical.removePrefix("md:")
		}
		return manga.url.substringAfterLast('/').ifBlank { manga.id.toString() }
	}

	private fun computeRollingReleaseIntervalDays(
		chapters: List<ChapterEntity>,
		fallbackDays: Int,
	): Int {
		val sortedDates = chapters.map { it.uploadDate }.filter { it > 0L }.sorted()
		if (sortedDates.size < 2) return fallbackDays
		val recentDates = sortedDates.takeLast(5)
		val spanMillis = (recentDates.last() - recentDates.first()).coerceAtLeast(0L)
		val intervals = (recentDates.size - 1).coerceAtLeast(1)
		val avgMillis = spanMillis / intervals
		val avgDays = (avgMillis / 86_400_000L).toInt()
		return if (avgDays > 0) avgDays else fallbackDays
	}

	private fun MangaEntity.toDomainManga(): Manga = Manga(
		id = id,
		title = title,
		altTitle = altTitles,
		url = url,
		publicUrl = publicUrl,
		rating = rating,
		isNsfw = isNsfw,
		coverUrl = coverUrl,
		largeCoverUrl = largeCoverUrl,
		description = description,
		author = authors,
		state = runCatching { MangaState.valueOf(state?.uppercase().orEmpty()) }.getOrDefault(MangaState.ONGOING),
		source = MangaSource(source),
	)
}
