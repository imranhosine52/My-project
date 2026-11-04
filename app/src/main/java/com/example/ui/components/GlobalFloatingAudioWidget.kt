package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocalVideoItem
import com.example.util.LocalAudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val WidgetBarBg = Color(0xF20B130E)
private val WidgetBorder = Color(0xFF1E3224)
private val TabBg = Color(0xF20D1711)

@Composable
fun GlobalFloatingAudioWidget(
    onOpenFullPlayer: (LocalVideoItem) -> Unit,
    hasBottomNav: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val currentTrack by LocalAudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by LocalAudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val isVisible by LocalAudioPlayerManager.isMiniPlayerVisible.collectAsStateWithLifecycle()

    if (!isVisible || currentTrack == null) return

    val track = currentTrack!!

    // ৮ সেকেন্ড টাইমার ও স্টেট
    var isExpanded by remember { mutableStateOf(true) }
    var userInteractionCount by remember { mutableLongStateOf(0L) }

    // অ্যালবাম কভার লোড করা
    var coverBitmap by remember(track.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track.id, track.path) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (track.path.isNotBlank() && File(track.path).exists()) {
                    retriever.setDataSource(track.path)
                } else {
                    retriever.setDataSource(context, Uri.parse(track.contentUriString))
                }
                val raw = retriever.embeddedPicture
                retriever.release()
                if (raw != null) {
                    coverBitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                }
            } catch (_: Exception) {}
        }
    }

    // ⏱️ ৮ সেকেন্ড পর স্বয়ংক্রিয়ভাবে সাইডে ছোট হয়ে যাওয়া
    LaunchedEffect(isExpanded, userInteractionCount, isPlaying) {
        if (isExpanded) {
            delay(8000L)
            isExpanded = false
        }
    }

    // স্ক্রিনের সেফ ড্র্যাগ পজিশন লিমিট
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val bottomNavHeightPx = with(density) { (if (hasBottomNav) 80.dp else 24.dp).toPx() }
    val topSafeLimitPx = with(density) { 100.dp.toPx() }

    var offsetY by remember { mutableFloatStateOf(screenHeightPx - bottomNavHeightPx - with(density) { 60.dp.toPx() }) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        userInteractionCount++
                        val newY = offsetY + dragAmount.y
                        offsetY = newY.coerceIn(topSafeLimitPx, screenHeightPx - bottomNavHeightPx)
                    }
                }
                .align(Alignment.TopStart)
        ) {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                label = "mini_player_state"
            ) { expanded ->
                if (expanded) {
                    // =========================================================
                    // 🎵 ২ নম্বর ছবির মতো পূর্ণাঙ্গ ভাসমান মিনি-বার
                    // =========================================================
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WidgetBarBg,
                        border = BorderStroke(0.8.dp, WidgetBorder),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .padding(horizontal = 12.dp)
                            .height(52.dp)
                            .clickable {
                                userInteractionCount++
                                onOpenFullPlayer(track)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // অ্যালবাম আর্ট
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2822)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverBitmap != null) {
                                    Image(
                                        bitmap = coverBitmap!!.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // গানের নাম
                            Text(
                                text = track.displayName.ifBlank { track.title },
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // প্লে/পজ বাটন
                            IconButton(
                                onClick = {
                                    userInteractionCount++
                                    LocalAudioPlayerManager.togglePlayPause()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // ক্লোজ বাটন (✕)
                            IconButton(
                                onClick = {
                                    LocalAudioPlayerManager.stopAndDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF8E95A5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // =========================================================
                    // 🌟 ৩ নম্বর ছবির মতো স্ক্রিনের সাইডে ডক হওয়া 🎵 ট্যাব
                    // =========================================================
                    Surface(
                        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                        color = TabBg,
                        border = BorderStroke(1.dp, Color(0xFF223628)),
                        shadowElevation = 8.dp,
                        onClick = {
                            userInteractionCount++
                            isExpanded = true
                        },
                        modifier = Modifier
                            .width(38.dp)
                            .height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Expand Player",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
