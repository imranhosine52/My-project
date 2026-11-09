@file:kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens.localplayer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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

    val tabPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { MediaTabType.entries.size }
    )

    val currentActiveTab = remember(tabPagerState.currentPage) {
        MediaTabType.entries[tabPagerState.currentPage]
    }

    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var specialView by remember { mutableStateOf(SpecialViewType.NONE) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }

    var allTabFolders by remember { mutableStateOf<Map<MediaTabType, List<LocalVideoFolder>>>(emptyMap()) }
    var allTabItems by remember { mutableStateOf<Map<MediaTabType, List<LocalVideoItem>>>(emptyMap()) }
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

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🗑️ Android 11+ Scoped Storage Permanent Delete Launcher
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "File permanently deleted from device storage", Toast.LENGTH_SHORT).show()
            selectedFileInfoItem?.let { item ->
                LocalMediaScanner.cleanUpDeletedPath(context, item.path)
            }
            selectedFileInfoItem = null
            // রিফ্রেশ করা
            coroutineScope.launch {
                loadCategoryDataDirect(context, currentActiveTab, specialView) { items, folders, specials ->
                    allTabItems = allTabItems + (currentActiveTab to items)
                    allTabFolders = allTabFolders + (currentActiveTab to folders)
                    specialItems = specials
                }
            }
        } else {
            Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
    }

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

    // 🛡️ All Files Permission Settings Launcher
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = false
                Toast.makeText(context, "All files access granted!", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    loadCategoryDataDirect(context, MediaTabType.FILES, specialView) { items, folders, specials ->
                        allTabItems = allTabItems + (MediaTabType.FILES to items)
                        allTabFolders = allTabFolders + (MediaTabType.FILES to folders)
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

    fun loadDataForTab(tab: MediaTabType) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val items = when (tab) {
                    MediaTabType.VIDEOS -> LocalMediaScanner.getAllVideos(context)
                    MediaTabType.MUSIC -> LocalMediaScanner.getAllAudioTracks(context)
                    MediaTabType.IMAGES -> LocalMediaScanner.getAllImages(context)
                    MediaTabType.FILES -> LocalMediaScanner.getAllDocuments(context)
                }
                val folders = LocalMediaScanner.getCategoryFolders(context, tab.index)
                allTabItems = allTabItems + (tab to items)
                allTabFolders = allTabFolders + (tab to folders)
            }
        }
    }

    fun loadAllMediaData() {
        coroutineScope.launch {
            when (specialView) {
                SpecialViewType.STARRED -> {
                    withContext(Dispatchers.IO) {
                        val all = LocalMediaScanner.getAllVideos(context) +
                                LocalMediaScanner.getAllAudioTracks(context) +
                                LocalMediaScanner.getAllImages(context) +
                                LocalMediaScanner.getAllDocuments(context)
                        val starredPaths = LocalMediaScanner.getStarredPaths(context)
                        specialItems = all.filter { it.path in starredPaths }
                    }
                }
                SpecialViewType.SAFE_FOLDER -> {
                    withContext(Dispatchers.IO) {
                        specialItems = LocalMediaScanner.getSafeFolderItems(context)
                    }
                }
                SpecialViewType.TRASH -> {
                    withContext(Dispatchers.IO) {
                        specialItems = LocalMediaScanner.getTrashItems(context)
                    }
                }
                SpecialViewType.NONE -> {
                    loadDataForTab(currentActiveTab)
                    MediaTabType.entries.filter { it != currentActiveTab }.forEach { tab ->
                        loadDataForTab(tab)
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) loadAllMediaData()
    }

    // =========================================================================
    // 🎯 ফিক্স ১: Files ট্যাবে প্রথমবার আসলেই পারমিশন চেক ও অটো-প্রম্পট
    // =========================================================================
    LaunchedEffect(tabPagerState.currentPage) {
        if (currentActiveTab == MediaTabType.FILES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = true
            } else {
                loadDataForTab(MediaTabType.FILES)
            }
        } else {
            loadDataForTab(currentActiveTab)
        }
    }

    LaunchedEffect(specialView, hasPermission) {
        hasPermission = permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) {
            if (specialView != SpecialViewType.NONE) {
                loadAllMediaData()
            } else {
                loadDataForTab(currentActiveTab)
            }
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
            loadAllMediaData()
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else if (tabPagerState.currentPage != 0) {
            coroutineScope.launch {
                tabPagerState.animateScrollToPage(page = 0)
            }
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
                    coroutineScope.launch {
                        drawerState.close()
                        selectedFolder = null
                        specialView = SpecialViewType.NONE
                        tabPagerState.animateScrollToPage(page = MediaTabType.MUSIC.index)
                    }
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
                                        loadAllMediaData()
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
                                else -> currentActiveTab.label
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
                                tint = if (currentActiveTab == MediaTabType.FILES) ElectricBlue else Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        selectedFolder = null
                                        coroutineScope.launch {
                                            tabPagerState.animateScrollToPage(page = MediaTabType.FILES.index)
                                        }
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

                // =============================================================
                // 📂 ২. সাব-ট্যাব বার (Folders | Videos Music Images Files)
                // =============================================================
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

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MediaTabType.entries.forEach { tab ->
                                val isSel = (tab.index == tabPagerState.currentPage)
                                Text(
                                    text = tab.label,
                                    color = if (isSel) ElectricBlue else Color(0xFF8E95A5),
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            coroutineScope.launch {
                                                tabPagerState.animateScrollToPage(page = tab.index)
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // ↔️ ৩. সোয়াইপযোগ্য পেজার (HorizontalPager)
                // =============================================================
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            loadAllMediaData()
                            delay(350)
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
                                                handleItemClick(item, displayList, currentActiveTab, context, onVideoClick) { list, idx ->
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
                                                handleItemClick(item, displayList, currentActiveTab, context, onVideoClick) { list, idx ->
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

                        // ফোল্ডার ওপেন করা থাকলে ফোল্ডারের ফাইলসমূহ
                        selectedFolder != null -> {
                            val folderMedia = (allTabItems[currentActiveTab] ?: emptyList())
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
                                                handleItemClick(item, folderMedia, currentActiveTab, context, onVideoClick) { list, idx ->
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
                                                handleItemClick(item, folderMedia, currentActiveTab, context, onVideoClick) { list, idx ->
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

                        // মূল ফোল্ডার পেজার
                        else -> {
                            HorizontalPager(
                                state = tabPagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val tab = MediaTabType.entries[pageIndex]
                                val folders = (allTabFolders[tab] ?: emptyList()).filter {
                                    it.folderName.contains(searchQuery, ignoreCase = true)
                                }

                                if (folders.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "No ${tab.label.lowercase()} folders found",
                                                color = Color(0xFF6B7280),
                                                fontSize = 13.5.sp
                                            )
                                            if (tab == MediaTabType.FILES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                                Button(
                                                    onClick = { showStoragePermissionDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Grant All Files Access", color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 100.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(folders, key = { "${tab.name}_${it.bucketId}" }) { folder ->
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
                                    loadAllMediaData()
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

            // 🛡️ All Files Permission ডায়ালগ
            if (showStoragePermissionDialog) {
                StoragePermissionDialog(
                    onDismiss = { showStoragePermissionDialog = false },
                    onGrant = {
                        showStoragePermissionDialog = false
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

            // =========================================================================
            // 🎯 ফিক্স ২: পার্মানেন্ট ডিলিট হ্যান্ডলিং
            // =========================================================================
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
                        loadAllMediaData()
                    },
                    onMoveToSafe = {
                        LocalMediaScanner.moveToSafeFolder(context, item.path)
                        Toast.makeText(context, "Moved to Safe Folder (Hidden)", Toast.LENGTH_SHORT).show()
                        selectedFileInfoItem = null
                        loadAllMediaData()
                    },
                    onRestoreFromSafe = {
                        LocalMediaScanner.restoreFromSafeFolder(context, item.path)
                        Toast.makeText(context, "Unhidden from Safe Folder", Toast.LENGTH_SHORT).show()
                        selectedFileInfoItem = null
                        loadAllMediaData()
                    },
                    onMoveToTrash = {
                        LocalMediaScanner.moveToTrash(context, item.path)
                        Toast.makeText(context, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show()
                        selectedFileInfoItem = null
                        loadAllMediaData()
                    },
                    onRestore = {
                        LocalMediaScanner.restoreFromTrash(context, item.path)
                        Toast.makeText(context, "Restored successfully", Toast.LENGTH_SHORT).show()
                        selectedFileInfoItem = null
                        loadAllMediaData()
                    },
                    onDeletePermanently = {
                        coroutineScope.launch {
                            // ১. সরাসরি ডিলিট চেষ্টা (All Files Access থাকলে সাথে সাথে সফল হবে)
                            val isDeleted = LocalMediaScanner.deletePermanently(context, item.path)
                            if (isDeleted) {
                                Toast.makeText(context, "Permanently deleted from device", Toast.LENGTH_SHORT).show()
                                selectedFileInfoItem = null
                                loadAllMediaData()
                            } else {
                                // ২. অন্য অ্যাপের ফাইল হলে Android 11+ System Delete Request ডায়ালগ তোলা
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val uriList = listOf(item.contentUri)
                                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uriList)
                                        systemDeleteLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        )
                                    } catch (e: Exception) {
                                        // যদি All Files পারমিশন মিসিং থাকে
                                        showStoragePermissionDialog = true
                                    }
                                } else {
                                    Toast.makeText(context, "Permission required to delete this file", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                            loadAllMediaData()
                        }
                    }
                )
            }

            movingItem?.let { itemToMove ->
                MoveToFolderDialog(
                    folders = allTabFolders[currentActiveTab] ?: emptyList(),
                    onDismiss = { movingItem = null },
                    onMoveToExisting = { folder ->
                        coroutineScope.launch {
                            val success = LocalMediaScanner.moveFileToDestination(context, itemToMove.path, folder.folderPath)
                            Toast.makeText(context, if (success) "Moved to ${folder.folderName}" else "Move failed", Toast.LENGTH_SHORT).show()
                            movingItem = null
                            loadAllMediaData()
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
                            loadAllMediaData()
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
                        loadAllMediaData()
                    }
                )
            }

            if (viewingImageInitialIndex != -1 && viewingImageList.isNotEmpty()) {
                FullscreenImageViewer(
                    images = viewingImageList,
                    initialIndex = viewingImageInitialIndex,
                    onClose = { viewingImageInitialIndex = -1 },
                    onImageUpdated = { loadAllMediaData() }
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
