@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieFilter
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.repository.ReelsRepository
import kotlinx.coroutines.launch

private val ActionGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val DarkCardBg = Color(0xFF141924)
private val BorderColor = Color(0xFF263346)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun CreateSeriesDialog(
    pageId: Long,
    onDismiss: () -> Unit,
    onSeriesCreated: (playlistId: Int, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ReelsRepository(context) }

    var seriesTitle by remember { mutableStateOf("") }
    var seriesDesc by remember { mutableStateOf("") }
    var selectedPosterUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBannerUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val posterPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedPosterUri = uri }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedBannerUri = uri }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(20.dp)),
            color = DarkCardBg
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // হেডার
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = CyanAccent)
                        Text("Create Mini-Drama Series", color = Color.White, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 0.8.dp)

                // =========================================================================
                // 🖼️ ডুয়েল ইমেজ পিকার (9:16 পোস্টার ও 16:9 ব্যানার)
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ১. ভার্টিক্যাল 9:16 পোস্টার
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Series Poster (9:16) *", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0C1017))
                                .border(1.dp, if (selectedPosterUri != null) CyanAccent else BorderColor, RoundedCornerShape(10.dp))
                                .clickable { posterPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedPosterUri != null) {
                                AsyncImage(
                                    model = selectedPosterUri,
                                    contentDescription = "Poster Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                                    Text("Pick Poster", color = TextMuted, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    // ২. হরিজন্টাল 16:9 ওয়াইডস্ক্রিন ব্যানার
                    Column(
                        modifier = Modifier.weight(1.3f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Notification Banner (16:9)", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0C1017))
                                .border(1.dp, if (selectedBannerUri != null) ActionGreen else BorderColor, RoundedCornerShape(10.dp))
                                .clickable { bannerPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedBannerUri != null) {
                                AsyncImage(
                                    model = selectedBannerUri,
                                    contentDescription = "Banner Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(24.dp))
                                    Text("Pick Banner", color = TextMuted, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }

                // টাইটেল ইনপুট
                OutlinedTextField(
                    value = seriesTitle,
                    onValueChange = { seriesTitle = it },
                    label = { Text("Series Title *", color = TextMuted) },
                    placeholder = { Text("e.g. CEO Secret Love Season 1", color = Color(0xFF475569)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // ডেসক্রিপশন ইনপুট
                OutlinedTextField(
                    value = seriesDesc,
                    onValueChange = { seriesDesc = it },
                    label = { Text("Synopsis / Description (Optional)", color = TextMuted) },
                    placeholder = { Text("Short story summary...", color = Color(0xFF475569)) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // সাবমিট বাটন
                Button(
                    onClick = {
                        val cleanTitle = seriesTitle.trim()
                        if (cleanTitle.length < 2) {
                            Toast.makeText(context, "Please enter a valid series title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        coroutineScope.launch {
                            val res = repository.createSeriesWorkflow(
                                pageId = pageId,
                                title = cleanTitle,
                                description = seriesDesc.trim().ifBlank { null },
                                posterUri = selectedPosterUri,
                                bannerUri = selectedBannerUri
                            )
                            isSubmitting = false

                            if (res.isSuccess) {
                                val createdId = res.getOrNull()?.playlistId ?: 0
                                Toast.makeText(context, "🎉 Series '$cleanTitle' created successfully!", Toast.LENGTH_SHORT).show()
                                onSeriesCreated(createdId, cleanTitle)
                                onDismiss()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Creation failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Create & Publish Series", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
