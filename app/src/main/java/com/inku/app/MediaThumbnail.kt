package com.inku.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal object MediaThumbnailStore {
    private fun thumbnailDirectory(context: Context): File =
        File(context.cacheDir, "episode_thumbnails").apply { mkdirs() }

    suspend fun loadOrCreate(
        context: Context,
        uriString: String,
        cacheKey: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheFile = File(thumbnailDirectory(context), "$cacheKey.jpg")
        if (cacheFile.exists()) {
            return@withContext runCatching {
                android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
            }.getOrNull()
        }

        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            val deterministicFraction = 0.29 + ((cacheKey.hashCode().ushr(1) % 31) / 100.0)
            val targetUs = (durationMs * deterministicFraction * 1000.0).toLong()
            val bitmap = retriever.getFrameAtTime(
                targetUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime

            if (bitmap != null) {
                runCatching {
                    FileOutputStream(cacheFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
                    }
                }
            }
            bitmap
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun clear(context: Context, cacheKey: String) {
        File(thumbnailDirectory(context), "$cacheKey.jpg").delete()
    }

    fun openVideo(
        context: Context,
        uriString: String,
        title: String = "Inku Player",
        subtitleUri: String = ""
    ): Result<Unit> = runCatching {
        require(uriString.isNotBlank()) { "The video file is missing." }
        val intent = Intent(context, InkuPlayerActivity::class.java).apply {
            putExtra(InkuPlayerActivity.EXTRA_VIDEO_URI, uriString)
            putExtra(InkuPlayerActivity.EXTRA_TITLE, title)
            putExtra(InkuPlayerActivity.EXTRA_SUBTITLE_URI, subtitleUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

@Composable
internal fun EpisodeVideoThumbnail(
    uriString: String?,
    cacheKey: String,
    modifier: Modifier = Modifier,
    fallbackText: String = "VIDEO"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uriString,
        key2 = cacheKey
    ) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            MediaThumbnailStore.loadOrCreate(context, uriString, cacheKey)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Episode video thumbnail",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF263B4D)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackText,
                color = Color(0xFFF4F7F8)
            )
        }
    }
}
