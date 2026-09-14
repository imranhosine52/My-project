@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val GoldAccent = Color(0xFFFFB300)
private val VipDarkCardBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val RejectRed = Color(0xFFFF334B)
private val VipBorderStrokeColor = Color(0xFF1E2536)

private data class FaqItem(val question: String, val answer: String)

private val faqList = listOf(
    FaqItem("How fast is VIP membership activated?", "Automatic Instant Activation: If your TrxID/Hash matches, VIP is activated within seconds! Manual reviews take 5-15 minutes."),
    FaqItem("Can I pay with Crypto (USDT)?", "Yes! We support multi-chain networks including BSC (BEP20), TRX (TRC20), Solana (SOL), TON, and Polygon."),
    FaqItem("Are all Asian dramas and movies 100% ad-free?", "Yes! VIP members enjoy zero video ads, full 1080p 60fps streaming, and unlimited offline downloads.")
)

private enum class VipScreenMode {
    PRICING,
    CHECKOUT,
    INVOICES
}

@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    var currentMode by remember { mutableStateOf(VipScreenMode.PRICING) }
    var selectedPlanForCheckout by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var showAuthBottomSheet by remember { mutableStateOf(false) }

    // 🔒 ইউজার বর্তমানে সক্রিয় VIP কিনা তা ইনভয়েস ও প্রোফাইল থেকে যাচাই করা
    val isUserCurrentlyVip = remember(vipState.invoiceHistory) {
        vipState.invoiceHistory.any { 
            it.status.equals("active", ignoreCase = true) || it.status.equals("approved", ignoreCase = true) 
        }
    }

    // ⏳ অলরেডি কোনো পেমেন্ট পেন্ডিং আছে কিনা যাচাই করা (ডাবল পেমেন্ট রোধ করতে)
    val hasPendingPayment = remember(vipState.invoiceHistory) {
        vipState.invoiceHistory.any { it.status.equals("pending", ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()
    }

    BackHandler {
        when (currentMode) {
            VipScreenMode.CHECKOUT -> currentMode = VipScreenMode.PRICING
            VipScreenMode.INVOICES -> currentMode = VipScreenMode.PRICING
            VipScreenMode.PRICING -> onNavigateBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (currentMode) {
            // =============================================================
            // 1. VIP PRICING PLANS (মূল প্রাইসিং পেজ)
            // =============================================================
            VipScreenMode.PRICING -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp, start = 14.dp, end = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }

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

                            Text(
                                text = "Invoices",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { currentMode = VipScreenMode.INVOICES }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // 👑 একটিভ ভিআইপি থাকলে তথ্য ব্যানার
                    if (isUserCurrentlyVip) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0F2618),
                                border = BorderStroke(1.2.dp, SafeGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("👑", fontSize = 24.sp)
                                    Column {
                                        Text("আপনার VIP সাবস্ক্রিপশন বর্তমানে সচল রয়েছে!", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text("চলমান প্ল্যানের মেয়াদ শেষ হওয়ার পূর্বে নতুন কোনো প্ল্যান নেওয়া যাবে না।", color = SafeGreen, fontSize = 11.5.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    } else if (hasPendingPayment) {
                        // ⏳ কোনো পেমেন্ট অপেক্ষমান থাকলে ওয়ার্নিং
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF332305),
                                border = BorderStroke(1.2.dp, GoldAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("⏳", fontSize = 22.sp)
                                    Column {
                                        Text("আপনার একটি পেমেন্ট ভেরিফিকেশনে রয়েছে!", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text("সার্ভার থেকে চূড়ান্ত সিদ্ধান্ত না আসা পর্যন্ত নতুন পেমেন্ট করা যাবে না।", color = GoldAccent, fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }

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
                                text = "Stream all movies, web series, and exclusive Asian dramas in 1080p with zero ads.",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // 🌐 সার্ভার থেকে প্ল্যান লোড করা (না থাকলে ডিফল্ট ডামি প্ল্যান)
                    val plans = vipState.plans.ifEmpty {
                        listOf(
                            SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59", rawOriginalPrice = "88.50", durationDays = 30, isPopular = true),
                            SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150", rawOriginalPrice = "200.00", durationDays = 90, isPopular = false)
                        )
                    }

                    items(plans) { plan ->
                        VipPricingPlanCard(
                            plan = plan,
                            isUserCurrentlyVip = isUserCurrentlyVip,
                            hasPendingPayment = hasPendingPayment,
                            onBuyNowClick = {
                                if (!authState.isLoggedIn) {
                                    Toast.makeText(context, "Please log in to purchase VIP membership", Toast.LENGTH_SHORT).show()
                                    showAuthBottomSheet = true
                                } else if (isUserCurrentlyVip) {
                                    Toast.makeText(context, "আপনার ভিআইপি মেয়াদ শেষ না হওয়া পর্যন্ত নতুন প্ল্যান নেওয়া যাবে না।", Toast.LENGTH_LONG).show()
                                } else if (hasPendingPayment) {
                                    Toast.makeText(context, "আপনার পূর্বের পেমেন্টটি এখনও পেন্ডিং রয়েছে।", Toast.LENGTH_LONG).show()
                                } else {
                                    selectedPlanForCheckout = plan
                                    currentMode = VipScreenMode.CHECKOUT
                                }
                            }
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF082618),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⚡", fontSize = 13.sp)
                                Text(
                                    text = "1-Sec Automated Instant Activation Engine",
                                    color = SafeGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        FaqSection()
                    }
                }
            }

            // =============================================================
            // 2. 📱 আলাদা ফাইলে থাকা CHECKOUT স্ক্রিন কল
            // =============================================================
            VipScreenMode.CHECKOUT -> {
                if (!authState.isLoggedIn) {
                    LaunchedEffect(Unit) {
                        currentMode = VipScreenMode.PRICING
                        showAuthBottomSheet = true
                    }
                } else {
                    val plan = selectedPlanForCheckout ?: vipState.selectedPlan ?: SubscriptionPlanDto(name = "Monthly VIP", rawPrice = "59")

                    VipCheckoutScreen(
                        plan = plan,
                        viewModel = viewModel,
                        invoices = vipState.invoiceHistory,
                        onBackClick = { currentMode = VipScreenMode.PRICING },
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToInvoices = { currentMode = VipScreenMode.INVOICES }
                    )
                }
            }

            // =============================================================
            // 3. 🧾 INVOICES & HISTORY (পেমেন্ট হিস্ট্রি)
            // =============================================================
            VipScreenMode.INVOICES -> {
                VipInvoicesScreen(
                    invoices = vipState.invoiceHistory,
                    onBackClick = { currentMode = VipScreenMode.PRICING }
                )
            }
        }

        if (showAuthBottomSheet) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthBottomSheet = false }
            )
        }
    }
}

// =============================================================
// 💳 VIP প্ল্যান প্রাইসিং কার্ড
// =============================================================
@Composable
private fun VipPricingPlanCard(
    plan: SubscriptionPlanDto,
    isUserCurrentlyVip: Boolean,
    hasPendingPayment: Boolean,
    onBuyNowClick: () -> Unit
) {
    val isMostPopular = plan.isPopular || plan.durationDays == 30
    val isButtonDisabled = isUserCurrentlyVip || hasPendingPayment

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isMostPopular) 8.dp else 0.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
            border = BorderStroke(
                width = if (isMostPopular) 1.5.dp else 1.dp,
                color = if (isMostPopular) GoldAccent else VipBorderStrokeColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Text(plan.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }

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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("৳ ${plan.priceFormatted}", color = GoldAccent, fontSize = 28.sp, fontWeight = FontWeight.Black)

                    if (plan.originalPriceDouble > plan.priceDouble) {
                        Text("৳ ${plan.originalPriceFormatted}", color = TextMuted, fontSize = 14.sp, textDecoration = TextDecoration.LineThrough)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4A1521),
                            border = BorderStroke(0.8.dp, Color(0xFFFF2A4B))
                        ) {
                            Text("${plan.discountPercent}% OFF", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }

                HorizontalDivider(color = VipBorderStrokeColor, thickness = 0.6.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                            Text(feature, color = Color(0xFFDCE0E8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Button(
                    onClick = onBuyNowClick,
                    enabled = !isButtonDisabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonDisabled) Color(0xFF2C3240) else GoldAccent,
                        disabledContainerColor = Color(0xFF1E2536)
                    )
                ) {
                    if (isUserCurrentlyVip) {
                        Text("VIP ACTIVE 👑", color = SafeGreen, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    } else if (hasPendingPayment) {
                        Text("VERIFICATION PENDING ⏳", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 15.sp)
                            Text("BUY VIP PASS NOW", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        if (isMostPopular) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFF9800),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-9).dp)
            ) {
                Text("MOST POPULAR", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

// =============================================================
// ❓ FAQ সেকশন
// =============================================================
@Composable
private fun FaqSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("❓", fontSize = 14.sp)
            Text("Frequently Asked Questions", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        faqList.forEach { faq ->
            var isExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                border = BorderStroke(1.dp, VipBorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(faq.question, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider(color = VipBorderStrokeColor, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(faq.answer, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// 🧾 ইনভয়েস হিস্ট্রি পেজ
// =============================================================
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
                items(
                    items = invoices,
                    key = { inv -> inv.trxId.ifBlank { inv.hashCode().toString() } }
                ) { inv ->
                    val st = inv.status.lowercase()
                    val isApproved = st == "active" || st == "approved"
                    val isRejected = st == "rejected" || st == "cancelled" || st == "failed"

                    val statusCol = when {
                        isApproved -> SafeGreen
                        isRejected -> RejectRed
                        else -> GoldAccent
                    }

                    val statusText = when {
                        isApproved -> "APPROVED ✅"
                        isRejected -> "REJECTED ❌"
                        else -> "PENDING ⏳"
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VipBorderStrokeColor),
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
                                    text = statusText,
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
