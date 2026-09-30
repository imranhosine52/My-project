@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorPageDto
import com.example.data.model.CreatorPlaylistDto
import com.example.data.repository.ReelsRepository
import com.example.service.ReelUploadWorker
import com.example.ui.screens.reels.components.ReelCoverPickerCard
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

private val TrendingHashtags = listOf(
    "#series", "#minidrama", "#episode", "#bangladub", "#kdrama", "#viral"
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

    var episodeTitle by remember { mutableStateOf("") }
    var captionText by remember { mutableStateOf("") }
    var episodeNumText by remember { mutableStateOf("1") }
    var isPublicPrivacy by remember { mutableStateOf(true) }

    // প্লেলিস্ট স্টেট
    var myPlaylists by remember { mutableStateOf<List<CreatorPlaylistDto>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var selectedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var isPlaylistsLoading by remember { mutableStateOf(true) }

    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    var newSeriesTitleInput by remember { mutableStateOf("") }
    var newSeriesDescInput by remember { mutableStateOf("") }
    var isCreatingSeries by remember { mutableStateOf(false) }

    // কভার স্টেট
    var videoFrameStrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customGalleryThumbUri by remember { mutableStateOf<Uri?>(null) }

    fun refreshPlaylists() {
        creatorPage?.id?.let { pId ->
            isPlaylistsLoading = true
            coroutineScope.launch {
                val res = repository.getPlaylists(pId)
                myPlaylists = res.getOrDefault(emptyList())
                isPlaylistsLoading = false
                // ডিফল্টভাবে ১ম প্লেলিস্ট সিলেক্ট রাখা
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

    // ফ্রেম এক্সট্র্যাক্টর
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
                        if (bmp != null) frames.add(bmp)
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
            Surface(color = Color(0xFF10141E), shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("Publish Series Episode", color = CyanAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }

                    if (creatorPage != null) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1C2534), border = BorderStroke(0.8.dp, CyanAccent)) {
                            Text(
                                text = "@${creatorPage.handle}",
                                color = CyanAccent,
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
            Surface(color = Color(0xFF10141E), shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val canPublish = selectedPlaylistId != null && selectedPlaylistId!! > 0

                    Button(
                        onClick = {
                            if (!canPublish) {
                                Toast.makeText(context, "⚠️ Please select or create a series first!", Toast.LENGTH_SHORT).show()
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

                                Toast.makeText(context, "🚀 Uploading Series Episode in background...", Toast.LENGTH_SHORT).show()
                                onPublishSuccessExit()
                            }
                        },
                        enabled = canPublish,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            disabledContainerColor = CyanAccent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                            Text("Publish Episode $episodeNumText", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            // =========================================================================
            // 📺 ১. প্লেলিস্ট সিলেকশন (বাধ্যতামূলক সেকশন)
            // =========================================================================
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.2.dp, CyanAccent.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Text("Choose Series / Playlist *", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { showCreateSeriesDialog = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("+ Create New Series", color = ActionGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isPlaylistsLoading) {
                        LinearProgressIndicator(color = CyanAccent, modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else if (myPlaylists.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF261D13),
                            border = BorderStroke(1.dp, Color(0xFFFFB300)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ No series created yet! Tap '+ Create New Series' above to start your playlist.",
                                color = Color(0xFFFFB300),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = if (selectedPlaylistTitle != null) "Selected: $selectedPlaylistTitle" else "Tap a series to select:",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // ক্রিয়েটরের তৈরি সমস্ত প্লেলিস্ট
                        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(myPlaylists, key = { it.effectiveId }) { pl ->
                                val isSelected = (selectedPlaylistId == pl.effectiveId)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CyanAccent.copy(alpha = 0.25f) else Color(0xFF1A2230),
                                    border = BorderStroke(if (isSelected) 1.5.dp else 0.8.dp, if (isSelected) CyanAccent else BorderColor),
                                    modifier = Modifier.clickable {
                                        selectedPlaylistId = pl.effectiveId
                                        selectedPlaylistTitle = pl.title
                                        episodeNumText = (pl.totalEpisodes + 1).toString()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = if (isSelected) CyanAccent else TextMuted, modifier = Modifier.size(16.dp))
                                        Column {
                                            Text(pl.title, color = if (isSelected) CyanAccent else Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            Text("${pl.totalEpisodes} episodes", color = TextMuted, fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // পর্ব নম্বর
                    OutlinedTextField(
                        value = episodeNumText,
                        onValueChange = { episodeNumText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Episode Number (Ep 1, Ep 2, Ep 3...)", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = CyanAccent) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ২. থাম্বনেল সিলেক্টর
            ReelCoverPickerCard(
                customGalleryThumbUri = customGalleryThumbUri,
                selectedFrameBitmap = selectedFrameBitmap,
                videoFrameStrip = videoFrameStrip,
                onUploadCoverClick = { customThumbPickerLauncher.launch("image/*") },
                onSelectFrame = { bmp ->
                    customGalleryThumbUri = null
                    selectedFrameBitmap = bmp
                }
            )

            // ৩. পর্বের নাম ও ক্যাপশন
            OutlinedTextField(
                value = episodeTitle,
                onValueChange = { episodeTitle = it },
                label = { Text("Episode Title *", color = TextMuted) },
                placeholder = { Text("e.g. The CEO Secret Revealed!", color = Color(0xFF475569)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = captionText,
                onValueChange = { captionText = it },
                label = { Text("Episode Synopsis & Hashtags", color = TextMuted) },
                placeholder = { Text("What happens in this episode? Add #kdrama #series", color = Color(0xFF475569)) },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৪. হ্যাশট্যাগ
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TrendingHashtags) { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1A2230),
                        border = BorderStroke(0.6.dp, BorderColor),
                        modifier = Modifier.clickable {
                            captionText = if (captionText.endsWith(" ") || captionText.isEmpty()) "$captionText$tag " else "$captionText $tag "
                        }
                    ) {
                        Text(tag, color = CyanAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }

            // ৫. প্রাইভেসি
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(0.8.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = if (isPublicPrivacy) Icons.Default.Public else Icons.Default.Lock, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                        Column {
                            Text(if (isPublicPrivacy) "Public Series" else "Private (Draft)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("Make episode available in feed & series drawer", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    Switch(
                        checked = isPublicPrivacy,
                        onCheckedChange = { isPublicPrivacy = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CyanAccent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // নতুন সিরিজ ক্রিয়েট ডায়ালগ
        if (showCreateSeriesDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreatingSeries) showCreateSeriesDialog = false },
                containerColor = Color(0xFF141924),
                shape = RoundedCornerShape(16.dp),
                title = { Text("Create Mini-Drama Series", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newSeriesTitleInput,
                            onValueChange = { newSeriesTitleInput = it },
                            label = { Text("Series Title *", color = TextMuted) },
                            placeholder = { Text("e.g. CEO Love Story Season 1", color = Color(0xFF475569)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanAccent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newSeriesDescInput,
                            onValueChange = { newSeriesDescInput = it },
                            label = { Text("Description (Optional)", color = TextMuted) },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanAccent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = newSeriesTitleInput.trim()
                            if (clean.length < 2) {
                                Toast.makeText(context, "Series title must be at least 2 characters", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isCreatingSeries = true
                            coroutineScope.launch {
                                val res = repository.createPlaylist(creatorPage?.id ?: 1, clean, newSeriesDescInput.trim().ifBlank { null })
                                isCreatingSeries = false
                                if (res.isSuccess) {
                                    selectedPlaylistId = res.getOrNull()?.playlistId ?: 0
                                    selectedPlaylistTitle = clean
                                    episodeNumText = "1"
                                    showCreateSeriesDialog = false
                                    newSeriesTitleInput = ""
                                    newSeriesDescInput = ""
                                    refreshPlaylists()
                                    Toast.makeText(context, "🎉 Series created & selected!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isCreatingSeries,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateSeriesDialog = false }, enabled = !isCreatingSeries) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}
