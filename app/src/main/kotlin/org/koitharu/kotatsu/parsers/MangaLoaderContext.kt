package org.koitharu.kotatsu.parsers

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class MutableCookieJar : CookieJar {
	private val store = ConcurrentHashMap<String, CopyOnWriteArrayList<Cookie>>()

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		val host = url.host.lowercase()
		val bucket = store.computeIfAbsent(host) { CopyOnWriteArrayList() }
		for (incoming in cookies) {
			bucket.removeAll { it.name == incoming.name }
			bucket.add(incoming)
		}
	}

	override fun loadForRequest(url: HttpUrl): List<Cookie> {
		val host = url.host.lowercase()
		return store[host]?.toList().orEmpty()
	}

	fun putRawCookieHeader(url: HttpUrl, rawCookieHeader: String) {
		val parts = rawCookieHeader.split(';')
		val parsedCookies = parts.mapNotNull { segment ->
			Cookie.parse(url, segment.trim())
		}
		if (parsedCookies.isNotEmpty()) {
			saveFromResponse(url, parsedCookies)
		}
	}

	fun hasClearanceCookie(host: String): Boolean {
		val cleanHost = host.lowercase()
		return store[cleanHost]?.any { it.name == "cf_clearance" && it.value.isNotBlank() } == true
	}

	fun getClearanceToken(host: String): String? {
		val cleanHost = host.lowercase()
		return store[cleanHost]?.firstOrNull { it.name == "cf_clearance" }?.value
	}
}

class MangaLoaderContext(
	val httpClient: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(10, TimeUnit.SECONDS)
		.build(),
	val cookieJar: CookieJar = MutableCookieJar(),
	val defaultUserAgent: String = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
	val mutableCookieJar: MutableCookieJar = (cookieJar as? MutableCookieJar) ?: MutableCookieJar(),
) {
	private val verifiedHostUserAgents = ConcurrentHashMap<String, String>()

	fun setVerifiedUserAgent(host: String, userAgent: String) {
		verifiedHostUserAgents[host.lowercase()] = userAgent
	}

	fun getUserAgentForHost(host: String): String =
		verifiedHostUserAgents[host.lowercase()] ?: defaultUserAgent
}

