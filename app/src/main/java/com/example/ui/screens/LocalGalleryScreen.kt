@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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

    var viewingImageItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var selectedFileInfoItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }

    // 🔒 Safe Folder PIN ভেরিফিকেশন স্টেট
    var showPinDialog by remember { mutableStateOf(false) }
    var isSafeFolderUnlocked by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

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
                    val safePaths = LocalMediaScanner.getSafePaths(context)
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context) + LocalMediaScanner.getAllDocuments(context)
                    specialItems = all.filter { it.path in safePaths }
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
        if (viewingImageItem != null) {
            viewingImageItem = null
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. টপ হেডার বার: Title  [📁 Documents] [🔍 Search] [🔲 Table/Grid]
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
                            specialView == SpecialViewType.STARRED -> "Starred Files"
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
                    // 📁 উপরের ফোল্ডার আইকন: সরাসরি ডকুমেন্টস ও ফাইলস সেকশনে নিয়ে যাবে
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

                    // 🔍 সার্চ
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) ElectricBlue else Color.White,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { isSearchActive = !isSearchActive }
                    )

                    // 🔲 Table (List) | Grid ভিউ সুইচ
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
            }

            // =========================================================================
            // 🎛️ ২. টপ টুল ক্যারোজেল (Music, Starred, Safe Folder, Trash)
            // =========================================================================
            if (selectedFolder == null && specialView == SpecialViewType.NONE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ১. Music
                    ToolCircleIconItem(
                        label = "Music",
                        icon = Icons.Default.Headphones,
                        gradientColors = listOf(Color(0xFFFF8A00), Color(0xFFFF3D00)),
                        onClick = { activeTab = MediaTabType.MUSIC }
                    )

                    // ২. Starred
                    ToolCircleIconItem(
                        label = "Starred",
                        icon = Icons.Default.Star,
                        gradientColors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00)),
                        onClick = { specialView = SpecialViewType.STARRED }
                    )

                    // ৩. Safe Folder
                    ToolCircleIconItem(
                        label = "Safe Folder",
                        icon = Icons.Default.Lock,
                        gradientColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                        onClick = {
                            if (isSafeFolderUnlocked) {
                                specialView = SpecialViewType.SAFE_FOLDER
                            } else {
                                showPinDialog = true
                            }
                        }
                    )

                    // ৪. Trash
                    ToolCircleIconItem(
                        label = "Trash",
                        icon = Icons.Default.Delete,
                        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
                        onClick = { specialView = SpecialViewType.TRASH }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // =========================================================================
            // 📂 ৩. "Folders" হেডার ও ক্যাটাগরি সুইচ (Videos • Music • Images • Files)
            // =========================================================================
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

            // =========================================================================
            // 📁 ৪. মূল কনটেন্ট (Table / List ভিউ এবং Grid ভিউ)
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
                when {
                    // স্পেশাল ভিউ (Starred, Safe Folder, Trash)
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
                                            onClick = { handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img } },
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
                                            onClick = { handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img } },
                                            onMenuClick = { selectedFileInfoItem = item }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ফোল্ডারের ভেতরের কনটেন্ট
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
                                        onClick = { handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img } },
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
                                        onClick = { handleItemClick(item, activeTab, context, onVideoClick) { img -> viewingImageItem = img } },
                                        onMenuClick = { selectedFileInfoItem = item }
                                    )
                                }
                            }
                        }
                    }

                    // প্রধান ফোল্ডার তালিকা
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

        // 🔒 Safe Folder PIN Dialog
        if (showPinDialog) {
            SafeFolderPinDialog(
                savedPin = LocalMediaScanner.getSafeFolderPin(context),
                onSuccess = {
                    isSafeFolderUnlocked = true
                    showPinDialog = false
                    specialView = SpecialViewType.SAFE_FOLDER
                },
                onDismiss = { showPinDialog = false }
            )
        }

        // ℹ️ ফাইল মেনু বটম শীট
        selectedFileInfoItem?.let { item ->
            FileActionMenuSheet(
                item = item,
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
                onToggleStar = {
                    LocalMediaScanner.toggleStarred(context, item.path)
                    selectedFileInfoItem = null
                    loadSelectedCategoryData()
                },
                onMoveToSafe = {
                    LocalMediaScanner.moveToSafeFolder(context, item.path)
                    Toast.makeText(context, "Moved to Safe Folder", Toast.LENGTH_SHORT).show()
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

        // 🖼️ ইমেজ ফুলস্ক্রিন ভিউয়ার
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
    }
}

// -------------------------------------------------------------
// ক্লিক হ্যান্ডলার
// -------------------------------------------------------------
private fun handleItemClick(
    item: LocalVideoItem,
    tab: MediaTabType,
    context: Context,
    onVideoClick: (LocalVideoItem) -> Unit,
    onImageView: (LocalVideoItem) -> Unit
) {
    when {
        item.mimeType?.startsWith("video") == true || tab == MediaTabType.VIDEOS -> onVideoClick(item)
        item.mimeType?.startsWith("audio") == true || tab == MediaTabType.MUSIC -> onVideoClick(item)
        item.mimeType?.startsWith("image") == true || tab == MediaTabType.IMAGES -> onImageView(item)
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
// 📁 ফোল্ডার আইটেম
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

// -------------------------------------------------------------
// 🔲 গ্রিড আইটেম (Grid View)
// -------------------------------------------------------------
@Composable
private fun Screenshot2GridItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
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
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.contentUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF221A30)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
// 📄 টেবিল / লিস্ট ভিউ (Table View)
// -------------------------------------------------------------
@Composable
private fun Screenshot3ListItem(
    item: LocalVideoItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
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
                .size(60.dp, 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardSurface),
            contentAlignment = Alignment.Center
        ) {
            if (!isAudio) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.contentUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
// ℹ️ ফাইল মেনু বটম শীট
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionMenuSheet(
    item: LocalVideoItem,
    isTrashItem: Boolean = false,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onToggleStar: () -> Unit,
    onMoveToSafe: () -> Unit,
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

            if (isTrashItem) {
                SheetActionRow(Icons.Default.Restore, "Restore File", Color(0xFF00E676), onRestore)
                SheetActionRow(Icons.Default.DeleteForever, "Delete Permanently", Color(0xFFFF5252), onDeletePermanently)
            } else {
                SheetActionRow(Icons.Default.Share, "Share", Color.White, onShare)
                SheetActionRow(Icons.Default.Edit, "Rename", Color.White, onRename)
                SheetActionRow(if (item.isStarred) Icons.Default.Star else Icons.Outlined.Star, if (item.isStarred) "Remove from Starred" else "Add to Starred", if (item.isStarred) Color(0xFFFFB300) else Color.White, onToggleStar)
                SheetActionRow(Icons.Default.Lock, "Move to Safe Folder", Color.White, onMoveToSafe)
                SheetActionRow(Icons.Default.Delete, "Move to Trash", Color(0xFFFF5252), onMoveToTrash)
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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, unfocusedBorderColor = Color(0xFF222638), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
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

@Composable
private fun SafeFolderPinDialog(
    savedPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141722))) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                Text("Enter Safe Folder PIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4) {
                            pin = it
                            isError = false
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, unfocusedBorderColor = Color(0xFF222638)),
                    modifier = Modifier.width(140.dp)
                )

                if (isError) {
                    Text("Incorrect PIN! Try again.", color = Color(0xFFFF5252), fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8E95A5)) }
                    Button(
                        onClick = {
                            if (pin == savedPin) {
                                onSuccess()
                            } else {
                                isError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Unlock", color = Color.White)
                    }
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
