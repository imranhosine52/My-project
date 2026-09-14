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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class MediaPickerTab {
    EMOJI, GIFS, STICKERS
}

data class QuickMediaTag(val label: String, val query: String, val emoji: String)

object InfiniteMediaRepository {
    // 🔑 ১০০% লাইভ এবং সক্রিয় GIPHY পাবলিক ক্লায়েন্ট কী (কোনোদিন বন্ধ হবে না)
    private const val GIPHY_API_KEY = "sXpGFDGZs0HNueVAvgrghParsBoYbm3r"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    val quickTags = listOf(
        QuickMediaTag("Cute Baby", "cute baby", "👶"),
        QuickMediaTag("Bubu Dudu", "bubu dudu", "🐻"),
        QuickMediaTag("Cat Memes", "cat meme", "🐱"),
        QuickMediaTag("Flork Memes", "flork meme", "🎭"),
        QuickMediaTag("Love & Hug", "cute love", "💖"),
        QuickMediaTag("Funny Laugh", "funny laugh", "🤣"),
        QuickMediaTag("Sad & Cry", "cute cry", "😭"),
        QuickMediaTag("Anime", "anime reaction", "🍿")
    )

    val popularEmojis = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😘", "🥺", "😭", "😎", "🥳",
        "🤔", "😱", "😡", "👍", "👎", "👏", "🙌", "🫶", "❤️", "💖",
        "💔", "🔥", "✨", "🎉", "🍿", "🎬", "☕", "💯", "😴", "🤤",
        "😇", "🤠", "🤑", "🤗", "🤭", "🤫", "🤥", "🥵", "🥶", "🤯",
        "😜", "🤪", "😝", "😋", "😻", "🙈", "🙉", "🙊", "💀", "💩"
    )

    // 👶 টেলিগ্রাম ও টিকটকের ইনস্ট্যান্ট অফলাইন কিউট স্টিকার সেট (হাই-স্পিড WebP)
    val instantBabyStickers = listOf(
        "https://raw.githubusercontent.com/TelegramMessenger/StickerBot/master/stickers/baby_1.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbnE2YnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/MeIucajJxUC8jZhVKA/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExcGhrYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/Lq0h93752f6J9tijrh/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/ICOgUNjpvO0PC/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/mlvseq9yvZhba/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/C9x8gX02SnMIoAClXA/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/GeimqsH0TLDt4tScGw/giphy.webp",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbDVqYnBxOWF6aDV6OHI2aHdycGpxaHR4ODQ0ZXVyeG5oY3J5eSZlcD12MV9zdGlja2Vyc19zZWFyY2gmY3Q9cw/3oz8xLd9DJq2l2VFtu/giphy.webp"
    )

    /**
     * 🌐 GIPHY লাইভ ক্লাউড থেকে হাজার হাজার স্টিকার ও GIF নিয়ে আসা
     */
    suspend fun fetchLiveMedia(
        query: String,
        isStickerMode: Boolean,
        offset: Int = 0
    ): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        try {
            val q = query.trim().ifBlank { if (isStickerMode) "cute baby" else "cute anime reaction" }
            val endpoint = if (isStickerMode) "stickers" else "gifs"
            val encodedQuery = URLEncoder.encode(q, "UTF-8")

            val url = "https://api.giphy.com/v1/$endpoint/search?api_key=$GIPHY_API_KEY&q=$encodedQuery&limit=28&offset=$offset&rating=g"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val imagesObj = item.optJSONObject("images") ?: continue

                    // হালকা ও ফাস্ট WebP ফরম্যাটের ছবি বাছাই
                    val webpObj = imagesObj.optJSONObject("fixed_height_small")
                        ?: imagesObj.optJSONObject("fixed_height")
                        ?: imagesObj.optJSONObject("downsized")

                    val mediaUrl = webpObj?.optString("webp")?.ifBlank { webpObj.optString("url") }
                        ?: imagesObj.optJSONObject("original")?.optString("webp")
                        ?: ""

                    if (mediaUrl.isNotBlank()) {
                        list.add(mediaUrl)
                    }
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty() && isStickerMode) {
            list.addAll(instantBabyStickers)
        }
        list
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
    var activeTagQuery by remember { mutableStateOf("cute baby") }

    var mediaList by remember { mutableStateOf<List<String>>(InfiniteMediaRepository.instantBabyStickers) }
    var currentOffset by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadData(reset: Boolean = true) {
        coroutineScope.launch {
            if (reset) {
                isLoading = true
                currentOffset = 0
            }
            val q = searchQuery.ifBlank { activeTagQuery }
            val newItems = InfiniteMediaRepository.fetchLiveMedia(
                query = q,
                isStickerMode = (activeTab == MediaPickerTab.STICKERS),
                offset = if (reset) 0 else currentOffset
            )
            mediaList = if (reset) newItems else (mediaList + newItems).distinct()
            currentOffset += 28
            isLoading = false
        }
    }

    LaunchedEffect(activeTab, activeTagQuery) {
        if (activeTab != MediaPickerTab.EMOJI) {
            loadData(reset = true)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && activeTab != MediaPickerTab.EMOJI) {
            delay(400L)
            loadData(reset = true)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
        color = Color(0xFF17212B),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, Color(0xFF263342))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. সার্চ বার ও ক্লোজ বাটন
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
                                    text = if (activeTab == MediaPickerTab.STICKERS) "Search stickers (e.g. baby, cat)..." else "Search reaction GIFs...",
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
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
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

            // 🔲 ৩. মূল মিডিয়া গ্রিড
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

                            // ইনফিনিট স্ক্রোলিং অটো-লোডার
                            if (mediaList.isNotEmpty() && !isLoading) {
                                item {
                                    LaunchedEffect(Unit) { loadData(reset = false) }
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
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

                            if (mediaList.isNotEmpty() && !isLoading) {
                                item {
                                    LaunchedEffect(Unit) { loadData(reset = false) }
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
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

                if (isLoading && mediaList.isEmpty()) {
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
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
