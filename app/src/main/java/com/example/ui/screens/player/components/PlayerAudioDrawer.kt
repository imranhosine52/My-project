package com.example.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎵 রিয়েল অডিও ট্র্যাক ডেটা মডেল
 */
data class RealAudioTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false
)

/**
 * 🎵 প্লেয়ার অডিও ল্যাঙ্গুয়েজ সিলেকশন সাইড ড্রয়ার
 */
@Composable
fun PlayerAudioDrawer(
    availableAudioTracks: List<RealAudioTrack>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (RealAudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 16.dp, start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ১. হেডার
        item {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "AUDIO LANGUAGE",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(2.dp)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ২. অডিও ভাষার তালিকা (Hindi, English, Bangla, Japanese ইত্যাদি)
        items(availableAudioTracks) { audioTrack ->
            val isSelected = (selectedAudioTrackId == audioTrack.id)

            Text(
                text = audioTrack.label,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clickable { onSelectAudioTrack(audioTrack) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}
