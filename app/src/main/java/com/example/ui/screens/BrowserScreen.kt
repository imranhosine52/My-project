@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.data.local.AppDatabase
import com.example.data.local.BrowserBookmarkEntity
import com.example.data.local.BrowserHistoryEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 🌐 In-App Web Browser (Chrome-style)
 *
 * A general-purpose browsing screen — separate from [com.example.ui.components.InAppBrowserDialog]
 * which is only used for sponsor/verification links. This screen lets the user freely browse
 * any website with multiple tabs, an editable address bar, history and bookmarks, similar to
 * a lightweight mobile Chrome.
 */

private const val HOME_PAGE_MARKER = "app://home"
private const val DEFAULT_SEARCH_ENGINE = "https://www.google.com/search?q="
private const val MAX_TABS = 8

/** Observable per-tab state. Each open tab gets its own instance and its own cached WebView. */
private class BrowserTabState(initialUrl: String = HOME_PAGE_MARKER) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New Tab")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var isLoading by mutableStateOf(false)
}

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

    // ---- Tabs ----
    val tabs = remember { mutableStateListOf(BrowserTabState()) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab by remember { derivedStateOf { tabs.first { it.id == activeTabId } } }
    // Keeps real WebView instances alive across tab switches so scroll/history state isn't lost.
    val webViewCache = remember { mutableMapOf<String, WebView>() }

    var addressBarText by remember(activeTabId) { mutableStateOf(activeTab.url.takeIf { it != HOME_PAGE_MARKER } ?: "") }
    var isEditingAddress by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var isCurrentBookmarked by remember { mutableStateOf(false) }

    LaunchedEffect(activeTabId, activeTab.url, bookmarkList) {
        isCurrentBookmarked = bookmarkList.any { it.url == activeTab.url }
    }

    fun openNewTab(url: String = HOME_PAGE_MARKER, switchToIt: Boolean = true) {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(context, "Maximum $MAX_TABS tabs allowed. Close one first.", Toast.LENGTH_SHORT).show()
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

    // Hardware/gesture back: WebView back -> close extra tab -> exit screen
    BackHandler {
        val webView = webViewCache[activeTab.id]
        when {
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
            BrowserTopBar(
                tab = activeTab,
                tabCount = tabs.size,
                addressBarText = addressBarText,
                isEditingAddress = isEditingAddress,
                isBookmarked = isCurrentBookmarked,
                onAddressTextChange = { addressBarText = it },
                onAddressFocused = { isEditingAddress = true },
                onSubmitAddress = { loadUrlInActiveTab(addressBarText) },
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
                        onSearchSubmit = { query -> loadUrlInActiveTab(query) }
                    )
                } else {
                    // Keyed so each tab gets its own AndroidView slot -> its own cached WebView.
                    key(activeTab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                webViewCache.getOrPut(activeTab.id) {
                                    createBrowserWebView(
                                        context = ctx,
                                        tabState = activeTab,
                                        onRequestNewTab = {
                                            // Creates the tab entry first so the WebView we're
                                            // about to build can report progress/title/back-forward
                                            // into the SAME state object the UI is reading from.
                                            val newTab = BrowserTabState()
                                            tabs.add(newTab)
                                            activeTabId = newTab.id
                                            newTab
                                        },
                                        onNewWebViewReady = { tabId, webView -> webViewCache[tabId] = webView },
                                        onRecordVisit = ::recordVisit
                                    )
                                }.also { webView ->
                                    // Re-attach if it was detached from a previous tab-switch.
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
            BrowserMenuDropdown(
                onDismiss = { showMenu = false },
                onNewTab = { openNewTab(); showMenu = false },
                onHistory = { showHistorySheet = true; showMenu = false },
                onBookmarks = { showBookmarksSheet = true; showMenu = false },
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
                onClearData = {
                    showMenu = false
                    CookieManager.getInstance().removeAllCookies(null)
                    webViewCache.values.forEach { it.clearHistory(); it.clearCache(true) }
                    scope.launch { historyDao.clearAll() }
                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                }
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
// Top bar (address bar + tabs + menu)
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
                    modifier = Modifier.size(14.dp)
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
                } else {
                    val displayText = when {
                        tab.url == HOME_PAGE_MARKER -> "Search or type a website name"
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

            // Tab count button
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .heightIn(min = 40.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = TealAccent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() })
    )
}

// ---------------------------------------------------------------------------------------------
// Bottom toolbar
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
// New-tab / home page (speed dial)
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserHomePage(
    recentHistory: List<BrowserHistoryEntity>,
    onShortcutClick: (String) -> Unit,
    onSearchSubmit: (String) -> Unit
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
// Tab switcher overlay
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
// Overflow menu
// ---------------------------------------------------------------------------------------------

@Composable
private fun BrowserMenuDropdown(
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onShare: () -> Unit,
    onClearData: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            BrowserMenuRow(Icons.Default.Add, "New Tab", TextPrimary, onNewTab)
            BrowserMenuRow(Icons.Default.History, "History", TextPrimary, onHistory)
            BrowserMenuRow(Icons.Default.Star, "Bookmarks", TextPrimary, onBookmarks)
            BrowserMenuRow(Icons.Default.Share, "Share Page", TextPrimary, onShare)
            HorizontalDivider(color = BorderDark, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
            BrowserMenuRow(Icons.Default.DeleteSweep, "Clear Browsing Data", RedAccent, onClearData)
        }
    }
}

@Composable
private fun BrowserMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// History & Bookmarks bottom sheets
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
// WebView factory
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
            // Keep the in-app browser sandboxed away from the device's local file system.
            allowFileAccess = false
            allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(this@apply, true)
            }
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
                Toast.makeText(context, "Downloading file…", Toast.LENGTH_SHORT).show()
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

            // Handles target="_blank" links / window.open() by spawning a genuine new tab.
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

            // Deny camera/mic permission requests by default for a safer general-purpose browser.
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
                    false // let the WebView load it normally, keeps everything in-app
                } else {
                    // Hand off non-web schemes (tel:, mailto:, intent:, market:, etc.) to the system.
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
