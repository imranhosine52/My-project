package com.example.ui.screens.reels.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val RATIO_HALF = 0.62f      // হাফ স্ক্রিন কমেন্ট (ভিডিও ৩৮%)
private const val RATIO_FULL = 1.00f      // ফুলস্ক্রিন কমেন্ট (ভিডিও ০%)
private const val RATIO_COLLAPSED = 0.00f // কমেন্ট বন্ধ (ভিডিও ১০০%)

/**
 * 🎬 ইন্টারঅ্যাক্টিভ ফিঙ্গার-ট্র্যাকিং ভিডিও শ্রিন্ক ইঞ্জিন:
 * (হাতের কন্ট্রোল অনুযায়ী ১:১ স্মুথ ড্র্যাগ, সফট স্প্রিং স্ন্যাপিং এবং মিনিমাইজ হ্যান্ডলিং)
 */
@Composable
fun ShrinkableVideoContainer(
    isCommentsOpen: Boolean,
    onCloseComments: () -> Unit,
    videoContent: @Composable BoxScope.(isShrunk: Boolean) -> Unit,
    commentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // কমেন্ট শিটের উচ্চতা অনুপাত (০.০f থেকে ১.০f)
    val sheetFraction = remember { Animatable(RATIO_COLLAPSED) }

    // ব্যাক বাটন হ্যান্ডলিং: ফুলস্ক্রিন থাকলে প্রথমে হাফে নামবে, হাফ থাকলে বন্ধ হবে
    BackHandler(enabled = isCommentsOpen) {
        if (sheetFraction.value > RATIO_HALF + 0.05f) {
            coroutineScope.launch {
                sheetFraction.animateTo(RATIO_HALF, spring(stiffness = Spring.StiffnessMediumLow))
            }
        } else {
            coroutineScope.launch {
                sheetFraction.animateTo(RATIO_COLLAPSED, spring(stiffness = Spring.StiffnessMediumLow))
                onCloseComments()
            }
        }
    }

    // কমেন্ট ওপেন/ক্লোজ স্টেট সিঙ্ক
    LaunchedEffect(isCommentsOpen) {
        if (isCommentsOpen) {
            sheetFraction.animateTo(
                targetValue = RATIO_HALF,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            sheetFraction.animateTo(
                targetValue = RATIO_COLLAPSED,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val totalHeightPx = with(density) { maxHeight.toPx() }

        // ড্র্যাগেবল স্টেট: সরাসরি আঙুলের মুভমেন্ট অনুসরণ করবে (হাতের কন্ট্রোল)
        val draggableState = rememberDraggableState { deltaPx ->
            val deltaFraction = -deltaPx / totalHeightPx
            val newFraction = (sheetFraction.value + deltaFraction).coerceIn(RATIO_COLLAPSED, RATIO_FULL)
            coroutineScope.launch {
                sheetFraction.snapTo(newFraction)
            }
        }

        val currentFraction = sheetFraction.value
        val isVideoShrunk = currentFraction > 0.05f
        val videoHeightFraction = (1.0f - currentFraction).coerceIn(0.0f, 1.0f)

        // ভিডিওর কর্নার রেডিয়াস ও মার্জিন ডায়নামিক রূপান্তর
        val cornerRadius = if (isVideoShrunk && videoHeightFraction > 0.05f) (18 * (currentFraction / RATIO_HALF)).coerceAtMost(18f).dp else 0.dp
        val horizontalMargin = if (isVideoShrunk && videoHeightFraction > 0.05f) (10 * (currentFraction / RATIO_HALF)).coerceAtMost(10f).dp else 0.dp
        val topMargin = if (isVideoShrunk && videoHeightFraction > 0.05f) (6 * (currentFraction / RATIO_HALF)).coerceAtMost(6f).dp else 0.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // 📺 ওপরে ভিডিও প্লেয়ার ফ্রেম (ভিডিও হাইট ০% হলে স্বয়ংক্রিয়ভাবে ফ্রেম সরে যাবে)
            // =========================================================================
            if (videoHeightFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(videoHeightFraction)
                        .statusBarsPadding()
                        .padding(top = topMargin, start = horizontalMargin, end = horizontalMargin)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(Color.Black)
                ) {
                    videoContent(isVideoShrunk)
                }
            }

            // =========================================================================
            // 💬 নিচে ইনস্টাগ্রাম স্টাইল ড্র্যাগেবল কমেন্ট বক্স (আঙুলের সাথে ওঠানামা করবে)
            // =========================================================================
            if (currentFraction > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(currentFraction)
                        .background(Color(0xFF0C0F15))
                ) {
                    // 🤏 হ্যান্ডেল বার হেডার (আঙুল দিয়ে টেনে ওপরে ফুলস্ক্রিন বা নিচে মিনিমাইজ করার জোন)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color(0xFF0C0F15))
                            .draggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        val current = sheetFraction.value
                                        val target = when {
                                            // দ্রুত ওপরে ফ্লিক করলে বা ৮০% এর বেশি টানলে ফুলস্ক্রিন
                                            velocity < -600f || current > 0.80f -> RATIO_FULL
                                            // দ্রুত নিচে ফ্লিক করলে
                                            velocity > 600f -> {
                                                if (current > RATIO_HALF + 0.1f) RATIO_HALF else RATIO_COLLAPSED
                                            }
                                            // পজিশন ভিত্তিক সফট স্ন্যাপিং
                                            current in 0.30f..0.80f -> RATIO_HALF
                                            current < 0.30f -> RATIO_COLLAPSED
                                            else -> RATIO_HALF
                                        }

                                        sheetFraction.animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )

                                        if (target == RATIO_COLLAPSED) {
                                            onCloseComments()
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // মাঝের ড্র্যাগ ড্যাশ
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333C4D))
                        )

                        // ডান কোণার ড্রপডাউন মিনিমাইজ বাটন [ ⌄ ]
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (sheetFraction.value > RATIO_HALF + 0.05f) {
                                        sheetFraction.animateTo(RATIO_HALF, spring(stiffness = Spring.StiffnessMediumLow))
                                    } else {
                                        sheetFraction.animateTo(RATIO_COLLAPSED, spring(stiffness = Spring.StiffnessMediumLow))
                                        onCloseComments()
                                    }
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

                    // কমেন্ট লিস্ট, কুইক ইমোজি ও ইনপুট বার
                    Box(modifier = Modifier.weight(1f)) {
                        commentsContent()
                    }
                }
            }
        }
    }
}
