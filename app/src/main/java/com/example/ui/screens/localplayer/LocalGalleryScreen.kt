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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.*
import com.example.ui.ShortTvCustomStackedIcon
import com.example.ui.screens.localplayer.gallery.*
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ScreenBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0D121F),
        Color(0xFF080B12),
        Color(0xFF05070B)
    )
)
private val ElectricCyan = Color(0xFF00E5FF)
private val NeonGreen = Color(0xFF00E676)
private val DangerRed = Color(0xFFFF5252)
private val GoldAccent = Color(0xFFFFB300)
private val SurfaceDarkCard = Color(0xFF101624)
private val BorderSubtle = Color(0xFF1D273B)
private val TextMuted = Color(0xFF8E95A5)

/**
 * 🧭 পাথ-মেমোরি হেল্পার: ভিডিও প্লেয়ার থেকে ব্যাক করলে হুবহু সেই ট্যাব ও ফোল্ডারে ফিরিয়ে আনে
 */
object GalleryNavMemory {
    var activeTabPageIndex: Int = 0 // 0: Folders, 1: Videos, 2: Music, 3: Images, 4: Files
    var activeFolderBucketId: String? = null
}

/**
 * ⚡ ইন-মেমোরি ফাস্ট ফোল্ডার জেনারেটর
 */
private fun groupItemsIntoFoldersFast(items: List<LocalVideoItem>): List<LocalVideoFolder> {
    return items.groupBy { it.bucketId.ifBlank { it.folderName.hashCode().toString() } }
        .map { (bId, list) ->
            val first = list.first()
            val totalSize = list.sumOf { it.sizeBytes }
            val path = try { File(first.path).parent ?: first.folderName } catch (_: Exception) { first.folderName }
            LocalVideoFolder(
                bucketId = bId,
                folderName = first.folderName,
                folderPath = path,
                videoCount = list.size,
                totalSizeBytes = totalSize,
                thumbnailUriString = first.contentUriString
            )
        }.sortedByDescending { it.videoCount }
}

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (mediaItem: LocalVideoItem) -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToShorts: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 🎯 পাথ মেমোরি থেকে পূর্বে থাকা ট্যাব পুনরুদ্ধার
    val tabPagerState = rememberPagerState(
        initialPage = GalleryNavMemory.activeTabPageIndex.coerceIn(0, 4),
        pageCount = { 5 }
    )

    // পেজ পরিবর্তন হলে মেমোরিতে ট্র্যাক রাখা
    LaunchedEffect(tabPagerState.currentPage) {
        GalleryNavMemory.activeTabPageIndex = tabPagerState.currentPage
    }

    var allIndexedVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var allIndexedAudios by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var allIndexedImages by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var allIndexedFiles by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }

    var videoFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(VideoSortOrder(VideoSortField.DATE_ADDED, SortDirection.DESCENDING)) }
    var filterConfig by remember { mutableStateOf(GalleryFilterConfig()) }
    var showSortFilterSheet by remember { mutableStateOf(false) }

    val selectedVideoIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedVideoIds.isNotEmpty()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteProgressState by remember { mutableStateOf(DeleteProgressState()) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }

    // 🌟 Starred Items আলাদা পেজ স্টেট
    var showStarredScreen by remember { mutableStateOf(false) }

    var showRecycleBinScreen by remember { mutableStateOf(false) }
    var showSafePinScreen by remember { mutableStateOf(false) }
    var isSafeFolderUnlocked by remember { mutableStateOf(false) }
    var selectedActionItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var movingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }

    var fullscreenImageIndex by remember { mutableIntStateOf(-1) }

    var isSeeMoreMenuExpanded by remember { mutableStateOf(false) }
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

    // =========================================================================
    // ⚡ ইনস্ট্যান্ট আল্ট্রা-ফাস্ট মিডিয়া লোডার (< 1s)
    // =========================================================================
    fun loadGalleryData() {
        coroutineScope.launch {
            val vids = withContext(Dispatchers.IO) {
                LocalMediaScanner.getAllVideos(context)
            }
            allIndexedVideos = vids
            val folders = groupItemsIntoFoldersFast(vids)
            videoFolders = folders

            // 🎯 পাথ মেমোরিতে থাকা পূর্বে ওপেন করা ফোল্ডার স্বয়ংক্রিয়ভাবে পুনরুদ্ধার
            if (GalleryNavMemory.activeFolderBucketId != null && selectedFolder == null) {
                selectedFolder = folders.find { it.bucketId == GalleryNavMemory.activeFolderBucketId }
            }

            isLoading = false

            // ব্যাকগ্রাউন্ড থ্রেডে প্যারালাল অ্যাসুরেন্সে অডিও, ইমেজ ও ফাইল লোড
            launch(Dispatchers.IO) {
                val audsDeferred = async { LocalMediaScanner.getAllAudioTracks(context) }
                val imgsDeferred = async { LocalMediaScanner.getAllImages(context) }
                val docsDeferred = async { LocalMediaScanner.getAllDocuments(context) }

                allIndexedAudios = audsDeferred.await()
                allIndexedImages = imgsDeferred.await()
                allIndexedFiles = docsDeferred.await()
            }
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

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            showStoragePermissionDialog = false
            loadGalleryData()
        }
    }

    val currentActiveItems = remember(tabPagerState.currentPage, allIndexedVideos, allIndexedAudios, allIndexedImages, allIndexedFiles) {
        when (tabPagerState.currentPage) {
            0, 1 -> allIndexedVideos
            2 -> allIndexedAudios
            3 -> allIndexedImages
            4 -> allIndexedFiles
            else -> allIndexedVideos
        }
    }

    val totalSelectedBytes = remember(selectedVideoIds.toList(), currentActiveItems) {
        currentActiveItems.filter { selectedVideoIds.contains(it.id) }.sumOf { it.sizeBytes }
    }

    val formattedSelectedSize = remember(totalSelectedBytes) {
        val mb = totalSelectedBytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        if (gb >= 1.0) String.format(java.util.Locale.US, "%.2f GB", gb) else String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    // =========================================================================
    // 🔙 ১০০% নিখুঁত পাথ-ফলোয়িং ব্যাক ন্যাভিগেশন
    // =========================================================================
    BackHandler {
        if (fullscreenImageIndex != -1) {
            fullscreenImageIndex = -1
        } else if (showStarredScreen) {
            showStarredScreen = false
            loadGalleryData()
        } else if (isSeeMoreMenuExpanded) {
            isSeeMoreMenuExpanded = false
        } else if (drawerState.isOpen) {
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
            // ফোল্ডার থেকে বের হওয়া
            selectedFolder = null
            GalleryNavMemory.activeFolderBucketId = null
        } else if (tabPagerState.currentPage != 0) {
            // যেকোনো ট্যাব থেকে Folders ট্যাবে ফেরত
            coroutineScope.launch {
                tabPagerState.animateScrollToPage(0)
            }
        } else {
            // গ্যালারি স্ক্রিন এক্সিট
            onBackClick()
        }
    }

    // ফুলস্ক্রিন ইমেজ ভিউয়ার
    if (fullscreenImageIndex in allIndexedImages.indices) {
        FullscreenImageViewer(
            images = allIndexedImages,
            initialIndex = fullscreenImageIndex,
            onClose = { fullscreenImageIndex = -1 },
            onImageUpdated = { loadGalleryData() }
        )
        return
    }

    // 🌟 Starred Items আলাদা ডেডিকেটেড পেজ
    if (showStarredScreen) {
        val allStarredItems = remember(allIndexedVideos, allIndexedAudios, allIndexedImages, allIndexedFiles) {
            val starredPaths = LocalMediaScanner.getStarredPaths(context)
            (allIndexedVideos + allIndexedAudios + allIndexedImages + allIndexedFiles).filter { it.path in starredPaths }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Starred পেজ হেডার
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
                        IconButton(
                            onClick = { showStarredScreen = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                Text("Starred Items", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${allStarredItems.size} favorites", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)

                if (allStarredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(46.dp))
                            Text("No Starred Items Yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Star your favorite videos or songs to see them here.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allStarredItems, key = { it.id }) { item ->
                            GalleryVideoListCard(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                onClick = {
                                    if (item.mimeType?.startsWith("image") == true) {
                                        fullscreenImageIndex = allIndexedImages.indexOfFirst { it.id == item.id }
                                    } else {
                                        onVideoClick(item)
                                    }
                                },
                                onLongClick = {},
                                onMenuClick = { selectedActionItem = item }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    if (showRecycleBinScreen) {
        GalleryRecycleBinScreen(
            onBackClick = {
                showRecycleBinScreen = false
                loadBinDataSafe(context) { loadGalleryData() }
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
                Toast.makeText(context, "Safe Folder Unlocked", Toast.LENGTH_SHORT).show()
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
                    coroutineScope.launch {
                        drawerState.close()
                        selectedFolder = null
                        GalleryNavMemory.activeFolderBucketId = null
                        tabPagerState.animateScrollToPage(2)
                    }
                },
                onOpenStarred = {
                    coroutineScope.launch {
                        drawerState.close()
                        // 🌟 Starred Items আলাদা পেজ ওপেন
                        showStarredScreen = true
                    }
                },
                onOpenSafeFolder = {
                    coroutineScope.launch { drawerState.close() }
                    showSafePinScreen = true
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
                // 🔝 ১. টপ অ্যাকশন বার
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                            }
                            Text(
                                text = "${selectedVideoIds.size} Selected",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (selectedFolder != null) {
                            IconButton(
                                onClick = {
                                    selectedFolder = null
                                    GalleryNavMemory.activeFolderBucketId = null
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Column {
                                Text(
                                    text = selectedFolder!!.folderName,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedFolder!!.videoCount} items",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                            Text(
                                text = "Media Gallery",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelectionMode) {
                            TextButton(
                                onClick = {
                                    if (selectedVideoIds.size == currentActiveItems.size) {
                                        selectedVideoIds.clear()
                                    } else {
                                        selectedVideoIds.clear()
                                        selectedVideoIds.addAll(currentActiveItems.map { it.id })
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedVideoIds.size == currentActiveItems.size) "Deselect" else "Select All",
                                    color = ElectricCyan,
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
                                    tint = if (isSearchActive) ElectricCyan else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showSortFilterSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (filterConfig.isFilterActive) NeonGreen else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // সার্চ বার
                AnimatedVisibility(visible = isSearchActive && !isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDarkCard)
                            .border(0.8.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search media or folders...", color = TextMuted, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(ElectricCyan),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp).clickable { searchQuery = "" }
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp).clickable {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search media...")
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
                // 📑 ২. ছবি ১ এর হুবহু টপ ন্যাভিগেশন বার
                // =============================================================
                if (selectedFolder == null && !isSelectionMode) {
                    val currentPage = tabPagerState.currentPage

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Folders",
                            color = if (currentPage == 0) ElectricCyan else Color.White.copy(alpha = 0.65f),
                            fontSize = 16.5.sp,
                            fontWeight = if (currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    coroutineScope.launch {
                                        tabPagerState.animateScrollToPage(0)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rightTabs = listOf(
                                Pair(1, "Videos"),
                                Pair(2, "Music"),
                                Pair(3, "Images"),
                                Pair(4, "Files")
                            )

                            rightTabs.forEach { (targetPage, label) ->
                                val isSelected = (currentPage == targetPage)
                                val textColor = if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.55f)

                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            coroutineScope.launch {
                                                tabPagerState.animateScrollToPage(targetPage)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // ↔️ ৩. ফ্লুইড সোয়াইপেবল HorizontalPager
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
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricCyan, strokeWidth = 2.5.dp)
                        }
                    } else if (selectedFolder != null) {
                        val inFolder = currentActiveItems.filter { it.bucketId == selectedFolder!!.bucketId }
                        val displayFolderItems = remember(inFolder, sortOrder, filterConfig, searchQuery) {
                            LocalMediaScanner.applyFiltersAndSorting(
                                videos = inFolder,
                                sortOrder = sortOrder,
                                filterConfig = filterConfig,
                                searchQuery = searchQuery
                            )
                        }

                        if (displayFolderItems.isEmpty()) {
                            EmptyGalleryNotice("No items found in this folder")
                        } else if (isGridView && tabPagerState.currentPage != 2) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayFolderItems, key = { it.id }) { item ->
                                    val isSelected = selectedVideoIds.contains(item.id)
                                    GalleryVideoGridCard(
                                        item = item,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                            } else if (item.mimeType?.startsWith("image") == true) {
                                                fullscreenImageIndex = allIndexedImages.indexOfFirst { it.id == item.id }
                                            } else {
                                                // 🎯 পাথ মেমোরি সেভ করে ভিডিও ওপেন
                                                GalleryNavMemory.activeFolderBucketId = selectedFolder?.bucketId
                                                onVideoClick(item)
                                            }
                                        },
                                        onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                        onMenuClick = { selectedActionItem = item }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayFolderItems, key = { it.id }) { item ->
                                    val isSelected = selectedVideoIds.contains(item.id)
                                    GalleryVideoListCard(
                                        item = item,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                            } else if (item.mimeType?.startsWith("image") == true) {
                                                fullscreenImageIndex = allIndexedImages.indexOfFirst { it.id == item.id }
                                            } else {
                                                // 🎯 পাথ মেমোরি সেভ করে ভিডিও ওপেন
                                                GalleryNavMemory.activeFolderBucketId = selectedFolder?.bucketId
                                                onVideoClick(item)
                                            }
                                        },
                                        onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                        onMenuClick = { selectedActionItem = item }
                                    )
                                }
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = tabPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                // পেজ ০: Folders
                                0 -> {
                                    val displayFolders = remember(videoFolders, searchQuery) {
                                        if (searchQuery.isBlank()) videoFolders
                                        else videoFolders.filter { it.folderName.contains(searchQuery, ignoreCase = true) }
                                    }

                                    if (displayFolders.isEmpty()) {
                                        EmptyGalleryNotice("No video folders found")
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 100.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(displayFolders, key = { it.bucketId }) { folder ->
                                                ScreenshotStyleFolderItem(
                                                    folder = folder,
                                                    onClick = {
                                                        selectedFolder = folder
                                                        GalleryNavMemory.activeFolderBucketId = folder.bucketId
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // পেজ ১: Videos
                                1 -> {
                                    val displayVideos = remember(allIndexedVideos, sortOrder, filterConfig, searchQuery) {
                                        LocalMediaScanner.applyFiltersAndSorting(
                                            videos = allIndexedVideos,
                                            sortOrder = sortOrder,
                                            filterConfig = filterConfig,
                                            searchQuery = searchQuery
                                        )
                                    }

                                    if (displayVideos.isEmpty()) {
                                        EmptyGalleryNotice("No videos found")
                                    } else if (isGridView) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            contentPadding = PaddingValues(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 100.dp),
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
                                                            if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                                        } else {
                                                            onVideoClick(item)
                                                        }
                                                    },
                                                    onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                                    onMenuClick = { selectedActionItem = item }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 100.dp),
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
                                                            if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                                        } else {
                                                            onVideoClick(item)
                                                        }
                                                    },
                                                    onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                                    onMenuClick = { selectedActionItem = item }
                                                )
                                            }
                                        }
                                    }
                                }

                                // পেজ ২: Music
                                2 -> {
                                    val displayAudios = remember(allIndexedAudios, searchQuery) {
                                        if (searchQuery.isBlank()) allIndexedAudios
                                        else allIndexedAudios.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                                    }

                                    if (displayAudios.isEmpty()) {
                                        EmptyGalleryNotice("No audio tracks found")
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 100.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(displayAudios, key = { it.id }) { item ->
                                                val isSelected = selectedVideoIds.contains(item.id)
                                                GalleryVideoListCard(
                                                    item = item,
                                                    isSelected = isSelected,
                                                    isSelectionMode = isSelectionMode,
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                                        } else {
                                                            onVideoClick(item)
                                                        }
                                                    },
                                                    onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                                    onMenuClick = { selectedActionItem = item }
                                                )
                                            }
                                        }
                                    }
                                }

                                // পেজ ৩: Images
                                3 -> {
                                    val displayImages = remember(allIndexedImages, searchQuery) {
                                        if (searchQuery.isBlank()) allIndexedImages
                                        else allIndexedImages.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                                    }

                                    if (displayImages.isEmpty()) {
                                        EmptyGalleryNotice("No images found")
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            contentPadding = PaddingValues(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 100.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(displayImages, key = { it.id }) { item ->
                                                val isSelected = selectedVideoIds.contains(item.id)
                                                GalleryVideoGridCard(
                                                    item = item,
                                                    isSelected = isSelected,
                                                    isSelectionMode = isSelectionMode,
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                                        } else {
                                                            fullscreenImageIndex = allIndexedImages.indexOfFirst { it.id == item.id }
                                                        }
                                                    },
                                                    onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                                    onMenuClick = { selectedActionItem = item }
                                                )
                                            }
                                        }
                                    }
                                }

                                // পেজ ৪: Files
                                4 -> {
                                    val displayFiles = remember(allIndexedFiles, searchQuery) {
                                        if (searchQuery.isBlank()) allIndexedFiles
                                        else allIndexedFiles.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                                    }

                                    if (displayFiles.isEmpty()) {
                                        EmptyGalleryNotice("No files found")
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 100.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(displayFiles, key = { it.id }) { item ->
                                                val isSelected = selectedVideoIds.contains(item.id)
                                                GalleryVideoListCard(
                                                    item = item,
                                                    isSelected = isSelected,
                                                    isSelectionMode = isSelectionMode,
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            if (isSelected) selectedVideoIds.remove(item.id) else selectedVideoIds.add(item.id)
                                                        } else {
                                                            onVideoClick(item)
                                                        }
                                                    },
                                                    onLongClick = { if (!isSelectionMode) selectedVideoIds.add(item.id) },
                                                    onMenuClick = { selectedActionItem = item }
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

            // =============================================================
            // ⚡ ৪. সিলেকশন মোড ফ্লোটিং বটম অ্যাকশন ডক
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
                                text = "${selectedVideoIds.size} Selected",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Size: $formattedSelectedSize",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val toShare = currentActiveItems.filter { selectedVideoIds.contains(it.id) }
                                    if (toShare.isNotEmpty()) shareSingleFile(context, toShare.first())
                                },
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(SurfaceDarkCard)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val toStar = currentActiveItems.filter { selectedVideoIds.contains(it.id) }
                                    toStar.forEach { LocalMediaScanner.toggleStarred(context, it.path) }
                                    Toast.makeText(context, "Favorites updated", Toast.LENGTH_SHORT).show()
                                    selectedVideoIds.clear()
                                    loadGalleryData()
                                },
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(SurfaceDarkCard)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Star", tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            }

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
            // 🌟 ৫. এক সাইডে শুধু "More" বাটন ও ক্লিন ফ্রস্টেড পপ-আপ মেনু
            // =============================================================
            if (!isSelectionMode) {
                FloatingMoreSpeedDialNav(
                    isExpanded = isSeeMoreMenuExpanded,
                    onToggleExpand = { isSeeMoreMenuExpanded = !isSeeMoreMenuExpanded },
                    onNavigateToHome = {
                        isSeeMoreMenuExpanded = false
                        onNavigateToHome()
                    },
                    onNavigateToShorts = {
                        isSeeMoreMenuExpanded = false
                        onNavigateToShorts()
                    },
                    onNavigateToDownloads = {
                        isSeeMoreMenuExpanded = false
                        onNavigateToDownloads()
                    },
                    onNavigateToProfile = {
                        isSeeMoreMenuExpanded = false
                        onNavigateToProfile()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp, end = 16.dp)
                )
            }

            // =============================================================
            // 🗑️ ডিলিট ডায়ালগসমূহ
            // =============================================================
            if (showDeleteConfirmDialog) {
                DeleteConfirmationDialog(
                    selectedCount = selectedVideoIds.size,
                    formattedTotalSize = formattedSelectedSize,
                    onConfirm = { option ->
                        showDeleteConfirmDialog = false
                        showDeleteProgressDialog = true

                        val targetItems = currentActiveItems.filter { selectedVideoIds.contains(it.id) }
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

            // সর্ট ও ফিল্টার বটম শিট
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
                        Text("Sort & Filter", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

                        Text("Sort By", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VideoSortField.entries.forEach { field ->
                                val isSel = (sortOrder.field == field)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) ElectricCyan.copy(alpha = 0.2f) else SurfaceDarkCard,
                                    border = BorderStroke(0.8.dp, if (isSel) ElectricCyan else BorderSubtle),
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
                                            color = if (isSel) ElectricCyan else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { showSortFilterSheet = false },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("Apply Filters", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ৩-ডট ফাইল অ্যাকশন মেনু
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

            movingItem?.let { item ->
                MoveToFolderDialog(
                    folders = videoFolders,
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

private fun loadBinDataSafe(context: Context, onDone: () -> Unit) {
    onDone()
}

// =============================================================================
// 🌟 এক নম্বর ছবির মতো বর্ডারবিহীন "More" বাটন ও ফ্রস্টেড পপ-আপ নেভিগেশন
// =============================================================================
@Composable
private fun FloatingMoreSpeedDialNav(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(150)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                PopUpNavItem(
                    label = "Home",
                    icon = Icons.Outlined.Home,
                    iconColor = Color(0xFF00E5FF),
                    onClick = onNavigateToHome
                )
                PopUpNavItem(
                    label = "Short TV",
                    customIcon = { tint -> ShortTvCustomStackedIcon(tint = tint, modifier = Modifier.size(20.dp)) },
                    iconColor = Color(0xFF00E676),
                    onClick = onNavigateToShorts
                )
                PopUpNavItem(
                    label = "Downloads",
                    icon = Icons.Outlined.FileDownload,
                    iconColor = Color(0xFFFFB300),
                    onClick = onNavigateToDownloads
                )
                PopUpNavItem(
                    label = "Profile",
                    icon = Icons.Outlined.Person,
                    iconColor = Color(0xFFD49BFF),
                    onClick = onNavigateToProfile
                )
            }
        }

        Surface(
            onClick = onToggleExpand,
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.28f),
            border = null,
            shadowElevation = 0.dp,
            modifier = Modifier.wrapContentSize()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle More",
                    tint = if (isExpanded) ElectricCyan else Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = if (isExpanded) "Close" else "More",
                    color = if (isExpanded) ElectricCyan else Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PopUpNavItem(
    label: String,
    icon: ImageVector? = null,
    customIcon: (@Composable (Color) -> Unit)? = null,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(0.8.dp, iconColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (customIcon != null) {
                customIcon(iconColor)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
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
                    imageVector = Icons.Default.FolderOpen,
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
                text = "Pull down to refresh or check phone storage.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
