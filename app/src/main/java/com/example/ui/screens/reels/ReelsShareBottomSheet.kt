@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserReelDto

private val DarkSheetBg = Color(0xFF16181F)
private val TextMuted = Color(0xFF8692A6)

@Composable
fun ReelsShareBottomSheet(
    reel: UserReelDto,
    isLoggedIn: Boolean,
    isCreatorPageUser: Boolean, // 👈 ক্রিয়েটর পেজ থাকলে Repost অপশন দেখাবে
    onDismiss: () -> Unit,
    onRepostClick: () -> Unit,
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
                .padding(vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ড্র্যাগ হ্যান্ডেল
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF333C4D),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            // হেডার রো
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Reel to",
                    color = Color.White,
                    fontSize = 16.5.sp,
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

            HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)

            // =========================================================================
            // 🌐 সোশ্যাল শেয়ার অ্যাকশন রো (সরাসরি অ্যাপে শেয়ার ও লিংক কপি)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🟡 ১. Repost বাটন (ক্রিয়েটর পেজ থাকলে দেখাবে)
                if (isCreatorPageUser) {
                    ShareActionCircularItem(
                        label = "Repost",
                        bgColor = Color(0xFFFFB300),
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
                    bgColor = Color(0xFF007AFF),
                    icon = Icons.Default.Link,
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Reel Link", reelShareUrl))
                        Toast.makeText(context, "✓ Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                // 🟢 ৩. WhatsApp
                ShareActionCircularItem(
                    label = "WhatsApp",
                    bgColor = Color(0xFF25D366),
                    icon = Icons.Default.Call,
                    onClick = {
                        shareToSpecificApp(context, "com.whatsapp", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🔵 ৪. Facebook
                ShareActionCircularItem(
                    label = "Facebook",
                    bgColor = Color(0xFF1877F2),
                    icon = Icons.Default.ThumbUp,
                    onClick = {
                        shareToSpecificApp(context, "com.facebook.katana", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🟣 ৫. Instagram
                ShareActionCircularItem(
                    label = "Instagram",
                    bgColor = Color(0xFFE1306C),
                    icon = Icons.Default.CameraAlt,
                    onClick = {
                        shareToSpecificApp(context, "com.instagram.android", reelShareUrl)
                        onDismiss()
                    }
                )

                // 🔷 ৬. Messenger
                ShareActionCircularItem(
                    label = "Messenger",
                    bgColor = Color(0xFF0084FF),
                    icon = Icons.Default.ChatBubble,
                    onClick = {
                        shareToSpecificApp(context, "com.facebook.orca", reelShareUrl)
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

                // 📤 ১০. More / System Share
                ShareActionCircularItem(
                    label = "More",
                    bgColor = Color(0xFF334155),
                    icon = Icons.Default.Share,
                    onClick = {
                        val genericIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch this reel on PlayDramaFlix:\n$reelShareUrl")
                        }
                        context.startActivity(Intent.createChooser(genericIntent, "Share Reel via"))
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
            .width(62.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
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
