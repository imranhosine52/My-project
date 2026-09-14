@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val GoldAccent = Color(0xFFFFB300)
private val VipDarkCardBg = Color(0xFF0F1522)
private val SafeGreen = Color(0xFF00D166)
private val InputDarkBg = Color(0xFF162032)
private val BorderDarkColor = Color(0xFF1E2536)

private enum class PaymentTypeTab {
    MFS_LOCAL,
    CRYPTO_GLOBAL
}

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
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    // 🎯 সক্রিয় গেটওয়ে ও ক্রিপ্টো ফিল্টার করা
    val activeGateways = remember(vipState.gateways) {
        vipState.gateways.filter { it.isActive && it.effectiveNumber.isNotBlank() }
    }
    val activeCryptoNetworks = remember(vipState.cryptoNetworks, vipState.cryptoEnabled) {
        if (vipState.cryptoEnabled) vipState.cryptoNetworks.filter { it.address.isNotBlank() } else emptyList()
    }

    val hasMfs = activeGateways.isNotEmpty()
    val hasCrypto = activeCryptoNetworks.isNotEmpty()

    // 🎯 সক্রিয় ক্যাটাগরি অনুযায়ী ডিফল্ট ট্যাব নির্ধারণ
    var selectedTab by remember(hasMfs, hasCrypto) {
        mutableStateOf(if (hasMfs) PaymentTypeTab.MFS_LOCAL else PaymentTypeTab.CRYPTO_GLOBAL)
    }
    
    var selectedGateway by remember { mutableStateOf<GatewayItemDto?>(null) }
    var selectedCryptoNetwork by remember { mutableStateOf<CryptoNetworkDto?>(null) }

    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // পেজে ঢুকলেই লেটেস্ট গেটওয়ে ডাটা সার্ভার থেকে রিফ্রেশ করা
    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
    }

    // গেটওয়ে সিলেকশন অটো-সিঙ্ক
    LaunchedEffect(activeGateways, activeCryptoNetworks) {
        if (selectedGateway == null || !activeGateways.contains(selectedGateway)) {
            selectedGateway = activeGateways.firstOrNull()
        }
        if (selectedCryptoNetwork == null || !activeCryptoNetworks.contains(selectedCryptoNetwork)) {
            selectedCryptoNetwork = activeCryptoNetworks.firstOrNull()
        }
    }

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

        // 🎟️ সিলেক্টেড প্ল্যান সামারি
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

        // 🧭 পেমেন্ট মেথড ক্যাটাগরি সুইচ (যদি উভয় ক্যাটাগরিই চালু থাকে)
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

        // 💳 ১. বাংলাদেশি সক্রিয় গেটওয়েসমূহ (bKash/Nagad/Rocket)
        if (selectedTab == PaymentTypeTab.MFS_LOCAL && hasMfs) {
            item {
                Text("Select Active Payment Gateway:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeGateways) { gw ->
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

            // গেটওয়ে নাম্বার ও কপি কার্ড
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
                                text = "💡 উপরের নাম্বারে ৳${plan.priceFormatted} Send Money করে নিচে আপনার প্রেরক নাম্বার ও TrxID প্রদান করুন।",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        } 
        // 🌐 ২. ক্রিপ্টো সক্রিয় নেটওয়ার্কসমূহ (শুধুমাত্র অন থাকা কয়েনগুলো দেখাবে)
        else if (selectedTab == PaymentTypeTab.CRYPTO_GLOBAL && hasCrypto) {
            item {
                Text("Select Active Crypto Network (${activeCryptoNetworks.size}):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activeCryptoNetworks) { net ->
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

            // ক্রিপ্টো অ্যাড্রেস কার্ড
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
                                text = "💡 Send equivalent USDT to this address and submit your TxID / Hash below.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        } else {
            // কোনো গেটওয়ে চালু না থাকলে নোটিশ
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

        // ✍️ ৩. ইনপুট ফর্ম (Sender Number & TrxID)
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

            // 🚀 ৪. সাবমিট বাটন
            item {
                Button(
                    onClick = {
                        val cleanTrx = trxId.trim()
                        val cleanSender = if (senderNumber.isBlank()) "01XXXXXXXXX" else senderNumber.trim()
                        val userId = authState.user?.id ?: 0

                        if (cleanTrx.length < 5) {
                            Toast.makeText(context, "অনুগ্রহ করে সঠিক TrxID / TxHash প্রদান করুন।", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val methodName = if (selectedTab == PaymentTypeTab.MFS_LOCAL) {
                            selectedGateway?.effectiveName ?: "bKash"
                        } else {
                            "Crypto (${selectedCryptoNetwork?.name ?: "USDT"})"
                        }

                        val request = SubscriptionSubmitRequest(
                            userId = userId,
                            userName = authState.user?.name,
                            userEmail = authState.user?.email,
                            userPhone = authState.user?.phone,
                            planId = plan.id,
                            planName = plan.name,
                            paymentMethod = methodName,
                            cryptoNetwork = selectedCryptoNetwork?.name,
                            trxId = cleanTrx,
                            senderNumber = cleanSender,
                            amount = plan.priceDouble
                        )

                        isSubmitting = true
                        viewModel.submitVipPayment(
                            request = request,
                            onSuccess = { res ->
                                isSubmitting = false
                                if (res.isAutoApproved) {
                                    Toast.makeText(context, "🎉 অভিনন্দন! আপনার VIP পাস সক্রিয় করা হয়েছে!", Toast.LENGTH_LONG).show()
                                    viewModel.refreshVipStatusAndProfile()
                                    onBackClick()
                                } else {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    onNavigateToInvoices()
                                }
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
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
