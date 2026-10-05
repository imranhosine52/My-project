@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.UnifiedAdManager
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.screens.profile.components.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val GoldVip = Color(0xFFFFB300)
private val CardBorderStroke = Color(0xFF1D2434)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    onNavigateToLocalGallery: () -> Unit,
    onNavigateToCommunityChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()

    val installedVersion = remember { viewModel.getInstalledAppVersion() }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // শিট ও ডায়ালগ কন্ট্রোল স্টেট
    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }

    // 🎯 ইন-অ্যাপ ওয়েবভিউ ইনভয়েস স্টেট
    var showInAppInvoiceWebView by remember { mutableStateOf(false) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var localAvatarOverride by remember { mutableStateOf<String?>(null) }

    // ইমেজ ক্রপার স্টেট (সরাসরি প্রোফাইল ছবিতে ট্যাপের জন্য)
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
            .background(Color(0xFF090C13))
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshVipStatusAndProfile()
                    viewModel.loadVipSubscriptionPlans()
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
                // ১. ইউজার প্রোফাইল হেডার
                ProfileHeaderCard(
                    isLoggedIn = authState.isLoggedIn,
                    userProfile = authState.userProfile,
                    isVip = vipState.isVip,
                    vipDaysLeft = vipState.daysRemaining,
                    isUploadingAvatar = isUploadingAvatar,
                    currentAvatarUrlOverride = localAvatarOverride,
                    onAvatarClick = {
                        val currentAvatar = localAvatarOverride ?: authState.userProfile?.avatar
                        if (!currentAvatar.isNullOrBlank()) {
                            showFullAvatarPreview = true
                        } else {
                            avatarGalleryPicker.launch("image/*")
                        }
                    },
                    onEditClick = { showEditProfileSheet = true },
                    onLogInClick = { showAuthDialog = true }
                )

                // ২. অফিসিয়াল ওয়েবসাইট ব্যানার
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

                // ৩. প্রিমিয়াম পাস সেকশন
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

                // ৬. সেটিংস ও ইনভয়েস (🎯 ইনভয়েস ১০০% ইন-অ্যাপ ওয়েবভিউতে ওপেন হবে)
                ModernMenuGroupCard {
                    if (authState.isLoggedIn) {
                        ModernMenuRowItem(
                            icon = Icons.Default.ManageAccounts,
                            title = "Edit Profile & Photo",
                            subtitle = "Update your name and photo",
                            iconTint = ActionGreen,
                            onClick = { showEditProfileSheet = true }
                        )
                        HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    }

                    // 🎯 ইন-অ্যাপ ওয়েবভিউ ইনভয়েস
                    ModernMenuRowItem(
                        icon = Icons.Default.ReceiptLong,
                        title = "Payment & Invoices",
                        subtitle = "View your live VIP invoices & receipt",
                        iconTint = Color(0xFFB388FF),
                        onClick = { showInAppInvoiceWebView = true }
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

        // =============================================================
        // 🧾 ৮. ১০০% ইন-অ্যাপ ওয়েবভিউ ইনভয়েস ডায়ালগ
        // =============================================================
        if (showInAppInvoiceWebView) {
            val numericUid = authState.userProfile?.id?.filter { it.isDigit() }?.ifBlank { "0" } ?: "0"
            InAppInvoiceWebViewDialog(
                userId = numericUid,
                onDismiss = { showInAppInvoiceWebView = false }
            )
        }

        // ক্রপার ডায়ালগ
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
                            Toast.makeText(context, "✓ Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update profile photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // এডিট প্রোফাইল শিট
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

        // ফুলস্ক্রিন অবতার প্রিভিউ
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
                            contentDescription = "Avatar Preview",
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

        // সেটিংস শিট
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

        // ভার্সন স্ক্যানার ডায়ালগ
        if (showScannerDialog) {
            VersionScannerDialog(
                viewModel = viewModel,
                installedVersion = installedVersion,
                onDismiss = { showScannerDialog = false }
            )
        }

        // পাসওয়ার্ড চেঞ্জ ডায়ালগ
        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onPasswordChanged = { _, _ ->
                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    showChangePasswordDialog = false
                }
            )
        }

        // অথ ডায়ালগ
        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }
    }
}

/**
 * 🧾 ১০০% ইন-অ্যাপ ওয়েবভিউ ইনভয়েস ডায়ালগ
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppInvoiceWebViewDialog(
    userId: String,
    onDismiss: () -> Unit
) {
    val invoiceUrl = "https://playdramaflix.com/app/vip/invoices.php?user_id=$userId"
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
                                cacheMode = WebSettings.LOAD_DEFAULT
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
