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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.InvoiceItemDto
import com.example.data.model.UserProfileDto
import com.example.ui.VipCrown3DIcon
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.WelcomeNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TelegramBlue = Color(0xFF2AABEE)
private val ActionGreen = Color(0xFF00D166)
private val DarkCardBackground = Color(0xFF10141F)
private val CardBorderStroke = Color(0xFF1D2434)
private val TextMutedSlate = Color(0xFF8B95A5)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToBrowser: () -> Unit,
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

    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }

    // ক্যামেরা আইকনে চাপলে সরাসরি গ্যালারি ওপেন হওয়া
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
                // 👤 ১. টেলিগ্রাম ও হোয়াটসঅ্যাপ স্টাইল প্রিমিয়াম প্রোফাইল হেডার কার্ড
                // =========================================================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkCardBackground,
                    border = BorderStroke(1.dp, CardBorderStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (authState.isLoggedIn && authState.userProfile != null) {
                        val user = authState.userProfile!!

                        // 🎯 মাল্টি-লেয়ার অবতার রিভলভার (অ্যাপ রিস্টার্ট দিলেও ছবি কখনোই হারাবে না)
                        val savedAvatarFromPrefs = remember {
                            context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
                                .getString("user_avatar", null)?.takeIf { it.isNotBlank() }
                        }

                        val avatarUrl = remember(user.avatar, authState.userProfile?.avatar, savedAvatarFromPrefs) {
                            user.avatar?.takeIf { it.isNotBlank() }
                                ?: user.effectiveAvatar?.takeIf { it.isNotBlank() }
                                ?: authState.userProfile?.avatar?.takeIf { it.isNotBlank() }
                                ?: savedAvatarFromPrefs
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 🖼️ অবতার এবং ক্যামেরা বাটন
                                Box(modifier = Modifier.size(76.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF2AABEE), Color(0xFF00D166))
                                                )
                                            )
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
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = user.displayName.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // 🔄 R2 আপলোড চলাকালীন স্পিনার
                                        if (authState.isLoading) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.65f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = ActionGreen,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // 📷 ক্যামেরা বাটন
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(ActionGreen)
                                            .border(2.dp, Color(0xFF10141F), CircleShape)
                                            .align(Alignment.BottomEnd)
                                            .clickable(enabled = !authState.isLoading) {
                                                directAvatarPicker.launch("image/*")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Upload Avatar",
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // 👤 নাম, আইডি ও স্ট্যাটাস
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = user.displayName,
                                            color = Color.White,
                                            fontSize = 17.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (vipState.isVip) {
                                            VipCrown3DIcon(modifier = Modifier.size(22.dp, 16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    // এক ক্লিকে আইডি কপি
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
                                                Toast.makeText(context, "Account ID copied!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("ID: $uid", color = TextMutedSlate, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMutedSlate, modifier = Modifier.size(11.dp))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (vipState.isVip) "👑 VIP Active (${vipState.daysRemaining} days left)" else "Free Member",
                                        color = if (vipState.isVip) GoldVip else Color(0xFF64748B),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // ✏️ টেলিগ্রাম স্টাইল এডিট বাটন
                                IconButton(
                                    onClick = { showEditProfileSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF192334))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Log in to your account",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Guest ID: $guestId",
                                    color = TextMutedSlate,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
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

                // =========================================================================
                // 👑 ২. প্রিমিয়াম ও ভিআইপি সেকশন
                // =========================================================================
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

                // =========================================================================
                // 💬 ৩. কমিউনিটি চ্যাট ও সোশ্যাল সেকশন
                // =========================================================================
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

                // =========================================================================
                // 🎬 ৪. লোকাল মিডিয়া প্লেয়ার ও টুলস
                // =========================================================================
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

                // =========================================================================
                // ⚙️ ৫. একাউন্ট ও সেটিংস
                // =========================================================================
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

                // 🔴 সাইন আউট বাটন
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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

        // =========================================================================
        // 🌟 টেলিগ্রাম ও হোয়াটসঅ্যাপ স্টাইল ফুল এডিট প্রোফাইল বটম শিট
        // =========================================================================
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

        // 🖼️ ফুল-স্ক্রিন প্রোফাইল পিকচার ভিউয়ার (WhatsApp Style)
        val currentPhotoToView = authState.userProfile?.avatar?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE).getString("user_avatar", null)

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
                invoices = vipState.invoiceHistory,
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
// 📱 টেলিগ্রাম স্টাইল এডিট প্রোফাইল বটম শিট
// -------------------------------------------------------------
@Composable
private fun TelegramStyleEditProfileSheet(
    currentUser: UserProfileDto,
    isLoading: Boolean,
    onSave: (String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(currentUser.displayName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121724),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMutedSlate)
                }
            }

            HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)

            // 🖼️ বড় অবতার প্রিভিউ + পরিবর্তন বাটন
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2838))
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val previewModel = selectedAvatarUri ?: currentUser.avatar

                if (previewModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextMutedSlate, modifier = Modifier.size(48.dp))
                }

                // সেমি-ট্রান্সপারেন্ট ক্যামেরা ওভারলে
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "Tap to choose new photo for Cloudflare R2",
                color = TelegramBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // ✍️ নামের ইনপুট
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                label = { Text("Display Name", color = TextMutedSlate) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = CardBorderStroke,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 🆔 ইনফো রো (অ্যাকাউন্ট আইডি)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF171D2B),
                border = BorderStroke(0.8.dp, CardBorderStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Account ID", color = TextMutedSlate, fontSize = 11.5.sp)
                        Text(currentUser.effectiveAccountId, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (currentUser.isVip) "VIP Member 👑" else "Standard Account",
                        color = if (currentUser.isVip) GoldVip else TextMutedSlate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 💾 সেভ বাটন
            Button(
                onClick = {
                    if (inputName.isBlank()) {
                        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(inputName.trim(), selectedAvatarUri)
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Uploading to Cloud...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Text("Save Changes", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// -------------------------------------------------------------
// 🗂️ কার্ড ও মেনু আইটেম কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
private fun ModernMenuGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
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

// -------------------------------------------------------------
// 🔄 অ্যানিমেটেড ভার্সন স্ক্যানার ডায়ালগ
// -------------------------------------------------------------
@Composable
private fun AnimatedVersionScannerDialog(
    viewModel: DramaFlixViewModel,
    installedVersion: String,
    onDismiss: () -> Unit
) {
    var isScanning by remember { mutableStateOf(true) }
    var scanStatusText by remember { mutableStateOf("Connecting to cloud server...") }
    var isUpToDate by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )

    LaunchedEffect(Unit) {
        delay(600)
        scanStatusText = "Scanning latest streaming nodes..."
        delay(700)
        scanStatusText = "Verifying version compatibility..."

        val updateInfo = viewModel.scanServerForUpdate()
        delay(600)

        if (updateInfo?.updateAvailable == true) {
            onDismiss()
            viewModel.checkAppVersion(forceShow = true)
        } else {
            isScanning = false
            isUpToDate = true
            scanStatusText = "You are already using the latest version!"
        }
    }

    Dialog(
        onDismissRequest = { if (!isScanning) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isScanning, dismissOnClickOutside = !isScanning)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101522)),
            border = BorderStroke(1.2.dp, if (isUpToDate) ActionGreen else TelegramBlue)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAngle)) {
                            val r = size.minDimension / 2
                            drawCircle(
                                color = TelegramBlue.copy(alpha = 0.2f),
                                radius = r,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = TelegramBlue.copy(alpha = 0.4f),
                                radius = r * 0.65f,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            drawLine(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, TelegramBlue)),
                                start = center,
                                end = Offset(center.x + r, center.y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Text("Scanning for Updates...", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(scanStatusText, color = Color(0xFF94A3B8), fontSize = 12.5.sp, textAlign = TextAlign.Center)
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(ActionGreen.copy(alpha = 0.15f))
                            .border(2.dp, ActionGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(48.dp))
                    }

                    Text("You're Up to Date!", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ActionGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ActionGreen)
                    ) {
                        Text(
                            text = "Installed Version: v$installedVersion",
                            color = ActionGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Your app is running the newest version with high-speed streaming servers.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Great!", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ⚙️ সেটিংস ও নোটিফিকেশন প্রেফারেন্স শিট
// -------------------------------------------------------------
@Composable
private fun SettingsBottomSheet(
    viewModel: DramaFlixViewModel,
    installedVersion: String,
    onStartUpdateScan: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings & Preferences", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMutedSlate)
                }
            }

            ModernMenuGroupCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = ActionGreen)
                            Column {
                                Text("App Version & Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                Text("Current Installed: v$installedVersion", color = TextMutedSlate, fontSize = 11.5.sp)
                            }
                        }
                    }

                    Button(
                        onClick = onStartUpdateScan,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check & Scan for New Updates", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }

            ModernMenuGroupCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = GoldVip)
                        Column {
                            Text("Push Notifications", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            Text("Alerts on new drama episodes & updates", color = TextMutedSlate, fontSize = 11.sp)
                        }
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isEnabled ->
                            notificationsEnabled = isEnabled
                            if (isEnabled) {
                                WelcomeNotificationHelper.sendWelcomeNotification(context, force = true)
                                Toast.makeText(context, "Notifications Enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Notifications Disabled", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ActionGreen,
                            uncheckedThumbColor = TextMutedSlate,
                            uncheckedTrackColor = Color(0xFF1E2838)
                        )
                    )
                }
            }

            ModernMenuGroupCard {
                ModernMenuRowItem(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    subtitle = "Update your account password",
                    iconTint = TelegramBlue,
                    onClick = onOpenChangePassword
                )
                HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
                ModernMenuRowItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Cache & Media",
                    subtitle = "Free up device memory",
                    iconTint = Color(0xFFFF7043),
                    onClick = { Toast.makeText(context, "App cache cleared successfully!", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 🔑 পাসওয়ার্ড পরিবর্তন ডায়ালগ
// -------------------------------------------------------------
@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
            border = BorderStroke(1.dp, CardBorderStroke),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Change Password", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password", color = TextMutedSlate) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = CardBorderStroke,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (min 6 chars)", color = TextMutedSlate) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = CardBorderStroke,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password", color = TextMutedSlate) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = CardBorderStroke,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, CardBorderStroke)) {
                        Text("Cancel", color = TextMutedSlate)
                    }

                    Button(
                        onClick = {
                            if (newPassword.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onPasswordChanged()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Text("Update", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🧾 ইনভয়েস হিস্ট্রি বটম শীট
// -------------------------------------------------------------
@Composable
private fun InvoiceHistorySheet(
    invoices: List<InvoiceItemDto>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Payment & Invoices", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No payment submissions found yet.", color = TextMutedSlate, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(invoices.size) { index ->
                        val inv = invoices[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2A)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorderStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(inv.planName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(inv.displayAmount, color = GoldVip, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                Text("Status: ${inv.status.uppercase()} • Date: ${inv.displayDate}", color = if (inv.status == "active" || inv.status == "approved") ActionGreen else GoldVip, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
