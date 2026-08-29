package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Authentic 4-Color Google "G" Vector Canvas Icon
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Blue right-arm and top-right quadrant
        val blueColor = Color(0xFF4285F4)
        val redColor = Color(0xFFEA4335)
        val yellowColor = Color(0xFFFBBC05)
        val greenColor = Color(0xFF34A853)

        // Draw Google G geometry
        // 1. Right Blue bar
        drawRect(
            color = blueColor,
            topLeft = Offset(cx, cy - r * 0.22f),
            size = Size(r * 0.95f, r * 0.44f)
        )

        // 2. Red Top Arc
        val pathRed = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -45f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(path = pathRed, color = redColor, style = Fill)

        // 3. Yellow Left-top arc
        val pathYellow = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -135f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(path = pathYellow, color = yellowColor, style = Fill)

        // 4. Green Bottom arc
        val pathGreen = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -225f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(path = pathGreen, color = greenColor, style = Fill)

        // 5. Blue Bottom-right sweep
        val pathBlue = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -315f,
                sweepAngleDegrees = -45f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(path = pathBlue, color = blueColor, style = Fill)

        // Inner circle hole cutout
        drawCircle(
            color = Color(0xFF1E293B), // Match card background
            radius = r * 0.55f,
            center = Offset(cx, cy)
        )

        // Redraw center bar inside circle hole
        drawRect(
            color = blueColor,
            topLeft = Offset(cx - r * 0.05f, cy - r * 0.20f),
            size = Size(r * 0.98f, r * 0.40f)
        )
    }
}

/**
 * Premium 1-Click Google Sign-In / Sign-Up Button
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Continue with Google",
    isLoading: Boolean = false,
    height: Dp = 50.dp,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("google_sign_in_button"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B), // Premium dark surface
        border = BorderStroke(1.2.dp, Color(0xFF334155)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Signing in...",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                GoogleLogoIcon(modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
