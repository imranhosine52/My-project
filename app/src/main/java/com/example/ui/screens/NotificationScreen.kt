@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.NotificationItemDto
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val UnreadGoldStripe = Color(0xFFFFB300)
private val UnreadRedDot = Color(0xFFFF3D00)
private val NotificationCardBg = Color(0xFF131A26)
private val ActionButtonBg = Color(0xFF1C2638)

@Composable
fun NotificationScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit,
    onDramaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val notificationState by viewModel.notificationUiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Surface(
                color = SurfaceDark,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceVariantDark)
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "Notifications",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (notificationState.unreadCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE53935)
                            ) {
                                Text(
                                    text = "${notificationState.unreadCount} New",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (notificationState.notifications.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllNotifications() },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear All",
                                color = Color(0xFFE53935),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Notification List
            if (notificationState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealAccent, strokeWidth = 2.5.dp)
                }
            } else if (notificationState.notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No notifications yet",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You will be notified when new drama episodes are released!",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notificationState.notifications, key = { it.id }) { item ->
                        val isUnread = item.id !in notificationState.readNotificationIds && !item.isRead

                        // 🔍 Smart Matching: নোটিফিকেশন টাইটেল দিয়ে ডাটাবেজ থেকে আসল ড্রামা পোস্টার বের করা
                        val matchedDrama = homeState.popularDramas.find { drama ->
                            drama.slug.equals(item.targetSlug, ignoreCase = true) ||
                            drama.title.contains(item.title.take(15), ignoreCase = true) ||
                            item.title.contains(drama.title.take(15), ignoreCase = true)
                        } ?: homeState.recentlyAdded.find { drama ->
                            item.title.contains(drama.title.take(12), ignoreCase = true)
                        }

                        val finalPosterUrl = item.effectivePoster
                            ?: matchedDrama?.posterUrl
                            ?: matchedDrama?.bannerUrl
                            ?: "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp"

                        val targetSlug = item.targetSlug.ifBlank { matchedDrama?.slug ?: "" }

                        NotificationCard(
                            item = item,
                            posterUrl = finalPosterUrl,
                            isUnread = isUnread,
                            onClick = {
                                viewModel.markNotificationAsRead(item.id)
                                if (targetSlug.isNotBlank()) {
                                    onDramaClick(targetSlug)
                                }
                            },
                            onDelete = { viewModel.deleteNotification(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItemDto,
    posterUrl: String,
    isUnread: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NotificationCardBg),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnread) UnreadGoldStripe.copy(alpha = 0.6f) else BorderDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(UnreadGoldStripe)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 🖼️ Real Drama Poster Box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Title & Message
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isUnread) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(UnreadRedDot)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = item.message,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.timeAgo,
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }

                // Actions: Play Arrow & Delete Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ActionButtonBg)
                            .clickable { onClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
