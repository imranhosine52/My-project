@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00D166)
private val AlertRed = Color(0xFFFF3B30)

private enum class AuthViewMode {
    WELCOME,
    SIGN_UP,
    LOG_IN
}

@Composable
fun AuthBottomSheetDialog(
    viewModel: DramaFlixViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()

    var viewMode by remember { mutableStateOf(AuthViewMode.WELCOME) }
    var inputEmail by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // ⚠️ সার্ভার এরর / আইপি লিমিট সতর্কবার্তা ব্যানার
            // =========================================================================
            AnimatedVisibility(
                visible = authState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E1015),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = authState.errorMessage ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            when (viewMode) {
                // =========================================================================
                // 🌟 ১. WELCOME / MAIN SCREEN (গুগল সাইন-ইনকে ১ নম্বরে রাখা হয়েছে)
                // =========================================================================
                AuthViewMode.WELCOME -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(28.dp))
                        Text(
                            text = "Welcome to PlayDramaFlix",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Sign in to watch unlimited Asian dramas with 24-Hour Free VIP Trial!",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 🎯 ১-ক্লিকে সরাসরি গুগল সাইন-ইন বাটন (4-Color Google Logo)
                    GoogleSignInButton(
                        onClick = {
                            viewModel.signInWithGoogle(context) { success ->
                                if (success) {
                                    Toast.makeText(context, "🎉 Welcome to PlayDramaFlix!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        isLoading = authState.isLoading,
                        text = "Continue with Google",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderDark, thickness = 0.8.dp)
                        Text("OR", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderDark, thickness = 0.8.dp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ইমেইল দিয়ে সাইন-আপ বাটন
                    Button(
                        onClick = {
                            viewModel.clearAuthMessage()
                            viewMode = AuthViewMode.SIGN_UP
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign up with Email", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ইমেইল লগইন বাটন
                    Button(
                        onClick = {
                            viewModel.clearAuthMessage()
                            viewMode = AuthViewMode.LOG_IN
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2636)),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Already have an account? Log In", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "By continuing, you agree to our User Agreement and Privacy Policy. Max 2 accounts per device IP.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }

                // =========================================================================
                // ✍️ ২. SIGN UP WITH EMAIL
                // =========================================================================
                AuthViewMode.SIGN_UP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewMode = AuthViewMode.WELCOME }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign up with Email", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Full Name", color = TextMuted) },
                        placeholder = { Text("e.g. John Doe", color = Color.DarkGray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Email Address", color = TextMuted) },
                        placeholder = { Text("e.g. yourname@gmail.com", color = Color.DarkGray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Password (min 6 characters)", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (inputName.isBlank()) {
                                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (inputEmail.isBlank() || !inputEmail.contains("@")) {
                                Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (inputPassword.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            viewModel.registerUser(
                                name = inputName.trim(),
                                emailOrPhone = inputEmail.trim(),
                                password = inputPassword
                            ) { success ->
                                if (success) {
                                    Toast.makeText(context, "🎉 Account created successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !authState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create Free Account", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Already registered? Log In here",
                        color = ActionGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            viewModel.clearAuthMessage()
                            viewMode = AuthViewMode.LOG_IN
                        }
                    )
                }

                // =========================================================================
                // 🔑 ৩. LOG IN WITH EMAIL
                // =========================================================================
                AuthViewMode.LOG_IN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewMode = AuthViewMode.WELCOME }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log In with Email", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Email or Phone", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Password", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (inputEmail.isBlank()) {
                                Toast.makeText(context, "Please enter your email or phone", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (inputPassword.isBlank()) {
                                Toast.makeText(context, "Please enter your password", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            viewModel.loginUser(
                                emailOrPhone = inputEmail.trim(),
                                password = inputPassword
                            ) { success ->
                                if (success) {
                                    Toast.makeText(context, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !authState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Log In", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Don't have an account? Sign up here",
                        color = ActionGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            viewModel.clearAuthMessage()
                            viewMode = AuthViewMode.SIGN_UP
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
