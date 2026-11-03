@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.VipCrown3DIcon

// 🎨 টেলিগ্রাম ডার্ক কালার প্যালেট
private val TelegramBg = Color(0xFF0E1621)
private val TelegramCardBg = Color(0xFF17212B)
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramMuted = Color(0xFF7E8D9F)
private val TelegramBlockYellow = Color(0xFFE5A93C)

@Composable
fun PeerProfileScreen(
    peerId: String,
    peerName: String,
    peerAvatar: String?,
    isPeerOnline: Boolean = true,
    isPeerVip: Boolean = false,
    peerAccountId: String? = null, // 👈 ৮ ডিজিটের অ্যাকাউন্ট আইডি
    peerBio: String? = null,
    onBackClick: () -> Unit,
    onOpenMessageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatPrefs = remember { context.getSharedPreferences("private_chat_user_prefs", Context.MODE_PRIVATE) }

    var isMuted by remember { mutableStateOf(chatPrefs.getBoolean("mute_$peerId", false)) }
    var isBlocked by remember { mutableStateOf(chatPrefs.getBoolean("block_$peerId", false)) }

    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showFullAvatarPreview by remember { mutableStateOf(false) }

    // 🎯 ৮-ডিজিট অ্যাকাউন্ট আইডি হিসাব / জেনারেশন (যাতে কখনই @id_175 না দেখায়)
    val displayAccountId = remember(peerAccountId, peerId) {
        if (!peerAccountId.isNullOrBlank() && peerAccountId.length == 8 && peerAccountId.all { it.isDigit() }) {
            peerAccountId
        } else {
            val clean = peerId.trim()
            if (clean.length == 8 && clean.all { it.isDigit() }) {
                clean
            } else {
                val numericPart = clean.filter { it.isDigit() }.toLongOrNull()
                    ?: (clean.hashCode().toLong().let { if (it < 0) -it else it } % 999999L)
                "${77000000L + (numericPart % 999999L)}"
            }
        }
    }

    val resolvedAvatar = remember(peerAvatar, peerName) {
        peerAvatar?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?name=${peerName}&background=1E293B&color=FACC15&bold=true"
    }

    val displayBio = remember(peerBio) {
        peerBio?.takeIf { it.isNotBlank() } ?: "Drama lover & PlayDramaFlix community member ✨"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TelegramBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // =========================================================================
            // 🔝 ১. টপ ব্যাক বাটন ও শেয়ার বাটন
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "PlayDramaFlix User: $peerName\nAccount ID: $displayAccountId"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Profile",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =========================================================================
            // 👤 ২. সেন্ট্রাল প্রোফাইল পিকচার ও নাম
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // গোল অবতার
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2834))
                        .clickable { showFullAvatarPreview = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = peerName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // নাম ও ভিআইপি ক্রাউন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = peerName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isPeerVip) {
                        VipCrown3DIcon(modifier = Modifier.size(18.dp, 14.dp))
                    }
                }

                // অনলাইন স্ট্যাটাস
                Text(
                    text = if (isPeerOnline) "online" else "last seen recently",
                    color = if (isPeerOnline) TelegramBlue else TelegramMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // 🔘 ৩. অ্যাকশন বাটন রো (Message, Mute, Block)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ১. Message বাটন
                TelegramActionButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "Message",
                    containerColor = TelegramCardBg,
                    iconTint = Color.White,
                    onClick = onOpenMessageClick,
                    modifier = Modifier.weight(1f)
                )

                // ২. Mute বাটন
                TelegramActionButton(
                    icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Outlined.Notifications,
                    label = if (isMuted) "Unmute" else "Mute",
                    containerColor = TelegramCardBg,
                    iconTint = if (isMuted) Color(0xFFFFB300) else Color.White,
                    onClick = {
                        val newMute = !isMuted
                        isMuted = newMute
                        chatPrefs.edit().putBoolean("mute_$peerId", newMute).apply()
                        Toast.makeText(
                            context,
                            if (newMute) "🔕 Notifications muted" else "🔔 Notifications unmuted",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                )

                // ৩. Block বাটন
                TelegramActionButton(
                    icon = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                    label = if (isBlocked) "Unblock" else "block",
                    containerColor = if (isBlocked) Color(0xFF26181A) else TelegramBlockYellow,
                    iconTint = if (isBlocked) Color(0xFFFF5252) else Color.Black,
                    labelColor = if (isBlocked) Color(0xFFFF5252) else Color.Black,
                    onClick = {
                        if (isBlocked) {
                            isBlocked = false
                            chatPrefs.edit().putBoolean("block_$peerId", false).apply()
                            Toast.makeText(context, "✓ User unblocked", Toast.LENGTH_SHORT).show()
                        } else {
                            showBlockConfirmDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 📋 ৪. ইনফো কার্ড (Bio / ৮-ডিজিট অ্যাকাউন্ট আইডি / Add to contacts)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ক) বায়ো ও ৮-ডিজিট আইডি কার্ড
                Surface(
                    color = TelegramCardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // বায়ো
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = displayBio,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                lineHeight = 19.sp
                            )
                            Text(
                                text = "Bio",
                                color = TelegramMuted,
                                fontSize = 11.sp
                            )
                        }

                        HorizontalDivider(color = Color(0xFF222B38), thickness = 0.6.dp)

                        // 🎯 ৮-ডিজিটের ইউনিক অ্যাকাউন্ট আইডি ও ওয়ান-ট্যাপ কপি
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Account ID", displayAccountId))
                                    Toast.makeText(context, "Account ID ($displayAccountId) copied!", Toast.LENGTH_SHORT).show()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "@$displayAccountId",
                                    color = TelegramBlue,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Account ID (8-Digit Contact Number)",
                                    color = TelegramMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // খ) "Add to contacts" কার্ড
                Surface(
                    color = TelegramCardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast.makeText(context, "Contact saved to favorites!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Add to contacts",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // =========================================================================
        // 🚫 ৫. ব্লক কনফার্মেশন অ্যালার্ট ডায়ালগ
        // =========================================================================
        if (showBlockConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBlockConfirmDialog = false },
                containerColor = Color(0xFF1E2834),
                title = { Text("Block $peerName?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Text(
                        "Blocked users will not be able to send you messages or see your online status.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isBlocked = true
                            chatPrefs.edit().putBoolean("block_$peerId", true).apply()
                            showBlockConfirmDialog = false
                            Toast.makeText(context, "🚫 User blocked", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Block User", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockConfirmDialog = false }) {
                        Text("Cancel", color = TelegramMuted)
                    }
                }
            )
        }

        // =========================================================================
        // 🖼️ ৬. ফুলস্ক্রিন অবতার প্রিভিউ
        // =========================================================================
        if (showFullAvatarPreview) {
            Dialog(
                onDismissRequest = { showFullAvatarPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = peerName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = { showFullAvatarPreview = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(14.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
