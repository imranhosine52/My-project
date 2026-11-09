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
import com.example.ui.screens.player.RealSubtitleTrack

/**
 * 💬 প্লেয়ার মাল্টি-সাবটাইটেল সিলেকশন সাইড ড্রয়ার
 * Embedded (SRT, VTT, ASS/SSA) এবং External Subtitles, Default/Forced ট্যাগ এবং On/Off হ্যান্ডলিং সাপোর্ট করে।
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

        // ২. সাবটাইটেল বন্ধ রাখার অপশন (Off)
        item {
            val isOffSelected = (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId.isBlank())

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSubtitleTrack(null) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = "Off",
                    color = if (isOffSelected) Color(0xFF00E5FF) else Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // ৩. উপলব্ধ সাবটাইটেল ট্র্যাক তালিকা
        items(
            items = availableSubtitleTracks,
            key = { it.id }
        ) { subTrack ->
            val isSelected = (selectedSubtitleTrackId == subTrack.id)
            val isSupported = subTrack.isSupported

            // ফরম্যাট শনাক্তকরণ (SRT / VTT / ASS / SSA)
            val formatName = when {
                subTrack.isAssOrSsa -> "ASS"
                subTrack.isVtt -> "VTT"
                subTrack.isSrt -> "SRT"
                subTrack.mimeType.isNotBlank() -> subTrack.mimeType.substringAfterLast("/").substringAfterLast("-").uppercase()
                else -> ""
            }

            // সাব-ইনফো স্ট্রিং (Embedded / External • Format • Forced / Default)
            val detailsText = buildString {
                if (subTrack.isExternal) {
                    append("External")
                } else {
                    append("Embedded")
                }

                if (formatName.isNotBlank()) {
                    append(" • ").append(formatName)
                }

                if (subTrack.isForced) {
                    append(" • Forced")
                } else if (subTrack.isDefault && !isSelected) {
                    append(" • Default")
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isSupported) {
                        if (isSupported) {
                            onSelectSubtitleTrack(subTrack)
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ডিভাইসে কোনো সাবটাইটেল ফরম্যাট ডিকোড না করা গেলে Unsupported ট্যাগ
                    if (!isSupported) {
                        Text(
                            text = "Unsupported",
                            color = Color(0xFFEF5350),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = subTrack.displayLabel,
                        color = when {
                            isSelected -> Color(0xFF00E5FF)
                            !isSupported -> Color(0xFF757575)
                            else -> Color.White
                        },
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // সাবটাইটেল টাইপ ও ফরম্যাটের বিবরণী
                if (detailsText.isNotBlank() && isSupported) {
                    Text(
                        text = detailsText,
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.75f) else Color(0xFF8E95A5),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
