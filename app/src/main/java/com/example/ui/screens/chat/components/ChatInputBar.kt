@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun TelegramChatInputBar(
    isUserJoined: Boolean,
    isRecordingVoice: Boolean,
    recordDurationSeconds: Long,
    messageText: String,
    currentUserAvatar: String?,
    currentUserName: String,
    isGroupMuted: Boolean,
    isSending: Boolean,
    onJoinGroupClick: () -> Unit,
    onMessageTextChange: (String) -> Unit,
    onToggleMuteClick: () -> Unit,
    onEmojiPackToggle: () -> Unit,
    onAttachClick: () -> Unit,
    onStartVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onSendVoiceRecord: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WhatsAppDarkBg,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)
    ) {
        if (!isUserJoined) {
            // গ্রুপে জয়েন না থাকলে বড় জয়েন বাটন
            Button(
                onClick = onJoinGroupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("JOIN COMMUNITY GROUP / জয়েন করুন", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (isRecordingVoice) {
            // লাইভ ভয়েস রেকর্ড বার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Color(0xFF1F2C34))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF2A4B)))
                    Text("Recording: ${recordDurationSeconds}s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancelVoiceRecord) {
                        Text("Cancel", color = Color(0xFFFF5252), fontSize = 13.sp)
                    }
                    Button(
                        onClick = onSendVoiceRecord,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // স্ক্রিনশটের হুবহু টেলিগ্রাম ইনপুট বার
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(WhatsAppBarBg)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ১. বাঁয়ের প্রোফাইল অবতার
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(getTelegramAvatarColor(currentUserName)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentUserAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = currentUserAvatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentUserName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ২. ইমোজি আইকন
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                        contentDescription = "Emoji Pack",
                        tint = Color(0xFF8696A0),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onEmojiPackToggle() }
                    )

                    // ৩. মেসেজ ইনপুট বক্স
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageText.isEmpty()) {
                            Text("Message...", color = Color(0xFF8696A0), fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF00A884)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ৪. নোটিফিকেশন ঘণ্টা
                    Icon(
                        imageVector = if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        contentDescription = "Mute/Unmute",
                        tint = if (isGroupMuted) Color(0xFF8696A0) else Color(0xFFFFB300),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onToggleMuteClick() }
                    )

                    // ৫. পেপারক্লিপ / অ্যাটাচমেন্ট
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach",
                        tint = Color(0xFF8696A0),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onAttachClick() }
                    )
                }

                // ৬. সেন্ড / মাইক বাটন (স্ক্রিনের ভেতর ফিক্সড)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884))
                        .clickable {
                            if (messageText.isNotBlank()) onSendMessage()
                            else onStartVoiceRecord()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (messageText.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Outlined.Mic, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/**
 * 😊 রেডিমেড ইমোজি প্যাক কার্ড
 */
@Composable
fun EmojiPackPopupCard(
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emojiList = listOf(
        "👍", "❤️", "😂", "🔥", "🙏", "😍", "🥰", "👏", "🎉", "😮",
        "😭", "🥺", "😎", "🥳", "✨", "💯", "😴", "🤔", "👀", "💔",
        "💖", "🤝", "✌️", "🤞", "🫶", "🍿", "🎬", "☕", "🌹", "🚀"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
        border = BorderStroke(1.dp, Color(0xFF2A3942)),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ready-made Emoji Pack", color = Color(0xFF8696A0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8696A0), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(emojiList) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
