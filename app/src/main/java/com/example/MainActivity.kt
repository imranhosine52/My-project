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
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.local.AppDatabase
import com.example.data.model.CreatorPageDto
import com.example.data.model.LocalVideoItem
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.InAppBrowserDialog
import com.example.ui.components.SocialBarAdOverlay
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*
import com.example.ui.screens.chat.CommunityChatScreen
import com.example.ui.screens.chat.InboxScreen
import com.example.ui.screens.chat.PersonalChatScreen
import com.example.ui.screens.chat.components.FloatingCommunityChatWidget
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.profile.CreatorStudioScreen
import com.example.ui.screens.profile.PageApplicationDialog
import com.example.ui.screens.profile.PublicCreatorProfileScreen
import com.example.ui.screens.reels.CreateReelUploadScreen
import com.example.ui.screens.reels.ReelDetailsPublishScreen
import com.example.ui.screens.reels.ReelsFeedScreen
import com.example.ui.screens.reels.ReelsSearchScreen
import com.example.ui.screens.reels.SuggestedAccountsScreen
import com.example.ui.screens.reels.VideoTrimmerScreen
import com.example.ui.screens.shorts.ShortsPlayerScreen
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.DramaFlixTheme
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.ui.viewmodel.DramaFlixViewModelFactory
import com.example.ui.viewmodel.ReelsViewModel
import com.example.ui.viewmodel.ReelsViewModelFactory
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
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
    object Downloads : Screen()
    object CommunityChat : Screen()
    object Inbox : Screen()
    data class PersonalChat(
        val otherUserId: String,
        val otherUserName: String,
        val otherUserAvatar: String?
    ) : Screen()
    object Reels : Screen()
    data class ReelsSearch(val initialQuery: String = "") : Screen()
    data class VideoTrimmer(val videoUri: Uri) : Screen()
    data class ReelDetailsPublish(val trimmedVideoPath: String, val isMuted: Boolean) : Screen()
    data class CreatorStudio(val page: CreatorPageDto) : Screen()
    data class PublicCreatorProfile(val pageId: Int) : Screen()
    object SuggestedAccounts : Screen()
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
    private val pendingBrowserUrl = mutableStateOf<String?>(null)
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

            val chatPrefs = getSharedPreferences("play_drama_flix_chat_group_prefs", Context.MODE_PRIVATE)
            val isMuted = chatPrefs.getBoolean("is_group_muted", false)
            if (!isMuted) {
                FirebaseMessaging.getInstance().subscribeToTopic("community_group_notifications")
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("community_group_notifications")
            }
        } catch (_: Exception) {}

        UnifiedAdManager.init(this)
        handleIncomingIntents(intent)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

                val initialSlug = pendingNotificationSlug.value
                val initialIsShorts = pendingNotificationIsShorts.value

                var currentScreen by remember {
                    mutableStateOf<Screen>(
                        if (pendingOpenCommunityChat.value) Screen.CommunityChat
                        else if (pendingOpenVipScreen.value) Screen.Vip
                        else if (pendingReelId.value != null) Screen.Reels
                        else if (pendingPageId.value != null) Screen.PublicCreatorProfile(pendingPageId.value!!)
                        else if (!initialSlug.isNullOrBlank()) {
                            if (initialIsShorts || initialSlug.contains("shorts", ignoreCase = true)) {
                                Screen.ShortsPlayer(initialSlug)
                            } else {
                                Screen.Player(initialSlug)
                            }
                        } 
                        else Screen.Home()
                    )
                }

                val navigationBackStack = remember { mutableStateListOf<Screen>() }

                fun resolveTabForScreen(screen: Screen): BottomNavTab {
                    return when (screen) {
                        is Screen.Home -> BottomNavTab.HOME
                        is Screen.ShortsPlayer -> BottomNavTab.SHORT_TV
                        is Screen.Reels, is Screen.ReelsSearch, is Screen.VideoTrimmer,
                        is Screen.ReelDetailsPublish, is Screen.PublicCreatorProfile -> BottomNavTab.REELS
                        is Screen.Downloads -> BottomNavTab.DOWNLOADS
                        is Screen.Profile, is Screen.CreatorStudio -> BottomNavTab.ME
                        else -> BottomNavTab.HOME
                    }
                }

                var selectedTab by remember {
                    mutableStateOf(
                        if (pendingReelId.value != null) BottomNavTab.REELS
                        else if (!initialSlug.isNullOrBlank() && (initialIsShorts || initialSlug.contains("shorts", ignoreCase = true))) BottomNavTab.SHORT_TV
                        else BottomNavTab.HOME
                    )
                }

                fun handleBackNavigation() {
                    if (navigationBackStack.isNotEmpty()) {
                        val previousScreen = navigationBackStack.removeAt(navigationBackStack.lastIndex)
                        currentScreen = previousScreen
                        selectedTab = resolveTabForScreen(previousScreen)
                    } else if (currentScreen !is Screen.Home) {
                        currentScreen = Screen.Home()
                        selectedTab = BottomNavTab.HOME
                    } else {
                        finish()
                    }
                }

                fun navigateTo(newScreen: Screen, tab: BottomNavTab? = null) {
                    if (currentScreen == newScreen) return

                    navigationBackStack.add(currentScreen)

                    if (tab != null) {
                        selectedTab = tab
                    } else {
                        selectedTab = resolveTabForScreen(newScreen)
                    }

                    if (newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                        newScreen is Screen.Browser || currentScreen is Screen.Browser ||
                        newScreen is Screen.ShortsPlayer || currentScreen is Screen.ShortsPlayer ||
                        newScreen is Screen.Player || currentScreen is Screen.Player ||
                        newScreen is Screen.Downloads || currentScreen is Screen.Downloads ||
                        newScreen is Screen.CommunityChat || currentScreen is Screen.CommunityChat ||
                        newScreen is Screen.Inbox || currentScreen is Screen.Inbox ||
                        newScreen is Screen.PersonalChat || currentScreen is Screen.PersonalChat ||
                        newScreen is Screen.Reels || currentScreen is Screen.Reels ||
                        newScreen is Screen.ReelsSearch || currentScreen is Screen.ReelsSearch ||
                        newScreen is Screen.VideoTrimmer || currentScreen is Screen.VideoTrimmer ||
                        newScreen is Screen.ReelDetailsPublish || currentScreen is Screen.ReelDetailsPublish ||
                        newScreen is Screen.CreatorStudio || currentScreen is Screen.CreatorStudio ||
                        newScreen is Screen.PublicCreatorProfile || currentScreen is Screen.PublicCreatorProfile ||
                        newScreen is Screen.SuggestedAccounts || currentScreen is Screen.SuggestedAccounts ||
                        newScreen is Screen.Vip || currentScreen is Screen.Vip) {
                        currentScreen = newScreen
                    } else {
                        UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                        UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                            currentScreen = newScreen
                        }
                    }
                }

                fun openDramaDirect(rawSlug: String, forceShorts: Boolean = false) {
                    val slug = rawSlug.substringBefore("###subTab=").trim()
                    val sourceSubTab = if (rawSlug.contains("###subTab=")) {
                        rawSlug.substringAfter("###subTab=").takeIf { it.isNotBlank() }
                    } else null

                    ShortTvNavHelper.activeSubTab = sourceSubTab

                    val home = viewModel.homeUiState.value
                    val allDramas = home.popularDramas + home.recentlyAdded + home.shortsContent + home.trendingDramas
                    val targetDrama = allDramas.find { it.slug == slug || it.id == slug }

                    val isShorts = forceShorts ||
                            targetDrama?.isShorts == true ||
                            slug.contains("shorts", ignoreCase = true) ||
                            targetDrama?.categories?.any { it.contains("shorts", ignoreCase = true) } == true

                    if (isShorts) {
                        navigateTo(Screen.ShortsPlayer(slug = slug, sourceSubTab = sourceSubTab), BottomNavTab.SHORT_TV)
                    } else {
                        navigateTo(Screen.Player(slug))
                    }
                }

                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()
                var showPageApplyDialog by remember { mutableStateOf(false) }

                val reelVideoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        navigateTo(Screen.VideoTrimmer(videoUri = uri))
                    }
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
                        is Screen.ReelsSearch -> "Reels Search Screen"
                        is Screen.VideoTrimmer -> "Video Trimmer Screen"
                        is Screen.ReelDetailsPublish -> "Reel Publishing Studio"
                        is Screen.CreatorStudio -> "Creator Studio Screen"
                        is Screen.PublicCreatorProfile -> "Public Creator Profile"
                        is Screen.SuggestedAccounts -> "Suggested Accounts (Find Friends)"
                        is Screen.Inbox -> "Inbox Screen"
                        is Screen.PersonalChat -> "Personal DM Chat"
                        is Screen.Vip -> "VIP Pricing Screen"
                        is Screen.Watchlist -> "My Watchlist Screen"
                        is Screen.Profile -> "Profile Screen"
                        is Screen.Downloads -> "Downloads Screen"
                        is Screen.Search -> "Search Screen"
                        is Screen.Notification -> "Notifications Screen"
                        is Screen.CommunityChat -> "Community Live Chat"
                        is Screen.Browser -> "In-App Browser"
                        is Screen.LocalGallery -> "Local Media Gallery"
                        is Screen.LocalPlayer -> "Playing Local: ${screen.videoItem.title}"
                    }
                    if (screenLabel != null) {
                        AppAnalyticsTracker.trackScreen(context, screenLabel, numericUserId)
                    }
                }

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

                LaunchedEffect(pendingReelId.value) {
                    val rId = pendingReelId.value
                    if (rId != null) {
                        navigateTo(Screen.Reels, BottomNavTab.REELS)
                        reelsViewModel.loadFeed("for_you")
                        pendingReelId.value = null
                    }
                }

                LaunchedEffect(pendingPageId.value) {
                    val pId = pendingPageId.value
                    if (pId != null) {
                        navigateTo(Screen.PublicCreatorProfile(pId))
                        pendingPageId.value = null
                    }
                }

                LaunchedEffect(pendingOpenCommunityChat.value) {
                    if (pendingOpenCommunityChat.value) {
                        navigateTo(Screen.CommunityChat)
                        pendingOpenCommunityChat.value = false
                    }
                }

                LaunchedEffect(pendingOpenVipScreen.value) {
                    if (pendingOpenVipScreen.value) {
                        navigateTo(Screen.Vip)
                        pendingOpenVipScreen.value = false
                    }
                }

                LaunchedEffect(pendingNotificationSlug.value) {
                    val slug = pendingNotificationSlug.value
                    val isShorts = pendingNotificationIsShorts.value
                    if (!slug.isNullOrBlank()) {
                        viewModel.loadDramaDetails(slug, context)
                        openDramaDirect(slug, isShorts)
                        pendingNotificationSlug.value = null
                        pendingNotificationIsShorts.value = false
                    }
                }

                LaunchedEffect(pendingExternalMediaItem.value) {
                    val mediaItem = pendingExternalMediaItem.value
                    if (mediaItem != null) {
                        navigateTo(Screen.LocalPlayer(mediaItem))
                        pendingExternalMediaItem.value = null
                    }
                }

                LaunchedEffect(pendingBrowserUrl.value) {
                    val url = pendingBrowserUrl.value
                    if (!url.isNullOrBlank()) {
                        navigateTo(Screen.Browser(initialUrl = url))
                        pendingBrowserUrl.value = null
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadRemoteAdsConfig(context)
                }

                BackHandler(enabled = navigationBackStack.isNotEmpty() || currentScreen !is Screen.Home) {
                    handleBackNavigation()
                }

                // 🎯 ইনস্টাগ্রাম স্ক্রিনশটের মতো: Screen.Reels-এ বটম ন্যাভিগেশন বার ভিজিবল থাকবে
                val shouldHideBottomNav = (currentScreen is Screen.Player && isLandscape) ||
                                          currentScreen is Screen.ShortsPlayer ||
                                          currentScreen is Screen.Browser || 
                                          currentScreen is Screen.Notification ||
                                          currentScreen is Screen.LocalGallery ||
                                          currentScreen is Screen.LocalPlayer ||
                                          currentScreen is Screen.Search ||
                                          currentScreen is Screen.CommunityChat ||
                                          currentScreen is Screen.PersonalChat ||
                                          currentScreen is Screen.Vip ||
                                          currentScreen is Screen.VideoTrimmer ||
                                          currentScreen is Screen.ReelDetailsPublish ||
                                          currentScreen is Screen.ReelsSearch ||
                                          currentScreen is Screen.PublicCreatorProfile ||
                                          currentScreen is Screen.SuggestedAccounts

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
                            if (!shouldHideBottomNav) {
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
                                                BottomNavTab.REELS -> Screen.Reels
                                                BottomNavTab.DOWNLOADS -> Screen.Downloads
                                                BottomNavTab.ME -> {
                                                    if (activeProfileMode == "creator_page" && myCreatorPage != null) {
                                                        Screen.CreatorStudio(myCreatorPage!!)
                                                    } else {
                                                        Screen.Profile
                                                    }
                                                }
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
                                        onNavigateToPlayer = { slug -> openDramaDirect(slug) },
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onNavigateToSearch = { navigateTo(Screen.Search) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) }
                                    )
                                }
                                is Screen.ShortsPlayer -> {
                                    ShortsPlayerScreen(
                                        slug = screen.slug,
                                        viewModel = viewModel,
                                        onBackClick = { handleBackNavigation() },
                                        onNavigateToVip = { navigateTo(Screen.Vip) }
                                    )
                                }
                                is Screen.Player -> {
                                    PlayerScreen(
                                        slug = screen.slug,
                                        viewModel = viewModel,
                                        onBackClick = { handleBackNavigation() },
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onRelatedDramaClick = { newSlug -> openDramaDirect(newSlug) },
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
                                        onOpenCreateReel = { 
                                            reelVideoPickerLauncher.launch("video/*") 
                                        },
                                        onOpenPageProfile = { pageId -> 
                                            navigateTo(Screen.PublicCreatorProfile(pageId))
                                        },
                                        onNavigateToSearch = { initialTag ->
                                            navigateTo(Screen.ReelsSearch(initialQuery = initialTag))
                                        },
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
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
                                        onReelClick = { reel -> navigateTo(Screen.Reels) },
                                        onOpenDirectMessage = { creatorId, creatorName -> 
                                            navigateTo(
                                                Screen.PersonalChat(
                                                    otherUserId = creatorId,
                                                    otherUserName = creatorName,
                                                    otherUserAvatar = null
                                                )
                                            )
                                        }
                                    )
                                }
                                is Screen.SuggestedAccounts -> {
                                    SuggestedAccountsScreen(
                                        reelsViewModel = reelsViewModel,
                                        onBackClick = { handleBackNavigation() },
                                        onOpenProfile = { userId ->
                                            navigateTo(Screen.PublicCreatorProfile(userId))
                                        },
                                        onReelClick = { reel ->
                                            navigateTo(Screen.Reels)
                                        }
                                    )
                                }
                                is Screen.Inbox -> {
                                    InboxScreen(
                                        currentUserId = authState.userProfile?.id ?: "guest",
                                        currentUserAvatar = authState.userProfile?.avatar,
                                        onOpenPersonalChat = { otherId, otherName, otherAvatar ->
                                            navigateTo(
                                                Screen.PersonalChat(
                                                    otherUserId = otherId,
                                                    otherUserName = otherName,
                                                    otherUserAvatar = otherAvatar
                                                )
                                            )
                                        },
                                        onOpenSearch = { navigateTo(Screen.SuggestedAccounts) },
                                        onCreateStoryOrReel = { reelVideoPickerLauncher.launch("video/*") }
                                    )
                                }
                                is Screen.PersonalChat -> {
                                    PersonalChatScreen(
                                        myUserId = authState.userProfile?.id ?: "guest",
                                        myUserName = authState.userProfile?.displayName ?: "User",
                                        myUserAvatar = authState.userProfile?.avatar,
                                        recipientUserId = screen.otherUserId,
                                        recipientUserName = screen.otherUserName,
                                        recipientUserAvatar = screen.otherUserAvatar,
                                        onBackClick = { handleBackNavigation() }
                                    )
                                }
                                is Screen.ReelsSearch -> {
                                    ReelsSearchScreen(
                                        viewModel = reelsViewModel,
                                        onBackClick = { handleBackNavigation() },
                                        onReelClick = { selectedReel ->
                                            navigateTo(Screen.Reels)
                                        }
                                    )
                                }
                                is Screen.VideoTrimmer -> {
                                    VideoTrimmerScreen(
                                        videoUri = screen.videoUri,
                                        onBackClick = { handleBackNavigation() },
                                        onNextClick = { trimmedPath, isMuted ->
                                            navigateTo(
                                                Screen.ReelDetailsPublish(
                                                    trimmedVideoPath = trimmedPath,
                                                    isMuted = isMuted
                                                )
                                            )
                                        }
                                    )
                                }
                                is Screen.ReelDetailsPublish -> {
                                    val uploadStateNow by reelsViewModel.uploadState.collectAsStateWithLifecycle()
                                    val currentUserIdInt = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 1

                                    ReelDetailsPublishScreen(
                                        trimmedVideoPath = screen.trimmedVideoPath,
                                        creatorPage = uploadStateNow.creatorPage,
                                        userId = currentUserIdInt,
                                        onBackClick = { handleBackNavigation() },
                                        onPublishSuccessExit = {
                                            navigateTo(Screen.Reels, BottomNavTab.REELS)
                                            reelsViewModel.loadFeed(tab = "for_you")
                                        }
                                    )
                                }
                                is Screen.CreatorStudio -> {
                                    CreatorStudioScreen(
                                        page = screen.page,
                                        reelsViewModel = reelsViewModel,
                                        onSwitchToPersonalProfile = {
                                            activeProfileMode = "personal"
                                            profileModePrefs.edit().putString("active_profile_mode", "personal").apply()
                                            navigateTo(Screen.Profile, BottomNavTab.ME)
                                            Toast.makeText(context, "Switched to Personal Profile", Toast.LENGTH_SHORT).show()
                                        },
                                        onBackClick = { handleBackNavigation() },
                                        onReelClick = { reel -> navigateTo(Screen.Reels) },
                                        onCreateReelClick = { reelVideoPickerLauncher.launch("video/*") }
                                    )
                                }
                                is Screen.Search -> {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = { slug -> openDramaDirect(slug) }
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
                                        onNavigateToPlayer = { slug -> openDramaDirect(slug) }
                                    )
                                }
                                is Screen.Profile -> {
                                    ProfileScreen(
                                        viewModel = viewModel,
                                        onNavigateToVip = { navigateTo(Screen.Vip) },
                                        onNavigateToWatchlist = { navigateTo(Screen.Watchlist) },
                                        onNavigateToBrowser = { navigateTo(Screen.Browser()) },
                                        onNavigateToNotification = { navigateTo(Screen.Notification) },
                                        onNavigateToLocalGallery = { navigateTo(Screen.LocalGallery) },
                                        onNavigateToCommunityChat = { navigateTo(Screen.CommunityChat) },
                                        onSwitchToCreatorStudio = { creatorPage ->
                                            activeProfileMode = "creator_page"
                                            profileModePrefs.edit().putString("active_profile_mode", "creator_page").apply()
                                            navigateTo(Screen.CreatorStudio(creatorPage))
                                            Toast.makeText(context, "Switched to ${creatorPage.pageName}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                is Screen.Browser -> {
                                    BrowserScreen(
                                        initialUrl = screen.initialUrl,
                                        onBackClick = { handleBackNavigation() }
                                    )
                                }
                                is Screen.Notification -> {
                                    NotificationScreen(
                                        viewModel = viewModel,
                                        onBackClick = { handleBackNavigation() },
                                        onDramaClick = { dramaSlug -> openDramaDirect(dramaSlug) }
                                    )
                                }
                                is Screen.LocalGallery -> {
                                    LocalGalleryScreen(
                                        onBackClick = { handleBackNavigation() },
                                        onVideoClick = { video -> navigateTo(Screen.LocalPlayer(video)) }
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
                                            navigateTo(Screen.LocalPlayer(localVideoItem))
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

                    if (currentScreen !is Screen.Player && 
                        currentScreen !is Screen.ShortsPlayer && 
                        currentScreen !is Screen.Reels &&
                        currentScreen !is Screen.ReelsSearch &&
                        currentScreen !is Screen.VideoTrimmer &&
                        currentScreen !is Screen.ReelDetailsPublish &&
                        currentScreen !is Screen.CreatorStudio &&
                        currentScreen !is Screen.PublicCreatorProfile &&
                        currentScreen !is Screen.SuggestedAccounts &&
                        currentScreen !is Screen.PersonalChat &&
                        currentScreen !is Screen.Inbox &&
                        currentScreen !is Screen.CommunityChat) {
                        FloatingCommunityChatWidget(
                            currentUserId = authState.userProfile?.id ?: "guest",
                            currentUserName = authState.userProfile?.displayName ?: "User",
                            currentUserEmail = authState.userProfile?.email,
                            currentUserAvatar = authState.userProfile?.avatar,
                            isVip = isVip,
                            onOpenFullScreenChat = {
                                navigateTo(Screen.CommunityChat)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 46.dp, end = 12.dp)
                        )
                    }

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

                if (authState.showAuthDialog) {
                    AuthBottomSheetDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showAuthDialog(false) }
                    )
                }

                if (showPageApplyDialog) {
                    PageApplicationDialog(
                        viewModel = viewModel,
                        onDismiss = { showPageApplyDialog = false },
                        onSuccess = {
                            reelsViewModel.checkMyCreatorPage()
                        }
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
