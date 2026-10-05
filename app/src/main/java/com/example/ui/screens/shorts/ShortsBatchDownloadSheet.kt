package com.example.ui.screens.shorts

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

// ⚡ ক্লাউডফ্লেয়ার R2 থেকে ফাইলের ১০০% আসল সাইজ লাইভ রিড করার মেথড
private suspend fun fetchLiveFileSize(url: String): Long = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext 0L
    try {
        val targetUrl = if (url.contains(".m3u8")) {
            url.substringBeforeLast("/") + "/download_720p.mp4"
        } else {
            url
        }

        val conn = (URL(targetUrl).openConnection() as? HttpURLConnection)?.apply {
            requestMethod = "HEAD"
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PlayDramaFlix")
        }
        var length = conn?.contentLengthLong ?: 0L
        conn?.disconnect()

        // ফলব্যাক: download_720p না পেলে download.mp4 চেক করা
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

        if (length > 0L) length else 0L
    } catch (_: Exception) {
        0L
    }
}

@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
    Canvas(modifier = modifier.size(13.dp)) {
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
    val key: String,
    val label: String,
    val isVipOnly: Boolean
)

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

    val dragOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1200f) }

    // 🎯 রিয়েল-টাইম লাইভ মেগাবাইট সাইজ ক্যাশ ম্যাপ
    val realSizesMap = remember { mutableStateMapOf<String, Long>() }

    // 🎯 দৈনিক ২ জিবি কোটা ট্র্যাকার স্টেট
    var todayUsedBytes by remember { mutableLongStateOf(DownloadQuotaManager.getTodayUsedBytes(context)) }

    // =========================================================================
    // 🔍 ১. ড্রামার আসল কোয়ালিটি ডিটেকশন
    // =========================================================================
    val realAvailableQualities = remember(episodes) {
        val detected = mutableListOf<SheetQualityItem>()
        val seen = mutableSetOf<String>()

        episodes.forEach { ep ->
            ep.downloadOptions?.forEach { opt ->
                val qLower = opt.quality.lowercase()
                val (key, label, isVipTag) = when {
                    qLower.contains("1080") -> Triple("1080p", "1080P", true)
                    qLower.contains("720")  -> Triple("720p", "720P", true)
                    qLower.contains("480")  -> Triple("480p", "480P", false)
                    qLower.contains("360")  -> Triple("360p", "360P", false)
                    else                   -> Triple("single", "HD", false)
                }
                if (seen.add(key)) {
                    detected.add(SheetQualityItem(key, label, isVipTag))
                }
            }
        }

        if (detected.isEmpty()) {
            listOf(SheetQualityItem("single", "HD", false))
        } else {
            detected.sortedByDescending { it.key }
        }
    }

    var selectedQuality by remember(realAvailableQualities) {
        mutableStateOf(realAvailableQualities.firstOrNull() ?: SheetQualityItem("single", "HD", false))
    }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    val filteredEpisodesByQuality = remember(episodes, selectedQuality, realAvailableQualities.size) {
        if (realAvailableQualities.size <= 1 || selectedQuality.key == "single") {
            episodes
        } else {
            episodes.filter { ep ->
                val opts = ep.downloadOptions
                if (!opts.isNullOrEmpty()) {
                    opts.any { 
                        it.quality.contains(selectedQuality.key, ignoreCase = true) || 
                        it.url.contains(selectedQuality.key, ignoreCase = true) 
                    }
                } else {
                    true
                }
            }.ifEmpty { episodes }
        }
    }

    // =========================================================================
    // ⚡ ২. লাইভ R2 সাইজ স্ক্যানার
    // =========================================================================
    LaunchedEffect(filteredEpisodesByQuality, selectedQuality) {
        filteredEpisodesByQuality.forEach { ep ->
            val matchingOpt = ep.downloadOptions?.firstOrNull {
                it.quality.contains(selectedQuality.key, true) || it.url.contains(selectedQuality.key, true)
            } ?: ep.downloadOptions?.firstOrNull()

            val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)

            if (!targetUrl.isNullOrBlank() && !realSizesMap.containsKey(targetUrl)) {
                val serverSize = matchingOpt?.size ?: ""
                if (serverSize.isNotBlank() && (serverSize.contains("MB") || serverSize.contains("GB"))) {
                    val parsedMb = serverSize.replace("MB", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0
                    if (parsedMb > 0.0) {
                        realSizesMap[targetUrl] = (parsedMb * 1024 * 1024).toLong()
                    }
                } else {
                    coroutineScope.launch(Dispatchers.IO) {
                        val bytes = fetchLiveFileSize(targetUrl)
                        if (bytes > 0L) {
                            realSizesMap[targetUrl] = bytes
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // ⚡ ৩. মোট সিলেক্টেড সাইজ ক্যালকুলেটর (MB)
    // =========================================================================
    val totalCalculatedMb = remember(selectedDownloadEpisodes.toList(), selectedQuality, realSizesMap.toMap()) {
        var totalBytes = 0L
        selectedDownloadEpisodes.forEach { ep ->
            val matchingOpt = ep.downloadOptions?.firstOrNull {
                it.quality.contains(selectedQuality.key, true) || it.url.contains(selectedQuality.key, true)
            } ?: ep.downloadOptions?.firstOrNull()

            val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)
            val liveBytes = realSizesMap[targetUrl] ?: 0L

            if (liveBytes > 0L) {
                totalBytes += liveBytes
            } else {
                val sizeString = matchingOpt?.size ?: ""
                val parsedMb = sizeString.replace("MB", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0
                totalBytes += (parsedMb * 1024 * 1024).toLong()
            }
        }
        totalBytes.toDouble() / (1024.0 * 1024.0)
    }

    val isAllSelected = remember(selectedDownloadEpisodes.size, filteredEpisodesByQuality.size) {
        filteredEpisodesByQuality.isNotEmpty() && selectedDownloadEpisodes.size == filteredEpisodesByQuality.size
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
                .fillMaxHeight(0.55f)
                .onGloballyPositioned { coordinates ->
                    sheetHeightPx = coordinates.size.height.toFloat()
                }
                .offset { IntOffset(0, dragOffsetY.value.coerceAtLeast(0f).roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = Color(0xFF0F1117)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp) // নিচে লিমিট টেক্সটের জন্য জায়গা বাড়ানো হলো
                ) {
                    // =============================================================
                    // 🔝 ১. ড্র্যাগ হ্যান্ডেল ও কোয়ালিটি চিপস
                    // =============================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720))
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (dragOffsetY.value > 120f) {
                                                dragOffsetY.animateTo(
                                                    targetValue = sheetHeightPx,
                                                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                                )
                                                onDismiss()
                                            } else {
                                                dragOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(4.5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                realAvailableQualities.forEach { q ->
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
                                            selectedDownloadEpisodes.clear()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (q.isVipOnly) {
                                                VipCrownVectorIcon(tint = if (isSelected) Color(0xFFF6D38B) else Color(0xFFC49A52))
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
                    // 📋 ২. পর্বের লিস্ট (ডানপাশে আসল MB সাইজ সহ)
                    // =============================================================
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredEpisodesByQuality, key = { it.episodeId }) { ep ->
                            val isSelected = selectedDownloadEpisodes.contains(ep)

                            val matchingOpt = ep.downloadOptions?.firstOrNull {
                                it.quality.contains(selectedQuality.key, true) || it.url.contains(selectedQuality.key, true)
                            } ?: ep.downloadOptions?.firstOrNull()

                            val targetUrl = matchingOpt?.url?.takeIf { it.isNotBlank() } ?: ep.resolveDownloadUrl(slug)
                            val liveBytes = realSizesMap[targetUrl] ?: 0L

                            // 🎯 আসল সাইজ ফরম্যাটিং
                            val sizeDisplay = when {
                                liveBytes > 0L -> {
                                    val mb = liveBytes / (1024.0 * 1024.0)
                                    String.format(Locale.US, "%.1f MB", mb)
                                }
                                matchingOpt?.size?.isNotBlank() == true && matchingOpt.size != "--" -> matchingOpt.size
                                else -> "Loading..."
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isSelected) selectedDownloadEpisodes.remove(ep)
                                        else selectedDownloadEpisodes.add(ep)
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 👈 বাঁয়ে: চেকমার্ক + পর্ব নম্বর
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
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

                                    Text(
                                        text = "E${ep.episodeNumber}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 👉 ডানে: ফাইলের আসল সাইজ ব্যাজ
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF1E2430),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (isSelected) Color(0xFF00E676).copy(alpha = 0.6f) else Color(0xFF2C3545)
                                    )
                                ) {
                                    Text(
                                        text = sizeDisplay,
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🎯 ৩. নিচের ফিক্সড ডাউনলোড বার (২ জিবি লিমিট টেক্সট সহ)
                // =============================================================
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color(0xFF141720),
                    border = BorderStroke(0.8.dp, Color(0xFF1E2432))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 👈 বামে: [○] Select All
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isAllSelected) {
                                            selectedDownloadEpisodes.clear()
                                        } else {
                                            selectedDownloadEpisodes.clear()
                                            selectedDownloadEpisodes.addAll(filteredEpisodesByQuality)
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                if (isAllSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(21.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(21.dp)
                                            .clip(CircleShape)
                                            .border(1.8.dp, Color(0xFF6B7280), CircleShape)
                                    )
                                }

                                Text(
                                    text = "Select All",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            // 👉 ডানে: ডাউনলোড বাটন
                            val isVipLocked = selectedQuality.isVipOnly && !isVip

                            val downloadButtonBrush = if (isVipLocked) {
                                Brush.horizontalGradient(listOf(Color(0xFFE5B567), Color(0xFFF6D38B)))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1E88E5),
                                        Color(0xFF00C853),
                                        Color(0xFF00E676)
                                    )
                                )
                            }

                            val buttonText = when {
                                isVipLocked -> "Unlock HD (${selectedQuality.label})"
                                totalCalculatedMb > 0.0 -> String.format(Locale.US, "Download · %.1fMB", totalCalculatedMb)
                                selectedDownloadEpisodes.isNotEmpty() -> "Download (${selectedDownloadEpisodes.size})"
                                else -> "Download · 0.0MB"
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 18.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(downloadButtonBrush)
                                    .clickable {
                                        if (isVipLocked) {
                                            onNavigateToVip()
                                            return@clickable
                                        }
                                        if (selectedDownloadEpisodes.isEmpty()) {
                                            Toast.makeText(context, "Please select at least one episode", Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }

                                        // 🎯 কোটা ভ্যালিডেশন
                                        val totalSelectedBytes = (totalCalculatedMb * 1024.0 * 1024.0).toLong()
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
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isVipLocked) {
                                        VipCrownVectorIcon(tint = Color(0xFF2B210E))
                                        Text(
                                            text = buttonText,
                                            color = Color(0xFF2B210E),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = buttonText,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // =============================================================
                        // 🌟 গোল বৃত্তের জায়গায় কাঙ্ক্ষিত ২ জিবি কোটা টেক্সট
                        // =============================================================
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
    }
}
