package com.example.ui.screens.localplayer.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DrawerBg = Color(0xFF10141F)
private val BorderSubtle = Color(0xFF222B3D)

/**
 * 🚪 গ্যালারি সাইডবার ড্রয়ার কম্পোনেন্ট (Music, Starred, Safe Folder, Trash)
 */
@Composable
fun GallerySidebarDrawerContent(
    onOpenMusic: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenSafeFolder: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DrawerBg,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .width(285.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ১. ব্র্যান্ডিং হেডার
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF007AFF), Color(0xFF00E5FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Media Gallery",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fast & Offline Media",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.5.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "QUICK TOOLS",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            // ২. ড্রয়ারের মেনু আইটেমসমূহ
            DrawerMenuCardItem(
                label = "Music & Audios",
                icon = Icons.Default.Headphones,
                iconColor = Color(0xFFFF8A00),
                onClick = onOpenMusic
            )

            DrawerMenuCardItem(
                label = "Starred Items",
                icon = Icons.Default.Star,
                iconColor = Color(0xFFFFB300),
                onClick = onOpenStarred
            )

            DrawerMenuCardItem(
                label = "Safe Folder",
                icon = Icons.Default.Lock,
                iconColor = Color(0xFF00C6FF),
                onClick = onOpenSafeFolder
            )

            DrawerMenuCardItem(
                label = "Recycle Bin",
                icon = Icons.Default.Delete,
                iconColor = Color(0xFFFF416C),
                onClick = onOpenTrash
            )

            Spacer(modifier = Modifier.weight(1f))

            // ৩. ফুটার টেক্সট (১০০% নিরাপদ টেক্সট অ্যালাইনমেন্ট)
            Text(
                text = "PlayDramaFlix Gallery v2.0",
                color = Color(0xFF4A5568),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun DrawerMenuCardItem(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161B28),
        border = BorderStroke(0.8.dp, Color(0xFF232D40)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
