package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎨 অ্যাপের থিমের সাথে মেলানো কালার প্যালেট
private val BackgroundDark = Color(0xFF090A0F)
private val CardSurface = Color(0xFF10141F)
private val BorderDark = Color(0xFF1E2638)
private val CyanGlow = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

// 🌟 নীল ও সবুজ গ্রেডিয়েন্ট বাটন ব্রাশ
private val BlueGreenGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF), // Electric Blue
        Color(0xFF00D166)  // Emerald Green
    )
)

@Composable
fun NotFoundScreen(
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit = {},
    onBackClick: () -> Unit = onNavigateHome,
    customMessage: String? = null,
    modifier: Modifier = Modifier
) {
    // ব্যাক প্রেস করলে হোমে নিয়ে যাবে
    BackHandler { onBackClick() }

    // 🌊 ভাসমান অ্যানিমেশন (Floating Icon Animation)
    val infiniteTransition = rememberInfiniteTransition(label = "float_404")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_y"
    )

    // 🌟 হালকা পালস গ্লো ইফেক্ট
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ১. টপ ব্যাক বাটন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141926))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ২. মূল কন্টেন্ট (সেন্টারে)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 🌀 ভাসমান ব্রোকেন লিংক আইকন
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatOffsetY
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFF2A4B).copy(alpha = 0.25f),
                                Color(0xFF1A1F2C)
                            )
                        )
                    )
                    .border(1.5.dp, Color(0xFFFF2A4B).copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = "Link Not Found",
                    tint = Color(0xFFFF4B72),
                    modifier = Modifier.size(54.dp)
                )
            }

            // 🏷️ বড় 404 টেক্সট ব্যাজ
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = "404 • NOT FOUND",
                    color = CyanGlow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // হেডিং ও ডেসক্রিপশন
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "পেজ বা কনটেন্ট খুঁজে পাওয়া যায়নি!",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = customMessage
                        ?: "আপনি যে লিংকে প্রবেশের চেষ্টা করছেন তা হয়তো সরিয়ে ফেলা হয়েছে, মেয়াদ শেষ হয়েছে বা লিংকটি ভুল।",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =========================================================================
            // 🔘 অ্যাকশন বাটনসমূহ: [ 🏠 Go to Home ] এবং [ 🔍 Search Dramas ]
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(0.92f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ১ম বাটন: হোমে ফিরে যাওয়ার প্রিমিয়াম গ্রেডিয়েন্ট বাটন
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(BlueGreenGradient)
                        .clickable { onNavigateHome() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "হোম পেজে ফিরে যান",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ২য় বাটন: সার্চ করার আউটলাইন বাটন
                Surface(
                    shape = RoundedCornerShape(23.dp),
                    color = Color(0xFF141926),
                    border = BorderStroke(1.dp, Color(0xFF242E42)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clickable { onNavigateSearch() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "অন্যান্য ড্রামা সার্চ করুন",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
