package com.example.ui.screens.reels.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val ActionGreen = Color(0xFF00E676)
private val CardBg = Color(0xFF131822)
private val BorderColor = Color(0xFF222B3D)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun ReelCoverPickerCard(
    customGalleryThumbUri: Uri?,
    selectedFrameBitmap: Bitmap?,
    videoFrameStrip: List<Bitmap>,
    onUploadCoverClick: () -> Unit,
    onSelectFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier.fillMaxWidth()
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
                    modifier = Modifier.clickable { onUploadCoverClick() }
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
                        bitmap = selectedFrameBitmap.asImageBitmap(),
                        contentDescription = "Frame Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(color = ActionGreen, strokeWidth = 2.dp)
                }
            }

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
                            .clickable { onSelectFrame(bmp) }
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
}
