package com.example.ui.screens.localplayer.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdvancedSubtitleTrackInfo
import com.example.ui.screens.player.RealSubtitleTrack

private val ActiveCyan = Color(0xFF00E5FF)
private val EmeraldActive = Color(0xFF00E676)
private val TextMutedSlate = Color(0xFF8E95A5)
private val CardBackground = Color(0xFF101726)
private val BorderSubtle = Color(0xFF1F2B42)

/**
 * 💬 আধুনিক ও অ্যাডভান্সড সাবটাইটেল ড্রয়ার (MX Player Style)
 * এমবেডেড সাবটাইটেল ট্র্যাক, অফ অপশন, এক্সটার্নাল SRT লোডার এবং ডিলে সিনক্রোনাইজেশন সাপোর্ট করে।
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

    // .srt, .vtt, .ass বা .ssa ফাইল মেমরি থেকে পিক করার সিস্টেম লঞ্চার
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onExternalSubtitleLoaded(uri)
            Toast.makeText(context, "External subtitle loaded successfully!", Toast.LENGTH_SHORT).show()
        }
    }

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
            .width(245.dp)
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
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = ActiveCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SUBTITLES (CC)",
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
        // ২. সাবটাইটেল ট্র্যাক লিস্ট (Off অপশন + এমবেডেড ট্র্যাকসমূহ)
        // =============================================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ২.১ অফ (Off) অপশন
            item {
                val isOffSelected = (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId.isBlank())
                SubtitleOptionCard(
                    title = "Off (Disable)",
                    subtitle = "Hide all subtitles",
                    badge = null,
                    isSelected = isOffSelected,
                    onClick = { onSelectSubtitleTrack(null) }
                )
            }

            // ২.২ ভিডিওর সব এমবেডেড ও এক্সটার্নাল সাবটাইটেল
            items(
                items = availableSubtitleTracks,
                key = { it.id }
            ) { subTrack ->
                val isSelected = (selectedSubtitleTrackId == subTrack.id)
                SubtitleOptionCard(
                    title = subTrack.displayName,
                    subtitle = if (subTrack.isExternal) "External file" else "Embedded in video",
                    badge = if (subTrack.isExternal) "EXT" else subTrack.languageCode.uppercase().take(3),
                    isSelected = isSelected,
                    onClick = { onSelectSubtitleTrack(subTrack) }
                )
            }

            // ২.৩ এক্সটার্নাল সাবটাইটেল যোগ করার বাটন (+ Add External)
            item {
                Surface(
                    onClick = { subtitleFilePicker.launch("*/*") },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131B2A),
                    border = BorderStroke(0.8.dp, ActiveCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ActiveCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add External (.srt / .vtt)",
                            color = ActiveCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // =============================================================
        // ৩. সাবটাইটেল সাইজ কন্ট্রোল (Small, Normal, Large)
        // =============================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "TEXT SIZE",
                color = TextMutedSlate,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sizes = listOf(
                    Triple(0.85f, "Small", "S"),
                    Triple(1.0f, "Normal", "M"),
                    Triple(1.25f, "Large", "L")
                )

                sizes.forEach { (scale, _, label) ->
                    val isSel = (currentTextSizeScale == scale)
                    Surface(
                        onClick = { onSubtitleSizeScaleChanged(scale) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) ActiveCyan else CardBackground,
                        border = BorderStroke(0.6.dp, if (isSel) ActiveCyan else BorderSubtle),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =============================================================
        // ৪. সাবটাইটেল সিনক্রোনাইজেশন / ডিলে কন্ট্রোল (+/- Delay)
        // =============================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSubtitleDelayMs == 0L) "Synced" else "${currentSubtitleDelayMs}ms",
                    color = if (currentSubtitleDelayMs != 0L) EmeraldActive else TextMutedSlate,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SYNC DELAY",
                    color = TextMutedSlate,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -100ms
                Surface(
                    onClick = { onSubtitleDelayChanged(currentSubtitleDelayMs - 100L) },
                    shape = RoundedCornerShape(6.dp),
                    color = CardBackground,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("-0.1s", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Reset
                Surface(
                    onClick = { onSubtitleDelayChanged(0L) },
                    shape = RoundedCornerShape(6.dp),
                    color = CardBackground,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("Reset", color = TextMutedSlate, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // +100ms
                Surface(
                    onClick = { onSubtitleDelayChanged(currentSubtitleDelayMs + 100L) },
                    shape = RoundedCornerShape(6.dp),
                    color = CardBackground,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("+0.1s", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 🎛️ সাবটাইটেল অপশন কার্ড
 */
@Composable
private fun SubtitleOptionCard(
    title: String,
    subtitle: String,
    badge: String?,
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
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

            // ডান পাশে: টেক্সট ও ব্যাজ
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF1B2436)
                        ) {
                            Text(
                                text = badge,
                                color = ActiveCyan,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 0.5.dp)
                            )
                        }
                    }

                    Text(
                        text = title,
                        color = if (isSelected) ActiveCyan else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(1.5.dp))

                Text(
                    text = subtitle,
                    color = TextMutedSlate,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 🛡️ Backward Compatibility Overload:
 * পুরোনো RealSubtitleTrack কলের সাথে ১০০% সামঞ্জস্য রক্ষা করে।
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
