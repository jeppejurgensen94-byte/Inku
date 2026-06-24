package com.inku.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

internal object InkuBackupStore {
    private val preferenceFiles = listOf(
        "inku_library_state",
        "inku_library_categories_v2",
        "inku_profile_edits",
        "inku_more_settings",
        "inku_settings",
        "inku_extensions",
        "inku_download_store",
        "inku_storage"
    )

    fun createBackup(context: Context): JSONObject = JSONObject().apply {
        put("format", "inku-backup")
        put("version", 1)
        put("createdAt", System.currentTimeMillis())
        put("preferences", JSONObject().apply {
            preferenceFiles.forEach { name ->
                val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                put(name, JSONObject().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Boolean -> put(key, value)
                            is Int -> put(key, value)
                            is Long -> put(key, value)
                            is Float -> put(key, value.toDouble())
                            is Set<*> -> put(key, JSONArray(value.filterIsInstance<String>()))
                        }
                    }
                })
            }
        })
        put("extensions", JSONArray().apply {
            ExtensionStore.listCustom(context).forEach { manifest ->
                put(manifest.toJson())
            }
        })
    }

    fun writeBackup(context: Context, uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(createBackup(context).toString(2))
        } ?: error("Could not open the selected backup file.")
    }

    fun restoreBackup(context: Context, uri: Uri): Result<String> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readText()
        } ?: error("Could not read the selected backup file.")
        val root = JSONObject(raw)
        require(root.optString("format") == "inku-backup") {
            "This is not an Inku backup."
        }

        val preferences = root.optJSONObject("preferences") ?: JSONObject()
        preferenceFiles.forEach { name ->
            val values = preferences.optJSONObject(name) ?: return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { key ->
                when (val value = values.get(key)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is JSONArray -> {
                        val set = buildSet {
                            for (index in 0 until value.length()) {
                                add(value.optString(index))
                            }
                        }
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.apply()
        }

        val extensions = root.optJSONArray("extensions") ?: JSONArray()
        for (index in 0 until extensions.length()) {
            val manifest = InkuExtensionManifest.fromJson(extensions.getJSONObject(index))
            ExtensionStore.save(context, manifest)
        }

        InkuRuntimeSettings.initialize(context)
        "Backup restored. Restart Inku to refresh every screen."
    }
}
