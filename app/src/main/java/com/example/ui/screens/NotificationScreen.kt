@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.NotificationItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// 🎨 প্রিমিয়াম ব্লু-গ্রিন প্লে বাটন গ্রেডিয়েন্ট
private val BlueGreenPlayBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF007AFF), // Electric Blue
        Color(0xFF00D166)  // Emerald Green
    )
)

// 👑 গোল্ডেন VIP গ্রেডিয়েন্ট
private val VipGoldBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF8C00)  // Dark Orange
    )
)

private val PureBlackBg = Color(0xFF06080E)
private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2536)
private val ActionRed = Color(0xFFFF3B30)

@Composable
fun NotificationScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notificationState by viewModel.notificationUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlackBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // =========================================================================
            // 🔝 ১. এজ-টু-এজ হেডার
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161B28),
                                Color(0xFF0E121B),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF19202E))
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Notifications",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (notificationState.unreadCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ActionRed
                            ) {
                                Text(
                                    text = "${notificationState.unreadCount} New",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

                    // 🗑️ Clear All Button
                    if (notificationState.notifications.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF261418),
                            border = BorderStroke(0.8.dp, ActionRed.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                viewModel.clearAllNotifications()
                                Toast.makeText(context, "All notifications cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All",
                                    tint = ActionRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Clear All",
                                    color = ActionRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 📱 ২. নোটিফিকেশন তালিকা
            // =========================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadNotifications()
                        delay(400)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (notificationState.isLoading && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF), strokeWidth = 2.5.dp)
                    }
                } else if (notificationState.notifications.isEmpty()) {
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
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(DeepCardBg)
                                    .border(1.dp, CardBorderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "No new notifications",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "You're all caught up! You'll be notified as soon as new episodes or movies are released.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 86.dp, start = 14.dp, end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = notificationState.notifications, 
                            key = { it.id }
                        ) { item ->
                            val isUnread = item.id !in notificationState.readNotificationIds && !item.isRead

                            // 🔍 ১. নোটিফিকেশন টাইপ নির্ধারণ (VIP নাকি সাধারণ ড্রামা)
                            val isVipNotification = remember(item.title, item.message) {
                                item.title.contains("VIP", ignoreCase = true) || 
                                item.message.contains("VIP", ignoreCase = true) ||
                                item.title.contains("Premium", ignoreCase = true) ||
                                item.message.contains("Free Trial", ignoreCase = true)
                            }

                            // 🔍 ২. ড্রামা ডাটা ম্যাচিং
                            val matchedDrama = remember(item.id, homeState.popularDramas, homeState.recentlyAdded) {
                                if (isVipNotification && item.targetSlug.isBlank()) null
                                else {
                                    homeState.popularDramas.find { drama ->
                                        drama.slug.equals(item.targetSlug, ignoreCase = true) ||
                                        drama.title.contains(item.title.take(15), ignoreCase = true) ||
                                        item.title.contains(drama.title.take(15), ignoreCase = true)
                                    } ?: homeState.recentlyAdded.find { drama ->
                                        item.title.contains(drama.title.take(12), ignoreCase = true)
                                    }
                                }
                            }

                            // 🔍 ৩. পোস্টার URL সিলেকশন (VIP হলে ড্রামা পোস্টার লোড হবে না)
                            val finalPosterUrl = when {
                                isVipNotification -> null
                                item.effectivePoster != null -> item.effectivePoster
                                matchedDrama != null -> matchedDrama.posterUrl ?: matchedDrama.bannerUrl
                                else -> null
                            }

                            val targetSlug = item.targetSlug.ifBlank { matchedDrama?.slug ?: "" }

                            // ↔️ সোয়াইপ কন্টেইনার
                            SwipeToDismissNotificationWrapper(
                                itemId = item.id,
                                onDismiss = {
                                    viewModel.deleteNotification(item.id)
                                    Toast.makeText(context, "Notification dismissed", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                CinemaPosterNotificationCard(
                                    item = item,
                                    posterUrl = finalPosterUrl,
                                    isVip = isVipNotification,
                                    isDrama = !isVipNotification && (targetSlug.isNotBlank() || finalPosterUrl != null),
                                    isUnread = isUnread,
                                    onClick = {
                                        viewModel.markNotificationAsRead(item.id)
                                        if (targetSlug.isNotBlank()) {
                                            onDramaClick(targetSlug)
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteNotification(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// ↔️ সোয়াইপ করে ডিলিট করার র‍্যাপার (Responsive & Density-Safe)
// -----------------------------------------------------------------------------
@Composable
private fun SwipeToDismissNotificationWrapper(
    itemId: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(itemId) { Animatable(0f) }

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val dismissThresholdPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    ) {
        // পেছনের লাল ব্যাকগ্রাউন্ড ও ডিলিট আইকন
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(ActionRed)
                .padding(horizontal = 20.dp),
            contentAlignment = if (offsetX.value > 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // মূল কার্ড
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(itemId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX.value) > dismissThresholdPx) {
                                coroutineScope.launch {
                                    val target = if (offsetX.value > 0) screenWidthPx * 1.2f else -screenWidthPx * 1.2f
                                    offsetX.animateTo(target, tween(200))
                                    onDismiss()
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

// -----------------------------------------------------------------------------
// 🎬 সিনেমা পোস্টার / VIP নোটিফিকেশন কার্ড
// -----------------------------------------------------------------------------
@Composable
private fun CinemaPosterNotificationCard(
    item: NotificationItemDto,
    posterUrl: String?,
    isVip: Boolean,
    isDrama: Boolean,
    isUnread: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCardBg),
        border = BorderStroke(
            width = if (isUnread) 1.2.dp else 0.8.dp,
            color = when {
                isUnread && isVip -> Color(0xFFFFD700).copy(alpha = 0.8f)
                isUnread -> Color(0xFFFFB300).copy(alpha = 0.7f)
                else -> CardBorderColor
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🖼️ থাম্বনেইল বক্স: ড্রামা হলে পোস্টার, VIP হলে গোল্ডেন ক্রাউন ব্যাজ
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isVip) Color(0xFF231B0E) else Color(0xFF1E2433)),
                contentAlignment = Alignment.Center
            ) {
                if (posterUrl != null && !isVip) {
                    // ড্রামা পোস্টার ইমেজ
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ডার্ক শ্যাডো
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    // সেন্ট্রাল প্লে আইকন
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // 👑 VIP / সিস্টেম নোটিফিকেশন আইকন
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isVip) VipGoldBrush else Brush.linearGradient(listOf(Color(0xFF2C384E), Color(0xFF1E2433)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVip) Icons.Default.WorkspacePremium else Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (isVip) Color.Black else Color(0xFF007AFF),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 📝 টাইটেল, মেসেজ ও অ্যাকশন বাটন
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            color = if (isVip) Color(0xFFFFD700) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isUnread) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isVip) Color(0xFFFFD700) else ActionRed)
                            )
                        }
                    }

                    // ✕ ডিলিট বাটন
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = Color(0xFF7E869E),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = item.message,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // নিচে সময় ও ডাইনামিক বাটন (VIP হলে 'VIP Active', ড্রামা হলে '▶ Play')
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.timeAgo,
                            color = Color(0xFF64748B),
                            fontSize = 10.5.sp
                        )
                    }

                    // 🌟 VIP ব্যাজ অথবা ড্রামার Play বাটন
                    if (isVip) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(VipGoldBrush)
                                .clickable { onClick() }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "VIP Active",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isDrama) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(BlueGreenPlayBrush)
                                .clickable { onClick() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Play",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
