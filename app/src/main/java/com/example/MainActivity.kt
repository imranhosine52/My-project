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
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.shorts.ShortsPlayerScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.DramaFlixTheme
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.ui.viewmodel.DramaFlixViewModelFactory
import com.example.util.WelcomeNotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

// 🎯 শর্ট ড্রামার পাথ ট্র্যাকিং হেলপার অবজেক্ট
object ShortTvNavHelper {
    var activeSubTab: String? = null
}

sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    data class ShortsPlayer(
        val slug: String,
        val sourceSubTab: String? = null // 👈 যে ট্যাব থেকে ওপেন করা হয়েছে
    ) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
    object Downloads : Screen()
    object CommunityChat : Screen() // 💬 লাইভ গ্লোবাল চ্যাট স্ক্রিন
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

                    if (newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                        currentScreen is Screen.LocalGallery || currentScreen is Screen.LocalPlayer ||
                        newScreen is Screen.Browser || currentScreen is Screen.Browser ||
                        newScreen is Screen.ShortsPlayer || currentScreen is Screen.ShortsPlayer ||
                        newScreen is Screen.Player || currentScreen is Screen.Player ||
                        newScreen is Screen.Downloads || currentScreen is Screen.Downloads ||
                        newScreen is Screen.CommunityChat || currentScreen is Screen.CommunityChat) {
                        currentScreen = newScreen
                    } else {
                        UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                        UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                            currentScreen = newScreen
                        }
                    }
                }

                // 🎯 পাথসহ ড্রামা ওপেন করার স্মার্ট হ্যান্ডলার
                fun openDrama(rawSlug: String) {
                    val slug = rawSlug.substringBefore("###subTab=")
                    val sourceSubTab = if (rawSlug.contains("###subTab=")) {
                        rawSlug.substringAfter("###subTab=").takeIf { it.isNotBlank() }
                    } else null

                    ShortTvNavHelper.activeSubTab = sourceSubTab

                    val home = viewModel.homeUiState.value
                    val allDramas = home.popularDramas + home.recentlyAdded + home.shortsContent + home.trendingDramas
                    val targetDrama = allDramas.find { it.slug == slug || it.id == slug }

                    val isShorts = targetDrama?.isShorts == true ||
                            slug.contains("shorts", ignoreCase = true) ||
                            targetDrama?.categories?.any { it.contains("shorts", ignoreCase = true) } == true

                    if (isShorts) {
                        navigateTo(
                            Screen.ShortsPlayer(slug = slug, sourceSubTab = sourceSubTab),
                            BottomNavTab.SHORT_TV
                        )
                    } else {
                        navigateTo(Screen.Player(slug))
                    }
                }

                LaunchedEffect(pendingNotificationSlug.value) {
                    val slug = pendingNotificationSlug.value
                    if (!slug.isNullOrBlank()) {
                        viewModel.loadDramaDetails(slug, context)
                        openDrama(slug)
                        pendingNotificationSlug.value = null
                    }
                }

                LaunchedEffect(pendingExternalMediaItem.value) {
                    val mediaItem = pendingExternalMediaItem.value
                    if (mediaItem != null) {
                        currentScreen = Screen.LocalPlayer(mediaItem)
                        pendingExternalMediaItem.value = null
                    }
                }

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

                // 🎯 পাথ অনুযায়ী ব্যাক বাটন হ্যান্ডলার (হায়ারার্কিক্যাল ব্যাক নেভিগেশন)
                BackHandler(enabled = currentScreen !is Screen.Home) {
                    when (val screen = currentScreen) {
                        is Screen.LocalPlayer -> currentScreen = Screen.LocalGallery
                        is Screen.LocalGallery -> navigateTo(Screen.Profile, BottomNavTab.ME)
                        is Screen.Browser -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Notification -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.CommunityChat -> navigateTo(Screen.Profile, BottomNavTab.ME) // 💬 চ্যাট থেকে প্রোফাইলে ব্যাক
                        is Screen.ShortsPlayer -> {
                            if (!screen.sourceSubTab.isNullOrBlank()) {
                                ShortTvNavHelper.activeSubTab = screen.sourceSubTab
                                navigateTo(Screen.Home(category = "Short TV"), BottomNavTab.SHORT_TV)
                            } else {
                                ShortTvNavHelper.activeSubTab = null
                                navigateTo(Screen.Home(category = "Short TV"), BottomNavTab.SHORT_TV)
                            }
                        }
                        is Screen.Player -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Downloads -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Vip -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Profile -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        is Screen.Search -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                        else -> navigateTo(Screen.Home(), BottomNavTab.HOME)
                    }
                }

                val isFullscreenOrSubScreen = currentScreen is Screen.Player || 
                                              currentScreen is Screen.ShortsPlayer ||
                                              currentScreen is Screen.Browser || 
                                              currentScreen is Screen.Notification ||
                                              currentScreen is Screen.LocalGallery ||
                                              currentScreen is Screen.LocalPlayer ||
                                              currentScreen is Screen.Search ||
                                              currentScreen is Screen.CommunityChat

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
                                                BottomNavTab.HOME -> Screen.Home(category = "Home")
                                                BottomNavTab.SHORT_TV -> {
                                                    ShortTvNavHelper.activeSubTab = null
                                                    Screen.Home(category = "Short TV")
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
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (val screen = currentScreen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        initialCategory = screen.category,
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
                                        onBackClick = {
                                            if (!screen.sourceSubTab.isNullOrBlank()) {
                                                ShortTvNavHelper.activeSubTab = screen.sourceSubTab
                                                navigateTo(Screen.Home(category = "Short TV"), BottomNavTab.SHORT_TV)
                                            } else {
                                                ShortTvNavHelper.activeSubTab = null
                                                navigateTo(Screen.Home(category = "Short TV"), BottomNavTab.SHORT_TV)
                                            }
                                        },
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
                                        onNavigateToDownloads = { navigateTo(Screen.Downloads, BottomNavTab.DOWNLOADS) }
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
                                        onNavigateToLocalGallery = { navigateTo(Screen.LocalGallery) },
                                        onNavigateToCommunityChat = { navigateTo(Screen.CommunityChat) } // 👈 চ্যাটে প্রবেশের অ্যাকশন যুক্ত করা হলো
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
                                    DownloadsScreen(
                                        onBackClick = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                                        onPlayDownloadedVideo = { localVideoItem ->
                                            currentScreen = Screen.LocalPlayer(localVideoItem)
                                        }
                                    )
                                }
                                is Screen.CommunityChat -> {
                                    CommunityChatScreen(
                                        viewModel = viewModel,
                                        onBackClick = { navigateTo(Screen.Profile, BottomNavTab.ME) } // 👈 ব্যাক করলে প্রোফাইলে ফিরবে
                                    )
                                }
                            }
                        }
                    }

                    // সোশ্যাল বার অ্যাড (নির্দিষ্ট স্ক্রিন ব্যতীত প্রদর্শন)
                    if (currentScreen !is Screen.LocalGallery && 
                        currentScreen !is Screen.LocalPlayer && 
                        currentScreen !is Screen.Browser && 
                        currentScreen !is Screen.ShortsPlayer &&
                        currentScreen !is Screen.Player &&
                        currentScreen !is Screen.CommunityChat) {
                        SocialBarAdOverlay(
                            isVip = isVip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isFullscreenOrSubScreen) 0.dp else 64.dp)
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
