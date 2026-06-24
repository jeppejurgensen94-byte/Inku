package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import tachiyomi.core.common.util.lang.awaitSingle
import rx.Observable

interface AnimeCatalogueSource : AnimeSource {
    val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    @Deprecated("Use getPopularAnime")
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated("Use getSearchAnime")
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated("Use getLatestUpdates")
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}
