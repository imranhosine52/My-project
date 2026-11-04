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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

private val DeepCardBg = Color(0xFF10141E)
private val CardBorderColor = Color(0xFF1E2638)
private val ActiveGreen = Color(0xFF00E676)

// 🌟 দুই সাইডে নীল এবং মাঝখানে সবুজ রঙের প্রিমিয়াম গ্রেডিয়েন্ট কম্বিনেশন (Blue -> Green -> Blue)
private val BlueGreenBlueBorderBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF0091EA), // বাম সাইডে উজ্জ্বল নীল
        Color(0xFF00E676), // মাঝখানে নিওন সবুজ
        Color(0xFF0091EA)  // ডান সাইডে উজ্জ্বল নীল
    )
)

/**
 * 🏷️ ফাইলের নাম থেকে .my মুছে আসল ফরম্যাট (.mkv/.mp4) যুক্ত করার হেল্পার
 */
private fun resolveCleanTitleWithRealFormat(title: String, path: String, mimeType: String?): String {
    val realExt = when {
        mimeType?.contains("mkv", true) == true || path.endsWith(".mkv", true) -> ".mkv"
        mimeType?.contains("mp4", true) == true || path.endsWith(".mp4", true) -> ".mp4"
        mimeType?.contains("webm", true) == true || path.endsWith(".webm", true) -> ".webm"
        mimeType?.contains("avi", true) == true || path.endsWith(".avi", true) -> ".avi"
        mimeType?.contains("mov", true) == true || path.endsWith(".mov", true) -> ".mov"
        else -> {
            val rawExt = path.substringAfterLast(".", "")
            if (rawExt.isNotBlank() && rawExt.length in 2..4 && !rawExt.equals("my", true)) ".$rawExt" else ".mkv"
        }
    }

    var baseName = title.trim()
    if (baseName.endsWith(".my", ignoreCase = true)) {
        baseName = baseName.substring(0, baseName.length - 3).trimEnd('.')
    }

    listOf(".mkv", ".mp4", ".webm", ".avi", ".mov", ".flv", ".ts").forEach { ext ->
        if (baseName.endsWith(ext, ignoreCase = true)) {
            baseName = baseName.removeSuffix(ext)
        }
    }

    return "$baseName$realExt"
}

/**
 * 🔢 স্মার্ট ন্যাচারাল এপিসোড ট্র্যাকার (E01, E02... সিরিয়ালে সাজানো)
 */
private fun extractEpisodeNumber(name: String): Int? {
    val regexList = listOf(
        Regex("""(?i)[se]\d+[e|ep](\d+)"""),              // S01E24, s1e24
        Regex("""(?i)\b(?:ep|episode)[-_\s.]*(\d+)\b"""), // Ep 24, Episode_24
        Regex("""(?i)\b[e](\d+)\b"""),                    // E24
        Regex("""(?i)[-_.\s](\d{1,4})[-_.\s]""")          // _24_ or .24.
    )
    for (regex in regexList) {
        val match = regex.find(name)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            if (num != null) return num
        }
    }
    return null
}

private fun compareNatural(str1: String, str2: String): Int {
    val regex = Regex("(\\d+)|(\\D+)")
    val tokens1 = regex.findAll(str1).map { it.value }.toList()
    val tokens2 = regex.findAll(str2).map { it.value }.toList()

    val length = minOf(tokens1.size, tokens2.size)
    for (i in 0 until length) {
        val t1 = tokens1[i]
        val t2 = tokens2[i]

        val num1 = t1.toLongOrNull()
        val num2 = t2.toLongOrNull()

        if (num1 != null && num2 != null) {
            val cmp = num1.compareTo(num2)
            if (cmp != 0) return cmp
        } else {
            val cmp = t1.compareTo(t2, ignoreCase = true)
            if (cmp != 0) return cmp
        }
    }
    return tokens1.size.compareTo(tokens2.size)
}

private fun naturalSortVideos(videos: List<LocalVideoItem>): List<LocalVideoItem> {
    val hasEpisodes = videos.count { extractEpisodeNumber(it.title) != null } >= (videos.size / 2).coerceAtLeast(1)

    return if (hasEpisodes) {
        // এপিসোড নম্বর থাকলে ১ থেকে শেষ পর্যন্ত ক্রমানুসারে সাজানো হবে
        videos.sortedWith(
            compareBy<LocalVideoItem> {
                extractEpisodeNumber(it.title) ?: Int.MAX_VALUE
            }.thenComparing { a, b ->
                compareNatural(a.title, b.title)
            }
        )
    } else {
        // সাধারণ ফাইলগুলোর জন্য ন্যাচারাল আলফানিউমেরিক সর্টিং (1, 2, 10...)
        videos.sortedWith { a, b -> compareNatural(a.title, b.title) }
    }
}

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
    // 🌟 এপিসোডগুলোকে ১ থেকে ক্রমানুসারে সিরিয়ালভাবে সাজানো হয়েছে
    val sortedVideos = remember(videos) { naturalSortVideos(videos) }

    val backgroundModifier = if (isLandscapeDrawer) {
        Modifier.background(
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xD9080B12),
                    Color(0xF2080B12)
                )
            )
        )
    } else {
        Modifier.background(Color(0xFF090C14))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        // =============================================================
        // ১. হেডার সেকশন
        // =============================================================
        if (isLandscapeDrawer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${folderName.ifBlank { "Videos" }} (${sortedVideos.size})",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161F2E),
                    border = BorderStroke(0.6.dp, Color(0xFF23324A)),
                    modifier = Modifier.clickable { onNextVideoClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Next",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        } else {
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${sortedVideos.size} videos in folder",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.5.sp
                    )
                }

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
        }

        HorizontalDivider(
            color = if (isLandscapeDrawer) Color(0x1FFFFFFF) else Color(0xFF1E2638),
            thickness = 0.5.dp
        )

        // =============================================================
        // ২. সিরিয়াল অনুযায়ী সাজানো প্লেলিস্ট তালিকা
        // =============================================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = if (isLandscapeDrawer) 8.dp else 12.dp,
                vertical = 6.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isLandscapeDrawer) 6.dp else 8.dp)
        ) {
            items(
                items = sortedVideos,
                key = { it.id }
            ) { videoItem ->
                val isPlayingThis = (videoItem.id == currentVideoId)

                val cleanTitle = remember(videoItem.title, videoItem.path, videoItem.mimeType) {
                    resolveCleanTitleWithRealFormat(videoItem.title, videoItem.path, videoItem.mimeType)
                }

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingThis) {
                            Color(0xFF0C1D18)
                        } else {
                            if (isLandscapeDrawer) Color(0x66131826) else DeepCardBg
                        }
                    ),
                    // 🔥 দুই সাইডে নীল ও মাঝখানে সবুজ কম্বিনেশন গ্রেডিয়েন্ট বর্ডার
                    border = BorderStroke(
                        width = if (isPlayingThis) 0.8.dp else 0.5.dp,
                        brush = if (isPlayingThis) BlueGreenBlueBorderBrush else SolidColor(CardBorderColor)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectVideo(videoItem) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isLandscapeDrawer) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isLandscapeDrawer) 72.dp else 96.dp)
                                .height(if (isLandscapeDrawer) 44.dp else 56.dp)
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

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = cleanTitle,
                                color = if (isPlayingThis) ActiveGreen else Color.White,
                                fontSize = if (isLandscapeDrawer) 11.sp else 13.sp,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = if (isLandscapeDrawer) 14.5.sp else 16.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = videoItem.formattedSize,
                                    color = Color(0xFF8E95A5),
                                    fontSize = if (isLandscapeDrawer) 10.sp else 11.sp
                                )
                                Text("•", color = Color(0xFF4C5466), fontSize = 10.sp)
                                Text(
                                    text = videoItem.formattedDate,
                                    color = Color(0xFF8E95A5),
                                    fontSize = if (isLandscapeDrawer) 10.sp else 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
