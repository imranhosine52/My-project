package com.example.ui.screens.shorts

import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// 👑 🎯 পিওর নেটিভ ভেক্টর ক্রাউন আইকন (কোনো ইমোজি ছাড়া নিখুঁত লাক্সারি ডিজাইন)
@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
    Canvas(modifier = modifier.size(15.dp)) {
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

data class SheetQualityItem(
    val key: String,       // "720p", "480p", "360p"
    val label: String,     // "720P", "480P", "360P"
    val isVipOnly: Boolean,
    val defaultMb: Double
)

// ⚡ ক্লাউডফ্লেয়ার R2 থেকে ফাইলের আসল সাইজ বের করা
private suspend fun fetchLiveFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val conn = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 3000
            readTimeout = 3000
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

// ⏱️ ভিডিওর আসল সময়কাল (Duration) বের করা
private suspend fun fetchLiveVideoDuration(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(url, HashMap())
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        time
    } catch (_: Exception) {
        0L
    }
}

private fun formatSingleSize(bytes: Long, fallbackMb: Double): String {
    val effective = if (bytes > 0L) bytes else (fallbackMb * 1024 * 1024).toLong()
    val mb = effective / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1fMB", mb)
}

// mm:ss ফরম্যাট (যেমন: 01:45 বা 43:25)
private fun formatDurationDisplay(durationMs: Long, rawFallback: String?): String {
    if (durationMs > 0L) {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }
    return if (!rawFallback.isNullOrBlank() && rawFallback.contains(":")) rawFallback else "02:15"
}

@Composable
fun ShortsBatchDownloadSheet(
    title: String,
    slug: String = "",
    episodes: List<EpisodeDto>,
    isVip: Boolean = false,
    onDismiss: () -> Unit,
    onDownloadSelected: (selectedEpisodes: List<EpisodeDto>, chosenQuality: String) -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ৩টি কোয়ালিটি: 720P ভিআইপি ক্রাউন আইকনসহ, বাকি দুটি ফ্রি
    val availableQualities = remember {
        listOf(
            SheetQualityItem("720p", "720P", isVipOnly = true, defaultMb = 45.0),
            SheetQualityItem("480p", "480P", isVipOnly = false, defaultMb = 22.0),
            SheetQualityItem("360p", "360P", isVipOnly = false, defaultMb = 12.0)
        )
    }

    var selectedQuality by remember { mutableStateOf(availableQualities[0]) } // ডিফল্ট 720P
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    val realDurations = remember { mutableStateMapOf<String, Long>() }

    // ব্যাকগ্রাউন্ডে আসল সাইজ ও আসল ডিউরেশন লোডার
    LaunchedEffect(selectedQuality, episodes) {
        episodes.take(25).forEach { ep ->
            val targetUrl = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }?.url
                ?: ep.resolveDownloadUrl(slug)

            val sizeKey = "${ep.episodeId}_${selectedQuality.key}"
            if (!realFileSizes.containsKey(sizeKey) && targetUrl.isNotBlank()) {
                coroutineScope.launch {
                    val size = fetchLiveFileSize(targetUrl)
                    if (size > 0L) realFileSizes[sizeKey] = size
                }
            }

            val durKey = ep.episodeId
            if (!realDurations.containsKey(durKey) && targetUrl.isNotBlank()) {
                coroutineScope.launch {
                    val dur = fetchLiveVideoDuration(targetUrl)
                    if (dur > 0L) realDurations[durKey] = dur
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // 🎯 ১. উচ্চতা মাঝখানের দাগ বরাবর লকড (স্ক্রিনের ৫২% উচ্চতা)
                .fillMaxHeight(0.52f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                // 🎯 ২. উপর থেকে নিচে টান দিলে স্মুথ মিনিমাইজ হওয়ার জেসচার
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 12f) {
                            onDismiss()
                        }
                    }
                },
            // 🎯 উপরে কোনো ক্রপ/রাউন্ডেড কর্নার নেই (একদম ফ্ল্যাট)
            shape = RoundedCornerShape(0.dp),
            color = Color(0xFF0F1117)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 70.dp)
                ) {
                    // =============================================================
                    // 🔝 ১. ড্র্যাগ হ্যান্ডেল + টপ বার (ইমোজি ছাড়া আসল ভেক্টর ক্রাউন)
                    // =============================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720))
                    ) {
                        // মিনিমাইজ ড্র্যাগ হ্যান্ডেল
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF333C4D))
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // কোয়ালিটি চিপস (আসল ভেক্টর ক্রাউন আইকনসহ)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableQualities.forEach { q ->
                                    val isSelected = (selectedQuality.key == q.key)

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) Color(0xFF382B17) else Color(0xFF1F2430),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)
                                        ),
                                        modifier = Modifier.clickable { selectedQuality = q }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            // 👑 আসল গোল্ডেন ক্রাউন আইকন
                                            if (q.isVipOnly) {
                                                VipCrownVectorIcon(
                                                    tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52)
                                                )
                                            }

                                            Text(
                                                text = q.label,
                                                color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF9E9EA7))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1A1F2C), thickness = 0.8.dp)

                    // =============================================================
                    // 📋 ২. ভার্টিক্যাল এপিসোড লিস্ট (আসল সাইজ ও আসল mm:ss ডিউরেশন)
                    // =============================================================
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(episodes, key = { it.episodeId }) { ep ->
                            val isSelected = selectedDownloadEpisodes.contains(ep)
                            val sizeKey = "${ep.episodeId}_${selectedQuality.key}"
                            val sizeText = formatSingleSize(realFileSizes[sizeKey] ?: 0L, selectedQuality.defaultMb)
                            
                            // 🎯 আসল ভিডিও ডিউরেশন ক্যালকুলেশন
                            val durationText = formatDurationDisplay(realDurations[ep.episodeId] ?: 0L, ep.duration)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isSelected) {
                                            selectedDownloadEpisodes.remove(ep)
                                        } else {
                                            selectedDownloadEpisodes.add(ep)
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // গোল্ডেন রেডিও চেকমার্ক
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF6D38B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF1F1604),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF4A5160), CircleShape)
                                    )
                                }

                                // এপিসোড ও আসল ডিউরেশন
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "E${ep.episodeNumber}",
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$sizeText | $durationText", // 👈 যেমন: 30.0MB | 02:15
                                        color = Color(0xFF757D8E),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🚀 ৩. নিচের ফিক্সড ডাউনলোড বার (আসল ক্রাউন আইকনসহ আনলক বাটন)
                // =============================================================
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color(0xFF12151D),
                    border = BorderStroke(0.8.dp, Color(0xFF1E2432))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 👈 বামে: Free downloads কাউন্টার
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Free downloads: ",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = if (isVip) "Unlimited" else "0",
                                color = if (isVip) Color(0xFFF6D38B) else Color(0xFF00E676),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 👉 ডানে: 720P সিলেক্টেড থাকলে এবং ইউজার ভিআইপি না হলে আনলক বাটন
                        val is720Selected = (selectedQuality.key == "720p")
                        val showVipUnlockButton = is720Selected && !isVip

                        Button(
                            onClick = {
                                if (showVipUnlockButton) {
                                    onNavigateToVip()
                                } else {
                                    if (selectedDownloadEpisodes.isEmpty()) {
                                        Toast.makeText(context, "Please select an episode", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    onDownloadSelected(selectedDownloadEpisodes.toList(), selectedQuality.key)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showVipUnlockButton) Color(0xFFF6D38B) else Color(0xFF00D166)
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            if (showVipUnlockButton) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VipCrownVectorIcon(
                                        tint = Color(0xFF2B210E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Unlock HD downloads",
                                        color = Color(0xFF2B210E),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (selectedDownloadEpisodes.isEmpty()) "Download" else "Download (${selectedDownloadEpisodes.size})",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
