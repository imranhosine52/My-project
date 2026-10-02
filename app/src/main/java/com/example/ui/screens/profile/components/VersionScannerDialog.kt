package com.example.ui.screens.profile.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay

private val ActionGreen = Color(0xFF00E676)
private val BlueRadar = Color(0xFF2AABEE)

/**
 * 🛰️ অ্যাপ ভার্সন রাডার স্ক্যানার ও আপডেট চেকার ডায়ালগ
 */
@Composable
fun VersionScannerDialog(
    viewModel: DramaFlixViewModel,
    installedVersion: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isScanning by remember { mutableStateOf(true) }
    var scanStatusText by remember { mutableStateOf("Connecting to update server...") }
    var isUpToDate by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )

    LaunchedEffect(Unit) {
        delay(600)
        scanStatusText = "Checking latest version compatibility..."
        delay(700)
        scanStatusText = "Verifying package integrity..."

        val updateInfo = viewModel.scanServerForUpdate()
        delay(500)

        if (updateInfo?.updateAvailable == true) {
            onDismiss()
            viewModel.checkAppVersion(forceShow = true)
        } else {
            isScanning = false
            isUpToDate = true
            scanStatusText = "You are already using the latest version!"
        }
    }

    Dialog(
        onDismissRequest = { if (!isScanning) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isScanning,
            dismissOnClickOutside = !isScanning
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101522)),
            border = BorderStroke(1.2.dp, if (isUpToDate) ActionGreen else BlueRadar)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAngle)) {
                            val r = size.minDimension / 2
                            drawCircle(
                                color = BlueRadar.copy(alpha = 0.2f),
                                radius = r,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = BlueRadar.copy(alpha = 0.4f),
                                radius = r * 0.65f,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            drawLine(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, BlueRadar)),
                                start = center,
                                end = Offset(center.x + r, center.y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = BlueRadar,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Text(
                        text = "Scanning for Updates...",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scanStatusText,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(ActionGreen.copy(alpha = 0.15f))
                            .border(2.dp, ActionGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ActionGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Text(
                        text = "You're Up to Date!",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ActionGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ActionGreen)
                    ) {
                        Text(
                            text = "Installed Version: v$installedVersion",
                            color = ActionGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Great!", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
