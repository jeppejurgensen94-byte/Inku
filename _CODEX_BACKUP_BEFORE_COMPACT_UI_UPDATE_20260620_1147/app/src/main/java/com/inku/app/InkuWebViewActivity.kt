package com.inku.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.inku.app.ui.theme.InkuTheme

class InkuWebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "WebView" }
        setContent {
            InkuTheme(dynamicColor = false) {
                InkuWebViewScreen(
                    initialUrl = initialUrl,
                    title = title,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "inku.webview.url"
        const val EXTRA_TITLE = "inku.webview.title"
    }
}

@Composable
private fun InkuWebViewScreen(
    initialUrl: String,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(title) }
    var javascriptEnabled by remember { mutableStateOf(false) }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2B39))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            WebAction("‹") {
                if (webView?.canGoBack() == true) webView?.goBack() else onClose()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pageTitle,
                    color = Color(0xFFF4F7F8),
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = currentUrl,
                    color = Color(0xFFB9C5CE),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            WebAction("↻") { webView?.reload() }
            WebAction(if (javascriptEnabled) "JS✓" else "JS") {
                javascriptEnabled = !javascriptEnabled
                webView?.settings?.javaScriptEnabled = javascriptEnabled
                webView?.reload()
            }
            WebAction("↗") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (initialUrl.startsWith("http://") || initialUrl.startsWith("https://")) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = javascriptEnabled
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mediaPlaybackRequiresUserGesture = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentUrl = url.orEmpty()
                                    pageTitle = view?.title.orEmpty().ifBlank { title }
                                }
                            }
                            loadUrl(initialUrl)
                            webView = this
                        }
                    },
                    update = { view ->
                        view.settings.javaScriptEnabled = javascriptEnabled
                    }
                )
            } else {
                Text(
                    text = "This source does not have a valid web address.",
                    color = Color(0xFFF4F7F8),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun WebAction(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(Color(0xFF263B4D), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF7FE0C2),
            fontSize = 13.sp
        )
    }
}
