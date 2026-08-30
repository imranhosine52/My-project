@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00D166)
private val CardDarkBg = Color(0xFF10141E)

@Composable
fun WatchlistScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()

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
            // -------------------------------------------------------------
            // Top Bar Header: My list
            // -------------------------------------------------------------
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "My list",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // -------------------------------------------------------------
            // 🔄 Chrome-Style Pull-To-Refresh Box
            // -------------------------------------------------------------
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadHomeContent()
                        delay(600)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (watchlistState.savedDramas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "Your list is empty",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Browse Asian dramas and tap Add to Watchlist to save them here.",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp, start = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Date Sub-header
                        item {
                            Text(
                                text = "Saved Dramas (${watchlistState.savedDramas.size})",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }

                        // My List Drama Cards
                        items(watchlistState.savedDramas, key = { it.id }) { drama ->
                            WatchlistDramaCard(
                                drama = drama,
                                onPlayClick = { onNavigateToPlayer(drama.slug) },
                                onShareClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Watch ${drama.title} on PlayDramaFlix: https://playdramaflix.com/watch/${drama.slug}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share drama"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🎬 Watchlist Drama Row Card (Screenshot Style)
// -------------------------------------------------------------
@Composable
private fun WatchlistDramaCard(
    drama: ContentItemDto,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", ignoreCase = true)
    val dubBadgeColor = if (isHindi) Color(0xFF1E88E5) else Color(0xFFFFB300)

    val categoriesText = drama.categories.take(2).joinToString(" • ").ifBlank { drama.type.replaceFirstChar { it.uppercase() } }
    val metaInfo = "📺 ${drama.releaseYear} • $categoriesText • ${drama.country}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDarkBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Poster Thumbnail with Badges
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(106.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .clickable { onPlayClick() }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Top-Right Dubbing Badge
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 5.dp),
                color = dubBadgeColor,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (isHindi) "Hindi" else "Bangla",
                    color = if (isHindi) Color.White else Color.Black,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                )
            }

            // Bottom-Right Rating Badge
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = if (drama.rating > 0) drama.rating.toString() else "8.5",
                    color = Color(0xFFFFC107),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Right Column: Title, Metadata & Actions (Play + Share)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title
            Text(
                text = drama.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata Line
            Text(
                text = metaInfo,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons Row: [▶ Play]  [↗ Share]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ▶ Play Button (Green)
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
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

                // ↗ Share Button (Dark)
                Button(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                    border = BorderStroke(1.dp, BorderDark),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Share",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
