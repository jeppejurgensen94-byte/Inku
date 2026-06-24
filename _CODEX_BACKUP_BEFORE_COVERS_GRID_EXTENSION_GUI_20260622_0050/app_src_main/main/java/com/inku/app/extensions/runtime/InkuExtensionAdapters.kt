package com.inku.app

import android.util.Log
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
    private const val SOURCE_TAG = "InkuSourceAdapter"

    suspend fun loadMangaCatalog(
        source: Source,
        query: String,
        latest: Boolean = false
    ): InkuSourceResult<List<InkuMangaCatalogItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                safeLog("Manga catalog call source=${source.name} latest=$latest search=${query.isNotBlank()}")
                val page = when {
                    query.isNotBlank() -> source.getSearchManga(1, query, source.getFilterListOrEmpty())
                    latest && source.supportsLatest -> source.getLatestUpdates(1)
                    else -> source.getPopularManga(1)
                }
                page.mangas.map { manga ->
                    val url = manga.safeUrl()
                    if (url.isBlank()) error("Source returned a manga catalog item without URL.")
                    InkuMangaCatalogItem(
                        title = manga.safeTitle().ifBlank { "Untitled manga" },
                        url = url,
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
                safeLog("Manga details call source=${item.sourceName}")
                val requestManga = item.raw.copy()
                val details = item.source.getMangaDetails(requestManga.copy()).withIdentityFallback(item.raw)
                val chapters = item.source.getChapterList(requestManga).map { chapter ->
                    val url = chapter.safeUrl()
                    if (url.isBlank()) error("Source returned a chapter without URL.")
                    InkuMangaChapter(
                        name = chapter.safeName().ifBlank { "Chapter" },
                        url = url,
                        number = chapter.chapter_number,
                        dateUpload = chapter.date_upload,
                        raw = chapter
                    )
                }
                safeLog("Manga details mapped source=${item.sourceName} chapterCount=${chapters.size}")
                InkuMangaDetails(
                    title = details.safeTitle().ifBlank { item.title },
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
                safeLog("Manga pages call source=${source.name} chapterNumber=${chapter.number}")
                val pages = source.getPageList(chapter.raw).map { page ->
                    InkuMangaPage(
                        index = page.index,
                        url = page.url,
                        imageUrl = page.imageUrl ?: page.url,
                        raw = page
                    )
                }
                safeLog("Manga pages mapped source=${source.name} pageCount=${pages.size}")
                pages
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
                safeLog("Anime catalog call source=${source.name} latest=$latest search=${query.isNotBlank()}")
                val catalogue = source as? AnimeCatalogueSource
                    ?: error("This anime source does not expose a browsable catalogue.")
                val page = when {
                    query.isNotBlank() -> catalogue.getSearchAnime(1, query, catalogue.getFilterListOrEmpty())
                    latest && catalogue.supportsLatest -> catalogue.getLatestUpdates(1)
                    else -> catalogue.getPopularAnime(1)
                }
                page.animes.map { anime ->
                    val url = anime.safeUrl()
                    if (url.isBlank()) error("Source returned an anime catalog item without URL.")
                    InkuAnimeCatalogItem(
                        title = anime.safeTitle().ifBlank { "Untitled anime" },
                        url = url,
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
                safeLog("Anime details call source=${item.sourceName}")
                val details = item.source.getAnimeDetails(item.raw.copy()).withIdentityFallback(item.raw)
                val episodes = item.source.getEpisodeList(details).map { episode ->
                    val url = episode.safeUrl()
                    if (url.isBlank()) error("Source returned an episode without URL.")
                    InkuAnimeEpisode(
                        name = episode.safeName().ifBlank { "Episode" },
                        url = url,
                        number = episode.episode_number,
                        dateUpload = episode.date_upload,
                        previewUrl = episode.preview_url.orEmpty(),
                        raw = episode
                    )
                }
                safeLog("Anime details mapped source=${item.sourceName} episodeCount=${episodes.size}")
                InkuAnimeDetails(
                    title = details.safeTitle().ifBlank { item.title },
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
                safeLog("Anime video call source=${source.name} episodeNumber=${episode.number}")
                val videos = source.getVideoList(episode.raw).map { video ->
                    InkuAnimeVideo(
                        title = video.videoTitle.ifBlank { video.quality.ifBlank { "Video" } },
                        videoUrl = video.videoUrl,
                        pageUrl = video.url,
                        headers = video.headers.toSimpleMap(),
                        raw = video
                    )
                }
                safeLog("Anime videos mapped source=${source.name} videoCount=${videos.size}")
                videos
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

    private fun safeLog(message: String) {
        runCatching { Log.i(SOURCE_TAG, message) }
    }

    private fun SManga.safeUrl(): String = runCatching { url }.getOrDefault("")
    private fun SManga.safeTitle(): String = runCatching { title }.getOrDefault("")
    private fun SChapter.safeUrl(): String = runCatching { url }.getOrDefault("")
    private fun SChapter.safeName(): String = runCatching { name }.getOrDefault("")
    private fun SAnime.safeUrl(): String = runCatching { url }.getOrDefault("")
    private fun SAnime.safeTitle(): String = runCatching { title }.getOrDefault("")
    private fun SEpisode.safeUrl(): String = runCatching { url }.getOrDefault("")
    private fun SEpisode.safeName(): String = runCatching { name }.getOrDefault("")

    private fun SManga.withIdentityFallback(original: SManga): SManga = apply {
        original.safeUrl().takeIf { it.isNotBlank() }?.let { url = it }
        if (safeTitle().isBlank()) title = original.safeTitle()
    }

    private fun SAnime.withIdentityFallback(original: SAnime): SAnime = apply {
        original.safeUrl().takeIf { it.isNotBlank() }?.let { url = it }
        if (safeTitle().isBlank()) title = original.safeTitle()
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
