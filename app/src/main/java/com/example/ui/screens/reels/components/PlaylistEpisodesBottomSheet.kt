@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserReelDto

private val DarkCardBg = Color(0xFF141722)
private val BorderStrokeColor = Color(0xFF222B3D)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 📺 সিরিজের সমস্ত পর্বের তালিকা প্রদর্শনের জন্য আধুনিক বটম শীট ড্রয়ার
 */
@Composable
fun PlaylistEpisodesBottomSheet(
    seriesTitle: String,
    currentReelId: Int,
    episodes: List<UserReelDto>,
    isLoading: Boolean,
    onEpisodeClick: (UserReelDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ড্র্যাগ হ্যান্ডেল বার
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333C4D))
                )
            }

            // হেডার রো
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📺 $seriesTitle",
                        color = Color.White,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${episodes.size} Episodes available",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted
                    )
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp)
                }
            } else if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No episodes found in this series.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(episodes, key = { index, ep -> "ep_${ep.id}_$index" }) { index, ep ->
                        val isCurrentPlaying = (ep.id == currentReelId)
                        val epNumber = ep.episodeNum

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrentPlaying) Color(0xFF132A38) else Color(0xFF19202E),
                            border = BorderStroke(
                                width = if (isCurrentPlaying) 1.2.dp else 0.8.dp,
                                color = if (isCurrentPlaying) CyanAccent else BorderStrokeColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEpisodeClick(ep)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // থাম্বনেল ও প্লে ইন্ডিকেটর
                                Box(
                                    modifier = Modifier
                                        .size(width = 50.dp, height = 65.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(ep.thumbUrl?.takeIf { it.isNotBlank() } ?: ep.videoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    if (isCurrentPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = "Playing",
                                                tint = CyanAccent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                // পর্বের তথ্য
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isCurrentPlaying) CyanAccent else Color(0xFF263346)
                                        ) {
                                            Text(
                                                text = "EP $epNumber",
                                                color = if (isCurrentPlaying) Color.Black else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = ep.title?.ifBlank { "Episode $epNumber" } ?: "Episode $epNumber",
                                            color = if (isCurrentPlaying) CyanAccent else Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "${ep.formattedViews} views • ${ep.formattedDuration}",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
