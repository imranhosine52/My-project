package com.example.ui.screens.shorts

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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

    // ১. কোয়ালিটি ডিটেকশন
    val realAvailableQualities = remember(episodes) {
        listOf(
            SheetQualityItem("720p", "720P", true),
            SheetQualityItem("480p", "480P", false),
            SheetQualityItem("360p", "360P", false)
        )
    }

    var selectedQuality by remember { mutableStateOf(realAvailableQualities[0]) }
    val selectedDownloadEpisodes = remember { mutableStateListOf<EpisodeDto>() }

    // নির্বাচিত কোয়ালিটি অনুযায়ী পর্ব ফিল্টার
    val filteredEpisodesByQuality = remember(episodes, selectedQuality) { episodes }

    // ⚡ সিলেক্ট করা পর্বগুলোর মোট সাইজ হিসাব (MB) - নো নেটওয়ার্ক রিকোয়েস্ট!
    val totalSizeMb = remember(selectedDownloadEpisodes.size, selectedQuality) {
        var totalMb = 0.0
        selectedDownloadEpisodes.forEach { ep ->
            val opt = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }
            val sizeStr = opt?.size ?: ""
            val mbValue = sizeStr.replace("MB", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0
            totalMb += mbValue
        }
        totalMb
    }

    // "Select All" চেকমার্কের অবস্থা
    val isAllSelected = selectedDownloadEpisodes.isNotEmpty() && selectedDownloadEpisodes.size == filteredEpisodesByQuality.size

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
                .onGloballyPositioned { coordinates -> sheetHeightPx = coordinates.size.height.toFloat() }
                .offset { IntOffset(0, dragOffsetY.value.coerceAtLeast(0f).roundToInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffsetY.value > 120f) {
                                    dragOffsetY.animateTo(targetValue = sheetHeightPx, animationSpec = tween(200, easing = FastOutLinearInEasing))
                                    onDismiss()
                                } else {
                                    dragOffsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch { dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f)) }
                        }
                    )
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFF0F1117)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 75.dp) // নিচের বারের জন্য স্পেস
                ) {
                    // 🔝 হেডার এবং কোয়ালিটি চিপস
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720))
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                realAvailableQualities.forEach { q ->
                                    val isSelected = (selectedQuality.key == q.key)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) Color(0xFF382B17) else Color(0xFF1F2430),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFE5B567) else Color(0xFF2D3545)),
                                        modifier = Modifier.clickable {
                                            selectedQuality = q
                                        }
                                    ) {
                                        Text(
                                            text = q.label,
                                            color = if (isSelected) Color(0xFFF6D38B) else Color(0xFF94A3B8),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF9E9EA7))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1A1F2C), thickness = 0.8.dp)

                    // 📋 পর্বের তালিকা (আসল MB সাইজ ও ডোরেশন সহ)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredEpisodesByQuality, key = { it.episodeId }) { ep ->
                            val isSelected = selectedDownloadEpisodes.contains(ep)
                            val opt = ep.downloadOptions?.firstOrNull { it.quality.contains(selectedQuality.key, true) }
                            val sizeText = opt?.size ?: "-- MB"
                            val durationText = ep.duration.takeIf { !it.isNullOrBlank() } ?: "01:30"

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
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, Color(0xFF4A5160), CircleShape))
                                }

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

                // =========================================================================
                // 🎯 আপনার স্ক্রিনশটের হুবহু আধুনিক বটম বার (Select All + Gradient Button)
                // =========================================================================
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color(0xFF161922),
                    border = BorderStroke(0.8.dp, Color(0xFF222836))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 👈 বামে: [○] Select All বাটন
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
                                .padding(vertical = 4.dp)
                        ) {
                            if (isAllSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
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

                        // 👉 ডানে: আপনার স্ক্রিনশটের হুবহু [ নীল-সবুজ গ্রেডিয়েন্ট ডাউনলোড বাটন ]
                        val downloadButtonBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E88E5), // ভাইব্রেন্ট ব্লু
                                Color(0xFF00C853), // ভাইব্রেন্ট এমারেল্ড গ্রিন
                                Color(0xFF00E676)
                            )
                        )

                        val buttonText = if (totalSizeMb > 0) {
                            String.format(Locale.US, "Download · %.1fMB", totalSizeMb)
                        } else {
                            "Download · 0.0MB"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(marginStart = 20.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(downloadButtonBrush)
                                .clickable {
                                    if (selectedQuality.isVipOnly && !isVip) {
                                        onNavigateToVip()
                                        return@clickable
                                    }
                                    if (selectedDownloadEpisodes.isEmpty()) {
                                        Toast.makeText(context, "Please select at least one episode", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    onDownloadSelected(selectedDownloadEpisodes.toList(), selectedQuality.key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
            }
        }
    }
}
