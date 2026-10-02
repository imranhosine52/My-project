@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay

private val ActionGreen = Color(0xFF00E676)
private val AlertRed = Color(0xFFFF3B30)
private val GoldVip = Color(0xFFFFB300)

/**
 * 🧾 ভিআইপি পেমেন্ট ইনভয়েস ও ট্রানজেকশন হিস্ট্রি বটম শীট
 */
@Composable
fun ProfileInvoiceSheet(
    viewModel: DramaFlixViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRefreshing = true
        viewModel.refreshVipStatusAndProfile()
        delay(400)
        isRefreshing = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // ড্র্যাগ ইন্ডিকেটর
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333D4F))
                )
            }

            // হেডার রো
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment & Invoices",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = ActionGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMutedSlate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // ইনভয়েস লিস্ট
            if (vipState.invoiceHistory.isEmpty() && !isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No payment submissions found yet.",
                        color = TextMutedSlate,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = vipState.invoiceHistory,
                        key = { it.trxId.ifBlank { it.id } }
                    ) { inv ->
                        val isApproved = inv.status.equals("active", true) || inv.status.equals("approved", true)
                        val isRejected = inv.status.equals("rejected", true) || inv.status.equals("declined", true) || inv.status.equals("failed", true)

                        val statusColor = when {
                            isApproved -> ActionGreen
                            isRejected -> AlertRed
                            else -> GoldVip
                        }

                        val statusLabel = when {
                            isApproved -> "APPROVED ✅"
                            isRejected -> "REJECTED ❌"
                            else -> "PENDING ⏳"
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2A)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CardBorderStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = inv.planName,
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = inv.displayAmount,
                                        color = GoldVip,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )

                                Text(
                                    text = "Status: $statusLabel • Date: ${inv.displayDate}",
                                    color = statusColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
