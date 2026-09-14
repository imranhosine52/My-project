@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import coil.compose.AsyncImage
import com.example.data.model.*
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

private enum class CheckoutStage {
    FORM,
    VERIFYING,
    APPROVED,
    REJECTED,
    TIMEOUT
}

@Composable
fun VipCheckoutScreen(
    plan: SubscriptionPlanDto,
    viewModel: DramaFlixViewModel,
    invoices: List<InvoiceItemDto>,
    gateways: List<GatewayItemDto> = emptyList(),          // 🌐 সার্ভার থেকে গেটওয়ে লিস্ট
    cryptoNetworks: List<CryptoNetworkDto> = emptyList(),  // 🌐 সার্ভার থেকে ক্রিপ্টো নেটওয়ার্ক লিস্ট
    cryptoEnabled: Boolean = true,                         // 🌐 সার্ভার থেকে ক্রিপ্টো চালু/বন্ধ ফ্ল্যাগ
    onBackClick: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInvoices: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 🌐 ১. সার্ভার থেকে আসা এক্টিভ গেটওয়ে ফিল্টারিং (সার্ভার থেকে বন্ধ হলে হাইড হয়ে যাবে)
    val activeGateways = remember(gateways) {
        val filtered = gateways.filter { it.isActive }
        if (filtered.isNotEmpty()) filtered else listOf(
            GatewayItemDto(id = "bkash", name = "bKash", number = "01330049110", type = "Personal", icon = "https://playdramaflix.com/public/bkash-logo.png"),
            GatewayItemDto(id = "nagad", name = "Nagad", number = "01330049110", type = "Personal", icon = "https://playdramaflix.com/public/nagad-logo.png")
        )
    }

    // 🌐 ২. সার্ভার থেকে আসা ক্রিপ্টো নেটওয়ার্ক (সার্ভার অ্যাড্রেস অনুযায়ী)
    val effectiveCryptoNetworks = remember(cryptoNetworks) {
        if (cryptoNetworks.isNotEmpty()) cryptoNetworks else listOf(
            CryptoNetworkDto(rawId = 1, name = "BSC (BEP20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / BNB", icon = "https://assets.coingecko.com/coins/images/825/standard/bnb-icon2_2x.png"),
            CryptoNetworkDto(rawId = 2, name = "TRX (TRC20)", address = "TJPXWFA8YZgjrRtTVDZP1r1QQJMsYM8Dt2", symbol = "USDT / TRX", icon = "https://assets.coingecko.com/coins/images/1094/standard/tron-logo.png"),
            CryptoNetworkDto(rawId = 3, name = "SOL (Solana)", address = "8QaBiG5yf4R8FAX4MmVHFkkBfJdtwZWbdtXusPW1ZjSS", symbol = "USDT / SOL", icon = "https://assets.coingecko.com/coins/images/4128/standard/solana.png"),
            CryptoNetworkDto(rawId = 4, name = "TON (TON)", address = "UQDpAC2Wbf-VU61mPFgXOKEoUD_owd77khHvj8TfKvBccgLF", symbol = "USDT / TON", icon = "https://assets.coingecko.com/coins/images/17980/standard/ton_symbol.png"),
            CryptoNetworkDto(rawId = 5, name = "Polygon (POL)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / MATIC", icon = "https://assets.coingecko.com/coins/images/4713/standard/polygon.png")
        )
    }

    var selectedMethodName by remember { mutableStateOf("") }
    var selectedGateway by remember { mutableStateOf<GatewayItemDto?>(null) }
    var selectedCryptoNetwork by remember { mutableStateOf(effectiveCryptoNetworks.first()) }

    // মেথড সিলেকশন সিনক্রোনাইজেশন
    LaunchedEffect(activeGateways, cryptoEnabled) {
        if (selectedMethodName.isBlank() || 
            (selectedMethodName != "USDT" && activeGateways.none { it.effectiveName.equals(selectedMethodName, ignoreCase = true) }) ||
            (selectedMethodName == "USDT" && !cryptoEnabled)) {
            selectedGateway = activeGateways.firstOrNull()
            selectedMethodName = selectedGateway?.effectiveName ?: if (cryptoEnabled) "USDT" else ""
        }
    }

    var checkoutStage by remember { mutableStateOf(CheckoutStage.FORM) }
    var submittedTrxId by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableIntStateOf(300) }

    // 💱 সার্ভার থেকে আসা টাকার পরিমাণ অনুযায়ী রিয়েল-টাইম USDT কনভার্ট
    val usdtAmountFormatted = remember(plan.priceDouble) {
        val calculated = plan.priceDouble / 120.0
        val finalVal = if (calculated < 0.50) 0.50 else calculated
        String.format(Locale.US, "%.2f", finalVal)
    }

    var senderNumberOrWallet by remember { mutableStateOf("") }
    var trxIdOrTxHash by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isSubmittingLoading by remember { mutableStateOf(false) }

    // 🔄 ইনভয়েস হিস্ট্রি চেক (Approved/Rejected)
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

    // ⏳ ৫ মিনিটের লাইভ টাইমার এবং ৪ সেকেন্ড পর পর সার্ভার পোলিং
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

        when (checkoutStage) {
            CheckoutStage.VERIFYING -> {
                LiveVerificationCountdownView(
                    remainingSeconds = remainingSeconds,
                    planName = plan.name,
                    trxId = submittedTrxId,
                    amount = if (selectedMethodName == "USDT") "$usdtAmountFormatted USDT" else "৳ ${plan.priceFormatted}",
                    paymentMethod = selectedMethodName
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
                // প্ল্যান ও ডাইনামিক প্রাইস কার্ড
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                    border = BorderStroke(1.dp, VipBorderStrokeColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.name, color = GoldAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${plan.durationDays} Days All-Access Pass", color = TextSecondary, fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (selectedMethodName == "USDT") {
                                Text("$usdtAmountFormatted USDT", color = CryptoCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("৳ ${plan.priceFormatted}", color = TextMuted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                            } else {
                                Text("৳ ${plan.priceFormatted}", color = SafeGreen, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Text("1. Select Payment Method", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

                // =========================================================================
                // 🌐 সার্ভার থেকে আসা এক্টিভ মেথডসমূহ (সার্ভার অফ করলে অটো হাইড হয়ে যাবে)
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeGateways.forEach { gw ->
                        val isSelected = selectedMethodName.equals(gw.effectiveName, ignoreCase = true)
                        val gwColor = when {
                            gw.effectiveName.contains("bkash", ignoreCase = true) -> Color(0xFFE2136E)
                            gw.effectiveName.contains("nagad", ignoreCase = true) -> Color(0xFFF7941D)
                            else -> GoldAccent
                        }
                        val gwBg = when {
                            isSelected && gw.effectiveName.contains("bkash", ignoreCase = true) -> Color(0xFF330C1C)
                            isSelected && gw.effectiveName.contains("nagad", ignoreCase = true) -> Color(0xFF331C08)
                            isSelected -> Color(0xFF261E05)
                            else -> Color(0xFF141A26)
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = gwBg,
                            border = BorderStroke(if (isSelected) 1.5.dp else 0.8.dp, if (isSelected) gwColor else VipBorderStrokeColor),
                            modifier = Modifier.weight(1f).height(82.dp).clickable {
                                selectedMethodName = gw.effectiveName
                                selectedGateway = gw
                                validationError = null
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val defaultLogo = when {
                                    gw.effectiveName.contains("bkash", ignoreCase = true) -> "https://playdramaflix.com/public/bkash-logo.png"
                                    gw.effectiveName.contains("nagad", ignoreCase = true) -> "https://playdramaflix.com/public/nagad-logo.png"
                                    else -> null
                                }
                                val logoUrl = gw.icon ?: defaultLogo

                                if (!logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = gw.effectiveName,
                                        modifier = Modifier.size(28.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text("💳", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = gw.effectiveName,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 🌐 ক্রিপ্টো অপশন (সার্ভার থেকে cryptoEnabled == true থাকলেই কেবল দেখাবে)
                    if (cryptoEnabled) {
                        val isUsdt = (selectedMethodName == "USDT")
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isUsdt) Color(0xFF062A33) else Color(0xFF141A26),
                            border = BorderStroke(if (isUsdt) 1.5.dp else 0.8.dp, if (isUsdt) CryptoCyan else VipBorderStrokeColor),
                            modifier = Modifier.weight(1f).height(82.dp).clickable {
                                selectedMethodName = "USDT"
                                selectedGateway = null
                                validationError = null
                            }
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
                                Text(
                                    text = "USDT (Crypto)",
                                    color = if (isUsdt) Color.White else TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // ক্রিপ্টো অথবা মোবাইল ব্যাংকিং তথ্য (সার্ভার ডেটা দিয়ে রেন্ডার)
                // =========================================================================
                if (selectedMethodName == "USDT" && cryptoEnabled) {
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
                                items(effectiveCryptoNetworks) { net ->
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

                            // 💵 কপি অ্যামাউন্ট কার্ড
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

                            // ওয়ালেট অ্যাড্রেস (সার্ভার থেকে লোডকৃত)
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
                    // 🌐 মোবাইল ব্যাংকিং নম্বর কার্ড (সার্ভারের effectiveNumber দিয়ে ডাইনামিক)
                    val activeNumber = selectedGateway?.effectiveNumber ?: "01330049110"
                    val activeType = selectedGateway?.type ?: "Personal"

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
                                Text("$selectedMethodName $activeType Send Money Number", color = TextMuted, fontSize = 11.sp)
                                Text(activeNumber, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Number", activeNumber))
                                Toast.makeText(context, "$selectedMethodName number copied!", Toast.LENGTH_SHORT).show()
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

                // ইনপুট ফর্ম
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
                            text = if (selectedMethodName == "USDT") "2. Enter Crypto Transaction Details" else "2. Enter Payment SMS Details",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = senderNumberOrWallet,
                            onValueChange = { senderNumberOrWallet = it; validationError = null },
                            label = { Text(if (selectedMethodName == "USDT") "Sender Wallet Address / Exchange" else "Sender Mobile Number") },
                            placeholder = { Text(if (selectedMethodName == "USDT") "e.g. Binance, TrustWallet, or Address" else "01XXXXXXXXX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedMethodName == "USDT") KeyboardType.Ascii else KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selectedMethodName == "USDT") CryptoCyan else SafeGreen,
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
                            label = { Text(if (selectedMethodName == "USDT") "Transaction Hash (TxID)" else "Transaction ID (TrxID)") },
                            placeholder = { Text(if (selectedMethodName == "USDT") "e.g. 0x8a9f... / TxID" else "e.g. BK927X10A") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selectedMethodName == "USDT") CryptoCyan else SafeGreen,
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

                                if (selectedMethodName != "USDT") {
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
                                checkoutStage = CheckoutStage.VERIFYING

                                viewModel.submitSubscriptionPayment(
                                    planId = plan.rawId ?: 1,
                                    planName = plan.name,
                                    amount = plan.priceDouble,
                                    paymentMethod = selectedMethodName,
                                    senderNumber = trimmedSender,
                                    trxId = trimmedTrx,
                                    notes = if (selectedMethodName == "USDT") "USDT Network: ${selectedCryptoNetwork.name}" else null
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
                                containerColor = if (selectedMethodName == "USDT") CryptoCyan else SafeGreen
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

            Text("ভেরিফিকেশন চলছে...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

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
