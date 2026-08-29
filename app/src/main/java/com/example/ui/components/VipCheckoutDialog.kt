package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GatewayItemDto
import com.example.data.model.SubscriptionPlanDto
import com.example.ui.VipCrownVectorIcon
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipCheckoutDialog(
    plan: SubscriptionPlanDto,
    gateways: List<GatewayItemDto>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitPayment: (
        planId: Any,
        planName: String,
        amount: Double,
        gateway: String,
        senderNumber: String,
        trxId: String,
        notes: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val activeGateways = remember(gateways) {
        if (gateways.isNotEmpty()) gateways.filter { it.isActive }
        else listOf(
            GatewayItemDto(
                id = "bkash",
                name = "bKash",
                number = "01330049110",
                type = "Personal",
                instructions = "Go to bKash App -> Send Money to the number above -> Copy TrxID and paste below.",
                color = "#E2136E",
                icon = "bkash"
            ),
            GatewayItemDto(
                id = "nagad",
                name = "Nagad",
                number = "01330049110",
                type = "Personal",
                instructions = "Go to Nagad App -> Send Money to the number above -> Copy TrxID and paste below.",
                color = "#F7941D",
                icon = "nagad"
            ),
            GatewayItemDto(
                id = "rocket",
                name = "Rocket",
                number = "01330049110",
                type = "Personal",
                instructions = "Go to Rocket App -> Send Money to the number above -> Copy TrxID and paste below.",
                color = "#8C3494",
                icon = "rocket"
            )
        )
    }

    var selectedGateway by remember { mutableStateOf(activeGateways.firstOrNull() ?: GatewayItemDto(name = "bKash", number = "01330049110")) }
    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var numberCopied by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .wrapContentHeight()
                    .testTag("vip_checkout_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldVip.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GoldVip.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = GoldVip,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "VIP Checkout",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Secure Mobile Payment",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selected Plan Summary Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF2A2000), Color(0xFF1B1500))
                                )
                            )
                            .border(1.dp, GoldVip.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = plan.name,
                                    color = GoldVip,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${plan.durationDays} Days All-Access Pass",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "৳ ${plan.priceFormatted}",
                                    color = GoldVip,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "One-time payment",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Step 1: Select Payment Gateway
                    Text(
                        text = "1. Select Payment Method",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeGateways.forEach { gateway ->
                            val isSelected = selectedGateway.id == gateway.id || selectedGateway.effectiveName.equals(gateway.effectiveName, ignoreCase = true)
                            val brandColor = parseGatewayColor(gateway.color, gateway.effectiveName)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) brandColor.copy(alpha = 0.2f) else SurfaceVariantDark)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) brandColor else BorderDark,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedGateway = gateway
                                        numberCopied = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                                    .testTag("gateway_option_${gateway.effectiveName.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(brandColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gateway.effectiveName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = gateway.effectiveName,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 2: Account Number & 1-Click Copy
                    val targetNumber = selectedGateway.effectiveNumber.ifBlank { "01330049110" }
                    val gatewayType = selectedGateway.type?.ifBlank { "Personal" } ?: "Personal"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = TealAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${selectedGateway.effectiveName} ($gatewayType Number):",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // 1-Click Copy Button
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("PlayDramaFlix Gateway", targetNumber)
                                        clipboard.setPrimaryClip(clip)
                                        numberCopied = true
                                        Toast.makeText(context, "${selectedGateway.effectiveName} number copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (numberCopied) TealAccent else GoldVip.copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).testTag("copy_gateway_number_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (numberCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = if (numberCopied) Color.Black else GoldVip,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (numberCopied) "Copied!" else "Copy",
                                            color = if (numberCopied) Color.Black else GoldVip,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Large Number Display
                            Text(
                                text = targetNumber,
                                color = TextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )

                            // Instructions
                            Text(
                                text = selectedGateway.effectiveInstructions.ifBlank {
                                    "Send exact ৳ ${plan.priceFormatted} via Send Money from your ${selectedGateway.effectiveName} App to the number above."
                                },
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 3: Enter Payment Details Form
                    Text(
                        text = "2. Enter Your Transaction Details",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )

                    // Sender Number Field
                    OutlinedTextField(
                        value = senderNumber,
                        onValueChange = {
                            senderNumber = it
                            validationError = null
                        },
                        label = { Text("Sender Mobile Number") },
                        placeholder = { Text("e.g. 01712345678") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = TextSecondary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = TealAccent,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sender_number_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // TrxID Field
                    OutlinedTextField(
                        value = trxId,
                        onValueChange = {
                            trxId = it.uppercase()
                            validationError = null
                        },
                        label = { Text("Transaction ID (TrxID)") },
                        placeholder = { Text("e.g. 9B8A7X6Y5Z") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = GoldVip)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldVip,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GoldVip,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trx_id_input")
                    )

                    // Validation Error Text
                    if (validationError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = validationError!!,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (senderNumber.trim().length < 6) {
                                validationError = "Please enter a valid Sender Number (at least 6 digits)."
                                return@Button
                            }
                            if (trxId.trim().length < 4) {
                                validationError = "Please enter the Transaction ID (TrxID) from your payment SMS."
                                return@Button
                            }
                            validationError = null
                            onSubmitPayment(
                                plan.rawId ?: 1,
                                plan.name,
                                plan.priceDouble,
                                selectedGateway.effectiveName,
                                senderNumber.trim(),
                                trxId.trim(),
                                notes.takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_payment_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldVip,
                            disabledContainerColor = SurfaceVariantDark
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = GoldButtonText,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GoldButtonText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Submit VIP Payment",
                                    color = GoldButtonText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "🔒 Payment is verified automatically or by admin within 5-15 minutes.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Success Invoice Dialog showing submission confirmation
@Composable
fun VipSubmissionSuccessDialog(
    invoiceId: String,
    planName: String,
    message: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .testTag("vip_submission_success_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldVip)
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
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(TealAccent, GoldVip)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BackgroundDark,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        text = "Payment Submitted!",
                        color = GoldVip,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = message ?: "Your payment information has been submitted. Our team is verifying your Transaction ID. Your VIP subscription will be activated automatically!",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Invoice Details Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Submission ID:", color = TextMuted, fontSize = 12.sp)
                                Text(invoiceId, color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Plan:", color = TextMuted, fontSize = 12.sp)
                                Text(planName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status:", color = TextMuted, fontSize = 12.sp)
                                Text("Pending Approval ⏳", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("done_vip_submission_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldVip)
                    ) {
                        Text(
                            text = "Continue Watching",
                            color = GoldButtonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun parseGatewayColor(colorHex: String?, name: String): Color {
    if (!colorHex.isNullOrBlank()) {
        try {
            return Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {}
    }
    return when {
        name.contains("bkash", ignoreCase = true) -> Color(0xFFE2136E)
        name.contains("nagad", ignoreCase = true) -> Color(0xFFF7941D)
        name.contains("rocket", ignoreCase = true) -> Color(0xFF8C3494)
        name.contains("upay", ignoreCase = true) -> Color(0xFF00A79D)
        else -> Color(0xFF00ACC1)
    }
}
