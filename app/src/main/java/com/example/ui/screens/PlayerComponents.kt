package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentItemDto
import com.example.data.model.DramaApiComment
import com.example.ui.theme.GoldVip

// -------------------------------------------------------------
// ⚡ চিকন স্কিপ আইকন (-10s / +10s)
// -------------------------------------------------------------
@Composable
fun SleekSkipIconOnline(
    isForward: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val strokeWidth = 1.6.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            if (isForward) {
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = color,
                    startAngle = 240f,
                    sweepAngle = -290f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = "10",
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// -------------------------------------------------------------
// ⚡ আল্ট্রা-স্লিম টাইমলাইন বার
// -------------------------------------------------------------
@Composable
fun SleekOnlineTimeline(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeekStarted: () -> Unit,
    onSeeking: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00E5FF),
    inactiveColor: Color = Color.White.copy(alpha = 0.28f)
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val newTarget = (newProgress * totalDurationMs).toLong()
                    onSeekFinished(newTarget)
                }
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { onSeekStarted() },
                    onDragEnd = {
                        val currentTarget = (progress * totalDurationMs).toLong()
                        onSeekFinished(currentTarget)
                    },
                    onDragCancel = {
                        val currentTarget = (progress * totalDurationMs).toLong()
                        onSeekFinished(currentTarget)
                    },
                    onHorizontalDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newTarget = (newProgress * totalDurationMs).toLong()
                        onSeeking(newTarget)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 2.8.dp.toPx()
            val thumbRadius = 5.2.dp.toPx()
            val trackWidth = size.width

            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            val activeEnd = trackWidth * progress
            if (activeEnd > 0) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(activeEnd.coerceIn(0f, trackWidth), centerY)
            )
        }
    }
}

// -------------------------------------------------------------
// 🎵 ইকুয়ালাইজার বার্স আইকন
// -------------------------------------------------------------
@Composable
fun EqualizerBarsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00D166)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_bars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 3f, targetValue = 13f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 13f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.2.dp).height(h1.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(h2.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.2.dp).height(h3.dp).background(tint, RoundedCornerShape(1.dp)))
    }
}

// -------------------------------------------------------------
// 🔒 কমপ্যাক্ট আনলক এপিসোড ডায়ালগ
// -------------------------------------------------------------
@Composable
fun CompactUnlockEpisodeDialog(
    episodeNumber: Int,
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onUpgradeVip: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131824)),
            border = BorderStroke(1.dp, Color(0xFF222B3D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2D2305))
                            .border(0.8.dp, GoldVip.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "EPISODE $episodeNumber LOCKED",
                            color = GoldVip,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDismiss() }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF292004))
                        .border(1.2.dp, GoldVip, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = GoldVip,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Unlock Episode $episodeNumber",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Watch a sponsor ad to unlock Episode $episodeNumber for 2 full hours, or upgrade to VIP for permanent ad-free streaming.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onWatchAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D166)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        Text("Watch Ad to Unlock (Free)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clickable { onUpgradeVip() },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF181C26),
                    border = BorderStroke(1.dp, GoldVip.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("👑 ", fontSize = 11.5.sp)
                        Text("Upgrade to VIP (Ad-Free All)", color = GoldVip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 💬 কমেন্ট রো আইটেম
// -------------------------------------------------------------
@Composable
fun ModernCommentRowItem(
    comment: DramaApiComment,
    onLike: () -> Unit,
    onOpenReplies: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val name = comment.displayName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenReplies() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                if (!comment.userAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(comment.userAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(name, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(comment.displayDate, color = Color(0xFF64748B), fontSize = 11.5.sp)
                }

                Text(comment.commentText, color = Color(0xFFE2E8F0), fontSize = 13.5.sp, lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onLike() }
                    ) {
                        Icon(
                            imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (comment.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.likesCount > 0) {
                            Text("${comment.likesCount}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onOpenReplies() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Replies",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        if (comment.repliesCount > 0) {
                            Text("${comment.repliesCount}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onShare() }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF161F2E), thickness = 0.5.dp)
    }
}

// -------------------------------------------------------------
// 💬 কমেন্ট রিপ্লাই থ্রেড ভিউ (Fix: onValueChange = onReplyTextChange)
// -------------------------------------------------------------
@Composable
fun CommentRepliesThreadView(
    parentComment: DramaApiComment,
    dramaContent: ContentItemDto?,
    currentUserAvatar: String,
    userInitials: String,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSendReply: () -> Unit,
    onLikeComment: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C14))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!parentComment.userAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = parentComment.userAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(parentComment.displayName.take(2).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Column {
                            Text(parentComment.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(parentComment.displayDate, color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }

                    Text(parentComment.commentText, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)

                    if (dramaContent != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141C2B))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = dramaContent.posterUrl ?: dramaContent.bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(dramaContent.title.split("|", "-").firstOrNull()?.trim() ?: dramaContent.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("📀 ${dramaContent.releaseYear} • Streaming", color = Color(0xFFFFC107), fontSize = 10.5.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable { onLikeComment(parentComment.id) }
                        ) {
                            Icon(if (parentComment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (parentComment.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            Text("${parentComment.likesCount.coerceAtLeast(1)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            Text("${parentComment.repliesList.size}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }

                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${parentComment.displayName}: ${parentComment.commentText}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share comment"))
                                }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${parentComment.repliesList.size} Comments", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (parentComment.repliesList.isEmpty()) {
                item {
                    Text("No replies yet. Be the first to reply!", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            } else {
                items(
                    count = parentComment.repliesList.size,
                    key = { index -> parentComment.repliesList[index].id }
                ) { index ->
                    val reply = parentComment.repliesList[index]

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8E24AA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(reply.displayName.take(2).uppercase(), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(reply.displayName, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Text(reply.displayDate, color = Color(0xFF64748B), fontSize = 10.5.sp)
                                }
                                Text(reply.commentText, color = Color(0xFFE2E8F0), fontSize = 12.5.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { onLikeComment(reply.id) }
                            ) {
                                Icon(
                                    imageVector = if (reply.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (reply.isLiked) Color(0xFFFF4B72) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (reply.likesCount > 0) {
                                    Text("${reply.likesCount}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Reply",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onReplyTextChange("@${reply.displayName} ") }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${reply.displayName}: ${reply.commentText}")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share reply"))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (reply.sharesCount > 0) {
                                    Text("${reply.sharesCount}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = Color(0xFF080C14),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161F30)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUserAvatar.isNotBlank()) {
                        AsyncImage(model = currentUserAvatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(userInitials, color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0xFF131926))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text("Add a reply...", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = onReplyTextChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color(0xFFFFC107)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSendReply() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    onClick = onSendReply,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
