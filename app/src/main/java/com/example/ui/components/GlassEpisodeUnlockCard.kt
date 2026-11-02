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
private val FrostedGlassBg = Color(0xD9101420) // হালকা ডার্ক ট্রান্সলুসেন্ট গ্লাস
private val GlassBorderColor = Color(0x33FFFFFF)
private val FrostedButtonBg = Color(0xFF2C3242)
private val PastelGoldButtonBg = Brush.horizontalGradient(
    listOf(Color(0xFFFBE4C0), Color(0xFFF5CD8E))
)
private val DarkGoldText = Color(0xFF332003)

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
    // 🎯 পুরো স্ক্রিন অতিরিক্ত ডার্ক না করে হালকা (alpha = 0.25f) ড্রপ রেখে প্লেয়ারকে স্পষ্ট রাখা হলো
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showCloseButton && !isLoading) onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // 🌟 আসল ফ্রস্টেড গ্লাস কার্ড
        Surface(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, GlassBorderColor), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}, // ক্লিক ইভেন্ট কনজিউম যাতে ব্যাকগ্রাউন্ডে ক্লিক না যায়
            shape = RoundedCornerShape(20.dp),
            color = FrostedGlassBg,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // টপ বার: স্টেপ ব্যাজ ও ক্লোজ বাটন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // স্টেপ ইন্ডিকেটর ব্যাজ
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (totalSteps > 1) "STEP $currentStep OF $totalSteps" else "EPISODE $episodeNumber",
                            color = Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (showCloseButton && !isLoading) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // ১. সেন্ট্রাল সাদা লক আইকন (সাইজ ৩৫.dp তে কম্প্যাক্ট করা হয়েছে)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Episode Locked",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // ২. [ Watch Ad to Unlock · Xs ] ছোট ও স্লিম বাটন
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
                        .height(38.dp) // 👈 ছোট উচ্চতা
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
                                modifier = Modifier.size(16.dp)
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
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = adButtonLabel,
                                    color = Color.White,
                                    fontSize = 12.5.sp, // 👈 স্লিম টেক্সট
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // ৩. [ 👑 Premium Pro Unlock ] ছোট ও স্লিম বাটন
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp) // 👈 ছোট উচ্চতা
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
                                modifier = Modifier.size(14.dp, 12.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Premium Pro Unlock",
                                color = DarkGoldText,
                                fontSize = 12.5.sp, // 👈 স্লিম টেক্সট
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
