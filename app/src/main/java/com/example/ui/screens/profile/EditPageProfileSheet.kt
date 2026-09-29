@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorPageDto
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF000000)
private val CardDarkBg = Color(0xFF16181F)
private val BorderStrokeColor = Color(0xFF222634)
private val TextMuted = Color(0xFF8692A6)
private val BlueText = Color(0xFF00B0FF)
private val ActionGreen = Color(0xFF00E676)

@Composable
fun EditPageProfileSheet(
    page: CreatorPageDto,
    repository: ReelsRepository,
    onBackClick: () -> Unit,
    onPageUpdated: (CreatorPageDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pageName by remember { mutableStateOf(page.pageName) }
    var handle by remember { mutableStateOf(page.handle.removePrefix("@")) }
    var bio by remember { mutableStateOf(page.bio ?: "Full Drama Link 👉 https://playdramaflix.com/page/${page.handle.removePrefix("@")}") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    var isSaving by remember { mutableStateOf(false) }

    // ডায়ালগ কন্ট্রোল স্টেট
    var editingFieldType by remember { mutableStateOf<String?>(null) } // "name", "username", "bio"

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    val pageShareLink = remember(handle) {
        "playdramaflix.com/@${handle.trim().removePrefix("@")}"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // =========================================================================
            // 🔝 ১. স্ক্রিনশট ১-এর হেডার বার: [ < ]  Edit profile  [ Save ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Edit profile",
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold
                )

                // 🎯 সেভ বাটন (ক্লিক করলে VPS 2 ও VPS 1 উভয় জায়গায় নিরাপদ আপডেট হবে)
                Text(
                    text = if (isSaving) "Saving..." else "Save",
                    color = if (isSaving) TextMuted else ActionGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = !isSaving) {
                            if (pageName.isBlank() || handle.isBlank()) {
                                Toast.makeText(context, "Name and Username cannot be empty", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            isSaving = true
                            coroutineScope.launch {
                                var newAvatarUrl: String? = page.avatar

                                // ১. যদি নতুন ছবি নির্বাচন করা হয়ে থাকে, তবে VPS 2-এ আপলোড
                                if (selectedAvatarUri != null) {
                                    val avatarUploadResult = repository.uploadUserAvatar(
                                        imageUri = selectedAvatarUri!!,
                                        fallbackUserId = page.userId
                                    )
                                    if (avatarUploadResult.isSuccess) {
                                        newAvatarUrl = avatarUploadResult.getOrNull()
                                    }
                                }

                                // ২. পেজের প্রোফাইল ডাটা সার্ভারে আপডেট ("Invalid action" মুক্ত)
                                val result = repository.updateCreatorPageProfile(
                                    pageId = page.id,
                                    pageName = pageName.trim(),
                                    handle = handle.trim(),
                                    bio = bio.trim(),
                                    customLink = bio.trim(),
                                    avatarUri = selectedAvatarUri,
                                    fallbackUserId = page.userId
                                )
                                isSaving = false

                                if (result.isSuccess) {
                                    Toast.makeText(context, "✓ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                    val updatedPage = page.copy(
                                        pageName = pageName.trim(),
                                        handle = handle.trim(),
                                        bio = bio.trim(),
                                        avatar = newAvatarUrl ?: page.avatar
                                    )
                                    onPageUpdated(updatedPage)
                                    onBackClick()
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Failed to update profile"
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 📷 ২. স্ক্রিনশট ১-এর সেন্ট্রাল ফটো + ক্যামেরা ও "Change photo"
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .clickable { avatarPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarPreview = selectedAvatarUri ?: page.avatar

                    if (avatarPreview != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarPreview)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF262A36)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pageName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // স্ক্রিনশট ১-এর মতো সেন্টারে কালো ডার্ক কাটিং ও ক্যামেরা আইকন
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = "Change photo",
                    color = BlueText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { avatarPickerLauncher.launch("image/*") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // 📋 ৩. গ্রুপড ইনফো কার্ড (Name, Username, Link)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDarkBg,
                border = BorderStroke(0.6.dp, BorderStrokeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ১. Name রো
                    ProfileEditRowItem(
                        label = "Name",
                        value = pageName,
                        showArrow = true,
                        onClick = { editingFieldType = "name" }
                    )

                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.6.dp)

                    // ২. Username রো
                    ProfileEditRowItem(
                        label = "Username",
                        value = handle,
                        showArrow = true,
                        onClick = { editingFieldType = "username" }
                    )

                    HorizontalDivider(color = BorderStrokeColor, thickness = 0.6.dp)

                    // ৩. Page Link রো (কপি আইকন সহ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Page Link", "https://$pageShareLink"))
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = pageShareLink,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // 📝 ৪. Basic info সেকশন (Bio)
            // =========================================================================
            Text(
                text = "Basic info",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDarkBg,
                border = BorderStroke(0.6.dp, BorderStrokeColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ProfileEditRowItem(
                    label = "Bio",
                    value = bio,
                    showArrow = true,
                    onClick = { editingFieldType = "bio" }
                )
            }
        }

        // =========================================================================
        // ✏️ ভ্যালু এডিট করার পপ-আপ ডায়ালগ
        // =========================================================================
        editingFieldType?.let { fieldType ->
            var tempInput by remember {
                mutableStateOf(
                    when (fieldType) {
                        "name" -> pageName
                        "username" -> handle
                        else -> bio
                    }
                )
            }

            val fieldTitle = when (fieldType) {
                "name" -> "Edit Page Name"
                "username" -> "Edit Username"
                else -> "Edit Bio & Drama Link"
            }

            Dialog(onDismissRequest = { editingFieldType = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
                    border = BorderStroke(1.dp, BorderStrokeColor)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(fieldTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = tempInput,
                            onValueChange = {
                                tempInput = if (fieldType == "username") {
                                    it.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_.]"), "")
                                } else it
                            },
                            singleLine = (fieldType != "bio"),
                            maxLines = if (fieldType == "bio") 3 else 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ActionGreen,
                                unfocusedBorderColor = BorderStrokeColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editingFieldType = null }) {
                                Text("Cancel", color = TextMuted)
                            }
                            Button(
                                onClick = {
                                    when (fieldType) {
                                        "name" -> pageName = tempInput.trim()
                                        "username" -> handle = tempInput.trim().removePrefix("@")
                                        "bio" -> bio = tempInput.trim()
                                    }
                                    editingFieldType = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                            ) {
                                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditRowItem(
    label: String,
    value: String,
    showArrow: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(90.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (showArrow) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
