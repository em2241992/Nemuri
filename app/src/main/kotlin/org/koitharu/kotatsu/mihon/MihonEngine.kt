package org.koitharu.kotatsu.mihon

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import uy.kohesive.injekt.MihonInjektBridge
import java.util.Locale

sealed interface MihonLoadResult {
	data class Success(
		val pkgName: String,
		val appName: String,
		val versionName: String,
		val versionCode: Long,
		val libVersion: Double,
		val lang: String,
		val isNsfw: Boolean,
		val sources: List<Source>,
	) : MihonLoadResult {
		val catalogueSources: List<CatalogueSource>
			get() = sources.filterIsInstance<CatalogueSource>()
	}

	data class Error(
		val pkgName: String,
		val message: String,
		val cause: Throwable? = null,
	) : MihonLoadResult
}

data class MihonMangaSource(
	val catalogueSource: CatalogueSource,
	val pkgName: String,
	val isNsfw: Boolean = false,
) {
	val name: String = "MIHON_${catalogueSource.id}"
	val displayName: String = buildString {
		append(catalogueSource.name)
		if (catalogueSource.lang.isNotBlank()) {
			append(" (").append(catalogueSource.lang.uppercase(Locale.ROOT)).append(")")
		}
	}

	fun toMangaSource(): MangaSource = MangaSource(name = name, title = displayName)
}

object MihonDataConverters {

	fun SManga.toKotatsuManga(
		source: MangaSource,
		baseUrl: String = "",
		isSourceNsfw: Boolean = false,
	): Manga {
		val resolvedPublicUrl = when {
			url.startsWith("http://") || url.startsWith("https://") -> url
			baseUrl.isNotBlank() -> "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
			else -> url
		}
		val resolvedCover = thumbnail_url?.takeIf { it.isNotBlank() }?.let { thumb ->
			when {
				thumb.startsWith("http://") || thumb.startsWith("https://") -> thumb
				baseUrl.isNotBlank() -> "${baseUrl.trimEnd('/')}/${thumb.trimStart('/')}"
				else -> thumb
			}
		}
		val tagsSet = genre.orEmpty()
			.split(',', ';')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { tagTitle ->
				MangaTag(
					title = tagTitle,
					key = tagTitle.lowercase(Locale.ROOT),
					source = source,
				)
			}
			.toSet()

		val mappedState = when (status) {
			SManga.ONGOING -> MangaState.ONGOING
			SManga.COMPLETED, SManga.PUBLISHING_FINISHED -> MangaState.FINISHED
			SManga.CANCELLED -> MangaState.ABANDONED
			SManga.ON_HIATUS -> MangaState.PAUSED
			else -> null
		}

		val stableId = ((source.name.hashCode().toLong() shl 31) xor url.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL

		return Manga(
			id = stableId,
			title = title,
			altTitle = null,
			url = url,
			publicUrl = resolvedPublicUrl,
			rating = -1f,
			isNsfw = isSourceNsfw,
			coverUrl = resolvedCover,
			largeCoverUrl = resolvedCover,
			description = description,
			tags = tagsSet,
			state = mappedState,
			author = listOfNotNull(author?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() && it != author })
				.joinToString(", ")
				.ifBlank { null },
			source = source,
		)
	}

	fun Manga.toSManga(): SManga = SManga.create().also { sm ->
		sm.url = url
		sm.title = title
		sm.author = author
		sm.description = description
		sm.thumbnail_url = coverUrl
		sm.genre = tags.joinToString(", ") { it.title }
		sm.initialized = !description.isNullOrBlank()
	}

	fun SChapter.toKotatsuChapter(source: MangaSource, index: Int): MangaChapter {
		val resolvedNum = if (chapter_number >= 0f) {
			chapter_number
		} else {
			extractChapterNumberFromName(name) ?: (index + 1).toFloat()
		}
		val stableId = ((source.name.hashCode().toLong() shl 31) xor url.hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL
		return MangaChapter(
			id = stableId,
			name = name.ifBlank { "Chapter ${resolvedNum.toInt()}" },
			number = resolvedNum,
			volume = 0,
			url = url,
			scanlator = scanlator?.takeIf { it.isNotBlank() },
			uploadDate = date_upload,
			branch = null,
			source = source,
		)
	}

	fun MangaChapter.toSChapter(): SChapter = SChapter.create().also { sc ->
		sc.url = url
		sc.name = name
		sc.chapter_number = number
		sc.date_upload = uploadDate
		sc.scanlator = scanlator
	}

	fun Page.toKotatsuPage(source: MangaSource): MangaPage {
		val effectiveUrl = imageUrl?.takeIf { it.isNotBlank() } ?: url
		val stableId = ((source.name.hashCode().toLong() shl 31) xor "${index}_$effectiveUrl".hashCode().toLong()) and 0x7FFFFFFFFFFFFFFFL
		return MangaPage(
			id = stableId,
			url = effectiveUrl,
			preview = imageUrl?.takeIf { it.isNotBlank() },
			source = source,
		)
	}

	private fun extractChapterNumberFromName(rawName: String): Float? {
		val match = Regex("""(?:ch(?:apter)?\.?\s*|#)(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
			.find(rawName)
			?: Regex("""(\d+(?:\.\d+)?)""").find(rawName)
		return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
	}
}

class MihonExtensionLoader(
	private val context: Context,
) {
	companion object {
		private const val EXTENSION_FEATURE = "tachiyomi.extension"
		private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
		private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
		private const val LIB_VERSION_MIN = 1.2
		private const val LIB_VERSION_MAX = 1.6
	}

	suspend fun loadInstalledExtensions(): List<MihonLoadResult> = withContext(Dispatchers.IO) {
		(context.applicationContext as? Application)?.let { MihonInjektBridge.ensureInitialized(it) }
		val pm = context.packageManager
		val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
		val installedPackages = runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
			} else {
				@Suppress("DEPRECATION")
				pm.getInstalledPackages(flags)
			}
		}.getOrDefault(emptyList())

		installedPackages
			.filter { isPackageAnExtension(it) }
			.map { loadExtensionFromPackageInfo(pm, it) }
	}

	private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
		val hasFeature = pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
		val hasMeta = pkgInfo.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true
		return hasFeature || hasMeta
	}

	private fun loadExtensionFromPackageInfo(pm: PackageManager, pkgInfo: PackageInfo): MihonLoadResult {
		val pkgName = pkgInfo.packageName
		val appInfo = pkgInfo.applicationInfo
			?: return MihonLoadResult.Error(pkgName, "Missing ApplicationInfo")
		val metaData = appInfo.metaData
			?: return MihonLoadResult.Error(pkgName, "Missing extension meta-data")

		val versionName = pkgInfo.versionName ?: "1.0"
		val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			pkgInfo.longVersionCode
		} else {
			@Suppress("DEPRECATION")
			pkgInfo.versionCode.toLong()
		}

		val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull() ?: 1.4
		if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
			// Still attempt loading within reasonable compatibility bounds unless wildly incompatible
		}

		val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1
		val appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }
			.getOrDefault(pkgName.substringAfterLast('.'))
			.removePrefix("Tachiyomi: ")
			.removePrefix("Mihon: ")
			.trim()

		val rawClassNames = metaData.getString(METADATA_SOURCE_CLASS)
			?: return MihonLoadResult.Error(pkgName, "Missing tachiyomi.extension.class")

		return runCatching {
			val pkgContext = context.createPackageContext(
				pkgName,
				Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
			)
			val classLoader = pkgContext.classLoader
			val classNames = rawClassNames.split(';').map { cls ->
				val trimmed = cls.trim()
				if (trimmed.startsWith(".")) pkgName + trimmed else trimmed
			}.filter { it.isNotBlank() }

			val instantiatedSources = mutableListOf<Source>()
			for (className in classNames) {
				val clazz = Class.forName(className, false, classLoader)
				val instance = clazz.getDeclaredConstructor().newInstance()
				when (instance) {
					is Source -> instantiatedSources += instance
					is SourceFactory -> instantiatedSources += instance.createSources()
				}
			}

			val lang = instantiatedSources.filterIsInstance<CatalogueSource>()
				.map { it.lang }
				.distinct()
				.let { langs ->
					when {
						langs.isEmpty() -> extractLangFromPkg(pkgName)
						langs.size == 1 -> langs.first()
						else -> "all"
					}
				}

			MihonLoadResult.Success(
				pkgName = pkgName,
				appName = appLabel,
				versionName = versionName,
				versionCode = versionCode,
				libVersion = libVersion,
				lang = lang,
				isNsfw = isNsfw,
				sources = instantiatedSources,
			)
		}.getOrElse { err ->
			MihonLoadResult.Error(pkgName, err.message ?: "Failed to load extension", err)
		}
	}

	private fun extractLangFromPkg(pkgName: String): String {
		return pkgName.removePrefix("eu.kanade.tachiyomi.extension.")
			.substringBefore('.')
			.ifBlank { "all" }
	}
}

class MihonExtensionManager(
	context: Context,
) {
	private val appContext = context.applicationContext
	private val loader = MihonExtensionLoader(appContext)

	private val _installedExtensions = MutableStateFlow<List<MihonLoadResult.Success>>(emptyList())
	val installedExtensions: StateFlow<List<MihonLoadResult.Success>> = _installedExtensions.asStateFlow()

	private val _mihonSources = MutableStateFlow<List<MihonMangaSource>>(emptyList())
	val mihonSources: StateFlow<List<MihonMangaSource>> = _mihonSources.asStateFlow()

	suspend fun refreshExtensions(): List<MihonLoadResult.Success> {
		val results = loader.loadInstalledExtensions()
		val successes = results.filterIsInstance<MihonLoadResult.Success>()
		_installedExtensions.value = successes
		_mihonSources.value = successes.flatMap { ext ->
			ext.catalogueSources.map { cat ->
				MihonMangaSource(
					catalogueSource = cat,
					pkgName = ext.pkgName,
					isNsfw = ext.isNsfw,
				)
			}
		}
		return successes
	}

	fun findSourceByNameOrId(query: String): MihonMangaSource? {
		val clean = query.trim()
		if (clean.isEmpty()) return null
		val current = _mihonSources.value
		val numericId = clean.removePrefix("MIHON_").toLongOrNull()
		if (numericId != null) {
			current.firstOrNull { it.catalogueSource.id == numericId }?.let { return it }
		}
		return current.firstOrNull { src ->
			src.name.equals(clean, ignoreCase = true) ||
				src.displayName.equals(clean, ignoreCase = true) ||
				src.catalogueSource.name.equals(clean, ignoreCase = true) ||
				src.pkgName.equals(clean, ignoreCase = true) ||
				clean.contains(src.catalogueSource.name, ignoreCase = true)
		}
	}

	companion object {
		@Volatile
		private var INSTANCE: MihonExtensionManager? = null

		fun getInstance(context: Context): MihonExtensionManager {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: MihonExtensionManager(context.applicationContext).also { INSTANCE = it }
			}
		}
	}
}

class MihonMangaRepository(
	private val mihonSource: MihonMangaSource,
) : CachingMangaRepository() {

	override val source: MangaSource = mihonSource.toMangaSource()

	private val catalogueSource: CatalogueSource
		get() = mihonSource.catalogueSource

	private val baseUrl: String
		get() = (catalogueSource as? HttpSource)?.baseUrl.orEmpty()

	override val sortOrders: Set<SortOrder>
		get() = buildSet {
			add(SortOrder.POPULARITY)
			if (catalogueSource.supportsLatest) {
				add(SortOrder.UPDATED)
			}
		}

	private var currentPage: Int = 1
	private var lastOffset: Int = -1

	override suspend fun getList(
		offset: Int,
		query: String?,
		sortOrder: SortOrder?,
	): List<Manga> = withContext(Dispatchers.IO) {
		val page = if (offset <= 0 || offset <= lastOffset) {
			currentPage = 1
			1
		} else {
			currentPage += 1
			currentPage
		}
		lastOffset = offset

		val mangasPage = when {
			!query.isNullOrBlank() -> {
				catalogueSource.getSearchManga(page, query.trim(), catalogueSource.getFilterList())
			}
			sortOrder == SortOrder.UPDATED && catalogueSource.supportsLatest -> {
				catalogueSource.getLatestUpdates(page)
			}
			else -> {
				catalogueSource.getPopularManga(page)
			}
		}

		mangasPage.mangas.map { smanga ->
			with(MihonDataConverters) {
				smanga.toKotatsuManga(
					source = source,
					baseUrl = baseUrl,
					isSourceNsfw = mihonSource.isNsfw,
				)
			}
		}
	}

	override suspend fun fetchDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
		val sManga = with(MihonDataConverters) { manga.toSManga() }
		val detailedSManga = runCatching { catalogueSource.getMangaDetails(sManga) }.getOrDefault(sManga)
		val sChapters = runCatching { catalogueSource.getChapterList(sManga) }.getOrDefault(emptyList())

		val convertedDetails = with(MihonDataConverters) {
			detailedSManga.toKotatsuManga(
				source = source,
				baseUrl = baseUrl,
				isSourceNsfw = mihonSource.isNsfw || manga.isNsfw,
			)
		}

		// Mihon returns chapters newest-first typically; normalize into ascending reading order
		val orderedSChapters = if (sChapters.size >= 2 && sChapters.first().chapter_number > sChapters.last().chapter_number) {
			sChapters.reversed()
		} else {
			sChapters
		}

		val chapters = orderedSChapters.mapIndexed { idx, sc ->
			with(MihonDataConverters) {
				sc.toKotatsuChapter(source = source, index = idx)
			}
		}

		manga.copy(
			title = convertedDetails.title.ifBlank { manga.title },
			description = convertedDetails.description ?: manga.description,
			author = convertedDetails.author ?: manga.author,
			coverUrl = convertedDetails.coverUrl ?: manga.coverUrl,
			largeCoverUrl = convertedDetails.largeCoverUrl ?: manga.largeCoverUrl,
			tags = if (convertedDetails.tags.isNotEmpty()) convertedDetails.tags else manga.tags,
			state = convertedDetails.state ?: manga.state,
			chapters = chapters,
		)
	}

	override suspend fun fetchPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val sChapter = with(MihonDataConverters) { chapter.toSChapter() }
		val pages = catalogueSource.getPageList(sChapter)
		val httpSource = catalogueSource as? HttpSource
		pages.map { page ->
			if (page.imageUrl.isNullOrBlank() && httpSource != null && page.url.isNotBlank()) {
				runCatching {
					page.imageUrl = httpSource.getImageUrl(page)
				}
			}
			with(MihonDataConverters) {
				page.toKotatsuPage(source)
			}
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
		if (page.url.startsWith("http://") || page.url.startsWith("https://")) {
			return@withContext page.url
		}
		val httpSource = catalogueSource as? HttpSource ?: return@withContext page.url
		val mihonPage = Page(index = 0, url = page.url, imageUrl = page.preview)
		runCatching { httpSource.getImageUrl(mihonPage) }.getOrDefault(page.url)
	}
}

class LazyMihonMangaRepository(
	override val source: MangaSource,
	private val manager: MihonExtensionManager,
) : MangaRepository {

	private suspend fun resolveDelegate(): MihonMangaRepository? {
		var found = manager.findSourceByNameOrId(source.name) ?: manager.findSourceByNameOrId(source.title)
		if (found == null) {
			manager.refreshExtensions()
			found = manager.findSourceByNameOrId(source.name) ?: manager.findSourceByNameOrId(source.title)
		}
		return found?.let { MihonMangaRepository(it) }
	}

	override val sortOrders: Set<SortOrder>
		get() = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)

	override suspend fun getList(offset: Int, query: String?, sortOrder: SortOrder?): List<Manga> {
		return resolveDelegate()?.getList(offset, query, sortOrder).orEmpty()
	}

	override suspend fun getDetails(manga: Manga): Manga {
		return resolveDelegate()?.getDetails(manga) ?: manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return resolveDelegate()?.getPages(chapter).orEmpty()
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return resolveDelegate()?.getPageUrl(page) ?: page.url
	}
}
