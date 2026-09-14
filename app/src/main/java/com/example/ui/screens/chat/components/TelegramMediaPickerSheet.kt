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
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class MediaPickerTab {
    EMOJI, GIFS, STICKERS
}

data class DynamicMediaPack(
    val categoryId: Int,
    val name: String,
    val iconEmoji: String,
    val items: List<String>
)

object ServerStickerRepository {
    private const val API_URL = "https://playdramaflix.com/api/v1/stickers"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // 👶 অফলাইন ব্যাকআপ স্টিকার প্যাক (নেটওয়ার্ক ফেইল করলেও সাথে সাথে শো করবে)
    val fallbackStickerPacks = listOf(
        DynamicMediaPack(
            categoryId = 1,
            name = "Cute Babies",
            iconEmoji = "👶",
            items = listOf(
                "https://media.tenor.com/vH9Z1i_d5XMAAAAi/baby-laughing.gif",
                "https://media.tenor.com/1v6H-o4z04kAAAAi/cute-baby.gif",
                "https://media.tenor.com/k1Fv3O9a6vAAAAAi/baby-dance.gif",
                "https://media.tenor.com/4B6Y-Fwz2_8AAAAi/baby-funny.gif",
                "https://media.tenor.com/6X2pY1p4mYgAAAAi/crying-baby.gif",
                "https://media.tenor.com/d_3T5I1Zq9AAAAAi/boss-baby.gif"
            )
        ),
        DynamicMediaPack(
            categoryId = 2,
            name = "Bubu Dudu",
            iconEmoji = "🐻",
            items = listOf(
                "https://media.tenor.com/2s_c711Wp3EAAAAi/bubu-dudu-bubu.gif",
                "https://media.tenor.com/0uB0B2vJ1fAAAAAi/bubu-dudu.gif",
                "https://media.tenor.com/w8pWj3s3XfIAAAAi/peach-and-goma-goma.gif",
                "https://media.tenor.com/T0bS1Y4L5ZcAAAAi/peach-goma.gif"
            )
        ),
        DynamicMediaPack(
            categoryId = 3,
            name = "Cat Memes",
            iconEmoji = "🐱",
            items = listOf(
                "https://media.tenor.com/Fw57n8c6xXQAAAAi/cat-meme.gif",
                "https://media.tenor.com/1G6K2V_42tUAAAAi/pop-cat.gif",
                "https://media.tenor.com/fKk_1Qp56uAAAAAi/cat-dance.gif",
                "https://media.tenor.com/T1G9s5QY1GAAAAAi/cat-jam.gif"
            )
        )
    )

    val fallbackGifs = listOf(
        DynamicMediaPack(
            categoryId = 4,
            name = "Reaction GIFs",
            iconEmoji = "🎬",
            items = listOf(
                "https://media.tenor.com/p_o6A8O3a50AAAAC/hug-love.gif",
                "https://media.tenor.com/2s_c711Wp3EAAAAC/bubu-dudu-bubu.gif",
                "https://media.tenor.com/X1V5n8m9xQAAAAAC/cute-dance.gif",
                "https://media.tenor.com/V7M8n9p4wEAAAAAC/anime-excited.gif"
            )
        )
    )

    val defaultEmojis = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😘", "🥺", "😭", "😎", "🥳",
        "🤔", "😱", "😡", "👍", "👎", "👏", "🙌", "🫶", "❤️", "💖",
        "💔", "🔥", "✨", "🎉", "🍿", "🎬", "☕", "💯", "😴", "🤤",
        "😇", "🤠", "🤑", "🤗", "🤭", "🤫", "🤥", "🥵", "🥶", "🤯",
        "😜", "🤪", "😝", "😋", "😻", "🙈", "🙉", "🙊", "💀", "💩"
    )

    /**
     * 🌐 নিজস্ব সার্ভার API থেকে সব ক্যাটাগরি ও স্টিকার লোড করা
     */
    suspend fun fetchServerMedia(): Triple<List<DynamicMediaPack>, List<DynamicMediaPack>, List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "PlayDramaFlix-AndroidApp/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful && bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                val stickerPacks = mutableListOf<DynamicMediaPack>()
                val gifPacks = mutableListOf<DynamicMediaPack>()
                val emojis = mutableListOf<String>()

                // ১. স্টিকার প্যাক পার্সিং
                val stArr = json.optJSONArray("sticker_packs")
                if (stArr != null) {
                    for (i in 0 until stArr.length()) {
                        val packObj = stArr.getJSONObject(i)
                        val itemsArr = packObj.optJSONArray("items") ?: continue
                        val urls = mutableListOf<String>()
                        for (j in 0 until itemsArr.length()) {
                            val u = itemsArr.getString(j)
                            if (u.isNotBlank()) urls.add(u)
                        }
                        if (urls.isNotEmpty()) {
                            stickerPacks.add(
                                DynamicMediaPack(
                                    categoryId = packObj.optInt("category_id", i + 1),
                                    name = packObj.optString("name", "Pack"),
                                    iconEmoji = packObj.optString("icon_emoji", "🧸"),
                                    items = urls
                                )
                            )
                        }
                    }
                }

                // ২. GIF প্যাক পার্সিং
                val gfArr = json.optJSONArray("gif_packs")
                if (gfArr != null) {
                    for (i in 0 until gfArr.length()) {
                        val packObj = gfArr.getJSONObject(i)
                        val itemsArr = packObj.optJSONArray("items") ?: continue
                        val urls = mutableListOf<String>()
                        for (j in 0 until itemsArr.length()) {
                            val u = itemsArr.getString(j)
                            if (u.isNotBlank()) urls.add(u)
                        }
                        if (urls.isNotEmpty()) {
                            gifPacks.add(
                                DynamicMediaPack(
                                    categoryId = packObj.optInt("category_id", i + 1),
                                    name = packObj.optString("name", "GIFs"),
                                    iconEmoji = packObj.optString("icon_emoji", "🎬"),
                                    items = urls
                                )
                            )
                        }
                    }
                }

                // ৩. ইমোজি পার্সিং
                val emArr = json.optJSONArray("emojis")
                if (emArr != null) {
                    for (i in 0 until emArr.length()) {
                        emojis.add(emArr.getString(i))
                    }
                }

                return@withContext Triple(
                    if (stickerPacks.isNotEmpty()) stickerPacks else fallbackStickerPacks,
                    if (gifPacks.isNotEmpty()) gifPacks else fallbackGifs,
                    if (emojis.isNotEmpty()) emojis else defaultEmojis
                )
            }
        } catch (_: Exception) {}

        Triple(fallbackStickerPacks, fallbackGifs, defaultEmojis)
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
    var selectedPackIndex by remember { mutableIntStateOf(0) }

    var serverStickerPacks by remember { mutableStateOf(ServerStickerRepository.fallbackStickerPacks) }
    var serverGifPacks by remember { mutableStateOf(ServerStickerRepository.fallbackGifs) }
    var serverEmojis by remember { mutableStateOf(ServerStickerRepository.defaultEmojis) }
    var isLoadingServerData by remember { mutableStateOf(true) }

    // 🔄 সার্ভার থেকে লাইভ ডাটা লোড
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val (stickers, gifs, emojis) = ServerStickerRepository.fetchServerMedia()
            serverStickerPacks = stickers
            serverGifPacks = gifs
            serverEmojis = emojis
            isLoadingServerData = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp),
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
                                    text = if (activeTab == MediaPickerTab.STICKERS) "Filter stickers..." else "Filter GIFs...",
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

            // 🏷️ ২. সার্ভার থেকে আসা ডায়নামিক ক্যাটাগরি প্যাক রো
            if (activeTab == MediaPickerTab.STICKERS && serverStickerPacks.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(serverStickerPacks) { idx, pack ->
                        val isSelected = (idx == selectedPackIndex)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF2B5278) else Color(0xFF1E2A38))
                                .clickable { selectedPackIndex = idx }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pack.iconEmoji} ${pack.name}",
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

            // 🔲 ৩. মূল মিডিয়া গ্রিড (সার্ভার থেকে লোড হওয়া আইটেম)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    MediaPickerTab.STICKERS -> {
                        val currentPack = serverStickerPacks.getOrElse(selectedPackIndex) { serverStickerPacks.first() }
                        val displayItems = currentPack.items.filter { it.contains(searchQuery, ignoreCase = true) || searchQuery.isEmpty() }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayItems) { stickerUrl ->
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
                        }
                    }

                    MediaPickerTab.GIFS -> {
                        val currentGifPack = serverGifPacks.firstOrNull()
                        val gifItems = currentGifPack?.items ?: emptyList()

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(gifItems) { gifUrl ->
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
                            items(serverEmojis) { emoji ->
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

                if (isLoadingServerData && serverStickerPacks.isEmpty()) {
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
