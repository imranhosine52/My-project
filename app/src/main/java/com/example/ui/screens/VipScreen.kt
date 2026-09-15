@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// 🎨 প্রিমিয়াম সিনেমাটিক কালার প্যালেট
private val PureBlackBg = Color(0xFF06080E)
private val DeepCardBg = Color(0xFF111520)
private val CardBorderColor = Color(0xFF1E2536)
private val GoldAccent = Color(0xFFFFB300)
private val SafeGreen = Color(0xFF00D166)
private val RejectRed = Color(0xFFFF3B30)

private data class FaqItem(val question: String, val answer: String)

// 📥 সম্পূর্ণ ইংরেজিতে FAQ তালিকা
private val faqList = listOf(
    FaqItem(
        "What is the video download policy?",
        "• Free Users: Download up to 2 GB per day at standard speed.\n• VIP Members: Enjoy 100% unlimited high-speed downloads in ultra crystal-clear 1080p with zero daily limits!"
    ),
    FaqItem(
        "How fast is VIP activation after payment?",
        "Instant Automated Activation: bKash, Nagad, and Crypto TrxID matching takes only 1-2 seconds to activate your account automatically! Manual reviews take 5-15 minutes max."
    ),
    FaqItem(
        "Can I pay with Cryptocurrencies (USDT/Crypto)?",
        "Yes! We accept payments across 19+ blockchain networks including Binance Pay, USDT (TRC20, BEP20), TON, Solana, Polygon, and more."
    ),
    FaqItem(
        "Are all movies and dramas 100% ad-free?",
        "Absolutely! VIP members experience 100% ad-free streaming in 1080p Ultra Full HD across all exclusive web series, dramas, and blockbuster movies."
    )
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

    var tutorialVideoUrl by remember { mutableStateOf("https://playdramaflix.com/downloads/how-to-buy-vip.mp4") }

    // 🎯 ১০০% সার্ভার ও লগইন নির্ভর রিয়াল ভিআইপি ও পেন্ডিং স্ট্যাটাস
    val isUserCurrentlyVip = remember(authState.isLoggedIn, authState.isVip, vipState.isVip) {
        authState.isLoggedIn && (authState.isVip || vipState.isVip)
    }

    val hasPendingPayment = remember(authState.isLoggedIn, isUserCurrentlyVip, vipState.invoiceHistory) {
        authState.isLoggedIn && !isUserCurrentlyVip && vipState.invoiceHistory.any { 
            it.status.equals("pending", ignoreCase = true) 
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadVipSubscriptionPlans()
        viewModel.refreshVipStatusAndProfile()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://playdramaflix.com/api/v1/subscription/plans")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == 200) {
                    val res = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    val json = JSONObject(res)
                    val fetchedVideo = json.optString("vip_tutorial_video_url", "")
                    if (fetchedVideo.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            tutorialVideoUrl = fetchedVideo
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            .background(PureBlackBg)
    ) {
        when (currentMode) {
            VipScreenMode.PRICING -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // =========================================================================
                    // 🎬 ১. শীর্ষে ফুল-উইডথ হিরো ভিডিও প্লেয়ার
                    // =========================================================================
                    item {
                        FullWidthEdgeAutoplayBanner(
                            videoUrl = tutorialVideoUrl,
                            onBackClick = onNavigateBack,
                            onInvoicesClick = { currentMode = VipScreenMode.INVOICES },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // =========================================================================
                    // ℹ️ ২. বডি কনটেন্ট (স্ট্যাটাস ব্যানার)
                    // =========================================================================
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // VIP Active Alert
                            AnimatedVisibility(
                                visible = isUserCurrentlyVip,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF0F2618),
                                    border = BorderStroke(1.2.dp, SafeGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("👑", fontSize = 24.sp)
                                        Column {
                                            Text(
                                                text = "Your VIP Subscription is Active!",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "You already have full access. New plans can be purchased to extend your expiry date.",
                                                color = SafeGreen,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Payment Pending Alert
                            AnimatedVisibility(
                                visible = hasPendingPayment,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF332305),
                                    border = BorderStroke(1.2.dp, GoldAccent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("⏳", fontSize = 22.sp)
                                        Column {
                                            Text(
                                                text = "Payment Verification Pending!",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Please wait while our automated engine confirms your TrxID submission.",
                                                color = GoldAccent,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // হেডার টাইটেল
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Upgrade to Ad-Free Ultra HD",
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Stream all movies, web series, and Asian dramas in 1080p with zero ads.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // প্ল্যান কার্ডস
                    val plans = vipState.plans.ifEmpty {
                        listOf(
                            SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59", rawOriginalPrice = "88.50", durationDays = 30, isPopular = true),
                            SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150", rawOriginalPrice = "200.00", durationDays = 90, isPopular = false)
                        )
                    }

                    items(plans) { plan ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            VipPricingPlanCard(
                                plan = plan,
                                isUserCurrentlyVip = isUserCurrentlyVip,
                                hasPendingPayment = hasPendingPayment,
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
                    }

                    // 1-Sec Instant Badge
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF082618),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⚡", fontSize = 13.sp)
                                Text(
                                    text = "1-Sec Automated Instant Activation Engine",
                                    color = SafeGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ❓ FAQ সেকশন
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            FaqSection()
                        }
                    }
                }
            }

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

            // 🎯 রিয়েল-টাইম সিঙ্কড ইনভয়েস মোড
            VipScreenMode.INVOICES -> {
                VipInvoicesScreen(
                    viewModel = viewModel,
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

// =============================================================================
// 🎬 ফুল-উইডথ হিরো ব্যানার
// =============================================================================
@Composable
private fun FullWidthEdgeAutoplayBanner(
    videoUrl: String,
    onBackClick: () -> Unit,
    onInvoicesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
                mediaPlayerRef = null
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10.5f)
            .background(PureBlackBg)
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(videoUrl))
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        mp.isLooping = true
                        mp.setVolume(1f, 1f)
                        start()
                    }
                }
            },
            update = { view ->
                if (videoUrl.isNotBlank()) {
                    view.setVideoURI(Uri.parse(videoUrl))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
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

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable { onInvoicesClick() }
            ) {
                Text(
                    text = "Invoices",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, PureBlackBg)
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(0.8.dp, GoldAccent.copy(alpha = 0.7f), CircleShape)
                .clickable {
                    isMuted = !isMuted
                    val vol = if (isMuted) 0f else 1f
                    try {
                        mediaPlayerRef?.setVolume(vol, vol)
                    } catch (e: Exception) {}
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute Toggle",
                tint = GoldAccent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// 💳 VIP প্ল্যান প্রাইসিং কার্ড (Surface ব্যবহার করা হয়েছে যাতে border সাপোর্ট করে)
@Composable
private fun VipPricingPlanCard(
    plan: SubscriptionPlanDto,
    isUserCurrentlyVip: Boolean,
    hasPendingPayment: Boolean,
    onBuyNowClick: () -> Unit
) {
    val isMostPopular = plan.isPopular || plan.durationDays == 30

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isMostPopular) 8.dp else 0.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DeepCardBg,
            border = BorderStroke(
                width = if (isMostPopular) 1.5.dp else 0.8.dp,
                color = if (isMostPopular) GoldAccent else CardBorderColor
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
                        Text(
                            text = "৳ ${plan.originalPriceFormatted}",
                            color = Color(0xFF64748B),
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

                HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                            Text(feature, color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        Text(
                            text = if (isUserCurrentlyVip) "RENEW / EXTEND VIP PASS" else "BUY VIP PASS NOW",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
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

// ❓ FAQ সেকশন
@Composable
private fun FaqSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("❓", fontSize = 15.sp)
            Text(
                text = "Frequently Asked Questions",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        faqList.forEach { faq ->
            var isExpanded by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                shape = RoundedCornerShape(12.dp),
                color = DeepCardBg,
                border = BorderStroke(0.8.dp, CardBorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = faq.question,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = faq.answer,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 🧾 রিয়েল-টাইম ইনভয়েস স্ক্রিন (Surface ব্যবহার করা হয়েছে)
// =============================================================================
@Composable
private fun VipInvoicesScreen(
    viewModel: DramaFlixViewModel,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 🚀 পেজে ঢোকামাত্রই সাথে সাথে সার্ভার থেকে ফ্রেশ ডাটা আনা হবে
    LaunchedEffect(Unit) {
        isRefreshing = true
        viewModel.refreshVipStatusAndProfile()
        delay(400)
        isRefreshing = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlackBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // হেডার বার
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                Text("Invoices & Subscriptions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // ছোট রিফ্রেশ বাটন
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.refreshVipStatusAndProfile()
                        delay(500)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldAccent, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 🔄 পুল-টু-রিফ্রেশ কন্টেইনার
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshVipStatusAndProfile()
                    delay(500)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isRefreshing && vipState.invoiceHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldAccent, strokeWidth = 2.5.dp)
                }
            } else if (vipState.invoiceHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No payment submissions found on server.", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = vipState.invoiceHistory,
                        key = { inv -> inv.trxId.ifBlank { inv.id } }
                    ) { inv ->
                        val st = inv.status.lowercase()
                        val isApproved = st == "active" || st == "approved"
                        val isRejected = st == "rejected" || st == "declined" || st == "failed"

                        val statusColor = when {
                            isApproved -> SafeGreen           // 🟢 Approved
                            isRejected -> RejectRed           // 🔴 Rejected
                            else -> GoldAccent                // 🟡 Pending
                        }

                        val statusText = when {
                            isApproved -> "APPROVED ✅"
                            isRejected -> "REJECTED ❌"
                            else -> "PENDING ⏳"
                        }

                        // 🎯 এখানে Surface ব্যবহার করা হয়েছে যাতে বর্ডারের এরর না আসে
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DeepCardBg,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(inv.planName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(inv.displayAmount, color = GoldAccent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                }

                                Text(
                                    text = "Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Date: ${inv.displayDate}", color = Color(0xFF64748B), fontSize = 11.5.sp)
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontSize = 12.sp,
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
}
