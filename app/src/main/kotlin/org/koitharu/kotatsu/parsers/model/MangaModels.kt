package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.core.model.MangaSource

enum class MangaState {
	ONGOING,
	FINISHED,
	ABANDONED,
	PAUSED,
	UPCOMING,
}

enum class SortOrder {
	UPDATED,
	POPULARITY,
	RATING,
	NEWEST,
	ALPHABETICAL,
	ALPHABETICAL_DESC,
}

data class MangaTag(
	val title: String,
	val key: String,
	val source: MangaSource = MangaSource("LOCAL"),
)

data class MangaPage(
	val id: Long,
	val url: String,
	val preview: String? = null,
	val source: MangaSource = MangaSource("LOCAL"),
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
	val source: MangaSource = MangaSource("LOCAL"),
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
	val tags: Set<MangaTag> = emptySet(),
	val author: String? = null,
	val state: MangaState? = MangaState.ONGOING,
	val source: MangaSource = MangaSource("LOCAL"),
	val chapters: List<MangaChapter>? = emptyList(),
)
