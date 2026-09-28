@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.InvoiceItemDto
import com.example.data.model.UserProfileDto
import com.example.ui.VipCrown3DIcon
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.screens.profile.PageApplicationDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.WelcomeNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TelegramBlue = Color(0xFF2AABEE)
private val ActionGreen = Color(0xFF00E676)
private val DarkCardBackground = Color(0xFF10141F)
private val CardBorderStroke = Color(0xFF1D2434)
private val TextMutedSlate = Color(0xFF8B95A5)
private val AlertRed = Color(0xFFFF2A4B)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    onNavigateToLocalGallery: () -> Unit,
    onNavigateToCommunityChat: () -> Unit = {},
    onSwitchToCreatorStudio: (CreatorPageDto) -> Unit, // 👈 ২ নম্বর ছবির সুইচ কলব্যাক
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

    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }

    // 🌟 ক্রিয়েটর পেজ স্টেট ও আবেদন ডায়ালগ
    var myCreatorPage by remember { mutableStateOf<CreatorPageDto?>(null) }
    var showPageApplicationDialog by remember { mutableStateOf(false) }

    fun refreshCreatorPageStatus() {
        if (authState.isLoggedIn) {
            coroutineScope.launch {
                val res = viewModel.repository.getMyCreatorPage()
                myCreatorPage = res.getOrNull()?.page
            }
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        refreshCreatorPageStatus()
    }

    val directAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val user = authState.userProfile
            val userName = user?.displayName ?: "DramaFlix Member"
            viewModel.updateUserProfileData(
                context = context,
                name = userName,
                avatarUri = uri
            ) { success ->
                if (success) {
                    Toast.makeText(context, "✓ Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshVipStatusAndProfile()
    }

    val guestId = remember { "77" + (100000..999999).random() }

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
                    delay(500)
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
                // =========================================================================
                // 👤 ১. ইউজার প্রোফাইল হেডার কার্ড (২ নম্বর ছবির হুবহু সুইচ বাটন সহ)
                // =========================================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkCardBackground,
                    border = BorderStroke(1.dp, CardBorderStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (authState.isLoggedIn && authState.userProfile != null) {
                        val user = authState.userProfile!!

                        val avatarUrl = remember(user.id, user.email, user.avatar, user.effectiveAvatar) {
                            user.avatar?.takeIf { it.isNotBlank() }
                                ?: user.effectiveAvatar?.takeIf { it.isNotBlank() }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // ইউজার অবতার
                                    Box(modifier = Modifier.size(68.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E2838))
                                                .clickable {
                                                    if (!avatarUrl.isNullOrBlank()) {
                                                        showFullAvatarPreview = true
                                                    } else {
                                                        directAvatarPicker.launch("image/*")
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(avatarUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = user.displayName,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = user.displayName.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(ActionGreen)
                                                .border(2.dp, DarkCardBackground, CircleShape)
                                                .align(Alignment.BottomEnd)
                                                .clickable { directAvatarPicker.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    // নাম, আইডি ও স্ট্যাটাস
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = user.displayName,
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (vipState.isVip) {
                                                VipCrown3DIcon(modifier = Modifier.size(20.dp, 15.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        val uid = user.effectiveAccountId
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF182030))
                                                .clickable {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("UID", uid))
                                                    Toast.makeText(context, "ID copied!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ID: $uid", color = TextMutedSlate, fontSize = 11.sp)
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextMutedSlate, modifier = Modifier.size(10.dp))
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = if (vipState.isVip) "👑 VIP Active (${vipState.daysRemaining} days left)" else "Free Member",
                                            color = if (vipState.isVip) GoldVip else Color(0xFF64748B),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // =========================================================================
                                // 🔄 ২ নম্বর ছবির হুবহু ফেসবুক স্টাইল প্রোফাইল ↔ পেজ সুইচ বাটন
                                // =========================================================================
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val page = myCreatorPage
                                    if (page != null && page.isApproved) {
                                        // ২ নম্বর ছবির মতো গোল ঘোরানো রিফ্রেশ অ্যারো রিং সহ পেজের লোগো
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clickable { onSwitchToCreatorStudio(page) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // চারপাশে ঘোরানো অ্যারো রিং
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .border(1.5.dp, Color(0xFF7E8698), CircleShape)
                                                    .padding(2.dp)
                                            ) {
                                                AsyncImage(
                                                    model = page.avatar ?: "https://ui-avatars.com/api/?name=${page.pageName}&background=00E676&color=000&bold=true",
                                                    contentDescription = "Page Avatar",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            // ছোট সুইচ আইকন ব্যাজ
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(ActionGreen),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Sync, contentDescription = "Switch", tint = Color.Black, modifier = Modifier.size(11.dp))
                                            }
                                        }

                                        // ড্রপডাউন অ্যারো (⌵) + লাল 9+ ব্যাজ
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E2638))
                                                .clickable { onSwitchToCreatorStudio(page) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Switch", tint = Color.White, modifier = Modifier.size(20.dp))

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AlertRed,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-4).dp)
                                            ) {
                                                Text("9+", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 3.dp))
                                            }
                                        }
                                    } else {
                                        // পেজ না থাকলে এডিট বাটন
                                        IconButton(
                                            onClick = { showEditProfileSheet = true },
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF192334))
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // লগইন না করা থাকলে
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Log in to your account", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("Guest ID: $guestId", color = TextMutedSlate, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Button(
                                onClick = { showAuthDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Log In", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // =========================================================================
                // 🌟 ২. ক্রিয়েটর পেজ ম্যানেজমেন্ট অপশন
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
                                title = "Creator Page Application",
                                subtitle = "@${page.handle} is under admin review",
                                badge = "PENDING ⏳",
                                badgeColor = Color(0xFFFFB300),
                                iconTint = Color(0xFFFFB300),
                                onClick = {
                                    Toast.makeText(context, "Your page application is under review by admin.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        else -> {
                            ModernMenuRowItem(
                                icon = Icons.Default.Storefront,
                                title = "Create Creator Page",
                                subtitle = "Apply for a Page to publish 3-min Reels & 24h Stories",
                                badge = "+ APPLY",
                                badgeColor = Color(0xFF00E5FF),
                                iconTint = Color(0xFF00E5FF),
                                onClick = {
                                    if (!authState.isLoggedIn) showAuthDialog = true
                                    else showPageApplicationDialog = true
                                }
                            )
                        }
                    }
                }

                // 🌐 অফিসিয়াল ওয়েবসাইট ব্যানার
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF082B1B),
                    border = BorderStroke(0.8.dp, Color(0xFF105B3A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://playdramaflix.com")))
                            } catch (_: Exception) {}
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

                // ৪. কমিউনিটি চ্যাট ও সোশ্যাল সেকশন
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
                        badge = "${watchlistState.savedDramas.size}",
                        badgeColor = Color.White,
                        iconTint = Color(0xFFFF4081),
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications & Alerts",
                        subtitle = "Updates on newly released episodes",
                        badge = "3",
                        badgeColor = Color(0xFFFF3B30),
                        iconTint = Color(0xFFFFB300),
                        onClick = onNavigateToNotification
                    )
                }

                // ৫. লোকাল মিডিয়া প্লেয়ার ও ব্রাউজার
                ModernMenuGroupCard {
                    ModernMenuRowItem(
                        icon = Icons.Default.VideoLibrary,
                        title = "Gallery Video Player",
                        subtitle = "MX Player Style • Play phone offline media",
                        badge = "100% Free",
                        badgeColor = ActionGreen,
                        iconTint = ActionGreen,
                        onClick = onNavigateToLocalGallery
                    )
                    HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                    ModernMenuRowItem(
                        icon = Icons.Default.TravelExplore,
                        title = "In-App Web Browser",
                        subtitle = "High-speed browsing with Ad-block support",
                        iconTint = TelegramBlue,
                        onClick = onNavigateToBrowser
                    )
                }

                // ৬. একাউন্ট ও সেটিংস
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

        // ডায়ালগসমূহ
        if (showPageApplicationDialog) {
            PageApplicationDialog(
                viewModel = viewModel,
                onDismiss = { showPageApplicationDialog = false },
                onSuccess = { refreshCreatorPageStatus() }
            )
        }

        if (showEditProfileSheet && authState.userProfile != null) {
            TelegramStyleEditProfileSheet(
                currentUser = authState.userProfile!!,
                isLoading = authState.isLoading,
                onSave = { newName, newAvatarUri ->
                    viewModel.updateUserProfileData(
                        context = context,
                        name = newName,
                        avatarUri = newAvatarUri
                    ) { success ->
                        if (success) {
                            Toast.makeText(context, "✓ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            showEditProfileSheet = false
                        }
                    }
                },
                onDismiss = { showEditProfileSheet = false }
            )
        }

        val currentPhotoToView = authState.userProfile?.effectiveAvatar?.takeIf { it.isNotBlank() }
            ?: authState.userProfile?.avatar?.takeIf { it.isNotBlank() }

        if (showFullAvatarPreview && !currentPhotoToView.isNullOrBlank()) {
            Dialog(
                onDismissRequest = { showFullAvatarPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentPhotoToView)
                            .crossfade(true)
                            .build(),
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

        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }

        if (showInvoiceSheet) {
            InvoiceHistorySheet(
                viewModel = viewModel,
                onDismiss = { showInvoiceSheet = false }
            )
        }

        if (showSettingsSheet) {
            SettingsBottomSheet(
                viewModel = viewModel,
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
            AnimatedVersionScannerDialog(
                viewModel = viewModel,
                installedVersion = installedVersion,
                onDismiss = { showScannerDialog = false }
            )
        }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onPasswordChanged = {
                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    showChangePasswordDialog = false
                }
            )
        }
    }
}

// -------------------------------------------------------------
// হেল্পার মেনু কার্ড ও আইটেম কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
private fun ModernMenuGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCardBackground,
        border = BorderStroke(1.dp, CardBorderStroke)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ModernMenuRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = TextMutedSlate,
    iconTint: Color = TextMutedSlate,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(text = subtitle, color = TextMutedSlate, fontSize = 11.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF5A667A), modifier = Modifier.size(18.dp))
        }
    }
}
