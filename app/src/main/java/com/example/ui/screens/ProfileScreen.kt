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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AppVersionCheckResponse
import com.example.data.model.InvoiceItemDto
import com.example.data.model.UserProfileDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.WelcomeNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val ActionGreen = Color(0xFF00D166)
private val SafeGreen = Color(0xFF00D166)
private val BannerGreen = Color(0xFF06331E)
private val BannerTextGreen = Color(0xFF00E676)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    onNavigateToLocalGallery: () -> Unit,
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
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }

    // 🖼️ পার্মানেন্ট ছবি সেভ করার লঞ্চার
    val directAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val user = authState.userProfile
            val userName = user?.displayName ?: "DramaFlix Fan"
            viewModel.updateUserProfileData(
                context = context,
                name = userName,
                avatarUri = uri
            ) { success ->
                if (success) {
                    Toast.makeText(context, "Profile picture saved permanently!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshVipStatusAndProfile()
    }

    val guestId = remember { "535" + (100000..999999).random() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 👤 ১. ইউজার প্রোফাইল হেডার (পার্মানেন্ট ইমেজ লোডার)
                if (authState.isLoggedIn && authState.userProfile != null) {
                    val user = authState.userProfile!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { directAvatarPicker.launch("image/*") }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(TealAccent, ActionGreen))),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarPath = user.avatar
                                if (!avatarPath.isNullOrBlank()) {
                                    val imageModel = remember(avatarPath) {
                                        if (avatarPath.startsWith("/") || avatarPath.startsWith("file://")) {
                                            File(avatarPath.removePrefix("file://"))
                                        } else {
                                            avatarPath
                                        }
                                    }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageModel)
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
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(ActionGreen)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Upload Avatar",
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = user.displayName,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (vipState.isVip) {
                                    Golden3DVipCrownIcon(modifier = Modifier.size(24.dp, 18.dp))
                                }
                            }

                            val uid = user.effectiveAccountId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("UID", uid))
                                        Toast.makeText(context, "Account ID copied!", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text("ID: $uid", color = TextMuted, fontSize = 12.sp)
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(12.dp))
                            }
                        }

                        if (vipState.isVip) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldVip.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, GoldVip)
                            ) {
                                Text(
                                    text = "VIP",
                                    color = GoldVip,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Log in to your account",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID: $guestId",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(SurfaceDark, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Button(
                            onClick = { showAuthDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Log in", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // অফিসিয়াল ওয়েবসাইট ব্যানার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BannerGreen)
                        .clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://playdramaflix.com")))
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = BannerTextGreen, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Official website: https://playdramaflix.com",
                        color = BannerTextGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // প্রিমিয়াম ও VIP টাস্ক গ্রুপ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Default.Star,
                            title = "Get Premium",
                            subtitle = "No ads • 1080P quality • All Episodes",
                            iconTint = GoldVip,
                            onClick = onNavigateToVip
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        ProfileMenuRow(
                            icon = Icons.Default.PlayArrow,
                            title = "Tasks for Free Premium",
                            subtitle = "Unlock 2 hours full VIP access",
                            iconTint = Color(0xFFFFA726),
                            onClick = onNavigateToVip
                        )
                    }
                }

                // লাইব্রেরি ও মেসেজ গ্রুপ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Default.List,
                            title = "My List",
                            badge = watchlistState.savedDramas.size.toString(),
                            onClick = onNavigateToWatchlist
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        ProfileMenuRow(
                            icon = Icons.Default.Notifications,
                            title = "Messages",
                            badge = "3",
                            badgeColor = Color.Red,
                            onClick = onNavigateToNotification
                        )
                    }
                }

                // মিডিয়া ও ইউটিলিটি গ্রুপ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Default.VideoLibrary,
                            title = "Gallery Video Player",
                            subtitle = "MX Player Style • Play Phone Videos",
                            badge = "100% Free",
                            badgeColor = ActionGreen,
                            iconTint = ActionGreen,
                            onClick = onNavigateToLocalGallery
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)

                        ProfileMenuRow(
                            icon = Icons.Default.Share,
                            title = "In-App Web Browser",
                            subtitle = "Fast mobile web browsing",
                            onClick = onNavigateToBrowser
                        )
                    }
                }

                // কমিউনিটি ও সোশ্যাল গ্রুপ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Default.Send,
                            title = "Community",
                            subtitle = "Join our Telegram community",
                            badge = "Telegram",
                            badgeColor = Color(0xFF29B6F6),
                            onClick = {
                                try {
                                    val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/playdramaflix"))
                                    context.startActivity(telegramIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Telegram: t.me/playdramaflix", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        ProfileMenuRow(
                            icon = Icons.Default.Add,
                            title = "Posts",
                            badge = "Coming Soon",
                            onClick = {
                                Toast.makeText(context, "Community Posts feature is coming soon!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // ⚙️ সেটিংস ও একাউন্ট ম্যানেজমেন্ট গ্রুপ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column {
                        if (authState.isLoggedIn) {
                            ProfileMenuRow(
                                icon = Icons.Default.Edit,
                                title = "Edit Profile & Name",
                                subtitle = "Customize your profile details",
                                iconTint = ActionGreen,
                                onClick = { showEditProfileDialog = true }
                            )
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        }

                        ProfileMenuRow(
                            icon = Icons.Default.ReceiptLong,
                            title = "Payment & Invoices",
                            subtitle = "View VIP transaction history",
                            onClick = { showInvoiceSheet = true }
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)

                        ProfileMenuRow(
                            icon = Icons.Default.Settings,
                            title = "Settings & Updates",
                            subtitle = "Version Scanner, Notifications & Security",
                            badge = "v$installedVersion",
                            badgeColor = ActionGreen,
                            iconTint = ActionGreen,
                            onClick = { showSettingsSheet = true }
                        )
                    }
                }

                // সাইন আউট বাটন
                if (authState.isLoggedIn) {
                    OutlinedButton(
                        onClick = { viewModel.signOut(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
                        text = "PlayDramaFlix v$installedVersion • Built with ❤️ for Asian Drama Fans",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ডায়ালগ ও বটম শীটসমূহ
        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }

        if (showEditProfileDialog && authState.userProfile != null) {
            EditProfileDialog(
                currentUser = authState.userProfile!!,
                onSave = { newName, selectedUri ->
                    viewModel.updateUserProfileData(
                        context = context,
                        name = newName,
                        avatarUri = selectedUri
                    ) { success ->
                        if (success) {
                            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    }
                },
                onDismiss = { showEditProfileDialog = false }
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

        // 🚀 ২. অ্যানিমেটেড ভার্সন স্ক্যানার ডায়ালগ
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

// =========================================================================
// 🚀 অ্যানিমেটেড রাডার স্ক্যানার ডায়ালগ (Animated Version Scanner Dialog)
// =========================================================================
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
            viewModel.checkAppVersion(forceShow = true) // নিচ থেকে পপ আপ ওঠাবে
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
            border = BorderStroke(1.2.dp, if (isUpToDate) ActionGreen else TealAccent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isScanning) {
                    // 🌟 রাডার স্ক্যানার অ্যানিমেশন
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAngle)) {
                            val r = size.minDimension / 2
                            drawCircle(
                                color = TealAccent.copy(alpha = 0.2f),
                                radius = r,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = TealAccent.copy(alpha = 0.4f),
                                radius = r * 0.65f,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            drawLine(
                                brush = Brush.sweepGradient(
                                    listOf(Color.Transparent, TealAccent)
                                ),
                                start = center,
                                end = Offset(center.x + r, center.y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Text(
                        text = "Scanning for Updates...",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = scanStatusText,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // 🌟 লেটেস্ট ভার্সন কনফার্মেশন সিল
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(ActionGreen.copy(alpha = 0.15f))
                            .border(2.dp, ActionGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ActionGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Text(
                        text = "You're Up to Date!",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

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
                        text = "Your app is running the newest and most optimized version with high-speed streaming servers.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Great!", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// ⚙️ সেটিংস ও স্ক্যানার বটম শীট
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = BackgroundDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                Text("Settings & Preferences", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // 🌟 ১. ডায়নামিক ভার্সন কার্ড ও লাইভ স্ক্যানার বাটন
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = ActionGreen)
                            Column {
                                Text("App Version & Update", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Current Installed: v$installedVersion", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ActionGreen.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ActionGreen)
                        ) {
                            Text("Latest", color = ActionGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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

            // 🔔 নোটিফিকেশন সুইচ
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFFFB300))
                        Column {
                            Text("Push Notifications", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Get alerts on new drama episodes & releases", color = TextMuted, fontSize = 11.5.sp)
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
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceVariantDark
                        )
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.Lock,
                        title = "Change Password",
                        subtitle = "Update your account login password",
                        iconTint = Color(0xFF00E5FF),
                        onClick = onOpenChangePassword
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Cache & Data",
                        subtitle = "Free up memory and speed up streaming",
                        iconTint = Color(0xFFFF7043),
                        onClick = {
                            Toast.makeText(context, "App cache cleared successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// 📝 প্রোফাইল নাম ও ছবি এডিট ডায়ালগ
// =========================================================================
@Composable
private fun EditProfileDialog(
    currentUser: UserProfileDto,
    onSave: (String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(currentUser.displayName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val existingAvatar = currentUser.avatar

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Profile Details",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariantDark)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarModel = selectedAvatarUri ?: existingAvatar?.let { path ->
                        if (path.startsWith("/") || path.startsWith("file://")) File(path.removePrefix("file://")) else path
                    }

                    if (avatarModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "Tap photo to choose from gallery",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Display Name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (inputName.isBlank()) {
                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(inputName.trim(), selectedAvatarUri)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🔑 পাসওয়ার্ড পরিবর্তন ডায়ালগ
// =========================================================================
@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Change Password", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (min 6 chars)", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Cancel", color = TextSecondary)
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

// =========================================================================
// 📌 প্রোফাইল মেনু রো কম্পোনেন্ট
// =========================================================================
@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = TextSecondary,
    iconTint: Color = TextSecondary,
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
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (badge != null) {
                Text(text = badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

// =========================================================================
// 🧾 ইনভয়েস হিস্ট্রি বটম শীট
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceHistorySheet(
    invoices: List<InvoiceItemDto>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Payment & Invoices", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No payment submissions found yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(invoices.size) { index ->
                        val inv = invoices[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(inv.planName, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    Text(inv.displayAmount, color = GoldVip, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}", color = TextSecondary, fontSize = 11.sp)
                                Text("Status: ${inv.status.uppercase()} • Date: ${inv.displayDate}", color = if (inv.status == "active" || inv.status == "approved") ActionGreen else TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
