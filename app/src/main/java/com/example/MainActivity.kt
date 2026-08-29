package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartIoAdManager
import com.example.ads.UnifiedAdManager
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.AppInstalledWelcomeDialog
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

sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: DramaFlixViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val apiService = ApiClient.apiService
        val repository = PlayDramaFlixRepository(applicationContext, apiService, database)
        DramaFlixViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Unified Ad Mediation Architecture
        UnifiedAdManager.init(this)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val isVip = authState.isVip
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home()) }
                var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                val inAppBrowserRequest by UnifiedAdManager.inAppBrowserRequest.collectAsStateWithLifecycle()
                var showWelcomeDialog by remember { mutableStateOf(false) }

                fun navigateTo(newScreen: Screen, tab: BottomNavTab? = null) {
                    if (tab != null) {
                        selectedTab = tab
                    }
                    // Adsterra Popunder check on page-to-page navigation (remotely managed via admin panel)
                    UnifiedAdManager.showPopunderIfEligible(context, isVip = isVip)
                    
                    UnifiedAdManager.showInterstitial(context, isVip = isVip) {
                        currentScreen = newScreen
                    }
                }

                // Check first install for welcome safety dialog & sync remote ad config
                LaunchedEffect(Unit) {
                    viewModel.loadRemoteAdsConfig(context)
                    val prefs = context.getSharedPreferences("dramaflix_prefs", MODE_PRIVATE)
                    val isFirstLaunch = prefs.getBoolean("is_first_install_launch", true)
                    if (isFirstLaunch) {
                        showWelcomeDialog = true
                        prefs.edit().putBoolean("is_first_install_launch", false).apply()
                    }
                }

                // Handle back button navigation
                BackHandler(enabled = currentScreen !is Screen.Home) {
                    navigateTo(Screen.Home(), BottomNavTab.HOME)
                }

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
                        // Only show bottom navigation on top-level tabs (hide during active player playback for immersion)
                        if (currentScreen !is Screen.Player) {
                            PlayDramaFlixBottomNav(
                                selectedTab = selectedTab,
                                onTabSelected = { tab ->
                                    if (selectedTab != tab) {
                                        val newScreen = when (tab) {
                                            BottomNavTab.HOME -> Screen.Home()
                                            BottomNavTab.SEARCH -> Screen.Search
                                            BottomNavTab.VIP -> Screen.Vip
                                            BottomNavTab.WATCHLIST -> Screen.Watchlist
                                            BottomNavTab.PROFILE -> Screen.Profile
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
                                bottom = if (currentScreen is Screen.Player) 0.dp else innerPadding.calculateBottomPadding()
                            )
                    ) {
                        when (val screen = currentScreen) {
                            is Screen.Home -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { slug ->
                                        navigateTo(Screen.Player(slug))
                                    },
                                    onNavigateToVip = {
                                        navigateTo(Screen.Vip, BottomNavTab.VIP)
                                    },
                                    onNavigateToSearch = {
                                        navigateTo(Screen.Search, BottomNavTab.SEARCH)
                                    }
                                )
                            }
                            is Screen.Player -> {
                                PlayerScreen(
                                    slug = screen.slug,
                                    viewModel = viewModel,
                                    onBackClick = {
                                        navigateTo(Screen.Home(), BottomNavTab.HOME)
                                    },
                                    onNavigateToVip = {
                                        navigateTo(Screen.Vip, BottomNavTab.VIP)
                                    },
                                    onRelatedDramaClick = { newSlug ->
                                        navigateTo(Screen.Player(newSlug))
                                    }
                                )
                            }
                            is Screen.Search -> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { slug ->
                                        navigateTo(Screen.Player(slug))
                                    }
                                )
                            }
                            is Screen.Vip -> {
                                VipScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = {
                                        navigateTo(Screen.Home(), BottomNavTab.HOME)
                                    }
                                )
                            }
                            is Screen.Watchlist -> {
                                WatchlistScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { slug ->
                                        navigateTo(Screen.Player(slug))
                                    }
                                )
                            }
                            is Screen.Profile -> {
                                ProfileScreen(
                                    viewModel = viewModel,
                                    onNavigateToVip = {
                                        navigateTo(Screen.Vip, BottomNavTab.VIP)
                                    },
                                    onNavigateToWatchlist = {
                                        navigateTo(Screen.Watchlist, BottomNavTab.WATCHLIST)
                                    }
                                )
                            }
                        }
                    }
                }

                // Adsterra Social Bar Ads Overlay (Suppressed if VIP or Ads Disabled)
                SocialBarAdOverlay(
                    isVip = isVip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (currentScreen is Screen.Player) 0.dp else 56.dp)
                )
            }

                // Global Auth Dialog
                if (authState.showAuthDialog) {
                    AuthBottomSheetDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showAuthDialog(false) }
                    )
                }

                // First Launch Welcome Dialog
                if (showWelcomeDialog) {
                    AppInstalledWelcomeDialog(
                        onDismiss = { showWelcomeDialog = false }
                    )
                }

                // In-App Update Dialog (Remote version check)
                if (updateState.showDialog && updateState.updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateState.updateInfo!!,
                        onDismiss = { viewModel.dismissUpdateDialog() }
                    )
                }

                // 🌐 In-App Browser Dialog (Opens Smartlink/Direct Link/Popunder strictly inside app)
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
}
