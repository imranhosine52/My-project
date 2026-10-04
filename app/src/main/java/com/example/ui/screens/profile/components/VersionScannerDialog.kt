package com.example.ui.screens.profile.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay

private val PlayProtectGreen = Color(0xFF00E676)
private val PlayProtectCyan = Color(0xFF00E5FF)

/**
 * 🛰️ গুগল প্লে-স্টোর (Play Protect) স্টাইল ফ্যান স্ক্যানার
 */
@Composable
fun VersionScannerDialog(
    viewModel: DramaFlixViewModel,
    installedVersion: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isScanning by remember { mutableStateOf(true) }
    var scanStatusText by remember { mutableStateOf("Scanning for updates...") }
    var isUpToDate by remember { mutableStateOf(false) }

    // 🌪️ ফ্যানের মতো ৩৬০ ডিগ্রি ঘূর্ণন অ্যানিমেশন
    val infiniteTransition = rememberInfiniteTransition(label = "fan_sweep")
    val fanRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fan_rotation"
    )

    // হালকা স্পন্দন (Pulse)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        delay(600)
        scanStatusText = "Verifying package integrity..."
        delay(800)
        scanStatusText = "Checking latest version..."

        val updateInfo = viewModel.scanServerForUpdate()
        delay(600)

        if (updateInfo?.updateAvailable == true) {
            onDismiss()
            viewModel.checkAppVersion(forceShow = true)
        } else {
            isScanning = false
            isUpToDate = true
            scanStatusText = "App is up to date"
            delay(1800)
            onDismiss() // স্ক্যান শেষ হলে স্বয়ংক্রিয়ভাবে বন্ধ হবে
        }
    }

    Dialog(
        onDismissRequest = { if (!isScanning) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isScanning,
            dismissOnClickOutside = !isScanning,
            usePlatformDefaultWidth = false
        )
    ) {
        // 🎯 কোনো চারকোনা কার্ড নেই — স্ক্রিনের ওপর স্বচ্ছ ফ্লোটিং সারফেস
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(enabled = !isScanning) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.padding(24.dp)
            ) {
                // =============================================================
                // 🌀 গুগল প্লে-স্টোরের মতো ফ্যান রাডার স্ক্যানার
                // =============================================================
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        // ১. ফ্যানের মতো ঘূর্ণায়মান রাডার ব্লেড
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(fanRotationAngle)
                        ) {
                            val r = size.minDimension / 2f
                            val sweepBrush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    PlayProtectGreen.copy(alpha = 0.05f),
                                    PlayProtectGreen.copy(alpha = 0.25f),
                                    PlayProtectGreen.copy(alpha = 0.90f)
                                )
                            )

                            // বাইরের পাতলা সার্কেল ট্র্যাক
                            drawCircle(
                                color = PlayProtectGreen.copy(alpha = 0.20f),
                                radius = r,
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // ফ্যানের ব্লেডের মতো রাডার সুইপ
                            drawArc(
                                brush = sweepBrush,
                                startAngle = 0f,
                                sweepAngle = 90f, // ফ্যানের পাখার মতো ৯০ ডিগ্রি স্লাইস
                                useCenter = true
                            )

                            // ঘূর্ণায়মান উজ্জ্বল অগ্রভাগ
                            drawCircle(
                                brush = sweepBrush,
                                radius = r,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // ২. মাঝখানের সিকিউরিটি শিল্ড আইকন
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF101722)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = PlayProtectGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        // ✅ স্ক্যান সম্পন্ন হওয়ার অ্যানিমেশন
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(PlayProtectGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = PlayProtectGreen,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                // =============================================================
                // 📝 টেক্সট ও স্ট্যাটাস
                // =============================================================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isScanning) "Scanning..." else "No updates found",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isScanning) scanStatusText else "PlayDramaFlix v$installedVersion is up to date",
                        color = if (isScanning) Color(0xFF94A3B8) else PlayProtectGreen,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // ক্লোজ বাটন (স্ক্যান চলাকালীন বন্ধ করতে চাইলে)
                if (isScanning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
