package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.core.model.MangaSource

enum class MangaState {
	ONGOING,
	FINISHED,
	ABANDONED,
	PAUSED,
	UPCOMING,
}

data class MangaPage(
	val id: Long,
	val url: String,
	val preview: String? = null,
	val source: MangaSource = MangaSource("NATIVE_KOTATSU"),
)

data class MangaChapter(
	val id: Long,
	val name: String,
	val number: Float,
	val volume: Int = 1,
	val url: String = "",
	val scanlator: String? = null,
	val uploadDate: Long = 0L,
	val branch: String? = null,
	val source: MangaSource = MangaSource("NATIVE_KOTATSU"),
) {
	val title: String
		get() = name
}

data class Manga(
	val id: Long,
	val title: String,
	val altTitle: String? = null,
	val url: String = "",
	val publicUrl: String = "",
	val rating: Float = 0f,
	val isNsfw: Boolean = false,
	val coverUrl: String? = null,
	val largeCoverUrl: String? = null,
	val description: String? = null,
	val author: String? = null,
	val state: MangaState? = MangaState.ONGOING,
	val source: MangaSource = MangaSource("NATIVE_KOTATSU"),
	val chapters: List<MangaChapter>? = emptyList(),
)
