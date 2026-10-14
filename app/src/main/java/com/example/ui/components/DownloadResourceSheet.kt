@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun VipCrownVectorIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF6D38B)
) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.30f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.50f, 0f)
            lineTo(w * 0.72f, h * 0.65f)
            lineTo(w, h * 0.30f)
            lineTo(w * 0.85f, h * 0.95f)
            lineTo(w * 0.15f, h * 0.95f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun DownloadResourceSheet(
    title: String,
    downloadUrl: String,
    isVip: Boolean = false,
    isLongVideo: Boolean = true,
    onDismiss: () -> Unit,
    onDownloadNow: () -> Unit,
    onOpenDownloadsPage: () -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val context = LocalContext.current
    val animatedProgress = remember { Animatable(0f) }
    var isDownloadStarted by remember { mutableStateOf(false) }

    // 🎯 লং ভিডিও এবং ফ্রি ইউজার হলে ডাউনলোড ব্লক থাকবে
    val isDownloadBlocked = isLongVideo && !isVip

    LaunchedEffect(isDownloadBlocked) {
        if (!isDownloadBlocked) {
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )

            isDownloadStarted = true
            onDownloadNow()

            delay(1000L)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141E),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp)
        ) {
            // 🔝 ১. হেডার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isDownloadBlocked -> "VIP Exclusive Download"
                        isDownloadStarted -> "Downloading Video..."
                        else -> "Ready to Download"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDownloadBlocked) Color(0xFF2C2208) else Color(0xFF003040),
                    border = BorderStroke(
                        0.8.dp,
                        if (isDownloadBlocked) Color(0xFFFFB300).copy(alpha = 0.8f) else Color(0xFF00E5FF).copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isDownloadBlocked) {
                            VipCrownVectorIcon(tint = Color(0xFFFFB300))
                        }
                        Text(
                            text = if (isDownloadBlocked) "VIP Only" else "1080p / 720p HD",
                            color = if (isDownloadBlocked) Color(0xFFFFB300) else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // =========================================================================
            // 🌟 অ্যানিমেটেড লাইন (শুধুমাত্র অনুমোদিত ডাউনলোডের ক্ষেত্রে চলবে)
            // =========================================================================
            if (!isDownloadBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .background(Color(0xFF1E2433))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animatedProgress.value)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF007AFF),
                                        Color(0xFF00E5FF),
                                        Color(0xFF00D166)
                                    )
                                )
                            )
                    )
                }
            } else {
                HorizontalDivider(color = Color(0xFF232B3D), thickness = 1.dp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🎯 সেন্ট্রাল আইকন
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDownloadBlocked -> Color(0xFF2C2208)
                            isDownloadStarted -> Color(0xFF00D166).copy(alpha = 0.15f)
                            else -> Color(0xFF007AFF).copy(alpha = 0.15f)
                        }
                    )
                    .border(
                        width = 1.2.dp,
                        color = when {
                            isDownloadBlocked -> Color(0xFFFFB300)
                            isDownloadStarted -> Color(0xFF00D166)
                            else -> Color(0xFF007AFF)
                        },
                        shape = CircleShape
                    )
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloadBlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "VIP Locked",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloadStarted) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isDownloadStarted) Color(0xFF00D166) else Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ভিডিও টাইটেল
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // স্ট্যাটাস ও তথ্য টেক্সট
            Text(
                text = when {
                    isDownloadBlocked -> "👑 লং ভিডিও ডাউনলোড শুধুমাত্র VIP মেম্বারদের জন্য প্রযোজ্য! ফুল ড্রামা ও হাই-স্পিড ডাউনলোডের জন্য VIP নিন।"
                    isDownloadStarted -> "✓ Download started in background!"
                    else -> "Connecting to High-Speed R2 Server..."
                },
                color = when {
                    isDownloadBlocked -> Color(0xFFFFD54F)
                    isDownloadStarted -> Color(0xFF00D166)
                    else -> Color(0xFF8E95A5)
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (isDownloadStarted || isDownloadBlocked) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // 🔘 অ্যাকশন বাটন সেকশন
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ১. VIP লক থাকলে প্রিমিয়াম আপগ্রেড বাটন
                if (isDownloadBlocked) {
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigateToVip()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFE5B567), Color(0xFFF6D38B))))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VipCrownVectorIcon(tint = Color(0xFF2B210E))
                            Text(
                                text = "Upgrade to VIP to Download 👑",
                                color = Color(0xFF2B210E),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // ২. ডাউনলোড লাইব্রেরি পেজ যাওয়ার বাটন
                Button(
                    onClick = {
                        if (!isDownloadBlocked && !isDownloadStarted) {
                            onDownloadNow()
                        }
                        onDismiss()
                        onOpenDownloadsPage()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161C28)),
                    border = BorderStroke(1.dp, Color(0xFF28344A)),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF007AFF).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "View Downloads & Offline Library",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
