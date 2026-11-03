package com.example.ui.screens.localplayer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.player.RealAudioTrack

private val DrawerBg = Color(0xFF10141E)
private val ActiveCyan = Color(0xFF00E5FF)
private val CardBorderColor = Color(0xFF1E2638)

/**
 * 🎵 লোকাল ভিডিও মাল্টি-অডিও সিলেকশন সাইড ড্রয়ার
 */
@Composable
fun LocalPlayerAudioDrawer(
    availableAudioTracks: List<RealAudioTrack>,
    selectedAudioTrackId: String,
    onSelectAudioTrack: (RealAudioTrack) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DrawerBg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // ১. হেডার ও ক্লোজ বাটন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ActiveCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Audio Tracks",
                        tint = ActiveCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column {
                    Text(
                        text = "Audio Track",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${availableAudioTracks.size} available",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

        Spacer(modifier = Modifier.height(10.dp))

        // ২. কোনো ট্র্যাক না পাওয়া গেলে নোটিশ
        if (availableAudioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Default Audio (Single Track)",
                    color = Color(0xFF8E95A5),
                    fontSize = 12.5.sp
                )
            }
        } else {
            // ৩. অডিও ভাষাগুলোর মাখনের মতো স্মুথ লিস্ট
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = availableAudioTracks,
                    key = { it.id }
                ) { audioTrack ->
                    val isSelected = (selectedAudioTrackId == audioTrack.id)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF0C2430) else Color(0xFF161B28),
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) ActiveCyan else CardBorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAudioTrack(audioTrack) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = audioTrack.label,
                                    color = if (isSelected) ActiveCyan else Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val langCode = if (audioTrack.language.isNotBlank() && audioTrack.language != "und") {
                                    audioTrack.language.uppercase()
                                } else "STEREO"

                                Text(
                                    text = langCode,
                                    color = Color(0xFF8E95A5),
                                    fontSize = 10.5.sp
                                )
                            }

                            // অ্যাক্টিভ চেক মার্ক
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(ActiveCyan),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
