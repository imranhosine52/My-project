@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.shorts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadOptionDto
import com.example.ui.screens.player.RealAudioTrack
import com.example.ui.screens.player.RealSubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// 🎯 Video Track Model
data class RealVideoTrack(
    val height: Int,
    val width: Int,
    val bitrate: Int,
    val label: String,
    val isAuto: Boolean = false
)

@Composable
private fun MiniCrownVector(modifier: Modifier = Modifier, tint: Color = Color(0xFFF6D38B)) {
    Canvas(modifier = modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.30f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.50f, 0f)
            lineTo(w * 0.72f, h * 0.65f)
            lineTo(w, h * 0.30f)
            lineTo(w * 0.85f, h * 0.95f)
            lineTo(w * 0.15f, h * 0.95f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

private suspend fun fetchLiveFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val conn = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
        }
        val length = conn?.contentLengthLong ?: 0L
        conn?.disconnect()
        if (length > 0) length else 0L
    } catch (_: Exception) {
        0L
    }
}

// =========================================================================
// 🎛️ ১. ভিডিও কোয়ালিটি সিলেকশন শিট (360p/480p ফ্রি • 720p+ VIP লকড)
// =========================================================================
@Composable
fun ShortsQualitySelectionSheet(
    availableTracks: List<RealVideoTrack>,
    currentSelectedHeight: Int,
    hasMultipleQualities: Boolean = false,
    isVip: Boolean = false,
    onNavigateToVip: () -> Unit = {},
    onSelectQuality: (targetHeight: Int, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

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
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Select Video Quality",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            if (availableTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Playing in Original quality (No other resolutions available).",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                availableTracks.forEach { track ->
                    val isSelected = if (track.isAuto) currentSelectedHeight == 0 else currentSelectedHeight == track.height
                    
                    // 🎯 রুল: মাল্টিপল কোয়ালিটি থাকলে এবং 720p+ হলে ফ্রি ইউজারদের জন্য লক থাকবে
                    val isTrackVipLocked = hasMultipleQualities && !isVip && (track.height >= 720)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected -> Color(0xFF1A2A38)
                            isTrackVipLocked -> Color(0xFF241C10)
                            else -> Color(0xFF1A1F2C)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.6.dp,
                            color = when {
                                isSelected -> Color(0xFF00E5FF)
                                isTrackVipLocked -> Color(0xFFFFB300).copy(alpha = 0.6f)
                                else -> Color(0xFF28344A)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isTrackVipLocked) {
                                    Toast.makeText(
                                        context,
                                        "👑 720p and higher qualities are exclusive to VIP members!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onNavigateToVip()
                                    onDismiss()
                                } else {
                                    onSelectQuality(track.height, if (track.isAuto) "Auto" else "${track.height}P")
                                    onDismiss()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isTrackVipLocked) {
                                    MiniCrownVector(tint = Color(0xFFFFB300))
                                }

                                Column {
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
                                    if (track.isAuto) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Automatically adapts based on network speed",
                                            color = Color(0xFF8E95A5),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// =========================================================================
// 🎵 ২. অডিও ট্র্যাক সিলেকশন শিট (Multi-Audio Support)
// =========================================================================
@Composable
fun ShortsAudioSelectionSheet(
    audioTracks: List<RealAudioTrack>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (RealAudioTrack) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

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
                        imageVector = Icons.Outlined.Audiotrack,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Select Audio Language",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            audioTracks.forEach { audioTrack ->
                val isSelected = (selectedAudioTrackId == audioTrack.id)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF1A2A38) else Color(0xFF1A1F2C),
                    border = BorderStroke(
                        width = if (isSelected) 1.2.dp else 0.6.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF28344A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectAudioTrack(audioTrack)
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
                        Text(
                            text = audioTrack.label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// =========================================================================
// 💬 ৩. সাবটাইটেল সিলেকশন শিট (Multi-Subtitle Support)
// =========================================================================
@Composable
fun ShortsSubtitleSelectionSheet(
    subtitleTracks: List<RealSubtitleTrack>,
    selectedSubtitleTrackId: String,
    onSelectSubtitleTrack: (RealSubtitleTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

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
                        imageVector = Icons.Outlined.ClosedCaption,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Subtitles",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            // Off Option
            val isOffSelected = (selectedSubtitleTrackId == "off")
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOffSelected) Color(0xFF1A2A38) else Color(0xFF1A1F2C),
                border = BorderStroke(
                    width = if (isOffSelected) 1.2.dp else 0.6.dp,
                    color = if (isOffSelected) Color(0xFF00E5FF) else Color(0xFF28344A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectSubtitleTrack(null)
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
                    Text(
                        text = "Off",
                        color = if (isOffSelected) Color(0xFF00E5FF) else Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    if (isOffSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            subtitleTracks.forEach { subTrack ->
                val isSelected = (selectedSubtitleTrackId == subTrack.id)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF1A2A38) else Color(0xFF1A1F2C),
                    border = BorderStroke(
                        width = if (isSelected) 1.2.dp else 0.6.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF28344A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectSubtitleTrack(subTrack)
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
                        Text(
                            text = subTrack.label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// =========================================================================
// ⏱️ ৪. স্পিড সিলেকশন বটম শিট
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

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
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Playback Speed",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
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
                        width = if (isSelected) 1.2.dp else 0.6.dp,
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
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// =========================================================================
// 📥 ৫. মাল্টি-কোয়ালিটি ডাউনলোড শীট
// =========================================================================
@Composable
fun MultiQualityDownloadSheet(
    episodeTitle: String,
    options: List<DownloadOptionDto>,
    onSelectDownload: (DownloadOptionDto) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val liveSizesMap = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(options) {
        options.forEach { opt ->
            if (opt.size.isNotBlank() && (opt.size.contains("MB", true) || opt.size.contains("GB", true))) {
                liveSizesMap[opt.url] = opt.size
            } else if (opt.url.isNotBlank()) {
                coroutineScope.launch {
                    val bytes = fetchLiveFileSize(opt.url)
                    if (bytes > 0L) {
                        val mb = bytes / (1024.0 * 1024.0)
                        val formatted = if (mb >= 1024.0) {
                            String.format(Locale.US, "%.2f GB", mb / 1024.0)
                        } else {
                            String.format(Locale.US, "%.1f MB", mb)
                        }
                        liveSizesMap[opt.url] = formatted
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Download Episode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episodeTitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

            if (options.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
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
                        shape = RoundedCornerShape(10.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = opt.quality.ifBlank { "Download Video" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val finalSizeText = liveSizesMap[opt.url]
                                ?: opt.size.takeIf { it.contains("MB") || it.contains("GB") }
                                ?: "Loading..."

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF00D166).copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, Color(0xFF00D166))
                            ) {
                                Text(
                                    text = finalSizeText,
                                    color = Color(0xFF00E676),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
