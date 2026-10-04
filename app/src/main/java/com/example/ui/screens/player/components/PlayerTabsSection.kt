package com.example.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.LanguageDubBadge
import java.util.Locale

private val GoldRating = Color(0xFFFFB300)

// =============================================================================
// 📑 ট্যাব হেডার (For you এবং Comments সিলেকশন)
// =============================================================================
@Composable
fun PlayerTabsHeader(
    selectedTabIndex: Int,
    commentsCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0C0F15),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // For you Tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(0) }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "For you",
                    color = if (selectedTabIndex == 0) Color.White else Color(0xFF8E95A5),
                    fontSize = 14.sp,
                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selectedTabIndex == 0) Color(0xFF00E5FF) else Color.Transparent)
                )
            }

            // Comments Tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(1) }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "Comments ($commentsCount)",
                    color = if (selectedTabIndex == 1) Color.White else Color(0xFF8E95A5),
                    fontSize = 14.sp,
                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selectedTabIndex == 1) Color(0xFF00E5FF) else Color.Transparent)
                )
            }
        }
    }
}

// =============================================================================
// 🎬 রিকমেন্ডেশন কার্ড (🎯 উপরে ডাব ব্যাজ, নিচে রেটিং ও Display Name সহ)
// =========================================================================
@Composable
fun PlayerRecommendationCard(
    drama: ContentItemDto,
    cardTitle: String,
    shiningBorderBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, shiningBorderBrush, RoundedCornerShape(8.dp))
                .background(Color(0xFF141A26))
        ) {
            AsyncImage(
                model = drama.posterUrl ?: drama.bannerUrl,
                contentDescription = drama.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // নিচের হালকা ডার্ক ওভারলে
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // 🎯 ১. উপরে ডানে সার্ভারের ডাইনামিক ডাবিং ব্যাজ
            LanguageDubBadge(
                dubText = drama.dubBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // ২. নিচে বামে এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Ep" else "HD"
            Text(
                text = epCount,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            )

            // 🎯 ৩. নিচে ডানে হালকা রেটিং (কোনো ভারী বক্স ছাড়া)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldRating,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = if (drama.rating > 0) String.format(Locale.US, "%.1f", drama.rating) else "8.5",
                    color = GoldRating,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(3.5.dp))

        // 🎯 ছোট ও পরিচ্ছন্ন নাম (Display Name)
        Text(
            text = drama.displayName,
            color = Color(0xFFCCD0DB),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// ✍️ কমেন্ট ইনপুট বক্স
// =============================================================================
@Composable
fun PlayerInlineCommentInput(
    userInitials: String,
    currentUserAvatar: String? = null,
    isLoggedIn: Boolean = true,
    text: String,
    onTextChange: (String) -> Unit,
    onOpenMediaPicker: () -> Unit = {},
    onRequireLogin: () -> Unit = {},
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF161F30)),
            contentAlignment = Alignment.Center
        ) {
            if (!currentUserAvatar.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUserAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = "My Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = userInitials,
                    color = Color(0xFFFFC107),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(Color(0xFF131926))
                .border(0.8.dp, Color(0xFF232B3E), RoundedCornerShape(21.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                contentDescription = "Emojis & Stickers",
                tint = Color(0xFFFFC107),
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        if (!isLoggedIn) onRequireLogin() else onOpenMediaPicker()
                    }
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (isLoggedIn) "Add a comment..." else "Log in to comment...",
                        color = if (isLoggedIn) Color(0xFF64748B) else Color(0xFFFFC107),
                        fontSize = 13.sp
                    )
                }
                if (isLoggedIn) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFFFFC107)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (text.isNotBlank()) onSend()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (!isLoggedIn) onRequireLogin()
                else if (text.isNotBlank()) onSend()
            },
            enabled = text.isNotBlank(),
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank()) Color(0xFFFFC107) else Color(0xFF222B38))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (text.isNotBlank()) Color.Black else Color(0xFF64748B),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
