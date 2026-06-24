@file:Suppress("FunctionName")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val defaultCacheControl = CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build()
private val defaultHeaders = Headers.Builder().build()
private val defaultBody: RequestBody = FormBody.Builder().build()

fun GET(
    url: String,
    headers: Headers = defaultHeaders,
    cache: CacheControl = defaultCacheControl
): Request = GET(url.toHttpUrl(), headers, cache)

fun GET(
    url: HttpUrl,
    headers: Headers = defaultHeaders,
    cache: CacheControl = defaultCacheControl
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun POST(
    url: String,
    headers: Headers = defaultHeaders,
    body: RequestBody = defaultBody,
    cache: CacheControl = defaultCacheControl
): Request = Request.Builder()
    .url(url)
    .post(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun PUT(
    url: String,
    headers: Headers = defaultHeaders,
    body: RequestBody = defaultBody,
    cache: CacheControl = defaultCacheControl
): Request = Request.Builder()
    .url(url)
    .put(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun DELETE(
    url: String,
    headers: Headers = defaultHeaders,
    body: RequestBody = defaultBody,
    cache: CacheControl = defaultCacheControl
): Request = Request.Builder()
    .url(url)
    .delete(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = defaultHeaders,
    cache: CacheControl = defaultCacheControl
): Response = newCall(GET(url, headers, cache)).awaitSuccess()

suspend fun OkHttpClient.post(
    url: String,
    headers: Headers = defaultHeaders,
    body: RequestBody = defaultBody,
    cache: CacheControl = defaultCacheControl
): Response = newCall(POST(url, headers, body, cache)).awaitSuccess()

fun Call.asObservableSuccess(): Observable<Response> =
    Observable.create { subscriber ->
        try {
            val response = execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code} ${response.message}")
            }
            if (!subscriber.isUnsubscribed) {
                subscriber.onNext(response)
                subscriber.onCompleted()
            } else {
                response.close()
            }
        } catch (error: Throwable) {
            if (!subscriber.isUnsubscribed) subscriber.onError(error)
        }
    }

suspend fun Call.awaitSuccess(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    response.close()
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("HTTP ${response.code} ${response.message}"))
                    }
                } else if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    progressListener: ProgressListener
): Call = newCall(request.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build())
