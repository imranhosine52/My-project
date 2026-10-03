@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.UnifiedAdManager
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserProfileMetricsDto
import com.example.data.repository.ReelsRepository
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.screens.profile.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val GoldVip = Color(0xFFFFB300)
private val TelegramBlue = Color(0xFF2AABEE)
private val CardBorderStroke = Color(0xFF1D2434)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    onNavigateToLocalGallery: () -> Unit,
    onNavigateToCommunityChat: () -> Unit = {},
    onSwitchToCreatorStudio: (CreatorPageDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reelsRepository = remember { ReelsRepository(context) }
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()

    val installedVersion = remember { viewModel.getInstalledAppVersion() }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // শিট ও ডায়ালগ কন্ট্রোল স্টেট
    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }

    var myCreatorPage by remember { mutableStateOf<CreatorPageDto?>(null) }
    var liveProfileMetrics by remember { mutableStateOf<UserProfileMetricsDto?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    var localAvatarOverride by remember { mutableStateOf<String?>(null) }
    var localCoverOverride by remember { mutableStateOf<String?>(null) }

    val currentUserIdInt = remember(authState.userProfile) {
        authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    fun refreshRealMetrics() {
        if (authState.isLoggedIn && currentUserIdInt > 0) {
            coroutineScope.launch {
                val metricsRes = reelsRepository.getUserProfileMetrics(currentUserIdInt)
                liveProfileMetrics = metricsRes.getOrNull()
            }
        }
    }

    fun refreshCreatorPageStatus() {
        if (authState.isLoggedIn) {
            coroutineScope.launch {
                val res = viewModel.repository.getMyCreatorPage()
                myCreatorPage = res.getOrNull()?.page
                refreshRealMetrics()
            }
        }
    }

    LaunchedEffect(authState.isLoggedIn, currentUserIdInt) {
        refreshCreatorPageStatus()
    }

    val directCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingCover = true
            coroutineScope.launch {
                val result = reelsRepository.uploadUserCover(uri, fallbackUserId = currentUserIdInt)
                isUploadingCover = false
                if (result.isSuccess) {
                    val newCoverUrl = result.getOrNull()
                    if (!newCoverUrl.isNullOrBlank()) {
                        localCoverOverride = newCoverUrl
                        authPrefs.edit().putString("user_cover", newCoverUrl).apply()
                    }
                    refreshRealMetrics()
                    Toast.makeText(context, "✓ Cover photo updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Cover upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C13))
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshVipStatusAndProfile()
                    viewModel.loadVipSubscriptionPlans()
                    refreshCreatorPageStatus()
                    delay(400)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ১. ইউজার প্রোফাইল হেডার কার্ড
                ProfileHeaderCard(
                    isLoggedIn = authState.isLoggedIn,
                    userProfile = authState.userProfile,
                    liveMetrics = liveProfileMetrics,
                    creatorPage = myCreatorPage,
                    isVip = vipState.isVip || liveProfileMetrics?.isVip == true,
                    vipDaysLeft = vipState.daysRemaining,
                    isUploadingAvatar = isUploadingAvatar,
                    isUploadingCover = isUploadingCover,
                    currentAvatarUrlOverride = localAvatarOverride,
                    currentCoverUrlOverride = localCoverOverride,
                    onAvatarClick = {
                        val currentAvatar = localAvatarOverride ?: liveProfileMetrics?.effectiveAvatar ?: authState.userProfile?.avatar
                        if (!currentAvatar.isNullOrBlank()) {
                            showFullAvatarPreview = true
                        } else {
                            showEditProfileSheet = true
                        }
                    },
                    onCoverClick = { directCoverPicker.launch("image/*") },
                    onEditClick = { showEditProfileSheet = true },
                    onSwitchToCreatorStudio = onSwitchToCreatorStudio,
                    onLogInClick = { showAuthDialog = true }
                )

                // =========================================================================
                // 🌟 ২. ক্রিয়েটর চ্যানেল ম্যানেজমেন্ট (Chrome Custom Tabs দিয়ে ওপেন হবে)
                // =========================================================================
                ModernMenuGroupCard {
                    val page = myCreatorPage
                    when {
                        page != null && page.isApproved -> {
                            ModernMenuRowItem(
                                icon = Icons.Default.Verified,
                                title = page.pageName,
                                subtitle = "@${page.handle} • Switch to Creator Studio",
                                badge = "ACTIVE 🌟",
                                badgeColor = ActionGreen,
                                iconTint = ActionGreen,
                                onClick = { onSwitchToCreatorStudio(page) }
                            )
                        }
                        page != null && page.isPending -> {
                            ModernMenuRowItem(
                                icon = Icons.Default.HourglassTop,
                                title = "Channel Under Review",
                                subtitle = "@${page.handle} • View application status",
                                badge = "PENDING ⏳",
                                badgeColor = Color(0xFFFFB300),
                                iconTint = Color(0xFFFFB300),
                                onClick = {
                                    val applyUrl = "https://playdramaflix.com/app/creator/apply.php?user_id=$currentUserIdInt"
                                    UnifiedAdManager.openChromeCustomTab(context, applyUrl)
                                }
                            )
                        }
                        else -> {
                            ModernMenuRowItem(
                                icon = Icons.Default.Storefront,
                                title = "Create Creator Channel",
                                subtitle = "Apply for verified channel to publish Reels & Series",
                                badge = "+ APPLY",
                                badgeColor = Color(0xFF00E5FF),
                                iconTint = Color(0xFF00E5FF),
                                onClick = {
                                    if (!authState.isLoggedIn) {
                                        showAuthDialog = true
                                    } else {
                                        val applyUrl = "https://playdramaflix.com/app/creator/apply.php?user_id=$currentUserIdInt"
                                        UnifiedAdManager.openChromeCustomTab(context, applyUrl)
                                    }
                                }
                            )
                        }
                    }
                }

                // অফিসিয়াল ওয়েবসাইট ব্যানার
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF082B1B),
                    border = BorderStroke(0.8.dp, Color(0xFF105B3A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            UnifiedAdManager.openChromeCustomTab(context, "https://playdramaflix.com")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Official Website: https://playdramaflix.com",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ৩. প্রিমিয়াম ও ভিআইপি সেকশন
                ModernMenuGroupCard {
                    ModernMenuRowItem(
                        icon = Icons.Default.Star,
                        title = "Get Premium Pass",
                        subtitle = "Zero ads • 1080P Ultra HD • All episodes",
                        iconTint = GoldVip,
                        badge = if (vipState.isVip) "VIP ACTIVE" else "UPGRADE",
                        badgeColor = if (vipState.isVip) ActionGreen else GoldVip,
                        onClick = onNavigateToVip
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Tasks for Free Premium",
                        subtitle = "Watch sponsors to unlock 2 hours free VIP",
                        iconTint = Color(0xFFFFA726),
                        onClick = onNavigateToVip
                    )
                }

                // ৪. কমিউনিটি চ্যাট ও ওয়াচলিস্ট সেকশন
                ModernMenuGroupCard {
                    ModernMenuRowItem(
                        icon = Icons.Default.Forum,
                        title = "Community Live Chat",
                        subtitle = "Chat live with drama fans & share moments",
                        badge = "LIVE ●",
                        badgeColor = ActionGreen,
                        iconTint = Color(0xFF00E5FF),
                        onClick = onNavigateToCommunityChat
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.Bookmark,
                        title = "My List & Watchlist",
                        subtitle = "Your saved and favorite drama series",
                        badge = if (watchlistState.savedDramas.isNotEmpty()) "${watchlistState.savedDramas.size}" else null,
                        badgeColor = Color.White,
                        iconTint = Color(0xFFFF4081),
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications & Alerts",
                        subtitle = "Updates on newly released episodes",
                        iconTint = Color(0xFFFFB300),
                        onClick = onNavigateToNotification
                    )
                }

                // ৫. লোকাল মিডিয়া প্লেয়ার
                ModernMenuGroupCard {
                    ModernMenuRowItem(
                        icon = Icons.Default.VideoLibrary,
                        title = "Gallery Video Player",
                        subtitle = "MX Player Style • Play phone offline media",
                        badge = "Free",
                        badgeColor = ActionGreen,
                        iconTint = ActionGreen,
                        onClick = onNavigateToLocalGallery
                    )
                }

                // ৬. সেটিংস ও ইনভয়েস
                ModernMenuGroupCard {
                    if (authState.isLoggedIn) {
                        ModernMenuRowItem(
                            icon = Icons.Default.ManageAccounts,
                            title = "Edit Profile & Photo",
                            subtitle = "Update your cloud avatar and name",
                            iconTint = ActionGreen,
                            onClick = { showEditProfileSheet = true }
                        )
                        HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    }

                    ModernMenuRowItem(
                        icon = Icons.Default.ReceiptLong,
                        title = "Payment & Invoices",
                        subtitle = "View your VIP transaction history",
                        iconTint = Color(0xFFB388FF),
                        onClick = { showInvoiceSheet = true }
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.Settings,
                        title = "Settings & Updates",
                        subtitle = "Version Scanner, Notifications & Cache",
                        badge = "v$installedVersion",
                        badgeColor = ActionGreen,
                        iconTint = Color(0xFF80D8FF),
                        onClick = { showSettingsSheet = true }
                    )
                }

                // সাইন আউট বাটন
                if (authState.isLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1418),
                        border = BorderStroke(1.dp, Color(0xFF4D1A25)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.signOut(context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFF4D4F), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Account", color = Color(0xFFFF4D4F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ফুটার
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PlayDramaFlix v$installedVersion • Stream in Ultra HD",
                        color = Color(0xFF556075),
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // এডিট প্রোফাইল শিট
        if (showEditProfileSheet && authState.userProfile != null) {
            EditUserProfileSheet(
                currentUser = authState.userProfile!!,
                isLoading = isUploadingAvatar,
                onSave = { newName, newAvatarUri ->
                    isUploadingAvatar = true
                    coroutineScope.launch {
                        if (newAvatarUri != null) {
                            val uploadRes = reelsRepository.uploadUserAvatar(newAvatarUri, fallbackUserId = currentUserIdInt)
                            if (uploadRes.isSuccess) {
                                val newUrl = uploadRes.getOrNull()
                                if (!newUrl.isNullOrBlank()) {
                                    localAvatarOverride = newUrl
                                    authPrefs.edit().putString("user_avatar", newUrl).apply()
                                }
                            }
                        }

                        viewModel.updateUserProfileData(context, newName, newAvatarUri) {
                            isUploadingAvatar = false
                            showEditProfileSheet = false
                            viewModel.refreshVipStatusAndProfile()
                            refreshRealMetrics()
                            Toast.makeText(context, "✓ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showEditProfileSheet = false }
            )
        }

        if (showFullAvatarPreview) {
            val fullAvatar = localAvatarOverride ?: liveProfileMetrics?.effectiveAvatar ?: authState.userProfile?.avatar
            if (!fullAvatar.isNullOrBlank()) {
                Dialog(
                    onDismissRequest = { showFullAvatarPreview = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(fullAvatar).crossfade(true).build(),
                            contentDescription = "Avatar Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { showFullAvatarPreview = false },
                            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showInvoiceSheet) {
            ProfileInvoiceSheet(
                viewModel = viewModel,
                onDismiss = { showInvoiceSheet = false }
            )
        }

        if (showSettingsSheet) {
            ProfileSettingsSheet(
                installedVersion = installedVersion,
                onStartUpdateScan = {
                    showSettingsSheet = false
                    showScannerDialog = true
                },
                onOpenChangePassword = { showChangePasswordDialog = true },
                onDismiss = { showSettingsSheet = false }
            )
        }

        if (showScannerDialog) {
            VersionScannerDialog(
                viewModel = viewModel,
                installedVersion = installedVersion,
                onDismiss = { showScannerDialog = false }
            )
        }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onPasswordChanged = { _, _ ->
                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    showChangePasswordDialog = false
                }
            )
        }

        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }
    }
}
