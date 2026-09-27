package com.example.ui.screens.reels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserStoryDto

// 🎨 ইনস্টাগ্রাম স্টাইল নিয়ন স্টোরি রিং গ্রেডিয়েন্ট
private val StoryRingGradient = Brush.sweepGradient(
    listOf(
        Color(0xFFFF007A), // Neon Pink
        Color(0xFFFF8A00), // Orange
        Color(0xFFFFD600), // Yellow Gold
        Color(0xFF00E676), // Green
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF007A)  // Loop Pink
    )
)

@Composable
fun StoryCarouselBar(
    currentUserAvatar: String?,
    currentUserName: String,
    stories: List<UserStoryDto>,
    onAddStoryClick: () -> Unit,
    onStoryClick: (storyIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // একই ক্রিয়েটরের একাধিক স্টোরি থাকলে একটি মাত্র গোল অবতারে গ্রুপ করা
    val groupedStories = remember(stories) {
        val uniqueList = mutableListOf<Pair<UserStoryDto, Int>>()
        val seenCreators = mutableSetOf<String>()

        stories.forEachIndexed { originalIndex, story ->
            val creatorKey = if (story.pageId != null && story.pageId > 0) "page_${story.pageId}" else "user_${story.userId}"
            if (seenCreators.add(creatorKey)) {
                uniqueList.add(Pair(story, originalIndex))
            }
        }
        uniqueList
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // =========================================================================
        // ➕ ১. প্রথম আইটেম: "Your Story" (নিজের স্টোরি আপলোড বাটন)
        // =========================================================================
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(66.dp)
                    .clickable { onAddStoryClick() }
            ) {
                Box(
                    modifier = Modifier.size(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // নিজের অবতার
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2838))
                            .border(1.dp, Color(0xFF2E384D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentUserAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUserAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "My Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentUserName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // নীল "+" অ্যাড বাটন (ডান নিচে ভাসমান)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF007AFF))
                            .border(1.5.dp, Color(0xFF0C0F15), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Story",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Text(
                    text = "Your story",
                    color = Color(0xFFCCD0DB),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // =========================================================================
        // 🌟 ২. অন্যান্য ক্রিয়েটরদের স্টোরি (রঙিন নিয়ন রিং সহ)
        // =========================================================================
        itemsIndexed(groupedStories, key = { _, pair -> pair.first.id }) { _, pair ->
            val story = pair.first
            val originalIndex = pair.second

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(66.dp)
                    .clickable { onStoryClick(originalIndex) }
            ) {
                // রঙিন গ্রেডিয়েন্ট বর্ডার সহ অবতার
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(width = 2.2.dp, brush = StoryRingGradient, shape = CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF1E2838)),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUrl = story.userAvatar?.takeIf { it.isNotBlank() }
                            ?: "https://ui-avatars.com/api/?name=${story.displayName}&background=00E676&color=000&bold=true"

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = story.displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // ক্রিয়েটরের নাম
                Text(
                    text = story.displayName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
