package coil3.disk

import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DiskCache(
	val directory: File,
	val maxSizeBytes: Long,
) {
	private val indexedKeys = ConcurrentHashMap.newKeySet<String>()

	init {
		runCatching { directory.mkdirs() }
	}

	fun recordKey(key: String) {
		indexedKeys.add(key)
	}

	fun containsKey(key: String): Boolean = key in indexedKeys

	class Builder {
		private var dir: File = File(".")
		private var maxBytes: Long = 500L * 1024L * 1024L

		fun directory(directory: File): Builder = apply {
			this.dir = directory
		}

		fun maxSizeBytes(size: Long): Builder = apply {
			this.maxBytes = size
		}

		fun build(): DiskCache = DiskCache(directory = dir, maxSizeBytes = maxBytes)
	}
}
