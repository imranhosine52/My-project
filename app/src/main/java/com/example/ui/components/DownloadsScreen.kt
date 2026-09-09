@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocalVideoItem
import com.example.ui.theme.*
import com.example.util.ActiveDownloadTask
import com.example.util.DownloadStateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// 🎨 মিনিমালিস্ট ব্ল্যাক ও হোয়াইট থিম কালার
private val PureBlackBg = Color(0xFF000000)
private val DarkCardBg = Color(0xFF10131B)
private val CardBorderColor = Color(0xFF1A1F2C)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMutedGray = Color(0xFF94A3B8)

// 🌟 প্রিমিয়াম ব্লু-গ্রিন গ্রেডিয়েন্ট ব্রাশ (MovieBox Play বাটন স্টাইল)
private val BlueGreenPlayBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF), // Vibrant Electric Blue
        Color(0xFF00C853)  // Clean Emerald Green
    )
)

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onPlayDownloadedVideo: (LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ০ = Ongoing (ডাউনলোড হচ্ছে), ১ = Completed (অফলাইন ভিডিও)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // লাইভ ডাউনলোড পর্যবেক্ষণ
    val activeTasksMap by DownloadStateTracker.activeDownloads.collectAsStateWithLifecycle()
    val ongoingList = remember(activeTasksMap) {
        activeTasksMap.values.filter { !it.isCompleted }.reversed()
    }

    // অফলাইন ডাউনলোড হওয়া ভিডিও
    var completedVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var isLoadingCompleted by remember { mutableStateOf(false) }

    fun loadCompletedVideos() {
        scope.launch {
            isLoadingCompleted = true
            completedVideos = withContext(Dispatchers.IO) {
                val list = mutableListOf<LocalVideoItem>()
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DATE_ADDED
                )

                try {
                    context.contentResolver.query(
                        collection,
                        projection,
                        null,
                        null,
                        "${MediaStore.Video.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: "Video"
                            val dur = cursor.getLong(durCol)
                            val size = cursor.getLong(sizeCol)
                            val path = cursor.getString(dataCol) ?: ""
                            val date = cursor.getLong(dateCol)

                            if (path.contains("Download", ignoreCase = true) && 
                                (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true))) {
                                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                                list.add(
                                    LocalVideoItem(
                                        id = id,
                                        title = name.substringBeforeLast(".").replace("_", " "),
                                        displayName = name,
                                        durationMs = dur,
                                        sizeBytes = size,
                                        path = path,
                                        contentUriString = uri.toString(),
                                        folderName = "Downloads",
                                        dateAdded = date,
                                        mimeType = "video/mp4"
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}

                // ব্যাকআপ ডিরেক্টরি স্ক্যান
                if (list.isEmpty()) {
                    try {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        downloadDir.listFiles { file ->
                            file.isFile && (file.name.endsWith(".mp4", true) || file.name.endsWith(".mkv", true))
                        }?.forEach { f ->
                            list.add(
                                LocalVideoItem(
                                    id = f.hashCode().toLong(),
                                    title = f.nameWithoutExtension.replace("_", " "),
                                    displayName = f.name,
                                    durationMs = 0L,
                                    sizeBytes = f.length(),
                                    path = f.absolutePath,
                                    contentUriString = Uri.fromFile(f).toString(),
                                    folderName = "Downloads",
                                    dateAdded = f.lastModified() / 1000,
                                    mimeType = "video/mp4"
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
                list
            }
            isLoadingCompleted = false
        }
    }

    LaunchedEffect(Unit) {
        loadCompletedVideos()
    }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1) {
            loadCompletedVideos()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlackBg)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. ফুলস্ক্রিন হেডার ও ৩ নম্বর ছবির মতো স্লিক পিল ট্যাব
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Downloads",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 🌟 ৩ নম্বর ছবির মতো [ Ongoing 1 ] ও [ Completed 98 ] ক্যাপসুল বাটন
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ongoing Tab Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTabIndex == 0) Color(0xFF222838) else Color(0xFF10131B))
                            .border(
                                width = 1.dp,
                                color = if (selectedTabIndex == 0) Color(0xFF007AFF).copy(alpha = 0.6f) else Color(0xFF1E2433),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTabIndex = 0 }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ongoing ${ongoingList.size}",
                            color = if (selectedTabIndex == 0) TextWhite else TextMutedGray,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    // Completed Tab Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTabIndex == 1) Color(0xFF222838) else Color(0xFF10131B))
                            .border(
                                width = 1.dp,
                                color = if (selectedTabIndex == 1) Color(0xFF007AFF).copy(alpha = 0.6f) else Color(0xFF1E2433),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTabIndex = 1 }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Completed ${completedVideos.size}",
                            color = if (selectedTabIndex == 1) TextWhite else TextMutedGray,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF161A24), thickness = 0.8.dp)

            // =========================================================================
            // 📱 ২. ট্যাব কনটেন্ট
            // =========================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTabIndex == 0) {
                    // -----------------------------------------------------------------
                    // ⏳ ONGOING TAB (ডাউনলোড চলছে + Pause/Cancel অপশন)
                    // -----------------------------------------------------------------
                    if (ongoingList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color(0xFF334155),
                                    modifier = Modifier.size(46.dp)
                                )
                                Text("No active downloads", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Downloading video progress will appear here.", color = TextMutedGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(ongoingList, key = { it.id }) { task ->
                                MinimalistOngoingDownloadCard(
                                    task = task,
                                    onCancel = {
                                        DownloadStateTracker.removeTask(task.id)
                                        Toast.makeText(context, "Download stopped", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // -----------------------------------------------------------------
                    // 🎬 COMPLETED TAB (আসল থাম্বনেইল + ব্লু-গ্রিন Play বাটন)
                    // -----------------------------------------------------------------
                    if (isLoadingCompleted) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF007AFF), strokeWidth = 2.5.dp)
                        }
                    } else if (completedVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFF334155),
                                    modifier = Modifier.size(46.dp)
                                )
                                Text("No downloaded videos yet", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Downloaded videos will appear here to watch offline.", color = TextMutedGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(completedVideos, key = { it.id }) { video ->
                                PremiumOfflineVideoCard(
                                    video = video,
                                    onPlay = { onPlayDownloadedVideo(video) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🔄 ৩ নম্বর ছবির স্টাইলের Ongoing কার্ড (Pause/Stop অপশন সহ)
// =========================================================================
@Composable
private fun MinimalistOngoingDownloadCard(
    task: ActiveDownloadTask,
    onCancel: () -> Unit
) {
    var isPaused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(0.8.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ডাউনলোড আইকন
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF162032)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // টাইটেল ও সাইজ
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${task.title} - EP ${task.episodeNumber}",
                        color = TextWhite,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (task.totalMb > 0) {
                            "${String.format(Locale.US, "%.1f", task.downloadedMb)} MB / ${String.format(Locale.US, "%.1f", task.totalMb)} MB"
                        } else {
                            "Connecting..."
                        },
                        color = TextMutedGray,
                        fontSize = 11.5.sp
                    )
                }

                // % কাউন্টার
                Text(
                    text = "${task.progressPercent}%",
                    color = Color(0xFF007AFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // ⏸️ Pause / Resume বাটন
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2433))
                        .clickable { isPaused = !isPaused },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause/Resume",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // ✕ Cancel বাটন
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2433))
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // স্লিম প্রোগ্রেস বার
            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF007AFF),
                trackColor = Color(0xFF1E2433)
            )
        }
    }
}

// =========================================================================
// 🎬 Completed অফলাইন কার্ড (আসল থাম্বনেইল + ব্লু-গ্রিন Play বাটন, ডিলিট ছাড়া)
// =========================================================================
@Composable
private fun PremiumOfflineVideoCard(
    video: LocalVideoItem,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    var videoThumbnailBitmap by remember(video.id) { mutableStateOf<Bitmap?>(null) }

    // 🖼️ MediaStore থেকে সরাসরি আসল ভিডিও ফ্রেম থাম্বনেইল তৈরি
    LaunchedEffect(video.path) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(video.path)
                if (file.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val bmp = context.contentResolver.loadThumbnail(video.contentUri, Size(200, 140), null)
                        videoThumbnailBitmap = bmp
                    } else {
                        @Suppress("DEPRECATION")
                        val bmp = ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND)
                        videoThumbnailBitmap = bmp
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(0.8.dp, CardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🖼️ আসল ভিডিও থাম্বনেইল বক্স
            Box(
                modifier = Modifier
                    .size(68.dp, 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161A24)),
                contentAlignment = Alignment.Center
            ) {
                if (videoThumbnailBitmap != null) {
                    Image(
                        bitmap = videoThumbnailBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // ছোট প্লে ব্যাজ
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // টাইটেল ও সাইজ (সাদা ও হালকা গ্রে টেক্সট)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.formattedSize,
                        color = TextMutedGray,
                        fontSize = 11.sp
                    )
                    Text("•", color = TextMutedGray, fontSize = 11.sp)
                    Text(
                        text = "Offline Ready",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 🌟 নীল এবং গ্রিন গ্রেডিয়েন্ট কম্বিনেশনের প্রিমিয়াম [ ▶ Play ] বাটন (MovieBox স্টাইল)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BlueGreenPlayBrush)
                    .clickable { onPlay() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Play",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
