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
 * 🎵 ছবি ১ ও ২ এর হুবহু আল্ট্রা-ক্লিন অডিও ল্যাঙ্গুয়েজ ড্রয়ার
 * কোনো অতিরিক্ত কার্ড, ব্যাজ বা ভারী ব্যাকগ্রাউন্ড ছাড়া একদম ক্লিন ফ্লোটিং টেক্সট ডিজাইন।
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<AdvancedAudioTrackInfo>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (AdvancedAudioTrackInfo) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ছবি ২ এর মতো হালকা কালো শ্যাডো ফেড গ্রেডিয়েন্ট (কোনো কড়া বা ভারী সলিড ব্যাকগ্রাউন্ড নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.25f),
            Color.Black.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.82f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(softFadeGradient)
            .padding(top = 22.dp, bottom = 20.dp, end = 24.dp, start = 14.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. ছবি ১ এর হুবহু "AUDIO LANGUAGE" হেডার + সায়ান আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 22.dp)
        ) {
            Text(
                text = "AUDIO LANGUAGE",
                color = ActiveCyan,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(2.dp)
                    .background(ActiveCyan)
            )
        }

        // =============================================================
        // ২. ছবি ১ এর হুবহু ক্লিন ফ্লোটিং ল্যাঙ্গুয়েজ টেক্সট লিস্ট (নো কার্ড, নো ব্যাজ)
        // =============================================================
        if (availableAudioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Default Audio",
                    color = ActiveCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectAudioTrack(track)
                            }
                            .padding(vertical = 2.dp)
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
