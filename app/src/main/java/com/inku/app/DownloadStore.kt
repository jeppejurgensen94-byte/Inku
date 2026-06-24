package com.inku.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal enum class InkuDownloadState {
    Queued,
    Running,
    Paused,
    Completed,
    Failed,
    MetadataOnly
}

internal data class InkuDownloadRecord(
    val id: String,
    val systemDownloadId: Long,
    val title: String,
    val kind: String,
    val selection: String,
    val sourceUrl: String,
    val fileName: String,
    val createdAt: Long,
    val state: InkuDownloadState,
    val reason: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val destinationUri: String = ""
) {
    val progressPercent: Int
        get() = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else if (state == InkuDownloadState.Completed) {
            100
        } else {
            0
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("systemDownloadId", systemDownloadId)
        put("title", title)
        put("kind", kind)
        put("selection", selection)
        put("sourceUrl", sourceUrl)
        put("fileName", fileName)
        put("createdAt", createdAt)
        put("state", state.name)
        put("reason", reason)
        put("downloadedBytes", downloadedBytes)
        put("totalBytes", totalBytes)
        put("destinationUri", destinationUri)
    }

    companion object {
        fun fromJson(json: JSONObject): InkuDownloadRecord = InkuDownloadRecord(
            id = json.optString("id"),
            systemDownloadId = json.optLong("systemDownloadId", -1L),
            title = json.optString("title", "Untitled"),
            kind = json.optString("kind", "media"),
            selection = json.optString("selection"),
            sourceUrl = json.optString("sourceUrl"),
            fileName = json.optString("fileName"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            state = runCatching {
                InkuDownloadState.valueOf(json.optString("state", "Queued"))
            }.getOrDefault(InkuDownloadState.Queued),
            reason = json.optString("reason"),
            downloadedBytes = json.optLong("downloadedBytes", 0L),
            totalBytes = json.optLong("totalBytes", 0L),
            destinationUri = json.optString("destinationUri")
        )
    }
}

internal object DownloadStore {
    private const val PREFS = "inku_download_store"
    private const val RECORDS_KEY = "records"

    fun list(context: Context): List<InkuDownloadRecord> = read(context)
        .sortedByDescending { it.createdAt }

    fun addMetadataQueue(
        context: Context,
        title: String,
        kind: String,
        selection: String
    ): InkuDownloadRecord {
        val record = InkuDownloadRecord(
            id = "metadata-${System.currentTimeMillis()}",
            systemDownloadId = -1L,
            title = title,
            kind = kind,
            selection = selection,
            sourceUrl = "",
            fileName = "",
            createdAt = System.currentTimeMillis(),
            state = InkuDownloadState.MetadataOnly,
            reason = "This source has not supplied a permitted direct file URL."
        )
        save(context, read(context) + record)
        return record
    }

    fun startDirectDownload(
        context: Context,
        url: String,
        title: String,
        kind: String,
        selection: String,
        preferredFileName: String = ""
    ): Result<InkuDownloadRecord> = runCatching {
        require(InkuFolderStore.isConfigured(context)) {
            "Choose the main Inku folder before starting a download."
        }
        val uri = Uri.parse(url.trim())
        require(uri.scheme in setOf("http", "https", "content", "file")) {
            "Only HTTP, HTTPS, content or file links can be copied."
        }
        if (uri.scheme in setOf("http", "https")) {
            require(!uri.host.isNullOrBlank()) {
                "The download link is missing a host."
            }
        }

        val cleanTitle = sanitizeFileName(title)
        val cleanFileName = if (preferredFileName.isNotBlank()) {
            ensureExtension(sanitizeFileName(preferredFileName), uri)
        } else {
            val selectionPart = sanitizeFileName(selection).ifBlank { "media" }
            ensureExtension("$cleanTitle - $selectionPart", uri)
        }
        val record = InkuDownloadRecord(
            id = "download-${System.currentTimeMillis()}-${url.hashCode().ushr(1)}",
            systemDownloadId = -1L,
            title = title,
            kind = kind,
            selection = selection,
            sourceUrl = url.trim(),
            fileName = cleanFileName,
            createdAt = System.currentTimeMillis(),
            state = InkuDownloadState.Queued,
            reason = "Waiting to start"
        )
        save(context, read(context) + record)

        val intent = Intent(context, InkuDownloadService::class.java).apply {
            action = InkuDownloadService.ACTION_START
            putExtra(InkuDownloadService.EXTRA_RECORD_ID, record.id)
        }
        ContextCompat.startForegroundService(context, intent)
        record
    }

    fun remove(context: Context, record: InkuDownloadRecord) {
        if (record.state in setOf(InkuDownloadState.Queued, InkuDownloadState.Running)) {
            val intent = Intent(context, InkuDownloadService::class.java).apply {
                action = InkuDownloadService.ACTION_CANCEL
                putExtra(InkuDownloadService.EXTRA_RECORD_ID, record.id)
            }
            context.startService(intent)
        }
        save(context, read(context).filterNot { it.id == record.id })
    }

    fun retry(context: Context, record: InkuDownloadRecord): Result<InkuDownloadRecord> {
        require(record.sourceUrl.isNotBlank()) {
            "This queue item has no direct URL yet."
        }
        remove(context, record)
        return startDirectDownload(
            context = context,
            url = record.sourceUrl,
            title = record.title,
            kind = record.kind,
            selection = record.selection,
            preferredFileName = record.fileName
        )
    }

    fun clearFinished(context: Context) {
        save(
            context,
            read(context).filterNot {
                it.state in setOf(
                    InkuDownloadState.Completed,
                    InkuDownloadState.Failed,
                    InkuDownloadState.MetadataOnly
                )
            }
        )
    }

    fun find(context: Context, id: String): InkuDownloadRecord? =
        read(context).firstOrNull { it.id == id }

    fun update(
        context: Context,
        id: String,
        transform: (InkuDownloadRecord) -> InkuDownloadRecord
    ): InkuDownloadRecord? {
        var updatedRecord: InkuDownloadRecord? = null
        val updated = read(context).map { record ->
            if (record.id == id) {
                transform(record).also { updatedRecord = it }
            } else {
                record
            }
        }
        save(context, updated)
        return updatedRecord
    }

    private fun read(context: Context): List<InkuDownloadRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RECORDS_KEY, "[]")
            ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(InkuDownloadRecord.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, records: List<InkuDownloadRecord>) {
        val array = JSONArray()
        records.distinctBy { it.id }.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RECORDS_KEY, array.toString())
            .apply()
    }

    private fun ensureExtension(fileName: String, uri: Uri): String {
        val existing = fileName.substringAfterLast('.', "")
        if (existing.length in 2..5 && existing.all { it.isLetterOrDigit() }) {
            return fileName
        }
        return fileName + guessExtension(uri.lastPathSegment)
    }

    private fun guessExtension(lastPath: String?): String {
        val name = lastPath.orEmpty().substringBefore('?')
        val extension = name.substringAfterLast('.', "")
        return if (extension.length in 2..5 && extension.all { it.isLetterOrDigit() }) {
            ".${extension.lowercase(Locale.ROOT)}"
        } else {
            ".bin"
        }
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "Inku download" }
}
