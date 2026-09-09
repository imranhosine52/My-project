@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DownloadResourceSheet(
    title: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
    onDownloadNow: () -> Unit,      // 🎯 স্বয়ংক্রিয়ভাবে ডাউনলোড শুরু করার কলব্যাক
    onOpenDownloadsPage: () -> Unit // 🎯 ডাউনলোড লিস্ট দেখার লম্বা বাটনের কলব্যাক
) {
    // ০ থেকে ১০০% পর্যন্ত মসৃণ লাইন অ্যানিমেশন ট্র্যাকার
    val animatedProgress = remember { Animatable(0f) }
    var isDownloadStarted by remember { mutableStateOf(false) }

    // ⚡ ১. লাইন অ্যানিমেশন চলবে ➔ ডাউনলোড স্টার্ট হবে ➔ ১ সেকেন্ড পর অটো ক্লোজ হবে
    LaunchedEffect(Unit) {
        // ১.২ সেকেন্ডে অ্যানিমেটেড লাইনটি বাম থেকে ডানে পূর্ণ হবে
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )

        // লাইন ফিল হওয়ামাত্রই ডাউনলোড শুরু
        isDownloadStarted = true
        onDownloadNow()

        // ডাউনলোড শুরু হওয়ার ১ সেকেন্ড পর শিট অটো মিনিমাইজ
        delay(1000L)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141E), // ডার্ক প্রিমিয়াম ব্যাকগ্রাউন্ড
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp)
        ) {
            // 🔝 হেডার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDownloadStarted) "Downloading Video..." else "Ready to Download",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF003040),
                    border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "1080p / 720p HD",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // =========================================================================
            // 🌟 অ্যানিমেটেড লাইন (বাম থেকে ডানে ০% থেকে ১০০% এ যাবে)
            // =========================================================================
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
                                    Color(0xFF007AFF), // Blue
                                    Color(0xFF00E5FF), // Cyan
                                    Color(0xFF00D166)  // Green
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🎯 সেন্ট্রাল আইকন (ডাউনলোড শুরু হলে সবুজ চেকমার্ক হবে)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDownloadStarted) Color(0xFF00D166).copy(alpha = 0.15f)
                        else Color(0xFF007AFF).copy(alpha = 0.15f)
                    )
                    .border(
                        width = 1.2.dp,
                        color = if (isDownloadStarted) Color(0xFF00D166) else Color(0xFF007AFF),
                        shape = CircleShape
                    )
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDownloadStarted) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isDownloadStarted) Color(0xFF00D166) else Color(0xFF00E5FF),
                    modifier = Modifier.size(28.dp)
                )
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

            // স্ট্যাটাস টেক্সট
            Text(
                text = if (isDownloadStarted) "✓ Download started in background!" else "Connecting to High-Speed R2 Server...",
                color = if (isDownloadStarted) Color(0xFF00D166) else Color(0xFF8E95A5),
                fontSize = 11.5.sp,
                fontWeight = if (isDownloadStarted) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // =========================================================================
            // 📂 লম্বা একটি প্রিমিয়াম বাটন (সোজা ডাউনলোড পেজে যাওয়ার জন্য)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = {
                        // ইউজার বাটনে চাপলে ডাউনলোড যদি শুরু না হয়ে থাকে তবে শুরু করিয়ে সাথে সাথে ডাউনলোড পেজে নেবে
                        if (!isDownloadStarted) {
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
