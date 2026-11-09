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
import com.example.ui.screens.player.RealAudioTrack

/**
 * 🎵 প্লেয়ার মাল্টি-অডিও ট্র্যাক সিলেকশন সাইড ড্রয়ার
 * MP4 ও MKV-এর Multiple Audio Channels, Language, Codec এবং ডিফল্ট ট্র্যাক সাপোর্ট করে।
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

        // ২. যদি কোনো অতিরিক্ত অডিও ট্র্যাক না থাকে
        if (availableAudioTracks.isEmpty()) {
            item {
                Text(
                    text = "Default Audio",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        } else {
            // ৩. অ্যাডভান্সড অডিও ভাষার তালিকা
            items(
                items = availableAudioTracks,
                key = { it.id }
            ) { audioTrack ->
                val isSelected = (selectedAudioTrackId == audioTrack.id)
                val isSupported = audioTrack.isSupported

                // চ্যানেল ও কোডেক ইনফরমেশন স্ট্রিং তৈরি
                val detailsText = buildString {
                    if (audioTrack.channelInfo.isNotBlank()) {
                        append(audioTrack.channelInfo)
                    } else if (audioTrack.channelCount > 2) {
                        append("${audioTrack.channelCount}.1 Surround")
                    } else if (audioTrack.channelCount == 2) {
                        append("Stereo")
                    } else if (audioTrack.channelCount == 1) {
                        append("Mono")
                    }

                    if (audioTrack.codec.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(audioTrack.codec.uppercase())
                    }

                    if (audioTrack.isDefault && !isSelected) {
                        if (isNotEmpty()) append(" • ")
                        append("Default")
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isSupported) {
                            if (isSupported) {
                                onSelectAudioTrack(audioTrack)
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // যদি ডিভাইসের ডিকোডার এই কোডেক সাপোর্ট না করে
                        if (!isSupported) {
                            Text(
                                text = "Unsupported",
                                color = Color(0xFFEF5350),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = audioTrack.label.ifBlank { audioTrack.language.ifBlank { "Audio" } },
                            color = when {
                                isSelected -> Color(0xFF00E5FF)
                                !isSupported -> Color(0xFF757575)
                                else -> Color.White
                            },
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    // সাব-ইনফো (চ্যানেল, কোডেক ইত্যাদি)
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
}
