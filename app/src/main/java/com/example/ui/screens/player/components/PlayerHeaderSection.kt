@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.player.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ContentItemDto
import com.example.ui.theme.GoldVip
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.util.Locale

// =============================================================================
// 🎭 ইমোজি মেটাডাটা
// =============================================================================
data class ReactionMeta(
    val key: String,
    val emoji: String
)

// ১ নম্বর ছবির শীর্ষের ৭টি কুইক ইমোজি
val quickReactionsList = listOf(
    ReactionMeta("heart", "❤️"),
    ReactionMeta("like", "👍"),
    ReactionMeta("dislike", "👎"),
    ReactionMeta("fire", "🔥"),
    ReactionMeta("love_face", "🥰"),
    ReactionMeta("clap", "👏"),
    ReactionMeta("laugh", "😁")
)

// টেলিগ্রামের ফুল ৬৪টি ইমোজি গ্রিড
val fullPickerReactionsList = listOf(
    ReactionMeta("heart", "❤️"), ReactionMeta("like", "👍"), ReactionMeta("dislike", "👎"),
    ReactionMeta("fire", "🔥"), ReactionMeta("love_face", "🥰"), ReactionMeta("clap", "👏"),
    ReactionMeta("laugh", "😁"), ReactionMeta("thinking", "🤔"), ReactionMeta("mind_blown", "🤯"),
    ReactionMeta("scream", "😱"), ReactionMeta("swearing", "🤬"), ReactionMeta("cry_sad", "😢"),
    ReactionMeta("party", "🎉"), ReactionMeta("star_eyes", "🤩"), ReactionMeta("vomit", "🤮"),
    ReactionMeta("poop", "💩"), ReactionMeta("pray", "🙏"), ReactionMeta("ok_hand", "👌"),
    ReactionMeta("dove", "🕊️"), ReactionMeta("clown", "🤡"), ReactionMeta("yawn", "🥱"),
    ReactionMeta("smirk", "😏"), ReactionMeta("heart_eyes", "😍"), ReactionMeta("whale", "🐳"),
    ReactionMeta("heart_fire", "❤️‍🔥"), ReactionMeta("moon", "🌚"), ReactionMeta("hotdog", "🌭"),
    ReactionMeta("hundred", "💯"), ReactionMeta("rofl", "🤣"), ReactionMeta("lightning", "⚡"),
    ReactionMeta("banana", "🍌"), ReactionMeta("trophy", "🏆"), ReactionMeta("broken_heart", "💔"),
    ReactionMeta("raised_eyebrow", "🤨"), ReactionMeta("neutral", "😐"), ReactionMeta("strawberry", "🍓"),
    ReactionMeta("champagne", "🍾"), ReactionMeta("kiss", "💋"), ReactionMeta("middle_finger", "🖕"),
    ReactionMeta("devil", "😈"), ReactionMeta("sleepy", "😴"), ReactionMeta("cry", "😭"),
    ReactionMeta("nerd", "🤓"), ReactionMeta("ghost", "👻"), ReactionMeta("developer", "👩‍💻"),
    ReactionMeta("eyes", "👀"), ReactionMeta("pumpkin", "🎃"), ReactionMeta("see_no_evil", "🙈"),
    ReactionMeta("angel", "😇"), ReactionMeta("fearful", "😨"), ReactionMeta("handshake", "🤝"),
    ReactionMeta("writing", "✍️"), ReactionMeta("hug", "🤗"), ReactionMeta("salute", "🫡"),
    ReactionMeta("santa", "🎅"), ReactionMeta("tree", "🎄"), ReactionMeta("snowman", "☃️"),
    ReactionMeta("nails", "💅"), ReactionMeta("zany", "🤪"), ReactionMeta("moyai", "🗿"),
    ReactionMeta("cool", "🆒"), ReactionMeta("cupid", "💘"), ReactionMeta("hear_no_evil", "🙉"),
    ReactionMeta("unicorn", "🦄")
)

fun getEmojiSymbolForKey(key: String): String {
    return fullPickerReactionsList.find { it.key.equals(key, ignoreCase = true) }?.emoji
        ?: when (key.lowercase()) {
            "heart" -> "❤️"
            "like" -> "👍"
            "dislike" -> "👎"
            "fire" -> "🔥"
            "love_face" -> "🥰"
            "clap" -> "👏"
            "laugh" -> "😁"
            "strawberry" -> "🍓"
            else -> key
        }
}

private val TelegramDarkMenuBg = Color(0xFF232D3B)
private val TelegramMenuBorder = Color(0xFF323F52)
private val TelegramMenuText = Color(0xFFE4ECF5)

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
    onReplyClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onSwipeDownFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pullDownOffsetY = remember { Animatable(0f) }

    // ১ নম্বর ছবির হুবহু টেলিগ্রাম মেনু ও ফুল ইমোজি পিকারের স্টেট
    var showTelegramContextMenu by remember { mutableStateOf(false) }
    var showFullTelegramGridDialog by remember { mutableStateOf(false) }

    // যেসব ইমোজি অলরেডি দেওয়া হয়েছে
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
        // =========================================================================
        // ১. টাইটেল ও Pre / Next বাটন রো
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
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

        // =========================================================================
        // 🎯 ২. একক অল-ইন-ওয়ান সিঙ্গেল লাইন (মেটাডাটা + ভিউস + লাভ/রিঅ্যাক্ট + ইমোজি ট্যাগস)
        // কোনো অতিরিক্ত নিচের লাইন ছাড়াই সব উপাদান পাশাপাশি থাকবে
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // বাঁয়ে: সাল, রেটিং, more
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = content.releaseYear.ifBlank { "2026" },
                    color = Color(0xFF8E95A5),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Icon(Icons.Default.Star, contentDescription = null, tint = GoldVip, modifier = Modifier.size(12.dp))
                Text(
                    text = if (content.rating > 0) String.format(Locale.US, "%.1f", content.rating) else "8.5",
                    color = GoldVip,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("•", color = Color(0xFF4C5466), fontSize = 11.sp)
                Text(
                    text = if (isDescriptionExpanded) "less" else "...more",
                    color = TealAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleDescription() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🎯 ডানে: [👁️ Views] [❤️ Reaction (পপআপ ট্রিগার)] [এক্টিভ ইমোজি পিলস]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ১. ভিউজ অপশন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Views",
                        tint = Color(0xFF8E95A5),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatCount(viewsCount),
                        color = Color(0xFFCBD0DB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ২. লাভ/রিঅ্যাক্ট অপশন (এখানে ক্লিক করলে ১ নম্বর ছবির পপআপ মেনু আসবে)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showTelegramContextMenu = true } // 👈 পপআপ ওপেন
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked || myReaction != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Reactions",
                        tint = if (isLiked || myReaction != null) Color(0xFFFF2A4B) else Color(0xFFADB3C2),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatCount(likesCount),
                        color = if (isLiked || myReaction != null) Color(0xFFFF2A4B) else Color(0xFFCBD0DB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ৩. সক্রিয় রিঅ্যাকশনগুলো (একই লাইনে স্ক্রোলযোগ্য)
                if (activeReactions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = 140.dp)
                    ) {
                        items(activeReactions.toList()) { (key, count) ->
                            val isMyChoice = (myReaction.equals(key, ignoreCase = true))
                            val emojiChar = getEmojiSymbolForKey(key)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMyChoice) Color(0xFF1B3245) else Color(0xFF161A23),
                                border = BorderStroke(
                                    width = 0.6.dp,
                                    color = if (isMyChoice) Color(0xFF00E5FF) else Color(0xFF293245)
                                ),
                                modifier = Modifier
                                    .height(22.dp)
                                    .clickable { onToggleReaction(key) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(text = emojiChar, fontSize = 11.sp)
                                    Text(
                                        text = count.toString(),
                                        color = if (isMyChoice) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ডেসক্রিপশন এক্সপ্যান্ড
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
    // 🌟 ১ নম্বর ছবির হুবহু টেলিগ্রাম স্টাইল কনটেক্সট পপআপ মেনু
    // =========================================================================
    if (showTelegramContextMenu) {
        Dialog(
            onDismissRequest = { showTelegramContextMenu = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
                    .clickable { showTelegramContextMenu = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .clickable(enabled = false) {}, // ক্লিক পাস-থ্রু প্রতিরোধ
                    horizontalAlignment = Alignment.End
                ) {
                    // ১. উপরের ফ্লোটিং ইমোজি পিল বাবল
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(TelegramDarkMenuBg)
                            .border(0.8.dp, TelegramMenuBorder, RoundedCornerShape(26.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickReactionsList.forEach { item ->
                                Text(
                                    text = item.emoji,
                                    fontSize = 20.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            onToggleReaction(item.key)
                                            showTelegramContextMenu = false
                                        }
                                        .padding(2.dp)
                                )
                            }

                            // ⌄ ড্রপডাউন অ্যারো
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF333E50))
                                    .clickable {
                                        showTelegramContextMenu = false
                                        showFullTelegramGridDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // স্পিচ বাবল টেইল (ছোট ত্রিভুজ পয়েন্টার)
                    Canvas(
                        modifier = Modifier
                            .size(16.dp, 8.dp)
                            .padding(end = 24.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(path = path, color = TelegramDarkMenuBg)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ২. নিচের অপশন মেনু কার্ড
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramDarkMenuBg,
                        border = BorderStroke(0.8.dp, TelegramMenuBorder),
                        shadowElevation = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // ১. Reply
                            TelegramContextMenuItem(
                                icon = Icons.AutoMirrored.Filled.Reply,
                                label = "Reply",
                                onClick = {
                                    showTelegramContextMenu = false
                                    onReplyClick()
                                }
                            )

                            HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                            // ২. Copy Link
                            TelegramContextMenuItem(
                                icon = Icons.Default.Link,
                                label = "Copy Link",
                                onClick = {
                                    showTelegramContextMenu = false
                                    val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Drama Link", shareUrl))
                                    Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                            // ৩. Favorite
                            TelegramContextMenuItem(
                                icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label = if (isInWatchlist) "Added to Favorite" else "Favorite",
                                iconColor = if (isInWatchlist) Color(0xFFFFB300) else Color.White,
                                onClick = {
                                    showTelegramContextMenu = false
                                    onWatchlistClick()
                                    Toast.makeText(
                                        context,
                                        if (isInWatchlist) "Removed from favorites" else "Added to favorites! ⭐",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                            // ৪. Forward
                            TelegramContextMenuItem(
                                icon = Icons.AutoMirrored.Filled.Forward,
                                label = "Forward",
                                onClick = {
                                    showTelegramContextMenu = false
                                    onForwardClick()
                                    try {
                                        val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Watch ${content.displayName} on PlayDramaFlix: $shareUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Forward Drama"))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 🧸 সম্পূর্ণ ৬৪টি ইমোজি গ্রিড ডায়ালগ
    // =========================================================================
    if (showFullTelegramGridDialog) {
        Dialog(onDismissRequest = { showFullTelegramGridDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TelegramDarkMenuBg,
                border = BorderStroke(1.dp, TelegramMenuBorder),
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

                    HorizontalDivider(color = TelegramMenuBorder, thickness = 0.8.dp)

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

// =============================================================================
// 🔘 পপআপ মেনু রো কম্পোনেন্ট
// =============================================================================
@Composable
private fun TelegramContextMenuItem(
    icon: ImageVector,
    label: String,
    iconColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = TelegramMenuText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
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
