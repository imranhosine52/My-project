@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile.components

import android.app.Activity
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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
 * ✏️ প্রোফাইল নাম ও ছবি পরিবর্তনের জন্য আধুনিক বটম শীট
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
            showCropperDialog = true // 👈 ক্রপ ডায়ালগ ওপেন
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
                selectedAvatarUri = croppedUri // 👈 ক্রপ করা ছবি প্রিভিউতে সেট হবে
                showCropperDialog = false
                rawPickedUri = null
                Toast.makeText(context, "✓ Photo cropped! Tap 'Save Changes' to update.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * ✂️ ১০০% দৃশ্যমান ও নিরাপদ ইন-অ্যাপ ক্রপার কম্পোনেন্ট
 * (টপ বারে [✓ Save] বাটন এবং বটমেও বড় [Crop & Set Profile Photo] বাটন রয়েছে)
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

    fun performCrop() {
        if (sourceBitmap == null || isProcessing) return
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
                croppedBmp.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
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
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
                ?: (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // =========================================================================
            // 🔝 ১. টপ হেডার বার: [✕ Cancel]  [ Crop Profile Photo ]  [ ✓ Save ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Text(
                    text = "Crop Profile Photo",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // 🎯 উপরেও সবসময় দৃশ্যমান সেভ বাটন
                Button(
                    onClick = { performCrop() },
                    enabled = sourceBitmap != null && !isProcessing,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Text("Save", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =========================================================================
            // ✂️ ২. সেন্ট্রাল ক্রপ এরিয়া (Pinch to Zoom & Drag)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sourceBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, ActionGreen, CircleShape)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4.5f)
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
                    CircularProgressIndicator(color = ActionGreen)
                }
            }

            // =========================================================================
            // 🔘 ৩. নিচে ফিক্সড ও সুরক্ষিত সেভ বাটন বার
            // =========================================================================
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pinch to zoom and drag image to fit circle",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Button(
                    onClick = { performCrop() },
                    enabled = sourceBitmap != null && !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cropping & Saving...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Crop & Set Profile Photo", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
