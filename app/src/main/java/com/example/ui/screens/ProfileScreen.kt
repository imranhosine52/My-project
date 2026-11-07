@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.UnifiedAdManager
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.UserProfileDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.PromoCodeDialog
import com.example.ui.screens.profile.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

// 🎨 স্ক্রিনশটের হুবহু ডার্ক কালার প্যালেট
private val ScreenBg = Color(0xFF0C0E14)
private val CardGroupBg = Color(0xFF131620)
private val CardBorderColor = Color(0xFF1C2230)
private val ActionGreen = Color(0xFF00E676)
private val GreenBannerBg = Color(0xFF07241A)
private val GreenBannerBorder = Color(0xFF0E523A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8E95A5)
private val GoldAccent = Color(0xFFFFB300)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToLocalGallery: () -> Unit,
    onNavigateToCommunityChat: () -> Unit = {},
    onNavigateToPlayer: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()
    val notificationState by viewModel.notificationUiState.collectAsStateWithLifecycle()
    val activityState by viewModel.activityUiState.collectAsStateWithLifecycle()

    // 📺 আপনার ফোনের আসল Room Database থেকে Watch History
    val watchHistoryList by viewModel.repository.continueWatchingFlow.collectAsStateWithLifecycle(emptyList())

    val installedVersion = remember { viewModel.getInstalledAppVersion() }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // ডায়ালগ কন্ট্রোল
    var showAuthDialog by remember { mutableStateOf(false) }
    var showPromoCodeDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }
    var showInAppInvoiceWebView by remember { mutableStateOf(false) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var localAvatarOverride by remember { mutableStateOf<String?>(null) }
    var rawSelectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageCropDialog by remember { mutableStateOf(false) }

    val avatarGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            rawSelectedImageUri = uri
            showImageCropDialog = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshVipStatusAndProfile()
                    viewModel.loadVipSubscriptionPlans()
                    viewModel.loadNotifications()
                    viewModel.loadUserActivity(isRefresh = true)
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // =============================================================
                // 🔝 ১. টপ প্রোফাইল হেডার
                // =============================================================
                MovieBoxStyleHeader(
                    isLoggedIn = authState.isLoggedIn,
                    userProfile = authState.userProfile,
                    isVip = vipState.isVip,
                    vipDaysLeft = vipState.daysRemaining,
                    onLoginClick = { showAuthDialog = true },
                    onEditClick = { showEditProfileSheet = true },
                    onAvatarClick = {
                        val currentAvatar = localAvatarOverride ?: authState.userProfile?.avatar
                        if (!currentAvatar.isNullOrBlank()) showFullAvatarPreview = true
                        else avatarGalleryPicker.launch("image/*")
                    },
                    onScannerClick = { showScannerDialog = true }
                )

                // =============================================================
                // 🟢 ২. সবুজ অফিশিয়াল ওয়েবসাইট রিবন
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GreenBannerBg,
                    border = BorderStroke(0.8.dp, GreenBannerBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            UnifiedAdManager.openChromeCustomTab(context, "https://playdramaflix.com")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = ActionGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Official website: https://playdramaflix.com",
                                color = ActionGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = ActionGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // =============================================================
                // 👑 ৩. গোল্ডেন VIP কার্ড (৫টি আসল ফিচার সহ)
                // =============================================================
                MovieBoxStyleVipCard(
                    isVip = vipState.isVip,
                    vipDaysLeft = vipState.daysRemaining,
                    planName = vipState.planName,
                    onUnlockClick = onNavigateToVip
                )

                // =============================================================
                // 🎁 ৪. প্রমো কোড রিবন
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B1914),
                    border = BorderStroke(0.8.dp, Color(0xFF3D321A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPromoCodeDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Have a VIP Promo Code?",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Redeem ➔",
                            color = ActionGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // =============================================================
                // 🕒 ৫. ওয়াচ হিস্ট্রি ক্যারোসেল
                // =============================================================
                if (watchHistoryList.isNotEmpty()) {
                    WatchHistorySection(
                        items = watchHistoryList,
                        onItemClick = { slug ->
                            onNavigateToPlayer?.invoke(slug) ?: run {
                                Toast.makeText(context, "Resuming video...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // =============================================================
                // 📋 ৬. গ্রুপ ১: My List, Messages, Invoices, Downloads
                // =============================================================
                MovieBoxMenuGroup {
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = "My List",
                        badgeText = if (watchlistState.savedDramas.isNotEmpty()) "${watchlistState.savedDramas.size}" else null,
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Messages & Notifications",
                        badgeText = if (notificationState.unreadCount > 0) "${notificationState.unreadCount}" else null,
                        badgeColor = Color(0xFFFF2D55),
                        onClick = {
                            viewModel.loadNotifications()
                            Toast.makeText(context, "${notificationState.notifications.size} updates available", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "Payment & Invoices",
                        badgeText = if (vipState.isVip) "VIP ACTIVE" else null,
                        badgeColor = if (vipState.isVip) ActionGreen else GoldAccent,
                        onClick = { showInAppInvoiceWebView = true }
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.VideoLibrary,
                        title = "Gallery Video Player",
                        badgeText = "OFFLINE",
                        badgeColor = Color(0xFF00E5FF),
                        onClick = onNavigateToLocalGallery
                    )
                }

                // =============================================================
                // 💬 ৭. গ্রুপ ২: Community & My Activity
                // =============================================================
                MovieBoxMenuGroup {
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.Forum,
                        title = "Community Live Chat",
                        badgeText = "LIVE ●",
                        badgeColor = ActionGreen,
                        onClick = onNavigateToCommunityChat
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "My Activity",
                        badgeText = "${activityState.activityData?.likesList?.size ?: 0}",
                        onClick = {
                            viewModel.loadUserActivity(isRefresh = true)
                            Toast.makeText(context, "Activity refreshed!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // =============================================================
                // ⚙️ ৮. গ্রুপ ৩: Settings, Clear Cache, Feedback
                // =============================================================
                MovieBoxMenuGroup {
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.Settings,
                        title = "Settings & Updates",
                        badgeText = "v$installedVersion",
                        onClick = { showSettingsSheet = true }
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear Cache & Media",
                        badgeText = "Clean",
                        badgeColor = ActionGreen,
                        onClick = {
                            val freedBytes = performRealCacheCleanup(context)
                            val freedMb = freedBytes / (1024.0 * 1024.0)
                            val message = if (freedMb > 0.05) {
                                String.format(Locale.US, "✓ Cleared %.1f MB of cache!", freedMb)
                            } else {
                                "✓ App cache is already clean!"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)
                    MovieBoxMenuRow(
                        icon = Icons.Outlined.Feedback,
                        title = "Feedback & Support",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@playdramaflix.com")
                                putExtra(Intent.EXTRA_SUBJECT, "PlayDramaFlix App Feedback v$installedVersion")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Feedback"))
                            } catch (_: Exception) {
                                UnifiedAdManager.openChromeCustomTab(context, "https://playdramaflix.com")
                            }
                        }
                    )
                }

                // সাইন আউট বাটন (যদি লগইন থাকে)
                if (authState.isLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1417),
                        border = BorderStroke(0.8.dp, Color(0xFF4A1A22)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.signOut(context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFF4D4F), modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log Out", color = Color(0xFFFF4D4F), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // =============================================================
                // 🌐 ৯. বটম ফুটার
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Official website: playdramaflix.com ",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Copy link",
                        color = ActionGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Website", "https://playdramaflix.com"))
                            Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // =============================================================
        // 🛡️ ডায়ালগ এবং শিটসমূহ
        // =============================================================
        if (showPromoCodeDialog) {
            PromoCodeDialog(
                viewModel = viewModel,
                onDismiss = { showPromoCodeDialog = false }
            )
        }

        if (showInAppInvoiceWebView) {
            InAppInvoiceWebViewDialog(
                userProfile = authState.userProfile,
                onDismiss = { showInAppInvoiceWebView = false }
            )
        }

        if (showImageCropDialog && rawSelectedImageUri != null) {
            InAppInteractiveImageCropper(
                imageUri = rawSelectedImageUri!!,
                onDismiss = {
                    showImageCropDialog = false
                    rawSelectedImageUri = null
                },
                onCropSuccess = { croppedUri ->
                    showImageCropDialog = false
                    rawSelectedImageUri = null
                    isUploadingAvatar = true

                    viewModel.updateUserProfileData(context, null, croppedUri) { success ->
                        isUploadingAvatar = false
                        if (success) {
                            localAvatarOverride = croppedUri.toString()
                            viewModel.refreshVipStatusAndProfile()
                            Toast.makeText(context, "✓ Profile photo updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update profile photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showEditProfileSheet && authState.userProfile != null) {
            EditUserProfileSheet(
                currentUser = authState.userProfile!!,
                isLoading = isUploadingAvatar,
                onSave = { newName, newAvatarUri ->
                    isUploadingAvatar = true
                    viewModel.updateUserProfileData(context, newName, newAvatarUri) {
                        isUploadingAvatar = false
                        showEditProfileSheet = false
                        if (newAvatarUri != null) localAvatarOverride = newAvatarUri.toString()
                        viewModel.refreshVipStatusAndProfile()
                        Toast.makeText(context, "✓ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showEditProfileSheet = false }
            )
        }

        if (showFullAvatarPreview) {
            val fullAvatar = localAvatarOverride ?: authState.userProfile?.avatar
            if (!fullAvatar.isNullOrBlank()) {
                Dialog(
                    onDismissRequest = { showFullAvatarPreview = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(fullAvatar).crossfade(true).build(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { showFullAvatarPreview = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
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

// =============================================================================
// 🔝 হেডার কম্পোনেন্ট
// =============================================================================
@Composable
private fun MovieBoxStyleHeader(
    isLoggedIn: Boolean,
    userProfile: UserProfileDto?,
    isVip: Boolean,
    vipDaysLeft: Int,
    onLoginClick: () -> Unit,
    onEditClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onScannerClick: () -> Unit
) {
    val context = LocalContext.current
    val effectiveId = userProfile?.effectiveAccountId
        ?: remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE).getString("account_id", null) ?: "535359562" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onScannerClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Scan",
                    tint = TextWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isLoggedIn && userProfile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2434))
                            .border(1.5.dp, if (isVip) GoldAccent else Color(0xFF2C374D), CircleShape)
                            .clickable { onAvatarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userProfile.avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(userProfile.avatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userProfile.displayName.take(1).uppercase(),
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = userProfile.displayName,
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVip) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2A2000),
                                    border = BorderStroke(0.6.dp, GoldAccent)
                                ) {
                                    Text(
                                        text = "VIP",
                                        color = GoldAccent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1A1F2C)
                        ) {
                            Text(
                                text = "ID: $effectiveId",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF192232),
                    border = BorderStroke(0.8.dp, Color(0xFF26354D)),
                    modifier = Modifier.clickable { onEditClick() }
                ) {
                    Text(
                        text = "Edit",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Log in to your account",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1B202D)
                    ) {
                        Text(
                            text = "ID: $effectiveId",
                            color = Color(0xFF7E8799),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Button(
                    onClick = onLoginClick,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Log in",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// 👑 গোল্ডেন VIP কার্ড (৫টি পার্কস আইকন সহ)
// =============================================================================
@Composable
private fun MovieBoxStyleVipCard(
    isVip: Boolean,
    vipDaysLeft: Int,
    planName: String?,
    onUnlockClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF9E783E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE5B567),
                            Color(0xFFC79549),
                            Color(0xFF8C5D23)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onUnlockClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFF2C1902),
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = if (isVip) (planName ?: "PlayDramaFlix VIP") else "PlayDramaFlix Premium",
                            color = Color(0xFF241502),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF241502),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFF2D9),
                        modifier = Modifier.clickable { onUnlockClick() }
                    ) {
                        Text(
                            text = if (isVip) "$vipDaysLeft Days" else "Unlock",
                            color = Color(0xFF2A1A04),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VipFeatureItem(icon = Icons.Default.MovieFilter, label = "Premium\nContents")
                    VipFeatureItem(icon = Icons.Default.Hd, label = "High\nQuality")
                    VipFeatureItem(icon = Icons.Default.Block, label = "No\nAds")
                    VipFeatureItem(icon = Icons.Default.FileDownload, label = "Free\nDownloads")
                    VipFeatureItem(icon = Icons.Default.PhoneAndroid, label = "2 Devices\nStream")
                }
            }
        }
    }
}

@Composable
private fun VipFeatureItem(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x38000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFFF6E5),
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = label,
            color = Color(0xFF332005),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================================
// 🕒 ওয়াচ হিস্ট্রি সেকশন
// =============================================================================
@Composable
private fun WatchHistorySection(
    items: List<WatchHistoryEntity>,
    onItemClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Watch History",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items.take(10), key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clickable { onItemClick(item.contentSlug) },
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2432))
                    ) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.contentTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Surface(
                            shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 4.dp),
                            color = Color(0xB3000000),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "EP ${item.episodeNumber}",
                                color = ActionGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (item.progressPercentage > 0f) {
                            LinearProgressIndicator(
                                progress = { item.progressPercentage.coerceIn(0f, 1f) },
                                color = ActionGreen,
                                trackColor = Color(0x66000000),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                    }

                    Text(
                        text = item.contentTitle,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =============================================================================
// 📋 মেনু গ্রুপ ও রো কম্পোনেন্ট
// =============================================================================
@Composable
private fun MovieBoxMenuGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardGroupBg,
        border = BorderStroke(0.8.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun MovieBoxMenuRow(
    icon: ImageVector,
    title: String,
    badgeText: String? = null,
    badgeColor: Color = TextMuted,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = title,
                color = TextWhite,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF555F73),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// =============================================================================
// 🧾 ইন-অ্যাপ ওয়েবভিউ ইনভয়েস ডায়ালগ
// =============================================================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppInvoiceWebViewDialog(
    userProfile: UserProfileDto?,
    onDismiss: () -> Unit
) {
    val numericUid = userProfile?.id?.filter { it.isDigit() }?.ifBlank { "0" } ?: "0"
    val rawId = Uri.encode(userProfile?.id ?: "0")
    val accountId = Uri.encode(userProfile?.effectiveAccountId ?: "")
    val email = Uri.encode(userProfile?.email ?: "")
    val phone = Uri.encode(userProfile?.phone ?: "")
    val name = Uri.encode(userProfile?.displayName ?: "")
    val isVipFlag = if (userProfile?.isVip == true || userProfile?.plan?.lowercase() in listOf("vip", "premium")) "1" else "0"

    val invoiceUrl = "https://playdramaflix.com/app/vip/invoices.php?user_id=$numericUid&raw_id=$rawId&account_id=$accountId&email=$email&phone=$phone&name=$name&is_vip=$isVipFlag"
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color(0xFF06080E),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "Invoices & History",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF06080E)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF06080E))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(false)
                            }
                            setBackgroundColor(android.graphics.Color.parseColor("#06080E"))

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                            }
                            webChromeClient = WebChromeClient()
                            loadUrl(invoiceUrl)
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        color = Color(0xFFFFB300),
                        trackColor = Color(0xFF1E2536),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

/**
 * 🧹 ক্যাশ মেমোরি ক্লিনআপ মেথড
 */
private fun performRealCacheCleanup(context: Context): Long {
    var totalDeletedBytes = 0L
    try {
        context.cacheDir?.let { cacheDir ->
            totalDeletedBytes += calculateFolderSize(cacheDir)
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        context.externalCacheDir?.let { extCache ->
            totalDeletedBytes += calculateFolderSize(extCache)
            extCache.deleteRecursively()
            extCache.mkdirs()
        }
    } catch (_: Exception) {}
    return totalDeletedBytes
}

private fun calculateFolderSize(dir: File): Long {
    var size = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
        size += if (file.isDirectory) calculateFolderSize(file) else file.length()
    }
    return size
}
