@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppVersionCheckResponse
import com.example.ui.theme.*
import com.example.util.InAppUpdateManager
import com.example.util.UpdateDownloadState

private val ActionGreen = Color(0xFF00D166)
private val CardSurfaceDark = Color(0xFF131722)
private val AlertRed = Color(0xFFFF3B30)

@Composable
fun UpdateDialog(
    updateInfo: AppVersionCheckResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by InAppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    // 🚨 ১. বাধ্যতামূলক আপডেট (Force Update) হলে ব্যাক বাটন বন্ধ থাকবে
    BackHandler(enabled = updateInfo.forceUpdate) {
        // Force update: cannot dismiss by back press
    }

    if (updateInfo.forceUpdate) {
        // =========================================================================
        // 🔒 মোড ১: বাধ্যতামূলক আপডেট (Force Update Modal - No Skip / Unclosable)
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
                    .background(Color.Black.copy(alpha = 0.92f))
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
                        DownloadProgressBarSection(downloadState = downloadState)

                        Spacer(modifier = Modifier.height(6.dp))

                        // বাধ্যতামূলক আপডেট বাটন
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
        // 📱 মোড ২: ঐচ্ছিক আপডেট (Flexible Bottom-Sheet Pop-Up)
        // =========================================================================
        ModalBottomSheet(
            onDismissRequest = {
                if (!isDownloading) onDismiss()
            },
            containerColor = CardSurfaceDark,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ActionGreen.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, ActionGreen)
                    ) {
                        Text(
                            text = "NEW VERSION v${updateInfo.latestVersion}",
                            color = ActionGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (!isDownloading) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF8E95A5)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF00E5FF), ActionGreen))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = updateInfo.displayTitle.ifBlank { "🚀 New Features Available!" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = updateInfo.displayMessage.ifBlank {
                        "We've added new fast video servers and improved streaming quality. Update now for the best experience!"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // চেঞ্জলগ তালিকা (যদি থাকে)
                if (!updateInfo.changelog.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B2030),
                        border = BorderStroke(0.8.dp, Color(0xFF2B3248))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("What's New:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            updateInfo.changelog.forEach { log ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(14.dp))
                                    Text(log, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }

                // ডাউনলোড প্রোগ্রেস বার
                DownloadProgressBarSection(downloadState = downloadState)

                // বাটনস রো: [Update Now] ও [Maybe Later]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDownloading) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2B3248)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E95A5))
                        ) {
                            Text("Later", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

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
                            .weight(if (isDownloading) 1f else 1.6f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isDownloading) "Downloading..." else "Update Now",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// 📥 ডাউনলোড প্রোগ্রেস বার কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
private fun DownloadProgressBarSection(downloadState: UpdateDownloadState) {
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
                    color = ActionGreen,
                    trackColor = Color(0xFF1E2434)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading (${String.format(Locale.US, "%.1f", downloadState.downloadedMb)} MB / ${String.format(Locale.US, "%.1f", downloadState.totalMb)} MB)",
                        color = Color(0xFF8E95A5),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${downloadState.progressPercent}%",
                        color = ActionGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        is UpdateDownloadState.ReadyToInstall -> {
            Text(
                text = "✓ Download complete. Opening installer...",
                color = ActionGreen,
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
