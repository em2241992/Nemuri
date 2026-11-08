package org.koitharu.kotatsu.backups

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.HistoryEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.db.entity.TitleNormalizer
import org.koitharu.kotatsu.core.db.entity.TrackEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

enum class BackupFormat(val label: String, val extension: String) {
	KOTATSU_BAK("Kotatsu Archive (.bak / .zip)", "bak"),
	MIHON_TACHIBK("Mihon / Tachiyomi Protobuf (.tachibk)", "tachibk"),
	MIHON_JSON("Portable Backup (.json)", "json"),
}

data class BackupRestoreSummary(
	val format: BackupFormat,
	val mangaRestored: Int,
	val categoriesRestored: Int,
	val chaptersRestored: Int,
	val historyRestored: Int,
	val duplicatesSwappedOrMerged: Int,
	val unmappedMihonSourcesFlaggedForAiFix: Int,
	val detailsLog: List<String>,
)

/**
 * Pure Kotlin Protobuf Wire-Format Encoder & Decoder for Mihon / Tachiyomi `.tachibk` (GZIP Protobuf) backups.
 * Implements the official Tachiyomi/Mihon Backup proto schema:
 * - Backup: 1=repeated BackupManga, 2=repeated BackupCategory, 101=repeated BackupSource
 * - BackupManga: 1=source(int64), 2=url, 3=title, 4=artist, 5=author, 6=description, 7=genre(repeated),
 *                9=thumbnailUrl, 13=dateAdded, 16=chapters(repeated), 17=categories(repeated)
 * - BackupChapter: 1=url, 2=name, 3=scanlator, 4=read(bool), 6=lastPageRead(int64), 8=dateUpload(int64), 9=chapterNumber(float)
 * - BackupCategory: 1=name, 2=order(int64)
 * - BackupSource: 1=name, 2=sourceId(int64)
 */
object TachibkProtobufCodec {

	data class ProtoChapter(
		val url: String = "",
		val name: String = "",
		val scanlator: String = "",
		val read: Boolean = false,
		val lastPageRead: Int = 0,
		val dateUpload: Long = 0L,
		val chapterNumber: Float = 1f,
	)

	data class ProtoManga(
		val sourceId: Long = 0L,
		val url: String = "",
		val title: String = "",
		val author: String = "",
		val description: String = "",
		val genres: List<String> = emptyList(),
		val thumbnailUrl: String = "",
		val dateAdded: Long = System.currentTimeMillis(),
		val chapters: List<ProtoChapter> = emptyList(),
		val categories: List<Long> = emptyList(),
	)

	data class ProtoCategory(
		val name: String = "",
		val order: Long = 0L,
	)

	data class ProtoSource(
		val name: String = "",
		val sourceId: Long = 0L,
	)

	data class ProtoBackup(
		val mangaList: List<ProtoManga>,
		val categories: List<ProtoCategory>,
		val sources: List<ProtoSource>,
	)

	fun encodeGzipped(backup: ProtoBackup): ByteArray {
		val rawProto = encodeBackup(backup)
		val bos = ByteArrayOutputStream()
		GZIPOutputStream(bos).use { gzip ->
			gzip.write(rawProto)
		}
		return bos.toByteArray()
	}

	fun decodeGzippedOrRaw(bytes: ByteArray): ProtoBackup {
		val rawBytes = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
			GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
		} else {
			bytes
		}
		return decodeBackup(rawBytes)
	}

	private fun encodeBackup(backup: ProtoBackup): ByteArray {
		val out = ByteArrayOutputStream()
		for (manga in backup.mangaList) {
			writeBytesField(out, 1, encodeManga(manga))
		}
		for (cat in backup.categories) {
			writeBytesField(out, 2, encodeCategory(cat))
		}
		for (src in backup.sources) {
			writeBytesField(out, 101, encodeSource(src))
		}
		return out.toByteArray()
	}

	private fun encodeManga(m: ProtoManga): ByteArray {
		val out = ByteArrayOutputStream()
		writeVarintField(out, 1, m.sourceId)
		writeStringField(out, 2, m.url)
		writeStringField(out, 3, m.title)
		writeStringField(out, 5, m.author)
		writeStringField(out, 6, m.description)
		for (g in m.genres) {
			writeStringField(out, 7, g)
		}
		writeStringField(out, 9, m.thumbnailUrl)
		writeVarintField(out, 13, m.dateAdded)
		for (ch in m.chapters) {
			writeBytesField(out, 16, encodeChapter(ch))
		}
		for (catOrder in m.categories) {
			writeVarintField(out, 17, catOrder)
		}
		writeVarintField(out, 100, 1L)
		return out.toByteArray()
	}

	private fun encodeChapter(c: ProtoChapter): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, c.url)
		writeStringField(out, 2, c.name)
		if (c.scanlator.isNotBlank()) writeStringField(out, 3, c.scanlator)
		writeVarintField(out, 4, if (c.read) 1L else 0L)
		writeVarintField(out, 6, c.lastPageRead.toLong())
		writeVarintField(out, 8, c.dateUpload)
		writeFloatField(out, 9, c.chapterNumber)
		return out.toByteArray()
	}

	private fun encodeCategory(cat: ProtoCategory): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, cat.name)
		writeVarintField(out, 2, cat.order)
		return out.toByteArray()
	}

	private fun encodeSource(src: ProtoSource): ByteArray {
		val out = ByteArrayOutputStream()
		writeStringField(out, 1, src.name)
		writeVarintField(out, 2, src.sourceId)
		return out.toByteArray()
	}

	private fun decodeBackup(bytes: ByteArray): ProtoBackup {
		val mangaList = mutableListOf<ProtoManga>()
		val categories = mutableListOf<ProtoCategory>()
		val sources = mutableListOf<ProtoSource>()
		val reader = ProtoReader(bytes)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint().toInt()
			if (tag == 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when (fieldNumber) {
				1 -> if (wireType == 2) mangaList.add(decodeManga(reader.readBytes())) else reader.skip(wireType)
				2 -> if (wireType == 2) categories.add(decodeCategory(reader.readBytes())) else reader.skip(wireType)
				100, 101 -> if (wireType == 2) sources.add(decodeSource(reader.readBytes())) else reader.skip(wireType)
				else -> reader.skip(wireType)
			}
		}
		return ProtoBackup(mangaList, categories, sources)
	}

	private fun decodeManga(bytes: ByteArray): ProtoManga {
		var sourceId = 0L
		var url = ""
		var title = ""
		var author = ""
		var description = ""
		val genres = mutableListOf<String>()
		var thumbnailUrl = ""
		var dateAdded = System.currentTimeMillis()
		val chapters = mutableListOf<ProtoChapter>()
		val categories = mutableListOf<Long>()
		val reader = ProtoReader(bytes)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint().toInt()
			if (tag == 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when (fieldNumber) {
				1 -> if (wireType == 0) sourceId = reader.readVarint() else reader.skip(wireType)
				2 -> if (wireType == 2) url = reader.readString() else reader.skip(wireType)
				3 -> if (wireType == 2) title = reader.readString() else reader.skip(wireType)
				4 -> if (wireType == 2) {
					val artist = reader.readString()
					if (author.isBlank()) author = artist
				} else reader.skip(wireType)
				5 -> if (wireType == 2) author = reader.readString() else reader.skip(wireType)
				6 -> if (wireType == 2) description = reader.readString() else reader.skip(wireType)
				7 -> if (wireType == 2) genres.add(reader.readString()) else reader.skip(wireType)
				9 -> if (wireType == 2) thumbnailUrl = reader.readString() else reader.skip(wireType)
				13 -> if (wireType == 0) dateAdded = reader.readVarint() else reader.skip(wireType)
				16 -> if (wireType == 2) chapters.add(decodeChapter(reader.readBytes())) else reader.skip(wireType)
				17 -> if (wireType == 0) {
					categories.add(reader.readVarint())
				} else if (wireType == 2) {
					val sub = ProtoReader(reader.readBytes())
					while (sub.hasRemaining()) categories.add(sub.readVarint())
				} else reader.skip(wireType)
				else -> reader.skip(wireType)
			}
		}
		return ProtoManga(
			sourceId = sourceId,
			url = url,
			title = title,
			author = author,
			description = description,
			genres = genres,
			thumbnailUrl = thumbnailUrl,
			dateAdded = dateAdded,
			chapters = chapters,
			categories = categories,
		)
	}

	private fun decodeChapter(bytes: ByteArray): ProtoChapter {
		var url = ""
		var name = ""
		var scanlator = ""
		var read = false
		var lastPageRead = 0
		var dateUpload = 0L
		var chapterNumber = 1f
		val reader = ProtoReader(bytes)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint().toInt()
			if (tag == 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when (fieldNumber) {
				1 -> if (wireType == 2) url = reader.readString() else reader.skip(wireType)
				2 -> if (wireType == 2) name = reader.readString() else reader.skip(wireType)
				3 -> if (wireType == 2) scanlator = reader.readString() else reader.skip(wireType)
				4 -> if (wireType == 0) read = reader.readVarint() != 0L else reader.skip(wireType)
				6 -> if (wireType == 0) lastPageRead = reader.readVarint().toInt() else reader.skip(wireType)
				8 -> if (wireType == 0) dateUpload = reader.readVarint() else reader.skip(wireType)
				9 -> if (wireType == 5) chapterNumber = reader.readFloat() else reader.skip(wireType)
				else -> reader.skip(wireType)
			}
		}
		return ProtoChapter(url, name, scanlator, read, lastPageRead, dateUpload, chapterNumber)
	}

	private fun decodeCategory(bytes: ByteArray): ProtoCategory {
		var name = ""
		var order = 0L
		val reader = ProtoReader(bytes)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint().toInt()
			if (tag == 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when (fieldNumber) {
				1 -> if (wireType == 2) name = reader.readString() else reader.skip(wireType)
				2 -> if (wireType == 0) order = reader.readVarint() else reader.skip(wireType)
				else -> reader.skip(wireType)
			}
		}
		return ProtoCategory(name, order)
	}

	private fun decodeSource(bytes: ByteArray): ProtoSource {
		var name = ""
		var sourceId = 0L
		val reader = ProtoReader(bytes)
		while (reader.hasRemaining()) {
			val tag = reader.readVarint().toInt()
			if (tag == 0) break
			val fieldNumber = tag ushr 3
			val wireType = tag and 0x07
			when (fieldNumber) {
				1 -> if (wireType == 2) name = reader.readString() else reader.skip(wireType)
				2 -> if (wireType == 0) sourceId = reader.readVarint() else reader.skip(wireType)
				else -> reader.skip(wireType)
			}
		}
		return ProtoSource(name, sourceId)
	}

	private fun writeVarintField(out: OutputStream, fieldNumber: Int, value: Long) {
		writeVarint(out, ((fieldNumber shl 3) or 0).toLong())
		writeVarint(out, value)
	}

	private fun writeStringField(out: OutputStream, fieldNumber: Int, value: String) {
		writeBytesField(out, fieldNumber, value.toByteArray(Charsets.UTF_8))
	}

	private fun writeBytesField(out: OutputStream, fieldNumber: Int, bytes: ByteArray) {
		writeVarint(out, ((fieldNumber shl 3) or 2).toLong())
		writeVarint(out, bytes.size.toLong())
		out.write(bytes)
	}

	private fun writeFloatField(out: OutputStream, fieldNumber: Int, value: Float) {
		writeVarint(out, ((fieldNumber shl 3) or 5).toLong())
		val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
		out.write(buf)
	}

	private fun writeVarint(out: OutputStream, value: Long) {
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

	private class ProtoReader(private val data: ByteArray) {
		private var pos = 0

		fun hasRemaining(): Boolean = pos < data.size

		fun readVarint(): Long {
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
			val length = readVarint().toInt().coerceAtLeast(0).coerceAtMost(data.size - pos)
			val slice = data.copyOfRange(pos, pos + length)
			pos += length
			return slice
		}

		fun readString(): String = readBytes().toString(Charsets.UTF_8)

		fun readFloat(): Float {
			if (pos + 4 > data.size) {
				pos = data.size
				return 0f
			}
			val f = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float
			pos += 4
			return f
		}

		fun skip(wireType: Int) {
			when (wireType) {
				0 -> readVarint()
				1 -> pos = (pos + 8).coerceAtMost(data.size)
				2 -> {
					val len = readVarint().toInt().coerceAtLeast(0)
					pos = (pos + len).coerceAtMost(data.size)
				}
				5 -> pos = (pos + 4).coerceAtMost(data.size)
				else -> pos = data.size
			}
		}
	}
}

class UniversalBackupRepository(
	private val db: MangaDatabase,
) {
	private val knownMihonSourceNames = mapOf(
		2499283573021220255L to "EXT:MangaDex (Mihon EN)",
		6905824949201189121L to "EXT:ComicK (Mihon Multi)",
		4102938475610293847L to "EXT:Bato.to (Mihon EN)",
		8819203948571029384L to "EXT:Webtoons (Mihon EN)",
		7999000011112222333L to "DEAD:LegacyMangaRock (Broken)",
	)

	/**
	 * Creates a Kotatsu `.bak` (ZIP archive containing favourites.json, categories.json, history.json, chapters.json).
	 */
	suspend fun exportKotatsuBak(): ByteArray {
		val bos = ByteArrayOutputStream()
		val allManga = db.getMangaDao().getAllWithTags()
		val categories = db.getFavouritesDao().getCategories()
		val favourites = db.getFavouritesDao().getAllFavourites()
		val history = db.getHistoryDao().getAllHistory()
		val chapters = db.getChaptersDao().getAllChapters()

		ZipOutputStream(bos).use { zip ->
			// 1. categories
			val catArray = JSONArray()
			for (cat in categories) {
				catArray.put(
					JSONObject().apply {
						put("category_id", cat.categoryId)
						put("created_at", cat.createdAt)
						put("sort_key", cat.sortKey)
						put("title", cat.title)
						put("order", cat.order)
						put("track", cat.track)
						put("show_in_lib", cat.isVisibleInLibrary)
					},
				)
			}
			writeZipEntry(zip, "categories", catArray.toString())

			// 2. favourites (with embedded manga)
			val mangaMap = allManga.associateBy { it.manga.id }
			val favArray = JSONArray()
			for (fav in favourites) {
				val mwt = mangaMap[fav.mangaId] ?: continue
				favArray.put(
					JSONObject().apply {
						put("manga_id", fav.mangaId)
						put("category_id", fav.categoryId)
						put("created_at", fav.createdAt)
						put("sort_key", fav.sortKey)
						put("manga", encodeKotatsuMangaJson(mwt.manga, mwt.tags))
					},
				)
			}
			writeZipEntry(zip, "favourites", favArray.toString())

			// 3. history
			val histArray = JSONArray()
			for (h in history) {
				val mwt = mangaMap[h.mangaId] ?: continue
				histArray.put(
					JSONObject().apply {
						put("manga_id", h.mangaId)
						put("created_at", h.createdAt)
						put("updated_at", h.updatedAt)
						put("chapter_id", h.chapterId)
						put("page", h.page)
						put("scroll", h.scroll.toDouble())
						put("percent", h.percent.toDouble())
						put("chapters_count", h.chaptersCount)
						put("manga", encodeKotatsuMangaJson(mwt.manga, mwt.tags))
					},
				)
			}
			writeZipEntry(zip, "history", histArray.toString())

			// 4. chapters
			val chArray = JSONArray()
			for (ch in chapters) {
				chArray.put(
					JSONObject().apply {
						put("chapter_id", ch.chapterId)
						put("manga_id", ch.mangaId)
						put("title", ch.title)
						put("number", ch.number.toDouble())
						put("url", ch.url)
						put("is_read", ch.isRead)
						put("last_page_read", ch.lastPageRead)
					},
				)
			}
			writeZipEntry(zip, "chapters", chArray.toString())

			// 5. bookmarks
			val bookmarks = db.getAuxiliaryDao().getAllBookmarks()
			val bmArray = JSONArray()
			for (bm in bookmarks) {
				bmArray.put(
					JSONObject().apply {
						put("manga_id", bm.mangaId)
						put("page_id", bm.pageId)
						put("chapter_id", bm.chapterId)
						put("page", bm.page)
						put("image_url", bm.imageUrl)
						put("created_at", bm.createdAt)
						put("percent", bm.percent.toDouble())
					},
				)
			}
			writeZipEntry(zip, "bookmarks", bmArray.toString())
		}
		return bos.toByteArray()
	}

	/**
	 * Exports the entire library to a Mihon/Tachiyomi `.tachibk` (GZIP-compressed Protobuf) binary.
	 */
	suspend fun exportMihonTachibk(): ByteArray {
		val allManga = db.getMangaDao().getAllWithTags()
		val categories = db.getFavouritesDao().getCategories()
		val favourites = db.getFavouritesDao().getAllFavourites().groupBy { it.mangaId }
		val chaptersByManga = db.getChaptersDao().getAllChapters().groupBy { it.mangaId }

		val protoCategories = categories.mapIndexed { idx, cat ->
			TachibkProtobufCodec.ProtoCategory(
				name = cat.title,
				order = (idx + 1).toLong(),
			)
		}
		val catIdToOrder = categories.mapIndexed { idx, cat -> cat.categoryId.toLong() to (idx + 1).toLong() }.toMap()

		val sourcesMap = LinkedHashMap<Long, String>()
		val protoMangaList = allManga.map { mwt ->
			val m = mwt.manga
			val sourceId = abs(m.source.hashCode().toLong()).coerceAtLeast(1000L)
			sourcesMap[sourceId] = m.source
			val mChapters = chaptersByManga[m.id].orEmpty().map { ch ->
				TachibkProtobufCodec.ProtoChapter(
					url = ch.url,
					name = ch.title,
					scanlator = ch.scanlator.orEmpty(),
					read = ch.isRead,
					lastPageRead = ch.lastPageRead,
					dateUpload = ch.uploadDate,
					chapterNumber = ch.number,
				)
			}
			val mCats = favourites[m.id].orEmpty().mapNotNull { catIdToOrder[it.categoryId] }
			TachibkProtobufCodec.ProtoManga(
				sourceId = sourceId,
				url = m.url,
				title = m.title,
				author = m.authors.orEmpty(),
				description = m.description,
				genres = mwt.tags.map { it.title },
				thumbnailUrl = m.coverUrl,
				dateAdded = favourites[m.id]?.firstOrNull()?.createdAt ?: System.currentTimeMillis(),
				chapters = mChapters,
				categories = mCats,
			)
		}

		val protoSources = sourcesMap.map { (id, name) ->
			TachibkProtobufCodec.ProtoSource(name = name, sourceId = id)
		}

		return TachibkProtobufCodec.encodeGzipped(
			TachibkProtobufCodec.ProtoBackup(
				mangaList = protoMangaList,
				categories = protoCategories,
				sources = protoSources,
			),
		)
	}

	/**
	 * Exports the library to a Mihon/Neko portable `.json` backup.
	 */
	suspend fun exportMihonJson(): String {
		val allManga = db.getMangaDao().getAllWithTags()
		val categories = db.getFavouritesDao().getCategories()
		val favourites = db.getFavouritesDao().getAllFavourites().groupBy { it.mangaId }
		val chaptersByManga = db.getChaptersDao().getAllChapters().groupBy { it.mangaId }

		val root = JSONObject()
		val catArray = JSONArray()
		categories.forEachIndexed { idx, cat ->
			catArray.put(
				JSONObject().apply {
					put("name", cat.title)
					put("order", idx + 1)
				},
			)
		}
		root.put("backupCategories", catArray)

		val catIdToOrder = categories.mapIndexed { idx, cat -> cat.categoryId.toLong() to (idx + 1).toLong() }.toMap()
		val sourcesArray = JSONArray()
		val mangaArray = JSONArray()
		val addedSources = mutableSetOf<Long>()

		for (mwt in allManga) {
			val m = mwt.manga
			val sourceId = abs(m.source.hashCode().toLong()).coerceAtLeast(1000L)
			if (addedSources.add(sourceId)) {
				sourcesArray.put(
					JSONObject().apply {
						put("name", m.source)
						put("sourceId", sourceId)
					},
				)
			}
			val chArray = JSONArray()
			for (ch in chaptersByManga[m.id].orEmpty()) {
				chArray.put(
					JSONObject().apply {
						put("url", ch.url)
						put("name", ch.title)
						put("chapterNumber", ch.number.toDouble())
						put("read", ch.isRead)
						put("lastPageRead", ch.lastPageRead)
						put("scanlator", ch.scanlator.orEmpty())
					},
				)
			}
			val genres = JSONArray()
			mwt.tags.forEach { genres.put(it.title) }
			val cats = JSONArray()
			favourites[m.id].orEmpty().forEach { fav ->
				cats.put(catIdToOrder[fav.categoryId] ?: fav.categoryId)
			}

			mangaArray.put(
				JSONObject().apply {
					put("source", sourceId)
					put("url", m.url)
					put("title", m.title)
					put("author", m.authors.orEmpty())
					put("description", m.description)
					put("thumbnailUrl", m.coverUrl)
					put("genre", genres)
					put("chapters", chArray)
					put("categories", cats)
				},
			)
		}
		root.put("backupSources", sourcesArray)
		root.put("backupManga", mangaArray)
		return root.toString(2)
	}

	/**
	 * Auto-detects backup format (.bak ZIP, .tachibk Protobuf GZIP, or .json) and restores to Kotatsu v28 DB
	 * with optional Check-and-Swap deduplication. Resilient against empty or malformed payloads.
	 */
	suspend fun restoreUniversalBackup(
		bytes: ByteArray,
		fileNameHint: String = "",
		checkAndSwapDuplicates: Boolean = true,
	): BackupRestoreSummary {
		if (bytes.isEmpty()) {
			return BackupRestoreSummary(
				format = BackupFormat.KOTATSU_BAK,
				mangaRestored = 0,
				categoriesRestored = 0,
				chaptersRestored = 0,
				historyRestored = 0,
				duplicatesSwappedOrMerged = 0,
				unmappedMihonSourcesFlaggedForAiFix = 0,
				detailsLog = listOf("Empty backup payload provided; 0 items restored."),
			)
		}
		val lowerName = fileNameHint.lowercase()
		return runCatching {
			// 1. Check ZIP magic bytes (PK\x03\x04 -> 0x50, 0x4B) for Kotatsu .bak / .zip
			if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
				return@runCatching restoreKotatsuBakZip(bytes, checkAndSwapDuplicates)
			}
			// 2. Check JSON text (starts with '{' or '[')
			val trimmedFirstByte = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }
			if (lowerName.endsWith(".json") || trimmedFirstByte == '{'.code.toByte() || trimmedFirstByte == '['.code.toByte()) {
				return@runCatching restoreMihonJson(bytes.decodeToString(), checkAndSwapDuplicates)
			}
			// 3. Otherwise decode as Mihon .tachibk (GZIP or raw Protobuf)
			restoreMihonTachibk(bytes, checkAndSwapDuplicates)
		}.getOrElse { err ->
			BackupRestoreSummary(
				format = BackupFormat.MIHON_TACHIBK,
				mangaRestored = 0,
				categoriesRestored = 0,
				chaptersRestored = 0,
				historyRestored = 0,
				duplicatesSwappedOrMerged = 0,
				unmappedMihonSourcesFlaggedForAiFix = 0,
				detailsLog = listOf("Skipped malformed backup stream (${err.javaClass.simpleName}): ${err.message.orEmpty()}"),
			)
		}
	}

	private suspend fun restoreKotatsuBakZip(
		bytes: ByteArray,
		checkAndSwapDuplicates: Boolean,
	): BackupRestoreSummary {
		var mangaCount = 0
		var catCount = 0
		var chCount = 0
		var histCount = 0
		var swappedCount = 0
		val logs = mutableListOf<String>()

		db.withTransaction {
			ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
				var entry: ZipEntry? = zip.nextEntry
				while (entry != null) {
					val content = zip.readBytes().decodeToString()
					when (entry.name.substringBefore('.')) {
						"categories" -> {
							val arr = JSONArray(content)
							for (i in 0 until arr.length()) {
								val jo = arr.getJSONObject(i)
								db.getFavouritesDao().upsertCategory(
									FavouriteCategoryEntity(
										categoryId = jo.optInt("category_id", i + 1),
										createdAt = jo.optLong("created_at", System.currentTimeMillis()),
										sortKey = jo.optInt("sort_key", i),
										title = jo.optString("title", "Imported Category"),
										order = jo.optString("order", "NEWEST"),
										track = jo.optBoolean("track", true),
										isVisibleInLibrary = jo.optBoolean("show_in_lib", true),
									),
								)
								catCount++
							}
						}
						"favourites" -> {
							val arr = JSONArray(content)
							for (i in 0 until arr.length()) {
								val jo = arr.getJSONObject(i)
								val mJson = jo.optJSONObject("manga") ?: continue
								val parsed = parseKotatsuMangaJson(mJson)
								val existingDups = if (checkAndSwapDuplicates) {
									db.getMangaDao().findByNormalizedTitle(parsed.first.normalizedTitle)
								} else emptyList()

								val targetId = if (existingDups.isNotEmpty() && existingDups.first().manga.id != parsed.first.id) {
									swappedCount++
									logs.add("Check & Swap merged '${parsed.first.title}' into existing library entry.")
									existingDups.first().manga.id
								} else {
									db.getMangaDao().upsert(parsed.first, parsed.second)
									mangaCount++
									parsed.first.id
								}
								db.getFavouritesDao().upsert(
									FavouriteEntity(
										mangaId = targetId,
										categoryId = jo.optLong("category_id", 1L),
										sortKey = jo.optInt("sort_key", i),
										createdAt = jo.optLong("created_at", System.currentTimeMillis()),
									),
								)
							}
						}
						"history" -> {
							val arr = JSONArray(content)
							for (i in 0 until arr.length()) {
								val jo = arr.getJSONObject(i)
								val mJson = jo.optJSONObject("manga")
								if (mJson != null) {
									val parsed = parseKotatsuMangaJson(mJson)
									db.getMangaDao().upsert(parsed.first, parsed.second)
								}
								db.getHistoryDao().upsert(
									HistoryEntity(
										mangaId = jo.getLong("manga_id"),
										createdAt = jo.optLong("created_at", System.currentTimeMillis()),
										updatedAt = jo.optLong("updated_at", System.currentTimeMillis()),
										chapterId = jo.optLong("chapter_id", 1L),
										page = jo.optInt("page", 0),
										scroll = jo.optDouble("scroll", 0.0).toFloat(),
										percent = jo.optDouble("percent", 0.0).toFloat(),
										chaptersCount = jo.optInt("chapters_count", 1),
									),
								)
								histCount++
							}
						}
						"chapters" -> {
							val arr = JSONArray(content)
							val list = mutableListOf<ChapterEntity>()
							for (i in 0 until arr.length()) {
								val jo = arr.getJSONObject(i)
								list.add(
									ChapterEntity(
										chapterId = jo.getLong("chapter_id"),
										mangaId = jo.getLong("manga_id"),
										title = jo.optString("title", "Chapter ${i + 1}"),
										number = jo.optDouble("number", (i + 1).toDouble()).toFloat(),
										volume = 0,
										url = jo.optString("url", "/ch/${i + 1}"),
										scanlator = null,
										uploadDate = System.currentTimeMillis(),
										branch = null,
										index = i,
										isRead = jo.optBoolean("is_read", false),
										lastPageRead = jo.optInt("last_page_read", 0),
									),
								)
								chCount++
							}
							db.getChaptersDao().upsertAll(list)
						}
						"bookmarks" -> {
							val arr = JSONArray(content)
							for (i in 0 until arr.length()) {
								val jo = arr.getJSONObject(i)
								db.getAuxiliaryDao().upsertBookmark(
									org.koitharu.kotatsu.core.db.entity.BookmarkEntity(
										mangaId = jo.getLong("manga_id"),
										pageId = jo.optLong("page_id", i.toLong()),
										chapterId = jo.optLong("chapter_id", 1L),
										page = jo.optInt("page", 0),
										imageUrl = jo.optString("image_url", "studio_page://bookmark/$i"),
										createdAt = jo.optLong("created_at", System.currentTimeMillis()),
										percent = jo.optDouble("percent", 0.0).toFloat(),
									),
								)
							}
						}
					}
					zip.closeEntry()
					entry = zip.nextEntry
				}
			}
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
		logs.add(0, "Restored Kotatsu .bak ZIP archive ($mangaCount manga, $catCount categories, $chCount chapters).")
		return BackupRestoreSummary(
			format = BackupFormat.KOTATSU_BAK,
			mangaRestored = mangaCount,
			categoriesRestored = catCount,
			chaptersRestored = chCount,
			historyRestored = histCount,
			duplicatesSwappedOrMerged = swappedCount,
			unmappedMihonSourcesFlaggedForAiFix = 0,
			detailsLog = logs,
		)
	}

	private suspend fun restoreMihonTachibk(
		bytes: ByteArray,
		checkAndSwapDuplicates: Boolean,
	): BackupRestoreSummary {
		val protoBackup = TachibkProtobufCodec.decodeGzippedOrRaw(bytes)
		var mangaCount = 0
		var catCount = 0
		var chCount = 0
		var histCount = 0
		var swappedCount = 0
		var unmappedCount = 0
		val logs = mutableListOf<String>()

		val sourceLookup = protoBackup.sources.associate { it.sourceId to it.name }.toMutableMap()
		knownMihonSourceNames.forEach { (id, name) -> sourceLookup.putIfAbsent(id, name) }

		db.withTransaction {
			val orderToCategoryId = mutableMapOf<Long, Long>()
			for ((idx, protoCat) in protoBackup.categories.withIndex()) {
				val catId = (idx + 10)
				db.getFavouritesDao().upsertCategory(
					FavouriteCategoryEntity(
						categoryId = catId,
						createdAt = System.currentTimeMillis(),
						sortKey = idx,
						title = protoCat.name.ifBlank { "Mihon Category ${idx + 1}" },
						order = "LAST_READ",
						track = true,
						isVisibleInLibrary = true,
					),
				)
				orderToCategoryId[protoCat.order] = catId.toLong()
				catCount++
			}

			for ((mIdx, pm) in protoBackup.mangaList.withIndex()) {
				val resolvedSource = sourceLookup[pm.sourceId] ?: "MIHON_UNMAPPED_${pm.sourceId}"
				val isBrokenSource = resolvedSource.startsWith("DEAD:") || resolvedSource.startsWith("MIHON_UNMAPPED_")
				if (isBrokenSource) unmappedCount++

				val normTitle = TitleNormalizer.normalize(pm.title)
				val existingMatches = if (checkAndSwapDuplicates) {
					db.getMangaDao().findByNormalizedTitle(normTitle)
				} else emptyList()

				val mangaId = if (existingMatches.isNotEmpty()) {
					val existing = existingMatches.first()
					// If existing is broken and imported is healthy, swap source!
					if (existing.manga.isBroken && !isBrokenSource) {
						db.getMangaDao().upsertManga(
							existing.manga.copy(
								source = resolvedSource,
								url = pm.url,
								isBroken = false,
								healthError = null,
							),
						)
						logs.add("Check & Swap: Repaired '${pm.title}' using healthy Mihon source $resolvedSource.")
					} else {
						logs.add("Check & Swap: Merged chapters/progress for duplicate '${pm.title}'.")
					}
					swappedCount++
					existing.manga.id
				} else {
					val newId = abs((pm.title + resolvedSource).hashCode().toLong()).coerceAtLeast(10_000L) + mIdx
					val tags = pm.genres.map { genre ->
						TagEntity(
							id = abs((genre + resolvedSource).hashCode().toLong()).coerceAtLeast(1L),
							title = genre,
							key = genre.lowercase(),
							source = resolvedSource,
						)
					}
					db.getMangaDao().upsert(
						MangaEntity(
							id = newId,
							title = pm.title,
							altTitles = null,
							url = pm.url,
							publicUrl = pm.url,
							rating = 0.92f,
							isNsfw = false,
							contentRating = "SAFE",
							coverUrl = pm.thumbnailUrl,
							largeCoverUrl = pm.thumbnailUrl,
							state = "ONGOING",
							authors = pm.author,
							source = resolvedSource,
							description = pm.description,
							normalizedTitle = normTitle,
							isBroken = isBrokenSource,
							healthError = if (isBrokenSource) "Unmapped Mihon Source ID (${pm.sourceId}) — Ready for AI Fix-It" else null,
						),
						tags = tags,
					)
					mangaCount++
					newId
				}

				val targetCats = pm.categories.mapNotNull { orderToCategoryId[it] }.ifEmpty { listOf(1L) }
				for (catId in targetCats) {
					db.getFavouritesDao().upsert(
						FavouriteEntity(
							mangaId = mangaId,
							categoryId = catId,
							sortKey = mIdx,
							createdAt = pm.dateAdded,
						),
					)
				}

				val chapterEntities = pm.chapters.mapIndexed { cIdx, ch ->
					ChapterEntity(
						chapterId = mangaId * 1000L + cIdx + 1L,
						mangaId = mangaId,
						title = ch.name.ifBlank { "Chapter ${ch.chapterNumber.toInt()}" },
						number = ch.chapterNumber,
						volume = 0,
						url = ch.url.ifBlank { "/manga/$mangaId/ch/${cIdx + 1}" },
						scanlator = ch.scanlator.ifBlank { "Mihon Import" },
						uploadDate = if (ch.dateUpload > 0) ch.dateUpload else System.currentTimeMillis(),
						branch = null,
						index = cIdx,
						isRead = ch.read,
						lastPageRead = ch.lastPageRead,
					)
				}
				if (chapterEntities.isNotEmpty()) {
					db.getChaptersDao().upsertAll(chapterEntities)
					chCount += chapterEntities.size
					val readCount = chapterEntities.count { it.isRead }
					val lastReadCh = chapterEntities.lastOrNull { it.isRead } ?: chapterEntities.first()
					db.getHistoryDao().upsert(
						HistoryEntity(
							mangaId = mangaId,
							createdAt = pm.dateAdded,
							updatedAt = System.currentTimeMillis() - (mIdx * 60_000L),
							chapterId = lastReadCh.chapterId,
							page = lastReadCh.lastPageRead,
							scroll = 0f,
							percent = (readCount.toFloat() / chapterEntities.size.toFloat()).coerceIn(0f, 1f),
							chaptersCount = chapterEntities.size,
						),
					)
					db.getAuxiliaryDao().upsertTrack(
						TrackEntity(
							mangaId = mangaId,
							totalChapters = chapterEntities.size,
							lastChapterId = chapterEntities.last().chapterId,
							newChapters = (chapterEntities.size - readCount).coerceAtLeast(0),
							lastCheckTime = System.currentTimeMillis(),
							lastChapterDate = System.currentTimeMillis(),
						),
					)
					histCount++
				}
			}
			db.openHelper.writableDatabase.execSQL("INSERT INTO manga_fts(manga_fts) VALUES('rebuild')")
		}
		logs.add(
			0,
			"Decoded Mihon .tachibk Protobuf archive: $mangaCount added, $swappedCount merged via Check & Swap, $chCount chapters restored.",
		)
		return BackupRestoreSummary(
			format = BackupFormat.MIHON_TACHIBK,
			mangaRestored = mangaCount,
			categoriesRestored = catCount,
			chaptersRestored = chCount,
			historyRestored = histCount,
			duplicatesSwappedOrMerged = swappedCount,
			unmappedMihonSourcesFlaggedForAiFix = unmappedCount,
			detailsLog = logs,
		)
	}

	private suspend fun restoreMihonJson(
		jsonString: String,
		checkAndSwapDuplicates: Boolean,
	): BackupRestoreSummary {
		val root = JSONObject(jsonString)
		val catArray = root.optJSONArray("backupCategories") ?: JSONArray()
		val srcArray = root.optJSONArray("backupSources") ?: JSONArray()
		val mangaArray = root.optJSONArray("backupManga") ?: JSONArray()

		val protoCats = (0 until catArray.length()).map { i ->
			val jo = catArray.getJSONObject(i)
			TachibkProtobufCodec.ProtoCategory(
				name = jo.optString("name", "Mihon JSON Category"),
				order = jo.optLong("order", (i + 1).toLong()),
			)
		}
		val protoSources = (0 until srcArray.length()).map { i ->
			val jo = srcArray.getJSONObject(i)
			TachibkProtobufCodec.ProtoSource(
				name = jo.optString("name", "EXT:MihonJSON"),
				sourceId = jo.optLong("sourceId", (i + 100).toLong()),
			)
		}
		val protoManga = (0 until mangaArray.length()).map { i ->
			val jo = mangaArray.getJSONObject(i)
			val genresArr = jo.optJSONArray("genre") ?: JSONArray()
			val genres = (0 until genresArr.length()).map { genresArr.getString(it) }
			val chArr = jo.optJSONArray("chapters") ?: JSONArray()
			val chapters = (0 until chArr.length()).map { cIdx ->
				val cJo = chArr.getJSONObject(cIdx)
				TachibkProtobufCodec.ProtoChapter(
					url = cJo.optString("url", "/ch/${cIdx + 1}"),
					name = cJo.optString("name", "Chapter ${cIdx + 1}"),
					scanlator = cJo.optString("scanlator", ""),
					read = cJo.optBoolean("read", false),
					lastPageRead = cJo.optInt("lastPageRead", 0),
					dateUpload = cJo.optLong("dateUpload", System.currentTimeMillis()),
					chapterNumber = cJo.optDouble("chapterNumber", (cIdx + 1).toDouble()).toFloat(),
				)
			}
			val catsArr = jo.optJSONArray("categories") ?: JSONArray()
			val cats = (0 until catsArr.length()).map { catsArr.optLong(it, 1L) }
			TachibkProtobufCodec.ProtoManga(
				sourceId = jo.optLong("source", 2499283573021220255L),
				url = jo.optString("url", "/manga/$i"),
				title = jo.optString("title", "Imported Manga $i"),
				author = jo.optString("author", "Unknown Author"),
				description = jo.optString("description", ""),
				genres = genres,
				thumbnailUrl = jo.optString("thumbnailUrl", ""),
				chapters = chapters,
				categories = cats,
			)
		}

		val encoded = TachibkProtobufCodec.encodeGzipped(
			TachibkProtobufCodec.ProtoBackup(protoManga, protoCats, protoSources),
		)
		val summary = restoreMihonTachibk(encoded, checkAndSwapDuplicates)
		return summary.copy(format = BackupFormat.MIHON_JSON)
	}

	/**
	 * Generates a realistic Mihon `.tachibk` (GZIP Protobuf) sample payload containing new series,
	 * a duplicate series (to trigger Check & Swap), and a legacy dead-source series (to test AI Fix-It).
	 */
	fun createSampleMihonTachibkPayload(): ByteArray {
		val backup = TachibkProtobufCodec.ProtoBackup(
			categories = listOf(
				TachibkProtobufCodec.ProtoCategory(name = "Mihon Seinen Vault", order = 1L),
				TachibkProtobufCodec.ProtoCategory(name = "Webtoon Action", order = 2L),
			),
			sources = listOf(
				TachibkProtobufCodec.ProtoSource(name = "EXT:MangaDex (Mihon EN)", sourceId = 2499283573021220255L),
				TachibkProtobufCodec.ProtoSource(name = "DEAD:LegacyMangaRock (Broken)", sourceId = 7999000011112222333L),
			),
			mangaList = listOf(
				TachibkProtobufCodec.ProtoManga(
					sourceId = 2499283573021220255L,
					url = "/manga/witch-hat-atelier-mihon",
					title = "Tongari Boushi no Atelier (Witch Hat Atelier)",
					author = "Kamome Shirahama",
					description = "In a world where everyone takes wonders like magic spells and dragons for granted, Coco is a girl with a simple dream: she wants to be a witch.",
					genres = listOf("Fantasy", "Seinen", "Magic", "Adventure"),
					thumbnailUrl = "studio_cover://witch_hat",
					chapters = (1..18).map { n ->
						TachibkProtobufCodec.ProtoChapter(
							url = "/ch/witch-$n",
							name = "Chapter $n: The Forbidden Ink",
							scanlator = "Atelier Scans",
							read = n <= 14,
							lastPageRead = if (n <= 14) 24 else 0,
							chapterNumber = n.toFloat(),
						)
					},
					categories = listOf(1L),
				),
				TachibkProtobufCodec.ProtoManga(
					sourceId = 7999000011112222333L,
					url = "https://dead-mangarock.example/series/vagabond-broken",
					title = "Vagabond [Broken Mihon Backup]",
					author = "Takehiko Inoue",
					description = "Shinmen Takezo is destined to become the legendary sword-saint Miyamoto Musashi. Imported from a defunct Mihon source.",
					genres = listOf("Historical", "Samurai", "Seinen"),
					thumbnailUrl = "studio_cover://vagabond",
					chapters = (1..12).map { n ->
						TachibkProtobufCodec.ProtoChapter(
							url = "/dead/vagabond-$n",
							name = "Chapter $n",
							scanlator = "LegacyBackup",
							read = n <= 8,
							lastPageRead = 19,
							chapterNumber = n.toFloat(),
						)
					},
					categories = listOf(1L),
				),
			),
		)
		return TachibkProtobufCodec.encodeGzipped(backup)
	}

	private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
		zip.putNextEntry(ZipEntry(name))
		zip.write(content.toByteArray(Charsets.UTF_8))
		zip.closeEntry()
	}

	private fun encodeKotatsuMangaJson(manga: MangaEntity, tags: List<TagEntity>): JSONObject {
		val tagArr = JSONArray()
		for (t in tags) {
			tagArr.put(
				JSONObject().apply {
					put("id", t.id)
					put("title", t.title)
					put("key", t.key)
					put("source", t.source)
				},
			)
		}
		return JSONObject().apply {
			put("id", manga.id)
			put("title", manga.title)
			put("alt_title", manga.altTitles)
			put("url", manga.url)
			put("public_url", manga.publicUrl)
			put("rating", manga.rating.toDouble())
			put("nsfw", manga.isNsfw)
			put("cover_url", manga.coverUrl)
			put("state", manga.state)
			put("author", manga.authors)
			put("source", manga.source)
			put("description", manga.description)
			put("tags", tagArr)
		}
	}

	private fun parseKotatsuMangaJson(jo: JSONObject): Pair<MangaEntity, List<TagEntity>> {
		val source = jo.optString("source", "EXT:KotatsuPlugin")
		val title = jo.optString("title", "Untitled")
		val tagsArr = jo.optJSONArray("tags") ?: JSONArray()
		val tags = (0 until tagsArr.length()).map { idx ->
			val tJo = tagsArr.getJSONObject(idx)
			TagEntity(
				id = tJo.optLong("id", (idx + 1).toLong()),
				title = tJo.optString("title", "Manga"),
				key = tJo.optString("key", "manga"),
				source = tJo.optString("source", source),
			)
		}
		val entity = MangaEntity(
			id = jo.getLong("id"),
			title = title,
			altTitles = jo.optString("alt_title").takeIf { it.isNotBlank() },
			url = jo.optString("url", ""),
			publicUrl = jo.optString("public_url", ""),
			rating = jo.optDouble("rating", 0.85).toFloat(),
			isNsfw = jo.optBoolean("nsfw", false),
			contentRating = "SAFE",
			coverUrl = jo.optString("cover_url", ""),
			largeCoverUrl = jo.optString("cover_url", ""),
			state = jo.optString("state", "ONGOING"),
			authors = jo.optString("author", ""),
			source = source,
			description = jo.optString("description", ""),
			normalizedTitle = TitleNormalizer.normalize(title),
		)
		return entity to tags
	}
}
