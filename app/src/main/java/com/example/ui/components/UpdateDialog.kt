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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.cos
import kotlin.math.sin

private val GoldColor = Color(0xFFFFD700)
private val BlueAccent = Color(0xFF2979FF)
private val CyanGlow = Color(0xFF00E5FF)
private val AlertRed = Color(0xFFFF3B30)
private val DarkCardBackground = Color(0xFF121622)
private val DarkBoxBg = Color(0xFF181E2E)

/**
 * 🌟 নীল ও গোল্ডেন কালারের চলমান অ্যানিমেটেড গ্রেডিয়েন্ট ব্রাশ
 */
@Composable
fun rememberAnimatedGlowBrush(
    colors: List<Color> = listOf(
        Color(0xFF2979FF), // নীল
        Color(0xFFFFD700), // গোল্ডেন
        Color(0xFF00E5FF), // সিয়ান নীল
        Color(0xFFFFB300), // ডিপ গোল্ড
        Color(0xFF2979FF)  // নীল
    ),
    durationMillis: Int = 3000
): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "borderTransition")
    val fraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientFraction"
    )

    val angle = (fraction * 2 * Math.PI)
    val cosVal = cos(angle).toFloat()
    val sinVal = sin(angle).toFloat()

    return Brush.linearGradient(
        colors = colors,
        start = Offset(600f * (1f - cosVal), 600f * (1f - sinVal)),
        end = Offset(600f * (1f + cosVal), 600f * (1f + sinVal))
    )
}

@Composable
fun UpdateDialog(
    updateInfo: AppVersionCheckResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by InAppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val isDownloading = downloadState is UpdateDownloadState.Downloading

    // 🌟 নীল ও গোল্ডেন সাইনিং বর্ডার ব্রাশ
    val animatedBorderBrush = rememberAnimatedGlowBrush()

    // 🚨 বাধ্যতামূলক আপডেটে ব্যাক বাটন ব্লক
    BackHandler(enabled = updateInfo.forceUpdate) { }

    if (updateInfo.forceUpdate) {
        // =========================================================================
        // 🔒 মোড ১: বাধ্যতামূলক আপডেট (Force Update)
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
                        .border(1.5.dp, animatedBorderBrush, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBackground)
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
        // 🌟 মোড ২: আধুনিক বটম আপডেট কার্ড (চিকন নীল ও গোল্ডেন অ্যানিমেটেড বর্ডার)
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
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    // 🌟 কার্ডের চারপাশে চিকন নীল-গোল্ডেন সাইনিং বর্ডার
                    .border(width = 1.3.dp, brush = animatedBorderBrush, shape = RoundedCornerShape(26.dp))
                    .background(DarkCardBackground, RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ১. টপ ব্যাজ ও ভার্সন
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BlueAccent.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, CyanGlow.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldColor, modifier = Modifier.size(13.dp))
                                Text("NEW VERSION AVAILABLE", color = CyanGlow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, GoldColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "v${updateInfo.latestVersion}",
                                color = GoldColor,
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
                                .background(Color(0xFF0A0E18)),
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
                                text = updateInfo.displayTitle.ifBlank { "PlayDramaFlix" },
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "A faster & smoother update is ready for you!",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF222A3C), thickness = 0.8.dp)

                    // ৩. চেঞ্জলগ / Details বক্স
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkBoxBg)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "What's New:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val changelogLines = if (!updateInfo.changelog.isNullOrEmpty()) {
                            updateInfo.changelog
                        } else {
                            listOf(
                                "Player speed and buffering enhancements",
                                "Smooth new in-app browser & local player fixes",
                                "Performance stability and bug fixes"
                            )
                        }

                        changelogLines.take(3).forEach { note ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        DownloadProgressBarSection(downloadState = downloadState, progressColor = CyanGlow)
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
                                .background(Brush.horizontalGradient(listOf(BlueAccent, CyanGlow)))
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
                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                Text(
                    text = "Download complete. Opening installer...",
                    color = CyanGlow,
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
