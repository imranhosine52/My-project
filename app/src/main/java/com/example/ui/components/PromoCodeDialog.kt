package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00E676)
private val AlertRed = Color(0xFFFF5252)
private val GoldAccent = Color(0xFFFFB300)
private val DarkCardBg = Color(0xFF10141F)
private val CardBorderColor = Color(0xFF232D42)

@Composable
fun PromoCodeDialog(
    viewModel: DramaFlixViewModel,
    onDismiss: () -> Unit
) {
    var promoCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading
        )
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.2.dp, CardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ১. হেডার রো (আইকন, টাইটেল ও ক্লোজ বাটন)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Redeem Promo Code",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isLoading) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF8E95A5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1D2436), thickness = 0.8.dp)

                // ২. বিবরণ টেক্সট
                Text(
                    text = "আপনার কাছে কোনো VIP গিফট ভাউচার বা প্রমো কোড থাকলে নিচে লিখুন এবং তাৎক্ষণিক VIP সাবস্ক্রিপশন ক্লেইম করুন।",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // ৩. প্রমো কোড ইনপুট বক্স (Auto Uppercase)
                OutlinedTextField(
                    value = promoCodeInput,
                    onValueChange = { 
                        promoCodeInput = it.uppercase()
                        statusMessage = null 
                    },
                    placeholder = { 
                        Text("e.g. VIPFREE7", color = Color(0xFF556075), fontSize = 13.sp) 
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = CardBorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // ৪. স্ট্যাটাস বা এরর মেসেজ বক্স
                AnimatedVisibility(
                    visible = statusMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSuccess) ActionGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (isSuccess) ActionGreen.copy(alpha = 0.6f) else AlertRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isSuccess) ActionGreen else AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = statusMessage ?: "",
                                color = if (isSuccess) ActionGreen else Color(0xFFFFB4B4),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // ৫. সাবমিট বাটন
                Button(
                    onClick = {
                        val code = promoCodeInput.trim()
                        if (code.isBlank()) return@Button

                        isLoading = true
                        statusMessage = null
                        focusManager.clearFocus()

                        viewModel.redeemPromoCode(code) { success, message ->
                            isLoading = false
                            isSuccess = success
                            statusMessage = message
                            if (success) {
                                promoCodeInput = ""
                            }
                        }
                    },
                    enabled = !isLoading && promoCodeInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionGreen,
                        disabledContainerColor = ActionGreen.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verifying Code...",
                            color = Color.Black,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Claim VIP Pass",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
