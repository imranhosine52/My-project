@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.data.model.StorageCategorySummary
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PureBlack = Color(0xFF090A0F)
private val CardSurfaceDark = Color(0xFF141722)
private val CardBorderDark = Color(0xFF222638)
private val ElectricBlue = Color(0xFF2979FF)

enum class StorageViewSection {
    DASHBOARD,
    CATEGORY_CONTENT,
    STARRED,
    SAFE_FOLDER,
    TRASH
}

@Composable
fun LocalGalleryScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videoItem: LocalVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentSection by remember { mutableStateOf(StorageViewSection.DASHBOARD) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedCategoryTitle by remember { mutableStateOf("Videos") }
    var selectedFolder by remember { mutableStateOf<LocalVideoFolder?>(null) }

    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<Long>() }

    // Dialogs & Sheets
    var selectedFileInfoItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var renamingItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var viewingImageItem by remember { mutableStateOf<LocalVideoItem?>(null) }
    var showSafePinDialog by remember { mutableStateOf(false) }

    var summaryData by remember { mutableStateOf(StorageCategorySummary()) }
    var currentItems by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var currentFolders by remember { mutableStateOf<List<LocalVideoFolder>>(emptyList()) }

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

    fun refreshDashboardAndData() {
        if (!hasPermission) return
        coroutineScope.launch {
            isLoading = true
            summaryData = LocalMediaScanner.getStorageSummary(context)
            currentItems = when (currentSection) {
                StorageViewSection.STARRED -> {
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context)
                    val starSet = LocalMediaScanner.getStarredPaths(context)
                    all.filter { it.path in starSet }
                }
                StorageViewSection.SAFE_FOLDER -> {
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context)
                    val safeSet = LocalMediaScanner.getSafePaths(context)
                    all.filter { it.path in safeSet }
                }
                StorageViewSection.TRASH -> {
                    val all = LocalMediaScanner.getAllVideos(context) + LocalMediaScanner.getAllAudioTracks(context) + LocalMediaScanner.getAllImages(context)
                    val trashSet = LocalMediaScanner.getTrashPaths(context)
                    all.filter { it.path in trashSet }
                }
                StorageViewSection.CATEGORY_CONTENT -> {
                    when (selectedCategoryIndex) {
                        1 -> LocalMediaScanner.getAllImages(context)
                        2 -> LocalMediaScanner.getAllAudioTracks(context)
                        3 -> LocalMediaScanner.getAllDocuments(context)
                        4 -> LocalMediaScanner.getAllDownloads(context)
                        5 -> LocalMediaScanner.getAllApks(context)
                        else -> LocalMediaScanner.getAllVideos(context)
                    }
                }
                else -> emptyList()
            }
            currentFolders = LocalMediaScanner.getCategoryFolders(context, selectedCategoryIndex)
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) refreshDashboardAndData()
    }

    LaunchedEffect(currentSection, selectedCategoryIndex, hasPermission) {
        if (hasPermission) {
            refreshDashboardAndData()
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    BackHandler {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedItemIds.clear()
        } else if (viewingImageItem != null) {
            viewingImageItem = null
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else if (currentSection != StorageViewSection.DASHBOARD) {
            currentSection = StorageViewSection.DASHBOARD
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
            // 🔝 ১. টপ অ্যাকশন বার (ম্যানেজার বা মাল্টি-সিলেক্ট বার)
            // =========================================================================
            if (isMultiSelectMode) {
                MultiSelectActionBar(
                    selectedCount = selectedItemIds.size,
                    onSelectAll = {
                        selectedItemIds.clear()
                        selectedItemIds.addAll(currentItems.map { it.id })
                    },
                    onShareBatch = {
                        val selectedItems = currentItems.filter { it.id in selectedItemIds }
                        shareMultipleFiles(context, selectedItems)
                    },
                    onDeleteBatch = {
                        val selectedItems = currentItems.filter { it.id in selectedItemIds }
                        selectedItems.forEach { LocalMediaScanner.moveToTrash(context, it.path) }
                        Toast.makeText(context, "${selectedItems.size} items moved to Trash", Toast.LENGTH_SHORT).show()
                        isMultiSelectMode = false
                        selectedItemIds.clear()
                        refreshDashboardAndData()
                    },
                    onClose = {
                        isMultiSelectMode = false
                        selectedItemIds.clear()
                    }
                )
            } else {
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
                        if (currentSection != StorageViewSection.DASHBOARD || selectedFolder != null) {
                            IconButton(
                                onClick = {
                                    if (selectedFolder != null) selectedFolder = null
                                    else currentSection = StorageViewSection.DASHBOARD
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                        Text(
                            text = when {
                                selectedFolder != null -> selectedFolder!!.folderName
                                currentSection == StorageViewSection.DASHBOARD -> "Files"
                                currentSection == StorageViewSection.STARRED -> "Starred"
                                currentSection == StorageViewSection.SAFE_FOLDER -> "Safe folder"
                                currentSection == StorageViewSection.TRASH -> "Trash"
                                else -> selectedCategoryTitle
                            },
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (currentSection != StorageViewSection.DASHBOARD) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "View Toggle",
                                tint = ElectricBlue,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { isGridView = !isGridView }
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 📱 ২. মূল স্ক্রিন কন্টেন্ট
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        refreshDashboardAndData()
                        delay(400)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when (currentSection) {
                    // 📊 ১. Google Files স্টাইল ড্যাশবোর্ড (স্ক্রিনশট ৪)
                    StorageViewSection.DASHBOARD -> {
                        DashboardView(
                            summary = summaryData,
                            onCategoryClick = { index, title ->
                                selectedCategoryIndex = index
                                selectedCategoryTitle = title
                                currentSection = StorageViewSection.CATEGORY_CONTENT
                            },
                            onStarredClick = { currentSection = StorageViewSection.STARRED },
                            onSafeFolderClick = { showSafePinDialog = true },
                            onTrashClick = { currentSection = StorageViewSection.TRASH }
                        )
                    }

                    // 📁 ২. ক্যাটাগরি, ফোল্ডার ও ফাইল তালিকা (স্ক্রিনশট ২ ও ৩)
                    else -> {
                        val displayItems = currentItems.filter {
                            selectedFolder == null || it.bucketId == selectedFolder?.bucketId
                        }

                        if (displayItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No files found in this section", color = Color(0xFF6B7280), fontSize = 14.sp)
                            }
                        } else if (isGridView) {
                            // 🔲 ৩-কলাম গ্রিড ভিউ (স্ক্রিনশট ২)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayItems, key = { it.id }) { item ->
                                    val isSelected = item.id in selectedItemIds
                                    Screenshot2GridItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                if (isSelected) selectedItemIds.remove(item.id) else selectedItemIds.add(item.id)
                                            } else {
                                                handleFileClick(item, context, onVideoClick) { img -> viewingImageItem = img }
                                            }
                                        },
                                        onLongClick = {
                                            isMultiSelectMode = true
                                            selectedItemIds.add(item.id)
                                        }
                                    )
                                }
                            }
                        } else {
                            // 📄 টেবিল / লিস্ট ভিউ সাথে ৩-ডট মেনু (স্ক্রিনশট ৩)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayItems, key = { it.id }) { item ->
                                    val isSelected = item.id in selectedItemIds
                                    Screenshot3ListItem(
                                        item = item,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                if (isSelected) selectedItemIds.remove(item.id) else selectedItemIds.add(item.id)
                                            } else {
                                                handleFileClick(item, context, onVideoClick) { img -> viewingImageItem = img }
                                            }
                                        },
                                        onLongClick = {
                                            isMultiSelectMode = true
                                            selectedItemIds.add(item.id)
                                        },
                                        onMenuClick = { selectedFileInfoItem = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // ℹ️ ৩-ডট ফাইল অপশন মেনু (BottomSheet Dialog)
        // =========================================================================
        selectedFileInfoItem?.let { item ->
            FileActionMenuSheet(
                item = item,
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
                    Toast.makeText(context, if (item.isStarred) "Removed from Starred" else "Added to Starred", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    refreshDashboardAndData()
                },
                onMoveToSafe = {
                    LocalMediaScanner.moveToSafeFolder(context, item.path)
                    Toast.makeText(context, "Moved to Safe folder", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    refreshDashboardAndData()
                },
                onMoveToTrash = {
                    LocalMediaScanner.moveToTrash(context, item.path)
                    Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    refreshDashboardAndData()
                },
                onPermanentDelete = {
                    LocalMediaScanner.deletePermanently(context, item.path)
                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    refreshDashboardAndData()
                },
                onRestore = {
                    LocalMediaScanner.restoreFromTrash(context, item.path)
                    Toast.makeText(context, "Restored to gallery", Toast.LENGTH_SHORT).show()
                    selectedFileInfoItem = null
                    refreshDashboardAndData()
                }
            )
        }

        // ✏️ ফাইল রিনেম ডায়ালগ
        renamingItem?.let { item ->
            RenameFileDialog(
                currentName = item.displayName.substringBeforeLast("."),
                onDismiss = { renamingItem = null },
                onSave = { newName ->
                    val success = LocalMediaScanner.renameFile(context, item.path, newName)
                    Toast.makeText(context, if (success) "Renamed successfully" else "Rename failed", Toast.LENGTH_SHORT).show()
                    renamingItem = null
                    refreshDashboardAndData()
                }
            )
        }

        // 🖼️ ফুলস্ক্রিন ইমেজ প্রিভিউ
        viewingImageItem?.let { imageItem ->
            Dialog(onDismissRequest = { viewingImageItem = null }) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable { viewingImageItem = null },
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

        // 🔒 সেফ ফোল্ডার PIN ডায়ালগ
        if (showSafePinDialog) {
            SafeFolderPinDialog(
                savedPin = LocalMediaScanner.getSafeFolderPin(context),
                onDismiss = { showSafePinDialog = false },
                onSuccess = {
                    showSafePinDialog = false
                    currentSection = StorageViewSection.SAFE_FOLDER
                }
            )
        }
    }
}

// -------------------------------------------------------------
// 📊 Google Files স্টাইল ড্যাশবোর্ড (স্ক্রিনশট ৪)
// -------------------------------------------------------------
@Composable
private fun DashboardView(
    summary: StorageCategorySummary,
    onCategoryClick: (Int, String) -> Unit,
    onStarredClick: () -> Unit,
    onSafeFolderClick: () -> Unit,
    onTrashClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Categories", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)

        // ২ কলাম ক্যাটাগরি গ্রিড (Downloads, Images, Videos, Audio, Documents, Apps)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryDashboardCard(
                    title = "Downloads",
                    sizeText = summary.downloadsSizeText,
                    icon = Icons.Outlined.FileDownload,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(4, "Downloads") }
                )
                CategoryDashboardCard(
                    title = "Images",
                    sizeText = summary.imagesSizeText,
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(1, "Images") }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryDashboardCard(
                    title = "Videos",
                    sizeText = summary.videosSizeText,
                    icon = Icons.Outlined.Movie,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(0, "Videos") }
                )
                CategoryDashboardCard(
                    title = "Audio",
                    sizeText = summary.audioSizeText,
                    icon = Icons.Outlined.MusicNote,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(2, "Audio") }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryDashboardCard(
                    title = "Documents",
                    sizeText = summary.documentsSizeText,
                    icon = Icons.Outlined.Description,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(3, "Documents") }
                )
                CategoryDashboardCard(
                    title = "Apps",
                    sizeText = summary.appsSizeText,
                    icon = Icons.Outlined.Apps,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(5, "Apps") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Collections Section (Starred, Safe folder, Trash)
        Text("Collections", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CollectionDashboardCard(
                title = "Starred",
                icon = Icons.Outlined.Star,
                modifier = Modifier.weight(1f),
                onClick = onStarredClick
            )
            CollectionDashboardCard(
                title = "Safe folder",
                icon = Icons.Outlined.Lock,
                modifier = Modifier.weight(1f),
                onClick = onSafeFolderClick
            )
        }

        CollectionDashboardCard(
            title = "Trash / Recycle Bin",
            icon = Icons.Outlined.DeleteOutline,
            modifier = Modifier.fillMaxWidth(),
            onClick = onTrashClick
        )
    }
}

@Composable
private fun CategoryDashboardCard(
    title: String,
    sizeText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, CardBorderDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(sizeText, color = Color(0xFF8E95A5), fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun CollectionDashboardCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = BorderStroke(1.dp, CardBorderDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// 🔲 ৩-কলাম গ্রিড আইটেম (স্ক্রিনশট ২)
// -------------------------------------------------------------
@Composable
private fun Screenshot2GridItem(
    item: LocalVideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isAudio = item.mimeType?.startsWith("audio") == true

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        border = if (isSelected) BorderStroke(2.dp, ElectricBlue) else BorderStroke(0.6.dp, CardBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

            // মাঝের প্লে আইকন
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

            // উপরে ডানপাশে সাইজ ট্যাগ (e.g. 342 MB)
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

            // নিচে টাইটেল স্ট্রিপ
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
// 📄 টেবিল / লিস্ট ভিউ সাথে ৩-ডট মেনু (স্ক্রিনশট ৩)
// -------------------------------------------------------------
@Composable
private fun Screenshot3ListItem(
    item: LocalVideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val isAudio = item.mimeType?.startsWith("audio") == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .size(60.dp, 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardSurfaceDark),
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

        // Title + Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${item.formattedSize} • ${item.formattedDate}", color = Color(0xFF8E95A5), fontSize = 11.5.sp)
        }

        // ⋮ 3-Dots Menu Button
        IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF8E95A5), modifier = Modifier.size(20.dp))
        }
    }
}

// -------------------------------------------------------------
// ☑️ মাল্টি-সিলেক্ট টপ বার
// -------------------------------------------------------------
@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onShareBatch: () -> Unit,
    onDeleteBatch: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElectricBlue)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text("$selectedCount selected", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onSelectAll, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
            }
            IconButton(onClick = onShareBatch, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(onClick = onDeleteBatch, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    }
}

// -------------------------------------------------------------
// ℹ️ ৩-ডট ফাইল অপশন বটম শীট (Info, Rename, Star, Safe, Trash)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionMenuSheet(
    item: LocalVideoItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onToggleStar: () -> Unit,
    onMoveToSafe: () -> Unit,
    onMoveToTrash: () -> Unit,
    onPermanentDelete: () -> Unit,
    onRestore: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.formattedSize} • ${item.path}", color = Color(0xFF8E95A5), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            HorizontalDivider(color = CardBorderDark, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 6.dp))

            if (item.isTrashed) {
                SheetActionRow(Icons.Default.Restore, "Restore to gallery", ElectricBlue, onRestore)
                SheetActionRow(Icons.Default.DeleteForever, "Delete permanently", Color(0xFFFF5252), onPermanentDelete)
            } else {
                SheetActionRow(Icons.Default.Share, "Share", Color.White, onShare)
                SheetActionRow(Icons.Default.Edit, "Rename", Color.White, onRename)
                SheetActionRow(if (item.isStarred) Icons.Default.Star else Icons.Outlined.Star, if (item.isStarred) "Remove from Starred" else "Add to Starred", if (item.isStarred) Color(0xFFFFB300) else Color.White, onToggleStar)
                SheetActionRow(Icons.Default.Lock, "Move to Safe folder", Color.White, onMoveToSafe)
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

// -------------------------------------------------------------
// ✏️ রিনেম ও 🔒 সেফ ফোল্ডার ডায়ালগসমূহ
// -------------------------------------------------------------
@Composable
private fun RenameFileDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rename File", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, unfocusedBorderColor = CardBorderDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
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
private fun SafeFolderPinDialog(savedPin: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                Text("Enter Safe Folder PIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(4); error = false },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, unfocusedBorderColor = CardBorderDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                if (error) Text("Incorrect PIN! (Default: 0000)", color = Color(0xFFFF5252), fontSize = 11.5.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8E95A5)) }
                    Button(
                        onClick = {
                            if (pin == savedPin) onSuccess() else error = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) { Text("Unlock", color = Color.White) }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// শেয়ার ও ফাইল হ্যান্ডলার
// -------------------------------------------------------------
private fun handleFileClick(item: LocalVideoItem, context: Context, onVideoClick: (LocalVideoItem) -> Unit, onImageView: (LocalVideoItem) -> Unit) {
    when {
        item.mimeType?.startsWith("video") == true -> onVideoClick(item)
        item.mimeType?.startsWith("audio") == true -> onVideoClick(item)
        item.mimeType?.startsWith("image") == true -> onImageView(item)
        else -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.contentUri, item.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
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

private fun shareMultipleFiles(context: Context, items: List<LocalVideoItem>) {
    try {
        val uris = ArrayList(items.map { it.contentUri })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${items.size} files"))
    } catch (_: Exception) {}
}
