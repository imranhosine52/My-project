package com.example.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ⏱️ প্লেয়ার স্পিড সিলেকশন সাইড ড্রয়ার
 */
@Composable
fun PlayerSpeedDrawer(
    currentSpeed: Float,
    speedOptions: List<Float> = listOf(2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f),
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 16.dp, start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ১. হেডার
        item {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SPEED",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ২. স্পিড অপশনস তালিকা (0.5x - 2.0x)
        items(speedOptions) { spd ->
            val isSelected = (currentSpeed == spd)
            val label = if (spd == 1.0f) "1.0x" else "${spd}x"

            Text(
                text = label,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onSelectSpeed(spd) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}
