package com.example.ui.screens.chat.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// 🎨 ২ নম্বর ছবির হুবহু টেলিগ্রাম কালার প্যালেট
private val TelegramBlueBtn = Color(0xFF2AABEE)          // টেলিগ্রামের উজ্জ্বল গোলাকার ব্লু বাটন
private val TelegramFieldBg = Color(0xFF212D3B)          // স্লিম ইনপুট ফিল্ড ব্যাকগ্রাউন্ড
private val TelegramHintColor = Color(0xFF7E8D9F)        // হালকা সিলভার ওয়াটারমার্ক
private val TelegramIconMuted = Color(0xFF8696A0)        // ইনপুটের ভেতরের আইকন কালার

@Composable
fun TelegramChatInputBar(
    isUserJoined: Boolean,
    isRecordingVoice: Boolean,
    recordDurationSeconds: Long,
    messageText: String,
    currentUserAvatar: String?,
    currentUserName: String,
    selectedImageUris: List<Uri> = emptyList(),
    selectedVideoUri: Uri?,
    isGroupMuted: Boolean,
    isSending: Boolean,
    onJoinGroupClick: () -> Unit,
    onMessageTextChange: (String) -> Unit,
    onToggleMuteClick: () -> Unit,
    onEmojiPackToggle: () -> Unit,
    onAttachClick: () -> Unit,
    onRemoveSingleImage: (Uri) -> Unit,
    onClearSelectedMedia: () -> Unit,
    onStartVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onSendVoiceRecord: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelectedMedia = selectedImageUris.isNotEmpty() || selectedVideoUri != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent) // 👈 কোনো অতিরিক্ত সলিড ব্যাকগ্রাউন্ড থাকবে না
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // মিডিয়া প্রিভিউ
        AnimatedVisibility(visible = hasSelectedMedia) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TelegramFieldBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141A24)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUris.isNotEmpty()) {
                            AsyncImage(
                                model = selectedImageUris.first(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (selectedVideoUri != null) {
                            AsyncVideoThumbnailLoader(
                                videoUri = selectedVideoUri,
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (selectedVideoUri != null) "Video selected" else "${selectedImageUris.size} Photos selected",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add caption & tap send",
                            color = TelegramBlueBtn,
                            fontSize = 10.5.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClearSelectedMedia,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // =====================================================================
        // 🎯 ২ নম্বর ছবির হুবহু আল্ট্রা-স্লিম টেলিগ্রাম ইনপুট বার
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isUserJoined) {
                Button(
                    onClick = onJoinGroupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlueBtn)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("JOIN COMMUNITY GROUP", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isRecordingVoice) {
                // ভয়েস রেকর্ডিং স্লিম বার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp) // 👈 স্লিম উচ্চতা
                        .clip(RoundedCornerShape(20.dp))
                        .background(TelegramFieldBg)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF2A4B)))
                        Text("${recordDurationSeconds}s", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onCancelVoiceRecord,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Cancel", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                        Button(
                            onClick = onSendVoiceRecord,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlueBtn),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            } else {
                // 💬 ২ নম্বর ছবির মতো স্লিম ক্যাপসুল ইনপুট বক্স
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp) // 👈 পারফেক্ট চিকন/স্লিম টেলিগ্রাম সাইজ
                        .clip(RoundedCornerShape(20.dp))
                        .background(TelegramFieldBg)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // বামে: স্টিকার/ইমোজি আইকন
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                        contentDescription = "Stickers & GIFs",
                        tint = TelegramIconMuted,
                        modifier = Modifier
                            .size(21.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onEmojiPackToggle() }
                    )

                    // মাঝখানে: টেক্সট ইনপুট
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageText.isEmpty()) {
                            Text(
                                text = if (hasSelectedMedia) "Add a caption..." else "Message",
                                color = TelegramHintColor,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(TelegramBlueBtn),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ডানে: পিন/অ্যাটাচমেন্ট আইকন
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach File",
                        tint = TelegramIconMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onAttachClick() }
                    )
                }

                // সবার ডানে: টেলিগ্রামের গোল ব্লু অ্যাকশন বাটন (ভয়েস বা সেন্ড)
                Box(
                    modifier = Modifier
                        .size(40.dp) // 👈 স্লিম ৪০dp সাইজ
                        .clip(CircleShape)
                        .background(TelegramBlueBtn)
                        .clickable {
                            if (messageText.isNotBlank() || hasSelectedMedia) {
                                onSendMessage()
                            } else {
                                onStartVoiceRecord()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (messageText.isNotBlank() || hasSelectedMedia) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
