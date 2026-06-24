package com.inku.app

import android.content.Context

internal object LibraryMembershipStore {
    private const val PREFS = "inku_library_membership"
    private const val ANIME_IDS = "anime_ids"
    private const val MANGA_IDS = "manga_ids"

    fun animeIds(context: Context): Set<Int> = read(context, ANIME_IDS)

    fun mangaIds(context: Context): Set<Int> = read(context, MANGA_IDS)

    fun containsAnime(context: Context, id: Int): Boolean = id in animeIds(context)

    fun containsManga(context: Context, id: Int): Boolean = id in mangaIds(context)

    fun setAnime(context: Context, id: Int, inLibrary: Boolean) {
        write(context, ANIME_IDS, animeIds(context).toMutableSet().apply {
            if (inLibrary) add(id) else remove(id)
        })
    }

    fun setManga(context: Context, id: Int, inLibrary: Boolean) {
        write(context, MANGA_IDS, mangaIds(context).toMutableSet().apply {
            if (inLibrary) add(id) else remove(id)
        })
    }

    private fun read(context: Context, key: String): Set<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

    private fun write(context: Context, key: String, ids: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(key, ids.map(Int::toString).toSet())
            .apply()
    }
}
