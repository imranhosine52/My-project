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
import androidx.compose.material.icons.filled.Mic
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

@Composable
fun PlayerTabsHeader(
    selectedTabIndex: Int,
    commentsCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = Color(0xFF0C0F15), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "For you",
                color = if (selectedTabIndex == 0) Color.White else Color(0xFF8E95A5),
                fontSize = 13.5.sp,
                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clickable { onTabSelected(0) }
            )
            Text(
                text = "Comments ($commentsCount)",
                color = if (selectedTabIndex == 1) Color.White else Color(0xFF8E95A5),
                fontSize = 13.5.sp,
                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clickable { onTabSelected(1) }
            )
        }
    }
}

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
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, shiningBorderBrush, RoundedCornerShape(8.dp))
                .background(Color(0xFF141A26))
        ) {
            AsyncImage(
                model = drama.posterUrl ?: drama.bannerUrl,
                contentDescription = cardTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("${drama.totalEpisodes} Episodes", color = Color(0xFFE2E8F0), fontSize = 9.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            cardTitle,
            color = Color(0xFFCCD0DB),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 🎯 কমেন্ট ইনপুট বার (ডাবল মাইক ফিক্সড: ভেতরের মাইক রিমুভ করা হয়েছে)
@Composable
fun PlayerInlineCommentInput(
    userInitials: String,
    currentUserAvatar: String? = null,
    isLoggedIn: Boolean = true,
    text: String,
    isRecordingVoice: Boolean = false,
    recordDurationSeconds: Long = 0L,
    onTextChange: (String) -> Unit,
    onOpenMediaPicker: () -> Unit = {},
    onStartVoiceRecord: () -> Unit = {},
    onCancelVoiceRecord: () -> Unit = {},
    onSendVoiceRecord: () -> Unit = {},
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
        // 🖼️ অবতার সার্কেল
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

        // ✍️ টাইপিং / ভয়েস রেকর্ডিং বক্স
        if (isRecordingVoice) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFF1E2834))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF2A4B)))
                    Text("Recording: ${recordDurationSeconds}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onCancelVoiceRecord() }
                    )
                    Text(
                        text = "Send",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSendVoiceRecord() }
                    )
                }
            }
        } else {
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
                // 🧸 ইমোজি ও স্টিকার বাটন
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

                // টেক্সট ফিল্ড (ভেতরের ছোট মাইকটি সম্পূর্ণ সরানো হয়েছে)
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
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 🎯 ডানের অ্যাকশন বাটন (টেক্সট থাকলে সেন্ড করবে, ফাঁকা থাকলে ভয়েস রেকর্ড চালু করবে)
            IconButton(
                onClick = {
                    if (!isLoggedIn) onRequireLogin()
                    else if (text.isNotBlank()) onSend()
                    else onStartVoiceRecord()
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107))
            ) {
                Icon(
                    imageVector = if (text.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                    contentDescription = if (text.isNotBlank()) "Send" else "Record Voice",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
