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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.StartIoAdManager
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.PlayDramaFlixRepository
import com.example.ui.PlayDramaFlixBottomNav
import com.example.ui.components.AppInstalledWelcomeDialog
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

        // Initialize Start.io Ads
        StartIoAdManager.init(this)

        setContent {
            DramaFlixTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home()) }
                var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
                val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
                var showWelcomeDialog by remember { mutableStateOf(false) }

                // Check first install for welcome safety dialog
                LaunchedEffect(Unit) {
                    val prefs = context.getSharedPreferences("dramaflix_prefs", MODE_PRIVATE)
                    val isFirstLaunch = prefs.getBoolean("is_first_install_launch", true)
                    if (isFirstLaunch) {
                        showWelcomeDialog = true
                        prefs.edit().putBoolean("is_first_install_launch", false).apply()
                    }
                }

                // Handle back button navigation
                BackHandler(enabled = currentScreen !is Screen.Home) {
                    if (currentScreen is Screen.Player) {
                        currentScreen = Screen.Home()
                        selectedTab = BottomNavTab.HOME
                    } else if (currentScreen !is Screen.Home) {
                        currentScreen = Screen.Home()
                        selectedTab = BottomNavTab.HOME
                    }
                }

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
                                    selectedTab = tab
                                    currentScreen = when (tab) {
                                        BottomNavTab.HOME -> Screen.Home()
                                        BottomNavTab.SEARCH -> Screen.Search
                                        BottomNavTab.VIP -> Screen.Vip
                                        BottomNavTab.WATCHLIST -> Screen.Watchlist
                                        BottomNavTab.PROFILE -> Screen.Profile
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
                                        currentScreen = Screen.Player(slug)
                                    },
                                    onNavigateToVip = {
                                        selectedTab = BottomNavTab.VIP
                                        currentScreen = Screen.Vip
                                    },
                                    onNavigateToSearch = {
                                        selectedTab = BottomNavTab.SEARCH
                                        currentScreen = Screen.Search
                                    }
                                )
                            }
                            is Screen.Player -> {
                                PlayerScreen(
                                    slug = screen.slug,
                                    viewModel = viewModel,
                                    onBackClick = {
                                        currentScreen = Screen.Home()
                                        selectedTab = BottomNavTab.HOME
                                    },
                                    onNavigateToVip = {
                                        selectedTab = BottomNavTab.VIP
                                        currentScreen = Screen.Vip
                                    },
                                    onRelatedDramaClick = { newSlug ->
                                        currentScreen = Screen.Player(newSlug)
                                    }
                                )
                            }
                            is Screen.Search -> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { slug ->
                                        currentScreen = Screen.Player(slug)
                                    }
                                )
                            }
                            is Screen.Vip -> {
                                VipScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = {
                                        currentScreen = Screen.Home()
                                        selectedTab = BottomNavTab.HOME
                                    }
                                )
                            }
                            is Screen.Watchlist -> {
                                WatchlistScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { slug ->
                                        currentScreen = Screen.Player(slug)
                                    }
                                )
                            }
                            is Screen.Profile -> {
                                ProfileScreen(
                                    viewModel = viewModel,
                                    onNavigateToVip = {
                                        selectedTab = BottomNavTab.VIP
                                        currentScreen = Screen.Vip
                                    },
                                    onNavigateToWatchlist = {
                                        selectedTab = BottomNavTab.WATCHLIST
                                        currentScreen = Screen.Watchlist
                                    }
                                )
                            }
                        }
                    }
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
            }
        }
    }
}
