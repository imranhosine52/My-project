@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorPageDto
import com.example.data.repository.ReelsRepository
import com.example.service.ReelUploadWorker
import com.example.ui.screens.reels.components.ReelCoverPickerCard
import com.example.ui.screens.reels.components.ReelSeriesSelectionCard
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

    // সিরিজ স্টেট
    var isAddToSeriesEnabled by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var selectedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var episodeNumText by remember { mutableStateOf("1") }

    // কভার ফ্রেম স্টেট
    var videoFrameStrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customGalleryThumbUri by remember { mutableStateOf<Uri?>(null) }

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
                            Text("Share Reel", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            // ১. কভার সিলেক্টর কার্ড (আলাদা কম্পোনেন্ট)
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
                            captionText = if (captionText.endsWith(" ") || captionText.isEmpty()) "$captionText$tag " else "$captionText $tag "
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
                            border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, if (isSelected) ActionGreen else BorderColor),
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

            // 📺 ৫. মিনি-ড্রামা সিরিজ সিলেকশন কার্ড (আলাদা কম্পোনেন্ট)
            ReelSeriesSelectionCard(
                pageId = creatorPage?.id ?: 1,
                isAddToSeriesEnabled = isAddToSeriesEnabled,
                onToggleSeries = { isAddToSeriesEnabled = it },
                selectedPlaylistId = selectedPlaylistId,
                selectedPlaylistTitle = selectedPlaylistTitle,
                onSelectPlaylist = { id, title, nextEp ->
                    selectedPlaylistId = id
                    selectedPlaylistTitle = title
                    episodeNumText = nextEp.toString()
                },
                episodeNumText = episodeNumText,
                onEpisodeNumChange = { episodeNumText = it.filter { ch -> ch.isDigit() } },
                repository = repository
            )

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
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                            Text(if (isPublicPrivacy) "Public" else "Private", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("Choose who can see this reel in their feed", color = TextMuted, fontSize = 11.sp)
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
    }
}
