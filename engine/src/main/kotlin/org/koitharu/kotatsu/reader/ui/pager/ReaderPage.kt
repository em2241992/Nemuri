package org.koitharu.kotatsu.reader.ui.pager

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.parcelable.MangaSourceParceler
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource

object StitchedMangaPagesParceler : Parceler<List<MangaPage>> {
	override fun create(parcel: Parcel): List<MangaPage> {
		val count = parcel.readInt()
		if (count <= 0) return emptyList()
		return List(count) {
			MangaPage(
				id = parcel.readLong(),
				url = parcel.readString().orEmpty(),
				preview = parcel.readString(),
				source = MangaSource(parcel.readString()),
			)
		}
	}

	override fun List<MangaPage>.write(parcel: Parcel, flags: Int) {
		parcel.writeInt(size)
		for (page in this) {
			parcel.writeLong(page.id)
			parcel.writeString(page.url)
			parcel.writeString(page.preview)
			parcel.writeString(page.source.name)
		}
	}
}

@Parcelize
@TypeParceler<MangaSource, MangaSourceParceler>
@TypeParceler<List<MangaPage>, StitchedMangaPagesParceler>
data class ReaderPage(
	val id: Long,
	val url: String,
	val preview: String?,
	val chapterId: Long,
	val index: Int,
	val source: MangaSource,
	/** The extra pager item showing the second half of a split wide page; shares the original's index. */
	val isSecondHalf: Boolean = false,
	/** Inclusive last raw chapter page index merged into this logical page when seamless slice stitching is active. */
	val endIndex: Int = index,
	/** Adjacent raw slices merged vertically into this single logical page (empty when un-stitched). */
	val stitchedPages: List<MangaPage> = emptyList(),
) : Parcelable {

	constructor(page: MangaPage, index: Int, chapterId: Long) : this(
		id = page.id,
		url = page.url,
		preview = page.preview,
		chapterId = chapterId,
		index = index,
		source = page.source,
		endIndex = index,
		stitchedPages = emptyList(),
	)

	val isStitched: Boolean
		get() = stitchedPages.size > 1

	fun containsRawIndex(rawIndex: Int): Boolean = rawIndex in index..endIndex

	fun toMangaPage() = MangaPage(
		id = id,
		url = url,
		preview = preview,
		source = source,
	)
}
