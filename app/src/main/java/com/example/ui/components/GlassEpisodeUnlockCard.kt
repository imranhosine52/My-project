package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎨 স্ক্রিনশটের হুবহু কালার প্যালেট
private val FrostedButtonBg = Color(0xFF505056).copy(alpha = 0.88f)
private val PastelGoldButtonBg = Color(0xFFFCE1B6)
private val DarkGoldText = Color(0xFF3E2703)

/**
 * 👑 ক্যাপসুল বাটনের জন্য মিনি প্রিমিয়াম ক্রাউন ভেক্টর আইকন
 */
@Composable
private fun SmallCrownIcon(
    modifier: Modifier = Modifier,
    tint: Color = DarkGoldText
) {
    Canvas(modifier = modifier.size(14.dp, 12.dp)) {
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
 * 🔒 স্ক্রিনশট অনুযায়ী আধুনিক ফ্রস্টেড গ্লাস আনলক কার্ড
 */
@Composable
fun GlassEpisodeUnlockCard(
    timerSeconds: Int = 4,
    isLoading: Boolean = false,
    onWatchAdClick: () -> Unit,
    onUpgradeVipClick: () -> Unit,
    onDismiss: () -> Unit = {},
    showCloseButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    // ব্যাকগ্রাউন্ডের হালকা কালো ব্লার ইফেক্ট
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showCloseButton && !isLoading) onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // ক্লোজ বাটন (যদি প্রয়োজন হয়)
        if (showCloseButton && !isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // সেন্ট্রাল কনটেন্ট কলাম
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.86f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ১. সেন্ট্রাল সাদা লক (Lock) আইকন
            Box(
                modifier = Modifier
                    .size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Episode Locked",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ২. [ Watch Ad to Unlock · Xs ] ক্যাপসুল বাটন
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = FrostedButtonBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = !isLoading) { onWatchAdClick() }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        val adText = if (timerSeconds > 0) {
                            "Watch Ad to Unlock · ${timerSeconds}s"
                        } else {
                            "Watch Ad to Unlock"
                        }

                        Text(
                            text = adText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            // ৩. [ 👑 Premium Pro Unlock ] ক্যাপসুল বাটন
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = PastelGoldButtonBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = !isLoading) { onUpgradeVipClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    SmallCrownIcon(
                        tint = DarkGoldText,
                        modifier = Modifier.size(16.dp, 14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Premium Pro Unlock",
                        color = DarkGoldText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}
