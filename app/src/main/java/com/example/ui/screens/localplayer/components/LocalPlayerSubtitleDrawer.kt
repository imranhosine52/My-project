package com.example.ui.screens.localplayer.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.player.RealSubtitleTrack

private val DrawerBg = Color(0xFF10141E)
private val ActiveCyan = Color(0xFF00E5FF)
private val CardBorderColor = Color(0xFF1E2638)

/**
 * 💬 লোকাল ভিডিও মাল্টি-সাবটাইটেল সিলেকশন ও এক্সটার্নাল সাবটাইটেল লোডার সাইড ড্রয়ার
 */
@Composable
fun LocalPlayerSubtitleDrawer(
    availableSubtitleTracks: List<RealSubtitleTrack>,
    selectedSubtitleTrackId: String,
    onSelectSubtitleTrack: (RealSubtitleTrack?) -> Unit,
    onExternalSubtitleLoaded: (Uri) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 📁 মেমরি থেকে বাহ্যিক .srt বা .vtt সাবটাইটেল ফাইল লোড করার সিস্টেম পিকার
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onExternalSubtitleLoaded(uri)
            Toast.makeText(context, "External subtitle loaded successfully!", Toast.LENGTH_SHORT).show()
        }
    }

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
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        tint = ActiveCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column {
                    Text(
                        text = "Subtitles (CC)",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (availableSubtitleTracks.isEmpty()) "No embedded tracks" else "${availableSubtitleTracks.size} tracks found",
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

        // ২. সাবটাইটেল তালিকা
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ২.১) সাবটাইটেল বন্ধ (Off) রাখার অপশন
            item {
                val isOffSelected = (selectedSubtitleTrackId == "off")

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOffSelected) Color(0xFF0C2430) else Color(0xFF161B28),
                    border = BorderStroke(
                        width = if (isOffSelected) 1.2.dp else 0.8.dp,
                        color = if (isOffSelected) ActiveCyan else CardBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubtitleTrack(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SubtitlesOff,
                                contentDescription = null,
                                tint = if (isOffSelected) ActiveCyan else Color(0xFF8E95A5),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Turn Off Subtitles",
                                color = if (isOffSelected) ActiveCyan else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        if (isOffSelected) {
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

            // ২.২) ভিডিওতে থাকা এমবেডেড সাবটাইটেল ট্র্যাকগুলো
            items(
                items = availableSubtitleTracks,
                key = { it.id }
            ) { subTrack ->
                val isSelected = (selectedSubtitleTrackId == subTrack.id)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF0C2430) else Color(0xFF161B28),
                    border = BorderStroke(
                        width = if (isSelected) 1.2.dp else 0.8.dp,
                        color = if (isSelected) ActiveCyan else CardBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubtitleTrack(subTrack) }
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
                                text = subTrack.label,
                                color = if (isSelected) ActiveCyan else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val langCode = if (subTrack.language.isNotBlank() && subTrack.language != "und") {
                                subTrack.language.uppercase()
                            } else "EMBEDDED"

                            Text(
                                text = langCode,
                                color = Color(0xFF8E95A5),
                                fontSize = 10.5.sp
                            )
                        }

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

        Spacer(modifier = Modifier.height(10.dp))

        // ৩. মেমরি থেকে বাহ্যিক সাবটাইটেল লোড করার বাটন (+ Add External Subtitle)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF151E2E),
            border = BorderStroke(1.dp, ActiveCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // সাবটাইটেল ফাইল ফিল্টার
                    subtitleFilePicker.launch("*/*")
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add External Subtitle",
                    tint = ActiveCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Load External Subtitle (.srt/.vtt)",
                    color = ActiveCyan,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
