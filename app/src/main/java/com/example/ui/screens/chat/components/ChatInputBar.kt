package com.example.ui.screens.chat.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val TelegramInputPill = Color(0xFF1E2834)
private val WhatsAppGreenAction = Color(0xFF00A884)

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
            .background(Color.Transparent)
    ) {
        // মিডিয়া প্রিভিউ ব্যানার
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (selectedVideoUri != null) "Video selected (Max 50MB)" else "${selectedImageUris.size} Photos selected",
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

        // মূল ইনপুট বার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isUserJoined) {
                Button(
                    onClick = onJoinGroupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenAction)
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
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreenAction),
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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 130.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(TelegramInputPill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 🧸 স্মাইলি আইকন: চাপলে সরাসরি Stickers, GIFs ও Emoji প্যানেল খুলবে
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                        contentDescription = "Stickers & GIFs",
                        tint = Color(0xFF8696A0),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onEmojiPackToggle() }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
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
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF00A884)),
                            singleLine = false,
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach File",
                        tint = Color(0xFF8696A0),
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { onAttachClick() }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WhatsAppGreenAction)
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
