@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.reels

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.CreatorPlaylistDto
import com.example.data.model.TrendingHashtagDto
import com.example.data.repository.ReelsRepository
import com.example.service.ReelUploadWorker
import com.example.ui.screens.reels.components.CreateSeriesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private val PureBlack = Color(0xFF000000)
private val CardBg = Color(0xFF141720)
private val BorderColor = Color(0xFF232A3B)
private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val TextMuted = Color(0xFF8E95A5)
private val AlertRed = Color(0xFFFF3B30)

private const val MAX_SERIES_DURATION_MS = 600_000L           // ১০ মিনিট (৬০০ সেকেন্ড)
private const val MAX_SERIES_SIZE_BYTES = 200L * 1024L * 1024L // ২০০ মেগাবাইট

// 🏷️ ক্যাটাগরি তালিকা (Bangla Dub সরানো হয়েছে, Anime এবং Chinese Drama যোগ করা হয়েছে)
val SeriesUploadCategories = listOf(
    "Drama", "Chinese Drama", "Anime", "K-Drama", "Movie & Drama", "Entertainment", "Comedy", "Action", "Romance"
)

@Composable
fun SeriesEpisodePublishScreen(
    trimmedVideoPath: String,
    creatorPage: CreatorPageDto?,
    userId: Int,
    onBackClick: () -> Unit,
    onPublishSuccessExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    // ইনপুট স্টেট
    var captionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SeriesUploadCategories.first()) }
    var episodeNumText by remember { mutableStateOf("1") }
    var isPublicPrivacy by remember { mutableStateOf(true) }

    // 🎯 সার্ভার থেকে আসা রিয়েল ট্রেন্ডিং হ্যাশট্যাগ স্টেট
    var serverTrendingHashtags by remember { mutableStateOf<List<TrendingHashtagDto>>(emptyList()) }
    var isHashtagsLoading by remember { mutableStateOf(false) }
    var showHashtagSuggestions by remember { mutableStateOf(false) }

    // প্লেলিস্ট স্টেট
    var myPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var selectedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var isPlaylistsLoading by remember { mutableStateOf(true) }
    var playlistSearchQuery by remember { mutableStateOf("") }

    // ডায়ালগ কন্ট্রোল
    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    var showCoverSelectionSheet by remember { mutableStateOf(false) }
    var editingPlaylistTarget by remember { mutableStateOf<CreatorPlaylistDto?>(null) }

    // কভার ও থাম্বনেল স্টেট
    var videoFrameStrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customGalleryThumbUri by remember { mutableStateOf<Uri?>(null) }

    // ভিডিও ভ্যালিডেশন স্টেট
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoSizeBytes by remember { mutableLongStateOf(0L) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // ১. সার্ভার থেকে লাইভ ট্রেন্ডিং হ্যাশট্যাগ ফেচ করা
    fun loadLiveTrendingHashtags() {
        isHashtagsLoading = true
        coroutineScope.launch {
            val res = repository.getTrendingHashtags()
            serverTrendingHashtags = res.getOrDefault(emptyList())
            isHashtagsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadLiveTrendingHashtags()
    }

    fun refreshPlaylists() {
        creatorPage?.id?.let { pId ->
            isPlaylistsLoading = true
            coroutineScope.launch {
                val res = repository.getPlaylists(pId)
                myPlaylists = res.getOrDefault(emptyList())
                isPlaylistsLoading = false
                if (selectedPlaylistId == null && myPlaylists.isNotEmpty()) {
                    val first = myPlaylists.first()
                    selectedPlaylistId = first.effectiveId
                    selectedPlaylistTitle = first.title
                    episodeNumText = (first.totalEpisodes + 1).toString()
                }
            }
        }
    }

    LaunchedEffect(creatorPage?.id) {
        refreshPlaylists()
    }

    // ভিডিও মেটাডাটা ও ১০ মিনিট / ২০০ MB ভ্যালিডেশন
    LaunchedEffect(trimmedVideoPath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(trimmedVideoPath)
                if (file.exists()) {
                    videoSizeBytes = file.length()

                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L
                        videoDurationMs = dur

                        val frames = mutableListOf<Bitmap>()
                        val stepUs = (dur * 1000L) / 8L

                        for (i in 0 until 8) {
                            val timeUs = (i * stepUs).coerceAtLeast(0L)
                            val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (bmp != null) frames.add(bmp)
                        }
                        videoFrameStrip = frames
                        selectedFrameBitmap = frames.firstOrNull()

                        if (videoSizeBytes > MAX_SERIES_SIZE_BYTES) {
                            val sizeMb = videoSizeBytes / (1024.0 * 1024.0)
                            validationError = "⚠️ Video exceeds 200 MB limit! (${String.format(Locale.US, "%.1f", sizeMb)} MB)"
                        } else if (dur > MAX_SERIES_DURATION_MS) {
                            val min = (dur / 1000) / 60
                            val sec = (dur / 1000) % 60
                            validationError = "⚠️ Video exceeds 10 minutes limit! (${String.format(Locale.US, "%02d:%02d", min, sec)})"
                        } else {
                            validationError = null
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val customCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customGalleryThumbUri = uri
            selectedFrameBitmap = null
        }
    }

    fun appendHashtagToCaption(tagWithHash: String) {
        val current = captionText
        val cleanTag = if (tagWithHash.startsWith("#")) tagWithHash else "#$tagWithHash"
        captionText = if (current.endsWith("#")) {
            current.dropLast(1) + "$cleanTag "
        } else if (current.endsWith(" ") || current.isEmpty()) {
            "$current$cleanTag "
        } else {
            "$current $cleanTag "
        }
        showHashtagSuggestions = false
    }

    Scaffold(
        topBar = {
            Surface(
                color = PureBlack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text("Post Series Episode", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)

                    if (creatorPage != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CardBg,
                            border = BorderStroke(0.6.dp, CyanAccent)
                        ) {
                            Text(
                                text = "@${creatorPage.handle}",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = PureBlack, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val canPublish = selectedPlaylistId != null && selectedPlaylistId!! > 0 && validationError == null

                    Button(
                        onClick = {
                            if (!canPublish) {
                                if (validationError != null) {
                                    Toast.makeText(context, validationError, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please select or create a series first!", Toast.LENGTH_SHORT).show()
                                }
                                return@Button
                            }

                            val cleanDesc = captionText.trim()
                            val cleanTitle = cleanDesc.lines().firstOrNull()?.trim()?.take(50).takeIf { !it.isNullOrBlank() }
                                ?: (selectedPlaylistTitle?.let { "$it - Episode $episodeNumText" } ?: "Episode $episodeNumText")

                            val hashtagList = Regex("#(\\w+)").findAll(cleanDesc).map { it.value }.toList()
                            val hashtagsString = hashtagList.joinToString(",")

                            coroutineScope.launch {
                                var thumbFilePath: String? = null
                                if (customGalleryThumbUri != null) {
                                    val tempThumb = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                                    context.contentResolver.openInputStream(customGalleryThumbUri!!)?.use { input ->
                                        FileOutputStream(tempThumb).use { output -> input.copyTo(output) }
                                    }
                                    thumbFilePath = tempThumb.absolutePath
                                } else if (selectedFrameBitmap != null) {
                                    val tempThumb = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(tempThumb).use { output ->
                                        selectedFrameBitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, output)
                                    }
                                    thumbFilePath = tempThumb.absolutePath
                                }

                                ReelUploadWorker.enqueueUpload(
                                    context = context,
                                    userId = userId,
                                    pageId = creatorPage?.id ?: 1,
                                    title = cleanTitle,
                                    description = cleanDesc,
                                    hashtags = hashtagsString,
                                    category = selectedCategory,
                                    linkUrl = null,
                                    privacy = if (isPublicPrivacy) "public" else "private",
                                    playlistId = selectedPlaylistId,
                                    episodeNum = episodeNumText.toIntOrNull() ?: 1,
                                    videoPath = trimmedVideoPath,
                                    thumbPath = thumbFilePath
                                )

                                Toast.makeText(context, "🚀 Publishing Episode $episodeNumText in background...", Toast.LENGTH_SHORT).show()
                                onPublishSuccessExit()
                            }
                        },
                        enabled = canPublish,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            disabledContainerColor = CyanAccent.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Publish Episode $episodeNumText", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = PureBlack
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 🌟 ১. টপ লেআউট: [ক্যাপশন ইনপুট] + [ভিডিও প্রিভিউ ফ্রেম]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // বাঁ পাশ: ক্যাপশন এবং স্বচ্ছ "# Hashtags" বাটন
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicTextField(
                        value = captionText,
                        onValueChange = {
                            captionText = it
                            if (it.endsWith("#")) {
                                showHashtagSuggestions = true
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(CyanAccent),
                        decorationBox = { innerTextField ->
                            if (captionText.isEmpty()) {
                                Text(
                                    text = "Add description...",
                                    color = Color(0xFF6B7280),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp, bottom = 6.dp)
                    )

                    // 🎯 ব্যাকগ্রাউন্ড রিমুভ করা পরিচ্ছন্ন "# Hashtags" বাটন
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showHashtagSuggestions = !showHashtagSuggestions }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            text = "# Hashtags",
                            color = CyanAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showHashtagSuggestions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // ডান পাশ: ৯:১৬ ভিডিও প্রিভিউ কার্ড + "Edit cover"
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1D26))
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .clickable { showCoverSelectionSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (customGalleryThumbUri != null) {
                        AsyncImage(
                            model = customGalleryThumbUri,
                            contentDescription = "Cover Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (selectedFrameBitmap != null) {
                        Image(
                            bitmap = selectedFrameBitmap!!.asImageBitmap(),
                            contentDescription = "Frame Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }

                    Text(
                        text = "Preview",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 5.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Edit cover",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 🌟 সার্ভার থেকে রিয়েল ট্রেন্ডিং হ্যাশট্যাগ সাজেশন ড্রপডাউন
            // =========================================================================
            AnimatedVisibility(
                visible = showHashtagSuggestions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBg,
                    border = BorderStroke(0.8.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🔥 Live Trending Hashtags",
                                    color = CyanAccent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isHashtagsLoading) {
                                    CircularProgressIndicator(
                                        color = CyanAccent,
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Suggestions",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showHashtagSuggestions = false }
                            )
                        }

                        HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                        if (isHashtagsLoading && serverTrendingHashtags.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.dp)
                            }
                        } else if (serverTrendingHashtags.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No trending hashtags available",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            serverTrendingHashtags.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { appendHashtagToCaption(item.displayTag) }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tag,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = item.displayTag.removePrefix("#"),
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (item.totalReels > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF1E2838)
                                            ) {
                                                Text(
                                                    text = item.displayReelsBadge,
                                                    color = CyanAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF1E2638)
                                        ) {
                                            Text(
                                                text = item.displayViews,
                                                color = TextMuted,
                                                fontSize = 10.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF1B202D), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.8.dp)

            // ১০ মিনিট / ২০০ MB ভ্যালিডেশন ওয়ার্নিং
            AnimatedVisibility(visible = validationError != null) {
                validationError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF261014),
                        border = BorderStroke(1.dp, AlertRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                            Text(err, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // =========================================================================
            // 📺 ২. প্লেলিস্ট / সিরিজ সেকশন (নিচে নিচে মসৃণ স্লাইডিং লিস্ট)
            // =========================================================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(0.8.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Text("Select Playlist / Series *", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { showCreateSeriesDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+ Create Series", color = ActionGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // সিরিজ সার্চ বার
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B202D),
                        border = BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            BasicTextField(
                                value = playlistSearchQuery,
                                onValueChange = { playlistSearchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(CyanAccent),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (playlistSearchQuery.isEmpty()) {
                                        Text("Search your playlists...", color = Color(0xFF6B7280), fontSize = 12.sp)
                                    }
                                    inner()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (playlistSearchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp).clickable { playlistSearchQuery = "" }
                                )
                            }
                        }
                    }

                    val filteredPlaylists = remember(myPlaylists, playlistSearchQuery) {
                        if (playlistSearchQuery.isBlank()) myPlaylists
                        else myPlaylists.filter { it.title.contains(playlistSearchQuery, ignoreCase = true) }
                    }

                    if (isPlaylistsLoading) {
                        LinearProgressIndicator(color = CyanAccent, modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else if (filteredPlaylists.isEmpty()) {
                        Text(
                            text = if (myPlaylists.isEmpty()) "No series created yet. Tap '+ Create Series' above!" else "No matching series found.",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        // 🎯 নিচে নিচে মসৃণ উল্লম্ব প্লেলিস্ট তালিকা (Vertical Smooth Sliding Cards)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredPlaylists.forEach { pl ->
                                val isSelected = (selectedPlaylistId == pl.effectiveId)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CyanAccent.copy(alpha = 0.16f) else Color(0xFF1B202D),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.2.dp else 0.6.dp,
                                        color = if (isSelected) CyanAccent else BorderColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlaylistId = pl.effectiveId
                                            selectedPlaylistTitle = pl.title
                                            episodeNumText = (pl.totalEpisodes + 1).toString()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // প্লেলিস্টের পোস্টার প্রিভিউ
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 32.dp, height = 44.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black)
                                            ) {
                                                AsyncImage(
                                                    model = pl.effectivePoster ?: pl.effectiveBanner,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = pl.title,
                                                    color = if (isSelected) CyanAccent else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${pl.totalEpisodes} episodes available",
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // ✏️ সম্পূর্ণ পোস্টার ও ব্যানার এডিট করার বাটন
                                            IconButton(
                                                onClick = { editingPlaylistTarget = pl },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Playlist",
                                                    tint = CyanAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = CyanAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 🎯 Category ড্রপডাউন এবং Ep No.
                    // =========================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var categoryExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded },
                            modifier = Modifier.weight(1.8f)
                        ) {
                            OutlinedTextField(
                                value = selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category", color = TextMuted, fontSize = 11.5.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false },
                                modifier = Modifier.background(CardBg)
                            ) {
                                SeriesUploadCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category, color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            selectedCategory = category
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = episodeNumText,
                            onValueChange = { episodeNumText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Ep No.", color = TextMuted, fontSize = 11.5.sp) },
                            leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }
            }

            // =========================================================================
            // 🔒 ৩. প্রাইভেসি সুইচ (Public / Private)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardBg,
                border = BorderStroke(0.6.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (isPublicPrivacy) Icons.Default.Public else Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isPublicPrivacy) "Public Series" else "Private (Draft)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Switch(
                        checked = isPublicPrivacy,
                        onCheckedChange = { isPublicPrivacy = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF1E2638)
                        )
                    )
                }
            }
        }

        // কভার ফটো সিলেকশন বটম শিট
        if (showCoverSelectionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCoverSelectionSheet = false },
                containerColor = CardBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select Episode Cover", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                    Button(
                        onClick = {
                            showCoverSelectionSheet = false
                            customCoverLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload from Gallery", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Text("Or choose a frame from video:", color = TextMuted, fontSize = 12.sp)

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = videoFrameStrip,
                            key = { index, _ -> "frame_$index" }
                        ) { _, frame ->
                            val isSelected = (selectedFrameBitmap == frame && customGalleryThumbUri == null)

                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.8.dp,
                                        color = if (isSelected) CyanAccent else BorderColor,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        customGalleryThumbUri = null
                                        selectedFrameBitmap = frame
                                        showCoverSelectionSheet = false
                                    }
                            ) {
                                Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // ডুয়েল ইমেজ সিরিজ তৈরির ডায়ালগ
        if (showCreateSeriesDialog) {
            CreateSeriesDialog(
                pageId = (creatorPage?.id ?: 1).toLong(),
                onDismiss = { showCreateSeriesDialog = false },
                onSeriesCreated = { createdId, title ->
                    selectedPlaylistId = createdId
                    selectedPlaylistTitle = title
                    episodeNumText = "1"
                    refreshPlaylists()
                    showCreateSeriesDialog = false
                }
            )
        }

        // =========================================================================
        // 🖼️ সম্পূর্ণ পোস্টার (৯:১৬) ও ব্যানার (১৬:৯) সহ প্লেলিস্ট এডিট ডায়ালগ
        // =========================================================================
        editingPlaylistTarget?.let { targetPlaylist ->
            var updatedTitle by remember { mutableStateOf(targetPlaylist.title) }
            var updatedDesc by remember { mutableStateOf(targetPlaylist.description ?: "") }
            var editPosterUri by remember { mutableStateOf<Uri?>(null) }
            var editBannerUri by remember { mutableStateOf<Uri?>(null) }
            var isSavingChanges by remember { mutableStateOf(false) }

            val editPosterPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri -> editPosterUri = uri }

            val editBannerPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri -> editBannerUri = uri }

            Dialog(
                onDismissRequest = { if (!isSavingChanges) editingPlaylistTarget = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Edit Series / Playlist", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { editingPlaylistTarget = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                        // পোস্টার ও ব্যানার রিপ্লেস প্রিভিউ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // ৯:১৬ পোস্টার
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Poster (9:16)", color = TextMuted, fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
                                        .clickable { editPosterPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val posterModel = editPosterUri ?: targetPlaylist.effectivePoster
                                    if (posterModel != null) {
                                        AsyncImage(
                                            model = posterModel,
                                            contentDescription = "Poster",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = CyanAccent)
                                    }
                                }
                            }

                            // ১৬:৯ ব্যানার
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Banner (16:9)", color = TextMuted, fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(1.dp, ActionGreen, RoundedCornerShape(8.dp))
                                        .clickable { editBannerPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bannerModel = editBannerUri ?: targetPlaylist.effectiveBanner
                                    if (bannerModel != null) {
                                        AsyncImage(
                                            model = bannerModel,
                                            contentDescription = "Banner",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ActionGreen)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = updatedTitle,
                            onValueChange = { updatedTitle = it },
                            label = { Text("Series Title *", color = TextMuted, fontSize = 11.5.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = updatedDesc,
                            onValueChange = { updatedDesc = it },
                            label = { Text("Synopsis / Description", color = TextMuted, fontSize = 11.5.sp) },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { editingPlaylistTarget = null },
                                enabled = !isSavingChanges,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, BorderColor)
                            ) {
                                Text("Cancel", color = TextMuted)
                            }

                            Button(
                                onClick = {
                                    if (updatedTitle.isBlank()) {
                                        Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    isSavingChanges = true
                                    coroutineScope.launch {
                                        val res = repository.createSeriesWorkflow(
                                            pageId = (creatorPage?.id ?: 1).toLong(),
                                            title = updatedTitle.trim(),
                                            description = updatedDesc.trim().ifBlank { null },
                                            posterUri = editPosterUri,
                                            bannerUri = editBannerUri
                                        )
                                        isSavingChanges = false

                                        if (res.isSuccess) {
                                            val newId = res.getOrNull()?.playlistId ?: targetPlaylist.effectiveId
                                            myPlaylists = myPlaylists.map {
                                                if (it.effectiveId == targetPlaylist.effectiveId) {
                                                    it.copy(
                                                        title = updatedTitle.trim(),
                                                        description = updatedDesc.trim(),
                                                        posterUrl = res.getOrNull()?.posterUrl ?: it.posterUrl,
                                                        bannerUrl = res.getOrNull()?.bannerUrl ?: it.bannerUrl
                                                    )
                                                } else it
                                            }
                                            if (selectedPlaylistId == targetPlaylist.effectiveId) {
                                                selectedPlaylistTitle = updatedTitle.trim()
                                            }
                                            Toast.makeText(context, "✓ Series updated with new poster/banner!", Toast.LENGTH_SHORT).show()
                                            refreshPlaylists()
                                            editingPlaylistTarget = null
                                        } else {
                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isSavingChanges,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSavingChanges) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
