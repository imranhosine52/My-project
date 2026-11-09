@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens.localplayer.gallery

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocalVideoItem
import com.example.data.model.TrashedVideoMetadata
import com.example.util.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F131D),
        Color(0xFF080A10),
        Color(0xFF000000)
    )
)
private val CardSurface = Color(0xFF121724)
private val CardBorderColor = Color(0xFF1E283C)
private val SafeGreen = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00E5FF)
private val DangerRed = Color(0xFFFF5252)
private val TextMuted = Color(0xFF8E95A5)

@Composable
fun GalleryRecycleBinScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var trashedVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var trashedMetadataMap by remember { mutableStateOf<Map<String, TrashedVideoMetadata>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val selectedItemIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedItemIds.isNotEmpty()

    var showEmptyBinDialog by remember { mutableStateOf(false) }
    var singleItemToDeletePermanently by remember { mutableStateOf<LocalVideoItem?>(null) }

    val pullRefreshState = rememberPullToRefreshState()

    fun loadBinData() {
        coroutineScope.launch {
            val (items, metadata) = withContext(Dispatchers.IO) {
                val list = LocalMediaScanner.getTrashItems(context)
                val metaList = LocalMediaScanner.getRecycleBinMetadataList(context)
                val map = metaList.associateBy { it.originalPath }
                Pair(list, map)
            }
            trashedVideos = items
            trashedMetadataMap = metadata
            isLoading = false
            selectedItemIds.removeAll { id -> items.none { it.id == id } }
        }
    }

    LaunchedEffect(Unit) {
        loadBinData()
    }

    BackHandler {
        if (isSelectionMode) {
            selectedItemIds.clear()
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =============================================================
            // 🔝 ১. হেডার বার (Header Bar)
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
                    IconButton(
                        onClick = {
                            if (isSelectionMode) selectedItemIds.clear() else onBackClick()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedItemIds.size} Selected" else "Recycle Bin",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isSelectionMode) {
                            Text(
                                text = "${trashedVideos.size} items in bin",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        TextButton(
                            onClick = {
                                if (selectedItemIds.size == trashedVideos.size) {
                                    selectedItemIds.clear()
                                } else {
                                    selectedItemIds.clear()
                                    selectedItemIds.addAll(trashedVideos.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedItemIds.size == trashedVideos.size) "Deselect All" else "Select All",
                                color = CyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (trashedVideos.isNotEmpty()) {
                        Button(
                            onClick = { showEmptyBinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f)),
                            border = BorderStroke(0.8.dp, DangerRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Empty Bin",
                                    color = DangerRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // =============================================================
            // ℹ️ ২. ৩০ দিনের রিটেনশন ইনফো ব্যানার
            // =============================================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131A26),
                border = BorderStroke(0.8.dp, Color(0xFF222F45)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Items in the Recycle Bin will be permanently erased after 30 days. You can restore them anytime to their original location.",
                        color = Color(0xFFC0CAD8),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // =============================================================
            // 📋 ৩. ট্র্যাশ ভিডিও তালিকা (Pull-to-refresh সহ)
            // =============================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadBinData()
                        delay(350)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanAccent, strokeWidth = 2.5.dp)
                    }
                } else if (trashedVideos.isEmpty()) {
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
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(CardSurface)
                                    .border(1.dp, CardBorderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "Recycle Bin is Empty",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Deleted videos are kept here safely for 30 days before permanent removal.",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            top = 6.dp,
                            end = 14.dp,
                            bottom = if (isSelectionMode) 90.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashedVideos, key = { it.id }) { item ->
                            val isSelected = selectedItemIds.contains(item.id)
                            val meta = trashedMetadataMap[item.path]

                            TrashedVideoCardItem(
                                item = item,
                                metadata = meta,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedItemIds.remove(item.id)
                                        else selectedItemIds.add(item.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedItemIds.add(item.id)
                                    }
                                },
                                onRestoreClick = {
                                    coroutineScope.launch {
                                        val success = if (meta != null) {
                                            LocalMediaScanner.restoreFromRecycleBinReal(context, meta)
                                        } else {
                                            LocalMediaScanner.restoreFromTrash(context, item.path)
                                            true
                                        }
                                        if (success) {
                                            Toast.makeText(context, "Restored: ${item.displayName}", Toast.LENGTH_SHORT).show()
                                            loadBinData()
                                        }
                                    }
                                },
                                onDeleteForeverClick = {
                                    singleItemToDeletePermanently = item
                                }
                            )
                        }
                    }
                }
            }

            // =============================================================
            // ⚡ ৪. সিলেকশন মোড বটম অ্যাকশন বার (Floating Multi-Select Action Bar)
            // =============================================================
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF151926),
                    border = BorderStroke(1.dp, Color(0xFF243048)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restore Selected (Green Button)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val toRestore = trashedVideos.filter { selectedItemIds.contains(it.id) }
                                    var count = 0
                                    for (item in toRestore) {
                                        val meta = trashedMetadataMap[item.path]
                                        val ok = if (meta != null) LocalMediaScanner.restoreFromRecycleBinReal(context, meta)
                                        else { LocalMediaScanner.restoreFromTrash(context, item.path); true }
                                        if (ok) count++
                                    }
                                    Toast.makeText(context, "Restored $count video(s)", Toast.LENGTH_SHORT).show()
                                    selectedItemIds.clear()
                                    loadBinData()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Restore (${selectedItemIds.size})",
                                    color = Color.Black,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Delete Forever (Red Button)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val toDelete = trashedVideos.filter { selectedItemIds.contains(it.id) }
                                    for (item in toDelete) {
                                        LocalMediaScanner.deletePermanently(context, item.path)
                                    }
                                    Toast.makeText(context, "Deleted ${toDelete.size} video(s) permanently", Toast.LENGTH_SHORT).show()
                                    selectedItemIds.clear()
                                    loadBinData()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Delete Forever",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =============================================================
        // 🗑️ ডায়ালগসমূহ (Empty Bin & Permanent Single Delete Confirmation)
        // =============================================================
        if (showEmptyBinDialog) {
            EmptyRecycleBinDialog(
                trashedItemCount = trashedVideos.size,
                onConfirmEmpty = {
                    coroutineScope.launch {
                        LocalMediaScanner.emptyTrash(context)
                        Toast.makeText(context, "Recycle Bin is now empty", Toast.LENGTH_SHORT).show()
                        showEmptyBinDialog = false
                        loadBinData()
                    }
                },
                onDismiss = { showEmptyBinDialog = false }
            )
        }

        singleItemToDeletePermanently?.let { item ->
            AlertDialog(
                onDismissRequest = { singleItemToDeletePermanently = null },
                title = {
                    Text(
                        text = "Delete Permanently?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "\"${item.displayName}\" will be permanently deleted from mobile storage. This cannot be undone.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                },
                containerColor = Color(0xFF141926),
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                LocalMediaScanner.deletePermanently(context, item.path)
                                Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                                singleItemToDeletePermanently = null
                                loadBinData()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { singleItemToDeletePermanently = null }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

/**
 * 🔲 ট্র্যাশ আইটেম কার্ড (Trashed Video Card Item)
 */
@Composable
private fun TrashedVideoCardItem(
    item: LocalVideoItem,
    metadata: TrashedVideoMetadata?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteForeverClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            width = if (isSelected) 1.2.dp else 0.8.dp,
            color = if (isSelected) CyanAccent else CardBorderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // সিলেকশন চেকমার্ক (মোড অন থাকলে)
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CyanAccent else Color.Transparent)
                        .border(1.2.dp, if (isSelected) CyanAccent else TextMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // থাম্বনেইল
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161F2E)),
                contentAlignment = Alignment.Center
            ) {
                AsyncVideoThumbnail(item = item, modifier = Modifier.fillMaxSize())

                if (metadata != null) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = "${metadata.daysRemainingBeforePurge}d left",
                            color = Color(0xFFFFB300),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // বিবরণ (Original path, size, date)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName.ifBlank { item.title },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.formattedSize} • ${item.folderName}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // কুইক রিস্টোর ও ডিলিট বাটন (সিলেকশন মোডে না থাকলে)
            if (!isSelectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Restore Button
                    IconButton(
                        onClick = onRestoreClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SafeGreen.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = SafeGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Forever Button
                    IconButton(
                        onClick = onDeleteForeverClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Forever",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
