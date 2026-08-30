@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvoiceItemDto
import com.example.data.model.SubscriptionPlanDto
import com.example.ui.components.VipCheckoutDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val GoldAccent = Color(0xFFFFB300)
private val CardDarkBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00E676)
private val SafeGreenBg = Color(0xFF082618)

private data class FaqItem(val question: String, val answer: String)

private val faqList = listOf(
    FaqItem("How fast is VIP membership activated?", "VIP membership is activated automatically within 5-15 minutes after our system verifies your TrxID."),
    FaqItem("Can I stream on multiple devices with one account?", "Yes, you can log in with your Google account on your phone, tablet, or Android TV simultaneously."),
    FaqItem("Are all movies and web series 100% ad-free?", "Yes! VIP members enjoy zero video ads, zero popunders, and full 1080p 60fps high-speed streaming.")
)

private enum class VipPageView {
    PRICING,
    INVOICES
}

@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    var currentView by remember { mutableStateOf(VipPageView.PRICING) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (currentView) {
            // -------------------------------------------------------------
            // 1. VIP PRICING VIEW (Matching Screenshot)
            // -------------------------------------------------------------
            VipPageView.PRICING -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp, start = 14.dp, end = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Navigation / Header Badge
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark)
                                    .clickable { onNavigateBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 👑 VIP STREAMING PASS Badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF261D05),
                                border = BorderStroke(1.2.dp, GoldAccent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("👑", fontSize = 12.sp)
                                    Text(
                                        text = "VIP STREAMING PASS",
                                        color = GoldAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Invoices History Button on top right
                            Text(
                                text = "Invoices",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { currentView = VipPageView.INVOICES }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Main Title & Subtitle
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "Upgrade to Ad-Free Ultra HD",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Stream all movies, web series, and exclusive dramas in 1080p 60fps with zero advertisements.",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // Section Heading: Choose Your Plan
                    item {
                        Text(
                            text = "Choose Your Plan",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    // VIP Pricing Plans List (Matching Screenshot Cards)
                    val plans = vipState.plans.ifEmpty {
                        listOf(
                            SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59", rawOriginalPrice = "88.50", durationDays = 30, isPopular = true),
                            SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150", rawOriginalPrice = "200.00", durationDays = 90, isPopular = false)
                        )
                    }

                    items(plans) { plan ->
                        VipPricingPlanCard(
                            plan = plan,
                            onBuyNowClick = {
                                viewModel.selectVipPlan(plan)
                                showCheckoutDialog = true
                            }
                        )
                    }

                    // 100% Safe Trust Badge
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SafeGreenBg,
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🛡️", fontSize = 13.sp)
                                Text(
                                    text = "100% Safe & Instant Automated Activation",
                                    color = SafeGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // FAQ Section
                    item {
                        FaqSection()
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. INVOICES & PAYMENT HISTORY VIEW
            // -------------------------------------------------------------
            VipPageView.INVOICES -> {
                VipInvoicesScreen(
                    invoices = vipState.invoiceHistory,
                    onBackClick = { currentView = VipPageView.PRICING }
                )
            }
        }

        // Dedicated Checkout Modal
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
                    ) { success, _ ->
                        showCheckoutDialog = false
                        if (success) {
                            // 🚀 পেমেন্ট সফল হলে সরাসরি ইনভয়েস পেজে নিয়ে যাবে
                            currentView = VipPageView.INVOICES
                        }
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// 💳 VIP Plan Card (Screenshot Style)
// -------------------------------------------------------------
@Composable
private fun VipPricingPlanCard(
    plan: SubscriptionPlanDto,
    onBuyNowClick: () -> Unit
) {
    val isMostPopular = plan.isPopular || plan.durationDays == 30

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isMostPopular) 8.dp else 0.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardDarkBg),
            border = BorderStroke(
                width = if (isMostPopular) 1.5.dp else 1.dp,
                color = if (isMostPopular) GoldAccent else BorderDark
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Plan Title + Duration Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = plan.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Duration Access Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF003B46),
                        border = BorderStroke(0.8.dp, Color(0xFF00ADB5))
                    ) {
                        Text(
                            text = "${plan.durationDays} Days Access",
                            color = Color(0xFF00FFF5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Price Row: ৳ 59  ৳ 88.50  33% OFF
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "৳ ${plan.priceFormatted}",
                        color = GoldAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )

                    if (plan.originalPriceDouble > plan.priceDouble) {
                        Text(
                            text = "৳ ${plan.originalPriceFormatted}",
                            color = TextMuted,
                            fontSize = 14.sp,
                            textDecoration = TextDecoration.LineThrough
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4A1521),
                            border = BorderStroke(0.8.dp, Color(0xFFFF2A4B))
                        ) {
                            Text(
                                text = "${plan.discountPercent}% OFF",
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, thickness = 0.6.dp)

                // Feature Checklist
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = feature,
                                color = Color(0xFFDCE0E8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ⚡ BUY NOW Glowing Button
                Button(
                    onClick = onBuyNowClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚡", fontSize = 15.sp)
                        Text(
                            text = "BUY NOW",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // MOST POPULAR Ribbon on Top Right
        if (isMostPopular) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFF9800),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-9).dp)
            ) {
                Text(
                    text = "MOST POPULAR",
                    color = Color.Black,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// ❓ FAQ Accordion Section
// -------------------------------------------------------------
@Composable
private fun FaqSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("❓", fontSize = 14.sp)
            Text(
                text = "Frequently Asked Questions",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        faqList.forEach { faq ->
            var isExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = faq.question,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = faq.answer,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🧾 Invoices & Payment History Screen
// -------------------------------------------------------------
@Composable
private fun VipInvoicesScreen(
    invoices: List<InvoiceItemDto>,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariantDark)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            Text("Invoices & Subscriptions", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (invoices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No payment submissions yet.", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(invoices.size) { index ->
                    val inv = invoices[index]
                    val isApproved = inv.status == "active" || inv.status == "approved"
                    val statusCol = if (isApproved) SafeGreen else Color(0xFFFFB300)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(inv.planName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(inv.displayAmount, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Text("Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}", color = TextSecondary, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Date: ${inv.displayDate}", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    text = if (isApproved) "APPROVED ✅" else "PENDING APPROVAL ⏳",
                                    color = statusCol,
                                    fontSize = 11.sp,
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
