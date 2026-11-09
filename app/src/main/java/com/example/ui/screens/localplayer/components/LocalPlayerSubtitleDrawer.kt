package com.example.ui.screens.localplayer.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
private val TextMutedSlate = Color(0xFF8E95A5)

/**
 * 💬 আল্ট্রা-ক্লিন সাবটাইটেল ড্রয়ার ও কন্ট্রোলার
 * প্রান্ত ঘেঁষা ডিজাইন, কমপ্যাক্ট লাইন স্পেসিং, হালকা ব্যাকগ্রাউন্ড এবং সাইজ/ফন্ট/কালার কাস্টমাইজেশন অপশন।
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
    currentFontFamily: String = "DEFAULT",
    onFontFamilyChanged: (String) -> Unit = {},
    currentBgStyle: String = "SEMI_TRANSPARENT",
    onBgStyleChanged: (String) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showStyleSettings by remember { mutableStateOf(false) }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onExternalSubtitleLoaded(uri)
            Toast.makeText(context, "External subtitle loaded!", Toast.LENGTH_SHORT).show()
        }
    }

    // স্ক্রিনশটের মতো অত্যন্ত হালকা কালো গ্রেডিয়েন্ট (কোনো চারকোনা বা কড়া ভারী ব্যাকগ্রাউন্ড নেই)
    val softFadeGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.18f),
            Color.Black.copy(alpha = 0.45f),
            Color.Black.copy(alpha = 0.72f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 175.dp, max = 225.dp)
            .background(softFadeGradient)
            // 🎯 সাইডের অতিরিক্ত স্পেস সম্পূর্ণ দূর করা হয়েছে (end = 8.dp)
            .padding(top = 18.dp, bottom = 16.dp, end = 8.dp, start = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // =============================================================
        // ১. "SUBTITLES" হেডার + সায়ান আন্ডারলাইন
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 14.dp, end = 4.dp)
        ) {
            Text(
                text = "SUBTITLES",
                color = ActiveCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(2.dp)
                    .background(ActiveCyan)
            )
        }

        // =============================================================
        // ২. কাছাকাছি সাজানো কম্প্যাক্ট সাবটাইটেল টেক্সট লিস্ট
        // =============================================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            // 🎯 অতিরিক্ত ফাঁকা জায়গা কমিয়ে চাপিয়ে রাখা হয়েছে
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // ২.১ সাবটাইটেল বন্ধ (Off)
            item {
                val isOffSelected = (selectedSubtitleTrackId == "off" || selectedSubtitleTrackId.isBlank())
                Text(
                    text = "Off",
                    color = if (isOffSelected) ActiveCyan else Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectSubtitleTrack(null)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
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
                    fontSize = 14.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectSubtitleTrack(subTrack)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // ২.৩ এক্সটার্নাল সাবটাইটেল যোগ করার বাটন (+ Add External)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            subtitleFilePicker.launch("*/*")
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = ActiveCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Add External (.srt)",
                        color = ActiveCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // =============================================================
        // ⚙️ ৩. সাবটাইটেল কন্ট্রোলার অপশন (ছোট-বড়, ফন্ট ও ব্যাকগ্রাউন্ড)
        // =============================================================
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 4.dp)
        ) {
            // ড্রপডাউন টগল বাটন
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showStyleSettings = !showStyleSettings
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Style & Size",
                    color = if (showStyleSettings) ActiveCyan else TextMutedSlate,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showStyleSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (showStyleSettings) ActiveCyan else TextMutedSlate,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showStyleSettings) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    // ৩.১ সাইজ ছোট-বড় (Text Size)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Size:", color = TextMutedSlate, fontSize = 10.sp)
                        val sizes = listOf(
                            Pair(0.80f, "S"),
                            Pair(1.0f, "M"),
                            Pair(1.25f, "L"),
                            Pair(1.50f, "XL")
                        )
                        sizes.forEach { (scale, label) ->
                            val isSel = (currentTextSizeScale == scale)
                            Surface(
                                onClick = { onSubtitleSizeScaleChanged(scale) },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) ActiveCyan else Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, if (isSel) ActiveCyan else Color(0xFF2E384D)),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // ৩.২ ফন্ট পরিবর্তন (Font Family)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Font:", color = TextMutedSlate, fontSize = 10.sp)
                        val fonts = listOf(
                            Pair("DEFAULT", "Sans"),
                            Pair("SERIF", "Serif"),
                            Pair("MONOSPACE", "Mono")
                        )
                        fonts.forEach { (fontKey, label) ->
                            val isSel = (currentFontFamily == fontKey)
                            Surface(
                                onClick = { onFontFamilyChanged(fontKey) },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) ActiveCyan else Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, if (isSel) ActiveCyan else Color(0xFF2E384D)),
                                modifier = Modifier.height(22.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // ৩.৩ ব্যাকগ্রাউন্ড কালার ও স্বচ্ছতা সমন্বয় (Background Color)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Bg:", color = TextMutedSlate, fontSize = 10.sp)
                        val bgStyles = listOf(
                            Pair("NONE", "None"),
                            Pair("SEMI_TRANSPARENT", "Semi"),
                            Pair("SOLID_BLACK", "Solid"),
                            Pair("YELLOW_BLACK", "Yellow")
                        )
                        bgStyles.forEach { (bgKey, label) ->
                            val isSel = (currentBgStyle == bgKey)
                            Surface(
                                onClick = { onBgStyleChanged(bgKey) },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) ActiveCyan else Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, if (isSel) ActiveCyan else Color(0xFF2E384D)),
                                modifier = Modifier.height(22.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
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
