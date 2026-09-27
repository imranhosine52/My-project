@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00D166)
private val CardBgDark = Color(0xFF10141F)
private val BorderStrokeColor = Color(0xFF1E2638)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun PageApplicationDialog(
    viewModel: DramaFlixViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pageName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBgDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ১. হেডার
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apply for Creator Page",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            HorizontalDivider(color = BorderStrokeColor, thickness = 0.8.dp)

            // ২. অ্যাডমিন রিভিউ পলিসি নোটিশ
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F2618),
                border = BorderStroke(1.dp, ActionGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = ActionGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Your page application will be reviewed by admin. Once approved, you can start posting reels and stories!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // ৩. এরর মেসেজ ব্যানার
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E1015),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // ৪. পেজ লোগো / অবতার নির্বাচন
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2838))
                    .border(2.dp, ActionGreen.copy(alpha = 0.7f), CircleShape)
                    .clickable { avatarPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedAvatarUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(selectedAvatarUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Avatar",
                        tint = ActionGreen,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "Logo",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text(
                text = "Tap to choose Page Logo / Photo",
                color = TextMuted,
                fontSize = 11.sp
            )

            // ৫. পেজের নাম
            OutlinedTextField(
                value = pageName,
                onValueChange = { pageName = it },
                label = { Text("Page Name *", color = TextMuted) },
                placeholder = { Text("e.g. K-Drama World", color = Color(0xFF475569)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = BorderStrokeColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৬. পেজের ইউনিক হ্যান্ডেল (@handle)
            OutlinedTextField(
                value = handle,
                onValueChange = { 
                    handle = it.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
                },
                label = { Text("Page Handle (@username) *", color = TextMuted) },
                placeholder = { Text("kdrama_world", color = Color(0xFF475569)) },
                prefix = { Text("@", color = ActionGreen, fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = BorderStrokeColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৭. পেজের বায়ো
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 160) bio = it },
                label = { Text("Bio / Description", color = TextMuted) },
                placeholder = { Text("Tell viewers what your page is about...", color = Color(0xFF475569)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = {
                    Text(
                        text = "${bio.length}/160",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ActionGreen,
                    unfocusedBorderColor = BorderStrokeColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ৮. সাবমিট বাটন
            Button(
                onClick = {
                    val cleanName = pageName.trim()
                    val cleanHandle = handle.trim().removePrefix("@")

                    if (cleanName.length < 3) {
                        errorMessage = "Page name must be at least 3 characters."
                        return@Button
                    }
                    if (cleanHandle.length < 3) {
                        errorMessage = "Page handle must be at least 3 characters."
                        return@Button
                    }

                    errorMessage = null
                    isSubmitting = true

                    coroutineScope.launch {
                        val result = viewModel.repository.applyForCreatorPage(
                            pageName = cleanName,
                            handle = cleanHandle,
                            bio = bio.trim().ifBlank { null },
                            avatarUri = selectedAvatarUri
                        )
                        isSubmitting = false

                        if (result.isSuccess) {
                            val res = result.getOrNull()!!
                            Toast.makeText(context, res.message ?: "Page application submitted!", Toast.LENGTH_LONG).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Application failed. Please try again."
                        }
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting Application...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Text("Submit Page for Approval", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
