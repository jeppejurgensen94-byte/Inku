package com.inku.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

internal enum class InkuRepositoryKind {
    Anime,
    Manga
}

internal data class InkuExtensionRepository(
    val id: String,
    val name: String,
    val url: String,
    val kind: InkuRepositoryKind,
    val enabled: Boolean = true,
    val lastUpdatedAt: Long = 0L,
    val extensionCount: Int = 0,
    val lastError: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("kind", kind.name)
        put("enabled", enabled)
        put("lastUpdatedAt", lastUpdatedAt)
        put("extensionCount", extensionCount)
        put("lastError", lastError)
    }

    companion object {
        fun fromJson(json: JSONObject): InkuExtensionRepository = InkuExtensionRepository(
            id = json.optString("id").ifBlank {
                stableRepositoryId(json.optString("kind"), json.optString("url"))
            },
            name = json.optString("name", "Repository"),
            url = json.optString("url"),
            kind = runCatching {
                InkuRepositoryKind.valueOf(json.optString("kind", InkuRepositoryKind.Manga.name))
            }.getOrDefault(InkuRepositoryKind.Manga),
            enabled = json.optBoolean("enabled", true),
            lastUpdatedAt = json.optLong("lastUpdatedAt", 0L),
            extensionCount = json.optInt("extensionCount", 0),
            lastError = json.optString("lastError")
        )
    }
}

internal data class InkuRepositorySource(
    val name: String,
    val language: String,
    val id: String,
    val baseUrl: String,
    val versionId: Long = 0L,
    val hasCloudflare: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("language", language)
        put("id", id)
        put("baseUrl", baseUrl)
        put("versionId", versionId)
        put("hasCloudflare", hasCloudflare)
    }

    companion object {
        fun fromJson(json: JSONObject): InkuRepositorySource = InkuRepositorySource(
            name = json.optString("name"),
            language = json.optString("language", json.optString("lang")),
            id = json.optString("id"),
            baseUrl = json.optString("baseUrl"),
            versionId = json.optLong("versionId", 0L),
            hasCloudflare = json.optBooleanFlexible("hasCloudflare")
        )
    }
}

internal data class InkuRepositoryExtension(
    val repositoryId: String,
    val repositoryName: String,
    val repositoryUrl: String,
    val kind: InkuRepositoryKind,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val language: String,
    val nsfw: Boolean,
    val apkUrl: String,
    val iconUrl: String,
    val compatibility: String,
    val updatedAt: Long,
    val sources: List<InkuRepositorySource>
) {
    val stableKey: String
        get() = "${kind.name}:${packageName.ifBlank { name }}"

    val displayName: String
        get() = name
            .removePrefix("Aniyomi:")
            .removePrefix("Tachiyomi:")
            .trim()
            .ifBlank { name }

    fun toJson(): JSONObject = JSONObject().apply {
        put("repositoryId", repositoryId)
        put("repositoryName", repositoryName)
        put("repositoryUrl", repositoryUrl)
        put("kind", kind.name)
        put("name", name)
        put("packageName", packageName)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("language", language)
        put("nsfw", nsfw)
        put("apkUrl", apkUrl)
        put("iconUrl", iconUrl)
        put("compatibility", compatibility)
        put("updatedAt", updatedAt)
        put("sources", JSONArray().apply { sources.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): InkuRepositoryExtension {
            val sourcesArray = json.optJSONArray("sources") ?: JSONArray()
            return InkuRepositoryExtension(
                repositoryId = json.optString("repositoryId"),
                repositoryName = json.optString("repositoryName"),
                repositoryUrl = json.optString("repositoryUrl"),
                kind = runCatching {
                    InkuRepositoryKind.valueOf(json.optString("kind", InkuRepositoryKind.Manga.name))
                }.getOrDefault(InkuRepositoryKind.Manga),
                name = json.optString("name"),
                packageName = json.optString("packageName"),
                versionName = json.optString("versionName"),
                versionCode = json.optLong("versionCode", 0L),
                language = json.optString("language"),
                nsfw = json.optBooleanFlexible("nsfw"),
                apkUrl = json.optString("apkUrl"),
                iconUrl = json.optString("iconUrl"),
                compatibility = json.optString("compatibility"),
                updatedAt = json.optLong("updatedAt", 0L),
                sources = buildList {
                    for (index in 0 until sourcesArray.length()) {
                        sourcesArray.optJSONObject(index)?.let {
                            add(InkuRepositorySource.fromJson(it))
                        }
                    }
                }
            )
        }
    }
}

internal data class InkuInstalledExtensionState(
    val installed: Boolean,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val updateAvailable: Boolean = false,
    val statusText: String = "Not installed"
)

internal object ExtensionRepositoryStore {
    private const val PREFS = "inku_extension_repositories"
    private const val REPOSITORIES_KEY = "repositories"
    private const val USER_AGENT = "Inku/0.13 Android extension repository"

    private val defaultRepositories = listOf(
        InkuExtensionRepository(
            id = "anime-yuzono",
            name = "Yuzono Anime",
            url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
            kind = InkuRepositoryKind.Anime
        ),
        InkuExtensionRepository(
            id = "anime-secozzi-aniyomi",
            name = "Secozzi Aniyomi",
            url = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json",
            kind = InkuRepositoryKind.Anime
        ),
        InkuExtensionRepository(
            id = "anime-kohi-den",
            name = "Kohi-den",
            url = "https://kohiden.xyz/Kohi-den/extensions/raw/branch/main/index.min.json",
            kind = InkuRepositoryKind.Anime
        ),
        InkuExtensionRepository(
            id = "manga-keiyoushi",
            name = "Keiyoushi",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        ),
        InkuExtensionRepository(
            id = "manga-yuzono-cursed",
            name = "Yuzono Cursed Manga",
            url = "https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        ),
        InkuExtensionRepository(
            id = "manga-yuzono",
            name = "Yuzono Manga",
            url = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        ),
        InkuExtensionRepository(
            id = "manga-kareadita",
            name = "Kareadita",
            url = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        ),
        InkuExtensionRepository(
            id = "manga-suwayomi",
            name = "Suwayomi",
            url = "https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        ),
        InkuExtensionRepository(
            id = "manga-thepbone-revived",
            name = "ThePBone Tachiyomi Extensions Revived",
            url = "https://raw.githubusercontent.com/ThePBone/tachiyomi-extensions-revived/repo/index.min.json",
            kind = InkuRepositoryKind.Manga
        )
    )

    fun repositoryCacheDirectory(context: Context): File =
        File(context.filesDir, "repository-cache").apply { mkdirs() }

    fun listRepositories(context: Context): List<InkuExtensionRepository> {
        val saved = readRepositories(context)
        val merged = mergeDefaults(saved)
        if (merged != saved) saveRepositories(context, merged)
        return merged
    }

    fun addRepository(
        context: Context,
        name: String,
        url: String,
        kind: InkuRepositoryKind
    ): InkuExtensionRepository {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
            "Use a complete http:// or https:// repository URL."
        }
        val existing = listRepositories(context)
        require(existing.none { it.kind == kind && it.url.equals(cleanUrl, ignoreCase = true) }) {
            "That repository already exists in ${kind.name}."
        }
        val repository = InkuExtensionRepository(
            id = stableRepositoryId(kind.name, cleanUrl),
            name = name.trim().ifBlank { cleanUrl.hostLabel() },
            url = cleanUrl,
            kind = kind
        )
        saveRepositories(context, existing + repository)
        return repository
    }

    fun updateRepository(context: Context, updated: InkuExtensionRepository) {
        val repositories = listRepositories(context).map {
            if (it.id == updated.id) updated else it
        }.distinctBy { it.id }
        saveRepositories(context, repositories)
    }

    fun removeRepository(context: Context, repository: InkuExtensionRepository) {
        saveRepositories(context, listRepositories(context).filterNot { it.id == repository.id })
        cacheFile(context, repository.id).delete()
        rawCacheFile(context, repository.id).delete()
    }

    fun cachedExtensions(context: Context, kind: InkuRepositoryKind? = null): List<InkuRepositoryExtension> {
        val enabledRepositoryIds = listRepositories(context)
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        return repositoryCacheDirectory(context)
            .listFiles { file -> file.name.endsWith(".extensions.json") }
            .orEmpty()
            .flatMap { file -> readExtensionCache(file) }
            .filter { it.repositoryId in enabledRepositoryIds }
            .filter { kind == null || it.kind == kind }
    }

    fun deduplicatedExtensions(
        context: Context,
        kind: InkuRepositoryKind? = null
    ): List<InkuRepositoryExtension> =
        cachedExtensions(context, kind)
            .groupBy { it.packageName.ifBlank { it.stableKey } }
            .map { (_, entries) ->
                entries.maxWithOrNull(
                    compareBy<InkuRepositoryExtension> { it.versionCode }
                        .thenBy { it.versionName }
                        .thenBy { it.repositoryName }
                ) ?: entries.first()
            }
            .sortedWith(
                compareBy<InkuRepositoryExtension> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.repositoryName.lowercase(Locale.ROOT) }
            )

    suspend fun refreshEnabled(context: Context): List<InkuExtensionRepository> =
        withContext(Dispatchers.IO) {
            val refreshed = listRepositories(context).map { repository ->
                if (repository.enabled) {
                    refreshRepository(context, repository)
                } else {
                    repository
                }
            }
            saveRepositories(context, refreshed)
            refreshed
        }

    suspend fun refreshRepository(
        context: Context,
        repository: InkuExtensionRepository
    ): InkuExtensionRepository = withContext(Dispatchers.IO) {
        runCatching {
            val raw = fetchTextWithRetry(repository.url)
            val extensions = parseRepositoryIndex(raw, repository)
            rawCacheFile(context, repository.id).writeText(raw, Charsets.UTF_8)
            writeExtensionCache(context, repository.id, extensions)
            repository.copy(
                lastUpdatedAt = System.currentTimeMillis(),
                extensionCount = extensions.size,
                lastError = ""
            )
        }.getOrElse { error ->
            val cachedCount = readExtensionCache(cacheFile(context, repository.id)).size
            repository.copy(
                extensionCount = cachedCount,
                lastError = error.message?.take(240) ?: error.javaClass.simpleName
            )
        }.also { updated ->
            updateRepository(context, updated)
        }
    }

    suspend fun testRepository(repository: InkuExtensionRepository): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseRepositoryIndex(fetchTextWithRetry(repository.url), repository).size
            }
        }

    fun installedState(
        context: Context,
        extension: InkuRepositoryExtension
    ): InkuInstalledExtensionState {
        val info = packageInfo(context, extension.packageName) ?: return InkuInstalledExtensionState(
            installed = false,
            statusText = "Not installed"
        )
        val installedCode = info.longVersionCodeCompat()
        val updateAvailable = extension.versionCode > installedCode && extension.versionCode > 0L
        return InkuInstalledExtensionState(
            installed = true,
            versionName = info.versionName.orEmpty(),
            versionCode = installedCode,
            updateAvailable = updateAvailable,
            statusText = if (extension.compatibility.isBlank()) {
                if (updateAvailable) "Update available" else "Installed"
            } else {
                "Installed - incompatible with this Inku version"
            }
        )
    }

    private fun readRepositories(context: Context): List<InkuExtensionRepository> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(REPOSITORIES_KEY, null)
            ?: return defaultRepositories
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let {
                        val repository = InkuExtensionRepository.fromJson(it)
                        if (repository.url.isNotBlank()) add(repository)
                    }
                }
            }
        }.getOrDefault(defaultRepositories)
    }

    private fun saveRepositories(context: Context, repositories: List<InkuExtensionRepository>) {
        val array = JSONArray()
        repositories
            .distinctBy { it.kind.name + ":" + it.url.lowercase(Locale.ROOT) }
            .forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(REPOSITORIES_KEY, array.toString())
            .apply()
    }

    private fun mergeDefaults(saved: List<InkuExtensionRepository>): List<InkuExtensionRepository> {
        val existingKeys = saved.map { it.kind.name + ":" + it.url.lowercase(Locale.ROOT) }.toSet()
        val missingDefaults = defaultRepositories.filter {
            it.kind.name + ":" + it.url.lowercase(Locale.ROOT) !in existingKeys
        }
        return (saved + missingDefaults)
            .distinctBy { it.kind.name + ":" + it.url.lowercase(Locale.ROOT) }
    }

    private fun parseRepositoryIndex(
        raw: String,
        repository: InkuExtensionRepository
    ): List<InkuRepositoryExtension> {
        val trimmed = raw.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("extensions")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("repo")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.firstString("pkg", "package", "packageName", "applicationId")
                val apk = item.firstString("apk", "apkUrl", "downloadUrl", "url")
                val name = item.firstString("name", "title", "label").ifBlank { packageName }
                if (packageName.isBlank() || apk.isBlank()) continue
                val sources = item.optJSONArray("sources")?.let { sourceArray ->
                    buildList {
                        for (sourceIndex in 0 until sourceArray.length()) {
                            sourceArray.optJSONObject(sourceIndex)?.let { source ->
                                add(
                                    InkuRepositorySource(
                                        name = source.optString("name"),
                                        language = source.firstString("lang", "language"),
                                        id = source.optString("id"),
                                        baseUrl = source.optString("baseUrl"),
                                        versionId = source.optLong("versionId", 0L),
                                        hasCloudflare = source.optBooleanFlexible("hasCloudflare")
                                    )
                                )
                            }
                        }
                    }
                }.orEmpty()
                add(
                    InkuRepositoryExtension(
                        repositoryId = repository.id,
                        repositoryName = repository.name,
                        repositoryUrl = repository.url,
                        kind = repository.kind,
                        name = name,
                        packageName = packageName,
                        versionName = item.firstString("version", "versionName"),
                        versionCode = item.firstLong("code", "versionCode"),
                        language = item.firstString("lang", "language").ifBlank { "all" },
                        nsfw = item.optBooleanFlexible("nsfw"),
                        apkUrl = resolveRepositoryAssetUrl(repository.url, apk, "apk"),
                        iconUrl = resolveRepositoryAssetUrl(
                            repository.url,
                            item.firstString("icon", "iconUrl", "image", "thumbnail")
                                .ifBlank { "$packageName.png" },
                            "icon"
                        ),
                        compatibility = compatibilityMessage(packageName, name, repository.kind),
                        updatedAt = item.firstLong("updated", "updatedAt", "lastUpdated", "date"),
                        sources = sources
                    )
                )
            }
        }
    }

    private fun compatibilityMessage(
        packageName: String,
        name: String,
        kind: InkuRepositoryKind
    ): String {
        val lower = "$packageName $name".lowercase(Locale.ROOT)
        return when {
            "aniyomi" in lower || ".animeextension." in lower ->
                "Aniyomi extensions require the Aniyomi/Tachiyomi host API, which is not present in this Inku build."
            "tachiyomi" in lower || ".extension." in lower || kind == InkuRepositoryKind.Manga ->
                "Tachiyomi/Mihon extensions require a source host bridge, which is not present in this Inku build."
            else -> "This APK extension format is not supported by this Inku build."
        }
    }

    private fun writeExtensionCache(
        context: Context,
        repositoryId: String,
        extensions: List<InkuRepositoryExtension>
    ) {
        val array = JSONArray()
        extensions.forEach { array.put(it.toJson()) }
        cacheFile(context, repositoryId).writeText(array.toString(), Charsets.UTF_8)
    }

    private fun readExtensionCache(file: File): List<InkuRepositoryExtension> =
        runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let {
                        add(InkuRepositoryExtension.fromJson(it))
                    }
                }
            }
        }.getOrDefault(emptyList())

    private fun cacheFile(context: Context, repositoryId: String): File =
        File(repositoryCacheDirectory(context), "$repositoryId.extensions.json")

    private fun rawCacheFile(context: Context, repositoryId: String): File =
        File(repositoryCacheDirectory(context), "$repositoryId.raw.json")

    private fun fetchTextWithRetry(url: String): String {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            runCatching { fetchText(url) }
                .onSuccess { return it }
                .onFailure { error ->
                    lastError = error
                    if (attempt == 0) Thread.sleep(450L)
                }
        }
        throw lastError ?: IllegalStateException("Repository could not be fetched.")
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 25_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val status = connection.responseCode
            require(status in 200..299) { "Repository returned HTTP $status." }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun packageInfo(context: Context, packageName: String): PackageInfo? =
        runCatching {
            val manager = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
}

private fun stableRepositoryId(kind: String, url: String): String =
    "repo-${kind.lowercase(Locale.ROOT)}-${url.lowercase(Locale.ROOT).hashCode().ushr(1)}"

private fun JSONObject.firstString(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotBlank() } }.orEmpty()

private fun JSONObject.firstLong(vararg keys: String): Long =
    keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    } ?: 0L

private fun JSONObject.optBooleanFlexible(key: String): Boolean =
    when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> false
    }

private fun resolveRepositoryAssetUrl(repositoryUrl: String, raw: String, folder: String): String {
    if (raw.isBlank()) return ""
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    val path = if ('/' in raw) raw else "$folder/$raw"
    return runCatching { URI(repositoryUrl).resolve(path).toString() }.getOrDefault(raw)
}

private fun String.hostLabel(): String =
    runCatching { URI(this).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .ifBlank { "Repository" }

private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
