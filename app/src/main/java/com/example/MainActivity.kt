package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.UnifiedAdManager
import com.example.data.local.AppDatabase
import com.example.data.model.CreatorPageDto
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.data.repository.ReelsRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.GlobalOverlays
import com.example.ui.navigation.*
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.DramaFlixTheme
import com.example.ui.viewmodel.*
import com.example.util.AppAnalyticsTracker
import com.example.util.ReelsCachePreloadManager
import com.example.util.WelcomeNotificationHelper
import com.google.firebase.messaging.FirebaseMessaging

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ১. কোর ইনিশিয়ালাইজেশন
        ReelsCachePreloadManager.initCache(applicationContext)
        UnifiedAdManager.init(this)

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            FirebaseMessaging.getInstance().subscribeToTopic("all")
        } catch (_: Exception) {}

        // ২. ইনকামিং নোটিফিকেশন ও ডিপ-লিংক পার্সিং
        val initialIntentResult = IntentDeepLinkHandler.processIncomingIntent(this, intent)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()
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
                var showPageApplyDialog by remember { mutableStateOf(false) }

                // ৩. প্রাথমিক স্ক্রিন রেজলভার
                var currentScreen by remember {
                    mutableStateOf<Screen>(
                        when {
                            initialIntentResult.openCommunityChat -> Screen.CommunityChat
                            initialIntentResult.openVipScreen -> Screen.Vip
                            initialIntentResult.targetReelId != null -> Screen.Reels
                            initialIntentResult.targetPageId != null -> Screen.PublicCreatorProfile(initialIntentResult.targetPageId)
                            initialIntentResult.externalMediaItem != null -> Screen.LocalPlayer(initialIntentResult.externalMediaItem)
                            !initialIntentResult.browserUrl.isNullOrBlank() -> Screen.Browser(initialIntentResult.browserUrl)
                            !initialIntentResult.targetSlug.isNullOrBlank() -> {
                                if (initialIntentResult.isShorts || initialIntentResult.targetSlug.contains("shorts", ignoreCase = true)) {
                                    Screen.ShortsPlayer(initialIntentResult.targetSlug)
                                } else {
                                    Screen.Player(initialIntentResult.targetSlug)
                                }
                            }
                            else -> Screen.Home()
                        }
                    )
                }

                val navigationBackStack = remember { mutableStateListOf<Screen>() }

                var selectedTab by remember {
                    mutableStateOf(
                        when {
                            initialIntentResult.targetReelId != null -> BottomNavTab.REELS
                            initialIntentResult.isShorts -> BottomNavTab.SHORT_TV
                            else -> BottomNavTab.HOME
                        }
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
                    selectedTab = tab ?: resolveTabForScreen(newScreen)

                    // ফুলস্ক্রিন প্লেয়ার বা ট্রিমার ছাড়া অন্যান্য সময়ে ইন্টারস্টিশিয়াল অ্যাড দেখানো
                    val isExempted = newScreen is Screen.LocalGallery || newScreen is Screen.LocalPlayer ||
                            newScreen is Screen.Browser || currentScreen is Screen.Browser ||
                            newScreen is Screen.ShortsPlayer || currentScreen is Screen.ShortsPlayer ||
                            newScreen is Screen.Player || currentScreen is Screen.Player ||
                            newScreen is Screen.Downloads || currentScreen is Screen.Downloads ||
                            newScreen is Screen.CommunityChat || currentScreen is Screen.CommunityChat ||
                            newScreen is Screen.Inbox || currentScreen is Screen.Inbox ||
                            newScreen is Screen.PersonalChat || currentScreen is Screen.PersonalChat ||
                            newScreen is Screen.Reels || currentScreen is Screen.Reels ||
                            newScreen is Screen.VideoTrimmer || currentScreen is Screen.VideoTrimmer ||
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

                fun openDramaDirect(rawSlug: String, forceShorts: Boolean = false) {
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

                // ভিডিও পিকার লঞ্চার
                val reelVideoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        navigateTo(Screen.VideoTrimmer(videoUri = uri, isSeries = (pendingUploadMode == "series")), null)
                    }
                }

                // নোটিফিকেশন পারমিশন ও ওয়েলকাম নোটিফিকেশন
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

                // অ্যানালিটিক্স ট্র্যাকার
                val numericUserId = remember(authState.userProfile) {
                    authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull()
                }
                LaunchedEffect(currentScreen) {
                    getScreenAnalyticsLabel(currentScreen)?.let { label ->
                        AppAnalyticsTracker.trackScreen(context, label, numericUserId)
                    }
                }

                BackHandler(enabled = navigationBackStack.isNotEmpty() || currentScreen !is Screen.Home) {
                    handleBackNavigation()
                }

                val isBottomNavHidden = shouldHideBottomNav(currentScreen, isLandscape)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().background(BackgroundDark),
                        bottomBar = {
                            if (!isBottomNavHidden) {
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
                                                        Screen.CreatorStudio(myCreatorPage)
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
                        // ৪. মূল স্ক্রিন সুইচার (AppNavGraph)
                        AppNavGraph(
                            currentScreen = currentScreen,
                            viewModel = viewModel,
                            reelsViewModel = reelsViewModel,
                            authState = authState,
                            pendingUploadMode = pendingUploadMode,
                            onNavigateTo = { screen, tab -> navigateTo(screen, tab) },
                            onBackClick = { handleBackNavigation() },
                            openDramaDirect = { slug, forceShorts -> openDramaDirect(slug, forceShorts) },
                            onSetPendingUploadMode = { mode -> pendingUploadMode = mode },
                            onLaunchVideoPicker = { type -> reelVideoPickerLauncher.launch(type) },
                            onSwitchToCreatorStudio = { creatorPage ->
                                activeProfileMode = "creator_page"
                                profileModePrefs.edit().putString("active_profile_mode", "creator_page").apply()
                                navigateTo(Screen.CreatorStudio(creatorPage), BottomNavTab.ME)
                                Toast.makeText(context, "Switched to ${creatorPage.pageName}", Toast.LENGTH_SHORT).show()
                            },
                            onSwitchToPersonalProfile = {
                                activeProfileMode = "personal"
                                profileModePrefs.edit().putString("active_profile_mode", "personal").apply()
                                navigateTo(Screen.Profile, BottomNavTab.ME)
                                Toast.makeText(context, "Switched to Personal Profile", Toast.LENGTH_SHORT).show()
                            },
                            onRequireLogin = { viewModel.showAuthDialog(true) }
                        )
                    }

                    // ৫. ভাসমান উইজেট, অ্যাড এবং সমস্ত গ্লোবাল ডায়ালগ
                    GlobalOverlays(
                        currentScreen = currentScreen,
                        isLandscape = isLandscape,
                        isVip = isVip,
                        userProfileId = authState.userProfile?.id,
                        userDisplayName = authState.userProfile?.displayName ?: "User",
                        userEmail = authState.userProfile?.email,
                        userAvatar = authState.userProfile?.avatar,
                        showAuthDialog = authState.showAuthDialog,
                        showPageApplyDialog = showPageApplyDialog,
                        updateStateShowDialog = updateState.showDialog,
                        updateInfo = updateState.updateInfo,
                        inAppBrowserRequest = inAppBrowserRequest,
                        viewModel = viewModel,
                        reelsViewModel = reelsViewModel,
                        onNavigateToCommunityChat = { navigateTo(Screen.CommunityChat, null) },
                        onDismissAuthDialog = { viewModel.showAuthDialog(false) },
                        onDismissPageApplyDialog = { showPageApplyDialog = false },
                        onPageApplySuccess = { reelsViewModel.checkMyCreatorPage() },
                        onDismissUpdateDialog = { viewModel.dismissUpdateDialog() }
                    )
                }
            }
        }
    }
}
