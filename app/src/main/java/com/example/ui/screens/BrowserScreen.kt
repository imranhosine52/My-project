@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.format.Formatter
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.data.local.AppDatabase
import com.example.data.local.BrowserBookmarkEntity
import com.example.data.local.BrowserHistoryEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

private const val HOME_PAGE_MARKER = "app://home"
private const val DEFAULT_SEARCH_ENGINE = "https://www.google.com/search?q="
private const val MAX_TABS = 8

private class BrowserTabState(initialUrl: String = HOME_PAGE_MARKER) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New Tab")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var isLoading by mutableStateOf(false)
    var isDesktopMode by mutableStateOf(false)
}

private data class DownloadInfo(
    val id: Long,
    val title: String,
    val status: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val localUri: String?
)

private data class QuickShortcut(val label: String, val url: String)

private val quickShortcuts = listOf(
    QuickShortcut("Google", "https://www.google.com"),
    QuickShortcut("YouTube", "https://www.youtube.com"),
    QuickShortcut("Facebook", "https://www.facebook.com"),
    QuickShortcut("Wikipedia", "https://www.wikipedia.org"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val historyDao = remember { AppDatabase.getInstance(context).browserHistoryDao() }
    val bookmarkDao = remember { AppDatabase.getInstance(context).browserBookmarkDao() }
    val historyList by historyDao.getHistory().collectAsState(initial = emptyList())
    val bookmarkList by bookmarkDao.getBookmarks().collectAsState(initial = emptyList())

    val tabs = remember { mutableStateListOf(BrowserTabState()) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab by remember { derivedStateOf { tabs.first { it.id == activeTabId } } }
    val webViewCache = remember { mutableMapOf<String, WebView>() }

    var addressBarText by remember(activeTabId) { mutableStateOf(activeTab.url.takeIf { it != HOME_PAGE_MARKER } ?: "") }
    var isEditingAddress by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var isCurrentBookmarked by remember { mutableStateOf(false) }

    var isFindInPageOpen by remember { mutableStateOf(false) }
    var findQueryText by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var sourceCodeContent by remember { mutableStateOf("") }
    var showMediaSheet by remember { mutableStateOf(false) }
    var sniffedMediaList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showResourcesSheet by remember { mutableStateOf(false) }
    var sniffedResourcesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSiteSettingsDialog by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }

    var isTtsSpeaking by remember { mutableStateOf(false) }
    val ttsInstance = remember { mutableStateOf<TextToSpeech?>(null) }

    // Voice Search Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                addressBarText = spokenText
                val target = if (spokenText.contains(".") && !spokenText.contains(" ")) {
                    if (spokenText.startsWith("http://") || spokenText.startsWith("https://")) spokenText else "https://$spokenText"
                } else {
                    DEFAULT_SEARCH_ENGINE + Uri.encode(spokenText)
                }
                isEditingAddress = false
                focusManager.clearFocus()
                val cached = webViewCache[activeTab.id]
                if (cached != null) cached.loadUrl(target) else activeTab.url = target
            }
        }
    }

    fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
            }
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsInstance.value = tts
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(activeTabId, activeTab.url, bookmarkList) {
        isCurrentBookmarked = bookmarkList.any { it.url == activeTab.url }
    }

    fun openNewTab(url: String = HOME_PAGE_MARKER, switchToIt: Boolean = true) {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(context, "Maximum $MAX_TABS tabs allowed", Toast.LENGTH_SHORT).show()
            return
        }
        val tab = BrowserTabState(url)
        tabs.add(tab)
        if (switchToIt) activeTabId = tab.id
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        webViewCache.remove(tabId)?.destroy()
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            onBackClick()
            return
        }
        if (activeTabId == tabId) {
            activeTabId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        }
    }

    fun normalizeInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return HOME_PAGE_MARKER
        val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ") &&
            (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                Regex("^[\\w-]+(\\.[\\w-]+)+.*$").matches(trimmed))
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            looksLikeUrl -> "https://$trimmed"
            else -> DEFAULT_SEARCH_ENGINE + Uri.encode(trimmed)
        }
    }

    fun loadUrlInActiveTab(rawInput: String) {
        val target = normalizeInputToUrl(rawInput)
        isEditingAddress = false
        focusManager.clearFocus()
        if (target == HOME_PAGE_MARKER) {
            activeTab.url = HOME_PAGE_MARKER
            return
        }
        val cachedWebView = webViewCache[activeTab.id]
        if (cachedWebView != null) {
            cachedWebView.loadUrl(target)
        } else {
            activeTab.url = target
        }
    }

    fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url == HOME_PAGE_MARKER || url.startsWith("about:")) return
        scope.launch {
            historyDao.insert(BrowserHistoryEntity(url = url, title = title.ifBlank { url }))
        }
    }

    fun saveCurrentWebPage() {
        val webView = webViewCache[activeTab.id] ?: return
        val cleanTitle = activeTab.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(30)
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "${cleanTitle}_${System.currentTimeMillis()}.mht")
        webView.saveWebArchive(file.absolutePath, false) { path ->
            if (path != null) {
                Toast.makeText(context, "Saved offline to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not save web page", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToDesktopShortcut() {
        if (activeTab.url == HOME_PAGE_MARKER) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeTab.url))
                val pinShortcutInfo = ShortcutInfo.Builder(context, UUID.randomUUID().toString())
                    .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_compass))
                    .setShortLabel(activeTab.title.take(15).ifBlank { "Website" })
                    .setLongLabel(activeTab.title.ifBlank { activeTab.url })
                    .setIntent(intent)
                    .build()
                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo)
                val successCallback = PendingIntent.getBroadcast(
                    context, 0, pinnedShortcutCallbackIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.intentSender)
                Toast.makeText(context, "Shortcut added to home screen", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(context, "Home screen shortcut not supported on this device", Toast.LENGTH_SHORT).show()
    }

    fun translateCurrentPage() {
        if (activeTab.url == HOME_PAGE_MARKER) return
        val translateUrl = "https://translate.google.com/translate?sl=auto&tl=bn&u=${Uri.encode(activeTab.url)}"
        loadUrlInActiveTab(translateUrl)
    }

    fun sniffMediaResources() {
        val webView = webViewCache[activeTab.id] ?: return
        val js = """
            (function() {
                var urls = [];
                document.querySelectorAll('video, audio, source').forEach(function(el) {
                    if (el.src && el.src.startsWith('http')) urls.push(el.src);
                    if (el.currentSrc && el.currentSrc.startsWith('http')) urls.push(el.currentSrc);
                });
                return JSON.stringify(Array.from(new Set(urls)));
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { res ->
            val list = runCatching {
                val raw = res?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                Regex("https?://[^\\s\",]+").findAll(raw).map { it.value }.distinct().toList()
            }.getOrDefault(emptyList())
            sniffedMediaList = list
            showMediaSheet = true
        }
    }

    fun sniffAllResources() {
        val webView = webViewCache[activeTab.id] ?: return
        val js = """
            (function() {
                var urls = [];
                document.querySelectorAll('img[src], link[href], script[src]').forEach(function(el) {
                    var src = el.src || el.href;
                    if (src && src.startsWith('http')) urls.push(src);
                });
                return JSON.stringify(Array.from(new Set(urls)));
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { res ->
            val list = runCatching {
                val raw = res?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                Regex("https?://[^\\s\",]+").findAll(raw).map { it.value }.distinct().toList()
            }.getOrDefault(emptyList())
            sniffedResourcesList = list
            showResourcesSheet = true
        }
    }

    fun viewSourceCode() {
        val webView = webViewCache[activeTab.id] ?: return
        webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
            val unescaped = html?.removeSurrounding("\"")
                ?.replace("\\u003C", "<")
                ?.replace("\\u003E", ">")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\") ?: "No HTML content found"
            sourceCodeContent = unescaped
            showSourceDialog = true
        }
    }

    fun injectDevTools() {
        val webView = webViewCache[activeTab.id] ?: return
        val js = """
            (function () {
                if (window.eruda) {
                    if (window.eruda._isInit) { window.eruda.destroy(); }
                    else { window.eruda.init(); }
                } else {
                    var script = document.createElement('script');
                    script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                    document.body.appendChild(script);
                    script.onload = function () { eruda.init(); };
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        Toast.makeText(context, "Developer Tools (Eruda) Injected", Toast.LENGTH_SHORT).show()
    }

    fun toggleTextToSpeech() {
        val tts = ttsInstance.value
        if (tts == null) {
            Toast.makeText(context, "TTS engine not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (isTtsSpeaking) {
            tts.stop()
            isTtsSpeaking = false
            Toast.makeText(context, "TTS Stopped", Toast.LENGTH_SHORT).show()
            return
        }
        val webView = webViewCache[activeTab.id] ?: return
        webView.evaluateJavascript("(function() { return document.body.innerText; })();") { text ->
            val unescaped = text?.removeSurrounding("\"")
                ?.replace("\\n", " ")
                ?.replace("\\\"", "\"") ?: ""
            if (unescaped.isNotBlank()) {
                tts.speak(unescaped.take(4000), TextToSpeech.QUEUE_FLUSH, null, "BROWSER_TTS")
                isTtsSpeaking = true
                Toast.makeText(context, "Reading page...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No text found to read", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        val webView = webViewCache[activeTab.id]
        when {
            isFindInPageOpen -> {
                isFindInPageOpen = false
                webView?.clearMatches()
            }
            isEditingAddress -> {
                isEditingAddress = false
                focusManager.clearFocus()
            }
            webView?.canGoBack() == true -> webView.goBack()
            tabs.size > 1 -> closeTab(activeTab.id)
            else -> onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isFindInPageOpen) {
                FindInPageBar(
                    query = findQueryText,
                    onQueryChange = { query ->
                        findQueryText = query
                        webViewCache[activeTab.id]?.findAllAsync(query)
                    },
                    onFindNext = { webViewCache[activeTab.id]?.findNext(true) },
                    onFindPrev = { webViewCache[activeTab.id]?.findNext(false) },
                    onClose = {
                        isFindInPageOpen = false
                        webViewCache[activeTab.id]?.clearMatches()
                    }
                )
            } else {
                BrowserTopBar(
                    tab = activeTab,
                    tabCount = tabs.size,
                    addressBarText = addressBarText,
                    isEditingAddress = isEditingAddress,
                    isBookmarked = isCurrentBookmarked,
                    onAddressTextChange = { addressBarText = it },
                    onAddressFocused = { isEditingAddress = true },
                    onSubmitAddress = { loadUrlInActiveTab(addressBarText) },
                    onClearAddress = { addressBarText = "" },
                    onVoiceSearch = { startVoiceSearch() },
                    onBackToApp = onBackClick,
                    onTabsClick = { showTabSwitcher = true },
                    onMenuClick = { showMenu = true },
                    onToggleBookmark = {
                        scope.launch {
                            if (isCurrentBookmarked) {
                                bookmarkDao.removeBookmark(activeTab.url)
                                Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                            } else if (activeTab.url != HOME_PAGE_MARKER) {
                                bookmarkDao.addBookmark(BrowserBookmarkEntity(url = activeTab.url, title = activeTab.title))
                                Toast.makeText(context, "Bookmarked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(visible = activeTab.isLoading) {
                LinearProgressIndicator(
                    progress = { activeTab.progress },
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = TealAccent,
                    trackColor = Color.Transparent
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (activeTab.url == HOME_PAGE_MARKER) {
                    BrowserHomePage(
                        recentHistory = historyList.take(6),
                        onShortcutClick = { url -> loadUrlInActiveTab(url) },
                        onSearchSubmit = { query -> loadUrlInActiveTab(query) },
                        onVoiceSearch = { startVoiceSearch() }
                    )
                } else {
                    key(activeTab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                webViewCache.getOrPut(activeTab.id) {
                                    createBrowserWebView(
                                        context = ctx,
                                        tabState = activeTab,
                                        onRequestNewTab = {
                                            val newTab = BrowserTabState()
                                            tabs.add(newTab)
                                            activeTabId = newTab.id
                                            newTab
                                        },
                                        onNewWebViewReady = { tabId, webView -> webViewCache[tabId] = webView },
                                        onRecordVisit = ::recordVisit
                                    )
                                }.also { webView ->
                                    (webView.parent as? ViewGroup)?.removeView(webView)
                                }
                            }
                        )
                    }
                }
            }

            BrowserBottomBar(
                canGoBack = activeTab.canGoBack,
                canGoForward = activeTab.canGoForward,
                onBackClick = { webViewCache[activeTab.id]?.goBack() },
                onForwardClick = { webViewCache[activeTab.id]?.goForward() },
                onHomeClick = { loadUrlInActiveTab(HOME_PAGE_MARKER) },
                onReloadClick = { webViewCache[activeTab.id]?.reload() },
                onNewTabClick = { openNewTab() }
            )
        }

        if (showTabSwitcher) {
            TabSwitcherOverlay(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = {
                    activeTabId = it
                    showTabSwitcher = false
                },
                onCloseTab = { closeTab(it) },
                onNewTab = {
                    openNewTab()
                    showTabSwitcher = false
                },
                onDismiss = { showTabSwitcher = false }
            )
        }

        if (showMenu) {
            BrowserFullMenuSheet(
                isBookmarked = isCurrentBookmarked,
                isTtsSpeaking = isTtsSpeaking,
                onDismiss = { showMenu = false },
                onToggleBookmark = {
                    showMenu = false
                    scope.launch {
                        if (isCurrentBookmarked) {
                            bookmarkDao.removeBookmark(activeTab.url)
                            Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                        } else if (activeTab.url != HOME_PAGE_MARKER) {
                            bookmarkDao.addBookmark(BrowserBookmarkEntity(url = activeTab.url, title = activeTab.title))
                            Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAddToQA = {
                    showMenu = false
                    if (activeTab.url != HOME_PAGE_MARKER) {
                        scope.launch {
                            bookmarkDao.addBookmark(BrowserBookmarkEntity(url = activeTab.url, title = activeTab.title))
                            Toast.makeText(context, "Added to Quick Access", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSiteSettings = { showMenu = false; showSiteSettingsDialog = true },
                onSaveWebPage = { showMenu = false; saveCurrentWebPage() },
                onShare = {
                    showMenu = false
                    if (activeTab.url != HOME_PAGE_MARKER) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, activeTab.url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share link via"))
                    }
                },
                onFindInPage = { showMenu = false; isFindInPageOpen = true },
                onAddToDesktop = { showMenu = false; addToDesktopShortcut() },
                onTranslate = { showMenu = false; translateCurrentPage() },
                onSniffMedia = { showMenu = false; sniffMediaResources() },
                onViewResources = { showMenu = false; sniffAllResources() },
                onViewSourceCode = { showMenu = false; viewSourceCode() },
                onDevTools = { showMenu = false; injectDevTools() },
                onTextToSpeech = { showMenu = false; toggleTextToSpeech() },
                onGenerateQR = { showMenu = false; showQrDialog = true },
                onDownloads = { showMenu = false; showDownloadsSheet = true },
                onHistory = { showMenu = false; showHistorySheet = true },
                onBookmarks = { showMenu = false; showBookmarksSheet = true },
                onClearData = {
                    showMenu = false
                    CookieManager.getInstance().removeAllCookies(null)
                    webViewCache.values.forEach { it.clearHistory(); it.clearCache(true) }
                    scope.launch { historyDao.clearAll() }
                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showDownloadsSheet) {
            BrowserDownloadsSheet(onDismiss = { showDownloadsSheet = false })
        }

        if (showQrDialog && activeTab.url != HOME_PAGE_MARKER) {
            QrCodeDialog(url = activeTab.url, onDismiss = { showQrDialog = false })
        }

        if (showSourceDialog) {
            SourceCodeViewerDialog(source = sourceCodeContent, onDismiss = { showSourceDialog = false })
        }

        if (showMediaSheet) {
            MediaResourcesSheet(mediaList = sniffedMediaList, onDismiss = { showMediaSheet = false })
        }

        if (showResourcesSheet) {
            PageResourcesSheet(resourceList = sniffedResourcesList, onDismiss = { showResourcesSheet = false })
        }

        if (showSiteSettingsDialog) {
            SiteSettingsDialog(
                tabState = activeTab,
                webView = webViewCache[activeTab.id],
                onDismiss = { showSiteSettingsDialog = false }
            )
        }

        if (showHistorySheet) {
            BrowserHistorySheet(
                historyList = historyList,
                onOpen = { url ->
                    loadUrlInActiveTab(url)
                    showHistorySheet = false
                },
                onDelete = { id -> scope.launch { historyDao.deleteEntry(id) } },
                onClearAll = { scope.launch { historyDao.clearAll() } },
                onDismiss = { showHistorySheet = false }
            )
        }

        if (showBookmarksSheet) {
            BrowserBookmarksSheet(
                bookmarks = bookmarkList,
                onOpen = { url ->
                    loadUrlInActiveTab(url)
                    showBookmarksSheet = false
                },
                onDelete = { url -> scope.launch { bookmarkDao.removeBookmark(url) } },
                onDismiss = { showBookmarksSheet = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top Bar (Fixed Text Visibility + Voice Search)
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserTopBar(
    tab: BrowserTabState,
    tabCount: Int,
    addressBarText: String,
    isEditingAddress: Boolean,
    isBookmarked: Boolean,
    onAddressTextChange: (String) -> Unit,
    onAddressFocused: () -> Unit,
    onSubmitAddress: () -> Unit,
    onClearAddress: () -> Unit,
    onVoiceSearch: () -> Unit,
    onBackToApp: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    Surface(color = SurfaceDark, tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onBackToApp, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Exit browser", tint = TextPrimary)
            }

            // Address bar pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(21.dp))
                    .clickable { onAddressFocused() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (tab.url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(16.dp)
                )

                if (isEditingAddress) {
                    BasicAddressTextField(
                        value = addressBarText,
                        onValueChange = onAddressTextChange,
                        onSubmit = onSubmitAddress,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f)
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                    if (addressBarText.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onClearAddress() }
                        )
                    }
                } else {
                    val displayText = when {
                        tab.url == HOME_PAGE_MARKER -> "Search or type URL"
                        else -> runCatching { Uri.parse(tab.url).host }.getOrNull() ?: tab.url
                    }
                    Text(
                        text = displayText,
                        color = if (tab.url == HOME_PAGE_MARKER) TextMuted else TextPrimary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable { onAddressFocused() }
                    )
                }

                // Microphone / Voice Search Button
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice search",
                    tint = TealAccent,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onVoiceSearch() }
                )
            }

            if (tab.url != HOME_PAGE_MARKER) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Default.StarBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) GoldVip else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, TextSecondary, RoundedCornerShape(8.dp))
                    .clickable { onTabsClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = tabCount.toString(), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun BasicAddressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .fillMaxWidth(),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(TealAccent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text("Search or type URL", color = TextMuted, fontSize = 13.5.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = SurfaceDark, tonalElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in page...", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(46.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark
                )
            )
            IconButton(onClick = onFindPrev) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev", tint = TextPrimary)
            }
            IconButton(onClick = onFindNext) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = TextPrimary)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = RedAccent)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom Toolbar
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onHomeClick: () -> Unit,
    onReloadClick: () -> Unit,
    onNewTabClick: () -> Unit
) {
    Surface(color = Color(0xFF0A0C12), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = if (canGoBack) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onForwardClick, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Forward", tint = if (canGoForward) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = TextPrimary)
            }
            IconButton(onClick = onReloadClick) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextPrimary)
            }
            IconButton(onClick = onNewTabClick) {
                Icon(Icons.Default.Add, contentDescription = "New tab", tint = TextPrimary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Complete Menu Sheet (Includes Downloads)
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserFullMenuSheet(
    isBookmarked: Boolean,
    isTtsSpeaking: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddToQA: () -> Unit,
    onSiteSettings: () -> Unit,
    onSaveWebPage: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onAddToDesktop: () -> Unit,
    onTranslate: () -> Unit,
    onSniffMedia: () -> Unit,
    onViewResources: () -> Unit,
    onViewSourceCode: () -> Unit,
    onDevTools: () -> Unit,
    onTextToSpeech: () -> Unit,
    onGenerateQR: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onClearData: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {
            BrowserMenuItem(
                icon = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                label = if (isBookmarked) "Remove Bookmark" else "Add to Bookmark",
                tint = if (isBookmarked) GoldVip else TextPrimary,
                onClick = onToggleBookmark
            )
            BrowserMenuItem(Icons.Outlined.AddBox, "Add to QA", TextPrimary, onAddToQA)
            BrowserMenuItem(Icons.Outlined.Settings, "Site Settings", TextPrimary, onSiteSettings)
            BrowserMenuItem(Icons.Outlined.SaveAlt, "Save Web Page", TextPrimary, onSaveWebPage)
            BrowserMenuItem(Icons.Outlined.Download, "Downloads", TealAccent, onDownloads)
            BrowserMenuItem(Icons.Outlined.Share, "Share", TextPrimary, onShare)
            BrowserMenuItem(Icons.Outlined.FindInPage, "Find in Page", TextPrimary, onFindInPage)
            BrowserMenuItem(Icons.Outlined.OpenInBrowser, "Add to Desktop", TextPrimary, onAddToDesktop)
            BrowserMenuItem(Icons.Outlined.Translate, "Translate Page", TextPrimary, onTranslate)
            BrowserMenuItem(Icons.Outlined.Podcasts, "Sniff Media Resource", TealAccent, onSniffMedia)
            BrowserMenuItem(Icons.Outlined.Layers, "View Page Resources", TextPrimary, onViewResources)
            BrowserMenuItem(Icons.Outlined.Code, "View Source Code", TextPrimary, onViewSourceCode)
            BrowserMenuItem(Icons.Outlined.Build, "Developer Tools", TextPrimary, onDevTools)
            BrowserMenuItem(
                icon = if (isTtsSpeaking) Icons.Filled.VolumeOff else Icons.Outlined.VolumeUp,
                label = if (isTtsSpeaking) "Stop Text To Speech" else "Page Text To Speech",
                tint = if (isTtsSpeaking) RedAccent else TextPrimary,
                onClick = onTextToSpeech
            )
            BrowserMenuItem(Icons.Outlined.QrCode, "Generate QR Code", TextPrimary, onGenerateQR)

            HorizontalDivider(color = BorderDark, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

            BrowserMenuItem(Icons.Outlined.History, "History", TextPrimary, onHistory)
            BrowserMenuItem(Icons.Outlined.BookmarkBorder, "Bookmarks", TextPrimary, onBookmarks)
            BrowserMenuItem(Icons.Outlined.DeleteSweep, "Clear Browsing Data", RedAccent, onClearData)
        }
    }
}

@Composable
private fun BrowserMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------------------------
// Chrome-Style Downloads Bottom Sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserDownloadsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloadManager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager }
    var downloads by remember { mutableStateOf<List<DownloadInfo>>(emptyList()) }

    fun refreshDownloads() {
        if (downloadManager == null) return
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query) ?: return
        val list = mutableListOf<DownloadInfo>()
        val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

        while (cursor.moveToNext()) {
            val id = if (idCol != -1) cursor.getLong(idCol) else 0L
            val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Downloaded File" else "Downloaded File"
            val status = if (statusCol != -1) cursor.getInt(statusCol) else DownloadManager.STATUS_SUCCESSFUL
            val bytesDownloaded = if (downloadedCol != -1) cursor.getLong(downloadedCol) else 0L
            val bytesTotal = if (totalCol != -1) cursor.getLong(totalCol) else 0L
            val uri = if (uriCol != -1) cursor.getString(uriCol) else null
            list.add(DownloadInfo(id, title, status, bytesDownloaded, bytesTotal, uri))
        }
        cursor.close()
        downloads = list.reversed()
    }

    LaunchedEffect(Unit) {
        refreshDownloads()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Downloads", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    try {
                        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open downloads folder", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Folder, contentDescription = "Open Folder", tint = TealAccent)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No downloads yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (item.status) {
                                            DownloadManager.STATUS_RUNNING -> Icons.Default.Download
                                            DownloadManager.STATUS_SUCCESSFUL -> Icons.Default.FileDownloadDone
                                            else -> Icons.Default.ErrorOutline
                                        },
                                        contentDescription = null,
                                        tint = if (item.status == DownloadManager.STATUS_SUCCESSFUL) TealAccent else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        val sizeText = if (item.bytesTotal > 0) {
                                            "${Formatter.formatFileSize(context, item.bytesDownloaded)} / ${Formatter.formatFileSize(context, item.bytesTotal)}"
                                        } else {
                                            Formatter.formatFileSize(context, item.bytesDownloaded)
                                        }
                                        Text(sizeText, color = TextMuted, fontSize = 11.sp)
                                    }
                                    if (item.status == DownloadManager.STATUS_SUCCESSFUL && item.localUri != null) {
                                        IconButton(onClick = {
                                            try {
                                                val fileUri = Uri.parse(item.localUri)
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(fileUri, context.contentResolver.getType(fileUri) ?: "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(openIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No app found to open file", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = TealAccent)
                                        }
                                    }
                                    IconButton(onClick = {
                                        downloadManager?.remove(item.id)
                                        refreshDownloads()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAccent, modifier = Modifier.size(20.dp))
                                    }
                                }

                                if (item.status == DownloadManager.STATUS_RUNNING && item.bytesTotal > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { item.bytesDownloaded.toFloat() / item.bytesTotal },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = TealAccent,
                                        trackColor = BorderDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Interactive Feature Dialogs (Source Code, QR Code, Media, Settings)
// ---------------------------------------------------------------------------------------------

@Composable
private fun QrCodeDialog(url: String, onDismiss: () -> Unit) {
    val qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=${Uri.encode(url)}"
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("QR Code for Page", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))
                AsyncImage(
                    model = qrApiUrl,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(url, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SourceCodeViewerDialog(source: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Page Source Code", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Source Code", source))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TealAccent)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = source,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaResourcesSheet(mediaList: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).padding(16.dp)) {
            Text("Sniffed Media Resources (${mediaList.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (mediaList.isEmpty()) {
                Text("No audio/video media streams found on this page.", color = TextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mediaList) { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = TealAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(url, color = TextPrimary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Media URL", url))
                                Toast.makeText(context, "Media link copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageResourcesSheet(resourceList: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).padding(16.dp)) {
            Text("Page Resources (${resourceList.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (resourceList.isEmpty()) {
                Text("No external resources found.", color = TextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(resourceList) { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantDark, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(url, color = TextPrimary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Resource URL", url))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SiteSettingsDialog(
    tabState: BrowserTabState,
    webView: WebView?,
    onDismiss: () -> Unit
) {
    var isJsEnabled by remember { mutableStateOf(webView?.settings?.javaScriptEnabled ?: true) }
    var isDesktop by remember { mutableStateOf(tabState.isDesktopMode) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Site Settings", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Desktop Site Mode", color = TextPrimary, fontSize = 13.5.sp)
                    Switch(
                        checked = isDesktop,
                        onCheckedChange = { checked ->
                            isDesktop = checked
                            tabState.isDesktopMode = checked
                            webView?.settings?.apply {
                                userAgentString = if (checked) {
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                } else null
                                useWideViewPort = checked
                                loadWithOverviewMode = checked
                            }
                            webView?.reload()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable JavaScript", color = TextPrimary, fontSize = 13.5.sp)
                    Switch(
                        checked = isJsEnabled,
                        onCheckedChange = { checked ->
                            isJsEnabled = checked
                            webView?.settings?.javaScriptEnabled = checked
                            webView?.reload()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = Color.Black)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home & Shortcuts
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserHomePage(
    recentHistory: List<BrowserHistoryEntity>,
    onShortcutClick: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onVoiceSearch: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Icon(Icons.Default.Public, contentDescription = null, tint = TealAccent, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Google or type a URL", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = TealAccent,
                    modifier = Modifier.clickable { onVoiceSearch() }
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = BorderDark,
                focusedContainerColor = SurfaceVariantDark,
                unfocusedContainerColor = SurfaceVariantDark
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSearchSubmit(query) })
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("Shortcuts", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            items(quickShortcuts) { shortcut ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onShortcutClick(shortcut.url) }
                ) {
                    val host = runCatching { Uri.parse(shortcut.url).host }.getOrNull()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://www.google.com/s2/favicons?domain=$host&sz=64",
                            contentDescription = shortcut.label,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(shortcut.label, color = TextSecondary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (recentHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            Text("Recently visited", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recentHistory.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onShortcutClick(entry.url) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Column {
                            Text(entry.title.ifBlank { entry.url }, color = TextPrimary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.url, color = TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab Switcher Overlay
// ---------------------------------------------------------------------------------------------

@Composable
private fun TabSwitcherOverlay(
    tabs: List<BrowserTabState>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark.copy(alpha = 0.98f))
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tabs.size} Tabs", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable { onSelectTab(tab.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            border = BorderStrokeOrNull(tab.id == activeTabId)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (tab.url == HOME_PAGE_MARKER) "New Tab" else tab.title.ifBlank { tab.url },
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onCloseTab(tab.id) }
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = if (tab.url == HOME_PAGE_MARKER) "" else (runCatching { Uri.parse(tab.url).host }.getOrNull() ?: ""),
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = onNewTab,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Tab", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BorderStrokeOrNull(active: Boolean) =
    if (active) androidx.compose.foundation.BorderStroke(1.5.dp, TealAccent) else androidx.compose.foundation.BorderStroke(1.dp, BorderDark)

// ---------------------------------------------------------------------------------------------
// History & Bookmarks Sheets
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserHistorySheet(
    historyList: List<BrowserHistoryEntity>,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearAll) { Text("Clear all", color = RedAccent, fontSize = 12.sp) }
            }
            if (historyList.isEmpty()) {
                Text("No browsing history yet.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(historyList, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(entry.url) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title.ifBlank { entry.url }, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.url, color = TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp).clickable { onDelete(entry.id) }
                            )
                        }
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserBookmarksSheet(
    bookmarks: List<BrowserBookmarkEntity>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Text("Bookmarks", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            if (bookmarks.isEmpty()) {
                Text("No bookmarks saved yet. Tap the ☆ icon on any page to save it.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyColumn {
                    items(bookmarks, key = { it.url }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(bookmark.url) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bookmark.title.ifBlank { bookmark.url }, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bookmark.url, color = TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove bookmark",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp).clickable { onDelete(bookmark.url) }
                            )
                        }
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// WebView Factory (Chrome-Style Cache Mode)
// ---------------------------------------------------------------------------------------------

private fun createBrowserWebView(
    context: Context,
    tabState: BrowserTabState,
    onRequestNewTab: () -> BrowserTabState,
    onNewWebViewReady: (String, WebView) -> Unit,
    onRecordVisit: (String, String) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false

            cacheMode = WebSettings.LOAD_DEFAULT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(this, true)
        }

        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(url.toUri()).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file...")
                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                    )
                }
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                Toast.makeText(context, "Download started… Check Downloads menu", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("BrowserScreen", "Download failed: ${e.message}", e)
                Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
            }
        })

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tabState.progress = newProgress / 100f
                tabState.isLoading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    tabState.title = title
                    onRecordVisit(tabState.url, title)
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val newTabState = onRequestNewTab()
                val newWebView = createBrowserWebView(context, newTabState, onRequestNewTab, onNewWebViewReady, onRecordVisit)
                onNewWebViewReady(newTabState.id, newWebView)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.deny()
            }
        }

        webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                tabState.isLoading = true
                url?.let { tabState.url = it }
                tabState.canGoBack = view?.canGoBack() ?: false
                tabState.canGoForward = view?.canGoForward() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tabState.isLoading = false
                url?.let { tabState.url = it }
                tabState.canGoBack = view?.canGoBack() ?: false
                tabState.canGoForward = view?.canGoForward() ?: false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase() ?: ""
                return if (scheme == "http" || scheme == "https") {
                    false
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, targetUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Log.w("BrowserScreen", "No app found to handle: $targetUri")
                    }
                    true
                }
            }
        }

        if (tabState.url != HOME_PAGE_MARKER) {
            loadUrl(tabState.url)
        }
    }
}
