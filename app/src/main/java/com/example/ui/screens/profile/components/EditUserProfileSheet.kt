@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserProfileDto

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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
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

            // 🎯 সিকিউরিটি ফিক্স: প্রফেশনাল ইউজার-ফ্রেন্ডলি টেক্সট
            Text(
                text = "Tap to change profile photo",
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
}
