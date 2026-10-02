package com.example.ui.screens.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TextMuted = Color(0xFF8692A6)
private val NavBgColor = Color(0xFF000000)

/**
 * 📱 রিলস পেজের ৫-আইটেম স্লিক বটম ন্যাভিগেশন বার:
 * [Home] - [Reels] - [+] (Upload) - [Downloads] - [Me]
 */
@Composable
fun ReelsBottomNavigationBar(
    onHomeClick: () -> Unit,
    onReelsClick: () -> Unit,
    onUploadClick: () -> Unit,
    onDownloadsClick: () -> Unit, // 👈 ইনবক্সের জায়গায় ডাউনলোড পেজ ন্যাভিগেশন
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NavBgColor)
            .navigationBarsPadding()
    ) {
        // সেপারেটর লাইন
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF1E2432))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // ১. Home
            ReelsNavItem(
                icon = Icons.Outlined.Home,
                label = "Home",
                isSelected = false,
                onClick = onHomeClick
            )

            // ২. Reels (Active)
            ReelsNavItem(
                icon = Icons.Default.Movie,
                label = "Reels",
                isSelected = true,
                onClick = onReelsClick
            )

            // ৩. [+] Upload বাটন
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onUploadClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Upload Reel",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ৪. 🎯 Downloads (ইনবক্সের পরিবর্তে)
            ReelsNavItem(
                icon = Icons.Outlined.FileDownload,
                label = "Downloads",
                isSelected = false,
                onClick = onDownloadsClick
            )

            // ৫. Me (Profile)
            ReelsNavItem(
                icon = Icons.Outlined.Person,
                label = "Me",
                isSelected = false,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
private fun ReelsNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else TextMuted,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
