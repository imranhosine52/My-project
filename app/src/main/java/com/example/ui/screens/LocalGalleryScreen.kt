@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import com.example.ui.theme.*
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SafeGreen = Color(0xFF00D166)
private val CardDarkBg = Color(0xFF121622)

private enum class GalleryTab {
    FOLDERS,
    ALL_VIDEOS
}

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videoItem: LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(GalleryTab.FOLDERS) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var allVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🔒 পারমিশন চেক লজিক (Android 13+ vs Old Android)
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun loadLocalMedia() {
        if (!hasPermission) return
        coroutineScope.launch {
            isLoading = true
            allVideos = LocalMediaScanner.getAllVideos(context)
            folders = LocalMediaScanner.getVideoFolders(context)
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            loadLocalMedia()
        } else {
            Toast.makeText(context, "Storage permission is needed to play phone videos", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            loadLocalMedia()
        } else {
            permissionLauncher.launch(requiredPermission)
        }
    }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 🔝 Top App Bar
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark)
                                    .clickable {
                                        if (selectedFolder != null) selectedFolder = null else onBackClick()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (selectedFolder != null) selectedFolder!!.folderName else "Gallery Video Player",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedFolder != null) "${selectedFolder!!.videoCount} Videos • ${selectedFolder!!.formattedTotalSize}" else "100% Offline & Ad-Free",
                                    color = SafeGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Search Toggle Button
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceVariantDark)
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 🔍 Search Bar Dropdown
                    AnimatedVisibility(visible = isSearchActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceVariantDark)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("Search local video title or folder...", color = TextMuted, fontSize = 13.5.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(SafeGreen),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 🗂️ Tabs Header (Folders vs All Videos)
                    if (selectedFolder == null) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Folders Tab
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = GalleryTab.FOLDERS }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = if (selectedTab == GalleryTab.FOLDERS) SafeGreen else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Folders (${folders.size})",
                                        color = if (selectedTab == GalleryTab.FOLDERS) SafeGreen else TextMuted,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (selectedTab == GalleryTab.FOLDERS) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(2.5.dp)
                                        .background(if (selectedTab == GalleryTab.FOLDERS) SafeGreen else Color.Transparent)
                                )
                            }

                            // All Videos Tab
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = GalleryTab.ALL_VIDEOS }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VideoLibrary,
                                        contentDescription = null,
                                        tint = if (selectedTab == GalleryTab.ALL_VIDEOS) SafeGreen else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "All Videos (${allVideos.size})",
                                        color = if (selectedTab == GalleryTab.ALL_VIDEOS) SafeGreen else TextMuted,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (selectedTab == GalleryTab.ALL_VIDEOS) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(2.5.dp)
                                        .background(if (selectedTab == GalleryTab.ALL_VIDEOS) SafeGreen else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            // 📱 Media Content Area
            if (!hasPermission) {
                PermissionRequestNotice(
                    onRequestPermission = { permissionLauncher.launch(requiredPermission) }
                )
            } else if (isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SafeGreen, strokeWidth = 2.5.dp)
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            allVideos = LocalMediaScanner.getAllVideos(context)
                            folders = LocalMediaScanner.getVideoFolders(context)
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedFolder != null) {
                        // 📂 নির্দিষ্ট ফোল্ডারের ভিডিও ভিউ
                        val folderVideos = allVideos
                            .filter { it.bucketId == selectedFolder!!.bucketId }
                            .filter { it.title.contains(searchQuery, ignoreCase = true) }

                        if (folderVideos.isEmpty()) {
                            EmptyMediaNotice(message = "No videos in this folder")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(folderVideos, key = { it.id }) { video ->
                                    LocalVideoRowCard(
                                        video = video,
                                        onClick = { onVideoClick(video) }
                                    )
                                }
                            }
                        }
                    } else if (selectedTab == GalleryTab.FOLDERS) {
                        // 📁 ফোল্ডার লিস্ট
                        val filteredFolders = folders.filter {
                            it.folderName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredFolders.isEmpty()) {
                            EmptyMediaNotice(message = "No video folders found on this device")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredFolders, key = { it.bucketId }) { folder ->
                                    LocalFolderRowCard(
                                        folder = folder,
                                        onClick = { selectedFolder = folder }
                                    )
                                }
                            }
                        }
                    } else {
                        // 🎬 সব ভিডিও লিস্ট
                        val filteredVideos = allVideos.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.folderName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredVideos.isEmpty()) {
                            EmptyMediaNotice(message = "No videos found matching your search")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredVideos, key = { it.id }) { video ->
                                    LocalVideoRowCard(
                                        video = video,
                                        onClick = { onVideoClick(video) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 📁 ফোল্ডার আইটেম কার্ড (MX Player Folder Design)
// -------------------------------------------------------------
@Composable
private fun LocalFolderRowCard(
    folder: LocalVideoFolder,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ফোল্ডার থাম্বনেইল / ফোল্ডার আইকন
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2638)),
                contentAlignment = Alignment.Center
            ) {
                if (!folder.thumbnailUriString.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(folder.thumbnailUriString)
                            .crossfade(true)
                            .build(),
                        contentDescription = folder.folderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // ফোল্ডারের নাম ও তথ্য
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${folder.videoCount} Videos • ${folder.formattedTotalSize}",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 🎬 ভিডিও আইটেম কার্ড (MX Player Video Row Card)
// -------------------------------------------------------------
@Composable
private fun LocalVideoRowCard(
    video: LocalVideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ভিডিও থাম্বনেইল + ডুরেশন ব্যাজ
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2638))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // প্লে ওভারলে আইকন
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // সময় ব্যাজ (Bottom Right)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }
            }

            // ভিডিওর টাইটেল ও ফাইল ডিটেইলস
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF183024),
                        border = BorderStroke(0.6.dp, SafeGreen.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = video.qualityTag,
                            color = SafeGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = "${video.formattedSize} • ${video.folderName}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🛡️ পারমিশন চাওয়ার নোটিশ কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
private fun PermissionRequestNotice(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = SafeGreen,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "Storage Permission Required",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Allow PlayDramaFlix to scan and play video files stored on your device smoothly with 0 ads.",
                color = TextMuted,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
            ) {
                Text("Allow Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
private fun EmptyMediaNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MovieCreation,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = TextMuted,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
