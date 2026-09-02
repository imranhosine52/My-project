@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AppVersionCheckResponse
import com.example.util.InAppUpdateManager
import com.example.util.UpdateDownloadState
import java.util.Locale

private val EmeraldGreen = Color(0xFF00E676)
private val CyanGlow = Color(0xFF00E5FF)
private val AlertRed = Color(0xFFFF3B30)
private val DarkCardSurface = Color(0xFF101420)
private val DarkBorderBase = Color(0xFF1E2536)

/**
 * 💫 কার্ডের চারপাশে চলমান গ্রেডিয়েন্ট বর্ডার তৈরি করার কাস্টম মডিফায়ার
 */
@Composable
fun Modifier.animatedGlowBorder(
    borderWidth: androidx.compose.ui.unit.Dp = 1.2.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    colors: List<Color> = listOf(CyanGlow, EmeraldGreen, Color(0xFF7C4DFF), CyanGlow),
    durationMillis: Int = 3500
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val degrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            rotate(degrees) {
                drawCircle(
                    brush = Brush.sweepGradient(colors),
                    radius = size.maxDimension,
                    blendMode = BlendMode.SrcIn
                )
            }
        }
        .border(borderWidth, Brush.sweepGradient(colors), shape)
}

@Composable
fun UpdateDialog(
    updateInfo: AppVersionCheckResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by InAppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    // 🚨 বাধ্যতামূলক আপডেটে ব্যাক বাটন ব্লক
    BackHandler(enabled = updateInfo.forceUpdate) { }

    if (updateInfo.forceUpdate) {
        // =========================================================================
        // 🔒 মোড ১: বাধ্যতামূলক আপডেট (Force Update - প্রিমিয়াম রেড অ্যালার্ট)
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
                        .wrapContentHeight()
                        .animatedGlowBorder(
                            borderWidth = 1.5.dp,
                            shape = RoundedCornerShape(24.dp),
                            colors = listOf(AlertRed, Color(0xFFFF9100), Color(0xFFFF1744), AlertRed)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
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
                            color = AlertRed.copy(alpha = 0.18f),
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
                                .background(Brush.linearGradient(listOf(AlertRed, Color(0xFFFF9100)))),
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

                        DownloadProgressBarSection(downloadState = downloadState, progressColor = AlertRed)

                        Spacer(modifier = Modifier.height(4.dp))

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
        // 🌟 মোড ২: আধুনিক ও আকর্ষণীয় বটম আপডেট কার্ড (অ্যানিমেটেড বর্ডার সহ)
        // =========================================================================
        ModalBottomSheet(
            onDismissRequest = {
                if (!isDownloading) onDismiss()
            },
            containerColor = Color.Transparent,
            dragHandle = null,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
                    .animatedGlowBorder(
                        borderWidth = 1.3.dp,
                        shape = RoundedCornerShape(26.dp),
                        colors = listOf(CyanGlow, EmeraldGreen, Color(0xFF9C27B0), CyanGlow)
                    )
                    .background(DarkCardSurface, RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ১. টপ ব্যাজ ও হেডার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyanGlow.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, CyanGlow.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(13.dp))
                                Text("NEW VERSION AVAILABLE", color = CyanGlow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "v${updateInfo.latestVersion}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // ২. অ্যাপ আইকন + অ্যাপ নাম
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
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

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = updateInfo.displayTitle.ifBlank { "PlayDramaFlix Update" },
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "A smoother & faster experience is waiting for you!",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = DarkBorderBase, thickness = 0.8.dp)

                    // ৩. চেঞ্জলগ / Details বক্স
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF161C2C))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "What's New in this update:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val changelogLines = if (!updateInfo.changelog.isNullOrEmpty()) {
                            updateInfo.changelog
                        } else {
                            listOf(
                                "Player speed and buffering improvements",
                                "Performance enhancements & bug fixes",
                                "Smooth new in-app browser & UI updates"
                            )
                        }

                        changelogLines.take(3).forEach { note ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", color = EmeraldGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = note.removePrefix("-").trim(),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // ডাউনলোড প্রোগ্রেস বার
                    if (isDownloading) {
                        DownloadProgressBarSection(downloadState = downloadState, progressColor = EmeraldGreen)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ৪. অ্যাকশন বাটনসমূহ: [ Later ]   [ ⚡ Update Now ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (!isDownloading) onDismiss() },
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Later", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
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
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            modifier = Modifier
                                .weight(1.6f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(EmeraldGreen, Color(0xFF00B0FF))))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDownloading) Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color(0xFF0A101D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isDownloading) "Downloading..." else "Update Now",
                                    color = Color(0xFF0A101D),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
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
                    .padding(vertical = 4.dp),
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
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading (${String.format(Locale.US, "%.1f", downloadState.downloadedMb)} MB / ${String.format(Locale.US, "%.1f", downloadState.totalMb)} MB)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${downloadState.progressPercent}%",
                        color = progressColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        is UpdateDownloadState.ReadyToInstall -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "Download complete. Opening installer...",
                    color = EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        is UpdateDownloadState.Error -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                Text(
                    text = "Download failed: ${downloadState.message}",
                    color = AlertRed,
                    fontSize = 11.5.sp
                )
            }
        }
        else -> {}
    }
}
