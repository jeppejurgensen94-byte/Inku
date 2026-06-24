package eu.kanade.tachiyomi.source.online

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

abstract class HttpSource : CatalogueSource {
    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String
    open fun getHomeUrl(): String = baseUrl
    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }
    val headers: Headers by lazy { headersBuilder().build() }
    open val client: OkHttpClient get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder =
        Headers.Builder().add("User-Agent", network.defaultUserAgentProvider())

    override fun toString(): String = "$name (${lang.uppercase()})"

    @Deprecated("Use getPopularManga")
    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess().map { popularMangaParse(it) }

    protected abstract fun popularMangaRequest(page: Int): Request
    protected abstract fun popularMangaParse(response: Response): MangasPage

    @Deprecated("Use getSearchManga")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.defer {
            try {
                client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            } catch (error: NoClassDefFoundError) {
                throw RuntimeException(error)
            }
        }.map { searchMangaParse(it) }

    protected abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    protected abstract fun searchMangaParse(response: Response): MangasPage

    @Deprecated("Use getLatestUpdates")
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map { latestUpdatesParse(it) }

    protected abstract fun latestUpdatesRequest(page: Int): Request
    protected abstract fun latestUpdatesParse(response: Response): MangasPage

    @Suppress("DEPRECATION")
    override suspend fun getMangaDetails(manga: SManga): SManga =
        fetchMangaDetails(manga).awaitSingle()

    @Deprecated("Use getMangaDetails")
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected abstract fun mangaDetailsParse(response: Response): SManga

    @Suppress("DEPRECATION")
    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        fetchChapterList(manga).awaitSingle()

    @Deprecated("Use getChapterList")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val request = chapterListRequest(manga)
        Log.i("InkuSourceAdapter", "Manga chapter request source=$name url=${request.url}")
        return client.newCall(request).asObservableSuccess().map { response ->
            Log.i("InkuSourceAdapter", "Manga chapter response source=$name code=${response.code} url=${response.request.url}")
            chapterListParse(response)
        }
    }

    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected abstract fun chapterListParse(response: Response): List<SChapter>
    protected open fun chapterPageParse(response: Response): SChapter = SChapter.create()

    @Suppress("DEPRECATION")
    override suspend fun getPageList(chapter: SChapter): List<Page> =
        fetchPageList(chapter).awaitSingle()

    @Deprecated("Use getPageList")
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess().map { pageListParse(it) }

    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
    protected abstract fun pageListParse(response: Response): List<Page>

    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()

    @Deprecated("Use getImageUrl")
    open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess().map { imageUrlParse(it) }

    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)
    protected open fun imageUrlParse(response: Response): String = response.request.url.toString()

    open suspend fun getImage(page: Page): Response =
        client.newCachelessCallWithProgress(imageRequest(page), page).awaitSuccess()

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl ?: page.url, headers)

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String =
        try {
            val uri = URI(orig.replace(" ", "%20"))
            buildString {
                append(uri.path)
                if (uri.query != null) append("?").append(uri.query)
                if (uri.fragment != null) append("#").append(uri.fragment)
            }
        } catch (_: URISyntaxException) {
            orig
        }

    open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()
    open fun getChapterUrl(chapter: SChapter): String = pageListRequest(chapter).url.toString()
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}
    override fun getFilterList(): FilterList = FilterList()
}
