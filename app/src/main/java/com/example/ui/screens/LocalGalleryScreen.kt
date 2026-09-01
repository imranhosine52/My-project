@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import coil.request.videoFrameMillis
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

enum class SpecialViewType {
    NONE, STARRED, SAFE_FOLDER, TRASH
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
    var specialView by remember { mutableStateOf(SpecialViewType.NONE) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }

    var currentFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var currentItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var specialItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var currentlyPlayingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var isMiniPlayerPlaying by remember { mutableStateOf(true) }

    var viewingImageInitialIndex by remember { mutableIntStateOf(-1) }
    var viewingImageList by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var selectedFileInfoItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var movingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    var showPinScreen by remember { mutableStateOf(false) }
    var isSafeFolderUnlocked by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchQuery = spokenText
                isSearchActive = true
            }
        }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(
            permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        )
    }

    fun loadSelectedCategoryData() {
        if (!hasPermission) return
        coroutineScope.launch {
            isLoading = true
            when (specialView) {
                SpecialViewType.STARRED -> {
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context) + LocalMediaScanner.getAllDocuments(context)
                    val starredPaths = LocalMediaScanner.getStarredPaths(context)
                    specialItems = all.filter { it.path in starredPaths }
                }
                SpecialViewType.SAFE_FOLDER -> {
                    specialItems = LocalMediaScanner.getSafeFolderItems(context)
                }
                SpecialViewType.TRASH -> {
                    val trashPaths = LocalMediaScanner.getTrashPaths(context)
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context) + LocalMediaScanner.getAllDocuments(context)
                    specialItems = all.filter { it.path in trashPaths }
                }
                SpecialViewType.NONE -> {
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
                }
            }
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) loadSelectedCategoryData()
    }

    LaunchedEffect(activeTab, specialView, hasPermission) {
        hasPermission = permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) {
            loadSelectedCategoryData()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    BackHandler {
        if (showPinScreen) {
            showPinScreen = false
        } else if (viewingImageInitialIndex != -1) {
            viewingImageInitialIndex = -1
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (specialView != SpecialViewType.NONE) {
            specialView = SpecialViewType.NONE
            loadSelectedCategoryData()
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else {
            onBackClick()
        }
    }

    if (showPinScreen) {
        FullscreenPinScreen(
            context = context,
            onSuccess = {
                isSafeFolderUnlocked = true
                showPinScreen = false
                specialView = SpecialViewType.SAFE_FOLDER
            },
            onBack = { showPinScreen = false }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🔝 ১. টপ হেডার বার (+ বাটন, ফোল্ডার, সার্চ, গ্রিড ভিউ)
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
                    if (selectedFolder != null || specialView != SpecialViewType.NONE) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    if (specialView != SpecialViewType.NONE) specialView = SpecialViewType.NONE
                                    else selectedFolder = null
                                    loadSelectedCategoryData()
                                }
                        )
                    }
                    Text(
                        text = when {
                            specialView == SpecialViewType.STARRED -> "Starred"
                            specialView == SpecialViewType.SAFE_FOLDER -> "Safe Folder"
                            specialView == SpecialViewType.TRASH -> "Recycle Bin"
                            selectedFolder != null -> selectedFolder!!.folderName
                            else -> activeTab.label
                        },
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
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = ElectricBlue,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { showCreateFolderDialog = true }
                    )

                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Documents & Files",
                        tint = if (activeTab == MediaTabType.FILES && specialView == SpecialViewType.NONE) ElectricBlue else Color.White,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable {
                                specialView = SpecialViewType.NONE
                                selectedFolder = null
                                activeTab = MediaTabType.FILES
                            }
                    )

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) ElectricBlue else Color.White,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { isSearchActive = !isSearchActive }
                    )

                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = ElectricBlue,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { isGridView = !isGridView }
                    )
                }
            }

            AnimatedVisibility(visible = isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C202B))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search in files...", color = Color(0xFF6B7280), fontSize = 13.sp)
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

                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = ElectricBlue,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search files...")
                                    }
                                    voiceSearchLauncher.launch(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Voice Search not available", Toast.LENGTH_SHORT).show()
                                }
                            }
                    )
                }
            }

            // 🎛️ ২. টপ টুল ক্যারোজেল
            if (selectedFolder == null && specialView == SpecialViewType.NONE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolCircleIconItem(
                        label = "Music",
                        icon = Icons.Default.Headphones,
                        gradientColors = listOf(Color(0xFFFF8A00), Color(0xFFFF3D00)),
                        onClick = { activeTab = MediaTabType.MUSIC }
                    )

                    ToolCircleIconItem(
                        label = "Starred",
                        icon = Icons.Default.Star,
                        gradientColors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00)),
                        onClick = { specialView = SpecialViewType.STARRED }
                    )

                    ToolCircleIconItem(
                        label = "Safe Folder",
                        icon = Icons.Default.Lock,
                        gradientColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                        onClick = {
                            if (isSafeFolderUnlocked) {
                                specialView = SpecialViewType.SAFE_FOLDER
                            } else {
                                showPinScreen = true
                            }
                        }
                    )

                    ToolCircleIconItem(
                        label = "Trash",
                        icon = Icons.Default.Delete,
                        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
                        onClick = { specialView = SpecialViewType.TRASH }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // 📂 ৩. "Folders" ক্যাটাগরি সুইচ
            if (selectedFolder == null && specialView == SpecialViewType.NONE) {
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
                        MediaTabType.entries.forEach { tab ->
                            val isSel = tab == activeTab
                            Text(
                                text = tab.label,
                                color = if (isSel) ElectricBlue else Color(0xFF6B7280),
                                fontSize = 12.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 📁 ৪. মূল ফাইল এবং ফোল্ডার তালিকা
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
                when {
                    specialView != SpecialViewType.NONE -> {
                        val displayList = specialItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        if (displayList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No files found in this section", color = Color(0xFF6B7280), fontSize = 13.sp)
                            }
                        } else {
                            if (isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayList, key = { it.id }) { item ->
                                        Screenshot2GridItem(
                                            item = item,
                                            onClick = {
                                                handleItemClick(item, displayList, activeTab, context, onVideoClick) { list, idx ->
                                                    viewingImageList = list
                                                    viewingImageInitialIndex = idx
                                                }
                                            },
                                            onMenuClick = { selectedFileInfoItem = item }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayList, key = { it.id }) { item ->
                                        Screenshot3ListItem(
                                            item = item,
                                            onClick = {
                                                handleItemClick(item, displayList, activeTab, context, onVideoClick) { list, idx ->
                                                    viewingImageList = list
                                                    viewingImageInitialIndex = idx
                                                }
                                            },
                                            onMenuClick = { selectedFileInfoItem = item }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    selectedFolder != null -> {
                        val folderMedia = currentItems
                            .filter { it.bucketId == selectedFolder!!.bucketId }
                            .filter { it.title.contains(searchQuery, ignoreCase = true) }

                        if (isGridView) {
                            val columns = if (activeTab == MediaTabType.IMAGES) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(folderMedia, key = { it.id }) { item ->
                                    Screenshot2GridItem(
                                        item = item,
                                        onClick = {
                                            handleItemClick(item, folderMedia, activeTab, context, onVideoClick) { list, idx ->
                                                viewingImageList = list
                                                viewingImageInitialIndex = idx
                                            }
                                        },
                                        onMenuClick = { selectedFileInfoItem = item }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(folderMedia, key = { it.id }) { item ->
                                    Screenshot3ListItem(
                                        item = item,
                                        onClick = {
                                            handleItemClick(item, folderMedia, activeTab, context, onVideoClick) { list, idx ->
                                                viewingImageList = list
                                                viewingImageInitialIndex = idx
                                            }
                                        },
                                        onMenuClick = { selectedFileInfoItem = item }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val filteredFolders = currentFolders.filter { it.folderName.contains(searchQuery, ignoreCase = true) }
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

        // 🎵 ডকড মিনি প্লেয়ার (সিঙ্কড)
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

        // ℹ️ ফাইল মেনু বটম শীট
        selectedFileInfoItem?.let { item ->
            FileActionMenuSheet(
                item = item,
                isSafeFolderItem = specialView == SpecialViewType.SAFE_FOLDER,
                isTrashItem = specialView == SpecialViewType.TRASH,
                onDismiss = { selectedFileInfoItem = null },
                onShare = {
                    shareSingleFile(context, item)
                    selectedFileInfoItem = null
                },
                onRename = {
                    renamingItem = item
                    selectedFileInfoItem = null
                },
                onMove = {
                    movingItem = item
                    selectedFileInfoItem = null
                },
                onToggleStar = {
                    LocalMediaScanner.toggleStarred(context, item.path)
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onMoveToSafe = {
                    LocalMediaScanner.moveToSafeFolder(context, item.path)
                    Toast.makeText(context, "Moved to Safe Folder (Hidden)", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onRestoreFromSafe = {
                    LocalMediaScanner.restoreFromSafeFolder(context, item.path)
                    Toast.makeText(context, "Unhidden from Safe Folder", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onMoveToTrash = {
                    LocalMediaScanner.moveToTrash(context, item.path)
                    Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onRestore = {
                    LocalMediaScanner.restoreFromTrash(context, item.path)
                    Toast.makeText(context, "Restored successfully", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onDeletePermanently = {
                    LocalMediaScanner.deletePermanently(context, item.path)
                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                }
            )
        }

        // ➕ নতুন ফোল্ডার তৈরি ডায়ালগ
        if (showCreateFolderDialog) {
            var newName by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showCreateFolderDialog = false }) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141722))) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create New Folder", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Folder Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, unfocusedBorderColor = Color(0xFF222638), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel", color = Color(0xFF8E95A5)) }
                            Button(
                                onClick = {
                                    if (newName.isNotBlank()) {
                                        coroutineScope.launch {
                                            val path = LocalMediaScanner.createNewFolderAtRoot(newName.trim())
                                            Toast.makeText(context, if (path != null) "Folder Created" else "Failed", Toast.LENGTH_SHORT).show()
                                            showCreateFolderDialog = false
                                            loadSelectedCategoryData()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Create", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 📂 যেকোনো ফোল্ডারে মুভ ডায়ালগ
        movingItem?.let { itemToMove ->
            MoveToFolderDialog(
                folders = currentFolders,
                onDismiss = { movingItem = null },
                onMoveToExisting = { folder ->
                    coroutineScope.launch {
                        val success = LocalMediaScanner.moveFileToDestination(context, itemToMove.path, folder.folderPath)
                        Toast.makeText(context, if (success) "Moved to ${folder.folderName}" else "Move failed", Toast.LENGTH_SHORT).show()
                        movingItem = null
                        loadSelectedCategoryData()
                    }
                },
                onCreateAndMove = { newFolderName ->
                    coroutineScope.launch {
                        val createdPath = LocalMediaScanner.createNewFolderAtRoot(newFolderName)
                        if (createdPath != null) {
                            val success = LocalMediaScanner.moveFileToDestination(context, itemToMove.path, createdPath)
                            Toast.makeText(context, if (success) "Moved to $newFolderName" else "Move failed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not create folder", Toast.LENGTH_SHORT).show()
                        }
                        movingItem = null
                        loadSelectedCategoryData()
                    }
                }
            )
        }

        // ✏️ রিনেম ডায়ালগ
        renamingItem?.let { item ->
            RenameFileDialog(
                currentName = item.displayName.substringBeforeLast("."),
                onDismiss = { renamingItem = null },
                onSave = { newName ->
                    val success = LocalMediaScanner.renameFile(context, item.path, newName)
                    Toast.makeText(context, if (success) "Renamed successfully" else "Rename failed", Toast.LENGTH_SHORT).show()
                    renamingItem = null
                    loadSelectedCategoryData()
                }
            )
        }

        // 🖼️ ফুলস্ক্রিন ইমেজ ভিউয়ার
        if (viewingImageInitialIndex != -1 && viewingImageList.isNotEmpty()) {
            FullscreenImageViewer(
                images = viewingImageList,
                initialIndex = viewingImageInitialIndex,
                onClose = { viewingImageInitialIndex = -1 },
                onImageUpdated = { loadSelectedCategoryData() }
            )
        }
    }
}

// -------------------------------------------------------------
// 🎬 নির্ভরযোগ্য ভিডিও এবং ইমেজ থাম্বনেল প্রিভিউ কম্পোনেন্ট
// -------------------------------------------------------------
@Composable
fun VideoThumbnailPreview(
    item: LocalVideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmapThumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    val isVideo = item.mimeType?.startsWith("video") == true || item.path.endsWith(".mp4", true) || item.path.endsWith(".mkv", true)

    LaunchedEffect(item.path) {
        if (isVideo) {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val file = File(item.path)
                        if (file.exists()) {
                            val bmp = context.contentResolver.loadThumbnail(item.contentUri, Size(250, 250), null)
                            bitmapThumbnail = bmp
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmapThumbnail != null) {
            Image(
                bitmap = bitmapThumbnail!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.contentUri)
                    .videoFrameMillis(1000)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 📄 টেবিল / লিস্ট ভিউ (ভিডিও থাম্বনেল সহ)
// -------------------------------------------------------------
@Composable
private fun Screenshot3ListItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val isAudio = item.mimeType?.startsWith("audio") == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp, 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!isAudio) {
                VideoThumbnailPreview(item = item, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${item.formattedSize} • ${item.formattedDate}", color = Color(0xFF8E95A5), fontSize = 11.5.sp)
        }

        IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF8E95A5), modifier = Modifier.size(20.dp))
        }
    }
}

// -------------------------------------------------------------
// 🔲 গ্রিড আইটেম (ভিডিও থাম্বনেল সহ)
// -------------------------------------------------------------
@Composable
private fun Screenshot2GridItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val isAudio = item.mimeType?.startsWith("audio") == true

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141722)),
        border = BorderStroke(0.6.dp, Color(0xFF222638)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .combinedClickable(onClick = onClick, onLongClick = onMenuClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isAudio) {
                VideoThumbnailPreview(item = item, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF221A30)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = item.formattedSize,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 🖼️ ফুলস্ক্রিন ইমেজ ভিউয়ার
// -------------------------------------------------------------
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { currentImage?.let { shareSingleFile(context, it) } }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }

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

                IconButton(onClick = {
                    currentImage?.let {
                        LocalMediaScanner.moveToTrash(context, it.path)
                        Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        val updated = imageList.toMutableList().apply { removeAt(pagerState.currentPage) }
                        if (updated.isEmpty()) onClose() else imageList = updated
                        onImageUpdated()
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }

                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Details", tint = Color.White)
                }
            }
        }

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
                        Text("File Details", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

// -------------------------------------------------------------
// 🔍 সোয়াইপ এবং জুমযোগ্য ইমেজ কম্পোনেন্ট
// -------------------------------------------------------------
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

// -------------------------------------------------------------
// 🔒 ফুলস্ক্রিন পিন স্ক্রিন (Create & Unlock)
// -------------------------------------------------------------
@Composable
fun FullscreenPinScreen(
    context: Context,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val isPinSet = remember { LocalMediaScanner.isSafeFolderPinSet(context) }
    var step by remember { mutableStateOf(if (isPinSet) "ENTER" else "CREATE") }
    var pinText by remember { mutableStateOf("") }
    var tempCreatedPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when (step) {
        "CREATE" -> "Set PIN"
        "CONFIRM" -> "Confirm PIN"
        else -> "Enter PIN"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF534C64))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFD700)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Key",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinText.length
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isFilled) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }

                errorMessage?.let {
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (pinText.length == 4) {
                            when (step) {
                                "CREATE" -> {
                                    tempCreatedPin = pinText
                                    pinText = ""
                                    errorMessage = null
                                    step = "CONFIRM"
                                }
                                "CONFIRM" -> {
                                    if (pinText == tempCreatedPin) {
                                        LocalMediaScanner.saveSafePin(context, pinText)
                                        onSuccess()
                                    } else {
                                        errorMessage = "PIN does not match! Try again."
                                        pinText = ""
                                        step = "CREATE"
                                    }
                                }
                                "ENTER" -> {
                                    val saved = LocalMediaScanner.getSavedSafePin(context)
                                    if (pinText == saved) {
                                        onSuccess()
                                    } else {
                                        errorMessage = "Incorrect PIN! Try again."
                                        pinText = ""
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(44.dp)
                ) {
                    Text(if (step == "ENTER") "Unlock" else "Next", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                CustomNumericKeypad(
                    onNumberClick = { num ->
                        if (pinText.length < 4) {
                            pinText += num
                            errorMessage = null
                        }
                    },
                    onDeleteClick = {
                        if (pinText.isNotEmpty()) {
                            pinText = pinText.dropLast(1)
                            errorMessage = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CustomNumericKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable(enabled = key.isNotBlank()) {
                                if (key == "DEL") onDeleteClick() else onNumberClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(24.dp))
                        } else if (key.isNotBlank()) {
                            Text(key, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 📁 ফোল্ডারে মুভ ডায়ালগ
// -------------------------------------------------------------
@Composable
fun MoveToFolderDialog(
    folders: List<LocalVideoFolder>,
    onDismiss: () -> Unit,
    onMoveToExisting: (LocalVideoFolder) -> Unit,
    onCreateAndMove: (String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141722))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Move to Folder", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                if (!isCreatingNew) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricBlue.copy(alpha = 0.15f))
                            .clickable { isCreatingNew = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ElectricBlue)
                        Text("+ Create New Folder", color = ElectricBlue, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(folders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMoveToExisting(folder) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(22.dp))
                                Text(folder.folderName, color = Color.White, fontSize = 13.5.sp, maxLines = 1)
                            }
                            HorizontalDivider(color = Color(0xFF222638), thickness = 0.5.dp)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("New Folder Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = Color(0xFF222638),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isCreatingNew = false }) { Text("Back", color = Color(0xFF8E95A5)) }
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) onCreateAndMove(newFolderName.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Create & Move", color = Color.White)
                        }
                    }
                }

                if (!isCreatingNew) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8E95A5)) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ক্লিক হ্যান্ডলার
// -------------------------------------------------------------
private fun handleItemClick(
    item: LocalVideoItem,
    allList: List<LocalVideoItem>,
    tab: MediaTabType,
    context: Context,
    onVideoClick: (LocalVideoItem) -> Unit,
    onImageViewerOpen: (List<LocalVideoItem>, Int) -> Unit
) {
    val isImage = item.mimeType?.startsWith("image") == true || tab == MediaTabType.IMAGES
    val isVideo = item.mimeType?.startsWith("video") == true || tab == MediaTabType.VIDEOS
    val isAudio = item.mimeType?.startsWith("audio") == true || tab == MediaTabType.MUSIC

    when {
        isImage -> {
            val imageList = allList.filter { it.mimeType?.startsWith("image") == true || it.path.endsWith(".jpg", true) || it.path.endsWith(".png", true) || it.path.endsWith(".jpeg", true) }
            val index = imageList.indexOf(item).coerceAtLeast(0)
            onImageViewerOpen(imageList.ifEmpty { listOf(item) }, index)
        }
        isVideo || isAudio -> onVideoClick(item)
        else -> {
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
                text = "${folder.videoCount} items • ${folder.formattedTotalSize}",
                color = Color(0xFF6B7280),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionMenuSheet(
    item: LocalVideoItem,
    isSafeFolderItem: Boolean = false,
    isTrashItem: Boolean = false,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onToggleStar: () -> Unit,
    onMoveToSafe: () -> Unit,
    onRestoreFromSafe: () -> Unit = {},
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit = {},
    onDeletePermanently: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.formattedSize} • ${item.path}", color = Color(0xFF8E95A5), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            HorizontalDivider(color = Color(0xFF222638), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 6.dp))

            when {
                isSafeFolderItem -> {
                    SheetActionRow(Icons.Default.LockOpen, "Unhide from Safe Folder", Color(0xFF00E5FF), onRestoreFromSafe)
                    SheetActionRow(Icons.Default.DeleteForever, "Delete Permanently", Color(0xFFFF5252), onDeletePermanently)
                }
                isTrashItem -> {
                    SheetActionRow(Icons.Default.Restore, "Restore File", Color(0xFF00E676), onRestore)
                    SheetActionRow(Icons.Default.DeleteForever, "Delete Permanently", Color(0xFFFF5252), onDeletePermanently)
                }
                else -> {
                    SheetActionRow(Icons.Default.Share, "Share", Color.White, onShare)
                    SheetActionRow(Icons.Default.DriveFileMove, "Move to Folder", Color.White, onMove)
                    SheetActionRow(Icons.Default.Edit, "Rename", Color.White, onRename)
                    SheetActionRow(if (item.isStarred) Icons.Default.Star else Icons.Outlined.Star, if (item.isStarred) "Remove from Starred" else "Add to Starred", if (item.isStarred) Color(0xFFFFB300) else Color.White, onToggleStar)
                    SheetActionRow(Icons.Default.Lock, "Move to Safe Folder", Color.White, onMoveToSafe)
                    SheetActionRow(Icons.Default.Delete, "Move to Trash", Color(0xFFFF5252), onMoveToTrash)
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RenameFileDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141722))) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rename File", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = Color(0xFF222638),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8E95A5)) }
                    Button(onClick = { onSave(name.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)) { Text("Save", color = Color.White) }
                }
            }
        }
    }
}

private fun shareSingleFile(context: Context, item: LocalVideoItem) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, item.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${item.title}"))
    } catch (_: Exception) {}
}
