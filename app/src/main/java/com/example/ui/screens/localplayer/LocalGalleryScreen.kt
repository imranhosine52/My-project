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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.*
import com.example.ui.screens.localplayer.gallery.*
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0D121F),
        Color(0xFF080B12),
        Color(0xFF05070B)
    )
)
private val CyanAccent = Color(0xFF00E5FF)
private val EmeraldAccent = Color(0xFF00E676)
private val DangerRed = Color(0xFFFF5252)
private val SurfaceDarkCard = Color(0xFF101624)
private val BorderSubtle = Color(0xFF1D273B)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videoItem: LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // =========================================================================
    // 🗂️ Gallery State & Navigation
    // =========================================================================
    var activeSection by remember { mutableStateOf(GallerySection.ALL_VIDEOS) }
    var allIndexedVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var folderList by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Search, View & Filter State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(VideoSortOrder(VideoSortField.DATE_ADDED, SortDirection.DESCENDING)) }
    var filterConfig by remember { mutableStateOf(GalleryFilterConfig()) }
    var showSortFilterSheet by remember { mutableStateOf(false) }

    // Multi-Selection State
    val selectedVideoIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedVideoIds.isNotEmpty()

    // Deletion Dialogs & Live Progress State
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteProgressState by remember { mutableStateOf(DeleteProgressState()) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }

    // Sub-screens & Dialogs
    var showRecycleBinScreen by remember { mutableStateOf(false) }
    var showSafePinScreen by remember { mutableStateOf(false) }
    var isSafeFolderUnlocked by remember { mutableStateOf(false) }
    var selectedActionItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var movingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }

    // Pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Permissions
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(
            permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        )
    }

    fun loadGalleryData() {
        coroutineScope.launch {
            val (videos, folders) = withContext(Dispatchers.IO) {
                val vids = LocalMediaScanner.getAllVideos(context)
                val flds = LocalMediaScanner.getCategoryFolders(context, 0)
                Pair(vids, flds)
            }
            allIndexedVideos = videos
            folderList = folders
            isLoading = false
            selectedVideoIds.removeAll { id -> videos.none { it.id == id } }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) loadGalleryData()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadGalleryData()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Voice search
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                searchQuery = text
                isSearchActive = true
            }
        }
    }

    // Storage access
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            showStoragePermissionDialog = false
            loadGalleryData()
        }
    }

    // Filtered & Sorted Videos computation
    val sectionVideos = remember(activeSection, allIndexedVideos) {
        when (activeSection) {
            GallerySection.ALL_VIDEOS, GallerySection.FOLDERS -> allIndexedVideos
            GallerySection.RECENTLY_ADDED -> {
                val fourteenDaysAgo = (System.currentTimeMillis() / 1000) - (14 * 24 * 3600)
                allIndexedVideos.filter { it.dateAdded >= fourteenDaysAgo }
            }
            GallerySection.RECENTLY_PLAYED -> {
                allIndexedVideos.filter { LocalMediaScanner.getLastPlaybackPosition(context, it.id) > 0 }
                    .sortedByDescending { LocalMediaScanner.getLastPlayedTimestamp(context, it.id) }
            }
            GallerySection.DOWNLOADS -> {
                allIndexedVideos.filter {
                    it.folderName.contains("download", ignoreCase = true) ||
                            it.path.contains("Download", ignoreCase = true)
                }
            }
            GallerySection.FAVORITES -> {
                val starred = LocalMediaScanner.getStarredPaths(context)
                allIndexedVideos.filter { it.path in starred }
            }
        }
    }

    val displayVideos = remember(sectionVideos, selectedFolder, sortOrder, filterConfig, searchQuery) {
        val baseList = if (selectedFolder != null) {
            sectionVideos.filter { it.bucketId == selectedFolder!!.bucketId }
        } else sectionVideos

        LocalMediaScanner.applyFiltersAndSorting(
            videos = baseList,
            sortOrder = sortOrder,
            filterConfig = filterConfig,
            searchQuery = searchQuery
        )
    }

    val totalSelectedBytes = remember(selectedVideoIds.toList(), allIndexedVideos) {
        allIndexedVideos.filter { selectedVideoIds.contains(it.id) }.sumOf { it.sizeBytes }
    }

    val formattedSelectedSize = remember(totalSelectedBytes) {
        val mb = totalSelectedBytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        if (gb >= 1.0) String.format(java.util.Locale.US, "%.2f GB", gb) else String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    BackHandler {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (showRecycleBinScreen) {
            showRecycleBinScreen = false
            loadGalleryData()
        } else if (showSafePinScreen) {
            showSafePinScreen = false
        } else if (isSelectionMode) {
            selectedVideoIds.clear()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else if (activeSection != GallerySection.ALL_VIDEOS) {
            activeSection = GallerySection.ALL_VIDEOS
        } else {
            onBackClick()
        }
    }

    if (showRecycleBinScreen) {
        GalleryRecycleBinScreen(
            onBackClick = {
                showRecycleBinScreen = false
                loadGalleryData()
            }
        )
        return
    }

    if (showSafePinScreen) {
        FullscreenPinScreen(
            context = context,
            onSuccess = {
                isSafeFolderUnlocked = true
                showSafePinScreen = false
                activeSection = GallerySection.FAVORITES
            },
            onBack = { showSafePinScreen = false }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode && selectedFolder == null,
        drawerContent = {
            GallerySidebarDrawerContent(
                onOpenMusic = {
                    coroutineScope.launch { drawerState.close() }
                    Toast.makeText(context, "Switching to Music Mode...", Toast.LENGTH_SHORT).show()
                },
                onOpenStarred = {
                    coroutineScope.launch { drawerState.close() }
                    activeSection = GallerySection.FAVORITES
                },
                onOpenSafeFolder = {
                    coroutineScope.launch { drawerState.close() }
                    if (isSafeFolderUnlocked) {
                        activeSection = GallerySection.FAVORITES
                    } else {
                        showSafePinScreen = true
                    }
                },
                onOpenTrash = {
                    coroutineScope.launch { drawerState.close() }
                    showRecycleBinScreen = true
                }
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // =============================================================
                // 🔝 ১. Play Drama Flix Gallery Header Bar
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isSelectionMode) {
                            IconButton(
                                onClick = { selectedVideoIds.clear() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Selection",
                                    tint = Color.White
                                )
                            }

                            Text(
                                text = "${selectedVideoIds.size} Selected",
                                color = Color.White,
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (selectedFolder != null) {
                            IconButton(
                                onClick = { selectedFolder = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Folders",
                                    tint = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = selectedFolder!!.folderName,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedFolder!!.videoCount} videos",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Play Drama Flix",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CyanAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "GALLERY",
                                            color = CyanAccent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${allIndexedVideos.size} Videos • ${folderList.size} Folders",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // হেডার অ্যাকশন বাটনসমূহ (Search, Grid/List, Sort/Filter, Multi-select)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelectionMode) {
                            TextButton(
                                onClick = {
                                    if (selectedVideoIds.size == displayVideos.size) {
                                        selectedVideoIds.clear()
                                    } else {
                                        selectedVideoIds.clear()
                                        selectedVideoIds.addAll(displayVideos.map { it.id })
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedVideoIds.size == displayVideos.size) "Deselect All" else "Select All",
                                    color = CyanAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { isSearchActive = !isSearchActive },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) CyanAccent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showSortFilterSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Sort and Filter",
                                    tint = if (filterConfig.isFilterActive) EmeraldAccent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle View",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // সার্চ বার (অ্যাক্টিভ হলে প্রদর্শিত হবে)
                AnimatedVisibility(visible = isSearchActive && !isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDarkCard)
                            .border(0.8.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search by name, folder or format...", color = TextMuted, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(CyanAccent),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = EmeraldAccent,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search video...")
                                        }
                                        voiceSearchLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Voice search not available", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    }
                }

                // =============================================================
                // 📑 ২. স্টিকি ৬-সেকশন ন্যাভিগেশন বার (Sticky Gallery Section Tabs)
                // =============================================================
                if (selectedFolder == null && !isSelectionMode) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GallerySection.entries.toTypedArray()) { section ->
                            val isSelected = (section == activeSection)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) CyanAccent.copy(alpha = 0.18f) else SurfaceDarkCard,
                                border = BorderStroke(
                                    width = if (isSelected) 1.dp else 0.8.dp,
                                    color = if (isSelected) CyanAccent else BorderSubtle
                                ),
                                modifier = Modifier
                                    .clickable { activeSection = section }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = section.displayName,
                                        color = if (isSelected) CyanAccent else TextMuted,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 🎬 ৩. ভিডিও গ্রিড / ফোল্ডার ব্রাউজার বডি (Pull-to-refresh সহ)
                // =============================================================
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            loadGalleryData()
                            delay(350)
                            isRefreshing = false
                        }
                    },
                    state = pullRefreshState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.5.dp)
                            }
                        }

                        // 📂 ফোল্ডার মোড
                        activeSection == GallerySection.FOLDERS && selectedFolder == null -> {
                            val displayFolders = remember(folderList, searchQuery) {
                                if (searchQuery.isBlank()) folderList
                                else folderList.filter { it.folderName.contains(searchQuery, ignoreCase = true) }
                            }

                            if (displayFolders.isEmpty()) {
                                EmptyGalleryNotice("No video folders found")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 90.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayFolders, key = { it.bucketId }) { folder ->
                                        ScreenshotStyleFolderItem(
                                            folder = folder,
                                            onClick = { selectedFolder = folder }
                                        )
                                    }
                                }
                            }
                        }

                        // 🎥 ভিডিও লিস্ট / গ্রিড
                        displayVideos.isEmpty() -> {
                            EmptyGalleryNotice(
                                message = if (searchQuery.isNotBlank()) "No videos match '$searchQuery'"
                                else "No videos in ${activeSection.displayName}"
                            )
                        }

                        isGridView -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    top = 6.dp,
                                    end = 8.dp,
                                    bottom = if (isSelectionMode) 100.dp else 80.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayVideos, key = { it.id }) { item ->
                                    val isSelected = selectedVideoIds.contains(item.id)

                                    GalleryVideoGridCard(
                                        item = item,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedVideoIds.remove(item.id)
                                                else selectedVideoIds.add(item.id)
                                            } else {
                                                onVideoClick(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedVideoIds.add(item.id)
                                            }
                                        },
                                        onMenuClick = { selectedActionItem = item }
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = 6.dp,
                                    end = 12.dp,
                                    bottom = if (isSelectionMode) 100.dp else 80.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayVideos, key = { it.id }) { item ->
                                    val isSelected = selectedVideoIds.contains(item.id)

                                    GalleryVideoListCard(
                                        item = item,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedVideoIds.remove(item.id)
                                                else selectedVideoIds.add(item.id)
                                            } else {
                                                onVideoClick(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedVideoIds.add(item.id)
                                            }
                                        },
                                        onMenuClick = { selectedActionItem = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =============================================================
            // ⚡ ৪. সিলেকশন মোড ফ্লোটিং বটম অ্যাকশন ডক (Floating Action Dock)
            // =============================================================
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color(0xFF141926),
                    border = BorderStroke(1.dp, Color(0xFF232F46)),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedVideoIds.size} Videos Selected",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total size: $formattedSelectedSize",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 📤 Share Selected
                            IconButton(
                                onClick = {
                                    val toShare = allIndexedVideos.filter { selectedVideoIds.contains(it.id) }
                                    if (toShare.isNotEmpty()) {
                                        shareSingleFile(context, toShare.first())
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceDarkCard)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            // ⭐ Star Selected
                            IconButton(
                                onClick = {
                                    val toStar = allIndexedVideos.filter { selectedVideoIds.contains(it.id) }
                                    toStar.forEach { LocalMediaScanner.toggleStarred(context, it.path) }
                                    Toast.makeText(context, "Favorites updated", Toast.LENGTH_SHORT).show()
                                    selectedVideoIds.clear()
                                    loadGalleryData()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceDarkCard)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Star", tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            }

                            // 🗑️ Delete Selected (Triggers Dual-Option Dialog)
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Delete (${selectedVideoIds.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =============================================================
            // 🗑️ ৫. ডিলিশন ও লাইভ প্রগ্রেস ডায়ালগসমূহ
            // =============================================================
            if (showDeleteConfirmDialog) {
                DeleteConfirmationDialog(
                    selectedCount = selectedVideoIds.size,
                    formattedTotalSize = formattedSelectedSize,
                    onConfirm = { option ->
                        showDeleteConfirmDialog = false
                        showDeleteProgressDialog = true

                        val targetItems = allIndexedVideos.filter { selectedVideoIds.contains(it.id) }

                        coroutineScope.launch {
                            LocalMediaScanner.executeBatchDelete(
                                context = context,
                                items = targetItems,
                                option = option,
                                onProgress = { progress ->
                                    deleteProgressState = progress
                                }
                            )
                        }
                    },
                    onDismiss = { showDeleteConfirmDialog = false }
                )
            }

            if (showDeleteProgressDialog) {
                LiveDeletionProgressDialog(
                    progressState = deleteProgressState,
                    onCancel = {
                        showDeleteProgressDialog = false
                        selectedVideoIds.clear()
                        loadGalleryData()
                    },
                    onDone = {
                        showDeleteProgressDialog = false
                        selectedVideoIds.clear()
                        loadGalleryData()
                    }
                )
            }

            // =============================================================
            // ⚙️ ৬. সর্ট ও ফিল্টার বটম শিট
            // =============================================================
            if (showSortFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSortFilterSheet = false },
                    containerColor = Color(0xFF131826)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Sort & Filter Videos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

                        // সর্টিং অপশন
                        Text("Sort By", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VideoSortField.entries.forEach { field ->
                                val isSel = (sortOrder.field == field)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) CyanAccent.copy(alpha = 0.2f) else SurfaceDarkCard,
                                    border = BorderStroke(0.8.dp, if (isSel) CyanAccent else BorderSubtle),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            sortOrder = sortOrder.copy(
                                                field = field,
                                                direction = if (isSel) {
                                                    if (sortOrder.direction == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
                                                } else SortDirection.DESCENDING
                                            )
                                        }
                                        .padding(vertical = 8.dp),
                                    content = {
                                        Text(
                                            text = field.label,
                                            color = if (isSel) CyanAccent else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        // রেজোলিউশন ফিল্টার
                        Text("Resolution", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(VideoResolutionFilter.entries.toTypedArray()) { res ->
                                val isSel = (filterConfig.resolutionFilter == res)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) EmeraldAccent.copy(alpha = 0.2f) else SurfaceDarkCard,
                                    border = BorderStroke(0.8.dp, if (isSel) EmeraldAccent else BorderSubtle),
                                    modifier = Modifier.clickable {
                                        filterConfig = filterConfig.copy(resolutionFilter = res)
                                    }
                                ) {
                                    Text(
                                        text = res.label,
                                        color = if (isSel) EmeraldAccent else Color.White,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // ডিউরেশন ফিল্টার
                        Text("Duration", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(VideoDurationFilter.entries.toTypedArray()) { dur ->
                                val isSel = (filterConfig.durationFilter == dur)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) EmeraldAccent.copy(alpha = 0.2f) else SurfaceDarkCard,
                                    border = BorderStroke(0.8.dp, if (isSel) EmeraldAccent else BorderSubtle),
                                    modifier = Modifier.clickable {
                                        filterConfig = filterConfig.copy(durationFilter = dur)
                                    }
                                ) {
                                    Text(
                                        text = dur.label,
                                        color = if (isSel) EmeraldAccent else Color.White,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showSortFilterSheet = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Apply Filters", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ৩-ডট ইন্ডিভিজুয়াল অ্যাকশন মেনু
            selectedActionItem?.let { item ->
                FileActionMenuSheet(
                    item = item,
                    onDismiss = { selectedActionItem = null },
                    onShare = {
                        shareSingleFile(context, item)
                        selectedActionItem = null
                    },
                    onRename = {
                        renamingItem = item
                        selectedActionItem = null
                    },
                    onMove = {
                        movingItem = item
                        selectedActionItem = null
                    },
                    onToggleStar = {
                        LocalMediaScanner.toggleStarred(context, item.path)
                        selectedActionItem = null
                        loadGalleryData()
                    },
                    onMoveToSafe = {
                        LocalMediaScanner.moveToSafeFolder(context, item.path)
                        Toast.makeText(context, "Moved to Safe Folder", Toast.LENGTH_SHORT).show()
                        selectedActionItem = null
                        loadGalleryData()
                    },
                    onMoveToTrash = {
                        selectedActionItem = null
                        selectedVideoIds.clear()
                        selectedVideoIds.add(item.id)
                        showDeleteConfirmDialog = true
                    }
                )
            }

            // রিনেম ডায়ালগ
            renamingItem?.let { item ->
                RenameFileDialog(
                    currentName = item.displayName.substringBeforeLast('.'),
                    onDismiss = { renamingItem = null },
                    onSave = { newName ->
                        val ok = LocalMediaScanner.renameFile(context, item.path, newName)
                        Toast.makeText(context, if (ok) "Renamed successfully" else "Rename failed", Toast.LENGTH_SHORT).show()
                        renamingItem = null
                        loadGalleryData()
                    }
                )
            }

            // মুভ ডায়ালগ
            movingItem?.let { item ->
                MoveToFolderDialog(
                    folders = folderList,
                    onDismiss = { movingItem = null },
                    onMoveToExisting = { folder ->
                        coroutineScope.launch {
                            val ok = LocalMediaScanner.moveFileToDestination(context, item.path, folder.folderPath)
                            Toast.makeText(context, if (ok) "Moved to ${folder.folderName}" else "Move failed", Toast.LENGTH_SHORT).show()
                            movingItem = null
                            loadGalleryData()
                        }
                    },
                    onCreateAndMove = { folderName ->
                        coroutineScope.launch {
                            val path = LocalMediaScanner.createNewFolderAtRoot(folderName)
                            if (path != null) {
                                val ok = LocalMediaScanner.moveFileToDestination(context, item.path, path)
                                Toast.makeText(context, if (ok) "Moved to $folderName" else "Move failed", Toast.LENGTH_SHORT).show()
                            }
                            movingItem = null
                            loadGalleryData()
                        }
                    }
                )
            }

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
        }
    }
}

@Composable
private fun EmptyGalleryNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141A26)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = message,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pull down to refresh or check your phone storage permissions.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
