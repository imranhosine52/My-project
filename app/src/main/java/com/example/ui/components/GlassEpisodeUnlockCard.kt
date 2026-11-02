package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎨 প্রিমিয়াম ফ্রস্টেড গ্লাস ও কালার প্যালেট
private val FrostedGlassBg = Color(0xDD111522) // হালকা কালো ফ্রস্টেড গ্লাস ইফেক্ট
private val GlassBorderColor = Color(0x38FFFFFF) // কাঁচের স্লিম লাইট বর্ডার
private val FrostedButtonBg = Color(0xFF282E3E)
private val PastelGoldButtonBg = Brush.horizontalGradient(
    listOf(Color(0xFFFBE4C0), Color(0xFFF5CD8E))
)
private val DarkGoldText = Color(0xFF332003)

/**
 * 👑 ক্যাপসুল বাটনের জন্য মিনি প্রিমিয়াম ক্রাউন ভেক্টর আইকন
 */
@Composable
private fun SmallCrownIcon(
    modifier: Modifier = Modifier,
    tint: Color = DarkGoldText
) {
    Canvas(modifier = modifier.size(13.dp, 11.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.28f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.50f, 0f)
            lineTo(w * 0.72f, h * 0.65f)
            lineTo(w, h * 0.28f)
            lineTo(w * 0.86f, h * 0.95f)
            lineTo(w * 0.14f, h * 0.95f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

/**
 * 🔒 আধুনিক স্লিম ফ্রস্টেড গ্লাস আনলক কার্ড
 */
@Composable
fun GlassEpisodeUnlockCard(
    episodeNumber: Int = 1,
    currentStep: Int = 1,
    totalSteps: Int = 1,
    timerSeconds: Int = 10,
    isLoading: Boolean = false,
    onWatchAdClick: () -> Unit,
    onUpgradeVipClick: () -> Unit,
    onDismiss: () -> Unit = {},
    showCloseButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 🎯 পুরো স্ক্রিন ব্লার বা কালো না করে একদম হালকা ড্রপ রাখা হলো যাতে পেছনের স্ক্রিন স্পষ্ট থাকে
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showCloseButton && !isLoading) onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // 🌟 আসল ফ্রস্টেড গ্লাস কার্ড (কমপ্যাক্ট ৩১০.dp উইডথ)
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.80f)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, GlassBorderColor), RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(22.dp),
            color = FrostedGlassBg,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ১. টপ বার: স্টেপ ব্যাজ ও ক্লোজ বাটন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // সার্ভারের ক্লিক সংখ্যা অনুযায়ী স্টেপ ব্যাজ
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (totalSteps > 1) "STEP $currentStep OF $totalSteps" else "EPISODE $episodeNumber",
                            color = Color(0xFF00E5FF),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (showCloseButton && !isLoading) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x2EFFFFFF))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // ২. সেন্ট্রাল লক আইকন (কমপ্যাক্ট ৩৬.dp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Episode Locked",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ৩. [ Watch Ad to Unlock · Xs ] ছোট ও স্লিম বোতাম
                val adButtonLabel = when {
                    isLoading -> "Loading Ad..."
                    totalSteps > 1 -> "Watch Ad (Step $currentStep/$totalSteps) · ${timerSeconds}s"
                    timerSeconds > 0 -> "Watch Ad to Unlock · ${timerSeconds}s"
                    else -> "Watch Ad to Unlock"
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FrostedButtonBg,
                    border = BorderStroke(0.8.dp, Color(0x44FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp) // 👈 ছোট উচ্চতা
                        .clickable(enabled = !isLoading) { onWatchAdClick() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.8.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = adButtonLabel,
                                    color = Color.White,
                                    fontSize = 12.sp, // 👈 কমপ্যাক্ট টেক্সট সাইজ
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // ৪. [ 👑 Premium Pro Unlock ] ছোট ও স্লিম গোল্ডেন বোতাম
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp) // 👈 ছোট উচ্চতা
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !isLoading) { onUpgradeVipClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PastelGoldButtonBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SmallCrownIcon(
                                tint = DarkGoldText,
                                modifier = Modifier.size(13.dp, 11.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Premium Pro Unlock",
                                color = DarkGoldText,
                                fontSize = 12.sp, // 👈 কমপ্যাক্ট টেক্সট সাইজ
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
