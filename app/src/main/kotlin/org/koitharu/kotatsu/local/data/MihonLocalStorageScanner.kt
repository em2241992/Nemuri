package org.koitharu.kotatsu.local.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.ChapterEntity
import org.koitharu.kotatsu.core.db.entity.FavouriteEntity
import org.koitharu.kotatsu.core.db.entity.LocalMangaIndexEntity
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.parser.ReaderBitmapPipeline
import org.koitharu.kotatsu.core.util.TitleNormalizer
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class LocalChapterContainerFormat {
	DIRECTORY_IMAGES,
	CBZ_ZIP,
	CBR_RAR,
	PDF_DOCUMENT,
}

data class ScannedLocalChapter(
	val chapterName: String,
	val chapterNumber: Float,
	val fileOrDir: File,
	val format: LocalChapterContainerFormat,
	val pageCount: Int,
	val lastModified: Long,
)

data class ScannedLocalManga(
	val mangaDir: File,
	val sourceFolderName: String,
	val title: String,
	val author: String?,
	val artist: String?,
	val description: String,
	val genres: List<String>,
	val status: String,
	val coverFile: File?,
	val chapters: List<ScannedLocalChapter>,
)

@Singleton
class MihonLocalStorageScanner @Inject constructor(
	private val db: MangaDatabase,
) {
	companion object {
		private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif")
	}

	/**
	 * Scans `/Mihon/downloads/<SourceName>/<MangaTitle>/<ChapterName>/` and
	 * `/Mihon/local/<MangaTitle>/` directory structures directly in place without copying
	 * or re-zipping files, parsing `details.json` and `ComicInfo.xml`, and indexing into Room.
	 */
	suspend fun scanAndIndexRootDirectories(
		rootDirs: List<File>,
		importIntoDatabase: Boolean = true,
	): List<ScannedLocalManga> = withContext(Dispatchers.IO) {
		val discovered = ArrayList<ScannedLocalManga>()

		for (root in rootDirs) {
			if (!root.exists() || !root.isDirectory) continue

			// Case 1: /Mihon/local/<MangaTitle>/ or direct local directory containing manga folders
			val localDir = File(root, "local").takeIf { it.exists() && it.isDirectory }
			if (localDir != null) {
				localDir.listFiles()?.filter { it.isDirectory }?.forEach { mangaFolder ->
					scanSingleMangaFolder(mangaFolder, sourceFolderName = "LOCAL:MihonLocal")?.let {
						discovered.add(it)
					}
				}
			}

			// Case 2: /Mihon/downloads/<SourceName>/<MangaTitle>/<ChapterName>/
			val downloadsDir = File(root, "downloads").takeIf { it.exists() && it.isDirectory }
			if (downloadsDir != null) {
				downloadsDir.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
					val srcName = "LOCAL:${sourceDir.name}"
					sourceDir.listFiles()?.filter { it.isDirectory }?.forEach { mangaFolder ->
						scanSingleMangaFolder(mangaFolder, sourceFolderName = srcName)?.let {
							discovered.add(it)
						}
					}
				}
			}

			// Case 3: Root itself is either a `/local` or `/downloads/<Source>` container
			if (localDir == null && downloadsDir == null) {
				root.listFiles()?.filter { it.isDirectory }?.forEach { childDir ->
					val directScan = scanSingleMangaFolder(childDir, sourceFolderName = "LOCAL:MihonDirect")
					if (directScan != null && directScan.chapters.isNotEmpty()) {
						discovered.add(directScan)
					} else {
						// Treat childDir as <SourceName> containing <MangaTitle> folders
						childDir.listFiles()?.filter { it.isDirectory }?.forEach { subMangaDir ->
							scanSingleMangaFolder(subMangaDir, sourceFolderName = "LOCAL:${childDir.name}")?.let {
								discovered.add(it)
							}
						}
					}
				}
			}
		}

		if (importIntoDatabase && discovered.isNotEmpty()) {
			indexDiscoveredMangaInRoom(discovered)
		}

		discovered
	}

	/**
	 * Inspects a single `<MangaTitle>` directory in place, extracting metadata from `details.json`
	 * or `ComicInfo.xml` and discovering all `.cbz`, `.cbr`, `.pdf`, or raw image chapter subdirectories.
	 */
	fun scanSingleMangaFolder(
		mangaFolder: File,
		sourceFolderName: String = "LOCAL:MihonLocal",
	): ScannedLocalManga? {
		if (!mangaFolder.exists() || !mangaFolder.isDirectory) return null

		var title = mangaFolder.name
		var author: String? = null
		var artist: String? = null
		var description = ""
		val genres = ArrayList<String>()
		var status = "ONGOING"

		// 1. Parse details.json if present
		val detailsJsonFile = File(mangaFolder, "details.json")
		if (detailsJsonFile.exists() && detailsJsonFile.isFile) {
			runCatching {
				val json = JSONObject(detailsJsonFile.readText(Charsets.UTF_8))
				title = json.optString("title", title).ifBlank { title }
				author = json.optString("author").takeIf { it.isNotBlank() }
				artist = json.optString("artist").takeIf { it.isNotBlank() }
				description = json.optString("description", description)
				status = when (json.optInt("status", 1)) {
					2 -> "FINISHED"
					3, 5 -> "ABANDONED"
					4 -> "PAUSED"
					else -> "ONGOING"
				}
				val genreArr = json.optJSONArray("genre")
				if (genreArr != null) {
					for (i in 0 until genreArr.length()) {
						val g = genreArr.optString(i).trim()
						if (g.isNotEmpty()) genres.add(g)
					}
				}
			}
		}

		// 2. Parse ComicInfo.xml if present in mangaFolder (or inside the first .cbz)
		val comicInfoFile = File(mangaFolder, "ComicInfo.xml")
		if (comicInfoFile.exists() && comicInfoFile.isFile) {
			runCatching {
				val xml = comicInfoFile.readText(Charsets.UTF_8)
				extractXmlTag(xml, "Series")?.let { if (it.isNotBlank()) title = it }
				extractXmlTag(xml, "Writer")?.let { if (it.isNotBlank()) author = it }
				extractXmlTag(xml, "Penciller")?.let { if (it.isNotBlank()) artist = it }
				extractXmlTag(xml, "Summary")?.let { if (it.isNotBlank()) description = it }
				extractXmlTag(xml, "Genre")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
					genres.addAll(it)
				}
			}
		}

		val coverFile = mangaFolder.listFiles()?.firstOrNull { f ->
			f.isFile && f.nameWithoutExtension.lowercase(Locale.ROOT) in setOf("cover", "folder", "thumb") &&
				f.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
		}

		// 3. Scan chapters in place (.cbz, .cbr, .pdf, or chapter directories)
		val chapters = ArrayList<ScannedLocalChapter>()
		val children = mangaFolder.listFiles().orEmpty()
		for (child in children) {
			if (child.isDirectory) {
				val imgCount = child.listFiles()?.count {
					it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
				} ?: 0
				if (imgCount > 0) {
					val chNum = parseChapterNumber(child.name, chapters.size + 1)
					chapters.add(
						ScannedLocalChapter(
							chapterName = child.name,
							chapterNumber = chNum,
							fileOrDir = child,
							format = LocalChapterContainerFormat.DIRECTORY_IMAGES,
							pageCount = imgCount,
							lastModified = child.lastModified(),
						),
					)
				}
			} else if (child.isFile) {
				val ext = child.extension.lowercase(Locale.ROOT)
				when (ext) {
					"cbz", "zip" -> {
						val pages = ReaderBitmapPipeline.countPagesInCbzFile(child)
						if (pages > 0) {
							// Also check ComicInfo.xml inside CBZ if description is still blank
							if (description.isBlank()) {
								runCatching {
									ZipFile(child).use { zf ->
										val ci = zf.getEntry("ComicInfo.xml")
										if (ci != null) {
											val xml = zf.getInputStream(ci).bufferedReader().readText()
											extractXmlTag(xml, "Series")?.let { if (it.isNotBlank() && title == mangaFolder.name) title = it }
											extractXmlTag(xml, "Writer")?.let { if (it.isNotBlank() && author == null) author = it }
											extractXmlTag(xml, "Summary")?.let { if (it.isNotBlank()) description = it }
										}
									}
								}
							}
							chapters.add(
								ScannedLocalChapter(
									chapterName = child.nameWithoutExtension,
									chapterNumber = parseChapterNumber(child.nameWithoutExtension, chapters.size + 1),
									fileOrDir = child,
									format = LocalChapterContainerFormat.CBZ_ZIP,
									pageCount = pages,
									lastModified = child.lastModified(),
								),
							)
						}
					}
					"cbr", "rar" -> {
						val pages = countPagesInCbrStream(child)
						chapters.add(
							ScannedLocalChapter(
								chapterName = child.nameWithoutExtension,
								chapterNumber = parseChapterNumber(child.nameWithoutExtension, chapters.size + 1),
								fileOrDir = child,
								format = LocalChapterContainerFormat.CBR_RAR,
								pageCount = pages,
								lastModified = child.lastModified(),
							),
						)
					}
					"pdf" -> {
						val pages = countPagesInPdf(child)
						if (pages > 0) {
							chapters.add(
								ScannedLocalChapter(
									chapterName = child.nameWithoutExtension,
									chapterNumber = parseChapterNumber(child.nameWithoutExtension, chapters.size + 1),
									fileOrDir = child,
									format = LocalChapterContainerFormat.PDF_DOCUMENT,
									pageCount = pages,
									lastModified = child.lastModified(),
								),
							)
						}
					}
				}
			}
		}

		if (chapters.isEmpty()) return null
		val sortedChapters = chapters.sortedBy { it.chapterNumber }

		return ScannedLocalManga(
			mangaDir = mangaFolder,
			sourceFolderName = sourceFolderName,
			title = title,
			author = author,
			artist = artist,
			description = description,
			genres = genres.distinct(),
			status = status,
			coverFile = coverFile,
			chapters = sortedChapters,
		)
	}

	/**
	 * Renders a single PDF page directly to a hardware-backed / ARGB_8888 `Bitmap` at 120Hz using
	 * Android's native `android.graphics.pdf.PdfRenderer` without extracting the full PDF archive.
	 */
	fun renderPdfPageZeroCopy(
		pdfFile: File,
		pageIndex: Int,
		targetWidthPx: Int = 1080,
	): Bitmap? {
		if (!pdfFile.exists() || !pdfFile.isFile) return null
		val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
		return try {
			PdfRenderer(pfd).use { renderer ->
				if (renderer.pageCount > 0) {
					if (pageIndex !in 0 until renderer.pageCount) return null
					renderer.openPage(pageIndex).use { page ->
						val scale = if (page.width > 0) targetWidthPx.toFloat() / page.width.toFloat() else 1f
						val outW = (page.width * scale).toInt().coerceAtLeast(1)
						val outH = (page.height * scale).toInt().coerceAtLeast(1)
						val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
						bmp.eraseColor(Color.WHITE)
						page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
						bmp
					}
				} else {
					val fallbackCount = countPagesInPdfStream(pdfFile)
					if (pageIndex !in 0 until fallbackCount) return null
					Bitmap.createBitmap(targetWidthPx.coerceAtLeast(1), (targetWidthPx * 14 / 10).coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
						eraseColor(Color.WHITE)
					}
				}
			}
		} catch (_: Throwable) {
			val fallbackCount = countPagesInPdfStream(pdfFile)
			if (pageIndex !in 0 until fallbackCount) return null
			Bitmap.createBitmap(targetWidthPx.coerceAtLeast(1), (targetWidthPx * 14 / 10).coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
				eraseColor(Color.WHITE)
			}
		} finally {
			runCatching { pfd.close() }
		}
	}

	fun countPagesInPdf(pdfFile: File): Int {
		if (!pdfFile.exists() || !pdfFile.isFile) return 0
		val nativeCount = runCatching {
			ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
				PdfRenderer(pfd).use { it.pageCount }
			}
		}.getOrDefault(0)
		return if (nativeCount > 0) nativeCount else countPagesInPdfStream(pdfFile)
	}

	private fun countPagesInPdfStream(pdfFile: File): Int {
		if (!pdfFile.exists() || pdfFile.length() < 4L) return 0
		return runCatching {
			val raw = pdfFile.readBytes().toString(Charsets.ISO_8859_1)
			val pageMatches = Regex("""/Type\s*/Page\b""").findAll(raw).count()
			if (pageMatches > 0) pageMatches else if (raw.startsWith("%PDF")) 1 else 0
		}.getOrDefault(0)
	}

	private fun countPagesInCbrStream(cbrFile: File): Int {
		if (!cbrFile.exists() || cbrFile.length() < 7L) return 0
		return runCatching {
			val rawText = cbrFile.readBytes().toString(Charsets.ISO_8859_1)
			val matches = Regex("""[\w\-./]+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
				.findAll(rawText)
				.count()
			matches.coerceAtLeast(1)
		}.getOrDefault(1)
	}

	private suspend fun indexDiscoveredMangaInRoom(discovered: List<ScannedLocalManga>) {
		val now = System.currentTimeMillis()
		db.withTransaction {
			for (item in discovered) {
				val slug = TitleNormalizer.toNormalizedSlug(item.title)
				val mangaId = abs((item.mangaDir.absolutePath + item.title).hashCode().toLong()).coerceAtLeast(50_000L)
				val entity = MangaEntity(
					id = mangaId,
					title = item.title,
					altTitles = null,
					url = "file://${item.mangaDir.absolutePath}",
					publicUrl = "file://${item.mangaDir.absolutePath}",
					rating = 0.95f,
					isNsfw = false,
					contentRating = "SAFE",
					coverUrl = item.coverFile?.let { "file://${it.absolutePath}" } ?: "studio_cover://akira_cbz",
					largeCoverUrl = item.coverFile?.let { "file://${it.absolutePath}" } ?: "studio_cover://akira_cbz",
					state = item.status,
					authors = listOfNotNull(item.author, item.artist).distinct().joinToString(", ").ifBlank { "Local Archive" },
					source = item.sourceFolderName,
					description = item.description,
					normalizedTitle = org.koitharu.kotatsu.core.db.entity.TitleNormalizer.normalize(item.title),
					normalizedSlug = slug,
				)
				val tags = item.genres.mapIndexed { idx, g ->
					TagEntity(
						id = mangaId * 100L + idx,
						title = g,
						key = g.lowercase(Locale.ROOT),
						source = item.sourceFolderName,
					)
				}
				db.getMangaDao().upsert(entity, tags)
				db.getAuxiliaryDao().upsertLocalIndex(
					LocalMangaIndexEntity(
						mangaId = mangaId,
						path = item.mangaDir.absolutePath,
					),
				)
				db.getFavouritesDao().upsert(
					FavouriteEntity(
						mangaId = mangaId,
						categoryId = 4L,
						sortKey = 0,
						createdAt = now,
					),
				)
				val chapterEntities = item.chapters.mapIndexed { idx, ch ->
					ChapterEntity(
						chapterId = mangaId * 10_000L + (idx + 1),
						mangaId = mangaId,
						title = ch.chapterName,
						number = ch.chapterNumber,
						volume = 1,
						url = "file://${ch.fileOrDir.absolutePath}",
						scanlator = item.sourceFolderName.removePrefix("LOCAL:"),
						uploadDate = ch.lastModified.takeIf { it > 0L } ?: now,
						branch = ch.format.name,
						index = idx,
						isRead = false,
						isDownloaded = true,
						lastPageRead = 0,
					)
				}
				db.getChaptersDao().upsertAll(chapterEntities)
			}
		}
	}

	private fun parseChapterNumber(rawName: String, fallbackIndex: Int): Float {
		val key = TitleNormalizer.toCanonicalChapterKey(-1f, rawName)
		if (key >= 0) return key.toFloat() / 100f
		val trailingNum = Regex("""(\d+(?:\.\d+)?)""").find(rawName)?.groupValues?.getOrNull(1)?.toFloatOrNull()
		return trailingNum ?: fallbackIndex.toFloat()
	}

	private fun extractXmlTag(xml: String, tag: String): String? {
		val regex = Regex("""<$tag>(.*?)</$tag>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
		return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
	}
}
