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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private val GoldAccent = Color(0xFFFFB300)
private val VipDarkCardBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val InputDarkBg = Color(0xFF162032)
private val BorderDarkColor = Color(0xFF1E2536)

private enum class PaymentTypeTab {
    MFS_LOCAL,     // bKash, Nagad, Rocket, Upay, Bank
    CRYPTO_GLOBAL  // 19 Multi-Chain Crypto Networks
}

// 🪙 ডিফল্ট ১৯টি ক্রিপ্টো ওয়ালেট তালিকা
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
    BackHandler { onBackClick() }

    // 🎯 ডাইনামিক লাইভ গেটওয়ে ও ক্রিপ্টো স্টেট
    var activeGateways by remember { mutableStateOf(DefaultGatewaysList) }
    var activeCryptoNetworks by remember { mutableStateOf(DefaultCryptoList) }
    var isCryptoGloballyEnabled by remember { mutableStateOf(true) }

    var selectedTab by remember { mutableStateOf(PaymentTypeTab.MFS_LOCAL) }
    var selectedGateway by remember { mutableStateOf<GatewayItemDto?>(DefaultGatewaysList.first()) }
    var selectedCryptoNetwork by remember { mutableStateOf<CryptoNetworkDto?>(DefaultCryptoList.first()) }

    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // 📡 পেজে ঢোকার সাথে সাথে ব্যাকএন্ড থেকে তাজা গেটওয়ে ডাটা ফেচ করা (অ্যাডমিন যা বন্ধ করবে তা সাথে সাথে হাইড হবে)
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://playdramaflix.com/api/v1/subscription/plans")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    if (json.optBoolean("success", false)) {
                        // গেটওয়ে পার্সিং
                        val gwArray = json.optJSONArray("payment_gateways") ?: JSONArray()
                        val fetchedGw = mutableListOf<GatewayItemDto>()
                        for (i in 0 until gwArray.length()) {
                            val g = gwArray.getJSONObject(i)
                            fetchedGw.add(
                                GatewayItemDto(
                                    id = g.optString("id"),
                                    name = g.optString("name"),
                                    number = g.optString("number"),
                                    type = g.optString("type", "Personal"),
                                    instructions = g.optString("instructions")
                                )
                            )
                        }

                        // ক্রিপ্টো পার্সিং
                        val isCryptoOn = json.optBoolean("crypto_enabled", true)
                        val netArray = json.optJSONArray("crypto_networks") ?: JSONArray()
                        val fetchedNet = mutableListOf<CryptoNetworkDto>()
                        for (i in 0 until netArray.length()) {
                            val n = netArray.getJSONObject(i)
                            fetchedNet.add(
                                CryptoNetworkDto(
                                    rawId = n.optInt("id", i + 1),
                                    name = n.optString("name"),
                                    address = n.optString("address"),
                                    symbol = n.optString("symbol", "USDT")
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            if (fetchedGw.isNotEmpty()) {
                                activeGateways = fetchedGw
                                selectedGateway = fetchedGw.first()
                            }
                            isCryptoGloballyEnabled = isCryptoOn
                            if (fetchedNet.isNotEmpty()) {
                                activeCryptoNetworks = fetchedNet
                                selectedCryptoNetwork = fetchedNet.first()
                            }
                            if (activeGateways.isEmpty() && isCryptoGloballyEnabled) {
                                selectedTab = PaymentTypeTab.CRYPTO_GLOBAL
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val hasMfs = activeGateways.isNotEmpty()
    val hasCrypto = isCryptoGloballyEnabled && activeCryptoNetworks.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 🔝 হেডার
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
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "VIP Checkout",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Invoices",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToInvoices() }
                )
            }
        }

        // 🎟️ সিলেক্টেড প্ল্যান সামারি কার্ড
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(plan.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${plan.durationDays} Days Access • 1080p Ultra HD", color = TextSecondary, fontSize = 11.5.sp)
                    }
                    Text("৳ ${plan.priceFormatted}", color = GoldAccent, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // 🧭 ৪. ক্যাটাগরি সুইচ (MFS vs Crypto)
        if (hasMfs && hasCrypto) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InputDarkBg,
                    border = BorderStroke(1.dp, BorderDarkColor),
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

        // 💳 ৫. বাংলাদেশি সক্রিয় গেটওয়েসমূহ (bKash/Nagad/Rocket)
        if (selectedTab == PaymentTypeTab.MFS_LOCAL && hasMfs) {
            item {
                Text("Select Payment Gateway:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

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
                            color = if (isSelected) gwColor.copy(alpha = 0.2f) else VipDarkCardBg,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) gwColor else BorderDarkColor),
                            modifier = Modifier.clickable { selectedGateway = gw }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(gw.effectiveName, color = if (isSelected) gwColor else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // গেটওয়ে নাম্বার ও ১-ক্লিক কপি কার্ড
            item {
                selectedGateway?.let { gw ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                        border = BorderStroke(1.dp, BorderDarkColor)
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
                                border = BorderStroke(1.dp, BorderDarkColor),
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
                                        Text("Copy", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                text = "💡 উপরের নাম্বারে ৳${plan.priceFormatted} Send Money করে নিচে আপনার প্রেরক মোবাইল নাম্বার ও TrxID প্রদান করুন।",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
        // 🌐 ৬. ক্রিপ্টো সক্রিয় নেটওয়ার্কসমূহ
        else if (selectedTab == PaymentTypeTab.CRYPTO_GLOBAL && hasCrypto) {
            item {
                Text("Select Blockchain Network (${activeCryptoNetworks.size}):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items = activeCryptoNetworks) { net ->
                        val isNetSelected = selectedCryptoNetwork?.name == net.name
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isNetSelected) GoldAccent.copy(alpha = 0.2f) else VipDarkCardBg,
                            border = BorderStroke(if (isNetSelected) 1.2.dp else 0.8.dp, if (isNetSelected) GoldAccent else BorderDarkColor),
                            modifier = Modifier.clickable { selectedCryptoNetwork = net }
                        ) {
                            Text(
                                text = net.name,
                                color = if (isNetSelected) GoldAccent else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // ক্রিপ্টো ডিপোজিট অ্যাড্রেস কার্ড
            item {
                selectedCryptoNetwork?.let { net ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                        border = BorderStroke(1.dp, BorderDarkColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(net.name, color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Deposit Address", color = SafeGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = InputDarkBg,
                                border = BorderStroke(1.dp, BorderDarkColor),
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
                                        Text("Copy", color = GoldAccent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                text = "💡 Send equivalent USDT to this address and submit your Transaction Hash (TxID) below.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                    border = BorderStroke(1.dp, BorderDarkColor)
                ) {
                    Text(
                        text = "পেমেন্ট গেটওয়েগুলো বর্তমানে আপগ্রেড করা হচ্ছে। অনুগ্রহ করে কিছুক্ষণ পর চেষ্টা করুন।",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    )
                }
            }
        }

        // ✍️ ৭. ইনপুট ফর্ম (Sender Number & TrxID)
        if (hasMfs || hasCrypto) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = VipDarkCardBg),
                    border = BorderStroke(1.dp, BorderDarkColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Payment Verification Details", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = senderNumber,
                            onValueChange = { senderNumber = it },
                            label = { Text(if (selectedTab == PaymentTypeTab.MFS_LOCAL) "Your bKash/Nagad Number" else "Sender Wallet / Note", fontSize = 12.sp) },
                            placeholder = { Text(if (selectedTab == PaymentTypeTab.MFS_LOCAL) "017XXXXXXXX" else "Optional sender identifier", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedTab == PaymentTypeTab.MFS_LOCAL) KeyboardType.Phone else KeyboardType.Text),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = BorderDarkColor,
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
                                unfocusedBorderColor = BorderDarkColor,
                                focusedContainerColor = InputDarkBg,
                                unfocusedContainerColor = InputDarkBg,
                                focusedTextColor = GoldAccent,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            // 🚀 ৮. সাবমিট বাটন (সরাসরি API কলিং ও ১-সেকেন্ড অটো-ভেরিফিকেশন)
            item {
                Button(
                    onClick = {
                        val cleanTrx = trxId.trim()
                        val cleanSender = if (senderNumber.isBlank()) "01XXXXXXXXX" else senderNumber.trim()

                        if (cleanTrx.length < 5) {
                            Toast.makeText(context, "অনুগ্রহ করে সঠিক TrxID / TxHash প্রদান করুন।", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val methodName = if (selectedTab == PaymentTypeTab.MFS_LOCAL) {
                            selectedGateway?.effectiveName ?: "bKash"
                        } else {
                            "Crypto (${selectedCryptoNetwork?.name ?: "USDT"})"
                        }

                        isSubmitting = true

                        // ⚡ সরাসরি সার্ভার এন্ডপয়েন্টে পোস্ট ও অটো-ম্যাচিং চেক
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val url = URL("https://playdramaflix.com/api/v1/subscription/submit")
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.setRequestProperty("Content-Type", "application/json")
                                conn.doOutput = true
                                conn.connectTimeout = 15000
                                conn.readTimeout = 15000

                                val body = JSONObject().apply {
                                    put("user_id", 1)
                                    put("plan_id", plan.id)
                                    put("payment_method", methodName)
                                    put("sender_number", cleanSender)
                                    put("trx_id", cleanTrx)
                                    put("amount", plan.priceDouble)
                                }

                                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                                val reader = BufferedReader(InputStreamReader(if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream))
                                val responseText = reader.readText()
                                reader.close()

                                val resJson = JSONObject(responseText)
                                val isSuccess = resJson.optBoolean("success", false)
                                val isAutoApproved = resJson.optBoolean("auto_approved", false) || resJson.optBoolean("is_vip", false)
                                val msg = resJson.optString("message", "Payment Submitted")

                                withContext(Dispatchers.Main) {
                                    isSubmitting = false
                                    if (isSuccess) {
                                        if (isAutoApproved) {
                                            Toast.makeText(context, "🎉 অভিনন্দন! আপনার VIP পাস সক্রিয় করা হয়েছে!", Toast.LENGTH_LONG).show()
                                            viewModel.refreshVipStatusAndProfile()
                                            onBackClick()
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            onNavigateToInvoices()
                                        }
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isSubmitting = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, disabledContainerColor = Color(0xFF2A2A2A))
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying Payment...", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Text("SUBMIT & ACTIVATE VIP", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
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
    Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
}
