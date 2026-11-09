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
 * স্ক্রিনের একদম সাইড থেকে শুরু (জিরো মার্জিন), লাইনের মধ্যবর্তী কমপ্যাক্ট স্পেসিং এবং হালকা ব্যাকগ্রাউন্ড।
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<AdvancedAudioTrackInfo>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (AdvancedAudioTrackInfo) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // স্ক্রিনশটের মতো অত্যন্ত হালকা কালো গ্রেডিয়েন্ট (কোনো চারকোনা বা কড়া ভারী ব্যাকগ্রাউন্ড নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.18f),
            Color.Black.copy(alpha = 0.45f),
            Color.Black.copy(alpha = 0.72f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 160.dp, max = 210.dp)
            .background(softFadeGradient)
            // 🎯 সাইডের অতিরিক্ত স্পেস সম্পূর্ণ দূর করা হয়েছে (end = 8.dp)
            .padding(top = 18.dp, bottom = 16.dp, end = 8.dp, start = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. "AUDIO LANGUAGE" হেডার + আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 14.dp, end = 4.dp)
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
        // ২. কাছাকাছি সাজানো কম্প্যাক্ট ল্যাঙ্গুয়েজ টেক্সট লিস্ট
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
                // 🎯 অতিরিক্ত ফাঁকা জায়গা কমিয়ে সুন্দরভাবে কাছাকাছি (চাপিয়ে) রাখা হয়েছে
                verticalArrangement = Arrangement.spacedBy(7.dp)
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
                            .padding(horizontal = 6.dp, vertical = 3.dp)
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
