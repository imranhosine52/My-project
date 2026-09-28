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

    var captionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ReelCategories.first()) }
    var linkUrlText by remember { mutableStateOf("") }
    var isPublicPrivacy by remember { mutableStateOf(true) }

    // থাম্বনেইল স্টেট
    var videoFrameStrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customGalleryThumbUri by remember { mutableStateOf<Uri?>(null) }
    var selectedThumbTimeUs by remember { mutableLongStateOf(0L) }

    // ১. ভিডিও ফ্রেম স্ক্রাবার জেনারেটর (ভিডিও থেকে ৮টি ফ্রেম নিয়ে স্ট্রিপ তৈরি)
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
                        val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.SEEK_TO_CLOSEST_SYNC)
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

    // ২. গ্যালারি থেকে কাস্টম থাম্বনেইল পিকার
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
            // =========================================================================
            // 🚀 "Share Reel" বাটন: ট্যাপ করলেই WorkManager ব্যাকগ্রাউন্ডে কাজ করবে
            // =========================================================================
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

                            // হ্যাশট্যাগ আলাদা করা
                            val hashtagList = Regex("#(\\w+)").findAll(cleanDesc).map { it.value }.toList()
                            val hashtagsString = hashtagList.joinToString(",")

                            coroutineScope.launch {
                                // থাম্বনেইল ফাইল সাময়িকভাবে সেভ করা
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

                                // 🎯 WorkManager ব্যাকগ্রাউন্ড সার্ভিস ট্রিগার করা
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
                                    videoPath = trimmedVideoPath,
                                    thumbPath = thumbFilePath
                                )

                                Toast.makeText(context, "🚀 Uploading Reel in background...", Toast.LENGTH_SHORT).show()

                                // ব্যবহারকারীকে ফ্রিজ না করে অবিলম্বে হোম/ফিডে ফিরিয়ে দেওয়া
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
            // =========================================================================
            // 🖼️ ১. থাম্বনেইল সিলেকশন (ভিডিও ফ্রেম স্ক্রাবার + কাস্টম থাম্বনেইল আপলোড)
            // =========================================================================
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

                    // নির্বাচিত থাম্বনেইল বড় প্রিভিউ
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

                    // 🎬 ভিডিও ফ্রেম স্ক্রাবার (Horizontally Scrollable Strip)
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

            // =========================================================================
            // 📝 ২. ক্যাপশন ও রিয়েল-টাইম হ্যাশট্যাগ চিপস
            // =========================================================================
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

            // ট্রেন্ডিং হ্যাশট্যাগ কুইক-ইনসার্ট চিপস
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
                            color = Color(0xFF00E5FF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 🏷️ ৩. ক্যাটাগরি সিলেক্টর (Entertainment, Drama, Comedy ইত্যাদি)
            // =========================================================================
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
            // 🔗 ৪. অপশনাল ওয়েবসাইট বা ড্রামা লিংক
            // =========================================================================
            OutlinedTextField(
                value = linkUrlText,
                onValueChange = { linkUrlText = it },
                label = { Text("Add External Link / Button (Optional)", color = TextMuted) },
                placeholder = { Text("https://example.com/product", color = Color(0xFF475569)) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF00E5FF)) },
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

            // =========================================================================
            // 🔒 ৫. প্রাইভেসি সেটিংস (Public / Private)
            // =========================================================================
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
    }
}
