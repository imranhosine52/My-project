package com.example.ui.screens.reels.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎯 ১ নম্বর ছবিতে দৃশ্যমান ৮টি ইনস্টাগ্রাম কুইক ইমোজি
private val DefaultQuickEmojis = listOf(
    "❤️", "🙌", "🔥", "👏", "😢", "😍", "😮", "😂"
)

@Composable
fun QuickEmojiRow(
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = DefaultQuickEmojis
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0F15))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onEmojiClick(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 22.sp
                )
            }
        }
    }
}
