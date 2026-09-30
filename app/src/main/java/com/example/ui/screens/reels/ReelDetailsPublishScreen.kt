@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CreatorPageDto
import com.example.data.model.CreatorPlaylistDto
import com.example.data.repository.ReelsRepository
import com.example.service.ReelUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val ActionGreen = Color(0xFF00E676)
private val BgDark = Color(0xFF0C0F15)
private val CardBg = Color(0xFF131822)
private val BorderColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)
private val CyanAccent = Color(0xFF00E5FF)

private val ReelCategories = listOf(
    "Entertainment", "Drama", "Comedy", "Short Film", "Vlog", "Gaming", "Music", "Action"
)

private val TrendingHashtags = listOf(
    "#viral", "#reels", "#dramaflix", "#trending", "#kdrama", "#bangla", "#shortvideo"
)

@Composable
fun ReelDetailsPublishScreen(
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

    var captionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ReelCategories.first()) }
    var linkUrlText by remember { mutableStateOf("") }
    var isPublicPrivacy by remember { mutableStateOf(true) }

    // 🎯 সিরিজ ও প্লেলিস্ট সংক্রান্ত স্টেটসমূহ
    var isAddToSeriesEnabled by remember { mutableStateOf(false) }
    var myPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var selectedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var episodeNumText by remember { mutableStateOf("1") }
    var isPlaylistsLoading by remember { mutableStateOf(false) }

    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    var newSeriesTitleInput by remember { mutableStateOf("") }
    var newSeriesDescInput by remember { mutableStateOf("") }
    var isCreatingSeries by remember { mutableStateOf(false) }

    var videoFrameStrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customGalleryThumbUri by remember { mutableStateOf<Uri?>(null) }

    // ক্রিয়েটরের পূর্বের সিরিজ লোড
    fun loadPlaylists() {
        creatorPage?.id?.let { pId ->
            if (pId > 0) {
                isPlaylistsLoading = true
                coroutineScope.launch {
                    val res = repository.getPlaylists(pId)
                    myPlaylists = res.getOrDefault(emptyList())
                    isPlaylistsLoading = false
                }
            }
        }
    }

    LaunchedEffect(creatorPage?.id) {
        loadPlaylists()
    }

    // ভিডিও ফ্রেম এক্সট্র্যাক্টর
    LaunchedEffect(trimmedVideoPath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(trimmedVideoPath)
                if (file.exists()) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L

                    val frames = mutableListOf<Bitmap>()
                    val stepUs = (durationMs * 1000L) / 8L

                    for (i in 0 until 8) {
                        val timeUs = (i * stepUs).coerceAtLeast(0L)
                        val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bmp != null) {
                            frames.add(bmp)
                        }
                    }
                    retriever.release()
                    videoFrameStrip = frames
                    selectedFrameBitmap = frames.firstOrNull()
                }
            } catch (_: Exception) {}
        }
    }

    val customThumbPickerLauncher = rememberLauncherForActivityResult(
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
                color = Color(0xFF10141E),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("Reel Details", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
                    }

                    if (creatorPage != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1C2534),
                            border = BorderStroke(0.8.dp, ActionGreen)
                        ) {
                            Text(
                                text = "@${creatorPage.handle}",
                                color = ActionGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF10141E),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            val cleanTitle = captionText.lines().firstOrNull()?.trim() ?: "My Reel"
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

                                val finalPlaylistId = if (isAddToSeriesEnabled) selectedPlaylistId else null
                                val finalEpisodeNum = if (isAddToSeriesEnabled) (episodeNumText.toIntOrNull() ?: 1) else 1

                                ReelUploadWorker.enqueueUpload(
                                    context = context,
                                    userId = userId,
                                    pageId = creatorPage?.id ?: 1,
                                    title = cleanTitle,
                                    description = cleanDesc,
                                    hashtags = hashtagsString,
                                    category = selectedCategory,
                                    linkUrl = linkUrlText.trim().ifBlank { null },
                                    privacy = if (isPublicPrivacy) "public" else "private",
                                    playlistId = finalPlaylistId,
                                    episodeNum = finalEpisodeNum,
                                    videoPath = trimmedVideoPath,
                                    thumbPath = thumbFilePath
                                )

                                Toast.makeText(context, "🚀 Uploading Reel in background...", Toast.LENGTH_SHORT).show()
                                onPublishSuccessExit()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                            Text(
                                text = "Share Reel",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = BgDark
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ১. থাম্বনেল সিলেক্টর কার্ড
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cover / Thumbnail", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E2838),
                            border = BorderStroke(0.8.dp, ActionGreen.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable { customThumbPickerLauncher.launch("image/*") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(13.dp))
                                Text("Upload Cover", color = ActionGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customGalleryThumbUri != null) {
                            AsyncImage(
                                model = customGalleryThumbUri,
                                contentDescription = "Custom Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (selectedFrameBitmap != null) {
                            Image(
                                bitmap = selectedFrameBitmap!!.asImageBitmap(),
                                contentDescription = "Frame Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp)
                        }
                    }

                    Text("Or slide to pick a video frame:", color = TextMuted, fontSize = 11.5.sp)

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(videoFrameStrip) { bmp ->
                            val isSelected = (selectedFrameBitmap == bmp && customGalleryThumbUri == null)

                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 66.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.8.dp,
                                        color = if (isSelected) ActionGreen else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        customGalleryThumbUri = null
                                        selectedFrameBitmap = bmp
                                    }
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // ২. ক্যাপশন ইনপুট
            OutlinedTextField(
                value = captionText,
                onValueChange = { captionText = it },
                label = { Text("Write a caption...", color = TextMuted) },
                placeholder = { Text("Share what your reel is about and add #hashtags", color = Color(0xFF475569)) },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৩. হ্যাশট্যাগ চিপস
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(TrendingHashtags) { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1A2230),
                        border = BorderStroke(0.6.dp, BorderColor),
                        modifier = Modifier.clickable {
                            captionText = if (captionText.endsWith(" ") || captionText.isEmpty()) {
                                "$captionText$tag "
                            } else {
                                "$captionText $tag "
                            }
                        }
                    ) {
                        Text(
                            text = tag,
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ৪. ক্যাটাগরি রো
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReelCategories) { cat ->
                        val isSelected = (selectedCategory == cat)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ActionGreen.copy(alpha = 0.15f) else CardBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.2.dp else 0.8.dp,
                                color = if (isSelected) ActionGreen else BorderColor
                            ),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) ActionGreen else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 📺 ৫. ADD TO SERIES / PLAYLIST SECTION (মিনি-ড্রামা সিরিজ সিলেকশন)
            // =========================================================================
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(0.8.dp, if (isAddToSeriesEnabled) CyanAccent.copy(alpha = 0.7f) else BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = if (isAddToSeriesEnabled) CyanAccent else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text("Add to Series / Playlist", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Organize related reels into a serial mini-drama", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Switch(
                            checked = isAddToSeriesEnabled,
                            onCheckedChange = { isAddToSeriesEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ActionGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF1E2838)
                            )
                        )
                    }

                    AnimatedVisibility(visible = isAddToSeriesEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(color = BorderColor, thickness = 0.6.dp)

                            // হেডার ও "+ Create New Series" বাটন
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedPlaylistTitle != null) "Series: $selectedPlaylistTitle" else "Select a Series:",
                                    color = CyanAccent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    onClick = { showCreateSeriesDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+ New Series", color = ActionGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // ক্রিয়েটরের বর্তমান প্লেলিস্ট চিপস তালিকা
                            if (isPlaylistsLoading) {
                                LinearProgressIndicator(color = CyanAccent, modifier = Modifier.fillMaxWidth().height(2.dp))
                            } else if (myPlaylists.isEmpty()) {
                                Text(
                                    text = "No series created yet. Tap '+ New Series' to create your first mini-drama!",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(myPlaylists, key = { it.effectiveId }) { pl ->
                                        val isSelected = (selectedPlaylistId == pl.effectiveId)

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else Color(0xFF1A2230),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.2.dp else 0.8.dp,
                                                color = if (isSelected) CyanAccent else BorderColor
                                            ),
                                            modifier = Modifier.clickable {
                                                selectedPlaylistId = pl.effectiveId
                                                selectedPlaylistTitle = pl.title
                                                episodeNumText = (pl.totalEpisodes + 1).toString()
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = if (isSelected) CyanAccent else TextMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = pl.title,
                                                    color = if (isSelected) CyanAccent else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // পর্ব নম্বর (Episode Number) ইনপুট
                            OutlinedTextField(
                                value = episodeNumText,
                                onValueChange = { episodeNumText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Episode Number (Ep 1, Ep 2...)", color = TextMuted) },
                                placeholder = { Text("1", color = Color.Gray) },
                                leadingIcon = {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ActionGreen,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ৬. এক্সটার্নাল লিংক ইনপুট
            OutlinedTextField(
                value = linkUrlText,
                onValueChange = { linkUrlText = it },
                label = { Text("Add External Link / Button (Optional)", color = TextMuted) },
                placeholder = { Text("https://example.com/product", color = Color(0xFF475569)) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = CyanAccent) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৭. প্রাইভেসি সুইচ
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(0.8.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isPublicPrivacy) Icons.Default.Public else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isPublicPrivacy) ActionGreen else Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = if (isPublicPrivacy) "Public (Everyone can watch)" else "Private (Only you can watch)",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose who can see this reel in their feed",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isPublicPrivacy,
                        onCheckedChange = { isPublicPrivacy = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ActionGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF1E2838)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // =========================================================================
        // 🆕 নতুন মিনি-ড্রামা সিরিজ তৈরির ডায়ালগ
        // =========================================================================
        if (showCreateSeriesDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreatingSeries) showCreateSeriesDialog = false },
                containerColor = Color(0xFF141924),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = CyanAccent)
                        Text("Create Mini-Drama Series", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Create a playlist title to group your series episodes together.",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )

                        OutlinedTextField(
                            value = newSeriesTitleInput,
                            onValueChange = { newSeriesTitleInput = it },
                            label = { Text("Series Title *", color = TextMuted) },
                            placeholder = { Text("e.g. CEO Love Story Season 1", color = Color(0xFF475569)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ActionGreen,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newSeriesDescInput,
                            onValueChange = { newSeriesDescInput = it },
                            label = { Text("Description (Optional)", color = TextMuted) },
                            placeholder = { Text("Short synopsis about this drama...", color = Color(0xFF475569)) },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ActionGreen,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanTitle = newSeriesTitleInput.trim()
                            if (cleanTitle.length < 2) {
                                Toast.makeText(context, "Series title must be at least 2 characters", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val pId = creatorPage?.id ?: 1
                            isCreatingSeries = true

                            coroutineScope.launch {
                                val res = repository.createPlaylist(
                                    pageId = pId,
                                    title = cleanTitle,
                                    description = newSeriesDescInput.trim().ifBlank { null }
                                )
                                isCreatingSeries = false

                                if (res.isSuccess) {
                                    val createdId = res.getOrNull()?.playlistId
                                    selectedPlaylistId = createdId
                                    selectedPlaylistTitle = cleanTitle
                                    episodeNumText = "1"
                                    showCreateSeriesDialog = false
                                    newSeriesTitleInput = ""
                                    newSeriesDescInput = ""
                                    loadPlaylists() // লিস্ট রিফ্রেশ
                                    Toast.makeText(context, "🎉 Series '$cleanTitle' created!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = res.exceptionOrNull()?.message ?: "Failed to create series"
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isCreatingSeries,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        if (isCreatingSeries) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Create & Select", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCreateSeriesDialog = false },
                        enabled = !isCreatingSeries
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}
