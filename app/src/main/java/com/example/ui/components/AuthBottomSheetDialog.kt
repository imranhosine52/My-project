package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

@Composable
fun AuthBottomSheetDialog(
    viewModel: DramaFlixViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var inputEmail by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var selectedAuthMode by remember { mutableIntStateOf(0) } // 0: Google Sign-In, 1: Quick Email Sign-In

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 440.dp)
                    .clickable(enabled = false) {}
                    .testTag("auth_dialog_surface"),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.2.dp, BorderDark),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row: Tag & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TealAccent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "PLAYDRAMAFLIX ACCOUNT",
                                color = TealAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Logo Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(RedAccent, TealAccent))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Sign In & Sign Up",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Connect your account to unlock 1080p Asian dramas, VIP pass & sync your watchlist across devices.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Mode Switcher Tabs
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAuthMode = 0 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedAuthMode == 0) RedAccent else Color.Transparent
                            ) {
                                Text(
                                    text = "Google 1-Tap",
                                    color = if (selectedAuthMode == 0) Color.White else TextMuted,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAuthMode = 1 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedAuthMode == 1) RedAccent else Color.Transparent
                            ) {
                                Text(
                                    text = "Email Sign-In",
                                    color = if (selectedAuthMode == 1) Color.White else TextMuted,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Tab Content
                    if (selectedAuthMode == 0) {
                        // Native One-Tap Google Button
                        GoogleSignInButton(
                            text = "Continue with Google",
                            isLoading = authState.isLoading,
                            onClick = {
                                viewModel.signInWithGoogle(context) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Welcome to PlayDramaFlix!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        // Fallback directly to email input for instant seamless login
                                        selectedAuthMode = 1
                                        Toast.makeText(context, "Please enter your Google email below", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Features List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardDark, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AuthBenefitRow(
                                icon = Icons.Outlined.WorkspacePremium,
                                title = "VIP Drama Access",
                                subtitle = "Unlock exclusive Asian drama episodes in 1080p",
                                iconColor = GoldVip
                            )
                            AuthBenefitRow(
                                icon = Icons.Outlined.CloudSync,
                                title = "Cross-Device Sync",
                                subtitle = "Save playback position & favorites securely",
                                iconColor = TealAccent
                            )
                            AuthBenefitRow(
                                icon = Icons.Outlined.Badge,
                                title = "Permanent 8-Digit UID",
                                subtitle = "Instant account linked with playdramaflix.com",
                                iconColor = RedAccent
                            )
                        }
                    } else {
                        // Fast Email & Name Sign-In / Register Form
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = inputEmail,
                                onValueChange = { inputEmail = it },
                                label = { Text("Google Email", color = TextMuted, fontSize = 12.sp) },
                                placeholder = { Text("e.g. yourname@gmail.com", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (inputEmail.isNotEmpty()) {
                                        IconButton(onClick = { inputEmail = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealAccent,
                                    unfocusedBorderColor = BorderDark,
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = inputName,
                                onValueChange = { inputName = it },
                                label = { Text("Your Name (Optional)", color = TextMuted, fontSize = 12.sp) },
                                placeholder = { Text("e.g. Tanjim Hasan", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RedAccent,
                                    unfocusedBorderColor = BorderDark,
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (inputEmail.isBlank() || !inputEmail.contains("@")) {
                                        Toast.makeText(context, "Please enter a valid Google email", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.signInOrRegisterWithGoogleEmail(
                                        email = inputEmail.trim(),
                                        name = inputName.trim().ifBlank { null }
                                    ) { success ->
                                        if (success) {
                                            Toast.makeText(context, "Welcome to PlayDramaFlix!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = !authState.isLoading && inputEmail.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_email_auth_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
                            ) {
                                if (authState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Authenticating...", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        GoogleLogoIcon(modifier = Modifier.size(16.dp))
                                        Text("Sign In / Register Account", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "By signing in, you agree to PlayDramaFlix Terms of Service & Privacy Policy.",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBenefitRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.5.sp
            )
        }
    }
}
