package com.inku.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal data class InkuPalette(
    val background: Color,
    val navigation: Color,
    val navigationSoft: Color,
    val mint: Color,
    val text: Color,
    val mutedText: Color,
    val darkText: Color
)

internal object InkuRuntimeSettings {
    private const val PREFS = "inku_settings"

    var themeName by mutableStateOf("Inku dark")
        private set
    var language by mutableStateOf("English")
        private set
    var smoothAnimations by mutableStateOf(true)
        private set
    var compactCards by mutableStateOf(false)
        private set
    var showProgress by mutableStateOf(true)
        private set
    var automaticUpdates by mutableStateOf(true)
        private set
    var use24HourTime by mutableStateOf(true)
        private set
    var wifiOnlyDownloads by mutableStateOf(true)
        private set
    var automaticDownloads by mutableStateOf(false)
        private set
    var incognitoMode by mutableStateOf(false)
        private set

    val palette: InkuPalette
        get() = when (themeName) {
            "Light" -> InkuPalette(
                background = Color(0xFFF2F6F7),
                navigation = Color(0xFFE1EAED),
                navigationSoft = Color(0xFFD5E1E5),
                mint = Color(0xFF55CFAE),
                text = Color(0xFF172833),
                mutedText = Color(0xFF5F737F),
                darkText = Color(0xFF10242D)
            )

            "Midnight" -> InkuPalette(
                background = Color(0xFF0B1118),
                navigation = Color(0xFF101B25),
                navigationSoft = Color(0xFF192734),
                mint = Color(0xFF6FE4C1),
                text = Color(0xFFF7FAFC),
                mutedText = Color(0xFF9EAFBD),
                darkText = Color(0xFF0E1A22)
            )

            "Soft slate" -> InkuPalette(
                background = Color(0xFF435B70),
                navigation = Color(0xFF26394A),
                navigationSoft = Color(0xFF334B5F),
                mint = Color(0xFF9AE7D1),
                text = Color(0xFFF7FAFB),
                mutedText = Color(0xFFC6D0D7),
                darkText = Color(0xFF20313E)
            )

            "High contrast" -> InkuPalette(
                background = Color(0xFF050708),
                navigation = Color(0xFF101314),
                navigationSoft = Color(0xFF1B2022),
                mint = Color(0xFF7FFFD4),
                text = Color.White,
                mutedText = Color(0xFFD9E1E5),
                darkText = Color.Black
            )

            else -> InkuPalette(
                background = Color(0xFF34495E),
                navigation = Color(0xFF1C2B39),
                navigationSoft = Color(0xFF263B4D),
                mint = Color(0xFF7FE0C2),
                text = Color(0xFFF4F7F8),
                mutedText = Color(0xFFB9C5CE),
                darkText = Color(0xFF1C2B39)
            )
        }

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        themeName = prefs.getString("theme", "Inku dark") ?: "Inku dark"
        language = prefs.getString("language", "English") ?: "English"
        smoothAnimations = prefs.getBoolean("smooth_animations", true)
        compactCards = prefs.getBoolean("compact_cards", false)
        showProgress = prefs.getBoolean("show_progress", true)
        automaticUpdates = prefs.getBoolean("automatic_updates", true)
        use24HourTime = prefs.getBoolean("use_24_hour_time", true)
        wifiOnlyDownloads = prefs.getBoolean("wifi_only_downloads", true)
        automaticDownloads = prefs.getBoolean("automatic_downloads", false)
        incognitoMode = context.getSharedPreferences(
            "inku_more_settings",
            Context.MODE_PRIVATE
        ).getBoolean("incognito_mode", false)
    }

    fun setTheme(context: Context, value: String) {
        themeName = value
        saveString(context, "theme", value)
    }

    fun setLanguage(context: Context, value: String) {
        language = value
        saveString(context, "language", value)
    }

    fun setSmoothAnimations(context: Context, value: Boolean) {
        smoothAnimations = value
        saveBoolean(context, "smooth_animations", value)
    }

    fun setCompactCards(context: Context, value: Boolean) {
        compactCards = value
        saveBoolean(context, "compact_cards", value)
    }

    fun setShowProgress(context: Context, value: Boolean) {
        showProgress = value
        saveBoolean(context, "show_progress", value)
    }

    fun setAutomaticUpdates(context: Context, value: Boolean) {
        automaticUpdates = value
        saveBoolean(context, "automatic_updates", value)
    }

    fun setUse24HourTime(context: Context, value: Boolean) {
        use24HourTime = value
        saveBoolean(context, "use_24_hour_time", value)
    }

    fun setWifiOnlyDownloads(context: Context, value: Boolean) {
        wifiOnlyDownloads = value
        saveBoolean(context, "wifi_only_downloads", value)
    }

    fun setAutomaticDownloads(context: Context, value: Boolean) {
        automaticDownloads = value
        saveBoolean(context, "automatic_downloads", value)
    }

    fun setIncognitoMode(context: Context, value: Boolean) {
        incognitoMode = value
        context.getSharedPreferences(
            "inku_more_settings",
            Context.MODE_PRIVATE
        ).edit().putBoolean("incognito_mode", value).apply()
    }

    private fun saveBoolean(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    private fun saveString(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }
}
