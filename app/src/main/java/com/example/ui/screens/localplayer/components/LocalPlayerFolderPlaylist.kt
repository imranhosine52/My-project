package com.example.ui.screens.localplayer.components

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2638)
private val ActiveGreen = Color(0xFF00E676)

/**
 * 🎵 প্লেইং ভিডিওতে মাখনের মতো মসৃণ ইকুয়ালাইজার বার্স
 */
@Composable
fun PlayingEqualizerBars(
    modifier: Modifier = Modifier,
    tint: Color = ActiveGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_anim")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 3f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 13f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.dp).height(h1.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.dp).height(h2.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.dp).height(h3.dp).background(tint, RoundedCornerShape(1.dp)))
    }
}

/**
 * 🖼️ মেমরি-অপ্টিমাইজড ফাস্ট ভিডিও থাম্বনেইল
 */
@Composable
private fun FastLocalVideoThumbnail(
    item: LocalVideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmapThumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.path) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val file = File(item.path)
                    if (file.exists()) {
                        val bmp = context.contentResolver.loadThumbnail(item.contentUri, Size(260, 160), null)
                        bitmapThumbnail = bmp
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val bmp = ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Images.Thumbnails.MINI_KIND)
                    bitmapThumbnail = bmp
                }
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmapThumbnail != null) {
            Image(
                bitmap = bitmapThumbnail!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // সময়সীমা ব্যাজ (Duration)
        if (item.formattedDuration.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = item.formattedDuration,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * 📂 ফোল্ডারের ভিডিও তালিকা (পোর্ট্রেটে নিচে এবং ল্যান্ডস্কেপে সাইড ড্রয়ারে কাজ করবে)
 */
@Composable
fun LocalPlayerFolderPlaylist(
    folderName: String,
    videos: List<LocalVideoItem>,
    currentVideoId: Long,
    onSelectVideo: (LocalVideoItem) -> Unit,
    onNextVideoClick: () -> Unit,
    isLandscapeDrawer: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        // ১. হেডার ও নেক্সট ভিডিও অ্যাকশন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = folderName.ifBlank { "Folder Videos" },
                    color = Color.White,
                    fontSize = if (isLandscapeDrawer) 13.5.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${videos.size} videos in folder",
                    color = Color(0xFF8E95A5),
                    fontSize = 11.5.sp
                )
            }

            // পরবর্তী ভিডিও দ্রুত প্লে করার বাটন
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161F2E),
                border = BorderStroke(0.8.dp, Color(0xFF23324A)),
                modifier = Modifier.clickable { onNextVideoClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Next",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Video",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

        // ২. মাখনের মতো মসৃণ অপ্টিমাইজড লিস্ট
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = videos,
                key = { it.id } // 👈 মসৃণ স্ক্রোল ও রি-কম্পোজিশন প্রতিরোধের জন্য আইডি ব্যবহার
            ) { videoItem ->
                val isPlayingThis = (videoItem.id == currentVideoId)

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) Color(0xFF0F1A24) else DeepCardBg
                    ),
                    border = BorderStroke(
                        width = if (isPlayingThis) 1.2.dp else 0.8.dp,
                        color = if (isPlayingThis) ActiveGreen else CardBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectVideo(videoItem) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // থাম্বনেইল বক্স
                        Box(
                            modifier = Modifier
                                .width(if (isLandscapeDrawer) 76.dp else 96.dp)
                                .height(if (isLandscapeDrawer) 48.dp else 56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1A2130))
                        ) {
                            FastLocalVideoThumbnail(
                                item = videoItem,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isPlayingThis) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PlayingEqualizerBars(modifier = Modifier.size(16.dp, 12.dp))
                                }
                            }
                        }

                        // ভিডিও টাইটেল ও মেটাডাটা
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = videoItem.title,
                                color = if (isPlayingThis) ActiveGreen else Color.White,
                                fontSize = if (isLandscapeDrawer) 11.5.sp else 13.sp,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = videoItem.formattedSize,
                                    color = Color(0xFF8E95A5),
                                    fontSize = 11.sp
                                )
                                Text("•", color = Color(0xFF4C5466), fontSize = 10.sp)
                                Text(
                                    text = videoItem.formattedDate,
                                    color = Color(0xFF8E95A5),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
