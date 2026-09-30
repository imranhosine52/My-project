@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 🎯 প্লাস (+) বাটনে চাপ দিলে ২টা অপশনের পপ-আপ চয়েসার
 */
@Composable
fun ReelUploadChooserBottomSheet(
    onChooseRegularReel: () -> Unit,
    onChooseSeriesEpisode: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    text = "Select Upload Type",
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            // অপশন ১: সাধারণ রিলস আপলোড
            UploadOptionCard(
                title = "Upload Single Reel",
                subtitle = "Share quick standalone short video (No Playlist)",
                icon = Icons.Default.Movie,
                accentColor = ActionGreen,
                onClick = {
                    onDismiss()
                    onChooseRegularReel()
                }
            )

            // অপশন ২: ড্রামা সিরিজ / প্লেলিস্ট পর্ব আপলোড
            UploadOptionCard(
                title = "Upload Series Episode",
                subtitle = "Add episodic reel to a Playlist / Mini-Drama (Required Playlist)",
                icon = Icons.Default.VideoLibrary,
                accentColor = CyanAccent,
                onClick = {
                    onDismiss()
                    onChooseSeriesEpisode()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun UploadOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF19202E),
        border = BorderStroke(1.dp, BorderStrokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
