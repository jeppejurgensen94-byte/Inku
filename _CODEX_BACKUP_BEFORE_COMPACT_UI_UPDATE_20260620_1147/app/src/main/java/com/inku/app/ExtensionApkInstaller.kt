package com.inku.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

internal data class InkuExtensionDownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    val percent: Int
        get() = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
}

internal enum class InkuInstallLaunchState {
    Started,
    PermissionRequired,
    Failed
}

internal data class InkuInstallLaunchResult(
    val state: InkuInstallLaunchState,
    val message: String
)

internal object ExtensionApkInstaller {
    private const val USER_AGENT = "Inku/0.13 Android extension installer"
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun apkCacheDirectory(context: Context): File =
        File(context.cacheDir, "extension_apks").apply { mkdirs() }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else {
            context.startActivity(
                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun downloadAndOpenInstaller(
        context: Context,
        extension: InkuRepositoryExtension,
        onProgress: (InkuExtensionDownloadProgress) -> Unit
    ): InkuInstallLaunchResult {
        if (!canRequestPackageInstalls(context)) {
            withContext(Dispatchers.Main) { openUnknownSourcesSettings(context) }
            return InkuInstallLaunchResult(
                InkuInstallLaunchState.PermissionRequired,
                "Allow Inku to install unknown apps, then tap Install again."
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val apk = downloadApk(context, extension, onProgress)
                validateApk(context, apk, extension)
                withContext(Dispatchers.Main) {
                    openPackageInstaller(context, apk)
                }
                InkuInstallLaunchResult(
                    InkuInstallLaunchState.Started,
                    "Android installer opened. Approve the installation to continue."
                )
            }.getOrElse { error ->
                cleanDownloads(context, extension.packageName)
                InkuInstallLaunchResult(
                    InkuInstallLaunchState.Failed,
                    error.message?.take(220) ?: "The APK could not be installed."
                )
            }
        }
    }

    fun openUninstaller(context: Context, packageName: String) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private suspend fun downloadApk(
        context: Context,
        extension: InkuRepositoryExtension,
        onProgress: (InkuExtensionDownloadProgress) -> Unit
    ): File {
        require(extension.apkUrl.startsWith("https://") || extension.apkUrl.startsWith("http://")) {
            "The repository did not provide a valid APK URL."
        }
        cleanDownloads(context, extension.packageName)
        val safeName = extension.packageName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "extension" }
        val tempFile = File(apkCacheDirectory(context), "$safeName.part")
        val targetFile = File(apkCacheDirectory(context), "$safeName.apk")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(extension.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 45_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "$APK_MIME,application/octet-stream,*/*")
            }
            val status = connection.responseCode
            require(status in 200..299) { "APK download returned HTTP $status." }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.buffered().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var downloaded = 0L
                    var lastProgressAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 250L) {
                            lastProgressAt = now
                            withContext(Dispatchers.Main) {
                                onProgress(InkuExtensionDownloadProgress(downloaded, total))
                            }
                        }
                    }
                    output.flush()
                    withContext(Dispatchers.Main) {
                        onProgress(InkuExtensionDownloadProgress(downloaded, total))
                    }
                }
            }
            if (targetFile.exists()) targetFile.delete()
            require(tempFile.renameTo(targetFile)) {
                "The downloaded APK could not be finalized."
            }
            return targetFile
        } catch (throwable: Throwable) {
            tempFile.delete()
            targetFile.delete()
            throw throwable
        } finally {
            connection?.disconnect()
        }
    }

    private fun validateApk(
        context: Context,
        apk: File,
        extension: InkuRepositoryExtension
    ) {
        require(apk.exists() && apk.length() > 0L) { "The downloaded APK is empty." }
        apk.inputStream().use { input ->
            val header = ByteArray(4)
            require(input.read(header) == 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
                "The downloaded file is not a valid APK archive."
            }
        }
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, archiveFlags)
        require(archiveInfo != null) { "Android could not read the APK manifest." }
        val actualPackage = archiveInfo.packageName.orEmpty()
        require(actualPackage == extension.packageName) {
            "APK package mismatch: expected ${extension.packageName}, got ${actualPackage.ifBlank { "unknown" }}."
        }
        val installedInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    extension.packageName,
                    PackageManager.PackageInfoFlags.of(archiveFlags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(extension.packageName, archiveFlags)
            }
        }.getOrNull()
        if (installedInfo != null) {
            val installedSignatures = installedInfo.signatureHashes()
            val archiveSignatures = archiveInfo.signatureHashes()
            require(installedSignatures.isNotEmpty() && archiveSignatures.isNotEmpty()) {
                "Signature conflict: Android could not read extension signatures."
            }
            require(installedSignatures.any { it in archiveSignatures }) {
                "Signature conflict: installed extension and downloaded update are signed by different certificates."
            }
        }
    }

    private fun openPackageInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun cleanDownloads(context: Context, packageName: String) {
        val safePrefix = packageName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .lowercase(Locale.ROOT)
        apkCacheDirectory(context)
            .listFiles()
            .orEmpty()
            .filter {
                it.name.lowercase(Locale.ROOT).startsWith(safePrefix) &&
                    it.extension in setOf("part", "apk")
            }
            .forEach { it.delete() }
    }
}

private fun android.content.pm.PackageInfo.signatureHashes(): List<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signing = signingInfo ?: return emptyList()
        if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
    } else {
        @Suppress("DEPRECATION")
        signatures
    } ?: return emptyList()
    return signatures.map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(":") { "%02X".format(Locale.ROOT, it) }
    }
}
