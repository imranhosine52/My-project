@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.DramaFlixApplication
import com.example.data.model.*
import com.example.data.repository.PlayDramaFlixRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.util.VipStatusNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val PureBlackBg = Color(0xFF06080E)
private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2536)
private val GoldAccent = Color(0xFFFFB300)
private val SafeGreen = Color(0xFF00D166)
private val RejectRed = Color(0xFFFF3B30)
private val InputDarkBg = Color(0xFF162032)

private const val BDT_TO_USD_RATE = 122.5

private enum class PaymentTypeTab {
    MFS_LOCAL,
    CRYPTO_GLOBAL
}

private enum class VerificationState {
    INPUT_FORM,
    COUNTDOWN_POLLING,
    APPROVED_SUCCESS,
    DECLINED_ERROR
}

private fun getCryptoLogoUrl(name: String): String {
    val n = name.uppercase(Locale.ROOT)
    return when {
        n.contains("BSC") || n.contains("BINANCE") || n.contains("BEP20") || n.contains("OPBNB") -> "https://cryptologos.cc/logos/bnb-bnb-logo.png"
        n.contains("TRX") || n.contains("TRON") || n.contains("TRC20") -> "https://cryptologos.cc/logos/tron-trx-logo.png"
        n.contains("ETH") || n.contains("ERC20") || n.contains("SCROLL") -> "https://cryptologos.cc/logos/ethereum-eth-logo.png"
        n.contains("SOL") || n.contains("SOLANA") -> "https://cryptologos.cc/logos/solana-sol-logo.png"
        n.contains("TON") -> "https://cryptologos.cc/logos/toncoin-ton-logo.png"
        n.contains("POL") || n.contains("POLYGON") || n.contains("MATIC") -> "https://cryptologos.cc/logos/polygon-matic-logo.png"
        n.contains("APT") || n.contains("APTOS") -> "https://cryptologos.cc/logos/aptos-apt-logo.png"
        n.contains("ARBITRUM") || n.contains("ARB") -> "https://cryptologos.cc/logos/arbitrum-arb-logo.png"
        n.contains("AVAX") || n.contains("AVALANCHE") -> "https://cryptologos.cc/logos/avalanche-avax-logo.png"
        n.contains("NEAR") -> "https://cryptologos.cc/logos/near-protocol-near-logo.png"
        n.contains("DOT") || n.contains("POLKADOT") -> "https://cryptologos.cc/logos/polkadot-new-dot-logo.png"
        n.contains("OPTIMISM") || n.contains("OP") -> "https://cryptologos.cc/logos/optimism-ethereum-op-logo.png"
        n.contains("CELO") -> "https://cryptologos.cc/logos/celo-celo-logo.png"
        n.contains("KAIA") || n.contains("KLAYTN") -> "https://cryptologos.cc/logos/klaytn-klay-logo.png"
        n.contains("KAVA") -> "https://cryptologos.cc/logos/kava-kava-logo.png"
        n.contains("XTZ") || n.contains("TEZOS") -> "https://cryptologos.cc/logos/tezos-xtz-logo.png"
        else -> "https://cryptologos.cc/logos/tether-usdt-logo.png"
    }
}

private fun getMfsLogoUrl(id: String): String {
    return when (id.lowercase()) {
        "bkash" -> "https://playdramaflix.com/public/bkash-logo.png"
        "nagad" -> "https://playdramaflix.com/public/nagad-logo.png"
        "rocket" -> "https://seeklogo.com/images/D/dutch-bangla-rocket-logo-B4D1CC458D-seeklogo.com.png"
        "upay" -> "https://seeklogo.com/images/U/upay-logo-746BC67156-seeklogo.com.png"
        else -> "https://cdn-icons-png.flaticon.com/512/2830/2830284.png"
    }
}

private val DefaultCryptoList = listOf(
    CryptoNetworkDto(rawId = 1, name = "BSC (BEP20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / BNB"),
    CryptoNetworkDto(rawId = 2, name = "TRX (TRC20)", address = "TJPXWFA8YZgjrRtTVDZP1r1QQJMsYM8Dt2", symbol = "USDT / TRX"),
    CryptoNetworkDto(rawId = 3, name = "ETH (ERC20)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT / ETH"),
    CryptoNetworkDto(rawId = 4, name = "APT (Aptos)", address = "0xb4646786df4ee092a5623c4c82dd9490d07eaffbe9c81c795ddf5a757b7d6473", symbol = "APT"),
    CryptoNetworkDto(rawId = 5, name = "PLASMA (Plasma)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "USDT"),
    CryptoNetworkDto(rawId = 6, name = "POL (Polygon)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "POL / USDT"),
    CryptoNetworkDto(rawId = 7, name = "SOL (Solana)", address = "8QaBiG5yf4R8FAX4MmVHFkkBfJdtwZWbdtXusPW1ZjSS", symbol = "USDT / SOL"),
    CryptoNetworkDto(rawId = 8, name = "TON (TON)", address = "UQDpAC2Wbf-VU61mPFgXOKEoUD_owd77khHvj8TfKvBccgLF", symbol = "USDT / TON"),
    CryptoNetworkDto(rawId = 9, name = "ARBITRUM", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "ARB / USDT"),
    CryptoNetworkDto(rawId = 10, name = "AVAXC (Avalanche)", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "AVAX / USDT"),
    CryptoNetworkDto(rawId = 11, name = "CELO", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "CELO"),
    CryptoNetworkDto(rawId = 12, name = "OPTIMISM", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "OP / USDT"),
    CryptoNetworkDto(rawId = 13, name = "KAIA", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "KAIA"),
    CryptoNetworkDto(rawId = 14, name = "OPBNB", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "BNB / USDT"),
    CryptoNetworkDto(rawId = 15, name = "NEAR", address = "d310cad8834cb5eff60f433c44d1f8f5e880546a2d61c8cae9f075103ab43cf3", symbol = "NEAR"),
    CryptoNetworkDto(rawId = 16, name = "KAVAEVM", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "KAVA"),
    CryptoNetworkDto(rawId = 17, name = "DOT (Polkadot)", address = "14rkSaESzfmkHHNS4iZXVggB3DMfQEmLoTYzyk3MVDLdmt7j", symbol = "DOT"),
    CryptoNetworkDto(rawId = 18, name = "XTZ (Tezos)", address = "tz2M9NH6ovFDigDREatUaiKnfWWaZxMVsLhY", symbol = "XTZ"),
    CryptoNetworkDto(rawId = 19, name = "SCROLL", address = "0x9cc85d119b113914034913858ea30d1f9eb52d2e", symbol = "ETH / USDT")
)

private val DefaultGatewaysList = listOf(
    GatewayItemDto(id = "bkash", name = "bKash", number = "01330049110", type = "Personal"),
    GatewayItemDto(id = "nagad", name = "Nagad", number = "01330049110", type = "Personal")
)

@Composable
fun VipCheckoutScreen(
    plan: SubscriptionPlanDto,
    viewModel: DramaFlixViewModel,
    invoices: List<InvoiceItemDto>,
    onBackClick: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInvoices: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    // 🎯 ফিক্সড: DramaFlixApplication থেকে সরাসরি পাবলিক রিপোজিটরি গ্রহণ (ViewModel এর private ভ্যারিয়েবলের ওপর নির্ভরশীলতা বাদ)
    val repository = remember(context) {
        (context.applicationContext as? DramaFlixApplication)?.repository 
            ?: PlayDramaFlixRepository(context.applicationContext)
    }

    val currentUserId = remember(authState.userProfile) {
        authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    BackHandler { onBackClick() }

    // পূর্বে সাবমিট করা কোনো রিয়াল পেন্ডিং ইনভয়েস থাকলে পোলিং মোডে যাওয়া
    val existingPendingInvoice = remember(invoices) {
        invoices.firstOrNull { it.status.equals("pending", ignoreCase = true) }
    }

    var activeGateways by remember { mutableStateOf(DefaultGatewaysList) }
    var activeCryptoNetworks by remember { mutableStateOf(DefaultCryptoList) }
    var isCryptoGloballyEnabled by remember { mutableStateOf(true) }

    var selectedTab by remember { mutableStateOf(PaymentTypeTab.MFS_LOCAL) }
    var selectedGateway by remember { mutableStateOf<GatewayItemDto?>(DefaultGatewaysList.first()) }
    var selectedCryptoNetwork by remember { mutableStateOf<CryptoNetworkDto?>(DefaultCryptoList.first()) }

    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }

    var verificationState by remember {
        mutableStateOf(
            if (existingPendingInvoice != null) VerificationState.COUNTDOWN_POLLING
            else VerificationState.INPUT_FORM
        )
    }

    var activeTrackingTrxId by remember {
        mutableStateOf(existingPendingInvoice?.trxId ?: "")
    }

    var remainingSeconds by remember { mutableIntStateOf(300) }
    var rejectionReasonMessage by remember { mutableStateOf("") }
    var isSubmittingToServer by remember { mutableStateOf(false) }

    val cryptoAmountInUsd = remember(plan.priceDouble) {
        String.format(Locale.US, "%.2f", plan.priceDouble / BDT_TO_USD_RATE)
    }

    // গেটওয়ে ও প্ল্যান লোড করা
    LaunchedEffect(Unit) {
        val result = repository.getSubscriptionPlans()
        if (result.isSuccess) {
            val res = result.getOrNull()
            if (res != null) {
                if (res.paymentGateways.isNotEmpty()) {
                    activeGateways = res.paymentGateways
                    selectedGateway = res.paymentGateways.first()
                }
                isCryptoGloballyEnabled = res.cryptoEnabled
                if (res.cryptoNetworks.isNotEmpty()) {
                    activeCryptoNetworks = res.cryptoNetworks
                    selectedCryptoNetwork = res.cryptoNetworks.first()
                }
            }
        }
    }

    // ⏱️ প্রতি ২ সেকেন্ডে সরাসরি TrxID দিয়ে সার্ভারে ইনস্ট্যান্ট স্ট্যাটাস যাচাই
    LaunchedEffect(verificationState, activeTrackingTrxId) {
        if (verificationState == VerificationState.COUNTDOWN_POLLING && activeTrackingTrxId.isNotBlank()) {
            remainingSeconds = 300
            val targetTrx = activeTrackingTrxId.trim()

            while (remainingSeconds > 0 && verificationState == VerificationState.COUNTDOWN_POLLING) {
                delay(2000) // 👈 প্রতি ২ সেকেন্ড পর পর সার্ভারে চেক করবে
                remainingSeconds -= 2

                scope.launch(Dispatchers.IO) {
                    try {
                        val statusUrl = "https://playdramaflix.com/api/v1/subscription/status?trx_id=$targetTrx&user_id=$currentUserId"
                        val url = java.net.URL(statusUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.setRequestProperty("Accept", "application/json")

                        if (conn.responseCode in 200..299) {
                            val responseText = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream)).readText()
                            val json = org.json.JSONObject(responseText)
                            val isVipActive = json.optBoolean("is_vip", false)
                            val trxStatus = json.optString("trx_status", "").lowercase()

                            withContext(Dispatchers.Main) {
                                // 🟢 ১. সার্ভার এপ্রুভ করামাত্রই সাথে সাথে গ্রিন স্ক্রিন
                                if (isVipActive || trxStatus == "approved" || trxStatus == "active") {
                                    viewModel.updateInvoiceStatus(targetTrx, "approved")
                                    VipStatusNotificationHelper.showVipApprovedNotification(context, plan.name)
                                    verificationState = VerificationState.APPROVED_SUCCESS
                                    viewModel.refreshVipStatusAndProfile()
                                } 
                                // 🔴 ২. সার্ভার রিজেক্ট করামাত্রই সাথে সাথে রেড স্ক্রিন
                                else if (trxStatus == "rejected" || trxStatus == "declined" || trxStatus == "failed") {
                                    viewModel.updateInvoiceStatus(targetTrx, "rejected")
                                    VipStatusNotificationHelper.showVipRejectedNotification(context, "Payment was rejected by admin. Please verify your TrxID.")
                                    verificationState = VerificationState.DECLINED_ERROR
                                    rejectionReasonMessage = "Transaction was rejected by admin. Please check your TrxID or contact support."
                                    viewModel.refreshVipStatusAndProfile()
                                }
                            }
                        }
                        conn.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }
    }
        }
    }

    val hasMfs = activeGateways.isNotEmpty()
    val hasCrypto = isCryptoGloballyEnabled && activeCryptoNetworks.isNotEmpty()
    val formattedMinutes = String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlackBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ১. টপ হেডার
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
                        .background(Color(0xFF19202E))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "VIP Checkout",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DeepCardBg,
                    border = BorderStroke(0.8.dp, CardBorderColor),
                    modifier = Modifier.clickable { onNavigateToInvoices() }
                ) {
                    Text(
                        text = "Invoices",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ২. কাউন্টডাউন পোলিং কার্ড
        if (verificationState == VerificationState.COUNTDOWN_POLLING) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCardBg),
                    border = BorderStroke(1.2.dp, GoldAccent)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(46.dp), color = GoldAccent, strokeWidth = 3.dp)

                        Text(
                            text = "Automated Verification in Progress...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = formattedMinutes,
                            color = GoldAccent,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "Your submission (TrxID: $activeTrackingTrxId) has reached our server and is being verified. Please wait while PayHook confirms the payment.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Button(
                            onClick = onNavigateToInvoices,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text("View Generated Invoice", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ৩. এপ্রুভড ব্যানার
        if (verificationState == VerificationState.APPROVED_SUCCESS) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2618)),
                    border = BorderStroke(1.2.dp, SafeGreen)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("👑", fontSize = 42.sp)
                        Text(
                            text = "🎉 Congratulations! VIP Pass Activated!",
                            color = SafeGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Enjoy uninterrupted ad-free 1080p Ultra HD streaming and unlimited downloads across all titles!",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Watching Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ৪. রিজেক্টেড ব্যানার
        if (verificationState == VerificationState.DECLINED_ERROR) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E0F14)),
                    border = BorderStroke(1.2.dp, RejectRed)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("⚠️", fontSize = 32.sp)
                        Text(
                            text = "Payment Verification Failed!",
                            color = RejectRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rejectionReasonMessage,
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                trxId = ""
                                activeTrackingTrxId = ""
                                verificationState = VerificationState.INPUT_FORM
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RejectRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again with Correct Details", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ৫. মূল পেমেন্ট ও TrxID ইনপুট ফর্ম
        if (verificationState == VerificationState.INPUT_FORM) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCardBg),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(plan.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${plan.durationDays} Days VIP Pass", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("৳ ${plan.priceFormatted}", color = GoldAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("≈ $cryptoAmountInUsd USDT", color = SafeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = InputDarkBg,
                            border = BorderStroke(0.8.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTab == PaymentTypeTab.MFS_LOCAL) "Pay Amount: ৳${plan.priceFormatted}" else "Pay Amount: $cryptoAmountInUsd USDT",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Button(
                                    onClick = {
                                        val amtToCopy = if (selectedTab == PaymentTypeTab.MFS_LOCAL) plan.priceFormatted else cryptoAmountInUsd
                                        copyToClipboard(context, amtToCopy, "Amount")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Copy Amount", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (hasMfs && hasCrypto) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = InputDarkBg,
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTab == PaymentTypeTab.MFS_LOCAL) GoldAccent else Color.Transparent)
                                    .clickable { selectedTab = PaymentTypeTab.MFS_LOCAL }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🇧🇩 bKash / Nagad",
                                    color = if (selectedTab == PaymentTypeTab.MFS_LOCAL) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTab == PaymentTypeTab.CRYPTO_GLOBAL) GoldAccent else Color.Transparent)
                                    .clickable { selectedTab = PaymentTypeTab.CRYPTO_GLOBAL }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🌐 Crypto (${activeCryptoNetworks.size})",
                                    color = if (selectedTab == PaymentTypeTab.CRYPTO_GLOBAL) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // MFS লোকাল গেটওয়ে
            if (selectedTab == PaymentTypeTab.MFS_LOCAL && hasMfs) {
                item {
                    Text("Select Payment Gateway:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = activeGateways) { gw ->
                            val isSelected = selectedGateway?.id == gw.id
                            val gwColor = when (gw.id?.lowercase()) {
                                "bkash" -> Color(0xFFE2136E)
                                "nagad" -> Color(0xFFF7941D)
                                "rocket" -> Color(0xFF8C3494)
                                else -> Color(0xFF0284C7)
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) gwColor.copy(alpha = 0.2f) else DeepCardBg,
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) gwColor else CardBorderColor),
                                modifier = Modifier.clickable { selectedGateway = gw }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(getMfsLogoUrl(gw.id ?: "")),
                                        contentDescription = gw.effectiveName,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                    Text(gw.effectiveName, color = if (isSelected) gwColor else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    selectedGateway?.let { gw ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DeepCardBg),
                            border = BorderStroke(1.dp, CardBorderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${gw.effectiveName} (${gw.type})", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Send Money", color = SafeGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = InputDarkBg,
                                    border = BorderStroke(1.dp, CardBorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = gw.effectiveNumber,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Button(
                                            onClick = { copyToClipboard(context, gw.effectiveNumber, "${gw.effectiveName} Number") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Number", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "💡 Please Send Money ৳${plan.priceFormatted} to the above number, then enter your sender number and TrxID below.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            } 
            // ক্রিপ্টো নেটওয়ার্ক
            else if (selectedTab == PaymentTypeTab.CRYPTO_GLOBAL && hasCrypto) {
                item {
                    Text("Select Crypto Blockchain Network (${activeCryptoNetworks.size}):", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = activeCryptoNetworks) { net ->
                            val isNetSelected = selectedCryptoNetwork?.name == net.name
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNetSelected) GoldAccent.copy(alpha = 0.2f) else DeepCardBg,
                                border = BorderStroke(if (isNetSelected) 1.2.dp else 0.8.dp, if (isNetSelected) GoldAccent else CardBorderColor),
                                modifier = Modifier.clickable { selectedCryptoNetwork = net }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(getCryptoLogoUrl(net.name)),
                                        contentDescription = net.name,
                                        modifier = Modifier.size(16.dp).clip(CircleShape)
                                    )
                                    Text(net.name, color = if (isNetSelected) GoldAccent else Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    selectedCryptoNetwork?.let { net ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DeepCardBg),
                            border = BorderStroke(1.dp, CardBorderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Image(
                                            painter = rememberAsyncImagePainter(getCryptoLogoUrl(net.name)),
                                            contentDescription = net.name,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(net.name, color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Pay: $cryptoAmountInUsd USDT", color = SafeGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = InputDarkBg,
                                    border = BorderStroke(1.dp, CardBorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = net.address,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                                        )

                                        Button(
                                            onClick = { copyToClipboard(context, net.address, "${net.name} Address") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Copy Address", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "💡 Transfer $cryptoAmountInUsd USDT to this address and paste your TxHash below.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // ইনপুট ফিল্ডস
            if (hasMfs || hasCrypto) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepCardBg),
                        border = BorderStroke(1.dp, CardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Payment Verification Details", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = senderNumber,
                                onValueChange = { senderNumber = it },
                                label = { Text(if (selectedTab == PaymentTypeTab.MFS_LOCAL) "Your Sender Number" else "Sender Wallet Note", fontSize = 12.sp) },
                                placeholder = { Text(if (selectedTab == PaymentTypeTab.MFS_LOCAL) "01XXXXXXXXX" else "Optional identifier", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = if (selectedTab == PaymentTypeTab.MFS_LOCAL) KeyboardType.Phone else KeyboardType.Text),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = CardBorderColor,
                                    focusedContainerColor = InputDarkBg,
                                    unfocusedContainerColor = InputDarkBg,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = trxId,
                                onValueChange = { trxId = it },
                                label = { Text(if (selectedTab == PaymentTypeTab.MFS_LOCAL) "Transaction ID (TrxID) *" else "Transaction Hash (TxID) *", fontSize = 12.sp) },
                                placeholder = { Text("Ex: DIDSGOHX2D or 0x...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = CardBorderColor,
                                    focusedContainerColor = InputDarkBg,
                                    unfocusedContainerColor = InputDarkBg,
                                    focusedTextColor = GoldAccent,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }

                // =========================================================================
                // 🚀 ১০০% সার্ভার-অথরিটেটিভ সাবমিট বাটন
                // =========================================================================
                item {
                    Button(
                        onClick = {
                            val cleanTrx = trxId.trim()
                            val cleanSender = senderNumber.trim()

                            if (currentUserId <= 0) {
                                Toast.makeText(context, "অনুগ্রহ করে অ্যাপে লগইন করুন।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (cleanSender.length < 6) {
                                Toast.makeText(context, "সঠিক সেন্ডার মোবাইল নম্বর প্রদান করুন।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (cleanTrx.length < 4) {
                                Toast.makeText(context, "সঠিক Transaction ID (TrxID) প্রদান করুন।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val methodName = if (selectedTab == PaymentTypeTab.MFS_LOCAL) {
                                selectedGateway?.effectiveName ?: "bKash"
                            } else {
                                "Crypto (${selectedCryptoNetwork?.name ?: "USDT"})"
                            }

                            isSubmittingToServer = true

                            val requestPayload = SubscriptionSubmitRequest(
                                userId = currentUserId,
                                planId = plan.id,
                                paymentMethod = methodName,
                                senderNumber = cleanSender,
                                trxId = cleanTrx,
                                amount = plan.priceDouble,
                                planName = plan.name
                            )

                            // 🎯 সরাসরি সেন্ট্রাল রিপোজিটরি দিয়ে সার্ভারে পাঠানো হচ্ছে
                            scope.launch {
                                val result = repository.submitSubscription(requestPayload)
                                isSubmittingToServer = false

                                if (result.isSuccess) {
                                    val response = result.getOrNull()!!
                                    activeTrackingTrxId = cleanTrx

                                    // ✅ সার্ভার গ্রহণ করলেই কেবল লোকাল ইনভয়েস তৈরি হবে
                                    viewModel.createInstantInvoice(
                                        planName = plan.name,
                                        amount = plan.priceDouble,
                                        paymentMethod = methodName,
                                        senderNumber = cleanSender,
                                        trxId = cleanTrx
                                    )

                                    if (response.isAutoApproved) {
                                        // ১-সেকেন্ডে PayHook এপ্রুভ করলে
                                        verificationState = VerificationState.APPROVED_SUCCESS
                                        viewModel.updateInvoiceStatus(cleanTrx, "approved")
                                        viewModel.refreshVipStatusAndProfile()
                                    } else {
                                        // পেন্ডিং থাকলে টাইমার ও সার্ভার পোলিং শুরু হবে
                                        verificationState = VerificationState.COUNTDOWN_POLLING
                                    }

                                    Toast.makeText(context, response.message, Toast.LENGTH_LONG).show()
                                } else {
                                    // ❌ সার্ভার রিজেক্ট করলে আসল এরর মেসেজ দেখাবে এবং স্ক্রিনেই রেখে দেবে
                                    val errMessage = result.exceptionOrNull()?.message ?: "পেমেন্ট রিকোয়েস্ট ব্যর্থ হয়েছে।"
                                    Toast.makeText(context, "সার্ভার এরর: $errMessage", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSubmittingToServer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            disabledContainerColor = GoldAccent.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isSubmittingToServer) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp
                                )
                                Text("সার্ভারে যাচাই করা হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⚡", fontSize = 16.sp)
                                Text(
                                    text = "SUBMIT & ACTIVATE VIP",
                                    color = Color.Black,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard: $text", Toast.LENGTH_SHORT).show()
}
