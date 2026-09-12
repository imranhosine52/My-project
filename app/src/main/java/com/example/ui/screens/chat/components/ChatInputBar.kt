package com.example.ui.screens.chat.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

private val TelegramInputPill = Color(0xFF1E2834)
private val TelegramBlueAction = Color(0xFF2AABEE)

@Composable
fun TelegramChatInputBar(
    isUserJoined: Boolean,
    isRecordingVoice: Boolean,
    recordDurationSeconds: Long,
    messageText: String,
    currentUserAvatar: String?,
    currentUserName: String,
    selectedImageUri: Uri?,
    selectedVideoUri: Uri?,
    isGroupMuted: Boolean,
    isSending: Boolean,
    onJoinGroupClick: () -> Unit,
    onMessageTextChange: (String) -> Unit,
    onToggleMuteClick: () -> Unit,
    onEmojiPackToggle: () -> Unit,
    onAttachClick: () -> Unit,
    onClearSelectedMedia: () -> Unit,
    onStartVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onSendVoiceRecord: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelectedMedia = selectedImageUri != null || selectedVideoUri != null

    // 🌟 ব্যাকগ্রাউন্ড ট্রান্সপারেন্ট রাখা হয়েছে যাতে ট্রু ওভারলে হিসেবে চ্যাটের ওপর ভাসে
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        AnimatedVisibility(visible = hasSelectedMedia) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2834))
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
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141A24)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (selectedVideoUri != null) "Video selected (Max 50MB)" else "Photo selected",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add caption below & tap Send",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClearSelectedMedia,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 🌟 টেলিগ্রামের মতো স্লিক ফ্লোটিং পিল ও গোল অ্যাকশন বাটন
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            if (!isUserJoined) {
                Button(
                    onClick = onJoinGroupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlueAction)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(TelegramInputPill)
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
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlueAction),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ২ নম্বর ছবির মতো ক্যাপসুল ইনপুট বক্স
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TelegramInputPill)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ইমোজি বাটন (বামে)
                        Icon(
                            imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                            contentDescription = "Emoji Pack",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onEmojiPackToggle() }
                        )

                        // মেসেজ টেক্সট ইনপুট
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (messageText.isEmpty()) {
                                Text(
                                    text = if (hasSelectedMedia) "Add a caption..." else "Message",
                                    color = Color(0xFF8696A0),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = messageText,
                                onValueChange = onMessageTextChange,
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(Color(0xFF2AABEE)),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // পেপারক্লিপ (ডানে)
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attach File",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier
                                .size(23.dp)
                                .clickable { onAttachClick() }
                        )
                    }

                    // গোল সেন্ড / মাইক বাটন (কীবোর্ড থেকে উপরে পর্যাপ্ত ফাঁকা থাকবে)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(TelegramBlueAction)
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
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (messageText.isNotBlank() || hasSelectedMedia) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)),
        border = BorderStroke(1.dp, Color(0xFF2B3A4A)),
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
