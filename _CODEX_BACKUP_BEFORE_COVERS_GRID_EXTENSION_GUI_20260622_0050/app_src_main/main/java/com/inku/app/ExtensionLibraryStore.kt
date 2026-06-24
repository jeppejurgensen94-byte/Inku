package com.inku.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal data class InkuExtensionLibraryEntry(
    val key: String,
    val mediaKind: LibraryMediaKind,
    val title: String,
    val mediaType: String,
    val coverUrl: String,
    val backdropUrl: String,
    val description: String,
    val genres: List<String>,
    val status: String,
    val sourceName: String,
    val sourceId: Long,
    val sourceClass: String,
    val extensionPackageName: String,
    val sourceUrl: String,
    val categories: Set<String>,
    val progress: Int,
    val metadataRefreshedAt: Long
) {
    val numericId: Int
        get() = stableExtensionLibraryNumericId(key)

    val categoryAssignmentId: Int
        get() = -numericId
}

internal object ExtensionLibraryStore {
    private const val PREFS = "inku_extension_library_v1"
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val SCHEMA_VERSION = 1

    fun contains(context: Context, kind: LibraryMediaKind, key: String): Boolean =
        key in keys(context, kind)

    fun find(context: Context, kind: LibraryMediaKind, key: String): InkuExtensionLibraryEntry? {
        val raw = prefs(context).getString(entryKey(kind, key), null) ?: return null
        return decode(raw).getOrNull()
    }

    fun entries(context: Context, kind: LibraryMediaKind): List<InkuExtensionLibraryEntry> =
        keys(context, kind).mapNotNull { find(context, kind, it) }

    fun findByMediaId(
        context: Context,
        kind: LibraryMediaKind,
        mediaId: Int
    ): InkuExtensionLibraryEntry? =
        entries(context, kind).firstOrNull { it.categoryAssignmentId == mediaId }

    fun save(context: Context, entry: InkuExtensionLibraryEntry) {
        ensureSchema(context)
        val validCategories = LibraryCategoryStore.categories(context, entry.mediaKind).toSet()
        val cleanEntry = entry.copy(
            categories = entry.categories.filterTo(mutableSetOf()) { it in validCategories }
        )
        val updatedKeys = keys(context, entry.mediaKind) + cleanEntry.key
        prefs(context).edit()
            .putStringSet(keysKey(entry.mediaKind), updatedKeys)
            .putString(entryKey(entry.mediaKind, cleanEntry.key), encode(cleanEntry))
            .apply()
        LibraryCategoryStore.setAssignments(
            context = context,
            kind = cleanEntry.mediaKind,
            mediaId = cleanEntry.categoryAssignmentId,
            categories = cleanEntry.categories
        )
    }

    fun remove(context: Context, kind: LibraryMediaKind, key: String) {
        val updatedKeys = keys(context, kind) - key
        prefs(context).edit()
            .putStringSet(keysKey(kind), updatedKeys)
            .remove(entryKey(kind, key))
            .apply()
        LibraryCategoryStore.setAssignments(
            context = context,
            kind = kind,
            mediaId = -stableExtensionLibraryNumericId(key),
            categories = emptySet()
        )
    }

    private fun keys(context: Context, kind: LibraryMediaKind): Set<String> {
        ensureSchema(context)
        return prefs(context).getStringSet(keysKey(kind), emptySet()).orEmpty()
    }

    private fun keysKey(kind: LibraryMediaKind): String =
        "${kind.storageName}_extension_keys"

    private fun entryKey(kind: LibraryMediaKind, key: String): String =
        "${kind.storageName}_extension_entry_$key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureSchema(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(SCHEMA_VERSION_KEY)) {
            prefs.edit().putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION).apply()
        }
    }

    private fun encode(entry: InkuExtensionLibraryEntry): String =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("key", entry.key)
            .put("mediaKind", entry.mediaKind.storageName)
            .put("title", entry.title)
            .put("mediaType", entry.mediaType)
            .put("coverUrl", entry.coverUrl)
            .put("backdropUrl", entry.backdropUrl)
            .put("description", entry.description)
            .put("genres", JSONArray().apply { entry.genres.forEach { put(it) } })
            .put("status", entry.status)
            .put("sourceName", entry.sourceName)
            .put("sourceId", entry.sourceId)
            .put("sourceClass", entry.sourceClass)
            .put("extensionPackageName", entry.extensionPackageName)
            .put("sourceUrl", entry.sourceUrl)
            .put("categories", JSONArray().apply { entry.categories.forEach { put(it) } })
            .put("progress", entry.progress)
            .put("metadataRefreshedAt", entry.metadataRefreshedAt)
            .toString()

    private fun decode(raw: String): Result<InkuExtensionLibraryEntry> = runCatching {
        val json = JSONObject(raw)
        val kind = when (json.optString("mediaKind")) {
            LibraryMediaKind.Anime.storageName -> LibraryMediaKind.Anime
            else -> LibraryMediaKind.Manga
        }
        InkuExtensionLibraryEntry(
            key = json.getString("key"),
            mediaKind = kind,
            title = json.optString("title"),
            mediaType = json.optString("mediaType"),
            coverUrl = json.optString("coverUrl"),
            backdropUrl = json.optString("backdropUrl"),
            description = json.optString("description"),
            genres = json.optJSONArray("genres").toStringList(),
            status = json.optString("status"),
            sourceName = json.optString("sourceName"),
            sourceId = json.optLong("sourceId"),
            sourceClass = json.optString("sourceClass"),
            extensionPackageName = json.optString("extensionPackageName"),
            sourceUrl = json.optString("sourceUrl"),
            categories = json.optJSONArray("categories").toStringList().toSet(),
            progress = json.optInt("progress"),
            metadataRefreshedAt = json.optLong("metadataRefreshedAt")
        )
    }
}

internal fun extensionLibraryKey(
    kind: LibraryMediaKind,
    extensionPackageName: String,
    sourceId: Long,
    sourceClass: String,
    sourceUrl: String
): String =
    listOf(kind.storageName, extensionPackageName, sourceId.toString(), sourceClass, sourceUrl)
        .joinToString("|")

internal fun stableExtensionLibraryNumericId(key: String): Int {
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    val raw = ((digest[0].toInt() and 0xFF) shl 24) or
        ((digest[1].toInt() and 0xFF) shl 16) or
        ((digest[2].toInt() and 0xFF) shl 8) or
        (digest[3].toInt() and 0xFF)
    return (raw and Int.MAX_VALUE).takeIf { it != 0 } ?: 1
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
