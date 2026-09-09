@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.text.format.Formatter
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark

data class DownloadItem(
    val id: Long,
    val title: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isFinished: Boolean
)

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onPlayDownloadedVideo: (String) -> Unit
) {
    val context = LocalContext.current
    var downloadList by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }

    fun refreshList() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val cursor = dm.query(DownloadManager.Query()) ?: return
        val list = mutableListOf<DownloadItem>()

        val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val bytesSoFarCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalBytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

        while (cursor.moveToNext()) {
            val id = if (idCol != -1) cursor.getLong(idCol) else 0L
            val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Downloaded Episode" else "Downloaded Episode"
            val downloaded = if (bytesSoFarCol != -1) cursor.getLong(bytesSoFarCol) else 0L
            val total = if (totalBytesCol != -1) cursor.getLong(totalBytesCol) else 0L
            val status = if (statusCol != -1) cursor.getInt(statusCol) else 0
            list.add(DownloadItem(id, title, downloaded, total, status == DownloadManager.STATUS_SUCCESSFUL))
        }
        cursor.close()
        downloadList = list.reversed()
    }

    LaunchedEffect(Unit) { refreshList() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(color = SurfaceDark, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Downloads", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (downloadList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloads yet", color = Color(0xFF8E95A5), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(downloadList, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00D166).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isFinished) Icons.Default.CheckCircle else Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color(0xFF00D166),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sizeText = if (item.totalBytes > 0) Formatter.formatFileSize(context, item.totalBytes) else "Downloading..."
                                    Text(sizeText, color = Color(0xFF8E95A5), fontSize = 12.sp)
                                }

                                if (item.isFinished) {
                                    IconButton(onClick = { onPlayDownloadedVideo(item.title) }) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
