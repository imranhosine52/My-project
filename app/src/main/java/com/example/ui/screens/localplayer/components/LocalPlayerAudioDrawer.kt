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
import com.example.ui.screens.player.RealAudioTrack

private val ActiveCyan = Color(0xFF00E5FF)

/**
 * 🎵 ৩ নম্বর স্ক্রিনশটের হুবহু আল্ট্রা-ক্লিন অডিও ল্যাঙ্গুয়েজ ড্রয়ার
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<RealAudioTrack>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (RealAudioTrack) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ৩ নম্বর ছবির মতো হালকা কালো শ্যাডো ফেড (কোনো সলিড ব্যাকগ্রাউন্ড নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.35f),
            Color.Black.copy(alpha = 0.70f),
            Color.Black.copy(alpha = 0.88f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(190.dp)
            .background(softFadeGradient)
            .padding(top = 22.dp, bottom = 20.dp, end = 24.dp, start = 14.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. ৩ নম্বর ছবির মতো "AUDIO LANGUAGE" হেডার + আন্ডারলাইন
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
        // ২. ৩ নম্বর ছবির মতো ক্লিন ফ্লোটিং ল্যাঙ্গুয়েজ টেক্সট লিস্ট
        // =============================================================
        val trackList = if (availableAudioTracks.isEmpty()) {
            listOf(
                RealAudioTrack(
                    id = "default",
                    language = "und",
                    label = "Hindi",
                    groupIndex = 0,
                    trackIndex = 0,
                    isSelected = true
                )
            )
        } else {
            availableAudioTracks
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(
                items = trackList,
                key = { it.id }
            ) { audioTrack ->
                val isSelected = (selectedAudioTrackId == audioTrack.id) ||
                        (selectedAudioTrackId == "default" && audioTrack == trackList.first())

                Text(
                    text = audioTrack.label,
                    color = if (isSelected) ActiveCyan else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectAudioTrack(audioTrack)
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
