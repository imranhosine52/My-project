@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class MediaFilterCategory(val label: String) {
    VIDEOS("Videos"),
    MUSIC("Music"),
    IMAGES("Images"),
    FILES("Files")
}

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videoItem: LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeCategory by remember { mutableStateOf(MediaFilterCategory.VIDEOS) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }

    var videoFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var audioFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var currentlyPlayingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var isMiniPlayerPlaying by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshAllMedia() {
        if (!hasPermission) return
        coroutineScope.launch {
            isLoading = true
            val vids = LocalMediaScanner.getAllVideos(context)
            val auds = LocalMediaScanner.getAllAudioTracks(context)
            allItems = if (activeCategory == MediaFilterCategory.MUSIC) auds else vids
            videoFolders = LocalMediaScanner.getMediaFolders(context, isAudio = false)
            audioFolders = LocalMediaScanner.getMediaFolders(context, isAudio = true)
            if (currentlyPlayingItem == null && allItems.isNotEmpty()) {
                currentlyPlayingItem = allItems.firstOrNull()
            }
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) refreshAllMedia()
    }

    LaunchedEffect(hasPermission, activeCategory) {
        if (hasPermission) {
            refreshAllMedia()
        } else {
            permissionLauncher.launch(permissionToRequest)
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
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ হেডার বার: Videos  [📁] [🔍] [🔲]
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
                        text = if (selectedFolder != null) selectedFolder!!.folderName else activeCategory.label,
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
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Folder",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { isSearchActive = !isSearchActive }
                    )
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Layout Toggle",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { isGridView = !isGridView }
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
                        Text("Search folder or media...", color = Color(0xFF6B7280), fontSize = 13.sp)
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
            // 🎛️ ২. টপ টুল ক্যারোজেল
            // =========================================================================
            if (selectedFolder == null) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ToolCircleIconItem(
                            label = "Music",
                            icon = Icons.Default.Headphones,
                            gradientColors = listOf(Color(0xFFFF8A00), Color(0xFFFF3D00)),
                            onClick = {
                                activeCategory = if (activeCategory == MediaFilterCategory.MUSIC) MediaFilterCategory.VIDEOS else MediaFilterCategory.MUSIC
                            }
                        )
                    }
                    item {
                        ToolCircleIconItem(
                            label = "File Transfer",
                            icon = Icons.Default.DriveFolderUpload,
                            gradientColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                            onClick = { Toast.makeText(context, "Ready to transfer files", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    item {
                        ToolCircleIconItem(
                            label = "Status Saver",
                            icon = Icons.Default.FileDownload,
                            gradientColors = listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
                            onClick = { Toast.makeText(context, "Status saver active", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    item {
                        ToolCircleIconItem(
                            label = "My Playlists",
                            icon = Icons.Default.PlaylistAddCheck,
                            gradientColors = listOf(Color(0xFFB388FF), Color(0xFF7C4DFF)),
                            onClick = { Toast.makeText(context, "Playlists", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    item {
                        ToolCircleIconItem(
                            label = "Cleaner",
                            icon = Icons.Default.CleaningServices,
                            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF1DE9B6)),
                            onClick = { Toast.makeText(context, "Cache Cleaned Successfully!", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    item {
                        ToolCircleIconItem(
                            label = "Privacy",
                            icon = Icons.Default.Security,
                            gradientColors = listOf(Color(0xFF2979FF), Color(0xFFFFD600)),
                            onClick = { Toast.makeText(context, "Private Vault Protected", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.width(6.dp).height(3.dp).clip(CircleShape).background(Color.White))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.width(3.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // =========================================================================
            // 📂 ৩. "Folders" হেডার ও ক্যাটাগরি সুইচ
            // =========================================================================
            if (selectedFolder == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Folders",
                        color = ElectricBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MediaFilterCategory.entries.forEach { cat ->
                            val isSel = cat == activeCategory
                            Text(
                                text = cat.label,
                                color = if (isSel) ElectricBlue else Color(0xFF6B7280),
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { activeCategory = cat }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 📁 ৪. ফোল্ডার ও মিডিয়া লিস্ট
            // =========================================================================
            val currentFolders = if (activeCategory == MediaFilterCategory.MUSIC) audioFolders else videoFolders

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        refreshAllMedia()
                        delay(400)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedFolder != null) {
                    val folderMedia = allItems
                        .filter { it.bucketId == selectedFolder!!.bucketId }
                        .filter { it.title.contains(searchQuery, ignoreCase = true) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folderMedia, key = { it.id }) { item ->
                            LocalMediaRowItem(
                                item = item,
                                onClick = {
                                    currentlyPlayingItem = item
                                    onVideoClick(item)
                                }
                            )
                        }
                    }
                } else {
                    val filteredFolders = currentFolders.filter {
                        it.folderName.contains(searchQuery, ignoreCase = true)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        // =========================================================================
        // 🔵 ৫. ফ্লোটিং ব্লু প্লে বাটন (FAB)
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 84.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(ElectricBlue)
                .clickable {
                    allItems.firstOrNull()?.let {
                        currentlyPlayingItem = it
                        onVideoClick(it)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play All",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // =========================================================================
        // 🎵 ৬. ডকড মিনি প্লেয়ার বার (Fixed Padding Overload)
        // =========================================================================
        currentlyPlayingItem?.let { playing ->
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.8.dp, Color(0xFF222836)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp) // 👈 প্যাডিং ফিক্স করা হয়েছে
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

                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🎛️ টপ সার্কুলার টুল আইটেম
// -------------------------------------------------------------
@Composable
private fun ToolCircleIconItem(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// -------------------------------------------------------------
// 📁 ফোল্ডার রো আইটেম
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
        Box(
            modifier = Modifier.width(54.dp).height(44.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FolderDarkGrey),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (folder.folderName.contains("movie", true)) Icons.Default.Movie else Icons.Default.Folder,
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
                color = if (folder.folderName.contains("movie", true)) ElectricBlue else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.videoCount} items, 1 folder",
                color = Color(0xFF6B7280),
                fontSize = 12.sp
            )
        }
    }
}

// -------------------------------------------------------------
// 🎬 মিডিয়া ফাইল রো আইটেম
// -------------------------------------------------------------
@Composable
private fun LocalMediaRowItem(
    item: LocalVideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isAudio = item.mimeType?.startsWith("audio") == true

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
            if (!isAudio) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${item.formattedDuration} • ${item.formattedSize}", color = Color(0xFF8E95A5), fontSize = 11.sp)
        }

        Icon(Icons.Default.PlayCircleOutline, contentDescription = "Play", tint = ElectricBlue, modifier = Modifier.size(22.dp))
    }
}
