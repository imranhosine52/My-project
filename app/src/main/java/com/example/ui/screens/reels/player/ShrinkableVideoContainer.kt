package com.example.ui.screens.reels.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 🎬 ১ নম্বর ছবির হুবহু ভিডিও শ্রিন্ক ইঞ্জিন:
 * কমেন্ট ওপেন হলে ভিডিও প্লেয়ার স্মুথলি রাউন্ডেড কর্নার সহ ওপরের ৩৮%-এ সংকুচিত হয়
 * এবং নিচে ইনস্টাগ্রাম কমেন্ট ইন্টারফেস প্রদর্শিত হয়।
 */
@Composable
fun ShrinkableVideoContainer(
    isCommentsOpen: Boolean,
    onCloseComments: () -> Unit,
    videoContent: @Composable BoxScope.(isShrunk: Boolean) -> Unit,
    commentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // কমেন্ট ওপেন থাকা অবস্থায় ব্যাক বাটন চাপলে অ্যাপ ব্যাক না হয়ে কমেন্ট বক্স ক্লোজ হবে
    BackHandler(enabled = isCommentsOpen) {
        onCloseComments()
    }

    // ১. ভিডিওর উচ্চতা অ্যানিমেশন (ফুলস্ক্রিন ১.০f থেকে কমে ০.৩৮f হবে)
    val videoHeightRatio by animateFloatAsState(
        targetValue = if (isCommentsOpen) 0.38f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "VideoHeightRatio"
    )

    // ২. ১ নম্বর ছবির মতো ভিডিওর চারপাশের রাউন্ডেড কর্নার অ্যানিমেশন
    val cornerRadius by animateDpAsState(
        targetValue = if (isCommentsOpen) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "VideoCornerRadius"
    )

    // ৩. ভিডিওর সাইড ও টপ মার্জিন অ্যানিমেশন
    val horizontalMargin by animateDpAsState(
        targetValue = if (isCommentsOpen) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "VideoHorizontalMargin"
    )

    val topMargin by animateDpAsState(
        targetValue = if (isCommentsOpen) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "VideoTopMargin"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // =========================================================================
        // 📺 ওপরে সংকুচিত হতে পারা ভিডিও প্লেয়ার সারফেস
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(videoHeightRatio)
                .statusBarsPadding()
                .padding(top = topMargin, start = horizontalMargin, end = horizontalMargin)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFF0C0F15))
        ) {
            videoContent(isCommentsOpen)
        }

        // =========================================================================
        // 💬 নিচে ইনস্টাগ্রাম স্টাইল কমেন্ট সেকশন (১ নম্বর ছবি)
        // =========================================================================
        AnimatedVisibility(
            visible = isCommentsOpen,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
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
                    // নিচের দিকে সোয়াইপ ড্র্যাগ করলে কমেন্ট বক্স স্মুথলি বন্ধ হবে
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 35) {
                                onCloseComments()
                            }
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ড্র্যাগ হ্যান্ডেল বার (১ নম্বর ছবির মতো উপরে ছোট ড্যাশ)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF333C4D))
                        )
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
