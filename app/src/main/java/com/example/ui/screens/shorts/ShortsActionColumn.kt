package com.example.ui.screens.shorts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun formatCountDisplay(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> "23.3K"
    }
}

@Composable
fun ShortsActionColumn(
    context: Context,
    title: String,
    slug: String,
    likesCount: Long,
    commentsCount: Int,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 12.dp, bottom = 86.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ১. ❤️ লাইক বাটন (সার্ভার ডাটাবেজ সিঙ্ক)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFFF2A4B) else Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = formatCountDisplay(likesCount),
                color = if (isLiked) Color(0xFFFF2A4B) else Color.White,
                fontSize = 11.5.sp
            )
        }

        // ২. 💬 কমেন্ট বাটন (সার্ভার কমেন্টস কাউন্ট সহ)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onCommentClick,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comments",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = commentsCount.toString(),
                color = Color.White,
                fontSize = 11.5.sp
            )
        }

        // ৩. ↗️ শেয়ার বাটন
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = {
                    val shareUrl = "https://playdramaflix.com/watch/$slug"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Watch $title on PlayDramaFlix: $shareUrl")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Drama"))
                },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Share",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}
