@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GatewayItemDto
import com.example.data.model.InvoiceItemDto
import com.example.data.model.SubscriptionPlanDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val GoldAccent = Color(0xFFFFB300)
private val CardDarkBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val SafeGreenBg = Color(0xFF082618)
private val WarningAmber = Color(0xFFFFB300)

private data class FaqItem(val question: String, val answer: String)

private val faqList = listOf(
    FaqItem("How fast is VIP membership activated?", "VIP membership is activated automatically within 5-15 minutes after our system verifies your TrxID."),
    FaqItem("Can I stream on multiple devices with one account?", "Yes, you can log in with your Google account on your phone, tablet, or Android TV simultaneously."),
    FaqItem("Are all movies and web series 100% ad-free?", "Yes! VIP members enjoy zero video ads, zero popunders, and full 1080p 60fps high-speed streaming.")
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle() // 👈 লগইন স্টেট ট্র্যাক করা

    var currentMode by remember { mutableStateOf(VipScreenMode.PRICING) }
    var selectedPlanForCheckout by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var showAuthBottomSheet by remember { mutableStateOf(false) } // 👈 সাইনআপ বটম শিট স্টেট

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()
    }

    // Smart Back Button Handling
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
            // -------------------------------------------------------------
            // 1. VIP PRICING SCREEN
            // -------------------------------------------------------------
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

                    item {
                        Text(
                            text = "Choose Your Plan",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

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
                                // 🔒 লগইন চেক লজিক: লগইন না থাকলে সাইনআপ বটম শিট ওপেন হবে
                                if (!authState.isLoggedIn) {
                                    Toast.makeText(context, "Please log in to purchase VIP membership", Toast.LENGTH_SHORT).show()
                                    showAuthBottomSheet = true
                                } else {
                                    selectedPlanForCheckout = plan
                                    currentMode = VipScreenMode.CHECKOUT // 👈 লগইন থাকলে সরাসরি চেকআউটে যাবে
                                }
                            }
                        )
                    }

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

                    item {
                        FaqSection()
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. 📱 FULL-SCREEN DEDICATED CHECKOUT PAGE
            // -------------------------------------------------------------
            VipScreenMode.CHECKOUT -> {
                // চেকআউট পেজে ঢোকার সিকিউরিটি গার্ড
                if (!authState.isLoggedIn) {
                    LaunchedEffect(Unit) {
                        currentMode = VipScreenMode.PRICING
                        showAuthBottomSheet = true
                    }
                } else {
                    val plan = selectedPlanForCheckout ?: vipState.selectedPlan ?: SubscriptionPlanDto(name = "Monthly VIP", rawPrice = "59")

                    FullScreenCheckoutView(
                        plan = plan,
                        gateways = vipState.paymentGateways,
                        isSubmitting = vipState.isSubmitting,
                        onBackClick = { currentMode = VipScreenMode.PRICING },
                        onSubmit = { senderNo, trxId ->
                            viewModel.submitSubscriptionPayment(
                                planId = plan.rawId ?: 1,
                                planName = plan.name,
                                amount = plan.priceDouble,
                                paymentMethod = "bKash",
                                senderNumber = senderNo,
                                trxId = trxId,
                                notes = null
                            ) { success, _ ->
                                if (success) {
                                    Toast.makeText(context, "Payment submitted successfully!", Toast.LENGTH_SHORT).show()
                                    currentMode = VipScreenMode.INVOICES
                                }
                            }
                        }
                    )
                }
            }

            // -------------------------------------------------------------
            // 3. 🧾 INVOICES & PAYMENT HISTORY SCREEN
            // -------------------------------------------------------------
            VipScreenMode.INVOICES -> {
                VipInvoicesScreen(
                    invoices = vipState.invoiceHistory,
                    onBackClick = { currentMode = VipScreenMode.PRICING }
                )
            }
        }

        // 🔐 সাইনআপ / লগইন বটম শিট (Authentication Bottom Sheet)
        if (showAuthBottomSheet) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthBottomSheet = false }
            )
        }
    }
}

// -------------------------------------------------------------
// 📱 Full-Screen Dedicated Checkout View
// -------------------------------------------------------------
@Composable
private fun FullScreenCheckoutView(
    plan: SubscriptionPlanDto,
    gateways: List<GatewayItemDto>,
    isSubmitting: Boolean,
    onBackClick: () -> Unit,
    onSubmit: (senderNumber: String, trxId: String) -> Unit
) {
    val context = LocalContext.current
    var selectedGatewayName by remember { mutableStateOf("bKash") }
    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val targetNumber = "01330049110"
    val targetAmount = "৳ ${plan.priceFormatted}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Back Row
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

            Text("Checkout", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 💳 Card 1: Secure Checkout Summary & Gateways
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardDarkBg),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                    Text("Secure Checkout", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // Package and Total Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Package:", color = TextMuted, fontSize = 12.5.sp)
                    Text(plan.name, color = GoldAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text("• Total Amount:", color = TextMuted, fontSize = 12.5.sp)
                    Text("৳ ${plan.priceFormatted}", color = SafeGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Text("1. Select Payment Method:", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                // Side-by-Side Gateway Cards (bKash & Nagad)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // bKash Card
                    val isBkash = (selectedGatewayName == "bKash")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBkash) Color(0xFF261019) else SurfaceVariantDark,
                        border = BorderStroke(if (isBkash) 1.5.dp else 0.8.dp, if (isBkash) Color(0xFFE2136E) else BorderDark),
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clickable { selectedGatewayName = "bKash" }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🦩", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("bKash (বিকাশ)", color = if (isBkash) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Nagad Card
                    val isNagad = (selectedGatewayName == "Nagad")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNagad) Color(0xFF2A1B0E) else SurfaceVariantDark,
                        border = BorderStroke(if (isNagad) 1.5.dp else 0.8.dp, if (isNagad) Color(0xFFF7941D) else BorderDark),
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clickable { selectedGatewayName = "Nagad" }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🔥", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Nagad (নগদ)", color = if (isNagad) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Send Money Guide Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(15.dp))
                        Text("SEND MONEY GUIDE", color = SafeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Target Number with Copy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C1017))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$selectedGatewayName Personal Send Money Number", color = TextMuted, fontSize = 11.sp)
                            Text(targetNumber, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Number", targetNumber))
                            Toast.makeText(context, "$selectedGatewayName number copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SafeGreen, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Amount with Copy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C1017))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Send Money Amount", color = TextMuted, fontSize = 11.sp)
                            Text(targetAmount, color = GoldAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Amount", plan.priceFormatted))
                            Toast.makeText(context, "Amount copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GoldAccent, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Warning Alert Notice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF261D05),
                        border = BorderStroke(0.8.dp, WarningAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Text(
                                text = "Please send exact amount to the personal number above via Send Money. Once transaction is successful, enter your TrxID below.",
                                color = WarningAmber,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 📝 Card 2: Submit Transaction Details
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardDarkBg),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                    Text("2. Submit Transaction Details", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Text("Enter your payment sender mobile number and TrxID below.", color = TextMuted, fontSize = 12.sp)

                // Sender Number Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SENDER MOBILE NUMBER (SENDER NO)", color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = senderNumber,
                        onValueChange = { senderNumber = it; validationError = null },
                        placeholder = { Text("017XXXXXXXX", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // TrxID Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TRANSACTION ID (TRXID)", color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = trxId,
                        onValueChange = { trxId = it.uppercase(); validationError = null },
                        placeholder = { Text("BK786X921", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (validationError != null) {
                    Text(validationError!!, color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Green Send Request Button
                Button(
                    onClick = {
                        if (senderNumber.trim().length < 6) {
                            validationError = "Please enter a valid Sender Number."
                            return@Button
                        }
                        if (trxId.trim().length < 4) {
                            validationError = "Please enter the Transaction ID (TrxID)."
                            return@Button
                        }
                        onSubmit(senderNumber.trim(), trxId.trim())
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Text("Send Request", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Change Plan Button
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text("Change Plan", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 💳 VIP Plan Pricing Card
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
                // Header
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

                // Price
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

                // ⚡ BUY NOW Button
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
// ❓ FAQ Section
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
            Text("Frequently Asked Questions", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        Text(faq.question, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(faq.answer, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 🧾 Invoices Screen
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
