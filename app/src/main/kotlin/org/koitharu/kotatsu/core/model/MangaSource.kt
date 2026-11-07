package org.koitharu.kotatsu.core.model

data class MangaSource(
	val name: String,
	val title: String = name.removePrefix("EXT:").removePrefix("MIHON_").removePrefix("LNREADER_"),
) {
	fun isNativeKotatsuParser(): Boolean {
		return !name.startsWith("MIHON_") && !name.startsWith("LNREADER_")
	}

	companion object {
		val MANGADEX = MangaSource("MANGADEX", "MangaDex")
	}
}
