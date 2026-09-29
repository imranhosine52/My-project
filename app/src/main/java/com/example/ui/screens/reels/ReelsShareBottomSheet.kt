@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DirectConversationItem
import com.example.data.model.UserReelDto

private val DarkSheetBg = Color(0xFF16181F)
private val TextMuted = Color(0xFF8692A6)
private val OnlineGreen = Color(0xFF00E676)

@Composable
fun ReelsShareBottomSheet(
    reel: UserReelDto,
    conversationsList: List<DirectConversationItem>,
    isLoggedIn: Boolean,
    isCreatorPageUser: Boolean, // 👈 ক্রিয়েটর পেজ আছে কি না তা নির্ধারণ করে
    onDismiss: () -> Unit,
    onRepostClick: () -> Unit,
    onSendToFriendInChat: (friendUserId: String, friendUserName: String) -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reelShareUrl = remember(reel) { reel.shareUrl }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 🔝 ১. হেডার: [ 🔍 Search ] ----- Send to ----- [ ✕ Close ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = "Send to",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // =========================================================================
            // 👥 ২. ফ্রেন্ডস রো (স্ক্রিনশট ২-এর ১ম সারি): ১-ক্লিকে ইনবক্সে পাঠানোর সুবিধা
            // =========================================================================
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ইনবক্সের আসল বন্ধুদের তালিকা
                val directContacts = conversationsList.filter { !it.isSystemNotification }
                items(directContacts, key = { it.conversationId }) { friend ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .width(62.dp)
                            .clickable {
                                if (!isLoggedIn) {
                                    onRequireLogin()
                                } else {
                                    onSendToFriendInChat(friend.otherUserId, friend.otherUserName)
                                    Toast.makeText(context, "Sent to ${friend.otherUserName}!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                    ) {
                        Box(modifier = Modifier.size(54.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF222838)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(friend.otherUserAvatar ?: "https://ui-avatars.com/api/?name=${friend.otherUserName}&background=1E2638&color=fff")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = friend.otherUserName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (friend.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .border(2.dp, DarkSheetBg, CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Text(
                            text = friend.otherUserName,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // শেষ আইটেম: "+ Invite friends to..." (স্ক্রিনশট ২-এর মতো বেগুনি বাটন)
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .width(68.dp)
                            .clickable {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Join PlayDramaFlix to watch trending reels: $reelShareUrl")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Invite Friends"))
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8A2BE2)), // Purple
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Invite friends to ...",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 2,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)

            // =========================================================================
            // 🌐 ৩. সোশ্যাল ও অ্যাকশন রো (স্ক্রিনশট ২-এর ২য় সারি)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🟡 ১. Repost বাটন (🎯 শর্তানুসারে শুধুমাত্র ক্রিয়েটর পেজ থাকলে দেখাবে, পার্সোনাল ইউজারদের দেখাবে না)
                if (isCreatorPageUser) {
                    ShareActionCircularItem(
                        label = "Repost",
                        bgColor = Color(0xFFFFB300), // Yellow
                        icon = Icons.Default.Repeat,
                        onClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                            } else {
                                onRepostClick()
                                onDismiss()
                            }
                        }
                    )
                }

                // 🔵 ২. Copy link বাটন
                ShareActionCircularItem(
                    label = "Copy link",
                    bgColor = Color(0xFF007AFF), // Blue
                    icon = Icons.Default.Link,
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Reel Link", reelShareUrl))
                        Toast.makeText(context, "✓ Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                // 🟣 ৩. Instagram Direct
                ShareActionCircularItem(
                    label = "Instagram Direct",
                    bgColor = Color(0xFFE1306C),
                    icon = Icons.Default.Send,
                    onClick = {
                        shareToSpecificApp(context, "com.instagram.android", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🔷 ৪. Messenger
                ShareActionCircularItem(
                    label = "Messenger",
                    bgColor = Color(0xFF0084FF),
                    icon = Icons.Default.Chat,
                    onClick = {
                        shareToSpecificApp(context, "com.facebook.orca", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🟢 ৫. WhatsApp
                ShareActionCircularItem(
                    label = "WhatsApp",
                    bgColor = Color(0xFF25D366),
                    icon = Icons.Default.Call,
                    onClick = {
                        shareToSpecificApp(context, "com.whatsapp", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🔵 ৬. Facebook
                ShareActionCircularItem(
                    label = "Facebook",
                    bgColor = Color(0xFF1877F2),
                    icon = Icons.Default.ThumbUp,
                    onClick = {
                        shareToSpecificApp(context, "com.facebook.katana", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🟢 ৭. Status
                ShareActionCircularItem(
                    label = "Status",
                    bgColor = Color(0xFF10B981),
                    icon = Icons.Default.AddCircle,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch this reel on PlayDramaFlix: $reelShareUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                        onDismiss()
                    }
                )

                // 💬 ৮. SMS
                ShareActionCircularItem(
                    label = "SMS",
                    bgColor = Color(0xFF0284C7),
                    icon = Icons.Default.Sms,
                    onClick = {
                        try {
                            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("smsto:")
                                putExtra("sms_body", "Watch this reel on PlayDramaFlix: $reelShareUrl")
                            }
                            context.startActivity(smsIntent)
                        } catch (_: Exception) {}
                        onDismiss()
                    }
                )

                // ✉️ ৯. Email
                ShareActionCircularItem(
                    label = "Email",
                    bgColor = Color(0xFF00E5FF),
                    icon = Icons.Default.Email,
                    onClick = {
                        try {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "Check out this reel on PlayDramaFlix")
                                putExtra(Intent.EXTRA_TEXT, reelShareUrl)
                            }
                            context.startActivity(emailIntent)
                        } catch (_: Exception) {}
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareActionCircularItem(
    label: String,
    bgColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun shareToSpecificApp(context: Context, packageName: String, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(packageName)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val genericIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(genericIntent, "Share Reel via"))
    }
}
