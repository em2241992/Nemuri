package org.koitharu.kotatsu.reader.domain

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.ui.image.PageImageLoader
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPagePreloader @Inject constructor(
	@ApplicationContext private val context: Context,
	@PageImageLoader private val pageImageLoader: ImageLoader,
	private val chapterLoader: SilentFailoverChapterLoader,
) {
	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))
	private var activeWindowJob: Job? = null
	private val preloadedNextChapters = ConcurrentHashMap<Long, List<MangaPage>>()

	fun onPageChanged(
		currentChapter: MangaChapter,
		pages: List<MangaPage>,
		currentIndex: Int,
		nextChapter: MangaChapter?,
		resolvePageImageUrl: suspend (MangaPage) -> String = { it.url },
	) {
		activeWindowJob?.cancel()
		activeWindowJob = scope.launch {
			prefetchWindowSuspend(
				currentChapter = currentChapter,
				pages = pages,
				currentIndex = currentIndex,
				nextChapter = nextChapter,
				resolvePageImageUrl = resolvePageImageUrl,
			)
		}
	}

	/**
	 * Executes the N+3 memory+disk pre-decode, N+4..N+6 disk-only prefetch, and `totalPages - 3`
	 * invisible chapter seam preloading synchronously inside a coroutine.
	 */
	suspend fun prefetchWindowSuspend(
		currentChapter: MangaChapter,
		pages: List<MangaPage>,
		currentIndex: Int,
		nextChapter: MangaChapter?,
		resolvePageImageUrl: suspend (MangaPage) -> String = { it.url },
	) {
		// 1. Decode N+1 .. N+3 into Memory + Disk Cache
		val memoryTargets = ((currentIndex + 1)..(currentIndex + 3))
			.filter { it in pages.indices }
		for (idx in memoryTargets) {
			runCatching {
				val url = resolvePageImageUrl(pages[idx])
				val req = ImageRequest.Builder(context)
					.data(url)
					.memoryCacheKey("page_${currentChapter.id}_$idx")
					.diskCacheKey(url)
					.allowHardware(true)
					.build()
				pageImageLoader.enqueue(req)
			}
		}

		// 2. Prefetch N+4 .. N+6 into Disk Cache only (MemoryCachePolicy.DISABLED)
		val diskTargets = ((currentIndex + 4)..(currentIndex + 6))
			.filter { it in pages.indices }
		for (idx in diskTargets) {
			runCatching {
				val url = resolvePageImageUrl(pages[idx])
				val req = ImageRequest.Builder(context)
					.data(url)
					.diskCacheKey(url)
					.memoryCachePolicy(CachePolicy.DISABLED)
					.build()
				pageImageLoader.enqueue(req)
			}
		}

		// 3. Invisible Chapter Seam: At totalPages - 3, pre-resolve nextChapter & decode its first 2 pages
		if (nextChapter != null && currentIndex >= (pages.size - 3).coerceAtLeast(0)) {
			if (!preloadedNextChapters.containsKey(nextChapter.id)) {
				runCatching {
					val nextPages = chapterLoader.getPagesWithFailover(nextChapter)
					if (nextPages.isNotEmpty()) {
						preloadedNextChapters[nextChapter.id] = nextPages
						nextPages.take(2).forEachIndexed { nextIdx, page ->
							val url = resolvePageImageUrl(page)
							val req = ImageRequest.Builder(context)
								.data(url)
								.memoryCacheKey("page_${nextChapter.id}_$nextIdx")
								.diskCacheKey(url)
								.allowHardware(true)
								.build()
							pageImageLoader.enqueue(req)
						}
					}
				}
			}
		}
	}

	fun consumePreloadedChapter(chapterId: Long): List<MangaPage>? =
		preloadedNextChapters.remove(chapterId)
}
