package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.local.AppDatabase
import com.example.data.model.LocalVideoItem
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.InAppBrowserDialog
import com.example.ui.components.SocialBarAdOverlay
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.DramaFlixTheme
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.ui.viewmodel.DramaFlixViewModelFactory
import com.example.util.WelcomeNotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: DramaFlixViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val apiService = ApiClient.apiService
        val repository = PlayDramaFlixRepository(applicationContext, apiService, database)
        DramaFlixViewModelFactory(repository)
    }

    private val pendingNotificationSlug = mutableStateOf<String?>(null)
    private val pendingExternalMediaItem = mutableStateOf<LocalVideoItem?>(null)
    private val pendingBrowserUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
        } catch (_: Exception) {}

        UnifiedAdManager.init(this)

        // ১. নোটিফিকেশন বা ডিপ লিংক থেকে ড্রামা স্লাগ এক্সট্রাক্ট করা
        handleIncomingIntents(intent)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val isVip = authState.isVip

                // 🚀 কোল্ড স্টার্ট: নোটিফিকেশনে ট্যাপ করলে সরাসরি নির্দিষ্ট ড্রামা প্লেয়ারে চলে যাবে
                val initialSlug = pendingNotificationSlug.value
                var currentScreen by remember {
                    mutableStateOf<Screen>(
                        if (!initialSlug.isNullOrBlank()) Screen.Player(initialSlug) else Screen.Home()
                    )
                }

                var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()

                // 🔔 নোটিফিকেশন পারমিশন হ্যান্ডলার (Android 13+)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        WelcomeNotificationHelper.sendWelcomeNotification(context)
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            WelcomeNotificationHelper.sendWelcomeNotification(context)
                        }
                    } else {
                        WelcomeNotificationHelper.sendWelcomeNotification(context)
                    }
                }

                fun navigateTo(newScreen: Screen, tab: BottomNavTab? = null) {
                    if (tab != null) {
                        selectedTab = tab
                    }

                    // 🔒 প্লেয়ার, লোকাল গ্যালারি ও ব্রাউজার স্ক্রিনে কোনো ইন্টারস্টিশিয়াল অ্যাড থাকবে না
                    if (newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                        currentScreen is Screen.LocalGallery || currentScreen is Screen.LocalPlayer ||
                        newScreen is Screen.Browser || currentScreen is Screen.Browser) {
                        currentScreen = newScreen
                    } else {
                        UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                        UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                            currentScreen = newScreen
                        }
                    }
                }

                // 🎬 ৩. নোটিফিকেশন ক্লিক অবজারভার (ব্যাকগ্রাউন্ড বা রানিং অবস্থায় ট্যাপ করলে সাথে সাথে প্লেয়ারে নিয়ে যাবে)
                LaunchedEffect(pendingNotificationSlug.value) {
                    val slug = pendingNotificationSlug.value
                    if (!slug.isNullOrBlank()) {
                        viewModel.loadDramaDetails(slug, context)
                        currentScreen = Screen.Player(slug)
                        pendingNotificationSlug.value = null
                    }
                }

                // 🎬 লোকাল ভিডিও ফাইল ওপেন হ্যান্ডলার
                LaunchedEffect(pendingExternalMediaItem.value) {
                    val mediaItem = pendingExternalMediaItem.value
                    if (mediaItem != null) {
                        currentScreen = Screen.LocalPlayer(mediaItem)
                        pendingExternalMediaItem.value = null
                    }
                }

                // 🌐 এক্সটার্নাল ব্রাউজার লিংক হ্যান্ডলার
                LaunchedEffect(pendingBrowserUrl.value) {
                    val url = pendingBrowserUrl.value
                    if (!url.isNullOrBlank()) {
                        currentScreen = Screen.Browser(initialUrl = url)
                        selectedTab = BottomNavTab.BROWSER
                        pendingBrowserUrl.value = null
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadRemoteAdsConfig(context)
                }

                BackHandler(enabled = currentScreen !is Screen.Home) {
                    when (currentScreen) {
                        is Screen.LocalPlayer -> currentScreen = Screen.LocalGallery
                        is Screen.LocalGallery -> navigateTo(Screen.Profile, BottomNavTab.PROFILE)
                        is Screen.Browser -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Notification -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        else -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                    }
                }

                val isFullscreenOrSubScreen = currentScreen is Screen.Player || 
                                              currentScreen is Screen.Browser || 
                                              currentScreen is Screen.Notification ||
                                              currentScreen is Screen.LocalGallery ||
                                              currentScreen is Screen.LocalPlayer

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundDark),
                        bottomBar = {
                            if (!isFullscreenOrSubScreen) {
                                PlayDramaFlixBottomNav(
                                    selectedTab = selectedTab,
                                    onTabSelected = { tab ->
                                        if (selectedTab != tab) {
                                            val newScreen = when (tab) {
                                                BottomNavTab.HOME -> Screen.Home()
                                                BottomNavTab.BROWSER -> Screen.Browser()
                                                BottomNavTab.FILES -> Screen.LocalGallery
                                                BottomNavTab.WATCHLIST -> Screen.Watchlist
                                                BottomNavTab.PROFILE -> Screen.Profile
                                                else -> Screen.Home()
                                            }
                                            navigateTo(newScreen, tab)
                                        }
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    bottom = if (isFullscreenOrSubScreen) 0.dp else innerPadding.calculateBottomPadding()
                                )
                        ) {
                            when (val screen = currentScreen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { slug -> navigateTo(Screen.Player(slug)) },
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onNavigateToSearch = { navigateTo(Screen.Search) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) }
                                    )
                                }
                                is Screen.Player -> {
                                    PlayerScreen(
                                        slug = screen.slug,
                                        viewModel = viewModel,
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onRelatedDramaClick = { newSlug -> navigateTo(Screen.Player(newSlug)) }
                                    )
                                }
                                is Screen.Search -> {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { slug -> navigateTo(Screen.Player(slug)) }
                                    )
                                }
                                is Screen.Vip -> {
                                    VipScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navigateTo(Screen.Home(), BottomNavTab.HOME) }
                                    )
                                }
                                is Screen.Watchlist -> {
                                    WatchlistScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { slug -> navigateTo(Screen.Player(slug)) }
                                    )
                                }
                                is Screen.Profile -> {
                                    ProfileScreen(
                                        viewModel = viewModel,
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onNavigateToWatchlist = { navigateTo(Screen.Watchlist, BottomNavTab.WATCHLIST) },
                                        onNavigateToBrowser = { navigateTo(Screen.Browser(), BottomNavTab.BROWSER) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) },
                                        onNavigateToLocalGallery = { navigateTo(Screen.LocalGallery, BottomNavTab.FILES) }
                                    )
                                }
                                is Screen.Browser -> {
                                    BrowserScreen(
                                        initialUrl = screen.initialUrl,
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) }
                                    )
                                }
                                is Screen.Notification -> {
                                    NotificationScreen(
                                        viewModel = viewModel,
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onDramaClick = { dramaSlug -> navigateTo(Screen.Player(dramaSlug)) }
                                    )
                                }
                                is Screen.LocalGallery -> {
                                    LocalGalleryScreen(
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onVideoClick = { video -> currentScreen = Screen.LocalPlayer(video) }
                                    )
                                }
                                is Screen.LocalPlayer -> {
                                    LocalPlayerScreen(
                                        videoItem = screen.videoItem,
                                        onBackClick = { currentScreen = Screen.LocalGallery }
                                    )
                                }
                            }
                        }
                    }

                    if (currentScreen !is Screen.LocalGallery && currentScreen !is Screen.LocalPlayer && currentScreen !is Screen.Browser) {
                        SocialBarAdOverlay(
                            isVip = isVip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (currentScreen is Screen.Player || currentScreen is Screen.Notification) 0.dp else 56.dp)
                        )
                    }
                }

                if (authState.showAuthDialog) {
                    AuthBottomSheetDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showAuthDialog(false) }
                    )
                }

                if (updateState.showDialog && updateState.updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateState.updateInfo!!,
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }

                inAppBrowserRequest?.let { req ->
                    InAppBrowserDialog(
                        url = req.url,
                        title = req.title,
                        verificationSeconds = req.verificationSeconds,
                        onVerificationComplete = req.onVerified,
                        onDismiss = { UnifiedAdManager.closeInAppBrowser() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntents(intent)
    }

    // 🧹 যেকোনো URL (e.g. https://www.playdramaflix.com/{slug}), JSON বা কাস্টম স্কিম থেকে স্লাগ আলাদা করার নির্ভুল পার্সার
    private fun extractCleanSlug(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var str = input.trim()

        // ১. যদি ডেটা JSON স্ট্রিং আকারে আসে (e.g. {"slug": "..."})
        if (str.startsWith("{") && str.endsWith("}")) {
            try {
                val json = JSONObject(str)
                str = json.optString("slug").takeIf { it.isNotBlank() }
                    ?: json.optString("post_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("target_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("url").takeIf { it.isNotBlank() }
                    ?: json.optString("link").takeIf { it.isNotBlank() }
                    ?: str
            } catch (_: Exception) {}
        }

        // ২. যদি সম্পূর্ণ ওয়েবসাইট ইউআরএল বা ডিপ-লিংক হয়
        if (str.startsWith("http://", ignoreCase = true) || 
            str.startsWith("https://", ignoreCase = true) || 
            str.startsWith("playdramaflix://", ignoreCase = true) ||
            str.startsWith("dramaflix://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(str) }.getOrNull()
            
            // কুয়েরি প্যারামিটার চেক (?slug=...)
            val querySlug = uri?.getQueryParameter("slug") ?: uri?.getQueryParameter("id")
            if (!querySlug.isNullOrBlank()) {
                return querySlug.trim()
            }
            str = uri?.path ?: ""
        }

        // ৩. পাথ প্রিফিক্স ক্লিন করা (যেমন: /watch/boss-and-the-sweet-wife... অথবা সরাসরি /boss-and-the-sweet-wife...)
        str = str.trim('/')
            .removePrefix("watch/")
            .removePrefix("drama/")
            .removePrefix("series/")
            .removePrefix("content/")
            .removePrefix("video/")
            .removePrefix("movie/")
            .removePrefix("post/")
            .trim('/')

        // ৪. ড্রামা স্লাগ ভ্যালিডেশন
        return str.takeIf { 
            it.isNotBlank() && 
            !it.contains("://") && 
            !it.equals("home", ignoreCase = true) && 
            !it.equals("index.php", ignoreCase = true) &&
            !it.equals("index.html", ignoreCase = true)
        }
    }

    // 🌟 সকল প্রকার নোটিফিকেশন, ডিপ লিংক ও এক্সটার্নাল ফাইল পার্সার
    private fun handleIncomingIntents(intent: Intent?) {
        if (intent == null) return

        // ১. অ্যাপ আপডেট নোটিফিকেশন চেক
        val isCustomUpdate = intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false)
        val isFcmUpdate = intent.getStringExtra("type") == "app_update" ||
                          intent.getStringExtra("click_action") == "OPEN_APP_UPDATE" ||
                          intent.action == "OPEN_APP_UPDATE"

        if (isCustomUpdate || isFcmUpdate) {
            viewModel.checkAppVersion(forceShow = true)
            return
        }

        // ২. 🎯 নোটিফিকেশন ও ডিপ-লিংক থেকে ড্রামা স্লাগ এক্সট্রাক্ট করা
        var foundSlug: String? = null

        // ক) Intent Data URI থেকে চেক করা (e.g. playdramaflix://watch/... অথবা https://www.playdramaflix.com/{slug})
        val dataUri: Uri? = intent.data
        if (dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "playdramaflix" || scheme == "dramaflix") {
                foundSlug = extractCleanSlug(dataUri.path)
            } else if (scheme == "http" || scheme == "https") {
                foundSlug = extractCleanSlug(dataUri.toString())
            }
        }

        // খ) সরাসরি ইন্টেন্ট এক্সট্রাস (FCM Extras) থেকে চেক করা
        if (foundSlug.isNullOrBlank()) {
            val extras = intent.extras
            if (extras != null) {
                // সরাসরি সব কী-তে লুপ চালিয়ে ড্রামা স্লাগ খুঁজে বের করা
                for (key in extras.keySet()) {
                    val value = extras.get(key)?.toString()
                    val clean = extractCleanSlug(value)
                    if (!clean.isNullOrBlank()) {
                        foundSlug = clean
                        break
                    }
                }
            }
        }

        // 🎬 ড্রামা স্লাগ পাওয়া গেলে সাথে সাথে লোড ও প্লেয়ার টার্গেটে পাঠানো
        if (!foundSlug.isNullOrBlank()) {
            Log.d("FCM_ROUTER", "✓ Target Drama Slug Detected: $foundSlug")
            viewModel.loadDramaDetails(foundSlug, applicationContext)
            pendingNotificationSlug.value = foundSlug
            return
        }

        // ৩. যদি ড্রামা লিংক না হয়ে সাধারণ কোনো ওয়েব লিংক হয়, তবে ইন-অ্যাপ ব্রাউজারে যাবে
        val action = intent.action
        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "http" || scheme == "https") {
                val urlString = dataUri.toString()
                val isDirectMediaFile = urlString.endsWith(".mp4", true) ||
                        urlString.endsWith(".mkv", true) ||
                        urlString.endsWith(".mp3", true)

                if (!isDirectMediaFile) {
                    pendingBrowserUrl.value = urlString
                    return
                }
            }
        }

        // ৪. বাহির থেকে শেয়ার করা লোকাল ফাইল ওপেন হ্যান্ডলার (ক্র্যাশ-প্রুফ সেফটি ব্লক)
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val mediaUri: Uri? = if (action == Intent.ACTION_VIEW) {
                intent.data
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri
            }

            if (mediaUri != null && (mediaUri.scheme == "content" || mediaUri.scheme == "file")) {
                var fileName = "External Media"
                var fileSize = 0L
                var mimeType: String = intent.type ?: "video/*"

                try {
                    val resolvedType = contentResolver.getType(mediaUri)
                    if (!resolvedType.isNullOrBlank()) {
                        mimeType = resolvedType
                    }
                    contentResolver.query(mediaUri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                            if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (_: Exception) {
                    fileName = mediaUri.lastPathSegment ?: "External Media"
                }

                val item = LocalVideoItem(
                    id = mediaUri.hashCode().toLong(),
                    title = fileName,
                    displayName = fileName,
                    durationMs = 0L,
                    sizeBytes = fileSize,
                    path = mediaUri.path ?: "",
                    contentUriString = mediaUri.toString(),
                    folderName = "External",
                    bucketId = "external_media",
                    dateAdded = System.currentTimeMillis() / 1000,
                    mimeType = mimeType
                )

                pendingExternalMediaItem.value = item
            }
        }
    }
}
