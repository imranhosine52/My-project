package com.example.ui.screens.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🔀 সার্ভার মডেল (এখানে রাখায় সব ফাইলে অনায়াসে কাজ করবে)
data class GlobalStreamServer(
    val id: String,
    val displayName: String,
    val providerInfo: String,
    val isEmbed: Boolean
)

@Composable
fun PlayerServerSelectorSheet(
    servers: List<GlobalStreamServer>,
    selectedServerId: String,
    onClose: () -> Unit,
    onSelectServer: (GlobalStreamServer) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10141E))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Text("Select Video Server", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                }
            }

            HorizontalDivider(color = Color(0xFF222836), thickness = 0.8.dp)

            Text("If current stream buffers or does not load, please switch server below:", color = Color(0xFF8E95A5), fontSize = 12.sp)

            servers.forEach { srv ->
                val isSelected = (selectedServerId == srv.id)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF0E272C) else Color(0xFF181D2A),
                    border = BorderStroke(if (isSelected) 1.2.dp else 0.6.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF283144)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectServer(srv) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = if (!srv.isEmbed) Icons.Default.FlashOn else Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8E95A5),
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = srv.displayName,
                                    color = if (isSelected) Color.White else Color(0xFFDCE0E8),
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = srv.providerInfo,
                                    color = Color(0xFF7E869E),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
