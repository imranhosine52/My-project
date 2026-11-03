package com.example.ui.screens.localplayer.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoItem
import com.example.util.LocalMediaScanner

private val ElectricBlue = Color(0xFF2979FF)

/**
 * 🖼️ ফুলস্ক্রিন ইমেজ ভিউয়ার
 */
@Composable
fun FullscreenImageViewer(
    images: List<LocalVideoItem>,
    initialIndex: Int,
    onClose: () -> Unit,
    onImageUpdated: () -> Unit
) {
    val context = LocalContext.current
    var imageList by remember { mutableStateOf(images) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { imageList.size }
    )
    val currentImage = imageList.getOrNull(pagerState.currentPage)

    var isStarred by remember(currentImage) {
        mutableStateOf(currentImage?.let { LocalMediaScanner.getStarredPaths(context).contains(it.path) } ?: false)
    }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // ১. সোয়াইপযোগ্য পেজার
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = imageList.getOrNull(page)
            if (item != null) {
                SwipeableZoomableImage(uri = item.contentUri)
            }
        }

        // ২. টপ অ্যাকশন বার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // শেয়ার
                IconButton(onClick = { currentImage?.let { shareSingleFile(context, it) } }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }

                // স্টার
                IconButton(onClick = {
                    currentImage?.let {
                        isStarred = LocalMediaScanner.toggleStarred(context, it.path)
                        onImageUpdated()
                    }
                }) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.Star,
                        contentDescription = "Star",
                        tint = if (isStarred) Color(0xFFFFB300) else Color.White
                    )
                }

                // ট্র্যাশ / ডিলেট
                IconButton(onClick = {
                    currentImage?.let {
                        LocalMediaScanner.moveToTrash(context, it.path)
                        Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        val updated = imageList.toMutableList().apply { removeAt(pagerState.currentPage) }
                        if (updated.isEmpty()) onClose() else imageList = updated
                        onImageUpdated()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }

                // ফাইল ইনফো
                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Details",
                        tint = Color.White
                    )
                }
            }
        }

        // ৩. ফাইল ইনফো ডায়ালগ
        if (showDetailsDialog && currentImage != null) {
            Dialog(onDismissRequest = { showDetailsDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141722))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "File Details",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = Color(0xFF222638), thickness = 0.6.dp)

                        Text("Title: ${currentImage.title}", color = Color.White, fontSize = 13.sp)
                        Text("Size: ${currentImage.formattedSize}", color = Color(0xFF8E95A5), fontSize = 13.sp)
                        Text("Date: ${currentImage.formattedDate}", color = Color(0xFF8E95A5), fontSize = 13.sp)
                        Text("Path: ${currentImage.path}", color = Color(0xFF8E95A5), fontSize = 11.5.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showDetailsDialog = false }) {
                                Text("OK", color = ElectricBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🔍 বাটার-স্মুথ ডাবল-ট্যাপ ও পিঞ্চ-টু-জুম ইমেজ কম্পোনেন্ট
 */
@Composable
fun SwipeableZoomableImage(uri: Any) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
            .pointerInput(scale) {
                if (scale > 1.05f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offsetX += pan.x
                        offsetY += pan.y
                        if (scale <= 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
