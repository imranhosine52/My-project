package com.example.ui.screens.shorts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun formatCountDisplay(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
fun ShortsActionColumn(
    context: Context,
    title: String,
    slug: String,
    likesCount: Long,
    isLiked: Boolean,
    isInWatchlist: Boolean,
    onLikeClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp) // 👈 সুন্দর কমপ্যাক্ট স্পেসিং
    ) {
        // ১. ❤️ লাইক বাটন
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFFF2A4B) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = formatCountDisplay(likesCount),
                color = if (isLiked) Color(0xFFFF2A4B) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ২. ↗️ শেয়ার বাটন
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
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
            Text(
                text = "Share",
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // ৩. 🔖 সেভ / বুকমার্ক বাটন
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onSaveClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isInWatchlist) Color(0xFF00E676) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Save",
                color = if (isInWatchlist) Color(0xFF00E676) else Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
