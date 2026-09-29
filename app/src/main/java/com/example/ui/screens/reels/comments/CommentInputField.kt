package com.example.ui.screens.reels.comments

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val InputBorderColor = Color(0xFF262C3A)
private val TextMuted = Color(0xFF8692A6)
private val InstagramBlue = Color(0xFF0095F6)

/**
 * 🔲 ১ নম্বর ছবির হুবহু বটম ইনপুট বার:
 * (ইউজার অ্যাভাটার + ক্যাপসুল টেক্সট ফিল্ড + GIF/Image আইকন + সেন্ড বাটন)
 */
@Composable
fun CommentInputField(
    currentUserAvatar: String?,
    currentUserName: String,
    targetCreatorName: String,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onGifClick: () -> Unit,
    isSubmitting: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        color = Color(0xFF0C0F15),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ১. বামে নিজের ছোট অ্যাভাটার
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2434)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUserAvatar ?: "https://ui-avatars.com/api/?name=${currentUserName}&background=1E2434&color=fff")
                        .crossfade(true)
                        .build(),
                    contentDescription = "My Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // ২. ক্যাপসুল আকৃতির ইনপুট বক্স
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF141722))
                    .border(0.8.dp, InputBorderColor, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Add a comment for $targetCreatorName",
                            color = TextMuted,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(InstagramBlue),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) onSendClick() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                // ৩. ইমেজ / গ্যালারি আইকন (১ নম্বর ছবি)
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Add image",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onPickImageClick() }
                )

                // ৪. GIF আইকন ব্যাজ (১ নম্বর ছবি)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, TextMuted),
                    modifier = Modifier.clickable { onGifClick() }
                ) {
                    Text(
                        text = "GIF",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // ৫. সেন্ড বাটন (টেক্সট থাকলে নীল রঙের অ্যাক্টিভ সেন্ড আইকন)
            AnimatedVisibility(visible = inputText.isNotBlank() || isSubmitting) {
                IconButton(
                    onClick = onSendClick,
                    enabled = !isSubmitting && inputText.isNotBlank(),
                    modifier = Modifier.size(38.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = InstagramBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = InstagramBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
