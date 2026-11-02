package com.example.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.shorts.RealVideoTrack

/**
 * 🎛️ ভিডিও কোয়ালিটি সিলেকশন সাইড ড্রয়ার (Auto, 360p, 480p, 720p, 1080p এবং VIP লক ইন্ডিকেটর সহ)
 */
@Composable
fun PlayerQualityDrawer(
    availableVideoTracks: List<RealVideoTrack>,
    currentSelectedHeight: Int,
    hasMultipleQualities: Boolean,
    isVip: Boolean,
    onSelectQuality: (targetHeight: Int, label: String) -> Unit,
    onNavigateToVip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 16.dp, start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ১. হেডার
        item {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "QUALITY",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(2.dp)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ২. রেজোলিউশন ট্র্যাক তালিকা
        items(availableVideoTracks) { track ->
            val isSelected = if (track.isAuto) currentSelectedHeight == 0 else currentSelectedHeight == track.height
            val isTrackVipLocked = hasMultipleQualities && !isVip && (track.height >= 720)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clickable {
                        if (isTrackVipLocked) {
                            Toast.makeText(
                                context,
                                "👑 720p and higher qualities are exclusive to VIP members!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateToVip()
                        } else {
                            onSelectQuality(
                                track.height,
                                if (track.isAuto) "Auto" else "${track.height}P"
                            )
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                if (isTrackVipLocked) {
                    MiniCrownVector(tint = Color(0xFFFFB300))
                }

                Text(
                    text = track.label,
                    color = when {
                        isSelected -> Color(0xFF00E5FF)
                        isTrackVipLocked -> Color(0xFFFFB300)
                        else -> Color.White
                    },
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected || isTrackVipLocked) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
