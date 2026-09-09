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
    data class ShortsPlayer(val slug: String) : Screen() // 📱 ৯:১৬ ফুল ভার্টিক্যাল শর্ট ড্রামা প্লেয়ার
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
    object Downloads : Screen() // 📥 লাইভ প্রোগ্রেস ও অফলাইন ডাউনলোড স্ক্রিন
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
        handleIncomingIntents(intent)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val isVip = authState.isVip

                val initialSlug = pendingNotificationSlug.value
                var currentScreen by remember {
                    mutableStateOf<Screen>(
                        if (!initialSlug.isNullOrBlank()) Screen.Player(initialSlug) else Screen.Home()
                    )
                }

                var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()

                // নোটিফিকেশন পারমিশন হ্যান্ডলার (Android 13+)
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

                    // প্লেয়ার, গ্যালারি, ডাউনলোড এবং ব্রাউজার স্ক্রিনে ইন্টারস্টিশিয়াল অ্যাড আটকানো
                    if (newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                        currentScreen is Screen.LocalGallery || currentScreen is Screen.LocalPlayer ||
                        newScreen is Screen.Browser || currentScreen is Screen.Browser ||
                        newScreen is Screen.ShortsPlayer || currentScreen is Screen.ShortsPlayer ||
                        newScreen is Screen.Player || currentScreen is Screen.Player ||
                        newScreen is Screen.Downloads || currentScreen is Screen.Downloads) {
                        currentScreen = newScreen
                    } else {
                        UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                        UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                            currentScreen = newScreen
                        }
                    }
                }

                // 🎯 স্মার্ট ড্রামা ওপেনার (Shorts নাকি 16:9 তা ডিটেক্ট করে)
                fun openDrama(slug: String) {
                    val allDramas = viewModel.homeUiState.value.popularDramas + viewModel.homeUiState.value.recentlyAdded
                    val targetDrama = allDramas.find { it.slug == slug || it.id == slug }

                    val isShorts = targetDrama?.isShorts == true ||
                            slug.contains("shorts", ignoreCase = true) ||
                            targetDrama?.categories?.any { it.contains("shorts", ignoreCase = true) } == true

                    if (isShorts) {
                        navigateTo(Screen.ShortsPlayer(slug))
                    } else {
                        navigateTo(Screen.Player(slug))
                    }
                }

                // নোটিফিকেশন ক্লিক অবজারভার
                LaunchedEffect(pendingNotificationSlug.value) {
                    val slug = pendingNotificationSlug.value
                    if (!slug.isNullOrBlank()) {
                        viewModel.loadDramaDetails(slug, context)
                        openDrama(slug)
                        pendingNotificationSlug.value = null
                    }
                }

                // লোকাল ভিডিও ফাইল ওপেন
                LaunchedEffect(pendingExternalMediaItem.value) {
                    val mediaItem = pendingExternalMediaItem.value
                    if (mediaItem != null) {
                        currentScreen = Screen.LocalPlayer(mediaItem)
                        pendingExternalMediaItem.value = null
                    }
                }

                // এক্সটার্নাল ব্রাউজার লিংক ওপেন
                LaunchedEffect(pendingBrowserUrl.value) {
                    val url = pendingBrowserUrl.value
                    if (!url.isNullOrBlank()) {
                        currentScreen = Screen.Browser(initialUrl = url)
                        pendingBrowserUrl.value = null
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadRemoteAdsConfig(context)
                }

                // 🔄 ব্যাক প্রেস লজিক
                BackHandler(enabled = currentScreen !is Screen.Home) {
                    when (currentScreen) {
                        is Screen.LocalPlayer -> currentScreen = Screen.LocalGallery
                        is Screen.LocalGallery -> navigateTo(Screen.Profile, BottomNavTab.ME)
                        is Screen.Browser -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Notification -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.ShortsPlayer -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Player -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Downloads -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Vip -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Profile -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Search -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        else -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                    }
                }

                // যে স্ক্রিনগুলোতে বটম নেভিগেশন বার লুকানো থাকবে
                val isFullscreenOrSubScreen = currentScreen is Screen.Player || 
                                              currentScreen is Screen.ShortsPlayer ||
                                              currentScreen is Screen.Browser || 
                                              currentScreen is Screen.Notification ||
                                              currentScreen is Screen.LocalGallery ||
                                              currentScreen is Screen.LocalPlayer ||
                                              currentScreen is Screen.Search

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
                                                BottomNavTab.SHORT_TV -> {
                                                    val firstShorts = viewModel.homeUiState.value.shortsContent.firstOrNull()
                                                        ?: viewModel.homeUiState.value.popularDramas.find { it.isShorts }
                                                        ?: viewModel.homeUiState.value.popularDramas.firstOrNull()
                                                    if (firstShorts != null) Screen.ShortsPlayer(firstShorts.slug) else Screen.Home()
                                                }
                                                BottomNavTab.PREMIUM -> Screen.Vip
                                                BottomNavTab.DOWNLOADS -> Screen.Downloads
                                                BottomNavTab.ME -> Screen.Profile
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
                                        onNavigateToPlayer = { slug -> openDrama(slug) },
                                        onNavigateToVip = { navigateTo(Screen.Vip, BottomNavTab.PREMIUM) },
                                        onNavigateToSearch = { navigateTo(Screen.Search) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) }
                                    )
                                }
                                is Screen.ShortsPlayer -> {
                                    ShortsPlayerScreen(
                                        slug = screen.slug,
                                        viewModel = viewModel,
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onNavigateToVip = { navigateTo(Screen.Vip, BottomNavTab.PREMIUM) }
                                    )
                                }
                                is Screen.Player -> {
                                    PlayerScreen(
                                        slug = screen.slug,
                                        viewModel = viewModel,
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onNavigateToVip = { navigateTo(Screen.Vip, BottomNavTab.PREMIUM) },
                                        onRelatedDramaClick = { newSlug -> openDrama(newSlug) },
                                        onNavigateToDownloads = { navigateTo(Screen.Downloads, BottomNavTab.DOWNLOADS) } // 🎯 ডাউনলোড পেজ ওপেন
                                    )
                                }
                                is Screen.Search -> {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { slug -> openDrama(slug) }
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
                                        onNavigateToPlayer = { slug -> openDrama(slug) }
                                    )
                                }
                                is Screen.Profile -> {
                                    ProfileScreen(
                                        viewModel = viewModel,
                                        onNavigateToVip = { navigateTo(Screen.Vip, BottomNavTab.PREMIUM) },
                                        onNavigateToWatchlist = { navigateTo(Screen.Watchlist) },
                                        onNavigateToBrowser = { navigateTo(Screen.Browser()) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) },
                                        onNavigateToLocalGallery = { navigateTo(Screen.LocalGallery) }
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
                                        onDramaClick = { dramaSlug -> openDrama(dramaSlug) }
                                    )
                                }
                                is Screen.LocalGallery -> {
                                    LocalGalleryScreen(
                                        onBackClick = { navigateTo(Screen.Profile, BottomNavTab.ME) },
                                        onVideoClick = { video -> currentScreen = Screen.LocalPlayer(video) }
                                    )
                                }
                                is Screen.LocalPlayer -> {
                                    LocalPlayerScreen(
                                        videoItem = screen.videoItem,
                                        onBackClick = { currentScreen = Screen.LocalGallery }
                                    )
                                }
                                is Screen.Downloads -> {
                                    // 📥 ডাউনলোড স্ক্রিন (লাইভ প্রোগ্রেস ও অফলাইন গ্যালারি)
                                    DownloadsScreen(
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onPlayDownloadedVideo = { localVideoItem ->
                                            // 🎯 অ্যাপের নিজস্ব প্লেয়ারেই অফলাইনে ভিডিও চালু করবে
                                            currentScreen = Screen.LocalPlayer(localVideoItem)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // সোশ্যাল বার অ্যাড
                    if (currentScreen !is Screen.LocalGallery && 
                        currentScreen !is Screen.LocalPlayer && 
                        currentScreen !is Screen.Browser && 
                        currentScreen !is Screen.ShortsPlayer) {
                        SocialBarAdOverlay(
                            isVip = isVip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isFullscreenOrSubScreen) 0.dp else 60.dp)
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

    private fun extractCleanSlug(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var str = input.trim()

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

        if (str.startsWith("http://", ignoreCase = true) || 
            str.startsWith("https://", ignoreCase = true) || 
            str.startsWith("playdramaflix://", ignoreCase = true) ||
            str.startsWith("dramaflix://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(str) }.getOrNull()
            val querySlug = uri?.getQueryParameter("slug") ?: uri?.getQueryParameter("id")
            if (!querySlug.isNullOrBlank()) {
                return querySlug.trim()
            }
            str = uri?.path ?: ""
        }

        str = str.trim('/')
            .removePrefix("watch/")
            .removePrefix("drama/")
            .removePrefix("series/")
            .removePrefix("content/")
            .removePrefix("shorts/")
            .removePrefix("video/")
            .removePrefix("movie/")
            .removePrefix("post/")
            .trim('/')

        return str.takeIf { 
            it.isNotBlank() && 
            !it.contains("://") && 
            !it.equals("home", ignoreCase = true) && 
            !it.equals("index.php", ignoreCase = true) &&
            !it.equals("index.html", ignoreCase = true)
        }
    }

    private fun handleIncomingIntents(intent: Intent?) {
        if (intent == null) return

        val isCustomUpdate = intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false)
        val isFcmUpdate = intent.getStringExtra("type") == "app_update" ||
                          intent.getStringExtra("click_action") == "OPEN_APP_UPDATE" ||
                          intent.action == "OPEN_APP_UPDATE"

        if (isCustomUpdate || isFcmUpdate) {
            viewModel.checkAppVersion(forceShow = true)
            return
        }

        var foundSlug: String? = null
        val dataUri: Uri? = intent.data
        if (dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "playdramaflix" || scheme == "dramaflix") {
                foundSlug = extractCleanSlug(dataUri.path)
            } else if (scheme == "http" || scheme == "https") {
                foundSlug = extractCleanSlug(dataUri.toString())
            }
        }

        if (foundSlug.isNullOrBlank()) {
            val extras = intent.extras
            if (extras != null) {
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

        if (!foundSlug.isNullOrBlank()) {
            Log.d("FCM_ROUTER", "✓ Target Drama Slug Detected: $foundSlug")
            viewModel.loadDramaDetails(foundSlug, applicationContext)
            pendingNotificationSlug.value = foundSlug
            return
        }

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
