package com.inku.app

import android.content.Context
import org.json.JSONArray

internal enum class LibraryMediaKind(val storageName: String) {
    Anime("anime"),
    Manga("manga")
}

internal object LibraryCategoryStore {
    private const val PREFS = "inku_library_categories_v2"

    fun categories(context: Context, kind: LibraryMediaKind): List<String> {
        val raw = prefs(context).getString("${kind.storageName}_categories", "[]") ?: "[]"
        return decodeList(raw)
    }

    fun saveCategories(
        context: Context,
        kind: LibraryMediaKind,
        categories: List<String>
    ) {
        val clean = categories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        prefs(context).edit()
            .putString("${kind.storageName}_categories", encodeList(clean))
            .apply()
    }

    fun assignments(
        context: Context,
        kind: LibraryMediaKind,
        mediaId: Int
    ): Set<String> {
        val raw = prefs(context).getString(
            assignmentKey(kind, mediaId),
            "[]"
        ) ?: "[]"
        return decodeList(raw).toSet()
    }

    fun setAssignments(
        context: Context,
        kind: LibraryMediaKind,
        mediaId: Int,
        categories: Set<String>
    ) {
        val valid = categories(context, kind).toSet()
        val clean = categories.filter { it in valid }
        prefs(context).edit()
            .putString(
                assignmentKey(kind, mediaId),
                encodeList(clean)
            )
            .apply()
    }

    fun renameCategory(
        context: Context,
        kind: LibraryMediaKind,
        oldName: String,
        newName: String,
        knownMediaIds: Collection<Int>
    ) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        val updated = categories(context, kind).map {
            if (it == oldName) clean else it
        }.distinctBy { it.lowercase() }
        saveCategories(context, kind, updated)
        knownMediaIds.forEach { id ->
            val current = assignments(context, kind, id)
            if (oldName in current) {
                setAssignments(
                    context,
                    kind,
                    id,
                    current - oldName + clean
                )
            }
        }
    }

    fun deleteCategory(
        context: Context,
        kind: LibraryMediaKind,
        name: String,
        knownMediaIds: Collection<Int>
    ) {
        saveCategories(context, kind, categories(context, kind) - name)
        knownMediaIds.forEach { id ->
            val current = assignments(context, kind, id)
            if (name in current) {
                setAssignments(context, kind, id, current - name)
            }
        }
    }

    private fun assignmentKey(kind: LibraryMediaKind, mediaId: Int): String =
        "${kind.storageName}_${mediaId}_assignments"

    private fun prefs(context: Context) = context.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    private fun encodeList(values: Collection<String>): String =
        JSONArray().apply { values.forEach(::put) }.toString()

    private fun decodeList(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }.getOrDefault(emptyList())
}
