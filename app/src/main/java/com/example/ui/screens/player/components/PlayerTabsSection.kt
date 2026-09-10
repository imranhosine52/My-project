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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.ui.screens.ModernCommentRowItem

@Composable
fun PlayerTabsHeader(
    selectedTabIndex: Int,
    commentsCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = Color(0xFF0C0F15), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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
        Text(cardTitle, color = Color(0xFFCCD0DB), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlayerInlineCommentInput(
    userInitials: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF161F30)),
            contentAlignment = Alignment.Center
        ) {
            Text(userInitials, color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF131926)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) Text("Add a comment...", color = Color(0xFF64748B), fontSize = 13.5.sp)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                cursorBrush = SolidColor(Color(0xFFFFC107)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFC107))
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(19.dp))
        }
    }
}
