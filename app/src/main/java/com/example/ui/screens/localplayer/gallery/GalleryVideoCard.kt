@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens.localplayer.gallery

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

private val CardBackground = Color(0xFF101522)
private val CardBorderNormal = Color(0xFF1D2638)
private val CyanActive = Color(0xFF00E5FF)
private val EmeraldActive = Color(0xFF00E676)
private val BadgeNeonRed = Color(0xFFFF2A4B)
private val TextSecondaryMuted = Color(0xFF8E95A5)

// 🌟 Selected Card Glowing Gradient Border
private val SelectionGlowBrush = Brush.horizontalGradient(
    colors = listOf(CyanActive, EmeraldActive)
)

/**
 * 🎬 Asynchronous Downscaled Video Thumbnail
 */
@Composable
fun AsyncVideoThumbnail(
    item: LocalVideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var memoryBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.path) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val file = File(item.path)
                    if (file.exists()) {
                        val bmp = context.contentResolver.loadThumbnail(
                            item.contentUri,
                            Size(320, 200),
                            null
                        )
                        memoryBitmap = bmp
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val bmp = ThumbnailUtils.createVideoThumbnail(
                        item.path,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                    memoryBitmap = bmp
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF141926)),
        contentAlignment = Alignment.Center
    ) {
        if (memoryBitmap != null) {
            Image(
                bitmap = memoryBitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 🔲 1. Premium Gallery Video Grid Card (16:10 Cinematic Aspect Ratio)
 */
@Composable
fun GalleryVideoGridCard(
    item: LocalVideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNew = remember(item.dateAdded) {
        (System.currentTimeMillis() / 1000) - item.dateAdded < (14 * 24 * 3600)
    }

    val hasSubtitle = remember(item.path) {
        val srtPath = item.path.substringBeforeLast(".") + ".srt"
        val vttPath = item.path.substringBeforeLast(".") + ".vtt"
        File(srtPath).exists() || File(vttPath).exists()
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.8.dp,
            brush = if (isSelected) SelectionGlowBrush else SolidColor(CardBorderNormal)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            // ১. থিম্যাটিক থাম্বনেইল ফ্রেম (16:10 Cinematic Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
                    .background(Color(0xFF141926))
            ) {
                AsyncVideoThumbnail(item = item, modifier = Modifier.fillMaxSize())

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // টপ-লেফট ব্যাজেস: [NEW] ও [রেজোলিউশন (4K, 1080p, 720p)]
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isNew) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BadgeNeonRed
                        ) {
                            Text(
                                text = "NEW",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    item.resolution?.let { resText ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = resText,
                                color = CyanActive,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // টপ-রাইট: সিলেকশন চেকমার্ক বা ফেভারিট স্টার
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                ) {
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) CyanActive else Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, if (isSelected) CyanActive else Color.White.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else if (item.isStarred) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Starred",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // বটম-রাইট: সময়সীমা (Duration) ও SRT ব্যাজ
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasSubtitle) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = EmeraldActive
                        ) {
                            Text(
                                text = "SRT",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }

                    if (item.formattedDuration.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.80f)
                        ) {
                            Text(
                                text = item.formattedDuration,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ২. টাইটেল ও থ্রি-ডট মেনু
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName.ifBlank { item.title },
                        color = if (isSelected) CyanActive else Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "${item.formattedSize} • ${item.folderName}",
                        color = TextSecondaryMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 12.dp)
                        ) { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 📄 2. Premium Gallery Video List Card (Horizontal Row Layout)
 */
@Composable
fun GalleryVideoListCard(
    item: LocalVideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.8.dp,
            brush = if (isSelected) SelectionGlowBrush else SolidColor(CardBorderNormal)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // থাম্বনেইল বক্স
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141926)),
                contentAlignment = Alignment.Center
            ) {
                AsyncVideoThumbnail(item = item, modifier = Modifier.fillMaxSize())

                if (item.formattedDuration.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = item.formattedDuration,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }

            // বিবরণ
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName.ifBlank { item.title },
                    color = if (isSelected) CyanActive else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.formattedSize,
                        color = TextSecondaryMuted,
                        fontSize = 11.sp
                    )
                    Text("•", color = TextSecondaryMuted, fontSize = 11.sp)
                    Text(
                        text = item.folderName,
                        color = CyanActive,
                        fontSize = 11.sp
                    )
                }
            }

            // সিলেকশন চেকমার্ক বা থ্রি-ডট
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CyanActive else Color.Transparent)
                        .border(1.2.dp, if (isSelected) CyanActive else Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
