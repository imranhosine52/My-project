package com.example.ui.screens.shorts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContentItemDto
import com.example.ui.screens.SleekOnlineTimeline
import java.util.Locale

// 🎵 Dubbing Audio Music Note Vector Icon
@Composable
fun ShortsMusicNoteDubbingIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00E5FF)
) {
    Canvas(modifier = modifier.size(13.dp)) {
        val w = size.width
        val h = size.height

        val headRadius = w * 0.22f
        val headCenter = Offset(w * 0.30f, h * 0.72f)
        drawCircle(color = tint, radius = headRadius, center = headCenter)

        val stemStroke = 1.6.dp.toPx()
        val stemX = headCenter.x + headRadius * 0.75f
        drawLine(
            color = tint,
            start = Offset(stemX, headCenter.y),
            end = Offset(stemX, h * 0.16f),
            strokeWidth = stemStroke,
            cap = StrokeCap.Round
        )

        val flagPath = Path().apply {
            moveTo(stemX, h * 0.16f)
            cubicTo(
                w * 0.72f, h * 0.16f,
                w * 0.82f, h * 0.30f,
                w * 0.72f, h * 0.46f
            )
            lineTo(stemX, h * 0.38f)
            close()
        }
        drawPath(flagPath, color = tint)
    }
}

// =============================================================================
// 🎬 1. Floating Video Frame Overlay (Poster + Title + Description + Timeline)
// =============================================================================
@Composable
fun ShortsVideoFloatingOverlay(
    content: ContentItemDto,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isUserSeeking: Boolean,
    seekPosition: Long,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onOpenIntroductionTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.65f)
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Title & Poster
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onOpenIntroductionTab() }
        ) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = content.posterUrl ?: content.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = content.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }

        // Description & More
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clickable { onOpenIntroductionTab() }
        ) {
            Text(
                text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                color = Color(0xFFD1D5DB),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "More ⌵",
                color = Color(0xFFE5E7EB),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Blue Timeline Bar
        SleekOnlineTimeline(
            currentPositionMs = if (isUserSeeking) seekPosition else currentPositionMs,
            totalDurationMs = totalDurationMs,
            onSeekStarted = onSeekStarted,
            onSeeking = onSeeking,
            onSeekFinished = onSeekFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

// =============================================================================
// ⬛ 2. Solid Black Bottom Bar (Episode, Speed, Quality, Multi-Audio & Subtitles)
// =============================================================================
@Composable
fun ShortsSolidBlackBottomBar(
    currentEpNum: Int,
    totalEpCount: Int,
    currentSpeedText: String = "1x",
    currentQualityText: String = "720P",
    currentAudioText: String = "Hindi",
    currentSubtitleText: String = "CC",
    hasMultipleAudio: Boolean = false,
    hasSubtitles: Boolean = false,
    isSubtitleActive: Boolean = false,
    onOpenEpisodesTab: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val padCurrent = String.format(Locale.US, "%02d", currentEpNum)
    val padTotal = String.format(Locale.US, "%02d", totalEpCount)
    val episodeDisplayText = "$padCurrent/$padTotal"

    Surface(
        color = Color.Black,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: "03/72 ⌄"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenEpisodesTab() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = episodeDisplayText,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open Episodes Drawer",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Right: [Speed] [Quality] [Audio Language] [Subtitles]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Playback Speed Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSpeedClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Speed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = currentSpeedText,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Video Quality Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onQualityClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 0.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HD",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 9.sp
                        )
                    }

                    Text(
                        text = currentQualityText.replace("p", "P", ignoreCase = true),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Multi-Audio Language Button (Visible when multiple audio tracks exist)
                if (hasMultipleAudio) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAudioClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        ShortsMusicNoteDubbingIcon(tint = Color(0xFF00E5FF))
                        Text(
                            text = currentAudioText,
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4. Closed Caption / Subtitle Button (Visible when subtitles exist)
                if (hasSubtitles) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSubtitleClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSubtitleText,
                            color = if (isSubtitleActive) Color(0xFF00E5FF) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
