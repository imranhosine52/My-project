@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadOptionDto

// 🎯 আসল ভিডিও ট্র্যাক মডেল
data class RealVideoTrack(
    val height: Int,
    val width: Int,
    val bitrate: Int,
    val label: String,
    val isAuto: Boolean = false
)

// =========================================================================
// 🎛️ ১. ExoPlayer-এর আসল ভিডিও কোয়ালিটি বটম শীট (১০০% ডায়নামিক)
// =========================================================================
@Composable
fun ShortsQualitySelectionSheet(
    availableTracks: List<RealVideoTrack>, // 👈 ExoPlayer থেকে প্রাপ্ত লাইভ ট্র্যাকসমূহ
    currentSelectedHeight: Int,          // 0 = Auto
    onSelectQuality: (targetHeight: Int, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // হেডার বার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Select Video Quality",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // যদি ভিডিওতে কোনো মাল্টিপল রেজোলিউশন না থাকে
            if (availableTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This video is playing in Original quality (No other resolutions available).",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                availableTracks.forEach { track ->
                    val isSelected = if (track.isAuto) currentSelectedHeight == 0 else currentSelectedHeight == track.height

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1A2A38) else Color(0xFF1A1F2C),
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.6.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF28344A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectQuality(track.height, if (track.isAuto) "Auto" else "${track.height}P")
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = track.label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (track.isAuto) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Automatically switches based on your network speed",
                                        color = Color(0xFF8E95A5),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// =========================================================================
// ⏱️ ২. স্পিড সিলেকশন বটম শীট
// =========================================================================
@Composable
fun ShortsSpeedSelectionSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = Color(0xFF00E5FF))
                    Text("Playback Speed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            speeds.forEach { spd ->
                val isSelected = (currentSpeed == spd)
                val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF1A2A38) else Color(0xFF1A1F2C),
                    border = BorderStroke(
                        width = if (isSelected) 1.dp else 0.6.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF28344A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectSpeed(spd)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// =========================================================================
// 📥 ৩. আসল ডাউনলোড অপশন বটম শীট (শুধুমাত্র API রেসপন্স অনুযায়ী দেখাবে)
// =========================================================================
@Composable
fun MultiQualityDownloadSheet(
    episodeTitle: String,
    options: List<DownloadOptionDto>, // 👈 কোনো ফেক/ডামি ডাটা থাকবে না
    onSelectDownload: (DownloadOptionDto) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Download Episode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episodeTitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // যদি সার্ভারে কোনো ডাউনলোড অপশন না থাকে
            if (options.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Direct download is not available for this episode.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                options.forEach { opt ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(1.dp, Color(0xFF28344A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectDownload(opt)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = opt.quality.ifBlank { "Download Video" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "High-speed direct MP4 link",
                                        color = Color(0xFF8E95A5),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // সার্ভার সাইজ পাঠালেই কেবল সাইজের ব্যাজ দেখাবে
                            if (opt.size.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF00D166).copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, Color(0xFF00D166))
                                ) {
                                    Text(
                                        text = opt.size,
                                        color = Color(0xFF00E676),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
