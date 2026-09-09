@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadResourceSheet(
    title: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress_anim")
    val progressOffset by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF24272E),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            // শিরোনাম: "Resources Detector"
            Text(
                text = "Resources Detector",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )

            // ৩য় ছবির ব্লু ও নিয়র-গ্রিন অ্যানিমেটেড প্রোগ্রেস লাইন
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF373B44))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressOffset)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2979FF), Color(0xFF00E676))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ৩য় ছবির মতো মাঝের গ্রিন সার্কেল আইকন
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00C853))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // সোর্স ইউআরএল ও স্ট্যাটাস
            Text(
                text = "Analysing from ${downloadUrl.ifBlank { "Cloudflare R2 Storage CDN" }}",
                color = Color(0xFF9AA0A6),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // অ্যাকশন বাটনসমূহ: [ Play Now ] & [ Details ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayNow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D166)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                OutlinedButton(
                    onClick = onOpenDetails,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF4C5260)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        }
    }
}
