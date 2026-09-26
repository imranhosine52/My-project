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
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// 👑 🎯 পিওর নেটিভ ভেক্টর ক্রাউন আইকন (কোনো ইমোজি ছাড়া গোল্ডেন ভেক্টর)
@Composable
fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
    Canvas(modifier = modifier.size(14.dp)) {
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

    // 🎯 ড্র্যাগ-টু-মিনিমাইজ অ্যানিমেশন স্টেট
    val dragOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1200f) }

    // =========================================================================
    // 🔍 ১. ড্রামাতে বাস্তবে যেসব কোয়ালিটি আছে শুধুমাত্র সেগুলোই ডিটেক্ট করা
    // =========================================================================
    val realAvailableQualities = remember(episodes) {
        val detected = mutableListOf<SheetQualityItem>()
        val seen = mutableSetOf<String>()

        episodes.forEach { ep ->
            ep.downloadOptions?.forEach { opt ->
                val qLower = opt.quality.lowercase()
                val (key, label, isVipTag) = when {
                    qLower.contains("1080") -> Triple("1080p", "1080P", true)
                    qLower.contains("720") -> Triple("720p", "720P", true)
                    qLower.contains("480") -> Triple("480p", "480P", false)
                    qLower.contains("360") -> Triple("360p", "360P", false)
                    else -> Triple("720p", "720P", false)
                }
                if (seen.add(key)) {
                    detected.add(SheetQualityItem(key, label, isVipTag))
                }
            }
        }

        if (detected.isEmpty()) {
            detected.add(SheetQualityItem("720p", "720P", false))
        }

        detected.sortedByDescending { it.key }
    }

    var selectedQuality by remember(realAvailableQualities) {
        mutableStateOf(realAvailableQualities.firstOrNull() ?: SheetQualityItem("720p", "720P", false))
    }

    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }

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
                .fillMaxHeight(0.52f) // 🎯 স্ক্রিনের ঠিক ৫২% উচ্চতায় লকড
                .onGloballyPositioned { coordinates ->
                    sheetHeightPx = coordinates.size.height.toFloat()
                }
                // 🎯 স্মুথ ফিজিক্স ড্র্যাগ-অফসেট
                .offset { IntOffset(0, dragOffsetY.value.coerceAtLeast(0f).roundToInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                // 🎯 উপর থেকে নিচে টান দিলে স্মুথ অ্যানিমেটেড মিনিমাইজ
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
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
                        }
                    ),
            shape = RoundedCornerShape(0.dp), // 🎯 উপরে কোনো ক্রপ/রাউন্ডেড কর্নার নেই
            color = Color(0xFF0F1117)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 70.dp)
                ) {
                    // =============================================================
                    // 🔝 ১. ড্র্যাগ হ্যান্ডেল + টপ বার (টাইটেলহীন পরিচ্ছন্ন বার)
                    // =============================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720))
                    ) {
                        // স্মুথ ড্র্যাগ হ্যান্ডেল
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // কোয়ালিটি চিপস (আসল ভেক্টর ক্রাউন আইকনসহ)
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

                            // ক্লোজ বাটন
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
                    // 📋 ২. ডামি-মুক্ত লাইভ এপিসোড লিস্ট (০ সেকেন্ড ইনস্ট্যান্ট লোড)
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

                            // 🎯 ডামি সাইজ ও ডামি ডিউরেশন দূর: না থাকলে '--' ও '--:--' দেখাবে
                            val sizeText = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }?.size?.takeIf { it.isNotBlank() } ?: "--"
                            val durationText = ep.duration.takeIf { !it.isNullOrBlank() && it != "24m" } ?: "--:--"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isSelected) selectedDownloadEpisodes.remove(ep)
                                        else selectedDownloadEpisodes.add(ep)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // গোল্ডেন রেডিও চেকমার্ক
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFF6D38B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1F1604), modifier = Modifier.size(13.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, Color(0xFF4A5160), CircleShape))
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
                                        text = "$sizeText | $durationText",
                                        color = Color(0xFF757D8E),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🚀 ৩. নিচের ফিক্সড ডাউনলোড বার
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
                        // ফ্রি ডাউনলোড টেক্সট
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Free downloads: ", color = Color(0xFF94A3B8), fontSize = 12.5.sp)
                            Text(
                                text = if (isVip) "Unlimited" else "0",
                                color = if (isVip) Color(0xFFF6D38B) else Color(0xFF00E676),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isVipLocked = selectedQuality.isVipOnly && !isVip

                        Button(
                            onClick = {
                                if (isVipLocked) {
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
                                containerColor = if (isVipLocked) Color(0xFFF6D38B) else Color(0xFF00D166)
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            if (isVipLocked) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VipCrownVectorIcon(tint = Color(0xFF2B210E))
                                    Text(text = "Unlock HD downloads", color = Color(0xFF2B210E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (selectedDownloadEpisodes.isEmpty()) "Download · 00 MB" else "Download (${selectedDownloadEpisodes.size})",
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
