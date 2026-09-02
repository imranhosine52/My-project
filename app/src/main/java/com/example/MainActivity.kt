package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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

sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    
    // 🎬 লোকাল গ্যালারি ও অফলাইন প্লেয়ার
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

    private var pendingNotificationSlug = mutableStateOf<String?>(null)
    private var pendingExternalMediaItem = mutableStateOf<LocalVideoItem?>(null)
    private var pendingBrowserUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        UnifiedAdManager.init(this)

        handleIncomingIntents(intent)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val isVip = authState.isVip
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home()) }
                var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()

                // 🔔 নোটিফিকেশন পারমিশন ও ওয়েলকাম নোটিফিকেশন হ্যান্ডলার
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

                    // 🔒 লোকাল গ্যালারি, ব্রাউজার ও প্লেয়ার স্ক্রিনে কোনো প্রকার অ্যাড থাকবে না (100% Ad-Free)
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

                // 🔔 নোটিফিকেশন থেকে ড্রামা পেজে ওপেন হলে
                LaunchedEffect(pendingNotificationSlug.value) {
                    pendingNotificationSlug.value?.let { slug ->
                        if (slug.isNotBlank()) {
                            navigateTo(Screen.Player(slug))
                            pendingNotificationSlug.value = null
                        }
                    }
                }

                // 🎬 বাহির থেকে ভিডিও/অডিও ওপেন বা শেয়ার করলে সরাসরি প্লেয়ারে ওপেন হবে
                LaunchedEffect(pendingExternalMediaItem.value) {
                    pendingExternalMediaItem.value?.let { mediaItem ->
                        currentScreen = Screen.LocalPlayer(mediaItem)
                        pendingExternalMediaItem.value = null
                    }
                }

                // 🌐 বাহিরের যেকোনো লিংকে ক্লিক করলে সরাসরি ইন-অ্যাপ ব্রাউজারে সেই লিংক ওপেন হবে
                LaunchedEffect(pendingBrowserUrl.value) {
                    pendingBrowserUrl.value?.let { url ->
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

    // 🔔 নোটিফিকেশন থেকে আসা আপডেট এবং ড্রামা স্লাগ হ্যান্ডলার
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val isCustomUpdate = intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false)
        val isFcmUpdate = intent.getStringExtra("type") == "app_update" ||
                          intent.getStringExtra("click_action") == "OPEN_APP_UPDATE" ||
                          intent.action == "OPEN_APP_UPDATE"

        if (isCustomUpdate || isFcmUpdate) {
            viewModel.checkAppVersion(forceShow = true)
            return
        }

        val slug = intent.getStringExtra("EXTRA_NOTIFICATION_SLUG")
            ?: intent.getStringExtra("slug")
            ?: intent.getStringExtra("content_slug")
            ?: intent.data?.lastPathSegment

        if (!slug.isNullOrBlank()) {
            pendingNotificationSlug.value = slug
        }
    }

    // 🌟 সকল ইনকামিং ইন্টেন্ট (Notification, Web Link, Media File) হ্যান্ডলার
    private fun handleIncomingIntents(intent: Intent?) {
        if (intent == null) return

        handleNotificationIntent(intent)

        val action = intent.action
        val dataUri: Uri? = intent.data

        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase()
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

        val mediaUri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri
            }
            else -> null
        }

        if (mediaUri != null) {
            var fileName = "External Media"
            var fileSize = 0L
            val mimeType = intent.type ?: contentResolver.getType(mediaUri) ?: "video/*"

            try {
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
