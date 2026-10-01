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
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.model.CreatorPlaylistDto
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

private val QuickHashtags = listOf("#series", "#minidrama", "#episode", "#bangladub", "#kdrama", "#viral")

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
    var episodeTitle by remember { mutableStateOf("") }
    var episodeNumText by remember { mutableStateOf("1") }
    var isPublicPrivacy by remember { mutableStateOf(true) }

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
                    retriever.release()
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

                            val cleanTitle = episodeTitle.trim().ifBlank { "Episode $episodeNumText" }
                            val cleanDesc = captionText.trim()
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
                                    category = "Drama",
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
            // 🌟 ১. দুই নম্বর ছবির হুবহু টপ লেআউট: [ক্যাপশন ইনপুট] + [ভিডিও প্রিভিউ ফ্রেম]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // বাঁ পাশ: ক্যাপশন এবং হ্যাশট্যাগ ইনপুট
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
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
                            .padding(vertical = 4.dp)
                    )

                    // হ্যাশট্যাগ চিপস (২ নম্বর ছবির হুবহু স্টাইল)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(QuickHashtags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CardBg,
                                border = BorderStroke(0.6.dp, BorderColor),
                                modifier = Modifier.clickable {
                                    captionText = if (captionText.endsWith(" ") || captionText.isEmpty()) "$captionText$tag " else "$captionText $tag "
                                }
                            ) {
                                Text(
                                    text = tag,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ডান পাশ: ২ নম্বর ছবির হুবহু ৯:১৬ ভিডিও প্রিভিউ কার্ড + "Edit cover"
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .height(145.dp)
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

                    // ওপরে "Preview" টেক্সট
                    Text(
                        text = "Preview",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 5.dp)
                    )

                    // নিচে ২ নম্বর ছবির মতো "Edit cover" বাটন
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
            // 📺 ২. প্লেলিস্ট / সিরিজ ম্যানেজমেন্ট সেকশন (সার্চ, ফিল্টার ও এডিট সহ)
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
                    OutlinedTextField(
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        placeholder = { Text("Search your playlists...", color = Color(0xFF6B7280), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    )

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
                        // প্লেলিস্ট হরিজন্টাল রো
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredPlaylists, key = { it.effectiveId }) { pl ->
                                val isSelected = (selectedPlaylistId == pl.effectiveId)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CyanAccent.copy(alpha = 0.18f) else Color(0xFF1B202D),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.2.dp else 0.6.dp,
                                        color = if (isSelected) CyanAccent else BorderColor
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedPlaylistId = pl.effectiveId
                                        selectedPlaylistTitle = pl.title
                                        episodeNumText = (pl.totalEpisodes + 1).toString()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
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

                                        Column {
                                            Text(
                                                text = pl.title,
                                                color = if (isSelected) CyanAccent else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("${pl.totalEpisodes} episodes", color = TextMuted, fontSize = 10.sp)
                                        }

                                        // এডিট বাটন
                                        IconButton(
                                            onClick = { editingPlaylistTarget = pl },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Playlist", tint = TextMuted, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // পর্ব নম্বর ইনপুট
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = episodeTitle,
                            onValueChange = { episodeTitle = it },
                            label = { Text("Episode Title", color = TextMuted, fontSize = 11.5.sp) },
                            placeholder = { Text("e.g. The Truth Revealed", color = Color(0xFF6B7280), fontSize = 11.5.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.8f)
                        )

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

        // =========================================================================
        // 🖼️ কভার ফটো সিলেকশন বটম শিট (Edit Cover এ চাপ দিলে খুলবে)
        // =========================================================================
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

                    // অপশন ১: গ্যালারি থেকে আপলোড
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

                    // অপশন ২: ভিডিও ফ্রেম স্ট্রিপ
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(videoFrameStrip) { frame ->
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

        // =========================================================================
        // 🌟 নতুন ডুয়েল ইমেজ সিরিজ ডায়ালগ (9:16 পোস্টার ও 16:9 ব্যানার)
        // =========================================================================
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
        // ✏️ প্লেলিস্ট টাইটেল এডিট ডায়ালগ
        // =========================================================================
        editingPlaylistTarget?.let { targetPlaylist ->
            var updatedTitle by remember { mutableStateOf(targetPlaylist.title) }

            AlertDialog(
                onDismissRequest = { editingPlaylistTarget = null },
                containerColor = CardBg,
                shape = RoundedCornerShape(12.dp),
                title = { Text("Edit Series Title", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = updatedTitle,
                        onValueChange = { updatedTitle = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (updatedTitle.isNotBlank()) {
                                myPlaylists = myPlaylists.map {
                                    if (it.effectiveId == targetPlaylist.effectiveId) it.copy(title = updatedTitle.trim())
                                    else it
                                }
                                if (selectedPlaylistId == targetPlaylist.effectiveId) {
                                    selectedPlaylistTitle = updatedTitle.trim()
                                }
                                Toast.makeText(context, "Series updated locally", Toast.LENGTH_SHORT).show()
                            }
                            editingPlaylistTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingPlaylistTarget = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}
