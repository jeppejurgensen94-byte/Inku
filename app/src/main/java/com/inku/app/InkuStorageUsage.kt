package com.inku.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

internal data class InkuStorageCategorySpec(
    val id: String,
    val label: String,
    val roots: List<File>,
    val color: Color,
    val cleanable: Boolean = false
)

internal data class InkuStorageCategoryUsage(
    val id: String,
    val label: String,
    val bytes: Long,
    val fileCount: Int,
    val paths: List<String>,
    val color: Color,
    val cleanable: Boolean
)

internal data class InkuStorageUsage(
    val categories: List<InkuStorageCategoryUsage>
) {
    val totalBytes: Long = categories.sumOf { it.bytes }
}

internal fun calculateStorageUsage(specs: List<InkuStorageCategorySpec>): InkuStorageUsage =
    InkuStorageUsage(
        categories = specs.map { spec ->
            val stats = spec.roots.fold(StorageStats()) { acc, root ->
                acc + scanStorageRoot(root)
            }
            InkuStorageCategoryUsage(
                id = spec.id,
                label = spec.label,
                bytes = stats.bytes,
                fileCount = stats.files,
                paths = spec.roots.map { it.absolutePath },
                color = spec.color,
                cleanable = spec.cleanable
            )
        }
    )

internal fun calculateInkuStorageUsage(context: Context): InkuStorageUsage {
    InkuFolderStore.ensurePrivateAppFolders(context)
    return calculateStorageUsage(inkuStorageSpecs(context))
}

internal fun clearInkuStorageCategory(context: Context, categoryId: String): Result<Int> = runCatching {
    val spec = inkuStorageSpecs(context).firstOrNull { it.id == categoryId }
        ?: error("Unknown storage category.")
    require(spec.cleanable) { "This category is not safe to clear automatically." }
    spec.roots.sumOf { root -> deleteChildren(root) }
}

internal fun formatStorageSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
}

private data class StorageStats(
    val bytes: Long = 0L,
    val files: Int = 0
) {
    operator fun plus(other: StorageStats): StorageStats =
        StorageStats(bytes + other.bytes, files + other.files)
}

private fun scanStorageRoot(root: File): StorageStats {
    if (!root.exists()) return StorageStats()
    if (root.isFile) return StorageStats(root.length(), 1)
    return root.listFiles().orEmpty().fold(StorageStats()) { acc, child ->
        acc + scanStorageRoot(child)
    }
}

private fun deleteChildren(root: File): Int {
    if (!root.exists() || !root.isDirectory) return 0
    return root.listFiles().orEmpty().sumOf { child ->
        if (child.isDirectory) {
            val nested = deleteChildren(child)
            if (child.delete()) nested + 1 else nested
        } else if (child.delete()) {
            1
        } else {
            0
        }
    }
}

private fun inkuStorageSpecs(context: Context): List<InkuStorageCategorySpec> {
    val inkuRoot = File(context.getExternalFilesDir(null), "Inku")
    fun externalFolder(name: String): File = File(inkuRoot, name)
    fun filesFolder(name: String): File = File(context.filesDir, name)
    fun cacheFolder(name: String): File = File(context.cacheDir, name)

    val knownExternal = setOf(
        InkuFolderStore.ANIME_FOLDER,
        InkuFolderStore.MANGA_FOLDER,
        InkuFolderStore.DOWNLOADS_FOLDER,
        InkuFolderStore.LOCAL_ANIME_FOLDER,
        InkuFolderStore.LOCAL_MANGA_FOLDER,
        InkuFolderStore.SUBTITLES_FOLDER,
        InkuFolderStore.COVERS_FOLDER,
        InkuFolderStore.BACKGROUNDS_FOLDER,
        InkuFolderStore.THUMBNAILS_FOLDER,
        InkuFolderStore.BACKUPS_FOLDER,
        InkuFolderStore.EXTENSIONS_FOLDER,
        InkuFolderStore.EXTENSION_CACHE_FOLDER,
        InkuFolderStore.REPOSITORY_CACHE_FOLDER,
        InkuFolderStore.CUSTOM_EXTENSIONS_FOLDER,
        InkuFolderStore.FONTS_FOLDER,
        InkuFolderStore.SHADERS_FOLDER,
        InkuFolderStore.SCRIPTS_FOLDER,
        InkuFolderStore.TEMP_FOLDER
    )
    val otherExternal = inkuRoot.listFiles().orEmpty()
        .filter { it.name !in knownExternal }

    return listOf(
        InkuStorageCategorySpec(
            id = "anime_downloads",
            label = "Anime downloads",
            roots = listOf(externalFolder(InkuFolderStore.ANIME_FOLDER), File(externalFolder(InkuFolderStore.DOWNLOADS_FOLDER), "Anime")),
            color = Color(0xFF7FE0C2)
        ),
        InkuStorageCategorySpec(
            id = "manga_chapters",
            label = "Manga chapters",
            roots = listOf(externalFolder(InkuFolderStore.MANGA_FOLDER), File(externalFolder(InkuFolderStore.DOWNLOADS_FOLDER), "Manga")),
            color = Color(0xFFFFC857)
        ),
        InkuStorageCategorySpec(
            id = "covers",
            label = "Covers",
            roots = listOf(externalFolder(InkuFolderStore.COVERS_FOLDER)),
            color = Color(0xFFFF6B8A)
        ),
        InkuStorageCategorySpec(
            id = "backgrounds",
            label = "Backgrounds",
            roots = listOf(externalFolder(InkuFolderStore.BACKGROUNDS_FOLDER)),
            color = Color(0xFF7C8CF8)
        ),
        InkuStorageCategorySpec(
            id = "subtitles",
            label = "Subtitles",
            roots = listOf(externalFolder(InkuFolderStore.SUBTITLES_FOLDER)),
            color = Color(0xFFB8E986)
        ),
        InkuStorageCategorySpec(
            id = "extensions",
            label = "Extensions",
            roots = listOf(externalFolder(InkuFolderStore.EXTENSIONS_FOLDER), filesFolder("extensions")),
            color = Color(0xFF9B8CFF)
        ),
        InkuStorageCategorySpec(
            id = "repository_cache",
            label = "Repository cache",
            roots = listOf(externalFolder(InkuFolderStore.REPOSITORY_CACHE_FOLDER), filesFolder("repository-cache")),
            color = Color(0xFF43D9AD),
            cleanable = true
        ),
        InkuStorageCategorySpec(
            id = "image_cache",
            label = "Image cache",
            roots = listOf(externalFolder(InkuFolderStore.THUMBNAILS_FOLDER), cacheFolder("images"), cacheFolder("image_cache")),
            color = Color(0xFF4EA5FF),
            cleanable = true
        ),
        InkuStorageCategorySpec(
            id = "temporary_files",
            label = "Temporary files",
            roots = listOf(externalFolder(InkuFolderStore.TEMP_FOLDER), cacheFolder("extension_apks"), cacheFolder("downloads")),
            color = Color(0xFFFF8E57),
            cleanable = true
        ),
        InkuStorageCategorySpec(
            id = "backups",
            label = "Backups",
            roots = listOf(externalFolder(InkuFolderStore.BACKUPS_FOLDER)),
            color = Color(0xFFB7C0D8)
        ),
        InkuStorageCategorySpec(
            id = "other",
            label = "Other Inku files",
            roots = otherExternal,
            color = Color(0xFF6C7A86)
        )
    )
}
