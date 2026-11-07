@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SubscriptionPlanDto
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.PromoCodeDialog
import com.example.ui.viewmodel.DramaFlixViewModel

// 🎨 স্ক্রিনশটের হুবহু কালার প্যালেট
private val CosmicDarkBg = Color(0xFF090A10)
private val GoldBorder = Color(0xFFD4A359)
private val BlueCardBorder = Color(0xFF4B6496)

/**
 * 👑 হেক্সাগন ব্যাজ (স্ক্রিনশটের মতো গোল্ডেন ও সিলভার ব্যাজ)
 */
@Composable
private fun HexagonBadgeIcon(
    isGold: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val hexPath = Path().apply {
                moveTo(w * 0.50f, 0f)
                lineTo(w, h * 0.25f)
                lineTo(w, h * 0.75f)
                lineTo(w * 0.50f, h)
                lineTo(0f, h * 0.75f)
                lineTo(0f, h * 0.25f)
                close()
            }

            // ব্যাকগ্রাউন্ড গ্রেডিয়েন্ট
            drawPath(
                path = hexPath,
                brush = if (isGold) {
                    Brush.verticalGradient(listOf(Color(0xFFF9D976), Color(0xFFE99C24)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF8EA5D8), Color(0xFF41568A)))
                },
                style = Fill
            )

            // বর্ডার স্ট্রোক
            drawPath(
                path = hexPath,
                color = if (isGold) Color(0xFFFFEFA7) else Color(0xFFD2E0FF),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        Icon(
            imageVector = if (isGold) Icons.Default.WorkspacePremium else Icons.Default.Shield,
            contentDescription = null,
            tint = if (isGold) Color(0xFF382305) else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun VipScreen(
    viewModel: DramaFlixViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()

    val isLoggedIn = authState.isLoggedIn
    val userProfile = authState.userProfile
    val isVip = vipState.isVip

    var showAuthModal by remember { mutableStateOf(false) }
    var showPromoModal by remember { mutableStateOf(false) }

    // 🎯 ওয়েবভিউ চেকআউট ডায়ালগ স্টেট
    var checkoutTargetUrl by remember { mutableStateOf<String?>(null) }

    val rawPlans = vipState.plans
    val effectivePlans = remember(rawPlans) {
        if (rawPlans.isNotEmpty()) rawPlans else listOf(
            SubscriptionPlanDto(rawId = 1, name = "1 Month", rawPrice = "59.00", rawOriginalPrice = "89.00", durationDays = 30),
            SubscriptionPlanDto(rawId = 2, name = "3 Months", rawPrice = "150.00", rawOriginalPrice = "225.00", durationDays = 90, isPopular = true),
            SubscriptionPlanDto(rawId = 3, name = "12 Months", rawPrice = "499.00", rawOriginalPrice = "899.00", durationDays = 365)
        )
    }

    var selectedGoldPlan by remember(effectivePlans) {
        mutableStateOf(effectivePlans.find { it.isPopular } ?: effectivePlans.firstOrNull() ?: effectivePlans[0])
    }
    var selectedBluePlan by remember(effectivePlans) {
        mutableStateOf(effectivePlans.firstOrNull() ?: effectivePlans[0])
    }

    // 🔒 সাবস্ক্রিপশন কেনার অ্যাকশন হ্যান্ডলার (Auth Guard সহ)
    fun initiateSubscriptionCheckout(plan: SubscriptionPlanDto) {
        if (!isLoggedIn) {
            Toast.makeText(context, "সাবস্ক্রিপশন কিনতে অনুগ্রহ করে প্রথমে লগইন করুন।", Toast.LENGTH_SHORT).show()
            showAuthModal = true
            return
        }

        val uid = userProfile?.id?.filter { it.isDigit() }?.ifBlank { "0" } ?: "0"
        val planId = plan.id
        checkoutTargetUrl = "https://playdramaflix.com/app/vip/index.php?user_id=$uid&plan_id=$planId"
    }

    BackHandler {
        if (checkoutTargetUrl != null) {
            checkoutTargetUrl = null
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // =============================================================
            // 🔝 ১. টপ বার: ব্যাক বাটন + লগইন স্ট্যাটাস পিল
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ইউজার স্ট্যাটাস পিল (Log in > অথবা ইউজারের নাম ও প্ল্যান)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1B1E2A),
                    border = BorderStroke(0.8.dp, Color(0xFF2E354A)),
                    modifier = Modifier.clickable {
                        if (!isLoggedIn) showAuthModal = true else onNavigateToProfile()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isLoggedIn) (userProfile?.displayName ?: "Member") else "Log in",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isVip) "• VIP Plan" else "• Free Plan",
                            color = if (isVip) Color(0xFFFFB300) else Color(0xFF8E95A5),
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // =============================================================
            // 📜 স্ক্রোলযোগ্য প্ল্যান কনটেন্ট
            // =============================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // =============================================================
                // 👑 ২. স্ক্রিনশটের ১ম কার্ড: রয়্যাল গোল্ড ভিআইপি কার্ড (PRO VIP)
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF2A1C08),
                    border = BorderStroke(1.2.dp, GoldBorder.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF473111),
                                        Color(0xFF261906),
                                        Color(0xFF1A1104)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // কার্ড হেডার
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Royal VIP Pass",
                                        color = Color(0xFFFFDF88),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Max 2 Devices • 1080P Full HD • Zero Ads",
                                        color = Color(0xFFE8C68C),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HexagonBadgeIcon(isGold = true)
                            }

                            // প্যাকেজ সিলেকশন রো
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                effectivePlans.take(3).forEach { plan ->
                                    val isSelected = (selectedGoldPlan.id == plan.id)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Color(0xFF593C10) else Color(0xFF2B1D08))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                                color = if (isSelected) Color(0xFFFFDF88) else Color(0xFF6B4B1B),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedGoldPlan = plan }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = plan.name,
                                                color = if (isSelected) Color.White else Color(0xFFE0C49B),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "৳${plan.priceFormatted}",
                                                color = Color(0xFFFFDF88),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = "৳${plan.originalPriceFormatted}",
                                                color = Color(0xFF9E8159),
                                                fontSize = 10.sp,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        }
                                    }
                                }
                            }

                            // 🌟 গোল্ডেন [ Pay Now ] বাটন
                            Button(
                                onClick = { initiateSubscriptionCheckout(selectedGoldPlan) },
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFFDF88), Color(0xFFF3BF58))
                                        )
                                    )
                            ) {
                                Text(
                                    text = "Pay Now",
                                    color = Color(0xFF221703),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 🥈 ৩. স্ক্রিনশটের ২য় কার্ড: সিলভার/ব্লু প্রিমিয়াম কার্ড
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF131726),
                    border = BorderStroke(1.2.dp, BlueCardBorder.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF242E4A),
                                        Color(0xFF151B2E),
                                        Color(0xFF0D121F)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // কার্ড হেডার
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Standard Pass",
                                        color = Color(0xFFD7E2FF),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "High-speed streaming • All episodes unlocked",
                                        color = Color(0xFFA5B7DE),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HexagonBadgeIcon(isGold = false)
                            }

                            // প্যাকেজ সিলেকশন রো
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                effectivePlans.take(3).forEach { plan ->
                                    val isSelected = (selectedBluePlan.id == plan.id)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Color(0xFF2B3A60) else Color(0xFF171E30))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                                color = if (isSelected) Color(0xFF8EA5D8) else Color(0xFF2C3955),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedBluePlan = plan }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = plan.name,
                                                color = if (isSelected) Color.White else Color(0xFFB8C7E6),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "৳${plan.priceFormatted}",
                                                color = Color(0xFFD7E2FF),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = "৳${plan.originalPriceFormatted}",
                                                color = Color(0xFF6C7C9E),
                                                fontSize = 10.sp,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        }
                                    }
                                }
                            }

                            // 🌟 সিলভার/ব্লু [ Pay Now ] বাটন
                            Button(
                                onClick = { initiateSubscriptionCheckout(selectedBluePlan) },
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFE2ECFF), Color(0xFFBDD2FC))
                                        )
                                    )
                            ) {
                                Text(
                                    text = "Pay Now",
                                    color = Color(0xFF141E33),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // 🎁 প্রমো কোড ক্লেইম কুইক লিঙ্ক
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141824))
                        .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(12.dp))
                        .clickable { showPromoModal = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Text("Have a VIP Promo Code?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Redeem ➔", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // =============================================================
                // 📊 ৪. স্ক্রিনশটের হুবহু ফিচার কম্প্যারিজন টেবিল (Features Table)
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // টেবিল হেডার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Features",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.8f)
                        )
                        Row(
                            modifier = Modifier.weight(1.2f),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Text("Free", color = Color(0xFF8E95A5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Pro VIP", color = Color(0xFFFFDF88), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1F2636), thickness = 0.8.dp)

                    // টেবিলের রোগুলো
                    FeatureTableRow(title = "Simultaneous Devices", freeValue = "1", vipValue = "2")
                    FeatureTableRow(title = "Zero Ads (100% Ad-Free)", freeCheck = false, vipCheck = true)
                    FeatureTableRow(title = "1080P Full HD Streaming", freeCheck = false, vipCheck = true)
                    FeatureTableRow(title = "High-Speed Video Downloads", freeCheck = false, vipCheck = true)
                    FeatureTableRow(title = "Unlock All Dubbed Episodes", freeCheck = false, vipCheck = true)
                    FeatureTableRow(title = "Exclusive Early Releases", freeCheck = false, vipCheck = true)
                    FeatureTableRow(title = "Auto-Approval Fast Activation", freeCheck = false, vipCheck = true)
                }
            }
        }

        // =============================================================
        // 💳 ৫. চেকআউট ওয়েবভিউ ডায়ালগ (শুধুমাত্র Pay Now চাপলে লোড হবে)
        // =============================================================
        if (checkoutTargetUrl != null) {
            CheckoutWebViewModal(
                checkoutUrl = checkoutTargetUrl!!,
                onDismiss = { checkoutTargetUrl = null },
                onSuccess = {
                    checkoutTargetUrl = null
                    viewModel.refreshVipStatusAndProfile()
                    Toast.makeText(context, "🎉 VIP Activated Successfully!", Toast.LENGTH_LONG).show()
                    onNavigateBack()
                }
            )
        }

        // অথ ডায়ালগ (লগইন না থাকলে)
        if (showAuthModal) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthModal = false }
            )
        }

        // প্রমো কোড ডায়ালগ
        if (showPromoModal) {
            PromoCodeDialog(
                viewModel = viewModel,
                onDismiss = { showPromoModal = false }
            )
        }
    }
}

/**
 * 📋 টেবিল রো কম্পোনেন্ট
 */
@Composable
private fun FeatureTableRow(
    title: String,
    freeValue: String? = null,
    vipValue: String? = null,
    freeCheck: Boolean? = null,
    vipCheck: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFFCCD3E0),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.8f)
        )

        Row(
            modifier = Modifier.weight(1.2f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Free Column
            if (freeValue != null) {
                Text(freeValue, color = Color(0xFF8E95A5), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            } else if (freeCheck == true) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(15.dp))
            } else {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF555F73), modifier = Modifier.size(15.dp))
            }

            // VIP Column
            if (vipValue != null) {
                Text(vipValue, color = Color(0xFFFFDF88), fontSize = 13.sp, fontWeight = FontWeight.Black)
            } else if (vipCheck == true) {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                }
            } else {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * 🌐 শুধুমাত্র চেকআউটের জন্য সুরক্ষিত ওয়েবভিউ ডায়ালগ
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CheckoutWebViewModal(
    checkoutUrl: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color(0xFF06080E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Secure VIP Checkout",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            },
            containerColor = Color(0xFF06080E)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.startsWith("playdramaflix://vip-activated") || url.startsWith("playdramaflix://payment-success")) {
                                        onSuccess()
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(checkoutUrl)
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        color = Color(0xFFFFB300),
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
