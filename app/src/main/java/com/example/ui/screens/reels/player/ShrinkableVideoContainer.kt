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

// 🎯 ১. স্ক্রিনশটের নীল দাগ অনুযায়ী সর্বোচ্চ উচ্চতা (ভিডিও ওপরের অংশে দেখা যাবে)
private const val RATIO_MAX = 0.86f       
// 🎯 ২. স্বাভাবিক হাফ স্ক্রিন উচ্চতা
private const val RATIO_HALF = 0.58f      
// 🎯 ৩. কমেন্ট বন্ধ থাকা অবস্থা
private const val RATIO_COLLAPSED = 0.00f 

/**
 * 🎬 ইন্টারঅ্যাক্টিভ ভিডিও শ্রিন্ক ও স্মুথ কমেন্ট ড্র্যাগ ইঞ্জিন
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

    // কমেন্ট শিটের উচ্চতার অ্যানিমেটেড ফ্র্যাকশন (০.০ থেকে ০.৮৬)
    val sheetFraction = remember { Animatable(RATIO_COLLAPSED) }

    // 🔙 ব্যাক বাটন হ্যান্ডলার: ফুলস্ক্রিন থাকলে হাফে নামবে, হাফ থাকলে বন্ধ হবে
    BackHandler(enabled = isCommentsOpen) {
        if (sheetFraction.value > RATIO_HALF + 0.05f) {
            coroutineScope.launch {
                sheetFraction.animateTo(
                    targetValue = RATIO_HALF,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        } else {
            coroutineScope.launch {
                sheetFraction.animateTo(
                    targetValue = RATIO_COLLAPSED,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
                onCloseComments()
            }
        }
    }

    // কমেন্ট ওপেন/ক্লোজ স্টেট সিঙ্ক
    LaunchedEffect(isCommentsOpen) {
        if (isCommentsOpen) {
            if (sheetFraction.value < RATIO_HALF) {
                sheetFraction.animateTo(
                    targetValue = RATIO_HALF,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            sheetFraction.animateTo(
                targetValue = RATIO_COLLAPSED,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val totalHeightPx = with(density) { maxHeight.toPx() }

        // 🤏 যেকোনো স্থান থেকে আঙুল দিয়ে টানার লাইভ ড্র্যাগ লজিক
        val draggableState = rememberDraggableState { deltaPx ->
            val deltaFraction = -deltaPx / totalHeightPx
            val newFraction = (sheetFraction.value + deltaFraction).coerceIn(RATIO_COLLAPSED, RATIO_MAX)
            coroutineScope.launch {
                sheetFraction.snapTo(newFraction)
            }
        }

        val currentFraction = sheetFraction.value
        val isVideoShrunk = currentFraction > 0.05f
        val videoHeightFraction = (1.0f - currentFraction).coerceIn(0.0f, 1.0f)

        // ভিডিওর কর্নার রেডিয়াস ও প্যাডিং স্মুথ রূপান্তর
        val cornerRadius = if (isVideoShrunk) (16 * (currentFraction / RATIO_HALF)).coerceAtMost(16f).dp else 0.dp
        val horizontalMargin = if (isVideoShrunk) (8 * (currentFraction / RATIO_HALF)).coerceAtMost(8f).dp else 0.dp
        val topMargin = if (isVideoShrunk) (6 * (currentFraction / RATIO_HALF)).coerceAtMost(6f).dp else 0.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // 📺 ১. ওপরের সংকুচিত ভিডিও প্লেয়ার অংশ (নীল দাগের ওপর পর্যন্ত দৃশ্যমান)
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
            // 💬 ২. ড্র্যাগেবল কমেন্ট বক্স (উপরে টানলে নীল দাগ পর্যন্ত সর্বোচ্চ বড় হবে)
            // =========================================================================
            if (currentFraction > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(currentFraction)
                        .background(Color(0xFF0C0F15))
                ) {
                    // 🤏 হ্যান্ডেল বার হেডার
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
                                            // দ্রুত ওপরে ফ্লিক করলে সর্বোচ্চ নীল দাগ (RATIO_MAX) পর্যন্ত উঠবে
                                            velocity < -500f -> RATIO_MAX
                                            // দ্রুত নিচে টান দিলে বন্ধ হবে
                                            velocity > 500f -> {
                                                if (current > RATIO_HALF + 0.05f) RATIO_HALF else RATIO_COLLAPSED
                                            }
                                            // পজিশন ভিত্তিক স্প্রিং স্ন্যাপ
                                            current > (RATIO_HALF + (RATIO_MAX - RATIO_HALF) / 2f) -> RATIO_MAX
                                            current in 0.28f..(RATIO_HALF + 0.12f) -> RATIO_HALF
                                            else -> RATIO_COLLAPSED
                                        }

                                        sheetFraction.animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
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
                        // মাঝের ছোট ড্র্যাগ ড্যাশ
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333C4D))
                        )

                        // ডানের মিনিমাইজ বাটন [ ⌄ ]
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (sheetFraction.value > RATIO_HALF + 0.05f) {
                                        sheetFraction.animateTo(
                                            targetValue = RATIO_HALF,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    } else {
                                        sheetFraction.animateTo(
                                            targetValue = RATIO_COLLAPSED,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                        )
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

                    // কমেন্ট লিস্ট ও ইনপুট বার
                    Box(modifier = Modifier.weight(1f)) {
                        commentsContent()
                    }
                }
            }
        }
    }
}
