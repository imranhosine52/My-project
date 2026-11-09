package com.example.ui.screens.localplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdvancedAudioTrackInfo
import com.example.ui.screens.player.RealAudioTrack

private val ActiveCyan = Color(0xFF00E5FF)

/**
 * 🎵 আল্ট্রা-ক্লিন অডিও ল্যাঙ্গুয়েজ ড্রয়ার
 * স্ক্রিনের একদম শেষ প্রান্ত ঘেঁষা (Zero Edge Gap), কম্প্যাক্ট লাইন স্পেসিং এবং হালকা শ্যাডো ব্যাকগ্রাউন্ড।
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<AdvancedAudioTrackInfo>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (AdvancedAudioTrackInfo) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ছবি ২ এর মতো অত্যন্ত হালকা কালো গ্রেডিয়েন্ট (কোনো চারকোনা কড়া বক্স নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.16f),
            Color.Black.copy(alpha = 0.42f),
            Color.Black.copy(alpha = 0.72f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(softFadeGradient)
            // 🎯 সাইডের ফাঁকা স্পেস ১০০% দূর করে একদম প্রান্ত ঘেঁষে স্থাপন (end = 2.dp)
            .padding(top = 18.dp, bottom = 14.dp, end = 2.dp, start = 12.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. "AUDIO LANGUAGE" হেডার + আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 12.dp, end = 4.dp)
        ) {
            Text(
                text = "AUDIO LANGUAGE",
                color = ActiveCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(2.dp)
                    .background(ActiveCyan)
            )
        }

        // =============================================================
        // ২. স্ক্রিনের একদম প্রান্ত ঘেঁষা কম্প্যাক্ট ল্যাঙ্গুয়েজ টেক্সট লিস্ট
        // =============================================================
        if (availableAudioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Default Audio",
                    color = ActiveCyan,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                // 🎯 লাইনের অতিরিক্ত ফাঁকা জায়গা আরও চাপিয়ে রাখা হয়েছে
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = availableAudioTracks,
                    key = { it.id }
                ) { track ->
                    val isSelected = (selectedAudioTrackId == track.id) ||
                            (selectedAudioTrackId == "default" && track.isSelected)

                    Text(
                        text = track.displayName,
                        color = if (isSelected) ActiveCyan else Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectAudioTrack(track)
                            }
                            // 🎯 ডানে কোনো অতিরিক্ত প্যাডিং নেই (end = 4.dp)
                            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 🛡️ Backward Compatibility Overload
 */
@JvmName("LocalPlayerAudioDrawerLegacy")
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<RealAudioTrack>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (RealAudioTrack) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mappedTracks = remember(availableAudioTracks) {
        availableAudioTracks.map { legacy ->
            AdvancedAudioTrackInfo(
                id = legacy.id,
                languageCode = legacy.language,
                displayName = legacy.label,
                channelInfo = "Stereo 2.0",
                codec = "AAC",
                groupIndex = legacy.groupIndex,
                trackIndex = legacy.trackIndex,
                isSelected = legacy.isSelected
            )
        }
    }

    LocalPlayerAudioDrawer(
        availableAudioTracks = mappedTracks,
        selectedAudioTrackId = selectedAudioTrackId,
        onSelectAudioTrack = { advanced ->
            val original = availableAudioTracks.find { it.id == advanced.id }
                ?: availableAudioTracks.firstOrNull()
            if (original != null) {
                onSelectAudioTrack(original)
            }
        },
        onClose = onClose,
        modifier = modifier
    )
}
