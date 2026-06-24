@file:Suppress("ALL")

package com.inku.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import com.inku.app.ui.theme.InkuTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@androidx.annotation.OptIn(UnstableApi::class)
class InkuPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Inku Player" }
        val subtitleUri = intent.getStringExtra(EXTRA_SUBTITLE_URI).orEmpty()
        val headers = parsePlayerHeaders(intent.getStringExtra(EXTRA_HEADERS_JSON).orEmpty())
        Log.i(
            "InkuPlayerHeaders",
            "Opening player uriHost=${runCatching { Uri.parse(videoUri).host }.getOrNull().orEmpty()} " +
                "headerCount=${headers.size} headerKeys=${headers.keys.sorted().joinToString(",")}"
        )

        setContent {
            InkuTheme(dynamicColor = false) {
                InkuPlayerScreen(
                    title = title,
                    initialVideoUri = videoUri,
                    initialSubtitleUri = subtitleUri,
                    requestHeaders = headers,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "inku.player.video_uri"
        const val EXTRA_SUBTITLE_URI = "inku.player.subtitle_uri"
        const val EXTRA_TITLE = "inku.player.title"
        const val EXTRA_HEADERS_JSON = "inku.player.headers_json"

    }
}

internal fun parsePlayerHeaders(raw: String): Map<String, String> = runCatching {
    if (raw.isBlank()) return@runCatching emptyMap()
    val json = InkuPlayerHeaderJson.parseToJsonElement(raw) as? JsonObject
        ?: return@runCatching emptyMap()
    json.mapNotNull { (key, value) ->
        val headerValue = value.jsonPrimitive.contentOrNull?.trim().orEmpty()
        if (key.isNotBlank() && headerValue.isNotBlank()) key to headerValue else null
    }.toMap()
}.getOrDefault(emptyMap())

private val InkuPlayerHeaderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Composable
private fun InkuPlayerScreen(
    title: String,
    initialVideoUri: String,
    initialSubtitleUri: String,
    requestHeaders: Map<String, String>,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var subtitleUri by remember { mutableStateOf(initialSubtitleUri) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            subtitleUri = uri.toString()
        }
    }

    BackHandler(onBack = onClose)

    DisposableEffect(initialVideoUri, subtitleUri, requestHeaders) {
        Log.i(
            "InkuPlayerHeaders",
            "Configuring Media3 DefaultHttpDataSource headerCount=${requestHeaders.size} " +
                "headerKeys=${requestHeaders.keys.sorted().joinToString(",")}"
        )
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(requestHeaders)
        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .also { built ->
            val mediaBuilder = MediaItem.Builder().setUri(Uri.parse(initialVideoUri))
            if (subtitleUri.isNotBlank()) {
                val subtitleMime = when {
                    subtitleUri.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUri))
                    .setMimeType(subtitleMime)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaBuilder.setSubtitleConfigurations(listOf(subtitle))
            }
            built.setMediaItem(mediaBuilder.build())
            built.prepare()
            built.playWhenReady = true
        }
        player = exoPlayer
        onDispose {
            exoPlayer.release()
            player = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101820))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayerAction("<", onClose)
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            PlayerAction(if (subtitleUri.isBlank()) "Subtitles" else "Subtitles on") {
                subtitlePicker.launch(
                    arrayOf(
                        "application/x-subrip",
                        "text/vtt",
                        "text/plain",
                        "*/*"
                    )
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { view -> view.player = player }
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PlayerAction(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(Color(0xFF263B4D), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color(0xFF7FE0C2), fontSize = 12.sp)
    }
}
