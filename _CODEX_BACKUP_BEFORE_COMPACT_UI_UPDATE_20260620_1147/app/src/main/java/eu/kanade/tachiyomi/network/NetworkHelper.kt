package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkHelper(
    context: Context,
    private val userAgent: String = "Inku/0.13 Android"
) {
    val cookieJar: CookieJar = InkuCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(Cache(File(context.cacheDir, "extension_network_cache"), 10L * 1024L * 1024L))
        .addInterceptor { chain ->
            val request = chain.request()
            val withUserAgent = if (request.header("User-Agent").isNullOrBlank()) {
                request.newBuilder().header("User-Agent", defaultUserAgentProvider()).build()
            } else {
                request
            }
            chain.proceed(withUserAgent)
        }
        .build()

    val nonCloudflareClient: OkHttpClient = client
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = userAgent
}

private class InkuCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        store.compute(url.host) { _, existing ->
            val next = existing.orEmpty()
                .filterNot { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
                .toMutableList()
            next.addAll(cookies)
            next
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]
            .orEmpty()
            .filter { it.expiresAt > now && it.matches(url) }
    }
}
