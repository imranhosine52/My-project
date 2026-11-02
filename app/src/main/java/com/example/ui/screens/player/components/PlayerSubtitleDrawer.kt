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
 * 💬 রিয়েল সাবটাইটেল ট্র্যাক ডেটা মডেল
 */
data class RealSubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false
)

/**
 * 💬 প্লেয়ার সাবটাইটেল সিলেকশন সাইড ড্রয়ার
 */
@Composable
fun PlayerSubtitleDrawer(
    availableSubtitleTracks: List<RealSubtitleTrack>,
    selectedSubtitleTrackId: String,
    onSelectSubtitleTrack: (RealSubtitleTrack?) -> Unit,
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
                    text = "SUBTITLES",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(2.dp)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ২. সাবটাইটেল বন্ধ রাখার অপশন ("Off")
        item {
            val isOffSelected = (selectedSubtitleTrackId == "off")
            Text(
                text = "Off",
                color = if (isOffSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 13.5.sp,
                fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clickable { onSelectSubtitleTrack(null) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        // ৩. উপলব্ধ সাবটাইটেল ট্র্যাক তালিকা
        items(availableSubtitleTracks) { subTrack ->
            val isSelected = (selectedSubtitleTrackId == subTrack.id)

            Text(
                text = subTrack.label,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clickable { onSelectSubtitleTrack(subTrack) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}
