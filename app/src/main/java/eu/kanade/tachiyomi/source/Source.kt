package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

interface Source {
    val id: Long
    val name: String
    val lang: String get() = ""
    val supportsLatest: Boolean

    fun getFilterList(): FilterList = FilterList()

    @Suppress("DEPRECATION")
    suspend fun getPopularManga(page: Int): MangasPage = fetchPopularManga(page).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): MangasPage = fetchLatestUpdates(page).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        fetchSearchManga(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean
    ): SMangaUpdate {
        val details = if (fetchDetails) fetchMangaDetails(manga).awaitSingle() else manga
        val chapterList = if (fetchChapters) fetchChapterList(manga).awaitSingle() else chapters
        return SMangaUpdate(details, chapterList)
    }

    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga = fetchMangaDetails(manga).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapterList(manga).awaitSingle()

    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> = fetchPageList(chapter).awaitSingle()

    @Deprecated("Use getPopularManga")
    fun fetchPopularManga(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()

    @Deprecated("Use getSearchManga")
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        throw UnsupportedOperationException()

    @Deprecated("Use getLatestUpdates")
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()

    @Deprecated("Use getMangaDetails")
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw UnsupportedOperationException()

    @Deprecated("Use getChapterList")
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw UnsupportedOperationException()

    @Deprecated("Use getPageList")
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw UnsupportedOperationException()
}
