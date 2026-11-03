@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.localplayer.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val FolderDarkGrey = Color(0xFF333E50)
private val ElectricBlue = Color(0xFF2979FF)
private val BadgeRed = Color(0xFFFF2A4B)
private val CardSurface = Color(0xFF131722)

/**
 * 🎬 ভিডিও ও ইমেজ দ্রুত প্রিভিউ করার জন্য থাম্বনেইল কম্পোনেন্ট
 */
@Composable
fun VideoThumbnailPreview(
    item: LocalVideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmapThumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    val isVideo = item.mimeType?.startsWith("video") == true ||
            item.path.endsWith(".mp4", true) ||
            item.path.endsWith(".mkv", true)

    LaunchedEffect(item.path) {
        if (isVideo) {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val file = File(item.path)
                        if (file.exists()) {
                            val bmp = context.contentResolver.loadThumbnail(item.contentUri, Size(250, 250), null)
                            bitmapThumbnail = bmp
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val bmp = ThumbnailUtils.createVideoThumbnail(
                            item.path,
                            MediaStore.Images.Thumbnails.MINI_KIND
                        )
                        bitmapThumbnail = bmp
                    }
                } catch (_: Exception) {}
            }
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

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 📂 ফোল্ডার রো আইটেম
 */
@Composable
fun ScreenshotStyleFolderItem(
    folder: LocalVideoFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(54.dp).height(44.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FolderDarkGrey),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (folder.folderName.contains("movie", true)) Icons.Default.Movie else Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF6B7B96),
                    modifier = Modifier.size(28.dp)
                )
            }

            if (folder.videoCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(BadgeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (folder.videoCount > 99) "99+" else folder.videoCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.folderName,
                color = if (folder.folderName.contains("movie", true)) ElectricBlue else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.videoCount} items • ${folder.formattedTotalSize}",
                color = Color(0xFF8E95A5),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 🔲 গ্রিড ভিউ কার্ড
 */
@Composable
fun Screenshot2GridItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAudio = item.mimeType?.startsWith("audio") == true

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141722)),
        border = BorderStroke(0.6.dp, Color(0xFF222638)),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .combinedClickable(onClick = onClick, onLongClick = onMenuClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isAudio) {
                VideoThumbnailPreview(item = item, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF221A30)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = item.formattedSize,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 📄 লিস্ট/টেবিল ভিউ আইটেম
 */
@Composable
fun Screenshot3ListItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAudio = item.mimeType?.startsWith("audio") == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp, 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!isAudio) {
                VideoThumbnailPreview(item = item, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.formattedSize} • ${item.formattedDate}",
                color = Color(0xFF8E95A5),
                fontSize = 11.5.sp
            )
        }

        IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color(0xFF8E95A5),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * ⚙️ ফাইল অ্যাকশন বটম শীট
 */
@Composable
fun FileActionMenuSheet(
    item: LocalVideoItem,
    isSafeFolderItem: Boolean = false,
    isTrashItem: Boolean = false,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onToggleStar: () -> Unit,
    onMoveToSafe: () -> Unit,
    onRestoreFromSafe: () -> Unit = {},
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit = {},
    onDeletePermanently: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.formattedSize} • ${item.path}",
                color = Color(0xFF8E95A5),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = Color(0xFF222638), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 6.dp))

            when {
                isSafeFolderItem -> {
                    SheetActionRow(Icons.Default.LockOpen, "Unhide from Safe Folder", Color(0xFF00E5FF), onRestoreFromSafe)
                    SheetActionRow(Icons.Default.DeleteForever, "Delete Permanently", Color(0xFFFF5252), onDeletePermanently)
                }
                isTrashItem -> {
                    SheetActionRow(Icons.Default.Restore, "Restore File", Color(0xFF00E676), onRestore)
                    SheetActionRow(Icons.Default.DeleteForever, "Delete Permanently", Color(0xFFFF5252), onDeletePermanently)
                }
                else -> {
                    SheetActionRow(Icons.Default.Share, "Share", Color.White, onShare)
                    SheetActionRow(Icons.Default.DriveFileMove, "Move to Folder", Color.White, onMove)
                    SheetActionRow(Icons.Default.Edit, "Rename", Color.White, onRename)
                    SheetActionRow(
                        icon = if (item.isStarred) Icons.Default.Star else Icons.Outlined.Star,
                        label = if (item.isStarred) "Remove from Starred" else "Add to Starred",
                        color = if (item.isStarred) Color(0xFFFFB300) else Color.White,
                        onClick = onToggleStar
                    )
                    SheetActionRow(Icons.Default.Lock, "Move to Safe Folder", Color.White, onMoveToSafe)
                    SheetActionRow(Icons.Default.Delete, "Move to Trash", Color(0xFFFF5252), onMoveToTrash)
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 📲 ফাইল শেয়ার হেল্পার ফাংশন
 */
fun shareSingleFile(context: Context, item: LocalVideoItem) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, item.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${item.title}"))
    } catch (_: Exception) {}
}
