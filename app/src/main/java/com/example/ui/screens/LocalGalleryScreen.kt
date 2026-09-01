@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF000000)
private val FolderDarkGrey = Color(0xFF384356)
private val ElectricBlue = Color(0xFF2979FF)
private val BadgeRed = Color(0xFFFF2A4B)
private val CardSurface = Color(0xFF12151C)

enum class MediaTabType(val label: String, val index: Int) {
    VIDEOS("Videos", 0),
    MUSIC("Music", 1),
    IMAGES("Images", 2),
    FILES("Files", 3)
}

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videoItem: LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(MediaTabType.VIDEOS) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 🔲 Table (List) vs Grid ভিউ স্টেট
    var isGridView by remember { mutableStateOf(false) }

    var currentFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var currentItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var currentlyPlayingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var isMiniPlayerPlaying by remember { mutableStateOf(true) }

    var viewingImageItem by remember { mutableStateOf<LocalVideoItem?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when (activeTab) {
            MediaTabType.MUSIC -> Manifest.permission.READ_MEDIA_AUDIO
            MediaTabType.IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
            else -> Manifest.permission.READ_MEDIA_VIDEO
        }
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun loadSelectedCategoryData() {
        if (!hasPermission) return
        coroutineScope.launch {
            isLoading = true
            currentItems = when (activeTab) {
                MediaTabType.MUSIC -> LocalMediaScanner.getAllAudioTracks(context)
                MediaTabType.IMAGES -> LocalMediaScanner.getAllImages(context)
                MediaTabType.FILES -> LocalMediaScanner.getAllDocuments(context)
                MediaTabType.VIDEOS -> LocalMediaScanner.getAllVideos(context)
            }
            currentFolders = LocalMediaScanner.getCategoryFolders(context, activeTab.index)
            if (currentlyPlayingItem == null && currentItems.isNotEmpty() && activeTab == MediaTabType.MUSIC) {
                currentlyPlayingItem = currentItems.firstOrNull()
            }
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) loadSelectedCategoryData()
    }

    LaunchedEffect(activeTab, hasPermission) {
        hasPermission = ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            loadSelectedCategoryData()
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    BackHandler {
        if (viewingImageItem != null) {
            viewingImageItem = null
        } else if (isSearchActive) {
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
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ হেডার বার: Title  [🔍 Search] [🔲 Table/Grid Switch]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedFolder != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { selectedFolder = null }
                        )
                    }
                    Text(
                        text = if (selectedFolder != null) selectedFolder!!.folderName else activeTab.label,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { isSearchActive = !isSearchActive }
                    )
                    // 🔲 Table (List) ও Grid সুইচ আইকন
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to Table/List" else "Switch to Grid",
                        tint = ElectricBlue,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                isGridView = !isGridView
                                Toast.makeText(context, if (isGridView) "Grid View Active" else "Table/List View Active", Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }

            // 🔍 সার্চ বার
            AnimatedVisibility(visible = isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C202B))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text("Search in ${activeTab.label}...", color = Color(0xFF6B7280), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(ElectricBlue),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // =========================================================================
            // 📂 ২. ক্যাটাগরি সুইচ হেডার (Videos • Music • Images • Files)
            // =========================================================================
            if (selectedFolder == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Folders (${currentFolders.size})",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaTabType.entries.forEach { tab ->
                            val isSel = tab == activeTab
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSel) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSel) BorderStroke(1.dp, ElectricBlue) else null,
                                modifier = Modifier.clickable {
                                    activeTab = tab
                                    selectedFolder = null
                                }
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSel) ElectricBlue else Color(0xFF8E95A5),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 📁 ৩. Table/List এবং Grid মোডে ফোল্ডার ও ফাইল তালিকা
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadSelectedCategoryData()
                        delay(400)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (selectedFolder != null) {
                    // নির্দিষ্ট ফোল্ডারের ফাইল তালিকা
                    val folderMedia = currentItems
                        .filter { it.bucketId == selectedFolder!!.bucketId }
                        .filter { it.title.contains(searchQuery, ignoreCase = true) }

                    if (isGridView) {
                        // 🔲 Grid View (ইমেজের জন্য ৩ কলাম, ভিডিও/অডিওর জন্য ২ কলাম)
                        val columns = if (activeTab == MediaTabType.IMAGES) 3 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(folderMedia, key = { it.id }) { item ->
                                MediaGridCardItem(
                                    item = item,
                                    tabType = activeTab,
                                    onClick = {
                                        handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img }
                                    }
                                )
                            }
                        }
                    } else {
                        // 📄 Table / List View
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folderMedia, key = { it.id }) { item ->
                                MediaTableRowItem(
                                    item = item,
                                    tabType = activeTab,
                                    onClick = {
                                        handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // 📁 মূল ফোল্ডার তালিকা
                    val filteredFolders = currentFolders.filter {
                        it.folderName.contains(searchQuery, ignoreCase = true)
                    }

                    if (isGridView) {
                        // 🔲 ফোল্ডার গ্রিড ভিউ (২ কলাম)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFolders, key = { it.bucketId }) { folder ->
                                FolderGridCardItem(
                                    folder = folder,
                                    onClick = { selectedFolder = folder }
                                )
                            }
                        }
                    } else {
                        // 📄 ফোল্ডার টেবিল/লিস্ট ভিউ (স্ক্রিনশট ১ এর মতো)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredFolders, key = { it.bucketId }) { folder ->
                                ScreenshotStyleFolderItem(
                                    folder = folder,
                                    onClick = { selectedFolder = folder }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 🖼️ ইমেজ ফুলস্ক্রিন প্রিভিউ ডায়ালগ
        viewingImageItem?.let { imageItem ->
            Dialog(onDismissRequest = { viewingImageItem = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable { viewingImageItem = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(imageItem.contentUri).crossfade(true).build(),
                        contentDescription = imageItem.title,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // 🔵 FAB Play Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 84.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(ElectricBlue)
                .clickable {
                    currentItems.firstOrNull()?.let {
                        currentlyPlayingItem = it
                        onVideoClick(it)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // 🎵 ডকড মিনি প্লেয়ার
        currentlyPlayingItem?.let { playing ->
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.8.dp, Color(0xFF222836)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                    .clickable { onVideoClick(playing) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8DEF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = playing.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (isMiniPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isMiniPlayerPlaying = !isMiniPlayerPlaying }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ক্লিক হ্যান্ডলার (Video, Audio, Image, Files)
// -------------------------------------------------------------
private fun handleItemClick(
    item: LocalVideoItem,
    tab: MediaTabType,
    context: android.content.Context,
    onVideoClick: (LocalVideoItem) -> Unit,
    onImageView: (LocalVideoItem) -> Unit
) {
    when (tab) {
        MediaTabType.VIDEOS -> onVideoClick(item)
        MediaTabType.MUSIC -> onVideoClick(item) // অডিও প্লেয়ারে ওপেন
        MediaTabType.IMAGES -> onImageView(item) // ইমেজ ফুলস্ক্রিন ভিউ
        MediaTabType.FILES -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.contentUri, item.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// -------------------------------------------------------------
// 📁 ফোল্ডার টেবিল / লিস্ট ভিউ (স্ক্রিনশট ১ এর মতো)
// -------------------------------------------------------------
@Composable
private fun ScreenshotStyleFolderItem(
    folder: LocalVideoFolder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(54.dp).height(44.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FolderDarkGrey),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF5B6B82),
                    modifier = Modifier.size(28.dp)
                )
            }

            if (folder.videoCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(BadgeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (folder.videoCount > 99) "99+" else folder.videoCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.folderName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.videoCount} items • ${folder.formattedTotalSize}",
                color = Color(0xFF6B7280),
                fontSize = 12.sp
            )
        }
    }
}

// -------------------------------------------------------------
// 🔲 ফোল্ডার গ্রিড কার্ড আইটেম (২ কলাম)
// -------------------------------------------------------------
@Composable
private fun FolderGridCardItem(
    folder: LocalVideoFolder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(0.8.dp, Color(0xFF222836))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FolderDarkGrey),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(folder.folderName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${folder.videoCount} items", color = Color(0xFF8E95A5), fontSize = 11.sp)
        }
    }
}

// -------------------------------------------------------------
// 📄 মিডিয়া টেবিল / লিস্ট ভিউ
// -------------------------------------------------------------
@Composable
private fun MediaTableRowItem(
    item: LocalVideoItem,
    tabType: MediaTabType,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardSurface)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E2433)),
            contentAlignment = Alignment.Center
        ) {
            when (tabType) {
                MediaTabType.VIDEOS, MediaTabType.IMAGES -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.contentUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MediaTabType.MUSIC -> Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
                MediaTabType.FILES -> Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            val detail = if (tabType == MediaTabType.VIDEOS || tabType == MediaTabType.MUSIC) "${item.formattedDuration} • ${item.formattedSize}" else item.formattedSize
            Text(detail, color = Color(0xFF8E95A5), fontSize = 11.sp)
        }

        Icon(
            imageVector = if (tabType == MediaTabType.IMAGES) Icons.Default.Visibility else Icons.Default.PlayCircleOutline,
            contentDescription = null,
            tint = ElectricBlue,
            modifier = Modifier.size(22.dp)
        )
    }
}

// -------------------------------------------------------------
// 🔲 মিডিয়া গ্রিড কার্ড আইটেম (২/৩ কলাম)
// -------------------------------------------------------------
@Composable
private fun MediaGridCardItem(
    item: LocalVideoItem,
    tabType: MediaTabType,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(0.8.dp, Color(0xFF222836))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Color(0xFF1E2433)),
                contentAlignment = Alignment.Center
            ) {
                if (tabType == MediaTabType.VIDEOS || tabType == MediaTabType.IMAGES) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.contentUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (tabType == MediaTabType.MUSIC) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                } else {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(32.dp))
                }
            }

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
