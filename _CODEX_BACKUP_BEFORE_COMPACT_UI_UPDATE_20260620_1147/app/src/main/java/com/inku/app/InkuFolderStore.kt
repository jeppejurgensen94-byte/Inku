package com.inku.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal data class LocalEpisodeFile(
    val number: Int,
    val title: String,
    val seasonName: String,
    val uriString: String,
    val fileName: String
)

internal data class LocalAnimeItem(
    val id: Int,
    val title: String,
    val description: String,
    val creator: String,
    val language: String,
    val type: String,
    val releaseState: String,
    val rating: Float,
    val genres: List<String>,
    val folderUri: String,
    val coverUri: String,
    val backgroundUri: String,
    val episodes: List<LocalEpisodeFile>
)

internal data class LocalMangaItem(
    val id: Int,
    val title: String,
    val description: String,
    val author: String,
    val language: String,
    val releaseState: String,
    val rating: Float,
    val genres: List<String>,
    val folderUri: String,
    val coverUri: String,
    val backgroundUri: String,
    val chapterCount: Int
)

internal data class LocalLibrarySnapshot(
    val anime: List<LocalAnimeItem>,
    val manga: List<LocalMangaItem>,
    val rootLabel: String
)

internal object InkuFolderStore {
    private const val PREFS = "inku_storage_root"
    private const val ROOT_URI = "root_tree_uri"

    private const val INKU_FOLDER = "Inku"
    const val ANIME_FOLDER = "Anime"
    const val MANGA_FOLDER = "Manga"
    const val DOWNLOADS_FOLDER = "Downloads"
    const val LOCAL_ANIME_FOLDER = "Local Anime"
    const val LOCAL_MANGA_FOLDER = "Local Manga"
    const val SUBTITLES_FOLDER = "Subtitles"
    const val COVERS_FOLDER = "Covers"
    const val BACKGROUNDS_FOLDER = "Backgrounds"
    const val THUMBNAILS_FOLDER = "Thumbnails"
    const val BACKUPS_FOLDER = "Backups"
    const val EXTENSIONS_FOLDER = "Extensions"
    const val EXTENSION_CACHE_FOLDER = "Extension Cache"
    const val REPOSITORY_CACHE_FOLDER = "Repository Cache"
    const val CUSTOM_EXTENSIONS_FOLDER = "Custom Extensions"
    const val FONTS_FOLDER = "Fonts"
    const val SHADERS_FOLDER = "Shaders"
    const val SCRIPTS_FOLDER = "Scripts"
    const val TEMP_FOLDER = "Temp"

    private val requiredFolders = listOf(
        ANIME_FOLDER,
        MANGA_FOLDER,
        DOWNLOADS_FOLDER,
        LOCAL_ANIME_FOLDER,
        LOCAL_MANGA_FOLDER,
        SUBTITLES_FOLDER,
        COVERS_FOLDER,
        BACKGROUNDS_FOLDER,
        THUMBNAILS_FOLDER,
        BACKUPS_FOLDER,
        EXTENSIONS_FOLDER,
        EXTENSION_CACHE_FOLDER,
        REPOSITORY_CACHE_FOLDER,
        CUSTOM_EXTENSIONS_FOLDER,
        FONTS_FOLDER,
        SHADERS_FOLDER,
        SCRIPTS_FOLDER,
        TEMP_FOLDER
    )

    private data class TreeEntry(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    fun isConfigured(context: Context): Boolean = rootTreeUri(context) != null

    fun ensurePrivateAppFolders(context: Context): Result<File> = runCatching {
        val root = File(context.getExternalFilesDir(null), INKU_FOLDER).apply { mkdirs() }
        requiredFolders.forEach { folderName ->
            File(root, folderName).mkdirs()
        }
        File(context.filesDir, "extensions").mkdirs()
        File(context.filesDir, "repository-cache").mkdirs()
        File(context.filesDir, "extension-cache").mkdirs()
        File(context.filesDir, "custom-extensions").mkdirs()
        File(context.cacheDir, "extension_apks").mkdirs()
        root
    }

    fun rootTreeUri(context: Context): Uri? = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ROOT_URI, null)
        ?.let(Uri::parse)

    fun rootLabel(context: Context): String {
        val treeUri = rootTreeUri(context) ?: return "Not connected"
        return queryDocument(context, treeUri, rootDocumentUri(treeUri))?.name
            ?.ifBlank { treeUri.lastPathSegment.orEmpty() }
            ?.ifBlank { "Connected folder" }
            ?: "Connected folder"
    }

    fun connectRoot(context: Context, treeUri: Uri): Result<Unit> = runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ROOT_URI, treeUri.toString())
            .apply()
        ensureStructure(context).getOrThrow()
    }

    fun disconnect(context: Context) {
        val oldUri = rootTreeUri(context)
        if (oldUri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ROOT_URI)
            .apply()
    }

    fun ensureStructure(context: Context): Result<Unit> = runCatching {
        val treeUri = rootTreeUri(context)
            ?: error("Choose a main Inku folder first.")
        val root = rootDocumentUri(treeUri)
        val inku = ensureDirectory(context, treeUri, root, INKU_FOLDER)
        requiredFolders.forEach { folderName ->
            ensureDirectory(context, treeUri, inku.uri, folderName)
        }
    }

    fun folderUri(context: Context, folderName: String): Uri? {
        val treeUri = rootTreeUri(context) ?: return null
        val root = rootDocumentUri(treeUri)
        val inku = findChild(context, treeUri, root, INKU_FOLDER) ?: return null
        return findChild(context, treeUri, inku.uri, folderName)?.uri
    }

    fun createDownloadFile(
        context: Context,
        kind: String,
        title: String,
        fileName: String,
        mimeType: String
    ): Result<Uri> = runCatching {
        ensureStructure(context).getOrThrow()
        val treeUri = rootTreeUri(context) ?: error("No Inku folder selected.")
        val downloads = folderUri(context, DOWNLOADS_FOLDER)
            ?: error("Downloads folder could not be opened.")
        val kindFolderName = if (kind.equals("manga", ignoreCase = true)) {
            "Manga"
        } else {
            "Anime"
        }
        val kindFolder = ensureDirectory(context, treeUri, downloads, kindFolderName)
        val titleFolder = ensureDirectory(
            context,
            treeUri,
            kindFolder.uri,
            "${sanitizeFileName(title)} - EN"
        )
        val cleanName = sanitizeFileName(fileName).ifBlank {
            "download-${System.currentTimeMillis()}"
        }
        findChild(context, treeUri, titleFolder.uri, cleanName)?.uri?.let { existing ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, existing) }
        }
        DocumentsContract.createDocument(
            context.contentResolver,
            titleFolder.uri,
            mimeType.ifBlank { "application/octet-stream" },
            cleanName
        ) ?: error("Android could not create the download file.")
    }

    fun scan(context: Context): LocalLibrarySnapshot {
        ensureStructure(context).getOrThrow()
        val anime = scanAnime(context)
        val manga = scanManga(context)
        return LocalLibrarySnapshot(
            anime = anime,
            manga = manga,
            rootLabel = rootLabel(context)
        )
    }

    private fun scanAnime(context: Context): List<LocalAnimeItem> {
        val treeUri = rootTreeUri(context) ?: return emptyList()
        val root = folderUri(context, LOCAL_ANIME_FOLDER) ?: return emptyList()
        return listChildren(context, treeUri, root)
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .mapNotNull { titleFolder ->
                runCatching {
                    val directChildren = listChildren(context, treeUri, titleFolder.uri)
                    val metadata = readMetadata(context, directChildren)
                    val collected = collectFiles(
                        context = context,
                        treeUri = treeUri,
                        parent = titleFolder,
                        seasonName = "Season 1",
                        depth = 0
                    )
                    val videoFiles = collected
                        .filter { (_, entry) -> isVideo(entry) }
                        .sortedWith(
                            compareBy<Pair<String, TreeEntry>> { it.first.lowercase(Locale.ROOT) }
                                .thenBy { parseEpisodeNumber(it.second.name) ?: Int.MAX_VALUE }
                                .thenBy { it.second.name.lowercase(Locale.ROOT) }
                        )
                    var fallbackNumber = 1
                    val episodes = videoFiles.map { (season, entry) ->
                        val parsed = parseEpisodeNumber(entry.name)
                        val number = parsed ?: fallbackNumber
                        fallbackNumber = maxOf(fallbackNumber + 1, number + 1)
                        LocalEpisodeFile(
                            number = number,
                            title = cleanEpisodeTitle(entry.name, number),
                            seasonName = season.ifBlank { "Season 1" },
                            uriString = entry.uri.toString(),
                            fileName = entry.name
                        )
                    }.sortedBy { it.number }

                    val cover = directChildren.firstOrNull { isCover(it) }
                    val background = directChildren.firstOrNull { isBackground(it) }
                    val title = metadata.optString("title").trim().ifBlank {
                        titleFolder.name
                    }
                    LocalAnimeItem(
                        id = stableId("anime:${titleFolder.uri}"),
                        title = title,
                        description = metadata.optString("description").trim(),
                        creator = metadata.optString("creator").trim().ifBlank {
                            metadata.optString("author").trim().ifBlank { "Unknown" }
                        },
                        language = metadata.optString("language").trim().ifBlank { "Local" },
                        type = metadata.optString("type").trim().ifBlank {
                            if (episodes.size == 1) "Movie" else "TV"
                        },
                        releaseState = metadata.optString("status").trim().ifBlank { "Local" },
                        rating = metadata.optDouble("rating", 0.0).toFloat().coerceIn(0f, 5f),
                        genres = jsonStringList(metadata, "genres"),
                        folderUri = titleFolder.uri.toString(),
                        coverUri = cover?.uri?.toString().orEmpty(),
                        backgroundUri = background?.uri?.toString().orEmpty(),
                        episodes = episodes
                    )
                }.getOrNull()
            }
    }

    private fun scanManga(context: Context): List<LocalMangaItem> {
        val treeUri = rootTreeUri(context) ?: return emptyList()
        val root = folderUri(context, LOCAL_MANGA_FOLDER) ?: return emptyList()
        return listChildren(context, treeUri, root)
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .mapNotNull { titleFolder ->
                runCatching {
                    val directChildren = listChildren(context, treeUri, titleFolder.uri)
                    val metadata = readMetadata(context, directChildren)
                    val collected = collectFiles(
                        context = context,
                        treeUri = treeUri,
                        parent = titleFolder,
                        seasonName = "",
                        depth = 0
                    ).map { it.second }
                    val chapterDirectories = directChildren.count { it.isDirectory }
                    val archiveCount = collected.count { isMangaArchive(it) }
                    val pageCount = collected.count { isImage(it) }
                    val chapterCount = when {
                        metadata.optInt("chapters", 0) > 0 -> metadata.optInt("chapters")
                        chapterDirectories > 0 -> chapterDirectories
                        archiveCount > 0 -> archiveCount
                        pageCount > 0 -> 1
                        else -> 0
                    }
                    val cover = directChildren.firstOrNull { isCover(it) }
                    val background = directChildren.firstOrNull { isBackground(it) }
                    LocalMangaItem(
                        id = stableId("manga:${titleFolder.uri}"),
                        title = metadata.optString("title").trim().ifBlank { titleFolder.name },
                        description = metadata.optString("description").trim(),
                        author = metadata.optString("author").trim().ifBlank { "Unknown" },
                        language = metadata.optString("language").trim().ifBlank { "Local" },
                        releaseState = metadata.optString("status").trim().ifBlank { "Local" },
                        rating = metadata.optDouble("rating", 0.0).toFloat().coerceIn(0f, 5f),
                        genres = jsonStringList(metadata, "genres"),
                        folderUri = titleFolder.uri.toString(),
                        coverUri = cover?.uri?.toString().orEmpty(),
                        backgroundUri = background?.uri?.toString().orEmpty(),
                        chapterCount = chapterCount
                    )
                }.getOrNull()
            }
    }

    private fun collectFiles(
        context: Context,
        treeUri: Uri,
        parent: TreeEntry,
        seasonName: String,
        depth: Int
    ): List<Pair<String, TreeEntry>> {
        if (depth > 5) return emptyList()
        val children = listChildren(context, treeUri, parent.uri)
        return buildList {
            children.forEach { child ->
                if (child.isDirectory) {
                    val nextSeason = if (depth == 0) child.name else seasonName
                    addAll(
                        collectFiles(
                            context,
                            treeUri,
                            child,
                            nextSeason.ifBlank { "Season 1" },
                            depth + 1
                        )
                    )
                } else {
                    add(seasonName.ifBlank { "Season 1" } to child)
                }
            }
        }
    }

    private fun readMetadata(context: Context, children: List<TreeEntry>): JSONObject {
        val metadata = children.firstOrNull {
            it.name.equals("metadata.json", ignoreCase = true) ||
                    it.name.equals("metadata.js", ignoreCase = true)
        } ?: return JSONObject()
        return runCatching {
            val raw = context.contentResolver.openInputStream(metadata.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
                .trim()
            val jsonText = raw.substringAfter('{', "").let {
                if (it.isBlank()) raw else "{$it"
            }.substringBeforeLast(';').trim()
            JSONObject(jsonText)
        }.getOrDefault(JSONObject())
    }

    private fun jsonStringList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return json.optString(key)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

    private fun ensureDirectory(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        name: String
    ): TreeEntry {
        findChild(context, treeUri, parentUri, name)?.let { existing ->
            require(existing.isDirectory) { "$name exists but is not a folder." }
            return existing
        }
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: error("Could not create folder: $name")
        return queryDocument(context, treeUri, created)
            ?: TreeEntry(
                documentId = DocumentsContract.getDocumentId(created),
                uri = created,
                name = name,
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                size = 0L,
                lastModified = System.currentTimeMillis()
            )
    }

    private fun findChild(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        name: String
    ): TreeEntry? = listChildren(context, treeUri, parentUri)
        .firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun listChildren(
        context: Context,
        treeUri: Uri,
        parentUri: Uri
    ): List<TreeEntry> {
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(treeUri) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentId
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return runCatching {
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val sizeIndex = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_SIZE
                )
                val modifiedIndex = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                buildList {
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(idIndex)
                        add(
                            TreeEntry(
                                documentId = documentId,
                                uri = DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri,
                                    documentId
                                ),
                                name = cursor.getString(nameIndex).orEmpty(),
                                mimeType = cursor.getString(mimeIndex).orEmpty(),
                                size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                    cursor.getLong(sizeIndex)
                                } else 0L,
                                lastModified = if (
                                    modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)
                                ) {
                                    cursor.getLong(modifiedIndex)
                                } else 0L
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun queryDocument(
        context: Context,
        treeUri: Uri,
        documentUri: Uri
    ): TreeEntry? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return runCatching {
            context.contentResolver.query(
                documentUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getString(0)
                TreeEntry(
                    documentId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = cursor.getString(1).orEmpty(),
                    mimeType = cursor.getString(2).orEmpty(),
                    size = if (!cursor.isNull(3)) cursor.getLong(3) else 0L,
                    lastModified = if (!cursor.isNull(4)) cursor.getLong(4) else 0L
                )
            }
        }.getOrNull()
    }

    private fun isVideo(entry: TreeEntry): Boolean =
        entry.mimeType.startsWith("video/") || entry.name.extension() in setOf(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts"
        )

    private fun isImage(entry: TreeEntry): Boolean =
        entry.mimeType.startsWith("image/") || entry.name.extension() in setOf(
            "jpg", "jpeg", "png", "webp", "avif", "gif"
        )

    private fun isMangaArchive(entry: TreeEntry): Boolean =
        entry.name.extension() in setOf("cbz", "cbr", "zip", "rar", "pdf", "epub")

    private fun isCover(entry: TreeEntry): Boolean {
        val base = entry.name.substringBeforeLast('.').lowercase(Locale.ROOT)
        return isImage(entry) && base in setOf("cover", "poster", "thumbnail", "thumb")
    }

    private fun isBackground(entry: TreeEntry): Boolean {
        val base = entry.name.substringBeforeLast('.').lowercase(Locale.ROOT)
        return isImage(entry) && base in setOf("background", "banner", "backdrop", "fanart")
    }

    private fun parseEpisodeNumber(name: String): Int? {
        val patterns = listOf(
            Regex("(?i)s\\d{1,2}[ ._-]*e(\\d{1,4})"),
            Regex("(?i)(?:episode|ep|e)[ ._-]*(\\d{1,4})"),
            Regex("(?<!\\d)(\\d{1,4})(?!\\d)")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private fun cleanEpisodeTitle(name: String, number: Int): String {
        val withoutExtension = name.substringBeforeLast('.', name)
        val cleaned = withoutExtension
            .replace(Regex("(?i)s\\d{1,2}[ ._-]*e\\d{1,4}"), "")
            .replace(Regex("(?i)(?:episode|ep|e)[ ._-]*\\d{1,4}"), "")
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_')
        return cleaned.ifBlank { "Episode $number" }
    }

    private fun String.extension(): String = substringAfterLast('.', "")
        .lowercase(Locale.ROOT)

    private fun stableId(value: String): Int = value.hashCode().let {
        if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it)
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
}
