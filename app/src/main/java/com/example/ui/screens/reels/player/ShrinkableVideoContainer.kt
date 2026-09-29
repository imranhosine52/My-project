package com.example.ui.screens.reels.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 🎚️ কমেন্ট শিটের ৩টি প্রসারণ স্তর
 */
enum class CommentSheetExpansion {
    COLLAPSED, // ০% কমেন্ট, ১০০% ফুলস্ক্রিন ভিডিও
    HALF,      // ৬২% কমেন্ট, ৩৮% সংকুচিত ভিডিও (১ নম্বর ছবি)
    EXPANDED   // ১০০% ফুলস্ক্রিন কমেন্ট, ০% ভিডিও
}

/**
 * 🎬 অ্যাডভান্সড ভিডিও শ্রিন্ক ও ড্র্যাগেবল ফুলস্ক্রিন কমেন্ট ইঞ্জিন
 */
@Composable
fun ShrinkableVideoContainer(
    isCommentsOpen: Boolean,
    onCloseComments: () -> Unit,
    videoContent: @Composable BoxScope.(isShrunk: Boolean) -> Unit,
    commentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // কমেন্ট শিটের বর্তমান প্রসারণ স্টেট
    var sheetExpansion by remember { mutableStateOf(CommentSheetExpansion.COLLAPSED) }

    // কমেন্ট ওপেন হলে ডিফল্টভাবে HALF স্টেটে যাবে, বন্ধ হলে COLLAPSED হবে
    LaunchedEffect(isCommentsOpen) {
        sheetExpansion = if (isCommentsOpen) {
            CommentSheetExpansion.HALF
        } else {
            CommentSheetExpansion.COLLAPSED
        }
    }

    // ব্যাক বাটন লজিক: ফুলস্ক্রিন থাকলে প্রথমে HALF হবে, আর HALF থাকলে বন্ধ হবে
    BackHandler(enabled = isCommentsOpen) {
        if (sheetExpansion == CommentSheetExpansion.EXPANDED) {
            sheetExpansion = CommentSheetExpansion.HALF
        } else {
            onCloseComments()
        }
    }

    // ১. ভিডিওর উচ্চতা রেশিও অ্যানিমেশন
    val targetVideoRatio = when (sheetExpansion) {
        CommentSheetExpansion.COLLAPSED -> 1.0f
        CommentSheetExpansion.HALF -> 0.38f
        CommentSheetExpansion.EXPANDED -> 0.0f // ফুলস্ক্রিন কমেন্টে ভিডিও সম্পূর্ণ হাইড
    }

    val videoHeightRatio by animateFloatAsState(
        targetValue = targetVideoRatio,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "VideoHeightRatio"
    )

    // ২. ভিডিওর কর্নার রেডিয়াস অ্যানিমেশন (HALF স্টেটে রাউন্ডেড হবে)
    val cornerRadius by animateDpAsState(
        targetValue = if (sheetExpansion == CommentSheetExpansion.HALF) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "VideoCornerRadius"
    )

    val horizontalMargin by animateDpAsState(
        targetValue = if (sheetExpansion == CommentSheetExpansion.HALF) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "VideoHorizontalMargin"
    )

    val topMargin by animateDpAsState(
        targetValue = if (sheetExpansion == CommentSheetExpansion.HALF) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "VideoTopMargin"
    )

    // ৩. ড্র্যাগ ট্র্যাকিং ভেরিয়েবল
    var cumulativeDragY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // 📺 ওপরে ভিডিও প্লেয়ার সারফেস (যখন videoHeightRatio > 0)
        // =========================================================================
        if (videoHeightRatio > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(videoHeightRatio)
                    .statusBarsPadding()
                    .padding(top = topMargin, start = horizontalMargin, end = horizontalMargin)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Black)
            ) {
                // 🎯 isShrunk = true পাস করা হচ্ছে যেন প্লেয়ার সব ওভারলে আইকন হাইড করে দেয়
                videoContent(sheetExpansion != CommentSheetExpansion.COLLAPSED)
            }
        }

        // =========================================================================
        // 💬 নিচে ইনস্টাগ্রাম স্টাইল ড্র্যাগেবল কমেন্ট সেকশন
        // =========================================================================
        AnimatedVisibility(
            visible = isCommentsOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C0F15))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // =============================================================
                    // 🤏 হ্যান্ডেল বার হেডার (ওপরে/নিচে ড্র্যাগ করার জন্য টাচ জোন)
                    // =============================================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color(0xFF0C0F15))
                            .pointerInput(sheetExpansion) {
                                detectVerticalDragGestures(
                                    onDragStart = { cumulativeDragY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        cumulativeDragY += dragAmount
                                    },
                                    onDragEnd = {
                                        // ⬆️ ওপরের দিকে টান দিলে ফুলস্ক্রিন হবে
                                        if (cumulativeDragY < -50f) {
                                            sheetExpansion = CommentSheetExpansion.EXPANDED
                                        }
                                        // ⬇️ নিচের দিকে টান দিলে হাফ বা মিনিমাইজ হবে
                                        else if (cumulativeDragY > 50f) {
                                            if (sheetExpansion == CommentSheetExpansion.EXPANDED) {
                                                sheetExpansion = CommentSheetExpansion.HALF
                                            } else if (sheetExpansion == CommentSheetExpansion.HALF) {
                                                onCloseComments()
                                            }
                                        }
                                        cumulativeDragY = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // মাঝের ছোট ড্যাশ বার (১ নম্বর ছবি)
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333C4D))
                        )

                        // ১ নম্বর স্ক্রিনশটের ডান কোণায় থাকা ডাউন অ্যারো [ ⌄ ]
                        IconButton(
                            onClick = {
                                if (sheetExpansion == CommentSheetExpansion.EXPANDED) {
                                    sheetExpansion = CommentSheetExpansion.HALF
                                } else {
                                    onCloseComments()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize comments",
                                tint = Color(0xFF8692A6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // মূল কমেন্ট কনটেন্ট (লিস্ট, কুইক ইমোজি ও ইনপুট বার)
                    Box(modifier = Modifier.weight(1f)) {
                        commentsContent()
                    }
                }
            }
        }
    }
}
