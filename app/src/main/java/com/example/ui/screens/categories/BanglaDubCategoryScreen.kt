package com.example.ui.screens.categories

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto

@Composable
fun BanglaDubCategoryScreen(
    items: List<ContentItemDto>,
    statusBarTop: Dp,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = statusBarTop + 94.dp, bottom = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Bangla dubbed dramas found",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), // 👈 চাইলে কলাম সংখ্যা পরিবর্তন করতে পারেন
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + 94.dp,
                bottom = 72.dp,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            items(items, key = { it.slug }) { drama ->
                BanglaDubDramaCard(
                    drama = drama,
                    onClick = { onNavigateToPlayer(drama.slug) }
                )
            }
        }
    }
}

// =========================================================================
// 🇧🇩 Bangla Dub পেজের সম্পূর্ণ নিজস্ব কার্ড ডিজাইন
// =========================================================================
@Composable
fun BanglaDubDramaCard(
    drama: ContentItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // বাংলা ডাবের জন্য প্রিমিয়াম গোল্ডেন-অ্যাম্বার শিমার
    val infiniteTransition = rememberInfiniteTransition(label = "banglaCardShine")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shineBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x33FFFFFF),
            Color(0xFFFFB300).copy(alpha = 0.85f), // Gold Amber
            Color(0xFF10B981).copy(alpha = 0.75f), // Emerald Green
            Color(0x33FFFFFF)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 350f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    brush = shineBorderBrush,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color(0xFF1E2430))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(drama.posterUrl ?: drama.bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 🇧🇩 বাংলা ডাব প্রিমিয়াম ব্যাজ
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
                        )
                    )
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "বাংলা",
                    color = Color.Black,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // এপিসোড সংখ্যা
            val epCount = if (drama.totalEpisodes > 0) "${drama.totalEpisodes} Episodes" else "Full HD"
            Text(
                text = epCount,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = drama.title,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
