@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.AppDownloadManager
import com.example.util.UniversalVideoExtractor
import kotlinx.coroutines.launch

private val SafeGreen = Color(0xFF00D166)
private val CardDarkBg = Color(0xFF131824)

private enum class DownloaderTab {
    DOWNLOAD,
    TASKS
}

@Composable
fun VideoDownloaderScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentTab by remember { mutableStateOf(DownloaderTab.DOWNLOAD) }
    var inputUrl by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var extractedVideoInfo by remember { mutableStateOf<DownloadableVideoInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeDownloads by AppDownloadManager.activeDownloads.collectAsStateWithLifecycle()

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (clipText.isNotBlank() && (clipText.startsWith("http://") || clipText.startsWith("https://"))) {
                inputUrl = clipText
                Toast.makeText(context, "Link pasted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No valid video link found in clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot read clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun analyzeVideoLink() {
        val targetUrl = inputUrl.trim()
        if (targetUrl.isBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
            Toast.makeText(context, "Please enter a valid video link", Toast.LENGTH_SHORT).show()
            return
        }

        focusManager.clearFocus()
        isAnalyzing = true
        errorMessage = null
        extractedVideoInfo = null

        coroutineScope.launch {
            val result = UniversalVideoExtractor.extractVideoInfo(targetUrl)
            isAnalyzing = false
            if (result.isSuccess) {
                extractedVideoInfo = result.getOrNull()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to extract video. Please check link."
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 🔝 Top App Bar
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark)
                                    .clickable { onBackClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "All-In-One Downloader",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "YouTube • Facebook • TikTok • Reels",
                                    color = SafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 📑 Tabs Header (Download vs Active Tasks)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { currentTab = DownloaderTab.DOWNLOAD }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Download Video",
                                color = if (currentTab == DownloaderTab.DOWNLOAD) SafeGreen else TextMuted,
                                fontSize = 13.5.sp,
                                fontWeight = if (currentTab == DownloaderTab.DOWNLOAD) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(2.5.dp)
                                    .background(if (currentTab == DownloaderTab.DOWNLOAD) SafeGreen else Color.Transparent)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { currentTab = DownloaderTab.TASKS }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Downloads (${activeDownloads.size})",
                                color = if (currentTab == DownloaderTab.TASKS) SafeGreen else TextMuted,
                                fontSize = 13.5.sp,
                                fontWeight = if (currentTab == DownloaderTab.TASKS) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(2.5.dp)
                                    .background(if (currentTab == DownloaderTab.TASKS) SafeGreen else Color.Transparent)
                            )
                        }
                    }
                }
            }

            // 📱 Screen Content Area
            if (currentTab == DownloaderTab.DOWNLOAD) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 🌐 Supported Platforms Badge Row
                    SupportedPlatformsRow()

                    // 🔗 Link Input Box & Paste Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Paste video link from any app:",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Input Box with Auto-Paste Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (inputUrl.isEmpty()) {
                                        Text("https://youtu.be/... or fb.watch/...", color = TextMuted, fontSize = 13.sp)
                                    }
                                    BasicTextField(
                                        value = inputUrl,
                                        onValueChange = { inputUrl = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                        cursorBrush = SolidColor(SafeGreen),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { analyzeVideoLink() }),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (inputUrl.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp).clickable { inputUrl = "" }
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SafeGreen.copy(alpha = 0.2f),
                                        modifier = Modifier.clickable { pasteFromClipboard() }
                                    ) {
                                        Text(
                                            text = "Paste",
                                            color = SafeGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // ⚡ Fetch & Download Button
                            Button(
                                onClick = { analyzeVideoLink() },
                                enabled = !isAnalyzing && inputUrl.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Fetching video streams...", color = Color.Black, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        Text("Analyze & Download", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ⚠️ Error Message Display
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF331418),
                            border = BorderStroke(0.8.dp, Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                            }
                        }
                    }

                    // 🎬 Extracted Video Formats & Quality Options
                    if (extractedVideoInfo != null) {
                        ExtractedVideoResultCard(
                            videoInfo = extractedVideoInfo!!,
                            onDownloadFormat = { format ->
                                AppDownloadManager.startDownload(context, extractedVideoInfo!!, format)
                                currentTab = DownloaderTab.TASKS
                            }
                        )
                    }
                }
            } else {
                // 📂 Active / Completed Downloads Tab
                if (activeDownloads.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.DownloadDone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("No downloads yet", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Videos you download will appear here and in your local gallery player.", color = TextMuted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeDownloads, key = { it.downloadId }) { task ->
                            DownloadTaskRowCard(
                                task = task,
                                onOpen = { AppDownloadManager.openDownloadedFile(context, task) },
                                onDelete = { AppDownloadManager.cancelDownload(context, task.downloadId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🌐 Supported Platforms Badge Grid
// -------------------------------------------------------------
@Composable
private fun SupportedPlatformsRow() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Supported Platforms", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val platforms = listOf(
                "🔴 YouTube",
                "⚫ TikTok (No Watermark)",
                "🔵 Facebook",
                "🟣 Instagram Reels",
                "⚪ X (Twitter)"
            )
            items(platforms) { tag ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardDarkBg,
                    border = BorderStroke(0.8.dp, BorderDark)
                ) {
                    Text(
                        text = tag,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🎬 Extracted Video Result & Formats Card
// -------------------------------------------------------------
@Composable
private fun ExtractedVideoResultCard(
    videoInfo: DownloadableVideoInfo,
    onDownloadFormat: (VideoFormatOption) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Video Thumbnail & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2638))
                ) {
                    if (!videoInfo.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(videoInfo.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = videoInfo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(32.dp).align(Alignment.Center))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(videoInfo.platform.iconColorHex).copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = videoInfo.platform.label,
                            color = Color(videoInfo.platform.iconColorHex),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = videoInfo.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = BorderDark, thickness = 0.6.dp)

            Text("Select Download Quality:", color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)

            // Quality Options List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                videoInfo.availableFormats.forEach { format ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceVariantDark,
                        border = BorderStroke(0.8.dp, if (format.qualityLabel.contains("1080p") || format.isAudioOnly) SafeGreen else BorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownloadFormat(format) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (format.isAudioOnly) Icons.Default.Audiotrack else Icons.Default.Hd,
                                    contentDescription = null,
                                    tint = if (format.isAudioOnly) Color(0xFFFFB300) else SafeGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(format.qualityLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${format.resolutionText} • ${format.extension.uppercase()}", color = TextMuted, fontSize = 10.5.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(format.formattedSize, color = SafeGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = SafeGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 📦 Download Task Progress Row Card
// -------------------------------------------------------------
@Composable
private fun DownloadTaskRowCard(
    task: ActiveDownloadTask,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (task.status == DownloadStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (task.status == DownloadStatus.COMPLETED) SafeGreen else Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(task.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${task.platform.label} • ${task.formatLabel}", color = TextMuted, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == DownloadStatus.COMPLETED) {
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = SafeGreen)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { task.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SafeGreen,
                    trackColor = SurfaceVariantDark
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Downloading...", color = TextMuted, fontSize = 10.5.sp)
                    Text("${task.progressPercent}%", color = SafeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if (task.status == DownloadStatus.COMPLETED) {
                Text("✓ Download completed (Saved to Downloads/PlayDramaFlix)", color = SafeGreen, fontSize = 11.sp)
            } else if (task.status == DownloadStatus.FAILED) {
                Text("✕ Download failed or cancelled", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
        }
    }
}
