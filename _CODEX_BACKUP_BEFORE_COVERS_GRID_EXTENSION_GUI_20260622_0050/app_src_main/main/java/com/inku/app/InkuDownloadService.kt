package com.inku.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class InkuDownloadService : Service() {
    private val executor = Executors.newCachedThreadPool()
    private val cancellations = ConcurrentHashMap<String, AtomicBoolean>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recordId = intent?.getStringExtra(EXTRA_RECORD_ID).orEmpty()
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancellations[recordId]?.set(true)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (recordId.isBlank()) return START_NOT_STICKY
                val cancellation = AtomicBoolean(false)
                cancellations[recordId] = cancellation
                val notificationId = notificationId(recordId)
                startInForeground(notificationId, "Preparing download", 0, true)
                executor.execute {
                    runDownload(recordId, notificationId, cancellation)
                    cancellations.remove(recordId)
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancellations.values.forEach { it.set(true) }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun runDownload(
        recordId: String,
        notificationId: Int,
        cancelled: AtomicBoolean
    ) {
        val record = DownloadStore.find(this, recordId) ?: return
        val sourceUri = Uri.parse(record.sourceUrl)
        val isNetworkSource = sourceUri.scheme in setOf("http", "https")
        if (isNetworkSource && InkuRuntimeSettings.wifiOnlyDownloads && !isWifiConnected()) {
            fail(recordId, notificationId, "Waiting failed: Wi-Fi only is enabled.")
            return
        }

        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        try {
            DownloadStore.update(this, recordId) {
                it.copy(state = InkuDownloadState.Running, reason = "Preparing ${record.selection}…")
            }
            updateNotification(notificationId, "Preparing ${record.selection}", 0, true)

            val sourceInfo = when (sourceUri.scheme) {
                "http", "https" -> {
                    connection = (URL(record.sourceUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Inku/0.13 Android downloader")
                        connect()
                    }
                    val status = requireNotNull(connection).responseCode
                    require(status in 200..299) { "Server returned HTTP $status." }
                    val total = requireNotNull(connection).contentLengthLong.coerceAtLeast(0L)
                    val mime = requireNotNull(connection).contentType?.substringBefore(';')
                        ?.takeIf { it.contains('/') }
                        ?: guessMime(record.fileName)
                    input = requireNotNull(connection).inputStream.buffered()
                    SourceInfo(total, mime)
                }

                "content" -> {
                    input = contentResolver.openInputStream(sourceUri)?.buffered()
                        ?: error("The local source file could not be opened.")
                    SourceInfo(
                        totalBytes = queryContentSize(sourceUri),
                        mimeType = contentResolver.getType(sourceUri)
                            ?: guessMime(record.fileName)
                    )
                }

                "file" -> {
                    val sourceFile = File(sourceUri.path ?: error("The local file path is missing."))
                    require(sourceFile.exists()) { "The local source file no longer exists." }
                    input = FileInputStream(sourceFile).buffered()
                    SourceInfo(
                        totalBytes = sourceFile.length(),
                        mimeType = guessMime(record.fileName)
                    )
                }

                else -> error("Unsupported source type: ${sourceUri.scheme}")
            }

            val destination = InkuFolderStore.createDownloadFile(
                context = this,
                kind = record.kind,
                title = record.title,
                fileName = record.fileName,
                mimeType = sourceInfo.mimeType
            ).getOrThrow()

            contentResolver.openOutputStream(destination, "w")?.use { output ->
                requireNotNull(input).use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var downloaded = 0L
                    var lastUiUpdate = 0L
                    while (true) {
                        if (cancelled.get()) error("Download cancelled.")
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate >= 300L) {
                            lastUiUpdate = now
                            val progress = if (sourceInfo.totalBytes > 0L) {
                                ((downloaded * 100L) / sourceInfo.totalBytes)
                                    .toInt()
                                    .coerceIn(0, 99)
                            } else 0
                            DownloadStore.update(this, recordId) {
                                it.copy(
                                    state = InkuDownloadState.Running,
                                    reason = "Downloading ${record.selection.ifBlank { "file" }}",
                                    downloadedBytes = downloaded,
                                    totalBytes = sourceInfo.totalBytes,
                                    destinationUri = destination.toString()
                                )
                            }
                            updateNotification(
                                notificationId,
                                "${record.title} • ${record.selection}",
                                progress,
                                sourceInfo.totalBytes <= 0L
                            )
                        }
                    }
                    output.flush()
                    DownloadStore.update(this, recordId) {
                        it.copy(
                            state = InkuDownloadState.Completed,
                            reason = "Saved in Inku/Downloads",
                            downloadedBytes = downloaded,
                            totalBytes = if (sourceInfo.totalBytes > 0L) {
                                sourceInfo.totalBytes
                            } else {
                                downloaded
                            },
                            destinationUri = destination.toString()
                        )
                    }
                }
            } ?: error("The selected folder could not be written to.")

            updateNotification(notificationId, "Download complete: ${record.selection}", 100, false)
        } catch (throwable: Throwable) {
            fail(
                recordId,
                notificationId,
                throwable.message?.take(180) ?: "Download failed."
            )
        } finally {
            runCatching { input?.close() }
            connection?.disconnect()
        }
    }

    private data class SourceInfo(
        val totalBytes: Long,
        val mimeType: String
    )

    private fun queryContentSize(uri: Uri): Long = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun fail(recordId: String, notificationId: Int, message: String) {
        DownloadStore.update(this, recordId) {
            it.copy(state = InkuDownloadState.Failed, reason = message)
        }
        updateNotification(notificationId, message, 0, false)
    }

    private fun isWifiConnected(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun startInForeground(
        notificationId: Int,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) {
        val notification = buildNotification(text, progress, indeterminate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(
        notificationId: Int,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildNotification(text, progress, indeterminate))
    }

    private fun buildNotification(
        text: String,
        progress: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Inku downloader")
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(progress in 0..99 && (indeterminate || progress < 100))
        .setProgress(100, progress.coerceIn(0, 100), indeterminate)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Inku downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active anime and manga downloads."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun notificationId(recordId: String): Int =
        3_000 + (recordId.hashCode().ushr(1) % 10_000)

    private fun guessMime(fileName: String): String = when (
        fileName.substringAfterLast('.', "").lowercase()
    ) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "pdf" -> "application/pdf"
        "zip", "cbz" -> "application/zip"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    companion object {
        const val ACTION_START = "com.inku.app.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.inku.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_RECORD_ID = "record_id"
        private const val CHANNEL_ID = "inku_downloads"
    }
}
