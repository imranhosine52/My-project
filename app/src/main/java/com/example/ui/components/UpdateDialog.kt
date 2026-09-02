@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AppVersionCheckResponse
import com.example.ui.theme.*
import com.example.util.InAppUpdateManager
import com.example.util.UpdateDownloadState
import java.util.Locale

private val PrimaryGreen = Color(0xFF00C853)
private val AlertRed = Color(0xFFFF3B30)
private val CardSurfaceDark = Color(0xFF131722)

@Composable
fun UpdateDialog(
    updateInfo: AppVersionCheckResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by InAppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    // 🚨 বাধ্যতামূলক আপডেট হলে ব্যাক বাটন বন্ধ থাকবে
    BackHandler(enabled = updateInfo.forceUpdate) {
        // Cannot dismiss when force update is active
    }

    if (updateInfo.forceUpdate) {
        // =========================================================================
        // 🔒 মোড ১: বাধ্যতামূলক আপডেট (Force Update Modal - স্ক্রিন সম্পূর্ণ ব্লক)
        // =========================================================================
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                    border = BorderStroke(1.2.dp, AlertRed.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AlertRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AlertRed)
                        ) {
                            Text(
                                text = "MANDATORY UPDATE v${updateInfo.latestVersion}",
                                color = AlertRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(AlertRed, Color(0xFFFF9100)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = updateInfo.displayTitle.ifBlank { "Critical Update Required!" },
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = updateInfo.displayMessage.ifBlank {
                                "A critical app update is required to continue streaming. Please update to the latest version now."
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        // ডাউনলোড প্রোগ্রেস বার
                        DownloadProgressBarSection(downloadState = downloadState, progressColor = AlertRed)

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                val apkUrl = updateInfo.targetDownloadUrl
                                if (apkUrl.isNotBlank()) {
                                    InAppUpdateManager.startInAppDownload(
                                        context = context,
                                        downloadUrl = apkUrl,
                                        targetVersion = updateInfo.latestVersion
                                    )
                                } else {
                                    Toast.makeText(context, "No download URL available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isDownloading) "Downloading..." else "Update Now to Continue",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // 📱 মোড ২: ঐচ্ছিক আপডেট (ক্র্যাশ-ফ্রি MovieBox স্টাইল বটম পপ-আপ)
        // =========================================================================
        ModalBottomSheet(
            onDismissRequest = {
                if (!isDownloading) onDismiss()
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // ১. হেডার: New Version
                Text(
                    text = "New Version",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ২. ক্র্যাশ-ফ্রি অ্যাপ আইকন + নাম + ভার্সন
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        // 🛡️ নিরাপদ ইমেজ লোডার (ক্র্যাশ বন্ধ করবে)
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.mipmap.ic_launcher)
                                .crossfade(true)
                                .build(),
                            contentDescription = "App Icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "PlayDramaFlix",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version: ${updateInfo.latestVersion}",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ৩. Details সেকশন
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Details",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val releaseNotes = if (!updateInfo.changelog.isNullOrEmpty()) {
                        "We've released a new version\n" + updateInfo.changelog.joinToString("\n") { "-$it" }
                    } else {
                        updateInfo.displayMessage.ifBlank {
                            "We've released a new version\n-Fixed some bugs and improved stability."
                        }
                    }

                    Text(
                        text = releaseNotes,
                        color = Color(0xFF333333),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    DownloadProgressBarSection(downloadState = downloadState, progressColor = PrimaryGreen)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ৪. অ্যাকশন বাটন রো: [ Later ]  ────────  [ Update ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Later",
                        color = Color(0xFF9E9E9E),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isDownloading) { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Text(
                        text = if (isDownloading) "Downloading..." else "Update",
                        color = PrimaryGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isDownloading) {
                                val apkUrl = updateInfo.targetDownloadUrl
                                if (apkUrl.isNotBlank()) {
                                    InAppUpdateManager.startInAppDownload(
                                        context = context,
                                        downloadUrl = apkUrl,
                                        targetVersion = updateInfo.latestVersion
                                    )
                                } else {
                                    Toast.makeText(context, "No download URL available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 📥 ডাউনলোড প্রোগ্রেস বার কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
private fun DownloadProgressBarSection(
    downloadState: UpdateDownloadState,
    progressColor: Color
) {
    when (downloadState) {
        is UpdateDownloadState.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { downloadState.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = Color(0xFFE0E0E0)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading (${String.format(Locale.US, "%.1f", downloadState.downloadedMb)} MB / ${String.format(Locale.US, "%.1f", downloadState.totalMb)} MB)",
                        color = Color(0xFF757575),
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "${downloadState.progressPercent}%",
                        color = progressColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        is UpdateDownloadState.ReadyToInstall -> {
            Text(
                text = "✓ Download complete. Opening installer...",
                color = PrimaryGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        is UpdateDownloadState.Error -> {
            Text(
                text = "Error: ${downloadState.message}",
                color = AlertRed,
                fontSize = 11.5.sp
            )
        }
        else -> {}
    }
}
