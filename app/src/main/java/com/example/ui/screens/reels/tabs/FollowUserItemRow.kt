package com.example.ui.screens.reels.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SuggestedPageDto

private val TikTokRed = Color(0xFFFE2C55)
private val GoldBadge = Color(0xFFF59E0B)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 🔲 ১ নম্বর ছবির হুবহু সাজেস্টেড ইউজার রো:
 * (অ্যাভাটার + গোল্ডেন 'V' ব্যাজ + নাম + "You May Like" + ক্যাপসুল লাল বাটন + '✕')
 */
@Composable
fun FollowUserItemRow(
    page: SuggestedPageDto,
    onProfileClick: () -> Unit,
    onFollowClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onProfileClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ১. অ্যাভাটার ও গোল্ডেন 'V' ব্যাজ
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(page.avatar ?: "https://ui-avatars.com/api/?name=${page.pageName}&background=222838&color=fff")
                    .crossfade(true)
                    .build(),
                contentDescription = page.pageName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // নিচের ডান কোণায় গোল্ডেন 'V' ব্যাজ
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(GoldBadge)
                    .border(1.2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ২. ইউজারের নাম ও "You May Like"
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = page.pageName,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "You May Like",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // ৩. ১ম ছবির হুবহু লাল ক্যাপসুল শেপ Follow বাটন
        Button(
            onClick = onFollowClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (page.isFollowing) Color(0xFF262C38) else TikTokRed
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text(
                text = if (page.isFollowing) "Following" else "Follow",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ৪. ডানপাশের '✕' রিমুভ আইকন
        IconButton(
            onClick = onDismissClick,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
