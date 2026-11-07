package coil3.size

data class Size(
	val width: Int,
	val height: Int,
) {
	companion object {
		val ORIGINAL = Size(-1, -1)
	}
}
