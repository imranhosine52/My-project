@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.screens.chat.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
    private const val API_URL_PRIMARY = "https://playdramaflix.com/api/v1/stickers"
    private const val API_URL_FALLBACK = "https://playdramaflix.com/api/v1/routes.php/stickers"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

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

    suspend fun fetchServerMedia(): Triple<List<DynamicMediaPack>, List<DynamicMediaPack>, List<String>> = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(API_URL_PRIMARY, API_URL_FALLBACK)

        for (targetUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "PlayDramaFlix-AndroidApp/1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                response.close()

                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    val stickerPacks = mutableListOf<DynamicMediaPack>()
                    val gifPacks = mutableListOf<DynamicMediaPack>()
                    val emojis = mutableListOf<String>()

                    val stArr = json.optJSONArray("sticker_packs")
                    if (stArr != null) {
                        for (i in 0 until stArr.length()) {
                            val packObj = stArr.getJSONObject(i)
                            val itemsArr = packObj.optJSONArray("items") ?: continue
                            val urls = mutableListOf<String>()
                            for (j in 0 until itemsArr.length()) {
                                val u = itemsArr.getString(j).trim()
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

                    val gfArr = json.optJSONArray("gif_packs")
                    if (gfArr != null) {
                        for (i in 0 until gfArr.length()) {
                            val packObj = gfArr.getJSONObject(i)
                            val itemsArr = packObj.optJSONArray("items") ?: continue
                            val urls = mutableListOf<String>()
                            for (j in 0 until itemsArr.length()) {
                                val u = itemsArr.getString(j).trim()
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

                    val emArr = json.optJSONArray("emojis")
                    if (emArr != null) {
                        for (i in 0 until emArr.length()) {
                            emojis.add(emArr.getString(i))
                        }
                    }

                    if (stickerPacks.isNotEmpty() || gifPacks.isNotEmpty()) {
                        return@withContext Triple(
                            if (stickerPacks.isNotEmpty()) stickerPacks else fallbackStickerPacks,
                            if (gifPacks.isNotEmpty()) gifPacks else fallbackGifs,
                            if (emojis.isNotEmpty()) emojis else defaultEmojis
                        )
                    }
                }
            } catch (_: Exception) {}
        }

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
    val favPrefs = remember { context.getSharedPreferences("chat_sticker_favorites", Context.MODE_PRIVATE) }

    // ⭐️ ফেভারিট স্টিকার তালিকা
    var favoriteList by remember {
        mutableStateOf(favPrefs.getStringSet("fav_items", emptySet())?.toList() ?: emptyList())
    }

    fun toggleFavoriteSticker(url: String) {
        val currentSet = favPrefs.getStringSet("fav_items", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentSet.contains(url)) {
            currentSet.remove(url)
            Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            currentSet.add(url)
            Toast.makeText(context, "⭐ Added to Favorites!", Toast.LENGTH_SHORT).show()
        }
        favPrefs.edit().putStringSet("fav_items", currentSet).apply()
        favoriteList = currentSet.toList()
    }

    // 🔀 ৩টি পেজ বিশিষ্ট সোয়াইপ পেজার: 0 = EMOJI, 1 = GIFS, 2 = STICKERS
    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 3 })

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackIndex by remember { mutableIntStateOf(0) }

    var serverStickerPacks by remember { mutableStateOf(ServerStickerRepository.fallbackStickerPacks) }
    var serverGifPacks by remember { mutableStateOf(ServerStickerRepository.fallbackGifs) }
    var serverEmojis by remember { mutableStateOf(ServerStickerRepository.defaultEmojis) }
    var isLoadingServerData by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (stickers, gifs, emojis) = ServerStickerRepository.fetchServerMedia()
        serverStickerPacks = stickers
        serverGifPacks = gifs
        serverEmojis = emojis
        isLoadingServerData = false
    }

    // অল প্যাকস সহ ফেভারিট প্যাক সমন্বয়
    val allStickerPacksWithFav = remember(serverStickerPacks, favoriteList) {
        if (favoriteList.isNotEmpty()) {
            listOf(
                DynamicMediaPack(
                    categoryId = -1,
                    name = "Favorites",
                    iconEmoji = "⭐",
                    items = favoriteList
                )
            ) + serverStickerPacks
        } else {
            serverStickerPacks
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(390.dp)
            .navigationBarsPadding(),
        color = Color(0xFF17212B),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, Color(0xFF263342))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. স্লিম সার্চ বার ও ক্লোজ আইকন
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pagerState.currentPage != 0) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp) // স্লিম উচ্চতা
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFF243447))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF8692A6),
                            modifier = Modifier.size(14.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (pagerState.currentPage == 2) "Filter stickers..." else "Filter GIFs...",
                                    color = Color(0xFF8692A6),
                                    fontSize = 11.5.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
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
                                    .size(14.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "😊 Expressive Emojis",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8692A6), modifier = Modifier.size(16.dp))
                }
            }

            // 🏷️ ২. স্লিম ক্যাটাগরি রো (স্টিকার পেজে)
            if (pagerState.currentPage == 2 && allStickerPacksWithFav.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(allStickerPacksWithFav) { idx, pack ->
                        val isSelected = (idx == selectedPackIndex)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF2B5278) else Color(0xFF1E2A38))
                                .clickable { selectedPackIndex = idx }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pack.iconEmoji} ${pack.name}",
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8692A6),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222C3A), thickness = 0.5.dp)

            // 🔀 ৩. ডানে-বামে সোয়াইপ উপযোগী পেজার কন্টেইনার
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        // 📄 PAGE 0: EMOJI
                        0 -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(8),
                                contentPadding = PaddingValues(6.dp),
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
                                        Text(text = emoji, fontSize = 21.sp)
                                    }
                                }
                            }
                        }

                        // 📄 PAGE 1: GIFS
                        1 -> {
                            val currentGifPack = serverGifPacks.firstOrNull()
                            val gifItems = currentGifPack?.items ?: emptyList()

                            LazyVerticalGrid(
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
                                            .combinedClickable(
                                                onClick = { onSendGif(gifUrl) },
                                                onLongClick = { toggleFavoriteSticker(gifUrl) }
                                            )
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

                        // 📄 PAGE 2: STICKERS & VIDEO STICKERS (Auto-Play Without Play Icons)
                        2 -> {
                            val currentPack = allStickerPacksWithFav.getOrElse(selectedPackIndex) { allStickerPacksWithFav.first() }
                            val displayItems = currentPack.items.filter { it.contains(searchQuery, ignoreCase = true) || searchQuery.isEmpty() }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayItems) { stickerUrl ->
                                    val isVideo = stickerUrl.endsWith(".mp4", true) || stickerUrl.endsWith(".webm", true)

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = { onSendSticker(stickerUrl) },
                                                onLongClick = { toggleFavoriteSticker(stickerUrl) }
                                            )
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isVideo) {
                                            // 🎥 কোনো প্লে বাটন ছাড়াই অটোমেটিক লুপড প্লে হবে
                                            AutoPlayGridVideoSticker(
                                                videoUrl = stickerUrl,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
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
                        }
                    }
                }

                if (isLoadingServerData && serverStickerPacks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                    }
                }
            }

            // 🌟 ৪. স্লিম বটম ট্যাব পিল (সোয়াইপের সাথে সিঙ্কড)
            Surface(
                color = Color(0xFF141C24),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF263342))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabPillButton(
                        text = "Emoji",
                        icon = "😊",
                        isSelected = (pagerState.currentPage == 0),
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                    )

                    TabPillButton(
                        text = "GIFs",
                        icon = "🎬",
                        isSelected = (pagerState.currentPage == 1),
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                    )

                    TabPillButton(
                        text = "Stickers",
                        icon = "🧸",
                        isSelected = (pagerState.currentPage == 2),
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                    )
                }
            }
        }
    }
}

/**
 * 🎥 গ্রিডের মধ্যে ভিডিও স্টিকার অটো প্লে করার জন্য স্লিম মিউটেড লুপ প্লেয়ার (কোনো প্লে বাটন ছাড়া)
 */
@Composable
private fun AutoPlayGridVideoSticker(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
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
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF2B5278) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 11.sp)
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFF8692A6),
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
