@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.reels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserReelDto

private val DarkSheetBg = Color(0xFF16181F)
private val TextMuted = Color(0xFF8692A6)

@Composable
fun ReelsShareBottomSheet(
    reel: UserReelDto,
    isLoggedIn: Boolean,
    isCreatorPageUser: Boolean,
    onDismiss: () -> Unit,
    onRepostClick: () -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reelShareUrl = remember(reel) { reel.shareUrl }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ড্র্যাগ হ্যান্ডেল
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF333C4D),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            // হেডার
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Reel to",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF222634), thickness = 0.6.dp)

            // =========================================================================
            // 🌐 সোশ্যাল মিডিয়ার আসল ব্র্যান্ড লোগো রো
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🔁 ১. Repost (ক্রিয়েটরদের জন্য)
                if (isCreatorPageUser) {
                    BrandItemContainer(label = "Repost", onClick = {
                        if (!isLoggedIn) onRequireLogin()
                        else {
                            onRepostClick()
                            onDismiss()
                        }
                    }) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB300)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                // 🔗 ২. Copy Link
                BrandItemContainer(label = "Copy link", onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Reel Link", reelShareUrl))
                    Toast.makeText(context, "✓ Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A4454)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val stroke = 2.4.dp.toPx()
                            drawArc(
                                color = Color.White,
                                startAngle = 135f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(2f, 2f),
                                size = Size(size.width * 0.55f, size.height * 0.55f),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color.White,
                                startAngle = -45f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.40f, size.height * 0.40f),
                                size = Size(size.width * 0.55f, size.height * 0.55f),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.35f, size.height * 0.65f),
                                end = Offset(size.width * 0.65f, size.height * 0.35f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // 🟢 ৩. WhatsApp (অরিজিনাল লোগো)
                BrandItemContainer(label = "WhatsApp", onClick = {
                    shareToSpecificApp(context, "com.whatsapp", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            val w = size.width
                            val h = size.height

                            val bubblePath = Path().apply {
                                moveTo(w * 0.50f, h * 0.12f)
                                arcTo(
                                    rect = androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f),
                                    startAngleDegrees = -90f,
                                    sweepAngleDegrees = 295f,
                                    forceMoveTo = false
                                )
                                lineTo(w * 0.15f, h * 0.85f)
                                lineTo(w * 0.28f, h * 0.74f)
                                close()
                            }
                            drawPath(bubblePath, color = Color.White, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                            val phonePath = Path().apply {
                                moveTo(w * 0.35f, h * 0.42f)
                                cubicTo(w * 0.38f, h * 0.35f, w * 0.42f, h * 0.36f, w * 0.45f, h * 0.40f)
                                lineTo(w * 0.49f, h * 0.47f)
                                cubicTo(w * 0.51f, h * 0.50f, w * 0.49f, h * 0.53f, w * 0.46f, h * 0.55f)
                                cubicTo(w * 0.49f, h * 0.61f, w * 0.54f, h * 0.66f, w * 0.60f, h * 0.69f)
                                cubicTo(w * 0.62f, h * 0.66f, w * 0.65f, h * 0.64f, w * 0.68f, h * 0.66f)
                                lineTo(w * 0.75f, h * 0.70f)
                                cubicTo(w * 0.79f, h * 0.73f, w * 0.80f, h * 0.77f, w * 0.73f, h * 0.80f)
                                cubicTo(w * 0.65f, h * 0.85f, w * 0.50f, h * 0.75f, w * 0.40f, h * 0.65f)
                                cubicTo(w * 0.30f, h * 0.55f, w * 0.20f, h * 0.40f, w * 0.35f, h * 0.42f)
                                close()
                            }
                            drawPath(phonePath, color = Color.White, style = Fill)
                        }
                    }
                }

                // 🔵 ৪. Facebook (অরিজিনাল 'f' লোগো)
                BrandItemContainer(label = "Facebook", onClick = {
                    shareToSpecificApp(context, "com.facebook.katana", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(30.dp)) {
                            val w = size.width
                            val h = size.height

                            val fPath = Path().apply {
                                moveTo(w * 0.62f, h)
                                lineTo(w * 0.44f, h)
                                lineTo(w * 0.44f, h * 0.52f)
                                lineTo(w * 0.32f, h * 0.52f)
                                lineTo(w * 0.32f, h * 0.38f)
                                lineTo(w * 0.44f, h * 0.38f)
                                lineTo(w * 0.44f, h * 0.28f)
                                cubicTo(w * 0.44f, h * 0.14f, w * 0.52f, h * 0.08f, w * 0.68f, h * 0.08f)
                                lineTo(w * 0.76f, h * 0.08f)
                                lineTo(w * 0.76f, h * 0.22f)
                                lineTo(w * 0.66f, h * 0.22f)
                                cubicTo(w * 0.60f, h * 0.22f, w * 0.58f, h * 0.25f, w * 0.58f, h * 0.30f)
                                lineTo(w * 0.58f, h * 0.38f)
                                lineTo(w * 0.74f, h * 0.38f)
                                lineTo(w * 0.71f, h * 0.52f)
                                lineTo(w * 0.58f, h * 0.52f)
                                lineTo(w * 0.58f, h)
                                close()
                            }
                            drawPath(fPath, color = Color.White)
                        }
                    }
                }

                // 🟣 ৫. Instagram (অরিজিনাল ক্যামেরা ও গ্রেডিয়েন্ট লোগো)
                BrandItemContainer(label = "Instagram", onClick = {
                    shareToSpecificApp(context, "com.instagram.android", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF833AB4),
                                        Color(0xFFFD1D1D),
                                        Color(0xFFFCB045)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) {
                            val w = size.width
                            val h = size.height
                            val stroke = 2.4.dp.toPx()

                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(w * 0.08f, h * 0.08f),
                                size = Size(w * 0.84f, h * 0.84f),
                                cornerRadius = CornerRadius(w * 0.24f, h * 0.24f),
                                style = Stroke(width = stroke)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = w * 0.22f,
                                center = center,
                                style = Stroke(width = stroke)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = w * 0.045f,
                                center = Offset(w * 0.72f, h * 0.28f)
                            )
                        }
                    }
                }

                // ⚡ ৬. Messenger (অরিজিনাল লাইটনিং বোল্ট ও গ্রেডিয়েন্ট)
                BrandItemContainer(label = "Messenger", onClick = {
                    shareToSpecificApp(context, "com.facebook.orca", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF00B2FE),
                                        Color(0xFF006AFF),
                                        Color(0xFF9B00E8)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            val w = size.width
                            val h = size.height

                            val bubblePath = Path().apply {
                                moveTo(w * 0.50f, h * 0.08f)
                                cubicTo(w * 0.80f, h * 0.08f, w * 0.95f, h * 0.26f, w * 0.95f, h * 0.48f)
                                cubicTo(w * 0.95f, h * 0.66f, w * 0.80f, h * 0.82f, w * 0.55f, h * 0.85f)
                                lineTo(w * 0.48f, h * 0.95f)
                                lineTo(w * 0.42f, h * 0.85f)
                                cubicTo(w * 0.18f, h * 0.82f, w * 0.05f, h * 0.66f, w * 0.05f, h * 0.48f)
                                cubicTo(w * 0.05f, h * 0.26f, w * 0.20f, h * 0.08f, w * 0.50f, h * 0.08f)
                                close()
                            }
                            drawPath(bubblePath, color = Color.White)

                            val boltPath = Path().apply {
                                moveTo(w * 0.28f, h * 0.54f)
                                lineTo(w * 0.44f, h * 0.38f)
                                lineTo(w * 0.54f, h * 0.48f)
                                lineTo(w * 0.72f, h * 0.38f)
                                lineTo(w * 0.56f, h * 0.58f)
                                lineTo(w * 0.46f, h * 0.48f)
                                close()
                            }
                            drawPath(boltPath, color = Color(0xFF006AFF))
                        }
                    }
                }

                // ✈️ ৭. Telegram (অরিজিনাল পেপার প্লেন লোগো)
                BrandItemContainer(label = "Telegram", onClick = {
                    shareToSpecificApp(context, "org.telegram.messenger", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2AABEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) {
                            val w = size.width
                            val h = size.height

                            val planePath = Path().apply {
                                moveTo(w * 0.15f, h * 0.52f)
                                lineTo(w * 0.85f, h * 0.18f)
                                lineTo(w * 0.72f, h * 0.82f)
                                lineTo(w * 0.48f, h * 0.62f)
                                lineTo(w * 0.42f, h * 0.75f)
                                lineTo(w * 0.38f, h * 0.58f)
                                close()
                            }
                            drawPath(planePath, color = Color.White)

                            val shadowPath = Path().apply {
                                moveTo(w * 0.48f, h * 0.62f)
                                lineTo(w * 0.42f, h * 0.75f)
                                lineTo(w * 0.65f, h * 0.52f)
                                close()
                            }
                            drawPath(shadowPath, color = Color(0xFFD2EAF7))
                        }
                    }
                }

                // 𝕏 ৮. X (Formerly Twitter)
                BrandItemContainer(label = "X", onClick = {
                    shareToSpecificApp(context, "com.twitter.android", reelShareUrl)
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F1419))
                            .border(1.dp, Color(0xFF333D4F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val stroke = 2.4.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // 💬 ৯. SMS
                BrandItemContainer(label = "Messages", onClick = {
                    try {
                        val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("smsto:")
                            putExtra("sms_body", "Watch this reel on PlayDramaFlix: $reelShareUrl")
                        }
                        context.startActivity(smsIntent)
                    } catch (_: Exception) {}
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0EA5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val w = size.width
                            val h = size.height

                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(0f, h * 0.12f),
                                size = Size(w, h * 0.70f),
                                cornerRadius = CornerRadius(w * 0.20f, h * 0.20f)
                            )
                            drawCircle(color = Color(0xFF0EA5E9), radius = 2.2.dp.toPx(), center = Offset(w * 0.28f, h * 0.47f))
                            drawCircle(color = Color(0xFF0EA5E9), radius = 2.2.dp.toPx(), center = Offset(w * 0.50f, h * 0.47f))
                            drawCircle(color = Color(0xFF0EA5E9), radius = 2.2.dp.toPx(), center = Offset(w * 0.72f, h * 0.47f))
                        }
                    }
                }

                // 📤 ১০. More (সিস্টেম শেয়ার ডায়ালগ)
                BrandItemContainer(label = "More", onClick = {
                    val genericIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Watch this reel on PlayDramaFlix:\n$reelShareUrl")
                    }
                    context.startActivity(Intent.createChooser(genericIntent, "Share Reel via"))
                    onDismiss()
                }) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF28303F))
                            .border(1.dp, Color(0xFF3B485E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val w = size.width
                            val h = size.height
                            val nodeR = 3.2.dp.toPx()
                            val stroke = 1.8.dp.toPx()

                            val c1 = Offset(w * 0.75f, h * 0.25f)
                            val c2 = Offset(w * 0.25f, h * 0.50f)
                            val c3 = Offset(w * 0.75f, h * 0.75f)

                            drawLine(Color.White, c2, c1, strokeWidth = stroke)
                            drawLine(Color.White, c2, c3, strokeWidth = stroke)

                            drawCircle(Color.White, nodeR, c1)
                            drawCircle(Color.White, nodeR, c2)
                            drawCircle(Color.White, nodeR, c3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandItemContainer(
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(58.dp)
            .clickable { onClick() }
    ) {
        iconContent()

        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun shareToSpecificApp(context: Context, packageName: String, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(packageName)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val genericIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(genericIntent, "Share Reel via"))
    }
}
