package org.koitharu.kotatsu.backups.domain

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.ChapterHistoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.MangaAliasEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.MangaMergeLinkEntity
import org.koitharu.kotatsu.core.db.entity.ScrobblingEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.util.TitleNormalizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class SourceResolutionTier {
	TIER_1_NATIVE_KOTATSU,
	TIER_2_INSTALLED_MIHON_EXTENSION,
	TIER_3_UNINSTALLED_STUB_SOURCE,
}

data class ResolvedSourceResult(
	val primarySourceName: String,
	val tier: SourceResolutionTier,
	val priorityWeight: Int,
	val fallbackExtensionSourceName: String? = null,
)

data class BackupSource(
	val sourceId: Long,
	val name: String,
)

data class BackupTracking(
	val syncId: Int, // 1 = MAL, 2 = AniList, 3 = Kitsu, 4 = Shikimori, 7 = MangaDex
	val mediaId: Long,
	val trackingUrl: String = "",
	val title: String = "",
	val lastChapterRead: Float = 0f,
	val totalChapters: Int = 0,
	val score: Float = 0f,
	val status: Int = 1,
)

data class BackupChapter(
	val url: String,
	val name: String,
	val scanlator: String? = null,
	val read: Boolean = false,
	val bookmark: Boolean = false,
	val lastPageRead: Int = 0,
	val dateFetch: Long = 0L,
	val dateUpload: Long = 0L,
	val chapterNumber: Float = -1f,
	val sourceOrder: Int = 0,
)

data class BackupCategory(
	val name: String,
	val order: Int = 0,
	val flags: Int = 0,
)

data class BackupManga(
	val source: Long,
	val url: String,
	val title: String = "",
	val artist: String? = null,
	val author: String? = null,
	val description: String? = null,
	val genre: List<String> = emptyList(),
	val status: Int = 0,
	val thumbnailUrl: String? = null,
	val dateAdded: Long = System.currentTimeMillis(),
	val viewer: Int = 0,
	val chapters: List<BackupChapter> = emptyList(),
	val categories: List<Int> = emptyList(),
	val tracking: List<BackupTracking> = emptyList(),
	val favorite: Boolean = true,
)

data class BackupPayload(
	val backupManga: List<BackupManga> = emptyList(),
	val backupCategories: List<BackupCategory> = emptyList(),
	val backupSources: List<BackupSource> = emptyList(),
)

data class TachibkImportReport(
	val totalIncomingManga: Int,
	val newlyInsertedManga: Int,
	val deduplicatedMergedLinksCreated: Int,
	val promotedToNativeKotatsuCount: Int,
	val matchedInstalledMihonCount: Int,
	val preservedAsUninstalledStubCount: Int,
	val chaptersMergedCount: Int,
	val trackersLinkedCount: Int,
)

@Singleton
class TachibkSmartMergeImporter @Inject constructor(
	private val db: MangaDatabase,
) {
	companion object {
		private const val IMPORT_BATCH_SIZE = 250

		private val NATIVE_KOTATSU_CATALOG_MAP = mapOf(
			"mangadex" to "MANGADEX",
			"mangasee" to "MANGASEE",
			"weebcentral" to "WEEBCENTRAL",
			"asurascans" to "ASURASCANS",
			"asuracomic" to "ASURASCANS",
			"batoto" to "BATOTO",
			"bato" to "BATOTO",
			"comick" to "COMICK",
			"mangafire" to "MANGAFIRE",
			"manganato" to "MANGANATO",
			"mangakakalot" to "MANGANATO",
		)
	}

	/**
	 * 1. Source Resolution & Native Promotion (`resolveBestSource`):
	 * - Tier 1 (Native Kotatsu Parser): Matches by normalized source name or domain host.
	 *   Promotes to native Kotatsu parser (`MANGADEX`, `MANGASEE`, `ASURASCANS`, `BATOTO`, etc.)
	 *   while linking the APK extension source as a secondary fallback.
	 * - Tier 2 (Installed Mihon Extension): Matches `MIHON_${sourceId}` if installed.
	 * - Tier 3 (Uninstalled Stub Source): Preserves under a stub source tag so library entries
	 *   and reading history are never lost.
	 */
	fun resolveBestSource(
		sourceId: Long,
		rawSourceName: String,
		installedMihonSourceIds: Set<Long> = emptySet(),
	): ResolvedSourceResult {
		val normalizedKey = rawSourceName
			.lowercase(Locale.ROOT)
			.replace(Regex("""\([^)]*\)"""), "")
			.replace(Regex("""[^a-z0-9]"""), "")

		for ((domainToken, nativeParserId) in NATIVE_KOTATSU_CATALOG_MAP) {
			if (normalizedKey.contains(domainToken)) {
				val fallbackExt = if (sourceId != 0L) "MIHON_${sourceId}_${rawSourceName.trim()}" else null
				return ResolvedSourceResult(
					primarySourceName = nativeParserId,
					tier = SourceResolutionTier.TIER_1_NATIVE_KOTATSU,
					priorityWeight = 10,
					fallbackExtensionSourceName = fallbackExt,
				)
			}
		}

		if (sourceId in installedMihonSourceIds) {
			return ResolvedSourceResult(
				primarySourceName = "MIHON_$sourceId",
				tier = SourceResolutionTier.TIER_2_INSTALLED_MIHON_EXTENSION,
				priorityWeight = 50,
				fallbackExtensionSourceName = null,
			)
		}

		val cleanStubName = rawSourceName.trim().ifBlank { "Source_$sourceId" }
		return ResolvedSourceResult(
			primarySourceName = "MIHON_STUB_${sourceId}_$cleanStubName",
			tier = SourceResolutionTier.TIER_3_UNINSTALLED_STUB_SOURCE,
			priorityWeight = 150,
			fallbackExtensionSourceName = null,
		)
	}

	/**
	 * Extracts canonical tracker identity (`al:...`, `mal:...`, `md:...`) from backup tracking records or URLs.
	 */
	fun extractCanonicalId(backupManga: BackupManga): String? {
		// Priority: AniList (syncId = 2), MyAnimeList (syncId = 1), MangaDex (syncId = 7 or URL UUID)
		val anilist = backupManga.tracking.firstOrNull { it.syncId == 2 && it.mediaId > 0L }
		if (anilist != null) return "al:${anilist.mediaId}"

		val mal = backupManga.tracking.firstOrNull { it.syncId == 1 && it.mediaId > 0L }
		if (mal != null) return "mal:${mal.mediaId}"

		val uuidRegex = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""", RegexOption.IGNORE_CASE)
		val urlUuid = uuidRegex.find(backupManga.url)?.value
		if (urlUuid != null) return "md:${urlUuid.lowercase(Locale.ROOT)}"

		return null
	}

	/**
	 * Streams a `.tachibk` (GZIP-compressed Protobuf) InputStream and imports it using
	 * Native-First Promotion and Sidecar Deduplication.
	 */
	suspend fun importFromTachibkStream(
		inputStream: InputStream,
		installedMihonSourceIds: Set<Long> = emptySet(),
	): TachibkImportReport = withContext(Dispatchers.IO) {
		val rawBytes = inputStream.readBytes()
		val payload = decodeTachibkBytes(rawBytes)
		importBackupPayload(payload, installedMihonSourceIds)
	}

	/**
	 * Imports a decoded `BackupPayload` in chunked transactions of 250 manga and refreshes `library_fast_view`.
	 */
	suspend fun importBackupPayload(
		payload: BackupPayload,
		installedMihonSourceIds: Set<Long> = emptySet(),
	): TachibkImportReport = withContext(Dispatchers.IO) {
		val sourceNameById = payload.backupSources.associate { it.sourceId to it.name }
		val now = System.currentTimeMillis()

		// Preload O(1) lookup maps for canonical_id and normalized_slug -> manga_id
		val existingManga = db.getMangaDao().getAllWithTags().map { it.manga }
		val canonicalMap = HashMap<String, Long>(existingManga.size * 2)
		val slugMap = HashMap<String, Long>(existingManga.size * 2)
		val sourceByMangaId = HashMap<Long, String>(existingManga.size * 2)

		for (m in existingManga) {
			val slug = m.normalizedSlug.ifBlank { TitleNormalizer.toNormalizedSlug(m.title) }
			if (slug.isNotBlank()) slugMap[slug] = m.id
			if (!m.canonicalId.isNullOrBlank()) canonicalMap[m.canonicalId] = m.id
			sourceByMangaId[m.id] = m.source
		}

		// Upsert categories first
		val categoryIdByOrder = HashMap<Int, Long>()
		if (payload.backupCategories.isNotEmpty()) {
			db.withTransaction {
				val existingCats = db.getFavouritesDao().getCategories().associateBy { it.title.lowercase(Locale.ROOT) }
				var nextCatId = (existingCats.values.maxOfOrNull { it.categoryId } ?: 4) + 1
				for ((idx, bCat) in payload.backupCategories.withIndex()) {
					val existing = existingCats[bCat.name.lowercase(Locale.ROOT)]
					val resolvedId = existing?.categoryId ?: nextCatId++
					if (existing == null) {
						db.getFavouritesDao().upsertCategory(
							FavouriteCategoryEntity(
								categoryId = resolvedId,
								createdAt = now,
								sortKey = bCat.order.takeIf { it > 0 } ?: idx,
								title = bCat.name,
								order = "LAST_READ",
								track = true,
								isVisibleInLibrary = true,
							),
						)
					}
					categoryIdByOrder[bCat.order] = resolvedId.toLong()
					categoryIdByOrder[idx] = resolvedId.toLong()
				}
			}
		}

		var newlyInserted = 0
		var mergedLinksCreated = 0
		var promotedNativeCount = 0
		var installedMihonCount = 0
		var stubCount = 0
		var chaptersMergedCount = 0
		var trackersLinkedCount = 0
		val touchedMangaIds = HashSet<Long>()

		// Process in batches of 250 per db.withTransaction
		for (chunk in payload.backupManga.chunked(IMPORT_BATCH_SIZE)) {
			db.withTransaction {
				for (bManga in chunk) {
					if (bManga.title.isBlank()) continue

					val rawSourceName = sourceNameById[bManga.source] ?: "Mihon_${bManga.source}"
					val resolvedSource = resolveBestSource(
						sourceId = bManga.source,
						rawSourceName = rawSourceName,
						installedMihonSourceIds = installedMihonSourceIds,
					)
					when (resolvedSource.tier) {
						SourceResolutionTier.TIER_1_NATIVE_KOTATSU -> promotedNativeCount++
						SourceResolutionTier.TIER_2_INSTALLED_MIHON_EXTENSION -> installedMihonCount++
						SourceResolutionTier.TIER_3_UNINSTALLED_STUB_SOURCE -> stubCount++
					}

					val slug = TitleNormalizer.toNormalizedSlug(bManga.title)
					val canonicalId = extractCanonicalId(bManga)
					val deterministicMangaId = computeDeterministicMangaId(resolvedSource.primarySourceName, bManga.url, bManga.title)

					val existingParentId = (if (canonicalId != null) canonicalMap[canonicalId] else null)
						?: (if (slug.isNotBlank()) slugMap[slug] else null)

					val stateStr = when (bManga.status) {
						2 -> "FINISHED"
						3, 5 -> "ABANDONED"
						4 -> "PAUSED"
						else -> "ONGOING"
					}

					val incomingEntity = MangaEntity(
						id = deterministicMangaId,
						title = bManga.title,
						altTitles = null,
						url = bManga.url,
						publicUrl = bManga.url,
						rating = 0.90f,
						isNsfw = false,
						contentRating = "SAFE",
						coverUrl = bManga.thumbnailUrl?.takeIf { it.isNotBlank() } ?: "studio_cover://frieren",
						largeCoverUrl = bManga.thumbnailUrl?.takeIf { it.isNotBlank() } ?: "studio_cover://frieren",
						state = stateStr,
						authors = bManga.author ?: bManga.artist,
						source = resolvedSource.primarySourceName,
						description = bManga.description.orEmpty(),
						normalizedTitle = org.koitharu.kotatsu.core.db.entity.TitleNormalizer.normalize(bManga.title),
						normalizedSlug = slug,
						canonicalId = canonicalId,
						isBroken = resolvedSource.tier == SourceResolutionTier.TIER_3_UNINSTALLED_STUB_SOURCE,
						healthError = if (resolvedSource.tier == SourceResolutionTier.TIER_3_UNINSTALLED_STUB_SOURCE) {
							"Uninstalled extension ($rawSourceName) — Tap AI Fix-It or install extension"
						} else {
							null
						},
					)

					val tags = bManga.genre.mapIndexed { tIdx, g ->
						TagEntity(
							id = abs((g + resolvedSource.primarySourceName).hashCode().toLong()).coerceAtLeast(1L) + tIdx,
							title = g,
							key = g.lowercase(Locale.ROOT),
							source = resolvedSource.primarySourceName,
						)
					}

					if (existingParentId == null || existingParentId == deterministicMangaId) {
						// Case A: No cross-source duplicate -> Insert as primary library card
						db.getMangaDao().upsert(incomingEntity, tags)
						if (slug.isNotBlank()) slugMap[slug] = deterministicMangaId
						if (canonicalId != null) canonicalMap[canonicalId] = deterministicMangaId
						sourceByMangaId[deterministicMangaId] = resolvedSource.primarySourceName
						touchedMangaIds.add(deterministicMangaId)
						newlyInserted++

						// Insert categories into favourites
						val targetCats = bManga.categories.mapNotNull { categoryIdByOrder[it] }.ifEmpty { listOf(1L) }
						for ((sIdx, catId) in targetCats.distinct().withIndex()) {
							db.getFavouritesDao().upsert(
								FavouriteEntity(
									mangaId = deterministicMangaId,
									categoryId = catId,
									sortKey = sIdx,
									createdAt = bManga.dateAdded.takeIf { it > 0L } ?: now,
								),
							)
						}

						// If promoted to Native Kotatsu, also register the original APK extension in manga_merge_links as fallback
						if (resolvedSource.fallbackExtensionSourceName != null) {
							val childExtId = computeDeterministicMangaId(
								resolvedSource.fallbackExtensionSourceName,
								bManga.url,
								bManga.title,
							)
							if (childExtId != deterministicMangaId) {
								db.getMangaDao().upsertManga(
									incomingEntity.copy(
										id = childExtId,
										source = resolvedSource.fallbackExtensionSourceName,
									),
								)
								db.getAuxiliaryDao().upsertMergeLink(
									MangaMergeLinkEntity(
										parentMangaId = deterministicMangaId,
										childMangaId = childExtId,
										sourceName = resolvedSource.fallbackExtensionSourceName,
										priorityWeight = 100,
										preferredScanlator = null,
										createdAt = now,
									),
								)
							}
						}

						// Insert chapters and read history
						upsertBackupChaptersAndHistory(
							targetMangaId = deterministicMangaId,
							backupChapters = bManga.chapters,
							now = now,
						)
						chaptersMergedCount += bManga.chapters.size

						// Insert tracker bindings
						trackersLinkedCount += upsertBackupTrackers(deterministicMangaId, bManga.tracking, now)
					} else {
						// Case B: Match already exists under a different source -> Sidecar link in manga_merge_links!
						db.getMangaDao().upsert(incomingEntity, tags)
						db.getAuxiliaryDao().upsertMergeLink(
							MangaMergeLinkEntity(
								parentMangaId = existingParentId,
								childMangaId = deterministicMangaId,
								sourceName = resolvedSource.primarySourceName,
								priorityWeight = resolvedSource.priorityWeight,
								preferredScanlator = bManga.chapters.firstOrNull { !it.scanlator.isNullOrBlank() }?.scanlator,
								createdAt = now,
							),
						)
						mergedLinksCreated++
						touchedMangaIds.add(existingParentId)
						touchedMangaIds.add(deterministicMangaId)

						// Merge read chapter timestamps and chapters into both child and parent
						upsertBackupChaptersAndHistory(
							targetMangaId = deterministicMangaId,
							backupChapters = bManga.chapters,
							now = now,
						)
						mergeReadProgressIntoParent(
							parentMangaId = existingParentId,
							incomingChapters = bManga.chapters,
							now = now,
						)
						chaptersMergedCount += bManga.chapters.size

						// Merge tracker bindings into parent
						trackersLinkedCount += upsertBackupTrackers(existingParentId, bManga.tracking, now)
						if (canonicalId != null) {
							val parentWithTags = db.getMangaDao().find(existingParentId)
							if (parentWithTags != null && parentWithTags.manga.canonicalId.isNullOrBlank()) {
								db.getMangaDao().updateCanonicalDetails(
									mangaId = existingParentId,
									title = parentWithTags.manga.title,
									normalizedSlug = parentWithTags.manga.normalizedSlug.ifBlank { slug },
									canonicalId = canonicalId,
									coverUrl = parentWithTags.manga.coverUrl,
									description = parentWithTags.manga.description,
								)
							}
						}
					}
				}
			}
		}

		// 3. Single Post-Import SQL Query to refresh library_fast_view for all imported/merged IDs
		refreshLibraryFastViewBatch(now)

		TachibkImportReport(
			totalIncomingManga = payload.backupManga.size,
			newlyInsertedManga = newlyInserted,
			deduplicatedMergedLinksCreated = mergedLinksCreated,
			promotedToNativeKotatsuCount = promotedNativeCount,
			matchedInstalledMihonCount = installedMihonCount,
			preservedAsUninstalledStubCount = stubCount,
			chaptersMergedCount = chaptersMergedCount,
			trackersLinkedCount = trackersLinkedCount,
		)
	}

	private suspend fun upsertBackupChaptersAndHistory(
		targetMangaId: Long,
		backupChapters: List<BackupChapter>,
		now: Long,
	) {
		if (backupChapters.isEmpty()) return
		val sorted = backupChapters.sortedBy { if (it.chapterNumber >= 0f) it.chapterNumber else it.sourceOrder.toFloat() }
		val chapterEntities = sorted.mapIndexed { idx, bCh ->
			val num = if (bCh.chapterNumber >= 0f) bCh.chapterNumber else (idx + 1).toFloat()
			val chId = targetMangaId * 10_000L + (idx + 1)
			ChapterEntity(
				chapterId = chId,
				mangaId = targetMangaId,
				title = bCh.name.ifBlank { "Chapter ${idx + 1}" },
				number = num,
				volume = 1,
				url = bCh.url.ifBlank { "/manga/$targetMangaId/ch/${idx + 1}" },
				scanlator = bCh.scanlator,
				uploadDate = bCh.dateUpload.takeIf { it > 0L } ?: (now - (sorted.size - idx) * 86_400_000L),
				branch = null,
				index = idx,
				isRead = bCh.read,
				isDownloaded = false,
				lastPageRead = bCh.lastPageRead,
			)
		}
		db.getChaptersDao().upsertAll(chapterEntities)

		var readCount = 0
		var lastReadChId = chapterEntities.first().chapterId
		var lastReadPage = 0
		for ((idx, chEnt) in chapterEntities.withIndex()) {
			if (chEnt.isRead) {
				readCount++
				lastReadChId = chEnt.chapterId
				lastReadPage = sorted[idx].lastPageRead
				db.getChaptersHistoryDao().upsert(
					ChapterHistoryEntity(
						mangaId = targetMangaId,
						chapterId = chEnt.chapterId,
						readAt = sorted[idx].dateFetch.takeIf { it > 0L } ?: now,
					),
				)
			}
		}

		if (readCount > 0) {
			val pct = (readCount.toFloat() / chapterEntities.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = targetMangaId,
					createdAt = now,
					updatedAt = now,
					chapterId = lastReadChId,
					page = lastReadPage,
					scroll = 0f,
					percent = pct,
					chaptersCount = chapterEntities.size,
				),
			)
		}
	}

	private suspend fun mergeReadProgressIntoParent(
		parentMangaId: Long,
		incomingChapters: List<BackupChapter>,
		now: Long,
	) {
		val readIncomingKeys = incomingChapters
			.filter { it.read }
			.map { TitleNormalizer.toCanonicalChapterKey(it.chapterNumber, it.name) }
			.filter { it >= 0 }
			.toSet()
		if (readIncomingKeys.isEmpty()) return

		val parentChapters = db.getChaptersDao().findAll(parentMangaId)
		var updatedAny = false
		for (pCh in parentChapters) {
			val key = TitleNormalizer.toCanonicalChapterKey(pCh.number, pCh.title)
			if (key in readIncomingKeys && !pCh.isRead) {
				db.getChaptersDao().updateReadStatus(parentMangaId, pCh.chapterId, true, 18)
				db.getChaptersHistoryDao().upsert(
					ChapterHistoryEntity(
						mangaId = parentMangaId,
						chapterId = pCh.chapterId,
						readAt = now,
					),
				)
				updatedAny = true
			}
		}

		if (updatedAny) {
			val refreshed = db.getChaptersDao().findAll(parentMangaId)
			val readCount = refreshed.count { it.isRead }
			val total = refreshed.size.coerceAtLeast(1)
			val lastRead = refreshed.lastOrNull { it.isRead } ?: refreshed.first()
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = parentMangaId,
					createdAt = now,
					updatedAt = now,
					chapterId = lastRead.chapterId,
					page = lastRead.lastPageRead,
					scroll = 0f,
					percent = (readCount.toFloat() / total.toFloat()).coerceIn(0f, 1f),
					chaptersCount = total,
				),
			)
		}
	}

	private suspend fun upsertBackupTrackers(
		mangaId: Long,
		trackings: List<BackupTracking>,
		now: Long,
	): Int {
		var count = 0
		for (tr in trackings) {
			if (tr.mediaId <= 0L) continue
			db.getAuxiliaryDao().upsertScrobbling(
				ScrobblingEntity(
					scrobbler = tr.syncId,
					id = tr.mediaId.toInt(),
					mangaId = mangaId,
					targetId = tr.mediaId,
					status = when (tr.status) {
						2 -> "COMPLETED"
						3 -> "ON_HOLD"
						4 -> "DROPPED"
						6 -> "PLAN_TO_READ"
						else -> "READING"
					},
					chapter = tr.lastChapterRead.toInt(),
					comment = tr.trackingUrl,
					rating = tr.score,
				),
			)
			if (tr.title.isNotBlank()) {
				val aliasSlug = TitleNormalizer.toNormalizedSlug(tr.title)
				if (aliasSlug.isNotBlank()) {
					db.getMangaAliasesDao().upsertAll(
						listOf(MangaAliasEntity(aliasSlug = aliasSlug, mangaId = mangaId, rawAlias = tr.title)),
					)
				}
			}
			count++
		}
		return count
	}

	private fun refreshLibraryFastViewBatch(now: Long) {
		val sdb = db.openHelper.writableDatabase
		sdb.execSQL(
			"""
			INSERT OR REPLACE INTO library_fast_view (
				manga_id,
				total_chapters,
				read_chapters,
				unread_count,
				last_read_at,
				latest_chapter_date,
				avg_release_interval_days,
				last_polled_at
			)
			SELECT
				m.manga_id,
				COUNT(c.chapter_id),
				COALESCE(SUM(CASE WHEN c.is_read = 1 THEN 1 ELSE 0 END), 0),
				COALESCE(SUM(CASE WHEN c.is_read = 0 THEN 1 ELSE 0 END), 0),
				COALESCE((SELECT h.updated_at FROM history h WHERE h.manga_id = m.manga_id LIMIT 1), 0),
				COALESCE(MAX(c.upload_date), 0),
				7,
				$now
			FROM manga m
			LEFT JOIN chapters c ON c.manga_id = m.manga_id
			GROUP BY m.manga_id
			""".trimIndent(),
		)
		runCatching {
			sdb.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
	}

	private fun computeDeterministicMangaId(sourceName: String, url: String, title: String): Long {
		var h = 1125899906842597L
		val key = "$sourceName|$url|$title"
		for (ch in key) {
			h = 31L * h + ch.code.toLong()
		}
		return abs(h).coerceAtLeast(10_000L)
	}

	/**
	 * Encodes a `BackupPayload` into GZIP-compressed Protobuf `.tachibk` bytes.
	 */
	fun exportToTachibkBytes(payload: BackupPayload): ByteArray {
		val protoOut = ByteArrayOutputStream()
		for (m in payload.backupManga) {
			val mangaBytes = encodeBackupMangaProto(m)
			writeTagAndLength(protoOut, fieldNumber = 1, wireType = 2, length = mangaBytes.size)
			protoOut.write(mangaBytes)
		}
		for (c in payload.backupCategories) {
			val catBytes = encodeBackupCategoryProto(c)
			writeTagAndLength(protoOut, fieldNumber = 2, wireType = 2, length = catBytes.size)
			protoOut.write(catBytes)
		}
		for (s in payload.backupSources) {
			val srcBytes = encodeBackupSourceProto(s)
			writeTagAndLength(protoOut, fieldNumber = 101, wireType = 2, length = srcBytes.size)
			protoOut.write(srcBytes)
		}
		val rawProto = protoOut.toByteArray()
		val gzipOut = ByteArrayOutputStream()
		GZIPOutputStream(gzipOut).use { it.write(rawProto) }
		return gzipOut.toByteArray()
	}

	/**
	 * Decodes GZIP-compressed or raw Protobuf `.tachibk` bytes into a `BackupPayload`.
	 */
	fun decodeTachibkBytes(bytes: ByteArray): BackupPayload {
		if (bytes.isEmpty()) return BackupPayload()
		val uncompressed = runCatching {
			if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
				GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
			} else {
				bytes
			}
		}.getOrDefault(bytes)

		val mangaList = ArrayList<BackupManga>()
		val categories = ArrayList<BackupCategory>()
		val sources = ArrayList<BackupSource>()

		val reader = ProtoCursor(uncompressed)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint32()
			if (tag <= 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when {
				fieldNumber == 1 && wireType == 2 -> {
					val sub = reader.readBytes()
					mangaList.add(decodeBackupMangaProto(sub))
				}
				fieldNumber == 2 && wireType == 2 -> {
					val sub = reader.readBytes()
					categories.add(decodeBackupCategoryProto(sub))
				}
				(fieldNumber == 100 || fieldNumber == 101) && wireType == 2 -> {
					val sub = reader.readBytes()
					sources.add(decodeBackupSourceProto(sub))
				}
				else -> reader.skipField(wireType)
			}
		}
		return BackupPayload(
			backupManga = mangaList,
			backupCategories = categories,
			backupSources = sources,
		)
	}

	private fun encodeBackupMangaProto(m: BackupManga): ByteArray {
		val out = ByteArrayOutputStream()
		writeVarintField(out, 1, m.source)
		writeStringField(out, 2, m.url)
		writeStringField(out, 3, m.title)
		if (!m.artist.isNullOrBlank()) writeStringField(out, 4, m.artist)
		if (!m.author.isNullOrBlank()) writeStringField(out, 5, m.author)
		if (!m.description.isNullOrBlank()) writeStringField(out, 6, m.description)
		for (g in m.genre) writeStringField(out, 7, g)
		writeVarintField(out, 8, m.status.toLong())
		if (!m.thumbnailUrl.isNullOrBlank()) writeStringField(out, 9, m.thumbnailUrl)
		writeVarintField(out, 13, m.dateAdded)
		for (ch in m.chapters) {
			val chBytes = encodeBackupChapterProto(ch)
			writeTagAndLength(out, 16, 2, chBytes.size)
			out.write(chBytes)
		}
		for (cat in m.categories) {
			writeVarintField(out, 17, cat.toLong())
		}
		for (tr in m.tracking) {
			val trBytes = encodeBackupTrackingProto(tr)
			writeTagAndLength(out, 18, 2, trBytes.size)
			out.write(trBytes)
		}
		return out.toByteArray()
	}

	private fun decodeBackupMangaProto(bytes: ByteArray): BackupManga {
		val c = ProtoCursor(bytes)
		var source = 0L
		var url = ""
		var title = ""
		var artist: String? = null
		var author: String? = null
		var description: String? = null
		val genres = ArrayList<String>()
		var status = 0
		var thumbnailUrl: String? = null
		var dateAdded = System.currentTimeMillis()
		val chapters = ArrayList<BackupChapter>()
		val categories = ArrayList<Int>()
		val tracking = ArrayList<BackupTracking>()

		while (c.hasRemaining()) {
			val tag = c.readVarint32()
			if (tag <= 0) break
			val fn = tag ushr 3
			val wt = tag and 0x07
			when {
				fn == 1 && wt == 0 -> source = c.readVarint64()
				fn == 2 && wt == 2 -> url = c.readString()
				fn == 3 && wt == 2 -> title = c.readString()
				fn == 4 && wt == 2 -> artist = c.readString()
				fn == 5 && wt == 2 -> author = c.readString()
				fn == 6 && wt == 2 -> description = c.readString()
				fn == 7 && wt == 2 -> genres.add(c.readString())
				fn == 8 && wt == 0 -> status = c.readVarint32()
				fn == 9 && wt == 2 -> thumbnailUrl = c.readString()
				fn == 13 && wt == 0 -> dateAdded = c.readVarint64()
				fn == 16 && wt == 2 -> chapters.add(decodeBackupChapterProto(c.readBytes()))
				fn == 17 && wt == 0 -> categories.add(c.readVarint32())
				fn == 18 && wt == 2 -> tracking.add(decodeBackupTrackingProto(c.readBytes()))
				else -> c.skipField(wt)
			}
		}
		return BackupManga(
			source = source,
			url = url,
			title = title,
			artist = artist,
			author = author,
			description = description,
			genre = genres,
			status = status,
			thumbnailUrl = thumbnailUrl,
			dateAdded = dateAdded,
			chapters = chapters,
			categories = categories,
			tracking = tracking,
		)
	}

	private fun encodeBackupChapterProto(ch: BackupChapter): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, ch.url)
		writeStringField(out, 2, ch.name)
		if (!ch.scanlator.isNullOrBlank()) writeStringField(out, 3, ch.scanlator)
		writeVarintField(out, 4, if (ch.read) 1L else 0L)
		writeVarintField(out, 5, if (ch.bookmark) 1L else 0L)
		writeVarintField(out, 6, ch.lastPageRead.toLong())
		writeVarintField(out, 7, ch.dateFetch)
		writeVarintField(out, 8, ch.dateUpload)
		writeFloatField(out, 9, ch.chapterNumber)
		writeVarintField(out, 10, ch.sourceOrder.toLong())
		return out.toByteArray()
	}

	private fun decodeBackupChapterProto(bytes: ByteArray): BackupChapter {
		val c = ProtoCursor(bytes)
		var url = ""
		var name = ""
		var scanlator: String? = null
		var read = false
		var bookmark = false
		var lastPageRead = 0
		var dateFetch = 0L
		var dateUpload = 0L
		var chapterNumber = -1f
		var sourceOrder = 0
		while (c.hasRemaining()) {
			val tag = c.readVarint32()
			if (tag <= 0) break
			val fn = tag ushr 3
			val wt = tag and 0x07
			when {
				fn == 1 && wt == 2 -> url = c.readString()
				fn == 2 && wt == 2 -> name = c.readString()
				fn == 3 && wt == 2 -> scanlator = c.readString()
				fn == 4 && wt == 0 -> read = c.readVarint32() != 0
				fn == 5 && wt == 0 -> bookmark = c.readVarint32() != 0
				fn == 6 && wt == 0 -> lastPageRead = c.readVarint32()
				fn == 7 && wt == 0 -> dateFetch = c.readVarint64()
				fn == 8 && wt == 0 -> dateUpload = c.readVarint64()
				fn == 9 && wt == 5 -> chapterNumber = c.readFloat()
				fn == 10 && wt == 0 -> sourceOrder = c.readVarint32()
				else -> c.skipField(wt)
			}
		}
		return BackupChapter(
			url = url,
			name = name,
			scanlator = scanlator,
			read = read,
			bookmark = bookmark,
			lastPageRead = lastPageRead,
			dateFetch = dateFetch,
			dateUpload = dateUpload,
			chapterNumber = chapterNumber,
			sourceOrder = sourceOrder,
		)
	}

	private fun encodeBackupTrackingProto(tr: BackupTracking): ByteArray {
		val out = ByteArrayOutputStream()
		writeVarintField(out, 1, tr.syncId.toLong())
		writeVarintField(out, 2, tr.mediaId)
		writeStringField(out, 4, tr.title)
		writeFloatField(out, 5, tr.lastChapterRead)
		writeVarintField(out, 6, tr.totalChapters.toLong())
		writeFloatField(out, 7, tr.score)
		writeVarintField(out, 8, tr.status.toLong())
		writeStringField(out, 11, tr.trackingUrl)
		return out.toByteArray()
	}

	private fun decodeBackupTrackingProto(bytes: ByteArray): BackupTracking {
		val c = ProtoCursor(bytes)
		var syncId = 1
		var mediaId = 0L
		var title = ""
		var lastChapterRead = 0f
		var totalChapters = 0
		var score = 0f
		var status = 1
		var trackingUrl = ""
		while (c.hasRemaining()) {
			val tag = c.readVarint32()
			if (tag <= 0) break
			val fn = tag ushr 3
			val wt = tag and 0x07
			when {
				fn == 1 && wt == 0 -> syncId = c.readVarint32()
				fn == 2 && wt == 0 -> mediaId = c.readVarint64()
				fn == 4 && wt == 2 -> title = c.readString()
				fn == 5 && wt == 5 -> lastChapterRead = c.readFloat()
				fn == 6 && wt == 0 -> totalChapters = c.readVarint32()
				fn == 7 && wt == 5 -> score = c.readFloat()
				fn == 8 && wt == 0 -> status = c.readVarint32()
				fn == 11 && wt == 2 -> trackingUrl = c.readString()
				else -> c.skipField(wt)
			}
		}
		return BackupTracking(
			syncId = syncId,
			mediaId = mediaId,
			trackingUrl = trackingUrl,
			title = title,
			lastChapterRead = lastChapterRead,
			totalChapters = totalChapters,
			score = score,
			status = status,
		)
	}

	private fun encodeBackupCategoryProto(cat: BackupCategory): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, cat.name)
		writeVarintField(out, 2, cat.order.toLong())
		writeVarintField(out, 100, cat.flags.toLong())
		return out.toByteArray()
	}

	private fun decodeBackupCategoryProto(bytes: ByteArray): BackupCategory {
		val c = ProtoCursor(bytes)
		var name = ""
		var order = 0
		var flags = 0
		while (c.hasRemaining()) {
			val tag = c.readVarint32()
			if (tag <= 0) break
			val fn = tag ushr 3
			val wt = tag and 0x07
			when {
				fn == 1 && wt == 2 -> name = c.readString()
				fn == 2 && wt == 0 -> order = c.readVarint32()
				fn == 100 && wt == 0 -> flags = c.readVarint32()
				else -> c.skipField(wt)
			}
		}
		return BackupCategory(name = name, order = order, flags = flags)
	}

	private fun encodeBackupSourceProto(src: BackupSource): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, src.name)
		writeVarintField(out, 2, src.sourceId)
		return out.toByteArray()
	}

	private fun decodeBackupSourceProto(bytes: ByteArray): BackupSource {
		val c = ProtoCursor(bytes)
		var name = ""
		var sourceId = 0L
		while (c.hasRemaining()) {
			val tag = c.readVarint32()
			if (tag <= 0) break
			val fn = tag ushr 3
			val wt = tag and 0x07
			when {
				fn == 1 && wt == 2 -> name = c.readString()
				fn == 2 && wt == 0 -> sourceId = c.readVarint64()
				else -> c.skipField(wt)
			}
		}
		return BackupSource(sourceId = sourceId, name = name)
	}

	private fun writeTagAndLength(out: ByteArrayOutputStream, fieldNumber: Int, wireType: Int, length: Int) {
		writeRawVarint64(out, ((fieldNumber shl 3) or wireType).toLong())
		writeRawVarint64(out, length.toLong())
	}

	private fun writeVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Long) {
		writeRawVarint64(out, ((fieldNumber shl 3) or 0).toLong())
		writeRawVarint64(out, value)
	}

	private fun writeStringField(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
		val bytes = value.toByteArray(Charsets.UTF_8)
		writeTagAndLength(out, fieldNumber, 2, bytes.size)
		out.write(bytes)
	}

	private fun writeFloatField(out: ByteArrayOutputStream, fieldNumber: Int, value: Float) {
		writeRawVarint64(out, ((fieldNumber shl 3) or 5).toLong())
		val bits = java.lang.Float.floatToIntBits(value)
		out.write(bits and 0xFF)
		out.write((bits ushr 8) and 0xFF)
		out.write((bits ushr 16) and 0xFF)
		out.write((bits ushr 24) and 0xFF)
	}

	private fun writeRawVarint64(out: ByteArrayOutputStream, value: Long) {
		var v = value
		while (true) {
			if ((v and 0x7FL.inv()) == 0L) {
				out.write(v.toInt())
				return
			} else {
				out.write(((v.toInt() and 0x7F) or 0x80))
				v = v ushr 7
			}
		}
	}

	private class ProtoCursor(private val data: ByteArray) {
		private var pos = 0

		fun hasRemaining(): Boolean = pos < data.size

		fun readVarint32(): Int = readVarint64().toInt()

		fun readVarint64(): Long {
			var result = 0L
			var shift = 0
			while (pos < data.size && shift < 64) {
				val b = data[pos++].toInt()
				result = result or ((b and 0x7F).toLong() shl shift)
				if ((b and 0x80) == 0) return result
				shift += 7
			}
			return result
		}

		fun readBytes(): ByteArray {
			val len = readVarint32().coerceAtLeast(0)
			val safeEnd = (pos + len).coerceAtMost(data.size)
			val slice = data.copyOfRange(pos, safeEnd)
			pos = safeEnd
			return slice
		}

		fun readString(): String = String(readBytes(), Charsets.UTF_8)

		fun readFloat(): Float {
			if (pos + 4 > data.size) {
				pos = data.size
				return 0f
			}
			val bits = (data[pos].toInt() and 0xFF) or
				((data[pos + 1].toInt() and 0xFF) shl 8) or
				((data[pos + 2].toInt() and 0xFF) shl 16) or
				((data[pos + 3].toInt() and 0xFF) shl 24)
			pos += 4
			return java.lang.Float.intBitsToFloat(bits)
		}

		fun skipField(wireType: Int) {
			when (wireType) {
				0 -> readVarint64()
				1 -> pos = (pos + 8).coerceAtMost(data.size)
				2 -> {
					val len = readVarint32().coerceAtLeast(0)
					pos = (pos + len).coerceAtMost(data.size)
				}
				5 -> pos = (pos + 4).coerceAtMost(data.size)
				else -> pos = data.size
			}
		}
	}
}
