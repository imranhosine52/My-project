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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdvancedSubtitleTrackInfo
import com.example.ui.screens.player.RealSubtitleTrack

private val ActiveCyan = Color(0xFF00E5FF)

/**
 * 💬 অডিও ড্রয়ারের হুবহু আল্ট্রা-ক্লিন সাবটাইটেল ড্রয়ার (Minimal Floating Text & Soft Fade)
 * কোনো অতিরিক্ত কার্ড বা ভারী ব্যাকগ্রাউন্ড ছাড়া একদম পরিষ্কার টেক্সট ডিজাইন।
 */
@Composable
fun LocalPlayerSubtitleDrawer(
    availableSubtitleTracks: List<AdvancedSubtitleTrackInfo>,
    selectedSubtitleTrackId: String,
    onSelectSubtitleTrack: (AdvancedSubtitleTrackInfo?) -> Unit,
    onExternalSubtitleLoaded: (Uri) -> Unit,
    currentSubtitleDelayMs: Long = 0L,
    onSubtitleDelayChanged: (Long) -> Unit = {},
    currentTextSizeScale: Float = 1.0f,
    onSubtitleSizeScaleChanged: (Float) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // .srt, .vtt, .ass ফাইল পিক করার সিস্টেম লঞ্চার
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onExternalSubtitleLoaded(uri)
            Toast.makeText(context, "External subtitle loaded!", Toast.LENGTH_SHORT).show()
        }
    }

    // ছবি ২ এর মতো হালকা কালো শ্যাডো ফেড গ্রেডিয়েন্ট (কোনো কড়া বা ভারী সলিড ব্যাকগ্রাউন্ড নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.25f),
            Color.Black.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.82f)
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
        // ১. ছবি ১ এর হুবহু "SUBTITLES" হেডার + সায়ান আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 22.dp)
        ) {
            Text(
                text = "SUBTITLES",
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
        // ২. ক্লিন ফ্লোটিং সাবটাইটেল টেক্সট লিস্ট (অডিও ড্রয়ারের মতো)
        // =============================================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ২.১ সাবটাইটেল বন্ধ (Off) অপশন
            item {
                val isOffSelected = (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId.isBlank())
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

            // ২.২ ভিডিওর সব এমবেডেড ও এক্সটার্নাল সাবটাইটেল
            items(
                items = availableSubtitleTracks,
                key = { it.id }
            ) { subTrack ->
                val isSelected = (selectedSubtitleTrackId == subTrack.id)

                Text(
                    text = subTrack.displayName,
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

            // ২.৩ এক্সটার্নাল সাবটাইটেল যুক্ত করার ক্লিন বাটন (+ Add External)
            item {
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
                        .padding(top = 10.dp, bottom = 4.dp)
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
    }
}

/**
 * 🛡️ Backward Compatibility Overload
 */
@JvmName("LocalPlayerSubtitleDrawerLegacy")
@Composable
fun LocalPlayerSubtitleDrawer(
    availableSubtitleTracks: List<RealSubtitleTrack>,
    selectedSubtitleTrackId: String,
    onSelectSubtitleTrack: (RealSubtitleTrack?) -> Unit,
    onExternalSubtitleLoaded: (Uri) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mappedTracks = remember(availableSubtitleTracks) {
        availableSubtitleTracks.map { legacy ->
            AdvancedSubtitleTrackInfo(
                id = legacy.id,
                languageCode = legacy.language,
                displayName = legacy.label,
                isExternal = legacy.id.startsWith("ext_"),
                groupIndex = legacy.groupIndex,
                trackIndex = legacy.trackIndex,
                isSelected = legacy.isSelected
            )
        }
    }

    LocalPlayerSubtitleDrawer(
        availableSubtitleTracks = mappedTracks,
        selectedSubtitleTrackId = selectedSubtitleTrackId,
        onSelectSubtitleTrack = { adv ->
            if (adv == null) {
                onSelectSubtitleTrack(null)
            } else {
                val orig = availableSubtitleTracks.find { it.id == adv.id }
                    ?: availableSubtitleTracks.firstOrNull()
                onSelectSubtitleTrack(orig)
            }
        },
        onExternalSubtitleLoaded = onExternalSubtitleLoaded,
        onClose = onClose,
        modifier = modifier
    )
}
