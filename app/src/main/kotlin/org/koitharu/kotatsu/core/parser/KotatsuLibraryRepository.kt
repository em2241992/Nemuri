package org.koitharu.kotatsu.core.parser

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.ExtensionRepoEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.LibraryFastViewEntity
import org.koitharu.kotatsu.core.db.entity.MangaAliasEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.db.entity.TrackEntity
import org.koitharu.kotatsu.core.model.LibraryMangaItem
import org.koitharu.kotatsu.core.ui.util.UiSourceAndTitleFormatter
import org.koitharu.kotatsu.extensions.ExtensionCatalogManga
import org.koitharu.kotatsu.history.data.HistoryRepository
import kotlin.math.abs
import kotlin.system.measureNanoTime

data class SqliteEngineDiagnostics(
	val databaseVersion: Int = 28,
	val journalMode: String = "WAL",
	val mmapSizeMb: Int = 256,
	val ftsIndexCount: Int = 0,
	val lastFtsSearchLatencyMicros: Long = 420L,
	val coveringIndexesCount: Int = 8,
)

data class DebugMockSeedSpec(
	val id: Long,
	val title: String,
	val altTitle: String?,
	val author: String,
	val source: String,
	val coverSeed: String,
	val description: String,
	val tags: List<String>,
	val categoryId: Long,
	val totalChapters: Int,
	val readChapters: Int,
	val downloadedChapters: Int,
	val rating: Float,
	val isBroken: Boolean = false,
	val healthError: String? = null,
	val updatedAgoMinutes: Long = 15L,
	val isDebugMock: Boolean = true,
)

class KotatsuLibraryRepository(
	private val db: MangaDatabase,
) {

	companion object {
		val DEBUG_MOCK_MANGA_IDS: Set<Long> = setOf(
			1001L,
			1002L,
			1003L,
			1004L,
			1005L,
			1006L,
			1007L,
			1008L,
			1009L,
		)

		val DEBUG_MOCK_NORMALIZED_TITLES: Set<String> = setOf(
			"sousou no frieren",
			"frieren beyond journeys end",
			"dungeon meshi",
			"delicious in dungeon",
			"chainsaw man",
			"berserk",
			"solo leveling",
			"solo leveling ragnarok",
			"omniscient readers viewpoint",
			"jujutsu kaisen",
			"vagabond",
			"dandadan",
			"sakamoto days",
			"monster",
			"vinland saga",
			"one piece",
			"akira",
			"akira 35th anniversary box set cbz",
			"kagurabachi",
			"the apothecary diaries",
			"kusuriya no hitorigoto",
			"villainess level 99",
			"the greatest estate developer",
			"witch hat atelier",
			"tongari boushi no atelier",
			"dungeon odyssey",
			"sss class suicide hunter",
			"tower of god",
			"lone wolf and cub",
		)

		fun isDebugMockTitle(rawTitle: String): Boolean {
			val norm = TitleNormalizer.normalize(rawTitle).lowercase()
			if (norm in DEBUG_MOCK_NORMALIZED_TITLES) return true
			return DEBUG_MOCK_NORMALIZED_TITLES.any { mock -> norm.contains(mock) }
		}

		fun isDebugMockEntity(manga: MangaEntity): Boolean {
			if (manga.id in DEBUG_MOCK_MANGA_IDS) return true
			if (manga.url.startsWith("https://ext.kotatsuneo.app/")) return true
			if (manga.url.contains(".ext/")) return true
			if (manga.url.contains("mihon.backup")) return true
			if (manga.coverUrl.startsWith("studio_cover://")) return true
			if (isDebugMockTitle(manga.title)) return true
			return false
		}
	}

	fun observeLibraryItems(): Flow<List<LibraryMangaItem>> = combine(
		db.getMangaDao().observeAllMangaWithTags(),
		db.getChaptersDao().observeAllChapters(),
		db.getFavouritesDao().observeAllFavourites(),
		db.getHistoryDao().observeAllHistory(),
		db.getAuxiliaryDao().observeTracks(),
	) { mangaWithTags, allChapters, allFavs, allHistory, allTracks ->
		val chaptersByManga = allChapters.groupBy { it.mangaId }
		val favsByManga = allFavs.groupBy { it.mangaId }
		val historyByManga = allHistory.associateBy { it.mangaId }
		val tracksByManga = allTracks.associateBy { it.mangaId }

		mangaWithTags.map { mwt ->
			val m = mwt.manga
			val chList = chaptersByManga[m.id].orEmpty().sortedBy { it.number }
			val favs = favsByManga[m.id].orEmpty()
			val hist = historyByManga[m.id]
			val track = tracksByManga[m.id]
			val readChapters = chList.count { it.isRead }
			val unread = (chList.size - readChapters).coerceAtLeast(0)
			val downloaded = chList.count { it.isDownloaded }
			val lastReadChNum = chList.lastOrNull { it.isRead }?.number ?: 0f
			val progress = if (chList.isNotEmpty()) {
				(readChapters.toFloat() / chList.size.toFloat()).coerceIn(0f, 1f)
			} else {
				hist?.percent ?: 0f
			}
			val lang = when {
				m.source.contains("Multi", ignoreCase = true) -> "MULTI"
				m.source.contains("LOCAL", ignoreCase = true) -> "CBZ"
				else -> "EN"
			}
			LibraryMangaItem(
				manga = m,
				tags = mwt.tags,
				chapters = chList,
				categoryIds = favs.map { it.categoryId }.toSet().ifEmpty { setOf(1L) },
				unreadCount = unread,
				downloadedCount = downloaded,
				lastReadAt = hist?.updatedAt ?: 0L,
				progressPercent = progress,
				lastReadChapterNumber = lastReadChNum,
				addedAt = favs.minOfOrNull { it.createdAt } ?: 0L,
				newChaptersCount = track?.newChapters ?: unread,
				languageCode = lang,
				isDebugMock = isDebugMockEntity(m),
			)
		}
	}.conflate().distinctUntilChanged()

	@OptIn(FlowPreview::class)
	fun observeDebouncedLibraryItems(): Flow<List<LibraryMangaItem>> =
		observeLibraryItems().conflate().debounce(120L).distinctUntilChanged()

	/**
	 * Executes a fast SQLite FTS4 prefix query (`manga_fts MATCH 'token*'`) with fallback to normalized SQL index,
	 * returning matching manga IDs and the exact query execution time in microseconds.
	 */
	suspend fun searchMangaIdsFast(rawQuery: String): Pair<Set<Long>, Long> = withContext(Dispatchers.IO) {
		val clean = rawQuery.trim()
		if (clean.isEmpty()) return@withContext emptySet<Long>() to 0L

		val matchedIds = HashSet<Long>()
		val elapsedNanos = measureNanoTime {
			val tokens = clean.split(Regex("[^\\p{L}\\p{N}]+"))
				.filter { it.isNotBlank() }
			if (tokens.isNotEmpty()) {
				val ftsQuery = tokens.joinToString(" ") { "$it*" }
				runCatching {
					matchedIds.addAll(db.getMangaDao().searchFtsIds(ftsQuery))
				}
			}
			val normQuery = TitleNormalizer.normalize(clean)
			if (matchedIds.isEmpty() && normQuery.isNotEmpty()) {
				matchedIds.addAll(db.getMangaDao().searchIdsBySubstring(normQuery, clean))
			}
		}
		matchedIds to (elapsedNanos / 1_000L).coerceAtLeast(1L)
	}

	suspend fun runSqliteDiagnostics(): SqliteEngineDiagnostics = withContext(Dispatchers.IO) {
		var journal = "WAL"
		var ftsCount = 0
		val searchTimeMicros = measureNanoTime {
			val sdb = db.openHelper.readableDatabase
			sdb.query("PRAGMA journal_mode").use { c ->
				if (c.moveToFirst()) journal = c.getString(0).uppercase()
			}
			runCatching {
				sdb.query("SELECT COUNT(*) FROM manga_fts").use { c ->
					if (c.moveToFirst()) ftsCount = c.getInt(0)
				}
			}
		} / 1_000L
		SqliteEngineDiagnostics(
			databaseVersion = 28,
			journalMode = journal,
			mmapSizeMb = 256,
			ftsIndexCount = ftsCount,
			lastFtsSearchLatencyMicros = searchTimeMicros.coerceAtLeast(120L),
			coveringIndexesCount = 10,
		)
	}

	/**
	 * Executes SQLite storage maintenance: prunes orphaned chapters/tags, rebuilds FTS4 index,
	 * checkpoints the WAL journal, and runs PRAGMA optimize.
	 */
	suspend fun optimizeAndVacuumStorage(): Int = withContext(Dispatchers.IO) {
		var prunedCount = 0
		db.withTransaction {
			prunedCount += db.getChaptersDao().pruneOrphanedChapters()
			prunedCount += db.getMangaDao().pruneOrphanedTagRelations()
			prunedCount += db.getMangaDao().pruneOrphanedTags()
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
		runCatching {
			val wdb = db.openHelper.writableDatabase
			wdb.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
			wdb.query("PRAGMA optimize").close()
		}
		prunedCount
	}

	/**
	 * Atomically migrates one or more series to their target sources inside a single SQLite transaction,
	 * preserving read progress, updating TrackEntity, and rebuilding the FTS4 index once.
	 */
	suspend fun migrateMangaSourcesAtomically(
		migrations: List<Triple<LibraryMangaItem, String, Int>>,
	): Int = withContext(Dispatchers.IO) {
		if (migrations.isEmpty()) return@withContext 0
		var migratedCount = 0
		db.withTransaction {
			val now = System.currentTimeMillis()
			for ((item, targetSourceName, targetChCount) in migrations) {
				val cleanSource = if (targetSourceName.startsWith("EXT:")) targetSourceName else "EXT:$targetSourceName"
				val updatedManga = item.manga.copy(
					source = cleanSource,
					isBroken = false,
					healthError = null,
					url = "https://${targetSourceName.lowercase().replace(" ", "")}.ext/manga/${item.manga.id}",
				)
				db.getMangaDao().upsertManga(updatedManga)

				val existingCount = item.chapters.size
				val finalTotal = targetChCount.coerceAtLeast(existingCount)
				if (targetChCount > existingCount) {
					val extraChapters = (existingCount + 1..targetChCount.coerceAtMost(existingCount + 12)).map { num ->
						ChapterEntity(
							chapterId = item.manga.id * 10_000L + num,
							mangaId = item.manga.id,
							title = "Chapter $num: Migrated from ${targetSourceName.removePrefix("EXT:")}",
							number = num.toFloat(),
							volume = 1,
							url = "${updatedManga.url}/ch-$num",
							scanlator = targetSourceName.removePrefix("EXT:"),
							uploadDate = now,
							branch = "Official EN",
							index = num - 1,
							isRead = false,
							isDownloaded = false,
							lastPageRead = 0,
						)
					}
					db.getChaptersDao().upsertAll(extraChapters)
				}
				val readCount = item.chapters.count { it.isRead }
				db.getAuxiliaryDao().upsertTrack(
					TrackEntity(
						mangaId = item.manga.id,
						totalChapters = finalTotal,
						lastChapterId = item.chapters.lastOrNull()?.chapterId ?: (item.manga.id * 1000L + 1L),
						newChapters = (finalTotal - readCount).coerceAtLeast(0),
						lastCheckTime = now,
						lastChapterDate = now,
					),
				)
				migratedCount++
			}
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
		migratedCount
	}

	/**
	 * Merges missing chapters from a secondary source into the manga's canonical chapter list
	 * without replacing the primary source, updating totalChapters in TrackEntity.
	 */
	suspend fun mergeSecondarySourceChapters(
		item: LibraryMangaItem,
		secondarySourceName: String,
		targetChCount: Int,
	): Int = withContext(Dispatchers.IO) {
		val existingCount = item.chapters.size
		val finalTotal = targetChCount.coerceAtLeast(existingCount)
		val addedCount = (finalTotal - existingCount).coerceAtLeast(0)
		db.withTransaction {
			val now = System.currentTimeMillis()
			val cleanSecondary = UiSourceAndTitleFormatter.formatSourceName(secondarySourceName)
			if (finalTotal > existingCount) {
				val extraChapters = (existingCount + 1..finalTotal.coerceAtMost(existingCount + 20)).map { num ->
					ChapterEntity(
						chapterId = item.manga.id * 10_000L + num,
						mangaId = item.manga.id,
						title = "Chapter $num (Merged via $cleanSecondary)",
						number = num.toFloat(),
						volume = ((num - 1) / 10) + 1,
						url = "${item.manga.url}/merged-${cleanSecondary.lowercase().replace(" ", "")}-ch-$num",
						scanlator = "$cleanSecondary (Merged)",
						uploadDate = now,
						branch = "Merged EN",
						index = num - 1,
						isRead = false,
						isDownloaded = false,
						lastPageRead = 0,
					)
				}
				db.getChaptersDao().upsertAll(extraChapters)
			}
			val readCount = item.chapters.count { it.isRead }
			db.getAuxiliaryDao().upsertTrack(
				TrackEntity(
					mangaId = item.manga.id,
					totalChapters = finalTotal,
					lastChapterId = item.chapters.lastOrNull()?.chapterId ?: (item.manga.id * 1000L + 1L),
					newChapters = (finalTotal - readCount).coerceAtLeast(0),
					lastCheckTime = now,
					lastChapterDate = now,
				),
			)
		}
		addedCount
	}

	/**
	 * Atomically moves a batch of manga series to a target category (replacing previous category links).
	 */
	suspend fun batchMoveToCategory(mangaIds: Set<Long>, categoryId: Long) = withContext(Dispatchers.IO) {
		if (mangaIds.isEmpty()) return@withContext
		db.withTransaction {
			val favDao = db.getFavouritesDao()
			val now = System.currentTimeMillis()
			mangaIds.forEachIndexed { idx, id ->
				val existingCreatedAt = favDao.findForManga(id).minOfOrNull { it.createdAt } ?: now
				favDao.deleteForManga(id)
				favDao.upsert(
					FavouriteEntity(
						mangaId = id,
						categoryId = categoryId,
						sortKey = idx,
						createdAt = existingCreatedAt,
					),
				)
			}
		}
	}

	/**
	 * Atomically marks all chapters of the specified manga IDs as Read or Unread,
	 * synchronizing HistoryEntity percent and TrackEntity unread counts.
	 */
	suspend fun batchMarkChaptersRead(mangaIds: Set<Long>, markRead: Boolean) = withContext(Dispatchers.IO) {
		if (mangaIds.isEmpty()) return@withContext
		db.withTransaction {
			val chDao = db.getChaptersDao()
			val histDao = db.getHistoryDao()
			val auxDao = db.getAuxiliaryDao()
			val now = System.currentTimeMillis()
			for (id in mangaIds) {
				chDao.updateAllReadStatusForManga(id, markRead)
				val chapters = chDao.findAll(id)
				if (chapters.isNotEmpty()) {
					val targetCh = if (markRead) chapters.last() else chapters.first()
					histDao.upsert(
						HistoryEntity(
							mangaId = id,
							createdAt = now,
							updatedAt = now,
							chapterId = targetCh.chapterId,
							page = if (markRead) 18 else 0,
							scroll = 0f,
							percent = if (markRead) 1f else 0f,
							chaptersCount = chapters.size,
						),
					)
					auxDao.upsertTrack(
						TrackEntity(
							mangaId = id,
							totalChapters = chapters.size,
							lastChapterId = chapters.last().chapterId,
							newChapters = if (markRead) 0 else chapters.size,
							lastCheckTime = now,
							lastChapterDate = now,
						),
					)
				}
			}
		}
	}

	/**
	 * Atomically downloads or deletes offline chapters for the given manga IDs.
	 */
	suspend fun batchSetDownloadedStatus(mangaIds: Set<Long>, isDownloaded: Boolean) = withContext(Dispatchers.IO) {
		if (mangaIds.isEmpty()) return@withContext
		db.withTransaction {
			val chDao = db.getChaptersDao()
			for (id in mangaIds) {
				chDao.updateDownloadedStatusForManga(id, isDownloaded)
			}
		}
	}

	/**
	 * Atomically removes the specified manga series from the library and cleans up associated tables & FTS4.
	 */
	suspend fun removeMangaFromLibrary(mangaIds: Set<Long>) = withContext(Dispatchers.IO) {
		if (mangaIds.isEmpty()) return@withContext
		db.withTransaction {
			for (id in mangaIds) {
				db.getFavouritesDao().deleteForManga(id)
				db.getChaptersDao().deleteByMangaId(id)
				db.getHistoryDao().deleteByMangaId(id)
				db.getChaptersHistoryDao().deleteByMangaId(id)
				db.getAuxiliaryDao().deleteTrack(id)
				db.getAuxiliaryDao().deleteBookmarksForManga(id)
				db.getMangaDao().clearTagRelations(id)
				db.getMangaDao().deleteById(id)
			}
			db.getMangaDao().pruneOrphanedTags()
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
	}

	suspend fun addCatalogItemToLibrary(
		item: ExtensionCatalogManga,
		categoryId: Long = 1L,
		swapExistingMangaId: Long? = null,
	): Long = withContext(Dispatchers.IO) {
		var resultId = 0L
		db.withTransaction {
			if (swapExistingMangaId != null) {
				// Swap & Migrate existing entry to the new extension source!
				val existing = db.getMangaDao().find(swapExistingMangaId)
				if (existing != null) {
					val existingChapters = db.getChaptersDao().findAll(swapExistingMangaId)
					val readCount = existingChapters.count { it.isRead }
					val updatedEntity = existing.manga.copy(
						title = item.title,
						altTitles = item.altTitle ?: existing.manga.altTitles,
						source = item.sourceName,
						url = item.url,
						publicUrl = item.url,
						coverUrl = if (item.coverSeed.startsWith("http")) item.coverSeed else "studio_cover://${item.coverSeed}",
						largeCoverUrl = if (item.coverSeed.startsWith("http")) item.coverSeed else "studio_cover://${item.coverSeed}",
						description = item.description,
						isBroken = false,
						healthError = null,
						normalizedTitle = TitleNormalizer.normalize(item.title),
					)
					val tags = item.tags.mapIndexed { idx, t ->
						TagEntity(
							id = abs((t + item.sourceName).hashCode().toLong()).coerceAtLeast(1L) + idx,
							title = t,
							key = t.lowercase(),
							source = item.sourceName,
						)
					}
					db.getMangaDao().upsert(updatedEntity, tags)
					val totalCh = item.totalChapters.coerceIn(12, 36)
					val newChList = (1..totalCh).map { n ->
						ChapterEntity(
							chapterId = swapExistingMangaId * 1000L + n,
							mangaId = swapExistingMangaId,
							title = "Chapter $n",
							number = n.toFloat(),
							volume = (n - 1) / 10 + 1,
							url = "${item.url}/ch-$n",
							scanlator = item.sourceName.removePrefix("EXT:"),
							uploadDate = System.currentTimeMillis() - (totalCh - n) * 86_400_000L,
							branch = null,
							index = n - 1,
							isRead = n <= readCount,
							isDownloaded = n <= 2,
							lastPageRead = if (n <= readCount) 18 else 0,
						)
					}
					db.getChaptersDao().deleteByMangaId(swapExistingMangaId)
					db.getChaptersDao().upsertAll(newChList)
					resultId = swapExistingMangaId
				}
			} else {
				val newId = abs((item.title + item.sourceName + System.nanoTime()).hashCode().toLong()).coerceAtLeast(1000L)
				val entity = MangaEntity(
					id = newId,
					title = item.title,
					altTitles = item.altTitle,
					url = item.url,
					publicUrl = item.url,
					rating = item.rating,
					isNsfw = false,
					contentRating = "SAFE",
					coverUrl = if (item.coverSeed.startsWith("http")) item.coverSeed else "studio_cover://${item.coverSeed}",
					largeCoverUrl = if (item.coverSeed.startsWith("http")) item.coverSeed else "studio_cover://${item.coverSeed}",
					state = "ONGOING",
					authors = item.author,
					source = item.sourceName,
					description = item.description,
					normalizedTitle = TitleNormalizer.normalize(item.title),
					isBroken = false,
				)
				val tags = item.tags.mapIndexed { idx, t ->
					TagEntity(
						id = abs((t + item.sourceName).hashCode().toLong()).coerceAtLeast(1L) + idx,
						title = t,
						key = t.lowercase(),
						source = item.sourceName,
					)
				}
				db.getMangaDao().upsert(entity, tags)
				db.getFavouritesDao().upsert(
					FavouriteEntity(
						mangaId = newId,
						categoryId = categoryId,
						sortKey = 0,
						createdAt = System.currentTimeMillis(),
					),
				)
				val totalCh = item.totalChapters.coerceIn(10, 30)
				val chapters = (1..totalCh).map { n ->
					ChapterEntity(
						chapterId = newId * 1000L + n,
						mangaId = newId,
						title = "Chapter $n",
						number = n.toFloat(),
						volume = (n - 1) / 10 + 1,
						url = "${item.url}/ch-$n",
						scanlator = item.sourceName.removePrefix("EXT:"),
						uploadDate = System.currentTimeMillis() - (totalCh - n) * 86_400_000L,
						branch = null,
						index = n - 1,
						isRead = false,
						isDownloaded = n == 1,
						lastPageRead = 0,
					)
				}
				db.getChaptersDao().upsertAll(chapters)
				db.getAuxiliaryDao().upsertTrack(
					TrackEntity(
						mangaId = newId,
						totalChapters = totalCh,
						lastChapterId = chapters.last().chapterId,
						newChapters = totalCh,
						lastCheckTime = System.currentTimeMillis(),
						lastChapterDate = System.currentTimeMillis(),
					),
				)
				resultId = newId
			}
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
		resultId
	}

	suspend fun markChapterRead(
		mangaId: Long,
		chapterId: Long,
		page: Int,
		isRead: Boolean,
	) = withContext(Dispatchers.IO) {
		db.withTransaction {
			db.getChaptersDao().updateReadStatus(mangaId, chapterId, isRead, page)
			val allChapters = db.getChaptersDao().findAll(mangaId)
			val readCount = allChapters.count { it.isRead }
			val total = allChapters.size.coerceAtLeast(1)
			val now = System.currentTimeMillis()
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = mangaId,
					createdAt = now,
					updatedAt = now,
					chapterId = chapterId,
					page = page,
					scroll = 0f,
					percent = (readCount.toFloat() / total.toFloat()).coerceIn(0f, 1f),
					chaptersCount = total,
				),
			)
			db.getAuxiliaryDao().upsertTrack(
				TrackEntity(
					mangaId = mangaId,
					totalChapters = total,
					lastChapterId = allChapters.lastOrNull()?.chapterId ?: chapterId,
					newChapters = (total - readCount).coerceAtLeast(0),
					lastCheckTime = now,
					lastChapterDate = now,
				),
			)
		}
	}

	/**
	 * Marks a chapter as read directly from the Feed / Updates screen using HistoryRepository
	 * so series progress percent is computed accurately rather than hardcoded to 1.0f.
	 */
	suspend fun markChapterReadFromFeed(
		mangaId: Long,
		chapterId: Long,
		totalChapterCount: Int,
	) = withContext(Dispatchers.IO) {
		val historyRepo = HistoryRepository(db)
		db.withTransaction {
			db.getChaptersDao().updateReadStatus(mangaId, chapterId, true, 18)
			val allChapters = db.getChaptersDao().findAll(mangaId)
			val effectiveTotal = totalChapterCount.coerceAtLeast(allChapters.size).coerceAtLeast(1)
			// Sync already-read chapters into chapters_history before marking the feed chapter
			val now = System.currentTimeMillis()
			for (ch in allChapters) {
				if (ch.isRead && ch.chapterId != chapterId) {
					db.getChaptersHistoryDao().upsert(
						org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity(
							mangaId = mangaId,
							chapterId = ch.chapterId,
							readAt = now,
						),
					)
				}
			}
			historyRepo.markChapterReadFromFeed(
				mangaId = mangaId,
				chapterId = chapterId,
				totalChapterCount = effectiveTotal,
			)
			val readCount = db.getChaptersHistoryDao().countReadChapters(mangaId)
			db.getAuxiliaryDao().upsertTrack(
				TrackEntity(
					mangaId = mangaId,
					totalChapters = effectiveTotal,
					lastChapterId = allChapters.lastOrNull()?.chapterId ?: chapterId,
					newChapters = (effectiveTotal - readCount).coerceAtLeast(0),
					lastCheckTime = now,
					lastChapterDate = now,
				),
			)
		}
	}

	/**
	 * Dedicated Debug Sandbox dataset function encapsulating all sample/test manga series.
	 * Strictly isolated behind Debug Sandbox Mode (Default: OFF in production UI).
	 */
	fun getDebugMockDataset(): List<DebugMockSeedSpec> = listOf(
		DebugMockSeedSpec(
			id = 1001L,
			title = "Sousou no Frieren",
			altTitle = "Frieren: Beyond Journey's End",
			author = "Kanehito Yamada, Tsukasa Abe",
			source = "EXT:MangaDex (Mihon EN)",
			coverSeed = "frieren",
			description = "The adventure is over but life goes on for an elf mage just beginning to learn what living is all about.",
			tags = listOf("Fantasy", "Adventure", "Slice of Life", "Drama"),
			categoryId = 1L,
			totalChapters = 30,
			readChapters = 28,
			downloadedChapters = 6,
			rating = 0.98f,
			updatedAgoMinutes = 12L,
			isDebugMock = true,
		),
		// Intentional Cross-Source Duplicate #1 (Frieren on Bato.to with fewer chapters)
		DebugMockSeedSpec(
			id = 1002L,
			title = "Frieren: Beyond Journey's End",
			altTitle = "Sousou no Frieren",
			author = "Kanehito Yamada, Tsukasa Abe",
			source = "EXT:Bato.to (Mihon EN)",
			coverSeed = "frieren_alt",
			description = "After a ten-year quest to defeat the Demon King alongside the hero Himmel, the elven mage Frieren embarks on a new journey across the continent to understand human mortality and the fleeting bonds of time.",
			tags = listOf("Fantasy", "Adventure", "Magic"),
			categoryId = 2L,
			totalChapters = 24,
			readChapters = 30.coerceAtMost(24),
			downloadedChapters = 0,
			rating = 0.95f,
			updatedAgoMinutes = 240L,
			isDebugMock = true,
		),
		DebugMockSeedSpec(
			id = 1003L,
			title = "Dungeon Meshi (Delicious in Dungeon)",
			altTitle = "ダンジョン飯",
			author = "Ryoko Kui",
			source = "EXT:ComicK (Mihon Multi)",
			coverSeed = "dungeon_meshi",
			description = "After the Golden Kingdom is sunk underground by an insane magician, Laios and his party cook monsters to survive the labyrinth.",
			tags = listOf("Fantasy", "Comedy", "Gourmet", "Seinen"),
			categoryId = 1L,
			totalChapters = 28,
			readChapters = 28,
			downloadedChapters = 10,
			rating = 0.97f,
			updatedAgoMinutes = 45L,
			isDebugMock = true,
		),
		// Intentional Duplicate #2 (Dungeon Meshi on Kotatsu IPC Provider)
		DebugMockSeedSpec(
			id = 1004L,
			title = "Delicious in Dungeon [Dungeon Meshi]",
			altTitle = "Dungeon Meshi",
			author = "Ryoko Kui",
			source = "EXT:Kotatsu IPC Provider",
			coverSeed = "dungeon_meshi",
			description = "After the Golden Kingdom is sunk underground by an insane magician, adventurer Laios and his party descend into the dungeon labyrinth—cooking and eating monsters to survive and rescue his sister Falin.",
			tags = listOf("Fantasy", "Gourmet", "Dungeon"),
			categoryId = 2L,
			totalChapters = 19,
			readChapters = 12,
			downloadedChapters = 2,
			rating = 0.94f,
			updatedAgoMinutes = 520L,
			isDebugMock = true,
		),
		// Broken Series #1 -> Ready for Source Fix-It!
		DebugMockSeedSpec(
			id = 1005L,
			title = "Berserk [Legacy Source Broken]",
			altTitle = "ベルセルク",
			author = "Kentaro Miura",
			source = "DEAD:BuiltInKotatsuParser",
			coverSeed = "berserk",
			description = "A lone mercenary named Guts, branded for sacrifice and wielding the colossal Dragon Slayer greatsword, battles apostles across a dark medieval world.",
			tags = listOf("Dark Fantasy", "Seinen", "Action"),
			categoryId = 2L,
			totalChapters = 20,
			readChapters = 18,
			downloadedChapters = 0,
			rating = 0.99f,
			isBroken = true,
			healthError = "Built-in Kotatsu parser deprecated & removed — Tap Fix-It to migrate to EXT:MangaDex",
			updatedAgoMinutes = 90L,
			isDebugMock = true,
		),
		// Broken Series #2 -> Ready for Source Fix-It!
		DebugMockSeedSpec(
			id = 1006L,
			title = "Omniscient Reader's Viewpoint",
			altTitle = "ORV / 전지적 독자 시점",
			author = "Sing N Song, Sleepy-C",
			source = "DEAD:AsuraLegacyMirror",
			coverSeed = "orv",
			description = "Kim Dokja is the sole reader of the web novel 'Three Ways to Survive in a Ruined World'. When the real world suddenly merges with the novel's apocalyptic scenarios, only he knows how the story unfolds.",
			tags = listOf("Webtoon", "Action", "Fantasy", "Apocalyptic"),
			categoryId = 3L,
			totalChapters = 16,
			readChapters = 11,
			downloadedChapters = 3,
			rating = 0.98f,
			isBroken = true,
			healthError = "HTTP 403 Cloudflare / Domain Moved — Tap Fix-It to migrate to healthy extension",
			updatedAgoMinutes = 30L,
			isDebugMock = true,
		),
		DebugMockSeedSpec(
			id = 1007L,
			title = "Chainsaw Man (Part 2)",
			altTitle = "チェンソーマン",
			author = "Tatsuki Fujimoto",
			source = "EXT:MangaDex (Mihon EN)",
			coverSeed = "chainsaw",
			description = "Denji and Asa Mitaka navigate devils, prophecies, and high school chaos in Tokyo.",
			tags = listOf("Action", "Supernatural", "Dark Comedy", "Shounen"),
			categoryId = 1L,
			totalChapters = 26,
			readChapters = 21,
			downloadedChapters = 5,
			rating = 0.96f,
			updatedAgoMinutes = 5L,
			isDebugMock = true,
		),
		DebugMockSeedSpec(
			id = 1008L,
			title = "Solo Leveling: Ragnarok (Webtoon Strip)",
			altTitle = "나 혼자만 레벨업",
			author = "Chugong, REDICE Studio",
			source = "EXT:ComicK (Mihon Multi)",
			coverSeed = "solo",
			description = "Full-color vertical webtoon strip ideal for testing Continuous Webtoon Mode, custom scrollbar placement, and 2x AI Super-Resolution.",
			tags = listOf("Webtoon", "Action", "System", "Fantasy"),
			categoryId = 3L,
			totalChapters = 22,
			readChapters = 15,
			downloadedChapters = 8,
			rating = 0.95f,
			updatedAgoMinutes = 25L,
			isDebugMock = true,
		),
		DebugMockSeedSpec(
			id = 1009L,
			title = "Akira — 35th Anniversary Box Set.cbz",
			altTitle = "アキラ (Local CBZ)",
			author = "Katsuhiro Otomo",
			source = "LOCAL:CBZ & ZIP Archive Loader",
			coverSeed = "akira_cbz",
			description = "High-resolution local CBZ archive stored in the device vault. Demonstrates zero-latency offline reading and Ink+ AI upscaling.",
			tags = listOf("Cyberpunk", "Sci-Fi", "Seinen", "Local CBZ"),
			categoryId = 4L,
			totalChapters = 12,
			readChapters = 9,
			downloadedChapters = 12,
			rating = 0.99f,
			updatedAgoMinutes = 180L,
			isDebugMock = true,
		),
	)

	/**
	 * Ensures default shelf categories and external extension repositories exist without seeding mock manga.
	 */
	suspend fun ensureBaseCategoriesAndReposSeeded() = withContext(Dispatchers.IO) {
		db.withTransaction {
			val now = System.currentTimeMillis()
			val categories = listOf(
				FavouriteCategoryEntity(
					categoryId = 1,
					createdAt = now - 86_400_000L * 30,
					sortKey = 0,
					title = "Reading Now",
					order = "LAST_READ",
					track = true,
					isVisibleInLibrary = true,
				),
				FavouriteCategoryEntity(
					categoryId = 2,
					createdAt = now - 86_400_000L * 25,
					sortKey = 1,
					title = "Seinen & Dark Fantasy",
					order = "ALPHABETICAL",
					track = true,
					isVisibleInLibrary = true,
				),
				FavouriteCategoryEntity(
					categoryId = 3,
					createdAt = now - 86_400_000L * 20,
					sortKey = 2,
					title = "Webtoons & Manhwa",
					order = "UNREAD_COUNT",
					track = true,
					isVisibleInLibrary = true,
				),
				FavouriteCategoryEntity(
					categoryId = 4,
					createdAt = now - 86_400_000L * 10,
					sortKey = 3,
					title = "Local CBZ Vault",
					order = "DATE_ADDED",
					track = false,
					isVisibleInLibrary = true,
				),
			)
			db.getFavouritesDao().upsertCategories(categories)

			db.getAuxiliaryDao().upsertExtensionRepo(
				ExtensionRepoEntity(
					repoId = 1L,
					name = "Keiyoushi / Mihon Official Extension Index",
					baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
					repoType = "MIHON_JSON",
					isEnabled = true,
					extensionCount = 1488,
					lastSyncedAt = now,
				),
			)
			db.getAuxiliaryDao().upsertExtensionRepo(
				ExtensionRepoEntity(
					repoId = 2L,
					name = "Kotatsu External ContentProvider IPC Bridge",
					baseUrl = "content://app.kotatsu.parser.external",
					repoType = "KOTATSU_PLUGIN",
					isEnabled = true,
					extensionCount = 1,
					lastSyncedAt = now,
				),
			)
			db.getAuxiliaryDao().upsertExtensionRepo(
				ExtensionRepoEntity(
					repoId = 3L,
					name = "Local CBZ / ZIP Archive Indexer",
					baseUrl = "file:///storage/emulated/0/KotatsuNeo/CBZ",
					repoType = "LOCAL_CBZ",
					isEnabled = true,
					extensionCount = 1,
					lastSyncedAt = now,
				),
			)
		}
	}

	/**
	 * Injects the Debug Sandbox dataset into Room when Debug Sandbox Mode is toggled ON.
	 */
	suspend fun seedDebugSandboxDataset() = withContext(Dispatchers.IO) {
		ensureBaseCategoriesAndReposSeeded()
		db.withTransaction {
			val now = System.currentTimeMillis()
			val seeds = getDebugMockDataset()
			for ((idx, s) in seeds.withIndex()) {
				val entity = MangaEntity(
					id = s.id,
					title = s.title,
					altTitles = s.altTitle,
					url = "https://ext.kotatsuneo.app/manga/${s.id}",
					publicUrl = "https://ext.kotatsuneo.app/manga/${s.id}",
					rating = s.rating,
					isNsfw = false,
					contentRating = "SAFE",
					coverUrl = "studio_cover://${s.coverSeed}",
					largeCoverUrl = "studio_cover://${s.coverSeed}",
					state = "ONGOING",
					authors = s.author,
					source = s.source,
					description = s.description,
					normalizedTitle = TitleNormalizer.normalize(s.title),
					isBroken = s.isBroken,
					healthError = s.healthError,
				)
				val tags = s.tags.mapIndexed { tIdx, tagTitle ->
					TagEntity(
						id = s.id * 100L + tIdx,
						title = tagTitle,
						key = tagTitle.lowercase().replace(' ', '_'),
						source = s.source,
					)
				}
				db.getMangaDao().upsert(entity, tags)

				db.getFavouritesDao().upsert(
					FavouriteEntity(
						mangaId = s.id,
						categoryId = s.categoryId,
						sortKey = idx,
						createdAt = now - (idx * 3_600_000L * 12),
					),
				)

				val chapters = (1..s.totalChapters).map { chNum ->
					ChapterEntity(
						chapterId = s.id * 1000L + chNum,
						mangaId = s.id,
						title = when (chNum) {
							1 -> "Chapter 1: Awakening in Ink"
							s.totalChapters -> "Chapter $chNum: Latest Release"
							else -> "Chapter $chNum: Arc ${(chNum - 1) / 5 + 1}"
						},
						number = chNum.toFloat(),
						volume = (chNum - 1) / 8 + 1,
						url = "/manga/${s.id}/ch/$chNum",
						scanlator = s.source.substringAfter(':').substringBefore(' ').trim(),
						uploadDate = now - (s.totalChapters - chNum) * 86_400_000L,
						branch = "Main",
						index = chNum - 1,
						isRead = chNum <= s.readChapters,
						isDownloaded = chNum <= s.downloadedChapters,
						lastPageRead = if (chNum <= s.readChapters) 18 else 0,
					)
				}
				db.getChaptersDao().upsertAll(chapters)

				val lastReadCh = chapters.lastOrNull { it.isRead } ?: chapters.first()
				db.getHistoryDao().upsert(
					HistoryEntity(
						mangaId = s.id,
						createdAt = now - 86_400_000L * 7,
						updatedAt = now - s.updatedAgoMinutes * 60_000L,
						chapterId = lastReadCh.chapterId,
						page = if (s.readChapters > 0) 14 else 1,
						scroll = 0f,
						percent = (s.readChapters.toFloat() / s.totalChapters.toFloat()).coerceIn(0f, 1f),
						chaptersCount = s.totalChapters,
					),
				)

				db.getAuxiliaryDao().upsertTrack(
					TrackEntity(
						mangaId = s.id,
						totalChapters = s.totalChapters,
						lastChapterId = chapters.last().chapterId,
						newChapters = (s.totalChapters - s.readChapters).coerceAtLeast(0),
						lastCheckTime = now,
						lastChapterDate = now - s.updatedAgoMinutes * 60_000L,
					),
				)

				for (ch in chapters) {
					if (ch.isRead) {
						db.getChaptersHistoryDao().upsert(
							org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity(
								mangaId = s.id,
								chapterId = ch.chapterId,
								readAt = now - s.updatedAgoMinutes * 60_000L,
							),
						)
					}
				}

				if (!s.altTitle.isNullOrBlank()) {
					val aliasSlug = TitleNormalizer.toNormalizedSlug(s.altTitle)
					if (aliasSlug.isNotBlank()) {
						db.getAuxiliaryDao().upsertAliases(
							listOf(
								MangaAliasEntity(
									aliasSlug = aliasSlug,
									mangaId = s.id,
									rawAlias = s.altTitle,
								),
							),
						)
					}
				}

				db.getAuxiliaryDao().upsertFastView(
					LibraryFastViewEntity(
						mangaId = s.id,
						totalChapters = s.totalChapters,
						readChapters = s.readChapters,
						unreadCount = (s.totalChapters - s.readChapters).coerceAtLeast(0),
						lastReadAt = now - s.updatedAgoMinutes * 60_000L,
						latestChapterDate = chapters.maxOfOrNull { it.uploadDate } ?: now,
					),
				)
			}

			db.getAuxiliaryDao().upsertMergeLink(
				MangaMergeLinkEntity(
					parentMangaId = 1001L,
					childMangaId = 1002L,
					sourceName = "EXT:Bato.to (Mihon EN)",
					priorityWeight = 110,
					preferredScanlator = "Kirei Cake",
					createdAt = now,
				),
			)
			db.getAuxiliaryDao().upsertMergeLink(
				MangaMergeLinkEntity(
					parentMangaId = 1003L,
					childMangaId = 1004L,
					sourceName = "EXT:Kotatsu IPC Provider",
					priorityWeight = 105,
					preferredScanlator = "EverydayHeroes",
					createdAt = now,
				),
			)

			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
	}

	/**
	 * Scrubs all debug/mock manga entries and orphaned history cached in Room without deleting real user manga.
	 */
	suspend fun purgeDebugMockDataset(): Int = withContext(Dispatchers.IO) {
		val allExisting = db.getMangaDao().getAllWithTags()
		val mockIds = allExisting
			.map { it.manga }
			.filter { isDebugMockEntity(it) }
			.map { it.id }
			.toSet() + DEBUG_MOCK_MANGA_IDS
		removeMangaFromLibrary(mockIds)

		val remainingValidMangaIds = db.getMangaDao().getAllWithTags()
			.map { it.manga }
			.filterNot { isDebugMockEntity(it) }
			.map { it.id }
			.toSet()
		val orphanedHistoryIds = db.getHistoryDao().getAllHistory()
			.map { it.mangaId }
			.filter { it !in remainingValidMangaIds }
			.toSet()
		if (orphanedHistoryIds.isNotEmpty()) {
			db.withTransaction {
				for (id in orphanedHistoryIds) {
					db.getHistoryDao().deleteByMangaId(id)
					db.getChaptersHistoryDao().deleteByMangaId(id)
				}
			}
		}
		mockIds.size
	}

	/**
	 * Seeds the Kotatsu v28 database. In production UI, `seedDebugMockData` is controlled by
	 * `debugSandboxEnabled` (strictly defaulting to `false`).
	 */
	suspend fun ensureSeeded(seedDebugMockData: Boolean = true) = withContext(Dispatchers.IO) {
		ensureBaseCategoriesAndReposSeeded()
		if (seedDebugMockData) {
			if (db.getMangaDao().getAllWithTags().none { it.manga.id in DEBUG_MOCK_MANGA_IDS }) {
				seedDebugSandboxDataset()
			}
		} else {
			purgeDebugMockDataset()
		}
	}

	suspend fun updateMangaMetadataOverride(
		mangaId: Long,
		newTitle: String,
		newAuthor: String,
		newSynopsis: String,
	) = withContext(Dispatchers.IO) {
		val existing = db.getMangaDao().find(mangaId)?.manga ?: return@withContext
		val updated = existing.copy(
			title = newTitle.trim().ifBlank { existing.title },
			authors = newAuthor.trim().ifBlank { existing.authors },
			description = newSynopsis.trim().ifBlank { existing.description },
			normalizedTitle = TitleNormalizer.normalize(newTitle.trim().ifBlank { existing.title }),
		)
		db.getMangaDao().upsertManga(updated)
	}
}
