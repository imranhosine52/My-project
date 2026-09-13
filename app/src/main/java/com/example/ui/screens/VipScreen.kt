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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val GoldAccent = Color(0xFFFFB300)
private val VipDarkCardBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val CryptoCyan = Color(0xFF00E5FF)
private val VipBorderStrokeColor = Color(0xFF1E2536)

private data class FaqItem(val question: String, val answer: String)

private val faqList = listOf(
    FaqItem("How fast is VIP membership activated?", "Automatic Instant Activation: If your TrxID/Hash matches, VIP is activated within 1 second! Manual reviews take 5-15 minutes."),
    FaqItem("Can I pay with Crypto (USDT)?", "Yes! We support multi-chain networks including BSC (BEP20), TRX (TRC20), Solana (SOL), TON, and Polygon."),
    FaqItem("Are all Asian dramas and movies 100% ad-free?", "Yes! VIP members enjoy zero video ads, full 1080p 60fps streaming, and unlimited offline downloads.")
)

private enum class VipScreenMode {
    PRICING,
    CHECKOUT,
    INVOICES
}

data class ActiveLiveVerificationInfo(
    val planName: String,
    val amount: String,
    val paymentMethod: String,
    val trxId: String
)

@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {}, // 👈 লাইভ অ্যাক্টিভ হলে সরাসরি প্রোফাইলে রিডাইরেক্ট
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    var currentMode by remember { mutableStateOf(VipScreenMode.PRICING) }
    var selectedPlanForCheckout by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var showAuthBottomSheet by remember { mutableStateOf(false) }

    // ⏳ ৫-মিনিটের লাইভ ভেরিফিকেশন স্টেট
    var liveVerificationInfo by remember { mutableStateOf<ActiveLiveVerificationInfo?>(null) }
    var autoApprovedInvoice by remember { mutableStateOf<InvoiceItemDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()
    }

    BackHandler {
        when {
            liveVerificationInfo != null -> liveVerificationInfo = null
            currentMode == VipScreenMode.CHECKOUT -> currentMode = VipScreenMode.PRICING
            currentMode == VipScreenMode.INVOICES -> currentMode = VipScreenMode.PRICING
            currentMode == VipScreenMode.PRICING -> onNavigateBack()
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
                                if (!authState.isLoggedIn) {
                                    Toast.makeText(context, "Please log in to purchase VIP membership", Toast.LENGTH_SHORT).show()
                                    showAuthBottomSheet = true
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
            // 2. 📱 FULL-SCREEN CHECKOUT
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
                        isSubmitting = vipState.isSubmitting,
                        onBackClick = { currentMode = VipScreenMode.PRICING },
                        onSubmit = { method, cryptoNet, senderNo, trxId ->
                            // 🚀 সাবমিট করার পর সাথে সাথে ৫ মিনিটের লাইভ ভেরিফিকেশন কাউন্টডাউন চালু
                            liveVerificationInfo = ActiveLiveVerificationInfo(
                                planName = plan.name,
                                amount = "৳ ${plan.priceFormatted}",
                                paymentMethod = if (method == "USDT") "USDT ($cryptoNet)" else method,
                                trxId = trxId
                            )

                            viewModel.submitSubscriptionPayment(
                                planId = plan.rawId ?: 1,
                                planName = plan.name,
                                amount = plan.priceDouble,
                                paymentMethod = method,
                                senderNumber = senderNo,
                                trxId = trxId,
                                notes = if (method == "USDT") "Crypto Network: $cryptoNet" else null
                            ) { success, msg ->
                                if (success) {
                                    if (msg?.contains("verified automatically", ignoreCase = true) == true ||
                                        msg?.contains("ACTIVE", ignoreCase = true) == true) {
                                        liveVerificationInfo = null
                                        autoApprovedInvoice = InvoiceItemDto(
                                            rawPlanName = plan.name,
                                            rawAmount = plan.priceDouble,
                                            rawPaymentMethod = method,
                                            rawTrxId = trxId,
                                            rawStatus = "approved"
                                        )
                                    }
                                }
                            }
                        }
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

        // =============================================================
        // ⏳ ৫ মিনিটের লাইভ ট্রানজেকশন ভেরিফিকেশন উইন্ডো
        // =============================================================
        liveVerificationInfo?.let { info ->
            LiveFiveMinuteVerificationDialog(
                info = info,
                userId = authState.userProfile?.id ?: "5",
                viewModel = viewModel,
                onVerifiedSuccess = { invoice ->
                    liveVerificationInfo = null
                    autoApprovedInvoice = invoice
                },
                onTimeout = {
                    liveVerificationInfo = null
                    currentMode = VipScreenMode.INVOICES
                },
                onCancel = { liveVerificationInfo = null }
            )
        }

        // 🎉 ভিআইপি অ্যাক্টিভেশন সেলিব্রেশন ও প্রোফাইলে রিডাইরেক্ট
        autoApprovedInvoice?.let { inv ->
            AutoApprovedCelebrationDialog(
                invoice = inv,
                onDismiss = {
                    autoApprovedInvoice = null
                    currentMode = VipScreenMode.PRICING
                    // 🎯 সরাসরি প্রোফাইল (Me) পেজে রিডাইরেক্ট
                    onNavigateToProfile()
                }
            )
        }
    }
}

// =============================================================
// 📱 Full-Screen Checkout (অরিজিনাল লোগো সহ)
// =============================================================
@Composable
private fun FullScreenVipCheckoutView(
    plan: SubscriptionPlanDto,
    isSubmitting: Boolean,
    onBackClick: () -> Unit,
    onSubmit: (paymentMethod: String, cryptoNetwork: String?, senderNumberOrWallet: String, trxId: String) -> Unit
) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf("bKash") }

    // 🌐 ক্রিপ্টো নেটওয়ার্কগুলোর অফিসিয়াল কয়েন লোগো
    val defaultCryptoNetworks = remember {
        listOf(
            CryptoNetworkDto(rawId = 1, name = "BSC (BEP20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / BNB", icon = "https://assets.coingecko.com/coins/images/825/standard/bnb-icon2_2x.png"),
            CryptoNetworkDto(rawId = 2, name = "TRX (TRC20)", address = "TJPXWFA8YZgjrRtTVDZP1r1QQJMsYM8Dt2", symbol = "USDT / TRX", icon = "https://assets.coingecko.com/coins/images/1094/standard/tron-logo.png"),
            CryptoNetworkDto(rawId = 3, name = "SOL (Solana)", address = "8QaBiG5yf4R8FAX4MmVHFkkBfJdtwZWbdtXusPW1ZjSS", symbol = "USDT / SOL", icon = "https://assets.coingecko.com/coins/images/4128/standard/solana.png"),
            CryptoNetworkDto(rawId = 4, name = "TON (TON)", address = "UQDpAC2Wbf-VU61mPFgXOKEoUD_owd77khHvj8TfKvBccgLF", symbol = "USDT / TON", icon = "https://assets.coingecko.com/coins/images/17980/standard/ton_symbol.png"),
            CryptoNetworkDto(rawId = 5, name = "Polygon (POL)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / MATIC", icon = "https://assets.coingecko.com/coins/images/4713/standard/polygon.png"),
            CryptoNetworkDto(rawId = 6, name = "Arbitrum One", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / ARB", icon = "https://assets.coingecko.com/coins/images/16547/standard/arbitrum-shield.png"),
            CryptoNetworkDto(rawId = 7, name = "ETH (ERC20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / ETH", icon = "https://assets.coingecko.com/coins/images/279/standard/ethereum.png")
        )
    }

    var selectedCryptoNetwork by remember { mutableStateOf(defaultCryptoNetworks.first()) }
    var senderNumberOrWallet by remember { mutableStateOf("") }
    var trxIdOrTxHash by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val bKashNagadNumber = "01330049110"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

            Text("Checkout • ${plan.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

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
                Text("৳ ${plan.priceFormatted}", color = SafeGreen, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }

        // =============================================================
        // 🔀 ৩টি পেমেন্ট মেথড (অরিজিনাল বিকাশ, নগদ ও USDT লোগো সহ)
        // =============================================================
        Text("1. Select Payment Method", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 🦩 বিকাশ বাটন (playdramaflix.com/public/bkash-logo.png)
            val isBkash = (selectedMethod == "bKash")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isBkash) Color(0xFF330C1C) else Color(0xFF141A26),
                border = BorderStroke(if (isBkash) 1.5.dp else 0.8.dp, if (isBkash) Color(0xFFE2136E) else VipBorderStrokeColor),
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp)
                    .clickable { selectedMethod = "bKash"; validationError = null }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://playdramaflix.com/public/bkash-logo.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = "bKash",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("bKash", color = if (isBkash) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 🔥 নগদ বাটন (playdramaflix.com/public/nagad-logo.png)
            val isNagad = (selectedMethod == "Nagad")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isNagad) Color(0xFF331C08) else Color(0xFF141A26),
                border = BorderStroke(if (isNagad) 1.5.dp else 0.8.dp, if (isNagad) Color(0xFFF7941D) else VipBorderStrokeColor),
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp)
                    .clickable { selectedMethod = "Nagad"; validationError = null }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://playdramaflix.com/public/nagad-logo.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Nagad",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Nagad", color = if (isNagad) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 💎 USDT Crypto Button
            val isUsdt = (selectedMethod == "USDT")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isUsdt) Color(0xFF062A33) else Color(0xFF141A26),
                border = BorderStroke(if (isUsdt) 1.5.dp else 0.8.dp, if (isUsdt) CryptoCyan else VipBorderStrokeColor),
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp)
                    .clickable { selectedMethod = "USDT"; validationError = null }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://assets.coingecko.com/coins/images/325/standard/Tether.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = "USDT",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("USDT (Crypto)", color = if (isUsdt) Color.White else TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =============================================================
        // 💎 USDT ক্রিপ্টো সিলেক্ট করা থাকলে: প্রতিটি কয়েনের অরিজিনাল লোগো
        // =============================================================
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
                            Text(
                                text = "Deposit Address (${selectedCryptoNetwork.name}):",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
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
                    Text(validationError!!, color = Color(0xFFFF5252), fontSize = 11.5.sp)
                }

                Button(
                    onClick = {
                        if (senderNumberOrWallet.trim().length < 4) {
                            validationError = if (selectedMethod == "USDT") "Please enter Sender Wallet/Exchange." else "Please enter Sender Mobile Number."
                            return@Button
                        }
                        if (trxIdOrTxHash.trim().length < 4) {
                            validationError = if (selectedMethod == "USDT") "Please enter Transaction Hash (TxID)." else "Please enter Transaction ID (TrxID)."
                            return@Button
                        }
                        onSubmit(
                            selectedMethod,
                            if (selectedMethod == "USDT") selectedCryptoNetwork.name else null,
                            senderNumberOrWallet.trim(),
                            trxIdOrTxHash.trim()
                        )
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMethod == "USDT") CryptoCyan else SafeGreen
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSubmitting) {
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

// =============================================================
// ⏳ ৫ মিনিটের লাইভ ট্রানজেকশন ভেরিফিকেশন ডায়ালগ (রিয়েল-টাইম পোলিং সহ)
// =============================================================
@Composable
private fun LiveFiveMinuteVerificationDialog(
    info: ActiveLiveVerificationInfo,
    userId: String,
    viewModel: DramaFlixViewModel,
    onVerifiedSuccess: (InvoiceItemDto) -> Unit,
    onTimeout: () -> Unit,
    onCancel: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(300) } // ৫ মিনিট = ৩০০ সেকেন্ড

    // 🔄 প্রতি ৪ সেকেন্ড পর পর সার্ভারে অটো-ভেরিফিকেশন চেক
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--

            if (remainingSeconds % 4 == 0) {
                val statusResult = viewModel.repository.getSubscriptionStatus(userId)
                val status = statusResult.getOrNull()
                if (status?.isVip == true) {
                    // 🎉 পেমেন্ট অটো ভেরিফাই হয়ে গেছে!
                    viewModel.refreshVipStatusAndProfile()
                    onVerifiedSuccess(
                        InvoiceItemDto(
                            rawPlanName = info.planName,
                            rawAmount = info.amount,
                            rawPaymentMethod = info.paymentMethod,
                            rawTrxId = info.trxId,
                            rawStatus = "approved"
                        )
                    )
                    break
                }
            }
        }

        if (remainingSeconds <= 0) {
            onTimeout()
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1420)),
            border = BorderStroke(1.2.dp, CryptoCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // অ্যানিমেটেড টাইমার সার্কেল
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { remainingSeconds / 300f },
                        color = CryptoCyan,
                        strokeWidth = 3.dp,
                        trackColor = Color(0xFF1B2636),
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = formattedTime,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Verifying Transaction...",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Checking with payment gateway in real-time. Your VIP will activate instantly once confirmed.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141D2B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("TrxID / Hash: ${info.trxId}", color = TextMuted, fontSize = 11.sp)
                        Text("Method: ${info.paymentMethod}", color = TextMuted, fontSize = 11.sp)
                        Text("Amount: ${info.amount}", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onCancel) {
                    Text("Check Later in Invoices", color = TextSecondary, fontSize = 12.5.sp)
                }
            }
        }
    }
}

// =============================================================
// 🎉 ইনস্ট্যান্ট অটো-অ্যাপ্রুভাল সেলিব্রেশন ও প্রোফাইলে রিডাইরেক্ট
// =============================================================
@Composable
private fun AutoApprovedCelebrationDialog(
    invoice: InvoiceItemDto,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1420)),
            border = BorderStroke(1.5.dp, SafeGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(SafeGreen.copy(alpha = 0.2f))
                        .border(2.dp, SafeGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👑", fontSize = 36.sp)
                }

                Text("VIP ACTIVATED!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

                Text(
                    text = "🎉 Payment verified automatically! Your ${invoice.planName} is now ACTIVE.",
                    color = SafeGreen,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141D2B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("TrxID / Hash: ${invoice.trxId}", color = TextMuted, fontSize = 11.5.sp)
                        Text("Amount: ${invoice.displayAmount}", color = GoldAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("Status: APPROVED ✅", color = SafeGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Go to Profile (Me) ➔", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
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
                        Text("BUY VIP PASS NOW", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
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
                items(invoices.size) { index ->
                    val inv = invoices[index]
                    val isApproved = inv.status == "active" || inv.status == "approved"
                    val statusCol = if (isApproved) SafeGreen else GoldAccent

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
                                    text = if (isApproved) "APPROVED ✅" else "PENDING ⏳",
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
