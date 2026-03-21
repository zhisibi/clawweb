package ai.openclaw.clawweb.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.KeyEvent
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.openclaw.clawweb.data.Bookmark
import ai.openclaw.clawweb.data.HistoryEntry

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onCloseApp: () -> Unit
) {
    val ctx = LocalContext.current

    val url by vm.url.collectAsState()
    val title by vm.title.collectAsState()
    val progress by vm.progress.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val canGoBack by vm.canGoBack.collectAsState()
    val canGoForward by vm.canGoForward.collectAsState()

    val showBookmarks by vm.showBookmarks.collectAsState()
    val showHistory by vm.showHistory.collectAsState()

    val bookmarks by vm.bookmarksFlow.collectAsState(initial = emptyList())
    val history by vm.historyFlow.collectAsState(initial = emptyList())

    var webView: WebView? by remember { mutableStateOf(null) }
    var address by remember { mutableStateOf(TextFieldValue(url)) }

    fun syncAddress(newUrl: String) {
        address = TextFieldValue(newUrl)
    }

    BackHandler(enabled = true) {
        val wv = webView
        when {
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> onCloseApp()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1) },
                    actions = {
                        IconButton(onClick = { vm.toggleBookmarks(true) }) { Text("☆") }
                        IconButton(onClick = { vm.toggleHistory(true) }) { Text("🕘") }
                        IconButton(onClick = {
                            webView?.let { wv -> vm.addBookmark(wv.url ?: url, title) }
                        }) { Text("+") }
                    }
                )

                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        placeholder = { Text("输入网址或搜索") },
                        keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                            onDone = { vm.open(address.text) }
                        )
                    )
                    Button(onClick = { vm.open(address.text) }) { Text("Go") }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Text("←") }
                    OutlinedButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Text("→") }
                    OutlinedButton(onClick = { webView?.reload() }) { Text("刷新") }
                    OutlinedButton(onClick = { webView?.stopLoading() }, enabled = isLoading) { Text("停止") }
                }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = {
                WebView(ctx).apply {
                    webView = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = true

                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            vm.setProgress(newProgress)
                            vm.setLoading(newProgress in 1..99)
                            vm.setNavState(canGoBack(), canGoForward())
                        }

                        override fun onReceivedTitle(view: WebView?, t: String?) {
                            vm.setTitle(t)
                        }

                        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {}
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            if (u.startsWith("http://") || u.startsWith("https://")) return false

                            return try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
                                ctx.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                true
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            val current = finishedUrl ?: view?.url ?: return
                            vm.openUrl(current)
                            vm.setNavState(canGoBack(), canGoForward())
                            syncAddress(current)
                            vm.recordHistoryIfPage(current, view?.title)
                        }
                    }

                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
                            goBack()
                            true
                        } else false
                    }

                    loadUrl(url)
                }
            },
            update = { wv ->
                if (wv.url != url) wv.loadUrl(url)
                vm.setNavState(wv.canGoBack(), wv.canGoForward())
            }
        )
    }

    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { vm.toggleBookmarks(false) }) {
            SheetBookmarks(
                bookmarks = bookmarks,
                onOpen = {
                    vm.toggleBookmarks(false)
                    vm.openUrl(it)
                },
                onDelete = { vm.removeBookmark(it) }
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { vm.toggleHistory(false) }) {
            SheetHistory(
                history = history,
                onOpen = {
                    vm.toggleHistory(false)
                    vm.openUrl(it)
                }
            )
        }
    }
}

@Composable
private fun SheetBookmarks(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("书签", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(bookmarks) { b ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title, maxLines = 1)
                        Text(b.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Row {
                        TextButton(onClick = { onOpen(b.url) }) { Text("打开") }
                        TextButton(onClick = { onDelete(b.url) }) { Text("删除") }
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun SheetHistory(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("历史记录", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(history) { h ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(h.title, maxLines = 1)
                        Text(h.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    TextButton(onClick = { onOpen(h.url) }) { Text("打开") }
                }
                Divider()
            }
        }
    }
}
