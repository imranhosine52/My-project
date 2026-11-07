@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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

// 🎨 Cosmic Luxury Color Palette
private val CosmicDarkBg = Color(0xFF07080E)
private val GoldBorder = Color(0xFFE5B567)
private val BlueCardBorder = Color(0xFF4B6496)

/**
 * 👑 Hexagon Badge Icon (Gold for Pro, Silver/Blue for Standard)
 */
@Composable
private fun HexagonBadgeIcon(
    isGold: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(44.dp),
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

            drawPath(
                path = hexPath,
                brush = if (isGold) {
                    Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFFA000)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF9CB8FF), Color(0xFF41568A)))
                },
                style = Fill
            )

            drawPath(
                path = hexPath,
                color = if (isGold) Color(0xFFFFF9C4) else Color(0xFFE3EDFF),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        Icon(
            imageVector = if (isGold) Icons.Default.WorkspacePremium else Icons.Default.Shield,
            contentDescription = null,
            tint = if (isGold) Color(0xFF2C1902) else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

data class FaqItem(
    val question: String,
    val answer: String
)

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
    var checkoutTargetUrl by remember { mutableStateOf<String?>(null) }

    // 📡 Live server plans (fallback to safe defaults if offline)
    val rawPlans = vipState.plans
    val allPlans = remember(rawPlans) {
        if (rawPlans.isNotEmpty()) rawPlans else listOf(
            SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59.00", rawOriginalPrice = "89.00", durationDays = 30),
            SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150.00", rawOriginalPrice = "225.00", durationDays = 90, isPopular = true)
        )
    }

    // 🎯 Categorize plans: 1 Month vs Multi-Month
    val monthlyPlans = remember(allPlans) {
        val list = allPlans.filter { it.durationDays <= 45 }
        list.ifEmpty {
            listOf(SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59.00", rawOriginalPrice = "89.00", durationDays = 30))
        }
    }

    val multiMonthPlans = remember(allPlans) {
        val list = allPlans.filter { it.durationDays > 45 }
        list.ifEmpty {
            listOf(SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150.00", rawOriginalPrice = "225.00", durationDays = 90, isPopular = true))
        }
    }

    var selectedMonthlyPlan by remember(monthlyPlans) {
        mutableStateOf(monthlyPlans.find { it.isPopular } ?: monthlyPlans.first())
    }
    var selectedMultiMonthPlan by remember(multiMonthPlans) {
        mutableStateOf(multiMonthPlans.find { it.isPopular } ?: multiMonthPlans.first())
    }

    // 🔒 Auth Guard & Checkout Launcher
    fun initiateSubscriptionCheckout(plan: SubscriptionPlanDto) {
        if (!isLoggedIn) {
            Toast.makeText(context, "Please sign in to purchase a VIP subscription.", Toast.LENGTH_SHORT).show()
            showAuthModal = true
            return
        }

        val uid = userProfile?.id?.filter { it.isDigit() }?.ifBlank { "0" } ?: "0"
        val planId = plan.id
        checkoutTargetUrl = "https://playdramaflix.com/app/vip/checkout.php?plan_id=$planId&user_id=$uid"
    }

    BackHandler {
        if (checkoutTargetUrl != null) {
            checkoutTargetUrl = null
        } else {
            onNavigateBack()
        }
    }

    // FAQ List in 100% International English
    val faqs = remember {
        listOf(
            FaqItem(
                question = "How fast is VIP activation?",
                answer = "Instant! Automated bKash, Nagad, and Crypto TrxID payments are verified in 1-2 seconds."
            ),
            FaqItem(
                question = "How many devices can stream simultaneously?",
                answer = "Each VIP account supports active streaming on up to 2 devices at the same time."
            ),
            FaqItem(
                question = "What happens if a 3rd device signs in?",
                answer = "To protect your account, signing in on a 3rd device automatically logs out the oldest device session."
            ),
            FaqItem(
                question = "Are all videos and series 100% ad-free?",
                answer = "Yes! VIP members enjoy zero video ads, zero popups, and uninterrupted 1080p Ultra HD streaming."
            ),
            FaqItem(
                question = "Is there any video download limit?",
                answer = "No limits! VIP members can download unlimited full drama episodes directly for offline viewing."
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicDarkBg)
    ) {
        // Cosmic Ambient Glow Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE5B567).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.65f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4B6496).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.45f),
                    radius = size.width * 0.70f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // =============================================================
            // 🔝 Top Navigation Bar
            // =============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
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

                // User Login Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1B1E2A),
                    border = BorderStroke(0.8.dp, Color(0xFF2E354A)),
                    modifier = Modifier.clickable {
                        if (!isLoggedIn) showAuthModal = true else onNavigateToProfile()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isLoggedIn) (userProfile?.displayName ?: "User") else "Log in",
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
            // 📜 Scrollable Pricing & Content Body
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
                // 👑 Card 1: Top Gold Tier (Monthly Pass)
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF2A1C08),
                    border = BorderStroke(1.2.dp, GoldBorder.copy(alpha = 0.85f)),
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

                            // Dynamic Plan Cards (Single full-width banner OR horizontal row)
                            if (monthlyPlans.size == 1) {
                                val singlePlan = monthlyPlans.first()
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF593C10),
                                    border = BorderStroke(1.5.dp, Color(0xFFFFDF88)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = singlePlan.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${singlePlan.durationDays} Days All-Access Pass",
                                                color = Color(0xFFE0C49B),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "৳${singlePlan.priceFormatted}",
                                                color = Color(0xFFFFDF88),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            if (singlePlan.originalPriceDouble > singlePlan.priceDouble) {
                                                Text(
                                                    text = "৳${singlePlan.originalPriceFormatted}",
                                                    color = Color(0xFF9E8159),
                                                    fontSize = 11.sp,
                                                    textDecoration = TextDecoration.LineThrough
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    monthlyPlans.forEach { plan ->
                                        val isSelected = (selectedMonthlyPlan.id == plan.id)
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
                                                .clickable { selectedMonthlyPlan = plan }
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
                                                if (plan.originalPriceDouble > plan.priceDouble) {
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
                                }
                            }

                            // Gold Pay Now Button
                            Button(
                                onClick = { initiateSubscriptionCheckout(selectedMonthlyPlan) },
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
                // 🥈 Card 2: Multi-Month Tier (Long-Term Savings)
                // =============================================================
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF131726),
                    border = BorderStroke(1.2.dp, BlueCardBorder.copy(alpha = 0.85f)),
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

                            // Dynamic Multi-Month Cards (Single full-width OR row)
                            if (multiMonthPlans.size == 1) {
                                val singlePlan = multiMonthPlans.first()
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF2B3A60),
                                    border = BorderStroke(1.5.dp, Color(0xFF8EA5D8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = singlePlan.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${singlePlan.durationDays} Days Extended Access",
                                                color = Color(0xFFB8C7E6),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "৳${singlePlan.priceFormatted}",
                                                color = Color(0xFFD7E2FF),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            if (singlePlan.originalPriceDouble > singlePlan.priceDouble) {
                                                Text(
                                                    text = "৳${singlePlan.originalPriceFormatted}",
                                                    color = Color(0xFF6C7C9E),
                                                    fontSize = 11.sp,
                                                    textDecoration = TextDecoration.LineThrough
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    multiMonthPlans.forEach { plan ->
                                        val isSelected = (selectedMultiMonthPlan.id == plan.id)
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
                                                .clickable { selectedMultiMonthPlan = plan }
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
                                                if (plan.originalPriceDouble > plan.priceDouble) {
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
                                }
                            }

                            // Silver/Blue Pay Now Button
                            Button(
                                onClick = { initiateSubscriptionCheckout(selectedMultiMonthPlan) },
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

                // 🎁 Promo Code Banner (100% English)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141824))
                        .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(14.dp))
                        .clickable { showPromoModal = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "Have a VIP Promo Code?",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Redeem ➔",
                        color = Color(0xFF00E676),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // =============================================================
                // 📊 Features Comparison Table (100% International English)
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    // 🎯 2 Devices Limit Explicitly Defined
                    FeatureRow(title = "Simultaneous Devices", freeValue = "1", vipValue = "2")
                    FeatureRow(title = "Zero Ads (100% Ad-Free)", freeCheck = false, vipCheck = true)
                    FeatureRow(title = "1080P Full HD Streaming", freeCheck = false, vipCheck = true)
                    FeatureRow(title = "High-Speed Video Downloads", freeCheck = false, vipCheck = true)
                    FeatureRow(title = "Unlock All Dubbed Episodes", freeCheck = false, vipCheck = true)
                    FeatureRow(title = "Exclusive Early Releases", freeCheck = false, vipCheck = true)
                    FeatureRow(title = "Auto-Approval Fast Activation", freeCheck = false, vipCheck = true)
                }

                // =============================================================
                // ❓ Interactive Frequently Asked Questions (FAQ Accordion)
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Frequently Asked Questions",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    faqs.forEach { faq ->
                        var isExpanded by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF111520),
                            border = BorderStroke(0.8.dp, Color(0xFF1E2536)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
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
                                    Text(
                                        text = faq.question,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = if (isExpanded) Color(0xFFFFB300) else Color(0xFF8E95A5),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(if (isExpanded) 180f else 0f)
                                    )
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Text(
                                        text = faq.answer,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =============================================================
        // 💳 Secure In-App Checkout Web Modal
        // =============================================================
        if (checkoutTargetUrl != null) {
            CheckoutWebViewModal(
                checkoutUrl = checkoutTargetUrl!!,
                onDismiss = { checkoutTargetUrl = null },
                onSuccess = {
                    checkoutTargetUrl = null
                    viewModel.refreshVipStatusAndProfile()
                    Toast.makeText(context, "VIP Activated Successfully!", Toast.LENGTH_LONG).show()
                    onNavigateBack()
                }
            )
        }

        // Auth Modal (Triggered by login guard)
        if (showAuthModal) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthModal = false }
            )
        }

        // Promo Code Modal
        if (showPromoModal) {
            PromoCodeDialog(
                viewModel = viewModel,
                onDismiss = { showPromoModal = false }
            )
        }
    }
}

@Composable
private fun FeatureRow(
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

            // Pro VIP Column
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
                                    // Intercept successful deep link from server checkout.php
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
