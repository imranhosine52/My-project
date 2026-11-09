package com.example.ui.screens.player.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.player.RealAudioTrack

/**
 * 🎵 ১ নম্বর ছবির মতো হুবহু ক্লিন ও স্লিম অডিও ল্যাঙ্গুয়েজ ড্রয়ার
 * অতিরিক্ত স্টেরিও/কোডেক টেক্সট ছাড়া শুধুমাত্র ভাষার নাম ডানপাশে কম্প্যাক্টভাবে প্রদর্শিত হবে।
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
            .padding(top = 18.dp, end = 16.dp, start = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ১. হেডার (১ নম্বর ছবির মতো আন্ডারলাইনসহ)
        item {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "AUDIO LANGUAGE",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(2.dp)
                        .background(Color(0xFF00E5FF))
                )
            }
        }

        // ২. যদি কোনো অডিও ট্র্যাক না থাকে
        if (availableAudioTracks.isEmpty()) {
            item {
                Text(
                    text = "Default",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            // ৩. ১ নম্বর ছবির হুবহু ভাষার তালিকা (কোনো সাবটেক্সট বা অতিরিক্ত লেখা ছাড়া)
            items(
                items = availableAudioTracks,
                key = { it.id }
            ) { audioTrack ->
                val isSelected = (selectedAudioTrackId == audioTrack.id)
                val languageName = audioTrack.label.ifBlank {
                    audioTrack.language.ifBlank { "Audio" }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (audioTrack.isSupported) {
                                onSelectAudioTrack(audioTrack)
                            }
                        }
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = languageName,
                        color = when {
                            isSelected -> Color(0xFF00E5FF) // সিলেক্টেড হলে ১ নম্বর ছবির মতো উজ্জ্বল সাইয়ান কালার
                            !audioTrack.isSupported -> Color(0xFF6B7280)
                            else -> Color.White // আনসিলেক্টেড হলে সাদা
                        },
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
