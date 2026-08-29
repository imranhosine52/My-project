package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.UnifiedAdManager
import com.example.ui.VipCrownVectorIcon
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.GoogleSignInButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAdminAdsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshVipStatusAndProfile()
    }

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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title
            Text(
                text = "My Profile",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            // User Identity & Card Section
            if (authState.isLoggedIn && authState.userProfile != null) {
                val user = authState.userProfile!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_profile_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, if (vipState.isVip) GoldVip else BorderDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // User Avatar
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(TealAccent, RedAccent))),
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
                                        fontSize = 24.sp,
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
                                        VipCrownVectorIcon(modifier = Modifier.size(20.dp, 15.dp))
                                    }
                                }

                                if (!user.email.isNullOrBlank()) {
                                    Text(
                                        text = user.email,
                                        color = TextSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }

                                // 8-Digit UID Pill with 1-Click Copy
                                val uid = user.effectiveAccountId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceVariantDark)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("PlayDramaFlix UID", uid))
                                            Toast.makeText(context, "UID $uid copied!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "UID: $uid",
                                        color = TealAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy UID",
                                        tint = TealAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // VIP Membership Status bar inside user card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (vipState.isVip) Color(0xFF2B2105) else SurfaceVariantDark)
                                .border(
                                    width = 1.dp,
                                    color = if (vipState.isVip) GoldVip else BorderDark,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (vipState.isVip) "${vipState.planName ?: "VIP Pass"} Active" else "Free Account",
                                        color = if (vipState.isVip) GoldVip else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (vipState.isVip) "${vipState.daysRemaining} days remaining" else "Upgrade for ad-free & full 1080p",
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }

                                if (!vipState.isVip) {
                                    Button(
                                        onClick = onNavigateToVip,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldVip),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Upgrade", color = GoldButtonText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Not logged in card -> 1-Click Native Google Sign-In Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(TealAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "Sign in to PlayDramaFlix",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Sync your watchlist, save playback position across devices, and manage VIP passes.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        GoogleSignInButton(
                            text = "Continue with Google",
                            isLoading = authState.isLoading,
                            onClick = {
                                viewModel.signInWithGoogle(context) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showAuthDialog = true
                                    }
                                }
                            }
                        )

                        OutlinedButton(
                            onClick = { showAuthDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealAccent)
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In / Register with Email", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Quick Menu Items
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileMenuItem(
                        icon = Icons.Default.WorkspacePremium,
                        title = "VIP Subscription Plans",
                        subtitle = "Zero ads, all episodes, 1080p streaming",
                        iconTint = GoldVip,
                        onClick = onNavigateToVip
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.8.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.Bookmark,
                        title = "My Watchlist",
                        subtitle = "View saved dramas and favorites",
                        iconTint = TealAccent,
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.8.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Check for Updates",
                        subtitle = "Get latest APK version & features",
                        iconTint = Color(0xFF00E676),
                        onClick = { viewModel.checkAppVersion() }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.8.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.AdsClick,
                        title = "Ad Network & Monetization",
                        subtitle = "Adsterra Smartlink, Popunder & Start.io",
                        iconTint = TealAccent,
                        onClick = { showAdminAdsDialog = true }
                    )
                }
            }

            // Sign Out Option if logged in
            if (authState.isLoggedIn) {
                OutlinedButton(
                    onClick = { viewModel.signOut(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp))
                        Text(text = "Sign Out", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // App Version Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PlayDramaFlix App v2.0.0 • playdramaflix.com",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
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
fun AdminAdSettingsDialog(
    viewModel: DramaFlixViewModel,
    isVip: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val liveAdConfig by UnifiedAdManager.adConfigState.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = TealAccent, modifier = Modifier.size(24.dp))
                Text("Ad Management & Verification", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• Primary Network: ${liveAdConfig.primaryNetwork.uppercase()}", color = TealAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("• Fallback Network: ${liveAdConfig.fallbackNetwork.uppercase()}", color = TextSecondary, fontSize = 12.sp)
                        Text("• Verification Timer: ${liveAdConfig.rules?.timerSeconds ?: 10} seconds", color = GoldVip, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Rewarded Duration: ${liveAdConfig.rules?.rewardedUnlockHours ?: 2} Hours", color = TextSecondary, fontSize = 12.sp)
                        Text("• VIP Bypass Active: ${if (isVip) "YES (All Ads Bypassed)" else "NO (Standard User)"}", color = if (isVip) GoldVip else TextMuted, fontSize = 12.sp)
                    }
                }

                // Adsterra Direct Link, Popunder & Social Bar Status
                Text("Adsterra Configuration", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val directLink = liveAdConfig.adsterra?.effectiveDirectLink ?: "None (Using fallback)"
                val popunderUrl = liveAdConfig.adsterra?.popunderUrl ?: "None (Disabled)"
                val socialBarStatus = if (liveAdConfig.adsterra?.socialBarEnabled != false) "Enabled (Active In-App)" else "Disabled"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF131A26))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Direct Smartlink (In-App Browsing):", color = TextMuted, fontSize = 11.sp)
                    Text(directLink, color = TealAccent, fontSize = 11.5.sp, maxLines = 2)
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    Text("Social Bar Ads:", color = TextMuted, fontSize = 11.sp)
                    Text(socialBarStatus, color = if (liveAdConfig.adsterra?.socialBarEnabled != false) Color(0xFF00E676) else TextMuted, fontSize = 11.5.sp)
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    Text("Popunder URL:", color = TextMuted, fontSize = 11.sp)
                    Text(popunderUrl, color = TextSecondary, fontSize = 11.5.sp, maxLines = 2)
                    Text("Frequency: Every ${liveAdConfig.adsterra?.popunderFrequency ?: 3} page transitions", color = TextMuted, fontSize = 11.sp)
                }

                // Test Actions
                Button(
                    onClick = {
                        val opened = UnifiedAdManager.openAdsterraDirectLink(
                            context = context,
                            isVip = false,
                            verificationSeconds = 10,
                            onVerified = {
                                Toast.makeText(context, "Verification Test Successful!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        if (opened) {
                            Toast.makeText(context, "Direct Smartlink opened in In-App Browser!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No active Direct Link configured.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Text("Test In-App Direct Smartlink (10s)", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        UnifiedAdManager.showPopunderIfEligible(context, isVip = false)
                        Toast.makeText(context, "Popunder transition triggered!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text("Test Page Transition Popunder", color = TextPrimary, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TealAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
