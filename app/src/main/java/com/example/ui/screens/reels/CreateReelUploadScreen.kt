@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Movie
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorPageDto
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val ActionGreen = Color(0xFF00E676)
private val AlertRed = Color(0xFFFF3B30)
private val BgDark = Color(0xFF0C0F15)
private val CardBg = Color(0xFF131822)
private val BorderColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)

private const val MAX_DURATION_MS = 180_000L // ৩ মিনিট (১৮০ সেকেন্ড)
private const val MAX_SIZE_BYTES = 80L * 1024L * 1024L // ৮০ এমবি

@Composable
fun CreateReelUploadScreen(
    viewModel: DramaFlixViewModel,
    creatorPage: CreatorPageDto?,
    onBackClick: () -> Unit,
    onUploadSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoSizeBytes by remember { mutableLongStateOf(0L) }

    var reelTitle by remember { mutableStateOf("") }
    var reelDescription by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            validationError = null

            coroutineScope.launch {
                val (dur, size, bmp) = extractVideoDetails(context, uri)
                videoDurationMs = dur
                videoSizeBytes = size
                videoThumbnail = bmp

                if (dur > MAX_DURATION_MS) {
                    validationError = "⚠️ Video exceeds 3 minutes! Selected: ${formatDuration(dur)}"
                } else if (size > MAX_SIZE_BYTES) {
                    validationError = "⚠️ Video exceeds 80 MB limit! Selected: ${formatSize(size)}"
                } else {
                    validationError = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ১. টপ বার
            Surface(
                color = Color(0xFF10141E),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
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
                        Text("Create Reel", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
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

            // ২. ফর্ম বডি
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedVideoUri == null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { videoPickerLauncher.launch("video/*") },
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.2.dp, Color(0xFF26334A))
                    ) {
                        // 🎯 এখানে ফিক্স করা হয়েছে: সঠিক Column Alignment & Arrangement
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ActionGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Movie,
                                    contentDescription = "Pick Video",
                                    tint = ActionGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Select Video to Upload", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Max Duration: 3 Minutes • Max Size: 80 MB", color = TextMuted, fontSize = 11.5.sp)
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, if (validationError != null) AlertRed else ActionGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (videoThumbnail != null) {
                                    Image(
                                        bitmap = videoThumbnail!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = ActionGreen)
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = formatDuration(videoDurationMs),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Selected Video", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Duration: ${formatDuration(videoDurationMs)} (Max 3m)", color = TextMuted, fontSize = 11.5.sp)
                                Text("Size: ${formatSize(videoSizeBytes)} (Max 80MB)", color = TextMuted, fontSize = 11.5.sp)

                                TextButton(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Change Video ↻", color = ActionGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = validationError != null) {
                    validationError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2E1015),
                            border = BorderStroke(1.dp, AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                                Text(err, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reelTitle,
                    onValueChange = { reelTitle = it },
                    label = { Text("Reel Caption / Title *", color = TextMuted) },
                    placeholder = { Text("e.g. Unbelievable plot twist! #drama #viral", color = Color(0xFF475569)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reelDescription,
                    onValueChange = { if (it.length <= 250) reelDescription = it },
                    label = { Text("Description & Tags (Optional)", color = TextMuted) },
                    placeholder = { Text("Add more context or credit original creators...", color = Color(0xFF475569)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    supportingText = {
                        Text(
                            text = "${reelDescription.length}/250",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141924),
                    border = BorderStroke(0.8.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Daily limit: You can upload up to 4 reels per day. Videos will be encoded and stored in Cloudflare R2.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // ৩. বটম আপলোড বাটন
            Surface(
                color = Color(0xFF10141E),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val canUpload = selectedVideoUri != null &&
                            reelTitle.isNotBlank() &&
                            validationError == null &&
                            !isUploading

                    Button(
                        onClick = {
                            if (!canUpload || selectedVideoUri == null) return@Button

                            isUploading = true
                            coroutineScope.launch {
                                val result = viewModel.repository.uploadReel(
                                    title = reelTitle.trim(),
                                    description = reelDescription.trim().ifBlank { null },
                                    videoUri = selectedVideoUri!!
                                )
                                isUploading = false

                                if (result.isSuccess) {
                                    Toast.makeText(context, "🎉 Reel uploaded successfully!", Toast.LENGTH_LONG).show()
                                    onUploadSuccess()
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Upload failed. Please check server."
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = canUpload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionGreen,
                            disabledContainerColor = ActionGreen.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Uploading & Encoding to R2...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                                Text("Publish Reel", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun extractVideoDetails(context: Context, uri: Uri): Triple<Long, Long, Bitmap?> = withContext(Dispatchers.IO) {
    var duration = 0L
    var size = 0L
    var bitmap: Bitmap? = null

    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        bitmap = retriever.getFrameAtTime(1000000)
        retriever.release()

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}

    Triple(duration, size, bitmap)
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
