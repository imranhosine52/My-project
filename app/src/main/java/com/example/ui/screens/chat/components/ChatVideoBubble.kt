package com.example.ui.screens.chat.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 🎬 ভিডিও মেসেজ বাবল (TikTok 9:16 অথবা YouTube 16:9 অটোমেটিক সাইজ)
 */
@Composable
fun VideoMessageThumbnailBubble(
    videoUrl: String,
    imageUrl: String?,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVerticalRatio by remember { mutableStateOf(true) }
    var durationText by remember { mutableStateOf("▶ 0:15") }

    LaunchedEffect(videoUrl) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoUrl, HashMap())
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 720f
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1280f
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15000L
                retriever.release()

                // 🎯 রেশিও ডিটেকশন: খাড়া হলে TikTok, চওড়া হলে YouTube
                isVerticalRatio = height > width
                val sec = (durationMs / 1000) % 60
                val min = (durationMs / 1000) / 60
                durationText = String.format(Locale.US, "▶ %d:%02d", min, sec)
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (isVerticalRatio) 0.68f else 1.77f) // TikTok vs YouTube
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F2C34))
            .clickable { onVideoClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Video Thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncVideoThumbnailLoader(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ডার্ক সেমি-ট্রান্সপারেন্ট ওভারলে
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.28f)))

        // সেন্ট্রাল প্লে বাটন
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // ৩ নম্বর ছবির মতো ভিডিও ডিউরেশন ব্যাজ
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(0.7f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        ) {
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 🎬 ২ নম্বর ছবির হুবহু লাইভ আপলোডিং ভিডিও বাবল: [✕] 62% (3s left)
 */
@Composable
fun UploadingVideoBubble(
    videoUri: Uri,
    percent: Int,
    secondsLeft: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = WhatsAppSentBubble,
            modifier = Modifier
                .width(260.dp)
                .height(220.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // আসল ভিডিওর ফ্রেম থাম্বনেল
                AsyncVideoThumbnailLoader(
                    videoUri = videoUri,
                    modifier = Modifier.fillMaxSize()
                )

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))

                // ২ নম্বর ছবির হুবহু WhatsApp লাইভ আপলোড প্রোগ্রেস পিল
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Cancel Upload",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCancel() }
                    )
                    Text(
                        text = "$percent% (${secondsLeft}s left)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 🖼️ ভিডিও থেকে অটোমেটিক ফ্রেম থাম্বনেল জেনারেটর (যাতে কোনো ভিডিও কালো বক্স না দেখায়)
 */
@Composable
fun AsyncVideoThumbnailLoader(
    videoUri: Uri? = null,
    videoUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(videoUri, videoUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoUri, videoUrl) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (videoUri != null) {
                    retriever.setDataSource(context, videoUri)
                } else if (!videoUrl.isNullOrBlank()) {
                    retriever.setDataSource(videoUrl, HashMap())
                }
                val frame = retriever.getFrameAtTime(1000000)
                retriever.release()
                bitmap = frame
            } catch (_: Exception) {}
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Video Frame",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color(0xFF1F2C34)))
    }
}
