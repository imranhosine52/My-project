package com.example.ui.screens.player

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎵 শর্টস ও লং প্লেয়ার উভয়ের জন্য অ্যাডভান্সড মাল্টি-অডিও ট্র্যাক মডেল
 * MP4 ও MKV-এর Multiple Audio Stream, Language Code, Codec ও Channel সমর্থন করে।
 */
data class RealAudioTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false,
    // 🚀 নতুন ফিচার ও অ্যাডভান্সড ট্র্যাক মেটাডাটা (১০০% ব্যাকওয়ার্ড কমপ্যাটিবল)
    val languageCode: String = language,
    val channelCount: Int = 2,
    val channelInfo: String = "",       // e.g. "Stereo (2.0)", "Surround 5.1"
    val codec: String = "",             // e.g. "AAC", "AC3", "E-AC3", "Opus", "MP3"
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val isDefault: Boolean = false,
    val isSupported: Boolean = true
) {
    val displayLanguage: String
        get() = label.ifBlank { language.ifBlank { "Audio" } }

    val formattedDescription: String
        get() = buildString {
            append(displayLanguage)
            if (channelInfo.isNotBlank()) {
                append(" • ").append(channelInfo)
            } else if (channelCount > 2) {
                append(" • ").append("${channelCount}.1 Surround")
            }
            if (codec.isNotBlank()) {
                append(" (").append(codec.uppercase()).append(")")
            }
        }
}

/**
 * 💬 শর্টস ও লং প্লেয়ার উভয়ের জন্য অ্যাডভান্সড মাল্টি-সাবটাইটেল ট্র্যাক মডেল
 * Embedded (SRT, VTT, ASS/SSA) এবং External সাবটাইটেল সমর্থন করে।
 */
data class RealSubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false,
    // 🚀 নতুন ফিচার ও অ্যাডভান্সড সাবটাইটেল মেটাডাটা (১০০% ব্যাকওয়ার্ড কমপ্যাটিবল)
    val languageCode: String = language,
    val mimeType: String = "",          // e.g. "text/vtt", "application/x-subrip", "text/x-ssa"
    val isExternal: Boolean = false,
    val externalUri: Uri? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isSupported: Boolean = true
) {
    val displayLabel: String
        get() = label.ifBlank { language.ifBlank { "Subtitle" } }

    val isAssOrSsa: Boolean
        get() = mimeType.contains("ssa", ignoreCase = true) || mimeType.contains("ass", ignoreCase = true)

    val isVtt: Boolean
        get() = mimeType.contains("vtt", ignoreCase = true)

    val isSrt: Boolean
        get() = mimeType.contains("subrip", ignoreCase = true) || mimeType.contains("srt", ignoreCase = true)
}

/**
 * 🌟 PD Flix Brand Logo (শর্টস ও লং প্লেয়ারের স্টার্টআপ স্ক্রিনের জন্য)
 */
@Composable
fun PdFlixBrandLogo(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "P",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFF00F0FF), Color(0xFF0072FF))),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            Text(
                text = "D",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFF007A), Color(0xFFC000FF))),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(modifier = Modifier.width(3.dp))

            Box(contentAlignment = Alignment.TopCenter) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .offset(x = 1.dp, y = (-4).dp)
                        .align(Alignment.TopCenter)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(5.dp)
                                .rotate(20f)
                                .background(Color(0xFF00D2FF), RoundedCornerShape(1.dp))
                        )
                    }
                }

                Text(
                    text = "Flix",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFD6E4FF), Color(0xFF9CB8FF))
                        ),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        Canvas(
            modifier = Modifier
                .width(175.dp)
                .height(14.dp)
                .offset(y = (-4).dp)
        ) {
            val w = size.width
            val h = size.height

            val swooshBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF0072FF),
                    Color(0xFFFF007A),
                    Color(0xFFFF007A).copy(alpha = 0f)
                )
            )

            val path = Path().apply {
                moveTo(0f, h * 0.45f)
                quadraticBezierTo(w * 0.45f, h * 1.15f, w * 0.96f, 0f)
                quadraticBezierTo(w * 0.45f, h * 0.85f, 0f, h * 0.45f)
                close()
            }
            drawPath(path = path, brush = swooshBrush)
        }
    }
}

/**
 * ⏩ ডাবল ট্যাপ স্কিপ ব্যাজ (+১০ / -১০)
 */
@Composable
fun OttDoubleTapSkipBadge(
    isForward: Boolean,
    skipSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (!isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.85f, h * 0.15f)
                    lineTo(w * 0.20f, h * 0.50f)
                    lineTo(w * 0.85f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Text(
            text = if (isForward) "+$skipSeconds" else "-$skipSeconds",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    blurRadius = 8f
                )
            )
        )
        if (isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.15f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.15f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
