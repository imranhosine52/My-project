@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.UnifiedAdManager
import com.example.ui.VipCrownVectorIcon
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00D166)
private val BannerGreen = Color(0xFF06331E)
private val BannerTextGreen = Color(0xFF00E676)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var showAuthDialog by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var showAdminAdsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshVipStatusAndProfile()
    }

    val guestId = remember { "535" + (100000..999999).random() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // -------------------------------------------------------------
            // Top Header: User Profile or Guest Login Header
            // -------------------------------------------------------------
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
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(TealAccent, ActionGreen))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user.avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(user.avatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = user.displayName,
                                modifier = Modifier.fillMaxSize(),
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
                                VipCrownVectorIcon(modifier = Modifier.size(18.dp, 14.dp))
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
                                    Toast.makeText(context, "UID copied!", Toast.LENGTH_SHORT).show()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "ID: $guestId",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(SurfaceDark, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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

            // -------------------------------------------------------------
            // Official Website Banner
            // -------------------------------------------------------------
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
                Icon(Icons.Default.Campaign, contentDescription = null, tint = BannerTextGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "Official website: https://playdramaflix.com",
                    color = BannerTextGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // -------------------------------------------------------------
            // Premium & Tasks Group
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.WorkspacePremium,
                        title = "Get Premium",
                        subtitle = "No ads • 1080P quality • Multi-downloads",
                        iconTint = GoldVip,
                        onClick = onNavigateToVip
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Checklist,
                        title = "Tasks for Free Premium",
                        subtitle = "Watch 1 quick ad to unlock 2 hours full VIP",
                        iconTint = Color(0xFFFFA726),
                        onClick = {
                            UnifiedAdManager.showRewardedVideo(
                                context = context,
                                onRewarded = {
                                    Toast.makeText(context, "🎉 2 Hours Free VIP Unlocked!", Toast.LENGTH_SHORT).show()
                                },
                                onFailed = {
                                    Toast.makeText(context, "Ad not ready, try again in a moment", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }

            // -------------------------------------------------------------
            // Library & History Group
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.BookmarkBorder,
                        title = "My List",
                        badge = watchlistState.savedDramas.size.toString(),
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.ThumbUpOffAlt,
                        title = "My Likes",
                        badge = "1",
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)

                    // Watch History Header & Carousel
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                Text("Watch History", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }

                        val recentDramas = homeState.recentlyAdded.take(8)
                        if (recentDramas.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(recentDramas) { drama ->
                                    Column(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .clickable { /* Play drama */ }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SurfaceVariantDark)
                                        ) {
                                            AsyncImage(
                                                model = drama.bannerUrl ?: drama.posterUrl,
                                                contentDescription = drama.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Progress Bar
                                            LinearProgressIndicator(
                                                progress = { 0.65f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .height(3.dp),
                                                color = ActionGreen,
                                                trackColor = Color.Black.copy(alpha = 0.5f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = drama.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.NotificationsNone,
                        title = "Messages",
                        badge = "3",
                        badgeColor = Color.Red,
                        onClick = { Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show() }
                    )
                }
            }

            // -------------------------------------------------------------
            // Community & Social Group (Telegram Community)
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.Telegram,
                        title = "Community",
                        subtitle = "Join our Telegram channel & group",
                        badge = "Telegram",
                        badgeColor = Color(0xFF29B6F6),
                        onClick = {
                            try {
                                val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/playdramaflix"))
                                context.startActivity(telegramIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Telegram link: t.me/playdramaflix", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.AddCircleOutline,
                        title = "Posts",
                        badge = "Coming Soon",
                        onClick = {
                            Toast.makeText(context, "Community Posts feature is coming soon!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = "My Comments",
                        badge = "0",
                        onClick = { Toast.makeText(context, "No comments recorded", Toast.LENGTH_SHORT).show() }
                    )
                }
            }

            // -------------------------------------------------------------
            // Settings & Invoices Group
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.ReceiptLong,
                        title = "Payment & Invoices",
                        subtitle = "View VIP transaction history",
                        onClick = { showInvoiceSheet = true }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Public,
                        title = "In-App Web Browser",
                        subtitle = "Fast mobile web browsing",
                        onClick = onNavigateToBrowser
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        title = "Settings & Updates",
                        badge = "New Version",
                        badgeColor = BannerTextGreen,
                        onClick = { viewModel.checkAppVersion() }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Tune,
                        title = "Ad Network Management",
                        onClick = { showAdminAdsDialog = true }
                    )
                }
            }

            // -------------------------------------------------------------
            // Sign Out Option (If logged in)
            // -------------------------------------------------------------
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

            // App Version Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PlayDramaFlix v2.1.0 • Built with ❤️ for Asian Drama Fans",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // --- Dialogs ---
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

        if (showAdminAdsDialog) {
            AdminAdSettingsDialog(
                viewModel = viewModel,
                isVip = vipState.isVip,
                onDismiss = { showAdminAdsDialog = false }
            )
        }
    }
}

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
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceHistorySheet(
    invoices: List<com.example.data.model.InvoiceItemDto>,
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
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(invoices) { inv ->
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
