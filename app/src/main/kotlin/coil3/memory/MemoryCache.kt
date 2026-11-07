package coil3.memory

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class MemoryCache(
	val maxSizePercent: Double,
) {
	private val entries = ConcurrentHashMap<String, Any>()

	fun put(key: String, value: Any) {
		entries[key] = value
	}

	fun get(key: String): Any? = entries[key]

	fun size(): Int = entries.size

	class Builder {
		private var maxPercent: Double = 0.20

		fun maxSizePercent(context: Context, percent: Double = 0.20): Builder = apply {
			this.maxPercent = percent
		}

		fun maxSizePercent(percent: Double): Builder = apply {
			this.maxPercent = percent
		}

		fun build(): MemoryCache = MemoryCache(maxSizePercent = maxPercent)
	}
}
