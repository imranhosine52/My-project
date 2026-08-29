package com.example.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ads.UnifiedAdManager
import com.example.data.model.EpisodeDto
import com.example.ui.VipCrownVectorIcon
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * UnlockEpisodeDialog
 * Rewarded Ad Episode Unlock Dialog supporting:
 * 1. Adsterra Direct Smartlink with a 10-Second Countdown Verification Engine.
 * 2. Start.io Rewarded Video mediation with automatic dynamic fallback to Adsterra Smartlink.
 * 3. 👑 Strict VIP Bypass: VIP members bypass all ads, dialogs, and timers completely.
 */
@Composable
fun UnlockEpisodeDialog(
    episode: EpisodeDto,
    dramaSlug: String,
    isVip: Boolean,
    onDismiss: () -> Unit,
    onWatchAdSuccess: () -> Unit,
    onUpgradeVipClick: () -> Unit
) {
    val context = LocalContext.current
    var isAdLoading by remember { mutableStateOf(false) }
    var isVerifyingTimer by remember { mutableStateOf(false) }
    val timerConfigSeconds = remember { UnifiedAdManager.getVerificationTimerSeconds() }
    var remainingSeconds by remember { mutableIntStateOf(timerConfigSeconds) }
    val unlockHours = remember { UnifiedAdManager.getUnlockDurationHours() }

    // Proactively preload Rewarded Video Ad when modal appears
    LaunchedEffect(Unit) {
        if (!isVip && UnifiedAdManager.isAdsGloballyEnabled()) {
            UnifiedAdManager.preloadRewardedVideo(context)
        }
    }

    // 10-Second Countdown Verification Loop
    LaunchedEffect(isVerifyingTimer) {
        if (isVerifyingTimer) {
            remainingSeconds = timerConfigSeconds
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds -= 1
            }
            // Timer completed successfully!
            isVerifyingTimer = false
            Toast.makeText(context, "✓ Ad visit verified! Unlocking Episode ${episode.episodeNumber}", Toast.LENGTH_SHORT).show()
            onWatchAdSuccess()
        }
    }

    // Function to trigger Adsterra Direct Link with Verification Timer
    fun triggerAdsterraVerificationFlow() {
        val linkOpened = UnifiedAdManager.openAdsterraDirectLink(context, isVip = isVip)
        if (linkOpened) {
            isAdLoading = false
            isVerifyingTimer = true
            Toast.makeText(
                context,
                "Please browse the sponsor page for $timerConfigSeconds seconds to unlock.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            isAdLoading = false
            // Fallback: If no direct link available, grant access directly
            Toast.makeText(context, "Enjoy your episode!", Toast.LENGTH_SHORT).show()
            onWatchAdSuccess()
        }
    }

    // Master function to execute ad unlock based on primary and fallback configuration
    fun executeAdUnlockFlow() {
        if (isVip || !UnifiedAdManager.isAdsGloballyEnabled()) {
            onWatchAdSuccess()
            return
        }

        val isAdsterraPrimary = UnifiedAdManager.isAdsterraPrimary()
        val isDirectLinkAvailable = UnifiedAdManager.isDirectLinkAvailable(isVip)

        if (isAdsterraPrimary && isDirectLinkAvailable) {
            // Direct Link is Primary
            triggerAdsterraVerificationFlow()
        } else {
            // Start.io Rewarded Video is Primary or Direct link fallback
            isAdLoading = true
            UnifiedAdManager.showRewardedVideo(
                context = context,
                isVip = isVip,
                onRewardUnlocked = {
                    isAdLoading = false
                    onWatchAdSuccess()
                },
                onAdNotReadyOrFailed = { reason ->
                    Log.w("UnlockEpisodeDialog", "Start.io ad failed ($reason), executing failover to Adsterra Direct Link...")
                    if (isDirectLinkAvailable) {
                        triggerAdsterraVerificationFlow()
                    } else {
                        isAdLoading = false
                        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                    }
                },
                onAdClosed = { rewardEarned ->
                    isAdLoading = false
                    if (!rewardEarned && !isVip && !isVerifyingTimer) {
                        Toast.makeText(
                            context,
                            "Ad closed early. Watch full ad to unlock Episode ${episode.episodeNumber}.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!isAdLoading && !isVerifyingTimer) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
                .testTag("unlock_episode_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.2.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Badge & Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoldVip.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "EPISODE ${episode.episodeNumber} LOCKED",
                            color = GoldVip,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (!isVerifyingTimer && !isAdLoading) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lock / Timer illustration badge
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isVerifyingTimer) listOf(Color(0xFF00302E), Color(0xFF001A19))
                                else listOf(Color(0xFF2A2000), Color(0xFF161200))
                            )
                        )
                        .border(
                            1.5.dp,
                            if (isVerifyingTimer) TealAccent else GoldVip,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVerifyingTimer) {
                        // Countdown Ring Indicator
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (remainingSeconds.toFloat() / timerConfigSeconds.toFloat()).coerceIn(0f, 1f) },
                                color = TealAccent,
                                trackColor = Color(0xFF1B3038),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "${remainingSeconds}s",
                                color = TealAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = GoldVip,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Title & Description
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isVerifyingTimer) "Verifying Ad Visit..." else "Unlock Episode ${episode.episodeNumber}",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isVerifyingTimer) {
                            "Please keep the sponsor page open. Verifying ad visit in ${remainingSeconds} seconds..."
                        } else {
                            "Watch a sponsored ad or visit sponsor to unlock Episode ${episode.episodeNumber} for $unlockHours full hours, or upgrade to VIP for permanent ad-free streaming."
                        },
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                HorizontalDivider(color = BorderDark.copy(alpha = 0.6f))

                // Option 1: 🎬 Watch Ad to Unlock (Free) / Verifying Button
                Button(
                    onClick = { executeAdUnlockFlow() },
                    enabled = !isAdLoading && !isVerifyingTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("watch_ad_unlock_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealAccent,
                        disabledContainerColor = if (isVerifyingTimer) Color(0xFF183B38) else TealAccent.copy(alpha = 0.4f)
                    )
                ) {
                    if (isVerifyingTimer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TealAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Verifying ad visit... (${remainingSeconds}s)",
                                color = TealAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isAdLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Loading Ad...",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (UnifiedAdManager.isAdsterraPrimary()) Icons.Default.OpenInBrowser else Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Watch Ad to Unlock (Free)",
                                color = Color.Black,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Option 2: 👑 Upgrade to VIP (Ad-Free All / Pricing)
                OutlinedButton(
                    onClick = onUpgradeVipClick,
                    enabled = !isAdLoading && !isVerifyingTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("upgrade_vip_unlock_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, GoldVip),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF2A2000).copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VipCrownVectorIcon(modifier = Modifier.size(20.dp, 16.dp))
                        Text(
                            text = "Upgrade to VIP (Ad-Free All)",
                            color = GoldVip,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
