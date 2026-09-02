@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.speech.RecognizerIntent
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.data.local.AppDatabase
import com.example.data.local.BrowserBookmarkEntity
import com.example.data.local.BrowserHistoryEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val HOME_PAGE_MARKER = "app://home"
private const val DEFAULT_SEARCH_ENGINE = "https://www.google.com/search?q="
private const val MAX_TABS = 12

private fun findActivityFromContext(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

internal class BrowserTabState(initialUrl: String = HOME_PAGE_MARKER) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New Tab")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var isLoading by mutableStateOf(false)
    var isDesktopMode by mutableStateOf(false)
    var previewBitmap by mutableStateOf<Bitmap?>(null)
}

internal data class DownloadInfo(
    val id: Long,
    val title: String,
    val status: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val localUri: String?
)

data class QuickShortcut(val label: String, val url: String, val isCustom: Boolean = false)

data class ShortcutCategoryGroup(
    val categoryName: String,
    val icon: ImageVector,
    val items: List<QuickShortcut>
)

// =========================================================================
// 🌟 সাজানো শর্টকাট তালিকা
// =========================================================================
private val categorizedShortcutsList = listOf(
    ShortcutCategoryGroup(
        categoryName = "AI Intelligence & Chatbots",
        icon = Icons.Default.SmartToy,
        items = listOf(
            QuickShortcut("ChatGPT", "https://chatgpt.com"),
            QuickShortcut("Gemini", "https://gemini.google.com"),
            QuickShortcut("Claude", "https://claude.ai"),
            QuickShortcut("DeepSeek", "https://chat.deepseek.com"),
            QuickShortcut("Perplexity", "https://www.perplexity.ai"),
            QuickShortcut("Copilot", "https://copilot.microsoft.com"),
            QuickShortcut("Poe AI", "https://poe.com"),
            QuickShortcut("HuggingFace", "https://huggingface.co"),
            QuickShortcut("Midjourney", "https://www.midjourney.com"),
            QuickShortcut("Leonardo AI", "https://leonardo.ai"),
            QuickShortcut("Mistral AI", "https://chat.mistral.ai"),
            QuickShortcut("Phind", "https://www.phind.com")
        )
    ),
    ShortcutCategoryGroup(
        categoryName = "Online Video Downloaders",
        icon = Icons.Default.DownloadForOffline,
        items = listOf(
            QuickShortcut("Cobalt Tools", "https://cobalt.tools"),
            QuickShortcut("SaveFrom", "https://en.savefrom.net"),
            QuickShortcut("SnapSave", "https://snapsave.app"),
            QuickShortcut("Y2Mate", "https://www.y2mate.com"),
            QuickShortcut("SSSTikTok", "https://ssstik.io"),
            QuickShortcut("FDown FB", "https://fdown.net"),
            QuickShortcut("SaveInsta", "https://saveinsta.app"),
            QuickShortcut("TwitterVid", "https://twittervid.com"),
            QuickShortcut("RapidSave", "https://rapidsave.com"),
            QuickShortcut("10Downloader", "https://10downloader.com")
        )
    ),
    ShortcutCategoryGroup(
        categoryName = "Google Workspace & Tools",
        icon = Icons.Default.Language,
        items = listOf(
            QuickShortcut("Google", "https://www.google.com"),
            QuickShortcut("Gmail", "https://mail.google.com"),
            QuickShortcut("Google Drive", "https://drive.google.com"),
            QuickShortcut("Google Maps", "https://maps.google.com"),
            QuickShortcut("Translate", "https://translate.google.com"),
            QuickShortcut("Photos", "https://photos.google.com"),
            QuickShortcut("Docs", "https://docs.google.com"),
            QuickShortcut("Sheets", "https://sheets.google.com"),
            QuickShortcut("Meet", "https://meet.google.com"),
            QuickShortcut("Calendar", "https://calendar.google.com"),
            QuickShortcut("Keep Notes", "https://keep.google.com"),
            QuickShortcut("Trends", "https://trends.google.com")
        )
    ),
    ShortcutCategoryGroup(
        categoryName = "Social Media & Community",
        icon = Icons.Default.Groups,
        items = listOf(
            QuickShortcut("Facebook", "https://www.facebook.com"),
            QuickShortcut("YouTube", "https://www.youtube.com"),
            QuickShortcut("Instagram", "https://www.instagram.com"),
            QuickShortcut("TikTok", "https://www.tiktok.com"),
            QuickShortcut("X (Twitter)", "https://www.x.com"),
            QuickShortcut("Reddit", "https://www.reddit.com"),
            QuickShortcut("Telegram Web", "https://web.telegram.org"),
            QuickShortcut("WhatsApp Web", "https://web.whatsapp.com"),
            QuickShortcut("LinkedIn", "https://www.linkedin.com"),
            QuickShortcut("Pinterest", "https://www.pinterest.com"),
            QuickShortcut("Discord", "https://discord.com"),
            QuickShortcut("Quora", "https://www.quora.com")
        )
    ),
    ShortcutCategoryGroup(
        categoryName = "Streaming & Entertainment",
        icon = Icons.Default.Movie,
        items = listOf(
            QuickShortcut("Netflix", "https://www.netflix.com"),
            QuickShortcut("Prime Video", "https://www.primevideo.com"),
            QuickShortcut("Spotify", "https://open.spotify.com"),
            QuickShortcut("SoundCloud", "https://soundcloud.com"),
            QuickShortcut("Twitch", "https://www.twitch.tv"),
            QuickShortcut("Crunchyroll", "https://www.crunchyroll.com"),
            QuickShortcut("IMDb", "https://www.imdb.com"),
            QuickShortcut("Wikipedia", "https://www.wikipedia.org"),
            QuickShortcut("XNXX", "https://www.xnxxvideos.me"),
            QuickShortcut("xHamster", "https://xhamster46.desi")
        )
    ),
    ShortcutCategoryGroup(
        categoryName = "Utilities & Tech Tools",
        icon = Icons.Default.Build,
        items = listOf(
            QuickShortcut("Canva", "https://www.canva.com"),
            QuickShortcut("Remove.bg", "https://www.remove.bg"),
            QuickShortcut("TinyPNG", "https://tinypng.com"),
            QuickShortcut("Speedtest", "https://www.speedtest.net"),
            QuickShortcut("GitHub", "https://github.com"),
            QuickShortcut("Stack Overflow", "https://stackoverflow.com"),
            QuickShortcut("Archive.org", "https://archive.org"),
            QuickShortcut("VirusTotal", "https://www.virustotal.com")
        )
    )
)

private fun captureTabSnapshot(webView: WebView?, tab: BrowserTabState) {
    try {
        if (webView != null && webView.width > 0 && webView.height > 0) {
            val bitmap = Bitmap.createBitmap(webView.width / 2, webView.height / 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(0.5f, 0.5f)
            webView.draw(canvas)
            tab.previewBitmap = bitmap
        }
    } catch (_: Exception) {}
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivityFromContext(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val historyDao = remember { AppDatabase.getInstance(context).browserHistoryDao() }
    val bookmarkDao = remember { AppDatabase.getInstance(context).browserBookmarkDao() }
    val bookmarkList by bookmarkDao.getBookmarks().collectAsState(initial = emptyList())
    val historyList by historyDao.getHistory().collectAsState(initial = emptyList())

    val startUrl = remember { initialUrl?.takeIf { it.isNotBlank() } ?: HOME_PAGE_MARKER }
    val tabs = remember { mutableStateListOf(BrowserTabState(startUrl)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab by remember { derivedStateOf { tabs.firstOrNull { it.id == activeTabId } ?: tabs.first() } }
    val webViewCache = remember { mutableMapOf<String, WebView>() }

    var browserCustomView by remember { mutableStateOf<View?>(null) }
    var browserCustomViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    var addressBarText by remember(activeTabId) { mutableStateOf(activeTab.url.takeIf { it != HOME_PAGE_MARKER } ?: "") }
    var isEditingAddress by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab.url) {
        if (!isEditingAddress) {
            addressBarText = if (activeTab.url == HOME_PAGE_MARKER) "" else activeTab.url
        }
    }

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var isCurrentBookmarked by remember { mutableStateOf(false) }

    var isFindInPageOpen by remember { mutableStateOf(false) }
    var findQueryText by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSiteSettingsDialog by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }

    val customShortcuts = remember { mutableStateListOf<QuickShortcut>() }

    fun loadCustomShortcuts() {
        val prefs = context.getSharedPreferences("browser_custom_shortcuts_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_shortcuts_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            customShortcuts.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                customShortcuts.add(QuickShortcut(obj.getString("name"), obj.getString("url"), isCustom = true))
            }
        } catch (_: Exception) {}
    }

    fun saveCustomShortcut(name: String, url: String) {
        val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        customShortcuts.add(QuickShortcut(name, cleanUrl, isCustom = true))

        val prefs = context.getSharedPreferences("browser_custom_shortcuts_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        customShortcuts.forEach {
            arr.put(JSONObject().apply {
                put("name", it.label)
                put("url", it.url)
            })
        }
        prefs.edit().putString("custom_shortcuts_json", arr.toString()).apply()
        Toast.makeText(context, "Shortcut added successfully!", Toast.LENGTH_SHORT).show()
    }

    fun deleteCustomShortcut(shortcut: QuickShortcut) {
        customShortcuts.remove(shortcut)
        val prefs = context.getSharedPreferences("browser_custom_shortcuts_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        customShortcuts.forEach {
            arr.put(JSONObject().apply {
                put("name", it.label)
                put("url", it.url)
            })
        }
        prefs.edit().putString("custom_shortcuts_json", arr.toString()).apply()
        Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { loadCustomShortcuts() }

    LaunchedEffect(browserCustomView) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (browserCustomView != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = act.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in any language...")
            }
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice recognition not available", Toast.LENGTH_SHORT).show()
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
        captureTabSnapshot(webViewCache[activeTab.id], activeTab)

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

    BackHandler {
        if (browserCustomView != null) {
            browserCustomViewCallback?.onCustomViewHidden()
            browserCustomView = null
        } else if (isFindInPageOpen) {
            isFindInPageOpen = false
            webViewCache[activeTab.id]?.clearMatches()
        } else if (isEditingAddress) {
            isEditingAddress = false
            focusManager.clearFocus()
        } else if (webViewCache[activeTab.id]?.canGoBack() == true) {
            webViewCache[activeTab.id]?.goBack()
        } else if (activeTab.url != HOME_PAGE_MARKER) {
            activeTab.url = HOME_PAGE_MARKER
            addressBarText = ""
        } else if (tabs.size > 1) {
            closeTab(activeTab.id)
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (browserCustomView != null) {
            AndroidView(
                factory = { browserCustomView!! },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        } else {
            // 🔝 স্ট্যান্ডার্ড লেআউট (সার্চ বার সবসময় ওয়েবসাইটের উপরে থাকবে, কোনো বাটন কাটবে না)
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
                        onAddressTextChange = { addressBarText = it },
                        onAddressFocused = { isEditingAddress = true },
                        onSubmitAddress = { loadUrlInActiveTab(addressBarText) },
                        onClearAddress = { addressBarText = "" },
                        onVoiceSearch = { startVoiceSearch() },
                        onNewTabClick = { openNewTab() },
                        onTabsClick = {
                            captureTabSnapshot(webViewCache[activeTab.id], activeTab)
                            showTabSwitcher = true
                        },
                        onMenuClick = { showMenu = true }
                    )
                }

                AnimatedVisibility(visible = activeTab.isLoading) {
                    LinearProgressIndicator(
                        progress = { activeTab.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = TealAccent,
                        trackColor = Color.Transparent
                    )
                }

                // 🌐 মূল কনটেন্ট (হোমপেজ অথবা রেসপনসিভ ওয়েবভিউ)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (activeTab.url == HOME_PAGE_MARKER) {
                        BrowserHomePage(
                            categories = categorizedShortcutsList,
                            customShortcuts = customShortcuts,
                            onShortcutClick = { url -> loadUrlInActiveTab(url) },
                            onAddCustomShortcut = { name, url -> saveCustomShortcut(name, url) },
                            onDeleteCustomShortcut = { deleteCustomShortcut(it) },
                            onSearchSubmit = { query -> loadUrlInActiveTab(query) },
                            onVoiceSearch = { startVoiceSearch() }
                        )
                    } else {
                        key(activeTab.id) {
                            val pullRefreshState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = activeTab.isLoading,
                                onRefresh = { webViewCache[activeTab.id]?.reload() },
                                state = pullRefreshState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        webViewCache.getOrPut(activeTab.id) {
                                            createBrowserWebView(
                                                context = ctx,
                                                tabState = activeTab,
                                                onRecordVisit = ::recordVisit,
                                                onShowCustomView = { view, callback ->
                                                    browserCustomView = view
                                                    browserCustomViewCallback = callback
                                                },
                                                onHideCustomView = {
                                                    browserCustomViewCallback?.onCustomViewHidden()
                                                    browserCustomView = null
                                                },
                                                onShowFileChooser = { callback ->
                                                    filePathCallback?.onReceiveValue(null)
                                                    filePathCallback = callback
                                                    try {
                                                        fileChooserLauncher.launch("*/*")
                                                    } catch (_: Exception) {
                                                        filePathCallback = null
                                                    }
                                                }
                                            )
                                        }.also { webView ->
                                            (webView.parent as? ViewGroup)?.removeView(webView)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dialogs & Sheets
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
                        saveCustomShortcut(activeTab.title.take(15), activeTab.url)
                    }
                },
                onSiteSettings = { showMenu = false; showSiteSettingsDialog = true },
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
                onQrCode = { showMenu = false; showQrDialog = true },
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

// -------------------------------------------------------------
// 🏠 হোম পেজ (ক্যাটাগরি ভিত্তিক সুসজ্জিত শর্টকাটস)
// -------------------------------------------------------------
@Composable
private fun BrowserHomePage(
    categories: List<ShortcutCategoryGroup>,
    customShortcuts: List<QuickShortcut>,
    onShortcutClick: (String) -> Unit,
    onAddCustomShortcut: (name: String, url: String) -> Unit,
    onDeleteCustomShortcut: (QuickShortcut) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onVoiceSearch: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Public, contentDescription = null, tint = TealAccent, modifier = Modifier.size(34.dp))
        Spacer(modifier = Modifier.height(10.dp))

        // 🔍 সার্চ বার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(SurfaceVariantDark)
                .border(1.dp, BorderDark, RoundedCornerShape(23.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))

            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.5.sp, color = Color.White, fontWeight = FontWeight.Normal),
                cursorBrush = SolidColor(TealAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSearchSubmit(query) }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search Google or type URL", color = TextMuted, fontSize = 13.sp)
                        }
                        innerTextField()
                    }
                }
            )

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Search",
                tint = TealAccent,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onVoiceSearch() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🌟 কুইক অ্যাক্সেস হেডার
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Access", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceVariantDark,
                border = BorderStroke(0.6.dp, TealAccent.copy(alpha = 0.6f)),
                modifier = Modifier.clickable { showAddDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = TealAccent, modifier = Modifier.size(12.dp))
                    Text("Add Link", color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (customShortcuts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(customShortcuts) { shortcut ->
                    CompactShortcutItem(shortcut = shortcut, onClick = onShortcutClick, onLongClick = onDeleteCustomShortcut)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔲 ক্যাটাগরি ভিত্তিক প্রিমিয়াম শর্টকাট গ্রিড
        categories.forEach { category ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(category.icon, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                    Text(category.categoryName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(color = BorderDark, thickness = 0.6.dp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                }

                val chunkedItems = category.items.chunked(2)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chunkedItems) { columnPair ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            columnPair.forEach { item ->
                                CompactShortcutItem(shortcut = item, onClick = onShortcutClick, onLongClick = onDeleteCustomShortcut)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddShortcutDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, url ->
                    onAddCustomShortcut(name, url)
                    showAddDialog = false
                }
            )
        }
    }
}

// -------------------------------------------------------------
// 🔲 কম্প্যাক্ট স্লিম শর্টকাট আইটেম
// -------------------------------------------------------------
@Composable
private fun CompactShortcutItem(
    shortcut: QuickShortcut,
    onClick: (String) -> Unit,
    onLongClick: (QuickShortcut) -> Unit
) {
    val host = runCatching { Uri.parse(shortcut.url).host }.getOrNull() ?: "google.com"
    val faviconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=64"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(108.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark)
            .border(BorderStroke(0.6.dp, TealAccent.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { onClick(shortcut.url) },
                onLongClick = { onLongClick(shortcut) }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF131A26)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = faviconUrl,
                contentDescription = shortcut.label,
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = shortcut.label,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// -------------------------------------------------------------
// ➕ Add Shortcut Dialog
// -------------------------------------------------------------
@Composable
private fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Custom Shortcut", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Website Name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) {
                                onAdd(name.trim(), url.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                    ) {
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🔝 টপ বার (Top Bar)
// -------------------------------------------------------------
@Composable
private fun BrowserTopBar(
    tab: BrowserTabState,
    tabCount: Int,
    addressBarText: String,
    isEditingAddress: Boolean,
    onAddressTextChange: (String) -> Unit,
    onAddressFocused: () -> Unit,
    onSubmitAddress: () -> Unit,
    onClearAddress: () -> Unit,
    onVoiceSearch: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Surface(
        color = SurfaceDark,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .clickable { onAddressFocused() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (tab.url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(15.dp)
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
                            modifier = Modifier.size(18.dp).clickable { onClearAddress() }
                        )
                    }
                } else {
                    val displayText = when {
                        tab.url == HOME_PAGE_MARKER -> "Search or type URL"
                        else -> runCatching { Uri.parse(tab.url).host }.getOrNull() ?: tab.url
                    }
                    Text(
                        text = displayText,
                        color = if (tab.url == HOME_PAGE_MARKER) TextMuted else Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable { onAddressFocused() }
                    )
                }

                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice search",
                    tint = TealAccent,
                    modifier = Modifier.size(19.dp).clip(RoundedCornerShape(10.dp)).clickable { onVoiceSearch() }
                )
            }

            IconButton(onClick = onNewTabClick, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .clickable { onTabsClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = tabCount.toString(), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onMenuClick, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = Color.White)
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
        modifier = modifier.focusRequester(focusRequester).fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.5.sp, color = Color.White, fontWeight = FontWeight.Normal),
        cursorBrush = SolidColor(TealAccent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Search or type URL", color = TextMuted, fontSize = 13.sp)
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                    cursorBrush = SolidColor(TealAccent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Find in page...", color = TextMuted, fontSize = 13.sp)
                        inner()
                    }
                )
            }

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
                .background(Color(0xFF080C14))
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tabs.size} Tabs", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onNewTab,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White)
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTabId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clickable { onSelectTab(tab.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) TealAccent else Color(0xFF1E293B)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color(0xFF1A2333) else Color(0xFF101622))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val host = runCatching { Uri.parse(tab.url).host }.getOrNull() ?: ""
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (host.isNotBlank() && tab.url != HOME_PAGE_MARKER) {
                                            AsyncImage(
                                                model = "https://www.google.com/s2/favicons?domain=$host&sz=32",
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Icon(Icons.Default.Public, contentDescription = null, tint = TealAccent, modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            text = if (tab.url == HOME_PAGE_MARKER) "New Tab" else tab.title.ifBlank { tab.url },
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { onCloseTab(tab.id) }
                                            .padding(2.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color(0xFF0C1017)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tab.previewBitmap != null) {
                                        Image(
                                            bitmap = tab.previewBitmap!!.asImageBitmap(),
                                            contentDescription = "Page Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(36.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Home Page", color = Color(0xFF64748B), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserFullMenuSheet(
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddToQA: () -> Unit,
    onSiteSettings: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onQrCode: () -> Unit,
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
            BrowserMenuItem(Icons.Outlined.AddBox, "Add to Quick Access", TextPrimary, onAddToQA)
            BrowserMenuItem(Icons.Outlined.Settings, "Site Settings", TextPrimary, onSiteSettings)
            BrowserMenuItem(Icons.Outlined.Download, "Downloads", TealAccent, onDownloads)
            BrowserMenuItem(Icons.Outlined.Share, "Share", TextPrimary, onShare)
            BrowserMenuItem(Icons.Outlined.FindInPage, "Find in Page", TextPrimary, onFindInPage)
            BrowserMenuItem(Icons.Outlined.QrCode, "Generate QR Code", TextPrimary, onQrCode)

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

    LaunchedEffect(Unit) { refreshDownloads() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Downloads", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No downloads found.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = TextPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    val sizeText = if (item.bytesTotal > 0) Formatter.formatFileSize(context, item.bytesTotal) else ""
                                    Text(sizeText, color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
private fun SiteSettingsDialog(
    tabState: BrowserTabState,
    webView: WebView?,
    onDismiss: () -> Unit
) {
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
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                } else null
                                useWideViewPort = checked
                                loadWithOverviewMode = checked
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserHistorySheet(
    historyList: List<BrowserHistoryEntity>,
    onOpen: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearAll) { Text("Clear all", color = RedAccent, fontSize = 13.sp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No browsing history yet.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(historyList, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(entry.url) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title.ifBlank { entry.url }, color = TextPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.url, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp).clickable { onDelete(entry.id) }
                            )
                        }
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    }
                }
            }
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Bookmarks", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No bookmarks saved yet.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(bookmarks, key = { it.url }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(bookmark.url) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bookmark.title.ifBlank { bookmark.url }, color = TextPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bookmark.url, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove bookmark",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp).clickable { onDelete(bookmark.url) }
                            )
                        }
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🌐 WebView Factory
// -------------------------------------------------------------
private fun createBrowserWebView(
    context: Context,
    tabState: BrowserTabState,
    onRecordVisit: (String, String) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit
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
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file...")
                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "Downloading file...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tabState.progress = newProgress / 100f
                tabState.isLoading = newProgress < 100
                if (newProgress > 70) {
                    captureTabSnapshot(view, tabState)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    tabState.title = title
                    onRecordVisit(tabState.url, title)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                onHideCustomView()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback != null) {
                    onShowFileChooser(filePathCallback)
                    return true
                }
                return false
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
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
                captureTabSnapshot(view, tabState)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val scheme = targetUri.scheme?.lowercase() ?: ""
                return if (scheme == "http" || scheme == "https") {
                    false
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, targetUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                    true
                }
            }
        }

        if (tabState.url != HOME_PAGE_MARKER) {
            loadUrl(tabState.url)
        }
    }
}
