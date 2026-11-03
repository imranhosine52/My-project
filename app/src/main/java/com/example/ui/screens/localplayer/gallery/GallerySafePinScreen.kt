package com.example.ui.screens.localplayer.gallery

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.LocalMediaScanner

/**
 * 🔒 সেফ ফোল্ডারের জন্য ফুলস্ক্রিন পিন যাচাই ও সেট করার স্ক্রিন
 */
@Composable
fun FullscreenPinScreen(
    context: Context,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val isPinSet = remember { LocalMediaScanner.isSafeFolderPinSet(context) }
    var step by remember { mutableStateOf(if (isPinSet) "ENTER" else "CREATE") }
    var pinText by remember { mutableStateOf("") }
    var tempCreatedPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when (step) {
        "CREATE" -> "Set PIN"
        "CONFIRM" -> "Confirm PIN"
        else -> "Enter PIN"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF383344))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ব্যাক বাটন
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // আইকন, টাইটেল ও পিন ইন্ডিকেটর
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFD700)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Key",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // ৪-ডিজিটের ড্যাশ/ডট ইন্ডিকেটর
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinText.length
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isFilled) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }

                // এরর মেসেজ
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // বাটন ও কাস্টম কি-প্যাড
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (pinText.length == 4) {
                            when (step) {
                                "CREATE" -> {
                                    tempCreatedPin = pinText
                                    pinText = ""
                                    errorMessage = null
                                    step = "CONFIRM"
                                }
                                "CONFIRM" -> {
                                    if (pinText == tempCreatedPin) {
                                        LocalMediaScanner.saveSafePin(context, pinText)
                                        onSuccess()
                                    } else {
                                        errorMessage = "PIN does not match! Try again."
                                        pinText = ""
                                        step = "CREATE"
                                    }
                                }
                                "ENTER" -> {
                                    val saved = LocalMediaScanner.getSavedSafePin(context)
                                    if (pinText == saved) {
                                        onSuccess()
                                    } else {
                                        errorMessage = "Incorrect PIN! Try again."
                                        pinText = ""
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(44.dp)
                ) {
                    Text(
                        text = if (step == "ENTER") "Unlock" else "Next",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                CustomNumericKeypad(
                    onNumberClick = { num ->
                        if (pinText.length < 4) {
                            pinText += num
                            errorMessage = null
                        }
                    },
                    onDeleteClick = {
                        if (pinText.isNotEmpty()) {
                            pinText = pinText.dropLast(1)
                            errorMessage = null
                        }
                    }
                )
            }
        }
    }
}

/**
 * 🔢 স্মুথ টাচ নিউমেরিক কি-প্যাড
 */
@Composable
fun CustomNumericKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable(enabled = key.isNotBlank()) {
                                if (key == "DEL") onDeleteClick() else onNumberClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (key.isNotBlank()) {
                            Text(
                                text = key,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
