package com.example.ui.screens.shorts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import com.example.util.DownloadQuotaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class SheetQualityOption(
    val key: String,       // "720p", "480p", "360p"
    val label: String,     // "👑 720P", "480P", "360P"
    val isVipOnly: Boolean,
    val defaultMb: Double
)

private suspend fun fetchLiveFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 3000
            readTimeout = 3000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
        }
        val length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()
        if (length > 0) length else 0L
    } catch (_: Exception) {
        0L
    }
}

private fun formatSingleSize(bytes: Long, fallbackMb: Double): String {
    val effective = if (bytes > 0L) bytes else (fallbackMb * 1024 * 1024).toLong()
    val mb = effective / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1fMB", mb)
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

    // 🎯 ৩টি কোয়ালিটি: 720P ভিআইপি, বাকি দুটি ফ্রি
    val availableQualities = remember {
        listOf(
            SheetQualityOption("720p", "👑 720P", isVipOnly = true, defaultMb = 45.0),
            SheetQualityOption("480p", "480P", isVipOnly = false, defaultMb = 22.0),
            SheetQualityOption("360p", "360P", isVipOnly = false, defaultMb = 12.0)
        )
    }

    var selectedQuality by remember { mutableStateOf(availableQualities[0]) } // ডিফল্ট 720P
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val realFileSizes = remember { mutableStateMapOf<String, Long>() }

    // ব্যাকগ্রাউন্ডে সাইজ লোডার
    LaunchedEffect(selectedQuality, episodes) {
        episodes.take(20).forEach { ep ->
            val targetUrl = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }?.url
                ?: ep.resolveDownloadUrl(slug)

            val cacheKey = "${ep.episodeId}_${selectedQuality.key}"
            if (!realFileSizes.containsKey(cacheKey) && targetUrl.isNotBlank()) {
                coroutineScope.launch {
                    val size = fetchLiveFileSize(targetUrl)
                    if (size > 0L) realFileSizes[cacheKey] = size
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
                .fillMaxHeight(0.80f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            // 🎯 স্ক্রিনশটের মতো উপরে কোনো ক্রপ/রাউন্ডেড কর্নার থাকবে না
            shape = RoundedCornerShape(0.dp),
            color = Color(0xFF0F1117)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 75.dp)
                ) {
                    // =============================================================
                    // 🔝 ১. টপ বার: টাইটেল সম্পূর্ণ বাদ, শুধু কোয়ালিটি চিপস ও ক্লোজ বাটন
                    // =============================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // কোয়ালিটি চিপস (👑 720P, 480P, 360P)
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
                                    modifier = Modifier.clickable {
                                        selectedQuality = q
                                    }
                                ) {
                                    Text(
                                        text = q.label,
                                        color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF9E9EA7))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1A1F2C), thickness = 0.8.dp)

                    // =============================================================
                    // 📋 ২. স্ক্রিনশটের হুবহু ভার্টিক্যাল এপিসোড লিস্ট
                    // =============================================================
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(episodes, key = { it.episodeId }) { ep ->
                            val isSelected = selectedDownloadEpisodes.contains(ep)
                            val cacheKey = "${ep.episodeId}_${selectedQuality.key}"
                            val sizeText = formatSingleSize(realFileSizes[cacheKey] ?: 0L, selectedQuality.defaultMb)
                            val durationText = ep.duration.takeIf { !it.isNullOrBlank() } ?: "02:15"

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
                                // 🎯 স্ক্রিনশটের হুবহু গোল্ডেন চেক রেডিও আইকন
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF6D38B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF1F1604),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF4A5160), CircleShape)
                                    )
                                }

                                // এপিসোড টাইটেল ও সাইজ/টাইম
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = String.format(Locale.US, "E%02d", ep.episodeNumber),
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$sizeText | $durationText",
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
                // 🚀 ৩. স্ক্রিনশটের হুবহু নিচের বটম বার
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 👈 বামে: Free downloads কাউন্টার
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Free downloads: ",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = if (isVip) "Unlimited" else "0",
                                color = if (isVip) Color(0xFFF6D38B) else Color(0xFF00E676),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 👉 ডানে: 720P সিলেক্টেড থাকলে এবং ইউজার ভিআইপি না হলে আনলক বাটন আসবে
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
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            if (showVipUnlockButton) {
                                Text(
                                    text = "👑 Unlock HD downloads",
                                    color = Color(0xFF2B210E),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (selectedDownloadEpisodes.isEmpty()) "Download" else "Download (${selectedDownloadEpisodes.size})",
                                    color = Color.Black,
                                    fontSize = 13.5.sp,
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
