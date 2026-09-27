package com.example.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private const val CHUNK_SIZE_DOWNLOAD = 50

private suspend fun fetchRealFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val targetUrl = if (url.contains(".m3u8")) {
            url.substringBeforeLast("/") + "/download_720p.mp4"
        } else url

        val connection = (URL(targetUrl).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
            setRequestProperty("Accept-Encoding", "identity")
        }
        var length = connection?.contentLengthLong ?: 0L
        connection?.disconnect()

        if (length <= 0L && url.contains(".m3u8")) {
            val fallbackUrl = url.substringBeforeLast("/") + "/download.mp4"
            val connFallback = (URL(fallbackUrl).openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "HEAD"
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PlayDramaFlix")
            }
            length = connFallback?.contentLengthLong ?: 0L
            connFallback?.disconnect()
        }

        if (length > 0) length else 0L
    } catch (_: Exception) {
        0L
    }
}

private fun formatSize(bytes: Long, isCalculating: Boolean): String {
    if (bytes <= 0L) {
        return if (isCalculating) "Calculating..." else "0 MB"
    }
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@Composable
private fun VipCrownMiniIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
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

data class DownloadQualityItem(
    val key: String,
    val label: String,
    val isVipOnly: Boolean
)

@Composable
fun PlayerBatchDownloadSheet(
    title: String,
    slug: String = "",
    episodes: List<EpisodeDto>,
    isVip: Boolean = false,
    onClose: () -> Unit,
    onDownloadSelected: (List<EpisodeDto>) -> Unit,
    onNavigateToVip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }
    val episodeChunks = remember(episodes) { episodes.chunked(CHUNK_SIZE_DOWNLOAD) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }

    val realFileSizes = remember { mutableStateMapOf<String, Long>() }
    var isFetchingSizes by remember { mutableStateOf(false) }

    // =========================================================================
    // 🎯 ১. ডাইনামিক কোয়ালিটি ডিটেকশন (৭২০p এবং ১০৮০p ভিআইপি থাকবে)
    // =========================================================================
    val availableQualities = remember(episodes) {
        val detected = mutableListOf<DownloadQualityItem>()
        val seen = mutableSetOf<String>()

        episodes.forEach { ep ->
            ep.downloadOptions?.forEach { opt ->
                val qLower = opt.quality.lowercase()
                val (key, label, isVipTag) = when {
                    qLower.contains("1080") -> Triple("1080p", "1080P", true)
                    qLower.contains("720")  -> Triple("720p", "720P", true)  // 👈 ৭২০p প্রিমিয়াম
                    qLower.contains("480")  -> Triple("480p", "480P", false)
                    qLower.contains("360")  -> Triple("360p", "360P", false)
                    else                   -> Triple("single", "HD", false)
                }
                if (seen.add(key)) detected.add(DownloadQualityItem(key, label, isVipTag))
            }
        }

        if (detected.isEmpty()) {
            listOf(
                DownloadQualityItem("1080p", "1080P", true),
                DownloadQualityItem("720p", "720P", true),   // 👈 ৭২০p প্রিমিয়াম
                DownloadQualityItem("480p", "480P", false),
                DownloadQualityItem("360p", "360P", false)
            )
        } else {
            detected.sortedByDescending { it.key }
        }
    }

    var selectedQuality by remember(availableQualities) {
        mutableStateOf(availableQualities.firstOrNull { !it.isVipOnly } ?: availableQualities.first())
    }

    val filteredEpisodes = remember(episodes, selectedQuality) {
        episodes.filter { ep ->
            val opts = ep.downloadOptions
            if (!opts.isNullOrEmpty()) {
                opts.any { 
                    it.quality.contains(selectedQuality.key, true) || 
                    it.url.contains(selectedQuality.key, true) 
                }
            } else true
        }.ifEmpty { episodes }
    }

    val isAllSelected = remember(selectedDownloadEpisodes.size, filteredEpisodes.size) {
        selectedDownloadEpisodes.size == filteredEpisodes.size && filteredEpisodes.isNotEmpty()
    }

    var todayUsedBytes by remember { mutableLongStateOf(DownloadQuotaManager.getTodayUsedBytes(context)) }

    // =========================================================================
    // ⚡ ২. নির্বাচিত কোয়ালিটির আসল MB সাইজ ক্যালকুলেশন
    // =========================================================================
    LaunchedEffect(selectedDownloadEpisodes.toList(), selectedQuality) {
        val uncalculated = selectedDownloadEpisodes.filter { ep ->
            val key = "${ep.episodeId}_${selectedQuality.key}"
            !realFileSizes.containsKey(key) || (realFileSizes[key] ?: 0L) <= 0L
        }

        if (uncalculated.isNotEmpty()) {
            isFetchingSizes = true
            uncalculated.forEach { ep ->
                coroutineScope.launch {
                    val matchingOpt = ep.downloadOptions?.firstOrNull {
                        it.quality.contains(selectedQuality.key, true) || it.url.contains(selectedQuality.key, true)
                    }
                    val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)
                    val size = fetchRealFileSize(targetUrl)
                    val key = "${ep.episodeId}_${selectedQuality.key}"
                    if (size > 0) {
                        realFileSizes[key] = size
                    }
                }
            }
            isFetchingSizes = false
        }
    }

    val totalSelectedBytes = remember(selectedDownloadEpisodes.toList(), selectedQuality, realFileSizes.toMap()) {
        selectedDownloadEpisodes.sumOf { ep ->
            val key = "${ep.episodeId}_${selectedQuality.key}"
            realFileSizes[key] ?: 0L
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141720))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 95.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ১. হেডার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF9E9EA7))
                }
            }

            HorizontalDivider(color = Color(0xFF2A303C), thickness = 0.8.dp)

            // =========================================================================
            // 🎯 কোয়ালিটি চিপস রো (1080p, 720p VIP Crown সহ)
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Quality:", color = Color(0xFF8E95A5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableQualities.forEach { q ->
                        val isSelected = selectedQuality.key == q.key
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF382B17) else Color(0xFF1F2430),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)
                            ),
                            modifier = Modifier.clickable {
                                selectedQuality = q
                                selectedDownloadEpisodes.clear()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (q.isVipOnly) {
                                    VipCrownMiniIcon(tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52))
                                }
                                Text(
                                    text = q.label,
                                    color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (episodeChunks.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(episodeChunks) { index, chunk ->
                        val start = index * CHUNK_SIZE_DOWNLOAD + 1
                        val end = start + chunk.size - 1
                        val isSelected = (index == selectedChunkIndex)

                        Text(
                            text = "$start-$end",
                            color = if (isSelected) Color(0xFF00E676) else Color(0xFF8E95A5),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clickable { selectedChunkIndex = index }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { emptyList() }

            // ২. পর্বের গ্রিড
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
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
                            .background(if (isSelectedForDl) Color(0xFF273832) else Color(0xFF333842))
                            .border(
                                width = if (isSelectedForDl) 1.5.dp else 0.dp,
                                color = if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (isSelectedForDl) selectedDownloadEpisodes.remove(ep)
                                else selectedDownloadEpisodes.add(ep)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ep.episodeNumber.toString(),
                            color = if (isSelectedForDl) Color(0xFF00E676) else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    if (isSelectedForDl) Color(0xFF00E676) else Color(0xFF5A6272),
                                    CircleShape
                                )
                                .background(if (isSelectedForDl) Color(0xFF00E676) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelectedForDl) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ৩. নিচের ফিক্সড ডাউনলোড বার (VIP কোয়ালিটি গার্ড সহ)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF1E222B))
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = Color(0xFF2F3646), thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Select All বাটন
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable {
                            if (isAllSelected) selectedDownloadEpisodes.clear()
                            else {
                                selectedDownloadEpisodes.clear()
                                selectedDownloadEpisodes.addAll(filteredEpisodes)
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (isAllSelected) Color(0xFF00E676) else Color(0xFF717886), CircleShape)
                                .background(if (isAllSelected) Color(0xFF00E676) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAllSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            }
                        }
                        Text("Select All", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    }

                    // 🎯 VIP কোয়ালিটি লক চেকার (৭২০p ও ১০৮০p এর জন্য)
                    val isVipLocked = selectedQuality.isVipOnly && !isVip

                    val downloadButtonBrush = if (isVipLocked) {
                        Brush.horizontalGradient(listOf(Color(0xFFE5B567), Color(0xFFF6D38B)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF0088FF), Color(0xFF00D26A)))
                    }

                    val displaySize = formatSize(totalSelectedBytes, isFetchingSizes && totalSelectedBytes == 0L)

                    val buttonText = when {
                        isVipLocked -> "Unlock ${selectedQuality.label} with VIP 👑"
                        totalSelectedBytes > 0 -> "Download (${selectedDownloadEpisodes.size}) · $displaySize"
                        selectedDownloadEpisodes.isNotEmpty() -> "Download (${selectedDownloadEpisodes.size})"
                        else -> "Download (${selectedQuality.label})"
                    }

                    Button(
                        onClick = {
                            // 🔒 ভিআইপি কোয়ালিটি হলে আগে ভিআইপি পেজে পাঠাবে
                            if (isVipLocked) {
                                Toast.makeText(context, "${selectedQuality.label} is exclusive for VIP members! Upgrade now.", Toast.LENGTH_SHORT).show()
                                onNavigateToVip()
                                return@Button
                            }

                            val targets = if (selectedDownloadEpisodes.isNotEmpty()) {
                                selectedDownloadEpisodes.toList()
                            } else {
                                filteredEpisodes.take(1)
                            }

                            // 🛡️ ২ জিবি লিমিট চেক
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
                                onDownloadSelected(targets)
                            } else {
                                Toast.makeText(context, checkResult.message, Toast.LENGTH_LONG).show()
                                onNavigateToVip()
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(downloadButtonBrush)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isVipLocked) {
                                VipCrownMiniIcon(tint = Color(0xFF2B210E))
                                Text(buttonText, color = Color(0xFF2B210E), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            } else {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                                Text(buttonText, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isVip) {
                    Text(
                        text = "👑 VIP Member: Unlimited HD Downloads",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    val usedFormatted = DownloadQuotaManager.formatBytes(todayUsedBytes)
                    val remainingFormatted = DownloadQuotaManager.formatBytes(DownloadQuotaManager.getRemainingFreeBytes(context))
                    Text(
                        text = "Daily Free Limit: $usedFormatted / 2.0 GB used ($remainingFormatted left)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
