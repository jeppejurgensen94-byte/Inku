package com.inku.app

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers

internal data class InkuMangaCatalogItem(
    val title: String,
    val url: String,
    val coverUrl: String,
    val sourceName: String,
    val source: Source,
    val raw: SManga
)

internal data class InkuMangaDetails(
    val title: String,
    val description: String,
    val author: String,
    val status: String,
    val genres: List<String>,
    val coverUrl: String,
    val chapters: List<InkuMangaChapter>,
    val raw: SManga
)

internal data class InkuMangaChapter(
    val name: String,
    val url: String,
    val number: Float,
    val dateUpload: Long,
    val raw: SChapter
)

internal data class InkuMangaPage(
    val index: Int,
    val url: String,
    val imageUrl: String,
    val raw: Page
)

internal data class InkuAnimeCatalogItem(
    val title: String,
    val url: String,
    val coverUrl: String,
    val sourceName: String,
    val source: AnimeSource,
    val raw: SAnime
)

internal data class InkuAnimeDetails(
    val title: String,
    val description: String,
    val author: String,
    val status: String,
    val genres: List<String>,
    val coverUrl: String,
    val backgroundUrl: String,
    val episodes: List<InkuAnimeEpisode>,
    val raw: SAnime
)

internal data class InkuAnimeEpisode(
    val name: String,
    val url: String,
    val number: Float,
    val dateUpload: Long,
    val previewUrl: String,
    val raw: SEpisode
)

internal data class InkuAnimeVideo(
    val title: String,
    val videoUrl: String,
    val pageUrl: String,
    val headers: Map<String, String>,
    val raw: Video
)

internal data class InkuSourceResult<T>(
    val loading: Boolean = false,
    val data: T? = null,
    val error: String = ""
)

internal object InkuExtensionAdapters {
    suspend fun loadMangaCatalog(
        source: Source,
        query: String,
        latest: Boolean = false
    ): InkuSourceResult<List<InkuMangaCatalogItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val page = when {
                    query.isNotBlank() -> source.getSearchManga(1, query, source.getFilterListOrEmpty())
                    latest && source.supportsLatest -> source.getLatestUpdates(1)
                    else -> source.getPopularManga(1)
                }
                page.mangas.map { manga ->
                    InkuMangaCatalogItem(
                        title = manga.title,
                        url = manga.url,
                        coverUrl = manga.thumbnail_url.orEmpty(),
                        sourceName = source.name,
                        source = source,
                        raw = manga
                    )
                }
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    suspend fun loadMangaDetails(item: InkuMangaCatalogItem): InkuSourceResult<InkuMangaDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val details = item.source.getMangaDetails(item.raw.copy())
                val chapters = item.source.getChapterList(details).map { chapter ->
                    InkuMangaChapter(
                        name = chapter.name,
                        url = chapter.url,
                        number = chapter.chapter_number,
                        dateUpload = chapter.date_upload,
                        raw = chapter
                    )
                }
                InkuMangaDetails(
                    title = details.title,
                    description = details.description.orEmpty(),
                    author = listOfNotNull(details.author, details.artist).distinct().joinToString(", "),
                    status = mangaStatus(details.status),
                    genres = details.getGenres().orEmpty(),
                    coverUrl = details.thumbnail_url.orEmpty().ifBlank { item.coverUrl },
                    chapters = chapters,
                    raw = details
                )
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    suspend fun loadMangaPages(source: Source, chapter: InkuMangaChapter): InkuSourceResult<List<InkuMangaPage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                source.getPageList(chapter.raw).map { page ->
                    InkuMangaPage(
                        index = page.index,
                        url = page.url,
                        imageUrl = page.imageUrl ?: page.url,
                        raw = page
                    )
                }
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    suspend fun loadAnimeCatalog(
        source: AnimeSource,
        query: String,
        latest: Boolean = false
    ): InkuSourceResult<List<InkuAnimeCatalogItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val catalogue = source as? AnimeCatalogueSource
                    ?: error("This anime source does not expose a browsable catalogue.")
                val page = when {
                    query.isNotBlank() -> catalogue.getSearchAnime(1, query, catalogue.getFilterListOrEmpty())
                    latest && catalogue.supportsLatest -> catalogue.getLatestUpdates(1)
                    else -> catalogue.getPopularAnime(1)
                }
                page.animes.map { anime ->
                    InkuAnimeCatalogItem(
                        title = anime.title,
                        url = anime.url,
                        coverUrl = anime.thumbnail_url.orEmpty(),
                        sourceName = source.name,
                        source = source,
                        raw = anime
                    )
                }
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    suspend fun loadAnimeDetails(item: InkuAnimeCatalogItem): InkuSourceResult<InkuAnimeDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val details = item.source.getAnimeDetails(item.raw.copy())
                val episodes = item.source.getEpisodeList(details).map { episode ->
                    InkuAnimeEpisode(
                        name = episode.name,
                        url = episode.url,
                        number = episode.episode_number,
                        dateUpload = episode.date_upload,
                        previewUrl = episode.preview_url.orEmpty(),
                        raw = episode
                    )
                }
                InkuAnimeDetails(
                    title = details.title,
                    description = details.description.orEmpty(),
                    author = listOfNotNull(details.author, details.artist).distinct().joinToString(", "),
                    status = animeStatus(details.status),
                    genres = details.getGenres().orEmpty(),
                    coverUrl = details.thumbnail_url.orEmpty().ifBlank { item.coverUrl },
                    backgroundUrl = details.background_url.orEmpty(),
                    episodes = episodes,
                    raw = details
                )
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    suspend fun loadAnimeVideos(source: AnimeSource, episode: InkuAnimeEpisode): InkuSourceResult<List<InkuAnimeVideo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                source.getVideoList(episode.raw).map { video ->
                    InkuAnimeVideo(
                        title = video.videoTitle.ifBlank { video.quality.ifBlank { "Video" } },
                        videoUrl = video.videoUrl,
                        pageUrl = video.url,
                        headers = video.headers.toSimpleMap(),
                        raw = video
                    )
                }
            }.fold(
                onSuccess = { InkuSourceResult(data = it) },
                onFailure = { InkuSourceResult(error = classifySourceError(it)) }
            )
        }

    private fun Source.getFilterListOrEmpty(): FilterList =
        runCatching { getFilterList() }.getOrDefault(FilterList())

    private fun AnimeCatalogueSource.getFilterListOrEmpty(): AnimeFilterList =
        runCatching { getFilterList() }.getOrDefault(AnimeFilterList())

    private fun Headers?.toSimpleMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return names().associateWith { name -> values(name).joinToString("; ") }
    }

    private fun mangaStatus(status: Int): String =
        when (status) {
            SManga.ONGOING -> "Ongoing"
            SManga.COMPLETED -> "Completed"
            SManga.LICENSED -> "Licensed"
            SManga.PUBLISHING_FINISHED -> "Publishing finished"
            SManga.CANCELLED -> "Cancelled"
            SManga.ON_HIATUS -> "On hiatus"
            else -> "Unknown"
        }

    private fun animeStatus(status: Int): String =
        when (status) {
            SAnime.ONGOING -> "Ongoing"
            SAnime.COMPLETED -> "Completed"
            SAnime.LICENSED -> "Licensed"
            SAnime.PUBLISHING_FINISHED -> "Publishing finished"
            SAnime.CANCELLED -> "Cancelled"
            SAnime.ON_HIATUS -> "On hiatus"
            else -> "Unknown"
        }

    fun classifySourceError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            "cloudflare" in message.lowercase() -> "Unsupported protection: Cloudflare challenge could not be solved by Inku."
            "HTTP 403" in message -> "Source error: HTTP 403. The source may require login, cookies or unsupported protection."
            "HTTP 429" in message -> "Rate limited by source. Wait and retry."
            error is NoClassDefFoundError -> "Missing host dependency: ${error.message}"
            error is LinkageError -> "Incompatible extension API: ${error.message}"
            message.isNotBlank() -> message.take(260)
            else -> error.javaClass.simpleName
        }
    }
}
