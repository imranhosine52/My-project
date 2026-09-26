package com.example.ui.screens.shorts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EpisodeDto
import com.example.ui.theme.GoldVip
import com.example.util.DownloadQuotaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// 🎯 স্ক্রিনশটের মতো ২৫ পর্বের চাঙ্ক সাইজ
private const val CHUNK_SIZE_DOWNLOAD = 25

// কোয়ালিটি অপশন মডেল
data class BatchQualityItem(
    val label: String,
    val key: String,
    val approxMbPerEp: Double,
    val subtitle: String
)

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 3500
            readTimeout = 3500
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

private fun formatTotalSize(totalBytes: Long, fallbackCount: Int, mbPerEp: Double): String {
    val bytes = if (totalBytes > 0L) totalBytes else (fallbackCount * mbPerEp * 1024 * 1024).toLong()
    if (bytes <= 0L) return "0 MB"

    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2fGB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1fMB", mb)
    }
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

    // 🎯 ১. কোয়ালিটি অপশন তালিকা (480P ও 720P)
    val qualityList = remember {
        listOf(
            BatchQualityItem("480P", "480p", 22.0, "About 22MB per episode"),
            BatchQualityItem("720P", "720p", 45.0, "About 45MB per episode")
        )
    }
    var selectedQuality by remember { mutableStateOf(qualityList[0]) } // ডিফল্ট 480P

    // পর্ব সিলেকশন স্টেট
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_DOWNLOAD) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var todayUsedBytes by remember { mutableLongStateOf(DownloadQuotaManager.getTodayUsedBytes(context)) }

    val isAllSelected = remember(selectedDownloadEpisodes.size, episodes.size) {
        selectedDownloadEpisodes.size == episodes.size && episodes.isNotEmpty()
    }

    // কোয়ালিটি পরিবর্তন হলে লাইভ সাইজ রি-ক্যালকুলেট করা
    LaunchedEffect(selectedDownloadEpisodes.toList(), selectedQuality) {
        selectedDownloadEpisodes.forEach { ep ->
            val targetUrl = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }?.url
                ?: ep.resolveDownloadUrl(slug)

            val cacheKey = "${ep.episodeId}_${selectedQuality.key}"
            if (!realFileSizes.containsKey(cacheKey)) {
                coroutineScope.launch {
                    val size = fetchRealFileSize(targetUrl)
                    if (size > 0L) {
                        realFileSizes[cacheKey] = size
                    }
                }
            }
        }
    }

    val totalSelectedBytes = remember(selectedDownloadEpisodes.toList(), selectedQuality, realFileSizes.toMap()) {
        selectedDownloadEpisodes.sumOf { ep ->
            val cacheKey = "${ep.episodeId}_${selectedQuality.key}"
            realFileSizes[cacheKey] ?: (selectedQuality.approxMbPerEp * 1024 * 1024).toLong()
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
                .fillMaxHeight(0.82f) // স্ক্রিনশটের মতো উচ্চতা
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF1E222A),
            tonalElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // =============================================================
                    // 🔝 ১. টাইটেল ও ক্লোজ বাটন (১ম ছবির মতো)
                    // =============================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9E9EA7)
                            )
                        }
                    }

                    // =============================================================
                    // 🎛️ ২. ভিডিও ডাউনলোড কোয়ালিটি কার্ডস (২য় ছবির হুবহু ডিজাইন)
                    // =============================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HighQuality,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Video download quality",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 480P ও 720P পাশাপাশি কার্ড
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            qualityList.forEach { qItem ->
                                val isChosen = (selectedQuality.key == qItem.key)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2B303C),
                                    border = BorderStroke(
                                        width = if (isChosen) 1.2.dp else 0.dp,
                                        color = if (isChosen) Color(0xFF00E676) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedQuality = qItem }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = qItem.label,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = qItem.subtitle,
                                                color = Color(0xFF8E95A5),
                                                fontSize = 11.sp
                                            )
                                        }

                                        // সবুজ রেডিও বাটন সার্কেল
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = 1.8.dp,
                                                    color = if (isChosen) Color(0xFF00E676) else Color(0xFF6B7280),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isChosen) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF00E676))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2C323E), thickness = 0.8.dp)

                    // =============================================================
                    // 📑 ৩. Download সাবটাইটেল ও ২৫ পর্বের রেঞ্জ ট্যাব (১ম ছবির মতো)
                    // =============================================================
                    Text(
                        text = "Download",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (episodeChunks.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(episodeChunks) { index, chunk ->
                                val start = index * CHUNK_SIZE_DOWNLOAD + 1
                                val end = start + chunk.size - 1
                                val isSelected = (index == selectedChunkIndex)

                                Text(
                                    text = "$start-$end",
                                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { selectedChunkIndex = index }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                    // =============================================================
                    // 🔲 ৪. ৫-কলামের স্কয়ার পর্ব গ্রিড (১ম ছবির হুবহু ডিজাইন)
                    // =============================================================
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5), // 🎯 ৫টি কলাম
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2B303C))
                                    .border(
                                        width = if (isSelectedForDl) 1.5.dp else 0.dp,
                                        color = if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelectedForDl) {
                                            selectedDownloadEpisodes.remove(ep)
                                        } else {
                                            selectedDownloadEpisodes.add(ep)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ep.episodeNumber.toString(),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // নিচে ডানে সবুজ টিকমার্ক বা গোল রিং
                                if (isSelectedForDl) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, Color(0xFF5A6272), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🚀 ৫. নিচের ফিক্সড ডাউনলোড বার (১ম ছবির হুবহু বাটন ও ক্যাপশন)
                // =============================================================
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF1E222A))
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        color = Color(0xFF2C323E),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Select All টগল
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable {
                                    if (isAllSelected) {
                                        selectedDownloadEpisodes.clear()
                                    } else {
                                        selectedDownloadEpisodes.clear()
                                        selectedDownloadEpisodes.addAll(episodes)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.8.dp,
                                            if (isAllSelected) Color(0xFF00E676) else Color(0xFF6B7280),
                                            CircleShape
                                        )
                                        .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Select All",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 🎯 ডাউনলোড বাটন (যেমন: Download · 123.3MB)
                            Button(
                                onClick = {
                                    val targets = if (selectedDownloadEpisodes.isNotEmpty()) {
                                        selectedDownloadEpisodes.toList()
                                    } else {
                                        episodes.take(1)
                                    }

                                    val checkResult = DownloadQuotaManager.checkCanDownload(
                                        context = context,
                                        bytesToDownload = totalSelectedBytes,
                                        isVip = isVip
                                    )

                                    if (checkResult.canDownload) {
                                        if (!isVip) {
                                            DownloadQuotaManager.recordDownloadUsage(context, totalSelectedBytes)
                                            todayUsedBytes = DownloadQuotaManager.getTodayUsedBytes(context)
                                        }
                                        onDownloadSelected(targets, selectedQuality.key)
                                    } else {
                                        Toast.makeText(context, checkResult.message, Toast.LENGTH_LONG).show()
                                        onNavigateToVip()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp), // ছবির মতো রাউন্ডেড কোণা
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF0088FF),
                                                Color(0xFF00D26A)
                                            )
                                        )
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    val sizeDisplay = formatTotalSize(
                                        totalBytes = totalSelectedBytes,
                                        fallbackCount = selectedDownloadEpisodes.size.coerceAtLeast(1),
                                        mbPerEp = selectedQuality.approxMbPerEp
                                    )

                                    Text(
                                        text = "Download · $sizeDisplay",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 🎯 "10 episodes selected" ক্যাপশন (১ম ছবির মতো)
                        Text(
                            text = "${selectedDownloadEpisodes.size} episodes selected",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        // কোটা স্ট্যাটাস
                        if (isVip) {
                            Text(
                                text = "👑 VIP Member: Unlimited Downloads",
                                color = GoldVip,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            val usedFormatted = DownloadQuotaManager.formatBytes(todayUsedBytes)
                            val remainingFormatted = DownloadQuotaManager.formatBytes(DownloadQuotaManager.getRemainingFreeBytes(context))
                            Text(
                                text = "Daily Limit: $usedFormatted / 2.0 GB used ($remainingFormatted left)",
                                color = Color(0xFF8E95A5),
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}
