package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
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

abstract class AnimeHttpSource : AnimeCatalogueSource {
    protected val network: NetworkHelper by injectLazy()

    abstract val baseUrl: String
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

    @Deprecated("Use getPopularAnime")
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        client.newCall(popularAnimeRequest(page)).asObservableSuccess().map { popularAnimeParse(it) }

    protected abstract fun popularAnimeRequest(page: Int): Request
    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    @Deprecated("Use getSearchAnime")
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.defer {
            try {
                client.newCall(searchAnimeRequest(page, query, filters)).asObservableSuccess()
            } catch (error: NoClassDefFoundError) {
                throw RuntimeException(error)
            }
        }.map { searchAnimeParse(it) }

    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    @Deprecated("Use getLatestUpdates")
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map { latestUpdatesParse(it) }

    protected abstract fun latestUpdatesRequest(page: Int): Request
    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    @Suppress("DEPRECATION")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        fetchAnimeDetails(anime).awaitSingle()

    @Deprecated("Use getAnimeDetails")
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        client.newCall(animeDetailsRequest(anime)).asObservableSuccess()
            .map { animeDetailsParse(it).apply { initialized = true } }

    open fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected abstract fun animeDetailsParse(response: Response): SAnime

    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        fetchEpisodeList(anime).awaitSingle()

    @Deprecated("Use getEpisodeList")
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        client.newCall(episodeListRequest(anime)).asObservableSuccess().map { episodeListParse(it) }

    protected open fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected abstract fun episodeListParse(response: Response): List<SEpisode>
    protected open fun episodeVideoParse(response: Response): SEpisode = SEpisode.create()

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        runCatching {
            client.newCall(seasonListRequest(anime)).awaitSuccess().use { seasonListParse(it) }
        }.getOrDefault(emptyList())

    protected open fun seasonListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)
    protected open fun seasonListParse(response: Response): List<SAnime> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        runCatching {
            client.newCall(hosterListRequest(episode)).awaitSuccess().use { hosterListParse(it) }
        }.getOrDefault(emptyList())

    protected open fun hosterListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun hosterListParse(response: Response): List<Hoster> = emptyList()

    override suspend fun getVideoList(hoster: Hoster): List<Video> =
        hoster.videoList ?: runCatching {
            client.newCall(videoListRequest(hoster)).awaitSuccess().use { videoListParse(it, hoster) }
        }.getOrDefault(emptyList())

    protected open fun videoListRequest(hoster: Hoster): Request = GET(hoster.hosterUrl, headers)
    protected open fun videoListParse(response: Response, hoster: Hoster): List<Video> = emptyList()
    open suspend fun resolveVideo(video: Video): Video? = video

    @Suppress("DEPRECATION")
    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        fetchVideoList(episode).awaitSingle()

    @Deprecated("Use getVideoList")
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        client.newCall(videoListRequest(episode)).asObservableSuccess().map { videoListParse(it) }

    protected open fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)
    protected open fun videoListParse(response: Response): List<Video> = emptyList()

    open fun List<Hoster>.sortHosters(): List<Hoster> = this
    open fun List<Video>.sortVideos(): List<Video> = this

    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun setUrlWithoutDomain(episode: SEpisode, url: String) {
        episode.url = getUrlWithoutDomain(url)
    }

    fun setUrlWithoutDomain(anime: SAnime, url: String) {
        anime.url = getUrlWithoutDomain(url)
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

    open fun getAnimeUrl(anime: SAnime): String = animeDetailsRequest(anime).url.toString()
    open fun getEpisodeUrl(episode: SEpisode): String = videoListRequest(episode).url.toString()
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()
}
