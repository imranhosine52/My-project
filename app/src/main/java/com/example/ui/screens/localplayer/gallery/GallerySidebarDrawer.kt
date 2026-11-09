package com.example.ui.screens.localplayer.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LockOutline
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

// 🎨 প্রিমিয়াম ফ্রস্টেড গ্লাস ও লাক্সারি ডার্ক কালার প্যালেট
private val DrawerDarkBg = Color(0xFF090D16)
private val DrawerCardSurface = Color(0xFF111726)
private val BorderSubtleGlow = Color(0xFF1E283E)
private val CyanElectric = Color(0xFF00E5FF)
private val OrangeVibrant = Color(0xFFFF8A00)
private val GoldAccent = Color(0xFFFFB300)
private val DangerNeonRed = Color(0xFFFF416C)
private val TextMutedSlate = Color(0xFF8E95A5)

/**
 * 🚪 আধুনিক ও লাক্সারি গ্যালারি সাইডবার ড্রয়ার
 * (Music & Audios, Starred, Safe Folder, Recycle Bin)
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
        color = DrawerDarkBg,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        border = BorderStroke(1.dp, BorderSubtleGlow),
        shadowElevation = 18.dp,
        modifier = modifier
            .width(295.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1524),
                            Color(0xFF090D16),
                            Color(0xFF06090F)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // =============================================================
            // 🌟 ১. প্রিমিয়াম ব্র্যান্ডিং হেডার
            // =============================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF007AFF), CyanElectric)
                            )
                        )
                        .padding(1.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.5.dp))
                            .background(Color(0xFF080D18)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Media Gallery Logo",
                            tint = CyanElectric,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Media Gallery",
                        color = Color.White,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Text(
                            text = "Offline Media Hub",
                            color = TextMutedSlate,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1B2336),
                thickness = 0.8.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // =============================================================
            // ⚡ ২. টুলস সেকশন হেডার
            // =============================================================
            Text(
                text = "QUICK TOOLS",
                color = CyanElectric,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
            )

            // =============================================================
            // 🎵 ৩.১ Music & Audios মেনু আইটেম (অডিও ফোল্ডারে সরাসরি গমন)
            // =============================================================
            DrawerMenuCardItem(
                label = "Music & Audios",
                subLabel = "Browse audio tracks & songs",
                icon = Icons.Default.Headphones,
                iconTint = OrangeVibrant,
                badgeText = "AUDIO",
                badgeColor = OrangeVibrant,
                onClick = onOpenMusic
            )

            // =============================================================
            // ⭐ ৩.২ Starred Items মেনু আইটেম
            // =============================================================
            DrawerMenuCardItem(
                label = "Starred Items",
                subLabel = "Favorite videos & bookmarks",
                icon = Icons.Default.StarOutline,
                iconTint = GoldAccent,
                badgeText = null,
                badgeColor = GoldAccent,
                onClick = onOpenStarred
            )

            // =============================================================
            // 🔒 ৩.৩ Safe Folder মেনু আইটেম (PIN সুরক্ষিত)
            // =============================================================
            DrawerMenuCardItem(
                label = "Safe Folder",
                subLabel = "PIN protected private vault",
                icon = Icons.Default.LockOutline,
                iconTint = CyanElectric,
                badgeText = "LOCKED",
                badgeColor = CyanElectric,
                onClick = onOpenSafeFolder
            )

            // =============================================================
            // 🗑️ ৩.৪ Recycle Bin মেনু আইটেম (Restore / Delete)
            // =============================================================
            DrawerMenuCardItem(
                label = "Recycle Bin",
                subLabel = "Restore or purge deleted media",
                icon = Icons.Default.DeleteOutline,
                iconTint = DangerNeonRed,
                badgeText = "TRASH",
                badgeColor = DangerNeonRed,
                onClick = onOpenTrash
            )

            Spacer(modifier = Modifier.weight(1f))

            // =============================================================
            // 📱 ৪. ফুটার ব্র্যান্ডিং ইনফো
            // =============================================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0C101A),
                border = BorderStroke(0.6.dp, Color(0xFF1A2234)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PlayDramaFlix Gallery v2.0 • Pro Edition",
                        color = Color(0xFF5E6A82),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 🎛️ একক প্রিমিয়াম মেনু কার্ড আইটেম
 */
@Composable
private fun DrawerMenuCardItem(
    label: String,
    subLabel: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String?,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DrawerCardSurface,
        border = BorderStroke(0.8.dp, BorderSubtleGlow),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // আইকন কন্টেইনার
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(0.6.dp, iconTint.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // টেক্সট বিবরণ
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(1.5.dp))

                Text(
                    text = subLabel,
                    color = TextMutedSlate,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // ডানে ছোট অ্যারো
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF4C5870),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
