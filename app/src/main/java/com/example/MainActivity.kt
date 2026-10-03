package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.UnifiedAdManager
import com.example.data.local.AppDatabase
import com.example.data.model.CreatorPageDto
import com.example.data.model.LocalVideoItem
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.SocialBarAdOverlay
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*
import com.example.ui.screens.chat.CommunityChatScreen
import com.example.ui.screens.chat.components.FloatingCommunityChatWidget
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.profile.CreatorWebDashboardScreen
import com.example.ui.screens.profile.PublicCreatorProfileScreen
import com.example.ui.screens.reels.*
import com.example.ui.screens.shorts.ShortsPlayerScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.DramaFlixTheme
import com.example.ui.viewmodel.*
import com.example.util.AppAnalyticsTracker
import com.example.util.ReelsCachePreloadManager
import com.example.util.WelcomeNotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import org.json.JSONObject

object ShortTvNavHelper {
    var activeSubTab: String? = null
}

sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    data class ShortsPlayer(
        val slug: String,
        val sourceSubTab: String? = null
    ) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
    object Downloads : Screen()
    object CommunityChat : Screen()
    object Reels : Screen()
    data class ReelsSearch(val initialQuery: String = "") : Screen()
    data class ReelsSearchResult(val query: String) : Screen()
    data class HashtagDetail(val hashtag: String) : Screen()
    data class VideoTrimmer(val videoUri: Uri, val isSeries: Boolean = false) : Screen()
    data class ReelDetailsPublish(val trimmedVideoPath: String, val isMuted: Boolean) : Screen()
    data class SeriesEpisodePublish(val trimmedVideoPath: String, val isMuted: Boolean) : Screen()
    data class CreatorWebDashboard(val pageId: Int, val studioUrl: String) : Screen()
    data class PublicCreatorProfile(val pageId: Int) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: DramaFlixViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val apiService = ApiClient.apiService
        val repository = PlayDramaFlixRepository(applicationContext, apiService, database)
        DramaFlixViewModelFactory(repository)
    }

    private val reelsViewModel: ReelsViewModel by viewModels {
        val reelsRepository = ReelsRepository(applicationContext)
        ReelsViewModelFactory(reelsRepository)
    }

    private val pendingNotificationSlug = mutableStateOf<String?>(null)
    private val pendingNotificationIsShorts = mutableStateOf(false)
    private val pendingExternalMediaItem = mutableStateOf<LocalVideoItem?>(null)
    private val pendingOpenCommunityChat = mutableStateOf(false)
    private val pendingOpenVipScreen = mutableStateOf(false)
    private val pendingReelId = mutableStateOf<Int?>(null)
    private val pendingPageId = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ReelsCachePreloadManager.initCache(applicationContext)

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            FirebaseMessaging.getInstance().subscribeToTopic("all")
        } catch (_: Exception) {}

        UnifiedAdManager.init(this)
        handleIncomingIntents(intent)

        setContent {
            DramaFlixTheme {
                MainAppContent(
                    viewModel = viewModel,
                    reelsViewModel = reelsViewModel,
                    pendingNotificationSlug = pendingNotificationSlug.value,
                    pendingNotificationIsShorts = pendingNotificationIsShorts.value,
                    pendingExternalMediaItem = pendingExternalMediaItem.value,
                    pendingOpenCommunityChat = pendingOpenCommunityChat.value,
                    pendingOpenVipScreen = pendingOpenVipScreen.value,
                    pendingReelId = pendingReelId.value,
                    pendingPageId = pendingPageId.value,
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun extractCleanSlug(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var str = input.trim()

        if (str.startsWith("{") && str.endsWith("}")) {
            try {
                val json = JSONObject(str)
                str = json.optString("slug").takeIf { it.isNotBlank() }
                    ?: json.optString("content_slug").takeIf { it.isNotBlank() }
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
            str.startsWith("dramaflix://", ignoreCase = true)
        ) {
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

        if (str.contains(" ")) {
            str = str.replace(Regex("\\s+"), "-")
        }

        return str.takeIf {
            it.isNotBlank() &&
            !it.contains("://") &&
            !it.startsWith("topics/") &&
            !it.equals("high", ignoreCase = true) &&
            !it.equals("default", ignoreCase = true) &&
            !it.equals("normal", ignoreCase = true) &&
            !it.equals("home", ignoreCase = true) &&
            !it.equals("index.php", ignoreCase = true) &&
            !it.equals("index.html", ignoreCase = true)
        }
    }

    private fun handleIncomingIntents(intent: Intent?) {
        if (intent == null) return

        val extras = intent.extras
        val dataUri: Uri? = intent.data
        val dataUriString = dataUri?.toString() ?: ""
        val action = intent.action ?: ""

        if (dataUriString.contains("/reel/") || dataUriString.startsWith("playdramaflix://reel")) {
            val rId = dataUri?.lastPathSegment?.toIntOrNull()
            if (rId != null) {
                pendingReelId.value = rId
                return
            }
        }

        if (dataUriString.contains("/page/") || dataUriString.startsWith("playdramaflix://page")) {
            val pId = dataUri?.lastPathSegment?.toIntOrNull()
            if (pId != null) {
                pendingPageId.value = pId
                return
            }
        }

        val isChatReply = intent.getBooleanExtra("EXTRA_OPEN_COMMUNITY_CHAT", false) ||
                intent.getStringExtra("type") == "chat_reply" ||
                intent.getStringExtra("type") == "community_chat" ||
                intent.getStringExtra("click_action") == "OPEN_COMMUNITY_CHAT" ||
                action == "OPEN_COMMUNITY_CHAT" ||
                dataUriString.contains("community_chat", ignoreCase = true)

        if (isChatReply) {
            pendingOpenCommunityChat.value = true
            return
        }

        val isVipAction = intent.getBooleanExtra("EXTRA_OPEN_VIP", false) ||
                intent.getStringExtra("type") == "vip_promo" ||
                intent.getStringExtra("type") == "vip_status_update" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_CHECKOUT" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_PRICING" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_RENEW" ||
                action == "OPEN_VIP_CHECKOUT" ||
                action == "OPEN_VIP_PRICING" ||
                action == "OPEN_VIP_RENEW" ||
                action == "OPEN_VIP_ACTIVE"

        if (isVipAction) {
            pendingOpenVipScreen.value = true
            return
        }

        val isCustomUpdate = intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false) ||
                intent.getStringExtra("type") == "app_update" ||
                intent.getStringExtra("click_action") == "OPEN_APP_UPDATE" ||
                action == "OPEN_APP_UPDATE"

        if (isCustomUpdate) {
            viewModel.checkAppVersion(forceShow = true)
            return
        }

        var foundSlug: String? = null

        val isShortsFromExtra = intent.getBooleanExtra("IS_SHORTS", false) ||
                extras?.get("is_shorts")?.toString() == "1" ||
                extras?.get("is_shorts")?.toString() == "true" ||
                extras?.get("type")?.toString()?.equals("shorts", ignoreCase = true) == true ||
                extras?.get("content_type")?.toString()?.equals("shorts", ignoreCase = true) == true ||
                extras?.get("category")?.toString()?.contains("shorts", ignoreCase = true) == true

        pendingNotificationIsShorts.value = isShortsFromExtra

        if (extras != null) {
            val targetKeys = listOf(
                "slug", "content_slug", "post_slug", "target_slug",
                "drama_slug", "EXTRA_NOTIFICATION_SLUG", "id", "content_id", "drama_id"
            )
            for (key in targetKeys) {
                val value = extras.get(key)?.toString()
                val clean = extractCleanSlug(value)
                if (!clean.isNullOrBlank()) {
                    foundSlug = clean
                    break
                }
            }

            if (foundSlug.isNullOrBlank()) {
                val urlKeys = listOf("url", "link", "watch_url", "target_url")
                for (key in urlKeys) {
                    val value = extras.get(key)?.toString()
                    val clean = extractCleanSlug(value)
                    if (!clean.isNullOrBlank()) {
                        foundSlug = clean
                        break
                    }
                }
            }

            if (foundSlug.isNullOrBlank() && extras.containsKey("data")) {
                val dataString = extras.get("data")?.toString()
                foundSlug = extractCleanSlug(dataString)
            }

            if (foundSlug.isNullOrBlank() && extras.containsKey("title")) {
                val titleVal = extras.get("title")?.toString()
                if (!titleVal.isNullOrBlank()) {
                    foundSlug = titleVal.trim().lowercase().replace(Regex("[^a-zA-Z0-9\\s-]"), "").replace(Regex("\\s+"), "-")
                }
            }
        }

        if (foundSlug.isNullOrBlank() && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "playdramaflix" || scheme == "dramaflix") {
                foundSlug = extractCleanSlug(dataUri.path) ?: extractCleanSlug(dataUri.host)
            } else if (scheme == "http" || scheme == "https") {
                foundSlug = extractCleanSlug(dataUri.toString())
            }
        }

        if (!foundSlug.isNullOrBlank()) {
            viewModel.loadDramaDetails(foundSlug, applicationContext)
            pendingNotificationSlug.value = foundSlug
            return
        }

        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "http" || scheme == "https") {
                val urlString = dataUri.toString()
                val isDirectMediaFile = urlString.endsWith(".mp4", true) ||
                        urlString.endsWith(".mkv", true) ||
                        urlString.endsWith(".mp3", true)

                if (!isDirectMediaFile) {
                    UnifiedAdManager.openChromeCustomTab(applicationContext, urlString)
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

/**
 * 🌟 Compose Root Composable (Separated to eliminate local function scope issues)
 */
@Composable
private fun MainAppContent(
    viewModel: DramaFlixViewModel,
    reelsViewModel: ReelsViewModel,
    pendingNotificationSlug: String?,
    pendingNotificationIsShorts: Boolean,
    pendingExternalMediaItem: LocalVideoItem?,
    pendingOpenCommunityChat: Boolean,
    pendingOpenVipScreen: Boolean,
    pendingReelId: Int?,
    pendingPageId: Int?,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reelsRepository = remember { ReelsRepository(context) }

    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val isVip = authState.isVip

    val profileModePrefs = remember {
        context.getSharedPreferences("user_profile_mode_prefs", Context.MODE_PRIVATE)
    }

    var activeProfileMode by remember {
        mutableStateOf(profileModePrefs.getString("active_profile_mode", "personal") ?: "personal")
    }

    val uploadState by reelsViewModel.uploadState.collectAsStateWithLifecycle()
    val myCreatorPage = uploadState.creatorPage

    var pendingUploadMode by remember { mutableStateOf("reel") }

    var currentScreen by remember {
        mutableStateOf<Screen>(
            if (pendingOpenCommunityChat) Screen.CommunityChat
            else if (pendingOpenVipScreen) Screen.Vip
            else if (pendingReelId != null) Screen.Reels
            else if (pendingPageId != null) Screen.PublicCreatorProfile(pendingPageId)
            else if (pendingExternalMediaItem != null) Screen.LocalPlayer(pendingExternalMediaItem)
            else if (!pendingNotificationSlug.isNullOrBlank()) {
                if (pendingNotificationIsShorts || pendingNotificationSlug.contains("shorts", ignoreCase = true)) {
                    Screen.ShortsPlayer(pendingNotificationSlug)
                } else {
                    Screen.Player(pendingNotificationSlug)
                }
            } else Screen.Home()
        )
    }

    val navigationBackStack = remember { mutableStateListOf<Screen>() }

    val resolveTabForScreen: (Screen) -> BottomNavTab = { screen ->
        when (screen) {
            is Screen.Home -> BottomNavTab.HOME
            is Screen.ShortsPlayer -> BottomNavTab.SHORT_TV
            is Screen.Reels, is Screen.ReelsSearch, is Screen.ReelsSearchResult,
            is Screen.HashtagDetail, is Screen.VideoTrimmer, is Screen.ReelDetailsPublish,
            is Screen.SeriesEpisodePublish, is Screen.PublicCreatorProfile -> BottomNavTab.REELS
            is Screen.Downloads -> BottomNavTab.DOWNLOADS
            is Screen.Profile, is Screen.CreatorWebDashboard -> BottomNavTab.ME
            else -> BottomNavTab.HOME
        }
    }

    var selectedTab by remember {
        mutableStateOf(
            if (pendingReelId != null) BottomNavTab.REELS
            else if (!pendingNotificationSlug.isNullOrBlank() && (pendingNotificationIsShorts || pendingNotificationSlug.contains("shorts", ignoreCase = true))) BottomNavTab.SHORT_TV
            else BottomNavTab.HOME
        )
    }

    val handleBackNavigation: () -> Unit = {
        if (navigationBackStack.isNotEmpty()) {
            val previousScreen = navigationBackStack.removeAt(navigationBackStack.lastIndex)
            currentScreen = previousScreen
            selectedTab = resolveTabForScreen(previousScreen)
        } else if (currentScreen !is Screen.Home) {
            currentScreen = Screen.Home()
            selectedTab = BottomNavTab.HOME
        } else {
            onFinish()
        }
    }

    val navigateTo: (Screen, BottomNavTab?) -> Unit = { newScreen, tab ->
        if (currentScreen != newScreen) {
            navigationBackStack.add(currentScreen)
            selectedTab = tab ?: resolveTabForScreen(newScreen)

            val isExempted = newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                    newScreen is Screen.ShortsPlayer || currentScreen is Screen.ShortsPlayer ||
                    newScreen is Screen.Player || currentScreen is Screen.Player ||
                    newScreen is Screen.Downloads || currentScreen is Screen.Downloads ||
                    newScreen is Screen.CommunityChat || currentScreen is Screen.CommunityChat ||
                    newScreen is Screen.Reels || currentScreen is Screen.Reels ||
                    newScreen is Screen.ReelsSearch || currentScreen is Screen.ReelsSearch ||
                    newScreen is Screen.ReelsSearchResult || currentScreen is Screen.ReelsSearchResult ||
                    newScreen is Screen.HashtagDetail || currentScreen is Screen.HashtagDetail ||
                    newScreen is Screen.VideoTrimmer || currentScreen is Screen.VideoTrimmer ||
                    newScreen is Screen.ReelDetailsPublish || currentScreen is Screen.ReelDetailsPublish ||
                    newScreen is Screen.SeriesEpisodePublish || currentScreen is Screen.SeriesEpisodePublish ||
                    newScreen is Screen.CreatorWebDashboard || currentScreen is Screen.CreatorWebDashboard ||
                    newScreen is Screen.PublicCreatorProfile || currentScreen is Screen.PublicCreatorProfile ||
                    newScreen is Screen.Vip || currentScreen is Screen.Vip

            if (isExempted) {
                currentScreen = newScreen
            } else {
                UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                    currentScreen = newScreen
                }
            }
        }
    }

    val openDramaDirect: (String, Boolean) -> Unit = { rawSlug, forceShorts ->
        val slug = rawSlug.substringBefore("###subTab=").trim()
        val sourceSubTab = if (rawSlug.contains("###subTab=")) rawSlug.substringAfter("###subTab=") else null
        ShortTvNavHelper.activeSubTab = sourceSubTab

        val home = viewModel.homeUiState.value
        val allDramas = home.popularDramas + home.recentlyAdded + home.shortsContent + home.trendingDramas
        val targetDrama = allDramas.find { it.slug == slug || it.id == slug }

        val isShorts = forceShorts || targetDrama?.isShorts == true || slug.contains("shorts", ignoreCase = true)
        if (isShorts) {
            navigateTo(Screen.ShortsPlayer(slug, sourceSubTab), BottomNavTab.SHORT_TV)
        } else {
            navigateTo(Screen.Player(slug), null)
        }
    }

    val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()

    val reelVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            navigateTo(Screen.VideoTrimmer(videoUri = uri, isSeries = (pendingUploadMode == "series")), null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) WelcomeNotificationHelper.sendWelcomeNotification(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                WelcomeNotificationHelper.sendWelcomeNotification(context)
            }
        }
        viewModel.loadRemoteAdsConfig(context)
    }

    val numericUserId = remember(authState.userProfile) {
        authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
    }

    LaunchedEffect(currentScreen) {
        val screenLabel = when (val screen = currentScreen) {
            is Screen.Home -> null
            is Screen.Player -> null
            is Screen.ShortsPlayer -> null
            is Screen.Reels -> "Reels Feed Screen"
            is Screen.ReelsSearch -> "Reels Search Landing Page"
            is Screen.ReelsSearchResult -> "Reels Search Results: ${screen.query}"
            is Screen.HashtagDetail -> "Hashtag Detail: ${screen.hashtag}"
            is Screen.VideoTrimmer -> "Video Trimmer Screen"
            is Screen.ReelDetailsPublish -> "Reel Publishing Studio"
            is Screen.SeriesEpisodePublish -> "Series Episode Publishing Studio"
            is Screen.CreatorWebDashboard -> "Creator Web Analytics Dashboard: ${screen.pageId}"
            is Screen.PublicCreatorProfile -> "Public Creator Profile: ${screen.pageId}"
            is Screen.Vip -> "VIP Pricing Screen"
            is Screen.Watchlist -> "My Watchlist Screen"
            is Screen.Profile -> "Profile Screen"
            is Screen.Downloads -> "Downloads Screen"
            is Screen.Search -> "Search Screen"
            is Screen.Notification -> "Notifications Screen"
            is Screen.CommunityChat -> "Community Live Chat"
            is Screen.LocalGallery -> "Local Media Gallery"
            is Screen.LocalPlayer -> "Playing Local: ${screen.videoItem.title}"
        }
        if (screenLabel != null) {
            AppAnalyticsTracker.trackScreen(context, screenLabel, numericUserId)
        }
    }

    BackHandler(enabled = navigationBackStack.isNotEmpty() || currentScreen !is Screen.Home) {
        handleBackNavigation()
    }

    val shouldHideBottomNav = (currentScreen is Screen.Player && isLandscape) ||
            currentScreen is Screen.ShortsPlayer ||
            currentScreen is Screen.Notification ||
            currentScreen is Screen.LocalGallery ||
            currentScreen is Screen.LocalPlayer ||
            currentScreen is Screen.Search ||
            currentScreen is Screen.CommunityChat ||
            currentScreen is Screen.Vip ||
            currentScreen is Screen.VideoTrimmer ||
            currentScreen is Screen.ReelDetailsPublish ||
            currentScreen is Screen.SeriesEpisodePublish ||
            currentScreen is Screen.ReelsSearch ||
            currentScreen is Screen.ReelsSearchResult ||
            currentScreen is Screen.HashtagDetail ||
            currentScreen is Screen.PublicCreatorProfile ||
            currentScreen is Screen.CreatorWebDashboard ||
            currentScreen is Screen.Reels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(BackgroundDark),
            bottomBar = {
                if (!shouldHideBottomNav) {
                    PlayDramaFlixBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            if (selectedTab != tab) {
                                when (tab) {
                                    BottomNavTab.HOME -> navigateTo(Screen.Home(category = "Home"), tab)
                                    BottomNavTab.SHORT_TV -> {
                                        ShortTvNavHelper.activeSubTab = null
                                        navigateTo(Screen.Home(category = "Short TV"), tab)
                                    }
                                    BottomNavTab.REELS -> navigateTo(Screen.Reels, tab)
                                    BottomNavTab.DOWNLOADS -> navigateTo(Screen.Downloads, tab)
                                    BottomNavTab.ME -> {
                                        if (activeProfileMode == "creator_page" && myCreatorPage != null) {
                                            coroutineScope.launch {
                                                val tokenRes = reelsRepository.getCreatorStudioUrl(myCreatorPage.id)
                                                val studioUrl = tokenRes.getOrNull()
                                                    ?: "https://playdramaflix.com/creator-studio/dashboard.php?page_id=${myCreatorPage.id}"
                                                navigateTo(Screen.CreatorWebDashboard(myCreatorPage.id, studioUrl), tab)
                                            }
                                        } else {
                                            navigateTo(Screen.Profile, tab)
                                        }
                                    }
                                }
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
                            onNavigateToPlayer = { slug -> openDramaDirect(slug, false) },
                            onNavigateToVip = { navigateTo(Screen.Vip, null) },
                            onNavigateToSearch = { navigateTo(Screen.Search, null) },
                            onNavigateToNotification = { navigateTo(Screen.Notification, null) }
                        )
                    }
                    is Screen.ShortsPlayer -> {
                        ShortsPlayerScreen(
                            slug = screen.slug,
                            viewModel = viewModel,
                            onBackClick = { handleBackNavigation() },
                            onNavigateToVip = { navigateTo(Screen.Vip, null) }
                        )
                    }
                    is Screen.Player -> {
                        PlayerScreen(
                            slug = screen.slug,
                            viewModel = viewModel,
                            onBackClick = { handleBackNavigation() },
                            onNavigateToVip = { navigateTo(Screen.Vip, null) },
                            onRelatedDramaClick = { newSlug -> openDramaDirect(newSlug, false) },
                            onNavigateToDownloads = { navigateTo(Screen.Downloads, BottomNavTab.DOWNLOADS) }
                        )
                    }
                    is Screen.Reels -> {
                        ReelsFeedScreen(
                            viewModel = reelsViewModel,
                            isLoggedIn = authState.isLoggedIn,
                            currentUserName = authState.userProfile?.displayName ?: "User",
                            currentUserAvatar = authState.userProfile?.avatar,
                            onBackClick = { handleBackNavigation() },
                            onNavigateToHome = { navigateTo(Screen.Home(), BottomNavTab.HOME) },
                            onNavigateToDownloads = { navigateTo(Screen.Downloads, BottomNavTab.DOWNLOADS) },
                            onNavigateToProfile = {
                                if (activeProfileMode == "creator_page" && myCreatorPage != null) {
                                    coroutineScope.launch {
                                        val tokenRes = reelsRepository.getCreatorStudioUrl(myCreatorPage.id)
                                        val studioUrl = tokenRes.getOrNull()
                                            ?: "https://playdramaflix.com/creator-studio/dashboard.php?page_id=${myCreatorPage.id}"
                                        navigateTo(Screen.CreatorWebDashboard(myCreatorPage.id, studioUrl), BottomNavTab.ME)
                                    }
                                } else {
                                    navigateTo(Screen.Profile, BottomNavTab.ME)
                                }
                            },
                            onOpenCreateReel = { mode ->
                                val hasApprovedPage = uploadState.creatorPage?.isApproved == true
                                if (!hasApprovedPage) {
                                    val uid = authState.userProfile?.id?.filter { it.isDigit() } ?: "0"
                                    val applyUrl = "https://playdramaflix.com/app/creator/apply.php?user_id=$uid"
                                    UnifiedAdManager.openChromeCustomTab(context, applyUrl)
                                } else {
                                    pendingUploadMode = mode
                                    reelVideoPickerLauncher.launch("video/*")
                                }
                            },
                            onNavigateToPageApply = {
                                val uid = authState.userProfile?.id?.filter { it.isDigit() } ?: "0"
                                val applyUrl = "https://playdramaflix.com/app/creator/apply.php?user_id=$uid"
                                UnifiedAdManager.openChromeCustomTab(context, applyUrl)
                            },
                            onOpenPageProfile = { pageId ->
                                navigateTo(Screen.PublicCreatorProfile(pageId), null)
                            },
                            onNavigateToSearch = { initialTag ->
                                if (initialTag.startsWith("#")) {
                                    navigateTo(Screen.HashtagDetail(initialTag), null)
                                } else {
                                    navigateTo(Screen.ReelsSearch(initialQuery = initialTag), null)
                                }
                            },
                            onNavigateToVip = { navigateTo(Screen.Vip, null) },
                            onRequireLogin = { viewModel.showAuthDialog(true) }
                        )
                    }
                    is Screen.PublicCreatorProfile -> {
                        PublicCreatorProfileScreen(
                            pageId = screen.pageId,
                            reelsViewModel = reelsViewModel,
                            isLoggedIn = authState.isLoggedIn,
                            onRequireLogin = { viewModel.showAuthDialog(true) },
                            onBackClick = { handleBackNavigation() },
                            onReelClick = { navigateTo(Screen.Reels, BottomNavTab.REELS) }
                        )
                    }
                    is Screen.ReelsSearch -> {
                        ReelsSearchScreen(
                            initialQuery = screen.initialQuery,
                            onBackClick = { handleBackNavigation() },
                            onNavigateToResults = { query ->
                                navigateTo(Screen.ReelsSearchResult(query = query), null)
                            }
                        )
                    }
                    is Screen.ReelsSearchResult -> {
                        ReelsSearchResultScreen(
                            searchQuery = screen.query,
                            viewModel = reelsViewModel,
                            onBackClick = { handleBackNavigation() },
                            onSearchSubmit = { newQuery ->
                                navigateTo(Screen.ReelsSearchResult(query = newQuery), null)
                            },
                            onReelClick = { navigateTo(Screen.Reels, BottomNavTab.REELS) },
                            onOpenCreatorProfile = { pageId ->
                                navigateTo(Screen.PublicCreatorProfile(pageId), null)
                            },
                            onOpenHashtagExplorer = { tag ->
                                navigateTo(Screen.HashtagDetail(tag), null)
                            }
                        )
                    }
                    is Screen.HashtagDetail -> {
                        HashtagDetailScreen(
                            hashtag = screen.hashtag,
                            onBackClick = { handleBackNavigation() },
                            onReelClick = { navigateTo(Screen.Reels, BottomNavTab.REELS) }
                        )
                    }
                    is Screen.VideoTrimmer -> {
                        VideoTrimmerScreen(
                            videoUri = screen.videoUri,
                            isSeries = screen.isSeries,
                            onBackClick = { handleBackNavigation() },
                            onNextClick = { trimmedPath, isMuted ->
                                if (screen.isSeries || pendingUploadMode == "series") {
                                    navigateTo(
                                        Screen.SeriesEpisodePublish(
                                            trimmedVideoPath = trimmedPath,
                                            isMuted = isMuted
                                        ),
                                        null
                                    )
                                } else {
                                    navigateTo(
                                        Screen.ReelDetailsPublish(
                                            trimmedVideoPath = trimmedPath,
                                            isMuted = isMuted
                                        ),
                                        null
                                    )
                                }
                            }
                        )
                    }
                    is Screen.ReelDetailsPublish -> {
                        val currentUserIdInt = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                        ReelDetailsPublishScreen(
                            trimmedVideoPath = screen.trimmedVideoPath,
                            creatorPage = uploadState.creatorPage,
                            userId = currentUserIdInt,
                            onBackClick = { handleBackNavigation() },
                            onPublishSuccessExit = {
                                navigateTo(Screen.Reels, BottomNavTab.REELS)
                                reelsViewModel.loadFeed(tab = "for_you")
                            }
                        )
                    }
                    is Screen.SeriesEpisodePublish -> {
                        val currentUserIdInt = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                        SeriesEpisodePublishScreen(
                            trimmedVideoPath = screen.trimmedVideoPath,
                            creatorPage = uploadState.creatorPage,
                            userId = currentUserIdInt,
                            onBackClick = { handleBackNavigation() },
                            onPublishSuccessExit = {
                                navigateTo(Screen.Reels, BottomNavTab.REELS)
                                reelsViewModel.loadFeed(tab = "for_you")
                            }
                        )
                    }
                    is Screen.CreatorWebDashboard -> {
                        CreatorWebDashboardScreen(
                            pageId = screen.pageId,
                            studioUrl = screen.studioUrl,
                            onSwitchToPersonalProfile = {
                                activeProfileMode = "personal"
                                profileModePrefs.edit().putString("active_profile_mode", "personal").apply()
                                navigateTo(Screen.Profile, BottomNavTab.ME)
                                Toast.makeText(context, "Switched to Personal Profile", Toast.LENGTH_SHORT).show()
                            },
                            onBackClick = { handleBackNavigation() }
                        )
                    }
                    is Screen.Search -> {
                        SearchScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { slug -> openDramaDirect(slug, false) }
                        )
                    }
                    is Screen.Vip -> {
                        VipScreen(
                            viewModel = viewModel,
                            onNavigateBack = { handleBackNavigation() }
                        )
                    }
                    is Screen.Watchlist -> {
                        WatchlistScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = { slug -> openDramaDirect(slug, false) }
                        )
                    }
                    is Screen.Profile -> {
                        ProfileScreen(
                            viewModel = viewModel,
                            onNavigateToVip = { navigateTo(Screen.Vip, null) },
                            onNavigateToWatchlist = { navigateTo(Screen.Watchlist, null) },
                            onNavigateToNotification = { navigateTo(Screen.Notification, null) },
                            onNavigateToLocalGallery = { navigateTo(Screen.LocalGallery, null) },
                            onNavigateToCommunityChat = { navigateTo(Screen.CommunityChat, null) },
                            onSwitchToCreatorStudio = { creatorPage: CreatorPageDto ->
                                coroutineScope.launch {
                                    val tokenRes = reelsRepository.getCreatorStudioUrl(creatorPage.id)
                                    val studioUrl = tokenRes.getOrNull()
                                        ?: "https://playdramaflix.com/creator-studio/dashboard.php?page_id=${creatorPage.id}"

                                    activeProfileMode = "creator_page"
                                    profileModePrefs.edit().putString("active_profile_mode", "creator_page").apply()

                                    navigateTo(Screen.CreatorWebDashboard(creatorPage.id, studioUrl), BottomNavTab.ME)
                                    Toast.makeText(context, "Switched to ${creatorPage.pageName} Studio", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    is Screen.Notification -> {
                        NotificationScreen(
                            viewModel = viewModel,
                            onBackClick = { handleBackNavigation() },
                            onDramaClick = { dramaSlug -> openDramaDirect(dramaSlug, false) }
                        )
                    }
                    is Screen.LocalGallery -> {
                        LocalGalleryScreen(
                            onBackClick = { handleBackNavigation() },
                            onVideoClick = { video -> navigateTo(Screen.LocalPlayer(video), null) }
                        )
                    }
                    is Screen.LocalPlayer -> {
                        LocalPlayerScreen(
                            videoItem = screen.videoItem,
                            onBackClick = { handleBackNavigation() }
                        )
                    }
                    is Screen.Downloads -> {
                        DownloadsScreen(
                            onBackClick = { handleBackNavigation() },
                            onPlayDownloadedVideo = { localVideoItem ->
                                navigateTo(Screen.LocalPlayer(localVideoItem), null)
                            }
                        )
                    }
                    is Screen.CommunityChat -> {
                        CommunityChatScreen(
                            viewModel = viewModel,
                            onBackClick = { handleBackNavigation() }
                        )
                    }
                }
            }
        }

        // 💬 কমিউনিটি চ্যাট ফ্লোটিং উইজেট
        val shouldHideFloatingChat = currentScreen is Screen.Player ||
                currentScreen is Screen.ShortsPlayer ||
                currentScreen is Screen.Reels ||
                currentScreen is Screen.ReelsSearch ||
                currentScreen is Screen.ReelsSearchResult ||
                currentScreen is Screen.HashtagDetail ||
                currentScreen is Screen.VideoTrimmer ||
                currentScreen is Screen.ReelDetailsPublish ||
                currentScreen is Screen.SeriesEpisodePublish ||
                currentScreen is Screen.CreatorWebDashboard ||
                currentScreen is Screen.PublicCreatorProfile ||
                currentScreen is Screen.CommunityChat

        if (!shouldHideFloatingChat) {
            FloatingCommunityChatWidget(
                currentUserId = authState.userProfile?.id ?: "guest",
                currentUserName = authState.userProfile?.displayName ?: "User",
                currentUserEmail = authState.userProfile?.email,
                currentUserAvatar = authState.userProfile?.avatar,
                isVip = isVip,
                onOpenFullScreenChat = { navigateTo(Screen.CommunityChat, null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 46.dp, end = 12.dp)
            )
        }

        // 📢 সোশ্যাল বার অ্যাড
        if (!shouldHideBottomNav && currentScreen !is Screen.Player && currentScreen !is Screen.Reels && currentScreen !is Screen.PublicCreatorProfile) {
            SocialBarAdOverlay(
                isVip = isVip,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
            )
        }
    }

    // ডায়ালগসমূহ
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
}
