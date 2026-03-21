package ai.openclaw.clawweb

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BrowserApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
fun BrowserApp() {
    var addressBar by remember { mutableStateOf("https://www.google.com") }
    var pageTitle by remember { mutableStateOf("ClawWeb") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val webView = remember {
        WebView(androidx.compose.ui.platform.LocalContext.current).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    addressBar = url.orEmpty()
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    addressBar = url.orEmpty()
                    pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "ClawWeb"
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    pageTitle = title?.takeIf { it.isNotBlank() } ?: "ClawWeb"
                }
            }
            loadUrl(addressBar)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(" ") -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
            else -> "https://$trimmed"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = addressBar,
                onValueChange = { addressBar = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = androidx.compose.ui.unit.dp(12), vertical = androidx.compose.ui.unit.dp(8)),
                singleLine = true,
                label = { Text("输入网址或搜索内容") }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = androidx.compose.ui.unit.dp(12)),
                horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8))
            ) {
                Button(onClick = {
                    if (webView.canGoBack()) webView.goBack()
                }, enabled = canGoBack) {
                    Text("后退")
                }
                Button(onClick = {
                    if (webView.canGoForward()) webView.goForward()
                }, enabled = canGoForward) {
                    Text("前进")
                }
                Button(onClick = { webView.reload() }) {
                    Text("刷新")
                }
                Button(onClick = {
                    scope.launch {
                        webView.loadUrl(normalizeUrl(addressBar))
                    }
                }) {
                    Text("打开")
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(androidx.compose.ui.unit.dp(12)),
                    horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8))
                ) {
                    CircularProgressIndicator(progress = { progress })
                    Text("加载中… ${(progress * 100).toInt()}%")
                }
            }

            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = androidx.compose.ui.unit.dp(8))
            )
        }
    }
}
