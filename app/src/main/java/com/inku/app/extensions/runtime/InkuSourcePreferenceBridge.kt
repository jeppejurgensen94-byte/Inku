package com.inku.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.preferenceKey as animePreferenceKey
import eu.kanade.tachiyomi.animesource.sourcePreferences as animeSourcePreferences
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey as mangaPreferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences as mangaSourcePreferences

internal object InkuSourcePreferenceBridge {
    private const val TAG = "InkuSourceRuntime"
    private const val INITIALIZED_KEY = "_inku_source_preferences_initialized"

    fun bootstrap(context: Context, source: Any) {
        when (source) {
            is ConfigurableSource -> bootstrapManga(context, source)
            is ConfigurableAnimeSource -> bootstrapAnime(context, source)
        }
    }

    private fun bootstrapManga(context: Context, source: ConfigurableSource) {
        val key = source.mangaPreferenceKey()
        val prefs = source.mangaSourcePreferences()
        bootstrapPreferenceScreen(context, key, prefs) { screen ->
            source.setupPreferenceScreen(screen)
        }
    }

    private fun bootstrapAnime(context: Context, source: ConfigurableAnimeSource) {
        val key = source.animePreferenceKey()
        val prefs = source.animeSourcePreferences()
        bootstrapPreferenceScreen(context, key, prefs) { screen ->
            source.setupPreferenceScreen(screen)
        }
    }

    private fun bootstrapPreferenceScreen(
        context: Context,
        sharedPreferencesName: String,
        prefs: SharedPreferences,
        setup: (androidx.preference.PreferenceScreen) -> Unit
    ) {
        if (prefs.getBoolean(INITIALIZED_KEY, false)) return
        val appContext = context.applicationContext
        val manager = PreferenceManager(appContext).apply {
            setSharedPreferencesName(sharedPreferencesName)
            setSharedPreferencesMode(Context.MODE_PRIVATE)
        }
        val screen = manager.createPreferenceScreen(appContext)
        setup(screen)
        manager.setPreferences(screen)
        val editor = prefs.edit()
        persistDefaults(screen, prefs, editor)
        editor.putBoolean(INITIALIZED_KEY, true)
        editor.apply()
        Log.i(TAG, "Initialized source preferences name=$sharedPreferencesName count=${screen.getPreferenceCount()}")
    }

    private fun persistDefaults(
        preference: Preference,
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val key = preference.key
        if (!key.isNullOrBlank() && !prefs.contains(key)) {
            when (preference) {
                is MultiSelectListPreference -> editor.putStringSet(key, preference.values.orEmpty())
                is ListPreference -> preference.value?.let { editor.putString(key, it) }
                is EditTextPreference -> preference.text?.let { editor.putString(key, it) }
                is TwoStatePreference -> editor.putBoolean(key, preference.isChecked)
            }
        }
        if (preference is PreferenceGroup) {
            for (index in 0 until preference.getPreferenceCount()) {
                persistDefaults(preference.getPreference(index), prefs, editor)
            }
        }
    }
}
