@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReelVideoQuality

private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF94A3B8)
private val CyanAccent = Color(0xFF00E5FF)
private val GoldAccent = Color(0xFFFFB300)

/**
 * ⚙️ ৩-ডট অপশনে চাপ দিলে প্রদর্শিত প্লেব্যাক সেটিংস বটম শীট
 */
@Composable
fun ReelsPlaybackSettingsSheet(
    selectedQuality: ReelVideoQuality,
    selectedSpeed: Float,
    onOpenQualityPicker: () -> Unit,
    onOpenSpeedPicker: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ড্র্যাগ হ্যান্ডেল
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

            // হেডার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playback Settings",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted
                    )
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            // ১. ভিডিও কোয়ালিটি অপশন
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF19202E),
                border = BorderStroke(0.8.dp, BorderStrokeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenQualityPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HighQuality,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Video Quality",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = selectedQuality.label,
                                color = CyanAccent,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text(text = "Change >", color = TextMuted, fontSize = 12.sp)
                }
            }

            // ২. প্লেব্যাক স্পিড অপশন
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF19202E),
                border = BorderStroke(0.8.dp, BorderStrokeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSpeedPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Playback Speed",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedSpeed == 1.0f) "1.0x (Normal)" else "${selectedSpeed}x",
                                color = GoldAccent,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text(text = "Change >", color = TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
