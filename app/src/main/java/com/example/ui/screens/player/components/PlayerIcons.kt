package com.example.ui.screens.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🌟 PD Flix Brand Logo
 */
@Composable
fun PdFlixBrandLogo(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "P",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFF00F0FF), Color(0xFF0072FF))),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            Text(
                text = "D",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFF007A), Color(0xFFC000FF))),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(modifier = Modifier.width(3.dp))

            Box(contentAlignment = Alignment.TopCenter) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .offset(x = 1.dp, y = (-4).dp)
                        .align(Alignment.TopCenter)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(5.dp)
                                .rotate(20f)
                                .background(Color(0xFF00D2FF), RoundedCornerShape(1.dp))
                        )
                    }
                }

                Text(
                    text = "Flix",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFD6E4FF), Color(0xFF9CB8FF))
                        ),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        Canvas(
            modifier = Modifier
                .width(175.dp)
                .height(14.dp)
                .offset(y = (-4).dp)
        ) {
            val w = size.width
            val h = size.height

            val swooshBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF0072FF),
                    Color(0xFFFF007A),
                    Color(0xFFFF007A).copy(alpha = 0f)
                )
            )

            val path = Path().apply {
                moveTo(0f, h * 0.45f)
                quadraticBezierTo(w * 0.45f, h * 1.15f, w * 0.96f, 0f)
                quadraticBezierTo(w * 0.45f, h * 0.85f, 0f, h * 0.45f)
                close()
            }
            drawPath(path = path, brush = swooshBrush)
        }
    }
}

/**
 * 👑 মিনি ক্রাউন ভেক্টর আইকন
 */
@Composable
fun MiniCrownVector(modifier: Modifier = Modifier, tint: Color = Color(0xFFF6D38B)) {
    Canvas(modifier = modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.30f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.50f, 0f)
            lineTo(w * 0.72f, h * 0.65f)
            lineTo(w, h * 0.30f)
            lineTo(w * 0.85f, h * 0.95f)
            lineTo(w * 0.15f, h * 0.95f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

/**
 * 🎵 ডাবিং মিউজিক নোট আইকন
 */
@Composable
fun MusicNoteDubbingIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00E5FF)
) {
    Canvas(modifier = modifier.size(13.dp)) {
        val w = size.width
        val h = size.height

        val headRadius = w * 0.22f
        val headCenter = Offset(w * 0.30f, h * 0.72f)
        drawCircle(color = tint, radius = headRadius, center = headCenter)

        val stemStroke = 1.6.dp.toPx()
        val stemX = headCenter.x + headRadius * 0.75f
        drawLine(
            color = tint,
            start = Offset(stemX, headCenter.y),
            end = Offset(stemX, h * 0.16f),
            strokeWidth = stemStroke,
            cap = StrokeCap.Round
        )

        val flagPath = Path().apply {
            moveTo(stemX, h * 0.16f)
            cubicTo(
                w * 0.72f, h * 0.16f,
                w * 0.82f, h * 0.30f,
                w * 0.72f, h * 0.46f
            )
            lineTo(stemX, h * 0.38f)
            close()
        }
        drawPath(flagPath, color = tint)
    }
}

/**
 * ⤢ ফুলস্ক্রিন ডায়াগোনাল আইকন
 */
@Composable
fun DiagonalFullscreenIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        drawLine(color, Offset(w * 0.14f, h * 0.44f), Offset(w * 0.14f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.44f, h * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, h * 0.14f), Offset(w * 0.40f, h * 0.40f), stroke, StrokeCap.Round)

        drawLine(color, Offset(w * 0.86f, h * 0.56f), Offset(w * 0.86f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.54f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.86f), Offset(w * 0.60f, h * 0.60f), stroke, StrokeCap.Round)
    }
}

/**
 * ⏩ ডাবল ট্যাপ স্কিপ ব্যাজ (+10 / -10)
 */
@Composable
fun OttDoubleTapSkipBadge(
    isForward: Boolean,
    skipSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (!isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.85f, h * 0.15f)
                    lineTo(w * 0.20f, h * 0.50f)
                    lineTo(w * 0.85f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Text(
            text = if (isForward) "+$skipSeconds" else "-$skipSeconds",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    blurRadius = 8f
                )
            )
        )
        if (isForward) {
            Canvas(modifier = Modifier.size(13.dp, 18.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.15f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.15f, h * 0.85f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
