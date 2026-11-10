package org.koitharu.kotatsu.core.model

data class MangaSource(
	val name: String,
	val title: String = name
		.removePrefix("EXT:")
		.removePrefix("MIHON_")
		.removePrefix("LNREADER_")
		.removePrefix("KOTATSU_"),
) {
	fun isNativeKotatsuParser(): Boolean {
		return name.startsWith("KOTATSU_", ignoreCase = true) ||
			name.equals("LOCAL", ignoreCase = true) ||
			name.startsWith("LOCAL:", ignoreCase = true)
	}

	companion object {
		val LOCAL = MangaSource("LOCAL", "Local Storage")
	}
}
