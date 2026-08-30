package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.theme.*

private val GoldAccent = Color(0xFFFFB300)

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
            GatewayItemDto(id = "bkash", name = "bKash", number = "01330049110", type = "Personal", color = "#E2136E"),
            GatewayItemDto(id = "nagad", name = "Nagad", number = "01330049110", type = "Personal", color = "#F7941D"),
            GatewayItemDto(id = "rocket", name = "Rocket", number = "01330049110", type = "Personal", color = "#8C3494")
        )
    }

    var selectedGateway by remember { mutableStateOf(activeGateways.firstOrNull() ?: GatewayItemDto(name = "bKash", number = "01330049110")) }
    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }
    var numberCopied by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.2.dp, GoldAccent.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Checkout • ${plan.name}",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    // Plan Price Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF241A00))
                            .border(1.dp, GoldAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(plan.name, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${plan.durationDays} Days All-Access Pass", color = TextSecondary, fontSize = 11.5.sp)
                            }
                            Text("৳ ${plan.priceFormatted}", color = GoldAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Step 1: Gateway Selection
                    Text("1. Select Payment Method", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeGateways.forEach { gateway ->
                            val isSelected = selectedGateway.name.equals(gateway.name, ignoreCase = true)
                            val brandCol = when {
                                gateway.name.contains("bkash", true) -> Color(0xFFE2136E)
                                gateway.name.contains("nagad", true) -> Color(0xFFF7941D)
                                else -> Color(0xFF8C3494)
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) brandCol.copy(alpha = 0.25f) else SurfaceVariantDark,
                                border = BorderStroke(if (isSelected) 1.5.dp else 0.8.dp, if (isSelected) brandCol else BorderDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedGateway = gateway
                                        numberCopied = false
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(gateway.name, color = if (isSelected) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Step 2: Target Number & 1-Click Copy
                    val targetNumber = selectedGateway.effectiveNumber.ifBlank { "01330049110" }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariantDark)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Send Money to (${selectedGateway.name} Personal):", color = TextSecondary, fontSize = 11.5.sp)
                                Button(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Gateway", targetNumber))
                                        numberCopied = true
                                        Toast.makeText(context, "Number copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (numberCopied) Color(0xFF00E676) else GoldAccent),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(if (numberCopied) "Copied!" else "Copy", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(targetNumber, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Step 3: Transaction Inputs
                    Text("2. Enter Payment SMS Details", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())

                    OutlinedTextField(
                        value = senderNumber,
                        onValueChange = { senderNumber = it; validationError = null },
                        label = { Text("Sender Mobile Number") },
                        placeholder = { Text("e.g. 01712345678") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldAccent, unfocusedBorderColor = BorderDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = trxId,
                        onValueChange = { trxId = it.uppercase(); validationError = null },
                        label = { Text("Transaction ID (TrxID)") },
                        placeholder = { Text("e.g. 9B8A7X6Y5Z") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldAccent, unfocusedBorderColor = BorderDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (validationError != null) {
                        Text(validationError!!, color = Color(0xFFFF5252), fontSize = 11.5.sp, modifier = Modifier.fillMaxWidth())
                    }

                    // Submit Button
                    Button(
                        onClick = {
                            if (senderNumber.trim().length < 6) {
                                validationError = "Please enter a valid Sender Number."
                                return@Button
                            }
                            if (trxId.trim().length < 4) {
                                validationError = "Please enter the Transaction ID (TrxID)."
                                return@Button
                            }
                            onSubmitPayment(
                                plan.rawId ?: 1,
                                plan.name,
                                plan.priceDouble,
                                selectedGateway.name,
                                senderNumber.trim(),
                                trxId.trim(),
                                null
                            )
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Submit VIP Payment", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
