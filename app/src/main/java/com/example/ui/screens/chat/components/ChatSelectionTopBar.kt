package com.example.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatSelectionTopBar(
    selectedCount: Int,
    canDeleteAny: Boolean, // অ্যাডমিন হলে অন্যের মেসেজও ডিলিট করতে পারবে
    canPinSelected: Boolean, // একটি মাত্র মেসেজ সিলেক্ট থাকলে পিন করার অপশন
    onCloseSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPinSelected: () -> Unit,
    onCopySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E2834),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onCloseSelection) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel Selection",
                        tint = Color.White
                    )
                }

                Text(
                    text = "$selectedCount Selected",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ১টি মেসেজ হলে পিন অপশন
                if (canPinSelected) {
                    IconButton(onClick = onPinSelected) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin Message",
                            tint = Color(0xFFFFB300)
                        )
                    }
                }

                // কপি বাটন
                IconButton(onClick = onCopySelected) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Text",
                        tint = Color.White
                    )
                }

                // 🗑️ একসাথে ডিলিট করার বাটন
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Selected Messages",
                        tint = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}
