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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.model.ContentItemDto
import com.example.ui.theme.GoldVip
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class ReactionMeta(
    val key: String,
    val emoji: String
)

// কুইক রিঅ্যাকশন ইমোজি লিস্ট
val quickReactionsList = listOf(
    ReactionMeta("heart", "❤️"),
    ReactionMeta("like", "👍"),
    ReactionMeta("dislike", "👎"),
    ReactionMeta("fire", "🔥"),
    ReactionMeta("love_face", "🥰"),
    ReactionMeta("clap", "👏"),
    ReactionMeta("laugh", "😁"),
    ReactionMeta("star_eyes", "🤩"),
    ReactionMeta("party", "🎉"),
    ReactionMeta("hundred", "💯")
)

// টেলিগ্রামের ফুল ৬৪টি ইমোজি লিস্ট
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
            "hundred" -> "💯"
            "star_eyes" -> "🤩"
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

    // পপআপ এবং ইন-প্লেস ইমোজি পিকারের স্টেট
    var showContextMenu by remember { mutableStateOf(false) }
    var isShowingFullEmojiPickerInPopup by remember { mutableStateOf(false) }

    // লাভ রিঅ্যাক্ট মূল লাইক কাউন্টারের সাথে থাকবে, বাকিগুলো পাশে দেখাবে
    val activeOtherReactions = remember(reactions) {
        reactions.filter { it.key.lowercase() != "heart" && it.value > 0 }
    }

    // লাভ রিঅ্যাক্টের বাউন্স অ্যানিমেশন
    val reactionAnimScale = remember { Animatable(1f) }

    fun triggerReactionWithBounce(key: String) {
        coroutineScope.launch {
            reactionAnimScale.snapTo(0.6f)
            reactionAnimScale.animateTo(
                targetValue = 1.35f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            delay(120)
            reactionAnimScale.animateTo(1f, tween(150))
        }

        if (key.equals("heart", ignoreCase = true)) {
            onLikeClick()
        } else {
            onToggleReaction(key)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        ) {
            // =========================================================================
            // 🎯 মূল হেডার লেআউট (টাইটেল, প্রি/নেক্সট এবং নীল দাগের পুরো কালো ইন্টারঅ্যাক্টিভ এরিয়া)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 👈 বাম পাশের কলাম: ড্রামা টাইটেল ও মেটাডাটা
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = shortTitle,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // মেটাডাটা রো: সাল • রেটিং • ...more
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = content.releaseYear.ifBlank { "2026" },
                            color = Color(0xFF8E95A5),
                            fontSize = 11.sp,
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
                }

                // 👉 ডান পাশের কলাম: Pre/Next এবং আপনার নীল দাগ দেওয়া সম্পূর্ণ কালো এরিয়া
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Pre / Next বাটন রো
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

                    // =====================================================================
                    // 🎯 ছবির নীল দাগ দেওয়া সম্পূর্ণ কালো অংশ:
                    // সিঙ্গেল ট্যাপ ➔ সাইড পপআপ ওপেন
                    // ডাবল ট্যাপ ➔ সরাসরি লাভ রিঅ্যাক্ট (❤️)
                    // =====================================================================
                    Box(
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Transparent)
                            .pointerInput(isLiked, likesCount, myReaction, reactions) {
                                detectTapGestures(
                                    onTap = {
                                        showContextMenu = !showContextMenu
                                        isShowingFullEmojiPickerInPopup = false
                                    },
                                    onDoubleTap = {
                                        triggerReactionWithBounce("heart")
                                    }
                                )
                            }
                            .padding(top = 4.dp, bottom = 2.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ১. অন্যান্য রিঅ্যাকশনগুলো (একদম স্বচ্ছ, কোনো ব্যাকগ্রাউন্ড ছাড়া)
                            if (activeOtherReactions.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    activeOtherReactions.toList().take(3).forEach { (key, count) ->
                                        val emojiChar = getEmojiSymbolForKey(key)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(text = emojiChar, fontSize = 12.sp)
                                            Text(
                                                text = count.toString(),
                                                color = Color(0xFFCBD0DB),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // ২. লাভ রিঅ্যাক্ট (❤️ লাইক কাউন্টারের সাথে সিঙ্ক)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.scale(reactionAnimScale.value)
                            ) {
                                Icon(
                                    imageVector = if (isLiked || myReaction?.equals("heart", true) == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isLiked || myReaction?.equals("heart", true) == true) Color(0xFFFF2A4B) else Color(0xFFADB3C2),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = formatCount(likesCount),
                                    color = if (isLiked || myReaction?.equals("heart", true) == true) Color(0xFFFF2A4B) else Color(0xFFCBD0DB),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // ৩. সবার ডানে মোবাইলের কোণায় ভিউস কাউন্টার (👁️)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ডেসক্রিপশন এক্সপ্যান্ডেড ভিউ
            if (isDescriptionExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
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
        // 🌟 ২ নম্বর ছবির হুবহু পপআপ (ক্লিক করা কালো অংশের সাথে অ্যাঙ্কর্ড, কোনো স্ক্রিন ডিম নেই)
        // =========================================================================
        if (showContextMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = (-12).dp.value.toInt(), y = 64.dp.value.toInt()),
                onDismissRequest = {
                    showContextMenu = false
                    isShowingFullEmojiPickerInPopup = false
                },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                AnimatedVisibility(
                    visible = showContextMenu,
                    enter = scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(140)),
                    exit = scaleOut(targetScale = 0.82f, animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
                ) {
                    AnimatedContent(
                        targetState = isShowingFullEmojiPickerInPopup,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                            }
                        },
                        label = "TelegramCardSwitch"
                    ) { isFullEmojiMode ->
                        if (!isFullEmojiMode) {
                            // -------------------------------------------------------------
                            // ক) মূল মেনু কার্ড (ইমোজি স্লাইডার + Reply, Copy Link ইত্যাদি)
                            // -------------------------------------------------------------
                            Column(
                                modifier = Modifier.width(260.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                // ১. স্লাইডিং ইমোজি বাবল
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = TelegramDarkMenuBg,
                                    border = BorderStroke(0.8.dp, TelegramMenuBorder),
                                    shadowElevation = 14.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // ↔️ ইমোজি স্ক্রোলিং বার
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            quickReactionsList.forEach { item ->
                                                Text(
                                                    text = item.emoji,
                                                    fontSize = 20.sp,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            triggerReactionWithBounce(item.key)
                                                            showContextMenu = false
                                                        }
                                                        .padding(3.dp)
                                                )
                                            }
                                        }

                                        // ⌄ ড্রপডাউন বাটন (ক্লিক করলে এখানেই কার্ডের ভেতর ৬৪টি ইমোজি আসবে)
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF333E50))
                                                .clickable {
                                                    isShowingFullEmojiPickerInPopup = true // 👈 ইন-প্লেস ইমোজি ওপেন
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "More Emojis",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // স্পিচ বাবল কানেক্টিং টেইল
                                Canvas(
                                    modifier = Modifier
                                        .size(18.dp, 8.dp)
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

                                Spacer(modifier = Modifier.height(2.dp))

                                // ২. অপশন মেনু কার্ড
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = TelegramDarkMenuBg,
                                    border = BorderStroke(0.8.dp, TelegramMenuBorder),
                                    shadowElevation = 18.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        TelegramContextMenuItem(
                                            icon = Icons.AutoMirrored.Filled.Reply,
                                            label = "Reply",
                                            onClick = {
                                                showContextMenu = false
                                                onReplyClick()
                                            }
                                        )

                                        HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                                        TelegramContextMenuItem(
                                            icon = Icons.Default.Link,
                                            label = "Copy Link",
                                            onClick = {
                                                showContextMenu = false
                                                val shareUrl = "https://playdramaflix.com/watch/${content.slug}"
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Drama Link", shareUrl))
                                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                                        TelegramContextMenuItem(
                                            icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            label = if (isInWatchlist) "Added to Favorite" else "Favorite",
                                            iconColor = if (isInWatchlist) Color(0xFFFFB300) else Color.White,
                                            onClick = {
                                                showContextMenu = false
                                                onWatchlistClick()
                                                Toast.makeText(
                                                    context,
                                                    if (isInWatchlist) "Removed from favorites" else "Added to favorites! ⭐",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )

                                        HorizontalDivider(color = TelegramMenuBorder.copy(alpha = 0.5f), thickness = 0.6.dp)

                                        TelegramContextMenuItem(
                                            icon = Icons.AutoMirrored.Filled.Forward,
                                            label = "Forward",
                                            onClick = {
                                                showContextMenu = false
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
                        } else {
                            // -------------------------------------------------------------
                            // খ) ঠিক এই পজিশনেই ওপেন হওয়া ৬৪টি ইমোজি কার্ড (ব্যাক ← বাটন সহ)
                            // -------------------------------------------------------------
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = TelegramDarkMenuBg,
                                border = BorderStroke(0.8.dp, TelegramMenuBorder),
                                shadowElevation = 18.dp,
                                modifier = Modifier
                                    .width(280.dp)
                                    .wrapContentHeight()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 🔙 হেডার: ব্যাক বাটন (←) + টাইটেল + ক্লোজ বাটন
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // ব্যাক বাটন (ক্লিক করলে আবার আগের মেনুতে ফেরত যাবে)
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2C394B))
                                                    .clickable {
                                                        isShowingFullEmojiPickerInPopup = false // 👈 ব্যাক করে মেনুতে ফিরবে
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Text(
                                                text = "Reactions",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF8E95A5),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    showContextMenu = false
                                                    isShowingFullEmojiPickerInPopup = false
                                                }
                                        )
                                    }

                                    HorizontalDivider(color = TelegramMenuBorder, thickness = 0.6.dp)

                                    // ৮-কলামের পূর্ণাঙ্গ টেলিগ্রাম ইমোজি গ্রিড
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(8),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                    ) {
                                        items(fullPickerReactionsList) { item ->
                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        triggerReactionWithBounce(item.key)
                                                        showContextMenu = false
                                                        isShowingFullEmojiPickerInPopup = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = item.emoji, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
