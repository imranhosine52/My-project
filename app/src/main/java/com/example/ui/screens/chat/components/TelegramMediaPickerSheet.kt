package com.example.ui.screens.chat.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class MediaPickerTab {
    EMOJI, GIFS, STICKERS
}

data class QuickMediaTag(val label: String, val query: String, val emoji: String)

object InfiniteMediaRepository {
    private const val TENOR_KEY = "LIVDSRZULELA" // 🔑 গ্লোবাল ফ্রি পাবলিক ক্লায়েন্ট কি
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    val quickTags = listOf(
        QuickMediaTag("Cute Baby", "cute baby sticker meme", "👶"),
        QuickMediaTag("Bubu Dudu", "bubu dudu sticker", "🐻"),
        QuickMediaTag("Cat Memes", "cute cat meme sticker", "🐱"),
        QuickMediaTag("Flork Memes", "flork meme sticker", "🎭"),
        QuickMediaTag("Love & Hug", "cute love hug sticker", "💖"),
        QuickMediaTag("Funny & Laugh", "funny laugh meme sticker", "🤣"),
        QuickMediaTag("Sad & Cry", "cute cry sad sticker", "😭"),
        QuickMediaTag("Anime Reaction", "anime cute reaction sticker", "🍿")
    )

    val popularEmojis = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😘", "🥺", "😭", "😎", "🥳",
        "🤔", "😱", "😡", "👍", "👎", "👏", "🙌", "🫶", "❤️", "💖",
        "💔", "🔥", "✨", "🎉", "🍿", "🎬", "☕", "💯", "😴", "🤤",
        "😇", "🤠", "🤑", "🤗", "🤭", "🤫", "🤥", "🥵", "🥶", "🤯",
        "😜", "🤪", "😝", "😋", "😻", "🙈", "🙉", "🙊", "💀", "💩"
    )

    // অফলাইন বা ইনস্ট্যান্ট লোডের জন্য ব্যাকআপ ভাইরাল স্টিকার
    val instantFallbackStickers = listOf(
        "https://media.tenor.com/vH9Z1i_d5XMAAAAi/baby-laughing.gif",
        "https://media.tenor.com/1v6H-o4z04kAAAAi/cute-baby.gif",
        "https://media.tenor.com/k1Fv3O9a6vAAAAAi/baby-dance.gif",
        "https://media.tenor.com/4B6Y-Fwz2_8AAAAi/baby-funny.gif",
        "https://media.tenor.com/6X2pY1p4mYgAAAAi/crying-baby.gif",
        "https://media.tenor.com/d_3T5I1Zq9AAAAAi/boss-baby.gif",
        "https://media.tenor.com/2s_c711Wp3EAAAAi/bubu-dudu-bubu.gif",
        "https://media.tenor.com/0uB0B2vJ1fAAAAAi/bubu-dudu.gif",
        "https://media.tenor.com/w8pWj3s3XfIAAAAi/peach-and-goma-goma.gif",
        "https://media.tenor.com/T0bS1Y4L5ZcAAAAi/peach-goma.gif",
        "https://media.tenor.com/Fw57n8c6xXQAAAAi/cat-meme.gif",
        "https://media.tenor.com/1G6K2V_42tUAAAAi/pop-cat.gif",
        "https://media.tenor.com/kS9l5n7Z-1sAAAAi/flork-de-meme-flork.gif",
        "https://media.tenor.com/k9m7xPq80qYAAAAi/flork-meme.gif",
        "https://media.tenor.com/y3y_eE-z0E8AAAAi/pepe-dance.gif",
        "https://media.tenor.com/G3_m8X0u5hIAAAAi/pepe-frog.gif"
    )

    /**
     * 🌐 Google Tenor API থেকে লাখ লাখ লাইভ স্টিকার বা GIF ফেচ করার ইঞ্জিন
     */
    suspend fun fetchMediaFromTenor(
        searchQuery: String,
        isStickerMode: Boolean,
        nextPos: String = ""
    ): Pair<List<String>, String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        var newNextPos = ""
        try {
            val q = searchQuery.trim().ifBlank { if (isStickerMode) "cute baby sticker" else "trending cute" }
            val filterParam = if (isStickerMode) "&searchfilter=sticker" else ""
            val posParam = if (nextPos.isNotBlank()) "&pos=$nextPos" else ""
            val url = "https://g.tenor.com/v1/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}&key=$TENOR_KEY&limit=32$filterParam$posParam"

            val req = Request.Builder().url(url).build()
            val res = httpClient.newCall(req).execute()
            val bodyStr = res.body?.string() ?: ""
            res.close()

            val json = JSONObject(bodyStr)
            newNextPos = json.optString("next", "")

            val results = json.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val mediaArray = item.optJSONArray("media")
                    if (mediaArray != null && mediaArray.length() > 0) {
                        val mediaObj = mediaArray.getJSONObject(0)
                        val gifObj = mediaObj.optJSONObject("tinygif") ?: mediaObj.optJSONObject("gif") ?: mediaObj.optJSONObject("nanogif")
                        val mediaUrl = gifObj?.optString("url") ?: ""
                        if (mediaUrl.isNotBlank()) {
                            list.add(mediaUrl)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty() && isStickerMode) {
            list.addAll(instantFallbackStickers)
        }
        Pair(list, newNextPos)
    }
}

@Composable
fun TelegramMediaPickerSheet(
    onSendSticker: (String) -> Unit,
    onSendGif: (String) -> Unit,
    onSelectEmoji: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var activeTab by remember { mutableStateOf(MediaPickerTab.STICKERS) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTagQuery by remember { mutableStateOf("cute baby sticker meme") }

    var mediaList by remember { mutableStateOf<List<String>>(InfiniteMediaRepository.instantFallbackStickers) }
    var nextPositionToken by remember { mutableStateOf("") }
    var isLoadingMore by remember { mutableStateOf(false) }

    // 🔄 সার্চ বা ট্যাগ পরিবর্তনের সাথে সাথে লাইভ স্টিকার লোড করা
    fun loadMedia(resetList: Boolean = true) {
        coroutineScope.launch {
            if (resetList) {
                isLoadingMore = true
                nextPositionToken = ""
            }
            val targetQuery = searchQuery.ifBlank { activeTagQuery }
            val (newItems, nextToken) = InfiniteMediaRepository.fetchMediaFromTenor(
                searchQuery = targetQuery,
                isStickerMode = (activeTab == MediaPickerTab.STICKERS),
                nextPos = if (resetList) "" else nextPositionToken
            )
            nextPositionToken = nextToken
            mediaList = if (resetList) newItems else (mediaList + newItems).distinct()
            isLoadingMore = false
        }
    }

    LaunchedEffect(activeTab, activeTagQuery) {
        if (activeTab != MediaPickerTab.EMOJI) {
            loadMedia(resetList = true)
        }
    }

    // সার্চ ইনপুট ডিবউন্স
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && activeTab != MediaPickerTab.EMOJI) {
            delay(450L)
            loadMedia(resetList = true)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp), // 👈 চমৎকার উচ্চতা
        color = Color(0xFF17212B),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, Color(0xFF263342))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. লাইভ সার্চ বার ও ক্লোজ বাটন
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeTab != MediaPickerTab.EMOJI) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF243447))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF8692A6),
                            modifier = Modifier.size(16.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (activeTab == MediaPickerTab.STICKERS) "Search millions of stickers..." else "Search millions of GIFs...",
                                    color = Color(0xFF8692A6),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF8692A6),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "😊 Expressive Emojis",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8692A6), modifier = Modifier.size(18.dp))
                }
            }

            // 🏷️ ২. ভাইরাল কুইক ট্যাগস রো
            if (activeTab != MediaPickerTab.EMOJI) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(InfiniteMediaRepository.quickTags) { tag ->
                        val isSelected = (activeTagQuery == tag.query && searchQuery.isEmpty())
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF2B5278) else Color(0xFF1E2A38))
                                .clickable {
                                    searchQuery = ""
                                    activeTagQuery = tag.query
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tag.emoji} ${tag.label}",
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8692A6),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            HorizontalDivider(color = Color(0xFF222C3A), thickness = 0.6.dp)

            // 🔲 ৩. মূল ইনফিনিট গ্রিড
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    MediaPickerTab.STICKERS -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mediaList) { stickerUrl ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSendSticker(stickerUrl) }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(stickerUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            // ♾️ ইনফিনিট স্ক্রোল লোডার
                            if (mediaList.isNotEmpty() && nextPositionToken.isNotBlank()) {
                                item {
                                    LaunchedEffect(Unit) {
                                        loadMedia(resetList = false)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00E5FF),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MediaPickerTab.GIFS -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mediaList) { gifUrl ->
                                Box(
                                    modifier = Modifier
                                        .height(105.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1B2430))
                                        .clickable { onSendGif(gifUrl) }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(gifUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            if (mediaList.isNotEmpty() && nextPositionToken.isNotBlank()) {
                                item {
                                    LaunchedEffect(Unit) {
                                        loadMedia(resetList = false)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00E5FF),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MediaPickerTab.EMOJI -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(InfiniteMediaRepository.popularEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable { onSelectEmoji(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

                if (isLoadingMore && mediaList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.5.dp)
                    }
                }
            }

            // =========================================================================
            // 🌟 ৪. টেলিগ্রাম ফ্রস্টেড সুইচ পিল: [ Emoji | GIFs | Stickers ]
            // =========================================================================
            Surface(
                color = Color(0xFF141C24),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF263342))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabPillButton(
                        text = "Emoji",
                        icon = "😊",
                        isSelected = (activeTab == MediaPickerTab.EMOJI),
                        onClick = { activeTab = MediaPickerTab.EMOJI }
                    )

                    TabPillButton(
                        text = "GIFs",
                        icon = "🎬",
                        isSelected = (activeTab == MediaPickerTab.GIFS),
                        onClick = { activeTab = MediaPickerTab.GIFS }
                    )

                    TabPillButton(
                        text = "Stickers",
                        icon = "🧸",
                        isSelected = (activeTab == MediaPickerTab.STICKERS),
                        onClick = { activeTab = MediaPickerTab.STICKERS }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPillButton(
    text: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFF2B5278) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 12.sp)
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFF8692A6),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
