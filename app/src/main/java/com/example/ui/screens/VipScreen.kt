package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvoiceItemDto
import com.example.data.model.SubscriptionPlanDto
import com.example.ui.VipCrownVectorIcon
import com.example.ui.components.VipCheckoutDialog
import com.example.ui.components.VipSubmissionSuccessDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("vip_screen_list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // VIP Hero Card Header
            item {
                VipHeroHeaderCard(
                    isVip = vipState.isVip,
                    planName = vipState.planName,
                    daysRemaining = vipState.daysRemaining,
                    expiresAt = vipState.expiresAt
                )
            }

            // VIP Benefits List
            item {
                VipBenefitsCard()
            }

            // Subscription Pricing Plans Header
            item {
                Text(
                    text = "Choose Your VIP Pass",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Pricing Plans
            items(vipState.plans) { plan ->
                val isSelected = vipState.selectedPlan?.id == plan.id || vipState.selectedPlan?.name == plan.name
                VipPlanCard(
                    plan = plan,
                    isSelected = isSelected,
                    onSelect = {
                        viewModel.selectVipPlan(plan)
                        showCheckoutDialog = true
                    }
                )
            }

            // Past Invoices & Status
            if (vipState.invoiceHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Subscriptions & Invoices",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(vipState.invoiceHistory) { invoice ->
                    InvoiceHistoryCard(invoice = invoice)
                }
            }
        }

        // Checkout Dialog
        if (showCheckoutDialog && vipState.selectedPlan != null) {
            VipCheckoutDialog(
                plan = vipState.selectedPlan!!,
                gateways = vipState.paymentGateways,
                isSubmitting = vipState.isSubmitting,
                onDismiss = { showCheckoutDialog = false },
                onSubmitPayment = { planId, planName, amount, gateway, sender, trxId, notes ->
                    viewModel.submitSubscriptionPayment(
                        planId = planId,
                        planName = planName,
                        amount = amount,
                        paymentMethod = gateway,
                        senderNumber = sender,
                        trxId = trxId,
                        notes = notes
                    ) { success, msg ->
                        showCheckoutDialog = false
                        if (success) {
                            showSuccessDialog = true
                        }
                    }
                }
            )
        }

        // Success Confirmation Dialog
        if (showSuccessDialog) {
            VipSubmissionSuccessDialog(
                invoiceId = vipState.lastSubmittedInvoiceId ?: "SUB-PENDING",
                planName = vipState.selectedPlan?.name ?: "VIP Pass",
                message = vipState.submissionMessage,
                onDismiss = {
                    showSuccessDialog = false
                    viewModel.resetSubmissionState()
                }
            )
        }
    }
}

@Composable
private fun VipHeroHeaderCard(
    isVip: Boolean,
    planName: String?,
    daysRemaining: Int,
    expiresAt: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF332600),
                        Color(0xFF1F1800),
                        Color(0xFF120E00)
                    )
                )
            )
            .border(1.2.dp, GoldVip, RoundedCornerShape(22.dp))
            .padding(18.dp)
            .testTag("vip_header_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VipCrownVectorIcon(modifier = Modifier.size(54.dp, 40.dp))

            Text(
                text = if (isVip) "VIP All-Access Active" else "PlayDramaFlix VIP",
                color = GoldVip,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )

            if (isVip) {
                Text(
                    text = "${planName ?: "VIP Plan"} • $daysRemaining Days Remaining",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Unlimited 1080p Asian Dramas, Bangla & Hindi Dubbed, Zero Ads",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VipBenefitsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "VIP Exclusive Privileges",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            VipBenefitItem(
                icon = Icons.Outlined.Block,
                title = "100% Ad-Free Experience",
                subtitle = "Bypass all video & banner ads across the entire app",
                iconTint = TealAccent
            )

            VipBenefitItem(
                icon = Icons.Outlined.LockOpen,
                title = "All Episodes Unlocked",
                subtitle = "Watch every episode without waiting or rewards",
                iconTint = GoldVip
            )

            VipBenefitItem(
                icon = Icons.Outlined.Hd,
                title = "1080p Ultra HD Streaming",
                subtitle = "Crystal clear playback with multi-language dubbed audio",
                iconTint = Color(0xFF00E676)
            )

            VipBenefitItem(
                icon = Icons.Outlined.Speed,
                title = "High-Speed Direct CDN",
                subtitle = "Instant bufferless playback on bKash, Nagad, Rocket networks",
                iconTint = RedAccent
            )
        }
    }
}

@Composable
private fun VipBenefitItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun VipPlanCard(
    plan: SubscriptionPlanDto,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFF261D05) else SurfaceDark)
            .border(
                width = if (isSelected || plan.isPopular) 1.5.dp else 1.dp,
                color = if (isSelected || plan.isPopular) GoldVip else BorderDark,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .padding(16.dp)
            .testTag("plan_card_${plan.name.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = plan.name,
                        color = if (isSelected) GoldVip else TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (plan.isPopular) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GoldVip)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BEST VALUE",
                                color = GoldButtonText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Text(
                    text = "${plan.durationDays} Days Access • All Devices",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "৳ ${plan.priceFormatted}",
                        color = GoldVip,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Button(
                    onClick = onSelect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldVip),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Get VIP",
                        color = GoldButtonText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceHistoryCard(invoice: InvoiceItemDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = invoice.planName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val statusColor = when (invoice.status.lowercase()) {
                    "active", "approved" -> Color(0xFF00E676)
                    "pending" -> Color(0xFFFFB300)
                    else -> Color(0xFFFF5252)
                }

                Text(
                    text = invoice.status.uppercase(),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trx: ${invoice.trxId} (${invoice.paymentMethod})",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = invoice.displayAmount,
                    color = GoldVip,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
