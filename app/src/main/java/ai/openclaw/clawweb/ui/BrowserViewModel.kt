package ai.openclaw.clawweb.ui

import android.app.Application
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.openclaw.clawweb.data.AppDatabase
import ai.openclaw.clawweb.data.Bookmark
import ai.openclaw.clawweb.data.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val db by lazy { AppDatabase.get(app) }

    private val _url = MutableStateFlow("https://www.google.com")
    val url: StateFlow<String> = _url

    private val _title = MutableStateFlow("clawweb")
    val title: StateFlow<String> = _title

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward

    private val _showBookmarks = MutableStateFlow(false)
    val showBookmarks: StateFlow<Boolean> = _showBookmarks

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory

    val bookmarksFlow = db.bookmarkDao().observeAll()
    val historyFlow = db.historyDao().observeRecent(limit = 200)

    fun setTitle(t: String?) { _title.value = t?.takeIf { it.isNotBlank() } ?: "clawweb" }
    fun setProgress(p: Int) { _progress.value = p.coerceIn(0, 100) }
    fun setLoading(loading: Boolean) { _isLoading.value = loading }
    fun setNavState(back: Boolean, forward: Boolean) {
        _canGoBack.value = back
        _canGoForward.value = forward
    }

    fun open(input: String) {
        _url.value = normalizeToUrlOrSearch(input)
    }

    fun openUrl(url: String) {
        _url.value = url
    }

    fun toggleBookmarks(show: Boolean) { _showBookmarks.value = show }
    fun toggleHistory(show: Boolean) { _showHistory.value = show }

    fun addBookmark(currentUrl: String, currentTitle: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().upsert(
                Bookmark(
                    url = currentUrl,
                    title = (currentTitle ?: "").ifBlank { currentUrl }
                )
            )
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bookmarkDao().deleteByUrl(url)
        }
    }

    fun recordHistoryIfPage(url: String, title: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().insert(
                HistoryEntry(
                    url = url,
                    title = (title ?: "").ifBlank { url },
                    visitedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun normalizeToUrlOrSearch(inputRaw: String): String {
        val input = inputRaw.trim()
        if (input.isBlank()) return "https://www.google.com"

        if (URLUtil.isValidUrl(input) && (input.startsWith("http://") || input.startsWith("https://"))) {
            return input
        }

        if (input.contains(".") && !input.contains(" ")) {
            return "https://$input"
        }

        val q = URLEncoder.encode(input, Charsets.UTF_8.name())
        return "https://www.google.com/search?q=$q"
    }
}
