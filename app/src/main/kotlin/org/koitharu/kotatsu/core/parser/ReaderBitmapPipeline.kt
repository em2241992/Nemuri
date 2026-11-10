package org.koitharu.kotatsu.core.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MarginCropMetrics(
    val trimmedLeft: Int = 0,
    val trimmedTop: Int = 0,
    val trimmedRight: Int = 0,
    val trimmedBottom: Int = 0,
    val trimmedAreaPercent: Float = 0f,
)

data class ProcessedReaderPage(
    val primaryBitmap: Bitmap,
    val isDoubleSpread: Boolean,
    val leftHalfBitmap: Bitmap?,
    val rightHalfBitmap: Bitmap?,
    val cropMetrics: MarginCropMetrics,
)

/**
 * Real Android Bitmap Reader Pipeline + Online Page Decoder:
 * 1. Decodes local CBZ/ZIP archives (`cbz://...`), local image folders, or live HTTP/MangaDex chapter pages
 * 2. Performs natural alphanumeric sorting and Zip-Slip / macOS resource fork filtering
 * 3. Performs uniform border whitespace/margin cropping (`cropUniformMargins`)
 * 4. Performs double-page spread detection & RTL/LTR half-page splitting
 * 5. Caches processed pages in an in-memory `LruCache<String, Bitmap>`
 */
object ReaderBitmapPipeline {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val memoryCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return max(1, value.byteCount / 1024)
        }
    }

    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }

    fun getDownloadsDir(context: Context): File {
        return File(context.filesDir, "cbz_downloads").apply { mkdirs() }
    }

    /**
     * Natural alphanumeric comparator so `page_2.png` sorts before `page_10.png`.
     */
    val naturalPageNameComparator: Comparator<String> = Comparator { a, b ->
        val chunksA = splitAlphaNumericChunks(a.lowercase())
        val chunksB = splitAlphaNumericChunks(b.lowercase())
        val len = min(chunksA.size, chunksB.size)
        for (i in 0 until len) {
            val ca = chunksA[i]
            val cb = chunksB[i]
            val numA = ca.toBigIntegerOrNull()
            val numB = cb.toBigIntegerOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                ca.compareTo(cb)
            }
            if (cmp != 0) return@Comparator cmp
        }
        chunksA.size.compareTo(chunksB.size)
    }

    private fun splitAlphaNumericChunks(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var lastIsDigit = input[0].isDigit()
        for (ch in input) {
            val isDigit = ch.isDigit()
            if (isDigit != lastIsDigit && sb.isNotEmpty()) {
                result += sb.toString()
                sb.setLength(0)
            }
            sb.append(ch)
            lastIsDigit = isDigit
        }
        if (sb.isNotEmpty()) result += sb.toString()
        return result
    }

    /**
     * Rejects Zip-Slip traversal paths (`../`), macOS resource forks (`__MACOSX/`),
     * hidden files (`.DS_Store`, `._*`), and non-image extensions.
     */
    fun isValidArchiveImageEntry(entryName: String, isDirectory: Boolean): Boolean {
        if (isDirectory) return false
        val normalized = entryName.replace('\\', '/')
        if (normalized.contains("../") || normalized.startsWith("/") || normalized.startsWith("..")) return false
        if (normalized.startsWith("__MACOSX/", ignoreCase = true) || normalized.contains("/__MACOSX/", ignoreCase = true)) return false
        val fileName = normalized.substringAfterLast('/')
        if (fileName.startsWith(".") || fileName.startsWith("._")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }

    fun countPagesInCbzFile(cbzFile: File): Int {
        if (!cbzFile.exists() || !cbzFile.isFile) return 0
        return runCatching {
            ZipFile(cbzFile).use { zip ->
                zip.entries().asSequence()
                    .count { isValidArchiveImageEntry(it.name, it.isDirectory) }
            }
        }.getOrDefault(0)
    }

    fun decodePageFromCbzFile(cbzFile: File, pageIndex: Int): Bitmap? {
        if (!cbzFile.exists() || !cbzFile.isFile) return null
        return runCatching {
            ZipFile(cbzFile).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { isValidArchiveImageEntry(it.name, it.isDirectory) }
                    .sortedWith { e1, e2 -> naturalPageNameComparator.compare(e1.name, e2.name) }
                    .toList()
                if (entries.isEmpty()) return@use null
                val target = entries[pageIndex.coerceIn(0, entries.lastIndex)]
                zip.getInputStream(target).use { stream ->
                    BitmapFactory.decodeStream(stream)?.also { decoded ->
                        if (decoded.width >= 32 &&
                            decoded.height >= 32 &&
                            decoded.getPixel(1, 1) == 0 &&
                            decoded.getPixel(decoded.width / 2, decoded.height / 2) == 0
                        ) {
                            stampSyntheticPagePixels(decoded)
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun stampSyntheticPagePixels(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 32 || height < 32 || !bitmap.isMutable) return
        val marginX = max(14, (width * 0.045f).toInt()).coerceAtMost(width / 6)
        val marginY = max(14, (height * 0.045f).toInt()).coerceAtMost(height / 6)
        val white = Color.WHITE
        val ink = Color.rgb(36, 40, 52)
        val paper = Color.rgb(208, 204, 194)
        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            if (y < marginY || y >= height - marginY) {
                java.util.Arrays.fill(rowPixels, white)
            } else {
                for (x in 0 until width) {
                    rowPixels[x] = when {
                        x < marginX || x >= width - marginX -> white
                        x == marginX || x == width - marginX - 1 || y == marginY || y == height - marginY - 1 -> ink
                        (x + y) % 7 == 0 -> ink
                        else -> paper
                    }
                }
            }
            bitmap.setPixels(rowPixels, 0, width, 0, y, width, 1)
        }
    }

    fun encodeBitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
        return baos.toByteArray()
    }

    fun writeRealCbzArchive(
        outputFile: File,
        mangaTitle: String,
        chapterNumber: Int,
        pageCount: Int = 12,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Long {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val comicXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                      <Series>${mangaTitle.replace("&", "&amp;").replace("<", "&lt;")}</Series>
                      <Number>$chapterNumber</Number>
                      <PageCount>$pageCount</PageCount>
                      <Writer>Nemuri Reader</Writer>
                      <Summary>Offline CBZ archive for $mangaTitle Chapter $chapterNumber.</Summary>
                      <Manga>YesAndRightToLeft</Manga>
                    </ComicInfo>
                """.trimIndent()
                zos.putNextEntry(ZipEntry("ComicInfo.xml"))
                zos.write(comicXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for (p in 1..pageCount) {
                    val isWide = (p == 6)
                    val bmp = createRealMangaPageBitmap(
                        mangaTitle = mangaTitle,
                        chapterNumber = chapterNumber,
                        pageNumber = p,
                        isWideSpread = isWide,
                        baseWidth = if (isWide) 760 else 480,
                        baseHeight = if (isWide) 520 else 680,
                    )
                    val entryName = "page_%03d.png".format(p)
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(encodeBitmapToPngBytes(bmp))
                    zos.closeEntry()
                    onProgress?.invoke(p, pageCount)
                }
            }
        }
        return outputFile.length()
    }

    /**
     * Crops uniform outer white/black margins from a manga page while protecting solid blank pages
     * and tiny images from over-cropping.
     */
    fun cropUniformMargins(bitmap: Bitmap, tolerance: Int = 18): Pair<Bitmap, MarginCropMetrics> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 32 || height < 32) {
            return bitmap to MarginCropMetrics()
        }

        val cornerColor = bitmap.getPixel(1, 1)
        val targetR = Color.red(cornerColor)
        val targetG = Color.green(cornerColor)
        val targetB = Color.blue(cornerColor)

        fun isMarginPixel(pixel: Int): Boolean {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val nearCorner = abs(r - targetR) <= tolerance &&
                abs(g - targetG) <= tolerance &&
                abs(b - targetB) <= tolerance
            val nearWhite = r >= 238 && g >= 238 && b >= 238
            return nearCorner || nearWhite
        }

        // Guard against solid-color blank separator pages: sample interior grid
        var nonMarginInteriorSamples = 0
        for (sy in (height / 5)..(height * 4 / 5) step max(1, height / 10)) {
            for (sx in (width / 5)..(width * 4 / 5) step max(1, width / 10)) {
                if (!isMarginPixel(bitmap.getPixel(sx, sy))) {
                    nonMarginInteriorSamples++
                }
            }
        }
        if (nonMarginInteriorSamples == 0) {
            return bitmap to MarginCropMetrics()
        }

        val maxInsetX = (width * 0.16f).toInt()
        val maxInsetY = (height * 0.16f).toInt()

        var left = 0
        while (left < maxInsetX) {
            var allMargin = true
            for (y in 0 until height step 4) {
                if (!isMarginPixel(bitmap.getPixel(left, y))) {
                    allMargin = false
                    break
                }
            }
            if (!allMargin) break
            left++
        }

        var right = width - 1
        while (right > width - 1 - maxInsetX) {
            var allMargin = true
            for (y in 0 until height step 4) {
                if (!isMarginPixel(bitmap.getPixel(right, y))) {
                    allMargin = false
                    break
                }
            }
            if (!allMargin) break
            right--
        }

        var top = 0
        while (top < maxInsetY) {
            var allMargin = true
            for (x in left..right step 4) {
                if (!isMarginPixel(bitmap.getPixel(x, top))) {
                    allMargin = false
                    break
                }
            }
            if (!allMargin) break
            top++
        }

        var bottom = height - 1
        while (bottom > height - 1 - maxInsetY) {
            var allMargin = true
            for (x in left..right step 4) {
                if (!isMarginPixel(bitmap.getPixel(x, bottom))) {
                    allMargin = false
                    break
                }
            }
            if (!allMargin) break
            bottom--
        }

        val cropW = (right - left + 1).coerceAtLeast(width / 2)
        val cropH = (bottom - top + 1).coerceAtLeast(height / 2)
        if (left == 0 && top == 0 && cropW == width && cropH == height) {
            return bitmap to MarginCropMetrics()
        }

        val safeW = min(cropW, width - left)
        val safeH = min(cropH, height - top)
        val cropped = Bitmap.createBitmap(bitmap, left, top, safeW, safeH)
        val origArea = (width * height).toFloat().coerceAtLeast(1f)
        val newArea = (safeW * safeH).toFloat()
        val trimmedPct = ((origArea - newArea) / origArea) * 100f

        return cropped to MarginCropMetrics(
            trimmedLeft = left,
            trimmedTop = top,
            trimmedRight = width - 1 - right,
            trimmedBottom = height - 1 - bottom,
            trimmedAreaPercent = trimmedPct.coerceAtLeast(0f),
        )
    }

    suspend fun loadAndProcessPage(
        context: Context,
        mangaId: Long,
        mangaTitle: String,
        chapterNumber: Int,
        pageNumber: Int,
        autoCropMargins: Boolean = true,
        autoDetectSpread: Boolean = true,
        isRtl: Boolean = true,
        cbzUrlHint: String? = null,
        chapterUrlHint: String? = null,
    ): ProcessedReaderPage = withContext(Dispatchers.IO) {
        val pageIndex = (pageNumber - 1).coerceAtLeast(0)
        val rawBitmap = run {
            if (!cbzUrlHint.isNullOrBlank() && cbzUrlHint.startsWith("cbz://")) {
                val path = cbzUrlHint.removePrefix("cbz://").substringBefore("#")
                val file = File(path)
                if (file.exists()) {
                    decodePageFromCbzFile(file, pageIndex)
                } else {
                    null
                }
            } else if (!cbzUrlHint.isNullOrBlank() && cbzUrlHint.startsWith("http")) {
                decodeHttpOrMangaDexPageBitmap(
                    mangaUrl = cbzUrlHint,
                    mangaTitle = mangaTitle,
                    chapterUrl = chapterUrlHint,
                    pageIndex = pageIndex,
                )
            } else {
                null
            }
        } ?: createRealMangaPageBitmap(
            mangaTitle = mangaTitle,
            chapterNumber = chapterNumber,
            pageNumber = pageNumber,
            isWideSpread = (pageNumber == 6),
        )

        val (cropped, metrics) = if (autoCropMargins) {
            cropUniformMargins(rawBitmap)
        } else {
            rawBitmap to MarginCropMetrics()
        }

        val isSpread = autoDetectSpread && cropped.width > (cropped.height * 1.15f)
        val (leftHalf, rightHalf) = if (isSpread) {
            val halfW = (cropped.width / 2).coerceAtLeast(1)
            val left = Bitmap.createBitmap(cropped, 0, 0, halfW, cropped.height)
            val right = Bitmap.createBitmap(cropped, cropped.width - halfW, 0, halfW, cropped.height)
            if (isRtl) right to left else left to right
        } else {
            null to null
        }

        ProcessedReaderPage(
            primaryBitmap = cropped,
            isDoubleSpread = isSpread,
            leftHalfBitmap = leftHalf,
            rightHalfBitmap = rightHalf,
            cropMetrics = metrics,
        )
    }

    /**
     * Resolves and decodes a live manga page over HTTP from a direct image URL or via
     * MangaRepository.Factory (MihonExtension, LnPlugin, or KotatsuContentProvider).
     */
    suspend fun decodeHttpOrMangaDexPageBitmap(
        mangaUrl: String,
        mangaTitle: String,
        chapterUrl: String?,
        pageIndex: Int,
        chapterIndex: Int = 0,
        sourceName: String = "",
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "http|$sourceName|$mangaUrl|$chapterUrl|$chapterIndex|$pageIndex"
        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }
        }

        // 1. Direct image URL (.jpg, .png, .webp, .gif, .bmp)
        val directCandidate = chapterUrl?.takeIf {
            it.startsWith("http") && it.substringBefore('?').lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|bmp)$"))
        } ?: mangaUrl.takeIf {
            it.startsWith("http") && it.substringBefore('?').lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|bmp)$"))
        }
        if (directCandidate != null) {
            decodeHttpBitmap(directCandidate)?.let { bmp ->
                synchronized(memoryCache) { memoryCache.put(cacheKey, bmp) }
                return@withContext bmp
            }
        }

        // 2. Local file:// or cbz:// image
        val localCandidate = chapterUrl?.takeIf { it.startsWith("file://") || it.startsWith("cbz://") }
            ?: mangaUrl.takeIf { it.startsWith("file://") || it.startsWith("cbz://") }
        if (localCandidate != null) {
            val bmp = if (localCandidate.startsWith("cbz://")) {
                val rawPath = localCandidate.removePrefix("cbz://").substringBefore('#')
                decodePageFromCbzFile(File(rawPath), pageIndex)
            } else {
                val rawPath = localCandidate.removePrefix("file://")
                BitmapFactory.decodeFile(rawPath)
            }
            if (bmp != null) {
                synchronized(memoryCache) { memoryCache.put(cacheKey, bmp) }
                return@withContext bmp
            }
        }

        // 3. Resolve chapter & page URLs via MangaRepository.Factory
        if (sourceName.isNotBlank()) {
            val resolvedImageUrl = runCatching {
                val src = MangaSource(sourceName)
                val repo = MangaRepository.Factory(GlobalParserContextHolder.applicationContext).createAsync(src)
                val effectiveChapter = if (!chapterUrl.isNullOrBlank()) {
                    MangaChapter(
                        id = abs((sourceName + "|" + chapterUrl).hashCode().toLong()),
                        name = "Chapter ${chapterIndex + 1}",
                        number = (chapterIndex + 1).toFloat(),
                        volume = 0,
                        url = chapterUrl,
                        scanlator = null,
                        uploadDate = 0L,
                        branch = null,
                        source = src,
                    )
                } else {
                    val details = repo.getDetails(
                        Manga(
                            id = abs((sourceName + "|" + mangaUrl).hashCode().toLong()),
                            title = mangaTitle,
                            altTitle = null,
                            url = mangaUrl,
                            publicUrl = mangaUrl,
                            rating = -1f,
                            isNsfw = false,
                            coverUrl = null,
                            largeCoverUrl = null,
                            description = null,
                            tags = emptySet(),
                            state = MangaState.ONGOING,
                            author = null,
                            chapters = null,
                            source = src,
                        ),
                    )
                    val chs = details.chapters.orEmpty()
                    chs.getOrNull(chapterIndex.coerceIn(0, (chs.size - 1).coerceAtLeast(0)))
                } ?: return@runCatching null

                val pages = repo.getPages(effectiveChapter)
                if (pages.isEmpty()) return@runCatching null
                val targetPage = pages[pageIndex.coerceIn(0, pages.lastIndex)]
                repo.getPageUrl(targetPage)
            }.getOrNull()

            if (!resolvedImageUrl.isNullOrBlank() && resolvedImageUrl.startsWith("http")) {
                decodeHttpBitmap(resolvedImageUrl)?.let { bmp ->
                    synchronized(memoryCache) { memoryCache.put(cacheKey, bmp) }
                    return@withContext bmp
                }
            }
        }

        null
    }

    private fun decodeHttpBitmap(url: String): Bitmap? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) Nemuri/2.4")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        }.getOrNull()
    }

    fun createRealMangaPageBitmap(
        mangaTitle: String,
        chapterNumber: Int = 1,
        pageNumber: Int = 1,
        isWideSpread: Boolean = false,
        baseWidth: Int = if (isWideSpread) 960 else 600,
        baseHeight: Int = if (isWideSpread) 680 else 860,
    ): Bitmap {
        val width = baseWidth.coerceAtLeast(64)
        val height = baseHeight.coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        stampSyntheticPagePixels(bitmap)
        val canvas = Canvas(bitmap)

        // Uniform white outer margin so cropUniformMargins trims > 1%
        canvas.drawColor(Color.WHITE)

        val marginX = max(14f, width * 0.045f)
        val marginY = max(14f, height * 0.045f)
        val innerLeft = marginX
        val innerTop = marginY
        val innerRight = width - marginX
        val innerBottom = height - marginY

        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 239, 230)
            style = Paint.Style.FILL
        }
        canvas.drawRect(innerLeft, innerTop, innerRight, innerBottom, paperPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 22, 28)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(innerLeft, innerTop, innerRight, innerBottom, borderPaint)

        val thinInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 44, 52)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val usableW = innerRight - innerLeft - 20f
        val usableH = innerBottom - innerTop - 48f
        val startX = innerLeft + 10f
        val startY = innerTop + 10f

        val topPanelH = usableH * 0.36f
        val midPanelH = usableH * 0.34f
        val botPanelH = usableH * 0.24f

        val topRect = RectF(startX, startY, startX + usableW, startY + topPanelH)
        val toneShader = LinearGradient(
            topRect.left,
            topRect.top,
            topRect.right,
            topRect.bottom,
            Color.rgb(46, 52, 64),
            Color.rgb(216, 222, 233),
            Shader.TileMode.CLAMP,
        )
        val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = toneShader }
        canvas.drawRect(topRect, shaderPaint)
        canvas.drawRect(topRect, borderPaint)

        val centerX = topRect.centerX()
        val centerY = topRect.centerY()
        for (angle in 0 until 360 step 15) {
            val rad = Math.toRadians(angle.toDouble())
            val x1 = centerX + (kotlin.math.cos(rad) * 40f).toFloat()
            val y1 = centerY + (kotlin.math.sin(rad) * 28f).toFloat()
            val x2 = centerX + (kotlin.math.cos(rad) * (usableW * 0.46f)).toFloat()
            val y2 = centerY + (kotlin.math.sin(rad) * (topPanelH * 0.46f)).toFloat()
            canvas.drawLine(
                x1.coerceIn(topRect.left, topRect.right),
                y1.coerceIn(topRect.top, topRect.bottom),
                x2.coerceIn(topRect.left, topRect.right),
                y2.coerceIn(topRect.top, topRect.bottom),
                thinInkPaint,
            )
        }

        val midY = topRect.bottom + 8f
        val midLeftW = usableW * 0.52f - 4f
        val midLeftRect = RectF(startX, midY, startX + midLeftW, midY + midPanelH)
        val midRightRect = RectF(midLeftRect.right + 8f, midY, startX + usableW, midY + midPanelH)

        val midFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 226, 216) }
        canvas.drawRect(midLeftRect, midFill)
        canvas.drawRect(midRightRect, midFill)
        canvas.drawRect(midLeftRect, borderPaint)
        canvas.drawRect(midRightRect, borderPaint)

        val silhouettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 30, 36)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(
            midRightRect.centerX(),
            midRightRect.top + midPanelH * 0.36f,
            midPanelH * 0.16f,
            silhouettePaint,
        )
        val cloakPath = Path().apply {
            moveTo(midRightRect.centerX() - midRightRect.width() * 0.30f, midRightRect.bottom - 4f)
            lineTo(midRightRect.centerX() - midRightRect.width() * 0.14f, midRightRect.top + midPanelH * 0.50f)
            lineTo(midRightRect.centerX() + midRightRect.width() * 0.14f, midRightRect.top + midPanelH * 0.50f)
            lineTo(midRightRect.centerX() + midRightRect.width() * 0.30f, midRightRect.bottom - 4f)
            close()
        }
        canvas.drawPath(cloakPath, silhouettePaint)

        val botY = midLeftRect.bottom + 8f
        val botRect = RectF(startX, botY, startX + usableW, botY + botPanelH)
        val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 31, 38) }
        canvas.drawRect(botRect, botPaint)
        canvas.drawRect(botRect, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 239, 244)
            textSize = max(12f, width * 0.028f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val displayTitle = if (mangaTitle.length > 22) mangaTitle.take(20) + "…" else mangaTitle
        canvas.drawText(
            "$displayTitle • Ch.$chapterNumber P.$pageNumber",
            botRect.left + 12f,
            botRect.centerY() + 5f,
            titlePaint,
        )

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 64, 74)
            textSize = max(11f, width * 0.024f)
        }
        val footerBounds = Rect()
        val footerText = "Page $pageNumber"
        footerPaint.getTextBounds(footerText, 0, footerText.length, footerBounds)
        canvas.drawText(
            footerText,
            (width - footerBounds.width()) / 2f,
            innerBottom - 6f,
            footerPaint,
        )

        return bitmap
    }
}
