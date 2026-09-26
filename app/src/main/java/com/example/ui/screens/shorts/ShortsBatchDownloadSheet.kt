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
import androidx.compose.ui.text.style.TextOverflow
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

private const val CHUNK_SIZE_DOWNLOAD = 25

data class DynamicQualityItem(
    val key: String,
    val label: String
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

private fun formatTotalBytes(bytes: Long, selectedCount: Int): String {
    if (selectedCount <= 0) return "0 MB"
    // যদি সাইজ এখনও লোড হচ্ছে, তখনো মিনিমাম এস্টিমেট দেখাবে
    val effectiveBytes = if (bytes > 0L) bytes else (selectedCount * 22L * 1024 * 1024)
    val mb = effectiveBytes / (1024.0 * 1024.0)
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

    // =========================================================================
    // 🔍 ১. ড্রামাতে বাস্তবে যেসব কোয়ালিটি আছে শুধুমাত্র সেগুলোই ডিটেক্ট করা
    // (যদি শুধু 720p থাকে, তবে শুধুই 720p দেখাবে! কোনো ডামি 480p বা 360p আসবে না)
    // =========================================================================
    val realAvailableQualities = remember(episodes) {
        val detected = mutableListOf<DynamicQualityItem>()
        val seenKeys = mutableSetOf<String>()

        episodes.forEach { ep ->
            val opts = ep.downloadOptions
            if (!opts.isNullOrEmpty()) {
                opts.forEach { opt ->
                    val qLower = opt.quality.lowercase()
                    val (key, label) = when {
                        qLower.contains("1080") -> "1080p" to "1080P"
                        qLower.contains("720") -> "720p" to "720P"
                        qLower.contains("480") -> "480p" to "480P"
                        qLower.contains("360") -> "360p" to "360P"
                        else -> "720p" to "720P"
                    }
                    if (seenKeys.add(key)) {
                        detected.add(DynamicQualityItem(key, label))
                    }
                }
            } else {
                // পুরোনো ড্রামার ফলব্যাক
                val url = ep.downloadUrl ?: ep.directStreamUrl ?: ep.videoUrl ?: ""
                val detectedQ = when {
                    url.contains("480", true) -> "480p" to "480P"
                    url.contains("360", true) -> "360p" to "360P"
                    else -> "720p" to "720P"
                }
                if (seenKeys.add(detectedQ.first)) {
                    detected.add(DynamicQualityItem(detectedQ.first, detectedQ.second))
                }
            }
        }

        if (detected.isEmpty()) {
            detected.add(DynamicQualityItem("720p", "720P"))
        }

        detected.sortedByDescending { it.key }
    }

    var selectedQuality by remember(realAvailableQualities) {
        mutableStateOf(realAvailableQualities.firstOrNull() ?: DynamicQualityItem("720p", "720P"))
    }

    fun getEpisodeDownloadUrl(ep: EpisodeDto, qualityKey: String): String {
        return ep.downloadOptions?.firstOrNull {
            it.quality.contains(qualityKey, ignoreCase = true) || it.url.contains(qualityKey, ignoreCase = true)
        }?.url ?: ep.downloadUrl?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)
    }

    // নির্বাচিত কোয়ালিটির পর্বগুলো ফিল্টার করা
    val filteredEpisodesByQuality = remember(episodes, selectedQuality) {
        episodes.filter { ep ->
            val opts = ep.downloadOptions
            if (!opts.isNullOrEmpty()) {
                opts.any { it.quality.contains(selectedQuality.key, true) || it.url.contains(selectedQuality.key, true) }
            } else {
                true
            }
        }
    }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val episodeChunks = remember(filteredEpisodesByQuality) { filteredEpisodesByQuality.chunked(CHUNK_SIZE_DOWNLOAD) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var todayUsedBytes by remember { mutableLongStateOf(DownloadQuotaManager.getTodayUsedBytes(context)) }

    val isAllSelected = remember(selectedDownloadEpisodes.size, filteredEpisodesByQuality.size) {
        selectedDownloadEpisodes.size == filteredEpisodesByQuality.size && filteredEpisodesByQuality.isNotEmpty()
    }

    // নির্বাচিত পর্বের লাইভ সাইজ ফেচ করা
    LaunchedEffect(selectedDownloadEpisodes.toList(), selectedQuality) {
        selectedDownloadEpisodes.forEach { ep ->
            val targetUrl = getEpisodeDownloadUrl(ep, selectedQuality.key)
            val cacheKey = "${ep.episodeId}_${selectedQuality.key}"

            if (!realFileSizes.containsKey(cacheKey) && targetUrl.isNotBlank()) {
                coroutineScope.launch {
                    val size = fetchLiveFileSize(targetUrl)
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
            realFileSizes[cacheKey] ?: (22L * 1024 * 1024)
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
                .fillMaxHeight(0.56f) // 🎯 মাঝের দাগ বরাবর উচ্চতা
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = Color(0xFF1E222A),
            tonalElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .padding(top = 12.dp, bottom = 85.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 🔝 টাইটেল বার (২ লাইন সাপোর্ট)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            lineHeight = 19.sp,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF9E9EA7)
                            )
                        }
                    }

                    // =============================================================
                    // 🎛️ ২. কোয়ালিটি কার্ডস (শুধু বিদ্যমান কোয়ালিটিগুলোই আসবে)
                    // =============================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HighQuality,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Video download quality",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 🎯 বাস্তব কোয়ালিটি অনুযায়ী ডায়নামিক কার্ড রেন্ডার
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            realAvailableQualities.forEach { qItem ->
                                val isChosen = (selectedQuality.key == qItem.key)

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF282D37),
                                    border = BorderStroke(
                                        width = if (isChosen) 1.2.dp else 0.dp,
                                        color = if (isChosen) Color(0xFF00E676) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedQuality = qItem
                                            selectedDownloadEpisodes.clear()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = qItem.label,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // রেডিও বাটন
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isChosen) Color(0xFF00E676) else Color(0xFF6B7280),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isChosen) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
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

                    HorizontalDivider(color = Color(0xFF2C323E), thickness = 0.6.dp)

                    // 📑 ৩. Download সাবটাইটেল ও ২৫ পর্বের রেঞ্জ বার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (episodeChunks.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                itemsIndexed(episodeChunks) { index, chunk ->
                                    val start = index * CHUNK_SIZE_DOWNLOAD + 1
                                    val end = start + chunk.size - 1
                                    val isSelected = (index == selectedChunkIndex)

                                    Text(
                                        text = "$start-$end",
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable { selectedChunkIndex = index }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

                    // 🔲 ৪. এক লাইনে ৬টি ছোট ছোট পর্ব (সবুজ বর্ডার ছাড়া)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentChunkEpisodes, key = { it.episodeId }) { ep ->
                            val isSelectedForDl = selectedDownloadEpisodes.contains(ep)

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF282D37))
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelectedForDl) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFF5A6272), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // 🚀 ৫. নিচের ফিক্সড ডাউনলোড বার (আসল সাইজ সহ)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF1E222A))
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        color = Color(0xFF2C323E),
                        thickness = 0.8.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    if (isAllSelected) {
                                        selectedDownloadEpisodes.clear()
                                    } else {
                                        selectedDownloadEpisodes.clear()
                                        selectedDownloadEpisodes.addAll(filteredEpisodesByQuality)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.5.dp,
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
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Select All",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 🎯 আসল সাইজ সহ ডাউনলোড বাটন (কোনো 0 MB থাকবে না)
                            Button(
                                onClick = {
                                    if (selectedDownloadEpisodes.isEmpty()) {
                                        Toast.makeText(context, "Please select at least 1 episode", Toast.LENGTH_SHORT).show()
                                        return@Button
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
                                        onDownloadSelected(selectedDownloadEpisodes.toList(), selectedQuality.key)
                                    } else {
                                        Toast.makeText(context, checkResult.message, Toast.LENGTH_LONG).show()
                                        onNavigateToVip()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
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
                                        modifier = Modifier.size(18.dp)
                                    )

                                    val sizeDisplay = formatTotalBytes(totalSelectedBytes, selectedDownloadEpisodes.size)

                                    Text(
                                        text = "Download · $sizeDisplay",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${selectedDownloadEpisodes.size} episodes selected",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        if (!isVip) {
                            val usedFormatted = DownloadQuotaManager.formatBytes(todayUsedBytes)
                            val remainingFormatted = DownloadQuotaManager.formatBytes(DownloadQuotaManager.getRemainingFreeBytes(context))
                            Text(
                                text = "Daily Limit: $usedFormatted / 2.0 GB ($remainingFormatted left)",
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
