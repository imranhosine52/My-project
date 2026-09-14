@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val GoldAccent = Color(0xFFFFB300)
private val VipDarkCardBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val CryptoCyan = Color(0xFF00E5FF)
private val VipBorderStrokeColor = Color(0xFF1E2536)
private val RejectRed = Color(0xFFFF334B)

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

private enum class CheckoutStage {
    FORM,           // সাধারণ চেকআউট ফর্ম
    VERIFYING,      // সাবমিটের পর ৫ মিনিটের লাইভ কাউন্টডাউন
    APPROVED,       // সফলভাবে অ্যাপ্রুভড
    REJECTED,       // সার্ভার থেকে রিজেক্টেড
    TIMEOUT         // সময় পার হয়ে গেলে পেন্ডিং মেসেজ
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

    // 🔒 ইউজার বর্তমানে সক্রিয় VIP কিনা তা যাচাই করা
    val isUserCurrentlyVip = remember(vipState.invoiceHistory) {
        vipState.invoiceHistory.any { 
            it.status.equals("active", ignoreCase = true) || it.status.equals("approved", ignoreCase = true) 
        }
    }

    // ⏳ অলরেডি কোনো পেমেন্ট পেন্ডিং আছে কিনা যাচাই করা
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
            // 1. VIP PRICING PLANS
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

                    // 👑 একটিভ ভিআইপি থাকলে সতর্কবার্তা
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
                                        Text("চলমান প্ল্যানের মেয়াদ শেষ হওয়ার পূর্বে দ্বিতীয়বার নতুন কোনো প্ল্যান কেনা যাবে না।", color = SafeGreen, fontSize = 11.5.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    } else if (hasPendingPayment) {
                        // ⏳ পেন্ডিং পেমেন্ট থাকলে ওয়ার্নিং
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
                        FaqSection()
                    }
                }
            }

            // =============================================================
            // 2. 📱 FULL-SCREEN CHECKOUT (লাইভ কাউন্টডাউন ও রিজেক্ট হ্যান্ডলিং সহ)
            // =============================================================
            VipScreenMode.CHECKOUT -> {
                if (!authState.isLoggedIn) {
                    LaunchedEffect(Unit) {
                        currentMode = VipScreenMode.PRICING
                        showAuthBottomSheet = true
                    }
                } else {
                    val plan = selectedPlanForCheckout ?: vipState.selectedPlan ?: SubscriptionPlanDto(name = "Monthly VIP", rawPrice = "59")

                    FullScreenVipCheckoutView(
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
            // 3. 🧾 INVOICES & HISTORY
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
// 📱 Full-Screen Checkout & Live State Handler
// =============================================================
@Composable
private fun FullScreenVipCheckoutView(
    plan: SubscriptionPlanDto,
    viewModel: DramaFlixViewModel,
    invoices: List<InvoiceItemDto>,
    onBackClick: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInvoices: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var checkoutStage by remember { mutableStateOf(CheckoutStage.FORM) }
    var selectedMethod by remember { mutableStateOf("bKash") }
    var submittedTrxId by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableIntStateOf(300) } // ৫ মিনিট = ৩০০ সেকেন্ড

    // 💱 BDT কে USDT তে কনভার্ট (১ USDT = ১২০ টাকা আনুমানিক অনুপাত)
    val usdtAmountFormatted = remember(plan.priceDouble) {
        val calculated = plan.priceDouble / 120.0
        val finalVal = if (calculated < 0.50) 0.50 else calculated
        String.format(Locale.US, "%.2f", finalVal)
    }

    val defaultCryptoNetworks = remember {
        listOf(
            CryptoNetworkDto(rawId = 1, name = "BSC (BEP20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / BNB", icon = "https://assets.coingecko.com/coins/images/825/standard/bnb-icon2_2x.png"),
            CryptoNetworkDto(rawId = 2, name = "TRX (TRC20)", address = "TJPXWFA8YZgjrRtTVDZP1r1QQJMsYM8Dt2", symbol = "USDT / TRX", icon = "https://assets.coingecko.com/coins/images/1094/standard/tron-logo.png"),
            CryptoNetworkDto(rawId = 3, name = "SOL (Solana)", address = "8QaBiG5yf4R8FAX4MmVHFkkBfJdtwZWbdtXusPW1ZjSS", symbol = "USDT / SOL", icon = "https://assets.coingecko.com/coins/images/4128/standard/solana.png"),
            CryptoNetworkDto(rawId = 4, name = "TON (TON)", address = "UQDpAC2Wbf-VU61mPFgXOKEoUD_owd77khHvj8TfKvBccgLF", symbol = "USDT / TON", icon = "https://assets.coingecko.com/coins/images/17980/standard/ton_symbol.png"),
            CryptoNetworkDto(rawId = 5, name = "Polygon (POL)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / MATIC", icon = "https://assets.coingecko.com/coins/images/4713/standard/polygon.png")
        )
    }

    var selectedCryptoNetwork by remember { mutableStateOf(defaultCryptoNetworks.first()) }
    var senderNumberOrWallet by remember { mutableStateOf("") }
    var trxIdOrTxHash by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isSubmittingLoading by remember { mutableStateOf(false) }

    val bKashNagadNumber = "01330049110"

    // 🔄 ভেরিফিকেশন চলাকালীন ইনভয়েস হিস্ট্রি চেক করা
    LaunchedEffect(invoices, checkoutStage) {
        if (checkoutStage == CheckoutStage.VERIFYING && submittedTrxId.isNotBlank()) {
            val matching = invoices.find { it.trxId.equals(submittedTrxId, ignoreCase = true) }
            if (matching != null) {
                val st = matching.status.lowercase()
                if (st == "approved" || st == "active") {
                    checkoutStage = CheckoutStage.APPROVED
                } else if (st == "rejected" || st == "cancelled" || st == "failed") {
                    checkoutStage = CheckoutStage.REJECTED
                }
            }
        }
    }

    // ⏳ ৫ মিনিটের লাইভ টাইমার এবং প্রতি ৪ সেকেন্ডে সার্ভার পোলিং
    LaunchedEffect(checkoutStage) {
        if (checkoutStage == CheckoutStage.VERIFYING) {
            remainingSeconds = 300
            while (remainingSeconds > 0 && checkoutStage == CheckoutStage.VERIFYING) {
                delay(1000L)
                remainingSeconds--
                if (remainingSeconds % 4 == 0) {
                    viewModel.refreshVipStatusAndProfile()
                }
            }
            if (remainingSeconds <= 0 && checkoutStage == CheckoutStage.VERIFYING) {
                checkoutStage = CheckoutStage.TIMEOUT
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // টপ বার
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (checkoutStage == CheckoutStage.FORM) {
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
            }
            Text("Checkout • ${plan.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // =========================================================================
        // ১. সাবমিটের পর পেজের ফর্ম উধাও হয়ে সরাসরি লাইভ স্ট্যাটাস স্ক্রিন আসবে
        // =========================================================================
        when (checkoutStage) {
            CheckoutStage.VERIFYING -> {
                LiveVerificationCountdownView(
                    remainingSeconds = remainingSeconds,
                    planName = plan.name,
                    trxId = submittedTrxId,
                    amount = if (selectedMethod == "USDT") "$usdtAmountFormatted USDT" else "৳ ${plan.priceFormatted}",
                    paymentMethod = selectedMethod
                )
            }
            CheckoutStage.APPROVED -> {
                ApprovedSuccessView(
                    planName = plan.name,
                    trxId = submittedTrxId,
                    onGoToProfile = onNavigateToProfile
                )
            }
            CheckoutStage.REJECTED -> {
                RejectedAlertView(
                    trxId = submittedTrxId,
                    onTryAgain = {
                        // ইউজারকে আবার সঠিকভাবে চেষ্টা করার সুযোগ দেওয়া
                        trxIdOrTxHash = ""
                        validationError = null
                        checkoutStage = CheckoutStage.FORM
                    }
                )
            }
            CheckoutStage.TIMEOUT -> {
                TimeoutPendingView(onGoToInvoices = onNavigateToInvoices)
            }
            CheckoutStage.FORM -> {
                // =========================================================================
                // ২. মূল চেকআউট ফর্ম (সাবমিটের পূর্বে প্রদর্শিত হবে)
                // =========================================================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                    border = BorderStroke(1.dp, VipBorderStrokeColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.name, color = GoldAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${plan.durationDays} Days All-Access Pass", color = TextSecondary, fontSize = 12.sp)
                        }

                        // 💱 ক্রিপ্টো সিলেক্ট থাকলে USDT প্রাইস শো করবে
                        Column(horizontalAlignment = Alignment.End) {
                            if (selectedMethod == "USDT") {
                                Text("$usdtAmountFormatted USDT", color = CryptoCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("৳ ${plan.priceFormatted}", color = TextMuted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                            } else {
                                Text("৳ ${plan.priceFormatted}", color = SafeGreen, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Text("1. Select Payment Method", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // bKash
                    val isBkash = (selectedMethod == "bKash")
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isBkash) Color(0xFF330C1C) else Color(0xFF141A26),
                        border = BorderStroke(if (isBkash) 1.5.dp else 0.8.dp, if (isBkash) Color(0xFFE2136E) else VipBorderStrokeColor),
                        modifier = Modifier.weight(1f).height(82.dp).clickable { selectedMethod = "bKash"; validationError = null }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AsyncImage(
                                model = "https://playdramaflix.com/public/bkash-logo.png",
                                contentDescription = "bKash",
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("bKash", color = if (isBkash) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Nagad
                    val isNagad = (selectedMethod == "Nagad")
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isNagad) Color(0xFF331C08) else Color(0xFF141A26),
                        border = BorderStroke(if (isNagad) 1.5.dp else 0.8.dp, if (isNagad) Color(0xFFF7941D) else VipBorderStrokeColor),
                        modifier = Modifier.weight(1f).height(82.dp).clickable { selectedMethod = "Nagad"; validationError = null }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AsyncImage(
                                model = "https://playdramaflix.com/public/nagad-logo.png",
                                contentDescription = "Nagad",
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Nagad", color = if (isNagad) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // USDT (Crypto)
                    val isUsdt = (selectedMethod == "USDT")
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isUsdt) Color(0xFF062A33) else Color(0xFF141A26),
                        border = BorderStroke(if (isUsdt) 1.5.dp else 0.8.dp, if (isUsdt) CryptoCyan else VipBorderStrokeColor),
                        modifier = Modifier.weight(1f).height(82.dp).clickable { selectedMethod = "USDT"; validationError = null }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AsyncImage(
                                model = "https://assets.coingecko.com/coins/images/325/standard/Tether.png",
                                contentDescription = "USDT",
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("USDT (Crypto)", color = if (isUsdt) Color.White else TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // =========================================================================
                // ক্রিপ্টো নেটওয়ার্ক ও অ্যামাউন্ট কপি অপশন
                // =========================================================================
                if (selectedMethod == "USDT") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1420)),
                        border = BorderStroke(1.dp, CryptoCyan.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Select Crypto Network (Chain):", color = CryptoCyan, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(defaultCryptoNetworks) { net ->
                                    val isSelectedNet = (net.name == selectedCryptoNetwork.name)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelectedNet) CryptoCyan.copy(alpha = 0.2f) else Color(0xFF161F2E),
                                        border = BorderStroke(1.dp, if (isSelectedNet) CryptoCyan else VipBorderStrokeColor),
                                        modifier = Modifier.clickable { selectedCryptoNetwork = net }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!net.icon.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = net.icon,
                                                    contentDescription = net.name,
                                                    modifier = Modifier.size(16.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                            Text(
                                                text = net.name,
                                                color = if (isSelectedNet) Color.White else TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelectedNet) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // 💵 কপি অ্যামাউন্ট কার্ড (স্ক্রিনশটে মার্ক করা স্থান)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF132230),
                                border = BorderStroke(1.dp, CryptoCyan.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Send Exact USDT Amount:", color = TextMuted, fontSize = 11.sp)
                                        Text("$usdtAmountFormatted USDT", color = CryptoCyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    }
                                    Button(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("USDT Amount", usdtAmountFormatted))
                                            Toast.makeText(context, "$usdtAmountFormatted USDT Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CryptoCyan),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Copy Amount", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // ওয়ালেট অ্যাড্রেস কপি বক্স
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141D2B))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Deposit Address (${selectedCryptoNetwork.name}):", color = TextSecondary, fontSize = 11.sp)
                                    Button(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Wallet", selectedCryptoNetwork.address))
                                            Toast.makeText(context, "${selectedCryptoNetwork.name} address copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CryptoCyan),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Copy Address", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    text = selectedCryptoNetwork.address,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp
                                )

                                Text(
                                    text = "Accepted Tokens: ${selectedCryptoNetwork.symbol}",
                                    color = GoldAccent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    // বিকাশ ও নগদ নাম্বার কার্ড
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF131A26))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0D121B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("$selectedMethod Personal Send Money Number", color = TextMuted, fontSize = 11.sp)
                                Text(bKashNagadNumber, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Number", bKashNagadNumber))
                                Toast.makeText(context, "$selectedMethod number copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SafeGreen, modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0D121B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Send Money Exact Amount", color = TextMuted, fontSize = 11.sp)
                                Text("৳ ${plan.priceFormatted}", color = GoldAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Amount", plan.priceFormatted))
                                Toast.makeText(context, "Amount copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GoldAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ফর্ম ফিল্ড ও সাবমিট বাটন
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                    border = BorderStroke(1.dp, VipBorderStrokeColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selectedMethod == "USDT") "2. Enter Crypto Transaction Details" else "2. Enter Payment SMS Details",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = senderNumberOrWallet,
                            onValueChange = { senderNumberOrWallet = it; validationError = null },
                            label = { Text(if (selectedMethod == "USDT") "Sender Wallet Address / Exchange" else "Sender Mobile Number") },
                            placeholder = { Text(if (selectedMethod == "USDT") "e.g. Binance, TrustWallet, or Address" else "017XXXXXXXX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedMethod == "USDT") KeyboardType.Ascii else KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selectedMethod == "USDT") CryptoCyan else SafeGreen,
                                unfocusedBorderColor = VipBorderStrokeColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = trxIdOrTxHash,
                            onValueChange = { trxIdOrTxHash = it.uppercase(); validationError = null },
                            label = { Text(if (selectedMethod == "USDT") "Transaction Hash (TxID)" else "Transaction ID (TrxID)") },
                            placeholder = { Text(if (selectedMethod == "USDT") "e.g. 0x8a9f... / TxID" else "e.g. BK927X10A") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selectedMethod == "USDT") CryptoCyan else SafeGreen,
                                unfocusedBorderColor = VipBorderStrokeColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (validationError != null) {
                            Text(validationError!!, color = RejectRed, fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                val trimmedSender = senderNumberOrWallet.trim()
                                val trimmedTrx = trxIdOrTxHash.trim()

                                if (selectedMethod != "USDT") {
                                    val bdPhoneRegex = Regex("^01[3-9]\\d{8}$")
                                    if (!bdPhoneRegex.matches(trimmedSender)) {
                                        validationError = "Please enter a valid 11-digit mobile number (01XXXXXXXXX)"
                                        return@Button
                                    }
                                    if (trimmedTrx.length < 6) {
                                        validationError = "Please enter a valid Transaction ID (TrxID)."
                                        return@Button
                                    }
                                } else {
                                    if (trimmedSender.length < 4) {
                                        validationError = "Please enter Sender Wallet/Exchange."
                                        return@Button
                                    }
                                    if (trimmedTrx.length < 6) {
                                        validationError = "Please enter Transaction Hash (TxID)."
                                        return@Button
                                    }
                                }

                                isSubmittingLoading = true
                                submittedTrxId = trimmedTrx

                                // 🚀 সাবমিট করার পর ফর্ম উধাও হয়ে কাউন্টডাউন স্টেজ চালু হবে
                                checkoutStage = CheckoutStage.VERIFYING

                                viewModel.submitSubscriptionPayment(
                                    planId = plan.rawId ?: 1,
                                    planName = plan.name,
                                    amount = plan.priceDouble,
                                    paymentMethod = selectedMethod,
                                    senderNumber = trimmedSender,
                                    trxId = trimmedTrx,
                                    notes = if (selectedMethod == "USDT") "USDT Network: ${selectedCryptoNetwork.name}" else null
                                ) { success, msg ->
                                    isSubmittingLoading = false
                                    viewModel.refreshVipStatusAndProfile()
                                    if (success) {
                                        if (msg?.contains("verified automatically", ignoreCase = true) == true ||
                                            msg?.contains("ACTIVE", ignoreCase = true) == true ||
                                            msg?.contains("approved", ignoreCase = true) == true) {
                                            checkoutStage = CheckoutStage.APPROVED
                                        }
                                    }
                                }
                            },
                            enabled = !isSubmittingLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMethod == "USDT") CryptoCyan else SafeGreen
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isSubmittingLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Text("Submit Payment for Verification", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// ⏳ ৫ মিনিটের লাইভ কাউন্টডাউন ভিউ (সাবমিটের পর ফর্মের স্থানে প্রদর্শিত হবে)
// =============================================================
@Composable
private fun LiveVerificationCountdownView(
    remainingSeconds: Int,
    planName: String,
    trxId: String,
    amount: String,
    paymentMethod: String
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1420)),
        border = BorderStroke(1.2.dp, CryptoCyan)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { remainingSeconds / 300f },
                    color = CryptoCyan,
                    strokeWidth = 4.dp,
                    trackColor = Color(0xFF1B2636),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = formattedTime,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "ভেরিফিকেশন চলছে...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "আমরা স্বয়ংক্রিয়ভাবে পেমেন্ট গেটওয়ের সাথে ট্রানজেকশন ম্যাচ করছি। ভেরিফাই হওয়ামাত্রই আপনার VIP লাইভ অ্যাক্টিভ হয়ে যাবে।",
                color = Color(0xFF94A3B8),
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141D2B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Plan: $planName", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Amount: $amount", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Method: $paymentMethod", color = TextMuted, fontSize = 11.5.sp)
                    Text("TrxID: $trxId", color = TextSecondary, fontSize = 11.5.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CryptoCyan, strokeWidth = 1.5.dp)
                Text("সার্ভার থেকে কনফার্মেশনের জন্য অপেক্ষা করা হচ্ছে...", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

// =============================================================
// 🎉 লাইভ সফল ও অ্যাক্টিভেশন স্ক্রিন
// =============================================================
@Composable
private fun ApprovedSuccessView(
    planName: String,
    trxId: String,
    onGoToProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1F16)),
        border = BorderStroke(1.5.dp, SafeGreen)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SafeGreen.copy(alpha = 0.2f))
                    .border(2.dp, SafeGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = 40.sp)
            }

            Text("VIP লাইভ অ্যাক্টিভ হয়েছে!", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)

            Text(
                text = "🎉 অভিনন্দন! আপনার ট্রানজেকশন সফলভাবে ম্যাচ হয়েছে। আপনার $planName এখন চালু রয়েছে।",
                color = SafeGreen,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF132B20),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("TrxID: $trxId", color = TextSecondary, fontSize = 12.sp)
                    Text("স্ট্যাটাস: APPROVED ✅", color = SafeGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onGoToProfile,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("প্রোফাইলে যান (Go to Profile) ➔", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================
// ❌ পেমেন্ট বাতিল / রিজেক্টেড স্ক্রিন
// =============================================================
@Composable
private fun RejectedAlertView(
    trxId: String,
    onTryAgain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF260C11)),
        border = BorderStroke(1.5.dp, RejectRed)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(RejectRed.copy(alpha = 0.2f))
                    .border(2.dp, RejectRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = RejectRed, modifier = Modifier.size(42.dp))
            }

            Text("পেমেন্ট বাতিল করা হয়েছে!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // আপনার চাহিদা মোতাবেক স্পষ্ট নির্দেশনা
            Text(
                text = "আপনার পাঠানো ট্রানজেকশন ম্যাচ করেনি। অনুগ্রহ করে সঠিক পরিমাণে টাকা/USDT পাঠান এবং সঠিক ট্রানজেকশন আইডি (TrxID / Hash) প্রদান করুন।",
                color = RejectRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF3B151B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("বাতিলকৃত TrxID: $trxId", color = TextMuted, fontSize = 11.5.sp)
                    Text("স্ট্যাটাস: REJECTED / CANCELLED ❌", color = RejectRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onTryAgain,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RejectRed),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("সঠিক তথ্য দিয়ে পুনরায় চেষ্টা করুন", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================
// ⌛ সময় শেষ হয়ে গেলে পেন্ডিং ভিউ
// =============================================================
@Composable
private fun TimeoutPendingView(
    onGoToInvoices: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E170A)),
        border = BorderStroke(1.2.dp, GoldAccent)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("⌛", fontSize = 42.sp)

            Text("অটো-ভেরিফিকেশন সময় শেষ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Text(
                text = "গেটওয়ে থেকে ইনস্ট্যান্ট রেসপন্স পাওয়া যায়নি। এটি বর্তমানে পেন্ডিং রয়েছে এবং আমাদের টিম ম্যানুয়ালি রিভিউ করে ৫-১৫ মিনিটের মধ্যে সক্রিয় করে দেবে।",
                color = TextSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Button(
                onClick = onGoToInvoices,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("ইনভয়েস স্ট্যাটাস দেখুন", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
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
// ❓ FAQ Section
// =============================================================
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
