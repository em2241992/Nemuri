package eu.kanade.tachiyomi.network

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MutableCookieJar
import rx.Observable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build()
val DEFAULT_HEADERS: Headers = Headers.Builder().build()
val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

fun GET(
	url: String,
	headers: Headers = DEFAULT_HEADERS,
	cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
	.url(url)
	.headers(headers)
	.cacheControl(cache)
	.build()

fun GET(
	url: HttpUrl,
	headers: Headers = DEFAULT_HEADERS,
	cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
	.url(url)
	.headers(headers)
	.cacheControl(cache)
	.build()

fun POST(
	url: String,
	headers: Headers = DEFAULT_HEADERS,
	body: RequestBody = DEFAULT_BODY,
	cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
	.url(url)
	.post(body)
	.headers(headers)
	.cacheControl(cache)
	.build()

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
	enqueue(object : Callback {
		override fun onResponse(call: Call, response: Response) {
			cont.resume(response)
		}

		override fun onFailure(call: Call, e: IOException) {
			if (cont.isActive) {
				cont.resumeWithException(e)
			}
		}
	})
	cont.invokeOnCancellation {
		runCatching { cancel() }
	}
}

suspend fun Call.awaitSuccess(): Response {
	val response = await()
	if (!response.isSuccessful) {
		response.close()
		throw IllegalStateException("HTTP error ${response.code}")
	}
	return response
}

fun Call.asObservable(): Observable<Response> = Observable.fromCallable {
	execute()
}

fun Call.asObservableSuccess(): Observable<Response> = Observable.fromCallable {
	val response = execute()
	if (!response.isSuccessful) {
		response.close()
		throw IllegalStateException("HTTP error ${response.code}")
	}
	response
}

open class NetworkHelper(context: Context? = null) {
	val cookieJar = MutableCookieJar()

	open val defaultUserAgentProvider: () -> String = {
		"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
	}

	open val client: OkHttpClient = OkHttpClient.Builder()
		.cookieJar(cookieJar)
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(25, TimeUnit.SECONDS)
		.followRedirects(true)
		.followSslRedirects(true)
		.build()

	@Deprecated("Use client instead", ReplaceWith("client"))
	open val cloudflareClient: OkHttpClient
		get() = client

	fun defaultUserAgent(): String = defaultUserAgentProvider()
}
