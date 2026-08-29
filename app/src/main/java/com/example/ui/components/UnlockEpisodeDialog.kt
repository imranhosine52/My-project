package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
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
import com.example.ads.StartIoAdManager
import com.example.data.model.EpisodeDto
import com.example.ui.VipCrownVectorIcon
import com.example.ui.theme.*

/**
 * UnlockEpisodeDialog
 * Rewarded Video Ad Episode Unlock Dialog with 2 options:
 * 1. "Watch Ad to Unlock (Free)" -> Triggers Start.io Rewarded Video Ad, unlocking for 2 Hours.
 * 2. "Upgrade to VIP (Ad-Free All)" -> Navigates to VIP Subscription (/pricing).
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

    Dialog(
        onDismissRequest = { if (!isAdLoading) onDismiss() },
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

                // Lock icon illustration badge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF2A2000), Color(0xFF161200))))
                        .border(1.5.dp, GoldVip, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = GoldVip,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Title & Description
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Unlock Episode ${episode.episodeNumber}",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Watch a quick sponsored video to unlock Episode ${episode.episodeNumber} for 2 full hours, or upgrade to VIP for permanent ad-free streaming.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                HorizontalDivider(color = BorderDark.copy(alpha = 0.6f))

                // Option 1: 🎬 Watch Ad to Unlock (Free) -> 2 Hours
                Button(
                    onClick = {
                        isAdLoading = true
                        StartIoAdManager.showRewardedVideo(
                            context = context,
                            isVip = isVip,
                            onRewardUnlocked = {
                                isAdLoading = false
                                onWatchAdSuccess()
                            },
                            onAdClosed = {
                                isAdLoading = false
                            }
                        )
                    },
                    enabled = !isAdLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("watch_ad_unlock_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealAccent,
                        disabledContainerColor = TealAccent.copy(alpha = 0.5f)
                    )
                ) {
                    if (isAdLoading) {
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
                                imageVector = Icons.Default.PlayCircle,
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
