@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val ActionGreen = Color(0xFF00E676)
private val BlueAccent = Color(0xFF2AABEE)
private val SheetBackground = Color(0xFF121724)

/**
 * ✏️ প্রোফাইল নাম ও ছবি পরিবর্তনের জন্য আধুনিক বটম শীট (ইনস্ট্যান্ট ক্রপার সহ)
 */
@Composable
fun EditUserProfileSheet(
    currentUser: UserProfileDto,
    isLoading: Boolean,
    onSave: (newName: String, newAvatarUri: Uri?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(currentUser.displayName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    var rawPickedUri by remember { mutableStateOf<Uri?>(null) }
    var showCropperDialog by remember { mutableStateOf(false) }

    // 🎯 গ্যালারি থেকে ছবি পিক করলে সাথে সাথে ক্রপ ডায়ালগ ওপেন হবে
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            rawPickedUri = uri
            showCropperDialog = true // 👈 সাথে সাথে ক্রপ ডায়ালগ ওপেন
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ড্র্যাগ ইন্ডিকেটর
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF333D4F))
            )

            // হেডার রো
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMutedSlate
                    )
                }
            }

            HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)

            // প্রোফাইল ফটো ও ক্যামেরা ওভারলে
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2838))
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val previewModel = selectedAvatarUri
                    ?: currentUser.avatar?.takeIf { it.isNotBlank() }
                    ?: currentUser.effectiveAvatar?.takeIf { it.isNotBlank() }

                if (previewModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextMutedSlate,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // ক্যামেরা ওভারলে
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "Tap to choose & crop profile photo",
                color = BlueAccent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { photoPickerLauncher.launch("image/*") }
            )

            // ডিসপ্লে নেম ইনপুট
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                label = { Text("Display Name", color = TextMutedSlate) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = CardBorderStroke,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // সেভ বাটন
            Button(
                onClick = {
                    if (inputName.isBlank()) {
                        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(inputName.trim(), selectedAvatarUri)
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Saving Changes...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Text("Save Changes", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ✂️ গ্যালারি থেকে ছবি সিলেক্ট হওয়ামাত্রই ক্রপ ডায়ালগ ওপেন হবে
    if (showCropperDialog && rawPickedUri != null) {
        InAppInteractiveImageCropper(
            imageUri = rawPickedUri!!,
            onDismiss = {
                showCropperDialog = false
                rawPickedUri = null
            },
            onCropSuccess = { croppedUri ->
                selectedAvatarUri = croppedUri // 👈 ক্রপ করা ছবি প্রিভিউতে বসবে
                showCropperDialog = false
                rawPickedUri = null
                Toast.makeText(context, "✓ Photo cropped! Tap 'Save Changes' to update.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * ✂️ ইন-অ্যাপ ক্রপার কম্পোনেন্ট
 */
@Composable
fun InAppInteractiveImageCropper(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropSuccess: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                sourceBitmap = bmp
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // টপ হেডার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Crop Profile Photo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                }

                // সেন্ট্রাল ক্রপ এরিয়া (Pinch & Pan)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (sourceBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF00E676), CircleShape)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = sourceBitmap!!.asImageBitmap(),
                                contentDescription = "Crop Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFF00E676))
                    }
                }

                // নির্দেশনা ও সেভ বাটন বার
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pinch to zoom and drag to adjust position",
                        color = Color(0xFF8E95A5),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E384D))
                        ) {
                            Text("Cancel", color = Color(0xFF8E95A5), fontSize = 13.5.sp)
                        }

                        Button(
                            onClick = {
                                if (sourceBitmap == null || isProcessing) return@Button
                                isProcessing = true

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val bmp = sourceBitmap!!
                                        val dimension = minOf(bmp.width, bmp.height)
                                        val x = ((bmp.width - dimension) / 2).coerceAtLeast(0)
                                        val y = ((bmp.height - dimension) / 2).coerceAtLeast(0)

                                        val croppedBmp = Bitmap.createBitmap(bmp, x, y, dimension, dimension)

                                        val tempFile = File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
                                        val outputStream = FileOutputStream(tempFile)
                                        croppedBmp.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)
                                        outputStream.flush()
                                        outputStream.close()

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            onCropSuccess(Uri.fromFile(tempFile))
                                        }
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(context, "Could not crop image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = sourceBitmap != null && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Crop & Set", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
