package com.example.ui.screens.localplayer.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.player.RealSubtitleTrack

private val ActiveCyan = Color(0xFF00E5FF)

/**
 * 💬 ৩ নম্বর স্ক্রিনশটের হুবহু আল্ট্রা-ক্লিন সাবটাইটেল ড্রয়ার
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

    // .srt বা .vtt ফাইল মেমরি থেকে পিক করার সিস্টেম লঞ্চার
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onExternalSubtitleLoaded(uri)
            Toast.makeText(context, "External subtitle loaded!", Toast.LENGTH_SHORT).show()
        }
    }

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
            .width(200.dp)
            .background(softFadeGradient)
            .padding(top = 22.dp, bottom = 20.dp, end = 24.dp, start = 14.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. "SUBTITLES (CC)" হেডার + আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 22.dp)
        ) {
            Text(
                text = "SUBTITLES (CC)",
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
        // ২. ক্লিন ফ্লোটিং সাবটাইটেল টেক্সট লিস্ট
        // =============================================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // সাবটাইটেল বন্ধ (Off) রাখার অপশন
            item {
                val isOffSelected = (selectedSubtitleTrackId == "off")
                Text(
                    text = "Off",
                    color = if (isOffSelected) ActiveCyan else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectSubtitleTrack(null)
                        }
                        .padding(vertical = 2.dp)
                )
            }

            // ভিডিওর এমবেডেড সাবটাইটেল ট্র্যাকগুলো
            items(
                items = availableSubtitleTracks,
                key = { it.id }
            ) { subTrack ->
                val isSelected = (selectedSubtitleTrackId == subTrack.id)
                Text(
                    text = subTrack.label,
                    color = if (isSelected) ActiveCyan else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectSubtitleTrack(subTrack)
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =============================================================
        // ৩. এক্সটার্নাল সাবটাইটেল যোগ করার অপশন (+ Add External)
        // =============================================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    subtitleFilePicker.launch("*/*")
                }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = ActiveCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Add External (.srt)",
                color = ActiveCyan,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
