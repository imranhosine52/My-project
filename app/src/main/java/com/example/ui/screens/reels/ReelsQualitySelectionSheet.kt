@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Tune
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
import com.example.data.model.UserReelDto

private val CyanAccent = Color(0xFF00E5FF)
private val ActionGreen = Color(0xFF00E676)
private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 🎛️ ডায়নামিক ভিডিও কোয়ালিটি সিলেক্টর বটম শীট
 * - ভিডিওতে একাধিক কোয়ালিটি না থাকলে ফেইক অপশন দেখাবে না।
 * - একাধিক ট্রান্সকোডেড কোয়ালিটি থাকলে তবেই ৭২০p, ৪৮০p, ৩৬০p সুইচ করার সুযোগ দেবে।
 */
@Composable
fun ReelsQualitySelectionSheet(
    currentReel: UserReelDto? = null, // 🎯 বর্তমান চলমান ভিডিও অবজেক্ট
    selectedQuality: ReelVideoQuality,
    onSelectQuality: (ReelVideoQuality) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ভিডিওটিতে একাধিক কোয়ালিটি আছে কিনা তা যাচাই
    val availableQualitiesMap = currentReel?.qualities
    val hasMultipleQualities = !availableQualitiesMap.isNullOrEmpty() && availableQualitiesMap.size > 1

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
            // ড্র্যাগ হ্যান্ডেল বার
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Video Quality / রেজোলিউশন",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            // =========================================================================
            // 🎯 ১. যদি ভিডিওতে একাধিক কোয়ালিটি না থাকে (সিঙ্গেল কোয়ালিটি ভিডিও)
            // =========================================================================
            if (!hasMultipleQualities) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF132A38),
                    border = BorderStroke(1.2.dp, CyanAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Original / Standard Quality",
                                    color = CyanAccent,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ActionGreen.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, ActionGreen)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = ActionGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "This creator uploaded this video in single source quality. Auto-adjusting for best performance.",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(CyanAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            // =========================================================================
            // 🎯 ২. যদি ভিডিওতে একাধিক কোয়ালিটি বিদ্যমান থাকে (৭২০p, ৪৮০p, ৩৬০p)
            // =========================================================================
            else {
                Text(
                    text = "Multiple resolutions available for this video. Select preferred quality:",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                // কোয়ালিটি অপশনসমূহ
                ReelVideoQuality.values().forEach { quality ->
                    val isSupportedByVideo = availableQualitiesMap.containsKey(quality.key)
                    val isSelected = (selectedQuality == quality)

                    if (isSupportedByVideo) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF132A38) else Color(0xFF19202E),
                            border = BorderStroke(
                                width = if (isSelected) 1.2.dp else 0.8.dp,
                                color = if (isSelected) CyanAccent else BorderStrokeColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(quality)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = quality.label,
                                        color = if (isSelected) CyanAccent else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = when (quality) {
                                            ReelVideoQuality.QUALITY_720P -> "Best for Wi-Fi & Fast Mobile Data • Ultra HD"
                                            ReelVideoQuality.QUALITY_480P -> "Standard balance for Mobile Networks"
                                            ReelVideoQuality.QUALITY_360P -> "Data Saver • Reduces data usage up to 70%"
                                        },
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(CyanAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
