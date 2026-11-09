package com.example.ui.screens.localplayer.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdvancedAudioTrackInfo
import com.example.ui.screens.player.RealAudioTrack

private val ActiveCyan = Color(0xFF00E5FF)
private val EmeraldActive = Color(0xFF00E676)
private val TextMutedSlate = Color(0xFF8E95A5)
private val CardBackground = Color(0xFF101726)
private val BorderSubtle = Color(0xFF1F2B42)

/**
 * 🎵 আল্ট্রা-ক্লিন ও প্রিমিয়াম অডিও ল্যাঙ্গুয়েজ ড্রয়ার (MX Player Style)
 * MP4 ও MKV ফাইলের সমস্ত এমবেডেড অডিও ট্র্যাক, ল্যাঙ্গুয়েজ কোড, চ্যানেল ও কোডেক প্রদর্শন করে।
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<AdvancedAudioTrackInfo>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (AdvancedAudioTrackInfo) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // প্রিমিয়াম আল্ট্রা-ডার্ক ফেড ব্যাকগ্রাউন্ড
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.40f),
            Color.Black.copy(alpha = 0.80f),
            Color.Black.copy(alpha = 0.95f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(235.dp)
            .background(softFadeGradient)
            .padding(top = 18.dp, bottom = 18.dp, end = 16.dp, start = 14.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. হেডার ও ক্লোজ বাটন
        // =============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextMutedSlate,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = ActiveCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AUDIO TRACKS",
                        color = ActiveCyan,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(ActiveCyan)
                )
            }
        }

        // =============================================================
        // ২. ট্র্যাক কাউন্ট ও বিবরণ
        // =============================================================
        if (availableAudioTracks.isNotEmpty()) {
            Text(
                text = "${availableAudioTracks.size} Track(s) Available",
                color = TextMutedSlate,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // =============================================================
        // ৩. অডিও ট্র্যাক তালিকা (ল্যাঙ্গুয়েজ, চ্যানেল ও কোডেক ব্যাজ সহ)
        // =============================================================
        if (availableAudioTracks.isEmpty()) {
            // Requirement 8: অডিও ট্র্যাক না থাকলে উপযুক্ত মেসেজ
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextMutedSlate,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Default Audio Only",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No additional audio tracks found in this video.",
                        color = TextMutedSlate,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = availableAudioTracks,
                    key = { it.id }
                ) { track ->
                    val isSelected = (selectedAudioTrackId == track.id) ||
                            (selectedAudioTrackId == "default" && track.isSelected)

                    AudioTrackItemCard(
                        track = track,
                        isSelected = isSelected,
                        onClick = { onSelectAudioTrack(track) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // নিচে হেল্পার ইনফো
        Text(
            text = "Seamless switching without pausing playback",
            color = Color(0xFF5A667D),
            fontSize = 9.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * 🎛️ একক অডিও ট্র্যাক কার্ড
 */
@Composable
private fun AudioTrackItemCard(
    track: AdvancedAudioTrackInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF0F2633) else CardBackground,
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.6.dp,
            color = if (isSelected) ActiveCyan else BorderSubtle
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // বাম পাশে: সিলেক্টেড চেকমার্ক
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ActiveCyan else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ActiveCyan else TextMutedSlate,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // ডান পাশে: টাইটেল ও ব্যাজসমূহ
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            ) {
                Text(
                    text = track.displayName,
                    color = if (isSelected) ActiveCyan else Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // কোডেক ব্যাজ (যেমন: AAC, AC3)
                    if (track.codec.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF1A2336)
                        ) {
                            Text(
                                text = track.codec,
                                color = Color(0xFF90A4CE),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // চ্যানেল ইনফো ব্যাজ (যেমন: Stereo, 5.1)
                    if (track.channelInfo.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = if (track.channelInfo.contains("5.1") || track.channelInfo.contains("7.1")) Color(0xFF261D10) else Color(0xFF132029),
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (track.channelInfo.contains("5.1")) Color(0xFFFFB300).copy(alpha = 0.7f) else ActiveCyan.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = track.channelInfo,
                                color = if (track.channelInfo.contains("5.1")) Color(0xFFFFB300) else ActiveCyan,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🛡️ Backward Compatibility Overload:
 * প্রজেক্টের যেকোনো পুরোনো ক্লাসের সাথে ১০০% কম্প্যাটিবিলিটি রক্ষা করে যাতে কখনো কম্পাইল এরর না আসে।
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
                channelInfo = if (legacy.label.contains("5.1", true)) "5.1 Surround" else "Stereo 2.0",
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
