@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ContentItemDto
import com.example.ui.theme.GoldVip
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.util.Locale

// =============================================================================
// 🎭 ইমোজি মেটাডাটা ও টেলিগ্রাম ম্যাপিং
// =============================================================================
data class ReactionMeta(
    val key: String,
    val emoji: String
)

// ছবি ১: কুইক বারের প্রধান ৬টি ইমোজি
val quickReactionsList = listOf(
    ReactionMeta("heart", "❤️"),
    ReactionMeta("like", "👍"),
    ReactionMeta("fire", "🔥"),
    ReactionMeta("love_face", "🥰"),
    ReactionMeta("clap", "👏"),
    ReactionMeta("laugh", "😁")
)

// ছবি ২: টেলিগ্রামের সম্পূর্ণ ৬৪টি ইমোজি গ্রিড
val fullPickerReactionsList = listOf(
    // Row 1
    ReactionMeta("heart", "❤️"),
    ReactionMeta("like", "👍"),
    ReactionMeta("dislike", "👎"),
    ReactionMeta("fire", "🔥"),
    ReactionMeta("love_face", "🥰"),
    ReactionMeta("clap", "👏"),
    ReactionMeta("laugh", "😁"),
    ReactionMeta("thinking", "🤔"),
    // Row 2
    ReactionMeta("mind_blown", "🤯"),
    ReactionMeta("scream", "😱"),
    ReactionMeta("swearing", "🤬"),
    ReactionMeta("cry_sad", "😢"),
    ReactionMeta("party", "🎉"),
    ReactionMeta("star_eyes", "🤩"),
    ReactionMeta("vomit", "🤮"),
    ReactionMeta("poop", "💩"),
    // Row 3
    ReactionMeta("pray", "🙏"),
    ReactionMeta("ok_hand", "👌"),
    ReactionMeta("dove", "🕊️"),
    ReactionMeta("clown", "🤡"),
    ReactionMeta("yawn", "🥱"),
    ReactionMeta("smirk", "😏"),
    ReactionMeta("heart_eyes", "😍"),
    ReactionMeta("whale", "🐳"),
    // Row 4
    ReactionMeta("heart_fire", "❤️‍🔥"),
    ReactionMeta("moon", "🌚"),
    ReactionMeta("hotdog", "🌭"),
    ReactionMeta("hundred", "💯"),
    ReactionMeta("rofl", "🤣"),
    ReactionMeta("lightning", "⚡"),
    ReactionMeta("banana", "🍌"),
    ReactionMeta("trophy", "🏆"),
    // Row 5
    ReactionMeta("broken_heart", "💔"),
    ReactionMeta("raised_eyebrow", "🤨"),
    ReactionMeta("neutral", "😐"),
    ReactionMeta("strawberry", "🍓"),
    ReactionMeta("champagne", "🍾"),
    ReactionMeta("kiss", "💋"),
    ReactionMeta("middle_finger", "🖕"),
    ReactionMeta("devil", "😈"),
    // Row 6
    ReactionMeta("sleepy", "😴"),
    ReactionMeta("cry", "😭"),
    ReactionMeta("nerd", "🤓"),
    ReactionMeta("ghost", "👻"),
    ReactionMeta("developer", "👩‍💻"),
    ReactionMeta("eyes", "👀"),
    ReactionMeta("pumpkin", "🎃"),
    ReactionMeta("see_no_evil", "🙈"),
    // Row 7
    ReactionMeta("angel", "😇"),
    ReactionMeta("fearful", "😨"),
    ReactionMeta("handshake", "🤝"),
    ReactionMeta("writing", "✍️"),
    ReactionMeta("hug", "🤗"),
    ReactionMeta("salute", "🫡"),
    ReactionMeta("santa", "🎅"),
    ReactionMeta("tree", "🎄"),
    // Row 8
    ReactionMeta("snowman", "☃️"),
    ReactionMeta("nails", "💅"),
    ReactionMeta("zany", "🤪"),
    ReactionMeta("moyai", "🗿"),
    ReactionMeta("cool", "🆒"),
    ReactionMeta("cupid", "💘"),
    ReactionMeta("hear_no_evil", "🙉"),
    ReactionMeta("unicorn", "🦄")
)

fun getEmojiSymbolForKey(key: String): String {
    return fullPickerReactionsList.find { it.key.equals(key, ignoreCase = true) }?.emoji
        ?: when (key.lowercase()) {
            "heart" -> "❤️"
            "strawberry" -> "🍓"
            "like" -> "👍"
            "fire" -> "🔥"
            "laugh" -> "😁"
            "cry" -> "😭"
            "love_face" -> "🥰"
            "hug" -> "🤗"
            "giggle" -> "🤭"
            "blush" -> "☺️"
            "star_eyes" -> "🤩"
            "angry" -> "😡"
            "tired" -> "😫"
            "sweat" -> "😓"
            "devil" -> "😈"
            "clap" -> "👏"
            "vomit" -> "🤮"
            "clown" -> "🤡"
            "cool" -> "😎"
            else -> key
        }
}

@Composable
fun PlayerHeaderSection(
    content: ContentItemDto,
    shortTitle: String,
    viewsCount: Long,
    likesCount: Long,
    isLiked: Boolean,
    isInWatchlist: Boolean,
    isDescriptionExpanded: Boolean,
    myReaction: String? = null,
    reactions: Map<String, Int> = emptyMap(),
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleDescription: () -> Unit,
    onLikeClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onToggleReaction: (String) -> Unit = {},
    onSwipeDownFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pullDownOffsetY = remember { Animatable(0f) }

    var showQuickReactionBubble by remember { mutableStateOf(false) }
    var showFullTelegramGridDialog by remember { mutableStateOf(false) }

    // সক্রিয় রিঅ্যাকশনগুলো বের করা (যেগুলোর সংখ্যা ০-এর বেশি)
    val activeReactions = remember(reactions) {
        reactions.filter { it.value > 0 }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = pullDownOffsetY.value }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (pullDownOffsetY.value > 30f) {
                                onSwipeDownFullscreen()
                            }
                            pullDownOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            pullDownOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0 || pullDownOffsetY.value > 0) {
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (pullDownOffsetY.value + dragAmount * 0.45f).coerceIn(0f, 50f)
                                pullDownOffsetY.snapTo(newOffset)
                            }
                        }
                    }
                )
            }
    ) {
        // ১. টাইটেল ও Pre / Next বাটন
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shortTitle,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161A23),
                    border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                    modifier = Modifier
                        .size(width = 54.dp, height = 30.dp)
                        .clickable { onPreviousClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Pre",
                            color = Color(0xFFB0B7C6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161A23),
                    border = BorderStroke(0.8.dp, Color(0xFF2B3346)),
                    modifier = Modifier
                        .size(width = 54.dp, height = 30.dp)
                        .clickable { onNextClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Next",
                            color = Color(0xFFB0B7C6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ২. মেটাডাটা ও অ্যাকশন আইকন (সাল, রেটিং, ভিউ, লাভ, বুকমার্ক)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = content.releaseYear.ifBlank { "2026" },
                    color = Color(0xFF8E95A5),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(13.dp))
                Text(
                    text = if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.9",
                    color = GoldVip,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Text(
                    text = if (isDescriptionExpanded) "less" else "...more",
                    color = TealAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleDescription() }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ভিউজ কাউন্টার
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Views", tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                    Text(formatCount(viewsCount), color = Color(0xFFCCD0DB), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // লাইক বাটন (ট্যাপে লাইক, পাশে রিঅ্যাক্ট অপশন)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatCount(likesCount),
                        color = if (isLiked) Color(0xFFFF4B72) else Color(0xFFADB3C2),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // বুকমার্ক আইকন
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isInWatchlist) TealAccent else Color(0xFFADB3C2),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onWatchlistClick() }
                )
            }
        }

        // =========================================================================
        // 💬 ৩. কুইক রিঅ্যাকশন বাবল (আপনার ছবি ১: ❤️ 👍 🔥 🥰 👏 😁 ⌄)
        // =========================================================================
        AnimatedVisibility(
            visible = showQuickReactionBubble,
            enter = scaleIn(initialScale = 0.85f, animationSpec = tween(180)) + fadeIn(),
            exit = scaleOut(targetScale = 0.85f, animationSpec = tween(150)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E2536),
                    border = BorderStroke(1.dp, Color(0xFF2E384D)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickReactionsList.forEach { item ->
                            Text(
                                text = item.emoji,
                                fontSize = 21.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onToggleReaction(item.key)
                                        showQuickReactionBubble = false
                                    }
                                    .padding(4.dp)
                            )
                        }

                        // ⌄ ড্রপডাউন অ্যারো বাটন (ফুল গ্রিড খুলবে)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF283448))
                                .clickable {
                                    showQuickReactionBubble = false
                                    showFullTelegramGridDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "More Reactions",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🌟 ৪. টেলিগ্রাম স্টাইল লাইভ রিঅ্যাকশন পিল রো (❤️ 54, 🍓 3... + ➕)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ➕ রিঅ্যাকশন খোলার বাটন
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = BorderStroke(0.8.dp, Color(0xFF2B3648)),
                modifier = Modifier
                    .height(28.dp)
                    .clickable { showQuickReactionBubble = !showQuickReactionBubble }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddReaction,
                        contentDescription = "Add Reaction",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(15.dp)
                    )
                    Text("React", color = Color(0xFF00E5FF), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // লাইভ পিলস তালিকা
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                items(activeReactions.toList()) { (key, count) ->
                    val isMyChoice = (myReaction.equals(key, ignoreCase = true))
                    val emojiChar = getEmojiSymbolForKey(key)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMyChoice) Color(0xFF163244) else Color(0xFF151922),
                        border = BorderStroke(
                            width = if (isMyChoice) 1.2.dp else 0.8.dp,
                            color = if (isMyChoice) Color(0xFF00E5FF) else Color(0xFF263244)
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .clickable { onToggleReaction(key) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = emojiChar, fontSize = 13.5.sp)
                            Text(
                                text = count.toString(),
                                color = if (isMyChoice) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ৩. ডেসক্রিপশন
        if (isDescriptionExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = content.description?.takeIf { it.isNotBlank() } ?: content.synopsis,
                    color = Color(0xFFCCD0DB),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }

    // =========================================================================
    // 🧸 ৫. টেলিগ্রাম ফুল ইমোজি গ্রিড ডায়ালগ (আপনার ছবি ২: ৬৪টি ইমোজি)
    // =========================================================================
    if (showFullTelegramGridDialog) {
        Dialog(onDismissRequest = { showFullTelegramGridDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2536),
                border = BorderStroke(1.dp, Color(0xFF2E384D)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reactions",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { showFullTelegramGridDialog = false }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF2B3548), thickness = 0.8.dp)

                    // ৮-কলামের টেলিগ্রাম গ্রিড
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(fullPickerReactionsList) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onToggleReaction(item.key)
                                        showFullTelegramGridDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = item.emoji, fontSize = 21.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> "0"
    }
}
