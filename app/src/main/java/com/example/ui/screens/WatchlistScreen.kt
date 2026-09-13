@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 🌟 প্রিমিয়াম ব্লু-গ্রিন প্লে বাটন গ্রেডিয়েন্ট
private val BlueGreenPlayBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF), // Electric Blue
        Color(0xFF00D166)  // Emerald Green
    )
)

private val PureBlackBg = Color(0xFF06080E)
private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2536)

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
            .background(PureBlackBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // =============================================================
            // 🔝 ১. এজ-টু-এজ ফুলস্ক্রিন হেডার (নোটিফিকেশন প্যানেলের নিচ দিয়ে শুরু)
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161B28),
                                Color(0xFF0E121B),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1C2232)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color(0xFF00D166),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "My List",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1A2130),
                        border = BorderStroke(0.8.dp, Color(0xFF2C364C))
                    ) {
                        Text(
                            text = "${watchlistState.savedDramas.size} Saved",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // =============================================================
            // 🔄 ২. পুল-টু-রিফ্রেশ ও ড্রামা লিস্ট
            // =============================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadHomeContent()
                        delay(500)
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
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF121622)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "Your list is empty",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Explore Asian dramas and tap 'Bookmark / Add list' to save them here for quick access.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 86.dp, start = 14.dp, end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchlistState.savedDramas, key = { it.id }) { drama ->
                            WatchlistDramaCard(
                                drama = drama,
                                onPlayClick = { onNavigateToPlayer(drama.slug) },
                                onShareClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Watch ${drama.title} on PlayDramaFlix: https://playdramaflix.com/watch/${drama.slug}"
                                        )
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

// =============================================================
// 🎬 ৩. আধুনিক ড্রামা কার্ড (Blue-Green Play বাটন ও পিওর হোয়াইট টেক্সট)
// =============================================================
@Composable
private fun WatchlistDramaCard(
    drama: ContentItemDto,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val isHindi = drama.isHindiDub || drama.dubBadge.contains("Hindi", ignoreCase = true)
    val dubBadgeColor = if (isHindi) Color(0xFF007AFF) else Color(0xFFFFB300)

    val categoriesText = drama.categories.take(2).joinToString(" • ").ifBlank { drama.type.replaceFirstChar { it.uppercase() } }
    val metaInfo = "📺 ${drama.releaseYear} • $categoriesText • ${drama.country}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CardBorderColor, RoundedCornerShape(16.dp))
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🖼️ পোস্টার বক্স (বর্ডার ও ব্যাজ সহ)
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .height(116.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF171B26))
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

                // হালকা ডার্ক ওভারলে
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // ডাবিং ব্যাজ (উপরে ডানে)
                Surface(
                    shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 6.dp),
                    color = dubBadgeColor,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (isHindi) "Hindi" else "Bangla",
                        color = if (isHindi) Color.White else Color.Black,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // রেটিং ব্যাজ (নিচে ডানে)
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                        Text(
                            text = if (drama.rating > 0) drama.rating.toString() else "8.5",
                            color = Color(0xFFFFB300),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 📝 টাইটেল, মেটাডাটা ও অ্যাকশন বাটন
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // শিরোনাম (উজ্জ্বল সাদা)
                Text(
                    text = drama.title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                // মেটাডাটা লাইন
                Text(
                    text = metaInfo,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // বাটন রো: [ ▶ Play ] (Blue-Green Gradient)  [ ↗ Share ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 🌟 নীল ও সবুজ গ্রেডিয়েন্টের [▶ Play] বাটন
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(BlueGreenPlayBrush)
                            .clickable { onPlayClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Play",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ↗ Share বাটন (ডার্ক গ্লাস লুক)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF19202E),
                        border = BorderStroke(0.8.dp, Color(0xFF2C374D)),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clickable { onShareClick() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Share",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
