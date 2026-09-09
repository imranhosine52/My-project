@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoItem
import com.example.ui.theme.*
import com.example.util.ActiveDownloadTask
import com.example.util.DownloadStateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onPlayDownloadedVideo: (LocalVideoItem) -> Unit, // 🎯 অ্যাপের নিজস্ব প্লেয়ারে অফলাইনে চালু করবে
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ০ = Downloading (ডাউনলোড হচ্ছে), ১ = Downloaded (অফলাইন গ্যালারি)
    var selectedTab by remember { mutableIntStateOf(0) }

    // ১. লাইভ ডাউনলোড পর্যবেক্ষণ
    val activeTasksMap by DownloadStateTracker.activeDownloads.collectAsStateWithLifecycle()
    val downloadingList = remember(activeTasksMap) {
        activeTasksMap.values.filter { !it.isCompleted }.reversed()
    }

    // ২. অফলাইন ডাউনলোড করা ভিডিও তালিকা
    var downloadedVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var isLoadingDownloaded by remember { mutableStateOf(false) }

    // 📁 ফোনের Download ফোল্ডারে থাকা ভিডিও স্ক্যান করা
    fun loadDownloadedVideos() {
        scope.launch {
            isLoadingDownloaded = true
            downloadedVideos = withContext(Dispatchers.IO) {
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

                            // শুধুমাত্র Download ফোল্ডারের MP4 ফাইলগুলো ফিল্টার করা
                            if (path.contains("Download", ignoreCase = true) && (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true))) {
                                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                                list.add(
                                    LocalVideoItem(
                                        id = id,
                                        title = name.substringBeforeLast("."),
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

                // যদি MediaStore-এ কিছু না মেলে তবে সরাসরি Download ডিরেক্টরি ফলব্যাক
                if (list.isEmpty()) {
                    try {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (downloadDir.exists() && downloadDir.isDirectory) {
                            downloadDir.listFiles { file ->
                                file.isFile && (file.name.endsWith(".mp4", ignoreCase = true) || file.name.endsWith(".mkv", ignoreCase = true))
                            }?.forEach { f ->
                                list.add(
                                    LocalVideoItem(
                                        id = f.hashCode().toLong(),
                                        title = f.nameWithoutExtension,
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
                        }
                    } catch (_: Exception) {}
                }

                list
            }
            isLoadingDownloaded = false
        }
    }

    LaunchedEffect(Unit) {
        loadDownloadedVideos()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            loadDownloadedVideos()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 🔝 Top Bar
            Surface(
                color = SurfaceDark,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Downloads & Offline Library",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 📑 ২টি ট্যাব: [ Downloading (X) ] ও [ Downloaded ]
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = TealAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = TealAccent,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Downloading",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (downloadingList.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = TealAccent
                                ) {
                                    Text(
                                        text = downloadingList.size.toString(),
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Downloaded (${downloadedVideos.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }

            // 📱 ট্যাব কনটেন্ট
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTab == 0) {
                    // =========================================================================
                    // 🚀 ট্যাব ১: লাইভ ডাউনলোড প্রোগ্রেস (MB ও % বৃদ্ধি পাবে)
                    // =========================================================================
                    if (downloadingList.isEmpty()) {
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
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No active downloads",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Active downloads will show real-time progress here.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(downloadingList, key = { it.id }) { task ->
                                LiveDownloadTaskCard(
                                    task = task,
                                    onCancel = {
                                        DownloadStateTracker.removeTask(task.id)
                                        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // =========================================================================
                    // 🎬 ট্যাব ২: অফলাইন গ্যালারি (ডাউনলোড সম্পন্ন হওয়া ভিডিও)
                    // =========================================================================
                    if (isLoadingDownloaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = TealAccent, strokeWidth = 2.5.dp)
                        }
                    } else if (downloadedVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No offline videos yet",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Downloaded videos will appear here to watch offline.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(downloadedVideos, key = { it.id }) { video ->
                                OfflineVideoItemCard(
                                    video = video,
                                    onPlay = { onPlayDownloadedVideo(video) },
                                    onDelete = {
                                        try {
                                            val f = File(video.path)
                                            if (f.exists()) f.delete()
                                            context.contentResolver.delete(video.contentUri, null, null)
                                            MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
                                            loadDownloadedVideos()
                                            Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🔄 লাইভ ডাউনলোড টাস্ক কার্ড (প্রোগ্রেস বার ও MB ট্র্যাকার)
// -------------------------------------------------------------
@Composable
private fun LiveDownloadTaskCard(
    task: ActiveDownloadTask,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141924)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ডাউনলোড আইকন
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TealAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // টাইটেল ও মেগা-বাইট প্রোগ্রেস টেক্সট
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${task.title} - EP ${task.episodeNumber}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (task.totalMb > 0) {
                            "${String.format(Locale.US, "%.1f", task.downloadedMb)} MB / ${String.format(Locale.US, "%.1f", task.totalMb)} MB"
                        } else if (task.downloadedMb > 0) {
                            "${String.format(Locale.US, "%.1f", task.downloadedMb)} MB downloaded"
                        } else {
                            "Connecting to server..."
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // লাইভ পার্সেন্টেজ
                Text(
                    text = "${task.progressPercent}%",
                    color = TealAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFF8E95A5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // প্রোগ্রেস বার
            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TealAccent,
                trackColor = Color(0xFF222B3D)
            )
        }
    }
}

// -------------------------------------------------------------
// 🎬 ডাউনলোড সম্পন্ন হওয়া ভিডিও কার্ড (MovieBox স্টাইল Play বাটন সহ)
// -------------------------------------------------------------
@Composable
private fun OfflineVideoItemCard(
    video: LocalVideoItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141924)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3D)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ভিডিও থাম্বনেইল
            Box(
                modifier = Modifier
                    .size(72.dp, 50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2638)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
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

            // টাইটেল ও সাইজ
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.formattedSize,
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                    Text("•", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "Offline Ready",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ▶ Play বাটন (MovieBox স্টাইল ক্যাপসুল বাটন)
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Play",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
