package com.inku.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.URI
import java.util.Locale

internal enum class InkuExtensionContentType {
    Anime,
    Manga,
    Both
}

internal data class InkuCatalogItem(
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val description: String = ""
)

internal data class InkuCatalogCategory(
    val name: String,
    val url: String
)

internal data class InkuCatalogResult(
    val success: Boolean,
    val items: List<InkuCatalogItem>,
    val categories: List<InkuCatalogCategory>,
    val logs: List<String>
)

internal data class InkuExtensionManifest(
    val id: String,
    val name: String,
    val contentType: InkuExtensionContentType,
    val language: String,
    val baseUrl: String,
    val version: String = "1.0.0",
    val searchPath: String = "",
    val listSelector: String = "",
    val titleSelector: String = "",
    val linkSelector: String = "",
    val coverSelector: String = "",
    val descriptionSelector: String = "",
    val categorySelector: String = "",
    val episodeSelector: String = "",
    val mediaSelector: String = "",
    val nextPageSelector: String = "",
    val iconUri: String = "",
    val qualityOptions: List<String> = emptyList(),
    val enabled: Boolean = true,
    val builtAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 2)
        put("id", id)
        put("name", name)
        put("contentType", contentType.name)
        put("language", language)
        put("baseUrl", baseUrl)
        put("version", version)
        put("searchPath", searchPath)
        put("selectors", JSONObject().apply {
            put("list", listSelector)
            put("title", titleSelector)
            put("link", linkSelector)
            put("cover", coverSelector)
            put("description", descriptionSelector)
            put("category", categorySelector)
            put("episode", episodeSelector)
            put("media", mediaSelector)
            put("nextPage", nextPageSelector)
        })
        put("iconUri", iconUri)
        put("qualityOptions", JSONArray().apply {
            qualityOptions.distinct().forEach(::put)
        })
        put("enabled", enabled)
        put("builtAt", builtAt)
        put("capabilities", JSONArray().apply {
            put("browse")
            put("search")
            put("details")
            put("editable-settings")
            if (episodeSelector.isNotBlank()) put("episodes-or-chapters")
            if (mediaSelector.isNotBlank()) put("direct-media-url")
        })
        put("safety", JSONObject().apply {
            put("allowsLoginBypass", false)
            put("allowsDrmBypass", false)
            put("allowsPaywallBypass", false)
            put("publicOrUserOwnedOnly", true)
        })
    }

    companion object {
        fun fromJson(json: JSONObject): InkuExtensionManifest {
            val selectors = json.optJSONObject("selectors") ?: JSONObject()
            val qualityArray = json.optJSONArray("qualityOptions") ?: JSONArray()
            val qualities = buildList {
                for (index in 0 until qualityArray.length()) {
                    qualityArray.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
            return InkuExtensionManifest(
                id = json.getString("id"),
                name = json.optString("name", json.getString("id")),
                contentType = runCatching {
                    InkuExtensionContentType.valueOf(json.optString("contentType", "Anime"))
                }.getOrDefault(InkuExtensionContentType.Anime),
                language = json.optString("language", "Unknown"),
                baseUrl = json.optString("baseUrl"),
                version = json.optString("version", "1.0.0"),
                searchPath = json.optString("searchPath"),
                listSelector = selectors.optString("list"),
                titleSelector = selectors.optString("title"),
                linkSelector = selectors.optString("link"),
                coverSelector = selectors.optString("cover"),
                descriptionSelector = selectors.optString("description"),
                categorySelector = selectors.optString("category"),
                episodeSelector = selectors.optString("episode"),
                mediaSelector = selectors.optString("media"),
                nextPageSelector = selectors.optString("nextPage"),
                iconUri = json.optString("iconUri"),
                qualityOptions = qualities,
                enabled = json.optBoolean("enabled", true),
                builtAt = json.optLong("builtAt", 0L)
            )
        }
    }
}

internal data class ExtensionBuildResult(
    val success: Boolean,
    val logs: List<String>,
    val manifest: InkuExtensionManifest?,
    val manifestJson: String,
    val adapterCode: String,
    val previewItems: List<InkuCatalogItem> = emptyList()
)

internal object ExtensionStore {
    private const val PREFS = "inku_extensions"
    private const val INSTALLED_KEY = "installed_ids"
    private const val PINNED_KEY = "pinned_ids"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Inku/0.13"

    fun extensionDirectory(context: Context): File =
        File(context.filesDir, "extensions").apply { mkdirs() }

    fun save(context: Context, manifest: InkuExtensionManifest): File {
        val file = File(extensionDirectory(context), "${manifest.id}.inku-extension.json")
        file.writeText(manifest.toJson().toString(2), Charsets.UTF_8)
        setInstalled(context, manifest.id, true)
        return file
    }

    fun update(context: Context, manifest: InkuExtensionManifest): File = save(context, manifest)

    fun delete(context: Context, id: String) {
        File(extensionDirectory(context), "$id.inku-extension.json").delete()
        setInstalled(context, id, false)
        setPinned(context, id, false)
    }

    fun listCustom(context: Context): List<InkuExtensionManifest> =
        extensionDirectory(context)
            .listFiles { file -> file.name.endsWith(".inku-extension.json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { InkuExtensionManifest.fromJson(JSONObject(file.readText())) }.getOrNull()
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun find(context: Context, id: String): InkuExtensionManifest? =
        listCustom(context).firstOrNull { it.id == id }

    fun installedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(INSTALLED_KEY, emptySet())
            ?.toSet()
            .orEmpty()

    fun pinnedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(PINNED_KEY, emptySet())
            ?.toSet()
            .orEmpty()

    fun setInstalled(context: Context, id: String, installed: Boolean) {
        val updated = installedIds(context).toMutableSet().apply {
            if (installed) add(id) else remove(id)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(INSTALLED_KEY, updated)
            .apply()
    }

    fun setPinned(context: Context, id: String, pinned: Boolean) {
        val updated = pinnedIds(context).toMutableSet().apply {
            if (pinned) add(id) else remove(id)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PINNED_KEY, updated)
            .apply()
    }

    fun fileFor(context: Context, id: String): File? {
        val file = File(extensionDirectory(context), "$id.inku-extension.json")
        return file.takeIf { it.exists() }
    }

    suspend fun autoBuildFromUrl(websiteUrl: String): ExtensionBuildResult =
        withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            val cleanUrl = websiteUrl.trim()
            logs += "[1/9] Validating website URL"
            val parsedUri = runCatching { URI(cleanUrl) }.getOrNull()
            if (parsedUri?.scheme !in setOf("http", "https") || parsedUri?.host.isNullOrBlank()) {
                return@withContext failure(logs, "Use a complete http:// or https:// website URL.")
            }
            val uri = requireNotNull(parsedUri)

            logs += "[2/9] Connecting to ${uri.host}"
            val document = runCatching { fetch(cleanUrl) }.getOrElse { error ->
                return@withContext failure(
                    logs,
                    "Connection failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
            logs += "      Loaded ${document.title().ifBlank { uri.host }}"

            logs += "[3/9] Detecting source name, language and content type"
            val name = detectName(document, uri.host)
            val language = detectLanguage(document)
            val contentType = detectContentType(document, cleanUrl)
            logs += "      $name • ${contentType.name} • $language"

            logs += "[4/9] Detecting catalog item cards"
            val listSelector = detectListSelector(document)
            if (listSelector.isBlank()) {
                return@withContext failure(
                    logs,
                    "No repeatable catalog cards were detected. Try the direct catalog page instead of the home page."
                )
            }
            val sampleCard = document.select(listSelector).first()
                ?: return@withContext failure(logs, "The detected card selector returned no items.")
            logs += "      List selector: $listSelector"

            logs += "[5/9] Detecting title, link, cover and description selectors"
            val titleSelector = detectInsideSelector(sampleCard, TITLE_CANDIDATES)
            val linkSelector = detectInsideSelector(sampleCard, LINK_CANDIDATES)
            val coverSelector = detectInsideSelector(sampleCard, COVER_CANDIDATES)
            val descriptionSelector = detectInsideSelector(sampleCard, DESCRIPTION_CANDIDATES)
            logs += "      Title: ${titleSelector.ifBlank { "card text fallback" }}"
            logs += "      Link: ${linkSelector.ifBlank { "no link detected" }}"
            logs += "      Cover: ${coverSelector.ifBlank { "no cover detected" }}"

            if (linkSelector.isBlank()) {
                return@withContext failure(logs, "A link inside the catalog cards could not be detected.")
            }

            logs += "[6/9] Detecting search and category paths"
            val searchPath = detectSearchPath(document)
            val categorySelector = detectGlobalSelector(document, CATEGORY_CANDIDATES)
            logs += "      Search: ${searchPath.ifBlank { "not detected" }}"
            logs += "      Categories: ${categorySelector.ifBlank { "not detected" }}"

            logs += "[7/9] Inspecting a detail page for episodes or chapters"
            val firstLink = absoluteLink(sampleCard.select(linkSelector).first(), document.baseUri())
            val detailDocument = if (firstLink.isNotBlank()) {
                runCatching { fetch(firstLink) }.getOrNull()
            } else {
                null
            }
            val episodeSelector = detailDocument?.let {
                detectGlobalSelector(it, EPISODE_CANDIDATES)
            }.orEmpty()
            val mediaSelector = detailDocument?.let {
                detectGlobalSelector(it, MEDIA_CANDIDATES)
            }.orEmpty()
            val nextPageSelector = detectGlobalSelector(document, NEXT_PAGE_CANDIDATES)
            val qualityOptions = detectQualities(document, detailDocument)
            logs += "      Episodes/chapters: ${episodeSelector.ifBlank { "not detected" }}"
            logs += "      Media: ${mediaSelector.ifBlank { "not detected" }}"
            logs += "      Qualities: ${qualityOptions.ifEmpty { listOf("Auto") }.joinToString()}"

            logs += "[8/9] Creating manifest and preview catalog"
            val id = slugify(name).ifBlank { slugify(uri.host) }.ifBlank { "custom-extension" }
            val manifest = InkuExtensionManifest(
                id = id,
                name = name,
                contentType = contentType,
                language = language,
                baseUrl = cleanUrl.trimEnd('/'),
                searchPath = searchPath,
                listSelector = listSelector,
                titleSelector = titleSelector,
                linkSelector = linkSelector,
                coverSelector = coverSelector,
                descriptionSelector = descriptionSelector,
                categorySelector = categorySelector,
                episodeSelector = episodeSelector,
                mediaSelector = mediaSelector,
                nextPageSelector = nextPageSelector,
                qualityOptions = qualityOptions.ifEmpty { listOf("Auto") }
            )
            val preview = parseCatalog(document, manifest).take(12)
            logs += "      Preview items: ${preview.size}"

            logs += "[9/9] Generating readable adapter code"
            logs += if (preview.isNotEmpty()) {
                "Build passed. The extension can be installed and edited in Extension Settings."
            } else {
                "Build completed, but the preview was empty. Open Extension Settings and adjust selectors."
            }

            ExtensionBuildResult(
                success = true,
                logs = logs,
                manifest = manifest,
                manifestJson = manifest.toJson().toString(2),
                adapterCode = generateAdapterCode(manifest),
                previewItems = preview
            )
        }

    suspend fun loadCatalog(
        manifest: InkuExtensionManifest,
        query: String = "",
        categoryUrl: String = ""
    ): InkuCatalogResult = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val url = when {
            categoryUrl.isNotBlank() -> categoryUrl
            query.isNotBlank() && manifest.searchPath.isNotBlank() -> buildSearchUrl(manifest, query)
            else -> manifest.baseUrl
        }
        return@withContext runCatching {
            logs += "Loading $url"
            val document = fetch(url)
            val items = parseCatalog(document, manifest)
            val categories = if (manifest.categorySelector.isBlank()) {
                emptyList()
            } else {
                document.select(manifest.categorySelector)
                    .mapNotNull { element ->
                        val name = element.text().trim().takeIf { it.length in 2..40 }
                            ?: return@mapNotNull null
                        val linkElement = when {
                            element.hasAttr("href") -> element
                            else -> element.selectFirst("a[href]")
                        }
                        InkuCatalogCategory(
                            name = name,
                            url = absoluteLink(linkElement, document.baseUri())
                        )
                    }
                    .distinctBy { it.name.lowercase(Locale.ROOT) to it.url }
                    .take(30)
            }
            InkuCatalogResult(
                success = true,
                items = items,
                categories = categories,
                logs = logs + "Loaded ${items.size} items"
            )
        }.getOrElse { error ->
            InkuCatalogResult(
                success = false,
                items = emptyList(),
                categories = emptyList(),
                logs = logs + "ERROR: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .referrer("https://www.google.com/")
        .timeout(15_000)
        .maxBodySize(4 * 1024 * 1024)
        .followRedirects(true)
        .get()

    private fun parseCatalog(
        document: Document,
        manifest: InkuExtensionManifest
    ): List<InkuCatalogItem> {
        if (manifest.listSelector.isBlank()) return emptyList()
        return document.select(manifest.listSelector)
            .mapNotNull { card ->
                val title = extractTitle(card, manifest.titleSelector)
                val linkElement = if (manifest.linkSelector.isBlank()) {
                    card.selectFirst("a[href]")
                } else {
                    card.selectFirst(manifest.linkSelector)
                }
                val link = absoluteLink(linkElement, document.baseUri())
                if (title.isBlank() || link.isBlank()) return@mapNotNull null
                val coverElement = if (manifest.coverSelector.isBlank()) {
                    card.selectFirst("img")
                } else {
                    card.selectFirst(manifest.coverSelector)
                }
                val descriptionElement = manifest.descriptionSelector
                    .takeIf(String::isNotBlank)
                    ?.let(card::selectFirst)
                InkuCatalogItem(
                    title = title,
                    url = link,
                    coverUrl = extractImageUrl(coverElement, document.baseUri()),
                    description = descriptionElement?.text().orEmpty().trim()
                )
            }
            .distinctBy { it.url }
            .take(120)
    }

    private fun detectName(document: Document, fallbackHost: String): String {
        val ogName = document.selectFirst("meta[property=og:site_name]")?.attr("content").orEmpty()
        val title = document.title().substringBefore("|").substringBefore("-").trim()
        return ogName.ifBlank { title }.ifBlank {
            fallbackHost.substringBefore('.').replaceFirstChar { it.uppercase(Locale.ROOT) }
        }.take(60)
    }

    private fun detectLanguage(document: Document): String {
        val raw = document.selectFirst("html")?.attr("lang").orEmpty().lowercase(Locale.ROOT)
        return when {
            raw.startsWith("da") -> "Danish"
            raw.startsWith("en") -> "English"
            raw.startsWith("ja") -> "Japanese"
            raw.startsWith("ko") -> "Korean"
            raw.startsWith("zh") -> "Chinese"
            raw.isBlank() -> "Unknown"
            else -> raw.substringBefore('-').uppercase(Locale.ROOT)
        }
    }

    private fun detectContentType(document: Document, url: String): InkuExtensionContentType {
        val sample = (document.title() + " " + document.body().text().take(30_000) + " " + url)
            .lowercase(Locale.ROOT)
        val animeScore = listOf("anime", "episode", "watch", "season", "stream").count(sample::contains)
        val mangaScore = listOf("manga", "chapter", "read", "manhwa", "comic").count(sample::contains)
        return when {
            animeScore > 0 && mangaScore > 0 -> InkuExtensionContentType.Both
            mangaScore > animeScore -> InkuExtensionContentType.Manga
            else -> InkuExtensionContentType.Anime
        }
    }

    private fun detectListSelector(document: Document): String {
        return LIST_CANDIDATES
            .mapNotNull { selector ->
                val elements = runCatching { document.select(selector) }.getOrNull() ?: return@mapNotNull null
                val count = elements.size
                if (count !in 2..250) return@mapNotNull null
                val sample = elements.take(12)
                val links = sample.count { it.selectFirst("a[href]") != null }
                val images = sample.count { it.selectFirst("img") != null }
                val titles = sample.count { element ->
                    TITLE_CANDIDATES.any { candidate -> element.selectFirst(candidate)?.text()?.isNotBlank() == true }
                }
                val avgText = sample.map { it.text().length.coerceAtMost(300) }.average()
                val score = (links * 4) + (images * 3) + (titles * 3) + count.coerceAtMost(40) -
                    if (avgText > 260) 15 else 0
                selector to score
            }
            .maxByOrNull { it.second }
            ?.first
            .orEmpty()
    }

    private fun detectInsideSelector(element: Element, candidates: List<String>): String =
        candidates.firstOrNull { candidate ->
            runCatching { element.selectFirst(candidate) }.getOrNull() != null
        }.orEmpty()

    private fun detectGlobalSelector(document: Document, candidates: List<String>): String =
        candidates
            .mapNotNull { candidate ->
                val count = runCatching { document.select(candidate).size }.getOrDefault(0)
                candidate.takeIf { count > 0 }?.let { it to count }
            }
            .maxByOrNull { it.second }
            ?.first
            .orEmpty()

    private fun detectSearchPath(document: Document): String {
        document.select("form").forEach { form ->
            val input = form.select("input").firstOrNull { element ->
                element.attr("name").lowercase(Locale.ROOT) in SEARCH_PARAM_NAMES
            } ?: return@forEach
            val action = form.absUrl("action").ifBlank { form.attr("action") }.ifBlank { document.baseUri() }
            val parameter = input.attr("name")
            val separator = if ('?' in action) '&' else '?'
            val full = "$action$separator$parameter={query}"
            return relativeOrAbsolute(full, document.baseUri())
        }
        return ""
    }

    private fun detectQualities(vararg documents: Document?): List<String> {
        val regex = Regex("(?i)\\b(2160p|1440p|1080p|720p|480p|360p|240p|4k)\\b")
        return documents.filterNotNull()
            .flatMap { regex.findAll(it.text()).map { match -> match.value.uppercase(Locale.ROOT) }.toList() }
            .distinct()
            .sortedByDescending(::qualityRank)
            .take(8)
    }

    private fun qualityRank(value: String): Int = when (value.lowercase(Locale.ROOT)) {
        "4k", "2160p" -> 2160
        "1440p" -> 1440
        "1080p" -> 1080
        "720p" -> 720
        "480p" -> 480
        "360p" -> 360
        "240p" -> 240
        else -> 0
    }

    private fun extractTitle(card: Element, selector: String): String {
        val selected = selector.takeIf(String::isNotBlank)?.let(card::selectFirst)
        return selected?.attr("title").orEmpty()
            .ifBlank { selected?.text().orEmpty() }
            .ifBlank { card.attr("title") }
            .ifBlank { card.text() }
            .trim()
            .take(160)
    }

    private fun extractImageUrl(element: Element?, baseUri: String): String {
        if (element == null) return ""
        val raw = listOf("data-src", "data-lazy-src", "data-original", "src", "srcset")
            .firstNotNullOfOrNull { key -> element.attr(key).trim().takeIf(String::isNotBlank) }
            ?.substringBefore(' ')
            .orEmpty()
        return resolveUrl(baseUri, raw)
    }

    private fun absoluteLink(element: Element?, baseUri: String): String {
        if (element == null) return ""
        val raw = element.attr("href").ifBlank { element.attr("data-href") }
        return resolveUrl(baseUri, raw)
    }

    private fun resolveUrl(baseUri: String, raw: String): String {
        if (raw.isBlank() || raw.startsWith("javascript:") || raw.startsWith("#")) return ""
        return runCatching { URI(baseUri).resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun relativeOrAbsolute(url: String, baseUri: String): String {
        return runCatching {
            val base = URI(baseUri)
            val target = URI(url)
            if (base.host == target.host) {
                val path = target.rawPath.orEmpty().ifBlank { "/" }
                path + target.rawQuery?.let { "?$it" }.orEmpty()
            } else {
                target.toString()
            }
        }.getOrDefault(url)
    }

    private fun buildSearchUrl(manifest: InkuExtensionManifest, query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val path = manifest.searchPath.replace("{query}", encoded)
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            URI(manifest.baseUrl).resolve(path).toString()
        }
    }

    private fun failure(logs: MutableList<String>, message: String): ExtensionBuildResult {
        logs += "ERROR: $message"
        return ExtensionBuildResult(
            success = false,
            logs = logs,
            manifest = null,
            manifestJson = "",
            adapterCode = ""
        )
    }

    private fun generateAdapterCode(manifest: InkuExtensionManifest): String = """
        package com.inku.generated.${manifest.id.replace('-', '_')}

        /** Generated by Inku Extension Builder for public or user-owned sources. */
        class ${manifest.name.toKotlinClassName()}Adapter {
            val baseUrl = "${manifest.baseUrl}"
            val contentType = "${manifest.contentType.name}"
            val language = "${manifest.language}"
            val qualityOptions = listOf(${manifest.qualityOptions.joinToString { "\"${it.escapeKotlin()}\"" }})

            object Selectors {
                const val LIST = "${manifest.listSelector.escapeKotlin()}"
                const val TITLE = "${manifest.titleSelector.escapeKotlin()}"
                const val LINK = "${manifest.linkSelector.escapeKotlin()}"
                const val COVER = "${manifest.coverSelector.escapeKotlin()}"
                const val DESCRIPTION = "${manifest.descriptionSelector.escapeKotlin()}"
                const val CATEGORY = "${manifest.categorySelector.escapeKotlin()}"
                const val EPISODE = "${manifest.episodeSelector.escapeKotlin()}"
                const val MEDIA = "${manifest.mediaSelector.escapeKotlin()}"
                const val NEXT_PAGE = "${manifest.nextPageSelector.escapeKotlin()}"
            }
        }
    """.trimIndent()

    private fun slugify(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(50)

    private fun String.toKotlinClassName(): String =
        split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .joinToString("") { part ->
                part.replaceFirstChar { char -> char.uppercase(Locale.ROOT) }
            }
            .ifBlank { "CustomExtension" }

    private fun String.escapeKotlin(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    private val SEARCH_PARAM_NAMES = setOf("q", "query", "search", "keyword", "s", "term")
    private val LIST_CANDIDATES = listOf(
        "article",
        ".flw-item",
        ".film-poster-ahref",
        ".anime-item",
        ".manga-item",
        ".page-item-detail",
        ".bs",
        ".utao",
        ".postbody .post",
        ".c-tabs-item__content",
        ".row.c-tabs-item__content",
        ".card",
        ".item",
        ".post",
        ".entry",
        "main li"
    )
    private val TITLE_CANDIDATES = listOf(
        ".film-name",
        ".film-name a",
        ".title",
        ".name",
        "[class*=title]",
        "h2",
        "h3",
        "h4",
        "a[title]"
    )
    private val LINK_CANDIDATES = listOf(
        "a[href]",
        ".film-poster-ahref[href]",
        ".title a[href]",
        "h2 a[href]",
        "h3 a[href]"
    )
    private val COVER_CANDIDATES = listOf(
        "img[data-src]",
        "img[data-lazy-src]",
        "img[data-original]",
        "img[src]",
        "source[srcset]"
    )
    private val DESCRIPTION_CANDIDATES = listOf(
        ".description",
        ".summary",
        ".synopsis",
        ".excerpt",
        "p"
    )
    private val CATEGORY_CANDIDATES = listOf(
        ".genres a",
        ".genre a",
        "[class*=genre] a",
        ".categories a",
        ".category a",
        "a[href*=genre]",
        "a[href*=category]"
    )
    private val EPISODE_CANDIDATES = listOf(
        ".episodes a",
        ".episode a",
        ".episode",
        ".chapters a",
        ".chapter a",
        ".chapter",
        "a[href*=episode]",
        "a[href*=chapter]"
    )
    private val MEDIA_CANDIDATES = listOf(
        "video source[src]",
        "video[src]",
        "a[download][href]",
        "a[href$=.mp4]",
        "a[href$=.mkv]",
        "a[href$=.webm]"
    )
    private val NEXT_PAGE_CANDIDATES = listOf(
        "a[rel=next]",
        ".pagination .next a",
        ".next.page-numbers",
        "a.next"
    )
}
