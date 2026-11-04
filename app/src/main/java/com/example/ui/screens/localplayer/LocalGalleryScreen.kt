@file:kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.localplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.LocalVideoFolder
import com.example.data.model.LocalVideoItem
import com.example.ui.screens.localplayer.gallery.*
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GalleryBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F131D),
        Color(0xFF080A0F),
        Color(0xFF000000)
    )
)
private val ElectricBlue = Color(0xFF2979FF)

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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var activeTab by remember { mutableStateOf(MediaTabType.VIDEOS) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var specialView by remember { mutableStateOf(SpecialViewType.NONE) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // ডিফল্ট ৩-কলাম গ্রিড ভিউ
    var isGridView by remember { mutableStateOf(true) }

    var currentFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var currentItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var specialItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var viewingImageInitialIndex by remember { mutableIntStateOf(-1) }
    var viewingImageList by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var selectedFileInfoItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var movingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }

    var showPinScreen by remember { mutableStateOf(false) }
    var isSafeFolderUnlocked by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchQuery = spokenText
                isSearchActive = true
            }
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = false
                coroutineScope.launch {
                    loadCategoryDataDirect(context, activeTab, specialView) { items, folders, specials ->
                        currentItems = items
                        currentFolders = folders
                        specialItems = specials
                    }
                }
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
        if (activeTab == MediaTabType.FILES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = true
            }
        }

        coroutineScope.launch {
            isLoading = true
            when (specialView) {
                SpecialViewType.STARRED -> {
                    val all = LocalMediaScanner.getAllVideos(context) +
                            LocalMediaScanner.getAllAudioTracks(context) +
                            LocalMediaScanner.getAllImages(context) +
                            LocalMediaScanner.getAllDocuments(context)
                    val starredPaths = LocalMediaScanner.getStarredPaths(context)
                    specialItems = all.filter { it.path in starredPaths }
                }
                SpecialViewType.SAFE_FOLDER -> {
                    specialItems = LocalMediaScanner.getSafeFolderItems(context)
                }
                SpecialViewType.TRASH -> {
                    specialItems = LocalMediaScanner.getTrashItems(context)
                }
                SpecialViewType.NONE -> {
                    currentItems = when (activeTab) {
                        MediaTabType.MUSIC -> LocalMediaScanner.getAllAudioTracks(context)
                        MediaTabType.IMAGES -> LocalMediaScanner.getAllImages(context)
                        MediaTabType.FILES -> LocalMediaScanner.getAllDocuments(context)
                        MediaTabType.VIDEOS -> LocalMediaScanner.getAllVideos(context)
                    }
                    currentFolders = LocalMediaScanner.getCategoryFolders(context, activeTab.index)
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
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (showPinScreen) {
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = selectedFolder == null && specialView == SpecialViewType.NONE,
        drawerContent = {
            GallerySidebarDrawerContent(
                onOpenMusic = {
                    coroutineScope.launch { drawerState.close() }
                    specialView = SpecialViewType.NONE
                    selectedFolder = null
                    activeTab = MediaTabType.MUSIC
                },
                onOpenStarred = {
                    coroutineScope.launch { drawerState.close() }
                    selectedFolder = null
                    specialView = SpecialViewType.STARRED
                },
                onOpenSafeFolder = {
                    coroutineScope.launch { drawerState.close() }
                    selectedFolder = null
                    if (isSafeFolderUnlocked) {
                        specialView = SpecialViewType.SAFE_FOLDER
                    } else {
                        showPinScreen = true
                    }
                },
                onOpenTrash = {
                    coroutineScope.launch { drawerState.close() }
                    selectedFolder = null
                    specialView = SpecialViewType.TRASH
                }
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(GalleryBackgroundBrush)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // =============================================================
                // 🔝 ১. টপ হেডার বার
                // =============================================================
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
                        } else {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu Drawer",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (specialView == SpecialViewType.TRASH && specialItems.isNotEmpty()) {
                            IconButton(
                                onClick = { showEmptyTrashConfirmDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Empty Bin",
                                    tint = Color(0xFFFF416C),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        if (specialView == SpecialViewType.NONE && selectedFolder == null) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = ElectricBlue,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { showCreateFolderDialog = true }
                            )

                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Files",
                                tint = if (activeTab == MediaTabType.FILES) ElectricBlue else Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        selectedFolder = null
                                        activeTab = MediaTabType.FILES
                                    }
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) ElectricBlue else Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { isSearchActive = !isSearchActive }
                        )

                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List",
                            tint = ElectricBlue,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { isGridView = !isGridView }
                        )
                    }
                }

                // সার্চ বক্স
                AnimatedVisibility(visible = isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF19202E))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search files...", color = Color(0xFF6B7280), fontSize = 13.sp)
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
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                        }
                                        voiceSearchLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Voice Search unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    }
                }

                // 📂 ২. ক্যাটাগরি ও ফোল্ডার হেডার
                if (selectedFolder == null && specialView == SpecialViewType.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Folders",
                            color = ElectricBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaTabType.entries.forEach { tab ->
                                val isSel = (tab == activeTab)
                                Text(
                                    text = tab.label,
                                    color = if (isSel) ElectricBlue else Color(0xFF8E95A5),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { activeTab = tab }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 📁 ৩. মূল গ্রিড/লিস্ট ভিউ (৩-কলাম বিন্যাস)
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            loadSelectedCategoryData()
                            delay(350)
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
                                    Text("No files in ${if (specialView == SpecialViewType.TRASH) "Recycle Bin" else "this section"}", color = Color(0xFF6B7280), fontSize = 13.5.sp)
                                }
                            } else if (isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

                        selectedFolder != null -> {
                            val folderMedia = currentItems
                                .filter { it.bucketId == selectedFolder!!.bucketId }
                                .filter { it.title.contains(searchQuery, ignoreCase = true) }

                            if (folderMedia.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Folder is empty", color = Color(0xFF6B7280), fontSize = 13.5.sp)
                                }
                            } else if (isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            if (filteredFolders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No folders found", color = Color(0xFF6B7280), fontSize = 13.5.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            }

            // ট্র্যাশ সম্পূর্ণ খালি করার ডায়ালগ
            if (showEmptyTrashConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showEmptyTrashConfirmDialog = false },
                    title = { Text("Empty Recycle Bin?", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text("All files in Recycle Bin will be permanently deleted from mobile storage.", color = Color(0xFFB0B7C3), fontSize = 13.sp) },
                    containerColor = Color(0xFF151924),
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    LocalMediaScanner.emptyTrash(context)
                                    Toast.makeText(context, "Recycle Bin Cleared", Toast.LENGTH_SHORT).show()
                                    showEmptyTrashConfirmDialog = false
                                    loadSelectedCategoryData()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C))
                        ) {
                            Text("Delete All", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // ডায়ালগসমূহ
            if (showStoragePermissionDialog) {
                StoragePermissionDialog(
                    onDismiss = { showStoragePermissionDialog = false },
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                manageStorageLauncher.launch(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                manageStorageLauncher.launch(intent)
                            }
                        }
                    }
                )
            }

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
                        Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
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
                        coroutineScope.launch {
                            val success = LocalMediaScanner.deletePermanently(context, item.path)
                            Toast.makeText(
                                context,
                                if (success) "Deleted permanently from storage" else "File deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                            selectedFileInfoItem = null
                            loadSelectedCategoryData()
                        }
                    }
                )
            }

            if (showCreateFolderDialog) {
                CreateNewFolderDialog(
                    onDismiss = { showCreateFolderDialog = false },
                    onCreate = { newFolderName ->
                        coroutineScope.launch {
                            val path = LocalMediaScanner.createNewFolderAtRoot(newFolderName)
                            Toast.makeText(context, if (path != null) "Folder Created" else "Failed", Toast.LENGTH_SHORT).show()
                            showCreateFolderDialog = false
                            loadSelectedCategoryData()
                        }
                    }
                )
            }

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
}

private suspend fun loadCategoryDataDirect(
    context: Context,
    activeTab: MediaTabType,
    specialView: SpecialViewType,
    onLoaded: (List<LocalVideoItem>, List<LocalVideoFolder>, List<LocalVideoItem>) -> Unit
) {
    val items = when (activeTab) {
        MediaTabType.MUSIC -> LocalMediaScanner.getAllAudioTracks(context)
        MediaTabType.IMAGES -> LocalMediaScanner.getAllImages(context)
        MediaTabType.FILES -> LocalMediaScanner.getAllDocuments(context)
        MediaTabType.VIDEOS -> LocalMediaScanner.getAllVideos(context)
    }
    val folders = LocalMediaScanner.getCategoryFolders(context, activeTab.index)
    val specials = when (specialView) {
        SpecialViewType.SAFE_FOLDER -> LocalMediaScanner.getSafeFolderItems(context)
        SpecialViewType.TRASH -> LocalMediaScanner.getTrashItems(context)
        else -> emptyList()
    }
    onLoaded(items, folders, specials)
}

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
            val imageList = allList.filter {
                it.mimeType?.startsWith("image") == true ||
                        it.path.endsWith(".jpg", true) ||
                        it.path.endsWith(".png", true) ||
                        it.path.endsWith(".jpeg", true)
            }
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
